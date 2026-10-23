package com.ngocdevv.nativecook.blocks.cards.lenticulareditionpass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace

/** Four ordinary tagged-sRGB frozen Swift rasters, decoded once; no network or packed data. */
internal class LenticularEditionPassResources private constructor(val paper: Bitmap,val atlas: Bitmap,val art: List<Bitmap>) {
    companion object {
        @Volatile private var cached: LenticularEditionPassResources?=null
        fun load(context: Context): LenticularEditionPassResources=cached?:synchronized(this) {
            cached?:run {
                fun image(name: String): Bitmap {
                    val options=BitmapFactory.Options().apply {inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888;inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)}
                    return context.assets.open("lenticulareditionpass/lenticulareditionpass_$name.png").use {BitmapFactory.decodeStream(it,null,options)!!}.apply {density=Bitmap.DENSITY_NONE}
                }
                LenticularEditionPassResources(image("paper"),image("atlas"),listOf(image("art-a"),image("art-b"))).also {cached=it}
            }
        }
    }
}
