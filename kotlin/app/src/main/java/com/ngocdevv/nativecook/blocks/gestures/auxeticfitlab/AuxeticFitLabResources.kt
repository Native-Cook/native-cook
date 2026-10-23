package com.ngocdevv.nativecook.blocks.gestures.auxeticfitlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace

/** The four ordinary sRGB images are copied verbatim from the frozen Swift cache. */
internal class AuxeticFitLabResources private constructor(val plate: Bitmap, val tightShadow: Bitmap,
    val ambientShadow: Bitmap, val paper: Bitmap) {
    companion object {
        fun load(context: Context): AuxeticFitLabResources {
            fun bitmap(name: String): Bitmap {
                val options = BitmapFactory.Options().apply {
                    inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                }
                return context.assets.open("auxeticfitlab/auxeticfitlab_$name.png").use {
                    requireNotNull(BitmapFactory.decodeStream(it, null, options))
                }
            }
            return AuxeticFitLabResources(bitmap("plate"), bitmap("tight-shadow"), bitmap("ambient-shadow"), bitmap("paper"))
        }
    }
}
