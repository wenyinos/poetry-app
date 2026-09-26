package me.javayhu.poetry.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import me.javayhu.poetry.PoetryApp
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 收藏的唯一入口：本地读写 + WebDAV 同步。
 *
 * 同步策略采用**合并**而非覆盖：两台设备各自新增的收藏都会保留下来，
 * 同一首诗两边都有时保留较早的收藏时间。代价是在一台设备上取消收藏后，
 * 另一台设备同步时可能让它"复活"——收藏是低价值、易重建的数据，
 * 少丢数据的收益大于删除不同步的代价。确实需要单向覆盖时，用
 * [forceUpload] / [forceDownload]。
 *
 * 除 [loadConfig] / [saveConfig] / [snapshot] 外，其余方法涉及磁盘或网络 IO，
 * 须在后台线程调用；[addListener] 注册的回调总在主线程触发。
 */
object FavoriteRepository {

    /** 云端同步文件名，设置页测试连接时也用它 */
    const val REMOTE_FILE = "favorites.json"
    private const val PREF_NAME = "webdav"
    private const val KEY_URL = "url"
    private const val KEY_USER = "user"
    private const val KEY_PASSWORD = "password"

    sealed class SyncResult {
        /** [addedLocally] 从远端合并进来的数量，[addedRemotely] 上传到远端的数量 */
        data class Success(val total: Int, val addedLocally: Int, val addedRemotely: Int) : SyncResult()
        data class Failed(val reason: String) : SyncResult()
        object NotConfigured : SyncResult()
    }

    @Volatile private var cache: FavoriteStore.Snapshot? = null
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------------- 监听 ----------------

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        mainHandler.post { listeners.forEach { it() } }
    }

    // ---------------- 本地收藏 ----------------

    fun snapshot(): FavoriteStore.Snapshot = cache ?: synchronized(this) {
        cache ?: FavoriteStore.load().also { cache = it }
    }

    fun list(): List<Favorite> = snapshot().items

    fun contains(id: Int): Boolean = snapshot().items.any { it.id == id }

    /** 切换收藏，返回切换后的状态（true = 已收藏） */
    fun toggle(poem: PoemContent): Boolean =
        toggle(poem.id, poem.name, poem.author, poem.dynasty)

    /** 仅知道概要信息（索引里的条目）时也能收藏 */
    fun toggle(id: Int, name: String, author: String, dynasty: String): Boolean {
        val entry = Favorite(
            id = id,
            name = name,
            author = author,
            dynasty = dynasty,
            addedAt = System.currentTimeMillis(),
        )
        val current = snapshot()
        val existed = current.items.any { it.id == entry.id }
        val items = if (existed) {
            current.items.filterNot { it.id == entry.id }
        } else {
            (current.items + entry).sortedByDescending { it.addedAt }
        }
        persist(FavoriteStore.Snapshot(System.currentTimeMillis(), items))
        return !existed
    }

    fun remove(id: Int) {
        val current = snapshot()
        if (current.items.none { it.id == id }) return
        persist(
            FavoriteStore.Snapshot(
                System.currentTimeMillis(),
                current.items.filterNot { it.id == id },
            )
        )
    }

    private fun persist(snapshot: FavoriteStore.Snapshot) {
        cache = snapshot
        FavoriteStore.save(snapshot)
        notifyChanged()
    }

    private fun replaceWith(snapshot: FavoriteStore.Snapshot) {
        cache = snapshot
        FavoriteStore.save(snapshot)
        notifyChanged()
    }

    // ---------------- WebDAV 配置 ----------------

    private fun prefs() =
        PoetryApp.appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): WebDavClient.Config {
        val p = prefs()
        return WebDavClient.Config(
            url = p.getString(KEY_URL, "").orEmpty(),
            user = p.getString(KEY_USER, "").orEmpty(),
            password = p.getString(KEY_PASSWORD, "").orEmpty(),
        )
    }

    fun saveConfig(config: WebDavClient.Config) {
        prefs().edit()
            .putString(KEY_URL, config.url.trim())
            .putString(KEY_USER, config.user.trim())
            .putString(KEY_PASSWORD, config.password)
            .apply()
    }

    fun isConfigured(): Boolean = loadConfig().isUsable

    // ---------------- 同步 ----------------

    /** 双向合并同步 */
    fun sync(): SyncResult {
        val config = loadConfig()
        if (!config.isUsable) return SyncResult.NotConfigured
        return runSync { local, remote ->
            merge(local.items, remote?.items ?: emptyList())
        }
    }

    /** 用本地收藏覆盖远端 */
    fun forceUpload(): SyncResult {
        val config = loadConfig()
        if (!config.isUsable) return SyncResult.NotConfigured
        return runSync { local, _ -> local.items }
    }

    /** 用远端收藏覆盖本地 */
    fun forceDownload(): SyncResult {
        val config = loadConfig()
        if (!config.isUsable) return SyncResult.NotConfigured
        return runSync { _, remote -> remote?.items ?: emptyList() }
    }

    private inline fun runSync(
        resolve: (FavoriteStore.Snapshot, FavoriteStore.Snapshot?) -> List<Favorite>,
    ): SyncResult {
        val config = loadConfig()
        return try {
            val local = snapshot()
            val remoteText = WebDavClient.get(config, REMOTE_FILE)
            val remote = remoteText?.let { FavoriteStore.Snapshot.parse(it) }

            val merged = resolve(local, remote)
            val localIds = local.items.map { it.id }.toSet()
            val remoteIds = remote?.items?.map { it.id }?.toSet() ?: emptySet()
            val mergedIds = merged.map { it.id }.toSet()

            val addedLocally = mergedIds.count { it !in localIds }
            val addedRemotely = mergedIds.count { it !in remoteIds }
            val removedLocally = localIds.count { it !in mergedIds }
            val removedRemotely = remoteIds.count { it !in mergedIds }

            val localChanged = addedLocally > 0 || removedLocally > 0
            val remoteChanged = addedRemotely > 0 || removedRemotely > 0 || remote == null

            if (localChanged) {
                replaceWith(FavoriteStore.Snapshot(System.currentTimeMillis(), merged))
            }
            if (remoteChanged) {
                WebDavClient.put(
                    config,
                    REMOTE_FILE,
                    FavoriteStore.Snapshot(System.currentTimeMillis(), merged).toJson(),
                )
            }
            SyncResult.Success(merged.size, addedLocally, addedRemotely)
        } catch (e: WebDavClient.WebDavException) {
            SyncResult.Failed(e.message ?: "同步失败")
        } catch (e: Exception) {
            SyncResult.Failed("网络异常：${e.message ?: "未知错误"}")
        }
    }

    /** 两边的并集；同一首保留较早的收藏时间 */
    private fun merge(a: List<Favorite>, b: List<Favorite>): List<Favorite> =
        (a + b).groupBy { it.id }
            .map { (_, group) -> group.minByOrNull { it.addedAt }!! }
            .sortedByDescending { it.addedAt }
}
