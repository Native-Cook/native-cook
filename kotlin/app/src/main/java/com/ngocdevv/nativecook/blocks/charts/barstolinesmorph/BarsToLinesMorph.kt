package com.ngocdevv.nativecook.blocks.charts.barstolinesmorph

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor

private val Background = Color(0xFF0F0F10)
private val Card = Color(0xFF181819)
private val CardEdge = Color(0xFF242426)
private val Selected = rgba(255, 255, 255, 0.04f)
private val SelectedEdge = Color(0xFF3A3A3C)
private val Ink = Color.White
private val Soft = Color(0xFFA1A1A6)
private val Mute = Color(0xFF6B6B70)
private val Grid = Color(0xFF2C2C30)
private val Red = Color(0xFFE5384B)
private val RedInk = Color(0xFFFF4D5E)

private const val MARGIN = 16f
private const val GAP = 8f
private const val TILE_HEIGHT = 76f
private const val TILE_RADIUS = 16f
private const val AXIS = 32f
private const val HOUR_LABELS = 30f
private val ChartLook = Look(barMax = 8f, barShare = 0.6f, barRadius = 2f, dot = 3f, gap = 1f)
private const val LINE_WIDTH = 1.75f
private const val LABEL_SIZE = 11f
private const val PEAK_RISE = 8f
private const val LOW_DROP = 16f
private val TickHeights = listOf(0.1f, 0.5f, 0.9f)
private const val SELECT_MOVE = 200
private val CubicOut = CubicBezierEasing(1f / 3f, 1f, 2f / 3f, 1f)
const val LOOP = 9f
private const val SWITCH_AT = 1f
private const val RETURN_AT = 5.2f
private const val USER_HOLD = 3f
private const val HEADER_IN = 0.2f
private const val TITLE_OUT = 0.12f
private const val AXIS_OUT = 0.15f
private const val AXIS_DELAY = 0.3f
private const val CHART_HEADER = 42f
private const val CHART_MARGIN = 16f
private const val CANVAS_TAIL = 20f
private const val HOURS_TOP = 8f
private const val HOUR_WIDTH = 60f
private const val TITLE_SIZE = 30f
private const val TITLE_HEIGHT = 36f
private const val SUBTITLE_SIZE = 13f
private const val SUBTITLE_HEIGHT = 18f
private const val SUBTITLE_MARGIN = 2f
private const val TILES_MARGIN = 20f
private const val TOP = 12f
private const val TILE_PAD = 12f
private const val ICON_SIZE = 15f
private const val ICON_GAP = 6f
private const val WARNING_SIZE = 14f
private const val NAME_SIZE = 12f
private const val NAME_HEIGHT = 16f
private const val VALUE_SIZE = 22f
private const val VALUE_HEIGHT = 27f
private const val UNIT_SIZE = 11f
private const val TIME_SIZE = 10f
private const val TIME_HEIGHT = 13f
private const val CARD_TITLE_SIZE = 16f
private const val CARD_TITLE_HEIGHT = 21f
private const val CAPTION_SIZE = 12f
private const val CAPTION_HEIGHT = 16f
private const val AXIS_SIZE = 10f
private const val AXIS_HEIGHT = 14f
private const val HOUR_TICK_WIDTH = 2f
private const val HOUR_TICK_HEIGHT = 4f
private const val HOUR_TICK_RADIUS = 1f
private const val FOOTNOTE_MARGIN = 4f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private fun chartText(size: Float, height: Float, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeightStyle = Centered, platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private data class Transition(val start: Long)

/** Six vital tiles and a chart that folds into dots before changing its readings and shape. */
@Composable
fun BarsToLinesMorph(
    title: String = "Vitals",
    subtitle: String = "Today · updated 9:48 AM",
    vitals: List<Vital> = SampleVitals,
    initialVital: Int = 0,
    hours: List<String> = listOf("12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM"),
    footnote: String = "Sample data",
    autoPlay: Boolean = false,
    onSelect: ((Vital) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val plots = remember(vitals) { vitals.map(::Plot) }
    val twoLines = plots.indexOfFirst { it.lines == 2 }
    val other = max(0, if (twoLines >= 0 && twoLines != initialVital) twoLines else plots.indexOfFirst { it.bars != plots[initialVital].bars })
    // Functions retained by the loop read the latest props and state, including rapid taps.
    val items by rememberUpdatedState(vitals)
    val alternate by rememberUpdatedState(other)
    val first by rememberUpdatedState(initialVital)
    val callback by rememberUpdatedState(onSelect)
    var selected by remember { mutableIntStateOf(initialVital) }
    var fromIndex by remember { mutableIntStateOf(initialVital) }
    var previous by remember { mutableStateOf<Int?>(null) }
    var transition by remember { mutableStateOf<Transition?>(null) }
    var touches by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val frameTime = remember { mutableLongStateOf(System.nanoTime()) }

    fun phase(): Float = transition?.let { ((frameTime.longValue - it.start) / 1e9f).coerceIn(0f, TOTAL) } ?: TOTAL

    fun select(index: Int, byHand: Boolean) {
        if (index == selected) return
        val now = System.nanoTime()
        val p = transition?.let { (now - it.start) / 1e9f } ?: TOTAL
        if (p >= B_END) fromIndex = selected
        previous = selected
        selected = index
        transition = Transition(now)
        frameTime.longValue = now
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        callback?.invoke(items[index])
    }

    LaunchedEffect(transition) {
        val run = transition ?: return@LaunchedEffect
        val visualEnd = if (reducedMotion) FADE * 2f else TOTAL
        do {
            val now = withFrameNanos { it }
            frameTime.longValue = max(now, run.start)
        } while ((frameTime.longValue - run.start) / 1e9f < visualEnd)
        until(run.start + (TOTAL * 1e9).toLong())
        transition = null
        previous = null
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            until(lastTouch + (USER_HOLD * 1e9).toLong())
            if (selected != first) {
                select(first, byHand = false)
                until(transition!!.start + (TOTAL * 1e9).toLong())
            }
        }
        var origin = System.nanoTime()
        while (true) {
            until(origin + (SWITCH_AT * 1e9).toLong())
            select(alternate, byHand = false)
            until(origin + (RETURN_AT * 1e9).toLong())
            select(first, byHand = false)
            origin += (LOOP * 1e9).toLong()
            until(origin)
        }
    }

    val frameX = animateFloatAsState((selected % 2).toFloat(), tween(if (reducedMotion) 0 else SELECT_MOVE, easing = CubicOut), label = "tile column")
    val frameY = animateFloatAsState((selected / 2).toFloat(), tween(if (reducedMotion) 0 else SELECT_MOVE, easing = CubicOut), label = "tile row")
    val phaseNow by rememberUpdatedState(::phase)
    val font = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = LABEL_SIZE
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }
    val axisFont = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = AXIS_SIZE; typeface = Typeface.DEFAULT } }

    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val width = maxWidth.value - MARGIN * 2f
        val tileWidth = (width - GAP) / 2f
        val rows = (vitals.size + 1) / 2
        Column(
            Modifier.fillMaxSize().padding(
                start = MARGIN.dp, end = MARGIN.dp,
                top = insets.calculateTopPadding() + TOP.dp,
                bottom = max(insets.calculateBottomPadding().value, MARGIN).dp,
            ),
        ) {
            BasicText(title, style = chartText(TITLE_SIZE, TITLE_HEIGHT, Ink, FontWeight.Bold, -0.6f), modifier = Modifier.height(TITLE_HEIGHT.dp))
            Spacer(Modifier.height(SUBTITLE_MARGIN.dp))
            BasicText(subtitle, style = chartText(SUBTITLE_SIZE, SUBTITLE_HEIGHT, Mute), modifier = Modifier.height(SUBTITLE_HEIGHT.dp))
            Spacer(Modifier.height(TILES_MARGIN.dp))
            Box(Modifier.fillMaxWidth().height((rows * TILE_HEIGHT + max(0, rows - 1) * GAP).dp)) {
                vitals.forEachIndexed { index, vital ->
                    Tile(
                        vital, selected == index,
                        Modifier.offset(x = ((index % 2) * (tileWidth + GAP)).dp, y = ((index / 2) * (TILE_HEIGHT + GAP)).dp).size(tileWidth.dp, TILE_HEIGHT.dp),
                    ) {
                        lastTouch = System.nanoTime()
                        touches++
                        select(index, byHand = true)
                    }
                }
                Box(
                    Modifier.size(tileWidth.dp, TILE_HEIGHT.dp)
                        .graphicsLayer {
                            translationX = frameX.value * (tileWidth + GAP).dp.toPx()
                            translationY = frameY.value * (TILE_HEIGHT + GAP).dp.toPx()
                        }
                        .background(Selected, RoundedCornerShape(TILE_RADIUS.dp))
                        .border(1.dp, SelectedEdge, RoundedCornerShape(TILE_RADIUS.dp)),
                )
            }
            Spacer(Modifier.height(MARGIN.dp))
            val vital = vitals[selected]
            val leaving = previous?.let { vitals[it] }
            Column(
                Modifier.fillMaxWidth().weight(1f).background(Card, RoundedCornerShape(TILE_RADIUS.dp))
                    .border(1.dp, CardEdge, RoundedCornerShape(TILE_RADIUS.dp)).padding((MARGIN + 1f).dp),
            ) {
                Column(Modifier.fillMaxWidth().height(CHART_HEADER.dp)) {
                    Box(Modifier.fillMaxWidth().height(CARD_TITLE_HEIGHT.dp)) {
                        BasicText(vital.name, style = chartText(CARD_TITLE_SIZE, CARD_TITLE_HEIGHT, Ink, FontWeight.SemiBold), modifier = Modifier.graphicsLayer {
                            alpha = if (reducedMotion || transition == null) 1f else layoutFade(phaseNow() / HEADER_IN)
                        })
                        if (leaving != null) BasicText(leaving.name, style = chartText(CARD_TITLE_SIZE, CARD_TITLE_HEIGHT, Ink, FontWeight.SemiBold), modifier = Modifier.graphicsLayer {
                            alpha = if (reducedMotion) 0f else 1f - layoutFade(phaseNow() / TITLE_OUT)
                        })
                    }
                    BasicText(caption(vital), style = chartText(CAPTION_SIZE, CAPTION_HEIGHT, Mute), modifier = Modifier.graphicsLayer {
                        alpha = if (reducedMotion || transition == null) 1f else layoutFade(phaseNow() / HEADER_IN)
                    })
                }
                Spacer(Modifier.height(CHART_MARGIN.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "${vital.name}, ${caption(vital)}" }) {
                    val w = max(0f, maxWidth.value - AXIS)
                    val h = max(0f, maxHeight.value - HOUR_LABELS)
                    Canvas(Modifier.width(maxWidth).height((h + CANVAS_TAIL).dp)) {
                        surface(0.dp) { _, _, _, _ ->
                            val p = phaseNow()
                            val axisIn = if (reducedMotion || transition == null) 1f else layoutFade((p - AXIS_DELAY) / HEADER_IN)
                            drawAxis(vital, axisIn, h, axisFont)
                            if (leaving != null) drawAxis(leaving, if (reducedMotion) 0f else 1f - layoutFade(p / AXIS_OUT), h, axisFont)
                            translate(AXIS, 0f) {
                                clipRect(0f, 0f, w, h + CANVAS_TAIL) {
                                    for (at in TickHeights) {
                                        val y = floor(h * (1f - at) + 0.5f) + 0.5f
                                        drawLine(Grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)))
                                    }
                                    val from = plots[fromIndex]
                                    val to = plots[selected]
                                    drawScene(scene(p, from, to, w, h, ChartLook, reducedMotion), from, to, w, h)
                                    for (which in 0..1) {
                                        val departing = if (reducedMotion) p < FADE else p < B_END
                                        val index = if (departing) fromIndex else selected
                                        drawExtreme(which, p, plots[index], vitals[index], w, h, reducedMotion, font)
                                    }
                                }
                            }
                        }
                    }
                    hours.forEachIndexed { k, label ->
                        Column(
                            Modifier.offset(x = (AXIS + (k * 4f + 0.5f) * w / HOURS - HOUR_WIDTH / 2f).dp, y = (h + HOURS_TOP).dp)
                                .width(HOUR_WIDTH.dp).height(HOUR_LABELS.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(HOUR_TICK_WIDTH.dp, HOUR_TICK_HEIGHT.dp).background(Grid, RoundedCornerShape(HOUR_TICK_RADIUS.dp)))
                            Spacer(Modifier.height(FOOTNOTE_MARGIN.dp))
                            BasicText(label, style = chartText(TIME_SIZE, TIME_HEIGHT, Mute), maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(FOOTNOTE_MARGIN.dp))
                BasicText(footnote, style = chartText(TIME_SIZE, TIME_HEIGHT, Mute), modifier = Modifier.height(TIME_HEIGHT.dp))
            }
        }
    }
}

/** Sleep to absolute deadlines so every autoplay period stays nine seconds. */
private suspend fun until(target: Long) {
    while (true) {
        val left = target - System.nanoTime()
        if (left <= 0L) return
        delay((left + 999_999L) / 1_000_000L)
    }
}

private fun caption(vital: Vital) = when (val chart = vital.chart) {
    is VitalChart.Bars -> "Hourly low to high"
    is VitalChart.Lines -> if (chart.second == null) "Hourly average" else "Systolic and diastolic, hourly"
}

@Composable
private fun Tile(vital: Vital, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(Card, RoundedCornerShape(TILE_RADIUS.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }.padding(TILE_PAD.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth().height(NAME_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ICON_GAP.dp)) {
            VitalIcon(vital)
            BasicText(vital.name, style = chartText(NAME_SIZE, NAME_HEIGHT, if (selected) Ink else Soft), maxLines = 1, modifier = Modifier.weight(1f))
            if (vital.warning) Icon(Icons.Filled.Warning, contentDescription = null, tint = RedInk, modifier = Modifier.size(WARNING_SIZE.dp))
        }
        Row(Modifier.fillMaxWidth().height(VALUE_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
            Value(vital, Modifier.weight(1f).alignByBaseline())
            Spacer(Modifier.width(ICON_GAP.dp))
            BasicText(vital.time, style = chartText(TIME_SIZE, TIME_HEIGHT, Mute), maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun Value(vital: Vital, modifier: Modifier) {
    val measure = rememberTextMeasurer()
    val density = LocalDensity.current
    val color = if (vital.warning) RedInk else Ink
    fun valueText(scale: Float) = buildAnnotatedString {
        append(vital.value)
        withStyle(SpanStyle(fontSize = (UNIT_SIZE * scale).sp, fontWeight = FontWeight.Normal, color = if (vital.warning) RedInk else Mute)) { append(" ${vital.unit}") }
    }
    BoxWithConstraints(modifier) {
        val style = chartText(VALUE_SIZE, VALUE_HEIGHT, color, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum")
        val text = valueText(1f)
        val measured = remember(text, style) { measure.measure(text, style, softWrap = false, maxLines = 1) }
        val factor = min(1f, with(density) { maxWidth.toPx() } / max(1, measured.size.width)).coerceAtLeast(0.8f)
        BasicText(valueText(factor), style = style.copy(fontSize = (VALUE_SIZE * factor).sp), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun VitalIcon(vital: Vital) {
    if (vital.icon == "lungs") {
        Canvas(Modifier.size(ICON_SIZE.dp)) {
            surface(0.dp) { _, _, _, _ ->
                val path = Path().apply {
                    moveTo(6.5f, 4f); cubicTo(5f, 1f, 2f, 5f, 1f, 9f); cubicTo(0f, 13f, 3f, 14f, 6f, 11f); close()
                    moveTo(8.5f, 4f); cubicTo(10f, 1f, 13f, 5f, 14f, 9f); cubicTo(15f, 13f, 12f, 14f, 9f, 11f); close()
                }
                drawPath(path, vital.iconColor)
                drawLine(vital.iconColor, Offset(7.5f, 1f), Offset(7.5f, 6f), strokeWidth = 1f)
            }
        }
    } else {
        val icon = when (vital.icon) {
            "heart-pulse" -> Icons.Outlined.MonitorHeart
            "thermometer" -> Icons.Outlined.DeviceThermostat
            "gauge" -> Icons.Outlined.Speed
            else -> Icons.Outlined.WaterDrop
        }
        Box(Modifier.size(ICON_SIZE.dp)) {
            Icon(icon, contentDescription = null, tint = vital.iconColor, modifier = Modifier.fillMaxSize())
            if (vital.icon == "water-percent") {
                BasicText("%", style = chartText(5f, 6f, vital.iconColor, FontWeight.Bold), modifier = Modifier.align(Alignment.Center).offset(y = 2.dp))
            }
        }
    }
}

private fun DrawScope.drawAxis(vital: Vital, opacity: Float, height: Float, paint: Paint) {
    paint.color = Mute.toArgb()
    paint.alpha = (opacity * 255f).toInt().coerceIn(0, 255)
    drawIntoCanvas { canvas ->
        vital.ticks.forEachIndexed { k, value ->
            val top = height * (1f - TickHeights[k]) - AXIS_HEIGHT / 2f
            val baseline = top + (AXIS_HEIGHT - paint.fontMetrics.descent - paint.fontMetrics.ascent) / 2f
            canvas.nativeCanvas.drawText(number(value), 0f, baseline, paint)
        }
    }
}

private fun DrawScope.drawScene(scene: Scene, from: Plot, to: Plot, width: Float, height: Float) {
    val c1 = mixColor(from.colors[0], to.colors[0], scene.mix)
    val c2 = mixColor(from.colors[1], to.colors[1], scene.mix)
    val bars = limitClips(scene.above, scene.below, width, height, ChartLook.gap)
    val lines = limitClips(scene.above, scene.below, width, height, 0f)
    clipPath(bars.second) { drawPath(scene.bars, c1, alpha = scene.opacity) }
    clipPath(bars.first) { drawPath(scene.bars, Red, alpha = scene.opacity) }
    val measure = PathMeasure()
    fun trimmed(path: Path): Path {
        if (scene.trim >= 1f) return path
        measure.setPath(path, false)
        return Path().also { measure.getSegment(0f, measure.length * scene.trim, it, true) }
    }
    val line1 = trimmed(scene.line1)
    val line2 = trimmed(scene.line2)
    val stroke = Stroke(LINE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for ((clip, first, second) in listOf(Triple(lines.second, c1, c2), Triple(lines.first, RedInk, RedInk))) {
        clipPath(clip) {
            drawPath(line1, first, alpha = scene.opacity, style = stroke)
            if (scene.lineCount == 2) drawPath(line2, second, alpha = scene.opacity, style = stroke)
            drawPath(scene.dots2, second, alpha = scene.opacity)
            drawPath(scene.dots1, first, alpha = scene.opacity)
        }
    }
}

private fun DrawScope.drawExtreme(which: Int, p: Float, plot: Plot, vital: Vital, width: Float, height: Float, still: Boolean, paint: Paint) {
    val leaving = if (still) p < FADE else p < B_END
    val hour = if (which == 0) plot.peak else plot.trough
    val readings = if (which == 0) (if (plot.bars) plot.b else plot.a) else (if (plot.bars) plot.a else plot.b)
    val text = plot.extremes(vital.chartUnit)[which]
    val textWidth = paint.measureText(text)
    val x = min(width - textWidth, max(0f, (hour + 0.5f) * width / HOURS - textWidth / 2f))
    val y = yOf(readings[hour], plot, height) + if (which == 0) -PEAK_RISE else LOW_DROP
    val shown = if (still) {
        if (leaving) 1f - p / FADE else smooth(p, FADE, FADE * 2f)
    } else {
        if (leaving) 1f - smooth(p, 0f, LABEL_OUT) else smooth(p, C_END, C_END + LABEL_IN)
    }
    val red = if (which == 0) plot.peakAbove else plot.troughBelow
    val color = if (red) RedInk else plot.colors[if (which == 0) 0 else if (plot.lines == 2) 1 else 0]
    paint.color = color.toArgb()
    paint.alpha = (shown * 255f).toInt().coerceIn(0, 255)
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y, paint) }
}
