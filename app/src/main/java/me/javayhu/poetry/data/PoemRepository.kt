package me.javayhu.poetry.data

/**
 * 取诗词详情的统一入口：内置热门 → 云端分片 → 本地索引兜底。
 */
object PoemRepository {

    sealed class Result {
        /** 有完整正文 */
        data class Full(val poem: PoemContent) : Result()

        /** 只有索引里的概要，正文尚未取得；[canFetch] 表示是否配置了数据源可重试 */
        data class Brief(val brief: PoemBrief, val canFetch: Boolean) : Result()

        object NotFound : Result()
    }

    /**
     * 阻塞方法，须在后台线程调用。
     *
     * 索引按 poetryId 升序排列（生成端保证），此处用二分查找避免全表扫描。
     */
    fun getPoem(id: Int): Result {
        LocalDataSource.hot()[id]?.let { return Result.Full(it) }
        RemoteDataSource.getPoem(id)?.let { return Result.Full(it) }

        val brief = findBrief(id) ?: return Result.NotFound
        return Result.Brief(brief, RemoteDataSource.isConfigured)
    }

    /** 在内存索引里按 id 二分查找 */
    fun findBrief(id: Int): PoemBrief? {
        val list = LocalDataSource.index()
        var lo = 0
        var hi = list.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = list[mid].id
            when {
                v == id -> return list[mid]
                v < id -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return null
    }

    /** 按热度取前 [limit] 首，用于首页列表 */
    fun featured(limit: Int): List<PoemBrief> =
        LocalDataSource.index().sortedByDescending { it.star }.take(limit)
}
