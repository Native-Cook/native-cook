package com.ngocdevv.nativecook.blocks.cards.cityeventhero

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal class CityEventHeroAssets(private val context: Context) {
    private fun bitmap(name: String): Bitmap = context.assets.open("cityeventhero/$name").use { stream ->
        val options = BitmapFactory.Options().apply {
            inScaled = false
        }
        BitmapFactory.decodeStream(stream, null, options) ?: error("Could not decode $name")
    }

    val cityBitmap: Bitmap = bitmap("cityeventhero_city.jpg")
    val city: ImageBitmap = cityBitmap.asImageBitmap()
    val guestBitmaps = (1..7).map { number -> bitmap("cityeventhero_guest-$number.jpg") }
    val guests: List<ImageBitmap> = guestBitmaps.map { it.asImageBitmap() }
    val viewer: Bitmap = guestBitmaps[3]
    val headline: Typeface = Typeface.createFromAsset(context.assets, "cityeventhero/cityeventhero_BricolageHeadline.ttf")
    val wordmark: Typeface = Typeface.createFromAsset(context.assets, "cityeventhero/cityeventhero_ArchivoWordmark.ttf")
    val fill: Bitmap = bitmap("cityeventhero_fill.jpg")
    val tower: Bitmap = bitmap("cityeventhero_tower.png")
    val near: Bitmap = bitmap("cityeventhero_near.png")
    val backDepth: Bitmap = bitmap("cityeventhero_backdepth.png")
    val depth: Bitmap = bitmap("cityeventhero_depth.png")
    val lights: Bitmap = bitmap("cityeventhero_lights.png")
    val key: Bitmap = bitmap("cityeventhero_key.png")
    val bloom: Bitmap = bitmap("cityeventhero_bloom.png")
    val bloomKey: Bitmap = bitmap("cityeventhero_bloomkey.png")
    val facade: Bitmap = bitmap("cityeventhero_facade.png")
    val glyphs: Bitmap = bitmap("cityeventhero_glyphs.png")
    val fillImage: ImageBitmap = fill.asImageBitmap()
    val towerImage: ImageBitmap = tower.asImageBitmap()
    val nearImage: ImageBitmap = near.asImageBitmap()
    val towerLayer: ImageBitmap = maskedPhoto(tower).asImageBitmap()
    val nearLayer: ImageBitmap = maskedPhoto(near).asImageBitmap()
    val lightsImage: ImageBitmap = lights.asImageBitmap()
    val bloomImage: ImageBitmap = bloom.asImageBitmap()
    val sparkGlow by lazy { CityEventHeroSparkGlowPainter() }
    val fallbackFacade by lazy { CityEventHeroFallbackFacadePainter(this) }

    private fun maskedPhoto(mask: Bitmap): Bitmap {
        // These are opaque grayscale maps: luminance, not their PNG alpha, is coverage.
        val pixels = IntArray(cityBitmap.width * cityBitmap.height)
        val coverage = IntArray(pixels.size)
        cityBitmap.getPixels(pixels, 0, cityBitmap.width, 0, 0, cityBitmap.width, cityBitmap.height)
        mask.getPixels(coverage, 0, mask.width, 0, 0, mask.width, mask.height)
        for (index in pixels.indices) {
            pixels[index] = (pixels[index] and 0x00FFFFFF) or ((coverage[index] and 0xFF) shl 24)
        }
        return Bitmap.createBitmap(pixels, cityBitmap.width, cityBitmap.height, Bitmap.Config.ARGB_8888)
    }
}
