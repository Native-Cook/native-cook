package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import androidx.compose.animation.core.CubicBezierEasing

internal const val LOOP: Long = PullLeverRefreshTokens.LOOP_MS

/** Compose motion types derived from the canonical React Native values in Tokens.kt. */
internal object PullLeverRefreshMotion {
    const val REPLICA_ONLY = PullLeverRefreshTokens.REPLICA_ONLY

    val Pull = PullLeverRefreshTokens.Pull
    val Spring = PullLeverRefreshTokens.SpringValues
    val Light = PullLeverRefreshTokens.Light

    internal object Timing {
        const val dotMs = PullLeverRefreshTokens.Timing.dotMs
        const val upAngle = PullLeverRefreshTokens.Timing.upAngle
        const val relaxAngle = PullLeverRefreshTokens.Timing.relaxAngle
        const val relaxMs = PullLeverRefreshTokens.Timing.relaxMs
        const val dimMs = PullLeverRefreshTokens.Timing.dimMs
        const val lightMs = PullLeverRefreshTokens.Timing.lightMs
        const val swayAmplitude = PullLeverRefreshTokens.Timing.swayAmplitude
        const val swayHalfPeriod = PullLeverRefreshTokens.Timing.swayHalfPeriod
        const val swayFade = PullLeverRefreshTokens.Timing.swayFade
        const val bloomScale = PullLeverRefreshTokens.Timing.bloomScale
        const val bloomRise = PullLeverRefreshTokens.Timing.bloomRise
        const val glowRise = PullLeverRefreshTokens.Timing.glowRise
        const val pulse = PullLeverRefreshTokens.Timing.pulse
        val easeInOut = CubicBezierEasing(
            PullLeverRefreshTokens.Timing.easeInOutX1,
            PullLeverRefreshTokens.Timing.easeInOutY1,
            PullLeverRefreshTokens.Timing.easeInOutX2,
            PullLeverRefreshTokens.Timing.easeInOutY2,
        )
        val easeOut = CubicBezierEasing(
            PullLeverRefreshTokens.Timing.easeOutX1,
            PullLeverRefreshTokens.Timing.easeOutY1,
            PullLeverRefreshTokens.Timing.easeOutX2,
            PullLeverRefreshTokens.Timing.easeOutY2,
        )
        const val knobFlash = PullLeverRefreshTokens.Timing.knobFlash
        const val knobFade = PullLeverRefreshTokens.Timing.knobFade
        val shake = PullLeverRefreshTokens.Timing.shake
        const val shakeStep = PullLeverRefreshTokens.Timing.shakeStep
        const val charIn = PullLeverRefreshTokens.Timing.charIn
        const val charOut = PullLeverRefreshTokens.Timing.charOut
        const val charStagger = PullLeverRefreshTokens.Timing.charStagger
        const val idleAmplitude = PullLeverRefreshTokens.Timing.idleAmplitude
        const val idlePeriod = PullLeverRefreshTokens.Timing.idlePeriod
        const val caption = PullLeverRefreshTokens.Timing.caption
        const val doneLabel = PullLeverRefreshTokens.Timing.doneLabel
        const val reduced = PullLeverRefreshTokens.Timing.reduced
    }

    const val FETCH_MS = PullLeverRefreshTokens.FETCH_MS
    const val LOOP_MS = PullLeverRefreshTokens.LOOP_MS
    const val RESUME_MS = PullLeverRefreshTokens.RESUME_MS

    internal object Demo {
        internal data class Script(
            val down: Float,
            val moveEnd: Float,
            val up: Float,
            val depth: Float,
        )

        private fun asScript(token: PullLeverRefreshTokens.DemoPull) = Script(
            down = token.down,
            moveEnd = token.moveEnd,
            up = token.up,
            depth = token.depth,
        )

        val full = asScript(PullLeverRefreshTokens.Demo.full)
        const val resolve = PullLeverRefreshTokens.Demo.resolve
        val partial = asScript(PullLeverRefreshTokens.Demo.partial)
        const val loopMs = LOOP
        const val resumeMs = RESUME_MS
        const val fetchMs = FETCH_MS
    }
}
