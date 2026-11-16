package online.duolap.tetviet.game.dephi.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.AdPlacement
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.databinding.ActivityDePhiBinding
import online.duolap.tetviet.game.dephi.engine.DePhiEvent
import online.duolap.tetviet.game.dephi.engine.DePhiPreferences
import online.duolap.tetviet.game.dephi.engine.DePhiState
import online.duolap.tetviet.game.dephi.engine.ItemType
import online.duolap.tetviet.views.CoinFlyAnimator
import online.duolap.tetviet.views.setSafeOnClickListener
import javax.inject.Inject

/**
 * "Cùng Dê Phi Đón Tết": chạy vô tận một chạm. Màn chơi là [DePhiGameView], Activity lo HUD, màn chờ,
 * tạm dừng, thẻ kết quả và cộng xu vào ví chung (dùng chung với Bầu Cua, Ô Ăn Quan, Đập Chuột).
 *
 * Quảng cáo giống Đập Chuột: [AdPlacement.DE_PHI_REVIVE] (hồi sinh 1 lần mỗi ván khi va chạm),
 * [AdPlacement.DE_PHI_DOUBLE_COINS] (nhân đôi xu trên thẻ kết quả) và interstitial [AdPlacement.DE_PHI_BREAK]
 * chỉ sau khi đóng thẻ kết quả (Chơi lại / Thoát).
 */
@AndroidEntryPoint
class DePhiActivity : AppCompatActivity(), DePhiGameView.Listener {

    @Inject
    lateinit var coinPrefs: BauCuaPreferences

    @Inject
    lateinit var adManager: AdManager

    private var reviveUsed = false
    private var reviveCountdownJob: Job? = null

    private lateinit var binding: ActivityDePhiBinding
    private lateinit var prefs: DePhiPreferences

    private var lastMeters = -1
    private var lastXu = -1
    private var startPulse: ValueAnimator? = null
    private var previewAnim: ValueAnimator? = null
    private var skin = GoatSkin.TRANG

    companion object {
        private const val REVIVE_OFFER_SECONDS = 5

        fun start(context: Context) {
            context.startActivity(Intent(context, DePhiActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDePhiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = DePhiPreferences(this)
        AppAnalytics.logScreenView("DePhiActivity")

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootDePhi) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.hud.setPadding(
                binding.hud.paddingLeft, bars.top + dp(8), binding.hud.paddingRight, binding.hud.paddingBottom
            )
            // Màn chờ: chừa chỗ cho thanh trạng thái và thanh điều hướng
            binding.layoutStart.setPadding(
                binding.layoutStart.paddingLeft, bars.top + dp(56), binding.layoutStart.paddingRight, bars.bottom
            )
            insets
        }

        binding.gameView.listener = this
        binding.btnBack.setSafeOnClickListener { handleBack() }
        binding.btnPlayAgain.setSafeOnClickListener { playAgainAfterBreak() }
        binding.btnExitResult.setSafeOnClickListener { leaveAfterBreak() }
        binding.btnRevive.setSafeOnClickListener { watchReviveAd() }
        binding.btnReviveSkip.setSafeOnClickListener { skipRevive() }
        binding.btnPlay.setSafeOnClickListener { startRun() }
        binding.btnShopStart.setSafeOnClickListener { openShop() }
        binding.btnShopResult.setSafeOnClickListener { openShop() }
        applySkin(GoatSkin.fromId(prefs.selectedSkin))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        updateCoins()
        showStart()
    }

    override fun onResume() {
        super.onResume()
        binding.gameView.setVisibleToUser(true)
    }

    override fun onPause() {
        // Rời màn hình giữa ván: tạm dừng, quay lại chạm để chơi tiếp
        if (binding.gameView.mode == DePhiGameView.Mode.RUNNING) {
            binding.gameView.pause()
            binding.tvPaused.setText(R.string.de_phi_paused)
            binding.tvPaused.isVisible = true
        }
        binding.gameView.setVisibleToUser(false)
        super.onPause()
    }

    override fun onDestroy() {
        startPulse?.cancel()
        previewAnim?.cancel()
        reviveCountdownJob?.cancel()
        super.onDestroy()
    }

    // --- Màn chờ / bắt đầu ---------------------------------------------------------------------

    private fun showStart() {
        binding.layoutStart.isVisible = true
        binding.layoutResult.isVisible = false
        binding.hudCenter.visibility = View.INVISIBLE
        binding.tvDistance.text = getString(R.string.de_phi_meters, 0)
        binding.tvRunXu.text = ""
        binding.progressTrack.isVisible = false
        binding.tvStartBest.text = getString(R.string.de_phi_best, prefs.bestMeters)
        binding.tvStartBest.isVisible = prefs.bestMeters > 0
        startPulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.btnPlay,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)
        ).apply {
            duration = 650
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        // Chú dê trên bục chạy tại chỗ và nảy nhẹ
        previewAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val f = it.animatedValue as Float
                val frame = (f * 8).toInt() % 2
                binding.ivStartGoat.setImageResource(if (frame == 0) skin.run1 else skin.run2)
                binding.ivStartGoat.translationY = -6 * resources.displayMetrics.density *
                    kotlin.math.abs(kotlin.math.sin(f * 4 * Math.PI)).toFloat()
            }
            start()
        }
    }

    private fun applySkin(newSkin: GoatSkin) {
        skin = newSkin
        binding.gameView.skin = newSkin
        binding.ivStartGoat.setImageResource(newSkin.run1)
        binding.tvStartSkin.setText(newSkin.nameRes)
    }

    private fun openShop() {
        DePhiShopDialog.show(this, prefs, coinPrefs) { chosen ->
            applySkin(chosen)
            updateCoins()
        }
        // Mua bằng xu: số dư trên HUD cập nhật khi đóng cửa hàng
        binding.root.postDelayed({ updateCoins() }, 300)
    }

    private fun startRun() {
        startPulse?.cancel()
        previewAnim?.cancel()
        binding.layoutStart.isVisible = false
        binding.hudCenter.visibility = View.VISIBLE
        binding.progressTrack.isVisible = prefs.bestMeters > 0
        binding.progressFill.pivotX = 0f
        binding.progressFill.scaleX = 0f
        binding.layoutResult.isVisible = false
        binding.tvPaused.isVisible = false
        lastMeters = -1
        lastXu = -1
        reviveUsed = false
        binding.layoutRevive.isVisible = false
        binding.gameView.newGame()
        prefs.totalGames++
        AppAnalytics.logFeatureClick("de_phi_start")
    }

    override fun onIdleTap() {
        when (binding.gameView.mode) {
            DePhiGameView.Mode.READY -> startRun()
            DePhiGameView.Mode.PAUSED -> {
                binding.tvPaused.isVisible = false
                binding.gameView.resume()
            }
            // Kết thúc ván: dùng nút trên thẻ kết quả
            DePhiGameView.Mode.OVER, DePhiGameView.Mode.RUNNING -> Unit
        }
    }

    // --- Trong ván -----------------------------------------------------------------------------

    override fun onTick(state: DePhiState) {
        if (state.meters != lastMeters) {
            lastMeters = state.meters
            binding.tvDistance.text = getString(R.string.de_phi_meters, state.meters)
            val best = prefs.bestMeters
            if (best > 0) binding.progressFill.scaleX = (state.meters.toFloat() / best).coerceIn(0f, 1f)
            updateRunLine(state)
        } else if (state.xu != lastXu) {
            updateRunLine(state)
        }
    }

    override fun onEvent(event: DePhiEvent) {
        when (event) {
            is DePhiEvent.Collected -> {
                updateRunLine(binding.gameView.state)
                if (event.type != ItemType.GOLD) bump(binding.tvRunXu)
            }
            is DePhiEvent.Milestone -> bump(binding.tvDistance)
            is DePhiEvent.Crashed -> {
                binding.gameView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                binding.gameView.postDelayed({
                    if (isFinishing) return@postDelayed
                    val state = binding.gameView.state
                    if (!reviveUsed && state.meters > 0) showReviveOffer(state) else showResult(state)
                }, 750)
            }
            DePhiEvent.Landed -> Unit
        }
    }

    // --- Hồi sinh bằng video ---------------------------------------------------------------------

    private fun showReviveOffer(state: DePhiState) {
        binding.tvReviveTitle.text = getString(R.string.de_phi_revive_title, state.meters)
        binding.ivReviveGoat.setImageResource(skin.jump)
        binding.btnRevive.isEnabled = true
        binding.layoutRevive.isVisible = true
        binding.layoutRevive.alpha = 0f
        binding.layoutRevive.animate().alpha(1f).setDuration(200).start()
        binding.reviveCard.scaleX = 0.6f
        binding.reviveCard.scaleY = 0.6f
        binding.reviveCard.animate().scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(OvershootInterpolator(1.8f)).start()
        reviveCountdownJob?.cancel()
        reviveCountdownJob = lifecycleScope.launch {
            // Không bắt người chơi chờ: tự chuyển sang kết quả sau vài giây nếu không bấm
            for (sec in REVIVE_OFFER_SECONDS downTo 1) {
                binding.btnReviveSkip.text = getString(R.string.de_phi_revive_skip, sec)
                delay(1000L)
            }
            skipRevive()
        }
    }

    private fun skipRevive() {
        reviveCountdownJob?.cancel()
        if (!binding.layoutRevive.isVisible) return
        binding.layoutRevive.isVisible = false
        showResult(binding.gameView.state)
    }

    private fun watchReviveAd() {
        reviveCountdownJob?.cancel()
        binding.btnRevive.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var rewarded = false
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.DE_PHI_REVIVE,
            onUserEarnedReward = { rewarded = true },
            // Hồi sinh khi quảng cáo đã đóng, để người chơi thấy và chủ động chạm chạy tiếp
            onDismissed = { if (rewarded) reviveRun() else skipRevive() },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                skipRevive()
            }
        )
    }

    private fun reviveRun() {
        reviveUsed = true
        binding.layoutRevive.isVisible = false
        if (binding.gameView.revive()) {
            binding.tvPaused.setText(R.string.de_phi_tap_to_continue)
            binding.tvPaused.isVisible = true
            AppAnalytics.logFeatureClick("de_phi_revive")
        } else {
            showResult(binding.gameView.state)
        }
    }

    // --- Nghỉ giữa hai ván (interstitial) --------------------------------------------------------

    /** Đóng thẻ kết quả trước, rồi mới xét quảng cáo xen kẽ ở điểm nghỉ tự nhiên giữa hai ván. */
    private fun playAgainAfterBreak() {
        binding.layoutResult.isVisible = false
        adManager.showInterstitialAd(this, AdPlacement.DE_PHI_BREAK) { startRun() }
    }

    private fun leaveAfterBreak() {
        binding.layoutResult.isVisible = false
        adManager.showInterstitialAd(this, AdPlacement.DE_PHI_BREAK) { finish() }
    }

    private fun handleBack() {
        when {
            binding.layoutRevive.isVisible -> skipRevive()
            binding.layoutResult.isVisible -> leaveAfterBreak()
            else -> finish()
        }
    }

    // --- Kết quả -------------------------------------------------------------------------------

    private fun showResult(state: DePhiState) {
        val meters = state.meters
        val score = state.totalScore
        val isRecord = meters > prefs.bestMeters
        if (isRecord) prefs.bestMeters = meters
        if (score > prefs.bestScore) prefs.bestScore = score

        // Cộng xu vào ví ngay; số dư trên HUD giữ nguyên tới khi xu bay vào
        val coinsBefore = coinPrefs.getCoins()
        if (state.xu > 0) {
            CoinFlyAnimator.hold(binding.tvCoins)
            coinPrefs.addCoins(state.xu)
        }

        AppAnalytics.logEvent(
            "de_phi_game_over",
            bundleOf("meters" to meters, "score" to score, "xu" to state.xu, "jumps" to state.jumps)
        )

        binding.tvResultTitle.text = getString(if (isRecord && meters > 0) R.string.de_phi_new_record else R.string.de_phi_game_over)
        binding.tvResultDistance.text = getString(R.string.de_phi_meters, meters)
        binding.tvResultScore.text = getString(R.string.de_phi_result_score, score, prefs.bestMeters)
        binding.tvCountGold.text = (state.collectedCounts[ItemType.GOLD] ?: 0).toString()
        binding.tvCountLixi.text = (state.collectedCounts[ItemType.LIXI] ?: 0).toString()
        binding.tvCountBanhChung.text = (state.collectedCounts[ItemType.BANH_CHUNG] ?: 0).toString()
        binding.tvResultXu.text = getString(R.string.de_phi_result_xu, state.xu)
        binding.btnDoubleCoins.isVisible = state.xu > 0
        binding.btnDoubleCoins.isEnabled = true
        binding.ivDoubleCoinsVideo.isVisible = true
        binding.tvDoubleCoins.text = getString(R.string.de_phi_double_coins, state.xu)
        binding.btnDoubleCoins.setSafeOnClickListener { watchDoubleCoinsAd(state.xu) }

        binding.layoutResult.isVisible = true
        binding.layoutResult.alpha = 0f
        binding.layoutResult.animate().alpha(1f).setDuration(200).start()
        val card = binding.resultCard
        card.scaleX = 0.6f
        card.scaleY = 0.6f
        card.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(1.6f))
            .withEndAction {
                if (state.xu > 0 && binding.layoutResult.isVisible) {
                    val (fx, fy) = CoinFlyAnimator.centerOnScreen(binding.boxCoins)
                    CoinFlyAnimator.fly(
                        this, fx, fy, binding.chipCoins, binding.tvCoins,
                        startValue = coinsBefore, endValue = coinsBefore + state.xu
                    )
                } else {
                    CoinFlyAnimator.release(binding.tvCoins)
                    updateCoins()
                }
            }.start()
    }

    private fun watchDoubleCoinsAd(xu: Int) {
        binding.btnDoubleCoins.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var earned = false
        var before = 0
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.DE_PHI_DOUBLE_COINS,
            // Cộng xu ngay để không mất thưởng; hiệu ứng chạy khi quảng cáo đã đóng để người chơi thấy
            onUserEarnedReward = {
                earned = true
                before = coinPrefs.getCoins()
                CoinFlyAnimator.hold(binding.tvCoins)
                coinPrefs.addCoins(xu)
            },
            onDismissed = {
                binding.btnDoubleCoins.isEnabled = !earned
                if (!earned) return@showRewardedAd
                binding.ivDoubleCoinsVideo.isVisible = false
                binding.tvDoubleCoins.text = getString(R.string.de_phi_double_coins_done, xu * 2)
                binding.tvResultXu.text = getString(R.string.de_phi_result_xu, xu * 2)
                bump(binding.boxCoins)
                binding.root.postDelayed({
                    if (!binding.layoutResult.isVisible) {
                        CoinFlyAnimator.release(binding.tvCoins)
                        updateCoins()
                        return@postDelayed
                    }
                    val (fx, fy) = CoinFlyAnimator.centerOnScreen(binding.boxCoins)
                    CoinFlyAnimator.fly(this, fx, fy, binding.chipCoins, binding.tvCoins, before, before + xu)
                }, 450)
            },
            onError = { message ->
                binding.btnDoubleCoins.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateRunLine(state: DePhiState) {
        lastXu = state.xu
        binding.tvRunXu.text = if (prefs.bestMeters > 0) {
            getString(R.string.de_phi_run_xu_best, state.xu, state.totalScore, prefs.bestMeters)
        } else {
            getString(R.string.de_phi_run_xu, state.xu, state.totalScore)
        }
    }

    private fun updateCoins() {
        if (CoinFlyAnimator.isHeld(binding.tvCoins)) return
        binding.tvCoins.text = coinPrefs.getCoins().toString()
    }

    private fun bump(view: View) {
        view.animate().cancel()
        view.scaleX = 1.2f
        view.scaleY = 1.2f
        view.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
