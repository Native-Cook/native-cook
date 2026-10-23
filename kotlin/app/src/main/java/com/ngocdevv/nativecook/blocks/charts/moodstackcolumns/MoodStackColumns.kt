package com.ngocdevv.nativecook.blocks.charts.moodstackcolumns

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** @param adjective Used in the caption under a selected column. */
data class Mood(val name: String, val adjective: String, val color: Color)

/** @param shares Percent per mood; a column is full height whatever the sum. */
data class MoodColumn(val name: String, val checkIns: Int, val shares: List<Double>)

val MOODS = listOf(
    Mood("Joy", "joyful", Color(0xFFFFD04E)),
    Mood("Calm", "calm", Color(0xFF4BE29B)),
    Mood("Low", "low", Color(0xFF7BA0FF)),
    Mood("Stress", "stressed", Color(0xFFFF5A4A)),
)
val COLUMNS = listOf(
    MoodColumn("Morning", 5, listOf(80.0, 0.0, 0.0, 20.0)),
    MoodColumn("Afternoon", 7, listOf(28.0, 14.0, 14.0, 42.0)),
    MoodColumn("Evening", 6, listOf(0.0, 50.0, 33.0, 16.0)),
    MoodColumn("Late Night", 1, listOf(0.0, 100.0, 0.0, 0.0)),
)
const val LOOP = 9.0
private object Motion {
    val selectAt = listOf(0.8 to 1, 2.2 to 3)
    const val releaseAt = 3.6; const val sortAt = 4.2; const val unsortAt = 6.0; const val userHold = 3.0; const val settle = 1.0
}
private object S {
    val background = Color(0xFF000000); val ink = Color(0xFFFFFFFF); val soft = Color(0xFFC7C7CC); val mute = Color(0xFF8E8E93); val segmentInk = Color(0xFF111111); val empty = Color(0xFF1C1C1E); val toggle = Color(0xFF1C1C1E); val thumb = Color(0xFF3A3A3C)
    const val margin = 20f; const val gap = 6f; const val columnRadius = 24f; const val widen = 1.36; const val dim = 0.5; const val labelGrow = 1.25; const val pulse = 1.02; const val minChart = 260f; const val maxChart = 420f; const val toggleHeight = 30f; const val toggleWidth = 70f
    const val focusDamping = 18.0; const val focusStiffness = 190.0; const val focusMass = 1.0; const val springEnergy = 6e-9; const val pulseFor = 0.15; const val sortFor = 0.7; const val sortStagger = 0.04; const val toggleFor = 0.22; const val captionFor = 0.2
    const val headingSize = 34f; const val headingHeight = 42f; const val headingTracking = -0.6f; const val headingInset = 12f; const val chevronSize = 26f; const val chevronGap = 6f; const val chevronDrop = 4f; const val datesGap = 6f; const val datesSize = 12f; const val datesHeight = 16f; const val datesTracking = 0.6f; const val toggleSize = 12f; const val toggleLine = 16f; const val toggleInset = 2f; const val ruleGap = 8f; const val ruleHeight = 1f; const val ruleMark = 0.22f; const val titleGap = 26f; const val titleSize = 26f; const val titleHeight = 32f; const val titleTracking = -0.4f; const val chartAllowance = 84f; const val nameGap = 10f; const val nameSize = 15f; const val nameHeight = 20f; const val checkSize = 13f; const val checkHeight = 18f; const val captionGap = 4f; const val captionSlot = 22f; const val captionSize = 13f; const val captionHeight = 18f; const val segmentSize = 16f; const val segmentHeight = 20f; const val minLabelHeight = 24f; const val teaserHeight = 64f; const val teaserOpacity = 0.55f; const val teaserStart = 0.1f; const val teaserEnd = 0.9f
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun cubic(x: Double): Double { val t = clamp(x); return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2 }
    fun quad(x: Double): Double { val t = clamp(x); return if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2 }
    fun outQuad(x: Double) = 1 - (1 - clamp(x)).pow(2)
    fun outCubic(x: Double) = 1 - (1 - clamp(x)).pow(3)
}
private data class Piece(val mood: Int, val share: Double, val label: Double, val byMood: Double = 0.0, val bySize: Double = 0.0)
private fun stack(column: MoodColumn): List<Piece> {
    val total = column.shares.sum().let { if (it == 0.0) 1.0 else it }
    var pieces = column.shares.mapIndexed { index, label -> Piece(index, label / total, label) }.filter { it.share > 0 }
    var top = 0.0
    pieces = pieces.map { p -> p.copy(byMood = top).also { top += p.share } }
    top = 0.0
    val tops = pieces.sortedWith(compareBy<Piece> { it.share }.thenBy { it.mood }).associate { p -> (p.mood to top).also { top += p.share } }
    return pieces.map { it.copy(bySize = tops.getValue(it.mood)) }.sortedWith(compareByDescending<Piece> { it.share }.thenBy { it.mood })
}

/** Springs carry their velocity through interruptions; only the Canvas reads frame samples. */
private data class Scalar(val from: Double, val to: Double = from, val at: Double = -100.0, val duration: Double = 0.0, val curve: Int = 0, val velocity: Double? = null) {
    fun sample(now: Double): Pair<Double, Double> {
        val t = max(0.0, now - at)
        if (velocity != null) {
            val z = S.focusDamping / (2 * S.focusMass); val w = sqrt(S.focusStiffness / S.focusMass - z * z); val a = from - to; val b = (velocity + z * a) / w
            val delta = exp(-z * t) * (a * cos(w * t) + b * sin(w * t))
            val v = exp(-z * t) * (velocity * cos(w * t) + (-z * b - w * a) * sin(w * t))
            val initial = S.focusMass * velocity * velocity + S.focusStiffness * a * a
            return if (initial == 0.0 || (S.focusMass * v * v + S.focusStiffness * delta * delta) / initial < S.springEnergy) to to 0.0 else (to + delta) to v
        }
        val u = if (duration == 0.0) 1.0 else S.clamp(t / duration)
        val p = when (curve) { 1 -> S.outQuad(u); 2 -> S.quad(u); 3 -> S.outCubic(u); else -> u }
        return (from + (to - from) * p) to 0.0
    }
    fun end(): Double {
        if (velocity == null) return at + duration
        var lo = 0.0; var hi = 1.0
        while (sample(at + hi).second != 0.0) hi *= 2
        repeat(48) { val mid = (lo + hi) / 2; if (sample(at + mid).second == 0.0) hi = mid else lo = mid }
        return at + hi
    }
    fun target(value: Double, now: Double, duration: Double, curve: Int = 0) = Scalar(sample(now).first, value, now, duration, curve)
    fun spring(value: Double, now: Double, reduced: Boolean): Scalar {
        if (reduced) return Scalar(value)
        if (to == value) return this
        val sample = sample(now)
        return Scalar(sample.first, value, now, velocity = sample.second)
    }
}
private data class Pulse(val at: Double = -100.0, val from: Double = 1.0) {
    fun value(now: Double): Double {
        val t = now - at
        if (t < 0 || t >= S.pulseFor * 2) return 1.0
        if (t < S.pulseFor) return from + (S.pulse - from) * S.outQuad(t / S.pulseFor)
        return S.pulse + (1 - S.pulse) * S.quad((t - S.pulseFor) / S.pulseFor)
    }
    fun start(now: Double) = Pulse(now, value(now))
    val end get() = at + S.pulseFor * 2
}
private data class Geometry(val width: Float, val height: Float, val top: Float, val bottom: Float, val count: Int) {
    val headingY get() = top + S.headingInset
    val datesY get() = headingY + S.headingHeight + S.datesGap
    val ruleY get() = datesY + S.toggleHeight + S.ruleGap
    val titleY get() = ruleY + S.ruleHeight + S.titleGap
    val headerHeight get() = titleY + S.titleHeight
    val teaserY get() = height - S.teaserHeight - bottom
    val chartSpace get() = teaserY - headerHeight
    val barHeight get() = max(S.minChart, min(S.maxChart, chartSpace - S.chartAllowance))
    val rowHeight get() = barHeight + S.nameGap + S.nameHeight + S.checkHeight + S.captionGap + S.captionSlot
    val rowY get() = headerHeight + (chartSpace - rowHeight) / 2
    val row get() = width - S.margin * 2 - S.gap * (count - 1)
    val base get() = row.toDouble() / count
    val wide get() = if (count > 1) base * S.widen else base
    val narrow get() = if (count > 1) (row - wide) / (count - 1) else base
    fun columnAt(x: Float, selected: Int): Int {
        var left = S.margin.toDouble()
        for (i in 0 until count) { val w = if (selected < 0) base else if (i == selected) wide else narrow; if (x < left + w + S.gap / 2) return i; left += w + S.gap }
        return count - 1
    }
}
private suspend fun until(date: Long) { while (System.nanoTime() < date) { val left = date - System.nanoTime(); delay(max(1L, (left + 999_999L) / 1_000_000L)) } }
private fun number(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
private fun caption(top: Piece?, moods: List<Mood>) = if (top == null) "" else "${number(top.label)}% ${moods[top.mood].adjective}"

/** A full-screen mood journal: tap again to release a column, drag to scrub, or re-sort its stack. */
@Composable
fun MoodStackColumns(
    heading: String = "All time",
    dateRange: String = "JUN 28 – OCT 12",
    title: String = "Your mood during the day",
    teaser: String = "Your emotions during the week",
    moods: List<Mood> = MOODS,
    columns: List<MoodColumn> = COLUMNS,
    byMoodLabel: String = "By mood",
    bySizeLabel: String = "By size",
    autoPlay: Boolean = false,
    onSelect: ((Int) -> Unit)? = null,
    onSortChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion(); val still by rememberUpdatedState(reduced)
    val count by rememberUpdatedState(columns.size)
    val selectedCallback by rememberUpdatedState(onSelect); val sortCallback by rememberUpdatedState(onSortChange)
    val birth = remember { System.nanoTime() }
    fun clock() = (System.nanoTime() - birth) / 1e9
    val time = remember { mutableDoubleStateOf(0.0) }
    var selected by remember { mutableIntStateOf(-1) }; var bySize by remember { mutableStateOf(false) }
    var any by remember { mutableStateOf(Scalar(0.0)) }
    var focuses by remember { mutableStateOf(List(7) { Scalar(0.0) }) }; var pulses by remember { mutableStateOf(List(7) { Pulse() }) }; var captions by remember { mutableStateOf(List(7) { Scalar(0.0) }) }
    var sort by remember { mutableStateOf(Scalar(0.0)) }; var toggle by remember { mutableStateOf(Scalar(0.0)) }
    var touches by remember { mutableIntStateOf(0) }; var lastTouch by remember { mutableDoubleStateOf(-100.0) }
    var animation by remember { mutableIntStateOf(0) }; var deadline by remember { mutableDoubleStateOf(-100.0) }
    val view = LocalView.current; val hapticView by rememberUpdatedState(view)
    fun touched() { lastTouch = clock(); touches++ }
    fun tick() { hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun activate(now: Double) { deadline = (listOf(any.end(), sort.end(), toggle.end()) + focuses.map { it.end() } + captions.map { it.end() } + pulses.map { it.end }).max(); time.doubleValue = now; animation++ }
    fun updateFocus(now: Double) {
        any = any.spring(if (selected >= 0) 1.0 else 0.0, now, still)
        focuses = focuses.mapIndexed { i, f -> f.spring(if (i == selected) 1.0 else 0.0, now, still) }
        captions = captions.mapIndexed { i, c -> c.target(if (i == selected) 1.0 else 0.0, now, S.captionFor, 2) }
        if (still) pulses = List(7) { Pulse() } else if (selected >= 0) pulses = pulses.mapIndexed { i, p -> if (i == selected) p.start(now) else p }
    }
    fun setSelection(index: Int, now: Double) { if (selected == index) return; selected = index; updateFocus(now); activate(now) }
    fun select(index: Int, hand: Boolean, now: Double) { setSelection(if (index == selected) -1 else index, now); if (hand) tick(); selectedCallback?.invoke(selected) }
    fun choose(index: Int) { if (index != selected) { touched(); setSelection(index, clock()); tick(); selectedCallback?.invoke(index) } }
    fun updateSort(now: Double) { val target = if (bySize) 1.0 else 0.0; sort = sort.target(target, now, if (still) 0.0 else S.sortFor + (count - 1) * S.sortStagger); toggle = toggle.target(target, now, if (still) 0.0 else S.toggleFor, 3) }
    fun resort(next: Boolean, hand: Boolean, now: Double) { if (next != bySize) { bySize = next; updateSort(now); activate(now) }; if (hand) tick(); sortCallback?.invoke(next) }
    LaunchedEffect(animation) { if (clock() < deadline) { do { time.doubleValue = max(0.0, (withFrameNanos { it } - birth) / 1e9) } while (clock() < deadline) }; time.doubleValue = clock() }
    LaunchedEffect(reduced) { if (animation > 0) { updateFocus(clock()); updateSort(clock()); activate(clock()) } }
    LaunchedEffect(columns.size) { if (animation > 0) { updateSort(clock()); activate(clock()) } }
    LaunchedEffect(autoPlay, touches, columns.size) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) { val reset = lastTouch + Motion.userHold; until(birth + (reset * 1e9).toLong()); setSelection(-1, reset); if (bySize) { bySize = false; updateSort(reset); activate(reset) }; until(birth + ((reset + Motion.settle) * 1e9).toLong()) }
        var origin = System.nanoTime()
        while (true) {
            for ((at, index) in Motion.selectAt) { val date = origin + (at * 1e9).toLong(); until(date); select(index % count, false, (date - birth) / 1e9) }
            val release = origin + (Motion.releaseAt * 1e9).toLong(); until(release); select(Motion.selectAt.last().second % count, false, (release - birth) / 1e9)
            val size = origin + (Motion.sortAt * 1e9).toLong(); until(size); resort(true, false, (size - birth) / 1e9)
            val mood = origin + (Motion.unsortAt * 1e9).toLong(); until(mood); resort(false, false, (mood - birth) / 1e9)
            origin += (LOOP * 1e9).toLong(); until(origin)
        }
    }
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window; val controller = window?.let { WindowCompat.getInsetsController(it, view) }; val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    val density = LocalDensity.current; val insets = WindowInsets.safeDrawing
    BoxWithConstraints(modifier.fillMaxSize().background(S.background)) {
        val geo = Geometry(maxWidth.value, maxHeight.value, insets.getTop(density) / density.density, insets.getBottom(density) / density.density, columns.size)
        val pieces = remember(columns) { columns.map(::stack) }
        val gestureGeometry by rememberUpdatedState(geo)
        val gestureDensity by rememberUpdatedState(density.density)
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "$heading, $dateRange, $title" }) {
            surface(0.dp) { _, _, _, _ -> drawScene(geo, columns, pieces, moods, heading, dateRange, title, teaser, byMoodLabel, bySizeLabel, bySize, reduced, any, focuses, pulses, captions, sort, toggle, time.doubleValue) }
        }
        val toggleX = geo.width - S.margin - S.toggleWidth * 2 - S.toggleInset * 2
        for (i in 0..1) {
            Box(Modifier.offset((toggleX + S.toggleInset + i * S.toggleWidth).dp, (geo.datesY + S.toggleInset).dp).size(S.toggleWidth.dp, (S.toggleHeight - S.toggleInset * 2).dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { touched(); if (bySize != (i == 1)) resort(i == 1, true, clock()) }
                .semantics { contentDescription = if (i == 0) byMoodLabel else bySizeLabel; this.selected = bySize == (i == 1) })
        }
        Box(Modifier.offset(y = geo.rowY.dp).size(geo.width.dp, geo.rowHeight.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = gestureGeometry.columnAt(down.position.x / gestureDensity, selected)
                val pressedSelected = start == selected
                var moved = false
                choose(start); touched(); down.consume()
                try {
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pressed = change.pressed
                        if (pressed) { val index = gestureGeometry.columnAt(change.position.x / gestureDensity, selected); if (index != start) moved = true; choose(index) }
                        change.consume()
                    }
                } finally {
                    if (pressedSelected && !moved) { touched(); setSelection(-1, clock()); tick(); selectedCallback?.invoke(-1) } else touched()
                }
            }
        })
        var left = S.margin.toDouble()
        for (i in columns.indices) {
            val w = if (selected < 0) geo.base else if (selected == i) geo.wide else geo.narrow
            Box(Modifier.offset(left.toFloat().dp, geo.rowY.dp).size(w.toFloat().dp, geo.rowHeight.dp).semantics {
                contentDescription = "${columns[i].name}, ${columns[i].checkIns} check-ins, ${caption(pieces[i].firstOrNull(), moods)}"
                onClick { touched(); select(i, true, clock()); true }
            })
            left += w + S.gap
        }
    }
}

private fun font(size: Float, weight: Int, serif: Boolean, tracking: Float = 0f, tabular: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    val base = Typeface.create(if (serif) "serif" else "sans-serif", Typeface.NORMAL)
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false) else Typeface.create(if (serif) "serif" else if (weight >= 500) "sans-serif-medium" else "sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    letterSpacing = if (size == 0f) 0f else tracking / size
    if (tabular) fontFeatureSettings = "tnum"
}
private fun fitted(value: String, size: Float, width: Float, weight: Int, serif: Boolean = false, tracking: Float = 0f): Float {
    val measured = font(size, weight, serif, tracking).measureText(value)
    return if (measured <= width || measured == 0f) size else size * width / measured
}
private fun ellipsis(value: String, size: Float, width: Float, weight: Int, serif: Boolean = false, tracking: Float = 0f, tabular: Boolean = false): String {
    val paint = font(size, weight, serif, tracking, tabular)
    if (paint.measureText(value) <= width) return value
    var text = value
    while (text.isNotEmpty() && paint.measureText("$text…") > width) text = text.dropLast(1)
    return "$text…"
}
private fun DrawScope.text(value: String, x: Float, y: Float, size: Float, height: Float, color: Color, weight: Int = 400, centered: Boolean = false, serif: Boolean = false, tracking: Float = 0f, tabular: Boolean = false) {
    val paint = font(size, weight, serif, tracking, tabular); paint.color = color.toArgb(); paint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
    val fm = paint.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(value, x, y + (height - fm.ascent - fm.descent) / 2, paint)
}
private fun DrawScope.drawScene(geo: Geometry, columns: List<MoodColumn>, pieces: List<List<Piece>>, moods: List<Mood>, heading: String, dates: String, title: String, teaser: String, byMoodLabel: String, bySizeLabel: String, bySize: Boolean, reduced: Boolean, any: Scalar, focuses: List<Scalar>, pulses: List<Pulse>, captions: List<Scalar>, sort: Scalar, toggle: Scalar, now: Double) {
    val head = ellipsis(heading, S.headingSize, geo.width - S.margin * 2 - S.chevronGap - S.chevronSize, 600, true, S.headingTracking)
    text(head, S.margin, geo.headingY, S.headingSize, S.headingHeight, S.ink, 600, serif = true, tracking = S.headingTracking)
    val iconX = S.margin + font(S.headingSize, 600, true, S.headingTracking).measureText(head) + S.chevronGap + S.chevronSize / 2
    val iconY = geo.headingY + S.headingHeight / 2 + S.chevronDrop / 2
    val chevron = Path().apply { moveTo(iconX - 6.5f, iconY - 3.25f); lineTo(iconX, iconY + 3.25f); lineTo(iconX + 6.5f, iconY - 3.25f) }
    drawPath(chevron, S.ink, style = Stroke(2f, cap = StrokeCap.Butt))
    text(dates, S.margin, geo.datesY + (S.toggleHeight - S.datesHeight) / 2, S.datesSize, S.datesHeight, S.soft, 500, tracking = S.datesTracking)
    val toggleX = geo.width - S.margin - S.toggleWidth * 2 - S.toggleInset * 2
    drawRoundRect(S.toggle, Offset(toggleX, geo.datesY), Size(S.toggleWidth * 2 + S.toggleInset * 2, S.toggleHeight), CornerRadius(S.toggleHeight / 2))
    drawRoundRect(S.thumb, Offset(toggleX + S.toggleInset + toggle.sample(now).first.toFloat() * S.toggleWidth, geo.datesY + S.toggleInset), Size(S.toggleWidth, S.toggleHeight - S.toggleInset * 2), CornerRadius((S.toggleHeight - S.toggleInset * 2) / 2))
    for ((i, label) in listOf(byMoodLabel, bySizeLabel).withIndex()) { text(label, toggleX + S.toggleInset + S.toggleWidth * (i + 0.5f), geo.datesY + (S.toggleHeight - S.toggleLine) / 2, S.toggleSize, S.toggleLine, if (bySize == (i == 1)) S.ink else S.mute, 500, true) }
    drawRect(S.thumb, Offset(S.margin, geo.ruleY), Size(geo.width - S.margin * 2, S.ruleHeight))
    drawRect(S.ink, Offset(S.margin, geo.ruleY), Size((geo.width - S.margin * 2) * S.ruleMark, S.ruleHeight))
    text(title, S.margin, geo.titleY, fitted(title, S.titleSize, geo.width - S.margin * 2, 600, true, S.titleTracking), S.titleHeight, S.ink, 600, serif = true, tracking = S.titleTracking)
    var left = S.margin
    val a = any.sample(now).first; val s = sort.sample(now).first
    for (i in pieces.indices) {
        val f = focuses[i].sample(now).first
        val width = (geo.base + (geo.wide - geo.base) * f + (geo.narrow - geo.base) * (a - f)).toFloat()
        val alpha = 1 - S.dim * S.clamp(a - f)
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.saveLayer(null, Paint().apply { this.alpha = (alpha * 255).roundToInt() })
        val top = pieces[i].firstOrNull()
        scale(pulses[i].value(now).toFloat(), pivot = Offset(left + width / 2, geo.rowY + geo.barHeight / 2)) {
            val barPath = Path().apply { addRoundRect(RoundRect(Rect(left, geo.rowY, left + width, geo.rowY + geo.barHeight), CornerRadius(S.columnRadius))) }
            clipPath(barPath) {
                drawRect(top?.let { moods[it.mood].color } ?: S.empty, Offset(left, geo.rowY), Size(width, geo.barHeight))
                // Preserve the RN progress formula in both directions, including its timing per column.
                val stagger = i * S.sortStagger
                val p = if (reduced) floor(s + 0.5) else S.cubic((s * (S.sortFor + stagger) - stagger) / S.sortFor)
                for (piece in pieces[i]) {
                    val y = geo.rowY + ((piece.byMood + (piece.bySize - piece.byMood) * p) * geo.barHeight).toFloat(); val height = (piece.share * geo.barHeight).toFloat()
                    drawRect(moods[piece.mood].color, Offset(left, y), Size(width, height))
                    if (height >= S.minLabelHeight) {
                        val label = ellipsis("${number(piece.label)}%", S.segmentSize, width, 700, tabular = true)
                        val center = Offset(left + width / 2, y + height / 2)
                        scale((1 + (S.labelGrow - 1) * f).toFloat(), pivot = center) { text(label, center.x, center.y - S.segmentHeight / 2, S.segmentSize, S.segmentHeight, S.segmentInk, 700, true, tabular = true) }
                    }
                }
            }
        }
        val nameY = geo.rowY + geo.barHeight + S.nameGap; val center = left + width / 2
        text(columns[i].name, center, nameY, fitted(columns[i].name, S.nameSize, width, 500), S.nameHeight, S.ink, 500, true)
        val checks = "${columns[i].checkIns} ${if (columns[i].checkIns == 1) "check-in" else "check-ins"}"
        text(checks, center, nameY + S.nameHeight, fitted(checks, S.checkSize, width, 400), S.checkHeight, S.mute, centered = true)
        val captionAlpha = captions[i].sample(now).first.toFloat()
        if (top != null && captionAlpha > 0) {
            text(ellipsis(caption(top, moods), S.captionSize, width, 600), center, nameY + S.nameHeight + S.checkHeight + S.captionGap, S.captionSize, S.captionHeight, moods[top.mood].color.copy(alpha = captionAlpha), 600, true)
        }
        canvas.restoreToCount(save)
        left += width + S.gap
    }
    clipRect(0f, geo.teaserY, geo.width, geo.height) {
        text(teaser, S.margin, geo.teaserY, fitted(teaser, S.titleSize, geo.width - S.margin * 2, 600, true, S.titleTracking), S.titleHeight, S.ink.copy(alpha = S.teaserOpacity), 600, serif = true, tracking = S.titleTracking)
        drawRect(Brush.verticalGradient(S.teaserStart to S.background.copy(alpha = 0f), S.teaserEnd to S.background, startY = geo.teaserY, endY = geo.height), Offset(0f, geo.teaserY), Size(geo.width, S.teaserHeight + geo.bottom))
    }
}
