package com.ngocdevv.nativecook.blocks.modals.tasktoastconfetti

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// The timeline of the screen, in seconds from the start of the loop.
internal const val LOOP = 9f

/** A touch starts here, a little before the tick. */
internal const val TAP_AT = 0.95f

/** Seconds a touch is left alone before the autoPlay loop starts again. */
private const val RESUME_AFTER = 3f

internal const val TICK_AT = 1.1f
internal const val TICK_IN = 0.3f
internal const val STRIKE_IN = 0.28f
internal const val HINT_OUT_AT = 1.1f
internal const val HINT_OUT_IN = 0.25f
internal const val NEXT_AT = 1.5f
internal const val TOAST_AT = 2.0f

/** The confetti bursts out of the toast as it lands. */
internal const val CONFETTI_AT = 2.12f

internal const val TOAST_AWAY_AT = 5.8f
internal const val TOAST_AWAY_IN = 0.8f
internal const val NEXT_AWAY_AT = 5.8f
internal const val NEXT_AWAY_IN = 0.6f
internal const val UNTICK_AT = 6.6f
internal const val UNTICK_IN = 0.5f
internal const val HINT_BACK_AT = 7.0f
internal const val HINT_BACK_IN = 0.45f

/** The first hint bobs by this much, once every period; the loop holds a whole number of periods. */
internal const val BOB = 3f
internal const val BOB_PERIOD = 0.9f

/** The toast drops on a spring; the next hint and the mascot pop on a softer one. */
internal const val TOAST_STIFFNESS = 300f
internal const val TOAST_DAMPING = 18f
internal const val POP_STIFFNESS = 260f
internal const val POP_DAMPING = 15f
internal const val REDUCED_FADE = 0.3f

/** 0 before [from], 1 after [to], a straight line between. */
internal fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

internal fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

internal fun easeIn(p: Float): Float = p * p * p

/** A spring's position in closed form, from 0 at `elapsed` = 0 towards 1, for a mass of 1. */
internal fun spring(elapsed: Float, stiffness: Float, damping: Float): Float {
    if (elapsed <= 0f) return 0f
    val omega = sqrt(stiffness)
    val zeta = damping / (2f * omega)
    val decay = exp(-zeta * omega * elapsed)
    val damped = omega * sqrt(1f - zeta * zeta)
    return 1f - decay * (cos(damped * elapsed) + (zeta * omega) / damped * sin(damped * elapsed))
}

/** A number in [0, 1) that is always the same for the same pair: the confetti table is built from it. */
internal fun hash(index: Int, salt: Int): Double {
    val x = sin(index * 127.1 + salt * 311.7) * 43758.5453
    return x - floor(x)
}

/**
 * The screen's clock, in seconds from 0 to [LOOP]. With autoPlay it runs by itself; [touch] (a
 * touch) takes over, jumps to [TAP_AT], runs to the end and rests there; autoPlay then starts
 * again after a pause. The state at 0 and at [LOOP] is the same.
 */
internal class LoopClock(val seconds: Animatable<Float, *>, val touch: () -> Boolean)

@Composable
internal fun rememberLoopClock(autoPlay: Boolean): LoopClock {
    val seconds = remember { Animatable(0f) }
    var touches by remember { mutableIntStateOf(0) }
    val busyUntil = remember { longArrayOf(0L) }

    LaunchedEffect(autoPlay, touches) {
        if (touches > 0) {
            // The run a touch started: from the tap to the end, then at rest.
            seconds.snapTo(TAP_AT)
            seconds.animateTo(LOOP, tween(((LOOP - TAP_AT) * 1000).toInt(), easing = LinearEasing))
            seconds.snapTo(0f)
            if (autoPlay) delay((RESUME_AFTER * 1000).toLong())
        }
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            seconds.snapTo(0f)
            seconds.animateTo(LOOP, tween((LOOP * 1000).toInt(), easing = LinearEasing))
        }
    }

    return remember(seconds) {
        LoopClock(seconds) {
            val now = System.nanoTime()
            if (now < busyUntil[0]) {
                false
            } else {
                busyUntil[0] = now + ((LOOP - TAP_AT) * 1e9).toLong()
                touches++
                true
            }
        }
    }
}
