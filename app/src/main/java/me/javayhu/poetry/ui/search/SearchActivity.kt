package me.javayhu.poetry.ui.search

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import me.javayhu.poetry.R
import me.javayhu.poetry.data.AppSettings
import me.javayhu.poetry.data.DiscoveryRepository
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.data.SearchMode
import me.javayhu.poetry.databinding.ActivitySearchBinding
import me.javayhu.poetry.ui.home.PoemAdapter
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/**
 * 搜索页。沿用原版的三档模式（诗名 / 作者 / 诗文），点模式按钮循环切换。
 *
 * 输入即搜并做 250 ms 防抖；每次搜索带序号，过期结果会被丢弃，
 * 避免快速输入时旧结果覆盖新结果。
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = PoemAdapter(::openPoem)

    private var mode = SearchMode.TITLE
    private var searchSeq = 0
    private var contentModeWarned = false

    private val debounce = Runnable { performSearch() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.resultList.layoutManager = LinearLayoutManager(this)
        binding.resultList.adapter = adapter
        binding.modeButton.setOnClickListener { cycleMode() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = scheduleSearch()
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                main.removeCallbacks(debounce)
                // 只在用户明确按下搜索键时记入历史，避免把输入过程中的
                // 半截词（「李」「李白」）都存进去
                AppSettings.addSearchHistory(
                    binding.searchInput.text?.toString().orEmpty()
                )
                performSearch()
                true
            } else {
                false
            }
        }
        binding.clearHistory.setOnClickListener {
            AppSettings.clearSearchHistory()
            showHistory()
        }

        binding.searchInput.requestFocus()
        showHistory()
    }

    private fun cycleMode() {
        mode = when (mode) {
            SearchMode.TITLE -> SearchMode.AUTHOR
            SearchMode.AUTHOR -> SearchMode.CONTENT
            SearchMode.CONTENT -> SearchMode.TITLE
        }
        binding.modeButton.text = getString(
            when (mode) {
                SearchMode.TITLE -> R.string.search_mode_title
                SearchMode.AUTHOR -> R.string.search_mode_author
                SearchMode.CONTENT -> R.string.search_mode_content
            }
        )
        if (mode == SearchMode.CONTENT && !contentModeWarned) {
            contentModeWarned = true
            Toast.makeText(this, R.string.search_hint_content, Toast.LENGTH_LONG).show()
        }
        performSearch()
    }

    private fun scheduleSearch() {
        main.removeCallbacks(debounce)
        main.postDelayed(debounce, 250)
    }

    private fun performSearch() {
        val query = binding.searchInput.text?.toString().orEmpty().trim()
        val requestedMode = mode
        val seq = ++searchSeq

        if (query.isEmpty()) {
            adapter.submit(emptyList())
            binding.resultCount.visibility = View.GONE
            binding.emptyView.visibility = View.GONE
            showHistory()
            return
        }
        binding.historyArea.visibility = View.GONE

        io.execute {
            val result = DiscoveryRepository.search(query, requestedMode)
            main.post {
                if (isFinishing || seq != searchSeq) return@post
                adapter.submit(result)
                binding.resultCount.text = getString(R.string.search_result_count, result.size)
                binding.resultCount.visibility = View.VISIBLE
                binding.emptyView.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /** 输入框为空时展示历史词，点击即回填 */
    private fun showHistory() {
        val history = AppSettings.searchHistory()
        binding.historyArea.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        binding.historyGroup.removeAllViews()
        for (word in history) {
            val chip = Chip(this).apply {
                text = word
                isCheckable = false
                setOnClickListener {
                    binding.searchInput.setText(word)
                    binding.searchInput.setSelection(word.length)
                }
            }
            binding.historyGroup.addView(chip)
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
        main.removeCallbacks(debounce)
        io.shutdown()
        super.onDestroy()
    }
}
