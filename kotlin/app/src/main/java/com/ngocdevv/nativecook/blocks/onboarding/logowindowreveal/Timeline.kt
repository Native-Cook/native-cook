package com.ngocdevv.nativecook.blocks.onboarding.logowindowreveal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The show as a function of its time `t`, in seconds: the mark, the flight through its window
// into the photo, the photo's fall into a list of cards, the list's zoom out into three columns,
// and the last row left hanging over the name. Nothing is stored per frame. Lengths are in dp.

/** autoPlay runs the show every LOOP seconds. By hand it stops at END, with everything in place. */
internal const val LOOP = 11.0
internal const val SWELL_AT = 0.6
private const val FLY_AT = 1.0
private const val HERO_AT = 1.7
private const val SHRINK_AT = 2.7
internal const val LIST_AT = 3.4
private const val ZOOM_AT = 6.4
internal const val HANG_AT = 8.2
internal const val END = 9.6
internal const val CLOSE_AT = 10.3

/** After a touch autoPlay waits this long before it closes the show and starts over. */
internal const val USER_HOLD = 3.0

/** A list that was scrolled by hand goes on by itself this long after the finger left. */
internal const val SCROLL_HOLD = 2.0

// The mark: a disc with a rounded window cut out, a little right of its centre.
internal const val MARK = 68.0
private const val WINDOW_WIDTH = 26.0
private const val WINDOW_HEIGHT = 30.0
private const val WINDOW_RADIUS = 9.0
private const val WINDOW_DX = 2.0

// It swells with a grey halo behind it, then grows until the window has swallowed the screen.
private const val SWELL = 1.9
private const val FLY_OVER = 1.25
private const val FLY_POWER = 1.6
internal const val HALO = 1.2
private const val HALO_OPACITY = 0.5

// The photo shows through the window from the first moment of the swell.
private const val PHOTO_FAINT = 0.2
private const val PHOTO_FULL_AT = 0.6

/** The hero photo is asked for this tall, so that it covers the screen and the card with its whole width. */
internal const val HERO_ASPECT = 0.4

// The hero's two lines rise into place one after the other, and leave when the photo shrinks.
private const val TEXT_IN = 0.4
private const val TEXT_GAP = 0.08
private const val TEXT_RISE = 20.0
private const val TEXT_OUT = 0.25

// The list: square cards one under the other. The photo lands as the second one.
internal const val CARD = 330.0
private const val GAP = 18.0
private const val PITCH = CARD + GAP
internal const val CARD_RADIUS = 22.0
internal const val HERO = 1
private val Land = Spring(stiffness = 170.0, damping = 20.0)

// Its neighbours come in from above and below, from this far.
private const val SLIDE = 420.0
private const val CARD_TEXT_IN = 0.3

// The list scrolls by itself at this speed, in dp a second, after a short run-up.
private const val SPEED = 230.0
private const val RUN_UP = 0.3

// Zoom out: the column shrinks to this scale about the middle of the screen while the scroll
// slows to a stop, and a column fades in on each side, half a card higher.
private const val ZOOM = 0.4
private const val ZOOM_FOR = HANG_AT - ZOOM_AT
private const val SIDE_IN = 0.6

/** Rows in a column; the side columns have no first row. */
internal const val ROWS = 8

// The end: the grid runs up until its last row hangs at the top, the middle card larger.
private const val HANG_FOR = 1.0
private const val HUNG = 154.0
private const val HUNG_Y = 140.0
private const val HUNG_SIDE = 105.0
private const val HUNG_SIDE_X = 40.5

// The name's letters come in one after the other, then the line under it.
private const val NAME_AFTER = 0.5
private const val LETTER_GAP = 0.04
private const val LETTER_IN = 0.3
private const val LETTER_RISE = 10.0
private const val TAGLINE_AFTER = 0.9
private const val TAGLINE_IN = 0.4

// Closing: the hanging cards leave upwards, the name shrinks into the middle and the mark takes its place.
private const val LEAVE = 260.0
private const val LEAVE_FOR = 0.4
private const val NAME_OUT = 0.3
private const val NAME_SHRINK = 0.6
private const val MARK_AFTER = 0.2
private const val MARK_IN = 0.35

// Reduced motion: the mark and the end fade into each other.
private const val STILL_IN = 1.0
private const val STILL_FADE = 0.4

private class Spring(val stiffness: Double, val damping: Double)

internal class MarkAt(val scale: Double, val opacity: Double, val halo: Double)

/** A box round its centre, the scale of the photo behind it, and how much shows. */
internal class HeroAt(val cx: Double, val cy: Double, val w: Double, val h: Double, val r: Double, val k: Double, val opacity: Double)

internal class CardAt(val x: Double, val y: Double, val scale: Double, val opacity: Double, val text: Double)

/** How much of a line or a letter shows, and how far under its place it is. */
internal class LineAt(val opacity: Double, val rise: Double)

internal class NameAt(val opacity: Double, val scale: Double)

private fun clamp01(t: Double) = min(1.0, max(0.0, t))

private fun smooth(t: Double, from: Double, to: Double): Double {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3 - 2 * u)
}

private fun easeOut(t: Double) = 1 - (1 - t) * (1 - t) * (1 - t)

private fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/** How far a spring of mass 1 has come from 0 to 1 after [t] seconds; it overshoots a little. */
private fun spring(t: Double, spring: Spring): Double {
    if (t <= 0) return 0.0
    val w = sqrt(spring.stiffness)
    val z = spring.damping / (2 * w)
    val wd = w * sqrt(1 - z * z)
    return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
}

/** Reduced motion shows two pictures, the mark and the end: how much of the end at [t]. */
internal fun stillEnd(t: Double) = smooth(t, STILL_IN, STILL_IN + STILL_FADE) * (1 - smooth(t, CLOSE_AT, CLOSE_AT + STILL_FADE))

/** The mark: its scale, and how much of it and of its halo shows. */
internal fun mark(t: Double, width: Double, height: Double): MarkAt {
    if (t >= CLOSE_AT) {
        val u = clamp01((t - CLOSE_AT - MARK_AFTER) / MARK_IN)
        return MarkAt(NAME_SHRINK + (1 - NAME_SHRINK) * easeOut(u), u, 0.0)
    }
    if (t >= HERO_AT) return MarkAt(1.0, 0.0, 0.0)
    if (t < SWELL_AT) return MarkAt(1.0, 1.0, 0.0)
    if (t < FLY_AT) {
        val u = (t - SWELL_AT) / (FLY_AT - SWELL_AT)
        return MarkAt(1 + (SWELL - 1) * easeInOut(u), 1.0, HALO_OPACITY * smooth(u, 0.0, 0.5))
    }
    // Each moment multiplies the size by the same factor, so the flight feels even, after a slow start.
    val u = (t - FLY_AT) / (HERO_AT - FLY_AT)
    val full = max(width / WINDOW_WIDTH, height / WINDOW_HEIGHT) * FLY_OVER
    return MarkAt(SWELL * (full / SWELL).pow(u.pow(FLY_POWER)), 1.0, HALO_OPACITY * (1 - smooth(u, 0.0, 0.3)))
}

/** The window of a mark of that scale, with nothing behind it. */
private fun windowOf(scale: Double, width: Double, height: Double, opacity: Double) =
    HeroAt(width / 2 + WINDOW_DX * scale, height / 2, WINDOW_WIDTH * scale, WINDOW_HEIGHT * scale, WINDOW_RADIUS * scale, 1.0, opacity)

/** The hero photo: the box it is seen through, the scale of the photo behind it, and how much shows. */
internal fun hero(t: Double, width: Double, height: Double): HeroAt {
    if (t < HERO_AT) {
        val opacity = when {
            t < SWELL_AT -> 0.0
            t < FLY_AT -> PHOTO_FAINT * (t - SWELL_AT) / (FLY_AT - SWELL_AT)
            else -> PHOTO_FAINT + (1 - PHOTO_FAINT) * clamp01((t - FLY_AT) / (HERO_AT - FLY_AT) / PHOTO_FULL_AT)
        }
        return windowOf(mark(t, width, height).scale, width, height, opacity)
    }
    val p = if (t >= LIST_AT) 1.0 else if (t < SHRINK_AT) 0.0 else spring(t - SHRINK_AT, Land)
    val w = width + (CARD - width) * p
    return HeroAt(width / 2, height / 2, w, height + (CARD - height) * p, CARD_RADIUS * clamp01(p), w / width, if (t >= LIST_AT) 0.0 else 1.0)
}

/** One of the hero's two lines. */
internal fun heroLine(t: Double, line: Int): LineAt {
    val u = clamp01((t - HERO_AT - line * TEXT_GAP) / TEXT_IN)
    return LineAt(u * (1 - smooth(t, SHRINK_AT, SHRINK_AT + TEXT_OUT)), TEXT_RISE * (1 - easeOut(u)))
}

/** How far the list has scrolled. [extra] is what a finger added; the end ignores it. */
private fun scroll(t: Double, height: Double, extra: Double): Double {
    val start = PITCH * HERO + CARD / 2 - height / 2
    val run = max(0.0, min(t, ZOOM_AT) - LIST_AT)
    val slow = clamp01((t - ZOOM_AT) / ZOOM_FOR) * ZOOM_FOR
    val auto = start +
        SPEED * (if (run < RUN_UP) run * run / (2 * RUN_UP) else run - RUN_UP / 2) +
        SPEED * (slow - slow * slow / (2 * ZOOM_FOR))
    // Kept between the first card and the last in the middle of the screen.
    val free = min(max(auto + extra, start - PITCH), start + PITCH * (ROWS - 1 - HERO))
    val end = PITCH * (ROWS - 1) + CARD / 2 - height / 2 + (height / 2 - HUNG_Y) / ZOOM
    return free + (end - free) * easeInOut(clamp01((t - HANG_AT) / HANG_FOR))
}

/** Where a card is: [column] -1, 0 or 1, [row] from the top. */
internal fun place(column: Int, row: Int, t: Double, width: Double, height: Double, extra: Double): CardAt {
    if (t < SHRINK_AT || (column != 0 && t < ZOOM_AT) || (column == 0 && row == HERO && t < LIST_AT)) {
        return CardAt(width / 2, -CARD, 1.0, 0.0, 0.0)
    }
    val z = 1 + (ZOOM - 1) * easeInOut(clamp01((t - ZOOM_AT) / ZOOM_FOR))
    val inList = row * PITCH + CARD / 2 - (if (column == 0) 0.0 else PITCH / 2)
    var x = width / 2 + column * PITCH * z
    var y = height / 2 + (inList - scroll(t, height, extra) - height / 2) * z
    var scale = z
    if (t < LIST_AT) y += (if (row < HERO) -1 else 1) * SLIDE * (1 - spring(t - SHRINK_AT, Land))
    if (row == ROWS - 1 && t > HANG_AT) {
        val p = easeInOut(clamp01((t - HANG_AT) / HANG_FOR))
        scale = z + ((if (column == 0) HUNG else HUNG_SIDE) / CARD - z) * p
        x += column * (width / 2 - HUNG_SIDE_X - PITCH * z) * p
    }
    if (t > CLOSE_AT) {
        val u = clamp01((t - CLOSE_AT) / LEAVE_FOR)
        y -= LEAVE * u * u
    }
    return CardAt(
        x, y, scale,
        opacity = if (column == 0) 1.0 else smooth(t, ZOOM_AT, ZOOM_AT + SIDE_IN),
        // The landed photo gets its card's lines a moment later.
        text = if (column == 0 && row == HERO) smooth(t, LIST_AT, LIST_AT + CARD_TEXT_IN) else 1.0,
    )
}

/** A letter of the name. */
internal fun letter(t: Double, index: Int): LineAt {
    val u = clamp01((t - HANG_AT - NAME_AFTER - index * LETTER_GAP) / LETTER_IN)
    return LineAt(u, LETTER_RISE * (1 - easeOut(u)))
}

/** The name as a whole while it closes: how much shows, and its scale. */
internal fun nameOut(t: Double): NameAt {
    val u = smooth(t, CLOSE_AT, CLOSE_AT + NAME_OUT)
    return NameAt(1 - u, 1 + (NAME_SHRINK - 1) * u)
}

internal fun tagline(t: Double) =
    smooth(t, HANG_AT + TAGLINE_AFTER, HANG_AT + TAGLINE_AFTER + TAGLINE_IN) * (1 - smooth(t, CLOSE_AT, CLOSE_AT + NAME_OUT))

/** The mark of a given scale as one path in dp: the disc, and the window as a hole in it. */
internal fun Path.markOutline(scale: Double, width: Double, height: Double) {
    val r = (MARK / 2 * scale).toFloat()
    val cx = (width / 2).toFloat()
    val cy = (height / 2).toFloat()
    val hole = windowOf(scale, width, height, 0.0)
    rewind()
    fillType = PathFillType.EvenOdd
    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    addRoundRect(hole.box())
}

/** The box as a rounded rectangle, scaled by [unit] (1 for dp). */
internal fun HeroAt.box(unit: Float = 1f) = RoundRect(
    ((cx - w / 2) * unit).toFloat(), ((cy - h / 2) * unit).toFloat(), ((cx + w / 2) * unit).toFloat(), ((cy + h / 2) * unit).toFloat(),
    CornerRadius((r * unit).toFloat()),
)
