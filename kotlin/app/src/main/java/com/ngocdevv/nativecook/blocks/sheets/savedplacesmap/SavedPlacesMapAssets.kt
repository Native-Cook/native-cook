package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface

internal class SavedPlacesMapAssets(context: Context) {
    val map: Bitmap = bitmap(context, "savedplacesmap_map.jpg")
    val cafe: Bitmap = bitmap(context, "savedplacesmap_list-cafe.jpg")
    val bar: Bitmap = bitmap(context, "savedplacesmap_list-bar.jpg")
    val avatar: Bitmap = bitmap(context, "savedplacesmap_avatar-sam.jpg")

    val archivo: Typeface = typeface(context, "savedplacesmap_Archivo-Regular.ttf")
    val archivoMedium: Typeface = typeface(context, "savedplacesmap_Archivo-Medium.ttf")
    val archivoSemiBold: Typeface = typeface(context, "savedplacesmap_Archivo-SemiBold.ttf")
    val archivoExtraBold: Typeface = typeface(context, "savedplacesmap_Archivo-ExtraBold.ttf")
    val barlow: Typeface = typeface(context, "savedplacesmap_Barlow-Regular.ttf")
    val barlowSemiBold: Typeface = typeface(context, "savedplacesmap_Barlow-SemiBold.ttf")
    val barlowItalic: Typeface = typeface(context, "savedplacesmap_Barlow-Italic.ttf")

    private fun bitmap(context: Context, name: String): Bitmap {
        return context.assets.open("savedplacesmap/$name").use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Unable to decode $name" }
        }
    }

    private fun typeface(context: Context, name: String): Typeface {
        return Typeface.createFromAsset(context.assets, "savedplacesmap/$name")
    }
}
