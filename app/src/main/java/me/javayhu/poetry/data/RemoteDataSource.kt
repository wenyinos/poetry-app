package me.javayhu.poetry.data

import me.javayhu.poetry.BuildConfig
import me.javayhu.poetry.PoetryApp
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 云端分片读取。
 *
 * 远端按 poetryId 区间分片（每 [SLICE_SIZE] 首一片，文件名 p0000.jsonl.gz…），
 * 因此用 id / SLICE_SIZE 即可直接定位分片，不需要额外的索引表。
 * 下载完成后写入 filesDir 缓存，之后该分片覆盖的 1000 首诗词均可离线阅读。
 */
object RemoteDataSource {

    const val SLICE_SIZE = 1000
    private const val TIMEOUT_MS = 20_000

    /** 是否配置了数据源；未配置时只能读内置数据 */
    val isConfigured: Boolean get() = BuildConfig.DATA_BASE_URL.isNotBlank()

    private val baseUrl: String get() = BuildConfig.DATA_BASE_URL.trim().trimEnd('/')

    private fun cacheFile(slice: Int): File =
        File(PoetryApp.appContext.filesDir, "poems/p%04d.jsonl.gz".format(slice))

    /** 该 id 所属分片是否已在本地缓存 */
    fun isCached(id: Int): Boolean = cacheFile(id / SLICE_SIZE).exists()

    /**
     * 取单首诗词：命中缓存不发请求，否则下载所属分片
     * （单片约 0.4 MB，一次覆盖 1000 首）。
     * 阻塞方法，须在后台线程调用；未配置数据源或下载失败时返回 null。
     */
    fun getPoem(id: Int): PoemContent? {
        val file = cacheFile(id / SLICE_SIZE)
        if (!file.exists()) {
            if (!isConfigured) return null
            if (!download("$baseUrl/poems/${file.name}", file)) return null
        }
        return readFrom(file, id)
    }

    private fun download(url: String, dest: File): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
            dest.parentFile?.mkdirs()
            // 先写临时文件再改名，避免下载中断留下损坏缓存
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(dest)
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun readFrom(file: File, wantId: Int): PoemContent? {
        GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: return null
                if (line.isEmpty()) continue
                val o = JSONObject(line)
                if (o.optInt("i") != wantId) continue
                return toPoem(o)
            }
        }
    }

    private fun toPoem(o: JSONObject): PoemContent {
        val arr = o.optJSONArray("t")
        val tags = if (arr == null) emptyList()
        else (0 until arr.length()).map { arr.optString(it) }
        return PoemContent(
            id = o.optInt("i"),
            name = o.optString("n"),
            author = o.optString("a"),
            dynasty = o.optString("d"),
            content = o.optString("c"),
            fanyi = o.optString("f"),
            shangxi = o.optString("x"),
            about = o.optString("b"),
            tags = tags,
            poetId = o.optInt("pi"),
        )
    }
}
