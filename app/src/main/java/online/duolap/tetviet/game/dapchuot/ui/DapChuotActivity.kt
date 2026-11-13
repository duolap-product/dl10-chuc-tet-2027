package online.duolap.tetviet.game.dapchuot.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.databinding.ActivityDapChuotBinding
import online.duolap.tetviet.databinding.DialogDapChuotResultBinding
import online.duolap.tetviet.game.dapchuot.engine.DapChuotPreferences
import online.duolap.tetviet.game.dapchuot.engine.MoleType
import online.duolap.tetviet.game.dapchuot.engine.WhackMoleEngine
import online.duolap.tetviet.views.setSafeOnClickListener
import javax.inject.Inject

@AndroidEntryPoint
class DapChuotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDapChuotBinding
    private lateinit var prefs: DapChuotPreferences

    @Inject
    lateinit var coinPrefs: BauCuaPreferences

    private lateinit var engine: WhackMoleEngine
    private val holeViews = ArrayList<FrameLayout>(9)
    private val moleViews = ArrayList<ImageView>(9)
    private var countdownJob: Job? = null
    private var resultDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDapChuotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = DapChuotPreferences(this)

        setupWindowInsets()
        initViews()
        initEngine()
        setupListeners()
        updateBestScoreView()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                binding.layoutHeader.paddingLeft,
                systemBars.top + 8,
                binding.layoutHeader.paddingRight,
                binding.layoutHeader.paddingBottom
            )
            insets
        }
    }

    private fun initViews() {
        holeViews.clear()
        moleViews.clear()

        // 9 holes and moles
        val holes = listOf(
            binding.hole0, binding.hole1, binding.hole2,
            binding.hole3, binding.hole4, binding.hole5,
            binding.hole6, binding.hole7, binding.hole8
        )
        val moles = listOf(
            binding.ivMole0, binding.ivMole1, binding.ivMole2,
            binding.ivMole3, binding.ivMole4, binding.ivMole5,
            binding.ivMole6, binding.ivMole7, binding.ivMole8
        )

        holeViews.addAll(holes)
        moleViews.addAll(moles)

        // Clip holes to ensure moles slide up smoothly from within the hole
        for (i in 0 until 9) {
            val mole = moleViews[i]
            mole.visibility = View.INVISIBLE
            mole.scaleX = 1f
            mole.scaleY = 1f
        }
    }

    private fun initEngine() {
        engine = WhackMoleEngine(
            holeCount = 9,
            gameDurationSeconds = 60,
            onMoleAppear = { holeIndex, type ->
                runOnUiThread {
                    animateMoleAppear(holeIndex, type)
                }
            },
            onMoleDisappear = { holeIndex ->
                runOnUiThread {
                    animateMoleDisappear(holeIndex)
                }
            },
            onScoreChanged = { newScore, delta, combo ->
                runOnUiThread {
                    onScoreUpdated(newScore, delta, combo)
                }
            },
            onTimerTick = { remainingSeconds ->
                runOnUiThread {
                    updateTimerUI(remainingSeconds)
                }
            },
            onGameOver = { finalScore ->
                runOnUiThread {
                    onGameEnded(finalScore)
                }
            }
        )
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener {
            vibrateLight(15)
            if (engine.isGameRunning()) {
                showExitConfirmationDialog()
            } else {
                finish()
            }
        }

        binding.btnStart.setSafeOnClickListener {
            vibrateLight(20)
            startReadyCountdown()
        }

        // Tap listener for each hole
        for (i in 0 until 9) {
            val holeIndex = i
            holeViews[i].setOnClickListener {
                onHoleTapped(holeIndex)
            }
        }
    }

    private fun onHoleTapped(holeIndex: Int) {
        if (!engine.isGameRunning()) return

        val mole = moleViews.getOrNull(holeIndex) ?: return
        if (mole.visibility != View.VISIBLE || mole.alpha < 0.2f) {
            // Tapped on empty hole
            vibrateLight(10)
            return
        }

        val wasHit = engine.hitMole(holeIndex)
        if (wasHit) {
            animateMoleHit(holeIndex)
        }
    }

    private fun animateMoleAppear(holeIndex: Int, type: MoleType) {
        val mole = moleViews.getOrNull(holeIndex) ?: return
        mole.animate().cancel()

        // Set visual style based on mole type
        when (type) {
            MoleType.NORMAL -> mole.setImageResource(R.drawable.ic_mole_normal)
            MoleType.GOLDEN -> mole.setImageResource(R.drawable.ic_mole_golden)
            MoleType.BOMB -> mole.setImageResource(R.drawable.ic_mole_bomb)
        }

        mole.alpha = 1f
        mole.scaleX = 1f
        mole.scaleY = 1f
        mole.visibility = View.VISIBLE

        // Slide up smoothly with juicy spring overshoot
        val height = if (mole.height > 0) mole.height.toFloat() else 180f
        mole.translationY = height * 0.75f

        val popAnim = ObjectAnimator.ofFloat(mole, "translationY", mole.translationY, 0f).apply {
            duration = 240L
            interpolator = OvershootInterpolator(1.4f)
        }
        popAnim.start()
    }

    private fun animateMoleHit(holeIndex: Int) {
        val mole = moleViews.getOrNull(holeIndex) ?: return
        mole.animate().cancel()

        // Squish and sink animation
        val scaleDownX = ObjectAnimator.ofFloat(mole, "scaleX", 1.25f, 0.7f).apply {
            duration = 160L
        }
        val scaleDownY = ObjectAnimator.ofFloat(mole, "scaleY", 0.65f, 0.4f).apply {
            duration = 160L
        }
        val sink = ObjectAnimator.ofFloat(mole, "translationY", 0f, 60f).apply {
            duration = 160L
        }
        val fade = ObjectAnimator.ofFloat(mole, "alpha", 1f, 0f).apply {
            duration = 160L
        }

        AnimatorSet().apply {
            playTogether(scaleDownX, scaleDownY, sink, fade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mole.visibility = View.INVISIBLE
                    mole.scaleX = 1f
                    mole.scaleY = 1f
                    mole.alpha = 1f
                    mole.translationY = 0f
                }
            })
            start()
        }
    }

    private fun animateMoleDisappear(holeIndex: Int) {
        val mole = moleViews.getOrNull(holeIndex) ?: return
        if (mole.visibility != View.VISIBLE || mole.alpha <= 0.1f) return

        mole.animate().cancel()
        val height = if (mole.height > 0) mole.height.toFloat() else 180f
        mole.animate()
            .translationY(height * 0.75f)
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                mole.visibility = View.INVISIBLE
                mole.translationY = 0f
                mole.alpha = 1f
            }
            .start()
    }

    private fun onScoreUpdated(newScore: Int, delta: Int, combo: Int) {
        binding.tvScore.text = newScore.toString()

        // Pulse score text
        binding.tvScore.animate().cancel()
        binding.tvScore.scaleX = 1.3f
        binding.tvScore.scaleY = 1.3f
        binding.tvScore.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200L)
            .setInterpolator(OvershootInterpolator(2.0f))
            .start()

        // Update Combo
        if (combo > 1) {
            binding.tvCombo.text = getString(R.string.dap_chuot_combo_prefix) + combo
            binding.tvCombo.setTextColor(Color.parseColor("#FF6D00"))
            binding.tvCombo.animate().cancel()
            binding.tvCombo.scaleX = 1.35f
            binding.tvCombo.scaleY = 1.35f
            binding.tvCombo.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(180L)
                .start()
        } else {
            binding.tvCombo.text = "x0"
            binding.tvCombo.setTextColor(Color.parseColor("#FFA726"))
        }

        // Haptic feel
        when {
            delta > 20 -> vibrateLight(45) // Golden or high combo
            delta < 0 -> vibrateLight(80)  // Bomb hit
            else -> vibrateLight(25)       // Normal
        }

        // Floating score popup effect
        showFloatingScore(delta, combo)
    }

    private fun showFloatingScore(delta: Int, combo: Int) {
        val text = when {
            delta > 25 -> "+$delta ⭐"
            delta > 0 && combo >= 3 -> "+$delta (x$combo)!"
            delta > 0 -> "+$delta"
            else -> "$delta 💥"
        }

        val color = when {
            delta > 20 -> Color.parseColor("#FFD700")
            delta < 0 -> Color.parseColor("#FF3D00")
            else -> Color.parseColor("#FFF9C4")
        }

        binding.tvScorePopup.text = text
        binding.tvScorePopup.setTextColor(color)
        binding.tvScorePopup.visibility = View.VISIBLE
        binding.tvScorePopup.alpha = 1f
        binding.tvScorePopup.scaleX = 0.6f
        binding.tvScorePopup.scaleY = 0.6f

        // Center on score panel
        binding.tvScorePopup.x = (binding.root.width - binding.tvScorePopup.width) / 2f
        binding.tvScorePopup.y = binding.scorePanel.y + binding.scorePanel.height + 20f

        val moveUp = ObjectAnimator.ofFloat(binding.tvScorePopup, "translationY", 0f, -50f)
        val scaleUp = ObjectAnimator.ofFloat(binding.tvScorePopup, "scaleX", 0.6f, 1.15f)
        val scaleUpY = ObjectAnimator.ofFloat(binding.tvScorePopup, "scaleY", 0.6f, 1.15f)
        val fadeOut = ObjectAnimator.ofFloat(binding.tvScorePopup, "alpha", 1f, 0f).apply {
            startDelay = 220L
            duration = 250L
        }

        AnimatorSet().apply {
            playTogether(moveUp, scaleUp, scaleUpY, fadeOut)
            duration = 450L
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.tvScorePopup.visibility = View.GONE
                }
            })
            start()
        }
    }

    private fun updateTimerUI(remainingSeconds: Int) {
        binding.tvTimer.text = "${remainingSeconds}s"

        // Smooth width change for progress bar
        val totalWidth = binding.timerBarContainer.width
        val targetWidth = (totalWidth * (remainingSeconds / 60f)).toInt()
        val params = binding.timerBarFill.layoutParams
        params.width = targetWidth
        binding.timerBarFill.layoutParams = params

        // Urgency color when low on time
        if (remainingSeconds <= 10) {
            binding.tvTimer.setTextColor(Color.parseColor("#FF1744"))
            binding.tvTimer.animate().cancel()
            binding.tvTimer.scaleX = 1.25f
            binding.tvTimer.scaleY = 1.25f
            binding.tvTimer.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200L).start()
        } else {
            binding.tvTimer.setTextColor(Color.WHITE)
        }
    }

    private fun startReadyCountdown() {
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            binding.btnStart.visibility = View.GONE
            val steps = listOf("3", "2", "1", getString(R.string.dap_chuot_countdown_go))

            for (step in steps) {
                binding.tvCountdownOverlay.text = step
                binding.tvCountdownOverlay.scaleX = 0.3f
                binding.tvCountdownOverlay.scaleY = 0.3f
                binding.tvCountdownOverlay.alpha = 0f
                binding.tvCountdownOverlay.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .alpha(1f)
                    .setDuration(220L)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()

                vibrateLight(20)
                delay(650L)
            }

            // Hide overlay
            binding.startOverlay.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction {
                    binding.startOverlay.visibility = View.GONE
                    engine.start(lifecycleScope)
                }
                .start()
        }
    }

    private fun onGameEnded(finalScore: Int) {
        val oldHigh = prefs.highScore
        val isNewRecord = finalScore > oldHigh
        if (isNewRecord) {
            prefs.highScore = finalScore
            updateBestScoreView()
            vibrateCelebration()
        } else if (finalScore > 0) {
            vibrateLight(40)
        }

        prefs.totalGamesPlayed++

        // Bonus lucky coins: 1 coin per 10 points
        val earnedCoins = finalScore / 10
        if (earnedCoins > 0) {
            coinPrefs.addCoins(earnedCoins)
        }

        showResultDialog(finalScore, prefs.highScore, isNewRecord, earnedCoins)
    }

    @SuppressLint("SetTextI18n")
    private fun showResultDialog(score: Int, best: Int, isNewRecord: Boolean, earnedCoins: Int) {
        if (isFinishing || isDestroyed) return

        val dialogBinding = DialogDapChuotResultBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.tvResultScore.text = score.toString()
        dialogBinding.tvResultBest.text = best.toString()

        if (isNewRecord) {
            dialogBinding.tvNewRecord.visibility = View.VISIBLE
            dialogBinding.tvResultTitle.text = getString(R.string.dap_chuot_new_record)
        } else {
            dialogBinding.tvNewRecord.visibility = View.GONE
            dialogBinding.tvResultTitle.text = getString(R.string.dap_chuot_result_title)
        }

        if (earnedCoins > 0) {
            dialogBinding.tvResultScoreLabel.text = "${getString(R.string.dap_chuot_score)} (+$earnedCoins Xu)"
        }

        dialogBinding.btnPlayAgain.setSafeOnClickListener {
            dialog.dismiss()
            resetGameUI()
            startReadyCountdown()
        }

        dialogBinding.btnExitResult.setSafeOnClickListener {
            dialog.dismiss()
            finish()
        }

        resultDialog = dialog
        dialog.show()
    }

    private fun resetGameUI() {
        binding.tvScore.text = "0"
        binding.tvCombo.text = "x0"
        binding.tvTimer.text = "60s"
        val params = binding.timerBarFill.layoutParams
        params.width = binding.timerBarContainer.width
        binding.timerBarFill.layoutParams = params

        for (mole in moleViews) {
            mole.animate().cancel()
            mole.visibility = View.INVISIBLE
            mole.translationY = 0f
            mole.alpha = 1f
            mole.scaleX = 1f
            mole.scaleY = 1f
        }

        binding.startOverlay.visibility = View.VISIBLE
        binding.startOverlay.alpha = 1f
        binding.btnStart.visibility = View.VISIBLE
        binding.tvCountdownOverlay.text = getString(R.string.dap_chuot_ready)
    }

    private fun updateBestScoreView() {
        binding.tvBest.text = prefs.highScore.toString()
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.feature_dap_chuot))
            .setMessage("Bạn có chắc muốn thoát khi ván chơi đang diễn ra?")
            .setPositiveButton("Thoát") { _, _ ->
                engine.stop()
                finish()
            }
            .setNegativeButton("Tiếp tục", null)
            .show()
    }

    private fun vibrateLight(millis: Long) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }

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
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }

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
        if (engine.isGameRunning()) {
            engine.stop()
            binding.startOverlay.visibility = View.VISIBLE
            binding.startOverlay.alpha = 1f
            binding.btnStart.visibility = View.VISIBLE
            binding.tvCountdownOverlay.text = getString(R.string.dap_chuot_ready)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        countdownJob?.cancel()
        resultDialog?.dismiss()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DapChuotActivity::class.java)
            context.startActivity(intent)
        }
    }
}

