package com.ngocdevv.nativecook.blocks.onboarding.emberglowstory

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// Every pose is a function of the time since the page last changed, so a touch and the loop that
// plays by itself make exactly the same pictures. Times are in seconds, lengths in dp.

/** autoPlay: the button is pressed before each of these moments and let go at it. */
internal const val LOOP = 11.0
internal val AUTO_TAPS = doubleArrayOf(2.6, 5.2, 7.8, 10.2)
internal const val PRESS_FOR = 0.15

/** After a touch autoPlay waits this long before it goes back to the first page and starts over. */
internal const val USER_HOLD = 3.0

// A page leaves upwards while the next one is written word by word.
internal const val OUT = 0.3
internal const val OUT_SHIFT = 20.0
internal const val WORDS_AFTER = 0.3
internal const val WORD_STAGGER = 0.06
internal const val WORD_IN = 0.25
internal const val WORD_SHIFT = 8.0

// Once the words are there, the key words light up one group after another, each with a flash.
internal const val LIGHT_AFTER = 0.15
internal const val LIGHT_STAGGER = 0.3
internal const val LIGHT_IN = 0.25
internal const val FLASH_OUT = 0.5
internal const val POP = 0.35

// A page that continues the one before: the old words dim and move up to make room.
internal const val DIM = 0.3
internal const val SLIDE = 0.5

// Going back, or back to the first page, a sentence fades in whole.
internal const val WHOLE_IN = 0.35

// The last page's button floods the screen with the glow before the first page returns.
internal const val RETURN = 0.8
internal const val FLOOD = 0.35
internal const val FLOOD_SCALE = 3.4
internal val FLOOD_FADE = doubleArrayOf(0.3, 0.55)

// The glow changes colour over GLOW_CROSS and surges SURGE dp up the screen on the way.
internal const val GLOW_CROSS = 0.8
internal const val SURGE = 80.0
internal const val SURGE_IN = 0.18
internal const val SURGE_OUT = 0.9

// At rest its hot spot breathes and drifts; both periods divide LOOP.
internal const val BREATH = 2.2
internal const val DRIFT = 15.0
internal const val DRIFT_PERIOD = 5.5
internal const val HOT_LOW = 0.6
internal const val HOT_BREATH = 0.17

// Under a finger on the button the hot spot comes over and brightens.
internal const val HOT_PRESS = 0.23
internal const val PRESS_IN = 0.2
internal const val PRESS_OUT = 0.3

// The amber streak of the third page.
internal val AMBER = doubleArrayOf(1.0, 1.8)

/** How a page was reached: written word by word, faded in whole, or after the flood. */
internal enum class How { Forward, Whole, Flooded }

/** The page shown, the one before it, when it changed and how. */
internal data class Scene(val page: Int, val from: Int, val at: Double, val how: How)

/** The button: whether a finger is on it, since when (or when it left), and where. */
internal data class Press(val down: Boolean = false, val at: Double = -10.0, val x: Double = 0.0)

internal class BlockPose(val opacity: Float, val y: Float)

internal class WordPose(val opacity: Float = 1f, val y: Float = 0f, val lit: Float = 0f, val flash: Float = 0f, val pop: Float = 1f)

internal class GlowPose(val opacity: Float, val lift: Float, val stretch: Float)

internal class Hot(val light: Float, val x: Float)

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

/**
 * A page's block of text: its opacity and its offset. [rise] is how far a page that continues the
 * one before starts below its place; [handsOver] says the next page continues this one, so this
 * one gives way at once.
 */
internal fun blockPose(index: Int, rise: Double, handsOver: Boolean, scene: Scene, t: Double, still: Boolean): BlockPose {
    val e = t - scene.at
    if (index == scene.page) {
        if (scene.how != How.Forward) {
            val p = easeOut((e - (if (scene.how == How.Flooded) RETURN - WHOLE_IN else OUT)) / WHOLE_IN)
            return BlockPose(p.toFloat(), if (still) 0f else (WORD_SHIFT * (1 - p)).toFloat())
        }
        return BlockPose(if (e < 0 && rise > 0) 0f else 1f, if (still) 0f else (rise * (1 - easeInOut(e / SLIDE))).toFloat())
    }
    if (index != scene.from) return BlockPose(0f, 0f)
    if (handsOver && scene.how == How.Forward) return BlockPose(if (e < 0) 1f else 0f, 0f)
    val p = easeIn(e / OUT)
    // Forwards the old page leaves upwards, backwards downwards.
    val way = if (scene.page < scene.from && scene.how == How.Whole) 1 else -1
    return BlockPose((1 - p).toFloat(), if (still) 0f else (way * OUT_SHIFT * p).toFloat())
}

/**
 * One word of the page that is showing. [order] is its place among the words that are new on the
 * page (negative for a word kept from the page before), [count] how many are new, [key] its turn
 * to light up (negative for a plain word), [was] whether it was lit on the page before.
 */
internal fun wordPose(order: Int, count: Int, key: Int, was: Boolean, scene: Scene, t: Double, still: Boolean): WordPose {
    val e = t - scene.at
    if (scene.how != How.Forward) return WordPose(lit = if (key >= 0) 1f else 0f)
    if (order < 0) return WordPose(lit = if (was) (1 - smooth(e, 0.0, DIM)).toFloat() else 0f)
    val shown = easeOut((e - WORDS_AFTER - (if (still) 0.0 else order * WORD_STAGGER)) / WORD_IN)
    val y = if (still) 0f else (WORD_SHIFT * (1 - shown)).toFloat()
    if (key < 0) return WordPose(shown.toFloat(), y)
    val due = WORDS_AFTER + count * WORD_STAGGER + LIGHT_AFTER + key * LIGHT_STAGGER
    val lit = smooth(e, due, due + LIGHT_IN)
    return WordPose(
        opacity = shown.toFloat(),
        y = y,
        lit = lit.toFloat(),
        flash = if (still) 0f else (lit * (1 - smooth(e, due + LIGHT_IN, due + LIGHT_IN + FLASH_OUT))).toFloat(),
        // A glyph is a little small until its word lights up, and overshoots as it grows.
        pop = if (still) 1f else (0.8 + 0.2 * lit + 0.15 * sin(PI * clamp01((e - due) / POP))).toFloat(),
    )
}

/** A word of a page that is leaving, or of any page at rest. */
internal fun wordAtRest(key: Int) = WordPose(lit = if (key >= 0) 1f else 0f)

/**
 * One colour of the glow: its opacity, how far it has climbed, and its stretch from the bottom
 * edge. [glow] and [before] are the colours of the page showing and of the one before it.
 */
internal fun glowPose(color: Int, glow: Int, before: Int, scene: Scene, t: Double, still: Boolean): GlowPose {
    val e = t - scene.at
    val flooded = scene.how == How.Flooded
    val arrived = if (flooded) smooth(e, RETURN - 0.3, RETURN) else smooth(e, 0.0, GLOW_CROSS)
    val left = if (flooded) 1 - smooth(e, FLOOD_FADE[0], FLOOD_FADE[1]) else 1 - arrived
    val opacity = when {
        glow == before -> if (color == glow) 1.0 else 0.0
        color == glow -> arrived
        color == before -> left
        else -> 0.0
    }
    val lift = if (still || scene.how != How.Forward) 0.0 else SURGE * smooth(e, 0.0, SURGE_IN) * (1 - easeOut(e / SURGE_OUT))
    val stretch = if (!still && flooded && color == before && color != glow) 1 + (FLOOD_SCALE - 1) * easeOut(e / FLOOD) else 1.0
    return GlowPose(opacity.toFloat(), lift.toFloat(), stretch.toFloat())
}

/** The amber streak: there on a page that has it, gone soon after it is left. */
internal fun amberAt(has: Boolean, had: Boolean, scene: Scene, t: Double): Float {
    val e = t - scene.at
    if (has) return (if (scene.how == How.Forward) smooth(e, AMBER[0], AMBER[1]) else smooth(e, 0.0, GLOW_CROSS)).toFloat()
    return if (had) (1 - smooth(e, 0.2, GLOW_CROSS)).toFloat() else 0f
}

/** How much of a press the button shows, 0 to 1. */
internal fun pressAt(press: Press, t: Double) =
    if (press.down) easeOut((t - press.at) / PRESS_IN) else 1 - easeOut((t - press.at) / PRESS_OUT)

/** The hot spot: how bright it is, and where its middle is for a resting place [home]. */
internal fun hotAt(home: Double, press: Press, t: Double, still: Boolean): Hot {
    val pressed = pressAt(press, t)
    val breath = if (still) 0.5 else 0.5 - 0.5 * cos(t / BREATH * 2 * PI)
    val drift = if (still) 0.0 else DRIFT * sin(t / DRIFT_PERIOD * 2 * PI)
    return Hot((HOT_LOW + HOT_BREATH * breath + HOT_PRESS * pressed).toFloat(), (home + drift + (press.x - home - drift) * pressed).toFloat())
}
