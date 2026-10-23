package com.ngocdevv.nativecook.blocks.onboarding.spirographpager

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Every figure is the same RINGS ellipses in a different arrangement, so one figure can turn into
// any other: each ellipse travels from its place in the one to its place in the other.

/** Number of ellipses in a figure. */
const val RINGS = 24

/**
 * A figure: RINGS ellipses of six numbers each. x and y of the centre from the centre of the
 * artwork, width, height, opacity, and how much the ellipse takes part in the turning (1 for a
 * meridian of a sphere, whose width follows the turn).
 */
typealias Figure = List<FloatArray>

/**
 * @param figure What the page shows when it arrives.
 * @param settled What that turns into a moment later, if anything.
 */
class SpirographPage(val title: String, val figure: Figure, val settled: Figure? = null)

/** A change of page: the rings travel for MOVE seconds, the texts for TEXT_MOVE. */
internal const val MOVE = 0.9f
internal const val TEXT_MOVE = 0.5f
internal const val TEXT_SLIDE = 60f

/** A page that has a settled figure waits SETTLE_AFTER, then takes SETTLE to turn into it. */
internal const val SETTLE_AFTER = 0.6f
internal const val SETTLE = 0.9f

/** Each ring starts this share of a change after the one before it. */
internal const val STAGGER = 0.016f

/** A drag of this many dp is a whole change; let go past COMMIT of it and the page changes. */
internal const val DRAG = 260f
internal const val COMMIT = 0.4f

// At rest the artwork breathes, and the spheres turn about their vertical axis. Both come back to
// where they started every LOOP seconds.
internal const val BREATH = 0.02f
internal const val BREATH_PERIOD = 2.5f

/** Degrees a second. */
internal const val SPIN = 36f

/** The spheres' meridians are not caught edge-on at rest. */
internal const val SPIN_START = 0.12f

/** autoPlay: next page at each of NEXT_AT, back to the first at HOME_AT, every LOOP seconds. */
internal const val LOOP = 10f
internal val NEXT_AT = floatArrayOf(2.3f, 4.5f, 7.2f)
internal const val HOME_AT = LOOP - MOVE

/** After a touch autoPlay waits this long before it starts over from the first page. */
internal const val USER_HOLD = 3f

internal fun clamp01(t: Float) = t.coerceIn(0f, 1f)

internal fun ease(t: Float): Float {
    val u = clamp01(t)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

/** How far ring [k] is through a change that is [t] of the way: the rings leave one after another. */
internal fun share(t: Float, k: Int) = ease((t - k * STAGGER) / (1f - (RINGS - 1) * STAGGER))

/** How long a run to a figure and on to its settled form takes. */
internal fun runLength(settles: Boolean) = if (settles) MOVE + SETTLE_AFTER + SETTLE else MOVE

/**
 * Value [i] of ring [k], [r] seconds into a run: first from where the rings were ([from], a flat
 * list of RINGS × 6 numbers) to [first], then, after a wait, from [first] to [second].
 */
internal fun ringValue(from: FloatArray, first: Figure, second: Figure, r: Float, k: Int, i: Int): Float {
    if (r < MOVE) {
        val start = from[k * 6 + i]
        return start + (first[k][i] - start) * share(r / MOVE, k)
    }
    val start = first[k][i]
    return start + (second[k][i] - start) * share((r - MOVE - SETTLE_AFTER) / SETTLE, k)
}

/** The turn of ring [k] about the vertical axis: the meridians of a sphere are an eighth of a half turn apart. */
internal fun phase(k: Int) = (k / 2) * PI.toFloat() / 8f + (k % 2) * (PI.toFloat() / 16f)

/** The width a turning ring shows, as a share of its own, [clock] seconds after the block appeared. */
internal fun turned(k: Int, spin: Float, clock: Float) = 1f - spin * (1f - abs(cos(SPIN_START + SPIN * PI.toFloat() * clock / 180f + phase(k))))

internal fun breath(clock: Float) = 1f + BREATH * sin(2f * PI.toFloat() * clock / BREATH_PERIOD)

/** How far the texts of a page that is leaving or arriving have got: 0 to 1. */
internal fun textMoved(r: Float) = ease(r / TEXT_MOVE)

// The figures.

private fun figure(ring: (Int) -> FloatArray): Figure = List(RINGS, ring)

/** The opacity that makes [count] rings lying on each other read as one line of opacity [total]. */
private fun stacked(count: Int, total: Float) = 1f - (1f - total).pow(1f / count)

/** One small circle: every ring lies on it. */
val Dot = figure { floatArrayOf(0f, 0f, 54f, 54f, stacked(RINGS, 0.6f), 0f) }

/** Fourteen circles from 24 to 190 dp, each a little higher than the one inside it: a look into a cone. */
val Cone = figure { k ->
    val j = min(k, 13).toFloat()
    val size = 24f + 166f * j / 13f
    floatArrayOf(0f, 13f - 2f * j, size, size, if (k < 14) 0.7f - 0.35f * j / 13f else 0f, 0f)
}

/** Two wire spheres, one on the other: eight meridians each, and their outlines. */
val Spheres = figure { k ->
    val y = if (k % 2 == 0) -95f else 95f
    if (k < 16) floatArrayOf(0f, y, 190f, 190f, 0.5f, 1f) else floatArrayOf(0f, y, 190f, 190f, if (k < 18) 0.35f else 0f, 0f)
}

/** Three sets of eight circles, 40 to 200 dp, 30 dp apart. */
val TreeRings = figure { k ->
    val j = (k / 3).toFloat()
    val size = 40f + 160f * j / 7f
    floatArrayOf(0f, (k % 3 - 1) * 30f, size, size, 0.6f - 0.3f * j / 7f, 0f)
}

/** The same rings squashed into a lens: the middle reads as a dark almond. */
val Lens = figure { k ->
    val j = (k / 3).toFloat()
    floatArrayOf(0f, (k % 3 - 1) * 10f, 260f, 8f + 112f * j / 7f, 0.6f - 0.25f * j / 7f, 0f)
}

/** Thirteen ellipses on the vertical axis, wide at the ends and narrow in the middle. */
val Hourglass = figure { k ->
    val j = (k % 13).toFloat()
    val waist = abs(2f * j / 12f - 1f)
    floatArrayOf(0f, -100f + 200f * j / 12f, 60f + 200f * waist, 16f + 54f * waist, if (k < 13) 0.55f else 0f, 0f)
}

/** The hourglass without its middle: two bowls. */
val Bowls = figure { k ->
    val j = k % 13
    val ring = Hourglass[k]
    val y = ring[1] + if (j < 5) -14f else if (j > 7) 14f else 0f
    floatArrayOf(0f, y, ring[2], ring[3], if (k < 13 && (j < 5 || j > 7)) 0.55f else 0f, 0f)
}

/** Four by six ellipses that touch their neighbours, like chain mail. */
val Lattice = figure { k -> floatArrayOf((k % 4 - 1.5f) * 46f, (k / 4 - 2.5f) * 30f, 46f, 30f, 0.5f, 0f) }

/** Three circles side by side, eight rings on each. */
val Trio = figure { k -> floatArrayOf((k % 3 - 1) * 54f, 0f, 54f, 54f, stacked(RINGS / 3, 0.6f), 0f) }

/** One large, faint circle. */
val Halo = figure { floatArrayOf(0f, 0f, 170f, 170f, stacked(RINGS, 0.2f), 0f) }

val SamplePages = listOf(
    SpirographPage("Promotion strategy\nand development", Dot, Cone),
    SpirographPage("Rebate fulfillment", Spheres),
    SpirographPage("Loyalty programs", TreeRings, Lens),
    SpirographPage("Sweepstakes\nand Contests", Hourglass, Bowls),
    SpirographPage("Customer service", Lattice, Trio),
    SpirographPage("Channel Incentives", Halo),
)
