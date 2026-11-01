package online.duolap.tetviet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import online.duolap.tetviet.data.model.CardStickerItem
import online.duolap.tetviet.databinding.ItemStickerGridBinding
import online.duolap.tetviet.views.setSafeOnClickListener

class StickerGridAdapter(
    private var items: List<CardStickerItem>,
    private val isItemUnlocked: (String) -> Boolean = { false },
    private val onItemClick: (CardStickerItem) -> Unit
) : RecyclerView.Adapter<StickerGridAdapter.ViewHolder>() {

    fun updateItems(newItems: List<CardStickerItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStickerGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(
        private val binding: ItemStickerGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CardStickerItem) {
            val context = binding.root.context

            // Hỗ trợ cả URL và drawable resource
            if (item.url?.startsWith("http://") == true || item.url?.startsWith("https://") == true) {
                Glide.with(context)
                    .load(item.url)
                    .fitCenter()
                    .into(binding.ivStickerPreview)
            } else if (item.resId != 0) {
                binding.ivStickerPreview.setImageResource(item.resId)
            } else if (!item.url.isNullOrEmpty()) {
                val resId = context.resources.getIdentifier(item.url, "drawable", context.packageName)
                if (resId != 0) {
                    binding.ivStickerPreview.setImageResource(resId)
                } else {
                    Glide.with(context).load(item.url).into(binding.ivStickerPreview)
                }
            }

            val isUnlocked = !item.isVip || isItemUnlocked(item.id)
            binding.ivStickerPreview.alpha = if (isUnlocked) 1.0f else 0.5f
            binding.tvStickerName.text = if (item.isVip && !isUnlocked) "👑 ${item.name}" else item.name

            binding.layoutStickerItem.setSafeOnClickListener(hasAnim = true) {
                onItemClick(item)
            }
        }
    }
}
