package com.ngocdevv.nativecook.blocks.buttons.applycheckbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HEIGHT = 50.dp
private val PAD_X = 46.dp

// The canvas overflows the button by this much on every side to fit the glow.
private val BLEED = 28.dp

// Gap that opens between the pill and its glow when it is clicked.
private const val RING = 3.4f
private const val HOLD_MS = 2000L
private const val AUTOPLAY_PAUSE_MS = 1400L

private val Lime = Color(0xFFBAEE33)
private val Charcoal = Color(0xFF222522)
private val RingColor = Color(0xFFF1F4EE)
private val LabelStyle = TextStyle(color = Color(0xFF1C2118), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

private class ApplyCheckMotion {
    val glow = Animatable(0f)
    val ring = Animatable(0f)
    val done = Animatable(0f)
    val check = Animatable(0f)
    var busy = false

    val halo = SoftRoundRect(rgba(186, 238, 51, 0.8f), sigma = 7f)
    val mark = Path()

    /** One confirmation. [hover] first lets the glow sit for a moment, like the reference's cursor arriving. */
    suspend fun confirm(hover: Boolean, reducedMotion: Boolean) {
        if (busy) return
        busy = true
        // Reduced motion keeps the colour change and the check as plain fades.
        val quick = if (reducedMotion) 0f else 1f
        try {
            coroutineScope {
                launch { glow.animateTo(quick, tween(if (hover) 400 else 300, easing = EaseOutStrong)) }
                if (hover) delay(900)
                launch { ring.animateTo(quick, tween(300, easing = EaseOutStrong)) }
                delay(if (hover) 420 else 380)

                launch { done.animateTo(1f, tween(220)) }
                launch { check.animateTo(1f, tween(320, delayMillis = 80, easing = EaseOutStrong)) }
                launch { glow.animateTo(0f, tween(260)) }
                launch { ring.animateTo(0f, tween(260)) }
                delay(HOLD_MS)

                launch { check.animateTo(0f, tween(140)) }
                delay(100)
                launch { done.animateTo(0f, tween(320)) }
                delay(420)
            }
        } finally {
            // If the run was cancelled half-way, drop back to the idle state.
            withContext(NonCancellable) {
                glow.snapTo(0f)
                ring.snapTo(0f)
                done.snapTo(0f)
                check.snapTo(0f)
            }
            busy = false
        }
    }
}

/**
 * Lime "Apply" pill. A tap lights its glow, opens a pale ring around it, then turns it charcoal
 * with a white check mark before it settles back.
 *
 * @param autoPlay Replays the confirmation by itself with a pause between runs.
 */
@Composable
fun ApplyCheckButton(
    label: String = "Apply",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { ApplyCheckMotion() }
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
            motion.confirm(hover = true, reducedMotion = still)
        }
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
                scope.launch { motion.confirm(hover = false, reducedMotion = still) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, h ->
                    val r = h / 2
                    val cx = x + w / 2
                    val cy = y + h / 2
                    val ring = motion.ring.value
                    val check = motion.check.value

                    // The glow sits just outside the pill and is pushed further out by the ring.
                    val reach = 1f + RING * ring
                    drawIntoCanvas {
                        motion.halo.draw(
                            it.nativeCanvas, x - reach, y - reach, w + reach * 2, h + reach * 2, r + reach,
                            alpha = motion.glow.value,
                        )
                    }
                    val ringWidth = RING * ring
                    if (ringWidth > 0f) {
                        drawRoundRect(
                            color = RingColor,
                            topLeft = Offset(x - ringWidth / 2, y - ringWidth / 2),
                            size = Size(w + ringWidth, h + ringWidth),
                            cornerRadius = CornerRadius(r + ringWidth / 2),
                            style = Stroke(ringWidth),
                            alpha = ring,
                        )
                    }

                    drawRoundRect(Lime, Offset(x, y), Size(w, h), CornerRadius(r))
                    drawRoundRect(Charcoal, Offset(x, y), Size(w, h), CornerRadius(r), alpha = motion.done.value)

                    if (check > 0f) {
                        motion.mark.reset()
                        motion.mark.moveTo(cx - 9.5f, cy + 0.5f)
                        motion.mark.lineTo(cx - 3.5f, cy + 6.5f)
                        motion.mark.lineTo(cx + 10f, cy - 7.5f)
                        scale(0.4f + 0.6f * check, pivot = Offset(cx, cy)) {
                            drawPath(
                                motion.mark,
                                Color.White,
                                alpha = check.coerceIn(0f, 1f),
                                style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            BasicText(
                text = label,
                style = LabelStyle,
                maxLines = 1,
                // The label makes way for the check slightly ahead of the colour change.
                modifier = Modifier.graphicsLayer { alpha = 1f - min(1f, motion.done.value * 1.6f) },
            )
        }
    }
}
