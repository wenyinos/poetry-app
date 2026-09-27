package me.javayhu.poetry.ui.setting

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import me.javayhu.poetry.PoetryApp
import me.javayhu.poetry.R
import me.javayhu.poetry.data.AppSettings
import me.javayhu.poetry.data.RemoteDataSource
import me.javayhu.poetry.data.UpdateChecker
import me.javayhu.poetry.databinding.ActivitySettingBinding
import me.javayhu.poetry.ui.about.AboutActivity
import me.javayhu.poetry.ui.favorite.FavoriteActivity
import me.javayhu.poetry.widget.CustomDayPoetryWidgetProvider
import me.javayhu.poetry.widget.CustomHourPoetryWidgetProvider
import me.javayhu.poetry.widget.CustomPoetryWidgetProvider
import me.javayhu.poetry.widget.DarkPoetryWidgetProvider
import me.javayhu.poetry.widget.LightPoetryWidgetProvider
import me.javayhu.poetry.widget.LinesPoetryWidgetProvider
import me.javayhu.poetry.widget.PoetryWidgetProvider
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
            title = getString(R.string.setting_theme),
            hint = AppSettings.THEME_LABELS[AppSettings.themeMode.coerceIn(
                0, AppSettings.THEME_LABELS.size - 1
            )],
            onClick = { showThemePicker() },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_font_size),
            hint = AppSettings.FONT_SIZE_LABELS[AppSettings.fontSizeLevel.coerceIn(
                0, AppSettings.FONT_SIZE_LABELS.size - 1
            )],
            onClick = { showFontPicker() },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_webdav),
            hint = getString(R.string.setting_webdav_hint),
            onClick = { open(WebDavActivity::class.java) },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_widget),
            hint = getString(R.string.setting_widget_hint),
            onClick = { showWidgetPicker() },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_download_all),
            hint = downloadHint(),
            onClick = { confirmDownloadAll() },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_cache),
            hint = cacheHint(),
            onClick = { clearCache() },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_update),
            hint = "",
            onClick = { checkUpdate() },
        ),
        SettingAdapter.Item(
            title = getString(R.string.setting_about),
            hint = "",
            onClick = { open(AboutActivity::class.java) },
        ),
    )

    /** 主题切换：选中后立即生效，AppCompatDelegate 会重建界面 */
    private fun showThemePicker() {
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_theme_title)
            .setSingleChoiceItems(
                AppSettings.THEME_LABELS.toTypedArray(),
                AppSettings.themeMode,
            ) { dialog, which ->
                AppSettings.themeMode = which
                PoetryApp.applyTheme()
                dialog.dismiss()
            }
            .show()
    }

    /** 阅读字号：四档，改完回到详情页即生效 */
    private fun showFontPicker() {
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_font_title)
            .setSingleChoiceItems(
                AppSettings.FONT_SIZE_LABELS.toTypedArray(),
                AppSettings.fontSizeLevel,
            ) { dialog, which ->
                AppSettings.fontSizeLevel = which
                dialog.dismiss()
                refreshItems()
            }
            .show()
    }

    /** 选项变化后重建列表，让 hint 反映最新值 */
    private fun refreshItems() {
        binding.settingList.adapter = SettingAdapter(buildItems())
    }

    // ---------------- 更新检查 ----------------

    private fun checkUpdate() {
        toast(getString(R.string.update_checking))
        io.execute {
            val release = UpdateChecker.check()
            main.post {
                if (isFinishing) return@post
                if (release == null) {
                    toast(getString(R.string.update_latest))
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.update_available, release.version))
                        .setMessage(release.pageUrl)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.action_download) { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                        }
                        .show()
                }
            }
        }
    }

    // ---------------- 全量离线 ----------------

    private fun downloadHint(): String {
        val cached = RemoteDataSource.cachedSliceCount()
        val total = RemoteDataSource.totalSliceCount()
        return if (total > 0 && cached >= total) {
            getString(R.string.download_all_done)
        } else if (cached > 0) {
            getString(R.string.download_all_progress_hint, cached, total)
        } else {
            getString(R.string.download_all_hint)
        }
    }

    private fun confirmDownloadAll() {
        if (!RemoteDataSource.isConfigured) {
            toast(getString(R.string.download_not_configured))
            return
        }
        if (RemoteDataSource.cachedSliceCount() >= RemoteDataSource.totalSliceCount()) {
            toast(getString(R.string.download_all_done))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.setting_download_all)
            .setMessage(R.string.download_all_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> startDownloadAll() }
            .show()
    }

    /** 用一条水平进度条显示整体进度；已缓存的分片会被跳过，因此可中断后续传 */
    private fun startDownloadAll() {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(48, 32, 48, 32) }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.download_all_running)
            .setView(bar)
            .setCancelable(false)
            .show()

        io.execute {
            val failed = RemoteDataSource.downloadAll { done, total ->
                if (total > 0) main.post { bar.progress = done * 100 / total }
            }
            main.post {
                dialog.dismiss()
                if (isFinishing) return@post
                toast(
                    if (failed <= 0) getString(R.string.download_all_done)
                    else getString(R.string.download_all_partial, failed)
                )
                refreshItems()
            }
        }
    }

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
                refreshItems()
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

    // ---------------- 桌面微件 ----------------

    /** 让用户挑一款样式，再交给系统弹出「添加到主屏」的确认框 */
    private fun showWidgetPicker() {
        val labels = WIDGETS.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_pick_title)
            .setItems(labels) { _, which -> requestPin(WIDGETS[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 走系统的 requestPinAppWidget（Android 8.0+）。
     * 部分第三方桌面未实现该能力，此时退回到「长按桌面手动添加」的提示。
     */
    private fun requestPin(entry: WidgetEntry) {
        val manager = AppWidgetManager.getInstance(this)
        if (manager == null || !manager.isRequestPinAppWidgetSupported) {
            toast(getString(R.string.widget_pin_unsupported))
            return
        }
        val requested = manager.requestPinAppWidget(
            ComponentName(this, entry.providerClass), null, null
        )
        if (!requested) toast(getString(R.string.widget_pin_failed))
    }

    private data class WidgetEntry(
        val labelRes: Int,
        val providerClass: Class<*>,
    )

    private companion object {
        /** 可添加的小组件样式，顺序与系统微件列表保持一致 */
        val WIDGETS = listOf(
            WidgetEntry(R.string.widget_label_default, PoetryWidgetProvider::class.java),
            WidgetEntry(R.string.widget_label_lines, LinesPoetryWidgetProvider::class.java),
            WidgetEntry(R.string.widget_label_light, LightPoetryWidgetProvider::class.java),
            WidgetEntry(R.string.widget_label_dark, DarkPoetryWidgetProvider::class.java),
            WidgetEntry(R.string.widget_label_custom, CustomPoetryWidgetProvider::class.java),
            WidgetEntry(
                R.string.widget_label_custom_hour,
                CustomHourPoetryWidgetProvider::class.java,
            ),
            WidgetEntry(
                R.string.widget_label_custom_day,
                CustomDayPoetryWidgetProvider::class.java,
            ),
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
