package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardConf
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardFrame
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardGeo
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardLoot
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardStory
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardToken
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.BURST
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.CLEAR_BACK
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.DEG
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.clamp01
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.easeOutCubic
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.easeOutQuad
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.fillAfter
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.hash
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.seg
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.slowTau
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.smooth
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.spring
import kotlin.math.*

/** Sweets, paper, confetti and balance tokens use the source's deterministic seeds and clocks. */
internal object PinataSmashRewardParticles {
    private fun lootPiece(k: Int, x0: Double, y0: Double, vx: Double, vy: Double, spin: Double, restRot: Double, floorY: Double, tau: Double): PinataSmashRewardLoot {
        val g = 2100.0; val dy = floorY - y0
        val t = (-vy + sqrt(vy * vy + 2 * g * max(dy, 1.0))) / g
        val xl = (x0 + vx * t * 0.55).coerceIn(18.0, 384.0); val vxe = (xl - x0) / t
        var x: Double; var y: Double; var rot: Double
        if (tau < t) { x = x0 + vxe * tau; y = y0 + vy * tau + 0.5 * g * tau * tau; rot = spin * tau } else {
            val vimp = vy + g * t; val v1 = vimp * 0.32; val t1 = 2 * v1 / g; val v2 = v1 * 0.32; val t2 = 2 * v2 / g
            val s = tau - t; val drift = vxe * 0.18; val aT = spin * t
            // JS Math.round ties toward +infinity.
            val target = restRot + floor((aT - restRot) / PI + 0.5) * PI
            when {
                s < t1 -> { x = xl + drift * s; y = floorY - (v1 * s - 0.5 * g * s * s); rot = aT + (target - aT) * easeOutQuad(s / (t1 + t2)) }
                s < t1 + t2 -> { val q = s - t1; x = xl + drift * (t1 + q * 0.5); y = floorY - (v2 * q - 0.5 * g * q * q); rot = aT + (target - aT) * easeOutQuad(s / (t1 + t2)) }
                else -> { x = xl + drift * (t1 + t2 * 0.5); y = floorY; rot = target }
            }
        }
        return PinataSmashRewardLoot(k, x, y, rot)
    }
    private fun confPiece(out: MutableList<PinataSmashRewardConf>, x0: Double, y0: Double, vx: Double, vy: Double, tau: Double, seed: Double, c: Int, fade: Double) {
        val k = 260.0 / 900; val e = 1 - exp(-tau / k)
        val x = x0 + vx * k * e + 12 * sin(2 * PI * 3 * tau + seed * 6.28) * clamp01(tau * 2)
        val y = y0 + 260 * tau + (vy - 260) * k * e
        if (y > 900) return
        val spin = (4 + hash(seed + 3) * 8) * if (hash(seed + 4) > 0.5) 1 else -1
        val ang = spin * tau + seed * 3; val diamond = hash(seed + 5) < 0.3
        val w = if (diamond) 8.0 else 4.0; val h = if (diamond) 8.0 else 9.0
        out += PinataSmashRewardConf(x, y, w * (0.35 + 0.65 * abs(cos(ang * 0.8))), h, ang, c, fade * (1 - seg(y, 820.0, 880.0)), diamond)
    }
    fun populate(f: PinataSmashRewardFrame, t: Double, story: PinataSmashRewardStory, geo: PinataSmashRewardGeo, swingX: Double, hopY: Double, dy: Double, burstT: Double, swapped: Boolean) {
        val clear = story.clear
        val chipReset = if (story.clearKind == CLEAR_BACK) 1.05 else 0.5
        val arrive = 0.02 + 0.25 + 0.52
        for (h in story.hits) {
            if (h.t > t) break
            for (j in 0 until h.tokens) {
                val tau = t - h.t - j * 0.12
                if (tau < 0.02) continue
                if (tau >= arrive) {
                    val k = clamp01((tau - arrive) / 0.2)
                    val reset = if (clear >= 0 && h.t < clear) easeOutCubic(seg(t - clear, chipReset, chipReset + 0.3)) else 0.0
                    f.count += h.value * easeOutCubic(k) * (1 - reset)
                    if (tau - arrive < 0.3) f.chipBump = max(f.chipBump, sin(PI * clamp01((tau - arrive) / 0.3)))
                    continue
                }
                val bx = if (h.kind == BURST) geo.flank.x + if (j == 0) -14 else 18 else h.x - 12 * (hash(h.seed + j) - 0.5)
                val by = if (h.kind == BURST) geo.flank.y - 20 else h.y + 6
                val pop = tau - 0.02; val s = 0.6 + 0.4 * spring(pop, 0.25, 0.6)
                val rx = bx + swingX * 0.6; val ry = by + (hopY + dy) * 0.9 - 26 * easeOutCubic(clamp01(pop / (0.25 + 0.1)))
                if (pop < 0.25) { f.tokens += PinataSmashRewardToken(rx, ry, s); continue }
                val tx = mutableListOf<Double>(); val ty = mutableListOf<Double>()
                var px = 0.0; var py = 0.0; var ps = 1.0
                for (q in 0 until 5) {
                    val u = clamp01((pop - 0.25 - q * 0.012) / 0.52); val e = u * u * (1.6 - 0.6 * u)
                    val cx = rx + (geo.chipX - rx) * 0.15; val cy = min(ry, geo.chipY) - 150
                    val ix = (1 - e) * (1 - e) * rx + 2 * (1 - e) * e * cx + e * e * geo.chipX
                    val iy = (1 - e) * (1 - e) * ry + 2 * (1 - e) * e * cy + e * e * geo.chipY
                    if (q == 0) { px = ix; py = iy; ps = 1 - 0.4 * e } else { tx += ix; ty += iy }
                }
                f.tokens += PinataSmashRewardToken(px, py, s * ps, tx = tx, ty = ty)
            }
        }
        var pieceIdx = 0
        for ((i, h) in story.hits.withIndex()) {
            if (h.t > t) break
            val tau0 = t - h.t; val isBurst = h.kind == BURST; val tau = if (isBurst) slowTau(tau0) else tau0
            val vac = if (clear >= 0 && h.t < clear && t >= clear) t - clear else -1.0
            val cfade = if (vac >= 0) 1 - seg(t - clear, 0.0, 0.3) else 1.0
            val base = atan2(h.y - geo.body.y, h.x - geo.body.x); val n = h.loot.size
            for (j in 0 until n) {
                val sd = h.seed * 31 + j * 7.13
                val ang: Double; val sp: Double
                if (isBurst) { ang = (150 + 240 * ((j + hash(sd) * 0.8) / n)) * DEG; sp = 420 + 560 * hash(sd + 1) } else {
                    val up = -PI / 2; var a0 = base + (up - base) * 0.35
                    if (abs(h.x - geo.body.x) < 6 && abs(h.y - geo.body.y) < 6) a0 = up
                    ang = if (cos(base) == 0.0) up else a0 + (hash(sd) - 0.5) * 100 * DEG * if (n > 1) 1.0 else 0.4
                    sp = 420 + 340 * hash(sd + 1)
                }
                val vx = cos(ang) * sp; val vy = sin(ang) * sp
                val spin = (200 + 500 * hash(sd + 2)) * DEG * if (hash(sd + 3) > 0.5) 1 else -1
                val floorY = 836 - 18 * hash(sd + 5) - (pieceIdx % 3) * 6
                val p = lootPiece(h.loot[j], if (isBurst) geo.flank.x + cos(ang) * 16 else h.x, if (isBurst) geo.flank.y + sin(ang) * 16 else h.y, vx, vy, spin, (hash(sd + 4) - 0.5) * 0.7, floorY, tau)
                pieceIdx++
                if (!isBurst && burstT >= 0 && !swapped) { val pj = t - burstT - 0.5; if (pj > 0 && pj < 0.3) p.y -= 6 * sin(PI * pj / 0.3) }
                if (vac >= 0) {
                    val d = hypot(p.x - geo.chipX, p.y - geo.chipY); val delay = clamp01((d - 480) / 420) * 0.14
                    val u = clamp01((vac - delay) / 0.34)
                    if (u >= 1) continue
                    val e = u * u; val cx = p.x + (geo.chipX - p.x) * 0.2; val cy = p.y - 240
                    p.x = (1 - e) * (1 - e) * p.x + 2 * (1 - e) * e * cx + e * e * geo.chipX
                    p.y = (1 - e) * (1 - e) * p.y + 2 * (1 - e) * e * cy + e * e * geo.chipY
                    p.s = 1 - 0.7 * e; p.a = 1 - smooth(seg(u, 0.75, 1.0)); p.rot += e * 3
                }
                f.loot += p
            }
            val fa = fillAfter(story, i)
            val nt = h.tiles + (if (fa > 0.3 && fa - h.inc <= 0.3) 4 else 0) + (if (fa > 0.6 && fa - h.inc <= 0.6) 4 else 0)
            for (j in 0 until nt) {
                val sd = h.seed * 17 + j * 3.31 + 100
                val ang = (if (isBurst) 160 + 220 * hash(sd) else 200 + 140 * hash(sd)) * DEG
                val sp = if (isBurst) 300 + 500 * hash(sd + 1) else 120 + 160 * hash(sd + 1)
                val ox = if (isBurst) geo.flank.x else geo.flank.x + (hash(sd + 6) - 0.5) * 50
                val oy = if (isBurst) geo.flank.y else geo.flank.y + (hash(sd + 7) - 0.5) * 40
                val k = 7 + floor(hash(sd + 2) * 3).toInt(); val tt = if (isBurst) tau else tau0
                val x = ox + cos(ang) * sp * min(tt, 0.5) * (1 - min(tt, 0.5)) * 1.4 + 18 * sin(tt * 5 + sd)
                val y = oy + sin(ang) * sp * min(tt, 0.5) + 0.5 * 700 * tt * tt
                if (y > 900) continue
                val s = (10 + 8 * hash(sd + 3)) / 14
                val spin = (3 + 6 * hash(sd + 4)) * if (hash(sd + 5) > 0.5) 1 else -1
                f.loot += PinataSmashRewardLoot(k, x, y, spin * tt, s, cfade * (1 - seg(y, 820.0, 880.0)), cos(spin * tt * 0.7))
            }
            for (j in 0 until h.conf) {
                val sd = h.seed * 13 + j * 1.77 + 50
                val ang = (if (isBurst) 150 + 240 * hash(sd) else -90 + (hash(sd) - 0.5) * 140) * DEG
                val sp = if (isBurst) 420 + 560 * hash(sd + 1) else 160 + 240 * hash(sd + 1)
                confPiece(f.conf, if (isBurst) geo.flank.x else h.x, if (isBurst) geo.flank.y else h.y, cos(ang) * sp, sin(ang) * sp, if (isBurst) tau else tau0, sd, floor(hash(sd + 2) * 6).toInt(), cfade)
            }
        }
    }
}
