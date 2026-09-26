package me.javayhu.poetry.ui.mingju

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
import me.javayhu.poetry.databinding.ActivityMingjuBinding
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/** 名句精选：5,764 条名句全部内置，离线可读；点击跳转到出处诗词 */
class MingjuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMingjuBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = MingjuAdapter(::openOrigin)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMingjuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.mingjuList.layoutManager = LinearLayoutManager(this)
        binding.mingjuList.adapter = adapter

        binding.progress.visibility = View.VISIBLE
        io.execute {
            val list = LocalDataSource.mingju()
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                adapter.submit(list)
                binding.countText.text = getString(R.string.mingju_count, list.size)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openOrigin(mingju: Mingju) {
        if (mingju.poetryId <= 0) return
        startActivity(
            Intent(this, PoetryActivity::class.java)
                .putExtra(PoetryActivity.EXTRA_ID, mingju.poetryId)
                .putExtra(PoetryActivity.EXTRA_TITLE, mingju.poetryName)
        )
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
