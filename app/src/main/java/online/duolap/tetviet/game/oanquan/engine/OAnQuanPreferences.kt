package online.duolap.tetviet.game.oanquan.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import online.duolap.tetviet.game.oanquan.ai.AIDifficulty
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAnQuanPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "tet_viet_oanquan_prefs"
        private const val KEY_PLAYER_ID = "player_id"
        private const val KEY_PLAYER_NAME = "player_name"
        private const val KEY_LAST_MODE = "last_mode"
        private const val KEY_AI_DIFFICULTY = "ai_difficulty"
        private const val KEY_TOTAL_GAMES = "total_games"
        private const val KEY_TOTAL_WINS = "total_wins"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"

        const val MODE_AI = "AI"
        const val MODE_PASS_AND_PLAY = "PASS_AND_PLAY"
        const val MODE_ONLINE = "ONLINE"
    }

    fun getPlayerId(): String {
        var id = prefs.getString(KEY_PLAYER_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_PLAYER_ID, id) }
        }
        return id
    }

    fun getPlayerName(): String {
        return prefs.getString(KEY_PLAYER_NAME, "Bạn") ?: "Bạn"
    }

    fun setPlayerName(name: String) {
        prefs.edit { putString(KEY_PLAYER_NAME, name.trim()) }
    }

    fun getLastMode(): String {
        return prefs.getString(KEY_LAST_MODE, MODE_AI) ?: MODE_AI
    }

    fun setLastMode(mode: String) {
        prefs.edit { putString(KEY_LAST_MODE, mode) }
    }

    fun getAIDifficulty(): AIDifficulty {
        val name = prefs.getString(KEY_AI_DIFFICULTY, AIDifficulty.MEDIUM.name)
        return runCatching { AIDifficulty.valueOf(name ?: AIDifficulty.MEDIUM.name) }
            .getOrDefault(AIDifficulty.MEDIUM)
    }

    fun setAIDifficulty(difficulty: AIDifficulty) {
        prefs.edit { putString(KEY_AI_DIFFICULTY, difficulty.name) }
    }

    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SOUND_ENABLED, enabled) }
    }

    fun isHapticEnabled(): Boolean {
        return prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
    }

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HAPTIC_ENABLED, enabled) }
    }

    fun recordGameFinished(isWin: Boolean) {
        val games = prefs.getInt(KEY_TOTAL_GAMES, 0) + 1
        val wins = prefs.getInt(KEY_TOTAL_WINS, 0) + if (isWin) 1 else 0
        prefs.edit {
            putInt(KEY_TOTAL_GAMES, games)
            putInt(KEY_TOTAL_WINS, wins)
        }
    }

    fun getTotalWins(): Int = prefs.getInt(KEY_TOTAL_WINS, 0)
    fun getTotalGames(): Int = prefs.getInt(KEY_TOTAL_GAMES, 0)
}

