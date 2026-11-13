package online.duolap.tetviet.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import online.duolap.tetviet.R

/**
 * Widget Lì xì.
 * - Bản gọn (4x1): số ngày + giờ:phút:giây, thanh tiến trình, ngày âm lịch.
 * - Bản cao (từ [LARGE_LAYOUT_MIN_HEIGHT_DP]): thêm lời chúc đổi mỗi ngày.
 *
 * Giờ:phút:giây do Chronometer của launcher tự chạy, app không phải cập nhật mỗi giây.
 */
class TetLixiWidgetProvider : TetWidgetProviderBase() {

    override val analyticsName = "lixi"

    override fun buildViews(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: gửi cả 2 layout, launcher tự chọn theo kích thước thật.
            // (Kích thước launcher báo qua options có thể sai, vd. Samsung báo 2 hàng khi widget chỉ cao 1 hàng.)
            return RemoteViews(
                mapOf(
                    SizeF(MIN_WIDTH_DP, 0f) to createViews(context, isLarge = false),
                    SizeF(MIN_WIDTH_DP, LARGE_LAYOUT_MIN_HEIGHT_DP) to createViews(context, isLarge = true)
                )
            )
        }
        val options = manager.getAppWidgetOptions(appWidgetId)
        // Android 11 trở xuống: dọc màn hình, launcher báo chiều cao qua MAX_HEIGHT
        val heightDp = maxOf(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        )
        return createViews(context, isLarge = heightDp >= LARGE_LAYOUT_MIN_HEIGHT_DP)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Người dùng đổi kích thước widget -> chọn lại layout gọn/cao
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, appWidgetManager, appWidgetId))
    }

    companion object {
        /** Từ chiều cao này (khoảng 2 hàng ô trên màn hình chính) widget hiện thêm lời chúc. */
        private const val LARGE_LAYOUT_MIN_HEIGHT_DP = 130f
        private const val MIN_WIDTH_DP = 200f

        fun createViews(context: Context, isLarge: Boolean): RemoteViews {
            val layout = if (isLarge) R.layout.widget_tet_lixi_large else R.layout.widget_tet_lixi
            val views = RemoteViews(context.packageName, layout)
            val now = System.currentTimeMillis()
            val countdownViews = listOf(R.id.tvWidgetDays, R.id.tvWidgetDaysUnit, R.id.chronoWidget, R.id.tvWidgetSuffix)

            if (TetCountdown.isTetArrived(now)) {
                views.setTextViewText(R.id.tvWidgetPrefix, context.getString(R.string.widget_tet_arrived_title))
                countdownViews.forEach { views.setViewVisibility(it, View.GONE) }
                views.setChronometer(R.id.chronoWidget, SystemClock.elapsedRealtime(), null, false)
            } else {
                val days = TetCountdown.fullDaysUntilTet(now)
                // Launcher có thể áp lại lên view cũ (reapply) nên phải bật lại các view từng bị ẩn
                countdownViews.forEach { views.setViewVisibility(it, View.VISIBLE) }
                views.setTextViewText(R.id.tvWidgetPrefix, context.getString(R.string.widget_prefix))
                views.setTextViewText(R.id.tvWidgetDays, days.toString())
                views.setTextViewText(
                    R.id.tvWidgetDaysUnit,
                    context.resources.getQuantityString(R.plurals.widget_days_unit, days.toInt())
                )
                // Đếm ngược phần lẻ trong ngày; về 0 đúng nửa đêm, lúc báo thức làm mới số ngày
                views.setChronometer(
                    R.id.chronoWidget,
                    SystemClock.elapsedRealtime() + TetCountdown.remainderOfDayMillis(now),
                    null,
                    true
                )
                views.setChronometerCountDown(R.id.chronoWidget, true)
            }

            val progress = TetCountdown.progressPercent(now)
            views.setProgressBar(R.id.progressWidget, 100, progress, false)
            views.setTextViewText(R.id.tvWidgetProgress, context.getString(R.string.widget_progress_format, progress))

            val lunar = LunarCalendar.fromMillis(now)
            val lunarText = context.getString(
                if (lunar.isLeapMonth) R.string.widget_lunar_leap_format else R.string.widget_lunar_format,
                lunar.day,
                lunar.month
            )
            views.setTextViewText(R.id.tvWidgetInfo, context.getString(R.string.widget_info_format, lunarText))

            if (isLarge) {
                val wishes = context.resources.getStringArray(R.array.widget_daily_wishes)
                val wish = wishes[(TetCountdown.dayIndex(now) % wishes.size).toInt()]
                views.setTextViewText(R.id.tvWidgetWish, context.getString(R.string.widget_wish_format, wish))
            }

            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent(context))
            return views
        }
    }
}
