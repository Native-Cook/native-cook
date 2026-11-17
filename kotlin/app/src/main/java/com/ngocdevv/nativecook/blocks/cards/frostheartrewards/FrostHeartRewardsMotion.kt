package com.ngocdevv.nativecook.blocks.cards.frostheartrewards

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Atelier timeline of fhr-motion.ts: every pose is sampled from seconds, so a frame and a loop agree. */
internal object FrostHeartRewardsMotion {
    const val LOOP = 10.0
    /** Only 25 and 50 are claimed by the demonstration; the other three remain touchable. */
    val CLAIMS = doubleArrayOf(1000.0, 1000.0, 1000.0, 0.6, 0.95)
    val VALUES = intArrayOf(10, 15, 20, 25, 50)
    const val HEART_X = 175.0
    const val HEART_Y = 278.0

    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = max(a, min(b, x))
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun smooth(a: Double, b: Double, x: Double): Double { val u = clamp((x - a) / (b - a)); return u * u * (3 - 2 * u) }

    /** Invert Bezier x with bounded bisection, as the reference does, so captures stay deterministic. */
    private fun bezier(x0: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp(x0)
        if (x == 0.0 || x == 1.0) return x
        var lo = 0.0; var hi = 1.0; var u = x
        repeat(12) {
            val v = 1 - u
            val bx = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
            if (bx < x) lo = u else hi = u
            u = (lo + hi) * 0.5
        }
        return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
    }
    fun ease(x: Double) = bezier(x, 0.77, 0.0, 0.175, 1.0)
    fun out(x: Double) = bezier(x, 0.23, 1.0, 0.32, 1.0)
    fun outCubic(x: Double): Double { val u = 1 - clamp(x); return 1 - u * u * u }
    fun inOutCubic(x: Double): Double { val u = clamp(x); return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).let { it * it * it } / 2 }
    fun hash(i: Double): Double { val n = sin(i * 127.1 + 311.7) * 43758.5453; return n - floor(n) }

    /** 0.82 damping ratio: one small magnetic overshoot, then a clean lock. */
    fun magnetic(age: Double): Double {
        if (age <= 0) return 1.0
        return exp(-11.5 * age) * (cos(8 * age) + 11.5 / 8 * sin(8 * age))
    }

    /** Per display piece spread of the shader's `spread(id)`: staggered 40 ms close after 5.4 s. */
    fun spread(t: Double, display: Double, group: Int): Double {
        if (t < 5.4) return display
        val age = max(0.0, t - 5.4 - group * 0.04)
        return exp(-14 * age) * (cos(10 * age) + 1.4 * sin(10 * age))
    }

    class Scene {
        var t = 0.0; var charge = 0.0; var gem = 0.0; var hue = 0.0; var flip = 0.0
        var reset = 0.0; var balance = 1650.0; var balanceFrom = 1650.0; var balanceTo = 1650.0
        var numberFade = 1.0; var remaining = 65.0; var level = 3; var spotlight = 0.0
        var cut = 0.0; var display = 0.0; var polish = 0.0; var polishX = 0.0
        var breath = 0.0; var breathX = 133.0; var breathY = 258.0; var core = 0.0
    }

    fun sceneAt(seconds: Double): Scene {
        val loop = ((seconds % LOOP) + LOOP) % LOOP
        val t = min(loop, 8.9)
        var balance = 1650.0
        for (i in 3 until 5) balance += VALUES[i] * out(seg(t - CLAIMS[i] - 0.65, 0.0, 0.1))
        val cut = ease(seg(t, 2.4, 3.6))
        val display = if (t < 5.4) out(seg(t, 3.9, 5.05)) else magnetic(t - 5.4)
        var core = 0.0
        for (i in 0 until 12) core += out(seg(t, 2.48 + i * 0.105, 2.60 + i * 0.105))
        return Scene().apply {
            this.t = t
            charge = clamp((balance - 1650) / 75)
            gem = cut
            hue = ease(seg(t, 6.4, 7.4))
            flip = ease(seg(t, 6.65, 7.25))
            reset = ease(seg(loop, 8.9, 10.0))
            this.balance = balance; balanceFrom = balance; balanceTo = balance
            remaining = max(0.0, 65 - (balance - 1650))
            level = if (t >= 6.95) 4 else 3
            spotlight = ease(seg(t, 1.7, 2.4))
            this.cut = cut
            this.display = if (t >= 3.9 && t < 6.4) display else 0.0
            breath = ease(seg(t, 1.75, 1.98)) * (1 - ease(seg(t, 2.06, 2.3)))
            this.core = core / 12
        }
    }

    /** True when the 24 sectors form one closed union (the shader's `assembled()`). */
    fun assembled(t: Double, display: Double) = display < 0.004 && (t <= 3.9 || t >= 6.35)

    const val TAU = PI * 2
}
