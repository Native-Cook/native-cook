package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import kotlin.math.*

/** Gesture bookkeeping mirrors the reference's UI-thread pan callback order. */
internal class GlassPayMorphTouch(val driver: GlassPayMorphDriver) {
    private val motion = GlassPayMorphMotion
    private var p0 = 0.0
    private var p = 0.0
    private var onCard = false
    private var tx0 = 0.0
    private var ty0 = 0.0

    private fun hitCard(x: Double, y: Double): Boolean {
        val s = motion.sceneAt(driver.time(), driver.mods())
        val m = motion.cardInverse(s)
        val z = m[6] * x + m[7] * y + m[8]
        val lx = (m[0] * x + m[1] * y + m[2]) / z
        val ly = (m[3] * x + m[4] * y + m[5]) / z
        return abs(lx) < s.w / 2 + 10 && abs(ly) < s.h / 2 + 10
    }

    private fun hitLight(x: Double, y: Double): Int {
        val s = motion.sceneAt(driver.time(), driver.mods())
        if (hypot(x - s.a[0], y - s.a[1]) < s.a[2]) return 3
        if (hypot(x - s.rd[0], y - s.rd[1]) < s.rd[2]) return 4
        return 0
    }

    /** Returns true only when a touch presses the glass. */
    fun begin(x: Double, y: Double, now: Double): Boolean {
        driver.takeOver(now)
        onCard = hitCard(x, y)
        val light = if (onCard) 0 else hitLight(x, y)
        p0 = motion.progressOf(driver.tau)
        p = p0
        tx0 = 0.0
        ty0 = 0.0
        driver.held = if (light != 0) light else 5
        if (onCard) driver.pressTarget = GlassPayMorphDriver.PRESS_IN
        return onCard
    }

    fun move(tx: Double, ty: Double, vx: Double, vy: Double, now: Double) {
        driver.last = now
        if (driver.held == 3 || driver.held == 4) {
            val offset = if (driver.held == 3) driver.aOff else driver.rOff
            offset[0] = tx
            offset[1] = ty
            offset[2] = vx
            offset[3] = vy
            return
        }
        if (driver.held == 5 && hypot(tx, ty) > GlassPayMorphDriver.AXIS_LOCK) {
            val t = driver.tau
            val midExpand = t > motion.EXPAND0 && t < motion.EXPAND1
            if (abs(ty) >= abs(tx) && !midExpand) {
                driver.held = 1
                p0 = motion.progressOf(t)
                p = p0
                ty0 = ty
                driver.pressTarget = 1.0
            } else if (onCard && driver.isRest(t) && abs(tx) > abs(ty)) {
                driver.held = 2
                tx0 = tx
                driver.pressTarget = 1.0
            } else {
                driver.held = 6
                driver.pressTarget = 1.0
            }
            return
        }
        if (driver.held == 1) {
            p = driver.scrubTo(p0, ty - ty0)
        } else if (driver.held == 2) {
            val over = (tx - tx0) * GlassPayMorphDriver.YAW_PER_PT / GlassPayMorphDriver.YAW_MAX
            val sign = if (over < 0) -1 else 1
            val a = abs(over)
            val y = if (a <= 1) a else 1 + (a - 1) / (1 + (a - 1) * 3)
            driver.yaw = sign * y * GlassPayMorphDriver.YAW_MAX
            driver.yawV = 0.0
        }
    }

    fun end(vx: Double, vy: Double, now: Double) {
        driver.last = now
        driver.pressTarget = 1.0
        val held = driver.held
        driver.held = 0
        if (held == 5) {
            if (onCard) driver.toggle()
        } else if (held == 1) driver.scrubEnd(p, vy)
        else if (held == 2) driver.yawV = vx * GlassPayMorphDriver.YAW_PER_PT
    }
}
