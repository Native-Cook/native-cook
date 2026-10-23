package com.ngocdevv.nativecook.blocks.progress.transitrouteprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 340f
private const val HEIGHT = 134f
private const val RADIUS = 37f

// The canvas overflows the card by this much on every side to fit the device body it sits in.
private val BLEED = 70.dp

private const val BAR_X = 27.2f
private const val BAR_Y = 69.1f
private const val BAR_W = 278.3f
private const val BAR_H = 15.3f
private const val BAR_CY = BAR_Y + BAR_H / 2
private const val RING_R = 15.5f

// Distance between the first and last station dots.
private const val SPAN = BAR_W - BAR_H

private val Lime = Color(0xFFB2FE5E)
private val Ink = Color(0xFFB4F56C)
private val InkDim = Color(0xFF5A8A25)

// The reference shows the train at the second station; the autoPlay loop built around it (seconds).
private const val REST = 0.5f
private const val START = 0.5f
private const val LEG = 2.4f
private const val HOLD = 3f
private const val HOLD_END = 1.8f
private const val RESET = 0.7f
private const val T2 = START + LEG + HOLD
private const val T3 = T2 + LEG + HOLD_END
private const val LOOP = T3 + RESET

// A moment in the loop where everything is at rest at the second station: used when nothing is playing.
private const val REST_T = START + LEG + 1.2f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun transitText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val LineStyle = transitText(15f, 20f, FontWeight.Medium, Ink)
private val ChipStyle = transitText(12.5f, 16f, FontWeight.SemiBold, Color(0xFF4D4500)).copy(fontFeatureSettings = "tnum")
private val StationStyle = transitText(13.5f, 20f, FontWeight.Medium, Ink)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

/** Position on the route at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < START + LEG -> 0.5f * easeInOutCubic((t - START) / LEG)
    t < T2 -> 0.5f
    t < T2 + LEG -> 0.5f + 0.5f * easeInOutCubic((t - T2) / LEG)
    t < T3 -> 1f
    else -> 1f - easeInOutCubic((t - T3) / RESET)
}

/** Marker centre, measured from the bar's left end. */
private fun markerX(p: Float) = BAR_H / 2 + SPAN * clamp01(p)

/** Which chip text fits the position: 0 far from the next stop, 1 near it, 2 there. */
private fun etaIndex(p: Float): Int {
    val leg = if (p <= 0.5f) p / 0.5f else (p - 0.5f) / 0.5f
    return if (leg >= 0.985f) 2 else if (leg >= 0.5f) 1 else 0
}

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

/** Places the element by its centre inside the card. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

private class TransitRoutePaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = 16f)

    // The ring's stroke again, wider and with a soft edge.
    val ringGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = rgba(116, 255, 171, 0.55f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val dotGlow = SoftRoundRect(rgba(255, 255, 255, 0.6f), sigma = 3f)

    val trainLegs = Path().apply {
        moveTo(7.2f, 22.4f)
        lineTo(4.6f, 26.4f)
        moveTo(18.8f, 22.4f)
        lineTo(21.4f, 26.4f)
    }
    val chevron = Path().apply {
        moveTo(-2f, -3f)
        lineTo(1.4f, 0f)
        lineTo(-2f, 3f)
    }
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2))

/**
 * Live-activity style route card: a train glyph and line name, a yellow chip with the time to the
 * next stop, and a lime route bar with three stations and a ringed marker for the train. Chevrons
 * run ahead of the train while it moves, each arrival pings (rings from the marker, the dot swells,
 * the station's name lights up), and the chip's text blurs into its next state.
 *
 * @param progress Controlled position on the route, 0…1: 0 is the first station, 0.5 the second, 1 the third.
 * @param autoPlay Rides from the first station to the second (the reference frame), on to the third, and starts over.
 * @param eta Chip text when the next stop is far, near, and reached.
 */
@Composable
fun TransitRouteProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    line: String = "Circle Line",
    stations: List<String> = listOf("Harbor", "Midtown", "Parkway"),
    eta: List<String> = listOf("2 min", "1 min", "now"),
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // 0…1, smoothed: is the train moving.
    val moving = remember { mutableFloatStateOf(0f) }
    // When the train last pulled into a station and when the chip's text last changed, on the `time` axis.
    val arrivedAt = remember { mutableFloatStateOf(-10f) }
    val chipChangedAt = remember { mutableFloatStateOf(-10f) }
    var chip by remember { mutableIntStateOf(etaIndex(target)) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 1200, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        var before = target
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
            moving.floatValue += ((if (abs(step) > 0.00002f) 1f else 0f) - moving.floatValue) * min(1f, dt * 8f)
            // Pulling into the second or third station sets off the ping.
            if ((before < 0.4995f && current >= 0.4995f) || (before < 0.9995f && current >= 0.9995f)) {
                arrivedAt.floatValue = time.floatValue
            }
            // On the way back to the start the chip already shows the full wait.
            val next = if (step < 0f) 0 else etaIndex(current)
            if (next != chip) {
                chip = next
                chipChangedAt.floatValue = time.floatValue
            }
            before = current
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val stop by remember(controlled) {
        derivedStateOf { ((if (controlled) held.value else loopValue(clock.floatValue)) * 2f).roundToInt() }
    }
    val still by remember(controlled) {
        derivedStateOf { etaIndex(if (controlled) held.value else loopValue(clock.floatValue)) }
    }
    val shown = if (reducedMotion) still else chip

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { TransitRoutePaints() }

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
            .semantics(mergeDescendants = true) {
                contentDescription = line
                stateDescription = "${stations[stop]}, ${eta[shown]}"
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val p = value()
                    val bx = x + BAR_X
                    val cy = y + BAR_CY

                    // The device the live activity sits in: a pale body that fades out below the card.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to rgba(250, 250, 250, 1f),
                            0.6f to rgba(240, 240, 240, 0.9f),
                            1f to rgba(231, 231, 231, 0f),
                            startY = y - 10f,
                            endY = y + HEIGHT + 70f,
                        ),
                        topLeft = Offset(x - 10f, y - 10f),
                        size = Size(WIDTH + 20f, HEIGHT + 80f),
                        cornerRadius = CornerRadius(RADIUS + 10f),
                    )
                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 10, y + 14, WIDTH - 20, HEIGHT - 4, RADIUS) }
                    drawRoundRect(Color.Black, Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))

                    // Train glyph: the front of a train in a 26 × 28 box.
                    translate(x + 27.2f, y + 25.6f) {
                        scale(0.98f, pivot = Offset.Zero) {
                            drawPath(paints.trainLegs, Ink, style = Stroke(2.7f, cap = StrokeCap.Round))
                            drawRoundRect(Ink, Offset(2f, 1f), Size(22f, 21f), CornerRadius(6f))
                            drawRoundRect(Color.Black, Offset(6f, 5f), Size(14f, 6.8f), CornerRadius(2.2f))
                            drawCircle(Color.Black, 1.9f, Offset(8.3f, 16.6f))
                            drawCircle(Color.Black, 1.9f, Offset(17.7f, 16.6f))
                        }
                    }

                    // Route: dim ahead, lit behind, a dot per station.
                    val at = markerX(p)
                    pill(Color(0xFF2D6E05), bx, y + BAR_Y, BAR_W, BAR_H)
                    // Chevrons run from the marker to the next station while the train is on its way there.
                    val from = at + RING_R + 4f
                    val room = BAR_H / 2 + SPAN * (if (p < 0.5f) 0.5f else 1f) - 6f - from
                    val going = moving.floatValue
                    if (room > 4f && going > 0.01f) {
                        for (index in 0 until 4) {
                            val along = (t * 30f + index * 17f) % room
                            translate(bx + from + along, cy) {
                                drawPath(
                                    path = paints.chevron,
                                    color = Lime,
                                    alpha = going * 0.75f * clamp01(along / 8f) * clamp01((room - along) / 8f),
                                    style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                    // The lit part ends in a round cap centred on the marker.
                    pill(Lime, bx, y + BAR_Y, at + BAR_H / 2, BAR_H)
                    for (station in floatArrayOf(0f, 0.5f, 1f)) {
                        drawCircle(Color.Black, 3.2f, Offset(bx + BAR_H / 2 + SPAN * station, cy))
                    }

                    translate(bx + at, cy) {
                        // Each arrival sends two rings out from the marker.
                        val since = if (ambient > 0f) t - arrivedAt.floatValue else 10f
                        for (delay in floatArrayOf(0f, 0.24f)) {
                            val spread = (since - delay) / 0.95f
                            if (spread > 0f && spread < 1f) {
                                drawCircle(
                                    color = Color(0xFF74FFAB),
                                    radius = RING_R + 26f * easeOutCubic(spread),
                                    center = Offset.Zero,
                                    alpha = 0.75f * (1f - spread),
                                    style = Stroke(1.4f),
                                )
                            }
                        }
                        val ring = RING_R + ambient * 0.7f * sin(t * 2.4f)
                        drawCircle(rgba(120, 255, 170, 0.12f), ring, Offset.Zero)
                        // Hardware canvases only honour mask filters from API 28.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            drawIntoCanvas { it.nativeCanvas.drawCircle(0f, 0f, ring, paints.ringGlow) }
                        }
                        drawCircle(Color(0xFF8DFFBA), ring, Offset.Zero, style = Stroke(1.7f))
                        drawIntoCanvas { paints.dotGlow.draw(it.nativeCanvas, -7f, -7f, 14f, 14f, 7f) }
                        // The marker's dot swells for a moment when the train arrives.
                        val swell = 1f + ambient * 0.38f * sin(PI.toFloat() * clamp01(since / 0.42f))
                        drawCircle(Color.White, 4.2f * swell, Offset.Zero)
                    }
                }
            }
        }

        BasicText(
            text = line,
            style = LineStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(63.5.dp, 37.8.dp).clearAndSetSemantics {},
        )

        Box(
            modifier = Modifier
                .offset(255.dp, 27.4.dp)
                .size(50.9.dp, 24.4.dp)
                .background(Color(0xFFF2E300), CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = eta[shown],
                style = ChipStyle,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    // 0 → 1 after the chip's text changes: it blurs in.
                    val since = time.floatValue - chipChangedAt.floatValue
                    val settle = if (ambient > 0f) EaseOutStrong.transform(clamp01(since / 0.52f)) else 1f
                    alpha = 0.25f + 0.75f * settle
                    scaleX = 0.86f + 0.14f * settle
                    scaleY = 0.86f + 0.14f * settle
                    val radius = 5.dp.toPx() * (1f - settle)
                    renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                },
            )
        }

        stations.take(3).forEachIndexed { index, name ->
            // A station's name lights up as the train pulls in.
            val color = ColorProducer { lerp(InkDim, Ink, clamp01((value() - (index * 0.5f - 0.07f)) / 0.07f)) }
            val place = when (index) {
                0 -> Modifier.leadingAt(26.dp, 100.2.dp)
                1 -> Modifier.centerAt((BAR_X + BAR_W / 2).dp, 100.2.dp)
                else -> Modifier.trailingAt((BAR_X + BAR_W - 2.5f).dp, 100.2.dp)
            }
            BasicText(text = name, style = StationStyle, maxLines = 1, color = color, modifier = place.clearAndSetSemantics {})
        }
    }
}
