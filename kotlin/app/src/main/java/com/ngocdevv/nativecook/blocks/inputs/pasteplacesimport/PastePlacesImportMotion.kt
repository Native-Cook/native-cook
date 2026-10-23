package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Every value on screen is a pure function of the loop time `t` (0 <= t < LOOP); t = LOOP equals t = 0.
 * Mirrors react-native/src/blocks/inputs/paste-places-import/motion.ts value for value.
 */
internal object PastePlacesImportMotion {
    const val LOOP = 10.0
    const val FILLED_AT = 2.05
    const val FOUND_AT = 5.75
    const val RESET_AT = 7.32
    const val REST_AGAIN = 8.72
    const val PASTE_AT = 0.9
    const val START_AT = 2.4
    const val RESUME_AFTER = 2.0
    const val REDUCED_FADE = 0.1

    object Presses {
        val paste = Span(0.8, 0.9)
        val start = Span(2.2, 2.33)
        val close = Span(7.2, 7.32)
    }

    object Press {
        const val down = 0.1
        const val up = 0.14
        const val scale = 0.97
        const val closeScale = 0.86
        const val closeDim = 0.45
        const val inSeconds = 0.11
        const val outSeconds = 0.16
    }

    object Fan {
        val out = Span(0.9, 1.22)
        val back = Span(1.3, 1.8)
        val notes = IconFan(-8.0, -9.0, -3.0, 1.0)
        val doc = IconFan(8.0, 9.0, -2.0, 1.0)
        val chat = IconFan(0.0, 0.0, -6.0, 1.04)
    }

    object NoteIn {
        val placeholderOut = Span(0.92, 1.04)
        const val start = 0.98
        const val stagger = 0.07
        const val duration = 0.34
        const val rise = 8.0
    }

    val countIn = TimedStagger(1.05, 0.8, 0.08)
    val countOut = TimedStagger(7.72, 0.5, 0.05)
    val fillIn = Span(1.5, 1.95)
    val fillOut = Span(7.5, 7.9)

    object Scan {
        const val start = 2.4
        const val duration = 0.9
        const val fade = 0.1
    }

    object Detect {
        const val highlight = 0.22
        const val lift = 0.12
        const val wordDim = 0.5
        const val cityLine = 0.25
        val tipLine = Span(0.05, 0.42)
    }

    /** The chip hovers over its word: rise (pt) and grow, closed-form spring. */
    object Hover {
        const val rise = 8.0
        const val grow = 0.04
        const val pill = 0.14
        val spring = Spring(13.0, 0.72)
    }

    /** The fold: `close` folds the text area shut, `open` unfolds it with a spring that overshoots past flat. */
    object Fold {
        const val panels = 4
        const val maxDeg = 84.0
        const val perspective = 640.0
        val close = Span(3.5, 3.88)
        val open = Span(3.88, 4.6)
        val reclose = Span(7.72, 8.08)
        val reopen = Span(8.08, 8.72)
        const val settle = 0.16
        val spring = Spring(11.0, 0.55)
        const val shadeDown = 0.28
        const val shadeUp = 0.06
        const val dir = 1.0
        const val shadowY = 6.0
        const val shadowBlur = 10.0
        const val shadowOpacity = 0.16
        const val margin = 28.0
    }

    /** Chips fly from their words to the map (spring + arc), then drop the last `Drop.height` pt as pins. */
    object Fly {
        const val start = 4.02
        const val stagger = 0.09
        val spring = Spring(9.5, 0.78)
        const val arc = 34.0
        const val grow = 0.06
        const val tilt = 4.0
        const val dropAfter = 0.38
    }

    object Drop {
        const val height = 24.0
        const val fall = 0.17
        val bounces = doubleArrayOf(0.12, 0.03)
        const val squashX = 0.07
        const val squashY = 0.12
        const val squashDecay = 0.06
    }

    val routeDraw = Span(5.0, 5.7)
    val routeRetract = Span(7.32, 7.56)

    object Pop {
        const val start = 7.34
        const val stagger = 0.04
        const val dur = 0.2
        const val rise = 6.0
        const val shrink = 0.2
    }

    object Dot {
        const val inTime = 0.2
        const val shadowAir = 0.05
        const val shadowGround = 0.22
        const val shadowRest = 0.14
    }

    object Label {
        const val out = 0.12
        const val gap = 0.06
        const val `in` = 0.18
        const val shift = 6.0
        const val backAt = 7.36
    }

    const val COUNT_ROLL = 0.24
    val resetTipLine = Span(7.4, 7.6)

    val quiet = listOf(Span(0.0, 0.79), Span(5.72, 7.19), Span(8.74, LOOP))

    fun quietAt(time: Double): Int = quiet.indexOfFirst { time >= it.start && time <= it.end }

    fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    fun seg(time: Double, start: Double, end: Double): Double = clamp01((time - start) / (end - start))

    fun lerp(start: Double, end: Double, progress: Double): Double = start + (end - start) * progress

    private fun bezier(progress: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(progress)
        if (x == 0.0 || x == 1.0) return x
        var low = 0.0
        var high = 1.0
        repeat(18) {
            val middle = (low + high) / 2.0
            val inverse = 1.0 - middle
            val at = 3.0 * inverse * inverse * middle * x1 + 3.0 * inverse * middle * middle * x2 + middle * middle * middle
            if (at < x) low = middle else high = middle
        }
        val middle = (low + high) / 2.0
        val inverse = 1.0 - middle
        return 3.0 * inverse * inverse * middle * y1 + 3.0 * inverse * middle * middle * y2 + middle * middle * middle
    }

    fun easeOut(progress: Double): Double = bezier(progress, 0.23, 1.0, 0.32, 1.0)

    fun easeInOut(progress: Double): Double = bezier(progress, 0.77, 0.0, 0.175, 1.0)

    /** A damped spring from 0 to 1, `time` seconds after it starts (closed form, so ports match value for value). */
    fun spring(time: Double, spring: Spring): Double {
        if (time <= 0.0) return 0.0
        val damped = spring.omega * sqrt(1.0 - spring.zeta * spring.zeta)
        val envelope = exp(-spring.zeta * spring.omega * time)
        val value = 1.0 - envelope * (cos(damped * time) + (spring.zeta * spring.omega / damped) * sin(damped * time))
        return if (envelope < 1e-4) 1.0 else value
    }

    fun pressAt(time: Double, span: Span): Double {
        if (time < span.start || time > span.end + Press.up) return 0.0
        if (time < span.end) return easeOut(seg(time, span.start, span.start + Press.down))
        return 1.0 - easeOut(seg(time, span.end, span.end + Press.up))
    }

    fun fanAt(time: Double): Double = easeOut(seg(time, Fan.out.start, Fan.out.end)) - easeInOut(seg(time, Fan.back.start, Fan.back.end))

    /** Note line i: 0 hidden ... 1 in place. The note leaves folded inside the paper, so after the reset it is 0. */
    fun noteLineAt(time: Double, index: Int): Double {
        if (time >= RESET_AT) return 0.0
        val start = NoteIn.start + index * NoteIn.stagger
        return easeOut(seg(time, start, start + NoteIn.duration))
    }

    fun placeholderAt(time: Double): Double {
        if (time >= RESET_AT) return 1.0
        return 1.0 - seg(time, NoteIn.placeholderOut.start, NoteIn.placeholderOut.end)
    }

    fun countAt(time: Double): Double {
        if (time < countOut.start) return seg(time, countIn.start, countIn.start + countIn.duration)
        return 1.0 - seg(time, countOut.start, countOut.start + countOut.duration)
    }

    fun slotAt(master: Double, index: Int, count: Int, span: Double, stagger: Double): Double {
        val fromRight = count - 1 - index
        val duration = 1.0 - ((count - 1) * stagger) / span
        return easeOut(clamp01((master - (fromRight * stagger) / span) / duration))
    }

    fun fillAt(time: Double): Double {
        if (time < fillOut.start) return easeInOut(seg(time, fillIn.start, fillIn.end))
        return 1.0 - easeInOut(seg(time, fillOut.start, fillOut.end))
    }

    fun scanAt(time: Double): ScanFrame {
        val progress = seg(time, Scan.start, Scan.start + Scan.duration)
        val opacity = minOf(
            seg(time, Scan.start, Scan.start + Scan.fade),
            1.0 - seg(time, Scan.start + Scan.duration - Scan.fade, Scan.start + Scan.duration),
        )
        return ScanFrame(progress, opacity)
    }

    fun passAt(fraction: Double): Double = Scan.start + fraction * Scan.duration

    fun highlightAt(time: Double, pass: Double): Double = easeOut(seg(time, pass, pass + Detect.highlight))

    fun cityLineAt(time: Double, cityPass: Double): Double = easeOut(seg(time, cityPass, cityPass + Detect.cityLine))

    fun tipLineAt(time: Double, cityPass: Double): Double {
        if (time >= resetTipLine.start) return 1.0 - easeOut(seg(time, resetTipLine.start, resetTipLine.end))
        return easeOut(seg(time, cityPass + Detect.tipLine.start, cityPass + Detect.tipLine.end))
    }

    /** The place's word in the note: dims once its chip has lifted off. */
    fun wordAt(time: Double, pass: Double): Double {
        val lift = pass + Detect.lift
        return lerp(1.0, Detect.wordDim, seg(time, lift, lift + 0.12))
    }

    // ───────────── fold ─────────────

    private fun foldPhase(time: Double, close: Span, open: Span): Double {
        if (time < close.start || time >= open.end) return 0.0
        if (time < close.end) return Fold.maxDeg * easeInOut(seg(time, close.start, close.end))
        val s = spring(time - open.start, Fold.spring)
        return Fold.maxDeg * (1.0 - s) * (1.0 - seg(time, open.end - Fold.settle, open.end))
    }

    /** Fold angle in degrees (0 flat ... maxDeg shut; a little negative while the paper flexes after opening). */
    fun foldAt(time: Double): Double =
        if (time < 6.0) foldPhase(time, Fold.close, Fold.open) else foldPhase(time, Fold.reclose, Fold.reopen)

    /** The paper panels replace the text area from the first fold to the end of the reset. */
    fun panelsAt(time: Double): Boolean = time >= Fold.close.start && time < Fold.reopen.end

    /** The map is printed on the paper from the moment the first fold is shut until the reset fold is shut. */
    fun mapFaceAt(time: Double): Boolean = time >= Fold.close.end && time < Fold.reclose.end

    fun onFlatMap(time: Double): Boolean = panelsAt(time) && mapFaceAt(time) && abs(foldAt(time)) < 3.0

    /** Panel i under fold angle `deg`: translateY (pt, from its resting place), rotation (deg) and shade (0..1). */
    fun panelAt(index: Int, deg: Double, panelHeight: Double, perspective: Double): PanelFrame {
        val th = Math.toRadians(deg)
        val sn = sin(th) * Fold.dir
        val cs = cos(th)
        val near = perspective / (perspective - (panelHeight / 2.0) * sn)
        val far = perspective / (perspective + (panelHeight / 2.0) * sn)
        var centre = (panelHeight / 2.0) * cs * far
        for (j in 0 until index) centre += panelHeight * cs * (if (j % 2 == 0) near else far)
        val sign = if (index % 2 == 0) 1.0 else -1.0
        val facing = sign * sn
        val f2 = facing * facing
        return PanelFrame(centre - (index + 0.5) * panelHeight, sign * deg, if (facing > 0.0) f2 * Fold.shadeDown else f2 * Fold.shadeUp)
    }

    /**
     * The projective factor k of panel i's `panelMatrix`: content measured from the panel's centre (u, v) lands at
     * centre + (u, cos(th) v) / (1 - k v).
     */
    fun panelK(index: Int, deg: Double, perspective: Double): Double {
        val th = Math.toRadians(deg)
        return ((if (index % 2 == 0) 1.0 else -1.0) * sin(th) * Fold.dir) / perspective
    }

    // ───────────── chips to pins ─────────────

    fun hoverAt(time: Double, pass: Double): Double = spring(time - pass - Detect.lift, Hover.spring)

    fun chipPillAt(time: Double, pass: Double): Double {
        val lift = pass + Detect.lift
        return seg(time, lift, lift + Hover.pill)
    }

    private fun flyStart(index: Int): Double = Fly.start + index * Fly.stagger

    fun flyAt(time: Double, index: Int): Double = spring(time - flyStart(index), Fly.spring)

    /** Time chip i first touches the map. */
    fun impactAt(index: Int): Double = flyStart(index) + Fly.dropAfter + Drop.fall

    /** Drop of chip i: height h (1 = Drop.height above the map, 0 on it) and the squash impulse of the impacts. */
    fun dropAt(time: Double, index: Int): DropFrame {
        val tau = time - flyStart(index) - Fly.dropAfter
        if (tau <= 0.0) return DropFrame(1.0, 0.0)
        val fall = Drop.fall
        var h = 0.0
        var at = fall
        var imp = 0.0
        if (tau < fall) h = 1.0 - (tau / fall) * (tau / fall) else imp += exp(-(tau - fall) / Drop.squashDecay)
        for (peak in Drop.bounces) {
            val duration = 2.0 * fall * sqrt(peak)
            if (tau >= at && tau < at + duration) {
                val u = (tau - at) / duration
                h = 4.0 * peak * u * (1.0 - u)
            }
            at += duration
            if (tau >= at) imp += sqrt(peak) * exp(-(tau - at) / Drop.squashDecay)
        }
        return DropFrame(h, imp)
    }

    fun popAt(time: Double, index: Int): Double {
        val start = Pop.start + index * Pop.stagger
        return easeOut(seg(time, start, start + Pop.dur))
    }

    /** The dot on the map under chip i: 0..1. */
    fun dotAt(time: Double, index: Int): Double {
        if (time >= Pop.start) return 1.0 - popAt(time, index)
        val start = impactAt(index)
        return easeOut(seg(time, start, start + Dot.inTime))
    }

    /** Contact shadow under chip i: opacity (0 while it is high in the air). */
    fun contactAt(time: Double, index: Int): Double {
        val start = flyStart(index) + Fly.dropAfter
        if (time < start) return 0.0
        val h = dropAt(time, index).h
        val landed = seg(time, impactAt(index) + 0.3, impactAt(index) + 0.6)
        val value = lerp(Dot.shadowAir, Dot.shadowGround, 1.0 - h)
        return lerp(value, Dot.shadowRest, landed) * (1.0 - popAt(time, index))
    }

    /** N of "found N places" (continuous); each landing rolls it on. */
    fun foundAt(time: Double, places: Int): Double {
        if (time >= Label.backAt) return places.toDouble()
        var value = 0.0
        for (i in 0 until places) value += easeOut(seg(time, impactAt(i), impactAt(i) + COUNT_ROLL))
        return value
    }

    /** Label morph, sequential: (old label gone, new label in); rest (0, 0), found (1, 1). */
    fun labelAt(time: Double): LabelFrame {
        fun transition(start: Double): LabelFrame {
            val oldOut = easeOut(seg(time, start, start + Label.out))
            val newIn = easeOut(seg(time, start + Label.out + Label.gap, start + Label.out + Label.gap + Label.`in`))
            return LabelFrame(oldOut, newIn)
        }
        if (time >= Label.backAt) {
            val reverse = transition(Label.backAt)
            return LabelFrame(1.0 - reverse.newLabel, 1.0 - reverse.oldLabel)
        }
        return transition(impactAt(0))
    }

    fun routeAt(time: Double): Double {
        if (time < routeRetract.start) return easeInOut(seg(time, routeDraw.start, routeDraw.end))
        return 1.0 - easeInOut(seg(time, routeRetract.start, routeRetract.end))
    }

    data class Span(val start: Double, val end: Double)
    data class IconFan(val rotation: Double, val x: Double, val y: Double, val scale: Double)
    data class TimedStagger(val start: Double, val duration: Double, val stagger: Double)
    data class Spring(val omega: Double, val zeta: Double)
    data class ScanFrame(val progress: Double, val opacity: Double)
    data class LabelFrame(val oldLabel: Double, val newLabel: Double)
    data class PanelFrame(val ty: Double, val rot: Double, val shade: Double)
    data class DropFrame(val h: Double, val imp: Double)
}
