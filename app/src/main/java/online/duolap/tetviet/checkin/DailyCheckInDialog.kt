package online.duolap.tetviet.checkin

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import online.duolap.tetviet.R
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.AdPlacement
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.databinding.DialogDailyCheckinBinding
import online.duolap.tetviet.databinding.ItemCheckinDayBinding
import online.duolap.tetviet.views.CoinFlyAnimator
import online.duolap.tetviet.views.setSafeOnClickListener

/**
 * Hộp thoại điểm danh nhận lì xì, dùng chung cho màn hình chính và Bầu Cua.
 *
 * Chưa nhận: hai nút "xem video x2" (rewarded [AdPlacement.DAILY_CHECKIN_DOUBLE]) và "nhận thường".
 * Nhận xong: chuyển sang trạng thái đã nhận ngay trong hộp thoại (số xu, hẹn ngày mai, nhắc 19:00).
 * Với quảng cáo: xu được cộng ngay khi SDK báo đã xem xong (không mất thưởng nếu app bị đóng),
 * nhưng màn "đã nhận" chỉ hiện sau khi người dùng đóng quảng cáo để họ thấy được.
 * Khi hộp thoại đóng, [onCoinsAdded] nhận điểm phát xu để màn hình gọi chạy [CoinFlyAnimator].
 */
object DailyCheckInDialog {

    /**
     * @param primaryActionText chữ trên nút sau khi nhận (ví dụ "Chơi Bầu Cua ngay" ở màn hình chính).
     * @param onPrimaryAction bấm nút đó; hộp thoại tự đóng trước khi gọi.
     * @param coinCounter TextView số dư của màn hình gọi: được giữ nguyên (không nhảy số) từ lúc cộng
     *   xu tới khi xu bay vào.
     * @param onCoinsAdded gọi khi hộp thoại đóng, nếu lần này có nhận xu: số xu và điểm phát (toạ độ màn hình).
     */
    fun show(
        activity: Activity,
        prefs: DailyCheckInPreferences,
        adManager: AdManager,
        source: String,
        primaryActionText: String,
        onPrimaryAction: () -> Unit = {},
        coinCounter: View? = null,
        onCoinsAdded: (amount: Int, fromScreenX: Float, fromScreenY: Float) -> Unit = { _, _, _ -> },
        onDismissed: () -> Unit = {}
    ) {
        val b = DialogDailyCheckinBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity).setView(b.root).create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val dayViews = buildDayRow(activity, b.layoutDialogDays)
        val halo = ObjectAnimator.ofPropertyValuesHolder(
            b.viewCheckInHalo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.12f)
        ).apply {
            duration = 1400
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        fun renderClaimed(amount: Int?) {
            val status = prefs.status.value
            bindDays(activity, dayViews, status)
            b.tvCheckInDialogTitle.text = activity.getString(R.string.checkin_dialog_claimed_title)
            b.tvCheckInDialogAmount.visibility = View.VISIBLE
            b.tvCheckInDialogAmount.text = activity.getString(
                R.string.checkin_dialog_amount, amount ?: status.todayReward
            )
            b.tvCheckInDialogDesc.text = activity.getString(R.string.checkin_dialog_claimed_desc)
            b.layoutClaimActions.visibility = View.GONE
            b.layoutClaimedActions.visibility = View.VISIBLE
            b.tvComeBackTomorrow.text = if (status.nextDayNumber == DailyCheckInPreferences.CYCLE) {
                activity.getString(R.string.checkin_come_back_big, status.nextReward)
            } else {
                activity.getString(R.string.checkin_come_back, status.nextReward)
            }
            val canNotify = canPostNotifications(activity) && prefs.isReminderEnabled
            b.tvReminderNote.text = activity.getString(
                if (canNotify) R.string.checkin_reminder_on else R.string.checkin_reminder_off
            )
            b.btnEnableReminder.visibility = if (canNotify) View.GONE else View.VISIBLE
            b.tvCheckInDone.text = primaryActionText
        }

        var addedAmount = 0
        var flyFrom: Pair<Float, Float>? = null

        fun credit(multiplier: Int): Int? {
            return prefs.claim(multiplier)?.also {
                addedAmount = it
                coinCounter?.let { counter -> CoinFlyAnimator.hold(counter) }
            }
        }

        fun onClaimSuccess(amount: Int, doubled: Boolean) {
            AppAnalytics.logFeatureClick(if (doubled) "daily_checkin_claim_x2" else "daily_checkin_claim")
            AppAnalytics.logFeatureClick("daily_checkin_day_${prefs.status.value.dayNumber}_$source")
            DailyCheckInReminder.dismissNotification(activity)
            DailyCheckInReminder.schedule(activity, prefs)
            renderClaimed(amount)
            b.tvCheckInDialogAmount.post {
                if (b.tvCheckInDialogAmount.isAttachedToWindow) flyFrom = CoinFlyAnimator.centerOnScreen(b.tvCheckInDialogAmount)
            }
            // Số xu đếm lên trong hộp thoại, phong bao bật lên mừng nhận lì xì
            android.animation.ValueAnimator.ofInt(0, amount).apply {
                duration = 700
                addUpdateListener {
                    b.tvCheckInDialogAmount.text = activity.getString(R.string.checkin_dialog_amount, it.animatedValue as Int)
                }
                start()
            }
            b.ivCheckInDialogEnvelope.scaleX = 0.6f
            b.ivCheckInDialogEnvelope.scaleY = 0.6f
            b.ivCheckInDialogEnvelope.animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }

        val status = prefs.refresh()
        if (status.claimedToday) {
            renderClaimed(null)
        } else {
            bindDays(activity, dayViews, status)
            b.tvCheckInDialogTitle.text = activity.getString(R.string.checkin_dialog_title, status.dayNumber)
            b.tvCheckInDialogDesc.text = activity.getString(
                R.string.checkin_dialog_desc, DailyCheckInPreferences.rewardFor(DailyCheckInPreferences.CYCLE)
            )
            val doubled = DailyCheckInPreferences.videoRewardFor(status.todayReward)
            b.tvClaimDouble.text = activity.getString(R.string.checkin_btn_double, doubled)
            // Chạm trần (ngày 7): x2 không thêm được xu nào, chỉ để nút nhận thường
            if (doubled <= status.todayReward) b.btnClaimDouble.visibility = View.GONE
            b.tvClaimNormal.text = activity.getString(R.string.checkin_btn_normal, status.todayReward)

            b.btnClaimNormal.setSafeOnClickListener {
                credit(1)?.let { onClaimSuccess(it, doubled = false) }
            }
            b.btnClaimDouble.setSafeOnClickListener {
                var earned: Int? = null
                adManager.showRewardedAd(
                    activity = activity,
                    placement = AdPlacement.DAILY_CHECKIN_DOUBLE,
                    // Cộng xu ngay để không mất thưởng; hiệu ứng để dành tới khi quảng cáo đóng
                    onUserEarnedReward = { earned = credit(2) },
                    onDismissed = { earned?.let { onClaimSuccess(it, doubled = true) } },
                    onError = { msg -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show() }
                )
            }
        }

        b.btnEnableReminder.setSafeOnClickListener {
            prefs.isReminderEnabled = true
            DailyCheckInReminder.schedule(activity, prefs)
            if (!canPostNotifications(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION
                )
            }
            AppAnalytics.logFeatureClick("daily_checkin_enable_reminder")
            b.btnEnableReminder.visibility = View.GONE
            b.tvReminderNote.text = activity.getString(R.string.checkin_reminder_on)
        }
        b.btnCheckInDone.setSafeOnClickListener {
            dialog.dismiss()
            onPrimaryAction()
        }
        b.btnCloseCheckIn.setSafeOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            halo.cancel()
            if (addedAmount > 0) {
                val dm = activity.resources.displayMetrics
                val (fx, fy) = flyFrom ?: (dm.widthPixels / 2f to dm.heightPixels / 2f)
                onCoinsAdded(addedAmount, fx, fy)
            }
            onDismissed()
        }
        dialog.show()
    }

    /** Thêm 7 ô ngày vào [container] (rỗng). Dùng cho cả thẻ ở màn hình chính. */
    fun buildDayRow(activity: Activity, container: LinearLayout): List<ItemCheckinDayBinding> {
        container.removeAllViews()
        return (1..DailyCheckInPreferences.CYCLE).map { day ->
            ItemCheckinDayBinding.inflate(LayoutInflater.from(activity), container, true).also {
                it.tvDayLabel.text = activity.getString(R.string.checkin_day_label, day)
                it.tvDayReward.text = activity.getString(
                    R.string.checkin_reward_short, DailyCheckInPreferences.rewardFor(day)
                )
                if (day == DailyCheckInPreferences.CYCLE) it.ivDayIcon.setImageResource(R.drawable.ic_custom_red_packet)
            }
        }
    }

    /** Tô trạng thái từng ô: đã nhận (vàng + dấu tích), hôm nay (đỏ), sắp tới (kem). */
    fun bindDays(
        activity: Activity,
        days: List<ItemCheckinDayBinding>,
        status: DailyCheckInPreferences.Status
    ) {
        val lastClaimed = if (status.claimedToday) status.dayNumber else status.dayNumber - 1
        days.forEachIndexed { i, d ->
            val day = i + 1
            val claimed = day <= lastClaimed
            val today = !status.claimedToday && day == status.dayNumber
            d.dayRoot.setBackgroundResource(
                when {
                    claimed -> R.drawable.bg_checkin_day_claimed
                    today -> R.drawable.bg_checkin_day_today
                    else -> R.drawable.bg_checkin_day_future
                }
            )
            d.ivDayCheck.visibility = if (claimed) View.VISIBLE else View.GONE
            d.ivDayIcon.alpha = if (claimed) 0.45f else 1f
            val labelColor = if (today) 0xFFFFE08A.toInt() else 0xFF8D5524.toInt()
            val rewardColor = if (today) 0xFFFFFDF7.toInt() else 0xFF7A090D.toInt()
            d.tvDayLabel.setTextColor(labelColor)
            d.tvDayReward.setTextColor(rewardColor)
            d.root.contentDescription = activity.getString(R.string.checkin_day_label, day) + ", " +
                activity.getString(R.string.checkin_dialog_amount, DailyCheckInPreferences.rewardFor(day))
        }
    }

    private fun canPostNotifications(activity: Activity): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private const val REQUEST_NOTIFICATION_PERMISSION = 3101
}
