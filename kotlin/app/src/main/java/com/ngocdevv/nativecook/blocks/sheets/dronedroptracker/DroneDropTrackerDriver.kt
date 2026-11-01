package com.ngocdevv.nativecook.blocks.sheets.dronedroptracker

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Mutable UI-thread state mirroring the RN shared-value worklet. */
internal class DroneDropTrackerDriver {
    var f = 0.0
    var drop = 0.0
    var carried = 1.0
    var back = 0.0
    var sheet = 0.0
    var sheetVelocity = 0.0
    var sheetTarget = 0.0
    var draggingSheet = false
    var scrubbingMap = false
    var sheetStart = 0.0
    var scrubStart = 0.0
    var fStart = 0.0
    var lastTouch = -100.0
    var scripted = false
    var loopTime = 0.0
    var delivered = false
    var clock = 0.0
    var justDelivered = false

    fun frame(t: Double = clock, live: Boolean = true) = DroneDropTrackerMotion.Frame(
        f = f,
        drop = drop,
        carried = carried,
        back = back,
        sheet = sheet,
        t = t,
        live = live,
    )

    /** Advance the physics with a .05 s Euler step; autoplay wall time is capped independently at .5 s. */
    fun tick(rawWall: Double, autoPlay: Boolean): DroneDropTrackerMotion.Frame {
        justDelivered = false
        val wall = max(0.0, rawWall)
        val physicsDt = min(wall, 0.05)
        val autoplayWall = min(wall, 0.5)
        clock += wall
        if (
            autoPlay &&
            !draggingSheet &&
            !scrubbingMap &&
            clock - lastTouch > 3.5 &&
            (scripted || (f < 0.001 && back == 0.0))
        ) {
            if (!scripted) {
                scripted = true
                loopTime = 0.0
            }
            loopTime = (loopTime + autoplayWall) % DroneDropTrackerMotion.LOOP
            val script = DroneDropTrackerMotion.scriptAt(loopTime)
            f = script.f
            drop = script.drop
            carried = script.carried
            back = script.back
            sheet = script.sheet
            sheetTarget = if (script.sheet > 0.5) 1.0 else 0.0
            return DroneDropTrackerMotion.Frame(
                f = script.f,
                drop = script.drop,
                carried = script.carried,
                back = script.back,
                sheet = script.sheet,
                t = clock,
                live = false,
            )
        }

        scripted = false
        if (!draggingSheet) {
            val stiffness = 180.0
            val damping = 2.0 * 0.9 * sqrt(stiffness)
            sheetVelocity += (stiffness * (sheetTarget - sheet) - damping * sheetVelocity) * physicsDt
            // Keep the RN worklet's unclamped Euler integration; the spring settles back to its target.
            sheet += sheetVelocity * physicsDt
        }
        if (!scrubbingMap && back == 0.0 && f < 1.0) {
            f = min(1.0, f + physicsDt / 30.0)
        }
        if (f >= 1.0 && drop < 1.0 && !scrubbingMap) {
            drop = min(1.0, drop + physicsDt / 1.1)
            if (drop >= 1.0 && !delivered) {
                delivered = true
                carried = 0.0
                justDelivered = true
            }
        }
        return frame()
    }

    fun beginSheetDrag() {
        draggingSheet = true
        scripted = false
        sheetStart = sheet
        sheetVelocity = 0.0
    }

    fun updateSheetDrag(translationY: Double, range: Double) {
        if (!draggingSheet) return
        sheet = DroneDropTrackerMotion.clamp01(sheetStart - translationY / range.coerceAtLeast(1.0))
        lastTouch = clock
    }

    fun finishSheetDrag(velocityY: Double, range: Double) {
        if (!draggingSheet) return
        draggingSheet = false
        sheetTarget = when {
            velocityY < -500.0 -> 1.0
            velocityY > 500.0 -> 0.0
            sheet > 0.5 -> 1.0
            else -> 0.0
        }
        sheetVelocity = -velocityY / range.coerceAtLeast(1.0)
        lastTouch = clock
    }

    fun beginMapScrub() {
        scrubbingMap = true
        scripted = false
        fStart = f
        back = 0.0
        carried = 1.0
        drop = 0.0
        delivered = false
    }

    fun updateMapScrub(translationX: Double, width: Double) {
        if (!scrubbingMap) return
        f = DroneDropTrackerMotion.clamp01(fStart + translationX / (width * 1.2).coerceAtLeast(1.0))
        lastTouch = clock
    }

    fun finishMapScrub() {
        scrubbingMap = false
        lastTouch = clock
    }
}
