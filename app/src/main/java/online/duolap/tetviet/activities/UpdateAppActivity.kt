package online.duolap.tetviet.activities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import online.duolap.tetviet.R
import online.duolap.tetviet.ads.AppOpenGate
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.databinding.ActivityUpdateAppBinding
import online.duolap.tetviet.update.PlayUpdateManager
import online.duolap.tetviet.views.setSafeOnClickListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UpdateAppActivity : AppCompatActivity() {

    @Inject
    lateinit var playUpdateManager: PlayUpdateManager

    private lateinit var binding: ActivityUpdateAppBinding

    private var isForceUpdate: Boolean = false
    private var newVersion: String = ""
    private var storeUrl: String? = null

    private var countdownTimer: CountDownTimer? = null
    private var hasRedirected: Boolean = false

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
        startCountdownTimer()
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

        binding.tvCurrentVersion.text = getString(R.string.update_current_ver).let {
            if (it.contains("%")) String.format(it, currentVersionName) else "Hiện tại: v$currentVersionName"
        }
        binding.tvNewVersion.text = getString(R.string.update_new_ver).let {
            if (it.contains("%")) String.format(it, newVersion) else "Mới: v$newVersion"
        }
    }

    private fun setupListeners() {
        // Nút Đóng (X)
        binding.btnClose.setSafeOnClickListener(hasAnim = true) {
            countdownTimer?.cancel()
            AppAnalytics.logFeatureClick("update_close_click")
            if (isForceUpdate) {
                finishAffinity()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        // Nút Cập nhật ngay
        binding.btnUpdateNow.setSafeOnClickListener(hasAnim = true) {
            countdownTimer?.cancel()
            hasRedirected = true
            AppAnalytics.logFeatureClick("update_now_click")
            openStoreForUpdate()
        }
    }

    private fun startCountdownTimer() {
        countdownTimer?.cancel()

        val totalDurationMs = 5000L
        val intervalMs = 1000L

        // Hiển thị ngay trạng thái ban đầu 5s
        updateCountdownUI(5)

        countdownTimer = object : CountDownTimer(totalDurationMs, intervalMs) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ((millisUntilFinished + 999) / 1000).toInt().coerceAtLeast(1)
                updateCountdownUI(secondsLeft)
            }

            override fun onFinish() {
                updateCountdownUI(0)
                if (!isFinishing && !isDestroyed && !hasRedirected) {
                    hasRedirected = true
                    AppAnalytics.logFeatureClick("update_auto_redirect")
                    openStoreForUpdate()
                }
            }
        }.start()
    }

    private fun updateCountdownUI(secondsLeft: Int) {
        if (secondsLeft > 0) {
            binding.btnUpdateNow.text = getString(R.string.update_btn_update_countdown, secondsLeft)
            binding.tvAutoRedirectNotice.text = getString(R.string.update_auto_redirect_notice, secondsLeft)
        } else {
            binding.btnUpdateNow.text = getString(R.string.update_btn_update)
            binding.tvAutoRedirectNotice.text = getString(R.string.update_auto_redirect_notice, 0)
        }
    }

    private fun openStoreForUpdate() {
        // Ưu tiên 1: Khởi chạy trực tiếp In-App Update của Google Play nếu khả dụng
        val appUpdateInfo = playUpdateManager.latestAppUpdateInfo
        if (appUpdateInfo != null && playUpdateManager.startUpdateFlow(this, appUpdateInfo)) {
            return
        }

        // Ưu tiên 2: Custom store URL nếu có
        val customUrl = storeUrl
        if (!customUrl.isNullOrBlank()) {
            val opened = runCatching {
                val intent = Intent(Intent.ACTION_VIEW, customUrl.toUri())
                // Mở trang cập nhật bên ngoài: không hiện App Open khi quay lại
                AppOpenGate.skipNextForeground()
                startActivity(intent)
                true
            }.getOrDefault(false)
            if (opened) return
        }

        // Ưu tiên 3: Mở trực tiếp trang Google Play Store
        val appPackage = packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$appPackage".toUri())
            marketIntent.addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
            // Mở Google Play để cập nhật: không hiện App Open khi quay lại
            AppOpenGate.skipNextForeground()
            startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$appPackage".toUri()
                )
                // Mở trang cập nhật bên ngoài: không hiện App Open khi quay lại
                AppOpenGate.skipNextForeground()
                startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.update_msg_store_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                countdownTimer?.cancel()
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

    override fun onDestroy() {
        countdownTimer?.cancel()
        countdownTimer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NEW_VERSION = "extra_new_version"
        const val EXTRA_IS_FORCE_UPDATE = "extra_is_force_update"
        const val EXTRA_STORE_URL = "extra_store_url"

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
