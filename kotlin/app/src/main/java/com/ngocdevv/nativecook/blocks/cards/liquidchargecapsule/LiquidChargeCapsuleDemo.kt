package com.ngocdevv.nativecook.blocks.cards.liquidchargecapsule

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

@Composable
fun LiquidChargeCapsuleDemo() {
    val snapshot = LocalActivity.current?.intent?.getStringExtra("snapshot-time")?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    LiquidChargeCapsule(autoPlay = true, previewTime = snapshot)
}
