package online.duolap.tetviet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MondayNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MondayNotificationScheduler.ACTION_MONDAY_NOTIFICATION -> {
                // Báo thức đặt trước khi người dùng tắt vẫn có thể tới: bỏ qua
                if (!MondayNotificationScheduler.isEnabled(context)) return
                MondayNotificationScheduler.showMondayNotification(context)

                MondayNotificationScheduler.scheduleNextMondayNotification(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                MondayNotificationScheduler.scheduleNextMondayNotification(context)
            }
        }
    }
}
