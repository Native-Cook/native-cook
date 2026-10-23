package com.ngocdevv.nativecook.blocks.progress.healthagedial

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class HealthAgeDialRenderFrame(
    val scroll: Double = 0.0,
    val years: Double = REST_YEARS,
    val trail: Double = 0.0,
    val revealElapsed: Double = -1.0,
    val revealOpacity: Double = 1.0,
    val lineProgress: Double = 0.0,
    val guides: Double = 0.0,
    val count1: Double = 0.0,
    val count2: Double = 0.0,
    val badge1: Double = -1.0,
    val badge2: Double = -1.0,
    val bump: Double = 0.0,
    val lift: Double = 0.0,
    val chipPress: Double = 0.0,
)

internal class HealthAgeDialPlayback {
    private enum class Mode {
        AUTOMATIC,
        MANUAL,
    }

    private var mode = Mode.MANUAL
    private var loopStart = 0.0
    private var autoPlay = false
    private var reduced = false
    private var pausedAt: Double? = null

    private var scroll = 0.0
    private var scrollReleaseAt: Double? = null
    private var scrollReleaseFrom = 0.0
    private var scrollReleaseVelocity = 0.0
    private var maxScroll = 0.0
    private var years = REST_YEARS
    private var yearsReleaseAt: Double? = null
    private var yearsReleaseFrom = REST_YEARS
    private var yearsReleaseVelocity = 0.0
    private var lastTouch = -100.0
    private var dragging = false
    private var forcedReturnAt: Double? = null
    private var forcedReturnFrom = 0.0

    private var revealAt: Double? = null
    private var fadeAt: Double? = null
    private var landAt = -100.0
    private var landed = false
    private var lastYearStep = floor(REST_YEARS).toInt()

    private var liftFrom = 0.0
    private var liftTarget = 0.0
    private var liftAt = 0.0
    private var liftDuration = 0.3
    private var liftDamping = 0.8
    private var pressFrom = 0.0
    private var pressTarget = 0.0
    private var pressAt = 0.0
    private var pressDuration = HealthAgeDialMotion.Press.pressIn

    fun start(autoPlay: Boolean, reduced: Boolean, now: Double) {
        this.autoPlay = autoPlay
        this.reduced = reduced
        mode = if (autoPlay && !reduced) Mode.AUTOMATIC else Mode.MANUAL
        loopStart = now
        scroll = 0.0
        scrollReleaseAt = null
        years = REST_YEARS
        yearsReleaseAt = null
        lastTouch = -100.0
        forcedReturnAt = null
        revealAt = if (reduced) now - 10.0 else null
        fadeAt = null
        landAt = -100.0
        landed = false
        liftFrom = 0.0
        liftTarget = 0.0
        pressFrom = 0.0
        pressTarget = 0.0
    }

    fun setConfiguration(autoPlay: Boolean, reduced: Boolean, now: Double) {
        val current = frame(now)
        this.autoPlay = autoPlay
        this.reduced = reduced
        if (reduced || !autoPlay) {
            mode = Mode.MANUAL
            scroll = current.scroll
            years = current.years
            scrollReleaseAt = null
            yearsReleaseAt = null
            forcedReturnAt = null
            revealAt = if (reduced) now - 10.0 else current.revealElapsed.takeIf { it >= 0.0 }?.let { now - it }
        } else if (mode == Mode.MANUAL) {
            lastTouch = now
        }
    }

    fun setActive(active: Boolean, now: Double) {
        if (!active && pausedAt == null) {
            pausedAt = now
            return
        }
        val pause = pausedAt
        if (!active || pause == null) {
            return
        }
        val shift = max(0.0, now - pause)
        loopStart += shift
        lastTouch += shift
        scrollReleaseAt = scrollReleaseAt?.plus(shift)
        yearsReleaseAt = yearsReleaseAt?.plus(shift)
        forcedReturnAt = forcedReturnAt?.plus(shift)
        revealAt = revealAt?.plus(shift)
        fadeAt = fadeAt?.plus(shift)
        landAt += shift
        liftAt += shift
        pressAt += shift
        pausedAt = null
    }

    fun setMaximumScroll(value: Double) {
        maxScroll = max(0.0, value)
    }

    fun beginTouch(now: Double) {
        val restartedAt = automaticRestartTime()
        val resumedAutomatic = mode == Mode.MANUAL && restartedAt?.let { now >= it } == true
        val current = if (resumedAutomatic) automaticFrame(now, restartedAt) else frame(now)
        if (mode == Mode.AUTOMATIC || resumedAutomatic) {
            mode = Mode.MANUAL
            scroll = current.scroll
            years = current.years
            val previous = automaticFrame(now - 1.0 / 240.0, if (resumedAutomatic) restartedAt else null)
            if (abs(current.years - REST_YEARS) > 0.001) {
                yearsReleaseFrom = current.years
                yearsReleaseVelocity = (current.years - previous.years) * 240.0
                yearsReleaseAt = now
            }
            if (current.revealElapsed >= 0.0) {
                revealAt = now - current.revealElapsed
            }
        } else {
            scroll = manualScroll(now)
            years = manualYears(now)
        }
        scrollReleaseAt = null
        forcedReturnAt = null
        if (revealAt?.let { it > now } == true) {
            revealAt = null
        }
        if (landAt > now) {
            landAt = -100.0
            landed = false
        }
        lastTouch = now
    }

    fun beginScroll(now: Double) {
        beginTouch(now)
        dragging = true
    }

    /** Returns true exactly when a finger-driven scroll first reaches the chip landing. */
    fun updateScroll(value: Double, now: Double, screenHeight: Double): Boolean {
        scroll = value.coerceIn(0.0, maxScroll)
        lastTouch = now
        updateReveal(now, screenHeight)
        val progress = HealthAgeDialMotion.clamp01(scroll / landingOffset)
        if (progress >= 0.999 && !landed) {
            landed = true
            if (!reduced) {
                landAt = now
                return true
            }
        } else if (progress < 0.9 && landed) {
            landed = false
        }
        return false
    }

    /** Returns the delayed landing haptic time, if inertial scrolling will cross the landing. */
    fun endScroll(velocity: Double, now: Double, screenHeight: Double): Double? {
        dragging = false
        scrollReleaseFrom = scroll
        scrollReleaseVelocity = velocity.coerceIn(-2200.0, 2200.0)
        scrollReleaseAt = now.takeIf { abs(scrollReleaseVelocity) > 20.0 }
        // RN refreshes lastTouch through momentum and onMomentumEnd. The closed-form decay reaches
        // its 20 pt/s rest threshold here, so resume still begins two seconds after momentum ends.
        val momentumDuration = if (scrollReleaseAt == null) 0.0 else ln(abs(scrollReleaseVelocity) / 20.0) / 2.0
        lastTouch = now + momentumDuration
        updateReveal(now, screenHeight)
        if (scrollReleaseAt == null) {
            return null
        }

        val chart = HealthAgeDialLayout.chart
        val visibleThreshold = -HealthAgeDialLayout.pinSafeTop + chart.top - (screenHeight - 150.0)
        if (revealAt == null) {
            momentumCrossingTime(scrollReleaseFrom, scrollReleaseVelocity, visibleThreshold)?.let { revealAt = now + it }
        }
        val goneThreshold = -HealthAgeDialLayout.pinSafeTop + chart.top - (screenHeight - 90.0)
        if (revealAt != null && fadeAt == null && scrollReleaseVelocity < 0.0) {
            momentumCrossingTime(scrollReleaseFrom, scrollReleaseVelocity, goneThreshold)?.let { fadeAt = now + it }
        }
        if (!reduced && !landed && scrollReleaseVelocity > 0.0) {
            val delay = momentumCrossingTime(scrollReleaseFrom, scrollReleaseVelocity, landingOffset)
            if (delay != null) {
                landAt = now + delay
                landed = true
                return delay
            }
        }
        return null
    }

    fun beginDial(now: Double) {
        beginTouch(now)
        dragging = true
        yearsReleaseAt = null
        setLift(target = 1.0, duration = 0.3, damping = 0.8, now = now)
    }

    /** Returns true when the dial crosses a whole-year detent. */
    fun updateDial(angle: Double, now: Double): Boolean {
        var next = angle / HealthAgeDialLayout.Dial.degreesPerYear
        if (abs(angle) > 150.0 && kotlin.math.sign(angle) != kotlin.math.sign(years)) {
            next = years
        }
        next = next.coerceIn(-HealthAgeDialLayout.Dial.maxYears.toDouble(), HealthAgeDialLayout.Dial.maxYears.toDouble())
        years = next
        lastTouch = now
        val step = floor(next).toInt()
        if (step != lastYearStep) {
            lastYearStep = step
            return true
        }
        return false
    }

    fun endDial(velocity: Double, now: Double) {
        dragging = false
        yearsReleaseFrom = years
        yearsReleaseVelocity = velocity.coerceIn(-40.0, 40.0)
        yearsReleaseAt = now
        lastTouch = now
        lastYearStep = floor(REST_YEARS).toInt()
        setLift(target = 0.0, duration = 0.4, damping = 1.0, now = now)
    }

    fun chipPress(pressed: Boolean, now: Double) {
        pressFrom = pressValue(now)
        pressTarget = if (pressed) 1.0 else 0.0
        pressAt = now
        pressDuration = if (pressed) HealthAgeDialMotion.Press.pressIn else HealthAgeDialMotion.Press.release
        if (pressed) {
            beginTouch(now)
        }
    }

    fun glideToDial(now: Double) {
        val current = frame(now)
        if (current.scroll <= 1.0) {
            return
        }
        mode = Mode.MANUAL
        scroll = current.scroll
        years = current.years
        forcedReturnFrom = current.scroll
        forcedReturnAt = now
        scrollReleaseAt = null
        lastTouch = now
    }

    fun frame(suppliedNow: Double): HealthAgeDialRenderFrame {
        val now = min(suppliedNow, pausedAt ?: suppliedNow)
        if (mode == Mode.AUTOMATIC && !reduced) {
            return automaticFrame(now)
        }

        var currentScroll = manualScroll(now)
        var currentYears = manualYears(now)
        val returnStart = forcedReturnAt ?: if (!reduced && autoPlay && !dragging) {
            lastTouch + HealthAgeDialMotion.resumeAfter
        } else {
            null
        }
        if (returnStart != null && now >= returnStart) {
            val origin = if (forcedReturnAt == null) manualScroll(returnStart) else forcedReturnFrom
            val progress = HealthAgeDialMotion.clamp01((now - returnStart) / HealthAgeDialMotion.returnDuration)
            currentScroll = origin * (1.0 - HealthAgeDialMotion.easeInOut(progress))
        }
        val restartAt = automaticRestartTime()
        if (restartAt != null && autoPlay && !reduced && now >= restartAt) {
            mode = Mode.AUTOMATIC
            loopStart = restartAt
            forcedReturnAt = null
            scrollReleaseAt = null
            return automaticFrame(now, restartAt)
        }

        val previousYears = manualYears(max(0.0, now - 1.0 / 240.0))
        val velocity = (currentYears - previousYears) * 240.0 * HealthAgeDialLayout.Dial.degreesPerYear
        val fadeFinished = fadeAt?.let { now - it >= HealthAgeDialMotion.Reveal.fade } == true
        val revealElapsed = if (fadeFinished) -1.0 else revealAt?.let { now - it } ?: -1.0
        val opacity = fadeAt?.let {
            1.0 - HealthAgeDialMotion.clamp01((now - it) / HealthAgeDialMotion.Reveal.fade)
        } ?: 1.0
        if (reduced && currentScroll > 0.0 && revealAt == null) {
            currentYears = REST_YEARS
        }
        return composedFrame(
            scroll = currentScroll,
            years = currentYears,
            trail = if (reduced) 0.0 else HealthAgeDialMotion.trailFor(velocity),
            revealElapsed = if (reduced && currentScroll > 0.0) 10.0 else revealElapsed,
            revealOpacity = opacity,
            bump = if (reduced) 0.0 else HealthAgeDialMotion.landBump(now - landAt),
            lift = liftValue(now),
            press = pressValue(now),
        )
    }

    fun tickEnd(now: Double): Double? {
        if (autoPlay && !reduced) {
            return null
        }
        if (dragging) {
            return null
        }
        var end = now
        scrollReleaseAt?.let { end = max(end, it + 3.0) }
        yearsReleaseAt?.let { end = max(end, it + if (reduced) 0.2 else 1.6) }
        revealAt?.let { end = max(end, it + 1.3) }
        fadeAt?.let { end = max(end, it + HealthAgeDialMotion.Reveal.fade) }
        if (landAt > -90.0) {
            end = max(end, landAt + 1.2)
        }
        forcedReturnAt?.let { end = max(end, it + HealthAgeDialMotion.returnDuration) }
        end = max(end, liftAt + liftDuration + 0.3)
        end = max(end, pressAt + pressDuration)
        return end
    }

    private fun automaticFrame(now: Double, start: Double? = null): HealthAgeDialRenderFrame {
        val origin = start ?: loopStart
        val time = normalized(now - origin)
        val frame = HealthAgeDialMotion.loopFrame(
            time = time,
            rest = REST_YEARS,
            degreesPerYear = HealthAgeDialLayout.Dial.degreesPerYear.toDouble(),
            historyOffset = HealthAgeDialLayout.historyOffset.toDouble(),
        )
        val landing = HealthAgeDialMotion.landingTime(
            offset = landingOffset,
            historyOffset = HealthAgeDialLayout.historyOffset.toDouble(),
        )
        return composedFrame(
            scroll = frame.scroll,
            years = frame.years,
            trail = HealthAgeDialMotion.trailFor(frame.velocity),
            revealElapsed = frame.reveal,
            revealOpacity = frame.revealOpacity,
            bump = HealthAgeDialMotion.landBump(time - landing),
            lift = 0.0,
            press = pressValue(now),
        )
    }

    private fun composedFrame(
        scroll: Double,
        years: Double,
        trail: Double,
        revealElapsed: Double,
        revealOpacity: Double,
        bump: Double,
        lift: Double,
        press: Double,
    ): HealthAgeDialRenderFrame {
        val elapsed = revealElapsed
        return HealthAgeDialRenderFrame(
            scroll = scroll,
            years = years,
            trail = trail,
            revealElapsed = elapsed,
            revealOpacity = revealOpacity,
            lineProgress = if (elapsed < 0.0) 0.0 else HealthAgeDialMotion.easeInOut(elapsed / HealthAgeDialMotion.Reveal.line),
            guides = if (elapsed < 0.0) 0.0 else HealthAgeDialMotion.easeOut((elapsed - HealthAgeDialMotion.Reveal.guidesAt) / HealthAgeDialMotion.Reveal.guides),
            count1 = if (elapsed < 0.0) 0.0 else HealthAgeDialMotion.easeOut((elapsed - HealthAgeDialMotion.Reveal.countAt[0]) / HealthAgeDialMotion.Reveal.count),
            count2 = if (elapsed < 0.0) 0.0 else HealthAgeDialMotion.easeOut((elapsed - HealthAgeDialMotion.Reveal.countAt[1]) / HealthAgeDialMotion.Reveal.count),
            badge1 = if (elapsed < 0.0) -1.0 else min(elapsed - HealthAgeDialMotion.Reveal.badgeAt[0], 2.0),
            badge2 = if (elapsed < 0.0) -1.0 else min(elapsed - HealthAgeDialMotion.Reveal.badgeAt[1], 2.0),
            bump = bump,
            lift = lift,
            chipPress = press,
        )
    }

    private fun manualScroll(now: Double): Double {
        val released = scrollReleaseAt ?: return scroll
        val seconds = max(0.0, now - released)
        val decay = 2.0
        val projected = scrollReleaseFrom + scrollReleaseVelocity / decay * (1.0 - exp(-decay * seconds))
        return projected.coerceIn(0.0, maxScroll)
    }

    private fun manualYears(now: Double): Double {
        val released = yearsReleaseAt ?: return years
        val seconds = max(0.0, now - released)
        if (reduced) {
            return HealthAgeDialMotion.lerp(
                yearsReleaseFrom,
                REST_YEARS,
                HealthAgeDialMotion.easeOut(seconds / 0.2),
            )
        }
        if (seconds > 1.6) {
            return REST_YEARS
        }
        return REST_YEARS + HealthAgeDialMotion.springDisplacement(
            tau = seconds,
            displacement = yearsReleaseFrom - REST_YEARS,
            velocity = yearsReleaseVelocity,
            spring = HealthAgeDialMotion.Spring.dot,
        )
    }

    private fun automaticRestartTime(): Double? {
        if (!autoPlay || reduced || dragging) {
            return null
        }
        val returnStart = forcedReturnAt ?: (lastTouch + HealthAgeDialMotion.resumeAfter)
        val extraDelay = if (forcedReturnAt == null) 0.0 else 2.8
        return returnStart + HealthAgeDialMotion.returnDuration + extraDelay
    }

    private fun momentumCrossingTime(from: Double, velocity: Double, target: Double): Double? {
        val decay = 2.0
        if (abs(velocity) <= 20.0) {
            return null
        }
        val projected = (from + velocity / decay).coerceIn(0.0, maxScroll)
        if (target < min(from, projected) || target > max(from, projected) || abs(target - from) <= 0.001) {
            return null
        }
        val remainder = 1.0 - (target - from) * decay / velocity
        if (remainder <= 0.0 || remainder > 1.0) {
            return null
        }
        return -ln(remainder) / decay
    }

    private fun updateReveal(now: Double, screenHeight: Double) {
        if (fadeAt?.let { now - it >= HealthAgeDialMotion.Reveal.fade } == true) {
            revealAt = null
            fadeAt = null
        }
        val top = -HealthAgeDialLayout.pinSafeTop + HealthAgeDialLayout.chart.top - scroll
        val visible = top < screenHeight - 150.0
        val gone = top > screenHeight - 90.0
        if (revealAt == null && visible) {
            revealAt = if (reduced) now - 10.0 else now
            fadeAt = null
        } else if (revealAt != null && gone) {
            if (fadeAt == null) {
                fadeAt = now
            }
        } else if (revealAt != null) {
            fadeAt = null
        }
    }

    private fun setLift(target: Double, duration: Double, damping: Double, now: Double) {
        liftFrom = liftValue(now)
        liftTarget = target
        liftAt = now
        liftDuration = duration
        liftDamping = damping
    }

    private fun liftValue(now: Double): Double {
        val elapsed = max(0.0, now - liftAt)
        val omega = 4.6 / max(0.001, liftDamping * liftDuration)
        val progress = if (liftDamping >= 1.0) {
            val value = omega * elapsed
            1.0 - exp(-value) * (1.0 + value)
        } else {
            HealthAgeDialMotion.spring(elapsed, HealthAgeDialSpring(omega, liftDamping))
        }
        return HealthAgeDialMotion.lerp(liftFrom, liftTarget, HealthAgeDialMotion.clamp01(progress))
    }

    private fun pressValue(now: Double): Double {
        val progress = HealthAgeDialMotion.easeOut((now - pressAt) / max(0.001, pressDuration))
        return HealthAgeDialMotion.lerp(pressFrom, pressTarget, progress)
    }

    private fun normalized(value: Double): Double {
        return ((value % HealthAgeDialMotion.loop) + HealthAgeDialMotion.loop) % HealthAgeDialMotion.loop
    }

    companion object {
        val landingOffset: Double = HealthAgeDialLayout.Dial.cy -
            (HealthAgeDialLayout.Chip.top + HealthAgeDialLayout.Chip.height / 2.0)

        fun tangentialYearsVelocity(velocityX: Double, velocityY: Double, years: Double): Double {
            val radians = years * HealthAgeDialLayout.Dial.degreesPerYear * Math.PI / 180.0
            val angular = (velocityX * cos(radians) + velocityY * sin(radians)) / HealthAgeDialLayout.Dial.radius
            return angular * 180.0 / Math.PI / HealthAgeDialLayout.Dial.degreesPerYear
        }
    }
}
