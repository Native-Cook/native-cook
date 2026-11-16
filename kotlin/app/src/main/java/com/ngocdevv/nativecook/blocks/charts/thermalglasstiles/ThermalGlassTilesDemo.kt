package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** LOOP = [ThermalGlassTilesMotion.LOOP] s. */
@Composable
fun ThermalGlassTilesDemo() {
    ThermalGlassTiles(autoPlay = true)
}

@Preview
@Composable
private fun ThermalGlassTilesDemoPreview() {
    ThermalGlassTilesDemo()
}

