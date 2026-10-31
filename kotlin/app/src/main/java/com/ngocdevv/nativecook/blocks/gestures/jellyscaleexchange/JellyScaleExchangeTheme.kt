package com.ngocdevv.nativecook.blocks.gestures.jellyscaleexchange

import android.content.Context
import android.graphics.Typeface

internal class JellyScaleExchangeTheme(context: Context) {
    val regular: Typeface=Typeface.createFromAsset(context.assets,"jellyscaleexchange/jellyscaleexchange_Inter-Regular.ttf")
    val medium: Typeface=Typeface.createFromAsset(context.assets,"jellyscaleexchange/jellyscaleexchange_Inter-Medium.ttf")
    val semibold: Typeface=Typeface.createFromAsset(context.assets,"jellyscaleexchange/jellyscaleexchange_Inter-SemiBold.ttf")
    companion object {
        val PAGE=0xFF14122B.toInt(); val INK=0xFFF3F1FF.toInt(); val MUTE=0xFF9894B8.toInt()
        val CARD=0xFF211E3D.toInt(); val LINE=0xFF34305A.toInt(); val ACCENT=0xFFFFC53D.toInt()
        val ACCENT_INK=0xFF2A1D00.toInt(); val LCD=0xFF9CF2C5.toInt()
    }
}
