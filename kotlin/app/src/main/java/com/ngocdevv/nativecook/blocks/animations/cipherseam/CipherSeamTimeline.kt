package com.ngocdevv.nativecook.blocks.animations.cipherseam

import kotlin.math.floor
import kotlin.math.sin

/**
 * The numbers and the maths that turn loop time into a frame, ported value for value from
 * `CipherSeam.tsx` (the names in the doc comments are that file's).
 */
internal object CipherSeamTimeline {
    /** `CARD_WIDTH`, `CARD_HEIGHT`: the card on the 402 pt design width, in dp. */
    const val CARD_WIDTH = 210f
    const val CARD_HEIGHT = 135f

    /** The design width the card is scaled from (`width / 402`). */
    const val DESIGN_WIDTH = 402f

    /** The width of the phone the left edge knots were measured in (`sourceX / 362 * width`). */
    const val SOURCE_VIEWPORT = 362f

    /** The drag moves loop time between 2.75 s and 10.5 s, 7.75 s over one hero width. */
    const val DRAG_FROM = 2.75
    const val DRAG_TO = 10.5
    const val DRAG_SPAN = 7.75

    // The measured left edge of the card (and of the token plane that follows it), in the source's
    // 362 pt viewport, as `[time, x]` knots of `interpolate(t, [...], [...], 'clamp')`.
    private val planeTimes = doubleArrayOf(0.0, 2.75, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 9.0, 9.8, 10.8)
    private val planeXs = doubleArrayOf(-188.0, -188.0, -167.0, -131.0, -92.0, -55.0, -17.0, 16.0, 55.0, 82.0, 120.0, 158.0, 197.0, 273.0, 333.0, 409.0)

    // The previous loop's token plane: leaves to the right until 2.5 s, waits unseen, and comes back
    // from 10.55 s so the loop closes on itself.
    private val previousTimes = doubleArrayOf(0.0, 2.5, 10.55, 10.8)
    private val previousXs = doubleArrayOf(226.0, 416.0, 226.0, 226.0)
    private val previousOpacityTimes = doubleArrayOf(0.0, 2.5, 2.51, 10.55, 10.8)
    private val previousOpacities = doubleArrayOf(1.0, 1.0, 0.0, 0.0, 1.0)

    private val flareTimes = doubleArrayOf(5.0, 5.4, 7.6, 8.1)
    private val flareValues = doubleArrayOf(0.0, 1.0, 1.0, 0.0)
    private val glowTimes = doubleArrayOf(0.0, 5.0, 5.4, 7.6, 8.1, 10.8)
    private val glowValues = doubleArrayOf(0.85, 0.85, 1.0, 1.0, 0.85, 0.85)

    private const val CHARACTERS = "08#0B8D0#8O08B#0"

    /**
     * `GLYPHS`: the reference's token matrix in eight phases, 16 rows of 35 small glyphs. The text
     * changes twelve times a second, stepping through the phases.
     */
    val glyphs: Array<String> = Array(8) { phase ->
        (0 until 16).joinToString("\n") { row ->
            String(CharArray(35) { column -> CHARACTERS[(row * 7 + column * 11 + row * column + phase * (column % 3)) % 16] })
        }
    }

    /** `interpolate(t, input, output, 'clamp')`. */
    fun interpolate(t: Double, input: DoubleArray, output: DoubleArray): Double {
        if (t <= input[0]) return output[0]
        val last = input.size - 1
        if (t >= input[last]) return output[last]
        var i = 1
        while (t > input[i]) i++
        val fraction = (t - input[i - 1]) / (input[i] - input[i - 1])
        return output[i - 1] + (output[i] - output[i - 1]) * fraction
    }

    /** The left edge of the plane in the 362 pt source viewport at loop time [t] (`sourceX`). */
    fun planeSourceX(t: Double, previous: Boolean): Double =
        if (previous) interpolate(t, previousTimes, previousXs) else interpolate(t, planeTimes, planeXs)

    /** The opacity of the previous loop's token plane (`opacity` of the `previous` Plane). */
    fun previousOpacity(t: Double): Double = interpolate(t, previousOpacityTimes, previousOpacities)

    /** `flare`: how far the seam has become a white band (0 idle, 1 while the card crosses). */
    fun flare(t: Double, reduced: Boolean): Double =
        if (reduced) 0.35 else interpolate(t, flareTimes, flareValues)

    /** `glow`: the brightness of the two violet glows, with the fast shimmer of the light. */
    fun glow(t: Double, reduced: Boolean): Double =
        if (reduced) 0.8 else interpolate(t, glowTimes, glowValues) * (0.94 + 0.06 * sin(t * 23))

    /** The phase of the token matrix shown at loop time [t] (`Math.floor(time * 12) % GLYPHS.length`). */
    fun tokenPhase(t: Double, reduced: Boolean): Int =
        if (reduced) 0 else floor(t * 12).toInt() % glyphs.size

    /** `hash`: the seeded noise of the dust, the sparks and the edge. */
    fun hash(i: Int): Double {
        val v = sin(i * 127.1 + 311.7) * 43758.5453
        return v - floor(v)
    }

    /** Folds a preview time into 0 until [loop] (`normalizedPreview`); null when it is not a number. */
    fun normalizePreview(value: Double?, loop: Double): Double? {
        if (value == null || !value.isFinite()) return null
        val phase = value % loop
        return if (phase < 0) phase + loop else phase
    }
}
