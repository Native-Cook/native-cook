package com.ngocdevv.nativecook.blocks.media.lumarunstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal class LumaRunStudioResources(private val context:Context) {
    private val images=HashMap<String,Bitmap>()
    private fun load(name:String):Bitmap=images.getOrPut(name){ context.assets.open("luma_run_studio/luma_run_studio_"+name).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888})!! } }
    val canonical=load("canonical.png")
    val frame=load("frame.png")
    val mask=load("roi_mask.png")
    fun loop():List<LumaRunStudioBitmap> = names.mapIndexed {index,name->LumaRunStudioBitmap(load(name),LumaRunStudioMath.knots[index].params)}
    companion object { val names=arrayOf(
        "canonical.png",
        "canonical.png",
        "knot_02.png",
        "knot_03.png",
        "knot_04.png",
        "knot_05.png",
        "knot_06.png",
        "knot_07.png",
        "knot_08.png",
        "knot_09.png",
        "knot_10.png",
        "knot_11.png",
        "knot_12.png",
        "knot_13.png",
        "knot_14.png",
        "knot_15.png",
        "knot_16.png",
        "knot_17.png",
        "knot_18.png",
        "knot_19.png",
        "knot_20.png",
        "knot_21.png",
        "knot_22.png",
        "knot_23.png",
        "knot_24.png",
        "knot_25.png",
        "knot_26.png",
        "knot_27.png",
        "knot_28.png",
        "knot_29.png",
        "knot_30.png",
        "knot_31.png",
        "knot_32.png",
        "knot_33.png",
        "knot_34.png",
        "knot_35.png",
        "knot_36.png",
        "knot_37.png",
        "knot_38.png",
        "knot_39.png",
        "knot_40.png",
        "knot_41.png",
        "knot_42.png",
        "knot_43.png",
        "knot_43.png",
        "knot_43.png",
        "knot_46.png",
        "knot_47.png",
        "knot_48.png",
        "knot_49.png",
        "knot_50.png",
        "knot_51.png",
        "knot_52.png",
        "knot_53.png",
        "knot_54.png",
        "knot_55.png",
        "knot_56.png",
        "knot_57.png",
        "knot_58.png",
        "knot_59.png",
        "canonical.png",
        "canonical.png",
        "canonical.png",
        "canonical.png"
    ) }
}
