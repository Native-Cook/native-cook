package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.mutableDoubleStateOf
import java.util.PriorityQueue

/*
 The React Native block runs on setTimeout and Reanimated withTiming. Here the same behaviour runs on
 one virtual clock, so any frame is a function of time: scheduled actions fire in order with their own
 scheduled time as "now", and every animated number is a FolioTween evaluated at the clock's time.
 With previewTime the clock is frozen and the scheduler is caught up to that time once.
*/

/**
 * The block's clock, in seconds. [now] is the time of the next frame's content; [frame] is the same time
 * as Compose state, read only inside draw, layout and graphicsLayer lambdas so they redraw every frame
 * without recomposing anything.
 */
internal class FolioFeatureLibraryClock(private val frozenAt: Double?) {
    private val origin = System.nanoTime()
    val frame = mutableDoubleStateOf(frozenAt ?: 0.0)

    /** True for a `previewTime` still: the clock never moves and touch is off. */
    val frozen: Boolean = frozenAt != null

    /** Seconds since the block appeared, or the frozen preview time. */
    fun now(): Double = frozenAt ?: ((System.nanoTime() - origin) / 1_000_000_000.0)

    /** Publishes the current time to the draw phase; called once a frame. */
    fun publish() {
        frame.doubleValue = now()
    }
}

/**
 * One animated number, the equivalent of a Reanimated shared value driven by `withTiming`.
 * [animateTo] starts from wherever the value is at the start time, like a retargeted timing animation.
 */
internal class FolioTween(initial: Float = 0f) {
    private var from = initial
    private var to = initial
    private var startAt = 0.0
    private var duration = 0.0
    private var easing: Easing = LinearEasing

    /** Where the tween is heading. */
    val target: Float get() = to

    fun value(now: Double): Float {
        if (duration <= 0.0 || now >= startAt + duration) return to
        if (now <= startAt) return from
        val fraction = ((now - startAt) / duration).toFloat()
        return from + (to - from) * easing.transform(fraction)
    }

    fun set(value: Float) {
        from = value
        to = value
        duration = 0.0
    }

    fun animateTo(target: Float, at: Double, seconds: Double, ease: Easing) {
        from = value(at)
        to = target
        startAt = at
        duration = seconds
        easing = ease
    }

    /** `cancelAnimation`: freeze at the current value. */
    fun cancel(at: Double) {
        set(value(at))
    }

    /** True while the value has not reached its target. */
    fun running(now: Double): Boolean = duration > 0.0 && now < startAt + duration
}

/** Reanimated's `interpolate(x, input, output)`, clamped or extended at both ends. */
internal fun folioInterpolate(x: Float, input: FloatArray, output: FloatArray, clamp: Boolean = true): Float {
    val last = input.size - 1
    if (x <= input[0]) {
        if (clamp) return output[0]
        return output[0] + (x - input[0]) * (output[1] - output[0]) / (input[1] - input[0])
    }
    if (x >= input[last]) {
        if (clamp) return output[last]
        return output[last] + (x - input[last]) * (output[last] - output[last - 1]) / (input[last] - input[last - 1])
    }
    var i = 0
    while (x > input[i + 1]) i++
    val u = (x - input[i]) / (input[i + 1] - input[i])
    return output[i] + (output[i + 1] - output[i]) * u
}

internal fun folioClamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

/** A cancellable entry of the [FolioFeatureLibraryScheduler]. */
internal class FolioTimer(val at: Double, val tag: Int, val sequence: Long, val action: () -> Unit) {
    var cancelled = false
}

/**
 * `setTimeout` on the virtual clock. Entries carry a tag: [TIMELINE] entries are the autoplay script
 * (dropped when autoplay stops), [TIMERS] entries belong to the visible state (operation deadlines, the
 * resume timer) and survive it.
 */
internal class FolioFeatureLibraryScheduler {
    private val queue = PriorityQueue<FolioTimer>(compareBy<FolioTimer>({ it.at }, { it.sequence }))
    private var sequence = 0L

    fun at(time: Double, tag: Int, action: () -> Unit): FolioTimer {
        val timer = FolioTimer(time, tag, sequence++, action)
        queue.add(timer)
        return timer
    }

    fun cancel(timer: FolioTimer?) {
        timer?.cancelled = true
    }

    fun cancelTag(tag: Int) {
        for (timer in queue) {
            if (timer.tag == tag) timer.cancelled = true
        }
    }

    /** Runs every entry due at or before [now], oldest first; [run] receives each entry's own time. */
    fun advanceTo(now: Double, run: (Double, () -> Unit) -> Unit) {
        while (true) {
            val next = queue.peek() ?: return
            if (next.at > now) return
            queue.poll()
            if (!next.cancelled) run(next.at, next.action)
        }
    }

    companion object {
        const val TIMELINE = 0
        const val TIMERS = 1
    }
}

/** Reanimated's default `withTiming` easing, `Easing.inOut(Easing.quad)`, which every `FadeIn` uses. */
internal val FolioQuadInOut = Easing { x ->
    if (x < 0.5f) {
        2f * x * x
    } else {
        val u = -2f * x + 2f
        1f - u * u / 2f
    }
}

/**
 * The opacity of a `FadeIn.duration(seconds).delay(delay)` entering animation that started at [since]:
 * 0 until the delay has passed, then in-out quad up to 1. Under Reduce Motion there is no animation.
 */
internal fun folioFadeIn(now: Double, since: Double, delay: Double, seconds: Double, reduced: Boolean): Float {
    if (reduced) return 1f
    return FolioQuadInOut.transform(folioClamp01(((now - since - delay) / seconds).toFloat()))
}
