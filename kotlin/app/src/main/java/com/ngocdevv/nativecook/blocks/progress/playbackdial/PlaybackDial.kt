package com.ngocdevv.nativecook.blocks.progress.playbackdial

import com.ngocdevv.nativecook.shared.EaseOutStrong
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.semantics.stateDescription
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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 330f
private const val HEIGHT = 250f
private const val RADIUS = 10f

// The canvases overflow the card by this much on every side to fit the dial's shadow and ripple.
private val BLEED = 30.dp

private const val CX = 165f
private const val CY = 127f
private const val R = 88f
private const val R_FACE = 77.7f
private const val R_WEDGE = 58f
private const val R_HAND = 41.7f
private const val R_HUB = 8.15f

private const val PILL_X = 24.5f
private const val PILL_Y = 70.4f
private const val PILL_W = 273f
private const val PILL_H = 31.6f
private const val PILL_CY = PILL_Y + PILL_H / 2f
private const val WAVE_X = 57.4f
private const val WAVE_PITCH = 2.8f
private const val WAVE_BARS = 66
private val WAVE_HEIGHTS = floatArrayOf(3.6f, 3.6f, 5.4f, 3.6f, 13.5f, 3.6f, 9f, 15f, 15f, 12f, 6f, 10f, 4f, 14f, 8f, 5f)

// The reference's frame: 16:30 into a 32:42 recording. The minute hand reads the elapsed time on
// a 60 minute face; the wedge is a faster sweep geared to land on 12 o'clock at that position.
private const val REST = 990f / 1962f
private const val SWEEP_TURNS = 2f / REST
private const val TAIL = 64f

// The autoPlay loop built around it (seconds).
private const val START = 0.5f
private const val RUN = 2.8f
private const val HOLD = 4.2f
private const val RESET = 1.0f
private const val LOOP = START + RUN + HOLD + RESET
private const val LANDED = START + RUN

// A moment in the loop where everything is at rest: used when nothing is playing.
private const val REST_T = LANDED + 1.8f

/** A twinkling dot: position, radius and twinkle group. */
private class Speck(val x: Float, val y: Float, val r: Float, val group: Int)

// Stars on the dial face, relative to its centre.
private val STARS = listOf(
    Speck(-35.5f, -34f, 0.6f, 0), Speck(-31.7f, -20.8f, 0.7f, 1), Speck(37.4f, -34f, 0.6f, 2), Speck(34f, -20.8f, 0.7f, 0),
    Speck(-63f, -29.3f, 0.9f, 1), Speck(65f, -29.3f, 0.9f, 2), Speck(-58f, 23.9f, 1.1f, 2), Speck(60f, 23.9f, 1.1f, 0),
    Speck(-27.2f, 37.8f, 0.6f, 0), Speck(29.4f, 37.8f, 0.6f, 1), Speck(-4f, 43.5f, 0.5f, 2), Speck(5.7f, 43.5f, 0.5f, 1),
    Speck(-46f, 63.4f, 1f, 1), Speck(48f, 63.4f, 1f, 2), Speck(-61.6f, 16.3f, 0.4f, 0), Speck(63.4f, 16.3f, 0.4f, 1),
)

// Dust at the foot of the card, in card coordinates.
private val DUST = listOf(
    Speck(44.7f, 184f, 0.6f, 0), Speck(50.7f, 200.8f, 0.5f, 1), Speck(61.3f, 200.8f, 0.4f, 2), Speck(97.5f, 210f, 0.5f, 0),
    Speck(235f, 210f, 0.5f, 1), Speck(269.7f, 200.8f, 0.5f, 2), Speck(281.8f, 200f, 0.6f, 0), Speck(287.8f, 184f, 0.6f, 1),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun dialText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = NoFontPadding,
)

private val TitleStyle = dialText(15.8f, 22f, FontWeight.Medium, Color(0xFFF1F1F4)).copy(letterSpacing = (-0.1).sp)
private val InitialStyle = dialText(5.6f, 7f, FontWeight.Bold, Color.White)
private val AuthorStyle = dialText(9.4f, 14f, FontWeight.Medium, Color(0xFFE4E4E8)).copy(letterSpacing = 0.2.sp)
private val DurationStyle = dialText(9.4f, 14f, FontWeight.Normal, Color(0xFF9C9CA3))
    .copy(letterSpacing = 0.3.sp, fontFeatureSettings = "tnum")
private val HeadingStyle = dialText(9.8f, 14f, FontWeight.SemiBold, Color(0xFFBCBCC2))
private val BodyStyle = dialText(10.4f, 16f, FontWeight.Normal, Color(0xFF8B8B92))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** Playback position at [t] seconds into the autoPlay loop. */
private fun loopValue(t: Float) = when {
    t < START -> 0f
    t < LANDED -> REST * easeInOutCubic((t - START) / RUN)
    t < LOOP - RESET -> REST
    else -> REST * (1f - easeInOutCubic((t - (LOOP - RESET)) / RESET))
}

/** 0…1: how fast the dial is turning at [t] seconds into the loop. */
private fun loopActivity(t: Float): Float {
    if (t >= LOOP - RESET) return sin(PI.toFloat() * clamp01((t - (LOOP - RESET)) / RESET))
    val u = (t - START) / RUN
    return if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
}

/** Brightness of twinkle group [group] at [time]. */
private fun twinkle(time: Float, group: Int, ambient: Float) =
    0.62f + 0.38f * ambient * sin(time * (1.5f + 0.35f * group) + group * 2.1f)

private fun timecode(seconds: Int) = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

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

private class DialPaints {
    val center = Offset(CX, CY)
    val face = Path().apply { addOval(Rect(center, R_FACE)) }
    val waveform = Path().apply {
        for (i in 0 until WAVE_BARS) {
            val h = WAVE_HEIGHTS[i % WAVE_HEIGHTS.size]
            moveTo(WAVE_X + i * WAVE_PITCH, PILL_CY - h / 2f)
            lineTo(WAVE_X + i * WAVE_PITCH, PILL_CY + h / 2f)
        }
    }
    val minorTicks = ticks(9f, 68.5f, 72.5f) { it % 5 != 0 }
    val majorTicks = ticks(45f, 62f, 67f) { true }

    // A generic "sound" glyph under the hub: three short bars.
    val glyph = Path().apply {
        moveTo(CX - 4.5f, CY + 23f); lineTo(CX - 4.5f, CY + 27f)
        moveTo(CX, CY + 20.5f); lineTo(CX, CY + 29.5f)
        moveTo(CX + 4.5f, CY + 23f); lineTo(CX + 4.5f, CY + 27f)
    }

    private fun ticks(step: Float, from: Float, to: Float, keep: (Int) -> Boolean) = Path().apply {
        var i = 0
        while (i * step < 360f) {
            if (keep(i)) {
                val a = (i * step - 90f) * PI.toFloat() / 180f
                moveTo(CX + from * cos(a), CY + from * sin(a))
                lineTo(CX + to * cos(a), CY + to * sin(a))
            }
            i++
        }
    }

    val cardFill = Brush.linearGradient(
        0f to Color(0xFF2E2E3D), 0.45f to Color(0xFF18181D), 1f to Color(0xFF0E0E11),
        start = Offset.Zero, end = Offset(WIDTH * 0.55f, HEIGHT),
    )
    val cardEdge = Brush.linearGradient(
        listOf(rgba(150, 150, 185, 0.75f), rgba(255, 255, 255, 0.12f), rgba(255, 255, 255, 0.05f)),
        start = Offset.Zero, end = Offset(WIDTH, HEIGHT * 0.7f),
    )
    val cardFade = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 150f, endY = HEIGHT - 6f)

    val bezel = Brush.linearGradient(
        0f to Color(0xFF56526C), 0.42f to Color(0xFF4A4760), 0.66f to Color(0xFF5F5488),
        0.88f to Color(0xFFA37FE6), 1f to Color(0xFFE2B3FA),
        start = Offset(CX - 20f, CY - R), end = Offset(CX + 20f, CY + R),
    )
    val bezelEdge = Brush.sweepGradient(
        0f to rgba(170, 160, 235, 0.2f), 0.22f to rgba(255, 225, 255, 0.85f), 0.5f to rgba(170, 160, 235, 0.15f),
        0.86f to rgba(176, 166, 240, 0.95f), 1f to rgba(170, 160, 235, 0.2f),
        center = center,
    )
    val faceFill = Brush.verticalGradient(
        listOf(Color(0xFF1C1B25), Color(0xFF1B1A23), Color(0xFF24143A)), startY = CY - R_FACE, endY = CY + R_FACE,
    )
    val violet = Brush.radialGradient(
        0.2f to rgba(112, 74, 232, 0.95f), 0.6f to rgba(112, 74, 232, 0.45f), 1f to rgba(112, 74, 232, 0f),
        center = Offset(CX - 34f, CY + R_FACE + 18f), radius = 84f,
    )
    val magenta = Brush.radialGradient(
        0.2f to rgba(226, 88, 236, 1f), 0.58f to rgba(204, 76, 226, 0.5f), 1f to rgba(204, 76, 226, 0f),
        center = Offset(CX + 32f, CY + R_FACE + 16f), radius = 84f,
    )
    val lip = Brush.verticalGradient(
        0f to rgba(20, 20, 28, 0.9f), 0.6f to rgba(60, 50, 90, 0.6f), 1f to rgba(246, 214, 255, 0.95f),
        startY = CY - R_FACE, endY = CY + R_FACE,
    )
    val sink = Brush.radialGradient(
        0.1f to rgba(27, 26, 35, 0.7f), 0.5f to rgba(27, 26, 35, 0.22f), 0.9f to rgba(27, 26, 35, 0f),
        center = center, radius = R_WEDGE,
    )
    val hub = Brush.radialGradient(
        0f to Color.White, 0.5f to Color(0xFFECECF0), 1f to Color(0xFFB9B9C2),
        center = Offset(CX - 2.4f, CY - 2.8f), radius = R_HUB * 1.7f,
    )

    private fun soft(color: Color, sigma: Float, stroke: Float? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (stroke != null) {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    val shadow = soft(rgba(0, 0, 0, 0.75f), 13f)
    val glow = soft(rgba(186, 96, 240, 0.55f), 20f)
    val ripple = soft(Color(0xFFE7C2FF), 1.2f, 1.6f)
    val rim = soft(rgba(0, 0, 0, 0.6f), 5f, 10f)
    val flare = soft(Color(0xFFF1C9FF), 3.5f)
    val handShadow = soft(rgba(0, 0, 0, 0.45f), 1.2f, 1.4f)
    val hubShadow = soft(rgba(0, 0, 0, 0.5f), 3f)
}

/**
 * Call-notes card with a recording row, and a glassy violet dial floating over it as the
 * recording's playback indicator: the thin hand reads the elapsed minutes and a wedge sweeps
 * round ahead of it. The wedge is a comet whose tail stretches with speed and whose leading edge
 * flares as it stops on 12 o'clock, the hand settles with a wobble, a ring leaves the bezel on
 * landing, and at rest the stars on the face twinkle while the lit foot of the bezel breathes.
 *
 * @param progress Controlled playback position, 0…1 of the recording. Leave null to show the reference's frame (or the autoPlay loop).
 * @param autoPlay Plays by itself: the dial scrubs from the start to the reference's position, holds, rewinds and starts over.
 * @param duration Length of the recording in seconds; the minute hand and the time label are derived from it.
 */
@Composable
fun PlaybackDial(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Design Sync Call notes",
    author: String = "Zach",
    updated: String = "Last updated 2h ago",
    duration: Int = 1962,
    onPlayClick: () -> Unit = {},
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
        held.animateTo(target, tween(if (reducedMotion) 200 else 900, easing = EaseOutStrong))
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
                val u = (time.floatValue - movedAt.floatValue) / 0.8f
                if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)
            }
        }
    }
    // Seconds since the dial landed on its value; large when nothing happened.
    val landed = {
        when {
            reducedMotion -> 10f
            !controlled -> if (clock.floatValue < LOOP - RESET) clock.floatValue - LANDED else 10f
            else -> time.floatValue - movedAt.floatValue - 0.7f
        }
    }
    // Elapsed time for assistive tech, in half minutes so it does not recompose every frame.
    val elapsed by remember(controlled, duration) {
        derivedStateOf {
            val seconds = clamp01(if (controlled) held.value else loopValue(clock.floatValue)) * duration
            (seconds / 30f).roundToInt() * 30
        }
    }

    val ambient = if (reducedMotion) 0f else 1f
    val paints = remember { DialPaints() }

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
                contentDescription = "$title, recording playback"
                stateDescription = "${timecode(elapsed)} of ${timecode(duration)}"
                progressBarRangeInfo = ProgressBarRangeInfo(elapsed / duration.toFloat(), 0f..1f)
            },
    ) {
        // The note card behind the text. Drawn offscreen so its foot can be dissolved by a mask.
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                surface(BLEED) { x, y, _, _ ->
                    translate(x, y) { drawCard(paints, value(), time.floatValue, ambient) }
                }
            }
        }

        BasicText(title, style = TitleStyle, maxLines = 1, modifier = Modifier.leadingAt(24.5.dp, 32.3.dp).clearAndSetSemantics {})
        Box(
            modifier = Modifier.centerAt(30.2.dp, 53.2.dp).size(10.dp).background(Color(0xFFE2795B), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(author.take(1).uppercase(), style = InitialStyle, modifier = Modifier.clearAndSetSemantics {})
        }
        BasicText(
            text = buildAnnotatedString {
                append(author)
                withStyle(SpanStyle(color = Color(0xFF77777E), fontWeight = FontWeight.Normal)) { append("   $updated") }
            },
            style = AuthorStyle,
            maxLines = 1,
            modifier = Modifier.leadingAt(38.dp, 53.2.dp).clearAndSetSemantics {},
        )
        BasicText(
            text = timecode(duration),
            style = DurationStyle,
            maxLines = 1,
            modifier = Modifier.trailingAt(287.5.dp, PILL_CY.dp).clearAndSetSemantics {},
        )
        BasicText("Summary", style = HeadingStyle, modifier = Modifier.leadingAt(24.5.dp, 126.8.dp).clearAndSetSemantics {})
        BasicText("The team brainstormed new layouts", style = BodyStyle, maxLines = 1, modifier = Modifier.leadingAt(24.5.dp, 147.dp).clearAndSetSemantics {})
        BasicText("and", style = BodyStyle, modifier = Modifier.leadingAt(252.dp, 147.dp).clearAndSetSemantics {})
        BasicText("aligned on what ships next.", style = BodyStyle, maxLines = 1, modifier = Modifier.leadingAt(24.5.dp, 163.dp).clearAndSetSemantics {})

        // Violet round button: play while the dial rests, pause while it runs.
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
        Canvas(
            Modifier
                .centerAt(40.5.dp, PILL_CY.dp)
                .size(24.dp)
                .graphicsLayer {
                    val scale = 1f - 0.08f * press
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Play recording", onClick = onPlayClick),
        ) {
            val running = clamp01(activity() * 6f)
            val unit = size.width / 24f
            drawCircle(
                Brush.linearGradient(listOf(Color(0xFF8777FB), Color(0xFF6654E6)), Offset(4f * unit, 2f * unit), Offset(20f * unit, 22f * unit)),
                10.6f * unit,
            )
            drawCircle(rgba(255, 255, 255, 0.22f), 10.2f * unit, style = Stroke(0.8f * unit))
            val triangle = Path().apply {
                moveTo(10.1f * unit, 8.9f * unit)
                lineTo(15.2f * unit, 12f * unit)
                lineTo(10.1f * unit, 15.1f * unit)
                close()
            }
            drawPath(triangle, Color.White, alpha = 1f - running)
            drawPath(triangle, Color.White, alpha = 1f - running, style = Stroke(1.1f * unit, join = StrokeJoin.Round))
            for (bx in floatArrayOf(10.2f, 13.8f)) {
                drawLine(Color.White, Offset(bx * unit, 9f * unit), Offset(bx * unit, 15f * unit), 1.9f * unit, StrokeCap.Round, alpha = running)
            }
        }

        // The dial that floats over the card.
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    translate(x, y) {
                        drawDial(paints, value(), time.floatValue, activity(), landed(), ambient, duration / 60f)
                    }
                }
            }
        }
    }
}

/** Surface, recording pill with its waveform, skeleton lines and dust; dissolves towards its foot. */
private fun DrawScope.drawCard(paints: DialPaints, value: Float, time: Float, ambient: Float) {
    drawRoundRect(paints.cardFill, Offset.Zero, Size(WIDTH, HEIGHT + 20f), CornerRadius(RADIUS))
    drawRoundRect(
        brush = paints.cardEdge,
        topLeft = Offset(0.5f, 0.5f),
        size = Size(WIDTH - 1f, HEIGHT + 19f),
        cornerRadius = CornerRadius(RADIUS - 0.5f),
        style = Stroke(1f),
    )

    // Recording pill
    drawRoundRect(Color(0xFF2A2A2F), Offset(PILL_X, PILL_Y), Size(PILL_W, PILL_H), CornerRadius(PILL_H / 2f))
    drawRoundRect(
        color = rgba(255, 255, 255, 0.16f),
        topLeft = Offset(PILL_X + 0.35f, PILL_Y + 0.35f),
        size = Size(PILL_W - 0.7f, PILL_H - 0.7f),
        cornerRadius = CornerRadius(PILL_H / 2f),
        style = Stroke(0.7f),
    )
    val bars = Stroke(1.25f, cap = StrokeCap.Round)
    drawPath(paints.waveform, rgba(255, 255, 255, 0.3f), style = bars)
    clipRect(WAVE_X - 1f, PILL_Y, WAVE_X - 1f + value * (WAVE_BARS - 1) * WAVE_PITCH + 1.6f, PILL_Y + PILL_H) {
        drawPath(paints.waveform, Color(0xFFF4F4F6), style = bars)
    }
    for (i in 0 until 3) drawCircle(Color(0xFF77777D), 0.7f, Offset(246.4f + i * 3.35f, PILL_CY))

    // Skeleton lines of the summary
    drawRoundRect(rgba(255, 255, 255, 0.13f), Offset(245.5f, 160.8f), Size(50.5f, 4.6f), CornerRadius(2.3f))
    drawRoundRect(rgba(255, 255, 255, 0.11f), Offset(24.5f, 184.5f), Size(205f, 4.6f), CornerRadius(2.3f))
    drawRoundRect(rgba(255, 255, 255, 0.1f), Offset(24.5f, 198.5f), Size(216f, 4.6f), CornerRadius(2.3f))
    drawRoundRect(rgba(255, 255, 255, 0.08f), Offset(24.5f, 225f), Size(186f, 4.6f), CornerRadius(2.3f))
    for (speck in DUST) {
        drawCircle(Color.White, speck.r, Offset(speck.x, speck.y), alpha = 0.8f * twinkle(time, speck.group, ambient))
    }

    drawRect(paints.cardFade, Offset(-2f, -2f), Size(WIDTH + 4f, HEIGHT + 24f), blendMode = BlendMode.DstIn)
}

private fun DrawScope.drawDial(
    paints: DialPaints,
    value: Float,
    time: Float,
    activity: Float,
    landed: Float,
    ambient: Float,
    minutes: Float,
) {
    val center = paints.center
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    if (blurs) {
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.drawCircle(CX, CY + 9f, R - 2f, paints.shadow)
            // Glow under the lit foot of the bezel: it breathes.
            val glow = 0.5f + 0.2f * ambient * sin(time * 1.1f) + 0.3f * activity
            paints.glow.alpha = (255f * 0.55f * glow).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(CX + 6f, CY + 26f, R - 22f, paints.glow)
            // A ring leaves the dial when it lands.
            val u = landed / 0.9f
            if (u > 0f && u < 1f) {
                paints.ripple.alpha = (255f * 0.5f * (1f - u)).roundToInt()
                canvas.drawCircle(CX, CY, R + 1f + 18f * (1f - (1f - u).pow(3)), paints.ripple)
            }
        }
    }

    // Bezel: grey-violet glass that lights up towards its foot.
    drawCircle(paints.bezel, R, center)
    drawCircle(paints.bezelEdge, R - 0.6f, center, style = Stroke(1.2f))

    // Face
    drawCircle(paints.faceFill, R_FACE, center)
    clipPath(paints.face) {
        drawCircle(paints.violet, 84f, Offset(CX - 34f, CY + R_FACE + 18f))
        drawCircle(paints.magenta, 84f, Offset(CX + 32f, CY + R_FACE + 16f))
        // Shadow the bezel casts onto the face.
        if (blurs) drawIntoCanvas { it.nativeCanvas.drawCircle(CX, CY + 3f, R_FACE + 4f, paints.rim) }
    }
    drawCircle(paints.lip, R_FACE - 0.4f, center, style = Stroke(0.9f))
    drawCircle(rgba(255, 255, 255, 0.07f), 72.5f, center, style = Stroke(0.6f))
    drawPath(paints.minorTicks, rgba(255, 255, 255, 0.13f), style = Stroke(0.6f, cap = StrokeCap.Round))
    drawPath(paints.majorTicks, rgba(238, 236, 248, 0.85f), style = Stroke(1.1f, cap = StrokeCap.Round))
    for (star in STARS) {
        drawCircle(Color(0xFFE9E2FF), star.r, Offset(CX + star.x, CY + star.y), alpha = twinkle(time, star.group, ambient))
    }
    drawPath(paints.glyph, rgba(178, 164, 220, 0.3f), style = Stroke(2.2f, cap = StrokeCap.Round))

    // Sweep wedge: a conic gradient whose seam is the bright leading edge. Its tail stretches with speed.
    val length = (TAIL + 76f * activity + 3f * ambient * sin(time * 1.3f)) / 360f
    rotate(value * SWEEP_TURNS * 360f - 90f, pivot = center) {
        drawCircle(
            Brush.sweepGradient(
                0f to rgba(120, 76, 236, 0f),
                1f - length to rgba(120, 76, 236, 0f),
                1f - 0.56f * length to rgba(128, 84, 242, 0.72f),
                1f - 0.2f * length to rgba(200, 140, 255, 1f),
                1f to Color(0xFFFAE4FF),
                center = center,
            ),
            R_WEDGE,
            center,
        )
        // The wedge sinks into the face towards the hub.
        drawCircle(paints.sink, R_WEDGE, center)
        // The edge flares up as the dial lands.
        if (blurs) {
            val u = landed / 0.7f
            val flare = 0.55f + 0.25f * activity + (if (u > 0f && u < 1f) 0.45f * (1f - u) else 0f)
            paints.flare.alpha = (255f * flare).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { it.nativeCanvas.drawRect(CX + 16f, CY - 1.5f, CX + R_WEDGE, CY + 1.5f, paints.flare) }
        }
        drawLine(rgba(255, 255, 255, 0.92f), Offset(CX + 8f, CY), Offset(CX + R_WEDGE, CY), 0.9f)
    }

    // Minute hand: settles with a small damped wobble.
    val wobble = if (landed > 0f && landed < 1.2f) 3.2f * exp(-5f * landed) * sin(17f * landed) else 0f
    rotate(value * minutes * 6f + wobble, pivot = center) {
        if (blurs) {
            drawIntoCanvas { it.nativeCanvas.drawLine(CX + 0.6f, CY + 1.2f, CX + 0.6f, CY - R_HAND + 1.2f, paints.handShadow) }
        }
        drawLine(Color(0xFFDCDCE4), center, Offset(CX, CY - R_HAND), 1.05f, StrokeCap.Round)
    }

    // Hub
    if (blurs) drawIntoCanvas { it.nativeCanvas.drawCircle(CX + 0.5f, CY + 2.5f, R_HUB + 0.5f, paints.hubShadow) }
    drawCircle(paints.hub, R_HUB, center)
}
