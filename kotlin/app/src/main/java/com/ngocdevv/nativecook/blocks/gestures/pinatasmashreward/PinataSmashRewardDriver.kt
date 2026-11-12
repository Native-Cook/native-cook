package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardDriver
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardFrame
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardGeo
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardHit
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardStory
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.BURST
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.CLEAR_BACK
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.CLEAR_DRAIN
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.HOP
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.JOLT
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.POKE
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.SCRIPT
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.burstLoot
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.hash
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardMotion.smooth
import kotlin.math.*

/** UI-thread-owned autoPlay ↔ finger driver, including the 4 ms spring integrator. */
internal object PinataSmashRewardDriverMotion {
    const val RELEASE_MAX = 520.0
    private const val RIBBON = 371.5 + 56
    private const val PEND_W = 2 * PI / 1.7
    fun takeOver(d: PinataSmashRewardDriver) {
        if (d.mode == 1) return
        val t0 = d.loopT; d.mode = 1; d.t = t0
        val hits = if (t0 < 6.0 + 1.6) SCRIPT.filter { it.t <= t0 } else emptyList()
        d.story = PinataSmashRewardStory(hits, if (t0 >= 6 && t0 < 6.0 + 1.6) 6.0 else -1.0, CLEAR_BACK)
        d.last = d.t
    }
    private fun storyBusy(d: PinataSmashRewardDriver): Boolean {
        val s = d.story
        if (s.clear < 0) return false
        val span = if (s.clearKind == CLEAR_BACK) 1.05 + 0.3 else 0.5
        return d.t >= s.clear && d.t < s.clear + span
    }
    /** 0 none, 1 light, 2 medium, 3 burst, 4 post-burst poke. */
    fun tapAt(d: PinataSmashRewardDriver, x: Double, y: Double, geo: PinataSmashRewardGeo): Int {
        takeOver(d); d.last = d.t
        if (storyBusy(d)) return 0
        val s = d.story; val hits = s.hits
        if (hits.lastOrNull()?.let { d.t - it.t < 0.09 } == true) return 0
        var fill = 0.0; var burst = false; var lastRound = -1.0
        for (h in hits) {
            if (s.clear >= 0 && h.t < s.clear || h.kind == POKE) continue
            fill += h.inc; if (h.kind == BURST) burst = true; lastRound = h.t
        }
        val seed = 20 + hits.size * 1.37 + floor(d.t * 10)
        val side = if (x < geo.body.x) 1 else -1; val amp = side * (4 + 3 * min(1.0, fill + 0.1))
        val newHits = hits.toMutableList()
        if (burst) {
            newHits += PinataSmashRewardHit(d.t, x, y, POKE, side * 4.0, 0.0, seed, emptyList(), 2, 0, 0, 0)
            d.story = s.copy(hits = newHits); return 4
        }
        val inc = 1.0 / 12; val willBurst = fill + inc >= 0.999
        val rest = lastRound < 0 || d.t - lastRound > 1.5
        when {
            willBurst -> newHits += PinataSmashRewardHit(d.t, geo.flank.x, geo.flank.y, BURST, -10.0, inc, seed, burstLoot(), 40, 18, 2, 20)
            rest && fill == 0.0 -> newHits += PinataSmashRewardHit(d.t, x, y, HOP, 0.0, inc, seed, emptyList(), 3, 0, 1, 10)
            else -> {
                val n = 2 + floor(hash(seed) * 2.2).toInt()
                val loot = List(n) { floor(hash(seed + it * 3.1) * 7).toInt() }
                newHits += PinataSmashRewardHit(d.t, x, y, JOLT, amp, inc, seed, loot, 4 + floor(hash(seed + 1) * 3).toInt(), 0, 1, 10)
            }
        }
        d.story = s.copy(hits = newHits)
        return if (willBurst) 3 else if (fill + inc >= 0.66) 2 else 1
    }
    private fun stepDrag(d: PinataSmashRewardDriver, dt: Double) {
        val steps = max(1, ceil(dt / 0.004).toInt()); val h = dt / steps
        repeat(steps) {
            if (d.drag) {
                var tx = d.fx + d.gx; var ty = d.fy + d.gy
                val dx = tx; val dy = ty + RIBBON; val r = hypot(dx, dy)
                if (r > RIBBON) { val over = r - RIBBON; val rr = RIBBON + over * 0.12 / (1 + over / 120); tx = dx * rr / r; ty = dy * rr / r - RIBBON }
                val ax = -320 * (d.ox - tx) - 26 * d.vx; val ay = -320 * (d.oy - ty) - 26 * d.vy
                d.vx += ax * h; d.vy += ay * h; d.ox += d.vx * h; d.oy += d.vy * h
            } else if (abs(d.ox) + abs(d.oy) + abs(d.vx) + abs(d.vy) + abs(d.th) + abs(d.om) > 0.001) {
                val px = d.ox; val py = d.oy + RIBBON; val r = hypot(px, py)
                var th = atan2(px, py); val om = (d.vx * cos(th) - d.vy * sin(th)) / max(r, 1.0)
                var vr = d.vx * sin(th) + d.vy * cos(th); val acc = -PEND_W * PEND_W * sin(th) - 2 * 0.9 * om
                val om2 = om + acc * h; th += om2 * h
                var rr = r + vr * h; vr += (-300 * (rr - RIBBON) - 30 * vr) * h; rr = max(0.0, rr)
                d.ox = rr * sin(th); d.oy = rr * cos(th) - RIBBON
                d.vx = om2 * rr * cos(th) + vr * sin(th); d.vy = -om2 * rr * sin(th) + vr * cos(th); d.th = th; d.om = om2
                if (abs(d.ox) + abs(d.oy) < 0.02 && abs(d.vx) + abs(d.vy) < 0.05) { d.ox = 0.0; d.oy = 0.0; d.vx = 0.0; d.vy = 0.0; d.th = 0.0; d.om = 0.0 }
            }
        }
    }
    fun stepDriver(d: PinataSmashRewardDriver, dt: Double, auto: Boolean): Double {
        d.press += (d.pressAim - d.press) * (1 - exp(-dt * 30))
        if (d.mode == 0) {
            if (auto) d.loopT = (d.loopT + dt) % PinataSmashRewardMotion.LOOP
            if (d.blendT >= 0) { d.blendT += dt; if (d.blendT > 0.5) d.blendT = -1.0 }
            stepDrag(d, dt); return d.loopT
        }
        d.t += dt; stepDrag(d, dt)
        val s = d.story; val idle = d.t - d.last
        if (!d.drag && idle > 2) {
            var burst = false; var any = false
            for (h in s.hits) { if (s.clear >= 0 && h.t < s.clear) continue; any = true; if (h.kind == BURST) burst = true }
            val cleared = s.clear >= 0 && s.clear >= d.last
            if (!cleared && (burst || any)) {
                d.story = PinataSmashRewardStory(s.hits.filter { s.clear < 0 || it.t >= s.clear }, d.t, if (burst) CLEAR_BACK else CLEAR_DRAIN)
            } else {
                val end = if (s.clear >= 0) s.clear + if (s.clearKind == CLEAR_BACK) 1.6 else 0.7 else d.last + 2
                val settled = abs(d.ox) + abs(d.oy) + abs(d.th) < 1.5
                if (d.t >= end && settled && auto) { d.mode = 0; d.loopT = 6.0 + 1.6; d.blendT = 0.0; d.blendPending = true }
            }
        }
        return d.t
    }
    fun applyDriver(f: PinataSmashRewardFrame, d: PinataSmashRewardDriver) {
        f.ax += d.ox; f.ay += d.oy
        f.roll += atan2(d.ox, d.oy + RIBBON) * 0.6 - (d.vx * 0.0006).coerceIn(-0.35, 0.35)
        f.tail += -(d.vx * 0.0012).coerceIn(-0.5, 0.5); f.press = d.press
        if (d.blendT >= 0) { val k = 1 - smooth(d.blendT / 0.5); f.roll += d.blendRoll * k; f.ax += d.blendX * k; f.ay += d.blendY * k }
    }
}
