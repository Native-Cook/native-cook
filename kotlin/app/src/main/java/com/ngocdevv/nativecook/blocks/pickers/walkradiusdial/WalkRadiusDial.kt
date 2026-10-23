package com.ngocdevv.nativecook.blocks.pickers.walkradiusdial

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WIDTH = 340f
private const val HEIGHT = 476f
private const val TITLE_HEIGHT = 68f
private const val BUTTON_HEIGHT = 52f

// The dial's radius and the height of its centre.
private const val DIAL = 150f
private const val DIAL_Y = 246f

// The circle's radius at the fewest and at the most minutes.
private const val MIN_RADIUS = 72f
private const val MAX_RADIUS = 145.5f

// How far past either end a finger can pull the handle.
private const val GIVE = 4.5f
private const val BADGE = 66f
private const val HANDLE_WIDTH = 42f
private const val HANDLE_HEIGHT = 30f
private const val HANDLE_TOUCH = 64f
private const val HALO = 36f

// The number rolls over this share of a minute to each side of the half-way point, by this many dp.
private const val ROLL_WINDOW = 0.25f
private const val ROLL_TRAVEL = 18f

private const val PRESS_TIME = 0.15f
private const val RIPPLE_TIME = 0.45f

// The fastest release the spring is given, in dp a second.
private const val MAX_FLING = 600f

// The script's drag ends at this multiple of its average speed (the slope at the end of `DragCurve`).
private const val DRAG_END_SPEED = 1.25f

// autoPlay (seconds): the handle is held at `GRAB_AT`, and dragged for `DRAG_TIME` 150 ms later.
private val GRAB_AT = floatArrayOf(0.9f, 3.15f, 5.5f)
private val DRAG_TIME = floatArrayOf(1.2f, 1.3f, 0.5f)
private const val LOOP = 8f

// After a touch the loop waits this long, returns to the first value, and starts over once it has settled.
private const val RESUME_AFTER = 2f
private const val SETTLE = 0.9f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.01f)

private val ReleaseSpring = physical(180f, 12f)
private val DragCurve = CubicBezierEasing(0.45f, 0f, 0.8f, 0.75f)

private val Blue = Color(0xFF2E8FD0)
private val Night = Color(0xFF1C1C1C)

// The invented neighbourhood, in map units with the dial's centre at the origin: at zoom 1 the dial
// shows 150 units to each side. It is drawn tilted, as a grid of avenues and streets with one
// boulevard across it, a few parks, a route and some pins.
private const val TILT = -24f
private val Land = Color(0xFFF1EFE9)
private val Park = Color(0xFFA8D08D)
private val Plaza = Color(0xFFF3E7B8)
private val RouteColor = Color(0xFFF29E4C)
private val Transit = Color(0xFF3B82F6)
private val Food = Color(0xFFF08A3C)
private val Veil = Color(0xFFAEB6C2).copy(alpha = 0.65f)

// Streets run across every 16 units (every fourth is a wide one), avenues up and down every 30.
private val NarrowStreets = Path().apply {
    for (k in 0 until 22) if (k % 4 != 1) {
        moveTo(-190f, -168f + 16f * k)
        lineTo(190f, -168f + 16f * k)
    }
}
private val WideStreets = Path().apply {
    for (k in 0 until 22) if (k % 4 == 1) {
        moveTo(-190f, -168f + 16f * k)
        lineTo(190f, -168f + 16f * k)
    }
}
private val Avenues = Path().apply {
    for (k in 0 until 12) {
        moveTo(-165f + 30f * k, -190f)
        lineTo(-165f + 30f * k, 190f)
    }
}
private val Boulevard = Path().apply {
    moveTo(-190f, 62f)
    lineTo(190f, -98f)
}
private val RoutePath = Path().apply {
    moveTo(-190f, 72f)
    lineTo(-45f, 72f)
    lineTo(-45f, 24f)
    lineTo(15f, 24f)
    lineTo(15f, -8f)
    lineTo(75f, -8f)
    lineTo(75f, -72f)
    lineTo(190f, -72f)
}

// Blocks turned into parks, and some into plazas: x, y, width, height.
private val Parks = listOf(
    Rect(Offset(-42.5f, 10f), Size(25f, 28f)),
    Rect(Offset(77.5f, -38f), Size(25f, 28f)),
    Rect(Offset(-102.5f, -70f), Size(25f, 28f)),
    Rect(Offset(17.5f, 58f), Size(55f, 28f)),
    Rect(Offset(-162.5f, 90f), Size(25f, 28f)),
    Rect(Offset(107.5f, -134f), Size(25f, 28f)),
)
private val Plazas = listOf(
    Rect(Offset(17.5f, -86f), Size(25f, 12f)),
    Rect(Offset(-72.5f, 74f), Size(25f, 12f)),
    Rect(Offset(107.5f, 26f), Size(25f, 12f)),
)
private val TransitPins = listOf(
    Offset(-15f, -24f), Offset(45f, 40f), Offset(-75f, 56f), Offset(75f, -56f),
    Offset(15f, -88f), Offset(-45f, -72f), Offset(135f, 8f), Offset(-135f, -8f),
)
private val FoodPins = listOf(
    Offset(-30f, -16f), Offset(30f, 16f), Offset(60f, 48f), Offset(-60f, -48f), Offset(90f, 16f),
    Offset(0f, 80f), Offset(30f, -48f), Offset(-120f, 32f), Offset(120f, -88f), Offset(-90f, 96f),
)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun radiusFor(minutes: Int, range: IntRange) =
    mix(MIN_RADIUS, MAX_RADIUS, (minutes - range.first) / max(1, range.last - range.first).toFloat())

private fun minutesAt(radius: Float, range: IntRange) =
    range.first + (radius - MIN_RADIUS) / (MAX_RADIUS - MIN_RADIUS) * (range.last - range.first)

private fun snapped(radius: Float, range: IntRange) = minutesAt(radius, range).roundToInt().coerceIn(range.first, range.last)

/**
 * The map's scale. The ground covered grows with the minutes, so the scale is the circle's radius
 * over the minutes: 1 at the most minutes, where the whole map shows.
 */
private fun zoomAt(radius: Float, range: IntRange): Float {
    val minutes = max(minutesAt(radius, range), max(1f, range.first * 0.75f))
    return radius / minutes / (MAX_RADIUS / max(1, range.last))
}

/** Past either end the handle follows the finger at a third of its speed, for a few dp. */
private fun rubber(radius: Float) = when {
    radius > MAX_RADIUS -> MAX_RADIUS + min(GIVE, (radius - MAX_RADIUS) / 3f)
    radius < MIN_RADIUS -> MIN_RADIUS - min(GIVE, (MIN_RADIUS - radius) / 3f)
    else -> radius
}

/** The map, drawn in dp about the dial's centre at [zoom]. */
private fun DrawScope.drawMap(zoom: Float) {
    translate(DIAL, DIAL) {
        scale(zoom, zoom, pivot = Offset.Zero) {
            rotate(TILT, pivot = Offset.Zero) {
                drawRect(Land, Offset(-260f, -260f), Size(520f, 520f))
                drawPath(NarrowStreets, Color.White, style = Stroke(1.8f))
                drawPath(WideStreets, Color.White, style = Stroke(3.4f))
                drawPath(Avenues, Color.White, style = Stroke(3f))
                Parks.forEach { drawRoundRect(Park, it.topLeft, it.size, CornerRadius(2.5f)) }
                Plazas.forEach { drawRoundRect(Plaza, it.topLeft, it.size, CornerRadius(2.5f)) }
                drawPath(Boulevard, Color.White, style = Stroke(5f))
                drawPath(RoutePath, RouteColor, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                TransitPins.forEach {
                    drawCircle(Transit, 2.6f, it)
                    drawCircle(Color.White, 1f, it)
                }
                FoodPins.forEach { drawCircle(Food, 2.1f, it) }
            }
        }
    }
}

private fun type(size: Float, weight: FontWeight, spacing: Float = 0f, color: Color = Color.White) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val QuestionStyle = type(24f, FontWeight.Normal, color = Color(0xFF1E1E1E)).copy(lineHeight = 32.sp)
private val NumberStyle = type(20f, FontWeight.Bold)
private val UnitStyle = type(10f, FontWeight.SemiBold, 1.6f, Color.White.copy(alpha = 0.9f))
private val NextStyle = type(16f, FontWeight.Bold)

/** What a touch and the script share. */
private class DialState(var minutes: Int) {
    var dragging = false

    /** The speed the script's drag ends with, handed to the spring on release. */
    var carry = 0f
}

/**
 * A walking-distance question answered on a round map. A circle of full colour sits on a washed-out
 * street map; a handle on its right edge is pulled outward to give more minutes and pushed inward
 * to give fewer. The map zooms with the circle, so the circle always covers the ground the minutes
 * reach, and the number in the middle rolls from minute to minute. Tapping the map moves the edge
 * there. An invented street grid is drawn in code: there are no map tiles.
 *
 * @param range The fewest and the most minutes; the circle is smallest and largest at these.
 * @param initial The minutes shown at first.
 * @param autoPlay Pulls the handle out and pushes it back by itself, in a loop.
 * @param onChange Called with each minute the handle passes, and with the one it is released on.
 * @param onNext Called with the chosen minutes when the button is tapped.
 */
@Composable
fun WalkRadiusDial(
    range: IntRange = 4..18,
    initial: Int = 6,
    question: String = "How far are you willing\nto walk?",
    unit: String = "MIN",
    nextLabel: String = "Next",
    autoPlay: Boolean = false,
    onChange: ((minutes: Int) -> Unit)? = null,
    onNext: ((minutes: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still
    val first = initial.coerceIn(range.first, range.last)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)

    // The circle's radius in dp. The minutes, the number and the map's zoom all follow it.
    val radius = remember(range) { Animatable(radiusFor(first, range)) }
    // 0 the handle at rest … 1 held.
    val press = remember { Animatable(0f) }
    // The ring a released handle sends out, 0…1.
    val ripple = remember { Animatable(1f) }
    val nextPress = remember { Animatable(0f) }
    val state = remember(range) { DialState(first) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    fun touch() {
        touches += 1
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    // The handle is held (blue, with a halo) or let go of; letting go sends out a ring.
    fun hold(held: Boolean) {
        scope.launch {
            val target = if (held) 1f else 0f
            if (still) press.snapTo(target) else press.animateTo(target, tween((PRESS_TIME * 1000).toInt(), easing = EaseOutStrong))
        }
        if (held || still) return
        scope.launch {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween((RIPPLE_TIME * 1000).toInt(), easing = EaseOutStrong))
        }
    }

    // Moves the circle's edge to `minutes` on the spring, starting at `speed` dp a second.
    fun settle(minutes: Int, speed: Float = 0f) {
        scope.launch {
            val target = radiusFor(minutes, range)
            if (still) radius.snapTo(target) else radius.animateTo(target, ReleaseSpring, initialVelocity = speed)
        }
    }

    // The script's drag: the handle travels to `minutes` in `time` seconds and is still moving when it gets there.
    fun drag(minutes: Int, time: Float) {
        val target = radiusFor(minutes, range)
        state.carry = DRAG_END_SPEED * (target - radius.value) / time
        scope.launch { radius.animateTo(target, tween((time * 1000).toInt(), easing = DragCurve)) }
    }

    // Every whole minute the circle's edge passes, whoever moves it.
    LaunchedEffect(radius, state) {
        snapshotFlow { snapped(radius.value, range) }.collect { minutes ->
            if (minutes != state.minutes) {
                state.minutes = minutes
                if (state.dragging) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                changed?.invoke(minutes)
            }
        }
    }

    // The script a finger would follow: out to the most minutes, in to the fewest, back to the start.
    LaunchedEffect(auto, touches, radius) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the first value, and start over once it has settled.
            delay((RESUME_AFTER * 1000).toLong())
            if (state.dragging) return@LaunchedEffect
            settle(first)
            delay((SETTLE * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        fun left(seconds: Float) = (begin + ((turn * LOOP + seconds) * 1e9).toLong() - System.nanoTime()) / 1e9f
        suspend fun until(seconds: Float) = delay((left(seconds) * 1000).toLong().coerceAtLeast(0))
        val stops = intArrayOf(range.last, range.first, first)
        while (true) {
            for (step in stops.indices) {
                until(GRAB_AT[step])
                hold(true)
                until(GRAB_AT[step] + PRESS_TIME)
                // The drag ends on time even when it starts late.
                val release = GRAB_AT[step] + PRESS_TIME + DRAG_TIME[step]
                drag(stops[step], max(0.2f, left(release)))
                until(release)
                settle(stops[step], state.carry)
                hold(false)
            }
            turn += 1
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            // One touch area for the dial and its handle, which moves: a touch that starts on the handle
            // drags it, and one that starts and ends on the map moves the circle's edge there.
            .pointerInput(range) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val grabbed = radius.value
                    val x = down.position.x / density
                    val y = down.position.y / density
                    if (abs(x - (WIDTH / 2f + grabbed)) <= HANDLE_TOUCH / 2f && abs(y - DIAL_Y) <= HANDLE_TOUCH / 2f) {
                        down.consume()
                        state.dragging = true
                        touch()
                        hold(true)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            val at = rubber(grabbed + (change.position.x - down.position.x) / density)
                            scope.launch { radius.snapTo(at) }
                        }
                        state.dragging = false
                        // The circle springs to the nearest minute, carrying the finger's speed.
                        val speed = (tracker.calculateVelocity().x / density).coerceIn(-MAX_FLING, MAX_FLING)
                        settle(snapped(radius.value, range), speed)
                        hold(false)
                        touch()
                    } else {
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val distance = hypot(up.position.x / density - WIDTH / 2f, up.position.y / density - DIAL_Y)
                        if (distance > BADGE / 2f && distance <= DIAL) {
                            touch()
                            settle(snapped(distance, range))
                        }
                    }
                }
            },
    ) {
        Box(Modifier.size(WIDTH.dp, TITLE_HEIGHT.dp), contentAlignment = Alignment.Center) {
            BasicText(question, style = QuestionStyle, maxLines = 2)
        }

        val reach = remember { Path() }
        val dial = remember { Path().apply { addOval(Rect(0f, 0f, DIAL * 2f, DIAL * 2f)) } }
        Canvas(Modifier.offset((WIDTH / 2f - DIAL).dp, (DIAL_Y - DIAL).dp).size((DIAL * 2f).dp)) {
            // Drawn in dp, like the Skia canvas of the React Native version.
            scale(density, density, pivot = Offset.Zero) {
                val edge = max(0f, radius.value)
                val zoom = zoomAt(radius.value, range)
                // The whole map under a blue-grey veil, then the same map in full colour inside the circle.
                clipPath(dial) {
                    drawMap(zoom)
                    drawCircle(Veil, DIAL, Offset(DIAL, DIAL))
                }
                reach.rewind()
                reach.addOval(Rect(DIAL - edge, DIAL - edge, DIAL + edge, DIAL + edge))
                clipPath(reach) { drawMap(zoom) }
                drawCircle(Color.White, edge, Offset(DIAL, DIAL), style = Stroke(3f))
            }
        }

        Box(
            Modifier
                .offset((WIDTH / 2f - BADGE / 2f).dp, (DIAL_Y - BADGE / 2f).dp)
                .size(BADGE.dp)
                .clip(CircleShape)
                .background(Night),
        ) {
            Box(
                Modifier
                    .offset(0.dp, (BADGE / 2f - 13f).dp)
                    .size(BADGE.dp, 26.dp)
                    .graphicsLayer {
                        val grow = 1f + 0.3f * press.value
                        translationY = (-7f - 2f * press.value).dp.toPx()
                        scaleX = grow
                        scaleY = grow
                    },
            ) {
                Digit(parity = 0) { minutesAt(radius.value, range).coerceIn(range.first.toFloat(), range.last.toFloat()) }
                Digit(parity = 1) { minutesAt(radius.value, range).coerceIn(range.first.toFloat(), range.last.toFloat()) }
            }
            Box(Modifier.offset(0.dp, (BADGE / 2f + 6f).dp).size(BADGE.dp, 14.dp), contentAlignment = Alignment.Center) {
                BasicText(unit, style = UnitStyle, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
            }
        }

        // The handle with its halo and the ring it sends out, on the circle's edge.
        Canvas(
            Modifier
                .offset((WIDTH / 2f - HALO).dp, (DIAL_Y - HALO).dp)
                .size((HALO * 2f).dp)
                .graphicsLayer { translationX = radius.value.dp.toPx() }
                .semantics { contentDescription = "Walking time, ${state.minutes} minutes" },
        ) {
            scale(density, density, pivot = Offset.Zero) {
                val center = Offset(HALO, HALO)
                val held = press.value
                drawCircle(
                    Brush.radialGradient(1f / 3f to Blue.copy(alpha = 0.45f), 1f to Blue.copy(alpha = 0f), center = center, radius = HALO),
                    radius = HALO,
                    center = center,
                    alpha = clamp01(held),
                )
                drawCircle(Blue, mix(18f, HALO, ripple.value), center, alpha = 0.5f * (1f - ripple.value), style = Stroke(2f))
                scale(1f + 0.06f * held, pivot = center) {
                    drawRoundRect(
                        lerp(Color(0xFF1F2A2E), Blue, clamp01(held)),
                        Offset(HALO - HANDLE_WIDTH / 2f, HALO - HANDLE_HEIGHT / 2f),
                        Size(HANDLE_WIDTH, HANDLE_HEIGHT),
                        CornerRadius(HANDLE_HEIGHT / 2f),
                    )
                    val ink = lerp(Color(0xFF3EC6C0), Color.White, clamp01(held))
                    val line = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    for (side in listOf(-1f, 1f)) {
                        val chevron = Path().apply {
                            moveTo(HALO + side * 5f, HALO - 4.5f)
                            lineTo(HALO + side * 9.5f, HALO)
                            lineTo(HALO + side * 5f, HALO + 4.5f)
                        }
                        drawPath(chevron, ink, style = line)
                    }
                }
            }
        }

        Box(
            Modifier
                .offset(0.dp, (HEIGHT - BUTTON_HEIGHT).dp)
                .size(WIDTH.dp, BUTTON_HEIGHT.dp)
                .graphicsLayer {
                    val dip = 1f - 0.03f * nextPress.value
                    scaleX = dip
                    scaleY = dip
                }
                .clip(RoundedCornerShape(6.dp))
                .background(Night)
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    // Up in 80 ms, a 40 ms hold, back in 180 ms: a tap.
                    scope.launch {
                        nextPress.animateTo(1f, tween(80, easing = LinearEasing))
                        delay(40)
                        nextPress.animateTo(0f, tween(180, easing = LinearEasing))
                    }
                    onNext?.invoke(state.minutes)
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(nextLabel, style = NextStyle, maxLines = 1)
        }
    }
}

/**
 * One slot of the number's drum. It shows the whole minute of its [parity] (0 even, 1 odd) nearest
 * to [value]: at that minute it is in place, and as [value] leaves it slides up or down and fades,
 * while the other slot arrives. Its text only changes while it is out of sight.
 */
@Composable
private fun Digit(parity: Int, value: () -> Float) {
    fun nearest() = 2 * ((value() - parity) / 2f).roundToInt() + parity
    val shown by remember(parity) { derivedStateOf { nearest() } }
    Box(
        Modifier
            .size(BADGE.dp, 26.dp)
            .graphicsLayer {
                val away = value() - nearest()
                val turn = clamp01((abs(away) - (0.5f - ROLL_WINDOW)) / (2f * ROLL_WINDOW))
                alpha = 1f - turn
                translationY = (-sign(away) * turn * ROLL_TRAVEL).dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("$shown", style = NumberStyle, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}
