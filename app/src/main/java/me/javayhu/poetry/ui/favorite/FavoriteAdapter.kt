package me.javayhu.poetry.ui.favorite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.javayhu.poetry.data.Favorite
import me.javayhu.poetry.databinding.ItemFavoriteBinding

/** 收藏列表适配器；点条目进详情，点星标取消收藏 */
class FavoriteAdapter(
    private val onClick: (Favorite) -> Unit,
    private val onRemove: (Favorite) -> Unit,
) : RecyclerView.Adapter<FavoriteAdapter.Holder>() {

    private val items = ArrayList<Favorite>()

    fun submit(list: List<Favorite>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemFavoriteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Favorite) {
            binding.favName.text = item.name
            binding.favMeta.text = listOf(item.dynasty, item.author)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            binding.root.setOnClickListener { onClick(item) }
            binding.favRemove.setOnClickListener { onRemove(item) }
        }
    }
}
