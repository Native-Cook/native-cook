package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SURFACE = Color(0xFFF4F7FA)
private val INK = Color(0xFF071015)
private val SHAPE = RoundedCornerShape(17.dp)
/** The reference's ripple on Android: rgba(7, 16, 21, 0.12), in place of Material's own alpha. */
private val PRESS_RIPPLE = RippleConfiguration(color = INK, rippleAlpha = RippleAlpha(0.12f, 0.12f, 0.12f, 0.12f))
/** Easing.out(Easing.cubic). */
private val EaseOutCubic = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }

/**
 * The stage's action button (ActionButton.tsx, its light tone): a near-white pill that shrinks to
 * 0.97 under the finger, in 100 ms and back in 160 ms. The label is plain text and changes at
 * once, as in the reference on Android; `typeScale` is the capped font scale.
 */
@Composable
internal fun ActionButton(
    label: String,
    typeScale: Float,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 100 else 160, easing = EaseOutCubic),
        label = "press",
    )
    val hairline = with(LocalDensity.current) { 1f.toDp() }

    CompositionLocalProvider(LocalRippleConfiguration provides PRESS_RIPPLE) {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .graphicsLayer {
                    val scale = 1f - 0.03f * press
                    scaleX = scale
                    scaleY = scale
                }
                .clip(SHAPE)
                .background(SURFACE)
                .clickable(
                    interactionSource = interactions,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onPress,
                )
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Arrow(typeScale)
                BasicText(label, style = buttonText(12f * typeScale, 0.55f * typeScale), maxLines = 1, softWrap = false)
            }
            // Lit from above: one hairline of light on the top edge, so the pill reads as a surface.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(hairline)
                    .background(Color.White.copy(alpha = 0.95f)),
            )
        }
    }
}

/**
 * The arrow before the label. The reference prints "\u2192" in the system's black weight, which is
 * a heavy arrow on iOS; Android's default font draws that character as a hairline at any weight,
 * so the arrow is drawn here to the same measure: 11.3 × 9.3 in a 16 pt line.
 */
@Composable
private fun Arrow(typeScale: Float) {
    Canvas(Modifier.size((13.9f * typeScale).dp, (9.3f * typeScale).dp)) {
        val unit = size.height / 9.3f
        val stroke = 2.3f * unit
        val middle = size.height / 2f
        val tip = 11.45f * unit
        drawLine(INK, Offset(2.45f * unit, middle), Offset(tip, middle), stroke, StrokeCap.Round)
        val head = Path().apply {
            moveTo(7.7f * unit, 1.15f * unit)
            lineTo(tip, middle)
            lineTo(7.7f * unit, size.height - 1.15f * unit)
        }
        drawPath(head, INK, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun buttonText(size: Float, tracking: Float) = TextStyle(
    color = INK,
    fontSize = size.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
