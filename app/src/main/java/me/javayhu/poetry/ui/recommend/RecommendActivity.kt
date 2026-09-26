package me.javayhu.poetry.ui.recommend

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.DiscoveryRepository
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.databinding.ActivityRecommendBinding
import me.javayhu.poetry.ui.home.PoemAdapter
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/** 诗词推荐：从热度前 2000 首中随机取样，工具栏可「换一批」 */
class RecommendActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecommendBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = PoemAdapter(::openPoem)
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecommendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_recommend)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_shuffle) {
                load()
                true
            } else {
                false
            }
        }

        binding.resultList.layoutManager = LinearLayoutManager(this)
        binding.resultList.adapter = adapter
        load()
    }

    private fun load() {
        if (loading) return
        loading = true
        binding.progress.visibility = View.VISIBLE
        io.execute {
            val list = DiscoveryRepository.recommend(RECOMMEND_LIMIT)
            main.post {
                loading = false
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                adapter.submit(list)
                // 换一批后回到顶部，否则停在原来的滚动位置会让人以为没变化
                binding.resultList.scrollToPosition(0)
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
        const val RECOMMEND_LIMIT = 100
    }
}
