package me.javayhu.poetry

import android.app.Application
import android.content.Context

class PoetryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        /** 供数据层读取 assets 使用；Application.onCreate 之后一定可用 */
        lateinit var appContext: Context
            private set
    }
}
