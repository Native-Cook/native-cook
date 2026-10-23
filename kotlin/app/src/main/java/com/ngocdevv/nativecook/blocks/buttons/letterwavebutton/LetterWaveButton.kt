package com.ngocdevv.nativecook.blocks.buttons.letterwavebutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HEIGHT = 56.dp
private val PAD_X = 34.dp
private const val RADIUS = 2f

// Extra room between letters; each one is its own node so it can hop by itself.
private val SPACING = 2.6.dp

// The colours sweep in with a round leading edge this big, relative to the button's height.
private const val EDGE_RADIUS = 0.6f
private const val SWEEP_MS = 190
private const val SWEEP_GAP_MS = 110

// Each letter swaps for a fresh copy over LETTER_MS, starting STAGGER_MS after the one before.
private const val LETTER_MS = 220
private const val STAGGER_MS = 100
private val RISE = 12.dp
private const val AUTOPLAY_PAUSE_MS = 1200L
private const val AUTOPLAY_HOLD_MS = 900L

private val Orange = Color(0xFFE57E32)
private val Navy = Color(0xFF1B1F65)
private val EaseOutQuad = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private val LetterStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

private class LetterWaveMotion {
    val orange = Animatable(0f)
    val navy = Animatable(0f)

    /** 0 → 1 as the hop travels through the label; it rests at 0. */
    val wave = Animatable(0f)

    /** 1: letters leave upwards (filling), -1: downwards (emptying). */
    var direction by mutableFloatStateOf(1f)
    var held = false
    var auto = false
    private var shown = false
    private var busy = false

    val clip = Path()

    /** Plays whole waves until the button shows what is wanted, so letters are never cut off mid-hop. */
    suspend fun sync(waveMs: Int, reducedMotion: Boolean) {
        if (busy) return
        busy = true
        try {
            while (shown != (held || auto)) {
                val on = !shown
                val sweep = tween<Float>(SWEEP_MS, easing = EaseOutQuad)
                val late = tween<Float>(SWEEP_MS, delayMillis = SWEEP_GAP_MS, easing = EaseOutQuad)
                coroutineScope {
                    launch { orange.animateTo(if (on) 1f else 0f, if (on) sweep else late) }
                    launch { navy.animateTo(if (on) 1f else 0f, if (on) late else sweep) }
                    // Reduced motion keeps the colour change as a fade and leaves the letters still.
                    if (!reducedMotion) {
                        direction = if (on) 1f else -1f
                        wave.snapTo(0f)
                        wave.animateTo(1f, tween(waveMs, easing = LinearEasing))
                        wave.snapTo(0f)
                    }
                    delay(40)
                }
                shown = on
            }
        } finally {
            busy = false
        }
    }
}

/**
 * Black "SWAGGER" slab. While pressed (or by itself with [autoPlay]) orange and then navy sweep
 * across it with a round leading edge while the letters hop one after another; on release the
 * colours pull back and the letters ripple the other way.
 *
 * @param autoPlay Fills and empties by itself, as if a pointer kept hovering in and out.
 */
@Composable
fun LetterWaveButton(
    label: String = "SWAGGER",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { LetterWaveMotion() }
    val waveMs = LETTER_MS + STAGGER_MS * max(label.length - 1, 0)

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
            motion.held = pressed
            scope.launch { motion.sync(waveMs, reducedMotion) }
        }
    }

    LaunchedEffect(autoPlay, waveMs) {
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            delay(AUTOPLAY_PAUSE_MS)
            motion.auto = true
            scope.launch { motion.sync(waveMs, reducedMotion) }
            delay(waveMs + AUTOPLAY_HOLD_MS)
            motion.auto = false
            scope.launch { motion.sync(waveMs, reducedMotion) }
            delay(waveMs.toLong())
        }
    }
    DisposableEffect(autoPlay) {
        onDispose {
            motion.auto = false
            scope.launch { motion.sync(waveMs, reducedMotion) }
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
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, h ->
                motion.clip.reset()
                motion.clip.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(RADIUS)))
                clipPath(motion.clip) {
                    drawRect(Color.Black, Offset.Zero, Size(w, h))
                    if (reducedMotion) {
                        // No sweep, the navy simply fades in.
                        drawRect(Navy, Offset.Zero, Size(w, h), alpha = motion.navy.value)
                    } else {
                        sweep(w, h, motion.orange.value, Orange)
                        sweep(w, h, motion.navy.value, Navy)
                    }
                }
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            // The copies are decoration; the button's own semantics carry the label.
            Row(Modifier.clearAndSetSemantics {}) {
                label.forEachIndexed { index, char ->
                    Letter(char.toString(), index, waveMs, motion)
                }
            }
        }
    }
}

/** A colour that has swept [progress] of the way across, led by a round edge. */
private fun DrawScope.sweep(w: Float, h: Float, progress: Float, color: Color) {
    val r = h * EDGE_RADIUS
    // The circle's centre travels from fully outside to where its arc has cleared the far corners.
    val travel = w + r - sqrt(r * r - h * h / 4f) + 1f
    val x = -r + travel * progress
    if (x > 0f) drawRect(color, Offset.Zero, Size(x, h))
    drawCircle(color, r, Offset(x, h / 2f))
}

/**
 * One letter as two stacked copies: as the wave passes, the first leaves in the wave's direction
 * while the second arrives from the opposite side.
 */
@Composable
private fun Letter(char: String, index: Int, waveMs: Int, motion: LetterWaveMotion) {
    fun progress(): Float =
        ((motion.wave.value * waveMs - index * STAGGER_MS) / LETTER_MS).coerceIn(0f, 1f)

    Box(Modifier.padding(horizontal = SPACING / 2)) {
        BasicText(
            text = char,
            style = LetterStyle,
            modifier = Modifier.graphicsLayer {
                val t = progress()
                alpha = 1f - t
                translationY = -motion.direction * RISE.toPx() * (t * t * (3f - 2f * t))
            },
        )
        BasicText(
            text = char,
            style = LetterStyle,
            modifier = Modifier.graphicsLayer {
                val t = progress()
                alpha = t
                translationY = motion.direction * RISE.toPx() * (1f - t * t * (3f - 2f * t))
            },
        )
    }
}
