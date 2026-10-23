package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import androidx.compose.animation.core.CubicBezierEasing
import com.ngocdevv.nativecook.blocks.charts.vitalschipmorph.VitalsChipMorphMotion as M
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** UI-thread owner; only draw/layout invalidation and settled semantics leave this controller. */
internal class VitalsChipMorphController {
    var lo = 0
    var hi = 0
    var u = 0.0
    var target = 0
    var beat = 0.0
    var press = 0.0
    var pressChip = -1
    var quiet = false
    var autoPlay = false
    var foreground = true
    var current = 0
    var clock = 0.0
    /** Liquid-material clock, shape speed, and fractional beats since the heart settled. */
    var fxTime = 0.0
    var speed = 0.0
    var beatCount = -1.0
    var shift = 0.0
    var dragging = false
    var animating = false
    private var automatic = false
    private var lastTs = -1.0
    private var loopOrigin = 0.0
    private var animStart = 0.0
    private var animFrom = 0.0
    private var animV0 = 0.0
    private var animKind = 0
    private var pending = -1
    private var dragStartState = -1
    private var dragBaseU = 0.0
    private var lastTouch = -1e9
    private var settledAt = 0.0
    private var previousU = 0.0
    private var previousPair = -1
    private var shiftStart = 0.0
    private var shiftFrom = 0.0
    private var shiftReturning = false
    private val pressFrom = DoubleArray(6)
    private val pressTo = DoubleArray(6)
    private val pressStart = DoubleArray(6)
    private val pressDuration = DoubleArray(6) { .16 }
    private val pressEase = CubicBezierEasing(0f, 0f, .58f, 1f)
    private val autoBlend = M.Blend()
    private val previousAutoBlend = M.Blend()

    fun configure(autoPlay: Boolean, quiet: Boolean) {
        this.autoPlay = autoPlay
        this.quiet = quiet
        if (quiet || !autoPlay) {
            if (automatic) {
                automatic = false
                lo = 0
                hi = 0
                u = 0.0
                target = 0
                beat = 0.0
                press = 0.0
                animating = false
            }
        } else if (!automatic && lo == 0 && hi == 0 && !animating) {
            automatic = true
            loopOrigin = clock
        }
    }

    fun resetClock() {
        lastTs = -1.0
    }

    fun needsFrames(): Boolean {
        if (!foreground) return false
        if (automatic || animating || dragging || shiftReturning || (!quiet && lo == hi && lo == 2) || (autoPlay && !quiet)) {
            return true
        }
        for (index in pressStart.indices) {
            if (clock - pressStart[index] < pressDuration[index]) return true
        }
        return false
    }

    private fun springOmega(kind: Int): Double {
        return when (kind) {
            1 -> M.SPRINGS.release.omega
            2 -> M.SPRINGS.idle.omega
            else -> M.SPRINGS.tap.omega
        }
    }

    private fun springZeta(kind: Int): Double {
        return when (kind) {
            1 -> M.SPRINGS.release.zeta
            2 -> M.SPRINGS.idle.zeta
            else -> M.SPRINGS.tap.zeta
        }
    }

    fun goTo(to: Int, kind: Int = 0, velocity: Double = 0.0) {
        val a = lo
        val b = hi
        var x0: Double
        var v0 = velocity
        if (a == b) {
            if (to == a) return
            lo = minOf(a, to)
            hi = maxOf(a, to)
            u = if (a < to) 0.0 else 1.0
            x0 = 0.0
        } else if (to == a || to == b) {
            val towardNow = if (target == b) u else 1 - u
            if (animating && !dragging) {
                val vNow = M.springVelocity(
                    clock - animStart,
                    animFrom,
                    animV0,
                    springOmega(animKind),
                    springZeta(animKind),
                )
                v0 = if (target == to) vNow else -vNow
            }
            x0 = if (to == b) u else 1 - u
            if (target == to) x0 = towardNow
        } else {
            pending = to
            return
        }
        target = to
        animating = true
        animStart = clock
        animFrom = x0
        animV0 = v0
        animKind = if (quiet) 3 else kind
        pending = -1
    }

    fun touch() {
        lastTouch = clock
        if (!automatic) return
        automatic = false
        beat = 0.0
        press = 0.0
        if (lo != hi && (u < .001 || u > .999)) {
            val at = if (u < .001) lo else hi
            lo = at
            hi = at
            u = 0.0
            target = at
            settledAt = clock
            current = at
        } else if (lo != hi) {
            animating = true
            animStart = clock
            animFrom = if (target == hi) u else 1 - u
            animV0 = 0.0
            animKind = 0
        }
    }

    fun selectChip(index: Int): Boolean {
        touch()
        val screen = VitalsChipMorphTheme.CHIP_SCREEN.getOrElse(index) { -1 }
        if (screen < 0) return false
        val changed = (if (lo == hi) lo else target) != screen
        goTo(screen)
        return changed
    }

    fun chipPress(index: Int, down: Boolean) {
        pressFrom[index] = chipPressValue(index)
        pressTo[index] = if (down) 1.0 else 0.0
        pressStart[index] = clock
        pressDuration[index] = if (down) .09 else .16
    }

    fun chipPressValue(index: Int): Double {
        val p = M.clamp01((clock - pressStart[index]) / pressDuration[index])
        return M.lerp(pressFrom[index], pressTo[index], pressEase.transform(p.toFloat()).toDouble())
    }

    fun tick(timestamp: Double) {
        if (!foreground) {
            resetClock()
            return
        }
        val prev = lastTs
        lastTs = timestamp
        if (prev < 0) return
        val dt = (timestamp - prev).coerceIn(0.0, .1)
        clock += dt
        if (shiftReturning) {
            val tau = clock - shiftStart
            val damped = rubberOmega * sqrt(1 - .8 * .8)
            val envelope = exp(-.8 * rubberOmega * tau)
            val displacement = envelope * (cos(damped * tau) + .8 * rubberOmega / damped * sin(damped * tau))
            val velocity = -.8 * rubberOmega * displacement +
                envelope * (.8 * rubberOmega * cos(damped * tau) - damped * sin(damped * tau))
            shift = shiftFrom * displacement
            if (displacement * displacement + velocity * velocity / (rubberOmega * rubberOmega) <= 6e-9) {
                shift = 0.0
                shiftReturning = false
            }
        }
        if (automatic) {
            val loopTime = ((clock - loopOrigin) % M.LOOP + M.LOOP) % M.LOOP
            val b = M.loopBlend(loopTime, autoBlend)
            fxTime = loopTime
            speed = M.loopSpeed(loopTime, previousAutoBlend, b)
            beatCount = M.loopBeatCount(loopTime)
            previousU = b.u
            previousPair = b.lo * 3 + b.hi
            lo = b.lo
            hi = b.hi
            u = b.u
            target = b.target
            beat = b.beat
            press = b.press
            pressChip = b.pressChip
            return
        }
        fxTime += dt
        if (animating && !dragging) {
            val tau = clock - animStart
            val omega = springOmega(animKind)
            val zeta = springZeta(animKind)
            val x = if (animKind == 3) M.clamp01(animFrom + tau / M.SPRINGS.reducedFade)
                else M.spring(tau, animFrom, animV0, omega, zeta)
            val v = if (animKind == 3) 0.0 else M.springVelocity(tau, animFrom, animV0, omega, zeta)
            val done = if (animKind == 3) x >= 1 else tau > .1 && abs(1 - x) < .0015 && abs(v) < .03
            if (done) {
                lo = target
                hi = target
                u = 0.0
                animating = false
                settledAt = clock
                current = target
                val next = pending
                if (next >= 0 && next != target) goTo(next)
            } else {
                u = if (target == hi) M.clamp01(x) else 1 - M.clamp01(x)
            }
        }
        val settled = lo == hi
        val pair = lo * 3 + hi
        val rawSpeed = if (pair == previousPair && dt > 0.0) (u - previousU) / dt else 0.0
        speed += (rawSpeed - speed) * M.LIQUID.speedSmoothing
        previousU = u
        previousPair = pair
        beatCount = if (settled && lo == 2 && !quiet) maxOf(clock - settledAt, 0.0) / M.BEAT else -1.0
        beat = if (settled && lo == 2 && !quiet) {
            M.pulse((((clock - settledAt) / M.BEAT) % 1 + 1) % 1) * M.clamp01((clock - settledAt) / .3)
        } else 0.0
        if (autoPlay && !quiet && !dragging && !animating && clock - lastTouch > M.RESUME_AFTER) {
            if (settled && lo != 0) goTo(0, 2)
            else if (settled) {
                automatic = true
                loopOrigin = clock
            }
        }
    }

    fun beginDrag() {
        touch()
        animating = false
        dragging = true
        shiftReturning = false
        dragStartState = if (lo == hi) lo else -1
        dragBaseU = u
    }

    /** Translation and velocity are in reference pixels, independent of screen density. */
    fun drag(translation: Double) {
        val d = translation / M.DRAG.span
        val start = dragStartState
        if (start >= 0) {
            val next = if (d < 0) start + 1 else start - 1
            if (next < 0 || next > 2) {
                lo = start
                hi = start
                u = 0.0
                shift = translation * M.DRAG.rubber / (1 + abs(translation) / M.DRAG.rubberFalloff)
                return
            }
            shift = 0.0
            lo = minOf(start, next)
            hi = maxOf(start, next)
            target = next
            val p = minOf(abs(d), 1.0)
            u = if (next > start) p else 1 - p
        } else u = M.clamp01(dragBaseU - d)
    }

    fun endDrag(velocity: Double, cancelled: Boolean = false): Boolean {
        dragging = false
        lastTouch = clock
        if (cancelled) return false
        shiftStart = clock
        shiftFrom = shift
        shiftReturning = shift != 0.0
        if (quiet) {
            shift = 0.0
            shiftReturning = false
        }
        if (lo == hi) return false
        val vu = -velocity / M.DRAG.span
        val forward = vu > M.DRAG.commitVelocity || (u > M.DRAG.commitDistance && vu > -M.DRAG.commitVelocity)
        val to = if (forward) hi else lo
        val haptic = dragStartState >= 0 && to != dragStartState
        target = to
        animating = true
        animStart = clock
        animFrom = if (to == hi) u else 1 - u
        animV0 = if (to == hi) vu else -vu
        animKind = if (quiet) 3 else 1
        return haptic
    }

    companion object {
        // Reanimated duration spring: 400 ms perceptual / 600 ms physical, mass 4, threshold 6e-9.
        private val rubberOmega: Double = run {
            fun energyDifference(stiffness: Double): Double {
                val omega = sqrt(stiffness / 4) * .8
                val envelope = exp(-omega * .6)
                val position = (1 + omega * .6) * envelope
                val velocity = -(1 + omega * .6) * envelope * omega + omega * envelope
                return (stiffness * position * position + 4 * velocity * velocity) / stiffness - 6e-9
            }
            var low = 2.220446049250313e-16
            var high = 8000.0
            var current = (high + low) / 2
            val direction = if (energyDifference(high) >= energyDifference(low)) 1 else -1
            var remaining = 100
            while (abs(energyDifference(current)) > 6e-12 && remaining > 0) {
                remaining--
                if (energyDifference(current) * direction < 0) low = current else high = current
                current = (high + low) / 2
            }
            sqrt(current / 4)
        }
    }
}
