package online.duolap.tetviet.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.duolap.tetviet.R
import online.duolap.tetviet.adapter.BackgroundGridAdapter
import online.duolap.tetviet.adapter.StickerGridAdapter
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.UnlockVipFeatureDialog
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.data.local.VipPreferences
import online.duolap.tetviet.data.model.CardBackground
import online.duolap.tetviet.data.model.CardBackgroundManager
import online.duolap.tetviet.data.model.CardColorManager
import online.duolap.tetviet.data.model.CardStickerItem
import online.duolap.tetviet.data.model.CardStickerManager
import online.duolap.tetviet.databinding.ActivityCardMakerBinding
import online.duolap.tetviet.databinding.DialogCardSavedBottomSheetBinding
import online.duolap.tetviet.databinding.DialogChooseBgBottomSheetBinding
import online.duolap.tetviet.databinding.DialogChooseStickerBottomSheetBinding
import online.duolap.tetviet.databinding.DialogEditWishBottomSheetBinding
import online.duolap.tetviet.databinding.ItemPaletteColorBinding
import online.duolap.tetviet.viewmodel.CardMakerViewModel
import online.duolap.tetviet.views.RedBoundsDrawable
import online.duolap.tetviet.views.StickerView
import online.duolap.tetviet.views.WishTransformHelper
import online.duolap.tetviet.views.setSafeOnClickListener
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class CardMakerActivity : AppCompatActivity() {

    private val viewModel: CardMakerViewModel by viewModels()

    @Inject
    lateinit var vipPreferences: VipPreferences

    @Inject
    lateinit var adManager: AdManager

    private lateinit var binding: ActivityCardMakerBinding
    private lateinit var redBoundsDrawable: RedBoundsDrawable
    private lateinit var transformHelper: WishTransformHelper

    private var backgroundCards: List<CardBackground> = CardBackgroundManager.BACKGROUND
    private var stickerItems: List<CardStickerItem> = CardStickerManager.STICKERS
    private var currentBgId: String = CardBackgroundManager.currentBgId

    private val activeStickers = mutableListOf<StickerView>()
    private val maxStickers = 3

    private var currentColor: Int = "#7A090D".toColorInt()
    private var currentGravity: Int = Gravity.CENTER
    private var currentTypefaceResId: Int = R.font.liberationsans_bold

    private var tetColors: List<Pair<String, String>> = CardColorManager.COLORS.map { Pair(it.name, it.colorHex) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCardMakerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWindowInsets()

        AppAnalytics.logScreenView("CardMakerActivity")
        AppCrashlytics.log("Vào màn hình Tạo Thiệp Tết")

        setupViews()
        setupWishTextTransform()
        setupObservers()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.backgrounds.collect { backgrounds ->
                        if (backgrounds.isNotEmpty()) {
                            backgroundCards = backgrounds
                            val currentBg = backgrounds.firstOrNull { it.id == currentBgId } ?: backgrounds.first()
                            applyCardBackground(currentBg)
                        }
                    }
                }
                launch {
                    viewModel.stickers.collect { stickers ->
                        if (stickers.isNotEmpty()) {
                            stickerItems = stickers
                        }
                    }
                }
                launch {
                    viewModel.colors.collect { colors ->
                        if (colors.isNotEmpty()) {
                            tetColors = colors
                        }
                    }
                }
            }
        }
    }

    private fun applyCardBackground(bgItem: CardBackground) {
        currentBgId = bgItem.id
        if (bgItem.url?.startsWith("http://") == true || bgItem.url?.startsWith("https://") == true) {
            Glide.with(this)
                .load(bgItem.url)
                .placeholder(R.drawable.bg_card_free_01)
                .centerCrop()
                .into(binding.ivCardBg)
        } else if (bgItem.resId != 0) {
            binding.ivCardBg.setImageResource(bgItem.resId)
        } else if (!bgItem.url.isNullOrEmpty()) {
            val resId = resources.getIdentifier(bgItem.url, "drawable", packageName)
            if (resId != 0) {
                binding.ivCardBg.setImageResource(resId)
            } else {
                Glide.with(this).load(bgItem.url).into(binding.ivCardBg)
            }
        }
        applyCardRatio(bgItem.ratio)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            exportAndSaveCard()
        } else {
            Toast.makeText(this, getString(R.string.msg_storage_permission_needed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViews() {
        binding.btnBack.setSafeOnClickListener(hasAnim = true) {
            finish()
        }

        binding.btnDownload.setSafeOnClickListener(hasAnim = true) {
            exportAndSaveCard()
        }

        binding.btnShare.setSafeOnClickListener(hasAnim = true) {
            shareCard()
        }

        binding.btnSendWish.setSafeOnClickListener(hasAnim = true) {
            shareCard()
        }

        binding.btnChangeBg.setSafeOnClickListener(hasAnim = true) {
            showChooseBackgroundBottomSheet()
        }

        binding.btnEditWish.setSafeOnClickListener(hasAnim = true) {
            deselectAllStickers()
            if (!redBoundsDrawable.isBoundsVisible) {
                redBoundsDrawable.isBoundsVisible = true
                binding.tvWish.invalidate()
            }
            showEditWishBottomSheet()
        }

        binding.btnAddSticker.setSafeOnClickListener(hasAnim = true) {
            onAddStickerClicked()
        }

        binding.ivCardBg.setOnLongClickListener {
            showChooseBackgroundBottomSheet()
            return@setOnLongClickListener false
        }

        binding.ivCardBg.setSafeOnClickListener(hasAnim = false) {
            if (activeStickers.any { it.isSelectedSticker }) {
                deselectAllStickers()
            }
            if (redBoundsDrawable.isBoundsVisible) {
                redBoundsDrawable.isBoundsVisible = false
                binding.tvWish.invalidate()
            }
        }

        binding.layoutCardContent.setOnClickListener {
            if (activeStickers.any { it.isSelectedSticker }) {
                deselectAllStickers()
            }
            if (redBoundsDrawable.isBoundsVisible) {
                redBoundsDrawable.isBoundsVisible = false
                binding.tvWish.invalidate()
            }
        }

        updateStickerButtonCount()
    }

    @SuppressLint("SetTextI18n")
    private fun updateStickerButtonCount() {
        binding.btnAddSticker.text = getString(R.string.card_maker_btn_sticker, activeStickers.size, maxStickers)
    }

    private fun deselectAllStickers() {
        activeStickers.forEach { it.isSelectedSticker = false }
    }

    private fun selectSticker(selectedView: StickerView) {
        if (redBoundsDrawable.isBoundsVisible) {
            redBoundsDrawable.isBoundsVisible = false
            binding.tvWish.invalidate()
        }
        activeStickers.forEach { it.isSelectedSticker = (it === selectedView) }
        selectedView.bringToFront()
    }

    private fun onAddStickerClicked() {
        if (activeStickers.size >= maxStickers) {
            Toast.makeText(this, getString(R.string.msg_max_stickers_reached, maxStickers), Toast.LENGTH_SHORT).show()
            return
        }
        showChooseStickerBottomSheet()
    }

    private fun showChooseStickerBottomSheet() {
        AppAnalytics.logFeatureClick("open_choose_sticker_bottom_sheet")

        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val sheetBinding = DialogChooseStickerBottomSheetBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvStickerSheetSubtitle.text =
            getString(R.string.card_maker_sticker_sheet_subtitle, activeStickers.size, maxStickers)

        sheetBinding.rvStickers.layoutManager = GridLayoutManager(this, 4)
        sheetBinding.rvStickers.adapter = StickerGridAdapter(
            items = stickerItems,
            isItemUnlocked = { id -> vipPreferences.isFeatureUnlocked(id) },
            onItemClick = { stickerItem ->
                if (activeStickers.size >= maxStickers) {
                    Toast.makeText(this, getString(R.string.msg_max_stickers_reached, maxStickers), Toast.LENGTH_SHORT)
                        .show()
                    dialog.dismiss()
                    return@StickerGridAdapter
                }
                if (!stickerItem.isVip || vipPreferences.isFeatureUnlocked(stickerItem.id)) {
                    addStickerToCard(stickerItem)
                } else {
                    val unlockDialog = UnlockVipFeatureDialog.newInstance(
                        featureId = stickerItem.id,
                        featureName = stickerItem.name
                    ) {
                        addStickerToCard(stickerItem)
                    }
                    unlockDialog.show(supportFragmentManager, "unlock_sticker_dialog")
                }
                dialog.dismiss()
            }
        )

        sheetBinding.btnCloseStickerSheet.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addStickerToCard(stickerItem: CardStickerItem) {
        if (activeStickers.size >= maxStickers) {
            Toast.makeText(this, getString(R.string.msg_max_stickers_reached, maxStickers), Toast.LENGTH_SHORT).show()
            return
        }

        val stickerView = StickerView(this)
        stickerView.setSticker(stickerItem)

        stickerView.onDeleteClickListener = { viewToRemove ->
            removeStickerFromCard(viewToRemove)
        }

        stickerView.onSelectedListener = { selectedView ->
            selectSticker(selectedView)
        }

        val parentW = binding.layoutCardContent.width
        val parentH = binding.layoutCardContent.height
        val totalSize = stickerView.getTotalSize()

        // Tính vị trí ban đầu so le đẹp mắt trên thiệp, tránh đè vào text lời chúc
        val (targetX, targetY) = when (activeStickers.size) {
            0 -> Pair(
                ((parentW - totalSize) * 0.72f).coerceAtLeast(0f),
                ((parentH - totalSize) * 0.15f).coerceAtLeast(0f)
            )

            1 -> Pair(
                ((parentW - totalSize) * 0.15f).coerceAtLeast(0f),
                ((parentH - totalSize) * 0.75f).coerceAtLeast(0f)
            )

            else -> Pair(
                ((parentW - totalSize) * 0.15f).coerceAtLeast(0f),
                ((parentH - totalSize) * 0.15f).coerceAtLeast(0f)
            )
        }

        stickerView.x = targetX
        stickerView.y = targetY

        binding.layoutCardContent.addView(stickerView)
        activeStickers.add(stickerView)
        selectSticker(stickerView)
        updateStickerButtonCount()

        AppAnalytics.logFeatureClick("add_sticker_${stickerItem.id}")
        Toast.makeText(this, getString(R.string.msg_added_sticker, stickerItem.name), Toast.LENGTH_SHORT).show()
    }

    private fun removeStickerFromCard(stickerView: StickerView) {
        binding.layoutCardContent.removeView(stickerView)
        activeStickers.remove(stickerView)
        updateStickerButtonCount()
        Toast.makeText(this, getString(R.string.msg_removed_sticker), Toast.LENGTH_SHORT).show()
        AppAnalytics.logFeatureClick("delete_sticker")
    }

    private fun showChooseBackgroundBottomSheet() {
        AppAnalytics.logFeatureClick("open_choose_bg_bottom_sheet")

        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val sheetBinding = DialogChooseBgBottomSheetBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(sheetBinding.root)

        sheetBinding.rvBackgrounds.layoutManager = GridLayoutManager(this, 3)
        sheetBinding.rvBackgrounds.isNestedScrollingEnabled = true
        sheetBinding.rvBackgrounds.adapter = BackgroundGridAdapter(
            items = backgroundCards,
            selectedId = currentBgId,
            isItemUnlocked = { id -> vipPreferences.isFeatureUnlocked(id) },
            onItemClick = { bgItem ->
                if (currentBgId != bgItem.id) {
                    if (bgItem.isFree || vipPreferences.isFeatureUnlocked(bgItem.id)) {
                        applyCardBackground(bgItem)
                        AppAnalytics.logFeatureClick("select_bg_${bgItem.id}")
                    } else {
                        val unlockDialog = UnlockVipFeatureDialog.newInstance(
                            featureId = bgItem.id,
                            featureName = getString(R.string.card_maker_choose_bg_title) + ": " + bgItem.id
                        ) {
                            applyCardBackground(bgItem)
                        }
                        unlockDialog.show(supportFragmentManager, "unlock_bg_dialog")
                    }
                }
                dialog.dismiss()
            }
        )

        sheetBinding.btnCloseBgSheet.setOnClickListener {
            dialog.dismiss()
        }

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val topThreshold = (56 * displayMetrics.density).toInt()

        val behavior = dialog.behavior
        behavior.isFitToContents = true
        behavior.peekHeight = (screenHeight * 0.58f).toInt()
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        behavior.skipCollapsed = false
        behavior.isHideable = true

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val lp = bottomSheet.layoutParams
                lp.height = screenHeight - topThreshold
                bottomSheet.layoutParams = lp
            }
        }

        dialog.show()
    }

    private fun applyCardRatio(ratio: String) {
        try {
            val constraintSet = ConstraintSet()
            constraintSet.clone(binding.main)
            constraintSet.setDimensionRatio(binding.cardBgWish.id, ratio)
            constraintSet.applyTo(binding.main)

            binding.cardBgWish.post {
                val parentW = binding.cardBgWish.width
                val parentH = binding.cardBgWish.height
                if (parentW <= 0 || parentH <= 0) return@post

                val density = resources.displayMetrics.density
                val minW = (100f * density).toInt()
                val minH = (50f * density).toInt()

                val lp = binding.tvWish.layoutParams
                var needRelayout = false

                if (binding.tvWish.width > parentW * 0.85f) {
                    lp.width = (parentW * 0.80f).toInt().coerceAtLeast(minW)
                    needRelayout = true
                }
                if (binding.tvWish.height > parentH * 0.70f) {
                    lp.height = (parentH * 0.50f).toInt().coerceAtLeast(minH)
                    needRelayout = true
                }

                if (needRelayout) {
                    binding.tvWish.layoutParams = lp
                    binding.tvWish.requestLayout()
                }

                val maxX = (parentW - binding.tvWish.width).toFloat().coerceAtLeast(0f)
                val maxY = (parentH - binding.tvWish.height).toFloat().coerceAtLeast(0f)

                binding.tvWish.x = binding.tvWish.x.coerceIn(0f, maxX)
                binding.tvWish.y = binding.tvWish.y.coerceIn(0f, maxY)

                activeStickers.forEach { sticker ->
                    val maxStickerX = (parentW - sticker.width).toFloat().coerceAtLeast(0f)
                    val maxStickerY = (parentH - sticker.height).toFloat().coerceAtLeast(0f)
                    sticker.x = sticker.x.coerceIn(0f, maxStickerX)
                    sticker.y = sticker.y.coerceIn(0f, maxStickerY)
                }
            }
        } catch (e: Exception) {
            AppCrashlytics.recordException(e, "CardMakerActivity.applyCardRatio")
        }
    }

    private fun setupWishTextTransform() {
        val density = resources.displayMetrics.density

        redBoundsDrawable = RedBoundsDrawable(density = density)
        binding.tvWish.background = redBoundsDrawable

        transformHelper = WishTransformHelper(
            targetView = binding.tvWish,
            parentContainer = binding.layoutCardContent,
            minWidthDp = 100f,
            minHeightDp = 50f,
            onResizeStateChanged = { isResizing ->
                redBoundsDrawable.isResizing = isResizing
                if (isResizing) {
                    deselectAllStickers()
                    if (!redBoundsDrawable.isBoundsVisible) {
                        redBoundsDrawable.isBoundsVisible = true
                        binding.tvWish.invalidate()
                    }
                }
            },
            onDragStarted = {
                deselectAllStickers()
                if (!redBoundsDrawable.isBoundsVisible) {
                    redBoundsDrawable.isBoundsVisible = true
                    binding.tvWish.invalidate()
                }
            },
            onClick = {
                deselectAllStickers()
                if (!redBoundsDrawable.isBoundsVisible) {
                    redBoundsDrawable.isBoundsVisible = true
                    binding.tvWish.invalidate()
                } else {
                    showEditWishBottomSheet()
                }
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun showEditWishBottomSheet() {
        AppAnalytics.logFeatureClick("open_edit_wish_bottom_sheet")

        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val sheetBinding = DialogEditWishBottomSheetBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(sheetBinding.root)

        val currentText = binding.tvWish.text.toString()
        sheetBinding.etWishContent.setText(currentText)
        sheetBinding.etWishContent.setSelection(sheetBinding.etWishContent.text.length)
        sheetBinding.btnClearWishContent.isVisible = currentText.isNotEmpty()

        sheetBinding.etWishContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                binding.tvWish.text = text.ifEmpty { getString(R.string.card_maker_placeholder_wish) }
                sheetBinding.btnClearWishContent.isVisible = text.isNotEmpty()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        sheetBinding.btnClearWishContent.setSafeOnClickListener(hasAnim = true) {
            sheetBinding.etWishContent.setText("")
            binding.tvWish.text = getString(R.string.card_maker_placeholder_wish)
            sheetBinding.etWishContent.requestFocus()
        }

        val suggestionChips = listOf(
            sheetBinding.chipWish1,
            sheetBinding.chipWish2,
            sheetBinding.chipWish3,
            sheetBinding.chipWish4,
            sheetBinding.chipWish5
        )
        suggestionChips.forEach { chip ->
            chip.setSafeOnClickListener(hasAnim = true) {
                val wish = chip.text.toString()
                sheetBinding.etWishContent.setText(wish)
                binding.tvWish.text = wish
                sheetBinding.etWishContent.setSelection(wish.length)
            }
        }

        setupColorPalette(sheetBinding)
        setupGravityControls(sheetBinding)
        setupFontControls(sheetBinding)

        sheetBinding.btnCloseSheet.setSafeOnClickListener(hasAnim = true) {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupColorPalette(sheetBinding: DialogEditWishBottomSheetBinding) {
        val colorBindings = mutableListOf<ItemPaletteColorBinding>()

        sheetBinding.layoutColorPalette.removeAllViews()

        tetColors.forEach { (_, hex) ->
            val colorInt = hex.toColorInt()
            val itemBinding = ItemPaletteColorBinding.inflate(layoutInflater, sheetBinding.layoutColorPalette, false)

            itemBinding.viewColorCircle.backgroundTintList = ColorStateList.valueOf(colorInt)

            val isSelected = (colorInt == currentColor)
            itemBinding.viewSelectionRing.isVisible = isSelected
            itemBinding.ivCheckmark.isVisible = isSelected
            itemBinding.ivCheckmark.imageTintList = ColorStateList.valueOf(
                if (hex == "#FFFFFF") "#7A090D".toColorInt() else Color.WHITE
            )

            itemBinding.root.setOnClickListener {
                currentColor = colorInt
                binding.tvWish.setTextColor(colorInt)

                colorBindings.forEachIndexed { index, b ->
                    val cInt = tetColors[index].second.toColorInt()
                    val selected = (cInt == currentColor)
                    b.viewSelectionRing.isVisible = selected
                    b.ivCheckmark.isVisible = selected
                }
            }

            colorBindings.add(itemBinding)
            sheetBinding.layoutColorPalette.addView(itemBinding.root)
        }
    }

    private fun setupGravityControls(sheetBinding: DialogEditWishBottomSheetBinding) {
        fun updateGravityUI(gravity: Int) {
            val isLeft = (gravity and Gravity.START) == Gravity.START || (gravity and Gravity.START) == Gravity.START
            val isRight = (gravity and Gravity.END) == Gravity.END || (gravity and Gravity.END) == Gravity.END
            val isCenter = !isLeft && !isRight

            sheetBinding.btnAlignLeft.setBackgroundResource(if (isLeft) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            sheetBinding.ivAlignLeft.imageTintList =
                ColorStateList.valueOf(if (isLeft) 0xFF2C1A00.toInt() else 0xFF5E5246.toInt())
            sheetBinding.tvAlignLeft.setTextColor(if (isLeft) 0xFF2C1A00.toInt() else 0xFF5E5246.toInt())

            sheetBinding.btnAlignCenter.setBackgroundResource(if (isCenter) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            sheetBinding.ivAlignCenter.imageTintList =
                ColorStateList.valueOf(if (isCenter) 0xFF2C1A00.toInt() else 0xFF5E5246.toInt())
            sheetBinding.tvAlignCenter.setTextColor(if (isCenter) 0xFF2C1A00.toInt() else 0xFF5E5246.toInt())

            sheetBinding.btnAlignRight.setBackgroundResource(if (isRight) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            sheetBinding.ivAlignRight.imageTintList =
                ColorStateList.valueOf(if (isRight) 0xFF2C1A00.toInt() else 0xFF5E5246.toInt())
            sheetBinding.tvAlignRight.setTextColor(if (isRight) 0xFF2C1A00.toInt() else 0xFF5E5246.toInt())
        }

        updateGravityUI(currentGravity)

        sheetBinding.btnAlignLeft.setOnClickListener {
            currentGravity = Gravity.START or Gravity.CENTER_VERTICAL
            binding.tvWish.gravity = currentGravity
            updateGravityUI(currentGravity)
        }

        sheetBinding.btnAlignCenter.setOnClickListener {
            currentGravity = Gravity.CENTER
            binding.tvWish.gravity = currentGravity
            updateGravityUI(currentGravity)
        }

        sheetBinding.btnAlignRight.setOnClickListener {
            currentGravity = Gravity.END or Gravity.CENTER_VERTICAL
            binding.tvWish.gravity = currentGravity
            updateGravityUI(currentGravity)
        }
    }

    private fun setupFontControls(sheetBinding: DialogEditWishBottomSheetBinding) {
        val fontOptions = listOf(
            Triple(sheetBinding.btnFontLiberationBold, R.font.liberationsans_bold, Typeface.DEFAULT_BOLD),
            Triple(sheetBinding.btnFontLobsterTwo, R.font.lobstertwo_bold, null),
            Triple(sheetBinding.btnFontLobsterItalic, R.font.lobstertwo_bolditalic, null),
            Triple(sheetBinding.btnFontLiberationRegular, R.font.liberationsans_regular, Typeface.DEFAULT),
            Triple(sheetBinding.btnFontBangers, R.font.bangers_regular, null),
            Triple(sheetBinding.btnFontSerif, 0, Typeface.SERIF)
        )

        fun updateFontUI(selectedResId: Int) {
            fontOptions.forEach { (view, resId, _) ->
                val isSelected = (resId == selectedResId)
                view.setBackgroundResource(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
                view.setTextColor(if (isSelected) 0xFF2C1A00.toInt() else 0xFF5E5246.toInt())
            }
        }

        updateFontUI(currentTypefaceResId)

        fontOptions.forEach { (view, resId, fallbackTypeface) ->
            view.setOnClickListener {
                currentTypefaceResId = resId
                val tf = if (resId != 0) {
                    ResourcesCompat.getFont(this, resId) ?: fallbackTypeface ?: Typeface.DEFAULT
                } else {
                    fallbackTypeface ?: Typeface.DEFAULT
                }
                binding.tvWish.typeface = tf
                updateFontUI(resId)
            }
        }
    }

    /**
     * Chụp lại Bitmap của cardBgWish (tạm ẩn viền đỏ và phục hồi lại ngay sau khi chụp)
     * Bo tròn 4 góc theo đúng corner radius của cardBgWish, khử răng cưa và trong suốt các góc ngoài
     */
    private fun captureCardBitmap(): Bitmap {
        val wasBoundsVisible = redBoundsDrawable.isBoundsVisible
        redBoundsDrawable.isBoundsVisible = false
        binding.tvWish.invalidate()

        activeStickers.forEach { it.isExportMode = true }

        val width = binding.cardBgWish.width
        val height = binding.cardBgWish.height

        val rawBitmap = createBitmap(width, height)
        val rawCanvas = Canvas(rawBitmap)
        binding.cardBgWish.draw(rawCanvas)

        redBoundsDrawable.isBoundsVisible = wasBoundsVisible
        binding.tvWish.invalidate()

        activeStickers.forEach { it.isExportMode = false }

        // Cắt theo góc bo viền corner radius của cardBgWish với khử răng cưa (anti-aliasing)
        val roundedBitmap = createBitmap(width, height)
        val roundedCanvas = Canvas(roundedBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = binding.cardBgWish.radius

        paint.color = Color.BLACK
        roundedCanvas.drawRoundRect(rectF, radius, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        roundedCanvas.drawBitmap(rawBitmap, 0f, 0f, paint)

        rawBitmap.recycle()
        return roundedBitmap
    }

    private fun saveCardToPublicGallery(bitmap: Bitmap): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ThiepTet_$timeStamp.png"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TetViet")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
            uri
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val tetVietDir = File(picturesDir, "TetViet").apply {
                if (!exists()) mkdirs()
            }
            val imageFile = File(tetVietDir, fileName)
            FileOutputStream(imageFile).use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            MediaScannerConnection.scanFile(
                this,
                arrayOf(imageFile.absolutePath),
                arrayOf("image/png"),
                null
            )
            Uri.fromFile(imageFile)
        }
    }

    private fun showLoading(message: String) {
        binding.tvLoadingText.text = message
        binding.layoutLoading.isVisible = true
    }

    private fun hideLoading() {
        binding.layoutLoading.isVisible = false
    }

    private fun showCardSavedBottomSheet(bitmap: Bitmap) {
        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val sheetBinding = DialogCardSavedBottomSheetBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(sheetBinding.root)

        sheetBinding.ivSavedCardPreview.setImageBitmap(bitmap)

        sheetBinding.btnShareSavedCard.setOnClickListener {
            dialog.dismiss()
            shareCard()
        }

        sheetBinding.btnDismissSavedCard.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.btnCloseSavedSheet.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun exportAndSaveCard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        showLoading(getString(R.string.card_maker_saving))

        lifecycleScope.launch {
            try {
                val bitmap = captureCardBitmap()
                val savedUri = withContext(Dispatchers.IO) {
                    saveCardToPublicGallery(bitmap)
                }

                hideLoading()

                if (savedUri != null) {
                    AppAnalytics.logFeatureClick("download_card_success")
                    showCardSavedBottomSheet(bitmap)
                    adManager.showInterstitialAd(this@CardMakerActivity)
                } else {
                    Toast.makeText(this@CardMakerActivity, getString(R.string.msg_save_card_failed), Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                hideLoading()
                AppCrashlytics.recordException(e, "CardMakerActivity.exportAndSaveCard")
                Toast.makeText(
                    this@CardMakerActivity,
                    getString(R.string.msg_save_card_error, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Chia sẻ hình ảnh thiệp sang các ứng dụng khác (Zalo, Messenger, Facebook, Drive...), có loading
     */
    private fun shareCard() {
        showLoading(getString(R.string.card_maker_preparing_share))

        lifecycleScope.launch {
            try {
                val bitmap = captureCardBitmap()
                val contentUri = withContext(Dispatchers.IO) {
                    val imagesFolder = File(cacheDir, "images").apply {
                        if (!exists()) mkdirs()
                    }
                    val file = File(imagesFolder, "thiep_tet_share.png")
                    FileOutputStream(file).use { outStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    }

                    FileProvider.getUriForFile(
                        this@CardMakerActivity,
                        "${packageName}.fileprovider",
                        file
                    )
                }

                hideLoading()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_card_subject))
                    putExtra(
                        Intent.EXTRA_TEXT,
                        getString(R.string.share_card_message)
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                AppAnalytics.logFeatureClick("share_card")
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
            } catch (e: Exception) {
                hideLoading()
                AppCrashlytics.recordException(e, "CardMakerActivity.shareCard")
                Toast.makeText(
                    this@CardMakerActivity,
                    getString(R.string.msg_share_card_error, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }
}
