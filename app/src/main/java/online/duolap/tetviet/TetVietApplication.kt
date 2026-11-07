package online.duolap.tetviet

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.audio.TetMusicManager
import online.duolap.tetviet.billing.BillingManager
import online.duolap.tetviet.data.local.AppDatabase
import online.duolap.tetviet.data.sync.FirestoreSyncManager
import online.duolap.tetviet.data.sync.FirestoreSyncManager.Companion.COLLECTION_CARD_COLORS
import online.duolap.tetviet.data.sync.FirestoreSyncManager.Companion.COLLECTION_CARD_MAKER
import javax.inject.Inject

@HiltAndroidApp
class TetVietApplication : Application(), Application.ActivityLifecycleCallbacks {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivityCount = 0

    @Inject
    lateinit var syncManager: FirestoreSyncManager

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()

        AppCrashlytics.init()
        AppAnalytics.init(this)
        AppCrashlytics.log("Ứng dụng Tết Việt khởi động")

        AppDatabase.getInstance(this)

        adManager.initialize()
        billingManager.initializeBillingClient()

        // Khởi tạo và đăng ký giám sát vòng đời phát nhạc toàn ứng dụng
        TetMusicManager.init(this)
        registerActivityLifecycleCallbacks(this)

        applicationScope.launch {
            try {
                val result = syncManager.syncData(COLLECTION_CARD_MAKER)
                result.onSuccess { summary ->
                    if (summary.isSkipped) {
                        Log.i("TetVietApp", "Auto sync 1 time/day: ${summary.message}")
                    } else {
                        Log.i("TetVietApp", "Auto sync completed: ${summary.message}")
                    }
                }.onFailure { err ->
                    Log.w("TetVietApp", "Auto sync warning on startup: ${err.message}")
                }

                val colorResult = syncManager.syncData(COLLECTION_CARD_COLORS)
                colorResult.onSuccess { summary ->
                    if (!summary.isSkipped) {
                        Log.i("TetVietApp", "Auto sync colors completed: ${summary.message}")
                    }
                }
            } catch (e: Exception) {
                AppCrashlytics.recordException(e, "TetVietApp.startupSync")
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        if (startedActivityCount == 1) {
            // App chuyển từ background vào foreground -> tiếp tục phát nhạc nếu người dùng bật
            TetMusicManager.onAppForeground(this)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount <= 0) {
            startedActivityCount = 0
            // App chuyển hoàn toàn vào background -> tạm dừng phát nhạc để tiết kiệm pin
            TetMusicManager.onAppBackground()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
