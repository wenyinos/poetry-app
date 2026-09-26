package me.javayhu.poetry.ui.setting

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.databinding.ActivitySettingBinding
import me.javayhu.poetry.ui.about.AboutActivity
import me.javayhu.poetry.ui.favorite.FavoriteActivity
import java.io.File
import java.util.concurrent.Executors

/** 应用设置：收藏、微件说明、WebDAV、清理已下载内容、关于 */
class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.settingList.layoutManager = LinearLayoutManager(this)
        binding.settingList.adapter = SettingAdapter(buildItems())
    }

    private fun buildItems() = listOf(
        SettingAdapter.Item(
            title = getString(R.string.setting_favorite),
            hint = getString(R.string.setting_favorite_hint),
            onClick = { open(FavoriteActivity::class.java) },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_webdav),
            hint = getString(R.string.setting_webdav_hint),
            onClick = { open(WebDavActivity::class.java) },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_widget),
            hint = getString(R.string.setting_widget_hint),
            onClick = {},
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_cache),
            hint = cacheHint(),
            onClick = { clearCache() },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_about),
            hint = "",
            onClick = { open(AboutActivity::class.java) },
        ),
    )

    /** 已下载的诗词与诗人分片放在 filesDir 下 */
    private fun downloadedDirs(): List<File> =
        listOf(File(filesDir, "poems"), File(filesDir, "poets"))

    private fun cacheSize(): Long =
        downloadedDirs().sumOf { dir ->
            dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }

    private fun cacheHint(): String {
        val size = cacheSize()
        return if (size <= 0) getString(R.string.setting_cache_empty)
        else getString(R.string.setting_cache_hint, formatSize(size))
    }

    private fun clearCache() {
        val size = cacheSize()
        if (size <= 0) {
            toast(getString(R.string.setting_cache_empty))
            return
        }
        io.execute {
            downloadedDirs().forEach { it.deleteRecursively() }
            main.post {
                if (isFinishing) return@post
                toast(getString(R.string.setting_cache_cleared, formatSize(size)))
                // 重建设置列表以刷新占用显示
                binding.settingList.adapter = SettingAdapter(buildItems())
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
        bytes >= 1 shl 10 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
