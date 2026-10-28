package com.ngocdevv.nativecook.blocks.pickers.keypadthemewave

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The theme step on the whole screen, sweeping through four themes by itself. */
@Composable
fun KeypadThemeWaveDemo() {
    KeypadThemeWave(autoPlay = true)
}

@Preview
@Composable
private fun KeypadThemeWaveDemoPreview() {
    KeypadThemeWaveDemo()
}
