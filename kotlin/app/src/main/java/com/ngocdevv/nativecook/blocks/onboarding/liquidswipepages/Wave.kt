package com.ngocdevv.nativecook.blocks.onboarding.liquidswipepages

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The wave: the edge of the next page, which waits behind the right side of the screen as a bump
// and is pulled across it. Everything here is a function of the time, so that a frame can be
// drawn from what was last decided (a grab, a release) without anything being stored per frame.

/** autoPlay: one page every PERIOD seconds; with three pages the screen is back at its start after LOOP. */
internal const val PERIOD = 3.2
internal const val LOOP = 9.6

// Within a period: the bump rests, a finger that is not there drags it out, lets go, and the
// next page settles for LEAD seconds before its own period starts.
internal const val DRAG_AT = 0.7
internal const val DRAG_FOR = 0.9
internal const val RELEASE = 0.8
internal const val LEAD = PERIOD - DRAG_AT - DRAG_FOR - RELEASE

/** After a touch autoPlay waits this long before it goes back to the first page and starts over. */
internal const val USER_HOLD = 3.0

/** What moves by itself (the bump's breath, the artwork) repeats on this period, which divides PERIOD. */
internal const val AMBIENT = 1.6

// The strip of the next page along the right edge, and the bump it swells into: how far the tip
// reaches into the page, half its height, how much deeper it breathes, and where it sits.
internal const val STRIP = 8.0
internal const val BUMP = 46.0
private const val BUMP_SPREAD = 95.0
private const val BREATH = 6.0
internal const val BUMP_Y = 0.604

// The breath is gone once the tip has been pulled this far.
private const val BREATH_OUT = 40.0

/** The arrow's centre, measured from the tip. */
internal const val ARROW_INSET = 20.0

// While it is dragged the tip stays under the finger, the straight part follows at a third of
// the finger's speed, and the bump grows taller by this much for each dp of travel.
private const val EDGE_FOLLOW = 0.3
private const val SPREAD_GAIN = 2.4

// The shoulders of the bump: how long the curve's handles are at the edge and at the tip, as a
// share of half the bump's height.
private const val K_EDGE = 0.45
private const val K_TIP = 0.45

// Let go: the straight part reaches the left side in SWEEP seconds, and the bulge springs flat.
private const val SWEEP = 0.5
private val Settle = Spring(stiffness = 100.0, damping = 12.0)
private const val FLAT_FROM = 0.6

// Meanwhile the bump shrinks to this share of the screen's height, so that the dent is a bulge too.
private const val DENT_SPREAD = 0.42
private const val ARROW_OUT = 0.15

// The next page's content comes in at the end of the wave.
private const val REVEAL_FROM = RELEASE - 0.35
private const val REVEAL_TO = RELEASE - 0.05

// A new bump grows out of the edge with a spring, the strip and the arrow with it.
private val Grow = Spring(stiffness = 300.0, damping = 20.0)
private const val STRIP_IN = 0.2
private const val ARROW_FROM = 0.15
private const val ARROW_TO = 0.45

// The bump slides to the height of the finger that took it.
private const val GRAB = 0.18

// Let go too early: the wave springs back.
private val Back = Spring(stiffness = 260.0, damping = 22.0)
private const val BACK_FOR = 0.6
private const val BACK_SLIDE = 0.3

/** A release completes when the finger has travelled this share of the width, or flicks left. */
internal const val COMPLETE_AT = 0.4
internal const val FLICK = 600.0

// A wave that plays by itself pulls the tip to this share of the width before it lets go.
private const val AUTO_TIP = 0.4

/** A tap on the arrow drags for this long; Skip is shorter and its release twice as fast. */
internal const val TAP_DRAG = 0.45
internal const val SKIP_DRAG = 0.2
internal const val SKIP_SPEED = 2.0

// Reduced motion: the next page fades in over the old one.
private const val FADE = 0.3

internal enum class Mode { Rest, Hand, Script, LetGo, Backing }

private class Spring(val stiffness: Double, val damping: Double)

/**
 * What was last decided. [travel] and [cy] are the finger's, or where a release or a return started.
 *
 * @param t0 When the mode began, in seconds on the screen's clock.
 */
internal data class Wave(
    val mode: Mode,
    val page: Int,
    val target: Int,
    val t0: Double,
    val dragFor: Double = 0.0,
    val speed: Double = 1.0,
    val travel: Double = 0.0,
    val cy: Double = 0.0,
    val cyFrom: Double = 0.0,
)

/**
 * One frame: which pages show, and the wave between them.
 *
 * @param incoming The page coming in over the wave, or -1.
 * @param tint The wave takes the colour of the page after [page], blended by [mix] into that of this page.
 */
internal class Shape(
    val page: Int,
    val incoming: Int,
    val tint: Int,
    val mix: Double,
    val reach: Double,
    val depth: Double,
    val cy: Double,
    val spread: Double,
    val arrow: Double,
    val reveal: Double,
    val fade: Double,
)

internal class Held(val travel: Double, val cy: Double)

private fun clamp01(t: Double) = min(1.0, max(0.0, t))

private fun smooth(t: Double, from: Double, to: Double): Double {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3 - 2 * u)
}

private fun easeOut(t: Double) = 1 - (1 - t) * (1 - t) * (1 - t)

private fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/** How far a spring of mass 1 has come from 0 to 1 after [t] seconds; it overshoots. */
private fun spring(t: Double, spring: Spring): Double {
    if (t <= 0) return 0.0
    val w = sqrt(spring.stiffness)
    val z = spring.damping / (2 * w)
    val wd = w * sqrt(1 - z * z)
    return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
}

internal fun resting(page: Int, since: Double) = Wave(Mode.Rest, page, page, since)

/** A wave that plays by itself from [at]: dragged out for [dragFor] seconds, then let go. */
internal fun script(page: Int, target: Int, at: Double, dragFor: Double, speed: Double) =
    Wave(Mode.Script, page, target, at, dragFor, speed)

/** The wave once what was started has run out: a page that arrived, or a bump that is back. */
internal fun settle(w: Wave, now: Double): Wave {
    if (w.mode == Mode.Script || w.mode == Mode.LetGo) {
        val end = w.t0 + (if (w.mode == Mode.Script) w.dragFor else 0.0) + RELEASE / w.speed
        return if (now >= end) resting(w.target, end) else w
    }
    if (w.mode == Mode.Backing && now >= w.t0 + BACK_FOR) return resting(w.page, w.t0 + BACK_FOR - LEAD)
    return w
}

/** Where the finger is, real or not: how far it has pulled the tip, and at which height. */
internal fun held(w: Wave, now: Double, width: Double, height: Double, still: Boolean): Held {
    val restY = height * BUMP_Y
    return when (w.mode) {
        Mode.Hand -> Held(w.travel, w.cyFrom + (w.cy - w.cyFrom) * smooth(now - w.t0, 0.0, GRAB))
        Mode.Backing -> Held(w.travel * (1 - spring(now - w.t0, Back)), w.cy + (restY - w.cy) * smooth(now - w.t0, 0.0, BACK_SLIDE))
        Mode.Script -> {
            val full = if (still) 0.0 else width * (1 - AUTO_TIP) - STRIP - BUMP
            Held(full * easeInOut(clamp01((now - w.t0) / w.dragFor)), restY)
        }
        Mode.LetGo -> Held(w.travel, w.cy)
        Mode.Rest -> Held(0.0, restY)
    }
}

internal fun resolve(wave: Wave, now: Double, width: Double, height: Double, count: Int, still: Boolean): Shape {
    val w = settle(wave, now)
    val breath = if (still) 0.0 else BREATH * (0.5 - 0.5 * cos(2 * PI * now / AMBIENT))
    if (w.mode == Mode.Rest) {
        val tau = now - w.t0
        return Shape(
            page = w.page, incoming = -1, tint = w.page, mix = 0.0,
            reach = STRIP * (if (still) 1.0 else clamp01(tau / STRIP_IN)),
            depth = (BUMP + breath) * (if (still) 1.0 else spring(tau, Grow)),
            cy = height * BUMP_Y, spread = BUMP_SPREAD,
            arrow = smooth(tau, ARROW_FROM, ARROW_TO), reveal = 0.0, fade = 0.0,
        )
    }
    val from = held(w, now, width, height, still)
    val travel = from.travel
    val reach = STRIP + EDGE_FOLLOW * travel
    val depth = BUMP + breath * (1 - clamp01(travel / BREATH_OUT)) + (1 - EDGE_FOLLOW) * travel
    val spread = BUMP_SPREAD + SPREAD_GAIN * travel
    // Seconds since the wave was let go; negative while it is held.
    val u = when (w.mode) {
        Mode.LetGo -> (now - w.t0) * w.speed
        Mode.Script -> (now - w.t0 - w.dragFor) * w.speed
        else -> -1.0
    }
    if (u < 0) {
        val mix = if (w.mode == Mode.Script) clamp01((now - w.t0) / w.dragFor) else 0.0
        return Shape(w.page, -1, w.target, mix, reach, depth, from.cy, spread, arrow = 1.0, reveal = 0.0, fade = 0.0)
    }
    if (still) {
        val fade = smooth(u, 0.0, FADE)
        return Shape(w.page, w.target, w.target, 1.0, reach, depth, from.cy, spread, arrow = 1 - fade, reveal = fade, fade = fade)
    }
    val sweep = easeOut(clamp01(u / SWEEP))
    return Shape(
        page = w.page, incoming = w.target, tint = w.target, mix = 1.0,
        reach = reach + (width - reach) * sweep,
        // The bulge overshoots into a dent, a last sliver of the old page at the left side.
        depth = depth * (1 - spring(u, Settle)) * (1 - smooth(u, FLAT_FROM, RELEASE)),
        cy = from.cy,
        spread = spread + (min(spread, height * DENT_SPREAD) - spread) * sweep,
        arrow = 1 - clamp01(u / ARROW_OUT),
        reveal = smooth(u, REVEAL_FROM, REVEAL_TO), fade = 0.0,
    )
}

/** The next page's side of the wave as one closed path in dp: rebuilt for every frame. */
internal fun Path.outline(s: Shape, width: Float, height: Float) {
    val ex = width - s.reach.toFloat()
    val tx = ex - s.depth.toFloat()
    val cy = s.cy.toFloat()
    val spread = s.spread.toFloat()
    val top = cy - spread
    val bottom = cy + spread
    val above = min(top, -1f)
    val below = max(bottom, height + 1f)
    rewind()
    moveTo(width + 1f, above)
    lineTo(ex, above)
    lineTo(ex, top)
    cubicTo(ex, top + spread * K_EDGE.toFloat(), tx, cy - spread * K_TIP.toFloat(), tx, cy)
    cubicTo(tx, cy + spread * K_TIP.toFloat(), ex, bottom - spread * K_EDGE.toFloat(), ex, bottom)
    lineTo(ex, below)
    lineTo(width + 1f, below)
    close()
}
