package com.ngocdevv.nativecook.blocks.onboarding.pressholdsunrise

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The whole screen is one function of a scene time `s`, in seconds. 0 is the welcome at rest;
// holding the button runs s to HOLD; from there it runs by itself through the tunnel to AT_B, the
// first page at rest; going back runs it on to END, which is the welcome again (s jumps to 0).

/** How long the button has to be held. */
internal const val HOLD = 1.4f

/** A release before that springs the button back: seconds, and the spring (mass 1). */
internal const val RELEASE = 0.6f
internal const val RELEASE_STIFFNESS = 220f
internal const val RELEASE_DAMPING = 22f

// After the release: the texts fade, the orange covers the screen, and five shapes are born in
// the centre one after another, each growing until it covers the screen in turn.
internal const val TEXT_OUT = 0.2f
internal const val COVER = 0.4f
internal const val SHAPE_FIRST = 0.3f
internal const val SHAPE_GAP = 0.2f
internal const val SHAPE_GROW = 0.5f

/** Degrees a shape turns while it grows. */
internal const val SHAPE_TURN = 25f

/** A shape is born this many dp across. */
internal const val SHAPE_BORN = 60f

// The first page arrives on the plain dark screen, each part a little after the one before.
internal const val ARRIVE = HOLD + 1.7f
internal const val MARK_IN = 0.3f
internal const val HEADING_AFTER = 0.1f
internal const val HEADING_IN = 0.4f
internal const val HEADING_SLIDE = 24f
internal const val DISC_AFTER = 0.2f
internal const val DISC_IN = 0.4f
internal const val BALL_AFTER = 0.3f
internal const val BALL_GAP = 0.1f
internal const val BALL_DROP = 70f
internal const val BALL_FADE = 0.12f
internal const val BALL_STIFFNESS = 180f
internal const val BALL_DAMPING = 16f

/** The balls' spring is taken as settled after this long. */
internal const val BALL_SETTLE = 0.7f
internal const val PAGER_AFTER = 0.6f
internal const val PAGER_IN = 0.4f
internal const val ACTION_AFTER = 0.7f
internal const val ACTION_GAP = 0.1f
internal const val ACTION_IN = 0.35f
internal const val ACTION_RISE = 16f
internal const val AT_B = ARRIVE + 1.2f

// Going back: the page fades and three shapes shrink into the centre, one behind the other.
internal const val PAGE_OUT = 0.15f
internal const val BACK_GAP = 0.15f
internal const val BACK_SHRINK = 0.5f
internal const val LABEL_BACK = 0.25f
internal const val END = AT_B + 2f * BACK_GAP + BACK_SHRINK

/** Under reduced motion the two screens cross-fade instead. */
internal const val CALM_FADE = 0.3f

/** autoPlay: the button is pressed at PRESS_AT of every LOOP seconds, the page left at BACK_AT. */
internal const val LOOP = 9f
internal const val PRESS_AT = 1.2f
internal const val BACK_AT = PRESS_AT + AT_B + 2f

/** After a touch autoPlay waits this long before it starts over from the welcome. */
internal const val USER_HOLD = 3f

// Sizes, in dp.
internal const val BUTTON = 58f

/** The button once the hold is complete: the white disc keeps a thin rim round it. */
internal const val BUTTON_FULL = 232f

/** The small button sits this far above the disc's centre, which is where it grows to. */
internal const val BUTTON_RISE = 10f
internal const val DISC = 259f

/** How much the disc swells while the button is held. */
internal const val DISC_SWELL = 0.04f

internal fun clamp01(t: Float) = t.coerceIn(0f, 1f)

internal fun smooth(t: Float, from: Float, to: Float): Float {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3f - 2f * u)
}

internal fun easeOut(t: Float): Float {
    val u = 1f - clamp01(t)
    return 1f - u * u * u
}

/** Where a spring of mass 1 that was let go at 0 is after [t] seconds, on its way to 1. */
internal fun spring(t: Float, stiffness: Float, damping: Float): Float {
    if (t <= 0f) return 0f
    val w = sqrt(stiffness)
    val z = damping / (2f * w)
    val wd = w * sqrt(1f - z * z)
    return 1f - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
}

/** The timing curve of an early release: the spring above, over RELEASE seconds. */
internal fun releaseEase(x: Float) = spring(x * RELEASE, RELEASE_STIFFNESS, RELEASE_DAMPING)

/**
 * How far the button has grown, 0 to 1: steady, easing out a little at the end. Below 0 while a
 * release overshoots, which makes the button dip under its size for a moment.
 */
internal fun held(s: Float): Float {
    val p = min(1f, s / HOLD)
    return p + 0.12f * sin(PI.toFloat() * p)
}

/** The orange circle's radius. [reach] is the radius that covers the screen. */
internal fun buttonRadius(s: Float, reach: Float, calm: Boolean): Float {
    if (s <= HOLD) return BUTTON / 2f + (BUTTON_FULL - BUTTON) / 2f * held(s)
    if (s > AT_B) return BUTTON / 2f
    if (calm) return BUTTON_FULL / 2f
    val u = clamp01((s - HOLD) / COVER)
    return BUTTON_FULL / 2f + (reach - BUTTON_FULL / 2f) * u * u * u
}

/** How far above the disc's centre the orange circle's centre is. */
internal fun buttonRise(s: Float): Float {
    if (s <= HOLD) return BUTTON_RISE * (1f - clamp01(held(s)))
    return if (s > AT_B) BUTTON_RISE else 0f
}

internal fun discScale(s: Float) = if (s <= HOLD) 1f + DISC_SWELL * clamp01(held(s)) else 1f

/** Under reduced motion: how much of the dark page shows over the welcome. */
internal fun dusk(s: Float): Float {
    if (s <= HOLD) return 0f
    return if (s <= AT_B) clamp01((s - HOLD) / CALM_FADE) else 1f - clamp01((s - AT_B) / CALM_FADE)
}

/** The welcome's texts, which lie under the tunnel. */
internal fun textAlpha(s: Float, calm: Boolean): Float {
    if (calm || s <= HOLD || s > AT_B) return 1f
    return 1f - clamp01((s - HOLD) / TEXT_OUT)
}

/** The bolt and the button's label, which lie over it. */
internal fun labelAlpha(s: Float, calm: Boolean): Float {
    if (calm) return 1f - dusk(s)
    if (s <= HOLD) return 1f
    if (s <= AT_B) return 1f - clamp01((s - HOLD) / TEXT_OUT)
    return clamp01((s - (END - LABEL_BACK)) / LABEL_BACK)
}

/** The status bar's icons are light from the moment the orange has covered the screen. */
internal fun night(s: Float, calm: Boolean): Boolean {
    if (calm) return dusk(s) > 0.5f
    return s > HOLD + COVER && s < AT_B + BACK_SHRINK
}

/** A tunnel shape at one moment: its radius in dp, its opacity and its turn in degrees. */
internal class Grown(var radius: Float = 0f, var alpha: Float = 0f, var turn: Float = 0f)

/** Shape [index] of the tunnel (0 to 4), written into [out]. */
internal fun grown(s: Float, index: Int, reach: Float, out: Grown) {
    val u = (s - HOLD - SHAPE_FIRST - index * SHAPE_GAP) / SHAPE_GROW
    val c = clamp01(u)
    // Translucent while small, so the shape behind tints it. Gone once the next one covers it.
    val covered = index < 4 && u >= 1f + SHAPE_GAP / SHAPE_GROW
    out.alpha = if (u <= 0f || s > AT_B || covered) 0f else min(1f, u / 0.12f) * (0.6f + 0.4f * smooth(u, 0.1f, 0.5f))
    out.radius = SHAPE_BORN / 2f * (reach / (SHAPE_BORN / 2f)).pow(c)
    out.turn = SHAPE_TURN * c
}

/** Shape [index] of the way back (0 is in front): the same, shrinking. */
internal fun shrunk(s: Float, index: Int, reach: Float, out: Grown) {
    val w = clamp01((s - AT_B - index * BACK_GAP) / BACK_SHRINK)
    out.alpha = if (s <= AT_B) 0f else 1f - smooth(w, 0.55f, 1f)
    out.radius = reach * (SHAPE_BORN / 2f / reach).pow(w)
    out.turn = -SHAPE_TURN * w
}

/** The first page fades as soon as the way back starts. */
internal fun pageOut(s: Float) = if (s > AT_B) 1f - clamp01((s - AT_B) / PAGE_OUT) else 1f

/** A part of the first page that fades in over [length], [after] seconds into the arrival. */
internal fun arrived(s: Float, after: Float, length: Float, calm: Boolean) = if (calm) 1f else easeOut((s - ARRIVE - after) / length)

/** The first page's opacity as a whole (each part multiplies its own arrival into it). */
internal fun pageAlpha(s: Float, calm: Boolean): Float {
    if (calm) return dusk(s)
    return if (s < ARRIVE) 0f else pageOut(s)
}

/** Ball [index]: how far above its place it is. */
internal fun ballLift(s: Float, index: Int, calm: Boolean): Float {
    if (calm) return 0f
    val t = s - ARRIVE - BALL_AFTER - index * BALL_GAP
    val settled = if (t >= BALL_SETTLE) 1f else spring(t, BALL_STIFFNESS, BALL_DAMPING)
    return BALL_DROP * (1f - settled)
}

/** Ball [index]: its opacity. */
internal fun ballAlpha(s: Float, index: Int, calm: Boolean) = if (calm) 1f else clamp01((s - ARRIVE - BALL_AFTER - index * BALL_GAP) / BALL_FADE)

/** How much of the pager's arc is drawn, from its left end. */
internal fun pagerDrawn(s: Float, calm: Boolean) = if (calm) 1f else smooth(s - ARRIVE, PAGER_AFTER, PAGER_AFTER + PAGER_IN)

// The tunnel's shapes, as outlines round the origin with a radius of about 1. Each is a closed
// line through many points, so the three stacks draw exactly the same outline.
private const val STEPS = 144

private fun polar(radius: (Double) -> Double) = Path().apply {
    for (i in 0 until STEPS) {
        val angle = i.toDouble() / STEPS * PI * 2
        val r = radius(angle)
        val x = (r * cos(angle)).toFloat()
        val y = (r * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A square of half-side 1 with corners rounded by 0.3. */
internal val Square = Path().apply {
    val corner = 0.3
    for (quarter in 0 until 4) {
        val base = quarter * PI / 2
        val cx = (1 - corner) * if (cos(base + PI / 4) < 0) -1 else 1
        val cy = (1 - corner) * if (sin(base + PI / 4) < 0) -1 else 1
        for (i in 0..12) {
            val angle = base + i / 12.0 * (PI / 2)
            val x = (cx + corner * cos(angle)).toFloat()
            val y = (cy + corner * sin(angle)).toFloat()
            if (quarter == 0 && i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    close()
}

/** A circle with twelve shallow lobes. */
internal val Scallop = polar { 1 + 0.06 * sin(12 * it) }

/** A superellipse: |x|⁴ + |y|⁴ = 1. */
internal val Squircle = polar { (cos(it).pow(4) + sin(it).pow(4)).pow(-0.25) }

/** A circle with a slow wave in its edge. */
internal val Blob = polar { 1 + 0.09 * sin(3 * it + 0.5) + 0.04 * sin(5 * it + 2) }
internal val Round = Path().apply { addOval(Rect(-1f, -1f, 1f, 1f)) }

/** The wavy shape is the one that reaches least far for its size: the others cover with room to spare. */
internal const val REACH = 1f / 0.87f + 0.03f

/** A lightning bolt in a 16 × 16 box. */
internal val Bolt = Path().apply {
    moveTo(9.4f, 0.8f); lineTo(3f, 9.1f); lineTo(7.3f, 9.1f)
    lineTo(6.3f, 15.2f); lineTo(13f, 6.7f); lineTo(8.6f, 6.7f)
    close()
}
