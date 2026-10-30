package com.ngocdevv.nativecook.blocks.cards.snowcloudforecast

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The weather screen draws its own background and handles both safe areas. */
@Composable
fun SnowCloudForecastDemo() {
    SnowCloudForecast(autoPlay = true)
}

@Preview
@Composable
private fun SnowCloudForecastDemoPreview() {
    SnowCloudForecastDemo()
}
