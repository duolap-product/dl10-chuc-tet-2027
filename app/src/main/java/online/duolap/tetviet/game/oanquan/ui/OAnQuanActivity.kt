package online.duolap.tetviet.game.oanquan.ui

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
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.databinding.ActivityOAnQuanBinding
import online.duolap.tetviet.databinding.DialogOAnQuanModeBinding
import online.duolap.tetviet.databinding.DialogOAnQuanResultBinding
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

@AndroidEntryPoint
class OAnQuanActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: OAnQuanPreferences

    @Inject
    lateinit var coinPrefs: BauCuaPreferences

    private lateinit var binding: ActivityOAnQuanBinding
    private var gameState: OAnQuanBoardState = OAnQuanEngine.createInitialState()
    private val minimaxBot = MinimaxBot(PlayerSide.PLAYER_2)
    private val onlineManager = OAnQuanOnlineManager()

    private var currentMode: String = OAnQuanPreferences.MODE_AI
    private var currentOnlineRoomId: String? = null
    private var onlineRoomListener: ListenerRegistration? = null
    private var isOnlineHost: Boolean = true

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OAnQuanActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOAnQuanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppAnalytics.logScreenView("OAnQuanActivity")

        currentMode = prefs.getLastMode()

        applyWindowInsets()
        setupListeners()
        updateModeDisplay()
        startNewGame()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOAnQuan) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top + 8,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener {
            vibrateLight(15)
            finish()
        }

        binding.layoutTitle.setSafeOnClickListener {
            showModeSelectionDialog()
        }

        binding.btnRules.setSafeOnClickListener {
            vibrateLight(15)
            showRulesDialog()
        }

        binding.btnSoundToggle.setSafeOnClickListener {
            val newState = !prefs.isSoundEnabled()
            prefs.setSoundEnabled(newState)
            updateSoundIcon()
            val msg = if (newState) "Đã bật âm thanh" else "Đã tắt âm thanh"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnChangeMode.setSafeOnClickListener {
            vibrateLight(15)
            showModeSelectionDialog()
        }

        binding.btnNewGame.setSafeOnClickListener {
            vibrateLight(15)
            startNewGame()
        }

        binding.btnHint.setSafeOnClickListener {
            vibrateLight(20)
            provideMoveHint()
        }

        // Bàn cờ tương tác khi người chơi chọn ô và hướng đi
        binding.boardView.onMoveSelectedListener = { pitIndex, direction ->
            executeMove(pitIndex, direction)
        }

        binding.boardView.onStepAnimatedListener = { step ->
            when (step) {
                is OAnQuanStep.DropOne -> {
                    if (prefs.isHapticEnabled()) vibrateLight(12)
                }
                is OAnQuanStep.Capture -> {
                    if (prefs.isHapticEnabled()) vibrateLight(28)
                    updateScoreViews()
                }
                is OAnQuanStep.ChungQuan -> {
                    if (prefs.isHapticEnabled()) vibrateLight(20)
                }
                is OAnQuanStep.ReliefStones -> {
                    updateScoreViews()
                }
                else -> {}
            }
        }
    }

    private fun updateSoundIcon() {
        val isEnabled = prefs.isSoundEnabled()
        binding.ivSoundIcon.setImageResource(
            if (isEnabled) R.drawable.ic_music_note else R.drawable.ic_music_off
        )
    }

    private fun startNewGame() {
        gameState = OAnQuanEngine.createInitialState()
        binding.boardView.setBoardState(gameState)
        binding.boardView.setInteractive(true)
        updateScoreViews()
        updateTurnIndicators()
        updateSoundIcon()
    }

    private fun updateScoreViews() {
        binding.tvPlayerScore.text = "${gameState.player1Score} đ"
        binding.tvOpponentScore.text = "${gameState.player2Score} đ"
    }

    private fun updateTurnIndicators() {
        val isP1Turn = gameState.currentTurn == PlayerSide.PLAYER_1

        if (isP1Turn) {
            binding.tvPlayerTurnBadge.setBackgroundResource(R.drawable.bg_oanquan_turn_active)
            binding.tvPlayerTurnBadge.setText(R.string.oanquan_turn_your)
            binding.tvOpponentTurnBadge.setBackgroundResource(R.drawable.bg_oanquan_turn_waiting)
            binding.tvOpponentTurnBadge.setText(R.string.oanquan_turn_opp)
        } else {
            binding.tvPlayerTurnBadge.setBackgroundResource(R.drawable.bg_oanquan_turn_waiting)
            binding.tvPlayerTurnBadge.setText(R.string.oanquan_turn_opp)
            binding.tvOpponentTurnBadge.setBackgroundResource(R.drawable.bg_oanquan_turn_active)
            binding.tvOpponentTurnBadge.setText(R.string.oanquan_turn_your)
        }
    }

    private fun updateModeDisplay() {
        when (currentMode) {
            OAnQuanPreferences.MODE_AI -> {
                val diff = prefs.getAIDifficulty()
                val diffName = when (diff) {
                    AIDifficulty.EASY -> getString(R.string.oanquan_diff_easy)
                    AIDifficulty.MEDIUM -> getString(R.string.oanquan_diff_medium)
                    AIDifficulty.HARD -> getString(R.string.oanquan_diff_hard)
                }
                binding.tvModeSubtitle.text = "Chơi với Máy ($diffName) ▾"
                binding.tvOpponentName.text = "Máy ($diffName)"
                binding.ivOpponentAvatar.setImageResource(R.drawable.ic_bot_ai)
                binding.btnHint.visibility = View.VISIBLE
                binding.tvOpponentTimer.visibility = View.GONE
            }
            OAnQuanPreferences.MODE_PASS_AND_PLAY -> {
                binding.tvModeSubtitle.text = "Chơi với Bạn (Cùng máy) ▾"
                binding.tvOpponentName.text = "Người chơi 2"
                binding.ivOpponentAvatar.setImageResource(R.drawable.ic_users_group)
                binding.btnHint.visibility = View.VISIBLE
                binding.tvOpponentTimer.visibility = View.GONE
            }
            OAnQuanPreferences.MODE_ONLINE -> {
                binding.tvModeSubtitle.text = "Chơi Online (PvP) ▾"
                binding.tvOpponentName.text = "Đối thủ Online"
                binding.ivOpponentAvatar.setImageResource(R.drawable.ic_globe_online)
                binding.btnHint.visibility = View.GONE
                binding.tvOpponentTimer.visibility = View.VISIBLE
            }
        }
    }

    private fun executeMove(pitIndex: Int, direction: MoveDirection) {
        if (!OAnQuanEngine.isMoveValid(gameState, gameState.currentTurn, pitIndex)) return

        val mover = gameState.currentTurn
        val steps = OAnQuanEngine.applyMove(gameState, pitIndex, direction)

        binding.boardView.animateSteps(steps, lifecycleScope) {
            updateScoreViews()
            updateTurnIndicators()

            if (gameState.isGameOver) {
                onGameOver()
                return@animateSteps
            }

            // Xử lý bước kế tiếp theo từng chế độ:
            when (currentMode) {
                OAnQuanPreferences.MODE_AI -> {
                    if (gameState.currentTurn == PlayerSide.PLAYER_2) {
                        triggerBotMove()
                    }
                }
                OAnQuanPreferences.MODE_ONLINE -> {
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
                else -> {
                    // Pass & Play: Người chơi 2 đánh trực tiếp trên màn hình
                }
            }
        }
    }

    private fun triggerBotMove() {
        lifecycleScope.launch {
            binding.boardView.setInteractive(false)
            delay(500) // Thời gian suy nghĩ của bot

            val diff = prefs.getAIDifficulty()
            val botMove = minimaxBot.findBestMove(gameState, diff)

            if (botMove != null) {
                executeMove(botMove.pitIndex, botMove.direction)
            } else {
                binding.boardView.setInteractive(true)
            }
        }
    }

    private fun provideMoveHint() {
        lifecycleScope.launch {
            val bestMove = minimaxBot.findBestMove(gameState, AIDifficulty.HARD)
            if (bestMove != null) {
                val dirName = if (bestMove.direction == MoveDirection.COUNTER_CLOCKWISE) "Thuận chiều ⮞" else "Ngược chiều ⮜"
                val message = "Gợi ý: Bốc ô ${bestMove.pitIndex + 1} và rải theo hướng $dirName để có lợi thế nhất!"
                Toast.makeText(this@OAnQuanActivity, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@OAnQuanActivity, "Không còn nước đi nào khả thi!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onGameOver() {
        val isWin = (gameState.winner == PlayerSide.PLAYER_1)
        prefs.recordGameFinished(isWin)

        if (isWin) {
            vibrateWinCelebration()
            coinPrefs.addCoins(100) // Thưởng 100 xu may mắn khi thắng
        }

        showResultDialog()
    }

    // Dialog: Chọn Chế Độ Chơi
    @SuppressLint("InflateParams")
    private fun showModeSelectionDialog() {
        val dialogBinding = DialogOAnQuanModeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var currentSelectedDiff = prefs.getAIDifficulty()

        fun updateDiffChips(diff: AIDifficulty) {
            currentSelectedDiff = diff
            prefs.setAIDifficulty(diff)
            dialogBinding.chipDiffEasy.setBackgroundResource(if (diff == AIDifficulty.EASY) R.drawable.bg_wishing_chip_selected else R.drawable.bg_wishing_chip_normal)
            dialogBinding.chipDiffMedium.setBackgroundResource(if (diff == AIDifficulty.MEDIUM) R.drawable.bg_wishing_chip_selected else R.drawable.bg_wishing_chip_normal)
            dialogBinding.chipDiffHard.setBackgroundResource(if (diff == AIDifficulty.HARD) R.drawable.bg_wishing_chip_selected else R.drawable.bg_wishing_chip_normal)
        }
        updateDiffChips(currentSelectedDiff)

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

    // Dialog: Kết Quả Ván Đấu
    @SuppressLint("InflateParams")
    private fun showResultDialog() {
        val dialogBinding = DialogOAnQuanResultBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val isWin = (gameState.winner == PlayerSide.PLAYER_1)
        val isDraw = (gameState.winner == null)

        when {
            isWin -> {
                dialogBinding.tvResultHeader.text = getString(R.string.oanquan_win_title)
                dialogBinding.tvResultHeader.setTextColor(Color.parseColor("#C62828"))
                dialogBinding.layoutCoinReward.visibility = View.VISIBLE
            }
            isDraw -> {
                dialogBinding.tvResultHeader.text = getString(R.string.oanquan_draw_title)
                dialogBinding.tvResultHeader.setTextColor(Color.parseColor("#FFA000"))
                dialogBinding.layoutCoinReward.visibility = View.GONE
            }
            else -> {
                dialogBinding.tvResultHeader.text = getString(R.string.oanquan_lose_title)
                dialogBinding.tvResultHeader.setTextColor(Color.parseColor("#7A5230"))
                dialogBinding.layoutCoinReward.visibility = View.GONE
            }
        }

        // Bảng tổng kết điểm
        dialogBinding.tvP1RawScore.text = gameState.player1Score.toString()
        dialogBinding.tvP2RawScore.text = gameState.player2Score.toString()
        dialogBinding.tvP1Debt.text = "-${gameState.player1Debt}"
        dialogBinding.tvP2Debt.text = "-${gameState.player2Debt}"
        dialogBinding.tvP1FinalScore.text = "${gameState.player1Score - gameState.player1Debt} đ"
        dialogBinding.tvP2FinalScore.text = "${gameState.player2Score - gameState.player2Debt} đ"

        dialogBinding.btnReplayGame.setSafeOnClickListener {
            dialog.dismiss()
            startNewGame()
        }

        dialogBinding.btnResultChangeMode.setSafeOnClickListener {
            dialog.dismiss()
            showModeSelectionDialog()
        }

        dialog.show()
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

    private fun vibrateWinCelebration() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }

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
        onlineRoomListener?.remove()
        super.onDestroy()
    }
}

