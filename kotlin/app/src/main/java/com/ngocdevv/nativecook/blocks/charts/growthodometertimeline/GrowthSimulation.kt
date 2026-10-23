package com.ngocdevv.nativecook.blocks.charts.growthodometertimeline

import kotlin.math.*

/** Fixed 240 Hz mechanical state. All positions are months; drum angles are digit cells. */
internal class GrowthSimulation(autoPlay: Boolean, val reduced: Boolean) {
    enum class Mode { Idle, Held, Drag, Coast, GoTo, Auto }

    class Drum(index: Int) {
        var theta = drumTarget(3000.0, index)
        var omega = 0.0
        var rate = 0.0
        var direction = 1.0
        var previous = theta
        var wraps = 0.0
        var spinning = false
        var runout = -1.0
    }

    var p = 0.0
    var velocity = 0.0
    var mode = Mode.Idle
    var target = 0.0
    var drums = Array(5) { Drum(it) }
    var clock = 0.0
    var steps = 0L
    var accumulator = 0.0
    var loopStep = 0
    val auto = autoPlay && !reduced
    var paused = false
    var returning = false
    var lastTouch = -100.0
    var raw0 = 0.0
    var grabTarget = 0.0
    var grip = 0.0
    var gripVelocity = 0.0
    var down = false
    var pressed = false
    var press = 0.0
    var pressVelocity = 0.0
    var touchStart = 0.0
    var gripFrom = 0.0
    var halo = 0.0
    var phase = 0.0
    var heroStart = -100.0
    var armed = true
    var tickStart = -100.0
    var month = 0
    var announced = 0
    var events = 0
    var lastHaptic = -100.0
    var endLatch = 0
    var reducedFrom = 0.0
    var reducedStart = 0.0
    var displayMonth = 0
    var reducedMonth = 0
    var fadeStart = -100.0
    var haloFrom = 0.0

    val age get() = clock - heroStart
    val time get() = if (reduced) 0.0 else if (auto && !paused) loopStep * DT else clock
    val flash get() = if (reduced) 1 - smooth(0.0, .2, age) else envelope(age, .35)
    val surge get() = if (reduced) flash * .25 else envelope(age, .55)
    val tick get() = 1 - smooth(0.0, .16, clock - tickStart)
    val numberAlpha: Double get() {
        val elapsed = clock - fadeStart
        return if (!reduced) 1.0 else 1 - .7 * if (elapsed < .08) smooth(0.0, .08, elapsed) else 1 - smooth(.08, .16, elapsed)
    }
    val chevronPhase: Double get() {
        if (reduced) return 0.0
        if (!auto || paused) return phase
        return .9 * time + .6 * p + .2 * smooth(9.6, 11.3, time)
    }

    fun touch() {
        phase = chevronPhase
        grabTarget = if (mode == Mode.GoTo) target else floor(p + .5)
        paused = true
        returning = false
        lastTouch = clock
        raw0 = inverseRubber(p)
        mode = Mode.Held
        velocity = 0.0
        down = true
        touchStart = clock
        gripFrom = grip
    }

    fun drag(translationY: Double, velocityY: Double) {
        p = rubber(raw0 - translationY / 64)
        velocity = (-velocityY / 64).coerceIn(-22.0, 22.0)
        mode = Mode.Drag
        lastTouch = clock
    }

    fun release(velocityY: Double) {
        down = false
        lastTouch = clock
        if (mode == Mode.Drag || mode == Mode.Held) {
            velocity = if (mode == Mode.Held) 0.0 else (-velocityY / 64).coerceIn(-22.0, 22.0)
            if (reduced) goTo(floor(p + .5)) else mode = Mode.Coast
        }
    }

    fun nextMonth(direction: Int = 1, rewind: Boolean = true, fromGrab: Boolean = false) {
        phase = chevronPhase
        val from = if (fromGrab || down) grabTarget else if (mode == Mode.GoTo) target else floor(p + .5)
        paused = true
        returning = false
        lastTouch = clock
        goTo(if (rewind && from >= 12) 0.0 else from + direction)
    }

    fun goTo(destination: Double) {
        target = destination.coerceIn(0.0, 12.0)
        mode = Mode.GoTo
        reducedFrom = p
        reducedStart = clock
    }

    fun advance(frameDelta: Double) {
        events = 0
        accumulator += frameDelta.coerceIn(0.0, .05)
        while (accumulator >= DT - 1e-10) {
            accumulator -= DT
            substep()
        }
    }

    private fun autoStep() {
        val elapsed = loopStep * DT
        if (elapsed < .8) {
            p = 0.0
            velocity = 0.0
            mode = Mode.Auto
        } else if (elapsed < 6.158333333) {
            val glide = elapsed - .8
            val raw = if (glide < .35) 2.2 * glide * glide / .7 else 2.2 * (glide - .175)
            if (raw < 11.4) {
                p = raw - .04 * sin(TAU * raw) / TAU
                velocity = min(2.2, 2.2 * glide / .35) * (1 - .04 * cos(TAU * raw))
                mode = Mode.Auto
            } else if (mode == Mode.Auto) goTo(12.0)
        }
        if (elapsed >= 6.158333333 && elapsed < 9.6 && mode == Mode.Auto) goTo(12.0)
        if (loopStep == 2304) goTo(0.0)
    }

    private fun wheelStep() {
        if (mode != Mode.GoTo && mode != Mode.Coast) return
        if (reduced) {
            val progress = smooth(0.0, .18, clock - reducedStart)
            p = reducedFrom + (target - reducedFrom) * progress
            velocity = 0.0
            if (progress == 1.0) mode = Mode.Idle
            return
        }
        var acceleration: Double
        if (mode == Mode.GoTo) {
            acceleration = -210 * (p - target) - 18 * velocity
        } else {
            val speed = abs(velocity)
            val damping = 1.9 + 17 * (1 - smooth(1.2, 3.5, speed))
            val detent = 260 * (1 - .75 * smooth(2.0, 9.0, speed))
            acceleration = -damping * velocity - detent * sin(TAU * p) / TAU
            if (p > 12) acceleration += -420 * min(1.0, p - 12) - 37 * velocity
            if (p < 0) acceleration += -420 * max(-1.0, p) - 37 * velocity
        }
        val maximum = if (mode == Mode.GoTo) 11.0 else 22.0
        velocity = (velocity + acceleration * DT).coerceIn(-maximum, maximum)
        p += velocity * DT
        val stop = if (mode == Mode.GoTo) target else floor(p + .5).coerceIn(0.0, 12.0)
        if (abs(velocity) < .02 && abs(p - stop) < .002) {
            p = stop
            velocity = 0.0
            mode = Mode.Idle
        }
    }

    private fun substep() {
        steps++
        clock = steps * DT
        val playing = auto && !paused
        if (playing) {
            loopStep++
            autoStep()
        }
        wheelStep()
        if (auto && paused && !down && mode == Mode.Idle && clock - lastTouch >= 3.5) {
            if (!returning) {
                returning = true
                goTo(0.0)
            } else {
                paused = false
                returning = false
                loopStep = 0
            }
        }
        if (p < 11.5) armed = true
        val hero = armed && p >= 11.999
        if (hero) {
            heroStart = clock
            armed = false
            if (!playing) events = events or 4
        }
        val nearest = floor(p + .5).toInt().coerceIn(0, 12)
        if (nearest != month) {
            month = nearest
            tickStart = clock
            if (!playing && !hero && nearest != 12 && clock - lastHaptic >= .028) {
                events = events or 1
                lastHaptic = clock
            }
        }
        val end = if (p > 12) 1 else if (p < 0) -1 else 0
        if (end != 0 && end != endLatch && velocity * end > 1.5 && !playing && !hero) events = events or 2
        endLatch = end
        if (mode == Mode.Idle && announced != nearest) {
            announced = nearest
            events = events or 8
        }
        if (down) {
            val value = ((clock - touchStart) / .12).coerceIn(0.0, 1.0)
            var progress = value
            repeat(5) {
                val remaining = 1 - progress
                val bezier = .69 * remaining * remaining * progress + .96 * remaining * progress * progress + progress * progress * progress
                val derivative = .69 * remaining * remaining + .54 * remaining * progress + 2.04 * progress * progress
                progress = (progress - (bezier - value) / max(.001, derivative)).coerceIn(0.0, 1.0)
            }
            grip = gripFrom + (1 - gripFrom) * (1 - (1 - progress).pow(3))
            gripVelocity = 0.0
        } else {
            gripVelocity += (-260 * grip - 24 * gripVelocity) * DT
            grip += gripVelocity * DT
        }
        pressVelocity += (300 * ((if (pressed) 1.0 else 0.0) - press) - 26 * pressVelocity) * DT
        press += pressVelocity * DT
        if (!reduced) phase += (.9 + .6 * velocity) * DT
        if (reduced && nearest != reducedMonth) {
            reducedMonth = nearest
            fadeStart = clock
            haloFrom = halo
        }
        if (reduced && clock - fadeStart >= .08) displayMonth = reducedMonth
        val value = valueAt(if (reduced) displayMonth.toDouble() else p)
        val landing = !down && !armed && target == 12.0 && (mode == Mode.GoTo || mode == Mode.Idle)
        val drumValue = if (landing && !reduced) 10000.0 else value
        drums.forEachIndexed { index, drum ->
            val destination = drumTarget(drumValue, index)
            if (reduced) {
                drum.theta = destination
                drum.omega = 0.0
                drum.previous = destination
            } else drumStep(drum, destination, index, landing)
        }
        val level = ((if (reduced) valueAt(reducedMonth.toDouble()) else value) - 3000) / 7000
        halo = if (reduced) haloFrom + (level - haloFrom) * smooth(0.0, .24, clock - fadeStart) else halo + (level - halo) * (1 - exp(-DT / .09))
        if (level == 0.0 && halo < .00001) halo = 0.0
        if (playing && loopStep >= 2880) resetLoop()
    }

    private fun resetLoop() {
        p = 0.0
        velocity = 0.0
        mode = Mode.Idle
        target = 0.0
        drums = Array(5) { Drum(it) }
        loopStep = 0
        paused = false
        returning = false
        lastTouch = -100.0
        raw0 = 0.0
        grabTarget = 0.0
        grip = 0.0
        gripVelocity = 0.0
        down = false
        pressed = false
        press = 0.0
        pressVelocity = 0.0
        touchStart = 0.0
        gripFrom = 0.0
        halo = 0.0
        phase = 0.0
        heroStart = -100.0
        armed = true
        tickStart = -100.0
        month = 0
        lastHaptic = -100.0
        endLatch = 0
        reducedFrom = 0.0
        reducedStart = 0.0
        displayMonth = 0
        reducedMonth = 0
        fadeStart = -100.0
        haloFrom = 0.0
    }

    companion object {
        const val DT = 1.0 / 240
        const val TAU = PI * 2
        val values = intArrayOf(3000, 3280, 3640, 4150, 4560, 5000, 5720, 6510, 7280, 8050, 9150, 9640, 10000)
        val months = arrayOf("Mar 2024", "Apr 2024", "May 2024", "Jun 2024", "July 2024", "Aug 2024", "Sept 2024", "Oct 2024", "Nov 2024", "Dec 2024", "Jan 2025", "Feb 2025", "Mar 2025")
        val fullMonths = arrayOf("March 2024", "April 2024", "May 2024", "June 2024", "July 2024", "August 2024", "September 2024", "October 2024", "November 2024", "December 2024", "January 2025", "February 2025", "March 2025")
        private val stiffness = doubleArrayOf(900.0, 640.0, 420.0, 300.0, 190.0)
        private val damping = doubleArrayOf(.50, .50, .48, .45, .42)
        private val maxSpeed = doubleArrayOf(14.0, 11.0, 8.5, 6.0, 4.0)
        private val carry = doubleArrayOf(0.0, .1, .02, .004, .0012)

        fun smooth(low: Double, high: Double, value: Double): Double {
            val progress = ((value - low) / (high - low)).coerceIn(0.0, 1.0)
            return progress * progress * (3 - 2 * progress)
        }

        fun valueAt(position: Double): Double {
            val scaled = position.coerceIn(0.0, 12.0)
            val index = min(11, floor(scaled).toInt())
            return values[index] + (values[index + 1] - values[index]) * (scaled - index)
        }

        fun rubber(raw: Double): Double {
            if (raw < 0) return -1.2 * (1 - 1 / (-raw * .55 / 1.2 + 1))
            if (raw > 12) return 12 + 1.2 * (1 - 1 / ((raw - 12) * .55 / 1.2 + 1))
            return raw - .3 * sin(TAU * raw) / TAU
        }

        fun inverseRubber(position: Double): Double {
            if (position < 0 || position > 12) {
                val excess = min(1.199, if (position < 0) -position else position - 12)
                val raw = excess / (.55 * (1 - excess / 1.2))
                return if (position < 0) -raw else 12 + raw
            }
            var raw = position
            repeat(3) { raw -= (rubber(raw) - position) / (1 - .3 * cos(TAU * raw)) }
            return raw
        }

        fun drumTarget(value: Double, index: Int): Double {
            if (index == 0) return value
            val scaled = value / 10.0.pow(index)
            val whole = floor(scaled)
            return whole + smooth(1 - carry[index], 1.0, scaled - whole)
        }

        fun drumStep(drum: Drum, target: Double, index: Int, landing: Boolean) {
            val delta = target - drum.previous
            drum.previous = target
            drum.rate += (abs(delta) / DT - drum.rate) * (1 - exp(-DT / .06))
            if (abs(delta) > 1e-8) drum.direction = sign(delta)
            if (!landing && drum.rate > .9 * maxSpeed[index]) {
                drum.runout = -1.0
                drum.spinning = true
                drum.omega += (drum.direction * maxSpeed[index] - drum.omega) * (1 - exp(-DT / .08))
            } else {
                if (drum.spinning && index < 4) {
                    if (drum.runout < 0) drum.runout = if (index < 3) index * .15 else 0.0
                    if (drum.runout > 0) {
                        drum.runout = max(0.0, drum.runout - DT)
                        drum.omega += (drum.direction * maxSpeed[index] - drum.omega) * (1 - exp(-DT / .08))
                        drum.theta += drum.omega * DT
                        return
                    }
                    val runway = .5 + if (index < 3) 1.5 * index else 0.0
                    drum.wraps = if (drum.direction > 0) ceil((drum.theta + runway - target) / 10) else floor((drum.theta - runway - target) / 10)
                }
                val error = target + 10 * drum.wraps - drum.theta
                drum.spinning = false
                drum.omega += (stiffness[index] * error - 2 * damping[index] * sqrt(stiffness[index]) * drum.omega) * DT
                if (abs(error) < .002 && abs(drum.omega) < .02) {
                    drum.theta = target + 10 * drum.wraps
                    drum.omega = 0.0
                    return
                }
            }
            drum.theta += drum.omega * DT
        }

        fun envelope(age: Double, decay: Double): Double {
            if (age < 0 || age > 4) return 0.0
            if (age < .14) return 1 - (1 - age / .14).pow(3)
            return if (age < .2) 1.0 else exp(-(age - .2) / decay)
        }
    }
}
