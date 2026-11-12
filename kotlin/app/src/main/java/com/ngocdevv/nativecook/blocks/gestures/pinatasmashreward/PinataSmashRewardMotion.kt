package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardFrame
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardGeo
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardHit
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardStory
import kotlin.math.*

/** Direct Double-precision port of psr-motion.ts. Every scripted frame is a function of story time. */
internal object PinataSmashRewardMotion {
    const val LOOP = 8.4
    const val STAGE_DY = 56.0
    const val HOP = 0; const val JOLT = 1; const val BURST = 2; const val POKE = 3
    const val CLEAR_BACK = 0; const val CLEAR_DRAIN = 1
    const val SCRIPT_BACK = 6.0
    const val BACK_END = 1.6
    const val DEG = PI / 180.0
    val KIND_W = doubleArrayOf(40.0, 40.0, 29.0, 22.0, 26.0, 21.0, 22.0, 14.0, 14.0, 14.0, 34.0)
    val ATLAS_NAMES = listOf("green-sweet", "magenta-sweet", "lollipop", "mint", "star", "bonbon", "gumdrop", "tile-violet", "tile-azure", "tile-aqua", "glow")
    fun burstLoot(): List<Int> {
        val order = listOf(0, 4, 1, 2, 3, 5, 6, 1, 0)
        return List(36) { order[(it * 5 + (it shr 2)) % order.size] }
    }
    private fun hit(t: Double, x: Double, y: Double, kind: Int, amp: Double, seed: Double, loot: List<Int>, conf: Int, tiles: Int = 0, tokens: Int = 1, value: Int = 10) =
        PinataSmashRewardHit(t, x, y + STAGE_DY, kind, amp, 1.0 / 9, seed, loot, conf, tiles, tokens, value)
    val SCRIPT = listOf(
        hit(0.6, 201.0, 359.0, HOP, 0.0, 1.0, emptyList(), 3),
        hit(1.85, 170.0, 370.0, JOLT, 5.0, 2.0, listOf(0, 1), 0),
        hit(2.3, 230.0, 365.0, JOLT, -4.0, 3.0, listOf(1, 6), 4),
        hit(2.62, 165.0, 350.0, JOLT, 5.0, 4.0, listOf(2, 3), 4),
        hit(2.9, 240.0, 380.0, JOLT, -6.0, 5.0, listOf(4, 0), 5),
        hit(3.14, 150.0, 370.0, JOLT, 6.0, 6.0, listOf(1, 5, 0), 5),
        hit(3.35, 225.0, 355.0, JOLT, -7.0, 7.0, listOf(4, 4, 3), 6),
        hit(3.54, 175.0, 365.0, JOLT, 7.0, 8.0, listOf(6, 2, 1, 5), 6),
        hit(3.74, 156.0, 365.0, BURST, -10.0, 9.0, burstLoot(), 40, 18, 2, 20),
    )
    val SCRIPT_STORY = PinataSmashRewardStory(SCRIPT, SCRIPT_BACK, CLEAR_BACK)
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOutCubic(u: Double): Double { val v = 1 - u; return 1 - v * v * v }
    fun easeInCubic(u: Double) = u * u * u
    fun easeInOutCubic(u: Double) = if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    fun easeOutQuad(u: Double) = 1 - (1 - u) * (1 - u)
    fun smooth(u: Double): Double { val v = clamp01(u); return v * v * (3 - 2 * v) }
    fun hash(n: Double): Double { val s = sin(n * 12.9898 + 78.233) * 43758.5453; return s - floor(s) }
    fun spring(tau: Double, response: Double, z: Double): Double {
        if (tau <= 0) return 0.0
        val w0 = 2 * PI / response; val a = z * w0; val wd = w0 * sqrt(1 - z * z)
        return 1 - exp(-a * tau) * (cos(wd * tau) + a / wd * sin(wd * tau))
    }
    fun ring(tau: Double, decay: Double, period: Double) = if (tau <= 0) 0.0 else exp(-tau / decay) * sin(2 * PI * tau / period)
    private val DONE_T98 = (1 until 200).firstOrNull { spring(it / 200.0, 0.24, 0.8) >= 0.98 }?.div(200.0) ?: 0.3
    fun slowTau(tau: Double): Double {
        val r = 0.35; val p = 0.08; val h = 0.42; val g = 0.25
        if (tau <= p) return tau
        if (tau <= h) return p + (tau - p) * r
        val base = p + (h - p) * r
        if (tau <= h + g) { val u = tau - h; return base + r * u + (1 - r) * u * u / (2 * g) }
        return base + (r * g + (1 - r) * g * 0.5) + (tau - h - g)
    }
    private fun bodyTime(t: Double, hits: List<PinataSmashRewardHit>): Double {
        var te = t
        for (h in hits) {
            if (h.t > t) break
            val stop = if (h.kind == BURST) 0.09 else if (h.kind == POKE) 0.02 else 0.033
            te -= min(t - h.t, stop)
        }
        return te
    }
    private fun hopAt(tau: Double): DoubleArray {
        val r = tau - 0.02
        var y = 0.0; var roll = 0.0; var yaw = 0.0
        when {
            r <= 0 -> Unit
            r < 0.43 -> { val u = easeOutCubic(r / 0.43); y = -120 * u; roll = -6 * u; yaw = 14 * u }
            r < 0.78 -> { val u = (r - 0.43) / 0.35; y = -120 - 4 * sin(PI * u); roll = -6 + 2 * smooth(u); yaw = 14 - 24 * smooth(u) }
            r < 1.18 -> { val e = easeInOutCubic((r - 0.43 - 0.35) / 0.4); y = -120 + 128 * e; roll = -4 + 7 * e; yaw = -10 * (1 - e) }
            else -> { val s = r - 0.43 - 0.35 - 0.4; y = 8 * (1 - spring(s, 0.35, 0.5)); roll = 3 * (1 - spring(s, 0.45, 0.55)) }
        }
        return doubleArrayOf(y, roll, yaw)
    }
    fun fillAfter(story: PinataSmashRewardStory, idx: Int): Double {
        val h = story.hits[idx]; val after = if (story.clear >= 0 && h.t >= story.clear) story.clear else -1.0
        var f = 0.0
        for (i in 0..idx) { val g = story.hits[i]; if (after >= 0 && g.t < after || g.kind == POKE) continue; f += g.inc }
        return f
    }
    private fun roundInfo(hits: List<PinataSmashRewardHit>, from: Double, to: Double): DoubleArray {
        var first = -1.0; var burst = -1.0; var inc = 0.0
        for (h in hits) {
            if (h.t > to) break
            if (h.t < from || h.kind == POKE) continue
            if (first < 0) first = h.t
            if (h.kind == BURST) burst = h.t
            inc += h.inc
        }
        return doubleArrayOf(first, burst, inc)
    }
    fun frameAt(t: Double, story: PinataSmashRewardStory, geo: PinataSmashRewardGeo, swayT: Double): PinataSmashRewardFrame {
        val f = PinataSmashRewardFrame()
        val hits = story.hits; val te = bodyTime(t, hits); val clear = story.clear
        val back = if (clear >= 0 && story.clearKind == CLEAR_BACK && t >= clear) t - clear else -1.0
        val drain = if (clear >= 0 && story.clearKind == CLEAR_DRAIN && t >= clear) t - clear else -1.0
        val swapped = back >= 0.6
        var roundFill = 0.0; var burstT = -1.0
        val after = if (clear >= 0 && t >= clear) clear else -1.0
        for (h in hits) {
            if (h.t > t) break
            if (after >= 0 && h.t < after || h.kind == POKE) continue
            if (h.kind == BURST) burstT = h.t
            val u = clamp01((t - h.t) / 0.12)
            roundFill += h.inc * (easeOutCubic(u) + 0.03 * sin(PI * u))
        }
        roundFill = min(roundFill, 1.03)
        val sway = 1.2 * sin(2 * PI * 3 * swayT / LOOP)
        val swayLag = 1.2 * sin(2 * PI * 3 * (swayT - 0.08) / LOOP)
        var swing = 0.0; var swingBody = 0.0; var swingTail = 0.0; var yaw = 0.0; var dy = 0.0
        var hopY = 0.0; var hopRoll = 0.0; var hopYaw = 0.0; var flashBulbs = 0.0; var waveFrom = -1; var tremble = 0.0
        f.bloomX = geo.body.x; f.bloomY = geo.body.y; f.rippleX = geo.body.x; f.rippleY = geo.body.y
        for ((i, h) in hits.withIndex()) {
            if (h.t > t) break
            val tau = t - h.t; val tb = te - bodyTime(h.t, hits); val old = swapped && h.t < clear
            when {
                h.kind == HOP -> { val hp = hopAt(tau); hopY += hp[0]; hopRoll += hp[1]; hopYaw += hp[2] }
                h.kind == JOLT || h.kind == POKE -> {
                    val amp = h.amp * 1.25
                    swing += h.amp * ring(tb, 0.3, 0.55)
                    swingBody += amp * ring(tb - 0.05, 0.3, 0.55)
                    swingTail += amp * 1.3 * ring(tb - 0.05 - 0.08, 0.3 * 1.2, 0.55)
                    val ya = min(abs(h.amp) * 1.6, 16.0) * if (h.amp > 0) -1 else 1
                    yaw += ya * ring(tb - 0.02, 0.4, 0.8)
                }
                h.kind == BURST && !old -> {
                    swing += h.amp * ring(tb, 0.3, 0.55) + -8 * ring(tb, 1.1, 1.9) * 0.6
                    swingBody += -12 * ring(tb - 0.05, 0.28, 0.6) + -8 * ring(tb - 0.1, 1.1, 1.9)
                    swingTail += -16 * ring(tb - 0.13, 0.4, 0.6) + -9 * ring(tb - 0.2, 1.1, 1.9)
                    yaw += -16 * ring(tb, 0.35, 0.7)
                }
            }
            if (old) continue
            if (tb < 0.12) dy += 3 * sin(PI * tb / 0.12) * if (h.kind == POKE) 0.5 else 1.0
            if (tb < 0.5) {
                val sqv = (if (h.kind == BURST) 1.6 else 1.0) * 0.06 * exp(-tb * 10.1) * cos(20 * tb)
                if (abs(sqv) > abs(f.sq)) { f.sq = sqv; f.sqAng = atan2(h.y - geo.body.y, h.x - geo.body.x) }
            }
            if (tau < 0.42) {
                val q = 1 - tau / 0.42; val b = q * sqrt(q) * 0.5
                if (b > f.bloom) { f.bloom = b; f.bloomX = h.x; f.bloomY = h.y }
            }
            if (tau < 0.6) { waveFrom = i; f.waveTau = tau; flashBulbs = max(flashBulbs, if (h.kind == BURST) 1.0 else 0.8) }
            if (h.kind == HOP && tau < 0.32) {
                val q = tau / 0.32; f.rippleR = 15 + (163 - 15) * easeOutCubic(q); f.rippleA = 0.6 * (1 - q * q); f.rippleX = h.x; f.rippleY = h.y
            }
            if (h.kind == POKE && tau < 0.4) f.shake = 4 * ring(tau, 0.12, 0.12)
            if (h.kind == BURST) {
                f.burst = clamp01(tau / 0.06)
                f.flash = if (tau < 0.09) 0.3 * tau / 0.09 else 0.3 * (1 - clamp01((tau - 0.09) / 0.16))
                if (tau < 0.38) { val u = easeOutCubic(tau / 0.38); f.ringR = 20 + (210 - 20) * u; f.ringA = 0.9 * (1 - u) }
                if (tau < 0.26) { val u = easeOutCubic(tau / 0.26); f.rippleR = 15 + 135 * u; f.rippleA = 0.5 * (1 - u); f.rippleX = geo.flank.x; f.rippleY = geo.flank.y }
                f.stage = 1 + 0.06 * (spring(tau, 0.5, 0.6) - spring(tau - 0.12, 0.5, 0.6))
                f.hole = 0.35 * (1 - clamp01(tau / 0.6)); f.sparks = tau; f.streamers = tau - 0.06
            }
            val fa = fillAfter(story, i)
            if (fa > 0.3 && f.crack1 == 0.0) f.crack1 = clamp01(tau / 0.12)
            if (fa > 0.6 && f.crack2 == 0.0) f.crack2 = clamp01(tau / 0.12)
            if (fa > 0.75 && h.kind != BURST) tremble = max(tremble, clamp01(tau / 0.2))
        }
        if (burstT >= 0) tremble = 0.0
        if (drain >= 0) { val k = 1 - seg(drain, 0.0, 0.3); f.crack1 *= k; f.crack2 *= k; tremble *= k }
        var hoistY = 0.0; var backRoll = 0.0; var tailWhip = 0.0
        if (back >= 0) {
            f.fade = 1 - seg(back, 0.0, 0.3)
            when {
                back < 0.15 -> hoistY = 12 * sin(PI * 0.5 * seg(back, 0.06, 0.15))
                back < 0.6 -> hoistY = 12 - 572 * easeInCubic(seg(back, 0.15, 0.6))
                back < 0.95 -> { val u = seg(back, 0.6, 0.95); hoistY = -560 * (1 - u * u); backRoll = 6.0 }
                else -> {
                    val s = back - 0.95; val win = 1 - smooth(seg(back, BACK_END - 0.3, BACK_END))
                    hoistY = 16 * ring(s, 0.16, 0.3) * win; backRoll = 6 * (1 - spring(s, 0.5, 0.55)) * win
                    tailWhip = 20 * ring(s - 0.04, 0.2, 0.36) * win
                }
            }
            if (swapped) { f.burst = 0.0; f.crack1 = 0.0; f.crack2 = 0.0; f.hole = 0.0 }
        }
        val tr = tremble * 1.2 * sin(2 * PI * 9 * t); val swingX = swing * 2.2
        f.ax = geo.attach.x + swingX; f.ay = geo.attach.y + hopY + hoistY + dy
        f.roll = (sway + swingBody + hopRoll + backRoll + tr) * DEG; f.yaw = (yaw + hopYaw) * DEG
        f.tail = (swingTail - swingBody + (sway - swayLag) * 2 + tailWhip) * DEG; f.bow = max(0.0, -hopY) * (16.0 / 120)
        val inReturn = (back >= 0 && back < BACK_END + 0.3) || (drain >= 0 && drain < 0.7 + 0.4)
        val fresh = if (clear >= 0 && t >= clear) roundInfo(hits, clear, t) else null
        val useOld = inReturn && (fresh == null || fresh[0] < 0)
        val info = if (useOld) roundInfo(hits, -1.0, clear) else fresh ?: roundInfo(hits, -1.0, t)
        val s1 = if (info[0] >= 0) spring(t - info[0], 0.22, 0.8) else 0.0
        val s2 = if (info[1] >= 0) spring(t - info[1], 0.24, 0.8) else 0.0
        f.doneText = if (info[1] >= 0) clamp01((t - info[1] - DONE_T98) / 0.12) else 0.0
        val c2 = if (info[1] >= 0) easeOutCubic(clamp01((t - info[1]) / 0.08)) else 0.0
        val ret = if (useOld) clear + if (back >= 0) 1.05 else 0.5 else -1.0
        val s3 = if (ret >= 0) spring(t - ret, 0.3, 0.78) else 0.0
        val k1 = clamp01(s1); val k3 = clamp01(s3)
        f.wL = (1 - k1) * (1 - k3) + k3; f.wB = k1 * (1 - c2) * (1 - k3); f.wD = k1 * c2 * (1 - k3)
        f.pillW = 186 + (120 - 186) * s1 + (172.5 - 120) * s2 + (186 - if (info[1] >= 0) 172.5 else 120.0) * s3
        f.fill = max(0.0, if (useOld) if (back >= 0) 1.0 else min(info[2], 1.0) * (1 - easeOutCubic(seg(drain, 0.0, 0.5))) else roundFill)
        f.fillGlow = smooth(seg(f.fill, 0.74, 0.8))
        PinataSmashRewardParticles.populate(f, t, story, geo, swingX, hopY, dy, burstT, swapped)
        f.shadowLift = clamp01(-(hopY + hoistY) / 140)
        if (tremble > 0) f.flicker = 1 - 0.18 * tremble * (0.5 + 0.5 * sin(2 * PI * 6 * t))
        if (burstT >= 0 && !swapped) {
            val bt = t - burstT
            f.bulbBoost = if (bt < 0.36) if (floor(bt / 0.06).toInt() % 2 == 0) 1.8 else 0.9 else 1 + 0.2 * (1 - seg(bt, 0.36, 1.5))
        }
        f.waveFrom = if (waveFrom >= 0) geo.bulbs.indices.minByOrNull { val b = geo.bulbs[it]; (b.x - hits[waveFrom].x).pow(2) + (b.y - hits[waveFrom].y).pow(2) } ?: 0 else -1
        f.waveTau = if (waveFrom >= 0) f.waveTau * if (flashBulbs > 0) 1 else 0 else 0.0
        f.bloom *= 1 + 0.15 * ((if (f.crack1 > 0) 1 else 0) + (if (f.crack2 > 0) 1 else 0))
        f.ringX = geo.flank.x; f.ringY = geo.flank.y; f.stageX = geo.flank.x; f.stageY = geo.flank.y
        if (back >= 0.6) f.streamers = -1.0
        f.swayT = swayT
        return f
    }
    fun bulbLevel(i: Int, f: PinataSmashRewardFrame): Double {
        val k = 2 + floor(hash(i + 0.5) * 4)
        val tw = 0.85 + 0.15 * sin(2 * PI * k * f.swayT / LOOP + hash(i + 9.0) * 6.28)
        var wave = 0.0
        if (f.waveFrom >= 0) { val s = f.waveTau - abs(i - f.waveFrom) * 0.04; if (s >= 0) wave = 0.8 * exp(-s / 0.18) }
        return (tw + wave) * f.bulbBoost * f.flicker
    }
}
