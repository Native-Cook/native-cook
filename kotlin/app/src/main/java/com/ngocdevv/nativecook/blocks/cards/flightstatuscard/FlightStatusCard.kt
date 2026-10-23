package com.ngocdevv.nativecook.blocks.cards.flightstatuscard

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One end of the flight. */
data class FlightStop(val code: String, val city: String, val time: String)

/** One of the three small fields at the bottom of the panel. */
data class FlightField(val label: String, val value: String)

private const val SIZE = 340f
private const val RADIUS = 28f

// The photo, 400 × 267, hangs over the card's sides and top so that it can slide and grow.
private const val PHOTO_W = 400f
private const val PHOTO_H = 267f
private const val PHOTO_Y = -60f

// How far the photo slides to either side of the middle, and how much it grows by the landing.
private const val DRIFT = 16f
private const val ZOOM = 0.05f

// Where the card starts to fade from the photo into a plain dusk.
private const val FADE_Y = 166f

private const val PANEL_W = 324f
private const val PANEL_H = 160f
private const val PANEL_Y = 172f

// The line the badge travels along, inside the panel, and the badge's radius.
private const val LINE_X = 98f
private const val LINE_W = 128f
private const val LINE_Y = 44f
private const val BADGE = 11f
private const val TRAVEL = LINE_W - BADGE * 2f

// The share of the flight at which the plane has left the gate.
private const val AIRBORNE = 0.04f

// The chip's flip: the old status tips out, then the new one tips in. Seconds.
private const val FLIP_OUT = 0.16f
private const val FLIP_IN = 0.22f

// Length of the cross-fade that replaces the flip under reduced motion.
private const val FADE_TIME = 0.2f
private const val GREEN_IN = 0.25f
private const val GREEN_OUT = 0.2f

// The landing knocks the badge's scale at this speed (per second); a spring of mass 1 brings it back.
private const val BOUNCE_KICK = 5f
private const val BOUNCE_STIFFNESS = 260f
private const val BOUNCE_DAMPING = 12f

// The clocks of the flip and of the bounce run this long: both are over well before.
private const val CLOCK = 1.5f

// autoPlay (seconds): take-off, the flight, a look at the landing, a quick rewind, a rest.
private const val TAKE_OFF_AT = 0.8f
private const val FLIGHT_TIME = 4.6f
private const val REWIND_AT = 7.2f
private const val REWIND_TIME = 0.7f
private const val LOOP = 9f

// After a touch the loop waits this long, then rewinds and starts over.
private const val RESUME_AFTER = 2f

private val FlightCurve = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
private val RewindCurve = CubicBezierEasing(0.5f, 0f, 0.2f, 1f)

private val Ink = Color(0xFF121316)
private val Grey = Color(0xFF8B8F97)
private val Line = Color(0xFFECECEE)

// The dot in the chip: amber at the gate, sky blue in the air, green on the ground.
private val ChipDots = listOf(Color(0xFFFBBF24), Color(0xFF7DD3FC), Color(0xFF4ADE80))

// A plane seen from above, nose up, in a 20 × 20 box.
private val Plane = PathParser().parsePathString(
    "M 10 1.5 Q 11.4 1.5 11.4 4 L 11.4 7.6 L 18 11.6 L 18 13.4 L 11.4 11.4 L 11.4 15.4 L 13.6 17 L 13.6 18.4 L 10 17.4 " +
        "L 6.4 18.4 L 6.4 17 L 8.6 15.4 L 8.6 11.4 L 2 13.4 L 2 11.6 L 8.6 7.6 L 8.6 4 Q 8.6 1.5 10 1.5 Z",
).toPath()
private val Dashes = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))

private val Fields = listOf(FlightField("Airline", "Nimbus Air"), FlightField("Flight Code", "NB 880"), FlightField("Class", "Economy"))

private fun type(size: Float, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val SmallStyle = type(10.5f, Grey)
private val CodeStyle = type(22f, Ink, FontWeight.Bold, -0.2f)
private val CityStyle = type(12f, Color(0xFF6F7379))
private val ValueStyle = type(12.5f, Ink, FontWeight.Medium)
private val ChipStyle = type(11.5f, Color.White, FontWeight.SemiBold)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

/** 0 at the gate, 1 in the air, 2 landed. */
private fun statusAt(progress: Float) = if (progress >= 0.999f) 2 else if (progress > AIRBORNE) 1 else 0

/** How far the knocked spring is from rest [t] seconds after the landing. */
private fun kick(t: Float): Float {
    if (t <= 0f) return 0f
    val decay = BOUNCE_DAMPING / 2f
    val ring = sqrt(BOUNCE_STIFFNESS - decay * decay)
    return BOUNCE_KICK / ring * exp(-decay * t) * sin(ring * t)
}

private class ChipLook(val angle: Float, val opacity: Float)

/** Angle in degrees and opacity of chip [k]: the old status tips back out of sight, then the new one tips in. */
private fun chipLook(k: Int, from: Int, shown: Int, flip: Float, still: Boolean): ChipLook {
    if (still) {
        val seen = clamp01(flip / FADE_TIME)
        return ChipLook(0f, if (k == shown) seen else if (k == from) 1f - seen else 0f)
    }
    if (k == shown) {
        val turn = clamp01((flip - FLIP_OUT) / FLIP_IN)
        return ChipLook(-90f * (1f - turn) * (1f - turn), if (flip >= FLIP_OUT) 1f else 0f)
    }
    if (k == from) {
        val turn = clamp01(flip / FLIP_OUT)
        return ChipLook(90f * turn * turn, if (flip < FLIP_OUT) 1f else 0f)
    }
    return ChipLook(0f, 0f)
}

/** What moves. */
private class FlightMotion {
    /** The share of the flight, 0 at the gate … 1 landed. */
    val progress = Animatable(0f)

    /** The status the chip is leaving, the one it shows, and the seconds since that flip began. */
    var statusFrom by mutableIntStateOf(0)
    var status by mutableIntStateOf(0)
    var flip by mutableFloatStateOf(CLOCK)

    /** 0 black … 1 green. */
    val green = Animatable(0f)

    /** Seconds since the plane landed. */
    var landing by mutableFloatStateOf(CLOCK)
    var flipClock: Job? = null
    var landingClock: Job? = null
}

/**
 * A flight on a square photo card: a view over the wing, and a white panel with the two airports, a
 * dashed line the plane's badge travels along, the time left and three small fields. As the flight
 * progresses the line turns solid behind the badge, the status chip on the photo flips from Boarding
 * to In flight to Landed, the time counts down and the clouds drift past; on landing the badge turns
 * green with a small bounce. Dragging the badge scrubs the flight.
 *
 * @param photo Address of the photo, three wide by two high.
 * @param tint The photo's main colour, shown until it arrives.
 * @param minutes Length of the flight in minutes: what is left of it counts down under the line.
 * @param statuses The chip's wording before take-off, in the air and after landing.
 * @param hourWords The word after the hours left: for one, and for any other number. [minuteWords] likewise.
 * @param arrived Shown in place of the time left once the plane has landed.
 * @param autoPlay Plays the flight, holds on the landing, then rewinds to the gate, in a loop.
 * @param onScrub Called with the share of the flight, 0…1, while the badge is dragged.
 * @param onLand Called when the plane lands.
 */
@Composable
fun FlightStatusCard(
    photo: String = "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?auto=format&fit=crop&w=1200&h=800&q=80",
    tint: Color = Color(0xFFA4ABB1),
    from: FlightStop = FlightStop("SUB", "Surabaya", "08:45 PM"),
    to: FlightStop = FlightStop("SIN", "Singapore", "12:10 AM"),
    flight: String = "NB-880",
    minutes: Int = 205,
    statuses: List<String> = listOf("Boarding", "In flight", "Landed"),
    hourWords: List<String> = listOf("Hour", "Hours"),
    minuteWords: List<String> = listOf("Minute", "Minutes"),
    arrived: String = "Arrived",
    fields: List<FlightField> = Fields,
    autoPlay: Boolean = false,
    onScrub: ((progress: Float) -> Unit)? = null,
    onLand: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val motion = remember { FlightMotion() }
    val scrubbed by rememberUpdatedState(onScrub)
    val landed by rememberUpdatedState(onLand)
    // A touch stops the loop; `touches` restarts the wait before it resumes.
    var touching by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.12f), sigma = 15f) }
    val badgeShadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.22f), sigma = 3f) }

    // Keeps the chip and the landing in step with the flight.
    LaunchedEffect(motion) {
        snapshotFlow { statusAt(motion.progress.value) }.collect { now ->
            val old = motion.status
            if (now == old) return@collect
            motion.statusFrom = old
            motion.status = now
            motion.flip = 0f
            motion.flipClock?.cancel()
            motion.flipClock = launch {
                animate(0f, CLOCK, animationSpec = tween((CLOCK * 1000).toInt(), easing = LinearEasing)) { value, _ -> motion.flip = value }
            }
            if (now == 2) {
                launch { motion.green.animateTo(1f, tween((GREEN_IN * 1000).toInt(), easing = LinearEasing)) }
                motion.landing = 0f
                motion.landingClock?.cancel()
                motion.landingClock = launch {
                    animate(0f, CLOCK, animationSpec = tween((CLOCK * 1000).toInt(), easing = LinearEasing)) { value, _ -> motion.landing = value }
                }
                if (touching) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                landed?.invoke()
            } else if (old == 2) {
                launch { motion.green.animateTo(0f, tween((GREEN_OUT * 1000).toInt(), easing = LinearEasing)) }
            }
        }
    }

    // The flight plays, holds on the landing and rewinds; after a touch it first goes back to the gate.
    LaunchedEffect(auto, touching, touches) {
        if (!auto || touching) return@LaunchedEffect
        if (touches > 0) {
            delay((RESUME_AFTER * 1000).toLong())
            motion.progress.animateTo(0f, tween((REWIND_TIME * 1000).toInt(), easing = RewindCurve))
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((turn * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            until(TAKE_OFF_AT)
            motion.progress.animateTo(1f, tween((FLIGHT_TIME * 1000).toInt(), easing = FlightCurve))
            until(REWIND_AT)
            motion.progress.animateTo(0f, tween((REWIND_TIME * 1000).toInt(), easing = RewindCurve))
            turn += 1
        }
    }

    val left by remember(minutes) { derivedStateOf { (minutes * (1f - motion.progress.value)).roundToInt() } }
    val remaining = if (left > 0) {
        val hours = left / 60
        val rest = left % 60
        val hourWord = (if (hours == 1) hourWords.firstOrNull() else hourWords.lastOrNull()) ?: ""
        val minuteWord = (if (rest == 1) minuteWords.firstOrNull() else minuteWords.lastOrNull()) ?: ""
        "$hours $hourWord ${if (rest < 10) "0" else ""}$rest $minuteWord"
    } else {
        arrived
    }

    Box(
        modifier
            .size(SIZE.dp)
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 16f, SIZE, SIZE, RADIUS) }
                }
            }
            .clip(RoundedCornerShape(RADIUS.dp))
            .background(tint)
            .drawWithContent {
                drawContent()
                // A white rim just inside the edge.
                val line = 2.dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(line / 2f, line / 2f),
                    size = Size(size.width - line, size.height - line),
                    cornerRadius = CornerRadius(RADIUS.dp.toPx() - line / 2f),
                    style = Stroke(line),
                )
            }
            .clearAndSetSemantics {
                contentDescription = "$flight, ${from.city} to ${to.city}"
                stateDescription = "${statuses.getOrElse(motion.status) { "" }}, $remaining"
            },
    ) {
        // The photo is wider than the card: it slides past and grows a touch as the flight goes on.
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current).data(photo).crossfade(200).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .offset(((SIZE - PHOTO_W) / 2f).dp, PHOTO_Y.dp)
                .size(PHOTO_W.dp, PHOTO_H.dp)
                .graphicsLayer {
                    if (!still) {
                        val grow = 1f + ZOOM * motion.progress.value
                        translationX = (DRIFT * (1f - 2f * motion.progress.value)).dp.toPx()
                        scaleX = grow
                        scaleY = grow
                    }
                },
        )
        // The photo ends behind the panel: below it the card fades into the colour of the dusk.
        Box(
            Modifier
                .offset(0.dp, FADE_Y.dp)
                .size(SIZE.dp, (SIZE - FADE_Y).dp)
                .background(Brush.verticalGradient(0f to Color(0x007C7A86), 0.23f to Color(0xFF7C7A86), 1f to Color(0xFF4A5068))),
        )

        statuses.take(3).forEachIndexed { k, label ->
            Row(
                Modifier
                    .offset(14.dp, 14.dp)
                    .graphicsLayer {
                        val look = chipLook(k, motion.statusFrom, motion.status, motion.flip, still)
                        rotationX = look.angle
                        alpha = look.opacity
                    }
                    .height(26.dp)
                    .background(Color(0x8010141E), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(ChipDots[k], CircleShape))
                BasicText(label, style = ChipStyle, maxLines = 1)
            }
        }

        // The white panel, laid out in a 324 × 160 box.
        Box(
            Modifier
                .offset(((SIZE - PANEL_W) / 2f).dp, PANEL_Y.dp)
                .size(PANEL_W.dp, PANEL_H.dp)
                .background(Color.White, RoundedCornerShape(22.dp)),
        ) {
            Stop(from, 20f, Alignment.CenterStart)
            Stop(to, 124f, Alignment.CenterEnd)

            At(flight, SmallStyle, 102f, 14f, 120f, 14f, Alignment.Center)
            Canvas(Modifier.fillMaxSize()) {
                // Drawn in dp, like the Skia canvas of the React Native version.
                scale(density, density, pivot = Offset.Zero) {
                    drawLine(badgeShadow, motion.progress.value, motion.green.value, if (still) 0f else kick(motion.landing))
                }
            }
            At(remaining, SmallStyle, 82f, 62f, 160f, 14f, Alignment.Center)

            Box(
                Modifier
                    .offset(12.dp, 88.dp)
                    .size(300.dp, 60.dp)
                    .background(Color(0xFFFAFAFA), RoundedCornerShape(14.dp))
                    .border(1.dp, Line, RoundedCornerShape(14.dp)),
            )
            fields.take(3).forEachIndexed { k, field ->
                if (k > 0) Box(Modifier.offset((12f + k * 100f - 0.5f).dp, 100.dp).size(1.dp, 36.dp).background(Line))
                At(field.label, SmallStyle, 12f + k * 100f, 100f, 100f, 14f, Alignment.Center)
                At(field.value, ValueStyle, 12f + k * 100f, 119f, 100f, 17f, Alignment.Center)
            }

            // Dragging along the line scrubs the flight.
            Box(
                Modifier
                    .offset((LINE_X - 16f).dp, 22.dp)
                    .size((LINE_W + 32f).dp, 44.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = motion.progress.value
                            var moved = 0f
                            touching = true
                            scope.launch { motion.progress.stop() }
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            horizontalDrag(down.id) { change ->
                                moved += change.positionChange().x / density
                                val next = clamp01(start + moved / TRAVEL)
                                scope.launch { motion.progress.snapTo(next) }
                                scrubbed?.invoke(next)
                                change.consume()
                            }
                            touching = false
                            touches += 1
                        }
                    },
            )
        }
    }
}

/** A line of text in a box whose top-left corner is at [x], [y]. */
@Composable
private fun BoxScope.At(text: String, style: TextStyle, x: Float, y: Float, width: Float, height: Float, align: Alignment) {
    Box(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp), contentAlignment = align) {
        BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

@Composable
private fun BoxScope.Stop(stop: FlightStop, x: Float, align: Alignment) {
    At(stop.time, SmallStyle, x, 14f, 180f, 14f, align)
    At(stop.code, CodeStyle, x, 30f, 180f, 28f, align)
    At(stop.city, CityStyle, x, 60f, 180f, 16f, align)
}

/** The dashed line, its solid part behind the badge, and the badge with its plane. */
private fun DrawScope.drawLine(shadow: SoftRoundRect, progress: Float, green: Float, bounce: Float) {
    val x = LINE_X + BADGE + TRAVEL * progress
    drawLine(Color(0xFFCFD2D6), Offset(LINE_X, LINE_Y), Offset(LINE_X + LINE_W, LINE_Y), 1.2f, pathEffect = Dashes)
    drawLine(Ink, Offset(LINE_X, LINE_Y), Offset(x, LINE_Y), 1.6f, StrokeCap.Round)

    withTransform({
        translate(x, LINE_Y)
        scale(1f + bounce, 1f + bounce, Offset.Zero)
    }) {
        drawIntoCanvas { shadow.draw(it.nativeCanvas, -BADGE, 2f - BADGE, BADGE * 2f, BADGE * 2f, BADGE) }
        drawCircle(Ink, BADGE, Offset.Zero)
        drawCircle(Color(0xFF1FA055), BADGE, Offset.Zero, alpha = green)
        // The plane is drawn nose up: turned to face the way it flies.
        withTransform({
            rotate(90f, Offset.Zero)
            scale(0.62f, 0.62f, Offset.Zero)
            translate(-10f, -10f)
        }) {
            drawPath(Plane, Color.White)
        }
    }
}
