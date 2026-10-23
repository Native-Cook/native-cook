package com.ngocdevv.nativecook.blocks.buttons.iridescenthomebutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.sweepStroke
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val HEIGHT = 64.dp
private val PAD_RIGHT = 26.dp

// Gap between the icon well and the label.
private val LABEL_GAP = 14.dp

// The canvas overflows the button by this much on every side to fit the flare's glow.
private val BLEED = 44.dp

// Thickness of the translucent band around the pill.
private const val BAND = 2.5f
private const val RING_R = 23.5f
private const val RING_WIDTH = 3.4f
private const val DISC_R = 20.5f
private const val GLOW_R = 38f

// Seconds per lap: the white flare runs clockwise, the rainbow film creeps the other way.
private const val FLARE_LAP = 5f
private const val FILM_LAP = 11f

// Where the clock is frozen when the system asks for reduced motion (flare at the upper right).
private const val STILL_TIME = 3.6f

// The flare is brightest when it passes this angle (upper right), as in the reference.
private const val PEAK_ANGLE = -0.6f
private const val FLARE_CENTER = 0.51f

private val Dark = rgba(8, 8, 10, 0f)
private val LabelStyle = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium)

private class IridescentPaints {
    // Thin-film colours scattered around the ring with dark gaps between them.
    val film = sweepStroke(
        0f to Dark,
        0.05f to Color(0xFF2B5CFF),
        0.09f to Color(0xFF58D6FF),
        0.13f to Color(0xFFFFF6D8),
        0.17f to Color(0xFFFFC93D),
        0.21f to Color(0xFFFF6A1A),
        0.26f to rgba(255, 45, 45, 0f),
        0.36f to Dark,
        0.42f to Color(0xFF7A3CFF),
        0.46f to Color(0xFF2B8CFF),
        0.5f to Color(0xFF7FF0E0),
        0.55f to rgba(127, 240, 224, 0f),
        0.64f to Dark,
        0.7f to Color(0xFFFF8A2A),
        0.74f to Color(0xFFFFE27A),
        0.78f to Color.White,
        0.83f to Color(0xFF5AD0FF),
        0.88f to Color(0xFF2F54FF),
        0.94f to Dark,
        1f to Dark,
    )

    // The flare: blue tail, white body, warm head. Centred on half a turn.
    val flare = sweepStroke(
        0f to rgba(58, 107, 255, 0f),
        0.28f to rgba(58, 107, 255, 0f),
        0.34f to Color(0xFF3A6BFF),
        0.4f to Color(0xFF9FE8FF),
        0.46f to Color.White,
        0.56f to Color.White,
        0.62f to Color(0xFFFFE9A8),
        0.67f to Color(0xFFFF8A2A),
        0.72f to rgba(255, 60, 40, 0f),
        1f to rgba(255, 60, 40, 0f),
    )
    val bloom = sweepStroke(
        0f to rgba(255, 244, 228, 0f),
        0.36f to rgba(255, 244, 228, 0f),
        0.46f to rgba(255, 244, 228, 0.95f),
        0.56f to rgba(255, 244, 228, 0.95f),
        0.68f to rgba(255, 244, 228, 0f),
        1f to rgba(255, 244, 228, 0f),
    )

    // Little house, centred on the origin: rounded roof tip, eaves, body and a door notch.
    val home = Path().apply {
        moveTo(-0.9f, -9.2f)
        quadraticTo(0f, -10f, 0.9f, -9.2f)
        lineTo(10.5f, -1f)
        quadraticTo(11.2f, 0f, 10f, 0f)
        lineTo(7.6f, 0f)
        lineTo(7.6f, 8f)
        quadraticTo(7.6f, 9.4f, 6.2f, 9.4f)
        lineTo(3.1f, 9.4f)
        quadraticTo(2.4f, 9.4f, 2.4f, 8.7f)
        lineTo(2.4f, 3.6f)
        lineTo(-2.4f, 3.6f)
        lineTo(-2.4f, 8.7f)
        quadraticTo(-2.4f, 9.4f, -3.1f, 9.4f)
        lineTo(-6.2f, 9.4f)
        quadraticTo(-7.6f, 9.4f, -7.6f, 8f)
        lineTo(-7.6f, 0f)
        lineTo(-10f, 0f)
        quadraticTo(-11.2f, 0f, -10.5f, -1f)
        close()
    }
}

// Brightest at the upper right, dimmest on the opposite side; a press keeps it up.
private fun flareStrength(time: Float, energy: Float): Float {
    val breath = 0.5f + 0.5f * cos(TAU * time / FLARE_LAP - PEAK_ANGLE)
    return (0.45f + 0.55f * energy) * (0.3f + 0.7f * breath)
}

/**
 * Dark glass "Home" pill with an icon well whose rim carries an iridescent film and a circling
 * white flare.
 *
 * @param autoPlay Keeps the flare at full strength. When off the ring idles dimmer and flares up while pressed.
 */
@Composable
fun IridescentHomeButton(
    label: String = "Home",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

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

    // Seconds, wrapped at 55 s: both laps divide it, so the wrap is seamless.
    val time = remember { mutableFloatStateOf(STILL_TIME) }
    // 0 = idling, 1 = full flare.
    val energy = remember { Animatable(0f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time.floatValue = (time.floatValue + min((now - last) / 1e6f, 64f) / 1000f) % (FLARE_LAP * FILM_LAP)
            last = now
        }
    }

    val lit = autoPlay || pressed
    LaunchedEffect(lit) {
        // Rises quickly, and fades slowly after release so the flare isn't hidden under the thumb.
        energy.animateTo(if (lit) 1f else 0f, tween(if (lit) 380 else 1400, easing = EaseOutStrong))
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val paints = remember { IridescentPaints() }
    val bloom = rememberBloom(5.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h -> drawBody(paints, x, y, w, h, time.floatValue, energy.value) }
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = bloom
                    alpha = if (bloom == null) 0f else flareStrength(time.floatValue, energy.value)
                },
            ) {
                surface(BLEED) { x, y, _, h ->
                    val cx = x + h / 2
                    val cy = y + h / 2
                    drawIntoCanvas {
                        paints.bloom.draw(
                            it.nativeCanvas, cx - RING_R, cy - RING_R, RING_R * 2, RING_R * 2, RING_R, 7f,
                            turns = time.floatValue / FLARE_LAP - FLARE_CENTER,
                        )
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, h ->
                    val cx = x + h / 2
                    val cy = y + h / 2
                    drawIntoCanvas {
                        paints.flare.draw(
                            it.nativeCanvas, cx - RING_R, cy - RING_R, RING_R * 2, RING_R * 2, RING_R, RING_WIDTH + 0.4f,
                            turns = time.floatValue / FLARE_LAP - FLARE_CENTER,
                            alpha = flareStrength(time.floatValue, energy.value),
                        )
                    }
                    drawWell(paints, cx, cy)
                }
            }
        }
        Box(
            Modifier
                .height(HEIGHT)
                // The icon well fills the left cap.
                .padding(start = HEIGHT + LABEL_GAP - 6.dp, end = PAD_RIGHT)
                .wrapContentHeight(),
        ) {
            BasicText(text = label, style = LabelStyle, maxLines = 1)
        }
    }
}

private fun DrawScope.drawBody(p: IridescentPaints, x: Float, y: Float, w: Float, h: Float, time: Float, energy: Float) {
    val r = h / 2
    // The well shares the pill's left cap.
    val cx = x + r
    val cy = y + r
    val flareAngle = TAU * time / FLARE_LAP
    val flare = flareStrength(time, energy)

    drawRoundRect(rgba(40, 40, 44, 0.72f), Offset(x, y), Size(w, h), CornerRadius(r))
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF1E1E20), Color(0xFF141416)), startY = y, endY = y + h),
        topLeft = Offset(x + BAND, y + BAND),
        size = Size(w - BAND * 2, h - BAND * 2),
        cornerRadius = CornerRadius(r - BAND),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(rgba(255, 255, 255, 0.1f), rgba(255, 255, 255, 0.02f), rgba(255, 255, 255, 0.05f)),
            startY = y,
            endY = y + h,
        ),
        topLeft = Offset(x + BAND + 0.5f, y + BAND + 0.5f),
        size = Size(w - BAND * 2 - 1, h - BAND * 2 - 1),
        cornerRadius = CornerRadius(r - BAND - 0.5f),
        style = Stroke(1f),
    )

    // The groove the ring sits in.
    drawCircle(Color(0xFF060607), RING_R + RING_WIDTH / 2 + 0.8f, Offset(cx, cy))

    // Warm light spilling from the flare onto the pill and the background.
    val glowAt = Offset(cx + RING_R * cos(flareAngle), cy + RING_R * sin(flareAngle))
    drawCircle(
        brush = Brush.radialGradient(
            0f to rgba(255, 236, 206, 0.7f),
            0.45f to rgba(255, 200, 150, 0.2f),
            1f to rgba(255, 170, 110, 0f),
            center = glowAt,
            radius = GLOW_R,
        ),
        radius = GLOW_R,
        center = glowAt,
        alpha = flare * 0.8f,
    )

    // Rainbow film: a slightly squashed ring turning with its gradient reads as a liquid wobble.
    rotate(-360f * time / FILM_LAP, pivot = Offset(cx, cy)) {
        drawIntoCanvas {
            p.film.draw(
                it.nativeCanvas, cx - RING_R - 0.7f, cy - RING_R + 0.7f, (RING_R + 0.7f) * 2, (RING_R - 0.7f) * 2,
                RING_R + 0.7f, RING_WIDTH,
                turns = 0f,
                alpha = 0.6f + 0.3f * energy,
            )
        }
    }
}

// Glass disc holding the icon.
private fun DrawScope.drawWell(p: IridescentPaints, cx: Float, cy: Float) {
    val k = DISC_R * 0.7f
    drawCircle(
        brush = Brush.linearGradient(
            listOf(Color(0xFF38383B), Color(0xFF232325), Color(0xFF19191B)),
            start = Offset(cx - k, cy - k),
            end = Offset(cx + k, cy + k),
        ),
        radius = DISC_R,
        center = Offset(cx, cy),
    )
    drawCircle(rgba(255, 255, 255, 0.07f), DISC_R - 0.5f, Offset(cx, cy), style = Stroke(1f))
    translate(cx, cy - 0.5f) {
        drawPath(
            p.home,
            Brush.linearGradient(
                0f to Color(0xFFFBFBFB),
                0.45f to Color(0xFFCFCFD1),
                0.55f to Color(0xFFF0F0F0),
                1f to Color(0xFFB2B2B5),
                start = Offset(-8f, -10f),
                end = Offset(8f, 10f),
            ),
        )
    }
}
