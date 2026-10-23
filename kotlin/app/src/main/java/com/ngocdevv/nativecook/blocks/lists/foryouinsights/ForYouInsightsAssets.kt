package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class ForYouInsightsAssets(
    val flats: List<ImageBitmap>,
    val layers: List<List<ImageBitmap>>,
)

fun loadForYouInsightsAssets(context: Context): ForYouInsightsAssets {
    val keys = ForYouInsightsContent.articles.map { article -> article.key }
    return ForYouInsightsAssets(
        flats = keys.map { key -> decodeForYouInsightsAsset(context, "foryouinsights_$key.jpg") },
        layers = keys.map { key ->
            listOf(
                decodeForYouInsightsAsset(context, "foryouinsights_${key}_l0.jpg"),
                decodeForYouInsightsAsset(context, "foryouinsights_${key}_l1.webp"),
                decodeForYouInsightsAsset(context, "foryouinsights_${key}_l2.webp"),
            )
        },
    )
}

private fun decodeForYouInsightsAsset(context: Context, name: String): ImageBitmap {
    return context.assets.open("foryouinsights/$name").use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream)) {
            "Unable to decode local For You Insights asset: $name"
        }.asImageBitmap()
    }
}
