package online.duolap.tetviet.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import online.duolap.tetviet.R

/**
 * Widget Cổ điển (4x1): "87 ngày đến Tết · 06/02/2027 • Mùng 1 Tết", khung viên thuốc viền vàng, mây vàng.
 * Đếm theo ngày lịch nên chỉ đổi lúc nửa đêm.
 */
class TetClassicWidgetProvider : TetWidgetProviderBase() {

    override val analyticsName = "classic"

    override fun buildViews(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews =
        createViews(context)

    companion object {
        fun createViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tet_classic)
            val days = TetCountdown.calendarDaysUntilTet()

            if (days > 0) {
                // Launcher có thể áp lại lên view cũ (reapply) nên phải bật lại view từng bị ẩn
                views.setViewVisibility(R.id.tvClassicDays, View.VISIBLE)
                views.setTextViewText(R.id.tvClassicDays, days.toString())
                views.setTextViewText(
                    R.id.tvClassicTitle,
                    context.resources.getQuantityString(R.plurals.widget_classic_days_until_tet, days)
                )
                views.setTextViewText(R.id.tvClassicSubtitle, context.getString(R.string.widget_classic_date_label))
            } else {
                views.setViewVisibility(R.id.tvClassicDays, View.GONE)
                views.setTextViewText(R.id.tvClassicTitle, context.getString(R.string.widget_tet_arrived_title))
                views.setTextViewText(R.id.tvClassicSubtitle, context.getString(R.string.widget_tet_arrived_subtitle))
            }

            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent(context))
            return views
        }
    }
}
