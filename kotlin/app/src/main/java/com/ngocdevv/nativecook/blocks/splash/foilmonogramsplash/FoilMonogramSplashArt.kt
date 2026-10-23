package com.ngocdevv.nativecook.blocks.splash.foilmonogramsplash

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The Loaven knot: two long loops (stadiums) crossed at ±KNOT_TILT, woven over and under at their
// four crossings like a braided loaf. Everything is in mark units around the knot's centre.
internal const val LOOP_W = 46f
internal const val LOOP_H = 136f
internal const val KNOT_TILT = 30f // degrees each loop leans from upright
internal const val STROKE = 8f // foil band width
internal const val GAP = 2.6f // clear space on each side of a strand where it passes over the other
internal const val OVER_SPAN = 0.06f // share of a loop's length redrawn on top around each crossing
internal const val CORE_OVERLAP = 0.012f // share of a loop the gold core of a woven-over patch runs past its cut

private const val R = LOOP_W / 2f
private const val HALF = LOOP_H / 2f - R
private val TILT_RAD = KNOT_TILT * PI.toFloat() / 180f

/** The knot's size in mark units, stroke included. */
internal val KNOT_WIDTH = 2f * (HALF * sin(TILT_RAD) + R + STROKE / 2f)
internal val KNOT_HEIGHT = 2f * (HALF * cos(TILT_RAD) + R + STROKE / 2f)

/**
 * Where the loops cross, as a share of the length of the loop that passes over there (each loop
 * starts at its top tip and runs clockwise): alternating over and under, a true weave.
 */
private class Crossing(val over: Int, val at: Float)

private val Crossings = listOf(Crossing(0, 0.2091f), Crossing(1, 0.6272f), Crossing(0, 0.7091f), Crossing(1, 0.1272f))

/** One loop, upright, then leaned by `sign` × KNOT_TILT, starting at its top tip and running clockwise. */
private fun loopPath(sign: Float): Path {
    val p = Path()
    p.moveTo(0f, -HALF - R)
    p.arcTo(RectF(-R, -HALF - R, R, -HALF + R), -90f, 90f, false)
    p.lineTo(R, HALF)
    p.arcTo(RectF(-R, HALF - R, R, HALF + R), 0f, 180f, false)
    p.lineTo(-R, -HALF)
    p.arcTo(RectF(-R, -HALF - R, R, -HALF + R), 180f, 90f, false)
    p.transform(Matrix().apply { setRotate(sign * KNOT_TILT) })
    return p
}

/** An arched doorway, unit radius: a half circle over a body that runs 1.5 radii below its centre. */
private fun archPath(outline: Boolean): Path {
    val p = Path()
    p.moveTo(-1f, 1.5f)
    p.lineTo(-1f, 0f)
    p.arcTo(RectF(-1f, -1f, 1f, 1f), 180f, 180f, false)
    p.lineTo(1f, 1.5f)
    if (!outline) p.close()
    return p
}

// MARK: The script, in seconds. It starts from a bare splash and comes back to it.

internal const val LOOP = 9f
private val DrawAt = floatArrayOf(0.3f, 0.55f)
private const val DRAW_TIME = 1.4f
private const val SWEEP_AT = 1.95f
private const val SWEEP_TIME = 0.95f
internal const val SWEEP_REACH = 190f
private const val LETTERS_AT = 1.5f
private const val LETTER_STAGGER = 0.07f
private const val LETTER_IN = 0.6f
internal const val TAG_AT = 2.25f
internal const val TAG_IN = 0.5f
internal const val HANDOFF_AT = 3.9f
private const val LETTER_OUT = 0.36f
private const val LETTER_OUT_STAGGER = 0.035f
private const val MOVE_AT = 3.98f
private const val MOVE_TIME = 0.9f
internal const val ARCH_AT = 4.05f
internal const val ARCH_TIME = 0.9f
private const val APP_AT = 4.4f
private const val APP_STAGGER = 0.07f
private const val APP_IN = 0.7f
internal const val APP_RISE = 18f
internal const val APP_REST = 6f
internal const val CLOSE_AT = 8f
private const val CLOSE_TIME = 0.8f
private const val KNOT_OUT = 0.3f
private const val RIM_FADE = 24f // dp of doorway radius over which its gold rim fades in
internal const val SKIP_SPEED = 3.5f
internal const val RESUME_AFTER = 1.5f

internal const val MARK_SCALE = 1.15f
internal const val HEADER_MARK = 36f
internal const val HEADER_LEFT = 20f

private const val WORD = "LOAVEN"
private const val WORD_SIZE = 34f
private const val WORD_TRACK = 11f

private val EaseOutCurve = floatArrayOf(0.23f, 1f, 0.32f, 1f)
private val EaseInOutCurve = floatArrayOf(0.65f, 0f, 0.35f, 1f)
private val TransitionCurve = floatArrayOf(0.77f, 0f, 0.175f, 1f)

/** Cubic bezier easing, as in CSS: solves x for `t` by Newton steps, then returns y. */
internal fun bezier(c: FloatArray, t: Float): Float {
    if (t <= 0f) return 0f
    if (t >= 1f) return 1f
    val x1 = c[0]
    val y1 = c[1]
    val x2 = c[2]
    val y2 = c[3]
    var u = t
    repeat(8) {
        val x = 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u - t
        val dx = 3 * (1 - u) * (1 - u) * x1 + 6 * (1 - u) * u * (x2 - x1) + 3 * u * u * (1 - x2)
        if (abs(x) < 1e-5f || abs(dx) < 1e-6f) return@repeat
        u = (u - x / dx).coerceIn(0f, 1f)
    }
    return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
}

internal fun step(t: Float, at: Float, time: Float) = ((t - at) / time).coerceIn(0f, 1f)

private fun drawn(t: Float, loop: Int, reduced: Boolean): Float =
    if (reduced) (if (t >= DrawAt[0]) 1f else 0f) else bezier(EaseInOutCurve, step(t, DrawAt[loop], DRAW_TIME))

private fun knotOpacity(t: Float, reduced: Boolean): Float {
    val enter = if (reduced) step(t, DrawAt[0], 0.5f) else 1f
    val leave = if (t >= CLOSE_AT) 1f - step(t, CLOSE_AT, KNOT_OUT) else 1f
    val swap = if (reduced && t >= HANDOFF_AT && t < CLOSE_AT) abs(1f - 2f * step(t, HANDOFF_AT, 0.8f)) else 1f
    return enter * leave * swap
}

private fun moved(t: Float, reduced: Boolean): Float =
    if (reduced) (if (t >= HANDOFF_AT + 0.4f) 1f else 0f) else bezier(TransitionCurve, step(t, MOVE_AT, MOVE_TIME))

private fun opened(t: Float): Float =
    if (t >= CLOSE_AT) 1f - bezier(TransitionCurve, step(t, CLOSE_AT, CLOSE_TIME)) else bezier(TransitionCurve, step(t, ARCH_AT, ARCH_TIME))

/** Where the scripted light is, -0.25 (off to the left) to 1.25 (off to the right). */
internal fun sweep(t: Float) = -0.25f + 1.5f * bezier(EaseInOutCurve, step(t, SWEEP_AT, SWEEP_TIME))

private fun letterShift(t: Float, i: Int): Float {
    val enter = bezier(EaseOutCurve, step(t, LETTERS_AT + i * LETTER_STAGGER, LETTER_IN))
    val leave = bezier(TransitionCurve, step(t, HANDOFF_AT + i * LETTER_OUT_STAGGER, LETTER_OUT))
    return 1f - enter - leave
}

/** An app element's entrance, 0 to 1. */
internal fun appIn(t: Float, k: Int) = bezier(EaseOutCurve, step(t, APP_AT + k * APP_STAGGER, APP_IN))

internal fun tagEnter(t: Float) = bezier(EaseOutCurve, step(t, TAG_AT, TAG_IN))

/** Where things sit on this screen, in dp. */
internal class FoilLayout(val width: Float, val height: Float, val top: Float) {
    val cx = width / 2f
    val cy = height * 0.43f
    val headerScale = HEADER_MARK / KNOT_HEIGHT
    val hx = HEADER_LEFT + KNOT_WIDTH * headerScale / 2f
    val hy = top + 10f + HEADER_MARK / 2f
    val baseline = cy + KNOT_HEIGHT * MARK_SCALE / 2f + 74f
    val archReach = hypot(max(cx, width - cx), cy) + 40f
}

private const val SPLASH = 0xFF130D0A.toInt()
private const val SPLASH_LIGHT = 0xFF2A1E16.toInt()
private const val RIM = 0xFFD8B77E.toInt()
private const val FOIL_EDGE = 0xFF4E341B.toInt()
private val Foil = intArrayOf(
    0xFF6F4B29.toInt(), 0xFFC9A167.toInt(), 0xFFF4E2B6.toInt(), 0xFFB8894E.toInt(),
    0xFFE3C68E.toInt(), 0xFF8A6236.toInt(), 0xFF5E3F22.toInt(),
)
private val FoilAt = floatArrayOf(0f, 0.22f, 0.38f, 0.55f, 0.7f, 0.86f, 1f)
private val Shine = intArrayOf(0x00FFF8E6, 0xC7FFF8E6.toInt(), 0x00FFF8E6)

/**
 * The splash drawn on one canvas, in dp: the espresso ground with its arched doorway, the
 * wordmark and the knot. Paths, paints and gradients are made once and reused every frame.
 */
internal class FoilArt {
    private val loops = arrayOf(loopPath(1f), loopPath(-1f))
    private val measures = loops.map { PathMeasure(it, false) }
    private val lengths = measures.map { it.length }
    private val arch = archPath(false)
    private val archRim = archPath(true)
    private val scratch = Path()
    private val transformed = Path()
    private val matrix = Matrix()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = STROKE + GAP * 2f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val atop = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val foil = LinearGradient(-KNOT_WIDTH / 2f, -KNOT_HEIGHT / 2f, KNOT_WIDTH / 2f, KNOT_HEIGHT / 2f, Foil, FoilAt, Shader.TileMode.CLAMP)
    private val shine = LinearGradient(-46f, -34f, 46f, 34f, Shine, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        color = android.graphics.Color.WHITE
    }

    // Built for one layout and density.
    private var builtFor = ""
    private var ground: RadialGradient? = null
    private var letterX = FloatArray(0)
    private var letterW = FloatArray(0)
    private var letterFoil = arrayOf<LinearGradient>()

    private fun build(l: FoilLayout, density: Float) {
        val key = "${l.width}x${l.height}@$density"
        if (key == builtFor) return
        builtFor = key
        ground = RadialGradient(l.cx * density, l.cy * density, max(1f, l.height * 0.75f * density), SPLASH_LIGHT, SPLASH, Shader.TileMode.CLAMP)
        text.textSize = WORD_SIZE * density
        val widths = FloatArray(WORD.length) { max(1f, text.measureText(WORD, it, it + 1) / density) }
        val total = widths.sum() + WORD_TRACK * (WORD.length - 1)
        var x = l.cx - total / 2f
        letterX = FloatArray(WORD.length) { i -> x.also { x += widths[i] + WORD_TRACK } }
        letterW = widths
        letterFoil = Array(WORD.length) { i ->
            LinearGradient(
                letterX[i] * density, (l.baseline - WORD_SIZE * 0.75f) * density,
                (letterX[i] + widths[i]) * density, l.baseline * density,
                Foil, FoilAt, Shader.TileMode.CLAMP,
            )
        }
    }

    fun draw(c: Canvas, l: FoilLayout, density: Float, t: Float, reduced: Boolean, shineX: Float) {
        build(l, density)
        drawGround(c, l, density, t, reduced)
        drawWordmark(c, l, density, t, reduced, shineX)
        drawKnot(c, l, density, t, reduced, shineX)
    }

    /** The splash, with the arch cut out of it as it opens, and a thin gold rim along the opening. */
    private fun drawGround(c: Canvas, l: FoilLayout, d: Float, t: Float, reduced: Boolean) {
        val o = opened(t)
        val opacity = when {
            !reduced -> if (o >= 0.999f) 0f else 1f
            t >= CLOSE_AT -> step(t, CLOSE_AT, 0.45f)
            else -> 1f - step(t, ARCH_AT, 0.45f)
        }
        val scale = if (reduced) 0.001f else max(0.001f, o * l.archReach)
        matrix.reset()
        matrix.setScale(scale * d, scale * d)
        matrix.postTranslate(l.cx * d, l.cy * d)
        if (opacity > 0f) {
            val save = c.saveLayerAlpha(0f, 0f, l.width * d, l.height * d, (opacity * 255f).toInt())
            fill.shader = ground
            fill.alpha = 255
            c.drawRect(0f, 0f, l.width * d, l.height * d, fill)
            fill.shader = null
            arch.transform(matrix, transformed)
            c.drawPath(transformed, clear)
            c.restoreToCount(save)
        }
        if (!reduced && o > 0f && o < 1f) {
            archRim.transform(matrix, transformed)
            stroke.shader = null
            stroke.color = RIM
            // Faded out while the doorway is still a speck, so it never shows as a dot.
            stroke.alpha = (0.85f * sin(PI.toFloat() * o) * min(1f, o * l.archReach / RIM_FADE) * 255f).toInt()
            stroke.strokeWidth = 1.4f * d
            stroke.strokeCap = Paint.Cap.BUTT
            c.drawPath(transformed, stroke)
            stroke.alpha = 255
        }
    }

    /** The wordmark: each letter, in foil, slides through a slot at its baseline. */
    private fun drawWordmark(c: Canvas, l: FoilLayout, d: Float, t: Float, reduced: Boolean, shineX: Float) {
        val baseline = l.baseline
        val reveal = WORD_SIZE * 0.95f
        val layer = c.saveLayer(0f, (baseline - WORD_SIZE * 1.2f) * d, l.width * d, (baseline + WORD_SIZE * 0.4f) * d, null)
        for (i in WORD.indices) {
            val shift = if (reduced) 0f else letterShift(t, i)
            val opacity = if (reduced) {
                step(t, LETTERS_AT, 0.5f) * (1f - step(t, HANDOFF_AT, 0.3f))
            } else if (shift >= 0.999f || shift <= -0.999f) 0f else 1f
            if (opacity <= 0f) continue
            val x = letterX[i]
            val w = letterW[i]
            c.save()
            c.clipRect((x - 6f) * d, (baseline - WORD_SIZE * 0.86f) * d, (x + w + 6f) * d, (baseline + WORD_SIZE * 0.16f) * d)
            c.translate(0f, shift * reveal * d)
            text.shader = letterFoil[i]
            text.alpha = (opacity * 255f).toInt()
            c.drawText(WORD, i, i + 1, x * d, baseline * d, text)
            c.restore()
        }
        text.shader = null
        text.alpha = 255
        drawShine(c, shineX * d, baseline * d, d, 0f, (baseline - WORD_SIZE * 1.2f) * d, l.width * d, (baseline + WORD_SIZE * 0.4f) * d)
        c.restoreToCount(layer)
    }

    /** The light across whatever is already drawn in the layer. */
    private fun drawShine(c: Canvas, x: Float, y: Float, scale: Float, left: Float, top: Float, right: Float, bottom: Float) {
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(x, y)
        shine.setLocalMatrix(matrix)
        atop.shader = shine
        c.drawRect(left, top, right, bottom, atop)
        atop.shader = null
    }

    /** The knot: two foil loops drawn on from their tips, woven at the crossings, under the light. */
    private fun drawKnot(c: Canvas, l: FoilLayout, d: Float, t: Float, reduced: Boolean, shineX: Float) {
        val opacity = knotOpacity(t, reduced)
        if (opacity <= 0f) return
        val m = moved(t, reduced)
        val s = MARK_SCALE + (l.headerScale - MARK_SCALE) * m
        val x = l.cx + (l.hx - l.cx) * m
        val y = l.cy + (l.hy - l.cy) * m
        val layer = c.saveLayerAlpha(0f, 0f, l.width * d, l.height * d, (opacity * 255f).toInt())
        c.translate(x * d, y * d)
        c.scale(s * d, s * d)
        for (i in 0..1) {
            val end = drawn(t, i, reduced)
            // A round cap would show as a dot before the stroke starts.
            if (end <= 0.001f) continue
            segment(i, 0f, end)
            strand(c, Paint.Cap.ROUND, edge = true)
            strand(c, Paint.Cap.ROUND, edge = false)
        }
        // Where a strand passes over the other, it clears a gap through the one below and is drawn
        // again; its gold core runs a little further each way so no seam shows.
        for (crossing in Crossings) {
            val done = drawn(t, crossing.over, reduced)
            val from = crossing.at - OVER_SPAN
            if (done <= from + 0.001f) continue
            segment(crossing.over, from, min(crossing.at + OVER_SPAN, done))
            c.drawPath(scratch, cut)
            strand(c, Paint.Cap.BUTT, edge = true)
            segment(crossing.over, from - CORE_OVERLAP, min(crossing.at + OVER_SPAN + CORE_OVERLAP, done))
            strand(c, Paint.Cap.BUTT, edge = false)
        }
        // The light, in the knot's own units.
        val lx = (shineX - x) / s
        drawShine(c, lx, 0f, 1f, -KNOT_WIDTH, -KNOT_HEIGHT, KNOT_WIDTH, KNOT_HEIGHT)
        c.restoreToCount(layer)
    }

    /** Puts the stretch of loop `i` from share `from` to `to` in the scratch path. */
    private fun segment(i: Int, from: Float, to: Float) {
        scratch.reset()
        val len = lengths[i]
        measures[i].getSegment(from.coerceIn(0f, 1f) * len, to.coerceIn(0f, 1f) * len, scratch, true)
    }

    /** Strokes the scratch path: the strand's dark edge, or its narrower gold core. */
    private fun strand(c: Canvas, cap: Paint.Cap, edge: Boolean) {
        stroke.strokeCap = cap
        if (edge) {
            stroke.shader = null
            stroke.color = FOIL_EDGE
            stroke.strokeWidth = STROKE
        } else {
            stroke.shader = foil
            stroke.strokeWidth = STROKE - 1.6f
        }
        c.drawPath(scratch, stroke)
        stroke.shader = null
    }
}
