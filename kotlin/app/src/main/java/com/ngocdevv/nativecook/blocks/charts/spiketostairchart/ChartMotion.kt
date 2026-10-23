package com.ngocdevv.nativecook.blocks.charts.spiketostairchart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal enum class ChartMode {
    Loop,
    Transition,
    Rest,
}

/** The playhead owns transitions and gestures; the renderer only reads it during drawing. */
internal class ChartMotion(
    val autoPlay: Boolean,
    val reduced: Boolean,
    private val haptic: (Int) -> Unit,
) {
    var selected by mutableIntStateOf(if (reduced || autoPlay) 1 else 0)
        private set

    var accessibilityValue by mutableStateOf("Day 0, 0")
        private set

    var l = if (reduced || autoPlay) 0.0 else 4.15
    var mode =
        if (reduced) ChartMode.Rest else if (autoPlay) ChartMode.Loop else ChartMode.Transition
    var destination = selected
    private var queued = -1
    private var end = 6.9
    var elapsed = 0.0
    private var idleElapsed = 0.0
    private var touchedAt = 0.0
    var paused = false
    private var userRun = false
    var presented = 0.0
    var sceneOpacity = 1.0
    var intro = if (reduced || autoPlay) 1.0 else 0.0
    private var readyTime = 0.0
    private var fadeStart = -100.0
    private var swapped = true
    private var thumb = destination.toDouble()
    private var thumbVelocity = 0.0
    var pressed = -1
    var pressAmount = 0.0
    private var pressTarget = 0.0
    private var pressFrom = 0.0
    private var pressAt = -100.0
    var scrubU = 0.0
    var scrubAlpha = 0.0
    var dragging = false
    private var scrubVelocity = 0.0
    private var scrubTarget = 0.0
    private var released = -100.0
    private var contactAt = -100.0
    private var scrubFadingIn = false
    val columnTimes = DoubleArray(5) { -100.0 }

    private fun timing(p: Double): Double {
        val q = clamp(p)
        return if (q < .5) 2 * q * q else 1 - (-2 * q + 2).pow(2) / 2
    }

    private fun spring(
        value: Double,
        velocity: Double,
        target: Double,
        dt: Double,
        damping: Double,
        stiffness: Double,
    ): Pair<Double, Double> {
        val a = damping / 2
        val w = sqrt(stiffness - a * a)
        val x = value - target
        val b = (velocity + a * x) / w
        val e = exp(-a * dt)
        val next = e * (x * cos(w * dt) + b * sin(w * dt))
        val speed = e * ((-a * x + b * w) * cos(w * dt) + (-a * b - x * w) * sin(w * dt))
        return target + next to speed
    }

    fun step(delta: Double) {
        val dt = min(.05, max(0.0, delta))
        if (readyTime < .3) {
            readyTime += dt
            presented = timing(readyTime / .3)
            return
        }
        elapsed += dt
        if (mode == ChartMode.Rest) idleElapsed += dt else idleElapsed = 0.0
        if (!reduced) intro = min(1.0, intro + dt / .9)
        if (reduced && !swapped && elapsed - fadeStart >= .12) {
            swapped = true
            l = if (destination == 1) 0.0 else 6.9
            thumb = destination.toDouble()
            selected = destination
        }
        sceneOpacity =
            when {
                !swapped -> 1 - timing((elapsed - fadeStart) / .12)
                elapsed - fadeStart < .28 -> timing((elapsed - fadeStart - .12) / .16)
                else -> 1.0
            }
        if (mode == ChartMode.Loop) {
            val old = l
            l = (l + dt) % 12
            if (old < 3.6 && l >= 3.6) selected = 0
            if (old < 6.95 && l >= 6.95) selected = 1
        } else if (mode == ChartMode.Transition) {
            val old = l
            l = min(end, l + dt * (if (queued >= 0) 2.5 else 1.0))
            if (userRun && ((old < 6.35 && l >= 6.35) || (old < 9.41 && l >= 9.41))) haptic(2)
            if (l >= end) {
                mode = ChartMode.Rest
                userRun = false
                touchedAt = elapsed
                if (queued >= 0) {
                    val q = queued
                    queued = -1
                    if (q != destination) begin(q)
                }
            }
        }
        if (
            autoPlay &&
                !reduced &&
                paused &&
                mode == ChartMode.Rest &&
                !dragging &&
                pressed < 0 &&
                elapsed - touchedAt >= 3.5
        ) {
            l = if (destination == 1) 0.0 else 6.75
            mode = ChartMode.Loop
            paused = false
            scrubAlpha = 0.0
            scrubFadingIn = false
        }
        if (mode != ChartMode.Loop && !reduced) {
            val next = spring(thumb, thumbVelocity, destination.toDouble(), dt, 20.0, 260.0)
            thumb = next.first
            thumbVelocity = next.second
        }
        // Analytic integration retains release velocity without depending on the display refresh
        // rate.
        if (!dragging && scrubAlpha > 0 && !reduced) {
            val next = spring(scrubU, scrubVelocity, scrubTarget, dt, 22.0, 240.0)
            scrubU = next.first
            scrubVelocity = next.second
        }
        if (scrubFadingIn) {
            scrubAlpha = timing((elapsed - contactAt) / .12)
            if (elapsed - contactAt >= .12) scrubFadingIn = false
        }
        if (!dragging && scrubAlpha > 0 && elapsed - released > .9)
            scrubAlpha = 1 - timing((elapsed - released - .9) / .22)
        val pressProgress = clamp((elapsed - pressAt) / .12)
        // Preserve the source switch's 120 ms press curve.
        pressAmount = mix(pressFrom, pressTarget, pressEase(pressProgress))
    }

    private fun begin(d: Int) {
        destination = d
        end = if (d == 1) 10.45 else 6.9
        l = if (d == 1) 7.05 else 3.6
        mode = ChartMode.Transition
        userRun = true
        selected = d
    }

    fun touch() {
        touchedAt = elapsed
        paused = true
        if (mode == ChartMode.Loop) {
            when {
                l >= 3.6 && l < 6.9 -> {
                    destination = 0
                    end = 6.9
                    mode = ChartMode.Transition
                }
                l >= 7.05 && l < 10.45 -> {
                    destination = 1
                    end = 10.45
                    mode = ChartMode.Transition
                }
                else -> {
                    destination = if (l >= 6.35 && l < 7.05) 0 else 1
                    mode = ChartMode.Rest
                }
            }
            thumb = film(l, reduced).thumb
            thumbVelocity = 0.0
            selected = destination
        }
    }

    fun go(d: Int) {
        touch()
        scrubAlpha = 0.0
        scrubFadingIn = false
        if (d == destination) return
        if (reduced) {
            destination = d
            fadeStart = elapsed
            swapped = false
        } else if (mode == ChartMode.Transition) queued = d else begin(d)
    }

    fun press(d: Int, on: Boolean) {
        if (on) {
            touch()
            haptic(1)
            pressed = d
        }
        pressFrom = pressAmount
        pressAt = elapsed
        pressTarget = if (on) 1.0 else 0.0
        // Retain the pressed glyph while its release animation settles.
        if (!on) touchedAt = elapsed
    }

    fun clearPress() {
        if (pressTarget == 0.0 && elapsed - pressAt > .12) pressed = -1
    }

    fun startScrub(x: Double) {
        touch()
        if (mode != ChartMode.Rest) return
        dragging = true
        scrubU = clamp((x - 64) / 274, 0.0, if (destination == 1) .968 else .957)
        scrubVelocity = 0.0
        contactAt = elapsed
        scrubFadingIn = true
        released = -100.0
    }

    fun moveScrub(x: Double) {
        touchedAt = elapsed
        if (!dragging) return
        val u = clamp((x - 64) / 274, 0.0, if (destination == 1) .968 else .957)
        val cuts = if (destination == 1) ChartGeometry.risers else ChartGeometry.extrema
        for (i in cuts.indices) {
            if ((scrubU < cuts[i] && u >= cuts[i]) || (scrubU >= cuts[i] && u < cuts[i])) {
                haptic(0)
                if (destination == 1) columnTimes[i] = elapsed
            }
        }
        scrubU = u
    }

    fun endScrub(velocity: Double) {
        if (!dragging) return
        dragging = false
        released = elapsed
        touchedAt = elapsed
        scrubTarget = if (destination == 1 && !reduced) nearestTread(scrubU) else scrubU
        scrubVelocity = if (reduced) 0.0 else velocity / 274
        announce(if (reduced) scrubU else scrubTarget)
    }

    fun adjust(direction: Int) {
        touch()
        scrubU = clamp(scrubU + direction * .1, 0.0, if (selected == 1) .968 else .957)
        scrubTarget = scrubU
        scrubVelocity = 0.0
        scrubAlpha = 1.0
        scrubFadingIn = false
        released = elapsed
        announce(scrubU)
        haptic(0)
    }

    fun setProgress(value: Float) {
        touch()
        scrubU = clamp(value.toDouble(), 0.0, if (selected == 1) .968 else .957)
        scrubTarget = scrubU
        scrubVelocity = 0.0
        scrubAlpha = 1.0
        scrubFadingIn = false
        released = elapsed
        announce(scrubU)
    }

    private fun announce(u: Double) {
        val p = atU(destination, u)
        accessibilityValue =
            "Day ${(30*u/(if(destination == 1) 1.0 else .957)).roundToInt()}, ${(clamp(p.v)*100).roundToInt()}"
    }

    fun thumbPosition(f: ChartFilm) = if (mode == ChartMode.Loop) f.thumb else thumb

    fun state(): ChartFilm {
        val f = film(l, reduced)
        if (mode == ChartMode.Rest && !reduced) {
            f.life = f.l + idleElapsed
            if (f.d == 1) {
                val phase = (f.life % 4) / 1.6
                f.glow = 1 + .06 * sin(f.life * PI / 2)
                f.packet = smooth(phase / .08) * (1 - smooth((phase - .92) / .08))
                f.packetStart = clamp(phase - .06)
                f.packetEnd = clamp(phase)
            } else f.glow = 1 + .1 * sin(f.life * 53) * sin(f.life * 19)
        }
        return f
    }
}
