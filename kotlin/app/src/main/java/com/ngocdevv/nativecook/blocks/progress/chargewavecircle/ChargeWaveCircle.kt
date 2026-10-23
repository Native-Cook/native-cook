package com.ngocdevv.nativecook.blocks.progress.chargewavecircle

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 330f
private const val HEIGHT = 414f

// The canvas overflows the component by this much on every side to fit the chip's glow.
private val BLEED = 20.dp

private const val CX = 165f
private const val CY = 167f
private const val R_BOWL = 155f
private const val R_OUTER = 90.7f
private const val R_RING = 84.2f
private const val R_LIQUID = 82.8f

private const val CHIP_X = 94.8f
private const val CHIP_Y = 12.6f
private const val CHIP_W = 140.4f
private const val CHIP_H = 32.9f
private const val CHIP_PAD = 12f

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.18f
private const val START = 0.5f
private const val FILL = 2.3f
private const val HOLD = 4.8f
private const val RESET = 0.9f
private const val LOOP = START + FILL + HOLD + RESET
private const val LANDED = START + FILL

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = LANDED + 2f

// A pulse leaves the ring every PULSE seconds while it charges.
private const val PULSE = 2.6f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun chargeText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val ChipStyle = chargeText(9.8f, 16f, FontWeight.Normal, Color(0xFFE3ECE4))
private val NumberStyle = chargeText(56f, 64f, FontWeight.Medium, Color(0xFFE6E8E6))
    .copy(letterSpacing = 0.5.sp, fontFeatureSettings = "tnum")
private val UnitStyle = chargeText(15.5f, 20f, FontWeight.Normal, Color(0xFFF0F4F0))
private val ReadoutStyle = chargeText(20f, 26f, FontWeight.Normal, Color(0xFFDFE2DF)).copy(fontFeatureSettings = "tnum")
private val SmallUnit = SpanStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
private val NoteStyle = chargeText(11.8f, 16f, FontWeight.Normal, Color(0xFF7D827E))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Charge level at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < LANDED -> REST * easeInOutCubic((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how fast the level is moving at [t] seconds into the loop. */
private fun loopActivity(t: Float): Float {
    if (t >= LOOP - RESET) return sin(PI.toFloat() * clamp01((t - (LOOP - RESET)) / RESET))
    val u = (t - START) / FILL
    return if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
}

/** Places the element by its leading edge and vertical centre inside the component. */
private fun Modifier.leadingAt(x: Dp, centerY: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx(), centerY.roundToPx() - placeable.height / 2)
    }
}

/** Places the element by its centre inside the component. */
private fun Modifier.centerAt(x: Dp, y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

private class ChargePaints {
    val center = Offset(CX, CY)
    val gauge = Path().apply { addOval(Rect(center, R_LIQUID)) }
    val wave = Path()
    val bubbles = Path()

    val bolt = Path().apply {
        moveTo(1.6f, -9f)
        lineTo(-5.6f, 1.4f)
        lineTo(-0.5f, 1.4f)
        lineTo(-1.9f, 9f)
        lineTo(5.6f, -1.5f)
        lineTo(0.5f, -1.5f)
        close()
    }
    val plug = Path().apply {
        moveTo(-9.4f, -5.6f)
        lineTo(9.4f, -5.6f)
        lineTo(9.4f, 1.8f)
        cubicTo(9.4f, 7.6f, 5.2f, 11.2f, 0f, 11.2f)
        cubicTo(-5.2f, 11.2f, -9.4f, 7.6f, -9.4f, 1.8f)
        close()
        for (x in floatArrayOf(-4.6f, 4.6f)) {
            moveTo(x, -11.2f)
            lineTo(x, -5.6f)
        }
    }
    val rocket = Path().apply {
        moveTo(0f, -12.5f)
        cubicTo(6f, -7.5f, 7f, 1f, 4.2f, 8f)
        lineTo(-4.2f, 8f)
        cubicTo(-7f, 1f, -6f, -7.5f, 0f, -12.5f)
        close()
        for (side in floatArrayOf(-1f, 1f)) {
            moveTo(side * 4.9f, 3.6f)
            lineTo(side * 9.2f, 9.8f)
            lineTo(side * 4.2f, 8.4f)
        }
        moveTo(-2f, 11f)
        quadraticTo(0f, 14.6f, 2f, 11f)
    }
    val snowflake = Path().apply {
        for (arm in 0 until 6) {
            val a = (arm * 60f + 30f) * PI.toFloat() / 180f
            moveTo(0f, 0f)
            lineTo(13f * cos(a), 13f * sin(a))
            // Two short barbs near the tip of every arm.
            val bx = 7.6f * cos(a)
            val by = 7.6f * sin(a)
            for (side in floatArrayOf(-1f, 1f)) {
                val b = a + side * 52f * PI.toFloat() / 180f
                moveTo(bx, by)
                lineTo(bx + 4.6f * cos(b), by + 4.6f * sin(b))
            }
        }
    }

    // The selected mode's tab: a slice of the bowl under the gauge.
    val tab = Path().apply {
        arcTo(Rect(center, 97f), 74f, 32f, true)
        arcTo(Rect(center, R_BOWL), 103.5f, -27f, false)
        close()
    }
    val chip = Path().apply {
        addRoundRect(RoundRect(Rect(CHIP_PAD, CHIP_PAD, CHIP_PAD + CHIP_W, CHIP_PAD + CHIP_H), CornerRadius(CHIP_H / 2f)))
    }

    val bowl = Brush.verticalGradient(
        0f to rgba(30, 54, 37, 0f), 0.5f to rgba(30, 54, 37, 0.75f), 1f to rgba(30, 56, 38, 1f),
        startY = CY - 70f, endY = CY + R_BOWL,
    )
    val tabFill = Brush.verticalGradient(
        listOf(rgba(52, 104, 64, 0.75f), rgba(40, 84, 52, 0.45f)), startY = CY + 97f, endY = CY + R_BOWL,
    )
    val face = Brush.radialGradient(
        0f to Color(0xFF17241B), 0.62f to Color(0xFF18281D), 0.9f to Color(0xFF1E3A27), 1f to Color(0xFF2A5A38),
        center = center, radius = R_RING,
    )
    val liquid = Brush.verticalGradient(listOf(Color(0xFF45C768), Color(0xFF2FA54D)), startY = CY + 30f, endY = CY + R_LIQUID)
    val chipFill = Brush.verticalGradient(listOf(Color(0xFF1F3F27), Color(0xFF1A3321)), startY = CHIP_PAD, endY = CHIP_PAD + CHIP_H)

    private fun soft(color: Color, sigma: Float, stroke: Float? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (stroke != null) {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    val shadow = soft(rgba(6, 12, 8, 0.75f), 9f)
    val plugGlow = soft(rgba(96, 228, 128, 0.8f), 4f, 3.4f)
    val pulse = soft(Color(0xFF8DF0A8), 1.4f, 1.4f)
    val rim = soft(rgba(10, 24, 14, 0.55f), 5f, 9f)
    val bloom = soft(rgba(104, 236, 138, 0.85f), 6f, 5.5f)
    val boltGlow = soft(Color(0xFFBAFFC9), 4f)
    val chipGlow = soft(rgba(60, 214, 84, 0.9f), 4.5f, 3f)
    val chipBoltGlow = soft(Color(0xFFD9FF3C), 3.2f)

    /**
     * One layer of the liquid: a travelling wave on a surface at [level], tilted by [tilt], closed
     * along the bottom of the gauge. The gauge's circle clips it.
     */
    fun liquid(level: Float, amplitude: Float, tilt: Float, phase: Float, wavelength: Float): Path {
        val y0 = CY + R_LIQUID - 2f * R_LIQUID * clamp01(level)
        val left = CX - R_LIQUID - 2f
        val right = CX + R_LIQUID + 2f
        val steps = 26
        wave.rewind()
        wave.moveTo(left, CY + R_LIQUID + 4f)
        for (i in 0..steps) {
            val x = left + (right - left) * i / steps
            wave.lineTo(x, y0 + amplitude * sin((x - CX) / wavelength * PI.toFloat() * 2f + phase) + tilt * (x - CX))
        }
        wave.lineTo(right, CY + R_LIQUID + 4f)
        wave.close()
        return wave
    }
}

/**
 * Dark green charging screen: a "speed up" chip, a glowing circular gauge with the charge level
 * and a liquid filling its foot, the charging modes and the time read-outs. The liquid is two
 * waves that never stop travelling; it sloshes and tilts while the level moves and rocks itself
 * calm afterwards, bubbles rise through it, a pulse of light keeps leaving the ring while the
 * bolt flashes with it, and the chip's glow breathes under a passing sheen. Tapping the chip
 * gives the liquid a kick.
 *
 * @param progress Controlled charge level, 0…1. Leave null to show the reference's 18% (or the autoPlay loop).
 * @param autoPlay Charges by itself from empty to 18%, holds, drains and starts over.
 * @param fullChargeMinutes Minutes a charge from empty to full takes; the remaining time is derived from the level.
 */
@Composable
fun ChargeWaveCircle(
    progress: Float? = null,
    autoPlay: Boolean = false,
    chipLabel: String = "Speed up charging",
    time: String = "10:23",
    date: String = "11/28 Sunday",
    caption: String = "Full Charge takes about",
    fullChargeMinutes: Int = 227,
    onSpeedUpClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

    // Seconds: `seconds` never stops (waves, pulses), `clock` is the position in the autoPlay loop.
    val seconds = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When a controlled value last changed / the chip last kicked the liquid, on the `seconds` axis.
    val movedAt = remember { mutableFloatStateOf(-10f) }
    val kickedAt = remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        movedAt.floatValue = seconds.floatValue
        held.animateTo(target, tween(if (reducedMotion) 200 else 900, easing = EaseOutStrong))
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = min((now - last) / 1e9f, 0.064f)
            last = now
            seconds.floatValue += dt
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
                val u = (seconds.floatValue - movedAt.floatValue) / 0.8f
                if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
            }
        }
    }
    // Seconds since the level last landed, or since the last boost; large when nothing happened.
    val landed = {
        val kick = seconds.floatValue - kickedAt.floatValue
        val t = clock.floatValue
        when {
            reducedMotion -> 10f
            !controlled -> min(kick, if (t < LOOP - RESET && t >= LANDED) t - LANDED else 10f)
            else -> min(kick, max(0f, seconds.floatValue - movedAt.floatValue - 0.7f))
        }
    }
    // The figures are real text: recompose only when a whole percent changes.
    val percent by remember(controlled) { derivedStateOf { (value() * 100f).roundToInt() } }
    val remaining = (fullChargeMinutes * (1f - percent / 100f)).roundToInt()

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { ChargePaints() }

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
                contentDescription = "Charging. $caption ${remaining / 60} hours ${remaining % 60} minutes"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    translate(x, y) { drawCharge(paints, value(), seconds.floatValue, activity(), landed(), ambient) }
                }
            }
        }

        BasicText(
            text = percent.toString(),
            style = NumberStyle,
            modifier = Modifier
                .centerAt(CX.dp, CY.dp)
                .graphicsLayer {
                    // The number swells a little while the level moves.
                    val s = 1f + 0.05f * activity()
                    scaleX = s
                    scaleY = s
                }
                .clearAndSetSemantics {},
        )
        BasicText("%", style = UnitStyle, modifier = Modifier.centerAt(CX.dp, 215.3.dp).clearAndSetSemantics {})

        BasicText(time, style = ReadoutStyle, maxLines = 1, modifier = Modifier.centerAt((CX / 2f).dp, 379.5.dp).clearAndSetSemantics {})
        BasicText(date, style = NoteStyle, maxLines = 1, modifier = Modifier.centerAt((CX / 2f).dp, 403.dp).clearAndSetSemantics {})
        BasicText(
            text = buildAnnotatedString {
                append("${remaining / 60}")
                withStyle(SmallUnit) { append(" H ") }
                append("${remaining % 60}")
                withStyle(SmallUnit) { append(" M") }
            },
            style = ReadoutStyle,
            maxLines = 1,
            modifier = Modifier.centerAt((CX * 1.5f).dp, 379.5.dp).clearAndSetSemantics {},
        )
        BasicText(caption, style = NoteStyle, maxLines = 1, modifier = Modifier.centerAt((CX * 1.5f).dp, 403.dp).clearAndSetSemantics {})

        // The "speed up" chip: its glow breathes and its bolt flickers; a sheen crosses it now and then.
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
        Box(
            Modifier
                .offset(CHIP_X.dp, CHIP_Y.dp)
                .size(CHIP_W.dp, CHIP_H.dp)
                .graphicsLayer {
                    val s = 1f - (1f - PRESS_SCALE) * press
                    scaleX = s
                    scaleY = s
                }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    if (!reducedMotion) kickedAt.floatValue = seconds.floatValue
                    onSpeedUpClick()
                },
        ) {
            Box(Modifier.matchParentSize().bleed(CHIP_PAD.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    scale(density, density, pivot = Offset.Zero) { drawChip(paints, seconds.floatValue, ambient) }
                }
            }
            BasicText(chipLabel, style = ChipStyle, maxLines = 1, modifier = Modifier.leadingAt(40.5.dp, (CHIP_H / 2f).dp))
        }
    }
}

private fun DrawScope.drawChip(paints: ChargePaints, time: Float, ambient: Float) {
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val radius = CHIP_H / 2f
    if (blurs) {
        paints.chipGlow.alpha = (255f * 0.9f * (0.6f + 0.25f * ambient * sin(time * 2.1f))).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(CHIP_PAD, CHIP_PAD, CHIP_PAD + CHIP_W, CHIP_PAD + CHIP_H, radius, radius, paints.chipGlow) }
    }
    drawPath(paints.chip, paints.chipFill)
    if (ambient > 0f) {
        val sx = -40f + (CHIP_W + 80f) * min(1f, ((time + 0.8f) % 3.8f) / 1.1f)
        clipPath(paints.chip) {
            withTransform({
                translate(sx, 0f)
                transform(androidx.compose.ui.graphics.Matrix().apply { values[4] = -0.35f })
            }) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(rgba(190, 255, 205, 0f), rgba(190, 255, 205, 0.16f), rgba(190, 255, 205, 0f)),
                        startX = -14f,
                        endX = 14f,
                    ),
                    topLeft = Offset(-14f, CHIP_PAD - 4f),
                    size = Size(28f, CHIP_H + 8f),
                )
            }
        }
    }
    drawRoundRect(
        color = Color(0xFF2F9A3F),
        topLeft = Offset(CHIP_PAD + 0.6f, CHIP_PAD + 0.6f),
        size = Size(CHIP_W - 1.2f, CHIP_H - 1.2f),
        cornerRadius = CornerRadius(radius),
        style = Stroke(1.2f),
    )
    withTransform({
        translate(CHIP_PAD + 20.2f, CHIP_PAD + CHIP_H / 2f)
        scale(0.88f, 0.88f, pivot = Offset.Zero)
    }) {
        if (blurs) {
            paints.chipBoltGlow.alpha = (255f * (0.6f + 0.4f * ambient * sin(time * 5.3f) * sin(time * 1.9f))).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { it.nativeCanvas.drawPath(paints.bolt.asAndroidPath(), paints.chipBoltGlow) }
        }
        drawPath(paints.bolt, Color(0xFFEAFF58))
    }
}

private fun DrawScope.drawCharge(paints: ChargePaints, value: Float, time: Float, activity: Float, landed: Float, ambient: Float) {
    val center = paints.center
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val green = Color(0xFF74E592)
    val dim = Color(0xFF33573F)

    // The bowl the gauge sits in: it shows towards its foot.
    drawCircle(paints.bowl, R_BOWL, center)
    drawPath(paints.tab, paints.tabFill)
    if (blurs) drawIntoCanvas { it.nativeCanvas.drawCircle(CX, CY + 3f, R_OUTER + 6f, paints.shadow) }

    // Mode icons
    translate(82.7f, 263.7f) { drawPath(paints.snowflake, dim, style = Stroke(2.3f, cap = StrokeCap.Round)) }
    translate(247.3f, 264.2f) {
        rotate(38f, pivot = Offset.Zero) {
            drawPath(paints.rocket, dim, style = Stroke(2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(dim, 2.1f, Offset(0f, -2.4f))
        }
    }
    translate(CX, 294f) {
        if (blurs) {
            paints.plugGlow.alpha = (255f * 0.8f * (0.55f + 0.3f * ambient * sin(time * 2.3f))).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { it.nativeCanvas.drawPath(paints.plug.asAndroidPath(), paints.plugGlow) }
        }
        drawPath(paints.plug, green, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        withTransform({
            translate(0f, 1.6f)
            scale(0.56f, 0.56f, pivot = Offset.Zero)
        }) { drawPath(paints.bolt, green) }
    }

    // Gauge
    drawCircle(rgba(86, 180, 110, 0.42f), R_OUTER, center, style = Stroke(1.1f))
    // A pulse of light leaves the ring towards the outer one, and the bolt flashes with it.
    val pulsePhase = (time % PULSE) / 1.25f
    if (ambient > 0f && pulsePhase < 1f && blurs) {
        paints.pulse.alpha = (255f * 0.55f * (1f - pulsePhase)).roundToInt()
        drawIntoCanvas { it.nativeCanvas.drawCircle(CX, CY, R_RING + 13f * (1f - (1f - pulsePhase).pow(2)), paints.pulse) }
    }
    drawCircle(paints.face, R_RING, center)

    // The liquid sloshes while the level moves and settles with a damped rocking once it lands.
    val shown = clamp01(value / 0.05f)
    val settle = if (landed > 0f && landed < 2f) exp(-2.6f * landed) else 0f
    val swell = shown * (ambient * 5.4f + 3.2f * activity + 3f * settle)
    val tilt = shown * (0.07f * activity * sin(time * 8f) + 0.11f * settle * sin(10f * landed))
    clipPath(paints.gauge) {
        drawPath(paints.liquid(value + 0.018f, swell * 0.85f, -tilt * 0.6f, -time * 1.3f + 2.1f, 118f), rgba(120, 226, 148, 0.5f))
        drawPath(paints.liquid(value, swell, tilt, time * 1.7f, 99f), paints.liquid)
        // Bubbles rise through the liquid and vanish under the surface.
        val surface = CY + R_LIQUID - 2f * R_LIQUID * value
        val bottom = CY + R_LIQUID
        if (ambient > 0f && bottom - surface >= 8f) {
            paints.bubbles.rewind()
            for (i in 0 until 6) {
                val cycle = time / (2.1f + 0.35f * i) + i * 0.37f
                val age = cycle - floor(cycle)
                val bx = CX + (i - 2.5f) * 15f + 4f * sin(time * 2f + i)
                val by = bottom - 4f - (bottom - surface - 2f) * age
                paints.bubbles.addOval(Rect(Offset(bx, by), (0.9f + 0.25f * (i % 3)) * sin(PI.toFloat() * age)))
            }
            drawPath(paints.bubbles, rgba(214, 255, 224, 0.55f))
        }
        // Shade under the rim, so the liquid sits inside the glass.
        if (blurs) drawIntoCanvas { it.nativeCanvas.drawCircle(CX, CY, R_LIQUID + 3f, paints.rim) }
    }

    // The ring's bloom breathes, brightens while charging and flashes as the level lands.
    if (blurs) {
        val u = landed / 0.8f
        val flash = if (u > 0f && u < 1f) 0.35f * (1f - u) else 0f
        val bloom = min(1f, 0.55f + 0.12f * ambient * sin(time * 1.7f) + 0.25f * activity + flash)
        paints.bloom.alpha = (255f * 0.85f * bloom).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { it.nativeCanvas.drawCircle(CX, CY, R_RING, paints.bloom) }
    }
    drawCircle(Color(0xFF8CECA6), R_RING, center, style = Stroke(1.8f))

    // Charging bolt
    val spark = ambient * exp(-3.2f * pulsePhase)
    withTransform({
        translate(CX, 113.3f)
        scale(1f + 0.14f * spark, 1f + 0.14f * spark, pivot = Offset.Zero)
    }) {
        if (blurs) {
            paints.boltGlow.alpha = (255f * (0.25f + 0.75f * spark)).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { it.nativeCanvas.drawPath(paints.bolt.asAndroidPath(), paints.boltGlow) }
        }
        drawPath(paints.bolt, Color(0xFFF1F6F1))
        drawPath(paints.bolt, Color(0xFFF1F6F1), style = Stroke(0.9f, join = StrokeJoin.Round))
    }

    // Divider between the read-outs
    drawRect(rgba(255, 255, 255, 0.14f), Offset(CX - 0.5f, 375f), Size(1f, 25f))
}
