package online.duolap.tetviet.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import online.duolap.tetviet.R
import online.duolap.tetviet.adapter.TetPhotoAdapter
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.model.TetPhoto
import online.duolap.tetviet.data.model.TetPhotoRepository
import online.duolap.tetviet.databinding.ActivityTetPhotosBinding
import online.duolap.tetviet.views.setSafeOnClickListener

/**
 * Màn hình danh mục "Hình ảnh Tết":
 * Hiển thị lưới ảnh Tết nghệ thuật 2 cột, hỗ trợ lọc theo danh mục
 * và mở trình xem ảnh toàn màn hình với hiệu ứng làm mờ nền khi click vào bất kỳ ảnh nào.
 */
class TetPhotosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTetPhotosBinding
    private lateinit var adapter: TetPhotoAdapter

    private var currentCategory: String = "Tất cả"
    private var displayedPhotos: List<TetPhoto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTetPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootGallery) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.rvPhotos.setPadding(
                binding.rvPhotos.paddingLeft,
                binding.rvPhotos.paddingTop,
                binding.rvPhotos.paddingRight,
                systemBars.bottom + 16
            )
            insets
        }

        AppAnalytics.logScreenView("TetPhotosActivity")

        setupRecyclerView()
        setupCategoryChips()
        setupListeners()
        loadPhotos()
    }

    private fun setupRecyclerView() {
        displayedPhotos = TetPhotoRepository.sampleImages

        adapter = TetPhotoAdapter(displayedPhotos) { photo, position ->
            AppAnalytics.logFeatureClick("open_photo_viewer")
            PhotoViewerActivity.start(
                context = this,
                photos = displayedPhotos,
                initialIndex = position
            )
        }

        binding.rvPhotos.layoutManager = GridLayoutManager(this, 2)
        binding.rvPhotos.adapter = adapter
    }

    private fun setupCategoryChips() {
        binding.layoutCategoryChips.removeAllViews()

        TetPhotoRepository.categories.forEach { category ->
            val tvChip = TextView(this).apply {
                text = category
                textSize = 13f
                includeFontPadding = false
                val density = resources.displayMetrics.density
                setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
                val layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
                this.layoutParams = layoutParams
            }

            updateChipStyle(tvChip, category == currentCategory)

            tvChip.setSafeOnClickListener(hasAnim = true) {
                if (currentCategory != category) {
                    currentCategory = category
                    refreshCategoryChipsSelection()
                    loadPhotos()
                }
            }

            binding.layoutCategoryChips.addView(tvChip)
        }
    }

    private fun refreshCategoryChipsSelection() {
        for (i in 0 until binding.layoutCategoryChips.childCount) {
            val child = binding.layoutCategoryChips.getChildAt(i) as? TextView ?: continue
            val isSelected = child.text.toString() == currentCategory
            updateChipStyle(child, isSelected)
        }
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected)
            chip.setTextColor("#2C1A00".toColorInt())
            chip.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_unselected)
            chip.setTextColor("#5E5246".toColorInt())
            chip.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun loadPhotos() {
        displayedPhotos = TetPhotoRepository.getImagesByCategory(currentCategory)
        adapter.updateData(displayedPhotos)
        binding.tvPhotoCountBadge.text = "${displayedPhotos.size} ảnh"
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener(hasAnim = true) {
            finish()
        }
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, TetPhotosActivity::class.java)
            context.startActivity(intent)
        }
    }
}

