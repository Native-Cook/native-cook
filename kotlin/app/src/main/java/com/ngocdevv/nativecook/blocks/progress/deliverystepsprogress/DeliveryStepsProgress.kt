package com.ngocdevv.nativecook.blocks.progress.deliverystepsprogress

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 356f
private const val HEIGHT = 148f
private const val RADIUS = 22f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 44.dp

private const val STEP_Y = 67.6f
private const val STEP_X = 28.2f
private const val LEG = 74.8f
private const val SPAN = LEG * 4
private const val DOT_R = 12f
private const val LINE_H = 6f

private val Blue = Color(0xFF2490FF)

// The reference shows the driver 60% of the way to the second step; the autoPlay loop built around it.
private const val REST = 0.15f

// Seconds, from, to.
private val Segments = arrayOf(
    floatArrayOf(0.5f, 0f, 0f),
    floatArrayOf(1.5f, 0f, REST),
    floatArrayOf(2.8f, REST, REST),
    floatArrayOf(0.8f, REST, 0.25f),
    floatArrayOf(0.6f, 0.25f, 0.25f),
    floatArrayOf(1.1f, 0.25f, 0.5f),
    floatArrayOf(0.6f, 0.5f, 0.5f),
    floatArrayOf(1.1f, 0.5f, 0.75f),
    floatArrayOf(0.6f, 0.75f, 0.75f),
    floatArrayOf(1.1f, 0.75f, 1f),
    floatArrayOf(2.2f, 1f, 1f),
    floatArrayOf(0.8f, 1f, 0f),
)
private val LOOP = Segments.sumOf { it[0].toDouble() }.toFloat()

// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
private const val REST_T = 0.5f + 1.5f + 1.4f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun deliveryText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

// Left to right the headline runs from soft blue through aqua to white.
private val TitleStyle = deliveryText(16.6f, 22f, FontWeight.Normal, Color.White, -0.5f).copy(
    brush = Brush.horizontalGradient(
        0f to Color(0xFF86B4F2),
        0.4f to Color(0xFF7EE2EE),
        0.66f to Color(0xFFE2F8FC),
        1f to Color.White,
    ),
)
private val EtaStyle = deliveryText(10.8f, 16f, FontWeight.Medium, Color.White, -0.2f)
private val InitialsStyle = deliveryText(12f, 16f, FontWeight.SemiBold, Color.White, 0.3f)
private val DriverStyle = deliveryText(13.2f, 18f, FontWeight.Medium, Color.White, -0.3f)
private val VehicleStyle = deliveryText(10.8f, 16f, FontWeight.Normal, Color(0xFFA7BCDC), -0.2f)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun easeOutCubic(t: Float) = 1f - (1f - clamp01(t)).pow(3)

/** Position along the steps at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float): Float {
    var at = t
    for (segment in Segments) {
        if (at < segment[0]) return segment[1] + (segment[2] - segment[1]) * easeInOutCubic(at / segment[0])
        at -= segment[0]
    }
    return 0f
}

/** Which headline fits the position: the leg being travelled, or 4 once the last step is reached. */
private fun legIndex(p: Float) = min(4, (clamp01(p) * 4f + 0.004f).toInt())

/** 0…1: how lit step [index] is. */
private fun lit(p: Float, index: Int) = if (index == 0) 1f else clamp01((p - (index / 4f - 0.012f)) / 0.012f)

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

// Hardware canvases only honour mask filters from API 28: soft layers are skipped below that.
private val CanBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun softPaint(sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    if (CanBlur) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun Paint.tinted(color: Color, alpha: Float = 1f): Paint {
    this.color = color.copy(alpha = (color.alpha * alpha).coerceIn(0f, 1f)).toArgb()
    return this
}

private fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

private class DeliveryPaints {
    val shadow = SoftRoundRect(rgba(20, 50, 110, 0.42f), sigma = 18f)
    val headGlow = softPaint(5f)
    val pulse = softPaint(1.2f)
    val halo = softPaint(6f)

    // Step glyphs, drawn around the dot's centre: parcel, garment, washer, van, home.
    val glyphs = listOf(
        "M0 -5.4L4.7 -2.7V2.7L0 5.4L-4.7 2.7V-2.7ZM-4.7 -2.7L0 0L4.7 -2.7M0 0V5.4",
        "M-2 -4.9L-5.4 -2.9L-4.1 -0.5L-2.9 -1.2V4.9H2.9V-1.2L4.1 -0.5L5.4 -2.9L2 -4.9C1.4 -3.5 -1.4 -3.5 -2 -4.9Z",
        "M-3.6 -5.2H3.6Q4.8 -5.2 4.8 -4V4Q4.8 5.2 3.6 5.2H-3.6Q-4.8 5.2 -4.8 4V-4Q-4.8 -5.2 -3.6 -5.2ZM-2.9 -3.2H-1.5M0 -1.5C1.5 -1.5 2.6 -0.4 2.6 1.1C2.6 2.6 1.5 3.7 0 3.7C-1.5 3.7 -2.6 2.6 -2.6 1.1C-2.6 -0.4 -1.5 -1.5 0 -1.5Z",
        "M-5.6 -3.6H0.8V2.6H-5.6ZM0.8 -1.4H3.5L5.6 0.8V2.6H0.8M-3.6 2.6C-3.6 3.5 -3 4.1 -2.2 4.1C-1.4 4.1 -0.8 3.5 -0.8 2.6M2 2.6C2 3.5 2.6 4.1 3.4 4.1C4.2 4.1 4.8 3.5 4.8 2.6",
        "M-5.4 -0.4L0 -5.1L5.4 -0.4M-3.9 -1.5V4.9H3.9V-1.5M-1.2 4.9V1.7H1.2V4.9",
    ).map(::svgPath)
    val phone = svgPath(
        "M-3 -5.2C-2.4 -5.4 -1.8 -5.1 -1.6 -4.5L-0.8 -2.4C-0.6 -1.9 -0.8 -1.4 -1.2 -1.1L-2.1 -0.4C-1.3 1.2 -0.1 2.4 1.5 3.2L2.2 2.3C2.5 1.9 3 1.7 3.5 1.9L5.6 2.7C6.2 2.9 6.5 3.5 6.3 4.1C5.9 5.4 4.8 6 3.6 5.8C-1.1 5 -4.7 1.4 -5.5 -3.3C-5.7 -4.2 -4.3 -4.8 -3 -5.2Z",
    )
    val bubble = svgPath(
        "M-4.3 -4.8H4.3Q5.7 -4.8 5.7 -3.4V1.6Q5.7 3 4.3 3H-1.5L-4.3 5.4V3Q-5.7 3 -5.7 1.6V-3.4Q-5.7 -4.8 -4.3 -4.8Z",
    )
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2))

/**
 * Deep blue glass order card: a headline and ETA, five ringed steps joined by a line, and the
 * driver's row with call and message buttons. A glowing head travels the current leg with pulses
 * of light running into it; reaching a step pops its dot, sends a ring out and lights its glyph,
 * the headline blurs into its next state and the ETA counts down.
 *
 * @param progress Controlled position along the five steps, 0…1: every quarter is one step reached.
 * @param autoPlay Heads for the second step (the reference frame), holds, then runs through the
 *   remaining steps and starts over.
 * @param titles Headline while heading for steps 2…5, and once the last one is reached.
 * @param tripMinutes Minutes the whole trip takes: the ETA counts down from it.
 */
@Composable
fun DeliveryStepsProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    titles: List<String> = listOf(
        "Driver Heading to Laundry", "Laundry in Progress", "Packed and Ready", "Driver Heading to You", "Order Delivered",
    ),
    tripMinutes: Float = 3f,
    driver: String = "Sam Carter",
    vehicle: String = "Compact Wagon",
    plate: String = "CAK 3816",
    onCallClick: () -> Unit = {},
    onMessageClick: () -> Unit = {},
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
    // The step that was reached last and when, on the `time` axis.
    val popped = remember { mutableIntStateOf(-1) }
    val poppedAt = remember { mutableFloatStateOf(-10f) }
    // Which headline is showing, and when it last changed.
    var leg by remember { mutableIntStateOf(legIndex(target)) }
    val legChangedAt = remember { mutableFloatStateOf(-10f) }

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
            // Reaching a step sets off its pop.
            for (index in 1..4) {
                val at = index / 4f - 0.002f
                if (before < at && current >= at) {
                    popped.intValue = index
                    poppedAt.floatValue = time.floatValue
                }
            }
            // On the way back to the start the headline already shows the first leg.
            val next = if (current < before) 0 else legIndex(current)
            if (next != leg) {
                leg = next
                legChangedAt.floatValue = time.floatValue
            }
            before = current
        }
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val still by remember(controlled) {
        derivedStateOf { legIndex(if (controlled) held.value else loopValue(clock.floatValue)) }
    }
    val minutes by remember(controlled, tripMinutes) {
        derivedStateOf {
            ceil(tripMinutes * (1f - clamp01(if (controlled) held.value else loopValue(clock.floatValue))) - 0.001f).roundToInt()
        }
    }
    val step by remember(controlled) {
        derivedStateOf { (clamp01(if (controlled) held.value else loopValue(clock.floatValue)) * 4f + 0.004f).toInt() + 1 }
    }
    val shown = if (reducedMotion) still else leg
    val title = titles[shown]
    val eta = if (minutes <= 0) "Arrived" else "ETA : $minutes minute${if (minutes == 1) "" else "s"}"
    val initials = driver.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { DeliveryPaints() }

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
                    val p = clamp01(value())
                    val lx = x + STEP_X
                    val cy = y + STEP_Y

                    // Soft shadow under the card.
                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 14, y + 26, WIDTH - 28, HEIGHT - 16, RADIUS) }
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color(0xFF01030A),
                            0.3f to Color(0xFF03112E),
                            0.75f to Color(0xFF09306F),
                            1f to Color(0xFF0C4296),
                            startY = y,
                            endY = y + HEIGHT,
                        ),
                        topLeft = Offset(x, y),
                        size = Size(WIDTH, HEIGHT),
                        cornerRadius = CornerRadius(RADIUS),
                    )
                    // Glass edge: a light rim that grows towards the bottom.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to rgba(120, 170, 255, 0f),
                            1f to rgba(120, 170, 255, 0.5f),
                            startY = y + HEIGHT * 0.35f,
                            endY = y + HEIGHT,
                        ),
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(WIDTH - 1f, HEIGHT - 1f),
                        cornerRadius = CornerRadius(RADIUS - 0.5f),
                        style = Stroke(1f),
                    )

                    // Route: dim line, lit part, glowing head.
                    val head = lx + SPAN * p
                    pill(Color(0xFF0D3D92), lx, cy - LINE_H / 2, SPAN, LINE_H)
                    // The bright head hides while it sits under a step's dot.
                    val off = abs(p * 4f - (p * 4f).roundToInt()) * LEG
                    val headShown = clamp01((off - 9f) / 7f)
                    if (CanBlur && headShown > 0f) {
                        drawIntoCanvas {
                            it.nativeCanvas.drawRoundRect(
                                head - 30f, cy - LINE_H / 2, head + 2f, cy + LINE_H / 2, LINE_H / 2, LINE_H / 2,
                                paints.headGlow.tinted(Color(0xFF6FDCFF), headShown * (0.85f + ambient * 0.15f * sin(t * 3f))),
                            )
                        }
                    }
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            0f to Blue,
                            0.65f to Color(0xFF7FE4FF),
                            1f to Color(0xFFF2FEFF),
                            startX = head - 46f,
                            endX = head,
                        ),
                        topLeft = Offset(lx, cy - LINE_H / 2),
                        size = Size(head - lx + LINE_H / 2, LINE_H),
                        cornerRadius = CornerRadius(LINE_H / 2),
                    )
                    // Pulses of light run from the last step to the head.
                    if (CanBlur && ambient > 0f && headShown > 0f) {
                        val pulseAt = (t * 0.8f) % 1f
                        val from = lx + LEG * floor(p * 4f - 0.0001f) + DOT_R
                        val px = from + (head - 8f - from) * pulseAt - 6f
                        drawIntoCanvas {
                            it.nativeCanvas.drawRoundRect(
                                px, cy - 1.2f, px + 12f, cy + 1.2f, 1.2f, 1.2f,
                                paints.pulse.tinted(Color.White, headShown * 0.9f * sin(PI.toFloat() * pulseAt)),
                            )
                        }
                    }

                    for (index in 0 until 5) {
                        val on = lit(p, index)
                        val since = if (ambient > 0f && popped.intValue == index) t - poppedAt.floatValue else 10f
                        // Reaching a step pops its dot with a little overshoot and sends a ring out.
                        val u = clamp01(since / 0.55f)
                        val pop = 1f + ambient * 0.5f * sin(PI.toFloat() * u) * (1f - u)
                        withTransform({
                            translate(lx + LEG * index, cy)
                            scale(pop, pop, pivot = Offset.Zero)
                        }) {
                            val spread = since / 0.85f
                            if (spread < 1f) {
                                drawCircle(
                                    color = Color(0xFF7FD0FF),
                                    radius = DOT_R + 15f * easeOutCubic(spread),
                                    center = Offset.Zero,
                                    alpha = 0.8f * (1f - spread),
                                    style = Stroke(1.5f),
                                )
                            }
                            // The step the driver left last keeps a halo that breathes.
                            val current = if (legIndex(p) == index || (index == 4 && p > 0.99f)) 1f else 0.35f
                            if (CanBlur) {
                                drawIntoCanvas {
                                    it.nativeCanvas.drawCircle(
                                        0f, 0f, DOT_R + 2f,
                                        paints.halo.tinted(Blue, on * current * (0.5f + ambient * 0.14f * sin(t * 2.2f))),
                                    )
                                }
                            }
                            drawCircle(lerp(Color(0xFF0E3F94), Blue, on), DOT_R, Offset.Zero)
                            drawCircle(rgba(96, 146, 232, 0.5f * (1f - on)), DOT_R - 0.5f, Offset.Zero, style = Stroke(1f))
                            drawPath(
                                paints.glyphs[index], lerp(Color(0xFF3F74C6), Color.White, on),
                                style = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }

                    // Driver's avatar tile.
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF7B8FA8), Color(0xFF46586F)),
                            start = Offset(x + 16f, y + 101f),
                            end = Offset(x + 47f, y + 132f),
                        ),
                        topLeft = Offset(x + 16.1f, y + 100.9f),
                        size = Size(31.4f, 31.4f),
                        cornerRadius = CornerRadius(9f),
                    )
                    drawRoundRect(
                        rgba(255, 255, 255, 0.22f), Offset(x + 16.6f, y + 101.4f), Size(30.4f, 30.4f),
                        CornerRadius(8.5f), style = Stroke(1f),
                    )
                }
            }
        }

        BasicText(
            text = title,
            style = TitleStyle,
            maxLines = 1,
            modifier = Modifier
                .leadingAt(16.9.dp, 25.5.dp)
                .graphicsLayer {
                    // 0 → 1 after the headline changes: it blurs in.
                    val since = time.floatValue - legChangedAt.floatValue
                    val settle = if (ambient > 0f) EaseOutStrong.transform(clamp01(since / 0.56f)) else 1f
                    alpha = 0.2f + 0.8f * settle
                    translationY = 5.dp.toPx() * (1f - settle)
                    val radius = 6.dp.toPx() * (1f - settle)
                    renderEffect = if (radius > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                }
                .semantics {
                    contentDescription = title
                    stateDescription = "Step $step of 5, $eta"
                },
        )
        BasicText(eta, Modifier.trailingAt(338.6.dp, 25.5.dp), EtaStyle, maxLines = 1)
        BasicText(
            initials, Modifier.centerAt((16.1f + 15.7f).dp, 116.6.dp).clearAndSetSemantics {}, InitialsStyle, maxLines = 1,
        )
        BasicText(driver, Modifier.leadingAt(59.dp, 111.3.dp), DriverStyle, maxLines = 1)
        Row(
            modifier = Modifier.leadingAt(59.dp, 124.8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(vehicle, style = VehicleStyle, maxLines = 1)
            Box(Modifier.size(1.dp, 11.dp).background(rgba(167, 188, 220, 0.35f)))
            BasicText(plate, style = VehicleStyle, maxLines = 1)
        }

        DeliveryPill(paints.phone, "Call $driver", 238.2f, 48.1f, onCallClick)
        DeliveryPill(paints.bubble, "Message $driver", 292.4f, 47f, onMessageClick)
    }
}

@Composable
private fun DeliveryPill(icon: Path, label: String, x: Float, width: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Canvas(
        modifier = Modifier
            .offset(x.dp, 108.7.dp)
            .size(width.dp, 23.9.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(rgba(255, 255, 255, 0.26f), CircleShape)
            .border(1.dp, rgba(255, 255, 255, 0.16f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        val unit = density
        withTransform({
            translate(size.width / 2, size.height / 2)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            drawPath(icon, Color.White)
        }
    }
}
