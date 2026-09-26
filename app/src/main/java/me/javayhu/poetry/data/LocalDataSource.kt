package me.javayhu.poetry.data

import me.javayhu.poetry.PoetryApp
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * 内置数据的读取入口。
 *
 * 数据以 gzip + JSONL（每行一条记录）存放在 assets/local/ 下。逐行解析后立即
 * 丢弃原始 JSON 对象，避免为 7 万条索引构建巨型 JSONArray 造成的内存峰值。
 *
 * 所有方法都是阻塞的，必须在后台线程调用；结果会常驻内存。
 */
object LocalDataSource {

    @Volatile private var indexCache: List<PoemBrief>? = null
    @Volatile private var hotCache: Map<Int, PoemContent>? = null
    @Volatile private var mingjuCache: List<Mingju>? = null
    @Volatile private var poetCache: Map<Int, PoetBrief>? = null
    @Volatile private var hotIdsCache: IntArray? = null

    /** 诗词索引，约 7.2 万条 */
    fun index(): List<PoemBrief> = indexCache ?: synchronized(this) {
        indexCache ?: loadIndex().also { indexCache = it }
    }

    /** 内置热门诗词正文，可离线阅读 */
    fun hot(): Map<Int, PoemContent> = hotCache ?: synchronized(this) {
        hotCache ?: loadHot().also { hotCache = it }
    }

    fun mingju(): List<Mingju> = mingjuCache ?: synchronized(this) {
        mingjuCache ?: loadMingju().also { mingjuCache = it }
    }

    fun poets(): Map<Int, PoetBrief> = poetCache ?: synchronized(this) {
        poetCache ?: loadPoets().also { poetCache = it }
    }

    /** 随机取一首内置诗词；无数据时返回 null。小组件与启动页用它保证离线可用 */
    fun randomHot(): PoemContent? {
        val hot = hot()
        if (hot.isEmpty()) return null
        val ids = hotIdsCache ?: synchronized(this) {
            hotIdsCache ?: hot.keys.toIntArray().also { hotIdsCache = it }
        }
        return hot[ids.random()]
    }

    private fun open(assetPath: String): InputStream =
        GZIPInputStream(PoetryApp.appContext.assets.open(assetPath))

    /** 逐行解析 gzip JSONL；stream 由调用方关闭 */
    internal fun forEachLine(assetPath: String, action: (JSONObject) -> Unit) {
        open(assetPath).use { gz ->
            BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    action(JSONObject(line))
                }
            }
        }
    }

    private fun loadIndex(): List<PoemBrief> {
        val list = ArrayList<PoemBrief>(73_000)
        forEachLine("local/index.jsonl.gz") { o ->
            list.add(
                PoemBrief(
                    id = o.optInt("i"),
                    name = o.optString("n"),
                    author = o.optString("a"),
                    dynasty = o.optString("d"),
                    star = o.optInt("s"),
                )
            )
        }
        return list
    }

    private fun loadHot(): Map<Int, PoemContent> {
        val map = HashMap<Int, PoemContent>(6_000)
        forEachLine("local/hot.jsonl.gz") { o ->
            val id = o.optInt("i")
            map[id] = PoemContent(
                id = id,
                name = o.optString("n"),
                author = o.optString("a"),
                dynasty = o.optString("d"),
                content = o.optString("c"),
            )
        }
        return map
    }

    private fun loadMingju(): List<Mingju> {
        val list = ArrayList<Mingju>(6_000)
        forEachLine("local/mingju.jsonl.gz") { o ->
            list.add(
                Mingju(
                    id = o.optInt("id"),
                    content = o.optString("content"),
                    poetName = o.optString("poetName"),
                    poetryName = o.optString("poetryName"),
                    poetryId = o.optInt("poetryId"),
                )
            )
        }
        return list
    }

    private fun loadPoets(): Map<Int, PoetBrief> {
        val map = HashMap<Int, PoetBrief>(3_200)
        forEachLine("local/poets_index.jsonl.gz") { o ->
            val id = o.optInt("i")
            map[id] = PoetBrief(
                id = id,
                name = o.optString("n"),
                dynasty = o.optString("d"),
                star = o.optInt("s"),
            )
        }
        return map
    }
}
