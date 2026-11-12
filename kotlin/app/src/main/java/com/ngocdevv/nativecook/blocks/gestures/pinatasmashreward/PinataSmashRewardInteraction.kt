package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardDriver
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardFrame
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardGeo
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardLoot
import com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward.PinataSmashRewardModel.PinataSmashRewardStory
import kotlin.math.hypot
import kotlin.math.min

/** All mutable gesture state stays on the main thread; only tick invalidates Canvas drawing. */
internal class PinataSmashRewardInteraction {
    var driver = PinataSmashRewardDriver()
        private set
    val tick = mutableIntStateOf(0)
    val awake = mutableStateOf(true)
    var clock = 0.0
    private var rmN = 0
    private var rmAt = -10.0
    private var rmBurstAt = -10.0
    private var startX = 0.0; private var startY = 0.0; private var startT = 0.0
    private var on = false; private var dragged = false; private var holding = false
    fun resetDriver() { driver = PinataSmashRewardDriver(); awake.value = true; tick.intValue++ }
    fun begin(x: Double, y: Double, geo: PinataSmashRewardGeo, reduced: Boolean) {
        val d = driver; val drawn = d.lastAx != 0.0 || d.lastAy != 0.0
        val ox = if (drawn) d.lastAx - geo.attach.x else 0.0; val oy = if (drawn) d.lastAy - geo.attach.y else 0.0
        on = x >= 49 + ox - 16 && x <= 49 + 286 + ox + 16 && y >= 227 + 56 + oy - 16 && y <= 227 + 56 + 303 + oy + 16
        startX = x; startY = y; startT = clock; dragged = false
        holding = on
        if (on) awake.value = true
        if (reduced || !on) return
        PinataSmashRewardDriverMotion.takeOver(d); d.last = d.t; d.pressAim = 1.0; awake.value = true
    }
    fun move(x: Double, y: Double, reduced: Boolean) {
        if (reduced || !on) return
        val d = driver; d.last = d.t
        if (!dragged && hypot(x - startX, y - startY) > 8) { dragged = true; d.drag = true; d.pressAim = 0.0; d.gx = d.ox - startX; d.gy = d.oy - startY }
        if (d.drag) { d.fx = x; d.fy = y }
    }
    fun end(vx: Double, vy: Double, geo: PinataSmashRewardGeo, reduced: Boolean): Int {
        val d = driver; d.pressAim = 0.0; holding = false
        if (!on) return 0
        if (dragged) {
            val v = hypot(vx, vy); val k = if (v > 520) 520 / v else 1.0
            d.drag = false; d.vx = vx * k; d.vy = vy * k; d.last = d.t; return 0
        }
        if (clock - startT > 0.35) return 0
        if (reduced) {
            if (rmBurstAt > -10) return 0
            rmN++; rmAt = clock; if (rmN >= 12) rmBurstAt = clock
            awake.value = true; tick.intValue++
            return if (rmN >= 12) 3 else 1
        }
        return PinataSmashRewardDriverMotion.tapAt(d, startX, startY, geo)
    }
    fun step(dt: Double, auto: Boolean, reduced: Boolean): Boolean {
        clock += dt
        if (reduced) {
            tick.intValue++
            return holding || clock - rmAt < 0.4 || clock - rmBurstAt < 0.4
        }
        PinataSmashRewardDriverMotion.stepDriver(driver, dt, auto); tick.intValue++
        return auto || driver.mode == 1 || driver.press > 0.0001 || driver.blendT >= 0
    }
    fun frame(geo: PinataSmashRewardGeo, reduced: Boolean): PinataSmashRewardFrame {
        val d = driver
        if (reduced) {
            val f = PinataSmashRewardMotion.frameAt(0.0, PinataSmashRewardStory(), geo, 0.0)
            val n = min(rmN, 12); val done = rmBurstAt > -10
            f.fill = n / 12.0; f.wL = if (n == 0) 1.0 else 0.0; f.wB = if (n > 0 && !done) 1.0 else 0.0; f.wD = if (done) 1.0 else 0.0
            f.pillW = if (done) 172.5 else if (n > 0) 120.0 else 186.0
            f.bloom = 0.25 * (1 - PinataSmashRewardMotion.clamp01((clock - rmAt) / 0.12)); f.bloomX = geo.body.x; f.bloomY = geo.body.y
            if (done) {
                val k = PinataSmashRewardMotion.easeOutCubic(PinataSmashRewardMotion.clamp01((clock - rmBurstAt) / 0.25)); f.burst = k; f.doneText = k
                f.loot += PinataSmashRewardLoot(0, 120.0, 820.0, 0.3, a = k)
                f.loot += PinataSmashRewardLoot(4, 170.0, 826.0, -0.2, a = k)
                f.loot += PinataSmashRewardLoot(1, 214.0, 818.0, -0.4, a = k)
                f.loot += PinataSmashRewardLoot(3, 256.0, 828.0, 0.0, a = k)
                f.loot += PinataSmashRewardLoot(2, 292.0, 812.0, 0.5, a = k)
                f.loot += PinataSmashRewardLoot(5, 330.0, 828.0, 0.0, a = k)
            }
            d.lastAx = f.ax; d.lastAy = f.ay
            return f
        }
        val live = d.mode == 1; val t = if (live) d.t else d.loopT
        val f = PinataSmashRewardMotion.frameAt(t, if (live) d.story else PinataSmashRewardMotion.SCRIPT_STORY, geo, t)
        if (d.blendPending) { d.blendRoll = d.lastRoll - f.roll; d.blendX = d.lastAx - f.ax; d.blendY = d.lastAy - f.ay; d.blendPending = false }
        PinataSmashRewardDriverMotion.applyDriver(f, d)
        d.lastAx = f.ax; d.lastAy = f.ay; d.lastRoll = f.roll
        return f
    }
}
