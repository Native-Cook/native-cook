package com.ngocdevv.nativecook.blocks.forms.dialslider.dial

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.DialRange
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getPresetInitialValue
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getSignedProgressColor

/**
 * Preset strip (or single centered tool) plus the centered value badge shown
 * while the ruler is being dragged.
 */
@Composable
internal fun DialPresetViewport(
    presets: List<DialPreset>,
    values: Map<String, Double>,
    selectedPreset: DialPreset,
    selectedRange: DialRange,
    displayValue: Double,
    formattedValue: String,
    multiPreset: Boolean,
    isInteracting: Boolean,
    contentPadding: Dp,
    accentColor: Color,
    adjustedColor: Color,
    presetRowAlpha: () -> Float,
    valueBadgeAlpha: () -> Float,
    scrollState: ScrollState,
    onViewportWidthChanged: (widthPx: Int) -> Unit,
    onSelectPreset: (preset: DialPreset, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedValue = values[selectedPreset.id] ?: getPresetInitialValue(selectedPreset)
    val badgeProgressColor = getSignedProgressColor(displayValue, accentColor, DialColors.Negative)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DialConfig.ITEM_SIZE.dp)
            .clipToBounds()
            .onSizeChanged { onViewportWidthChanged(it.width) },
    ) {
        if (multiPreset) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = presetRowAlpha() }
                    .horizontalScroll(scrollState, enabled = !isInteracting)
                    .padding(horizontal = contentPadding),
                horizontalArrangement = Arrangement.spacedBy(DialConfig.ITEM_GAP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                presets.forEachIndexed { index, preset ->
                    PresetButton(
                        preset = preset,
                        value = values[preset.id] ?: getPresetInitialValue(preset),
                        selected = preset.id == selectedPreset.id,
                        accentColor = accentColor,
                        adjustedColor = adjustedColor,
                        onPress = { onSelectPreset(preset, index) },
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PresetButton(
                    preset = selectedPreset,
                    value = selectedValue,
                    selected = true,
                    accentColor = accentColor,
                    adjustedColor = adjustedColor,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(DialConfig.ITEM_SIZE.dp)
                .graphicsLayer {
                    val opacity = valueBadgeAlpha()
                    alpha = opacity
                    val scale = 0.94f + opacity * 0.06f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(DialColors.ItemSurface)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            PresetRing(
                value = displayValue,
                minValue = selectedRange.min,
                maxValue = selectedRange.max,
                color = badgeProgressColor,
                backgroundColor = DialColors.SelectedRingBackground,
                modifier = Modifier.size(DialConfig.ITEM_SIZE.dp),
            )
            BasicText(
                text = formattedValue,
                style = TextStyle(
                    color = accentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
