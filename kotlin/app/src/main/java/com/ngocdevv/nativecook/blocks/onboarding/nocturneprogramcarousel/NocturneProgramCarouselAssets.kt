package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Where the bundled artwork lives: `assets/nocturneprogramcarousel/nocturneprogramcarousel_<name>`. */
private const val GUIDE_ASSET = "nocturneprogramcarousel/nocturneprogramcarousel_evening-guide.webp"
private const val HOLOGRAM_ASSET = "nocturneprogramcarousel/nocturneprogramcarousel_body-hologram.webp"

/** The two pictures of the block: `evening-guide.webp` (560 by 747, with transparency) and `body-hologram.webp` (600 by 900). */
internal class NocturneProgramCarouselImages(
    val guide: ImageBitmap,
    val hologram: ImageBitmap,
)

/** Decodes both pictures once at their own size in sRGB. */
internal fun loadNocturneProgramCarouselImages(context: Context): NocturneProgramCarouselImages =
    NocturneProgramCarouselImages(
        guide = decode(context, GUIDE_ASSET),
        hologram = decode(context, HOLOGRAM_ASSET),
    )

private fun decode(context: Context, path: String): ImageBitmap {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        inScaled = false
    }
    val bitmap = context.assets.open(path).use { stream ->
        checkNotNull(BitmapFactory.decodeStream(stream, null, options)) { "Cannot decode $path" }
    }
    bitmap.prepareToDraw()
    return bitmap.asImageBitmap()
}
