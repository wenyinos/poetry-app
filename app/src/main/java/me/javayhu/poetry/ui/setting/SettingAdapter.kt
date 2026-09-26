package me.javayhu.poetry.ui.setting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.javayhu.poetry.databinding.ItemSettingBinding

/** 设置项适配器；条目内容与点击行为由 Activity 组装后传入 */
class SettingAdapter(
    private val items: List<Item>,
) : RecyclerView.Adapter<SettingAdapter.Holder>() {

    data class Item(
        val title: String,
        val hint: String,
        val onClick: () -> Unit,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class Holder(private val binding: ItemSettingBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            binding.settingTitle.text = item.title
            binding.settingHint.text = item.hint
            binding.settingHint.visibility =
                if (item.hint.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            binding.root.setOnClickListener { item.onClick() }
        }
    }
}
