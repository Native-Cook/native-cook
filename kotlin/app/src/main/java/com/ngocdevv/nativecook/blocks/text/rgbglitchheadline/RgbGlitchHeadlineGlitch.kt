package com.ngocdevv.nativecook.blocks.text.rgbglitchheadline

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * The headline as one path, laid out for a screen width (in dp): centred lines of glyphs placed by
 * their advances plus a fixed tracking, so every stack lays it out alike.
 */
internal class RgbGlitchHeadlineType(private val typeface: Typeface) {
    class Block(
        val path: Path,
        /** Top of the first line's capitals and the last baseline. */
        val top: Float,
        val bottom: Float,
        val capHeight: Float,
        val centreX: Float,
        val centreY: Float,
    )

    private var cached: Block? = null
    private var key: Triple<Float, Float, List<String>>? = null

    /** [width] is the screen's width (the widest line fits between 24 dp margins); [centreY] is where the block's middle goes. */
    fun block(lines: List<String>, width: Float, centreY: Float): Block {
        val k = Triple(width, centreY, lines)
        cached?.let { if (k == key) return it }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = this@RgbGlitchHeadlineType.typeface
            textSize = UNIT
        }
        val shaped = lines.map { line(it, paint) }
        val widest = shaped.maxOf { it.second }
        val size = min((width - 48f) / widest * UNIT, MAX_SIZE)
        val scale = size / UNIT
        val capBounds = Rect().also { paint.getTextBounds("H", 0, 1, it) }
        val cap = capBounds.height() * scale
        val step = size * LEADING
        val height = cap + step * (lines.size - 1)
        val top = centreY - height / 2f

        val path = Path()
        shaped.forEachIndexed { index, (glyphs, lineWidth) ->
            val baseline = top + cap + step * index
            val x = (width - lineWidth * scale) / 2f
            val m = Matrix().apply {
                postScale(scale, scale)
                postTranslate(x, baseline)
            }
            path.addPath(glyphs, m)
        }
        return Block(path, top, top + height, cap, width / 2f, centreY).also {
            cached = it
            key = k
        }
    }

    /** One line at 100 units, baseline at y = 0, and its width. */
    private fun line(text: String, paint: Paint): Pair<Path, Float> {
        val path = Path()
        val glyph = Path()
        var x = 0f
        for (ch in text) {
            val s = ch.toString()
            paint.getTextPath(s, 0, s.length, x, 0f, glyph)
            path.addPath(glyph)
            x += paint.measureText(s) + TRACKING * UNIT
        }
        return path to max(x - TRACKING * UNIT, 1f)
    }

    companion object {
        const val UNIT = 100f
        /** Letter spacing, in ems. */
        const val TRACKING = 0.06f
        /** Baseline to baseline, in ems. */
        const val LEADING = 1.02f
        const val MAX_SIZE = 78f
    }
}

/**
 * One stepped frame of interference: horizontal bands of the headline knocked sideways, a red and a
 * cyan copy split apart, sometimes a shear, a band gone, and thin noise lines across.
 */
internal class RgbGlitchHeadlineFrame {
    /** [hidden]: the white layer is missing here; only the colour copies show. */
    class Band(val y0: Float, val y1: Float, val dx: Float, var hidden: Boolean = false)
    class Strip(val y0: Float, val y1: Float, val dx: Float, val hidden: Boolean)

    val bands = ArrayList<Band>()
    /** How far the red copy sits right of the white one (the cyan one as far left). */
    var split = 0f
    /** The whole block's sideways jolt. */
    var jolt = 0f
    /** Shear of the whole block (tan of the angle; positive leans the tops right). */
    var shear = 0f
    /** Noise lines: y and opacity. */
    val noise = ArrayList<Pair<Float, Float>>()

    /** The block cut into horizontal strips at every band edge; a strip takes the offset of the last band that covers it. */
    fun strips(top: Float, bottom: Float): List<Strip> {
        val edges = ArrayList<Float>().apply {
            add(top); add(bottom)
            for (b in bands) {
                add(b.y0.coerceIn(top, bottom))
                add(b.y1.coerceIn(top, bottom))
            }
            sort()
        }
        val out = ArrayList<Strip>()
        for (i in 0 until edges.size - 1) {
            if (edges[i + 1] - edges[i] <= 0.01f) continue
            val mid = (edges[i] + edges[i + 1]) / 2f
            val band = bands.lastOrNull { mid >= it.y0 && mid < it.y1 }
            out += Strip(edges[i], edges[i + 1], band?.dx ?: 0f, band?.hidden ?: false)
        }
        return out
    }

    companion object {
        /** The frame for one step of a burst. [amount] 0…1 scales every offset. */
        fun make(seed: Int, step: Int, amount: Float, block: RgbGlitchHeadlineType.Block): RgbGlitchHeadlineFrame {
            fun r(k: Int) = random(seed * 7919 + step * 131 + k)
            val cap = block.capHeight
            val height = block.bottom - block.top
            val frame = RgbGlitchHeadlineFrame()
            val count = 2 + (r(0) * 4).toInt()
            for (i in 0 until count) {
                val centre = block.top + height * r(10 + i * 4)
                val h = cap * (0.06f + 0.5f * r(11 + i * 4))
                val dx = (if (r(12 + i * 4) < 0.5f) -1f else 1f) * cap * (0.08f + 0.42f * r(13 + i * 4)) * amount
                frame.bands += Band(centre - h / 2f, centre + h / 2f, dx)
            }
            if (r(30) < 0.3f * amount) frame.bands[0].hidden = true
            frame.split = (if (r(32) < 0.5f) -1f else 1f) * cap * (0.04f + 0.1f * r(31)) * amount
            frame.shear = if (r(33) < 0.22f * amount) tan(10.0 * Math.PI / 180.0).toFloat() else 0f
            frame.jolt = cap * 0.06f * (r(35) * 2f - 1f) * amount
            val lines = (r(36) * 3f * amount).toInt()
            for (j in 0 until lines) {
                frame.noise += (block.top - cap * 0.4f + (height + cap * 0.8f) * r(40 + j)) to (0.15f + 0.25f * r(44 + j))
            }
            return frame
        }

        /** A 32-bit integer hash mapped to 0..<1; the same in every stack. */
        fun random(n: Int): Float {
            var x = n
            x = x xor (x ushr 16)
            x *= 0x7feb352d
            x = x xor (x ushr 15)
            x *= 0x846ca68b.toInt()
            x = x xor (x ushr 16)
            return ((x.toLong() and 0xffffffffL).toDouble() / 4294967296.0).toFloat()
        }
    }
}
