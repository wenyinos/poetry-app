package me.javayhu.poetry

import android.app.Application
import android.content.Context
import me.javayhu.poetry.util.EdgeToEdgeCallbacks

class PoetryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // 在应用层统一处理异形屏与系统栏占位，各页面无需改动
        registerActivityLifecycleCallbacks(EdgeToEdgeCallbacks())
    }

    companion object {
        /** 供数据层读取 assets 使用；Application.onCreate 之后一定可用 */
        lateinit var appContext: Context
            private set
    }
}
