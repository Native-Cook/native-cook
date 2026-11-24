package com.ngocdevv.nativecook.blocks.text.scrollreadhighlight

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The scripted scroll and the reading front, as pure functions; the same numbers as the React Native block's `motion.ts`. */
internal object ScrollReadHighlightMotion {
    const val LOOP = 10f
    const val RESUME_AFTER = 2f
    const val RETURN_SECONDS = 1.1f

    /** Three flicks, each to a share of the article's scroll range: start, end, to. */
    private val FLICK_START = floatArrayOf(1.0f, 2.7f, 4.4f)
    private val FLICK_END = floatArrayOf(2.6f, 4.3f, 6.2f)
    private val FLICK_TO = floatArrayOf(0.2f, 0.42f, 0.62f)
    const val SCRIPT_RETURN0 = 7.0f
    const val SCRIPT_RETURN1 = 8.6f

    /** A word takes this many words of travel to ink in: the soft leading edge. */
    const val FADE_WORDS = 4f
    const val WORDS_PER_MINUTE = 140f

    fun seg(t: Float, a: Float, b: Float) = min(max((t - a) / (b - a), 0f), 1f)

    fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k

    fun easeOutCubic(k: Float) = 1 - (1 - k).pow(3)

    fun easeInOutCubic(k: Float) = if (k < 0.5f) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2

    /** Share of the scroll range (0..1) at loop time t. */
    fun scriptAt(t: Float): Float {
        var at = 0f
        for (i in FLICK_TO.indices) {
            at = mix(at, FLICK_TO[i], easeOutCubic(seg(t, FLICK_START[i], FLICK_END[i])))
        }
        return at * (1 - easeInOutCubic(seg(t, SCRIPT_RETURN0, SCRIPT_RETURN1)))
    }

    fun ghostOpacityAt(t: Float): Float {
        var opacity = 0f
        for (start in FLICK_START) {
            opacity = max(opacity, seg(t, start - 0.2f, start - 0.04f) * (1 - seg(t, start + 0.34f, start + 0.5f)))
        }
        return opacity
    }

    /** The ghost finger's travel, 0 (low on the screen) to 1 (high), during each flick. */
    fun ghostTravelAt(t: Float): Float {
        var travel = 0f
        for (start in FLICK_START) {
            if (t >= start - 0.2f && t <= start + 0.5f) travel = easeOutCubic(seg(t, start, start + 0.4f))
        }
        return travel
    }

    /** Index of the last line whose top is at or above y, or -1. */
    fun lineAt(y: Float, tops: FloatArray): Int {
        var low = 0
        var high = tops.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (tops[middle] <= y) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }

    /** The reading front in words (fractional), from the article's y of the reading line. */
    fun frontAt(y: Float, tops: FloatArray, heights: FloatArray, firsts: IntArray, counts: IntArray): Float {
        val line = lineAt(y, tops)
        if (line < 0) return 0f
        val along = min(max((y - tops[line]) / heights[line], 0f), 1f)
        return firsts[line] + along * counts[line]
    }

    fun wordInk(front: Float, word: Int): Float {
        val k = min(max((front - word) / FADE_WORDS, 0f), 1f)
        return k * k * (3 - 2 * k)
    }
}
