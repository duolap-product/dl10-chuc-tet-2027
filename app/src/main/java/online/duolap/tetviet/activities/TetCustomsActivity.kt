package online.duolap.tetviet.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import online.duolap.tetviet.R
import online.duolap.tetviet.adapter.TetCustomsAdapter
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.data.TetCustomCategory
import online.duolap.tetviet.data.TetCustomItem
import online.duolap.tetviet.data.TetCustomRepository
import online.duolap.tetviet.databinding.ActivityTetCustomsBinding
import online.duolap.tetviet.databinding.DialogTetCustomDetailBinding

class TetCustomsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTetCustomsBinding
    private lateinit var adapter: TetCustomsAdapter

    private val allItems = TetCustomRepository.getAll()
    private var selectedCategory: TetCustomCategory = TetCustomCategory.ALL
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTetCustomsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppAnalytics.logScreenView("TetCustomsActivity")
        AppCrashlytics.log("Vào màn hình Phong Tục Tết")

        setupWindowInsets()
        setupViews()
        setupCategoryChips()
        setupSearch()
        applyFilterAndSearch()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }

        adapter = TetCustomsAdapter { item ->
            AppAnalytics.logFeatureClick("view_custom_${item.id}")
            showDetailDialog(item)
        }

        binding.rvCustoms.layoutManager = LinearLayoutManager(this)
        binding.rvCustoms.adapter = adapter
    }

    private fun setupCategoryChips() {
        val chipMap =
                mapOf(
                        binding.chipAll to TetCustomCategory.ALL,
                        binding.chipBeforeTet to TetCustomCategory.BEFORE_TET,
                        binding.chipEve to TetCustomCategory.NEW_YEAR_EVE,
                        binding.chipDuringTet to TetCustomCategory.TET_DAYS,
                        binding.chipAfterTet to TetCustomCategory.AFTER_TET
                )

        chipMap.forEach { (chipView, category) ->
            chipView.setOnClickListener {
                if (selectedCategory != category) {
                    selectedCategory = category
                    updateChipsUI(chipMap)
                    applyFilterAndSearch()
                }
            }
        }

        updateChipsUI(chipMap)
    }

    private fun updateChipsUI(chipMap: Map<TextView, TetCustomCategory>) {
        chipMap.forEach { (chipView, category) ->
            val isSelected = category == selectedCategory
            val title =
                    when (category) {
                        TetCustomCategory.ALL -> getString(R.string.customs_category_all_raw)
                        TetCustomCategory.BEFORE_TET ->
                                getString(R.string.customs_category_before_tet)
                        TetCustomCategory.NEW_YEAR_EVE -> getString(R.string.customs_category_eve)
                        TetCustomCategory.TET_DAYS ->
                                getString(R.string.customs_category_during_tet)
                        TetCustomCategory.AFTER_TET ->
                                getString(R.string.customs_category_after_tet)
                    }
            if (isSelected) {
                chipView.setBackgroundResource(R.drawable.bg_chip_selected)
                chipView.setTextColor(0xFF2C1A00.toInt())
                chipView.text = "✓ $title"
            } else {
                chipView.setBackgroundResource(R.drawable.bg_chip_unselected)
                chipView.setTextColor(0xFF5E5246.toInt())
                chipView.text = title
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        currentSearchQuery = s?.toString()?.trim().orEmpty()
                        binding.btnClearSearch.isVisible = currentSearchQuery.isNotEmpty()
                        applyFilterAndSearch()
                    }

                    override fun afterTextChanged(s: Editable?) {}
                }
        )

        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    @SuppressLint("SetTextI18n")
    private fun applyFilterAndSearch() {
        val filtered =
                allItems.filter { item ->
                    val matchesCategory =
                            (selectedCategory == TetCustomCategory.ALL) ||
                                    (item.category == selectedCategory)
                    val matchesQuery =
                            if (currentSearchQuery.isEmpty()) {
                                true
                            } else {
                                item.title.contains(currentSearchQuery, ignoreCase = true) ||
                                        item.description.contains(
                                                currentSearchQuery,
                                                ignoreCase = true
                                        ) ||
                                        item.meaning.contains(
                                                currentSearchQuery,
                                                ignoreCase = true
                                        ) ||
                                        item.category.displayName.contains(
                                                currentSearchQuery,
                                                ignoreCase = true
                                        )
                            }
                    matchesCategory && matchesQuery
                }

        adapter.submitList(filtered)

        binding.tvItemCount.text =
                getString(R.string.customs_count_format, filtered.size, allItems.size)

        val isEmpty = filtered.isEmpty()
        binding.layoutEmptyState.isVisible = isEmpty
        binding.rvCustoms.isVisible = !isEmpty
    }

    private fun showDetailDialog(item: TetCustomItem) {
        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val dialogBinding = DialogTetCustomDetailBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(dialogBinding.root)

        val catTitle =
                when (item.category) {
                    TetCustomCategory.ALL -> getString(R.string.customs_category_all_raw)
                    TetCustomCategory.BEFORE_TET -> getString(R.string.customs_category_before_tet)
                    TetCustomCategory.NEW_YEAR_EVE -> getString(R.string.customs_category_eve)
                    TetCustomCategory.TET_DAYS -> getString(R.string.customs_category_during_tet)
                    TetCustomCategory.AFTER_TET -> getString(R.string.customs_category_after_tet)
                }
        dialogBinding.tvDetailTitle.text = item.title
        dialogBinding.tvDetailCategory.text = catTitle
        dialogBinding.ivDetailIcon.setImageResource(item.iconResId)
        dialogBinding.tvDetailMeaning.text = item.meaning
        dialogBinding.tvDetailRitual.text = item.ritual
        dialogBinding.tvDetailTaboo.text = item.taboo

        dialogBinding.btnDetailClose.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDetailUnderstand.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
