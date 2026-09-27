package me.javayhu.poetry.ui.poetry

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import me.javayhu.poetry.R
import me.javayhu.poetry.data.AppSettings
import me.javayhu.poetry.data.FavoriteRepository
import me.javayhu.poetry.data.PoemBrief
import me.javayhu.poetry.data.PoemContent
import me.javayhu.poetry.data.PoemRepository
import me.javayhu.poetry.data.PoetRepository
import me.javayhu.poetry.databinding.ActivityPoetryBinding
import me.javayhu.poetry.ui.poet.PoetActivity
import me.javayhu.poetry.ui.share.ShareActivity
import java.util.Locale
import java.util.concurrent.Executors

/** 诗词详情页：内置热门直接命中，其余按分片从云端取回并缓存 */
class PoetryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPoetryBinding
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** 当前展示的诗，用于收藏；正文未取回时退回索引里的概要信息 */
    private var favoriteTarget: PoemBrief? = null

    /** 当前作者名，用于跳转诗人页 */
    private var currentAuthor: String = ""

    /** 完整正文，供复制与朗读使用；只有概要时为空 */
    private var currentPoem: PoemContent? = null

    private var tts: TextToSpeech? = null
    private var pendingSpeech: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoetryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra(EXTRA_ID, 0)
        binding.toolbar.title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_poetry)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorite -> {
                    toggleFavorite()
                    true
                }
                R.id.action_share -> {
                    openShare()
                    true
                }
                else -> false
            }
        }
        binding.progress.visibility = View.VISIBLE
        // 点作者名查看诗人简介；长按正文可复制或朗读
        binding.poetryMeta.setOnClickListener { openPoet() }
        binding.poetryContent.setOnLongClickListener {
            showTextActions()
            true
        }
        AppSettings.addBrowseHistory(id)

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
                currentAuthor = p.author
                currentPoem = p
                bind(p)
            }
            is PoemRepository.Result.Brief -> {
                val brief = result.brief
                favoriteTarget = brief
                currentAuthor = brief.author
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
        applyFontSize()
    }

    /** 正文字号取自设置，译文/赏析/背景跟随同一档 */
    private fun applyFontSize() {
        val size = AppSettings.bodyTextSize
        listOf(
            binding.poetryContent, binding.fanyiText,
            binding.shangxiText, binding.aboutText,
        ).forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, size) }
    }

    // ---------------- 复制与朗读 ----------------

    private fun showTextActions() {
        if (currentPoem == null) return
        val actions = arrayOf(
            getString(R.string.action_copy),
            getString(R.string.action_read_aloud),
        )
        AlertDialog.Builder(this)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> copyPoem()
                    1 -> speakPoem()
                }
            }
            .show()
    }

    private fun copyPoem() {
        val p = currentPoem ?: return
        val meta = listOf(p.dynasty, p.author).filter { it.isNotEmpty() }.joinToString(" · ")
        val text = buildString {
            append(p.name).append('\n')
            if (meta.isNotEmpty()) append(meta).append("\n\n")
            append(p.content)
        }
        val manager = getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText(p.name, text))
        toast(getString(R.string.copied))
    }

    /** 首次调用才初始化 TTS，就绪后再补上待朗读的内容 */
    private fun speakPoem() {
        val p = currentPoem ?: return
        val text = "${p.name}。${listOf(p.dynasty, p.author).filter { it.isNotEmpty() }.joinToString("，")}。${p.content}"
        val engine = tts
        if (engine == null) {
            pendingSpeech = text
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINA
                    pendingSpeech?.let {
                        tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
                    }
                } else {
                    toast(getString(R.string.tts_unavailable))
                }
                pendingSpeech = null
            }
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
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

    /** 打开分享页，把这首诗渲染成竖版图片 */
    private fun openShare() {
        val target = favoriteTarget ?: return
        startActivity(
            Intent(this, ShareActivity::class.java).putExtra(ShareActivity.EXTRA_ID, target.id)
        )
    }

    /** 诗词索引里只有作者姓名，按名字反查诗人条目再跳转 */
    private fun openPoet() {        val author = currentAuthor
        if (author.isEmpty()) return
        io.execute {
            val poet = PoetRepository.findByName(author)
            main.post {
                if (isFinishing) return@post
                if (poet == null) {
                    toast(getString(R.string.poet_not_found))
                } else {
                    startActivity(
                        Intent(this, PoetActivity::class.java)
                            .putExtra(PoetActivity.EXTRA_ID, poet.id)
                            .putExtra(PoetActivity.EXTRA_NAME, poet.name)
                    )
                }
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val UTTERANCE_ID = "poetry"
        const val EXTRA_ID = "poetry_id"
        const val EXTRA_TITLE = "poetry_title"
    }
}
