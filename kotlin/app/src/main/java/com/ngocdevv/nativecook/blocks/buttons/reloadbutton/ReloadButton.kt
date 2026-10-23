package com.ngocdevv.nativecook.blocks.buttons.reloadbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// All in dp, the same numbers as the React Native and SwiftUI versions.
private const val HEIGHT = 56f
private const val RADIUS = 7f
private const val PAD_LEFT = 26f
private const val ICON = 24f
private const val GAP = 18f
private const val PAD_RIGHT = 32f

// Width of the square the button collapses to while it works.
private const val COLLAPSED = 60f
private const val SPIN_TURNS = 2f
private const val SPIN_MS = 1300
private const val AUTOPLAY_PAUSE_MS = 1500L

private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val EaseSpin = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

private val FaceBlue = Color(0xFFC7DFF3)
private val FaceGreen = Color(0xFFCBF8D0)
private val IconBlue = Color(0xFF2E3CCB)
private val IconGreen = Color(0xFF42C258)
private val LabelBlue = Color(0xFF3A63B7)
private val LabelGreen = Color(0xFF46C357)
private val LabelStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

private class ReloadMotion {
    /** 1 = full width with the label, 0 = collapsed to the icon. */
    val open = Animatable(1f)

    /** Icon rotation in turns. */
    val spin = Animatable(0f)

    /** 0 = blue, 1 = green (done). */
    val ok = Animatable(0f)

    /** A small swell before the collapse, like the reference's wind-up. */
    val swell = Animatable(1f)
    var busy = false

    val face = Path()

    // Two arcs (radius 9) chasing each other clockwise, each ending in an arrowhead. Centred on the origin.
    val arcs = Path().apply {
        val circle = Rect(-9f, -9f, 9f, 9f)
        arcTo(circle, 215f, 135f, forceMoveTo = true)
        arcTo(circle, 35f, 135f, forceMoveTo = true)
    }
    val heads = Path().apply {
        moveTo(5.8f, -1.6f)
        lineTo(12.2f, -1.6f)
        lineTo(9f, 2.4f)
        close()
        moveTo(-12.2f, 1.6f)
        lineTo(-5.8f, 1.6f)
        lineTo(-9f, -2.4f)
        close()
    }

    suspend fun reload(reducedMotion: Boolean) {
        if (busy) return
        busy = true
        try {
            coroutineScope {
                if (reducedMotion) {
                    // No collapse or spin: just the colour change.
                    delay(500)
                    launch { ok.animateTo(1f, tween(300)) }
                    delay(1600)
                    launch { ok.animateTo(0f, tween(450)) }
                    delay(450)
                    return@coroutineScope
                }

                launch { swell.animateTo(1.05f, tween(200, easing = EaseOutStrong)) }
                delay(200)
                launch { swell.animateTo(1f, tween(420, easing = EaseInOut)) }
                launch { open.animateTo(0f, tween(420, easing = EaseInOut)) }
                delay(500)

                // Whole turns, so the icon comes to rest exactly as it started.
                spin.snapTo(0f)
                launch { spin.animateTo(SPIN_TURNS, tween(SPIN_MS, easing = EaseSpin)) }
                delay(SPIN_MS - 250L)
                launch { ok.animateTo(1f, tween(300)) }
                delay(950)

                launch { open.animateTo(1f, tween(460, easing = EaseInOut)) }
                delay(460L + 1100L)
                launch { ok.animateTo(0f, tween(450)) }
                delay(450)
            }
        } finally {
            // If the run was cancelled half-way, drop back to the idle state.
            withContext(NonCancellable) {
                open.snapTo(1f)
                spin.snapTo(0f)
                ok.snapTo(0f)
                swell.snapTo(1f)
            }
            busy = false
        }
    }
}

/**
 * Pale blue "Reload" button. A tap collapses it to a square around its refresh icon, the icon
 * spins while it works, turns green when done, and the button opens back up before returning to blue.
 *
 * @param autoPlay Replays the reload by itself with a pause between runs.
 */
@Composable
fun ReloadButton(
    label: String = "Reload",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { ReloadMotion() }
    val still by rememberUpdatedState(reducedMotion)

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

    LaunchedEffect(autoPlay) {
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            delay(AUTOPLAY_PAUSE_MS)
            motion.reload(still)
        }
    }

    val press by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    // The label is drawn in the canvas so it can be clipped by the shrinking face.
    val measurer = rememberTextMeasurer()
    val text = remember(label, measurer) { measurer.measure(label, LabelStyle, maxLines = 1) }
    val labelWidth = with(LocalDensity.current) { text.size.width.toDp() }

    Canvas(
        modifier
            .size(width = (PAD_LEFT + ICON + GAP + PAD_RIGHT).dp + labelWidth, height = HEIGHT.dp)
            .graphicsLayer {
                val s = press * motion.swell.value
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onClick()
                scope.launch { motion.reload(still) }
            }
            .semantics { contentDescription = label },
    ) {
        val open = motion.open.value
        val ok = motion.ok.value
        // Everything below is in dp except the text, which is already laid out in pixels.
        val width = size.width / density
        val w = COLLAPSED + (width - COLLAPSED) * open
        val x = (width - w) / 2

        scale(density, density, pivot = Offset.Zero) {
            drawRoundRect(FaceBlue, Offset(x, 0f), Size(w, HEIGHT), CornerRadius(RADIUS))
            drawRoundRect(FaceGreen, Offset(x, 0f), Size(w, HEIGHT), CornerRadius(RADIUS), alpha = ok)
        }

        // The label leaves upwards as the button closes and drops back in as it opens,
        // clipped by the shrinking face.
        motion.face.reset()
        motion.face.addRoundRect(
            RoundRect(x * density, 0f, (x + w) * density, size.height, CornerRadius(RADIUS * density)),
        )
        clipPath(motion.face) {
            drawText(
                text,
                color = lerp(LabelBlue, LabelGreen, ok),
                topLeft = Offset(
                    (x + PAD_LEFT + ICON + GAP) * density,
                    (size.height - text.size.height) / 2 - (1 - open) * size.height * 0.55f,
                ),
                alpha = (open * open).coerceIn(0f, 1f),
            )
        }

        // The icon sits in the leading padding and drifts to the middle of the square.
        scale(density, density, pivot = Offset.Zero) {
            translate(x + COLLAPSED / 2 + (PAD_LEFT + ICON / 2 - COLLAPSED / 2) * open, HEIGHT / 2) {
                rotate(360f * motion.spin.value, pivot = Offset.Zero) {
                    val color = lerp(IconBlue, IconGreen, ok)
                    drawPath(motion.arcs, color, style = Stroke(2f, cap = StrokeCap.Round))
                    drawPath(motion.heads, color)
                }
            }
        }
    }
}
