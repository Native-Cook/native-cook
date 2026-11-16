package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Port of cpc-motion.ts: every motion number of the block and the pure functions of time built on them.

internal const val CpcLOOP = 8.0
internal const val CpcDT = 1.0 / 120

internal object CpcValve {
    const val rate = 20.0
    const val attack = 0.25
    const val release = 0.12
    const val throttleMin = 0.15
    const val throttleSlide = 120.0
    const val throttleLag = 0.08
}

internal object CpcScript {
    const val down1 = 0.5
    const val up1 = 1.55
    const val down2 = 1.75
    const val slideOn0 = 1.75
    const val slideOn1 = 1.95
    const val slideOff0 = 2.45
    const val slideOff1 = 2.8
    const val slideMax = 100.0
    const val end = 3.9
}

internal object CpcEmpty {
    const val rainDelay = 0.6
    const val pillAlpha = 0.55
}

internal object CpcRain {
    const val stagger = 0.062
    const val gravity = 2400.0
    const val spawnY = 46.0
    const val spawnSpread = 80.0
    const val glide = 0.7
    const val glideStagger = 0.02
    const val bounce = 0.34
    const val bounceZ = 9.0
}

internal const val CpcRESTORE_IDLE = 2.0

internal object CpcXCoin {
    const val turn = 4.0
    const val kickDelay = 0.08
    const val kickTurns = 2
    const val kickLength = 0.6
    const val restWindow = 0.7
    const val tilt = 0.16
}

internal object CpcPill {
    const val pressScale = 0.97
    const val bulge = 0.035
    const val bulgeResponse = 0.2
    const val bulgeZeta = 0.5
    const val lineMax = 0.6
    const val litTau = 0.14
    const val pressTau = 0.045
    const val alphaTau = 0.12
}

internal const val CpcROLL_TAU = 0.04

internal object CpcGesture {
    const val tapMax = 0.18
    const val fingerRadius = 22.0
    const val hitSlopPill = 8.0
    const val coinTick = 3
    const val hapticGap = 0.12
    const val impactMin = 200.0
    const val clickGap = 0.12
    const val tiltG = 0.25
}

internal fun cpcClamp01(v: Double): Double = if (v < 0) 0.0 else if (v > 1) 1.0 else v
internal fun cpcMix(a: Double, b: Double, k: Double): Double = a + (b - a) * k
internal fun cpcSmooth(a: Double, b: Double, v: Double): Double {
    val k = cpcClamp01((v - a) / (b - a))
    return k * k * (3 - 2 * k)
}
internal fun cpcEaseInOut(k: Double): Double {
    val x = cpcClamp01(k)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3.0) / 2
}

internal fun cpcSpringAt(t: Double, response: Double, zeta: Double): Double {
    if (t <= 0) return 0.0
    val w = 2 * PI / response
    if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
    val wd = w * sqrt(1 - zeta * zeta)
    return 1 - exp(-zeta * w * t) * (cos(wd * t) + (zeta * w / wd) * sin(wd * t))
}

internal fun cpcScriptAt(t: Double, out: DoubleArray, k: Int) {
    val held = (t >= CpcScript.down1 && t < CpcScript.up1) || (t >= CpcScript.down2 && t < CpcScript.end)
    val on = cpcSmooth(CpcScript.slideOn0, CpcScript.slideOn1, t)
    val off = cpcSmooth(CpcScript.slideOff0, CpcScript.slideOff1, t)
    out[k] = if (held) 1.0 else 0.0
    out[k + 1] = CpcScript.slideMax * on * (1 - off)
}

internal fun cpcXcoinAngle(t: Double, kickAt: Double): Double {
    val w = 2 * PI / CpcXCoin.turn
    var a = w * t
    if (kickAt >= 0) {
        val k = (t - kickAt - CpcXCoin.kickDelay) / CpcXCoin.kickLength
        if (k > 0) {
            val e = if (k >= 1) 1.0 else cpcEaseInOut(k)
            val over = if (k >= 1) 0.03 * exp(-(k - 1) * 9) * sin((k - 1) * 22) else 0.0
            a += 2 * PI * CpcXCoin.kickTurns * (e + over)
        }
    }
    return a
}

internal fun cpcXcoinEase(th0: Double, th1: Double, w: Double, s: Double): Double {
    val u = cpcClamp01(s)
    val tt = CpcXCoin.restWindow
    val h00 = 2 * u * u * u - 3 * u * u + 1
    val h10 = u * u * u - 2 * u * u + u
    val h01 = -2 * u * u * u + 3 * u * u
    return h00 * th0 + h10 * tt * w + h01 * th1
}

internal fun cpcXcoinTarget(th0: Double, w: Double): Double {
    val ahead = th0 + 0.5 * w * CpcXCoin.restWindow
    return 2 * PI * ceil(ahead / (2 * PI))
}
