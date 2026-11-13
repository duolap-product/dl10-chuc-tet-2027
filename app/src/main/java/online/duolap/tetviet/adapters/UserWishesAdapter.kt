package online.duolap.tetviet.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import online.duolap.tetviet.R
import online.duolap.tetviet.data.local.WishEntity
import online.duolap.tetviet.data.model.WishCategory
import online.duolap.tetviet.databinding.ItemUserWishBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserWishesAdapter(
    private val onItemClick: (WishEntity) -> Unit
) : RecyclerView.Adapter<UserWishesAdapter.WishViewHolder>() {

    private val items = mutableListOf<WishEntity>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<WishEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WishViewHolder {
        val binding = ItemUserWishBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WishViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WishViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class WishViewHolder(
        private val binding: ItemUserWishBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WishEntity) {
            val context: Context = binding.root.context
            val category = WishCategory.fromName(item.category)

            binding.ivItemCategoryIcon.setImageResource(category.iconRes)
            binding.tvItemCategoryTitle.setText(category.titleRes)
            binding.tvItemDate.text = dateFormat.format(Date(item.createdAt))
            binding.tvItemContent.text = item.content
            binding.tvItemBlessings.text = context.getString(
                R.string.wishing_tree_blessings_count,
                item.blessingCount
            )

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

