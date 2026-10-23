package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface

internal class TearAssets(context: Context) {
    private val assets = context.assets
    val packMaps = bitmap("pack-maps.png")
    val packPrint = bitmap("pack-print.png")
    val cardFace = bitmap("card-face.png")
    val cardBack = bitmap("card-back.png")
    val cardMaps = bitmap("card-maps.png")
    val tabLabel = bitmap("tab-label.png")
    val spark = bitmap("spark.png")
    val font: Typeface = Typeface.createFromAsset(assets, "foilpacktear/foilpacktear_ArchivoExpanded-Regular.ttf")

    private fun bitmap(name: String): Bitmap {
        return assets.open("foilpacktear/foilpacktear_$name").use {
            requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }))
        }
    }
}
