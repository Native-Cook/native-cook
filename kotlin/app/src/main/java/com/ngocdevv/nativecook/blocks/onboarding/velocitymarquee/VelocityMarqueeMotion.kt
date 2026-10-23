package com.ngocdevv.nativecook.blocks.onboarding.velocitymarquee

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ─── Colours ─────────────────────────────────────────────────────────────────────────────────
// A tile before its photo arrives.
internal const val TILE_PAPER = 0xFFE3DED3.toInt()

// ─── Page ────────────────────────────────────────────────────────────────────────────────────
// The side margin (dp); the brand line sits HEADER_TOP below the safe top and is HEADER_H tall.
// The rows fill the space between it and the text, HERO_GAP clear of each.
internal const val MARGIN = 24f
internal const val MIN_TOP = 20f
internal const val HEADER_TOP = 6f
internal const val HEADER_H = 26f
internal const val HERO_GAP = 18f
// The text block sits FOOT_GAP above the safe bottom.
internal const val FOOT_GAP = 4f
// The paper's grain: a GRAIN_SIZE² tile of seeded noise, each texel GRAIN_TEXEL dp, with dark and
// light specks up to these opacities. Drawn once.
internal const val GRAIN_SIZE = 128
internal const val GRAIN_TEXEL = 0.5f
private const val GRAIN_DARK = 0.06
private const val GRAIN_LIGHT = 0.07
private const val GRAIN_SEED = 20261112

// ─── Rows ────────────────────────────────────────────────────────────────────────────────────
// The rows lie at TILT degrees, rising to the right, ROW_PITCH dp apart centre to centre. The three
// rows grow or shrink together with the screen, within these scales.
internal const val TILT = -8f
internal const val ROW_H = 96f
internal const val ROW_PITCH = 112f
internal const val MIN_HERO_SCALE = 0.72f
internal const val MAX_HERO_SCALE = 1.2f
// Tile widths by orientation, the gap between tiles and their corner radius, in dp.
internal const val PORTRAIT_W = 72f
internal const val LANDSCAPE_W = 134f
internal const val TILE_GAP = 12f
internal const val TILE_RADIUS = 18f
// Photos are fetched at about three times their drawn width.
internal const val PORTRAIT_PX = 240
internal const val LANDSCAPE_PX = 420
// Each photo fades in over this many seconds once it has loaded.
internal const val PHOTO_FADE = 0.45f

// ─── Speed ───────────────────────────────────────────────────────────────────────────────────
// Rows drift at DRIFT dp/s (row one leftwards, the way the pages go); a drag moves them with the
// finger and a release throws them at the finger's speed, which eases back to the drift on a
// critically damped spring of natural frequency OMEGA (1/s): v(t) = v0 (1 + ωt) e^(−ωt).
private const val DRIFT = 40.0
private const val OMEGA = 3.2
private const val MAX_THROW = 6000f
// A vertical drag moves the rows too, at this share, so a drag in any direction throws them.
private const val VERTICAL_SHARE = 0.4f
// The finger's speed while dragging is smoothed over about this many seconds.
private const val DRAG_SMOOTH = 0.06f

// The lean follows the speed on an underdamped spring, so tiles overshoot a little and wobble
// upright when the rows slow down. Skew is LEAN_PER degrees per dp/s, up to LEAN_MAX; tiles
// stretch along the row by STRETCH_PER per dp/s, up to STRETCH_MAX, and thin a third as much.
private const val LEAN_STIFFNESS = 140f
private const val LEAN_DAMPING = 11.8f
internal const val LEAN_PER = 0.0055f
internal const val LEAN_MAX = 16f
internal const val STRETCH_PER = 0.00004f
internal const val STRETCH_MAX = 0.12f

// ─── Pages ───────────────────────────────────────────────────────────────────────────────────
// A release that has moved PAGE_DISTANCE of the width sideways, or is going faster than PAGE_SPEED
// dp/s sideways, turns the page. The page settles on Reanimated's spring of 560 ms at a damping
// ratio of 1, which works out to a critically damped spring of natural frequency PAGE_OMEGA (1/s)
// when it starts at rest. Under reduced motion it fades across in PAGE_FADE s instead.
private const val PAGE_DISTANCE = 0.18f
private const val PAGE_SPEED = 450f
private const val PAGE_OMEGA = 14.7236
private const val PAGE_FADE = 0.32
// The text of a page slides TEXT_SHIFT dp with the page and is gone by TEXT_GONE of a page away, so
// the old line has left before the new one arrives.
internal const val TEXT_SHIFT = 28f
internal const val TEXT_GONE = 0.46f
// The page dots: DOT dp round, ACTIVE_W wide for the current page, DOT_GAP apart.
internal const val DOT = 6f
internal const val ACTIVE_W = 20f
internal const val DOT_GAP = 6f

// ─── Entrance ────────────────────────────────────────────────────────────────────────────────
// Once laid out, the rows fade in over HERO_FADE s while rising HERO_RISE dp, the brand line with
// them; the dots, the text, the button and the sign-in line follow, starting ENTER_DELAY s after
// the rows and STAGGER s apart. Each fades in over ENTER_FADE s while rising ENTER_RISE dp.
private const val HERO_FADE = 0.9f
internal const val HERO_RISE = 14f
private const val ENTER_DELAY = 0.22f
private const val STAGGER = 0.09f
private const val ENTER_FADE = 0.6f
internal const val ENTER_RISE = 10f

// ─── autoPlay ────────────────────────────────────────────────────────────────────────────────
// Loop (seconds): the page rests, then a swipe to the next page at each SWIPES time: a PUSH s push
// of the rows to about FLING dp/s (leftwards, as a finger swiping to the next page throws them),
// released into the same spring as a hand's throw, the page turning on release over PAGE_TURN s.
// Three swipes carry the pages round to where they started. The fling's peak is set a little
// either side of FLING so the rows travel a whole number of row lengths per loop and close it
// seamlessly.
private const val LOOP = 15.0
private val Swipes = doubleArrayOf(3.2, 8.2, 13.2)
private const val FLING = 1400.0
private const val PUSH = 0.28
private const val PAGE_TURN = 0.6
// After a touch the loop waits this long, then starts over from where things are.
private const val USER_HOLD = 3f

internal fun clamp(x: Float, lo: Float, hi: Float) = min(hi, max(lo, x))

/** x mod m, always in [0, m). */
internal fun wrap(x: Double, m: Double): Double {
    if (!(m > 0) || !x.isFinite()) return 0.0
    val r = x % m
    return if (r < 0) r + m else r
}

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Double) = floor(x + 0.5)

/** How far page `index` is from the place `p`, the short way round `count` pages. */
internal fun pageDistance(p: Double, index: Int, count: Int): Double {
    val n = max(1, count).toDouble()
    return wrap(p - index + n / 2, n) - n / 2
}

/** A fling's speed over time, as a share of its peak: a smooth push, then the release spring. */
private fun flingShape(s: Double): Double {
    if (s <= 0) return 0.0
    if (s <= PUSH) {
        val u = s / PUSH
        return u * u * (3 - 2 * u)
    }
    val u = s - PUSH
    return (1 + OMEGA * u) * exp(-OMEGA * u)
}

/** How far a fling of peak 1 has carried the rows after s seconds (the integral of flingShape). */
private fun flingArea(s: Double): Double {
    if (s <= 0) return 0.0
    if (s <= PUSH) {
        val u = s / PUSH
        return PUSH * (u * u * u - (u * u * u * u) / 2)
    }
    val u = s - PUSH
    return PUSH / 2 + 2 / OMEGA - exp(-OMEGA * u) * (2 / OMEGA + u)
}

/** A strong ease-out, for the page turning in the script. */
private fun easeOut(u: Double): Double {
    val v = min(1.0, max(0.0, u))
    return 1 - (1 - v) * (1 - v) * (1 - v)
}

/** Where the scripted loop's page has got to by time t: one more page per swipe. */
private fun scriptedPage(t: Double): Double = Swipes.sumOf { easeOut((t - it - PUSH) / PAGE_TURN) }

/** Reanimated's default timing curve, an ease-in-out quad. */
internal val InOutQuad = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f }

internal class MarqueeTile(val offset: Float, val width: Float, val index: Int)

internal class MarqueeRow(val tiles: List<MarqueeTile>, val length: Float, val dir: Float)

/** Each row's tiles laid end to end, and the length after which the row repeats. */
private fun makeRows(rows: List<List<MarqueePhoto>>): List<MarqueeRow> {
    var index = 0
    val made = mutableListOf<MarqueeRow>()
    for (photos in rows) {
        var x = 0f
        val tiles = photos.map { photo ->
            val tile = MarqueeTile(x, if (photo.portrait) PORTRAIT_W else LANDSCAPE_W, index)
            x += tile.width + TILE_GAP
            index += 1
            tile
        }
        if (tiles.isNotEmpty()) made += MarqueeRow(tiles, x, if (made.size % 2 == 0) 1f else -1f)
    }
    return made
}

/** The paper's grain: a tile of seeded noise (mulberry32, the same numbers as the other ports). */
internal fun marqueeGrain(): Bitmap {
    var s = GRAIN_SEED
    fun next(): Double {
        s += 0x6D2B79F5
        var t = (s xor (s ushr 15)) * (1 or s)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
    val colors = IntArray(GRAIN_SIZE * GRAIN_SIZE)
    for (i in colors.indices) {
        val n = next()
        val dark = n < 0.5
        val a = (((if (dark) (0.5 - n) * 2 * GRAIN_DARK else (n - 0.5) * 2 * GRAIN_LIGHT) * 255).roundToInt()).coerceIn(0, 255)
        // Dark specks are black, light specks white, each with its own strength.
        colors[i] = (a shl 24) or (if (dark) 0 else 0xFFFFFF)
    }
    return Bitmap.createBitmap(colors, GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888)
}

/**
 * A photo that has arrived, when it did, and a shader that paints it centred on the origin, so a
 * tile is one rounded rectangle filled with it instead of a clip and a bitmap.
 */
internal class Loaded(val image: Bitmap, val at: Float) {
    val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setTranslate(-image.width / 2f, -image.height / 2f) })
    }
}

/** A value moved by Reanimated-style timings: a delay, then a curve from wherever it was. */
internal class MarqueeTimed(var value: Float) {
    private var from = 0f
    private var to = 0f
    private var time = 0f
    private var duration = 0f
    private var wait = 0f
    private var easing: Easing = InOutQuad
    private var running = false

    fun animate(target: Float, seconds: Float, curve: Easing, delay: Float = 0f) {
        from = value
        to = target
        time = 0f
        wait = delay
        duration = seconds
        easing = curve
        running = true
    }

    fun step(dt: Float) {
        if (!running) return
        if (wait > 0f) {
            wait -= dt
            if (wait > 0f) return
            // The timing starts from the value as it is when the delay ends.
            from = value
        }
        time += dt
        val p = min(1f, time / max(duration, 0.0001f))
        value = from + (to - from) * easing.transform(p)
        if (p >= 1f) running = false
    }
}

/** Everything the frame loop, the drag and the canvas share, kept off Compose state but the clock. */
internal class MarqueeMotion(photos: List<List<MarqueePhoto>>, pages: Int) {
    val rows = makeRows(photos)
    val photoCount = photos.sumOf { it.size }
    val count = max(1, pages)
    // Rows of different lengths travel at speeds in proportion, so all of them repeat together.
    val reference = max(1f, rows.sumOf { it.length.toDouble() }.toFloat() / max(1, rows.size))
    // The script's fling: the peak nearest FLING that, with the drift, carries the rows a whole
    // number of row lengths per loop. Each swipe also counts the tail of its own fling from the
    // loop before, so the speed is the same either side of the seam.
    private val fling: Double = run {
        val area = Swipes.sumOf { flingArea(2 * LOOP - it) }
        val laps = max(1.0, jsRound((DRIFT * LOOP + FLING * area) / reference))
        max(0.0, (laps * reference - DRIFT * LOOP) / max(0.001, area))
    }
    val loaded = arrayOfNulls<Loaded>(photoCount)

    // Distance travelled, in dp of the reference row (row one moves with it), and the speed above
    // the drift (dp/s). The drift and every swipe to the next page run leftwards: negative.
    var travel = 0.0
    var boost = 0f
    // The lean spring's position and speed, following `boost`.
    var lean = 0f
    private var leanSpeed = 0f
    private var dragging = false
    // Seconds since the block appeared. The canvas and the layers read it, so they are drawn again
    // every frame.
    var elapsed by mutableFloatStateOf(0f)
    // The release spring on `boost`: its starting speed and how long it has run (−1 for none).
    private var releaseFrom = 0f
    private var releaseTime = -1f
    // The page as a place: 1.5 is halfway from the second page to the third. The script runs it
    // round past the last page, so it is read the short way round.
    var page = 0.0
    // The page nearest the place, as state: only it is read out by accessibility services.
    var shown by mutableIntStateOf(0)
        private set
    // The page's own animation after a hand turns it: a spring (or, under reduced motion, a fade)
    // from where it was to the page it lands on; −1 for none.
    private var moveFrom = 0.0
    private var moveTo = 0.0
    private var moveTime = -1.0
    // autoPlay: the loop's clock, the travel and the page it started from, and the pause after a touch.
    private var clock = 0.0
    private var from = 0.0
    private var pageFrom = 0.0
    private var looping = false
    private var hold = 0f
    var reduced = false

    // Where the text block starts (dp from the top), once laid out; the rows fill the space above.
    var textTop = Float.NaN
    // The entrance: the rows, then the brand line, the dots, the text, the button and the sign-in line.
    val hero = MarqueeTimed(0f)
    val enter = Array(5) { MarqueeTimed(0f) }
    private var entered = false

    /** autoPlay was switched on or off: the loop starts over from where things are. */
    fun setAuto(on: Boolean) {
        looping = on
        clock = 0.0
        from = travel
        pageFrom = restingPage()
        hold = 0f
    }

    /** The page the place is nearest, as an index. */
    private fun restingPage(): Double = jsRound(wrap(page, count.toDouble())) % count

    /** The rows rise and fade in once everything has a place; the foot follows them in. */
    fun enterOnce(curve: Easing) {
        if (entered) return
        entered = true
        hero.animate(1f, HERO_FADE, curve)
        for (i in enter.indices) enter[i].animate(1f, ENTER_FADE, curve, if (i == 0) 0f else ENTER_DELAY + (i - 1) * STAGGER)
    }

    /** Where the scripted loop has carried the rows by time t. */
    private fun scriptedTravel(t: Double): Double {
        var moved = -DRIFT * t
        for (at in Swipes) moved -= fling * (flingArea(t - at) + flingArea(t + LOOP - at) - flingArea(LOOP - at))
        return moved
    }

    /** How fast above the drift the scripted loop runs at time t. */
    private fun scriptedBoost(t: Double): Float {
        var speed = 0.0
        for (at in Swipes) speed -= fling * (flingShape(t - at) + flingShape(t + LOOP - at))
        return speed.toFloat()
    }

    fun step(dt: Float) {
        elapsed += dt
        // The release spring: critically damped, so boost = v0 (1 + ωt) e^(−ωt).
        if (releaseTime >= 0f) {
            releaseTime += dt
            val w = OMEGA.toFloat()
            val e = exp(-w * releaseTime)
            val value = releaseFrom * (1f + w * releaseTime) * e
            val velocity = -releaseFrom * w * w * releaseTime * e
            if (abs(value) < 0.01f && abs(velocity) < 2f) {
                boost = 0f
                releaseTime = -1f
            } else {
                boost = value
            }
        }
        // The page a hand turned: a critically damped spring from rest, or a fade under reduced motion.
        if (moveTime >= 0) {
            moveTime += dt
            if (reduced) {
                val p = min(1.0, moveTime / PAGE_FADE)
                page = moveFrom + (moveTo - moveFrom) * InOutQuad.transform(p.toFloat())
                if (p >= 1.0) moveTime = -1.0
            } else {
                val u = PAGE_OMEGA * moveTime
                val left = (1 + u) * exp(-u)
                if (left < 1e-4) {
                    page = moveTo
                    moveTime = -1.0
                } else {
                    page = moveTo + (moveFrom - moveTo) * left
                }
            }
        }
        val drift = if (reduced) 0f else -DRIFT.toFloat()
        if (hold > 0f && !dragging) {
            hold -= dt
            if (hold <= 0f) {
                // The loop starts over from wherever the hand left the rows and the page.
                clock = 0.0
                from = travel
                pageFrom = restingPage()
            }
        }
        if (looping && hold <= 0f && !dragging) {
            val t = clock + dt
            if (t >= LOOP) {
                // One loop carries every row a whole number of lengths and the page round to where
                // it started, so this is the same picture as t = 0.
                from += scriptedTravel(LOOP)
                clock = t - LOOP
            } else {
                clock = t
            }
            travel = from + scriptedTravel(clock)
            boost = scriptedBoost(clock)
            releaseTime = -1f
            moveTime = -1.0
            page = wrap(pageFrom + scriptedPage(clock), count.toDouble())
        } else if (!dragging) {
            travel += (drift + boost) * dt
        } else {
            // The finger moves the rows itself (see the drag); the drift carries on under it.
            travel += drift * dt
        }
        // Everything repeats every reference length, so the distance is kept small without a seam.
        val laps = (travel / reference / 64).toLong() * 64
        if (laps != 0L) {
            travel -= laps * reference.toDouble()
            from -= laps * reference.toDouble()
        }
        if (!travel.isFinite()) travel = 0.0
        if (!boost.isFinite()) boost = 0f
        if (!page.isFinite()) page = 0.0
        val near = restingPage().toInt()
        if (near != shown) shown = near
        // The lean chases the speed on its spring; under reduced motion nothing leans.
        val target = if (reduced) 0f else boost
        val accel = LEAN_STIFFNESS * (target - lean) - LEAN_DAMPING * leanSpeed
        leanSpeed += accel * dt
        lean += leanSpeed * dt
        if (!lean.isFinite() || !leanSpeed.isFinite()) {
            lean = 0f
            leanSpeed = 0f
        }
        hero.step(dt)
        for (e in enter) e.step(dt)
    }

    /**
     * Turns to the page `step` away from the one shown, if there is one; returns the page it
     * landed on when that is a new one, else −1.
     */
    fun turn(step: Int): Int {
        // Read the short way round, so a page caught mid-way back to the first settles without
        // passing through the others.
        val p = wrap(page + 0.5, count.toDouble()) - 0.5
        val current = min(count - 1.0, max(0.0, jsRound(p)))
        val next = min(count - 1.0, max(0.0, current + step))
        page = p
        moveFrom = p
        moveTo = next
        moveTime = 0.0
        return if (next != current) next.toInt() else -1
    }

    fun begin() {
        // Catching the rows stops the throw under the finger; the lean relaxes on its spring.
        dragging = true
        hold = USER_HOLD
        boost = 0f
        releaseTime = -1f
    }

    fun change(dx: Float, dy: Float, vx: Float, vy: Float) {
        val along = dx - dy * VERTICAL_SHARE
        if (!along.isFinite()) return
        // Each row moves by the finger in its own direction; travel is measured on row one.
        travel += along
        val speed = vx - vy * VERTICAL_SHARE
        if (!speed.isFinite()) return
        val k = clamp(1f / 60f / DRAG_SMOOTH, 0f, 1f)
        boost += (clamp(speed, -MAX_THROW, MAX_THROW) - boost) * k
    }

    /** The release: the throw, and the page turn of a sideways swipe; returns `turn`'s answer or −1. */
    fun end(dx: Float, dy: Float, vx: Float, vy: Float, width: Float): Int {
        val raw = vx - vy * VERTICAL_SHARE
        val speed = if (raw.isFinite()) clamp(raw, -MAX_THROW, MAX_THROW) else 0f
        // The throw carries the finger's speed and settles back to the drift on the spring.
        boost = if (reduced) 0f else speed
        if (!reduced) {
            releaseFrom = speed
            releaseTime = 0f
        }
        // A sideways swipe turns the page: leftwards to the next one.
        val x = if (dx.isFinite()) dx else 0f
        val y = if (dy.isFinite()) dy else 0f
        val v = if (vx.isFinite()) vx else 0f
        val w = max(1f, width)
        val sideways = abs(x) > abs(y)
        return when {
            sideways && (x < -w * PAGE_DISTANCE || v < -PAGE_SPEED) -> turn(1)
            sideways && (x > w * PAGE_DISTANCE || v > PAGE_SPEED) -> turn(-1)
            else -> -1
        }
    }

    fun finish() {
        dragging = false
        hold = USER_HOLD
    }
}
