package com.ngocdevv.nativecook.blocks.progress.healthagedial

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun HealthAgeDialDemo() {
    HealthAgeDial(
        modifier = Modifier.fillMaxSize(),
        autoPlay = true,
    )
}

@Preview
@Composable
private fun HealthAgeDialDemoPreview() {
    HealthAgeDialDemo()
}
