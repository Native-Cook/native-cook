package com.ngocdevv.nativecook.blocks.buttons.tiltbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fixed size: the tilt is measured against it.
private val WIDTH = 190.dp
private val HEIGHT = 58.dp
private const val MAX_TILT_DEG = 22f
private const val PERSPECTIVE = 520f

// The label floats this far above the pill, so it slides away from the raised side.
private val LIFT = 26.dp

// The pill grows a little while it is tilted, as in the reference's hover.
private const val GROW = 0.06f

// One lap of the imaginary pointer around the pill.
private const val LAP_MS = 3600
private const val AUTOPLAY_PAUSE_MS = 1300L
private const val AUTOPLAY_RAMP_MS = 600

// How far from the centre the imaginary pointer circles, 1 being the pill's edge.
private const val AUTOPLAY_REACH = 0.9f

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val Violet = Color(0xFF5D1BEE)
private val Blue = Color(0xFF12A4F1)
private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 21.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.5.sp,
)

private class TiltMotion(private val scope: CoroutineScope) {
    // Where the finger is, -1…1 on both axes from the pill's centre, and how much it counts.
    val touchX = Animatable(0f)
    val touchY = Animatable(0f)
    val press = Animatable(0f)

    // The imaginary pointer: its angle around the pill and how much it counts.
    val theta = Animatable(0f)
    val sway = Animatable(0f)

    fun track(position: Offset, width: Float, height: Float, fresh: Boolean) {
        val x = ((position.x - width / 2) / (width / 2)).coerceIn(-1f, 1f)
        val y = ((position.y - height / 2) / (height / 2)).coerceIn(-1f, 1f)
        scope.launch {
            // A fresh touch starts from where the finger landed.
            if (fresh) touchX.snapTo(x) else touchX.animateTo(x, tween(90, easing = EaseOutStrong))
        }
        scope.launch {
            if (fresh) touchY.snapTo(y) else touchY.animateTo(y, tween(90, easing = EaseOutStrong))
        }
    }

    // The pointer the pill reacts to: the finger while pressed, otherwise the imaginary one.
    val x: Float
        get() {
            val auto = cos(theta.value) * sway.value * AUTOPLAY_REACH
            return auto + (touchX.value - auto) * press.value
        }
    val y: Float
        get() {
            val auto = sin(theta.value) * sway.value * AUTOPLAY_REACH
            return auto + (touchY.value - auto) * press.value
        }
    val amount: Float get() = max(sway.value, press.value)
}

/** Tilts a layer so the side the pointer is on rises towards the viewer. */
private fun GraphicsLayerScope.tilt(motion: TiltMotion, scale: Float) {
    // Positive rotationX brings the bottom edge forward, positive rotationY sends the right edge back.
    rotationX = MAX_TILT_DEG * motion.y
    rotationY = -MAX_TILT_DEG * motion.x
    // The camera distance is counted in 72nds of the depth in pixels.
    cameraDistance = PERSPECTIVE * density / 72f
    scaleX = scale
    scaleY = scale
}

/**
 * Violet-to-blue pill that tilts in 3D so the side under the finger rises towards you, with the
 * label floating above it. With [autoPlay] it sways through the same tilt by itself.
 *
 * @param autoPlay Sways through the tilt by itself, as if a pointer were circling over it, with
 *   rests in between.
 */
@Composable
fun TiltButton(
    label: String = "BUTTON",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val motion = remember { TiltMotion(scope) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    launch { motion.press.animateTo(1f, tween(220, easing = EaseOutStrong)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    launch { motion.press.animateTo(0f, tween(500, easing = EaseOutStrong)) }
            }
        }
    }

    LaunchedEffect(autoPlay, reducedMotion) {
        // Reduced motion: no swaying, and a press only dims the pill.
        if (!autoPlay || reducedMotion) return@LaunchedEffect
        launch {
            motion.theta.snapTo(0f)
            motion.theta.animateTo(
                (PI * 2).toFloat(),
                infiniteRepeatable(tween(LAP_MS, easing = LinearEasing), RepeatMode.Restart),
            )
        }
        try {
            while (true) {
                delay(AUTOPLAY_PAUSE_MS)
                motion.sway.animateTo(1f, tween(AUTOPLAY_RAMP_MS, easing = EaseInOutCubic))
                delay(LAP_MS.toLong())
                motion.sway.animateTo(0f, tween(AUTOPLAY_RAMP_MS, easing = EaseInOutCubic))
            }
        } finally {
            scope.launch { motion.sway.animateTo(0f, tween(AUTOPLAY_RAMP_MS)) }
        }
    }

    Box(
        modifier = modifier
            // Only reads where the finger is; `clickable` below still handles the tap.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    motion.track(down.position, size.width.toFloat(), size.height.toFloat(), fresh = true)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        motion.track(change.position, size.width.toFloat(), size.height.toFloat(), fresh = false)
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .size(WIDTH, HEIGHT),
    ) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                if (reducedMotion) {
                    alpha = 1f - 0.18f * motion.press.value
                } else {
                    tilt(motion, 1f + GROW * motion.amount)
                }
            },
        ) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Violet, Blue),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                ),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (reducedMotion) return@graphicsLayer
                    val amount = motion.amount
                    tilt(motion, (1f + GROW * amount) * (1f + LIFT.toPx() / (PERSPECTIVE * density) * amount))
                    // Standing in for a z translation: the lifted label shifts along the pill's tilted normal.
                    val toRadians = MAX_TILT_DEG * PI.toFloat() / 180f
                    translationX = -LIFT.toPx() * sin(toRadians * motion.x)
                    translationY = -LIFT.toPx() * sin(toRadians * motion.y)
                }
                // The button's own semantics carry the label.
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text = label, style = LabelStyle, maxLines = 1)
        }
    }
}
