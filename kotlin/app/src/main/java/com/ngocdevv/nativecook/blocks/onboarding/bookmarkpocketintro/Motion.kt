package com.ngocdevv.nativecook.blocks.onboarding.bookmarkpocketintro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The stage is drawn for a screen 393 by 852 dp and scaled to the screen's width; every pose is a
// function of the time since something happened, so a touch and the loop that plays by itself
// make exactly the same pictures. Lengths are in the stage's dp, times in seconds.

/** autoPlay: three cards are fed, the bookmark is tapped, and the screen goes back to rest. */
internal const val LOOP = 9.0
internal val AUTO_FEEDS = doubleArrayOf(0.0, 0.3, 0.6)
internal const val AUTO_ORBIT = 1.2
internal const val AUTO_REST = 7.5

/** After a touch autoPlay waits this long before it goes back to rest and starts over. */
internal const val USER_HOLD = 3.0

internal const val STAGE_WIDTH = 393.0
internal const val STAGE_HEIGHT = 852.0
internal const val CENTER_X = STAGE_WIDTH / 2
internal const val BOOK_WIDTH = 177.0
internal const val BOOK_HEIGHT = 213.0
internal const val BOOK_TOP = 320.0

// The hub of the orbits, and the bookmark there: 70 dp wide and leaning to the right.
internal const val ORBIT_Y = 355.0
internal const val BOOK_SMALL = 70 / BOOK_WIDTH
internal const val TILT = 15.0
internal val RINGS = doubleArrayOf(107.5, 182.5)

// Degrees a second: the inner ring turns clockwise, the outer one the other way.
internal val SPINS = doubleArrayOf(6.0, -4.0)

// A card let go with its lower edge at the top of the stage reaches the pocket 0.45 s later.
internal const val GRAVITY = 3160.0

// Near the pocket a card is smaller and darker; a card dragged aside is pulled back over it.
internal const val CARD_SHRINK = 0.85
internal const val CARD_DARK = 0.25
internal const val SHRINK_FROM = 100.0
internal const val SUCK = 0.2

// How much of the waiting card shows under the top edge, and how it comes and goes.
internal const val PEEK = 106.0
internal const val WAIT_IN = 0.3
internal const val WAIT_OUT = 0.2
internal const val WAIT_AFTER = 0.25
internal const val BACK = 0.25

// A waiting card pulled down by less than this goes back; by more, it falls in.
internal const val FEED_AFTER = 40.0

// The gulp: the sides bow out by BULGE and the top edge dips, then both swing back once.
internal const val BULGE = 9.5
internal const val DIP = 4.0
internal const val GULP_DECAY = 9.0
internal const val GULP_SPEED = 13.0
private val GulpPeak = atan(GULP_SPEED / GULP_DECAY) / GULP_SPEED
private val GulpGain = 1 / (exp(-GULP_DECAY * GulpPeak) * sin(GULP_SPEED * GulpPeak))
internal const val GLOW_IN = 0.12
internal const val GLOW_HOLD = 0.2
internal const val GLOW_OUT = 0.5

// To the orbits: the bookmark shrinks, springs up towards the viewer as it leans, and drops to
// the hub while the rings open and the icons fly out one after another.
internal const val SHRINK = 0.4
internal const val SHRUNK = 0.62
internal const val LIFT = 0.4
internal const val LIFTED = 1.5
internal const val LIFT_STIFFNESS = 324.0
internal const val LIFT_DAMPING = 18.0
internal const val DROP = 0.5
internal const val ORBIT_IN = SHRINK + LIFT + DROP
internal const val RINGS_IN = 0.4
internal const val ICON_STAGGER = 0.04
internal const val ICON_OUT = 0.35
internal const val ICON_STIFFNESS = 400.0
internal const val ICON_DAMPING = 22.0

// Back to the pocket: icons and rings fall into the bookmark, which grows and straightens.
internal const val COLLAPSE = 0.4
internal const val COLLAPSE_STAGGER = 0.02
internal const val GROW_AFTER = 0.3
internal const val GROW = 0.5
internal const val POCKET_IN = GROW_AFTER + GROW

// Without motion one picture fades out and the other in.
internal const val SWAP = 0.3

// Characters a second; each letter is up to TYPE_JITTER of a letter early or late.
internal const val TYPE_AFTER = 0.2
internal const val TYPE_RATE = 28.0
internal const val TYPE_JITTER = 0.35
internal const val DELETE_RATE = 60.0
internal const val BLINK = 0.5
internal const val LINE_SHIFT = 0.2

/** Which picture is up, since when, and how long the rings had been turning by then. */
internal data class Mode(val orbit: Boolean, val at: Double, val spun: Double)

/** A card let go: when, where its lower edge was, how far aside, and how strongly the pocket glows for it. */
internal data class Drop(val at: Double = -100.0, val bottom: Double = 0.0, val dx: Double = 0.0, val glow: Double = 0.0)

/** The card waiting at the top edge: whether it is there, and since when. */
internal data class Wait(val on: Boolean, val at: Double)

/** How far a finger has pulled the waiting card; [at] is when it was let go without being fed. */
internal data class Hold(val dx: Double = 0.0, val dy: Double = 0.0, val at: Double = -100.0)

/** The tagline goes from one text to another from [at] on. */
internal data class Tagline(val from: String, val to: String, val at: Double)

/** The bookmark: its scale, its lean in degrees, how far it is on its way to the hub, its opacity. */
internal class BookmarkPose(val scale: Float, val tilt: Float, val lift: Float, val opacity: Float)

internal class IconPose(val x: Float, val y: Float, val scale: Float, val opacity: Float)

internal class CardPose(val x: Float, val bottom: Float, val scale: Float, val dark: Float, val opacity: Float)

internal class Gulp(val bulge: Float, val glow: Float)

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

/** A spring of mass 1 from 0 to 1, t seconds after it was let go. */
internal fun spring(t: Double, stiffness: Double, damping: Double): Double {
    if (t <= 0) return 0.0
    val omega = sqrt(stiffness)
    val zeta = damping / (2 * omega)
    val damped = omega * sqrt(1 - zeta * zeta)
    return 1 - exp(-zeta * omega * t) * (cos(damped * t) + (zeta * omega / damped) * sin(damped * t))
}

/** A number between 0 and 1 that is always the same for the same n. */
internal fun hash(n: Int): Double {
    val x = sin(n * 127.1 + 311.7) * 43758.5453
    return x - floor(x)
}

/** With reduced motion: whether the orbits show `e` seconds after a change, and how opaque the picture is. */
private fun swapped(orbit: Boolean, e: Double): Pair<Boolean, Double> {
    val q = clamp01(e / SWAP)
    return (if (q >= 0.5) orbit else !orbit) to abs(1 - 2 * q)
}

internal fun bookmarkPose(mode: Mode, t: Double, still: Boolean): BookmarkPose {
    val e = t - mode.at
    if (still) {
        val (orbit, opacity) = swapped(mode.orbit, e)
        return BookmarkPose(if (orbit) BOOK_SMALL.toFloat() else 1f, if (orbit) TILT.toFloat() else 0f, if (orbit) 1f else 0f, opacity.toFloat())
    }
    if (mode.orbit) {
        val up = spring(e - SHRINK, LIFT_STIFFNESS, LIFT_DAMPING)
        val raised = 1 + (SHRUNK - 1) * easeInOut(e / SHRINK) + (LIFTED - SHRUNK) * up
        return BookmarkPose(
            scale = (raised + (BOOK_SMALL - raised) * easeInOut((e - SHRINK - LIFT) / DROP)).toFloat(),
            tilt = (TILT * up).toFloat(),
            lift = easeInOut((e - SHRINK) / (LIFT + DROP)).toFloat(),
            opacity = 1f,
        )
    }
    val grown = easeInOut((e - GROW_AFTER) / GROW)
    return BookmarkPose((BOOK_SMALL + (1 - BOOK_SMALL) * grown).toFloat(), (TILT * (1 - grown)).toFloat(), (1 - grown).toFloat(), 1f)
}

/** How open the rings are, 0 to 1. */
internal fun ringsAt(mode: Mode, t: Double, still: Boolean): Float {
    val e = t - mode.at
    if (still) {
        val (orbit, opacity) = swapped(mode.orbit, e)
        return if (orbit) opacity.toFloat() else 0f
    }
    return (if (mode.orbit) easeOut((e - SHRINK - LIFT) / RINGS_IN) else 1 - easeIn(e / COLLAPSE)).toFloat()
}

/** One icon: its offset from the hub, its scale and its opacity. [turn] is what a finger added, in degrees. */
internal fun iconPose(index: Int, ring: Int, angle: Double, mode: Mode, t: Double, turn: Double, still: Boolean): IconPose {
    val e = t - mode.at
    if (still) {
        val (orbit, opacity) = swapped(mode.orbit, e)
        val at = (angle + turn) * PI / 180
        return IconPose((RINGS[ring] * cos(at)).toFloat(), (RINGS[ring] * sin(at)).toFloat(), 1f, if (orbit) opacity.toFloat() else 0f)
    }
    val out: Double
    val scale: Double
    val spun: Double
    if (mode.orbit) {
        val since = e - SHRINK - LIFT - index * ICON_STAGGER
        out = easeOut(since / ICON_OUT)
        scale = spring(since, ICON_STIFFNESS, ICON_DAMPING)
        spun = max(0.0, e - SHRINK - LIFT)
    } else {
        out = 1 - easeIn((e - index * COLLAPSE_STAGGER) / (COLLAPSE - 8 * COLLAPSE_STAGGER))
        scale = out
        spun = mode.spun + e
    }
    val at = (angle + turn + SPINS[ring] * spun) * PI / 180
    return IconPose((RINGS[ring] * out * cos(at)).toFloat(), (RINGS[ring] * out * sin(at)).toFloat(), scale.toFloat(), smooth(scale, 0.0, 0.15).toFloat())
}

/** When the lower edge of a dropped card reaches the pocket. */
private fun gulpStart(drop: Drop) = drop.at + sqrt(max(0.0, 2 * (BOOK_TOP - drop.bottom) / GRAVITY))

/** How far the bookmark's sides bow out, in dp (the top edge dips in proportion), and how strong the glow under it is. */
internal fun gulpAt(drops: List<Drop>, t: Double, still: Boolean): Gulp {
    var bulge = 0.0
    var glow = 0.0
    for (drop in drops) {
        val u = t - gulpStart(drop)
        if (u <= 0) continue
        bulge += BULGE * GulpGain * exp(-GULP_DECAY * u) * sin(GULP_SPEED * u)
        glow += drop.glow * smooth(u, 0.0, GLOW_IN) * (1 - smooth(u, GLOW_HOLD, GLOW_HOLD + GLOW_OUT))
    }
    return Gulp(if (still) 0f else bulge.toFloat(), min(1.0, glow).toFloat())
}

/** A card's scale for the place of its lower edge. */
internal fun cardScale(bottom: Double) = 1 - (1 - CARD_SHRINK) * smooth(bottom, SHRINK_FROM, BOOK_TOP)

/**
 * One card: where its lower edge is, how far aside it is, its scale about that edge, how dark it
 * is and its opacity. [top] is the stage's y at the top of the screen; [waiting] says the card is
 * the one at the top edge.
 */
internal fun cardPose(height: Double, waiting: Boolean, wait: Wait, hold: Hold, drop: Drop, top: Double, t: Double, still: Boolean): CardPose {
    val shown = if (wait.on) easeOut((t - wait.at) / WAIT_IN) else 1 - easeIn((t - wait.at) / WAIT_OUT)
    if (waiting && shown > 0) {
        // Pulled by a finger, or on its way back after being let go too early.
        val pull = 1 - easeOut((t - hold.at) / BACK)
        val bottom = top + PEEK * (if (still) 1.0 else shown) + hold.dy * pull
        val scale = cardScale(bottom)
        return CardPose((hold.dx * pull).toFloat(), bottom.toFloat(), scale.toFloat(), (CARD_DARK * (1 - scale) / (1 - CARD_SHRINK)).toFloat(), if (still) shown.toFloat() else 1f)
    }
    val e = t - drop.at
    if (e < 0) return CardPose(0f, top.toFloat(), 1f, 0f, 0f)
    // Without motion a card that was fed fades where it is.
    if (still) return CardPose(drop.dx.toFloat(), drop.bottom.toFloat(), cardScale(drop.bottom).toFloat(), 0f, (1 - clamp01(e / SUCK)).toFloat())
    val bottom = drop.bottom + GRAVITY * e * e / 2
    val scale = cardScale(bottom)
    // Gone once its upper edge is behind the bookmark.
    val opacity = if (bottom - height * scale < BOOK_TOP + 10) 1f else 0f
    return CardPose((drop.dx * (1 - easeOut(e / SUCK))).toFloat(), bottom.toFloat(), scale.toFloat(), (CARD_DARK * (1 - scale) / (1 - CARD_SHRINK)).toFloat(), opacity)
}

/** When the tagline has finished changing. */
internal fun typedBy(tagline: Tagline): Double {
    val kept = tagline.from.commonPrefixWith(tagline.to).length
    return tagline.at + (tagline.from.length - kept) / DELETE_RATE + (tagline.to.length - kept + TYPE_JITTER) / TYPE_RATE
}

/** The tagline as it reads at t: the old text is deleted from its end down to what the two share, then the new one is typed. */
internal fun typedAt(tagline: Tagline, t: Double, still: Boolean): String {
    val e = t - tagline.at
    if (e < 0) return tagline.from
    if (still) return tagline.to
    val kept = tagline.from.commonPrefixWith(tagline.to).length
    val deleting = (tagline.from.length - kept) / DELETE_RATE
    if (e < deleting) return tagline.from.take(tagline.from.length - (e * DELETE_RATE).toInt())
    // Letter n is due at (n + its own lateness) / TYPE_RATE.
    val due = (e - deleting) * TYPE_RATE
    var count = max(0, floor(due).toInt() - 1)
    while (count < tagline.to.length - kept && count + (hash(count) * 2 - 1) * TYPE_JITTER <= due) count++
    return tagline.to.take(kept + count)
}
