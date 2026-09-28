package me.javayhu.poetry.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import me.javayhu.poetry.R

/**
 * 小组件基类：统一处理「后台线程取诗 → 逐个实例更新」的流程。
 *
 * 数据来自内置热门集，整个刷新过程不需要网络；刷新周期由各自的
 * appwidget-provider 配置决定（3 小时 / 1 小时 / 24 小时）。
 */
abstract class BasePoetryWidgetProvider : AppWidgetProvider() {

    /** 由子类决定这个小组件长什么样 */
    protected abstract fun render(context: Context, widgetId: Int): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WidgetSupport.io.execute {
            try {
                for (id in ids) {
                    manager.updateAppWidget(id, render(context, id))
                }
            } catch (_: Exception) {
                // 数据读取失败时保留上一次内容，等待下次刷新
            } finally {
                pending.finish()
            }
        }
    }
}

/** 默认款：品牌浅蓝背景，4 行，3 小时刷新 */
class PoetryWidgetProvider : BasePoetryWidgetProvider() {
    override fun render(context: Context, widgetId: Int) =
        WidgetSupport.simple(context, R.layout.widget_poetry, 4)
}

/** 多行款：同样式但容纳 8 行，适合放大的尺寸 */
class LinesPoetryWidgetProvider : BasePoetryWidgetProvider() {
    override fun render(context: Context, widgetId: Int) =
        WidgetSupport.simple(context, R.layout.widget_poetry_lines, 8)
}

/** 定制款：配色与字号可配置，3 小时刷新 */
class CustomPoetryWidgetProvider : BasePoetryWidgetProvider() {
    override fun render(context: Context, widgetId: Int) =
        WidgetSupport.custom(context, widgetId)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // 实例被移除时清掉它的配置，避免残留
        WidgetConfig.clear(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }
}

/** 定制款·整点：每小时换一首 */
class CustomHourPoetryWidgetProvider : BasePoetryWidgetProvider() {
    override fun render(context: Context, widgetId: Int) =
        WidgetSupport.custom(context, widgetId)
}

/** 定制款·每日：每天换一首 */
class CustomDayPoetryWidgetProvider : BasePoetryWidgetProvider() {
    override fun render(context: Context, widgetId: Int) =
        WidgetSupport.custom(context, widgetId)
}
