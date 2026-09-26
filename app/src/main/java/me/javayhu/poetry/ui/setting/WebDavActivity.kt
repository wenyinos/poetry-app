package me.javayhu.poetry.ui.setting

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import me.javayhu.poetry.R
import me.javayhu.poetry.data.FavoriteRepository
import me.javayhu.poetry.data.FavoriteStore
import me.javayhu.poetry.data.WebDavClient
import me.javayhu.poetry.databinding.ActivityWebdavBinding
import java.util.concurrent.Executors

/** WebDAV 同步设置：填写网盘地址与凭据，可测试连接、单向覆盖 */
class WebDavActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebdavBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebdavBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val config = FavoriteRepository.loadConfig()
        binding.inputUrl.setText(config.url)
        binding.inputUser.setText(config.user)
        binding.inputPassword.setText(config.password)

        binding.saveButton.setOnClickListener { save() }
        binding.testButton.setOnClickListener { testConnection() }
        binding.uploadButton.setOnClickListener { confirmOverwrite(upload = true) }
        binding.downloadButton.setOnClickListener { confirmOverwrite(upload = false) }
    }

    private fun currentConfig() = WebDavClient.Config(
        url = binding.inputUrl.text?.toString()?.trim().orEmpty(),
        user = binding.inputUser.text?.toString()?.trim().orEmpty(),
        password = binding.inputPassword.text?.toString().orEmpty(),
    )

    private fun save() {
        val config = currentConfig()
        if (!config.isUsable) {
            toast(getString(R.string.webdav_not_configured))
            return
        }
        FavoriteRepository.saveConfig(config)
        toast(getString(R.string.webdav_saved))
    }

    private fun testConnection() {
        val config = currentConfig()
        if (!config.isUsable) {
            toast(getString(R.string.webdav_not_configured))
            return
        }
        // 测试通过即视为确认配置，直接保存，省去再点一次保存
        FavoriteRepository.saveConfig(config)
        binding.progress.visibility = View.VISIBLE
        io.execute {
            val outcome =
                runCatching { WebDavClient.get(config, FavoriteRepository.REMOTE_FILE) }
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                outcome.fold(
                    onSuccess = { text ->
                        val count = text?.let { FavoriteStore.Snapshot.parse(it).items.size }
                        toast(
                            if (count == null) getString(R.string.webdav_test_ok_empty)
                            else getString(R.string.webdav_test_ok_with_data, count)
                        )
                    },
                    onFailure = {
                        toast(getString(R.string.webdav_test_failed, it.message ?: "未知错误"))
                    },
                )
            }
        }
    }

    private fun confirmOverwrite(upload: Boolean) {
        val config = currentConfig()
        if (!config.isUsable) {
            toast(getString(R.string.webdav_not_configured))
            return
        }
        AlertDialog.Builder(this)
            .setMessage(if (upload) R.string.webdav_upload_confirm else R.string.webdav_download_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> doOverwrite(config, upload) }
            .show()
    }

    private fun doOverwrite(config: WebDavClient.Config, upload: Boolean) {
        FavoriteRepository.saveConfig(config)
        binding.progress.visibility = View.VISIBLE
        io.execute {
            val result = if (upload) FavoriteRepository.forceUpload() else FavoriteRepository.forceDownload()
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                when (result) {
                    is FavoriteRepository.SyncResult.Success -> toast(
                        if (upload) getString(R.string.favorite_overwrite_upload_done, result.total)
                        else getString(R.string.favorite_overwrite_download_done, result.total)
                    )
                    is FavoriteRepository.SyncResult.Failed ->
                        toast(getString(R.string.favorite_sync_failed, result.reason))
                    FavoriteRepository.SyncResult.NotConfigured ->
                        toast(getString(R.string.webdav_not_configured))
                }
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
