package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TasteProfileDemo() {
    TasteProfile(autoPlay = true)
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun TasteProfileDemoPreview() {
    TasteProfileDemo()
}
