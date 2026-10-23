package com.ngocdevv.nativecook.blocks.modals.leveluptakeover

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
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The timeline of the screen, in seconds from the start of the loop.
internal const val LOOP = 9f

/** A touch starts here. */
internal const val TAP_AT = 0.5f

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

/** A number in [0, 1) that is always the same for the same pair: the particle tables are built from it. */
internal fun hash(index: Int, salt: Int): Double {
    val x = sin(index * 127.1 + salt * 311.7) * 43758.5453
    return x - floor(x)
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

internal const val BURST_AT = 0.6f
internal const val DOT_OUT_IN = 0.2f
internal const val GLOW_AT = 1.0f
internal const val GLOW_FOR = 0.6f
internal const val TITLE_AT = 1.5f
internal const val TITLE_IN = 0.4f
internal const val STATS_AT = 1.9f
internal const val STATS_COUNT = 0.6f
internal const val STATS_STAGGER = 0.1f
internal const val CTA_AT = 2.5f
internal const val LINK_AT = 3.1f
internal const val LINK_IN = 0.4f
internal const val SHINE_AT = 3.3f
internal const val SHINE_FOR = 0.6f
internal const val SECOND_BURST_AT = 4.0f
internal const val SECOND_BURST_COUNT = 18
internal const val PRESS_AT = 6.0f
internal const val PRESS_DOWN = 0.15f
internal const val PRESS_UP = 0.25f
internal const val OUT_AT = 7.0f
internal const val OUT_IN = 1.0f
internal const val DOT_BACK_AT = 8.2f
internal const val DOT_BACK_IN = 0.4f
internal const val REDUCED_FADE = 0.3f

/** The badge bursts out on a bouncy spring: damping ratio 0.55 is 2 * 0.55 * sqrt(stiffness). */
internal const val BADGE_STIFFNESS = 220f
internal const val BADGE_DAMPING = 16.3f
internal const val BADGE_FROM = 0.2f
internal const val CTA_STIFFNESS = 200f
internal const val CTA_DAMPING = 22f
internal const val SLIDE = 16f
internal const val CTA_SLIDE = 40f
internal const val OUT_SCALE = 0.96f
internal const val PRESS_SCALE = 0.96f
