package online.duolap.tetviet.audio

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import online.duolap.tetviet.R
import online.duolap.tetviet.analytics.AppCrashlytics
import online.duolap.tetviet.data.model.TetSong

object TetMusicManager {

    private const val PREFS_NAME = "tet_music_prefs"
    private const val KEY_MUSIC_ENABLED = "key_tet_bgm_enabled"
    private const val KEY_SELECTED_SONG_ID = "key_tet_selected_song_id"
    private const val TARGET_VOLUME = 0.70f

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isAppInBackground = false

    private val _currentSongFlow = MutableStateFlow(TetSongRepository.defaultSong)
    val currentSongFlow: StateFlow<TetSong> = _currentSongFlow.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Khởi tạo nhạc nền khi Application khởi động.
     */
    fun init(context: Context) {
        val prefs = getPrefs(context)
        val savedSongId = prefs.getString(KEY_SELECTED_SONG_ID, TetSongRepository.defaultSong.id) ?: TetSongRepository.defaultSong.id
        val song = TetSongRepository.getSongById(savedSongId)
        _currentSongFlow.value = song
    }

    /**
     * Kiểm tra trạng thái bật/tắt nhạc nền (mặc định là false - tắt).
     */
    fun isMusicEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MUSIC_ENABLED, false)
    }

    /**
     * Lấy bài hát hiện đang được chọn làm nhạc nền.
     */
    fun getCurrentSong(context: Context): TetSong {
        val savedSongId = getPrefs(context).getString(KEY_SELECTED_SONG_ID, TetSongRepository.defaultSong.id) ?: TetSongRepository.defaultSong.id
        return TetSongRepository.getSongById(savedSongId)
    }

    /**
     * Bật hoặc tắt nhạc nền.
     */
    fun setMusicEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_MUSIC_ENABLED, enabled) }
        if (enabled) {
            if (!isAppInBackground) {
                startCurrentSong(context)
            }
        } else {
            pauseWithFade()
        }
    }

    /**
     * Đảo ngược trạng thái bật/tắt nhạc nền.
     */
    fun toggleMusic(context: Context): Boolean {
        val newState = !isMusicEnabled(context)
        setMusicEnabled(context, newState)
        return newState
    }

    /**
     * Người dùng chọn một bài hát mới làm nhạc nền.
     * Lưu bài hát vào cài đặt và lập tức chuyển nhạc.
     */
    fun selectSong(context: Context, song: TetSong) {
        getPrefs(context).edit {
            putString(KEY_SELECTED_SONG_ID, song.id)
            putBoolean(KEY_MUSIC_ENABLED, true)
        }
        _currentSongFlow.value = song

        if (!isAppInBackground) {
            playSong(context, song)
        }
    }

    /**
     * Bắt đầu phát bài hát hiện tại đang chọn nếu được phép.
     */
    fun startCurrentSong(context: Context) {
        if (!isMusicEnabled(context) || isAppInBackground) return
        val song = getCurrentSong(context)
        _currentSongFlow.value = song
        playSong(context, song)
    }

    /**
     * Phát một bài hát cụ thể.
     */
    private fun playSong(context: Context, song: TetSong) {
        release()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }

            if (song.rawResId != null) {
                // Bài hát cục bộ (nhạc offline chất lượng cao)
                val afd = context.applicationContext.resources.openRawResourceFd(song.rawResId)
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                player.isLooping = true
                player.setVolume(TARGET_VOLUME, TARGET_VOLUME)
                player.prepare()
                isPrepared = true
                player.start()
                _isPlayingFlow.value = true
                mediaPlayer = player
                fadeIn()
            } else if (song.url.isNotBlank()) {
                player.setDataSource(song.url)
                player.setOnPreparedListener { mp ->
                    isPrepared = true
                    mp.isLooping = true
                    mp.start()
                    _isPlayingFlow.value = true
                    fadeIn()
                }
                player.setOnErrorListener { _, what, extra ->
                    AppCrashlytics.log("Lỗi phát nhạc online ($what, $extra), chuyển về nhạc mặc định")
                    fallbackToDefault(context)
                    true
                }
                player.prepareAsync()
                mediaPlayer = player
            } else {
                fallbackToDefault(context)
            }
        } catch (e: Exception) {
            AppCrashlytics.recordException(e, "TetMusicManager.playSong")
            fallbackToDefault(context)
        }
    }

    /**
     * Chuyển về bài mặc định nếu bài online gặp sự cố mạng.
     */
    private fun fallbackToDefault(context: Context) {
        try {
            val defaultSong = TetSongRepository.defaultSong
            _currentSongFlow.value = defaultSong
            if (defaultSong.rawResId != null) {
                release()
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    val afd = context.applicationContext.resources.openRawResourceFd(defaultSong.rawResId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    isLooping = true
                    setVolume(TARGET_VOLUME, TARGET_VOLUME)
                    prepare()
                    start()
                }
                isPrepared = true
                _isPlayingFlow.value = true
                mediaPlayer = player
                fadeIn()
            }
        } catch (ex: Exception) {
            AppCrashlytics.recordException(ex, "TetMusicManager.fallbackToDefault")
        }
    }

    /**
     * Gọi từ TetVietApplication khi app vào foreground (người dùng mở lại app).
     */
    fun onAppForeground(context: Context) {
        isAppInBackground = false
        if (isMusicEnabled(context)) {
            mediaPlayer?.let { player ->
                if (isPrepared && !player.isPlaying) {
                    player.start()
                    _isPlayingFlow.value = true
                    fadeIn()
                    return
                }
            }
            startCurrentSong(context)
        }
    }

    /**
     * Gọi từ TetVietApplication khi app vào background (người dùng chuyển app khác hoặc về màn hình chính).
     */
    fun onAppBackground() {
        isAppInBackground = true
        pauseWithFade()
    }

    /**
     * Tạm dừng có hiệu ứng giảm dần âm lượng (Fade out).
     */
    private fun pauseWithFade() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) {
            _isPlayingFlow.value = false
            return
        }

        val animator = ValueAnimator.ofFloat(TARGET_VOLUME, 0.0f).apply {
            duration = 300L
            addUpdateListener { va ->
                val v = va.animatedValue as Float
                try {
                    player.setVolume(v, v)
                } catch (_: Exception) {}
            }
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                try {
                    if (player.isPlaying) {
                        player.pause()
                    }
                    _isPlayingFlow.value = false
                    player.setVolume(TARGET_VOLUME, TARGET_VOLUME)
                } catch (_: Exception) {}
            }
        })
        animator.start()
    }

    /**
     * Hiệu ứng tăng dần âm lượng (Fade in).
     */
    private fun fadeIn() {
        val player = mediaPlayer ?: return
        try {
            player.setVolume(0.0f, 0.0f)
            val animator = ValueAnimator.ofFloat(0.0f, TARGET_VOLUME).apply {
                duration = 450L
                addUpdateListener { va ->
                    val v = va.animatedValue as Float
                    try {
                        player.setVolume(v, v)
                    } catch (_: Exception) {}
                }
            }
            animator.start()
        } catch (e: Exception) {
            player.setVolume(TARGET_VOLUME, TARGET_VOLUME)
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Giải phóng tài nguyên MediaPlayer.
     */
    fun release() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            AppCrashlytics.recordException(e, "TetMusicManager.release")
        } finally {
            mediaPlayer = null
            isPrepared = false
            _isPlayingFlow.value = false
        }
    }
}
