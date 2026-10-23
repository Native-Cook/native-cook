package com.ngocdevv.nativecook.blocks.progress.barcodeprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 344f
private const val HEIGHT = 175f
private const val RADIUS = 7f

// The canvas overflows the card by this much on every side to fit the scanner's glow.
private val BLEED = 16.dp

private const val BARS = 33
private const val ROW_X = 11f
private const val ROW_Y = 115f
private const val ROW_W = 322f
private const val ROW_H = 49.5f
private const val GAP = 1.3f
private const val PITCH = (ROW_W + GAP) / BARS
private const val BAR_W = PITCH - GAP
private const val ROW_RADIUS = 2.2f

private const val FIGURE_Y = 84.7f
private const val CHIP_X = 66.8f
private const val CHIP_W = 40f
private const val CHIP_H = 17f

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.66f
private const val START = 0.4f
private const val FILL = 1.9f
private const val HOLD = 4.2f
private const val RESET = 0.7f
private const val LOOP = START + FILL + HOLD + RESET
private const val LANDED = START + FILL

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = LANDED + 1.2f

// The ripple that keeps running through the bars: one pass every RIPPLE_PERIOD seconds.
private const val RIPPLE_PERIOD = 3.6f
private const val RIPPLE_PASS = 1.15f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun barcodeText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val TitleStyle = barcodeText(11.6f, 16f, FontWeight.Medium, Color(0xFFF5F5F5)).copy(letterSpacing = 0.1.sp)
private val CaptionStyle = barcodeText(10.2f, 14f, FontWeight.Normal, Color(0xFF9A9A9A)).copy(letterSpacing = 0.1.sp)
private val FigureStyle = barcodeText(23.5f, 30f, FontWeight.Normal, Color.White)
    .copy(letterSpacing = (-0.6).sp, fontFeatureSettings = "tnum")
private val ChangeStyle = barcodeText(8.2f, 12f, FontWeight.Medium, Color(0xFFEDEDED))
    .copy(fontFamily = FontFamily.Monospace)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Progress at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < LANDED -> REST * easeInOutCubic((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how fast the scanner is travelling at [t] seconds into the loop. */
private fun loopActivity(t: Float): Float {
    val u = (t - START) / FILL
    return if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
}

/** 0…1: how lit bar [index] is. A bar switches on as the scanner crosses its middle third. */
private fun barLit(value: Float, index: Int) = clamp01((value * BARS - index - 0.5f) * 3f + 0.5f)

/**
 * Height of bar [index], 0…1 of the row. The bars around the moving scanner duck, so a notch
 * travels with it, and a shallow ripple keeps running through the row while it rests.
 */
private fun barHeight(value: Float, index: Int, activity: Float, time: Float, ambient: Float): Float {
    val near = clamp01(1f - abs(value * BARS - index - 0.5f) / 2.6f)
    val notch = 0.42f * activity * near * near * (3f - 2f * near)
    val phase = (time % RIPPLE_PERIOD) / RIPPLE_PASS
    val at = -4f + (BARS + 8) * phase
    val wave = if (phase < 1f) exp(-((index - at) / 2.1f).pow(2)) else 0f
    return 1f - notch - 0.2f * ambient * (1f - activity) * wave
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

private class BarcodePaints {
    val row = Path().apply {
        addRoundRect(RoundRect(Rect(ROW_X, ROW_Y, ROW_X + ROW_W, ROW_Y + ROW_H), CornerRadius(ROW_RADIUS)))
    }
    val arrow = Path().apply {
        moveTo(CHIP_X + 8.2f, FIGURE_Y + 2.4f)
        lineTo(CHIP_X + 13f, FIGURE_Y - 2.4f)
        moveTo(CHIP_X + 9.1f, FIGURE_Y - 2.5f)
        lineTo(CHIP_X + 13.1f, FIGURE_Y - 2.5f)
        lineTo(CHIP_X + 13.1f, FIGURE_Y + 1.5f)
    }

    private fun soft(color: Color, sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    val bloom = soft(rgba(255, 255, 255, 0.4f), 7f)
    val line = soft(Color.White, 0.6f)
}

/**
 * Black "Progress Indicator" card: a caption, the percentage with a change chip, and a wide
 * barcode of 33 bars, white for the completed part. A scanner line with a soft bloom rides the
 * head of the fill and the bars around it duck, so a notch travels along the row and each bar
 * snaps to white as the line crosses it; the chip lands with a springy pop, and while the value
 * rests a shallow ripple keeps running through the bars.
 *
 * @param progress Controlled progress, 0…1. Leave null to show the reference's 66% (or the autoPlay loop).
 * @param autoPlay Scans the bars from 0 to 66% by itself, holds, rewinds and starts over.
 * @param change Change against the last period, in percent, shown in the chip.
 */
@Composable
fun BarcodeProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Progress Indicator",
    caption: String = "You are on track to finish the goal three days early",
    change: Int = 30,
    comparison: String = "vs. the last period",
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

    // Seconds: `time` never stops (ripple), `clock` is the position in the autoPlay loop.
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
    // The chip shrinks away while the bars rewind and lands with an overshoot once they are filled.
    val chip = {
        val t = clock.floatValue
        val u = (t - LANDED + 0.25f) / 0.6f
        when {
            reducedMotion || controlled -> 1f
            t >= LOOP - RESET -> 1f - 0.25f * clamp01((t - (LOOP - RESET)) / 0.25f)
            u <= 0f -> 0.75f
            u >= 1f -> 1f
            else -> 1f - 0.25f * exp(-6f * u) * cos(11f * u)
        }
    }
    // The figures are real text: recompose only when a whole percent changes.
    val percent by remember(controlled) {
        derivedStateOf { (clamp01(if (controlled) held.value else loopValue(clock.floatValue)) * 100f).roundToInt() }
    }
    // In the autoPlay loop the change counts up together with the bars.
    val delta = if (controlled) change else (change * percent / (REST * 100f)).roundToInt()

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { BarcodePaints() }

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
                contentDescription = "$title. $caption. Up $delta% $comparison"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        val moreSource = remember { MutableInteractionSource() }
        val morePressed by moreSource.collectIsPressedAsState()
        LaunchedEffect(morePressed) {
            if (morePressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = value()
                    val t = time.floatValue
                    val busy = activity()
                    translate(x, y) {
                        drawRoundRect(Color(0xFF0B0B0B), Offset.Zero, Size(WIDTH, HEIGHT), CornerRadius(RADIUS))
                        drawRoundRect(
                            color = Color(0xFF1D1D1D),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(WIDTH - 1f, HEIGHT - 1f),
                            cornerRadius = CornerRadius(RADIUS - 0.5f),
                            style = Stroke(1f),
                        )

                        // "More" dots (dimmed while pressed) and the divider under the caption.
                        for (i in 0 until 3) {
                            drawCircle(Color(0xFFF2F2F2), 0.75f, Offset(323.3f + i * 2.5f, 18.4f), alpha = if (morePressed) 0.4f else 1f)
                        }
                        drawRect(Color(0xFF272727), Offset(ROW_X, 59f), Size(ROW_W, 0.5f))

                        // Change chip: outline and arrow; the label is real text scaled with it.
                        val ink = Color(0xFFEDEDED)
                        scale(chip(), pivot = Offset(CHIP_X + CHIP_W / 2f, FIGURE_Y)) {
                            drawRoundRect(
                                color = ink,
                                topLeft = Offset(CHIP_X + 0.4f, FIGURE_Y - CHIP_H / 2f + 0.4f),
                                size = Size(CHIP_W - 0.8f, CHIP_H - 0.8f),
                                cornerRadius = CornerRadius(CHIP_H / 2f),
                                style = Stroke(0.8f),
                            )
                            drawPath(paints.arrow, ink, style = Stroke(0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }

                        // The bars: dim row, the lit ones on top, and the one switching on cross-fading.
                        clipPath(paints.row) {
                            for (index in 0 until BARS) {
                                val h = ROW_H * barHeight(p, index, busy, t, ambient)
                                val topLeft = Offset(ROW_X + index * PITCH, ROW_Y + (ROW_H - h) / 2f)
                                val size = Size(BAR_W, h)
                                drawRect(Color(0xFF474747), topLeft, size)
                                val lit = barLit(p, index)
                                if (lit > 0f) drawRect(Color(0xFFFAFAFA), topLeft, size, alpha = lit)
                            }
                        }

                        // The scanner: a bright line riding the head of the fill, with a soft
                        // bloom, only while it moves.
                        if (busy > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val bx = ROW_X + p * (ROW_W + GAP) - GAP / 2f
                            drawIntoCanvas {
                                val canvas = it.nativeCanvas
                                paints.bloom.alpha = (255f * 0.4f * busy).roundToInt().coerceIn(0, 255)
                                canvas.drawRect(bx - 9f, ROW_Y - 2f, bx + 9f, ROW_Y + ROW_H + 2f, paints.bloom)
                                paints.line.alpha = (255f * busy).roundToInt().coerceIn(0, 255)
                                canvas.drawRoundRect(bx - 0.9f, ROW_Y - 6f, bx + 0.9f, ROW_Y + ROW_H + 6f, 0.9f, 0.9f, paints.line)
                            }
                        }
                    }
                }
            }
        }

        BasicText(title, style = TitleStyle, maxLines = 1, modifier = Modifier.leadingAt(11.dp, 18.8.dp).clearAndSetSemantics {})
        BasicText(caption, style = CaptionStyle, maxLines = 1, modifier = Modifier.leadingAt(11.dp, 42.7.dp).clearAndSetSemantics {})
        BasicText(
            text = "$percent%",
            style = FigureStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(11.dp, FIGURE_Y.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = "$delta%",
            style = ChangeStyle,
            maxLines = 1,
            modifier = Modifier
                .centerAt((CHIP_X + CHIP_W / 2f + 6.5f).dp, FIGURE_Y.dp)
                .graphicsLayer {
                    // Scales about the chip's centre, like its outline on the canvas.
                    val s = chip()
                    scaleX = s
                    scaleY = s
                    translationX = -6.5f * density * (1f - s)
                }
                .clearAndSetSemantics {},
        )
        BasicText(comparison, style = CaptionStyle, maxLines = 1, modifier = Modifier.leadingAt(118.dp, FIGURE_Y.dp).clearAndSetSemantics {})

        Box(
            Modifier
                .offset(306.dp, 1.dp)
                .size(38.dp, 35.dp)
                .clickable(interactionSource = moreSource, indication = null, role = Role.Button, onClickLabel = "More", onClick = onMoreClick),
        )
    }
}
