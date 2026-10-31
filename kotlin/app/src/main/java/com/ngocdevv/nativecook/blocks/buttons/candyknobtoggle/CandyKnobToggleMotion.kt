package com.ngocdevv.nativecook.blocks.buttons.candyknobtoggle

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Seconds; the first and last frame are the same on/rest state. */
const val LOOP = 6.0
internal const val CANDY_TRAVEL = 172.0
private const val SNAP = 0.8
private const val HOLD = 2.0

internal data class CandyKnobToggleFrame(
    val position: Double = 1.0,
    val moving: Double = 0.0,
    val squash: Double = 0.0,
)

internal fun candyClamp(x: Double) = x.coerceIn(0.0, 1.0)
internal fun candySegment(t: Double, a: Double, b: Double) = candyClamp((t - a) / (b - a))
internal fun candyCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun candyLand(x: Double) = if (x <= 0 || x > 0.6) 0.0 else 0.1 * exp(-8 * x) * sin(18 * x)

internal fun candyKnobAt(time: Double): CandyKnobToggleFrame {
    val t = ((time % LOOP) + LOOP) % LOOP
    val left = candyCubic(candySegment(t, 1.2, 2.0))
    val right = candyCubic(candySegment(t, 4.2, 5.0))
    val moving = when {
        t >= 1.2 && t < 2.0 -> candySegment(t, 1.2, 2.0)
        t >= 4.2 && t < 5.0 -> candySegment(t, 4.2, 5.0)
        else -> 0.0
    }
    return CandyKnobToggleFrame(1 - left + right, moving, candyLand(t - 2) + candyLand(t - 5))
}

/** The RN gesture and frame state machine, using time that stops when the app is inactive. */
internal class CandyKnobToggleMotion {
    var elapsed = 0.0
        private set
    var frame = CandyKnobToggleFrame()
        private set
    var touching = false
        private set
    var manual = false
        private set
    private var origin = 0.0
    private var released = -1.0
    private var from = 1.0
    private var target = 1.0

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching) {
            frame = frame.copy(squash = 0.0)
            return
        }
        if (manual) {
            val since = elapsed - released
            val p = candyClamp(since / SNAP)
            frame = CandyKnobToggleFrame(
                from + (target - from) * candyCubic(p),
                if (abs(target - from) > 0.05 && p < 1) p else 0.0,
                candyLand(since - SNAP) * minOf(1.0, abs(target - from) * 2),
            )
            if (since > SNAP + HOLD) {
                manual = false
                origin = if (target > 0.5) elapsed else elapsed - 2.4
                if (!autoPlay) frame = CandyKnobToggleFrame()
            }
            return
        }
        frame = if (autoPlay) candyKnobAt(elapsed - origin) else CandyKnobToggleFrame()
    }

    fun drag(position: Double) {
        touching = true
        manual = true
        frame = CandyKnobToggleFrame(candyClamp(position))
    }

    fun release(to: Double) {
        touching = false
        manual = true
        from = frame.position
        target = to
        released = elapsed
    }
}
