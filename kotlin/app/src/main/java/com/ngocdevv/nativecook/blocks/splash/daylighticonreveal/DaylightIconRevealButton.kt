package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect

/**
 * The Continue button: `PressScale` around the `cta` style of the React Native file.
 *
 * Under a finger the whole surface settles to 0.96 in 150 ms on the strong ease-out and returns on
 * the same curve; the transition retargets mid-flight. Under Reduce Motion it dims to 0.72 instead.
 * A press is kept while the finger stays within 16 dp of the button (`pressRetentionOffset`): it
 * ends (press out) when the finger leaves that area, begins again when it returns, and only a release
 * inside counts as a tap. A scroll view that takes over the touch cancels the press. A disabled
 * button never moves and never answers.
 *
 * Surface: #503ad4, radius 16, minimum height 48, padding 25 by 13, a 1 dp white 18% line along its
 * top edge (inset shadow) and `0 12 22 -12 rgba(80,58,212,0.75)` below.
 */
@Composable
internal fun DaylightIconRevealButton(
    label: String,
    width: Dp,
    enabled: Boolean,
    reduced: Boolean,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPressIn by rememberUpdatedState(onPressIn)
    val currentPressOut by rememberUpdatedState(onPressOut)
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val down = pressed && enabled
    val progress = remember { Animatable(0f) }
    val shadow = remember { SoftRoundRect(Color(80 / 255f, 58 / 255f, 212 / 255f, 0.75f), sigma = 11f) }
    val dimPaint = remember { Paint() }

    LaunchedEffect(down) {
        progress.animateTo(if (down) 1f else 0f, tween(150, easing = DaylightIconRevealTimeline.Ease))
    }

    Box(
        modifier = modifier
            .width(width)
            .graphicsLayer {
                if (!reduced) {
                    val scale = 1f - 0.04f * progress.value
                    scaleX = scale
                    scaleY = scale
                }
            }
            .drawWithContent {
                val opacity = if (reduced) 1f - 0.28f * progress.value else 1f
                if (opacity >= 0.999f) {
                    drawContent()
                } else {
                    // A group opacity with room around the box, so the shadow is not clipped.
                    dimPaint.alpha = opacity
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(-120f, -120f, size.width + 120f, size.height + 120f), dimPaint)
                        drawContent()
                        canvas.restore()
                    }
                }
            }
            .drawBehind { drawSurface(shadow) }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
                onClick(label) {
                    if (!enabled) return@onClick false
                    currentPress()
                    return@onClick true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val retention = 16.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var inside = true
                    var released = false
                    pressed = true
                    currentPressIn()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            if (change.isConsumed) break
                            val within = change.position.x >= -retention &&
                                change.position.x <= size.width + retention &&
                                change.position.y >= -retention &&
                                change.position.y <= size.height + retention
                            if (within != inside) {
                                inside = within
                                pressed = within
                                if (within) currentPressIn() else currentPressOut()
                            }
                            if (!change.pressed) {
                                released = inside
                                break
                            }
                        }
                    } finally {
                        if (inside) {
                            pressed = false
                            currentPressOut()
                        }
                    }
                    if (released) currentPress()
                }
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 25.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeightStyle = DaylightIconRevealLineHeightStyle,
            ),
        )
    }
}

/** The button's shadow, fill and top line, in that order, behind its text. */
private fun DrawScope.drawSurface(shadow: SoftRoundRect) {
    val radius = 16.dp.toPx()
    val box = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
    // Spread -12 pulls the shadow 12 dp inside the box on every side and takes the same off its
    // corner radius; the offset then moves it 12 dp down. Only the part outside the box shows.
    clipPath(box, ClipOp.Difference) {
        scale(density, density, pivot = Offset.Zero) {
            drawIntoCanvas { canvas ->
                shadow.draw(canvas.nativeCanvas, 12f, 24f, size.width / density - 24f, size.height / density - 24f, 4f)
            }
        }
    }
    drawRoundRect(color = Color(0xFF503AD4), cornerRadius = CornerRadius(radius))
    // The inset shadow `0 1 0 rgba(255,255,255,0.18)`: what is inside the box but not inside the
    // box moved 1 dp down, which is a line along the top edge that follows the corners.
    val line = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-2f, -2f, size.width + 2f, size.height + 4f))
        addRoundRect(RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), CornerRadius(radius)))
    }
    clipPath(box) {
        drawPath(line, Color.White.copy(alpha = 0.18f))
    }
}
