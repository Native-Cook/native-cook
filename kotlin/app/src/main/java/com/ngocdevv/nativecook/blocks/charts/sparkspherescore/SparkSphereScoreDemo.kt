package com.ngocdevv.nativecook.blocks.charts.sparkspherescore

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The loop opens at the pin's rest (0.8x, score 29.9), the finger lands on the ruler at 0.6 s and the sphere runs hot, then cold. */
@Composable
fun SparkSphereScoreDemo() {
    SparkSphereScore(autoPlay = true)
}

@Preview
@Composable
private fun SparkSphereScoreDemoPreview() {
    SparkSphereScoreDemo()
}
