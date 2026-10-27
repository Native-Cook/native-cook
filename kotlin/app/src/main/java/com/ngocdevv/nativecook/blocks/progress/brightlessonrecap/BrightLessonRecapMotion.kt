package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One period of the autoPlay loop in seconds (`LOOP` in BrightLessonRecap.tsx:18). */
const val BRIGHT_LESSON_RECAP_LOOP = 6.5

/** The three result pills the screen counts up. */
internal enum class BrightLessonRecapStatKind { Points, Accuracy, Time }

/**
 * One result pill: its label, the number it counts to, the phase times of its animation and its
 * three colours. Copied from the `STATS` table in BrightLessonRecap.tsx:24-28.
 */
internal class BrightLessonRecapStat(
    val label: String,
    val value: Int,
    /** The small sparkles around the pill start here. */
    val sparkle: Double,
    /** The outlined body starts to grow here. */
    val body: Double,
    /** The coloured cap starts and finishes rising here. */
    val cap: Double,
    val capEnd: Double,
    /** The count lands here and the number gives its small kick. */
    val land: Double,
    val color: Color,
    val light: Color,
    val border: Color,
    val kind: BrightLessonRecapStatKind,
    /** The spoken value, as in the React Native accessibility label. */
    val spoken: String,
)

internal val BrightLessonRecapStats = listOf(
    BrightLessonRecapStat(
        "TOTAL XP", 45, 1.90, 2.367, 2.667, 2.734, 3.1,
        Color(0xFFD98A00), Color(0xFFFFF4D6), Color(0xFFF4AE16), BrightLessonRecapStatKind.Points,
        "total xp: 45",
    ),
    BrightLessonRecapStat(
        "AMAZING", 100, 2.85, 2.967, 3.25, 3.283, 3.283,
        Color(0xFF3E9B22), Color(0xFFE7F7DE), Color(0xFF5BC236), BrightLessonRecapStatKind.Accuracy,
        "amazing: 100 percent",
    ),
    BrightLessonRecapStat(
        "QUICK", 220, 3.56, 3.65, 3.967, 4.05, 3.95,
        Color(0xFF1985C9), Color(0xFFE1F2FC), Color(0xFF2FA8EA), BrightLessonRecapStatKind.Time,
        "quick: 3 minutes 40 seconds",
    ),
)

/** The scene's pure functions of loop time, one-to-one with the worklets in BrightLessonRecap.tsx and motion.ts. */
internal object BrightLessonRecapMotion {
    /** The reference story ends here; the rest of the loop fades and resets (BrightLessonRecap.tsx:19). */
    const val SOURCE_END = 5.851

    /** The static, complete pose (`settled` argument of `useSceneClock`, BrightLessonRecap.tsx:100). */
    const val SETTLED = 5.0

    /** The scene has faded out completely here (BrightLessonRecap.tsx:123). */
    const val FADE_END = 6.25

    /** The mascot and bands start entering here. */
    const val MASCOT_FROM = 0.333

    /** The actions can be used from here (BrightLessonRecap.tsx:106). */
    const val ACTIONS_FROM = 2.35

    /** The hero swaps its big title for the small one here. */
    const val TITLE_SWAP = 1.333

    private val enterCurve = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    private val dockTimes = doubleArrayOf(1.2, 1.267, 1.3, 1.333, 1.35, 1.4, 1.45)
    private val dockValues = doubleArrayOf(0.0, 0.09, 0.22, 0.63, 0.80, 0.96, 1.0)

    private val mascotXTimes = doubleArrayOf(0.333, 0.4, 0.5, 0.6, 0.75, 1.2, 1.45)
    private val mascotXValues = doubleArrayOf(-0.8, -0.28, -0.09, -0.02, 0.015, 0.085, 0.03)

    private val mascotYTimes = doubleArrayOf(0.333, 0.5, 0.75, 1.2, 1.3, 1.35, 1.5, 1.7, 1.9)
    private val mascotYValues = doubleArrayOf(0.44, 0.439, 0.428, 0.423, 0.433, 0.427, 0.392, 0.378, 0.376)

    /** `ENTER`, the strong ease-out `Easing.bezierFn(0.23, 1, 0.32, 1)`. */
    fun enter(x: Double): Double = enterCurve.transform(x.toFloat()).toDouble()

    /** Progress of [t] between [a] and [b], clamped, reaching exactly 1 at the end (`fraction`). */
    fun fraction(t: Double, a: Double, b: Double): Double {
        if (!t.isFinite()) return 0.0
        if (t >= b - 1e-9) return 1.0
        return min(1.0, max(0.0, (t - a) / (b - a)))
    }

    /** Smoothstep of [t] between [start] and [end] (`phase` in motion.ts). */
    fun phase(t: Double, start: Double, end: Double): Double {
        val x = min(1.0, max(0.0, (t - start) / (end - start)))
        return x * x * (3 - 2 * x)
    }

    /** `normalizeSceneTime`: a time folded into [0, period). */
    fun normalize(value: Double, period: Double): Double {
        if (!value.isFinite() || !period.isFinite() || period <= 0) return 0.0
        val remainder = value % period
        return if (remainder < 0) remainder + period else remainder
    }

    /** Piecewise-linear table lookup that clamps outside the table (`interpolate(..., 'clamp')`). */
    fun interpolate(t: Double, inputs: DoubleArray, outputs: DoubleArray): Double {
        if (t <= inputs[0]) return outputs[0]
        val last = inputs.size - 1
        if (t >= inputs[last]) return outputs[last]
        var i = 1
        while (t > inputs[i]) i++
        val u = (t - inputs[i - 1]) / (inputs[i] - inputs[i - 1])
        return outputs[i - 1] + u * (outputs[i] - outputs[i - 1])
    }

    /** How far the big title and the mascot have docked into the compact layout, 0 to 1 (`dockAt`). */
    fun dock(t: Double): Double = interpolate(t, dockTimes, dockValues)

    /** The mascot's horizontal drift as a fraction of the window width. */
    fun mascotX(t: Double): Double = interpolate(t, mascotXTimes, mascotXValues)

    /** The mascot's centre as a fraction of the window height. */
    fun mascotCenter(t: Double): Double = interpolate(t, mascotYTimes, mascotYValues)

    /** The counter's progress for the pill at [index] (`counterProgress`). */
    fun counterProgress(index: Int, t: Double): Double {
        if (index == 0) return 0.89 * enter(fraction(t, 2.35, 2.65)) + 0.11 * fraction(t, 2.8, 3.1)
        return fraction(t, if (index == 1) 2.95 else 3.65, if (index == 1) 3.283 else 3.95)
    }

    /**
     * The text of a pill's counter. The source counts the minute and second columns
     * independently, then lands on 3:40 (BrightLessonRecap.tsx:69-70).
     */
    fun counterText(index: Int, t: Double): String {
        val stat = BrightLessonRecapStats[index]
        val p = counterProgress(index, t)
        if (stat.kind == BrightLessonRecapStatKind.Time) {
            val minutes = floor(3 * p).toInt()
            val seconds = jsRound(40 * p).toString().padStart(2, '0')
            return "$minutes:$seconds"
        }
        val suffix = if (stat.kind == BrightLessonRecapStatKind.Accuracy) "%" else ""
        return "${jsRound(stat.value * p)}$suffix"
    }

    /** JavaScript's `Math.round`: ties go up. */
    private fun jsRound(value: Double): Int = floor(value + 0.5).toInt()
}
