package com.ngocdevv.nativecook.blocks.buttons.depthbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val FACE_HEIGHT = 46.dp

// Height of the side wall, and how far the face travels when pressed.
private val DEPTH = 9.dp
private val RADIUS = 5.dp
private const val AUTOPLAY_CYCLE_MS = 4400L
private const val AUTOPLAY_HOLD_MS = 240L

private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 17.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.5.sp,
)

/** Generic glyphs for [DepthButton], drawn on an 18dp grid. [stroke] is null for filled ones. */
enum class DepthButtonIcon(internal val stroke: Float?, internal val build: Path.() -> Unit) {
    Share(2f, {
        moveTo(9f, 11.5f)
        lineTo(9f, 2.6f)
        moveTo(5.4f, 6f)
        lineTo(9f, 2.4f)
        lineTo(12.6f, 6f)
        moveTo(3f, 10f)
        lineTo(3f, 14.2f)
        quadraticTo(3f, 15.6f, 4.4f, 15.6f)
        lineTo(13.6f, 15.6f)
        quadraticTo(15f, 15.6f, 15f, 14.2f)
        lineTo(15f, 10f)
    }),
    Plus(2.4f, {
        moveTo(9f, 3.2f)
        lineTo(9f, 14.8f)
        moveTo(3.2f, 9f)
        lineTo(14.8f, 9f)
    }),
    Heart(null, {
        moveTo(9f, 15.5f)
        cubicTo(3f, 11.5f, 1.5f, 8.5f, 1.5f, 6f)
        cubicTo(1.5f, 3.8f, 3.2f, 2.3f, 5.2f, 2.3f)
        cubicTo(6.8f, 2.3f, 8.2f, 3.2f, 9f, 4.6f)
        cubicTo(9.8f, 3.2f, 11.2f, 2.3f, 12.8f, 2.3f)
        cubicTo(14.8f, 2.3f, 16.5f, 3.8f, 16.5f, 6f)
        cubicTo(16.5f, 8.5f, 15f, 11.5f, 9f, 15.5f)
        close()
    }),
    Play(null, {
        moveTo(5f, 2.8f)
        lineTo(15.2f, 9f)
        lineTo(5f, 15.2f)
        close()
    }),
}

@Composable
private fun Glyph(icon: DepthButtonIcon) {
    val path = remember(icon) { Path().apply(icon.build) }
    Canvas(Modifier.size(18.dp)) {
        surface(0.dp) { _, _, _, _ ->
            val stroke = icon.stroke
            if (stroke != null) {
                drawPath(path, Color.White, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(path, Color.White)
                // A thin same-colour outline rounds the filled shape's corners.
                drawPath(path, Color.White, style = Stroke(1.4f, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Chunky push button: a flat coloured face sitting on a darker side wall. Pressing it sinks the
 * face into the wall; letting go pops it back up.
 *
 * @param color Colour of the face.
 * @param sideColor Colour of the side wall the face sinks into.
 * @param autoPlay Presses itself once per cycle, as if a finger kept tapping it.
 * @param autoPlayDelayMs Where in the cycle the self-press happens, so a group can take turns.
 */
@Composable
fun DepthButton(
    label: String = "Button",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1361EF),
    sideColor: Color = Color(0xFF1D2F61),
    icon: DepthButtonIcon? = null,
    width: Dp = 148.dp,
    autoPlay: Boolean = false,
    autoPlayDelayMs: Long = 0,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var held by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    held = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> held = false
            }
        }
    }

    LaunchedEffect(autoPlay, autoPlayDelayMs) {
        auto = false
        if (!autoPlay) return@LaunchedEffect
        delay(900 + autoPlayDelayMs)
        while (true) {
            auto = true
            delay(AUTOPLAY_HOLD_MS)
            auto = false
            delay(AUTOPLAY_CYCLE_MS - AUTOPLAY_HOLD_MS)
        }
    }

    val down = held || auto
    // Quick on the way down, a touch slower coming back up; reduced motion snaps, which is
    // exactly how the reference behaves.
    val sink by animateFloatAsState(
        targetValue = if (down) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(if (down) 90 else 150, easing = EaseOutStrong),
        label = "sink",
    )

    Box(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .width(width)
            .height(FACE_HEIGHT + DEPTH)
            .drawBehind {
                val radius = CornerRadius(RADIUS.toPx())
                val face = Size(size.width, FACE_HEIGHT.toPx())
                drawRoundRect(sideColor, Offset(0f, DEPTH.toPx()), face, radius)
                drawRoundRect(color, Offset(0f, sink * DEPTH.toPx()), face, radius)
            },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .height(FACE_HEIGHT)
                .graphicsLayer { translationY = sink * DEPTH.toPx() },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Glyph(icon)
            BasicText(label, style = LabelStyle, maxLines = 1)
        }
    }
}

/** The four buttons from the reference as a 2×2 group. With [autoPlay] they press in turn. */
@Composable
fun DepthButtonGroup(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    // The order the reference's cursor visits the buttons in: clockwise from the bottom left.
    fun turn(index: Int) = index * AUTOPLAY_CYCLE_MS / 4
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DepthButton(
                label = "Share", color = Color(0xFF1361EF), sideColor = Color(0xFF1D2F61),
                icon = DepthButtonIcon.Share, autoPlay = autoPlay, autoPlayDelayMs = turn(2),
            )
            DepthButton(
                label = "Follow", color = Color(0xFF198CEC), sideColor = Color(0xFF233D6B),
                icon = DepthButtonIcon.Plus, autoPlay = autoPlay, autoPlayDelayMs = turn(3),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DepthButton(
                label = "Like", color = Color(0xFFEE542C), sideColor = Color(0xFF8A0A24),
                icon = DepthButtonIcon.Heart, autoPlay = autoPlay, autoPlayDelayMs = turn(0),
            )
            DepthButton(
                label = "Play", color = Color(0xFFCF161E), sideColor = Color(0xFF8A0005),
                icon = DepthButtonIcon.Play, autoPlay = autoPlay, autoPlayDelayMs = turn(1),
            )
        }
    }
}
