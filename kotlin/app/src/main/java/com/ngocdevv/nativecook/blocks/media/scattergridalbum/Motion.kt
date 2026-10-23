package com.ngocdevv.nativecook.blocks.media.scattergridalbum

import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The numbers, the layout and the timeline of Scatter Grid Album, the same in the three stacks.
// Every pose is a pure function of the clock, the layout and the times of the events.

/** One loop of the autoPlay, in seconds. Every drift period divides it, so the loop has no jump. */
internal const val LOOP = 8.0

// The autoPlay's timeline, in seconds from the start of a loop: open and drifting, folded into the
// card, the heart tapped on and off, unfolded, open again.
internal const val FOLD_TAP = 1.2
internal const val HEART_ON_TAP = 3.0
internal const val HEART_OFF_TAP = 4.6
internal const val UNFOLD_TAP = 5.6

/** Seconds after the last touch before the autoPlay starts again (unfolding first if folded). */
internal const val USER_HOLD = 4.0

/** The autoPlay waits for the photos, but never longer than this after the block appears. */
internal const val PRELOAD_WAIT = 3.0

// Layout (dp; the reference screen is 393 × 852, everything follows the safe area and width).

/** A photo lying open, before its tilt: 3:4, a little taller than wide, as in the design. */
internal const val PHOTO_WIDTH = 132.0
internal const val PHOTO_HEIGHT = 176.0

/** Corner radius of a photo open and in its cell; the frame morphs between. */
internal const val PHOTO_RADIUS_OPEN = 18.0
internal const val PHOTO_RADIUS_CELL = 15.0

/** The band the open photos lie in: from this far under the safe top to this far over the safe bottom. */
internal const val SCATTER_TOP = 70.0
internal const val SCATTER_BOTTOM = 10.0

/**
 * Where a photo lies open: its centre as shares of the screen's width and of the band's height, its
 * tilt in degrees, and its drift: periods in seconds (each divides [LOOP]) and where each sine starts
 * (radians).
 */
internal class Slot(
    val x: Double,
    val y: Double,
    val tilt: Double,
    val periodX: Double,
    val periodY: Double,
    val periodTilt: Double,
    val phase: Double,
)

/**
 * The eight photos in grid order (row by row, skipping the centre). Each lies open on the side of the
 * screen its cell is on, as in the design: the second one (top middle cell) is the one tucked in the
 * middle, behind its neighbours; the seventh (bottom middle) lies right of centre, low.
 */
internal val SLOTS = listOf(
    Slot(0.24, 0.135, -4.0, 8.0, 4.0, 8.0, 0.0),
    Slot(0.50, 0.315, -1.5, 4.0, 8.0, 8.0, 2.1),
    Slot(0.69, 0.155, 4.0, 8.0, 4.0, 4.0, 4.0),
    Slot(0.235, 0.48, 3.5, 4.0, 8.0, 8.0, 1.3),
    Slot(0.78, 0.46, -3.5, 8.0, 4.0, 8.0, 5.2),
    Slot(0.26, 0.775, -5.5, 8.0, 8.0, 4.0, 3.3),
    Slot(0.61, 0.635, -5.0, 4.0, 4.0, 8.0, 0.7),
    Slot(0.715, 0.86, 4.0, 8.0, 4.0, 8.0, 4.6),
)

/** Back to front: the tucked one first, then the rest in order (so the sky lies over the dog, etc.). */
internal val DRAW_ORDER = intArrayOf(1, 0, 2, 3, 4, 5, 6, 7)

/** Drift amplitudes: dp each way and degrees each way. */
internal const val DRIFT_X = 2.0
internal const val DRIFT_Y = 3.0
internal const val DRIFT_TILT = 0.6

/**
 * The folded card: a square this far in from each side, its centre at this share of the screen
 * height; its corners round like the outer cells' outer corners, so they line up.
 */
internal const val CARD_SIDE = 20.0
internal const val CARD_CENTER = 0.46
internal const val CARD_RADIUS = 15.0

/**
 * The 3×3 grid on the card: no outer padding; the columns are equal, the middle row taller (as in the
 * design, so the name has room), the top and bottom rows equal.
 */
internal const val GAP_X = 16.0
internal const val GAP_Y = 12.0
internal const val MIDDLE_ROW_SHARE = 0.43

/** The heading open: the year's top this far under the safe top, the name's line box [NAME_GAP] under it. */
internal const val YEAR_TOP = 10.0
internal const val YEAR_SIZE = 11.0
internal const val YEAR_LINE = 14.0

/**
 * The name open. Swift sets it in SF at 52 pt, heavy, with −1 tracking, which makes "Highlands" about
 * 241 dp of ink. Roboto's extra bold is narrower than SF's heavy, so the same width takes a larger size
 * here (measured on the emulator: 47 dp gave 200 dp of ink). The line box stays Swift's, so the year
 * and the photos sit where they do there; the folded size is fitted by measured width, as in Swift.
 */
internal const val NAME_SIZE = 56.0
internal const val NAME_LINE = 60.0
internal const val NAME_TRACKING = -1.0
internal const val NAME_GAP = 7.0

/**
 * The heading folded: the name at most this share of its open size (Swift: 32 of 52 pt), smaller when
 * it must, to fit the middle cell with [NAME_INSET] each side; its centre this far from the cell's
 * centre, the year's centre that far.
 */
internal const val NAME_FOLDED_SCALE = 32.0 / 52.0
internal const val NAME_INSET = 4.0
internal const val NAME_FOLDED_DY = -6.0
internal const val YEAR_FOLDED_SIZE = 10.0
internal const val YEAR_FOLDED_DY = 15.0

/** The icon row: centres this far under the card, [ICON_SPACING] (share of the card side) apart, centred on the card. */
internal const val ICON_DROP = 46.0
internal const val ICON_SPACING = 0.215
internal const val ICON_SIZE = 22.0
internal const val ICON_STROKE = 1.6

/** The square each icon takes touches in. */
internal const val ICON_TARGET = 44.0

/** How far an icon rises as it comes in. */
internal const val ICON_RISE = 8.0

// Motion

internal class Spring(val mass: Double, val stiffness: Double, val damping: Double)

/** A photo flying between its open place and its cell: lands with a slight overshoot. */
internal val PHOTO_SPRING = Spring(1.0, 210.0, 24.0)

/** The gradient's frame and the heading: settles without a visible bounce. */
internal val CARD_SPRING = Spring(1.0, 180.0, 26.0)

/** A pressed icon coming back up. */
internal val RELEASE = Spring(1.0, 380.0, 20.0)

/**
 * Seconds between photos leaving, by distance from the card's centre (nearest first when folding,
 * farthest first when unfolding).
 */
internal const val STAGGER = 0.018

/**
 * The year over the name fades out over this share of the heading's travel; the one under it in the
 * card fades in over the last share.
 */
internal const val YEAR_OUT_SHARE = 0.25
internal const val YEAR_IN_SHARE = 0.3

/** The gradient's edges go soft while it moves (as in the design's frames): up to this blur, at the middle of its travel. */
internal const val FEATHER = 8.0

// Icons: each comes in over ICON_IN, ICON_STEP after the one before, the first ICON_DELAY after the
// fold; on an unfold they all leave over ICON_OUT, and the photos and the card start back
// UNFOLD_DELAY after the tap (the design's card is already moving while its icons vanish).
internal const val ICON_DELAY = 0.35
internal const val ICON_STEP = 0.04
internal const val ICON_IN = 0.25
internal const val ICON_OUT = 0.12
internal const val UNFOLD_DELAY = 0.05

// The heart: a pop (an impulse on a spring: scale 1 → 1.35 → a small dip → 1), a ring of six dots,
// and the fill (in fast, out over HEART_OFF).
internal val POP_SPRING = Spring(1.0, 400.0, 12.0)
internal const val POP_PEAK = 0.35
internal const val HEART_IN = 0.06
internal const val HEART_OFF = 0.2
internal const val BURST_DOTS = 6
internal const val BURST_FOR = 0.4

/** Dots travel from this radius (just inside the heart's edge) out to the other. */
internal const val BURST_FROM = 6.0
internal const val BURST_TO = 20.0
internal const val BURST_DOT = 3.5

/** Presses on the icons: down over [PRESS_IN] to [PRESS_SCALE], back on [RELEASE]. */
internal const val PRESS_IN = 0.1
internal const val PRESS_SCALE = 0.85

/** Reduced motion: fold and unfold are a cross-fade of this length; nothing drifts, pops or bursts. */
internal const val FADE = 0.25

/** Seconds from a fold or an unfold until everything is still. */
internal const val SETTLE = UNFOLD_DELAY + STAGGER * 7 + 1.0

internal const val NEVER = -1e6

internal fun easeOut(t: Double) = EaseOutStrong.transform(clamp01(t).toFloat()).toDouble()

// Events

/** Every channel's progress (0 open, 1 folded) when the last fold or unfold began. */
internal data class Snapshot(val photos: List<Double>, val card: Double, val icons: List<Double>) {
    companion object {
        val Open = Snapshot(List(SLOTS.size) { 0.0 }, 0.0, List(4) { 0.0 })
        val Folded = Snapshot(List(SLOTS.size) { 1.0 }, 1.0, List(4) { 1.0 })
    }
}

/** Everything that happened, as times on the clock in seconds. [from] is where every channel was at [at]. */
internal data class Events(
    val folded: Boolean = false,
    val at: Double = NEVER,
    val from: Snapshot = Snapshot.Open,
    val heartOn: Boolean = false,
    val heartAt: Double = NEVER,
    val downAt: List<Double> = List(4) { NEVER },
    val upAt: List<Double> = List(4) { NEVER },
)

/** The time of an event at [offset] in a loop: this loop's if it has happened, else the last loop's. */
private fun last(base: Double, tau: Double, offset: Double) = if (tau >= offset) base + offset else base + offset - LOOP

/**
 * The events of the autoPlay at [now] for a loop that started at [start]. Each fold or unfold starts
 * from rest, since the one before it has long settled.
 */
internal fun autoEvents(now: Double, start: Double): Events {
    val tau = (now - start) % LOOP
    val base = now - tau
    val fold = last(base, tau, FOLD_TAP)
    val unfold = last(base, tau, UNFOLD_TAP)
    val on = last(base, tau, HEART_ON_TAP)
    val off = last(base, tau, HEART_OFF_TAP)
    val folded = fold > unfold
    return Events(
        folded = folded,
        at = max(fold, unfold),
        from = if (folded) Snapshot.Open else Snapshot.Folded,
        heartOn = on > off,
        heartAt = max(on, off),
    )
}

// Curves

internal fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)

internal fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

/** A spring let go from 0 towards 1 at rest, [t] seconds later (the closed form, so every stack agrees). */
internal fun spring(t: Double, s: Spring): Double {
    if (t <= 0) return 0.0
    val w0 = sqrt(s.stiffness / s.mass)
    val zeta = s.damping / (2 * sqrt(s.stiffness * s.mass))
    if (zeta >= 1) return 1 - exp(-w0 * t) * (1 + w0 * t)
    val wd = w0 * sqrt(1 - zeta * zeta)
    return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
}

/** A spring struck at rest, [t] seconds later: up to 1 at its peak, back through a small dip to 0. */
internal fun impulse(t: Double, s: Spring): Double {
    if (t <= 0) return 0.0
    val w0 = sqrt(s.stiffness / s.mass)
    val zeta = s.damping / (2 * sqrt(s.stiffness * s.mass))
    val wd = w0 * sqrt(1 - zeta * zeta)
    val peakT = atan(wd / (zeta * w0)) / wd
    val peak = exp(-zeta * w0 * peakT) * sin(wd * peakT)
    return exp(-zeta * w0 * t) * sin(wd * t) / peak
}

// Progress (0 open, 1 folded; a photo overshoots 1 a little as it lands)

/** Where a fold or an unfold takes a channel that started at [from], through [curve] (0 → 1). */
private fun toward(e: Events, from: Double, curve: Double) = lerp(from, if (e.folded) 1.0 else 0.0, curve)

/** Reduced motion: the cross-fade between the two layouts. */
internal fun fadeAmount(e: Events, now: Double) = toward(e, e.from.card, clamp01((now - e.at) / FADE))

/** Photo [i], whose turn (by distance from the card's centre, nearest first) is [rank]. */
internal fun photoProgress(i: Int, rank: Int, e: Events, now: Double): Double {
    val delay = if (e.folded) STAGGER * rank else UNFOLD_DELAY + STAGGER * (SLOTS.size - 1 - rank)
    return toward(e, e.from.photos[i], spring(now - e.at - delay, PHOTO_SPRING))
}

/** The gradient's frame and the heading. */
internal fun cardProgress(e: Events, now: Double) =
    toward(e, e.from.card, spring(now - e.at - (if (e.folded) 0.0 else UNFOLD_DELAY), CARD_SPRING))

/** Icon [k]: its opacity, and how far it has risen. */
internal fun iconProgress(k: Int, e: Events, now: Double): Double {
    val t = now - e.at
    if (e.folded) return toward(e, e.from.icons[k], easeOut((t - ICON_DELAY - ICON_STEP * k) / ICON_IN))
    return toward(e, e.from.icons[k], clamp01(t / ICON_OUT))
}

/** What is on screen now, for the next fold or unfold to start from. */
internal fun snapshot(e: Events, ranks: IntArray, now: Double, still: Boolean): Snapshot {
    if (still) {
        val x = fadeAmount(e, now)
        return Snapshot(List(SLOTS.size) { x }, x, List(4) { x })
    }
    return Snapshot(
        List(SLOTS.size) { photoProgress(it, ranks[it], e, now) },
        cardProgress(e, now),
        List(4) { iconProgress(it, e, now) },
    )
}

/** The order photos leave in: by distance of their open place from the card's centre, nearest first. */
internal fun ranks(open: List<Point>, cx: Double, cy: Double): IntArray {
    val order = open.indices.sortedBy { hypot(open[it].x - cx, open[it].y - cy) }
    val ranks = IntArray(open.size)
    order.forEachIndexed { rank, i -> ranks[i] = rank }
    return ranks
}

// Drift

internal class Drift(val x: Double, val y: Double, val tilt: Double)

/** A photo's drift at [t] seconds of the drift clock: x and y in dp, tilt in degrees. */
internal fun drift(slot: Slot, t: Double) = Drift(
    DRIFT_X * sin(2 * PI * t / slot.periodX + slot.phase),
    DRIFT_Y * sin(2 * PI * t / slot.periodY + slot.phase * 1.7),
    DRIFT_TILT * sin(2 * PI * t / slot.periodTilt + slot.phase * 0.6),
)

// Layout and poses

internal data class Point(val x: Double, val y: Double)

internal data class Area(val x: Double, val y: Double, val width: Double, val height: Double) {
    val midX get() = x + width / 2
    val midY get() = y + height / 2
    val maxX get() = x + width
    val maxY get() = y + height

    fun contains(px: Double, py: Double) = px >= x && px <= maxX && py >= y && py <= maxY
}

/** Where everything goes on a screen of this size and safe area. */
internal class Layout(
    val width: Double,
    val height: Double,
    /** The photos' centres lying open (before drift), in grid order. */
    val open: List<Point>,
    val card: Area,
    /** The eight cells, row by row, skipping the middle one. */
    val cells: List<Area>,
    val middle: Area,
    /** The centre of the name's line box open and folded, and its scale folded. */
    val nameOpen: Point,
    val nameFolded: Point,
    val nameFoldedScale: Double,
    /** The centre of the year under the name in the card. */
    val yearFolded: Point,
    val icons: List<Point>,
    /** Each photo's turn when folding (nearest the card's centre first). */
    val ranks: IntArray,
)

/** [nameWidth] is the name's width set at [NAME_SIZE] with [NAME_TRACKING], measured. */
internal fun layout(width: Double, height: Double, top: Double, bottom: Double, nameWidth: Double): Layout {
    val bandTop = top + SCATTER_TOP
    val band = height - bottom - SCATTER_BOTTOM - bandTop
    val open = SLOTS.map { Point(it.x * width, bandTop + it.y * band) }

    val side = width - CARD_SIDE * 2
    val card = Area(CARD_SIDE, height * CARD_CENTER - side / 2, side, side)
    val column = (side - GAP_X * 2) / 3
    val rows = side - GAP_Y * 2
    val edgeRow = rows * (1 - MIDDLE_ROW_SHARE) / 2
    val heights = doubleArrayOf(edgeRow, rows * MIDDLE_ROW_SHARE, edgeRow)
    val grid = ArrayList<Area>(9)
    var y = card.y
    for (r in 0 until 3) {
        for (c in 0 until 3) grid.add(Area(card.x + c * (column + GAP_X), y, column, heights[r]))
        y += heights[r] + GAP_Y
    }
    val middle = grid[4]
    val cells = grid.filterIndexed { i, _ -> i != 4 }

    val nameOpen = Point(width / 2, top + YEAR_TOP + YEAR_LINE + NAME_GAP + NAME_LINE / 2)
    val scale = min(NAME_FOLDED_SCALE, (column - NAME_INSET * 2) / max(1.0, nameWidth))
    val icons = List(4) { k -> Point(card.midX + (k - 1.5) * ICON_SPACING * side, card.maxY + ICON_DROP) }
    return Layout(
        width = width,
        height = height,
        open = open,
        card = card,
        cells = cells,
        middle = middle,
        nameOpen = nameOpen,
        nameFolded = Point(middle.midX, middle.midY + NAME_FOLDED_DY),
        nameFoldedScale = scale,
        yearFolded = Point(middle.midX, middle.midY + YEAR_FOLDED_DY),
        icons = icons,
        ranks = ranks(open, card.midX, card.midY),
    )
}

/** A photo's frame: centre, size, corners, tilt, and [lift] (1 open, 0 in its cell) that scales its shadow. */
internal class PhotoPose(
    val cx: Double,
    val cy: Double,
    val width: Double,
    val height: Double,
    val radius: Double,
    val tilt: Double,
    val lift: Double,
)

/** Photo [i] at progress [u] (0 open, 1 in its cell), drifting by the drift clock [driftT] while open. */
internal fun photoPose(i: Int, u: Double, layout: Layout, driftT: Double?): PhotoPose {
    val slot = SLOTS[i]
    val cell = layout.cells[i]
    val away = 1 - clamp01(u)
    var ox = layout.open[i].x
    var oy = layout.open[i].y
    var tilt = slot.tilt
    if (driftT != null) {
        val d = drift(slot, driftT)
        ox += d.x * away
        oy += d.y * away
        tilt += d.tilt * away
    }
    return PhotoPose(
        cx = lerp(ox, cell.midX, u),
        cy = lerp(oy, cell.midY, u),
        width = max(1.0, lerp(PHOTO_WIDTH, cell.width, u)),
        height = max(1.0, lerp(PHOTO_HEIGHT, cell.height, u)),
        radius = lerp(PHOTO_RADIUS_OPEN, PHOTO_RADIUS_CELL, clamp01(u)),
        tilt = lerp(tilt, 0.0, u),
        lift = away,
    )
}

/** The gradient's frame: [rect], corners, edge blur, and [shadow] (0 open: it fills the screen; 1 folded). */
internal class CardPose(val rect: Area, val radius: Double, val feather: Double, val shadow: Double)

/** The gradient's frame at progress [c]: the whole screen open, the card folded. */
internal fun cardPose(c: Double, layout: Layout): CardPose {
    val card = layout.card
    val rect = Area(lerp(0.0, card.x, c), lerp(0.0, card.y, c), lerp(layout.width, card.width, c), lerp(layout.height, card.height, c))
    val k = clamp01(c)
    return CardPose(rect, CARD_RADIUS * k, FEATHER * 4 * k * (1 - k), k)
}

/** The name and the year over it (open) and under it (folded). */
internal class HeadingPose(
    val name: Point,
    val scale: Double,
    val yearTop: Point,
    val yearTopOpacity: Double,
    val yearCell: Point,
    val yearCellOpacity: Double,
)

/** The name and the year at progress [c]. */
internal fun headingPose(c: Double, layout: Layout): HeadingPose {
    val name = Point(lerp(layout.nameOpen.x, layout.nameFolded.x, c), lerp(layout.nameOpen.y, layout.nameFolded.y, c))
    val scale = lerp(1.0, layout.nameFoldedScale, c)
    // The top year rides above the name, the cell's year below it, each at its own place at its end.
    val above = (NAME_LINE / 2 + NAME_GAP + YEAR_LINE / 2) * scale
    val below = layout.yearFolded.y - layout.nameFolded.y
    return HeadingPose(
        name = name,
        scale = scale,
        yearTop = Point(name.x, name.y - above),
        yearTopOpacity = 1 - clamp01(c / YEAR_OUT_SHARE),
        yearCell = Point(name.x, name.y + below),
        yearCellOpacity = clamp01((c - (1 - YEAR_IN_SHARE)) / YEAR_IN_SHARE),
    )
}

// Heart

/** How filled the heart is. */
internal fun heartFill(e: Events, now: Double): Double {
    val t = now - e.heartAt
    return if (e.heartOn) clamp01(t / HEART_IN) else 1 - clamp01(t / HEART_OFF)
}

/** The heart's scale: the pop after it is filled. */
internal fun heartScale(e: Events, now: Double, still: Boolean): Double {
    if (!e.heartOn || still) return 1.0
    return 1 + POP_PEAK * impulse(now - e.heartAt, POP_SPRING)
}

/** The burst's progress (0 → 1 over [BURST_FOR]), or null when there is none. */
internal fun burst(e: Events, now: Double, still: Boolean): Double? {
    val t = now - e.heartAt
    if (!e.heartOn || still || t < 0 || t >= BURST_FOR) return null
    return t / BURST_FOR
}

/** An icon's scale while pressed and as it springs back. */
internal fun pressScale(k: Int, e: Events, now: Double): Double {
    val down = e.downAt[k]
    val up = e.upAt[k]
    val press = if (down > up) {
        easeOut((now - down) / PRESS_IN)
    } else {
        easeOut((up - down) / PRESS_IN) * (1 - spring(now - up, RELEASE))
    }
    return 1 - (1 - PRESS_SCALE) * press
}

internal fun anyPressed(e: Events) = e.downAt.indices.any { e.downAt[it] > e.upAt[it] }
