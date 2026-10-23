package com.ngocdevv.nativecook.blocks.navigation.railtobarmorph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** The screen the surface lives on, in dp: its size, the top safe-area inset and the bar's whole height. */
internal class Frame(val width: Float, val height: Float, val top: Float, val bar: Float)

internal const val LIST = 290f
internal const val RAIL = 64f
internal const val LIST_RADIUS = 28f
internal const val RAIL_RADIUS = 24f
internal const val BAR_RADIUS = 28f

// The bar's height above the bottom safe-area inset.
internal const val BAR = 62f

internal const val MARK = 28f

// The mark's centre and the first row's centre, below the top safe-area inset.
internal const val MARK_Y = 24f
internal const val ROW_Y = 96f
internal const val PITCH = 56f
internal const val ICON = 24f
internal const val BAR_ICON = 26f

// The centre of the icon column in the list and in the rail.
internal const val LIST_X = 42f
internal const val RAIL_X = 32f
internal const val LABEL_X = 72f

// The rail becomes the bar in DOCK_IN seconds and the bar becomes the list in DOCK_OUT; each of
// the bar's icons sets off STAGGER seconds after the one below it.
internal const val DOCK_IN = 0.7f
internal const val DOCK_OUT = 0.8f
internal const val STAGGER = 0.025f

// The icons that do not go to the bar are gone after this part of the way.
internal const val SPARE_OUT = 0.3f

private const val QUARTER = (PI / 2).toFloat()

internal fun clamp(t: Float) = t.coerceIn(0f, 1f)

internal fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

internal fun easeInOut(t: Float): Float {
    val c = clamp(t)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}

internal fun easeOut(t: Float) = 1f - (1f - clamp(t)).pow(3)

/**
 * How far the icon that sets off in place [order] is along its flight to the bar, as a quarter
 * turn in radians. [docked] icons set off one after another within the clock [dock].
 */
internal fun turnAt(order: Int, docked: Int, dock: Float): Float {
    val lag = STAGGER / DOCK_IN
    return easeInOut((dock - order * lag) / (1f - (docked - 1) * lag)) * QUARTER
}

internal class Panel(val top: Float, val width: Float, val topLeft: Float, val topRight: Float, val bottomRight: Float)

/**
 * The white surface. [wide] is 1 for the list and 0 for the rail; [dock] is the clock of the rail
 * becoming the bar. The left and bottom edges stay where they are. The right edge goes out with
 * the first icon to leave and the top edge comes down with the last one, so that every icon stays
 * on the surface all the way; the rounding moves from the right corners to the top corners.
 */
internal fun panelAt(wide: Float, dock: Float, docked: Int, frame: Frame): Panel {
    val e = easeInOut(dock)
    val strip = mix(RAIL, LIST, wide)
    val corner = mix(RAIL_RADIUS, LIST_RADIUS, clamp(wide))
    return Panel(
        top = (1f - cos(turnAt(docked - 1, docked, dock))) * (frame.height - frame.bar),
        width = mix(strip, frame.width, sin(turnAt(0, docked, dock))),
        topLeft = BAR_RADIUS * e,
        topRight = mix(corner, BAR_RADIUS, e),
        bottomRight = corner * (1f - e),
    )
}

/** Where an icon is; [way] is how far along its flight to the bar it is, 0 to 1. */
internal class IconPlace(val x: Float, val y: Float, val scale: Float, val alpha: Float, val way: Float)

/**
 * Where icon [index] is. In the list and the rail the icons are a column; the first [docked] of
 * them go to the bar, the lowest first, each along a quarter of an ellipse: out to the right, then
 * down, so that mid-way they lie on a diagonal curve in their order. The others fade where they are.
 */
internal fun iconAt(index: Int, docked: Int, wide: Float, dock: Float, frame: Frame): IconPlace {
    val x = mix(RAIL_X, LIST_X, wide)
    val y = frame.top + ROW_Y + index * PITCH
    if (index >= docked) return IconPlace(x, y, 1f, 1f - clamp(dock / SPARE_OUT), 0f)
    val turn = turnAt(docked - 1 - index, docked, dock)
    val way = turn / QUARTER
    val toX = frame.width * (index + 0.5f) / docked
    val toY = frame.height - frame.bar + BAR / 2f
    return IconPlace(
        x = x + (toX - x) * sin(turn),
        y = y + (toY - y) * (1f - cos(turn)),
        scale = mix(1f, BAR_ICON / ICON, way),
        alpha = 1f,
        way = way,
    )
}

/** The mark rides above the icon column and flies to the top centre as the bar forms: its x. */
internal fun markX(wide: Float, dock: Float, frame: Frame) = mix(mix(RAIL_X, LIST_X, wide), frame.width / 2f, easeInOut(dock))
