package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.ngocdevv.nativecook.shared.EaseOutStrong

/**
 * `PressScale` from the shared React Native helpers: the whole surface settles to [scale] in 150 ms on the strong
 * ease-out and returns on the same curve; under Reduce Motion it dims to 0.72 instead. A disabled control never moves.
 * It is a checkbox here because its only use is a task row.
 */
@Composable
internal fun EmberDayReviewPressScale(
    checked: Boolean,
    enabled: Boolean,
    reduced: Boolean,
    scale: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = pressed && enabled
    val scaleValue by animateFloatAsState(
        targetValue = if (down && !reduced) scale else 1f,
        animationSpec = tween(150, easing = EaseOutStrong),
        label = "press-scale",
    )
    val dim by animateFloatAsState(
        targetValue = if (reduced && down) 0.72f else 1f,
        animationSpec = tween(150, easing = EaseOutStrong),
        label = "press-dim",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scaleValue
                scaleY = scaleValue
                alpha = dim
            }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onToggle() },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** A plain `Pressable`: a tap target with no feedback of its own, as in the React Native file. */
@Composable
internal fun EmberDayReviewTap(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.clickable(
            enabled = enabled,
            role = role,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
