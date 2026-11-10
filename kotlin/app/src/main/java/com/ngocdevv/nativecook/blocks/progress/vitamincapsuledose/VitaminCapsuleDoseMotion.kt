package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import kotlin.math.*

/** Same ten-second script and easing functions as vcd-motion.ts. */
internal object VitaminCapsuleDoseMotion {
    const val LOOP = 10.0
    const val HOLD = 1.2
    const val MORPH = 0.9
    const val TAKE = 1.25
    const val REFORM = 0.6
    const val TILT = 0.785
    const val POUR_TILT = -0.4
    const val SEP_OFF = 1.5
    data class Frame(
        var a: Double = 0.0, var b: Double = 0.0, var k: Double = 0.0,
        var hold: Double = 0.0, var take: Double = 0.0, var reform: Double = 1.0,
        var yaw: Double = 0.0, var pitch: Double = 0.0, var bob: Double = 0.0,
        var sep: Double = 0.0, var twist: Double = 0.0, var tilt: Double = TILT,
        var hidden: Double = 0.0, var mode: Int = 0, var taken: Double = 0.0,
        var finger: Double = 0.0, var fingerAt: Int = 0, var fingerX: Double = 0.0,
        var press: Double = 0.0, var t: Double = 0.0,
    )
    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun io(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun out(x: Double) = 1 - (1 - x).pow(3)
    fun back(x: Double): Double { val u = x - 1; return 1 + 2.9 * u * u * u + 1.9 * u * u }
    fun idle(t: Double): Triple<Double, Double, Double> {
        val a = 2 * PI * t / LOOP
        return Triple(0.22 * sin(a), 0.1 * sin(2 * a + 0.7), 2.2 * sin(2 * a))
    }
    fun frameAt(time: Double): Frame {
        val t = ((time % LOOP) + LOOP) % LOOP
        val f = Frame(t = t)
        f.hold = if (t >= 5 && t < 6.2) seg(t, 5.0, 6.2) else 0.0
        f.taken = out(seg(t, 7.15, 7.35)) * (1 - io(seg(t, 8.65, 9.0)))
        val closing = io(seg(t, 7.4, 8.1))
        f.sep = (if (t >= 1.15) 0.55 + 0.95 * back(seg(t, 1.15, 1.5)) else 0.55 * io(seg(t, 0.45, 1.15))) * (1 - closing)
        f.twist = 1.1 * out(seg(t, 0.45, 1.5)) * (1 - closing)
        f.tilt = TILT + (POUR_TILT - TILT) * io(seg(t, 1.2, 1.7)) * (1 - io(seg(t, 7.2, 7.8)))
        f.hidden = if (t >= 1.3 && t < 8.1) 1.0 else 0.0
        f.mode = if (t < 1.3 || t >= 7.4) 0 else if (t < 6.2) 2 else 4
        val id = idle(t)
        f.yaw = id.first * (1 - f.hidden * 0.6); f.pitch = id.second - f.hold * 0.06; f.bob = id.third
        fun finger(a: Double, b: Double, at: Int, x: Double = 0.0) {
            val q = out(seg(t, a - 0.18, a - 0.02)) * (1 - out(seg(t, b, b + 0.16)))
            if (q > f.finger) { f.finger = q; f.fingerAt = at; f.fingerX = x; f.press = q }
        }
        finger(0.45, 1.15, 0, io(seg(t, 0.45, 1.15)))
        finger(3.9, 4.6, 3, io(seg(t, 3.9, 4.6)))
        val h = out(seg(t, 4.8, 4.97)) * (1 - out(seg(t, 6.2, 6.38)))
        if (h > f.finger) { f.finger = h; f.fingerAt = 1; f.press = h }
        val u = out(seg(t, 8.4, 8.56)) * (1 - out(seg(t, 8.74, 8.9)))
        if (u > f.finger) { f.finger = u; f.fingerAt = 2; f.fingerX = 1.0
            f.press = out(seg(t, 8.56, 8.65)) * (1 - out(seg(t, 8.7, 8.82))) }
        return f
    }
    fun hash(n: Double): Double { val v = sin(n * 91.3458) * 47453.5453; return v - floor(v) }
    /** CSS cubic-bezier, including the overshooting cap/reform curves. */
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        var lo = 0.0; var hi = 1.0
        repeat(18) { val q = (lo + hi) / 2; val u = 1 - q
            if (3 * u * u * q * x1 + 3 * u * q * q * x2 + q.pow(3) < x) lo = q else hi = q }
        val q = (lo + hi) / 2; val u = 1 - q
        return 3 * u * u * q * y1 + 3 * u * q * q * y2 + q.pow(3)
    }
}
