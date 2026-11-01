package online.duolap.tetviet.activities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import online.duolap.tetviet.R
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.databinding.ActivityUpdateAppBinding
import online.duolap.tetviet.views.setSafeOnClickListener

/**
 * Màn hình thông báo cập nhật phiên bản mới cho ứng dụng Tết Việt.
 * Gồm 2 lựa chọn chính:
 * 1. "Bỏ qua": Đóng màn hình để tiếp tục trải nghiệm app (hoặc thoát app nếu bắt buộc cập nhật).
 * 2. "Cập nhật ngay": Mở Google Play Store để tải phiên bản mới nhất.
 */
class UpdateAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateAppBinding

    private var isForceUpdate: Boolean = false
    private var newVersion: String = "2027.2.0"
    private var storeUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUpdateAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootUpdateApp) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.layoutBottomActions.setPadding(
                binding.layoutBottomActions.paddingLeft,
                binding.layoutBottomActions.paddingTop,
                binding.layoutBottomActions.paddingRight,
                systemBars.bottom + 12
            )
            insets
        }

        AppAnalytics.logScreenView("UpdateAppActivity")

        extractIntentData()
        setupUI()
        setupListeners()
        setupBackPressHandling()
        animateEntrance()
    }

    private fun extractIntentData() {
        newVersion = intent.getStringExtra(EXTRA_NEW_VERSION) ?: "2027.2.0"
        isForceUpdate = intent.getBooleanExtra(EXTRA_IS_FORCE_UPDATE, false)
        storeUrl = intent.getStringExtra(EXTRA_STORE_URL)
    }

    private fun setupUI() {
        val currentVersionName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
        } catch (_: Exception) {
            "1.0.0"
        }

        binding.tvCurrentVersion.text = "Hiện tại: v$currentVersionName"
        binding.tvNewVersion.text = "Mới: v$newVersion"

        if (isForceUpdate) {
            binding.tvSubtitle.text = "Đây là bản cập nhật quan trọng để đảm bảo ứng dụng Tết Việt hoạt động ổn định và chính xác. Vui lòng cập nhật để tiếp tục sử dụng."
            binding.btnSkip.text = "Thoát"
        }
    }

    private fun setupListeners() {
        // Option 1: Bỏ qua (hoặc Thoát nếu là bản cập nhật bắt buộc)
        binding.btnSkip.setSafeOnClickListener(hasAnim = true) {
            AppAnalytics.logFeatureClick("update_skip_click")
            if (isForceUpdate) {
                finishAffinity()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        // Option 2: Cập nhật ngay
        binding.btnUpdateNow.setSafeOnClickListener(hasAnim = true) {
            AppAnalytics.logFeatureClick("update_now_click")
            openStoreForUpdate()
        }
    }

    private fun openStoreForUpdate() {
        val customUrl = storeUrl
        if (!customUrl.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(customUrl))
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Fallback xuống Play Store mặc định
            }
        }

        val appPackage = packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackage"))
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackage"))
                startActivity(webIntent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.update_msg_store_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isForceUpdate) {
                    finishAffinity()
                } else {
                    finish()
                }
            }
        })
    }

    private fun animateEntrance() {
        binding.cardBanner.alpha = 0f
        binding.cardBanner.scaleX = 0.94f
        binding.cardBanner.scaleY = 0.94f
        binding.cardBanner.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    companion object {
        const val EXTRA_NEW_VERSION = "extra_new_version"
        const val EXTRA_IS_FORCE_UPDATE = "extra_is_force_update"
        const val EXTRA_STORE_URL = "extra_store_url"

        /**
         * Tiện ích mở màn hình cập nhật từ bất kỳ đâu (MainActivity, Splash, RemoteConfig, Notification,...)
         */
        @JvmStatic
        fun start(
            context: Context,
            newVersion: String = "2027.2.0",
            isForceUpdate: Boolean = false,
            storeUrl: String? = null
        ) {
            val intent = Intent(context, UpdateAppActivity::class.java).apply {
                putExtra(EXTRA_NEW_VERSION, newVersion)
                putExtra(EXTRA_IS_FORCE_UPDATE, isForceUpdate)
                putExtra(EXTRA_STORE_URL, storeUrl)
            }
            context.startActivity(intent)
        }
    }
}
