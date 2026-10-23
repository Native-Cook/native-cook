package com.ngocdevv.nativecook.blocks.forms.dialslider.dial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.clampDialValue
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getPresetRange
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getSignedProgressColor
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.jsRound

/**
 * Circular preset tool with its progress ring and icon.
 *
 * @param onPress Omit for a visual-only indicator that should not receive accessibility focus.
 */
@Composable
internal fun PresetButton(
    preset: DialPreset,
    value: Double,
    selected: Boolean,
    accentColor: Color,
    adjustedColor: Color,
    modifier: Modifier = Modifier,
    onPress: (() -> Unit)? = null,
) {
    val (min, max) = getPresetRange(preset)
    val adjusted = jsRound(value) != jsRound(clampDialValue(0.0, min, max))
    val iconState = DialPresetIconState(selected = selected, adjusted = adjusted, value = value)
    val positiveProgressColor = if (selected) accentColor else adjustedColor
    val progressColor = getSignedProgressColor(value, positiveProgressColor, DialColors.Negative)
    val ringBackground = if (selected) {
        DialColors.SelectedRingBackground
    } else {
        DialColors.UnselectedRingBackground
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val formattedValue = preset.formatValue?.invoke(value) ?: formatDialValue(value)

    val semanticsModifier = if (onPress == null) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = preset.label
            this.selected = selected
            stateDescription = formattedValue
            if (preset.disabled) disabled()
        }
    }
    val clickModifier = if (onPress == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = !preset.disabled,
            onClick = onPress,
        )
    }

    Box(
        modifier = modifier
            .size(DialConfig.ITEM_SIZE.dp)
            .then(semanticsModifier)
            .graphicsLayer {
                val scale = if (pressed && onPress != null) 0.96f else 1f
                scaleX = scale
                scaleY = scale
                alpha = when {
                    preset.disabled -> 0.35f
                    pressed && onPress != null -> 0.85f
                    else -> 1f
                }
            }
            .clip(CircleShape)
            .background(DialColors.ItemSurface)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        PresetRing(
            value = value,
            minValue = min,
            maxValue = max,
            color = progressColor,
            backgroundColor = ringBackground,
            modifier = Modifier.size(DialConfig.ITEM_SIZE.dp),
        )
        Box(
            modifier = Modifier
                .size((DialConfig.ITEM_SIZE - 12).dp)
                .clip(CircleShape)
                .alpha(if (selected) 1f else 0.88f),
            contentAlignment = Alignment.Center,
        ) {
            preset.icon(iconState)
        }
    }
}

/** Mirrors JavaScript template literal formatting of `Math.round(value)`. */
internal fun formatDialValue(value: Double): String = jsRound(value).toLong().toString()
