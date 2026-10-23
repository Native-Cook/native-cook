package com.ngocdevv.nativecook.blocks.progress.chargepillprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 340f
private const val HEIGHT = 136f

// The canvas overflows the component by this much on every side to fit the pill's shadow.
private val BLEED = 36.dp

// Compact pill: bolt on the left, level on the right.
private const val MINI_X = 70f
private const val MINI_W = 200f
private const val MINI_H = 36f
private const val MINI_BOLT_X = MINI_X + 16f

// Expanded pill: bar with a knob, time left on the right.
private const val PILL_Y = 60f
private const val PILL_H = 76f
private const val BAR_X = 33f
private const val BAR_W = 228f
private const val BAR_H = 7.2f
private const val BAR_CY = PILL_Y + PILL_H / 2
private const val KNOB_W = 34f
private const val KNOB_H = 18.4f

private const val DIGIT_W = 8.7f
private const val DIGIT_H = 18f

private val Green = Color(0xFF3FC760)
private val Mint = Color(0xFFC4FFD2)

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.76f
private const val FROM = 0.2f
private const val START = 0.4f
private const val FILL = 3.2f
private const val HOLD = 3.4f
private const val RESET = 0.6f
private const val LOOP = START + FILL + HOLD + RESET

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = START + FILL + 1f

// A pulse of charge leaves the start of the bar this often; it takes TRAVEL of that to reach the knob.
private const val PULSE = 1.3f
private const val TRAVEL = 0.55f
private const val STREAK = 46f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun chargeText(size: Float, lineHeight: Float, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val DigitStyle = chargeText(14f, DIGIT_H, Green)
private val KnobStyle = chargeText(12.4f, 16f, Color.White)
private val EtaStyle = chargeText(13.5f, 18f, Green).copy(textAlign = TextAlign.Start)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Charge level at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> FROM
    t < START + FILL -> FROM + (REST - FROM) * easeInOutCubic((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST - (REST - FROM) * easeInOutCubic((t - (LOOP - RESET)) / RESET)
}

/** Where the knob's centre sits on the bar, from its left end. */
private fun knobCenter(p: Float) = KNOB_W / 2 + (BAR_W - KNOB_W) * clamp01(p)

/** 0…1 along the bar for the travelling pulse; it accelerates into the knob. */
private fun pulseTravel(time: Float): Float {
    val phase = (time % PULSE) / PULSE
    return if (phase < TRAVEL) (phase / TRAVEL).pow(2) else 1f
}

/** 0…1 flash that starts when a pulse reaches the knob and dies away quickly. */
private fun pulseFlare(time: Float): Float {
    val since = ((time % PULSE) / PULSE - TRAVEL) * PULSE
    return if (since < 0f) 0f else min(1f, since / 0.05f) * exp(-5.5f * since)
}

private class ChargePillPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.22f), sigma = 14f)
    val boltGlow = SoftRoundRect(Color(0xFF35E066), sigma = 7f)
    val haloTail = SoftRoundRect(rgba(40, 214, 90, 0.55f), sigma = 11f)
    val haloCore = SoftRoundRect(rgba(48, 226, 98, 0.85f), sigma = 8f)

    // SF-style bolt in an 11 × 14 box.
    val bolt = Path().apply {
        moveTo(7.8f, 0f)
        lineTo(0.2f, 8.4f)
        lineTo(4.5f, 8.4f)
        lineTo(3.2f, 14f)
        lineTo(10.8f, 5.5f)
        lineTo(6.5f, 5.5f)
        close()
    }
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2), alpha = alpha)

/** One wheel of the odometer. [place] is 1 for ones, 10 for tens, 100 for hundreds. */
@Composable
private fun ChargeWheel(place: Int, value: () -> Float) {
    Box(Modifier.size(DIGIT_W.dp, DIGIT_H.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val v = clamp01(value()) * 100f
                    // A wheel turns with the one below it only while that one rolls from 9 to 0.
                    val lower = if (place == 1) 0f else (v / (place / 10)) % 10f
                    val turn = if (place == 1) v % 10f else floor(v / place) % 10f + max(0f, lower - 9f)
                    translationY = -turn * DIGIT_H.dp.toPx()
                    // Leading zeros stay hidden.
                    alpha = if (place == 1) 1f else clamp01(v - (place - 1))
                },
        ) {
            for (i in 0..10) {
                BasicText(text = (i % 10).toString(), style = DigitStyle, modifier = Modifier.size(DIGIT_W.dp, DIGIT_H.dp))
            }
        }
    }
}

/**
 * Live-activity style charging UI: a compact black pill with a bolt and the level, and an expanded
 * pill whose green bar ends in a glowing knob, with the time left beside it. Pulses of charge run
 * along the bar into the knob, which flares and bumps as each one lands (the compact bolt flashes
 * with it), the halo breathes, and the compact level rolls like an odometer.
 *
 * @param progress Controlled charge level, 0…1. Leave null to show the reference's 76% (or the autoPlay loop).
 * @param autoPlay Charges from 20% to 76% by itself, holds, drops back and starts over.
 * @param fullChargeMinutes Minutes a full charge takes; the "~15m" estimate is the share of it that is left.
 */
@Composable
fun ChargePillProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    fullChargeMinutes: Float = 62f,
    label: String = "Charging",
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val target = (progress ?: REST).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        held.animateTo(target, tween(if (reducedMotion) 200 else 700, easing = EaseOutStrong))
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
    val value = { if (controlled) held.value else loopValue(clock.floatValue) }
    val ambient = if (reducedMotion) 0f else 1f
    val flare = { ambient * pulseFlare(time.floatValue) }
    // The knob's number and the estimate are real text: recompose only when a whole number changes.
    val percent by remember(controlled) {
        derivedStateOf { ((if (controlled) held.value else loopValue(clock.floatValue)) * 100f).roundToInt() }
    }
    val minutes = ((1f - percent / 100f) * fullChargeMinutes).roundToInt()

    val paints = remember { ChargePillPaints() }

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
                contentDescription = label
                stateDescription = "$percent%, about $minutes minutes left"
                progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val t = time.floatValue
                    val f = flare()
                    val bx = x + BAR_X
                    val cy = y + BAR_CY
                    val knobX = knobCenter(value())
                    val boltGlow = 0.75f * f

                    // Compact pill.
                    pill(Color.Black, x + MINI_X, y, MINI_W, MINI_H)
                    translate(x + MINI_BOLT_X, y + MINI_H / 2) {
                        drawIntoCanvas { paints.boltGlow.draw(it.nativeCanvas, -9f, -9f, 18f, 18f, 9f, alpha = boltGlow) }
                        scale(1.31f * (1f + 0.1f * f), pivot = Offset.Zero) {
                            translate(-5.5f, -7f) {
                                drawPath(paints.bolt, Green)
                                drawPath(paints.bolt, Green, style = Stroke(1f, join = StrokeJoin.Round))
                                drawPath(paints.bolt, Mint, alpha = boltGlow)
                            }
                        }
                    }

                    // Expanded pill.
                    drawIntoCanvas {
                        paints.shadow.draw(it.nativeCanvas, x + 8, y + PILL_Y + 14, WIDTH - 16, PILL_H - 6, PILL_H / 2)
                    }
                    pill(Color.Black, x, y + PILL_Y, WIDTH, PILL_H)
                    pill(Color(0xFF313131), bx, cy - BAR_H / 2, BAR_W, BAR_H)

                    // The halo trails to the left like a comet, breathes, and flares when a pulse lands.
                    val haloAlpha = (0.62f + ambient * 0.08f * sin(t * 2.3f) + 0.38f * f).coerceAtMost(1f)
                    translate(bx + knobX, cy) {
                        scale(1f + 0.22f * f, pivot = Offset.Zero) {
                            drawIntoCanvas {
                                paints.haloTail.draw(it.nativeCanvas, -62f, -9f, 76f, 18f, 9f, alpha = haloAlpha)
                                paints.haloCore.draw(
                                    it.nativeCanvas,
                                    -KNOB_W / 2 - 2,
                                    -KNOB_H / 2 - 2,
                                    KNOB_W + 4,
                                    KNOB_H + 4,
                                    KNOB_H / 2 + 2,
                                    alpha = haloAlpha,
                                )
                            }
                        }
                    }

                    pill(Green, bx, cy - BAR_H / 2, knobX, BAR_H)
                    // The pulse: a streak of light that runs along the fill into the knob.
                    val phase = (t % PULSE) / PULSE
                    if (ambient > 0f && phase < TRAVEL) {
                        val sx = bx - STREAK + (knobX + STREAK * 0.4f) * pulseTravel(t)
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                0f to Mint.copy(alpha = 0f),
                                0.75f to Mint.copy(alpha = 0.95f),
                                1f to Mint.copy(alpha = 0f),
                                startX = sx,
                                endX = sx + STREAK,
                            ),
                            topLeft = Offset(bx, cy - BAR_H / 2),
                            size = Size(knobX, BAR_H),
                            cornerRadius = CornerRadius(BAR_H / 2),
                            alpha = clamp01(phase / 0.12f),
                        )
                    }

                    translate(bx + knobX, cy) {
                        scale(1f + 0.08f * f, pivot = Offset.Zero) {
                            pill(Color(0xFF41C966), -KNOB_W / 2, -KNOB_H / 2, KNOB_W, KNOB_H)
                            pill(Mint, -KNOB_W / 2, -KNOB_H / 2, KNOB_W, KNOB_H, alpha = boltGlow)
                            translate(-KNOB_W / 2 + 7.8f, 0f) {
                                scale(0.86f, pivot = Offset.Zero) {
                                    translate(-5.5f, -7f) { drawPath(paints.bolt, Color(0xFFB4ECC2)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .offset((MINI_X + MINI_W - 8.7f - DIGIT_W * 3).dp, ((MINI_H - DIGIT_H) / 2).dp)
                .clearAndSetSemantics {},
        ) {
            ChargeWheel(100, value)
            ChargeWheel(10, value)
            ChargeWheel(1, value)
        }

        // The number sits to the right of the knob's bolt and rides (and bumps) with the knob.
        BasicText(
            text = percent.toString(),
            style = KnobStyle,
            maxLines = 1,
            modifier = Modifier
                .offset(y = (BAR_CY - 8f).dp)
                .size(30.dp, 16.dp)
                .graphicsLayer {
                    translationX = (BAR_X + knobCenter(value()) + 5.6f - 15f).dp.toPx()
                    transformOrigin = TransformOrigin(0.5f - 5.6f / 30f, 0.5f)
                    val s = 1f + 0.08f * flare()
                    scaleX = s
                    scaleY = s
                }
                .clearAndSetSemantics {},
        )

        BasicText(
            text = if (minutes > 0) "~${minutes}m" else "Full",
            style = EtaStyle,
            maxLines = 1,
            modifier = Modifier.offset(270.dp, (BAR_CY - 9f).dp).clearAndSetSemantics {},
        )
    }
}
