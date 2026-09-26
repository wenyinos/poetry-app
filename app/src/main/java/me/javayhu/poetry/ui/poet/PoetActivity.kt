package me.javayhu.poetry.ui.poet

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.data.PoetRepository
import me.javayhu.poetry.databinding.ActivityPoetBinding
import me.javayhu.poetry.ui.home.PoemAdapter
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/**
 * 诗人详情：简介与生平按分片从云端取回，作品列表来自内置索引。
 *
 * 头部与作品列表用 ConcatAdapter 拼在一个 RecyclerView 里，整体一次滚动。
 */
class PoetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPoetBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val headerAdapter = PoetHeaderAdapter()
    private val worksAdapter = PoemAdapter(::openPoem)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra(EXTRA_ID, 0)
        val passedName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        binding.toolbar.title = passedName.ifEmpty { getString(R.string.title_poet) }
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.contentList.layoutManager = LinearLayoutManager(this)
        binding.contentList.adapter = ConcatAdapter(headerAdapter, worksAdapter)

        binding.progress.visibility = View.VISIBLE
        io.execute {
            val result = PoetRepository.getPoet(id)
            val poetName = when (result) {
                is PoetRepository.Result.Full -> result.poet.name
                is PoetRepository.Result.Brief -> result.brief.name
                PoetRepository.Result.NotFound -> passedName
            }
            val works = PoetRepository.poemsOf(poetName, WORKS_LIMIT)
            val header = buildHeader(result, passedName, works.size)
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                headerAdapter.set(header)
                worksAdapter.submit(works)
            }
        }
    }

    private fun buildHeader(
        result: PoetRepository.Result,
        fallbackName: String,
        worksCount: Int,
    ): PoetHeaderAdapter.Header {
        val worksLabel = getString(R.string.poet_works, worksCount)
        return when (result) {
            is PoetRepository.Result.Full -> PoetHeaderAdapter.Header(
                name = result.poet.name,
                meta = joinMeta(result.poet.dynasty, worksLabel),
                desc = result.poet.desc,
                content = result.poet.content,
                worksTitle = worksLabel,
            )
            is PoetRepository.Result.Brief -> PoetHeaderAdapter.Header(
                name = result.brief.name,
                meta = joinMeta(result.brief.dynasty, worksLabel),
                desc = getString(
                    if (result.canFetch) R.string.poet_fetch_failed
                    else R.string.poet_offline_only
                ),
                content = "",
                worksTitle = worksLabel,
            )
            PoetRepository.Result.NotFound -> PoetHeaderAdapter.Header(
                name = fallbackName,
                meta = worksLabel,
                desc = getString(R.string.poet_empty),
                content = "",
                worksTitle = worksLabel,
            )
        }
    }

    private fun joinMeta(dynasty: String, worksLabel: String): String =
        if (dynasty.isEmpty()) worksLabel else "$dynasty · $worksLabel"

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

    companion object {
        const val EXTRA_ID = "poet_id"
        const val EXTRA_NAME = "poet_name"

        /** 作品列表最多展示的条数，避免一次塞入上千条 */
        private const val WORKS_LIMIT = 200
    }
}
