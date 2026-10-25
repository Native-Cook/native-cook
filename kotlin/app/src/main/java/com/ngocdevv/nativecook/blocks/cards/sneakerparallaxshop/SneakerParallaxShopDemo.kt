package com.ngocdevv.nativecook.blocks.cards.sneakerparallaxshop

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

@Composable
fun SneakerParallaxShopDemo() {
    val snapshot = LocalActivity.current?.intent?.getStringExtra("snapshot-time")?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    SneakerParallaxShop(autoPlay = true, previewTime = snapshot)
}
