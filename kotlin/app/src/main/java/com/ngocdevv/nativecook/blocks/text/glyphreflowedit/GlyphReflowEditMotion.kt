package com.ngocdevv.nativecook.blocks.text.glyphreflowedit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The loop as pure functions of time; the same numbers as the React Native block's `motion.ts`. */
internal object GlyphReflowEditMotion {
    const val LOOP = 9f
    const val RESUME_AFTER = 2f

    // original -> Shorter
    const val MORPH_A0 = 0.95f
    const val MORPH_A1 = 2.35f
    // the ghost drags the bar back to the original
    const val SCRUB_BACK0 = 3.55f
    const val SCRUB_BACK1 = 4.35f
    // chip indicator moves (text is on the original here)
    const val TONE_TO_WARMER0 = 4.55f
    const val TONE_TO_WARMER1 = 4.8f
    // original -> Warmer
    const val MORPH_B0 = 4.85f
    const val MORPH_B1 = 6.25f
    // Warmer -> original
    const val MORPH_BACK0 = 7.05f
    const val MORPH_BACK1 = 7.85f
    const val TONE_TO_FIRST0 = 7.95f
    const val TONE_TO_FIRST1 = 8.2f

    // GLYPH
    const val SPREAD = 0.34f // share of the morph used by the reading-order stagger
    const val LETTER_LAG = 0.008f // delay added per letter inside a word
    const val ARC_MIN = 5f
    const val ARC_MAX = 22f
    const val ARC_PER_POINT = 0.09f
    const val OUT_END = 0.34f // a removed glyph is gone this far into its window
    const val OUT_STAGGER = 0.25f
    const val DROP = 10f
    const val IN_START = 0.52f
    const val IN_STAGGER = 0.2f
    const val IN_LENGTH = 0.28f
    const val RISE = 7f

    // response ~0.48 s, damping ratio 0.85
    const val SPRING_STIFFNESS = 170f
    const val SPRING_DAMPING = 22.2f

    fun seg(t: Float, a: Float, b: Float) = min(max((t - a) / (b - a), 0f), 1f)

    fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k

    fun easeInOutCubic(k: Float) = if (k < 0.5f) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2

    fun easeInOutSine(k: Float) = -(cos(PI.toFloat() * k) - 1) / 2

    fun easeOutCubic(k: Float) = 1 - (1 - k).pow(3)

    /** Morph amount 0 (original) to 1 (rewrite). Linear in the morphs: each glyph applies its own ease. */
    fun progressAt(t: Float): Float {
        if (t < SCRUB_BACK0) return seg(t, MORPH_A0, MORPH_A1)
        if (t < MORPH_B0) return 1 - easeInOutSine(seg(t, SCRUB_BACK0, SCRUB_BACK1))
        if (t < MORPH_BACK0) return seg(t, MORPH_B0, MORPH_B1)
        return 1 - seg(t, MORPH_BACK0, MORPH_BACK1)
    }

    /** Which rewrite the text is morphing to. It only changes while the morph is at 0. */
    fun toneAt(t: Float) = if (t >= TONE_TO_WARMER0 && t < TONE_TO_FIRST0) 1 else 0

    fun toneIndicatorAt(t: Float) =
        easeInOutCubic(seg(t, TONE_TO_WARMER0, TONE_TO_WARMER1)) * (1 - easeInOutCubic(seg(t, TONE_TO_FIRST0, TONE_TO_FIRST1)))

    fun ghostOpacityAt(t: Float): Float {
        val chipA = seg(t, 0.6f, 0.78f) * (1 - seg(t, 1.05f, 1.25f))
        val bar = seg(t, 3.15f, 3.38f) * (1 - seg(t, 4.3f, 4.48f))
        val chipB = seg(t, 4.5f, 4.66f) * (1 - seg(t, 4.95f, 5.15f))
        return max(chipA, max(bar, chipB))
    }

    /** 0 = hovering, 1 = pressed. */
    fun ghostPressAt(t: Float): Float {
        val chipA = seg(t, 0.82f, 0.92f) * (1 - seg(t, 0.98f, 1.1f))
        val bar = seg(t, 3.4f, 3.52f) * (1 - seg(t, 4.3f, 4.42f))
        val chipB = seg(t, 4.7f, 4.8f) * (1 - seg(t, 4.86f, 4.98f))
        return max(chipA, max(bar, chipB))
    }

    /** The bar past its ends gives way like a rubber band. */
    fun rubber(value: Float): Float {
        if (value < 0) return value / (1 - value * 3) * 0.4f
        if (value > 1) {
            val over = value - 1
            return 1 + (over / (1 + over * 3)) * 0.4f
        }
        return value
    }
}
