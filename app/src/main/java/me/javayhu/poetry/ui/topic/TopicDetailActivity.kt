package me.javayhu.poetry.ui.topic

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.data.TopicRepository
import me.javayhu.poetry.databinding.ActivityTopicDetailBinding
import me.javayhu.poetry.ui.home.PoemAdapter
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/** 专题详情：只接收专题的标题与类型参数，内容在本地索引上现算 */
class TopicDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopicDetailBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = PoemAdapter(::openPoem)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopicDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val kindName = intent.getStringExtra(EXTRA_KIND).orEmpty()
        val value = intent.getStringExtra(EXTRA_VALUE).orEmpty()

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.resultList.layoutManager = LinearLayoutManager(this)
        binding.resultList.adapter = adapter

        val kind = runCatching { TopicRepository.Kind.valueOf(kindName) }.getOrNull()
        if (kind == null) {
            binding.emptyView.visibility = View.VISIBLE
            return
        }

        binding.progress.visibility = View.VISIBLE
        io.execute {
            val list = TopicRepository.poemsOf(TopicRepository.Topic(title, "", kind, value))
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                adapter.submit(list)
                binding.countText.text = getString(R.string.topic_count, list.size)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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

    companion object {
        const val EXTRA_TITLE = "topic_title"
        const val EXTRA_KIND = "topic_kind"
        const val EXTRA_VALUE = "topic_value"
    }
}
