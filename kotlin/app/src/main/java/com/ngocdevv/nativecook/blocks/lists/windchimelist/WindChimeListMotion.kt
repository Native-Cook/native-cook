package com.ngocdevv.nativecook.blocks.lists.windchimelist

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Wind Chime List — the sessions, the scripted scroll and every tube's swing, as pure functions of
 the loop time (a port of windChimeMotion.ts).

 LOOP = 9 s
   0.0 – 1.5   rest at the top of the list.
   1.5 – 4.0   a fling down to 420 dp (fast start, exponential slow-down). The tubes lag the
               scroll: each one is a damped pendulum (ζ 0.22, 1.0–2.4 Hz by its length) kicked
               when the scroll speeds up and slows down, 40 ms later per row, neighbours swinging
               opposite ways, so at the peaks they touch and ring.
   4.2         row 7 (mid-screen) is struck: it swings hard (ζ 0.12) and its waveform plays for 2.4 s.
   5.2 – 7.4   the list scrolls back up (ease-in-out), kicking the tubes again.
   7.4 – 9.0   the swings die out (a window closes the last 0.6 s so t = 9 equals t = 0).
*/

const val LOOP = 9f
internal const val CHIME_ROW_H = 92f
internal const val CHIME_TUBE_H = 64f
internal const val CHIME_STRING_H = CHIME_ROW_H - CHIME_TUBE_H
internal const val CHIME_ROW_DELAY = 0.04f
private const val FLING_AT = 1.5f
private const val FLING_END = 4.0f
private const val FLING_TO = 420f
private const val BACK_AT = 5.2f
private const val BACK_END = 7.4f
internal const val CHIME_STRIKE_ROW = 6
internal const val CHIME_STRIKE_AT = 4.2f
internal const val CHIME_PLAY_LEN = 2.4f
internal const val CHIME_VISUAL_TURN = 0.35f
internal const val CHIME_SWAY = 26f
internal const val CHIME_TOUCH = 0.37f
internal const val CHIME_RING_LEN = 0.55f

internal class ChimeSession(val title: String, val meta: String, val minutes: Int, val brass: Boolean, val shrink: Int, val seed: Int)

internal val ChimeSessions = listOf(
    ChimeSession("Low Tide Bowls", "Singing bowls", 24, true, 0, 1),
    ChimeSession("Cedar Hollow", "Bamboo chimes", 18, false, 22, 2),
    ChimeSession("Slow Lantern", "Gong & drone", 32, true, 10, 3),
    ChimeSession("Morning Kelp", "Crystal bowls", 15, true, 30, 4),
    ChimeSession("Rain on Slate", "Field recording", 40, false, 6, 5),
    ChimeSession("Night Ferry", "Drone", 28, true, 18, 6),
    ChimeSession("Moss Garden", "Bamboo chimes", 12, false, 36, 7),
    ChimeSession("Ember Circle", "Frame drum", 22, true, 4, 8),
    ChimeSession("Still Harbour", "Singing bowls", 35, true, 26, 9),
    ChimeSession("Paper Lanterns", "Kalimba", 16, false, 14, 10),
    ChimeSession("Fog Bell", "Bells & breath", 20, true, 32, 11),
    ChimeSession("Riverstone", "Water & bowls", 45, false, 8, 12),
    ChimeSession("Quiet Orchard", "Chimes", 14, true, 20, 13),
    ChimeSession("Last Light", "Gong bath", 50, true, 2, 14),
)

internal fun chimeClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun chimeSeg(t: Float, a: Float, b: Float) = chimeClamp01((t - a) / (b - a))
internal fun chimeEaseInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun chimeHash(i: Float): Float {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Row i's pendulum: angular frequency (rad/s, shorter tubes ring faster). */
internal fun chimeOmega(i: Int): Float {
    val shrink = ChimeSessions[i % ChimeSessions.size].shrink
    return 2 * PI.toFloat() * (1.0f + 1.4f * (shrink / 36f) * 0.7f + 0.25f * chimeHash(i + 7f))
}

/** Row i's swing direction and strength. */
internal fun chimeDir(i: Int) = (if (i % 2 == 0) 1 else -1) * (0.8f + 0.4f * chimeHash(i * 3 + 1f))

/** The scripted scroll offset (dp). */
internal fun chimeScrollAt(t: Float): Float {
    if (t < FLING_AT) return 0f
    if (t < BACK_AT) {
        val u = chimeSeg(t, FLING_AT, FLING_END)
        return (FLING_TO * (1 - exp(-4.2f * u))) / (1 - exp(-4.2f))
    }
    return FLING_TO * (1 - chimeEaseInOutCubic(chimeSeg(t, BACK_AT, BACK_END)))
}

/** Damped impulse response, normalised to a unit first peak. */
private fun pulse(tau: Float, w: Float, zeta: Float): Float {
    if (tau <= 0f) return 0f
    val wd = w * sqrt(1 - zeta * zeta)
    return exp(-zeta * w * tau) * sin(wd * tau)
}

// Kicks the script gives every row: [time, strength] — the fling starting and braking, the
// return starting and stopping.
private val KICK_T = floatArrayOf(1.5f, 2.15f, 3.3f, 5.2f, 6.3f, 7.25f)
private val KICK_S = floatArrayOf(0.22f, -0.13f, -0.07f, -0.15f, 0.07f, 0.13f)

/** Row i's swing angle (rad) at loop time t. */
internal fun chimeSwingAt(t: Float, i: Int): Float {
    val w = chimeOmega(i)
    val d = chimeDir(i)
    var a = 0f
    for (k in KICK_T.indices) a += KICK_S[k] * d * pulse(t - KICK_T[k] - i * CHIME_ROW_DELAY, w, 0.22f)
    if (i == CHIME_STRIKE_ROW) a += 0.32f * pulse(t - CHIME_STRIKE_AT, w, 0.12f)
    a += 0.012f * sin((2 * PI.toFloat() * 2 * t) / LOOP + i * 0.7f)
    return a * (1 - chimeEaseInOutCubic(chimeSeg(t, 8.3f, 8.95f)))
}

/** Seconds since rows i and i + 1 last started touching (−1: not within RING_LEN) and the side (±1), into out. */
internal fun chimeRingAt(t: Float, i: Int, out: FloatArray, o: Int) {
    val step = 1f / 30f
    var touching = false
    var side = 0f
    var k = 0
    while (k * step <= CHIME_RING_LEN + step) {
        val at = t - k * step
        if (at < 0f) break
        val delta = chimeSwingAt(at, i) - chimeSwingAt(at, i + 1)
        if (abs(delta) > CHIME_TOUCH) {
            touching = true
            side = if (delta > 0f) 1f else -1f
        } else if (touching) {
            out[o] = (k - 1) * step
            out[o + 1] = side
            return
        }
        k++
    }
    out[o] = -1f
    out[o + 1] = 0f
}

/** The strike row's waveform level τ seconds after a strike (0…1). */
internal fun chimePlayEnvelope(tau: Float): Float {
    if (tau < 0f || tau > CHIME_PLAY_LEN) return 0f
    return chimeClamp01(tau / 0.06f) * (1 - tau / CHIME_PLAY_LEN).pow(1.6f)
}

/** Bar k of a waveform (static height 0…1). */
internal fun chimeBarHeight(seed: Int, k: Int) =
    0.25f + 0.75f * abs(sin(k * 0.9f + seed * 1.7f) * 0.6f + sin(k * 0.37f + seed) * 0.4f)
