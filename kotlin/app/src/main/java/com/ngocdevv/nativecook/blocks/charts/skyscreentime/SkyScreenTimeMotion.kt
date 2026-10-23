package com.ngocdevv.nativecook.blocks.charts.skyscreentime

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SkyScreenTimeHour(
    val productive: Double,
    val neutral: Double,
    val distracted: Double,
    val pickups: Int,
    val notifications: Int,
) {
    val minutes: Double get() = productive + neutral + distracted
    val quality: Double get() = if (minutes > 0.0) productive / minutes else 0.9
}

object SkyScreenTimeData {
    private val totals = doubleArrayOf(3.0, 0.0, 13.0, 25.0, 0.0, 0.0, 0.0, 17.0, 16.0, 3.0, 0.0, 1.5, 6.5, 0.5, 2.8, 0.0, 4.1, 5.0, 1.5, 19.0, 0.5, 19.6, 0.0, 0.0)
    private val pickups = intArrayOf(1, 0, 4, 7, 0, 0, 0, 5, 5, 1, 0, 1, 2, 0, 1, 0, 1, 2, 0, 5, 0, 5, 0, 0)
    private val notifications = intArrayOf(1, 0, 2, 3, 0, 0, 0, 3, 2, 0, 0, 1, 1, 0, 2, 0, 3, 1, 0, 2, 0, 3, 0, 0)

    fun hours(day: Int): List<SkyScreenTimeHour> = totals.indices.map { index ->
        val total = totals[index]
        val neutral = if (index == 2) 3.5 else if (index == 7) 3.4 else 0.0
        val distracted = if (index == 14 || index == 16) total else 0.0
        if (day == 1) {
            val scaled = total * 1.2
            SkyScreenTimeHour(scaled * 0.62, scaled * 0.13, scaled * 0.25, pickups[index] + if (total > 5.0) 1 else 0, notifications[index])
        } else SkyScreenTimeHour(total - neutral - distracted, neutral, distracted, pickups[index], notifications[index])
    }

    fun total(day: Int): SkyScreenTimeHour = hours(day).fold(SkyScreenTimeHour(0.0, 0.0, 0.0, 0, 0)) { acc, item ->
        SkyScreenTimeHour(acc.productive + item.productive, acc.neutral + item.neutral, acc.distracted + item.distracted, acc.pickups + item.pickups, acc.notifications + item.notifications)
    }

    fun quality(hour: Double, day: Int): Double {
        val values = hours(day)
        val position = hour.coerceIn(0.0, 23.0)
        val left = position.toInt()
        val right = min(23, left + 1)
        return values[left].quality + (values[right].quality - values[left].quality) * (position - left)
    }

    fun timeLabel(index: Int): String {
        val hour = if (index % 12 == 0) 12 else index % 12
        return "$hour ${if (index < 12) "AM" else "PM"}"
    }

    fun duration(minutes: Double): String {
        val value = (if (minutes >= 0.0) floor(minutes + 0.5) else kotlin.math.ceil(minutes - 0.5)).toInt()
        return if (value >= 60) "${value / 60}h${(value % 60).toString().padStart(2, '0')}" else "${value}m"
    }
}

private fun skyScreenTimeFade(value: Double): Double {
    val t = value.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

data class SkyScreenTimeFrame(
    val hour: Double = 3.0,
    val selection: Double = 0.0,
    val quality: Double = 0.9,
    val storm: Double = 0.0,
    val focusSeconds: Int = 0,
) {
    val selectedIndex: Int? get() = if (selection >= 0.5) hour.roundToInt().coerceIn(0, 23) else null
    val title: String get() = if (quality >= 0.75) "Productive" else if (quality >= 0.45) "Balanced" else "Distracted"
    val titleOpacities: List<Double>
        get() {
            val productive = skyScreenTimeFade((quality - 0.71) / 0.08)
            val distracted = 1.0 - skyScreenTimeFade((quality - 0.41) / 0.08)
            return listOf(productive, 1.0 - productive - distracted, distracted)
        }
}

class SkyScreenTimeMotion {
    enum class Cadence(val interval: Double) { STOPPED(1.0 / 60.0), SECONDS(1.0), ANIMATION(1.0 / 60.0) }

    var day: Int = 0
        private set
    var autoPlay: Boolean = false
        private set
    var quiet: Boolean = false
        private set
    var active: Boolean = true
        private set
    private var appeared = false
    private var origin = 0.0
    private var pausedAt: Double? = null
    private var selectedHour: Double? = null
    private var fingerDown = false
    private var interruptedAt: Double? = null
    private var returnStarted: Double? = null
    private var returnFrame = SkyScreenTimeFrame()
    private var resetRequested = false
    private var focusStarted: Double? = null

    private fun logicalTime(at: Double): Double = pausedAt ?: at

    fun appear(autoPlay: Boolean, quiet: Boolean, at: Double) {
        if (appeared) return
        appeared = true
        this.autoPlay = autoPlay
        this.quiet = quiet
        origin = logicalTime(at)
    }

    fun setAutoPlay(enabled: Boolean, at: Double) {
        autoPlay = enabled
        val now = logicalTime(at)
        origin = now
        if (!enabled && !resetRequested) returnStarted = null
        if (selectedHour != null) interruptedAt = now
    }

    fun setQuiet(enabled: Boolean, at: Double) {
        if (quiet == enabled) return
        if (enabled && resetRequested) clearSelection(at)
        quiet = enabled
        returnStarted = null
        resetRequested = false
        origin = logicalTime(at)
        if (selectedHour != null) interruptedAt = logicalTime(at)
    }

    fun setActive(enabled: Boolean, at: Double) {
        if (active == enabled) return
        if (enabled) {
            val pause = max(0.0, at - (pausedAt ?: at))
            origin += pause
            interruptedAt = interruptedAt?.plus(pause)
            returnStarted = returnStarted?.plus(pause)
            pausedAt = null
        } else {
            val wasScrubbing = fingerDown
            pausedAt = at
            fingerDown = false
            if (wasScrubbing && selectedHour != null) interruptedAt = at
        }
        active = enabled
    }

    fun beginScrub(at: Double) {
        val current = frame(at)
        selectedHour = current.hour
        fingerDown = true
        interruptedAt = null
        returnStarted = null
        resetRequested = false
    }

    fun scrub(hour: Double) { selectedHour = hour.coerceIn(0.0, 23.0) }

    fun endScrub(at: Double) {
        fingerDown = false
        interruptedAt = logicalTime(at)
    }

    fun chooseDay(index: Int, at: Double) {
        day = index.coerceIn(0, 1)
        clearSelection(at)
    }

    fun reset(at: Double) {
        val current = frame(at)
        if (quiet || current.selection <= 0.001) {
            clearSelection(at)
            return
        }
        selectedHour = current.hour
        fingerDown = false
        returnFrame = current
        returnStarted = logicalTime(at)
        resetRequested = true
    }

    private fun clearSelection(at: Double) {
        selectedHour = null
        interruptedAt = null
        returnStarted = null
        fingerDown = false
        resetRequested = false
        origin = logicalTime(at)
    }

    fun toggleFocus(at: Double) { focusStarted = if (remainingFocus(at) > 0) null else at }

    fun remainingFocus(at: Double): Int {
        val start = focusStarted ?: return 0
        return max(0, FOCUS_DURATION - max(0.0, at - start).toInt())
    }

    fun frame(at: Double): SkyScreenTimeFrame {
        val now = logicalTime(at)
        var result: SkyScreenTimeFrame
        val hour = selectedHour
        if (hour != null) {
            val ratio = SkyScreenTimeData.quality(hour, day)
            result = SkyScreenTimeFrame(hour, 1.0, ratio, storm(ratio))
            val idleDeadline = interruptedAt?.plus(2.0)
            if (resetRequested || (autoPlay && !quiet && !fingerDown && now >= (idleDeadline ?: Double.POSITIVE_INFINITY))) {
                if (returnStarted == null) {
                    returnStarted = idleDeadline ?: now
                    returnFrame = result
                }
                val t = smooth((now - (returnStarted ?: now)) / 0.5)
                val daily = SkyScreenTimeData.total(day).quality
                val selection = returnFrame.selection * (1.0 - t)
                val quality = returnFrame.quality + (daily - returnFrame.quality) * t
                result = SkyScreenTimeFrame(hour, selection, quality, storm(quality))
                if (t >= 1.0) {
                    clearSelection(at)
                    result = demoFrame(0.0, day)
                }
            }
        } else if (autoPlay && !quiet) {
            result = demoFrame(max(0.0, now - origin), day)
        } else {
            val ratio = SkyScreenTimeData.total(day).quality
            result = SkyScreenTimeFrame(quality = ratio, storm = storm(ratio))
        }
        if (quiet) result = result.copy(storm = 0.0)
        return result.copy(focusSeconds = remainingFocus(at))
    }

    fun cadence(at: Double): Cadence {
        if (!active) return Cadence.STOPPED
        if (resetRequested && !quiet) return Cadence.ANIMATION
        if (autoPlay && !quiet && !fingerDown) return Cadence.ANIMATION
        return if (remainingFocus(at) > 0) Cadence.SECONDS else Cadence.STOPPED
    }

    companion object {
        const val LOOP = 8.0
        const val FOCUS_DURATION = 25 * 60

        fun demoFrame(at: Double, day: Int = 0): SkyScreenTimeFrame {
            val t = max(0.0, at) % LOOP
            val daily = SkyScreenTimeData.total(day).quality
            var hour = 3.0
            var alpha = 0.0
            if (t >= 1.3 && t < 1.7) alpha = smooth((t - 1.3) / 0.4)
            else if (t >= 1.7 && t < 3.3) { alpha = 1.0; hour = 3.0 + 11.0 * smooth((t - 1.7) / 1.6) }
            else if (t >= 3.3 && t < 4.4) { alpha = 1.0; hour = 14.0 }
            else if (t >= 4.4 && t < 5.9) { alpha = 1.0; hour = 14.0 + 7.0 * smooth((t - 4.4) / 1.5) }
            else if (t >= 5.9 && t < 6.6) { hour = 21.0; alpha = 1.0 - smooth((t - 5.9) / 0.7) }
            val ratio = daily + (SkyScreenTimeData.quality(hour, day) - daily) * alpha
            return SkyScreenTimeFrame(hour, alpha, ratio, storm(ratio))
        }

        private fun storm(quality: Double): Double = ((0.9 - quality) / 0.9).coerceIn(0.0, 1.0)
        private fun smooth(value: Double): Double {
            val t = value.coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }
    }
}
