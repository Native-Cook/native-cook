package com.ngocdevv.nativecook.blocks.inputs.oracleglassgrid

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Timing, copy, colours, icons and the frame of the Oracle Glass Grid (a port of OracleGlassGridModel.swift).
 * Every value is a function of the story time t (0 until LOOP) and the shimmer clock.
 */
internal object OggModel {
    const val LOOP = 10.0

    // Copy
    const val TITLE_GRID = "What answers do you seek?"
    const val TITLE_FIELD = "Type your question"
    const val PLACEHOLDER = "Tap to type"
    const val SAMPLE = "Should I take the leap?"
    const val ASK = "ASK FOR ME"
    val LABELS = listOf(
        listOf("School"), listOf("Friends and", "Family"), listOf("Love"),
        listOf("Career"), listOf("Life's Greatest", "Mysteries"), listOf("My Future"),
        listOf("Myself"), listOf("Style"), listOf("Lunch"),
    )
    const val AUTO_CHOICE = 3

    // Colours
    const val WHITE = 0xFFFFFF
    const val CARD_WHITE = 0xF6F3EE
    const val INK = 0x1E2A23

    // Layout (dp)
    const val CARD_W = 100.0
    const val CARD_H = 120.0
    const val CARD_R = 14.0
    const val GAP = 7.0
    const val CENTER_SCALE = 1.22
    const val FIELD_H = 56.0
    const val FIELD_MARGIN = 26.5
    const val CARET_W = 2.0
    const val CARET_H = 24.0
    const val CAMERA = 600.0
    const val ICON_SCALE = 1.18
    const val ICON_LIFT = 13.0
    const val ASK_W = 236.0
    const val ASK_H = 44.0

    fun gridCenter(w: Double, h: Double, top: Double, bottom: Double) = doubleArrayOf(w / 2, top + (h - top - bottom) / 2 + 10)
    fun fieldCenter(w: Double, h: Double) = doubleArrayOf(w / 2, h / 2)
    fun slotX(i: Int, gx: Double) = gx + (i % 3 - 1) * (CARD_W + GAP)
    fun slotY(i: Int, gy: Double) = gy + (i / 3 - 1) * (CARD_H + GAP)

    fun ranks(chosen: Int): IntArray {
        fun d(i: Int): Double {
            val dx = (i % 3 - chosen % 3).toDouble()
            val dy = (i / 3 - chosen / 3).toDouble()
            return sqrt(dx * dx + dy * dy)
        }
        val order = (0 until 9).filter { it != chosen }.sortedWith(compareBy<Int>({ d(it) }, { it }))
        val r = IntArray(9)
        order.forEachIndexed { k, i -> r[i] = k + 1 }
        return r
    }

    // Curves
    fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeIn(x: Double) = x * x * x
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutBack(x: Double, s: Double = 1.70158): Double {
        val c3 = s + 1
        return 1 + c3 * (x - 1).pow(3) + s * (x - 1).pow(2)
    }
    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun wave(t: Double, k: Double, phase: Double) = 0.5 + 0.5 * sin(2 * PI * k * t / LOOP + phase)
    fun blink(s: Double) = min(max(0.5 + 3 * cos(2 * PI * s), 0.0), 1.0)

    // Typing
    fun typeTime(k: Int) = 5.60 + 0.056 * k + (if (k == 0) 0.0 else 0.018 * sin(k * 2.3))
    fun typed(t: Double): Int {
        if (t >= 7.50) return 0
        var n = 0
        for (k in SAMPLE.indices) if (typeTime(k) <= t) n = k + 1
        return n
    }

    fun frame(t: Double, chosen: Int, typedOverride: Int? = null, blinkClock: Double? = null, clock: Double): OggFrame {
        val f = OggFrame()
        f.clock = clock
        f.chosen = chosen
        val rank = ranks(chosen)
        f.press = easeOut(seg(t, 1.30, 1.45)) * (1 - easeOutBack(seg(t, 1.45, 1.85), 1.2))
        f.pick = easeOut(seg(t, 1.45, 1.85)) * (1 - easeInOut(seg(t, 8.50, 8.90)))
        f.center = easeInOut(seg(t, 2.40, 3.30)) * (1 - easeInOut(seg(t, 8.50, 9.30)))
        f.vanish = easeIn(seg(t, 3.10, 3.50)) * (1 - easeOut(seg(t, 8.45, 8.65)))
        f.flip = easeIn(seg(t, 3.50, 4.00)) * (1 - easeOut(seg(t, 8.20, 8.55)))
        f.line = easeInOut(seg(t, 4.00, 4.25)) * (1 - easeInOut(seg(t, 7.95, 8.20)))
        f.open = easeOutBack(seg(t, 4.25, 4.90), 1.1) * (1 - easeInOut(seg(t, 7.45, 7.95)))
        f.halo = easeOut(seg(t, 1.45, 1.85)) * (1 - f.flip) * (1 - easeInOut(seg(t, 8.50, 8.90)))
        for (i in 0 until 9) {
            if (i == chosen) continue
            val a = 1.70 + 0.045 * (rank[i] - 1)
            val b = 8.60 + 0.04 * (rank[i] - 1)
            f.gather[i] = easeInOut(seg(t, a, a + 0.60)) * (1 - easeOutBack(seg(t, b, b + 0.55), 1.4))
        }
        f.ranks = rank
        f.titleGrid = (1 - easeIn(seg(t, 1.70, 2.00))) + easeOut(seg(t, 9.00, 9.40))
        f.titleGridY = if (t < 5) -8 * easeIn(seg(t, 1.70, 2.00)) else -8 * (1 - easeOut(seg(t, 9.00, 9.40)))
        f.titleField = easeOut(seg(t, 4.50, 4.90)) * (1 - easeIn(seg(t, 7.40, 7.70)))
        f.titleFieldY = if (t < 7.70) 8 * (1 - easeOut(seg(t, 4.50, 4.90))) else 8.0
        f.askAlpha = (1 - easeIn(seg(t, 1.70, 2.00))) + easeOut(seg(t, 4.70, 5.05)) * (1 - easeIn(seg(t, 7.40, 7.60))) +
            easeOut(seg(t, 9.10, 9.45))
        f.rings = easeOut(seg(t, 4.66, 4.90)) * (1 - easeIn(seg(t, 7.45, 7.60)))

        val n = typedOverride ?: typed(t)
        f.typed = n
        f.textAlpha = 1 - easeIn(seg(t, 7.30, 7.50))
        f.toPlaceholder = easeInOut(seg(t, 4.75, 5.00)) * (1 - easeIn(seg(t, 7.30, 7.50)))
        f.placeholder = if (n == 0) easeInOut(seg(t, 4.75, 5.00)) * (1 - easeIn(seg(t, 7.30, 7.45))) else 0.0
        var caret = 1.0
        if (t >= 4.90 && t < 7.30) {
            caret = when {
                blinkClock != null -> blink(blinkClock)
                t < 5.60 -> blink(t - 4.90)
                t >= 6.95 -> blink(t - 6.95)
                else -> 1.0
            }
            caret = max(caret, seg(t, 7.20, 7.30))
        }
        f.caret = caret
        f.lineAlpha = seg(f.flip, 0.86, 1.0)
        f.sheen = if (t < 1.15) seg(t, 0.15, 1.15) else 0.0
        return f
    }

    fun reduced(s: Double, chosen: Int): OggFrame {
        val f = frame(0.0, chosen, clock = 0.0)
        val open = frame(5.2, chosen, typedOverride = 0, blinkClock = 0.1, clock = 0.0)
        f.cardAlpha = 1 - s
        f.titleGrid = 1 - s
        f.titleField = s
        f.titleFieldY = 0.0
        f.open = open.open
        f.line = 1.0
        f.lineAlpha = 1.0
        f.fieldAlpha = s
        f.placeholder = 1.0
        f.toPlaceholder = 1.0
        f.rings = 1.0
        f.caret = 1.0
        f.sheen = 0.0
        f.reducedField = true
        return f
    }

    // Icons — 56 × 56 boxes, absolute SVG paths, drawn with 1.3 pt round strokes.
    class Mark(val d: String, val fill: Boolean = false, val glow: Boolean = false, val k: Double = 0.0, val ph: Double = 0.0, val dim: Double = 1.0)

    val ICONS: List<List<Mark>> = listOf(
        listOf(
            Mark("M28 41C22 37 14 37 8 39L8 23C14 21 22 21 28 25Z"),
            Mark("M28 41C34 37 42 37 48 39L48 23C42 21 34 21 28 25Z"),
            Mark("M28 25L28 41"),
            Mark("M19.5 19.9L13.9 17.9", glow = true, k = 4.0, ph = 0.0),
            Mark("M21.1 17.2L16.5 13.4", glow = true, k = 4.0, ph = -0.6),
            Mark("M23.5 15.2L20.5 10", glow = true, k = 4.0, ph = -1.2),
            Mark("M26.4 14.1L25.4 8.2", glow = true, k = 4.0, ph = -1.8),
            Mark("M29.6 14.1L30.6 8.2", glow = true, k = 4.0, ph = -2.4),
            Mark("M32.5 15.2L35.5 10", glow = true, k = 4.0, ph = -3.0),
            Mark("M34.9 17.2L39.5 13.4", glow = true, k = 4.0, ph = -3.6),
            Mark("M36.5 19.9L42.1 17.9", glow = true, k = 4.0, ph = -4.2),
        ),
        listOf(
            Mark("M14 36L28 15L42 32L24 46Z", dim = 0.45),
            Mark("M14 32L14.8 35.2L18 36L14.8 36.8L14 40L13.2 36.8L10 36L13.2 35.2Z", fill = true, glow = true, k = 3.0, ph = 0.4),
            Mark("M28 10.5L28.8 14.2L32.5 15L28.8 15.8L28 19.5L27.2 15.8L23.5 15L27.2 14.2Z", fill = true, glow = true, k = 4.0, ph = 2.1),
            Mark("M42 28L42.8 31.2L46 32L42.8 32.8L42 36L41.2 32.8L38 32L41.2 31.2Z", fill = true, glow = true, k = 5.0, ph = 4.0),
            Mark("M24 43.4L24.6 45.4L26.6 46L24.6 46.6L24 48.6L23.4 46.6L21.4 46L23.4 45.4Z", fill = true, glow = true, k = 6.0, ph = 1.3),
        ),
        listOf(
            Mark("M28 46C14 36 9 28 13 21C17 14 25 15 28 21C31 15 39 14 43 21C47 28 42 36 28 46Z"),
            Mark("M28 36C22 32 20 28 22 25C24 22 27 23 28 26C29 23 32 22 34 25C36 28 34 32 28 36Z", fill = true, glow = true, k = 5.0, ph = 0.9),
            Mark("M12 8.5L12.6 11.4L15.5 12L12.6 12.6L12 15.5L11.4 12.6L8.5 12L11.4 11.4Z", fill = true, glow = true, k = 3.0, ph = 2.6),
            Mark("M45 8L45.6 10.4L48 11L45.6 11.6L45 14L44.4 11.6L42 11L44.4 10.4Z", fill = true, glow = true, k = 4.0, ph = 5.0),
        ),
        listOf(
            Mark("M19 40L19.2 38.2L19.7 36.6L20.5 35L21.6 33.6L23 32.5L24.6 31.7L26.2 31.2L28 31L29.8 31.2L31.4 31.7L33 32.5L34.4 33.6L35.5 35L36.3 36.6L36.8 38.2L37 40", glow = true, k = 3.0, ph = 0.0),
            Mark("M13 40L13.3 37.1L14.1 34.3L15.5 31.7L17.4 29.4L19.7 27.5L22.3 26.1L25.1 25.3L28 25L30.9 25.3L33.7 26.1L36.3 27.5L38.6 29.4L40.5 31.7L41.9 34.3L42.7 37.1L43 40", glow = true, k = 3.0, ph = -0.9),
            Mark("M7 40L7.4 35.9L8.6 32L10.5 28.3L13.2 25.2L16.3 22.5L20 20.6L23.9 19.4L28 19L32.1 19.4L36 20.6L39.7 22.5L42.8 25.2L45.5 28.3L47.4 32L48.6 35.9L49 40", glow = true, k = 3.0, ph = -1.8),
            Mark("M28 29.5L29.1 33.9L33.5 35L29.1 36.1L28 40.5L26.9 36.1L22.5 35L26.9 33.9Z", fill = true, glow = true, k = 5.0, ph = 1.5),
        ),
        listOf(
            Mark("M7 30C15 19 41 19 49 30C41 41 15 41 7 30Z"),
            Mark("M34 30C34 33.3 31.3 36 28 36C24.7 36 22 33.3 22 30C22 26.7 24.7 24 28 24C31.3 24 34 26.7 34 30Z"),
            Mark("M30.6 30C30.6 31.4 29.4 32.6 28 32.6C26.6 32.6 25.4 31.4 25.4 30C25.4 28.6 26.6 27.4 28 27.4C29.4 27.4 30.6 28.6 30.6 30Z", fill = true, glow = true, k = 4.0, ph = 3.1),
            Mark("M19 21L16.5 15.5", glow = true, k = 6.0, ph = 0.2),
            Mark("M28 19L28 12.5", glow = true, k = 6.0, ph = -0.8),
            Mark("M37 21L39.5 15.5", glow = true, k = 6.0, ph = -1.6),
        ),
        listOf(
            Mark("M6 40L50 40"),
            Mark("M35 36C35 39.9 31.9 43 28 43C24.1 43 21 39.9 21 36C21 32.1 24.1 29 28 29C31.9 29 35 32.1 35 36Z", fill = true, glow = true, k = 3.0, ph = 0.6),
            Mark("M16.2 33.9L9.3 32.7", glow = true, k = 5.0, ph = 0.0),
            Mark("M17.6 30L11.5 26.5", glow = true, k = 5.0, ph = 2.4),
            Mark("M20.3 26.8L15.8 21.4", glow = true, k = 5.0, ph = 0.9),
            Mark("M23.9 24.7L21.5 18.1", glow = true, k = 5.0, ph = 3.6),
            Mark("M28 24L28 17", glow = true, k = 5.0, ph = 1.7),
            Mark("M32.1 24.7L34.5 18.1", glow = true, k = 5.0, ph = 4.4),
            Mark("M35.7 26.8L40.2 21.4", glow = true, k = 5.0, ph = 0.3),
            Mark("M38.4 30L44.5 26.5", glow = true, k = 5.0, ph = 2.9),
            Mark("M39.8 33.9L46.7 32.7", glow = true, k = 5.0, ph = 5.2),
        ),
        listOf(
            Mark("M28 7C35 7 39 14 39 21C39 28 35 35 28 35C21 35 17 28 17 21C17 14 21 7 28 7Z"),
            Mark("M28 35L28 50"),
            Mark("M24.5 38L31.5 38"),
            Mark("M21.5 17C22.3 14.2 24 12.4 26.4 11.6", glow = true, k = 4.0, ph = 1.0),
            Mark("M45 8L45.6 10.4L48 11L45.6 11.6L45 14L44.4 11.6L42 11L44.4 10.4Z", fill = true, glow = true, k = 3.0, ph = 3.7),
        ),
        listOf(
            Mark("M28 22C28 18 32 17.5 32 14C32 11 29.5 10 27.5 10.6C26 11.1 25 12.3 25 13.6"),
            Mark("M28 22L8 36L48 36Z"),
            Mark("M44 12L44.6 14.4L47 15L44.6 15.6L44 18L43.4 15.6L41 15L43.4 14.4Z", fill = true, glow = true, k = 4.0, ph = 0.5),
            Mark("M12 19.4L12.5 21.5L14.6 22L12.5 22.5L12 24.6L11.5 22.5L9.4 22L11.5 21.5Z", fill = true, glow = true, k = 6.0, ph = 2.8),
        ),
        listOf(
            Mark("M17 9L17 21C17 25 19 27 21 27L21 49"),
            Mark("M21 9L21 21"),
            Mark("M25 9L25 21C25 25 23 27 21 27"),
            Mark("M36 49L36 9C41 13 42 22 40 29L36 29"),
            Mark("M41 8.2L41.5 10.5L43.8 11L41.5 11.5L41 13.8L40.5 11.5L38.2 11L40.5 10.5Z", fill = true, glow = true, k = 5.0, ph = 4.2),
        ),
    )

    fun markAlpha(m: Mark, clock: Double): Double {
        if (m.k <= 0) return m.dim
        return m.dim * (0.45 + 0.55 * wave(clock, m.k, m.ph))
    }
}

internal class OggFrame {
    var clock = 0.0
    var chosen = OggModel.AUTO_CHOICE
    var ranks = IntArray(9)
    var press = 0.0
    var pick = 0.0
    val gather = DoubleArray(9)
    var center = 0.0
    var vanish = 0.0
    var flip = 0.0
    var line = 0.0
    var open = 0.0
    var halo = 0.0
    var lineAlpha = 0.0
    var caret = 1.0
    var typed = 0
    var textAlpha = 1.0
    var placeholder = 0.0
    var toPlaceholder = 0.0
    var rings = 0.0
    var titleGrid = 1.0
    var titleGridY = 0.0
    var titleField = 0.0
    var titleFieldY = 8.0
    var askAlpha = 1.0
    var sheen = 0.0
    var cardAlpha = 1.0
    var fieldAlpha = 1.0
    var reducedField = false
}

/** Plays the story, hands it to the finger, and returns to the loop 2 s after the last touch. */
internal class OggDriver {
    private enum class Phase { OPENING, HOLD, CLOSING, REST }

    private var epoch: Double? = null
    private var storyEpoch: Double? = null
    var manual = false
        private set
    private var phase = Phase.REST
    private var tm0 = 0.0
    private var phaseStart = 0.0
    private var chosen = OggModel.AUTO_CHOICE
    private var frozenTyped = 0
    private var lastTouch = -100.0
    private var shown = 0.0
    private var fadeFrom = 0.0
    private var fadeStart = -100.0

    private fun clock(now: Double): Double {
        val e = epoch ?: now.also { epoch = it }
        return (now - e) % OggModel.LOOP
    }

    private fun storyTime(now: Double): Double {
        val e = storyEpoch ?: now.also { storyEpoch = it }
        return (now - e) % OggModel.LOOP
    }

    private fun manualTime(now: Double, auto: Boolean): Double = when (phase) {
        Phase.OPENING -> {
            val t = min(tm0 + (now - phaseStart), 4.90)
            if (t >= 4.90) {
                phase = Phase.HOLD
                tm0 = 4.90
                phaseStart = now
                frozenTyped = 0
            }
            t
        }
        Phase.HOLD -> {
            if (auto && now - lastTouch > 2 && now - phaseStart > 2) {
                startClosing(now)
                7.30
            } else tm0
        }
        Phase.CLOSING -> {
            val t = min(tm0 + (now - phaseStart), 9.45)
            if (t >= 9.45) phase = Phase.REST
            t
        }
        Phase.REST -> 0.0
    }

    fun frame(now: Double, auto: Boolean, reduced: Boolean): OggFrame {
        val c = clock(now)
        if (reduced) return OggModel.reduced(shownAmount(now), chosen)
        if (manual) {
            val t = manualTime(now, auto)
            if (phase == Phase.REST && auto && now - lastTouch > 2) {
                manual = false
                storyEpoch = now
                return OggModel.frame(0.0, OggModel.AUTO_CHOICE, clock = c)
            }
            if (phase == Phase.REST) return OggModel.frame(0.0, chosen, clock = c)
            val blinkClock = if (phase == Phase.HOLD) now - phaseStart else null
            return OggModel.frame(t, chosen, frozenTyped, blinkClock, c)
        }
        if (!auto) return OggModel.frame(0.0, chosen, clock = c)
        return OggModel.frame(storyTime(now), OggModel.AUTO_CHOICE, clock = c)
    }

    private fun startClosing(now: Double) {
        phase = Phase.CLOSING
        tm0 = 7.30
        phaseStart = now
    }

    fun isRest(now: Double, auto: Boolean): Boolean {
        if (manual) return phase == Phase.REST
        if (!auto) return true
        val t = storyTime(now)
        return t < 1.30 || t >= 9.45
    }

    fun pick(i: Int, now: Double) {
        lastTouch = now
        manual = true
        chosen = i
        phase = Phase.OPENING
        tm0 = 1.30
        phaseStart = now
        frozenTyped = 0
    }

    fun takeOver(now: Double, auto: Boolean) {
        lastTouch = now
        if (manual || !auto) return
        val t = storyTime(now)
        manual = true
        chosen = OggModel.AUTO_CHOICE
        frozenTyped = OggModel.typed(t)
        phaseStart = now
        tm0 = t
        phase = when {
            t >= 1.30 && t < 4.90 -> Phase.OPENING
            t >= 4.90 && t < 7.30 -> Phase.HOLD
            t >= 7.30 && t < 9.45 -> Phase.CLOSING
            else -> Phase.REST
        }
    }

    fun close(now: Double): Boolean {
        lastTouch = now
        if (!manual || phase != Phase.HOLD) return false
        startClosing(now)
        return true
    }

    /** Whether frames must keep coming without autoPlay. */
    fun busy(now: Double): Boolean = manual || now - fadeStart < 0.4

    fun shownAmount(now: Double): Double {
        val k = OggModel.seg(now, fadeStart, fadeStart + 0.25)
        return fadeFrom + (shown - fadeFrom) * k
    }

    fun setReduced(open: Boolean, chosenCard: Int?, now: Double) {
        fadeFrom = shownAmount(now)
        shown = if (open) 1.0 else 0.0
        if (chosenCard != null) chosen = chosenCard
        fadeStart = now
    }

    val reducedOpen get() = shown > 0.5
}
