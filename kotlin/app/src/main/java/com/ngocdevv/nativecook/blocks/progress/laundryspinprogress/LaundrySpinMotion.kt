package com.ngocdevv.nativecook.blocks.progress.laundryspinprogress

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Laundry Spin Progress — the wash cycle's script and the clothes' motion, as pure functions of the
 loop time (frame at t = LOOP equals frame at 0), ported from laundrySpinMotion.ts.

 A scene is [drum angle, speed rev/s, tumble, plaster, water level, suds, fog, slosh].

 autoPlay script (LOOP = 10 s):
   0.0 – 1.6   rest: water calm, clothes settled, a few suds.
   1.6 – 4.2   wash: the drum starts (0.8 rev/s), the clothes tumble, suds build.
   4.2 – 5.6   drain (to 12 %) and rinse (back to 32 %, slower, fewer suds).
   5.6 – 8.0   spin: the water drains, the drum speeds up to 11 rev/s, the clothes plaster to the
               wall (motion blur), then it slows to a stop.
   8.0 – 8.6   done: the clothes drop off the wall into a pile.
   8.6 – 10.0  the glass fogs up, the water and suds come back behind it, the fog clears.
 The drum turns a whole number of times per loop, so its holes and lifters match at the seam.
 */

internal const val LOOP = 10.0
internal const val S_ANGLE = 0
internal const val S_SPEED = 1
internal const val S_TUMBLE = 2
internal const val S_PLASTER = 3
internal const val S_LEVEL = 4
internal const val S_SUDS = 5
internal const val S_FOG = 6
internal const val S_SLOSH = 7
internal const val LaundryGarments = 6

private val SPEED_T = doubleArrayOf(0.0, 1.6, 2.0, 4.0, 4.3, 5.6, 6.0, 7.0, 7.4, 8.0, 10.0)
private val SPEED_V = doubleArrayOf(0.0, 0.0, 0.8, 0.8, 0.4, 0.5, 0.6, 11.0, 11.0, 0.0, 0.0)

private fun clamp01(x: Double) = min(1.0, max(0.0, x))
private fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
private fun easeOut(x: Double): Double {
    val u = 1 - x
    return 1 - u * u * u
}
internal fun laundryEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun hash(i: Int): Double {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return s - floor(s)
}

private fun speedAt(t: Double): Double {
    for (i in 0 until SPEED_T.size - 1) {
        if (t <= SPEED_T[i + 1]) return SPEED_V[i] + (SPEED_V[i + 1] - SPEED_V[i]) * (t - SPEED_T[i]) / (SPEED_T[i + 1] - SPEED_T[i])
    }
    return 0.0
}

private fun revsRaw(t: Double): Double {
    var sum = 0.0
    for (i in 0 until SPEED_T.size - 1) {
        if (t <= SPEED_T[i]) break
        val e = min(t, SPEED_T[i + 1])
        val vE = SPEED_V[i] + (SPEED_V[i + 1] - SPEED_V[i]) * (e - SPEED_T[i]) / (SPEED_T[i + 1] - SPEED_T[i])
        sum += (SPEED_V[i] + vE) / 2 * (e - SPEED_T[i])
    }
    return sum
}

private val TOTAL = revsRaw(LOOP)
private val WHOLE = TOTAL.roundToInt().toDouble()

private fun angleAt(t: Double) = revsRaw(t) * WHOLE * PI * 2 / TOTAL

internal fun laundryRestScene() = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.38, 0.15, 0.0, 0.0)

internal fun laundrySceneAt(t: Double): DoubleArray {
    val s = laundryRestScene()
    val v = speedAt(t)
    s[S_ANGLE] = angleAt(t)
    s[S_SPEED] = v
    val wash = laundryEaseInOut(seg(t, 1.6, 2.2)) * (1 - laundryEaseInOut(seg(t, 4.2, 4.6)))
    val rinse = laundryEaseInOut(seg(t, 4.7, 5.1)) * (1 - laundryEaseInOut(seg(t, 5.6, 6.0)))
    s[S_TUMBLE] = max(wash, rinse * 0.8)
    s[S_PLASTER] = laundryEaseInOut(seg(t, 6.0, 6.7)) * (1 - easeOut(seg(t, 7.9, 8.4)))
    var level = 0.38
    if (t >= 4.2) level = 0.38 - 0.26 * laundryEaseInOut(seg(t, 4.2, 4.7)) + 0.2 * laundryEaseInOut(seg(t, 4.8, 5.4))
    if (t >= 5.6) level = 0.32 * (1 - laundryEaseInOut(seg(t, 5.6, 6.1)))
    if (t >= 8.8) level = 0.38 * laundryEaseInOut(seg(t, 8.8, 9.4))
    s[S_LEVEL] = level
    var suds = 0.15 + 0.85 * easeOut(seg(t, 1.8, 4.0))
    if (t >= 4.2) suds = 1 - 0.7 * laundryEaseInOut(seg(t, 4.2, 5.2))
    if (t >= 5.6) suds = 0.3 * (1 - laundryEaseInOut(seg(t, 5.6, 6.2)))
    if (t >= 8.8) suds = 0.15 * laundryEaseInOut(seg(t, 8.8, 9.4))
    s[S_SUDS] = suds
    s[S_FOG] = easeOut(seg(t, 8.6, 9.2)) * (1 - laundryEaseInOut(seg(t, 9.4, 10.0)))
    s[S_SLOSH] = min(1.0, v / 0.8) * (1 - s[S_PLASTER])
    return s
}

private val PILE = arrayOf(
    doubleArrayOf(-0.46, 0.56, 0.5),
    doubleArrayOf(0.06, 0.66, -0.3),
    doubleArrayOf(0.5, 0.5, 1.0),
    doubleArrayOf(-0.2, 0.36, 1.7),
    doubleArrayOf(0.3, 0.28, -1.2),
    doubleArrayOf(-0.04, 0.12, 2.5),
)

/** Garment i for a scene, written into out at 5·i: [x, y (drum units), rotation, blur, stretch]. */
internal fun laundryGarmentAt(i: Int, s: DoubleArray, out: DoubleArray) {
    val pile = PILE[i]
    val a = s[S_ANGLE]
    val phase = ((a / (PI * 2) + hash(i + 3) * 0.9 + i * 0.17) % 1.0 + 1.0) % 1.0
    val ride = 0.58
    val tx: Double
    val ty: Double
    var stretch = 0.0
    val r = 0.6 - 0.06 * hash(i + 9)
    val a0 = -110 * PI / 180
    val a1 = 30 * PI / 180
    if (phase < ride) {
        val an = a0 + (a1 - a0) * (phase / ride)
        tx = cos(an) * r
        ty = -sin(an) * r
    } else {
        val f = (phase - ride) / (1 - ride)
        val x0 = cos(a1) * r
        val y0 = -sin(a1) * r
        val x1 = cos(a0) * r
        val y1 = -sin(a0) * r
        tx = x0 + (x1 - x0) * f
        ty = y0 + (y1 - y0) * f * f - 0.25 * sin(PI * f) * (1 - f)
        stretch = sin(PI * f) * 0.35
    }
    val tw = s[S_TUMBLE]
    var x = pile[0] + (tx - pile[0]) * tw
    var y = pile[1] + (ty - pile[1]) * tw
    var rot = pile[2] + phase * PI * 2 * 0.6 * tw
    val pa = a + i.toDouble() / LaundryGarments * PI * 2 + 0.3 * hash(i + 5)
    val pw = s[S_PLASTER]
    x += (cos(pa) * 0.74 - x) * pw
    y += (-sin(pa) * 0.74 - y) * pw
    rot += (-pa + PI / 2 - rot) * pw
    val blur = min(0.9, s[S_SPEED] * 0.09) * pw
    val o = i * 5
    out[o] = x
    out[o + 1] = y
    out[o + 2] = rot
    out[o + 3] = blur
    out[o + 4] = stretch * tw * (1 - pw)
}

internal val LaundryPhases = listOf("Ready to wash", "Washing", "Rinsing", "Spinning", "Done")

internal fun laundryPhaseAt(t: Double) = when {
    t < 1.6 -> 0
    t < 4.2 -> 1
    t < 5.6 -> 2
    t < 8.0 -> 3
    t < 9.4 -> 4
    else -> 0
}

internal fun laundryProgressAt(t: Double): Double {
    if (t < 1.6) return 0.0
    if (t < 8.0) {
        if (t < 4.2) return 0.25 * seg(t, 1.6, 4.2)
        if (t < 5.6) return 0.25 + 0.25 * seg(t, 4.2, 5.6)
        return 0.5 + 0.25 * seg(t, 5.6, 8.0)
    }
    if (t < 9.0) return 0.75 + 0.25 * easeOut(seg(t, 8.0, 8.5))
    return 1 - laundryEaseInOut(seg(t, 9.0, 9.6))
}

/** Minutes left in a 42-minute cycle at a progress. */
internal fun laundryMinutesLeft(p: Double) = floor(42 * (1 - p) + 0.5)

/** A hand-turned drum's scene from what it was when grabbed and how fast it turns now. */
internal fun laundryHandScene(base: DoubleArray, angle: Double, speed: Double): DoubleArray {
    val s = base.copyOf()
    val v = abs(speed)
    val plaster = min(1.0, max(0.0, (v - 2.5) / 2))
    s[S_ANGLE] = angle
    s[S_SPEED] = v
    s[S_PLASTER] = plaster * plaster * (3 - 2 * plaster)
    s[S_TUMBLE] = min(1.0, v / 0.4) * (1 - s[S_PLASTER])
    s[S_SLOSH] = min(1.0, v / 0.8) * (1 - s[S_PLASTER])
    s[S_FOG] = 0.0
    return s
}
