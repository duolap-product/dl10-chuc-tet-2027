package online.duolap.tetviet.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import online.duolap.tetviet.analytics.AppCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xin sự đồng ý quảng cáo qua Google UMP (bắt buộc với người dùng EEA/UK).
 * Chỉ được khởi tạo AdMob và tải quảng cáo khi [canRequestAds] trả về true.
 */
@Singleton
class AdConsentManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    /** Người dùng ở vùng bắt buộc phải có lối vào "Tùy chọn quyền riêng tư" trong app. */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Cập nhật trạng thái đồng ý và hiện form nếu cần. [onComplete] luôn được gọi đúng 1 lần.
     */
    fun gatherConsent(activity: Activity, onComplete: (canRequestAds: Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { logError("loadAndShowConsentForm", it) }
                    onComplete(canRequestAds)
                }
            },
            { requestError ->
                logError("requestConsentInfoUpdate", requestError)
                onComplete(canRequestAds)
            }
        )
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            formError?.let { logError("showPrivacyOptionsForm", it) }
            onDismissed()
        }
    }

    private fun logError(step: String, error: FormError) {
        Log.w(TAG, "$step failed: ${error.errorCode} ${error.message}")
        AppCrashlytics.log("UMP $step lỗi: ${error.errorCode} ${error.message}")
    }

    companion object {
        private const val TAG = "AdConsentManager"
    }
}
