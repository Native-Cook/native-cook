package com.ngocdevv.nativecook.blocks.charts.rangemorphpriceline

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** autoPlay: 1D rests, then every range in turn, a hold on the last, and a slower morph home. */
const val LOOP = 9f
private const val FIRST_STEP_AT = 0.9f
private const val LAST_STEP_AT = 6.15f
private const val HOME_AT = 7.5f
/** After a touch autoPlay waits this long, then morphs home and plays again. */
private const val USER_HOLD = 3f

// One morph: every point moves in y while x stays put; going home is a little slower.
private const val MORPH = 450
private const val HOME_MORPH = 600
private const val SLIDE = 200
private const val COLOR_FOR = 300
// The old change fades out before the new one fades in, so the two never read over each other.
private const val DELTA_OUT = 120
private const val DELTA_IN = 180
private const val SWIPE = 40f

private val Background = Color(0xFF000000)
private val Ink = Color(0xFFFFFFFF)
private val Mute = Color(0xFF8E8E93)
private val Red = Color(0xFFE0102B)
private val Green = Color(0xFF5BE02B)
private val RedText = Color(0xFFFF2D3D)
// The area under the line: the line's hue, dark, deepening to black at the base.
private val FillRed = listOf(Color(0xFF3D0008), Color(0xFF1A0004))
private val FillGreen = listOf(Color(0xFF1F3D0A), Color(0xFF0C1804))
private val Badge = Color(0xFF4CD91F)
private val ButtonColor = Color(0xFF3FD62B)
private val SelectorColor = Color(0xFF1C1C1E)
private val Selected = Color(0xFF3A3A3C)

private val MARGIN = 24.dp
private val SELECTOR_SIDE = 26.dp
private const val SELECTOR_H = 30f
private const val SELECTOR_INSET = 2f
private const val SEPARATOR_H = 14f
private const val LINE = 2f
// The line keeps to this band of the chart's height (from the top); the area runs to its base.
private const val BAND_TOP = 0.16f
private const val BAND_BOTTOM = 0.8f
private val BUTTON_H = 56.dp
private val BADGE_SIZE = 40.dp

// Material Design's "sine-wave" glyph, on its 24-point grid.
private val SineWave = PathParser().parsePathString(
    "M16.5,21C13.5,21 12.31,16.76 11.05,12.28C10.14,9.04 9,5 7.5,5C4.11,5 4,11.93 4,12H2C2,11.63 2.06,3 7.5,3" +
        "C10.5,3 11.71,7.25 12.97,11.74C13.83,14.8 15,19 16.5,19C19.94,19 20.07,12.07 20.07,12H22.07" +
        "C22.07,12.37 22,21 16.5,21Z",
).toPath()

/** `Easing.inOut` of a power curve, as Reanimated's `Easing.inOut(Easing.cubic)` (3) or its default (2). */
private fun inOut(t: Float, power: Int): Float =
    if (t < 0.5f) (2 * t).pow(power) / 2 else 1 - (2 - 2 * t).pow(power) / 2

/** A straight RGB mix, as `interpolateColor(…, 'RGB', { gamma: 1 })`. */
private fun mix(a: Color, b: Color, t: Float) =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)

/** A number timed against the frame clock, so drawing can sample it at any frame. */
private class Tween(val from: Float, val to: Float, val start: Long = 0L, val duration: Long = 0L, val power: Int = 2) {
    fun at(now: Long): Float {
        if (duration <= 0L) return to
        val t = ((now - start).toFloat() / duration).coerceIn(0f, 1f)
        return from + (to - from) * inOut(t, power)
    }

    fun retarget(target: Float, now: Long, durationMs: Int, power: Int) =
        Tween(at(now), target, now, durationMs * 1_000_000L, power)
}

/** Everything that moves: the line morphs from [shown] to [target] by [progress]. */
private class Motion(
    val shown: FloatArray,
    val target: FloatArray,
    val progress: Tween,
    val slot: Tween,
    val up: Tween,
) {
    fun line(now: Long): FloatArray {
        val p = progress.at(now)
        return FloatArray(shown.size) { shown[it] + (target[it] - shown[it]) * p }
    }
}

private class Step(val at: Float, val index: Int, val time: Int)

/**
 * An exchange-rate screen: a jagged price line over a dark area, and a range selector under it.
 * Choosing a range morphs the line into the next series point by point while the area follows,
 * the highlight slides, and the line turns red or green with the sign of the change.
 *
 * @param price The rate now, as shown under the title.
 * @param ranges The ranges of the selector, each with its own series; every one is drawn with the same point count.
 * @param from The left button at the bottom.
 * @param to The right button at the bottom.
 * @param autoPlay Steps through the ranges by itself, every [LOOP] seconds.
 */
@Composable
fun RangeMorphPriceLine(
    title: String = "USD per 1 EUR",
    price: String = "$1.0835",
    ranges: List<RangeMorphPriceLineRange> = RangeMorphPriceLineRanges,
    from: String = "EUR",
    to: String = "USD",
    autoPlay: Boolean = false,
    onRangeChange: ((RangeMorphPriceLineRange, Int) -> Unit)? = null,
    onCurrency: ((String) -> Unit)? = null,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val currentRanges by rememberUpdatedState(ranges)
    val rangeChanged by rememberUpdatedState(onRangeChange)

    var motion by remember {
        val first = normalized(ranges[0].series)
        val rising = if (ranges[0].change >= 0) 1f else 0f
        mutableStateOf(Motion(first, first, Tween(1f, 1f), Tween(0f, 0f), Tween(rising, rising)))
    }
    var range by remember { mutableIntStateOf(0) }
    // The frame clock runs only while something moves; at rest every tween is read at its end.
    var now by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var restAt by remember { mutableLongStateOf(0L) }
    var touches by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** A new range starts from wherever the line is, so a morph can be interrupted by another. */
    fun select(index: Int, duration: Int, at: Long) {
        val m = motion
        val rising = if (currentRanges[index].change >= 0) 1f else 0f
        motion = Motion(
            shown = m.line(at),
            target = normalized(currentRanges[index].series),
            progress = Tween(0f, 0f).retarget(1f, at, if (still) 0 else duration, 3),
            slot = m.slot.retarget(index.toFloat(), at, if (still) 0 else SLIDE, 3),
            up = m.up.retarget(rising, at, if (still) 0 else COLOR_FOR, 2),
        )
        range = index
        rangeChanged?.invoke(currentRanges[index], index)
        if (!still) {
            // The first frame of the morph draws its start, not the end the resting clock reads.
            if (now == Long.MAX_VALUE) now = at
            restAt = max(restAt, at + max(duration, max(SLIDE, COLOR_FOR)) * 1_000_000L)
        }
    }

    /**
     * A touch takes over at once; some seconds after the last one the loop starts again, and its
     * first step morphs the line home to the first range.
     */
    fun touched() {
        touches++
    }

    LaunchedEffect(restAt) {
        while (true) {
            val frame = withFrameNanos { it }
            if (frame >= restAt) break
            now = frame
        }
        now = Long.MAX_VALUE
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            if (dragging) return@LaunchedEffect
        }
        // autoPlay's steps: when, which range, and how long its morph takes.
        val count = currentRanges.size
        val gap = if (count > 2) (LAST_STEP_AT - FIRST_STEP_AT) / (count - 2) else 0f
        val steps = listOf(Step(0f, 0, HOME_MORPH)) +
            (1 until count).map { Step(FIRST_STEP_AT + (it - 1) * gap, it, MORPH) } +
            Step(HOME_AT, 0, HOME_MORPH)
        var origin = System.nanoTime()
        while (true) {
            // Each step at its own time from the start of the loop, so every loop is the same.
            for (step in steps) {
                val at = origin + (step.at * 1e9f).toLong()
                delay(max(0L, (at - System.nanoTime()) / 1_000_000))
                if (step.index != range) select(step.index, step.time, at)
            }
            origin += (LOOP * 1e9f).toLong()
        }
    }

    fun tap(index: Int) {
        touched()
        haptic()
        select(index, MORPH, System.nanoTime())
    }

    val current = ranges[range]
    val rose = current.change >= 0

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(top = insets.calculateTopPadding() + 20.dp),
    ) {
        // Header.
        Column(Modifier.padding(horizontal = MARGIN)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    title,
                    Modifier.weight(1f),
                    style = TextStyle(color = Ink, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .size(BADGE_SIZE)
                        .background(Badge, CircleShape)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) { drawPath(SineWave, Ink) }
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    price,
                    style = TextStyle(color = Mute, fontSize = 20.sp, lineHeight = 25.sp, fontFeatureSettings = "tnum"),
                )
                val label = "${if (rose) "Up" else "Down"} ${abs(current.change)} percent over ${current.label}"
                AnimatedContent(
                    targetState = range,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .height(25.dp)
                        .widthIn(min = 100.dp)
                        .clearAndSetSemantics { contentDescription = label },
                    transitionSpec = {
                        fadeIn(tween(if (still) 0 else DELTA_IN, delayMillis = if (still) 0 else DELTA_OUT)) togetherWith
                            fadeOut(tween(if (still) 0 else DELTA_OUT))
                    },
                    contentAlignment = Alignment.TopStart,
                    label = "delta",
                ) { index ->
                    val shown = ranges[index]
                    val up = shown.change >= 0
                    BasicText(
                        "${if (up) "↑" else "↓"} ${String.format(Locale.US, "%.2f", abs(shown.change))}%",
                        style = TextStyle(
                            color = if (up) Green else RedText,
                            fontSize = 20.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        // Chart.
        Canvas(
            Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .weight(1f)
                .semantics { contentDescription = "$title, ${current.label}" }
                .pointerInput(Unit) {
                    var travel = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            travel = 0f
                            dragging = true
                            touched()
                        },
                        onDragEnd = {
                            dragging = false
                            // Swiping left goes on to a longer range, as the selector reads left to right.
                            val dx = travel.toDp().value
                            val step = if (dx < -SWIPE) 1 else if (dx > SWIPE) -1 else 0
                            val next = min(currentRanges.size - 1, max(0, range + step))
                            if (next != range) {
                                select(next, MORPH, System.nanoTime())
                                haptic()
                            }
                            touched()
                        },
                        onDragCancel = {
                            dragging = false
                            touched()
                        },
                    ) { _, amount -> travel += amount }
                },
        ) {
            val m = motion
            val values = m.line(now)
            val up = m.up.at(now)
            val width = size.width
            val height = size.height
            val dx = width / (values.size - 1)
            val top = height * BAND_TOP
            val span = height * (BAND_BOTTOM - BAND_TOP)
            val line = Path()
            values.forEachIndexed { i, v ->
                val y = top + (1 - v) * span
                if (i == 0) line.moveTo(0f, y) else line.lineTo(i * dx, y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(mix(FillRed[0], FillGreen[0], up), mix(FillRed[1], FillGreen[1], up)),
                    startY = top,
                    endY = height,
                ),
            )
            drawPath(
                line,
                mix(Red, Green, up),
                style = Stroke(width = LINE.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Range selector.
        BoxWithConstraints(
            Modifier
                .padding(top = 28.dp, start = SELECTOR_SIDE, end = SELECTOR_SIDE)
                .fillMaxWidth()
                .height(SELECTOR_H.dp)
                .background(SelectorColor, RoundedCornerShape(8.dp)),
        ) {
            val segment = (maxWidth.value - SELECTOR_INSET * 2) / ranges.size
            Box(
                Modifier
                    .offset { IntOffset(((SELECTOR_INSET + motion.slot.at(now) * segment) * density.density).roundToInt(), (SELECTOR_INSET * density.density).roundToInt()) }
                    .width(segment.dp)
                    .height((SELECTOR_H - SELECTOR_INSET * 2).dp)
                    .background(Selected, RoundedCornerShape(6.dp)),
            )
            // Hidden next to the highlight, as in the system's segmented control.
            for (i in 0 until ranges.size - 1) {
                Box(
                    Modifier
                        .offset(x = (SELECTOR_INSET + (i + 1) * segment).dp, y = ((SELECTOR_H - SEPARATOR_H) / 2).dp)
                        .width(with(density) { 2f.toDp() })
                        .height(SEPARATOR_H.dp)
                        .graphicsLayer {
                            val slot = motion.slot.at(now)
                            alpha = min(1f, min(abs(slot - i), abs(slot - i - 1)))
                        }
                        .background(Selected),
                )
            }
            Row(Modifier.fillMaxSize().padding(horizontal = SELECTOR_INSET.dp)) {
                ranges.forEachIndexed { index, r ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { selected = index == range }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClickLabel = r.label,
                            ) { tap(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            r.label,
                            style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                            color = ColorProducer { mix(Mute, Ink, max(0f, 1 - abs(motion.slot.at(now) - index))) },
                        )
                    }
                }
            }
        }

        // Currency buttons.
        Row(
            Modifier
                .padding(top = 28.dp, start = SELECTOR_SIDE, end = SELECTOR_SIDE)
                .padding(bottom = max(insets.calculateBottomPadding().value, 16f).dp + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            listOf(from, to).forEach { code ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Row(
                    Modifier
                        .weight(1f)
                        .height(BUTTON_H)
                        .graphicsLayer { alpha = if (pressed) 0.7f else 1f }
                        .background(ButtonColor, CircleShape)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                            touched()
                            onCurrency?.invoke(code)
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RangeMorphPriceLineCurrencyDisc(if (code == "USD") CurrencyKind.Usd else CurrencyKind.Eur)
                    BasicText(code, style = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}
