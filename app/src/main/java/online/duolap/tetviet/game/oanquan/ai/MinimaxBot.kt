package online.duolap.tetviet.game.oanquan.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.duolap.tetviet.game.oanquan.engine.MoveDirection
import online.duolap.tetviet.game.oanquan.engine.OAnQuanBoardState
import online.duolap.tetviet.game.oanquan.engine.OAnQuanEngine
import online.duolap.tetviet.game.oanquan.engine.PlayerSide
import kotlin.random.Random

class MinimaxBot(
    private val botSide: PlayerSide = PlayerSide.PLAYER_2
) {

    suspend fun findBestMove(
        state: OAnQuanBoardState,
        difficulty: AIDifficulty
    ): AIMove? = withContext(Dispatchers.Default) {
        val validMoves = OAnQuanEngine.getValidMoves(state, botSide)
        if (validMoves.isEmpty()) return@withContext null

        when (difficulty) {
            AIDifficulty.EASY -> findEasyMove(state, validMoves)
            AIDifficulty.MEDIUM -> findMinimaxMove(state, validMoves, maxDepth = 2)
            AIDifficulty.HARD -> findMinimaxMove(state, validMoves, maxDepth = 4)
        }
    }

    private fun findEasyMove(
        state: OAnQuanBoardState,
        validMoves: List<Pair<Int, MoveDirection>>
    ): AIMove {
        // 40% chọn nước đi ngẫu nhiên
        if (Random.nextFloat() < 0.40f) {
            val randomChoice = validMoves.random()
            return AIMove(randomChoice.first, randomChoice.second)
        }

        // 60% chọn tham lam (nước đi có điểm ăn ngay cao nhất)
        var bestMove = validMoves.random()
        var maxGain = -1

        for ((pit, dir) in validMoves) {
            val clone = state.deepCopy()
            val beforeScore = if (botSide == PlayerSide.PLAYER_1) clone.player1Score else clone.player2Score
            OAnQuanEngine.applyMove(clone, pit, dir)
            val afterScore = if (botSide == PlayerSide.PLAYER_1) clone.player1Score else clone.player2Score
            val gain = afterScore - beforeScore
            if (gain > maxGain) {
                maxGain = gain
                bestMove = Pair(pit, dir)
            }
        }

        return AIMove(bestMove.first, bestMove.second)
    }

    private fun findMinimaxMove(
        state: OAnQuanBoardState,
        validMoves: List<Pair<Int, MoveDirection>>,
        maxDepth: Int
    ): AIMove {
        var bestScore = Int.MIN_VALUE
        var bestMove = validMoves.random()

        var alpha = Int.MIN_VALUE
        val beta = Int.MAX_VALUE

        for ((pit, dir) in validMoves) {
            val clone = state.deepCopy()
            OAnQuanEngine.applyMove(clone, pit, dir)

            val score = minimax(
                state = clone,
                depth = maxDepth - 1,
                alpha = alpha,
                beta = beta,
                isMaximizing = false
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = Pair(pit, dir)
            }
            alpha = maxOf(alpha, bestScore)
            if (beta <= alpha) break
        }

        return AIMove(bestMove.first, bestMove.second)
    }

    private fun minimax(
        state: OAnQuanBoardState,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean
    ): Int {
        if (depth == 0 || state.isGameOver) {
            return evaluateBoard(state)
        }

        var currentAlpha = alpha
        var currentBeta = beta
        val currentPlayer = if (isMaximizing) botSide else botSide.opponent
        val moves = OAnQuanEngine.getValidMoves(state, currentPlayer)

        if (moves.isEmpty()) {
            return evaluateBoard(state)
        }

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for ((pit, dir) in moves) {
                val clone = state.deepCopy()
                OAnQuanEngine.applyMove(clone, pit, dir)
                val eval = minimax(clone, depth - 1, currentAlpha, currentBeta, false)
                maxEval = maxOf(maxEval, eval)
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for ((pit, dir) in moves) {
                val clone = state.deepCopy()
                OAnQuanEngine.applyMove(clone, pit, dir)
                val eval = minimax(clone, depth - 1, currentAlpha, currentBeta, true)
                minEval = minOf(minEval, eval)
                currentBeta = minOf(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    private fun evaluateBoard(state: OAnQuanBoardState): Int {
        if (state.isGameOver) {
            return when (state.winner) {
                botSide -> 50000
                botSide.opponent -> -50000
                else -> 0
            }
        }

        val botScore = if (botSide == PlayerSide.PLAYER_1) state.player1Score - state.player1Debt else state.player2Score - state.player2Debt
        val oppScore = if (botSide == PlayerSide.PLAYER_1) state.player2Score - state.player2Debt else state.player1Score - state.player1Debt

        val botHomeStones = botSide.ownedPits.sumOf { state.pits[it] }
        val oppHomeStones = botSide.opponent.ownedPits.sumOf { state.pits[it] }

        // Trọng số đánh giá thế trận
        return (botScore - oppScore) * 100 + (botHomeStones - oppHomeStones) * 10
    }
}

