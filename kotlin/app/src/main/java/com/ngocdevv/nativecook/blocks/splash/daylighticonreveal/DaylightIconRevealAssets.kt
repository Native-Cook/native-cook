package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace

/** Where the bundled icon artwork lives: `assets/daylighticonreveal/daylighticonreveal_<name>`. */
private const val ICON_ASSET = "daylighticonreveal/daylighticonreveal_daylight-icon.webp"

/**
 * The icon artwork (`assets/daylight-icon.webp`, 1254 by 1254 with transparency), decoded once at
 * its own size in sRGB and with mipmaps, so it stays smooth while the icon shrinks into place.
 */
internal fun loadDaylightIconRevealBitmap(context: Context): Bitmap {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        inScaled = false
    }
    val bitmap = context.assets.open(ICON_ASSET).use { stream ->
        checkNotNull(BitmapFactory.decodeStream(stream, null, options)) { "Cannot decode the bundled Daylight icon" }
    }
    bitmap.setHasMipMap(true)
    bitmap.prepareToDraw()
    return bitmap
}
