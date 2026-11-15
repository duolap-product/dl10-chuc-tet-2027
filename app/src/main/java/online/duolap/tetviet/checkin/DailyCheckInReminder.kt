package online.duolap.tetviet.checkin

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import online.duolap.tetviet.R
import online.duolap.tetviet.activities.MainActivity
import online.duolap.tetviet.ads.AppOpenGate
import online.duolap.tetviet.widget.TetCountdown
import java.util.Calendar
import javax.inject.Inject

/**
 * Nhắc điểm danh: 19:00 (giờ VN) nếu hôm đó người dùng chưa nhận lì xì.
 *
 * Mỗi lần mở app hoặc nhận lì xì thì đặt lại báo thức cho lần 19:00 kế tiếp. Người dùng bỏ app quá
 * [MAX_IDLE_DAYS] ngày thì thôi nhắc, tới khi họ mở app lại. Dùng báo thức không chính xác
 * (setAndAllowWhileIdle) nên không cần quyền SCHEDULE_EXACT_ALARM; lệch vài phút không sao.
 */
object DailyCheckInReminder {

    const val CHANNEL_ID = "daily_checkin_channel"
    const val ACTION_REMIND = "online.duolap.tetviet.ACTION_DAILY_CHECKIN_REMIND"
    /** Intent mở MainActivity từ thông báo: mở luôn hộp thoại điểm danh. */
    const val EXTRA_OPEN_DAILY_CHECKIN = "extra_open_daily_checkin"

    private const val NOTIFICATION_ID = 2028
    private const val REQUEST_CODE_ALARM = 1101
    private const val REQUEST_CODE_CONTENT = 2004
    private const val REMIND_HOUR = 19
    private const val MAX_IDLE_DAYS = 3L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.checkin_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.checkin_channel_desc) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /** Đặt báo thức 19:00 kế tiếp (hôm nay nếu chưa nhận và chưa qua 19:00, nếu không thì ngày mai). */
    fun schedule(context: Context, prefs: DailyCheckInPreferences) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmPendingIntent(context)
        if (!prefs.isReminderEnabled) {
            alarmManager.cancel(pending)
            return
        }
        val now = System.currentTimeMillis()
        val todayAt = Calendar.getInstance(TetCountdown.TIME_ZONE).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, REMIND_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val trigger = if (!prefs.hasClaimedToday() && now < todayAt) todayAt else todayAt + DAY_MILLIS
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(alarmPendingIntent(context))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Đã nhận lì xì thì gỡ thông báo nhắc còn treo (nếu có). */
    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    internal fun onAlarm(context: Context, prefs: DailyCheckInPreferences) {
        if (!prefs.isReminderEnabled) return
        val status = prefs.refresh()
        val idleDays = prefs.daysSinceLastActive()
        if (!status.claimedToday && idleDays <= MAX_IDLE_DAYS) {
            showNotification(context, status)
        }
        if (idleDays < MAX_IDLE_DAYS) schedule(context, prefs)
    }

    private fun showNotification(context: Context, status: DailyCheckInPreferences.Status) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createNotificationChannel(context)

        val (title, text) = when {
            status.dayNumber == DailyCheckInPreferences.CYCLE -> context.getString(R.string.checkin_notif_title_big) to
                context.getString(R.string.checkin_notif_text_big, status.todayReward)
            status.streak > 0 -> context.getString(R.string.checkin_notif_title_streak, status.dayNumber) to
                context.getString(R.string.checkin_notif_text_streak, status.todayReward, status.streak)
            else -> context.getString(R.string.checkin_notif_title_start) to
                context.getString(R.string.checkin_notif_text_start, status.todayReward)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_DAILY_CHECKIN, true)
            putExtra(AppOpenGate.EXTRA_SKIP_APP_OPEN, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context, REQUEST_CODE_CONTENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyCheckInReminderReceiver::class.java).setAction(ACTION_REMIND)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_ALARM, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}

@AndroidEntryPoint
class DailyCheckInReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: DailyCheckInPreferences

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DailyCheckInReminder.ACTION_REMIND -> DailyCheckInReminder.onAlarm(context, prefs)
            Intent.ACTION_BOOT_COMPLETED -> {
                if (prefs.daysSinceLastActive() < 3) DailyCheckInReminder.schedule(context, prefs)
            }
        }
    }
}
