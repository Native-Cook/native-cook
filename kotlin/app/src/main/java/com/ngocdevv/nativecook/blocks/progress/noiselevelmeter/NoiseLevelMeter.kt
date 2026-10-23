package com.ngocdevv.nativecook.blocks.progress.noiselevelmeter

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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class NoiseTone { Green, Yellow }

private const val SIZE = 164f
private const val RADIUS = 31f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 24.dp

private const val MAX_DB = 120f
private const val BAND = 30f

private const val TROUGH_X = 16.5f
private const val TROUGH_Y = 106.5f
private const val TROUGH_W = 131f
private const val TROUGH_H = 41.5f
private const val TROUGH_R = 7f

// The block stops short of the level, a thin needle marks it and the empty trough starts after.
private const val BLOCK_GAP = 2.4f
private const val NEEDLE_AT = 1.1f
private const val NEEDLE_W = 1.8f
private const val NEEDLE_H = 33f
private const val REST_GAP = 4.6f

private const val STRIPE_PITCH = 4.4f
private const val STRIPE_SLANT = 11f

// Signal bars: centre x, resting height.
private val BAR_X = floatArrayOf(127.4f, 134.4f, 141.2f)
private val BAR_H = floatArrayOf(5.4f, 12.1f, 18.8f)
private const val BARS_BOTTOM = 61.2f
private const val BAR_W = 2.2f

// autoPlay loop (seconds).
private const val START = 0.45f
private const val RISE = 1.7f
private const val HOLD = 3.6f
private const val RESET = 0.6f
private const val LOOP = START + RISE + HOLD + RESET

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = START + RISE + 1f

private class NoiseToneColors(val from: Color, val to: Color, val glow: Color, val needle: Color, val bar: Color)

private val GreenTone = NoiseToneColors(
    from = Color(0xFF1CCF45),
    to = Color(0xFF30E96B),
    glow = rgba(40, 220, 90, 0.55f),
    needle = Color(0xFF86AC96),
    bar = Color(0xFF22D94E),
)
private val YellowTone = NoiseToneColors(
    from = Color(0xFFE7B400),
    to = Color(0xFFF9EA00),
    glow = rgba(246, 214, 0, 0.5f),
    needle = Color(0xFFB9AD90),
    bar = Color(0xFFF1C500),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun meterText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val TitleStyle = meterText(13.2f, 18f, FontWeight.Light, Color(0xFF7C8099)).copy(letterSpacing = (-0.1).sp)
private val NumberStyle = meterText(39.5f, 46f, FontWeight.Bold, Color(0xFF0B0D12))
    .copy(letterSpacing = (-0.6).sp, fontFeatureSettings = "tnum")
private val UnitStyle = meterText(16f, 20f, FontWeight.Normal, Color(0xFF7C8099))
private val ScaleStyle = meterText(8.3f, 11f, FontWeight.Normal, Color(0xFF8286A0))
private val MarkStyle = meterText(11.6f, 14f, FontWeight.Bold, Color.White).copy(fontFeatureSettings = "tnum")

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

// A damped swing: shoots past the target by about 8% and settles, like a meter needle.
private fun swing(u: Float) = 1f - exp(-8f * u) * cos(10f * u)

/** Level (0…1 of the scale) at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float, rest: Float) = when {
    t < START -> 0f
    t < START + RISE -> rest * swing((t - START) / RISE)
    t < LOOP - RESET -> rest
    else -> rest * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how lively the meter is at [t] seconds into the loop. */
private fun loopActivity(t: Float) = clamp01((t - START) / 0.15f) * clamp01((START + RISE * 0.85f - t) / 0.6f)

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

private class NoiseMeterPaints {
    val shadow = SoftRoundRect(rgba(96, 106, 130, 0.28f), sigma = 12f)
    val greenGlow = SoftRoundRect(GreenTone.glow, sigma = 7f)
    val yellowGlow = SoftRoundRect(YellowTone.glow, sigma = 7f)
    val block = Path()
    val rest = Path()

    // Slanted hairlines covering the whole trough, shifted and clipped to the block when drawn.
    val stripes = Path().apply {
        var x = -STRIPE_SLANT - STRIPE_PITCH * 2
        while (x < TROUGH_W + STRIPE_PITCH * 2) {
            moveTo(x, -1f)
            lineTo(x + STRIPE_SLANT, TROUGH_H + 1f)
            x += STRIPE_PITCH
        }
    }
}

private fun Path.setRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    rewind()
    addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r)))
}

private fun DrawScope.drawNoiseFill(
    paints: NoiseMeterPaints,
    colors: NoiseToneColors,
    glow: SoftRoundRect,
    x: Float,
    y: Float,
    edge: Float,
    time: Float,
    opacity: Float,
    glowAlpha: Float,
) {
    val width = max(0f, edge - BLOCK_GAP)
    val radius = min(TROUGH_R, width / 2f)
    drawIntoCanvas { glow.draw(it.nativeCanvas, x, y + 2f, width, TROUGH_H, radius, opacity * glowAlpha) }
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(colors.from, colors.to),
            start = Offset(x, y),
            end = Offset(x + TROUGH_W * 0.55f, y + TROUGH_H),
        ),
        topLeft = Offset(x, y),
        size = Size(width, TROUGH_H),
        cornerRadius = CornerRadius(radius),
        alpha = opacity,
    )
    // The texture is pinned to the leading edge, so it travels with the level, and drifts slowly at rest.
    val shift = (edge * 0.7f + time * 2.6f) % STRIPE_PITCH
    paints.block.setRoundRect(x, y, width, TROUGH_H, radius)
    clipPath(paints.block) {
        translate(x + shift, y) {
            drawPath(paints.stripes, rgba(255, 255, 255, 0.22f), alpha = opacity, style = Stroke(0.8f))
        }
    }
    drawRoundRect(
        color = colors.needle,
        topLeft = Offset(x + edge + NEEDLE_AT - NEEDLE_W / 2f, y + (TROUGH_H - NEEDLE_H) / 2f),
        size = Size(NEEDLE_W, NEEDLE_H),
        cornerRadius = CornerRadius(NEEDLE_W / 2f),
        alpha = opacity * clamp01((edge - 5f) / 8f),
    )
}

/**
 * Soft light widget with a big dB reading, a signal-bars glyph and a Low–High trough holding a
 * chunky striped block. The level swings in like a meter needle and overshoots, the stripes
 * travel with it, the bars bounce like an equaliser while it moves, the block turns from green to
 * yellow past 50 dB and the number inside it steps through the 30 dB bands with a pop.
 *
 * @param progress Controlled level, 0…1 of the 120 dB scale. Leave null to show [level] (or the autoPlay loop).
 * @param autoPlay Swings from silence up to [level] like a meter needle, holds, drops and starts over.
 * @param level Reading in dB shown at rest: 40 and 60 are the two cards of the reference.
 * @param tone Fixed colour. When null the meter is green and turns yellow as it passes 50 dB.
 */
@Composable
fun NoiseLevelMeter(
    progress: Float? = null,
    autoPlay: Boolean = false,
    level: Float = 40f,
    tone: NoiseTone? = null,
    title: String = "Current noise level",
    lowLabel: String = "Low",
    highLabel: String = "High",
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val controlled = progress != null
    val restValue = (level / MAX_DB).coerceIn(0f, 1f)
    val target = (progress ?: restValue).coerceIn(0f, 1f)
    val auto = autoPlay && !controlled && !reducedMotion
    val looping by rememberUpdatedState(auto)

    // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
    val time = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(if (auto) 0f else REST_T) }
    var playing by remember { mutableStateOf(false) }
    val held = remember { Animatable(target) }
    // When a controlled value last changed, on the `time` axis.
    val movedAt = remember { mutableFloatStateOf(-10f) }
    val pop = remember { Animatable(1f) }

    LaunchedEffect(auto) {
        playing = auto
        clock.floatValue = if (auto) 0f else REST_T
    }

    LaunchedEffect(controlled, target) {
        if (!controlled || held.targetValue == target) return@LaunchedEffect
        movedAt.floatValue = time.floatValue
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
    val value = { if (controlled) held.value else loopValue(clock.floatValue, restValue) }
    val activity = {
        when {
            reducedMotion -> 0f
            !controlled -> loopActivity(clock.floatValue)
            else -> {
                val since = time.floatValue - movedAt.floatValue
                clamp01(since / 0.15f) * clamp01((0.9f - since) / 0.5f)
            }
        }
    }
    val mix = {
        when (tone) {
            NoiseTone.Yellow -> 1f
            NoiseTone.Green -> 0f
            null -> clamp01((value() * MAX_DB - 46f) / 8f)
        }
    }

    // The numbers are real text: recompose only when a whole dB (or a band) changes.
    val decibels by remember(controlled, restValue) {
        derivedStateOf { ((if (controlled) held.value else loopValue(clock.floatValue, restValue)) * MAX_DB).roundToInt() }
    }
    val band by remember(controlled, restValue) {
        derivedStateOf {
            val db = (if (controlled) held.value else loopValue(clock.floatValue, restValue)) * MAX_DB
            (floor((db + 0.5f) / BAND) * BAND).roundToInt()
        }
    }
    val lastBand = remember { mutableIntStateOf(band) }
    LaunchedEffect(band) {
        val rose = band > lastBand.intValue
        lastBand.intValue = band
        if (rose && !reducedMotion) {
            pop.animateTo(1.28f, tween(90))
            pop.animateTo(1f, tween(320, easing = EaseOutStrong))
        }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { NoiseMeterPaints() }

    Box(
        modifier = modifier
            .size(SIZE.dp)
            .pointerInput(controlled, reducedMotion) {
                detectTapGestures {
                    if (!controlled && !reducedMotion) {
                        clock.floatValue = 0f
                        playing = true
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = title
                stateDescription = "$decibels dB"
                progressBarRangeInfo = ProgressBarRangeInfo(decibels / MAX_DB, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = value()
                    val t = time.floatValue
                    val m = mix()

                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x + 4, y + 10, SIZE - 8, SIZE - 8, RADIUS) }
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFEEEFF3), Color(0xFFE9EAEE)), startY = y, endY = y + SIZE),
                        topLeft = Offset(x, y),
                        size = Size(SIZE, SIZE),
                        cornerRadius = CornerRadius(RADIUS),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(rgba(255, 255, 255, 0.9f), rgba(255, 255, 255, 0.25f)),
                            startY = y,
                            endY = y + SIZE,
                        ),
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(SIZE - 1f, SIZE - 1f),
                        cornerRadius = CornerRadius(RADIUS - 0.5f),
                        style = Stroke(1f),
                    )

                    val tx = x + TROUGH_X
                    val ty = y + TROUGH_Y

                    // Where the level sits in the trough, with the faint tremble of a live microphone.
                    val tremble = ambient * 0.45f * sin(t * 9.1f) * sin(t * 2.3f)
                    val edge = TROUGH_W * clamp01(p) + (if (p > 0.02f) tremble else 0f)

                    // Empty part of the trough, a touch warmer where it meets the needle.
                    val left = min(TROUGH_W, if (edge < 5f) edge else edge + REST_GAP)
                    val restWidth = TROUGH_W - left
                    val restRadius = min(TROUGH_R, restWidth / 2f)
                    drawRoundRect(
                        color = Color(0xFFD9DBE3),
                        topLeft = Offset(tx + left, ty),
                        size = Size(restWidth, TROUGH_H),
                        cornerRadius = CornerRadius(restRadius),
                    )
                    paints.rest.setRoundRect(tx + left, ty, restWidth, TROUGH_H, restRadius)
                    clipPath(paints.rest) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(rgba(212, 211, 212, 0.6f), rgba(212, 211, 212, 0f)),
                                startX = tx + left,
                                endX = tx + left + 18f,
                            ),
                            topLeft = Offset(tx + left, ty),
                            size = Size(18f, TROUGH_H),
                            alpha = clamp01((edge - 5f) / 8f),
                        )
                    }

                    // The yellow copy fades in over the green one, whose glow has to leave or it
                    // would show around it.
                    val breathing = clamp01(edge / 14f) * (0.8f + ambient * 0.2f * sin(t * 2.1f))
                    drawNoiseFill(paints, GreenTone, paints.greenGlow, tx, ty, edge, t, 1f, (1f - m) * breathing)
                    if (m > 0f) drawNoiseFill(paints, YellowTone, paints.yellowGlow, tx, ty, edge, t, m, breathing)

                    // Signal bars: each grows as the level passes 10, 20 and 30 dB, and bounces
                    // like an equaliser while it moves.
                    val busy = activity()
                    for (i in BAR_X.indices) {
                        val grown = clamp01((p * MAX_DB - i * 9f) / 12f)
                        val bounce = 1f + busy * 0.32f * sin(t * (17f - i * 3.5f) + i * 2.1f)
                        val height = min(BAR_H[2] + 1.5f, max(BAR_W, BAR_H[i] * grown * bounce))
                        val topLeft = Offset(x + BAR_X[i] - BAR_W / 2f, y + BARS_BOTTOM - height)
                        val size = Size(BAR_W, height)
                        drawRoundRect(GreenTone.bar, topLeft, size, CornerRadius(BAR_W / 2f))
                        if (m > 0f) drawRoundRect(YellowTone.bar, topLeft, size, CornerRadius(BAR_W / 2f), alpha = m)
                    }
                }
            }
        }

        BasicText(
            text = title,
            style = TitleStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(16.5.dp, 21.dp).clearAndSetSemantics {},
        )
        Row(
            modifier = Modifier.leadingAt(16.5.dp, 51.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.Top,
        ) {
            BasicText(text = decibels.toString(), style = NumberStyle)
            BasicText(text = "dB", style = UnitStyle, modifier = Modifier.padding(start = 6.dp, top = 4.5.dp))
        }
        BasicText(
            text = lowLabel,
            style = ScaleStyle,
            modifier = Modifier.leadingAt(16.5.dp, 99.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = highLabel,
            style = ScaleStyle,
            modifier = Modifier.trailingAt((SIZE - 16.5f).dp, 99.dp).clearAndSetSemantics {},
        )
        // "120" hides as the block closes in on it.
        BasicText(
            text = MAX_DB.roundToInt().toString(),
            style = MarkStyle.copy(color = rgba(255, 255, 255, 0.72f)),
            modifier = Modifier
                .trailingAt((SIZE - 21.1f).dp, 136.5.dp)
                .graphicsLayer { alpha = clamp01((0.78f - value()) / 0.06f) }
                .clearAndSetSemantics {},
        )
        BasicText(
            text = band.toString(),
            style = MarkStyle,
            modifier = Modifier
                .leadingAt(21.1.dp, 136.5.dp)
                .graphicsLayer {
                    alpha = clamp01((value() * MAX_DB - 26f) / 4f)
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clearAndSetSemantics {},
        )
    }
}
