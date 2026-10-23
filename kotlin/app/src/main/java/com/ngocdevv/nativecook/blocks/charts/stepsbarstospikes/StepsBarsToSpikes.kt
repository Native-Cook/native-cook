package com.ngocdevv.nativecook.blocks.charts.stepsbarstospikes

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.charts.stepsbarstospikes.StepsBarsToSpikesChart.drawChart
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private typealias Chart = StepsBarsToSpikesChart
private typealias Figures = StepsBarsToSpikesData

private val FIELD_TOP = Color(0xFF2B1BFF)
private val FIELD_BOTTOM = Color(0xFF3A2BFF)
private val SHEET = Color(0xFF101012)
private val INK = Color.White
private val FIELD_MUTE = Color.White.copy(alpha = 0.45f)
private val FIELD_FAINT = Color.White.copy(alpha = 0.3f)
private val MUTE = Color(0xFF8A8A8E)
private val DOWN = Color(0xFFFF5A4E)
private val UP = Color(0xFF3DDC97)
private val SWITCH_TRACK = Color(0xFF1E1E22)
private val BUTTON_DIM = Color(0xFF1E1E22)
private val NAV_TRACK = Color(0xFF18181B)
private val CHIP = Color.White.copy(alpha = 0.12f)
private val BADGE = Color.White.copy(alpha = 0.7f)
// The big number is white text tinted by this gradient laid over it with `darken`, which leaves the
// darker sheet around the digits as it is; drawn here as the gradient kept where the digits are.
private val NUMBER_TINT = listOf(Color(0xFF7C8CFF), Color(0xFFC6CCFF))

// Layout, in points.
private val MARGIN = 20.dp
// The blue field takes this share of the screen; the sheet over the rest has rounded top corners.
private const val FIELD_SHARE = 0.56f
private val SHEET_RADIUS = 28.dp
private const val AXIS_WIDTH = 34f
private const val X_LABELS = 22f
private const val TICKS = 9
private val KEY = 32.dp
private val NAV_HEIGHT = 48.dp

// Motion: the switch's thumb springs (damping 20, stiffness 260, mass 0.7), as Compose's unit-mass spring.
private const val THUMB_STIFFNESS = 260f / 0.7f
private val THUMB_DAMPING_RATIO = 20f / (2f * kotlin.math.sqrt(260f * 0.7f))
// Labels fade out over 0.15 s and the new ones in over 0.25 s, the chart's after 0.15 s.
private const val FADE_OUT = 0.15
private const val FADE_IN = 0.25
private const val FADE_IN_DELAY = 0.15

// The autoPlay timeline, in seconds from the start of each loop.
const val STEPS_BARS_TO_SPIKES_LOOP = 8.0
private val SWITCHES = listOf(1.0 to 1, 2.8 to 2, 5.8 to 0)
// A finger drawn over the month's spikes, by bar.
private const val DRAG_DOWN = 4.0
private const val DRAG_FROM = 1
private const val DRAG_TO = 11
private const val DRAG_START = 4.1
private const val DRAG_END = 5.0
private const val DRAG_UP = 5.25
// Seconds after the last touch before the chart returns to the days and the loop starts again.
private const val USER_HOLD = 3.0

private fun now() = System.nanoTime() / 1e9

/** Reanimated's default timing curve, which its fades use. */
private fun quad(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
}

/** The times within a loop at which the drawn finger moves onto each next bar. */
private val FINGER_STEPS: List<Pair<Double, Int>> = buildList {
    add(DRAG_DOWN to DRAG_FROM)
    val span = (DRAG_TO - DRAG_FROM).toDouble()
    for (bar in DRAG_FROM + 1..DRAG_TO) {
        // Where the smoothstep glide rounds up to this bar: its inverse, in closed form.
        val y = ((bar - DRAG_FROM) - 0.5) / span
        val x = 0.5 - sin(asin(1 - 2 * y) / 3)
        add(DRAG_START + (DRAG_END - DRAG_START) * x to bar)
    }
    add(DRAG_UP to -1)
}

/** The screen's moving parts, kept in one remembered object so handlers read current values. */
private class StepsState {
    var viewIndex by mutableIntStateOf(0)
    var fromIndex by mutableIntStateOf(0)
    var switchAt by mutableDoubleStateOf(-1e9)
    var switching by mutableStateOf(false)
    var frame by mutableDoubleStateOf(0.0)
    var picked by mutableIntStateOf(-1)
    var touches by mutableIntStateOf(0)
    var holding = false
    var lastTouch = 0.0
    /** Where the bars stand, for the finger; set as the chart is drawn. */
    var box = StepsBarsToSpikesChart.Box(AXIS_WIDTH, 0f, 0f, 0f)

    var views: List<StepsBarsToSpikesView> = Figures.VIEWS
    var still = false
    var stepHaptic: () -> Unit = {}
    var pressHaptic: () -> Unit = {}
    var onViewChange: ((StepsBarsToSpikesView, Int) -> Unit)? = null
    var onSelect: ((Int, Double) -> Unit)? = null

    val view get() = views[viewIndex]

    fun since(at: Double) = min(Chart.SWITCH_FOR, at - switchAt)

    fun choose(index: Int, auto: Boolean) {
        if (index == viewIndex) return
        if (!auto) pressHaptic()
        fromIndex = viewIndex
        viewIndex = index
        onViewChange?.invoke(views[index], index)
        // Under reduced motion the bars and numbers jump to the new view.
        switchAt = if (still) -1e9 else now()
        switching = !still
    }

    fun setFinger(index: Int, hand: Boolean) {
        if (index == picked) return
        picked = index
        if (index >= 0) {
            if (hand) stepHaptic()
            onSelect?.invoke(index, view.values[index])
        }
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        if (!holding) setFinger(-1, false)
        lastTouch = now()
        touches++
    }

    fun barAt(x: Float, box: StepsBarsToSpikesChart.Box): Int {
        if (box.width <= 0) return -1
        val count = view.values.size
        return floor((x - box.left) / box.width * count).toInt().coerceIn(0, count - 1)
    }

    private suspend fun waitUntil(at: Double) {
        while (now() < at) delay(((at - now()) * 1000).roundToInt().coerceAtLeast(1).toLong())
    }

    suspend fun runSwitch() {
        if (!switching) return
        waitUntil(switchAt + max(Chart.SWITCH_FOR, FADE_IN_DELAY + FADE_IN))
        switching = false
    }

    /** The autoPlay loop presses the switch and drags a finger that is not there. */
    suspend fun autoplay(autoPlay: Boolean) {
        if (!autoPlay) return
        if (touches > 0) {
            val resume = lastTouch + USER_HOLD
            waitUntil(resume)
            if (holding) return
            setFinger(-1, false)
            choose(0, true)
            waitUntil(resume + Chart.SWITCH_FOR + 0.3)
        }
        val events = (SWITCHES.map { (at, view) -> at to { choose(view, true) } } +
            FINGER_STEPS.map { (at, bar) -> at to { setFinger(bar, false) } }).sortedBy { it.first }
        var start = now()
        while (true) {
            for ((at, run) in events) {
                waitUntil(start + at)
                run()
            }
            start += STEPS_BARS_TO_SPIKES_LOOP
            waitUntil(start)
        }
    }
}

private fun style(size: Int, lineHeight: Int, color: Color, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f, tabular: Boolean = false) =
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        fontFeatureSettings = if (tabular) "tnum" else null,
    ).centeredLines()

/**
 * A steps screen: bars on an electric-blue field over a black sheet with the total and three
 * stats. The D / W / M switch sinks the bars and grows the next set in a stagger, from seven soft
 * bars to a month of hairline spikes, while every number rolls to its new value. Dragging over the
 * bars lights the one under the finger and shows its steps.
 *
 * @param autoPlay Switches day, week and month by itself every [STEPS_BARS_TO_SPIKES_LOOP] seconds, with a drag over the month.
 * @param onSelect Called with a bar's index and steps while a finger is on it.
 */
@Composable
fun StepsBarsToSpikes(
    modifier: Modifier = Modifier,
    views: List<StepsBarsToSpikesView> = Figures.VIEWS,
    unitLabel: String = "Steps",
    autoPlay: Boolean = false,
    onViewChange: ((StepsBarsToSpikesView, Int) -> Unit)? = null,
    onSelect: ((Int, Double) -> Unit)? = null,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val state = remember { StepsState() }
    state.views = views
    state.still = still
    state.onViewChange = onViewChange
    state.onSelect = onSelect
    state.stepHaptic = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    state.pressHaptic = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

    // Light status bar icons over the blue field.
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    LaunchedEffect(state.switchAt) { state.runSwitch() }
    LaunchedEffect(state.switching) {
        while (state.switching) withFrameNanos { state.frame = it / 1e9 }
    }
    LaunchedEffect(state.touches, autoPlay) { state.autoplay(autoPlay) }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(modifier.fillMaxSize().background(SHEET)) {
        val fieldHeight = maxHeight * FIELD_SHARE
        Box(
            Modifier
                .fillMaxWidth()
                .height(fieldHeight + SHEET_RADIUS)
                .background(Brush.verticalGradient(listOf(FIELD_TOP, FIELD_BOTTOM))),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .padding(horizontal = MARGIN)
                .padding(top = insets.calculateTopPadding() + 8.dp),
        ) {
            Header(state)
            Field(state, unitLabel, Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp, bottom = 12.dp))
        }
        Sheet(state, unitLabel, still, insets.calculateBottomPadding(), Modifier.padding(top = fieldHeight))
    }
}

@Composable
private fun Header(state: StepsState) {
    val view = state.view
    val change = Figures.round((view.total - view.previous) / max(1.0, view.previous) * 100).toInt()
    val color = if (change < 0) DOWN else UP
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Box {
            // The note of the view switched from fades out as the new one fades in.
            if (state.fromIndex != state.viewIndex) {
                Note(state.views[state.fromIndex], Modifier.graphicsLayer {
                    alpha = (1 - quad(state.since(if (state.switching) state.frame else Double.MAX_VALUE) / FADE_OUT)).toFloat()
                })
            }
            Note(view, Modifier.graphicsLayer {
                alpha = if (state.fromIndex == state.viewIndex) 1f
                else quad(state.since(if (state.switching) state.frame else Double.MAX_VALUE) / FADE_IN).toFloat()
            })
        }
        Row(
            Modifier.height(30.dp).background(CHIP, CircleShape).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (change < 0) Icons.Filled.ArrowCircleDown else Icons.Filled.ArrowCircleUp, null, Modifier.size(13.dp), tint = color)
            BasicText("${abs(change)}%", style = style(11, 14, color, FontWeight.SemiBold), maxLines = 1)
            BasicText("${if (change < 0) "lower" else "higher"} ${view.changeLabel}", style = style(11, 14, INK, FontWeight.Medium), maxLines = 1)
        }
    }
}

@Composable
private fun Note(view: StepsBarsToSpikesView, modifier: Modifier) {
    Column(modifier.padding(start = 30.dp)) {
        Row(Modifier.offset(x = (-20).dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.RemoveCircle, null, Modifier.size(15.dp), tint = FIELD_MUTE)
            BasicText(Figures.grouped(view.note), style = style(17, 22, FIELD_MUTE, FontWeight.SemiBold, tabular = true))
        }
        BasicText(view.noteLabel, style = style(11, 14, FIELD_FAINT))
    }
}

@Composable
private fun Field(state: StepsState, unitLabel: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val axisStyle = style(10, 14, FIELD_MUTE, tabular = true)
    val dayStyle = style(11, 14, FIELD_MUTE).copy(textAlign = TextAlign.Center)
    val badgeStyle = style(12, 16, BADGE, FontWeight.SemiBold, 0.6f)
    val view = state.view
    val kind = when (view.key) { "D" -> "day"; "W" -> "week"; else -> "day of the month" }
    Canvas(
        modifier
            .semantics { contentDescription = "$unitLabel by $kind" }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    state.holding = true
                    state.setFinger(state.barAt(down.position.x / density, state.box), true)
                    state.touched()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) state.setFinger(state.barAt(change.position.x / density, state.box), true)
                        change.consume()
                    } while (change.pressed)
                    state.holding = false
                    state.setFinger(-1, false)
                    state.touched()
                }
            },
    ) {
        val w = size.width / density
        val h = size.height / density
        val box = StepsBarsToSpikesChart.Box(AXIS_WIDTH, max(0f, w - AXIS_WIDTH), h - X_LABELS, h - X_LABELS - 12)
        state.box = box
        val since = state.since(if (state.switching) state.frame else Double.MAX_VALUE)
        val switched = state.fromIndex != state.viewIndex
        scale(density, density, pivot = Offset.Zero) {
            drawChart(box, state.views, state.fromIndex, state.viewIndex, since, state.picked)
        }
        // Labels of the view switched from fade out, the new ones fade in after them.
        fun labels(of: StepsBarsToSpikesView, alpha: Float) {
            if (alpha <= 0f) return
            for (i in 0 until TICKS) {
                val share = (TICKS - i).toFloat() / TICKS
                val layout = measurer.measure(Figures.axisLabel(of.max * share), axisStyle)
                drawText(layout, topLeft = Offset(0f, (box.base - box.height * share) * density - layout.size.height / 2f), alpha = alpha)
            }
            val step = box.width / of.values.size
            for ((i, label) in of.labels.withIndex()) {
                if (label.isEmpty()) continue
                val layout = measurer.measure(label, dayStyle)
                val x = (box.left + step * (i + 0.5f)) * density - layout.size.width / 2f
                drawText(layout, topLeft = Offset(x, (box.base + 6) * density), alpha = alpha)
            }
        }
        if (switched) labels(state.views[state.fromIndex], (1 - quad(since / FADE_OUT)).toFloat())
        labels(view, if (switched) quad((since - FADE_IN_DELAY) / FADE_IN).toFloat() else 1f)
        val badge = view.badge
        if (badge != null) {
            val alpha = when {
                state.viewIndex == 2 -> min(1.0, since / 0.3)
                state.fromIndex == 2 -> max(0.0, 1 - since / 0.2)
                else -> 0.0
            }.toFloat()
            val layout = measurer.measure(badge, badgeStyle)
            drawText(layout, topLeft = Offset(size.width - layout.size.width, 0f), alpha = alpha)
        }
    }
}

@Composable
private fun Sheet(state: StepsState, unitLabel: String, still: Boolean, bottomInset: Dp, modifier: Modifier) {
    val view = state.view
    val picked = state.picked
    val shown = if (picked >= 0 && picked < view.values.size) view.values[picked] else view.total
    val stats = Figures.statsOf(shown)
    val thumb by animateFloatAsState(
        state.viewIndex.toFloat(),
        if (still) snap() else spring(dampingRatio = THUMB_DAMPING_RATIO, stiffness = THUMB_STIFFNESS),
        label = "thumb",
    )
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS))
            .background(SHEET)
            .padding(horizontal = MARGIN)
            .padding(top = 20.dp, bottom = max(bottomInset, 12.dp)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // A generic round monogram standing in for the app's mark.
            Box(Modifier.size(48.dp, 30.dp).border(2.5.dp, INK, CircleShape), contentAlignment = Alignment.Center) {
                BasicText("GO", style = style(14, 18, INK, FontWeight.Black, -0.5f))
            }
            Box(Modifier.background(SWITCH_TRACK, CircleShape).padding(3.dp)) {
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((thumb * KEY.toPx()).roundToInt(), 0) }.size(KEY).background(INK, CircleShape))
                Row {
                    state.views.forEachIndexed { index, item ->
                        val on = index == state.viewIndex
                        Box(
                            Modifier
                                .size(KEY)
                                .semantics {
                                    role = Role.Tab
                                    selected = on
                                }
                                .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    state.touched()
                                    state.choose(index, false)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(item.key, style = style(12, 16, if (on) SHEET else MUTE, if (on) FontWeight.ExtraBold else FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(top = 16.dp)) {
            StepsBarsToSpikesRoller(
                Figures.grouped(shown),
                style(60, 68, INK, FontWeight.ExtraBold, -1.5f, tabular = true),
                68f,
                still,
                Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.linearGradient(NUMBER_TINT, start = Offset.Zero, end = Offset(size.width * 0.4f, size.height)),
                            blendMode = BlendMode.SrcIn,
                        )
                    },
            )
            val name = if (picked >= 0 && picked < view.names.size) "$unitLabel · ${view.names[picked]}" else unitLabel
            BasicText(name, style = style(14, 18, INK, FontWeight.Medium))
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.dp)) {
            Stat(stats.distance, "km", "Distance", still, Modifier.weight(1f))
            Stat(stats.calories, "kcal", "Calories", still, Modifier.weight(1.35f))
            Stat(stats.floors, "", "Floors", still, Modifier.weight(0.65f))
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .background(NAV_TRACK, CircleShape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(NAV_HEIGHT).background(INK, CircleShape).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(18.dp), tint = SHEET)
                BasicText(unitLabel, style = style(14, 18, SHEET, FontWeight.SemiBold))
            }
            NavRound { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp), tint = MUTE) }
            NavRound { Icon(Icons.Sharp.Settings, null, Modifier.size(17.dp), tint = MUTE) }
        }
    }
}

@Composable
private fun NavRound(content: @Composable () -> Unit) {
    Box(Modifier.size(NAV_HEIGHT).background(BUTTON_DIM, CircleShape), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Stat(value: String, unit: String, label: String, still: Boolean, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            StepsBarsToSpikesRoller(value, style(22, 26, INK, FontWeight.Bold, -0.5f, tabular = true), 26f, still)
            if (unit.isNotEmpty()) {
                BasicText(unit, Modifier.padding(start = 2.dp, bottom = 2.dp), style = style(14, 18, INK, FontWeight.Bold))
            }
        }
        BasicText(label, Modifier.padding(top = 2.dp).width(120.dp), style = style(11, 14, MUTE))
    }
}
