package com.ngocdevv.nativecook.blocks.pickers.weatherfandial

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** One piece of an icon's outline, in a box of ±24 dp around its middle. */
sealed interface WeatherStroke {
    data class Move(val x: Float, val y: Float) : WeatherStroke

    data class Line(val x: Float, val y: Float) : WeatherStroke

    /** An arc round ([x], [y]): the angle it starts at and the degrees it sweeps, clockwise from 3 o'clock. */
    data class Arc(val x: Float, val y: Float, val radius: Float, val start: Float, val sweep: Float) : WeatherStroke

    data object Close : WeatherStroke
}

/**
 * @param temperature Degrees shown next to the name.
 * @param icon The icon's outline.
 */
data class WeatherCondition(val name: String, val temperature: Int, val icon: List<WeatherStroke>)

private const val WIDTH = 340f
private const val HEIGHT = 320f

// The knob's centre: the wheel turns about it.
private const val KNOB_X = 170f
private const val KNOB_Y = 192f
private const val KNOB = 27f
private const val BEZEL = 36f

// The fan: its radius, the radius of the bite round the knob, and the degrees it opens to each side.
private const val RADIUS = 176f
private const val BITE = 43f
private const val SPREAD = 62f

// Degrees one condition takes on the wheel.
private const val SEGMENT = 45f
private const val ICON_RADIUS = 112f
private const val ICON_SCALE = 1.3f
private const val GLOW = 120f

private const val LEAD_Y = 262f
private const val READOUT_Y = 294f

// How far a read-out line has slid when its segment is one place from the pointer.
private const val READOUT_SHIFT = 28f

// A touch that moves less than this is a tap.
private const val SLOP = 8f

// A flick carries the wheel on by its speed times this many seconds.
private const val CARRY = 0.18f

// The knob's tap: the arrow's turn, and the speed the wheel is knocked with, in segments a second.
private const val REFRESH_TIME = 0.6f
private const val SHIVER = 2.4f

// The spin back to the first condition.
private const val BACK_TIME = 1f
private const val LOOP = 7f

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

// Overshoots a segment by about a ninth and settles in 0.45 s.
private val CatchSpring = physical(240f, 18f)
private val FlickSpring = physical(110f, 14f)
private val RefreshCurve = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

// Ease-in-out that runs 4.5% past its target at 79% of the time and comes back.
private val BackCurve = CubicBezierEasing(0.5f, 0f, 0.2f, 1.3f)

private val Mustard = Color(0xFFC9B458)
private val Dull = Color(0xFF3A3C44)
private val Cream = Color(0xFFF4EFDC)

private fun move(x: Float, y: Float) = WeatherStroke.Move(x, y)

private fun line(x: Float, y: Float) = WeatherStroke.Line(x, y)

private fun arc(x: Float, y: Float, radius: Float, start: Float, sweep: Float) = WeatherStroke.Arc(x, y, radius, start, sweep)

/** A cloud about 38 dp wide whose middle is at ([x], [y]). */
private fun cloud(x: Float, y: Float) = listOf(
    move(x - 12f, y + 9f), line(x + 12f, y + 9f),
    arc(x + 12f, y + 2f, 7f, 90f, -180f),
    arc(x + 1.8f, y - 3.5f, 10.31f, -8.4f, -168.5f),
    arc(x - 12f, y + 2f, 7f, -60f, -210f),
    WeatherStroke.Close,
)

/** Short strokes under a cloud, each from a point down by ([dx], [dy]). */
private fun drops(from: List<Pair<Float, Float>>, dx: Float, dy: Float) =
    from.flatMap { (x, y) -> listOf(move(x, y), line(x + dx, y + dy)) }

/** A crescent open to the upper right. */
private val Moon = listOf(move(14.85f, 2.07f), arc(0f, 0f, 15f, 7.9f, 254.1f), arc(7f, -7f, 12f, 220.9f, -171.8f), WeatherStroke.Close)

/** The knob's arrow: three quarters of a circle, clockwise, with a head at its end. */
private val Refresh = listOf(
    move(9.77f, -3.56f), arc(0f, 0f, 10.4f, -20f, 275f),
    move(-6.9f, -5.8f), line(-2.7f, -10.05f), line(-8.5f, -11.6f),
)

private val Conditions = listOf(
    WeatherCondition("Clear", 24, Moon),
    WeatherCondition("Cloudy", 21, cloud(-3f, 3f) + listOf(move(-0.4f, -10.2f), arc(8f, -7f, 9f, 201f, 205f))),
    WeatherCondition("Drizzle", 18, cloud(0f, -5f) + drops(listOf(-8f to 12f, 0f to 16f, 8f to 12f), 0.4f, 0.8f)),
    WeatherCondition("Rain", 17, cloud(0f, -6f) + drops(listOf(-7f to 10f, 1f to 10f, 9f to 10f), -3f, 8f)),
    WeatherCondition("Storm", 16, cloud(0f, -7f) + listOf(move(3f, 7f), line(-3f, 14f), line(3f, 14f), line(-2f, 22f))),
    WeatherCondition(
        "Snow", -1,
        cloud(0f, -6f) + listOf(
            move(-6.4f, 13f), arc(-8f, 13f, 1.6f, 0f, 359.9f),
            move(1.6f, 17f), arc(0f, 17f, 1.6f, 0f, 359.9f),
            move(9.6f, 13f), arc(8f, 13f, 1.6f, 0f, 359.9f),
        ),
    ),
    WeatherCondition("Fog", 9, cloud(0f, -8f) + listOf(move(-13f, 11f), line(13f, 11f), move(-7f, 18f), line(7f, 18f))),
    WeatherCondition(
        "Windy", 19,
        listOf(
            move(-16f, -5f), line(3f, -5f), arc(3f, -10f, 5f, 90f, -250f),
            move(-16f, 3f), line(11f, 3f), arc(11f, -2f, 5f, 90f, -250f),
            move(-16f, 11f), line(1f, 11f), arc(1f, 16f, 5f, -90f, 250f),
        ),
    ),
)

private fun outline(strokes: List<WeatherStroke>) = Path().apply {
    for (stroke in strokes) {
        when (stroke) {
            is WeatherStroke.Move -> moveTo(stroke.x, stroke.y)
            is WeatherStroke.Line -> lineTo(stroke.x, stroke.y)
            is WeatherStroke.Arc -> arcTo(
                Rect(stroke.x - stroke.radius, stroke.y - stroke.radius, stroke.x + stroke.radius, stroke.y + stroke.radius),
                stroke.start, stroke.sweep, false,
            )
            WeatherStroke.Close -> close()
        }
    }
}

/** The point [radius] from the knob's centre, [degrees] clockwise of straight up. */
private fun point(degrees: Float, radius: Float): Offset {
    val angle = degrees * PI.toFloat() / 180f
    return Offset(KNOB_X + radius * sin(angle), KNOB_Y - radius * cos(angle))
}

/** The fan's outline: a sector of the wheel with a round bite for the knob. */
private val Fan = Path().apply {
    arcTo(Rect(KNOB_X - RADIUS, KNOB_Y - RADIUS, KNOB_X + RADIUS, KNOB_Y + RADIUS), -90f - SPREAD, SPREAD * 2f, true)
    arcTo(Rect(KNOB_X - BITE, KNOB_Y - BITE, KNOB_X + BITE, KNOB_Y + BITE), -90f + SPREAD, -SPREAD * 2f, false)
    close()
}

private val PointerTip = Path().apply {
    moveTo(KNOB_X - 12f, KNOB_Y - RADIUS - 6f)
    lineTo(KNOB_X + 12f, KNOB_Y - RADIUS - 6f)
    lineTo(KNOB_X, KNOB_Y - RADIUS + 7f)
    close()
}

private val RefreshPath = outline(Refresh)

private fun type(size: Float, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = FontWeight.Thin,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val LeadStyle = type(17f, Color(0xFF82848F))
private val ReadoutStyle = type(30f, Color(0xFFB0B2BC))

/** How many segments [index] is clockwise of the pointer, by the shorter way round. */
private fun away(index: Int, turn: Float, count: Int): Float {
    var d = (index - turn) % count
    if (d > count / 2f) d -= count
    if (d < -count / 2f) d += count
    return d
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

/**
 * A weather dial sunk into a dark surface. The fan is the part in sight of a wheel of conditions
 * centred on the knob: dragging across it turns the wheel under a fixed pointer, a flick carries it
 * on, and it catches on the nearest segment with a spring. The segment under the pointer lights up
 * and the line below reads it out. Tapping a side segment brings it in; tapping the knob spins its
 * arrow and shakes the wheel.
 *
 * @param conditions The conditions, clockwise round the wheel; it has no end, so the last one is left of the first.
 * @param initial Index of the condition under the pointer at first.
 * @param lead The small line over the read-out.
 * @param autoPlay Turns the wheel one segment, flicks it two more, taps the knob and spins back by itself, in a loop.
 * @param onChange Called with the index of the condition the wheel settles on.
 * @param onRefresh Called when the knob is tapped.
 */
@Composable
fun WeatherFanDial(
    conditions: List<WeatherCondition> = Conditions,
    initial: Int = 0,
    lead: String = "It's",
    autoPlay: Boolean = false,
    onChange: ((index: Int) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val count = max(1, conditions.size)
    val home = initial.coerceIn(0, count - 1)
    val auto = autoPlay && !still && conditions.size > 3
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)
    val refreshed by rememberUpdatedState(onRefresh)

    // The wheel's position, in segments; it keeps counting past the last condition.
    val turn = remember(conditions.size) { Animatable(home.toFloat()) }
    // The knob's arrow, in degrees, and the knob's dip under the finger.
    val arrow = remember { Animatable(0f) }
    val knobPress = remember { Animatable(0f) }
    // The pointer's flicker as a segment catches.
    val flicker = remember { Animatable(0f) }
    // The segment the wheel is heading for.
    var heading by remember(conditions.size) { mutableIntStateOf(home) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }
    // `hand`: a finger turned the wheel last, so each segment is felt. `held`: the finger is still down.
    val state = remember { object { var hand = false; var held = false } }

    val icons = remember(conditions) { conditions.map { outline(it.icon) } }
    val shade = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = Color.Black.copy(alpha = 0.6f).toArgb()
            // Hardware canvases only honour mask filters from API 28.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(7f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val knobShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.55f), sigma = 5f) }

    fun wrap(index: Int) = ((index % count) + count) % count

    fun label(condition: WeatherCondition) = "${condition.name} & ${condition.temperature}°"

    fun head(target: Int) {
        if (target == heading) return
        heading = target
        if (conditions.isNotEmpty()) changed?.invoke(wrap(target))
    }

    // Turns the wheel `by` segments from where it is heading: one on the catch spring, more on a softer one.
    fun step(by: Int) {
        if (by == 0) return
        val target = heading + by
        scope.launch {
            if (still) turn.snapTo(target.toFloat()) else turn.animateTo(target.toFloat(), if (abs(by) == 1) CatchSpring else FlickSpring)
        }
        head(target)
    }

    // The knob: its arrow goes once round and the wheel shakes in its catch.
    fun refresh() {
        if (!still) {
            scope.launch {
                knobPress.animateTo(1f, tween(80, easing = LinearEasing))
                knobPress.animateTo(0f, tween(200, easing = LinearEasing))
            }
            scope.launch {
                arrow.snapTo(0f)
                arrow.animateTo(360f, tween((REFRESH_TIME * 1000).toInt(), easing = RefreshCurve))
            }
            scope.launch { turn.animateTo(heading.toFloat(), CatchSpring, initialVelocity = SHIVER) }
        }
        refreshed?.invoke()
    }

    // Spins to segment `target` in one sweep that runs a little past and comes back.
    fun spin(target: Int) {
        scope.launch { turn.animateTo(target.toFloat(), tween((BACK_TIME * 1000).toInt(), easing = BackCurve)) }
        head(target)
    }

    fun touch(down: Boolean) {
        state.hand = true
        state.held = down
        touches += 1
    }

    // A segment has come under the pointer: the pointer flickers like a detent catching.
    LaunchedEffect(turn, still) {
        snapshotFlow { turn.value.roundToInt() }.drop(1).collect {
            if (state.hand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (!still) {
                launch {
                    flicker.animateTo(1f, tween(50, easing = LinearEasing))
                    flicker.animateTo(0f, tween(250, easing = LinearEasing))
                }
            }
        }
    }

    // The script a finger would follow: one segment on, a flick of two more, the knob, and back.
    LaunchedEffect(auto, touches, home, turn) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, turn back to the first condition by the shorter way and start over.
            delay((RESUME_AFTER * 1000).toLong())
            if (state.held) return@LaunchedEffect
            state.hand = false
            spin(home + ((turn.value - home) / count).roundToInt() * count)
            delay(((BACK_TIME + 0.2f) * 1000).toLong())
            // Counts the wheel's turns from the first condition again; what shows does not change.
            heading = home
            turn.snapTo(home.toFloat())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var round = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((round * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            until(1.0f)
            step(1)
            until(2.4f)
            step(2)
            until(4.1f)
            refresh()
            until(5.2f)
            spin(home)
            round += 1
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .semantics {
                contentDescription = lead
                stateDescription = conditions.getOrNull(wrap(heading))?.let(::label).orEmpty()
                customActions = listOf(
                    CustomAccessibilityAction("Next") {
                        touch(false)
                        step(1)
                        true
                    },
                    CustomAccessibilityAction("Previous") {
                        touch(false)
                        step(-1)
                        true
                    },
                )
            }
            .pointerInput(conditions.size, still) {
                val unit = density
                val tracker = VelocityTracker()
                // Degrees clockwise of straight up at which the point lies from the knob.
                fun degreesOf(at: Offset) = atan2(at.x - KNOB_X * unit, KNOB_Y * unit - at.y) * 180f / PI.toFloat()
                fun reachOf(at: Offset) = hypot(at.x - KNOB_X * unit, at.y - KNOB_Y * unit) / unit
                fun inFan(at: Offset) = reachOf(at) > BITE && reachOf(at) < RADIUS + 12f && abs(degreesOf(at)) < SPREAD + 4f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touch(true)
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val onFan = inFan(down.position)
                    var at = down.position
                    var lastAngle = degreesOf(at)
                    var swept = 0f
                    var grabTurn = 0f
                    var dragging = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        at = change.position
                        tracker.addPosition(change.uptimeMillis, at)
                        // The finger's angle wraps behind the knob; the turn it adds up to does not.
                        val angle = degreesOf(at)
                        var delta = angle - lastAngle
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        lastAngle = angle
                        if (!dragging && (at - down.position).getDistance() >= SLOP * unit) {
                            dragging = true
                            grabTurn = turn.value
                        }
                        if (dragging && onFan) {
                            swept += delta
                            val next = grabTurn - swept / SEGMENT
                            scope.launch { turn.snapTo(next) }
                            change.consume()
                        }
                    }
                    touch(false)
                    if (!dragging) {
                        if (reachOf(at) <= BEZEL + 4f) {
                            refresh()
                        } else if (inFan(at)) {
                            // A side segment comes under the pointer.
                            step((degreesOf(at) / SEGMENT + turn.value).roundToInt() - heading)
                        }
                        return@awaitEachGesture
                    }
                    if (!onFan) return@awaitEachGesture
                    // How fast the finger was going round the knob, in segments a second.
                    val velocity = tracker.calculateVelocity()
                    val x = at.x - KNOB_X * unit
                    val y = at.y - KNOB_Y * unit
                    val reach = max(x * x + y * y, (50f * unit) * (50f * unit))
                    val speed = if (still) 0f else -(x * velocity.y - y * velocity.x) / reach * 180f / PI.toFloat() / SEGMENT
                    val target = (turn.value + speed * CARRY).roundToInt()
                    scope.launch {
                        if (still) turn.snapTo(target.toFloat()) else turn.animateTo(target.toFloat(), CatchSpring, initialVelocity = speed)
                    }
                    head(target)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Drawn in dp, like the other versions.
            scale(density, density, pivot = Offset.Zero) {
                drawFan(turn.value, flicker.value, icons, shade)
                drawKnob(arrow.value, knobPress.value, knobShadow)
            }
        }

        Line(lead, LeadStyle, LEAD_Y - 11f, 22f) { 1f to 0f }
        conditions.forEachIndexed { index, condition ->
            // The lines ride with the wheel: the one that leaves slides away and fades as the next one slides in.
            Line(label(condition), ReadoutStyle, READOUT_Y - 19f, 38f) {
                val d = away(index, turn.value, count)
                (1f - min(1f, abs(d) * 2f)) to d * READOUT_SHIFT
            }
        }
    }
}

/** A centred line of text in a box [height] dp tall whose top is at [y]; [look] gives its opacity and how far it is shifted down. */
@Composable
private fun BoxScope.Line(text: String, style: TextStyle, y: Float, height: Float, look: () -> Pair<Float, Float>) {
    Box(
        Modifier
            .offset(0.dp, y.dp)
            .size(WIDTH.dp, height.dp)
            .graphicsLayer {
                val (opacity, shift) = look()
                alpha = opacity
                translationY = shift.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

private fun DrawScope.drawFan(turn: Float, flicker: Float, icons: List<Path>, shade: Paint) {
    drawPath(Fan, Color(0xFF1D1F25))
    clipPath(Fan) {
        // The light that falls from the pointer onto the segment under it.
        val top = point(0f, RADIUS - 6f)
        val seated = 1f - min(1f, abs(turn - turn.roundToInt()) * 2f)
        val light = Mustard.copy(alpha = ((0.2f + 0.14f * seated) * (1f + 0.5f * flicker)).coerceIn(0f, 1f))
        drawCircle(Brush.radialGradient(listOf(light, Mustard.copy(alpha = 0f)), center = top, radius = GLOW), GLOW, top)

        val count = max(1, icons.size)
        icons.forEachIndexed { index, icon ->
            val d = away(index, turn, count)
            if (abs(d) >= 2.2f) return@forEachIndexed
            val degrees = d * SEGMENT
            // The divider on this segment's clockwise side, with a faint lit edge beside it.
            drawLine(Color(0xFF15161A), point(degrees + SEGMENT / 2f, BITE), point(degrees + SEGMENT / 2f, RADIUS), strokeWidth = 1.5f)
            drawLine(Color.White.copy(alpha = 0.03f), point(degrees + SEGMENT / 2f + 0.5f, BITE), point(degrees + SEGMENT / 2f + 0.5f, RADIUS), strokeWidth = 1f)

            // The icon stands along its radius, and lights as it comes under the pointer.
            val lit = smooth(1f - min(1f, abs(d) * 2f))
            val center = point(degrees, ICON_RADIUS)
            val grow = ICON_SCALE * (1f + 0.06f * lit)
            withTransform({
                translate(center.x, center.y)
                rotate(degrees, pivot = Offset.Zero)
                scale(grow, grow, pivot = Offset.Zero)
            }) {
                drawPath(icon, lerp(Dull, Cream, lit), style = Stroke(3.2f + 0.8f * lit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // The fan is sunk into the surface: its edges cast a shadow inward.
        drawIntoCanvas { it.nativeCanvas.drawPath(Fan.asAndroidPath(), shade) }
    }
    drawPath(
        Fan,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.1f)), startY = KNOB_Y - RADIUS * 0.6f, endY = KNOB_Y),
        style = Stroke(1f),
    )

    // The pointer: a line from the triangle's tip down to the knob.
    drawLine(
        Mustard.copy(alpha = 0.75f + 0.25f * flicker),
        Offset(KNOB_X, KNOB_Y - RADIUS + 4f),
        Offset(KNOB_X, KNOB_Y - BITE),
        strokeWidth = 1f + 0.6f * flicker,
    )
    drawPath(PointerTip, Mustard)
}

/** The raised round button the wheel turns about, with its refresh arrow. */
private fun DrawScope.drawKnob(arrow: Float, press: Float, shadow: SoftRoundRect) {
    val center = Offset(KNOB_X, KNOB_Y)
    // A faint indigo light under it.
    withTransform({
        translate(KNOB_X, KNOB_Y + 16f)
        scale(62f, 46f, pivot = Offset.Zero)
    }) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0x4D4B3FD9), Color(0x004B3FD9)), center = Offset.Zero, radius = 1f),
            1f,
            Offset.Zero,
        )
    }
    drawCircle(Brush.verticalGradient(listOf(Color(0xFF17181C), Color(0xFF222327)), startY = KNOB_Y - BEZEL, endY = KNOB_Y + BEZEL), BEZEL, center)

    val dip = 1f - 0.04f * press
    scale(dip, dip, pivot = center) {
        drawIntoCanvas { shadow.draw(it.nativeCanvas, KNOB_X - KNOB, KNOB_Y - KNOB + 4f, KNOB * 2f, KNOB * 2f, KNOB) }
        drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A3B42), Color(0xFF24252A)), startY = KNOB_Y - KNOB, endY = KNOB_Y + KNOB), KNOB, center)
        drawCircle(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f)), startY = KNOB_Y - KNOB, endY = KNOB_Y),
            KNOB - 0.5f,
            center,
            style = Stroke(1f),
        )
        withTransform({
            translate(KNOB_X, KNOB_Y)
            rotate(arrow, pivot = Offset.Zero)
        }) {
            drawPath(RefreshPath, Color(0xFF5A5C66), style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
