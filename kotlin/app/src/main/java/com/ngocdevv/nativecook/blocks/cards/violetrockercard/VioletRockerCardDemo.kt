package com.ngocdevv.nativecook.blocks.cards.violetrockercard

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

@Composable
fun VioletRockerCardDemo() {
    val raw = LocalActivity.current?.intent?.getStringExtra("snapshot-time")?.toDoubleOrNull()
    VioletRockerCard(autoPlay = true, previewTime = raw?.takeIf { it.isFinite() }?.coerceAtLeast(0.0))
}
