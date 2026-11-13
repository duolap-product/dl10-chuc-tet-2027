package online.duolap.tetviet.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.AdPlacement
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.data.model.BauCuaItem
import online.duolap.tetviet.databinding.ActivityBauCuaBinding
import online.duolap.tetviet.databinding.DialogBaucuaReliefBinding
import online.duolap.tetviet.databinding.DialogBaucuaResultBinding
import online.duolap.tetviet.views.setSafeOnClickListener
import javax.inject.Inject

@AndroidEntryPoint
class BauCuaActivity : AppCompatActivity() {

    @Inject
    lateinit var bauCuaPreferences: BauCuaPreferences

    @Inject
    lateinit var adManager: AdManager

    private lateinit var binding: ActivityBauCuaBinding

    private var selectedChip = 10
    private val currentBets = mutableMapOf<BauCuaItem, Int>()
    private val lastRoundBets = mutableMapOf<BauCuaItem, Int>()

    private var currentDice = listOf(BauCuaItem.BAU, BauCuaItem.CUA, BauCuaItem.TOM)
    private val recentHistory = mutableListOf<List<BauCuaItem>>()

    private enum class GameState {
        IDLE, RUNNING
    }

    private var gameState = GameState.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBauCuaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootBauCua) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            binding.layoutBottomControls.setPadding(
                binding.layoutBottomControls.paddingLeft,
                binding.layoutBottomControls.paddingTop,
                binding.layoutBottomControls.paddingRight,
                systemBars.bottom + 12
            )
            insets
        }

        AppAnalytics.logScreenView("BauCuaActivity")

        // Ban đầu để nắp bát hơi nhấc lên hé mở xúc xắc
        binding.ivBowlCover.translationY = -160f
        binding.ivBowlCover.alpha = 0.15f

        setupListeners()
        setupChipSelectors()
        setupBetSlots()
        observeCoins()
        updateTotalBetDisplay()
        updateDiceViews(currentDice)
        setInputEnabled(true)
    }

    private fun observeCoins() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bauCuaPreferences.coinsFlow.collect { coins ->
                    binding.tvUserCoins.text = coins.toString()
                }
            }
        }
    }

    private fun setupListeners() {
        // Nút back trên thanh công cụ
        binding.btnBack.setSafeOnClickListener(hasAnim = true) {
            refundCurrentBets()
            finish()
        }

        // Bắt sự kiện back cứng / cử chỉ hệ thống
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                refundCurrentBets()
                finish()
            }
        })

        binding.layoutCoinBalance.setSafeOnClickListener(hasAnim = true) {
            if (gameState != GameState.IDLE) return@setSafeOnClickListener
            showReliefCoinsDialog()
        }

        binding.btnMainAction.setSafeOnClickListener(hasAnim = true) {
            if (gameState == GameState.IDLE) {
                startShaking()
            }
        }

        binding.btnClearBet.setSafeOnClickListener(hasAnim = true) {
            if (gameState == GameState.IDLE) {
                refundCurrentBets()
            }
        }

        binding.btnDoubleBet.setSafeOnClickListener(hasAnim = true) {
            if (gameState == GameState.IDLE) {
                doubleCurrentBets()
            }
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private val scaleSelected = 60f / 44f // ~1.364f

    private fun setupChipSelectors() {
        val chipViews = mapOf(
            5 to binding.chip5,
            10 to binding.chip10,
            20 to binding.chip20,
            50 to binding.chip50,
            100 to binding.chip100
        )

        chipViews.forEach { (value, view) ->
            view.setSafeOnClickListener(hasAnim = true) {
                if (gameState != GameState.IDLE) return@setSafeOnClickListener
                if (selectedChip == value) return@setSafeOnClickListener
                selectedChip = value

                chipViews.values.filter { it != view }.forEach { chip ->
                    chip.setBackgroundResource(R.drawable.bg_chip_default_baucua)
                    chip.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .setInterpolator(null)
                        .start()
                }

                view.setBackgroundResource(R.drawable.bg_chip_selected_baucua)
                view.animate()
                    .scaleX(scaleSelected)
                    .scaleY(scaleSelected)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()

                vibrateLight(15)
            }
        }

        with(binding.chip10) {
            setBackgroundResource(R.drawable.bg_chip_selected_baucua)
            animate()
                .scaleX(scaleSelected)
                .scaleY(scaleSelected)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun setupBetSlots() {
        val slots = mapOf(
            BauCuaItem.NAI to binding.slotNai,
            BauCuaItem.BAU to binding.slotBau,
            BauCuaItem.GA to binding.slotGa,
            BauCuaItem.CA to binding.slotCa,
            BauCuaItem.CUA to binding.slotCua,
            BauCuaItem.TOM to binding.slotTom
        )

        slots.forEach { (item, view) ->
            view.setSafeOnClickListener(hasAnim = true) {
                if (gameState == GameState.IDLE) {
                    placeBet(item)
                }
            }
        }
    }

    /**
     * Bật / tắt khả năng tương tác của người dùng trong suốt quá trình xóc và mở bát.
     * Ngoại trừ duy nhất nút Back (btnBack) luôn hoạt động để thoát ra an toàn.
     */
    private fun setInputEnabled(enabled: Boolean) {
        binding.btnMainAction.isEnabled = enabled
        binding.btnClearBet.isEnabled = enabled
        binding.btnDoubleBet.isEnabled = enabled
        binding.layoutCoinBalance.isClickable = enabled

        val mainAlpha = if (enabled) 1.0f else 0.65f
        binding.btnMainAction.alpha = mainAlpha

        val quickActionAlpha = if (enabled) 1.0f else 0.4f
        binding.btnClearBet.alpha = quickActionAlpha
        binding.btnDoubleBet.alpha = quickActionAlpha

        binding.layoutChips.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun placeBet(item: BauCuaItem) {
        if (gameState != GameState.IDLE) return

        val currentCoins = bauCuaPreferences.getCoins()
        if (currentCoins < selectedChip) {
            Toast.makeText(this, getString(R.string.baucua_msg_not_enough_coins), Toast.LENGTH_SHORT).show()
            shakeView(binding.layoutCoinBalance)
            if (bauCuaPreferences.canClaimFreeRelief()) {
                showReliefCoinsDialog()
            }
            return
        }

        if (bauCuaPreferences.deductCoins(selectedChip)) {
            val prevBet = currentBets[item] ?: 0
            currentBets[item] = prevBet + selectedChip
            vibrateLight(20)
            updateSlotBadge(item)
            updateTotalBetDisplay()
        }
    }

    private fun doubleCurrentBets() {
        if (gameState != GameState.IDLE) return

        val totalCurrentBet = currentBets.values.sum()
        if (totalCurrentBet <= 0) return

        val currentCoins = bauCuaPreferences.getCoins()
        if (currentCoins < totalCurrentBet) {
            Toast.makeText(this, getString(R.string.baucua_msg_not_enough_coins), Toast.LENGTH_SHORT).show()
            shakeView(binding.layoutCoinBalance)
            return
        }

        if (bauCuaPreferences.deductCoins(totalCurrentBet)) {
            currentBets.keys.forEach { item ->
                currentBets[item] = (currentBets[item] ?: 0) * 2
                updateSlotBadge(item)
            }
            vibrateLight(25)
            updateTotalBetDisplay()
        }
    }

    private fun refundCurrentBets() {
        val totalRefund = currentBets.values.sum()
        if (totalRefund > 0) {
            bauCuaPreferences.addCoins(totalRefund)
            currentBets.clear()
            BauCuaItem.entries.forEach { updateSlotBadge(it) }
            updateTotalBetDisplay()
            vibrateLight(15)
        }
    }

    private fun getSlotView(item: BauCuaItem): FrameLayout {
        return when (item) {
            BauCuaItem.NAI -> binding.slotNai
            BauCuaItem.BAU -> binding.slotBau
            BauCuaItem.GA -> binding.slotGa
            BauCuaItem.CA -> binding.slotCa
            BauCuaItem.CUA -> binding.slotCua
            BauCuaItem.TOM -> binding.slotTom
        }
    }

    private fun getSlotBadge(item: BauCuaItem): TextView {
        return when (item) {
            BauCuaItem.NAI -> binding.tvBetNai
            BauCuaItem.BAU -> binding.tvBetBau
            BauCuaItem.GA -> binding.tvBetGa
            BauCuaItem.CA -> binding.tvBetCa
            BauCuaItem.CUA -> binding.tvBetCua
            BauCuaItem.TOM -> binding.tvBetTom
        }
    }

    private fun updateSlotBadge(item: BauCuaItem) {
        val betAmount = currentBets[item] ?: 0
        val badge = getSlotBadge(item)

        if (betAmount > 0) {
            badge.visibility = View.VISIBLE
            badge.text = "$betAmount"
            badge.setBackgroundResource(R.drawable.bg_version_badge_v2)
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun updateTotalBetDisplay() {
        val total = currentBets.values.sum()
        binding.tvTotalBet.text = getString(R.string.baucua_total_bet_label, total)
    }

    /**
     * Bắt đầu ván chơi: Đậy nắp bát và xóc đĩa.
     * Toàn bộ thao tác của người chơi bị khóa (trừ nút Back).
     */
    private fun startShaking() {
        val totalBet = currentBets.values.sum()
        if (totalBet <= 0) {
            Toast.makeText(this, getString(R.string.baucua_msg_need_bet), Toast.LENGTH_SHORT).show()
            shakeView(binding.layoutBetBoard)
            return
        }

        // Lưu lại cược ván này
        lastRoundBets.clear()
        lastRoundBets.putAll(currentBets)

        gameState = GameState.RUNNING
        setInputEnabled(false)
        binding.btnMainAction.text = "Đang xóc đĩa... 🧧"

        // Đậy nắp bát úp xuống đĩa
        binding.ivBowlCover.animate()
            .translationY(0f)
            .translationX(0f)
            .rotation(0f)
            .alpha(1f)
            .setDuration(240)
            .withEndAction {
                executeShakingAnimation()
            }
            .start()
    }

    /**
     * Rung lắc đĩa & nắp bát.
     * Sau khi xóc xong, TỰ ĐỘNG chuyển sang mở bát từ từ, không cần người dùng bấm.
     */
    private fun executeShakingAnimation() {
        // Sinh ngẫu nhiên 3 xúc xắc
        currentDice = listOf(
            BauCuaItem.random(),
            BauCuaItem.random(),
            BauCuaItem.random()
        )
        updateDiceViews(currentDice)

        // Hoạt ảnh rung lắc đĩa & nắp bát
        val rotateAnim = ObjectAnimator.ofFloat(
            binding.ivBowlCover, "rotation",
            0f, -12f, 12f, -10f, 10f, -6f, 6f, 0f
        ).apply { duration = 1200 }

        val transX = ObjectAnimator.ofFloat(
            binding.ivBowlCover, "translationX",
            0f, -14f, 14f, -10f, 10f, -5f, 5f, 0f
        ).apply { duration = 1200 }

        val transY = ObjectAnimator.ofFloat(
            binding.ivBowlCover, "translationY",
            0f, -8f, 6f, -6f, 5f, 0f
        ).apply { duration = 1200 }

        val set = AnimatorSet().apply {
            playTogether(rotateAnim, transX, transY)
            interpolator = AccelerateDecelerateInterpolator()
        }

        vibrateDiceShake()
        set.start()

        // Xóc xong -> TỰ ĐỘNG MỞ BÁT TỪ TỪ (không yêu cầu nhấn nút mở bát)
        binding.ivBowlCover.postDelayed({
            openBowlSlowly()
        }, 1250)
    }

    /**
     * Hiệu ứng từ từ mở nắp bát và hé lộ 3 viên xúc xắc nảy lên.
     */
    private fun openBowlSlowly() {
        binding.btnMainAction.text = "Đang mở bát... 🌸"

        // Hoạt ảnh nắp bát từ từ nhấc lên cao với độ dốc mượt mà
        binding.ivBowlCover.animate()
            .translationY(-160f)
            .rotation(-6f)
            .alpha(0.12f)
            .setDuration(850)
            .setInterpolator(DecelerateInterpolator(1.2f))
            .withEndAction {
                // Mở bát xong -> Chạy hiệu ứng cho các slot chính xác nhiều hơn
                executeWinningSlotsAnimation()
            }
            .start()

        // Khi nắp bát nhấc lên được một đoạn (250ms), 3 xúc xắc nảy lên lộ diện trên đĩa
        binding.root.postDelayed({
            vibrateLight(25)
            listOf(binding.dice1, binding.dice2, binding.dice3).forEachIndexed { index, diceView ->
                diceView.scaleX = 0.4f
                diceView.scaleY = 0.4f
                diceView.rotation = -12f
                diceView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .rotation(0f)
                    .setDuration(360 + (index * 60L))
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()
            }
        }, 250)
    }

    /**
     * Hiệu ứng cho TẤT CẢ các slot chính xác (linh vật có trong 3 xúc xắc).
     * Slot trúng đổi viền sáng vàng rực rỡ và nảy 3 nhịp mạnh mẽ.
     * Sau đó mới hiển thị Dialog kết quả.
     */
    private fun executeWinningSlotsAnimation() {
        val diceCounts = currentDice.groupingBy { it }.eachCount()
        var totalWinnings = 0
        val totalBet = currentBets.values.sum()

        // Tính thưởng theo luật: Thắng x2 xu mỗi con trúng (x3 nếu 2 con, x4 nếu 3 con)
        currentBets.forEach { (item, betAmount) ->
            val count = diceCounts[item] ?: 0
            if (count > 0) {
                val winAmount = betAmount * (count + 1)
                totalWinnings += winAmount
            }
        }

        val winningItems = diceCounts.keys
        winningItems.forEachIndexed { index, item ->
            val slotView = getSlotView(item)
            val badge = getSlotBadge(item)
            val count = diceCounts[item] ?: 1
            val userBetAmount = currentBets[item] ?: 0
            val isUserBetWon = userBetAmount > 0

            if (isUserBetWon) {
                val winAmount = userBetAmount * (count + 1)
                badge.visibility = View.VISIBLE
                badge.text = "+$winAmount"
                badge.setBackgroundResource(R.drawable.bg_version_badge_v2)
                badge.animate()
                    .scaleX(1.25f)
                    .scaleY(1.25f)
                    .setDuration(220)
                    .withEndAction {
                        badge.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180)
                            .start()
                    }
                    .start()
            }

            // Chạy hiệu ứng nảy nhịp pulse nhiều lần cho slot
            animateWinningSlotPulse(slotView, isUserBetWon, delay = index * 40L)
        }

        if (totalWinnings > 0) {
            vibrateWinCelebration()
        }

        // 2. Chờ hiệu ứng các slot kết thúc trọn vẹn (khoảng 1550ms) rồi mới hiển thị thông báo Dialog kết quả
        binding.root.postDelayed({
            // Cập nhật lịch sử các ván trước
            addRoundToHistory(currentDice)

            // Cộng xu thắng (nếu có)
            if (totalWinnings > 0) {
                bauCuaPreferences.addCoins(totalWinnings)
            }

            // Hiển thị Dialog thông báo kết quả
            showResultDialog(totalWinnings, totalBet, currentDice)
        }, 1550)
    }

    /**
     * Hiệu ứng nảy 3 nhịp liên tiếp cho slot chính xác, tạo cảm giác hồi hộp & ăn mừng.
     */
    @SuppressLint("Recycle")
    private fun animateWinningSlotPulse(slotView: View, isUserBetWon: Boolean, delay: Long) {
        slotView.postDelayed({
            val scaleUpX = ObjectAnimator.ofFloat(slotView, "scaleX", 1.0f, 1.12f).apply {
                duration = 220
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleUpY = ObjectAnimator.ofFloat(slotView, "scaleY", 1.0f, 1.12f).apply {
                duration = 220
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleDownX = ObjectAnimator.ofFloat(slotView, "scaleX", 1.12f, 1.0f).apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleDownY = ObjectAnimator.ofFloat(slotView, "scaleY", 1.12f, 1.0f).apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
            }

            val singlePulse = AnimatorSet().apply {
                play(scaleUpX).with(scaleUpY)
                play(scaleDownX).with(scaleDownY).after(scaleUpX)
            }

            val pulse1 = singlePulse.clone()
            val pulse2 = singlePulse.clone()
            val pulse3 = singlePulse.clone()

            singlePulse.apply {
                playSequentially(pulse1, pulse2, pulse3)
                start()
            }

            if (isUserBetWon) {
                vibrateLight(30)
            }
        }, delay)
    }

    private fun resetBoardForNextRound() {
        gameState = GameState.IDLE
        setInputEnabled(true)
        binding.btnMainAction.text = getString(R.string.baucua_btn_shake)

        currentBets.clear()
        BauCuaItem.entries.forEach {
            updateSlotBadge(it)
            val slotView = getSlotView(it)
            slotView.setBackgroundResource(R.drawable.bg_dreamy_golden)
            slotView.scaleX = 1.0f
            slotView.scaleY = 1.0f
        }
        updateTotalBetDisplay()
    }

    private fun updateDiceViews(diceList: List<BauCuaItem>) {
        if (diceList.size < 3) return
        binding.ivDice1.setImageResource(diceList[0].iconRes)
        binding.ivDice2.setImageResource(diceList[1].iconRes)
        binding.ivDice3.setImageResource(diceList[2].iconRes)
    }

    private fun addRoundToHistory(diceList: List<BauCuaItem>) {
        recentHistory.add(0, diceList)
        if (recentHistory.size > 5) {
            recentHistory.removeAt(recentHistory.size - 1)
        }

        binding.layoutHistoryDice.removeAllViews()
        val density = resources.displayMetrics.density
        recentHistory.take(4).forEach { round ->
            val roundView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((4 * density).toInt(), (2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt())
                setBackgroundResource(R.drawable.bg_version_badge)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, (6 * density).toInt(), 0)
                }
            }

            round.forEach { item ->
                val size = (18 * density).toInt()
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        setMargins(0, 0, (2 * density).toInt(), 0)
                    }
                    setImageResource(item.iconRes)
                }
                roundView.addView(iv)
            }
            binding.layoutHistoryDice.addView(roundView)
        }
    }

    private fun showResultDialog(totalWinnings: Int, totalBet: Int, dice: List<BauCuaItem>) {
        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val sheetBinding = DialogBaucuaResultBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        dialog.setOnDismissListener {
            resetBoardForNextRound()
        }

        sheetBinding.ivResultDice1.setImageResource(dice[0].iconRes)
        sheetBinding.ivResultDice2.setImageResource(dice[1].iconRes)
        sheetBinding.ivResultDice3.setImageResource(dice[2].iconRes)

        val netProfit = totalWinnings - totalBet

        when {
            netProfit > 0 -> {
                sheetBinding.tvResultTitle.text = getString(R.string.baucua_win_dialog_title)
                sheetBinding.tvResultDesc.text = getString(R.string.baucua_win_dialog_desc, totalWinnings)
                sheetBinding.tvProfitBadge.text = "+$netProfit Xu Thắng Thưởng"
                sheetBinding.tvProfitBadge.setTextColor(getColor(R.color.white))
                sheetBinding.tvProfitBadge.setBackgroundResource(R.drawable.bg_update_primary_btn)
                AppAnalytics.logFeatureClick("baucua_win")
            }

            totalWinnings > 0 -> {
                sheetBinding.tvResultTitle.text = getString(R.string.baucua_draw_dialog_title)
                sheetBinding.tvResultDesc.text = getString(R.string.baucua_draw_dialog_desc, totalWinnings)
                sheetBinding.tvProfitBadge.text = "Hòa Tiền Vốn"
                AppAnalytics.logFeatureClick("baucua_draw")
            }

            else -> {
                sheetBinding.tvResultTitle.text = getString(R.string.baucua_lose_dialog_title)
                sheetBinding.tvResultDesc.text = getString(R.string.baucua_lose_dialog_desc)
                sheetBinding.tvProfitBadge.text = "Thua $totalBet Xu"
                AppAnalytics.logFeatureClick("baucua_lose")
            }
        }

        sheetBinding.btnContinue.setSafeOnClickListener(hasAnim = true) {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showReliefCoinsDialog() {
        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val sheetBinding = DialogBaucuaReliefBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val hasClaimedToday = bauCuaPreferences.hasClaimedDailyGiftToday()

        if (!hasClaimedToday) {
            sheetBinding.tvReliefTitle.text = getString(R.string.baucua_dialog_relief_title)
            sheetBinding.tvReliefDesc.text = getString(R.string.baucua_dialog_relief_desc)

            sheetBinding.btnWatchAdForDouble.visibility = View.VISIBLE
            sheetBinding.btnClaimFree.visibility = View.VISIBLE
            sheetBinding.btnWatchAdForCoins.visibility = View.GONE

            sheetBinding.btnClaimFree.setSafeOnClickListener(hasAnim = true) {
                dialog.dismiss()
                bauCuaPreferences.claimDailyGift(100)
                playCoinRewardAnimation(100, "Lì Xì May Mắn Đầu Xuân")
            }

            sheetBinding.btnWatchAdForDouble.setSafeOnClickListener(hasAnim = true) {
                dialog.dismiss()
                adManager.showRewardedAd(
                    activity = this,
                    placement = AdPlacement.BAUCUA_DOUBLE_GIFT,
                    onUserEarnedReward = {
                        bauCuaPreferences.claimDailyGift(200)
                        playCoinRewardAnimation(200, "Nhân Đôi Lì Xì (x2) May Mắn")
                    },
                    onError = { errorMsg ->
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        } else {
            sheetBinding.tvReliefTitle.text = getString(R.string.baucua_dialog_relief_claimed_title)
            sheetBinding.tvReliefDesc.text = getString(R.string.baucua_dialog_relief_claimed_desc)

            sheetBinding.btnWatchAdForDouble.visibility = View.GONE
            sheetBinding.btnClaimFree.visibility = View.GONE
            sheetBinding.btnWatchAdForCoins.visibility = View.VISIBLE

            sheetBinding.btnWatchAdForCoins.setSafeOnClickListener(hasAnim = true) {
                dialog.dismiss()
                adManager.showRewardedAd(
                    activity = this,
                    placement = AdPlacement.BAUCUA_EXTRA_COINS,
                    onUserEarnedReward = {
                        bauCuaPreferences.addCoins(150)
                        playCoinRewardAnimation(150, "Thưởng Video May Mắn")
                    },
                    onError = { errorMsg ->
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        dialog.show()
    }

    private fun playCoinRewardAnimation(amount: Int, desc: String) {
        val overlay = binding.layoutRewardAnimationOverlay
        val card = binding.cardRewardPopup
        val tvAmount = binding.tvRewardAmount
        val tvDesc = binding.tvRewardDesc

        tvAmount.text = "+$amount Xu"
        tvDesc.text = desc

        overlay.visibility = View.VISIBLE
        overlay.alpha = 1.0f

        card.translationX = 0f
        card.translationY = 0f
        card.scaleX = 0.2f
        card.scaleY = 0.2f
        card.alpha = 0f

        vibrateWinCelebration()

        card.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(380)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                shakeView(card)

                card.postDelayed({
                    val balanceLoc = IntArray(2)
                    binding.layoutCoinBalance.getLocationInWindow(balanceLoc)

                    val cardLoc = IntArray(2)
                    card.getLocationInWindow(cardLoc)

                    val targetX = (balanceLoc[0] - cardLoc[0]).toFloat()
                    val targetY = (balanceLoc[1] - cardLoc[1]).toFloat()

                    card.animate()
                        .translationX(targetX)
                        .translationY(targetY)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .alpha(0.0f)
                        .setDuration(520)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            overlay.visibility = View.GONE
                            card.translationX = 0f
                            card.translationY = 0f

                            // Nảy ô số dư trên topBar khi xu bay vào
                            binding.layoutCoinBalance.animate()
                                .scaleX(1.35f)
                                .scaleY(1.35f)
                                .setDuration(180)
                                .withEndAction {
                                    binding.layoutCoinBalance.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(160)
                                        .start()
                                }
                                .start()

                            vibrateLight(35)
                        }
                        .start()

                }, 800)
            }
            .start()
    }

    private fun shakeView(view: View) {
        val anim = ObjectAnimator.ofFloat(view, "translationX", 0f, -10f, 10f, -8f, 8f, -4f, 4f, 0f)
        anim.duration = 400
        anim.start()
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

    private fun vibrateDiceShake() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 80, 120, 80, 140, 80, 100)
                val amplitudes = intArrayOf(0, 180, 0, 220, 0, 255, 0, 160)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
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
        refundCurrentBets()
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, BauCuaActivity::class.java))
        }
    }
}
