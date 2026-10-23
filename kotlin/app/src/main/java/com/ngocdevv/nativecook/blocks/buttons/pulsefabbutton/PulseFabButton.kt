package com.ngocdevv.nativecook.blocks.buttons.pulsefabbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.bleed
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.launch

private val SIZE = 56.dp

// The canvas overflows the button by this much on every side to fit the rings.
private val BLEED = 24.dp
private const val PULSE_MS = 2000

// The ring is gone after this share of the cycle; the rest is a pause.
private const val PULSE_ACTIVE = 0.7f

// How far the ambient ring and the tap ring travel beyond the button's edge.
private const val PULSE_SPREAD = 10f
private const val BURST_SPREAD = 18f
private const val BURST_MS = 650

private val Yellow = Color(0xFFFFBD0B)
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * Round yellow "+" action button with a ring that keeps pulsing outward from it. A tap sends out
 * one larger ring of its own.
 *
 * @param label The button has no text, so this is what TalkBack announces.
 * @param autoPlay The pulse is ambient and runs either way; the flag only exists so every gallery
 *   button shares the same API.
 */
@Composable
fun PulseFabButton(
    label: String = "Add",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }

    // Position in the pulse cycle, 0…1.
    val clock = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }

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

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        clock.snapTo(0f)
        clock.animateTo(1f, infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart))
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onClick()
                scope.launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(BURST_MS, easing = EaseOutCubic))
                }
            }
            .semantics { contentDescription = label }
            .size(SIZE),
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    val c = Offset(x + w / 2, y + h / 2)
                    val r = w / 2

                    val b = burst.value
                    if (b < 1f) {
                        drawCircle(Yellow, r + BURST_SPREAD * (if (reducedMotion) 0.6f else b), c, alpha = 0.55f * (1f - b))
                    }
                    if (reducedMotion) {
                        // A faint resting halo stands in for the travelling ring.
                        drawCircle(Yellow, r + PULSE_SPREAD * 0.6f, c, alpha = 0.28f)
                    } else {
                        // The ring leaves quickly and slows down, staying strong until it is most of the way out.
                        val u = min(clock.value / PULSE_ACTIVE, 1f)
                        drawCircle(Yellow, r + PULSE_SPREAD * (1f - (1f - u) * (1f - u)), c, alpha = 0.5f * (1f - u * u))
                    }
                    drawCircle(Yellow, r, c)

                    drawLine(Color.White, Offset(c.x - 11.5f, c.y), Offset(c.x + 11.5f, c.y), 2.4f, StrokeCap.Round)
                    drawLine(Color.White, Offset(c.x, c.y - 11.5f), Offset(c.x, c.y + 11.5f), 2.4f, StrokeCap.Round)
                }
            }
        }
    }
}
