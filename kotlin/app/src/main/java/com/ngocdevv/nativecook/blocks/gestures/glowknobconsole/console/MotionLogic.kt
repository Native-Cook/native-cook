// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Pure rules behind the console's motion and readout. No Android imports, so
// they are unit-tested on the JVM.

private fun clamp(value: Double, lo: Double, hi: Double) = max(lo, min(hi, value))

/** Width, in drawing units, of the right-aligned "NN%" glyph run. */
fun glyphRun(value: Double, size: Double, advance: Double = 0.62): Double {
    val digits = if (value >= 100) 3 else if (value >= 10) 2 else 1
    return (digits + 1) * advance * size
}

/** Share (0…1) of a right-aligned glyph run that the light's edge covers. */
fun runCoverage(edge: Double, boxRight: Double, run: Double): Double {
    if (run <= 0) return 0.0
    return clamp((edge - (boxRight - run)) / run, 0.0, 1.0)
}

/**
 * Whether the readout should be dark (1) or light (0). Hysteresis keeps it
 * from flickering while the light's edge rests inside the number: it turns
 * dark above `on` and only turns light again below `off`.
 */
fun inkTarget(coverage: Double, dark: Boolean, on: Double = 0.5, off: Double = 0.4): Int =
    if (dark) (if (coverage < off) 0 else 1) else (if (coverage > on) 1 else 0)

/**
 * Whether a readout step should roll. A channel swap never rolls (it happens
 * under the fade). While dragging, a step that changes the tens or hundreds
 * digit is applied instantly, since two digits rolling at once read as blur.
 */
fun rollsOnStep(previous: Int?, next: Int, dragging: Boolean, inPlace: Boolean): Boolean {
    if (!inPlace) return false
    if (!dragging || previous == null) return true
    return floor(next / 10.0) == floor(previous / 10.0)
}

/** Meniscus target: how far the leading edge leans for a given light speed. */
fun sloshTarget(speed: Double) = clamp(speed * 0.02, -18.0, 24.0)

data class SpringState(val position: Double, val velocity: Double)

/**
 * One semi-implicit Euler step of the meniscus spring. The step is clamped
 * to 50ms so a hitch (or a backgrounded frame) cannot blow the spring up.
 */
fun springStep(
    position: Double,
    velocity: Double,
    target: Double,
    dt: Double,
    stiffness: Double = 360.0,
    damping: Double = 16.0,
): SpringState {
    val h = clamp(dt, 0.0, 0.05)
    val acceleration = stiffness * (target - position) - damping * velocity
    val v = velocity + acceleration * h
    return SpringState(position + v * h, v)
}

/** True once the meniscus spring is visually at rest. */
fun springSettled(position: Double, velocity: Double) = abs(position) < 0.05 && abs(velocity) < 0.5

/**
 * Lifted console released with `velocity` (rad/s): the half turn it comes
 * to rest on (0 = face, ±1 = back, ±2 = face again…). The flick projects the
 * yaw forward by `reach` seconds before rounding to the nearest face.
 */
fun restingHalfTurn(yaw: Double, velocity: Double, reach: Double = 0.2): Int =
    jsRound((yaw + velocity * reach) / PI).toInt()

/** Yaw to set the console down at: the nearest full turn, face up. */
fun faceUpYaw(yaw: Double) = jsRound(yaw / (PI * 2)) * PI * 2
