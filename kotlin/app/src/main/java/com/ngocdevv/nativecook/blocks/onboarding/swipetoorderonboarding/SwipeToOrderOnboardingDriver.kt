package com.ngocdevv.nativecook.blocks.onboarding.swipetoorderonboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class SwipeToOrderOnboardingDriver(private val cue: (String) -> Unit, private val buzz: (Int) -> Unit) {
    val frame = SwipeToOrderOnboardingMotion.Frame()
    var awake by mutableStateOf(false)
    var done by mutableStateOf(false)
    private var now = 0.0
    private var last = -10.0
    private var doneAt = -10.0
    private var velocity = 0.0
    private var held = false
    private var start = 0.0
    private var placed = 0
    private fun settle(sound: Boolean) {
        if (!frame.done && frame.s >= 0.985) {
            frame.done = true
            if (sound) { doneAt = now; cue("done"); buzz(2) }
        } else if (frame.done && frame.s < 0.6) {
            frame.done = false
            if (sound) cue("back")
        }
        done = frame.done
    }
    fun step(dt: Double, auto: Boolean, reduced: Boolean) {
        now += dt
        val touched = held || now - last < 2
        if (auto && !touched) {
            frame.t = (frame.t + dt) % SwipeToOrderOnboardingMotion.LOOP
            frame.s = SwipeToOrderOnboardingMotion.knob(frame.t)
            settle(false)
        } else if (!held) {
            val target = if (frame.done) 1.0 else 0.0
            velocity += (260 * (target - frame.s) - 2 * sqrt(260.0) * 0.95 * velocity) * dt
            frame.s = SwipeToOrderOnboardingMotion.clamp(frame.s + velocity * dt)
            settle(true)
        } else settle(true)
        SwipeToOrderOnboardingMotion.step(frame.cards, frame.s, dt, reduced)
        if (!reduced) frame.spin = if (auto && !touched) 4 * PI * frame.t / 8 else frame.spin + dt * 4 * PI / 8
        val next = floor(frame.s / 0.34).toInt()
        if (next > placed && held) { cue("snap"); buzz(0) }
        placed = next
        frame.since = if (auto && !held && now - last >= 2) { if (frame.t >= 3.8 && frame.t < 5.9) frame.t - 3.8 else -1.0 } else now - doneAt
        if (reduced && !held && abs(velocity) < 0.00001 && frame.cards.all { b -> b.v.all { abs(it) < 0.00001 } }) awake = false
    }
    fun begin() { last = now; held = true; start = frame.s; velocity = 0.0; awake = true; buzz(1) }
    fun drag(dx: Double, range: Double) { last = now; frame.s = SwipeToOrderOnboardingMotion.clamp(start + dx / range) }
    fun end(vx: Double, range: Double) {
        last = now; held = false; velocity = vx / range
        if (frame.s > 0.85 || (frame.s > 0.5 && vx > 900)) frame.s = max(frame.s, 0.986)
        settle(true)
        if (!frame.done && frame.s > 0.05) cue("back")
    }
    fun activate() { frame.s = if (frame.done) 0.0 else 0.99; last = now; awake = true }
}
