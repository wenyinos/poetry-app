package me.javayhu.poetry.ui.favorite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.Favorite
import me.javayhu.poetry.data.FavoriteStore
import me.javayhu.poetry.data.FavoriteRepository
import me.javayhu.poetry.databinding.ActivityFavoriteBinding
import me.javayhu.poetry.ui.poetry.PoetryActivity
import me.javayhu.poetry.ui.setting.WebDavActivity
import java.util.concurrent.Executors

/** 收藏列表；工具栏提供同步与 WebDAV 设置入口 */
class FavoriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val adapter = FavoriteAdapter(::openPoem, ::removeFavorite)
    private var syncing = false

    private val onFavoritesChanged: () -> Unit = { refresh() }

    /** 导出：让用户选保存位置（SAF，无需存储权限） */
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { doExport(it) } }

    /** 导入：让用户挑一个 JSON 文件 */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { doImport(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_favorite)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sync -> {
                    startSync()
                    true
                }
                R.id.action_webdav_settings -> {
                    openSettings()
                    true
                }
                R.id.action_export -> {
                    exportLauncher.launch("shijing-favorites.json")
                    true
                }
                R.id.action_import -> {
                    // 有些文件管理器给的 MIME 不标准，放宽类型由用户自己挑
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    true
                }
                else -> false
            }
        }

        binding.favoriteList.layoutManager = LinearLayoutManager(this)
        binding.favoriteList.adapter = adapter

        // 收藏在其他界面被改动时同步刷新
        FavoriteRepository.addListener(onFavoritesChanged)
        refresh()
    }

    private fun refresh() {
        io.execute {
            val list = FavoriteRepository.list()
            main.post {
                if (isFinishing) return@post
                adapter.submit(list)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openPoem(favorite: Favorite) {
        startActivity(
            Intent(this, PoetryActivity::class.java)
                .putExtra(PoetryActivity.EXTRA_ID, favorite.id)
                .putExtra(PoetryActivity.EXTRA_TITLE, favorite.name)
        )
    }

    private fun removeFavorite(favorite: Favorite) {
        io.execute {
            FavoriteRepository.remove(favorite.id)
            main.post { toast(getString(R.string.favorite_removed)) }
        }
    }

    private fun startSync() {
        if (syncing) return
        if (!FavoriteRepository.isConfigured()) {
            toast(getString(R.string.favorite_sync_not_configured))
            openSettings()
            return
        }
        syncing = true
        binding.progress.visibility = View.VISIBLE
        io.execute {
            val result = FavoriteRepository.sync()
            main.post {
                syncing = false
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                when (result) {
                    is FavoriteRepository.SyncResult.Success -> toast(
                        getString(
                            R.string.favorite_sync_done,
                            result.total, result.addedLocally, result.addedRemotely,
                        )
                    )
                    is FavoriteRepository.SyncResult.Failed ->
                        toast(getString(R.string.favorite_sync_failed, result.reason))
                    FavoriteRepository.SyncResult.NotConfigured ->
                        toast(getString(R.string.favorite_sync_not_configured))
                }
                refresh()
            }
        }
    }

    /** 把当前收藏整份写成 JSON；格式与 WebDAV 同步用的完全一致 */
    private fun doExport(uri: Uri) {
        io.execute {
            val snapshot = FavoriteRepository.snapshot()
            val outcome = runCatching {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(snapshot.toJson().toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选位置")
            }
            main.post {
                if (isFinishing) return@post
                outcome.fold(
                    onSuccess = {
                        toast(getString(R.string.favorite_export_done, snapshot.items.size))
                    },
                    onFailure = {
                        toast(getString(R.string.favorite_sync_failed, it.message ?: "未知错误"))
                    },
                )
            }
        }
    }

    /** 导入采用合并策略，不会覆盖本机已有的收藏 */
    private fun doImport(uri: Uri) {
        io.execute {
            val outcome = runCatching {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("无法读取所选文件")
                val parsed = FavoriteStore.Snapshot.parse(text)
                if (parsed.items.isEmpty()) error(getString(R.string.favorite_import_failed))
                FavoriteRepository.mergeIn(parsed.items)
            }
            main.post {
                if (isFinishing) return@post
                outcome.fold(
                    onSuccess = { total ->
                        toast(getString(R.string.favorite_import_done, total))
                        refresh()
                    },
                    onFailure = {
                        toast(getString(R.string.favorite_sync_failed, it.message ?: "未知错误"))
                    },
                )
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, WebDavActivity::class.java))
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        FavoriteRepository.removeListener(onFavoritesChanged)
        io.shutdown()
        super.onDestroy()
    }
}
