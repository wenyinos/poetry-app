package me.javayhu.poetry.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.databinding.ItemPoemBinding

/** 诗词列表适配器；数据量小（首页固定条数），用 notifyDataSetChanged 即可 */
class PoemAdapter(
    private val onClick: (PoemBrief) -> Unit,
) : RecyclerView.Adapter<PoemAdapter.Holder>() {

    private val items = ArrayList<PoemBrief>()

    fun submit(list: List<PoemBrief>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPoemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemPoemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(poem: PoemBrief) {
            binding.poemName.text = poem.name
            binding.poemMeta.text = listOf(poem.dynasty, poem.author)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            binding.root.setOnClickListener { onClick(poem) }
        }
    }
}
