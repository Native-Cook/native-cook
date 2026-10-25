package com.ngocdevv.nativecook.blocks.pickers.paintmixerpicker

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 The bowl under the finger (a port of paintMixerSim.ts): the same paint model as the script,
 stepped by the frame loop. Drops fly from the pot (or straight down from the finger) and land;
 each landing kicks the swirl, whose angular speed decays (τ 1.1 s); stirring pulls the swirl
 towards the finger's angular speed. Winding (the total turned, either way) is what mixes the
 drops. 2 s after the last touch the bowl is washed back to white in 0.85 s and the script
 restarts from rest.
 */

private const val MAX_DROPS = 4
private const val SWIRL_DECAY = 1.1f
private const val LAND_KICK = 2.4f
private const val WASH_TIME = 0.85f
private const val PIF = PI.toFloat()
internal const val PAINT_EVENT_LAND = 1
internal const val PAINT_EVENT_GRAB = 2

private class SimDrop(val x: Float, val y: Float, var amt: Float, val a: FloatArray, val windLand: Float, val twistLand: Float)
private class SimFlight(val fx: Float, val fy: Float, val tx: Float, val ty: Float, val t0: Double, val dur: Float, val pot: Int, val into: Boolean)
private class SimRipple(val x: Float, val y: Float, val t0: Double, val str: Float)
private class SimSplash(val x: Float, val y: Float, val t0: Double, val pot: Int)
private class SimHeld(var x: Float, var y: Float, val pot: Int)

internal class PaintSim(private val reduced: Boolean) {
    private var baseA = PAINT_BASE_A.copyOf()
    private var baseW = 1f
    private val drops = ArrayList<SimDrop>()
    private var wind = 0f
    private var twist = 0f
    private var omega = 0f
    private var stirW = 0f
    private var stirring = false
    private val ripples = ArrayList<SimRipple>()
    private val splashes = ArrayList<SimSplash>()
    private val flights = ArrayList<SimFlight>()
    private var held: SimHeld? = null
    private val lift = FloatArray(5)
    /** Wash-back start time (−1: none). */
    private var wash = -1.0
    /** Amount scale left by a hand-over during the script's own wash. */
    private var fade = 1f
    var events = 0
    private val p = FloatArray(2)
    private val l = FloatArray(2)

    val holding get() = held != null
    val flying get() = flights.isNotEmpty()

    /** Takes the script's bowl at loop time t over (the same frame, now driven by the sim). */
    fun fromAuto(g: PaintGeo, t: Float, now: Double) {
        val m = paintAutoMix(g, t)
        val w = paintWindingAt(t)
        wind = w
        twist = if (t > 6.6f) w * (1 - paintEaseInOutSine(paintSeg(t, 6.6f, 8.4f))) else w
        val dt = 0.02f
        omega = if (t < 6.6f) (paintWindingAt(t + dt) - w) / dt else 0f
        drops.clear()
        flights.clear()
        held = null
        stirring = false
        baseA = PAINT_BASE_A.copyOf()
        baseW = 1f
        for ((pot, at) in arrayOf(PAINT_YELLOW to PAINT_LAND_Y, PAINT_TEAL to PAINT_LAND_T)) {
            if (t >= at) {
                paintLanding(g, pot, l)
                val d = m.drops[drops.size]
                drops.add(SimDrop(l[0], l[1], d.amt, PAINT_POT_A[pot], paintWindingAt(at), twist - d.tw))
            } else if (t >= at - 0.5f) {
                paintPotCenter(g, pot, p)
                paintLanding(g, pot, l)
                flights.add(SimFlight(p[0], p[1], l[0], l[1], now - (t - (at - 0.5f)), 0.5f, pot, true))
            }
        }
        ripples.clear()
        for (r in m.ripples) ripples.add(SimRipple(r.x, r.y, now - r.age, r.str))
        splashes.clear()
        for (s in m.splashes) splashes.add(SimSplash(s.x, s.y, now - s.age, s.pot))
        m.lift.copyInto(lift)
        fade = m.fade
        wash = -1.0
    }

    private fun addDrop(x: Float, y: Float, pot: Int, now: Double) {
        if (drops.size >= MAX_DROPS) {
            // The oldest drop is folded into the base paint.
            val o = drops.removeAt(0)
            val wgt = PAINT_K * o.amt * fade
            for (c in 0 until 3) baseA[c] = (baseA[c] * baseW + o.a[c] * wgt) / (baseW + wgt)
            baseW += wgt
        }
        drops.add(SimDrop(x, y, if (reduced) 0.001f else 0f, PAINT_POT_A[pot], wind, twist))
        if (!reduced) {
            omega += LAND_KICK
            ripples.add(SimRipple(x, y, now, 1f))
            if (ripples.size > 3) ripples.removeAt(0)
            splashes.add(SimSplash(x, y, now, pot))
        }
        events = events or PAINT_EVENT_LAND
    }

    fun step(now: Double, dt: Float) {
        // Flights land.
        for (i in flights.indices.reversed()) {
            val f = flights[i]
            if (now >= f.t0 + f.dur) {
                flights.removeAt(i)
                if (f.into) addDrop(f.tx, f.ty, f.pot, now)
            }
        }
        // Drops pour in; the swirl decays or follows the stirring finger.
        for (d in drops) d.amt += (1 - d.amt) * (1 - exp(-dt / (if (reduced) 0.25f else 0.13f)))
        if (stirring && !reduced) omega += (stirW - omega) * min(1f, dt * 8) else omega *= exp(-dt / SWIRL_DECAY)
        if (reduced) omega = 0f
        twist += omega * dt
        wind += abs(omega) * dt
        ripples.removeAll { now - it.t0 >= 1.6 }
        splashes.removeAll { now - it.t0 >= 0.45 }
        for (i in 0 until 5) {
            val target = if (held?.pot == i) 1f else 0f
            lift[i] += (target - lift[i]) * (1 - exp(-dt / 0.08f))
        }
        // Wash back to white, then empty.
        if (wash >= 0 && now - wash >= WASH_TIME) {
            drops.clear()
            baseA = PAINT_BASE_A.copyOf()
            baseW = 1f
            wind = 0f
            twist = 0f
            omega = 0f
            fade = 1f
            wash = -1.0
        }
    }

    /** Whether anything is still moving (otherwise the frame can be skipped). */
    fun busy(): Boolean {
        if (flights.isNotEmpty() || ripples.isNotEmpty() || splashes.isNotEmpty() || held != null || wash >= 0) return true
        if (abs(omega) > 0.01f) return true
        for (d in drops) if (d.amt < 0.995f) return true
        for (v in lift) if (v > 0.005f) return true
        return false
    }

    fun mix(g: PaintGeo, now: Double): PaintMix {
        var front = -1f
        var f = fade
        var back = 0f
        if (wash >= 0) {
            val s = paintSeg((now - wash).toFloat(), 0f, WASH_TIME)
            front = (g.paint + 46 * g.u) * paintEaseInOutSine(s)
            f *= 1 - paintEaseInOutCubic(s)
            back = -2.4f * PIF * paintEaseInOutSine(s)
        }
        val ds = drops.map { PaintDrop(it.x, it.y, it.amt, if (reduced) 1f else paintMixedShare(wind - it.windLand), twist - it.twistLand, it.a) }
        val fs = ArrayList<PaintFlight>(flights.size + 1)
        for (fl in flights) {
            val s = paintSeg((now - fl.t0).toFloat(), 0f, fl.dur.coerceAtLeast(1e-4f))
            val e = if (fl.into) paintEaseInOutSine(s) else 1 - (1 - s) * (1 - s) * (1 - s)
            fs.add(PaintFlight(fl.fx + (fl.tx - fl.fx) * e, fl.fy + (fl.ty - fl.fy) * e, if (fl.dur > 0.4f) sin(PIF * s) else 1 - s, fl.pot))
        }
        held?.let { fs.add(PaintFlight(it.x, it.y, 1f, it.pot)) }
        val rs = ripples.map { PaintRipple(it.x, it.y, (now - it.t0).toFloat(), it.str) }
        val ss = splashes.map { PaintSplash(it.x, it.y, (now - it.t0).toFloat(), it.pot) }
        return PaintMix(baseA.copyOf(), baseW, ds, rs, ss, fs, front, back, f, floatArrayOf(0f, 0f, 0f), lift.copyOf())
    }

    /** A finger down: on a pot it picks up a drop, in the bowl it stirs. */
    fun down(g: PaintGeo, x: Float, y: Float) {
        for (i in 0 until 5) {
            paintPotCenter(g, i, p)
            if (hypot(x - p[0], y - p[1]) < g.potR + 16) {
                held = SimHeld(x, y - 26 * g.u, i)
                events = events or PAINT_EVENT_GRAB
                return
            }
        }
        if (hypot(x - g.cx, y - g.cy) < g.rim) {
            stirring = true
            stirW = 0f
        }
    }

    /** A finger moving: carries the drop, or stirs at the finger's angular speed (rad/s). */
    fun move(g: PaintGeo, x: Float, y: Float, vx: Float, vy: Float) {
        val h = held
        if (h != null) {
            // The drop hangs a little above the fingertip so it stays visible.
            h.x = x
            h.y = y - 26 * g.u
            return
        }
        if (stirring) {
            val dx = x - g.cx
            val dy = y - g.cy
            val q = g.paint * 0.25f
            val r2 = max(dx * dx + dy * dy, q * q)
            stirW = max(-14f, min(14f, (dx * vy - dy * vx) / r2))
        }
    }

    /** A finger up: a drop over the paint falls in; anywhere else it flies back to its pot. */
    fun up(g: PaintGeo, now: Double) {
        stirring = false
        stirW = 0f
        val h = held ?: return
        held = null
        val r = hypot(h.x - g.cx, h.y - g.cy)
        if (r < g.paint) {
            flights.add(SimFlight(h.x, h.y, h.x, h.y, now, if (reduced) 0f else 0.22f, h.pot, true))
        } else {
            paintPotCenter(g, h.pot, p)
            flights.add(SimFlight(h.x, h.y, p[0], p[1], now, if (reduced) 0f else 0.3f, h.pot, false))
        }
    }

    fun startWash(now: Double) {
        if (drops.isEmpty() && baseW == 1f) return
        wash = now
    }
}
