package online.duolap.tetviet.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import online.duolap.tetviet.data.TetCustomItem
import online.duolap.tetviet.databinding.ItemTetCustomBinding

class TetCustomsAdapter(
    private val onItemClick: (TetCustomItem) -> Unit
) : ListAdapter<TetCustomItem, TetCustomsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTetCustomBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTetCustomBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TetCustomItem) {
            binding.tvCustomTitle.text = item.title
            binding.tvCustomDescription.text = item.description
            binding.tvCustomCategory.text = item.category.displayName
            binding.ivCustomIcon.setImageResource(item.iconResId)

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TetCustomItem>() {
            override fun areItemsTheSame(oldItem: TetCustomItem, newItem: TetCustomItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: TetCustomItem, newItem: TetCustomItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}

