package me.javayhu.poetry.ui.poet

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.PoetBrief
import me.javayhu.poetry.data.PoetRepository
import me.javayhu.poetry.databinding.ActivityPoetListBinding
import java.util.concurrent.Executors

/** 诗人列表：按热度排序，顶部可搜索 */
class PoetListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPoetListBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var adapter: PoetAdapter? = null
    private var searchSeq = 0

    private val debounce = Runnable { performSearch() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoetListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                main.removeCallbacks(debounce)
                main.postDelayed(debounce, 250)
            }
        })

        // 作品数先聚合好再建适配器（一次遍历索引，别放进 bind）
        io.execute {
            val counts = PoetRepository.worksCount()
            val poets = PoetRepository.featured(LIST_LIMIT)
            main.post {
                if (isFinishing) return@post
                val a = PoetAdapter(counts, ::openPoet)
                adapter = a
                binding.poetList.layoutManager = LinearLayoutManager(this)
                binding.poetList.adapter = a
                a.submit(poets)
            }
        }
    }

    private fun performSearch() {
        val keyword = binding.searchInput.text?.toString().orEmpty()
        val seq = ++searchSeq
        io.execute {
            val result = PoetRepository.search(keyword, LIST_LIMIT)
            main.post {
                if (isFinishing || seq != searchSeq) return@post
                adapter?.submit(result)
                binding.emptyView.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openPoet(poet: PoetBrief) {
        startActivity(
            Intent(this, PoetActivity::class.java)
                .putExtra(PoetActivity.EXTRA_ID, poet.id)
                .putExtra(PoetActivity.EXTRA_NAME, poet.name)
        )
    }

    override fun onDestroy() {
        main.removeCallbacks(debounce)
        io.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val LIST_LIMIT = 500
    }
}
