package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import androidx.compose.animation.core.CubicBezierEasing

// Page Curl Journal: every motion value.

/** The index of the cover, the last page. */
internal const val LAST = 3

/** QA holds freeze one state for the fidelity screenshots (null in the shipped block). */
internal enum class QaHold { Rest, A, B, P1, P2 }

internal val QA_HOLD: QaHold? = null

// Slow-motion review: the autoplay clock, every spring and every timing run at QA_SPEED (1 in the shipped
// block), and QA_START starts the loop at that time.
internal const val QA_SPEED = 1f
internal const val QA_START = 0f

/** A spring as Reanimated takes it: mass, stiffness and damping, or a perceptual duration and damping ratio. */
internal class SpringConfig private constructor(
    val mass: Float,
    val stiffness: Float,
    val damping: Float,
    val durationMs: Float,
    val dampingRatio: Float,
    val useDuration: Boolean,
) {
    companion object {
        fun physical(mass: Float, stiffness: Float, damping: Float = 10f) =
            SpringConfig(mass, stiffness, damping, 0f, 0f, false)

        /** Reanimated fills the mass of a duration spring from its gentle default: 4. */
        fun perceptual(durationMs: Float, dampingRatio: Float) =
            SpringConfig(4f, 0f, 0f, durationMs, dampingRatio, true)
    }
}

/** The QA slow-motion scale applied to a spring (identity in the shipped block). */
internal fun sp(c: SpringConfig): SpringConfig {
    val k = QA_SPEED
    return if (c.useDuration) {
        SpringConfig.perceptual(c.durationMs / k, c.dampingRatio)
    } else {
        SpringConfig.physical(c.mass, c.stiffness * k * k, c.damping * k)
    }
}

internal fun ms(x: Float): Float = x / QA_SPEED

/** The source's mid-turn frame (015): the top-right corner of the title page lies at this point. */
internal object HOLD_A {
    val grab = floatArrayOf(402f, -4f)
    val tip = floatArrayOf(-187.68f, 250.27f)
}

internal object TURN {
    const val PICK_SLOP = 4f // pt of travel before a drag picks forward or back
    const val COMMIT_VELOCITY = 450f // pt/s: a flick commits whatever the distance
    const val COMMIT_PROGRESS = 0.5f // past half a turn the page goes over on release
    const val RAMP_IN = 120f // pt of pull over which the roll grows to its full radius (a sheet first bends gently)
    const val RAMP_OUT = 140f // and shrinks as it lands flat

    // Mass-spring for the released page: a little heavier going over than falling back.
    val over = SpringConfig.physical(mass = 1f, stiffness = 190f, damping = 27f)
    val back = SpringConfig.physical(mass = 1f, stiffness = 260f, damping = 32f)

    // The sheet flexes with speed: the roll opens up to +60 % at 2500 pt/s and springs back when it lands.
    const val FLEX_PER_SPEED = 1f / 4200f
    const val FLEX_MAX = 0.6f
    val flex = SpringConfig.physical(mass = 1f, stiffness = 240f, damping = 14f)
    const val RELEASE_KICK = 1f / 9000f // a release kicks the roll's flex by its speed: a flung sheet billows, then settles
    const val BUTTON_GRAB_Y = 0.62f // "Turn the page" lifts the right edge at 62 % of the height
    const val PRE_LIFT = 10f // pt: touch-down in the right 40 % lifts the corner within a frame
    const val PRE_LIFT_ZONE = 0.6f
    const val BACK_FACTOR = 2f // a backward drag moves the fold 1:1, so the hidden corner moves twice as far
}

/** The cast shadow answers the lift: narrower and lighter while the sheet is low, as fitted at the source's lift (p = 0.8). */
internal object LIFT {
    const val WIDTH_BASE = 0.6f
    const val WIDTH_PER_TURN = 0.5f
    const val DARK_BASE = 0.8f
    const val DARK_PER_TURN = 0.25f
}

/** Riffle: a flick right this fast turns every page back to the title, sheets in flight together. */
internal object RIFFLE {
    const val VELOCITY = 1500f // pt/s of the fold
    const val STAGGER_MS = 180f
    val spring = SpringConfig.physical(mass = 1f, stiffness = 260f, damping = 30f)
    const val LAUNCH = 1800f // pt/s each later sheet starts with
    const val SLOTS = 3
}

/** Haptic grain while dragging: a tick per step of fold travel, never closer than MIN_GAP_MS. */
internal object GRAIN {
    const val STEP_PT = 34f
    const val MIN_GAP_MS = 55f
}

/** Idle nudge: the bottom-right corner lifts and falls back with a wobble. */
internal object NUDGE {
    const val AFTER_MS = 4500f
    const val EVERY_MS = 7000f
    const val LIFT_X = -30f
    const val LIFT_Y = -18f
    val up = SpringConfig.perceptual(durationMs = 420f, dampingRatio = 0.85f)
    val down = SpringConfig.physical(mass = 1f, stiffness = 300f, damping = 16f)
    const val HOLD_MS = 160f
    const val KICK = 1.2f // flex impulse when it drops
    const val MAX = 2f // two hints per idle stretch, then still until the next touch
}

/** The mark's sparkles twinkle when the title page settles. */
internal object TWINKLE {
    const val DURATION_MS = 900f
    const val SCALE = 1.35f
    const val TURN_DEG = 45f
    const val STAGGER_MS = 120f
}

/** Foil light on the cover: tilt moves the band; on its own it sweeps every 6 s and once on landing. */
internal object FOIL {
    const val SWEEP_MS = 1400f
    const val EVERY_MS = 6000f
    const val WIDTH = 26f // pt
    const val AMOUNT = 0.62f
    const val ANGLE_DEG = 62f // band direction from the x axis
    const val TILT_GAIN = 260f // pt of band travel per radian of roll
    const val REST = -400f // band position at rest: off the title
    const val REACH = 520f // band position at the end of a sweep
}

/** Page-stack edge at the right: one hairline per sheet still to turn. */
internal object STACK {
    const val GAP = 1.6f
    const val LINE = 1f
}

internal const val FADE_MS = 200f // Reduce Motion: pages cross-fade

// Preserve the RN source value for parity; the approved interaction behavior resumes after 2 s.
internal const val IDLE_RESUME_MS = 3500f
internal const val TOUCH_RESUME_MS = 2000f

/** The autoplay idle nudge on the title page after the riffle, ms on the loop. */
internal const val NUDGE_AT = 12200f

/**
 * Autoplay: a scripted finger through the same input as a real one. Times in ms on a 14 s loop.
 * Each stroke: down at `from`, moves through `via` to `to`, lifts at t1 with the path's speed.
 */
internal const val LOOP = 14f // seconds
internal const val LOOP_MS = 14000f

internal class ScriptStroke(
    val t0: Float,
    val t1: Float,
    val from: FloatArray,
    val via: FloatArray,
    val to: FloatArray,
)

internal val SCRIPT = arrayOf(
    // A hesitant peek at the corner, let go: the page falls back.
    ScriptStroke(900f, 2500f, floatArrayOf(384f, 64f), floatArrayOf(150f, 330f), floatArrayOf(310f, 170f)),
    // Title page over to the preface: a short drag and a flick.
    ScriptStroke(3200f, 3700f, floatArrayOf(386f, 540f), floatArrayOf(300f, 520f), floatArrayOf(150f, 480f)),
    ScriptStroke(5300f, 5800f, floatArrayOf(388f, 300f), floatArrayOf(300f, 310f), floatArrayOf(160f, 340f)),
    ScriptStroke(7400f, 7900f, floatArrayOf(388f, 690f), floatArrayOf(300f, 670f), floatArrayOf(160f, 610f)),
    // A fast flick right from the cover: the riffle turns every page back to the title.
    ScriptStroke(9900f, 10180f, floatArrayOf(60f, 450f), floatArrayOf(120f, 446f), floatArrayOf(260f, 438f)),
)

/** Ease in and out with the site's curve: twinkle and foil sweep. */
internal val EASE_IN_OUT = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/** The approved ease-out for page-stack edges and reduced-motion cross-fades. */
internal val EASE_OUT = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
