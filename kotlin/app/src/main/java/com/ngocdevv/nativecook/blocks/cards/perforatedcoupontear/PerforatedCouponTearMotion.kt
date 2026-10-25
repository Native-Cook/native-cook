package com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The coupon's clock and geometry, ported from couponMotion.ts. Every value on screen is a pure
 * function of the loop time (or of the finger's state), so the frame at t = LOOP is the frame at 0.
 * Lengths are dp (= pt), angles radians, times seconds. Coordinates are card-local: (0, 0) is the
 * coupon's top-left corner at rest.
 */
internal object CouponMotion {
    const val LOOP = 9.0

    // Script beats (s).
    private const val GRAB_AT = 1.4
    private const val TEAR_END = 3.6
    private const val SWING_END = 4.6
    const val FLY_START = SWING_END
    private const val FLY_END = 5.4
    const val STAMP_AT = 5.0
    private const val STAMP_LEN = 0.38
    private const val SINK_START = 6.8
    private const val SINK_END = 7.4
    private const val EXIT_END = 7.6
    /** The fresh coupon starts sliding in while the redeemed one is still leaving. */
    private const val ENTER_START = 7.1
    private const val ENTER_END = 8.2

    /** Card height; the stub is STUB_W wide on the right, joined by the perforation. */
    const val CARD_H = 200f
    const val STUB_W = 116f
    const val CORNER = 20f
    const val NOTCH_R = 11f
    const val HOLE_R = 2.7f
    const val HOLE_GAP = 10f
    /** The stub leans at most this far (rad) about the tear front while it rips (about 13°). */
    private const val TEAR_LEAN = 0.22
    /** Where the stub hangs from the finger once it is free (rad). */
    const val HANG = -0.09
    /** Swing: decay rate (1/s) and frequency (Hz) of the free stub's pendulum. */
    private const val SWING_DECAY = 3.2
    private const val SWING_HZ = 1.45
    /** The stub sits in the wallet at this angle and scale. */
    const val POCKET_TILT = 0.12
    const val POCKET_SCALE = 0.6
    /** A filed stub sinks this far below its peek before the next coupon arrives. */
    private const val SINK_DEPTH = 96.0

    class Geom(
        val cw: Double,
        val px: Double,
        /** The stub's grab point, in card coordinates. */
        val gx: Double,
        val gy: Double,
        /** Where the grab point lands in the wallet, in card coordinates. */
        val pocketX: Double,
        val pocketY: Double,
        /** Width of the screen: how far the coupon slides out and in. */
        val travel: Double,
        /** Top of the wallet's front panel, in card coordinates. */
        val front: Double,
    )

    class Pose {
        /** Tear progress 0…1 and where the tear front is (dp from the top). */
        var tear = 0.0
        var front = NOTCH_R.toDouble()
        /** World position of the grab point (card coordinates), stub angle and scale. */
        var sx = 0.0
        var sy = 0.0
        var phi = 0.0
        var scale = 1.0
        /** 0 lying flat … 1 lifted (shadow). */
        var lift = 0.0
        /** Ink stamp on the body, 0…1 (with overshoot inside). */
        var stamp = 0.0
        /** Horizontal offset of the whole coupon (the swap). */
        var slide = 0.0
        /** Whether the stub is drawn in the wallet layer (behind the pocket's front). */
        var filed = false
        /** The fresh coupon arriving while the redeemed one leaves: its offset, and whether it shows. */
        var fresh = 0.0
        var freshOn = false
    }

    class Fly(val x: Double, val y: Double, val phi: Double, val on: Boolean)

    fun clamp01(x: Double) = min(1.0, kotlin.math.max(0.0, x))
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)

    /** Ease-out that passes the target by about 4 % and settles. */
    private fun easeOutBack(x: Double): Double {
        val c = 0.9
        return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2)
    }

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** Number of perforation holes down the card. */
    val holeCount: Int = floor((CARD_H - 2 * NOTCH_R - 8) / HOLE_GAP).toInt() + 1

    /** The tear front travels hole by hole: each gap rips quickly, then the next hole holds a moment. */
    fun frontAt(tear: Double): Double {
        val n = holeCount
        val x = clamp01(tear) * n
        val k = floor(x)
        val f = x - k
        val q = min(n.toDouble(), k + if (f < 0.3) 0.0 else (f - 0.3) / 0.7) / n
        return NOTCH_R + (CARD_H - 2 * NOTCH_R) * q
    }

    /** The stub while still joined: it turns about the tear front by up to TEAR_LEAN. Returns front, sx, sy, phi. */
    fun attachedPose(g: Geom, tear: Double, nudge: Double): DoubleArray {
        val front = frontAt(tear)
        val theta = TEAR_LEAN * tear + nudge
        val dx = g.gx - g.px
        val dy = g.gy - front
        val c = cos(theta)
        val s = sin(theta)
        return doubleArrayOf(front, g.px + dx * c - dy * s, front + dx * s + dy * c, theta)
    }

    /** The free stub's pendulum, tau seconds after it came loose. */
    private fun swingAngle(tau: Double) =
        HANG + (TEAR_LEAN - HANG) * exp(-SWING_DECAY * tau) * cos(2 * PI * SWING_HZ * tau)

    /** Free stub held by the script's finger, which drifts down and back in toward the wallet: sx, sy, phi. */
    private fun swingPose(g: Geom, tau: Double): DoubleArray {
        val end = attachedPose(g, 1.0, 0.0)
        val drift = easeOut(clamp01(tau))
        return doubleArrayOf(end[1] - 46 * drift, end[2] + 34 * drift, swingAngle(tau))
    }

    /** Where the scripted flight starts unless a finger let go of the stub somewhere else. */
    fun defaultFly(g: Geom): Fly {
        val p = swingPose(g, SWING_END - TEAR_END)
        return Fly(p[0], p[1], p[2], false)
    }

    /** Offset of the fresh coupon sliding in from the right (0 once it has settled). */
    private fun arriving(g: Geom, t: Double) = g.travel * (1 - easeOutBack(seg(t, ENTER_START, ENTER_END)))

    /** The whole loop, as a function of t in [0, LOOP). [fly] is where the flight to the wallet starts. */
    fun scriptPose(g: Geom, t: Double, fly: Fly?): Pose {
        val p = Pose()
        if (t < TEAR_END) {
            val grab = seg(t, GRAB_AT, GRAB_AT + 0.25)
            // Anticipation: the finger presses the stub back a hair before it pulls.
            val nudge = -0.035 * sin(PI * seg(t, GRAB_AT, GRAB_AT + 0.32))
            val u = seg(t, GRAB_AT + 0.2, TEAR_END)
            // Slow first holes, then the rip runs away.
            val tear = 0.22 * u + 0.78 * u * u * u
            val a = attachedPose(g, tear, nudge)
            p.tear = tear
            p.front = a[0]
            p.sx = a[1]
            p.sy = a[2]
            p.phi = a[3]
            p.lift = if (t < GRAB_AT) 0.0 else easeOut(grab)
        } else if (t < FLY_START) {
            val s = swingPose(g, t - TEAR_END)
            p.tear = 1.0
            p.front = CARD_H.toDouble()
            p.sx = s[0]
            p.sy = s[1]
            p.phi = s[2]
            p.lift = 1.0
        } else if (t < EXIT_END) {
            val from = if (fly != null && fly.on) fly else defaultFly(g)
            val u = easeInOut(seg(t, FLY_START, FLY_END))
            // A quadratic arc that swings out to the right and drops into the wallet's slot.
            val cx = (from.x + g.pocketX) / 2 + 28
            val cy = from.y + (g.pocketY - from.y) * 0.3 - 24
            val a = (1 - u) * (1 - u)
            val b = 2 * (1 - u) * u
            val c = u * u
            p.tear = 1.0
            p.front = CARD_H.toDouble()
            p.sx = a * from.x + b * cx + c * g.pocketX
            p.sy = a * from.y + b * cy + c * g.pocketY + SINK_DEPTH * easeInOut(seg(t, SINK_START, SINK_END))
            p.phi = from.phi + (POCKET_TILT - from.phi) * u
            p.scale = 1 + (POCKET_SCALE - 1) * u
            p.lift = 1 - seg(t, FLY_END - 0.2, FLY_END)
            // The stub moves behind the pocket's front the moment its lower edge reaches it.
            val bottom = p.sy + p.scale * (CARD_H - g.gy) * cos(p.phi)
            p.filed = u > 0.3 && bottom > g.front - 1
            val st = seg(t, STAMP_AT, STAMP_AT + STAMP_LEN)
            p.stamp = if (t < STAMP_AT) 0.0 else 1 + 0.35 * (1 - easeOutBack(st))
            // The redeemed coupon leaves to the left.
            p.slide = -g.travel * easeInOut(seg(t, SINK_START, EXIT_END))
            p.fresh = arriving(g, t)
            p.freshOn = t >= ENTER_START
        } else {
            // The fresh coupon (stub joined again) is now the live one; it settles with a small overshoot.
            p.slide = arriving(g, t)
        }
        if (p.tear == 0.0 && p.sx == 0.0) {
            val a = attachedPose(g, 0.0, 0.0)
            p.sx = a[1]
            p.sy = a[2]
        }
        return p
    }

    /** The finger's version: joined with tear progress, or free and held at (x, y, phi). */
    fun userPose(g: Geom, tear: Double, free: Boolean, x: Double, y: Double, phi: Double): Pose {
        val p = Pose()
        if (free) {
            p.tear = 1.0
            p.front = CARD_H.toDouble()
            p.sx = x
            p.sy = y
            p.phi = phi
            p.lift = 1.0
            return p
        }
        val t = kotlin.math.max(0.0, tear)
        val a = attachedPose(g, t, 0.0)
        p.tear = t
        p.front = a[0]
        p.sx = a[1]
        p.sy = a[2]
        p.phi = a[3]
        p.lift = clamp01(tear * 6)
        return p
    }

    /**
     * Reanimated's withSpring(0, { duration: 420, dampingRatio: 0.78, velocity }) in closed form:
     * value [t] seconds after release from [x0] with velocity [v0] (units/s).
     */
    fun springBack(x0: Double, v0: Double, t: Double): Double {
        val z = 0.78
        val w = 12.2
        val a = z * w
        val wd = w * sqrt(1 - z * z)
        return exp(-a * t) * (x0 * cos(wd * t) + (v0 + a * x0) / wd * sin(wd * t))
    }

    fun springSettled(x0: Double, v0: Double, t: Double) = t > 0.2 && abs(springBack(x0, v0, t)) < 0.0005 && abs(springBack(x0, v0, t + 0.05)) < 0.0005
}
