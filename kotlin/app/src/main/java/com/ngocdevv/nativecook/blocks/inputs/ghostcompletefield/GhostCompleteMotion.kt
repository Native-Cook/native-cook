package com.ngocdevv.nativecook.blocks.inputs.ghostcompletefield

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Ghost Complete Field: the script and the clock of the demo.

 The loop is a pure function of the loop time `t` (seconds, 0 ..< LOOP). It produces the same
 `GhostPose` a person's typing produces through `GhostController`, so the field never knows which
 of the two is driving. Positions are glyph counts and caret stops, never points: the field turns
 them into geometry with its own layout.
*/

const val LOOP = 10f
/** Seconds after the last touch before the screen eases back and the loop restarts. */
internal const val IDLE = 2f
internal const val RETURN_LEN = 0.6f

// ---- The script -----------------------------------------------------------------------------------
internal const val CC_TYPED = "theo@"
internal const val CC_DOMAIN = "postbox.co"
internal const val CC_FULL = CC_TYPED + CC_DOMAIN
internal val CC_CHIPS = listOf("postbox.co", "northmail.com")
internal const val BODY_TYPED_1 = "Hi Mara, tha"
internal const val BODY_GHOST_1 = "nks for the notes — "
internal const val BODY_TYPED_2 = "can we "
internal const val BODY_GHOST_2 = "walk through them Thursday?"
internal const val BODY_FULL = BODY_TYPED_1 + BODY_GHOST_1 + BODY_TYPED_2 + BODY_GHOST_2
/** Glyph counts at the four seams of the body. */
private val B1 = BODY_TYPED_1.length
private val B2 = B1 + BODY_GHOST_1.length
private val B3 = B2 + BODY_TYPED_2.length
private val B4 = BODY_FULL.length
internal val BODY_GHOSTS = listOf(B1 to B2, B3 to B4)
internal val CC_GHOSTS = listOf(CC_TYPED.length to CC_FULL.length)

// ---- Timeline (seconds) ------------------------------------------------------------------------------
private const val KEY_GAP = 0.07f
private const val CC_TAP = 0.55f
private const val CC_TYPE = 0.75f
private const val CHIPS_AT = 1.2f
private const val PICK_AT = 1.85f
private const val PICK_LEN = 0.55f
private const val BODY_TAP = 2.55f
private const val TYPE_1 = 2.75f
private val TYPE_1_END = TYPE_1 + (B1 - 1) * KEY_GAP
/** The suggestion arrives a short pause after the last key, as it does for a person. */
private val GHOST_1 = TYPE_1_END + 0.26f
private val KEYCAP_1 = GHOST_1 + 0.38f
private const val KEY_TAP = 4.8f
private const val ACCEPT_1 = 4.85f
private const val TYPE_2 = 5.55f
private val TYPE_2_END = TYPE_2 + (B3 - B2 - 1) * KEY_GAP
private val GHOST_2 = TYPE_2_END + 0.26f
private val KEYCAP_2 = GHOST_2 + 0.36f
private const val SWIPE_IN = 7.05f
private const val SWIPE_AT = 7.25f
private const val RELEASE = 7.75f
private const val CLEAR_AT = 8.5f
private const val CLEAR_LEN = 0.35f
/** Loop time of the finished message with nothing moving: what reduced motion shows. */
internal const val T_FILLED = 8.2f

// ---- Motion constants shared by the loop and by real input ----------------------------------------------
/** A suggestion's words arrive this far apart, each over WORD_MS from a blur of WORD_BLUR. */
internal const val WORD_GAP = 0.07f
internal const val WORD_MS = 280f
internal const val WORD_BLUR = 5f
/** A rejected suggestion dissolves: it blurs out and fades over this long. */
internal const val DISSOLVE_MS = 200f
internal const val DISSOLVE_BLUR = 4f
/** The ink sweep over an accepted suggestion, and the width of its coloured front, dp. */
internal const val SWEEP_MS = 550f
internal const val SWEEP_BAND = 48f
/** The caret's jump to the end of the accepted text; it overshoots by about 3 % (ease-out-back). */
internal const val JUMP_MS = 320f
internal const val JUMP_BACK = 0.9f
internal const val KEYCAP_MS = 280f
/** Pause after the last key before a suggestion is offered. */
internal const val DEBOUNCE_MS = 240f
/** How far a swipe travels to fill the whole suggestion, dp, and the share that accepts on release. */
internal const val SWIPE_DIST = 180f
internal const val SWIPE_ACCEPT = 0.45f
/** The demo finger's swipe, dp, and the share of the sweep it reaches before letting go. */
internal const val DEMO_SWIPE = 150f
private const val DEMO_SWIPE_SHARE = 0.8f
/** A domain chip stretching into the field. */
internal const val PICK_MS = PICK_LEN * 1000f
internal const val CHIP_GAP = 0.07f
internal const val CHIP_MS = 260f

/** What a field shows. Snapshot state, so a draw lambda re-runs only for the values it reads. */
internal class GhostPose {
    /** Glyphs in ink: what has been typed (or accepted). */
    var n by mutableIntStateOf(0)
    /** Which suggestion of the layout shows, -1 for none. */
    var run by mutableIntStateOf(-1)
    /** Seconds since that suggestion began to arrive. */
    var appear by mutableFloatStateOf(0f)
    /** 1, or falling to 0 while a rejected suggestion dissolves. */
    var fade by mutableFloatStateOf(1f)
    /** 0 to 1: the ink front crossing the suggestion. */
    var sweep by mutableFloatStateOf(0f)
    /** The caret stands between two stops of the layout; `caretMix` may overshoot 1. */
    var caretFrom by mutableIntStateOf(0)
    var caretTo by mutableIntStateOf(0)
    var caretMix by mutableFloatStateOf(1f)
    /** Caret opacity (focus and blink). */
    var caret by mutableFloatStateOf(0f)
    /** 0 to 1: the keycap chip has popped. */
    var keycap by mutableFloatStateOf(0f)
    var keycapPress by mutableFloatStateOf(0f)
    /** Opacity of the text (the form emptying) and of the placeholder. */
    var ink by mutableFloatStateOf(1f)
    var hint by mutableFloatStateOf(1f)
}

internal class ChipsPose {
    /** Seconds since the chips began to pop; 0 or less hides them. */
    var show by mutableFloatStateOf(0f)
    /** The chip that was picked, -1 for none. */
    var pick by mutableIntStateOf(-1)
    /** 0 to 1: that chip stretching into the field while the others leave. */
    var take by mutableFloatStateOf(0f)
}

internal class DotPose {
    /** 0 Cc row, 1 first domain chip, 2 body, 3 keycap, 4 the swipe over the suggestion. */
    var target by mutableIntStateOf(0)
    var alpha by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    /** 0 to 1 along the swipe. */
    var travel by mutableFloatStateOf(0f)
}

// ---- Curves ----------------------------------------------------------------------------------------------
internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

internal fun easeOutCubic(x: Float): Float {
    val u = 1f - x
    return 1f - u * u * u
}

internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

internal fun easeOutBack(x: Float, c: Float): Float {
    val u = x - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}

/** Keys typed by loop time t: one every `gap` from `at`, `max` in all. */
private fun keys(t: Float, at: Float, gap: Float, max: Int): Int {
    if (t < at) return 0
    return min(max, floor((t - at) / gap + 1e-4f).toInt() + 1)
}

/** The caret holds for half a second after anything happens, then blinks. */
private fun blink(since: Float): Float =
    if (since < 0.5f) 1f else clamp01(0.5f + 1.6f * cos(2f * PI.toFloat() * (since - 0.5f) / 1.06f))

// ---- The loop -----------------------------------------------------------------------------------------------

/** The text fades as the reply clears; once it is gone (and empty) it is opaque again for the next loop. */
private fun inkAt(t: Float) = if (t >= CLEAR_AT + CLEAR_LEN) 1f else 1f - seg(t, CLEAR_AT, CLEAR_AT + CLEAR_LEN)

/** Glyphs of the body in ink at loop time t. */
internal fun bodyCount(t: Float): Int {
    if (t >= CLEAR_AT + CLEAR_LEN) return 0
    if (t >= RELEASE + 0.2f) return B4
    if (t >= ACCEPT_1 + SWEEP_MS / 1000f) return B2 + keys(t, TYPE_2, KEY_GAP, B3 - B2)
    return keys(t, TYPE_1, KEY_GAP, B1)
}

/** Glyphs of the Cc address in ink at loop time t. */
internal fun ccCount(t: Float): Int {
    if (t >= CLEAR_AT + CLEAR_LEN) return 0
    if (t >= PICK_AT + PICK_LEN) return CC_FULL.length
    return keys(t, CC_TYPE, KEY_GAP, CC_TYPED.length)
}

internal fun autoBody(t: Float, out: GhostPose) {
    val n = bodyCount(t)
    val accept1End = ACCEPT_1 + SWEEP_MS / 1000f
    val accept2End = RELEASE + 0.2f
    var run = -1
    var appear = 0f
    var sweep = 0f
    var keycap = 0f
    var keycapPress = 0f
    if (t >= GHOST_1 && t < accept1End) {
        run = 0
        appear = t - GHOST_1
        sweep = easeInOutCubic(seg(t, ACCEPT_1, accept1End))
        keycap = easeOutBack(seg(t, KEYCAP_1, KEYCAP_1 + KEYCAP_MS / 1000f), 1.6f) * (1f - seg(t, ACCEPT_1, ACCEPT_1 + 0.14f))
        keycapPress = sin(PI.toFloat() * seg(t, KEY_TAP - 0.04f, KEY_TAP + 0.14f))
    } else if (t >= GHOST_2 && t < accept2End) {
        run = 1
        appear = t - GHOST_2
        sweep = DEMO_SWIPE_SHARE * easeInOutCubic(seg(t, SWIPE_AT, RELEASE)) +
            (1f - DEMO_SWIPE_SHARE) * easeOutCubic(seg(t, RELEASE, accept2End))
        keycap = easeOutBack(seg(t, KEYCAP_2, KEYCAP_2 + KEYCAP_MS / 1000f), 1.6f) * (1f - seg(t, RELEASE, RELEASE + 0.14f))
    }

    // The caret follows the typing, and jumps ahead of the ink when a suggestion is accepted.
    var caretFrom = n
    var caretTo = n
    var caretMix = 1f
    if (t >= ACCEPT_1 && t < accept1End) {
        caretFrom = B1
        caretTo = B2
        caretMix = easeOutBack(seg(t, ACCEPT_1, ACCEPT_1 + JUMP_MS / 1000f), JUMP_BACK)
    } else if (t >= RELEASE && t < CLEAR_AT + CLEAR_LEN) {
        caretFrom = B3
        caretTo = B4
        caretMix = easeOutBack(seg(t, RELEASE, RELEASE + JUMP_MS / 1000f), JUMP_BACK)
    }

    var last = BODY_TAP
    if (t >= TYPE_1) last = min(t, TYPE_1_END)
    if (t >= ACCEPT_1) last = min(t, ACCEPT_1 + JUMP_MS / 1000f)
    if (t >= TYPE_2) last = min(t, TYPE_2_END)
    if (t >= SWIPE_AT) last = min(t, RELEASE + JUMP_MS / 1000f)

    out.n = n
    out.run = run
    out.appear = appear
    out.fade = 1f
    out.sweep = sweep
    out.caretFrom = caretFrom
    out.caretTo = caretTo
    out.caretMix = caretMix
    out.caret = seg(t, BODY_TAP, BODY_TAP + 0.12f) * (1f - seg(t, CLEAR_AT - 0.1f, CLEAR_AT)) * blink(t - last)
    out.keycap = keycap
    out.keycapPress = keycapPress
    out.ink = inkAt(t)
    out.hint = if (t < TYPE_1) 1f else seg(t, CLEAR_AT + CLEAR_LEN, CLEAR_AT + CLEAR_LEN + 0.3f)
}

internal fun autoCc(t: Float, out: GhostPose) {
    val n = ccCount(t)
    val pickEnd = PICK_AT + PICK_LEN
    val picking = t >= PICK_AT && t < pickEnd
    var caretFrom = n
    var caretTo = n
    var caretMix = 1f
    if (picking) {
        caretFrom = CC_TYPED.length
        caretTo = CC_FULL.length
        caretMix = easeOutBack(seg(t, PICK_AT + 0.08f, PICK_AT + 0.08f + JUMP_MS / 1000f), JUMP_BACK)
    }
    var last = CC_TAP
    if (t >= CC_TYPE) last = min(t, CC_TYPE + (CC_TYPED.length - 1) * KEY_GAP)
    if (t >= PICK_AT) last = t

    out.n = n
    out.run = if (picking) 0 else -1
    out.appear = 9f
    out.fade = 1f
    out.sweep = easeInOutCubic(seg(t, PICK_AT + 0.08f, pickEnd))
    out.caretFrom = caretFrom
    out.caretTo = caretTo
    out.caretMix = caretMix
    out.caret = seg(t, CC_TAP, CC_TAP + 0.12f) * (1f - seg(t, BODY_TAP - 0.1f, BODY_TAP)) * blink(t - last)
    out.keycap = 0f
    out.keycapPress = 0f
    out.ink = inkAt(t)
    out.hint = if (t < CC_TYPE) 1f else seg(t, CLEAR_AT + CLEAR_LEN, CLEAR_AT + CLEAR_LEN + 0.3f)
}

internal fun autoChips(t: Float, out: ChipsPose) {
    if (t < CHIPS_AT || t >= PICK_AT + PICK_LEN) {
        out.show = 0f
        out.pick = -1
        out.take = 0f
        return
    }
    out.show = t - CHIPS_AT
    out.pick = if (t >= PICK_AT) 0 else -1
    out.take = seg(t, PICK_AT, PICK_AT + PICK_LEN)
}

internal fun autoSend(t: Float): Float =
    easeOutCubic(seg(t, TYPE_1, TYPE_1 + 0.3f)) * (1f - seg(t, CLEAR_AT, CLEAR_AT + CLEAR_LEN))

private val TAPS = floatArrayOf(CC_TAP, PICK_AT, BODY_TAP, KEY_TAP)

/** The ghost finger: four taps, then a swipe to the right over the second suggestion. */
internal fun autoDot(t: Float, out: DotPose) {
    if (t >= SWIPE_IN - 0.02f) {
        out.target = 4
        out.alpha = seg(t, SWIPE_IN, SWIPE_IN + 0.16f) * (1f - seg(t, RELEASE, RELEASE + 0.2f))
        out.scale = 1f - 0.16f * seg(t, SWIPE_AT - 0.08f, SWIPE_AT) * (1f - seg(t, RELEASE - 0.02f, RELEASE + 0.1f))
        out.travel = easeInOutCubic(seg(t, SWIPE_AT, RELEASE))
        return
    }
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
    out.travel = 0f
}

// ---- Real input: one number that eases, stepped by the screen's single frame loop -----------------

internal val Linear: (Float) -> Float = { it }
internal val OutCubic: (Float) -> Float = ::easeOutCubic
internal val InOutCubic: (Float) -> Float = ::easeInOutCubic
internal fun outBack(c: Float): (Float) -> Float = { easeOutBack(it, c) }

/**
 Reads and writes are synchronous, and a new target replaces the running one from wherever it is.
*/
internal class Tween(initial: Float = 0f) {
    var v by mutableFloatStateOf(initial)
        private set

    private enum class Kind { Idle, Timing, Blink, Spring }

    private var kind = Kind.Idle
    private var started = false
    private var from = initial
    private var target = initial
    private var duration = 0f
    private var wait = 0f
    private var elapsed = 0f
    private var velocity = 0f
    private var damping = 0f
    private var stiffness = 0f
    private var ease: (Float) -> Float = Linear

    fun set(x: Float) {
        kind = Kind.Idle
        v = x
    }

    fun to(x: Float, ms: Float, curve: (Float) -> Float = Linear, delayMs: Float = 0f) {
        if (ms <= 0f && delayMs <= 0f) {
            set(x)
            return
        }
        kind = Kind.Timing
        started = false
        target = x
        duration = ms / 1000f
        wait = delayMs / 1000f
        elapsed = 0f
        ease = curve
    }

    fun spring(x: Float, startVelocity: Float, springDamping: Float, springStiffness: Float) {
        kind = Kind.Spring
        target = x
        velocity = startVelocity
        damping = springDamping
        stiffness = springStiffness
    }

    /** Holds 500 ms, fades out in 140, stays away 360, fades back in 140, for as long as it runs. */
    fun blink() {
        kind = Kind.Blink
        elapsed = 0f
        v = 1f
    }

    fun step(dt: Float) {
        when (kind) {
            Kind.Idle -> Unit
            Kind.Blink -> {
                elapsed = (elapsed + dt) % 1.14f
                v = when {
                    elapsed < 0.5f -> 1f
                    elapsed < 0.64f -> 1f - (elapsed - 0.5f) / 0.14f
                    elapsed < 1f -> 0f
                    else -> (elapsed - 1f) / 0.14f
                }
            }
            Kind.Spring -> {
                var x = v
                var left = dt
                while (left > 0f) {
                    val h = min(left, 1f / 240f)
                    velocity += (-stiffness * (x - target) - damping * velocity) * h
                    x += velocity * h
                    left -= h
                }
                if (kotlin.math.abs(x - target) < 0.001f && kotlin.math.abs(velocity) < 0.01f) {
                    set(target)
                } else {
                    v = x
                }
            }
            Kind.Timing -> {
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
                    set(target)
                } else {
                    v = from + (target - from) * ease(elapsed / duration)
                }
            }
        }
    }
}
