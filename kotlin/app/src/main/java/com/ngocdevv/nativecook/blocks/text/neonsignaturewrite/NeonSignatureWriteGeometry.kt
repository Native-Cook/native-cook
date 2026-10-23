package com.ngocdevv.nativecook.blocks.text.neonsignaturewrite

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One word of the sign: its text and the colours its tube burns in once lit. */
data class NeonSignatureWriteWord(
    val text: String,
    /** The fill runs from [start] (left) to [end] (right). */
    val start: Color,
    val end: Color,
    /** The halo around the letters and the light it throws on the wall. */
    val glow: Color,
) {
    companion object {
        val samples = listOf(
            NeonSignatureWriteWord("Afterglow", Color(0xFFFFC35C), Color(0xFFFF4A2B), Color(0xFFFF6A2C)),
            NeonSignatureWriteWord("Midnight", Color(0xFF3A6DFF), Color(0xFF40D2FF), Color(0xFF2F7BFF)),
            NeonSignatureWriteWord("Daydream", Color(0xFFFF5CE0), Color(0xFFFF3C6C), Color(0xFFFF3BB4)),
        )
    }
}

/**
 * The outlines of the sign's words, laid out once for a screen size (in dp): every contour of every
 * glyph (the tubes trace these), the filled word, and the sparks thrown from the pen heads.
 */
internal class NeonSignatureWriteGeometry(private val typeface: Typeface) {
    class Contour(
        val path: Path,
        val measure: PathMeasure,
        val length: Float,
        /** Where along the word the contour starts, 0 (left) to 1 (right): later contours start later. */
        val delay: Float,
        val sparks: List<Spark>,
    )

    class Spark(
        /** The tube (0 mint, 1 rose) whose head throws it, and how far round the contour that head is then. */
        val layer: Int,
        val at: Float,
        val origin: PointF,
        val vx: Float,
        val vy: Float,
        val life: Float,
        val length: Float,
        val width: Float,
        val white: Boolean,
    )

    class Word(
        val fill: Path,
        val contours: List<Contour>,
        /** The word's box before it is tilted; [left] and [right] are its mid-left and mid-right tilted. */
        val box: RectF,
        val left: PointF,
        val right: PointF,
    )

    private var words: List<Word> = emptyList()
    private var key: Triple<Float, Float, List<String>>? = null

    fun words(texts: List<String>, width: Float, height: Float): List<Word> {
        val k = Triple(width, height, texts)
        if (k == key) return words
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = this@NeonSignatureWriteGeometry.typeface
            textSize = UNIT
        }
        val outlines = texts.map { text -> Path().also { paint.getTextPath(text, 0, text.length, 0f, 0f, it) } }
        val bounds = outlines.map { RectF().also { b -> it.computeBounds(b, true) } }

        // One scale for every word, so the widest one (tilted) fits between 28 dp margins.
        val angle = abs(TILT)
        val room = width - 56f
        val widest = bounds.maxOf { it.width() * cos(angle) + it.height() * sin(angle) }
        val scale = min(room / widest, 1.9f)
        val cx = width / 2f
        val cy = height * 0.45f

        words = outlines.mapIndexed { index, outline ->
            val b = bounds[index]
            val matrix = Matrix().apply {
                postTranslate(-b.centerX(), -b.centerY())
                postScale(scale, scale)
                postRotate(Math.toDegrees(TILT.toDouble()).toFloat())
                postTranslate(cx, cy)
            }
            val fill = Path(outline).apply { transform(matrix) }
            val contours = mutableListOf<Contour>()
            val walker = PathMeasure(outline, false)
            var c = 0
            do {
                val length = walker.length
                if (length > 0.5f) {
                    val raw = Path()
                    walker.getSegment(0f, length, raw, true)
                    val rawBounds = RectF().also { raw.computeBounds(it, true) }
                    val path = Path(raw).apply { transform(matrix) }
                    val measure = PathMeasure(path, false)
                    val delay = (rawBounds.left - b.left) / max(b.width(), 1f)
                    contours += Contour(path, measure, measure.length, delay, sparks(measure, index * 997 + c * 31))
                    c++
                }
            } while (walker.nextContour())
            val box = RectF(cx - b.width() * scale / 2f, cy - b.height() * scale / 2f, cx + b.width() * scale / 2f, cy + b.height() * scale / 2f)
            val tilt = Matrix().apply { postRotate(Math.toDegrees(TILT.toDouble()).toFloat(), cx, cy) }
            val ends = floatArrayOf(box.left, box.centerY(), box.right, box.centerY()).also { tilt.mapPoints(it) }
            Word(fill, contours, box, PointF(ends[0], ends[1]), PointF(ends[2], ends[3]))
        }
        key = k
        return words
    }

    /** Three sparks per tube head on each contour, from a seeded hash so every loop throws the same ones. */
    private fun sparks(measure: PathMeasure, seed: Int): List<Spark> {
        val pos = FloatArray(2)
        return (0 until 6).map { k ->
            fun r(salt: Int) = random(seed * 13 + k * 7 + salt)
            val at = ((k / 2).toFloat() + 0.15f + 0.7f * r(1)) / 3f
            measure.getPosTan(at * measure.length, pos, null)
            val angle = r(2) * 2f * Math.PI.toFloat()
            val speed = 26f + 90f * r(3)
            Spark(
                layer = k % 2, at = at, origin = PointF(pos[0], pos[1]),
                vx = cos(angle) * speed, vy = sin(angle) * speed - 30f,
                life = 0.32f + 0.3f * r(4), length = 2f + 6f * r(5), width = 1.4f + 1.2f * r(6), white = r(8) < 0.6f,
            )
        }
    }

    companion object {
        /** The words are laid out at this size, then all scaled by the same factor to fit the screen. */
        const val UNIT = 100f
        /** The sign leans up to the right. */
        val TILT = (-8.0 * Math.PI / 180.0).toFloat()

        /** A 32-bit integer hash mapped to 0..<1; the same in every stack. */
        fun random(n: Int): Float {
            var x = n
            x = x xor (x ushr 16)
            x *= 0x7feb352d
            x = x xor (x ushr 15)
            x *= 0x846ca68b.toInt()
            x = x xor (x ushr 16)
            return (x.toLong() and 0xffffffffL).toDouble().div(4294967296.0).toFloat()
        }
    }
}
