package com.ngocdevv.nativecook.blocks.cards.fruitsmoothiecarousel

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

@Composable
fun FruitSmoothieCarouselDemo() {
    val snapshot = LocalActivity.current?.intent?.getStringExtra("snapshot-time")
        ?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    FruitSmoothieCarousel(autoPlay = true, previewTime = snapshot)
}
