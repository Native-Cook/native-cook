package com.ngocdevv.nativecook.blocks.buttons.sharerevealbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ShareTarget(val label: String) {
    Message("Message"),
    Mail("Mail"),
    Copy("Copy link"),
    More("More"),
}

private const val WIDTH = 208f
private const val HEIGHT = 64f

// Bevelled rim of the indigo tray the white cover sits in.
private const val BORDER = 2.5f
private const val INNER_W = WIDTH - BORDER * 2
private const val INNER_H = HEIGHT - BORDER * 2

// How much of the cover stays in view when open: a tab to pull it back by.
private const val SLIVER = 16f
private const val TRAVEL = INNER_W - SLIVER

// The icons glide in from this far to the right as the cover leaves.
private const val ICON_DRIFT = 40f
private val ICON_X = floatArrayOf(59f, 99f, 139f, 175f)
private const val TAB_HIT = 38f
private const val ICON_HIT = 40f

// The canvas overflows the button by this much on every side to fit the glow.
private val BLEED = 48.dp
private const val AUTO_OPEN_MS = 2400L
private const val AUTO_CLOSED_MS = 1700L

// Slightly under-damped (mass 1, stiffness 320, damping 27): the cover overshoots by a few dp and settles.
private const val SPRING_STIFFNESS = 320f
private const val SPRING_DAMPING_RATIO = 0.755f

private val LabelStyle = TextStyle(
    color = Color(0xFF3D37C6),
    fontSize = 17.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = 0.6.sp,
)

/** Generic line icons on a 20dp box centred on the origin. */
private fun iconPath(target: ShareTarget) = Path().apply {
    when (target) {
        // Speech bubble with its tail at the bottom left.
        ShareTarget.Message -> {
            moveTo(-6f, -8f); lineTo(6f, -8f); quadraticTo(9f, -8f, 9f, -5f); lineTo(9f, 2f)
            quadraticTo(9f, 5f, 6f, 5f); lineTo(-0.5f, 5f); lineTo(-5f, 9f); lineTo(-5f, 5f); lineTo(-6f, 5f)
            quadraticTo(-9f, 5f, -9f, 2f); lineTo(-9f, -5f); quadraticTo(-9f, -8f, -6f, -8f); close()
        }
        // Envelope and its flap.
        ShareTarget.Mail -> {
            moveTo(-6.5f, -6.5f); lineTo(6.5f, -6.5f); quadraticTo(9f, -6.5f, 9f, -4f); lineTo(9f, 4f)
            quadraticTo(9f, 6.5f, 6.5f, 6.5f); lineTo(-6.5f, 6.5f); quadraticTo(-9f, 6.5f, -9f, 4f)
            lineTo(-9f, -4f); quadraticTo(-9f, -6.5f, -6.5f, -6.5f); close()
            moveTo(-8f, -4.8f); lineTo(0f, 1.2f); lineTo(8f, -4.8f)
        }
        // Two stacked sheets.
        ShareTarget.Copy -> {
            moveTo(-0.5f, -3f); lineTo(6f, -3f); quadraticTo(8.5f, -3f, 8.5f, -0.5f); lineTo(8.5f, 6f)
            quadraticTo(8.5f, 8.5f, 6f, 8.5f); lineTo(-0.5f, 8.5f); quadraticTo(-3f, 8.5f, -3f, 6f)
            lineTo(-3f, -0.5f); quadraticTo(-3f, -3f, -0.5f, -3f); close()
            moveTo(-6f, 3f); quadraticTo(-8.5f, 3f, -8.5f, 0.5f); lineTo(-8.5f, -6f)
            quadraticTo(-8.5f, -8.5f, -6f, -8.5f); lineTo(0.5f, -8.5f); quadraticTo(3f, -8.5f, 3f, -6f)
        }
        // Arrow leaving a tray.
        ShareTarget.More -> {
            moveTo(-7f, 0.5f); lineTo(-7f, 6f); quadraticTo(-7f, 8.5f, -4.5f, 8.5f); lineTo(4.5f, 8.5f)
            quadraticTo(7f, 8.5f, 7f, 6f); lineTo(7f, 0.5f)
            moveTo(0f, 3f); lineTo(0f, -8.5f)
            moveTo(-4f, -4.5f); lineTo(0f, -8.5f); lineTo(4f, -4.5f)
        }
    }
}

private class ShareRevealPaints {
    val glow = SoftRoundRect(rgba(74, 62, 240, 0.6f), sigma = 16f)
    val coverShadow = SoftRoundRect(rgba(16, 12, 84, 0.45f), sigma = 5f)
    val icons = ShareTarget.entries.map(::iconPath)
    val tray = Path()
}

/**
 * White "SHARE" cover that springs aside to reveal four share targets on an indigo tray.
 *
 * @param expanded Controlled state. Leave null to let the button keep its own.
 * @param onShare One of the four share targets was tapped; the cover slides back afterwards.
 * @param autoPlay Slide open and shut by itself with a pause in each state. Tapping still works.
 */
@Composable
fun ShareRevealButton(
    label: String = "SHARE",
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    onShare: (ShareTarget) -> Unit = {},
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    var inner by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val open = expanded ?: inner
    val controlled by rememberUpdatedState(expanded != null)
    val notify by rememberUpdatedState(onExpandedChange)

    fun setOpen(next: Boolean) {
        // The touch target that was just tapped is about to be swapped out, and may never report its release.
        pressed = false
        if (!controlled) inner = next
        notify(next)
    }

    // Re-armed after every change, so a tap simply restarts the pause.
    LaunchedEffect(autoPlay, reducedMotion, pressed, open) {
        if (!autoPlay || reducedMotion || pressed) return@LaunchedEffect
        delay(if (open) AUTO_OPEN_MS else AUTO_CLOSED_MS)
        setOpen(!open)
    }

    // 0 = covered, 1 = open. A spring, so it runs a little past either end before settling.
    val p = animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reducedMotion) tween(200) else spring(SPRING_DAMPING_RATIO, SPRING_STIFFNESS),
        label = "reveal",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    val paints = remember { ShareRevealPaints() }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Canvas(Modifier.matchParentSize().bleed(BLEED)) {
            surface(BLEED) { x, y, w, h -> drawShare(paints, x, y, w, h, p.value, still = reducedMotion) }
        }
        // The label rides on the cover, clipped by the tray like the cover itself.
        Box(
            modifier = Modifier
                .padding(BORDER.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(percent = 50))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = label,
                style = LabelStyle,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    if (reducedMotion) {
                        alpha = (1f - p.value).coerceIn(0f, 1f)
                    } else {
                        translationX = -TRAVEL * p.value * density
                    }
                },
            )
        }

        if (open) {
            Hit("Close", 0f, TAB_HIT, { pressed = it }) { setOpen(false) }
            ShareTarget.entries.forEachIndexed { index, target ->
                Hit(target.label, ICON_X[index] - ICON_HIT / 2, ICON_HIT, { pressed = it }) {
                    onShare(target)
                    setOpen(false)
                }
            }
        } else {
            Hit(label, 0f, WIDTH, { pressed = it }) { setOpen(true) }
        }
    }
}

/** Invisible touch target that only reports its pressed state, so the shared tray can react. */
@Composable
private fun Hit(
    name: String,
    x: Float,
    width: Float,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val report by rememberUpdatedState(onPressedChange)

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    report(true)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> report(false)
            }
        }
    }

    Box(
        Modifier
            .offset(x = x.dp)
            .size(width.dp, HEIGHT.dp)
            .semantics { contentDescription = name }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    )
}

private fun DrawScope.drawShare(
    paints: ShareRevealPaints,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    p: Float,
    still: Boolean,
) {
    val ix = x + BORDER
    val iy = y + BORDER
    val clamped = p.coerceIn(0f, 1f)

    drawIntoCanvas { paints.glow.draw(it.nativeCanvas, x + 6f, y + 14f, w - 12f, h, h / 2, alpha = clamped) }

    // Tray: a rim lit from above, then the indigo floor.
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF9499F3),
            0.5f to Color(0xFF5A56DC),
            1f to Color(0xFF2A2694),
            startY = y,
            endY = y + h,
        ),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2),
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color(0xFF4B46DE), Color(0xFF352FB6)),
            start = Offset(ix, iy),
            end = Offset(ix + INNER_W, iy + INNER_H),
        ),
        topLeft = Offset(ix, iy),
        size = Size(INNER_W, INNER_H),
        cornerRadius = CornerRadius(INNER_H / 2),
    )

    paints.tray.reset()
    paints.tray.addRoundRect(RoundRect(ix, iy, ix + INNER_W, iy + INNER_H, CornerRadius(INNER_H / 2)))
    clipPath(paints.tray) {
        val drift = if (still) 0f else ICON_DRIFT * (1f - p)
        val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        paints.icons.forEachIndexed { index, icon ->
            translate(x + ICON_X[index] + drift, y + h / 2) { drawPath(icon, Color.White, style = stroke) }
        }

        val coverAlpha = if (still) 1f - clamped else 1f
        translate(if (still) 0f else -TRAVEL * p, 0f) {
            drawIntoCanvas {
                paints.coverShadow.draw(it.nativeCanvas, ix + 3f, iy, INNER_W, INNER_H, INNER_H / 2, alpha = coverAlpha)
            }
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFBFBFA), Color(0xFFF1F1EE)), startY = iy, endY = iy + INNER_H),
                topLeft = Offset(ix, iy),
                size = Size(INNER_W, INNER_H),
                cornerRadius = CornerRadius(INNER_H / 2),
                alpha = coverAlpha,
            )
        }
    }
}
