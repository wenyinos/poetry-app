package me.javayhu.poetry.ui.favorite

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.R
import me.javayhu.poetry.data.Favorite
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
