package me.javayhu.poetry.data

/**
 * 诗词专题。
 *
 * 原版的专题是编辑策划的合集，数据源里没有对应字段，无法还原。
 * 这里改用**数据本身能支撑**的维度自动生成：热门榜单、按朝代、按主题标签 ——
 * 都能直接从内置数据算出来，不依赖人工维护。
 *
 * 数据源里还有一份「每日诗词」索引（3,067 天），但它存的是 LeanCloud 的
 * objectId，而诗词表本身没有该字段、服务端又已下线，无法与诗词关联，
 * 因此没有采用。
 */
object TopicRepository {

    /** 专题类型 */
    enum class Kind { POPULAR, DYNASTY, TAG }

    data class Topic(
        val title: String,
        val hint: String,
        val kind: Kind,
        val value: String,
    )

    private const val DYNASTY_TOPICS = 6
    private const val TAG_TOPICS = 8
    private const val POPULAR_LIMIT = 300
    private const val TOPIC_LIMIT = 300

    fun topics(): List<Topic> {
        val list = ArrayList<Topic>()

        list.add(
            Topic(
                title = "热门榜单",
                hint = "最受欢迎的 $POPULAR_LIMIT 首",
                kind = Kind.POPULAR,
                value = "",
            )
        )

        // 按作品数量取几个主要朝代
        LocalDataSource.dynasties()
            .sortedByDescending { it.second }
            .take(DYNASTY_TOPICS)
            .forEach { (name, count) ->
                list.add(
                    Topic(
                        title = "${name}诗词",
                        hint = "$count 首",
                        kind = Kind.DYNASTY,
                        value = name,
                    )
                )
            }

        // 按覆盖诗词数取几个主要主题
        LocalDataSource.tagIndex().entries
            .sortedByDescending { it.value.size }
            .take(TAG_TOPICS)
            .forEach { (tag, ids) ->
                list.add(
                    Topic(
                        title = tag,
                        hint = "${ids.size} 首",
                        kind = Kind.TAG,
                        value = tag,
                    )
                )
            }

        return list
    }

    /** 取某个专题下的诗词；阻塞方法，须在后台线程调用 */
    fun poemsOf(topic: Topic): List<PoemBrief> = when (topic.kind) {
        Kind.POPULAR -> LocalDataSource.index()
            .sortedByDescending { it.star }
            .take(POPULAR_LIMIT)
        Kind.DYNASTY -> LocalDataSource.index()
            .filter { it.dynasty == topic.value }
            .sortedByDescending { it.star }
            .take(TOPIC_LIMIT)
        Kind.TAG -> {
            val ids = LocalDataSource.tagIndex()[topic.value]?.toHashSet() ?: emptySet()
            LocalDataSource.index()
                .filter { it.id in ids }
                .sortedByDescending { it.star }
                .take(TOPIC_LIMIT)
        }
    }
}
