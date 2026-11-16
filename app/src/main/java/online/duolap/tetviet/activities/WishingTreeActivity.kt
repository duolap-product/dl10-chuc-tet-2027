package online.duolap.tetviet.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.adapters.UserWishesAdapter
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.AdPlacement
import online.duolap.tetviet.ads.AppOpenGate
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.WishEntity
import online.duolap.tetviet.data.model.WishCategory
import online.duolap.tetviet.data.local.BauCuaPreferences
import online.duolap.tetviet.data.repository.WishingTreeRepository
import online.duolap.tetviet.data.wishing.WishContentFilter
import online.duolap.tetviet.views.CoinFlyAnimator
import online.duolap.tetviet.widget.TetCountdown
import online.duolap.tetviet.databinding.ActivityWishingTreeBinding
import online.duolap.tetviet.databinding.DialogCreateWishBinding
import online.duolap.tetviet.databinding.DialogUserWishesBinding
import online.duolap.tetviet.databinding.DialogWishDetailBinding
import online.duolap.tetviet.databinding.DialogLanternGreetingBinding
import online.duolap.tetviet.databinding.ViewWishingLanternBinding
import online.duolap.tetviet.databinding.ViewWishingLeafBinding
import online.duolap.tetviet.views.LanternGlowAnimator
import online.duolap.tetviet.views.WishLeafSwayAnimator
import online.duolap.tetviet.views.setSafeOnClickListener
import java.util.Locale
import javax.inject.Inject
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout

@AndroidEntryPoint
class WishingTreeActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: WishingTreeRepository

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var coinPrefs: BauCuaPreferences

    private lateinit var binding: ActivityWishingTreeBinding

    /** Lá đang treo trên cây, theo id điều ước (để cập nhật thay vì xoá/vẽ lại toàn bộ). */
    private class LeafHolder(val binding: ViewWishingLeafBinding, var wish: WishEntity) {
        var boundText: String? = null
        var boundColor = -1
        var boundCategory: String? = null
        var flying: AnimatorSet? = null
    }

    private val leaves = LinkedHashMap<Long, LeafHolder>()
    private val swayAnimator = WishLeafSwayAnimator()
    private val lanternGlow = LanternGlowAnimator()
    private val lanterns = mutableListOf<ViewWishingLanternBinding>()
    /** Tâm ngang của đèn lồng (% chiều rộng): nằm giữa các cột lá để không che lá. */
    private val lanternCentersX = listOf(0.25f, 0.75f)
    private var lastGreetingIndex = -1
    private var latestWishes: List<WishEntity> = emptyList()
    private var hasRenderedOnce = false
    /** Id điều ước vừa viết, chờ lá của nó xuất hiện để chạy hiệu ứng bay lên cây. */
    private var pendingCeremonyWishId: Long? = null

    /**
     * Một vị trí treo lá: [centerX] là tâm lá theo % chiều rộng khung cây, [row] là hàng (0 trên, 1 dưới),
     * [offsetDp] lệch dọc nhẹ để hai hàng so le như treo trên các cành khác nhau.
     *
     * Bố cục 2 hàng × 4 cột chia đều hết chiều ngang, lệch dọc kiểu lượn sóng: cột cạnh nhau một cao
     * một thấp, nên lá không chồng lên nhau (bố cục cũ dồn lá về bên trái, nhiều cặp lá đè nhau).
     * Thứ tự trong danh sách là thứ tự lấp chỗ, xen kẽ trái/phải và trên/dưới để khi ít lá cây vẫn cân.
     * branchIndex lưu trong Room trỏ vào danh sách này.
     */
    private class LeafSlot(val centerX: Float, val row: Int, val offsetDp: Int)

    private val leafSlots = listOf(
        LeafSlot(0.125f, 0, 18),
        LeafSlot(0.625f, 0, 24),
        LeafSlot(0.375f, 1, 22),
        LeafSlot(0.875f, 1, 16),
        LeafSlot(0.375f, 0, 0),
        LeafSlot(0.875f, 0, 6),
        LeafSlot(0.125f, 1, 0),
        LeafSlot(0.625f, 1, 4)
    )

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WishingTreeActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWishingTreeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppAnalytics.logScreenView("WishingTreeActivity")

        applyWindowInsets()
        setupListeners()
        binding.treeBranchesCanvas.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) renderTreeTags(latestWishes)
        }
        observeData()

        repository.warmUp()
        lifecycleScope.launch {
            repository.ensureInitialWishesSeeded()
            // Tải lên điều ước còn chờ (viết lúc mất mạng), rồi lấy lá cộng đồng + lời chúc mới
            repository.syncPending()
            val freshBlessings = repository.refreshCommunity()
            if (freshBlessings > 0 && !isFinishing) {
                Toast.makeText(
                    this@WishingTreeActivity,
                    getString(R.string.wishing_new_blessings, freshBlessings),
                    Toast.LENGTH_LONG
                ).show()
                repository.markBlessingsSeen()
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootWishingTree) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                systemBars.top + 8,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            val btnParams = binding.btnWriteWish.layoutParams as ViewGroup.MarginLayoutParams
            btnParams.bottomMargin = systemBars.bottom + 16
            binding.btnWriteWish.layoutParams = btnParams
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener {
            vibrateLight(15)
            finish()
        }

        binding.btnWriteWish.setSafeOnClickListener {
            vibrateLight(20)
            AppAnalytics.logFeatureClick("wishing_tree_open_create")
            onWriteWishClicked()
        }

        binding.cardStats.setSafeOnClickListener {
            vibrateLight(15)
            AppAnalytics.logFeatureClick("wishing_tree_view_my_wishes")
            showUserWishesDialog()
        }
    }

    private fun observeData() {
        // Observe tree wishes and display hanging tags
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getTreeWishes().collectLatest { wishes ->
                    latestWishes = wishes
                    binding.treeBranchesCanvas.doOnLayout { renderTreeTags(latestWishes) }
                }
            }
        }

        // Số điều ước thật của cộng đồng (Firestore); chưa tải được thì hiện số điều ước của chính mình
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(repository.stats, repository.getUserWishCount()) { stats, mine ->
                    stats to mine
                }.collectLatest { (stats, mine) ->
                    val total = stats?.total ?: mine.toLong()
                    binding.tvTotalWishCount.text = compactCount(total)
                    if (stats != null) {
                        binding.tvStatsDesc.text = getString(R.string.wishing_stats_desc, compactCount(stats.today))
                    }
                }
            }
        }

        // Số xu (thưởng điều ước đầu tiên mỗi ngày bay vào đây)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                coinPrefs.coinsFlow.collect { coins ->
                    if (!CoinFlyAnimator.isHeld(binding.tvWishCoins)) binding.tvWishCoins.text = coins.toString()
                }
            }
        }
    }

    private fun compactCount(n: Long): String =
        if (n >= 1000) String.format(Locale.US, "%.1fK", n / 1000f) else n.toString()

    private fun onWriteWishClicked() {
        when (repository.writeQuota()) {
            WishingTreeRepository.WriteQuota.FREE,
            WishingTreeRepository.WriteQuota.EXTRA_GRANTED -> showCreateWishDialog()
            WishingTreeRepository.WriteQuota.EXTRA_AVAILABLE -> showExtraWishOffer()
            WishingTreeRepository.WriteQuota.NONE -> showComeBackTomorrow()
        }
    }

    private fun showExtraWishOffer() {
        AppAnalytics.logFeatureClick("wishing_tree_extra_offer")
        AlertDialog.Builder(this)
            .setTitle(R.string.wishing_extra_title)
            .setMessage(R.string.wishing_extra_msg)
            .setPositiveButton(R.string.wishing_extra_watch) { _, _ ->
                var rewarded = false
                Toast.makeText(this, R.string.msg_ad_loading_short, Toast.LENGTH_SHORT).show()
                adManager.showRewardedAd(
                    activity = this,
                    placement = online.duolap.tetviet.ads.AdPlacement.WISHING_EXTRA_WISH,
                    onUserEarnedReward = {
                        rewarded = true
                        repository.grantExtraWish()
                    },
                    // Mở hộp thoại viết khi quảng cáo đã đóng
                    onDismissed = { if (rewarded && !isFinishing) showCreateWishDialog() },
                    onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                )
            }
            .setNegativeButton(R.string.wishing_extra_later, null)
            .show()
    }

    private fun showComeBackTomorrow() {
        val remaining = (TetCountdown.nextDayStartMillis() - System.currentTimeMillis()).coerceAtLeast(0) / 1000
        val hm = String.format(Locale.US, "%02d:%02d", remaining / 3600, remaining % 3600 / 60)
        AlertDialog.Builder(this)
            .setTitle(R.string.wishing_quota_none_title)
            .setMessage(getString(R.string.wishing_quota_none_msg, hm))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun rejectionMessage(r: WishContentFilter.Rejection): String = getString(
        when (r) {
            WishContentFilter.Rejection.EMPTY -> R.string.wishing_tree_input_empty_error
            WishContentFilter.Rejection.TOO_LONG -> R.string.wishing_reject_too_long
            WishContentFilter.Rejection.BAD_WORD -> R.string.wishing_reject_bad_word
            WishContentFilter.Rejection.LINK -> R.string.wishing_reject_link
            WishContentFilter.Rejection.PHONE -> R.string.wishing_reject_phone
            WishContentFilter.Rejection.EMAIL -> R.string.wishing_reject_email
            WishContentFilter.Rejection.SPAM -> R.string.wishing_reject_spam
        }
    )

    private fun renderTreeTags(wishes: List<WishEntity>) {
        val canvas = binding.treeBranchesCanvas
        val canvasWidth = canvas.width
        val canvasHeight = canvas.height
        if (canvasWidth == 0 || canvasHeight == 0) return

        val leafWidth = dpToPx(64)
        val leafHeight = dpToPx(112)

        // Gỡ lá không còn trong danh sách (mờ dần thay vì biến mất đột ngột)
        val keepIds = wishes.mapTo(HashSet()) { it.id }
        leaves.keys.filter { it !in keepIds }.forEach { id ->
            val holder = leaves.remove(id) ?: return@forEach
            val view = holder.binding.root
            holder.flying?.cancel()
            swayAnimator.remove(view)
            view.isClickable = false
            view.animate().alpha(0f).setDuration(250).withEndAction { canvas.removeView(view) }.start()
        }

        // Khoảng cách hai hàng: giãn theo chiều cao khung (tối đa 170dp) nhưng không vượt khung;
        // màn thấp thì hàng dưới được phép chồng nhẹ lên tua rua hàng trên.
        val topPad = dpToPx(6)
        val maxOffset = dpToPx(24)
        val rowGap = (canvasHeight - leafHeight - maxOffset - topPad)
            .coerceAtMost(dpToPx(170))
            .coerceAtLeast((leafHeight * 0.7f).toInt())
        val edge = dpToPx(4).toFloat()
        layoutLanterns(canvasWidth, canvasHeight, topPad + rowGap + maxOffset + leafHeight)

        // Mỗi lá một vị trí riêng: ưu tiên branchIndex đã lưu, trùng thì lấy vị trí trống kế tiếp
        val usedSlots = BooleanArray(leafSlots.size)
        wishes.forEachIndexed { index, wish ->
            val preferred = if (wish.branchIndex in leafSlots.indices) wish.branchIndex else index
            var slotIndex = preferred % leafSlots.size
            if (wishes.size <= leafSlots.size) {
                while (usedSlots[slotIndex]) slotIndex = (slotIndex + 1) % leafSlots.size
            }
            usedSlots[slotIndex] = true
            val slot = leafSlots[slotIndex]
            val targetX = (slot.centerX * canvasWidth - leafWidth / 2f)
                .coerceIn(edge, (canvasWidth - leafWidth - edge).coerceAtLeast(edge))
            val targetY = (topPad + slot.row * rowGap + dpToPx(slot.offsetDp))
                .coerceAtMost(canvasHeight - leafHeight).coerceAtLeast(0).toFloat()

            val existing = leaves[wish.id]
            if (existing != null) {
                existing.wish = wish
                bindLeaf(existing)
                if (existing.flying == null) {
                    existing.binding.root.x = targetX
                    existing.binding.root.y = targetY
                }
                return@forEachIndexed
            }

            val leafBinding = ViewWishingLeafBinding.inflate(layoutInflater, canvas, false)
            val holder = LeafHolder(leafBinding, wish)
            bindLeaf(holder)
            val view = leafBinding.root
            view.layoutParams = FrameLayout.LayoutParams(leafWidth, leafHeight)
            view.pivotX = leafWidth / 2f
            view.pivotY = dpToPx(4).toFloat() // xoay quanh móc treo
            view.x = targetX
            view.y = targetY
            view.setOnClickListener { onLeafClicked(holder) }

            leaves[wish.id] = holder
            canvas.addView(view)
            swayAnimator.add(view, seed = wish.id.toInt() * 31 + index)

            if (wish.id == pendingCeremonyWishId) {
                pendingCeremonyWishId = null
                playHangingCeremonyAnimation(holder, targetX, targetY)
            } else if (hasRenderedOnce) {
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(300).start()
            }
        }
        hasRenderedOnce = true
    }

    private fun bindLeaf(holder: LeafHolder) {
        val wish = holder.wish
        val b = holder.binding
        b.ivLeafMine.visibility = if (wish.isUserCreated) View.VISIBLE else View.GONE
        val text = formatTagText(wish.content)
        if (holder.boundText != text) {
            holder.boundText = text
            b.tvLeafContent.text = text
            b.root.contentDescription = if (wish.isUserCreated) {
                getString(R.string.wishing_leaf_mine_desc) + ": " + wish.content
            } else wish.content
        }
        if (holder.boundCategory != wish.category) {
            holder.boundCategory = wish.category
            b.ivLeafCategoryIcon.setImageResource(WishCategory.fromName(wish.category).leafIconRes)
        }
        if (holder.boundColor != wish.tagColorType) {
            holder.boundColor = wish.tagColorType
            val (bgRes, textColor) = leafStyle(wish.tagColorType)
            b.ivLeafBg.setImageResource(bgRes)
            b.tvLeafContent.setTextColor(textColor)
        }
    }

    /** Nền và màu chữ của lá theo tagColorType (0 = kem, 1 = đỏ, 2 = vàng). */
    private fun leafStyle(tagColorType: Int): Pair<Int, Int> = when (tagColorType) {
        1 -> R.drawable.bg_wishing_leaf_red to "#FFF4D6".toColorInt()
        2 -> R.drawable.bg_wishing_leaf_gold to "#6E0A0C".toColorInt()
        else -> R.drawable.bg_wishing_leaf_cream to "#7A090D".toColorInt()
    }

    private fun onLeafClicked(holder: LeafHolder) {
        if (holder.flying != null) return
        vibrateLight(20)
        val view = holder.binding.root
        swayAnimator.nudge(view)
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(110)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
        showWishDetailDialog(holder.wish)
    }

    /**
     * Treo đèn lồng dưới hàng lá thứ hai, ở khe giữa các cột lá; dây treo dài đi lên qua khe đó tới cành.
     * Đèn được thêm vào đầu danh sách con của khung cây nên luôn nằm sau lá; màn thấp thì đèn kéo lên,
     * chồng nhẹ sau lá.
     */
    private fun layoutLanterns(canvasWidth: Int, canvasHeight: Int, leavesBottom: Int) {
        val canvas = binding.treeBranchesCanvas
        if (lanterns.isEmpty()) {
            lanternCentersX.forEachIndexed { i, _ ->
                val lb = ViewWishingLanternBinding.inflate(layoutInflater, canvas, false)
                val root = lb.root
                root.pivotX = dpToPx(55).toFloat()
                root.pivotY = 0f // xoay quanh đầu dây treo trên cành
                lb.ivLanternBody.setSafeOnClickListener { onLanternClicked(lb) }
                canvas.addView(root, i)
                swayAnimator.add(root, seed = 1000 + i * 17)
                lanternGlow.add(lb.ivLanternGlow, lb.ivLanternLight, seed = i + 1)
                lanterns += lb
            }
            lanternGlow.start()
        }
        val lanternWidth = dpToPx(110)
        val lanternHeight = dpToPx(222)
        val cordLength = dpToPx(110)
        lanterns.forEachIndexed { i, lb ->
            val x = lanternCentersX[i] * canvasWidth - lanternWidth / 2f
            // Thân đèn ngay dưới thân lá hàng 2 (trên đầu con lân), hai đèn lệch cao thấp cho tự nhiên
            val y = (leavesBottom - dpToPx(60) - cordLength + if (i == 1) dpToPx(10) else 0)
                .coerceAtMost(canvasHeight - lanternHeight)
                .coerceAtLeast(0)
            lb.root.x = x
            lb.root.y = y.toFloat()
        }
    }

    private fun onLanternClicked(lantern: ViewWishingLanternBinding) {
        vibrateLight(25)
        AppAnalytics.logFeatureClick("wishing_tree_lantern_tap")
        swayAnimator.nudge(lantern.root)
        lanternGlow.burst(lantern.ivLanternGlow)
        // Cho người dùng thấy đèn bừng sáng rồi mới mở lời chúc
        lantern.root.postDelayed({
            if (!isFinishing && !isDestroyed) showLanternGreetingDialog()
        }, 280)
    }

    private fun randomGreeting(): String {
        val greetings = resources.getStringArray(R.array.wishing_lantern_greetings)
        var index = greetings.indices.random()
        if (greetings.size > 1 && index == lastGreetingIndex) index = (index + 1) % greetings.size
        lastGreetingIndex = index
        return greetings[index]
    }

    @SuppressLint("InflateParams")
    private fun showLanternGreetingDialog() {
        val dialogBinding = DialogLanternGreetingBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        var greeting = randomGreeting()
        dialogBinding.tvGreetingContent.text = "“$greeting”"

        val dialogGlow = LanternGlowAnimator().apply {
            add(dialogBinding.ivGreetingGlow, dialogBinding.ivGreetingLight, seed = 7)
            start()
            burst(dialogBinding.ivGreetingGlow)
        }

        dialogBinding.btnAnotherGreeting.setSafeOnClickListener {
            vibrateLight(15)
            AppAnalytics.logFeatureClick("wishing_tree_lantern_another")
            greeting = randomGreeting()
            dialogGlow.burst(dialogBinding.ivGreetingGlow)
            val content = dialogBinding.tvGreetingContent
            content.animate().alpha(0f).setDuration(140).withEndAction {
                content.text = "“$greeting”"
                content.animate().alpha(1f).setDuration(200).start()
            }.start()
        }

        dialogBinding.btnShareGreeting.setSafeOnClickListener {
            vibrateLight(15)
            AppAnalytics.logFeatureClick("wishing_tree_lantern_share")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.wishing_lantern_share_format, greeting))
            }
            // Người dùng rời app có chủ đích (chia sẻ): không hiện App Open khi quay lại
            AppOpenGate.skipNextForeground()
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
        }

        dialogBinding.btnCloseGreeting.setSafeOnClickListener {
            vibrateLight(15)
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            dialogGlow.clear()
            dialogBinding.tvGreetingContent.animate().cancel()
            // Điểm nghỉ tự nhiên: hộp thoại lời chúc vừa đóng (nút X, Back hoặc chạm ra ngoài).
            // AdPolicy quyết định tần suất; bỏ qua nếu màn hình đang đóng/không còn hiển thị.
            if (!isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                adManager.showInterstitialAd(this, AdPlacement.WISHING_LANTERN_BREAK)
            }
        }
        dialog.show()
    }

    private fun formatTagText(content: String): String {
        val trimmed = content.trim()
        val commaIndex = trimmed.indexOfAny(charArrayOf(',', '.', ';', '\n'))
        val firstClause = if (commaIndex in 1..25) trimmed.substring(0, commaIndex) else trimmed

        val words = firstClause.split(" ").filter { it.isNotBlank() }
        return when {
            words.size <= 2 -> words.joinToString("\n")
            words.size <= 4 -> "${words.take(2).joinToString(" ")}\n${words.drop(2).joinToString(" ")}"
            else -> "${words.take(2).joinToString(" ")}\n${words.subList(2, 4).joinToString(" ")}"
        }
    }

    @SuppressLint("InflateParams")
    private fun showCreateWishDialog() {
        val dialogBinding = DialogCreateWishBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        var selectedCategory = WishCategory.HEALTH

        val tagColorType = (0..2).random()
        leafStyle(tagColorType).let { (bgRes, textColor) ->
            dialogBinding.ivPreviewLeafBg.setImageResource(bgRes)
            dialogBinding.tvPreviewTagContent.setTextColor(textColor)
        }
        val preview = dialogBinding.layoutPreviewTag
        preview.pivotX = dpToPx(40).toFloat()
        preview.pivotY = dpToPx(5).toFloat() // xoay quanh móc treo
        val previewSway = WishLeafSwayAnimator().apply {
            add(preview, seed = tagColorType + 7)
            start()
        }
        val categoryChips = listOf(
            Triple(dialogBinding.chipHealth, WishCategory.HEALTH, dialogBinding.tvLabelHealth),
            Triple(dialogBinding.chipCareer, WishCategory.CAREER, dialogBinding.tvLabelCareer),
            Triple(dialogBinding.chipLove, WishCategory.LOVE, dialogBinding.tvLabelLove),
            Triple(dialogBinding.chipStudy, WishCategory.STUDY, dialogBinding.tvLabelStudy),
            Triple(dialogBinding.chipFinance, WishCategory.FINANCE, dialogBinding.tvLabelFinance),
            Triple(dialogBinding.chipFamily, WishCategory.FAMILY, dialogBinding.tvLabelFamily),
            Triple(dialogBinding.chipOther, WishCategory.OTHER, dialogBinding.tvLabelOther)
        )

        fun updateCategorySelection(cat: WishCategory) {
            selectedCategory = cat
            categoryChips.forEach { (chip, itemCat, label) ->
                if (itemCat == cat) {
                    chip.setBackgroundResource(R.drawable.bg_wishing_chip_selected)
                    label.setTextColor("#C62828".toColorInt())
                } else {
                    chip.setBackgroundResource(R.drawable.bg_wishing_chip_normal)
                    label.setTextColor("#6B3F21".toColorInt())
                }
            }
            dialogBinding.ivPreviewCategoryIcon.setImageResource(cat.leafIconRes)
        }

        categoryChips.forEach { (chip, cat, _) ->
            chip.setOnClickListener {
                vibrateLight(15)
                updateCategorySelection(cat)
            }
        }

        dialogBinding.etWishContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val len = s?.length ?: 0
                dialogBinding.tvCharCounter.text = "$len/100"
                if (s.isNullOrBlank()) {
                    dialogBinding.tvPreviewTagContent.text = "Điều ước\ncủa bạn"
                } else {
                    dialogBinding.tvPreviewTagContent.text = formatTagText(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Gentle breathing glow animation on the halo
        val haloAnim = ObjectAnimator.ofFloat(dialogBinding.viewHalo, "scaleX", 0.95f, 1.1f).apply {
            duration = 1600
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
        }
        val haloAnimY = ObjectAnimator.ofFloat(dialogBinding.viewHalo, "scaleY", 0.95f, 1.1f).apply {
            duration = 1600
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
        }
        haloAnim.start()
        haloAnimY.start()
        dialog.setOnDismissListener {
            haloAnim.cancel()
            haloAnimY.cancel()
            previewSway.clear()
        }

        dialogBinding.btnClose.setSafeOnClickListener {
            vibrateLight(15)
            dialog.dismiss()
        }

        dialogBinding.etWishContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dialogBinding.tvWishError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.btnHangWish.setSafeOnClickListener {
            val content = dialogBinding.etWishContent.text?.toString()?.trim().orEmpty()
            WishContentFilter.check(content)?.let { reason ->
                dialogBinding.tvWishError.text = rejectionMessage(reason)
                dialogBinding.tvWishError.visibility = View.VISIBLE
                AppAnalytics.logFeatureClick("wishing_tree_rejected_${reason.name.lowercase()}")
                return@setSafeOnClickListener
            }

            lifecycleScope.launch {
                val targetBranch = (System.currentTimeMillis() % leafSlots.size).toInt()
                val result = repository.createWish(
                    content = content,
                    category = selectedCategory,
                    branchIndex = targetBranch,
                    tagColorType = tagColorType
                )
                result.rejection?.let {
                    dialogBinding.tvWishError.text = rejectionMessage(it)
                    dialogBinding.tvWishError.visibility = View.VISIBLE
                    return@launch
                }
                if (result.quotaExceeded) {
                    dialog.dismiss()
                    onWriteWishClicked()
                    return@launch
                }
                val createdWish = result.wish ?: return@launch

                vibrateWinCelebration()
                AppAnalytics.logFeatureClick("wishing_tree_hang_success")
                dialog.dismiss()
                Toast.makeText(
                    this@WishingTreeActivity,
                    getString(R.string.wishing_tree_hang_success),
                    Toast.LENGTH_LONG
                ).show()

                // Lá thật do renderTreeTags tạo khi Room phát dữ liệu mới sẽ bay lên cây.
                // Nếu Room đã phát trước khi tới đây thì lá đã treo sẵn: cho nó bay lại từ dưới lên.
                val existing = leaves[createdWish.id]
                if (existing != null) {
                    playHangingCeremonyAnimation(existing, existing.binding.root.x, existing.binding.root.y)
                } else {
                    pendingCeremonyWishId = createdWish.id
                }

                // Thưởng xu cho điều ước đầu tiên trong ngày: xu bay từ nút viết lên ô số xu
                if (result.coinsAwarded > 0) {
                    val end = coinPrefs.getCoins()
                    CoinFlyAnimator.hold(binding.tvWishCoins)
                    binding.root.postDelayed({
                        if (isFinishing) return@postDelayed
                        val (fx, fy) = CoinFlyAnimator.centerOnScreen(binding.btnWriteWish)
                        CoinFlyAnimator.fly(
                            this@WishingTreeActivity, fx, fy, binding.chipWishCoins, binding.tvWishCoins,
                            startValue = end - result.coinsAwarded, endValue = end
                        )
                    }, 1200)
                }

                repository.syncPending()
            }
        }

        dialog.show()
    }

    // Hanging Ceremony Animation: lá bay từ nút "Viết điều ước" lên nhánh cây
    private fun playHangingCeremonyAnimation(holder: LeafHolder, targetX: Float, targetY: Float) {
        val view = holder.binding.root
        val canvas = binding.treeBranchesCanvas
        holder.flying?.cancel()
        swayAnimator.setSuspended(view, true)

        val startX = canvas.width / 2f - view.layoutParams.width / 2f
        val startY = canvas.height.toFloat() - dpToPx(30)
        // Đặt sẵn trạng thái đầu để khung hình đầu tiên không loé lá ở vị trí đích
        view.x = startX
        view.y = startY
        view.alpha = 0f
        view.scaleX = 0.3f
        view.scaleY = 0.3f

        val path = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.X, startX, targetX),
                ObjectAnimator.ofFloat(view, View.Y, startY, targetY)
            )
            interpolator = DecelerateInterpolator(1.6f)
        }
        val pop = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.3f, 1.15f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.3f, 1.15f, 1f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f, 1f, 1f),
                ObjectAnimator.ofFloat(view, View.ROTATION, 0f, -10f, 7f, -4f, 0f)
            )
            interpolator = AccelerateDecelerateInterpolator()
        }
        val set = AnimatorSet().apply {
            playTogether(path, pop)
            duration = 1100
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (holder.flying === animation) holder.flying = null
                    // Hoà dần từ góc 0 vào nhịp đung đưa, không bị nhảy góc
                    swayAnimator.setSuspended(view, false)
                    swayAnimator.nudge(view)
                }
            })
        }
        holder.flying = set
        set.start()
    }

    // Detail Dialog: Inspect a Wish
    @SuppressLint("InflateParams")
    private fun showWishDetailDialog(wish: WishEntity) {
        val dialogBinding = DialogWishDetailBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val category = WishCategory.fromName(wish.category)
        dialogBinding.ivDetailCategoryIcon.setImageResource(category.leafIconRes)
        dialogBinding.tvDetailCategoryTitle.setText(category.titleRes)

        dialogBinding.tvDetailContent.text = "“${wish.content}”"
        dialogBinding.tvDetailAuthor.text = "— ${wish.authorName}"
        dialogBinding.tvDetailBlessingCount.text = getString(
            R.string.wishing_tree_blessings_count,
            wish.blessingCount
        )

        // Chúc phúc: mỗi người 1 lần mỗi lá; không tự chúc điều ước của mình
        var hasBlessed = repository.hasBlessed(wish)
        when {
            wish.isUserCreated -> {
                dialogBinding.tvBlessWish.setText(R.string.wishing_bless_own)
                dialogBinding.btnBlessWish.isEnabled = false
                dialogBinding.btnBlessWish.alpha = 0.6f
            }
            hasBlessed -> {
                dialogBinding.tvBlessWish.setText(R.string.wishing_bless_done)
                dialogBinding.btnBlessWish.alpha = 0.6f
            }
        }
        dialogBinding.btnBlessWish.setSafeOnClickListener {
            if (!hasBlessed && !wish.isUserCreated) {
                hasBlessed = true
                vibrateLight(25)
                lifecycleScope.launch {
                    repository.blessWish(wish)
                }
                val newBlessingCount = wish.blessingCount + 1
                dialogBinding.tvDetailBlessingCount.text = getString(
                    R.string.wishing_tree_blessings_count,
                    newBlessingCount
                )
                dialogBinding.tvBlessWish.setText(R.string.wishing_bless_done)
                dialogBinding.btnBlessWish.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(150)
                    .withEndAction {
                        dialogBinding.btnBlessWish.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }
                    .start()
                Toast.makeText(
                    this,
                    getString(R.string.wishing_tree_blessed_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Báo cáo lá cộng đồng không phù hợp
        dialogBinding.btnReportWish.visibility =
            if (wish.remoteId != null && !wish.isUserCreated) View.VISIBLE else View.GONE
        dialogBinding.btnReportWish.setSafeOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.wishing_report_title)
                .setMessage(R.string.wishing_report_msg)
                .setPositiveButton(R.string.wishing_report_confirm) { _, _ ->
                    AppAnalytics.logFeatureClick("wishing_tree_report")
                    lifecycleScope.launch { repository.reportWish(wish) }
                    dialog.dismiss()
                    Toast.makeText(this, R.string.wishing_report_done, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        dialogBinding.btnShareWish.setSafeOnClickListener {
            vibrateLight(15)
            val shareText = "🌸 Lời chúc đầu xuân từ Cây Điều Ước:\n\n“${wish.content}”\n\nCùng gửi gắm ước mơ năm mới tại ứng dụng Tết Việt!"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            // Người dùng rời app có chủ đích (chia sẻ): không hiện App Open khi quay lại
            AppOpenGate.skipNextForeground()
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
        }

        dialogBinding.btnCloseDetail.setSafeOnClickListener {
            vibrateLight(15)
            dialog.dismiss()
        }

        dialog.show()
    }

    // User's past wishes Dialog
    @SuppressLint("InflateParams")
    private fun showUserWishesDialog() {
        val dialogBinding = DialogUserWishesBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val adapter = UserWishesAdapter { clickedWish ->
            dialog.dismiss()
            showWishDetailDialog(clickedWish)
        }

        dialogBinding.rvUserWishes.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvUserWishes.adapter = adapter

        lifecycleScope.launch { repository.markBlessingsSeen() }
        lifecycleScope.launch {
            repository.getUserWishes().collectLatest { userWishes ->
                if (userWishes.isEmpty()) {
                    dialogBinding.tvEmptyWishes.visibility = View.VISIBLE
                    dialogBinding.rvUserWishes.visibility = View.GONE
                } else {
                    dialogBinding.tvEmptyWishes.visibility = View.GONE
                    dialogBinding.rvUserWishes.visibility = View.VISIBLE
                    adapter.submitList(userWishes)
                }
            }
        }

        dialogBinding.btnCloseUserWishes.setSafeOnClickListener {
            vibrateLight(15)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        swayAnimator.start()
        lanternGlow.start()
    }

    override fun onStop() {
        swayAnimator.stop()
        lanternGlow.stop()
        super.onStop()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
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
        swayAnimator.clear()
        lanternGlow.clear()
        leaves.values.forEach { it.flying?.cancel() }
        super.onDestroy()
    }
}

