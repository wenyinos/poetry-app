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
 */
object PoemImageRenderer {

    private const val WIDTH = 1080
    private const val PADDING = 88f

    private const val BG_COLOR = "#F3F8FB"
    private const val TITLE_COLOR = "#212121"
    private const val META_COLOR = "#8A8A8A"
    private const val BODY_COLOR = "#37474F"
    private const val BRAND_COLOR = "#03A9F4"

    private const val TITLE_SIZE = 58f
    private const val META_SIZE = 32f
    private const val BODY_SIZE = 42f
    private const val FOOTER_SIZE = 26f

    private const val TITLE_LINE = 86f
    private const val META_LINE = 52f
    private const val BODY_LINE = 70f
    private const val BLOCK_GAP = 64f

    fun render(poem: PoemContent, footer: String): Bitmap {
        val titlePaint = textPaint(TITLE_SIZE, TITLE_COLOR, bold = true)
        val metaPaint = textPaint(META_SIZE, META_COLOR)
        val bodyPaint = textPaint(BODY_SIZE, BODY_COLOR)
        val footerPaint = textPaint(FOOTER_SIZE, BRAND_COLOR)

        val contentWidth = WIDTH - PADDING * 2
        val titleLines = wrap(poem.name, titlePaint, contentWidth)
        val meta = listOf(poem.dynasty, poem.author)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        val bodyLines = wrap(poem.content.trim(), bodyPaint, contentWidth)

        val bodyHeight = bodyLines.size * BODY_LINE
        val metaHeight = if (meta.isEmpty()) 0f else META_LINE
        val totalHeight = (
            PADDING +
                titleLines.size * TITLE_LINE +
                metaHeight +
                BLOCK_GAP +
                bodyHeight +
                BLOCK_GAP +
                FOOTER_SIZE * 2 +
                PADDING
            ).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor(BG_COLOR))

        var y = PADDING
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

        return bitmap
    }

    private fun textPaint(size: Float, colorHex: String, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
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
