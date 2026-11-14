package online.duolap.tetviet.ads

import online.duolap.tetviet.ads.AdPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AdPolicyTest {

    private class InMemoryStore : AdPolicy.Store {
        val values = mutableMapOf<String, Long>()
        override fun getLong(key: String, default: Long): Long = values[key] ?: default
        override fun putLong(key: String, value: Long) {
            values[key] = value
        }
    }

    private lateinit var store: InMemoryStore
    private var currentTime = 0L

    private fun newPolicy() = AdPolicy(store, now = { currentTime })

    private fun advance(millis: Long) {
        currentTime += millis
    }

    /** Tạo người dùng đã qua phiên đầu tiên, đang ở phiên thứ 2. */
    private fun returningUserPolicy(): AdPolicy {
        val policy = newPolicy()
        policy.onAppForeground()
        policy.onAppBackground()
        advance(TimeUnit.MINUTES.toMillis(31))
        policy.onAppForeground()
        return policy
    }

    private fun showInterstitialAndWait(policy: AdPolicy) {
        assertEquals(Decision.ALLOWED, policy.checkInterstitial())
        policy.recordInterstitialShown()
        advance(TimeUnit.SECONDS.toMillis(61))
    }

    @Before
    fun setUp() {
        store = InMemoryStore()
        // 08:00 sáng để các bài test trong ngày không vắt qua nửa đêm
        currentTime = Calendar.getInstance().apply {
            set(2027, Calendar.JANUARY, 20, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun firstSessionIsAdFree() {
        val policy = newPolicy()
        policy.onAppForeground()
        assertEquals(Decision.FIRST_SESSION, policy.checkInterstitial())
    }

    @Test
    fun firstSessionAllowsInterstitialAfterGracePeriod() {
        val policy = newPolicy()
        policy.onAppForeground()
        advance(TimeUnit.SECONDS.toMillis(170))
        assertEquals(Decision.FIRST_SESSION, policy.checkInterstitial())
        advance(TimeUnit.SECONDS.toMillis(11))
        assertEquals(Decision.ALLOWED, policy.checkInterstitial())
    }

    @Test
    fun shortBackgroundDoesNotStartNewSession() {
        val policy = newPolicy()
        policy.onAppForeground()
        policy.onAppBackground()
        advance(TimeUnit.MINUTES.toMillis(1))
        policy.onAppForeground()
        assertEquals(Decision.FIRST_SESSION, policy.checkInterstitial())
    }

    @Test
    fun secondSessionAllowsInterstitial() {
        assertEquals(Decision.ALLOWED, returningUserPolicy().checkInterstitial())
    }

    @Test
    fun coldStartAfterProcessDeathCountsAsNewSession() {
        newPolicy().onAppForeground()
        val restarted = newPolicy()
        restarted.onAppForeground()
        assertEquals(Decision.ALLOWED, restarted.checkInterstitial())
    }

    @Test
    fun minimumIntervalBetweenInterstitials() {
        val policy = returningUserPolicy()
        policy.recordInterstitialShown()
        advance(TimeUnit.SECONDS.toMillis(50))
        assertEquals(Decision.TOO_SOON, policy.checkInterstitial())
        advance(TimeUnit.SECONDS.toMillis(11))
        assertEquals(Decision.ALLOWED, policy.checkInterstitial())
    }

    @Test
    fun cooldownAfterRewarded() {
        val policy = returningUserPolicy()
        policy.recordRewardedShown()
        advance(TimeUnit.SECONDS.toMillis(50))
        assertEquals(Decision.AFTER_REWARDED, policy.checkInterstitial())
        advance(TimeUnit.SECONDS.toMillis(11))
        assertEquals(Decision.ALLOWED, policy.checkInterstitial())
    }

    @Test
    fun sessionCapResetsInNewSession() {
        val policy = returningUserPolicy()
        repeat(6) { showInterstitialAndWait(policy) }
        assertEquals(Decision.SESSION_CAP, policy.checkInterstitial())

        policy.onAppBackground()
        advance(TimeUnit.MINUTES.toMillis(31))
        policy.onAppForeground()
        assertEquals(Decision.ALLOWED, policy.checkInterstitial())
    }

    @Test
    fun dailyCapResetsNextDay() {
        val policy = returningUserPolicy()
        repeat(3) {
            repeat(5) { showInterstitialAndWait(policy) }
            policy.onAppBackground()
            advance(TimeUnit.MINUTES.toMillis(31))
            policy.onAppForeground()
        }
        assertEquals(Decision.DAILY_CAP, policy.checkInterstitial())

        advance(TimeUnit.DAYS.toMillis(1))
        assertEquals(Decision.ALLOWED, policy.checkInterstitial())
    }

    @Test
    fun intervalSurvivesProcessRestart() {
        val policy = returningUserPolicy()
        policy.recordInterstitialShown()
        advance(TimeUnit.SECONDS.toMillis(30))

        val restarted = newPolicy()
        restarted.onAppForeground()
        assertEquals(Decision.TOO_SOON, restarted.checkInterstitial())
    }

    /** Phiên thứ 2, vừa quay lại từ nền sau [backgroundMs]. */
    private fun returnFromBackground(policy: AdPolicy, backgroundMs: Long) {
        policy.onAppBackground()
        advance(backgroundMs)
        policy.onAppForeground()
    }

    @Test
    fun appOpenNotOnColdStart() {
        newPolicy().onAppForeground()
        val restarted = newPolicy()
        restarted.onAppForeground()
        assertEquals(Decision.COLD_START, restarted.checkAppOpen())
    }

    @Test
    fun appOpenNotInFirstSession() {
        val policy = newPolicy()
        policy.onAppForeground()
        returnFromBackground(policy, TimeUnit.MINUTES.toMillis(5))
        assertEquals(Decision.FIRST_SESSION, policy.checkAppOpen())
    }

    @Test
    fun appOpenNeedsEnoughTimeInBackground() {
        val policy = returningUserPolicy()
        returnFromBackground(policy, TimeUnit.SECONDS.toMillis(10))
        assertEquals(Decision.BACKGROUND_TOO_SHORT, policy.checkAppOpen())

        returnFromBackground(policy, TimeUnit.SECONDS.toMillis(31))
        assertEquals(Decision.ALLOWED, policy.checkAppOpen())
    }

    @Test
    fun appOpenAtMostOncePerSixHours() {
        val policy = returningUserPolicy()
        assertEquals(Decision.ALLOWED, policy.checkAppOpen())
        policy.recordAppOpenShown()

        returnFromBackground(policy, TimeUnit.HOURS.toMillis(5))
        assertEquals(Decision.APP_OPEN_COOLDOWN, policy.checkAppOpen())

        returnFromBackground(policy, TimeUnit.HOURS.toMillis(1))
        assertEquals(Decision.ALLOWED, policy.checkAppOpen())
    }

    @Test
    fun interstitialWaitsAfterAppOpen() {
        val policy = returningUserPolicy()
        policy.recordAppOpenShown()
        advance(TimeUnit.SECONDS.toMillis(30))
        assertEquals(Decision.TOO_SOON, policy.checkInterstitial())
    }

    @Test
    fun appOpenWaitsAfterInterstitial() {
        val policy = returningUserPolicy()
        policy.recordInterstitialShown()
        returnFromBackground(policy, TimeUnit.SECONDS.toMillis(40))
        assertEquals(Decision.TOO_SOON, policy.checkAppOpen())
    }

    @Test
    fun appOpenNotRightAfterRewarded() {
        val policy = returningUserPolicy()
        policy.recordRewardedShown()
        returnFromBackground(policy, TimeUnit.SECONDS.toMillis(40))
        assertEquals(Decision.AFTER_REWARDED, policy.checkAppOpen())
    }
}
