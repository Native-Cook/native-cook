package com.ngocdevv.nativecook.blocks.buttons.recordbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Diameter of the glass sphere at rest and while recording, and the pill that opens behind it.
private const val SPHERE = 72f
private const val SPHERE_RECORDING = 76f
private const val PILL_WIDTH = 208f
private const val PILL_HEIGHT = 66f

// Distance from the centre to the middle of each timer column.
private const val COLUMN = 68f

// The canvas overflows the sphere by this much on every side to fit the pill, the glow and the reflection.
private val BLEED = 104.dp
private const val REFLECTION_DROP = 84f

// How long the green "saved" flash stays before the pill closes.
private const val DONE_MS = 800L
private const val AUTOPLAY_REST_MS = 1500L
private const val AUTOPLAY_RECORD_MS = 3400L
private const val PULSE_HZ = 1.1f

private val MicInk = Color(0xFFF4F4F4)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val DigitStyle = TextStyle(
    color = Color(0xFFF2F2F2),
    fontSize = 23.sp,
    lineHeight = 25.sp,
    fontWeight = FontWeight.ExtraLight,
    fontFeatureSettings = "tnum",
    platformStyle = NoFontPadding,
)
private val UnitStyle = TextStyle(
    color = rgba(255, 255, 255, 0.78f),
    fontSize = 8.sp,
    lineHeight = 10.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.6.sp,
    platformStyle = NoFontPadding,
)

private enum class RecordPhase { Idle, Recording, Done }

/** Paints and paths, all laid out around the origin; the canvas is translated to the sphere's centre. */
private class RecordPaints {
    val redGlow = SoftRoundRect(rgba(255, 44, 48, 0.75f), sigma = 14f)
    val greenGlow = SoftRoundRect(rgba(170, 235, 60, 0.6f), sigma = 14f)
    val dotGlow = SoftRoundRect(rgba(255, 70, 74, 0.85f), sigma = 7f)
    val micGlow = SoftRoundRect(rgba(255, 255, 255, 0.22f), sigma = 10f)

    val floor = fade(255, 255, 255, 0.1f, 0.03f)
    val redFloor = fade(255, 40, 44, 0.2f, 0.07f)
    val greenFloor = fade(170, 235, 60, 0.16f, 0.05f)

    val pillTint = Brush.radialGradient(
        listOf(rgba(255, 48, 52, 0.3f), rgba(255, 48, 52, 0f)),
        center = Offset.Zero,
        radius = 92f,
    )
    val pillEdge = Brush.verticalGradient(
        listOf(rgba(255, 255, 255, 0.14f), rgba(255, 255, 255, 0.03f)),
        startY = -PILL_HEIGHT / 2,
        endY = PILL_HEIGHT / 2,
    )

    // Glass sphere: grey at rest, with a red and a green version faded over it.
    val greyGlass = glass(0xFF625E5F, 0xFF3B3838, 0xFF222020, 0.6f, Offset(0f, -4f), SPHERE_RECORDING * 0.55f)
    val redGlass = glass(0xFF7F3136, 0xFFA8272C, 0xFFF42C30, 0.55f, Offset(0f, -6f), SPHERE_RECORDING * 0.6f)
    val greenGlass = glass(0xFF56663A, 0xFF7C9A30, 0xFFB9E23C, 0.55f, Offset(0f, -6f), SPHERE_RECORDING * 0.6f)
    val rim = Brush.verticalGradient(
        listOf(rgba(255, 255, 255, 0.3f), rgba(255, 255, 255, 0.04f)),
        startY = -SPHERE / 2,
        endY = SPHERE / 2,
    )

    // Microphone: capsule body, cradle, stem and foot.
    val micLines = Path().apply {
        // Angles grow clockwise on screen, so a 180° sweep from 0° passes through the bottom.
        arcTo(Rect(-14f, 1.8f - 14f, 14f, 1.8f + 14f), 0f, 180f, forceMoveTo = true)
        moveTo(0f, 15.8f)
        lineTo(0f, 20.7f)
        moveTo(-10f, 20.7f)
        lineTo(10f, 20.7f)
    }
    val micStroke = Stroke(width = 2.5f, cap = StrokeCap.Round)

    private fun fade(r: Int, g: Int, b: Int, peak: Float, mid: Float) = Brush.radialGradient(
        0f to rgba(r, g, b, peak),
        0.5f to rgba(r, g, b, mid),
        1f to rgba(r, g, b, 0f),
        center = Offset.Zero,
        radius = 26f,
    )

    private fun glass(inner: Long, middle: Long, outer: Long, stop: Float, center: Offset, radius: Float) =
        Brush.radialGradient(
            0f to Color(inner),
            stop to Color(middle),
            1f to Color(outer),
            center = center,
            radius = radius,
        )
}

/**
 * Round glass microphone button. A tap opens it into a recording pill with a pulsing red dot and
 * a running timer; a second tap flashes it green and closes it again.
 *
 * @param recording Controlled state. Leave null to let the button start and stop by itself on tap.
 * @param autoPlay Records for a few seconds, stops, rests, and starts over.
 */
@Composable
fun RecordButton(
    recording: Boolean? = null,
    onRecordingChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    minutesLabel: String = "MIN.",
    secondsLabel: String = "SEC.",
    contentDescription: String = "Record",
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(if (recording == true) RecordPhase.Recording else RecordPhase.Idle) }
    var seconds by remember { mutableIntStateOf(0) }
    val onChange by rememberUpdatedState(onRecordingChange)

    // 0 = sphere only, 1 = pill fully open.
    val expand = remember { Animatable(if (phase == RecordPhase.Idle) 0f else 1f) }
    val red = remember { Animatable(if (phase == RecordPhase.Recording) 1f else 0f) }
    val green = remember { Animatable(0f) }
    // 1 = microphone, 0 = record dot.
    val mic = remember { Animatable(if (phase == RecordPhase.Recording) 0f else 1f) }
    val time = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    // Follow the controlled value.
    LaunchedEffect(recording) {
        if (recording == true && phase != RecordPhase.Recording) {
            seconds = 0
            phase = RecordPhase.Recording
        } else if (recording == false && phase == RecordPhase.Recording) {
            phase = RecordPhase.Done
        }
    }

    LaunchedEffect(phase) {
        coroutineScope {
            fun Animatable<Float, *>.to(target: Float, ms: Int) =
                launch { animateTo(target, tween(ms, easing = EaseOutStrong)) }
            when (phase) {
                RecordPhase.Recording -> {
                    expand.to(1f, 520)
                    red.to(1f, 320)
                    green.to(0f, 200)
                    mic.to(0f, 260)
                }
                RecordPhase.Done -> {
                    red.to(0f, 260)
                    green.to(1f, 260)
                    mic.to(1f, 300)
                }
                RecordPhase.Idle -> {
                    expand.to(0f, 480)
                    red.to(0f, 300)
                    green.to(0f, 500)
                    mic.to(1f, 300)
                }
            }
        }
    }

    LaunchedEffect(phase) {
        when (phase) {
            RecordPhase.Idle -> Unit
            RecordPhase.Recording -> while (true) {
                delay(1000)
                seconds++
            }
            RecordPhase.Done -> {
                delay(DONE_MS)
                phase = RecordPhase.Idle
            }
        }
    }

    LaunchedEffect(phase, autoPlay) {
        if (!autoPlay || phase == RecordPhase.Done) return@LaunchedEffect
        if (phase == RecordPhase.Recording) {
            delay(AUTOPLAY_RECORD_MS)
            phase = RecordPhase.Done
        } else {
            delay(AUTOPLAY_REST_MS)
            seconds = 0
            phase = RecordPhase.Recording
        }
    }

    val pulsing = phase == RecordPhase.Recording && !reducedMotion
    LaunchedEffect(pulsing) {
        if (!pulsing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time.floatValue += min((now - last) / 1e6f, 64f) / 1000f
            last = now
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val paints = remember { RecordPaints() }
    val clock = "%02d:%02d".format(seconds / 60, seconds % 60)

    Box(
        modifier = modifier
            .size(SPHERE.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onClick()
                if (phase != RecordPhase.Done) {
                    val next = phase != RecordPhase.Recording
                    onChange(next)
                    if (recording == null) {
                        if (next) seconds = 0
                        phase = if (next) RecordPhase.Recording else RecordPhase.Done
                    }
                }
            }
            .semantics {
                this.contentDescription = contentDescription
                selected = phase == RecordPhase.Recording
                if (phase != RecordPhase.Idle) stateDescription = clock
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    translate(x + w / 2, y + h / 2) {
                        drawRecord(
                            paints,
                            e = expand.value,
                            red = red.value,
                            green = green.value,
                            mic = mic.value,
                            pulse = 0.5f + 0.5f * sin(TAU * PULSE_HZ * time.floatValue),
                        )
                    }
                }
            }
        }
        // The timer columns slide out from behind the sphere once the pill is mostly open.
        TimerColumn(pad(seconds / 60), minutesLabel, side = -1f, expand = { expand.value })
        TimerColumn(pad(seconds % 60), secondsLabel, side = 1f, expand = { expand.value })
    }
}

private fun pad(n: Int) = "%02d".format(n)

@Composable
private fun TimerColumn(digits: String, unit: String, side: Float, expand: () -> Float) {
    Column(
        modifier = Modifier
            .wrapContentSize(unbounded = true)
            // Roboto's line box puts the digits a touch lower than SF does; the lift matches iOS.
            .offset(x = (COLUMN * side).dp, y = (-1.2).dp)
            .graphicsLayer {
                val e = expand()
                alpha = ((e - 0.45f) / 0.55f).coerceIn(0f, 1f)
                translationX = -side * 22.dp.toPx() * (1f - e)
            }
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text = digits, style = DigitStyle, maxLines = 1)
        BasicText(text = unit, style = UnitStyle, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
    }
}

/** Draws everything around the origin, which is the centre of the sphere. */
private fun DrawScope.drawRecord(p: RecordPaints, e: Float, red: Float, green: Float, mic: Float, pulse: Float) {
    val radius = (SPHERE + (SPHERE_RECORDING - SPHERE) * e) / 2

    // Faint reflection on the floor below, tinted by whatever the sphere is doing.
    translate(0f, REFLECTION_DROP) {
        scale((1f + 1.6f * e), 20f / 26f, pivot = Offset.Zero) {
            drawCircle(p.floor, 26f, Offset.Zero)
            drawCircle(p.redFloor, 26f, Offset.Zero, alpha = red)
            drawCircle(p.greenFloor, 26f, Offset.Zero, alpha = green)
        }
    }

    // The pill grows out from behind the sphere.
    val pillWidth = SPHERE + (PILL_WIDTH - SPHERE) * e
    val pillOpacity = min(1f, 4f * e)
    if (pillOpacity > 0f) {
        val topLeft = Offset(-pillWidth / 2, -PILL_HEIGHT / 2)
        val size = Size(pillWidth, PILL_HEIGHT)
        val corner = CornerRadius(PILL_HEIGHT / 2)
        drawRoundRect(Color(0xFF161415), topLeft, size, corner, alpha = pillOpacity)
        drawRoundRect(p.pillTint, topLeft, size, corner, alpha = pillOpacity * red * e)
        drawRoundRect(p.pillEdge, topLeft, size, corner, alpha = pillOpacity, style = Stroke(1f))
    }

    val glow = radius + 4f + 3f * pulse * red
    drawIntoCanvas {
        p.redGlow.draw(it.nativeCanvas, -glow, -glow, glow * 2, glow * 2, glow, alpha = red * (0.7f + 0.3f * pulse))
        p.greenGlow.draw(it.nativeCanvas, -glow, -glow, glow * 2, glow * 2, glow, alpha = green)
    }

    drawCircle(p.greyGlass, radius, Offset.Zero)
    drawCircle(p.redGlass, radius, Offset.Zero, alpha = red)
    drawCircle(p.greenGlass, radius, Offset.Zero, alpha = green)
    drawCircle(p.rim, radius, Offset.Zero, style = Stroke(1f))
    drawCircle(rgba(255, 176, 176, 0.9f), radius, Offset.Zero, alpha = red, style = Stroke(1f))
    drawCircle(rgba(228, 255, 170, 0.9f), radius, Offset.Zero, alpha = green, style = Stroke(1f))

    // Record dot.
    val dot = 1f - mic
    if (dot > 0f) {
        drawIntoCanvas { p.dotGlow.draw(it.nativeCanvas, -15f, -15f, 30f, 30f, 15f, alpha = dot) }
        drawCircle(Color(0xFFFF5357), 8f + 1.2f * pulse, Offset.Zero, alpha = dot)
    }

    // Microphone.
    if (mic > 0f) {
        drawIntoCanvas { p.micGlow.draw(it.nativeCanvas, -18f, -18f, 36f, 36f, 18f, alpha = mic) }
        scale(0.5f + 0.5f * mic, pivot = Offset.Zero) {
            drawRoundRect(MicInk, Offset(-9f, -21.6f), Size(18f, 32.4f), CornerRadius(9f), alpha = mic)
            drawPath(p.micLines, MicInk, alpha = mic, style = p.micStroke)
        }
    }
}
