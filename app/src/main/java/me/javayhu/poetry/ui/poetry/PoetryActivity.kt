package me.javayhu.poetry.ui.poetry

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import me.javayhu.poetry.R
import me.javayhu.poetry.data.FavoriteRepository
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.data.PoemContent
import me.javayhu.poetry.data.PoemRepository
import me.javayhu.poetry.databinding.ActivityPoetryBinding
import java.util.concurrent.Executors

/** 诗词详情页：内置热门直接命中，其余按分片从云端取回并缓存 */
class PoetryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPoetryBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** 当前展示的诗，用于收藏；正文未取回时退回索引里的概要信息 */
    private var favoriteTarget: PoemBrief? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoetryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra(EXTRA_ID, 0)
        binding.toolbar.title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_poetry)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_favorite) {
                toggleFavorite()
                true
            } else {
                false
            }
        }
        binding.progress.visibility = View.VISIBLE

        io.execute {
            val result = PoemRepository.getPoem(id)
            main.post {
                if (isFinishing) return@post
                binding.progress.visibility = View.GONE
                render(result)
                refreshFavoriteIcon()
            }
        }
    }

    private fun render(result: PoemRepository.Result) {
        when (result) {
            is PoemRepository.Result.Full -> {
                val p = result.poem
                favoriteTarget = PoemBrief(p.id, p.name, p.author, p.dynasty, 0)
                bind(p)
            }
            is PoemRepository.Result.Brief -> {
                val brief = result.brief
                favoriteTarget = brief
                binding.poetryTitle.text = brief.name
                binding.poetryMeta.text = meta(brief.dynasty, brief.author)
                binding.poetryContent.text = getString(
                    if (result.canFetch) R.string.poetry_fetch_failed
                    else R.string.poetry_offline_only
                )
                hideExtras()
            }
            PoemRepository.Result.NotFound -> {
                binding.poetryContent.text = getString(R.string.poetry_not_found)
                hideExtras()
            }
        }
    }

    private fun bind(p: PoemContent) {
        binding.poetryTitle.text = p.name
        binding.poetryMeta.text = meta(p.dynasty, p.author)
        binding.poetryContent.text = p.content

        binding.poetryTags.text = p.tags.joinToString("　")
        binding.poetryTags.visibility = if (p.tags.isEmpty()) View.GONE else View.VISIBLE

        bindSection(binding.fanyiBlock, binding.fanyiText, p.fanyi)
        bindSection(binding.shangxiBlock, binding.shangxiText, p.shangxi)
        bindSection(binding.aboutBlock, binding.aboutText, p.about)
    }

    /** 译文/赏析/背景只有约一成诗词具备，空段落整块隐藏 */
    private fun bindSection(block: View, text: TextView, content: String) {
        if (content.isBlank()) {
            block.visibility = View.GONE
        } else {
            block.visibility = View.VISIBLE
            text.text = content.trim()
        }
    }

    private fun hideExtras() {
        binding.poetryTags.visibility = View.GONE
        binding.fanyiBlock.visibility = View.GONE
        binding.shangxiBlock.visibility = View.GONE
        binding.aboutBlock.visibility = View.GONE
    }

    // ---------------- 收藏 ----------------

    private fun toggleFavorite() {
        val target = favoriteTarget ?: return
        io.execute {
            val nowFavorite = FavoriteRepository.toggle(
                target.id, target.name, target.author, target.dynasty,
            )
            main.post {
                if (isFinishing) return@post
                refreshFavoriteIcon()
                toast(
                    getString(
                        if (nowFavorite) R.string.favorite_added else R.string.favorite_removed
                    )
                )
            }
        }
    }

    private fun refreshFavoriteIcon() {
        val target = favoriteTarget ?: return
        io.execute {
            val isFavorite = FavoriteRepository.contains(target.id)
            main.post {
                if (isFinishing) return@post
                binding.toolbar.menu.findItem(R.id.action_favorite)?.setIcon(
                    if (isFavorite) R.drawable.ic_star_fill_white_24dp
                    else R.drawable.ic_star_border_white_24dp
                )
            }
        }
    }

    private fun meta(dynasty: String, author: String): String =
        listOf(dynasty, author).filter { it.isNotEmpty() }.joinToString(" · ")

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "poetry_id"
        const val EXTRA_TITLE = "poetry_title"
    }
}
