// Split Amount Pad — the loop, the springs and the amount's rules, as named numbers and pure functions
// (sap-motion.ts and sap-theme.ts of the React Native block), plus the two small animators the screen
// runs on: a damped spring with Reanimated's stiffness/damping/mass and a timed tween.
package com.ngocdevv.nativecook.blocks.inputs.splitamountpad

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** the loop's period, seconds */
const val LOOP = 9.0

/** key codes: 0…9 the digits, then */
internal const val KEY_DOT = 10
internal const val KEY_BACK = 11
internal const val KEY_CLEAR = 12

/** most a bill may be, in cents */
internal const val LIMIT_CENTS = 250000
internal const val MAX_INT_DIGITS = 4
internal const val MAX_DECIMALS = 2
internal const val FRIENDS = 4

internal const val DO_KEY = 0
internal const val DO_FRIEND = 1
/** fired by the long press the row before it began: no finger move of its own */
internal const val DO_CLEAR = 2

/** one script row: time (s), kind, argument, the time the ghost finger lifts */
internal class Cue(val at: Double, val kind: Int, val arg: Int, val lift: Double)

internal val SCRIPT = arrayOf(
    Cue(0.7, DO_KEY, 1, 0.7),
    Cue(1.02, DO_KEY, 2, 1.02),
    Cue(1.34, DO_KEY, 8, 1.34),
    Cue(1.72, DO_KEY, KEY_DOT, 1.72),
    Cue(2.04, DO_KEY, 4, 2.04),
    Cue(2.36, DO_KEY, 0, 2.36),
    Cue(4.1, DO_FRIEND, 2, 4.1),
    Cue(5.9, DO_KEY, KEY_BACK, 6.32),
    Cue(6.32, DO_CLEAR, 0, 6.32),
    Cue(6.95, DO_FRIEND, 2, 6.95),
)

/** a touch hands the pad back this long after it ends; then the amount clears and the loop restarts */
internal const val IDLE = 2.0
internal const val RETURN = 0.7
/** a held backspace clears the amount after this long */
internal const val HOLD_MS = 420L

/** a spring as Reanimated takes it: stiffness, damping, mass 1 */
internal class SpringSpec(val stiffness: Float, val damping: Float)

/** a digit dropping in: response 0.39 s, damping ratio 0.53 */
internal val SPRING_DROP = SpringSpec(260f, 17f)
/** the amount re-centring: response 0.34 s, damping ratio 0.81 */
internal val SPRING_SLIDE = SpringSpec(340f, 30f)
/** a share's digit rolling: response 0.42 s, damping ratio 0.81 */
internal val SPRING_ROLL = SpringSpec(220f, 24f)
/** an avatar toggling */
internal val SPRING_TOGGLE = SpringSpec(300f, 20f)

/** timings, ms */
internal const val T_KEY_DOWN = 70f
internal const val T_KEY_UP = 170f
internal const val T_FLICK = 230f
internal const val T_FLICK_STAGGER = 28f
internal const val T_DROP_STAGGER = 45f
internal const val T_ROLL_FRIEND = 45f
internal const val T_ROLL_PLACE = 18f
internal const val T_SHAKE = 55f

/** a dropping digit starts this far above its place, this small and turned up to this much */
internal const val DROP_RISE = -24f
internal const val DROP_SCALE = 0.6f
internal const val DROP_TURN_MIN = 6
internal const val DROP_TURN_SPAN = 7
/** a flicked digit ends this far to the right, having risen and fallen, turned and shrunk */
internal const val FLICK_X = 26f
internal const val FLICK_UP = -34f
internal const val FLICK_FALL = 60f
internal const val FLICK_TURN = 38f
internal const val FLICK_SHRINK = 0.35f
/** the shake's legs, dp: over the limit, and a softer "no" for a key that does nothing */
internal val SHAKE_HARD = floatArrayOf(-11f, 9f, -6f, 3f, 0f)
internal val SHAKE_SOFT = floatArrayOf(-5f, 4f, -2f, 0f)

/* ------------------------------------------------------------------ theme and layout numbers */

internal val KEY_LABELS = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "")
/** the code each pad cell sends (10 the dot, 11 backspace) */
internal val KEY_CODES = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 11)

internal const val SIDE = 20f
/** the small rolling share: cell widths at 15 dp semibold digits */
internal const val SHARE_SIZE = 15f
internal const val SHARE_LINE = 20f
internal const val SHARE_DIGIT = 9.6f
internal const val SHARE_MARK = 4.4f
internal const val DOLLAR_SCALE = 0.56f
internal const val DOLLAR_GAP = 4f
/** digit slots kept alive: seven can be typed, the rest are still flying away */
internal const val POOL = 12

/** the numbers that change with the screen: a phone under 740 dp tall gets the compact set */
internal class SapLayout(width: Float, height: Float) {
    val compact = height < 740f
    val gap = if (compact) 8f else 10f
    val keyH = if (compact) 54f else 64f
    val keyW = (width - SIDE * 2 - gap * 2) / 3f
    /** the amount's font size and line height */
    val amount = if (compact) 54f else 64f
    val line = (amount * 1.14f).roundToInt().toFloat()
    val disc = if (compact) 46f else 52f
    val cta = if (compact) 48f else 54f
    val block = if (compact) 10f else 16f
    val column = (width - SIDE * 2) / 4f
}

/** the amount's glyph widths, dp */
internal class Metrics(val digit: Float, val dot: Float, val comma: Float, val dollar: Float)

/** where everything sits for some typed codes, x relative to the row's middle */
internal class AmountLayout(val dollar: Float, val comma: Float, val hasComma: Boolean, val centres: FloatArray)

internal fun amountLayout(codes: IntArray, m: Metrics): AmountLayout {
    var whole = codes.size
    for (i in codes.indices) {
        if (codes[i] == KEY_DOT) {
            whole = i
            break
        }
    }
    val hasComma = whole >= 4
    var total = m.dollar + DOLLAR_GAP + (if (hasComma) m.comma else 0f)
    // an empty amount shows a placeholder zero
    if (codes.isEmpty()) total += m.digit
    for (c in codes) total += if (c == KEY_DOT) m.dot else m.digit
    val centres = FloatArray(codes.size)
    var comma = 0f
    var x = -total / 2f + m.dollar + DOLLAR_GAP
    for (i in codes.indices) {
        if (hasComma && i == whole - 3) {
            comma = x + m.comma / 2f
            x += m.comma
        }
        val w = if (codes[i] == KEY_DOT) m.dot else m.digit
        centres[i] = x + w / 2f
        x += w
    }
    return AmountLayout(-total / 2f, comma, hasComma, centres)
}

/* ------------------------------------------------------------------ pure functions */

internal fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01(((t - a) / (b - a)).toFloat())

internal fun smooth(v: Float) = v * v * (3f - 2f * v)

/** where the dot is in the typed codes, or -1 */
internal fun dotAt(codes: IntArray): Int = codes.indexOf(KEY_DOT)

/** the typed codes as cents */
internal fun centsOf(codes: IntArray): Int {
    val dot = dotAt(codes)
    val whole = if (dot < 0) codes.size else dot
    var cents = 0
    for (i in 0 until whole) cents = cents * 10 + codes[i]
    cents *= 100
    if (dot >= 0) {
        if (dot + 1 < codes.size) cents += codes[dot + 1] * 10
        if (dot + 2 < codes.size) cents += codes[dot + 2]
    }
    return cents
}

/** split cents between the friends that are in: the first ones take the odd cents; -1 for one who sits out */
internal fun splitShares(cents: Int, active: IntArray): IntArray {
    val count = active.count { it > 0 }
    val base = if (count > 0) cents / count else 0
    var odd = if (count > 0) cents - base * count else 0
    return IntArray(active.size) { i ->
        if (active[i] > 0) {
            val share = base + (if (odd > 0) 1 else 0)
            if (odd > 0) odd -= 1
            share
        } else {
            -1
        }
    }
}

/** which pad cell a key code sits in (rows of three: 1 2 3 / 4 5 6 / 7 8 9 / . 0 back) */
internal fun cellOf(code: Int) = when (code) {
    0 -> 10
    KEY_DOT -> 9
    KEY_BACK -> 11
    else -> code - 1
}

/** which script row the ghost finger is heading for or pressing at t (DO_CLEAR rows are skipped) */
internal fun ghostRow(t: Double): Int {
    var last = 0
    for (i in SCRIPT.indices) {
        if (SCRIPT[i].kind == DO_CLEAR) continue
        last = i
        if (t < SCRIPT[i].lift + 0.3) return i
    }
    return last
}

/** the row before it that has a finger move, or itself */
internal fun ghostPrev(row: Int): Int {
    for (i in row - 1 downTo 0) if (SCRIPT[i].kind != DO_CLEAR) return i
    return row
}

/** how far the ghost has glided from the previous row's target to this one's, 0…1 */
internal fun ghostGlide(t: Double, row: Int): Float {
    val at = SCRIPT[row].at
    return smooth(seg(t, at - 0.27, at - 0.05))
}

internal fun ghostAlpha(t: Double): Float {
    var a = 0f
    for (cue in SCRIPT) {
        if (cue.kind == DO_CLEAR) continue
        val v = smooth(seg(t, cue.at - 0.32, cue.at - 0.12)) * (1f - smooth(seg(t, cue.lift + 0.12, cue.lift + 0.36)))
        if (v > a) a = v
    }
    return a
}

/** how hard the ghost presses a row at t, 0…1 */
internal fun ghostPress(t: Double, row: Int): Float {
    val cue = SCRIPT[row]
    return smooth(seg(t, cue.at - 0.07, cue.at)) * (1f - smooth(seg(t, cue.lift + 0.03, cue.lift + 0.19)))
}

/* ------------------------------------------------------------------ animators */

/** Reanimated's default timing curve, Easing.inOut(Easing.quad) */
internal val EaseInOutQuad: (Float) -> Float = { v -> if (v < 0.5f) 2f * v * v else 1f - 2f * (1f - v) * (1f - v) }
internal val EaseOutQuad: (Float) -> Float = { v -> 1f - (1f - v) * (1f - v) }

/** one timed leg: wait, then go to a value over a duration (ms) */
internal class Leg(val to: Float, val ms: Float, val delayMs: Float = 0f, val ease: (Float) -> Float = EaseInOutQuad)

/**
 * One animated number, stepped by the screen's clock. It is either at rest, on a spring (which keeps
 * its velocity when it is given a new target, as Reanimated's does) or running a queue of timed legs.
 */
internal class Anim(initial: Float) {
    var value = initial
        private set
    private var velocity = 0f
    private var mode = REST

    private var target = initial
    private var stiffness = 0f
    private var damping = 0f
    private var wait = 0f

    private var legs: Array<out Leg> = emptyArray()
    private var leg = 0
    private var from = initial
    private var elapsed = 0f

    val resting get() = mode == REST

    fun snap(v: Float) {
        value = v
        velocity = 0f
        mode = REST
    }

    fun spring(to: Float, spec: SpringSpec, delayMs: Float = 0f) {
        target = to
        stiffness = spec.stiffness
        damping = spec.damping
        wait = delayMs / 1000f
        mode = SPRING
    }

    fun tween(vararg sequence: Leg) {
        legs = sequence
        leg = 0
        from = value
        elapsed = 0f
        velocity = 0f
        mode = TWEEN
    }

    fun tween(to: Float, ms: Float, delayMs: Float = 0f, ease: (Float) -> Float = EaseInOutQuad) = tween(Leg(to, ms, delayMs, ease))

    fun step(dt: Float) {
        when (mode) {
            SPRING -> {
                var left = dt
                if (wait > 0f) {
                    val used = min(wait, left)
                    wait -= used
                    left -= used
                    if (wait > 0f) return
                }
                while (left > 0f) {
                    val h = min(left, 0.002f)
                    velocity += (-stiffness * (value - target) - damping * velocity) * h
                    value += velocity * h
                    left -= h
                }
                if (abs(value - target) < 0.0005f && abs(velocity) < 0.005f) {
                    value = target
                    velocity = 0f
                    mode = REST
                }
            }
            TWEEN -> {
                var left = dt
                while (leg < legs.size) {
                    val now = legs[leg]
                    val delay = now.delayMs / 1000f
                    val length = now.ms / 1000f
                    elapsed += left
                    if (elapsed < delay) return
                    val p = if (length <= 0f) 1f else (elapsed - delay) / length
                    if (p < 1f) {
                        value = from + (now.to - from) * now.ease(p)
                        return
                    }
                    value = now.to
                    left = elapsed - delay - length
                    from = value
                    elapsed = 0f
                    leg += 1
                }
                mode = REST
            }
        }
    }

    private companion object {
        const val REST = 0
        const val SPRING = 1
        const val TWEEN = 2
    }
}

internal fun floorInt(v: Float) = floor(v).toInt()
