package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.ngocdevv.nativecook.R

/** The registered artwork of the source (RN `ART`), decoded once at its own pixel size. */
internal class CassetteDeckAssets(context: Context) {
    val cassette: Bitmap = load(context, R.drawable.cassettedeck_cassette)
    val reel: Bitmap = load(context, R.drawable.cassettedeck_reel)
    val keyRed: Bitmap = load(context, R.drawable.cassettedeck_key_red)
    val keyRedDown: Bitmap = load(context, R.drawable.cassettedeck_key_red_down)
    val keyDark: Bitmap = load(context, R.drawable.cassettedeck_key_dark)
    val keyDarkDown: Bitmap = load(context, R.drawable.cassettedeck_key_dark_down)
    val covers: List<Bitmap> = listOf(
        load(context, R.drawable.cassettedeck_cover_1),
        load(context, R.drawable.cassettedeck_cover_2),
        load(context, R.drawable.cassettedeck_cover_3),
    )

    private fun load(context: Context, @DrawableRes id: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = checkNotNull(BitmapFactory.decodeResource(context.resources, id, options)) {
            "Cassette Deck: drawable $id did not decode"
        }
        // Mipmaps keep the covers (336 px drawn at about 100 px) from shimmering when they slide.
        bitmap.setHasMipMap(true)
        return bitmap
    }
}
