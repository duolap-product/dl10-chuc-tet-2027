package online.duolap.tetviet.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import online.duolap.tetviet.R
import online.duolap.tetviet.adapter.TetSongAdapter
import online.duolap.tetviet.analytics.AppAnalytics
import online.duolap.tetviet.audio.TetMusicManager
import online.duolap.tetviet.audio.TetSongRepository
import online.duolap.tetviet.data.model.TetSong
import online.duolap.tetviet.databinding.ActivityMusicSelectionBinding
import online.duolap.tetviet.views.setSafeOnClickListener

@AndroidEntryPoint
class MusicSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMusicSelectionBinding
    private lateinit var songAdapter: TetSongAdapter
    private var discRotationAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMusicSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMusicSelection) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        AppAnalytics.logScreenView("MusicSelectionActivity")

        setupListeners()
        setupDiscAnimation()
        setupSongList()
        observeMusicState()
    }

    private fun setupListeners() {
        binding.btnBack.setSafeOnClickListener(hasAnim = true) {
            finish()
        }

        val isMusicEnabled = TetMusicManager.isMusicEnabled(this)
        binding.switchMasterMusic.isChecked = isMusicEnabled
        updateCardsAlpha(isMusicEnabled)

        binding.switchMasterMusic.setOnCheckedChangeListener { _, isChecked ->
            TetMusicManager.setMusicEnabled(this, isChecked)
            updateCardsAlpha(isChecked)
            val msg = if (isChecked) getString(R.string.msg_music_on) else getString(R.string.msg_music_off)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            AppAnalytics.logFeatureClick(if (isChecked) "enable_music_screen" else "disable_music_screen")
        }

        binding.ivPlayPauseQuick.setSafeOnClickListener(hasAnim = true) {
            val newState = TetMusicManager.toggleMusic(this)
            binding.switchMasterMusic.isChecked = newState
            updateCardsAlpha(newState)
            val msg = if (newState) getString(R.string.msg_music_on) else getString(R.string.msg_music_off)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCardsAlpha(isMusicOn: Boolean) {
        val targetAlpha = if (isMusicOn) 1.0f else 0.55f
        binding.cardNowPlaying.alpha = targetAlpha
    }

    private fun setupDiscAnimation() {
        discRotationAnimator = ObjectAnimator.ofFloat(binding.ivNowPlayingDisc, "rotation", 0f, 360f).apply {
            duration = 8000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun setupSongList() {
        val allSongs = TetSongRepository.getAllSongs()
        val currentSong = TetMusicManager.getCurrentSong(this)
        val isPlaying = TetMusicManager.isPlaying()

        binding.tvSongListHeader.text = "${getString(R.string.music_song_list_header)} (${allSongs.size})"

        songAdapter = TetSongAdapter(
            songs = allSongs,
            selectedSongId = currentSong.id,
            isPlaying = isPlaying,
            onSongSelected = { song ->
                onUserSelectedSong(song)
            }
        )

        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MusicSelectionActivity)
            adapter = songAdapter
        }
    }

    private fun onUserSelectedSong(song: TetSong) {
        TetMusicManager.selectSong(this, song)
        binding.switchMasterMusic.isChecked = true
        updateCardsAlpha(true)
        Toast.makeText(this, "Đã chọn: ${song.title} 🎶", Toast.LENGTH_SHORT).show()
        AppAnalytics.logFeatureClick("select_song_${song.id}")
    }

    private fun observeMusicState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    TetMusicManager.currentSongFlow.collect { song ->
                        updateNowPlayingUI(song, TetMusicManager.isPlaying())
                    }
                }
                launch {
                    TetMusicManager.isPlayingFlow.collect { isPlaying ->
                        val song = TetMusicManager.currentSongFlow.value
                        updateNowPlayingUI(song, isPlaying)
                    }
                }
            }
        }
    }

    private fun updateNowPlayingUI(song: TetSong, isPlaying: Boolean) {
        binding.tvNowPlayingTitle.text = song.title
        binding.tvNowPlayingArtist.text = "${song.artist} • ${song.source}"

        if (isPlaying && TetMusicManager.isMusicEnabled(this)) {
            binding.ivPlayPauseQuick.setImageResource(R.drawable.ic_sound_wave)
            if (discRotationAnimator?.isStarted != true) {
                discRotationAnimator?.start()
            } else {
                discRotationAnimator?.resume()
            }
        } else {
            binding.ivPlayPauseQuick.setImageResource(R.drawable.ic_music_off)
            discRotationAnimator?.pause()
        }

        songAdapter.updateSelectedSong(song.id, isPlaying && TetMusicManager.isMusicEnabled(this))
    }

    override fun onDestroy() {
        discRotationAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, MusicSelectionActivity::class.java))
        }
    }
}
