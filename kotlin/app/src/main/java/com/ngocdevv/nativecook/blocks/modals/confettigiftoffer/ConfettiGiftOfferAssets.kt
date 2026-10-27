package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/** The bundled raccoon art and the headline font, decoded once (assets/confettigiftoffer/). */
internal class ConfettiGiftOfferAssets(context: Context) {
    val hearts: ImageBitmap = bitmap(context, "raccoon-hearts.webp")
    val worried: ImageBitmap = bitmap(context, "raccoon-worried.webp")
    val love: ImageBitmap = bitmap(context, "raccoon-love.webp")
    val tail: ImageBitmap = bitmap(context, "raccoon-tail.webp")

    /** Barlow Condensed ExtraBold (SIL OFL 1.1), the reference's condensed headline face. */
    val headline: FontFamily = FontFamily(
        Font(path = "confettigiftoffer/confettigiftoffer_BarlowCondensed-ExtraBold.ttf", assetManager = context.assets),
    )

    private fun bitmap(context: Context, name: String): ImageBitmap {
        context.assets.open("confettigiftoffer/confettigiftoffer_$name").use { stream ->
            val decoded = checkNotNull(BitmapFactory.decodeStream(stream)) { "Cannot decode Confetti Gift Offer asset: $name" }
            return decoded.asImageBitmap()
        }
    }
}

@Composable
internal fun rememberConfettiGiftOfferAssets(): ConfettiGiftOfferAssets {
    val context = LocalContext.current
    return remember(context) { ConfettiGiftOfferAssets(context) }
}
