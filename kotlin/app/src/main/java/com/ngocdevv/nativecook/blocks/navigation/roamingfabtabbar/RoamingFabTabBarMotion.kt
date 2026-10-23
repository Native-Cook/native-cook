package com.ngocdevv.nativecook.blocks.navigation.roamingfabtabbar

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Seconds of one autoPlay loop, and when in it each tab is chosen: Search, Friends, Chats, Home. */
internal const val LOOP = 9f
internal val Steps = listOf(1.2f to 1, 2.8f to 2, 4.4f to 3, 6.4f to 0)

/** Seconds after a touch before the loop starts again. */
internal const val USER_HOLD = 3f

/** Seconds a tab change runs for: the new icon's wiggle. The clock that drives it is linear. */
internal const val SWITCH = 0.5f

/** Seconds an icon takes to change drawing, and a page to fade in. */
internal const val ICON_FADE = 0.15f
internal const val PAGE_FADE = 0.2f

/** The wiggle: degrees of the first swing, how fast it dies, and how much the icon grows. */
internal const val WIGGLE = 16f
internal const val WIGGLE_DECAY = 0.8f
internal const val WIGGLE_GROW = 0.15f

/** Seconds the button takes to shrink away, to pop in, and to slide between its two places. */
internal const val SHRINK = 0.2f
internal const val POP = 0.4f
internal const val SLIDE = 0.45f

/** The same slide under reduced motion, where it is a fade out and a fade in. */
internal const val REDUCED_SLIDE = 0.3f

/** The curves: the shrink eases in, the pop passes its end by a tenth, the slide by less. */
internal val ShrinkEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)
internal val PopEase = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
internal val SlideEase = CubicBezierEasing(0.34f, 1.25f, 0.64f, 1f)

/** How high the button rises on its way across, and how much a press shrinks it. */
internal const val ARC = 10f
internal const val PRESS = 0.08f
internal const val PRESS_IN = 0.08f
internal const val PRESS_OUT = 0.16f

/** The tab bar above the bottom inset, the least that inset counts for, and the icons' middle below the bar's top. */
internal const val BAR = 56f
internal const val BAR_BOTTOM = 28f
internal const val ICON_Y = 30f

/** Half the square a tab answers to a touch in. */
internal const val TAB_HIT = 28f

/** The button on the first tab and on the last: its size and its middle above the bar's top. */
internal const val HOME_SIZE = 58f
internal const val HOME_RISE = 52f
internal const val CHAT_SIZE = 50f
internal const val CHAT_RISE = 46f

/** The last tab's button, from the right edge to its middle. */
internal const val CHAT_INSET = 49f

/** The button's shadow: its offset, its blur as a standard deviation, and its strength. */
internal const val SHADOW_Y = 6f
internal const val SHADOW_BLUR = 7f
internal const val SHADOW_ALPHA = 0.3f
internal const val GLYPH_STROKE = 2.4f

/** The bubble of the last tab's button is the tab's own, a little smaller. */
internal const val COMPOSE_SCALE = 0.92f

internal const val MARGIN = 20f
internal const val TITLE_TOP = 17f
internal const val TITLE_LINE = 41f

internal fun clamp01(t: Float) = min(1f, max(0f, t))

internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Degrees the chosen icon is turned by, [t] of the way through its wiggle: three swings that die away. */
internal fun wiggle(t: Float) = -WIGGLE * sin(3f * PI.toFloat() * t) * (1f - clamp01(t)).pow(WIGGLE_DECAY)

internal fun swell(t: Float) = 1f + WIGGLE_GROW * sin(PI.toFloat() * clamp01(t))

// What the icons are drawn from, in dp around the icon's middle. A piece is a path or a circle,
// stroked or filled in the icon's colour or, for a detail cut out of a solid shape, in the page's.
internal enum class Coat { Stroke, Fill, CutStroke, CutFill }

internal class Piece(val path: Path, val paint: Coat, val width: Float = 0f)

internal class Icon(val pivot: Offset, val off: List<Piece>, val on: List<Piece>)

/** A path from M, L, Q, C and Z with absolute numbers, the form the three versions of the block share. */
internal fun shape(d: String) = PathParser().parsePathString(d).toPath()

private fun piece(d: String, paint: Coat, width: Float = 0f) = Piece(shape(d), paint, width)

private fun circle(x: Float, y: Float, r: Float, paint: Coat, width: Float = 0f) =
    Piece(Path().apply { addOval(Rect(x - r, y - r, x + r, y + r)) }, paint, width)

private const val HOUSE = "M-8.5 -1.6L0 -9L8.5 -1.6L8.5 6.6Q8.5 9 6.1 9L-6.1 9Q-8.5 9 -8.5 6.6Z"
private const val DOOR = "M-2.3 9L-2.3 4.2Q-2.3 2.2 0 2.2Q2.3 2.2 2.3 4.2L2.3 9Z"
private const val HANDLE = "M3.2 3.2L8.6 8.6"
private const val GLINT = "M-4.9 -1.9Q-4.9 -4.9 -1.9 -4.9"
private const val SHOULDERS = "M-9.5 8.5C-9.5 3.2 -6 1.4 -2 1.4C0 1.4 1.8 1.9 3.2 2.8"
private const val ADD = "M6.5 2.3L6.5 8.7M3.3 5.5L9.7 5.5"
private const val SMILE = "M-4.4 -0.4Q-1.7 2.6 1 -0.4"
private const val SHOULDER = "M-8.8 9.8Q-8.4 7 -6.2 5.4"
private const val BADGE_PLUS = "M6.8 4L6.8 8.4M4.6 6.2L9 6.2"
private const val BUBBLE = "M-2.5 -8.5L2.5 -8.5Q9.5 -8.5 9.5 -1.5L9.5 -0.5Q9.5 6.5 2.5 6.5L-3.5 6.5L-8.6 9.2L-7.6 4.6Q-9.5 2.6 -9.5 -0.5L-9.5 -1.5Q-9.5 -8.5 -2.5 -8.5Z"
private const val BARS = "M-3.4 -3L-3.4 1M0 -4.2L0 2.2M3.4 -3L3.4 1"
internal val Bubble = shape(BUBBLE)
internal val Plus = shape("M-9 0L9 0M0 -9L0 9")
internal val Pencil = shape("M-2.8 1.8L3.4 -4.4")

/** The four tabs: a house, a magnifier that swings on the end of its handle, a person who nods, a bubble. */
internal val Icons = listOf(
    Icon(
        pivot = Offset(0f, 0f),
        off = listOf(piece(HOUSE, Coat.Stroke, 2f)),
        on = listOf(piece(HOUSE, Coat.Fill), piece(HOUSE, Coat.Stroke, 2f), piece(DOOR, Coat.CutFill)),
    ),
    Icon(
        pivot = Offset(8.6f, 8.6f),
        off = listOf(circle(-1.6f, -1.6f, 6.4f, Coat.Stroke, 2f), piece(HANDLE, Coat.Stroke, 2f)),
        on = listOf(circle(-1.6f, -1.6f, 6.4f, Coat.Stroke, 3.4f), piece(HANDLE, Coat.Stroke, 3.4f), piece(GLINT, Coat.Stroke, 1.6f)),
    ),
    Icon(
        pivot = Offset(0f, 10f),
        off = listOf(circle(-2f, -5f, 3.8f, Coat.Stroke, 2f), piece(SHOULDERS, Coat.Stroke, 2f), piece(ADD, Coat.Stroke, 2f)),
        on = listOf(
            circle(-1.5f, -2.5f, 7.2f, Coat.Stroke, 2.2f),
            circle(-4f, -4f, 1.1f, Coat.Fill),
            circle(0.6f, -4f, 1.1f, Coat.Fill),
            piece(SMILE, Coat.Stroke, 1.6f),
            piece(SHOULDER, Coat.Stroke, 2.2f),
            circle(6.8f, 6.2f, 6.2f, Coat.CutFill),
            circle(6.8f, 6.2f, 4.6f, Coat.Fill),
            piece(BADGE_PLUS, Coat.CutStroke, 1.6f),
        ),
    ),
    Icon(
        pivot = Offset(0f, 0f),
        off = listOf(piece(BUBBLE, Coat.Stroke, 2f), piece(BARS, Coat.Stroke, 1.8f)),
        on = listOf(piece(BUBBLE, Coat.Fill), piece(BUBBLE, Coat.Stroke, 2f), piece(BARS, Coat.CutStroke, 1.8f)),
    ),
)
