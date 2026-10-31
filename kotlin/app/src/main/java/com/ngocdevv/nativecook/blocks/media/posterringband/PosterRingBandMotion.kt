package com.ngocdevv.nativecook.blocks.media.posterringband

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor

/** Seconds. One clockwise revolution, with two breaths of the ring's tilt. */
const val LOOP = 9.0
internal const val POSTER_RING_BAND_COUNT = 12
internal const val POSTER_RING_BAND_RADIUS = 150.0
internal const val POSTER_RING_BAND_STEP = 2 * PI / POSTER_RING_BAND_COUNT

internal fun posterRingBandMod(value: Double, period: Double) = ((value % period) + period) % period

// Math.round in the reference rounds a negative half toward positive infinity.
internal fun posterRingBandRound(value: Double) = floor(value + 0.5)

internal fun posterRingBandFront(turn: Double): Int =
    posterRingBandMod(posterRingBandRound(-turn / POSTER_RING_BAND_STEP), POSTER_RING_BAND_COUNT.toDouble()).toInt()

/** Mutable only on the UI thread; the draw pass reads it after the frame revision changes. */
internal class PosterRingBandMotion {
    var elapsed = 0.0
    var origin = 0.0
    var clock = 0.0
    var turn = 0.0
    var spin = 0.0
    var velocity = 0.0
    var touching = false
    var manual = false
    var released = -1.0
    var settleFrom = 0.0
    var lastX = 0.0
    var lastFront = 0

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        clock = (elapsed - origin) % LOOP
        when {
            touching -> turn = spin
            manual -> {
                val since = elapsed - released
                if (since < 2.0) {
                    spin += velocity * dt
                    velocity *= exp(-2.5 * dt)
                    settleFrom = spin
                } else {
                    val target = posterRingBandRound(settleFrom / POSTER_RING_BAND_STEP) * POSTER_RING_BAND_STEP
                    val x = ((since - 2.0) / 0.6).coerceIn(0.0, 1.0)
                    val eased = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2
                    spin = settleFrom + (target - settleFrom) * eased
                    if (since > 2.6) {
                        manual = false
                        // Preserve the reference's resume-origin formula literally.
                        val frac = posterRingBandMod(-target / (2 * PI), 1.0)
                        origin = elapsed - (1 - frac) * LOOP
                    }
                }
                turn = spin
            }
            else -> turn = if (autoPlay) -2 * PI * clock / LOOP else 0.0
        }
    }

    fun begin(x: Double) {
        touching = true
        manual = true
        spin = turn
        velocity = 0.0
        lastX = x
    }

    fun drag(x: Double, velocityX: Double): Boolean {
        spin += (x - lastX) / POSTER_RING_BAND_RADIUS
        lastX = x
        velocity = velocityX / POSTER_RING_BAND_RADIUS
        val front = posterRingBandFront(spin)
        return (front != lastFront).also { if (it) lastFront = front }
    }

    fun end() {
        touching = false
        released = elapsed
    }
}
