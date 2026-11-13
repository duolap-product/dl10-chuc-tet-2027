package online.duolap.tetviet.game.dapchuot.engine

import android.content.Context
import android.content.SharedPreferences

class DapChuotPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var highScore: Int
        get() = prefs.getInt(KEY_HIGH_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_HIGH_SCORE, value).apply()

    var totalGamesPlayed: Int
        get() = prefs.getInt(KEY_TOTAL_GAMES, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_GAMES, value).apply()

    companion object {
        private const val PREFS_NAME = "dap_chuot_prefs"
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_TOTAL_GAMES = "total_games"
    }
}
