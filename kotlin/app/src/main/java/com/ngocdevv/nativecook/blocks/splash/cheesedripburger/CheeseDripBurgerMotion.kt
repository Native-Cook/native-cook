package com.ngocdevv.nativecook.blocks.splash.cheesedripburger

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Timing and the cheese model of Cheese Drip Burger, ported from CheeseDripBurgerMotion.swift.
 * Every value is a pure function of the loop time t (0 ..< LOOP) and, for touch, of the clock.
 */
object CheeseDripBurgerMotion {
    const val LOOP = 8.0

    fun clamp(v: Double, a: Double = 0.0, b: Double = 1.0) = min(max(v, a), b)
    fun mod(a: Double, b: Double): Double { val r = a % b; return if (r < 0) r + b else r }
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun lerp(a: Double, b: Double, f: Double) = a + (b - a) * f
    fun hash(i: Double): Double { val s = sin(i * 12.9898 + 78.233) * 43758.5453; return s - floor(s) }

    // Cheese layout (burger space: points inside the 372 pt image square, origin top-left).

    const val IMAGE_SIZE = 372.0

    class Drip(val u: Double, val w: Double, val rest: Double, val peak: Double)

    /** One slice: its front edge is the line y + slope·(x − cx), with a slight bow of `sag`. */
    class Layer(val cx: Double, val half: Double, val y: Double, val slope: Double, val sag: Double, val band: Double, val drips: List<Drip>) {
        fun edge(x: Double): Double { val u = (x - cx) / half; return y + slope * (x - cx) + sag * (1 - u * u) }
    }

    /** Index 0: the lower slice (between the patties); 1: the upper slice (under the lettuce). */
    val layers = listOf(
        Layer(173.0, 132.0, 261.0, 0.06, 1.5, 9.0, listOf(
            Drip(-0.98, 20.0, 16.0, 44.0),
            Drip(-0.66, 30.0, 22.0, 80.0),
            Drip(-0.36, 22.0, 10.0, 24.0),
            Drip(-0.06, 34.0, 26.0, 70.0),
            Drip(0.26, 24.0, 14.0, 56.0),
            Drip(0.52, 30.0, 20.0, 80.0),
            Drip(0.76, 20.0, 10.0, 24.0),
            Drip(0.98, 20.0, 15.0, 50.0),
        )),
        Layer(196.0, 114.0, 218.0, -0.026, 1.5, 8.0, listOf(
            Drip(-0.97, 18.0, 12.0, 40.0),
            Drip(-0.60, 28.0, 18.0, 56.0),
            Drip(-0.25, 20.0, 9.0, 34.0),
            Drip(0.08, 30.0, 20.0, 58.0),
            Drip(0.42, 22.0, 11.0, 40.0),
            Drip(0.72, 26.0, 16.0, 54.0),
            Drip(0.98, 18.0, 12.0, 38.0),
        )),
    )

    const val PUDDLE_Y = 358.0
    const val PUDDLE_CX = 187.0

    /** Melt of one drip (0 rest … 1 peak): builds 1.6 → 4.3 s, holds, sets back 5.0 → 7.0 s. */
    fun meltLoop(t: Double, delay: Double): Double {
        val up = easeInOutCubic(seg(t, 1.6 + delay, 4.3 + delay * 0.5))
        val down = easeInOutCubic(seg(t, 5.0 + delay * 0.6, 7.0))
        return up * (1 - down)
    }

    fun delay(layer: Int, drip: Int) = hash((layer * 17 + drip).toDouble() + 3) * 0.35

    const val ROWS = 3

    fun rowLit(t: Double, i: Int): Double {
        val up = easeOutCubic(seg(t, 1.2 + 0.09 * i, 1.75 + 0.09 * i))
        val down = easeInOutCubic(seg(t, 3.5 + 0.07 * i, 4.3 + 0.07 * i))
        return up * (1 - down)
    }

    fun rowSlide(t: Double, i: Int): Double {
        val up = easeOutCubic(seg(t, 1.2 + 0.09 * i, 1.75 + 0.09 * i))
        return (1 - up) * 70 * (if (i % 2 == 0) -1 else 1)
    }

    class Event(val layer: Int, val drip: Int, val t0: Double)

    val loopEvents = listOf(Event(0, 2, 4.15), Event(0, 6, 4.6))

    const val PINCH_LEAD = 0.6
    const val EVENT_TAIL = 1.4
    const val GRAVITY = 1100.0

    const val FALL_TIME = 0.42
    const val FALL_FROM = -560.0

    /** Burger offset (pt) and scale (x, y) at τ s after a tap. */
    fun dropIn(tau: Double): DoubleArray {
        if (tau < 0 || tau > 2.5) return doubleArrayOf(0.0, 1.0, 1.0)
        if (tau < FALL_TIME) {
            val f = tau / FALL_TIME
            return doubleArrayOf(FALL_FROM * (1 - f * f), 1 - 0.04 * f, 1 + 0.05 * f)
        }
        val s = exp(-6.9 * (tau - FALL_TIME)) * cos(10.5 * (tau - FALL_TIME))
        return doubleArrayOf(0.0, 1 + 0.09 * s, 1 - 0.12 * s)
    }

    fun jiggle(tau: Double): Double {
        val d = tau - FALL_TIME
        if (d < 0 || d > 2.0) return 0.0
        return 16 * exp(-4.5 * d) * sin(13 * d)
    }

    class DripShape(val x: Double, val y0: Double, val w: Double, val length: Double, val r: Double, val neck: Double)
    class DropShape(val x: Double, val y: Double, val rx: Double, val ry: Double)
    class Bump(val layer: Int, val x: Double, val amp: Double) // layer 2 = the puddle

    class Frame(val drips: List<List<DripShape>>, val drops: List<DropShape>, val bumps: List<Bump>, val melt: Double)

    fun dripLength(d: Drip, m: Double, ambient: Double, seed: Double): Double {
        val k = 1 + floor(hash(seed) * 3)
        val wob = 1.2 * sin(2 * Math.PI * k * ambient / LOOP + seed * 6.1) * (0.4 + m)
        return d.rest + (d.peak - d.rest) * m + wob
    }

    /** The whole cheese at one moment; `events` pairs an event with the seconds since its t0. */
    fun frame(melts: List<DoubleArray>, events: List<Pair<Event, Double>>, ambient: Double, jig: Double): Frame {
        var sum = 0.0
        var n = 0.0
        val drips = layers.mapIndexed { li, layer ->
            layer.drips.mapIndexed { di, d ->
                val m = melts[li][di]
                sum += m; n += 1
                val seed = (li * 31 + di).toDouble()
                val x = layer.cx + d.u * layer.half
                var length = dripLength(d, m, ambient, seed) + jig * (0.5 + hash(seed + 9))
                var r = d.w * 0.5 * (0.8 + 0.12 * m)
                var neck = 0.35 * m
                for ((e, dt) in events) {
                    if (e.layer != li || e.drip != di) continue
                    if (dt < 0) {
                        val q = easeInCubic(clamp((dt + PINCH_LEAD) / PINCH_LEAD))
                        length += 18 * q
                        r *= 1 + 0.25 * q
                        neck = lerp(neck, 0.92, q)
                    } else {
                        val back = 0.5 * exp(-4.5 * dt) * cos(8 * dt)
                        length *= 1 - max(0.0, back)
                        neck = lerp(neck, 0.5, exp(-6 * dt))
                    }
                }
                DripShape(x, layer.edge(x), d.w, max(length, 3.0), r, neck)
            }
        }
        val drops = ArrayList<DropShape>()
        val bumps = ArrayList<Bump>()
        for ((e, dt) in events) {
            if (dt < 0) continue
            val layer = layers[e.layer]
            val d = layer.drips[e.drip]
            val m = melts[e.layer][e.drip]
            val seed = (e.layer * 31 + e.drip).toDouble()
            val x = layer.cx + d.u * layer.half
            val r0 = d.w * 0.5 * (0.8 + 0.12 * m) * 1.18
            val start = layer.edge(x) + dripLength(d, m, ambient, seed) + 18
            val target: Double
            val targetLayer: Int
            if (e.layer == 1) {
                val below = layers[0]
                target = below.edge(x) + below.band + 4
                targetLayer = 0
            } else {
                target = PUDDLE_Y + 2
                targetLayer = 2
            }
            val v0 = 20.0
            val dist = max(0.0, target - r0 - start)
            val dtLand = (-v0 + sqrt(v0 * v0 + 2 * GRAVITY * dist)) / GRAVITY
            if (dt < dtLand) {
                val y = start + v0 * dt + 0.5 * GRAVITY * dt * dt
                val v = v0 + GRAVITY * dt
                val st = min(v / 1100, 0.45)
                drops.add(DropShape(x, y, r0 / sqrt(1 + st), r0 * (1 + st)))
            } else {
                val dl = dt - dtLand
                val sink = easeOutCubic(clamp(dl / 0.2))
                if (sink < 1) {
                    val rr = r0 * (1 - sink)
                    drops.add(DropShape(x, target - r0 + sink * r0 * 0.6, rr * (1 + 0.6 * sink), rr))
                }
                val env = if (dl < 0.08) dl / 0.08 else exp(-(dl - 0.08) * 3.2)
                bumps.add(Bump(targetLayer, x, 6 * env * (if (e.layer == 0) 1.3 else 1.0)))
            }
        }
        return Frame(drips, drops, bumps, if (n > 0) sum / n else 0.0)
    }
}

/** Touch state, read by the frame loop; plain fields, so writing them never recomposes. */
class CheeseDripBurgerTouch {
    private val m = CheeseDripBurgerMotion

    var touching = false
        private set
    private var running = true
    private var base = 0.0
    private var pressClock = -100.0
    private var releaseClock = -100.0
    private var carry: List<DoubleArray> = m.layers.map { DoubleArray(it.drips.size) }
    private var released: List<DoubleArray> = m.layers.map { DoubleArray(it.drips.size) }
    private var rowCarry = DoubleArray(m.ROWS)
    private val events = ArrayList<CheeseDripBurgerMotion.Event>()
    private var nextDrop = 0.0
    private var dropCursor = 0
    var dropInClock = -100.0
        private set

    companion object {
        const val HEAT_MAX = 1.35
        const val IDLE = 2.0
        val HOLD_ORDER = listOf(0 to 2, 0 to 5, 0 to 6, 0 to 1, 0 to 4, 0 to 0, 0 to 7, 0 to 3)
    }

    /** Loop time, or null while the loop waits; it resumes at 0 two seconds after the last touch. */
    fun loopTime(clock: Double, autoPlay: Boolean): Double? {
        if (!autoPlay) return null
        if (!running) {
            if (touching || clock < releaseClock + IDLE) return null
            running = true
            base = releaseClock + IDLE
        }
        return m.mod(clock - base, m.LOOP)
    }

    private fun heat(clock: Double) = if (touching) HEAT_MAX * (1 - exp(-0.8 * (clock - pressClock))) else 0.0

    fun melts(clock: Double, t: Double?): List<DoubleArray> {
        val h = heat(clock)
        val decay = exp(-1.1 * max(0.0, clock - releaseClock))
        return m.layers.mapIndexed { li, layer ->
            DoubleArray(layer.drips.size) { di ->
                val loopM = if (t != null) m.meltLoop(t, m.delay(li, di)) else 0.0
                if (touching) max(carry[li][di], h) else max(loopM, released[li][di] * decay)
            }
        }
    }

    fun rowLit(clock: Double, t: Double?, i: Int): Double {
        val loopL = if (t != null) m.rowLit(t, i) else 0.0
        return max(loopL, rowCarry[i] * exp(-2.5 * max(0.0, clock - pressClock)))
    }

    fun activeEvents(clock: Double, t: Double?): List<Pair<CheeseDripBurgerMotion.Event, Double>> {
        val out = ArrayList<Pair<CheeseDripBurgerMotion.Event, Double>>()
        if (t != null) for (e in m.loopEvents) {
            val dt = t - e.t0
            if (dt > -m.PINCH_LEAD && dt < m.EVENT_TAIL) out.add(e to dt)
        }
        for (e in events) {
            val dt = clock - e.t0
            if (dt > -m.PINCH_LEAD && dt < m.EVENT_TAIL) out.add(e to dt)
        }
        return out
    }

    /** Schedules the next drop while the finger holds; true when one was just scheduled. */
    fun update(clock: Double): Boolean {
        events.removeAll { clock - it.t0 > m.EVENT_TAIL }
        if (!touching || clock - pressClock <= 0.9 || clock < nextDrop - m.PINCH_LEAD) return false
        val (l, d) = HOLD_ORDER[dropCursor % HOLD_ORDER.size]
        dropCursor += 1
        events.add(CheeseDripBurgerMotion.Event(l, d, nextDrop))
        nextDrop += 0.85
        return true
    }

    fun begin(clock: Double, autoPlay: Boolean) {
        if (touching) return
        val t = loopTime(clock, autoPlay)
        carry = melts(clock, t)
        rowCarry = DoubleArray(m.ROWS) { rowLit(clock, t, it) }
        if (t != null) for (e in m.loopEvents) {
            if (t - e.t0 > -m.PINCH_LEAD && t - e.t0 < m.EVENT_TAIL) {
                events.add(CheeseDripBurgerMotion.Event(e.layer, e.drip, clock + (e.t0 - t)))
            }
        }
        touching = true
        running = false
        pressClock = clock
        nextDrop = clock + 1.5
    }

    fun end(clock: Double) {
        if (!touching) return
        released = melts(clock, null)
        touching = false
        releaseClock = clock
    }

    fun dropIn(clock: Double) { dropInClock = clock }

    val restsAfter: Double
        get() = maxOf(releaseClock + 6, dropInClock + 2.6, (events.maxOfOrNull { it.t0 } ?: 0.0) + m.EVENT_TAIL)
}
