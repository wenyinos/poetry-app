package me.javayhu.poetry.ui.history

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.AppSettings
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.data.PoemRepository
import me.javayhu.poetry.databinding.ActivityHistoryBinding
import me.javayhu.poetry.ui.home.PoemAdapter
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/**
 * 最近浏览。
 *
 * 只保存诗词 id（最多 50 条），展示时用索引二分查找补全诗名与作者，
 * 因此几乎不占存储。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = PoemAdapter(::openPoem)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_history)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_history) {
                confirmClear()
                true
            } else {
                false
            }
        }

        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter
        load()
    }

    private fun load() {
        binding.progress.visibility = View.VISIBLE
        io.execute {
            // 存的是 id，这里回到索引里补全标题与作者
            val poems = AppSettings.browseHistory().mapNotNull { PoemRepository.findBrief(it) }
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                adapter.submit(poems)
                binding.countText.text = getString(R.string.history_count, poems.size)
                binding.emptyView.visibility = if (poems.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_clear_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AppSettings.clearBrowseHistory()
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                load()
            }
            .show()
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
}
