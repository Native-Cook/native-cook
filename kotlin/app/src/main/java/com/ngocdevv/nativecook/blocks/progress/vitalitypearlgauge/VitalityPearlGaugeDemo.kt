package com.ngocdevv.nativecook.blocks.progress.vitalitypearlgauge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun VitalityPearlGaugeDemo() {
    VitalityPearlGauge(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun VitalityPearlGaugeDemoPreview() {
    VitalityPearlGaugeDemo()
}
