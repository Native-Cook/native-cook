package com.ngocdevv.nativecook.blocks.progress.signalstrengthbar

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 340f
private const val HEIGHT = 218f
private const val PAD = 18f

// The canvas overflows the panel by this much on every side for the backdrop and the brackets.
private val BLEED = 44.dp

private const val HEADER_H = 36f
private const val BAR_X = PAD
private const val BAR_Y = 110f
private const val BAR_W = WIDTH - PAD * 2
private const val BAR_H = 7.5f
private const val TICKS = 60
private const val TICK_W = 1.3f
private const val DIVIDER_Y = 153f
private const val CHIP_Y = 184f
private const val CHIP = 16f
private const val CHIP_PITCH = 19.5f
private const val SCAN_W = 34f
private const val BRACKET = 5f

// The channel chips wake up one after another as the signal passes these levels.
private val CHANNEL_AT = floatArrayOf(0.1f, 0.22f, 0.34f, 0.46f)

// Value shown in the reference, and the autoPlay loop built around it (seconds).
private const val REST = 0.562f
private const val START = 0.55f
private const val FILL = 2.1f
private const val HOLD = 3.6f
private const val RESET = 0.6f
private const val LOOP = START + FILL + HOLD + RESET

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = START + FILL + 1f

private val SignalYellow = Color(0xFFF6EF4C)
private const val BACKDROP_TILT = -58f

// One corner bracket per corner, drawn 1.5dp outside the panel: corner x, corner y, x dir, y dir.
private val SignalCorners = arrayOf(
    floatArrayOf(0f, 0f, 1f, 1f),
    floatArrayOf(WIDTH, 0f, -1f, 1f),
    floatArrayOf(0f, HEIGHT, 1f, -1f),
    floatArrayOf(WIDTH, HEIGHT, -1f, -1f),
)

/** A soft streak of the out-of-focus backdrop: centre, half length, half thickness, colour, alpha. */
private class SignalBlob(val x: Float, val y: Float, val l: Float, val t: Float, r: Int, g: Int, b: Int, val alpha: Float) {
    val brush = Brush.radialGradient(listOf(rgba(r, g, b, 1f), rgba(r, g, b, 0f)), center = Offset.Zero, radius = 1f)
}

private val SignalBackdrop = listOf(
    SignalBlob(40f, -10f, 190f, 70f, 143, 182, 201, 0.95f),
    SignalBlob(170f, 20f, 150f, 40f, 168, 201, 214, 0.7f),
    SignalBlob(60f, 230f, 220f, 60f, 24, 48, 26, 0.9f),
    SignalBlob(300f, 250f, 200f, 70f, 20, 40, 24, 0.9f),
    SignalBlob(330f, 60f, 160f, 46f, 36, 70, 38, 0.8f),
    SignalBlob(250f, 10f, 170f, 16f, 176, 180, 84, 0.7f),
    SignalBlob(20f, 120f, 150f, 14f, 190, 190, 96, 0.6f),
    SignalBlob(120f, 250f, 150f, 12f, 170, 176, 90, 0.5f),
    SignalBlob(350f, 150f, 150f, 18f, 110, 150, 80, 0.7f),
)
private val SignalGlass = SignalBlob(96f, 60f, 190f, 62f, 150, 150, 120, 0.17f)

// Brightness of the static along the ticks: an irregular pattern that jumps about a dozen times a second.
private val StaticPattern = floatArrayOf(
    0f, 0.5f, 0f, 0f, 0.9f, 0f, 0.3f, 0f, 0f, 0.7f, 0f, 0f, 1f, 0f, 0.4f, 0f, 0f, 0.8f, 0f, 0f,
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun panelText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val MonoStyle = panelText(8.6f, 12f, FontWeight.Normal, Color.White).copy(fontFamily = FontFamily.Monospace)
private val LabelStyle = MonoStyle.copy(color = rgba(255, 255, 255, 0.74f), letterSpacing = 0.5.sp)
private val HeaderStyle = panelText(11.5f, 16f, FontWeight.Medium, Color.White)
private val ReadingStyle = panelText(30f, 38f, FontWeight.Medium, Color.White)
    .copy(letterSpacing = (-0.6).sp, fontFeatureSettings = "tnum")

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Signal strength at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < START + FILL -> REST * easeInOutCubic((t - START) / FILL)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how hard the probe is scanning at [t] seconds into the loop. */
private fun loopActivity(t: Float) = clamp01((t - START) / 0.2f) * clamp01((START + FILL + 0.25f - t) / 0.5f)

/** 0…1: how far the corner brackets have locked onto the panel at [t] seconds into the loop. */
private fun loopLock(t: Float) =
    if (t > LOOP - RESET) {
        1f - clamp01((t - (LOOP - RESET)) / (RESET * 0.8f))
    } else {
        1f - (1f - clamp01((t - 0.05f) / 0.45f)).pow(3)
    }

private fun percent(tenths: Int) = String.format(Locale.US, "%.1f%%", tenths / 10f)

/** The static pattern sampled at [x] dp along the scale: it repeats every 97dp. */
private fun staticLevel(x: Float): Float {
    var u = (x % 97f) / 97f
    if (u < 0f) u += 1f
    val position = u * (StaticPattern.size - 1)
    val index = min(position.toInt(), StaticPattern.size - 2)
    val mix = position - index
    return 0.3f * (StaticPattern[index] * (1f - mix) + StaticPattern[index + 1] * mix)
}

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

private class SignalBarPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.4f), sigma = 12f)
    val glow = SoftRoundRect(rgba(246, 239, 76, 0.3f), sigma = 5f)
    val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rgba(255, 252, 170, 0.9f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(2.4f), BlurMaskFilter.Blur.SOLID)
        }
    }
    val bracket = Path()
    val links = Path().apply {
        moveTo(-2.3f, -3.3f)
        lineTo(2.5f, 0f)
        lineTo(-2.3f, 3.3f)
    }
    val half = Path().apply {
        moveTo(0f, -3.9f)
        arcTo(Rect(-3.9f, -3.9f, 3.9f, 3.9f), -90f, 180f, false)
        close()
    }
}

/** A soft-edged streak: a radial fade squashed along the backdrop's tilt. */
private fun DrawScope.softStreak(blob: SignalBlob) {
    withTransform({
        translate(blob.x, blob.y)
        rotate(BACKDROP_TILT, pivot = Offset.Zero)
        scale(blob.l, blob.t, pivot = Offset.Zero)
    }) {
        drawCircle(blob.brush, radius = 1f, center = Offset.Zero, alpha = blob.alpha)
    }
}

/** Generic channel glyphs, each drawn around its own centre: linked nodes, a grid, a target, a half disc. */
private fun DrawScope.drawSignalGlyph(paints: SignalBarPaints, index: Int, alpha: Float) {
    val ink = Color.White
    when (index) {
        0 -> {
            drawPath(paints.links, ink, alpha = alpha, style = Stroke(1f, join = StrokeJoin.Miter))
            drawCircle(ink, 1.5f, Offset(-2.3f, -3.3f), alpha = alpha)
            drawCircle(ink, 1.5f, Offset(2.5f, 0f), alpha = alpha)
            drawCircle(ink, 1.5f, Offset(-2.3f, 3.3f), alpha = alpha)
        }
        1 -> for (x in floatArrayOf(-3.9f, 0.7f)) {
            for (y in floatArrayOf(-3.9f, 0.7f)) {
                drawRoundRect(ink, Offset(x, y), Size(3.2f, 3.2f), CornerRadius(0.9f), alpha = alpha)
            }
        }
        2 -> {
            drawCircle(ink, 3.9f, Offset.Zero, alpha = alpha, style = Stroke(1.1f))
            drawCircle(ink, 1.4f, Offset.Zero, alpha = alpha)
        }
        else -> {
            drawCircle(ink, 3.9f, Offset.Zero, alpha = alpha, style = Stroke(1.1f))
            drawPath(paints.half, ink, alpha = alpha)
        }
    }
}

/**
 * Dark monospace readout over a blurred backdrop: signal strength and noise ratio in large
 * figures, a yellow bar on a ticked 0–100% scale and the active channel chips. The corner
 * brackets fly in and lock onto the panel, a probe scans the ticks ahead of the bar, static
 * crackles over the empty part of the scale, both figures count against each other and the
 * channels come online one by one as the signal climbs.
 *
 * @param progress Controlled signal strength, 0…1. Leave null to show the reference's 56.2% (or the autoPlay loop).
 * @param autoPlay Sweeps from 0 to 56.2% by itself, holds, drops and starts over.
 * @param delta Change shown next to the reading.
 */
@Composable
fun SignalStrengthBar(
    progress: Float? = null,
    autoPlay: Boolean = false,
    section: String = "Threshold",
    title: String = "Signal strength",
    signalLabel: String = "SIGNAL STRENGTH",
    noiseLabel: String = "NOISE RATIO",
    delta: String = "+32%",
    channelsLabel: String = "ACTIVE CHANNELS",
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

    // Read inside draw lambdas so a frame only redraws, it does not recompose.
    val value = { clamp01(if (controlled) held.value else loopValue(clock.floatValue)) }
    val activity = {
        when {
            reducedMotion -> 0f
            !controlled -> loopActivity(clock.floatValue)
            else -> {
                val since = time.floatValue - movedAt.floatValue
                clamp01(since / 0.2f) * clamp01((1.1f - since) / 0.5f)
            }
        }
    }
    val lock = { if (controlled || reducedMotion) 1f else loopLock(clock.floatValue) }

    // The figures are real text: recompose only when a tenth of a percent (or the channel count) changes.
    val tenths by remember(controlled) {
        derivedStateOf { (clamp01(if (controlled) held.value else loopValue(clock.floatValue)) * 1000f).roundToInt() }
    }
    val inputs by remember(controlled) {
        derivedStateOf {
            val v = clamp01(if (controlled) held.value else loopValue(clock.floatValue))
            CHANNEL_AT.count { v >= it + 0.015f }
        }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { SignalBarPaints() }

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
                contentDescription = title
                stateDescription = percent((target * 1000f).roundToInt())
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val p = value()
                    val t = time.floatValue
                    val busy = activity()
                    translate(x, y) {
                        // Out-of-focus backdrop: soft streaks of sky, leaves and light, swaying slowly.
                        val sway = ambient * 9f * sin(t * 0.23f)
                        val tilt = Math.toRadians(BACKDROP_TILT.toDouble()).toFloat()
                        translate(sway * cos(tilt), sway * sin(tilt)) {
                            for (blob in SignalBackdrop) softStreak(blob)
                        }

                        drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, 2f, 8f, WIDTH - 4f, HEIGHT - 4f, 0f) }
                        drawRect(rgba(24, 27, 24, 0.95f), Offset.Zero, Size(WIDTH, HEIGHT))
                        // A paler band where the backdrop shows through the glass.
                        clipRect(0f, 0f, WIDTH, HEIGHT) { softStreak(SignalGlass) }
                        drawRect(
                            color = rgba(255, 255, 255, 0.09f),
                            topLeft = Offset(0.5f, 0.5f),
                            size = Size(WIDTH - 1f, HEIGHT - 1f),
                            style = Stroke(1f),
                        )

                        // Corner brackets: they fly in from outside and snap onto the corners.
                        val locked = lock()
                        val away = (1f - locked) * 9f
                        for (corner in SignalCorners) {
                            val bx = corner[0] - corner[2] * (1.5f + away)
                            val by = corner[1] - corner[3] * (1.5f + away)
                            paints.bracket.rewind()
                            paints.bracket.moveTo(bx + corner[2] * BRACKET, by)
                            paints.bracket.lineTo(bx, by)
                            paints.bracket.lineTo(bx, by + corner[3] * BRACKET)
                            drawPath(paints.bracket, Color.White, alpha = locked, style = Stroke(1f))
                        }

                        val line = rgba(255, 255, 255, 0.09f)
                        drawRect(line, Offset(0f, HEADER_H), Size(WIDTH, 1f))
                        drawRect(line, Offset(PAD, DIVIDER_Y), Size(BAR_W, 1f))
                        drawRect(rgba(150, 148, 50, 0.3f), Offset(WIDTH - PAD - 40f, 10f), Size(40f, 16f))
                        drawRect(rgba(150, 148, 50, 0.34f), Offset(WIDTH - PAD - 54f, CHIP_Y - 0.5f), Size(54f, 17f))

                        // Scale: a faint track with ticks, static on its empty part, the lit ticks ahead of the bar.
                        val fill = BAR_W * p
                        val head = BAR_X + fill
                        drawRect(rgba(255, 255, 255, 0.05f), Offset(BAR_X, BAR_Y), Size(BAR_W, BAR_H))
                        val shift = (floor(t * 13f) * 37.7f) % 97f
                        val crackle = ambient * (0.35f + 0.65f * (1f - p))
                        val scanAlpha = 0.25f + 0.75f * busy
                        for (i in 0 until TICKS) {
                            val center = BAR_X + (i + 0.5f) * (BAR_W / TICKS)
                            val topLeft = Offset(center - TICK_W / 2f, BAR_Y)
                            val size = Size(TICK_W, BAR_H)
                            drawRect(Color.White, topLeft, size, alpha = 0.15f)
                            if (center <= head) continue
                            // Static crackles over the empty part of the scale, louder the higher the noise ratio.
                            val noise = staticLevel(center - BAR_X - shift) * crackle
                            if (noise > 0f) drawRect(Color.White, topLeft, size, alpha = noise)
                            // Ticks just ahead of the bar light up, as if a probe were scanning them.
                            val ahead = 1f - (center - head) / SCAN_W
                            if (ahead > 0f) drawRect(SignalYellow, topLeft, size, alpha = 0.95f * ahead * scanAlpha)
                        }

                        drawIntoCanvas { paints.glow.draw(it.nativeCanvas, BAR_X, BAR_Y + 1f, fill, BAR_H, 0f) }
                        drawRect(SignalYellow, Offset(BAR_X, BAR_Y), Size(fill, BAR_H))

                        // The probe: a hot line at the head of the bar while it moves.
                        val headAlpha = busy * clamp01(p / 0.01f)
                        if (headAlpha > 0f) {
                            paints.probe.alpha = (255f * 0.9f * headAlpha).roundToInt().coerceIn(0, 255)
                            drawIntoCanvas {
                                it.nativeCanvas.drawRect(head - 0.8f, BAR_Y - 3.5f, head + 0.8f, BAR_Y + BAR_H + 3.5f, paints.probe)
                            }
                        }

                        // Channel chips: each wakes up with a yellow flash as the signal passes its level.
                        for (index in CHANNEL_AT.indices) {
                            val cx = PAD + index * CHIP_PITCH
                            val on = clamp01((p - CHANNEL_AT[index]) / 0.03f)
                            drawRect(Color.White, Offset(cx, CHIP_Y), Size(CHIP, CHIP), alpha = 0.09f)
                            drawRect(SignalYellow, Offset(cx, CHIP_Y), Size(CHIP, CHIP), alpha = 4f * on * (1f - on))
                            translate(cx + CHIP / 2f, CHIP_Y + CHIP / 2f) {
                                drawSignalGlyph(paints, index, 0.28f + 0.72f * on)
                            }
                        }
                    }
                }
            }
        }

        val signal = percent(tenths)
        // The two readings always add up to 100.0.
        val noise = percent(1000 - tenths)

        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = rgba(255, 255, 255, 0.42f))) { append("$section  /  ") }
                append(title)
            },
            style = HeaderStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(PAD.dp, (HEADER_H / 2f).dp).clearAndSetSemantics {},
        )
        BasicText(
            text = signal,
            style = MonoStyle.copy(color = Color(0xFFECE65C)),
            modifier = Modifier.centerAt((WIDTH - PAD - 20f).dp, 18.dp).clearAndSetSemantics {},
        )

        BasicText(
            text = signalLabel,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(PAD.dp, 55.dp).clearAndSetSemantics {},
        )
        // The delta follows the end of the reading, whatever its digit count.
        Row(
            modifier = Modifier.leadingAt(PAD.dp, 85.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BasicText(text = signal, style = ReadingStyle, modifier = Modifier.height(38.dp))
            BasicText(
                text = delta,
                style = MonoStyle.copy(color = Color(0xFF65DC63)),
                modifier = Modifier.padding(top = 20.5.dp),
            )
        }
        // The noise label starts where its reading starts.
        Column(
            modifier = Modifier.trailingAt((WIDTH - PAD).dp, 76.5.dp).clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BasicText(text = noiseLabel, style = LabelStyle, maxLines = 1, modifier = Modifier.height(12.dp))
            BasicText(text = noise, style = ReadingStyle, modifier = Modifier.height(38.dp))
        }

        Row(
            modifier = Modifier.centerAt((WIDTH / 2f).dp, 133.dp).width(BAR_W.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (mark in listOf("0%", "25%", "50%", "75%", "100%")) {
                BasicText(text = mark, style = MonoStyle.copy(color = rgba(255, 255, 255, 0.5f)))
            }
        }

        BasicText(
            text = channelsLabel,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(PAD.dp, 172.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = "$inputs ${if (inputs == 1) "INPUT" else "INPUTS"}",
            style = MonoStyle.copy(color = Color(0xFFF0EA58), letterSpacing = 0.4.sp),
            modifier = Modifier.centerAt((WIDTH - PAD - 27f).dp, (CHIP_Y + 8f).dp).clearAndSetSemantics {},
        )
    }
}
