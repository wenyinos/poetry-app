package me.javayhu.poetry.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import me.javayhu.poetry.R
import me.javayhu.poetry.data.LocalDataSource
import me.javayhu.poetry.data.PoemContent
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/**
 * 桌面诗词小组件。
 *
 * 内容取自内置热门集，因此断网也能正常显示；刷新周期在
 * res/xml/poetry_widget_info.xml 中配置（3 小时）。
 */
class PoetryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        IO.execute {
            try {
                for (id in ids) {
                    val poem = LocalDataSource.randomHot()
                    manager.updateAppWidget(id, buildViews(context, poem))
                }
            } catch (_: Exception) {
                // 数据读取失败时保留上一次内容，等待下次刷新
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, poem: PoemContent?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_poetry)
        if (poem == null) return views
        views.setTextViewText(R.id.widget_content, leadingLines(poem.content, MAX_LINES))
        views.setTextViewText(R.id.widget_source, source(poem))
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, poem))
        return views
    }

    /** 小组件高度有限，只取前几行非空内容 */
    private fun leadingLines(text: String, maxLines: Int): String =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(maxLines)
            .joinToString("\n")

    private fun source(poem: PoemContent): String =
        if (poem.author.isEmpty()) poem.name else "${poem.name} · ${poem.author}"

    private fun openIntent(context: Context, poem: PoemContent): PendingIntent {
        val intent = Intent(context, PoetryActivity::class.java).apply {
            putExtra(PoetryActivity.EXTRA_ID, poem.id)
            putExtra(PoetryActivity.EXTRA_TITLE, poem.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // requestCode 用诗歌 id，点击不同诗词时能各自更新
        return PendingIntent.getActivity(
            context,
            poem.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val MAX_LINES = 4
        private val IO = Executors.newSingleThreadExecutor()

        /** 数据变化后主动通知所有小组件刷新 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PoetryWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, PoetryWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
