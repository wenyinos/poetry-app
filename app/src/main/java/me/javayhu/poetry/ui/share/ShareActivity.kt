package me.javayhu.poetry.ui.share

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.chip.Chip
import me.javayhu.poetry.R
import me.javayhu.poetry.data.PoemContent
import me.javayhu.poetry.data.PoemRepository
import me.javayhu.poetry.databinding.ActivityShareBinding
import me.javayhu.poetry.util.PoemImageRenderer
import java.io.File
import java.util.concurrent.Executors

/**
 * 诗词分享：把诗渲染成竖版图片，可保存到相册或分享给其它应用。
 *
 * 保存相册走 MediaStore，Android 10 起无需任何存储权限；Android 9 上
 * 该系统接口需要 WRITE_EXTERNAL_STORAGE，因此只提供「分享」，由用户在
 * 目标应用里自行保存。
 */
class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var poem: PoemContent? = null
    private var busy = false
    private var template = PoemImageRenderer.Template.CLASSIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.shareFooter.text = getString(R.string.app_name)
        setBusy(false)

        val id = intent.getIntExtra(EXTRA_ID, 0)
        io.execute {
            val result = PoemRepository.getPoem(id)
            main.post {
                if (isFinishing) return@post
                setBusy(false)
                when (result) {
                    is PoemRepository.Result.Full -> {
                        poem = result.poem
                        bind(result.poem)
                    }
                    is PoemRepository.Result.Brief -> bindBrief(
                        result.brief.name, result.brief.dynasty, result.brief.author
                    )
                    PoemRepository.Result.NotFound -> finish()
                }
            }
        }

        buildTemplates()
        binding.saveButton.setOnClickListener { saveToGallery() }
        binding.shareButton.setOnClickListener { shareImage() }
    }

    private fun bind(p: PoemContent) {
        binding.shareTitle.text = p.name
        binding.shareMeta.text = listOf(p.dynasty, p.author)
            .filter { it.isNotEmpty() }.joinToString(" · ")
        binding.shareContent.text = p.content
    }

    private fun bindBrief(name: String, dynasty: String, author: String) {
        binding.shareTitle.text = name
        binding.shareMeta.text = listOf(dynasty, author)
            .filter { it.isNotEmpty() }.joinToString(" · ")
        binding.shareContent.text = getString(R.string.poetry_fetch_failed)
    }

    // ---------------- 图片 ----------------

    /** 三套样式，切换时同步刷新预览配色 */
    private fun buildTemplates() {
        binding.templateGroup.removeAllViews()
        for (t in PoemImageRenderer.Template.entries) {
            val chip = Chip(this).apply {
                text = t.label
                isCheckable = true
                isChecked = t == template
                setOnClickListener {
                    template = t
                    buildTemplates()
                    applyPreviewColors()
                }
            }
            binding.templateGroup.addView(chip)
        }
        applyPreviewColors()
    }

    /** 预览区直接复用模板配色，所见即所得 */
    private fun applyPreviewColors() {
        binding.previewBox.setBackgroundColor(template.bgColor)
        binding.shareTitle.setTextColor(template.titleColor)
        binding.shareMeta.setTextColor(template.metaColor)
        binding.shareContent.setTextColor(template.bodyColor)
        binding.shareFooter.setTextColor(template.accentColor)
    }

    private fun renderBitmap(): Bitmap? {
        val p = poem ?: return null
        return PoemImageRenderer.render(p, getString(R.string.app_name), template)
    }

    private fun saveToGallery() {
        if (poem == null) {
            toast(getString(R.string.share_failed, getString(R.string.poetry_not_found)))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 上写相册需要额外权限，引导用户改用分享
            toast(getString(R.string.share_hint))
            return
        }
        if (busy) return
        busy = true
        setBusy(true)
        io.execute {
            val outcome = runCatching {
                val bitmap = renderBitmap() ?: error("无法生成图片")
                saveViaMediaStore(bitmap)
            }
            main.post {
                busy = false
                if (isFinishing) return@post
                setBusy(false)
                outcome.fold(
                    onSuccess = { name -> toast(getString(R.string.share_saved, name)) },
                    onFailure = { toast(getString(R.string.share_failed, it.message ?: "未知错误")) },
                )
            }
        }
    }

    private fun saveViaMediaStore(bitmap: Bitmap): String {
        val name = "shijing_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/诗鲸",
            )
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("系统相册不可写")
        contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: error("写入相册失败")
        return name
    }

    private fun shareImage() {
        if (poem == null) {
            toast(getString(R.string.share_failed, getString(R.string.poetry_not_found)))
            return
        }
        if (busy) return
        busy = true
        setBusy(true)
        io.execute {
            val outcome = runCatching {
                val bitmap = renderBitmap() ?: error("无法生成图片")
                // 写入 cacheDir，再由 FileProvider 授权给目标应用
                val dir = File(cacheDir, "images").apply { mkdirs() }
                val file = File(dir, "shijing_${System.currentTimeMillis()}.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            }
            main.post {
                busy = false
                if (isFinishing) return@post
                setBusy(false)
                outcome.fold(
                    onSuccess = { uri -> launchShareSheet(uri) },
                    onFailure = { toast(getString(R.string.share_failed, it.message ?: "未知错误")) },
                )
            }
        }
    }

    private fun launchShareSheet(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share_image)))
    }

    // ---------------- 辅助 ----------------

    /** 生成图片期间禁用按钮，避免重复点击 */
    private fun setBusy(value: Boolean) {
        binding.shareButton.isEnabled = !value
        binding.saveButton.isEnabled = !value
        binding.shareButton.text = getString(
            if (value) R.string.share_generating else R.string.action_share_image
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "share_poem_id"
    }
}
