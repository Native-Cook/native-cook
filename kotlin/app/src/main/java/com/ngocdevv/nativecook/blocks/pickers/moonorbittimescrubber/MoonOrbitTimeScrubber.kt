package com.ngocdevv.nativecook.blocks.pickers.moonorbittimescrubber

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val HEIGHT = 640f
private const val MARGIN = 16f
private const val MAX_WIDTH = 380f
private const val RADIUS = 36f
private const val LAST_MINUTE = 1439f
private const val TAU = (Math.PI * 2).toFloat()

// The pill's run down the right edge: midnight at `TRACK_TOP`, 23:59 at `TRACK_BOTTOM`.
private const val TRACK_TOP = 64f
private const val TRACK_BOTTOM = 508f
private const val PILL_W = 74f
private const val PILL_H = 30f
private const val PILL_INSET = 20f

// How far the pill comes away from the edge while it is held.
private const val PULL = 8f

// The strip along the right edge where a touch takes the pill.
private const val GRIP = 120f

// The pill settles on a multiple of this many minutes, and ticks every `NOTCH` minutes.
private const val SNAP = 5f
private const val NOTCH = 30f

// The model: its centre (left of the card's centre line), the ring's radius, the pyramid's and the moon's size.
private const val MODEL_X = -16f
private const val MODEL_Y = 268f
private const val RING = 96f
private const val SOLID = 24f
private const val MOON = 13f
private const val DOTS = 30

// The moon's seas: centre and radii, in units of its radius.
private val Seas = listOf(
    floatArrayOf(-0.3f, -0.2f, 0.36f, 0.28f),
    floatArrayOf(0.32f, 0.22f, 0.26f, 0.22f),
    floatArrayOf(0.02f, -0.56f, 0.2f, 0.13f),
    floatArrayOf(-0.34f, 0.46f, 0.18f, 0.14f),
)

private const val DATE_Y = 205f
private const val BAR_Y = 568f

// The lit share of the moon's face at midnight and at 23:59, in percent.
private const val SHARE_FROM = 82.2f
private const val SHARE_TO = 88.2f

// autoPlay (seconds): when the pill sets off, to which minute of the day (null: back to where it began), and how long it takes.
private val Script = listOf<Triple<Float, Float?, Float>>(Triple(0.8f, 1320f, 2.2f), Triple(3.9f, 1430f, 0.7f), Triple(5.1f, 240f, 1.6f), Triple(7.2f, null, 1.3f))
private const val LOOP = 9f

// After a touch the loop waits this long, then takes `RETURN_TIME` to glide back.
private const val RESUME_AFTER = 2f
private const val RETURN_TIME = 0.8f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = min(damping / (2f * sqrt(stiffness)), 1f), stiffness = stiffness, visibilityThreshold = 0.01f)

private val FollowSpring = physical(600f, 46f)
private val SnapSpring = physical(300f, 24f)
private val GrabSpring = physical(320f, 20f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val Pale = Color(0xFF8E93C9)
private val Ink = Color(0xFF14163A)

private fun type(size: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ClockStyle = type(14f, Ink, FontWeight.Bold)
private val EndStyle = type(9f, Color.White.copy(alpha = 0.6f), FontWeight.Medium)
private val NowStyle = type(9.5f, Color.White.copy(alpha = 0.85f), FontWeight.SemiBold)
private val DateStyle = type(12f, Ink, FontWeight.Bold)
private val PhaseStyle = type(14f, Color.White, FontWeight.Bold)
private val ShareStyle = type(14f, Pale, FontWeight.Medium)
private val PlaceStyle = type(11f, Pale.copy(alpha = 0.85f))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun smooth(t: Float): Float {
    val x = clamp01(t)
    return x * x * (3f - 2f * x)
}

/** How much of the sky is day at [hour]: dawn runs from 04:36 to 06:36 and dusk from 17:54 to 19:24. */
private fun daylight(hour: Float) = smooth((hour - 4.6f) / 2f) * (1f - smooth((hour - 17.9f) / 1.5f))

private fun clock(time: Float): String {
    val minute = time.roundToInt().coerceIn(0, LAST_MINUTE.toInt())
    return "${minute / 60}".padStart(2, '0') + ":" + "${minute % 60}".padStart(2, '0')
}

/** A point of the model on the screen, in units of the ring's radius, `y` down; [depth] grows towards us. */
private class Spot(val x: Float, val y: Float, val depth: Float)

/**
 * The model's pose at an hour of the day. The ring looks [open] high for 1 wide, from 0.06 (edge-on, at
 * 07:00) to 0.66, and the model turns once round in a day; the moon's path is edge-on at 07:30 and
 * 19:30, away from moonrise and moonset.
 */
private class Pose(hour: Float) {
    val open = 0.36f - 0.3f * cos(TAU * (hour - 7f) / 24f)
    private val turn = TAU * (hour - 1.5f) / 24f
    private val rise = sqrt(1f - open * open)

    private fun project(x: Float, up: Float, z: Float): Spot {
        val depth = z * cos(turn) - x * sin(turn)
        return Spot(x * cos(turn) + z * sin(turn), depth * open - up * rise, depth)
    }

    /** A point of the moon's path, 0 where it rises … 1 where it sets: half an ellipse standing on the ring. */
    fun onPath(along: Float) = project(cos(Math.PI.toFloat() * along), 0.78f * sin(Math.PI.toFloat() * along), 0f)

    val apex get() = project(0f, 1.5f, 0f)

    fun base(corner: Int) = project(cos(TAU * corner / 3f), 0f, sin(TAU * corner / 3f))

    /** How brightly side [side] is lit, 0…1, or null when it faces away from us. */
    fun shade(side: Int): Float? {
        // A side's outward normal leans up by the base's inradius over the pyramid's height.
        val angle = TAU * (side + 0.5f) / 3f
        val length = sqrt(1.5f * 1.5f + 0.25f)
        val normal = project(1.5f * cos(angle) / length, 0f, 1.5f * sin(angle) / length)
        val up = 0.5f / length
        if (open * up + rise * normal.depth <= 0f) return null
        // Light comes from the upper left, a little in front.
        return clamp01(0.5f + 0.5f * (-0.5f * normal.x + 0.62f * up + 0.6f * normal.depth) / 0.997f)
    }
}

/** A fill with a soft edge. Mask filters only work on hardware canvases from API 28; below that it is sharp. */
private class Soft(sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }

    fun fill(scope: DrawScope, path: Path, color: Color) {
        paint.color = color.toArgb()
        scope.drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
    }
}

private fun pill(x: Float, y: Float, width: Float, height: Float) =
    Path().apply { addRoundRect(RoundRect(Rect(x, y, x + width, y + height), CornerRadius(min(width, height) / 2f))) }

private fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    close()
}

/**
 * A time scrubber over a little model of the sky. A white pill with the time rides the card's right
 * edge, midnight at the top and 23:59 at the bottom, and everything follows it: the sky turns from
 * navy through violet to periwinkle, the horizon ring and the dotted path of the moon turn as one
 * solid model around a small pyramid, the moon climbs that path between moonrise and moonset, and
 * the bar at the bottom counts the lit share of its face.
 *
 * @param initial The time the pill starts on, in minutes after midnight.
 * @param moonrise When the moon comes over the horizon, in minutes after midnight.
 * @param moonset When it goes under.
 * @param now Where the "Now" chip sits.
 * @param autoPlay Scrubs through a night by itself, in a loop.
 * @param onChange Called with the time the pill settles on, in minutes after midnight.
 */
@Composable
fun MoonOrbitTimeScrubber(
    initial: Int = 720,
    moonrise: Int = 980,
    moonset: Int = 250,
    now: Int = 570,
    date: String = "Mar 10",
    phase: String = "Waxing Gibbous",
    place: String = "Reykjavik",
    autoPlay: Boolean = false,
    onChange: ((minutes: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still
    val start = initial.toFloat().coerceIn(0f, LAST_MINUTE)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)

    // The time, in minutes after midnight, continuous while the pill moves.
    val time = remember(start) { Animatable(start) }
    // 1 while the pill is held: it grows and comes away from the edge.
    val grab = remember { Animatable(0f) }
    // `held`: a finger has the pill. `byHand`: it moves under a finger, not under the script; only then it ticks.
    val hand = remember { object { var held = false; var byHand = false } }
    // Counts touches: each one stops the script and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    val measurer = rememberTextMeasurer()
    val soft = remember { object { val ground = Soft(4f); val faces = Soft(2.5f); val halo = Soft(7f); val seas = Soft(1f) } }
    val pillShade = remember { SoftRoundRect(Color.Black.copy(alpha = 0.28f), sigma = 6f) }

    // Under reduced motion nothing eases: every change is a jump.
    fun move(target: Float, spec: AnimationSpec<Float>) {
        scope.launch { if (still) time.snapTo(target) else time.animateTo(target, spec) }
    }

    fun hold(down: Boolean) {
        scope.launch { if (still) grab.snapTo(if (down) 1f else 0f) else grab.animateTo(if (down) 1f else 0f, GrabSpring) }
    }

    // Lets the pill go: it settles on the nearest five minutes.
    fun settle(at: Float) {
        val target = ((at / SNAP).roundToInt() * SNAP).coerceIn(0f, LAST_MINUTE)
        move(target, SnapSpring)
        changed?.invoke(target.toInt())
    }

    // A tick for each half hour the pill passes under a finger.
    LaunchedEffect(time) {
        snapshotFlow { (time.value / NOTCH).toInt() }.drop(1).collect {
            if (hand.byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // The script a finger would follow: down into the night, to the end of the day, up to before dawn and back to noon.
    LaunchedEffect(auto, touches, time) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, glide back to the first time, and start over from rest.
            delay((RESUME_AFTER * 1000).toLong())
            if (hand.held) return@LaunchedEffect
            hand.byHand = false
            move(start, tween((RETURN_TIME * 1000).toInt(), easing = EaseInOut))
            delay((RETURN_TIME * 1000).toLong())
        }
        hand.byHand = false
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((turn * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            for ((at, to, length) in Script) {
                val target = to ?: start
                until(at)
                hold(true)
                move(target, tween((length * 1000).toInt(), easing = EaseInOut))
                until(at + length)
                hold(false)
                changed?.invoke(target.toInt())
            }
            turn += 1
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(HEIGHT.dp)
            .semantics {
                contentDescription = "Time"
                stateDescription = clock(time.targetValue)
            }
            // A touch on the right-hand strip takes the pill: it comes to the finger and follows it up and down.
            .pointerInput(time, still) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = min(size.width - MARGIN.dp.toPx() * 2f, MAX_WIDTH.dp.toPx())
                    val right = (size.width + width) / 2f
                    if (down.position.x < right - GRIP.dp.toPx() || down.position.x > right + MARGIN.dp.toPx()) return@awaitEachGesture
                    fun minuteAt(y: Float) = clamp01((y.toDp().value - TRACK_TOP) / (TRACK_BOTTOM - TRACK_TOP)) * LAST_MINUTE
                    var at = minuteAt(down.position.y)
                    hand.held = true
                    hand.byHand = true
                    touches += 1
                    hold(true)
                    move(at, FollowSpring)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            at = minuteAt(change.position.y)
                            move(at, FollowSpring)
                        }
                    } finally {
                        hand.held = false
                        hold(false)
                        settle(at)
                        touches += 1
                    }
                }
            },
    ) {
        val d = density
        val width = min(size.width / d - MARGIN * 2f, MAX_WIDTH)
        val minute = time.value
        val held = grab.value
        // Everything below is drawn in dp, from the card's top-left corner.
        withTransform({
            translate((size.width - width * d) / 2f, 0f)
            scale(d, d, pivot = Offset.Zero)
        }) {
            val card = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, width, HEIGHT), CornerRadius(RADIUS))) }
            clipPath(card) {
                val day = daylight(minute / 60f)
                // The sky: night, with day laid over it, and a wash of violet where one turns into the other.
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0C1033), Color(0xFF0A0D28)), 0f, HEIGHT), size = Size(width, HEIGHT))
                drawRect(Brush.verticalGradient(listOf(Color(0xFF3F45C4), Color(0xFF6A55D6)), 0f, HEIGHT), size = Size(width, HEIGHT), alpha = day)
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFF5A49C9), Color(0xFF7C55D2)), 0f, HEIGHT),
                    size = Size(width, HEIGHT), alpha = 0.6f * 4f * day * (1f - day),
                )

                val center = Offset(width / 2f + MODEL_X, MODEL_Y)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f + 0.07f * day), Color.Transparent), center, 150f),
                    radius = 150f, center = center,
                )
                drawModel(center, minute, day, moonrise, moonset, soft.ground, soft.faces, soft.halo, soft.seas)
                drawEdges(measurer, width, d, moonrise, moonset, now, date)
                drawBar(measurer, width, d, minute, phase, place)

                // The pill, and the nub that ties it to the edge.
                val y = TRACK_TOP + (TRACK_BOTTOM - TRACK_TOP) * minute / LAST_MINUTE
                drawPath(pill(width - 3f, y - 8f, 6f, 16f), Color.White)
                withTransform({
                    translate(width - PILL_INSET - PILL_W / 2f - PULL * held, y)
                    scale(1f + 0.06f * held, pivot = Offset.Zero)
                }) {
                    drawIntoCanvas { pillShade.draw(it.nativeCanvas, -PILL_W / 2f, -PILL_H / 2f + 3f, PILL_W, PILL_H, PILL_H / 2f) }
                    drawPath(pill(-PILL_W / 2f, -PILL_H / 2f, PILL_W, PILL_H), Color.White)
                    label(measurer, clock(minute), ClockStyle, 0f, 0f, d, 0.5f)
                }
            }
            drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(0.5f, 0.5f), Size(width - 1f, HEIGHT - 1f), CornerRadius(RADIUS - 0.5f), style = Stroke(1f))
        }
    }
}

/** Draws [text] in a canvas scaled to dp: its middle at height [y], and its box's fraction [anchor] at [x]. */
private fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, d: Float, anchor: Float): Float {
    val layout = measurer.measure(text, style, maxLines = 1)
    // The text was measured in pixels.
    withTransform({
        translate(x, y)
        scale(1f / d, pivot = Offset.Zero)
    }) {
        drawText(layout, topLeft = Offset(-layout.size.width * anchor, -layout.size.height / 2f))
    }
    return layout.size.width / d
}

/**
 * The horizon ring, the moon's dotted path over it and the pyramid in the middle: one solid model
 * that turns once a day and tips from edge-on in the morning to wide open at night.
 */
private fun DrawScope.drawModel(
    center: Offset,
    minute: Float,
    day: Float,
    moonrise: Int,
    moonset: Int,
    ground: Soft,
    faces: Soft,
    halo: Soft,
    seas: Soft,
) {
    val pose = Pose(minute / 60f)
    val ink = 0.22f + 0.1f * day

    drawOval(
        Color.White.copy(alpha = ink),
        topLeft = Offset(center.x - RING, center.y - RING * pose.open),
        size = Size(RING * 2f, RING * 2f * pose.open),
        style = Stroke(1.2f),
    )

    // The path's far half is drawn before the pyramid and its near half after.
    val far = Path()
    val near = Path()
    for (index in 0 until DOTS) {
        val spot = pose.onPath(index / (DOTS - 1f))
        (if (spot.depth < 0f) far else near).addOval(Rect(center + Offset(spot.x, spot.y) * RING, 1.3f))
    }
    drawPath(far, Color.White.copy(alpha = ink + 0.12f))

    ground.fill(
        this,
        Path().apply { addOval(Rect(center.x - SOLID * 1.1f, center.y - SOLID * 1.1f * pose.open, center.x + SOLID * 1.1f, center.y + SOLID * 1.1f * pose.open)) },
        Color.Black.copy(alpha = 0.16f),
    )

    // The pyramid: a rounded outline in a middle tone, with each face that looks at us laid softly over it.
    val dark = lerp(Color(0xFF5E648F), Color(0xFF8A90DC), day)
    val light = lerp(Color(0xFFB9BEDD), Color(0xFFD9DCFF), day)
    fun corner(spot: Spot) = center + Offset(spot.x, spot.y) * SOLID
    val top = corner(pose.apex)
    val base = List(3) { corner(pose.base(it)) }
    val outline = triangle(base[0], base[1], base[2])
    for (side in 0 until 3) {
        if (pose.shade(side) != null) outline.addPath(triangle(top, base[side], base[(side + 1) % 3]))
    }
    val middle = lerp(dark, light, 0.5f)
    drawPath(outline, middle)
    drawPath(outline, middle, style = Stroke(8f, join = StrokeJoin.Round))
    for (side in 0 until 3) {
        val lit = pose.shade(side) ?: continue
        faces.fill(this, triangle(top, base[side], base[(side + 1) % 3]), lerp(dark, light, lit))
    }

    drawPath(near, Color.White.copy(alpha = ink + 0.3f))

    // The moon rides the path while it is up, and fades where the path meets the ring.
    val up = ((moonset - moonrise + 1440) % 1440).toFloat()
    val since = (minute - moonrise + 1440f) % 1440f
    if (up <= 0f || since > up) return
    val along = since / up
    val spot = pose.onPath(along)
    val radius = MOON * (1f + 0.1f * spot.depth)
    val alpha = clamp01(along / 0.06f) * clamp01((1f - along) / 0.06f)
    val at = center + Offset(spot.x, spot.y) * RING
    val disc = Path().apply { addOval(Rect(at, radius)) }
    halo.fill(this, disc, Color.White.copy(alpha = 0.3f * (1f - day) * alpha))
    drawPath(
        disc,
        Brush.radialGradient(
            0f to Color(0xFFF6F6F8), 0.55f to Color(0xFFC8C9D2), 1f to Color(0xFF8B8D9B),
            center = at + Offset(-0.35f, -0.4f) * radius, radius = 1.6f * radius,
        ),
        alpha = alpha,
    )
    clipPath(disc) {
        for (sea in Seas) {
            val box = Rect(at.x + (sea[0] - sea[2]) * radius, at.y + (sea[1] - sea[3]) * radius, at.x + (sea[0] + sea[2]) * radius, at.y + (sea[1] + sea[3]) * radius)
            seas.fill(this, Path().apply { addOval(box) }, Color(0xFF70727F).copy(alpha = 0.5f * alpha))
        }
    }
}

/** What is fixed along the two edges: the ends of the day, moonset and moonrise, "Now", and the date. */
private fun DrawScope.drawEdges(measurer: TextMeasurer, width: Float, d: Float, moonrise: Int, moonset: Int, now: Int, date: String) {
    fun y(minute: Int) = TRACK_TOP + (TRACK_BOTTOM - TRACK_TOP) * minute / LAST_MINUTE
    val line = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    for ((text, at, up) in listOf(Triple("00:00", TRACK_TOP - 34f, true), Triple("23:59", TRACK_BOTTOM + 32f, false))) {
        val left = width - 12f - 52f
        drawPath(pill(left + 0.5f, at - 8.5f, 51f, 17f), Color.White.copy(alpha = 0.28f), style = Stroke(1f))
        label(measurer, text, EndStyle, left + 21f, at, d, 0.5f)
        val tip = if (up) -1.5f else 1.5f
        val chevron = Path().apply {
            moveTo(left + 38f, at - tip)
            lineTo(left + 41f, at + tip)
            lineTo(left + 44f, at - tip)
        }
        drawPath(chevron, Color.White.copy(alpha = 0.6f), style = line)
    }

    // A small moon with an arrow: down where it sets, up where it rises.
    for ((minute, rising) in listOf(moonset to false, moonrise to true)) {
        val at = Offset(width - 21f, y(minute))
        drawCircle(Pale, radius = 5f, center = at)
        val turn = if (rising) -1f else 1f
        val arrow = Path().apply {
            moveTo(at.x + 9f, at.y - 3.5f * turn)
            lineTo(at.x + 9f, at.y + 3.5f * turn)
            moveTo(at.x + 6.5f, at.y + turn)
            lineTo(at.x + 9f, at.y + 3.5f * turn)
            lineTo(at.x + 11.5f, at.y + turn)
        }
        drawPath(arrow, Pale, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    drawPath(pill(width - 14f - 36f, y(now) - 9.5f, 36f, 19f), Color.White.copy(alpha = 0.16f))
    label(measurer, "Now", NowStyle, width - 14f - 18f, y(now), d, 0.5f)

    drawPath(pill(-3f, DATE_Y - 8f, 6f, 16f), Color.White)
    drawPath(pill(14f, DATE_Y - 13f, 60f, 26f), Color.White)
    label(measurer, date, DateStyle, 44f, DATE_Y, d, 0.5f)
    for ((at, filled) in listOf(120f to false, 300f to true, 470f to false)) {
        if (filled) {
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 2.5f, center = Offset(12f, at))
        } else {
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 2.5f, center = Offset(12f, at), style = Stroke(1f))
        }
    }
}

/** The bar at the bottom: the phase, the lit share of the moon's face, and the place. */
private fun DrawScope.drawBar(measurer: TextMeasurer, width: Float, d: Float, minute: Float, phase: String, place: String) {
    val left = 12f
    val right = width - 12f
    val middle = BAR_Y + 28f
    drawRoundRect(Color.Black.copy(alpha = 0.24f), Offset(left, BAR_Y), Size(right - left, 56f), CornerRadius(22f))
    drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(left + 0.5f, BAR_Y + 0.5f), Size(right - left - 1f, 55f), CornerRadius(21.5f), style = Stroke(1f))

    // A gibbous disc: lit, with a sliver of shadow on its left.
    val tile = Offset(left + 28f, middle)
    drawCircle(Color.White.copy(alpha = 0.1f), radius = 18f, center = tile)
    drawCircle(Color(0xFFDADDF6), radius = 8f, center = tile)
    clipPath(Path().apply { addOval(Rect(tile, 8f)) }) {
        drawCircle(Ink.copy(alpha = 0.6f), radius = 8f, center = tile + Offset(-12.5f, 0f))
    }

    val nameWidth = label(measurer, phase, PhaseStyle, left + 56f, middle - 8f, d, 0f)
    val tenths = ((SHARE_FROM + (SHARE_TO - SHARE_FROM) * minute / LAST_MINUTE) * 10f).roundToInt()
    label(measurer, "${tenths / 10}.${tenths % 10}%", ShareStyle, left + 56f + nameWidth + 5f, middle - 8f, d, 0f)
    label(measurer, place, PlaceStyle, left + 56f, middle + 10f, d, 0f)

    drawRoundRect(Color.White.copy(alpha = 0.1f), Offset(right - 42f, middle - 15f), Size(30f, 30f), CornerRadius(10f))
    val calendar = Path().apply {
        addRoundRect(RoundRect(Rect(right - 33f, middle - 5f, right - 21f, middle + 6f), CornerRadius(2.5f)))
        moveTo(right - 33f, middle - 1.5f)
        lineTo(right - 21f, middle - 1.5f)
    }
    drawPath(calendar, Color.White.copy(alpha = 0.8f), style = Stroke(1.2f))
}
