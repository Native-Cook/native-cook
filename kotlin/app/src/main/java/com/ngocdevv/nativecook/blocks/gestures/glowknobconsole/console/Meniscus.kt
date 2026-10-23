// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.FrameCallback
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SharedValue
import kotlin.math.max

// Frames of stillness (light unchanged, spring at rest) before the per-frame
// simulation switches itself off: ~0.5s at 60Hz, ~0.25s at 120Hz.
private const val QUIET_FRAMES = 30

/**
 * Liquid meniscus (`use-meniscus.ts`): the light's leading edge is a damped
 * spring driven by the light's own speed, so it stretches while moving and
 * wobbles to rest. The frame callback only runs while the light moves or the
 * spring rings; any change of the light wakes it, and it parks itself once
 * everything is still.
 */
class Meniscus(private val fill: () -> Double, private val on: Boolean) {
    val slosh = SharedValue(0.0)
    private var velocity = 0.0
    private var lastFill = -1.0
    private var quiet = 0
    private var live = false
    private var previousFrame = -1.0

    private val frame = FrameCallback { t ->
        // useFrameCallback's timeSincePreviousFrame is null on the first frame.
        val dt = (if (previousFrame < 0) 16.0 else t - previousFrame) / 1000
        previousFrame = t
        val current = fill()
        val previous = lastFill
        lastFill = current
        if (previous < 0) return@FrameCallback
        val speed = ((current - previous) * G.Track.width) / max(dt, 1.0 / 240)
        val next = springStep(slosh.get(), velocity, sloshTarget(speed), dt)
        slosh.set(next.position)
        velocity = next.velocity
        if (current == previous && springSettled(next.position, next.velocity)) {
            quiet += 1
            if (quiet > QUIET_FRAMES) {
                slosh.set(0.0)
                velocity = 0.0
                live = false
                park()
            }
        } else quiet = 0
    }

    private fun start() {
        previousFrame = -1.0
        AnimationEngine.addFrameCallback(frame)
    }

    private fun park() { AnimationEngine.removeFrameCallback(frame) }

    private val reaction = AnimationEngine.reaction({ fill() }) { current, previous ->
        if (!on || previous == null || current == previous || live) return@reaction
        live = true
        lastFill = -1.0
        quiet = 0
        start()
    }

    fun dispose() {
        park()
        AnimationEngine.removeReaction(reaction)
    }
}
