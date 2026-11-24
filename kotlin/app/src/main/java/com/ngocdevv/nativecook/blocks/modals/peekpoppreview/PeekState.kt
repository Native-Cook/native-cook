package com.ngocdevv.nativecook.blocks.modals.peekpoppreview

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One animated value: set directly, or driven by a spring, a timing or a decay, stepped by the screen's one clock. */
@Stable
internal class Channel(initial: Float = 0f, private val settle: Float = 0.0005f) {
    private enum class Kind { Idle, Spring, Timing, Decay }

    private val state = mutableFloatStateOf(initial)
    val value: Float get() = state.floatValue

    private var kind = Kind.Idle
    private var velocity = 0f
    private var target = 0f
    private var stiffness = 0f
    private var damping = 0f
    private var from = 0f
    private var duration = 0f
    private var elapsed = 0f
    private var ease: (Float) -> Float = ::easeInOutQuad
    private var low = 0f
    private var high = 0f

    // withDelay: whatever is running keeps running until the delayed animation starts.
    private var pendingIn = 0f
    private var pending: (() -> Unit)? = null

    fun set(next: Float) {
        pending = null
        kind = Kind.Idle
        velocity = 0f
        state.floatValue = next
    }

    fun cancel() {
        pending = null
        kind = Kind.Idle
        velocity = 0f
    }

    /** Like withSpring: a velocity of 0 keeps what a running spring is already doing. */
    fun spring(to: Float, spec: PeekSpring, startVelocity: Float = 0f) {
        pending = null
        val carried = if (kind == Kind.Spring) velocity else 0f
        val w = 2f * PI.toFloat() / spec.response
        stiffness = w * w
        damping = 2f * spec.ratio * w
        target = to
        velocity = if (startVelocity != 0f) startVelocity else carried
        kind = Kind.Spring
    }

    fun timing(to: Float, seconds: Float, curve: (Float) -> Float = ::easeInOutQuad) {
        pending = null
        from = value
        target = to
        duration = seconds
        elapsed = 0f
        ease = curve
        velocity = 0f
        kind = Kind.Timing
    }

    fun decay(startVelocity: Float, min: Float, max: Float) {
        pending = null
        velocity = startVelocity
        low = min
        high = max
        kind = Kind.Decay
    }

    fun after(seconds: Float, start: () -> Unit) {
        pendingIn = seconds
        pending = start
    }

    fun step(dt: Float) {
        pending?.let { run ->
            pendingIn -= dt
            if (pendingIn <= 0f) {
                pending = null
                run()
            }
        }
        when (kind) {
            Kind.Idle -> Unit
            Kind.Spring -> stepSpring(dt)
            Kind.Timing -> {
                elapsed += dt
                val u = if (duration <= 0f) 1f else min(elapsed / duration, 1f)
                state.floatValue = mix(from, target, ease(u))
                if (u >= 1f) kind = Kind.Idle
            }
            Kind.Decay -> {
                var next = value + velocity * dt
                // Reanimated's decay: 0.998 per millisecond.
                velocity *= 0.998f.pow(dt * 1000f)
                if (next <= low || next >= high) {
                    next = clampTo(next, low, high)
                    velocity = 0f
                }
                state.floatValue = next
                if (abs(velocity) < 1f) {
                    velocity = 0f
                    kind = Kind.Idle
                }
            }
        }
    }

    private fun stepSpring(dt: Float) {
        val steps = max(1, ceil(dt * 240f).toInt())
        val h = dt / steps
        var x = value
        var v = velocity
        repeat(steps) {
            v += (-stiffness * (x - target) - damping * v) * h
            x += v * h
        }
        if (abs(x - target) < settle && abs(v) < settle * 10f) {
            x = target
            v = 0f
            kind = Kind.Idle
        }
        velocity = v
        state.floatValue = x
    }
}

/** What a release decided. */
internal enum class PeekRelease { Stayed, Popped, Closed }

/** The state, its transitions and the two things that tick by themselves: the reveal clock and the reset. */
@Stable
internal class PeekState {
    /** The item that is out, or -1. */
    var active by mutableIntStateOf(-1)

    val press = Channel()
    val peek = Channel()
    val sink = Channel()
    val pop = Channel()
    val reveal = Channel()
    val scroll = Channel(settle = 0.05f)

    /** How far the open page can scroll; the caller sets it from its content. */
    var maxScroll = 0f

    /** True while the preview is meant to be up (drives `reveal`). */
    var wants = false

    /** Fingers down on a trigger or the frame. */
    var held = 0

    /** Counts touches, so the autoplay can tell a finger arrived. */
    var touches = 0

    /** True while a script writes the values above; the first touch clears it. */
    var scripted by mutableIntStateOf(0)

    val pk: Float get() = peek.value - sink.value

    private fun drive(channel: Channel, target: Float, spring: PeekSpring, velocity: Float, reduced: Boolean) {
        if (reduced) channel.timing(target, REDUCED_SECONDS) else channel.spring(target, spring, velocity)
    }

    /** The row's preview comes up (after the long press). */
    fun peekOpen(reduced: Boolean) {
        wants = true
        sink.set(0f)
        pop.set(0f)
        drive(peek, 1f, PEEK_SPRING, 0f, reduced)
        press.after(PRESS_RELEASE_FROM) { press.timing(0f, PRESS_RELEASE_TO - PRESS_RELEASE_FROM) }
    }

    /** The preview settles where it floats. */
    fun peekStay(reduced: Boolean) {
        wants = true
        drive(peek, 1f, PEEK_SPRING, 0f, reduced)
        drive(sink, 0f, PEEK_SPRING, 0f, reduced)
        drive(pop, 0f, CLOSE_SPRING, 0f, reduced)
    }

    /** The preview becomes the page. `velocity` is pop per second. */
    fun peekPop(velocity: Float, reduced: Boolean) {
        wants = true
        press.timing(0f, 0.12f)
        drive(sink, 0f, PEEK_SPRING, 0f, reduced)
        drive(peek, 1f, PEEK_SPRING, 0f, reduced)
        drive(pop, 1f, POP_SPRING, velocity, reduced)
    }

    /** Everything goes back into the row, along the path it came. `velocity` is per second, negative toward the row. */
    fun peekClose(velocity: Float, reduced: Boolean) {
        wants = false
        val fromPage = pop.value > 0.2f
        press.timing(0f, 0.12f)
        scroll.timing(0f, 0.26f)
        drive(sink, 0f, CLOSE_SPRING, 0f, reduced)
        drive(pop, 0f, CLOSE_SPRING, if (fromPage) velocity else 0f, reduced)
        if (fromPage && !reduced) {
            peek.after(CLOSE_FOLLOW) { peek.spring(0f, CLOSE_SPRING) }
        } else {
            drive(peek, 0f, CLOSE_SPRING, if (fromPage) 0f else velocity, reduced)
        }
    }

    /** A script was driving and a finger arrived: carry on from wherever it was to the nearest resting state. */
    fun peekSettle(reduced: Boolean) {
        if (active < 0) return
        when {
            pop.value > 0.5f -> peekPop(0f, reduced)
            pk > 0.5f -> peekStay(reduced)
            else -> peekClose(0f, reduced)
        }
    }

    /** Call from every gesture's start. */
    fun peekTouch(reduced: Boolean) {
        touches += 1
        if (scripted == 1) {
            scripted = 0
            peekSettle(reduced)
        }
    }

    /** While peeking, the finger's vertical travel: up pops, down sinks the preview back toward its row. */
    fun peekDrag(travel: Float) {
        if (travel < 0f) {
            sink.set(0f)
            pop.set(min(-travel / POP_UP_SPAN, 1.02f))
        } else {
            pop.set(0f)
            sink.set(rubber(travel, SINK_RUBBER) / SINK_SPAN)
        }
    }

    fun peekRelease(travel: Float, velocity: Float, reduced: Boolean): PeekRelease {
        if (pop.value > POP_COMMIT || (travel < -12f && velocity < -POP_VELOCITY)) {
            peekPop(-velocity / POP_UP_SPAN, reduced)
            return PeekRelease.Popped
        }
        if (travel > SINK_CLOSE || velocity > SINK_VELOCITY) {
            peekClose(-velocity * rubberSlope(max(travel, 0f), SINK_RUBBER) / SINK_SPAN, reduced)
            return PeekRelease.Closed
        }
        peekStay(reduced)
        return PeekRelease.Stayed
    }

    /** On the open page: the finger scrolls it, and past the top pulls the page back toward the preview. */
    fun pageDrag(scrollAtGrab: Float, travel: Float) {
        val want = scrollAtGrab - travel
        if (want >= 0f) {
            scroll.set(if (want <= maxScroll) want else maxScroll + rubber(want - maxScroll, SCROLL_RUBBER))
            pop.set(1f)
        } else {
            scroll.set(0f)
            pop.set(1f - rubber(-want, POP_DOWN_RUBBER) / POP_DOWN_SPAN)
        }
    }

    fun pageRelease(scrollAtGrab: Float, travel: Float, velocity: Float, reduced: Boolean): PeekRelease {
        val want = scrollAtGrab - travel
        if (want < 0f) {
            if (-want > DISMISS_PULL || velocity > DISMISS_VELOCITY) {
                peekClose(-velocity * rubberSlope(-want, POP_DOWN_RUBBER) / POP_DOWN_SPAN, reduced)
                return PeekRelease.Closed
            }
            drive(pop, 1f, POP_SPRING, 0f, reduced)
            return PeekRelease.Stayed
        }
        if (want > maxScroll) {
            drive(scroll, maxScroll, CLOSE_SPRING, 0f, reduced)
        } else if (maxScroll > 0f) {
            scroll.decay(-velocity, 0f, maxScroll)
        }
        return PeekRelease.Stayed
    }

    /** Steps every value; then the reveal clock and the reset once all is back. */
    fun tick(dt: Float) {
        press.step(dt)
        peek.step(dt)
        sink.step(dt)
        pop.step(dt)
        reveal.step(dt)
        scroll.step(dt)
        if (scripted == 1) return
        val now = reveal.value
        val up = wants && sink.value < 0.08f
        val next = if (up) min(1f, now + dt / REVEAL_IN) else max(0f, now - dt / REVEAL_OUT)
        if (next != now) reveal.set(next)
        if (active >= 0 && held == 0 && !wants && peek.value < 0.002f && pop.value < 0.002f && press.value < 0.002f) {
            active = -1
            press.set(0f)
            peek.set(0f)
            sink.set(0f)
            pop.set(0f)
            scroll.set(0f)
        }
    }
}
