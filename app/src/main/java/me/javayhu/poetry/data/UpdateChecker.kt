package me.javayhu.poetry.data

import me.javayhu.poetry.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新检查。
 *
 * 直接查 GitHub Releases API —— 不引入第三方更新 SDK，也不上报任何设备信息，
 * 与本项目「无埋点、无后端」的定位一致（请求只会暴露 IP，和浏览网页一样）。
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/wenyinos/poetry-app/releases/latest"
    private const val TIMEOUT_MS = 12_000

    data class Release(val version: String, val pageUrl: String)

    /**
     * 有新版本时返回它；已是最新或检查失败都返回 null。
     * 阻塞方法，须在后台线程调用。
     */
    fun check(): Release? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub 要求带 User-Agent，顺便把当前版本一起报上去
                setRequestProperty("User-Agent", "ShiJing/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name").removePrefix("v")
            val page = obj.optString("html_url")
            if (tag.isEmpty() || page.isEmpty() || !isNewer(tag)) null
            else Release(tag, page)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** 逐段比较数字，避免字符串比较把「4.10」判成小于「4.9」 */
    private fun isNewer(candidate: String): Boolean {
        val remote = candidate.split('.').mapNotNull { it.toIntOrNull() }
        val local = BuildConfig.VERSION_NAME.split('.').mapNotNull { it.toIntOrNull() }
        if (remote.isEmpty()) return false
        for (i in 0 until maxOf(remote.size, local.size)) {
            val a = remote.getOrElse(i) { 0 }
            val b = local.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
