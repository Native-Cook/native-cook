package com.ngocdevv.nativecook.blocks.cards.postcardstampcancel

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Layout, timeline and poses of the Postcard Stamp Cancel (the numbers of postcardMotion.ts). */
internal object PostcardStampCancelMotion {
    /** Loop period in seconds. */
    const val LOOP = 9f

    /** The design box (393 × 759 dp); the screen scales it to fit. */
    const val DESIGN_W = 393f
    const val DESIGN_H = 759f

    /** The postcard (writing side up), in design dp. */
    const val CARD_X = 24f
    const val CARD_Y = 116f
    const val CARD_W = 345f
    const val CARD_H = 230f

    /** The ink of the cancellation lives in this box on the card (card dp). */
    const val INK_X = 196f
    const val INK_Y = 6f
    const val INK_W = 146f
    const val INK_H = 104f

    /** Centre of the date ring in ink-box dp; the wavy bars run to its right. */
    const val RING_X = 56f
    const val RING_Y = 52f
    const val RING_R = 33f
    const val RING_INNER = 22f

    /** The duplex handstamp's rubber block and knob. */
    const val TOOL_W = 112f
    const val TOOL_H = 72f
    const val TOOL_KNOB = 26f
    const val TOOL_PAD = 6f

    /** The ink pad's centre: the tool rests here. */
    const val PAD_X = 116f
    const val PAD_Y = 522f
    const val PAD_R = 70f

    /** Where the tool's centre sits when it prints. */
    const val TARGET_X = CARD_X + INK_X + 76f
    const val TARGET_Y = CARD_Y + INK_Y + RING_Y

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
    fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

    /** A damped wobble starting at t0: 0 before it, settling to 0 after about [len] seconds. */
    fun kick(t: Float, t0: Float, len: Float, freq: Float): Float {
        if (t < t0 || t > t0 + len) return 0f
        val u = t - t0
        return exp(-u * 5f / len) * sin(u * freq) * (1f - seg(t, t0 + len * 0.7f, t0 + len))
    }

    /**
     * One frame of the screen: the tool's centre and height (0 resting … 1 carried), the rubber's
     * squash, the ink transferred and its opacity, the card's flip and tilt in degrees, and the
     * trip counter (0 shows 12, 1 shows 13).
     */
    class Pose(
        val x: Float,
        val y: Float,
        val h: Float,
        val squash: Float,
        val ink: Float,
        val fade: Float,
        val flip: Float,
        val tilt: Float,
        val count: Float,
    )

    /**
     * The self-playing loop. Rest 0–1.4 s; lift 1.4–1.75 s and carry over the stamp along an arc
     * 1.6–2.4 s; press 2.4–3.2 s; lift 3.2–3.55 s; carry home 3.6–4.5 s and set down 4.5–4.85 s;
     * the card turns to its photo and back as a fresh card 5.8–7.6 s; rest.
     */
    fun autoPose(t: Float): Pose {
        val go = easeInOutCubic(seg(t, 1.6f, 2.4f))
        val back = easeInOutCubic(seg(t, 3.6f, 4.5f))
        val u = go * (1f - back)
        val arc = sin(PI.toFloat() * (if (go < 1f) go else back)) * 46f
        val x = PAD_X + (TARGET_X - PAD_X) * u
        val y = PAD_Y + (TARGET_Y - PAD_Y) * u - arc
        var h = easeOutCubic(seg(t, 1.4f, 1.75f))
        h += 0.1f * sin(PI.toFloat() * seg(t, 2.4f, 2.52f))
        h -= easeInOutCubic(seg(t, 2.5f, 2.72f))
        h += easeOutCubic(seg(t, 3.2f, 3.55f))
        h -= easeInOutCubic(seg(t, 4.5f, 4.85f))
        val squash = sin(PI.toFloat() * seg(t, 2.72f, 3.2f)) * 0.9f + 0.35f * sin(PI.toFloat() * seg(t, 4.85f, 5.1f))
        val ink = easeOutCubic(seg(t, 2.72f, 3.15f))
        val fade = if (t < 6.5f) 1f else 0f
        val turn = easeInOutCubic(seg(t, 5.8f, 6.5f)) + easeInOutCubic(seg(t, 6.9f, 7.6f))
        val tilt = -1.6f * kick(t, 2.72f, 1.1f, 16f)
        val count = easeInOutCubic(seg(t, 3.6f, 3.95f)) * (1f - easeInOutCubic(seg(t, 6.55f, 6.85f)))
        return Pose(x, y, h, squash, ink, fade, 180f * turn, tilt, count)
    }

    /** Reduced motion: the card stamped, the tool back on its pad. */
    fun stillPose() = Pose(PAD_X, PAD_Y, 0f, 0f, 1f, 1f, 0f, 0f, 1f)
}
