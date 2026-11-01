package online.duolap.tetviet.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import online.duolap.tetviet.data.model.TetPhoto
import online.duolap.tetviet.databinding.ItemFullscreenImageBinding

class FullscreenImageAdapter(
    private val images: List<TetPhoto>,
    private val onPhotoClick: (TetPhoto, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<FullscreenImageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFullscreenImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(images[position], position)
    }

    inner class ViewHolder(
        private val binding: ItemFullscreenImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TetPhoto, position: Int) {
            binding.progressBarLoading.isVisible = true

            Glide.with(binding.root.context)
                .load(item.imageUrl)
                .error(item.localResId)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.progressBarLoading.isVisible = false
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.progressBarLoading.isVisible = false
                        return false
                    }
                })
                .into(binding.ivPhoto)

            binding.cardPhotoContainer.setOnClickListener {
                onPhotoClick(item, position)
            }
        }
    }
}
