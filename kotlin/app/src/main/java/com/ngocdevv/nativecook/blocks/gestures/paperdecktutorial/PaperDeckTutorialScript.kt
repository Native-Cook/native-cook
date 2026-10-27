package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import kotlin.math.max
import kotlin.math.min

/** `LOOP`: the length of one `autoPlay` loop, in seconds: the reference's 17.5 s story plus a 1 s return. */
const val PAPER_DECK_TUTORIAL_LOOP = 18.5

/** One frame of the deck (`DeckPose` in the React Native file). [x] is the top card's offset in dp. */
internal class PaperDeckTutorialPose(
    val step: Int,
    val x: Double,
    val flip: Double,
    val time: Double,
)

/** One card's copy (`STEPS`): a two-line title and an emoji. */
internal class PaperDeckTutorialStep(val title: String, val symbol: String)

/**
 * The numbers of the loop, copied from `PaperDeckTutorial.tsx`: the measured tracks, the throws, the
 * card copy and the curve, together with the monotone-cubic `track` that reads them.
 */
internal object PaperDeckTutorialScript {
    /** Where the loop's return begins: from 17.9 s the screen is the resting cover again. */
    private const val RETURN_FROM = 17.9

    /** `clamp`. */
    fun clamp(value: Double, lower: Double = 0.0, upper: Double = 1.0): Double = max(lower, min(upper, value))

    private fun key(time: Double, value: Double) = doubleArrayOf(time, value)

    private fun frame(number: Int) = number / 60.0

    // Source f860-868: the last card's fitted visible edges. Values are normalised to the source viewport
    // (x as a fraction of the screen's width, y of its height), except the roll, in degrees.
    val LAST_X: Array<DoubleArray> = arrayOf(
        key(frame(860), 0.0), key(frame(861), 0.085104), key(frame(862), 0.112787), key(frame(863), 0.146173),
        key(frame(864), 0.209716), key(frame(865), 0.305564), key(frame(866), 0.434484), key(frame(867), 0.595059),
        key(frame(868), 0.783343), key(frame(869), 1.0), key(frame(870), 1.25),
    )
    val LAST_Y: Array<DoubleArray> = arrayOf(
        key(frame(860), 0.0), key(frame(861), -0.026521), key(frame(862), -0.03552), key(frame(863), -0.048075),
        key(frame(864), -0.072457), key(frame(865), -0.108951), key(frame(866), -0.158152), key(frame(867), -0.21929),
        key(frame(868), -0.291471), key(frame(869), -0.375), key(frame(870), -0.47),
    )
    val LAST_ROLL: Array<DoubleArray> = arrayOf(
        key(frame(860), 0.0), key(frame(861), 1.73147), key(frame(862), 2.26703), key(frame(863), 3.00973),
        key(frame(864), 4.32635), key(frame(865), 5.97817), key(frame(866), 9.07868), key(frame(867), 12.31907),
        key(frame(868), 16.26098), key(frame(869), 20.3), key(frame(870), 24.5),
    )

    /** `THROWS`: for each of the six cards, time against the x offset as a fraction of the screen's width. */
    val THROWS: Array<Array<DoubleArray>> = arrayOf(
        arrayOf(
            key(3.4, 0.0), key(3.5, 0.02), key(3.65, 0.04), key(3.8, 0.07),
            key(3.85, 0.18), key(3.9, 0.42), key(3.95, 0.85), key(4.0, 1.4),
        ),
        arrayOf(
            key(5.15, 0.0), key(5.35, 0.03), key(5.45, 0.055), key(5.5, 0.09),
            key(5.6, 0.3), key(5.6833, 0.8), key(5.75, 1.4),
        ),
        arrayOf(key(9.1167, 0.0), key(9.15, 0.1), key(9.2, 0.3), key(9.25, 0.75), key(9.3333, 1.4)),
        arrayOf(key(11.2667, 0.0), key(11.3, 0.1), key(11.35, 0.55), key(11.4333, 1.4)),
        arrayOf(key(12.55, 0.0), key(12.5833, 0.1), key(12.6333, 0.55), key(12.75, 1.4)),
        LAST_X,
    )

    /** `FLIP`: the cover's turn about its vertical axis, in degrees, 0.6 to 1.1167 s. */
    val FLIP: Array<DoubleArray> = arrayOf(
        key(0.6, 0.0), key(0.65, 7.0), key(0.7, 24.0), key(0.75, 45.0), key(0.8, 69.0), key(0.8333, 87.4),
        key(0.85, 90.0), key(0.8833, 99.0), key(0.9167, 117.0), key(0.95, 133.0), key(1.0, 154.0),
        key(1.0667, 171.0), key(1.1167, 180.0),
    )

    /** `LIFT`: the cover rising from below, 0.2 to 0.7 s. */
    val LIFT: Array<DoubleArray> = arrayOf(
        key(0.0, 0.0), key(0.2, 0.0), key(0.3, 0.05), key(0.4, 0.27), key(0.5, 0.55), key(0.6, 0.81), key(0.7, 1.0),
    )

    /** The rear sheets settling in, 1.1 to 1.6 s (inline in `PaperCard`). */
    val INTRO_REAR: Array<DoubleArray> = arrayOf(key(1.1, 0.0), key(1.22, 0.6), key(1.34, 1.04), key(1.6, 1.0))

    /** The cards behind the cover fading out, 0.2 to 0.6 s (inline in `IllustratedDeck`). */
    val BACKS_FADE: Array<DoubleArray> = arrayOf(key(0.2, 0.0), key(0.6, 1.0))

    /** `ANCHORS`: where the loop resumes for each step after a touch. */
    val ANCHORS: DoubleArray = doubleArrayOf(1.8, 4.3, 6.0, 9.65, 11.65, 12.95, 15.5)

    /** `DIRECTIONS`: the way each card flies off. */
    val DIRECTIONS: DoubleArray = doubleArrayOf(1.0, -1.0, 1.0, 1.0, 1.0, 1.0)

    /** `STEPS`. The emoji are written as escapes: the pointing hands, the playing card, the eyes, the map and the smile. */
    val STEPS: List<PaperDeckTutorialStep> = listOf(
        PaperDeckTutorialStep("Swipe the card\nto the right", "\uD83D\uDC49"),
        PaperDeckTutorialStep("Swipe the card\nto the left", "\uD83D\uDC48"),
        PaperDeckTutorialStep("Little questions.\nMany ways to connect.", "\uD83C\uDCCF"),
        PaperDeckTutorialStep("Pick a theme\nfor the moment.", "\uD83D\uDC40"),
        PaperDeckTutorialStep("One card can start\na great conversation.", "\uD83D\uDDFA\uFE0F"),
        PaperDeckTutorialStep("You are ready.\nLet curiosity lead.", "\u263A\uFE0F"),
    )

    /** `EASE`: `Easing.bezier(0.23, 1, 0.32, 1)`. */
    private val EASE = PaperDeckTutorialBezier(0.23, 1.0, 0.32, 1.0)

    fun ease(progress: Double): Double = EASE.at(progress)

    /** `previewPhase`: [value] folded into 0 until the loop's length. */
    fun previewPhase(value: Double): Double {
        if (!value.isFinite()) return 0.0
        val phase = value % PAPER_DECK_TUTORIAL_LOOP
        return if (phase < 0) phase + PAPER_DECK_TUTORIAL_LOOP else phase
    }

    /**
     * `track`: a monotone cubic Hermite curve through [keys] (time, value), flat before the first
     * key and after the last, with the harmonic-mean tangents of the React Native file.
     */
    fun track(t: Double, keys: Array<DoubleArray>): Double {
        if (t <= keys[0][0]) return keys[0][1]
        val last = keys.size - 1
        if (t >= keys[last][0]) return keys[last][1]
        var i = 0
        while (keys[i + 1][0] < t) i++
        val a = keys[i][0]
        val y0 = keys[i][1]
        val b = keys[i + 1][0]
        val y1 = keys[i + 1][1]
        val secant = (y1 - y0) / (b - a)
        val before = if (i > 0) (y0 - keys[i - 1][1]) / (a - keys[i - 1][0]) else 0.0
        val after = if (i + 1 < last) (keys[i + 2][1] - y1) / (keys[i + 2][0] - b) else 0.0
        val m0 = if (before * secant > 0) 2 * before * secant / (before + secant) else 0.0
        val m1 = if (after * secant > 0) 2 * after * secant / (after + secant) else 0.0
        val p = (t - a) / (b - a)
        val p2 = p * p
        val p3 = p2 * p
        return (2 * p3 - 3 * p2 + 1) * y0 + (p3 - 2 * p2 + p) * (b - a) * m0 + (-2 * p3 + 3 * p2) * y1 + (p3 - p2) * (b - a) * m1
    }

    /** The deck at loop time [t] while the loop plays by itself (the `pose` derived value with `manual` off). */
    fun autoPose(t: Double, screenWidth: Double): PaperDeckTutorialPose {
        if (t >= RETURN_FROM) return PaperDeckTutorialPose(0, 0.0, 0.0, 0.0)
        var active = 0
        while (active < 6 && t >= THROWS[active].last()[0]) active++
        val x = if (active < 6) DIRECTIONS[active] * screenWidth * track(t, THROWS[active]) else 0.0
        return PaperDeckTutorialPose(active, x, track(t, FLIP) / 180.0, t)
    }
}
