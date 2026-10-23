package com.ngocdevv.nativecook.blocks.playground.fractaltreebloom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Growth, in levels of branches per second (the tree has MAX_DEPTH + 1 levels), and the rate the
// tree draws back into its seed when replanted.
private const val GROW_RATE = 1.9
private const val RECEDE_RATE = 6.0
// Blossoms open over BLOOM_TIME s once the tree is grown, each a little after the last.
private const val BLOOM_TIME = 1.3
// One season to the next, s.
private const val SEASON_TIME = 1.8
// Dragging this far across bends the wind fully; past it the drag gives less and less.
private const val WIND_DRAG = 150.0
private const val WIND_MAX = 1.6
// The mound: its height and spread (share of the width), above the bottom inset.
private const val MOUND_HEIGHT = 26.0
private const val MOUND_SPREAD = 0.42
private const val GROUND_ABOVE = 64.0

private const val TICK = 1.0 / 120
private const val MAX_TICKS = 4

// autoPlay, s.
private const val LOOP = 17.4
private const val GROW_AT = 0.4
private const val GUST_FROM = 5.8
private const val GUST_TO = 7.0
private const val SUMMER_AT = 7.4
private const val AUTUMN_AT = 9.6
private const val WINTER_AT = 12.0
private const val REPLANT_AT = 15.0
private const val USER_HOLD = 4.0

// What happened during a frame, for haptics and the season's name.
internal const val EVENT_TURNED = 1
internal const val EVENT_PLANTED = 2
internal const val EVENT_SEASON = 4

/** Whether a time `at` was passed going from `before` to `t` on a looping clock. */
private fun crossed(before: Double, t: Double, at: Double) =
    (before < at && t >= at) || (t < before && (at > before || at <= t))

/**
 * The frame loop of the React Native block (its `useFrameCallback`, script and gestures), step for
 * step: growth, bloom, the season, the wind and what falls, on one fixed tick. No Android here: it
 * compiles on its own.
 */
internal class TreeEngine(auto: Boolean, val reduced: Boolean, val seed: Int) {
    var tree: Tree? = null
        private set
    val weather = Weather(seed * 7 + 1)
    val particles = Particles()
    var ground = TreeGround(0.0, 0.0, 0.0, 1.0)
        private set
    var rootX = 0.0
        private set
    var rootY = 0.0
        private set
    private var width = 0.0

    private var acc = 0.0
    /** Growth (0 … MAX_DEPTH + 1), whether it is growing (1), drawing back (−1) or still (0), and the bloom. */
    var growth = if (auto) 0.0 else (MAX_DEPTH + 1).toDouble()
        private set
    var growing = 0
        private set
    var bloom = if (auto) 0.0 else 2.0
        private set
    /** The season, and its move from one to the next. */
    var season = 0.0
        private set
    private var seasonFrom = 0.0
    private var seasonTo = 0.0
    private var seasonT = 1.0
    var shownSeason = 0
        private set
    /** The wind's drag. */
    private var dragging = false

    private var clock = 0.0
    private var hold = 0.0
    private var looping = auto

    /** What happened since the events were last read (EVENT_… bits). */
    var events = 0

    /** Restarts the autoPlay clock (autoPlay switched on or off). */
    fun setAuto(auto: Boolean) {
        looping = auto
        clock = 0.0
        hold = 0.0
    }

    /** Moves on to the next season. */
    private fun nextSeason() {
        val to = floor(seasonTo + 0.5) + 1
        seasonFrom = season
        seasonTo = to
        seasonT = 0.0
    }

    /** Draws the tree back into its seed (it grows again from there) and turns the year to spring. */
    private fun replant() {
        growing = -1
        val to = ceil(seasonTo / 4 - 1e-6) * 4
        if (to > seasonTo) {
            seasonFrom = season
            seasonTo = to
            seasonT = 0.0
        }
    }

    /** One step of the autoPlay script, from `before` to `t` s into the loop. */
    private fun script(before: Double, t: Double) {
        if (crossed(before, t, GROW_AT)) {
            growth = 0.0
            growing = 1
            bloom = 0.0
        }
        if (t >= GUST_FROM && t < GUST_TO) {
            // A finger draws the wind to the right, then lets go.
            val k = (t - GUST_FROM) / (GUST_TO - GUST_FROM)
            weather.target = 0.95 * sin(min(1.0, k * 1.6) * PI * 0.5)
        }
        if (crossed(before, t, GUST_TO)) weather.target = 0.0
        if (crossed(before, t, SUMMER_AT) || crossed(before, t, AUTUMN_AT) || crossed(before, t, WINTER_AT)) nextSeason()
        if (crossed(before, t, REPLANT_AT)) replant()
    }

    /** One frame `frameDt` s after the last one, on a screen `width` × `height` dp with `bottomInset`. */
    fun frame(frameDt: Double, width: Double, height: Double, bottomInset: Double) {
        if (width == 0.0) return
        val tree = this.tree ?: Tree(seed).also {
            val base = height - bottomInset - GROUND_ABOVE
            ground = TreeGround(base, width / 2, MOUND_HEIGHT, width * MOUND_SPREAD)
            rootX = width / 2
            rootY = ground.y(width / 2) + 2
            this.width = width
            this.tree = it
        }
        val maxGrowth = (MAX_DEPTH + 1).toDouble()
        val dt = min(frameDt, MAX_TICKS * TICK)
        var pending = acc + dt
        var ticks = 0
        while (pending >= TICK && ticks < MAX_TICKS) {
            pending -= TICK
            ticks += 1
            if (hold > 0) {
                if (!dragging) hold -= TICK
                if (hold <= 0) {
                    // Pick the loop up where the tree is: still growing from a replant, at the same
                    // point of the growth; otherwise from the replant, drawing it back to grow it again.
                    clock = if (growing > 0) GROW_AT + 0.01 + growth / GROW_RATE else REPLANT_AT - 0.01
                }
            } else if (looping) {
                val before = clock
                val t = (before + TICK) % LOOP
                clock = t
                script(before, t)
            }
            // Growth, level by level, and the bloom once it is grown.
            val g = growth
            if (growing > 0) {
                val next = if (reduced) maxGrowth else min(maxGrowth, g + GROW_RATE * TICK)
                growth = next
                if (next >= maxGrowth) growing = 0
            } else if (growing < 0) {
                val next = if (reduced) 0.0 else max(0.0, g - RECEDE_RATE * TICK)
                growth = next
                if (next <= 0) {
                    // Back in the seed: it rests a moment, then grows again (unless the script grows it).
                    growing = if (looping && hold <= 0) 0 else 2
                    particles.count = 0
                    bloom = 0.0
                }
            } else if (growing == 2) {
                growing = 1
            }
            if (growth >= MAX_DEPTH + 0.6 && growing >= 0) bloom = min(2.0, bloom + TICK / BLOOM_TIME)
            // The season's move.
            if (seasonT < 1) {
                val k = min(1.0, seasonT + TICK / (if (reduced) 0.6 else SEASON_TIME))
                seasonT = k
                season = seasonFrom + (seasonTo - seasonFrom) * treeEaseInOut(k)
                if (k >= 1 && seasonTo >= 4) {
                    // Whole years change nothing: keep the numbers small.
                    val years = floor(seasonTo / 4) * 4
                    seasonTo -= years
                    seasonFrom -= years
                    season -= years
                    for (i in 0 until tree.sites) tree.siteSeen[i] = -1.0
                }
            }
            weather.step(TICK)
            tree.layout(weather, rootX, rootY, growth, if (reduced) 0.0 else 1.0)
            particles.step(tree, weather, season, TICK, width, ground, !reduced && growing >= 0)
        }
        acc = min(pending, TICK)
        // floorMod keeps it a valid index into the season names whatever the season number does.
        val name = Math.floorMod(floor(season + 0.5).toInt(), 4)
        if (name != shownSeason) {
            shownSeason = name
            events = events or EVENT_SEASON
        }
    }

    // ─── Gestures ────────────────────────────────────────────────────────────────────────────────

    private fun touched() {
        hold = USER_HOLD
    }

    fun windStart() {
        touched()
        dragging = true
    }

    /** The drag is `translationX` dp across. */
    fun windMove(translationX: Double) {
        if (tree == null) return
        // Rubber-banded: the further the drag, the less each point adds.
        val raw = translationX / WIND_DRAG
        val sign = if (raw < 0) -1.0 else 1.0
        val a = abs(raw)
        weather.target = sign * (if (a < 1) a else 1 + (WIND_MAX - 1) * (1 - exp(-(a - 1))))
    }

    /**
     * Let go at `velocityX` dp/s: the wind springs back, carrying the finger's speed. A cancelled drag
     * springs back too (Gesture Handler's `onEnd` also runs on cancel), only without the fling.
     */
    fun windEnd(velocityX: Double, cancelled: Boolean = false) {
        if (tree != null) {
            weather.target = 0.0
            if (!cancelled && velocityX.isFinite()) weather.windSpeed += (velocityX / WIND_DRAG) * 0.35
        }
        dragging = false
    }

    fun tap() {
        touched()
        nextSeason()
        events = events or EVENT_TURNED
    }

    fun longPress() {
        touched()
        replant()
        events = events or EVENT_PLANTED
    }
}
