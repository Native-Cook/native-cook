package com.ngocdevv.nativecook.blocks.gestures.frostchestopening

import kotlin.math.*

/** Value-for-value reference timeline. Double arithmetic preserves the RN seeds. */
internal object FrostChestOpeningMotion {
    const val LOOP = 9.6f
    const val DRAIN = 4.0
    const val RESUME = 2.0
    private const val L_CARD_Y = 226.0
    private const val L_CARD_X = 201.5
    object T {
        val press = 0.62
        val pop = doubleArrayOf(0.645, 0.665)
        val popPeak = 0.82
        val popSettle = 0.95
        val meniscus = 0.66
        val fillKnee = 0.81
        val fillEnd = 2.26
        val drift = doubleArrayOf(0.88, 2.35)
        val hintOut = doubleArrayOf(0.62, 0.8)
        val charge = doubleArrayOf(0.8, 1.9)
        val shaft = doubleArrayOf(1.98, 2.43)
        val headerOut = doubleArrayOf(2.35, 2.62)
        val pillOut = doubleArrayOf(2.3, 2.54)
        val flyStart = 2.38
        val impact = 2.64
        val flashIn = doubleArrayOf(2.632, 2.74)
        val flashOut = doubleArrayOf(2.88, 3.02)
        val swap = 2.715
        val glareChest = doubleArrayOf(2.74, 2.86)
        val pale = doubleArrayOf(3.8, 4.2)
        val sparkle = doubleArrayOf(2.74, 3.4)
        val frostIn = doubleArrayOf(3.02, 3.22)
        val card = 3.03
        val cardFull = 3.27
        val cardShrink = doubleArrayOf(3.45, 3.64)
        val cardSharp = doubleArrayOf(3.26, 3.38)
        val panel = doubleArrayOf(3.98, 4.12)
        val others = doubleArrayOf(4.08, 4.38)
        val tileStart = doubleArrayOf(4.13, 4.2, 4.28)
        val tileTau = doubleArrayOf(0.065, 0.06, 0.05)
        val claim = 4.245
        val claimShow = doubleArrayOf(4.17, 4.27)
        val settled = 4.7
        val leak = doubleArrayOf(2.4, 2.632)
        val burstAt = 2.632
        val surge = doubleArrayOf(3.0, 3.1)
        val cardCool = doubleArrayOf(3.2, 3.45)
        val motes = doubleArrayOf(2.74, 4.8)
        val claimPress = doubleArrayOf(7.7, 7.86)
        val exit = doubleArrayOf(7.86, 8.14)
        val sink = doubleArrayOf(7.92, 8.36)
        val flash2In = doubleArrayOf(8.24, 8.38)
        val flash2Out = doubleArrayOf(8.42, 8.86)
        val swapBack = 8.4
        val restIn = doubleArrayOf(8.46, 9.06)
    }
    data class OrbPoint(val x: Double = 0.0, val y: Double = 0.0, val r: Double = 0.0, val dy: Double = 0.0, val dr: Double = 0.0, val dur: Double = 0.0)
    object ORB {
        val bead = OrbPoint(x = 201.5,
        y = 651.0,
        r = 6.5)
        val start = OrbPoint(x = 201.5,
        y = 648.0,
        r = 55.0)
        val end = OrbPoint(x = 201.5,
        y = 643.0,
        r = 66.8)
        val swallow = OrbPoint(x = 203.0,
        dy = 205.0,
        dr = 36.8,
        dur = 0.26)
    }
    fun clamp01(x: Double): Double {
        return (if (x < 0.0) 0.0 else (if (x > 1.0) 1.0 else x))
    }
    fun seg(t: Double, a: Double, b: Double): Double {
        return clamp01((t - a) / (b - a))
    }
    fun easeOut(x: Double): Double {
        val u = 1.0 - x
        return 1.0 - u * u * u
    }
    fun easeIn(x: Double): Double {
        return x * x * x
    }
    fun easeInOut(x: Double): Double {
        return (if (x < 0.5) 4.0 * x * x * x else 1.0 - (-2.0 * x + 2.0).pow(3.0) / 2.0)
    }
    fun mix(a: Double, b: Double, k: Double): Double {
        return a + (b - a) * k
    }
    data class Frame(
        val t: Double,
        val idle: Double,
        val orb: Double,
        val orbX: Double,
        val orbY: Double,
        val orbR: Double,
        val fill: Double,
        val charge: Double,
        val sky: Double,
        val shaft: Double,
        val fly: Double,
        val header: Double,
        val pill: Double,
        val hint: Double,
        val press: Double,
        val seam: Double,
        val flash: Double,
        val reveal: Double,
        val frost: Double,
        val glare: Double,
        val pale: Double,
        val mouth: Double,
        val sparkle: Double,
        val openDY: Double,
        val cardX: Double,
        val cardY: Double,
        val cardS: Double,
        val cardA: Double,
        val spin: Double,
        val cardBlur: Double,
        val sink: Double,
        val panel: Double,
        val others: Double,
        val tiles: DoubleArray,
        val claim: Double,
        val claimInk: Double,
        val claimY: Double,
        val claimDown: Double,
        val lCore: Double,
        val lRays: Double,
        val lReach: Double,
        val lStreak: Double,
        val lRingR: Double,
        val lRingA: Double,
        val lPillar: Double,
        val lX: Double,
        val lY: Double,
        val lBelow: Double,
        val lBehind: Double,
        val exposure: Double,
        val vignette: Double,
        val dim: Double,
        val shakeX: Double,
        val shakeY: Double,
        val punch: Double,
        val cardHot: Double,
        val cardHalo: Double,
        val motes: Double
    )
    val SPIN_T = doubleArrayOf(3.03, 3.13, 3.17, 3.23, 3.31, 3.42, 3.565, 3.68, 3.8)
    val SPIN_A = doubleArrayOf(180.0, 200.0, 270.0, 360.0, 450.0, 540.0, 630.0, 705.0, 720.0)
    fun spinAt(t: Double): Double {
        if (t <= SPIN_T[0]) return SPIN_A[0]
        for (i in 1 until SPIN_T.size) {
            if (t < SPIN_T[i]) {
                val k = (t - SPIN_T[i - 1]) / (SPIN_T[i] - SPIN_T[i - 1])
                val e = (if (i == SPIN_T.size - 1) 1.0 - (1.0 - k) * (1.0 - k) else k)
                return mix(SPIN_A[i - 1], SPIN_A[i], e)
            }
        }
        return SPIN_A[SPIN_A.size - 1]
    }
    fun fillAt(t: Double): Double {
        if (t < T.meniscus) return 0.0
        if (t < T.fillKnee) return mix(0.03, 0.15, seg(t, T.meniscus, T.fillKnee))
        return mix(0.15, 1.0, seg(t, T.fillKnee, T.fillEnd))
    }
    fun frameAt(t: Double, idle: Double): Frame {
        val charging = t >= T.press && t < T.swap
        val popK = seg(t, T.pop[0], T.pop[1])
        val pop = (if (t < T.pop[1]) 0.8 * easeOut(popK) else (if (t < T.popPeak) mix(0.8, 1.065, easeOut(seg(t, T.pop[1], T.popPeak))) else mix(1.065, 1.0, easeInOut(seg(t, T.popPeak, T.popSettle)))))
        val d = seg(t, T.drift[0], T.drift[1])
        var orbX = mix(ORB.start.x, ORB.end.x, d)
        var orbY = mix(ORB.start.y, ORB.end.y, d)
        var orbR = mix(ORB.start.r, ORB.end.r, d)
        if (t < T.popSettle) {
            val k = seg(t, T.pop[0], T.popSettle)
            orbX = mix(ORB.bead.x, ORB.start.x, k)
            orbY = mix(ORB.bead.y, ORB.start.y, k)
            orbR = mix(ORB.bead.r, ORB.start.r, pop)
        }
        val flyK = seg(t, T.flyStart, T.impact)
        if (t > T.flyStart) {
            val u = min(t, T.impact) - T.flyStart
            val k = u / ORB.swallow.dur
            orbX = mix(ORB.end.x, ORB.swallow.x, min(1.0, k))
            orbY = ORB.end.y - ORB.swallow.dy * (0.25 * k + 0.75 * (k).pow(2.4))
            orbR = ORB.end.r - ORB.swallow.dr * (0.3 * k + 0.7 * (k).pow(1.8))
        }
        val orb = (if (charging && t < T.impact) 1.0 else 0.0)
        val fill = (if (charging) fillAt(t) else 0.0)
        val fly = (if (charging) flyK else 0.0)
        val charge = (if (t < T.swap) easeInOut(seg(t, T.charge[0], T.charge[1])) else 0.0)
        val sky = (if (t < T.swap) easeInOut(seg(t, T.charge[0], T.impact)) else 0.0)
        val shaft = (if (t > T.shaft[0] && t < T.shaft[1]) seg(t, T.shaft[0], T.shaft[1]) else 0.0)
        val restBack = easeOut(seg(t, T.restIn[0], T.restIn[1]))
        val header = (if (t < T.swapBack) 1.0 - seg(t, T.headerOut[0], T.headerOut[1]) else restBack)
        val pill = (if (t < T.swapBack) 1.0 - easeOut(seg(t, T.press, T.popSettle)) else restBack)
        val hint = (if (t < T.swapBack) 1.0 - easeOut(seg(t, T.hintOut[0], T.hintOut[1])) else restBack)
        val press = (if (t < T.press) 0.0 else (if (t < T.fillEnd) easeOut(seg(t, T.press, T.press + 0.12)) else 1.0 - seg(t, T.fillEnd, T.fillEnd + 0.2)))
        val seam = (if (t < T.swap) seg(t, T.impact - 0.04, T.swap) else 0.0)
        val flash1 = (if (t < T.flashOut[0]) seg(t, T.flashIn[0], T.flashIn[0] + 0.014) else 1.0 - easeOut(seg(t, T.flashOut[0], T.flashOut[1])))
        val flash2 = (if (t < T.flash2Out[0]) easeIn(seg(t, T.flash2In[0], T.flash2In[1])) else 1.0 - easeOut(seg(t, T.flash2Out[0], T.flash2Out[1])))
        val flash = (if (t < 6.0) flash1 else flash2 * 0.92)
        val reveal = (if (t >= T.swap && t < T.swapBack) 1.0 else 0.0)
        val frost = (if (t < T.swapBack) easeInOut(seg(t, T.frostIn[0], T.frostIn[1])) else 0.0)
        val glare = reveal * (1.0 - seg(t, T.glareChest[0], T.glareChest[1]))
        val pale = easeInOut(seg(t, T.pale[0], T.pale[1]))
        val mouth = reveal * mix(1.0, 0.55, seg(t, 3.0, 3.85))
        val sparkle = (if (t >= T.sparkle[0] && t < T.sparkle[1]) t - T.sparkle[0] else -1.0)
        val openDY = reveal * (5.0 * (1.0 - seg(t, 3.0, 5.5)) + 3.0 * (1.0 - easeOut(seg(t, T.swap, 2.85))))
        val sink = easeIn(seg(t, T.sink[0], T.sink[1]))
        val cu = max(0.0, t - T.card) / 0.17
        val cardY = L_CARD_Y + 88.0 * exp(-cu * cu)
        val cardX = L_CARD_X - 3.0 * (1.0 - seg(t, 3.4, 3.7))
        val sk = seg(t, T.card, T.cardFull)
        val cardS = (if (t < T.cardShrink[0]) 0.1 + 0.98 * sk * sk * (3.0 - 2.0 * sk) else mix(1.08, 1.0, easeInOut(seg(t, T.cardShrink[0], T.cardShrink[1]))))
        val cardA = reveal * seg(t, T.card, T.card + 0.07)
        val spin = spinAt(t)
        val cardBlur = 4.5 * (1.0 - seg(t, T.cardSharp[0], T.cardSharp[1])) * ((if (t >= T.card) 1.0 else 0.0))
        val exit = easeOut(seg(t, T.exit[0], T.exit[1]))
        val panel = seg(t, T.panel[0], T.panel[1]) * (1.0 - exit)
        val others = seg(t, T.others[0], T.others[1]) * (1.0 - exit)
        val tile = { k: Int -> ((if (t < T.tileStart[k]) 0.0 else 1.0 - exp(-(t - T.tileStart[k]) / T.tileTau[k]))) * (1.0 - exit) }
        val ct = max(0.0, t - T.claim)
        val claimY = ((if (t < T.claim) 60.0 else 60.0 * exp(-ct / 0.1))) + 40.0 * exit
        val claim = seg(t, T.claimShow[0], T.claimShow[1]) * (1.0 - exit)
        val claimInk = (if (t < T.claim) 0.0 else 1.0 - exp(-ct / 0.09))
        val cd = seg(t, T.claimPress[0], T.claimPress[1])
        val claimDown = (if (cd <= 0.0 || cd >= 1.0) 0.0 else sin(cd * PI))
        val bt = t - T.burstAt
        val lit = t >= T.burstAt && t < T.swapBack
        val leak = (if (t < T.burstAt && t >= T.leak[0]) easeIn(seg(t, T.leak[0], T.leak[1])) else 0.0)
        val sinkLight = 0.8 * sin(PI * seg(t, T.sink[0], T.sink[1] + 0.05))
        var lCore = 0.18 * leak
        if (lit) {
            if (bt < 0.03) lCore = mix(0.2, 3.2, bt / 0.03) else if (t < 2.9) lCore = mix(3.2, 2.6, seg(t, T.burstAt + 0.03, 2.9)) else if (t < T.surge[0]) lCore = mix(2.6, 1.3, seg(t, 2.9, T.surge[0])) else if (t < T.surge[1]) lCore = mix(1.3, 2.6, easeOut(seg(t, T.surge[0], T.surge[1]))) else if (t < 3.45) {
                val k = seg(t, T.surge[1] + 0.08, 3.45)
                lCore = mix(2.6, 0.35, 1.0 - (1.0 - k) * (1.0 - k))
            } else lCore = mix(0.35, 0.1, seg(t, 3.4, 4.4))
            lCore = lCore * (1.0 - seg(t, T.sink[0], T.sink[1])) + sinkLight
        }
        val lRays = (if (lit) ((if (bt < 0.6) 1.1 else mix(1.05, 0.18, seg(t, 3.3, 3.95)))) * (1.0 - 0.8 * seg(t, T.sink[0], T.sink[1])) else 0.7 * leak)
        val lReach = (if (lit) 0.2 + 0.9 * easeOut(seg(t, T.burstAt, 2.95)) else 0.18 + 0.1 * leak)
        var lStreak = 0.15 * leak
        if (lit) {
            if (bt < 0.04) lStreak = mix(0.2, 1.2, bt / 0.04) else if (t < T.surge[0]) lStreak = mix(1.2, 0.25, easeOut(seg(t, T.burstAt + 0.04, T.surge[0]))) else if (t < T.surge[1]) lStreak = mix(0.25, 0.8, seg(t, T.surge[0], T.surge[1])) else lStreak = mix(0.8, 0.0, easeOut(seg(t, T.surge[1], 3.6)))
        }
        var lRingR = 0.0
        var lRingA = 0.0
        if (lit && t < 3.05) {
            lRingR = 740.0 * easeOut(seg(t, T.burstAt, 3.05))
            lRingA = 0.9 * (1.0 - seg(t, T.burstAt, 3.05))
        } else if (t >= T.surge[0] + 0.04 && t < 3.45) {
            lRingR = 420.0 * easeOut(seg(t, T.surge[0] + 0.04, 3.45))
            lRingA = 0.45 * (1.0 - seg(t, T.surge[0] + 0.04, 3.45))
        }
        val onCard = seg(t, T.surge[0], T.surge[0] + 0.08) * (1.0 - seg(t, 3.5, 4.0))
        val lPillar = (if (lit) seg(t, 2.85, 2.98) * (1.0 - onCard) * (1.0 - seg(t, 3.3, 3.6)) else 0.0)
        val toMouth = easeInOut(seg(t, T.burstAt, 2.8))
        val lX = (if (lit) mix(mix(200.0, 198.5, toMouth), cardX, onCard) else 200.0)
        val lY = (if (lit) mix(mix(430.0, 400.0, toMouth), cardY, onCard) else 425.0)
        val lBelow = (if (t < 2.88) 1.0 else mix(1.0, 0.1, seg(t, 2.88, 3.02)))
        val lBehind = (if (lit) 0.0 else 1.0)
        val hump1 = seg(t, T.burstAt, 2.68) * (1.0 - seg(t, 2.9, 3.05))
        val hump2 = sin(PI * seg(t, T.surge[0], 3.4))
        val exposure = 1.0 + ((if (lit) 0.6 * hump1 + 0.35 * hump2 else 0.0))
        val dimIn = easeInOut(seg(t, 2.3, 2.6))
        val dimOut = easeInOut(seg(t, 3.25, 3.8))
        val dim = (if (t < T.swapBack) 0.72 * dimIn * (1.0 - dimOut) else 0.0)
        val vignette = 0.38 * dimIn * (1.0 - dimOut) * ((if (t < T.swapBack) 1.0 else 0.0))
        var shakeX = 0.0
        var shakeY = 0.0
        if (bt >= 0.0 && bt < 0.35) {
            val amp = 5.0 * exp(-bt / 0.07)
            shakeX = amp * sin(bt * 95.0)
            shakeY = amp * cos(bt * 78.0 + 0.7)
        } else if (t >= T.surge[0] + 0.02 && t < T.surge[0] + 0.25) {
            val amp = 2.0 * exp(-(t - T.surge[0] - 0.02) / 0.06)
            shakeX = amp * sin(t * 90.0)
            shakeY = amp * cos(t * 70.0)
        }
        val pushIn = (if (t < T.burstAt) 0.012 * easeIn(seg(t, 1.9, T.burstAt)) else 0.012 * (1.0 - easeOut(seg(t, T.burstAt, T.burstAt + 0.5))))
        val punch = 1.0 + pushIn + ((if (bt >= 0.0 && t < T.swapBack) 0.03 * exp(-bt / 0.09) else 0.0))
        val cardHot = (if (t < T.cardCool[0]) 1.0 else 1.0 - easeOut(seg(t, T.cardCool[0], T.cardCool[1])))
        val cardHalo = seg(t, T.card, T.card + 0.07) * (1.0 - seg(t, 3.35, 3.9))
        val motes = (if (t >= T.motes[0] && t < T.motes[1]) t - T.motes[0] else -1.0)
        return Frame(lCore = lCore,
        lRays = lRays,
        lReach = lReach,
        lStreak = lStreak,
        lRingR = lRingR,
        lRingA = lRingA,
        lPillar = lPillar,
        lX = lX,
        lY = lY,
        lBelow = lBelow,
        lBehind = lBehind,
        exposure = exposure,
        vignette = vignette,
        dim = dim,
        shakeX = shakeX,
        shakeY = shakeY,
        punch = punch,
        cardHot = cardHot,
        cardHalo = cardHalo,
        motes = motes,
        t = t,
        idle = idle,
        orb = orb,
        orbX = orbX,
        orbY = orbY,
        orbR = orbR,
        fill = fill,
        charge = charge,
        sky = sky,
        shaft = shaft,
        fly = fly,
        header = header,
        pill = pill,
        hint = hint,
        press = press,
        seam = seam,
        flash = flash,
        reveal = reveal,
        frost = frost,
        glare = glare,
        pale = pale,
        mouth = mouth,
        sparkle = sparkle,
        openDY = openDY,
        cardX = cardX,
        cardY = cardY,
        cardS = cardS,
        cardA = cardA,
        spin = spin,
        cardBlur = cardBlur,
        sink = sink,
        panel = panel,
        others = others,
        tiles = doubleArrayOf(tile(0), tile(1), tile(2)),
        claim = claim,
        claimInk = claimInk,
        claimY = claimY,
        claimDown = claimDown)
    }
    fun rand(i: Double, salt: Double): Double {
        val x = sin(i * 127.1 + salt * 311.7) * 43758.5453
        return x - floor(x)
    }
}
