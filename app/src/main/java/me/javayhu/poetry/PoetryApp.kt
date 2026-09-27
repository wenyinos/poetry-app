package me.javayhu.poetry

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import me.javayhu.poetry.data.AppSettings
import me.javayhu.poetry.util.EdgeToEdgeCallbacks

class PoetryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        applyTheme()
        // 在应用层统一处理异形屏与系统栏占位，各页面无需改动
        registerActivityLifecycleCallbacks(EdgeToEdgeCallbacks())
    }

    companion object {
        /** 供数据层读取 assets 使用；Application.onCreate 之后一定可用 */
        lateinit var appContext: Context
            private set

        /**
         * 应用深浅色偏好。
         *
         * 切换后调用它即可立即生效 —— AppCompatDelegate 会重建当前 Activity，
         * 因此各页面不必自己处理主题变化。
         */
        fun applyTheme() {
            AppCompatDelegate.setDefaultNightMode(
                when (AppSettings.themeMode) {
                    AppSettings.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    AppSettings.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
