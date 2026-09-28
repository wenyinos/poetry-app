package me.javayhu.poetry.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import me.javayhu.poetry.R
import me.javayhu.poetry.data.LocalDataSource
import me.javayhu.poetry.data.PoemContent
import me.javayhu.poetry.ui.poetry.PoetryActivity
import java.util.concurrent.Executors

/**
 * 各款小组件共用的渲染与刷新逻辑。
 *
 * 内容取自内置热门集，断网也能显示；各款的差异只在布局、行数与刷新周期，
 * 因此渲染集中在这里，各 Provider 只声明自己的参数。
 *
 * 注意 RemoteViews 的限制：targetSdk 31+ 只允许调用带 @RemotableViewMethod
 * 注解的方法，因此这里只用 setTextViewText / setTextColor / setViewVisibility /
 * setBackgroundColor 这几个受支持的方法。
 */
object WidgetSupport {

    /** 各 Provider 共用一个后台线程池 */
    val io = Executors.newSingleThreadExecutor()

    /** 定制款显示的行数 */
    private const val CUSTOM_LINES = 5

    /** 用固定布局渲染（默认 / 浅色 / 深色 / 多行 共用） */
    fun simple(context: Context, layoutRes: Int, maxLines: Int): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        LocalDataSource.randomHot()?.let { poem ->
            views.setTextViewText(R.id.widget_content, leadingLines(poem.content, maxLines))
            views.setTextViewText(R.id.widget_source, source(poem))
            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, poem))
        }
        return views
    }

    /** 定制微件：按实例配置设置配色、字号与是否显示来源 */
    fun custom(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_poetry_custom)
        val config = WidgetConfig.load(context, widgetId)
        val colors = config.colors

        // 底色打在内层：根布局要留给上面那层柔光描边
        views.setInt(R.id.widget_tint, "setBackgroundColor", colors.background)
        views.setTextColor(R.id.widget_content, colors.content)
        views.setTextColor(R.id.widget_source, colors.source)
        views.setTextViewTextSize(
            R.id.widget_content, TypedValue.COMPLEX_UNIT_SP, config.textSize
        )
        views.setViewVisibility(
            R.id.widget_source, if (config.showSource) View.VISIBLE else View.GONE
        )

        LocalDataSource.randomHot()?.let { poem ->
            views.setTextViewText(R.id.widget_content, leadingLines(poem.content, CUSTOM_LINES))
            views.setTextViewText(R.id.widget_source, source(poem))
            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, poem))
        }
        return views
    }

    /** 小组件高度有限，只取前几行非空内容 */
    fun leadingLines(text: String, maxLines: Int): String =
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

    /** 触发指定小组件的全部实例重新取诗并刷新 */
    fun refreshAll(context: Context, provider: Class<*>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(context, provider).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
        )
    }
}
