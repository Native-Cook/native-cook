package com.ngocdevv.nativecook.blocks.charts.prismwindowchart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PrismWindowChartDemo() {
    PrismWindowChart(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun PrismWindowChartDemoPreview() {
    PrismWindowChartDemo()
}
