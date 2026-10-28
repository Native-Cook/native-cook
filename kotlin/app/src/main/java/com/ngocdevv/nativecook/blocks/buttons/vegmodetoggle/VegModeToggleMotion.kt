package com.ngocdevv.nativecook.blocks.buttons.vegmodetoggle

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Veg Mode Toggle — the springs, the model and the autoPlay script (a port of VegModeToggleMotion.swift).

 Every moving value is an analytic spring chained on events: an event restarts the spring from the
 value and the velocity it has at that moment, towards a new target. The screen is a pure function of
 those springs and the time, so the loop is exact and the three stacks match frame for frame.

 Channels: p (knob, 0 = Veg with the knob on the right, 1 = Non veg with the knob on the left),
 b / b2 (broccoli, and its front floret and bulb 60 ms late), m / m2 (drumstick, and its egg),
 q (the dish list), press (the knob pressed in).

 autoPlay: LOOP = 7 s
   0.0 – 1.0   rest, Veg
   1.0         tap → Non veg: the knob slides, the broccoli sinks, the drumstick pops up and wobbles,
               the count rolls 24 → 38 and the rows swap
   2.2 – 3.5   hold, Non veg
   3.5 – 4.3   a finger drags the knob back to 0.3: the drumstick sinks, the broccoli peeks out
   4.3         release → Veg: the knob snaps, the broccoli pops, the list swaps back
   5.5 – 7.0   rest, Veg (the frame at 7 is the frame at 0)
*/

internal class VmtSpring(val response: Double, val damping: Double)

/** One analytic spring segment. */
internal class VmtSegment(val t0: Double, val x0: Double, val v0: Double, val target: Double, val spring: VmtSpring) {
    fun eval(t: Double): Pair<Double, Double> {
        val tau = t - t0
        if (tau <= 0) return x0 to v0
        val z = min(spring.damping, 0.999)
        val w0 = 2 * PI / spring.response
        val wd = w0 * sqrt(1 - z * z)
        val a = x0 - target
        val b = (v0 + z * w0 * a) / wd
        val e = exp(-z * w0 * tau)
        val c = cos(wd * tau)
        val s = sin(wd * tau)
        return (target + e * (a * c + b * s)) to (e * ((b * wd - z * w0 * a) * c - (a * wd + z * w0 * b) * s))
    }
}

/** A value driven by a chain of spring segments; a new one may start in the future (a delay). */
internal class VmtChannel(x: Double) {
    private val segs = ArrayList<VmtSegment>().apply { add(VmtSegment(-1e9, x, 0.0, x, VmtSpring(1.0, 1.0))) }

    private fun at(t: Double): VmtSegment {
        var seg = segs[0]
        for (s in segs) if (s.t0 <= t) seg = s
        return seg
    }

    fun eval(t: Double) = at(t).eval(t)

    /** The target of the segment running at time t. */
    fun target(t: Double) = at(t).target

    private fun push(seg: VmtSegment) {
        segs.removeAll { it.t0 > seg.t0 }
        segs.add(seg)
        while (segs.size > 6) segs.removeAt(0)
    }

    /** Starts a spring towards `target` at time t; segments planned after t are dropped. */
    fun go(target: Double, t: Double, spring: VmtSpring) {
        val (x, v) = eval(t)
        push(VmtSegment(t, x, v, target, spring))
    }

    /** Holds a value (and velocity) from time t. */
    fun set(x: Double, t: Double, v: Double = 0.0) {
        push(VmtSegment(t, x, v, x, VmtSpring(1.0, 1.0)))
    }
}

internal class VmtFrame(
    var p: Double = 0.0,
    var pv: Double = 0.0,
    var b: Double = 1.0,
    var bv: Double = 0.0,
    var b2: Double = 1.0,
    var m: Double = 0.0,
    var mv: Double = 0.0,
    var m2: Double = 0.0,
    var q: Double = 0.0,
    /** Where the list is heading (the rows stagger from the top either way). */
    var qTarget: Double = 0.0,
    var press: Double = 0.0,
    /** The broccoli (or the drumstick) is drawn over the knob, not under it. */
    var bFront: Boolean = true,
    var mFront: Boolean = false,
    var reduced: Boolean = false,
)

internal object VmtMotion {
    const val LOOP = 7.0

    val KNOB = VmtSpring(0.42, 0.78)
    val ENTER = VmtSpring(0.52, 0.52)
    val LEAVE = VmtSpring(0.24, 1.0)
    val LIST = VmtSpring(0.5, 0.9)
    val PRESS_IN = VmtSpring(0.12, 1.0)
    val PRESS_OUT = VmtSpring(0.3, 0.6)
    val CALM = VmtSpring(0.2, 1.0)

    const val ENTER_DELAY = 0.08
    const val PART_DELAY = 0.06
    const val LIST_DELAY = 0.05
    /** How fast the gap between the art and the knob's position closes while dragging. */
    const val DRAG_BLEND = 0.12

    const val IDLE_BEFORE_BACK = 2.0
    const val SETTLE = 1.6

    const val TAP_AT = 1.0
    const val DRAG_FROM = 3.5
    const val DRAG_TO = 4.3
    const val DRAG_END = 0.3

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun smooth(x: Double): Double { val c = clamp01(x); return c * c * (3 - 2 * c) }
    fun smooth(a: Double, b: Double, x: Double) = smooth((x - a) / (b - a))

    /** How far a dish's art is out while the knob sits at p (1 at its own end). */
    fun reach(p: Double) = smooth(0.2, 1.0, p)

    /** The knob's position during the scripted drag. */
    fun scriptP(t: Double) = 1 - (1 - DRAG_END) * smooth((t - DRAG_FROM) / (DRAG_TO - DRAG_FROM))
}

/** The springs of one toggle and a drag in progress. */
internal class VmtSim(var mode: Int = 0) {
    class Drag(val t0: Double, var p: Double, val db: Double, val db2: Double, val dm: Double, val dm2: Double)

    private val v0 = mode.toDouble()
    val p = VmtChannel(v0)
    val b = VmtChannel(1 - v0)
    val b2 = VmtChannel(1 - v0)
    val m = VmtChannel(v0)
    val m2 = VmtChannel(v0)
    val q = VmtChannel(v0)
    val press = VmtChannel(0.0)
    var drag: Drag? = null
    var reduced = false

    /** Commits a mode: the knob slides, the old art leaves, the new one pops up after `delay`. */
    fun flip(target: Int, t: Double, delay: Double = VmtMotion.ENTER_DELAY) {
        mode = target
        val veg = target == 0
        val to = target.toDouble()
        if (reduced) {
            p.go(to, t, VmtMotion.CALM)
            b.go(if (veg) 1.0 else 0.0, t, VmtMotion.CALM)
            b2.go(if (veg) 1.0 else 0.0, t, VmtMotion.CALM)
            m.go(if (veg) 0.0 else 1.0, t, VmtMotion.CALM)
            m2.go(if (veg) 0.0 else 1.0, t, VmtMotion.CALM)
            q.go(to, t, VmtMotion.CALM)
            return
        }
        p.go(to, t, VmtMotion.KNOB)
        if (veg) {
            m.go(0.0, t, VmtMotion.LEAVE)
            m2.go(0.0, t, VmtMotion.LEAVE)
            b.go(1.0, t + delay, VmtMotion.ENTER)
            b2.go(1.0, t + delay + VmtMotion.PART_DELAY, VmtMotion.ENTER)
        } else {
            b.go(0.0, t, VmtMotion.LEAVE)
            b2.go(0.0, t, VmtMotion.LEAVE)
            m.go(1.0, t + delay, VmtMotion.ENTER)
            m2.go(1.0, t + delay + VmtMotion.PART_DELAY, VmtMotion.ENTER)
        }
        q.go(to, t + VmtMotion.LIST_DELAY, VmtMotion.LIST)
    }

    fun tap(t: Double) {
        press.go(1.0, t, VmtMotion.PRESS_IN)
        press.go(0.0, t + 0.1, VmtMotion.PRESS_OUT)
        flip(1 - mode, t)
    }

    fun beginDrag(t: Double) {
        val f = frame(t)
        drag = Drag(
            t, f.p,
            f.b - VmtMotion.reach(1 - f.p), f.b2 - VmtMotion.reach(1 - f.p),
            f.m - VmtMotion.reach(f.p), f.m2 - VmtMotion.reach(f.p),
        )
        press.go(1.0, t, VmtMotion.PRESS_IN)
    }

    fun dragTo(p: Double) {
        drag?.p = p
    }

    /** Ends a drag: everything springs on from where it is. `target` null = the nearest end. */
    fun release(t: Double, velocity: Double, target: Int? = null, delay: Double = 0.0) {
        val f = frame(t)
        drag = null
        p.set(f.p, t, velocity)
        b.set(f.b, t)
        b2.set(f.b2, t)
        m.set(f.m, t)
        m2.set(f.m2, t)
        press.go(0.0, t, VmtMotion.PRESS_OUT)
        val to = target ?: if (f.p + 0.12 * velocity >= 0.5) 1 else 0
        flip(to, t, delay)
    }

    fun frame(t: Double): VmtFrame {
        val f = VmtFrame(reduced = reduced)
        f.q = q.eval(t).first
        f.qTarget = q.target(t)
        f.press = press.eval(t).first
        val d = drag
        if (d != null) {
            val k = exp(-max(0.0, t - d.t0) / VmtMotion.DRAG_BLEND)
            f.p = d.p
            f.b = VmtMotion.reach(1 - d.p) + d.db * k
            f.b2 = VmtMotion.reach(1 - d.p) + d.db2 * k
            f.m = VmtMotion.reach(d.p) + d.dm * k
            f.m2 = VmtMotion.reach(d.p) + d.dm2 * k
            f.bFront = d.p < 0.5
            f.mFront = !f.bFront
            return f
        }
        p.eval(t).let { f.p = it.first; f.pv = it.second }
        b.eval(t).let { f.b = it.first; f.bv = it.second }
        f.b2 = b2.eval(t).first
        m.eval(t).let { f.m = it.first; f.mv = it.second }
        f.m2 = m2.eval(t).first
        f.bFront = b.target(t) >= 0.5
        f.mFront = m.target(t) >= 0.5
        return f
    }
}

/** The autoPlay clock and the session a touch starts. */
internal class VmtDriver {
    private var origin: Double? = null
    var live: VmtSim? = null
        private set
    var lastTouch = -1e9
        private set
    private var returned = false
    var touching = false

    companion object {
        /** The scripted loop's springs at `now`, for the loop that started at `base`. */
        fun scripted(base: Double, now: Double): VmtSim {
            val s = VmtSim()
            // Only events that have happened: a touch takes over this state as it is.
            if (now >= base + VmtMotion.TAP_AT) s.tap(base + VmtMotion.TAP_AT)
            if (now >= base + VmtMotion.DRAG_FROM) {
                s.beginDrag(base + VmtMotion.DRAG_FROM)
                s.dragTo(VmtMotion.scriptP(min(now, base + VmtMotion.DRAG_TO) - base))
                if (now >= base + VmtMotion.DRAG_TO) s.release(base + VmtMotion.DRAG_TO, 0.0)
            }
            return s
        }
    }

    private fun base(o: Double, now: Double) = o + floor((now - o) / VmtMotion.LOOP) * VmtMotion.LOOP

    fun frame(now: Double, auto: Boolean, reduced: Boolean): VmtFrame {
        if (origin == null) origin = now
        val l = live
        if (l != null) {
            l.reduced = reduced
            if (!auto || touching || now < lastTouch + VmtMotion.IDLE_BEFORE_BACK) return l.frame(now)
            if (!returned) {
                returned = true
                val at = lastTouch + VmtMotion.IDLE_BEFORE_BACK
                if (l.drag != null) l.release(at, 0.0, target = 0)
                else if (l.mode != 0) l.flip(0, at, delay = 0.0)
            }
            if (now < lastTouch + VmtMotion.IDLE_BEFORE_BACK + VmtMotion.SETTLE) return l.frame(now)
            live = null
            origin = lastTouch + VmtMotion.IDLE_BEFORE_BACK + VmtMotion.SETTLE
        }
        if (!auto) {
            val s = VmtSim().also { it.reduced = reduced }
            live = s
            return s.frame(now)
        }
        return scripted(base(origin ?: now, now), now).frame(now)
    }

    /** The springs a touch acts on (the loop's current state when it was playing). */
    fun session(now: Double, auto: Boolean, reduced: Boolean): VmtSim {
        val s = live ?: run {
            val o = origin
            if (auto && o != null) scripted(base(o, now), now) else VmtSim()
        }
        live = s
        s.reduced = reduced
        touched(now)
        return s
    }

    fun touched(now: Double) {
        lastTouch = now
        returned = false
    }
}
