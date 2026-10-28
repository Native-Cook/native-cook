package com.ngocdevv.nativecook.blocks.pickers.guestcountfigures

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Guest Count Figures — the model, the springs and the autoPlay script (a port of
 GuestCountFiguresMotion.swift, same numbers).

 The screen is a pure function of an event list and the time: each event sets the state
 (adults, children, rows or the big counter) at a time and may name the control a finger pressed.
 Positions are chained analytic springs: every event restarts the spring from the value and the
 velocity it had, towards the new target. A figure that appears starts at its slot and hops in;
 one that leaves crouches, rises and fades.

 autoPlay: LOOP = 10 s
   0.0 – 1.2   rest: one adult, the big counter
   1.2         big +        → 2 adults (the second adult hops in, the pair re-centres)
   2.5         Add children → the counter folds into the Adults / Children rows
   3.6, 4.6    children +   → 1, 2 children (each hops in; at 4 people both + disable)
   4.6 – 6.2   peak hold
   6.2, 6.9    children −   → 1, 0
   7.6         Adults only  → back to the big counter
   8.5         big −        → 1 adult
   9.1 – 10.0  rest (the frame at 10 is the frame at 0)
*/

internal data class GcfState(val adults: Int, val children: Int, val rows: Boolean)

/** Controls a finger can press: adult −, adult +, child −, child +, the pill. */
internal object GcfControl {
    const val NONE = -1
    const val ADULT_MINUS = 0
    const val ADULT_PLUS = 1
    const val CHILD_MINUS = 2
    const val CHILD_PLUS = 3
    const val PILL = 4
}

internal class GcfEvent(val t: Double, val s: GcfState, val press: Int)

internal class GcfFigureFrame(
    val index: Int,
    val x: Double,
    val lift: Double,
    val sx: Double,
    val sy: Double,
    val alpha: Double,
)

internal class GcfFrame {
    val figures = ArrayList<GcfFigureFrame>(7)
    /** 0 = the big counter, 1 = the two rows. */
    var m = 0.0
    var adultsV = 1.0
    var childrenV = 0.0
    /** Disabled amount of adult −, adult +, child −, child +. */
    val disabled = doubleArrayOf(1.0, 0.0, 1.0, 0.0)
    /** Press amount of adult −, adult +, child −, child +, the pill. */
    val press = DoubleArray(5)
}

internal class GcfGeo(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    val cx = w / 2
    val ground = top + 296
    val numY = top + 405
    val rowA = top + 385
    val rowC = top + 463
    val divider = top + 424
    val pillY = top + 566
    val footY = h - bottom - 40
    val progressY = h - bottom - 84
    val rowPlusX = w - 42
    val rowNumX = w - 80
    val rowMinusX = w - 118
}

internal object GcfMotion {
    const val LOOP = 10.0
    const val MAX_PEOPLE = 4
    val REST = GcfState(1, 0, false)
    val PEAK = GcfState(2, 2, true)
    const val IDLE_BEFORE_BACK = 2.0
    const val SETTLE = 1.6

    val SCRIPT = listOf(
        GcfEvent(0.0, REST, GcfControl.NONE),
        GcfEvent(1.2, GcfState(2, 0, false), GcfControl.ADULT_PLUS),
        GcfEvent(2.5, GcfState(2, 0, true), GcfControl.PILL),
        GcfEvent(3.6, GcfState(2, 1, true), GcfControl.CHILD_PLUS),
        GcfEvent(4.6, GcfState(2, 2, true), GcfControl.CHILD_PLUS),
        GcfEvent(6.2, GcfState(2, 1, true), GcfControl.CHILD_MINUS),
        GcfEvent(6.9, GcfState(2, 0, true), GcfControl.CHILD_MINUS),
        GcfEvent(7.6, GcfState(2, 0, false), GcfControl.PILL),
        GcfEvent(8.5, REST, GcfControl.ADULT_MINUS),
    )

    // Figures: 0…3 adults, 4…6 children, in that order along the ground.
    const val FIGURES = 7
    const val ADULT_SLOT = 46.0
    const val CHILD_SLOT = 38.0
    const val GAP = 12.0
    /** Every figure is drawn 10 % larger than its art (GcfArt), slots included. */
    const val FIGURE_SCALE = 1.1

    fun isChild(i: Int) = i >= 4
    fun present(i: Int, s: GcfState) = if (i < 4) i < s.adults else (i - 4) < s.children
    fun slot(i: Int) = (if (isChild(i)) CHILD_SLOT else ADULT_SLOT) * FIGURE_SCALE

    /** Centre x of every figure in state s (NaN when absent), and its rank along the row. */
    fun layout(s: GcfState, cx: Double, xs: DoubleArray, rank: IntArray) {
        var total = 0.0
        var n = 0
        for (i in 0 until FIGURES) if (present(i, s)) { total += slot(i); n++ }
        total += GAP * max(0, n - 1)
        var cursor = cx - total / 2
        var r = 0
        for (i in 0 until FIGURES) {
            if (present(i, s)) {
                xs[i] = cursor + slot(i) / 2
                rank[i] = r
                cursor += slot(i) + GAP
                r++
            } else {
                xs[i] = Double.NaN
                rank[i] = -1
            }
        }
    }

    // Curves.

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun easeOut(x: Double): Double { val k = 1 - x; return 1 - k * k * k }
    fun easeIn(x: Double) = x * x * x
    fun smooth(x: Double): Double { val c = clamp01(x); return c * c * (3 - 2 * c) }

    /** Advances an underdamped spring (displacement d from its target, velocity v) by dt; writes out[0], out[1]. */
    fun step(d: Double, v: Double, dt: Double, response: Double, damping: Double, out: DoubleArray) {
        if (dt <= 0) { out[0] = d; out[1] = v; return }
        val w0 = 2 * PI / response
        val a = damping * w0
        val b = w0 * sqrt(1 - damping * damping)
        val e = exp(-a * dt)
        if (e < 1e-9) { out[0] = 0.0; out[1] = 0.0; return }
        val c = cos(b * dt)
        val s = sin(b * dt)
        val bb = (v + a * d) / b
        out[0] = e * (d * c + bb * s)
        out[1] = e * (v * c - (a * v + w0 * w0 * d) / b * s)
    }

    // Channels: 0…6 figure x, 7 mode, 8 adults value, 9 children value, 10…13 disabled.
    const val CHANNELS = 14
    private val RESPONSE = doubleArrayOf(0.55, 0.55, 0.55, 0.55, 0.55, 0.55, 0.55, 0.5, 0.42, 0.42, 0.3, 0.3, 0.3, 0.3)
    private val DAMPING = doubleArrayOf(0.72, 0.72, 0.72, 0.72, 0.72, 0.72, 0.72, 0.86, 0.72, 0.72, 0.95, 0.95, 0.95, 0.95)

    private fun targets(s: GcfState, cx: Double, out: DoubleArray, rank: IntArray) {
        layout(s, cx, out, rank)
        out[7] = if (s.rows) 1.0 else 0.0
        out[8] = s.adults.toDouble()
        out[9] = s.children.toDouble()
        val total = s.adults + s.children
        out[10] = if (s.adults <= 1) 1.0 else 0.0
        out[11] = if (total >= MAX_PEOPLE) 1.0 else 0.0
        out[12] = if (s.children <= 0) 1.0 else 0.0
        out[13] = if (total >= MAX_PEOPLE) 1.0 else 0.0
    }

    // Figure moves: out = (lift, scale, sx, sy, alpha).

    /** A figure hopping in, τ seconds after it appeared. */
    fun enter(tau: Double, out: DoubleArray) {
        out[4] = clamp01(tau / 0.12)
        out[1] = 0.55 + 0.45 * easeOut(clamp01(tau / 0.30))
        out[0] = 0.0; out[2] = 1.0; out[3] = 1.0
        if (tau < 0.44) {
            out[0] = 36 * sin(PI * max(0.0, tau) / 0.44)
        } else {
            val t2 = tau - 0.44
            val s = 0.12 * exp(-7 * t2) * cos(2 * PI * t2 / 0.26)
            out[3] = 1 - s
            out[2] = 1 + 0.6 * s
        }
    }

    /** A figure leaving, τ seconds after it was removed. */
    fun exit(tau: Double, out: DoubleArray) {
        if (tau < 0.08) {
            val k = tau / 0.08
            out[0] = 0.0; out[1] = 1.0; out[2] = 1 + 0.03 * k; out[3] = 1 - 0.06 * k; out[4] = 1.0
            return
        }
        val u = clamp01((tau - 0.08) / 0.26)
        out[0] = 22 * easeOut(u)
        out[1] = 1 - 0.45 * easeIn(u)
        out[2] = 1.03 - 0.03 * u
        out[3] = 0.94 + 0.06 * u
        out[4] = 1 - easeIn(u)
    }

    const val EXIT_TIME = 0.34

    /** The frame at time t of an event list (sorted by time, the first one is the base). */
    fun frame(ev: List<GcfEvent>, t: Double, cx: Double): GcfFrame {
        val f = GcfFrame()
        if (ev.isEmpty()) return f
        val rank = IntArray(FIGURES)
        val tg = DoubleArray(CHANNELS)
        val nt = DoubleArray(CHANNELS)
        val x = DoubleArray(CHANNELS)
        val v = DoubleArray(CHANNELS)
        val o = DoubleArray(2)

        // Chained springs.
        targets(ev[0].s, cx, tg, rank)
        for (c in 0 until CHANNELS) x[c] = if (tg[c].isNaN()) cx else tg[c]
        var last = ev[0].t
        var k = 1
        while (k < ev.size && ev[k].t <= t) {
            val dt = ev[k].t - last
            for (c in 0 until CHANNELS) {
                step(x[c] - tg[c], v[c], dt, RESPONSE[c], DAMPING[c], o)
                x[c] = tg[c] + o[0]
                v[c] = o[1]
            }
            targets(ev[k].s, cx, nt, rank)
            for (c in 0 until CHANNELS) {
                if (nt[c].isNaN()) continue                  // an absent figure keeps where it was
                if (c < FIGURES && !present(c, ev[k - 1].s)) {
                    x[c] = nt[c]                             // an arriving figure starts at its slot
                    v[c] = 0.0
                }
                tg[c] = nt[c]
            }
            last = ev[k].t
            k++
        }
        val dt = t - last
        for (c in 0 until CHANNELS) {
            step(x[c] - tg[c], v[c], dt, RESPONSE[c], DAMPING[c], o)
            x[c] = tg[c] + o[0]
            v[c] = o[1]
        }
        val applied = k
        val now = ev[applied - 1].s

        f.m = x[7]
        f.adultsV = x[8]
        f.childrenV = x[9]
        for (c in 0 until 4) f.disabled[c] = x[10 + c]

        // Presses.
        for (e in ev) {
            if (e.press == GcfControl.NONE) continue
            val d = t - e.t
            if (d <= -0.1 || d >= 0.18) continue
            val amount = if (d < 0) smooth((d + 0.1) / 0.1) else 1 - smooth(d / 0.18)
            f.press[e.press] = max(f.press[e.press], amount)
        }

        // Figures: the last time each one arrived or left.
        val changeAt = DoubleArray(FIGURES) { Double.NEGATIVE_INFINITY }
        val entered = BooleanArray(FIGURES)
        for (j in 1 until applied) {
            for (i in 0 until FIGURES) {
                val was = present(i, ev[j - 1].s)
                val isIn = present(i, ev[j].s)
                if (was != isIn) {
                    changeAt[i] = ev[j].t
                    entered[i] = isIn
                }
            }
        }

        // Welcome hops: when someone arrives, the others bounce, nearest first.
        val welcome = DoubleArray(FIGURES)
        val xs = DoubleArray(FIGURES)
        var j = applied - 1
        while (j >= 1 && t - ev[j].t < 1.2) {
            layout(ev[j].s, cx, xs, rank)
            for (n in 0 until FIGURES) {
                if (!present(n, ev[j].s) || present(n, ev[j - 1].s)) continue
                for (i in 0 until FIGURES) {
                    if (i == n || !present(i, ev[j].s) || !present(i, now)) continue
                    val dist = abs(rank[i] - rank[n]).toDouble()
                    val u = (t - ev[j].t - 0.30 - 0.05 * dist) / 0.30
                    if (u > 0 && u < 1) welcome[i] = max(welcome[i], 7 * sin(PI * u))
                }
            }
            j--
        }

        val mv = DoubleArray(5)
        for (i in 0 until FIGURES) {
            val isIn = present(i, now)
            val tau = t - changeAt[i]
            if (isIn) {
                if (changeAt[i].isFinite() && entered[i]) enter(tau, mv)
                else { mv[0] = 0.0; mv[1] = 1.0; mv[2] = 1.0; mv[3] = 1.0; mv[4] = 1.0 }
            } else {
                if (!(changeAt[i].isFinite() && !entered[i] && tau < EXIT_TIME)) continue
                exit(tau, mv)
            }
            f.figures.add(GcfFigureFrame(i, x[i], mv[0] + welcome[i], mv[1] * mv[2], mv[1] * mv[3], mv[4]))
        }
        return f
    }

    fun scriptFrame(t: Double, cx: Double) = frame(SCRIPT, t, cx)
}

/** The live model: the autoPlay clock, and the session a touch starts. */
internal class GcfSim {
    private var loopStart: Double? = null
    private var session: MutableList<GcfEvent>? = null
    @Volatile var touching = false
    var lastTouch = Double.NEGATIVE_INFINITY
        private set
    private var restQueued = false

    fun frame(now: Double, cx: Double, auto: Boolean, reduced: Boolean): GcfFrame {
        if (reduced) {
            return GcfMotion.frame(session ?: listOf(GcfEvent(0.0, GcfMotion.PEAK, GcfControl.NONE)), now + 100, cx)
        }
        val ev = session
        if (ev != null) {
            if (auto && !touching) {
                if (!restQueued && now >= lastTouch + GcfMotion.IDLE_BEFORE_BACK) {
                    ev.add(GcfEvent(lastTouch + GcfMotion.IDLE_BEFORE_BACK, GcfMotion.REST, GcfControl.NONE))
                    restQueued = true
                }
                if (restQueued && now >= lastTouch + GcfMotion.IDLE_BEFORE_BACK + GcfMotion.SETTLE) {
                    session = null
                    loopStart = lastTouch + GcfMotion.IDLE_BEFORE_BACK + GcfMotion.SETTLE
                    return GcfMotion.scriptFrame(loopTime(now), cx)
                }
            }
            return GcfMotion.frame(ev, now, cx)
        }
        if (!auto) return GcfMotion.frame(listOf(GcfEvent(0.0, GcfMotion.REST, GcfControl.NONE)), 100.0, cx)
        if (loopStart == null) loopStart = now
        return GcfMotion.scriptFrame(loopTime(now), cx)
    }

    private fun loopTime(now: Double): Double {
        val t = (now - (loopStart ?: now)) % GcfMotion.LOOP
        return if (t < 0) t + GcfMotion.LOOP else t
    }

    /** The state the screen is heading to now. */
    fun state(now: Double, auto: Boolean, reduced: Boolean): GcfState {
        session?.let { return it.last().s }
        if (reduced) return GcfMotion.PEAK
        if (!auto || loopStart == null) return GcfMotion.REST
        val t = loopTime(now)
        return GcfMotion.SCRIPT.lastOrNull { it.t <= t }?.s ?: GcfMotion.REST
    }

    /** Starts a session that carries on from what the screen shows. */
    private fun begin(now: Double, auto: Boolean, reduced: Boolean) {
        if (session != null) return
        val start = loopStart
        session = when {
            reduced -> mutableListOf(GcfEvent(now - 10, GcfMotion.PEAK, GcfControl.NONE))
            auto && start != null -> {
                val base = start + floor((now - start) / GcfMotion.LOOP) * GcfMotion.LOOP
                val ev = GcfMotion.SCRIPT.map { GcfEvent(base - GcfMotion.LOOP + it.t, it.s, it.press) }.toMutableList()
                GcfMotion.SCRIPT.filter { base + it.t <= now }.forEach { ev.add(GcfEvent(base + it.t, it.s, it.press)) }
                ev
            }
            else -> mutableListOf(GcfEvent(now - 10, GcfMotion.REST, GcfControl.NONE))
        }
    }

    fun touch(now: Double, auto: Boolean, reduced: Boolean) {
        begin(now, auto, reduced)
        lastTouch = now
        restQueued = false
    }

    /** Applies a new state from a finger. Returns true when something changed. */
    fun apply(s: GcfState, press: Int, now: Double, auto: Boolean, reduced: Boolean): Boolean {
        touch(now, auto, reduced)
        val ev = session ?: return false
        if (ev.last().s == s) return false
        ev.add(GcfEvent(now, s, press))
        // Everything older than 3 s has settled: keep the last such event as the base.
        val j = ev.indexOfLast { it.t < now - 3 }
        if (j > 0) repeat(j) { ev.removeAt(0) }
        return true
    }
}
