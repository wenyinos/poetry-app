package me.javayhu.poetry.ui.explore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import me.javayhu.poetry.R
import me.javayhu.poetry.data.DiscoveryRepository
import me.javayhu.poetry.data.LocalDataSource
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.databinding.ActivityExploreBinding
import me.javayhu.poetry.ui.home.PoemAdapter
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/**
 * 诗词筛选：按朝代与标签过滤，两类条件都做成可横滑的单选 chip。
 *
 * 原版还有「形式」（诗/词/曲/文言文）一档，但开源数据里没有该字段，
 * 无法可靠还原，因此只保留有数据支撑的朝代与标签两个维度。
 */
class ExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExploreBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = PoemAdapter(::openPoem)

    private val dynastyChips = ArrayList<Chip>()
    private val tagChips = ArrayList<Chip>()

    private var selectedDynasty: String? = null
    private var selectedTag: String? = null
    private var filterSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.resultList.layoutManager = LinearLayoutManager(this)
        binding.resultList.adapter = adapter

        binding.progress.visibility = View.VISIBLE
        io.execute {
            val dynasties = LocalDataSource.dynasties()
            val tags = topTags()
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                buildChips(dynasties, tags)
                applyFilter()
            }
        }
    }

    /** 标签按覆盖的诗词数量取前若干个，全量 45 个会让面板过长 */
    private fun topTags(): List<String> =
        LocalDataSource.tagIndex().entries
            .sortedByDescending { it.value.size }
            .take(MAX_TAGS)
            .map { it.key }

    private fun buildChips(dynasties: List<Pair<String, Int>>, tags: List<String>) {
        binding.dynastyGroup.removeAllViews()
        dynastyChips.clear()
        dynastyChips.add(
            makeChip(null, getString(R.string.filter_any)) { selectDynasty(it) }
        )
        for ((name, count) in dynasties) {
            dynastyChips.add(makeChip(name, "$name $count") { selectDynasty(it) })
        }
        dynastyChips.forEach { binding.dynastyGroup.addView(it) }

        binding.tagGroup.removeAllViews()
        tagChips.clear()
        tagChips.add(
            makeChip(null, getString(R.string.filter_any)) { selectTag(it) }
        )
        for (tag in tags) {
            tagChips.add(makeChip(tag, tag) { selectTag(it) })
        }
        tagChips.forEach { binding.tagGroup.addView(it) }

        syncChipState(dynastyChips, selectedDynasty)
        syncChipState(tagChips, selectedTag)
    }

    private fun makeChip(value: String?, label: String, onPick: (String?) -> Unit): Chip =
        Chip(this).apply {
            text = label
            tag = value.orEmpty()
            isCheckable = true
            setOnClickListener { onPick(value) }
        }

    private fun syncChipState(chips: List<Chip>, selected: String?) {
        for (chip in chips) {
            val value = (chip.tag as? String).orEmpty().takeIf { it.isNotEmpty() }
            chip.isChecked = value == selected
        }
    }

    private fun selectDynasty(value: String?) {
        selectedDynasty = value
        syncChipState(dynastyChips, value)
        applyFilter()
    }

    private fun selectTag(value: String?) {
        selectedTag = value
        syncChipState(tagChips, value)
        applyFilter()
    }

    private fun applyFilter() {
        val dynasty = selectedDynasty
        val tag = selectedTag
        val seq = ++filterSeq
        binding.progress.visibility = View.VISIBLE
        io.execute {
            val result = DiscoveryRepository.filter(dynasty, tag)
            main.post {
                if (isFinishing || seq != filterSeq) return@post
                binding.progress.visibility = View.GONE
                adapter.submit(result)
                binding.resultCount.text = getString(R.string.filter_result, result.size)
                binding.emptyView.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openPoem(poem: PoemBrief) {
        startActivity(
            Intent(this, PoetryActivity::class.java)
                .putExtra(PoetryActivity.EXTRA_ID, poem.id)
                .putExtra(PoetryActivity.EXTRA_TITLE, poem.name)
        )
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val MAX_TAGS = 24
    }
}
