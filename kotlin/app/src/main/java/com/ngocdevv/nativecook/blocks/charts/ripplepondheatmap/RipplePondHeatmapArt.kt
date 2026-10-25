package com.ngocdevv.nativecook.blocks.charts.ripplepondheatmap

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Stillpond's October: the data, and the lily pads baked once (off the main thread) into a bitmap,
 ported from rippleArt.ts.

 October 2026 starts on a Thursday; weeks start on Monday, so the grid is 7 × 5 and day d sits at
 index d + 2. Today is the 25th: the days after it are drawn as faint rings. A pad's colour
 deepens with the minutes practised (5 levels, pale sage to deep emerald); a day over an hour
 carries a small water-lily flower. Each pad has the classic notch cut toward its centre (at a
 seeded angle), fine radial veins, a lighter rim, and its day number.

 Unlike React Native the bitmap covers only a band of the screen around the grid (full width, the
 grid plus a transparent margin) instead of the whole screen; the shader samples it with a clamped
 bitmap shader, whose transparent edges stand in for the rest.
*/

private const val FIRST_COL = 3
private const val DAYS = 31
internal const val POND_TODAY = 25
/** Minutes practised per day of October (up to today). */
private val MINUTES = intArrayOf(22, 35, 0, 48, 15, 30, 28, 0, 40, 62, 55, 12, 42, 38, 0, 25, 33, 55, 20, 44, 36, 50, 0, 65, 72)
private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val FOCUS = listOf("Breath focus", "Body scan", "Open awareness", "Walking", "Loving kindness")

private fun sessionsOf(m: Int) = if (m == 0) 0 else max(1, (m / 16f).roundToInt())

private fun levelOf(m: Int) = when {
    m <= 0 -> 0
    m < 20 -> 1
    m < 35 -> 2
    m < 50 -> 3
    else -> 4
}

/** Column and row of a day in the grid. */
internal fun pondCellCol(day: Int) = (day - 1 + FIRST_COL) % 7
internal fun pondCellRow(day: Int) = (day - 1 + FIRST_COL) / 7

private fun hm(min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

private val TOTAL = MINUTES.sum()
private val SESSIONS = MINUTES.sumOf { sessionsOf(it) }
internal val POND_BEST_STREAK: Int = run {
    var best = 0
    var run = 0
    for (m in MINUTES) {
        run = if (m > 0) run + 1 else 0
        best = max(best, run)
    }
    best
}
internal val POND_SUMMARY = "${hm(TOTAL)} · $SESSIONS sessions · $POND_BEST_STREAK-day best streak"

internal class PondDetail(val title: String, val sub: String)

/** What the detail card says: -1 = the month, 1…31 = a day, 100 + row = a week. */
internal fun pondDetailFor(id: Int): PondDetail {
    if (id >= 100) {
        val row = id - 100
        var min = 0
        var ses = 0
        var first = 0
        var last = 0
        for (d in 1..POND_TODAY) {
            if (pondCellRow(d) != row) continue
            if (first == 0) first = d
            last = d
            min += MINUTES[d - 1]
            ses += sessionsOf(MINUTES[d - 1])
        }
        return PondDetail("Week ${row + 1} · ${hm(min)} · $ses sessions", "Oct $first – $last · ripples across the week")
    }
    if (id >= 1) {
        val m = MINUTES.getOrElse(id - 1) { 0 }
        val wd = WEEKDAYS[pondCellCol(id)]
        if (id > POND_TODAY) return PondDetail("$wd $id · not yet", "Plan a sit for this day")
        if (m == 0) return PondDetail("$wd $id · rest day", "No sessions logged")
        val s = sessionsOf(m)
        return PondDetail("$wd $id · $m min · $s ${if (s == 1) "session" else "sessions"}", "Mostly ${FOCUS[id % FOCUS.size].lowercase()}")
    }
    return PondDetail("October · ${hm(TOTAL)}", "$SESSIONS sessions · $POND_BEST_STREAK-day best streak · tap a day")
}

private val PAD_COLORS = arrayOf(
    intArrayOf(0xFF9DB3A2.toInt(), 0xFF7F978A.toInt()),
    intArrayOf(0xFFA9CF8E.toInt(), 0xFF86B06E.toInt()),
    intArrayOf(0xFF6FB06A.toInt(), 0xFF4F914F.toInt()),
    intArrayOf(0xFF3E8C4E.toInt(), 0xFF2D7040.toInt()),
    intArrayOf(0xFF1F6A3C.toInt(), 0xFF15502C.toInt()),
)

/** Pixels per dp of the baked pads, as in React Native. */
internal const val POND_ART_SCALE = 2f
/** Transparent margin around the grid in dp (more than the pads' bob and shadow reach). */
private const val MARGIN = 40f

private fun hash(n: Float): Float {
    val x = sin(n * 127.1f) * 43758.5453f
    return x - floor(x)
}

private fun argb(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)

private fun drawPad(canvas: Canvas, cx: Float, cy: Float, r: Float, day: Int, font: Paint) {
    val m = if (day <= POND_TODAY) MINUTES[day - 1] else -1
    val lvl = if (m < 0) 0 else levelOf(m)
    val future = m < 0
    val size = if (future) r * 0.82f else if (lvl == 0) r * 0.78f else r * (0.86f + 0.035f * lvl)
    val notch = hash(day * 3.7f) * PI.toFloat() * 2f
    if (future) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = argb(220, 240, 228, 0.32f)
        }
        canvas.drawCircle(cx, cy, size, paint)
    } else {
        // The pad: a disc with a narrow wedge cut to its centre.
        val half = 0.22f
        val path = Path()
        path.moveTo(cx, cy)
        val steps = 48
        for (i in 0..steps) {
            val a = notch + half + (i.toFloat() / steps) * (PI.toFloat() * 2f - 2f * half)
            path.lineTo(cx + cos(a) * size, cy + sin(a) * size)
        }
        path.close()
        val c0 = PAD_COLORS[lvl][0]
        val c1 = PAD_COLORS[lvl][1]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx - size * 0.3f, cy - size * 0.35f, size * 1.5f, intArrayOf(c0, c1, c1), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
            if (lvl == 0) alpha = (0.62f * 255f).roundToInt()
        }
        canvas.drawPath(path, paint)
        // Veins.
        val vein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            strokeCap = Paint.Cap.ROUND
            color = if (lvl >= 3) argb(170, 220, 170, 0.28f) else argb(40, 80, 50, 0.22f)
        }
        for (i in 1 until 9) {
            val a = notch + 0.22f + (i / 9f) * (PI.toFloat() * 2f - 0.44f)
            canvas.drawLine(cx + cos(a) * size * 0.18f, cy + sin(a) * size * 0.18f, cx + cos(a) * size * 0.88f, cy + sin(a) * size * 0.88f, vein)
        }
        // Rim: lighter on the upper left, darker below.
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            shader = LinearGradient(
                cx - size, cy - size, cx + size, cy + size,
                intArrayOf(argb(235, 255, 225, 0.55f), argb(235, 255, 225, 0f), argb(10, 40, 20, 0.35f)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(path, rim)
        // A water lily on the best days.
        if (m >= 60) {
            val fx = cx + cos(notch + PI.toFloat() * 0.8f) * size * 0.64f
            val fy = cy + sin(notch + PI.toFloat() * 0.8f) * size * 0.64f
            val petal = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 7) {
                val a = (i / 7f) * PI.toFloat() * 2f + day
                petal.color = if (i % 2 == 1) 0xFFF6C9D6.toInt() else 0xFFFBE3EA.toInt()
                val ox = fx + cos(a) * 3f
                val oy = fy + sin(a) * 3f
                canvas.drawOval(ox - 2.5f, oy - 2.5f, ox + 2.5f, oy + 2.5f, petal)
            }
            petal.color = 0xFFF4C94E.toInt()
            canvas.drawCircle(fx, fy, 1.8f, petal)
        }
    }
    // The day number.
    val text = "$day"
    font.color = if (future) argb(220, 240, 228, 0.5f) else if (lvl >= 3) argb(236, 250, 236, 0.95f) else argb(18, 48, 30, 0.85f)
    canvas.drawText(text, cx - font.measureText(text) / 2f, cy + 4.3f, font)
    if (day == POND_TODAY) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            color = argb(250, 240, 200, 0.9f)
            maskFilter = BlurMaskFilter(sigmaToRadius(1.2f * POND_ART_SCALE), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, size + 3.5f, ring)
    }
}

internal class PondPads(val bitmap: Bitmap, val bandTop: Float)

/** The pads at their places, baked at 2× into a band of the screen around the grid. */
internal fun bakePondPads(w: Float, grid: FloatArray): PondPads {
    val bandTop = grid[1] - MARGIN
    val bandH = grid[3] * 5f + MARGIN * 2f
    val pw = max(1, ceil(w * POND_ART_SCALE).toInt())
    val ph = max(1, ceil(bandH * POND_ART_SCALE).toInt())
    val bitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(POND_ART_SCALE, POND_ART_SCALE)
    canvas.translate(0f, -bandTop)
    val font = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.DEFAULT_BOLD
    }
    val r = min(grid[2], grid[3]) * 0.43f
    for (d in 1..DAYS) {
        drawPad(canvas, grid[0] + (pondCellCol(d) + 0.5f) * grid[2], grid[1] + (pondCellRow(d) + 0.5f) * grid[3], r, d, font)
    }
    return PondPads(bitmap, bandTop)
}
