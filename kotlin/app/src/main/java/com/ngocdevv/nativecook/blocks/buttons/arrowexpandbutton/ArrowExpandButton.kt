package com.ngocdevv.nativecook.blocks.buttons.arrowexpandbutton

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HEIGHT = 48.dp

// The label starts to the right of the circle; the circle grows over it.
private val LABEL_LEFT = 65.dp
private val PAD_RIGHT = 31.dp
private const val EXPAND_MS = 450
private const val AUTOPLAY_REST_MS = 1300L
private const val AUTOPLAY_HOLD_MS = 1700L

// Tip of the chevron at rest, how far the arrow travels, and the length of the tail it grows.
private const val TIP_X = 28.5f
private const val ARROW_SHIFT = 15f
private const val TAIL = 18f
private const val HEAD = 6.5f

private val Ink = Color(0xFF22242F)

// Slow start, hard brake: the circle seems to gather itself before it sweeps across.
private val EaseSweep = CubicBezierEasing(0.65f, 0f, 0.076f, 1f)

private val LabelStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp)
private val ArrowStroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

// Blends in plain sRGB components, like the React Native and Swift versions.
private fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)

/**
 * "LEARN MORE" with a dark circle holding a chevron. While pressed (or by itself with [autoPlay])
 * the circle sweeps across into a full pill, the chevron slides right and grows a tail, and the
 * label turns white; then it all contracts again.
 *
 * @param autoPlay Expands and contracts by itself, as if a pointer kept hovering over it.
 * @param lingerMs How long it stays expanded after the finger lifts, so it isn't hidden under the thumb.
 */
@Composable
fun ArrowExpandButton(
    label: String = "LEARN MORE",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    lingerMs: Long = 900,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var lingering by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }
    val linger by rememberUpdatedState(lingerMs)

    LaunchedEffect(interaction) {
        var lingerJob: Job? = null
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    lingerJob?.cancel()
                    pressed = true
                    lingering = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = false
                    lingerJob = launch {
                        delay(linger)
                        lingering = false
                    }
                }
            }
        }
    }

    val expanded = pressed || lingering || auto
    val expand = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        expand.animateTo(if (expanded) 1f else 0f, tween(EXPAND_MS, easing = EaseSweep))
    }

    // The loop is pure motion, so it is dropped when the system asks for reduced motion.
    val looping = autoPlay && !reducedMotion
    LaunchedEffect(looping, auto) {
        if (!looping) return@LaunchedEffect
        delay(if (auto) AUTOPLAY_HOLD_MS else AUTOPLAY_REST_MS)
        auto = !auto
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val chevron = remember {
        Path().apply {
            moveTo(TIP_X - HEAD, -HEAD)
            lineTo(TIP_X, 0f)
            lineTo(TIP_X - HEAD, HEAD)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.matchParentSize()) {
            surface(0.dp) { _, _, w, h ->
                val e = expand.value
                drawRoundRect(Ink, Offset.Zero, Size(h + (w - h) * e, h), CornerRadius(h / 2))
                translate(ARROW_SHIFT * e, h / 2) {
                    drawLine(
                        color = Color.White,
                        start = Offset(TIP_X - TAIL, 0f),
                        end = Offset(TIP_X - 1f, 0f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                        alpha = e,
                    )
                    drawPath(chevron, Color.White, style = ArrowStroke)
                }
            }
        }
        Box(Modifier.height(HEIGHT).padding(start = LABEL_LEFT, end = PAD_RIGHT).wrapContentHeight()) {
            BasicText(
                text = label,
                style = LabelStyle,
                maxLines = 1,
                color = ColorProducer { mix(Ink, Color.White, expand.value) },
            )
        }
    }
}
