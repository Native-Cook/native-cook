package com.ngocdevv.nativecook.blocks.onboarding.mokafloodwelcome

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Timing and touch model of Moka Flood Welcome, ported from MokaFloodWelcomeMotion.swift. Every
 * loop value is a pure function of the loop time, so the loop's last frame equals its first.
 */
object MokaFloodWelcomeMotion {
    const val LOOP = 9.5

    const val REST_ANGLE = -6.0
    const val POUR_ANGLE = -50.0
    const val GRAVITY = 2200.0
    const val POUR_THRESHOLD = -36.0

    /** One falling stream: how far its head and its broken tail have fallen, and its width share. */
    class Stream(val head: Double, val tail: Double = 0.0, val width: Double = 1.0)

    /** What the art needs for one frame. */
    class Frame(
        val angle: Double = REST_ANGLE,
        /** 0 empty glass, 1 at the rim, 1–1.75 dome, spills and puddle, 1.75–3 the flood rises. */
        val volume: Double = 0.0,
        /** While set, the flood drains from above the top (0) to below the bottom (1). */
        val drain: Double? = null,
        val title: Double = 0.0,
        val sub: Double = 0.0,
        val stream: Stream? = null,
        val time: Double = 0.0,
        val slosh: Double = 0.0,
    )

    // Curves

    fun mod(a: Double, b: Double): Double {
        val r = a % b
        return if (r < 0) r + b else r
    }

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = v.coerceIn(lo, hi)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeInOutSine(x: Double) = -(cos(PI * x) - 1) / 2

    fun springDecay(tau: Double, z: Double, response: Double): Double {
        if (tau <= 0) return 1.0
        val w = 2 * PI / response
        val wd = w * sqrt(1 - z * z)
        return exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    fun springStep(tau: Double, z: Double, response: Double) = if (tau <= 0) 0.0 else 1 - springDecay(tau, z, response)

    fun hash(x: Double): Double {
        val s = sin(x * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** Write progress of title line [i] for a title value 0–1. */
    fun lineProgress(title: Double, i: Int) = easeOutCubic(clamp((title - 0.29 * i) / 0.42))

    // The loop (P = 9.5 s)

    const val STREAM_AT = 1.2
    const val FILL_AT = 1.72
    const val BREAK_AT = 5.4
    const val DRAIN_AT = 8.1
    const val DRAIN_END = 9.0

    fun loopVolume(t: Double): Double = when {
        t < FILL_AT -> 0.0
        t < 3.6 -> easeInOutSine(seg(t, FILL_AT, 3.6))
        t < 4.7 -> 1 + 0.75 * seg(t, 3.6, 4.7)
        else -> 1.75 + 1.25 * easeInOutSine(seg(t, 4.7, 6.5))
    }

    /**
     * Rest 0–1 s → the pot tilts on a spring (1.0) and pours (1.2) → the glass fills (1.72–3.6) →
     * domes, spills and puddles (3.6–4.7) → the flood rises (4.7–6.5) while the pot rights itself
     * (5.15) and the stream breaks (5.4) → the title is written (6.55–7.55) → the flood drains
     * (8.1–9.0) → rest.
     */
    fun loopFrame(t: Double): Frame {
        val up = springStep(t - 1.0, 0.62, 0.6)
        val back = springStep(t - 5.15, 0.6, 0.6)
        val anticipation = 4 * sin(PI * seg(t, 0.85, 1.05))
        val angle = REST_ANGLE + (POUR_ANGLE - REST_ANGLE) * (up - back) + anticipation
        var stream: Stream? = null
        if (t >= STREAM_AT) {
            val tau = t - STREAM_AT
            val tail = if (t > BREAK_AT) 0.5 * GRAVITY * (t - BREAK_AT) * (t - BREAK_AT) else 0.0
            if (tail < 1200) stream = Stream(0.5 * GRAVITY * tau * tau, tail, 1 - 0.65 * easeInCubic(seg(t, 5.1, BREAK_AT)))
        }
        val slosh = seg(t, FILL_AT, FILL_AT + 0.25) * (1 - seg(t, BREAK_AT, 6.0))
        if (t >= DRAIN_END) return Frame(angle = angle, stream = stream, time = t, slosh = slosh)
        return Frame(
            angle = angle,
            volume = loopVolume(t),
            drain = if (t >= DRAIN_AT) easeInOutCubic(seg(t, DRAIN_AT, DRAIN_END)) else null,
            title = seg(t, 6.55, 7.55),
            sub = easeOutCubic(seg(t, 7.25, 7.65)),
            stream = stream,
            time = t,
            slosh = slosh,
        )
    }
}

/**
 * The finger: hold to tilt the pot and pour, drag left or down to pour harder; release to let it
 * settle. Two seconds after the last touch the coffee drains and the loop starts again from rest.
 */
class MokaFloodWelcomeTouch {
    private val m = MokaFloodWelcomeMotion

    companion object {
        const val IDLE = 2.0
        const val DRAIN_TIME = 0.9
    }

    var touching = false
        private set
    private var base = 0.0
    private var lastClock = -100.0
    private var lastStep = -1.0
    private var angle = MokaFloodWelcomeMotion.REST_ANGLE
    private var spin = 0.0
    private var volume = 0.0
    private var drag = 0.0
    private var streamStart: Double? = null
    private var streamBreak: Double? = null
    private var drainSweep = false
    private val hapticDue = ArrayList<Double>()

    val restsAfter: Double get() = lastClock + IDLE + DRAIN_TIME

    fun loopTime(clock: Double, autoPlay: Boolean): Double? {
        if (touching || clock < restsAfter) return null
        if (!autoPlay) return 0.0
        if (lastClock > -50) base = restsAfter
        return m.mod(clock - base, m.LOOP)
    }

    fun frame(clock: Double, autoPlay: Boolean): MokaFloodWelcomeMotion.Frame {
        loopTime(clock, autoPlay)?.let { t -> return if (autoPlay) m.loopFrame(t) else MokaFloodWelcomeMotion.Frame() }
        step(clock)
        var vol = minOf(volume, 3.0)
        val title = m.clamp(volume - 3)
        var stream: MokaFloodWelcomeMotion.Stream? = null
        streamStart?.let { start ->
            val tau = clock - start
            var width = 1.0
            var tail = 0.0
            streamBreak?.let { b ->
                val k = clock - b
                width = 1 - 0.65 * m.easeInCubic(m.clamp(k / 0.22))
                if (k > 0.22) tail = 0.5 * m.GRAVITY * (k - 0.22) * (k - 0.22)
            }
            if (tail < 1200) stream = MokaFloodWelcomeMotion.Stream(0.5 * m.GRAVITY * tau * tau, tail, width)
        }
        var drain: Double? = null
        val drainStart = lastClock + IDLE
        if (!touching && clock > drainStart) {
            val d = m.easeInOutCubic(m.clamp((clock - drainStart) / DRAIN_TIME))
            if (drainSweep) drain = d else vol *= 1 - d
        }
        return MokaFloodWelcomeMotion.Frame(
            angle = angle,
            volume = vol,
            drain = drain,
            title = title,
            sub = m.easeOutCubic(m.clamp((title - 0.7) / 0.3)),
            stream = stream,
            time = clock,
            slosh = if (stream != null) 1.0 else 0.4,
        )
    }

    /** Integrates the pot's spring and the poured volume (touch only; the loop is closed-form). */
    private fun step(clock: Double) {
        val dt = m.clamp(clock - lastStep, 0.0, 1.0 / 30)
        lastStep = clock
        if (dt <= 0) return
        val target = if (touching) m.REST_ANGLE + (m.POUR_ANGLE - m.REST_ANGLE) * (0.85 + 0.25 * drag) else m.REST_ANGLE
        val w = 2 * PI / 0.6
        val z = if (touching) 0.62 else 0.6
        var sub = 0.0
        while (sub < dt) {
            val h = minOf(1.0 / 240, dt - sub)
            spin += (-w * w * (angle - target) - 2 * z * w * spin) * h
            angle += spin * h
            sub += h
        }
        val pouring = angle < m.POUR_THRESHOLD
        if (pouring && (streamStart == null || streamBreak != null)) {
            streamStart = clock
            streamBreak = null
        } else if (!pouring && streamStart != null && streamBreak == null) {
            streamBreak = clock
        }
        val start = streamStart
        if (pouring && start != null && clock - start > 0.4) {
            val before = volume
            volume = minOf(4.0, volume + dt * (0.45 + 0.6 * drag))
            for (mark in doubleArrayOf(0.0, 1.0, 3.0)) if (before <= mark && volume > mark) hapticDue.add(clock)
        }
    }

    fun begin(clock: Double, autoPlay: Boolean) {
        if (!touching) {
            val t = loopTime(clock, autoPlay)
            if (t != null && autoPlay) {
                // Carry the loop as it is on screen into the touch.
                val f = m.loopFrame(t)
                angle = f.angle
                spin = 0.0
                volume = if (f.drain != null) 0.0 else if (f.title > 0) 3 + f.title else f.volume
                if (t >= m.STREAM_AT && t < m.BREAK_AT) {
                    streamStart = clock - (t - m.STREAM_AT)
                } else {
                    streamStart = null
                }
                streamBreak = null
            } else if (clock > lastClock + IDLE) {
                // Touched while the last pour drains: start again from what is left.
                val d = m.easeInOutCubic(m.clamp((clock - lastClock - IDLE) / DRAIN_TIME))
                volume = if (drainSweep) (if (d > 0.5) 0.0 else volume) else volume * (1 - d)
            }
            lastStep = clock
        }
        touching = true
        drag = 0.0
        lastClock = clock
    }

    /** [dx], [dy]: the finger's travel in pt; left or down tilts the pot further. */
    fun move(clock: Double, dx: Double, dy: Double) {
        if (!touching) return
        drag = m.clamp((-dx + dy) / 160)
        lastClock = clock
    }

    fun end(clock: Double) {
        if (!touching) return
        step(clock)
        touching = false
        drag = 0.0
        lastClock = clock
        drainSweep = volume >= 2.9
    }

    fun takeHaptic(clock: Double): Boolean {
        if (hapticDue.none { it <= clock }) return false
        hapticDue.removeAll { it <= clock }
        return true
    }
}
