package online.duolap.tetviet.home

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.activities.BauCuaActivity
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.checkin.DailyCheckInDialog
import online.duolap.tetviet.checkin.DailyCheckInPreferences
import online.duolap.tetviet.checkin.DailyCheckInReminder
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.databinding.ItemCheckinDayBinding
import online.duolap.tetviet.databinding.ItemHomeGameCardBinding
import online.duolap.tetviet.databinding.LayoutHomeGameZoneBinding
import online.duolap.tetviet.game.dapchuot.ui.DapChuotActivity
import online.duolap.tetviet.game.dephi.ui.DePhiActivity
import online.duolap.tetviet.game.oanquan.ui.OAnQuanActivity
import online.duolap.tetviet.views.CoinFlyAnimator
import online.duolap.tetviet.views.setSafeOnClickListener
import online.duolap.tetviet.widget.TetCountdown
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Khu "Sân Chơi Ngày Tết" ở đầu màn hình chính: số xu, thẻ điểm danh 7 ngày và 3 thẻ game.
 *
 * Hiệu ứng (vệt sáng lướt qua thẻ game, phong bao lắc, nút nhận "thở") chạy bằng một ValueAnimator,
 * chỉ đổi translationX/rotation/scale nên không vẽ lại view; dừng khi màn hình không hiển thị.
 */
class HomeGameZoneController(
    private val activity: AppCompatActivity,
    private val zone: LayoutHomeGameZoneBinding,
    private val checkInPrefs: DailyCheckInPreferences,
    private val coinPrefs: BauCuaPreferences,
    private val adManager: AdManager
) {
    private val dayViews: List<ItemCheckinDayBinding> = DailyCheckInDialog.buildDayRow(activity, zone.layoutCheckInDays)
    private val games = listOf(zone.gameBauCua, zone.gameOAnQuan, zone.gameDapChuot)
    private var lastCtaText: String? = null
    private var lastRenderedDay = -1L
    private val density = activity.resources.displayMetrics.density
    private val coinFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))

    /** Một nhịp 4.5 s: 0–0.45 ba vệt sáng lần lượt lướt qua thẻ game; phong bao lắc ở đầu nhịp. */
    private val ambient = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4500
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { onAmbientFrame(it.animatedValue as Float) }
    }

    init {
        bindGame(zone.gameBauCua, R.drawable.bg_game_baucua_main, R.string.game_zone_bau_cua,
            R.string.game_zone_badge_hot, R.drawable.bg_game_badge_hot, "bau_cua") { BauCuaActivity.start(activity) }
        bindGame(zone.gameOAnQuan, R.drawable.bg_oaq_home_card, R.string.feature_o_an_quan,
            R.string.game_zone_badge_strategy, R.drawable.bg_game_badge_gold, "o_an_quan") { OAnQuanActivity.start(activity) }
        bindGame(zone.gameDapChuot, R.drawable.bg_dc_home_card, R.string.feature_dap_chuot,
            R.string.game_zone_badge_new, R.drawable.bg_game_badge_gold, "dap_chuot") { DapChuotActivity.start(activity) }

        zone.cardDePhi.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("de_phi")
            AppAnalytics.logFeatureClick("game_zone_de_phi")
            DePhiActivity.start(activity)
        }
        zone.btnCheckIn.setSafeOnClickListener { openCheckIn("home_card") }
        zone.cardCheckIn.setSafeOnClickListener { openCheckIn("home_card") }
        zone.chipCoinBalance.setSafeOnClickListener { openCheckIn("coin_chip") }

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    coinPrefs.coinsFlow.collect { coins ->
                        val text = coinFormat.format(coins)
                        // Đang chờ xu bay vào: để CoinFlyAnimator đếm số lên
                        if (!CoinFlyAnimator.isHeld(zone.tvCoinBalance)) zone.tvCoinBalance.text = text
                        zone.chipCoinBalance.contentDescription =
                            activity.getString(R.string.game_zone_coin_desc, text)
                    }
                }
                launch { checkInPrefs.status.collect { render(it) } }
            }
        }
    }

    fun onResume() {
        checkInPrefs.refresh()
        checkInPrefs.markAppOpenedToday()
        DailyCheckInReminder.schedule(activity, checkInPrefs)
        if (!ambient.isStarted) ambient.start()
    }

    fun onPause() {
        ambient.cancel()
        zone.ivCheckInEnvelope.rotation = 0f
        zone.btnCheckIn.scaleX = 1f
        zone.btnCheckIn.scaleY = 1f
        zone.ivDePhiGoat.translationY = 0f
        zone.ivDePhiGold.translationY = 0f
    }

    /** Gọi mỗi giây (cùng nhịp đồng hồ đếm ngược Tết) để cập nhật "Quay lại sau hh:mm:ss". */
    fun onTick() {
        if (TetCountdown.dayIndex() != lastRenderedDay) {
            checkInPrefs.refresh() // vừa qua nửa đêm: lì xì mới (hoặc chuỗi vừa đứt)
            return
        }
        updateCta(checkInPrefs.status.value)
    }

    fun openCheckIn(source: String) {
        AppAnalytics.logFeatureClick("daily_checkin_open_$source")
        DailyCheckInDialog.show(
            activity = activity,
            prefs = checkInPrefs,
            adManager = adManager,
            source = "home",
            primaryActionText = activity.getString(R.string.checkin_btn_play_bau_cua),
            coinCounter = zone.tvCoinBalance,
            onCoinsAdded = { amount, fx, fy ->
                val end = coinPrefs.getCoins()
                CoinFlyAnimator.fly(
                    activity, fx, fy, zone.chipCoinBalance, zone.tvCoinBalance,
                    startValue = end - amount, endValue = end, format = { coinFormat.format(it) }
                )
            },
            onPrimaryAction = {
                AppAnalytics.logFeatureClick("daily_checkin_play_bau_cua")
                BauCuaActivity.start(activity)
            }
        )
    }

    private fun render(status: DailyCheckInPreferences.Status) {
        lastRenderedDay = TetCountdown.dayIndex()
        DailyCheckInDialog.bindDays(activity, dayViews, status)
        val bigReward = DailyCheckInPreferences.rewardFor(DailyCheckInPreferences.CYCLE)
        zone.tvCheckInSubtitle.text = if (status.streak > 0) {
            activity.getString(R.string.checkin_subtitle_streak, status.streak, bigReward)
        } else {
            activity.getString(R.string.checkin_subtitle_start, bigReward)
        }
        if (status.claimedToday) {
            zone.btnCheckIn.setBackgroundResource(R.drawable.bg_checkin_btn_waiting)
            zone.tvCheckInCta.setTextColor(0xFF7A090D.toInt())
        } else {
            zone.btnCheckIn.setBackgroundResource(R.drawable.bg_wishing_cta_button)
            zone.tvCheckInCta.setTextColor(0xFFFFFDF7.toInt())
        }
        lastCtaText = null
        updateCta(status)
    }

    private fun updateCta(status: DailyCheckInPreferences.Status) {
        val text = if (status.claimedToday) {
            val remaining = (TetCountdown.nextDayStartMillis() - System.currentTimeMillis()).coerceAtLeast(0) / 1000
            val hms = String.format(Locale.US, "%02d:%02d:%02d", remaining / 3600, remaining % 3600 / 60, remaining % 60)
            activity.getString(R.string.checkin_cta_wait, hms, status.nextReward)
        } else {
            activity.getString(R.string.checkin_cta_claim, status.todayReward)
        }
        if (text != lastCtaText) {
            lastCtaText = text
            zone.tvCheckInCta.text = text
        }
    }

    private fun bindGame(
        card: ItemHomeGameCardBinding,
        @DrawableRes art: Int,
        @StringRes title: Int,
        @StringRes badge: Int,
        @DrawableRes badgeBg: Int,
        analyticsName: String,
        open: () -> Unit
    ) {
        card.ivGameArt.setImageResource(art)
        card.tvGameTitle.setText(title)
        card.tvGameBadge.setText(badge)
        card.tvGameBadge.setBackgroundResource(badgeBg)
        card.tvGameBadge.setTextColor(
            if (badgeBg == R.drawable.bg_game_badge_hot) 0xFFFFF3C4.toInt() else 0xFF7A090D.toInt()
        )
        card.tvGameBadge.visibility = View.VISIBLE
        card.gameCardRoot.contentDescription = activity.getString(title)
        card.gameCardRoot.setSafeOnClickListener {
            AppAnalytics.logFeatureClick(analyticsName)
            AppAnalytics.logFeatureClick("game_zone_$analyticsName")
            open()
        }
    }

    private fun onAmbientFrame(f: Float) {
        // Vệt sáng: thẻ i lướt trong khoảng [i*0.12, i*0.12 + 0.22] của nhịp
        games.forEachIndexed { i, card ->
            val start = i * 0.12f
            val p = (f - start) / 0.22f
            val width = card.layoutGameArt.width.toFloat()
            card.viewGameShine.translationX = if (p in 0f..1f) {
                -60 * density + p * (width + 100 * density)
            } else {
                -60 * density
            }
        }
        val available = !checkInPrefs.status.value.claimedToday
        // Phong bao lắc 2 lần đầu nhịp, nút nhận "thở" khi còn lì xì chưa nhận
        zone.ivCheckInEnvelope.rotation = if (available && f < 0.25f) {
            val t = f / 0.25f
            (12 * sin(t * 4 * PI) * (1 - t)).toFloat()
        } else 0f
        // Dê trên thẻ nổi bật nhún nhảy, đồng vàng lơ lửng
        zone.ivDePhiGoat.translationY = (-7 * density * kotlin.math.abs(sin(f * 4 * PI))).toFloat()
        zone.ivDePhiGold.translationY = (3 * density * sin(f * 2 * PI + 1)).toFloat()
        val pulse = if (available) (1 + 0.035 * sin(f * 6 * PI)).toFloat() else 1f
        zone.btnCheckIn.scaleX = pulse
        zone.btnCheckIn.scaleY = pulse
    }
}
