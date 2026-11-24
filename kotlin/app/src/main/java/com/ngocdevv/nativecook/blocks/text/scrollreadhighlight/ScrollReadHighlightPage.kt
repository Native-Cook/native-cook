package com.ngocdevv.nativecook.blocks.text.scrollreadhighlight

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Lays the article out once, ported from the React Native block's `page.ts`. StaticLayout only breaks the lines
 (greedy, like Skia's paragraph); every line sits on an exact `leading` grid with the extra space split above and
 below (Skia's halfLeading), and is drawn with one drawText. All values are in px.
 */

internal class PageColors(
    val ink: Int,
    val dim: Int,
    val soft: Int,
    val coverTop: Int,
    val coverMid: Int,
    val coverLow: Int,
    val hill: Int,
    val hillNear: Int,
    val sun: Int,
)

/** One laid-out paragraph: its lines on a fixed leading grid, relative to its own top. */
internal class ReadParagraph(
    val text: String,
    val lineStart: IntArray,
    val lineEnd: IntArray,
    val lineX: FloatArray,
    val baseline: FloatArray,
    val lineTop: FloatArray,
    val height: Float,
    /** Top and height of the glyph box (ascent to descent) inside a line. */
    val boxTop: Float,
    val boxHeight: Float,
    val layout: StaticLayout,
) {
    fun draw(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        for (i in lineStart.indices) {
            if (lineEnd[i] > lineStart[i]) canvas.drawText(text, lineStart[i], lineEnd[i], x + lineX[i], y + baseline[i], paint)
        }
    }
}

internal class ReadBlock(val y: Float, val height: Float, val first: Int, val last: Int, val paragraph: ReadParagraph)

internal class ReadPage(
    val headHeight: Float,
    val blocks: List<ReadBlock>,
    val height: Float,
    val wordCount: Int,
    val firstBodyTop: Float,
    val lastBottom: Float,
    /** Words: page x of the first glyph, block-relative baseline, the characters they span. */
    val wordX: FloatArray,
    val wordBaseline: FloatArray,
    val wordStart: IntArray,
    val wordEnd: IntArray,
    val lineTop: FloatArray,
    val lineHeight: FloatArray,
    val lineFirst: IntArray,
    val lineCount: IntArray,
    val dimPaint: Paint,
    val inkPaint: Paint,
    private val drawHead: (Canvas) -> Unit,
    /** The column's left edge: paragraphs are laid out from 0, words already carry it in [wordX]. */
    val left: Float,
) {
    fun head(canvas: Canvas) = drawHead(canvas)
}

internal fun weighted(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, weight, false)
    else Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

private fun textPaint(sizePx: Float, color: Int, weight: Int, spacingPx: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    typeface = weighted(weight)
    textSize = sizePx
    this.color = color
    letterSpacing = spacingPx / sizePx
}

private fun paragraphOf(text: String, width: Float, paint: TextPaint, leading: Float): ReadParagraph {
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(floor(width).toInt(), 1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()
    val metrics = paint.fontMetrics
    val box = metrics.descent - metrics.ascent
    val boxTop = (leading - box) / 2
    val n = layout.lineCount
    val start = IntArray(n)
    val end = IntArray(n)
    val lineX = FloatArray(n)
    val baseline = FloatArray(n)
    val tops = FloatArray(n)
    for (i in 0 until n) {
        start[i] = layout.getLineStart(i)
        end[i] = layout.getLineVisibleEnd(i)
        lineX[i] = layout.getLineLeft(i)
        tops[i] = i * leading
        baseline[i] = tops[i] + boxTop - metrics.ascent
    }
    return ReadParagraph(text, start, end, lineX, baseline, tops, n * leading, boxTop, box, layout)
}

private fun hillPath(x: Float, y: Float, width: Float, height: Float, a: Float, b: Float) = Path().apply {
    moveTo(x, y + height)
    lineTo(x, y + height * a)
    cubicTo(x + width * 0.28f, y + height * (a - 0.2f), x + width * 0.52f, y + height * (b + 0.16f), x + width, y + height * b)
    lineTo(x + width, y + height)
    close()
}

/** A dusk over water, drawn: three-stop sky, a low sun, two headlands and a few strokes of light on the sea. Built once. */
private fun coverPainter(x: Float, y: Float, width: Float, height: Float, radius: Float, d: Float, colors: PageColors): (Canvas) -> Unit {
    val frame = Path().apply { addRoundRect(RectF(x, y, x + width, y + height), radius, radius, Path.Direction.CW) }
    val rect = RectF(x, y, x + width, y + height)
    val sky = Paint().apply {
        shader = LinearGradient(x, y, x, y + height, intArrayOf(colors.coverTop, colors.coverMid, colors.coverLow), floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP)
    }
    val sun = Paint().apply {
        shader = RadialGradient(
            x + width * 0.66f, y + height * 0.56f, height * 0.5f,
            intArrayOf(colors.sun, android.graphics.Color.argb(0, 244, 190, 130)), floatArrayOf(0.08f, 1f), Shader.TileMode.CLAMP,
        )
    }
    val far = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.hill }
    val farPath = hillPath(x, y + height * 0.4f, width, height * 0.3f, 0.72f, 0.38f)
    val near = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.hillNear }
    val nearPath = hillPath(x, y + height * 0.52f, width * 0.62f, height * 0.2f, 0.3f, 1f)
    val water = Paint().apply { color = 0xFF20262E.toInt() }
    val waterRect = RectF(x, y + height * 0.7f, x + width, y + height)
    val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((0.3f * 255).roundToInt(), 247, 201, 150) }
    val glints = (0 until 6).map { i ->
        val seed = (abs(sin(i * 12.9898) * 43758.5453) % 1.0).toFloat()
        val spread = (i + 1) * 3.4f * d
        val w = (10 + i * 5 + seed * 10) * d
        val gx = x + width * 0.66f - w / 2 + (seed - 0.5f) * spread * 2
        val gy = y + height * 0.74f + i * height * 0.042f
        RectF(gx, gy, gx + w, gy + 1.2f * d)
    }
    return { canvas ->
        canvas.save()
        canvas.clipPath(frame)
        canvas.drawRect(rect, sky)
        canvas.drawRect(rect, sun)
        canvas.drawPath(farPath, far)
        canvas.drawPath(nearPath, near)
        canvas.drawRect(waterRect, water)
        for (g in glints) canvas.drawRoundRect(g, d, d, glint)
        canvas.restore()
    }
}

/**
 * [x] and [width] are the text column in px, [bodySize] and [bodyLeading] in dp; [d] is the density.
 */
internal fun makePage(paragraphs: List<String>, title: String, byline: String, x: Float, width: Float, bodySize: Float, bodyLeading: Float, d: Float, colors: PageColors): ReadPage {
    val titleSize = if (bodySize >= 19) 34f else 30f
    val titleParagraph = paragraphOf(title, width, textPaint(titleSize * d, colors.ink, 700, -0.5f * d), titleSize * 1.12f * d)
    val titlePaint = textPaint(titleSize * d, colors.ink, 700, -0.5f * d)
    val bylinePaint = textPaint(15 * d, colors.soft, 500, 0f)
    val bylineParagraph = paragraphOf(byline, width, bylinePaint, 20 * d)
    val titleY = 16 * d
    val bylineY = titleY + titleParagraph.height + 8 * d
    val coverY = bylineY + (20 + 18) * d
    val coverHeight = min(width / d * 0.5f, 210f).roundToInt() * d
    val headHeight = coverY + coverHeight + 24 * d
    val cover = coverPainter(x, coverY, width, coverHeight, 18 * d, d, colors)
    val drawHead: (Canvas) -> Unit = { canvas ->
        titleParagraph.draw(canvas, x, titleY, titlePaint)
        bylineParagraph.draw(canvas, x, bylineY, bylinePaint)
        cover(canvas)
    }

    val dimPaint = textPaint(bodySize * d, colors.dim, 400, -0.1f * d)
    val inkPaint = textPaint(bodySize * d, colors.ink, 400, -0.1f * d)
    val leading = bodyLeading * d

    val blocks = ArrayList<ReadBlock>()
    val wordX = ArrayList<Float>()
    val wordBaseline = ArrayList<Float>()
    val wordStart = ArrayList<Int>()
    val wordEnd = ArrayList<Int>()
    val lineTop = ArrayList<Float>()
    val lineHeight = ArrayList<Float>()
    val lineFirst = ArrayList<Int>()
    val lineCount = ArrayList<Int>()
    var y = headHeight

    for (text in paragraphs) {
        val paragraph = paragraphOf(text, width, inkPaint, leading)
        val first = wordX.size
        var row = -1
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                i += 1
                continue
            }
            var end = i
            while (end < text.length && !text[end].isWhitespace()) end += 1
            val line = paragraph.layout.getLineForOffset(i)
            if (line != row) {
                row = line
                lineTop.add(y + paragraph.lineTop[line] + paragraph.boxTop)
                lineHeight.add(paragraph.boxHeight)
                lineFirst.add(wordX.size)
                lineCount.add(0)
            }
            lineCount[lineCount.size - 1] = lineCount.last() + 1
            wordX.add(x + paragraph.layout.getPrimaryHorizontal(i))
            wordBaseline.add(paragraph.baseline[line])
            wordStart.add(i)
            wordEnd.add(end)
            i = end
        }
        blocks.add(ReadBlock(y, paragraph.height, first, wordX.size - 1, paragraph))
        y += paragraph.height + (bodyLeading * 0.62f).roundToInt() * d
    }

    val lastLine = lineTop.size - 1
    return ReadPage(
        headHeight = headHeight,
        blocks = blocks,
        height = y + 40 * d,
        wordCount = wordX.size,
        firstBodyTop = lineTop.firstOrNull() ?: headHeight,
        lastBottom = if (lastLine >= 0) lineTop[lastLine] + lineHeight[lastLine] else y,
        wordX = wordX.toFloatArray(),
        wordBaseline = wordBaseline.toFloatArray(),
        wordStart = wordStart.toIntArray(),
        wordEnd = wordEnd.toIntArray(),
        lineTop = lineTop.toFloatArray(),
        lineHeight = lineHeight.toFloatArray(),
        lineFirst = lineFirst.toIntArray(),
        lineCount = lineCount.toIntArray(),
        dimPaint = dimPaint,
        inkPaint = inkPaint,
        drawHead = drawHead,
        left = x,
    )
}
