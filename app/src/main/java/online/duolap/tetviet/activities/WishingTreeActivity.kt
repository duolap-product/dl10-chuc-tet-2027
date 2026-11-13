package online.duolap.tetviet.activities

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
import online.duolap.tetviet.ads.AppOpenGate
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.local.WishEntity
import online.duolap.tetviet.data.model.WishCategory
import online.duolap.tetviet.data.repository.WishingTreeRepository
import online.duolap.tetviet.databinding.ActivityWishingTreeBinding
import online.duolap.tetviet.databinding.DialogCreateWishBinding
import online.duolap.tetviet.databinding.DialogUserWishesBinding
import online.duolap.tetviet.databinding.DialogWishDetailBinding
import online.duolap.tetviet.databinding.ViewWishingTagBinding
import online.duolap.tetviet.views.setSafeOnClickListener
import java.util.Locale
import javax.inject.Inject
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

@AndroidEntryPoint
class WishingTreeActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: WishingTreeRepository

    private lateinit var binding: ActivityWishingTreeBinding
    private val activeAnimators = mutableListOf<ObjectAnimator>()

    // Anchor relative coordinate positions on tree branches (X%, Y%)
    private val branchAnchors = listOf(
        Pair(0.08f, 0.22f), // Branch 0: Top-Left
        Pair(0.38f, 0.17f), // Branch 1: Center-High
        Pair(0.68f, 0.26f), // Branch 2: Top-Right
        Pair(0.24f, 0.46f), // Branch 3: Mid-Left
        Pair(0.60f, 0.50f), // Branch 4: Mid-Right
        Pair(0.80f, 0.40f), // Branch 5: Far-Right
        Pair(0.04f, 0.44f), // Branch 6: Far-Left
        Pair(0.44f, 0.36f)  // Branch 7: Central trunk branch
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
        observeData()

        lifecycleScope.launch {
            repository.ensureInitialWishesSeeded()
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
            showCreateWishDialog()
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
                    binding.treeBranchesCanvas.post {
                        renderTreeTags(wishes)
                    }
                }
            }
        }

        // Observe total count
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getTotalWishCount().collectLatest { totalCount ->
                    val displayCount = if (totalCount >= 1000) {
                        String.format(Locale.US, "%.1fK", totalCount / 1000f)
                    } else {
                        totalCount.toString()
                    }
                    binding.tvTotalWishCount.text = displayCount
                }
            }
        }
    }

    private fun renderTreeTags(wishes: List<WishEntity>) {
        cancelAllAnimators()
        binding.treeBranchesCanvas.removeAllViews()

        val canvasWidth = binding.treeBranchesCanvas.width
        val canvasHeight = binding.treeBranchesCanvas.height

        if (canvasWidth == 0 || canvasHeight == 0) return

        wishes.forEachIndexed { index, wish ->
            val anchorIndex = if (wish.branchIndex in branchAnchors.indices) {
                wish.branchIndex
            } else {
                index % branchAnchors.size
            }
            val anchor = branchAnchors[anchorIndex]

            val tagBinding = ViewWishingTagBinding.inflate(
                layoutInflater,
                binding.treeBranchesCanvas,
                false
            )

            // Setup Tag content
            val category = WishCategory.fromName(wish.category)
            tagBinding.ivTagCategoryIcon.setImageResource(category.iconRes)

            // Format short display text (max 2-3 words for tag plaque)
            val displayText = formatTagText(wish.content)
            tagBinding.tvTagContent.text = displayText

            // Apply tag color style
            when (wish.tagColorType) {
                1 -> { // Red
                    tagBinding.tagCard.setBackgroundResource(R.drawable.bg_wishing_tag_red)
                    tagBinding.tvTagContent.setTextColor("#FFFDF7".toColorInt())
                }
                2 -> { // Gold
                    tagBinding.tagCard.setBackgroundResource(R.drawable.bg_wishing_tag_gold)
                    tagBinding.tvTagContent.setTextColor("#7A090D".toColorInt())
                }
                else -> { // Cream
                    tagBinding.tagCard.setBackgroundResource(R.drawable.bg_wishing_tag_cream)
                    tagBinding.tvTagContent.setTextColor("#7A090D".toColorInt())
                }
            }

            // Tag Layout params & coordinate placement
            val tagWidth = dpToPx(68)
            val tagHeight = dpToPx(96)
            val params = FrameLayout.LayoutParams(tagWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            tagBinding.root.layoutParams = params

            val posX = (anchor.first * (canvasWidth - tagWidth)).coerceAtLeast(0f)
            val posY = (anchor.second * (canvasHeight - tagHeight)).coerceAtLeast(0f)

            tagBinding.root.x = posX
            tagBinding.root.y = posY
            tagBinding.root.pivotX = tagWidth / 2f
            tagBinding.root.pivotY = 0f

            // Gentle Wind Swaying Animation
            val animDuration = 2600L + (index * 230L) % 700L
            val swayAnim = ObjectAnimator.ofFloat(tagBinding.root, "rotation", -2.8f, 2.8f).apply {
                duration = animDuration
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = (index * 320L) % 1100L
            }
            activeAnimators.add(swayAnim)
            swayAnim.start()

            // Click listener to inspect wish
            tagBinding.root.setOnClickListener {
                vibrateLight(20)
                tagBinding.root.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(120)
                    .withEndAction {
                        tagBinding.root.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(160)
                            .setInterpolator(OvershootInterpolator(1.8f))
                            .start()
                    }
                    .start()
                showWishDetailDialog(wish)
            }

            binding.treeBranchesCanvas.addView(tagBinding.root)
        }
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

    // Modal: Viết điều ước (Screen 2)
    @SuppressLint("InflateParams")
    private fun showCreateWishDialog() {
        val dialogBinding = DialogCreateWishBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        var selectedCategory = WishCategory.HEALTH
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
            dialogBinding.ivPreviewCategoryIcon.setImageResource(cat.iconRes)
        }

        categoryChips.forEach { (chip, cat, _) ->
            chip.setOnClickListener {
                vibrateLight(15)
                updateCategorySelection(cat)
            }
        }

        // Real-time Live Preview Tag Text
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

        dialogBinding.btnClose.setSafeOnClickListener {
            vibrateLight(15)
            dialog.dismiss()
        }

        dialogBinding.btnHangWish.setSafeOnClickListener {
            val content = dialogBinding.etWishContent.text?.toString()?.trim().orEmpty()
            if (content.isBlank()) {
                Toast.makeText(
                    this,
                    getString(R.string.wishing_tree_input_empty_error),
                    Toast.LENGTH_SHORT
                ).show()
                return@setSafeOnClickListener
            }

            vibrateWinCelebration()
            AppAnalytics.logFeatureClick("wishing_tree_hang_success")

            lifecycleScope.launch {
                // Determine target branch anchor
                val targetBranch = (System.currentTimeMillis() % branchAnchors.size).toInt()
                val createdWish = repository.createWish(
                    content = content,
                    category = selectedCategory,
                    authorName = "Người gửi ẩn danh",
                    branchIndex = targetBranch,
                    tagColorType = (0..2).random()
                )

                dialog.dismiss()

                // Play Hanging Ceremony Fly Animation
                playHangingCeremonyAnimation(createdWish, targetBranch)
            }
        }

        dialog.show()
    }

    // Hanging Ceremony Animation: Tag flies up to the tree branch
    private fun playHangingCeremonyAnimation(wish: WishEntity, branchIndex: Int) {
        val anchor = branchAnchors[branchIndex % branchAnchors.size]
        val canvasWidth = binding.treeBranchesCanvas.width
        val canvasHeight = binding.treeBranchesCanvas.height

        if (canvasWidth == 0 || canvasHeight == 0) return

        val tagBinding = ViewWishingTagBinding.inflate(
            layoutInflater,
            binding.treeBranchesCanvas,
            false
        )

        val category = WishCategory.fromName(wish.category)
        tagBinding.ivTagCategoryIcon.setImageResource(category.iconRes)
        tagBinding.tvTagContent.text = formatTagText(wish.content)

        val tagWidth = dpToPx(68)
        val tagHeight = dpToPx(96)
        val params = FrameLayout.LayoutParams(tagWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        tagBinding.root.layoutParams = params

        val targetX = (anchor.first * (canvasWidth - tagWidth)).coerceAtLeast(0f)
        val targetY = (anchor.second * (canvasHeight - tagHeight)).coerceAtLeast(0f)

        // Start from bottom center (near the CTA button)
        val startX = (canvasWidth / 2f) - (tagWidth / 2f)
        val startY = canvasHeight.toFloat() - dpToPx(30)

        tagBinding.root.x = startX
        tagBinding.root.y = startY
        tagBinding.root.scaleX = 0.3f
        tagBinding.root.scaleY = 0.3f
        tagBinding.root.alpha = 0f

        binding.treeBranchesCanvas.addView(tagBinding.root)

        val animX = ObjectAnimator.ofFloat(tagBinding.root, "x", startX, targetX)
        val animY = ObjectAnimator.ofFloat(tagBinding.root, "y", startY, targetY)
        val scaleX = ObjectAnimator.ofFloat(tagBinding.root, "scaleX", 0.3f, 1.25f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(tagBinding.root, "scaleY", 0.3f, 1.25f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(tagBinding.root, "alpha", 0f, 1.0f)
        val rot = ObjectAnimator.ofFloat(tagBinding.root, "rotation", 0f, -8f, 6f, -3f, 0f)

        val flySet = AnimatorSet().apply {
            playTogether(animX, animY, scaleX, scaleY, alpha, rot)
            duration = 1100
            interpolator = OvershootInterpolator(1.2f)
        }

        flySet.start()

        Toast.makeText(this, getString(R.string.wishing_tree_hang_success), Toast.LENGTH_LONG).show()
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
        dialogBinding.ivDetailCategoryIcon.setImageResource(category.iconRes)
        dialogBinding.tvDetailCategoryTitle.setText(category.titleRes)

        dialogBinding.tvDetailContent.text = "“${wish.content}”"
        dialogBinding.tvDetailAuthor.text = "— ${wish.authorName}"
        dialogBinding.tvDetailBlessingCount.text = getString(
            R.string.wishing_tree_blessings_count,
            wish.blessingCount
        )

        var hasBlessed = false
        dialogBinding.btnBlessWish.setSafeOnClickListener {
            if (!hasBlessed) {
                hasBlessed = true
                vibrateLight(25)
                lifecycleScope.launch {
                    repository.blessWish(wish.id)
                }
                val newBlessingCount = wish.blessingCount + 1
                dialogBinding.tvDetailBlessingCount.text = getString(
                    R.string.wishing_tree_blessings_count,
                    newBlessingCount
                )
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

    private fun cancelAllAnimators() {
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
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
        cancelAllAnimators()
        super.onDestroy()
    }
}

