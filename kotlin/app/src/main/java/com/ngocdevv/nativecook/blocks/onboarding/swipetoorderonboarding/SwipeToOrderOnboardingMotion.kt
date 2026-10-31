package com.ngocdevv.nativecook.blocks.onboarding.swipetoorderonboarding

import kotlin.math.*

internal object SwipeToOrderOnboardingMotion {
    const val LOOP = 8.0
    val pile = arrayOf(doubleArrayOf(52.0, 70.0, -9.0, 1.06), doubleArrayOf(-36.0, 196.0, -13.0, 1.0), doubleArrayOf(18.0, 290.0, 8.0, 0.96))
    val listY = doubleArrayOf(60.0, 156.0, 252.0)
    data class Body(val p: DoubleArray, val v: DoubleArray = DoubleArray(4))
    class Frame {
        val cards = pile.map { Body(it.copyOf()) }
        var s = 0.0
        var t = 0.0
        var spin = 0.0
        var done = false
        var since = -1.0
    }
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun smooth(k: Double): Double { val x = clamp(k); return x * x * (3 - 2 * x) }
    fun ease(k: Double): Double { val x = clamp(k); return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2 }
    fun knob(t: Double): Double = when {
        t < 1.2 -> 0.0
        t < 2.6 -> 0.55 * ease((t - 1.2) / 1.4)
        t < 3.0 -> mix(0.55, 0.42, smooth((t - 2.6) / 0.4))
        t < 3.8 -> mix(0.42, 1.0, ease((t - 3.0) / 0.8))
        t < 5.9 -> 1.0
        t < 6.5 -> 1 - ease((t - 5.9) / 0.6)
        else -> 0.0
    }
    fun step(cards: List<Body>, s: Double, dt: Double, reduced: Boolean) {
        val k = if (reduced) 400.0 else 180.0
        val c = 2 * sqrt(k) * if (reduced) 1.0 else 0.62
        val n = max(1, ceil(dt / (1.0 / 240)).toInt())
        val h = dt / n
        cards.forEachIndexed { i, b ->
            val q = smooth((s - i * 0.12) / 0.76)
            val target = doubleArrayOf(0.0, listY[i], 0.0, 1.0)
            repeat(n) {
                repeat(4) { a ->
                    val aim = mix(pile[i][a], target[a], q)
                    b.v[a] += (k * (aim - b.p[a]) - c * b.v[a]) * h
                    b.p[a] += b.v[a] * h
                }
            }
        }
    }
}
