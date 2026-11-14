package online.duolap.tetviet.ads

import android.content.SharedPreferences
import java.util.Calendar

/**
 * Quy tắc tần suất quảng cáo toàn màn hình (Interstitial, App Open) dùng chung cho toàn app.
 * App Open và Interstitial dùng chung khoảng cách tối thiểu giữa 2 quảng cáo toàn màn hình.
 *
 * Rewarded do người dùng chủ động chọn xem nên luôn được phép, nhưng vẫn được ghi nhận
 * để không bắn thêm interstitial ngay sau khi người dùng vừa xem video.
 */
class AdPolicy(
    private val store: Store,
    private val now: () -> Long = System::currentTimeMillis,
    private val config: Config = Config()
) {

    data class Config(
        val minIntervalBetweenInterstitialsMs: Long = 60_000L,
        val cooldownAfterRewardedMs: Long = 60_000L,
        val maxInterstitialsPerSession: Int = 6,
        val maxInterstitialsPerDay: Int = 15,
        // Cùng cách tính phiên với Firebase Analytics: ở nền quá 30 phút thì tính là phiên mới
        val sessionTimeoutMs: Long = 30 * 60_000L,
        val firstSessionAdFree: Boolean = true,
        // Người dùng mới: không có interstitial trong vài phút đầu kể từ lần mở app đầu tiên.
        // Sau đó interstitial được phép ngay cả trong phiên đầu (App Open vẫn chờ tới phiên 2).
        val newUserInterstitialGraceMs: Long = 3 * 60_000L,
        // App Open: tối đa 1 lần mỗi 6 giờ, chỉ khi quay lại sau khi ở nền đủ lâu
        val appOpenCooldownMs: Long = 6 * 60 * 60_000L,
        val minBackgroundForAppOpenMs: Long = 30_000L
    )

    enum class Decision(val reason: String) {
        ALLOWED("allowed"),
        FIRST_SESSION("first_session"),
        SESSION_CAP("session_cap"),
        DAILY_CAP("daily_cap"),
        TOO_SOON("too_soon"),
        AFTER_REWARDED("after_rewarded"),
        COLD_START("cold_start"),
        BACKGROUND_TOO_SHORT("background_too_short"),
        APP_OPEN_COOLDOWN("app_open_cooldown")
    }

    interface Store {
        fun getLong(key: String, default: Long): Long
        fun putLong(key: String, value: Long)
    }

    class SharedPrefsStore(private val prefs: SharedPreferences) : Store {
        override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
        override fun putLong(key: String, value: Long) {
            prefs.edit().putLong(key, value).apply()
        }
    }

    private var sessionStarted = false
    private var lastBackgroundAt = 0L
    private var interstitialsThisSession = 0

    // Lần vào foreground gần nhất: quay lại từ nền (warm) hay khởi động nguội, và đã ở nền bao lâu
    private var lastForegroundWasWarm = false
    private var lastBackgroundDurationMs = 0L

    /** Gọi khi app từ nền quay lại foreground (hoặc khởi động nguội). */
    fun onAppForeground() {
        val currentTime = now()
        lastForegroundWasWarm = lastBackgroundAt > 0L
        lastBackgroundDurationMs = if (lastForegroundWasWarm) currentTime - lastBackgroundAt else 0L
        if (!sessionStarted || currentTime - lastBackgroundAt >= config.sessionTimeoutMs) {
            sessionStarted = true
            interstitialsThisSession = 0
            store.putLong(KEY_SESSION_COUNT, store.getLong(KEY_SESSION_COUNT, 0L) + 1)
        }
        if (store.getLong(KEY_FIRST_OPEN_AT, 0L) == 0L) {
            store.putLong(KEY_FIRST_OPEN_AT, currentTime)
        }
    }

    /** Gọi khi toàn bộ app chuyển vào nền. */
    fun onAppBackground() {
        lastBackgroundAt = now()
    }

    fun checkInterstitial(): Decision {
        val currentTime = now()

        val firstOpenAt = store.getLong(KEY_FIRST_OPEN_AT, 0L)
        if (config.firstSessionAdFree && store.getLong(KEY_SESSION_COUNT, 0L) <= 1L &&
            (firstOpenAt == 0L || currentTime - firstOpenAt < config.newUserInterstitialGraceMs)
        ) {
            return Decision.FIRST_SESSION
        }
        if (interstitialsThisSession >= config.maxInterstitialsPerSession) {
            return Decision.SESSION_CAP
        }
        if (interstitialsToday(currentTime) >= config.maxInterstitialsPerDay) {
            return Decision.DAILY_CAP
        }
        return checkFullScreenSpacing(currentTime)
    }

    /** App Open khi quay lại app: không khi khởi động nguội, không trong phiên đầu, tối đa 1 lần / [Config.appOpenCooldownMs]. */
    fun checkAppOpen(): Decision {
        val currentTime = now()

        if (config.firstSessionAdFree && store.getLong(KEY_SESSION_COUNT, 0L) <= 1L) {
            return Decision.FIRST_SESSION
        }
        if (!lastForegroundWasWarm) {
            return Decision.COLD_START
        }
        if (lastBackgroundDurationMs < config.minBackgroundForAppOpenMs) {
            return Decision.BACKGROUND_TOO_SHORT
        }
        val lastAppOpenAt = store.getLong(KEY_LAST_APP_OPEN_AT, 0L)
        if (lastAppOpenAt > 0L && currentTime - lastAppOpenAt < config.appOpenCooldownMs) {
            return Decision.APP_OPEN_COOLDOWN
        }
        return checkFullScreenSpacing(currentTime)
    }

    /** Khoảng cách tối thiểu với quảng cáo toàn màn hình trước đó (Interstitial/App Open) và với Rewarded. */
    private fun checkFullScreenSpacing(currentTime: Long): Decision {
        val lastFullScreenAt = maxOf(
            store.getLong(KEY_LAST_INTERSTITIAL_AT, 0L),
            store.getLong(KEY_LAST_APP_OPEN_AT, 0L)
        )
        if (lastFullScreenAt > 0L && currentTime - lastFullScreenAt < config.minIntervalBetweenInterstitialsMs) {
            return Decision.TOO_SOON
        }
        val lastRewardedAt = store.getLong(KEY_LAST_REWARDED_AT, 0L)
        if (lastRewardedAt > 0L && currentTime - lastRewardedAt < config.cooldownAfterRewardedMs) {
            return Decision.AFTER_REWARDED
        }
        return Decision.ALLOWED
    }

    fun recordInterstitialShown() {
        val currentTime = now()
        interstitialsThisSession++
        store.putLong(KEY_DAILY_COUNT, interstitialsToday(currentTime) + 1)
        store.putLong(KEY_DAILY_DAY, dayKey(currentTime))
        store.putLong(KEY_LAST_INTERSTITIAL_AT, currentTime)
    }

    fun recordAppOpenShown() {
        store.putLong(KEY_LAST_APP_OPEN_AT, now())
    }

    fun recordRewardedShown() {
        store.putLong(KEY_LAST_REWARDED_AT, now())
    }

    private fun interstitialsToday(currentTime: Long): Long {
        return if (store.getLong(KEY_DAILY_DAY, -1L) == dayKey(currentTime)) {
            store.getLong(KEY_DAILY_COUNT, 0L)
        } else {
            0L
        }
    }

    private fun dayKey(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        const val PREFS_NAME = "ad_policy_prefs"

        private const val KEY_SESSION_COUNT = "session_count"
        private const val KEY_DAILY_COUNT = "interstitial_daily_count"
        private const val KEY_DAILY_DAY = "interstitial_daily_day"
        private const val KEY_LAST_INTERSTITIAL_AT = "last_interstitial_at"
        private const val KEY_LAST_REWARDED_AT = "last_rewarded_at"
        private const val KEY_LAST_APP_OPEN_AT = "last_app_open_at"
        private const val KEY_FIRST_OPEN_AT = "first_open_at"
    }
}
