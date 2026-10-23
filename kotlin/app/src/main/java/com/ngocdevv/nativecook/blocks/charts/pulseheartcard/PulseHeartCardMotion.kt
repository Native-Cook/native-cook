package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Every timing value of the living heart, as pure functions (motion.ts).
 *
 * The heart is drawn by an AGSL shader. Each frame the engine computes one small set of numbers from
 * "time since the last lub" and hands them to the shader as uniforms.
 * Units: seconds, and "ref pt" (dp of the 402 wide pin screen).
 */
internal object PulseHeartCardMotion {
    /** Autoplay loop: six beats at 62 BPM, then the heart rests (about 20 % of the loop: 0-0.14 s and 6.52-8 s). */
    const val LOOP = 8.0
    const val REST_BPM = 62.0
    const val BEAT = 60.0 / REST_BPM

    /** First R-peak of the loop. The ECG pen enters the left edge half a beat before it. */
    const val FIRST_LUB = 0.62
    const val LUBS = 6

    /** A finger lifted for this long hands the heart back to the autoplay loop (from rest). */
    const val IDLE_RESET = 2.0

    /** The chart selection fades out over the last 200 ms of that wait. */
    const val CURSOR_FADE = 0.2

    /** "Never happened" for a time-since-beat. */
    const val NEVER = 99.0

    /** The final fixture is the resting 62 BPM reading. */
    val samples = intArrayOf(
        82, 100, 100, 110, 100, 88, 65, 90, 60, 62,
        60, 43, 43, 120, 100, 82, 84, 60, 84, 62,
    )

    class BarBounds(val x: Float, val width: Float, val segments: List<Pair<Float, Float>>) {
        val y0: Float get() = segments.first().first
        val y1: Float get() = segments.last().second
    }

    /** Measured on the pin (ref pt), in the same fixture order as [samples]. */
    private val segmentTable: List<List<Pair<Float, Float>>> = listOf(
        listOf(679f to 696.3f),
        listOf(670.3f to 687f),
        listOf(670.7f to 693f),
        listOf(675f to 679f, 681.7f to 693.3f),
        listOf(671f to 678.3f, 682f to 695f),
        listOf(678.3f to 696.7f),
        listOf(685f to 697.7f),
        listOf(677.3f to 697.3f),
        listOf(688.3f to 698.3f),
        listOf(686.3f to 702f),
        listOf(688f to 702.7f),
        listOf(696.7f to 702.3f),
        listOf(697.3f to 702.7f),
        listOf(660.7f to 675f, 680.7f to 697.3f),
        listOf(672.3f to 691f),
        listOf(682.3f to 694.3f),
        listOf(681f to 699f),
        listOf(688f to 699.3f),
        listOf(679.3f to 682.7f, 684.3f to 688.3f, 692.7f to 698.7f),
        listOf(675.7f to 702.3f),
    )

    val barBounds: List<BarBounds> = segmentTable.mapIndexed { index, segments ->
        BarBounds(x = 34f + index * 7.25f, width = 3.8f, segments = segments)
    }

    // The beat: a damped spring kicked at each lub (S1) and, weaker, at each dub (S2).

    /** Ringing period of the heart's flesh (s): ~3.3 Hz reads as a soft, heavy muscle. */
    private const val SPRING_PERIOD = 0.3

    /** Exponential decay time (s): one visible rebound, a faint second one. */
    private const val SPRING_DECAY = 0.1
    private const val OMEGA = 2 * PI / SPRING_PERIOD

    /** Normalises the first peak of the impulse response to 1 (peak at ~0.054 s). */
    private val peak: Double = run {
        val t = atan(OMEGA * SPRING_DECAY) / OMEGA
        return@run exp(-t / SPRING_DECAY) * sin(OMEGA * t)
    }

    /** Dub follows the lub by this long, or by this share of the period at fast rates. */
    private const val DUB_DELAY = 0.3
    private const val DUB_SHARE = 0.36
    private const val DUB_WEIGHT = 0.45

    /** Whole-body expansion at the lub's peak (+5.2 %). */
    private const val BODY = 0.052

    /** Pre-beat gather: the heart draws in by 1.4 % over 90 ms before the lub. */
    private const val ANTICIPATION = 0.014
    private const val ANTICIPATION_LEAD = 0.09

    /** The lobes are heavier than the body: they arrive 35 ms late and overshoot. */
    private const val LOBE = 0.024
    private const val LOBE_LAG = 0.035

    /** Width bulge after the peak (positive = wider and shorter), 70 ms late. */
    private const val SQUASH = 0.022
    private const val SQUASH_LAG = 0.07

    /** The heart hops up by this many ref pt at the peak. */
    private const val LIFT = -2.4

    /** Halo ring pushed outwards by this many ref pt, 20 ms after the lub. */
    private const val PUSH = 7.0
    private const val PUSH_LAG = 0.02

    /** Wet gloss: rises in 25 ms, fades over 320 ms. */
    private const val GLOSS_RISE = 0.025
    private const val GLOSS_FADE = 0.32

    /** The highlight slides this far (light-direction units) towards the centre and back. */
    private const val LIGHT = 0.28
    private const val LIGHT_RISE = 0.06
    private const val LIGHT_FADE = 0.4

    /** The reading flashes white on the R-peak and fades over 200 ms. */
    private const val FLASH_FADE = 0.2

    /** Normalised impulse response of the heart's flesh, 0 before the kick. */
    fun kick(tau: Double): Double {
        if (tau < 0 || tau > 1.2) return 0.0
        return exp(-tau / SPRING_DECAY) * sin(OMEGA * tau) / peak
    }

    private fun rise(tau: Double, riseTime: Double, fadeTime: Double): Double {
        if (tau < 0 || tau > 3) return 0.0
        return (1 - exp(-tau / riseTime)) * exp(-tau / fadeTime)
    }

    private fun gather(untilNext: Double): Double {
        if (untilNext <= 0 || untilNext >= ANTICIPATION_LEAD) return 0.0
        val s = sin(PI * (ANTICIPATION_LEAD - untilNext) / ANTICIPATION_LEAD)
        return s * s
    }

    class Beat(
        val body: Double = 0.0,
        val lobe: Double = 0.0,
        val squash: Double = 0.0,
        val lift: Double = 0.0,
        val gloss: Double = 0.0,
        val light: Double = 0.0,
        val push: Double = 0.0,
        val flash: Double = 0.0,
    )

    /**
     * Everything the heart does at one instant.
     * [tau0]/[tau1]: seconds since the last and the previous lub ([NEVER] if none);
     * [untilNext]: seconds until the next lub ([NEVER] if none is coming); [period]: seconds per beat.
     */
    fun beatAt(tau0: Double, tau1: Double, untilNext: Double, period: Double): Beat {
        val dub = min(DUB_DELAY, DUB_SHARE * period)
        fun both(lag: Double) = kick(tau0 - lag) + kick(tau1 - lag)
        val lub = both(0.0)
        val dubs = both(dub) * DUB_WEIGHT
        val gloss = min(
            1.0,
            rise(tau0, GLOSS_RISE, GLOSS_FADE) + rise(tau1, GLOSS_RISE, GLOSS_FADE) +
                DUB_WEIGHT * (rise(tau0 - dub, GLOSS_RISE, GLOSS_FADE) + rise(tau1 - dub, GLOSS_RISE, GLOSS_FADE)),
        )
        return Beat(
            body = BODY * (lub + dubs) - ANTICIPATION * gather(untilNext),
            lobe = LOBE * (both(LOBE_LAG) + DUB_WEIGHT * both(dub + LOBE_LAG)),
            squash = SQUASH * (both(SQUASH_LAG) + DUB_WEIGHT * both(dub + SQUASH_LAG)),
            lift = LIFT * (lub + dubs),
            gloss = gloss,
            light = LIGHT * min(1.0, rise(tau0, LIGHT_RISE, LIGHT_FADE) + rise(tau1, LIGHT_RISE, LIGHT_FADE)),
            push = PUSH * maxOf(0.0, both(PUSH_LAG) + DUB_WEIGHT * both(dub + PUSH_LAG)),
            flash = if (tau0 >= 0 && tau0 < 1.5) exp(-tau0 / FLASH_FADE) else 0.0,
        )
    }

    // Autoplay: a pure function of loop time.

    class Pulse(
        /** Continuous beat count: an integer at every R-peak. Drives the ECG pen. */
        val phi: Double,
        /** Trace is drawn for beat counts in [traceStart, traceEnd]; it fades after traceEnd. */
        val traceStart: Double,
        val traceEnd: Double,
        val tau0: Double,
        val tau1: Double,
        val untilNext: Double,
        val period: Double,
    )

    /** The ECG pen enters at the left edge (half a beat before the R-peak at the centre). */
    const val TRACE_LEAD = 0.5

    /** Old trace fades with age (per beat) like phosphor; the trace fades out over this many beats after the last peak. */
    const val TRACE_FADE_OUT = 0.6

    fun autoplayAt(time: Double): Pulse {
        val t = ((time % LOOP) + LOOP) % LOOP
        val phi = (t - FIRST_LUB) / BEAT
        val last = min(floor(phi).toInt(), LUBS - 1)
        val tau0 = if (last >= 0) t - (FIRST_LUB + last * BEAT) else NEVER
        val tau1 = if (last >= 1) tau0 + BEAT else NEVER
        val next = last + 1
        val untilNext = if (next <= LUBS - 1) FIRST_LUB + next * BEAT - t else NEVER
        return Pulse(
            phi = phi,
            traceStart = -TRACE_LEAD,
            traceEnd = LUBS - 1 + TRACE_LEAD,
            tau0 = tau0,
            tau1 = tau1,
            untilNext = untilNext,
            period = BEAT,
        )
    }

    // Below these the beat is invisible (body/lobe/squash as a share of size, lift/push in ref pt, the
    // rest 0-1), so the autoplay frame is the pin's and the shader need not redraw: the loop's rest stretch.
    private const val IDLE_SHAPE = 0.0002
    private const val IDLE_LIFT = 0.02
    private const val IDLE_PUSH = 0.03
    private const val IDLE_GLOSS = 0.02
    private const val IDLE_LIGHT = 0.01
    private const val IDLE_FLASH = 0.01
    private const val IDLE_RIPPLE = 0.75

    /** True when nothing on the heart moves: the beat has died away, its ripples are gone and the ECG trace has faded. */
    fun autoplayIdle(beat: Beat, tau0: Double, phi: Double, traceStart: Double, traceEnd: Double): Boolean {
        if (phi >= traceStart && phi <= traceEnd + TRACE_FADE_OUT) return false
        if (tau0 < IDLE_RIPPLE) return false
        return abs(beat.body) < IDLE_SHAPE && abs(beat.lobe) < IDLE_SHAPE && abs(beat.squash) < IDLE_SHAPE &&
            abs(beat.lift) < IDLE_LIFT && abs(beat.push) < IDLE_PUSH &&
            beat.gloss < IDLE_GLOSS && beat.light < IDLE_LIGHT && beat.flash < IDLE_FLASH
    }

    /**
     * Loop time at which the idle stretch ends: the ECG pen enters at phi = -TRACE_LEAD (a few ms inside,
     * so a timer that fires on time finds the trace window open).
     */
    const val WAKE_LOOP_TIME = FIRST_LUB - TRACE_LEAD * BEAT + 0.004

    // Chart scrub helpers.

    fun clampIndex(index: Double): Int = Math.round(index).toInt().coerceIn(0, samples.size - 1)

    fun sampleTime(index: Int): String {
        val hour = if (index / 2 == 0) 12 else index / 2
        return "%d:%02d AM".format(hour, (index % 2) * 30)
    }

    /** Press feedback on the Heart chip. */
    const val PRESS_SCALE = 0.97f
    const val PRESS_IN_MS = 120
    const val PRESS_OUT_MS = 100
}
