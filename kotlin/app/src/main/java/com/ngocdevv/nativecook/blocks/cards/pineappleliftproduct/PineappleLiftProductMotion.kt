package com.ngocdevv.nativecook.blocks.cards.pineappleliftproduct

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Timing and touch model of Pineapple Lift Product, ported from PineappleLiftProductMotion.swift.
 * Every value is a pure function of the loop time (autoPlay) or of the clock since a touch event,
 * so the loop's last frame equals its first.
 */
object PineappleLiftProductMotion {
    const val LOOP = 7.0

    class Burst(val age: Double, val strength: Double)

    /** What the art needs for one frame. */
    class Frame(
        var open: Double = 0.0, // the top half: 0 closed, 1 lifted up-right and tilted
        var rise: Double = 0.0, // the bottle: 0 hidden in the fruit, 1 hovering above it
        var squash: Double = 0.0, // pt the top half presses down (anticipation) or hops up (negative)
        var glow: Double = 0.0, // warm glow behind the bottle
        var drift: Double = 0.0, // hibiscus flowers drifting outward
        var sheen: Double = -1.0, // a light band crossing the bottle, 0 → 1 (negative: none)
        val bursts: MutableList<Burst> = ArrayList(2),
    )

    // Curves

    fun mod(a: Double, b: Double): Double {
        val r = a % b
        return if (r < 0) r + b else r
    }

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(max(v, lo), hi)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double) = x * x * x
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeInOutSine(x: Double) = -(cos(PI * x) - 1) / 2
    fun smoothstep(x: Double) = x * x * (3 - 2 * x)

    /** A released spring's remaining share of its start value (zero start velocity). */
    fun springDecay(tau: Double, z: Double, response: Double): Double {
        if (tau <= 0) return 1.0
        val w = 2 * PI / response
        val wd = w * sqrt(1 - z * z)
        return exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    /** A spring stepping from 0 to 1. */
    fun springStep(tau: Double, z: Double, response: Double) = if (tau <= 0) 0.0 else 1 - springDecay(tau, z, response)

    fun hash(x: Double): Double {
        val s = sin(x * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    // The loop (P = 7 s)

    const val BURST_AT = 1.45
    const val LAND_AT = 5.75

    /**
     * Rest 0–1 s → the top half presses, then lifts on a spring (1.15 s) → the bottle rises out of
     * the fruit (1.3 s) in a ring of juice drops (1.45 s) → it hovers → sinks back (4.6–5.5 s) → the
     * top closes (5.0–5.75 s) and lands with a small hop and a few drops → rest 6.3–7 s.
     */
    fun loopFrame(t: Double): Frame {
        val f = Frame()
        val press = easeInOutSine(seg(t, 1.0, 1.15)) * (1 - easeOutCubic(seg(t, 1.15, 1.35)))
        val up = springStep(t - 1.15, 0.62, 0.62)
        f.open = up * (1 - easeInCubic(seg(t, 5.0, 5.75)))
        val lift = springStep(t - 1.3, 0.55, 0.7)
        f.rise = lift * (1 - easeInOutCubic(seg(t, 4.6, 5.5)))
        f.squash = 5 * press - hop(t - LAND_AT) * (1 - smoothstep(seg(t, 6.2, 6.6)))
        f.glow = easeOutCubic(seg(t, 1.3, 2.2)) * (1 - easeInOutCubic(seg(t, 4.6, 5.4)))
        f.drift = easeOutCubic(seg(t, 1.3, 2.6)) * (1 - easeInOutCubic(seg(t, 4.6, 6.0)))
        if (t > 2.7 && t < 3.6) f.sheen = easeInOutSine(seg(t, 2.7, 3.6))
        val a = t - BURST_AT
        if (a >= 0 && a < 1.9) f.bursts.add(Burst(a, 1.0))
        val b = t - LAND_AT
        if (b >= 0 && b < 1.2) f.bursts.add(Burst(b, 0.45))
        return f
    }

    /** The landing hop of the top half, in pt. */
    fun hop(tau: Double) = if (tau < 0 || tau > 0.9) 0.0 else 7 * exp(-7 * tau) * abs(sin(11 * tau))

    // Juice drops

    const val DROP_COUNT = 54
    const val GLOB_COUNT = 7
    const val GRAVITY = 1300.0

    class Drop(val x: Double, val y: Double, val vx: Double, val vy: Double, val r: Double, val alpha: Double)

    /** Drops of one burst at [age] s; rx, ry = the cut ellipse's radii in pt. */
    fun drops(age: Double, strength: Double, rx: Double, ry: Double, out: MutableList<Drop>) {
        out.clear()
        val total = DROP_COUNT + GLOB_COUNT
        for (i in 0 until total) {
            val fi = i.toDouble()
            val glob = i >= DROP_COUNT
            val h1 = hash(fi * 1.37 + 0.11)
            val h2 = hash(fi * 2.71 + 0.37)
            val h3 = hash(fi * 0.93 + 0.53)
            val h4 = hash(fi * 3.17 + 0.71)
            val h5 = hash(fi * 1.91 + 0.29)
            val life = (if (glob) 1.25 + 0.35 * h5 else 0.95 + 0.6 * h5) * (if (strength < 0.7) 0.72 else 1.0)
            if (age > life) continue
            val n = (if (glob) GLOB_COUNT else DROP_COUNT).toDouble()
            val k = if (glob) fi - DROP_COUNT else fi
            val th = 2 * PI * (k + 0.6 * h1) / n + (if (glob) 0.4 else 0.0)
            val sx = cos(th) * rx * 0.92
            val sy = sin(th) * ry * 0.92
            val speed = strength * (if (glob) 0.62 else 1.0)
            val vx0 = cos(th) * (70 + 190 * h2) * speed
            val vy0 = -(250 + 330 * h3) * speed * (0.8 + 0.2 * abs(cos(th)))
            val x = sx + vx0 * age
            val y = sy + vy0 * age + 0.5 * GRAVITY * age * age
            val r = (if (glob) 5.5 + 3.5 * h4 else 2.0 + 3.8 * h4 * h4) * (0.75 + 0.25 * strength)
            val fadeIn = min(1.0, age / 0.05)
            val fadeOut = 1 - (age / life).pow(3)
            out.add(Drop(x, y, vx0, vy0 + GRAVITY * age, r, fadeIn * fadeOut))
        }
    }
}

/**
 * The finger: drag up to lift (the bottle follows), release to spring back, tap for a hop and a
 * splash. Two seconds after the last touch the loop starts again from rest.
 */
class PineappleLiftProductTouch {
    private val m = PineappleLiftProductMotion

    var touching = false
        private set
    private var base = 0.0 // clock at loop time 0
    private var startOpen = 0.0
    private var dragOpen = 0.0
    private var armed = true
    private var releaseClock = -100.0
    private var releaseOpen = 0.0
    private var releaseRise = 0.0
    private var landClock = -100.0
    private var tapClock = -100.0
    private var lastClock = -100.0 // last touch event (press, release or tap)
    private val bursts = ArrayList<DoubleArray>() // [clock, strength]
    private val hapticDue = ArrayList<Double>()

    val restsAfter: Double get() = lastClock + IDLE

    /** Loop time, or null while the finger owns the scene. */
    fun loopTime(clock: Double, autoPlay: Boolean): Double? {
        if (touching || clock < lastClock + IDLE) return null
        if (!autoPlay) return 0.0
        if (lastClock > -50) base = lastClock + IDLE
        return m.mod(clock - base, m.LOOP)
    }

    fun frame(clock: Double, autoPlay: Boolean): PineappleLiftProductMotion.Frame {
        val t = loopTime(clock, autoPlay)
        if (t != null) return if (autoPlay) m.loopFrame(t) else PineappleLiftProductMotion.Frame()
        val f = PineappleLiftProductMotion.Frame()
        val tap = tapPulse(clock - tapClock)
        if (touching) {
            f.open = dragOpen
            f.rise = max(0.0, dragOpen)
        } else {
            val tau = clock - releaseClock
            f.open = releaseOpen * m.springDecay(tau, 0.78, 0.5) + tap
            f.rise = releaseRise * m.springDecay(tau, 0.62, 0.62) + 0.55 * tap
        }
        f.squash = -m.hop(clock - landClock) * 0.8
        f.glow = m.clamp(f.rise)
        f.drift = m.clamp(f.open)
        for (b in bursts) {
            if (clock >= b[0] && clock - b[0] < 1.9) f.bursts.add(PineappleLiftProductMotion.Burst(clock - b[0], b[1]))
        }
        return f
    }

    /** A quick hop of the top half for a tap: up and down in 0.42 s. */
    private fun tapPulse(tau: Double) = if (tau < 0 || tau > 0.42) 0.0 else 0.3 * sin(PI * tau / 0.42)

    fun begin(clock: Double, autoPlay: Boolean) {
        // Carry the scene as it is on screen into the drag.
        val now = frame(clock, autoPlay)
        bursts.clear()
        for (b in now.bursts) bursts.add(doubleArrayOf(clock - b.age, b.strength))
        startOpen = m.clamp(now.open, 0.0, 1.2)
        dragOpen = startOpen
        armed = startOpen < 0.55
        touching = true
        lastClock = clock
    }

    /** [dy] is the finger's vertical travel in pt (negative = up). */
    fun drag(clock: Double, dy: Double) {
        if (!touching) return
        val raw = startOpen - dy / DRAG_SPAN
        dragOpen = when {
            raw > 1 -> {
                val over = raw - 1
                1 + over / (1 + over / 0.35)
            }
            raw < 0 -> raw / (1 - raw / 0.04)
            else -> raw
        }
        if (armed && dragOpen > 0.55) {
            armed = false
            addBurst(clock, 1.0)
        } else if (dragOpen < 0.3) {
            armed = true
        }
        lastClock = clock
    }

    fun end(clock: Double, tapped: Boolean) {
        if (!touching) return
        touching = false
        releaseClock = clock
        releaseOpen = dragOpen
        releaseRise = max(0.0, dragOpen)
        lastClock = clock
        if (tapped) {
            tapClock = clock
            addBurst(clock + 0.08, 0.7)
            landClock = clock + 0.42
            hapticDue.add(clock + 0.42)
            lastClock = clock + 0.42
        } else if (releaseOpen > 0.3) {
            // When the closing spring reaches the fruit: a hop, a few drops and a light tap.
            var tau = 0.0
            while (tau < 1.5 && releaseOpen * m.springDecay(tau, 0.78, 0.5) > 0.03) tau += 1.0 / 120
            landClock = clock + tau
            addBurst(landClock, 0.45)
        }
    }

    private fun addBurst(clock: Double, strength: Double) {
        bursts.removeAll { clock - it[0] > 2 }
        bursts.add(doubleArrayOf(clock, strength))
        hapticDue.add(clock)
    }

    /** True once per due haptic. */
    fun takeHaptic(clock: Double): Boolean {
        if (hapticDue.none { it <= clock }) return false
        hapticDue.removeAll { it <= clock }
        return true
    }

    companion object {
        const val IDLE = 2.0
        const val DRAG_SPAN = 200.0 // pt of upward drag for open = 1
    }
}
