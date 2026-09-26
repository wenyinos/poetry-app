package me.javayhu.poetry.ui.home

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.LocalDataSource
import me.javayhu.poetry.data.Mingju
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.data.PoemRepository
import me.javayhu.poetry.databinding.ActivityHomeBinding
import me.javayhu.poetry.ui.favorite.FavoriteActivity
import me.javayhu.poetry.ui.mingju.MingjuActivity
import me.javayhu.poetry.ui.poet.PoetListActivity
import me.javayhu.poetry.ui.poetry.PoetryActivity
import me.javayhu.poetry.ui.search.SearchActivity
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = PoemAdapter(::openPoem)
    private var mingju: List<Mingju> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.poemList.layoutManager = LinearLayoutManager(this)
        binding.poemList.adapter = adapter

        binding.toolbar.inflateMenu(R.menu.menu_home)
        binding.toolbar.setOnMenuItemClickListener { item ->
            val target = when (item.itemId) {
                R.id.action_search -> SearchActivity::class.java
                R.id.action_favorites -> FavoriteActivity::class.java
                R.id.action_poets -> PoetListActivity::class.java
                R.id.action_mingju -> MingjuActivity::class.java
                else -> null
            }
            if (target != null) {
                startActivity(Intent(this, target))
                true
            } else {
                false
            }
        }

        // 点卡片换一条名句
        binding.mingjuCard.setOnClickListener { showRandomMingju() }

        load()
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        io.execute {
            // 索引有 7 万条，取热度前若干条用于首页；排序在后台线程完成
            val featured = PoemRepository.featured(FEATURED_LIMIT)
            val mingjuList = LocalDataSource.mingju()
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                mingju = mingjuList
                adapter.submit(featured)
                showRandomMingju()
            }
        }
    }

    private fun showRandomMingju() {
        if (mingju.isEmpty()) return
        val m = mingju.random()
        binding.mingjuContent.text = m.content
        binding.mingjuSource.text = if (m.poetName.isEmpty()) {
            m.poetryName
        } else {
            "${m.poetryName} · ${m.poetName}"
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
        const val FEATURED_LIMIT = 100
    }
}
