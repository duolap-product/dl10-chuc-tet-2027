package online.duolap.tetviet.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import online.duolap.tetviet.data.local.VipPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trình điều phối quảng cáo AdMob (Banner, Interstitial, Rewarded Ads)
 * Tích hợp tự động với cơ chế kiểm tra VIP (không bao giờ hiển thị quảng cáo cho tài khoản VIP).
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vipPreferences: VipPreferences
) {
    private var rewardedAd: RewardedAd? = null
    private var isRewardedAdLoading = false

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialAdLoading = false

    /**
     * Khởi tạo Google Mobile Ads SDK.
     */
    fun initialize() {
        MobileAds.initialize(context) { status ->
            Log.i(TAG, "AdMob SDK Initialized: ${status.adapterStatusMap}")
            // Tải trước quảng cáo để sẵn sàng hiển thị khi người dùng yêu cầu
            if (!vipPreferences.isVip.value) {
                preloadRewardedAd()
                preloadInterstitialAd()
            }
        }
    }

    /**
     * Tải trước quảng cáo có thưởng (Rewarded Ad).
     */
    fun preloadRewardedAd(onLoaded: (() -> Unit)? = null) {
        if (vipPreferences.isVip.value) return
        if (rewardedAd != null || isRewardedAdLoading) return

        isRewardedAdLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            AD_UNIT_REWARDED_TEST,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.i(TAG, "Rewarded ad successfully loaded.")
                    rewardedAd = ad
                    isRewardedAdLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    isRewardedAdLoading = false
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo có thưởng để mở khóa tính năng (Xem video mở khoá).
     * @param activity Màn hình hiện tại để hiển thị quảng cáo
     * @param onUserEarnedReward Callback được gọi khi người dùng xem hết quảng cáo và nhận thưởng
     * @param onDismissed Callback khi quảng cáo đóng (có thể đã nhận thưởng hoặc không)
     * @param onError Callback khi xảy ra lỗi không thể hiển thị quảng cáo
     */
    fun showRewardedAd(
        activity: Activity,
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

        val ad = rewardedAd
        if (ad != null) {
            var rewardGranted = false

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.i(TAG, "Rewarded ad dismissed.")
                    rewardedAd = null
                    // Tải lại quảng cáo cho lần kế tiếp
                    preloadRewardedAd()
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Failed to show rewarded ad: ${adError.message}")
                    rewardedAd = null
                    preloadRewardedAd()
                    onError(adError.message)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.i(TAG, "Rewarded ad showed fullscreen.")
                }
            }

            ad.show(activity) { rewardItem ->
                Log.i(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                rewardGranted = true
                onUserEarnedReward()
            }
        } else {
            // Nếu chưa tải xong, thử tải ngay
            preloadRewardedAd {
                // Tải xong thì thử hiển thị lại nếu activity còn hợp lệ
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showRewardedAd(activity, onUserEarnedReward, onDismissed, onError)
                }
            }
            onError("Đang chuẩn bị video quảng cáo, vui lòng thử lại sau vài giây.")
        }
    }

    /**
     * Tải trước quảng cáo xen kẽ (Interstitial Ad).
     */
    fun preloadInterstitialAd() {
        if (vipPreferences.isVip.value) return
        if (interstitialAd != null || isInterstitialAdLoading) return

        isInterstitialAdLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            AD_UNIT_INTERSTITIAL_TEST,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.i(TAG, "Interstitial ad successfully loaded.")
                    interstitialAd = ad
                    isInterstitialAdLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
                    interstitialAd = null
                    isInterstitialAdLoading = false
                }
            }
        )
    }

    /**
     * Hiển thị quảng cáo xen kẽ khi chuyển cảnh hoặc hoàn thành tác vụ.
     */
    fun showInterstitialAd(
        activity: Activity,
        onDismissed: () -> Unit = {}
    ) {
        if (vipPreferences.isVip.value) {
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitialAd()
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    preloadInterstitialAd()
                    onDismissed()
                }
            }
            ad.show(activity)
        } else {
            preloadInterstitialAd()
            onDismissed()
        }
    }

    companion object {
        private const val TAG = "AdManager"

        // Mã vị trí quảng cáo AdMob chính thức của Google dành cho Test / Development
        const val AD_UNIT_REWARDED_TEST = "ca-app-pub-3940256099942544/5224354917"
        const val AD_UNIT_INTERSTITIAL_TEST = "ca-app-pub-3940256099942544/1033173712"
        const val AD_UNIT_BANNER_TEST = "ca-app-pub-3940256099942544/6300978111"
    }
}

