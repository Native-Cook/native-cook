package com.ngocdevv.nativecook.blocks.onboarding.lampwishcompanion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun LampWishCompanionDemo() {
    LampWishCompanion(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun LampWishCompanionDemoPreview() {
    LampWishCompanionDemo()
}
