package online.duolap.tetviet.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import online.duolap.tetviet.activities.MainActivity
import online.duolap.tetviet.ads.AppOpenGate
import online.duolap.tetviet.analytics.AppAnalytics

/**
 * Phần dùng chung của các widget đếm ngược Tết (Cổ điển, Lì xì).
 * Nội dung chỉ đổi lúc nửa đêm (giờ VN), nên mỗi loại widget đặt 1 báo thức RTC (không đánh thức máy)
 * để tự làm mới, cộng với updatePeriodMillis làm phương án dự phòng.
 */
abstract class TetWidgetProviderBase : AppWidgetProvider() {

    /** Tên loại widget trong sự kiện Analytics, vd. "classic". */
    protected abstract val analyticsName: String

    protected abstract fun buildViews(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, buildViews(context, appWidgetManager, it)) }
        scheduleNextDayUpdate(context)
    }

    override fun onEnabled(context: Context) {
        AppAnalytics.logFeatureClick("widget_${analyticsName}_enabled")
    }

    override fun onDisabled(context: Context) {
        alarmManager(context).cancel(dayChangePendingIntent(context))
        AppAnalytics.logFeatureClick("widget_${analyticsName}_disabled")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_DAY_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
                if (ids.isNotEmpty()) onUpdate(context, manager, ids)
            }

            // Launcher (vd. Samsung) tự báo "đã thêm widget" nên app không hiện thông báo riêng
            ACTION_WIDGET_PINNED -> AppAnalytics.logFeatureClick("widget_${analyticsName}_pinned")
        }
    }

    private fun scheduleNextDayUpdate(context: Context) {
        val triggerAt = TetCountdown.nextDayStartMillis()
        val alarmManager = alarmManager(context)
        val pendingIntent = dayChangePendingIntent(context)
        // RTC (không WAKEUP): máy đang ngủ thì widget cập nhật ngay khi người dùng mở máy.
        // Cần đúng giờ vì Chronometer của widget Lì xì về 0 lúc nửa đêm; không có quyền báo thức chính xác
        // thì cho trễ tối đa DAY_CHANGE_WINDOW_MS.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExact(AlarmManager.RTC, triggerAt, pendingIntent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC, triggerAt, DAY_CHANGE_WINDOW_MS, pendingIntent)
        }
    }

    /** Mỗi loại widget có báo thức riêng (intent nhắm vào đúng class của nó). */
    private fun dayChangePendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, javaClass).setAction(ACTION_DAY_CHANGED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmManager(context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    companion object {
        private const val ACTION_DAY_CHANGED = "online.duolap.tetviet.widget.ACTION_DAY_CHANGED"
        private const val ACTION_WIDGET_PINNED = "online.duolap.tetviet.widget.ACTION_WIDGET_PINNED"
        private const val DAY_CHANGE_WINDOW_MS = 60_000L

        // FLAG_UPDATE_CURRENT chỉ thay extras, KHÔNG thay cờ của Intent: muốn đổi cờ mở app thì phải đổi mã này
        // (mã 0 cũ mang cờ CLEAR_TOP gây lỗi phải Back 2 lần)
        private const val REQUEST_CODE_OPEN_APP = 1

        /**
         * Hiện hộp thoại hệ thống để ghim widget loại [providerClass] ra màn hình chính.
         * @return false nếu máy/launcher không hỗ trợ (Android < 8 hoặc launcher không cho ghim).
         */
        fun requestPin(context: Context, providerClass: Class<out TetWidgetProviderBase>): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false

            // Hệ thống gắn thêm EXTRA_APPWIDGET_ID vào intent này nên phải là MUTABLE
            val pinnedCallback = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, providerClass).setAction(ACTION_WIDGET_PINNED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            return manager.requestPinAppWidget(ComponentName(context, providerClass), null, pinnedCallback)
        }

        /**
         * Chạm vào widget thì mở app ở màn hình đếm ngược, luôn chỉ còn đúng 1 màn hình để 1 lần Back là thoát.
         * Không dùng CLEAR_TOP: nếu task đang có màn hình khác ở gốc (vd. Tạo Thiệp), CLEAR_TOP không tìm thấy
         * MainActivity để dọn và sẽ chồng thêm 1 MainActivity lên trên -> phải Back 2 lần.
         */
        fun openAppPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_APP,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(AppOpenGate.EXTRA_SKIP_APP_OPEN, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
