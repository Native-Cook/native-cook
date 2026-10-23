package com.ngocdevv.nativecook.blocks.charts.duallineredrawranges

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

const val LOOP = 9.0

private object S {
    val background = Color(0xff101010); val ink = Color(0xfff2f2f2); val mute = Color(0xff6b6b70); val grid = Color(0xff2a2a2e); val track = Color(0xff232326); val thumb = Color(0xfff2f2f2); val thumbInk = Color(0xff111111); val tile = Color(0xff18181b)
    const val margin = 20f; const val axis = 40f; const val rightPad = 10f; const val ticksHeight = 28f; const val gridSteps = 5; const val lineWidth = 2f; const val head = 3.5f; const val headGlow = 6f; const val glowBlur = 5f; const val segmented = 34f; const val labelSize = 12f; const val labelRise = 10f; const val labelDrop = 20f
    const val wipe = 0.35; const val draw = 0.9; const val stagger = 0.12; const val labelIn = 0.2; const val total = wipe + stagger + draw + labelIn; const val fade = 0.25; const val thumbSlide = 0.2; const val swipe = 48f
    const val loop = LOOP; val switches = listOf(0.9, 3.9, 6.9); const val userHold = 3.0
    const val captionIn = 0.2; const val ticksIn = 0.22; const val ticksOutStill = 0.12; const val valueIn = 0.24; const val valueOut = 0.18; const val valueTravel = 25f
    const val titleSize = 22; const val titleLine = 28; const val titleSpacing = -0.3; const val captionSize = 14; const val captionLine = 19; const val captionTop = 4; const val infoSize = 14; const val infoGap = 6; const val infoMark = 9
    const val segmentedTop = 24; const val thumbInset = 3f; const val segmentSize = 13; const val legendTop = 20; const val chipGap = 8; const val chipHeight = 28; const val chipPad = 12; const val sampleWidth = 14; const val sampleHeight = 2; const val chipSize = 12
    const val chartTop = 24; const val chartBottom = 8; const val axisSize = 10f; const val axisLine = 14f; const val tickSize = 10f; const val tickLine = 13f; const val tickTop = 10f; const val tickWidth = 60f
    const val statsTop = 12; const val tileGap = 12; const val tilePad = 16; const val tileRadius = 20; const val tileLabel = 12; const val tileLabelLine = 16; const val valueTop = 6; const val valueSize = 26f; const val valueLine = 32f
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smooth(t: Double, from: Double, to: Double): Double { val u = clamp((t - from) / (to - from)); return u * u * (3 - 2 * u) }
    fun quadratic(x: Double): Double { val u = clamp(x); return if (u < 0.5) 2 * u * u else 1 - (-2 * u + 2).pow(2) / 2 }
    fun out(x: Double) = 1 - (1 - clamp(x)).pow(3)
    fun average(values: List<Double>) = "${floor(values.sum() / values.size + 0.5).toInt()} %"
    fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))
}

private data class Window(val new: Boolean, val from: Double, val to: Double, val opacity: Double)
private fun lineWindow(line: Int, phase: Double, end: Double, still: Boolean): Window {
    if (still) return if (phase < S.fade) Window(false, 0.0, end, 1 - phase / S.fade) else Window(true, 0.0, 1.0, S.clamp((phase - S.fade) / S.fade))
    if (phase < S.wipe) return Window(false, end * (phase / S.wipe).pow(2), end, 1.0)
    return Window(true, 0.0, S.smooth(phase, S.wipe + line * S.stagger, S.wipe + line * S.stagger + S.draw), 1.0)
}
private data class Ghost(val index: Int, val at: Double)
private data class Scene(val from: Int, val to: Int, val ghosts: List<Ghost> = emptyList(), val ends: List<Double> = listOf(1.0, 1.0), val at: Double = -100.0, val thumbFrom: Double = to.toDouble(), val textAt: Double = 0.0) {
    fun phase(now: Double) = (now - at).coerceIn(0.0, S.total)
    fun thumb(now: Double, still: Boolean) = thumbFrom + (to - thumbFrom) * if (still) 1.0 else S.out((now - at) / S.thumbSlide)
}

@Composable
fun DualLineRedrawRanges(
    title: String = "Average % move of high / low",
    ranges: List<LineRange> = Ranges,
    initialRange: Int = 1,
    highLabel: String = "High",
    lowLabel: String = "Low",
    highColor: String = "#e9c46a",
    lowColor: String = "#8b7cf6",
    autoPlay: Boolean = false,
    onRangeChange: ((LineRange) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val still by rememberUpdatedState(reduced)
    val items by rememberUpdatedState(ranges)
    val first by rememberUpdatedState(initialRange)
    val callback by rememberUpdatedState(onRangeChange)
    val origin = remember { System.nanoTime() }
    val time = remember { mutableDoubleStateOf(0.0) }
    var scene by remember { mutableStateOf(Scene(initialRange, initialRange)) }
    var touches by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current
    val labels = listOf(highLabel, lowLabel)
    val colors = remember(highColor, lowColor) { listOf(S.color(highColor), S.color(lowColor)) }
    fun clock() = (System.nanoTime() - origin) / 1e9
    fun touch() { lastTouch = System.nanoTime(); touches++ }
    fun select(next: Int, hand: Boolean) {
        if (next !in items.indices || next == scene.to) return
        val now = clock(); val windows = (0..1).map { lineWindow(it, scene.phase(now), scene.ends[it], still) }
        scene = Scene(if (windows[0].new) scene.to else scene.from, next, scene.ghosts.filter { now - it.at < S.wipe } + Ghost(scene.to, now), windows.map { it.to }, now, scene.thumb(now, still), now)
        time.doubleValue = now
        if (hand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        callback?.invoke(items[next])
    }
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    LaunchedEffect(scene.at, reduced) {
        if (scene.at < 0 && reduced) return@LaunchedEffect
        val end = if (scene.at < 0) S.wipe + S.valueIn else scene.at + if (reduced) 2 * S.fade else S.total
        do { time.doubleValue = max(0.0, (withFrameNanos { it } - origin) / 1e9) } while (time.doubleValue < end)
    }
    LaunchedEffect(autoPlay, touches, dragging) {
        if (!autoPlay || dragging) return@LaunchedEffect
        if (touches > 0) until(lastTouch + (S.userHold * 1e9).toLong())
        val reset = scene.to != first
        if (reset) select(first, false)
        var start = System.nanoTime() + if (reset) (S.total * 1e9).toLong() else 0L
        val order = (1 until items.size).map { (first + it) % items.size } + first
        while (true) {
            for (k in 0 until min(S.switches.size, order.size)) { until(start + (S.switches[k] * 1e9).toLong()); select(order[k], false) }
            start += (S.loop * 1e9).toLong(); until(start)
        }
    }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(modifier.fillMaxSize().background(S.background).padding(insets).padding(horizontal = S.margin.dp)
        .padding(top = 16.dp, bottom = max(0f, S.margin - insets.calculateBottomPadding().value).dp)) {
        Label(title, S.titleSize, S.titleLine, S.ink, FontWeight.SemiBold, Modifier.fillMaxWidth(), S.titleSpacing)
        Row(Modifier.padding(top = S.captionTop.dp).height(S.captionLine.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(ranges[scene.to].caption, S.captionSize, S.captionLine, S.mute, modifier = Modifier.graphicsLayer { alpha = if (reduced) 1f else S.quadratic((time.doubleValue - scene.textAt) / S.captionIn).toFloat() })
            Spacer(Modifier.width(S.infoGap.dp))
            Box(Modifier.size(S.infoSize.dp).border(1.dp, S.mute, CircleShape), contentAlignment = Alignment.Center) { Label("i", S.infoMark, 11, S.mute, FontWeight.Bold) }
        }
        BoxWithConstraints(Modifier.padding(top = S.segmentedTop.dp).fillMaxWidth().height(S.segmented.dp).background(S.track, RoundedCornerShape(17.dp))) {
            val segmentWidth = maxWidth.value / ranges.size
            Box(Modifier.offset(S.thumbInset.dp, S.thumbInset.dp).size((segmentWidth - 6f).dp, (S.segmented - 6f).dp)
                .graphicsLayer { translationX = (scene.thumb(time.doubleValue, reduced) * segmentWidth * density.density).toFloat() }
                .background(S.thumb, RoundedCornerShape(14.dp)))
            Row(Modifier.fillMaxSize()) {
                ranges.forEachIndexed { i, range ->
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { touch(); select(i, true) }
                        .semantics { selected = i == scene.to }, contentAlignment = Alignment.Center) {
                        Label(range.name, S.segmentSize, 17, if (i == scene.to) S.thumbInk else S.mute, if (i == scene.to) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
        Row(Modifier.padding(top = S.legendTop.dp), horizontalArrangement = Arrangement.spacedBy(S.chipGap.dp)) {
            for (line in 0..1) Row(Modifier.height(S.chipHeight.dp).background(S.tile, RoundedCornerShape(14.dp)).padding(horizontal = S.chipPad.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(S.sampleWidth.dp, S.sampleHeight.dp).background(colors[line], RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(S.chipGap.dp)); Label(labels[line], S.chipSize, 16, S.ink, FontWeight.Medium)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = S.chartTop.dp, bottom = S.chartBottom.dp)
            .semantics { contentDescription = "$title, ${ranges[scene.to].caption}" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var active = false; var failed = false; var delta = Offset.Zero
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        delta = change.position - down.position
                        if (!active && abs(delta.y) > 24f * density.density) failed = true
                        if (!active && !failed && abs(delta.x) > 16f * density.density) { active = true; dragging = true; touch() }
                        if (active) change.consume()
                        if (!change.pressed) break
                    } while (true)
                    if (active) {
                        if (abs(delta.x) >= S.swipe * density.density) select(scene.to + if (delta.x < 0) 1 else -1, true)
                        dragging = false; touch()
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, w, h -> chart(scene, ranges, colors, time.doubleValue, reduced, w, h, 0) } }
            Box(Modifier.fillMaxSize().padding(start = S.axis.dp, bottom = S.ticksHeight.dp).clipToBounds()) {
                Canvas(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(sigmaToRadius(S.glowBlur).dp, BlurredEdgeTreatment.Unbounded) else Modifier.graphicsLayer { alpha = 0.15f })) {
                    surface(0.dp) { _, _, w, h -> chart(scene, ranges, colors, time.doubleValue, reduced, w + S.axis, h + S.ticksHeight, 1) }
                }
            }
            Canvas(Modifier.fillMaxSize()) { surface(0.dp) { _, _, w, h -> chart(scene, ranges, colors, time.doubleValue, reduced, w, h, 2) } }
        }
        Row(Modifier.padding(top = S.statsTop.dp), horizontalArrangement = Arrangement.spacedBy(S.tileGap.dp)) {
            for (line in 0..1) Column(Modifier.weight(1f).background(S.tile, RoundedCornerShape(S.tileRadius.dp)).padding(S.tilePad.dp)) {
                Label("${labels[line]} · average", S.tileLabel, S.tileLabelLine, S.mute)
                Canvas(Modifier.padding(top = S.valueTop.dp).fillMaxWidth().height(S.valueLine.dp).clipToBounds().semantics { contentDescription = S.average(if (line == 0) ranges[scene.to].high else ranges[scene.to].low) }) {
                    surface(0.dp) { _, _, _, _ ->
                        val phase = time.doubleValue - scene.textAt
                        if (!reduced) for (ghost in scene.ghosts) { val u = S.quadratic((time.doubleValue - ghost.at) / S.valueOut); val old = ranges[ghost.index]; text(S.average(if (line == 0) old.high else old.low), 0f, -S.valueTravel * u.toFloat(), S.valueSize, S.valueLine, colors[line].copy(alpha = (1 - u).toFloat()), 600) }
                        val u = if (reduced) 1.0 else S.quadratic((phase - S.wipe) / S.valueIn); val next = ranges[scene.to]
                        text(S.average(if (line == 0) next.high else next.low), 0f, S.valueTravel * (1 - u).toFloat(), S.valueSize, S.valueLine, colors[line].copy(alpha = u.toFloat()), 600)
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(value: String, size: Int, height: Int, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, spacing: Double = 0.0) {
    BasicText(value, modifier, style = TextStyle(color = color, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, letterSpacing = spacing.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
}
private suspend fun until(target: Long) { while (true) { val left = target - System.nanoTime(); if (left <= 0) return; delay((left + 999_999L) / 1_000_000L) } }
private fun DrawScope.text(value: String, x: Float, top: Float, size: Float, height: Float, color: Color, weight: Int = 400, align: Int = 0) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color.toArgb(); fontFeatureSettings = "tnum"; typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false) else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        val baseline = top + (height - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2f
        val width = paint.measureText(value); canvas.nativeCanvas.drawText(value, x - if (align == 1) width / 2f else if (align == 2) width else 0f, baseline, paint)
    }
}
private fun DrawScope.chart(scene: Scene, ranges: List<LineRange>, colors: List<Color>, now: Double, still: Boolean, boxWidth: Float, boxHeight: Float, pass: Int) {
    val phase = scene.phase(now)
    val textPhase = now - scene.textAt
    val height = max(1f, boxHeight - S.ticksHeight); val width = boxWidth - S.axis - S.rightPad
    if (pass == 0) {
        for (k in 0..S.gridSteps) {
            val y = height * k / S.gridSteps
            text("${100 - k * 20} %", 0f, y - 7f, S.axisSize, S.axisLine, S.mute)
            clipRect(S.axis, 0f, boxWidth, height) { drawLine(S.grid, Offset(S.axis, floor(y + 0.5f) + 0.5f), Offset(boxWidth, floor(y + 0.5f) + 0.5f), 1f) }
        }
    }
    val shift = if (pass == 1) 0f else S.axis
    translate(shift, 0f) {
        clipRect(0f, 0f, boxWidth - S.axis, height) {
            for (line in 0..1) {
                val win = lineWindow(line, phase, scene.ends[line], still); val range = ranges[if (win.new) scene.to else scene.from]
                val values = if (line == 0) range.high else range.low; val tip = pointAt(values, win.to, width, height)
                if (pass == 0) drawPath(linePart(values, win.from, win.to, width, height), colors[line].copy(alpha = win.opacity.toFloat()), style = Stroke(S.lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val tipAlpha = win.opacity * if (win.new) { if (win.to <= 0) 0.0 else 1.0 } else if (still) 1.0 else 1 - S.smooth(phase, S.wipe * 0.75, S.wipe)
                if (pass == 1) drawCircle(colors[line].copy(alpha = (tipAlpha * 0.45).toFloat()), S.headGlow, tip)
                if (pass == 2) {
                    drawCircle(colors[line].copy(alpha = tipAlpha.toFloat()), S.head, tip)
                    val drawn = S.wipe + line * S.stagger + S.draw
                    val alpha = if (still) win.opacity else if (win.new) S.smooth(phase, drawn, drawn + S.labelIn) else 1 - S.smooth(phase, 0.0, S.wipe * 0.5)
                    val label = "${floor(values.last() + 0.5).toInt()} %"
                    drawIntoCanvas { canvas ->
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = S.labelSize; typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD; color = colors[line].copy(alpha = alpha.toFloat()).toArgb() }
                        canvas.nativeCanvas.drawText(label, width - paint.measureText(label) - 6f, tip.y + if (line == 0) -S.labelRise else S.labelDrop, paint)
                    }
                }
            }
        }
    }
    if (pass == 2) {
        fun ticks(range: LineRange, alpha: Double) { range.ticks.forEachIndexed { k, label -> text(label, S.axis + width * k / (range.ticks.size - 1), height + S.tickTop, S.tickSize, S.tickLine, S.mute.copy(alpha = alpha.toFloat()), align = if (k == 0) 0 else if (k == range.ticks.lastIndex) 2 else 1) } }
        if (!still) for (ghost in scene.ghosts) ticks(ranges[ghost.index], 1 - S.quadratic((now - ghost.at) / S.wipe))
        ticks(ranges[scene.to], if (still) 1.0 else S.quadratic((textPhase - S.wipe) / S.ticksIn))
    }
}
