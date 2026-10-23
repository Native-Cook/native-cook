package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.stepSimulation
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.close
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.reveal
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.touchDown
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.release
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.tapAt
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.pressShare
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.travel
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.dragTo
import kotlin.math.abs

/** Fixed 120 Hz integration, splitting precisely at the original scripted beats. */
internal object CrystalTrophyPerformance {
    private val beats = doubleArrayOf(0.7, 2.1, 2.8, 7.4, 8.1, 9.3, 9.6, 12.0, 12.8, 12.95, 15.0)

    fun advance(previous: CrystalTrophyState, elapsed: Double, width: Double): CrystalTrophyState {
        var s = previous
        var remaining = elapsed.coerceIn(0.0, 1.0 / 30)
        repeat(8) {
            if (remaining < 1e-9) return@repeat
            var dt = minOf(remaining, 1.0 / 120)
            val scripted = s.auto && !s.reduced && !s.paused
            var beat = -1.0
            if (scripted) {
                for (candidate in beats) {
                    val gap = candidate - s.demoTime
                    if (gap > 1e-8 && gap <= dt + 1e-8) {
                        dt = gap
                        beat = candidate
                        break
                    }
                }
            }
            stepSimulation(s, dt)
            remaining -= dt
            if (scripted) {
                s.demoTime += dt
                drag(s, width)
                when (beat) {
                    0.7 -> close(s)
                    2.8 -> reveal(s)
                    7.4 -> touchDown(s, 0.20, 2.05)
                    9.6 -> release(s, 1500.0)
                    12.0 -> tapAt(s, -0.48, 2.15)
                    12.8 -> pressShare(s, true)
                    12.95 -> pressShare(s, false, true)
                }
                if (s.demoTime >= 15 - 1e-8) s = reset(s)
            }
            if (s.auto && s.paused && !s.realDown && s.clock - s.lastInput >= 3.5) {
                if (!s.recovering) {
                    s.recovering = true
                    s.down = false
                    s.spin = false
                    s.releaseAge = 0.0
                    s.releaseYaw = s.yaw
                    if (s.phase == "closed") reveal(s)
                }
                if (s.phase == "closed") reveal(s)
                if (s.phase == "open" && abs(s.yaw) < 0.002 && s.flash == 0.0 && s.copied == 0.0 && s.touch == 0.0) {
                    s = reset(s)
                }
            }
        }
        return s
    }

    private fun reset(s: CrystalTrophyState): CrystalTrophyState {
        return CrystalTrophyState(true, s.reduced).also {
            it.clock = s.clock
            it.lastInput = s.clock
            it.events = s.events
        }
    }

    private fun drag(s: CrystalTrophyState, width: Double) {
        val t = s.demoTime
        if (t < 7.4 || t >= 9.6) return
        val angle = when {
            t < 8.1 -> 0.52 * travel((t - 7.4) / 0.7)
            t < 9.3 -> 0.52 - 1.04 * travel((t - 8.1) / 1.0)
            else -> -0.52 + 1.04 * travel((t - 9.3) / 0.3)
        }
        dragTo(s, angle / 0.0062, 0.0, 0.20 + angle * 0.55, 2.05 + angle * 0.19, width)
    }
}
