package online.duolap.tetviet.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayUpdateManager @Inject constructor(
    val appUpdateManager: AppUpdateManager
) {
    /**
     * Cờ đánh dấu đã kiểm tra trong phiên hiện tại (tránh spam người dùng khi quay lại Home)
     */
    var hasCheckedThisSession: Boolean = false
        private set

    /**
     * Cache thông tin cập nhật mới nhất từ Google Play
     */
    var latestAppUpdateInfo: AppUpdateInfo? = null
        private set

    /**
     * Kiểm tra phiên bản mới từ Google Play Core API
     *
     * @param forceCheck Bỏ qua cờ session để bắt buộc kiểm tra lại (VD khi user bấm kiểm tra trong Cài đặt)
     * @param onUpdateAvailable Callback khi Google Play báo có phiên bản mới
     * @param onNoUpdate Callback khi ứng dụng đang ở phiên bản mới nhất
     * @param onError Callback khi gặp lỗi (chưa publish, thiết bị không có Play Services, không có mạng...)
     */
    fun checkForUpdate(
        forceCheck: Boolean = false,
        onUpdateAvailable: (AppUpdateInfo) -> Unit,
        onNoUpdate: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        if (!forceCheck && hasCheckedThisSession) {
            return
        }

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                hasCheckedThisSession = true
                val availability = appUpdateInfo.updateAvailability()
                if (availability == UpdateAvailability.UPDATE_AVAILABLE ||
                    availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    latestAppUpdateInfo = appUpdateInfo
                    AppAnalytics.logFeatureClick("play_update_available")
                    onUpdateAvailable(appUpdateInfo)
                } else {
                    latestAppUpdateInfo = null
                    onNoUpdate?.invoke()
                }
            }
            .addOnFailureListener { exception ->
                hasCheckedThisSession = true
                AppCrashlytics.log("PlayUpdateManager check failed: ${exception.message}")
                onError?.invoke(exception)
            }
    }

    /**
     * Bắt đầu luồng cập nhật Google Play In-App Updates (Immediate hoặc Flexible)
     */
    fun startUpdateFlow(
        activity: Activity,
        appUpdateInfo: AppUpdateInfo,
        updateType: Int = AppUpdateType.IMMEDIATE,
        requestCode: Int = REQUEST_CODE_IN_APP_UPDATE
    ): Boolean {
        if (!appUpdateInfo.isUpdateTypeAllowed(updateType)) {
            return false
        }
        return try {
            val options = AppUpdateOptions.newBuilder(updateType).build()
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, activity, options, requestCode)
            true
        } catch (e: Exception) {
            AppCrashlytics.log("PlayUpdateManager startUpdateFlow error: ${e.message}")
            false
        }
    }

    companion object {
        const val REQUEST_CODE_IN_APP_UPDATE = 8899
    }
}

