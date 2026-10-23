package com.ngocdevv.nativecook.blocks.charts.candlepatternwalkthrough

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object CandlePatternWalkthroughMotion {
    internal const val BASE = 28
    internal val SWING_HIGHS = listOf(3, 11, 19, 26)
    internal const val BREAK_FROM = 27
    internal const val PEAK = 31
    internal const val RETEST = 34
    internal const val CONFIRM = 35
    internal const val LAST = 43
    internal const val ENTRY = 42f
    internal const val STOP = 34f
    internal const val RATIO = 3
    internal const val ZONE_FROM = 35.6f
    internal const val ZONE_CANDLES = 10
    internal const val LINE_TO = 48
    internal const val LOOP = 11f
    internal const val SWING_AT = 0.8f
    internal const val SWING_STEP = 0.3f
    internal const val CIRCLE_IN = 0.25f
    internal val SWING_OUT = listOf(2.9f, 3.2f)
    internal val TREND_DRAW = listOf(2.3f, 2.9f)
    internal val TREND_TYPE = listOf(2.9f, 3.3f)
    internal const val PRINT_GROW = 0.1f
    internal const val BREAK_PRINT = 3.6f
    internal const val PRINT_STEP = 0.12f
    internal val ARROW_DRAW = listOf(4.1f, 4.45f)
    internal val BREAK_OUT = listOf(4.6f, 4.9f)
    internal const val RETEST_PRINT = 5f
    internal val ZIGZAG_DRAW = listOf(5.4f, 5.85f)
    internal const val RETEST_LABEL = 5.6f
    internal const val CONFIRM_PRINT = 6f
    internal const val CONFIRM_MARK = 6.15f
    internal val NOTES_OUT = listOf(6.6f, 6.85f)
    internal const val BOX_IN = 6.85f
    internal val ZONE_RIGHT = listOf(7.05f, 7.3f)
    internal val ZONE_UP = listOf(7.3f, 7.9f)
    internal const val RALLY_PRINT = 7.95f
    internal const val RALLY_STEP = 0.13f
    internal const val BURST = 8.95f
    internal const val BURST_FOR = 0.9f
    internal val FADE = listOf(9.7f, 10.1f)
    internal const val REPRINT = 10.15f
    internal const val REPRINT_STEP = 0.022f
    internal val CHAPTER_STARTS = listOf(0.8f, 2.3f, 3.3f, 4.6f, 5.95f, 6.6f, 7.8f)
    internal val STOPS = listOf(2.3f, 3.3f, 4.6f, 5.95f, 6.6f, 7.8f, 9.7f, 11f)
    internal const val GRAVITY = 1200f
    internal val CANDLES = listOf(
        listOf(38f, 42.8f, 37.2f, 42f),
        listOf(42f, 47.6f, 41.3f, 47f),
        listOf(47f, 48.2f, 44.1f, 45f),
        listOf(45f, 58f, 44.6f, 56f),
        listOf(56f, 56.8f, 48.2f, 49f),
        listOf(49f, 50.1f, 42f, 43f),
        listOf(43f, 44f, 37.1f, 38f),
        listOf(38f, 41.2f, 37.4f, 40f),
        listOf(40f, 40.9f, 36.2f, 37f),
        listOf(37f, 42.9f, 36.5f, 42f),
        listOf(42f, 47.8f, 41.1f, 47f),
        listOf(47f, 51.74f, 46.3f, 50.5f),
        listOf(50.5f, 50.6f, 44.2f, 45f),
        listOf(45f, 46.1f, 40.3f, 41f),
        listOf(41f, 41.8f, 35.4f, 36f),
        listOf(36f, 39f, 35.2f, 38f),
        listOf(38f, 38.7f, 33.4f, 34f),
        listOf(34f, 39.8f, 33.6f, 39f),
        listOf(39f, 42.9f, 38.3f, 42f),
        listOf(42f, 45.48f, 41.4f, 44.5f),
        listOf(44.5f, 44.6f, 39.2f, 40f),
        listOf(40f, 40.7f, 34.3f, 35f),
        listOf(35f, 35.6f, 30.2f, 31f),
        listOf(31f, 34.8f, 30.4f, 34f),
        listOf(34f, 34.9f, 32.1f, 33f),
        listOf(33f, 37.9f, 32.5f, 37f),
        listOf(37f, 40f, 36.4f, 38.5f),
        listOf(38.5f, 38.9f, 34.3f, 35f),
        listOf(35f, 38.2f, 34.4f, 37.8f),
        listOf(37.8f, 43.2f, 37.3f, 42.6f),
        listOf(42.6f, 46.9f, 41.9f, 46.2f),
        listOf(46.2f, 49.8f, 45.4f, 49f),
        listOf(49f, 49.6f, 44.2f, 44.8f),
        listOf(44.8f, 45.4f, 40.1f, 40.6f),
        listOf(40.6f, 41.2f, 34.2f, 36.4f),
        listOf(36.4f, 42.6f, 35.9f, 42f),
        listOf(42f, 47.8f, 41.4f, 47f),
        listOf(47f, 47.9f, 44.2f, 45f),
        listOf(45f, 52.6f, 44.6f, 52f),
        listOf(52f, 58.7f, 51.4f, 58f),
        listOf(58f, 58.6f, 55.2f, 56f),
        listOf(56f, 62.8f, 55.6f, 62f),
        listOf(62f, 67.4f, 61.5f, 66.8f),
        listOf(66.8f, 71.2f, 66.1f, 70.5f),
    )
    internal val VIEWS = listOf(
        listOf(0f, 13.5f, 44f, 1f),
        listOf(3.3f, 13.5f, 44f, 1f),
        listOf(3.9f, 21f, 43f, 1.08f),
        listOf(4.6f, 21f, 43f, 1.08f),
        listOf(5.2f, 28f, 41f, 1.45f),
        listOf(6.6f, 28f, 41f, 1.45f),
        listOf(7.2f, 34f, 49f, 1.12f),
        listOf(7.8f, 34f, 49f, 1.12f),
        listOf(9.1f, 38f, 52f, 1.2f),
    )
    internal val CONFETTI = listOf(
        listOf(-260f, 580f, 0f, 0f, 0f, 16f),
        listOf(-180f, 680f, 1f, 3f, 0.6f, 11f),
        listOf(-95f, 740f, 3f, 1f, -0.5f, 13f),
        listOf(-20f, 620f, 2f, 2f, 0.4f, 16f),
        listOf(70f, 780f, 1f, 0f, -0.7f, 12f),
        listOf(140f, 660f, 0f, 3f, 0f, 9f),
        listOf(220f, 580f, 3f, 2f, 0.5f, 12f),
        listOf(300f, 490f, 2f, 0f, -0.4f, 14f),
        listOf(-330f, 450f, 2f, 1f, 0.3f, 12f),
        listOf(25f, 440f, 3f, 3f, -0.6f, 9f),
        listOf(-140f, 500f, 1f, 2f, 0.5f, 12f),
        listOf(180f, 400f, 0f, 1f, 0f, 11f),
    )
    
    private val FIRST = SWING_HIGHS.first()
    private val FINAL = SWING_HIGHS.last()
    private val SLOPE = (CANDLES[FINAL][1] - CANDLES[FIRST][1]) / (FINAL - FIRST)
    private val PRINT_AT = CANDLES.indices.map { index ->
        when {
            index < BASE -> 0f
            index <= PEAK -> BREAK_PRINT + (index - BASE) * PRINT_STEP
            index <= RETEST -> RETEST_PRINT + (index - PEAK - 1) * PRINT_STEP
            index == CONFIRM -> CONFIRM_PRINT
            else -> RALLY_PRINT + (index - CONFIRM - 1) * RALLY_STEP
        }
    }
    internal fun clamp01(value: Float) = value.coerceIn(0f, 1f)
    internal fun smooth(time: Float, from: Float, to: Float): Float {
        val u = clamp01((time - from) / (to - from))
        return u * u * (3f - 2f * u)
    }
    internal fun spring(elapsed: Float, omega: Float, damping: Float): Float {
        if (elapsed <= 0f) return 0f
        val decay = damping * omega
        val ringing = omega * sqrt(1f - damping * damping)
        return 1f - exp(-decay * elapsed) * (cos(ringing * elapsed) + decay / ringing * sin(ringing * elapsed))
    }
    internal fun lineAt(index: Float): Float {
        return CANDLES[FIRST][1] + SLOPE * (index - FIRST)
    }
    internal fun chapterAt(time: Float) = CHAPTER_STARTS.indexOfLast { time >= it }.coerceAtLeast(0)
    internal fun camera(time: Float, still: Boolean): List<Float> {
        if (time >= FADE[1] || time < VIEWS[1][0]) return VIEWS[0].drop(1)
        if (time >= VIEWS.last()[0]) return VIEWS.last().drop(1)
        var k = 0
        while (time >= VIEWS[k + 1][0]) k++
        val a = VIEWS[k]; val b = VIEWS[k + 1]
        val u = (time - a[0]) / (b[0] - a[0])
        val e = if (still) (if (u < 0.5f) 0f else 1f) else u * u * (3f - 2f * u)
        return (1..3).map { a[it] + (b[it] - a[it]) * e }
    }
    internal fun growth(index: Int, time: Float, still: Boolean): Float {
        val at = PRINT_AT[index]
        val g = clamp01(if (index < BASE) {
            if (time < FADE[1]) 1f else (time - REPRINT - index * REPRINT_STEP) / PRINT_GROW
        } else {
            if (time >= FADE[1]) 0f else (time - at) / PRINT_GROW
        })
        return if (still) (if (g > 0f) 1f else 0f) else g
    }
}

internal object CandlePatternWalkthroughStyle {
    const val TITLE_SIZE = 22f
    const val TITLE_HEIGHT = 28f
    const val TITLE_TRACKING = -0.4f
    const val HEADER_PAD = 12f
    const val HINT_SIZE = 13f
    const val HINT_HEIGHT = 18f
    const val HINT_MARGIN = 4f
    const val FOOTER_PAD = 16f
    const val SEGMENT_HEIGHT = 3f
    const val SEGMENT_RADIUS = 1.5f
    const val CAPTION_MARGIN = 14f
    const val CAPTION_HEIGHT = 26f
    const val STEP_WIDTH = 32f
    const val STEP_SIZE = 13f
    const val STEP_HEIGHT = 18f
    const val CHAPTER_SIZE = 19f
    const val CHAPTER_HEIGHT = 24f
    const val CHAPTER_IN = 0.18f
    const val CHAPTER_OUT = 0.12f
    const val PAN_SLOP = 12f
    const val TAP_SLOP = 10f
    const val STOP_EPSILON = 0.01f
    const val SCRUB_END_EPSILON = 0.001f
    const val UNBLURRED_GLOW = 0.15f

    const val PITCH = 13f
    const val BODY_RATIO = 0.58f
    const val WICK = 1.5f
    const val UNIT = 11f
    const val MIN_CANDLES_ACROSS = 30f
    const val MIN_PRICE_SPAN = 54f
    const val GLOW_BLUR = 6f
    const val GLOW_ALPHA = 0.45f
    const val LINE_WIDTH = 2.5f
    const val CIRCLE_RADIUS = 7f
    const val CIRCLE_WIDTH = 1.5f
    const val ARROW_WIDTH = 2f
    const val ARROW_HEAD = 9f
    const val DOT = 3.5f
    const val CONFETTI_WIDTH = 2.6f
    const val GRID_STEP = 16f
    const val GRID_DOT = 0.9f
    const val HEADER = 72f
    const val FOOTER = 92f
    const val EDGE_FADE = 36f
    const val MARGIN = 20f
    const val SEGMENT_GAP = 4f
    const val LABEL_SIZE = 17f
    const val LABEL_LINE = 18f
    const val BIG_SIZE = 21f
    const val SMALL_SIZE = 13f
    const val RATIO_SIZE = 26f
    const val POP_OMEGA = 22f
    const val POP_DAMPING = 0.5f
    const val SLIDE_OMEGA = 11f
    const val SLIDE_DAMPING = 0.78f
    const val LABEL_FADE = 0.15f
    const val POP_FADE = 0.06f
    const val USER_HOLD = 3f
    const val VEIL = 0.35f
    const val CONFETTI_BLUR = 4f
    val BACKGROUND = Color(0xFF000000)
    val INK = Color(0xFFFFFFFF)
    val MUTE = Color(0xFF7D7D7D)
    val TRACK = Color(0xFF262626)
    val GRID = Color(0xFF151515)
    val RISK = Color(0xFF7A2E3E)
    val REWARD = rgba(31, 77, 69, 0.85f)
    val DASH = listOf(7f, 6f)
    val CONFETTI_COLORS = listOf(Color(0xFF3DE0FF), Color(0xFFD86BFF), Color(0xFFFFB03B), Color.White)
}

private val M = CandlePatternWalkthroughMotion
private val S = CandlePatternWalkthroughStyle
private fun <T> choose(condition: Boolean, yes: T, no: T): T = if (condition) yes else no
private fun f(value: Int) = value.toFloat()
private fun whole(value: Float) = value.toInt().toString()
private fun prefix(value: String, fraction: Float) = value.take(ceil(fraction * value.length).toInt())

private class Pen {
    val path = android.graphics.Path()
    var roundCaps = true
    var roundJoin = false
    fun move(x: Float, y: Float) = path.moveTo(x, y)
    fun line(x: Float, y: Float) = path.lineTo(x, y)
    fun rect(x: Float, y: Float, w: Float, h: Float) { if (w > 0f && h > 0f) path.addRect(x, y, x + w, y + h, android.graphics.Path.Direction.CW) }
    fun circle(x: Float, y: Float, r: Float) = path.addCircle(x, y, r, android.graphics.Path.Direction.CW)
    fun arc(x: Float, y: Float, r: Float, start: Float, sweep: Float) = path.arcTo(android.graphics.RectF(x - r, y - r, x + r, y + r), start, sweep, true)
    fun close() = path.close()
}

private class Ink(val canvas: android.graphics.Canvas) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun fill(path: Pen, color: Color, alpha: Float) {
        paint.style = Paint.Style.FILL; paint.color = color.toArgb(); paint.alpha = (color.alpha * alpha * 255f).toInt().coerceIn(0, 255); paint.pathEffect = null
        canvas.drawPath(path.path, paint)
    }
    fun stroke(path: Pen, color: Color, width: Float, alpha: Float, trim: Float, dash: List<Float>) {
        if (trim <= 0f || alpha <= 0f) return
        paint.style = Paint.Style.STROKE; paint.color = color.toArgb(); paint.alpha = (color.alpha * alpha * 255f).toInt().coerceIn(0, 255)
        paint.strokeWidth = width; paint.strokeCap = if (path.roundCaps) Paint.Cap.ROUND else Paint.Cap.BUTT; paint.strokeJoin = if (path.roundJoin) Paint.Join.ROUND else Paint.Join.MITER
        paint.pathEffect = if (dash.isEmpty()) null else DashPathEffect(dash.toFloatArray(), 0f)
        // The arrow has two contours. Measure all of them so its head draws after its shaft.
        var drawn = path.path
        if (trim < 1f) {
            val measure = android.graphics.PathMeasure(path.path, false)
            var total = 0f
            do { total += measure.length } while (measure.nextContour())
            measure.setPath(path.path, false)
            var left = total * trim
            drawn = android.graphics.Path()
            do {
                val length = measure.length
                if (left > 0f) measure.getSegment(0f, min(left, length), drawn, true)
                left -= length
            } while (left > 0f && measure.nextContour())
        }
        canvas.drawPath(drawn, paint)
    }
    fun label(lines: List<String>, typed: String?, x: Float, y: Float, rotation: Float, scale: Float, alpha: Float, size: Float, black: Boolean) {
        if (alpha <= 0f || scale <= 0f) return
        paint.style = Paint.Style.FILL; paint.pathEffect = null; paint.color = Color.White.toArgb(); paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255); paint.textSize = size
        paint.typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, if (black) 900 else 800, false) else Typeface.create("sans-serif-black", Typeface.NORMAL)
        val save = canvas.save()
        canvas.translate(x, y); canvas.rotate(rotation * 180f / PI.toFloat()); canvas.scale(scale, scale)
        lines.forEachIndexed { index, line -> canvas.drawText(typed ?: line, -paint.measureText(line) / 2f, -(lines.size - 1 - index) * S.LABEL_LINE, paint) }
        canvas.restoreToCount(save)
    }
}

internal data class CandlePatternWalkthroughDrawing(
    val swingHighLabel: String, val trendLineLabel: String, val breakoutLabel: String, val retestLabel: String,
    val confirmationLabel: String, val ratioPrefix: String, val upColor: Color, val downColor: Color,
) {
    fun draw(scope: DrawScope, width: Float, height: Float, t: Float, veil: Float, still: Boolean, pass: Int) = with(scope) {
        drawIntoCanvas { canvas ->
            val ink = Ink(canvas.nativeCanvas)
            val swingLines = swingHighLabel.split("\n")
            val view = M.camera(t, still)
            val px = min(S.PITCH, width / S.MIN_CANDLES_ACROSS)
            val py = min(S.UNIT, height / S.MIN_PRICE_SPAN)
            fun x(index: Float) = width / 2f + (index - view[0]) * px * view[2]
            fun y(price: Float) = height / 2f - (price - view[1]) * py * view[2]
            val v = M.camera(t, still)
            val pitch = min(S.PITCH, width / S.MIN_CANDLES_ACROSS)
            val unit = min(S.UNIT, height / S.MIN_PRICE_SPAN)
            val ratioOf = pitch / S.PITCH
            val scale = v[2] * ratioOf
            val chartOpacity = choose(t >= M.FADE[1], 1f, 1f - M.smooth(t, M.FADE[0], M.FADE[1])) * veil
            val notesOpacity = choose(t >= M.FADE[1], 0f, 1f - M.smooth(t, M.FADE[0], M.FADE[1])) * veil
            val notesOut = 1f - M.smooth(t, M.NOTES_OUT[0], M.NOTES_OUT[1])
            val angle = atan2(-(M.lineAt(1f) - M.lineAt(0f)) * unit, pitch)
            if (pass == 0) {
                val grid = Pen()
                var gx = S.GRID_STEP / 2f
                while (gx < width) {
                    var gy = S.GRID_STEP / 2f
                    while (gy < height) { grid.circle(gx, gy, S.GRID_DOT); gy += S.GRID_STEP }
                    gx += S.GRID_STEP
                }
                ink.fill(grid, S.GRID, 1f)
            }
            if (pass == 0 || pass == 5) {
                val slide = choose(still, 1f, M.spring(t - M.BOX_IN, S.SLIDE_OMEGA, S.SLIDE_DAMPING))
                val home = x(M.ZONE_FROM)
                val bx = home + (1f - slide) * max(0f, width - home)
                val bw = f(M.ZONE_CANDLES) * pitch * v[2]
                val right = M.smooth(t, M.ZONE_RIGHT[0], M.ZONE_RIGHT[1])
                val up = M.smooth(t, M.ZONE_UP[0], M.ZONE_UP[1])
                val multiple = 1f + f(M.RATIO - 1) * up
                val entry = y(M.ENTRY)
                val top = y(M.ENTRY + (M.ENTRY - M.STOP) * multiple)
                val shown = choose(still, M.smooth(t, M.BOX_IN, M.BOX_IN + S.LABEL_FADE), choose(t >= M.BOX_IN, 1f, 0f)) * notesOpacity
                if (pass == 0) {
                    val risk = Pen(); risk.rect(bx, entry, bw, y(M.STOP) - entry)
                    val reward = Pen(); reward.rect(bx, top, bw * right, entry - top)
                    ink.fill(reward, S.REWARD, shown)
                    ink.fill(risk, S.RISK, shown)
                } else {
                    val count = min(f(M.RATIO), 1f + floor(up * f(M.RATIO - 1) + 0.5f))
                    ink.label(listOf(ratioPrefix + whole(f(M.RATIO))), ratioPrefix + whole(count), bx + bw / 2f, (entry + y(M.STOP)) / 2f + S.RATIO_SIZE * 0.36f * scale, 0f, scale, shown, S.RATIO_SIZE, true)
                }
            }
            if (pass == 1 || pass == 2) {
                val ups = Pen(); val downs = Pen()
                for (i in 0 until M.CANDLES.size) {
                    val c = M.CANDLES[i]
                    val g = M.growth(i, t, still)
                    val cx = x(f(i))
                    val reach = pitch * v[2]
                    if (g > 0f && cx >= -reach && cx <= width + reach) {
                        val p = choose(c[3] >= c[0], ups, downs)
                        val now = c[0] + (c[3] - c[0]) * g
                        val top = y(max(c[0], now))
                        val bottom = y(min(c[0], now))
                        val high = y(c[0] + (c[1] - c[0]) * g)
                        val low = y(c[0] + (c[2] - c[0]) * g)
                        val body = reach * S.BODY_RATIO
                        val wick = max(1f, S.WICK * ratioOf * v[2])
                        p.rect(cx - body / 2f, top, body, max(1f, bottom - top))
                        p.rect(cx - wick / 2f, high, wick, low - high)
                    }
                }
                val alpha = chartOpacity * choose(pass == 1, S.GLOW_ALPHA, 1f)
                ink.fill(ups, upColor, alpha); ink.fill(downs, downColor, alpha)
            }
            if (pass == 3) {
                val circles = Pen(); circles.roundCaps = false
                val off = 1f - M.smooth(t, M.SWING_OUT[0], M.SWING_OUT[1])
                for (k in 0 until M.SWING_HIGHS.size) {
                    val i = M.SWING_HIGHS[k]
                    val at = M.SWING_AT + f(k) * M.SWING_STEP
                    val p = M.clamp01((t - at) / M.CIRCLE_IN)
                    if (p > 0f) { circles.arc(x(f(i)), y(M.CANDLES[i][1]), S.CIRCLE_RADIUS * scale, -90f, choose(still, 359.9f, min(359.9f, 360f * p))) }
                    val shown = choose(still, M.smooth(t, at, at + S.LABEL_FADE), M.clamp01((t - at) / S.POP_FADE))
                    val pop = choose(still, 1f, M.spring(t - at, S.POP_OMEGA, S.POP_DAMPING))
                    ink.label(swingLines, null, x(f(i)), y(M.CANDLES[i][1]) - (S.CIRCLE_RADIUS + 7f) * scale, 0f, scale * (0.4f + 0.6f * pop), shown * off * notesOpacity, S.LABEL_SIZE, false)
                }
                ink.stroke(circles, S.INK, S.CIRCLE_WIDTH, off * notesOpacity, 1f, emptyList())
            }
            if (pass == 4 || pass == 5) {
                val line = Pen(); line.move(x(f(M.SWING_HIGHS[0])), y(M.lineAt(f(M.SWING_HIGHS[0])))); line.line(x(f(M.LINE_TO)), y(M.lineAt(f(M.LINE_TO))))
                val u = M.clamp01((t - M.TREND_DRAW[0]) / (M.TREND_DRAW[1] - M.TREND_DRAW[0]))
                val trim = choose(still, choose(t >= M.TREND_DRAW[0], 1f, 0f), 1f - (1f - u) * (1f - u))
                val shown = choose(still, M.smooth(t, M.TREND_DRAW[0], M.TREND_DRAW[0] + S.LABEL_FADE), 1f)
                ink.stroke(line, S.INK, S.LINE_WIDTH * choose(pass == 4, 2f, 1f), shown * notesOpacity * choose(pass == 4, 0.35f, 1f), trim, emptyList())
            }
            if (pass == 5) {
                val trendShown = choose(still, M.smooth(t, M.TREND_TYPE[0], M.TREND_TYPE[0] + S.LABEL_FADE), choose(t >= M.TREND_TYPE[0], 1f, 0f)) * notesOpacity
                val typed = prefix(trendLineLabel, choose(still, 1f, M.clamp01((t - M.TREND_TYPE[0]) / (M.TREND_TYPE[1] - M.TREND_TYPE[0]))))
                ink.label(listOf(trendLineLabel), typed, x(7.4f) + sin(angle) * 10f * scale, y(M.lineAt(7.4f)) - cos(angle) * 10f * scale, angle, scale, trendShown, S.BIG_SIZE, false)
                val ax = x(f(M.BREAK_FROM) + 0.4f); val ay = y(M.CANDLES[M.BREAK_FROM][2] - 1.6f)
                val bx = x(f(M.PEAK) + 1.4f); val by = y(M.CANDLES[M.PEAK][1] + 3.5f)
                val length = hypot(bx - ax, by - ay)
                val ux = (bx - ax) / length; val uy = (by - ay) / length
                val head = S.ARROW_HEAD * scale
                val arrow = Pen(); arrow.roundJoin = true; arrow.move(ax, ay); arrow.line(bx, by)
                arrow.move(bx - head * (ux - uy * 0.7f), by - head * (uy + ux * 0.7f)); arrow.line(bx, by); arrow.line(bx - head * (ux + uy * 0.7f), by - head * (uy - ux * 0.7f))
                val arrowEnd = choose(still, choose(t >= M.ARROW_DRAW[0], 1f, 0f), M.smooth(t, M.ARROW_DRAW[0], M.ARROW_DRAW[1]))
                val breakShown = choose(still, M.smooth(t, M.ARROW_DRAW[0], M.ARROW_DRAW[0] + S.LABEL_FADE), choose(t >= M.ARROW_DRAW[0], 1f, 0f)) * (1f - M.smooth(t, M.BREAK_OUT[0], M.BREAK_OUT[1]))
                ink.stroke(arrow, S.INK, S.ARROW_WIDTH, breakShown * notesOpacity, arrowEnd, emptyList())
                val dot = Pen(); dot.circle(ax, ay, S.DOT * scale); ink.fill(dot, S.INK, breakShown * notesOpacity)
                val pop = choose(still, 1f, M.spring(t - M.ARROW_DRAW[0] - 0.1f, S.POP_OMEGA, S.POP_DAMPING))
                ink.label(listOf(breakoutLabel), null, ax, ay + 28f * scale, 0f, scale * (0.4f + 0.6f * pop), breakShown * notesOpacity * choose(still, 1f, M.clamp01((t - M.ARROW_DRAW[0] - 0.1f) / S.POP_FADE)), S.BIG_SIZE, false)
                val zig = Pen(); zig.move(x(f(M.BREAK_FROM) + 1.6f), y(M.lineAt(f(M.BREAK_FROM) + 1.6f))); zig.line(x(f(M.PEAK)), y(M.CANDLES[M.PEAK][1])); zig.line(x(f(M.RETEST)), y(M.CANDLES[M.RETEST][2]))
                val zigEnd = choose(still, choose(t >= M.ZIGZAG_DRAW[0], 1f, 0f), M.smooth(t, M.ZIGZAG_DRAW[0], M.ZIGZAG_DRAW[1]))
                val zigShown = choose(still, M.smooth(t, M.ZIGZAG_DRAW[0], M.ZIGZAG_DRAW[0] + S.LABEL_FADE), 1f) * notesOut * notesOpacity
                ink.stroke(zig, S.INK, S.ARROW_WIDTH, zigShown, zigEnd, S.DASH)
                val retestShown = choose(still, M.smooth(t, M.RETEST_LABEL, M.RETEST_LABEL + S.LABEL_FADE), M.clamp01((t - M.RETEST_LABEL) / 0.12f)) * notesOut * notesOpacity
                ink.label(listOf(retestLabel), null, x(f(M.PEAK) - 0.6f) - sin(angle) * 14f * scale, y(M.lineAt(f(M.PEAK) - 0.6f)) + cos(angle) * 14f * scale, angle, scale, retestShown, S.SMALL_SIZE, false)
                val c = M.CANDLES[M.CONFIRM]; val pad = 3f * scale
                val half = pitch * v[2] * S.BODY_RATIO / 2f + pad
                val top = y(min(c[1], c[3])) - pad
                val box = Pen(); box.roundCaps = false; box.rect(x(f(M.CONFIRM)) - half, top, half * 2f, y(c[0]) + pad - top)
                val confirmShown = choose(still, M.smooth(t, M.CONFIRM_MARK, M.CONFIRM_MARK + S.LABEL_FADE), M.clamp01((t - M.CONFIRM_MARK) / 0.1f)) * notesOut * notesOpacity
                ink.stroke(box, S.INK, S.CIRCLE_WIDTH, confirmShown, 1f, emptyList())
                val rise = choose(still, 0f, (1f - M.smooth(t, M.CONFIRM_MARK, M.CONFIRM_MARK + 0.25f)) * 8f)
                ink.label(listOf(confirmationLabel), null, x(f(M.CONFIRM)), y(c[1]) - (10f - rise) * scale, 0f, scale, confirmShown, S.SMALL_SIZE, false)
            }
            if (pass == 6 || pass == 7) {
                val u = t - M.BURST
                if (u >= 0f && u <= M.BURST_FOR) {
                    val elapsed = choose(still, M.BURST_FOR * 0.45f, u)
                    val shown = choose(still, min(M.smooth(u, 0f, 0.15f), 1f - M.smooth(u, M.BURST_FOR - 0.2f, M.BURST_FOR)), 1f - M.smooth(u, M.BURST_FOR * 0.55f, M.BURST_FOR)) * notesOpacity
                    for (slot in 0 until S.CONFETTI_COLORS.size) {
                        val path = Pen(); path.roundJoin = true; path.roundCaps = pass == 7
                        for (k in 0 until M.CONFETTI.size) {
                            val p = M.CONFETTI[k]
                            if (p[3] == f(slot)) {
                                val cx = x(f(M.LAST)) + p[0] * elapsed
                                val cy = y(M.CANDLES[M.LAST][1]) - p[1] * elapsed + 0.5f * M.GRAVITY * elapsed * elapsed
                                val turn = p[4] * elapsed * TAU
                                val r = p[5] / 2f
                                if (p[2] == 0f) { path.circle(cx, cy, r) }
                                else {
                                    val corners = choose(p[2] == 2f, 3, 4)
                                    val start = choose(p[2] == 2f, -PI.toFloat() / 2f, PI.toFloat() / 4f)
                                    for (c in 0 until corners) {
                                        val a = turn + start + f(c) * TAU / f(corners)
                                        val px = cx + cos(a) * r * choose(p[2] == 3f, 1.2f, 1f)
                                        val py = cy + sin(a) * r * choose(p[2] == 3f, 1.2f, 1f)
                                        if (p[2] == 1f) { if (c < 2) { path.move(px, py); path.line(2f * cx - px, 2f * cy - py) } }
                                        else if (c == 0) { path.move(px, py) }
                                        else { path.line(px, py) }
                                    }
                                    if (p[2] != 1f) { path.close() }
                                }
                            }
                        }
                        ink.stroke(path, S.CONFETTI_COLORS[slot], S.CONFETTI_WIDTH * choose(pass == 6, 2f, 1f), shown * choose(pass == 6, 0.5f, 1f), 1f, emptyList())
                    }
                }
            }
        }
    }
}
