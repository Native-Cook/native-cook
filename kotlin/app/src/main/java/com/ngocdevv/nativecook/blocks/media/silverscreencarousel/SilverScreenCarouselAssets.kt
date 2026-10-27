package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import java.io.IOException

/** Where the bundled files live: `assets/silverscreencarousel/silverscreencarousel_<name>`. */
private const val ASSET_DIRECTORY = "silverscreencarousel"
private const val SERIF_FILE = "silverscreencarousel_SourceSerif4-Regular.ttf"

/**
 * One bundled photograph (`assets/<name>.jpg` of the React Native block, 680 or 800 px square),
 * decoded at its own size in sRGB, or null if it cannot be read. Call it off the main thread.
 */
internal fun loadSilverScreenCarouselPhoto(context: Context, name: String): ImageBitmap? {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        inScaled = false
    }
    try {
        val bitmap = context.assets.open("$ASSET_DIRECTORY/$name").use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return bitmap?.asImageBitmap()
    } catch (error: IOException) {
        return null
    }
}

/**
 * Source Serif 4 Regular, the heading face (`useFonts`, SilverScreenCarousel.tsx:62). If the file
 * cannot be read the system serif takes over, as the React Native style sheet does on Android
 * (`fontFamily: 'serif'`, line 124).
 */
internal fun loadSilverScreenCarouselSerif(context: Context): FontFamily {
    try {
        val typeface = Typeface.createFromAsset(context.assets, "$ASSET_DIRECTORY/$SERIF_FILE")
        return FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
    } catch (error: RuntimeException) {
        return FontFamily.Serif
    }
}
