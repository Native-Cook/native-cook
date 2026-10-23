package com.ngocdevv.nativecook.blocks.media.tapeshelfplayer

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** The block's clock: inserting and ejecting a tape as functions of time. Ported from TapeShelfPlayerMotion.swift. */
internal object TapeMotion {
    const val LOOP = 10.0
    /** The tape the loop plays (the middle of the grid). */
    const val LOOP_TAPE = 4
    /** The loop inserts at 0.8 s and ejects at 6.6 s. */
    const val INSERT_AT = 0.8
    const val EJECT_AT = 6.6
    /** How long the two sequences take. */
    const val INSERT_LENGTH = 2.2
    const val EJECT_LENGTH = 2.2
    /** When, inside the insert sequence, the screen starts to light up. */
    const val POWER_ON_AT = 1.7

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun easeInOut(x: Double): Double {
        val t = clamp01(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }
    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(3)
    fun easeIn(x: Double) = clamp01(x).pow(3)
    fun ramp(a: Double, b: Double, x: Double) = clamp01((x - a) / (b - a))

    class Pose {
        var tape = LOOP_TAPE
        /** The sleeve rising off the shelf to let its cassette out (or in): 0 to 1. */
        var lift = 0.0
        /** The cassette sliding out of the sleeve to the right: 0 in, 1 out. */
        var slide = 0.0
        /** The flight from beside the sleeve to above the slot: 0 to 1. */
        var flight = 0.0
        /** Going into the slot (squashed flat as it disappears): 0 to 1. */
        var squash = 0.0
        var cassetteAlpha = 0.0
        /** The screen: 0 off, 1 on; the power line effect runs between. */
        var screen = 0.0
        /** 1 while powering on, −1 while powering off (which picture the transition shows). */
        var powering = 1.0
        var led = 0.0
        /** The player's jolt when the tape seats, in dp. */
        var bounce = 0.0
        var transport = 0.0
        var pill = 1.0
        var ejectPress = 0.0
        /** Seconds the tape has been playing (drives the picture on the screen). */
        var video = 0.0
    }

    /** e seconds into inserting [tape]; after INSERT_LENGTH the tape plays on. */
    fun insert(e: Double, tape: Int) = Pose().apply {
        this.tape = tape
        lift = easeOut(e / 0.25) * (1 - smoothstep(0.6, 0.9, e))
        slide = easeInOut((e - 0.15) / 0.45)
        flight = easeInOut((e - 0.6) / 0.75)
        squash = easeIn((e - 1.35) / 0.3)
        cassetteAlpha = (if (e > 0.1) 1.0 else 0.0) * (1 - ramp(1.55, 1.65, e))
        val tau = e - 1.65
        bounce = if (tau > 0) 3 * exp(-8 * tau) * sin(25 * tau) else 0.0
        led = if (e >= 1.65) 1.0 else 0.0
        screen = ramp(POWER_ON_AT, POWER_ON_AT + 0.35, e)
        powering = 1.0
        transport = easeOut((e - 1.8) / 0.4)
        pill = 1 - easeInOut((e - 1.6) / 0.3)
        video = max(0.0, e - POWER_ON_AT)
    }

    /** e seconds into ejecting [tape] (played for [played] seconds); after EJECT_LENGTH all is at rest. */
    fun eject(e: Double, tape: Int, played: Double) = Pose().apply {
        this.tape = tape
        ejectPress = smoothstep(0.0, 0.06, e) * (1 - smoothstep(0.12, 0.25, e))
        screen = 1 - ramp(0.0, 0.3, e)
        powering = -1.0
        transport = 1 - easeInOut((e - 0.1) / 0.4)
        pill = easeOut((e - 0.4) / 0.4)
        led = if (e < 0.3) 1.0 else 0.0
        squash = 1 - easeOut((e - 0.3) / 0.3)
        cassetteAlpha = if (e >= 1.8) 0.0 else ramp(0.3, 0.4, e)
        flight = 1 - easeInOut((e - 0.6) / 0.75)
        lift = easeOut((e - 1.2) / 0.25) * (1 - smoothstep(1.8, 2.2, e))
        slide = 1 - easeInOut((e - 1.35) / 0.45)
        video = played + e
    }

    /** The loop at time t. */
    fun loopPose(t: Double): Pose {
        if (t >= INSERT_AT && t < EJECT_AT) return insert(t - INSERT_AT, LOOP_TAPE)
        if (t >= EJECT_AT && t < EJECT_AT + EJECT_LENGTH) return eject(t - EJECT_AT, LOOP_TAPE, EJECT_AT - INSERT_AT - POWER_ON_AT)
        return Pose()
    }

    /** A small integer hash (PCG), the same in every stack: 0 to 1. */
    fun hash(x: Int, salt: Int): Double {
        var h = x * 747796405 + salt * -1403630843 + 1 // 2891336453 as a signed 32-bit int
        h = ((h ushr ((h ushr 28) + 4)) xor h) * 277803737
        h = (h ushr 22) xor h
        return (h.toLong() and 0xffffffffL).toDouble() / 4294967295.0
    }
}
