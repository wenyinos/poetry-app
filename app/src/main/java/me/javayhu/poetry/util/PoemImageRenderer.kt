package me.javayhu.poetry.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import me.javayhu.poetry.data.PoemContent

/**
 * 把一首诗词渲染成竖版分享图。
 *
 * 纯 Canvas 绘制，不依赖任何图片库：先按正文换行结果算出总高度，
 * 再建位图逐段绘制，因此不论诗有多长都不会被截断。
 *
 * 提供三套模板，配色与装饰不同，布局逻辑共用。
 */
object PoemImageRenderer {

    /** 分享图模板 */
    enum class Template(
        val label: String,
        private val bg: String,
        private val title: String,
        private val meta: String,
        private val body: String,
        private val accent: String,
        /** 是否绘制上下装饰细线 */
        val decorated: Boolean,
    ) {
        CLASSIC("经典", "#F3F8FB", "#212121", "#8A8A8A", "#37474F", "#03A9F4", false),
        PAPER("宣纸", "#FAF6EC", "#3E2723", "#9E8B7A", "#4E342E", "#8D6E63", true),
        DARK("深色", "#1E1E1E", "#F2F2F2", "#9E9E9E", "#CFCFCF", "#4FC3F7", true);

        val bgColor: Int get() = Color.parseColor(bg)
        val titleColor: Int get() = Color.parseColor(title)
        val metaColor: Int get() = Color.parseColor(meta)
        val bodyColor: Int get() = Color.parseColor(body)
        val accentColor: Int get() = Color.parseColor(accent)
    }

    private const val WIDTH = 1080
    private const val PADDING = 88f

    private const val TITLE_SIZE = 58f
    private const val META_SIZE = 32f
    private const val BODY_SIZE = 42f
    private const val FOOTER_SIZE = 26f

    private const val TITLE_LINE = 86f
    private const val META_LINE = 52f
    private const val BODY_LINE = 70f
    private const val BLOCK_GAP = 64f

    fun render(
        poem: PoemContent,
        footer: String,
        template: Template = Template.CLASSIC,
    ): Bitmap {
        val titlePaint = textPaint(TITLE_SIZE, template.titleColor, bold = true)
        val metaPaint = textPaint(META_SIZE, template.metaColor)
        val bodyPaint = textPaint(BODY_SIZE, template.bodyColor)
        val footerPaint = textPaint(FOOTER_SIZE, template.accentColor)

        val contentWidth = WIDTH - PADDING * 2
        val titleLines = wrap(poem.name, titlePaint, contentWidth)
        val meta = listOf(poem.dynasty, poem.author)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        val bodyLines = wrap(poem.content.trim(), bodyPaint, contentWidth)

        val bodyHeight = bodyLines.size * BODY_LINE
        val metaHeight = if (meta.isEmpty()) 0f else META_LINE
        val decoration = if (template.decorated) DECORATION_GAP else 0f
        val totalHeight = (
            PADDING + decoration +
                titleLines.size * TITLE_LINE +
                metaHeight +
                BLOCK_GAP +
                bodyHeight +
                BLOCK_GAP +
                FOOTER_SIZE * 2 +
                decoration +
                PADDING
            ).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(template.bgColor)

        var y = PADDING
        if (template.decorated) {
            y += decoration
            drawRule(canvas, y - decoration * 0.55f, template.accentColor)
        }

        for (line in titleLines) {
            y += TITLE_LINE
            canvas.drawText(line, WIDTH / 2f, y - TITLE_SIZE * 0.35f, titlePaint)
        }

        if (meta.isNotEmpty()) {
            y += META_LINE * 0.7f
            canvas.drawText(meta, WIDTH / 2f, y, metaPaint)
            y += META_LINE * 0.3f
        }

        y += BLOCK_GAP
        for (line in bodyLines) {
            y += BODY_LINE
            canvas.drawText(line, WIDTH / 2f, y - BODY_SIZE * 0.35f, bodyPaint)
        }

        y += BLOCK_GAP + FOOTER_SIZE
        canvas.drawText(footer, WIDTH / 2f, y, footerPaint)

        if (template.decorated) {
            y += decoration
            drawRule(canvas, y - FOOTER_SIZE * 0.2f, template.accentColor)
        }

        return bitmap
    }

    private const val DECORATION_GAP = 48f

    /** 上下两条居中的短横线，给宣纸与深色模板一点仪式感 */
    private fun drawRule(canvas: Canvas, y: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
            alpha = 150
        }
        val half = 64f
        canvas.drawLine(WIDTH / 2f - half, y, WIDTH / 2f + half, y, paint)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            isFakeBoldText = bold
        }

    /** 按字符逐个测量换行，中文与标点都能正确断开 */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = ArrayList<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) {
                result.add("")
                continue
            }
            val line = StringBuilder()
            for (ch in paragraph) {
                val candidate = line.toString() + ch
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    result.add(line.toString())
                    line.setLength(0)
                }
                line.append(ch)
            }
            if (line.isNotEmpty()) result.add(line.toString())
        }
        return result
    }
}
