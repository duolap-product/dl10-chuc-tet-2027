package online.duolap.tetviet.analytics

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Helper quản lý Firebase Crashlytics tập trung.
 * Cung cấp các tiện ích ghi log, custom key, ghi nhận ngoại lệ không gây crash (non-fatal).
 */
object AppCrashlytics {

    private const val TAG = "AppCrashlytics"
    private var isInitialized = false

    fun init() {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.isCrashlyticsCollectionEnabled = true
            crashlytics.setCustomKey("app_initialized_at", System.currentTimeMillis())
            isInitialized = true
            Log.d(TAG, "Firebase Crashlytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Crashlytics", e)
        }
    }

    /**
     * Ghi log breadcrumb vào báo cáo Crashlytics
     */
    fun log(message: String) {
        Log.i(TAG, message)
        if (!isInitialized) return
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }

    /**
     * Đặt custom key để lọc lỗi trên Crashlytics Console
     */
    fun setCustomKey(key: String, value: String) {
        if (!isInitialized) return
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    fun setCustomKey(key: String, value: Long) {
        if (!isInitialized) return
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    fun setCustomKey(key: String, value: Boolean) {
        if (!isInitialized) return
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    /**
     * Gán User Identifier
     */
    fun setUserId(userId: String) {
        if (!isInitialized) return
        runCatching { FirebaseCrashlytics.getInstance().setUserId(userId) }
    }

    /**
     * Ghi nhận ngoại lệ non-fatal (lỗi mạng, parse data,...) để theo dõi trên console
     */
    fun recordException(throwable: Throwable, contextTag: String? = null) {
        Log.e(TAG, "Exception recorded: ${contextTag ?: ""}", throwable)
        if (!isInitialized) return
        runCatching {
            contextTag?.let { setCustomKey("last_error_context", it) }
            FirebaseCrashlytics.getInstance().recordException(throwable)
        }
    }
}
