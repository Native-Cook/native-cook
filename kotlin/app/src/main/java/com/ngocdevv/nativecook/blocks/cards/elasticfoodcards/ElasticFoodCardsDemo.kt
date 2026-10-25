package com.ngocdevv.nativecook.blocks.cards.elasticfoodcards

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

@Composable
fun ElasticFoodCardsDemo() {
    val raw = LocalActivity.current?.intent?.getStringExtra("snapshot-time")?.toDoubleOrNull()
    val snapshot = raw?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    ElasticFoodCards(autoPlay = true, previewTime = snapshot)
}
