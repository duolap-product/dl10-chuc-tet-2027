package online.duolap.tetviet.game.oanquan.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.AdPlacement
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.views.CoinFlyAnimator
import online.duolap.tetviet.databinding.ActivityOAnQuanV2Binding
import online.duolap.tetviet.databinding.DialogOAnQuanModeBinding
import online.duolap.tetviet.databinding.DialogOAnQuanResultV2Binding
import online.duolap.tetviet.databinding.DialogOAnQuanRoomBinding
import online.duolap.tetviet.databinding.DialogOAnQuanRulesBinding
import online.duolap.tetviet.game.oanquan.ai.AIDifficulty
import online.duolap.tetviet.game.oanquan.ai.MinimaxBot
import online.duolap.tetviet.game.oanquan.engine.MoveDirection
import online.duolap.tetviet.game.oanquan.engine.OAnQuanBoardState
import online.duolap.tetviet.game.oanquan.engine.OAnQuanEngine
import online.duolap.tetviet.game.oanquan.engine.OAnQuanPreferences
import online.duolap.tetviet.game.oanquan.engine.OAnQuanStep
import online.duolap.tetviet.game.oanquan.engine.PlayerSide
import online.duolap.tetviet.game.oanquan.online.OAnQuanOnlineManager
import online.duolap.tetviet.game.oanquan.online.OAnQuanRoomData
import online.duolap.tetviet.views.setSafeOnClickListener
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class OAnQuanActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: OAnQuanPreferences

    @Inject
    lateinit var coinPrefs: BauCuaPreferences

    @Inject
    lateinit var adManager: AdManager

    private lateinit var binding: ActivityOAnQuanV2Binding
    private var gameState: OAnQuanBoardState = OAnQuanEngine.createInitialState()
    private val minimaxBot = MinimaxBot(PlayerSide.PLAYER_2)
    private val onlineManager = OAnQuanOnlineManager()

    private var currentMode: String = OAnQuanPreferences.MODE_AI
    private var currentOnlineRoomId: String? = null
    private var onlineRoomListener: ListenerRegistration? = null
    private var isOnlineHost: Boolean = true

    private var botJob: Job? = null
    private var introJob: Job? = null
    private var isAnimating = false
    private var movesMade = 0

    // Trạng thái ngay trước nước đi gần nhất của người chơi (chỉ dùng ở chế độ chơi với Máy).
    // Đi lại = quay về trạng thái này, bỏ luôn nước đáp trả của Máy.
    private var undoSnapshot: OAnQuanBoardState? = null
    private var freeHintUsed = false
    private var resultUndoUsed = false
    private var chainCount = 0
    private var lastGameOver: OAnQuanStep.GameOver? = null

    private var resultBinding: DialogOAnQuanResultV2Binding? = null
    // Kết quả ván chờ ghi vào thống kê; hoãn tới khi đóng thẻ kết quả vì người chơi có thể đi lại
    private var pendingRecordWin: Boolean? = null

    private var shownP1Score = 0
    private var shownP2Score = 0
    private var thinkingAnimator: ObjectAnimator? = null
    private var lastStoneVibrateMs = 0L
    private val location = IntArray(2)
    private val location2 = IntArray(2)

    companion object {
        private const val WIN_COINS = 100
        private val STAR_MARGINS = intArrayOf(1, 10, 25)

        fun start(context: Context) {
            val intent = Intent(context, OAnQuanActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOAnQuanV2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        AppAnalytics.logScreenView("OAnQuanActivity")

        currentMode = prefs.getLastMode()

        applyWindowInsets()
        setupListeners()
        updateModeDisplay()
        updateCoinsView()
        binding.effectsView.setAmbientPetals(true)
        startNewGame()
    }

    private fun applyWindowInsets() {
        val baseTop = binding.topBar.paddingTop
        val baseBottom = (binding.layoutBottomControls.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOAnQuan) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(binding.topBar.paddingLeft, baseTop + bars.top, binding.topBar.paddingRight, binding.topBar.paddingBottom)
            binding.ivGarland.translationY = bars.top * 0.5f
            binding.layoutBottomControls.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = baseBottom + bars.bottom }
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener {
            vibrate(15)
            finish()
        }
        binding.layoutTitle.setSafeOnClickListener { showModeSelectionDialog() }
        binding.btnRules.setSafeOnClickListener {
            vibrate(15)
            showRulesDialog()
        }
        binding.btnNewGame.setSafeOnClickListener {
            vibrate(15)
            // Bỏ ván đang chơi dở cũng là một điểm nghỉ tự nhiên
            if (movesMade > 0 && !gameState.isGameOver) {
                adManager.showInterstitialAd(this, AdPlacement.OAN_QUAN_BREAK) { startNewGame() }
            } else {
                startNewGame()
            }
        }
        binding.btnHint.setSafeOnClickListener { onHintClicked() }
        binding.btnUndo.setSafeOnClickListener { onUndoClicked() }

        binding.boardView.onMoveSelected = { pitIndex, direction ->
            vibrate(12)
            executeMove(pitIndex, direction)
        }
        binding.boardView.onStep = { step, point -> handleStep(step, point) }
        binding.boardView.onStoneLanded = {
            val now = SystemClock.uptimeMillis()
            if (prefs.isHapticEnabled() && now - lastStoneVibrateMs > 45L) {
                lastStoneVibrateMs = now
                vibrate(8)
            }
        }
    }

    // =====================================================================
    // Vòng đời ván chơi
    // =====================================================================

    private fun startNewGame() {
        botJob?.cancel()
        introJob?.cancel()
        stopThinking()
        binding.boardView.cancelAnimation()
        hideResult()

        gameState = OAnQuanEngine.createInitialState()
        undoSnapshot = null
        freeHintUsed = false
        resultUndoUsed = false
        movesMade = 0
        lastGameOver = null
        isAnimating = true
        shownP1Score = 0
        shownP2Score = 0
        binding.tvPlayerScore.text = "0"
        binding.tvOpponentScore.text = "0"
        binding.boardView.clearForDeal(gameState)
        updateTurnIndicators()
        updateControls()

        introJob = lifecycleScope.launch {
            playIntro()
            binding.boardView.playDealIn(gameState, lifecycleScope) {
                isAnimating = false
                showBanner(getString(R.string.oanquan_first_move, player1Name()), Color.parseColor("#FFFFEB3B"), 28f)
                onTurnReady()
            }
        }
    }

    /** Màn "Bạn VS Máy" ngắn trước mỗi ván. */
    private suspend fun playIntro() {
        val overlay = binding.introOverlay
        binding.tvIntroYou.text = player1Name()
        binding.tvIntroOpp.text = opponentName()
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(180L).start()
        val w = binding.root.width.toFloat().coerceAtLeast(dp(360f))
        binding.tvIntroYou.translationX = -w
        binding.tvIntroOpp.translationX = w
        binding.tvIntroYou.animate().translationX(0f).setDuration(380L).setInterpolator(OvershootInterpolator(1.2f)).start()
        binding.tvIntroOpp.animate().translationX(0f).setDuration(380L).setInterpolator(OvershootInterpolator(1.2f)).start()
        binding.tvIntroVs.scaleX = 0f
        binding.tvIntroVs.scaleY = 0f
        binding.tvIntroVs.rotation = -90f
        binding.tvIntroVs.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(260L).setDuration(360L)
            .setInterpolator(OvershootInterpolator(2.5f)).start()
        delay(1150L)
        overlay.animate().alpha(0f).setDuration(220L).start()
        delay(220L)
        overlay.visibility = View.GONE
        binding.tvIntroVs.animate().setStartDelay(0L)
    }

    private fun isLocalTurn(): Boolean = when (currentMode) {
        OAnQuanPreferences.MODE_AI -> gameState.currentTurn == PlayerSide.PLAYER_1
        else -> true
    }

    /** Sau khi một nước đi (hoặc màn mở ván) diễn xong: chuyển lượt cho người hoặc Máy. */
    private fun onTurnReady() {
        updateTurnIndicators()
        updateControls()
        if (gameState.isGameOver) return
        if (currentMode == OAnQuanPreferences.MODE_AI && gameState.currentTurn == PlayerSide.PLAYER_2) {
            triggerBotMove()
        } else {
            binding.boardView.setInteractive(true)
        }
    }

    private fun executeMove(pitIndex: Int, direction: MoveDirection) {
        if (isAnimating || !OAnQuanEngine.isMoveValid(gameState, gameState.currentTurn, pitIndex)) return

        val mover = gameState.currentTurn
        if (currentMode == OAnQuanPreferences.MODE_AI && mover == PlayerSide.PLAYER_1) {
            undoSnapshot = gameState.deepCopy()
        }
        val before = gameState.deepCopy()
        val steps = OAnQuanEngine.applyMove(gameState, pitIndex, direction)
        movesMade++
        chainCount = 0
        isAnimating = true
        updateControls()

        binding.boardView.animateSteps(before, steps, lifecycleScope) {
            isAnimating = false
            binding.boardView.setBoardState(gameState)
            setScore(PlayerSide.PLAYER_1, gameState.player1Score)
            setScore(PlayerSide.PLAYER_2, gameState.player2Score)

            if (gameState.isGameOver) {
                onGameOver()
                return@animateSteps
            }

            if (currentMode == OAnQuanPreferences.MODE_ONLINE) {
                currentOnlineRoomId?.let { roomId ->
                    val localUid = prefs.getPlayerId()
                    lifecycleScope.launch {
                        onlineManager.sendMove(
                            roomId = roomId,
                            moverUid = localUid,
                            nextTurnUid = if (mover == PlayerSide.PLAYER_1) "OPPONENT" else localUid,
                            pitIndex = pitIndex,
                            direction = direction,
                            boardState = gameState,
                            isHost = isOnlineHost
                        )
                    }
                }
            }
            onTurnReady()
        }
    }

    private fun triggerBotMove() {
        botJob?.cancel()
        botJob = lifecycleScope.launch {
            binding.boardView.setInteractive(false)
            startThinking()
            delay(700)
            val botMove = minimaxBot.findBestMove(gameState.deepCopy(), prefs.getAIDifficulty())
            stopThinking()
            styleTurnBadge(binding.tvOpponentTurnBadge, true, getString(R.string.oanquan_turn_now))
            if (botMove != null) {
                executeMove(botMove.pitIndex, botMove.direction)
            } else {
                binding.boardView.setInteractive(true)
            }
        }
    }

    // =====================================================================
    // Hiệu ứng theo từng bước của nước đi
    // =====================================================================

    private fun handleStep(step: OAnQuanStep, boardPoint: PointF) {
        val (x, y) = toEffectsCoords(boardPoint)
        when (step) {
            is OAnQuanStep.Capture -> {
                chainCount++
                if (prefs.isHapticEnabled()) vibrate(if (step.capturedQuan) 45 else 25)
                binding.effectsView.burstStars(x, y, golden = step.capturedQuan)
                showFloatingPoints(x, y, step.pointsAwarded, step.player)
                setScore(step.player, step.newPlayerScore)

                val mine = step.player == PlayerSide.PLAYER_1 || currentMode == OAnQuanPreferences.MODE_PASS_AND_PLAY
                when {
                    !mine -> showBanner(getString(R.string.oanquan_banner_opp_capture, step.pointsAwarded), Color.parseColor("#FFFFCDD2"), 24f)
                    step.capturedQuan -> {
                        showBanner(getString(R.string.oanquan_banner_capture_quan, step.pointsAwarded), Color.parseColor("#FFFFD54F"), 38f)
                        shakeScreen()
                    }
                    chainCount >= 2 -> showBanner(getString(R.string.oanquan_banner_chain, chainCount), Color.parseColor("#FFFFEB3B"), 34f)
                    else -> showBanner(getString(R.string.oanquan_banner_capture, step.pointsAwarded), Color.WHITE, 28f)
                }
            }
            is OAnQuanStep.ChungQuan -> {
                if (prefs.isHapticEnabled()) vibrate(20)
                showBanner(getString(R.string.oanquan_banner_chung_quan), Color.parseColor("#FFFFE0B2"), 26f)
            }
            is OAnQuanStep.ReliefStones -> {
                val text = if (step.debtAdded > 0) getString(R.string.oanquan_banner_relief_debt, step.debtAdded)
                else getString(R.string.oanquan_banner_relief)
                showBanner(text, Color.parseColor("#FFFFE0B2"), 24f)
                setScore(step.player, step.currentScore)
            }
            is OAnQuanStep.GameOver -> {
                lastGameOver = step
                showBanner(getString(R.string.oanquan_banner_game_over), Color.parseColor("#FFFFEB3B"), 30f)
                setScore(PlayerSide.PLAYER_1, step.p1Score)
                setScore(PlayerSide.PLAYER_2, step.p2Score)
            }
            is OAnQuanStep.DeadEnd -> binding.effectsView.puffDust(x, y)
            else -> {}
        }
    }

    private fun toEffectsCoords(boardPoint: PointF): Pair<Float, Float> {
        binding.boardView.getLocationInWindow(location)
        binding.effectsView.getLocationInWindow(location2)
        return Pair(location[0] - location2[0] + boardPoint.x, location[1] - location2[1] + boardPoint.y)
    }

    private fun scoreBoxFor(player: PlayerSide): View =
        if (player == PlayerSide.PLAYER_1) binding.boxPlayerScore else binding.boxOpponentScore

    /** Điểm "+N" bay từ ô bị ăn về ô điểm của người ăn. */
    private fun showFloatingPoints(x: Float, y: Float, points: Int, player: PlayerSide) {
        val tv = TextView(this).apply {
            text = "+$points"
            setTextColor(if (points >= OAnQuanBoardState.QUAN_VALUE) Color.parseColor("#FFFFD54F") else Color.WHITE)
            textSize = if (points >= OAnQuanBoardState.QUAN_VALUE) 30f else 24f
            typeface = ResourcesCompat.getFont(context, R.font.arimo_bold)
            setShadowLayer(8f, 0f, 3f, Color.parseColor("#E6000000"))
        }
        binding.rootOAnQuan.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        tv.x = x - tv.measuredWidth / 2f
        tv.y = y - tv.measuredHeight / 2f

        val box = scoreBoxFor(player)
        box.getLocationInWindow(location)
        binding.rootOAnQuan.getLocationInWindow(location2)
        val tx = location[0] - location2[0] + box.width / 2f - tv.measuredWidth / 2f
        val ty = location[1] - location2[1] + box.height / 2f - tv.measuredHeight / 2f

        tv.scaleX = 0.4f
        tv.scaleY = 0.4f
        tv.animate().scaleX(1.2f).scaleY(1.2f).translationYBy(-dp(24f)).setDuration(260L)
            .setInterpolator(OvershootInterpolator(2f))
            .withEndAction {
                tv.animate().x(tx).y(ty).scaleX(0.6f).scaleY(0.6f).alpha(0.3f).setDuration(420L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        binding.rootOAnQuan.removeView(tv)
                        bounce(box, 1.25f)
                    }.start()
            }.start()
    }

    private fun setScore(player: PlayerSide, value: Int) {
        val tv = if (player == PlayerSide.PLAYER_1) binding.tvPlayerScore else binding.tvOpponentScore
        val from = if (player == PlayerSide.PLAYER_1) shownP1Score else shownP2Score
        if (player == PlayerSide.PLAYER_1) shownP1Score = value else shownP2Score = value
        if (from == value) {
            tv.text = value.toString()
            return
        }
        ValueAnimator.ofInt(from, value).apply {
            duration = 450L
            startDelay = 300L
            addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    // =====================================================================
    // Thẻ người chơi, lượt, nút điều khiển
    // =====================================================================

    private fun player1Name(): String =
        if (currentMode == OAnQuanPreferences.MODE_PASS_AND_PLAY) getString(R.string.oanquan_player_1) else getString(R.string.oanquan_you)

    private fun opponentName(): String = when (currentMode) {
        OAnQuanPreferences.MODE_AI -> getString(R.string.oanquan_opp_ai_format, difficultyName())
        OAnQuanPreferences.MODE_PASS_AND_PLAY -> getString(R.string.oanquan_player_2)
        else -> binding.tvOpponentName.text?.toString()?.takeIf { it.isNotBlank() } ?: getString(R.string.oanquan_opp_online)
    }

    private fun difficultyName(): String = when (prefs.getAIDifficulty()) {
        AIDifficulty.EASY -> getString(R.string.oanquan_diff_easy)
        AIDifficulty.MEDIUM -> getString(R.string.oanquan_diff_medium)
        AIDifficulty.HARD -> getString(R.string.oanquan_diff_hard)
    }

    private fun updateModeDisplay() {
        binding.tvPlayerName.text = player1Name()
        when (currentMode) {
            OAnQuanPreferences.MODE_AI -> {
                binding.tvModeSubtitle.text = getString(R.string.oanquan_mode_sub_ai, difficultyName())
                binding.tvOpponentName.text = opponentName()
                binding.ivOpponentAvatar.setImageResource(R.drawable.ic_bot_ai)
                binding.btnHint.visibility = View.VISIBLE
                binding.btnUndo.visibility = View.VISIBLE
                binding.tvOpponentTimer.visibility = View.GONE
            }
            OAnQuanPreferences.MODE_PASS_AND_PLAY -> {
                binding.tvModeSubtitle.text = getString(R.string.oanquan_mode_sub_pass)
                binding.tvOpponentName.text = opponentName()
                binding.ivOpponentAvatar.setImageResource(R.drawable.ic_users_group)
                binding.btnHint.visibility = View.VISIBLE
                binding.btnUndo.visibility = View.GONE
                binding.tvOpponentTimer.visibility = View.GONE
            }
            OAnQuanPreferences.MODE_ONLINE -> {
                binding.tvModeSubtitle.text = getString(R.string.oanquan_mode_sub_online)
                binding.tvOpponentName.text = getString(R.string.oanquan_opp_online)
                binding.ivOpponentAvatar.setImageResource(R.drawable.ic_globe_online)
                binding.btnHint.visibility = View.GONE
                binding.btnUndo.visibility = View.GONE
                binding.tvOpponentTimer.visibility = View.VISIBLE
            }
        }
    }

    private fun updateTurnIndicators() {
        val over = gameState.isGameOver
        val p1Turn = !over && gameState.currentTurn == PlayerSide.PLAYER_1
        val p2Turn = !over && gameState.currentTurn == PlayerSide.PLAYER_2

        binding.layoutPlayer.setBackgroundResource(if (p1Turn) R.drawable.bg_oaq_player_card_active else R.drawable.bg_oaq_player_card)
        binding.layoutOpponent.setBackgroundResource(if (p2Turn) R.drawable.bg_oaq_player_card_active else R.drawable.bg_oaq_player_card)

        styleTurnBadge(binding.tvPlayerTurnBadge, p1Turn, getString(R.string.oanquan_turn_your))
        if (thinkingAnimator == null) {
            styleTurnBadge(binding.tvOpponentTurnBadge, p2Turn, getString(R.string.oanquan_turn_now))
        }
        if (p1Turn) bounce(binding.layoutPlayer, 1.04f)
        if (p2Turn) bounce(binding.layoutOpponent, 1.04f)
    }

    private fun styleTurnBadge(badge: TextView, active: Boolean, activeText: String) {
        badge.setBackgroundResource(if (active) R.drawable.bg_oaq_turn_active else R.drawable.bg_oaq_turn_waiting)
        badge.text = if (active) activeText else getString(R.string.oanquan_turn_waiting)
        badge.setTextColor(if (active) Color.parseColor("#FF7A090D") else Color.parseColor("#FFFFF8E1"))
    }

    private fun startThinking() {
        stopThinking()
        styleTurnBadge(binding.tvOpponentTurnBadge, true, getString(R.string.oanquan_thinking))
        thinkingAnimator = ObjectAnimator.ofFloat(binding.tvOpponentTurnBadge, View.ALPHA, 1f, 0.45f).apply {
            duration = 420L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopThinking() {
        thinkingAnimator?.cancel()
        thinkingAnimator = null
        binding.tvOpponentTurnBadge.alpha = 1f
    }

    private fun updateControls() {
        val localTurn = !isAnimating && !gameState.isGameOver && isLocalTurn()
        binding.btnHint.isEnabled = localTurn
        binding.btnHint.alpha = if (localTurn) 1f else 0.5f
        binding.tvHintTag.visibility = if (freeHintUsed) View.GONE else View.VISIBLE
        binding.ivHintIcon.setImageResource(if (freeHintUsed) R.drawable.ic_dc_video else R.drawable.ic_oaq_bulb)

        val canUndo = localTurn && undoSnapshot != null
        binding.btnUndo.isEnabled = canUndo
        binding.btnUndo.alpha = if (canUndo) 1f else 0.5f
    }

    // =====================================================================
    // Gợi ý và Đi lại (quảng cáo có thưởng)
    // =====================================================================

    private fun onHintClicked() {
        if (isAnimating || gameState.isGameOver || !isLocalTurn()) return
        vibrate(15)
        if (!freeHintUsed) {
            // Mỗi ván được 1 lần gợi ý miễn phí, từ lần thứ 2 xem video
            freeHintUsed = true
            updateControls()
            revealHint()
            return
        }
        binding.btnHint.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var rewarded = false
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.OAN_QUAN_HINT,
            onUserEarnedReward = { rewarded = true },
            onDismissed = {
                updateControls()
                if (rewarded) revealHint()
            },
            onError = { message ->
                updateControls()
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun revealHint() {
        lifecycleScope.launch {
            // Bot gợi ý phải tính cho bên đang tới lượt (bot của Máy luôn là người chơi 2)
            val best = MinimaxBot(gameState.currentTurn).findBestMove(gameState.deepCopy(), AIDifficulty.HARD)
            if (best == null || isAnimating) {
                if (best == null) Toast.makeText(this@OAnQuanActivity, R.string.oanquan_hint_none, Toast.LENGTH_SHORT).show()
                return@launch
            }
            binding.boardView.showHint(best.pitIndex, best.direction)
            showBanner(getString(R.string.oanquan_hint_banner), Color.parseColor("#FFFFF8E1"), 20f)
        }
    }

    private fun onUndoClicked() {
        if (isAnimating || gameState.isGameOver || !isLocalTurn() || undoSnapshot == null) return
        vibrate(15)
        watchUndoAd { performUndo() }
    }

    private fun watchUndoAd(onRewarded: () -> Unit) {
        binding.btnUndo.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var rewarded = false
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.OAN_QUAN_UNDO,
            onUserEarnedReward = { rewarded = true },
            onDismissed = {
                updateControls()
                if (rewarded) onRewarded()
            },
            onError = { message ->
                updateControls()
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun performUndo() {
        val snapshot = undoSnapshot ?: return
        botJob?.cancel()
        stopThinking()
        binding.boardView.cancelAnimation()
        isAnimating = false
        gameState = snapshot
        undoSnapshot = null
        binding.boardView.setBoardState(gameState)
        setScore(PlayerSide.PLAYER_1, gameState.player1Score)
        setScore(PlayerSide.PLAYER_2, gameState.player2Score)
        showBanner(getString(R.string.oanquan_banner_undo), Color.parseColor("#FFB9F6CA"), 30f)
        onTurnReady()
    }

    // =====================================================================
    // Kết quả
    // =====================================================================

    private fun onGameOver() {
        stopThinking()
        updateTurnIndicators()
        updateControls()
        binding.boardView.setInteractive(false)
        if (gameState.winner == PlayerSide.PLAYER_1) vibrateCelebration()
        lifecycleScope.launch {
            delay(800)
            showResult()
        }
    }

    private fun showResult() {
        if (isFinishing || isDestroyed || binding.resultOverlay.isVisible) return
        val winner = gameState.winner
        val p1Wins = winner == PlayerSide.PLAYER_1
        val p1Final = gameState.player1Score - gameState.player1Debt
        val p2Final = gameState.player2Score - gameState.player2Debt

        val coins = if (p1Wins) WIN_COINS else 0
        val coinsBefore = coinPrefs.getCoins()
        if (coins > 0) {
            // Giữ số dư cũ tới khi xu bay từ thẻ kết quả lên thanh trên cùng
            CoinFlyAnimator.hold(binding.tvCoins)
            coinPrefs.addCoins(coins)
        }
        pendingRecordWin = p1Wins

        val rb = DialogOAnQuanResultV2Binding.inflate(layoutInflater, binding.resultOverlay, false)
        resultBinding = rb
        binding.resultOverlay.removeAllViews()
        binding.resultOverlay.addView(rb.root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))

        val canUndo = currentMode == OAnQuanPreferences.MODE_AI && winner == PlayerSide.PLAYER_2 &&
            undoSnapshot != null && !resultUndoUsed
        rb.tvResultTitle.text = when {
            winner == null -> getString(R.string.oanquan_result_draw)
            currentMode == OAnQuanPreferences.MODE_PASS_AND_PLAY ->
                getString(R.string.oanquan_result_player_wins, if (p1Wins) player1Name() else opponentName())
            p1Wins -> getString(R.string.oanquan_result_win)
            else -> getString(R.string.oanquan_result_lose)
        }
        rb.tvResultDesc.text = when {
            winner == null -> getString(R.string.oanquan_result_desc_draw)
            p1Wins || currentMode == OAnQuanPreferences.MODE_PASS_AND_PLAY -> getString(R.string.oanquan_result_desc_win)
            canUndo -> getString(R.string.oanquan_result_desc_lose)
            else -> getString(R.string.oanquan_result_desc_lose_plain)
        }
        rb.tvP1Name.text = player1Name()
        rb.tvP2Name.text = opponentName()
        val go = lastGameOver
        val p1Harvest = go?.p1Harvest ?: 0
        val p2Harvest = go?.p2Harvest ?: 0
        rb.tvP1Captured.text = (gameState.player1Score - p1Harvest).toString()
        rb.tvP2Captured.text = (gameState.player2Score - p2Harvest).toString()
        rb.tvP1Harvest.text = "+$p1Harvest"
        rb.tvP2Harvest.text = "+$p2Harvest"
        rb.tvP1Debt.text = "-${gameState.player1Debt}"
        rb.tvP2Debt.text = "-${gameState.player2Debt}"
        rb.tvP1Final.setTextColor(Color.parseColor(if (winner == PlayerSide.PLAYER_2) "#FF5D2A12" else "#FFC62828"))
        rb.tvP2Final.setTextColor(Color.parseColor(if (winner == PlayerSide.PLAYER_2) "#FFC62828" else "#FF5D2A12"))

        rb.boxCoins.visibility = if (coins > 0) View.VISIBLE else View.GONE
        rb.tvResultCoins.text = getString(R.string.oanquan_result_coins, coins)

        when {
            coins > 0 -> {
                rb.tvRewarded.text = getString(R.string.oanquan_result_double)
                rb.btnRewarded.setSafeOnClickListener { watchDoubleCoinsAd(rb, coins) }
            }
            canUndo -> {
                rb.tvRewarded.text = getString(R.string.oanquan_result_undo)
                rb.btnRewarded.setSafeOnClickListener { watchResultUndoAd(rb) }
            }
            else -> rb.btnRewarded.visibility = View.GONE
        }
        rb.btnReplayGame.setSafeOnClickListener { closeResultThen { startNewGame() } }
        rb.btnResultChangeMode.setSafeOnClickListener { closeResultThen { showModeSelectionDialog() } }

        binding.resultOverlay.visibility = View.VISIBLE
        binding.resultOverlay.alpha = 0f
        binding.resultOverlay.animate().alpha(1f).setDuration(200L).start()
        rb.root.scaleX = 0.5f
        rb.root.scaleY = 0.5f
        rb.root.animate().scaleX(1f).scaleY(1f).setDuration(380L).setInterpolator(OvershootInterpolator(1.6f)).start()

        animateResult(rb, p1Final, p2Final, winner, coinsBefore, coins)
    }

    private fun animateResult(
        rb: DialogOAnQuanResultV2Binding, p1Final: Int, p2Final: Int, winner: PlayerSide?,
        coinsBefore: Int, coins: Int
    ) {
        for ((tv, value) in listOf(rb.tvP1Final to p1Final, rb.tvP2Final to p2Final)) {
            ValueAnimator.ofInt(0, value).apply {
                duration = 700L
                startDelay = 250L
                interpolator = DecelerateInterpolator()
                addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
                start()
            }
        }

        // Sao theo cách biệt điểm của người thắng; thua hoặc hoà thì sao để trống
        val margin = abs(p1Final - p2Final)
        val showStars = winner == PlayerSide.PLAYER_1 ||
            (winner != null && currentMode == OAnQuanPreferences.MODE_PASS_AND_PLAY)
        val stars = if (showStars) STAR_MARGINS.count { margin >= it } else 0
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
                binding.resultEffectsView.getLocationInWindow(location2)
                binding.resultEffectsView.burstStars(location[0] - location2[0] + star.width / 2f,
                    location[1] - location2[1] + star.height / 2f, golden = true)
                vibrate(25)
            }, 650L + i * 320L)
        }

        if (showStars) {
            rb.root.postDelayed({
                if (resultBinding !== rb) return@postDelayed
                binding.resultEffectsView.rainConfetti()
                if (rb.boxCoins.isVisible) {
                    rb.boxCoins.getLocationInWindow(location)
                    binding.resultEffectsView.getLocationInWindow(location2)
                    binding.resultEffectsView.burstCoins(location[0] - location2[0] + rb.boxCoins.width / 2f,
                        location[1] - location2[1] + rb.boxCoins.height / 2f, 12)
                    bounce(rb.boxCoins, 1.2f)
                }
            }, 650L + stars * 320L)
        }
        if (coins > 0) {
            rb.root.postDelayed({ flyCoinsToHud(rb, coinsBefore, coinsBefore + coins) }, 650L + stars * 320L + 350L)
        }
    }

    private fun watchDoubleCoinsAd(rb: DialogOAnQuanResultV2Binding, coins: Int) {
        rb.btnRewarded.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var rewarded = false
        var before = 0
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.OAN_QUAN_DOUBLE_COINS,
            // Cộng xu ngay để không mất thưởng; hiệu ứng chạy khi quảng cáo đã đóng để người chơi thấy
            onUserEarnedReward = {
                rewarded = true
                before = coinPrefs.getCoins()
                CoinFlyAnimator.hold(binding.tvCoins)
                coinPrefs.addCoins(coins)
            },
            onDismissed = {
                rb.btnRewarded.isEnabled = !rewarded
                if (!rewarded) return@showRewardedAd
                rb.tvResultCoins.text = getString(R.string.oanquan_result_coins, coins * 2)
                rb.tvRewarded.text = getString(R.string.oanquan_result_double_done, coins * 2)
                rb.boxCoins.getLocationInWindow(location)
                binding.resultEffectsView.getLocationInWindow(location2)
                binding.resultEffectsView.burstCoins(location[0] - location2[0] + rb.boxCoins.width / 2f,
                    location[1] - location2[1] + rb.boxCoins.height / 2f, 14)
                bounce(rb.boxCoins, 1.2f)
                vibrateCelebration()
                rb.root.postDelayed({ flyCoinsToHud(rb, before, before + coins) }, 450L)
            },
            onError = { message ->
                rb.btnRewarded.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun watchResultUndoAd(rb: DialogOAnQuanResultV2Binding) {
        rb.btnRewarded.isEnabled = false
        Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
        var rewarded = false
        adManager.showRewardedAd(
            activity = this,
            placement = AdPlacement.OAN_QUAN_UNDO,
            onUserEarnedReward = { rewarded = true },
            onDismissed = {
                rb.btnRewarded.isEnabled = true
                if (rewarded) {
                    // Ván chưa kết thúc nên không ghi vào thống kê; mỗi ván chỉ được cứu một lần
                    resultUndoUsed = true
                    pendingRecordWin = null
                    hideResult()
                    performUndo()
                }
            },
            onError = { message ->
                rb.btnRewarded.isEnabled = true
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    /** Đóng thẻ kết quả trước, rồi mới xét quảng cáo xen kẽ ở điểm nghỉ giữa hai ván. */
    private fun closeResultThen(action: () -> Unit) {
        commitPendingRecord()
        hideResult()
        adManager.showInterstitialAd(this, AdPlacement.OAN_QUAN_BREAK) { action() }
    }

    private fun commitPendingRecord() {
        pendingRecordWin?.let { prefs.recordGameFinished(it) }
        pendingRecordWin = null
    }

    private fun hideResult() {
        resultBinding = null
        binding.resultEffectsView.clear()
        binding.resultOverlay.visibility = View.GONE
        binding.resultOverlay.removeAllViews()
    }

    // =====================================================================
    // Hộp thoại (giữ nguyên giao diện cũ)
    // =====================================================================

    @SuppressLint("InflateParams")
    private fun showModeSelectionDialog() {
        val dialogBinding = DialogOAnQuanModeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        fun updateDiffChips(diff: AIDifficulty) {
            prefs.setAIDifficulty(diff)
            dialogBinding.chipDiffEasy.setBackgroundResource(if (diff == AIDifficulty.EASY) R.drawable.bg_wishing_chip_selected else R.drawable.bg_wishing_chip_normal)
            dialogBinding.chipDiffMedium.setBackgroundResource(if (diff == AIDifficulty.MEDIUM) R.drawable.bg_wishing_chip_selected else R.drawable.bg_wishing_chip_normal)
            dialogBinding.chipDiffHard.setBackgroundResource(if (diff == AIDifficulty.HARD) R.drawable.bg_wishing_chip_selected else R.drawable.bg_wishing_chip_normal)
        }
        updateDiffChips(prefs.getAIDifficulty())

        dialogBinding.chipDiffEasy.setOnClickListener { updateDiffChips(AIDifficulty.EASY) }
        dialogBinding.chipDiffMedium.setOnClickListener { updateDiffChips(AIDifficulty.MEDIUM) }
        dialogBinding.chipDiffHard.setOnClickListener { updateDiffChips(AIDifficulty.HARD) }

        dialogBinding.btnModeAI.setSafeOnClickListener {
            currentMode = OAnQuanPreferences.MODE_AI
            prefs.setLastMode(currentMode)
            updateModeDisplay()
            startNewGame()
            dialog.dismiss()
        }

        dialogBinding.btnModePassAndPlay.setSafeOnClickListener {
            currentMode = OAnQuanPreferences.MODE_PASS_AND_PLAY
            prefs.setLastMode(currentMode)
            updateModeDisplay()
            startNewGame()
            dialog.dismiss()
        }

        dialogBinding.btnModeOnline.setSafeOnClickListener {
            dialog.dismiss()
            showOnlineRoomDialog()
        }

        dialogBinding.btnCloseMode.setSafeOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // Dialog: Online Matchmaking & Room Code
    @SuppressLint("InflateParams")
    private fun showOnlineRoomDialog() {
        val dialogBinding = DialogOAnQuanRoomBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val localUid = prefs.getPlayerId()
        val localName = prefs.getPlayerName()

        // Tìm trận nhanh
        dialogBinding.btnQuickMatch.setSafeOnClickListener {
            dialogBinding.tvQuickMatchText.text = "Đang tìm trận..."
            dialogBinding.btnQuickMatch.isEnabled = false

            lifecycleScope.launch {
                val matchResult = onlineManager.quickMatch(localUid, localName)
                if (matchResult.isSuccess) {
                    val roomId = matchResult.getOrThrow()
                    currentOnlineRoomId = roomId
                    isOnlineHost = true
                    currentMode = OAnQuanPreferences.MODE_ONLINE
                    prefs.setLastMode(currentMode)
                    updateModeDisplay()
                    startNewGame()
                    dialog.dismiss()
                    listenToOnlineRoom(roomId)
                    Toast.makeText(this@OAnQuanActivity, "Đã vào phòng $roomId!", Toast.LENGTH_SHORT).show()
                } else {
                    dialogBinding.tvQuickMatchText.text = "Ghép Trận Nhanh"
                    dialogBinding.btnQuickMatch.isEnabled = true
                    Toast.makeText(this@OAnQuanActivity, "Không thể kết nối phòng!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Tạo phòng mới 6 số
        dialogBinding.btnCreateRoom.setSafeOnClickListener {
            lifecycleScope.launch {
                val createResult = onlineManager.createRoom(localUid, localName)
                if (createResult.isSuccess) {
                    val roomId = createResult.getOrThrow()
                    currentOnlineRoomId = roomId
                    isOnlineHost = true
                    dialogBinding.tvDisplayRoomCode.text = roomId
                    dialogBinding.layoutRoomCreatedInfo.visibility = View.VISIBLE
                    listenToOnlineRoom(roomId)
                } else {
                    Toast.makeText(this@OAnQuanActivity, "Không thể tạo phòng!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Vào phòng bằng mã 6 số
        dialogBinding.btnJoinRoom.setSafeOnClickListener {
            val code = dialogBinding.etRoomCode.text?.toString()?.trim().orEmpty()
            if (code.length != 6) {
                Toast.makeText(this, "Vui lòng nhập đúng 6 chữ số mã phòng!", Toast.LENGTH_SHORT).show()
                return@setSafeOnClickListener
            }

            lifecycleScope.launch {
                val joinResult = onlineManager.joinRoom(code, localUid, localName)
                if (joinResult.isSuccess) {
                    currentOnlineRoomId = code
                    isOnlineHost = false
                    currentMode = OAnQuanPreferences.MODE_ONLINE
                    prefs.setLastMode(currentMode)
                    updateModeDisplay()
                    startNewGame()
                    dialog.dismiss()
                    listenToOnlineRoom(code)
                    Toast.makeText(this@OAnQuanActivity, "Đã tham gia phòng $code!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OAnQuanActivity, joinResult.exceptionOrNull()?.message ?: "Lỗi vào phòng!", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialogBinding.btnCloseRoom.setSafeOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun listenToOnlineRoom(roomId: String) {
        onlineRoomListener?.remove()
        onlineRoomListener = onlineManager.listenRoom(
            roomId = roomId,
            onUpdate = { roomData ->
                if (roomData.status == OAnQuanRoomData.STATUS_PLAYING) {
                    binding.tvOpponentName.text = if (isOnlineHost) roomData.guestName ?: "Đối thủ" else roomData.hostName
                }
            },
            onError = {}
        )
    }

    // Dialog: Luật Chơi Dân Gian
    @SuppressLint("InflateParams")
    private fun showRulesDialog() {
        val dialogBinding = DialogOAnQuanRulesBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnCloseRules.setSafeOnClickListener { dialog.dismiss() }
        dialogBinding.btnUnderstood.setSafeOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // =====================================================================
    // Tiện ích
    // =====================================================================

    private fun showBanner(text: String, color: Int, sizeSp: Float) {
        val tv = binding.tvBanner
        tv.animate().cancel()
        tv.text = text
        tv.setTextColor(color)
        tv.textSize = sizeSp
        tv.visibility = View.VISIBLE
        tv.alpha = 0f
        tv.translationY = 0f
        tv.scaleX = 0.3f
        tv.scaleY = 0.3f
        tv.rotation = -8f
        tv.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(280L).setInterpolator(OvershootInterpolator(2.6f))
            .withEndAction {
                tv.animate().alpha(0f).translationYBy(-dp(30f)).setStartDelay(650L).setDuration(240L)
                    .withEndAction {
                        tv.visibility = View.GONE
                        tv.translationY = 0f
                        tv.animate().setStartDelay(0L)
                    }.start()
            }.start()
    }

    private fun shakeScreen() {
        ObjectAnimator.ofFloat(binding.gameContent, View.TRANSLATION_X, 0f, -dp(8f), dp(7f), -dp(5f), dp(3f), 0f).apply {
            duration = 380L
            start()
        }
    }

    private fun bounce(view: View, to: Float) {
        view.animate().cancel()
        view.scaleX = to
        view.scaleY = to
        view.animate().scaleX(1f).scaleY(1f).setDuration(220L).setInterpolator(OvershootInterpolator(3f)).start()
    }

    private fun updateCoinsView() {
        if (CoinFlyAnimator.isHeld(binding.tvCoins)) return
        binding.tvCoins.text = coinPrefs.getCoins().toString()
    }

    /** Xu bay từ ô xu trên thẻ kết quả vào ô số dư ở thanh trên cùng, số dư đếm lên dần. */
    private fun flyCoinsToHud(rb: DialogOAnQuanResultV2Binding, start: Int, end: Int) {
        if (resultBinding !== rb || !rb.boxCoins.isAttachedToWindow) {
            CoinFlyAnimator.release(binding.tvCoins)
            updateCoinsView()
            return
        }
        val (fx, fy) = CoinFlyAnimator.centerOnScreen(rb.boxCoins)
        CoinFlyAnimator.fly(this, fx, fy, binding.tvCoins.parent as View, binding.tvCoins, start, end)
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
                val timings = longArrayOf(0, 80, 100, 80, 100, 120)
                val amplitudes = intArrayOf(0, 180, 0, 200, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(350)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) commitPendingRecord()
        onlineRoomListener?.remove()
        super.onDestroy()
    }
}
