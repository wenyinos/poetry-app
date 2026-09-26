package me.javayhu.poetry.data

/**
 * 「发现」类查询的统一入口：搜索、按条件筛选、随机推荐。
 *
 * 全部在内存索引（7.2 万条）上完成，无需网络。索引按 poetryId 升序，
 * 每次筛选都是全表扫描 —— 实测约 10 ms 量级，放在后台线程完全够用，
 * 不值得为它额外维护倒排索引。
 */
object DiscoveryRepository {

    /**
     * 搜索。[SearchMode.CONTENT] 只覆盖内置的热门 5000 首 ——
     * 其余诗词的正文要到云端按需下载，不适合做全库正文检索。
     */
    fun search(keyword: String, mode: SearchMode, limit: Int = 300): List<PoemBrief> {
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()
        val matched = when (mode) {
            SearchMode.TITLE -> LocalDataSource.index()
                .filter { it.name.contains(q, ignoreCase = true) }
            SearchMode.AUTHOR -> LocalDataSource.index()
                .filter { it.author.contains(q, ignoreCase = true) }
            SearchMode.CONTENT -> LocalDataSource.hot().values
                .filter { it.content.contains(q) }
                .map { PoemBrief(it.id, it.name, it.author, it.dynasty, 0) }
        }
        return matched.sortedByDescending { it.star }.take(limit)
    }

    /** 按朝代与标签筛选；两者传 null 或空串表示不限 */
    fun filter(dynasty: String?, tag: String?, limit: Int = 300): List<PoemBrief> {
        val index = LocalDataSource.index()
        val base = if (tag.isNullOrEmpty()) {
            index
        } else {
            val ids = LocalDataSource.tagIndex()[tag]?.toHashSet() ?: return emptyList()
            index.filter { it.id in ids }
        }
        val narrowed =
            if (dynasty.isNullOrEmpty()) base else base.filter { it.dynasty == dynasty }
        return narrowed.sortedByDescending { it.star }.take(limit)
    }

    /** 随机推荐：从热度前 2000 首中取样，每次进入都不同 */
    fun recommend(limit: Int = 100): List<PoemBrief> =
        LocalDataSource.index()
            .sortedByDescending { it.star }
            .take(2000)
            .shuffled()
            .take(limit)
}
