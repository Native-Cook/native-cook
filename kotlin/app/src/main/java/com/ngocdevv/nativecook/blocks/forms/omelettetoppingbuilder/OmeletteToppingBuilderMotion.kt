package com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Omelette Topping Builder — the model, the seeded pieces and the autoPlay script
 (a port of OmeletteToppingBuilderMotion.swift).

 The screen is a pure function of a few events and the time: each topping is an add time (and, once
 lifted off, a remove time), the shelf is a chain of analytic springs. Every piece's flight, hop, spin
 and squash is computed from those times and its seeded layout, so the loop is exact and the three
 stacks match frame for frame.

 autoPlay: LOOP = 9 s
   0.0 – 1.2   rest: the plain omelette, Red Bell Pepper centred, $7.50
   1.2         add the pepper: it is tossed onto the omelette (lands at 1.82) and bursts into 22 cubes;
               the shelf slides to Baby Spinach at 2.1
   3.2         add the spinach (14 leaves), shelf → Smoked Bacon at 4.1
   5.2         add the bacon (7 strips), shelf → Cherry Tomato at 6.1
   7.4 – 7.64  the three toppings lift off and fade, last added first
   7.5         the shelf glides on two more items, back to the pepper
   8.6 – 9.0   rest (the frame at 9 is the frame at 0)
*/

internal class OtbSpring(val response: Double, val damping: Double)

/** One analytic spring segment. */
internal class OtbSegment(val t0: Double, val x0: Double, val v0: Double, val target: Double, val spring: OtbSpring) {
    fun eval(t: Double): Pair<Double, Double> {
        val tau = t - t0
        if (tau <= 0) return x0 to v0
        val z = min(spring.damping, 0.999)
        val w0 = 2 * PI / spring.response
        val wd = w0 * sqrt(1 - z * z)
        val a = x0 - target
        val b = (v0 + z * w0 * a) / wd
        val e = exp(-z * w0 * tau)
        // Settled: land exactly on the target, so a rest frame is the same frame every loop.
        if (e < 1e-4) return target to 0.0
        val c = cos(wd * tau)
        val s = sin(wd * tau)
        return (target + e * (a * c + b * s)) to (e * ((b * wd - z * w0 * a) * c - (a * wd + z * w0 * b) * s))
    }
}

/** A value driven by a chain of spring segments; a segment may start in the future (a delay). */
internal class OtbChannel(x: Double) {
    val segs = mutableListOf(OtbSegment(-1e9, x, 0.0, x, OtbSpring(1.0, 1.0)))

    fun copy(): OtbChannel = OtbChannel(0.0).also { it.segs.clear(); it.segs.addAll(segs) }

    fun eval(t: Double): Pair<Double, Double> {
        var seg = segs[0]
        for (s in segs) if (s.t0 <= t) seg = s
        return seg.eval(t)
    }

    /** Starts a spring towards [target] at time t; segments planned after t are dropped. */
    fun go(target: Double, t: Double, spring: OtbSpring) {
        val (x, v) = eval(t)
        segs.removeAll { it.t0 > t }
        segs.add(OtbSegment(t, x, v, target, spring))
        while (segs.size > 6) segs.removeAt(0)
    }

    /** Holds a value (and velocity) from time t. */
    fun set(x: Double, t: Double, v: Double = 0.0) {
        segs.removeAll { it.t0 > t }
        segs.add(OtbSegment(t, x, v, x, OtbSpring(1.0, 1.0)))
        while (segs.size > 6) segs.removeAt(0)
    }
}

/** One topping on the omelette: when it was added, where its flight started, when it was lifted off. */
internal data class OtbTopping(val kind: Int, val ta: Double, val sx: Double, var tr: Double? = null)

/** One seeded piece, in the omelette's own coordinates (dp, fold edge on y = 0, bulge towards +y). */
internal class OtbPiece(
    val x: Double, val y: Double, val rot: Double, val size: Double,
    val spin: Double, val delay: Double, val lift: Double, val phase: Double,
)

internal class OtbFrame(
    val t: Double = 0.0,
    val s: Double = 0.0,
    val toppings: List<OtbTopping> = emptyList(),
    val shakeAt: Double = -1e9,
    val shakeKind: Int = 0,
    val reduced: Boolean = false,
)

internal object OtbMotion {
    const val LOOP = 9.0

    // The shelf ring.
    const val KINDS = 5
    val names = listOf("Red Bell Pepper", "Baby Spinach", "Smoked Bacon", "Cherry Tomato", "Button Mushroom")
    val cents = listOf(80, 90, 160, 70, 90)
    const val BASE_CENTS = 750
    val counts = listOf(22, 14, 7, 12, 10)
    val minGap = listOf(17.0, 22.0, 30.0, 22.0, 24.0)
    /** Each kind's piece scale. */
    val pieceScale = listOf(1.0, 1.2, 1.1, 1.0, 1.0)

    // The omelette: a half disc of radius R, its fold edge turned to -40°, its centroid on the plate's centre.
    const val R = 110.0
    val ANGLE = -40.0 * PI / 180
    val CENTROID_Y = 4 * R / (3 * PI)

    // One add, from the tap.
    const val PRESS = 0.08
    const val LAND = 0.62
    const val COLLAPSE = 0.14
    const val ADVANCE_AT = 0.9
    const val RESTOCK_AT = 1.3
    const val RESTOCK_DUR = 0.35
    const val PRICE_DELAY = 0.1
    const val PRICE_ROLL = 0.35
    const val LIFT_DUR = 0.42
    const val LIFT_STAGGER = 0.12
    const val SHAKE_DUR = 0.4

    val shelfSpring = OtbSpring(0.55, 0.8)
    val rewindSpring = OtbSpring(0.8, 0.88)
    val snapSpring = OtbSpring(0.45, 0.82)
    val calm = OtbSpring(0.3, 1.0)

    // The script.
    val adds = listOf(1.2 to 0, 3.2 to 1, 5.2 to 2)
    const val CLEAR_AT = 7.4
    const val CLEAR_STEP = 0.12
    const val REWIND_AT = 7.5

    const val IDLE_BEFORE_BACK = 2.0
    const val SETTLE = 1.6

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun smooth(x: Double): Double { val c = clamp01(x); return c * c * (3 - 2 * c) }
    fun easeOut(x: Double): Double { val c = 1 - clamp01(x); return 1 - c * c * c }
    fun easeIn(x: Double): Double { val c = clamp01(x); return c * c * c }
    fun easeInOut(x: Double): Double {
        val c = clamp01(x)
        return if (c < 0.5) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2
    }
    fun easeOutBack(x: Double): Double {
        val c = clamp01(x)
        val k = 1.70158
        return 1 + (k + 1) * (c - 1).pow(3) + k * (c - 1).pow(2)
    }

    /** Rounds half away from zero, like Swift's rounded(). */
    fun round(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)

    /** The ring index of a shelf position. */
    fun wrap(i: Int) = ((i % KINDS) + KINDS) % KINDS

    /** Signed distance of item i from the shelf position s, in items, within (-2.5, 2.5]. */
    fun slot(i: Int, s: Double): Double {
        var d = (i - s) % KINDS
        if (d > 2.5) d -= KINDS
        if (d <= -2.5) d += KINDS
        return d
    }

    /** A 32-bit integer hash (lowbias32) in [0, 1): the same bits in Swift, Kotlin and JavaScript. */
    fun hash(n: Int): Double {
        var x = n
        x = x xor (x ushr 16)
        x *= 0x7FEB352D
        x = x xor (x ushr 15)
        x *= 0x846CA68B.toInt()
        x = x xor (x ushr 16)
        return (x.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }

    /** Where a kind's ingredient lands, in omelette coordinates. */
    fun landing(k: Int) = (hash(90000 + k * 2) - 0.5) * 24 to CENTROID_Y + (hash(90001 + k * 2) - 0.5) * 16

    /** The seeded spots of each kind's pieces on the omelette, spaced by a minimum gap. */
    val pieces: List<List<OtbPiece>> = (0 until KINDS).map { layout(it) }

    private fun layout(k: Int): List<OtbPiece> {
        val out = mutableListOf<OtbPiece>()
        var j = 0
        while (out.size < counts[k] && j < 2000) {
            val h1 = hash(k * 10000 + j * 4)
            val h2 = hash(k * 10000 + j * 4 + 1)
            j += 1
            val th = PI * (0.05 + 0.9 * h1)
            val rho = (R - 15) * sqrt(0.06 + 0.94 * h2)
            val x = cos(th) * rho
            val y = sin(th) * rho
            if (y < 10) continue
            val gap = if (j > 1200) 0.0 else minGap[k]
            if (out.any { hypot(it.x - x, it.y - y) < gap }) continue
            val i = out.size
            val b = 50000 + k * 1000 + i * 8
            out.add(
                OtbPiece(
                    x, y,
                    rot = hash(b) * 2 * PI,
                    size = 0.85 + 0.3 * hash(b + 1),
                    spin = (hash(b + 2) - 0.5) * 5,
                    delay = 0.02 * hash(b + 3),
                    lift = LIFT_STAGGER * hash(b + 4),
                    phase = hash(b + 5) * 2 * PI,
                ),
            )
        }
        return out
    }

    fun bezier(a: Double, c: Double, b: Double, u: Double) = (1 - u) * (1 - u) * a + 2 * (1 - u) * u * c + u * u * b

    /** Total in cents at time t. */
    fun total(f: OtbFrame, t: Double): Int {
        var c = BASE_CENTS
        for (tp in f.toppings) {
            val on = if (f.reduced) tp.ta else tp.ta + LAND + PRICE_DELAY
            val off = tp.tr?.let { t >= it } ?: false
            if (t >= on && !off) c += cents[tp.kind]
        }
        return c
    }

    /** The latest time at or before t when the total changed (or null). */
    fun lastChange(f: OtbFrame, t: Double): Double? {
        var last: Double? = null
        for (tp in f.toppings) {
            val on = if (f.reduced) tp.ta else tp.ta + LAND + PRICE_DELAY
            if (on <= t) last = max(last ?: on, on)
            val r = tp.tr
            if (r != null && r <= t && r > on) last = max(last ?: r, r)
        }
        return last
    }

    fun money(cents: Int) = "$" + (cents / 100) + "." + (cents % 100).toString().padStart(2, '0')
}

/** The toppings and the shelf of one session. */
internal class OtbSim {
    var shelf = OtbChannel(0.0)
    var drag: Double? = null
    val toppings = mutableListOf<OtbTopping>()
    var shakeAt = -1e9
    var shakeKind = 0
    var reduced = false

    fun shelfAt(t: Double) = drag ?: shelf.eval(t).first

    fun isOn(k: Int) = toppings.any { it.kind == k && it.tr == null }

    /** Adds kind k from the shelf's centre (offset sx); the shelf moves on to the next item. */
    fun add(k: Int, t: Double, sx: Double = 0.0) {
        toppings.removeAll { tp -> tp.tr?.let { t > it + OtbMotion.LIFT_DUR + OtbMotion.LIFT_STAGGER + 0.1 } ?: false }
        toppings.add(OtbTopping(k, t, sx))
        val s = shelfAt(t)
        if (reduced) shelf.go(OtbMotion.round(s) + 1, t + 0.4, OtbMotion.calm)
        else shelf.go(OtbMotion.round(s) + 1, t + OtbMotion.ADVANCE_AT, OtbMotion.shelfSpring)
    }

    /** A tap on the centred ingredient: adds it, or shakes it when it is already on. True when added. */
    fun tapCentre(t: Double): Boolean {
        val s = shelfAt(t)
        val k = OtbMotion.wrap(OtbMotion.round(s).roundToLong().toInt())
        if (isOn(k)) {
            shakeAt = t
            shakeKind = k
            return false
        }
        add(k, t, OtbMotion.slot(k, s))
        return true
    }

    /** Centres the item [d] slots away from the current one. */
    fun select(d: Int, t: Double) {
        val s = shelfAt(t)
        shelf.go(OtbMotion.round(s) + d, t, if (reduced) OtbMotion.calm else OtbMotion.snapSpring)
    }

    /** Lifts the last landed topping off. True when there was one. */
    fun removeLast(t: Double): Boolean {
        val i = toppings.indexOfLast { it.tr == null && (reduced || t >= it.ta + OtbMotion.LAND) }
        if (i < 0) return false
        toppings[i].tr = t
        return true
    }

    /** Lifts every topping off, last added first, [step] apart. */
    fun clearAll(t: Double, step: Double) {
        var n = 0
        for (i in toppings.indices.reversed()) {
            if (toppings[i].tr == null) {
                toppings[i].tr = t + step * n
                n += 1
            }
        }
    }

    /** Springs the shelf to the nearest place where the pepper is centred. */
    fun rewind(t: Double, spring: OtbSpring) {
        val s = shelfAt(t)
        val target = OtbMotion.round(s / OtbMotion.KINDS) * OtbMotion.KINDS
        shelf.go(target, t, if (reduced) OtbMotion.calm else spring)
    }

    fun beginDrag(t: Double) { drag = shelf.eval(t).first }

    fun dragTo(s: Double) { drag = s }

    /** Ends a drag: snaps to the nearest item along the finger's speed (items per second). */
    fun release(t: Double, velocity: Double) {
        val s = drag ?: shelf.eval(t).first
        drag = null
        shelf.set(s, t, velocity)
        val aim = s + 0.18 * velocity
        val target = min(OtbMotion.round(s) + 2, max(OtbMotion.round(s) - 2, OtbMotion.round(aim)))
        shelf.go(target, t, if (reduced) OtbMotion.calm else OtbMotion.snapSpring)
    }

    fun frame(t: Double) = OtbFrame(t, shelfAt(t), toppings.map { it.copy() }, shakeAt, shakeKind, reduced)
}

/** The autoPlay clock and the session a touch starts. */
internal class OtbDriver {
    private var origin: Double? = null
    var live: OtbSim? = null
        private set
    var lastTouch = -1e9
        private set
    private var returned = false
    var touching = false

    companion object {
        /** The scripted loop at [now], for the loop that started at [base]: only events that have happened. */
        fun scripted(base: Double, now: Double): OtbSim {
            val s = OtbSim()
            for ((at, kind) in OtbMotion.adds) if (now >= base + at) s.add(kind, base + at)
            if (now >= base + OtbMotion.CLEAR_AT) s.clearAll(base + OtbMotion.CLEAR_AT, OtbMotion.CLEAR_STEP)
            if (now >= base + OtbMotion.REWIND_AT) s.rewind(base + OtbMotion.REWIND_AT, OtbMotion.rewindSpring)
            return s
        }
    }

    fun frame(now: Double, auto: Boolean, reduced: Boolean): OtbFrame {
        if (origin == null) origin = now
        val l = live
        if (l != null) {
            l.reduced = reduced
            if (!auto || touching || now < lastTouch + OtbMotion.IDLE_BEFORE_BACK) return l.frame(now)
            if (!returned) {
                returned = true
                val at = lastTouch + OtbMotion.IDLE_BEFORE_BACK
                if (l.drag != null) l.release(at, 0.0)
                l.clearAll(at, 0.08)
                l.rewind(at, OtbMotion.rewindSpring)
            }
            if (now < lastTouch + OtbMotion.IDLE_BEFORE_BACK + OtbMotion.SETTLE) return l.frame(now)
            live = null
            origin = lastTouch + OtbMotion.IDLE_BEFORE_BACK + OtbMotion.SETTLE
        }
        if (!auto) {
            val s = OtbSim().also { it.reduced = reduced }
            live = s
            return s.frame(now)
        }
        val o = origin ?: now
        val base = o + floor((now - o) / OtbMotion.LOOP) * OtbMotion.LOOP
        return scripted(base, now).frame(now)
    }

    /** The session a touch acts on (the loop's current state when it was playing). */
    fun session(now: Double, auto: Boolean, reduced: Boolean): OtbSim {
        val o = origin
        val s = live ?: if (auto && o != null) {
            scripted(o + floor((now - o) / OtbMotion.LOOP) * OtbMotion.LOOP, now)
        } else {
            OtbSim()
        }
        live = s
        s.reduced = reduced
        returned = false
        return s
    }

    fun touched(now: Double) {
        lastTouch = now
        returned = false
    }
}
