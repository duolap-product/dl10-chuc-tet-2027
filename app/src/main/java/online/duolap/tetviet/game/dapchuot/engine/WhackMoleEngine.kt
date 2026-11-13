package online.duolap.tetviet.game.dapchuot.engine

import kotlinx.coroutines.*
import kotlin.random.Random

class WhackMoleEngine(
    private val holeCount: Int = 9,
    private val gameDurationSeconds: Int = 60,
    private val onMoleAppear: (holeIndex: Int, type: MoleType) -> Unit,
    private val onMoleDisappear: (holeIndex: Int) -> Unit,
    private val onScoreChanged: (score: Int, delta: Int, combo: Int) -> Unit,
    private val onTimerTick: (remainingSeconds: Int) -> Unit,
    private val onGameOver: (finalScore: Int) -> Unit
) {
    private var score = 0
    private var combo = 0
    private var consecutiveHits = 0
    private var remainingSeconds = gameDurationSeconds
    private val activeMoles = mutableMapOf<Int, MoleState>() // holeIndex -> MoleState
    private var gameJob: Job? = null
    private var timerJob: Job? = null
    private var scope: CoroutineScope? = null
    private var isRunning = false
    private var difficultyLevel = 1

    // Difficulty tuning
    private val baseStayDurationMs get() = when {
        difficultyLevel < 3 -> 1800L
        difficultyLevel < 5 -> 1400L
        difficultyLevel < 7 -> 1100L
        else -> 850L
    }
    private val spawnIntervalMs get() = when {
        difficultyLevel < 3 -> 900L
        difficultyLevel < 5 -> 700L
        difficultyLevel < 7 -> 550L
        else -> 400L
    }
    private val maxSimultaneousMoles get() = when {
        difficultyLevel < 3 -> 1
        difficultyLevel < 5 -> 2
        difficultyLevel < 7 -> 2
        else -> 3
    }

    fun start(coroutineScope: CoroutineScope) {
        if (isRunning) return
        isRunning = true
        score = 0
        combo = 0
        consecutiveHits = 0
        remainingSeconds = gameDurationSeconds
        activeMoles.clear()
        scope = coroutineScope
        startTimer()
        startMoleSpawner()
    }

    fun stop() {
        isRunning = false
        gameJob?.cancel()
        timerJob?.cancel()
        activeMoles.clear()
    }

    fun hitMole(holeIndex: Int): Boolean {
        val mole = activeMoles[holeIndex] ?: return false
        if (mole.isHit || !mole.isVisible) return false
        mole.isHit = true
        activeMoles.remove(holeIndex)

        val delta: Int
        if (mole.type == MoleType.BOMB) {
            consecutiveHits = 0
            combo = 0
            delta = mole.type.points // negative
        } else {
            consecutiveHits++
            if (consecutiveHits >= 3) {
                combo++
                val comboBonus = combo * 5
                delta = mole.type.points + comboBonus
            } else {
                delta = mole.type.points
            }
        }

        score = maxOf(0, score + delta)
        onScoreChanged(score, delta, if (mole.type != MoleType.BOMB) consecutiveHits else 0)
        onMoleDisappear(holeIndex)
        return true
    }

    private fun startTimer() {
        timerJob = scope?.launch {
            while (isRunning && remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
                difficultyLevel = ((gameDurationSeconds - remainingSeconds) / 8) + 1
                onTimerTick(remainingSeconds)
                if (remainingSeconds <= 0) {
                    isRunning = false
                    gameJob?.cancel()
                    onGameOver(score)
                }
            }
        }
    }

    private fun startMoleSpawner() {
        gameJob = scope?.launch {
            while (isRunning) {
                delay(spawnIntervalMs)
                if (!isRunning) break
                spawnMole()
                // Also check for expired moles
                checkExpiredMoles()
            }
        }
    }

    private fun spawnMole() {
        if (activeMoles.size >= maxSimultaneousMoles) return
        val availableHoles = (0 until holeCount).filter { it !in activeMoles }
        if (availableHoles.isEmpty()) return

        val holeIndex = availableHoles.random()
        val type = randomMoleType()
        val stayDuration = baseStayDurationMs + Random.nextLong(-200, 200)
        val mole = MoleState(
            holeIndex = holeIndex,
            type = type,
            isVisible = true,
            appearTimeMs = System.currentTimeMillis(),
            stayDurationMs = stayDuration
        )
        activeMoles[holeIndex] = mole
        onMoleAppear(holeIndex, type)

        // Auto-hide after stayDuration
        scope?.launch {
            delay(stayDuration)
            if (activeMoles[holeIndex] == mole && !mole.isHit) {
                activeMoles.remove(holeIndex)
                consecutiveHits = 0
                combo = 0
                onMoleDisappear(holeIndex)
            }
        }
    }

    private fun checkExpiredMoles() {
        val now = System.currentTimeMillis()
        val expired = activeMoles.filter { (_, mole) ->
            !mole.isHit && now - mole.appearTimeMs > mole.stayDurationMs + 200
        }
        expired.forEach { (index, _) ->
            activeMoles.remove(index)
            consecutiveHits = 0
            combo = 0
            onMoleDisappear(index)
        }
    }

    private fun randomMoleType(): MoleType {
        val r = Random.nextInt(100)
        return when {
            r < 10 -> MoleType.GOLDEN   // 10% golden
            r < 18 -> MoleType.BOMB     // 8% bomb
            else -> MoleType.NORMAL     // 82% normal
        }
    }

    fun getCurrentScore() = score
    fun isGameRunning() = isRunning
}
