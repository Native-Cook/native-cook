package com.ngocdevv.nativecook.blocks.inputs.inksignaturepad

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The signature autoPlay writes: "Mara Ellison" in a quick cursive hand, an i-dot and an underline
// flourish. Each stroke is a run of key points in a small grid (x 0…102, the letters' baseline at
// y 40.5, the x-height 9 units above it and the loops of the capitals and the l's up to y 12), drawn
// through as a Catmull-Rom curve and timed like a hand: slower in the tight turns, faster on the long
// runs, quickest on the flourish.

private class ScriptStroke(
    /** Pause before the pen lands, in seconds. */
    val gap: Double,
    /** Speed on a straight run, in grid units a second. */
    val speed: Double,
    vararg val keys: Double,
)

private const val BASELINE = 40.5
// The hand leans right: x moves this many units per unit of height above the baseline.
private const val SLANT = 0.22
// "Ellison", its dot and nothing else start this far right of where their keys say, to open a word
// space after "Mara".
private const val WORD_SPACE = 4.0

private val STROKES = listOf(
    // M, a, r, a in one stroke.
    ScriptStroke(
        0.0, 235.0,
        2.0, 41.0, 4.0, 33.0, 6.5, 20.0, 8.5, 13.5, 10.0, 13.0, 11.0, 16.0, 12.0, 26.0, 13.0, 38.0, 13.5, 40.0,
        13.8, 36.0, 15.0, 24.0, 17.0, 16.0, 18.5, 15.0, 19.5, 18.0, 20.5, 28.0, 21.5, 38.0, 22.5, 40.5,
        24.5, 39.0, 26.5, 35.0, 29.0, 32.0, 30.0, 31.5,
        28.0, 31.0, 26.3, 33.5, 26.2, 37.5, 27.8, 40.0, 30.0, 39.0, 31.3, 35.5, 31.8, 31.5,
        31.6, 36.0, 32.0, 39.8, 33.5, 40.3, 35.0, 38.0,
        36.2, 33.0, 37.2, 31.2, 38.4, 32.6, 39.4, 31.4, 40.0, 33.0, 40.2, 38.0, 41.0, 40.2, 42.5, 38.5,
        44.5, 33.0, 46.0, 31.6, 44.0, 31.2, 42.6, 34.0, 42.8, 38.2, 44.6, 40.2, 46.4, 38.6,
        47.2, 34.5, 47.6, 31.2, 47.4, 36.0, 47.9, 39.8, 49.5, 40.4, 51.5, 38.0,
    ),
    // E, l, l, i, s, o, n in one stroke.
    ScriptStroke(
        0.26, 245.0,
        62.0, 17.0, 60.5, 14.5, 57.5, 14.0, 55.5, 16.5, 56.5, 21.0, 59.5, 24.0,
        56.0, 25.0, 53.5, 28.5, 53.5, 34.0, 55.5, 39.0, 59.5, 40.5, 63.0, 38.5, 65.0, 35.0,
        67.0, 28.0, 69.0, 19.0, 69.5, 14.0, 68.5, 12.0, 67.2, 13.5, 66.8, 19.0, 67.2, 30.0, 67.8, 38.5,
        69.0, 40.4, 70.8, 39.0,
        72.5, 30.0, 74.0, 20.0, 74.3, 14.5, 73.3, 12.6, 72.1, 14.2, 71.8, 20.0, 72.2, 31.0, 72.8, 39.0,
        74.0, 40.5, 75.6, 38.4,
        76.6, 34.0, 77.0, 31.5, 77.1, 36.0, 77.6, 40.0, 79.0, 40.0, 80.3, 37.5,
        81.6, 31.2, 83.6, 35.5, 83.8, 39.0, 82.0, 40.6, 80.6, 39.6, 82.4, 40.4, 84.6, 39.0,
        86.5, 33.0, 88.4, 31.4, 86.6, 32.5, 85.9, 36.5, 87.2, 40.2, 89.4, 39.6, 90.3, 35.5,
        89.4, 31.8, 88.3, 32.4, 90.5, 32.8, 92.0, 32.5,
        93.0, 32.0, 93.4, 36.0, 93.4, 40.2, 93.8, 35.0, 95.6, 31.6, 97.4, 32.6, 97.8, 36.5,
        98.0, 40.0, 99.5, 40.6, 101.5, 38.0,
    ),
    // The dot of the i.
    ScriptStroke(0.22, 60.0, 77.6, 25.6, 78.4, 25.1),
    // The flourish under the name, right to left and back.
    ScriptStroke(
        0.24, 420.0,
        99.0, 44.5, 85.0, 46.0, 65.0, 47.0, 45.0, 47.2, 25.0, 46.5, 12.0, 45.0, 7.0, 43.0, 9.5, 41.8, 14.0, 42.3,
    ),
)

// Samples are taken about this far apart along a stroke, in grid units.
private const val STEP = 0.45
// How much a turn slows the hand: speed × (FLOOR + (1 − FLOOR) / (1 + TURN · curvature)).
private const val FLOOR = 0.32
private const val TURN = 9.0
// The pen speeds up over its first RAMP units and slows over its last RAMP units.
private const val RAMP = 5.0

/** The key points (x, y pairs) drawn through as one smooth curve, sampled about STEP apart. */
private fun densify(keys: DoubleArray): List<Pair<Double, Double>> {
    val n = keys.size / 2
    fun x(i: Int) = keys[i * 2]
    fun y(i: Int) = keys[i * 2 + 1]
    val out = mutableListOf(x(0) to y(0))
    for (i in 0 until n - 1) {
        val a = max(0, i - 1)
        val d = min(n - 1, i + 2)
        val steps = max(1, ceil(hypot(x(i + 1) - x(i), y(i + 1) - y(i)) / STEP).toInt())
        for (k in 1..steps) {
            val u = k.toDouble() / steps
            out.add(catmull(x(a), x(i), x(i + 1), x(d), u) to catmull(y(a), y(i), y(i + 1), y(d), u))
        }
    }
    return out
}

/**
 * The signature laid out between [left] and [right] with its baseline at [baseline] (dp), timed sample
 * by sample, as ink samples (x, y, ms from its start, 0, stroke), and how many strokes it has.
 */
internal fun signatureScript(left: Double, right: Double, baseline: Double): Pair<Ink, Int> {
    val laid = STROKES.mapIndexed { index, s ->
        DoubleArray(s.keys.size) { i ->
            if (i % 2 == 0) s.keys[i] + (BASELINE - s.keys[i + 1]) * SLANT + (if (index == 1 || index == 2) WORD_SPACE else 0.0) else s.keys[i]
        }
    }
    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    laid.forEach { keys -> for (i in keys.indices step 2) { minX = min(minX, keys[i]); maxX = max(maxX, keys[i]) } }
    val unit = (right - left) / (maxX - minX)
    val out = Ink()
    var clock = 0.0
    laid.forEachIndexed { index, keys ->
        val s = STROKES[index]
        clock += s.gap
        val points = densify(keys)
        // Arc length to every sample, for the ramps at both ends.
        val along = DoubleArray(points.size)
        for (i in 1 until points.size) along[i] = along[i - 1] + hypot(points[i].first - points[i - 1].first, points[i].second - points[i - 1].second)
        val total = along.last()
        for (i in points.indices) {
            if (i > 0) {
                val ds = along[i] - along[i - 1]
                // Curvature from the turn between the segment before and the one after.
                val a = points[max(0, i - 1)]
                val b = points[i]
                val c = points[min(points.size - 1, i + 1)]
                val h1 = atan2(b.second - a.second, b.first - a.first)
                val h2 = atan2(c.second - b.second, c.first - b.first)
                var turn = abs(h2 - h1)
                if (turn > PI) turn = 2 * PI - turn
                val curvature = turn / max(ds, 1e-3)
                val ends = min(1.0, 0.35 + (0.65 * min(along[i], total - along[i])) / RAMP)
                val speed = s.speed * (FLOOR + (1 - FLOOR) / (1 + TURN * curvature)) * ends
                clock += ds / max(speed, 1.0)
            }
            out.push(left + (points[i].first - minX) * unit, baseline + (points[i].second - BASELINE) * unit, clock * 1000, 0.0, index.toDouble())
        }
    }
    return out to STROKES.size
}

// The wax seal: a disc of red sealing wax pressed with a monogram. The rim is not a circle: wax
// spreads unevenly, so its radius wanders by a few points on three ripples.
internal const val SEAL_SIZE = 92f
private const val SEAL_RADIUS = 34f
private const val SEAL_RING = 24f

private fun rimPath(): Path {
    val ripples = listOf(Triple(5.0, 1.7, 0.3), Triple(9.0, 1.1, 1.2), Triple(14.0, 0.6, 2.1))
    val c = SEAL_SIZE / 2
    val path = Path()
    val steps = 120
    for (i in 0..steps) {
        val a = i.toDouble() / steps * PI * 2
        val r = ripples.fold(SEAL_RADIUS.toDouble()) { sum, (k, amp, phase) -> sum + amp * sin(k * a + phase) }
        val x = (c + r * cos(a)).toFloat()
        val y = (c + r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** The wax seal, SEAL_SIZE dp square. */
@Composable
internal fun WaxSeal(monogram: String, modifier: Modifier = Modifier) {
    val parts = remember {
        val c = SEAL_SIZE / 2
        object {
            val rim = rimPath()
            val wax = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    c - 9, c - 11, SEAL_RADIUS * 1.35f,
                    intArrayOf(0xFFC8333A.toInt(), 0xFF9D1B23.toInt(), 0xFF6A0D14.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
            }
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(0.45f, 30 / 255f, 4 / 255f, 6 / 255f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(5f), BlurMaskFilter.Blur.NORMAL)
            }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // Baskerville SemiBold on iOS; the serif at 600 here (bold before Android 9).
                typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SERIF, 600, false) else Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textSize = 21f
            }
            val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(c - 13, c - 14, 9f, intArrayOf(0x59FFDCD7, 0x00FFDCD7), null, Shader.TileMode.CLAMP)
            }
        }
    }
    Canvas(modifier.size(SEAL_SIZE.dp)) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(density, density)
            val c = SEAL_SIZE / 2
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                native.save()
                native.translate(0f, 3f)
                native.drawPath(parts.rim, parts.shadow)
                native.restore()
            }
            native.drawPath(parts.rim, parts.wax)
            // The pressed ring: a dark groove with light catching its lower edge.
            parts.ring.strokeWidth = 1.4f
            parts.ring.color = android.graphics.Color.argb(0.22f, 1f, 190 / 255f, 180 / 255f)
            native.drawCircle(c, c + 0.8f, SEAL_RING, parts.ring)
            parts.ring.strokeWidth = 1.6f
            parts.ring.color = android.graphics.Color.argb(0.55f, 60 / 255f, 0f, 8 / 255f)
            native.drawCircle(c, c, SEAL_RING, parts.ring)
            // The monogram, its baseline 7 dp under the centre, with a light edge below it.
            val x = c - parts.text.measureText(monogram) / 2
            val y = c + 7
            parts.text.color = android.graphics.Color.argb(0.28f, 1f, 190 / 255f, 180 / 255f)
            native.drawText(monogram, x + 0.6f, y + 0.9f, parts.text)
            parts.text.color = 0xFF5C0910.toInt()
            native.drawText(monogram, x, y, parts.text)
            // A soft gloss on the upper left of the wax.
            native.drawCircle(c - 13, c - 14, 9f, parts.gloss)
            native.restore()
        }
    }
}

/** The pad's buttons' symbols, drawn: an eraser, a turn back, a check. */
@Composable
internal fun PadIcon(kind: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(15.dp)) {
        val u = size.minDimension / 15f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            // eraser: a block tilted 45°, its tip drawn off, on a line.
            0 -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(8.5f * u, 2f * u)
                    lineTo(13f * u, 6.5f * u)
                    lineTo(6.5f * u, 13f * u)
                    lineTo(2f * u, 8.5f * u)
                    close()
                    moveTo(5f * u, 5.5f * u)
                    lineTo(9.5f * u, 10f * u)
                }
                drawPath(p, color, style = stroke)
            }
            // arrow.counterclockwise: most of a circle, its head at the top left.
            1 -> {
                drawArc(color, 200f, 290f, false, topLeft = androidx.compose.ui.geometry.Offset(2f * u, 2.5f * u), size = androidx.compose.ui.geometry.Size(11f * u, 11f * u), style = stroke)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(1.2f * u, 3.2f * u)
                    lineTo(2.6f * u, 6.4f * u)
                    lineTo(5.8f * u, 5.2f * u)
                }
                drawPath(p, color, style = stroke)
            }
            // checkmark.
            else -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(2f * u, 8f * u)
                    lineTo(6f * u, 12f * u)
                    lineTo(13f * u, 3f * u)
                }
                drawPath(p, color, style = stroke)
            }
        }
    }
}
