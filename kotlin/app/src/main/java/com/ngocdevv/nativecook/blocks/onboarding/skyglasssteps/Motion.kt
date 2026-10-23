package com.ngocdevv.nativecook.blocks.onboarding.skyglasssteps

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

// Everything on the screen is a function of the clock and of the last few events (a swipe, a
// change of the step in focus, a tap), so a finger and the loop that plays by itself make exactly
// the same pictures. Times are in seconds, lengths in dp.

/** autoPlay: one pass through the three steps every LOOP seconds. The sky repeats in the same time. */
internal const val LOOP = 9.0

/** autoPlay swipes on at these moments; the last swipe brings the first step round again. */
internal val AUTO_SWIPES = doubleArrayOf(2.2, 5.2, 8.2)

/** How long the first step has been in focus when a loop starts: its rows are in and it is still. */
internal val LEAD = LOOP - AUTO_SWIPES[2]

/** After a touch autoPlay waits this long before it goes back to the first step and starts over. */
internal const val USER_HOLD = 3.0

internal const val STEPS = 3

/** From one card to the next: a card's width and the gap. */
internal const val PITCH = 314.0

// The carousel: a spring with almost no bounce (response 0.5 s, damping ratio 0.85).
internal const val SLIDE_MASS = 1.0
internal const val SLIDE_STIFFNESS = 158.0
internal const val SLIDE_DAMPING = 21.4

/** After this long the spring is where it was going. */
internal const val SLIDE_REST = 1.2

/** How far ahead a flick looks for the card to stop on, and the fastest flick, in cards a second. */
internal const val FLICK = 0.2
internal const val FLICK_MAX = 8.0

// A preview comes in when its card takes the focus: each part rises and fades in, one after another.
internal const val ENTER_AFTER = 0.1
internal const val ENTER_RISE = 0.45
internal const val ENTER_FADE = 0.25

/** The preview of the card that lost the focus fades out in this time. */
internal const val LEAVE_FADE = 0.25

/** With reduced motion, and for anything that only fades. */
internal const val FADE = 0.2

// Step 1: four rows; the white fill steps down them once.
internal const val ROW_STAGGER = 0.06
internal const val ROW_SHIFT = 16.0
internal const val ROW_FIRST = 1.2
internal const val ROW_EVERY = 0.55
internal const val ROW_SLIDE = 0.3

// Step 2: three lesson cards, back to front; the front one lifts and settles once.
internal const val LESSON_STAGGER = 0.08
internal const val LESSON_SHIFT = 30.0
internal const val LIFT_AFTER = 1.3
internal const val LIFT_FOR = 0.6
internal const val LIFT = 4.0

// Step 3: the panel grows in, the checks tick in reading order, the badge pulses once.
internal const val PANEL_IN = 0.35
internal const val PANEL_SCALE = 0.96
internal const val TICK_FIRST = 0.7
internal const val TICK_EVERY = 0.15
internal const val TICK_FOR = 0.3
internal const val TICK_POP = 0.2
internal const val UNTICK_FOR = 0.15
internal const val PULSE_AFTER = 0.2
internal const val PULSE_FOR = 0.45
internal const val PULSE = 0.14

// The headline moves against a dragging finger by up to this much, and goes back when it lifts.
internal const val PARALLAX = 6.0
internal const val PARALLAX_BACK = 0.3

/** The longest a preview plays after its card took the focus; the clock may stop after this. */
internal const val QUIET = 3.5

/** The carousel's spring: where it is going (a whole card, counted without end), from where, how fast it was let go, and when. */
internal data class Slide(val page: Int, val from: Double, val velocity: Double, val at: Double)

/**
 * The card whose preview plays: its page, the step that had the focus before and for how long,
 * and when the focus changed. [warm] means the card kept the focus and only starts over, so
 * nothing comes in again; [row] is where its white fill was then.
 */
internal data class Focus(val page: Int, val was: Int, val held: Double, val at: Double, val warm: Boolean, val row: Double)

/** A row picked by hand: the fill goes there from where it was. No row is -1. */
internal data class Pick(val row: Int, val from: Double, val at: Double)

/** The headline's drag: how far the finger has pulled, and when it let go (negative while it holds). */
internal data class Pull(val x: Double, val released: Double)

/** The clock of a step's preview: seconds since its card took the focus, how visible it is, and whether its parts come in. */
internal class Preview(val e: Double, val shown: Float, val warm: Boolean)

/** One part coming in: its opacity and how far below its place it is. */
internal class Entrance(val opacity: Float, val y: Float)

internal class PanelPose(val opacity: Float, val scale: Float)

/** How checked a square is, 0 to 1, and how much it swells while it is being checked. */
internal class Tick(val q: Float, val pop: Float)

internal fun clamp01(t: Double) = min(1.0, max(0.0, t))

internal fun smooth(t: Double, from: Double, to: Double): Double {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3 - 2 * u)
}

internal fun easeOut(t: Double): Double {
    val u = 1 - clamp01(t)
    return 1 - u * u * u
}

internal fun easeInOut(t: Double): Double {
    val u = clamp01(t)
    return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
}

/** One smooth rise and fall over 0…1, flat at both ends. */
internal fun bump(t: Double) = (1 - cos(2 * PI * clamp01(t))) / 2

/** The step a page shows: pages go on without end in both directions. */
internal fun stepOf(page: Int) = ((page % STEPS) + STEPS) % STEPS

/** How many cards a step sits from the middle of the screen, the short way round. */
internal fun offsetOf(step: Int, position: Double): Double {
    val d = step - position
    return d - STEPS * round(d / STEPS)
}

/** Where the carousel is, in cards. With reduced motion it is where it is going. */
internal fun slideAt(slide: Slide, t: Double, still: Boolean): Double {
    val e = t - slide.at
    val page = slide.page.toDouble()
    if (still || e >= SLIDE_REST) return page
    if (e <= 0) return slide.from
    val omega = sqrt(SLIDE_STIFFNESS / SLIDE_MASS)
    val decay = SLIDE_DAMPING / (2 * SLIDE_MASS)
    val damped = sqrt(omega * omega - decay * decay)
    val a = slide.from - page
    val b = (slide.velocity + decay * a) / damped
    return page + exp(-decay * e) * (a * cos(damped * e) + b * sin(damped * e))
}

/**
 * The clock of a step's preview. The card that lost the focus stays as it was and fades; the
 * third one is hidden.
 */
internal fun previewAt(step: Int, focus: Focus, t: Double): Preview {
    val since = t - focus.at
    if (step == stepOf(focus.page)) return Preview(since, 1f, focus.warm)
    if (step == focus.was) return Preview(focus.held, (1 - clamp01(since / LEAVE_FADE)).toFloat(), false)
    return Preview(0.0, 0f, false)
}

/** One part coming in, [delay] after the others started. */
internal fun enter(e: Double, delay: Double, shift: Double, warm: Boolean, still: Boolean): Entrance {
    if (warm) return Entrance(1f, 0f)
    val since = e - ENTER_AFTER - delay
    if (still) return Entrance(clamp01(since / FADE).toFloat(), 0f)
    return Entrance(clamp01(since / ENTER_FADE).toFloat(), (shift * (1 - easeOut(since / ENTER_RISE))).toFloat())
}

/** Step 1: the row the white fill is on, 0 to 3; between rows while it slides. */
internal fun rowAt(e: Double, from: Double, pick: Pick, t: Double, still: Boolean): Double {
    if (pick.row >= 0) {
        return if (still) pick.row.toDouble() else pick.from + (pick.row - pick.from) * easeInOut((t - pick.at) / ROW_SLIDE)
    }
    val steps = min(3, max(0, floor((e - ROW_FIRST) / ROW_EVERY).toInt() + 1)).toDouble()
    // A card that starts over brings its fill back to the first row.
    if (steps == 0.0) return if (still) 0.0 else from * (1 - easeInOut(e / ROW_SLIDE))
    if (still) return steps
    return steps - 1 + easeInOut((e - ROW_FIRST - (steps - 1) * ROW_EVERY) / ROW_SLIDE)
}

/** Step 2: how far the front lesson card is lifted. */
internal fun liftAt(e: Double, still: Boolean) = if (still) 0.0 else LIFT * bump((e - LIFT_AFTER) / LIFT_FOR)

/** Step 3: the panel's opacity and scale. */
internal fun panelAt(e: Double, warm: Boolean, still: Boolean): PanelPose {
    if (warm) return PanelPose(1f, 1f)
    val since = e - ENTER_AFTER
    if (still) return PanelPose(clamp01(since / FADE).toFloat(), 1f)
    return PanelPose(smooth(since, 0.0, PANEL_IN).toFloat(), (PANEL_SCALE + (1 - PANEL_SCALE) * easeOut(since / PANEL_IN)).toFloat())
}

/**
 * Step 3: one square. [order] is its place among the squares that tick by themselves (-1: it
 * stays empty), [toggled] the time a finger last changed it: positive for checked, negative for
 * cleared, 0 for never.
 */
internal fun tickAt(e: Double, order: Int, toggled: Double, t: Double, still: Boolean): Tick {
    var q = 0.0
    var rising = true
    if (toggled > 0) {
        q = clamp01((t - toggled) / TICK_FOR)
    } else if (toggled < 0) {
        q = 1 - clamp01((t + toggled) / UNTICK_FOR)
        rising = false
    } else if (order >= 0) {
        q = clamp01((e - TICK_FIRST - order * TICK_EVERY) / TICK_FOR)
    }
    return Tick(q.toFloat(), if (still || !rising) 1f else (1 + TICK_POP * sin(PI * q)).toFloat())
}

/** Step 3: the badge's scale; it pulses once after the last square that ticks by itself. */
internal fun pulseAt(e: Double, ticks: Int, still: Boolean): Float {
    if (still) return 1f
    val from = TICK_FIRST + max(0, ticks - 1) * TICK_EVERY + TICK_FOR + PULSE_AFTER
    return (1 + PULSE * bump((e - from) / PULSE_FOR)).toFloat()
}

/** How far the headline stands off its place. */
internal fun parallaxAt(pull: Pull, t: Double, still: Boolean): Float {
    if (still) return 0f
    val back = if (pull.released < 0) 1.0 else 1 - easeOut((t - pull.released) / PARALLAX_BACK)
    return (-PARALLAX * sin(PI / 2 * max(-1.0, min(1.0, pull.x / PITCH))) * back).toFloat()
}
