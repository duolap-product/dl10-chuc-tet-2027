package online.duolap.tetviet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import online.duolap.tetviet.data.model.TetPhoto
import online.duolap.tetviet.databinding.ItemTetPhotoBinding

class TetPhotoAdapter(
    private var items: List<TetPhoto>,
    private val onItemClick: (TetPhoto, Int) -> Unit
) : RecyclerView.Adapter<TetPhotoAdapter.ViewHolder>() {

    fun updateData(newItems: List<TetPhoto>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTetPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    inner class ViewHolder(
        private val binding: ItemTetPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TetPhoto, position: Int) {
            binding.tvTitle.text = item.title
            binding.tvCategoryTag.text = item.category

            Glide.with(binding.root.context)
                .load(item.imageUrl)
                .error(item.localResId)
                .transition(DrawableTransitionOptions.withCrossFade(250))
                .into(binding.ivThumbnail)

            binding.cardPhotoItem.setOnClickListener {
                onItemClick(item, position)
            }
        }
    }
}

