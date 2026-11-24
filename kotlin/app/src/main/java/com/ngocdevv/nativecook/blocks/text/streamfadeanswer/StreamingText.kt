package com.ngocdevv.nativecook.blocks.text.streamfadeanswer

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

/*
 StreamingText — text that arrives word by word, the way an assistant's answer streams in.

 The paragraph is laid out once, in full: every word is measured (advances of the system font)
 and wrapped into lines before anything shows, so a word never moves sideways or pushes another
 when it appears. Only three things animate per word: opacity 0 → 1, a blur clearing and a rise
 of a few dp. Whatever the length of the text, one Canvas draws it from the clock, read in the
 draw: nothing recomposes per frame and there is no animated node per word.

 The newest line also fades toward the front of the stream (a horizontal gradient mask that
 runs ahead with the words), which softens the leading edge without a caret.

 Drive it with a shared clock (`time`, in seconds, plus `start` for this text's place on it) to
 scrub, pause or chain several texts, or leave `time` out and it plays once when it appears.
 A port of the React Native StreamingText, number for number.
*/

/** Room around the text for the blur and the rise inside the leading edge's layer. */
private const val PAD = 12f

private const val TEXT_FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG

/** The system font at a CSS weight (400, 500, 600 or 700). */
internal fun systemTypeface(weight: Int): Typeface = when {
    weight <= 400 -> Typeface.SANS_SERIF
    Build.VERSION.SDK_INT >= 28 -> Typeface.create(Typeface.SANS_SERIF, weight, false)
    weight == 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
}

/** One blur per step of a word's arrival, shared by every text. Sigma in dp: the canvas is scaled to dp. */
private val blurFilters: Array<BlurMaskFilter> by lazy {
    Array(BLUR_LEVELS + 1) { k -> BlurMaskFilter(sigmaToRadius(max(0.01f, WORD_BLUR * k / BLUR_LEVELS)), BlurMaskFilter.Blur.NORMAL) }
}

/** A text wrapped and timed once. All lengths in dp, all times in seconds. */
internal class StreamLayout(
    text: String,
    val width: Float,
    rate: Float,
    fontSize: Float,
    fontWeight: Int,
    val lineHeight: Float,
    color: Int,
    seed: Float,
) {
    private val paint = Paint(TEXT_FLAGS).apply {
        typeface = systemTypeface(fontWeight)
        textSize = fontSize
        this.color = color
    }
    private val baseAlpha = paint.alpha
    /** The first baseline below a line's top: where a native text of this line height would put it. */
    private val firstBase: Float
    private val words: Array<String>
    /** Per word: pen x, baseline y, the line it is on, when it starts. */
    private val xs: FloatArray
    private val ys: FloatArray
    private val lineOf: IntArray
    private val starts: FloatArray
    val lineCount: Int
    val height: Float
    val wordCount: Int get() = words.size
    /** Width of the longest line. */
    val widest: Float
    /** How fast the front of the stream runs along a line, dp/s. */
    private val frontSpeed: Float
    /** When the last word has arrived, seconds after the first starts. */
    val duration: Float

    private val mask = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        shader = LinearGradient(0f, 0f, FEATHER + 8, 0f, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
    }
    private val maskAt = Matrix()

    init {
        val metrics = paint.fontMetrics
        firstBase = (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
        val space = paint.measureText(" ")
        val w = ArrayList<String>()
        val x = ArrayList<Float>()
        val l = ArrayList<Int>()
        var line = 0
        var travelled = 0f
        var wide = 0f
        // A line break in the text starts a new line.
        for (paragraph in text.split('\n')) {
            val tokens = paragraph.split(' ').filter { it.isNotEmpty() }
            var pen = 0f
            for (token in tokens) {
                val advance = paint.measureText(token)
                if (pen > 0 && pen + advance > width) {
                    line += 1
                    pen = 0f
                }
                w += token
                x += pen
                l += line
                wide = max(wide, pen + advance)
                pen += advance + space
                travelled += advance + space
            }
            if (tokens.isNotEmpty()) line += 1
        }
        words = w.toTypedArray()
        xs = x.toFloatArray()
        lineOf = l.toIntArray()
        lineCount = max(1, line)
        ys = FloatArray(words.size) { firstBase + lineOf[it] * lineHeight }
        starts = wordStarts(words.size, rate, seed)
        height = lineCount * lineHeight
        widest = wide
        frontSpeed = if (words.isNotEmpty()) (travelled / words.size) * rate else 0f
        duration = (if (words.isNotEmpty()) starts[words.size - 1] else 0f) + WORD_DUR
    }

    /**
     The text at stream time s (seconds since its first word started), into a canvas scaled to dp
     with the text's top-left corner at the origin. `whole` draws every word.
    */
    fun draw(canvas: android.graphics.Canvas, s: Float, whole: Boolean) {
        val n = words.size
        if (n == 0 || (!whole && s <= 0f)) return
        // Past the last word the front still has to run off the line before the mask is gone.
        if (whole || s >= duration + 0.12f) {
            for (i in 0 until n) canvas.drawText(words[i], xs[i], ys[i], paint)
            return
        }

        // Words [0, a) have arrived, [a, b) are arriving, the rest have not started.
        var a = 0
        while (a < n && s - starts[a] >= WORD_DUR) a += 1
        var b = a
        while (b < n && starts[b] < s) b += 1

        // Arrived words are drawn singly at the place they were measured at, so a word does not
        // shift when its line settles (a shaped line would kern across the spaces).
        for (i in 0 until a) canvas.drawText(words[i], xs[i], ys[i], paint)
        if (b <= a) return

        val layer = canvas.saveLayer(-PAD, -PAD, width + PAD, height + PAD, null)
        for (i in a until b) {
            val q = (s - starts[i]) / WORD_DUR
            val e = easeOutCubic(q)
            val level = ((1 - e) * BLUR_LEVELS).roundToInt()
            paint.alpha = (baseAlpha * easeOutQuad(q)).roundToInt()
            paint.maskFilter = if (level > 0) blurFilters[level] else null
            canvas.drawText(words[i], xs[i], ys[i] + WORD_RISE * (1 - e), paint)
        }
        paint.alpha = baseAlpha
        paint.maskFilter = null
        // The newest line fades toward the front, which keeps running at the pace of the stream.
        val k = b - 1
        val front = xs[k] + (s - starts[k]) * frontSpeed
        maskAt.setTranslate(front - FEATHER, 0f)
        mask.shader.setLocalMatrix(maskAt)
        // Only this line's box (and the rise under it): the line above keeps its descenders.
        val top = lineOf[k] * lineHeight
        canvas.drawRect(-PAD, top, width + PAD, top + lineHeight + WORD_RISE + 4, mask)
        canvas.restoreToCount(layer)
    }
}

/** The size and timing of a StreamingText before it is shown. */
internal class StreamMeasure(
    val height: Float,
    val lines: Int,
    val words: Int,
    /** The longest line, dp. */
    val widest: Float,
    /** Seconds from the first word starting to the last word having arrived. */
    val duration: Float,
    /** When the last word begins: where the next text on the same clock can follow. */
    val lastStart: Float,
)

/** The line height a text gets when none is given. */
internal fun defaultLineHeight(fontSize: Float) = round(fontSize * 1.42f)

/** Measures a text (dp, seconds): its layout is final from the start, so its height is too. */
internal fun measureStreamingText(
    text: String,
    width: Float,
    rate: Float = WORD_RATE,
    fontSize: Float = 16f,
    fontWeight: Int = 400,
    lineHeight: Float = defaultLineHeight(fontSize),
    seed: Float = 0f,
): StreamMeasure {
    val layout = StreamLayout(text, width, rate, fontSize, fontWeight, lineHeight, 0xFF000000.toInt(), seed)
    return StreamMeasure(layout.height, layout.lineCount, layout.wordCount, layout.widest, layout.duration, layout.duration - WORD_DUR)
}

/**
 * @param width Width to wrap in. The height follows from the text.
 * @param rate Words per second. The default is one word every 28 ms.
 * @param time A shared clock in seconds, read while drawing. Without it the text plays by itself once.
 * @param start When on the clock this text starts.
 * @param fontWeight 400, 500, 600 or 700.
 * @param seed Varies the rhythm between texts on one screen.
 * @param settled Show the whole text at once (static text, or reduced motion).
 */
@Composable
internal fun StreamingText(
    text: String,
    width: Dp,
    modifier: Modifier = Modifier,
    rate: Float = WORD_RATE,
    time: (() -> Float)? = null,
    start: Float = 0f,
    fontSize: Dp = 16.dp,
    fontWeight: Int = 400,
    lineHeight: Dp = defaultLineHeight(fontSize.value).dp,
    color: Color = Color(0xFF1D1C1A),
    seed: Float = 0f,
    settled: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val layout = remember(text, width, rate, fontSize, fontWeight, lineHeight, color, seed) {
        StreamLayout(text, width.value, rate, fontSize.value, fontWeight, lineHeight.value, color.toArgb(), seed)
    }
    val whole = settled || reduced

    // Its own clock, used only when no shared one is given.
    val own = remember(layout) { mutableFloatStateOf(0f) }
    if (time == null && !whole) {
        LaunchedEffect(layout) {
            var last = 0L
            while (own.floatValue <= layout.duration + 0.2f) {
                withFrameNanos { now ->
                    if (last != 0L) own.floatValue += min((now - last) / 1e9f, 0.05f)
                    last = now
                }
            }
        }
    }

    Canvas(modifier.size(width, layout.height.dp).semantics { contentDescription = text }) {
        val s = if (whole) 0f else (time?.invoke() ?: own.floatValue) - start
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            layout.draw(canvas, s, whole)
            canvas.restore()
        }
    }
}
