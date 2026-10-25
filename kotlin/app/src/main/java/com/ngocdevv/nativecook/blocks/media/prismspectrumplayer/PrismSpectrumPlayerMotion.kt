package com.ngocdevv.nativecook.blocks.media.prismspectrumplayer

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Prism Spectrum Player — timing and geometry (a port of prismSpectrumMotion.ts). Every value the
 screen shows is a pure function of the loop time t (seconds, 0 ≤ t < LOOP), so the frame at
 t = LOOP equals the frame at t = 0.

 autoPlay timeline (LOOP = 8 s):
   0.0 – 1.2   rest: paused, a faint beam, short dim bars (the poster).
   1.08 – 1.32 the play button is pressed (scale 1 → 0.92 → 1).
   1.2 – 1.8   play: the beam brightens, the fan opens, the bars rise (ease-out cubic);
               the icon morphs play → pause over 1.2 – 1.45.
   1.8 – 6.4   playing; the elapsed time runs 1:12 → 1:17.
   3.0 – 5.6   the prism turns 0 → +18° → 0 → −18° → 0; the fan follows 90 ms behind and swings
               1.7× as far, opening wider as it turns.
   6.28 – 6.52 pause pressed; 6.4 – 7.0 the beam dims, bars fall (ease-in-out cubic).
   6.95 – 7.25 "back 5 s" pressed; 7.0 – 7.7 the scrubber glides back to 1:12.
   7.0 – 8.0   rest.
 */

/** The autoPlay loop in seconds (the manifest's recording.loop). */
const val LOOP = 8f
/** Beam strength while paused. */
internal const val PRISM_REST_BEAM = 0.4f
/** Elapsed seconds at rest (1:12) and the track's length (3:42). */
internal const val PRISM_REST_ELAPSED = 72f
internal const val PRISM_TRACK_LENGTH = 222f
private const val PIF = PI.toFloat()
/** How far the prism may turn (±25°) and how far the script turns it (±18°). */
internal const val PRISM_ROT_LIMIT = 25 * PIF / 180
private const val SWEEP = 18 * PIF / 180
/** The fan's lag behind the prism in the script (s), and how much more it swings. */
private const val FAN_LAG = 0.09f
internal const val PRISM_FAN_GAIN = 1.7f

internal val PrismEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeOutCubic(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeInOutSine(x: Float) = 0.5f - 0.5f * cos(PIF * x)
/** 0 → 1 → 0 over [a, b]. */
private fun bump(t: Float, a: Float, b: Float) = sin(PIF * seg(t, a, b))
private fun sweep(t: Float) = SWEEP * sin(2 * PIF * easeInOutSine(seg(t, 3.0f, 5.6f)))

/** What the screen shows at one moment. */
internal class PrismPose {
    /** Prism angle and the fan's (lagging) angle, radians. */
    var rot = 0f
    var fan = 0f
    /** 0 paused … 1 playing (bars), the beam's strength, and the play/pause icon morph. */
    var play = 0f
    var beam = PRISM_REST_BEAM
    var icon = 0f
    /** Elapsed seconds of the track. */
    var elapsed = PRISM_REST_ELAPSED
    /** Press amounts of the play and back-5 buttons (0 … 1). */
    var pressPlay = 0f
    var pressBack = 0f
}

internal fun prismLoopPose(t: Float, p: PrismPose): PrismPose {
    val play = easeOutCubic(seg(t, 1.2f, 1.8f)) * (1 - easeInOutCubic(seg(t, 6.4f, 7.0f)))
    val icon = easeInOutCubic(seg(t, 1.2f, 1.45f)) * (1 - easeInOutCubic(seg(t, 6.4f, 6.65f)))
    val played = seg(t, 1.35f, 6.55f) * 5.2f
    val back = easeInOutCubic(seg(t, 7.0f, 7.7f))
    p.rot = sweep(t)
    p.fan = sweep(t - FAN_LAG)
    p.play = play
    p.beam = PRISM_REST_BEAM + (1 - PRISM_REST_BEAM) * play
    p.icon = icon
    p.elapsed = PRISM_REST_ELAPSED + played * (1 - back)
    p.pressPlay = bump(t, 1.08f, 1.32f) + bump(t, 6.28f, 6.52f)
    p.pressBack = bump(t, 6.95f, 7.25f)
    return p
}

/** Past ±limit the prism resists: limit + a falling-off share of the overshoot. */
internal fun prismRubberBand(x: Float, limit: Float): Float {
    val a = abs(x)
    if (a <= limit) return x
    val over = a - limit
    return sign(x) * (limit + (over * 0.55f) / (1 + over / 0.35f))
}

/** Screen layout in dp from the canvas size and the safe area. */
internal class PrismGeo(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    private val heroTop = top + 56
    val titleTop = (h * 0.585f).roundToInt().toFloat()
    val heroBottom = titleTop - 12
    val u = min(w / 393f, (heroBottom - heroTop) / 371f)
    val side = 204 * u
    /** Prism centroid and circumradius. */
    val r = side / sqrt(3f)
    val cx = w * 0.32f
    val cy = heroTop + (heroBottom - heroTop) * 0.5f
    // The exit point at rest sits at cx + 0.433 r; the bars must stop short of the right edge.
    private val exitX = cx + 0.433f * r
    /** Shortest and longest bar. */
    val lmin = 48 * u
    val lmax = min(w - exitX - 18, 214 * u)
    val scrubY = titleTop + 88
    val controlsY = min(scrubY + 86, h - bottom - 128)
}

/** Where the beam enters (middle of the left face) and leaves (middle of the right face): inX, inY, outX, outY. */
internal fun prismPorts(g: PrismGeo, rot: Float, out: FloatArray) {
    val c = cos(rot)
    val s = sin(rot)
    val lx = -0.433f * g.r
    val ly = -0.25f * g.r
    val ex = 0.433f * g.r
    out[0] = g.cx + c * lx - s * ly
    out[1] = g.cy + s * lx + c * ly
    out[2] = g.cx + c * ex - s * ly
    out[3] = g.cy + s * ex + c * ly
}

/** The fan's centre direction for a fan angle. */
internal fun prismFanDir(fan: Float) = 0.4f + PRISM_FAN_GAIN * fan
/** The fan's total spread (radians) for a fan angle and an opening. */
internal fun prismFanSpread(fan: Float, open: Float) = 0.48f * (1 + (0.9f * abs(fan)) / PRISM_ROT_LIMIT) * (0.78f + 0.22f * open)

internal fun prismFormatTime(seconds: Float): String {
    val s = max(0, floor(seconds).toInt())
    val m = s / 60
    val r = s % 60
    return "$m:${if (r < 10) "0" else ""}$r"
}

private fun fract(x: Float) = x - floor(x)
private fun h11(x: Float) = fract(sin(x * 78.233f) * 43758.5453f)

/** A band's level 0 … 1 at clock t, as in the shader (used by the drawn fallback). */
internal fun prismLevel(i: Int, t: Float, play: Float): Float {
    val fi = i.toFloat()
    val a = h11(fi * 7.13f + 1f)
    val b = h11(fi * 3.71f + 2f)
    val c = h11(fi * 5.31f + 3f)
    val w = 2 * PIF * t / LOOP
    val k1 = 2f + floor(a * 3f)
    val k2 = 7f + floor(b * 6f)
    val k3 = 19f + floor(c * 9f)
    val kick = (0.5f + 0.5f * cos(w * 16f)).pow(6f)
    val low = 1f - fi / 11f
    val v = 0.40f + 0.20f * sin(w * k1 + b * 2 * PIF) + 0.14f * sin(w * k2 + c * 2 * PIF) +
        0.09f * sin(w * k3 + a * 2 * PIF) + kick * (0.10f + 0.30f * low) - 0.08f * (1f - low)
    val idle = 0.13f + 0.10f * sin(PIF * (fi + 0.5f) / 12f) + 0.05f * a
    return idle + (v.coerceIn(0f, 1f) - idle) * play
}

/** A tweened or sprung value, stepped by the frame loop (Reanimated's withTiming / withSpring). */
internal class PrismAnim(var value: Float) {
    private var mode = 0
    private var from = 0f
    private var to = 0f
    private var t0 = 0.0
    private var dur = 0f
    private var vel = 0f
    private var k = 0f
    private var c = 0f
    val active get() = mode != 0

    fun set(x: Float) {
        value = x
        mode = 0
        vel = 0f
    }

    /** Eases from the current value to `target` over `duration` seconds from time n. */
    fun tween(n: Double, target: Float, duration: Float) {
        from = value
        to = target
        t0 = n
        dur = duration
        mode = 1
    }

    /** A spring towards `target` that settles in about `duration` seconds, starting at velocity v. */
    fun spring(target: Float, v: Float, duration: Float, zeta: Float) {
        to = target
        vel = v
        val w = 2 * PIF / duration
        k = w * w
        c = 2 * zeta * w
        mode = 2
    }

    fun step(n: Double, dt: Float) {
        when (mode) {
            1 -> {
                val x = if (dur <= 0f) 1f else ((n - t0) / dur).toFloat().coerceIn(0f, 1f)
                value = from + (to - from) * PrismEaseInOut.transform(x)
                if (x >= 1f) mode = 0
            }
            2 -> {
                val a = k * (to - value) - c * vel
                vel += a * dt
                value += vel * dt
                if (abs(vel) < 1e-4f && abs(to - value) < 1e-5f) {
                    value = to
                    mode = 0
                }
            }
        }
    }
}
