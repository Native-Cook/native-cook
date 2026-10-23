package com.ngocdevv.nativecook.blocks.cards.routeprogresscard

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param photo Address of the photo.
 * @param tint The photo's main colour, shown until the photo arrives.
 */
data class StopPhoto(val photo: String, val tint: Color)

/**
 * @param glow The glow across the top of the card.
 * @param accent The brighter colour of the marker, the ring and the bar.
 * @param share How far along the leg the marker stops, 0…1.
 * @param stops The next two stops, each with a small photo in [photos].
 */
data class RouteMode(
    val travel: Travel,
    val name: String,
    val glow: Color,
    val accent: Color,
    val distance: Int,
    val time: Int,
    val timeUnit: String,
    val share: Float,
    val start: String,
    val arrive: String,
    val stops: List<String>,
    val photos: List<StopPhoto>,
)

/** The fixed words on the card. */
data class RouteLabels(
    val from: String = "From",
    val to: String = "To",
    val go: String = "Go",
    val done: String = "Done",
    val next: String = "Next Step",
    val distance: String = "Distance",
    val time: String = "Time",
    val start: String = "Start",
    val travelTime: String = "Travel time",
    val arrive: String = "Arrive",
    val nextStop: String = "Next stop",
    val then: String = "Then",
)

private const val WIDTH = 350f
private const val HEIGHT = 356f

private const val CARD_W = 330f
private const val CARD_H = 204f

// Top of the open card: there is room for two pills above it and two below.
private const val CARD_Y = 76f
private const val CENTER_Y = CARD_Y + CARD_H / 2f
private const val CARD_RADIUS = 26f

// The two pills the card unfolds from, the capsule they merge into and the button between them.
private const val END_W = 128f
private const val CAPSULE_W = 236f
private const val CAPSULE_H = 44f
private const val PILL_GAP = 6f
private const val GO_W = 56f

// A waiting leg is a pill: wide next to the card, narrower one further away.
private const val PILL_H = 32f
private const val NEAR_W = 240f
private const val FAR_W = 184f
private const val GAP = 6f

// autoPlay (seconds): Go, then a new leg every `LEG_TIME`, then the card folds away.
private const val GO_IN = 0.35f
private const val GO_AT = 0.9f
private const val LEG_TIME = 2.3f
private const val LOOP = 10f

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

// Opening and closing, in seconds.
private const val GO_OUT = 0.16f
private const val MERGE_TIME = 0.22f
private const val OPEN_DELAY = 0.14f

// The marker sets off this long after its card starts to open, and travels for `PLAY_TIME`.
private const val PLAY_DELAY = 0.4f
private const val PLAY_TIME = 1.5f
private const val SPLIT_DELAY = 0.34f
private const val DONE_DELAY = 0.44f
private const val DONE_OUT = 1.25f

// From the end of the last leg until Go is back.
private const val CLOSE_TIME = 2.55f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

private val PopSpring = physical(320f, 18f)
private val OpenSpring = physical(200f, 22f)
private val StackSpring = physical(190f, 20f)
private val EaseIn = CubicBezierEasing(0.5f, 0f, 1f, 1f)
private val EaseInOut = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

// The figures count in this many steps.
private const val COUNT_STEPS = 50

private fun photoUrl(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=102&h=102&q=80"

private val Modes = listOf(
    RouteMode(
        travel = Travel.Fly, name = "Flight", glow = Color(0xFF0D9488), accent = Color(0xFF5EEAD4),
        distance = 953, time = 3, timeUnit = "hours", share = 0.8f, start = "11:15 AM", arrive = "2:15 PM",
        stops = listOf("Alder Field", "Coral Bay Airport"),
        photos = listOf(
            StopPhoto(photoUrl("1436491865332-7a61a109cc05"), Color(0xFFB4B7B8)),
            StopPhoto(photoUrl("1506905925346-21bda4d32df4"), Color(0xFF6A6D7F)),
        ),
    ),
    RouteMode(
        travel = Travel.Drive, name = "Driving", glow = Color(0xFF2563EB), accent = Color(0xFF93C5FD),
        distance = 1090, time = 18, timeUnit = "hours", share = 0.62f, start = "10:40 AM", arrive = "4:40 AM",
        stops = listOf("Riverton", "Lakeside"),
        photos = listOf(
            StopPhoto(photoUrl("1500530855697-b586d89ba3ee"), Color(0xFF6E6462)),
            StopPhoto(photoUrl("1501594907352-04cda38ebc29"), Color(0xFF79949C)),
        ),
    ),
    RouteMode(
        travel = Travel.Walk, name = "Walking", glow = Color(0xFF6D28D9), accent = Color(0xFFA78BFA),
        distance = 2, time = 31, timeUnit = "minutes", share = 0.28f, start = "10:00 AM", arrive = "10:31 AM",
        stops = listOf("Linden Circle", "Clock Tower"),
        photos = listOf(
            StopPhoto(photoUrl("1493976040374-85c8e12f0c0e"), Color(0xFF5B5353)),
            StopPhoto(photoUrl("1583417319070-4a69db38a482"), Color(0xFF977B78)),
        ),
    ),
)

private val Night = Color(0xFF0B0B0F)
private val Violet = Color(0xFF6D28D9)
private val Rim = Color.White.copy(alpha = 0.16f)

private fun type(size: Float, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val DimStyle = type(7.5f, Color.White.copy(alpha = 0.45f))
private val PercentStyle = type(8.5f, Color.White.copy(alpha = 0.9f), FontWeight.Medium)
private val NextStyle = type(9.5f, Color.White.copy(alpha = 0.9f))
private val FigureStyle = type(21f, Color.White, FontWeight.Light, -0.3f)
private val StampStyle = type(9.5f, Color.White.copy(alpha = 0.8f))
private val StopStyle = type(10.5f, Color.White.copy(alpha = 0.9f))
private val NameStyle = type(13f, Color.White.copy(alpha = 0.9f))
private val SummaryStyle = type(11.5f, Color.White.copy(alpha = 0.85f))
private val EndLabelStyle = type(7.5f, Color.White.copy(alpha = 0.5f))
private val EndPlaceStyle = type(12f, Color.White, FontWeight.Medium)
private val GoStyle = type(14f, Color.White, FontWeight.SemiBold)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smooth(t: Float) = t * t * (3f - 2f * t)

private fun grouped(n: Int): String {
    var digits = n.toString()
    var out = ""
    while (digits.length > 3) {
        out = "," + digits.takeLast(3) + out
        digits = digits.dropLast(3)
    }
    return digits + out
}

/** The share [eased] of [total]: a whole number, except that totals under ten count in tenths. */
private fun amount(total: Int, eased: Float): String {
    val value = total * eased
    if (total >= 10) return grouped(value.roundToInt())
    val tenths = (value * 10f).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/** @param expand 0 a pill … 1 the card. */
private class PanelBox(val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float, val expand: Float)

/**
 * Where leg [index]'s panel is. [active] is the open card's place in the list: as it slides from one
 * leg to the next, one card shrinks to a pill while the other grows, and the card's top stays put.
 * At [open] 0 every panel is the capsule the two pills merged into.
 */
private fun panelLayout(index: Int, open: Float, active: Float): PanelBox {
    var top = CARD_Y - active * (PILL_H + GAP)
    var width = 0f
    var height = 0f
    var expand = 0f
    for (i in 0..index) {
        if (i > 0) top += height + GAP
        val away = abs(i - active)
        // A little past 1 near the card, so the spring's overshoot does not squeeze it.
        expand = clamp01((1f - away) * 1.1f)
        width = mix(mix(NEAR_W, FAR_W, clamp01(away - 1f)), CARD_W, expand)
        height = mix(PILL_H, CARD_H, expand)
    }
    return PanelBox(
        x = WIDTH / 2f - mix(CAPSULE_W, width, open) / 2f,
        y = mix(CENTER_Y - CAPSULE_H / 2f, top, open),
        width = mix(CAPSULE_W, width, open),
        height = mix(CAPSULE_H, height, open),
        radius = mix(CAPSULE_H / 2f, mix(PILL_H / 2f, CARD_RADIUS, expand), open),
        expand = expand,
    )
}

/** The panels' contents show once the capsule has grown about halfway into the card. */
private fun seenAt(open: Float) = clamp01((open - 0.45f) / 0.4f)

private enum class Phase { Idle, Open, Closing }

/** What moves. */
private class RouteMotion(count: Int) {
    /** The Go button: 0 away … 1 between the pills, which it pushes apart. */
    val go = Animatable(0f)

    /** 0 two pills … 1 merged into one capsule. */
    val merge = Animatable(0f)

    /** 0 that capsule … 1 the open card with the other legs around it. */
    val open = Animatable(0f)

    /** Position in the list of the leg whose card is open; it slides when another is picked. */
    val active = Animatable(count - 1f)

    /** Each leg's marker, 0…1 before easing, and the press feedback of its pill. */
    val progress = List(count) { Animatable(0f) }
    val press = List(count) { Animatable(0f) }
    val goPress = Animatable(0f)
    val nextPress = Animatable(0f)

    /** A clock for the road. */
    var seconds by mutableFloatStateOf(0f)

    var phase = Phase.Idle

    /** Go is on screen and can be tapped. */
    var armed = false

    /** The leg whose card is open, or opens next. */
    var shown by mutableIntStateOf(count - 1)

    /** The button between the pills reads Done instead of Go. */
    var done by mutableStateOf(false)

    /** Steps still waiting; a new run of the card drops them. */
    var later: Job? = null
}

/**
 * A route planner that unfolds from two pills. "From" and "To" sit side by side with a Go button
 * between them; tapping it merges them into a dark card for the first leg, whose marker travels while
 * its distance and time count up. The other ways to travel wait as pills above and below the card:
 * tapping one (or "Next Step") shrinks the card into a pill and opens that one, each with its own
 * glow colour and drawing. After the last leg the card folds back into the two pills.
 *
 * @param modes The three ways to travel, top to bottom. The route is followed from the last to the first.
 * @param autoPlay Taps Go, steps through the legs and folds the card away by itself, in a loop.
 * @param onStart Called when Go opens the card.
 * @param onSelect Called with the index of the leg that was opened.
 * @param onFinish Called when the card folds back into the two pills.
 */
@Composable
fun RouteProgressCard(
    from: String = "Alder Park",
    to: String = "Coral Bay",
    modes: List<RouteMode> = Modes,
    distanceUnit: String = "miles",
    labels: RouteLabels = RouteLabels(),
    autoPlay: Boolean = false,
    onStart: (() -> Unit)? = null,
    onSelect: ((index: Int) -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still && modes.size > 1
    val last = modes.lastIndex
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember(modes.size) { RouteMotion(modes.size) }
    val started by rememberUpdatedState(onStart)
    val selected by rememberUpdatedState(onSelect)
    val finished by rememberUpdatedState(onFinish)
    // Counts taps: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    // Under reduced motion nothing travels: every change is a jump.
    fun move(value: Animatable<Float, AnimationVector1D>, target: Float, spec: AnimationSpec<Float>, delay: Float = 0f) {
        scope.launch {
            if (delay > 0f) delay((delay * 1000).toLong())
            if (still) value.snapTo(target) else value.animateTo(target, spec)
        }
    }

    // Up in 80 ms, a 40 ms hold, back in 180 ms: a tap.
    fun pulse(value: Animatable<Float, AnimationVector1D>) {
        scope.launch {
            value.animateTo(1f, tween(80, easing = LinearEasing))
            delay(40)
            value.animateTo(0f, tween(180, easing = LinearEasing))
        }
    }

    fun play(leg: Int, delay: Float) {
        scope.launch {
            motion.progress[leg].snapTo(0f)
            delay((delay * 1000).toLong())
            if (still) motion.progress[leg].snapTo(1f) else motion.progress[leg].animateTo(1f, tween((PLAY_TIME * 1000).toInt(), easing = LinearEasing))
        }
    }

    fun showGo() {
        motion.armed = true
        motion.done = false
        move(motion.go, 1f, PopSpring)
    }

    // Go: the pills merge, and the capsule they make grows into the first leg's card.
    fun start() {
        if (motion.phase != Phase.Idle || !motion.armed) return
        motion.phase = Phase.Open
        motion.armed = false
        motion.later?.cancel()
        motion.shown = last
        pulse(motion.goPress)
        scope.launch { motion.active.snapTo(last.toFloat()) }
        move(motion.go, 0f, tween((GO_OUT * 1000).toInt(), easing = EaseIn))
        move(motion.merge, 1f, tween((MERGE_TIME * 1000).toInt(), easing = EaseInOut))
        move(motion.open, 1f, OpenSpring, OPEN_DELAY)
        play(last, PLAY_DELAY + OPEN_DELAY)
        started?.invoke()
    }

    // Opens leg `index`: the open card shrinks to a pill as this one grows into the card.
    fun select(index: Int) {
        if (motion.phase != Phase.Open || index == motion.shown || index !in 0..last) return
        motion.shown = index
        pulse(motion.press[index])
        move(motion.active, index.toFloat(), StackSpring)
        play(index, PLAY_DELAY)
        selected?.invoke(index)
    }

    // The card folds back into the two pills; Done shows between them for a moment, then Go returns.
    fun finish() {
        if (motion.phase != Phase.Open) return
        motion.phase = Phase.Closing
        motion.done = true
        move(motion.open, 0f, OpenSpring)
        move(motion.merge, 0f, tween((MERGE_TIME * 1000).toInt(), easing = EaseInOut), SPLIT_DELAY)
        move(motion.go, 1f, PopSpring, DONE_DELAY)
        motion.later?.cancel()
        motion.later = scope.launch {
            delay((DONE_OUT * 1000).toLong())
            move(motion.go, 0f, tween((GO_OUT * 1000).toInt(), easing = EaseIn))
            motion.phase = Phase.Idle
            delay(((CLOSE_TIME - DONE_OUT) * 1000).toLong())
            showGo()
        }
        finished?.invoke()
    }

    fun next() {
        pulse(motion.nextPress)
        if (motion.shown > 0) select(motion.shown - 1) else finish()
    }

    fun touch() {
        touches += 1
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    LaunchedEffect(motion) {
        motion.later = scope.launch {
            delay((GO_IN * 1000).toLong())
            showGo()
        }
    }

    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var before = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            motion.seconds += min((frame - before) / 1e9f, 0.064f)
            before = frame
        }
    }

    // The script a finger would follow: Go, then each leg in turn, then the end.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, fold the card away if it is open, and start over from the pills.
            delay((RESUME_AFTER * 1000).toLong())
            val busy = motion.phase != Phase.Idle || !motion.armed
            finish()
            if (busy) delay(((CLOSE_TIME - GO_IN) * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((turn * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            until(GO_AT)
            start()
            for (step in 1..last) {
                until(GO_AT + step * LEG_TIME)
                select(last - step)
            }
            until(GO_AT + modes.size * LEG_TIME)
            finish()
            turn += 1
        }
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        modes.forEachIndexed { index, mode ->
            Panel(
                index = index,
                mode = mode,
                labels = labels,
                distanceUnit = distanceUnit,
                motion = motion,
                still = still,
                onSelect = {
                    touch()
                    select(index)
                },
                onNext = {
                    touch()
                    next()
                },
            )
        }

        EndPill(labels.from, from) {
            val gap = mix(PILL_GAP, GO_W + 12f, motion.go.value)
            mix(WIDTH / 2f - gap / 2f - END_W, WIDTH / 2f - CAPSULE_W / 2f, motion.merge.value) to 1f - motion.merge.value
        }
        EndPill(labels.to, to) {
            val gap = mix(PILL_GAP, GO_W + 12f, motion.go.value)
            mix(WIDTH / 2f + gap / 2f, WIDTH / 2f + CAPSULE_W / 2f - END_W, motion.merge.value) to 1f - motion.merge.value
        }

        val goWord = if (motion.done) labels.done else labels.go
        Box(
            Modifier
                .offset((WIDTH / 2f - GO_W / 2f).dp, (CENTER_Y - CAPSULE_H / 2f).dp)
                .size(GO_W.dp, CAPSULE_H.dp)
                .graphicsLayer {
                    val grow = max(0f, motion.go.value) * (1f - 0.08f * motion.goPress.value)
                    alpha = clamp01(motion.go.value * 4f)
                    scaleX = grow
                    scaleY = grow
                }
                .softShadow(remember { SoftRoundRect(Violet.copy(alpha = 0.45f), sigma = 8f) }, 5f) { CAPSULE_H / 2f }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Violet)))
                .rim(Color.White.copy(alpha = 0.22f)) { CAPSULE_H / 2f }
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    start()
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(goWord, style = GoStyle, maxLines = 1)
        }
    }
}

/** A soft drop shadow under a rounded box, [drop] dp down. */
private fun Modifier.softShadow(shadow: SoftRoundRect, drop: Float, radius: () -> Float) = drawBehind {
    val width = size.width / density
    val height = size.height / density
    scale(density, density, pivot = Offset.Zero) {
        drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, drop, width, height, radius()) }
    }
}

/** A hairline just inside the edge of a rounded box. */
private fun Modifier.rim(color: Color, radius: () -> Float) = drawWithContent {
    drawContent()
    val line = 1.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(line / 2f, line / 2f),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius(radius().dp.toPx() - line / 2f),
        style = Stroke(line),
    )
}

/** One leg: a pill while it waits, the card while it is open. */
@Composable
private fun Panel(
    index: Int,
    mode: RouteMode,
    labels: RouteLabels,
    distanceUnit: String,
    motion: RouteMotion,
    still: Boolean,
    onSelect: () -> Unit,
    onNext: () -> Unit,
) {
    val shown = motion.shown == index
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.22f), sigma = 10f) }
    fun box() = panelLayout(index, motion.open.value, motion.active.value)

    Box(
        Modifier
            .zIndex(if (shown) 1f else 0f)
            .layout { measurable, _ ->
                val box = box()
                val placeable = measurable.measure(Constraints.fixed(box.width.dp.roundToPx(), box.height.dp.roundToPx()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val box = box()
                val dip = 1f - 0.04f * motion.press[index].value
                translationX = box.x.dp.toPx()
                translationY = box.y.dp.toPx()
                alpha = motion.merge.value
                scaleX = dip
                scaleY = dip
            }
            .softShadow(shadow, 8f) { box().radius }
            .graphicsLayer {
                clip = true
                shape = RoundedCornerShape(box().radius.dp)
            }
            .background(Night)
            .rim(Rim) { box().radius }
            .clickable(enabled = !shown, interactionSource = null, indication = null, role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = "${mode.name}, ~${mode.time} ${mode.timeUnit}" },
    ) {
        // The pill's edge that faces the card catches the leg's colour.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = (1f - box().expand) * clamp01(motion.active.value - index) }
                .background(Brush.verticalGradient(0.35f to mode.glow.copy(alpha = 0f), 1f to mode.glow.copy(alpha = 0.5f))),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = (1f - box().expand) * clamp01(index - motion.active.value) }
                .background(Brush.verticalGradient(0f to mode.glow.copy(alpha = 0.5f), 0.65f to mode.glow.copy(alpha = 0f))),
        )

        Box(
            Modifier
                .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                .size(CARD_W.dp, CARD_H.dp)
                .graphicsLayer { alpha = smooth(clamp01((box().expand - 0.45f) / 0.55f)) * seenAt(motion.open.value) },
        ) {
            Face(mode, labels, distanceUnit, motion, index, still, enabled = shown, onNext = onNext)
        }

        Row(
            Modifier
                .height(PILL_H.dp)
                .graphicsLayer {
                    val expand = box().expand
                    val grow = mix(0.9f, 1f, expand)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = mix(12f, 18f, expand).dp.toPx()
                    translationY = mix(0f, 12f, expand).dp.toPx()
                    scaleX = grow
                    scaleY = grow
                    alpha = seenAt(motion.open.value)
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TravelIcon(mode.travel, Modifier.size(20.dp))
            BasicText(mode.name, style = NameStyle, maxLines = 1)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .height(PILL_H.dp)
                .padding(end = 12.dp)
                .graphicsLayer { alpha = (1f - clamp01(box().expand * 3f)) * seenAt(motion.open.value) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText("~${mode.time} ${mode.timeUnit}", style = SummaryStyle, maxLines = 1)
        }
    }
}

/** The inside of an open card, laid out in a 330 × 204 box. */
@Composable
private fun BoxScope.Face(
    mode: RouteMode,
    labels: RouteLabels,
    distanceUnit: String,
    motion: RouteMotion,
    index: Int,
    still: Boolean,
    enabled: Boolean,
    onNext: () -> Unit,
) {
    val progress = motion.progress[index]
    val soft = remember(mode.accent) { Halo(mode.accent.copy(alpha = 0.9f), sigma = 5f) }
    val sharp = remember(mode.accent) { Halo(mode.accent.copy(alpha = 0.9f), sigma = 4f) }
    // The marker's eased progress in steps, for the figures.
    val step by remember(progress) { derivedStateOf { (inOut(progress.value) * COUNT_STEPS).roundToInt() } }
    val eased = step / COUNT_STEPS.toFloat()
    val walking = mode.travel == Travel.Walk
    val labelY = if (walking) 116f else 90f

    Canvas(Modifier.fillMaxSize()) {
        // Drawn in dp, like the Skia canvas of the React Native version.
        scale(density, density, pivot = Offset.Zero) {
            drawFace(
                mode.travel, mode.glow, mode.accent, soft, sharp,
                along = mode.share * inOut(progress.value),
                seconds = if (still || mode.travel != Travel.Drive) 0f else motion.seconds,
                nextPress = motion.nextPress.value,
            )
        }
    }

    At("${(mode.share * eased * 100f).roundToInt()}%", PercentStyle, 145f, 20f, 40f, 16f, Alignment.Center)
    At(labels.next, NextStyle, 248f, 16f, 48f, 24f, Alignment.Center)
    Box(
        Modifier
            .offset(236.dp, 8.dp)
            .size(82.dp, 40.dp)
            .clickable(enabled = enabled, interactionSource = null, indication = null, role = Role.Button, onClick = onNext)
            .semantics { contentDescription = labels.next },
    )

    At(labels.distance, DimStyle, 18f, 48f, 120f, 10f)
    At("${amount(mode.distance, eased)} $distanceUnit", FigureStyle, 18f, 58f, 180f, 26f)
    At(labels.time, DimStyle, 192f, 48f, 120f, 10f, Alignment.CenterEnd)
    At("~${amount(mode.time, eased)} ${mode.timeUnit}", FigureStyle, 132f, 58f, 180f, 26f, Alignment.CenterEnd)

    Stamp(labels.start, mode.start, 18f, labelY, Alignment.CenterStart)
    if (walking) Stamp(labels.travelTime, "${mode.time} min", 106f, labelY, Alignment.CenterStart)
    Stamp(labels.arrive, mode.arrive, 232f, labelY, Alignment.CenterEnd)

    mode.stops.take(2).forEachIndexed { row, stop ->
        At(if (row == 0) labels.nextStop else labels.then, DimStyle, 28f, 156f + row * 16f, 44f, 12f)
        At(stop, StopStyle, 74f, 155f + row * 16f, 150f, 14f)
    }
    mode.photos.take(2).forEachIndexed { row, item ->
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current).data(item.photo).crossfade(200).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.offset((238f + row * 40f).dp, 153.dp).size(34.dp).clip(RoundedCornerShape(8.dp)).background(item.tint),
        )
    }
}

/** A line of text in a box whose top-left corner is at [x], [y]. */
@Composable
private fun BoxScope.At(text: String, style: TextStyle, x: Float, y: Float, width: Float, height: Float, align: Alignment = Alignment.CenterStart) {
    Box(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp), contentAlignment = align) {
        BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

/** A small grey label over its value. */
@Composable
private fun BoxScope.Stamp(label: String, value: String, x: Float, y: Float, align: Alignment) {
    At(label, DimStyle, x, y, 80f, 10f, align)
    At(value, StampStyle, x, y + 10f, 80f, 13f, align)
}

/** One of the two pills the card unfolds from. [place] gives its left edge in dp and its opacity. */
@Composable
private fun EndPill(label: String, name: String, place: () -> Pair<Float, Float>) {
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.22f), sigma = 10f) }
    Column(
        Modifier
            .offset(0.dp, (CENTER_Y - CAPSULE_H / 2f).dp)
            .size(END_W.dp, CAPSULE_H.dp)
            .graphicsLayer {
                val (x, opacity) = place()
                translationX = x.dp.toPx()
                alpha = opacity
            }
            .softShadow(shadow, 8f) { CAPSULE_H / 2f }
            .clip(CircleShape)
            .background(Night)
            .background(Brush.verticalGradient(0f to Violet.copy(alpha = 0.55f), 0.6f to Violet.copy(alpha = 0f)))
            .rim(Rim) { CAPSULE_H / 2f }
            .padding(start = 18.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        BasicText(label, style = EndLabelStyle, maxLines = 1)
        BasicText(name, style = EndPlaceStyle, maxLines = 1)
    }
}
