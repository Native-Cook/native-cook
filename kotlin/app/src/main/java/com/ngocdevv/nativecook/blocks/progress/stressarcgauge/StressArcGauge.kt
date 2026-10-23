package com.ngocdevv.nativecook.blocks.progress.stressarcgauge

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 340f
private const val HEIGHT = 198f
private const val RADIUS = 26.5f

// The canvas overflows the card by this much on every side to fit its coloured edge glow.
private val BLEED = 24.dp

// The arc is part of a large circle whose centre lies below the card.
private const val ARC_CX = 165.1f
private const val ARC_CY = 273.1f
private const val ARC_R = 213.6f

// Degrees from straight up: where the stroke enters and leaves the card, and the 0% / 100% marks on it.
private const val ARC_FROM = -52f
private const val ARC_TO = 56.5f
private const val ZERO = -37.4f
private const val FULL = 42.8f
private const val DOT_R = 5.3f
private const val TAIL = 0.2f

// The reference shows 85% (red) and 50% (green); the autoPlay loop visits both (seconds).
private const val REST = 0.85f
private const val CALM = 0.5f
private const val BASE = 0.2f
private const val RISE = 1.6f
private const val T1 = 0.3f
private const val T2 = T1 + RISE + 2.3f
private const val T3 = T2 + RISE + 2.9f
private const val FALL = 0.9f
private const val LOOP = T3 + FALL

// A moment in the loop where everything is at rest at 85%: used when nothing is playing.
private const val REST_T = T3 - 1f

// Calm, in-between and stressed colours for the stroke, the text and the ambient light.
private val LineStops = arrayOf(Color(47, 255, 71), Color(255, 214, 60), Color(255, 116, 47))
private val InkStops = arrayOf(Color(99, 226, 131), Color(246, 214, 110), Color(241, 128, 111))
private val LightStops = arrayOf(Color(38, 150, 36), Color(150, 120, 30), Color(190, 52, 30))
private val EdgeStops = arrayOf(Color(90, 255, 120), Color(255, 214, 120), Color(255, 130, 110))

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun stressText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = stressText(14.5f, 20f, FontWeight.SemiBold, Color.White)
private val CaptionStyle = stressText(9.5f, 14f, FontWeight.Normal, Color(0xFFF2F2F2))
private val NumberStyle = stressText(28.5f, 36f, FontWeight.Normal, Color.White).copy(fontFeatureSettings = "tnum")
private val ChipStyle = stressText(10.5f, 14f, FontWeight.SemiBold, Color.White)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Stress level at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < T1 -> BASE
    t < T1 + RISE -> BASE + (CALM - BASE) * easeInOutCubic((t - T1) / RISE)
    t < T2 -> CALM
    t < T2 + RISE -> CALM + (REST - CALM) * easeInOutCubic((t - T2) / RISE)
    t < T3 -> REST
    else -> REST - (REST - BASE) * easeInOutCubic((t - T3) / FALL)
}

/** Degrees from straight up at which the marker sits. */
private fun markerAngle(p: Float) = ZERO + (FULL - ZERO) * clamp01(p)

/** Calm → in-between → stressed, crossing over between 56% and 72%. */
private fun blend(stops: Array<Color>, p: Float, alpha: Float = 1f): Color {
    val t = clamp01((p - 0.56f) / 0.16f) * 2f
    val a = if (t < 1f) stops[0] else stops[1]
    val b = if (t < 1f) stops[1] else stops[2]
    val f = if (t < 1f) t else t - 1f
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = alpha,
    )
}

/** Heartbeat for a phase 0…1: a strong thump followed by a softer one. */
private fun heartbeat(phase: Float): Float {
    val a = (phase - 0.1f) / 0.055f
    val b = (phase - 0.32f) / 0.055f
    return exp(-a * a) + 0.55f * exp(-b * b)
}

/** Places the element by its leading edge and vertical centre inside the card. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its centre inside the card. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

/** A soft-edged paint whose colour can change every frame. Drawn in the current (dp) units. */
private class StressGlow(sigma: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    private fun prepare(color: Color, stroke: Float?): Boolean {
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        paint.color = color.toArgb()
        paint.style = if (stroke == null) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = stroke ?: 0f
        return true
    }

    fun arc(canvas: android.graphics.Canvas, color: Color, width: Float, fromDeg: Float, toDeg: Float) {
        if (!prepare(color, width)) return
        canvas.drawArc(
            ARC_CX - ARC_R, ARC_CY - ARC_R, ARC_CX + ARC_R, ARC_CY + ARC_R, fromDeg - 90f, toDeg - fromDeg, false, paint,
        )
    }

    fun circle(canvas: android.graphics.Canvas, color: Color, center: Offset, radius: Float) {
        if (prepare(color, null)) canvas.drawCircle(center.x, center.y, radius, paint)
    }

    fun roundRect(canvas: android.graphics.Canvas, color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
        if (prepare(color, null)) canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
}

private class StressArcPaints {
    val edge = StressGlow(4f)
    val lineGlow = StressGlow(4f)
    val tailGlow = StressGlow(3f)
    val tailCore = StressGlow(1.2f)
    val dotGlow = StressGlow(5f)
    val card = Path()
    private var cardX = Float.NaN

    // Person holding their head, in an 18 × 18 box.
    val iconArms = PathParser().parsePathString(
        "M5.6 11.7C2.2 10.7 1.6 6.7 4.4 4.7C5.2 4.1 6 4.1 6.6 4.5M12.4 11.7C15.8 10.7 16.4 6.7 13.6 4.7C12.8 4.1 12 4.1 11.4 4.5",
    ).toPath()
    val iconBody = PathParser().parsePathString(
        "M5 17.6V12.5C5 11.4 5.9 10.6 7 10.6H11C12.1 10.6 13 11.4 13 12.5V17.6Z",
    ).toPath()
    val iconRays = PathParser().parsePathString("M9 0.2V1.8M6.2 0.9L6.9 2.2M11.8 0.9L11.1 2.2").toPath()
    val arrow = PathParser().parsePathString("M1.5 9L9 1.5M3.2 1.5H9V7.3").toPath()

    fun update(x: Float, y: Float) {
        if (x == cardX) return
        cardX = x
        card.reset()
        card.addRoundRect(RoundRect(x, y, x + WIDTH, y + HEIGHT, CornerRadius(RADIUS)))
    }
}

/** Part of the arc between two angles (degrees from straight up), in card coordinates. */
private fun DrawScope.arc(color: Color, width: Float, fromDeg: Float, toDeg: Float, alpha: Float = 1f) = drawArc(
    color = color,
    startAngle = fromDeg - 90f,
    sweepAngle = toDeg - fromDeg,
    useCenter = false,
    topLeft = Offset(ARC_CX - ARC_R, ARC_CY - ARC_R),
    size = Size(ARC_R * 2, ARC_R * 2),
    alpha = alpha,
    style = Stroke(width, cap = StrokeCap.Round),
)

/**
 * Mental-health widget: a dark card crossed by a wide glowing arc with a marker on it, the stress
 * level as a large percentage and a chip naming the state. The marker drags a comet tail while it
 * moves, beats like a heart (faster as stress rises, a ring going out with each beat), and the
 * whole card shifts from calm green to stressed red as the level climbs.
 *
 * @param progress Controlled stress level, 0…1. Leave null to show the reference's 85% (or the autoPlay loop).
 * @param autoPlay Climbs to 50% ("Balanced", green), then to 85% ("High", red), holds each, and starts over.
 * @param levels State names for the chip: below 35%, up to 70%, and above.
 */
@Composable
fun StressArcGauge(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Stress",
    caption: String = "Your stress level",
    levels: List<String> = listOf("Low", "Balanced", "High"),
    onOpenClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val isControlled by rememberUpdatedState(controlled)

    // Seconds: `clock` is the position in the autoPlay loop.
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // Phase of the heartbeat, 0…1, and how fast the marker is moving (−1…1, smoothed).
    val beat = remember { mutableFloatStateOf(0.6f) }
    val tail = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 900, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        var before = target
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
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
            // The heart beats 54 times a minute when calm and up to 114 when stressed.
            beat.floatValue = (beat.floatValue + dt * (0.9f + current)) % 1f
            val speed = ((current - before) / max(dt, 0.001f) / 0.22f).coerceIn(-1f, 1f)
            tail.floatValue += (speed - tail.floatValue) * min(1f, dt * 9f)
            before = current
        }
    }

    // Read inside draw lambdas so a frame only redraws, it does not recompose.
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    // The number and the chip are real text: recompose only when the whole percent changes.
    val percent by remember(controlled) {
        derivedStateOf { ((if (controlled) held.value else loopValue(clock.floatValue)) * 100f).roundToInt() }
    }
    val level = when {
        percent < 35 -> levels[0]
        percent < 70 -> levels[1]
        else -> levels[2]
    }
    val ink = ColorProducer { blend(InkStops, value()) }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { StressArcPaints() }

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
                contentDescription = "$title. $caption"
                stateDescription = "$percent%, $level"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    paints.update(x, y)
                    val p = value()
                    val phase = beat.floatValue
                    val throb = ambient * heartbeat(phase)
                    val line = blend(LineStops, p)
                    val angle = markerAngle(p)
                    val rad = angle * PI.toFloat() / 180f
                    // Marker centre in card coordinates.
                    val mx = ARC_CX + ARC_R * sin(rad)
                    val my = ARC_CY - ARC_R * cos(rad)

                    drawIntoCanvas {
                        paints.edge.roundRect(
                            it.nativeCanvas, blend(EdgeStops, p, 0.36f), x + 6, y + 8, WIDTH - 12, HEIGHT - 4, RADIUS,
                        )
                    }

                    clipPath(paints.card) {
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color(0xFF222222),
                                0.2f to Color(0xFF232323),
                                0.47f to Color(0xFF3A3A3A),
                                0.64f to Color(0xFF303030),
                                1f to Color(0xFF373737),
                                start = Offset(x, y),
                                end = Offset(x + WIDTH, y + 30f),
                            ),
                            topLeft = Offset(x, y),
                            size = Size(WIDTH, HEIGHT),
                        )
                        // Ambient light on the right of the card: it leans towards the marker and throbs with the heartbeat.
                        val strength = 0.5f + 0.14f * throb
                        val lightAt = Offset(x + 259f + 0.25f * (mx - 259f), y + 82f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to blend(LightStops, p, strength),
                                0.5f to blend(LightStops, p, strength * 0.45f),
                                1f to blend(LightStops, p, 0f),
                                center = lightAt,
                                radius = 125f,
                            ),
                            radius = 125f,
                            center = lightAt,
                        )

                        translate(x, y) {
                            // What is still ahead of the marker is faint, what is behind glows.
                            arc(line, 1.6f, angle, ARC_TO, alpha = 0.34f)
                            drawIntoCanvas {
                                paints.lineGlow.arc(it.nativeCanvas, line.copy(alpha = 0.5f), 5f, ARC_FROM, angle)
                            }
                            arc(line, 1.6f, ARC_FROM, angle)
                            // While the marker moves it drags a bright tail along the stroke, like a comet.
                            val drag = tail.floatValue
                            val tailAlpha = min(1f, abs(drag) * 1.4f)
                            if (tailAlpha > 0.01f) {
                                val other = (angle - TAIL * drag * (ARC_TO - ARC_FROM)).coerceIn(ARC_FROM, ARC_TO)
                                val from = min(angle, other)
                                val to = max(angle, other)
                                drawIntoCanvas {
                                    paints.tailGlow.arc(it.nativeCanvas, line.copy(alpha = tailAlpha), 5f, from, to)
                                    paints.tailCore.arc(it.nativeCanvas, Color.White.copy(alpha = tailAlpha), 2.4f, from, to)
                                }
                            }

                            // Each beat sends a ring out from the marker.
                            val marker = Offset(mx, my)
                            if (ambient > 0f && phase >= 0.1f) {
                                val spread = clamp01((phase - 0.1f) / 0.75f)
                                drawCircle(line, DOT_R + 17f * spread, marker, alpha = 0.5f * (1f - spread), style = Stroke(1.2f))
                            }
                            drawIntoCanvas { paints.dotGlow.circle(it.nativeCanvas, line.copy(alpha = 0.45f), marker, DOT_R + 3f) }
                            drawCircle(line, DOT_R * (1f + 0.16f * throb), marker)
                        }
                    }

                    // Person holding their head; the lines above it twitch with the heartbeat when stress is high.
                    translate(x + 18.8f, y + 14.4f) {
                        drawPath(
                            paints.iconArms,
                            Color.White,
                            style = Stroke(2.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                        drawPath(paints.iconBody, Color.White)
                        drawCircle(Color(0xFF262626), 3.9f, Offset(9f, 6.8f))
                        drawCircle(Color.White, 3f, Offset(9f, 6.8f))
                        drawPath(
                            paints.iconRays,
                            Color.White,
                            alpha = (1f - 0.55f * throb * clamp01((p - 0.56f) / 0.16f)).coerceIn(0f, 1f),
                            style = Stroke(0.9f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }

        BasicText(
            text = title,
            style = TitleStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(45.8.dp, 23.dp).clearAndSetSemantics {},
        )
        // The reference centres its text a touch left of the card's middle.
        BasicText(
            text = caption,
            style = CaptionStyle,
            maxLines = 1,
            modifier = Modifier.centerAt(168.5.dp, 97.4.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = "$percent%",
            style = NumberStyle,
            maxLines = 1,
            color = ink,
            modifier = Modifier.centerAt(168.5.dp, 127.8.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = level,
            style = ChipStyle,
            maxLines = 1,
            color = ink,
            modifier = Modifier
                .centerAt(168.5.dp, 160.3.dp)
                .height(20.2.dp)
                .background(rgba(255, 255, 255, 0.2f), CircleShape)
                .border(1.dp, rgba(255, 255, 255, 0.22f), CircleShape)
                .padding(horizontal = 14.dp)
                .wrapContentHeight()
                .clearAndSetSemantics {},
        )

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val press by animateFloatAsState(
            targetValue = if (pressed) PRESS_SCALE else 1f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        // A generous touch target around the small arrow.
        Box(
            modifier = Modifier
                .offset(300.2.dp, 3.7.dp)
                .size(38.5.dp)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpenClick)
                .semantics { contentDescription = "Open" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier.size(10.5.dp).graphicsLayer {
                    scaleX = press
                    scaleY = press
                },
            ) {
                // The arrow is authored in dp.
                scale(density, density, pivot = Offset.Zero) {
                    drawPath(paints.arrow, Color.White, style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
