package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Seven locally bundled photographs, decoded once for a carousel instance. */
internal class GrassTimerCarouselAssets(context: Context) {
    private val images = AssetNames.associateWith { name ->
        context.assets.open("grasstimercarousel/grasstimercarousel_$name.jpg").use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream)) { "Unable to decode Grass Timer asset: $name" }
                .asImageBitmap()
        }
    }

    operator fun get(name: String): ImageBitmap = checkNotNull(images[name])

    private companion object {
        val AssetNames = listOf(
            "card-grass",
            "card-quiet",
            "orb-ocean",
            "orb-waves",
            "orb-airplane",
            "story-mountains",
            "story-clouds",
        )
    }
}
