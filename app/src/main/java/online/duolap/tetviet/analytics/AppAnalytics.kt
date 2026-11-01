package online.duolap.tetviet.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Helper quản lý Firebase Analytics tập trung.
 * Ghi nhận các sự kiện: mở màn hình, đồng bộ Firestore, quay lì xì, click tính năng.
 */
object AppAnalytics {

    private const val TAG = "AppAnalytics"
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            Log.d(TAG, "Firebase Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics", e)
        }
    }

    /**
     * Ghi nhận màn hình người dùng đang xem
     */
    fun logScreenView(screenName: String, screenClass: String = screenName) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "ScreenView logged: $screenName")
        } catch (_: Exception) {}
    }

    /**
     * Ghi nhận sự kiện đồng bộ Firestore vào Room
     */
    fun logSyncEvent(success: Boolean, count: Int, isIncremental: Boolean, errorMsg: String? = null) {
        try {
            val bundle = Bundle().apply {
                putBoolean("sync_success", success)
                putInt("items_synced", count)
                putBoolean("is_incremental", isIncremental)
                errorMsg?.let { putString("error_message", it.take(100)) }
            }
            firebaseAnalytics?.logEvent("firestore_sync_completed", bundle)
            Log.d(TAG, "SyncEvent logged: success=$success, count=$count, incremental=$isIncremental")
        } catch (_: Exception) {}
    }

    /**
     * Ghi nhận sự kiện quay vòng quay may mắn
     */
    fun logSpinWheel(prizeText: String) {
        try {
            val bundle = Bundle().apply {
                putString("prize_won", prizeText)
                putLong("timestamp", System.currentTimeMillis())
            }
            firebaseAnalytics?.logEvent("lucky_wheel_spin", bundle)
            Log.d(TAG, "SpinWheel logged: prize=$prizeText")
        } catch (_: Exception) {}
    }

    /**
     * Ghi nhận sự kiện người dùng bấm vào các tính năng chính
     */
    fun logFeatureClick(featureName: String) {
        try {
            val bundle = Bundle().apply {
                putString("feature_name", featureName)
            }
            firebaseAnalytics?.logEvent("feature_card_clicked", bundle)
        } catch (_: Exception) {}
    }

    /**
     * Ghi nhận sự kiện tùy chỉnh
     */
    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            firebaseAnalytics?.logEvent(eventName, params)
        } catch (_: Exception) {}
    }
}
