package com.ngocdevv.nativecook.blocks.modals.bannergrowcard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The timeline of the screen, in seconds from the start of the loop.
internal const val LOOP = 9f

/** A touch starts here. */
internal const val TAP_AT = 0.7f

/** Seconds a touch is left alone before the autoPlay loop starts again. */
private const val RESUME_AFTER = 3f

/** 0 before [from], 1 after [to], a straight line between. */
internal fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

internal fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

internal fun easeIn(p: Float): Float = p * p * p

internal fun easeInOut(p: Float): Float = if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).pow(3) / 2f

/** A spring's position in closed form, from 0 at `elapsed` = 0 towards 1, for a mass of 1. */
internal fun spring(elapsed: Float, stiffness: Float, damping: Float): Float {
    if (elapsed <= 0f) return 0f
    val omega = sqrt(stiffness)
    val zeta = damping / (2f * omega)
    val decay = exp(-zeta * omega * elapsed)
    val damped = omega * sqrt(1f - zeta * zeta)
    return 1f - decay * (cos(damped * elapsed) + (zeta * omega) / damped * sin(damped * elapsed))
}

/**
 * The screen's clock, in seconds from 0 to [LOOP]. With autoPlay it runs by itself; [touch] (a
 * touch) takes over, jumps to a time of the loop, runs to the end and rests there; autoPlay then
 * starts again after a pause. The state at 0 and at [LOOP] is the same.
 */
internal class LoopClock(val seconds: Animatable<Float, *>, val touch: (from: Float, force: Boolean) -> Boolean)

private class Run(val from: Float)

@Composable
internal fun rememberLoopClock(autoPlay: Boolean): LoopClock {
    val seconds = remember { Animatable(0f) }
    var run by remember { mutableStateOf<Run?>(null) }
    val busyUntil = remember { longArrayOf(0L) }

    LaunchedEffect(autoPlay, run) {
        run?.let {
            // The run a touch started: from its time to the end, then at rest.
            seconds.snapTo(it.from)
            seconds.animateTo(LOOP, tween(((LOOP - it.from) * 1000).toInt(), easing = LinearEasing))
            seconds.snapTo(0f)
            if (autoPlay) delay((RESUME_AFTER * 1000).toLong()) else return@LaunchedEffect
        }
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            seconds.snapTo(0f)
            seconds.animateTo(LOOP, tween((LOOP * 1000).toInt(), easing = LinearEasing))
        }
    }

    return remember(seconds) {
        LoopClock(seconds) { from, force ->
            val now = System.nanoTime()
            if (!force && now < busyUntil[0]) {
                false
            } else {
                busyUntil[0] = now + ((LOOP - from) * 1e9).toLong()
                run = Run(from)
                true
            }
        }
    }
}

internal const val OUTLINE_AT = 0.8f
internal const val OUTLINE_IN = 0.5f
internal const val BANNER_AT = 1.3f
internal const val BANNER_IN = 0.6f
internal const val WARM_AT = 1.9f
internal const val WARM_IN = 0.8f
internal const val GROW_AT = 2.7f
internal const val LINES_OUT_AT = 2.9f
internal const val LINES_OUT_IN = 0.25f
internal const val CAPTION_AT = 3.35f
internal const val CAPTION_IN = 0.45f
internal const val BREATHE_AT = 3.9f
internal const val BREATHE_FOR = 2.2f
internal const val COLLAPSE_AT = 6.1f
internal const val COLLAPSE_IN = 0.8f
internal const val BANNER_OUT_AT = 6.9f
internal const val BANNER_OUT_IN = 0.5f
internal const val OUTLINE_OUT_AT = 7.0f
internal const val OUTLINE_OUT_IN = 0.6f
internal const val REDUCED_FADE = 0.3f

/** The card grows on a spring with a damping ratio of 0.9: 2 * 0.9 * sqrt(stiffness). */
internal const val GROW_STIFFNESS = 110f
internal const val GROW_DAMPING = 18.9f
internal const val BREATHE = 0.02f

/** The photo shows through the warming banner this much before the card grows. */
internal const val PEEK = 0.4f
