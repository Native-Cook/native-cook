package com.ngocdevv.nativecook.blocks.onboarding.coincardonboarding

import kotlin.math.*

internal object CoinCardOnboardingMotion {
    const val LOOP = 9.0
    val front = arrayOf(doubleArrayOf(18.0, 0.0, 0.0), doubleArrayOf(6.0, -4.0, 34.0), doubleArrayOf(-14.0, -6.0, 0.0), doubleArrayOf(-20.0, 14.0, 0.0), doubleArrayOf(10.0, 6.0, 46.0))
    val back = arrayOf(doubleArrayOf(-52.0, -74.0, 0.0), doubleArrayOf(40.0, 30.0, 56.0), doubleArrayOf(-21.0, -28.0, 0.0), doubleArrayOf(-24.0, 8.0, 0.0), doubleArrayOf(10.0, 4.0, 46.0))
    val euro = arrayOf(doubleArrayOf(64.0, -86.0, 74.0), doubleArrayOf(-74.0, -96.0, -34.0), doubleArrayOf(36.0, 34.0, 30.0), doubleArrayOf(0.0, 360.0, 720.0))
    val dollar = arrayOf(doubleArrayOf(22.0, 92.0, -70.0), doubleArrayOf(-18.0, -18.0, -26.0), doubleArrayOf(31.0, 30.0, 30.0), doubleArrayOf(0.0, -360.0, -720.0))
    val titles = arrayOf("The Card That\nWorks Anywhere", "Swap Between\nCoins in a Tap", "Your Keys,\nYour Money")
    val bodies = arrayOf("Create your account to trade, store,\nand grow your digital assets securely", "Move between 40 currencies at the\nrate you see, with no hidden spread", "Biometric approvals and cold storage\nkeep every coin in your hands")
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, x: Double) = a + (b - a) * x
    fun smooth(x: Double): Double { val q = clamp(x); return q * q * (3 - 2 * q) }
    fun script(t: Double) = if (t >= 6.8) 0.0 else if (t >= 4.2) 2.0 else if (t >= 1.6) 1.0 else 0.0
    fun key(k: DoubleArray, p: Double): Double {
        val q = p.coerceIn(0.0, 2.0)
        val i = min(1, floor(q).toInt())
        val base = mix(k[i], k[i + 1], smooth(q - i))
        return when { p < 0 -> base + (k[0] - k[1]) * 0.25 * -p; p > 2 -> base + (k[2] - k[1]) * 0.25 * (p - 2); else -> base }
    }
}

internal class CoinCardOnboardingDriver {
    var p = 0.0; var v = 0.0; var target = 0.0
    var drag = false; var finger = 0.0; var start = 0.0
    var time = 0.0; var clock = 0.0; var last = -10.0
    fun spring(dt: Double, k: Double) {
        val c = 2 * sqrt(k) * 0.92
        val n = max(1, ceil(dt / (1.0 / 240)).toInt())
        val h = dt / n
        repeat(n) { v += (k * (target - p) - c * v) * h; p += v * h }
    }
    fun step(dt: Double, auto: Boolean, reduced: Boolean) {
        clock += dt
        if (drag) { v = if (dt > 0) v * 0.6 + ((finger - p) / dt) * 0.4 else 0.0; p = finger }
        else if (auto && !reduced && clock - last >= 2) {
            time += dt
            if (time >= CoinCardOnboardingMotion.LOOP) time -= CoinCardOnboardingMotion.LOOP
            target = CoinCardOnboardingMotion.script(time)
            spring(dt, 60.0)
        } else spring(dt, if (reduced) 300.0 else 150.0)
    }
    fun begin() { last = clock }
    fun update(dx: Double, width: Double, starting: Boolean) {
        if (starting) { drag = true; start = p; finger = p }
        last = clock
        var f = start - dx / (width * 0.8)
        if (f < 0) f = -0.3 * tanh(-f / 0.3)
        if (f > 2) f = 2 + 0.3 * tanh((f - 2) / 0.3)
        finger = f
    }
    fun end(vx: Double, width: Double): Boolean {
        last = clock
        if (!drag) return false
        drag = false
        v = -vx / (width * 0.8)
        target = floor(p + v * 0.2 + 0.5).coerceIn(0.0, 2.0)
        return true
    }
    fun goTo(n: Int) { last = clock; target = n.coerceIn(0, 2).toDouble() }
    fun moving() = drag || abs(p - target) >= 1e-9 || abs(v) >= 1e-9
}
