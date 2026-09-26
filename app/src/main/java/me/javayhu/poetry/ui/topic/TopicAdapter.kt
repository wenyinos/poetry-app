package me.javayhu.poetry.ui.topic

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.javayhu.poetry.data.TopicRepository
import me.javayhu.poetry.databinding.ItemTopicBinding

/** 专题列表适配器 */
class TopicAdapter(
    private val onClick: (TopicRepository.Topic) -> Unit,
) : RecyclerView.Adapter<TopicAdapter.Holder>() {

    private val items = ArrayList<TopicRepository.Topic>()

    fun submit(list: List<TopicRepository.Topic>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemTopicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(topic: TopicRepository.Topic) {
            binding.topicTitle.text = topic.title
            binding.topicHint.text = topic.hint
            binding.root.setOnClickListener { onClick(topic) }
        }
    }
}
