package com.ngocdevv.nativecook.blocks.buttons.fuseburnbutton

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The Fuse Burn Button's clock and geometry (the React Native fuseBurnMotion.ts). Every value is a
 closed-form function of the loop time t (seconds), so t = LOOP draws the same frame as t = 0.
 Lengths in dp.

   0.0 – 1.4   rest: a braided fuse wrapped round the "Light it up" capsule.
   1.4 – 1.8   a match flares at the fuse's start (the capsule's left end).
   1.8 – 6.2   the spark runs clockwise round the capsule: it catches slowly, speeds up,
               sputters at 40 % (dims and flickers, nearly stops), then runs on, easing in and
               out. Behind it the cord turns to crinkled charcoal and smoke rises.
   6.2 – 6.9   the spark meets the start: 30 sparks burst from the capsule, it lights up amber,
               the label becomes "You're in", the count rolls 211 → 212.
   6.9 – 7.8   hold.
   7.8 – 8.7   the cord re-weaves from the start (drawn back on), the charcoal fades, the label
               and the count go back.
   8.7 – 9.0   rest.
 */

const val LOOP = 9f
private const val TAU = PI.toFloat() * 2f

internal fun fuseClamp01(x: Float) = min(1f, max(0f, x))

internal fun fuseSeg(t: Float, a: Float, b: Float) = fuseClamp01((t - a) / (b - a))

internal fun fuseEaseOut(x: Float): Float {
    val u = 1f - fuseClamp01(x)
    return 1f - u * u * u
}

internal fun fuseEaseInOut(x: Float): Float {
    val u = fuseClamp01(x)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

/** Quadratic in-out: gentler top speed than the cubic (also Reanimated's default timing curve). */
internal fun fuseEaseInOut2(x: Float): Float {
    val u = fuseClamp01(x)
    return if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f).pow(2) / 2f
}

internal fun fuseHash(i: Double): Float {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

internal fun fuseHash(i: Int) = fuseHash(i.toDouble())

/** Where the burn has reached along the fuse (0…1) at loop time t. */
internal fun fuseBurnAt(t: Float): Float {
    val u = fuseSeg(t, 1.8f, 6.2f)
    if (u < 0.38f) return 0.4f * fuseEaseInOut2(u / 0.38f)
    if (u < 0.5f) return 0.4f + 0.012f * ((u - 0.38f) / 0.12f)
    return 0.412f + 0.588f * fuseEaseInOut2((u - 0.5f) / 0.5f)
}

internal class FusePose {
    /** Burnt up to b (0…1 of the fuse); the cord re-woven over [0, w]. */
    var b = 0f
    var w = 0f
    /** The spark's brightness (0 off … 1), the match flare (0…1), its speed along the fuse (dp/s). */
    var spark = 0f
    var flare = 0f
    var speed = 0f
    /** Seconds since the burst (negative = none). */
    var burstAge = -1f
    /** Capsule lit amber, label "You're in", count rolled to 212 (each 0…1). */
    var lit = 0f
    var label = 0f
    var count = 0f
    /** The time particles are animated by (loop time in the loop, the frame clock under a finger). */
    var time = 0f
}

internal fun FusePose.setLoop(t: Float, length: Float) {
    b = fuseBurnAt(t)
    speed = (fuseBurnAt(t + 0.01f) - fuseBurnAt(t - 0.01f)) / 0.02f * length
    val u = fuseSeg(t, 1.8f, 6.2f)
    // The sputter: around 40 % the spark dims and flickers at 5 Hz.
    val dip = sin(PI.toFloat() * fuseSeg(u, 0.36f, 0.52f))
    val flicker = 0.6f + 0.4f * sin(TAU * 45f * t / LOOP)
    var s = fuseEaseOut(fuseSeg(t, 1.4f, 1.55f)) * (1f - 0.7f * dip * flicker)
    s *= 1f - fuseSeg(t, 6.2f, 6.32f)
    spark = s
    flare = sin(PI.toFloat() * fuseSeg(t, 1.4f, 1.8f))
    val back = fuseEaseInOut(fuseSeg(t, 7.8f, 8.4f))
    w = fuseEaseInOut(fuseSeg(t, 7.8f, 8.7f))
    burstAge = if (t >= 6.2f) t - 6.2f else -1f
    lit = fuseEaseOut(fuseSeg(t, 6.2f, 6.5f)) * (1f - back)
    label = fuseEaseOut(fuseSeg(t, 6.3f, 6.8f)) * (1f - back)
    count = fuseEaseInOut(fuseSeg(t, 6.35f, 6.9f)) * (1f - back)
    time = t
}

/** Points sampled evenly along the fuse. */
internal const val FUSE_SAMPLES = 480

/** The fuse's samples and their outward normals. */
internal class FuseLine(
    val xs: FloatArray,
    val ys: FloatArray,
    val nx: FloatArray,
    val ny: FloatArray,
    val length: Float,
)

/**
 * The fuse: a stadium (capsule outline) of half-height r round the capsule, starting at its left
 * end and running clockwise (up over the top first).
 */
internal fun fuseMake(x: Float, y: Float, w: Float, h: Float, offset: Float): FuseLine {
    val r = h / 2f + offset
    val cy = y + h / 2f
    val left = x + h / 2f
    val right = x + w - h / 2f
    val straight = right - left
    val arc = PI.toFloat() * r
    val length = 2f * straight + 2f * arc
    val xs = FloatArray(FUSE_SAMPLES)
    val ys = FloatArray(FUSE_SAMPLES)
    val nx = FloatArray(FUSE_SAMPLES)
    val ny = FloatArray(FUSE_SAMPLES)
    for (i in 0 until FUSE_SAMPLES) {
        var s = i.toFloat() / (FUSE_SAMPLES - 1) * length
        val a: Float
        val px: Float
        val py: Float
        if (s < arc / 2f) {
            a = PI.toFloat() + s / r
            px = left + cos(a) * r
            py = cy + sin(a) * r
        } else {
            s -= arc / 2f
            if (s < straight) {
                a = -PI.toFloat() / 2f
                px = left + s
                py = cy - r
            } else {
                s -= straight
                if (s < arc) {
                    a = -PI.toFloat() / 2f + s / r
                    px = right + cos(a) * r
                    py = cy + sin(a) * r
                } else {
                    s -= arc
                    if (s < straight) {
                        a = PI.toFloat() / 2f
                        px = right - s
                        py = cy + r
                    } else {
                        s -= straight
                        a = PI.toFloat() / 2f + s / r
                        px = left + cos(a) * r
                        py = cy + sin(a) * r
                    }
                }
            }
        }
        xs[i] = px
        ys[i] = py
        nx[i] = cos(a)
        ny[i] = sin(a)
    }
    return FuseLine(xs, ys, nx, ny, length)
}

/** A point on the fuse at u (0…1), written into out: x, y, normal x, normal y. */
internal fun fuseAt(f: FuseLine, u: Float, out: FloatArray) {
    val k = fuseClamp01(u) * (FUSE_SAMPLES - 1)
    val i = min(FUSE_SAMPLES - 2, floor(k).toInt())
    val fr = k - i
    out[0] = f.xs[i] + (f.xs[i + 1] - f.xs[i]) * fr
    out[1] = f.ys[i] + (f.ys[i + 1] - f.ys[i]) * fr
    out[2] = f.nx[i] + (f.nx[i + 1] - f.nx[i]) * fr
    out[3] = f.ny[i] + (f.ny[i + 1] - f.ny[i]) * fr
}

/** The fuse's u nearest to (x, y) in out[0], and the distance to it in out[1]. */
internal fun fuseNearest(f: FuseLine, x: Float, y: Float, out: FloatArray) {
    var best = 0
    var bestD = 1e9f
    var i = 0
    while (i < FUSE_SAMPLES) {
        val dx = f.xs[i] - x
        val dy = f.ys[i] - y
        val d = dx * dx + dy * dy
        if (d < bestD) {
            bestD = d
            best = i
        }
        i += 2
    }
    out[0] = best.toFloat() / (FUSE_SAMPLES - 1)
    out[1] = sqrt(bestD)
}

/** A value animated as a pure function of the screen clock (a timing curve from where it was). */
internal class FuseTrack(initial: Float) {
    private var from = initial
    private var to = initial
    private var start = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = ::fuseEaseInOut2

    fun set(v: Float) {
        from = v; to = v; duration = 0f
    }

    fun timing(now: Float, target: Float, seconds: Float, ease: (Float) -> Float = ::fuseEaseInOut2) {
        from = value(now); to = target; start = now; duration = seconds; easing = ease
    }

    fun value(now: Float): Float {
        if (duration <= 0f) return to
        val u = (now - start) / duration
        if (u <= 0f) return from
        if (u >= 1f) return to
        return from + (to - from) * easing(u)
    }

    fun done(now: Float) = duration <= 0f || now >= start + duration
}

private val EaseOutBezier = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOutBezier = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
internal val FuseEaseOutCurve: (Float) -> Float = { EaseOutBezier.transform(it) }
internal val FuseEaseInOutCurve: (Float) -> Float = { EaseInOutBezier.transform(it) }
