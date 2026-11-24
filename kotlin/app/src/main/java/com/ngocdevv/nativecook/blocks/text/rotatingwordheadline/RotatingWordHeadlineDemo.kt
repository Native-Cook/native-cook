package com.ngocdevv.nativecook.blocks.text.rotatingwordheadline

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Cadence's landing hero: "Plan your week" with the bold word rolling through week, launch, wedding, sprint. */
@Composable
fun RotatingWordHeadlineDemo() {
    CadenceHero(autoPlay = true)
}

@Preview
@Composable
private fun RotatingWordHeadlineDemoPreview() {
    RotatingWordHeadlineDemo()
}
