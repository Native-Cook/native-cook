package com.ngocdevv.nativecook.blocks.pickers.cameramodearcdial

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
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

/** How a mode is drawn on the ring. */
sealed interface CameraModeMark {
    /** A tulip, for close-ups. */
    data object Macro : CameraModeMark

    /** Two peaks and a sun. */
    data object Landscape : CameraModeMark

    /** A word in an outlined capsule. */
    data class Capsule(val text: String) : CameraModeMark

    /** A letter on a white disc. */
    data class Disc(val text: String) : CameraModeMark

    /** Bare monospaced text. */
    data class Plain(val text: String) : CameraModeMark
}

/** @param name Shown in the middle of the dial while the mode is under the index. */
data class CameraMode(val mark: CameraModeMark, val name: String)

private const val WIDTH = 336f
private const val HEIGHT = 304f
private const val CENTER_X = 168f
private const val CENTER_Y = 166f

// Outer radius of the ticks, their length and the index's.
private const val RADIUS = 160f
private const val TICK = 10f
private const val INDEX = 26f

// How far the index reaches outward as a mark clicks in.
private const val KICK = 5f
private const val TICKS = 49

// Radius of the ring of marks, and the degrees from one mark to the next.
private const val RING = 106f
private const val STEP = 19f
private const val CAPSULE_W = 46f
private const val CAPSULE_H = 23f
private const val DISC = 27f

// The mark under the index is this much larger.
private const val GROW = 0.12f

// A tap this close to a mark's centre picks it.
private const val REACH = 20f

// A touch that moves less than this is a tap.
private const val SLOP = 8f

// A flick carries the ring on by its speed times this many seconds.
private const val CARRY = 0.18f

private const val CAPTION_FADE = 120
private const val SWEEP_MAX = 0.9f

// autoPlay (seconds): rest, a click every `STEP_GAP` with one gap skipped halfway, the sweep back.
private const val REST = 0.9f
private const val STEP_GAP = 0.6f
private const val LOOP = 8f

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

private val ClickSpring = physical(190f, 18f)
private val FlingSpring = physical(140f, 20f)

// Ease-in-out that runs 4.5% past its target at 79% of the time and comes back.
private val SweepCurve = CubicBezierEasing(0.5f, 0f, 0.2f, 1.3f)

private val Modes = listOf(
    CameraMode(CameraModeMark.Macro, "Macro"),
    CameraMode(CameraModeMark.Landscape, "Landscape"),
    CameraMode(CameraModeMark.Capsule("AUTO"), "Auto"),
    CameraMode(CameraModeMark.Disc("P"), "Program"),
    CameraMode(CameraModeMark.Disc("S"), "Shutter priority"),
    CameraMode(CameraModeMark.Disc("A"), "Aperture priority"),
    CameraMode(CameraModeMark.Disc("M"), "Manual"),
    CameraMode(CameraModeMark.Plain("C1"), "Custom 1"),
    CameraMode(CameraModeMark.Plain("C2"), "Custom 2"),
    CameraMode(CameraModeMark.Plain("C3"), "Custom 3"),
)

private fun type(size: Float, color: Color, weight: FontWeight, spacing: Float = 0f, family: FontFamily? = null) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFamily = family,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CapsuleStyle = type(11f, Color.White, FontWeight.ExtraBold, 0.4f)
private val DiscStyle = type(14f, Color.Black, FontWeight.Bold)
private val PlainStyle = type(13f, Color.White, FontWeight.Bold, family = FontFamily.Monospace)
private val LabelStyle = type(9.5f, Color.White.copy(alpha = 0.45f), FontWeight.SemiBold, 2f)
private val NameStyle = type(15f, Color.White, FontWeight.SemiBold)

/** The macro tulip, in a 24 dp box around the origin. */
private val Tulip = Path().apply {
    moveTo(-6.5f, -10.5f)
    lineTo(-3.25f, -7.5f)
    lineTo(0f, -10.5f)
    lineTo(3.25f, -7.5f)
    lineTo(6.5f, -10.5f)
    lineTo(6.5f, -4.5f)
    arcTo(Rect(-6.5f, -11f, 6.5f, 2f), 0f, 180f, false)
    close()
    addRect(Rect(-1f, 1.5f, 1f, 11.5f))
    for (side in listOf(-1f, 1f)) {
        moveTo(0f, 11.5f)
        quadraticTo(side * 9f, 11.5f, side * 9f, 3.5f)
        quadraticTo(side * 2f, 5.5f, 0f, 11.5f)
        close()
    }
}

/** The landscape's two peaks and sun, in a 24 dp box around the origin. */
private val Peaks = Path().apply {
    moveTo(-11f, 9f)
    lineTo(-3.5f, -8f)
    lineTo(4f, 9f)
    close()
    moveTo(1.5f, 9f)
    lineTo(6.5f, 0f)
    lineTo(11.5f, 9f)
    close()
    addOval(Rect(5.4f, -9f, 10.6f, -3.8f))
}

/** The point [radius] from the dial's centre, [degrees] clockwise of 12 o'clock. */
private fun point(degrees: Float, radius: Float): Offset {
    val angle = degrees * PI.toFloat() / 180f
    return Offset(CENTER_X + radius * sin(angle), CENTER_Y - radius * cos(angle))
}

/** Past either end the ring gives less and less, up to 0.6 of a mark. */
private fun rubber(turn: Float, last: Int): Float {
    val over = if (turn < 0f) -turn else max(0f, turn - last)
    if (over <= 0f) return turn
    val give = 0.6f * (1f - 1f / (over * 0.9f + 1f))
    return if (turn < 0f) -give else last + give
}

/**
 * The top of a camera's mode dial, drawn flat: a fixed half circle of ticks with a long index at
 * 12 o'clock, and inside it a ring of mode marks that turns in clicks. Dragging around the centre
 * turns the ring, a flick carries it on, and it always settles with one mark under the index;
 * tapping a mark brings it there. The mode's name shows in the middle.
 *
 * @param modes The modes, in the order they sit on the ring (clockwise).
 * @param initial Index of the mode under the index at first, and where [autoPlay] rests.
 * @param label The small label over the mode's name.
 * @param autoPlay Clicks through the modes and sweeps back by itself, in a loop.
 * @param onChange Called with the index of the mode the ring settles on.
 */
@Composable
fun CameraModeArcDial(
    modes: List<CameraMode> = Modes,
    initial: Int = 2,
    label: String = "MODE",
    autoPlay: Boolean = false,
    onChange: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val last = max(0, modes.lastIndex)
    val home = initial.coerceIn(0, last)
    val auto = autoPlay && !still && modes.size > 1
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)

    // The ring's position, in marks: at 3 the fourth mode is under the index.
    val turn = remember(modes.size) { Animatable(home.toFloat()) }
    // The index tick's kick as a mark clicks in under it, 0…1.
    val kick = remember { Animatable(0f) }
    // The mode the ring is heading for.
    var selected by remember(modes.size) { mutableIntStateOf(home) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }
    // `hand`: a finger moved the ring last, so its clicks are felt. `held`: the finger is still down.
    val state = remember { object { var hand = false; var held = false } }
    val nearest by remember(turn, last) { derivedStateOf { turn.value.roundToInt().coerceIn(0, last) } }

    // Marks are measured at one pixel a dp, and drawn on a canvas scaled to the screen's density.
    val measurer = rememberTextMeasurer()
    val texts = remember(modes) {
        modes.map { mode ->
            when (val mark = mode.mark) {
                is CameraModeMark.Capsule -> measurer.measure(mark.text, CapsuleStyle, density = Density(1f))
                is CameraModeMark.Disc -> measurer.measure(mark.text, DiscStyle, density = Density(1f))
                is CameraModeMark.Plain -> measurer.measure(mark.text, PlainStyle, density = Density(1f))
                else -> null
            }
        }
    }

    fun settle(index: Int) {
        if (index == selected) return
        selected = index
        changed?.invoke(index)
    }

    // Turns the ring until mode `index` is under the index: one click on a spring to a neighbour,
    // one sweep that runs a little past and comes back to a mode further away.
    fun select(index: Int) {
        val target = index.coerceIn(0, last)
        val distance = abs(target - turn.value)
        scope.launch {
            when {
                still -> turn.snapTo(target.toFloat())
                distance <= 1.2f -> turn.animateTo(target.toFloat(), ClickSpring)
                else -> turn.animateTo(target.toFloat(), tween((min(SWEEP_MAX, 0.3f + 0.1f * distance) * 1000).toInt(), easing = SweepCurve))
            }
        }
        settle(target)
    }

    fun touch(down: Boolean) {
        state.hand = true
        state.held = down
        touches += 1
    }

    // A mark has come under the index: the index kicks, and a ring turned by hand clicks.
    LaunchedEffect(turn, still) {
        snapshotFlow { nearest }.drop(1).collect {
            if (state.hand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (!still) {
                launch {
                    kick.animateTo(1f, tween(60, easing = LinearEasing))
                    kick.animateTo(0f, tween(220, easing = LinearEasing))
                }
            }
        }
    }

    // The script a finger would follow: click through the modes after the first, pausing halfway,
    // then sweep back.
    LaunchedEffect(auto, touches, home, last) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, turn back to the first mode and start over.
            delay((RESUME_AFTER * 1000).toLong())
            if (state.held) return@LaunchedEffect
            state.hand = false
            select(home)
            delay(((SWEEP_MAX + 0.2f) * 1000).toLong())
        }
        val forward = home < last
        val count = if (forward) last - home else home
        val gap = min(STEP_GAP, 4.8f / (count + 1))
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var round = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((round * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            for (k in 0 until count) {
                val slot = if (k >= (count + 1) / 2) k + 1 else k
                until(REST + slot * gap)
                select(home + if (forward) k + 1 else -(k + 1))
            }
            until(REST + (count + 1) * gap + 0.5f)
            select(home)
            round += 1
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .semantics {
                contentDescription = label
                stateDescription = modes.getOrNull(selected)?.name.orEmpty()
                customActions = listOf(
                    CustomAccessibilityAction("Next mode") {
                        touch(false)
                        select(selected + 1)
                        true
                    },
                    CustomAccessibilityAction("Previous mode") {
                        touch(false)
                        select(selected - 1)
                        true
                    },
                )
            }
            .pointerInput(modes.size, still) {
                val unit = density
                val tracker = VelocityTracker()
                // Degrees clockwise of 12 o'clock at which the point lies from the dial's centre.
                fun degreesOf(at: Offset) = atan2(at.x - CENTER_X * unit, CENTER_Y * unit - at.y) * 180f / PI.toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touch(true)
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
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
                        // The finger's angle wraps at 6 o'clock; the turn it adds up to does not.
                        val angle = degreesOf(at)
                        var delta = angle - lastAngle
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        lastAngle = angle
                        if (!dragging && (at - down.position).getDistance() >= SLOP * unit) {
                            dragging = true
                            grabTurn = turn.value
                        }
                        if (dragging) {
                            swept += delta
                            val next = rubber(grabTurn - swept / STEP, last)
                            scope.launch { turn.snapTo(next) }
                            change.consume()
                        }
                    }
                    touch(false)
                    if (!dragging) {
                        val hit = modes.indices.firstOrNull { i ->
                            val center = point((i - turn.value) * STEP, RING) * unit
                            (at - center).getDistance() < REACH * unit
                        }
                        if (hit != null) select(hit)
                        return@awaitEachGesture
                    }
                    if (still) {
                        select(turn.value.roundToInt())
                        return@awaitEachGesture
                    }
                    // How fast the finger was going round the centre, in marks a second.
                    val velocity = tracker.calculateVelocity()
                    val x = at.x - CENTER_X * unit
                    val y = at.y - CENTER_Y * unit
                    val reach = max(x * x + y * y, (40f * unit) * (40f * unit))
                    val speed = -(x * velocity.y - y * velocity.x) / reach * 180f / PI.toFloat() / STEP
                    val target = (turn.value + speed * CARRY).roundToInt().coerceIn(0, last)
                    scope.launch { turn.animateTo(target.toFloat(), FlingSpring, initialVelocity = speed) }
                    settle(target)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Drawn in dp, like the other versions.
            scale(density, density, pivot = Offset.Zero) {
                drawTicks(kick.value)
                modes.forEachIndexed { index, mode -> drawMark(mode.mark, texts[index], index - turn.value) }
            }
        }

        At(label, LabelStyle, CENTER_Y - 17f, 12f) { 1f }
        modes.forEachIndexed { index, mode ->
            val shown by animateFloatAsState(
                if (nearest == index) 1f else 0f,
                tween(if (still) 0 else CAPTION_FADE, easing = LinearEasing),
                label = "caption",
            )
            At(mode.name, NameStyle, CENTER_Y - 3f, 20f) { shown }
        }
    }
}

/** A centred line of text in a box [height] dp tall whose top is at [y]. */
@Composable
private fun BoxScope.At(text: String, style: TextStyle, y: Float, height: Float, opacity: () -> Float) {
    Box(
        Modifier.offset(0.dp, y.dp).size(WIDTH.dp, height.dp).graphicsLayer { alpha = opacity() },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

/** The fixed half circle of ticks; the one at 12 o'clock is the index. */
private fun DrawScope.drawTicks(kick: Float) {
    for (i in 0 until TICKS) {
        if (i == TICKS / 2) continue
        val degrees = -90f + 180f * i / (TICKS - 1)
        drawLine(Color.White.copy(alpha = 0.9f), point(degrees, RADIUS - TICK), point(degrees, RADIUS), strokeWidth = 1.5f, cap = StrokeCap.Round)
    }
    drawLine(Color.White, point(0f, RADIUS - INDEX), point(0f, RADIUS + KICK * kick), strokeWidth = 1.5f + 0.5f * kick, cap = StrokeCap.Round)
}

/**
 * One mode's mark, [away] marks clockwise of the index. Its top points along the ring, so it stands
 * upright at 9 o'clock and reads top to bottom under the index.
 */
private fun DrawScope.drawMark(mark: CameraModeMark, text: TextLayoutResult?, away: Float) {
    val degrees = away * STEP
    if (abs(degrees) >= 200f) return
    val grow = 1f + GROW * max(0f, 1f - abs(away))
    // Marks more than one place from the index dim a little, down to 60% four places away.
    val opacity = 1f - 0.4f * ((abs(away) - 1f) / 3f).coerceIn(0f, 1f)
    val center = point(degrees, RING)
    withTransform({
        translate(center.x, center.y)
        rotate(degrees + 90f, pivot = Offset.Zero)
        scale(grow, grow, pivot = Offset.Zero)
    }) {
        when (mark) {
            CameraModeMark.Macro -> drawPath(Tulip, Color.White, alpha = opacity)
            CameraModeMark.Landscape -> drawPath(Peaks, Color.White, alpha = opacity)
            is CameraModeMark.Capsule -> drawRoundRect(
                Color.White,
                topLeft = Offset(-CAPSULE_W / 2f + 0.75f, -CAPSULE_H / 2f + 0.75f),
                size = Size(CAPSULE_W - 1.5f, CAPSULE_H - 1.5f),
                cornerRadius = CornerRadius(CAPSULE_H / 2f),
                style = Stroke(1.5f),
                alpha = opacity,
            )
            is CameraModeMark.Disc -> drawCircle(Color.White, radius = DISC / 2f, center = Offset.Zero, alpha = opacity)
            is CameraModeMark.Plain -> Unit
        }
        if (text != null) {
            drawText(text, topLeft = Offset(-text.size.width / 2f, -text.size.height / 2f), alpha = opacity)
        }
    }
}
