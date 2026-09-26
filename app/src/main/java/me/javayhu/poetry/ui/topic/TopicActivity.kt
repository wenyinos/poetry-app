package me.javayhu.poetry.ui.topic

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.databinding.ActivityTopicBinding
import me.javayhu.poetry.data.TopicRepository
import java.util.concurrent.Executors

/** 诗词专题：专题由内置数据自动生成（热门 / 朝代 / 主题标签） */
class TopicActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopicBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = TopicAdapter(::openTopic)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.topicList.layoutManager = LinearLayoutManager(this)
        binding.topicList.adapter = adapter

        io.execute {
            val topics = TopicRepository.topics()
            main.post {
                if (isFinishing) return@post
                adapter.submit(topics)
            }
        }
    }

    private fun openTopic(topic: TopicRepository.Topic) {
        startActivity(
            Intent(this, TopicDetailActivity::class.java)
                .putExtra(TopicDetailActivity.EXTRA_TITLE, topic.title)
                .putExtra(TopicDetailActivity.EXTRA_KIND, topic.kind.name)
                .putExtra(TopicDetailActivity.EXTRA_VALUE, topic.value)
        )
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
