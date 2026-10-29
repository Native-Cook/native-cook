package com.ngocdevv.nativecook.blocks.lists.pickleflavourwipe

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
 * Timing and touch model of Pickle Flavour Wipe, ported from PickleFlavourWipeMotion.swift. Every
 * value is a pure function of the loop time (autoPlay) or of the clock since a touch event, so the
 * loop's last frame equals its first.
 */
object PickleFlavourWipeMotion {
    const val LOOP = 10.0
    const val PAGES = 4

    /** Loop time at which each wipe starts: Mango → Lime, Lime → Garlic, Garlic → Trio, Trio → Mango. */
    val WIPE_AT = doubleArrayOf(1.0, 3.25, 5.5, 7.75)
    /** How long the edge takes to cross the screen. */
    const val SWEEP = 0.8
    /** Loop time at which each page is on screen and settled (where the loop resumes after a touch). */
    val SETTLED_AT = doubleArrayOf(9.6, 3.1, 5.35, 7.6)
    /** Entrance age of a page that has fully arrived. */
    const val SETTLED_AGE = 4.0

    /** What the art needs for one frame: page [from] lies under, page [to] is revealed by the edge. */
    data class Scene(
        val from: Int = 0,
        val fromAge: Double = SETTLED_AGE,
        val to: Int = 0,
        val toAge: Double = SETTLED_AGE,
        val reveal: Double = 0.0, // 0 … 1: how far the edge has crossed
        val dir: Double = 1.0, // +1: the edge moves right → left (next page); −1: left → right
        val fade: Double = -1.0, // reduced motion: cross-fade 0 → 1 (negative: none)
    )

    fun still(page: Int, age: Double = SETTLED_AGE) = Scene(page, age, page, age, 0.0)

    // Curves

    fun mod(a: Double, b: Double): Double {
        val r = a % b
        return if (r < 0) r + b else r
    }

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(max(v, lo), hi)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** A released spring's remaining share of its start value (zero start velocity). */
    fun springDecay(tau: Double, damping: Double, response: Double): Double {
        if (tau <= 0) return 1.0
        val w = 2 * PI / response
        val wd = w * sqrt(1 - damping * damping)
        return exp(-damping * w * tau) * (cos(wd * tau) + damping * w / wd * sin(wd * tau))
    }

    /** A spring stepping from 0 to 1. */
    fun springStep(tau: Double, damping: Double, response: Double) =
        if (tau <= 0) 0.0 else 1 - springDecay(tau, damping, response)

    fun hash(x: Double): Double {
        val s = sin(x * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** A sine that repeats a whole number of times per loop. */
    fun wave(t: Double, k: Double, phase: Double) = sin(2 * PI * k * t / LOOP + phase)

    // Entrance of a page (age = seconds since its wipe began)

    /** The jar: rises 300 pt from below, turning from +14°, on a spring. */
    fun jarIn(age: Double) = springStep(age - 0.22, 0.62, 0.6)
    /** Ingredient [i] pops out from behind the jar. */
    fun ingredientIn(age: Double, i: Int) = springStep(age - 0.45 - 0.07 * i, 0.55, 0.5)
    /** The title slides in from the left. */
    fun titleIn(age: Double) = springStep(age - 0.5, 0.75, 0.45)
    /** The title's speed (share of its travel per second), for the smear. */
    fun titleSpeed(age: Double) = (titleIn(age) - titleIn(age - 1.0 / 60)) * 60
    /** Subtitle and description fade up. */
    fun copyIn(age: Double) = easeOutCubic(seg(age, 0.85, 1.2))
    /** Trio page: jar [j] of the line-up rises, staggered. */
    fun trioJarIn(age: Double, j: Int) = springStep(age - 0.22 - 0.09 * j, 0.62, 0.6)

    // The loop (P = 10 s)

    /**
     * Rest on Mango 0–1 s → the edge wipes Lime in (1.0–1.8 s), the jar, ingredients and title
     * arrive → hold → Garlic (3.25 s) → hold → the three jars line up on the Trio page (5.5 s) →
     * hold → Mango wipes back in (7.75 s) and settles by 9.5 s → rest.
     */
    fun loopScene(t: Double): Scene {
        var k = WIPE_AT.size - 1
        while (k >= 0 && t < WIPE_AT[k]) k -= 1
        // Before the first wipe: the last wipe (→ Mango) of the previous loop.
        val start = if (k >= 0) WIPE_AT[k] else WIPE_AT[WIPE_AT.size - 1] - LOOP
        val wipe = if (k >= 0) k else WIPE_AT.size - 1
        val age = t - start
        val from = wipe
        val to = (wipe + 1) % PAGES
        if (age < SWEEP) return Scene(from, SETTLED_AGE, to, age, easeInOutCubic(age / SWEEP), 1.0)
        return still(to, age)
    }
}

/**
 * The finger: swipe left for the next flavour, right for the previous one; the edge follows the
 * finger, release completes the wipe or springs it back. Two seconds after the last touch the loop
 * resumes from the page on screen.
 */
class PickleFlavourWipeTouch {
    private val m = PickleFlavourWipeMotion

    companion object {
        const val IDLE = 2.0
        const val DRAG_SPAN = 300.0 // pt of horizontal drag for a full wipe
    }

    var touching = false
        private set
    var page = 0 // the page that is (or is becoming) the settled one
        private set
    private var pageAgeBase = -100.0
    private var base = 0.0
    private var lastClock = -100.0
    private var startS = 0.0
    private var s = 0.0 // signed wipe while dragging: + towards the next page, − towards the previous
    private var releaseClock = -100.0
    private var releaseFrom = 0
    private var releaseTo = 0
    private var releaseReveal = 0.0
    private var releaseDir = 1.0
    private var releaseCommit = false
    private var releaseAge = 0.0
    private val hapticDue = mutableListOf<Double>()

    val restsAfter get() = lastClock + IDLE
    private val everTouched get() = lastClock > -50

    fun loopTime(clock: Double, autoPlay: Boolean): Double? {
        if (touching || clock < lastClock + IDLE) return null
        if (!autoPlay) return m.SETTLED_AT[page]
        if (everTouched) base = lastClock + IDLE - m.SETTLED_AT[page]
        return m.mod(clock - base, m.LOOP)
    }

    fun scene(clock: Double, autoPlay: Boolean): PickleFlavourWipeMotion.Scene {
        loopTime(clock, autoPlay)?.let { return m.loopScene(it) }
        if (touching) {
            val reveal = abs(s)
            if (reveal < 1e-4) return m.still(page, clock - pageAgeBase)
            val dir = if (s > 0) 1.0 else -1.0
            val to = (page + if (s > 0) 1 else m.PAGES - 1) % m.PAGES
            return PickleFlavourWipeMotion.Scene(page, clock - pageAgeBase, to, 0.9 * min(reveal, 1.0), reveal, dir)
        }
        val tau = clock - releaseClock
        if (releaseCommit) {
            val reveal = 1 + (releaseReveal - 1) * m.springDecay(tau, 0.86, 0.42)
            if (tau > 1.2) return m.still(releaseTo, releaseAge + tau)
            return PickleFlavourWipeMotion.Scene(releaseFrom, m.SETTLED_AGE, releaseTo, releaseAge + tau, reveal, releaseDir)
        }
        val reveal = releaseReveal * m.springDecay(tau, 0.86, 0.38)
        if (tau > 1.2 || (abs(reveal) < 1e-4 && tau > 0.3)) return m.still(releaseFrom, clock - pageAgeBase)
        return PickleFlavourWipeMotion.Scene(releaseFrom, clock - pageAgeBase, releaseTo, 0.9 * max(0.0, reveal), reveal, releaseDir)
    }

    fun begin(clock: Double, autoPlay: Boolean) {
        // Carry the scene as it is on screen into the drag.
        val now = scene(clock, autoPlay)
        if (now.from == now.to || now.reveal > 0.5) {
            page = now.to
            pageAgeBase = clock - now.toAge
            startS = if (now.from == now.to) 0.0 else -now.dir * (1 - now.reveal)
        } else {
            page = now.from
            pageAgeBase = clock - now.fromAge
            startS = now.dir * now.reveal
        }
        s = startS
        touching = true
        lastClock = clock
    }

    /** [dx] is the finger's horizontal travel in pt (negative = left). */
    fun drag(clock: Double, dx: Double) {
        if (!touching) return
        val raw = startS - dx / DRAG_SPAN
        val mag = abs(raw)
        s = if (mag > 1) (if (raw > 0) 1.0 else -1.0) * (1 + (mag - 1) / (1 + (mag - 1) / 0.12)) else raw
        lastClock = clock
    }

    /** [vx] is the finger's horizontal speed in pt/s. */
    fun end(clock: Double, vx: Double) {
        if (!touching) return
        touching = false
        val speed = -vx / DRAG_SPAN
        val dir = if (s > 0 || (s == 0.0 && speed > 0)) 1.0 else -1.0
        val reveal = abs(s)
        val fling = speed * dir
        releaseClock = clock
        releaseFrom = page
        releaseTo = (page + if (dir > 0) 1 else m.PAGES - 1) % m.PAGES
        releaseDir = dir
        releaseReveal = reveal
        releaseCommit = reveal > 0.35 || (fling > 2 && reveal > 0.04)
        releaseAge = 0.9 * min(reveal, 1.0)
        if (releaseCommit) {
            page = releaseTo
            pageAgeBase = clock - releaseAge
            hapticDue.add(clock)
        }
        lastClock = clock
    }

    // Reduced motion: a swipe changes the page with a cross-fade.
    private var fadeClock = -100.0
    private var fadeFrom = 0

    fun fadeScene(clock: Double): PickleFlavourWipeMotion.Scene {
        val x = m.seg(clock - fadeClock, 0.0, 0.3)
        if (x >= 1) return m.still(page)
        return m.still(fadeFrom).copy(to = page, fade = x)
    }

    fun fadeSwipe(clock: Double, dx: Double) {
        if (abs(dx) <= 50) return
        fadeFrom = page
        page = (page + if (dx < 0) 1 else m.PAGES - 1) % m.PAGES
        fadeClock = clock
        lastClock = clock
        hapticDue.add(clock)
    }

    fun fading(clock: Double) = clock - fadeClock < 0.35

    /** True once per due haptic. */
    fun takeHaptic(clock: Double): Boolean {
        if (hapticDue.none { it <= clock }) return false
        hapticDue.removeAll { it <= clock }
        return true
    }
}
