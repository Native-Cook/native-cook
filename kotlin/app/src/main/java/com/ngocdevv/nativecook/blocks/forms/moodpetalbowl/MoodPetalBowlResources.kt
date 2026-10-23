package com.ngocdevv.nativecook.blocks.forms.moodpetalbowl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Exact ordinary sRGB rasters exported once from the frozen Swift renderer. */
internal class MoodPetalBowlResources private constructor(val tokens: List<Bitmap>,val paper: Bitmap,val header: Bitmap,
    val sharpStrip: Bitmap,val softStrip: Bitmap) {
    companion object {
        fun load(context: Context): MoodPetalBowlResources {
            fun read(name: String): Bitmap = context.assets.open("moodpetalbowl/moodpetalbowl_$name.png").use {
                requireNotNull(BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888 })) }
            return MoodPetalBowlResources(listOf("calm","low","bright","tense").map { read("token-$it") },read("paper"),read("header"),read("sharp-strip"),read("soft-strip"))
        }
    }
}
