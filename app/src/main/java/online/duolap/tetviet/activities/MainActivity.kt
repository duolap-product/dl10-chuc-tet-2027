package online.duolap.tetviet.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import online.duolap.tetviet.MondayNotificationScheduler
import online.duolap.tetviet.R
import online.duolap.tetviet.adapter.CountdownPagerAdapter
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.audio.TetMusicManager
import online.duolap.tetviet.data.local.VipPreferences
import online.duolap.tetviet.data.model.MondayCountdownInfo
import online.duolap.tetviet.databinding.ActivityMainBinding
import online.duolap.tetviet.update.PlayUpdateManager
import online.duolap.tetviet.views.setSafeOnClickListener
import com.google.android.play.core.install.model.UpdateAvailability
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var vipPreferences: VipPreferences

    @Inject
    lateinit var playUpdateManager: PlayUpdateManager

    private lateinit var binding: ActivityMainBinding
    private lateinit var countdownAdapter: CountdownPagerAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    // Target: Mùng 1 Tết Nguyên Đán Đinh Mùi 2027 (06/02/2027 00:00:00 GMT+7)
    private val tet2027TargetMillis: Long by lazy {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"))
        cal.set(Calendar.YEAR, 2027)
        cal.set(Calendar.MONTH, Calendar.FEBRUARY)
        cal.set(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

//    private var selectedTabId = R.id.tabHome

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                MondayNotificationScheduler.scheduleNextMondayNotification(this)
                Toast.makeText(
                    this,
                    getString(R.string.msg_monday_notification_enabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupCountdownViewPager()
        startCountdownTimer()
        setupClickListeners()
        updateMusicButtonUI(TetMusicManager.isMusicEnabled(this))

        AppAnalytics.logScreenView("MainActivity")
        AppCrashlytics.log("MainActivity onCreate")

        handleNotificationIntent(intent)
        checkAndRequestNotificationPermission()
        checkGooglePlayUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val openMonday = intent?.getBooleanExtra(MondayNotificationScheduler.EXTRA_OPEN_MONDAY_PAGE, false) ?: false
        if (openMonday) {
            binding.vpCountdown.post {
                binding.vpCountdown.currentItem = 1
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        MondayNotificationScheduler.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                MondayNotificationScheduler.scheduleNextMondayNotification(this)
            } else {
                requestNotificationPermissionLauncher.launch(permission)
            }
        } else {
            MondayNotificationScheduler.scheduleNextMondayNotification(this)
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density

            // Top action buttons padding dưới status bar
            val topPaddingPx = systemBars.top + (8 * density).toInt()
            binding.layoutTopActions.setPadding(
                binding.layoutTopActions.paddingLeft,
                topPaddingPx,
                binding.layoutTopActions.paddingRight,
                binding.layoutTopActions.paddingBottom
            )

            binding.gridFeatures.setPadding(
                binding.gridFeatures.paddingLeft,
                binding.gridFeatures.paddingTop,
                binding.gridFeatures.paddingRight,
                systemBars.bottom + (92 * density).toInt()
            )
            insets
        }
    }

    private fun setupCountdownViewPager() {
        countdownAdapter = CountdownPagerAdapter()
        binding.vpCountdown.adapter = countdownAdapter
        binding.vpCountdown.offscreenPageLimit = 1

        binding.vpCountdown.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
            }
        })

        binding.dotPage0.setOnClickListener {
            binding.vpCountdown.currentItem = 0
        }
        binding.dotPage1.setOnClickListener {
            binding.vpCountdown.currentItem = 1
        }
    }

    private fun updateIndicators(position: Int) {
        val density = resources.displayMetrics.density
        val activeWidth = (16 * density).toInt()
        val inactiveWidth = (6 * density).toInt()

        if (position == 0) {
            binding.dotPage0.layoutParams = binding.dotPage0.layoutParams.apply { width = activeWidth }
            binding.dotPage0.setBackgroundResource(R.drawable.bg_indicator_active)

            binding.dotPage1.layoutParams = binding.dotPage1.layoutParams.apply { width = inactiveWidth }
            binding.dotPage1.setBackgroundResource(R.drawable.bg_indicator_inactive)
        } else {
            binding.dotPage0.layoutParams = binding.dotPage0.layoutParams.apply { width = inactiveWidth }
            binding.dotPage0.setBackgroundResource(R.drawable.bg_indicator_inactive)

            binding.dotPage1.layoutParams = binding.dotPage1.layoutParams.apply { width = activeWidth }
            binding.dotPage1.setBackgroundResource(R.drawable.bg_indicator_active)
        }
    }

    private fun startCountdownTimer() {
        countdownRunnable = object : Runnable {
            override fun run() {
                updateCountdownUI()
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun updateCountdownUI() {
        val now = System.currentTimeMillis()
        val diff = tet2027TargetMillis - now

        if (diff > 0) {
            val totalSeconds = diff / 1000L
            val days = totalSeconds / (24L * 3600L)
            val hours = (totalSeconds % (24L * 3600L)) / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L

            countdownAdapter.updateTimer(days, hours, minutes, seconds, false)
        } else {
            countdownAdapter.updateTimer(0, 0, 0, 0, true)
        }

        countdownAdapter.updateMonday(calculateMondayCountdown())
    }

    private fun calculateMondayCountdown(): MondayCountdownInfo {
        val tz = TimeZone.getTimeZone("GMT+7")
        val nowCal = Calendar.getInstance(tz)

        val targetCal = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, 2027)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (nowCal.timeInMillis >= targetCal.timeInMillis) {
            return MondayCountdownInfo(
                mondaysRemaining = 0,
                nextMondayText = "--/--",
                lastMondayText = "01/02",
                isTodayMonday = false,
                motivationalText = "Tết Đinh Mùi 2027 đã đến rồi! Chúc Mừng Năm Mới!"
            )
        }

        val isTodayMonday = nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY

        val cur = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, nowCal.get(Calendar.YEAR))
            set(Calendar.MONTH, nowCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, nowCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val mondayDates = mutableListOf<Calendar>()
        while (cur.before(targetCal)) {
            if (cur.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                mondayDates.add(cur.clone() as Calendar)
            }
            cur.add(Calendar.DAY_OF_YEAR, 1)
        }

        val mondaysRemaining = mondayDates.size
        val nextMondayText = if (mondayDates.isNotEmpty()) {
            val next = mondayDates.first()
            String.format(
                Locale.getDefault(),
                "%02d/%02d",
                next.get(Calendar.DAY_OF_MONTH),
                next.get(Calendar.MONTH) + 1
            )
        } else {
            "--/--"
        }

        val lastMondayText = if (mondayDates.isNotEmpty()) {
            val last = mondayDates.last()
            String.format(
                Locale.getDefault(),
                "%02d/%02d",
                last.get(Calendar.DAY_OF_MONTH),
                last.get(Calendar.MONTH) + 1
            )
        } else {
            "01/02"
        }

        val motivationalText = when {
            mondaysRemaining == 0 -> "Tuần này là Tết rồi! Về nhà sum vầy thôi! 🧧"
            isTodayMonday && mondaysRemaining == 1 -> "Hôm nay là Thứ Hai cuối cùng trước Tết! 🌸"
            isTodayMonday -> "Hôm nay là Thứ Hai! Còn $mondaysRemaining đầu tuần nữa là đón Tết! 🌸"
            mondaysRemaining == 1 -> "Chỉ còn 1 Thứ Hai nữa thôi là đón Tết sum vầy! 🧧"
            else -> "Chỉ còn $mondaysRemaining lần đầu tuần nữa là đón Tết sum vầy! 🌸"
        }

        return MondayCountdownInfo(
            mondaysRemaining = mondaysRemaining,
            nextMondayText = nextMondayText,
            lastMondayText = lastMondayText,
            isTodayMonday = isTodayMonday,
            motivationalText = motivationalText
        )
    }

    private fun setupClickListeners() {
        // Top action icons
        binding.btnVip.setSafeOnClickListener(hasAnim = true) {
            AppAnalytics.logFeatureClick("open_vip_from_main")
            val intent = Intent(this, VipActivity::class.java)
            startActivity(intent)
        }

        binding.btnMusicToggle.setSafeOnClickListener(hasAnim = true) {
            AppAnalytics.logFeatureClick("open_music_selection")
            MusicSelectionActivity.start(this)
        }

        binding.btnMusicToggle.setOnLongClickListener {
            val newState = TetMusicManager.toggleMusic(this)
            updateMusicButtonUI(newState)
            val msg = if (newState) getString(R.string.msg_music_on) else getString(R.string.msg_music_off)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            AppAnalytics.logFeatureClick(if (newState) "quick_enable_bgm" else "quick_disable_bgm")
            true
        }

        binding.btnSettings.setSafeOnClickListener(hasAnim = true) {
            showSettingsDialog()
        }

        // 4 Main Feature Cards
        binding.cardHoroscope.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("tu_vi")
            showTuViDialog()
        }

        binding.cardWishes.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("loi_chuc")
            showLoiChucDialog()
        }

        binding.cardCustoms.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("phong_tuc")
            val intent = Intent(this, TetCustomsActivity::class.java)
            startActivity(intent)
        }

        binding.cardCardMaker.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("tao_thiep")
            val intent = Intent(this, CardMakerActivity::class.java)
            startActivity(intent)
        }

        binding.cardLuckyWheel.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("li_xi_may_man")
            val intent = Intent(this, LuckyWheelActivity::class.java)
            startActivity(intent)
        }

        binding.cardTetPhotos.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("hinh_anh_tet")
            TetPhotosActivity.start(this)
        }

        binding.cardOption2.setSafeOnClickListener {
            AppAnalytics.logFeatureClick("bau_cua")
            BauCuaActivity.start(this)
        }

        binding.cardOption3.setSafeOnClickListener {
            Toast.makeText(this, getString(R.string.msg_feature_coming_soon), Toast.LENGTH_SHORT).show()
        }
    }

    // Dialog 1: Tử Vi & Tử Khởi
    @SuppressLint("InflateParams")
    private fun showTuViDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_horoscope, null)
        dialog.setContentView(view)

        view.findViewById<ImageView>(R.id.btnCloseHoroscope).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // Dialog 2: Lời Chúc Ý Nghĩa
    @SuppressLint("InflateParams")
    private fun showLoiChucDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_wishes, null)
        dialog.setContentView(view)

        view.findViewById<ImageView>(R.id.btnCloseWishes).setOnClickListener {
            dialog.dismiss()
        }

        val tvWish1 = view.findViewById<TextView>(R.id.tvWishContent1)
        val tvWish2 = view.findViewById<TextView>(R.id.tvWishContent2)

        view.findViewById<MaterialButton>(R.id.btnCopyWish1).setOnClickListener {
            copyTextToClipboard(getString(R.string.wishes_share_subject), tvWish1.text.toString())
        }

        view.findViewById<MaterialButton>(R.id.btnShareWish1).setOnClickListener {
            shareText(tvWish1.text.toString())
        }

        view.findViewById<MaterialButton>(R.id.btnCopyWish2).setOnClickListener {
            copyTextToClipboard(getString(R.string.wishes_share_subject), tvWish2.text.toString())
        }

        view.findViewById<MaterialButton>(R.id.btnShareWish2).setOnClickListener {
            shareText(tvWish2.text.toString())
        }

        dialog.show()
    }

    private fun copyTextToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.msg_wish_copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.wishes_share_subject))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.wishes_share_chooser)))
    }

    // Dialog 3: Cài Đặt Ứng Dụng
    @SuppressLint("InflateParams")
    private fun showSettingsDialog() {
        AppAnalytics.logFeatureClick("open_settings")
        val dialog = BottomSheetDialog(this, R.style.Theme_TetViet_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        dialog.setContentView(view)

        val switchSound = view.findViewById<SwitchMaterial>(R.id.switchSound)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseSettings)

        // Đồng bộ trạng thái nhạc nền Tết
        val isSoundOn = TetMusicManager.isMusicEnabled(this)
        switchSound.isChecked = isSoundOn
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            TetMusicManager.setMusicEnabled(this, isChecked)
            updateMusicButtonUI(isChecked)
            AppAnalytics.logFeatureClick(if (isChecked) "enable_bgm_settings" else "disable_bgm_settings")
        }

        val tvCurrentSong = view.findViewById<TextView>(R.id.tvCurrentSongSetting)
        tvCurrentSong?.text = TetMusicManager.getCurrentSong(this).title

        view.findViewById<android.view.View>(R.id.layoutChangeSongSetting)?.setOnClickListener {
            dialog.dismiss()
            MusicSelectionActivity.start(this)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<android.view.View>(R.id.layoutVipSetting)?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, VipActivity::class.java)
            startActivity(intent)
        }

        // Kiểm tra phiên bản mới thủ công khi bấm vào thông tin phiên bản
        view.findViewById<android.view.View>(R.id.layoutAppVersion)?.setOnClickListener {
            Toast.makeText(this, getString(R.string.update_checking_toast), Toast.LENGTH_SHORT).show()
            playUpdateManager.checkForUpdate(
                forceCheck = true,
                onUpdateAvailable = { appUpdateInfo ->
                    dialog.dismiss()
                    val newVersionCode = appUpdateInfo.availableVersionCode()
                    UpdateAppActivity.start(
                        context = this,
                        newVersion = "v$newVersionCode",
                        isForceUpdate = appUpdateInfo.updatePriority() >= 4
                    )
                },
                onNoUpdate = {
                    Toast.makeText(this, getString(R.string.update_msg_already_latest), Toast.LENGTH_SHORT).show()
                },
                onError = {
                    Toast.makeText(this, getString(R.string.update_msg_already_latest), Toast.LENGTH_SHORT).show()
                }
            )
        }

        dialog.show()
    }

    private fun updateMusicButtonUI(isMusicOn: Boolean) {
        if (isMusicOn) {
            binding.btnMusicToggle.setImageResource(R.drawable.ic_music_note)
            binding.btnMusicToggle.imageTintList = ColorStateList.valueOf("#FFE8A3".toColorInt())
            binding.btnMusicToggle.contentDescription = getString(R.string.content_desc_music_turn_off)
        } else {
            binding.btnMusicToggle.setImageResource(R.drawable.ic_music_off)
            binding.btnMusicToggle.imageTintList = ColorStateList.valueOf("#A38F7E".toColorInt())
            binding.btnMusicToggle.contentDescription = getString(R.string.content_desc_music_turn_on)
        }
    }

    private fun updateVipBadgeUI() {
        if (vipPreferences.isVip.value) {
            binding.tvVipBadgeMain.text = "VIP 👑"
            binding.btnVip.setBackgroundResource(R.drawable.bg_vip_card_gold)
        } else {
            binding.tvVipBadgeMain.text = "VIP"
            binding.btnVip.setBackgroundResource(R.drawable.bg_vip_card_gold)
        }
    }

    override fun onResume() {
        super.onResume()
        updateMusicButtonUI(TetMusicManager.isMusicEnabled(this))
        updateVipBadgeUI()
        checkPendingGooglePlayUpdate()
    }

    private fun checkGooglePlayUpdate() {
        playUpdateManager.checkForUpdate(
            forceCheck = false,
            onUpdateAvailable = { appUpdateInfo ->
                val newVersionCode = appUpdateInfo.availableVersionCode()
                UpdateAppActivity.start(
                    context = this,
                    newVersion = "v$newVersionCode",
                    isForceUpdate = appUpdateInfo.updatePriority() >= 4
                )
            }
        )
    }

    private fun checkPendingGooglePlayUpdate() {
        playUpdateManager.appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                playUpdateManager.startUpdateFlow(this, appUpdateInfo)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }
}