package online.duolap.tetviet.activities

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.duolap.tetviet.adapter.FullscreenImageAdapter
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.data.model.TetPhoto
import online.duolap.tetviet.data.model.TetPhotoRepository
import online.duolap.tetviet.databinding.ActivityPhotoViewerBinding
import online.duolap.tetviet.views.BlurHelper
import online.duolap.tetviet.views.TetPhotoPageTransformer
import online.duolap.tetviet.views.setSafeOnClickListener
import java.io.File
import java.io.FileOutputStream

/**
 * Màn hình xem ảnh Tết toàn màn hình với nền làm mờ (blurred background) động
 * và thẻ ảnh chính chiếm 90% kích thước màn hình kết hợp hiệu ứng 3D Carousel PageTransformer.
 */
class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private var photos: List<TetPhoto> = emptyList()
    private var currentIndex: Int = 0
    private var areControlsVisible: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdgeAndImmersive()

        AppAnalytics.logScreenView("PhotoViewerActivity")

        extractIntentData()
        setupViewPager()
        setupListeners()
    }

    private fun setupEdgeToEdgeAndImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootViewer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBarViewer.setPadding(
                binding.topBarViewer.paddingLeft,
                systemBars.top + 8,
                binding.topBarViewer.paddingRight,
                binding.topBarViewer.paddingBottom
            )
            binding.cardBottomInfo.translationY = -systemBars.bottom.toFloat()
            insets
        }

        hideSystemBars()
    }

    private fun hideSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !areControlsVisible) {
            hideSystemBars()
        }
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun extractIntentData() {
        val extraList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_PHOTOS, ArrayList::class.java) as? ArrayList<TetPhoto>
        } else {
            intent.getSerializableExtra(EXTRA_PHOTOS) as? ArrayList<TetPhoto>
        }

        photos = if (!extraList.isNullOrEmpty()) extraList else TetPhotoRepository.sampleImages
        currentIndex = intent.getIntExtra(EXTRA_INITIAL_INDEX, 0).coerceIn(0, maxOf(0, photos.size - 1))
    }

    private fun setupViewPager() {
        val adapter = FullscreenImageAdapter(photos) { _, _ ->
            toggleControlsVisibility()
        }

        binding.viewPagerPhotos.apply {
            this.adapter = adapter
            offscreenPageLimit = 2
            setPageTransformer(TetPhotoPageTransformer())
            setCurrentItem(currentIndex, false)

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentIndex = position
                    updateUIForCurrentPhoto()
                }
            })
        }

        updateUIForCurrentPhoto()
    }

    private fun updateUIForCurrentPhoto() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return

        val currentPhoto = photos[currentIndex]
        binding.tvCounter.text = "${currentIndex + 1} / ${photos.size}"
        binding.tvPhotoTitle.text = currentPhoto.title
        binding.tvPhotoCategory.text = currentPhoto.category
        binding.tvPhotoDescription.text = currentPhoto.description

        loadAndApplyBlurredBackground(currentPhoto)
    }

    private fun loadAndApplyBlurredBackground(photo: TetPhoto) {
        Glide.with(this)
            .asBitmap()
            .load(photo.imageUrl)
            .error(photo.localResId)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    applyBlurredBackground(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun applyBlurredBackground(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BlurHelper.applyBlurEffect(binding.ivBlurBackgroundFront, blurRadius = 32f)
            crossfadeBlurBackground(bitmap)
        } else {
            lifecycleScope.launch {
                val blurred = BlurHelper.blurBitmap(bitmap, radius = 18, scale = 0.2f)
                crossfadeBlurBackground(blurred)
            }
        }
    }

    private fun crossfadeBlurBackground(newBitmap: Bitmap) {
        binding.ivBlurBackgroundBack.setImageDrawable(binding.ivBlurBackgroundFront.drawable)
        binding.ivBlurBackgroundFront.alpha = 0f
        binding.ivBlurBackgroundFront.setImageBitmap(newBitmap)
        binding.ivBlurBackgroundFront.animate()
            .alpha(1f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener(hasAnim = true) {
            finish()
        }

        binding.btnDownloadPhoto.setSafeOnClickListener(hasAnim = true) {
            downloadCurrentPhoto()
        }

        binding.btnSharePhoto.setSafeOnClickListener(hasAnim = true) {
            shareCurrentPhoto()
        }
    }

    private fun toggleControlsVisibility() {
        areControlsVisible = !areControlsVisible
        val targetAlpha = if (areControlsVisible) 1f else 0f

        if (areControlsVisible) {
            showSystemBars()
        } else {
            hideSystemBars()
        }

        binding.topBarViewer.animate()
            .alpha(targetAlpha)
            .setDuration(250)
            .withEndAction {
                binding.topBarViewer.isVisible = areControlsVisible
            }
            .start()

        binding.cardBottomInfo.animate()
            .alpha(targetAlpha)
            .setDuration(250)
            .withEndAction {
                binding.cardBottomInfo.isVisible = areControlsVisible
            }
            .start()
    }

    private fun downloadCurrentPhoto() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        val currentPhoto = photos[currentIndex]

        Toast.makeText(this, getString(online.duolap.tetviet.R.string.photos_msg_downloading), Toast.LENGTH_SHORT).show()
        AppAnalytics.logFeatureClick("download_tet_photo")

        Glide.with(this)
            .asBitmap()
            .load(currentPhoto.imageUrl)
            .error(currentPhoto.localResId)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    lifecycleScope.launch {
                        val saved = savePhotoToPublicGallery(resource, "TetViet_${currentPhoto.id}")
                        if (saved) {
                            Toast.makeText(
                                this@PhotoViewerActivity,
                                getString(online.duolap.tetviet.R.string.photos_msg_download_success),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this@PhotoViewerActivity, getString(online.duolap.tetviet.R.string.photos_msg_download_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private suspend fun savePhotoToPublicGallery(bitmap: Bitmap, baseName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "${baseName}_${System.currentTimeMillis()}.jpg"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TetViet")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }

                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: return@withContext false

                    contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                    true
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val tetVietDir = File(picturesDir, "TetViet").apply { if (!exists()) mkdirs() }
                    val file = File(tetVietDir, fileName)

                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    MediaScannerConnection.scanFile(this@PhotoViewerActivity, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun shareCurrentPhoto() {
        if (photos.isEmpty() || currentIndex !in photos.indices) return
        val currentPhoto = photos[currentIndex]

        AppAnalytics.logFeatureClick("share_tet_photo")

        Glide.with(this)
            .asBitmap()
            .load(currentPhoto.imageUrl)
            .error(currentPhoto.localResId)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    lifecycleScope.launch {
                        val uri = withContext(Dispatchers.IO) {
                            try {
                                val cachePath = File(cacheDir, "shared_images").apply { if (!exists()) mkdirs() }
                                val file = File(cachePath, "share_tet_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(file).use { out ->
                                    resource.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                }
                                FileProvider.getUriForFile(this@PhotoViewerActivity, "$packageName.fileprovider", file)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "🧧 ${currentPhoto.title}\n${currentPhoto.description}\n\nChúc Mừng Năm Mới 2027 từ ứng dụng Tết Việt!"
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, getString(online.duolap.tetviet.R.string.action_share)))
                        } else {
                            Toast.makeText(this@PhotoViewerActivity, getString(online.duolap.tetviet.R.string.photos_msg_share_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    companion object {
        const val EXTRA_PHOTOS = "extra_photos"
        const val EXTRA_INITIAL_INDEX = "extra_initial_index"

        @JvmStatic
        fun start(context: Context, photos: List<TetPhoto>, initialIndex: Int = 0) {
            val intent = Intent(context, PhotoViewerActivity::class.java).apply {
                putExtra(EXTRA_PHOTOS, ArrayList(photos))
                putExtra(EXTRA_INITIAL_INDEX, initialIndex)
            }
            context.startActivity(intent)
        }
    }
}
