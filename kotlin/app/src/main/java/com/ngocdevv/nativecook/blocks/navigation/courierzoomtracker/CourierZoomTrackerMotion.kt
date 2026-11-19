package com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// One loop drives the whole film. Every film value is a pure function of the loop time t (0 <= t < LOOP), so t = LOOP
// gives the same frame as t = 0.
//
//  0.0 –  0.9  poster: the whole city, the blue route from the hub to Home, "Arriving in 12 min"
//  0.9 – 12.6  the drive. The camera frames the van and the door together, so it zooms in by itself as the van gets
//              closer: whole city → district → street; the map turns 37° until north is up and tilts into 3D.
//  5.25 – 7.0  a finger drags the map aside to look round (the camera stops following; the locate button empties)
//  7.5  – 8.3  a tap on the locate button: the camera glides back onto the van
//  ~8.8 – 12.6 the last 100 m: right in to the door, the van parks; 12.45 "Arrived", the door pulses
// 13.1  – 14.45 Call is tapped: a "Calling Marco…" pill shows, then the call ends
// 14.45 – 17.0 Message is tapped: the quick reply "Coming down now" is sent (the toast from 14.6)
// 15.45 – 18.35 the camera pulls back out to the whole city; the van leaves the door and the next round starts at the
//              hub. 18.35 – 19.0 poster. Last frame = first frame.

/** The screen camera: centre (m), zoom (dp per m), rotation, focus point, ground squash and tilt. */
internal class CourierCam(
    val cx: Double, val cy: Double, val s: Double, val cosB: Double, val sinB: Double,
    val fx: Double, val fy: Double, val squash: Double, val tilt: Double,
) {
    fun sx(wx: Double, wy: Double): Double {
        val dx = (wx - cx) * s
        val dy = (wy - cy) * s
        return fx + dx * cosB - dy * sinB
    }

    fun sy(wx: Double, wy: Double): Double {
        val dx = (wx - cx) * s
        val dy = (wy - cy) * s
        return fy + (dx * sinB + dy * cosB) * squash
    }

    fun worldX(sx: Double, sy: Double): Double {
        val x = sx - fx
        val y = (sy - fy) / squash
        return cx + (x * cosB + y * sinB) / s
    }

    fun worldY(sx: Double, sy: Double): Double {
        val x = sx - fx
        val y = (sy - fy) / squash
        return cy + (-x * sinB + y * cosB) / s
    }

    val heightScale: Double get() = s * tilt * 0.9
}

/** Width, height, focus x, focus y, framing width, framing height, panel top (dp). */
internal data class CourierLay(
    val w: Double, val h: Double, val fx: Double, val fy: Double, val aw: Double, val ah: Double, val panel: Double,
)

/** The camera along the film or under a finger: centre, log2 zoom, bearing. */
internal data class CourierPose(val x: Double, val y: Double, val z: Double, val b: Double) {
    companion object {
        fun lerp(a: CourierPose, b: CourierPose, k: Double) =
            CourierPose(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k, a.b + (b.b - a.b) * k)
    }
}

internal class CourierFinger(val kind: Int, val vis: Double, val press: Double, val x: Double, val y: Double)

internal object CourierMotion {
    private val City = CourierCity

    const val LOOP = 19.0

    const val driveA = 0.9
    const val driveB = 12.6
    const val grabIn = 5.25
    const val grab = 5.45
    const val dragA = 5.5
    const val dragB = 6.7
    const val release = 6.8
    const val recenter = 7.5
    const val glideB = 8.3
    const val arriveA = 12.45
    const val arriveB = 12.85
    const val call = 13.1
    const val callOpen = 13.2
    const val callClose = 14.2
    const val msg = 14.45
    const val msgSend = 14.6
    const val backA = 15.45
    const val backB = 18.35
    const val vanOutA = 15.7
    const val vanOutB = 16.2
    const val vanInA = 16.9
    const val vanInB = 17.6

    const val etaMin = 12.0
    const val sMax = 8.5
    const val sMin = 0.07
    private const val sFloor = 0.12
    private const val soft = 40.0
    private const val rampIn = 1.6
    private const val rampOut = 2.2
    val bearing0 = 36.8 * PI / 180
    const val dragX = -120.0
    const val dragY = 36.0
    const val idle = 2.0
    const val returnDur = 1.5
    const val extrudeX = 0.2

    // helpers

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeInOutSine(x: Double) = 0.5 - 0.5 * cos(PI * x)
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun smooth01(x: Double): Double {
        val k = clamp01(x)
        return k * k * (3 - 2 * k)
    }

    fun bump(t: Double, a: Double, b: Double): Double {
        val k = sin(PI * seg(t, a, b))
        return k * k
    }

    /** Cubic bezier (0.65, 0, 0.35, 1), the RN `EASE_IN_OUT`. */
    fun easeBezier(x: Double): Double {
        fun bx(u: Double) = 3 * (1 - u) * (1 - u) * u * 0.65 + 3 * (1 - u) * u * u * 0.35 + u * u * u
        fun by(u: Double) = 3 * (1 - u) * u * u + u * u * u
        val xx = clamp01(x)
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val m = (lo + hi) / 2
            if (bx(m) < xx) lo = m else hi = m
        }
        return by((lo + hi) / 2)
    }

    fun remainingAt(u: Double, len: Double) = (len + soft) * (soft / (len + soft)).pow(u) - soft
    fun logParam(dLeft: Double, len: Double) = ln((len + soft) / (max(dLeft, 0.0) + soft)) / ln((len + soft) / soft)

    /** A point on the route d metres from the hub: x, y, heading (into [out]). */
    fun routeAt(d: Double, out: DoubleArray) {
        val rx = City.routeX
        val ry = City.routeY
        val n = rx.size - 1
        val f = min(max(d / City.ROUTE_STEP, 0.0), n.toDouble())
        val i = min(floor(f).toInt(), n - 1)
        val k = f - i
        val a = max(i - 3, 0)
        val b = min(i + 4, n)
        val hx = rx[b] - rx[a]
        val hy = ry[b] - ry[a]
        val hl = if (hypot(hx, hy) == 0.0) 1.0 else hypot(hx, hy)
        out[0] = rx[i] + (rx[i + 1] - rx[i]) * k
        out[1] = ry[i] + (ry[i + 1] - ry[i]) * k
        out[2] = hx / hl
        out[3] = hy / hl
    }

    fun bearingOf(u: Double) = bearing0 * (1 - smooth01((u - 0.5) / 0.36))

    fun tiltOf(s: Double): Double {
        val k = (log2(s) - log2(0.55)) / (log2(3.6) - log2(0.55))
        return 0.8 * smooth01(k)
    }

    /** The camera that frames the van (d metres along the route) and the door together. */
    fun framing(d: Double, lay: CourierLay): CourierPose {
        val len = City.routeLen
        val p = DoubleArray(4)
        routeAt(d, p)
        val n = City.routeX.size - 1
        val hx = City.routeX[n]
        val hy = City.routeY[n]
        val left = len - d
        val b = bearingOf(logParam(left, len))
        val vx = hx - p[0]
        val vy = hy - p[1]
        val c = cos(-b)
        val s = sin(-b)
        val sx = vx * c - vy * s
        val sy = vx * s + vy * c
        val a = lay.ah * 0.8
        val w = lay.aw * 0.66
        val vl = hypot(sx, sy)
        val grow = if (vl > 1) sqrt(max(left, vl) / vl) else 1.0
        val q = ((sy / a) * (sy / a) + (sx / w) * (sx / w)) * grow * grow
        val inv = 1 / sMax
        val zc = 1 / (q * q + inv * inv * inv * inv).pow(0.25)
        val z = (zc * zc * zc * zc + sFloor * sFloor * sFloor * sFloor).pow(0.25)
        val near = 1 - clamp01(left / 150)
        return CourierPose((p[0] + hx) / 2, (p[1] + hy) / 2 - 5 * near, log2(z), b)
    }

    // the film

    fun driveU(t: Double): Double {
        val tt = driveB - driveA
        val tau = min(max(t - driveA, 0.0), tt)
        val v = 1 / (tt - rampIn / 2 - rampOut / 2)
        if (tau < rampIn) {
            val x = tau / rampIn
            return v * rampIn * (x * x * x - x * x * x * x / 2)
        }
        if (tau < tt - rampOut) return v * (rampIn / 2 + tau - rampIn)
        val y = (tau - (tt - rampOut)) / rampOut
        return min(1.0, v * (rampIn / 2 + (tt - rampOut - rampIn) + rampOut * (y - (y * y * y - y * y * y * y / 2))))
    }

    fun filmDrive(t: Double) = City.routeLen - remainingAt(driveU(t), City.routeLen)

    /** The film's camera. */
    fun filmCamera(t: Double, lay: CourierLay): CourierPose {
        val len = City.routeLen
        var live: CourierPose
        if (t < backA) {
            live = framing(filmDrive(t), lay)
        } else {
            val a = framing(len, lay)
            val b = framing(0.0, lay)
            val k = easeInOutSine(seg(t, backA, backB))
            val lz = lerp(a.z, b.z, k)
            val ia = 2.0.pow(-a.z)
            val ib = 2.0.pow(-b.z)
            val w = (2.0.pow(-lz) - ia) / (ib - ia)
            live = CourierPose(lerp(a.x, b.x, w), lerp(a.y, b.y, w), lz, lerp(a.b, b.b, k))
        }
        val f = easeOutCubic(seg(t, grab - 0.05, grab + 0.3)) * (1 - easeInOutCubic(seg(t, recenter, glideB)))
        if (f > 0) {
            val fr = framing(filmDrive(grab - 0.05), lay)
            val k = easeInOutCubic(seg(t, dragA, dragB))
            val ox = dragX * k
            val oy = dragY * k
            val z = 2.0.pow(fr.z)
            val cb = cos(fr.b)
            val sb = sin(fr.b)
            val wx = (ox * cb - oy * sb) / z
            val wy = (ox * sb + oy * cb) / z
            live = CourierPose(lerp(live.x, fr.x - wx, f), lerp(live.y, fr.y - wy, f), lerp(live.z, fr.z, f), lerp(live.b, fr.b, f))
        }
        val drift = bump(t, 11.8, 16.4)
        return live.copy(z = live.z + 0.1 * drift, b = live.b - 0.09 * drift)
    }

    fun filmFollow(t: Double) = !(t >= grab && t < recenter)

    fun filmVanD(t: Double) = if (t < 16.5) filmDrive(t) else 0.0
    fun filmVanA(t: Double) = if (t < 16.5) 1 - easeInCubic(seg(t, vanOutA, vanOutB)) else easeOutCubic(seg(t, vanInA, vanInB))

    fun filmTravEnd(t: Double) = if (t < 17.3) filmDrive(t) / City.routeLen else 0.0
    fun filmTravA(t: Double) = if (t < 17.3) 1 - easeInOutCubic(seg(t, 16.5, 17.3)) else 1.0

    fun filmArrived(t: Double) = easeInOutCubic(seg(t, arriveA, arriveB)) * (1 - easeInOutCubic(seg(t, vanInA, vanInB)))

    fun filmToast(t: Double) = easeOutCubic(seg(t, msgSend, msgSend + 0.3)) * (1 - easeInCubic(seg(t, 16.6, 17.0)))

    fun filmCall(t: Double) = easeOutCubic(seg(t, callOpen, callOpen + 0.3)) * (1 - easeInCubic(seg(t, callClose, callClose + 0.25)))

    fun filmDoorPulse(t: Double): Double {
        val k = seg(t, driveB - 0.05, driveB + 1.0)
        return if (k > 0 && k < 1) k else 0.0
    }

    /** Ghost finger: kind (0 none, 1 drag, 2 locate, 3 Call, 4 Message), visibility, press, x, y. */
    fun filmFinger(t: Double, lay: CourierLay, buttons: DoubleArray): CourierFinger {
        if (t >= grabIn && t < release + 0.3) {
            val k = easeInOutCubic(seg(t, dragA, dragB))
            val vis = easeOutCubic(seg(t, grabIn, grab)) * (1 - easeOutCubic(seg(t, release, release + 0.3)))
            val press = seg(t, grabIn + 0.08, grab) * (1 - seg(t, dragB + 0.02, release))
            return CourierFinger(1, vis, press, lay.w * 0.7 + dragX * k, lay.fy - 110 + dragY * k)
        }
        val taps = doubleArrayOf(recenter, call, msg)
        for (i in 0 until 3) {
            val tap = taps[i]
            if (t >= tap - 0.32 && t < tap + 0.42) {
                val vis = easeOutCubic(seg(t, tap - 0.32, tap - 0.12)) * (1 - easeOutCubic(seg(t, tap + 0.14, tap + 0.42)))
                return CourierFinger(2 + i, vis, bump(t, tap - 0.12, tap + 0.14), buttons[i * 2], buttons[i * 2 + 1] + 3)
            }
        }
        return CourierFinger(0, 0.0, 0.0, 0.0, 0.0)
    }

    // copy

    fun etaText(dLeft: Double): String {
        val m = max(1, ceil(etaMin * dLeft / City.routeLen - 0.02).toInt())
        return "$m min"
    }

    fun distanceText(dLeft: Double): String {
        val d = max(dLeft, 0.0)
        if (d < 15) return "Pulling up outside"
        val dist = if (d >= 1000) {
            val tenths = floor(d / 100 + 0.5) / 10
            "${String.format(java.util.Locale.US, "%.1f", tenths)} km"
        } else {
            "${max(10, floor(d / 10 + 0.5).toInt() * 10)} m"
        }
        return "$dist away · ${if (d > 400) "On the way" else "Almost there"}"
    }

    // projection

    fun screenCam(v: CourierPose, lay: CourierLay): CourierCam {
        val s = 2.0.pow(v.z)
        val tilt = tiltOf(s)
        return CourierCam(v.x, v.y, s, cos(-v.b), sin(-v.b), lay.fx, lay.fy, 1 - 0.28 * tilt, tilt)
    }

    /** A fling's decay (RN `withDecay`, deceleration 0.994 per ms): how far a unit velocity has carried after e s. */
    fun decayTravel(e: Double): Double {
        val k = -ln(0.994) * 1000
        return (1 - exp(-k * max(0.0, e))) / k
    }
}
