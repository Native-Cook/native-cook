package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Stamp Diary Calendar motion, ported value for value from motion.ts (and equal to the Swift port
 * to 1e-14). Every value on screen is a pure function of the story time tau (seconds); autoPlay
 * runs tau = (clock - start) mod LOOP, so tau = LOOP equals tau = 0 (the pin).
 *
 *   0.00-0.90  rest = the pin
 *   0.90-2.00  PEEL: the May 12 stamp rises off its cell while its bottom-right corner curls back
 *   2.00-3.00  FLIGHT: it lets go, the curl springs flat, it flutters up an S-curve into the collage
 *   3.00       IMPACT: it slaps down on its copy in the collage
 *   3.02-4.10  INK: a postmark spreads from the ring, bleeds into the paper, dries
 *   ...-5.50   hold
 *   5.50-6.62  RETURN: the ink lifts, the stamp flies home on a lower arc
 *   6.62-7.12  RESTICK: it lays down corner last and presses flat
 *   7.12-8.60  rest
 */
internal object StampDiaryCalendarMotion {
    const val LOOP = 8.6

    object T {
        const val PEEL_START = 0.9
        const val DETACH = 2.0
        const val LAND = 3.0
        const val INK = 3.02
        const val HOLD_END = 5.5
        const val RETURN_FLIGHT = 5.58
        const val RESTICK = 6.62
        const val REST = 7.12
    }

    object Peel {
        const val LIFT = 1.7
        const val LIFT_SHARE = 0.5
        const val RISE = 3.0
        const val AMOUNT = 0.8
        const val REACH = 1.2
        const val WOBBLE = 0.12
        const val RADIUS_PER_DISTANCE = 0.22
        const val RADIUS_MAX = 0.16
        const val SHADOW = 0.55
        const val RELEASE = 0.28
    }

    object Flight {
        val ease = doubleArrayOf(0.4, 0.0, 0.55, 0.92)
        val sizeEase = doubleArrayOf(0.3, 0.0, 0.3, 1.0)
        const val BUMP = 0.16
        const val ROT_Z = 10.0
        const val ROT_X = 24.0
        const val ROT_Y = 20.0
        const val BEND = 0.8
        const val BEND_DEPTH = 0.3
        const val BEND_RADIUS = 0.55
        const val SHEEN = 0.32
        const val OUT_X = 70.0
        const val OUT_Y = -150.0
        const val IN_X = 110.0
        const val IN_Y = 40.0
        const val PERSPECTIVE = 520.0
    }

    object Impact {
        const val ATTACK = 0.045
        const val DECAY = 0.09
        const val PERIOD = 0.32
        const val SQUASH_X = 0.05
        const val SQUASH_Y = 0.07
        const val NUDGE = 0.012
    }

    object Ink {
        const val SPREAD = 0.34
        const val BLEED = 1.1
        const val THIN = 0.5
        const val BLEED_BY = 0.13
        const val DRY_AFTER = 0.25
        const val DRY = 1.4
        const val LIFT = 0.34
        const val LIFT_BY = 0.62
    }

    object Return {
        const val PRE_LIFT = 1.04
        const val LIFT = 1.3
        const val FLUTTER = 0.55
        const val OUT_X = -80.0
        const val OUT_Y = 70.0
        const val IN_X = -70.0
        const val IN_Y = -130.0
        const val CORNER = 0.3
        const val CORNER_FROM = 0.7
        const val UNDERSHOOT = 0.025
    }

    object PileDim {
        const val TO = 0.5
        const val IN_AT = T.LAND - 0.45
        const val IN_FOR = 0.6
        const val OUT_AT = T.HOLD_END + 0.05
        const val OUT_FOR = 0.55
    }

    object Reveal {
        const val IN_AT = T.DETACH - 0.05
        const val IN_FOR = 0.25
        const val OUT_AT = 6.55
        const val OUT_FOR = 0.25
    }

    object Touch {
        const val COMMIT_SHARE = 0.42
        const val COMMIT_VELOCITY = -500.0
        const val SLOP = 6.0
        const val LIFT_IN = 0.22
        const val SNAP_TAU = 0.07
        const val SNAP_FOR = 0.5
        const val RESUME_AFTER = 2.0
        const val RETURN_RATE = 1.3
        const val FADE = 0.2
    }

    const val STAMP_PAD = 0.55

    // ---- small helpers

    fun clamp01(v: Double): Double = if (v < 0) 0.0 else if (v > 1) 1.0 else v

    fun mix(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    fun ease01(v: Double): Double {
        val x = clamp01(v)
        return x * x * (3 - 2 * x)
    }

    fun easeOut3(v: Double): Double {
        val x = 1 - clamp01(v)
        return 1 - x * x * x
    }

    /** CSS-style cubic-bezier(x1, y1, x2, y2) evaluated at x (Newton, then bisection). */
    fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        val t0 = clamp01(x)
        if (t0 == 0.0 || t0 == 1.0) return t0
        val cx = 3 * x1
        val bx = 3 * (x2 - x1) - cx
        val ax = 1 - cx - bx
        val cy = 3 * y1
        val by = 3 * (y2 - y1) - cy
        val ay = 1 - cy - by
        var t = t0
        for (i in 0 until 6) {
            val fx = ((ax * t + bx) * t + cx) * t - t0
            val d = (3 * ax * t + 2 * bx) * t + cx
            if (abs(fx) < 1e-5 || abs(d) < 1e-6) break
            t -= fx / d
        }
        if (t < 0 || t > 1) {
            var lo = 0.0
            var hi = 1.0
            t = t0
            for (i in 0 until 24) {
                val fx = ((ax * t + bx) * t + cx) * t
                if (fx < t0) lo = t else hi = t
                t = (lo + hi) / 2
            }
        }
        return ((ay * t + by) * t + cy) * t
    }

    private fun cubicPoint(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val u = 1 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    /** 0 at both ends, 1 in the middle, smooth (sin squared). */
    private fun envelope(u: Double): Double {
        val s = sin(PI * clamp01(u))
        return s * s
    }

    /** Impact wobble k(t), t = seconds since contact: a fast attack then a damped cosine. */
    fun impact(t: Double): Double {
        if (t < 0) return 0.0
        if (t < Impact.ATTACK) return sin((PI / 2) * (t / Impact.ATTACK))
        val s = t - Impact.ATTACK
        if (s > 0.9) return 0.0
        return exp(-s / Impact.DECAY) * cos((2 * PI * s) / Impact.PERIOD)
    }

    /** Critically damped return 1 -> 0 (snap back after a cancelled peel). */
    fun settle(t: Double): Double {
        if (t <= 0) return 1.0
        val x = t / Touch.SNAP_TAU
        return (1 + x) * exp(-x)
    }

    // ---- calendar (Gregorian, Sunday first)

    data class Month(val year: Int, val month: Int)

    val MAY_2026 = Month(2026, 5)

    fun isMay2026(m: Month): Boolean = m.year == 2026 && m.month == 5

    fun addMonths(m: Month, delta: Int): Month {
        val index = m.year * 12 + (m.month - 1) + delta
        val year = floor(index / 12.0).toInt()
        return Month(year, ((index % 12) + 12) % 12 + 1)
    }

    fun daysIn(m: Month): Int {
        if (m.month == 2) {
            val leap = (m.year % 4 == 0 && m.year % 100 != 0) || m.year % 400 == 0
            return if (leap) 29 else 28
        }
        return if (m.month == 4 || m.month == 6 || m.month == 9 || m.month == 11) 30 else 31
    }

    /** Weekday of the 1st, 0 = Sunday (Sakamoto). */
    fun firstWeekday(m: Month): Int {
        val table = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
        val y = if (m.month < 3) m.year - 1 else m.year
        val a = y + floor(y / 4.0).toInt() - floor(y / 100.0).toInt() + floor(y / 400.0).toInt()
        return (a + table[m.month - 1] + 1) % 7
    }

    data class Cell(val column: Int, val row: Int)

    fun cellOf(day: Int, m: Month): Cell {
        val index = firstWeekday(m) + day - 1
        return Cell(index % 7, index / 7)
    }

    data class Point(val x: Double, val y: Double)

    fun cellCenter(day: Int, m: Month): Point {
        val c = cellOf(day, m)
        return Point(
            (c.column + 0.5) * StampDiaryCalendarTheme.COLUMN_WIDTH,
            StampDiaryCalendarTheme.GRID_TOP + (c.row + 0.5) * StampDiaryCalendarTheme.ROW_HEIGHT,
        )
    }

    fun dayAt(x: Double, y: Double, m: Month): Int {
        val g = StampDiaryCalendarTheme
        if (x < 0 || x >= 402 || y < g.GRID_TOP || y >= g.GRID_TOP + g.ROW_HEIGHT * g.ROWS) return 0
        val column = min(6, floor(x / g.COLUMN_WIDTH).toInt())
        val row = min(g.ROWS - 1, floor((y - g.GRID_TOP) / g.ROW_HEIGHT).toInt())
        val day = row * 7 + column - firstWeekday(m) + 1
        return if (day >= 1 && day <= daysIn(m)) day else 0
    }

    fun memoryIndex(day: Int, m: Month): Int {
        if (!isMay2026(m)) return -1
        return StampDiaryCalendarData.memories.indexOfFirst { it.day == day }
    }

    /** Where a memory lands: on its copy in the collage when that copy is sharp, otherwise the free spot. */
    fun landingQuad(memory: Int): StampDiaryCalendarQuad {
        val twin = StampDiaryCalendarData.memories[memory].twin
        if (twin >= 0 && StampDiaryCalendarData.pile[twin].blurBottom == 0.0) return StampDiaryCalendarData.pile[twin].quad
        return StampDiaryCalendarData.freeSpot
    }

    fun holesOfQuad(q: StampDiaryCalendarQuad): Pair<Int, Int> =
        Pair(10, max(4, Math.round(10 * q.h / q.w).toInt()))

    fun holesOf(memory: Int): Pair<Int, Int> = holesOfQuad(landingQuad(memory))

    // ---- curl

    /**
     * The paper rolls around a cylinder of radius r whose axis is the line dot(p, n) = a in the
     * stamp's own centred coordinates; the side where dot(p, n) > a is lifted and folded back.
     */
    data class Curl(val nx: Double, val ny: Double, val a: Double, val r: Double)

    val FLAT = Curl(1.0, 0.0, 1e4, 0.0)

    /** A corner c pulled to the finger f: the fold is the bisector, so the corner lands on the finger. */
    fun curlFromFinger(cx: Double, cy: Double, fx: Double, fy: Double, w: Double, h: Double): Curl {
        val dx = cx - fx
        val dy = cy - fy
        val d = hypot(dx, dy)
        if (d < 0.01) return FLAT
        val nx = dx / d
        val ny = dy / d
        val r = min(Peel.RADIUS_MAX * max(w, h), Peel.RADIUS_PER_DISTANCE * d)
        val sc = (d + PI * r) / 2
        return Curl(nx, ny, cx * nx + cy * ny - sc, r)
    }

    /** The autoplay finger: from the bottom-right corner towards the top left, with a slight drift. */
    fun peelCurl(amount: Double, w: Double, h: Double): Curl {
        val base = atan2(-h, -w) + Peel.WOBBLE * sin(PI * amount)
        val travel = amount * Peel.REACH * hypot(w, h)
        val cx = w / 2
        val cy = h / 2
        return curlFromFinger(cx, cy, cx + cos(base) * travel, cy + sin(base) * travel, w, h)
    }

    /** Paper bending in the air: a wide roll along an edge whose direction turns during the flight. */
    fun bendCurl(angle: Double, bend: Double, w: Double, h: Double): Curl {
        if (bend <= 0.001) return FLAT
        val nx = cos(angle)
        val ny = sin(angle)
        val extent = abs(nx) * w * 0.5 + abs(ny) * h * 0.5
        return Curl(nx, ny, extent - bend * Flight.BEND_DEPTH * min(w, h), Flight.BEND_RADIUS * max(w, h))
    }

    /** Blend two curls (direction by angle, then offset and radius). A flat curl blends by its offset. */
    fun blendCurl(from: Curl, to: Curl, t: Double, w: Double, h: Double): Curl {
        if (t <= 0) return from
        if (t >= 1) return to
        val fromFlat = from.r <= 0
        val toFlat = to.r <= 0
        if (fromFlat && toFlat) return FLAT
        val ref = if (fromFlat) to else from
        val out = if (toFlat) from else to
        val refExtent = abs(ref.nx) * w * 0.5 + abs(ref.ny) * h * 0.5
        val outExtent = abs(out.nx) * w * 0.5 + abs(out.ny) * h * 0.5
        val f = if (fromFlat) Curl(ref.nx, ref.ny, refExtent + 1, ref.r) else from
        val g = if (toFlat) Curl(out.nx, out.ny, outExtent + 1, out.r) else to
        var a0 = atan2(f.ny, f.nx)
        val a1 = atan2(g.ny, g.nx)
        var da = a1 - a0
        while (da > PI) da -= 2 * PI
        while (da < -PI) da += 2 * PI
        a0 += da * t
        return Curl(cos(a0), sin(a0), mix(f.a, g.a, t), mix(f.r, g.r, t))
    }

    // ---- pose

    data class Pose(
        val active: Double = 0.0,
        val memory: Int = 0,
        val cx: Double = 0.0,
        val cy: Double = 0.0,
        val w: Double = 0.0,
        val h: Double = 0.0,
        val skew: Double = 0.0,
        val lift: Double = 1.0,
        val sx: Double = 1.0,
        val sy: Double = 1.0,
        val rotX: Double = 0.0,
        val rotY: Double = 0.0,
        val rotZ: Double = 0.0,
        val curl: Curl = FLAT,
        val peelShadow: Double = 0.0,
        val sheenPos: Double = 0.0,
        val sheenAmt: Double = 0.0,
        val height: Double = 0.0,
        val inkSpread: Double = 0.0,
        val inkThreshold: Double = 1.0,
        val inkWet: Double = 0.0,
        val inkOn: Double = 0.0,
        val opacity: Double = 1.0,
        val pileDim: Double = 1.0,
        val pileNudge: Double = 0.0,
        val reveal: Double = 0.0,
    )

    fun restPose(memory: Int): Pose {
        val m = StampDiaryCalendarData.memories[memory]
        val c = cellCenter(m.day, MAY_2026)
        return Pose(memory = memory, cx = c.x, cy = c.y, w = m.w, h = m.h)
    }

    private fun quadCenter(q: StampDiaryCalendarQuad) = Point(q.x + q.w / 2, q.y + q.h / 2 + q.dy / 2)

    /** The peel's curl at the moment it lets go (autoPlay), the default start of the release blend. */
    fun detachCurl(memory: Int): Curl {
        val m = StampDiaryCalendarData.memories[memory]
        return peelCurl(Peel.AMOUNT, m.w, m.h)
    }

    private fun withInk(pose: Pose, tau: Double): Pose {
        if (tau < T.INK) return pose
        val t = tau - T.INK
        val threshold = Ink.THIN - Ink.BLEED_BY * easeOut3(t / Ink.BLEED) + Ink.LIFT_BY * ease01((tau - T.HOLD_END) / Ink.LIFT)
        return pose.copy(
            inkOn = if (threshold > 1.05) 0.0 else 1.0,
            inkSpread = easeOut3(t / Ink.SPREAD),
            inkThreshold = threshold,
            inkWet = 1 - ease01((t - Ink.DRY_AFTER) / Ink.DRY),
        )
    }

    /** The story pose at tau. `release` is the curl the stamp had when it let go. */
    fun storyPose(memory: Int, tau: Double, release: Curl): Pose {
        val rest = restPose(memory)
        if (tau < T.PEEL_START || tau >= T.REST) return rest
        val m = StampDiaryCalendarData.memories[memory]
        val cell = cellCenter(m.day, MAY_2026)
        val q = landingQuad(memory)
        val land = quadCenter(q)
        val landSkew = q.dy / q.w
        val reveal = ease01((tau - Reveal.IN_AT) / Reveal.IN_FOR) * (1 - ease01((tau - Reveal.OUT_AT) / Reveal.OUT_FOR))
        val pileDim = mix(1.0, PileDim.TO, ease01((tau - PileDim.IN_AT) / PileDim.IN_FOR) * (1 - ease01((tau - PileDim.OUT_AT) / PileDim.OUT_FOR)))
        val base = rest.copy(active = 1.0, reveal = reveal, pileDim = pileDim)

        if (tau < T.DETACH) {
            val p = (tau - T.PEEL_START) / (T.DETACH - T.PEEL_START)
            val rise = easeOut3(p / Peel.LIFT_SHARE)
            return base.copy(
                lift = mix(1.0, Peel.LIFT, rise),
                cy = cell.y - Peel.RISE * rise,
                curl = peelCurl(Peel.AMOUNT * bezier(0.5, 0.0, 0.6, 1.0, p), m.w, m.h),
                peelShadow = Peel.SHADOW,
                height = 0.25 * rise,
            )
        }

        if (tau < T.LAND) {
            val u = (tau - T.DETACH) / (T.LAND - T.DETACH)
            val e = bezier(Flight.ease[0], Flight.ease[1], Flight.ease[2], Flight.ease[3], u)
            val se = bezier(Flight.sizeEase[0], Flight.sizeEase[1], Flight.sizeEase[2], Flight.sizeEase[3], u)
            val env = envelope(u)
            val x0 = cell.x
            val y0 = cell.y - Peel.RISE
            val w = mix(m.w, q.w, se)
            val h = mix(m.h, q.h, se)
            val bend = bendCurl(0.7 + 4.5 * u, Flight.BEND * env, w, h)
            val releaseShare = ease01((tau - T.DETACH) / Peel.RELEASE)
            return base.copy(
                cx = cubicPoint(x0, x0 + Flight.OUT_X, land.x + Flight.IN_X, land.x, e),
                cy = cubicPoint(y0, y0 + Flight.OUT_Y, land.y + Flight.IN_Y, land.y, e),
                w = w,
                h = h,
                skew = landSkew * se,
                lift = mix(Peel.LIFT, 1.0, se) * (1 + Flight.BUMP * env),
                rotZ = Flight.ROT_Z * env * sin(2 * PI * 1.15 * u),
                rotX = Flight.ROT_X * env * sin(2 * PI * 0.9 * u + 0.5),
                rotY = -Flight.ROT_Y * env * sin(2 * PI * 1.3 * u + 1.3),
                curl = blendCurl(release, bend, releaseShare, w, h),
                peelShadow = Peel.SHADOW * (1 - releaseShare),
                sheenAmt = Flight.SHEEN * env,
                sheenPos = 0.5 + 0.9 * sin(2 * PI * 0.9 * u + 0.5),
                height = max(0.25 * (1 - e), env),
            )
        }

        if (tau < T.HOLD_END) {
            val k = impact(tau - T.LAND)
            return withInk(
                base.copy(
                    cx = land.x,
                    cy = land.y,
                    w = q.w,
                    h = q.h,
                    skew = landSkew,
                    sx = 1 + Impact.SQUASH_X * k,
                    sy = 1 - Impact.SQUASH_Y * k,
                    pileNudge = Impact.NUDGE * k,
                ),
                tau,
            )
        }

        if (tau < T.RESTICK) {
            val pre = ease01((tau - T.HOLD_END) / (T.RETURN_FLIGHT - T.HOLD_END))
            val u = clamp01((tau - T.RETURN_FLIGHT) / (T.RESTICK - T.RETURN_FLIGHT))
            val e = bezier(0.45, 0.0, 0.3, 1.0, u)
            val env = envelope(u) * Return.FLUTTER
            val x3 = cell.x
            val y3 = cell.y - Peel.RISE
            val w = mix(q.w, m.w, e)
            val h = mix(q.h, m.h, e)
            val bend = bendCurl(3.6 - 3.5 * u, Flight.BEND * envelope(u) * Return.FLUTTER, w, h)
            val corner = peelCurl(Return.CORNER, w, h)
            val share = ease01((u - Return.CORNER_FROM) / (1 - Return.CORNER_FROM))
            return withInk(
                base.copy(
                    cx = cubicPoint(land.x, land.x + Return.OUT_X, x3 + Return.IN_X, x3, e),
                    cy = cubicPoint(land.y, land.y + Return.OUT_Y, y3 + Return.IN_Y, y3, e),
                    w = w,
                    h = h,
                    skew = landSkew * (1 - e),
                    lift = mix(mix(1.0, Return.PRE_LIFT, pre), Return.LIFT, e) * (1 + Flight.BUMP * env),
                    rotZ = -Flight.ROT_Z * env * sin(2 * PI * 1.0 * u + 0.4),
                    rotX = Flight.ROT_X * env * sin(2 * PI * 0.8 * u + 2.1),
                    rotY = Flight.ROT_Y * env * sin(2 * PI * 1.1 * u + 0.2),
                    curl = blendCurl(bend, corner, share, w, h),
                    peelShadow = Peel.SHADOW * share,
                    sheenAmt = Flight.SHEEN * env,
                    sheenPos = 0.5 + 0.9 * sin(2 * PI * 0.8 * u + 2.1),
                    height = max(0.3 * pre * (1 - u), max(envelope(u), 0.3 * e)),
                ),
                tau,
            )
        }

        // Restick: the corner lays down last, the stamp sinks to the paper and presses in.
        val r = clamp01((tau - T.RESTICK) / (T.REST - T.RESTICK))
        val e = easeOut3(r)
        return base.copy(
            curl = peelCurl(Return.CORNER * (1 - e), m.w, m.h),
            peelShadow = Peel.SHADOW * (1 - e),
            lift = 1 + (Return.LIFT - 1) * (1 - e) - Return.UNDERSHOOT * sin(PI * e),
            cy = cell.y - Peel.RISE * (1 - e),
            height = 0.3 * (1 - e),
        )
    }

    /** A finger peeling a memory in its cell: lift by time since touch-down, curl from the finger. */
    fun fingerPose(memory: Int, held: Double, cornerX: Double, cornerY: Double, fingerX: Double, fingerY: Double): Pose {
        val rest = restPose(memory)
        val m = StampDiaryCalendarData.memories[memory]
        val rise = easeOut3(held / Touch.LIFT_IN)
        return rest.copy(
            active = 1.0,
            lift = mix(1.0, Peel.LIFT, rise),
            cy = rest.cy - Peel.RISE * rise,
            curl = curlFromFinger(cornerX, cornerY, fingerX, fingerY, m.w, m.h),
            peelShadow = Peel.SHADOW,
            height = 0.25 * rise,
        )
    }

    /** Reduced motion: the stamp fades in on its landing spot, postmarked; v = 0..1 shown. */
    fun quietPose(memory: Int, v: Double): Pose {
        val rest = restPose(memory)
        if (v <= 0) return rest
        val q = landingQuad(memory)
        val land = quadCenter(q)
        return rest.copy(
            active = 1.0,
            cx = land.x,
            cy = land.y,
            w = q.w,
            h = q.h,
            skew = q.dy / q.w,
            opacity = v,
            inkOn = 1.0,
            inkSpread = 1.0,
            inkThreshold = Ink.THIN - Ink.BLEED_BY,
            reveal = v,
            pileDim = mix(1.0, PileDim.TO, v),
        )
    }

    /** Share of the diagonal a finger has peeled (for the commit test). */
    fun peelShare(memory: Int, dx: Double, dy: Double): Double {
        val m = StampDiaryCalendarData.memories[memory]
        return hypot(dx, dy) / hypot(m.w, m.h)
    }
}
