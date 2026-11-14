package online.duolap.tetviet.activities

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import online.duolap.tetviet.ads.AdManager
import online.duolap.tetviet.ads.AdPlacement
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.data.local.VipPreferences
import online.duolap.tetviet.views.LuckyWheelView
import online.duolap.tetviet.data.model.LuckyWheelPrefHelper
import online.duolap.tetviet.data.model.SpinHistoryItem
import online.duolap.tetviet.data.model.WheelItem
import online.duolap.tetviet.data.model.WheelSticker
import online.duolap.tetviet.data.model.WheelStickerManager
import online.duolap.tetviet.databinding.ActivityLuckyWheelBinding
import online.duolap.tetviet.databinding.DialogAddEditPrizeBinding
import online.duolap.tetviet.databinding.DialogSpinHistoryBinding
import online.duolap.tetviet.databinding.DialogSpinResultBinding
import online.duolap.tetviet.databinding.DialogWheelSettingsBinding
import online.duolap.tetviet.databinding.ItemPaletteColorBinding
import online.duolap.tetviet.databinding.ItemPaletteStickerBinding
import online.duolap.tetviet.databinding.ItemSpinHistoryBinding
import online.duolap.tetviet.databinding.ItemWheelSettingBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class LuckyWheelActivity : AppCompatActivity() {

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var vipPreferences: VipPreferences

    private lateinit var binding: ActivityLuckyWheelBinding
    private var wheelItems = mutableListOf<WheelItem>()
    private var spinCount = 0

    // Festival colors palette for newly added items
    private val festiveColors = listOf(
        "#C62828", "#FFA000", "#D32F2F", "#FFB300",
        "#B71C1C", "#FF8F00", "#D81B60", "#E64A19",
        "#8E24AA", "#00897B", "#FB8C00", "#C2185B",
        "#1E88E5", "#43A047", "#5D4037", "#3949AB"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLuckyWheelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            false

        setupWindowInsets()
        loadWheelItems()
        setupListeners()

        AppAnalytics.logScreenView("LuckyWheelActivity")
        AppCrashlytics.log("LuckyWheelActivity onCreate")
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val topPaddingPx = systemBars.top + (4 * density).toInt()

            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                topPaddingPx,
                binding.topBar.paddingRight,
                (10 * density).toInt()
            )
            insets
        }
    }

    private fun loadWheelItems() {
        wheelItems = LuckyWheelPrefHelper.getWheelItems(this)
        binding.luckyWheelView.setItems(wheelItems)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        binding.btnCustomize.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnHistory.setOnClickListener {
            showHistoryDialog()
        }
        binding.btnViewHistory.setOnClickListener {
            showHistoryDialog()
        }

        binding.btnSpin.setOnClickListener {
            spinWithAdBreak()
        }
    }

    /**
     * Weighted Random Selection: Items with higher frequency have higher chances to win
     */
    private fun pickWeightedIndex(items: List<WheelItem>): Int {
        if (items.isEmpty()) return 0
        val totalWeight = items.sumOf { it.frequency.coerceIn(1, 100) }
        var randomWeight = Random.nextInt(totalWeight)
        for (i in items.indices) {
            val w = items[i].frequency.coerceIn(1, 100)
            if (randomWeight < w) {
                return i
            }
            randomWeight -= w
        }
        return items.indices.random()
    }

    /**
     * Điểm dừng tự nhiên: từ lượt quay thứ 2 trở đi, xét quảng cáo trước khi quay.
     * Tần suất do AdPolicy quyết định theo thời gian (khoảng cách tối thiểu, giới hạn phiên/ngày).
     * Gọi từ cả nút "Quay" lẫn "Quay lại" để đóng bảng kết quả rồi bấm "Quay" cũng được xét.
     */
    private fun spinWithAdBreak() {
        if (binding.luckyWheelView.isSpinning) return
        if (spinCount > 0 && wheelItems.size >= 2) {
            adManager.showInterstitialAd(this, AdPlacement.LUCKY_WHEEL_SPIN) { startLuckySpin() }
        } else {
            startLuckySpin()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startLuckySpin() {
        if (binding.luckyWheelView.isSpinning) return
        if (wheelItems.size < 2) {
            Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_need_at_least_2), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSpin.isEnabled = false
        binding.btnSpin.text = getString(online.duolap.tetviet.R.string.wheel_btn_spinning)
        binding.tvWheelInstruction.text = getString(online.duolap.tetviet.R.string.wheel_instruction_spinning)

        val targetIndex = pickWeightedIndex(wheelItems)
        binding.luckyWheelView.startSpin(targetIndex) { wonItem ->
            binding.btnSpin.isEnabled = true
            binding.btnSpin.text = getString(online.duolap.tetviet.R.string.wheel_btn_spin)
            binding.tvWheelInstruction.text = getString(online.duolap.tetviet.R.string.wheel_instruction_default)

            // Save to history
            LuckyWheelPrefHelper.addSpinHistory(this, wonItem.text)

            // Log Analytics & Crashlytics
            AppAnalytics.logSpinWheel(wonItem.text)
            AppCrashlytics.log("Người dùng quay trúng giải: ${wonItem.text}")


            if (isFinishing || isDestroyed) return@startSpin

            // Show congratulations popup
            showResultDialog(wonItem.text)
            spinCount++
        }
    }

    private fun showResultDialog(prizeText: String) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogSpinResultBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvResultPrize.text = prizeText
        dialogBinding.btnSpinAgain.setOnClickListener {
            dialog.dismiss()
            spinWithAdBreak()
        }
        dialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showSettingsDialog() {
        if (binding.luckyWheelView.isSpinning) return

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogWheelSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)

        val tempItems = wheelItems.toMutableList()

        dialogBinding.rvWheelItems.layoutManager = LinearLayoutManager(this)
        lateinit var adapter: WheelSettingsAdapter

        adapter = WheelSettingsAdapter(
            items = tempItems,
            onEditClick = { pos ->
                showAddEditPrizeDialog(
                    itemToEdit = tempItems[pos]
                ) { updatedItem ->
                    tempItems[pos] = updatedItem
                    adapter.notifyItemChanged(pos)
                }
            },
            onDeleteClick = { deletedPos ->
                if (tempItems.size <= 2) {
                    Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_need_min_2), Toast.LENGTH_SHORT).show()
                    return@WheelSettingsAdapter
                }
                tempItems.removeAt(deletedPos)
                adapter.notifyItemRemoved(deletedPos)
                adapter.notifyItemRangeChanged(deletedPos, tempItems.size)
            }
        )
        dialogBinding.rvWheelItems.adapter = adapter

        // Add new item opens comprehensive dialog
        dialogBinding.btnAddPrize.setOnClickListener {
            if (tempItems.size >= 16) {
                Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_max_16), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddEditPrizeDialog(
                itemToEdit = null
            ) { newItem ->
                tempItems.add(newItem)
                adapter.notifyItemInserted(tempItems.size - 1)
                dialogBinding.rvWheelItems.smoothScrollToPosition(tempItems.size - 1)
            }
        }

        // Reset to default
        dialogBinding.btnResetDefault.setOnClickListener {
            tempItems.clear()
            tempItems.addAll(LuckyWheelPrefHelper.resetWheelItemsToDefault(this))
            adapter.notifyDataSetChanged()
            Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_reset_success), Toast.LENGTH_SHORT).show()
        }

        // Save & Apply
        dialogBinding.btnSaveWheelSettings.setOnClickListener {
            if (tempItems.size < 2) {
                Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_need_min_2), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            wheelItems.clear()
            wheelItems.addAll(tempItems)
            LuckyWheelPrefHelper.saveWheelItems(this, wheelItems)
            binding.luckyWheelView.setItems(wheelItems)

            dialog.dismiss()
            Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_updated), Toast.LENGTH_SHORT).show()
        }

        dialogBinding.btnCloseWheelSettings.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Dialog to Add or Edit a prize option with Text, Frequency (1-100), Background Color and Background Sticker
     */
    private fun showAddEditPrizeDialog(
        itemToEdit: WheelItem?,
        onSaved: (WheelItem) -> Unit
    ) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogAddEditPrizeBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val isEditing = itemToEdit != null
        dialogBinding.tvDialogTitle.text = if (isEditing) {
            getString(online.duolap.tetviet.R.string.wheel_prize_edit_title)
        } else {
            getString(online.duolap.tetviet.R.string.wheel_prize_add_title)
        }

        // Initial values
        var selectedColor = itemToEdit?.colorHex ?: festiveColors[wheelItems.size % festiveColors.size]
        var selectedStickerId = itemToEdit?.bgImageName ?: "none"
        var currentFrequency = itemToEdit?.frequency ?: 50

        // 1. Text input
        if (isEditing) {
            dialogBinding.etPrizeTitle.setText(itemToEdit.text)
        }

        // 2. Frequency SeekBar (1 - 100)
        @SuppressLint("SetTextI18n")
        fun updateFrequencyUI(freq: Int) {
            dialogBinding.tvFrequencyNumber.text = freq.toString()
            val desc = when {
                freq <= 10 -> "Cực hiếm (Tỉ lệ xuất hiện rất thấp)"
                freq <= 30 -> "Hiếm (Ít xuất hiện)"
                freq <= 60 -> "Bình thường"
                freq <= 85 -> "Dễ xuất hiện"
                else -> "Rất dễ xuất hiện (Tỉ lệ trúng cao nhất)"
            }
            dialogBinding.tvFrequencyHint.text = "★ $desc"
        }

        dialogBinding.sbFrequency.progress = (currentFrequency - 1).coerceIn(0, 99)
        updateFrequencyUI(currentFrequency)

        dialogBinding.sbFrequency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentFrequency = progress + 1
                updateFrequencyUI(currentFrequency)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 3. Color Palette RecyclerView
        dialogBinding.rvColorPalette.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        lateinit var colorAdapter: ColorPaletteAdapter
        colorAdapter = ColorPaletteAdapter(festiveColors, selectedColor) { newColor ->
            selectedColor = newColor
            colorAdapter.setSelectedColor(newColor)
        }
        dialogBinding.rvColorPalette.adapter = colorAdapter

        // 4. Sticker Palette RecyclerView
        dialogBinding.rvStickerPalette.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        lateinit var stickerAdapter: StickerPaletteAdapter
        stickerAdapter = StickerPaletteAdapter(WheelStickerManager.STICKERS, selectedStickerId) { newSticker ->
            selectedStickerId = newSticker.id
            stickerAdapter.setSelectedId(newSticker.id)
        }
        dialogBinding.rvStickerPalette.adapter = stickerAdapter

        // 5. Actions
        dialogBinding.btnCancelEdit.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSavePrize.setOnClickListener {
            val text = dialogBinding.etPrizeTitle.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_enter_prize_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val resultItem = if (isEditing) {
                itemToEdit.copy(
                    text = text,
                    colorHex = selectedColor,
                    frequency = currentFrequency,
                    bgImageName = if (selectedStickerId == "none") null else selectedStickerId
                )
            } else {
                WheelItem(
                    text = text,
                    colorHex = selectedColor,
                    frequency = currentFrequency,
                    bgImageName = if (selectedStickerId == "none") null else selectedStickerId
                )
            }

            onSaved(resultItem)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showHistoryDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogSpinHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val historyList = LuckyWheelPrefHelper.getSpinHistory(this)

        if (historyList.isEmpty()) {
            dialogBinding.tvEmptyHistory.visibility = View.VISIBLE
            dialogBinding.rvSpinHistory.visibility = View.GONE
            dialogBinding.btnClearHistory.isEnabled = false
        } else {
            dialogBinding.tvEmptyHistory.visibility = View.GONE
            dialogBinding.rvSpinHistory.visibility = View.VISIBLE
            dialogBinding.rvSpinHistory.layoutManager = LinearLayoutManager(this)
            dialogBinding.rvSpinHistory.adapter = SpinHistoryAdapter(historyList)
            dialogBinding.btnClearHistory.isEnabled = true
        }

        dialogBinding.btnClearHistory.setOnClickListener {
            LuckyWheelPrefHelper.clearSpinHistory(this)
            historyList.clear()
            dialogBinding.tvEmptyHistory.visibility = View.VISIBLE
            dialogBinding.rvSpinHistory.visibility = View.GONE
            dialogBinding.btnClearHistory.isEnabled = false
            Toast.makeText(this, getString(online.duolap.tetviet.R.string.msg_wheel_history_cleared), Toast.LENGTH_SHORT).show()
        }

        dialogBinding.btnCloseHistory.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // Adapter for Wheel Settings
    private class WheelSettingsAdapter(
        private val items: List<WheelItem>,
        private val onEditClick: (Int) -> Unit,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<WheelSettingsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemWheelSettingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWheelSettingBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvPrizeName.text = item.text

            val totalWeight = maxOf(1, items.sumOf { it.frequency })
            val percent = (item.frequency * 100) / totalWeight
            holder.binding.tvPrizeFrequency.text = holder.itemView.context.getString(
                online.duolap.tetviet.R.string.wheel_freq_format,
                item.frequency,
                percent
            )

            // Color circle
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                try {
                    setColor(item.colorHex.toColorInt())
                } catch (_: Exception) {
                    setColor(Color.RED)
                }
            }
            holder.binding.viewColorDot.background = drawable

            // Sticker icon
            val stickerResId = WheelStickerManager.getDrawableResId(item.bgImageName)
            if (stickerResId != 0) {
                holder.binding.ivItemSticker.visibility = View.VISIBLE
                holder.binding.ivItemSticker.setImageResource(stickerResId)
            } else {
                holder.binding.ivItemSticker.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onEditClick(holder.bindingAdapterPosition)
            }
            holder.binding.btnEditPrize.setOnClickListener {
                onEditClick(holder.bindingAdapterPosition)
            }
            holder.binding.btnDeletePrize.setOnClickListener {
                onDeleteClick(holder.bindingAdapterPosition)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    // Adapter for Color Palette Selection
    private class ColorPaletteAdapter(
        private val colors: List<String>,
        private var selectedColor: String,
        private val onColorSelected: (String) -> Unit
    ) : RecyclerView.Adapter<ColorPaletteAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemPaletteColorBinding) :
            RecyclerView.ViewHolder(binding.root)

        @SuppressLint("NotifyDataSetChanged")
        fun setSelectedColor(color: String) {
            selectedColor = color
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPaletteColorBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val colorHex = colors[position]
            val isSelected = colorHex.equals(selectedColor, ignoreCase = true)

            val colorCircleDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                try {
                    setColor(colorHex.toColorInt())
                } catch (_: Exception) {
                    setColor(Color.RED)
                }
            }
            holder.binding.viewColorCircle.background = colorCircleDrawable

            holder.binding.viewSelectionRing.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.binding.ivCheckmark.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                onColorSelected(colorHex)
            }
        }

        override fun getItemCount(): Int = colors.size
    }

    // Adapter for Sticker Palette Selection
    private class StickerPaletteAdapter(
        private val stickers: List<WheelSticker>,
        private var selectedId: String,
        private val onStickerSelected: (WheelSticker) -> Unit
    ) : RecyclerView.Adapter<StickerPaletteAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemPaletteStickerBinding) :
            RecyclerView.ViewHolder(binding.root)

        @SuppressLint("NotifyDataSetChanged")
        fun setSelectedId(id: String) {
            selectedId = id
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPaletteStickerBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sticker = stickers[position]
            val isSelected = sticker.id == selectedId

            holder.binding.tvStickerName.text = sticker.name

            if (sticker.resId != 0) {
                holder.binding.ivStickerPreview.setImageResource(sticker.resId)
            } else {
                holder.binding.ivStickerPreview.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            }

            if (isSelected) {
                holder.binding.layoutStickerContainer.setBackgroundResource(online.duolap.tetviet.R.drawable.bg_countdown_card)
                holder.binding.tvStickerName.setTextColor("#7A090D".toColorInt())
            } else {
                holder.binding.layoutStickerContainer.setBackgroundResource(online.duolap.tetviet.R.drawable.bg_feature_card)
                holder.binding.tvStickerName.setTextColor("#5D4037".toColorInt())
            }

            holder.itemView.setOnClickListener {
                onStickerSelected(sticker)
            }
        }

        override fun getItemCount(): Int = stickers.size
    }

    private class SpinHistoryAdapter(
        private val list: List<SpinHistoryItem>
    ) : RecyclerView.Adapter<SpinHistoryAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("HH:mm:ss • dd/MM/yyyy", Locale.getDefault())

        class ViewHolder(val binding: ItemSpinHistoryBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSpinHistoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.binding.tvHistoryPrize.text = item.prizeText
            holder.binding.tvHistoryTime.text = dateFormat.format(Date(item.timestamp))
        }

        override fun getItemCount(): Int = list.size
    }
}