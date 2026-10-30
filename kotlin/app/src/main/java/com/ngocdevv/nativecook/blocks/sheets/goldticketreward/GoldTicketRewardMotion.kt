package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.A
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.B
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.Box


/** Analytic RN timeline, including its spring response and coin gravity. */
internal object GoldTicketRewardMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val PEAK = 3.25
    const val COIN_FRAMES = 24
    const val SCRUB_DIST = 210.0
    const val SCRUB_END = 2.45
    const val COMMIT = 0.4

    object T {
        val press = doubleArrayOf(1.45, 1.57)
        val commit = 1.57
        val carsOut = doubleArrayOf(1.58, 2.12)
        val glowOut = doubleArrayOf(1.6, 2.3)
        val thanksOut = doubleArrayOf(1.6, 1.84)
        val callbackOut = doubleArrayOf(1.64, 1.88)
        val dividerOut = doubleArrayOf(1.68, 1.9)
        val dim = doubleArrayOf(1.66, 2.36)
        val ink = doubleArrayOf(1.66, 2.45)
        val morph = 1.6
        val halo = doubleArrayOf(1.68, 1.95)
        val gold = doubleArrayOf(1.86, 2.42)
        val coinUp = doubleArrayOf(1.72, 2.3)
        val coinDrop = doubleArrayOf(2.3, 2.6)
        val coinSpin = doubleArrayOf(1.72, 2.6)
        val land = 2.6
        val squash = doubleArrayOf(2.6, 2.95)
        val ring = doubleArrayOf(2.6, 3.05)
        val flash = doubleArrayOf(2.6, 2.95)
        val burst = doubleArrayOf(2.6, 3.15)
        val third = doubleArrayOf(1.76, 1.96)
        val benefits = doubleArrayOf(1.8, 2.3)
        val button = doubleArrayOf(1.84, 2.32)
        val closeIn = doubleArrayOf(2.55, 2.85)
        val sweep = doubleArrayOf(3.7, 4.5)
        val glint = doubleArrayOf(4.35, 4.85)
        val ctaGlow = doubleArrayOf(4.9, 5.7)
        val carsGlint = doubleArrayOf(0.35, 1.15)
        val anticipation = doubleArrayOf(1.6, 1.72)
        val relief = doubleArrayOf(1.72, 1.95)
        val hold = doubleArrayOf(3.15, 6.7)
        val closeOut = doubleArrayOf(6.7, 6.84)
        val burstBack = doubleArrayOf(6.7, 6.98)
        val coinLift = doubleArrayOf(6.78, 7.05)
        val coinReturn = doubleArrayOf(7.05, 7.45)
        val coinBackSpin = doubleArrayOf(6.8, 7.45)
        val goldOut = doubleArrayOf(6.9, 7.32)
        val morphBack = doubleArrayOf(6.95, 7.55)
        val inkBack = doubleArrayOf(6.92, 7.6)
        val dimBack = doubleArrayOf(6.95, 7.6)
        val buttonBack = doubleArrayOf(7.0, 7.45)
        val benefitsBack = doubleArrayOf(7.0, 7.45)
        val haloIn = doubleArrayOf(7.3, 7.55)
        val glowIn = doubleArrayOf(7.15, 7.7)
        val carsIn = doubleArrayOf(7.2, 7.72)
        val thirdIn = doubleArrayOf(7.38, 7.6)
        val thanksIn = doubleArrayOf(7.42, 7.7)
        val callbackIn = doubleArrayOf(7.46, 7.74)
        val dividerIn = doubleArrayOf(7.5, 7.76)
    }

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p

    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(5)

    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 16 * p.pow(5) else 1 - (-2 * p + 2).pow(5) / 2
    }

    fun easeOutCubic(x: Double) = 1 - (1 - clamp01(x)).pow(3)

    fun easeOutBack(x: Double, k: Double = 1.4): Double {
        val p = clamp01(x) - 1
        return 1 + (k + 1) * p * p * p + k * p * p
    }

    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
    }

    private fun wobble(tau: Double,
        period: Double,
        decay: Double) = if (tau <= 0) 0.0 else exp(-tau / decay) * cos(2 * PI * tau / period)

    data class Fade(val a: Double, val dy: Double)

    data class Coin(val x: Double, val y: Double, val r: Double, val frame: Double, val shadow: Double)

    data class Star(val x: Double, val y: Double, val s: Double, val a: Double, val rot: Double)

    data class Close(val a: Double, val s: Double, val rot: Double)

    data class Frame(val t: Double,
        val dim: Double,
        val glow: Double,
        val cars: Double,
        val thanks: Fade,
        val callback: Fade,
        val divider: Double,
        val ink: Double,
        val ticket: Box,
        val notch: Double,
        val tScaleX: Double,
        val tScaleY: Double,
        val tDy: Double,
        val gold: Double,
        val sweep: Double,
        val lift: Double,
        val halo: Double,
        val coin: Coin,
        val glint: Double,
        val ringR: Double,
        val ringA: Double,
        val flash: Double,
        val stars: List<Star> ,
        val starsA: Double,
        val benefits: Double,
        val benefits2: Double,
        val third: Double,
        val button: Double,
        val close: Close,
        val bAmount: Double,
        val aura: Double,
        val ctaGlow: Double,
        val carsGlint: Double)

    fun frameAt(t: Double, extraSpin: Double = 0.0, hop: Double = 0.0, twinkle: Double = 0.0): Frame {
        val fwd = t < 5

        fun s(a: DoubleArray) = seg(t, a[0], a[1])

        fun outIn(o: DoubleArray, i: DoubleArray) = if (fwd) 1 - s(o) else s(i)
        val morphP = if (fwd) spring(t - T.morph, 0.55, 0.74) else 1 - easeInOut(s(T.morphBack))
        val press = 1 - 0.03 * easeOut(s(T.press)) * (1 - spring(t - T.commit, 0.35, 0.8))
        val land = t - T.land
        val squash = if (fwd && land > 0) 0.035 * wobble(land, 0.24, 0.12) else 0.0
        val ticket = Box(mix(A.ticket.x,
            B.ticket.x,
            morphP),
            mix(A.ticket.y,
            B.ticket.y,
            morphP),
            mix(A.ticket.w,
            B.ticket.w,
            morphP),
            mix(A.ticket.h,
            B.ticket.h,
            morphP))
        val upP = easeOutCubic(s(T.coinUp))
        val dropP = s(T.coinDrop)
        var cx: Double
        var cy: Double
        var cr: Double
        var frame: Double
        if (fwd) {
            if (t < T.coinDrop[0]) {
                cx = mix(A.coin.x, B.coin.x, upP)
                cy = A.coin.y - 78 * upP
                cr = A.coin.r * mix(1.0, 1.35, upP)
            } else {
                val g = dropP * dropP
                cx = B.coin.x
                cy = mix(A.coin.y - 78, B.coin.y, g)
                cr = mix(A.coin.r * 1.35, B.coin.r, g)
            }
            cy += 2.6 * sin(PI * s(T.anticipation))
            if (land > 0) cy -= 3.2 * max(0.0, sin(min(land / 0.22, 1.0) * PI)) * exp(-land * 4)
            frame = COIN_FRAMES * 4 * easeInOut(s(T.coinSpin))
        } else {
            val liftP = easeOut(s(T.coinLift))
            val retP = easeInOut(s(T.coinReturn))
            cx = mix(B.coin.x, A.coin.x, retP)
            cy = mix(B.coin.y - 26 * liftP, A.coin.y, retP)
            cr = mix(B.coin.r, A.coin.r, retP)
            frame = COIN_FRAMES * 2 * easeInOut(s(T.coinBackSpin))
        }
        val bAmount = if (fwd) seg(t, T.hold[0] - 0.3, T.hold[0]) else 1 - seg(t, T.hold[1], T.hold[1] + 0.3)
        cy += bAmount * 1.6 * sin(2 * PI * 4 * t / LOOP) - hop
        frame += extraSpin
        val air = if (fwd) {
            if (t < T.coinDrop[0]) upP else 1 - dropP * dropP
        } else easeOut(s(T.coinLift)) * (1 - seg(t, T.coinReturn[1] - 0.12, T.coinReturn[1]))
        val stars = B.sparkles.mapIndexed {
            i, sp ->
            val tw = 0.8 + 0.2 * (0.5 + 0.5 * sin(2 * PI * (sp.k * t / LOOP + sp.ph)))
            if (fwd) {
                val p = seg(t, T.burst[0] + i * 0.025, T.burst[1] + i * 0.025)
                val q = easeOutBack(p, 1.1)
                Star(mix(B.coin.x,
                    sp.x,
                    q),
                    mix(B.coin.y,
                    sp.y,
                    q),
                    sp.s * mix(0.25,
                    1.0,
                    easeOut(p)) * tw * (1 + 0.35 * twinkle * sin(PI * twinkle)),
                    clamp01(p * 6),
                    (1 - easeOut(p)) * 1.6)
            } else {
                val p = easeInOut(s(T.burstBack))
                Star(mix(sp.x, B.coin.x, p * 0.35), mix(sp.y, B.coin.y, p * 0.35), sp.s * tw * (1 - 0.7 * p), 1 - p, p * 0.8)
            }
        }
        val thanksA = if (fwd) 1 - easeOutCubic(s(T.thanksOut)) else easeOut(s(T.thanksIn))
        val callbackA = if (fwd) 1 - easeOutCubic(s(T.callbackOut)) else easeOut(s(T.callbackIn))
        val closeP = if (fwd) easeOut(s(T.closeIn)) else 1 - s(T.closeOut)
        val ringP = s(T.ring)
        val flashP = s(T.flash)
        val sweepP = s(T.sweep)
        val pour = if (fwd) easeInOut(s(T.gold)) else 1 - easeInOut(s(T.goldOut))
        val flare = if (fwd && land > 0) 0.32 * exp(-land / 0.28) * min(1.0, land / 0.04) else 0.0
        return Frame(t,
            if (fwd) easeInOut(s(T.dim)) else 1 - easeInOut(s(T.dimBack)),
            if (fwd) 1 - easeInOut(s(T.glowOut)) else easeInOut(s(T.glowIn)),
            if (fwd) s(T.carsOut) else 1 - s(T.carsIn),
            Fade(thanksA,
            - 10 * (1 - thanksA)),
            Fade(callbackA,
            - 10 * (1 - callbackA)),
            outIn(T.dividerOut,
            T.dividerIn),
            if (fwd) easeInOut(s(T.ink)) else 1 - easeInOut(s(T.inkBack)),
            ticket,
            mix(A.notchR,
            B.notchR,
            morphP),
            press * (1 + squash * 0.6),
            press * (1 - squash),
            squash * 40 - (if (fwd) 3 * easeOut(s(T.relief)) * (1 - seg(t,
            T.coinDrop[1] - 0.1,
            T.coinDrop[1])) else 0.0),
            pour,
            if (sweepP > 0 && sweepP < 1) easeInOut(sweepP) else - 1.0,
            if (fwd) easeInOut(seg(t, T.gold[0], T.gold[1] + 0.2)) else 1 - easeInOut(s(T.morphBack)),
            if (fwd) 1 - easeInOut(s(T.halo)) else easeOut(s(T.haloIn)),
            Coin(cx,
            cy,
            cr,
            frame,
            air),
            s(T.glint),
            B.coin.r * mix(1.05,
            3.4,
            easeOut(ringP)),
            if (ringP > 0 && ringP < 1) 0.85 * (1 - ringP) else 0.0,
            if (flashP > 0 && flashP < 1) sin(PI * min(1.0, flashP * 2.4)) * (1 - flashP) else 0.0,
            stars, if (fwd) 1 - seg(t, T.glowOut[0], T.glowOut[0] + 0.25) else seg(t, T.glowIn[1] - 0.25, T.glowIn[1]),
            if (fwd) easeInOut(s(T.benefits)) else 1 - easeInOut(s(T.benefitsBack)),
            if (fwd) easeInOut(seg(t,
            T.benefits[0] + 0.04,
            T.benefits[1] + 0.04)) else 1 - easeInOut(seg(t,
            T.benefitsBack[0] + 0.04,
            T.benefitsBack[1] + 0.04)),
            outIn(T.third,
            T.thirdIn),
            if (fwd) easeInOut(s(T.button)) else 1 - easeInOut(s(T.buttonBack)),
            Close(closeP,
            mix(0.8,
            1.0,
            closeP),
            (1 - closeP) *  - 1),
            bAmount,
            pour * 0.2 + flare + bAmount * 0.04 * sin(2 * PI * 3 * t / LOOP),
            sin(PI * s(T.ctaGlow)),
            if (t > T.carsGlint[0] && t < T.carsGlint[1]) s(T.carsGlint) else - 1.0)
    }

    fun inRestA(t: Double) = t < T.press[0] || t >= T.dividerIn[1]

    fun inRewardB(t: Double) = t >= T.closeIn[1] && t < T.hold[1]

    fun bAmountAt(t: Double) = if (t < 5) seg(t,
        T.hold[0] - 0.3,
        T.hold[0]) else 1 - seg(t,
        T.hold[1],
        T.hold[1] + 0.3)
}
