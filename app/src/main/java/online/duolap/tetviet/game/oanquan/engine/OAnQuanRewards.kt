package online.duolap.tetviet.game.oanquan.engine

import online.duolap.tetviet.game.oanquan.ai.AIDifficulty

/**
 * Xu thưởng khi thắng một ván Ô Ăn Quan (chưa nhân đôi bằng quảng cáo).
 *
 * Cân theo lì xì điểm danh (10→100 xu/ngày, trung bình ~38): một ván thắng ≈ một ngày điểm danh.
 * Chơi 2 người cùng máy không có thưởng, vì một người có thể tự điều khiển cả hai bên để "thắng".
 */
object OAnQuanRewards {
    const val WIN_EASY = 15
    const val WIN_MEDIUM = 30
    const val WIN_HARD = 50
    const val WIN_ONLINE = 40

    fun winCoins(mode: String, difficulty: AIDifficulty): Int = when (mode) {
        OAnQuanPreferences.MODE_AI -> when (difficulty) {
            AIDifficulty.EASY -> WIN_EASY
            AIDifficulty.MEDIUM -> WIN_MEDIUM
            AIDifficulty.HARD -> WIN_HARD
        }
        OAnQuanPreferences.MODE_ONLINE -> WIN_ONLINE
        else -> 0
    }
}
