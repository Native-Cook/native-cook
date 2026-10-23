package com.ngocdevv.nativecook.blocks.pickers.arcactivitypicker

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The drawing on an activity's chip, in a 24 × 24 box: its main lines, and a second set drawn fainter.
 *
 * @param lines path commands.
 * @param rings circles, each a centre and a radius.
 */
class ActivityIcon(
    lines: String = "",
    rings: List<List<Float>> = emptyList(),
    soft: String = "",
    softRings: List<List<Float>> = emptyList(),
) {
    internal val main = outline(lines, rings)
    internal val faint = outline(soft, softRings)
}

/** @param color the icon's colour, and the selected chip's outline and tint. */
class Activity(val name: String, val color: Color, val icon: ActivityIcon)

private fun outline(commands: String, rings: List<List<Float>>): Path {
    val path = if (commands.isEmpty()) Path() else PathParser().parsePathString(commands).toPath()
    for (ring in rings) path.addOval(Rect(center = Offset(ring[0], ring[1]), radius = ring[2]))
    return path
}

private const val HEIGHT = 700f

// The rows ride an ellipse (a wheel seen at an angle), one every 15°.
private const val STEP = (Math.PI / 12).toFloat()
private const val RADIUS_X = 205f
private const val RADIUS_Y = 362f

// The selected chip's centre, right of the panel's centre line.
private const val CHIP_X = 44f

// Rows further than this from the knob are not drawn; their opacity reaches 0 there.
private const val REACH = 4.5f

// Degrees a row leans per row of distance, and how much it shrinks.
private const val TILT = 4.8f
private const val SHRINK = 0.035f

private const val CHIP_W = 68f
private const val CHIP_SEATED = 76f
private const val CHIP_H = 52f
private const val CHIP_RADIUS = 21f
private const val LABEL_GAP = 28f
private const val LABEL_W = 190f
private const val PLATE_PAD = 10f

// The icons are drawn in a 24 dp box and shown this much larger.
private const val GLYPH = 1.15f
private const val PEN = 1.7f

// The rim is a circle whose leftmost point is `RIM_X` right of the centre line, and the knob sits on it.
private const val RIM_X = 116f
private const val RIM_R = 480f
private const val KNOB_X = 125f
private const val KNOB_W = 46f
private const val KNOB_H = 17f

// Degrees the knob rocks as a row passes.
private const val ROCK = 7f

// Half the height of the stretch of rim that darkens.
private const val GLINT = 64f

// Dragging: dp of travel per row, the travel that makes a touch a drag, and how far a flick carries.
private const val PITCH = 94f
private const val SLOP = 8f
private const val GLIDE = 0.16f
private const val MAX_SPEED = 24f

// autoPlay (seconds): when the wheel sets off, and to which row, counted from the resting one.
private val Script = listOf(1.0f to 2, 2.6f to -2, 4.4f to -3, 5.65f to 0)
private const val LOOP = 8f

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.0005f)

private val WheelSpring = physical(180f, 17.5f)
private val SeatSpring = physical(300f, 20f)

private val Activities = listOf(
    Activity(
        "Running", Color(0xFF3FC59D),
        ActivityIcon(
            "M 7.5 10.8 L 11 8.6 L 14.2 8.8 L 16.4 11.6 L 19.4 11.6 M 14.2 8.8 L 11.4 14 L 14.6 16.6 L 13.8 21 M 11.4 14 L 8.6 17.6 L 4.8 17.6",
            rings = listOf(listOf(15.8f, 4.8f, 2f)), soft = "M 2.6 7.6 L 6 7.6 M 2 12.6 L 4.6 12.6",
        ),
    ),
    Activity(
        "Sleep", Color(0xFF5B9BF0),
        ActivityIcon(
            "M 6.6 13.2 Q 8 14.6 9.4 13.2 M 11.6 13.2 Q 13 14.6 14.4 13.2 M 9.4 17 Q 10.5 17.8 11.6 17",
            rings = listOf(listOf(10.5f, 13.8f, 7.4f)), soft = "M 16.6 2.6 L 21 2.6 L 16.6 7.6 L 21 7.6",
        ),
    ),
    Activity(
        "Skipping", Color(0xFFF08A4B),
        ActivityIcon(
            "M 12 8.4 L 12 14.4 M 12 14.4 L 10 19.4 M 12 14.4 L 14 19.4 M 7.6 12.4 L 12 9.8 L 16.4 12.4",
            rings = listOf(listOf(12f, 4.8f, 2f)), soft = "M 7.6 12.4 C 1.6 15.4 5 22.4 12 22.4 C 19 22.4 22.4 15.4 16.4 12.4",
        ),
    ),
    Activity(
        "Power Training", Color(0xFFEE5A5A),
        ActivityIcon(
            "M 8.6 12 L 15.4 12 M 5.6 7 L 8.6 7 L 8.6 17 L 5.6 17 Z M 15.4 7 L 18.4 7 L 18.4 17 L 15.4 17 Z",
            soft = "M 5.6 9.6 L 2.8 9.6 L 2.8 14.4 L 5.6 14.4 M 18.4 9.6 L 21.2 9.6 L 21.2 14.4 L 18.4 14.4",
        ),
    ),
    Activity(
        "Cycling", Color(0xFFD94FD0),
        ActivityIcon(
            "M 6 15.6 L 9.8 9 L 15.2 9 L 18 15.6 M 9.8 9 L 12 15.6 L 15.2 9 M 8 6.4 L 11 6.4 M 9.4 6.4 L 9.8 9 M 15.2 9 L 14.4 5.8 L 17 5.8",
            softRings = listOf(listOf(6f, 15.6f, 3.9f), listOf(18f, 15.6f, 3.9f)),
        ),
    ),
    Activity(
        "Swimming", Color(0xFF58AEEF),
        ActivityIcon(
            "M 4.4 12.2 L 9.4 7.8 L 13.6 11.4 M 2.5 16 Q 5 13.8 7.5 16 Q 10 18.2 12.5 16 Q 15 13.8 17.5 16 Q 19.6 17.8 21.5 16.4",
            rings = listOf(listOf(16.8f, 7.6f, 2f)),
            soft = "M 2.5 20.2 Q 5 18 7.5 20.2 Q 10 22.4 12.5 20.2 Q 15 18 17.5 20.2 Q 19.6 22 21.5 20.6",
        ),
    ),
    Activity(
        "Tennis", Color(0xFFF5B63C),
        ActivityIcon(
            rings = listOf(listOf(12f, 12f, 8.5f)),
            soft = "M 5.4 6.6 C 9.2 9 9.2 15 5.4 17.4 M 18.6 6.6 C 14.8 9 14.8 15 18.6 17.4",
        ),
    ),
    Activity(
        "Basketball", Color(0xFF9C95F2),
        ActivityIcon(
            rings = listOf(listOf(12f, 12f, 8.5f)),
            soft = "M 12 3.5 L 12 20.5 M 3.5 12 L 20.5 12 M 6 6 C 9.4 9.4 9.4 14.6 6 18 M 18 6 C 14.6 9.4 14.6 14.6 18 18",
        ),
    ),
    Activity(
        "Walking", Color(0xFF58C487),
        ActivityIcon(
            "M 3 16.4 L 3 10.4 C 4.6 11.6 6.6 11.4 8 9.8 L 12.6 14 C 15.2 15 18.6 14.8 20.6 16.6 C 21.4 17.4 21 18.6 20 18.6 L 4.6 18.6 C 3.6 18.6 3 17.6 3 16.4 Z",
            soft = "M 9.6 11.4 L 11.2 10 M 11.4 13 L 13 11.6 M 3 15.4 L 14 15.4",
        ),
    ),
    Activity(
        "Hiking", Color(0xFF33B8C4),
        ActivityIcon(
            "M 2.6 19.6 L 9 7.6 L 13 14.6 L 15.6 11 L 21.4 19.6 Z",
            soft = "M 7.1 11.2 L 9 12.8 L 10.7 10.6", softRings = listOf(listOf(18f, 5.6f, 2f)),
        ),
    ),
)

private val RimColor = Color(0xFFD9D9D9)
private val BeadColor = Color(0xFFBDBDBD)
private val KnobColor = Color(0xFF4A4D52)
private val ChipColor = Color(0xFFEBEBEB)
private val ChipLit = Color(0xFFFDFDFD)
private val PlateColor = Color(0xFFF8F8F8)
private val PlateRim = Color(0xFFE0E0E0)
private val NameColor = Color(0xFF9B9B9B)
private val NameLit = Color(0xFF2B2B2B)

private val NameStyle = TextStyle(
    fontSize = 18.sp,
    fontWeight = FontWeight.Medium,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val Pen = Stroke(PEN, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun wrapped(index: Int, count: Int) = ((index % count) + count) % count

/** How many rows [index] is below the knob (above it when negative), taking the short way round. */
private fun distance(index: Int, wheel: Float, count: Int): Float {
    val away = (index - wheel) % count
    return if (away > count / 2f) away - count else if (away < -count / 2f) away + count else away
}

/**
 * A row's opacity [far] rows from the knob: 1, 0.8, 0.57, 0.33, 0.1 and gone half a row later.
 * A short list also fades where it wraps, half a turn away.
 */
private fun fade(far: Float, count: Int): Float {
    val seam = clamp01((count / 2f - far) / 0.5f)
    if (far < 1f) return min(seam, 1f - 0.2f * far)
    if (far < 4f) return min(seam, 0.8f - 0.7f / 3f * (far - 1f))
    return min(seam, max(0f, 0.1f * (1f - (far - 4f) / 0.5f)))
}

/**
 * A wheel of activities seen from its left flank. The rows ride an arc whose centre is far off the
 * right edge: each is a label and an icon chip, tilted along its spoke and fading towards the ends.
 * Dragging turns the wheel, and the row that reaches the knob on the rim becomes the selection: its
 * chip widens into a tinted pill and its label gets a chip of its own. Ten rows or more wrap around.
 *
 * @param initial Index of the row that rests at the knob.
 * @param autoPlay Turns the wheel through a short script by itself, in a loop.
 * @param onSelect Called with the index of the row the wheel is settling on.
 */
@Composable
fun ArcActivityPicker(
    items: List<Activity> = Activities,
    initial: Int = 4,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val count = max(items.size, 1)
    val start = wrapped(initial, count)
    val auto = autoPlay && !still && items.size > 1
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val chosen by rememberUpdatedState(onSelect)

    // The wheel: the index at the knob, continuous while it turns and unbounded as it wraps.
    val wheel = remember(count, start) { Animatable(start.toFloat()) }
    // How far each row has seated as the selection, 0…1.
    val seats = remember(count, start) { List(count) { Animatable(if (it == start) 1f else 0f) } }
    // The knob's reaction to a row arriving, 1 falling to 0.
    val knock = remember { Animatable(0f) }
    // `held`: a finger is on the wheel. `byHand`: it turns under a finger, not under the script; only then it ticks.
    val hand = remember { object { var held = false; var byHand = false } }
    // Counts touches: each one stops the script and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    val measurer = rememberTextMeasurer()
    val names = remember(items, measurer) { items.map { measurer.measure(it.name, NameStyle, maxLines = 1) } }
    val glows = remember(items) { items.map { SoftRoundRect(it.color.copy(alpha = 0.24f), sigma = 9f) } }
    val knobShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.2f), sigma = 5f) }

    // Sends the wheel to a whole position, carrying on at `velocity` rows per second.
    fun settle(target: Float, velocity: Float = 0f) {
        scope.launch {
            // Under reduced motion nothing eases: the wheel jumps.
            if (still) wheel.snapTo(target) else wheel.animateTo(target, WheelSpring, initialVelocity = velocity)
        }
        chosen?.invoke(wrapped(target.roundToInt(), count))
    }

    // Brings row `index` to the knob the short way round.
    fun select(index: Int) {
        settle((wheel.value + distance(index, wheel.value, count)).roundToInt().toFloat())
    }

    // When another row comes nearest the knob, the seat changes hands and the knob reacts.
    LaunchedEffect(wheel, seats, still) {
        var nearest = wheel.value.roundToInt()
        snapshotFlow { wheel.value.roundToInt() }.drop(1).collect { now ->
            val from = seats[wrapped(nearest, count)]
            val to = seats[wrapped(now, count)]
            nearest = now
            launch { if (still) from.snapTo(0f) else from.animateTo(0f, SeatSpring) }
            launch { if (still) to.snapTo(1f) else to.animateTo(1f, SeatSpring) }
            if (!still) {
                launch {
                    knock.animateTo(1f, tween(50, easing = LinearEasing))
                    knock.animateTo(0f, tween(150, easing = LinearEasing))
                }
            }
            if (hand.byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // The script a finger would follow: it turns the wheel to four rows in turn and ends where it began.
    LaunchedEffect(auto, touches, wheel) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, bring the first row back, and start over from rest.
            delay((RESUME_AFTER * 1000).toLong())
            if (hand.held) return@LaunchedEffect
        }
        hand.byHand = false
        select(start)
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            for ((at, rows) in Script) {
                val due = begin + ((turn * LOOP + at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                select(start + rows)
            }
            turn += 1
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(HEIGHT.dp)
            .clipToBounds()
            .semantics {
                contentDescription = "Activity"
                if (items.isNotEmpty()) stateDescription = items[wrapped(wheel.targetValue.roundToInt(), count)].name
            }
            // One gesture for the whole wheel: a touch catches it, a drag turns it and lets go with
            // momentum, and a touch that never moved selects the row under it.
            .pointerInput(wheel, still) {
                val pitch = PITCH.dp.toPx()
                val slop = SLOP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val from = wheel.value
                    val tracker = VelocityTracker()
                    var zero: Float? = null
                    var here = from
                    scope.launch { wheel.stop() }
                    hand.held = true
                    hand.byHand = true
                    touches += 1
                    tracker.addPosition(down.uptimeMillis, down.position)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val travel = change.position.y - down.position.y
                            if (zero == null && abs(travel) > slop) zero = travel
                            val began = zero ?: continue
                            change.consume()
                            here = from - (travel - began) / pitch
                            scope.launch { wheel.snapTo(here) }
                        }
                    } finally {
                        if (zero == null) {
                            // A tap: find the row at that height, and take it if the touch was on its label or chip.
                            val lift = (down.position.y - size.height / 2f) / RADIUS_Y.dp.toPx()
                            val away = if (abs(lift) < 1f) (asin(lift) / STEP).roundToInt().toFloat() else REACH
                            val chip = size.width / 2f + (CHIP_X + RADIUS_X * (1f - cos(away * STEP))).dp.toPx()
                            val reach = (down.position.x - chip).toDp().value
                            val hit = abs(away) < REACH - 0.5f && reach > -LABEL_W - CHIP_W && reach < CHIP_W
                            settle((wheel.value + if (hit) away else 0f).roundToInt().toFloat())
                        } else {
                            val speed = (-tracker.calculateVelocity().y / pitch).coerceIn(-MAX_SPEED, MAX_SPEED)
                            settle((here + speed * GLIDE).roundToInt().toFloat(), speed)
                        }
                        hand.held = false
                        touches += 1
                    }
                }
            },
    ) {
        val d = density
        val cx = size.width / 2f
        val cy = size.height / 2f
        val turn = wheel.value
        val knocked = knock.value

        // The rim: a hairline with a bead where each row's spoke meets it, and a short stretch beside
        // the knob that darkens when a row arrives.
        val hub = Offset(cx + (RIM_X + RIM_R) * d, cy)
        drawCircle(RimColor, radius = RIM_R * d, center = hub, style = Stroke(1.2f * d))
        for (index in items.indices) {
            val away = distance(index, turn, count)
            if (abs(away) >= REACH) continue
            val y = RADIUS_Y * sin(away * STEP)
            val x = RIM_X + RIM_R - sqrt(RIM_R * RIM_R - y * y)
            drawCircle(BeadColor, radius = 2f * d, center = Offset(cx + x * d, cy + y * d), alpha = fade(abs(away), count))
        }
        if (knocked > 0f) {
            clipRect(top = cy - GLINT * d, bottom = cy + GLINT * d) {
                drawCircle(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to KnobColor.copy(alpha = 0.7f * knocked),
                        1f to Color.Transparent,
                        startY = cy - GLINT * d,
                        endY = cy + GLINT * d,
                    ),
                    radius = RIM_R * d,
                    center = hub,
                    style = Stroke(1.6f * d),
                )
            }
        }

        for (index in items.indices) {
            val away = distance(index, turn, count)
            if (abs(away) >= REACH) continue
            drawRow(items[index], names[index], glows[index], away, seats[index].value, fade(abs(away), count), cx, cy)
        }

        // The knob rocks like a pawl as rows pass it, and dips when one arrives.
        withTransform({
            translate(cx + KNOB_X * d, cy)
            rotate(ROCK * sin(2f * Math.PI.toFloat() * (turn - turn.roundToInt())), pivot = Offset.Zero)
            scale((1f - 0.08f * knocked) * d, (1f + 0.06f * knocked) * d, pivot = Offset.Zero)
        }) {
            drawIntoCanvas { knobShadow.draw(it.nativeCanvas, -KNOB_W / 2f, -KNOB_H / 2f + 3f, KNOB_W, KNOB_H, KNOB_H / 2f) }
            drawRoundRect(KnobColor, Offset(-KNOB_W / 2f, -KNOB_H / 2f), Size(KNOB_W, KNOB_H), CornerRadius(KNOB_H / 2f))
        }
    }
}

/**
 * One activity, drawn around its chip's centre in dp: the label ends 28 dp to the chip's left.
 * Rows above the knob lean clockwise and rows below it the other way, each along its spoke.
 */
private fun DrawScope.drawRow(
    item: Activity,
    name: TextLayoutResult,
    glow: SoftRoundRect,
    away: Float,
    seat: Float,
    alpha: Float,
    cx: Float,
    cy: Float,
) {
    val d = density
    val seen = clamp01(seat)
    val width = mix(CHIP_W, CHIP_SEATED, seat)
    val radius = CornerRadius(mix(CHIP_RADIUS, CHIP_H / 2f, seat))
    val corner = Offset(-width / 2f, -CHIP_H / 2f)
    val box = Size(width, CHIP_H)

    withTransform({
        translate(cx + (CHIP_X + RADIUS_X * (1f - cos(away * STEP))) * d, cy + RADIUS_Y * sin(away * STEP) * d)
        rotate(-TILT * away, pivot = Offset.Zero)
        scale((1f - SHRINK * abs(away)) * d, pivot = Offset.Zero)
    }) {
        val nameW = name.size.width / d
        val nameH = name.size.height / d
        val right = -CHIP_W / 2f - LABEL_GAP
        drawRoundRect(
            PlateColor,
            Offset(right - nameW - PLATE_PAD, -nameH / 2f - 5f),
            Size(nameW + PLATE_PAD * 2f, nameH + 10f),
            CornerRadius(10f),
            alpha = seen * alpha,
        )
        drawRoundRect(
            PlateRim,
            Offset(right - nameW - PLATE_PAD + 0.5f, -nameH / 2f - 4.5f),
            Size(nameW + PLATE_PAD * 2f - 1f, nameH + 9f),
            CornerRadius(9.5f),
            style = Stroke(1f),
            alpha = seen * alpha,
        )
        // The text was measured in pixels.
        withTransform({ scale(1f / d, pivot = Offset.Zero) }) {
            drawText(name, lerp(NameColor, NameLit, seen), Offset((right - nameW) * d, -nameH / 2f * d), alpha = alpha)
        }

        if (seen > 0f) drawIntoCanvas { glow.draw(it.nativeCanvas, corner.x, corner.y + 5f, width, CHIP_H, radius.x, seen * alpha) }
        drawRoundRect(ChipColor, corner, box, radius, alpha = alpha)
        drawRoundRect(ChipLit, corner, box, radius, alpha = seen * alpha)
        drawRoundRect(item.color, corner, box, radius, alpha = 0.17f * seen * alpha)
        drawRoundRect(
            item.color,
            Offset(corner.x + 0.75f, corner.y + 0.75f),
            Size(width - 1.5f, CHIP_H - 1.5f),
            CornerRadius(radius.x - 0.75f),
            style = Stroke(1.5f),
            alpha = 0.85f * seen * alpha,
        )
        withTransform({
            scale(GLYPH * (1f + 0.1f * seat), pivot = Offset.Zero)
            translate(-12f, -12f)
        }) {
            drawPath(item.icon.faint, item.color, alpha = 0.45f * alpha, style = Pen)
            drawPath(item.icon.main, item.color, alpha = alpha, style = Pen)
        }
    }
}
