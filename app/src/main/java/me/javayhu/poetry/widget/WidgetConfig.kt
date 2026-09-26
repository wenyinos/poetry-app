package me.javayhu.poetry.widget

import android.content.Context
import android.graphics.Color

/**
 * 「定制微件」的每实例配置。
 *
 * 原版提供完整的调色盘与对齐设置；这里保留配色与字号，对齐则不做 ——
 * RemoteViews 在 targetSdk 31+ 只允许调用带 @RemotableViewMethod 注解的方法，
 * TextView.setGravity 不在其列，动态对齐会在运行时抛异常。
 *
 * 配置按小组件实例 id 分别保存。
 */
object WidgetConfig {

    /** 一套配色预设 */
    data class Preset(
        val name: String,
        val background: Int,
        val content: Int,
        val source: Int,
    )

    val PRESETS = listOf(
        Preset("纯白", Color.parseColor("#FFFFFF"), Color.parseColor("#37474F"), Color.parseColor("#737373")),
        Preset("浅蓝", Color.parseColor("#E1F5FE"), Color.parseColor("#37474F"), Color.parseColor("#0288D1")),
        Preset("深色", Color.parseColor("#2B2B2B"), Color.parseColor("#EEEEEE"), Color.parseColor("#99FFFFFF")),
        Preset("米黄", Color.parseColor("#FFF8E1"), Color.parseColor("#4E342E"), Color.parseColor("#8D6E63")),
    )

    /** 可选字号（sp） */
    val TEXT_SIZES = listOf(12f, 14f, 16f, 18f)

    data class Config(
        val preset: Int = 1,
        val textSize: Float = 14f,
        val showSource: Boolean = true,
    ) {
        val colors: Preset get() = PRESETS[preset.coerceIn(0, PRESETS.size - 1)]
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

    fun load(context: Context, widgetId: Int): Config {
        val p = prefs(context)
        return Config(
            preset = p.getInt(key(widgetId, "preset"), 1),
            textSize = p.getFloat(key(widgetId, "size"), 14f),
            showSource = p.getBoolean(key(widgetId, "source"), true),
        )
    }

    fun save(context: Context, widgetId: Int, config: Config) {
        prefs(context).edit()
            .putInt(key(widgetId, "preset"), config.preset)
            .putFloat(key(widgetId, "size"), config.textSize)
            .putBoolean(key(widgetId, "source"), config.showSource)
            .apply()
    }

    /** 小组件实例被移除时清理它的配置 */
    fun clear(context: Context, widgetIds: IntArray) {
        val editor = prefs(context).edit()
        for (id in widgetIds) {
            editor.remove(key(id, "preset"))
                .remove(key(id, "size"))
                .remove(key(id, "source"))
        }
        editor.apply()
    }

    private fun key(widgetId: Int, field: String) = "w${widgetId}_$field"
}
