package com.ngocdevv.nativecook.blocks.charts.barstolinesmorph

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val HOURS = 24
internal const val COLLAPSE = 0.25f
internal const val STEP = 0.008f
internal const val TRAVEL = 0.45f
internal const val SECOND = 0.04f
internal const val GROW = 0.25f
internal const val DRAW = 0.45f
internal const val LABEL_IN = 0.2f
internal const val LABEL_OUT = 0.15f
internal const val A_END = COLLAPSE + (HOURS - 1) * STEP
internal const val B_END = A_END + TRAVEL + SECOND
internal val C_END = B_END + max(GROW + (HOURS - 1) * STEP, DRAW)
internal val TOTAL = C_END + LABEL_IN
internal const val FADE = 0.2f

internal data class Look(val barMax: Float, val barShare: Float, val barRadius: Float, val dot: Float, val gap: Float)

internal class Scene {
    val bars = Path()
    val dots1 = Path()
    val dots2 = Path()
    val line1 = Path()
    val line2 = Path()
    var trim = 1f
    var mix = 1f
    var opacity = 1f
    var lineCount = 0
    var above = 0f
    var below = 0f
}

internal fun ease(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

internal fun smooth(t: Float, from: Float, to: Float) = ease((t - from) / (to - from))
internal fun layoutFade(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return if (u < 0.5f) 2f * u * u else 1f - 2f * (1f - u) * (1f - u)
}
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
internal fun yOf(value: Float, plot: Plot, height: Float) = (height * (1f - (value - plot.min) / (plot.max - plot.min))).coerceIn(-40f, height + 40f)

private fun addLine(path: Path, values: List<Float>, plot: Plot, pitch: Float, height: Float) {
    for (i in 0 until HOURS) {
        val x = (i + 0.5f) * pitch
        val y = yOf(values[i], plot, height)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
}

internal fun scene(p: Float, from: Plot, to: Plot, width: Float, height: Float, look: Look, still: Boolean): Scene {
    val pitch = width / HOURS
    val bw = min(look.barMax, pitch * look.barShare)
    val out = Scene()
    var plot = to
    var stage = 3
    if (still) {
        plot = if (p < FADE) from else to
        out.opacity = if (p < FADE) 1f - p / FADE else ((p - FADE) / FADE).coerceIn(0f, 1f)
        out.mix = if (p < FADE) 0f else 1f
    } else if (p < A_END) {
        plot = from; stage = 1; out.mix = 0f
    } else if (p < B_END) {
        stage = 2
    }
    out.lineCount = plot.lines
    if (stage == 1 || stage == 3) {
        for (i in 0 until HOURS) {
            val x = (i + 0.5f) * pitch
            val ya = yOf(plot.a[i], plot, height)
            val yb = yOf(plot.b[i], plot, height)
            if (plot.bars) {
                val fold = if (still) 0f else if (stage == 1) ease((p - i * STEP) / COLLAPSE) else 1f - ease((p - B_END - i * STEP) / GROW)
                val centre = (ya + yb) / 2f
                val half = lerp(max(bw / 2f, abs(ya - yb) / 2f), bw / 2f, fold)
                val radius = lerp(look.barRadius, bw / 2f, fold)
                out.bars.addRoundRect(RoundRect(x - bw / 2f, centre - half, x + bw / 2f, centre + half, radius, radius))
            } else {
                out.dots1.addOval(Rect(x - look.dot, ya - look.dot, x + look.dot, ya + look.dot))
                if (plot.lines == 2) out.dots2.addOval(Rect(x - look.dot, yb - look.dot, x + look.dot, yb + look.dot))
            }
        }
        if (!plot.bars) {
            addLine(out.line1, plot.a, plot, pitch, height)
            if (plot.lines == 2) addLine(out.line2, plot.b, plot, pitch, height)
            if (!still) out.trim = if (stage == 1) 1f - ease(p / A_END) else ease((p - B_END) / DRAW)
        }
    } else {
        val e1 = ease((p - A_END) / TRAVEL)
        val e2 = ease((p - A_END - SECOND) / TRAVEL)
        out.mix = e1
        out.trim = 0f
        val r1 = lerp(if (from.bars) bw / 2f else look.dot, if (to.bars) bw / 2f else look.dot, e1)
        val r2 = lerp(if (from.bars) bw / 2f else look.dot, if (to.bars) bw / 2f else look.dot, e2)
        for (i in 0 until HOURS) {
            val x = (i + 0.5f) * pitch
            val fa = yOf(from.a[i], from, height)
            val fb = yOf(from.b[i], from, height)
            val ta = yOf(to.a[i], to, height)
            val tb = yOf(to.b[i], to, height)
            val f1 = if (from.bars) (fa + fb) / 2f else fa
            val f2 = if (from.bars) (fa + fb) / 2f else fb
            val t1 = if (to.bars) (ta + tb) / 2f else ta
            val t2 = if (to.bars) (ta + tb) / 2f else tb
            val y1 = lerp(f1, t1, e1)
            val y2 = lerp(f2, t2, e2)
            out.dots1.addOval(Rect(x - r1, y1 - r1, x + r1, y1 + r1))
            out.dots2.addOval(Rect(x - r2, y2 - r2, x + r2, y2 + r2))
        }
    }
    val travelled = if (stage == 2) ease((p - A_END) / TRAVEL) else if (stage == 1) 0f else 1f
    out.above = lerp(yOf(from.above, from, height), yOf(to.above, to, height), if (still) out.mix else travelled)
    out.below = lerp(yOf(from.below, from, height), yOf(to.below, to, height), if (still) out.mix else travelled)
    return out
}

/** Bars leave a gap at the limit; lines only change colour there. */
internal fun limitClips(above: Float, below: Float, width: Float, height: Float, gap: Float): Pair<Path, Path> {
    val red = Path()
    val plain = Path()
    if (above - gap > 0f) red.addRect(Rect(0f, -50f, width, above - gap))
    if (below + gap < height + 50f) red.addRect(Rect(0f, below + gap, width, height + 50f))
    val top = max(-50f, above + gap)
    val bottom = min(height + 50f, below - gap)
    if (bottom > top) plain.addRect(Rect(0f, top, width, bottom))
    return red to plain
}

/** The source requests RGB interpolation with gamma 1, so mix sRGB components directly. */
internal fun mixColor(from: Color, to: Color, t: Float): Color {
    val a = from.convert(ColorSpaces.Srgb)
    val b = to.convert(ColorSpaces.Srgb)
    return Color(lerp(a.red, b.red, t), lerp(a.green, b.green, t), lerp(a.blue, b.blue, t), lerp(a.alpha, b.alpha, t))
}
