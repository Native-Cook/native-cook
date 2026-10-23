package com.ngocdevv.nativecook.blocks.charts.sleepcolumnscrubber

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val SkyTop = Color(0xFF2A0F3A)
private val SkyBottom = Color(0xFF120818)
private val PanelColor = Color(0xFF1D1027)
private val Ink = Color.White
private val Mute = Color(0xFF9C8BA8)
private val Axis = Color(0xFF8A8A8E)
private val Weekend = Color(0xFF5E5068)
private val ButtonColor = Color.White.copy(alpha = 0.08f)
private val TrackColor = Color(0xFF2D1B3C)
private val ThumbColor = Color(0xFF4A3560)
private val Good = Color(0xFF3DDC97)
private val BadgeInk = Color(0xFF111111)
private val TabBarColor = Color(0xFF1A1220)
private val TabActive = Color(0xFFFF2D55)

// Layout, in dp.
private const val MARGIN = 20f
private val NAV = 56.dp
private val ROUND_BUTTON = 40.dp
private val SEGMENT = 36.dp
private const val COLUMN = 14f
// The chart: room above the columns for the floating label, right for the time axis, below for days.
private const val LABEL_SPACE = 44f
private const val AXIS_WIDTH = 50f
private const val DAYS = 46f
private const val DAY_BADGE_W = 28f
private const val DAY_BADGE_H = 38f
private const val TIME_LABEL_W = 104f
private const val TIME_LABEL_H = 22f
private const val TIME_LABEL_GAP = 7f
private val TAB_HEIGHT = 60.dp
// The time axis: 23:30 to 09:00, labelled every two hours.
private const val AXIS_FROM = -30f
private const val AXIS_TO = 540f
private val GRID = listOf(0.0, 120.0, 240.0, 360.0, 480.0)
private val IDEAL_ASLEEP = -15.0 to 45.0
private val USUAL_WAKE = 420.0 to 510.0

// Motion. Springs in React Native's mass, stiffness and damping.
private class SpringParams(val damping: Float, val stiffness: Float, val mass: Float) {
    fun spec(threshold: Float): AnimationSpec<Float> = spring(
        dampingRatio = damping / (2 * sqrt(stiffness * mass)),
        stiffness = stiffness / mass,
        visibilityThreshold = threshold,
    )
}
private val FOLLOW = SpringParams(damping = 22f, stiffness = 300f, mass = 0.6f)
private val PRESS = SpringParams(damping = 18f, stiffness = 260f, mass = 0.6f)
// Milliseconds for a column to take or give up the lift, on Reanimated's default curve.
private const val LIFT_FOR = 160
private val InOutQuad = Easing { if (it < 0.5f) 2 * it * it else 1 - (2 - 2 * it).pow(2) / 2 }

// The autoPlay timeline, in seconds from the start of each loop; the finger's place is in columns.
const val LOOP = 9f
private class Move(val from: Float, val to: Float, val start: Float, val end: Float)
private class Sweep(val down: Float, val up: Float, val moves: List<Move>)
private val SWEEPS = listOf(
    Sweep(0.8f, 4.4f, listOf(Move(13f, 0f, 0.95f, 4.0f))),
    Sweep(4.9f, 8.1f, listOf(Move(11f, 3f, 5.0f, 6.6f), Move(3f, 13f, 6.8f, 7.9f))),
)
// Seconds after the last touch before the chart returns to tonight and the loop starts again.
private const val USER_HOLD = 3f
private const val RESUME_AFTER = 400L

private val MODES = listOf("Duration", "Consistency", "Stages")

/** Smooth start and stop over 0…1. */
private fun glide(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

/** Two lines under each column, weekday and day of the month; [color] null keeps the axis colours. */
@Composable
private fun DayRow(nights: List<SleepNight>, step: Float, color: Color?, modifier: Modifier = Modifier) {
    Row(modifier) {
        for (night in nights) {
            Column(Modifier.width(step.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val style = TextStyle(
                    color = color ?: if (night.weekday == "Su") Weekend else Axis,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                )
                BasicText(night.weekday, style = style)
                BasicText(night.day.toString(), style = style)
            }
        }
    }
}

@Composable
private fun Round(icon: SleepColumnScrubberIcon, size: Float, modifier: Modifier = Modifier) {
    Box(modifier.size(ROUND_BUTTON).background(ButtonColor, CircleShape), contentAlignment = Alignment.Center) {
        SleepColumnScrubberGlyph(icon, size.dp, Ink)
    }
}

@Composable
private fun LegendText(text: String) {
    BasicText(text, style = TextStyle(color = Mute, fontSize = 11.sp, lineHeight = 14.sp))
}

/**
 * A sleep consistency chart: one column per night from falling asleep to waking, striped with its
 * sleep stages. Dragging across the chart widens and rings the night under the finger, dims the
 * others, slides the white day badge to it and moves a label above it whose times roll.
 *
 * @param initialNight The night shown when the screen opens, by its place in [nights]; the last one by default.
 * @param autoPlay Moves a finger over the nights by itself every [LOOP] seconds and comes back to the last one.
 */
@Composable
fun SleepColumnScrubber(
    title: String = "Sleep",
    nights: List<SleepNight> = SleepNights,
    initialNight: Int? = null,
    modes: List<String> = MODES,
    initialMode: Int = 1,
    asleepStatus: String = "STABLE",
    variabilityStatus: String = "NORMAL",
    autoPlay: Boolean = false,
    onSelect: ((SleepNight, Int) -> Unit)? = null,
    onModeChange: ((String, Int) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val home = initialNight ?: (nights.size - 1)
    val currentNights by rememberUpdatedState(nights)
    val selectChanged by rememberUpdatedState(onSelect)

    var mode by remember { mutableIntStateOf(initialMode) }
    var touches by remember { mutableIntStateOf(0) }

    val averageAsleep = mean(nights.map { it.asleep })
    val averageAwake = mean(nights.map { it.awake })
    val variability = floor(spread(nights.map { it.asleep }) + 0.5).toInt()

    // The finger's place in columns, and whether it is down.
    var scrubAt by remember { mutableFloatStateOf(home.toFloat()) }
    var touching by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    var byHand by remember { mutableStateOf(false) }
    // Whether the autoPlay loop is moving the finger.
    var looping by remember { mutableStateOf(false) }

    val last = nights.size - 1
    val selected by remember(last) { derivedStateOf { min(last, max(0, floor(scrubAt + 0.5f).toInt())) } }
    val press = remember { Animatable(0f) }
    val columnX = remember { Animatable(home.toFloat()) }
    val columnTop = remember { Animatable(nights[home].asleep.toFloat()) }
    val lifts = remember { List(nights.size) { Animatable(if (it == home) 1f else 0f) } }

    LaunchedEffect(touching) {
        if (reducedMotion) press.snapTo(touching.toFloat()) else press.animateTo(touching.toFloat(), PRESS.spec(0.001f))
    }

    LaunchedEffect(Unit) {
        var before = selected
        snapshotFlow { selected }.collect { now ->
            if (now == before) return@collect
            before = now
            if (byHand && touching == 1) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            selectChanged?.invoke(currentNights[now], now)
            launch { if (reducedMotion) columnX.snapTo(now.toFloat()) else columnX.animateTo(now.toFloat(), FOLLOW.spec(0.001f)) }
            val top = currentNights[now].asleep.toFloat()
            launch { if (reducedMotion) columnTop.snapTo(top) else columnTop.animateTo(top, FOLLOW.spec(0.01f)) }
            lifts.forEachIndexed { i, lift ->
                val to = if (i == now) 1f else 0f
                if (lift.targetValue != to) launch { lift.animateTo(to, if (reducedMotion) snap() else tween(LIFT_FOR, easing = InOutQuad)) }
            }
        }
    }

    // The autoPlay loop moves a finger that is not there, through the same values a touch sets.
    LaunchedEffect(autoPlay, touches, home) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // A finger still on the chart puts the loop off again when it lifts.
            if (holding) return@LaunchedEffect
            touching = 0
            scrubAt = home.toFloat()
            delay(RESUME_AFTER)
        }
        byHand = false
        looping = true
        var seen = -1f
        val origin = withFrameNanos { it }
        while (looping) {
            val frame = withFrameNanos { it }
            if (!looping) break
            val t = ((frame - origin) / 1e9f) % LOOP
            val was = if (seen > t) -1f else seen
            seen = t
            for (sweep in SWEEPS) {
                if (was < sweep.down && t >= sweep.down) {
                    scrubAt = sweep.moves[0].from
                    touching = 1
                }
                sweep.moves.forEachIndexed { j, move ->
                    if (t >= move.start && t <= sweep.up && (j + 1 == sweep.moves.size || t < sweep.moves[j + 1].start)) {
                        scrubAt = move.from + (move.to - move.from) * glide((t - move.start) / (move.end - move.start))
                    }
                }
                if (was < sweep.up && t >= sweep.up) touching = 0
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))) {
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())) {
            // Navigation bar.
            Box(Modifier.fillMaxWidth().height(NAV).padding(horizontal = 16.dp)) {
                BasicText(
                    title,
                    Modifier.align(Alignment.Center),
                    style = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                )
                Round(
                    SleepColumnScrubberIcon.Back,
                    20f,
                    Modifier
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Back", role = Role.Button) { onBack?.invoke() },
                )
                Row(Modifier.align(Alignment.CenterEnd).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Round(SleepColumnScrubberIcon.Calendar, 18f)
                    Round(SleepColumnScrubberIcon.Options, 20f)
                }
            }

            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxSize()
                    .background(PanelColor, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(top = 20.dp, start = MARGIN.dp, end = MARGIN.dp),
            ) {
                BasicText("Overview", style = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))

                // Mode switch.
                BoxWithConstraints(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(SEGMENT)
                        .background(TrackColor, CircleShape)
                        .padding(3.dp),
                ) {
                    val width = maxWidth / modes.size
                    val thumb by animateFloatAsState(
                        mode.toFloat(),
                        if (reducedMotion) snap() else PRESS.spec(0.001f),
                        label = "thumb",
                    )
                    Box(
                        Modifier
                            .offset { IntOffset((thumb * width.toPx()).roundToInt(), 0) }
                            .width(width)
                            .fillMaxHeight()
                            .background(ThumbColor, CircleShape),
                    )
                    Row(Modifier.fillMaxSize()) {
                        modes.forEachIndexed { index, label ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .semantics { this.selected = index == mode }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab,
                                    ) {
                                        mode = index
                                        onModeChange?.invoke(label, index)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    label,
                                    style = TextStyle(
                                        color = if (index == mode) Ink else Mute,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                        fontWeight = if (index == mode) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Averages.
                Row(Modifier.padding(top = 18.dp)) {
                    val asleepValue = buildAnnotatedString { append(clock(averageAsleep)) }
                    val variabilityValue = buildAnnotatedString {
                        append(variability.toString())
                        withStyle(SpanStyle(color = Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium)) { append("m") }
                    }
                    for ((caption, value, status) in listOf(
                        Triple("Avg. fall asleep", asleepValue, asleepStatus),
                        Triple("Fall asleep variability", variabilityValue, variabilityStatus),
                    )) {
                        Column(Modifier.weight(1f)) {
                            BasicText(caption, style = TextStyle(color = Mute, fontSize = 12.sp, lineHeight = 16.sp))
                            val big = TextStyle(color = Ink, fontFamily = FontFamily.Monospace, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                            BasicText(value, Modifier.padding(top = 2.dp), style = big)
                            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(14.dp).background(Good, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                    SleepColumnScrubberGlyph(SleepColumnScrubberIcon.Check, 11.dp, BadgeInk)
                                }
                                BasicText(
                                    status,
                                    style = TextStyle(color = Ink, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                                )
                            }
                        }
                    }
                }

                // Chart.
                BoxWithConstraints(Modifier.padding(top = 4.dp).fillMaxWidth().weight(1f)) {
                    val width = maxWidth.value
                    val plotWidth = max(0f, width - AXIS_WIDTH)
                    val step = if (nights.isEmpty()) 0f else plotWidth / nights.size
                    val frame = remember(width, maxHeight, step) {
                        ChartFrame(
                            width = width,
                            top = LABEL_SPACE,
                            bottom = max(LABEL_SPACE, maxHeight.value - DAYS - 8),
                            from = AXIS_FROM,
                            to = AXIS_TO,
                            first = step / 2,
                            step = step,
                            column = min(COLUMN, step * 0.65f),
                            plotWidth = plotWidth,
                        )
                    }
                    val bands = Bands(IDEAL_ASLEEP, USUAL_WAKE, GRID, averageAsleep, averageAwake)
                    val drawing = remember(frame, nights) { SleepColumns(frame, nights, bands) }
                    val currentFrame by rememberUpdatedState(frame)
                    val night = nights[selected]
                    fun x() = frame.first + frame.step * columnX.value
                    fun top() = frame.y(columnTop.value.toDouble())

                    Box(
                        Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "${night.weekday} ${night.day}, ${clock(night.asleep)} to ${clock(night.awake)}" }
                            .pointerInput(Unit) {
                                fun columnAt(x: Float): Float {
                                    val f = currentFrame
                                    return if (f.step > 0) (x.toDp().value - f.first) / f.step else 0f
                                }
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    // The loop stops here, before the finger's first move.
                                    looping = false
                                    byHand = true
                                    holding = true
                                    touching = 1
                                    scrubAt = columnAt(down.position.x)
                                    touches++
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            scrubAt = columnAt(change.position.x)
                                            change.consume()
                                        }
                                    } finally {
                                        holding = false
                                        touching = 0
                                        touches++
                                    }
                                }
                            },
                    ) {
                        Canvas(Modifier.size(width.dp, (frame.bottom + 24).dp)) {
                            scale(density.density, density.density, pivot = Offset.Zero) {
                                with(drawing) { draw({ lifts[it].value }, press.value, reducedMotion) }
                            }
                        }
                        val labels = GRID.filter { abs(it - averageAsleep) > 22 && abs(it - averageAwake) > 22 }.map { it to false } +
                            listOf(averageAsleep to true, averageAwake to true)
                        for ((minutes, strong) in labels) {
                            BasicText(
                                clock(minutes),
                                Modifier.offset((plotWidth + 6).dp, (frame.y(minutes) - 7).dp),
                                style = TextStyle(
                                    color = if (strong) Ink else Axis,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                        }
                        DayRow(nights, step, null, Modifier.offset(y = (frame.bottom + 10).dp))
                        // The white badge: a window onto a black copy of the days, slid under the column.
                        Box(
                            Modifier
                                .offset { IntOffset(((x() - DAY_BADGE_W / 2) * density.density).roundToInt(), ((frame.bottom + 10 - 3) * density.density).roundToInt()) }
                                .size(DAY_BADGE_W.dp, DAY_BADGE_H.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ink),
                        ) {
                            DayRow(
                                nights,
                                step,
                                BadgeInk,
                                Modifier
                                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                                    .offset { IntOffset(((DAY_BADGE_W / 2 - x()) * density.density).roundToInt(), (3 * density.density).roundToInt()) },
                            )
                        }
                        Box(
                            Modifier
                                .offset { IntOffset(((x() - 0.5f) * density.density).roundToInt(), ((top() - TIME_LABEL_GAP) * density.density).roundToInt()) }
                                .size(1.dp, TIME_LABEL_GAP.dp)
                                .background(Ink),
                        )
                        // The label stays on screen; its stem stays on the column.
                        Box(
                            Modifier
                                .offset {
                                    val labelX = min(width + MARGIN - 8 - TIME_LABEL_W, max(8 - MARGIN, x() - TIME_LABEL_W / 2))
                                    IntOffset((labelX * density.density).roundToInt(), ((top() - TIME_LABEL_GAP - TIME_LABEL_H) * density.density).roundToInt())
                                }
                                .size(TIME_LABEL_W.dp, TIME_LABEL_H.dp)
                                .dropShadow(CircleShape, Shadow(radius = 12.dp, color = Color.Black, offset = DpOffset(0.dp, 4.dp), alpha = 0.35f))
                                .background(Ink, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            SleepColumnScrubberRoller(
                                "${clock(night.asleep)}—${clock(night.awake)}",
                                TextStyle(color = BadgeInk, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                                16.dp,
                                reducedMotion,
                            )
                        }
                    }
                }

                // Legends.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(12.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(3.dp)))
                        LegendText("Ideal fall asleep")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Canvas(Modifier.size(12.dp)) {
                            val inset = 0.5.dp.toPx()
                            drawRoundRect(
                                Color.White.copy(alpha = 0.35f),
                                topLeft = Offset(inset, inset),
                                size = size.copy(width = size.width - inset * 2, height = size.height - inset * 2),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
                            )
                        }
                        LegendText("Usual wake-up")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(12.dp, 1.5.dp).background(Ink))
                        LegendText("Avg. fall asleep")
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (stage in SleepStage.entries) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(stage.color, CircleShape))
                            LegendText(stage.label)
                        }
                    }
                }

                // Tab bar.
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp, bottom = max(insets.calculateBottomPadding().value, 12f).dp)
                        .background(TabBarColor, CircleShape)
                        .border(with(density) { 1f.toDp() }, Color.White.copy(alpha = 0.08f), CircleShape)
                        .padding(4.dp)
                        .clearAndSetSemantics {},
                ) {
                    for ((icon, label, on) in listOf(
                        Triple(SleepColumnScrubberIcon.Today, "Today", true),
                        Triple(SleepColumnScrubberIcon.Trending, "Progress", false),
                        Triple(SleepColumnScrubberIcon.Barbell, "Workouts", false),
                    )) {
                        Column(
                            Modifier
                                .size(92.dp, TAB_HEIGHT)
                                .background(if (on) Color.White.copy(alpha = 0.07f) else Color.Transparent, CircleShape),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                        ) {
                            val color = if (on) TabActive else Ink
                            SleepColumnScrubberGlyph(icon, 20.dp, color)
                            BasicText(label, style = TextStyle(color = color, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}
