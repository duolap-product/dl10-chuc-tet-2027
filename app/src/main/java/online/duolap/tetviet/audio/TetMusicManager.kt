package online.duolap.tetviet.audio

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import online.duolap.tetviet.R
import online.duolap.tetviet.analytics.AppCrashlytics
import androidx.core.content.edit

/**
 * Quản lý phát nhạc nền Tết vui tươi cho ứng dụng Tết Việt.
 * Hỗ trợ:
 * - Lưu trạng thái Bật/Tắt vào SharedPreferences
 * - Hiệu ứng Fade In / Fade Out mượt mà
 * - Tự động lặp lại liên tục (seamless loop)
 * - Tạm dừng khi Activity ẩn và tiếp tục khi Activity hiển thị lại
 */
object TetMusicManager {

    private const val PREFS_NAME = "tet_music_prefs"
    private const val KEY_MUSIC_ENABLED = "key_tet_bgm_enabled"
    private const val TARGET_VOLUME = 0.70f

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isFading = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Kiểm tra xem người dùng có đang bật nhạc nền Tết hay không.
     * Mặc định là bật (true).
     */
    fun isMusicEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MUSIC_ENABLED, true)
    }

    /**
     * Bật hoặc tắt nhạc nền và lưu vào cài đặt người dùng.
     */
    fun setMusicEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_MUSIC_ENABLED, enabled) }
        if (enabled) {
            start(context)
        } else {
            pauseWithFade()
        }
    }

    /**
     * Đảo ngược trạng thái bật/tắt nhạc nền.
     * @return Trạng thái mới sau khi đảo (true = đang bật, false = đã tắt)
     */
    fun toggleMusic(context: Context): Boolean {
        val newState = !isMusicEnabled(context)
        setMusicEnabled(context, newState)
        return newState
    }

    /**
     * Khởi động phát nhạc nếu người dùng cho phép và chưa đang phát.
     */
    fun start(context: Context) {
        if (!isMusicEnabled(context)) return

        try {
            if (mediaPlayer == null) {
                initMediaPlayer(context)
            }

            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    fadeIn()
                }
            }
        } catch (e: Exception) {
            AppCrashlytics.recordException(e, "TetMusicManager.start")
        }
    }

    /**
     * Tạm dừng phát nhạc khi Activity chuyển vào background.
     */
    fun pause() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
            }
        } catch (e: Exception) {
            AppCrashlytics.recordException(e, "TetMusicManager.pause")
        }
    }

    /**
     * Tiếp tục phát nhạc khi Activity quay trở lại foreground (nếu người dùng bật).
     */
    fun resume(context: Context) {
        if (isMusicEnabled(context)) {
            start(context)
        }
    }

    /**
     * Tạm dừng có hiệu ứng giảm dần âm lượng (Fade out)
     */
    private fun pauseWithFade() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) return

        val animator = ValueAnimator.ofFloat(TARGET_VOLUME, 0.0f).apply {
            duration = 350L
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
                    player.setVolume(TARGET_VOLUME, TARGET_VOLUME)
                } catch (_: Exception) {}
            }
        })
        animator.start()
    }

    /**
     * Hiệu ứng tăng dần âm lượng (Fade in)
     */
    private fun fadeIn() {
        val player = mediaPlayer ?: return
        try {
            player.setVolume(0.0f, 0.0f)
            val animator = ValueAnimator.ofFloat(0.0f, TARGET_VOLUME).apply {
                duration = 500L
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

    private fun initMediaPlayer(context: Context) {
        release()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                val afd = context.applicationContext.resources.openRawResourceFd(R.raw.bgm_tet_celebration)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                isLooping = true
                setVolume(TARGET_VOLUME, TARGET_VOLUME)
                prepare()
                isPrepared = true
            }
        } catch (e: Exception) {
            AppCrashlytics.recordException(e, "TetMusicManager.initMediaPlayer")
            release()
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
     * Giải phóng tài nguyên MediaPlayer khi không dùng nữa
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
        }
    }
}

