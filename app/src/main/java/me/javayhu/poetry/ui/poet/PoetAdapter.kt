package me.javayhu.poetry.ui.poet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.javayhu.poetry.R
import me.javayhu.poetry.data.PoetBrief
import me.javayhu.poetry.databinding.ItemPoetBinding

/**
 * 诗人列表适配器。
 *
 * 作品数由调用方通过 [PoetRepository.worksCount] 一次算好传入，
 * 避免在 bind 里反复扫描索引。
 */
class PoetAdapter(
    private val worksCount: Map<String, Int>,
    private val onClick: (PoetBrief) -> Unit,
) : RecyclerView.Adapter<PoetAdapter.Holder>() {

    private val items = ArrayList<PoetBrief>()

    fun submit(list: List<PoetBrief>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPoetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemPoetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(poet: PoetBrief) {
            binding.poetName.text = poet.name
            val works = binding.root.context.getString(
                R.string.poet_works, worksCount[poet.name] ?: 0
            )
            binding.poetMeta.text =
                if (poet.dynasty.isEmpty()) works else "${poet.dynasty} · $works"
            binding.root.setOnClickListener { onClick(poet) }
        }
    }
}
