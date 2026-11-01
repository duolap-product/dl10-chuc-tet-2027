package online.duolap.tetviet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import online.duolap.tetviet.R
import online.duolap.tetviet.data.model.CardBackground
import online.duolap.tetviet.databinding.ItemCardBackgroundBinding

class BackgroundGridAdapter(
    private var items: List<CardBackground>,
    private var selectedId: String,
    private val isItemUnlocked: (String) -> Boolean = { false },
    private val onItemClick: (CardBackground) -> Unit
) : RecyclerView.Adapter<BackgroundGridAdapter.ViewHolder>() {

    fun updateItems(newItems: List<CardBackground>, newSelectedId: String = selectedId) {
        this.items = newItems
        this.selectedId = newSelectedId
        notifyDataSetChanged()
    }

    fun setSelectedId(newSelectedId: String) {
        this.selectedId = newSelectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCardBackgroundBinding.inflate(
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
        private val binding: ItemCardBackgroundBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CardBackground) {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            // Hỗ trợ cả URL từ Firestore lẫn Resource Drawable cục bộ
            if (item.url?.startsWith("http://") == true || item.url?.startsWith("https://") == true) {
                Glide.with(context)
                    .load(item.url)
                    .placeholder(R.drawable.bg_card_free_01)
                    .centerCrop()
                    .into(binding.ivThumbnail)
            } else if (item.resId != 0) {
                binding.ivThumbnail.setImageResource(item.resId)
            } else if (!item.url.isNullOrEmpty()) {
                val resId = context.resources.getIdentifier(item.url, "drawable", context.packageName)
                if (resId != 0) {
                    binding.ivThumbnail.setImageResource(resId)
                } else {
                    Glide.with(context).load(item.url).into(binding.ivThumbnail)
                }
            }

            val isUnlocked = item.isFree || isItemUnlocked(item.id)
            binding.tvBadge.isVisible = item.isFree
            binding.ivVIP.isVisible = !isUnlocked && !item.isAds
            binding.layoutAds.isVisible = !isUnlocked && item.isAds

            binding.ivThumbnail.alpha = if (isUnlocked) 1.0F else 0.5F

            val isSelected = (item.id == selectedId)
            binding.cardBgItem.strokeColor = if (isSelected) "#7A090D".toColorInt() else "#E8DAC5".toColorInt()
            binding.cardBgItem.strokeWidth = if (isSelected) (2.5f * density).toInt() else (1.5f * density).toInt()

            binding.cardBgItem.setOnClickListener {
                selectedId = item.id
                notifyDataSetChanged()
                onItemClick(item)
            }
        }
    }
}
