package com.ngocdevv.nativecook.blocks.onboarding.pricetagwelcome

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal class PriceTagWelcomeAssets private constructor(
    val art: Map<String, ImageBitmap>,
    val shadows: Map<String, ImageBitmap>,
    val cardIcon: ImageBitmap,
) {
    companion object {
        fun load(context: Context): PriceTagWelcomeAssets {
            fun bitmap(name: String): ImageBitmap {
                return context.assets.open("pricetagwelcome/pricetagwelcome_$name.png").use { stream ->
                    BitmapFactory.decodeStream(stream).asImageBitmap()
                }
            }

            val keys = PriceTagWelcomeTheme.items.map { it.key }
            return PriceTagWelcomeAssets(
                art = keys.associateWith(::bitmap),
                shadows = keys.associateWith { key -> bitmap("${key}_shadow") },
                cardIcon = bitmap("card_icon"),
            )
        }
    }
}

@Composable
internal fun rememberPriceTagWelcomeAssets(context: Context): PriceTagWelcomeAssets {
    return remember(context) { PriceTagWelcomeAssets.load(context) }
}
