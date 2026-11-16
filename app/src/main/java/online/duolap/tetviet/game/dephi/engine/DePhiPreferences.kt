package online.duolap.tetviet.game.dephi.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Kỷ lục và số ván của "Cùng Dê Phi Đón Tết". */
class DePhiPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var bestMeters: Int
        get() = prefs.getInt(KEY_BEST_METERS, 0)
        set(value) = prefs.edit { putInt(KEY_BEST_METERS, value) }

    var bestScore: Int
        get() = prefs.getInt(KEY_BEST_SCORE, 0)
        set(value) = prefs.edit { putInt(KEY_BEST_SCORE, value) }

    var totalGames: Int
        get() = prefs.getInt(KEY_TOTAL_GAMES, 0)
        set(value) = prefs.edit { putInt(KEY_TOTAL_GAMES, value) }

    /** Id các trang phục dê đã mua (trang phục mặc định luôn có, không cần lưu). */
    var ownedSkins: Set<String>
        get() = prefs.getStringSet(KEY_OWNED_SKINS, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_OWNED_SKINS, value.toSet()) }

    var selectedSkin: String?
        get() = prefs.getString(KEY_SELECTED_SKIN, null)
        set(value) = prefs.edit { putString(KEY_SELECTED_SKIN, value) }

        private companion object {
        const val PREFS_NAME = "de_phi_prefs"
        const val KEY_BEST_METERS = "best_meters"
        const val KEY_BEST_SCORE = "best_score"
        const val KEY_TOTAL_GAMES = "total_games"
        const val KEY_OWNED_SKINS = "owned_skins"
        const val KEY_SELECTED_SKIN = "selected_skin"
    }
}
