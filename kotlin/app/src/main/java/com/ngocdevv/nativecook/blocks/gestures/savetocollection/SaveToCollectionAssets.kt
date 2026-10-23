package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Typeface

internal class SaveToCollectionAssets(context: Context) {
    val photos: List<Bitmap> = SaveToCollectionTokens.Photos.map { photo ->
        loadBitmap(context, "${SaveToCollectionTokens.AssetRoot}/savetocollection_${photo.asset}.jpg")
    }
    val regular: Typeface = Typeface.createFromAsset(
        context.assets,
        "${SaveToCollectionTokens.AssetRoot}/savetocollection_Inter-Regular.ttf",
    )
    val medium: Typeface = Typeface.createFromAsset(
        context.assets,
        "${SaveToCollectionTokens.AssetRoot}/savetocollection_Inter-Medium.ttf",
    )
    val semibold: Typeface = Typeface.createFromAsset(
        context.assets,
        "${SaveToCollectionTokens.AssetRoot}/savetocollection_Inter-SemiBold.ttf",
    )

    private fun loadBitmap(context: Context, path: String): Bitmap {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        return requireNotNull(context.assets.open(path).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }) { "Missing SaveToCollection asset $path" }.apply {
            density = Bitmap.DENSITY_NONE
            prepareToDraw()
        }
    }
}
