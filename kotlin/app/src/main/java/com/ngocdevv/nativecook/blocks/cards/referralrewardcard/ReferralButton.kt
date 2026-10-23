package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size as CanvasSize

// The glass and white collapse overlay use the same measured body. The comet clock also
// hides the button during departure, so its waiting dot cannot be drawn twice.
@Composable
internal fun ReferralButton(
    state: ReferralState,
    buttonWidth: Float,
    buttonHeight: Float,
    density: Float,
    effects: Array<androidx.compose.ui.graphics.RenderEffect?>?,
    first: String,
    second: String,
    labelStyle: TextStyle,
    modifier: Modifier,
) {
    Box(
        modifier.size(buttonWidth.dp, buttonHeight.dp).graphicsLayer {
            alpha = state.part(2).toFloat()
            translationY = if (state.reduced) 0f else (8 * (1 - state.part(2)) * density).toFloat()
        },
        contentAlignment = Alignment.Center,
    ) {
        ReferralButtonBody(state, buttonWidth, buttonHeight, density, Modifier.matchParentSize())
        for (i in 0..1) {
            BasicText(
                if (i == 0) first else second,
                Modifier
                    .padding(horizontal = Size.cardPad.dp)
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        val swap = state.swap.value
                        val fade = state.label.value
                        val fit =
                            if (state.reduced) {
                                1.0
                            } else {
                                (1 - fade) * (1 - ((state.shape.value - 0.1) / 0.3).coerceIn(0.0, 1.0))
                            }
                        alpha = ((if (i == 0) 1 - swap else swap) * fit).toFloat().coerceIn(0f, 1f)
                        translationY =
                            if (state.reduced) {
                                0f
                            } else {
                                ((if (i == 0) -swap else 1 - swap) * 4 * density).toFloat()
                            }
                        renderEffect =
                            if (state.reduced) {
                                null
                            } else {
                                effects?.get((fade * 64).roundToInt().coerceIn(0, 64))
                            }
                    },
                style = labelStyle,
            )
        }
        Box(
            Modifier
                .requiredSize((buttonWidth + 16).dp, (buttonHeight + 16).dp)
                .referralButtonGesture(state, density)
                .semantics {
                    role = Role.Button
                    contentDescription =
                        if (state.count == ReferralPalette.friendCount) second else first
                    onClick {
                        state.activate()
                        true
                    }
                },
        )
    }
}

@Composable
private fun ReferralButtonBody(
    state: ReferralState,
    buttonWidth: Float,
    buttonHeight: Float,
    density: Float,
    modifier: Modifier,
) {
    Canvas(modifier.graphicsLayer()) {
        val shape = if (state.reduced) 0f else state.shape.value.toFloat()
        val elapsed = state.comet.value * (state.path?.total ?: 1.0)
        if (
            shape < 1 &&
            (state.fromCapsule || !(state.comet.value < 1 && elapsed >= 0 && elapsed < 120))
        ) {
            val scale = 1 - 0.04f * state.press.value.toFloat() * (1 - shape)
            val bodyWidth = (buttonWidth + (12 - buttonWidth) * shape) * scale * density
            val bodyHeight = (buttonHeight + (12 - buttonHeight) * shape) * scale * density
            val radius =
                (max(24f, buttonHeight / 2) + (6 - max(24f, buttonHeight / 2)) * shape) *
                    scale *
                    density
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
                alpha = shape.coerceIn(0f, 1f).pow(4),
            )
        }
    }
}
