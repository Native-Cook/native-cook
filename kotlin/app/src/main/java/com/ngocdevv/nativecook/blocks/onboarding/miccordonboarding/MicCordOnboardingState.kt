package com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.FIXED_STEP
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.GRAB_AT
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.GRAB_DX
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.GRAB_DY
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.GRAB_RELEASE
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.LOOP
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.N
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.Rope

/** Mutable values shared by the frame driver, pointer input, and the Canvas renderer. */
internal class MicCordOnboardingState(
    val width: Double,
    val height: Double,
    val topInset: Double,
) {
    val anchorX = width * 0.52
    val anchorY = topInset - 30.0
    val rope: Rope = MicCordOnboardingPhysics.makeRope(anchorX, anchorY)
    val rest: Rope
    val tick = mutableIntStateOf(0)

    var time = 0.0
    var clock = 0.0
    var lastTouch = -10.0
    var accumulator = 0.0
    // Separate real finger ownership from Rope.held: the scripted autoplay also uses held while
    // it performs its 1.0–1.8 s pull and must not pause itself at the scripted grab.
    private var userHolding = false
    var buttonPressed by mutableStateOf(false)
    var awake by mutableStateOf(false)

    private var grabDx = 0.0
    private var grabDy = 0.0
    private var grabStartX = 0.0
    private var grabStartY = 0.0

    init {
        rest = makeRestPose()
        copyInto(rope, rest)
    }

    private fun makeRestPose(): Rope {
        val r = MicCordOnboardingPhysics.makeRope(anchorX, anchorY)
        repeat(600) {
            MicCordOnboardingPhysics.stepRope(r, anchorX, anchorY, FIXED_STEP, 0.96)
        }
        for (i in 0 until N) {
            r.px[i] = r.x[i]
            r.py[i] = r.y[i]
        }
        return r
    }

    private fun copyInto(destination: Rope, source: Rope) {
        source.x.copyInto(destination.x)
        source.y.copyInto(destination.y)
        source.px.copyInto(destination.px)
        source.py.copyInto(destination.py)
        source.notes.copyInto(destination.notes)
        destination.held = source.held
        destination.hx = source.hx
        destination.hy = source.hy
        destination.speed = source.speed
        destination.spawn = source.spawn
        destination.burst = source.burst
        destination.was = source.was
        destination.seed = source.seed
    }

    private fun smooth(value: Double): Double {
        val x = value.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private fun scriptStep(r: Rope, t: Double) {
        if (t >= GRAB_AT && t < GRAB_RELEASE) {
            val k = smooth((t - GRAB_AT) / (GRAB_RELEASE - GRAB_AT - 0.15))
            r.held = 1
            r.hx = rest.x[N - 1] + GRAB_DX * k
            r.hy = rest.y[N - 1] + GRAB_DY * k
        } else {
            r.held = 0
        }
        MicCordOnboardingPhysics.stepRope(r, anchorX, anchorY, FIXED_STEP, 0.992)
        MicCordOnboardingPhysics.stepNotes(r, FIXED_STEP, false)
        if (t > LOOP - 0.6) {
            val k = smooth((t - (LOOP - 0.6)) / 0.6) * 0.08
            for (i in 0 until N) {
                r.x[i] += (rest.x[i] - r.x[i]) * k
                r.y[i] += (rest.y[i] - r.y[i]) * k
                r.px[i] += (rest.px[i] - r.px[i]) * k
                r.py[i] += (rest.py[i] - r.py[i]) * k
            }
        }
    }

    /** Advances wall time, consuming it only in 120 Hz physics steps. */
    fun step(dt: Double, auto: Boolean, reduced: Boolean) {
        val safeDt = dt.coerceAtLeast(0.0)
        clock += safeDt
        var r = rope
        val touched = userHolding || clock - lastTouch < 2.0
        var a = accumulator + safeDt
        if (auto && !touched) {
            while (a >= FIXED_STEP) {
                a -= FIXED_STEP
                time += FIXED_STEP
                if (time >= LOOP) {
                    time -= LOOP
                    r = MicCordOnboardingPhysics.copyRope(rest)
                    copyInto(rope, r)
                    r = rope
                }
                scriptStep(r, time)
            }
        } else {
            while (a >= FIXED_STEP) {
                a -= FIXED_STEP
                if (!reduced || r.held == 1) {
                    MicCordOnboardingPhysics.stepRope(r, anchorX, anchorY, FIXED_STEP, 0.992)
                }
                MicCordOnboardingPhysics.stepNotes(r, FIXED_STEP, reduced)
            }
        }
        accumulator = a
        tick.intValue += 1
    }

    fun bump() {
        tick.intValue += 1
    }

    /** Starts a mic drag only inside the RN hit radius around the mic's middle. */
    fun beginMicTouch(x: Double, y: Double): Boolean {
        val r = rope
        lastTouch = clock
        val angle = atan2(r.y[N - 1] - r.y[N - 3], r.x[N - 1] - r.x[N - 3])
        val mx = r.x[N - 1] + kotlin.math.cos(angle) * 90.0 * 1.4
        val my = r.y[N - 1] + kotlin.math.sin(angle) * 90.0 * 1.4
        if (hypot(x - mx, y - my) > 100.0) return false
        userHolding = true
        r.held = 1
        grabDx = r.x[N - 1] - x
        grabDy = r.y[N - 1] - y
        grabStartX = x
        grabStartY = y
        r.hx = r.x[N - 1]
        r.hy = r.y[N - 1]
        awake = true
        bump()
        return true
    }

    fun updateMicTouch(x: Double, y: Double) {
        val r = rope
        if (r.held != 1) return
        lastTouch = clock
        r.hx = x + grabDx
        r.hy = max(anchorY + 40.0, y + grabDy)
        bump()
    }

    data class TouchEnd(val tap: Boolean, val fast: Boolean)

    fun endMicTouch(x: Double, y: Double, velocityX: Double, velocityY: Double): TouchEnd? {
        val r = rope
        if (r.held != 1) return null
        lastTouch = clock
        userHolding = false
        r.held = 0
        val tap = hypot(x - grabStartX, y - grabStartY) < 6.0
        if (tap) {
            r.px[N - 1] = r.x[N - 1] - 7.0
            r.py[N - 1] = r.y[N - 1] + 2.0
        }
        val fast = hypot(velocityX, velocityY) > 500.0
        if (reducedAwake) awake = false
        bump()
        return TouchEnd(tap = tap, fast = fast)
    }

    // The reduced-motion frame loop toggles this through [setReducedAwake] so touch cleanup does
    // not need to know the current accessibility setting.
    private var reducedAwake = false

    fun setReducedMotion(reduced: Boolean) {
        reducedAwake = reduced
        if (!reduced) awake = true
    }

    fun pressButton(pressed: Boolean) {
        buttonPressed = pressed
        bump()
    }
}
