package me.javayhu.poetry.ui.mingju

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.javayhu.poetry.data.Mingju
import me.javayhu.poetry.databinding.ItemMingjuBinding

/** 名句列表适配器 */
class MingjuAdapter(
    private val onClick: (Mingju) -> Unit,
) : RecyclerView.Adapter<MingjuAdapter.Holder>() {

    private val items = ArrayList<Mingju>()

    fun submit(list: List<Mingju>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemMingjuBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemMingjuBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Mingju) {
            binding.mingjuContent.text = item.content
            binding.mingjuSource.text = if (item.poetName.isEmpty()) {
                item.poetryName
            } else {
                "${item.poetryName} · ${item.poetName}"
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
