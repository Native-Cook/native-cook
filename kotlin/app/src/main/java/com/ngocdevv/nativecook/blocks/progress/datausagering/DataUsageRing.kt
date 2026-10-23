package com.ngocdevv.nativecook.blocks.progress.datausagering

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 280f
private const val HEIGHT = 366f
private const val RADIUS = 11f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 30.dp

private const val CX = 140f
private const val CY = 180.5f
private const val R_OUT = 116f
private const val R_IN = 69f
private const val R_MID = (R_OUT + R_IN) / 2f
private const val THICK = R_OUT - R_IN

// The arc's corners are rounded by stroking a slightly smaller sector with round joins.
private const val CORNER = 3f
private const val TICKS = 12
private const val TICK_R = 95.8f

// Sweep gradients start at 3 o'clock; the arc is drawn in a frame turned so the seam sits just
// before 12 o'clock.
private const val SEAM = -96f

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.36f
private const val START = 0.45f
private const val DRAW = 1.9f
private const val HOLD = 3.9f
private const val RESET = 0.75f
private const val LOOP = START + DRAW + HOLD + RESET
private const val LANDED = START + DRAW

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = LANDED + 1.6f
private const val TICK_PERIOD = 3.4f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun ringText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val TitleStyle = ringText(19f, 25f, FontWeight.Bold, Color(0xFF232323)).copy(letterSpacing = (-0.3).sp)
private val NumberStyle = ringText(48f, 58f, FontWeight.Bold, Color.Black)
    .copy(letterSpacing = (-1.2).sp, fontFeatureSettings = "tnum")
private val PercentStyle = ringText(11.5f, 14f, FontWeight.Bold, Color.Black)
private val AmountStyle = ringText(9.8f, 14f, FontWeight.Normal, Color(0xFF9A9A9A)).copy(letterSpacing = (-0.2).sp)
private val RenewsStyle = ringText(11.6f, 18f, FontWeight.Normal, Color(0xFF111111)).copy(letterSpacing = (-0.2).sp)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Usage at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < LANDED -> REST * easeInOutCubic((t - START) / DRAW)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how fast the head of the arc is travelling at [t] seconds into the loop. */
private fun loopActivity(t: Float): Float {
    val u = (t - START) / DRAW
    return if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
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

private class DataRingPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.1f), sigma = 14f)
    val dial = Path().apply { addOval(Rect(Offset(CX, CY), R_OUT)) }
    val sector = Path()

    val green = Brush.sweepGradient(
        0.017f to Color(0xFF35D655),
        0.2f to Color(0xFF58EE6C),
        0.39f to Color(0xFF42DE60),
        1f to Color(0xFF42DE60),
        center = Offset(CX, CY),
    )
    val rims = Brush.radialGradient(
        R_IN / R_OUT to rgba(20, 150, 54, 0.2f),
        R_IN / R_OUT + 0.09f to rgba(20, 150, 54, 0f),
        0.93f to rgba(20, 150, 54, 0f),
        1f to rgba(20, 150, 54, 0.2f),
        center = Offset(CX, CY),
        radius = R_OUT,
    )

    private fun soft(color: Color, sigma: Float, stroke: Float?) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (stroke != null) {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    val rim = soft(rgba(0, 0, 0, 0.17f), 5f, 7f)
    val haze = soft(rgba(84, 226, 104, 0.3f), 11f, THICK + 10f)
    val hazeCore = soft(rgba(84, 226, 104, 0.45f), 11f, THICK + 10f)
    val sheen = soft(Color.White, 7f, THICK - 10f)
    val cap = soft(rgba(214, 255, 220, 0.85f), 2.5f, THICK - 8f)
    val ripple = soft(Color.White, 1.5f, 2.5f)
    val hub = soft(rgba(0, 0, 0, 0.26f), 8f, null)

    /** Strokes an arc along the middle of the ring; [alpha] scales the paint's own alpha. */
    fun arc(canvas: android.graphics.Canvas, paint: Paint, base: Float, from: Float, to: Float, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val sweep = (to - from).coerceIn(0.01f, 359.5f)
        paint.alpha = (255f * base * alpha).roundToInt().coerceIn(0, 255)
        canvas.drawArc(CX - R_MID, CY - R_MID, CX + R_MID, CY + R_MID, from, sweep, false, paint)
    }
}

/**
 * White "Summary" card with a thick green usage ring on a soft grey dial with tick dots, the
 * percentage and the used / total amount in its raised hub and the renewal line below. The arc
 * is drawn with a bright leading cap and a haze that stretches ahead of it, each tick swells as
 * the head reaches it, the hub figure pops and sends a ripple across the dial when the arc
 * lands, then a sheen glides along the arc and a slow pulse keeps going round the ticks.
 *
 * @param progress Controlled usage, 0…1 of the plan. Leave null to show the reference's 36% (or the autoPlay loop).
 * @param autoPlay Draws the ring from 0 to 36% by itself, holds, unwinds and starts over.
 * @param totalGB Size of the plan in GB; the used amount is derived from the progress.
 */
@Composable
fun DataUsageRing(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Summary",
    totalGB: Int = 10,
    renews: String = "Renews 1st August",
    editLabel: String = "Edit",
    onEditClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When a controlled value last changed, on the `time` axis.
    val movedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        movedAt.floatValue = time.floatValue
        held.animateTo(target, tween(if (reducedMotion) 200 else 800, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            time.floatValue += dt
            if (!playing) continue
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
    }

    // Read inside draw / layer lambdas so a frame only redraws, it does not recompose.
    val value = { clamp01(if (controlled) held.value else loopValue(clock.floatValue)) }
    val activity = {
        when {
            reducedMotion -> 0f
            !controlled -> loopActivity(clock.floatValue)
            else -> {
                val u = (time.floatValue - movedAt.floatValue) / 0.7f
                if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
            }
        }
    }
    // Seconds since the arc landed on its value; large when nothing happened.
    val landed = {
        when {
            reducedMotion -> 10f
            !controlled -> if (clock.floatValue < LOOP - RESET) clock.floatValue - LANDED else 10f
            else -> time.floatValue - movedAt.floatValue - 0.6f
        }
    }
    // The figures are real text: recompose only when the whole percent changes.
    val percent by remember(controlled) {
        derivedStateOf { (clamp01(if (controlled) held.value else loopValue(clock.floatValue)) * 100f).roundToInt() }
    }
    val used = String.format(Locale.US, "%.1f", percent / 100f * totalGB)

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { DataRingPaints() }

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
                contentDescription = "$title, ${used}GB of ${totalGB}GB used"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = value()
                    val t = time.floatValue
                    val busy = activity()
                    val since = landed()
                    translate(x, y) {
                        drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, 4f, 10f, WIDTH - 8f, HEIGHT - 8f, RADIUS) }
                        drawRoundRect(Color.White, Offset.Zero, Size(WIDTH, HEIGHT), CornerRadius(RADIUS))
                        drawRoundRect(
                            color = Color(0xFFE7E7E7),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(WIDTH - 1f, HEIGHT - 1f),
                            cornerRadius = CornerRadius(RADIUS - 0.5f),
                            style = Stroke(1f),
                        )

                        val center = Offset(CX, CY)
                        // Dial: a shallow grey groove, darker along its upper left rim.
                        drawCircle(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFFE2E2E2), Color(0xFFEDEDED), Color(0xFFFBFBFB)),
                                start = Offset(CX - R_OUT * 0.7f, CY - R_OUT * 0.7f),
                                end = Offset(CX + R_OUT * 0.7f, CY + R_OUT * 0.7f),
                            ),
                            radius = R_OUT,
                            center = center,
                        )

                        val sweep = p * 360f
                        val shown = clamp01(p / 0.012f)

                        clipPath(paints.dial) {
                            drawIntoCanvas {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    it.nativeCanvas.drawCircle(CX + 3f, CY + 4f, R_OUT + 3f, paints.rim)
                                }
                            }

                            // Ticks: one swells and turns green as the head of the arc reaches it,
                            // and the free ones are swept by a slow pulse that keeps going round the dial.
                            val pulseAt = (t % TICK_PERIOD) / 1.5f * 360f
                            for (index in 0 until TICKS) {
                                val angle = index * 360f / TICKS
                                val a = (angle - 90f) * PI.toFloat() / 180f
                                val tx = CX + TICK_R * cos(a)
                                val ty = CY + TICK_R * sin(a)
                                val reached = busy * clamp01(1f - abs(angle - sweep - 7f) / 11f)
                                val pulse = ambient * (if (pulseAt < 360f) clamp01(1f - abs(angle - pulseAt) / 26f) else 0f)
                                val half = 1f + 1.5f * reached + 0.7f * pulse
                                val topLeft = Offset(tx - half, ty - half)
                                val size = Size(half * 2f, half * 2f)
                                val corner = CornerRadius(0.4f * half)
                                drawRoundRect(Color(0xFFB7B7B7), topLeft, size, corner, alpha = 0.75f + 0.25f * pulse)
                                if (reached > 0f) drawRoundRect(Color(0xFF2FCF50), topLeft, size, corner, alpha = reached)
                            }

                            // Soft green haze before the start and past the end of the arc; it
                            // stretches ahead while the arc moves.
                            drawIntoCanvas {
                                val canvas = it.nativeCanvas
                                paints.arc(canvas, paints.haze, 0.3f, -90f - 30f, -90f + sweep + 38f + 26f * busy, 0.9f * shown)
                                paints.arc(canvas, paints.hazeCore, 0.45f, -90f - 12f, -90f + sweep + 15f + 20f * busy, 0.9f * shown)
                            }

                            // The arc itself: a sector with rounded corners, slightly deeper green
                            // towards both rims. Drawn in a turned frame for the gradient's seam.
                            val inset = CORNER / R_MID * 180f / PI.toFloat()
                            val a0 = -90f - SEAM + inset
                            val a1 = max(a0 + 0.01f, -90f - SEAM + sweep - inset)
                            paints.sector.rewind()
                            paints.sector.arcTo(Rect(center, R_OUT - CORNER), a0, a1 - a0, true)
                            paints.sector.arcTo(Rect(center, R_IN + CORNER), a1, a0 - a1, false)
                            paints.sector.close()
                            val rounded = Stroke(CORNER * 2f, join = StrokeJoin.Round)
                            rotate(SEAM, pivot = center) {
                                drawPath(paints.sector, paints.green, alpha = shown)
                                drawPath(paints.sector, paints.green, alpha = shown, style = rounded)
                                drawPath(paints.sector, paints.rims, alpha = shown, style = rounded)
                                drawPath(paints.sector, paints.rims, alpha = shown)
                            }

                            drawIntoCanvas {
                                val canvas = it.nativeCanvas
                                // A glassy sheen that glides along the arc every few seconds.
                                val sheenPhase = (t % 4.2f) / 1.3f
                                if (ambient > 0f && sheenPhase < 1f && shown > 0f) {
                                    val at = -90f + (sweep + 30f) * sheenPhase - 15f
                                    val from = max(-90f, at - 13f)
                                    val to = min(-90f + sweep, at + 13f)
                                    if (to > from) {
                                        paints.arc(canvas, paints.sheen, 1f, from, to, shown * 0.3f * sin(PI.toFloat() * sheenPhase))
                                    }
                                }
                                // Bright leading cap while the arc is being drawn.
                                if (busy * shown > 0f) {
                                    paints.arc(canvas, paints.cap, 0.85f, -90f + sweep - 5f, -90f + sweep - 0.5f, busy * shown)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    // A ripple leaves the hub when the arc lands.
                                    val u = since / 0.9f
                                    if (u > 0f && u < 1f) {
                                        paints.ripple.alpha = (255f * 0.55f * (1f - u)).roundToInt().coerceIn(0, 255)
                                        canvas.drawCircle(CX, CY, R_IN + 2f + 44f * (1f - (1f - u).pow(3)), paints.ripple)
                                    }
                                    // Raised white hub with its shadow falling on the groove.
                                    canvas.drawCircle(CX - 2f, CY + 6f, R_IN, paints.hub)
                                }
                            }
                        }
                        drawCircle(Color.White, R_IN, center)
                    }
                }
            }
        }

        BasicText(
            text = title,
            style = TitleStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(17.dp, 32.5.dp).clearAndSetSemantics {},
        )
        Row(
            modifier = Modifier
                .centerAt((CX + 2f).dp, (CY - 13f).dp)
                .graphicsLayer {
                    // The hub figure swells as the arc lands.
                    val u = clamp01(landed() / 0.5f)
                    val pop = if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u) * (1f - u)
                    scaleX = 1f + 0.16f * pop
                    scaleY = 1f + 0.16f * pop
                }
                .clearAndSetSemantics {},
        ) {
            BasicText(text = percent.toString(), style = NumberStyle, modifier = Modifier.alignByBaseline())
            BasicText(text = "%", style = PercentStyle, modifier = Modifier.alignByBaseline().padding(start = 1.5.dp))
        }
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold)) { append("${used}GB") }
                append(" / ${totalGB}GB")
            },
            style = AmountStyle,
            maxLines = 1,
            modifier = Modifier.centerAt(CX.dp, (CY + 19.5f).dp).clearAndSetSemantics {},
        )

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        LaunchedEffect(pressed) {
            if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val press by animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
            label = "press",
        )
        Row(
            modifier = Modifier.centerAt((WIDTH / 2f).dp, 333.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = renews, style = RenewsStyle, maxLines = 1)
            BasicText(text = "•", style = RenewsStyle.copy(fontSize = 9.sp), modifier = Modifier.padding(horizontal = 4.dp))
            BasicText(
                text = editLabel,
                style = RenewsStyle.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer {
                        val scale = 1f - (1f - PRESS_SCALE) * press
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.4f * press
                    }
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onEditClick),
            )
        }
    }
}
