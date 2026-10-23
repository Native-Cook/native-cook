package com.ngocdevv.nativecook.blocks.buttons.ticketbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HEIGHT = 48.dp
private val PAD_X = 62.dp

// The label rolls inside a window this tall, like a one-line ticker.
private val LINE = 24.dp
private const val FILL_MS = 550
private const val ROLL_MS = 300
private const val AUTOPLAY_PAUSE_MS = 1300L
private const val AUTOPLAY_HOLD_MS = 1500L

// How far the corners of the moving edge trail behind its middle, as a fraction of the height.
private const val LAG = 0.45f

// Circle-to-cubic constant for the rounded corners of the edge.
private const val K = 0.5523f

private val Paper = Color(0xFFFFFDFE)
private val Teal = Color(0xFF46C9CB)
private val Ink = Color(0xFF141016)
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

private val LabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    // Keep the full 24sp line and centre the glyphs in it; the default trims the line to the font's own height.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp,
)

private class TicketMotion {
    /**
     * Runs 0 → 1 while the teal sheet rises from the bottom and 1 → 2 while the white sheet drops
     * back from the top; 2 looks exactly like 0, so it can snap back unnoticed.
     */
    val phase = Animatable(0f)

    /** 0 shows the dark label, 1 the white one. */
    val roll = Animatable(0f)
    var held = false
    var auto = false
    private var shown = false
    private var busy = false

    val sheet = Path()

    /** Plays whole sweeps until the button shows what is wanted, so a quick tap never cuts one short. */
    suspend fun sync() {
        if (busy) return
        busy = true
        try {
            while (shown != (held || auto)) {
                val on = !shown
                if (on) phase.snapTo(0f)
                coroutineScope {
                    launch { phase.animateTo(if (on) 1f else 2f, tween(FILL_MS, easing = EaseOutCubic)) }
                    launch { roll.animateTo(if (on) 1f else 0f, tween(ROLL_MS, easing = EaseOutCubic)) }
                }
                shown = on
            }
        } finally {
            busy = false
        }
    }
}

/**
 * White "GET TICKET" slab. While pressed (or by itself with [autoPlay]) a teal sheet with a
 * rounded leading edge rises from the bottom and the label rolls over to white; on release a white
 * sheet drops back down from the top.
 *
 * @param autoPlay Fills and empties by itself, as if a pointer kept hovering in and out.
 */
@Composable
fun TicketButton(
    label: String = "GET TICKET",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val motion = remember { TicketMotion() }

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
            scope.launch { motion.sync() }
        }
    }

    LaunchedEffect(autoPlay) {
        if (!autoPlay) return@LaunchedEffect
        while (true) {
            delay(AUTOPLAY_PAUSE_MS)
            motion.auto = true
            scope.launch { motion.sync() }
            delay(FILL_MS + AUTOPLAY_HOLD_MS)
            motion.auto = false
            scope.launch { motion.sync() }
            delay(FILL_MS.toLong())
        }
    }
    DisposableEffect(autoPlay) {
        onDispose {
            motion.auto = false
            scope.launch { motion.sync() }
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
        // The sheet's trailing corners start below (or above) the slab, so it has to be clipped to it.
        Canvas(Modifier.matchParentSize().clipToBounds()) {
            surface(0.dp) { _, _, w, h ->
                val phase = motion.phase.value
                val filling = phase <= 1f
                val progress = if (filling) phase else phase - 1f
                drawRect(if (filling) Paper else Teal, Offset.Zero, Size(w, h))

                // Reduced motion: the sheet does not travel, it fades in over the whole button.
                val p = if (reducedMotion) 1f else progress
                val lag = h * LAG * (1f - p)
                // A full-width dome at first that flattens into a straight edge with rounded corners.
                val rx = w * (0.06f + 0.44f * (1f - p) * (1f - p) * (1f - p))
                // `toward` points from the edge back to the side the sheet came from.
                val home = if (filling) h else 0f
                val toward = if (filling) 1f else -1f
                val edge = if (filling) h * (1f - p) else h * p
                val side = edge + toward * lag

                val sheet = motion.sheet
                sheet.reset()
                sheet.moveTo(0f, home)
                sheet.lineTo(0f, side)
                sheet.cubicTo(0f, side - toward * K * lag, rx - K * rx, edge, rx, edge)
                sheet.lineTo(w - rx, edge)
                sheet.cubicTo(w - rx + K * rx, edge, w, side - toward * K * lag, w, side)
                sheet.lineTo(w, home)
                sheet.close()
                drawPath(sheet, if (filling) Teal else Paper, alpha = if (reducedMotion) progress else 1f)
            }
        }
        Box(Modifier.height(HEIGHT).padding(horizontal = PAD_X).wrapContentHeight()) {
            // Both copies say the same thing; the button's own semantics carry the label.
            Box(Modifier.height(LINE).clipToBounds().clearAndSetSemantics {}) {
                BasicText(
                    text = label,
                    style = LabelStyle.copy(color = Ink),
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val roll = motion.roll.value
                        if (reducedMotion) alpha = 1f - roll else translationY = -LINE.toPx() * roll
                    },
                )
                BasicText(
                    text = label,
                    style = LabelStyle.copy(color = Color.White),
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val roll = motion.roll.value
                        if (reducedMotion) alpha = roll else translationY = LINE.toPx() * (1f - roll)
                    },
                )
            }
        }
    }
}
