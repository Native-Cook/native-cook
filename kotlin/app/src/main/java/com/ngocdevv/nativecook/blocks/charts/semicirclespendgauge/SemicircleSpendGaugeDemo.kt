package com.ngocdevv.nativecook.blocks.charts.semicirclespendgauge

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen with its own backdrop, so it is shown without a stage. */
@Composable
fun SemicircleSpendGaugeDemo() {
    SemicircleSpendGauge(autoPlay = true)
}

@Preview
@Composable
private fun SemicircleSpendGaugeDemoPreview() {
    SemicircleSpendGaugeDemo()
}
