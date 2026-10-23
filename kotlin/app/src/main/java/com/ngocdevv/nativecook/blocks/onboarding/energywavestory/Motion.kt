package com.ngocdevv.nativecook.blocks.onboarding.energywavestory

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Everything on the screen is a function of the time since the step last changed, so a touch and
// the loop that plays by itself make exactly the same pictures. Times are in seconds.

/** autoPlay: one pass through the three steps every LOOP seconds, the footer pressed before each change. */
internal const val LOOP = 10.0
internal val AUTO_TAPS = doubleArrayOf(3.4, 6.0, 9.0)
internal const val PRESS_FOR = 0.15

/** After a touch autoPlay waits this long before it goes back to the first step and starts over. */
internal const val USER_HOLD = 3.0

// Back to the first step: the tags leave, the colour drains, and RETURN later the wave is grey and
// still. The colour then sets off again by itself.
internal const val RETURN = 1.0
internal const val SHRINK = 0.2
internal const val DRAIN_AFTER = 0.1
internal const val DRAIN = 0.6
internal const val FILL_AFTER = 0.4
internal const val FILL = 1.9

// A tap before the colour has arrived: it runs the rest of the way in this time.
internal const val FILL_FINISH = 0.3

internal const val TEXT_OUT = 0.2
internal const val TEXT_IN_AFTER = 0.12
internal const val TEXT_IN = 0.3
internal const val TEXT_SHIFT = 12.0

// The tags pop out of the tip of their tail one after another, on a spring that overshoots by an eighth.
internal const val TAG_AFTER = 0.15
internal const val TAG_STAGGER = 0.1
internal const val TAG_MASS = 1.0
internal const val TAG_STIFFNESS = 260.0
internal const val TAG_DAMPING = 17.7

// As they arrive the crests rise by FLEX dp and settle with one soft bounce.
internal const val FLEX = 6.0
internal const val FLEX_DECAY = 7.0
internal const val FLEX_SPEED = 11.0

// The gain that makes the first swing of exp(-FLEX_DECAY t) · sin(FLEX_SPEED t) exactly FLEX high.
private val FlexPeak = atan(FLEX_SPEED / FLEX_DECAY) / FLEX_SPEED
private val FlexGain = FLEX / (exp(-FLEX_DECAY * FlexPeak) * sin(FLEX_SPEED * FlexPeak))

// Third step: each tag squashes flat and comes back with its new emoji, and the second one moves
// along to the wave's last slope.
internal const val FLIP = 0.25
internal const val FLIP_STAGGER = 0.08
internal const val SLIDE = 0.45
internal const val SLIDE_X = 40.0
internal const val SLIDE_Y = 10.0

internal const val BOB = 2.0
internal const val BOB_PERIOD = 2.5

// A tapped tag hops and its emoji turns once.
internal const val JUMP = 0.45
internal const val JUMP_HEIGHT = 12.0
internal const val SPIN = 0.55

internal const val FLASH_IN = 0.05
internal const val FLASH_OUT = 0.25

/** Half of a tag with its tail: a squash about the tag's middle is a scale about the tip plus this much travel. */
internal const val TAG_HALF = 23.0

/** Where the story is: the step shown, the one before it, when it changed, and how far the colour had got then. */
internal data class Scene(val step: Int, val from: Int, val at: Double, val fill: Double)

internal class TextPose(val opacity: Float, val y: Float)

/**
 * One tag: its offset from its place, its scale about the tip of its tail, and whether it shows
 * the emoji of the last step ([late]).
 */
internal class TagPose(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val opacity: Float,
    val late: Boolean,
    val spin: Float,
)

internal fun clamp01(t: Double) = t.coerceIn(0.0, 1.0)

internal fun smooth(t: Double, from: Double, to: Double): Double {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3 - 2 * u)
}

internal fun easeIn(t: Double): Double {
    val u = clamp01(t)
    return u * u
}

internal fun easeOut(t: Double): Double {
    val u = 1 - clamp01(t)
    return 1 - u * u * u
}

internal fun easeInOut(t: Double): Double {
    val u = clamp01(t)
    return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
}

/** The tags' spring from 0 to 1, t seconds after it was let go. */
internal fun spring(t: Double): Double {
    if (t <= 0) return 0.0
    val omega = sqrt(TAG_STIFFNESS / TAG_MASS)
    val zeta = TAG_DAMPING / (2 * sqrt(TAG_STIFFNESS * TAG_MASS))
    val damped = omega * sqrt(1 - zeta * zeta)
    return 1 - exp(-zeta * omega * t) * (cos(damped * t) + (zeta * omega / damped) * sin(damped * t))
}

/** How much of the wave is coloured, 0 to 1 along its length. */
internal fun fillAt(scene: Scene, t: Double): Double {
    val e = t - scene.at
    if (scene.step != 0) return scene.fill + (1 - scene.fill) * easeInOut(e / FILL_FINISH)
    if (e < RETURN) return scene.fill * (1 - easeInOut((e - DRAIN_AFTER) / DRAIN))
    return easeInOut((e - RETURN - FILL_AFTER) / FILL)
}

/** How many dp the crests stand above their place. */
internal fun flexAt(scene: Scene, t: Double, still: Boolean): Double {
    val e = t - scene.at
    if (still || scene.step != 1 || e <= 0) return 0.0
    return FlexGain * exp(-FLEX_DECAY * e) * sin(FLEX_SPEED * e)
}

/** When nothing moves any more; never, while the tags bob. */
internal fun settledAt(scene: Scene, still: Boolean): Double {
    if (scene.step == 0) return scene.at + RETURN + FILL_AFTER + FILL
    return if (still) scene.at + 1 else Double.POSITIVE_INFINITY
}

/** The text of one step: it leaves upwards and the next one comes in from below. */
internal fun textPose(index: Int, scene: Scene, t: Double, still: Boolean): TextPose {
    val e = t - scene.at
    if (scene.step == scene.from) return TextPose(if (index == scene.step) 1f else 0f, 0f)
    if (index == scene.step) {
        val p = easeOut((e - TEXT_IN_AFTER) / TEXT_IN)
        return TextPose(p.toFloat(), if (still) 0f else (TEXT_SHIFT * (1 - p)).toFloat())
    }
    if (index == scene.from) {
        val p = easeIn(e / TEXT_OUT)
        return TextPose((1 - p).toFloat(), if (still) 0f else (-TEXT_SHIFT * p).toFloat())
    }
    return TextPose(0f, 0f)
}

/** [jumped] is when the tag was last tapped. */
internal fun tagPose(index: Int, scene: Scene, t: Double, jumped: Double, still: Boolean): TagPose {
    val e = t - scene.at
    var show = 0.0
    var squash = 1.0
    var slide = 0.0
    var late = false
    if (scene.step == 1) {
        val since = e - TAG_AFTER - index * TAG_STAGGER
        show = if (still) clamp01(since / SHRINK) else spring(since)
    } else if (scene.step == 2) {
        val p = clamp01((e - index * FLIP_STAGGER) / FLIP)
        show = 1.0
        squash = abs(cos(PI * p))
        late = p >= 0.5
        slide = if (still) (if (late) 1.0 else 0.0) else easeInOut(e / SLIDE)
    } else if (scene.from != 0) {
        show = 1 - easeIn(e / SHRINK)
        late = scene.from == 2
        slide = if (late) 1.0 else 0.0
    }
    val x = if (index == 1) slide * SLIDE_X else 0.0
    val y = if (index == 1) slide * SLIDE_Y else 0.0
    // Without motion a tag fades where the others scale, and changes its emoji at the bottom of a dip.
    if (still) return TagPose(x.toFloat(), y.toFloat(), 1f, 1f, (min(1.0, show) * squash).toFloat(), late, 0f)
    val bob = BOB * sin((t / BOB_PERIOD + index / 3.0) * 2 * PI) * min(1.0, show)
    val hop = clamp01((t - jumped) / JUMP)
    // The tag under the wave has its tail on top, so its middle is below the tip.
    val middle = (if (index == 2) 1 else -1) * TAG_HALF
    return TagPose(
        x = x.toFloat(),
        y = (y + bob - JUMP_HEIGHT * 4 * hop * (1 - hop) + middle * (1 - squash) * show).toFloat(),
        scaleX = show.toFloat(),
        scaleY = (show * squash).toFloat(),
        opacity = smooth(show, 0.0, 0.1).toFloat(),
        late = late,
        spin = (360 * easeOut((t - jumped) / SPIN)).toFloat(),
    )
}

/** The grey pill behind the footer while it is pressed. */
internal fun flashAt(pressed: Double, until: Double, t: Double) =
    (smooth(t, pressed, pressed + FLASH_IN) * (1 - smooth(t, until, until + FLASH_OUT))).toFloat()
