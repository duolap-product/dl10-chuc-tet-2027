package online.duolap.tetviet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import online.duolap.tetviet.R
import online.duolap.tetviet.data.model.TetSong
import online.duolap.tetviet.databinding.ItemTetSongBinding
import online.duolap.tetviet.views.setSafeOnClickListener

class TetSongAdapter(
    private val songs: List<TetSong>,
    private var selectedSongId: String,
    private var isPlaying: Boolean,
    private val onSongSelected: (TetSong) -> Unit
) : RecyclerView.Adapter<TetSongAdapter.SongViewHolder>() {

    inner class SongViewHolder(val binding: ItemTetSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: TetSong) {
            val isSelected = song.id == selectedSongId

            binding.tvSongTitle.text = song.title
            binding.tvSongArtist.text = song.artist
            binding.tvSongSource.text = "Nguồn: ${song.source}"

            if (isSelected) {
                binding.layoutSongRoot.setBackgroundResource(R.drawable.bg_song_item_selected)
                binding.layoutSelectedBadge.visibility = View.VISIBLE
                binding.btnSelectSong.visibility = View.GONE
                binding.ivWaveIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE
            } else {
                binding.layoutSongRoot.setBackgroundResource(R.drawable.bg_song_item_normal)
                binding.layoutSelectedBadge.visibility = View.GONE
                binding.btnSelectSong.visibility = View.VISIBLE
                binding.ivWaveIndicator.visibility = View.GONE
            }

            binding.layoutSongRoot.setSafeOnClickListener(hasAnim = true) {
                if (song.id != selectedSongId) {
                    onSongSelected(song)
                }
            }

            binding.btnSelectSong.setSafeOnClickListener(hasAnim = true) {
                onSongSelected(song)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemTetSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size

    fun updateSelectedSong(newSelectedId: String, playing: Boolean) {
        if (selectedSongId != newSelectedId || isPlaying != playing) {
            selectedSongId = newSelectedId
            isPlaying = playing
            notifyDataSetChanged()
        }
    }
}

