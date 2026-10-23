package com.ngocdevv.nativecook.blocks.progress.flightprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** One end of the route. */
data class FlightStop(val code: String, val city: String, val time: String)

enum class FlightTint { Lime, Orange }

private const val WIDTH = 356f
private const val HEIGHT = 160f
private const val RADIUS = 22f

// The canvas overflows the card by this much on every side so the soft shadow is not clipped.
private val BLEED = 24.dp

private const val BAR_X = 14.8f
private const val BAR_Y = 109.1f
private const val BAR_W = 315.3f
private const val BAR_H = 36.5f
private const val BAR_CY = BAR_Y + BAR_H / 2

// The plane sits this far inside the filled part's leading edge.
private const val PLANE_INSET = 19.4f

private const val BOX_X = 220.5f
private const val BOX_Y = 15.3f
private const val BOX_W = 120.7f
private const val BOX_H = 78.2f
private const val SWAP_X = 319.2f
private const val SWAP_Y = 36.8f
private const val SWAP_R = 10.1f

private const val ARROW_FROM = 94.3f
private const val ARROW_TO = 122f
private const val ARROW_Y = 44f

// The reference shows 54% flown; the autoPlay loop built around it (seconds).
private const val REST = 0.54f
private const val LOW = 0.04f
private const val START = 0.5f
private const val FILL = 3f
private const val HOLD = 3.4f
private const val RESET = 0.8f
private const val LOOP = START + FILL + HOLD + RESET

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = START + FILL + 1.6f

private class FlightPalette(
    val glow: Color,
    val fill: List<Color>,
    val rim: Color,
    val arrow: Color,
    val note: Color,
    val haze: Color,
)

private val LimePalette = FlightPalette(
    glow = Color(0xFFB4F01F),
    fill = listOf(Color(0xFFEBFB7C), Color(0xFFC8EF38), Color(0xFF84BD16)),
    rim = Color(0xFFC2F41E),
    arrow = Color(0xFFB9F52C),
    note = Color(0xFFFF7D1A),
    haze = Color(0xFFB8F227),
)

private val OrangePalette = FlightPalette(
    glow = Color(0xFFFF8A1F),
    fill = listOf(Color(0xFFFFD3A8), Color(0xFFFFB45E), Color(0xFFFF6A05)),
    rim = Color(0xFFFFC686),
    arrow = Color(0xFFFFB90A),
    note = Color(0xFFC9EF4B),
    haze = Color(0xFFFF9628),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun flightText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CodeStyle = flightText(24.5f, 29f, FontWeight.Black, Color.White).copy(letterSpacing = 0.6.sp)
private val CityStyle = flightText(11.2f, 16f, FontWeight.Medium, Color(0xFFF1F1F1))
private val TimeStyle = flightText(10.8f, 16f, FontWeight.Medium, Color(0xFF6E6E6E))
private val EtaStyle = flightText(12.4f, 18f, FontWeight.Medium, Color.White)
private val ZoneStyle = flightText(11f, 16f, FontWeight.Medium, Color(0xFF9A9A9A))
private val NoteStyle = flightText(10.6f, 16f, FontWeight.Medium, Color.White)
private val RemainingStyle =
    flightText(10.6f, 16f, FontWeight.Bold, Color(0xFF4C4C4C)).copy(fontFeatureSettings = "tnum")

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Share of the flight flown at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> LOW
    t < START + FILL -> LOW + (REST - LOW) * easeInOutCubic((t - START) / FILL)
    t < START + FILL + HOLD -> REST
    else -> REST - (REST - LOW) * easeInOutCubic((t - START - FILL - HOLD) / RESET)
}

private fun fillWidth(p: Float) = max(BAR_H, BAR_W * clamp01(p))

/** Places the element by its leading edge and vertical centre inside the card. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its trailing edge and vertical centre inside the card. */
private fun Modifier.trailingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width, centerY.roundToPx() - placeable.height / 2)
    }
}

// Hardware canvases only honour mask filters from API 28: soft layers are skipped below that.
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun softPaint(sigma: Float, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    if (stroke > 0f) {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    if (CanBlur) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun Paint.tinted(color: Color, alpha: Float = 1f): Paint {
    this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    return this
}

private class FlightPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.55f), sigma = 12f)
    val bevel = softPaint(5f, stroke = 5f)
    val arrowGlow = softPaint(7f, stroke = 7f)
    val spark = softPaint(2.5f)
    val glow = softPaint(9f)
    val rim = softPaint(2.2f, stroke = 3.5f)
    val sheen = softPaint(5f)
    val sweep = softPaint(8f)

    val card = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, WIDTH, HEIGHT), CornerRadius(RADIUS))) }
    val fill = Path()

    /**
     * Dust on the card, densest along the top edge: three sets of dots that twinkle out of phase.
     * A fixed-seed generator keeps the pattern identical on every platform.
     */
    val dust = List(3) { Path() }.also { sets ->
        var seed = 20240611L
        fun next(): Double {
            seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return seed / 4294967296.0
        }
        for (index in 0 until 420) {
            val x = 4 + next() * (WIDTH - 8)
            val fall = next()
            val y = 2 + fall * fall * fall * 112
            val r = 0.18 + next() * 0.26
            sets[index % 3].addOval(
                Rect((x - r).toFloat(), (y - r).toFloat(), (x + r).toFloat(), (y + r).toFloat()),
            )
        }
    }

    // A small plane seen from the side, nose to the right, centred on its body.
    val plane = Path().apply {
        moveTo(-6.9f, -1.2f)
        lineTo(-8f, -5.6f)
        lineTo(-5.3f, -5.6f)
        lineTo(-2.6f, -1.6f)
        close()
        moveTo(-1.6f, 0.4f)
        lineTo(2.6f, 0.4f)
        lineTo(-1.2f, 5.9f)
        lineTo(-3.9f, 5.9f)
        close()
        moveTo(-7.2f, 0.4f)
        cubicTo(-7.2f, -1.4f, -5.8f, -2.2f, -4f, -2.2f)
        lineTo(3.2f, -2.2f)
        cubicTo(5.9f, -2.2f, 7.8f, -1f, 7.8f, 0.2f)
        cubicTo(7.8f, 1.4f, 5.9f, 2.3f, 3.2f, 2.3f)
        lineTo(-4.8f, 2.3f)
        cubicTo(-6.2f, 2.3f, -7.2f, 1.5f, -7.2f, 0.4f)
        close()
    }
    val arrow = Path().apply {
        moveTo(ARROW_FROM, ARROW_Y)
        lineTo(ARROW_TO, ARROW_Y)
        moveTo(ARROW_TO - 8.6f, ARROW_Y - 8.6f)
        lineTo(ARROW_TO, ARROW_Y)
        lineTo(ARROW_TO - 8.6f, ARROW_Y + 8.6f)
    }

    // A slanted band of light, centred on the bar's middle line.
    val sweepBand = Path().apply {
        moveTo(-6f, -26f)
        lineTo(22f, -26f)
        lineTo(6f, 26f)
        lineTo(-22f, 26f)
        close()
    }

    // Two small chevrons, one above the other, centred on the button.
    val swap = Path().apply {
        moveTo(1.4f, -5.6f)
        lineTo(-1.6f, -3.3f)
        lineTo(1.4f, -1f)
        moveTo(-1.4f, 1f)
        lineTo(1.6f, 3.3f)
        lineTo(-1.4f, 5.6f)
    }
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2), alpha = alpha)

/**
 * Dark flight card: both airports with an arrow between them, an ETA box, and a glossy pill bar a
 * small plane rides. The plane noses up and leaves a slipstream while the bar grows, a band of
 * light crosses the bar when it levels off, the label counts the time left down, the dust on the
 * card twinkles and a spark runs down the arrow.
 *
 * @param progress Controlled share of the flight flown, 0…1.
 * @param autoPlay Flies from the gate to the point shown in the reference, holds, and starts over.
 * @param tint Colour of the bar, the arrow and the dust; the note under the ETA takes the other colour.
 * @param totalMinutes Length of the flight: the bar's label counts the minutes left down from it.
 */
@Composable
fun FlightProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    tint: FlightTint = FlightTint.Lime,
    origin: FlightStop = FlightStop("NPT", "Northport", "MON, 6:14 PM"),
    destination: FlightStop = FlightStop("BVW", "Bayview", "MON, 7:14 AM"),
    eta: String = "ETA 2:15 PM",
    zone: String = "Bayview Time",
    note: String = "DINNER IN 2:34H",
    totalMinutes: Int = 915,
    onSwapClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)
    val palette = if (tint == FlightTint.Lime) LimePalette else OrangePalette

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // 0…1, smoothed: is the plane flying forwards.
    val moving = remember { mutableFloatStateOf(0f) }
    // When the plane last levelled off, on the `time` axis.
    val arrivedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 1400, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        var before = target
        var airborne = false
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
            val flying = step > 0.00004f
            moving.floatValue += ((if (flying) 1f else 0f) - moving.floatValue) * min(1f, dt * 9f)
            // Levelling off after a climb sets off the sweep of light.
            if (flying && moving.floatValue > 0.5f) {
                airborne = true
            } else if (!flying && airborne && step >= 0f) {
                airborne = false
                arrivedAt.floatValue = time.floatValue
            } else if (step < 0f) {
                airborne = false
            }
        }
    }

    // Read inside draw lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    // Minutes left: recomposes the label only when the number changes.
    val left by remember(controlled, totalMinutes) {
        derivedStateOf {
            (totalMinutes * (1f - clamp01(if (controlled) held.value else loopValue(clock.floatValue)))).roundToInt()
        }
    }
    val remaining = "-${left / 60}H ${(left % 60).toString().padStart(2, '0')}M"

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { FlightPaints() }
    val noteGlow = with(LocalDensity.current) { 6.dp.toPx() }

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
            }
            .semantics {
                contentDescription = "${origin.code} ${origin.city} to ${destination.code} ${destination.city}, $eta"
                stateDescription = remaining
                progressBarRangeInfo = ProgressBarRangeInfo(1f - left / totalMinutes.toFloat(), 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val p = value()
                    val going = if (ambient > 0f) moving.floatValue else 0f
                    val bx = x + BAR_X
                    val by = y + BAR_Y
                    val cy = y + BAR_CY
                    val width = fillWidth(p)

                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 8, y + 12, WIDTH - 16, HEIGHT - 8, RADIUS) }
                    drawRoundRect(Color(0xFF080808), Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))

                    translate(x, y) {
                        clipPath(paints.card) {
                            // Tinted haze behind the route, and the dust floating in it.
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to palette.haze.copy(alpha = 0.13f),
                                    0.5f to palette.haze.copy(alpha = 0.05f),
                                    1f to palette.haze.copy(alpha = 0f),
                                    center = Offset(150f, 64f),
                                    radius = 170f,
                                ),
                                radius = 170f,
                                center = Offset(150f, 64f),
                            )
                            drawPath(paints.dust[0], palette.glow, alpha = 0.5f + ambient * 0.3f * sin(t * 1.3f))
                            drawPath(paints.dust[1], palette.glow, alpha = 0.5f + ambient * 0.3f * sin(t * 1.7f + 2.1f))
                            drawPath(paints.dust[2], palette.glow, alpha = 0.5f + ambient * 0.3f * sin(t * 1.1f + 4.2f))
                            // Bevel: a soft light rim just inside the card's edge.
                            if (CanBlur) {
                                drawIntoCanvas {
                                    it.nativeCanvas.drawRoundRect(
                                        0f, 0f, WIDTH, HEIGHT, RADIUS, RADIUS,
                                        paints.bevel.tinted(Color.White, 0.13f),
                                    )
                                }
                            }
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    0f to palette.haze.copy(alpha = 0.4f),
                                    1f to palette.haze.copy(alpha = 0f),
                                    startY = 0f,
                                    endY = HEIGHT * 0.5f,
                                ),
                                topLeft = Offset(0.5f, 0.5f),
                                size = Size(WIDTH - 1f, HEIGHT - 1f),
                                cornerRadius = CornerRadius(RADIUS),
                                style = Stroke(1f),
                            )

                            // Arrow between the airports.
                            if (CanBlur) {
                                drawIntoCanvas {
                                    it.nativeCanvas.drawPath(
                                        paints.arrow.asAndroidPath(),
                                        paints.arrowGlow.tinted(palette.arrow, 0.5f),
                                    )
                                }
                            }
                            drawPath(
                                paints.arrow, palette.arrow,
                                style = Stroke(3.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                            // A spark runs down the arrow every couple of seconds.
                            val sparkAt = (t % 2.6f) / 0.9f
                            if (CanBlur && ambient > 0f && sparkAt < 1f) {
                                drawIntoCanvas {
                                    it.nativeCanvas.drawCircle(
                                        ARROW_FROM + (ARROW_TO - ARROW_FROM) * sparkAt, ARROW_Y, 3.4f,
                                        paints.spark.tinted(Color.White, sin(PI.toFloat() * sparkAt)),
                                    )
                                }
                            }

                            // ETA box.
                            drawRoundRect(Color(0xFF040404), Offset(BOX_X, BOX_Y), Size(BOX_W, BOX_H), CornerRadius(9.5f))
                            drawRoundRect(
                                rgba(255, 255, 255, 0.14f), Offset(BOX_X, BOX_Y), Size(BOX_W, BOX_H),
                                CornerRadius(9.5f), style = Stroke(1f),
                            )
                        }
                    }

                    // Progress bar: track, glow, glossy fill.
                    pill(Color(0xFF1B1B1B), bx, by, BAR_W, BAR_H)
                    if (CanBlur) {
                        // The glow under the bar breathes.
                        drawIntoCanvas {
                            it.nativeCanvas.drawRoundRect(
                                bx, by, bx + width, by + BAR_H, BAR_H / 2, BAR_H / 2,
                                paints.glow.tinted(palette.glow, 0.62f + ambient * 0.14f * sin(t * 1.9f)),
                            )
                        }
                    }
                    paints.fill.rewind()
                    paints.fill.addRoundRect(RoundRect(Rect(bx, by, bx + width, by + BAR_H), CornerRadius(BAR_H / 2)))
                    clipPath(paints.fill) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to palette.fill[0],
                                0.5f to palette.fill[1],
                                1f to palette.fill[2],
                                startX = bx,
                                endX = bx + width,
                            ),
                            topLeft = Offset(bx, by),
                            size = Size(width, BAR_H),
                        )
                        if (CanBlur) {
                            drawIntoCanvas {
                                // Gloss: a bright rim that is strongest along the top.
                                it.nativeCanvas.drawRoundRect(
                                    bx, by, bx + width, by + BAR_H, BAR_H / 2, BAR_H / 2,
                                    paints.rim.tinted(palette.rim, 0.9f),
                                )
                                it.nativeCanvas.drawRoundRect(
                                    bx + 6, by + BAR_H - 9, bx + 6 + BAR_W, by + BAR_H + 3, 6f, 6f,
                                    paints.sheen.tinted(Color.White, 0.16f),
                                )
                            }
                        }
                        // Slipstream: short lines that fall behind the plane while it flies.
                        if (going > 0.01f) {
                            for (index in 0 until 3) {
                                val along = (t * 70f + index * 23f) % 62f
                                val lineY = cy + (index - 1) * 6.5f + (if (index == 1) 1.5f else 0f)
                                pill(
                                    Color.White, bx + width - PLANE_INSET - 16f - along, lineY - 0.6f, 9f + index * 3f, 1.2f,
                                    alpha = going * 0.7f * clamp01(along / 8f) * clamp01((62f - along) / 30f),
                                )
                            }
                        }
                        // A band of light crosses the filled part when the plane levels off.
                        val sweepAt = (t - arrivedAt.floatValue - 0.15f) / 1.1f
                        if (CanBlur && ambient > 0f && sweepAt > 0f && sweepAt < 1f) {
                            translate(bx - 30f + (width + 60f) * sweepAt, cy) {
                                drawIntoCanvas {
                                    it.nativeCanvas.drawPath(
                                        paints.sweepBand.asAndroidPath(),
                                        paints.sweep.tinted(Color.White, 0.5f * sin(PI.toFloat() * sweepAt)),
                                    )
                                }
                            }
                        }
                    }

                    // The plane noses up and lifts off its ground line while it flies, and bobs a little when level.
                    val ground = 8.6f * (1f - 0.45f * going)
                    pill(Color.White, bx + width - PLANE_INSET - ground / 2, cy + 5.1f, ground, 1.1f, alpha = 1f - 0.5f * going)
                    val bob = ambient * (1f - going) * 0.7f * sin(t * 2.3f)
                    withTransform({
                        translate(bx + width - PLANE_INSET, cy - 2.2f - 2.4f * going + bob)
                        rotate(-(0.27f + 0.2f * going) * 180f / PI.toFloat(), pivot = Offset.Zero)
                    }) {
                        drawPath(paints.plane, Color.White)
                    }
                }
            }
        }

        listOf(origin to 15.3f, destination to 134.5f).forEach { (stop, left) ->
            BasicText(stop.code, Modifier.leadingAt((left - 1f).dp, 31.dp).clearAndSetSemantics {}, CodeStyle, maxLines = 1)
            BasicText(stop.city, Modifier.leadingAt(left.dp, 52.9.dp).clearAndSetSemantics {}, CityStyle, maxLines = 1)
            BasicText(stop.time, Modifier.leadingAt(left.dp, 67.4.dp).clearAndSetSemantics {}, TimeStyle, maxLines = 1)
        }
        BasicText(eta, Modifier.leadingAt(233.dp, 37.8.dp).clearAndSetSemantics {}, EtaStyle, maxLines = 1)
        BasicText(zone, Modifier.leadingAt(233.dp, 58.3.dp).clearAndSetSemantics {}, ZoneStyle, maxLines = 1)
        BasicText(
            text = note,
            modifier = Modifier.leadingAt(233.dp, 75.4.dp).clearAndSetSemantics {},
            style = NoteStyle.copy(color = palette.note, shadow = Shadow(palette.note, Offset.Zero, noteGlow)),
            maxLines = 1,
        )
        BasicText(remaining, Modifier.trailingAt(312.5.dp, 127.3.dp).clearAndSetSemantics {}, RemainingStyle, maxLines = 1)

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val view = LocalView.current
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.9f else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        Canvas(
            modifier = Modifier
                .offset((SWAP_X - SWAP_R).dp, (SWAP_Y - SWAP_R).dp)
                .size((SWAP_R * 2).dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(Color(0xFF2D2D2D), CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onSwapClick)
                .semantics { contentDescription = "Switch time zone" },
        ) {
            val unit = density
            withTransform({
                translate(size.width / 2, size.height / 2)
                scale(unit, unit, pivot = Offset.Zero)
            }) {
                drawPath(
                    paints.swap, Color(0xFFF2F2F2),
                    style = Stroke(1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
