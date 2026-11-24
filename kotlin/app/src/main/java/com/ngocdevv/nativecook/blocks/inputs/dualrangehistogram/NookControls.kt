package com.ngocdevv.nativecook.blocks.inputs.dualrangehistogram

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ACCENT = Color(0xFFD9542F)
private val INK = Color(0xFF2A2420)
private val MUTE = Color(0xFF7C7267)
private val ON = Color(0xFFFFFDF9)
private val OFF_TRACK = Color(0xFFD8D1C5)
private val SEG_BG = Color(42, 36, 32).copy(alpha = 0.08f)
private val SEG_EDGE = Color(42, 36, 32).copy(alpha = 0.12f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segments(items: List<String>, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        items.forEachIndexed { i, name ->
            SegmentedButton(
                selected = value == i,
                onClick = { onChange(i) },
                shape = SegmentedButtonDefaults.itemShape(i, items.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = ON,
                    activeContentColor = INK,
                    activeBorderColor = SEG_EDGE,
                    inactiveContainerColor = SEG_BG,
                    inactiveContentColor = MUTE,
                    inactiveBorderColor = SEG_EDGE,
                ),
                border = BorderStroke(1.dp, SEG_EDGE),
                contentPadding = PaddingValues(horizontal = 4.dp),
                icon = {},
                label = {
                    Text(
                        name,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = if (value == i) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                },
            )
        }
    }
}

/** The place type as a Material segmented control. */
@Composable
internal fun NookPlacePicker(width: Dp, placeType: Int, onPlaceType: (Int) -> Unit) {
    Segments(PLACE_TYPES, placeType, onPlaceType, Modifier.width(width))
}

private val LABEL = TextStyle(color = INK, fontSize = 15.sp, fontWeight = FontWeight.Medium)

/** Bedrooms and the amenities: Material segmented buttons and switches. */
@Composable
internal fun NookControls(
    width: Dp,
    bedrooms: Int,
    amenities: List<Boolean>,
    amenityRows: Int,
    onBedrooms: (Int) -> Unit,
    onAmenity: (Int, Boolean) -> Unit,
) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Bedrooms", style = LABEL)
            Segments(BEDROOMS, bedrooms, onBedrooms, Modifier.width(200.dp))
        }
        AMENITIES.take(amenityRows).forEachIndexed { i, a ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(a.label, style = LABEL.copy(fontWeight = FontWeight.Normal))
                Switch(
                    checked = amenities[i],
                    onCheckedChange = { onAmenity(i, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ON,
                        checkedTrackColor = ACCENT,
                        checkedBorderColor = ACCENT,
                        uncheckedThumbColor = ON,
                        uncheckedTrackColor = OFF_TRACK,
                        uncheckedBorderColor = OFF_TRACK,
                    ),
                )
            }
        }
    }
}
