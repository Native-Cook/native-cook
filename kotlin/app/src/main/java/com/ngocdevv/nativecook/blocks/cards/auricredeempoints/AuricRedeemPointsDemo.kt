package com.ngocdevv.nativecook.blocks.cards.auricredeempoints

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AuricRedeemPointsDemo() {
    AuricRedeemPoints(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun AuricRedeemPointsDemoPreview() {
    AuricRedeemPointsDemo()
}
