package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.ngocdevv.nativecook.shared.EaseOutStrong

/** Press feedback: 0.97 in 120 ms on touch-down, back in 100 ms, ease-out (PRESS in motion.ts). */
internal fun Modifier.pulseHeartCardPress(role: Role, label: String, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PulseHeartCardMotion.PRESS_SCALE else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) PulseHeartCardMotion.PRESS_IN_MS else PulseHeartCardMotion.PRESS_OUT_MS,
            easing = EaseOutStrong,
        ),
        label = "pulseHeartCardPress",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, role = role, onClickLabel = label, onClick = onClick)
}
