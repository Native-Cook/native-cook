package com.ngocdevv.nativecook.blocks.buttons.glowborderbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.sweepStroke
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class GlowBorderMode {
    /** The light runs while pressed/hovered, then finishes its lap and settles back into the top highlight. */
    Press,

    /** The light never stops. */
    Always,
}

private val HEIGHT = 54.dp
private val PAD_X = 38.dp

// The canvas overflows the button by this much on every side to fit the bloom and the shadow.
private val BLEED = 36.dp
private const val LAP_MS = 2200

// How far (in laps) the light must still travel after release, so the run-out isn't hidden under the thumb.
private const val MIN_RUNOUT = 0.5f

// Starts at 1.25× average speed and lands with zero velocity: paired with a 1.25× duration it
// picks up the linear loop without a speed jump.
private val EaseSettle = CubicBezierEasing(0.4f, 0.5f, 0.6f, 1f)
private val EaseInOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private val ClearWhite = rgba(255, 255, 255, 0f)
private val ClearBlue = rgba(48, 32, 255, 0f)
private val Blue = Color(0xFF3A26FF)
private val Violet = Color(0xFFB266FF)
private val Magenta = Color(0xFFEA3DFF)

// The coloured head leads the white highlight by about a third of a lap.
private const val COMET_OFFSET = 0.75f

private val LabelStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)

private class GlowBorderPaints {
    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.55f), sigma = 14f)

    // Sweep stops run clockwise from 3 o'clock, so 0.75 is the top of the button.
    val highlight = sweepStroke(
        0f to ClearBlue,
        0.55f to ClearBlue,
        0.585f to Blue,
        0.615f to Violet,
        0.645f to Color.White,
        0.855f to Color.White,
        0.885f to Violet,
        0.915f to Blue,
        0.95f to ClearBlue,
        1f to ClearBlue,
    )
    val highlightBloom = sweepStroke(
        0f to ClearWhite,
        0.63f to ClearWhite,
        0.69f to rgba(255, 255, 255, 0.85f),
        0.81f to rgba(255, 255, 255, 0.85f),
        0.87f to ClearWhite,
        1f to ClearWhite,
    )
    val comet = cometStroke()
    val cometBloom = cometStroke()

    private fun cometStroke() = sweepStroke(
        0f to ClearBlue,
        0.08f to rgba(48, 32, 255, 0.6f),
        0.24f to Blue,
        0.3f to Magenta,
        0.37f to Magenta,
        0.43f to Color(0xFF4A2CFF),
        0.5f to ClearBlue,
        1f to ClearBlue,
    )
}

/**
 * "Let me glow" button: a black pill with a white highlight resting on its top edge and a
 * blue → magenta light that runs clockwise around the border.
 */
@Composable
fun GlowBorderButton(
    label: String = "Let me glow",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    mode: GlowBorderMode = GlowBorderMode.Press,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
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

    val running = mode == GlowBorderMode.Always || pressed || hovered

    // Measured in laps; whole numbers put the white highlight back at the top.
    val angle = remember { Animatable(0f) }
    val comet = remember { Animatable(0f) }

    LaunchedEffect(running, reducedMotion) {
        if (reducedMotion) {
            comet.animateTo(if (running) 1f else 0f, tween(200))
            return@LaunchedEffect
        }
        // Keep the value small: only the fractional part matters, and floats lose precision as it grows.
        angle.snapTo(angle.value % 1f)
        val from = angle.value
        if (running) {
            launch { comet.animateTo(1f, tween(350)) }
            angle.animateTo(
                from + 1f,
                infiniteRepeatable(tween(LAP_MS, easing = LinearEasing), RepeatMode.Restart),
            )
        } else if (from != 0f || comet.value != 0f) {
            var runout = ceil(from) - from
            if (runout < MIN_RUNOUT) runout += 1f
            val duration = (1.25f * runout * LAP_MS).roundToInt()
            launch { comet.animateTo(0f, tween(duration, easing = EaseInOutQuad)) }
            angle.animateTo(from + runout, tween(duration, easing = EaseSettle))
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val paints = remember { GlowBorderPaints() }
    val cometBloom = rememberBloom(4.5.dp)
    val highlightBloom = rememberBloom(5.dp)

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
                surface(BLEED) { x, y, w, h ->
                    val r = h / 2
                    drawIntoCanvas { paints.shadow.draw(it.nativeCanvas, x, y + 6, w, h, r) }
                    drawRoundRect(Color.Black, Offset(x, y), Size(w, h), CornerRadius(r))
                    drawRoundRect(
                        color = rgba(255, 255, 255, 0.035f),
                        topLeft = Offset(x + 0.5f, y + 0.5f),
                        size = Size(w - 1, h - 1),
                        cornerRadius = CornerRadius(r - 0.5f),
                        style = Stroke(1f),
                    )
                }
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = cometBloom
                    alpha = if (cometBloom == null) 0f else comet.value
                },
            ) {
                surface(BLEED) { x, y, w, h ->
                    drawIntoCanvas {
                        paints.cometBloom.draw(it.nativeCanvas, x, y, w, h, h / 2, 4f, turns = angle.value + COMET_OFFSET)
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    drawIntoCanvas {
                        paints.comet.draw(
                            it.nativeCanvas, x + 1, y + 1, w - 2, h - 2, h / 2 - 1, 2f,
                            turns = angle.value + COMET_OFFSET,
                            alpha = comet.value,
                        )
                    }
                }
            }
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = highlightBloom
                    alpha = if (highlightBloom == null) 0f else 1f
                },
            ) {
                surface(BLEED) { x, y, w, h ->
                    drawIntoCanvas {
                        paints.highlightBloom.draw(it.nativeCanvas, x, y, w, h, h / 2, 3f, turns = angle.value)
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    drawIntoCanvas {
                        paints.highlight.draw(it.nativeCanvas, x + 1, y + 1, w - 2, h - 2, h / 2 - 1, 2f, turns = angle.value)
                    }
                }
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            BasicText(text = label, style = LabelStyle, maxLines = 1)
        }
    }
}
