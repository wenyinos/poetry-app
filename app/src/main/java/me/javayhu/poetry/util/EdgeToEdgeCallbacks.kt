package me.javayhu.poetry.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import me.javayhu.poetry.R

/**
 * 异形屏与系统栏适配。
 *
 * targetSdk 35 起 Android 会强制 edge-to-edge：窗口铺满整屏，状态栏、导航栏
 * 以及刘海/挖孔区域都会盖在内容之上。因此需要主动让位：
 *
 * - 有工具栏的页面：工具栏向下让出状态栏（含刘海）高度，并把自身高度加上这一段，
 *   于是工具栏的蓝色背景自然延伸到状态栏区域，视觉上仍是完整的一条
 * - 无工具栏的页面（启动页）：根布局整体让位
 * - 所有页面：根布局底部让出导航栏高度，避免底部按钮被系统栏遮住
 *
 * 通过 ActivityLifecycleCallbacks 在 Application 层统一挂载，各页面无需改动。
 */
internal class EdgeToEdgeCallbacks : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val decor = activity.window?.decorView ?: return
        // onCreate 期间布局可能尚未挂载，等一帧再取视图
        decor.post { apply(activity) }
    }

    private fun apply(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) as? View ?: return
        val toolbar = activity.findViewById<View>(R.id.toolbar)
        if (toolbar != null) {
            toolbar.padForStatusBar()
            root.padForNavigationBar()
        } else {
            root.padForSystemBars()
        }
    }

    /** 工具栏让出状态栏与刘海高度，并相应加高，使背景延伸上去 */
    private fun View.padForStatusBar() {
        val base = actionBarHeight()
        setTag(R.id.edge_to_edge_base_height, base)
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            v.updatePadding(top = top)
            v.updateLayoutParams { height = base + top }
            insets
        }
    }

    /** 根布局底部让出导航栏高度 */
    private fun View.padForNavigationBar() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }
    }

    /** 没有工具栏的页面（启动页）整体让出系统栏与刘海区域 */
    private fun View.padForSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    /** 从主题取 actionBarSize，比读 layoutParams 更可靠（后者可能尚未解析） */
    private fun View.actionBarHeight(): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.actionBarSize, value, true)) {
            TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
        } else {
            (56 * resources.displayMetrics.density).toInt()
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
