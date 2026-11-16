package online.duolap.tetviet.game.dapchuot.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
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
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.views.CoinFlyAnimator
import online.duolap.tetviet.databinding.ActivityDapChuotV2Binding
import online.duolap.tetviet.databinding.DialogDapChuotResultV2Binding
import online.duolap.tetviet.game.dapchuot.engine.DapChuotPreferences
import online.duolap.tetviet.game.dapchuot.engine.MoleType
import online.duolap.tetviet.game.dapchuot.engine.WhackMoleEngine
import online.duolap.tetviet.views.setSafeOnClickListener
import javax.inject.Inject

@AndroidEntryPoint
class DapChuotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDapChuotV2Binding
    private lateinit var prefs: DapChuotPreferences

    @Inject
    lateinit var coinPrefs: BauCuaPreferences

    @Inject
    lateinit var adManager: AdManager

    private lateinit var engine: WhackMoleEngine
    private val holes = ArrayList<MoleHoleView>(HOLE_COUNT)
    private val point = FloatArray(2)

    private var countdownJob: Job? = null
    private var reviveCountdownJob: Job? = null
    private var timerAnimator: ValueAnimator? = null
    private val startScreenAnimators = mutableListOf<Animator>()
    private var timerUrgent = false

    // Trạng thái một ván
    private var timerTotalSeconds = GAME_SECONDS
    private var reviveUsed = false
    private var lastDelta = 0
    private var lastCombo = 0
    private var pendingHitHole = -1
    private var resultBinding: DialogDapChuotResultV2Binding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDapChuotV2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = DapChuotPreferences(this)

        setupWindowInsets()
        initHoles()
        initEngine()
        setupListeners()
        updateBestScoreView()
        updateCoinsView()
        resetTimerBar()
        showStartScreen()
    }

    private fun setupWindowInsets() {
        val headerTop = binding.layoutHeader.paddingTop
        val fieldBottom = (binding.fieldContainer.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                binding.layoutHeader.paddingLeft, bars.top + headerTop,
                binding.layoutHeader.paddingRight, binding.layoutHeader.paddingBottom
            )
            binding.ivGarland.translationY = bars.top.toFloat()
            (binding.fieldContainer.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bars.bottom + fieldBottom
            binding.fieldContainer.requestLayout()
            insets
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initHoles() {
        holes.clear()
        holes += listOf(
            binding.hole0, binding.hole1, binding.hole2,
            binding.hole3, binding.hole4, binding.hole5,
            binding.hole6, binding.hole7, binding.hole8
        )
        holes.forEachIndexed { index, hole ->
            // Phản hồi ngay khi chạm xuống, không đợi nhấc tay như onClick
            hole.setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onHoleTapped(index)
                    v.performClick()
                }
                true
            }
        }
    }

    private fun initEngine() {
        engine = WhackMoleEngine(
            holeCount = HOLE_COUNT,
            gameDurationSeconds = GAME_SECONDS,
            onMoleAppear = { holeIndex, type -> runOnUiThread { holes.getOrNull(holeIndex)?.popUp(type) } },
            onMoleDisappear = { holeIndex ->
                runOnUiThread { if (holeIndex != pendingHitHole) holes.getOrNull(holeIndex)?.hide() }
            },
            onScoreChanged = { newScore, delta, combo -> runOnUiThread { onScoreUpdated(newScore, delta, combo) } },
            onTimerTick = { remaining -> runOnUiThread { updateTimerUI(remaining) } },
            onGameOver = { finalScore -> runOnUiThread { onTimeUp(finalScore) } }
        )
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener { handleBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        binding.btnStart.setSafeOnClickListener {
            vibrate(20)
            startReadyCountdown(continueAfterRevive = false)
        }
        binding.btnRevive.setSafeOnClickListener { watchReviveAd() }
        binding.btnReviveSkip.setSafeOnClickListener {
            reviveCountdownJob?.cancel()
            showResult(engine.getCurrentScore())
        }
    }

    private fun handleBack() {
        vibrate(15)
        when {
            engine.isGameRunning() -> showExitConfirmationDialog()
            binding.resultOverlay.isVisible -> leaveAfterBreak()
            else -> finish()
        }
    }

    // ---------------------------------------------------------------- Màn hình bắt đầu

    private fun showStartScreen() {
        binding.startOverlay.visibility = View.VISIBLE
        binding.startOverlay.alpha = 1f
        binding.startContent.visibility = View.VISIBLE
        binding.startContent.alpha = 1f
        binding.startContent.scaleX = 1f
        binding.startContent.scaleY = 1f
        binding.tvCountdown.visibility = View.GONE
        binding.effectsView.setAmbientPetals(true)

        stopStartScreenAnimations()
        // Tiêu đề rơi xuống nảy nhẹ, nút bắt đầu "thở" để mời bấm, chuột ló lên ló xuống
        binding.tvStartTitle.translationY = -dp(60f)
        binding.tvStartTitle.alpha = 0f
        binding.tvStartTitle.animate().translationY(0f).alpha(1f)
            .setDuration(520L).setInterpolator(OvershootInterpolator(2.2f)).start()

        startScreenAnimators += ObjectAnimator.ofFloat(binding.tvStartTitle, View.ROTATION, -2.5f, 2.5f).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        startScreenAnimators += AnimatorSet().apply {
            playTogether(
                pulse(binding.btnStart, View.SCALE_X, 1.07f),
                pulse(binding.btnStart, View.SCALE_Y, 1.07f)
            )
            start()
        }
        binding.heroHole.post {
            if (binding.startOverlay.isVisible) startScreenAnimators += binding.heroHole.peekLoop()
        }
    }

    private fun pulse(view: View, property: android.util.Property<View, Float>, to: Float) =
        ObjectAnimator.ofFloat(view, property, 1f, to).apply {
            duration = 650L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
        }

    private fun stopStartScreenAnimations() {
        startScreenAnimators.forEach { it.cancel() }
        startScreenAnimators.clear()
        binding.btnStart.scaleX = 1f
        binding.btnStart.scaleY = 1f
        binding.tvStartTitle.rotation = 0f
        binding.heroHole.reset()
    }

    /** Đếm 3-2-1 rồi vào ván; sau khi xem quảng cáo chơi tiếp thì đếm lại để người chơi kịp chuẩn bị. */
    private fun startReadyCountdown(continueAfterRevive: Boolean) {
        countdownJob?.cancel()
        stopStartScreenAnimations()
        binding.effectsView.setAmbientPetals(false)
        binding.startOverlay.visibility = View.VISIBLE
        binding.startOverlay.alpha = 1f
        binding.startContent.visibility = View.GONE
        binding.tvCountdown.visibility = View.VISIBLE

        countdownJob = lifecycleScope.launch {
            val steps = listOf("3", "2", "1", getString(R.string.dap_chuot_countdown_go))
            for ((i, step) in steps.withIndex()) {
                val isGo = i == steps.lastIndex
                binding.tvCountdown.text = step
                binding.tvCountdown.textSize = if (isGo) 84f else 110f
                binding.tvCountdown.scaleX = 2.2f
                binding.tvCountdown.scaleY = 2.2f
                binding.tvCountdown.alpha = 0f
                binding.tvCountdown.rotation = if (isGo) -8f else 0f
                binding.tvCountdown.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f)
                    .setDuration(260L).setInterpolator(OvershootInterpolator(2.4f)).start()
                vibrate(if (isGo) 40 else 18)
                if (isGo) {
                    binding.root.post {
                        binding.effectsView.burstStars(binding.root.width / 2f, binding.root.height / 2f, golden = true)
                    }
                }
                delay(if (isGo) 520L else 700L)
            }
            binding.startOverlay.animate().alpha(0f).setDuration(220L).withEndAction {
                binding.startOverlay.visibility = View.GONE
                binding.tvCountdown.visibility = View.GONE
                if (continueAfterRevive) {
                    engine.continueWithExtraTime(lifecycleScope, REVIVE_SECONDS)
                } else {
                    engine.start(lifecycleScope)
                }
            }.start()
        }
    }

    // ---------------------------------------------------------------- Trong ván

    private fun onHoleTapped(holeIndex: Int) {
        if (!engine.isGameRunning()) return
        val hole = holes.getOrNull(holeIndex) ?: return
        hole.moleCenterIn(binding.root, point)
        val x = point[0]
        val y = point[1]
        swingHammer(x, y)

        val type = hole.currentType
        if (type == null) {
            binding.effectsView.puffDust(x, y + hole.height * 0.2f)
            vibrate(8)
            return
        }

        // Engine báo "biến mất" ngay trong lúc xử lý cú đập: bỏ qua để chạy hoạt cảnh bị đập thay vì trốn
        pendingHitHole = holeIndex
        val wasHit = engine.hitMole(holeIndex)
        pendingHitHole = -1
        if (!wasHit) {
            hole.hide()
            return
        }
        hole.hit()

        when (type) {
            MoleType.BOMB -> onBombHit(x, y)
            MoleType.GOLDEN -> {
                binding.effectsView.burstStars(x, y, golden = true)
                vibrate(45)
            }
            MoleType.NORMAL -> {
                binding.effectsView.burstStars(x, y, golden = false)
                vibrate(22)
            }
        }
        showFloatingScore(x, y, lastDelta, lastCombo)
        if (type != MoleType.BOMB) maybeShowComboBanner(lastCombo)
    }

    private fun swingHammer(x: Float, y: Float) {
        val hammer = binding.ivHammer
        val w = if (hammer.width > 0) hammer.width.toFloat() else dp(88f)
        val h = if (hammer.height > 0) hammer.height.toFloat() else dp(88f)
        // Chuôi búa đặt dưới bên phải chuột; xoay quanh đuôi chuôi để đầu búa đập đúng vào giữa
        hammer.pivotX = w * 0.5f
        hammer.pivotY = h * 0.95f
        hammer.x = x + w * 0.55f - hammer.pivotX
        hammer.y = y + h * 0.55f - hammer.pivotY
        hammer.animate().cancel()
        hammer.visibility = View.VISIBLE
        hammer.alpha = 1f
        hammer.rotation = 12f
        hammer.animate().rotation(-42f).setDuration(70L).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                hammer.animate().rotation(-28f).alpha(0f).setStartDelay(90L).setDuration(160L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { hammer.visibility = View.GONE; hammer.animate().setStartDelay(0L) }
                    .start()
            }.start()
    }

    private fun onBombHit(x: Float, y: Float) {
        vibrate(90)
        binding.effectsView.burstSparks(x, y)

        val boom = binding.ivBoom
        val size = dp(150f)
        boom.x = x - size / 2f
        boom.y = y - size / 2f
        boom.animate().cancel()
        boom.visibility = View.VISIBLE
        boom.alpha = 1f
        boom.scaleX = 0.3f
        boom.scaleY = 0.3f
        boom.rotation = 0f
        boom.animate().scaleX(1.25f).scaleY(1.25f).rotation(25f).alpha(0f)
            .setDuration(480L).setInterpolator(DecelerateInterpolator())
            .withEndAction { boom.visibility = View.GONE }.start()

        // Màn hình rung và viền đỏ chớp
        ObjectAnimator.ofFloat(binding.gameContent, View.TRANSLATION_X, 0f, -dp(14f), dp(12f), -dp(9f), dp(6f), -dp(3f), 0f).apply {
            duration = 380L
            start()
        }
        binding.flashOverlay.animate().cancel()
        binding.flashOverlay.alpha = 1f
        binding.flashOverlay.animate().alpha(0f).setDuration(450L).start()
        showBanner(getString(R.string.dap_chuot_bomb_hit), Color.parseColor("#FF8A80"), 32f)
    }

    private fun onScoreUpdated(newScore: Int, delta: Int, combo: Int) {
        lastDelta = delta
        lastCombo = combo
        binding.tvScore.text = newScore.toString()
        bounce(binding.chipScore, 1.12f)

        binding.tvCombo.text = getString(R.string.dap_chuot_combo_prefix) + combo
        binding.tvCombo.setTextColor(
            when {
                combo >= 8 -> Color.parseColor("#FF5252")
                combo >= 5 -> Color.parseColor("#FFEB3B")
                combo >= 3 -> Color.parseColor("#FFB74D")
                else -> Color.parseColor("#FFE0B2")
            }
        )
        if (combo >= 3) bounce(binding.chipCombo, 1.15f)
    }

    private fun bounce(view: View, to: Float) {
        view.animate().cancel()
        view.scaleX = to
        view.scaleY = to
        view.animate().scaleX(1f).scaleY(1f).setDuration(220L).setInterpolator(OvershootInterpolator(3f)).start()
    }

    private fun showFloatingScore(x: Float, y: Float, delta: Int, combo: Int) {
        val tv = TextView(this).apply {
            text = when {
                delta < 0 -> delta.toString()
                combo >= 3 -> "+$delta  x$combo"
                else -> "+$delta"
            }
            setTextColor(
                when {
                    delta < 0 -> Color.parseColor("#FF5252")
                    delta >= 30 -> Color.parseColor("#FFD54F")
                    else -> Color.WHITE
                }
            )
            textSize = if (delta >= 30 || delta < 0) 30f else 24f
            typeface = ResourcesCompat.getFont(context, R.font.arimo_bold)
            setShadowLayer(8f, 0f, 3f, Color.parseColor("#CC000000"))
            gravity = Gravity.CENTER
        }
        binding.root.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        tv.post {
            tv.x = x - tv.width / 2f
            tv.y = y - tv.height
            tv.scaleX = 0.4f
            tv.scaleY = 0.4f
            tv.animate().scaleX(1.1f).scaleY(1.1f).translationYBy(-dp(70f))
                .setDuration(520L).setInterpolator(DecelerateInterpolator()).start()
            tv.animate().alpha(0f).setStartDelay(380L).setDuration(260L)
                .withEndAction { binding.root.removeView(tv) }.start()
        }
    }

    private fun maybeShowComboBanner(combo: Int) {
        val praise = when (combo) {
            3 -> null
            5 -> getString(R.string.dap_chuot_praise_good)
            8 -> getString(R.string.dap_chuot_praise_great)
            12, 16, 20 -> getString(R.string.dap_chuot_praise_legend)
            else -> return
        }
        val text = getString(R.string.dap_chuot_combo_banner, combo) + (praise?.let { "\n$it" } ?: "")
        val color = if (combo >= 8) Color.parseColor("#FF6E40") else Color.parseColor("#FFEB3B")
        showBanner(text, color, if (combo >= 5) 40f else 34f)
        if (combo >= 5) vibrate(35)
    }

    private fun showBanner(text: String, color: Int, sizeSp: Float) {
        val tv = binding.tvBanner
        tv.animate().cancel()
        tv.text = text
        tv.setTextColor(color)
        tv.textSize = sizeSp
        tv.visibility = View.VISIBLE
        tv.alpha = 0f
        tv.scaleX = 0.3f
        tv.scaleY = 0.3f
        tv.rotation = -8f
        tv.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(280L).setInterpolator(OvershootInterpolator(2.6f))
            .withEndAction {
                tv.animate().alpha(0f).translationYBy(-dp(30f)).setStartDelay(520L).setDuration(240L)
                    .withEndAction {
                        tv.visibility = View.GONE
                        tv.translationY = 0f
                        tv.animate().setStartDelay(0L)
                    }.start()
            }.start()
    }

    private fun updateTimerUI(remainingSeconds: Int) {
        binding.tvTimer.text = getString(R.string.dap_chuot_timer_format, remainingSeconds)
        val fill = binding.timerBarFill
        val target = (remainingSeconds * 10_000 / timerTotalSeconds.coerceAtLeast(1)).coerceIn(0, 10_000)
        timerAnimator?.cancel()
        timerAnimator = ValueAnimator.ofInt(fill.background.level, target).apply {
            duration = 950L
            interpolator = LinearInterpolator()
            addUpdateListener { fill.background.level = it.animatedValue as Int }
            start()
        }

        val urgent = remainingSeconds <= LAST_SECONDS
        if (urgent != timerUrgent) {
            // Đổi sang thanh đỏ khi sắp hết giờ, giữ nguyên độ dài đang chạy
            timerUrgent = urgent
            val level = fill.background.level
            fill.setBackgroundResource(if (urgent) R.drawable.bg_dc_timer_fill_urgent else R.drawable.bg_dc_timer_fill)
            fill.background.level = level
            binding.tvTimer.setTextColor(Color.parseColor(if (urgent) "#FFFFFFFF" else "#FF5D2A12"))
        }
        if (urgent && remainingSeconds > 0) {
            bounce(binding.timerBarContainer, 1.06f)
            if (remainingSeconds <= 5) vibrate(12)
        }
        if (remainingSeconds == LAST_SECONDS && timerTotalSeconds > LAST_SECONDS) {
            showBanner(getString(R.string.dap_chuot_last_seconds), Color.parseColor("#FF8A80"), 34f)
        }
    }

    private fun resetTimerBar() {
        timerAnimator?.cancel()
        timerUrgent = false
        binding.timerBarFill.setBackgroundResource(R.drawable.bg_dc_timer_fill)
        binding.timerBarFill.background.level = 10_000
        binding.tvTimer.setTextColor(Color.parseColor("#FF5D2A12"))
        binding.tvTimer.text = getString(R.string.dap_chuot_timer_format, timerTotalSeconds)
    }

    // ---------------------------------------------------------------- Hết giờ, chơi tiếp, kết quả

    private fun onTimeUp(finalScore: Int) {
        holes.forEach { it.hide() }
        showBanner(getString(R.string.dap_chuot_time_up), Color.parseColor("#FFEB3B"), 48f)
        vibrate(60)
        lifecycleScope.launch {
            delay(1000L)
            if (isFinishing || isDestroyed) return@launch
            if (!reviveUsed && finalScore > 0) showReviveOffer() else showResult(finalScore)
        }
    }

    private fun showReviveOffer() {
        binding.timeUpOverlay.visibility = View.VISIBLE
        binding.timeUpOverlay.alpha = 0f
        binding.timeUpOverlay.animate().alpha(1f).setDuration(200L).start()
        binding.reviveCard.scaleX = 0.6f
        binding.reviveCard.scaleY = 0.6f
        binding.reviveCard.animate().scaleX(1f).scaleY(1f).setDuration(320L)
            .setInterpolator(OvershootInterpolator(1.8f)).start()

        reviveCountdownJob?.cancel()
        reviveCountdownJob = lifecycleScope.launch {
            // Không bắt người chơi chờ: tự chuyển sang kết quả sau vài giây nếu không bấm
            for (s in REVIVE_OFFER_SECONDS downTo 1) {
                binding.btnReviveSkip.text = getString(R.string.dap_chuot_revive_skip, s)
                delay(1000L)
            }
            showResult(engine.getCurrentScore())
        }
    }

    private fun watchReviveAd() {
        reviveCountdownJob?.cancel()
        binding.btnRevive.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var rewarded = false
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.DAP_CHUOT_REVIVE,
            onUserEarnedReward = { rewarded = true },
            onDismissed = {
                binding.btnRevive.isEnabled = true
                if (rewarded) resumeAfterRevive() else showResult(engine.getCurrentScore())
            },
            onError = { message ->
                binding.btnRevive.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                showResult(engine.getCurrentScore())
            }
        )
    }

    private fun resumeAfterRevive() {
        reviveUsed = true
        binding.timeUpOverlay.visibility = View.GONE
        timerTotalSeconds = REVIVE_SECONDS
        resetTimerBar()
        startReadyCountdown(continueAfterRevive = true)
    }

    private fun showResult(finalScore: Int) {
        if (isFinishing || isDestroyed || binding.resultOverlay.isVisible) return
        reviveCountdownJob?.cancel()
        binding.timeUpOverlay.visibility = View.GONE

        val oldHigh = prefs.highScore
        val isNewRecord = finalScore > oldHigh
        if (isNewRecord) {
            prefs.highScore = finalScore
            updateBestScoreView()
        }
        prefs.totalGamesPlayed++

        // 1 Xu cho mỗi 20 điểm, tối đa 60 Xu mỗi ván, cộng vào ví Xu dùng chung với Bầu Cua
        val earnedCoins = WhackMoleEngine.coinsForScore(finalScore)
        val coinsBefore = coinPrefs.getCoins()
        if (earnedCoins > 0) {
            // Giữ số dư cũ tới khi xu bay từ thẻ kết quả lên thanh trên cùng
            CoinFlyAnimator.hold(binding.tvCoins)
            coinPrefs.addCoins(earnedCoins)
        }
        updateCoinsView()

        val rb = DialogDapChuotResultV2Binding.inflate(layoutInflater, binding.resultOverlay, false)
        resultBinding = rb
        binding.resultOverlay.removeAllViews()
        binding.resultOverlay.addView(rb.root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))

        rb.tvNewRecord.visibility = View.GONE
        rb.tvResultBest.text = prefs.highScore.toString()
        rb.tvResultCoins.text = getString(R.string.dap_chuot_coins_earned, earnedCoins)
        rb.btnDoubleCoins.visibility = if (earnedCoins > 0) View.VISIBLE else View.GONE

        rb.btnDoubleCoins.setSafeOnClickListener { watchDoubleCoinsAd(rb, earnedCoins) }
        rb.btnPlayAgain.setSafeOnClickListener { playAgainAfterBreak() }
        rb.btnExitResult.setSafeOnClickListener { leaveAfterBreak() }

        binding.resultOverlay.visibility = View.VISIBLE
        binding.resultOverlay.alpha = 0f
        binding.resultOverlay.animate().alpha(1f).setDuration(200L).start()
        rb.root.scaleX = 0.5f
        rb.root.scaleY = 0.5f
        rb.root.animate().scaleX(1f).scaleY(1f).setDuration(380L).setInterpolator(OvershootInterpolator(1.6f)).start()

        animateResult(rb, finalScore, isNewRecord)
        if (earnedCoins > 0) {
            rb.root.postDelayed({ flyCoinsToHud(rb, coinsBefore, coinsBefore + earnedCoins) }, 900L)
        }
    }

    /** Xu bay từ ô xu trên thẻ kết quả vào ô số dư ở thanh trên cùng, số dư đếm lên dần. */
    private fun flyCoinsToHud(rb: DialogDapChuotResultV2Binding, start: Int, end: Int) {
        if (resultBinding !== rb || !rb.boxCoins.isAttachedToWindow) {
            CoinFlyAnimator.release(binding.tvCoins)
            updateCoinsView()
            return
        }
        val (fx, fy) = CoinFlyAnimator.centerOnScreen(rb.boxCoins)
        CoinFlyAnimator.fly(this, fx, fy, binding.tvCoins.parent as View, binding.tvCoins, start, end)
    }

    private fun animateResult(rb: DialogDapChuotResultV2Binding, score: Int, isNewRecord: Boolean) {
        ValueAnimator.ofInt(0, score).apply {
            duration = (500L + score).coerceAtMost(1400L)
            startDelay = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener { rb.tvResultScore.text = (it.animatedValue as Int).toString() }
            start()
        }

        val stars = starsFor(score)
        listOf(rb.ivStar1, rb.ivStar2, rb.ivStar3).forEachIndexed { i, star ->
            if (i >= stars) return@forEachIndexed
            rb.root.postDelayed({
                if (resultBinding !== rb) return@postDelayed
                star.setImageResource(R.drawable.ic_dc_star_filled)
                star.scaleX = 2.2f
                star.scaleY = 2.2f
                star.rotation = -30f
                star.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(320L)
                    .setInterpolator(OvershootInterpolator(2.4f)).start()
                star.getLocationInWindow(location)
                binding.resultEffectsView.burstStars(location[0] + star.width / 2f, location[1] + star.height / 2f, golden = true)
                vibrate(25)
            }, 600L + i * 320L)
        }

        if (isNewRecord) {
            rb.root.postDelayed({
                if (resultBinding !== rb) return@postDelayed
                rb.tvNewRecord.visibility = View.VISIBLE
                rb.tvNewRecord.scaleX = 0f
                rb.tvNewRecord.scaleY = 0f
                rb.tvNewRecord.animate().scaleX(1f).scaleY(1f).setDuration(300L)
                    .setInterpolator(OvershootInterpolator(3f)).start()
                binding.resultEffectsView.rainConfetti()
                vibrateCelebration()
            }, 600L + stars * 320L)
        }
    }

    private val location = IntArray(2)

    private fun watchDoubleCoinsAd(rb: DialogDapChuotResultV2Binding, earnedCoins: Int) {
        rb.btnDoubleCoins.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var earned = false
        var before = 0
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.DAP_CHUOT_DOUBLE_COINS,
            // Cộng xu ngay để không mất thưởng; hiệu ứng chạy khi quảng cáo đã đóng để người chơi thấy
            onUserEarnedReward = {
                earned = true
                before = coinPrefs.getCoins()
                CoinFlyAnimator.hold(binding.tvCoins)
                coinPrefs.addCoins(earnedCoins)
            },
            onDismissed = {
                rb.btnDoubleCoins.isEnabled = !earned
                if (!earned) return@showRewardedAd
                rb.tvResultCoins.text = getString(R.string.dap_chuot_coins_earned, earnedCoins * 2)
                rb.tvDoubleCoins.text = getString(R.string.dap_chuot_double_coins_done, earnedCoins * 2)
                rb.boxCoins.getLocationInWindow(location)
                binding.resultEffectsView.burstCoins(location[0] + rb.boxCoins.width / 2f, location[1] + rb.boxCoins.height / 2f, 14)
                bounce(rb.boxCoins, 1.2f)
                vibrateCelebration()
                rb.root.postDelayed({ flyCoinsToHud(rb, before, before + earnedCoins) }, 450L)
            },
            onError = { message ->
                rb.btnDoubleCoins.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    /** Đóng thẻ kết quả trước, rồi mới xét quảng cáo xen kẽ ở điểm nghỉ tự nhiên giữa hai ván. */
    private fun playAgainAfterBreak() {
        hideResult()
        adManager.showInterstitialAd(this, AdPlacement.DAP_CHUOT_BREAK) {
            resetGameUI()
            startReadyCountdown(continueAfterRevive = false)
        }
    }

    private fun leaveAfterBreak() {
        hideResult()
        adManager.showInterstitialAd(this, AdPlacement.DAP_CHUOT_BREAK) { finish() }
    }

    private fun hideResult() {
        resultBinding = null
        binding.resultEffectsView.clear()
        binding.resultOverlay.visibility = View.GONE
        binding.resultOverlay.removeAllViews()
    }

    private fun resetGameUI() {
        reviveUsed = false
        timerTotalSeconds = GAME_SECONDS
        binding.tvScore.text = "0"
        binding.tvCombo.text = getString(R.string.dap_chuot_combo_prefix) + 0
        binding.tvCombo.setTextColor(Color.parseColor("#FFE0B2"))
        resetTimerBar()
        holes.forEach { it.reset() }
        binding.effectsView.clear()
        binding.tvBanner.visibility = View.GONE
    }

    private fun updateBestScoreView() {
        binding.tvBest.text = prefs.highScore.toString()
    }

    private fun updateCoinsView() {
        if (CoinFlyAnimator.isHeld(binding.tvCoins)) return
        binding.tvCoins.text = coinPrefs.getCoins().toString()
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.feature_dap_chuot))
            .setMessage(R.string.dap_chuot_exit_confirm_message)
            .setPositiveButton(R.string.dap_chuot_exit_confirm_yes) { _, _ ->
                engine.stop()
                finish()
            }
            .setNegativeButton(R.string.dap_chuot_exit_confirm_no, null)
            .show()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(VIBRATOR_SERVICE) as? Vibrator
    }

    private fun vibrate(millis: Long) {
        runCatching {
            val vibrator = vibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(millis)
            }
        }
    }

    private fun vibrateCelebration() {
        runCatching {
            val vibrator = vibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 70, 80, 70, 80, 110)
                val amplitudes = intArrayOf(0, 180, 0, 210, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Rời app giữa ván thì huỷ ván và quay về màn hình bắt đầu (không áp dụng lúc đang xem quảng cáo)
        if (engine.isGameRunning() || countdownJob?.isActive == true) {
            engine.stop()
            countdownJob?.cancel()
            resetGameUI()
            showStartScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        countdownJob?.cancel()
        reviveCountdownJob?.cancel()
        timerAnimator?.cancel()
        stopStartScreenAnimations()
    }

    companion object {
        private const val HOLE_COUNT = 9
        private const val GAME_SECONDS = 60
        private const val REVIVE_SECONDS = 10
        private const val REVIVE_OFFER_SECONDS = 5
        private const val LAST_SECONDS = 10

        // Mốc điểm cho 1, 2, 3 sao
        private val STAR_THRESHOLDS = intArrayOf(100, 250, 450)

        fun starsFor(score: Int): Int = STAR_THRESHOLDS.count { score >= it }

        fun start(context: Context) {
            val intent = Intent(context, DapChuotActivity::class.java)
            context.startActivity(intent)
        }
    }
}
