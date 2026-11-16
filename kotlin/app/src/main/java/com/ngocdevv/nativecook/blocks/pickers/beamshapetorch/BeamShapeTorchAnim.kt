package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * One animated value, the way Reanimated drives the RN block's shared values: a plain write cancels whatever runs; `run` starts a
 * sequence of steps (a delay, a timing with an easing, a damped spring that may start with a velocity) from the value as it is.
 * Stepped once per frame by the engine, on the main thread.
 */
internal class BeamShapeTorchAnim(initial: Double) {
    sealed class Step {
        class Delay(val seconds: Double) : Step()
        class Timing(
            val to: Double,
            val seconds: Double,
            val ease: (Double) -> Double = { it },
            val done: (() -> Unit)? = null,
        ) : Step()

        class Spring(val to: Double, val spring: BeamShapeTorchSpring, val velocity: Double? = null) : Step()
    }

    var value = initial
        private set
    private var velocity = 0.0
    private val steps = ArrayList<Step>()
    private var elapsed = 0.0
    private var from = 0.0
    private var started = false

    val running: Boolean get() = steps.isNotEmpty()

    /** A plain write: stops any animation. */
    fun set(v: Double) {
        steps.clear()
        started = false
        velocity = 0.0
        value = v
    }

    fun cancel() {
        steps.clear()
        started = false
    }

    fun run(vararg next: Step) {
        // a spring that replaces a spring keeps the speed it had, like the platform's own animation replacement
        val carried = if (steps.isNotEmpty() && steps[0] is Step.Spring && started) velocity else 0.0
        steps.clear()
        steps.addAll(next)
        started = false
        elapsed = 0.0
        velocity = if (next.firstOrNull() is Step.Spring && (next.first() as Step.Spring).velocity == null) carried else 0.0
    }

    fun step(dt: Double) {
        var left = dt
        var guard = 0
        while (steps.isNotEmpty() && left > 0 && guard++ < 8) {
            val s = steps[0]
            if (!started) {
                started = true
                elapsed = 0.0
                from = value
                if (s is Step.Spring && s.velocity != null) velocity = s.velocity
            }
            when (s) {
                is Step.Delay -> {
                    val take = min(left, s.seconds - elapsed)
                    elapsed += take
                    left -= take
                    if (elapsed >= s.seconds - 1e-9) next()
                }
                is Step.Timing -> {
                    val take = min(left, s.seconds - elapsed)
                    elapsed += take
                    left -= take
                    val k = if (s.seconds <= 0) 1.0 else min(elapsed / s.seconds, 1.0)
                    value = from + (s.to - from) * s.ease(k)
                    if (elapsed >= s.seconds - 1e-9) {
                        value = s.to
                        velocity = 0.0
                        val done = s.done
                        next()
                        done?.invoke()
                    }
                }
                is Step.Spring -> {
                    val sp = s.spring
                    val n = max(1, ceil(left / (1.0 / 240)).toInt())
                    val h = left / n
                    var x = value
                    var v = velocity
                    for (i in 0 until n) {
                        val nv = v + (-sp.stiffness * (x - s.to) - sp.damping * v) / sp.mass * h
                        x += nv * h
                        v = nv
                    }
                    value = x
                    velocity = v
                    left = 0.0
                    if (abs(v) < 0.02 && abs(x - s.to) < 0.0006) {
                        value = s.to
                        velocity = 0.0
                        next()
                    }
                }
            }
        }
    }

    private fun next() {
        if (steps.isNotEmpty()) steps.removeAt(0)
        started = false
    }
}
