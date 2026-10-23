package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PulseHeartCardDemo() {
    PulseHeartCard(autoPlay = true)
}

@Preview
@Composable
private fun PulseHeartCardDemoPreview() {
    PulseHeartCardDemo()
}
