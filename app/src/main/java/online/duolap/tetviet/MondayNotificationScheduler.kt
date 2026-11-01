package online.duolap.tetviet

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import online.duolap.tetviet.activities.MainActivity
import java.util.Calendar
import java.util.TimeZone

object MondayNotificationScheduler {

    const val CHANNEL_ID = "tet_monday_countdown_channel"
    const val NOTIFICATION_ID = 2027
    const val ACTION_MONDAY_NOTIFICATION = "online.duolap.tetviet.ACTION_MONDAY_NOTIFICATION"
    const val EXTRA_OPEN_MONDAY_PAGE = "extra_open_monday_page"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Đếm ngược Thứ Hai đến Tết"
            val descriptionText = "Thông báo 8h30 sáng Thứ Hai nhắc nhở số đầu tuần còn lại đến Tết Đinh Mùi 2027"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Tính thời điểm 8h30 sáng Thứ Hai sắp tới (GMT+7)
     */
    fun getNextMonday830Millis(): Long {
        val tz = TimeZone.getTimeZone("GMT+7")
        val now = Calendar.getInstance(tz)

        val target = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Nếu hôm nay là Thứ Hai và thời gian hiện tại trước 8h30 sáng:
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY && now.before(target)) {
            return target.timeInMillis
        }

        // Nếu không, tìm ngày Thứ Hai tiếp theo
        target.add(Calendar.DAY_OF_YEAR, 1)
        while (target.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    /**
     * Lên lịch thông báo 8h30 sáng Thứ Hai hàng tuần
     */
    fun scheduleNextMondayNotification(context: Context) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MondayNotificationReceiver::class.java).apply {
            action = ACTION_MONDAY_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = getNextMonday830Millis()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Bắn notification thông báo đếm ngược Thứ Hai
     */
    fun showMondayNotification(context: Context) {
        createNotificationChannel(context)

        val mondaysRemaining = calculateMondaysRemaining()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MONDAY_PAGE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🌸 Chào Thứ Hai! Đếm ngược Tết 2027"
        val content = when {
            mondaysRemaining > 1 -> "Còn $mondaysRemaining cái Thứ Hai đầu tuần nữa là đến Tết rồi! Cố lên nhé! 🧧"
            mondaysRemaining == 1 -> "Hôm nay là Thứ Hai cuối cùng trước Tết! Chuẩn bị đón xuân sum vầy thôi! 🌸"
            mondaysRemaining == 0 -> "Tết Đinh Mùi 2027 đã đến rồi! Chúc bạn và gia đình năm mới An Khang Thịnh Vượng! 🧧"
            else -> "Chúc bạn một tuần mới tràn đầy năng lượng và may mắn! 🌸"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun calculateMondaysRemaining(): Int {
        val tz = TimeZone.getTimeZone("GMT+7")
        val nowCal = Calendar.getInstance(tz)

        val targetCal = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, 2027)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (nowCal.timeInMillis >= targetCal.timeInMillis) return 0

        val cur = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, nowCal.get(Calendar.YEAR))
            set(Calendar.MONTH, nowCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, nowCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var count = 0
        while (cur.before(targetCal)) {
            if (cur.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                count++
            }
            cur.add(Calendar.DAY_OF_YEAR, 1)
        }
        return count
    }
}
