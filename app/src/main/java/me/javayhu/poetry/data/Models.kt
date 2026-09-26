package me.javayhu.poetry.data

/** 索引条目：列表与搜索用，字段最短化以压缩体积 */
data class PoemBrief(
    val id: Int,
    val name: String,
    val author: String,
    val dynasty: String,
    val star: Int,
)

/** 诗词正文，来自内置热门集或云端分片 */
data class PoemContent(
    val id: Int,
    val name: String,
    val author: String,
    val dynasty: String,
    val content: String,
    val fanyi: String = "",
    val shangxi: String = "",
    val about: String = "",
    val tags: List<String> = emptyList(),
    val poetId: Int = 0,
)

/** 名句 */
data class Mingju(
    val id: Int,
    val content: String,
    val poetName: String,
    val poetryName: String,
    val poetryId: Int,
)

/** 诗人索引 */
data class PoetBrief(
    val id: Int,
    val name: String,
    val dynasty: String,
    val star: Int,
)

/**
 * 收藏条目。
 *
 * 冗余保存诗名与作者，使收藏列表无需回查索引即可渲染，
 * 导入导出后也能独立使用。
 */
data class Favorite(
    val id: Int,
    val name: String,
    val author: String,
    val dynasty: String,
    val addedAt: Long,
)

/** 诗人详情，来自云端分片（内置的只有 [PoetBrief] 索引） */
data class PoetDetail(
    val id: Int,
    val name: String,
    val dynasty: String,
    val desc: String,
    val content: String,
    val image: String,
    val star: Int,
)

/** 搜索模式，对应原版的「默认 / 作者 / 诗文」三档 */
enum class SearchMode {
    /** 按诗名搜 */
    TITLE,

    /** 按作者搜 */
    AUTHOR,

    /** 按正文搜（仅覆盖内置的热门集） */
    CONTENT,
}
