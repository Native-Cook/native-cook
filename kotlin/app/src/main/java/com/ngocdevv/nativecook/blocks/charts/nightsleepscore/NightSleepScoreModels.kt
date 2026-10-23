package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import java.util.Locale
import kotlin.math.floor

 enum class NightSleepScoreStage(val title: String) { AWAKE("Awake"), REM("REM"), CORE("Core"), DEEP("Deep") }

data class NightSleepScoreSegment(val stage: NightSleepScoreStage, val minutes: Int)
/** One sampled frame. `night` darkens the card into a sky (0 = the pin's light card), `reveal` is how much of the
 * aurora hypnogram and the night ring is drawn, `meteor` is a shooting star's progress (-1 when none). */
data class NightSleepScoreFrame(
    val cursorProgress: Double, val selectionPresence: Double, val segmentIndex: Int?, val isRest: Boolean,
    val night: Double = 0.0, val reveal: Double = 0.0, val meteor: Double = -1.0,
) {
    companion object { val REST = NightSleepScoreFrame(0.0, 0.0, null, true) }
}

enum class NightSleepScoreCadence(val interval: Double) { STOPPED(1.0 / 60.0), ANIMATION(1.0 / 60.0) }

object NightSleepScoreData {
    const val windowStartMinute = 23 * 60 + 18
    const val windowMinutes = 446
    const val score = 84
    const val readiness = 7
    val stages = NightSleepScoreStage.entries
    val segments = listOf(
        NightSleepScoreSegment(NightSleepScoreStage.AWAKE, 4), NightSleepScoreSegment(NightSleepScoreStage.CORE, 12),
        NightSleepScoreSegment(NightSleepScoreStage.DEEP, 7), NightSleepScoreSegment(NightSleepScoreStage.CORE, 18),
        NightSleepScoreSegment(NightSleepScoreStage.DEEP, 12), NightSleepScoreSegment(NightSleepScoreStage.CORE, 48),
        NightSleepScoreSegment(NightSleepScoreStage.REM, 7), NightSleepScoreSegment(NightSleepScoreStage.CORE, 7),
        NightSleepScoreSegment(NightSleepScoreStage.REM, 5), NightSleepScoreSegment(NightSleepScoreStage.CORE, 24),
        NightSleepScoreSegment(NightSleepScoreStage.AWAKE, 2), NightSleepScoreSegment(NightSleepScoreStage.CORE, 78),
        NightSleepScoreSegment(NightSleepScoreStage.DEEP, 9), NightSleepScoreSegment(NightSleepScoreStage.CORE, 7),
        NightSleepScoreSegment(NightSleepScoreStage.DEEP, 8), NightSleepScoreSegment(NightSleepScoreStage.CORE, 10),
        NightSleepScoreSegment(NightSleepScoreStage.REM, 19), NightSleepScoreSegment(NightSleepScoreStage.CORE, 64),
        NightSleepScoreSegment(NightSleepScoreStage.AWAKE, 2), NightSleepScoreSegment(NightSleepScoreStage.CORE, 12),
        NightSleepScoreSegment(NightSleepScoreStage.REM, 32), NightSleepScoreSegment(NightSleepScoreStage.CORE, 14),
        NightSleepScoreSegment(NightSleepScoreStage.AWAKE, 3), NightSleepScoreSegment(NightSleepScoreStage.CORE, 18),
        NightSleepScoreSegment(NightSleepScoreStage.AWAKE, 2), NightSleepScoreSegment(NightSleepScoreStage.CORE, 21),
        NightSleepScoreSegment(NightSleepScoreStage.AWAKE, 1),
    )
    fun total(stage: NightSleepScoreStage): Int = segments.sumOf { if (it.stage == stage) it.minutes else 0 }
    val asleepMinutes: Int get() = total(NightSleepScoreStage.REM) + total(NightSleepScoreStage.CORE) + total(NightSleepScoreStage.DEEP)
    fun segmentIndex(minute: Double): Int {
        val bounded = minute.coerceIn(0.0, windowMinutes - .000001)
        var edge = 0.0
        segments.forEachIndexed { i, segment -> edge += segment.minutes; if (bounded < edge) return i }
        return segments.lastIndex
    }
    fun segmentStart(index: Int): Int = segments.take(index.coerceIn(0, segments.size)).sumOf { it.minutes }
    fun clockTime(minute: Double): String {
        val offset = floor(minute.coerceIn(0.0, windowMinutes.toDouble())).toInt()
        val clock = (windowStartMinute + offset) % (24 * 60)
        return String.format(Locale.US, "%02d:%02d", clock / 60, clock % 60)
    }
    fun range(index: Int): String {
        val start = segmentStart(index).toDouble()
        return "${clockTime(start)}–${clockTime(start + segments[index].minutes)} overnight window"
    }
}

object NightSleepScoreMotion {
    const val loop = 9.0

    /** Autoplay timeline in seconds of the 9 s loop. Rest 0-1.0 and 8.05-9.0 is the pin's light card. */
    object Timeline {
        const val duskStart = 1.0
        const val duskEnd = 2.05
        const val presenceStart = 1.55
        const val presenceEnd = 1.9
        const val sweepStart = 1.75
        const val sweepEnd = 6.35
        const val sweepRamp = .16
        const val meteorStart = 3.3
        const val meteorEnd = 4.0
        const val dayStart = 6.25
        const val dayEnd = 8.05
        const val presenceOutStart = 6.45
        const val presenceOutEnd = 6.9
    }

    const val frontWidth = .6
    const val readoutSwap = .45

    object Chase {
        const val night = 9.0
        const val reveal = 6.5
        const val phase = 11.0
        const val lockDistance = .002
        const val lockVelocity = .05
    }

    /** Tab bar lens: a spring slide that stretches 12 % on the way; press 0.97 in 120 ms, back in 100 ms. */
    object TabMotion {
        const val lensDuration = .4
        const val lensDamping = .8f
        const val stretch = 1.12f
        const val stretchMs = 120
        const val settleDuration = .3
        const val settleDamping = .7f
        const val pressScale = .97f
        const val pressInMs = 120
        const val pressOutMs = 100
        /** Spring stiffness whose period equals the duration (Reanimated's duration-based spring). */
        fun stiffness(duration: Double): Float {
            val omega = 2 * Math.PI / duration
            return (omega * omega).toFloat()
        }
    }

    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun easeOutCubic(v: Double): Double { val x = clamp(v); return 1 - (1 - x) * (1 - x) * (1 - x) }
    fun easeInOut(v: Double): Double { val x = clamp(v); return if (x < .5) 4 * x * x * x else 1 - Math.pow(-2 * x + 2, 3.0) / 2 }
    fun smoothStep(v: Double): Double { val x = clamp(v); return x * x * (3 - 2 * x) }
    fun smootherStep(v: Double): Double { val x = clamp(v); return x * x * x * (x * (x * 6 - 15) + 10) }
    private fun rampIntegral(x: Double) = x * x * x - x * x * x * x / 2

    /** Playhead position for a sweep fraction: velocity eases in and out over `ramp`, constant between. */
    fun sweep(v: Double): Double {
        val ramp = Timeline.sweepRamp
        val u = clamp(v)
        val peak = 1 / (1 - ramp)
        if (u < ramp) return peak * ramp * rampIntegral(u / ramp)
        if (u > 1 - ramp) return 1 - peak * ramp * rampIntegral((1 - u) / ramp)
        return peak * (u - ramp / 2)
    }

    /** Night falls from the top of the card and day returns from its bottom edge: local darkness at card height `v`. */
    fun nightAt(night: Double, v: Double): Double = smootherStep((night * (1 + frontWidth) - v) / frontWidth)

    /** Critically damped spring step (exact for any dt). Returns value to velocity. */
    fun chase(value: Double, velocity: Double, target: Double, dt: Double, omega: Double): Pair<Double, Double> {
        val delta = value - target
        val decay = Math.exp(-omega * dt)
        val k = velocity + omega * delta
        return Pair(target + (delta + k * dt) * decay, (velocity - omega * k * dt) * decay)
    }

    fun selectedFrame(minute: Double, secondsSinceTouch: Double?, quiet: Boolean, holdDuration: Double?): NightSleepScoreFrame {
        val presence = if (quiet) 1.0 else if (holdDuration != null && secondsSinceTouch != null && secondsSinceTouch > holdDuration) 1 - easeInOut((secondsSinceTouch - holdDuration) / .25) else 1.0
        return NightSleepScoreFrame(clamp(minute / NightSleepScoreData.windowMinutes), presence, NightSleepScoreData.segmentIndex(minute), presence == 0.0, presence, 1.0, -1.0)
    }

    fun frame(elapsed: Double): NightSleepScoreFrame {
        val r = elapsed % loop
        val t = if (r < 0) r + loop else r
        val tl = Timeline
        if (t < tl.duskStart || t >= tl.dayEnd) return NightSleepScoreFrame.REST
        val night = if (t < tl.dayStart) smoothStep((t - tl.duskStart) / (tl.duskEnd - tl.duskStart)) else 1 - smoothStep((t - tl.dayStart) / (tl.dayEnd - tl.dayStart))
        val presence = if (t < tl.presenceOutStart) smootherStep((t - tl.presenceStart) / (tl.presenceEnd - tl.presenceStart)) else 1 - smootherStep((t - tl.presenceOutStart) / (tl.presenceOutEnd - tl.presenceOutStart))
        val progress = sweep((t - tl.sweepStart) / (tl.sweepEnd - tl.sweepStart))
        val meteor = if (t >= tl.meteorStart && t < tl.meteorEnd) (t - tl.meteorStart) / (tl.meteorEnd - tl.meteorStart) else -1.0
        val index = if (presence > 0) NightSleepScoreData.segmentIndex(progress * NightSleepScoreData.windowMinutes) else null
        return NightSleepScoreFrame(progress, presence, index, false, night, progress, meteor)
    }
}
