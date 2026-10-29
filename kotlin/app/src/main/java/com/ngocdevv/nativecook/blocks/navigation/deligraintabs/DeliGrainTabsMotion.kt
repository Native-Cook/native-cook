package com.ngocdevv.nativecook.blocks.navigation.deligraintabs

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Timing and touch model of Deli Grain Tabs. In the loop every value is a pure function of the
 * loop time, so the loop's last frame equals its first.
 */
object DeliGrainTabsMotion {
    const val LOOP = 9.6
    const val HOLD = 0.9 // rest before each sift
    const val SIFT = 1.5 // one shape pouring into the next
    val ORDER = intArrayOf(0, 2, 6, 3) // Balsamico, Flour & Pizza, Pasta & Risotto, Sweets & Coffee
    const val RESUME_AFTER = 2.0

    /** A sample of the finger's trail; each pushes the grains near it, then lets them settle back. */
    data class Stir(val x: Double, val y: Double, val vx: Double, val vy: Double, val at: Double, val live: Boolean = false)

    /** What the art needs for one frame. */
    data class Frame(
        /** Shape the grains leave (−1: from [buffer], wherever they were when a tab was tapped). */
        val from: Int = 0,
        val to: Int = 0,
        /** The tab selected before (for the tab colours, the indicator and the label). */
        val fromTab: Int = 0,
        /** Seconds into the sift; at [SIFT] or later the grains rest on [to]. */
        val p: Double = SIFT,
        val buffer: DeliGrainTabsBuffer? = null,
        val stirs: List<Stir> = emptyList(),
        val clock: Double = 0.0,
    ) {
        val resting: Boolean get() = p >= SIFT
    }

    // Curves

    fun mod(a: Double, b: Double): Double {
        val r = a % b
        return if (r < 0) r + b else r
    }

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(max(v, lo), hi)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    // The loop (P = 9.6 s): four segments of 0.9 s rest + 1.5 s sift

    fun loopFrame(t: Double): Frame {
        val segment = HOLD + SIFT
        val k = min(ORDER.size - 1, (t / segment).toInt())
        val tau = t - k * segment
        val a = ORDER[k]
        val b = ORDER[(k + 1) % ORDER.size]
        if (tau < HOLD) return Frame(from = a, to = a, fromTab = a, p = SIFT)
        return Frame(from = a, to = b, fromTab = a, p = tau - HOLD)
    }

    // Grains

    /** Per-grain constants, seeded: six per grain. */
    val seeds: DoubleArray = DoubleArray(DeliGrainTabsArt.GRAIN_COUNT * 6).also { s ->
        for (i in 0 until DeliGrainTabsArt.GRAIN_COUNT) for (k in 1..6) {
            s[i * 6 + k - 1] = DeliGrainTabsArt.hash(i * 1.733 + k * 9.17)
        }
    }

    /**
     * Where grain [i] is (points) into [out] (x, y, flight share: 0 at rest, 1 mid-air).
     */
    fun grain(
        i: Int, sx: Double, sy: Double, rFrom: Double, tx: Double, ty: Double, rTo: Double, p: Double,
        cx: Double, cy: Double, out: DoubleArray,
    ) {
        if (p >= SIFT) { out[0] = tx; out[1] = ty; out[2] = 0.0; return }
        val o = i * 6
        val h0 = seeds[o]; val h1 = seeds[o + 1]; val h2 = seeds[o + 2]
        val h3 = seeds[o + 3]; val h4 = seeds[o + 4]; val h5 = seeds[o + 5]
        val start = 0.36 * (1 - rFrom) + 0.10 * h0
        val land = 1.0 + 0.40 * (1 - rTo) + 0.08 * h1
        val u = clamp((p - start) / (land - start))
        if (u <= 0) { out[0] = sx; out[1] = sy; out[2] = 0.0; return }
        if (u >= 1) { out[0] = tx; out[1] = ty; out[2] = 0.0; return }
        val e = easeInOutCubic(u)
        // Falls out of the old shape, swings round and rains onto the new one.
        val c1x = sx + 50 * (2 * h2 - 1)
        val c1y = sy + 150 + 50 * h3
        val c2x = tx + 40 * (2 * h4 - 1)
        val c2y = ty - 160 - 60 * h5
        val v = 1 - e
        var x = v * v * v * sx + 3 * v * v * e * c1x + 3 * v * e * e * c2x + e * e * e * tx
        var y = v * v * v * sy + 3 * v * v * e * c1y + 3 * v * e * e * c2y + e * e * e * ty
        val lift = sin(PI * e)
        val angle = lift * (0.55 + 0.7 * h5)
        val dx = x - cx
        val dy = y - cy
        x = cx + dx * cos(angle) - dy * sin(angle)
        y = cy + dx * sin(angle) + dy * cos(angle)
        x += 10 * lift * sin(2 * PI * (2 * e + h2))
        y += 7 * lift * cos(2 * PI * (2 * e + h3))
        out[0] = x; out[1] = y; out[2] = sin(PI * u)
    }

    /**
     * How the finger's trail pushes a grain at (x, y): away from each sample and along its motion,
     * fading with a springy settle; capped softly at 34 pt. Adds to [out] (0, 1).
     */
    fun stirOffset(x: Double, y: Double, stirs: List<Stir>, clock: Double, out: DoubleArray) {
        var ox = 0.0
        var oy = 0.0
        for (s in stirs) {
            val env = if (s.live) 1.0 else {
                val a = clock - s.at
                if (a > 1.6) continue
                exp(-3.2 * a) * cos(4.5 * a)
            }
            val dx = x - s.x
            val dy = y - s.y
            val d2 = dx * dx + dy * dy
            if (d2 > 8100) continue
            val w = exp(-d2 / 1800) * env
            val d = sqrt(d2) + 0.5
            ox += dx / d * 26 * w + clamp(s.vx * 0.05, -30.0, 30.0) * w
            oy += dy / d * 26 * w + clamp(s.vy * 0.05, -30.0, 30.0) * w
        }
        val m = sqrt(ox * ox + oy * oy)
        if (m < 0.01) return
        val capped = 34 * tanh(m / 34)
        out[0] += ox / m * capped
        out[1] += oy / m * capped
    }

    /** 0 when no stir is near, 1 while the finger is down; drives the solid fill's fade. */
    fun stirEnergy(stirs: List<Stir>, clock: Double): Double {
        var e = 0.0
        for (s in stirs) e = max(e, if (s.live) 1.0 else clamp(exp(-3.2 * (clock - s.at)) * 1.6))
        return clamp(e)
    }

    /** Fills [xs], [ys] and [fly] with every grain's position (points) and flight share. */
    fun positions(f: Frame, l: DeliGrainTabsArt.Layout, xs: DoubleArray, ys: DoubleArray, fly: DoubleArray) {
        val target = DeliGrainTabsArt.grains(f.to)
        val source = if (f.from >= 0) DeliGrainTabsArt.grains(f.from) else null
        val cx = l.swirlX
        val cy = l.swirlY
        val out = DoubleArray(3)
        val push = DoubleArray(2)
        for (i in 0 until DeliGrainTabsArt.GRAIN_COUNT) {
            val tx = l.heroX(target.x[i])
            val ty = l.heroY(target.y[i])
            var px = tx
            var py = ty
            var share = 0.0
            if (!f.resting) {
                val buffer = f.buffer
                val sx: Double
                val sy: Double
                val rFrom: Double
                if (source != null) {
                    sx = l.heroX(source.x[i]); sy = l.heroY(source.y[i]); rFrom = source.r[i]
                } else if (buffer != null) {
                    sx = buffer.x[i]; sy = buffer.y[i]; rFrom = buffer.r[i]
                } else {
                    sx = tx; sy = ty; rFrom = target.r[i]
                }
                grain(i, sx, sy, rFrom, tx, ty, target.r[i], f.p, cx, cy, out)
                px = out[0]; py = out[1]; share = out[2]
            }
            if (f.stirs.isNotEmpty()) {
                push[0] = 0.0; push[1] = 0.0
                stirOffset(px, py, f.stirs, f.clock, push)
                px += push[0]; py += push[1]
            }
            xs[i] = px
            ys[i] = py
            fly[i] = share
        }
    }
}

/** Grain positions captured when a tab is tapped mid-flight (points, plus each grain's height share). */
class DeliGrainTabsBuffer(val x: DoubleArray, val y: DoubleArray) {
    val r: DoubleArray

    init {
        val top = y.minOrNull() ?: 0.0
        val span = max((y.maxOrNull() ?: 1.0) - top, 1.0)
        r = DoubleArray(y.size) { (y[it] - top) / span }
    }
}

/**
 * Touch state: taps sift to a tab, drags stir the grains; 2 s after the last touch the grains sift
 * back to Balsamico and the loop restarts.
 */
class DeliGrainTabsTouch {
    private val m = DeliGrainTabsMotion

    private var manual = false
    private var from = 0
    private var to = 0
    private var fromTab = 0
    private var start = -100.0
    private var buffer: DeliGrainTabsBuffer? = null
    private var loopOrigin = 0.0
    private var lastTouch = -100.0
    private val trail = ArrayList<DeliGrainTabsMotion.Stir>()
    private var finger: DeliGrainTabsMotion.Stir? = null
    var touching = false
        private set

    /** The clock time after which nothing moves any more (for a block that does not autoplay). */
    val restsAfter: Double get() = max(start + DeliGrainTabsMotion.SIFT, lastTouch + 1.6)

    fun frame(clock: Double, autoPlay: Boolean): DeliGrainTabsMotion.Frame {
        if (manual && autoPlay && !touching && clock > lastTouch + m.RESUME_AFTER && clock >= start + m.SIFT) {
            if (to != m.ORDER[0]) {
                // Sift back to the loop's first shape, then hand over to the loop.
                from = to
                fromTab = to
                to = m.ORDER[0]
                buffer = null
                start = clock
            } else {
                manual = false
                loopOrigin = clock
                trail.clear()
            }
        }
        val base = if (manual || !autoPlay) {
            DeliGrainTabsMotion.Frame(from = from, to = to, fromTab = fromTab, p = clock - start, buffer = buffer)
        } else {
            m.loopFrame(m.mod(clock - loopOrigin, m.LOOP))
        }
        trail.removeAll { clock - it.at > 1.6 }
        val stirs = ArrayList(trail)
        finger?.let { live ->
            // A finger that stops stops dragging the grains along.
            val calm = exp(-8 * max(0.0, clock - live.at))
            stirs.add(live.copy(vx = live.vx * calm, vy = live.vy * calm))
        }
        return base.copy(stirs = stirs, clock = clock)
    }

    /** Freezes the loop where it is so a touch can take over. */
    private fun takeOver(clock: Double, autoPlay: Boolean) {
        if (manual || !autoPlay) return
        val f = m.loopFrame(m.mod(clock - loopOrigin, m.LOOP))
        manual = true
        from = f.from
        to = f.to
        fromTab = f.fromTab
        buffer = null
        start = clock - min(f.p, m.SIFT)
    }

    fun tap(tab: Int, clock: Double, autoPlay: Boolean, still: Boolean, layout: DeliGrainTabsArt.Layout) {
        takeOver(clock, autoPlay)
        manual = true
        lastTouch = clock
        val current = frame(clock, autoPlay)
        if (tab == to && current.resting) return
        if (still) {
            // Reduced motion: a cross-fade from the shape on screen.
            from = to
            fromTab = to
            to = tab
            buffer = null
            start = clock
            return
        }
        val n = DeliGrainTabsArt.GRAIN_COUNT
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        val fly = DoubleArray(n)
        m.positions(current, layout, xs, ys, fly)
        buffer = DeliGrainTabsBuffer(xs, ys)
        from = -1
        fromTab = to
        to = tab
        start = clock
        // The pushes are baked into the buffer.
        trail.clear()
    }

    fun begin(x: Double, y: Double, clock: Double, autoPlay: Boolean) {
        takeOver(clock, autoPlay)
        manual = true
        touching = true
        lastTouch = clock
        finger = DeliGrainTabsMotion.Stir(x, y, 0.0, 0.0, clock, live = true)
    }

    fun move(x: Double, y: Double, clock: Double) {
        val f = finger ?: return
        val dt = max(clock - f.at, 1.0 / 120)
        val dx = x - f.x
        val dy = y - f.y
        if (dx * dx + dy * dy >= 36) {
            trail.add(DeliGrainTabsMotion.Stir(f.x, f.y, f.vx, f.vy, clock))
            while (trail.size > 10) trail.removeAt(0)
            finger = DeliGrainTabsMotion.Stir(
                x, y, f.vx * 0.4 + dx / dt * 0.6, f.vy * 0.4 + dy / dt * 0.6, clock, live = true,
            )
        }
        lastTouch = clock
    }

    fun end(clock: Double) {
        finger?.let { trail.add(DeliGrainTabsMotion.Stir(it.x, it.y, it.vx, it.vy, clock)) }
        finger = null
        touching = false
        lastTouch = clock
    }
}
