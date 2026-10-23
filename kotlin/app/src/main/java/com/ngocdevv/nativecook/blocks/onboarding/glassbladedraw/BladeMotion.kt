package com.ngocdevv.nativecook.blocks.onboarding.glassbladedraw

import kotlin.math.*
import kotlin.random.Random

/** One frame clock owns the loop and every interrupted touch animation. */
internal class BladeMotion(var autoPlay: Boolean, initialReduced: Boolean, val haptic: (Boolean) -> Unit) {
    private enum class Mode { Opening, Loop, Idle, Held, Drag, Draw, Step, Spring, Pulse, Fade, Returning }
    var reduced = initialReduced
        set(value) {
            if (field == value) return
            field = value
            if (value && !touchOwned) {
                freeze()
                step(floor(frame.pos + .5))
            }
        }
    var touchOwned = false
        private set
    var frame = BladeFrame(charge = 0.0, intro = 0.0)
        private set
    var seconds = 0.0
        private set
    var opacity = 0.0
        private set
    var drawing = false
        private set
    private var mode = Mode.Opening
    private var elapsed = 0.0
    private var age = 0.0
    private var base = 0.0
    private var initial = frame
    private var target = 0.0
    private var duration = .7
    private var velocity = 0.0
    private var landed = false
    private var tactile = false
    private var previousHero = 0

    fun freeze() {
        mode = Mode.Idle
        elapsed = 0.0
        drawing = false
        tactile = false
        initial = frame
    }

    private fun begin(nextMode: Mode, next: Double = frame.pos) {
        initial = frame
        elapsed = 0.0
        target = next
        mode = nextMode
    }

    fun press(): Boolean {
        if (touchOwned) return false
        touchOwned = true
        freeze()
        begin(Mode.Held)
        haptic(false)
        return true
    }

    fun draw() {
        touchOwned = false
        val next = floor(frame.pos + .5) + 3 + Random.nextInt(3)
        begin(if (reduced) Mode.Fade else Mode.Draw, next)
        drawing = true
        tactile = true
        landed = false
    }

    fun step(next: Double) {
        begin(if (reduced) Mode.Fade else Mode.Step, next)
        duration = .7 * sqrt(max(1.0, abs(next - frame.pos)))
    }

    fun startDrag(): Boolean {
        if (touchOwned) return false
        touchOwned = true
        freeze()
        begin(Mode.Drag)
        return true
    }

    fun drag(translation: Double, slot: Double) {
        tactile = true
        frame = frame.copy(pos = initial.pos + translation / slot)
    }

    fun endFan(x: Double, width: Double, slot: Double, dragged: Boolean, speed: Double, cancelled: Boolean) {
        touchOwned = false
        if (dragged || cancelled) {
            velocity = if (cancelled) 0.0 else speed / slot
            val next = floor(frame.pos + (velocity * .16).coerceIn(-4.0, 4.0) + .5)
            begin(if (reduced) Mode.Fade else Mode.Spring, next)
            return
        }
        val slots = floor((x - width / 2) / slot + .5).coerceIn(-3.0, 3.0)
        if (slots != 0.0 || reduced) step(floor(frame.pos + .5) - slots)
        else begin(Mode.Pulse)
    }

    fun accessibilityStep(delta: Int): Boolean {
        if (touchOwned) return false
        freeze()
        step(floor(frame.pos + .5) + delta)
        return true
    }

    fun accessibilityDraw(): Boolean {
        if (!press()) return false
        draw()
        return true
    }

    fun cancelPress() {
        touchOwned = false
        step(floor(frame.pos + .5))
    }

    private fun restored(t: Double): BladeFrame {
        return frame.copy(
            charge = 1 + BladeTimeline.releasedPress(t, initial.charge - 1),
            flare = initial.flare * (1 - BladeTimeline.entrance(t / .18)),
            ctaEnergy = initial.ctaEnergy + (1 - initial.ctaEnergy) * BladeTimeline.entrance(t / .3),
            press = BladeTimeline.releasedPress(t, initial.press),
            intro = initial.intro + (1 - initial.intro) * BladeTimeline.entrance(t / .3),
        )
    }

    private fun finish() {
        frame = frame.copy(charge = 1.0, flare = 0.0, press = 0.0, ctaEnergy = 1.0, intro = 1.0)
        mode = Mode.Idle
        drawing = false
        tactile = false
        elapsed = 0.0
    }

    fun advance(dt: Double) {
        val delta = dt.coerceIn(0.0, .1)
        age += delta
        if (!reduced) seconds += delta
        opacity = BladeTimeline.entrance(age / .3)
        elapsed += delta
        val t = elapsed
        when (mode) {
            Mode.Opening -> {
                frame = if (reduced) BladeFrame(still = 1.0) else frame.copy(
                    intro = BladeTimeline.entrance(t / .9), charge = BladeTimeline.entrance((t - .4) / .5))
                if (t >= if (reduced) .3 else .9) {
                    finish()
                    if (autoPlay && !reduced) mode = Mode.Loop
                }
            }
            Mode.Loop -> {
                if (reduced || !autoPlay) step(floor(frame.pos + .5))
                else frame = BladeTimeline.loopState(t, base)
            }
            Mode.Held -> frame = frame.copy(press = initial.press + (1 - initial.press) * BladeTimeline.entrance(t / .11))
            Mode.Drag -> if (tactile) frame = restored(t)
            Mode.Draw -> {
                val n = target - initial.pos
                val state = BladeTimeline.drawState(t, n, initial = initial)
                frame = state.copy(pos = initial.pos + state.pos,
                    intro = initial.intro + (1 - initial.intro) * BladeTimeline.entrance(t / .3))
                if (!landed && t >= BladeTimeline.flareStart(n)) {
                    landed = true
                    haptic(true)
                }
                if (t >= BladeTimeline.drawDuration(n)) finish()
            }
            Mode.Step, Mode.Returning -> {
                frame = restored(t).copy(pos = initial.pos + (target - initial.pos) * BladeTimeline.move(t / duration))
                if (t >= duration) {
                    val returning = mode == Mode.Returning
                    finish()
                    if (returning && autoPlay && !reduced) {
                        base = target
                        mode = Mode.Loop
                    }
                }
            }
            Mode.Spring -> {
                val w = sqrt(150.0 - 10.5 * 10.5)
                val displacement = initial.pos - target
                val b = (velocity + 10.5 * displacement) / w
                val decay = exp(-10.5 * t)
                val offset = decay * (displacement * cos(w * t) + b * sin(w * t))
                val speed = decay * ((b * w - 10.5 * displacement) * cos(w * t) - (displacement * w + 10.5 * b) * sin(w * t))
                frame = restored(t).copy(pos = target + offset)
                if ((abs(offset) < .001 && abs(speed) < .01) || t > 2) {
                    frame = frame.copy(pos = target)
                    finish()
                }
            }
            Mode.Pulse -> {
                val charge = if (t < .12) initial.charge + (1.15 - initial.charge) * BladeTimeline.entrance(t / .12)
                    else 1.15 - .15 * BladeTimeline.entrance((t - .12) / .4)
                frame = restored(t).copy(charge = charge)
                if (t >= .52) finish()
            }
            Mode.Fade -> {
                frame = restored(t).copy(flare = 0.0, intro = 1.0,
                    pos = if (t < .16) initial.pos else target,
                    charge = if (t < .16) initial.charge * (1 - BladeTimeline.entrance(t / .16))
                        else BladeTimeline.entrance((t - .16) / .24))
                if (t >= .4) finish()
            }
            Mode.Idle -> if (autoPlay && !reduced && t >= 3.5) {
                begin(Mode.Returning, floor(frame.pos / 9 + .5) * 9)
                duration = .9
            }
        }
        frame = frame.copy(still = if (reduced) 1.0 else 0.0)
        val hero = floor(frame.pos + .5).toInt()
        if (hero != previousHero && tactile) haptic(false)
        previousHero = hero
    }
}
