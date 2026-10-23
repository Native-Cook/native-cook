package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

/**
 * The cues, windows and curves of the React Native block's `motion.ts`. Times are milliseconds on
 * the loop clock; the functions are pure, like the worklets they come from.
 */
internal object DonationFanCarouselMotion {
    /** `LOOP_MS`. */
    const val LoopMs = 5366.0

    /** `RESUME_MS`: how long after the last touch the loop starts again from rest. */
    const val ResumeMs = 3500.0

    /** `cue`. */
    object Cue {
        const val FirstCoinIn = 700.0
        const val FirstCoinOut = 1480.0
        const val AdvanceStart = 980.0
        const val AdvanceEnd = 1680.0
        const val HeartsIn = 1880.0
        const val HeartsOut = 3150.0
        const val SecondCoinIn = 3380.0
        const val SecondCoinOut = 4320.0
        const val ReturnStart = 3670.0
        const val ReturnEnd = 4460.0
    }

    /** `snap`: the spring that settles the fan when a drag is released. */
    const val SnapDurationMs = 430.0
    const val SnapDampingRatio = 0.86

    /** `projectSeconds`: how far ahead of the release the velocity is projected. */
    const val ProjectSeconds = 0.14

    /** `dragPointsPerPage`: dp of drag per donor, at stage scale 1. */
    const val DragPointsPerPage = 112.0

    /** The loop time wrapped into [0, LOOP_MS): `((input % LOOP_MS) + LOOP_MS) % LOOP_MS`. */
    fun wrapped(input: Double): Double {
        return ((input % LoopMs) + LoopMs) % LoopMs
    }

    /** `smoother`: the quintic ease (6x^5 - 15x^4 + 10x^3) of a clamped input. */
    fun smoother(value: Double): Double {
        val x = DonationFanCarouselMath.clamp01(value)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    /** `window`: 0 before [start], 1 between the two edges, 0 again after [end]. */
    fun window(t: Double, start: Double, end: Double, edge: Double = 130.0): Double {
        return smoother((t - start) / edge) * (1 - smoother((t - (end - edge)) / edge))
    }

    /** `pageAt`: the fan position at loop time [input], 0...3. */
    fun pageAt(input: Double): Double {
        val t = wrapped(input)
        if (t < Cue.AdvanceStart) return 0.0
        if (t < Cue.AdvanceEnd) {
            return 3 * smoother((t - Cue.AdvanceStart) / (Cue.AdvanceEnd - Cue.AdvanceStart))
        }
        if (t < Cue.ReturnStart) return 3.0
        if (t < Cue.ReturnEnd) {
            return 3 * (1 - smoother((t - Cue.ReturnStart) / (Cue.ReturnEnd - Cue.ReturnStart)))
        }
        return 0.0
    }

    /** `coinAt`: how present the contribution coin is, 0...1. */
    fun coinAt(input: Double): Double {
        val t = wrapped(input)
        return maxOf(
            window(t, Cue.FirstCoinIn, Cue.FirstCoinOut, 100.0),
            window(t, Cue.SecondCoinIn, Cue.SecondCoinOut, 110.0),
        )
    }

    /** `heartsAt`: how present the hearts are, 0...1. */
    fun heartsAt(input: Double): Double {
        val t = wrapped(input)
        return window(t, Cue.HeartsIn, Cue.HeartsOut, 180.0)
    }

    /** `borderAt`: the lime keyline is hidden while the fan is travelling, otherwise 1. */
    fun borderAt(input: Double): Double {
        val t = wrapped(input)
        val advancing = t >= Cue.AdvanceStart && t <= Cue.AdvanceEnd
        val returning = t >= Cue.ReturnStart && t <= Cue.ReturnEnd
        return if (advancing || returning) 0.0 else 1.0
    }
}
