package com.ngocdevv.nativecook.blocks.charts.energyscrubareacard

import androidx.compose.ui.graphics.Path
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

data class Film(val cursor: Double, val held: Double, val down: Double, val eco: Double, val knob: Double)
data class Frame(val width: Float, val top: Float, val bottom: Float, val min: Double, val max: Double)
const val LOOP = 9.0
internal object Motion {
    const val pressAt = 0.8; const val glideAt = 1.0; const val glideFor = 3.2; const val backFor = 0.8; const val liftAt = 5.6; const val ecoOnAt = 6.0; const val ecoOffAt = 7.6
    const val morphFor = 0.8; const val knobFor = 0.35; const val dotIn = 0.3; const val dotOut = 0.25; const val userHold = 3.0; const val returnFor = 0.45
    const val startDay = 0; const val turnDay = 6; const val restDay = 2; const val backOvershoot = 1.4
    fun clamp(t: Double) = t.coerceIn(0.0, 1.0)
    fun easeInOut(t: Double): Double { val u = clamp(t); return if (u < 0.5) 4 * u.pow(3) else 1 - (-2 * u + 2).pow(3) / 2 }
    fun backOut(t: Double): Double { val u = clamp(t); return 1 + (backOvershoot + 1) * (u - 1).pow(3) + backOvershoot * (u - 1).pow(2) }
    fun dayX(day: Int) = (day + 0.5) / 7
    fun filmAt(t: Double, still: Boolean): Film {
        val down = if (t >= pressAt && t < liftAt) 1.0 else 0.0
        val glide = easeInOut((t - glideAt) / glideFor); val back = easeInOut((t - glideAt - glideFor) / backFor)
        val cursor = if (still) dayX(restDay) else dayX(startDay) + (dayX(turnDay) - dayX(startDay)) * glide + (dayX(restDay) - dayX(turnDay)) * back
        val held = if (still) down else if (t < liftAt) { if (t < pressAt) 0.0 else backOut((t - pressAt) / dotIn) } else 1 - clamp((t - liftAt) / dotOut)
        val eco = if (still) { if (t >= ecoOnAt && t < ecoOffAt) 1.0 else 0.0 } else easeInOut((t - ecoOnAt) / morphFor) - easeInOut((t - ecoOffAt) / morphFor)
        val knob = if (still) eco else backOut((t - ecoOnAt) / knobFor) - backOut((t - ecoOffAt) / knobFor)
        return Film(cursor, held, down, eco, knob)
    }
    fun mix(a: List<Double>, b: List<Double>, t: Double) = a.indices.map { a[it] + (b[it] - a[it]) * t }
    fun slope(values: List<Double>, i: Int) = when (i) { 0 -> values[1] - values[0]; values.lastIndex -> values[i] - values[i - 1]; else -> (values[i + 1] - values[i - 1]) / 2 }
    fun valueAt(values: List<Double>, x: Double): Double {
        val steps = values.size - 1; val at = clamp(x) * steps; val i = min(steps - 1, floor(at).toInt()); val t = at - i; val t2 = t * t; val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * values[i] + (t3 - 2 * t2 + t) * slope(values, i) + (-2 * t3 + 3 * t2) * values[i + 1] + (t3 - t2) * slope(values, i + 1)
    }
    fun yOf(value: Double, frame: Frame) = (frame.bottom - (value - frame.min) / (frame.max - frame.min) * (frame.bottom - frame.top)).toFloat()
    fun curvePath(values: List<Double>, frame: Frame, floor: Float? = null): Path {
        val path = Path(); val steps = values.size - 1; val dx = frame.width / steps
        path.moveTo(0f, yOf(values[0], frame))
        for (i in 0 until steps) path.cubicTo(i * dx + dx / 3, yOf(values[i] + slope(values, i) / 3, frame), (i + 1) * dx - dx / 3, yOf(values[i + 1] - slope(values, i + 1) / 3, frame), (i + 1) * dx, yOf(values[i + 1], frame))
        if (floor != null) { path.lineTo(frame.width, floor); path.lineTo(0f, floor); path.close() }
        return path
    }
}
