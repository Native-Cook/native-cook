package com.ngocdevv.nativecook.blocks.forms.dialslider.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.dialslider.dial.DialPreset
import com.ngocdevv.nativecook.blocks.forms.dialslider.dial.DialSlider

@Composable
private fun PresetGlyph(glyph: String, selected: Boolean) {
    Text(
        text = glyph,
        color = if (selected) Color.White else Color(0xFFE4E4E7),
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
    )
}

private fun photoPreset(id: String, label: String, glyph: String) = DialPreset(
    id = id,
    label = label,
    icon = { state -> PresetGlyph(glyph, state.selected) },
    minValue = -100.0,
    maxValue = 100.0,
    initialValue = 0.0,
)

private val PHOTO_PRESETS = listOf(
    photoPreset("exposure", "Exposure", "◐"),
    photoPreset("brilliance", "Brilliance", "◭"),
    photoPreset("highlights", "Highlights", "◒"),
    photoPreset("shadows", "Shadows", "◑"),
    photoPreset("contrast", "Contrast", "◉"),
    photoPreset("brightness", "Brightness", "☀"),
    photoPreset("saturation", "Saturation", "◍"),
)

@Composable
fun ExampleScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        DialSlider(
            presets = PHOTO_PRESETS,
            initialPresetId = "highlights",
            backgroundColor = Color.Black,
            testTag = "dial-slider",
        )
    }
}
