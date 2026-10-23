package com.ngocdevv.nativecook.blocks.navigation.roadmappathstepper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class RoadmapStep(val title: String, val description: String)

val SampleRoadmapSteps = listOf(
    RoadmapStep("You are here", "You've made the greatest decision of your life to use Passa"),
    RoadmapStep("Submit docs on Passa", "Plug your information into free templates!"),
    RoadmapStep("Passa reviews docs", "We'll ensure your application is error free"),
    RoadmapStep("We submit to Govt.", "The embassy reviews your application"),
    RoadmapStep("Track trip status", "We'll always update you on your trip"),
    RoadmapStep("Get there on time!", "You're ready to start packing for your trip!"),
)

private val Screen = Color.White
private val Accent = Color(0xFF413EF0)
private val Ink = Color(0xFF1F1F24)
private val Mute = Color(0xFF6B6B76)
private val Track = Color(0xFFE1E1E1)
private val Outline = Color(0xFFD5D5DC)

// The top bar, below the safe area.
private const val SIDE = 20.0
private const val BAR_TOP = 11.0
private const val BAR = 32.0
private const val ROUND = 26.0

// The stage holds the heading, the path and the list. Everything below is measured from its top
// left corner, which sits this far under the safe area.
private const val STAGE_TOP = 59.0
private const val HEADING_X = 86.0

// The path: from the pin beside the heading's first line, left, down, right, and down the rail.
private const val PIN_X = 72.0
private const val PIN_Y = 17.0
private const val BRACKET_X = 31.0
private const val BRACKET_DROP = 68.0
private const val CORNER = 10.0
private const val RAIL_X = 98.0
private const val STROKE = 2.5
private const val RAIL = 2.0

// The list scrolls under this line; a step's node sits NODE_INSET under the top of its row.
private const val LIST_TOP = 113.0
private const val NODE_INSET = 16.0
private const val ROW = 120.0

// What the last step needs under its node: three lines of description and some air.
private const val TAIL = 96.0
private const val NUMBER_X = 63.0
private const val TEXT_X = 129.0
private const val TEXT_WIDTH = 220.0
private const val NODE = 16.0
private const val HOLLOW = 14.0
private const val DOT = 5.0

// The pin is a node with one corner left sharp, turned to point down, and a larger dot.
private const val PIN_TIP = 2.0
private const val PIN_DOT = 6.0
private const val BUTTON = 44.0
private const val PANEL_PAD = 12.0
private const val BUTTON_LIFT = 8.0

private const val RUN_A = PIN_X - BRACKET_X - CORNER
private const val RUN_B = BRACKET_DROP - CORNER * 2
private const val RUN_C = RAIL_X - BRACKET_X - CORNER * 2
private const val TURN = PI * CORNER / 2

// The length of the path down to the list's top edge, and to the first node.
private const val PATH_TO_LIST = RUN_A + RUN_B + RUN_C + TURN * 3 + (LIST_TOP - PIN_Y - BRACKET_DROP - CORNER)
private const val PATH_TO_NODE = PATH_TO_LIST + NODE_INSET

// The path is drawn this much longer than the list, for a pull past the top.
private const val OVERSCROLL = 240.0

/** Seconds. The loop rests on the finished screen, clears it, and plays the entrance again. */
const val LOOP = 10.0
private const val PLAY_AT = 1.5

// Before the entrance the finished screen fades to white, then stays empty for a beat.
private const val OUT = 0.45
private const val GAP = 0.1

// The entrance, from the moment the screen is empty: the heading and the first step fade in at
// mid-screen while the pin pops and the path draws itself; the pin rides the path to the first
// node; the screen rises as the accent runs down the rail and brings in a step at each node;
// Continue slides up; then the list goes back to the reached step.
private const val HEAD_IN = 0.4
private const val POP = 0.45
private const val DRAW = 0.5
private const val GREY_AT = 0.1
private const val GREY = 0.5
private const val TRAVEL_AT = 0.4
private const val TRAVEL = 0.8
private const val TOUR_AT = 1.2
private const val TOUR = 4.6

// The share of the tour spent speeding up, and slowing down.
private const val RAMP = 0.2
private const val BUTTON_AT = 5.3
private const val BUTTON_IN = 0.4
private const val END = 6.3

// After the entrance the accent takes this long to go back up to the reached step.
private const val RETURN = 1.2

// A step starts to show when the accent is LEAD above its node and is whole REVEAL further on.
private const val LEAD = 56.0
private const val REVEAL = 44.0
private const val SLIDE = 12.0

// A node turns over the last points before the accent's end reaches its centre.
private const val NODE_SPAN = 10.0

// Points a second, for the accent on the rail after a tap.
private const val FILL_SPEED = 300.0

// With reduced motion a change is a fade this long.
private const val FADE = 0.25

// After a touch the loop waits, puts the screen back and waits for that to end.
private const val USER_HOLD = 3.0
private const val SETTLE = 2.2

// A flick carries the list this many seconds of its speed further.
private const val GLIDE = 0.3
private const val BAND = 180.0

// A flick settles without a bounce (critical damping); a scripted move overshoots a little.
private const val STIFFNESS = 90.0
private val GLIDE_DAMPING = 2 * sqrt(STIFFNESS)
private const val RETURN_DAMPING = 14.0

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

private fun easeOut(t: Double): Double {
    val u = 1 - clamp01(t)
    return 1 - u * u * u
}

private fun easeInOut(t: Double): Double {
    val u = clamp01(t)
    return if (u < 0.5) 2 * u * u else 1 - 2 * (1 - u) * (1 - u)
}

/** Ease-out that goes a little past 1 and comes back. */
private fun overshoot(t: Double): Double {
    val u = clamp01(t) - 1
    return 1 + 2.70158 * u * u * u + 1.70158 * u * u
}

private fun smooth(t: Double): Double {
    val u = clamp01(t)
    return u * u * (3 - 2 * u)
}

/** A constant speed with a short ramp at each end. */
private fun steady(t: Double): Double {
    val u = clamp01(t)
    val area = 2 * RAMP * (1 - RAMP)
    if (u < RAMP) return u * u / area
    if (u > 1 - RAMP) return 1 - (1 - u) * (1 - u) / area
    return (u - RAMP / 2) / (1 - RAMP)
}

/** How far a node has turned for an accent that reaches [fill] points under the first node. */
private fun reachedBy(fill: Double, index: Int) = clamp01((fill - index * ROW) / NODE_SPAN + 1)

/** The point at a length along the path: three straight runs, three corners, then the rail. */
private fun pathPoint(length: Double): Pair<Double, Double> {
    val x = BRACKET_X
    val y = PIN_Y
    val drop = PIN_Y + BRACKET_DROP
    val r = CORNER
    var l = length
    if (l <= RUN_A) return PIN_X - l to y
    l -= RUN_A
    if (l <= TURN) return x + r - r * sin(l / r) to y + r - r * cos(l / r)
    l -= TURN
    if (l <= RUN_B) return x to y + r + l
    l -= RUN_B
    if (l <= TURN) return x + r - r * cos(l / r) to drop - r + r * sin(l / r)
    l -= TURN
    if (l <= RUN_C) return x + r + l to drop
    l -= RUN_C
    if (l <= TURN) return RAIL_X - r + r * sin(l / r) to drop + r - r * cos(l / r)
    return RAIL_X to drop + r + l - TURN
}

/** The path from the pin: left, down, right, and down the rail to [end]; in pixels. */
private fun roadPath(end: Double, density: Float) = Path().apply {
    fun p(v: Double) = (v * density).toFloat()
    val x = BRACKET_X
    val y = PIN_Y
    val drop = PIN_Y + BRACKET_DROP
    val r = CORNER
    moveTo(p(PIN_X), p(y))
    lineTo(p(x + r), p(y))
    arcTo(Rect(p(x), p(y), p(x + 2 * r), p(y + 2 * r)), 270f, -90f, false)
    lineTo(p(x), p(drop - r))
    arcTo(Rect(p(x), p(drop - 2 * r), p(x + 2 * r), p(drop)), 180f, -90f, false)
    lineTo(p(RAIL_X - r), p(drop))
    arcTo(Rect(p(RAIL_X - 2 * r), p(drop), p(RAIL_X), p(drop + 2 * r)), 270f, 90f, false)
    lineTo(p(RAIL_X), p(end))
}

/** A pull past an edge moves the list less and less. */
private fun banded(raw: Double, max: Double) = when {
    raw < 0 -> -(-raw / (1 - raw / BAND))
    raw > max -> max + (raw - max) / (1 + (raw - max) / BAND)
    else -> raw
}

private fun unbanded(shown: Double, max: Double): Double {
    val past = if (shown < 0) -shown else if (shown > max) shown - max else 0.0
    val pulled = past / (1 - min(past, BAND - 1) / BAND)
    return if (shown < 0) -pulled else if (shown > max) max + pulled else shown
}

/** Seconds on the clock every motion here is timed against. */
private fun clock() = System.nanoTime() / 1e9

/** A value going from one place to another at a constant speed. */
private class Move(val from: Double, val to: Double, val start: Double, val duration: Double) {
    fun progress(at: Double) = if (duration > 0) clamp01((at - start) / duration) else 1.0

    fun value(at: Double) = from + (to - from) * progress(at)
}

/** The list's scroll position on a spring of mass 1. */
private class Glide(val from: Double, val to: Double, val velocity: Double, val start: Double, val damping: Double) {
    fun value(at: Double): Double {
        val t = at - start
        if (t <= 0) return from
        if (from == to && velocity == 0.0) return to
        val natural = sqrt(STIFFNESS)
        val ratio = damping / (2 * natural)
        val x = from - to
        if (ratio < 1) {
            val damped = natural * sqrt(1 - ratio * ratio)
            return to + exp(-ratio * natural * t) * (x * cos(damped * t) + (velocity + ratio * natural * x) / damped * sin(damped * t))
        }
        return to + exp(-natural * t) * (x + (velocity + natural * x) * t)
    }

    companion object {
        fun still(value: Double) = Glide(value, value, 0.0, 0.0, GLIDE_DAMPING)
    }
}

/** The sizes that depend on the screen, in dp. */
private class Layout(val width: Double, val height: Double, top: Double, bottom: Double, val span: Double) {
    val stageTop = top + STAGE_TOP
    val stageHeight = height - stageTop
    val panel = PANEL_PAD + BUTTON + max(bottom, 16.0) + BUTTON_LIFT
    val maxScroll = max(0.0, NODE_INSET + span + TAIL - (stageHeight - panel - LIST_TOP))

    // The entrance starts with the heading's first line at mid-screen, this far under its place.
    val rise = max(0.0, height / 2 - stageTop - PIN_Y)
    val riseSpan = min(rise, span)
    val textWidth = max(0.0, min(TEXT_WIDTH, width - TEXT_X - SIDE))
}

/** Where everything is at one moment. */
private class Frame(val offset: Double, count: Int) {
    var rise = 0.0
    var heading = 1.0
    var panelSlide = 0.0
    var veil = 0.0

    // For each step, how much of it shows and how far its node has turned.
    val shown = DoubleArray(count) { 1.0 }
    val reached = DoubleArray(count)
    var solid = 0.0
    var ghost = 0.0
    var ghostOpacity = 0.0
    var greyFrom = 0.0
    var greyTo = 0.0
    var pinX = 0.0
    var pinY = 0.0
    var pinRound = 0.0
    var pinScale = 0.0
    var pinShown = false
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun roadText(size: Int, lineHeight: Int, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadingStyle = roadText(26, 34, FontWeight.Medium, Ink)
private val NumberStyle = roadText(13, 18, FontWeight.SemiBold, Ink).copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
private val TitleStyle = roadText(17, 22, FontWeight.Medium, Ink)
private val DescriptionStyle = roadText(14, 19, FontWeight.Normal, Mute)
private val ButtonStyle = roadText(14, 18, FontWeight.SemiBold, Screen)
private val PillStyle = roadText(13, 17, FontWeight.Normal, Screen)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun Density.px(value: Double) = (value * density).toFloat()

/**
 * A roadmap screen: a heading, and a path that leaves a pin beside it, turns under it and becomes
 * the rail of a list of steps. On entry the path draws itself, the pin rides it to the first step,
 * and the accent runs down the rail while the screen rises, bringing each step in as it reaches its
 * node. After that the list scrolls, a tap on a step moves the accent to it, and Continue goes one
 * step on.
 *
 * @param initialStep The step that is reached when the screen opens, from 0.
 * @param heading One or two lines.
 * @param tripLabel The first part of the pill in the top bar.
 * @param tripDate Its second part, which is heavier.
 * @param autoPlay Replays the entrance every [LOOP] seconds, starting from the finished screen.
 * @param onSelect Called with the step a tap chose.
 * @param onContinue Called with the step Continue moved to.
 * @param onDone Called when Continue is pressed on the last step.
 */
@Composable
fun RoadmapPathStepper(
    steps: List<RoadmapStep> = SampleRoadmapSteps,
    initialStep: Int = 0,
    heading: String = "The Roadmap to\nYour Trip",
    tripLabel: String = "Trip on",
    tripDate: String = "22 Dec, 09:52 PM",
    continueLabel: String = "Continue",
    accent: Color = Accent,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    onContinue: ((index: Int) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    // The loop, the gestures and the frame outlive a recomposition: they read these as state.
    val auto by rememberUpdatedState(autoPlay)
    val last by rememberUpdatedState(max(steps.size - 1, 0))
    val first by rememberUpdatedState(initialStep.coerceIn(0, last))
    val selected by rememberUpdatedState(onSelect)
    val continued by rememberUpdatedState(onContinue)
    val done by rememberUpdatedState(onDone)

    // The reached step; null until a touch changes it.
    var chosen by remember { mutableStateOf<Int?>(null) }
    // The moment the screen is empty and the entrance starts; null when none is running.
    var zero by remember { mutableStateOf<Double?>(null) }
    // Without autoPlay the entrance plays once, when the screen appears: until then it is empty.
    var entered by remember { mutableStateOf(false) }
    // The accent's end under the first node, moving from one place to another.
    var fill by remember { mutableStateOf<Move?>(null) }
    var scroll by remember { mutableStateOf(Glide.still(0.0)) }
    // Where the list was taken, while a finger drags it.
    var grab by remember { mutableStateOf<Double?>(null) }
    // How far the list scrolls on this screen.
    var limit by remember { mutableDoubleStateOf(0.0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The frame clock runs while something moves, and until this moment. Read while drawing only.
    var busyUntil by remember { mutableDoubleStateOf(0.0) }
    val now = remember { mutableDoubleStateOf(clock()) }

    val length = if (reduced) FADE else END

    fun index() = min(chosen ?: first, last)

    fun span() = last * ROW

    fun waiting() = !auto && !reduced && !entered

    /** The entrance is on its way: touches wait for it. */
    fun playing() = zero != null || waiting()

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    /** Keeps the frame clock running for this long. */
    fun stir(seconds: Double) {
        busyUntil = max(busyUntil, clock() + seconds)
    }

    /** Moves the accent, and the reached node, to a step. */
    fun reach(step: Int) {
        val at = clock()
        val from = fill?.value(at) ?: (index() * ROW)
        val to = step * ROW
        val duration = if (reduced) FADE else abs(to - from) / FILL_SPEED
        chosen = step
        fill = Move(from, to, at, duration)
        stir(duration + 0.1)
    }

    fun scrollTo(to: Double) {
        val at = clock()
        scroll = if (reduced) Glide.still(to) else Glide(scroll.value(at), to, 0.0, at, RETURN_DAMPING)
        stir(SETTLE)
    }

    /** Plays the entrance: over the finished screen, which fades out first, or from an empty one. */
    fun play(fromRest: Boolean) {
        val lead = if (fromRest) OUT + GAP else 0.0
        zero = clock() + lead
        stir(lead + length + SETTLE)
    }

    fun select(step: Int) {
        if (playing()) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        reach(step)
        selected?.invoke(step)
    }

    fun advance() {
        touched()
        if (playing()) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val current = index()
        if (current >= last) {
            done?.invoke()
            return
        }
        // The step it moved to is brought under the one before it at the top.
        scrollTo(min(current * ROW, limit))
        reach(current + 1)
        continued?.invoke(current + 1)
    }

    fun tap(y: Double) {
        touched()
        val k = floor((y + scroll.value(clock())) / ROW).toInt()
        if (k in 0..last) select(k)
    }

    fun release(velocity: Double) {
        if (grab == null) return
        grab = null
        val from = scroll.to
        val to = (from + velocity * GLIDE).coerceIn(0.0, limit)
        scroll = Glide(from, to, velocity, clock(), GLIDE_DAMPING)
        stir(SETTLE)
    }

    DisposableEffect(view) {
        // The screen is white: the status bar's icons have to be dark while it shows.
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    LaunchedEffect(Unit) {
        if (waiting()) play(fromRest = false)
        entered = true
    }

    LaunchedEffect(busyUntil) {
        while (clock() < busyUntil) now.doubleValue = withFrameNanos { it } / 1e9
        now.doubleValue = clock()
    }

    // The entrance ends at the last step: it hands the list over there, and the list goes back.
    LaunchedEffect(zero) {
        val start = zero ?: return@LaunchedEffect
        val ended = start + length
        delay(max(0L, ((ended - clock()) * 1000).toLong()))
        if (!reduced) {
            scroll = Glide(limit, 0.0, 0.0, ended, RETURN_DAMPING)
            fill = Move(span(), index() * ROW, ended, RETURN)
        }
        zero = null
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            while (zero != null) delay(100)
            // The loop starts over from the screen it was given.
            reach(first)
            scrollTo(0.0)
            delay((SETTLE * 1000).toLong())
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Double) = delay(max(0L, (origin + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(PLAY_AT)
            play(fromRest = true)
            until(LOOP)
            origin += (LOOP * 1e9).toLong()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Screen).clipToBounds()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value.toDouble()
        val bottom = insets.calculateBottomPadding().value.toDouble()
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val count = steps.size
        val layout = remember(width, height, top, bottom, count) { Layout(width, height, top, bottom, max(count - 1, 0) * ROW) }
        LaunchedEffect(layout) { limit = layout.maxScroll }

        // Where everything is at the clock's moment. Read in draw and layer blocks only, so a
        // moving value redraws without recomposing the screen.
        val frame by remember(layout, reduced) {
            derivedStateOf {
                val t = now.doubleValue
                val e = zero?.let { min(t - it, length) } ?: if (waiting()) 0.0 else END
                // While the entrance runs it places everything; before and after, the list's own values do.
                val live = !reduced && playing() && e >= 0
                // How far the accent has run down the rail during the entrance.
                val tour = layout.span * steady((e - TOUR_AT) / TOUR)
                val offset = if (live) (tour - layout.riseSpan).coerceIn(0.0, layout.maxScroll) else scroll.value(t)
                val from = fill?.from ?: (index() * ROW)
                val to = fill?.to ?: from
                val moved = fill?.progress(t) ?: 1.0
                val landed = e >= TRAVEL_AT + TRAVEL

                val frame = Frame(offset, max(count, 1))
                for (k in 0 until count) {
                    if (live) {
                        frame.shown[k] = if (k == 0) smooth(e / HEAD_IN) else smooth((tour - (k * ROW - LEAD)) / REVEAL)
                        frame.reached[k] = if (!landed) 0.0 else if (k == 0) 1.0 else reachedBy(tour, k)
                    } else {
                        // With reduced motion the two states cross-fade; otherwise the accent passes the node.
                        frame.reached[k] =
                            if (reduced) reachedBy(from, k) * (1 - moved) + reachedBy(to, k) * moved else reachedBy(from + (to - from) * moved, k)
                    }
                }

                // The accent's length along the path: one stroke, and with reduced motion a
                // second one that fades in or out over the stretch that changes.
                fun lengthFor(fill: Double) = max(PATH_TO_LIST, PATH_TO_NODE + fill - offset)
                if (live) {
                    frame.solid = if (e < DRAW) PATH_TO_NODE * easeOut(e / DRAW) else lengthFor(tour)
                } else if (reduced) {
                    frame.solid = lengthFor(min(from, to))
                    frame.ghost = lengthFor(max(from, to))
                    frame.ghostOpacity = if (to > from) moved else 1 - moved
                } else {
                    frame.solid = lengthFor(from + (to - from) * moved)
                }

                val railTop = max(LIST_TOP, LIST_TOP + NODE_INSET - offset)
                val full = min(max(railTop, LIST_TOP + NODE_INSET + layout.span - offset), layout.stageHeight)
                frame.greyFrom = railTop
                frame.greyTo = railTop + (full - railTop) * (if (live) easeOut((e - GREY_AT) / GREY) else 1.0)

                if (live) {
                    val lifted = if (layout.riseSpan > 0) min(tour / layout.riseSpan, 1.0) else steady((e - TOUR_AT) / TOUR)
                    frame.rise = layout.rise * (1 - lifted)
                    frame.heading = clamp01(e / HEAD_IN)
                    frame.panelSlide = layout.panel * (1 - easeOut((e - BUTTON_AT) / BUTTON_IN))
                    val along = easeInOut((e - TRAVEL_AT) / TRAVEL)
                    val point = pathPoint(PATH_TO_NODE * along)
                    frame.pinX = point.first
                    frame.pinY = point.second
                    // Near the node the tip rounds off and the pin is the node.
                    frame.pinRound = smooth((along - 0.7) / 0.3)
                    frame.pinScale = overshoot(e / POP)
                    frame.pinShown = !landed
                }
                frame.veil = if (e < 0) clamp01((e + OUT + GAP) / OUT) else if (reduced && zero != null) 1 - clamp01(e / FADE) else 0.0
                frame
            }
        }

        val density = LocalDensity.current.density
        val road = remember(density, layout) { roadPath(LIST_TOP + NODE_INSET + layout.span + OVERSCROLL, density) }
        val measure = remember(road) { PathMeasure().apply { setPath(road, false) } }
        val segment = remember { Path() }
        val pin = remember { Path() }
        val dragState = rememberDraggableState { delta ->
            val taken = grab
            if (taken != null) {
                val raw = taken - delta / density
                grab = raw
                scroll = Glide.still(banded(raw, limit))
            }
        }

        Box(
            Modifier
                .offset(y = layout.stageTop.dp)
                .fillMaxWidth()
                .height(layout.stageHeight.dp)
                .graphicsLayer { translationY = px(frame.rise) },
        ) {
            BasicText(
                text = heading,
                style = HeadingStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = HEADING_X.dp)
                    .width((width - HEADING_X - SIDE).dp)
                    .graphicsLayer { alpha = frame.heading.toFloat() }
                    .semantics { heading() },
            )

            Canvas(Modifier.matchParentSize()) {
                val f = frame
                drawLine(Track, Offset(px(RAIL_X), px(f.greyFrom)), Offset(px(RAIL_X), px(f.greyTo)), strokeWidth = px(RAIL))
                val stroke = Stroke(width = px(STROKE), cap = StrokeCap.Round)
                if (f.ghostOpacity > 0) {
                    segment.rewind()
                    if (measure.getSegment(0f, px(f.ghost), segment, true)) drawPath(segment, accent, alpha = f.ghostOpacity.toFloat(), style = stroke)
                }
                if (f.solid > 0) {
                    segment.rewind()
                    if (measure.getSegment(0f, px(f.solid), segment, true)) drawPath(segment, accent, style = stroke)
                }
            }

            Box(
                Modifier
                    .offset(y = LIST_TOP.dp)
                    .fillMaxWidth()
                    .height(max(0.0, layout.stageHeight - LIST_TOP).dp)
                    .clipToBounds()
                    .pointerInput(Unit) { detectTapGestures { tap(it.y.toDouble() / density) } }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = {
                            touched()
                            grab = if (playing()) null else unbanded(scroll.value(clock()), limit)
                        },
                        onDragStopped = { velocity -> release(-velocity.toDouble() / density) },
                    ),
            ) {
                steps.forEachIndexed { k, step ->
                    StepRow(
                        index = k,
                        step = step,
                        accent = accent,
                        textWidth = layout.textWidth,
                        isSelected = k == index(),
                        reached = { frame.reached[k] },
                        onPress = { select(k) },
                        modifier = Modifier
                            .offset(y = (k * ROW).dp)
                            .graphicsLayer {
                                val shown = frame.shown[k]
                                alpha = shown.toFloat()
                                translationY = px(-frame.offset + (1 - shown) * SLIDE)
                            },
                    )
                }
            }

            Canvas(Modifier.matchParentSize()) {
                val f = frame
                if (!f.pinShown || f.pinScale <= 0) return@Canvas
                val half = px(NODE / 2)
                val pop = f.pinScale.toFloat()
                val center = Offset(px(f.pinX), px(f.pinY))
                val tip = CornerRadius(px(PIN_TIP + (NODE / 2 - PIN_TIP) * f.pinRound))
                withTransform({
                    translate(center.x, center.y)
                    scale(pop, pop, Offset.Zero)
                    rotate(-45f, Offset.Zero)
                }) {
                    pin.rewind()
                    pin.addRoundRect(RoundRect(Rect(-half, -half, half, half), CornerRadius(half), CornerRadius(half), CornerRadius(half), tip))
                    drawPath(pin, accent)
                }
                drawCircle(Screen, radius = px(PIN_DOT / 2 * (1 - (1 - DOT / PIN_DOT) * f.pinRound)) * pop, center = center)
            }
        }

        // The panel is solid, so the list ends under it; it also keeps touches from the list.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(layout.panel.dp)
                .graphicsLayer { translationY = px(frame.panelSlide) }
                .background(Screen)
                .pointerInput(Unit) {}
                .padding(start = SIDE.dp, end = SIDE.dp, top = PANEL_PAD.dp),
        ) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val dim by animateFloatAsState(if (pressed) 0.8f else 1f, tween(120), label = "continue")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BUTTON.dp)
                    .graphicsLayer { alpha = dim }
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent)
                    .clickable(interaction, indication = null, role = Role.Button) { advance() },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(continueLabel, style = ButtonStyle, maxLines = 1)
            }
        }

        Box(Modifier.matchParentSize().graphicsLayer { alpha = frame.veil.toFloat() }.background(Screen))

        Row(
            Modifier
                .offset(y = (top + BAR_TOP).dp)
                .fillMaxWidth()
                .height(BAR.dp)
                .padding(horizontal = SIDE.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(ROUND.dp)
                    .border(1.5.dp, accent, CircleShape)
                    .clip(CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        touched()
                        onBack?.invoke()
                    }
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Row(
                Modifier.height(BAR.dp).clip(CircleShape).background(accent).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Screen, modifier = Modifier.size(14.dp))
                BasicText(
                    text = buildAnnotatedString {
                        append("$tripLabel ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(tripDate) }
                    },
                    style = PillStyle,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .size(ROUND.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        touched()
                        onHome?.invoke()
                    }
                    .semantics { contentDescription = "Home" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Home, contentDescription = null, tint = Screen, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** One step: its number, its node on the rail, its title and its description. */
@Composable
private fun StepRow(
    index: Int,
    step: RoadmapStep,
    accent: Color,
    textWidth: Double,
    isSelected: Boolean,
    reached: () -> Double,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(ROW.dp)
            .drawBehind {
                val center = Offset(px(RAIL_X), px(NODE_INSET))
                drawCircle(Screen, radius = px(HOLLOW / 2), center = center)
                drawCircle(Outline, radius = px(HOLLOW / 2 - 0.75), center = center, style = Stroke(px(1.5)))
                val turned = reached()
                if (turned > 0) {
                    val size = 0.6 + 0.4 * turned
                    drawCircle(accent, radius = px(NODE / 2 * size), center = center, alpha = turned.toFloat())
                    drawCircle(Screen, radius = px(DOT / 2 * size), center = center, alpha = turned.toFloat())
                }
            }
            .clearAndSetSemantics {
                contentDescription = "${index + 1}. ${step.title}. ${step.description}"
                role = Role.Button
                selected = isSelected
                onClick {
                    onPress()
                    true
                }
            },
    ) {
        Box(Modifier.offset(x = (NUMBER_X - 20).dp, y = (NODE_INSET - 9).dp).width(40.dp).height(18.dp), contentAlignment = Alignment.Center) {
            BasicText("${index + 1}", style = NumberStyle)
        }
        BasicText(
            text = step.title,
            style = TitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = TEXT_X.dp, y = (NODE_INSET - 11).dp).padding(end = (TEXT_X + SIDE).dp),
        )
        BasicText(
            text = step.description,
            style = DescriptionStyle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = TEXT_X.dp, y = (NODE_INSET + 17).dp).width(textWidth.dp),
        )
    }
}
