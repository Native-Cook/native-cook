package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class ReededGlassRevealSpring(val damping: Double, val stiffness: Double, val mass: Double)

/** What the screen draws: one depth per flute, the glyph's turn, the button's scale, the front (dp). RN `Look`. */
internal class ReededGlassRevealLook(
    val depths: DoubleArray,
    val glyph: Double,
    val press: Double,
    val front: Double,
    val mean: Double,
    /** the front 60 ms ago (dp): the capsule's highlight follows it */
    val lag: Double,
    /** 0..1 progress of the soft glint that runs down three flutes at rest; -1 = off */
    val run: Double,
    /** -1..1 where the key light sits */
    val light: Double,
    /** -1..1 breathing of the magnification */
    val breath: Double,
    /** 0..1 a long press has opened a window */
    val peek: Double,
)

/** Geometry the timeline needs, computed once per screen width. RN `Geo`. */
internal class ReededGlassRevealGeo(
    val w: Double,
    val ribW: Double,
    val front0: Double,
    val front1: Double,
    val edges: DoubleArray,
    val centres: DoubleArray,
    /** times the front crosses each rib centre on the way out (tA) and back (tB) */
    val tA: DoubleArray,
    val tB: DoubleArray,
    /** where the front parks, dp */
    val xPark: Double,
)

/** Live state under a finger (or after one): integrated every frame. RN `Live`. */
internal class ReededGlassRevealLive(geo: ReededGlassRevealGeo) {
    var fx = geo.front0 // front, dp
    var fv = 0.0 // dp/s
    var mode = ReededGlassRevealMotion.SPRING
    var target = geo.front0
    var sweepFrom = geo.front0
    var sweepTo = geo.front0
    var sweepT = 0.0
    var sweepDur = 0.0
    var depth = DoubleArray(ReededGlassRevealMotion.RIBS)
    var vel = DoubleArray(ReededGlassRevealMotion.RIBS)
    var glyph = 0.0
    var glyphV = 0.0
    var glyphTarget = 0.0
    var touching = false
    var touchX = 0.0
    var revealed = false
    var lastRib = -1
    var lastTick = -1.0 // s
    var fade = 1.0 // Reduce Motion: 1 = deep, 0 = clear
    var press = 1.0
    /** the front clears from the right edge (a drag that began there) */
    var flip = false
    /** the finger has moved past the dead zone */
    var dragged = false
    /** seconds the finger has rested without dragging */
    var holdT = 0.0
    /** 0..1 the peek window's opening; peekRel: s since the finger lifted from an open window (-1: not lifted) */
    var peek = 0.0
    var peekRel = -1.0
    var peekX = 0.0
    /** 0..1 how fast the last swipe was: sets the ribs' damping */
    var speedK = 0.0
    /** the front 60 ms ago (smoothed), dp */
    var fl = geo.front0
    var light = 0.0

    init {
        for (i in 0 until ReededGlassRevealMotion.RIBS) depth[i] = 1.0
    }
}

/** Events a live step reports (for haptics and callbacks). */
internal class ReededGlassRevealEvents(var tick: Boolean = false, var edge: Boolean = false, var revealed: Boolean = false, var masked: Boolean = false)

/**
 * The timeline and the live physics of the RN block (`rgr-motion.ts`). Every autoplay frame is a pure function of the loop time t (0 ..< LOOP): the
 * front's path is a timed curve and each rib's depth is an analytic spring started when the front crosses the rib's centre. Under a finger the same
 * springs are integrated live, and 2 s after the last touch the glass eases back to rest and the loop starts again at t = 0.
 */
internal object ReededGlassRevealMotion {
    const val LOOP = 8.0
    const val RIBS = 22

    // phase starts and lengths, s
    const val T_PRESS1 = 0.7
    const val T_PRESS_IN = 0.06
    const val T_PRESS_OUT = 0.1
    const val T_SWEEP1 = 0.8
    const val T_SWEEP1_DUR = 0.8
    const val T_PARK_START = 1.6
    const val T_PARK_END = 2.6
    const val T_SWEEP_ON = 2.6
    const val T_SWEEP_ON_DUR = 0.8
    const val T_GLYPH1 = 3.46
    const val T_PRESS2 = 4.76
    const val T_SWEEP2 = 4.84
    const val T_SWEEP_DUR = 1.6
    const val T_GLYPH2 = 6.5
    const val T_REST = 7.2
    const val T_RUN = 7.0
    const val T_RUN_DUR = 1.4

    // springs, mass 1: response s / damping ratio for the analytic form, k / c for the live one
    const val SNAP_K = 322.3
    const val GLIDE_K = 157.9
    const val GLIDE_C = 22.1
    const val POP_RESPONSE = 0.45
    const val POP_ZETA = 0.68
    const val POP_K = 195.0
    const val POP_C = 19.0

    // easings (x1, y1, x2, y2)
    private val EASE_OUT = doubleArrayOf(0.2, 0.8, 0.2, 1.0)
    private val EASE_SWEEP = doubleArrayOf(0.35, 0.15, 0.65, 0.85)
    private val EASE_PARK = doubleArrayOf(0.3, 0.1, 0.25, 1.0)
    private val EASE_THROUGH = doubleArrayOf(0.45, 0.0, 0.4, 1.0)

    // gesture constants
    const val PROJECTION = 0.12 // s: release projection tau
    const val RIB_SOFT = 1.2 // the front's soft edge, in rib widths (the lens is 2.4 ribs wide)
    const val DIP = 0.2 // ribs under a resting finger dip 20 %
    const val DIP_RADIUS = 1.2 // in rib widths
    const val RESUME_AFTER = 2.0 // s after the last touch
    const val TICK_GAP = 0.0625 // s between two rib ticks (<= 16 per second)
    const val REDUCED_FADE = 0.3 // s: Reduce Motion optical dissolve
    const val SETTLE = 0.004 // depth / velocity threshold for "at rest"
    const val SWELL = 0.25 // the leading rib swells to 1.25 before it falls
    const val PEEK_AFTER = 0.18 // s: a press held this long within 6 dp opens a window
    const val PEEK_DEPTH = 0.12 // depth inside the window
    const val PEEK_SIGMA = 1.4 // ribs
    const val PEEK_RETURN = 0.03 // s per rib: the window closes outward
    const val DIRECTION_DECIDE = 12.0 // dp: the first 12 dp of a drag decide the direction from a resting end
    const val WAKE_SLOW = 19.0 // rib damping after a slow wipe
    const val WAKE_FAST = 11.0 // ... and after a fast one (speed >= 1800 dp/s)

    const val PRESS_SCALE = 0.94
    const val PRESS_FINGER = 0.96

    // live modes
    const val DRAG = 0
    const val SPRING = 1
    const val SWEEP = 2
    const val HOLD = 3

    /** the gasp frame: the front parked at mid-screen, half way through the hold */
    const val GASP_T = (T_PARK_START + T_PARK_END) / 2

    /** cubic bezier easing y(x) for control points (x1, y1, x2, y2): Newton on x */
    fun bezier(c: DoubleArray, x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val x1 = c[0]
        val y1 = c[1]
        val x2 = c[2]
        val y2 = c[3]
        var s = x
        for (i in 0 until 8) {
            val cx = 3 * x1 * s * (1 - s) * (1 - s) + 3 * x2 * s * s * (1 - s) + s * s * s - x
            val dx = 3 * x1 * (1 - s) * (1 - 3 * s) + 3 * x2 * s * (2 - 3 * s) + 3 * s * s
            if (abs(cx) < 1e-6 || dx == 0.0) break
            s = min(1.0, max(0.0, s - cx / dx))
        }
        return 3 * y1 * s * (1 - s) * (1 - s) + 3 * y2 * s * s * (1 - s) + s * s * s
    }

    fun sweepEase(x: Double) = bezier(EASE_SWEEP, x)
    fun easeOut(x: Double) = bezier(EASE_OUT, x)
    fun parkEase(x: Double) = bezier(EASE_PARK, x)
    fun throughEase(x: Double) = bezier(EASE_THROUGH, x)

    /** 0 -> 1 step response of a spring (response s, damping ratio zeta), in closed form */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (kotlin.math.cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x

    fun smooth(a: Double, b: Double, x: Double): Double {
        val s = clamp01((x - a) / (b - a))
        return s * s * (3 - 2 * s)
    }

    /** the rib under screen x, by the flute edges (the flutes are not a ruler's width) */
    fun ribAt(edges: DoubleArray, x: Double): Int {
        var idx = 0
        for (k in 1 until RIBS) if (x >= edges[k]) idx = k
        return idx
    }

    /** front progress 0 (left of the glass) -> 1 (right of it) at loop time t: to mid-screen, parked, on, held, back */
    fun frontAt(t: Double): Double {
        if (t < T_SWEEP1) return 0.0
        if (t < T_SWEEP1 + T_SWEEP1_DUR) return 0.5 * parkEase((t - T_SWEEP1) / T_SWEEP1_DUR)
        if (t < T_SWEEP_ON) return 0.5
        if (t < T_SWEEP_ON + T_SWEEP_ON_DUR) return 0.5 + 0.5 * throughEase((t - T_SWEEP_ON) / T_SWEEP_ON_DUR)
        if (t < T_SWEEP2) return 1.0
        if (t < T_SWEEP2 + T_SWEEP_DUR) return 1 - sweepEase((t - T_SWEEP2) / T_SWEEP_DUR)
        return 0.0
    }

    /** the swap button's scale at loop time t: a press before each sweep */
    fun pressAt(t: Double): Double {
        fun one(t0: Double): Double {
            val a = t - t0
            if (a <= 0 || a >= T_PRESS_IN + T_PRESS_OUT) return 1.0
            if (a < T_PRESS_IN) return 1 - (1 - PRESS_SCALE) * easeOut(a / T_PRESS_IN)
            return PRESS_SCALE + (1 - PRESS_SCALE) * easeOut((a - T_PRESS_IN) / T_PRESS_OUT)
        }
        return one(T_PRESS1) * one(T_PRESS2)
    }

    /**
     * The swap glyph's turn, degrees: 180 x the front's progress, followed through the pop spring, so it turns while the front travels, hangs at 90
     * while the front is parked, and pops past 180 when the glass is clear. Integrated at a fixed 1/120 s from the first press (semi-implicit Euler).
     */
    fun glyphAt(t: Double): Double {
        val h = 1.0 / 120
        val from = T_SWEEP1 - 0.05
        val steps = floor((min(t, T_REST) - from) / h).toInt()
        var g = 0.0
        var v = 0.0
        for (i in 0 until steps) {
            val target = 180 * frontAt(from + i * h)
            v += (POP_K * (target - g) - POP_C * v) * h
            g += v * h
        }
        return g
    }

    /** inverse of a monotone 0..1 easing by bisection (once per layout) */
    private fun inverse(ease: (Double) -> Double, y: Double): Double {
        var lo = 0.0
        var hi = 1.0
        for (i in 0 until 40) {
            val mid = (lo + hi) / 2
            if (ease(mid) < y) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    fun makeGeo(w: Double, ribW: Double, front0: Double, front1: Double, edges: DoubleArray, centres: DoubleArray): ReededGlassRevealGeo {
        val tA = DoubleArray(RIBS)
        val tB = DoubleArray(RIBS)
        for (i in 0 until RIBS) {
            val p = (centres[i] - front0) / (front1 - front0)
            tA[i] = if (p <= 0.5) {
                T_SWEEP1 + T_SWEEP1_DUR * inverse(::parkEase, p / 0.5)
            } else {
                T_SWEEP_ON + T_SWEEP_ON_DUR * inverse(::throughEase, (p - 0.5) / 0.5)
            }
            tB[i] = T_SWEEP2 + T_SWEEP_DUR * inverse(::sweepEase, 1 - p)
        }
        return ReededGlassRevealGeo(w, ribW, front0, front1, edges.copyOf(), centres.copyOf(), tA, tB, front0 + 0.5 * (front1 - front0))
    }

    private fun clampT(t: Double): Double {
        val r = t % LOOP
        return if (r < 0) r + LOOP else r
    }

    /** the leading rib swells before it falls: 1 -> 1.25 over the 0.12 s before the front reaches its centre, then it follows the fall */
    private fun swell(tau: Double): Double {
        val rise = smooth(-0.12, 0.0, tau)
        return 1 + SWELL * rise * exp(-max(tau, 0.0) / 0.12)
    }

    fun timelineLook(tIn: Double, g: ReededGlassRevealGeo): ReededGlassRevealLook {
        val t = clampT(tIn)
        val depths = DoubleArray(RIBS)
        var sum = 0.0
        // the soft glint that runs down three flutes at rest wraps through the loop point, so the seam is invisible
        val r = ((t - T_RUN + LOOP) % LOOP) / T_RUN_DUR
        val run = if (r < 1) r else -1.0
        val light = 0.5 * sin((2 * PI * t) / LOOP)
        val breath = 0.2 * sin((8 * PI * t) / LOOP)
        val inMotion = t > 0 && t < T_REST
        // the parked front breathes: its edge moves a third of a rib to and fro at 0.8 Hz
        val park = smooth(T_PARK_START, T_PARK_START + 0.3, t) * (1 - smooth(T_PARK_END - 0.3, T_PARK_END, t))
        val wob = 0.35 * park * sin(2 * PI * 0.8 * (t - T_PARK_START))
        for (i in 0 until RIBS) {
            var d = 1.0
            if (inMotion) {
                val ta = t - g.tA[i]
                val tb = t - g.tB[i]
                d = (1 - springAt(ta, POP_RESPONSE, POP_ZETA)) * swell(ta) + springAt(tb, POP_RESPONSE, POP_ZETA)
                if (park > 0) {
                    val q = (g.centres[i] - g.xPark - 0.3 * g.ribW) / (0.8 * g.ribW)
                    d += wob * exp(-q * q)
                }
            }
            depths[i] = d
            sum += d
        }
        val front = if (inMotion) g.front0 + (g.front1 - g.front0) * frontAt(t) else g.front0
        return ReededGlassRevealLook(
            depths, if (inMotion) glyphAt(t) else 0.0, if (inMotion) pressAt(t) else 1.0, front, sum / RIBS,
            if (inMotion) g.front0 + (g.front1 - g.front0) * frontAt(t - 0.06) else g.front0, run, light, breath, 0.0,
        )
    }

    /** hand over from the timeline at loop time t: positions and velocities, no jump */
    fun liveFromTimeline(tIn: Double, g: ReededGlassRevealGeo): ReededGlassRevealLive {
        val t = clampT(tIn)
        val a = timelineLook(t, g)
        val b = timelineLook(t + 1.0 / 120, g)
        val m = ReededGlassRevealLive(g)
        for (i in 0 until RIBS) m.vel[i] = (b.depths[i] - a.depths[i]) * 120
        // the state flips with the glyph's turn; the front springs to the nearer end
        val revealed = t >= T_GLYPH1 && t < T_GLYPH2
        val p = (a.front - g.front0) / (g.front1 - g.front0)
        m.fx = a.front
        m.fv = (b.front - a.front) * 120
        m.target = if (p >= 0.5) g.front1 else g.front0
        m.sweepFrom = a.front
        m.sweepTo = a.front
        m.depth = a.depths.copyOf()
        m.glyph = a.glyph
        m.glyphV = (b.glyph - a.glyph) * 120
        m.glyphTarget = 180 * min(1.0, max(0.0, p))
        m.revealed = revealed
        m.press = a.press
        m.fl = a.lag
        m.light = a.light
        return m
    }

    /** a resting live state (no autoplay, or Reduce Motion) */
    fun restLive(g: ReededGlassRevealGeo) = ReededGlassRevealLive(g)

    /** the glass already clear (defaultRevealed) */
    fun clearedLive(g: ReededGlassRevealGeo): ReededGlassRevealLive {
        val m = restLive(g)
        for (i in 0 until RIBS) m.depth[i] = 0.0
        m.fx = g.front1
        m.target = g.front1
        m.fl = g.front1
        m.revealed = true
        m.glyph = 180.0
        m.glyphTarget = 180.0
        m.fade = 0.0
        return m
    }

    /** start a timed sweep (the button) from wherever the front is, at the loop's speed */
    fun startSweep(m: ReededGlassRevealLive, g: ReededGlassRevealGeo, to: Double) {
        m.mode = SWEEP
        m.sweepFrom = m.fx
        m.sweepTo = to
        m.sweepT = 0.0
        m.sweepDur = max(0.35, (T_SWEEP_DUR * abs(to - m.fx)) / (g.front1 - g.front0))
        m.target = to
    }

    /** finger displacement is direct inside the travel limits, with resistance beyond them; a flipped front runs the other way */
    fun dragLive(m: ReededGlassRevealLive, g: ReededGlassRevealGeo, frontStart: Double, translation: Double, velocity: Double) {
        val sign = if (m.flip) -1.0 else 1.0
        val raw = frontStart + sign * translation
        val over = if (raw < g.front0) raw - g.front0 else if (raw > g.front1) raw - g.front1 else 0.0
        val front = if (over == 0.0) raw else (if (raw < g.front0) g.front0 else g.front1) + over / (1 + abs(over) / 120)
        m.mode = DRAG
        m.fx = front
        m.target = front
        m.fv = sign * velocity
    }

    /** from a resting end the first dp of a drag decide which edge the front starts from */
    fun decideDirection(m: ReededGlassRevealLive, g: ReededGlassRevealGeo, startX: Double, translation: Double) {
        val atMasked = m.fx <= g.front0 + 1 && !m.revealed
        if (!atMasked) return
        m.flip = startX > g.w * 0.5 && translation < 0
    }

    /** release carries velocity into the spring and selects the end the projected front is nearer to (past `commit` it clears) */
    fun releaseLive(m: ReededGlassRevealLive, g: ReededGlassRevealGeo, velocity: Double, commit: Double = 0.5) {
        val v = if (m.flip) -velocity else velocity
        val projected = m.fx + v * PROJECTION
        val p = (projected - g.front0) / (g.front1 - g.front0)
        m.target = if (p >= commit) g.front1 else g.front0
        m.mode = SPRING
        m.fv = v
    }

    /** cancellation releases the hold but never activates the reduced-motion toggle */
    fun finishTouch(m: ReededGlassRevealLive, g: ReededGlassRevealGeo, dragged: Boolean, velocity: Double, reduce: Boolean, success: Boolean, commit: Double = 0.5) {
        m.touching = false
        if (m.peek > 0.05) {
            m.peekRel = 0.0
            m.mode = SPRING
            return
        }
        if (reduce && success) {
            m.target = if (m.target >= g.front1 - 1) g.front0 else g.front1
        } else if (!reduce && (m.mode == DRAG || dragged)) {
            releaseLive(m, g, if (success) velocity else 0.0, commit)
        }
        m.mode = SPRING
    }

    /** only real input updates lastTouch; completion events never extend this deadline */
    fun shouldResume(m: ReededGlassRevealLive, now: Double, lastTouch: Double) =
        !m.touching && m.mode == SPRING && m.peek < 0.02 && now - lastTouch >= RESUME_AFTER

    /** one live step of dt seconds; `now` is the clock (s) for rate-limiting ticks */
    fun stepLive(m: ReededGlassRevealLive, dt: Double, g: ReededGlassRevealGeo, reduce: Boolean, now: Double, peekOn: Boolean = true): ReededGlassRevealEvents {
        val ev = ReededGlassRevealEvents()
        val steps = max(1, ceil(dt / (1.0 / 240)).toInt())
        val h = dt / steps
        if (reduce) {
            // Reduce Motion: no travelling front; the whole pane dissolves between deep and clear
            val want = if (m.target >= g.front1 - 1) 0.0 else 1.0
            val dir = if (want < m.fade) -1.0 else 1.0
            m.fade = clamp01(m.fade + (dir * dt) / REDUCED_FADE)
            if ((dir < 0 && m.fade < want) || (dir > 0 && m.fade > want)) m.fade = want
            m.fx = m.target
            m.fv = 0.0
            m.mode = SPRING
            for (i in 0 until RIBS) {
                m.depth[i] = m.fade
                m.vel[i] = 0.0
            }
        } else {
            // a long press opens a window that follows the finger; it closes outward from the finger on lift
            if (peekOn && m.touching && !m.dragged && m.mode == HOLD) {
                m.holdT += dt
                if (m.holdT >= PEEK_AFTER) m.peek = clamp01(m.peek + dt / 0.12)
            } else if (m.touching && m.peek > 0) {
                m.peek = clamp01(m.peek + dt / 0.12) // an open window stays open while the finger slides
            } else if (m.peekRel >= 0) {
                m.peekRel += dt
                if (m.peekRel > PEEK_RETURN * RIBS + 0.1) {
                    m.peekRel = -1.0
                    m.peek = 0.0
                }
            } else if (!m.touching) {
                m.peek = 0.0
            }
            if (!m.touching) m.holdT = 0.0
            m.speedK = max(m.speedK * exp(-dt / 0.35), clamp01(abs(m.fv) / 1800))
            val damping = WAKE_SLOW + (WAKE_FAST - WAKE_SLOW) * m.speedK
            val soft = RIB_SOFT * g.ribW
            val mirror = g.front0 + g.front1
            val iTouch = ribAt(g.edges, m.touchX)
            for (s in 0 until steps) {
                // the front
                if (m.mode == SWEEP) {
                    m.sweepT += h
                    val p = clamp01(m.sweepT / m.sweepDur)
                    val nx = m.sweepFrom + (m.sweepTo - m.sweepFrom) * sweepEase(p)
                    m.fv = (nx - m.fx) / h
                    m.fx = nx
                    if (p >= 1) {
                        m.mode = SPRING
                        m.fv = 0.0
                    }
                } else if (m.mode == SPRING) {
                    val a = GLIDE_K * (m.target - m.fx) - GLIDE_C * m.fv
                    m.fv += a * h
                    m.fx += m.fv * h
                }
                // each rib follows its own target through its own spring
                for (i in 0 until RIBS) {
                    val c = if (m.flip) mirror - g.centres[i] else g.centres[i]
                    var target = 1 - smooth(c - soft, c + soft, m.fx)
                    // the leading rib swells before it falls
                    if (m.mode != HOLD || m.dragged) {
                        val lead = (c - m.fx - 0.9 * g.ribW) / (0.7 * g.ribW)
                        if (c > m.fx && target > 0.5) target += SWELL * exp(-lead * lead) * clamp01(abs(m.fv) / 120)
                    }
                    if (m.touching) {
                        val q = (g.centres[i] - m.touchX) / (DIP_RADIUS * g.ribW)
                        target *= 1 - DIP * exp(-q * q)
                    }
                    // the peek window: the ribs around the finger lie almost flat; after a lift they close outward, 30 ms apart
                    var open = 0.0
                    if (m.touching) open = m.peek else if (m.peekRel >= 0 && m.peekRel < PEEK_RETURN * abs(i - iTouch)) open = 1.0
                    if (open > 0) {
                        val q = (g.centres[i] - m.touchX) / (PEEK_SIGMA * g.ribW)
                        val win = exp(-q * q)
                        target = min(target, 1 - (1 - PEEK_DEPTH) * open * win)
                    }
                    val a = POP_K * (target - m.depth[i]) - damping * m.vel[i]
                    m.vel[i] += a * h
                    m.depth[i] += m.vel[i] * h
                }
            }
        }
        m.press += (1 - m.press) * min(1.0, dt / 0.08)
        // the glyph turns with the front (180 x progress) through the pop spring
        m.glyphTarget = 180 * clamp01((m.fx - g.front0) / (g.front1 - g.front0))
        val ga = POP_K * (m.glyphTarget - m.glyph) - POP_C * m.glyphV
        m.glyphV += ga * dt
        m.glyph += m.glyphV * dt
        if (reduce) {
            m.glyph = 0.0
            m.glyphV = 0.0
        }
        // the lagging front the capsule's highlight follows (60 ms), and the light: it follows the finger, else settles at the centre
        m.fl += (m.fx - m.fl) * (1 - exp(-dt / 0.06))
        val wantLight = if (m.touching && m.touchX > -1e8) max(-1.0, min(1.0, (m.touchX / g.w - 0.5) * 1.6)) else 0.0
        m.light += (wantLight - m.light) * (1 - exp(-dt / 0.25))
        // a tick each time the front crosses a rib under the finger (<= 16 per second); the last rib is a harder tap
        if (m.mode == DRAG) {
            val rib = ribAt(g.edges, m.fx)
            if (m.lastRib >= 0 && rib != m.lastRib && rib >= 0 && rib < RIBS && now - m.lastTick >= TICK_GAP) {
                ev.tick = true
                ev.edge = rib == 0 || rib == RIBS - 1
                m.lastTick = now
            }
            m.lastRib = rib
        } else {
            m.lastRib = -1
        }
        // completed states
        if (m.mode != DRAG && m.mode != SWEEP && !m.touching && m.peek < 0.02) {
            val atEnd = abs(m.fx - m.target) < 1 && abs(m.fv) < 8
            var clear = true
            var masked = true
            for (i in 0 until RIBS) {
                if (m.depth[i] > 0.02) clear = false
                if (m.depth[i] < 0.98) masked = false
            }
            if (atEnd && clear && m.target >= g.front1 - 1 && !m.revealed) {
                m.revealed = true
                ev.revealed = true
            } else if (atEnd && masked && m.target <= g.front0 + 1 && m.revealed) {
                m.revealed = false
                m.flip = false
                ev.masked = true
            } else if (atEnd && masked && m.target <= g.front0 + 1) {
                m.flip = false
            }
        }
        return ev
    }

    /** the look of a live state */
    fun liveLook(m: ReededGlassRevealLive, pressScale: Double): ReededGlassRevealLook {
        var sum = 0.0
        for (i in 0 until RIBS) sum += m.depth[i]
        return ReededGlassRevealLook(m.depth.copyOf(), m.glyph, pressScale * m.press, m.fx, sum / RIBS, m.fl, -1.0, m.light, 0.0, m.peek)
    }

    /** true once a live state has come back to the timeline's first frame */
    fun liveAtRest(m: ReededGlassRevealLive, g: ReededGlassRevealGeo): Boolean {
        if (abs(m.fx - g.front0) > 1 || abs(m.fv) > 8) return false
        if (abs(m.glyph) > 0.5 || abs(m.glyphV) > 2) return false
        if (m.peek > 0.01 || m.peekRel >= 0 || abs(m.light) > 0.02) return false
        for (i in 0 until RIBS) if (abs(m.depth[i] - 1) > SETTLE * 5 || abs(m.vel[i]) > 0.05) return false
        return true
    }

    /** depth of the glass at screen x (the rib under it) */
    fun depthAt(depths: DoubleArray, x: Double, edges: DoubleArray) = depths[ribAt(edges, x)]
}
