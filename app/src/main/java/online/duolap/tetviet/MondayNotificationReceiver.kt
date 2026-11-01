package online.duolap.tetviet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MondayNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MondayNotificationScheduler.ACTION_MONDAY_NOTIFICATION -> {
                MondayNotificationScheduler.showMondayNotification(context)

                MondayNotificationScheduler.scheduleNextMondayNotification(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                MondayNotificationScheduler.scheduleNextMondayNotification(context)
            }
        }
    }
}
