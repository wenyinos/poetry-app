package me.javayhu.poetry.data

import me.javayhu.poetry.PoetryApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 收藏的本地持久化。
 *
 * 存成单个 JSON 文件，字段名做最短化处理——这份内容会被整体同步到 WebDAV，
 * 紧凑格式能直接减小同步流量。
 *
 * 文件形如：
 * {"v":1,"t":1700000000000,"items":[{"id":7722,"n":"将进酒","a":"李白","d":"唐代","t":1700000000000}]}
 */
object FavoriteStore {

    private const val FILE_NAME = "favorites.json"
    private const val VERSION = 1

    /** 一份收藏快照；[updatedAt] 用于同步时判断新旧 */
    data class Snapshot(
        val updatedAt: Long,
        val items: List<Favorite>,
    ) {
        fun toJson(): String {
            val arr = JSONArray()
            for (f in items) {
                arr.put(
                    JSONObject()
                        .put("id", f.id)
                        .put("n", f.name)
                        .put("a", f.author)
                        .put("d", f.dynasty)
                        .put("t", f.addedAt)
                )
            }
            return JSONObject()
                .put("v", VERSION)
                .put("t", updatedAt)
                .put("items", arr)
                .toString()
        }

        companion object {
            /** 解析失败时返回空快照，避免一条坏数据导致收藏整体不可用 */
            fun parse(text: String): Snapshot = try {
                val root = JSONObject(text)
                val arr = root.optJSONArray("items") ?: JSONArray()
                val items = ArrayList<Favorite>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    items.add(
                        Favorite(
                            id = o.optInt("id"),
                            name = o.optString("n"),
                            author = o.optString("a"),
                            dynasty = o.optString("d"),
                            addedAt = o.optLong("t"),
                        )
                    )
                }
                Snapshot(root.optLong("t"), items)
            } catch (_: Exception) {
                Snapshot(0L, emptyList())
            }
        }
    }

    private val file: File get() = File(PoetryApp.appContext.filesDir, FILE_NAME)

    @Synchronized
    fun load(): Snapshot =
        if (!file.exists()) Snapshot(0L, emptyList())
        else Snapshot.parse(runCatching { file.readText() }.getOrDefault(""))

    @Synchronized
    fun save(snapshot: Snapshot) {
        runCatching { file.writeText(snapshot.toJson()) }
    }
}
