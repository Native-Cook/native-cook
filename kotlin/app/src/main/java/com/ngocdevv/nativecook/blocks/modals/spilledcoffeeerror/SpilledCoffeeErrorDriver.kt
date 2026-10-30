package com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.T
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.LOOP
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.RESUME
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.Pose
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.poseAt
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.retryPose
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.mix
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.easeOut
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.smooth
import com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror.SpilledCoffeeErrorMotion.clamp01

/** Mutable touch state owned by the UI thread. A grab freezes the shared retry script in place. */
internal class SpilledCoffeeErrorDriver(var t: Double = 0.0) {
    var mode = 0
    var last = -10.0
    var amb = t
    var theta = 90.0
    var thetaV = 0.0
    var dragging = 0
    var dragTheta = 90.0
    var spill = 1.0
    var lid = 1.0
    var lidFrom = 1.0
    var lidAt = -10.0
    var lidTo = 1.0
    var hopAt = -10.0
    var retryAt = -10.0
    var r0 = doubleArrayOf(90.0, 1.0, 1.0)
    var splashAt = -10.0
    var pressAt = -10.0
    var pressing = 0
    fun takeOver(now: Double) {
        last = now
        if (mode == 2) return
        mode = 2
        if (t >= T.press && t < LOOP - 0.3) {
            retryAt = now - (t - T.press)
            r0 = doubleArrayOf(90.0, 1.0, 1.0)
        }
        else retryAt = -10.0
        val p = poseAt(t)
        theta = p.theta
        thetaV = 0.0
        spill = p.spill
        lid = p.lid
        lidTo = if (p.lid > 0.5) 1.0 else 0.0
        lidFrom = lid
        lidAt = now - 1
    }

    private fun freeze(now: Double) {
        if (retryAt < 0) return
        val p = retryPose(now - retryAt, r0[0], r0[1], r0[2], amb)
        theta = p.theta
        spill = p.spill
        lid = p.lid
        lidFrom = p.lid
        lidTo = if (p.lid > 0.5) 1.0 else 0.0
        lidAt = now
        retryAt = -10.0
    }

    fun grabCup(now: Double) {
        freeze(now)
        dragging = 1
        dragTheta = theta
    }

    fun retry(now: Double) {
        freeze(now)
        retryAt = now - 0.02
        r0 = doubleArrayOf(theta, spill, lid)
    }

    fun step(dt: Double, now: Double, auto: Boolean) {
        amb = (amb + dt) % LOOP
        if (mode == 0) {
            if (auto) t = (t + dt) % LOOP
            return
        }

        if (retryAt > 0) {
            val tau = now - retryAt
            if (tau >= LOOP - T.press - 0.2) {
                val p = retryPose(tau, r0[0], r0[1], r0[2], amb)
                theta = p.theta
                spill = p.spill
                lid = 1.0
                lidTo = 1.0
                lidFrom = 1.0
                retryAt = -10.0
            }

            return
        }

        if (dragging == 1) {
            theta += (dragTheta - theta) * min(1.0, dt * 20)
            thetaV = 0.0
        }
        else {
            val target = if (theta > 45) 90.0 else 0.0
            val k = if (target == 90.0) 110.0 else 90.0
            val c = 2 * (if (target == 90.0) 0.35 else 0.5) * sqrt(k)
            thetaV += (-k * (theta - target) - c * thetaV) * dt
            theta += thetaV * dt
            if (theta > 90) {
                theta = 90.0
                thetaV = -abs(thetaV) * 0.3
            }
        }

        if (theta > 55 && spill < 1) {
            val before = spill
            spill = min(1.0, spill + 1.1 * ((theta - 55) / 35) * dt)
            if (before == 0.0 && spill > 0) splashAt = now
        }

        if (theta > 60 && lidTo == 0.0) {
            lidFrom = lid
            lidTo = 1.0
            lidAt = now
        }

        lid = mix(lidFrom, lidTo, easeOut((now - lidAt) / 0.55))
        if (auto && dragging == 0 && now - last >= RESUME) {
            if (theta < 89.5 && theta <= 45) thetaV += 260 * dt
            if (theta > 89.4 && abs(thetaV) < 2 && spill > 0.999 && lid > 0.999 && now - splashAt > 1.2) {
                mode = 0
                t = 0.0
            }
        }
    }

    fun pose(now: Double): Pose {
        if (mode == 0) return poseAt(t)
        if (retryAt > 0) return retryPose(now - retryAt, r0[0], r0[1], r0[2], amb)
        val pour = if (theta > 55 && spill < 1) clamp01((theta - 55) / 20) else 0.0
        return Pose(amb, theta, spill, pour, lid, if (now - hopAt < 0.7) now - hopAt else -1.0, 0.0, 0.0, 0.0, 0.0,
        if (pressing == 1) smooth((now - pressAt) / 0.1) else 0.0, if (now - splashAt < 1.4) now - splashAt else -1.0, -1.0)
    }
}
