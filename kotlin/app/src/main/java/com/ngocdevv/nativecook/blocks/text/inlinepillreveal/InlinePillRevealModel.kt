package com.ngocdevv.nativecook.blocks.text.inlinepillreveal

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Timing, copy, colours and the frame the Inline Pill Reveal draws (a port of
 * InlinePillRevealModel.swift). Every value is a function of the loop time (autoPlay) or of the
 * reveal amount q (touch), so the three stacks match.
 */
internal object IprModel {
    const val LOOP = 8.0

    val LINE1 = listOf("Slow", "mornings,")
    val LINE2 = listOf("made", "yours")
    const val BRAND = "slowfield"
    const val SKIP = "Skip"
    const val CTA = "Find your stay"

    const val PAGE = 0xB9715AL
    const val CREAM = 0xFBF3EAL
    const val INK = 0x3A2420L

    const val FONT_SIZE = 44f
    const val TRACKING = -0.4f
    const val BASELINE_DROP = 15.0
    const val CARET_W = 2.5
    const val CARET_H = 44.0
    const val PILL_H = 76.0
    const val PILL_MARGIN = 14.0
    const val WORD_GAP = 12.0
    const val CARET_PART = 3.0
    const val DRAG_SPAN = 0.75

    const val REGULAR_ASSET = "inlinepillreveal/inlinepillreveal_Outfit-Regular.ttf"
    const val MEDIUM_ASSET = "inlinepillreveal/inlinepillreveal_Outfit-Medium.ttf"

    fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeIn(x: Double) = x * x * x
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /** The autoPlay timeline (seconds in 0 until LOOP). Frame 0 and frame LOOP are both the text line at rest. */
    fun timeline(t: Double): IprFrame {
        val f = IprFrame()
        f.t = t
        f.part = easeOut(seg(t, 0.70, 0.90)) * (1 - easeInOut(seg(t, 7.30, 7.55)))
        val blinkA = seg(t, 0.70, 0.78) - seg(t, 0.98, 1.06)
        val blinkB = seg(t, 1.14, 1.22) * (1 - seg(t, 1.40, 1.47))
        val closing = seg(t, 7.36, 7.40) * (1 - seg(t, 7.46, 7.56))
        f.caret = max(blinkA, max(blinkB, closing))
        f.open = easeInOut(seg(t, 1.40, 2.60)) * (1 - easeInOut(seg(t, 6.40, 7.40)))
        f.full = easeInOut(seg(t, 2.60, 3.30)) * (1 - easeInOut(seg(t, 5.70, 6.40)))
        for (i in 0 until 2) {
            f.wordIn[i] = easeOut(seg(t, 3.25 + 0.10 * i, 3.70 + 0.10 * i))
            f.wordOut[i] = easeIn(seg(t, 5.46 - 0.06 * i, 5.76 - 0.06 * i))
        }
        f.ctaIn = easeOut(seg(t, 3.45, 3.90))
        f.ctaOut = easeIn(seg(t, 5.40, 5.70))
        f.dots = f.full
        return f
    }

    /** The same screen driven by the reveal amount q (0 = text line, 1 = full scene). */
    fun reveal(q: Double, t: Double): IprFrame {
        val f = IprFrame()
        f.t = t
        f.open = easeInOut(seg(q, 0.0, 0.6))
        f.full = easeInOut(seg(q, 0.6, 1.0))
        for (i in 0 until 2) f.wordIn[i] = easeOut(seg(q, 0.84 + 0.05 * i, 0.95 + 0.05 * i))
        f.ctaIn = easeOut(seg(q, 0.9, 1.0))
        f.dots = f.full
        return f
    }

    /** The reveal amount that shows the same pill as f (open and full never move at the same time). */
    fun q(f: IprFrame) = if (f.full > 0) 0.6 + 0.4 * inverseEaseInOut(f.full) else 0.6 * inverseEaseInOut(f.open)

    private fun inverseEaseInOut(y: Double): Double = when {
        y <= 0 -> 0.0
        y >= 1 -> 1.0
        y < 0.5 -> cbrt(y / 4)
        else -> 1 - cbrt(2 * (1 - y)) / 2
    }
}

internal class IprFrame {
    var t = 0.0
    var part = 0.0
    var caret = 0.0
    var open = 0.0
    var full = 0.0
    val wordIn = doubleArrayOf(0.0, 0.0)
    val wordOut = doubleArrayOf(0.0, 0.0)
    var ctaIn = 0.0
    var ctaOut = 0.0
    var dots = 0.0
}

/** Plays the timeline, hands over to the finger, and eases home 2 s after the last touch. */
internal class IprDriver {
    private var epoch: Double? = null
    var manual = false
        private set
    var dragging = false
        private set
    private var q = 0.0
    private var q0 = 0.0
    private var animFrom = 0.0
    private var animTo = 0.0
    private var animStart = 0.0
    private var animDur = 0.0
    private var animating = false
    private var lastTouch = -100.0
    private var returning = false
    private var shown = 1.0
    private var fadeFrom = 1.0
    private var fadeStart = -100.0

    private fun animated(now: Double): Double {
        if (!animating) return q
        val k = IprModel.seg(now, animStart, animStart + animDur)
        val v = animFrom + (animTo - animFrom) * IprModel.easeInOut(k)
        if (k >= 1) {
            q = animTo
            animating = false
        }
        return v
    }

    private fun animate(target: Double, from: Double, now: Double) {
        q = from
        animFrom = from
        animTo = target
        animStart = now
        animDur = 0.3 + 1.2 * abs(target - from)
        animating = true
    }

    fun frame(now: Double, auto: Boolean, reduced: Boolean): IprFrame {
        if (epoch == null) epoch = now
        val t = (now - (epoch ?: now)) % IprModel.LOOP
        if (reduced) return IprModel.reveal(1.0, 0.0)
        if (manual) {
            val v = animated(now)
            if (auto && !dragging && now - lastTouch > 2) {
                if (!returning) {
                    returning = true
                    animate(0.0, v, now)
                } else if (!animating) {
                    manual = false
                    returning = false
                    epoch = now
                    return IprModel.timeline(0.0)
                }
            }
            return IprModel.reveal(v, t)
        }
        return if (auto) IprModel.timeline(t) else IprModel.reveal(0.0, t)
    }

    /** Whether frames must keep coming without autoPlay (a touch or a fade still moving). */
    fun busy(now: Double, reduced: Boolean) =
        if (reduced) now - fadeStart < 0.3 else dragging || animating

    fun shownAmount(now: Double): Double {
        val k = IprModel.seg(now, fadeStart, fadeStart + 0.25)
        return fadeFrom + (shown - fadeFrom) * k
    }

    fun toggleReduced(now: Double) {
        fadeFrom = shownAmount(now)
        shown = if (shown > 0.5) 0.0 else 1.0
        fadeStart = now
    }

    fun accepts(y: Double, cy: Double, now: Double, auto: Boolean): Boolean {
        val f = frame(now, auto, false)
        return IprModel.q(f) > 0.02 || abs(y - cy) < 70
    }

    fun begin(now: Double, auto: Boolean) {
        val f = frame(now, auto, false)
        q = if (manual) animated(now) else IprModel.q(f)
        q0 = q
        animating = false
        manual = true
        dragging = true
        returning = false
        lastTouch = now
    }

    fun drag(dx: Double, width: Double, now: Double) {
        val dir = if (q0 < 0.5) 1.0 else -1.0
        q = min(max(q0 + dir * abs(dx) / (IprModel.DRAG_SPAN * width), 0.0), 1.0)
        lastTouch = now
    }

    /** Ends the drag or tap; true when the reveal commits to the other state. */
    fun end(dx: Double, vx: Double, tap: Boolean, width: Double, now: Double): Boolean {
        dragging = false
        lastTouch = now
        val wasOpen = q0 >= 0.5
        val target = if (tap) {
            if (wasOpen) 0.0 else 1.0
        } else {
            val dir = if (wasOpen) -1.0 else 1.0
            val projected = q0 + dir * abs(dx + 0.25 * vx) / (IprModel.DRAG_SPAN * width)
            if (projected > 0.5) 1.0 else 0.0
        }
        animate(target, q, now)
        return (target > 0.5) != wasOpen
    }
}
