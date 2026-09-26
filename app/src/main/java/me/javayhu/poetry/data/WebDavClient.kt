package me.javayhu.poetry.data

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 WebDAV 客户端，只实现同步需要的三个动作：MKCOL / GET / PUT。
 *
 * 刻意不引入第三方 WebDAV 库：这几个动作用 HTTP 直连即可完成，
 * 少一个依赖就少一份体积与混淆负担。兼容坚果云、Nextcloud 等标准实现。
 *
 * 所有方法都是阻塞的，须在后台线程调用。
 */
object WebDavClient {

    /** 同步目标；[url] 是 WebDAV 上的目录地址，如 https://dav.jianguoyun.com/dav/shijing */
    data class Config(
        val url: String,
        val user: String,
        val password: String,
    ) {
        val isUsable: Boolean get() = url.startsWith("http") && user.isNotBlank()
    }

    class WebDavException(message: String) : IOException(message)

    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "ShiJing/1.0"

    private fun dirUrl(config: Config): String = config.url.trim().trimEnd('/')

    private fun open(config: Config, url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            val token = Base64.encodeToString(
                "${config.user}:${config.password}".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
            setRequestProperty("Authorization", "Basic $token")
        }

    /** 读取文件；文件不存在返回 null，其余错误抛 [WebDavException] */
    fun get(config: Config, fileName: String): String? {
        val conn = open(config, "${dirUrl(config)}/$fileName", "GET")
        return try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                HttpURLConnection.HTTP_UNAUTHORIZED ->
                    throw WebDavException("认证失败，请检查用户名与密码")
                HttpURLConnection.HTTP_FORBIDDEN ->
                    throw WebDavException("无权访问该目录，请检查路径与授权")
                else -> throw WebDavException("读取失败（HTTP $code）")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 上传文件。目录不存在时先尝试创建再重试一次 */
    fun put(config: Config, fileName: String, content: String) {
        val url = "${dirUrl(config)}/$fileName"
        var code = doPut(config, url, content)
        if (code == HttpURLConnection.HTTP_CONFLICT) {
            ensureCollection(config)
            code = doPut(config, url, content)
        }
        when {
            code in 200..299 -> Unit
            code == HttpURLConnection.HTTP_UNAUTHORIZED ->
                throw WebDavException("认证失败，请检查用户名与密码")
            code == HttpURLConnection.HTTP_CONFLICT ->
                throw WebDavException("目录不存在，请先在网盘中创建该目录")
            else -> throw WebDavException("上传失败（HTTP $code）")
        }
    }

    private fun doPut(config: Config, url: String, content: String): Int {
        val conn = open(config, url, "PUT")
        return try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } catch (_: Exception) {
            -1
        } finally {
            conn.disconnect()
        }
    }

    /** 尽力创建目录；已存在（405）或服务端不支持时静默忽略 */
    private fun ensureCollection(config: Config) {
        val conn = open(config, dirUrl(config), "MKCOL")
        try {
            conn.responseCode
        } catch (_: Exception) {
            // 忽略：紧随其后的 PUT 会给出明确错误
        } finally {
            conn.disconnect()
        }
    }
}
