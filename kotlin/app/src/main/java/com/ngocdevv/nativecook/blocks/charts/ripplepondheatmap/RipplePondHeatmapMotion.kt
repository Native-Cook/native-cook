package com.ngocdevv.nativecook.blocks.charts.ripplepondheatmap

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Ripple Pond Heatmap — geometry, the autoPlay script and the touch simulation, ported from
 rippleMotion.ts. LOOP = 9 s:
   0.0 – 1.4   rest: still water, caustics drifting (one cycle per loop).
   1.4         a tap on day 7: a ripple leaves it (140 dp/s), the pads around bob as it passes,
               the detail card rolls to "Wed 7 · 28 min · 2 sessions" and a ring lights the pad.
   3.2         a tap on day 18, the same.
   4.6 – 5.8   a finger drags across week 4 (ease-in-out), leaving a wake: a ripple every 0.2 s
               along its path; the card rolls to the week.
   5.8 – 7.6   the water settles (every ripple fades out by 7.8); 7.4 the card rolls back to
               the month and the ring fades.
   7.6 – 9.0   rest.
*/

internal const val LOOP = 9f
private const val REDUCED_AT = 0f
private const val IDLE_BEFORE_BACK = 2f
private const val BACK_FOR = 0.9f
private const val MODE_AUTO = 0
private const val MODE_LIVE = 1
private const val MODE_BACK = 2
internal const val POND_SOURCES = 8

internal const val POND_HEADER_H = 118f
internal const val POND_CARD_H = 92f
internal const val POND_CHIPS_H = 40f

internal class PondGeo(val w: Float, val h: Float, val grid: FloatArray, val cardTop: Float, val legendTop: Float, val chipsTop: Float)

internal fun makePondGeo(w: Float, h: Float, insetTop: Float, insetBottom: Float): PondGeo {
    val left = 16f
    val cellW = (w - left * 2f) / 7f
    val gridTop = insetTop + POND_HEADER_H + 34f
    val chipsTop = h - max(insetBottom, 12f) - 8f - POND_CHIPS_H
    val cardTop = chipsTop - 12f - POND_CARD_H
    // Rows taller than wide (the pond spreads down the screen), short enough to leave the legend
    // room above the card.
    val rowH = max(1f, min(cellW * 1.45f, (cardTop - 56f - gridTop) / 5f))
    return PondGeo(w, h, floatArrayOf(left, gridTop, cellW, rowH), cardTop, gridTop + rowH * 5f + 14f, chipsTop)
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private fun padX(g: PondGeo, day: Int) = g.grid[0] + (pondCellCol(day) + 0.5f) * g.grid[2]
private fun padY(g: PondGeo, day: Int) = g.grid[1] + (pondCellRow(day) + 0.5f) * g.grid[3]

internal class PondFrame {
    /** 8 sources × (x, y, age, amplitude). */
    val src = FloatArray(POND_SOURCES * 4).also { for (i in 0 until POND_SOURCES) it[i * 4 + 2] = -1f }
    /** Selected pad x, y, glow. */
    val sel = floatArrayOf(0f, 0f, 0f)
    /** Detail card: -1 month, 1…31 day, 100 + row week. */
    var detail = -1
    var phase = 0f
}

private val TAP_AT = floatArrayOf(1.4f, 3.2f)
private val TAP_DAY = intArrayOf(7, 18)
private const val WAKE_FROM = 4.6f
private const val WAKE_TO = 5.8f
private const val WAKE_ROW = 3

private fun scriptFrame(t: Float, g: PondGeo, f: PondFrame) {
    f.phase = t / LOOP
    val calm = 1f - easeInOut(seg(t, 6.8f, 7.8f))
    for (i in 0 until 2) {
        f.src[i * 4] = padX(g, TAP_DAY[i])
        f.src[i * 4 + 1] = padY(g, TAP_DAY[i])
        f.src[i * 4 + 2] = if (t >= TAP_AT[i]) t - TAP_AT[i] else -1f
        f.src[i * 4 + 3] = calm
    }
    for (j in 0 until 6) {
        val born = WAKE_FROM + j * 0.2f
        // The finger's place along the wake when this ripple was born (ease-in-out across the row).
        val u = easeInOut(seg(born, WAKE_FROM, WAKE_TO))
        val x0 = g.grid[0] + g.grid[2] * 0.2f
        val x1 = g.grid[0] + g.grid[2] * 6.8f
        val k = (2 + j) * 4
        f.src[k] = x0 + (x1 - x0) * u
        f.src[k + 1] = g.grid[1] + (WAKE_ROW + 0.5f) * g.grid[3] + sin(u * PI.toFloat()) * 6f
        f.src[k + 2] = if (t >= born) t - born else -1f
        f.src[k + 3] = 0.55f * calm
    }
    // Card and ring.
    var detail = -1
    var day = 0
    if (t >= 1.45f && t < 3.25f) {
        detail = 7
        day = 7
    } else if (t >= 3.25f && t < 4.7f) {
        detail = 18
        day = 18
    } else if (t >= 4.7f && t < 7.4f) {
        detail = 100 + WAKE_ROW
    }
    f.detail = detail
    if (day > 0) {
        f.sel[0] = padX(g, day)
        f.sel[1] = padY(g, day)
    }
    val glow7 = easeInOut(seg(t, 1.45f, 1.75f)) * (1f - easeInOut(seg(t, 3.2f, 3.45f)))
    val glow18 = easeInOut(seg(t, 3.3f, 3.6f)) * (1f - easeInOut(seg(t, 4.6f, 4.9f)))
    f.sel[2] = if (day == 7) glow7 else if (day == 18) glow18 else 0f
}

/** The day under a point, or 0. */
private fun dayAt(g: PondGeo, x: Float, y: Float): Int {
    val c = floor((x - g.grid[0]) / g.grid[2]).toInt()
    val r = floor((y - g.grid[1]) / g.grid[3]).toInt()
    if (c < 0 || c > 6 || r < 0 || r > 4) return 0
    val day = r * 7 + c - 3 + 1
    if (day < 1 || day > 31) return 0
    val cx = g.grid[0] + (c + 0.5f) * g.grid[2]
    val cy = g.grid[1] + (r + 0.5f) * g.grid[3]
    return if (hypot(x - cx, y - cy) < g.grid[2] * 0.48f) day else 0
}

internal class PondSim {
    private var mode = MODE_AUTO
    private var t = 0f
    private var now = 0f
    private var last = 0f
    private var back = 0f
    val frame = PondFrame()
    private var next = 0
    var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private val from = FloatArray(10)

    /** Whether the frame loop has nothing to do: at rest outside autoPlay. */
    fun idle(auto: Boolean) = mode == MODE_AUTO && !auto

    private fun goLive() {
        if (mode == MODE_LIVE) return
        mode = MODE_LIVE
        // Ripples already out keep travelling; the oldest slot is reused first.
        var oldest = 0
        var age = -2f
        for (i in 0 until POND_SOURCES) {
            val a = frame.src[i * 4 + 2]
            val rank = if (a < 0) 1e9f else a
            if (rank > age) {
                age = rank
                oldest = i
            }
        }
        next = oldest
    }

    private fun emit(x: Float, y: Float, amp: Float) {
        val k = next * 4
        frame.src[k] = x
        frame.src[k + 1] = y
        frame.src[k + 2] = 0f
        frame.src[k + 3] = amp
        next = (next + 1) % POND_SOURCES
    }

    fun down(x: Float, y: Float) {
        goLive()
        last = now
        lastX = x
        lastY = y
    }

    /** A tap drops a ripple; on a pad it selects the day. Returns the day (0 for open water). */
    fun tap(g: PondGeo, x: Float, y: Float): Int {
        goLive()
        last = now
        val day = dayAt(g, x, y)
        emit(x, y, 1f)
        if (day > 0) {
            frame.sel[0] = padX(g, day)
            frame.sel[1] = padY(g, day)
            frame.sel[2] = if (day <= POND_TODAY) 1f else 0.5f
            frame.detail = day
        }
        return day
    }

    /** A drag leaves a wake: a ripple every 20 dp, stronger the faster the finger moves. */
    fun drag(x: Float, y: Float, speed: Float) {
        goLive()
        dragging = true
        last = now
        if (hypot(x - lastX, y - lastY) >= 20f) {
            emit(x, y, (speed / 900f).coerceIn(0.3f, 0.8f))
            lastX = x
            lastY = y
        }
    }

    fun dragEnd(g: PondGeo, y: Float) {
        dragging = false
        last = now
        val r = floor((y - g.grid[1]) / g.grid[3]).toInt()
        if (r in 0..4) {
            frame.detail = 100 + r
            frame.sel[2] = 0f
        }
    }

    /** One frame of the simulation. */
    fun step(dt: Float, g: PondGeo, auto: Boolean, reduced: Boolean) {
        now += dt
        val f = frame
        if (mode == MODE_AUTO) {
            if (auto) t = (t + dt) % LOOP
            scriptFrame(if (reduced) REDUCED_AT else t, g, f)
            return
        }
        f.phase += dt / LOOP
        for (i in 0 until POND_SOURCES) {
            val a = f.src[i * 4 + 2]
            if (a >= 0) f.src[i * 4 + 2] = if (a + dt > 6f) -1f else a + dt
        }
        if (mode == MODE_LIVE) {
            if (auto && !dragging && now - last > IDLE_BEFORE_BACK) {
                mode = MODE_BACK
                back = 0f
                for (i in 0 until POND_SOURCES) from[i] = f.src[i * 4 + 3]
                from[8] = f.sel[2]
                from[9] = f.phase
                f.detail = -1
            }
            return
        }
        back = min(1f, back + dt / BACK_FOR)
        val k = easeInOut(back)
        for (i in 0 until POND_SOURCES) f.src[i * 4 + 3] = from[i] * (1f - k)
        f.sel[2] = from[8] * (1f - k)
        // The caustics' clock eases on to the next whole loop, where the script picks it up.
        f.phase = from[9] + (ceil(from[9]) - from[9]) * k
        if (back >= 1f) {
            mode = MODE_AUTO
            t = 0f
            scriptFrame(0f, g, f)
        }
    }
}
