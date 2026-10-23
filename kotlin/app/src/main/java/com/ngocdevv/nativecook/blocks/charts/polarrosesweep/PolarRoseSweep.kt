package com.ngocdevv.nativecook.blocks.charts.polarrosesweep

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class PolarRoseSweepRound(
    /** The toggle's label. */
    val name: String,
    /** Per idea, in percent of the people asked; what is left over did not answer. */
    val likely: List<Double>,
    val unsure: List<Double>,
    val unlikely: List<Double>,
)

val PolarRoseSweepSamples = listOf(
    PolarRoseSweepRound(
        "March",
        listOf(77.0, 71.0, 67.0, 56.0, 54.0, 46.0, 46.0, 46.0, 46.0, 31.0),
        listOf(13.0, 14.0, 10.0, 16.0, 12.0, 18.0, 14.0, 20.0, 12.0, 22.0),
        listOf(8.0, 9.0, 15.0, 18.0, 22.0, 20.0, 26.0, 18.0, 30.0, 28.0),
    ),
    PolarRoseSweepRound(
        "June",
        listOf(82.0, 64.0, 72.0, 49.0, 61.0, 58.0, 40.0, 52.0, 38.0, 44.0),
        listOf(10.0, 17.0, 11.0, 20.0, 13.0, 14.0, 18.0, 16.0, 15.0, 19.0),
        listOf(6.0, 12.0, 12.0, 22.0, 18.0, 20.0, 28.0, 22.0, 30.0, 26.0),
    ),
)

private val IDEAS = listOf(
    "Layout refiner", "Sketch converter", "Prompt builder", "Search assistant", "Anomaly detector",
    "Group finder", "Price predictor", "Diagram seeker", "Graph copilot", "Link analyst",
)

private object K {
    val CENTRE = Color(0xFF3A1A57)
    val EDGE = Color(0xFF0E0516)
    val TITLE = Color(0xFFF1D9FF)
    val SOFT = Color(0xFFA57BC0)
    const val LABEL = 0xB58AD1
    const val GRID = 0x5C3380
    // The inner sector's colour follows its value: dark plum for the least likely, lavender for the most.
    val LIKELY_STOPS = listOf(30.0 to 0x6F4687, 45.0 to 0x9568AB, 65.0 to 0xCF97E2, 80.0 to 0xEEB3F7)
    const val UNSURE = 0x6B2BBF
    const val UNLIKELY = 0x2B1345
    const val NUMBER = 0x3B1C57
    const val NUMBER_SELECTED = 0x111111
    const val UNSURE_NUMBER = 0xE3C4F7
    const val WHITE = 0xFFFFFF
    val LEGEND = listOf(Color(0xFFEEB3F7), Color(0xFF7B2FD2), Color(0xFF2B1345))
    val LEGEND_RING = Color(0xFF5C3380)
    val PILL = Color(26 / 255f, 10 / 255f, 40 / 255f, 0.8f)
    val THUMB = Color(241 / 255f, 217 / 255f, 1f, 0.16f)

    // The chart. Radius is linear in percent (not area): 100 % is the outer ring.
    const val MAX_RADIUS = 170f
    const val LABEL_ROOM = 32f
    const val LABEL_GAP = 15f
    val RINGS = floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f, 1f)
    const val GRID_WIDTH = 0.75f
    const val GRID_ALPHA = 0.55
    // The selected wedge pushes out and brightens; the others dim a little.
    const val PUSH = 10.0
    const val BRIGHTEN = 0.14
    const val DIM = 0.78
    const val NUMBER_SIZE = 15f
    const val NUMBER_SELECTED_SIZE = 30f
    const val NUMBER_AT = 0.66
    const val NUMBER_OUT = 0.1
    const val UNSURE_SIZE = 14f
    const val LABEL_SIZE = 12f
    const val TAP_ROOM = 36f

    // Seconds. A spring step (damping ratio and angular frequency) for pushes and re-flows.
    const val ZETA = 0.62
    const val OMEGA = 12.0
    const val SETTLED = 0.9
    // A radar line sweeps clockwise; each wedge re-flows to the new round as it passes.
    const val SWEEP_FOR = 1.5
    const val TRAIL = 32f
    const val TRAIL_STEPS = 12
    const val TRAIL_ALPHA = 0.16
    const val REDUCED_FADE = 0.18
    const val TOGGLE_FOR = 0.24
    // CSS's "ease", which React Native's colour transition uses.
    val COLOR_EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    // The autoPlay timeline, in seconds from the start of each loop.
    const val LOOP = 10.0
    val TOUR = listOf(0.8 to 1, 1.6 to 2, 2.4 to 3, 5.6 to 0)
    const val NEXT_ROUND_AT = 3.4
    const val BACK_AT = 6.4
    const val USER_HOLD = 3.0
    const val SETTLE = 2.0

    fun clamp(value: Double) = value.coerceIn(0.0, 1.0)

    /** A damped spring's step response at [t] seconds: 0 before, about 1 once settled. */
    fun spring(t: Double): Double {
        if (t <= 0) return 0.0
        if (t >= SETTLED * 2) return 1.0
        val decay = ZETA * OMEGA
        val damped = OMEGA * sqrt(1 - ZETA * ZETA)
        return 1 - exp(-decay * t) * (cos(damped * t) + (decay / damped) * sin(damped * t))
    }

    fun channels(hex: Int) = doubleArrayOf(((hex shr 16) and 0xFF).toDouble(), ((hex shr 8) and 0xFF).toDouble(), (hex and 0xFF).toDouble())

    /** A colour as 0xAARRGGBB, mixed toward white by [lighten], with [opacity]; a spring overshoots past 1, so both stop at the ends. */
    fun argb(c: DoubleArray, lighten: Double, opacity: Double): Int {
        val light = clamp(lighten)
        val r = (c[0] + (255 - c[0]) * light).roundToInt()
        val g = (c[1] + (255 - c[1]) * light).roundToInt()
        val b = (c[2] + (255 - c[2]) * light).roundToInt()
        return ((clamp(opacity) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun mix(a: DoubleArray, b: DoubleArray, u: Double) = DoubleArray(3) { a[it] + (b[it] - a[it]) * u }
}

/** What is selected and shown, and when each last changed, in seconds on the block's clock. */
private data class Run(
    val selected: Int = 0,
    val previous: Int = -1,
    val selectedAt: Double = -100.0,
    val round: Int = 0,
    val fromRound: Int = 0,
    val sweptAt: Double = -100.0,
    val toggleFrom: Double = 0.0,
    val toggleAt: Double = -100.0,
    val busyUntil: Double = 0.0,
) {
    /** The toggle's thumb position, gliding between rounds with an ease-out cubic. */
    fun toggle(t: Double, still: Boolean): Double {
        if (still) return round.toDouble()
        val u = 1 - (1 - K.clamp((t - toggleAt) / K.TOGGLE_FOR)).pow(3)
        return toggleFrom + (round - toggleFrom) * u
    }
}

/** Paints and a path made once and reused every frame. */
private class Paints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val number = textPaint(K.NUMBER_SIZE, 700)
    val unsure = textPaint(K.UNSURE_SIZE, 600)
    val label = textPaint(K.LABEL_SIZE, 400)
    val path = android.graphics.Path()
    val oval = RectF()

    private fun textPaint(size: Float, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else if (weight >= 600) {
            Typeface.DEFAULT_BOLD
        } else {
            Typeface.DEFAULT
        }
    }

    /** An annular sector between two radii and two angles (degrees, clockwise from +x). */
    fun sector(cx: Float, cy: Float, r0: Float, r1: Float, a0: Float, sweep: Float): android.graphics.Path {
        path.rewind()
        if (r1 <= r0) return path
        oval.set(cx - r1, cy - r1, cx + r1, cy + r1)
        path.arcTo(oval, a0, sweep, true)
        if (r0 < 0.5f) {
            path.lineTo(cx, cy)
        } else {
            oval.set(cx - r0, cy - r0, cx + r0, cy + r0)
            path.arcTo(oval, a0 + sweep, -sweep, false)
        }
        path.close()
        return path
    }
}

/**
 * A survey as a polar-area chart: one wedge per idea, each three stacked sectors (likely, hard to
 * say, not likely) whose radii read in percent. A touch selects the wedge under it, which pushes out
 * on a spring; the round toggle sends a radar line round the chart, each wedge re-flowing to the
 * new answers as the line passes while its number rolls.
 */
@Composable
fun PolarRoseSweep(
    modifier: Modifier = Modifier,
    title: String = "How likely are you to use it?",
    caption: String? = null,
    /** One wedge per idea, clockwise from the top. */
    ideas: List<String> = IDEAS,
    /** Two or more survey rounds over the same ideas; the toggle switches between them. */
    rounds: List<PolarRoseSweepRound> = PolarRoseSweepSamples,
    legend: List<String> = listOf("Likely", "Hard to say", "Not likely"),
    /** Tours the wedges and sweeps to the next round and back every LOOP seconds. */
    autoPlay: Boolean = false,
    onSelect: ((Int) -> Unit)? = null,
    onRoundChange: ((Int) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val birth = remember { System.nanoTime() }
    val paints = remember { Paints() }
    var run by remember { mutableStateOf(Run()) }
    var dragging by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableDoubleStateOf(0.0) }
    var chartBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val toggleWidths = remember { mutableStateListOf<Float>() }
    val time = remember { mutableDoubleStateOf(0.0) }
    // Read by the actions, which the loop and the gestures call long after the first composition.
    val still by rememberUpdatedState(reduced)
    val count by rememberUpdatedState(ideas.size)
    val roundCount by rememberUpdatedState(rounds.size)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnRoundChange by rememberUpdatedState(onRoundChange)

    // Cumulative percent per round and wedge: the outer edge of each of the three sectors.
    val table = remember(rounds, ideas) {
        rounds.map { r ->
            List(ideas.size) { i ->
                val a = r.likely.getOrElse(i) { 0.0 }
                val b = a + r.unsure.getOrElse(i) { 0.0 }
                doubleArrayOf(a, b, b + r.unlikely.getOrElse(i) { 0.0 })
            }
        }
    }
    val labelWidths = remember(ideas) { ideas.map { paints.label.measureText(it) } }

    fun clock() = (System.nanoTime() - birth) / 1e9

    fun select(index: Int, byHand: Boolean, t: Double) {
        val r = run
        if (index == r.selected || index < 0 || index >= count) return
        run = r.copy(previous = r.selected, selected = index, selectedAt = t, busyUntil = max(r.busyUntil, t + K.SETTLED * 2))
        if (byHand) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            currentOnSelect?.invoke(index)
        }
    }

    fun switchRound(index: Int, byHand: Boolean, t: Double) {
        val r = run
        if (index == r.round) return
        run = r.copy(
            toggleFrom = r.toggle(t, still), toggleAt = t, fromRound = r.round, round = index, sweptAt = t,
            busyUntil = max(r.busyUntil, t + K.SWEEP_FOR + K.SETTLED * 2),
        )
        if (byHand) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            currentOnRoundChange?.invoke(index)
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
    LaunchedEffect(run.busyUntil) {
        while (clock() <= run.busyUntil) withFrameNanos { time.doubleValue = (it - birth) / 1e9 }
        time.doubleValue = clock()
    }

    // Each step is stamped with its time on the loop, not the time the coroutine woke, so every loop is identical.
    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            waitUntil(lastTouch + K.USER_HOLD)
            if (dragging) return@LaunchedEffect
        }
        val now = clock()
        val atRest = run.round == 0 && run.selected == 0
        switchRound(0, false, now)
        select(0, false, now)
        var start = now + if (atRest) 0.0 else K.SETTLE
        // Wedges first, then rounds, in the order of their times.
        val steps = (K.TOUR.map { Triple(it.first, it.second % count, false) } +
            listOf(Triple(K.NEXT_ROUND_AT, 1 % roundCount, true), Triple(K.BACK_AT, 0, true)))
            .sortedBy { it.first }
        while (true) {
            for ((at, index, isRound) in steps) {
                waitUntil(start + at)
                if (isRound) switchRound(index, false, start + at) else select(index, false, start + at)
            }
            start += K.LOOP
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(K.EDGE)
                drawRect(
                    Brush.radialGradient(
                        listOf(K.CENTRE, K.EDGE),
                        center = chartBounds.center,
                        radius = max(1f, max(chartBounds.width, chartBounds.height) * 0.85f),
                    ),
                )
            }
            .padding(top = insets.calculateTopPadding()),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 16.dp)) {
            BasicText(
                title,
                style = TextStyle(color = K.TITLE, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
            BasicText(
                caption ?: "Survey, ${ideas.size} ideas",
                Modifier.padding(top = 4.dp),
                style = TextStyle(color = K.SOFT, fontSize = 13.sp, lineHeight = 18.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .background(K.PILL, RoundedCornerShape(18.dp))
                    .padding(3.dp),
            ) {
                // The thumb: under one label, or between two while it glides.
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            if (toggleWidths.size < rounds.size) return@drawBehind
                            val v = run.toggle(time.doubleValue, still)
                            val i = min(floor(v).toInt(), toggleWidths.size - 1)
                            val j = min(i + 1, toggleWidths.size - 1)
                            val u = (v - floor(v)).toFloat()
                            var left = 0f
                            for (k in 0 until i) left += toggleWidths[k]
                            val width = toggleWidths[i] + (toggleWidths[j] - toggleWidths[i]) * u
                            drawRoundRect(K.THUMB, Offset(left + toggleWidths[i] * u, 0f), Size(width, size.height), CornerRadius(15.dp.toPx()))
                        },
                )
                Row {
                    rounds.forEachIndexed { index, round ->
                        val color by animateColorAsState(
                            if (index == run.round) K.TITLE else K.SOFT,
                            tween((K.TOGGLE_FOR * 1000).toInt(), easing = K.COLOR_EASE),
                            label = "toggle",
                        )
                        Box(
                            Modifier
                                .height(30.dp)
                                .onGloballyPositioned { coordinates ->
                                    while (toggleWidths.size <= index) toggleWidths.add(0f)
                                    toggleWidths[index] = coordinates.size.width.toFloat()
                                }
                                .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                                    touch()
                                    switchRound(index, true, clock())
                                }
                                .semantics { selected = index == run.round }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                round.name,
                                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                color = { color },
                            )
                        }
                    }
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { chartBounds = it.boundsInRoot() }
                .clearAndSetSemantics {
                    contentDescription = "Survey chart"
                    stateDescription = "${ideas[run.selected]}: ${rounds[run.round].likely.getOrElse(run.selected) { 0.0 }.roundToInt()}%"
                    progressBarRangeInfo = ProgressBarRangeInfo(run.selected.toFloat(), 0f..(ideas.size - 1f), max(0, ideas.size - 2))
                    setProgress { target ->
                        touch()
                        select(target.roundToInt().coerceIn(0, count - 1), true, clock())
                        true
                    }
                },
        ) {
            val width = maxWidth.value
            val height = maxHeight.value
            val radius = max(60f, min(K.MAX_RADIUS, min(width / 2 - K.LABEL_ROOM, height / 2 - K.LABEL_ROOM)))
            val cx = width / 2
            val cy = height / 2

            // The wedge under a point of the chart, or -1 away from it.
            fun wedgeAt(x: Float, y: Float): Int {
                val dx = x - cx
                val dy = y - cy
                if (hypot(dx, dy) > radius + K.TAP_ROOM) return -1
                val degrees = Math.toDegrees(atan2(dy, dx).toDouble()) + 90
                return floor(((degrees % 360) + 360) % 360 / (360.0 / count)).toInt() % count
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(radius, cx, cy) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            dragging = true
                            touch()
                            select(wedgeAt(down.position.x / density, down.position.y / density), true, clock())
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                touch()
                                select(wedgeAt(change.position.x / density, change.position.y / density), true, clock())
                            }
                            dragging = false
                            touch()
                        }
                    },
            ) {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { drawRose(it.nativeCanvas, cx, cy, radius, time.doubleValue, run, table, ideas, labelWidths, still, paints) }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                // At least 16 dp below, more where the system bars need it, then 8 more.
                .padding(bottom = (max(insets.calculateBottomPadding().value, 16f) + 8).dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .height(36.dp)
                    .background(K.PILL, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                legend.forEachIndexed { i, name ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(K.LEGEND[i], CircleShape)
                                .then(if (i == 2) Modifier.border(1.dp, K.LEGEND_RING, CircleShape) else Modifier),
                        )
                        BasicText(
                            name,
                            style = TextStyle(color = K.TITLE, fontSize = 12.sp, lineHeight = 16.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        )
                    }
                }
            }
        }
    }
}

private fun drawRose(
    canvas: android.graphics.Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    now: Double,
    run: Run,
    table: List<List<DoubleArray>>,
    ideas: List<String>,
    labelWidths: List<Float>,
    still: Boolean,
    paints: Paints,
) {
    val count = ideas.size
    val step = 360f / count
    val unit = radius / 100
    val current = run.selected
    val before = run.previous
    val pushed = if (still) K.clamp((now - run.selectedAt) / K.REDUCED_FADE) else K.spring(now - run.selectedAt)
    val target = table[run.round]
    val source = table[run.fromRound]
    val since = now - run.sweptAt
    val fill = paints.fill
    val stops = K.LIKELY_STOPS.map { it.first to K.channels(it.second) }

    // Wedges, the selected one last so its push is drawn over its neighbours.
    fun drawWedge(i: Int) {
        val flow = if (still) K.clamp((since - K.SWEEP_FOR / 2) / K.REDUCED_FADE) else K.spring(since - i.toDouble() / count * K.SWEEP_FOR)
        val value = DoubleArray(3) { source[i][it] + (target[i][it] - source[i][it]) * flow }
        val pop = if (i == current) pushed else if (i == before) 1 - pushed else 0.0
        val a0 = -90f + i * step
        val middle = Math.toRadians((a0 + step / 2).toDouble())
        val push = if (still) 0.0 else K.PUSH * pop
        val x = (cx + cos(middle) * push).toFloat()
        val y = (cy + sin(middle) * push).toFloat()
        // The push may overshoot; colours and sizes follow the settled amount.
        val tone = K.clamp(pop)
        val light = K.BRIGHTEN * tone
        val alpha = K.DIM + (1 - K.DIM) * tone

        // The inner sector's colour from its value along the stops.
        var s = 0
        while (s < stops.size - 2 && value[0] > stops[s + 1].first) s++
        val u = K.clamp((value[0] - stops[s].first) / (stops[s + 1].first - stops[s].first))
        val inner = K.mix(stops[s].second, stops[s + 1].second, u)

        fill.color = K.argb(K.channels(K.UNLIKELY), light, alpha)
        canvas.drawPath(paints.sector(x, y, (value[1] * unit).toFloat(), (value[2] * unit).toFloat(), a0, step), fill)
        fill.color = K.argb(K.channels(K.UNSURE), light, alpha)
        canvas.drawPath(paints.sector(x, y, (value[0] * unit).toFloat(), (value[1] * unit).toFloat(), a0, step), fill)
        fill.color = K.argb(inner, light, alpha)
        canvas.drawPath(paints.sector(x, y, 0f, (value[0] * unit).toFloat(), a0, step), fill)

        // The likely share inside the inner sector, larger on the selected wedge; the larger number
        // moves out to where the wedge is wide enough for it.
        val text = "${value[0].roundToInt()}%"
        val scale = (1 + (K.NUMBER_SELECTED_SIZE / K.NUMBER_SIZE - 1) * tone).toFloat()
        val width = paints.number.measureText(text)
        val at = value[0] * unit * (K.NUMBER_AT + K.NUMBER_OUT * tone)
        paints.number.color = K.argb(K.mix(K.channels(K.NUMBER), K.channels(K.NUMBER_SELECTED), tone), 0.0, 0.82 + 0.18 * tone)
        canvas.save()
        canvas.translate((x + cos(middle) * at).toFloat(), (y + sin(middle) * at).toFloat())
        canvas.scale(scale, scale)
        canvas.drawText(text, -width / 2, K.NUMBER_SIZE * 0.36f, paints.number)
        canvas.restore()

        // On the selected wedge, the "hard to say" share sits in its band.
        if (tone > 0.02 && value[1] - value[0] >= 6) {
            val band = "${(value[1] - value[0]).roundToInt()}%"
            val bandAt = (value[0] + value[1]) / 2 * unit
            paints.unsure.color = K.argb(K.channels(K.UNSURE_NUMBER), 0.0, tone)
            canvas.drawText(
                band,
                (x + cos(middle) * bandAt).toFloat() - paints.unsure.measureText(band) / 2,
                (y + sin(middle) * bandAt).toFloat() + 5,
                paints.unsure,
            )
        }
    }
    for (i in 0 until count) if (i != current && i != before) drawWedge(i)
    if (before >= 0 && before != current) drawWedge(before)
    drawWedge(current)

    // Reference rings and spokes over the wedges, faint.
    val stroke = paints.stroke
    stroke.strokeWidth = K.GRID_WIDTH
    stroke.strokeCap = Paint.Cap.BUTT
    stroke.color = K.argb(K.channels(K.GRID), 0.0, K.GRID_ALPHA)
    for (ring in K.RINGS) canvas.drawCircle(cx, cy, ring * radius, stroke)
    for (i in 0 until count) {
        val a = Math.toRadians((-90.0 + i * step))
        canvas.drawLine(cx, cy, cx + (cos(a) * radius).toFloat(), cy + (sin(a) * radius).toFloat(), stroke)
    }

    // The radar line and its fading trail while a new round sweeps in.
    if (!still && since >= 0 && since <= K.SWEEP_FOR) {
        val p = since / K.SWEEP_FOR
        val fade = K.clamp(p / 0.08) * K.clamp((1 - p) / 0.08)
        val head = (-90 + 360 * p).toFloat()
        val slice = K.TRAIL / K.TRAIL_STEPS
        val white = K.channels(K.WHITE)
        for (k in 0 until K.TRAIL_STEPS) {
            fill.color = K.argb(white, 0.0, K.TRAIL_ALPHA * fade * (1 - k.toDouble() / K.TRAIL_STEPS))
            canvas.drawPath(paints.sector(cx, cy, 0f, radius, head - (k + 1) * slice, slice), fill)
        }
        stroke.strokeWidth = 1.5f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = K.argb(white, 0.0, 0.9 * fade)
        val a = Math.toRadians(head.toDouble())
        canvas.drawLine(cx, cy, cx + (cos(a) * (radius + 6)).toFloat(), cy + (sin(a) * (radius + 6)).toFloat(), stroke)
    }

    // Idea names along the circle, turned so that none reads upside down.
    for (i in 0 until count) {
        val pop = if (i == current) pushed else if (i == before) 1 - pushed else 0.0
        val middle = -90f + (i + 0.5f) * step
        val a = Math.toRadians(middle.toDouble())
        val r = radius + K.LABEL_GAP + (if (still) 0.0 else K.PUSH * pop).toFloat()
        val lower = sin(a) > 0.01
        paints.label.color = K.argb(K.channels(K.LABEL), pop, 1.0)
        canvas.save()
        canvas.translate(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat())
        canvas.rotate(if (lower) middle - 90 else middle + 90)
        canvas.drawText(ideas[i], -labelWidths[i] / 2, K.LABEL_SIZE * 0.36f, paints.label)
        canvas.restore()
    }
}
