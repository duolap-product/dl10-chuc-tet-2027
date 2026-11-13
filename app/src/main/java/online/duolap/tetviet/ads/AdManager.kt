package online.duolap.tetviet.ads

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import online.duolap.tetviet.BuildConfig
import online.duolap.tetviet.R
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.VipPreferences
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trình điều phối quảng cáo AdMob (Banner, Interstitial, Rewarded, App Open).
 * - Không bao giờ hiển thị quảng cáo cho tài khoản VIP.
 * - Chỉ khởi tạo SDK khi đã có sự đồng ý quảng cáo ([AdConsentManager]).
 * - Interstitial và App Open bị giới hạn tần suất bởi [AdPolicy]; App Open còn bị chặn bởi [AppOpenGate].
 * - Tự tải lại khi lỗi (backoff) và bỏ quảng cáo đã hết hạn.
 */
@Singleton
class AdManager @Inject constructor(
    @field:ApplicationContext private val context: Context,
    private val vipPreferences: VipPreferences,
    private val adPolicy: AdPolicy,
    private val consentManager: AdConsentManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isInitialized = false

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoadedAt = 0L
    private var isRewardedAdLoading = false
    private var rewardedRetryAttempt = 0
    private val retryRewardedLoad = Runnable { preloadRewardedAd() }

    /** Yêu cầu xem rewarded đang chờ quảng cáo tải xong (tối đa [REWARDED_WAIT_TIMEOUT_MS]). */
    private var pendingRewarded: PendingRewardedRequest? = null

    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoadedAt = 0L
    private var isInterstitialAdLoading = false
    private var interstitialRetryAttempt = 0
    private val retryInterstitialLoad = Runnable { preloadInterstitialAd() }

    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var isAppOpenAdLoading = false
    private var appOpenRetryAttempt = 0
    private val retryAppOpenLoad = Runnable { preloadAppOpenAd() }

    /** Đang có quảng cáo toàn màn hình trên màn hình (không hiện App Open chồng lên). */
    private var isShowingFullScreenAd = false

    private inner class PendingRewardedRequest(
        activity: Activity,
        val placement: AdPlacement,
        val onUserEarnedReward: () -> Unit,
        val onDismissed: () -> Unit,
        val onError: (String) -> Unit
    ) {
        val activityRef = WeakReference(activity)
        val timeout = Runnable {
            if (pendingRewarded === this) {
                pendingRewarded = null
                logAdEvent(EVENT_AD_SKIPPED, FORMAT_REWARDED, placement, "load_timeout")
                onError(context.getString(R.string.msg_ad_not_ready))
            }
        }
    }

    /**
     * Khởi tạo Google Mobile Ads SDK. Chỉ gọi sau khi [AdConsentManager.canRequestAds] là true.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        MobileAds.initialize(context) { status ->
            Log.i(TAG, "AdMob SDK Initialized: ${status.adapterStatusMap}")
            // Tải trước quảng cáo để sẵn sàng hiển thị khi người dùng yêu cầu
            preloadRewardedAd()
            preloadInterstitialAd()
            preloadAppOpenAd()
        }
    }

    /** Màn hình mở thẳng (không qua MainActivity) vẫn dùng được quảng cáo nếu đã có đồng ý từ trước. */
    private fun ensureInitialized(): Boolean {
        if (!isInitialized && consentManager.canRequestAds) {
            initialize()
        }
        return isInitialized
    }

    /**
     * Tải trước quảng cáo có thưởng (Rewarded Ad).
     */
    fun preloadRewardedAd() {
        if (!isInitialized || vipPreferences.isVip.value) return
        if (isRewardedAdLoading) return
        if (rewardedAd != null && isFresh(rewardedLoadedAt)) return

        rewardedAd = null
        isRewardedAdLoading = true
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.i(TAG, "Rewarded ad successfully loaded.")
                    rewardedAd = ad
                    rewardedLoadedAt = SystemClock.elapsedRealtime()
                    isRewardedAdLoading = false
                    rewardedRetryAttempt = 0
                    servePendingRewarded()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    isRewardedAdLoading = false
                    logLoadFailed(FORMAT_REWARDED, loadAdError)
                    failPendingRewarded()
                    scheduleRetry(retryRewardedLoad, rewardedRetryAttempt++)
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo có thưởng để mở khóa tính năng (Xem video mở khoá).
     * Nếu quảng cáo chưa sẵn sàng, chờ tối đa [REWARDED_WAIT_TIMEOUT_MS] rồi mới báo lỗi;
     * quá thời gian thì huỷ hẳn để video không tự bật lên sau đó.
     * @param activity Màn hình hiện tại để hiển thị quảng cáo
     * @param placement Vị trí quảng cáo (để đo hiệu quả)
     * @param onUserEarnedReward Callback được gọi khi người dùng xem hết quảng cáo và nhận thưởng
     * @param onDismissed Callback khi quảng cáo đóng (có thể đã nhận thưởng hoặc không)
     * @param onError Callback khi không thể hiển thị quảng cáo, kèm thông báo cho người dùng
     */
    fun showRewardedAd(
        activity: Activity,
        placement: AdPlacement,
        onUserEarnedReward: () -> Unit,
        onDismissed: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Nếu người dùng đã là VIP, lập tức trao quyền lợi mà không cần xem quảng cáo
        if (vipPreferences.isVip.value) {
            onUserEarnedReward()
            onDismissed()
            return
        }

        if (!ensureInitialized()) {
            logAdEvent(EVENT_AD_SKIPPED, FORMAT_REWARDED, placement, "no_consent")
            onError(context.getString(R.string.msg_ad_not_ready))
            return
        }

        val ad = takeFreshRewardedAd()
        if (ad != null) {
            presentRewardedAd(activity, ad, placement, onUserEarnedReward, onDismissed, onError)
            return
        }

        // Chưa có quảng cáo: tải ngay (bỏ qua thời gian chờ backoff) và chờ trong giới hạn
        pendingRewarded?.let { mainHandler.removeCallbacks(it.timeout) }
        val request = PendingRewardedRequest(activity, placement, onUserEarnedReward, onDismissed, onError)
        pendingRewarded = request
        mainHandler.postDelayed(request.timeout, REWARDED_WAIT_TIMEOUT_MS)
        mainHandler.removeCallbacks(retryRewardedLoad)
        preloadRewardedAd()
    }

    private fun servePendingRewarded() {
        val request = pendingRewarded ?: return
        pendingRewarded = null
        mainHandler.removeCallbacks(request.timeout)

        val activity = request.activityRef.get()
        val ad = takeFreshRewardedAd()
        if (activity == null || !canPresentOn(activity) || ad == null) {
            if (ad != null) {
                rewardedAd = ad
            }
            logAdEvent(EVENT_AD_SKIPPED, FORMAT_REWARDED, request.placement, "screen_left")
            request.onError(context.getString(R.string.msg_ad_not_ready))
            return
        }
        presentRewardedAd(
            activity, ad, request.placement,
            request.onUserEarnedReward, request.onDismissed, request.onError
        )
    }

    private fun failPendingRewarded() {
        val request = pendingRewarded ?: return
        pendingRewarded = null
        mainHandler.removeCallbacks(request.timeout)
        logAdEvent(EVENT_AD_SKIPPED, FORMAT_REWARDED, request.placement, "load_failed")
        request.onError(context.getString(R.string.msg_ad_not_ready))
    }

    private fun presentRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        placement: AdPlacement,
        onUserEarnedReward: () -> Unit,
        onDismissed: () -> Unit,
        onError: (String) -> Unit
    ) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
                adPolicy.recordRewardedShown()
                logAdEvent(EVENT_AD_SHOWN, FORMAT_REWARDED, placement)
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                Log.i(TAG, "Rewarded ad dismissed.")
                // Tải lại quảng cáo cho lần kế tiếp
                preloadRewardedAd()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd = false
                Log.e(TAG, "Failed to show rewarded ad: ${adError.message}")
                logAdEvent(EVENT_AD_SKIPPED, FORMAT_REWARDED, placement, "show_failed")
                preloadRewardedAd()
                onError(context.getString(R.string.msg_ad_not_ready))
            }
        }

        ad.show(activity) { rewardItem ->
            Log.i(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            logAdEvent(EVENT_AD_REWARD_EARNED, FORMAT_REWARDED, placement)
            onUserEarnedReward()
        }
    }

    private fun takeFreshRewardedAd(): RewardedAd? {
        val ad = rewardedAd
        rewardedAd = null
        return if (ad != null && isFresh(rewardedLoadedAt)) ad else null
    }

    /**
     * Tải trước quảng cáo xen kẽ (Interstitial Ad).
     */
    fun preloadInterstitialAd() {
        if (!isInitialized || vipPreferences.isVip.value) return
        if (isInterstitialAdLoading) return
        if (interstitialAd != null && isFresh(interstitialLoadedAt)) return

        interstitialAd = null
        isInterstitialAdLoading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.i(TAG, "Interstitial ad successfully loaded.")
                    interstitialAd = ad
                    interstitialLoadedAt = SystemClock.elapsedRealtime()
                    isInterstitialAdLoading = false
                    interstitialRetryAttempt = 0
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
                    interstitialAd = null
                    isInterstitialAdLoading = false
                    logLoadFailed(FORMAT_INTERSTITIAL, loadAdError)
                    scheduleRetry(retryInterstitialLoad, interstitialRetryAttempt++)
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo xen kẽ ở điểm dừng tự nhiên (hoàn thành tác vụ, chuyển cảnh).
     * [onDismissed] luôn được gọi, kể cả khi quảng cáo bị bỏ qua do [AdPolicy] hoặc chưa tải xong,
     * nên có thể đặt hành động tiếp theo của người dùng vào đó.
     */
    fun showInterstitialAd(
        activity: Activity,
        placement: AdPlacement,
        onDismissed: () -> Unit = {}
    ) {
        if (vipPreferences.isVip.value || !ensureInitialized()) {
            onDismissed()
            return
        }

        val decision = adPolicy.checkInterstitial()
        if (decision != AdPolicy.Decision.ALLOWED) {
            logAdEvent(EVENT_AD_SKIPPED, FORMAT_INTERSTITIAL, placement, decision.reason)
            onDismissed()
            return
        }

        if (!canPresentOn(activity)) {
            onDismissed()
            return
        }

        val ad = interstitialAd?.takeIf { isFresh(interstitialLoadedAt) }
        interstitialAd = null
        if (ad == null) {
            logAdEvent(EVENT_AD_SKIPPED, FORMAT_INTERSTITIAL, placement, "not_loaded")
            preloadInterstitialAd()
            onDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
                adPolicy.recordInterstitialShown()
                logAdEvent(EVENT_AD_SHOWN, FORMAT_INTERSTITIAL, placement)
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                preloadInterstitialAd()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd = false
                logAdEvent(EVENT_AD_SKIPPED, FORMAT_INTERSTITIAL, placement, "show_failed")
                preloadInterstitialAd()
                onDismissed()
            }
        }
        ad.show(activity)
    }

    /**
     * Tải trước quảng cáo App Open (hiện khi người dùng quay lại app).
     */
    fun preloadAppOpenAd() {
        if (!isInitialized || vipPreferences.isVip.value) return
        if (isAppOpenAdLoading) return
        if (appOpenAd != null && isFresh(appOpenLoadedAt, APP_OPEN_EXPIRY_MS)) return

        appOpenAd = null
        isAppOpenAdLoading = true
        AppOpenAd.load(
            context,
            BuildConfig.ADMOB_APP_OPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.i(TAG, "App open ad successfully loaded.")
                    appOpenAd = ad
                    appOpenLoadedAt = SystemClock.elapsedRealtime()
                    isAppOpenAdLoading = false
                    appOpenRetryAttempt = 0
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "App open ad failed to load: ${loadAdError.message}")
                    appOpenAd = null
                    isAppOpenAdLoading = false
                    logLoadFailed(FORMAT_APP_OPEN, loadAdError)
                    scheduleRetry(retryAppOpenLoad, appOpenRetryAttempt++)
                }
            }
        )
    }

    /**
     * Gọi khi app từ nền quay lại foreground ([activity] là màn hình đang mở lại).
     * Chỉ hiện App Open khi người dùng thực sự quay lại app: bỏ qua khi vừa chia sẻ/mua VIP/cập nhật
     * ([AppOpenGate]), khi mở từ widget/thông báo, khi đang có quảng cáo khác, và theo quy tắc của [AdPolicy].
     */
    fun onAppForeground(activity: Activity) {
        // Luôn "tiêu" các cờ bỏ qua, kể cả khi người dùng là VIP, để cờ không còn sót lại cho lần sau
        val launchedFromShortcut = activity.intent?.getBooleanExtra(AppOpenGate.EXTRA_SKIP_APP_OPEN, false) == true
        if (launchedFromShortcut) {
            // Màn hình này có thể được mở lại nhiều lần sau đó: chỉ bỏ qua đúng lần mở từ widget/thông báo
            activity.intent?.removeExtra(AppOpenGate.EXTRA_SKIP_APP_OPEN)
        }
        val skipReason = when {
            AppOpenGate.consumeSkip() -> "returned_from_external"
            launchedFromShortcut -> "launched_from_shortcut"
            isShowingFullScreenAd || activity.javaClass.name.startsWith(GMA_PACKAGE) -> "ad_on_screen"
            else -> null
        }

        if (vipPreferences.isVip.value || !ensureInitialized()) return
        if (skipReason != null) {
            logAdEvent(EVENT_AD_SKIPPED, FORMAT_APP_OPEN, AdPlacement.APP_OPEN_RESUME, skipReason)
            return
        }

        val decision = adPolicy.checkAppOpen()
        if (decision != AdPolicy.Decision.ALLOWED) {
            // Phiên đầu / khởi động nguội là chuyện bình thường, không cần ghi sự kiện mỗi lần mở app
            if (decision != AdPolicy.Decision.COLD_START) {
                logAdEvent(EVENT_AD_SKIPPED, FORMAT_APP_OPEN, AdPlacement.APP_OPEN_RESUME, decision.reason)
            }
            preloadAppOpenAd()
            return
        }

        val ad = appOpenAd?.takeIf { isFresh(appOpenLoadedAt, APP_OPEN_EXPIRY_MS) }
        appOpenAd = null
        if (ad == null) {
            logAdEvent(EVENT_AD_SKIPPED, FORMAT_APP_OPEN, AdPlacement.APP_OPEN_RESUME, "not_loaded")
            preloadAppOpenAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
                adPolicy.recordAppOpenShown()
                logAdEvent(EVENT_AD_SHOWN, FORMAT_APP_OPEN, AdPlacement.APP_OPEN_RESUME)
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                preloadAppOpenAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Failed to show app open ad: ${adError.code} ${adError.message}")
                isShowingFullScreenAd = false
                logAdEvent(EVENT_AD_SKIPPED, FORMAT_APP_OPEN, AdPlacement.APP_OPEN_RESUME, "show_failed")
                preloadAppOpenAd()
            }
        }
        showWhenProcessForeground(activity, ad, attempt = 0)
    }

    /**
     * SDK từ chối hiện App Open (lỗi 3 "app is not in foreground") nếu hệ thống chưa kịp nâng tiến trình lên
     * foreground, điều hay gặp trên Samsung dù màn hình đã onResume. Chờ thêm tối đa ~1 giây rồi mới show.
     */
    private fun showWhenProcessForeground(activity: Activity, ad: AppOpenAd, attempt: Int) {
        if (activity.isFinishing || activity.isDestroyed) {
            // Người dùng đã rời màn hình: giữ lại quảng cáo cho lần quay lại sau
            appOpenAd = ad
            return
        }
        if (isProcessForeground() || attempt >= FOREGROUND_WAIT_ATTEMPTS) {
            ad.show(activity)
            return
        }
        mainHandler.postDelayed({ showWhenProcessForeground(activity, ad, attempt + 1) }, FOREGROUND_WAIT_STEP_MS)
    }

    private fun isProcessForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /**
     * Gắn banner thích ứng (anchored adaptive) vào [container] ở cuối màn hình.
     * Chiều cao banner được giữ chỗ trước khi tải để nội dung không bị nhảy (tránh bấm nhầm).
     * Banner tự pause/resume/destroy theo vòng đời [activity] và tự gỡ khi người dùng lên VIP.
     */
    fun attachBanner(activity: ComponentActivity, container: ViewGroup, placement: AdPlacement) {
        if (vipPreferences.isVip.value || !ensureInitialized()) return

        container.post {
            if (activity.isFinishing || activity.isDestroyed) return@post

            val density = activity.resources.displayMetrics.density
            val widthPx = container.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, (widthPx / density).toInt())

            val adView = AdView(activity).apply {
                adUnitId = bannerAdUnitId(placement)
                setAdSize(adSize)
                adListener = object : AdListener() {
                    override fun onAdImpression() {
                        logAdEvent(EVENT_AD_SHOWN, FORMAT_BANNER, placement)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(TAG, "Banner failed to load: ${loadAdError.message}")
                        logLoadFailed(FORMAT_BANNER, loadAdError)
                    }
                }
            }
            container.addView(
                adView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    adSize.getHeightInPixels(activity),
                    Gravity.CENTER_HORIZONTAL
                )
            )
            adView.loadAd(AdRequest.Builder().build())

            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    // Người dùng vừa mua VIP (từ VipActivity quay lại) -> gỡ banner
                    if (vipPreferences.isVip.value) {
                        container.removeView(adView)
                        adView.destroy()
                        owner.lifecycle.removeObserver(this)
                    } else {
                        adView.resume()
                    }
                }

                override fun onPause(owner: LifecycleOwner) {
                    adView.pause()
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    adView.destroy()
                }
            })
        }
    }

    private fun canPresentOn(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val lifecycleOwner = activity as? LifecycleOwner ?: return true
        return lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private fun isFresh(loadedAt: Long, expiryMs: Long = AD_EXPIRY_MS): Boolean {
        return SystemClock.elapsedRealtime() - loadedAt < expiryMs
    }

    private fun scheduleRetry(retry: Runnable, attempt: Int) {
        val delay = RETRY_DELAYS_MS[attempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
        mainHandler.removeCallbacks(retry)
        mainHandler.postDelayed(retry, delay)
    }

    /** Mỗi vị trí banner một ad unit riêng để xem doanh thu theo từng màn hình trên AdMob. */
    private fun bannerAdUnitId(placement: AdPlacement): String = when (placement) {
        AdPlacement.BANNER_TET_CUSTOMS -> BuildConfig.ADMOB_BANNER_TET_CUSTOMS_ID
        AdPlacement.BANNER_TET_PHOTOS -> BuildConfig.ADMOB_BANNER_TET_PHOTOS_ID
        AdPlacement.BANNER_MUSIC_SELECTION -> BuildConfig.ADMOB_BANNER_MUSIC_SELECTION_ID
        else -> BuildConfig.ADMOB_BANNER_ID
    }

    private fun logLoadFailed(format: String, error: LoadAdError) {
        AppAnalytics.logEvent(EVENT_AD_LOAD_FAILED, Bundle().apply {
            putString(PARAM_FORMAT, format)
            putInt(PARAM_ERROR_CODE, error.code)
        })
    }

    private fun logAdEvent(event: String, format: String, placement: AdPlacement, reason: String? = null) {
        AppAnalytics.logEvent(event, Bundle().apply {
            putString(PARAM_FORMAT, format)
            putString(PARAM_PLACEMENT, placement.key)
            reason?.let { putString(PARAM_REASON, it) }
        })
    }

    companion object {
        private const val TAG = "AdManager"

        private const val REWARDED_WAIT_TIMEOUT_MS = 5_000L

        // Quảng cáo AdMob hết hạn sau 1 giờ kể từ lúc tải (App Open: 4 giờ); chừa 5 phút an toàn
        private const val AD_EXPIRY_MS = 55 * 60_000L
        private const val APP_OPEN_EXPIRY_MS = 4 * 60 * 60_000L - 5 * 60_000L
        private const val GMA_PACKAGE = "com.google.android.gms.ads"
        private const val FOREGROUND_WAIT_STEP_MS = 100L
        private const val FOREGROUND_WAIT_ATTEMPTS = 10
        private val RETRY_DELAYS_MS = longArrayOf(10_000L, 30_000L, 60_000L, 120_000L)

        private const val EVENT_AD_SHOWN = "ad_shown"
        private const val EVENT_AD_SKIPPED = "ad_skipped"
        private const val EVENT_AD_REWARD_EARNED = "ad_reward_earned"
        private const val EVENT_AD_LOAD_FAILED = "ad_load_failed"
        private const val PARAM_FORMAT = "ad_format"
        private const val PARAM_PLACEMENT = "placement"
        private const val PARAM_REASON = "reason"
        private const val PARAM_ERROR_CODE = "error_code"
        private const val FORMAT_REWARDED = "rewarded"
        private const val FORMAT_INTERSTITIAL = "interstitial"
        private const val FORMAT_BANNER = "banner"
        private const val FORMAT_APP_OPEN = "app_open"
    }
}
