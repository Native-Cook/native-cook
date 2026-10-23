package com.ngocdevv.nativecook.blocks.charts.dotmatrixrevenue

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class RevenueGroup(val label: String, val total: Float, val heights: List<Int>)
data class RevenueRange(val name: String, val change: Float, val groups: List<RevenueGroup>)

val SampleRanges = listOf(
    RevenueRange("DAILY", 33f, listOf(
        RevenueGroup("MON", 412f, listOf(1, 1, 1, 2, 3, 5, 4, 3, 4, 6, 5, 3, 2, 2, 3, 4, 3, 2, 2, 1, 1, 1, 1)),
        RevenueGroup("TUE", 548f, listOf(1, 1, 2, 3, 4, 7, 6, 4, 5, 8, 9, 6, 4, 3, 4, 6, 5, 4, 3, 2, 2, 1, 1)),
    )),
    RevenueRange("WEEKLY", 84f, listOf(
        RevenueGroup("WK 24", 2910f, listOf(2, 3, 5, 4, 6, 3, 2, 1, 3, 4, 7, 5, 3, 2, 2, 4, 5, 3, 2, 1, 2, 3, 2)),
        RevenueGroup("WK 25", 5336f, listOf(3, 5, 8, 6, 4, 9, 11, 7, 5, 3, 4, 6, 8, 10, 7, 5, 3, 2, 4, 6, 5, 3, 2)),
    )),
    RevenueRange("MONTHLY", 326f, listOf(
        RevenueGroup("MAY", 3250f, listOf(1, 2, 4, 8, 3, 3, 4, 6, 3, 1, 2, 2, 3, 3, 4, 3, 3, 1, 2, 2, 2)),
        RevenueGroup("JUN", 12392f, listOf(6, 7, 12, 10, 8, 9, 6, 5, 4, 5, 2, 1, 3, 2, 1, 5, 2, 1, 1, 4, 3, 5, 3, 4, 2)),
    )),
)

private object S {
    val page = Color(0xFFEEEEEE)
    val card = Color(0xFFF7F7F7)
    val line = Color(0xFFE4E4E4)
    val ink = Color(0xFF111111)
    val mute = Color(0xFFA0A0A0)
    const val dark = 74.0f
    const val light = 201.0f
    const val shades = 16
    const val margin = 16.0f
    const val maxCard = 420.0f
    const val radius = 32.0f
    const val pad = 22.0f
    const val header = 98.0f
    const val rows = 14
    const val chartTop = 18.0f
    const val dot = 0.46f
    const val edgeColumns = 4
    const val edgeAlpha = 0.2f
    const val columnStagger = 0.018f
    const val dotStagger = 0.04f
    const val leaveFor = 0.26f
    const val leaveRise = 14.0f
    const val dropFor = 0.42f
    const val drop = 40.0f
    const val overshoot = 1.9f
    const val waveStagger = 0.025f
    const val shadeFor = 0.14f
    const val lift = 3.0f
    const val liftFor = 0.34f
    const val rowLag = 0.008f
    const val rippleAfter = 0.95f
    const val rippleStagger = 0.016f
    const val reducedFade = 0.16f
    const val count = 0.7f
    const val underline = 0.26f
    const val colorFor = 0.2f
    const val loop = 9.0f
    const val highlightAt = 0.8f
    const val highlightColumn = 9.0f
    const val weeklyAt = 2.6f
    const val weekly = 1
    const val backAt = 5.2f
    const val userHold = 3.0f
    const val settle = 2.0f
    const val headerTop = 24.0f
    const val titleSize = 13.0f
    const val titleLine = 18.0f
    const val titleSpacing = 0.5f
    const val headlineTop = 4.0f
    const val changeSize = 40.0f
    const val changeLine = 46.0f
    const val changeSpacing = -0.5f
    const val percentSize = 20.0f
    const val percentGap = 4.0f
    const val tabGap = 14.0f
    const val tabSize = 12.0f
    const val tabLine = 18.0f
    const val tabSpacing = 0.3f
    const val underlineTop = 21.0f
    const val captions = 52.0f
    const val captionTop = 10.0f
    const val captionSize = 13.0f
    const val captionLine = 18.0f
    val cssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    fun clamp(x: Float) = x.coerceIn(0f, 1f)
    fun out(x: Float): Float { val u = 1f - clamp(x); return 1f - u * u * u }
    fun backOut(t: Float): Float { val x = t - 1f; return 1f + (overshoot + 1f) * x * x * x + overshoot * x * x }
    fun rounded(x: Float) = floor(x + 0.5f).toInt()
    fun width(text: String, size: Float, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = Typeface.MONOSPACE }.measureText(text) + text.length * spacing
    fun money(symbol: String, value: Float): String = symbol + NumberFormat.getIntegerInstance(Locale.US).format(rounded(value))
}

private class Geometry(val width: Float, ranges: List<RevenueRange>) {
    val columns = ranges.maxOf { it.groups[0].heights.size + 1 + it.groups[1].heights.size }
    val pitch = (width - S.pad) / columns
    val chartHeight = S.rows * pitch + S.chartTop
    val height = S.header + 1f + chartHeight + S.captions + 2f
    private val widths = ranges.map { S.width(it.name, S.tabSize, S.tabSpacing) }
    val tabs: List<Rect>
    init {
        var x = width - 2f - S.pad - widths.sum() - (ranges.size - 1) * S.tabGap
        tabs = widths.map { w -> Rect(x, S.headerTop, x + w, S.headerTop + S.tabLine).also { x += w + S.tabGap } }
    }
}

private class Layout(range: RevenueRange, columns: Int) {
    val heights = MutableList(columns) { 0 }
    val groups = MutableList(columns) { -1 }
    val start = listOf(0, range.groups[0].heights.size + 1)
    init { for (group in 0..1) range.groups[group].heights.forEachIndexed { c, value -> heights[start[group] + c] = value; groups[start[group] + c] = group } }
}

private class Grid(private val ranges: List<RevenueRange>, private val rest: Int, private val geo: Geometry) {
    var range = rest
    var active = 1
    private val laid = Layout(ranges[rest], geo.columns)
    var from = laid.heights.toMutableList()
    var to = from.toMutableList()
    var flowAt = MutableList(geo.columns) { -100f }
    var shadeFrom = laid.groups.map { if (it == 1) 1f else 0f }.toMutableList()
    var shadeTo = shadeFrom.toMutableList()
    var shadeAt = flowAt.toMutableList()
    var liftAt = flowAt.toMutableList()
    var tintFrom = (ranges.indices.map { if (it == rest) 1f else 0f } + listOf(0f, 1f)).toMutableList()
    var tintTo = tintFrom.toMutableList()
    var tintAt = -100f
    var countAt = -100f
    var changeFrom = ranges[rest].change
    var changeTo = changeFrom
    var busyUntil = -100f
    var lineFromX = geo.tabs[rest].left
    var lineFromW = geo.tabs[rest].width
    var lineAt = -100f
    fun copy() = Grid(ranges, rest, geo).also {
        it.range = range; it.active = active; it.from = from.toMutableList(); it.to = to.toMutableList(); it.flowAt = flowAt.toMutableList()
        it.shadeFrom = shadeFrom.toMutableList(); it.shadeTo = shadeTo.toMutableList(); it.shadeAt = shadeAt.toMutableList(); it.liftAt = liftAt.toMutableList()
        it.tintFrom = tintFrom.toMutableList(); it.tintTo = tintTo.toMutableList(); it.tintAt = tintAt
        it.countAt = countAt; it.changeFrom = changeFrom; it.changeTo = changeTo; it.busyUntil = busyUntil
        it.lineFromX = lineFromX; it.lineFromW = lineFromW; it.lineAt = lineAt
    }
    fun shade(c: Int, t: Float, reduced: Boolean) = shadeFrom[c] + (shadeTo[c] - shadeFrom[c]) * S.clamp((t - shadeAt[c]) / if (reduced) S.reducedFade else S.shadeFor)
    fun tint(c: Int, t: Float, reduced: Boolean) = tintFrom[c] + (tintTo[c] - tintFrom[c]) * if (reduced) 1f else S.cssEase.transform(S.clamp((t - tintAt) / S.colorFor))
    fun change(t: Float, reduced: Boolean) = changeFrom + (changeTo - changeFrom) * if (reduced) 1f else S.out((t - countAt) / S.count)
    fun share(t: Float, reduced: Boolean) = if (reduced) 1f else S.rounded(S.out((t - countAt) / S.count) * 200f) / 200f
    fun line(geo: Geometry, t: Float, reduced: Boolean): Pair<Float, Float> {
        val u = if (reduced) 1f else S.out((t - lineAt) / S.underline)
        val tab = geo.tabs[range]
        return (lineFromX + (tab.left - lineFromX) * u) to (lineFromW + (tab.width - lineFromW) * u)
    }
    private fun tintChanged(t: Float, reduced: Boolean, count: Int) {
        tintFrom = tintFrom.indices.map { tint(it, t, reduced) }.toMutableList()
        tintTo = ((0 until count).map { if (it == range) 1f else 0f } + listOf(if (active == 0) 1f else 0f, if (active == 1) 1f else 0f)).toMutableList()
        tintAt = t
    }
    fun highlight(column: Float, t: Float, ranges: List<RevenueRange>, geo: Geometry, reduced: Boolean): Boolean {
        val c = S.rounded(column).coerceIn(0, geo.columns - 1)
        val groups = Layout(ranges[range], geo.columns).groups
        val group = groups[c]
        if (group < 0 || group == active) return false
        var last = t
        for (k in 0 until geo.columns) {
            shadeFrom[k] = shade(k, t, reduced); shadeTo[k] = if (groups[k] == group) 1f else 0f
            shadeAt[k] = t + if (reduced) 0f else kotlin.math.abs(k - c) * S.waveStagger
            if (!reduced) liftAt[k] = shadeAt[k]
            last = max(last, shadeAt[k])
        }
        active = group; tintChanged(t, reduced, ranges.size)
        busyUntil = max(busyUntil, if (reduced) t + S.reducedFade else last + S.liftFor + S.rows * S.rowLag)
        return true
    }
    fun switchRange(index: Int, t: Float, ranges: List<RevenueRange>, geo: Geometry, reduced: Boolean): Boolean {
        if (index == range) return false
        val oldLine = line(geo, t, reduced); val oldChange = change(t, reduced); val laid = Layout(ranges[index], geo.columns)
        from = to.toMutableList(); to = laid.heights.toMutableList()
        var last = t
        for (c in 0 until geo.columns) {
            flowAt[c] = t + if (reduced) 0f else c * S.columnStagger
            shadeFrom[c] = shade(c, t, reduced); shadeTo[c] = if (laid.groups[c] == 1) 1f else 0f; shadeAt[c] = flowAt[c]
            if (!reduced) liftAt[c] = flowAt[c] + S.rippleAfter + c * (S.rippleStagger - S.columnStagger)
            last = max(last, if (reduced) flowAt[c] + S.reducedFade else liftAt[c] + S.liftFor + S.rows * S.rowLag)
        }
        range = index; active = 1; tintChanged(t, reduced, ranges.size)
        changeFrom = oldChange; changeTo = ranges[index].change; countAt = t
        lineFromX = oldLine.first; lineFromW = oldLine.second; lineAt = t; busyUntil = max(busyUntil, last)
        return true
    }
}

/** All dots and numbers read the frame clock inside Canvas, so a frame does not recompose the card. */
@Composable
fun DotMatrixRevenue(
    title: String = "REVENUE",
    ranges: List<RevenueRange> = SampleRanges,
    initialRange: Int = ranges.size - 1,
    symbol: String = "$",
    autoPlay: Boolean = false,
    onRangeChange: ((Int, RevenueRange) -> Unit)? = null,
    onSelectGroup: ((Int, RevenueRange) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val items by rememberUpdatedState(ranges)
    val first by rememberUpdatedState(initialRange.coerceIn(0, ranges.size - 1))
    val still by rememberUpdatedState(reduced)
    val picked by rememberUpdatedState(onRangeChange)
    val highlighted by rememberUpdatedState(onSelectGroup)
    val origin = remember { System.nanoTime() }
    var dragging by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableStateOf(0L) }
    val time = remember { mutableFloatStateOf(0f) }
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(S.page), contentAlignment = Alignment.Center) {
        val geometry = remember(maxWidth, ranges) { Geometry(min(S.maxCard, maxWidth.value - S.margin * 2f), ranges) }
        val geo by rememberUpdatedState(geometry)
        var state by remember { mutableStateOf(Grid(ranges, first, geometry)) }
        fun clock() = (System.nanoTime() - origin) / 1e9f
        fun touched() { lastTouch = System.nanoTime(); touches++ }
        fun highlight(column: Float, hand: Boolean) {
            val next = state.copy()
            val t = clock()
            if (!next.highlight(column, t, items, geo, still)) return
            state = next; time.floatValue = t
            if (hand) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); highlighted?.invoke(next.active, items[next.range]) }
        }
        fun switchRange(index: Int, hand: Boolean) {
            val next = state.copy()
            val t = clock()
            if (!next.switchRange(index, t, items, geo, still)) return
            state = next; time.floatValue = t
            if (hand) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); picked?.invoke(index, items[index]) }
        }
        LaunchedEffect(state.busyUntil) {
            if (state.busyUntil < 0f) return@LaunchedEffect
            do { time.floatValue = max(0f, (withFrameNanos { it } - origin) / 1e9f) } while (time.floatValue < state.busyUntil)
        }
        LaunchedEffect(autoPlay, touches, dragging) {
            if (!autoPlay || dragging) return@LaunchedEffect
            if (touches > 0) until(lastTouch + (S.userHold * 1e9).toLong())
            val atRest = state.range == first && state.active == 1
            if (state.range != first) switchRange(first, false)
            else if (state.active != 1) highlight(Layout(items[first], geo.columns).start[1].toFloat(), false)
            var start = System.nanoTime() + if (atRest) 0L else (S.settle * 1e9).toLong()
            while (true) {
                until(start + (S.highlightAt * 1e9).toLong()); highlight(S.highlightColumn, false)
                until(start + (S.weeklyAt * 1e9).toLong()); switchRange(min(S.weekly, items.size - 1), false)
                until(start + (S.backAt * 1e9).toLong()); switchRange(first, false)
                start += (S.loop * 1e9).toLong(); until(start)
            }
        }
        Box(Modifier.size(geometry.width.dp, geometry.height.dp).background(S.card, RoundedCornerShape(S.radius.dp)).clip(RoundedCornerShape(S.radius.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                surface(0.dp) { _, _, _, _ -> drawCard(state, geometry, time.floatValue, reduced, title, symbol, ranges) }
            }
            Box(Modifier.offset(1.dp, 1.dp).size((geometry.width - 2f).dp, S.header.dp).semantics {
                val change = ranges[state.range].change
                contentDescription = "$title ${if (change >= 0f) "up" else "down"} ${S.rounded(kotlin.math.abs(change))} percent"
            })
            ranges.forEachIndexed { index, range ->
                val tab = geometry.tabs[index]
                Box(Modifier.offset((tab.left - 4f + 1f).dp, (S.headerTop - 12f + 1f).dp)
                    .size((tab.width + 8f).dp, (S.tabLine + 24f).dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { touched(); switchRange(index, true) }
                    .semantics { contentDescription = range.name; selected = state.range == index })
            }
            Box(
                Modifier.offset(1.dp, (S.header + 2f).dp).size((geometry.width - 2f).dp, geometry.chartHeight.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            dragging = true; touched(); highlight((down.position.x / density - S.pad) / geo.pitch - 0.5f, true)
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                touched(); highlight((change.position.x / density - S.pad) / geo.pitch - 0.5f, true)
                                change.consume()
                            } while (change.pressed)
                            dragging = false; touched()
                        }
                    }
                    .semantics {
                        contentDescription = "Revenue chart"; stateDescription = "${ranges[state.range].groups[state.active].label} highlighted"
                        progressBarRangeInfo = ProgressBarRangeInfo(state.active.toFloat(), 0f..1f)
                        setProgress { value -> touched(); highlight(Layout(items[state.range], geo.columns).start[if (value >= 0.5f) 1 else 0].toFloat(), true); true }
                    },
            )
            Box(Modifier.fillMaxSize().border(1.dp, S.line, RoundedCornerShape(S.radius.dp)))
        }
    }
}

private suspend fun until(target: Long) {
    while (true) { val left = target - System.nanoTime(); if (left <= 0L) return; delay((left + 999_999L) / 1_000_000L) }
}

private fun DrawScope.drawCard(g: Grid, geo: Geometry, t: Float, reduced: Boolean, title: String, symbol: String, ranges: List<RevenueRange>) {
    drawIntoCanvas { surface ->
        val canvas = surface.nativeCanvas
        val save = canvas.save(); canvas.translate(1f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun tint(k: Int): Color { val v = (160f + (17f - 160f) * g.tint(k, t, reduced)) / 255f; return Color(v, v, v) }
        fun text(value: String, x: Float, top: Float, size: Float, height: Float, color: Color, mono: Boolean, light: Boolean = false, spacing: Float = 0f) {
            paint.textSize = size
            paint.typeface = if (mono) Typeface.MONOSPACE else if (!light) Typeface.DEFAULT else if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 300, false) else Typeface.create("sans-serif-light", Typeface.NORMAL)
            paint.fontFeatureSettings = "tnum"
            paint.style = Paint.Style.FILL; paint.color = color.toArgb()
            val baseline = top + (height - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2f
            if (spacing == 0f) canvas.drawText(value, x, baseline, paint)
            else { var at = x; value.forEach { char -> val glyph = char.toString(); canvas.drawText(glyph, at, baseline, paint); at += paint.measureText(glyph) + spacing } }
        }
        text(title, S.pad, S.headerTop, S.titleSize, S.titleLine, S.ink, true, spacing = S.titleSpacing)
        val change = S.rounded(g.change(t, reduced))
        val value = (if (change >= 0) "+" else "−") + kotlin.math.abs(change)
        val headline = S.headerTop + S.titleLine + S.headlineTop
        text(value, S.pad, headline, S.changeSize, S.changeLine, S.ink, false, light = true, spacing = S.changeSpacing)
        val baseline = headline + (S.changeLine - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2f
        val width = paint.measureText(value) + value.length * S.changeSpacing
        paint.textSize = S.percentSize
        text("%", S.pad + width + S.percentGap, baseline + paint.fontMetrics.ascent, S.percentSize, paint.fontMetrics.descent - paint.fontMetrics.ascent, S.ink, false, light = true)
        ranges.indices.forEach { text(ranges[it].name, geo.tabs[it].left, S.headerTop, S.tabSize, S.tabLine, tint(it), true, spacing = S.tabSpacing) }
        val line = g.line(geo, t, reduced)
        paint.color = S.ink.toArgb(); canvas.drawRect(line.first, S.headerTop + S.underlineTop, line.first + line.second, S.headerTop + S.underlineTop + 1f, paint)
        paint.color = S.line.toArgb(); canvas.drawRect(0f, S.header, geo.width - 2f, S.header + 1f, paint)
        val radius = geo.pitch * S.dot / 2f
        val floorY = geo.chartHeight - geo.pitch / 2f
        val chartSave = canvas.save()
        canvas.clipRect(0f, S.header + 1f, geo.width - 2f, S.header + 1f + geo.chartHeight)
        for (column in 0 until geo.columns) {
            val shade = S.rounded(g.shade(column, t, reduced) * S.shades)
            val byte = S.rounded(S.light + (S.dark - S.light) * shade / S.shades)
            val color = rgba(byte, byte, byte, 1f)
            val edge = if (column >= geo.columns - S.edgeColumns) 1f - (column - (geo.columns - S.edgeColumns) + 1).toFloat() / S.edgeColumns * (1f - S.edgeAlpha) else 1f
            val since = t - g.flowAt[column]
            for (row in 0 until max(g.from[column], g.to[column])) {
                var alpha = 1f; var dy = 0f
                if (row >= g.to[column]) {
                    val u = S.clamp((since - (g.from[column] - 1 - row) * if (reduced) 0f else S.dotStagger) / if (reduced) S.reducedFade else S.leaveFor)
                    alpha = 1f - u
                    if (!reduced) dy = -S.leaveRise * (1f - (1f - u) * (1f - u))
                } else if (row >= g.from[column]) {
                    if (reduced) alpha = S.clamp(since / S.reducedFade)
                    else { val u = S.clamp((since - (row - g.from[column]) * S.dotStagger) / S.dropFor); alpha = S.clamp(u * 3f); dy = -S.drop * (1f - S.backOut(u)) }
                }
                if (alpha <= 0.01f) continue
                val lifted = (t - g.liftAt[column] - row * S.rowLag) / S.liftFor
                if (lifted > 0f && lifted < 1f) dy -= S.lift * sin(PI.toFloat() * lifted)
                paint.color = color.toArgb(); paint.alpha = (alpha * edge * 255f).toInt().coerceIn(0, 255)
                canvas.drawCircle(S.pad + column * geo.pitch + geo.pitch / 2f, S.header + 1f + floorY - row * geo.pitch + dy, radius, paint)
            }
        }
        canvas.restoreToCount(chartSave)
        val laid = Layout(ranges[g.range], geo.columns)
        val share = g.share(t, reduced)
        for (i in 0..1) { val group = ranges[g.range].groups[i]; text(group.label + " " + S.money(symbol, group.total * share), S.pad + laid.start[i] * geo.pitch + geo.pitch / 2f - radius, S.header + 1f + geo.chartHeight + S.captionTop, S.captionSize, S.captionLine, tint(ranges.size + i), true) }
        canvas.restoreToCount(save)
    }
}
