package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size as CanvasSize

// Freeze the launch position while the capsule closes, so the collapsing action keeps
// meeting the comet's origin instead of following the retreating text.
@Composable
internal fun ReferralCapsuleAction(
    state: ReferralState,
    width: Float,
    density: Float,
    actionWidth: Float,
    action: String,
    actionLabel: String,
    labelStyle: androidx.compose.ui.text.TextStyle,
    effects: Array<RenderEffect?>?,
) {
    Box(
        Modifier
            .offset(x = (width - 44 - actionWidth).dp, y = 90.dp)
            .size(actionWidth.dp, 44.dp)
            .graphicsLayer {
                val launching = !state.reduced && state.launchingPeek >= 0
                val at = if (launching) state.launchingPeek else state.peek.value
                val arrived = ((at - 0.5) / 0.4).coerceIn(0.0, 1.0)
                val presence =
                    if (launching) {
                        arrived + (1 - arrived) * state.capsuleShape.value
                    } else {
                        arrived
                    }
                alpha = presence.toFloat().coerceIn(0f, 1f)
                translationX =
                    if (state.reduced) {
                        0f
                    } else {
                        (-(width / 2 - 66) * (1 - at) * density).toFloat()
                    }
                val scale =
                    (
                        (if (state.reduced) 1.0 else 0.92 + 0.08 * presence) *
                            (1 - 0.04 * state.actionPress.value)
                    ).toFloat()
                scaleX = scale
                scaleY = scale
                renderEffect =
                    if (state.reduced) {
                        null
                    } else {
                        effects?.get(((1 - presence) * 64).roundToInt().coerceIn(0, 64))
                    }
            }.clearAndSetSemantics {
                if (state.open) {
                    role = Role.Button
                    contentDescription = actionLabel
                    onClick {
                        state.inviteCapsule()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val shape =
                if (state.reduced || state.launchingPeek < 0) {
                    0f
                } else {
                    state.capsuleShape.value.toFloat()
                }
            if (shape < 1) {
                val bodyWidth = (actionWidth + (12 - actionWidth) * shape) * density
                val bodyHeight = (44 - 32 * shape) * density
                val radius = (22 - 16 * shape) * density
                drawRoundRect(
                    Color.White,
                    topLeft =
                        Offset(
                            (size.width - bodyWidth) / 2,
                            (size.height - bodyHeight) / 2,
                        ),
                    size =
                        CanvasSize(bodyWidth, bodyHeight),
                    cornerRadius =
                        CornerRadius(radius),
                )
            }
        }
        BasicText(
            action,
            Modifier.graphicsLayer {
                val fade =
                    if (state.reduced || state.launchingPeek < 0) {
                        0.0
                    } else {
                        state.capsuleLabel.value
                    }
                alpha = (1 - fade).toFloat()
                renderEffect =
                    if (state.reduced) {
                        null
                    } else {
                        effects?.get((fade * 64).roundToInt().coerceIn(0, 64))
                    }
            },
            style = labelStyle.copy(color = Color(0xFF0C0C0D)),
        )
    }
}
