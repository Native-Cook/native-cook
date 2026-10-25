package com.ngocdevv.nativecook.blocks.sheets.velvetcurtainsheet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Velvet Curtain Sheet — layout of the seat map and the autoPlay script (a port of velvetMotion.ts).

 LOOP = 9 s:
   0.0 – 1.4   rest: the sheet at its half detent, the curtain closed over the seat map;
               1.1 – 1.4 the "Choose seats" button is pressed (scale 0.96 and back).
   1.4 – 2.6   the curtain's rail opens (ease-in-out); the hem lags behind on a spring
               (ω 9 rad/s, damping ratio 0.35), so the cloth swings out, overshoots and settles by 3.2.
   3.2 – 5.6   seats C7 (3.6) and C8 (4.3) light up with a pop; the booking bar rises at 3.0.
   5.6 – 6.8   the curtain closes (ease-in-out), the hem swings in after it; the bar drops at 5.6.
   7.4 – 7.6   the seats switch off behind the closed curtain.
   7.6 – 9.0   rest. The hem's swing is faded out by 8.6 – 9.0 so t = 9 equals t = 0.
*/

internal const val LOOP = 9f

internal val VELVET_ROWS = listOf("A", "B", "C", "D", "E", "F", "G", "H")
internal const val PER_ROW = 12
internal const val SEAT_W = 20f
internal const val SEAT_H = 18f
private const val SEAT_PITCH = 24f
private const val AISLE = 18f
/** Sheet coordinates: where the curtain starts (under the pelmet), the screen arc, the first row. */
internal const val PELMET = 40f
internal const val SCREEN_Y = 62f
internal const val SEATS_Y = 98f
internal const val PRICE = 14

/** Seats the loop selects: C7 and C8 (indices into the row-major seat list). */
internal val LOOP_SEATS = intArrayOf(2 * PER_ROW + 6, 2 * PER_ROW + 7)
internal val LOOP_SEAT_AT = floatArrayOf(3.6f, 4.3f)

/** Sway of the hem: a spring trailing the rail. */
internal const val SWAY_OMEGA = 9f
internal const val SWAY_ZETA = 0.35f

internal const val VELVET_IDLE_BEFORE_RETURN = 2f
internal const val VELVET_RETURN_FOR = 0.8f

internal fun vClamp(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))

private fun seg(t: Float, a: Float, b: Float) = vClamp((t - a) / (b - a), 0f, 1f)

internal fun vEaseInOut(x: Float): Float {
    val c = vClamp(x, 0f, 1f)
    return if (c < 0.5f) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2
}

internal fun vEaseOut(x: Float): Float = 1 - (1 - vClamp(x, 0f, 1f)).pow(3)

/** Row pitch that keeps the map, the legend and the bar apart on short screens. */
internal fun rowPitch(visible: Float) = if (visible < 420f) 22f else 24f

internal fun seatsWidth() = PER_ROW * SEAT_PITCH - (SEAT_PITCH - SEAT_W) + AISLE

/** Top-left of seat i in sheet coordinates, into [out]. */
internal fun seatAt(i: Int, width: Float, pitch: Float, out: FloatArray) {
    val row = i / PER_ROW
    val col = i % PER_ROW
    val x0 = (width - seatsWidth()) / 2
    out[0] = x0 + col * SEAT_PITCH + if (col >= PER_ROW / 2) AISLE else 0f
    out[1] = SEATS_Y + row * pitch
}

internal fun seatName(i: Int) = "${VELVET_ROWS[i / PER_ROW]}${i % PER_ROW + 1}"

/** Taken seats: seeded, never the loop's pair or their neighbours. */
internal fun isTaken(i: Int): Boolean {
    val row = i / PER_ROW
    if (row == 2 && abs(i % PER_ROW - 6.5) < 2) return false
    val h = abs(sin(i * 12.9898 + 4.1) * 43758.5453) % 1.0
    return h < if (row < 2) 0.18 else if (row > 5) 0.42 else 0.3
}

/** The rail's opening in the loop (0 closed, 1 open). */
internal fun railAt(t: Float) = vEaseInOut(seg(t, 1.4f, 2.6f)) * (1 - vEaseInOut(seg(t, 5.6f, 6.8f)))

/** The hem's opening: the rail's, through the sway spring, integrated from t = 0 (pure in t). */
internal fun hemAt(t: Float): Float {
    val step = 1f / 60f
    var x = 0f
    var v = 0f
    val n = floor(t / step).toInt()
    for (i in 1..n) {
        val goal = railAt(i * step)
        val a = -SWAY_OMEGA * SWAY_OMEGA * (x - goal) - 2 * SWAY_ZETA * SWAY_OMEGA * v
        v += a * step
        x += v * step
    }
    val rail = railAt(t)
    val fade = 1 - vEaseInOut(seg(t, 8.6f, 9.0f))
    return rail + (x - rail) * fade
}

/** A seat's light in the loop, 0 when off. */
internal fun loopSeat(t: Float, k: Int): Float {
    val on = seg(t, LOOP_SEAT_AT[k], LOOP_SEAT_AT[k] + 0.35f)
    val off = seg(t, 7.4f, 7.6f)
    return on * (1 - off)
}

/** Pop scale for a seat lit `e` seconds ago (a small overshoot, settled in 0.35 s). */
internal fun popScale(e: Float): Float {
    if (e < 0f) return 1f
    val x = vClamp(e / 0.35f, 0f, 1f)
    return 1 + 0.12f * sin(PI.toFloat() * x) * (1 - x * 0.4f)
}

internal fun pressAt(t: Float) = 1 - 0.04f * sin(PI.toFloat() * seg(t, 1.1f, 1.4f))

/** The booking bar's rise (0 hidden, 1 shown). */
internal fun barAt(t: Float) = vEaseOut(seg(t, 3.0f, 3.4f)) * (1 - vEaseInOut(seg(t, 5.6f, 5.9f)))

/** The seat under a point in sheet coordinates, or -1. */
internal fun seatIndexAt(x: Float, y: Float, width: Float, pitch: Float): Int {
    val row = floor((y - SEATS_Y + (pitch - SEAT_H) / 2) / pitch).toInt()
    if (row < 0 || row >= VELVET_ROWS.size) return -1
    val p = FloatArray(2)
    for (col in 0 until PER_ROW) {
        seatAt(row * PER_ROW + col, width, pitch, p)
        if (x >= p[0] - 2 && x <= p[0] + SEAT_W + 2) return row * PER_ROW + col
    }
    return -1
}
