package com.ngocdevv.nativecook.blocks.animations.soapbubblepop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/*
 Soap Bubble Pop — geometry, the autoPlay script and the touch simulation, ported from
 bubbleMotion.ts. Units: dp and seconds. LOOP = 8 s:
   0.0 – 1.2   rest: three bubbles hover (a 3–4 dp bob at 1 and 2 cycles per loop, so it repeats).
   1.2 – 3.0   they drift up (ease-in-out).
   3.0         a tap pops bubble A: a ragged hole tears across it in 0.2 s, droplets fly out and
               fall, a "+10" star floats up, the round score rolls 0 → 10.
   4.6         bubble B pops the same way (10 → 20).
   5.4 – 6.4   hold: only C hovers.
   6.4 – 6.95  a new A inflates from the wand's soap film, detaches and floats to its place
               (6.95 – 7.55); 6.95 – 7.5 a new B inflates, floats home 7.5 – 8.0. C sinks back
               to rest over 6.4 – 8.0; the score rolls back to 0 (a new round) 6.45 – 7.0.
   8.0         the frame at 0.
*/

internal const val LOOP = 8f
private const val REDUCED_AT = 0.4f
private const val IDLE_BEFORE_BACK = 2f
private const val BACK_FOR = 0.9f
private const val MODE_AUTO = 0
private const val MODE_LIVE = 1
private const val MODE_BACK = 2

/** Inflating at the wand, then floating to the bubble's place (s). */
private const val INFLATE = 0.55f
private const val TRAVEL = 0.6f
private val POP_AT = floatArrayOf(3.0f, 4.6f)
/** Where the scripted taps land, as a share of the bubble's radius. */
private val TAP_AT = arrayOf(floatArrayOf(0.42f, -0.5f), floatArrayOf(-0.56f, -0.18f))
private val RESPAWN_AT = floatArrayOf(6.4f, 6.95f)
private const val TRAVEL_B = 0.5f
/** A popped bubble's star floats this long (s). */
internal const val BUBBLE_STAR_FOR = 1.1f

internal const val BUBBLE_HEADER_H = 128f
internal const val BUBBLE_CARD_H = 136f

internal class BubbleGeo(
    val w: Float,
    val h: Float,
    /** Rest x, y and radius of A, B, C. */
    val rest: FloatArray,
    /** Drift of each bubble (dx, dy) during 1.2 – 3.0. */
    val drift: FloatArray,
    val wx: Float,
    val wy: Float,
    val wr: Float,
    val cardTop: Float,
)

internal fun makeBubbleGeo(w: Float, h: Float, insetTop: Float, insetBottom: Float): BubbleGeo {
    val cardTop = h - max(insetBottom, 16f) - 12f - BUBBLE_CARD_H
    val wy = cardTop - 56f
    val fieldTop = insetTop + BUBBLE_HEADER_H + 8f
    val fieldH = wy - 70f - fieldTop
    val lift = min(44f, fieldH * 0.11f)
    return BubbleGeo(
        w = w,
        h = h,
        rest = floatArrayOf(
            w * 0.3f, fieldTop + fieldH * 0.3f, min(w * 0.17f, 72f),
            w * 0.7f, fieldTop + fieldH * 0.55f, min(w * 0.14f, 60f),
            w * 0.36f, fieldTop + fieldH * 0.84f, min(w * 0.11f, 46f),
        ),
        drift = floatArrayOf(8f, -lift, -10f, -lift * 0.8f, 12f, -lift * 0.7f),
        wx = w * 0.5f,
        wy = wy,
        wr = 24f,
        cardTop = cardTop,
    )
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

private const val TWO_PI = (2.0 * PI).toFloat()

/** The hover: 3 dp sideways once a loop, 4 dp up and down twice (film = loop clock in loops). */
private fun bobX(i: Int, film: Float) = 3f * sin(TWO_PI * (film + i * 0.31f))
private fun bobY(i: Int, film: Float) = 4f * sin(TWO_PI * (2f * film + i * 0.17f))

/**
 Bubble i growing at the wand and floating home: s seconds after it started, `travel` the time
 its trip takes. Writes the pose (x, y, radius, alpha, pop age, tap dx, tap dy, wobble) into out.
*/
private fun inflatePose(g: BubbleGeo, i: Int, s: Float, travel: Float, film: Float, out: FloatArray) {
    val r0 = g.rest[i * 3 + 2]
    val k = easeOut(clamp01(s / INFLATE))
    val r = g.wr * 1.05f + (r0 - g.wr * 1.05f) * k
    // Attached: the bubble balloons up out of the ring.
    val ax = g.wx
    val ay = g.wy - (r - g.wr * 0.92f)
    val u = easeInOut(clamp01((s - INFLATE) / travel))
    val tx = g.rest[i * 3] + bobX(i, film) * u
    val ty = g.rest[i * 3 + 1] + bobY(i, film) * u
    val arc = sin(PI.toFloat() * u) * (if (i == 0) -22f else 22f)
    out[0] = ax + (tx - ax) * u + arc
    out[1] = ay + (ty - ay) * u
    out[2] = r
    out[3] = 1f
    out[4] = -1f
    out[5] = 0f
    out[6] = 0f
    // Wobbly while it grows and right after it lets go, calm by the time it lands.
    val since = max(0f, s - INFLATE)
    out[7] = 0.012f + 0.03f * (1f - k) + 0.03f * exp(-since / 0.3f) * (if (s > INFLATE) 1f else 0f) * (1f - u)
}

internal class BubbleFrame {
    /** Per bubble: x, y, radius, alpha, pop age (< 0 whole), tap dx, tap dy, wobble. */
    val poses = Array(3) { floatArrayOf(0f, 0f, 0f, 0f, -1f, 0f, 0f, 0.012f) }
    /** Wand film alpha. */
    var wandFilm = 1f
    /** Finger mark: x, y, age (< 0 none). */
    val tap = floatArrayOf(0f, 0f, -1f)
    /** Star age per bubble (< 0 none), and where it starts. */
    val stars = floatArrayOf(-1f, -1f, -1f)
    val starX = floatArrayOf(0f, 0f, 0f)
    val starY = floatArrayOf(0f, 0f, 0f)
    /** Round score in tens (continuous while it rolls). */
    var score = 0f
    var film = 0f
}

private fun restPose(g: BubbleGeo, i: Int, dx: Float, dy: Float, film: Float, out: FloatArray) {
    out[0] = g.rest[i * 3] + dx + bobX(i, film)
    out[1] = g.rest[i * 3 + 1] + dy + bobY(i, film)
    out[2] = g.rest[i * 3 + 2]
    out[3] = 1f
    out[4] = -1f
    out[5] = 0f
    out[6] = 0f
    out[7] = 0.012f
}

/** The autoPlay frame at loop time t (pure). */
private fun scriptFrame(t: Float, g: BubbleGeo, f: BubbleFrame) {
    val film = t / LOOP
    f.film = film
    val up = easeInOut(seg(t, 1.2f, 3.0f))
    f.tap[2] = -1f
    f.wandFilm = 1f
    for (i in 0 until 2) {
        val p = f.poses[i]
        f.stars[i] = -1f
        if (t < POP_AT[i]) {
            restPose(g, i, g.drift[i * 2] * up, g.drift[i * 2 + 1] * up, film, p)
        } else if (t < RESPAWN_AT[i]) {
            // Popped where it was when the finger came down.
            restPose(g, i, g.drift[i * 2], g.drift[i * 2 + 1], POP_AT[i] / LOOP, p)
            p[4] = t - POP_AT[i]
            p[5] = TAP_AT[i][0] * p[2]
            p[6] = TAP_AT[i][1] * p[2]
            f.stars[i] = if (t - POP_AT[i] < BUBBLE_STAR_FOR) t - POP_AT[i] else -1f
            f.starX[i] = p[0]
            f.starY[i] = p[1]
            val tapAge = t - POP_AT[i] + 0.08f
            if (tapAge < 0.45f) {
                f.tap[0] = p[0] + p[5]
                f.tap[1] = p[1] + p[6]
                f.tap[2] = tapAge
            }
        } else {
            inflatePose(g, i, t - RESPAWN_AT[i], if (i == 0) TRAVEL else TRAVEL_B, film, p)
            if (t - RESPAWN_AT[i] < INFLATE + 0.25f) f.wandFilm = 0f
        }
        // The finger mark shows just before the pop too.
        val pre = t - POP_AT[i] + 0.08f
        if (pre >= 0 && pre < 0.08f) {
            f.tap[0] = g.rest[i * 3] + g.drift[i * 2] + TAP_AT[i][0] * g.rest[i * 3 + 2]
            f.tap[1] = g.rest[i * 3 + 1] + g.drift[i * 2 + 1] + TAP_AT[i][1] * g.rest[i * 3 + 2]
            f.tap[2] = pre
        }
    }
    // The film comes back to the ring once the second bubble has left it.
    val back = seg(t, RESPAWN_AT[1] + INFLATE + 0.05f, RESPAWN_AT[1] + INFLATE + 0.35f)
    if (t > RESPAWN_AT[1] + INFLATE) f.wandFilm = back
    val settle = easeInOut(seg(t, 6.4f, 8.0f))
    restPose(g, 2, g.drift[4] * up * (1f - settle), g.drift[5] * up * (1f - settle), film, f.poses[2])
    f.stars[2] = -1f
    f.score = easeInOut(seg(t, 3.05f, 3.4f)) + easeInOut(seg(t, 4.65f, 5.0f)) - 2f * easeInOut(seg(t, 6.45f, 7.0f))
}

/** The touch simulation. */
internal class BubbleSim {
    private var mode = MODE_AUTO
    private var t = 0f
    private var now = 0f
    private var last = 0f
    private var back = 0f
    val frame = BubbleFrame()
    /** Base (un-bobbed) x, y of each whole bubble while live; spawn clock (< 0 none). */
    private val base = FloatArray(6)
    private val spawn = floatArrayOf(-1f, -1f, -1f)
    private var score = 0f
    /** Snapshot at the start of "back". */
    private val from = FloatArray(9)
    private var filmFrom = 0f
    private var scoreFrom = 0f

    /** Whether the frame loop has nothing to do: at rest outside autoPlay. */
    fun idle(auto: Boolean) = mode == MODE_AUTO && !auto

    /** A finger lands: the script stops; whole bubbles keep hovering where they are. */
    private fun goLive() {
        if (mode == MODE_LIVE) return
        val f = frame
        for (i in 0 until 3) {
            val p = f.poses[i]
            base[i * 2] = p[0] - bobX(i, f.film)
            base[i * 2 + 1] = p[1] - bobY(i, f.film)
            spawn[i] = -1f
            // A bubble still at the wand or on its way home finishes that trip under the finger's clock.
            if (mode == MODE_AUTO && p[4] < 0 && i < 2 && t >= RESPAWN_AT[i]) spawn[i] = t - RESPAWN_AT[i]
        }
        score = round(f.score)
        f.score = score
        mode = MODE_LIVE
    }

    /** A tap: pops the topmost whole bubble under the finger. Returns its index, or -1. */
    fun tap(x: Float, y: Float): Int {
        goLive()
        last = now
        val f = frame
        f.tap[0] = x
        f.tap[1] = y
        f.tap[2] = 0f
        for (i in 0 until 3) {
            val p = f.poses[i]
            if (p[4] >= 0 || p[3] < 0.5f) continue
            val dx = x - p[0]
            val dy = y - p[1]
            if (dx * dx + dy * dy > p[2] * p[2] * 1.12f) continue
            base[i * 2] = p[0] - bobX(i, f.film)
            base[i * 2 + 1] = p[1] - bobY(i, f.film)
            spawn[i] = -1f
            p[4] = 0f
            p[5] = dx
            p[6] = dy
            f.stars[i] = 0f
            f.starX[i] = p[0]
            f.starY[i] = p[1]
            score = min(9f, score + 1f)
            return i
        }
        return -1
    }

    fun touch() {
        goLive()
        last = now
    }

    /** One frame of the simulation. */
    fun step(dt: Float, g: BubbleGeo, auto: Boolean, reduced: Boolean) {
        now += dt
        val f = frame
        if (mode == MODE_AUTO) {
            if (auto) t = (t + dt) % LOOP
            scriptFrame(if (reduced) REDUCED_AT else t, g, f)
            return
        }
        if (f.tap[2] >= 0) f.tap[2] += dt
        if (mode == MODE_LIVE) {
            f.film += dt / LOOP
            f.wandFilm = 1f
            var busy = false
            for (i in 0 until 3) if (spawn[i] >= 0 && spawn[i] < INFLATE + 0.25f) busy = true
            for (i in 0 until 3) {
                val p = f.poses[i]
                if (f.stars[i] >= 0) f.stars[i] = if (f.stars[i] + dt < BUBBLE_STAR_FOR) f.stars[i] + dt else -1f
                if (spawn[i] >= 0) {
                    spawn[i] += dt
                    inflatePose(g, i, spawn[i], TRAVEL, f.film, p)
                    if (spawn[i] < INFLATE + 0.25f) f.wandFilm = 0f
                    if (spawn[i] >= INFLATE + TRAVEL) {
                        spawn[i] = -1f
                        base[i * 2] = g.rest[i * 3]
                        base[i * 2 + 1] = g.rest[i * 3 + 1]
                    }
                } else if (p[4] >= 0) {
                    p[4] += dt
                    // Once its droplets are gone, a new one is blown at the wand (one at a time).
                    if (p[4] > 1.3f && !busy) {
                        spawn[i] = 0f
                        busy = true
                        inflatePose(g, i, 0f, TRAVEL, f.film, p)
                    }
                } else {
                    p[0] = base[i * 2] + bobX(i, f.film)
                    p[1] = base[i * 2 + 1] + bobY(i, f.film)
                    p[7] = 0.012f
                }
            }
            // The score's digits roll to the new value and rest exactly on it.
            f.score += (score - f.score) * min(1f, dt * 9f)
            if (abs(score - f.score) < 0.002f) f.score = score
            if (auto && now - last > IDLE_BEFORE_BACK) {
                mode = MODE_BACK
                back = 0f
                for (i in 0 until 3) {
                    val p = f.poses[i]
                    val whole = p[4] < 0 && spawn[i] < 0
                    from[i * 3] = if (whole) p[0] - bobX(i, f.film) else g.rest[i * 3]
                    from[i * 3 + 1] = if (whole) p[1] - bobY(i, f.film) else g.rest[i * 3 + 1]
                    from[i * 3 + 2] = if (whole) 1f else 0f
                }
                filmFrom = f.film
                scoreFrom = f.score
            }
            return
        }
        // Back to rest: whole bubbles glide home; missing ones fade in at their place.
        back = min(1f, back + dt / BACK_FOR)
        val k = easeInOut(back)
        f.film = filmFrom + (ceil(filmFrom) - filmFrom) * k
        f.wandFilm = max(f.wandFilm, k)
        for (i in 0 until 3) {
            val p = f.poses[i]
            val bx = from[i * 3] + (g.rest[i * 3] - from[i * 3]) * k
            val by = from[i * 3 + 1] + (g.rest[i * 3 + 1] - from[i * 3 + 1]) * k
            restPose(g, i, bx - g.rest[i * 3], by - g.rest[i * 3 + 1], f.film, p)
            if (from[i * 3 + 2] < 0.5f) {
                p[3] = k
                p[2] = g.rest[i * 3 + 2] * (0.92f + 0.08f * k)
            }
            if (f.stars[i] >= 0) f.stars[i] = if (f.stars[i] + dt < BUBBLE_STAR_FOR) f.stars[i] + dt else -1f
        }
        f.score = scoreFrom * (1f - k)
        if (back >= 1f) {
            mode = MODE_AUTO
            t = 0f
            score = 0f
            for (i in 0 until 3) spawn[i] = -1f
            scriptFrame(0f, g, f)
        }
    }
}
