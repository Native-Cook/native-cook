package com.ngocdevv.nativecook.blocks.navigation.livetimertab

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.max
import kotlin.math.min

/** Seconds of one autoPlay loop, and when in it the timer starts, the page scrolls up and back, and the timer stops. */
internal const val LOOP = 10f
internal const val START_AT = 0.8f
internal const val SCROLL_AT = 2.2f
internal const val BACK_AT = 6.2f
internal const val STOP_AT = 8.6f

/** Seconds after a touch before the loop starts again. */
internal const val USER_HOLD = 3f

/** How far the loop scrolls, in dp, and for how long each way. */
internal const val AUTO_SCROLL = 420f
internal const val SCROLL_UP = 1.2f
internal const val SCROLL_BACK = 1f

/** Seconds the page takes to return to the top when the loop takes over again. */
internal const val SCROLL_HOME = 0.5f

/** Seconds from the black button to the light box, and back. */
internal const val START = 0.35f
internal const val STOP = 0.3f

/** Seconds the stopwatch takes to swell into the pill, and the pill to collapse. */
internal const val FORM = 0.4f
internal const val COLLAPSE = 0.35f

/** A changing digit: seconds, and the dp the old one falls and the new one drops in from. */
internal const val ROLL = 0.3f
internal const val ROLL_SHIFT = 8f

/** Seconds the pill's time takes to fade when the pill collapses. */
internal const val TIME_OUT = 0.15f

/** Seconds of the cross-fade between the pages. */
internal const val TAB_FADE = 0.25f

/** The curves: a strong ease-out, the swell that passes its end, and the scroll. */
internal val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
internal val Swell = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)
internal val Glide = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

internal const val MARGIN = 20f
internal const val HERO = 240f
internal const val TITLE_GAP = 24f
internal const val TITLE_LINE = 34f
internal const val SUBTITLE_GAP = 6f
internal const val SUBTITLE_LINE = 20f
internal const val CONTROL_GAP = 24f
internal const val CONTROL = 68f
internal const val CONTROL_RADIUS = 14f
internal const val BOX_WIDTH = 160f

/** The middle of the timer control, from the top of the page. */
internal const val CONTROL_CENTRE = HERO + TITLE_GAP + TITLE_LINE + SUBTITLE_GAP + SUBTITLE_LINE + CONTROL_GAP + CONTROL / 2f
internal const val BODY_GAP = 28f
internal const val BODY_LINE = 28f

/** The tab bar above the bottom inset, and the least that inset counts for. */
internal const val TAB_BAR = 60f
internal const val TAB_BOTTOM = 24f

/** The page's last lines fade out over this height above the bar. */
internal const val FADE = 60f

/** From the screen's edges to the middle of the outer tabs. */
internal const val TAB_INSET = 48f
internal const val TAB_ICON = 26f
internal const val TAB_STROKE = 1.8f
internal const val PILL_WIDTH = 86f
internal const val PILL_HEIGHT = 46f

/** The stopwatch's ring, and how far its middle sits under the middle of the icon. */
internal const val DIAL = 19f
internal const val DIAL_DROP = 1.5f

/** The stopwatch's hand, from the middle of the ring. */
internal const val HAND = 4.6f

/** The dash above the ring: its width, and its middle above the ring's top edge. */
internal const val TICK = 6f
internal const val TICK_GAP = 3f

/** The control has left the page when its middle is this far past an edge of what shows, and is back this far inside it. */
internal const val HYSTERESIS = 8f

/** What the timer counts down from, in seconds. */
internal const val TIMER = 900

/** The time that rises from the pill to the box: its size at the pill, and the part of the way it fades in over. */
internal const val GHOST_SCALE = 13f / 19f
internal const val GHOST_IN = 0.15f

internal fun clamp01(t: Float) = min(1f, max(0f, t))

internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** "14:59" for 899 seconds. */
internal fun clock(seconds: Int): String {
    val whole = max(0, seconds)
    return (whole / 60).toString().padStart(2, '0') + ":" + (whole % 60).toString().padStart(2, '0')
}
