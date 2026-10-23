package com.ngocdevv.nativecook.blocks.charts.pixelspendstaircase

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// May: steady days, rent on the 18th, and $123 today. April ran ahead of it from the 6th on.
private val SPENDING = listOf(620.0, 560.0, 70.0, 60.0, 250.0, 590.0, 90.0, 780.0, 70.0, 290.0, 320.0, 310.0, 70.0, 60.0, 480.0, 80.0, 320.0, 3030.0, 470.0, 613.0, 123.0)
private val PREVIOUS = listOf(
    400.0, 500.0, 100.0, 150.0, 250.0, 1160.0, 60.0, 1080.0, 60.0, 840.0, 50.0, 250.0, 300.0, 300.0, 700.0, 1500.0, 80.0, 820.0, 600.0, 60.0, 40.0, 40.0, 60.0, 50.0, 60.0, 57.0,
)

private object K {
    val BACKGROUND = Color(0xFF1A1A1A)
    val INK = Color(0xFFF2F2F2)
    val MUTE = Color(0xFF8A8A8A)
    val FAINT = Color(0xFF6F6F6F)
    val CHEVRON = Color(0xFF4A4A4A)
    // Cells by height, bottom to top: the bottom rows sink into the screen.
    val GREY_STOPS = listOf(0.0 to 0x1F1F1F, 0.14 to 0x2A2A2A, 1.0 to 0x2C2C2C)
    val EMBER_STOPS = listOf(0.0 to 0x2E1D15, 0.1 to 0x47291A, 0.4 to 0x8E4321, 0.7 to 0xD2552A, 0.86 to 0xEE6430, 1.0 to 0xF56B32)
    val CURSOR = Color(0xFFFF4D26)
    val FLASH = Color(0xFFFFD9C2)

    // The grid: cells fill the width with a hairline gap; rows are as many as fit, up to MAX_ROWS.
    const val SIDE = 4f
    const val GAP = 1.5f
    const val RADIUS = 1.5f
    const val MAX_ROWS = 31
    // Room above the tallest column for the floating labels. Last month's label sits this many rows
    // higher than the cursor's would, so the two never meet at the end of the month.
    const val LABEL_SPACE = 76f
    const val PREVIOUS_RISE = 2f
    const val LABEL_GAP = 8f
    const val LABEL_WIDTH = 180f
    const val LABEL_LINE = 20f
    // The glow behind the cursor cell, in cells, and its blur (a Skia sigma) in dp.
    const val GLOW = 1.7f
    const val GLOW_BLUR = 5f
    const val GLOW_ALPHA = 0.32
    const val GLOW_RADIUS = 3f

    // Seconds. A column lights from the bottom up, one row every ROW_ON, and goes dark from the top.
    const val ROW_ON = 0.012
    const val ROW_OFF = 0.02
    const val CELL_IN = 0.09
    const val CELL_OUT = 0.12
    // Columns passed in one jump (a tap far from the cursor) start one after the other.
    const val COLUMN_STAGGER = 0.035
    // The top cell of a column flashes when it lights.
    const val FLASH_FOR = 0.35
    const val FLASH_PEAK = 0.75
    // A brighter band runs along the tops of the lit columns once the cursor is back at today.
    const val SHIMMER_AFTER = 0.15
    const val SHIMMER_FOR = 1.2
    const val SHIMMER_DEPTH = 4
    const val SHIMMER_WIDTH = 1.4
    const val SHIMMER_PEAK = 0.22
    const val REDUCED_FADE = 0.16
    // The amounts roll to their new value, and the label glides to its column.
    const val ROLL = 0.32
    const val LABEL_GLIDE = 0.18

    // The autoPlay timeline, in seconds from the start of each loop.
    const val LOOP = 9.0
    const val SCRUB_TO = 4
    const val LEFT_AT = 0.8
    const val LEFT_FOR = 1.8
    const val RIGHT_AT = 3.3
    const val RIGHT_FOR = 2.4
    // After a touch the block waits this long, then glides back to today and loops again.
    const val USER_HOLD = 3.0
    const val RETURN_FOR = 0.9

    const val HEADER_TOP = 36f
    const val AMOUNT = 72f

    fun clamp(value: Double) = value.coerceIn(0.0, 1.0)

    fun easeInOut(t: Double): Double {
        val x = clamp(t)
        return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    }

    fun easeOutCubic(t: Double) = 1 - (1 - clamp(t)).pow(3)

    /** The colour at [t] along a list of stops, mixed per channel in sRGB. */
    fun along(stops: List<Pair<Double, Int>>, t: Double): Int {
        var i = 0
        while (i < stops.size - 2 && t > stops[i + 1].first) i++
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        val u = ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val a = (c0 shr shift) and 0xFF
            val b = (c1 shr shift) and 0xFF
            return (a + (b - a) * u).roundToInt()
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun running(values: List<Double>): List<Double> {
        var sum = 0.0
        return values.map { sum += it; sum }
    }

    private val grouping = NumberFormat.getIntegerInstance(Locale.US)

    fun money(symbol: String, value: Double) = symbol + grouping.format(Math.round(value))

    /**
     * The times at which an ease-in-out glide from column [a] to [b], starting at [start], reaches
     * each column it passes (rounded as JavaScript rounds), with that column.
     */
    fun glide(a: Int, b: Int, start: Double, duration: Double): List<Pair<Double, Int>> {
        if (a == b) return emptyList()
        val step = if (b > a) 1 else -1
        return generateSequence(a + step) { it + step }.take(kotlin.math.abs(b - a)).map { column ->
            val edge = column - 0.5 * step
            val share = (edge - a) / (b - a)
            var lo = 0.0
            var hi = 1.0
            repeat(40) {
                val mid = (lo + hi) / 2
                if (easeInOut(mid) < share) lo = mid else hi = mid
            }
            start + hi * duration to column
        }.toList()
    }
}

/** A value rolling to a new one with an ease-out cubic. */
private data class Roll(val from: Double, val to: Double = from, val at: Double = 0.0, val duration: Double = 0.0) {
    fun value(t: Double) = if (duration <= 0) to else from + (to - from) * K.easeOutCubic((t - at) / duration)

    fun target(value: Double, t: Double, duration: Double) =
        if (value == to) this else Roll(value(t), value, t, duration)
}

/** Which columns are lit, when each last changed, and the cursor. */
private data class Grid(
    val on: List<Boolean>,
    val at: List<Double>,
    val selected: Int,
    val shimmerAt: Double = -100.0,
    val busyUntil: Double = 0.0,
)

/** Where everything sits for one chart size, in dp. */
private class Layout(
    val width: Float,
    val height: Float,
    totals: List<Double>,
    previousTotals: List<Double>,
    val columns: Int,
) {
    val pitch = if (width > 0) (width - K.SIDE * 2) / columns else 0f
    val rows = if (pitch > 0) max(4, min(K.MAX_ROWS, floor((height - K.LABEL_SPACE) / pitch).toInt())) else K.MAX_ROWS
    val left = (width - pitch * columns) / 2
    val floor = height
    // Column heights in cells: the taller month sets the scale.
    private val top = max(totals.lastOrNull() ?: 1.0, previousTotals.lastOrNull() ?: 1.0)
    private fun cells(value: Double) = if (value > 0) max(1, (value / top * rows).roundToInt()) else 0
    val lit = totals.map(::cells)
    val ghost = List(columns) { cells(previousTotals.getOrElse(it) { 0.0 }) }
    val grey = IntArray(rows) { K.along(K.GREY_STOPS, it / (rows - 1.0)) }
    val ember = IntArray(rows) { K.along(K.EMBER_STOPS, it / (rows - 1.0)) }

    fun cellLeft(c: Int) = left + c * pitch + K.GAP / 2
    fun cellTop(r: Int) = floor - (r + 1) * pitch + K.GAP / 2

    /** The top-left corner of the label over column [c], clear of last month's staircase. */
    fun label(c: Int): Offset {
        val h = max(lit.getOrElse(c) { 0 }, ghost[c])
        val x = min(width - K.LABEL_WIDTH / 2 - K.SIDE, max(K.LABEL_WIDTH / 2 + K.SIDE, left + c * pitch + pitch / 2))
        return Offset(x - K.LABEL_WIDTH / 2, floor - h * pitch - K.LABEL_GAP - K.LABEL_LINE)
    }
}

private class Paints {
    val cell = Paint(Paint.ANTI_ALIAS_FLAG)
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Hardware canvases honour mask filters from API 28; below, the glow is left out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(K.GLOW_BLUR), BlurMaskFilter.Blur.NORMAL)
    }
}

/**
 * A month's spending as a staircase of pixels: each column is a day, lit from the bottom up to the
 * month's running total, over last month's staircase in grey. Dragging moves the cursor (the bright
 * top cell): days after it go dark from the top, and moving forward lights them again in a rising
 * pixel sweep, while the amount and the label above the cursor roll to the day.
 */
@Composable
fun PixelSpendStaircase(
    modifier: Modifier = Modifier,
    /** What was spent on each day of this month so far; the last entry is today. */
    spending: List<Double> = SPENDING,
    /** What was spent on each day of last month, drawn as the grey staircase behind. */
    previousSpending: List<Double> = PREVIOUS,
    month: String = "May",
    previousMonth: String = "Apr",
    symbol: String = "$",
    todayLabel: String = "today",
    /** Shown under the chart while this month's total is below last month's on the same day. */
    lessLabel: String = "You’re spending less…",
    moreLabel: String = "You’re spending more…",
    /** Scrubs back to an early day and lights the month up again every LOOP seconds. */
    autoPlay: Boolean = false,
    /** Called with the day (1-based), that day's spend and the month's total up to it. */
    onSelect: ((Int, Double, Double) -> Unit)? = null,
    /** The chevron under the chart: where a detail sheet would open. */
    onOpenDetails: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val birth = remember { System.nanoTime() }
    val paints = remember { Paints() }

    val today = spending.size - 1
    val columns = max(spending.size, previousSpending.size)
    val totals = remember(spending) { K.running(spending) }
    val previousTotals = remember(previousSpending) { K.running(previousSpending) }

    var grid by remember(spending, previousSpending) {
        mutableStateOf(Grid(List(columns) { it <= today }, List(columns) { -100.0 }, today))
    }
    var amount by remember(spending) { mutableStateOf(Roll(spending[today])) }
    var total by remember(spending) { mutableStateOf(Roll(totals[today])) }
    var labelX by remember { mutableStateOf(Roll(0.0)) }
    var labelY by remember { mutableStateOf(Roll(0.0)) }
    var layout by remember { mutableStateOf<Layout?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableDoubleStateOf(0.0) }
    val time = remember { mutableDoubleStateOf(0.0) }
    // Read by the actions, which the loop and the gestures call long after the first composition.
    val still by rememberUpdatedState(reduced)
    val currentSpending by rememberUpdatedState(spending)
    val currentTotals by rememberUpdatedState(totals)
    val currentOnSelect by rememberUpdatedState(onSelect)

    fun clock() = (System.nanoTime() - birth) / 1e9

    // Moves the cursor to the column nearest x; columns passed light up or go dark.
    fun scrub(x: Double, byHand: Boolean, t: Double) {
        val l = layout ?: return
        if (l.pitch <= 0) return
        val now = grid
        val last = currentSpending.size - 1
        val next = floor(x + 0.5).toInt().coerceIn(0, last)
        val old = now.selected
        if (next == old) return
        val on = now.on.toMutableList()
        val at = now.at.toMutableList()
        val stagger = if (still) 0.0 else K.COLUMN_STAGGER
        var latest = t
        if (next > old) {
            for (c in old + 1..next) {
                on[c] = true
                at[c] = t + (c - old - 1) * stagger
                latest = at[c]
            }
        } else {
            for (c in old downTo next + 1) {
                on[c] = false
                at[c] = t + (old - c) * stagger
                latest = at[c]
            }
        }
        var shimmerAt = now.shimmerAt
        var busy = latest + l.rows * K.ROW_OFF + K.CELL_OUT + K.FLASH_FOR
        if (next == last && !still) {
            shimmerAt = at[last] + (l.lit[last] - 1) * K.ROW_ON + K.SHIMMER_AFTER
            busy = max(busy, shimmerAt + K.SHIMMER_FOR)
        }
        // The amounts roll and the label glides on the same clock.
        busy = max(busy, t + K.ROLL)
        grid = Grid(on, at, next, shimmerAt, max(now.busyUntil, busy))
        amount = amount.target(currentSpending[next], t, if (still) 0.0 else K.ROLL)
        total = total.target(currentTotals[next], t, if (still) 0.0 else K.ROLL)
        val point = l.label(next)
        labelX = labelX.target(point.x.toDouble(), t, if (still) 0.0 else K.LABEL_GLIDE)
        labelY = labelY.target(point.y.toDouble(), t, if (still) 0.0 else K.LABEL_GLIDE)
        if (byHand) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            currentOnSelect?.invoke(next + 1, currentSpending[next], currentTotals[next])
        }
    }

    fun touch() {
        lastTouch = clock()
        touches += 1
    }

    suspend fun waitUntil(at: Double) {
        while (true) {
            val left = at - clock()
            if (left <= 0) return
            delay((left * 1000).toLong().coerceAtLeast(1))
        }
    }

    // The clock only runs while something moves.
    LaunchedEffect(grid.busyUntil) {
        while (clock() <= grid.busyUntil) withFrameNanos { time.doubleValue = (it - birth) / 1e9 }
        time.doubleValue = clock()
    }

    // Each step is stamped with its time on the loop, not the time the coroutine woke, so every loop is identical.
    LaunchedEffect(autoPlay, touches, layout != null) {
        if (!autoPlay || layout == null) return@LaunchedEffect
        if (touches > 0) {
            waitUntil(lastTouch + K.USER_HOLD)
            if (dragging) return@LaunchedEffect
        }
        val last = currentSpending.size - 1
        val now = clock()
        val from = grid.selected
        var start = now
        if (from != last && !still) {
            start = now + K.RETURN_FOR
            for ((at, column) in K.glide(from, last, now, K.RETURN_FOR)) {
                waitUntil(at)
                scrub(column.toDouble(), false, at)
            }
        }
        val low = min(K.SCRUB_TO, last)
        // Under reduced motion the cursor jumps instead of travelling.
        val steps = if (still) {
            listOf(K.LEFT_AT to low, K.RIGHT_AT to last)
        } else {
            K.glide(last, low, K.LEFT_AT, K.LEFT_FOR) + K.glide(low, last, K.RIGHT_AT, K.RIGHT_FOR)
        }
        while (true) {
            for ((at, column) in steps) {
                waitUntil(start + at)
                scrub(column.toDouble(), false, start + at)
            }
            start += K.LOOP
        }
    }

    val day = grid.selected
    val spend by remember { derivedStateOf { amount.value(time.doubleValue).roundToInt() } }
    val sum by remember { derivedStateOf { total.value(time.doubleValue).roundToInt() } }
    val isToday = day == today
    val dayName = "$month ${day + 1}"
    val previousAtDay = previousTotals.getOrElse(min(day, previousTotals.size - 1)) { 0.0 }
    val semibold15 = TextStyle(fontSize = 15.sp, lineHeight = K.LABEL_LINE.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false))

    Column(modifier.fillMaxSize().background(K.BACKGROUND).padding(top = insets.calculateTopPadding())) {
        Column(
            Modifier.fillMaxWidth().padding(top = K.HEADER_TOP.dp).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                K.money(symbol, spend.toDouble()),
                Modifier.height(84.dp),
                style = TextStyle(
                    color = K.INK, fontSize = K.AMOUNT.sp, lineHeight = 84.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp, fontFeatureSettings = "tnum", textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = (K.AMOUNT / 2).sp, maxFontSize = K.AMOUNT.sp),
            )
            BasicText(
                if (isToday) todayLabel else dayName,
                Modifier.offset(y = (-6).dp).height(30.dp),
                style = TextStyle(color = K.MUTE, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // 8 dp under the subtitle, which is drawn 6 dp up into the amount's line.
                .padding(top = 2.dp)
                .clearAndSetSemantics {
                    contentDescription = "Spending in $month"
                    stateDescription = "$dayName: ${K.money(symbol, spend.toDouble())}, ${K.money(symbol, sum.toDouble())} so far"
                    progressBarRangeInfo = ProgressBarRangeInfo(day.toFloat(), 0f..today.toFloat(), max(0, today - 1))
                    setProgress { target ->
                        touch()
                        scrub(target.toDouble(), true, clock())
                        true
                    }
                },
        ) {
            val chart = remember(maxWidth, maxHeight, totals, previousTotals) {
                Layout(maxWidth.value, maxHeight.value, totals, previousTotals, columns)
            }
            LaunchedEffect(chart) {
                // The label starts over the cursor's column without a glide.
                layout = chart
                val point = chart.label(grid.selected)
                labelX = Roll(point.x.toDouble())
                labelY = Roll(point.y.toDouble())
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(chart) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            dragging = true
                            touch()
                            scrub((down.position.x / density - chart.left) / chart.pitch - 0.5, true, clock())
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                touch()
                                scrub((change.position.x / density - chart.left) / chart.pitch - 0.5, true, clock())
                            }
                            dragging = false
                            touch()
                        }
                    },
            ) {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { drawGrid(it.nativeCanvas, chart, grid, time.doubleValue, today, still, paints) }
                }
            }
            if (chart.pitch > 0) {
                val lastGhost = chart.ghost.lastOrNull() ?: 0
                BasicText(
                    "$previousMonth: ${K.money(symbol, previousTotals.lastOrNull() ?: 0.0)}",
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = (K.SIDE + 2).dp)
                        .offset(y = (chart.floor - (lastGhost + K.PREVIOUS_RISE) * chart.pitch - K.LABEL_GAP - K.LABEL_LINE).dp),
                    style = semibold15.copy(color = K.FAINT),
                    maxLines = 1,
                )
                BasicText(
                    "${if (isToday) month else dayName}: ${K.money(symbol, sum.toDouble())}",
                    Modifier
                        .offset {
                            val t = time.doubleValue
                            IntOffset((labelX.value(t) * density).roundToInt(), (labelY.value(t) * density).roundToInt())
                        }
                        .width(K.LABEL_WIDTH.dp),
                    style = semibold15.copy(color = K.MUTE, textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                // The chevron's touch room reaches 6 dp into this.
                .padding(top = 14.dp, bottom = (max(insets.calculateBottomPadding().value, 16f) - 6).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                if (sum > previousAtDay) moreLabel else lessLabel,
                Modifier.height(24.dp),
                style = TextStyle(color = K.MUTE, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1,
            )
            // The chevron keeps 12 dp of touch room on every side: 22 dp above it, 6 dp below.
            Box(
                Modifier
                    .padding(top = (22 - 12).dp)
                    .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { onOpenDetails?.invoke() }
                    .semantics { contentDescription = "Details"; role = Role.Button }
                    .padding(12.dp),
            ) {
                Canvas(Modifier.size(56.dp, 16.dp)) {
                    val path = Path().apply {
                        moveTo(4.dp.toPx(), 4.dp.toPx())
                        lineTo(28.dp.toPx(), 12.dp.toPx())
                        lineTo(52.dp.toPx(), 4.dp.toPx())
                    }
                    drawPath(path, K.CHEVRON, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

private fun drawGrid(canvas: android.graphics.Canvas, l: Layout, grid: Grid, now: Double, today: Int, still: Boolean, paints: Paints) {
    if (l.pitch <= 0) return
    val paint = paints.cell
    val side = l.pitch - K.GAP
    val shimmer = (now - grid.shimmerAt) / K.SHIMMER_FOR
    val band = if (shimmer in 0.0..1.0) -2 + shimmer * (today + 4) else null
    val cursor = grid.selected
    var cursorLit = 0.0
    val flash = K.FLASH.toArgb()

    fun fill(c: Int, r: Int, color: Int, alpha: Double) {
        paint.color = color
        paint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        val x = l.cellLeft(c)
        val y = l.cellTop(r)
        canvas.drawRoundRect(x, y, x + side, y + side, K.RADIUS, K.RADIUS, paint)
    }

    for (c in 0 until l.columns) {
        val own = if (c <= today) l.lit[c] else 0
        val height = max(own, l.ghost[c])
        val since = now - grid.at[c]
        for (r in 0 until height) {
            fill(c, r, l.grey[r], 1.0)
            if (r >= own) continue
            val level = when {
                still -> if (grid.on[c]) K.clamp(since / K.REDUCED_FADE) else 1 - K.clamp(since / K.REDUCED_FADE)
                grid.on[c] -> K.clamp((since - r * K.ROW_ON) / K.CELL_IN)
                else -> 1 - K.clamp((since - (own - 1 - r) * K.ROW_OFF) / K.CELL_OUT)
            }
            if (level <= 0) continue
            fill(c, r, l.ember[r], level)
            // The top cell flashes as it lights, and the shimmer brightens the top few rows.
            var glint = 0.0
            if (r == own - 1 && grid.on[c] && !still) {
                val lighted = since - r * K.ROW_ON
                if (lighted >= 0) glint = K.FLASH_PEAK * (1 - K.clamp(lighted / K.FLASH_FOR))
            }
            val depth = own - 1 - r
            if (depth < K.SHIMMER_DEPTH && band != null) {
                val d = (c - band) / K.SHIMMER_WIDTH
                glint = max(glint, K.SHIMMER_PEAK * exp(-d * d) * (1 - depth.toDouble() / K.SHIMMER_DEPTH))
            }
            if (glint > 0.005) fill(c, r, flash, glint * level)
            if (c == cursor && r == own - 1) cursorLit = level
        }
    }

    // The cursor: the top cell of the selected day, red, in a soft square glow.
    val top = l.lit[cursor] - 1
    if (cursorLit <= 0 || top < 0) return
    val halo = l.pitch * K.GLOW
    val cx = l.left + cursor * l.pitch + l.pitch / 2
    val cy = l.floor - (top + 0.5f) * l.pitch
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        paints.glow.color = K.CURSOR.toArgb()
        paints.glow.alpha = (K.GLOW_ALPHA * cursorLit * 255).roundToInt()
        canvas.drawRoundRect(cx - halo / 2, cy - halo / 2, cx + halo / 2, cy + halo / 2, K.GLOW_RADIUS, K.GLOW_RADIUS, paints.glow)
    }
    fill(cursor, top, K.CURSOR.toArgb(), cursorLit)
}
