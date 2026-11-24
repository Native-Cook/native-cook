package com.ngocdevv.nativecook.blocks.text.scribbleemphasisnotes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One line's stretch of a phrase, in dp: left, right, baseline. */
internal class Band(var x0: Float, var x1: Float, val base: Float)

/**
 * Lays the paragraph out once and measures every word, so the marks sit on real text. A StaticLayout chooses the
 * line breaks and the x positions; the lines are drawn one by one on baselines of a fixed line height. All public
 * numbers are dp in the screen's coordinates.
 */
internal class ScribblePage(
    private val text: String,
    val x: Float,
    val y: Float,
    width: Float,
    val fontSize: Float,
    lineHeight: Float,
    color: Int,
    private val density: Float,
) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.color = color
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        // TextPaint has its own `density` field (1 by default): name the page's explicitly
        textSize = fontSize * this@ScribblePage.density
        letterSpacing = -0.15f / fontSize
    }
    private val layout: StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, (width * density).roundToInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .setIncludePad(false)
        .build()

    val lineCount = layout.lineCount
    val height = lineCount * lineHeight
    val lineBase: FloatArray

    /** Per word: left edge, width, line index, sentence index. */
    val wordX: FloatArray
    val wordW: FloatArray
    val wordLine: IntArray
    val wordSentence: IntArray

    /** Per sentence: first and last word. */
    val sentenceFirst: IntArray
    val sentenceLast: IntArray

    /** Per line: left and right edge of its ink. */
    val lineLeft: FloatArray
    val lineRight: FloatArray

    init {
        val metrics = paint.fontMetrics
        val ascent = -metrics.ascent / density
        val descent = metrics.descent / density
        // Half-leading: the line's extra height is shared above and below the glyphs.
        val firstBase = y + (lineHeight - (ascent + descent)) / 2f + ascent
        lineBase = FloatArray(lineCount) { firstBase + it * lineHeight }

        val xs = ArrayList<Float>()
        val ws = ArrayList<Float>()
        val lines = ArrayList<Int>()
        val sentences = ArrayList<Int>()
        val firsts = arrayListOf(0)
        val lasts = ArrayList<Int>()
        val left = FloatArray(lineCount) { Float.POSITIVE_INFINITY }
        val right = FloatArray(lineCount) { Float.NEGATIVE_INFINITY }
        var sentence = 0
        for (match in Regex("\\S+").findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            val line = layout.getLineForOffset(start)
            val wordLeft = x + layout.getPrimaryHorizontal(start) / density
            val wordWidth = paint.measureText(text, start, end) / density
            val index = xs.size
            xs += wordLeft
            ws += wordWidth
            lines += line
            sentences += sentence
            left[line] = min(left[line], wordLeft)
            right[line] = max(right[line], wordLeft + wordWidth)
            if (match.value.last() in ".!?") {
                lasts += index
                sentence += 1
                firsts += index + 1
            }
        }
        if (lasts.size < firsts.size) lasts += xs.size - 1
        if (firsts.last() >= xs.size) {
            firsts.removeAt(firsts.lastIndex)
            while (lasts.size > firsts.size) lasts.removeAt(lasts.lastIndex)
        }
        wordX = xs.toFloatArray()
        wordW = ws.toFloatArray()
        wordLine = lines.toIntArray()
        wordSentence = sentences.toIntArray()
        sentenceFirst = firsts.toIntArray()
        sentenceLast = lasts.toIntArray()
        lineLeft = FloatArray(lineCount) { if (left[it].isFinite()) left[it] else x + layout.getLineLeft(it) / density }
        lineRight = FloatArray(lineCount) { if (right[it].isFinite()) right[it] else x + layout.getLineRight(it) / density }
    }

    /** The bands (one per line) a character range covers. */
    fun bands(start: Int, end: Int): List<Band> {
        val out = ArrayList<Band>()
        for (line in layout.getLineForOffset(start)..layout.getLineForOffset(max(end - 1, start))) {
            val a = max(start, layout.getLineStart(line))
            val b = min(end, layout.getLineVisibleEnd(line))
            if (b <= a) continue
            val left = layout.getPrimaryHorizontal(a) / density
            val width = paint.measureText(text, a, b) / density
            if (width < 0.5f) continue
            out += Band(x + left, x + left + width, lineBase[line])
        }
        return out
    }

    /** Draws the text on a canvas in pixels. */
    fun draw(canvas: Canvas) {
        for (line in 0 until lineCount) {
            canvas.drawText(
                text,
                layout.getLineStart(line),
                layout.getLineVisibleEnd(line),
                (x + layout.getLineLeft(line) / density) * density,
                lineBase[line] * density,
                paint,
            )
        }
    }
}
