package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace

internal class DragCardToSendAssets(context: Context) {
    private val assets = context.assets

    val portraits: Map<String, Bitmap> = DragCardToSendTokens.People.associate { person ->
        person.id to decode(person.asset)
    }
    val header: Bitmap = decode(DragCardToSendTokens.HeaderAsset)

    private fun decode(path: String): Bitmap {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        return requireNotNull(assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }) {
            "Unable to decode $path"
        }.apply {
            density = Bitmap.DENSITY_NONE
        }
    }
}
