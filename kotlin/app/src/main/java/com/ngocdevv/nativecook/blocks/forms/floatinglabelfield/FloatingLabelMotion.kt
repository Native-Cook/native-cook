package com.ngocdevv.nativecook.blocks.forms.floatinglabelfield

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/*
 Floating Label Field: the script and the clock of the demo.

 Everything the loop shows is a pure function of the loop time `t` (seconds, 0 ..< LOOP): the
 three fields' poses, the button and the ghost finger. The same poses are produced from `Tween`s
 when a person types, so the field never knows which of the two is driving.
*/

const val LOOP = 11f
/** Seconds after the last touch before the screen eases back and the loop restarts. */
internal const val IDLE = 2f
/** How long the ease back to the empty form takes before the loop restarts. */
internal const val RETURN_LEN = 0.75f

internal const val NAME = "Mara Lindqvist"
internal const val EMAIL_TYPO = "mara@larchco"
internal const val EMAIL = "mara@larch.co"
internal const val PASSWORD = "Tamarack42#"

// ---- Timeline (seconds) ---------------------------------------------------------------------
/** A field takes the focus / gives it up. Email keeps it through its error and the fix. */
internal val FOCUS_AT = floatArrayOf(0.85f, 2.25f, 5.05f)
internal val BLUR_AT = floatArrayOf(2.25f, 5.05f, 8.35f)
private const val NAME_TYPE_AT = 1.05f
private const val EMAIL_TYPE_AT = 2.5f
private const val KEY_GAP = 0.075f
/** Return is pressed on the mistyped address: shake, red outline, helper line. */
private const val SUBMIT_AT = 3.55f
/** First backspace of the fix; the error clears with it. */
private const val FIX_AT = 4.35f
private const val PASSWORD_TYPE_AT = 5.3f
private const val PASSWORD_KEY_GAP = 0.15f
private const val READY_AT = 7.0f
private const val GLEAM_AT = 7.25f
private const val GLEAM_LEN = 0.8f
private const val TAP_AT = 8.35f
private const val DONE_AT = 8.5f
/** The form empties: values fade, then the labels sink back, 60 ms apart. */
private const val RESET_AT = 9.3f
private const val CLEAR_AT = 9.55f
private const val SINK_AT = 9.5f
/** Loop time of the filled form with nothing moving: what reduced motion shows. */
internal const val T_FILLED = 8.1f

// ---- Motion constants shared by the loop and by real input -----------------------------------
internal const val FOCUS_MS = 200f
internal const val FLOAT_MS = 420f
internal const val DRAW_MS = 600f
internal const val RETRACT_MS = 450f
internal const val CHECK_MS = 350f
internal const val ERROR_MS = 300f
internal const val ERROR_CLEAR_MS = 220f
internal const val SHAKE_MS = 450f
internal const val DOT_MS = 140f
internal const val STRENGTH_MS = 300f
internal const val READY_MS = 400f
internal const val GLEAM_MS = GLEAM_LEN * 1000f
/** Peak of the shake, dp, and its number of swings. */
private const val SHAKE_AMP = 9f
private const val SHAKE_SWINGS = 3

/** What one field shows. Snapshot state, so a draw lambda re-runs only for the values it reads. */
internal class FieldPose {
    var focus by mutableFloatStateOf(0f)
    var float by mutableFloatStateOf(0f)
    var lineStart by mutableFloatStateOf(0f)
    var lineEnd by mutableFloatStateOf(0f)
    var valid by mutableFloatStateOf(0f)
    var error by mutableFloatStateOf(0f)
    var shake by mutableFloatStateOf(0f)
    /** Index into the script's values while the loop types; -1 when a person does. */
    var frame by mutableIntStateOf(0)
    var count by mutableFloatStateOf(0f)
    var caret by mutableFloatStateOf(0f)
    var strength by mutableFloatStateOf(0f)
    var text by mutableFloatStateOf(1f)
}

internal class ButtonPose {
    var ready by mutableFloatStateOf(0f)
    var gleam by mutableFloatStateOf(0f)
    var press by mutableFloatStateOf(0f)
    var done by mutableFloatStateOf(0f)
}

internal class DotPose {
    /** Which target the ghost finger is over: 0-2 the fields, 3 the button. */
    var target by mutableIntStateOf(0)
    var alpha by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
}

// ---- Validation: the same rules for the script and for a person --------------------------------
private val NAME_RE = Regex("^\\s*\\S{2,}\\s+\\S{2,}")
private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

internal fun nameOk(v: String) = NAME_RE.containsMatchIn(v)

internal fun emailOk(v: String) = EMAIL_RE.containsMatchIn(v.trim())

/** 0 empty, 1 weak, 2 fair, 3 good, 4 strong. */
internal fun passwordLevel(v: String): Int {
    if (v.isEmpty()) return 0
    var points = 0
    if (v.length >= 6) points += 1
    if (v.length >= 10) points += 1
    if (v.any { it in 'a'..'z' } && v.any { it in 'A'..'Z' }) points += 1
    if (v.any { it in '0'..'9' }) points += 1
    if (v.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' }) points += 1
    return when {
        points <= 1 -> 1
        points == 2 -> 2
        points <= 4 -> 3
        else -> 4
    }
}

internal fun fieldOk(i: Int, v: String) = when (i) {
    0 -> nameOk(v)
    1 -> emailOk(v)
    else -> passwordLevel(v) >= 3
}

internal fun fieldMessage(i: Int, v: String): String = when (i) {
    0 -> if (v.trim().isEmpty()) "Enter your name" else "Add your first and last name"
    1 -> when {
        v.trim().isEmpty() -> "Enter your email"
        !v.contains('@') -> "An email address needs an @"
        else -> "That address needs a domain, like larch.co"
    }
    else -> if (v.isEmpty()) "Choose a password" else "Too easy to guess. Add a number or a symbol"
}

// ---- The script: every value a field holds, and when it starts to hold it -----------------------
private fun typed(text: String, at: Float, gap: Float): List<Pair<String, Float>> =
    (1..text.length).map { k -> text.substring(0, k) to at + (k - 1) * gap }

private val STEPS: List<List<Pair<String, Float>>> = listOf(
    typed(NAME, NAME_TYPE_AT, KEY_GAP),
    typed(EMAIL_TYPO, EMAIL_TYPE_AT, KEY_GAP) + listOf(
        "mara@larchc" to FIX_AT,
        "mara@larch" to FIX_AT + 0.12f,
        "mara@larch." to FIX_AT + 0.29f,
        "mara@larch.c" to FIX_AT + 0.39f,
        EMAIL to FIX_AT + 0.49f,
    ),
    typed(PASSWORD, PASSWORD_TYPE_AT, PASSWORD_KEY_GAP),
)

/** VALUES[field][frame] and the loop time that frame starts at (frame 0 is the empty field). */
internal val VALUES: List<List<String>> = STEPS.map { s -> listOf("") + s.map { it.first } }
internal val TIMES: List<FloatArray> = STEPS.map { s -> floatArrayOf(-1f) + s.map { it.second }.toFloatArray() }

/** When each field's value becomes valid for good. */
private val VALID_AT: FloatArray = FloatArray(3) { i ->
    val values = VALUES[i]
    var k = values.size - 1
    if (!fieldOk(i, values[k])) {
        1e9f
    } else {
        while (k > 0 && fieldOk(i, values[k - 1])) k -= 1
        TIMES[i][k]
    }
}

/** When the password reaches each strength level (index 0 unused). */
private val LEVEL_AT: FloatArray = FloatArray(5) { level ->
    val k = VALUES[2].indexOfFirst { passwordLevel(it) >= level }
    if (k < 0) 1e9f else TIMES[2][k]
}

// ---- Curves ------------------------------------------------------------------------------------
internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

internal fun easeOutCubic(x: Float): Float {
    val u = 1f - x
    return 1f - u * u * u
}

internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

internal fun easeOutBack(x: Float): Float {
    val c = 1.70158f
    val u = x - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}

internal val Linear: (Float) -> Float = { it }
internal val OutCubic: (Float) -> Float = ::easeOutCubic
internal val InOutCubic: (Float) -> Float = ::easeInOutCubic

/** Sideways offset of the shake, dp, from its progress: three swings that die out. */
internal fun shakeX(p: Float): Float {
    if (p <= 0f || p >= 1f) return 0f
    val u = 1f - p
    return SHAKE_AMP * sin(p * PI.toFloat() * 2f * SHAKE_SWINGS) * u * u
}

/** The script frame a field shows at loop time t. */
internal fun frameAt(i: Int, t: Float): Int {
    if (t >= CLEAR_AT) return 0
    val times = TIMES[i]
    var k = 0
    for (j in 1 until times.size) if (t >= times[j]) k = j
    return k
}

// ---- The loop ------------------------------------------------------------------------------------
internal fun autoField(i: Int, t: Float, out: FieldPose) {
    val f0 = FOCUS_AT[i]
    val b0 = BLUR_AT[i]
    val times = TIMES[i]
    val k = frameAt(i, t)
    val gone = 1f - seg(t, RESET_AT, RESET_AT + 0.25f)
    val sinkAt = SINK_AT + i * 0.06f
    val sink = 1f - easeInOutCubic(seg(t, sinkAt, sinkAt + FLOAT_MS / 1000f))

    var error = 0f
    var shake = 0f
    if (i == 1) {
        error = easeOutCubic(seg(t, SUBMIT_AT, SUBMIT_AT + ERROR_MS / 1000f)) *
            (1f - seg(t, FIX_AT, FIX_AT + ERROR_CLEAR_MS / 1000f))
        shake = seg(t, SUBMIT_AT, SUBMIT_AT + SHAKE_MS / 1000f)
    }

    var count = 0f
    var strength = 0f
    if (i == 2) {
        for (j in 1..k) count += easeOutCubic(seg(t, times[j], times[j] + DOT_MS / 1000f))
        for (level in 1..4) strength += easeOutCubic(seg(t, LEVEL_AT[level], LEVEL_AT[level] + STRENGTH_MS / 1000f))
        strength *= 1f - easeInOutCubic(seg(t, RESET_AT, RESET_AT + 0.3f))
    }

    // The caret holds while keys land and blinks once they stop.
    val since = t - max(times[k], f0)
    val blink = if (since < 0.5f) 1f else clamp01(0.5f + 1.6f * cos(2f * PI.toFloat() * (since - 0.5f) / 1.06f))
    val caret = seg(t, f0 + 0.22f, f0 + 0.34f) * (1f - seg(t, b0 - 0.04f, b0 + 0.04f)) * blink

    out.focus = easeOutCubic(seg(t, f0, f0 + FOCUS_MS / 1000f)) * (1f - seg(t, b0, b0 + FOCUS_MS / 1000f))
    out.float = easeOutCubic(seg(t, f0, f0 + FLOAT_MS / 1000f)) * sink
    out.lineStart = easeInOutCubic(seg(t, b0, b0 + RETRACT_MS / 1000f))
    out.lineEnd = easeInOutCubic(seg(t, f0, f0 + DRAW_MS / 1000f))
    out.valid = easeOutCubic(seg(t, VALID_AT[i], VALID_AT[i] + CHECK_MS / 1000f)) * gone
    out.error = error
    out.shake = shake
    out.frame = k
    out.count = count
    out.caret = caret
    out.strength = strength
    // Once the field is empty its (now blank) value is opaque again, so the loop ends as it starts.
    out.text = if (t >= CLEAR_AT) 1f else gone
}

internal fun autoButton(t: Float, out: ButtonPose) {
    out.ready = easeInOutCubic(seg(t, READY_AT, READY_AT + READY_MS / 1000f)) *
        (1f - easeInOutCubic(seg(t, RESET_AT + 0.15f, RESET_AT + 0.55f)))
    out.gleam = seg(t, GLEAM_AT, GLEAM_AT + GLEAM_LEN)
    out.press = seg(t, TAP_AT, TAP_AT + 0.1f) * (1f - seg(t, TAP_AT + 0.12f, TAP_AT + 0.3f))
    out.done = easeOutCubic(seg(t, DONE_AT, DONE_AT + 0.35f)) * (1f - seg(t, RESET_AT, RESET_AT + 0.3f))
}

private val TAPS = floatArrayOf(FOCUS_AT[0], FOCUS_AT[1], FOCUS_AT[2], TAP_AT)

/** The ghost finger: it fades in over a target, presses when the tap lands and fades out. */
internal fun autoDot(t: Float, out: DotPose) {
    var target = 0
    var alpha = 0f
    var scale = 1f
    for (k in TAPS.indices) {
        val at = TAPS[k]
        val a = seg(t, at - 0.24f, at - 0.06f) * (1f - seg(t, at + 0.12f, at + 0.36f))
        if (a > alpha) {
            alpha = a
            target = k
            scale = 1f - 0.22f * sin(PI.toFloat() * seg(t, at - 0.06f, at + 0.16f))
        }
    }
    out.target = target
    out.alpha = alpha
    out.scale = scale
}

// ---- Real input: one number that eases, stepped by the screen's single frame loop -----------------

/**
 Reads and writes are synchronous (a guard such as "is the outline drawn?" never sees a stale
 value), and a new target replaces the running one from wherever it is.
*/
internal class Tween(initial: Float = 0f) {
    var v by mutableFloatStateOf(initial)
        private set

    private var running = false
    private var blinking = false
    private var started = false
    private var from = initial
    private var target = initial
    private var duration = 0f
    private var wait = 0f
    private var elapsed = 0f
    private var ease: (Float) -> Float = Linear

    fun set(x: Float) {
        running = false
        blinking = false
        v = x
    }

    fun to(x: Float, ms: Float, curve: (Float) -> Float = Linear, delayMs: Float = 0f) {
        blinking = false
        if (ms <= 0f && delayMs <= 0f) {
            running = false
            v = x
            return
        }
        running = true
        started = false
        target = x
        duration = ms / 1000f
        wait = delayMs / 1000f
        elapsed = 0f
        ease = curve
    }

    fun stop() {
        running = false
        blinking = false
    }

    /** Holds 500 ms, fades out in 140, stays away 360, fades back in 140, for as long as it runs. */
    fun blink() {
        running = false
        blinking = true
        elapsed = 0f
        v = 1f
    }

    fun step(dt: Float) {
        if (blinking) {
            elapsed = (elapsed + dt) % 1.14f
            v = when {
                elapsed < 0.5f -> 1f
                elapsed < 0.64f -> 1f - (elapsed - 0.5f) / 0.14f
                elapsed < 1f -> 0f
                else -> (elapsed - 1f) / 0.14f
            }
            return
        }
        if (!running) return
        var left = dt
        if (wait > 0f) {
            wait -= left
            if (wait > 0f) return
            left = -wait
            wait = 0f
        }
        if (!started) {
            started = true
            from = v
        }
        elapsed += left
        if (duration <= 0f || elapsed >= duration) {
            v = target
            running = false
            return
        }
        v = from + (target - from) * ease(elapsed / duration)
    }
}
