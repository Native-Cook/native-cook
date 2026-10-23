// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim

import androidx.compose.runtime.BroadcastFrameClock
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Perf
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The UI-thread runtime Reanimated provides, reduced to what the console
 * needs: shared values animated by Compose `Animatable`s, frame callbacks
 * (`useFrameCallback`) and reactions (`useAnimatedReaction`) that run after
 * every value of the frame has advanced, with the previous input.
 *
 * One `withFrameNanos` loop on the composition's frame clock drives a frame:
 *  1. ported steppers (`withDelay` timers, `withDecay`),
 *  2. every running `Animatable` (they await [frameClock], a
 *     `BroadcastFrameClock` fed from that loop, on an unconfined dispatcher,
 *     so they all advance inside the frame and completion callbacks fire
 *     there, as Reanimated's do),
 *  3. frame callbacks, then reactions.
 *
 * Values are Compose snapshot state, so a draw lambda that reads one is
 * invalidated by the change alone: per-frame motion never recomposes.
 */
object AnimationEngine {
    /** Reanimated's `ReducedMotionManager` value (ReduceMotion.System). */
    @Volatile var systemReduceMotion: Boolean = false

    /** Monotonic clock (ms) for values set outside a frame; swappable in tests. */
    var clock: () -> Double = { System.nanoTime() / 1e6 }

    private var stepQueue = ArrayList<(Double) -> Unit>()
    private val frameCallbacks = LinkedHashSet<FrameCallback>()
    private val reactions = ArrayList<Reaction<*>>()
    private var dirty = false
    private var inFrame = false
    private var frameTimestamp = 0.0
    private var lastTimestamp = 0.0
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val frames = BroadcastFrameClock { requestFrame() }

    /**
     * Frames handed synchronously to an animation being started: Reanimated
     * runs `onStart(now)` and the first `onFrame(now)` inside the assignment,
     * so its timeline starts at `now` (or at the previous animation's last
     * frame), not at the next vsync.
     */
    private var immediate = ArrayDeque<Double>()

    /** Frame time (ms) of the frame an `Animatable` is currently being stepped to. */
    internal var deliveredMs = 0.0
        private set

    private val clockElement = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            currentCoroutineContext().ensureActive()
            val t = immediate.removeFirstOrNull()
            if (t != null) {
                deliveredMs = t
                return onFrame(nanos(t))
            }
            return frames.withFrameNanos(onFrame)
        }
    }

    /**
     * Where `Animatable`s run. Unconfined: a frame resumes each one in place,
     * within [frame], so values, completion callbacks and the reactions that
     * follow keep Reanimated's order. No `MotionDurationScale`: Reanimated
     * ignores the animator duration scale (Reduce Motion is handled apart).
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clockElement)

    private fun nanos(ms: Double): Long = (ms * 1_000_000.0).roundToLong()

    /** Reanimated's `global.__frameTimestamp || _getAnimationTimestamp()`, monotonic. */
    fun timestamp(): Double {
        if (inFrame) return frameTimestamp
        lastTimestamp = max(lastTimestamp, clock())
        return lastTimestamp
    }

    /**
     * Starts [block] (an `Animatable` run) now: it receives [startFrames]
     * (ms) synchronously, then awaits the engine's frames.
     */
    internal fun launchAt(startFrames: List<Double>, block: suspend CoroutineScope.() -> Unit): Job {
        val saved = immediate
        immediate = ArrayDeque(startFrames)
        try {
            return scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)
        } finally {
            immediate = saved
        }
    }

    internal fun requestAnimationFrame(step: (Double) -> Unit) {
        stepQueue.add(step)
        requestFrame()
    }

    internal fun markDirty() {
        dirty = true
        requestFrame()
    }

    private fun requestFrame() {
        wake.trySend(Unit)
    }

    fun addFrameCallback(callback: FrameCallback) {
        frameCallbacks.add(callback)
        requestFrame()
    }

    fun removeFrameCallback(callback: FrameCallback) {
        frameCallbacks.remove(callback)
    }

    fun <T> reaction(prepare: () -> T, react: (T, T?) -> Unit): Reaction<T> {
        val r = Reaction(prepare, react)
        reactions.add(r)
        r.run() // useAnimatedReaction runs once on mount with previous = null
        return r
    }

    fun removeReaction(r: Reaction<*>) {
        reactions.remove(r)
    }

    private val busy
        get() = stepQueue.isNotEmpty() || frameCallbacks.isNotEmpty() || dirty || frames.hasAwaiters

    /** Drives the runtime; call from one long-lived composition-scoped coroutine. */
    suspend fun run() {
        while (true) {
            if (!busy) wake.receive()
            withFrameNanos { nanos -> frame(nanos / 1e6) }
        }
    }

    internal fun frame(timeMs: Double) {
        lastTimestamp = max(lastTimestamp, timeMs)
        frameTimestamp = lastTimestamp
        inFrame = true
        try {
            Perf.section("Anim:steps") {
                val steps = stepQueue
                stepQueue = ArrayList()
                for (step in steps) step(frameTimestamp)
            }
            Perf.section("Anim:animatables") {
                deliveredMs = frameTimestamp
                frames.sendFrame(nanos(frameTimestamp))
            }
            Perf.section("Anim:frameCallbacks") {
                for (callback in frameCallbacks.toList()) callback.onFrame(frameTimestamp)
            }
            // Reactions observe the frame's final values. A reaction that
            // writes values marks the engine dirty again: they run next frame,
            // as Reanimated mappers do.
            dirty = false
            Perf.section("Anim:reactions") {
                for (r in reactions.toList()) r.run()
            }
        } finally {
            inFrame = false
        }
    }

    /** Test hook: advance to `timeMs` (unit tests drive frames by hand). */
    fun frameForTest(timeMs: Double) = frame(timeMs)

    fun resetForTest() {
        scope.coroutineContext.cancelChildren()
        stepQueue.clear(); frameCallbacks.clear(); reactions.clear(); dirty = false
        immediate.clear()
        lastTimestamp = 0.0; frameTimestamp = 0.0; deliveredMs = 0.0
    }
}

fun interface FrameCallback {
    /** `timeSincePreviousFrame` is derived by the callback from successive times. */
    fun onFrame(timestampMs: Double)
}

class Reaction<T>(private val prepare: () -> T, private val react: (T, T?) -> Unit) {
    private var previous: T? = null
    private var first = true
    fun run() {
        val next = prepare()
        react(next, if (first) null else previous)
        first = false
        previous = next
    }
}

/**
 * A numeric shared value (`useSharedValue<number>`), with Reanimated's
 * `valueSetter` semantics: assigning an animation reports the previous one
 * unfinished, hands it to the new one's `onStart`, and takes the first step
 * synchronously; assigning the current value again is a no-op.
 */
class SharedValue(initial: Double) {
    private val state: MutableDoubleState = mutableDoubleStateOf(initial)
    private var animation: Animation? = null

    fun get(): Double = state.doubleValue
    val value: Double get() = state.doubleValue

    private fun write(v: Double) {
        if (state.doubleValue != v) {
            state.doubleValue = v
            AnimationEngine.markDirty()
        }
    }

    fun set(v: Double) {
        dropAnimation()?.detach()
        // Setting the same value again does not trigger mappers.
        write(v)
    }

    private fun dropAnimation(): Animation? {
        val previous = animation
        if (previous != null) {
            animation = null
            if (!previous.finished) {
                previous.cancelled = true
                previous.callback?.invoke(false)
            }
        }
        return previous
    }

    fun set(anim: Animation) {
        val previous = dropAnimation()
        // Built-in non-higher-order animations hold their target in `current`
        // until started: setting a value to itself completes immediately.
        if (state.doubleValue == anim.current && !anim.isHigherOrder) {
            previous?.detach()
            anim.callback?.invoke(true)
            return
        }
        animation = anim
        val sink = object : Animation.Sink {
            override fun read() = state.doubleValue
            override fun write(v: Double) = this@SharedValue.write(v)
            override fun done() {
                // A previous animation a withDelay keeps running was already
                // reported unfinished; it only stops.
                if (anim.cancelled) return
                anim.finished = true
                anim.callback?.invoke(true)
            }
        }
        anim.start(state.doubleValue, AnimationEngine.timestamp(), previous, sink)
        // withDelay keeps the previous animation running while it waits.
        if (previous != null && !anim.adopts(previous)) previous.detach()
    }

    val isAnimating: Boolean get() = animation?.let { !it.finished && !it.cancelled } ?: false
}

/** cancelAnimation(sv): sets the value to itself, which drops the animation. */
fun cancelAnimation(sv: SharedValue) = sv.set(sv.get())

/** A shared value of any other type (plain set/get, no animations). */
class Shared<T>(initial: T) {
    private val state: MutableState<T> = mutableStateOf(initial, neverEqualPolicy())
    fun get(): T = state.value
    fun set(v: T) {
        state.value = v
        AnimationEngine.markDirty()
    }
}
