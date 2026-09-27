package me.javayhu.poetry.data

import android.content.Context
import me.javayhu.poetry.PoetryApp

/**
 * 全局偏好设置：主题模式、阅读字号、搜索与浏览历史。
 *
 * 全部存在 SharedPreferences 里，不需要数据库。历史记录都做了条数上限，
 * 避免无限增长。
 */
object AppSettings {

    // ---------------- 主题 ----------------

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    val THEME_LABELS = listOf("跟随系统", "浅色", "深色")

    // ---------------- 阅读字号 ----------------

    /** 四档正文字号（sp） */
    private val FONT_SIZES = listOf(15f, 17f, 19f, 22f)
    val FONT_SIZE_LABELS = listOf("小", "中", "大", "特大")
    private const val DEFAULT_FONT_LEVEL = 1

    private const val PREF = "settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_FONT = "font_level"
    private const val KEY_SEARCH = "search_history"
    private const val KEY_BROWSE = "browse_history"

    private const val MAX_SEARCH = 20
    private const val MAX_BROWSE = 50

    private fun prefs() =
        PoetryApp.appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    var themeMode: Int
        get() = prefs().getInt(KEY_THEME, THEME_SYSTEM)
        set(value) = prefs().edit().putInt(KEY_THEME, value).apply()

    var fontSizeLevel: Int
        get() = prefs().getInt(KEY_FONT, DEFAULT_FONT_LEVEL)
        set(value) = prefs().edit()
            .putInt(KEY_FONT, value.coerceIn(0, FONT_SIZES.size - 1))
            .apply()

    /** 当前正文与译文使用的字号（sp） */
    val bodyTextSize: Float
        get() = FONT_SIZES[fontSizeLevel.coerceIn(0, FONT_SIZES.size - 1)]

    // ---------------- 搜索历史 ----------------

    /**
     * 用换行拼接保存，而不是 StringSet —— Set 不保证顺序，
     * 而这里「最近搜的排最前」正是要保住的语义。
     */
    fun searchHistory(): List<String> =
        prefs().getString(KEY_SEARCH, "").orEmpty()
            .split('\n').filter { it.isNotBlank() }

    /** 新搜索的词排在最前，重复的会被提到最前而不是重复记录 */
    fun addSearchHistory(keyword: String) {
        val word = keyword.trim()
        if (word.isEmpty()) return
        val list = searchHistory().filterNot { it == word }.toMutableList()
        list.add(0, word)
        prefs().edit()
            .putString(KEY_SEARCH, list.take(MAX_SEARCH).joinToString("\n"))
            .apply()
    }

    fun clearSearchHistory() {
        prefs().edit().remove(KEY_SEARCH).apply()
    }

    // ---------------- 浏览历史 ----------------

    /** 最近看过的诗词 id，最新的在最前 */
    fun browseHistory(): List<Int> =
        prefs().getString(KEY_BROWSE, "").orEmpty()
            .split(',').mapNotNull { it.toIntOrNull() }

    fun addBrowseHistory(poetryId: Int) {
        if (poetryId <= 0) return
        val list = browseHistory().filterNot { it == poetryId }.toMutableList()
        list.add(0, poetryId)
        prefs().edit()
            .putString(KEY_BROWSE, list.take(MAX_BROWSE).joinToString(","))
            .apply()
    }

    fun clearBrowseHistory() {
        prefs().edit().remove(KEY_BROWSE).apply()
    }
}
