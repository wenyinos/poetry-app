package me.javayhu.poetry.data

/**
 * 诗人取数入口。
 *
 * 列表走内置索引（3,154 位全量，离线可用），详情（简介与生平）按分片
 * 从云端取回并缓存，与诗词详情同一套分层策略。
 */
object PoetRepository {

    sealed class Result {
        data class Full(val poet: PoetDetail) : Result()

        /** 只有索引里的概要；[canFetch] 表示是否配置了数据源可重试 */
        data class Brief(val brief: PoetBrief, val canFetch: Boolean) : Result()

        object NotFound : Result()
    }

    /** 诗人列表，按热度排序 */
    fun featured(limit: Int): List<PoetBrief> =
        LocalDataSource.poets().values.sortedByDescending { it.star }.take(limit)

    /** 按姓名搜诗人；关键词为空时等同于 [featured] */
    fun search(keyword: String, limit: Int): List<PoetBrief> {
        val q = keyword.trim()
        if (q.isEmpty()) return featured(limit)
        return LocalDataSource.poets().values
            .filter { it.name.contains(q, ignoreCase = true) }
            .sortedByDescending { it.star }
            .take(limit)
    }

    /** 某位诗人的作品。索引里作者字段是姓名，直接等值匹配即可 */
    fun poemsOf(authorName: String, limit: Int): List<PoemBrief> {
        if (authorName.isEmpty()) return emptyList()
        val matched = LocalDataSource.index()
            .filter { it.author == authorName }
            .sortedByDescending { it.star }
        return if (limit > 0) matched.take(limit) else matched
    }

    @Volatile private var worksCountCache: Map<String, Int>? = null

    /**
     * 作者名 → 作品数。
     *
     * 诗人列表每一项都要显示作品数，若在列表滚动时逐个统计就要反复扫描
     * 7.2 万条索引，因此在这里一次性聚合好供 UI 查表。
     */
    fun worksCount(): Map<String, Int> = worksCountCache ?: synchronized(this) {
        worksCountCache ?: buildWorksCount().also { worksCountCache = it }
    }

    private fun buildWorksCount(): Map<String, Int> {
        val counter = HashMap<String, Int>(4_000)
        for (p in LocalDataSource.index()) {
            if (p.author.isEmpty()) continue
            counter[p.author] = (counter[p.author] ?: 0) + 1
        }
        return counter
    }

    /** 按姓名精确查找诗人；诗词索引里只有作者姓名，靠它反查诗人 id */
    fun findByName(name: String): PoetBrief? {
        if (name.isEmpty()) return null
        return LocalDataSource.poets().values.firstOrNull { it.name == name }
    }

    /** 取诗人详情：云端优先，未配置或下载失败时退回索引信息 */
    fun getPoet(id: Int): Result {
        RemoteDataSource.getPoet(id)?.let { return Result.Full(it) }
        val brief = LocalDataSource.poets()[id] ?: return Result.NotFound
        return Result.Brief(brief, RemoteDataSource.isConfigured)
    }
}
