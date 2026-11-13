package online.duolap.tetviet.game.oanquan.ai

import online.duolap.tetviet.game.oanquan.engine.MoveDirection

enum class AIDifficulty {
    EASY,   // Tập sự
    MEDIUM, // Vừa sức
    HARD    // Cao thủ
}

data class AIMove(
    val pitIndex: Int,
    val direction: MoveDirection
)

interface OAnQuanAI {
    suspend fun findBestMove(
        difficulty: AIDifficulty
    ): AIMove?
}

