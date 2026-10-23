package com.ngocdevv.nativecook.blocks.buttons.gradientsubscribebutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val HEIGHT = 54.dp
private val PAD_X = 40.dp

// The canvas overflows the button by this much on every side to fit the glow.
private val BLEED = 44.dp

// One full trip through the gradient, which is four button-widths long.
private const val CYCLE_MS = 3300
private const val SPAN = 4f

// How far (in cycles) the gradient must still travel after release, so the run-out isn't hidden under the thumb.
private const val MIN_RUNOUT = 0.35f
private val GLOW_BLUR = 13.dp
private const val GLOW_OPACITY = 0.78f

// Starts at 1.25× average speed and lands with zero velocity: paired with a 1.25× duration it
// picks up the linear loop without a speed jump.
private val EaseSettle = CubicBezierEasing(0.4f, 0.5f, 0.6f, 1f)
private val EaseInQuad = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)

// Magenta → crimson → orange → back to magenta, tiled so the drift never shows a seam.
private val Magenta = Color(0xFFE830C8)
private val Crimson = Color(0xFFD8252F)
private val Orange = Color(0xFFFF9A10)

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 15.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 2.6.sp,
)

// The gradient slides left, so the colours travel from the right edge to the left one.
private fun DrawScope.driftPill(x: Float, y: Float, w: Float, h: Float, phase: Float) {
    val cycle = SPAN * w
    val startX = x - (phase % 1f) * cycle
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Magenta,
            1f / 3f to Crimson,
            2f / 3f to Orange,
            1f to Magenta,
            start = Offset(startX, y + h / 2),
            end = Offset(startX + cycle, y + h / 2),
            tileMode = TileMode.Repeated,
        ),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2),
    )
}

/**
 * "SUBSCRIBE" pill whose magenta → crimson → orange gradient drifts sideways under a matching glow.
 *
 * @param autoPlay Keep the gradient drifting and the glow lit all the time. When off, the button
 * rests on its magenta face and only drifts while pressed/hovered, then finishes its cycle and settles.
 */
@Composable
fun GradientSubscribeButton(
    label: String = "SUBSCRIBE",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
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

    val running = autoPlay || pressed || hovered

    // Measured in cycles; whole numbers put the magenta face back under the label.
    val phase = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(running, reducedMotion) {
        if (reducedMotion) {
            glow.animateTo(if (running) 1f else 0f, tween(200))
            return@LaunchedEffect
        }
        // Keep the value small: only the fractional part matters, and floats lose precision as it grows.
        phase.snapTo(phase.value % 1f)
        val from = phase.value
        if (running) {
            launch { glow.animateTo(1f, tween(350)) }
            phase.animateTo(
                from + 1f,
                infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            )
        } else if (from != 0f || glow.value != 0f) {
            var runout = ceil(from) - from
            if (runout < MIN_RUNOUT) runout += 1f
            val duration = (1.25f * runout * CYCLE_MS).roundToInt()
            launch { glow.animateTo(0f, tween(duration, easing = EaseInQuad)) }
            phase.animateTo(from + runout, tween(duration, easing = EaseSettle))
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val bloom = rememberBloom(GLOW_BLUR)

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
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = bloom
                    alpha = if (bloom == null) 0f else glow.value * GLOW_OPACITY
                },
            ) {
                surface(BLEED) { x, y, w, h -> driftPill(x, y, w, h, phase.value) }
            }
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h -> driftPill(x, y, w, h, phase.value) }
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            BasicText(text = label, style = LabelStyle, maxLines = 1)
        }
    }
}
