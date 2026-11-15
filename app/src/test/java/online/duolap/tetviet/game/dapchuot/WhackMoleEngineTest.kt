package online.duolap.tetviet.game.dapchuot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import online.duolap.tetviet.game.dapchuot.engine.MoleType
import online.duolap.tetviet.game.dapchuot.engine.WhackMoleEngine
import org.junit.Assert.*
import org.junit.Test

class WhackMoleEngineTest {

    @Test
    fun testInitialState() {
        val engine = WhackMoleEngine(
            holeCount = 9,
            gameDurationSeconds = 60,
            onMoleAppear = { _, _ -> },
            onMoleDisappear = { _ -> },
            onScoreChanged = { _, _, _ -> },
            onTimerTick = { _ -> },
            onGameOver = { _ -> }
        )

        assertEquals(0, engine.getCurrentScore())
        assertFalse(engine.isGameRunning())
    }

    @Test
    fun testStartAndStop() {
        val scope = CoroutineScope(Dispatchers.Default)

        var gameEnded = false
        val engine = WhackMoleEngine(
            holeCount = 9,
            gameDurationSeconds = 60,
            onMoleAppear = { _, _ -> },
            onMoleDisappear = { _ -> },
            onScoreChanged = { _, _, _ -> },
            onTimerTick = { _ -> },
            onGameOver = { _ -> gameEnded = true }
        )

        engine.start(scope)
        assertTrue(engine.isGameRunning())

        engine.stop()
        assertFalse(engine.isGameRunning())
        assertFalse(gameEnded)
        scope.cancel()
    }

    @Test
    fun testHitNonExistentMoleReturnsFalse() {
        val engine = WhackMoleEngine(
            holeCount = 9,
            gameDurationSeconds = 60,
            onMoleAppear = { _, _ -> },
            onMoleDisappear = { _ -> },
            onScoreChanged = { _, _, _ -> },
            onTimerTick = { _ -> },
            onGameOver = { _ -> }
        )

        assertFalse(engine.hitMole(0))
        assertFalse(engine.hitMole(4))
        assertFalse(engine.hitMole(8))
    }

    @Test
    fun testMoleTypePoints() {
        assertEquals(10, MoleType.NORMAL.points)
        assertEquals(30, MoleType.GOLDEN.points)
        assertEquals(-15, MoleType.BOMB.points)
    }

    @Test
    fun continueWithExtraTimeRestartsTimerAndKeepsScore() {
        val scope = CoroutineScope(Dispatchers.Default)
        val engine = WhackMoleEngine(
            holeCount = 9,
            gameDurationSeconds = 60,
            onMoleAppear = { _, _ -> },
            onMoleDisappear = { _ -> },
            onScoreChanged = { _, _, _ -> },
            onTimerTick = { _ -> },
            onGameOver = { _ -> }
        )

        // Đang chơi thì không cho chơi tiếp chồng lên
        engine.start(scope)
        engine.continueWithExtraTime(scope, 10)
        assertEquals(60, engine.getRemainingSeconds())

        engine.stop()
        engine.continueWithExtraTime(scope, 10)
        assertTrue(engine.isGameRunning())
        assertEquals(10, engine.getRemainingSeconds())
        assertEquals(0, engine.getCurrentScore())

        engine.stop()
        engine.continueWithExtraTime(scope, 0)
        assertFalse(engine.isGameRunning())
        scope.cancel()
    }
}
