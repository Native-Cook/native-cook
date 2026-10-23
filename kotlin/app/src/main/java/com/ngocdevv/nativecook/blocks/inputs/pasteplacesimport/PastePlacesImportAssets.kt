package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build

internal class PastePlacesImportAssets(context: Context) {
    private val assets = context.assets

    val textTypeface: Typeface = Typeface.createFromAsset(assets, PastePlacesImportTheme.Font.text)
    val extraTypeface: Typeface = Typeface.createFromAsset(assets, PastePlacesImportTheme.Font.extra)
    val systemExtraBold: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 800, false)
    } else {
        Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    val system: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    val notes: Bitmap = bitmap("pasteplacesimport_icon-notes.png")
    val chat: Bitmap = bitmap("pasteplacesimport_icon-chat.png")
    val doc: Bitmap = bitmap("pasteplacesimport_icon-doc.png")

    private fun bitmap(name: String): Bitmap {
        return assets.open("pasteplacesimport/$name").use { stream ->
            requireNotNull(
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ),
            ) { "Unable to decode Paste Places Import asset: $name" }
        }
    }
}
