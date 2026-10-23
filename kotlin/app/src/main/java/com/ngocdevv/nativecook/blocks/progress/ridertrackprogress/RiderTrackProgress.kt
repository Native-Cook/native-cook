package com.ngocdevv.nativecook.blocks.progress.ridertrackprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 340f
private const val HEIGHT = 183f
private const val RADIUS = 35f

// The canvas overflows the panel by this much on every side to fit the screen glow around it.
private val BLEED = 60.dp

private const val TRACK_X = 17.3f
private const val TRACK_W = 304f
private const val TRACK_Y = 109.1f
private const val TRACK_H = 5.8f

// The scooter is drawn around the point where it meets the road, at this scale.
private const val RIDER_SCALE = 0.97f
private const val RIDER_FRONT = 20.2f * RIDER_SCALE + 2.3f
private const val WHEEL_R = 5f
private const val GROUND_Y = TRACK_Y - TRACK_H / 2 + 0.5f

private val Teal = Color(0xFF2FDCA8)
private val Grey = Color(0xFF7B7B7B)

// The reference shows the rider 69% of the way; the autoPlay loop built around it (seconds).
private const val REST = 0.69f
private const val LOW = 0.14f
private const val START = 0.6f
private const val RIDE = 3.2f
private const val HOLD = 3f
private const val FINISH = 1.7f
private const val HOLD_END = 1.6f
private const val RESET = 0.8f
private const val T1 = START + RIDE
private const val T2 = T1 + HOLD
private const val T3 = T2 + FINISH
private const val T4 = T3 + HOLD_END
private const val LOOP = T4 + RESET

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = T1 + 1.5f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun riderText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.3.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val NameStyle = riderText(12.6f, 18f, FontWeight.Bold, Color.White)
private val DetailStyle = riderText(11f, 16f, FontWeight.Medium, Grey)
private val LeftStyle = riderText(11f, 16f, FontWeight.Bold, Teal).copy(fontFeatureSettings = "tnum")
private val InitialsStyle = riderText(11.5f, 16f, FontWeight.Bold, Color.White)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Share of the trip ridden at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> LOW
    t < T1 -> LOW + (REST - LOW) * easeInOutCubic((t - START) / RIDE)
    t < T2 -> REST
    t < T3 -> REST + (1f - REST) * easeInOutCubic((t - T2) / FINISH)
    t < T4 -> 1f
    else -> LOW
}

/** The rider fades out at the door and back in at the start. */
private fun loopPresence(t: Float) = when {
    t < START -> clamp01(t / 0.4f)
    t < T4 -> 1f
    else -> 1f - clamp01((t - T4) / (RESET * 0.6f))
}

private fun fillEnd(p: Float) = TRACK_X + TRACK_W * clamp01(p)

/** Places the element by its leading edge and vertical centre inside the panel. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its trailing edge and vertical centre inside the panel. */
private fun Modifier.trailingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width, centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its centre inside the panel. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

// Hardware canvases only honour mask filters from API 28: soft layers are skipped below that.
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

private class RiderPaints {
    val barGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (CanBlur) maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL)
    }
    val panel = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, WIDTH, HEIGHT), CornerRadius(RADIUS))) }

    // Scooter and rider, nose to the right; the origin is the middle of the road under the wheels.
    val body = svgPath(
        "M-19.5 -8.5C-19.5 -13.5 -16 -16.5 -10 -16.5L-2.5 -16.5L-2.5 -9.5L5.5 -9.5L9.2 -24.5L12.4 -24.5L14.4 -10C11.5 -9.5 9 -7.6 8.4 -5L-7 -5C-7.6 -9.5 -12 -11.5 -15.5 -10.5C-17 -10 -18.6 -9.4 -19.5 -8.5Z",
    )
    val fender = svgPath(
        "M7.8 -6C8.3 -10.2 11 -12 14.2 -12C17 -12 19.6 -10.2 20.2 -7L17 -7.6C16.2 -9 15 -9.6 13.8 -9.6C11.8 -9.6 10.6 -8 10.4 -6Z",
    )
    val handlebar = svgPath("M10.4 -25.6L6.2 -28.2")
    val leg = svgPath("M-6.5 -20.6L1 -20.6L2.2 -10.8")
    val shoe = svgPath("M1.2 -11.2H5.2V-9.4H1.2Z")
    val torso = svgPath("M-9 -19.5L-8 -29C-7.8 -31 -6.2 -32 -4.4 -32C-2.4 -32 -1.2 -30.6 -1.4 -28.6L-2.4 -19.5Z")
    val arm = svgPath("M-3.8 -28.8L2 -26.4L6.4 -27.8")
    val spokes = svgPath("M-3.2 0H3.2M0 -3.2V3.2")

    // Burger and drink, centred on the badge.
    val burger = svgPath(
        "M-7.6 -0.4C-7.6 -4.4 -4.8 -6.4 -1.4 -6.4C2 -6.4 4.8 -4.4 4.8 -0.4ZM-8 1H5.2V3H-8ZM-7.6 4.4H4.8V5.4C4.8 6.4 4 7 3 7H-5.8C-6.8 7 -7.6 6.4 -7.6 5.4Z",
    )
    val drink = svgPath("M3.4 -4.6H9.4L8.6 7H6.2V-0.6C6.2 -2.2 5 -3.8 3.4 -4.6Z")
    val straw = svgPath("M6.8 -4.6L7.6 -8.2L9.4 -8.8")
    val phone = svgPath(
        "M-4 -4.2C-3.5 -4.4 -3 -4.1 -2.8 -3.6L-2.1 -1.8C-1.9 -1.4 -2.1 -1 -2.4 -0.7L-3.2 -0.1C-2.5 1.3 -1.5 2.3 -0.1 3L0.5 2.2C0.8 1.9 1.2 1.7 1.6 1.9L3.4 2.6C3.9 2.8 4.2 3.3 4 3.8C3.7 4.9 2.7 5.4 1.7 5.2C-2.3 4.5 -5.4 1.4 -6.1 -2.6C-6.3 -3.4 -5.1 -3.9 -4 -4.2Z",
    )
    val waveNear = svgPath("M1.2 -3.8Q3.6 -3.2 4.2 -0.8")
    val waveFar = svgPath("M1.8 -6.6Q6.4 -5.6 7.2 -1.2")
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2), alpha = alpha)

/**
 * Order-tracking panel on a glowing phone screen: the restaurant and order, a road with a courier
 * on a scooter, and the courier's row with the time left and a call button. The wheels turn with
 * the distance covered, the scooter bounces, tips as it pulls away and brakes, and leaves exhaust
 * puffs; a teal glow rides along, the minutes tick down with a small pop and the handset rings.
 *
 * @param progress Controlled share of the trip ridden, 0…1.
 * @param autoPlay Rides to the point shown in the reference, waits there, rides on to the door, and starts over.
 * @param arrival Text in front of the time left.
 * @param tripMinutes Minutes the whole trip takes: the time left counts down from it.
 */
@Composable
fun RiderTrackProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    restaurant: String = "Food Hub",
    order: String = "Pizza Pepperoni",
    price: String = "250₹",
    payment: String = "COD",
    courier: String = "Alex R.",
    arrival: String = "Will arrive in",
    tripMinutes: Float = 16f,
    onCallClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)
    val trip by rememberUpdatedState(tripMinutes)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // 0…1, smoothed: is the scooter rolling.
    val moving = remember { mutableFloatStateOf(0f) }
    // Positive while pulling away, negative while braking: tips the scooter.
    val pitch = remember { mutableFloatStateOf(0f) }
    // How far the wheels have turned, radians.
    val wheel = remember { mutableFloatStateOf(0f) }
    // When the minutes last changed, on the `time` axis.
    val tickedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 1600, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        var before = target
        var shown = ceil(trip * (1f - target) - 0.001f).roundToInt()
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            time.floatValue += dt
            if (playing) {
                var t = clock.floatValue + dt
                if (looping) {
                    if (t >= LOOP) t -= LOOP
                } else if (t >= REST_T) {
                    // A tapped, one-off run stops at the resting frame.
                    t = REST_T
                    playing = false
                }
                clock.floatValue = t
            }
            val current = if (isControlled) held.value else loopValue(clock.floatValue)
            val step = current - before
            before = current
            // A jump back to the start is not a ride.
            val rolling = abs(step) > 0.00003f && abs(step) < 0.05f
            val was = moving.floatValue
            moving.floatValue = was + ((if (rolling) 1f else 0f) - was) * min(1f, dt * 7f)
            pitch.floatValue += ((if (rolling) 1f else 0f) - was - pitch.floatValue) * min(1f, dt * 10f)
            if (rolling) wheel.floatValue += step * TRACK_W / (WHEEL_R * RIDER_SCALE)
            val minutes = ceil(trip * (1f - clamp01(current)) - 0.001f).roundToInt()
            if (minutes != shown) {
                shown = minutes
                tickedAt.floatValue = time.floatValue
            }
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val presence = { if (controlled) 1f else loopPresence(clock.floatValue) }
    val minutes by remember(controlled, tripMinutes) {
        derivedStateOf {
            ceil(tripMinutes * (1f - clamp01(if (controlled) held.value else loopValue(clock.floatValue))) - 0.001f).roundToInt()
        }
    }
    val left = if (minutes > 0) "${minutes}min" else "a moment"
    val initials = courier.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { RiderPaints() }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .pointerInput(controlled, reducedMotion) {
                detectTapGestures {
                    if (!controlled && !reducedMotion) {
                        clock.floatValue = 0f
                        playing = true
                    }
                }
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val shown = presence()
                    val going = if (ambient > 0f) moving.floatValue else 0f
                    val cy = y + TRACK_Y
                    val end = x + fillEnd(value())

                    // The screen the panel floats on: pale at the top, deep blue towards the bottom.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to rgba(222, 227, 238, 1f),
                            0.5f to rgba(150, 178, 214, 0.9f),
                            0.74f to rgba(40, 86, 160, 0.5f),
                            1f to rgba(10, 30, 90, 0f),
                            startY = y - 9f,
                            endY = y + HEIGHT + 61f,
                        ),
                        topLeft = Offset(x - 9f, y - 9f),
                        size = Size(WIDTH + 18f, HEIGHT + 70f),
                        cornerRadius = CornerRadius(RADIUS + 9f),
                    )
                    drawRoundRect(Color.Black, Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))

                    translate(x, y) {
                        clipPath(paints.panel) {
                            val ex = end - x
                            val ty = TRACK_Y
                            // The glow travels with the rider and breathes.
                            val glowCenter = Offset(ex - 22f, ty - 14f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to rgba(24, 214, 160, 0.42f),
                                    0.4f to rgba(20, 190, 150, 0.2f),
                                    0.75f to rgba(16, 150, 130, 0.05f),
                                    1f to rgba(16, 150, 130, 0f),
                                    center = glowCenter,
                                    radius = 92f,
                                ),
                                radius = 92f,
                                center = glowCenter,
                                alpha = shown * (0.86f + ambient * 0.14f * sin(t * 2f)),
                            )

                            // Road: dim track, lit part with its glow.
                            val top = ty - TRACK_H / 2
                            val litWidth = max(TRACK_H, ex - TRACK_X)
                            pill(Color(0xFF2D2D2D), TRACK_X, top, TRACK_W, TRACK_H)
                            if (CanBlur) {
                                paints.barGlow.color = Color(0xFF1FD9B4).copy(alpha = shown * 0.55f).toArgb()
                                drawIntoCanvas {
                                    it.nativeCanvas.drawRoundRect(
                                        TRACK_X, top, TRACK_X + litWidth, top + TRACK_H, TRACK_H / 2, TRACK_H / 2, paints.barGlow,
                                    )
                                }
                            }
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color(0xFF1AAEF5),
                                    0.6f to Color(0xFF19D2C8),
                                    1f to Color(0xFF22E3A0),
                                    startX = TRACK_X,
                                    endX = ex,
                                ),
                                topLeft = Offset(TRACK_X, top),
                                size = Size(litWidth, TRACK_H),
                                cornerRadius = CornerRadius(TRACK_H / 2),
                                alpha = shown,
                            )

                            // Exhaust: small clouds that drift back from the rear wheel and thin out.
                            if (going > 0.01f) {
                                for (index in 0 until 3) {
                                    val u = (t * 1.5f + index / 3f) % 1f
                                    drawCircle(
                                        color = Color(0xFFC9EFE4),
                                        radius = 1.3f + 2.6f * u,
                                        center = Offset(
                                            ex - RIDER_FRONT - 22f * RIDER_SCALE - 2f - 15f * u,
                                            GROUND_Y - 7f - 6f * u,
                                        ),
                                        alpha = shown * going * 0.55f * (1f - u) * clamp01(u * 6f),
                                    )
                                }
                            }

                            // The scooter bounces on the road while it rolls, idles with a slow sway, and tips
                            // as it pulls away or brakes.
                            val bounce = ambient * (
                                going * 0.7f * abs(sin(t * 13f)) + (1f - going) * 0.35f * (0.5f + 0.5f * sin(t * 2.4f))
                                )
                            withTransform({
                                translate(ex - RIDER_FRONT, GROUND_Y - bounce)
                                rotate(
                                    degrees = -0.12f * ambient * pitch.floatValue * 180f / Math.PI.toFloat(),
                                    pivot = Offset.Zero,
                                )
                                scale(RIDER_SCALE, RIDER_SCALE, pivot = Offset.Zero)
                            }) {
                                for (wheelX in floatArrayOf(-13f, 13.5f)) {
                                    val hub = Offset(wheelX, -WHEEL_R)
                                    drawCircle(Color(0xFF3A3C48), WHEEL_R, hub, alpha = shown)
                                    drawCircle(Color(0xFFC9CCD8), 2.6f, hub, alpha = shown)
                                    translate(wheelX, -WHEEL_R) {
                                        rotateRad(wheel.floatValue, pivot = Offset.Zero) {
                                            drawPath(
                                                paints.spokes, Color(0xFF3A3C48), alpha = shown,
                                                style = Stroke(0.9f, cap = StrokeCap.Round),
                                            )
                                        }
                                    }
                                }
                                // Delivery box and seat.
                                drawRoundRect(Color(0xFFE8562A), Offset(-20.5f, -25.5f), Size(8f, 8.6f), CornerRadius(1f), alpha = shown)
                                drawRoundRect(Color(0xFFF5C53A), Offset(-21.2f, -27.2f), Size(9.4f, 2.3f), CornerRadius(1f), alpha = shown)
                                drawRoundRect(Color(0xFFF5C53A), Offset(-12.4f, -19f), Size(10.4f, 2.8f), CornerRadius(1.4f), alpha = shown)
                                drawPath(paints.body, Color(0xFF3D8DF2), alpha = shown)
                                drawPath(paints.fender, Color(0xFFF2C230), alpha = shown)
                                drawPath(paints.handlebar, Color(0xFF2A2C36), alpha = shown, style = Stroke(1.4f, cap = StrokeCap.Round))
                                drawCircle(Color(0xFFF5C53A), 2.1f, Offset(12.6f, -25.8f), alpha = shown)
                                // Rider.
                                drawPath(
                                    paints.leg, Color.White, alpha = shown,
                                    style = Stroke(3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                                drawPath(paints.shoe, Color(0xFFE9EDF5), alpha = shown)
                                drawPath(paints.torso, Color(0xFFF04E2C), alpha = shown)
                                drawPath(
                                    paints.arm, Color(0xFFF04E2C), alpha = shown,
                                    style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                                drawCircle(Color(0xFFF3C9A4), 2.6f, Offset(-2.2f, -34.6f), alpha = shown)
                                drawCircle(Color(0xFFF5C22E), 3.7f, Offset(-4.2f, -35.6f), alpha = shown)
                            }
                        }
                    }

                    // Restaurant badge and courier avatar.
                    drawCircle(Color(0xFF353C3B), 15f, Offset(x + 32.8f, y + 36.5f))
                    withTransform({
                        translate(x + 32.4f, y + 36.8f)
                        scale(0.92f, 0.92f, pivot = Offset.Zero)
                    }) {
                        drawPath(paints.drink, Color.White)
                        drawPath(paints.straw, Color.White, style = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(paints.burger, Color.White)
                    }
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFD49A6A), Color(0xFF8A5636)),
                            start = Offset(x + 20f, y + 132f),
                            end = Offset(x + 46f, y + 160f),
                        ),
                        radius = 15f,
                        center = Offset(x + 32.8f, y + 146.1f),
                    )
                }
            }
        }

        BasicText(
            text = restaurant,
            style = NameStyle,
            maxLines = 1,
            modifier = Modifier
                .leadingAt(59.7.dp, 28.3.dp)
                .semantics {
                    contentDescription = "$restaurant, $order"
                    stateDescription = "$courier ${arrival.lowercase()} $left"
                    progressBarRangeInfo = ProgressBarRangeInfo(tripMinutes - minutes, 0f..tripMinutes)
                },
        )
        BasicText(order, Modifier.leadingAt(59.7.dp, 46.1.dp), DetailStyle, maxLines = 1)
        BasicText(price, Modifier.trailingAt(320.9.dp, 28.3.dp), NameStyle, maxLines = 1)
        BasicText(payment, Modifier.trailingAt(320.9.dp, 46.1.dp), DetailStyle, maxLines = 1)

        BasicText(initials, Modifier.centerAt(32.8.dp, 146.1.dp).clearAndSetSemantics {}, InitialsStyle, maxLines = 1)
        BasicText(courier, Modifier.leadingAt(59.7.dp, 137.7.dp), NameStyle, maxLines = 1)
        Row(Modifier.leadingAt(59.7.dp, 155.5.dp)) {
            BasicText("$arrival ", style = DetailStyle, maxLines = 1)
            BasicText(
                text = left,
                style = LeftStyle,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    // 0 → 1 after the minutes change: the number pops.
                    val since = time.floatValue - tickedAt.floatValue
                    val tick = if (ambient > 0f) EaseOutStrong.transform(clamp01(since / 0.38f)) else 1f
                    alpha = 0.4f + 0.6f * tick
                    scaleX = 1f + 0.14f * (1f - tick)
                    scaleY = 1f + 0.14f * (1f - tick)
                    translationY = -3.dp.toPx() * (1f - tick)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            )
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val view = LocalView.current
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.92f else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        Canvas(
            modifier = Modifier
                .offset((306.8f - 15f).dp, (146.1f - 15f).dp)
                .size(30.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(Color(0xFF343B3A), CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onCallClick)
                .semantics { contentDescription = "Call $courier" },
        ) {
            // The handset "rings": its two waves light up one after the other.
            val t = time.floatValue
            val near = if (ambient > 0f) 0.35f + 0.65f * clamp01(sin(t * 3.2f) * 1.4f + 0.3f) else 1f
            val far = if (ambient > 0f) 0.25f + 0.75f * clamp01(sin(t * 3.2f - 0.9f) * 1.4f + 0.3f) else 1f
            val unit = density
            withTransform({
                scale(unit, unit, pivot = Offset.Zero)
                translate(15.2f, 15f)
                scale(1.12f, 1.12f, pivot = Offset.Zero)
            }) {
                drawPath(paints.phone, Teal, style = Stroke(1.25f, join = StrokeJoin.Round))
                drawPath(paints.waveNear, Teal, alpha = near, style = Stroke(1.25f, cap = StrokeCap.Round))
                drawPath(paints.waveFar, Teal, alpha = far, style = Stroke(1.25f, cap = StrokeCap.Round))
            }
        }
    }
}
