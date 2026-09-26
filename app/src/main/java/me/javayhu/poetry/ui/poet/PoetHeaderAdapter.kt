package me.javayhu.poetry.ui.poet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.javayhu.poetry.databinding.ItemPoetHeaderBinding

/**
 * 诗人详情的头部，作为列表的第 0 个条目与作品列表拼在一起，
 * 这样整体共用一次滚动，不必嵌套滚动容器。
 */
class PoetHeaderAdapter : RecyclerView.Adapter<PoetHeaderAdapter.Holder>() {

    /** 头部要显示的文本；为空时返回 0 个条目 */
    data class Header(
        val name: String,
        val meta: String,
        val desc: String,
        val content: String,
        val worksTitle: String,
    )

    private var header: Header? = null

    fun set(header: Header?) {
        this.header = header
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (header == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPoetHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        header?.let { holder.bind(it) }
    }

    class Holder(private val binding: ItemPoetHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(h: Header) {
            binding.poetName.text = h.name
            binding.poetMeta.text = h.meta
            binding.poetDesc.text = h.desc
            binding.poetDesc.visibility = if (h.desc.isBlank()) android.view.View.GONE
            else android.view.View.VISIBLE
            binding.poetContent.text = h.content
            binding.poetContent.visibility = if (h.content.isBlank()) android.view.View.GONE
            else android.view.View.VISIBLE
            binding.worksTitle.text = h.worksTitle
        }
    }
}
