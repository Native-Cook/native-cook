package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface

internal class TasteProfileAssets(context: Context) {
    private val assets = context.assets

    val bodyTypeface: Typeface = Typeface.createFromAsset(assets, "tasteprofile/tasteprofile_anybody_body.ttf")
    val semiTypeface: Typeface = Typeface.createFromAsset(assets, "tasteprofile/tasteprofile_anybody_semibold.ttf")
    val extraTypeface: Typeface = Typeface.createFromAsset(assets, "tasteprofile/tasteprofile_anybody_extrabold.ttf")

    val profile = bitmap("tasteprofile_profile.jpg")
    val mutual1 = bitmap("tasteprofile_mutual_1.jpg")
    val mutual2 = bitmap("tasteprofile_mutual_2.jpg")
    /** The strip's photos: the pin's four, then six per city (STRIP_PHOTOS indexes this list). */
    val photos: List<Bitmap> = buildList {
        for (i in 1..4) add(bitmap("tasteprofile_grid_$i.jpg"))
        for (city in listOf("ny", "ld", "pa")) for (i in 1..6) add(bitmap("tasteprofile_${city}_$i.webp"))
    }
    val big1 = bitmap("tasteprofile_grid_5.jpg")
    val big2 = bitmap("tasteprofile_grid_6.jpg")
    val coin = bitmap("tasteprofile_coin.webp")
    val gem = bitmap("tasteprofile_gem.webp")

    fun typeface(font: TasteProfileFont): Typeface = when (font) {
        TasteProfileFont.BODY -> bodyTypeface
        TasteProfileFont.SEMI -> semiTypeface
        TasteProfileFont.EXTRA -> extraTypeface
    }

    private fun bitmap(name: String): Bitmap = assets.open("tasteprofile/$name").use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream)) { "Unable to decode taste-profile asset $name" }
    }
}
