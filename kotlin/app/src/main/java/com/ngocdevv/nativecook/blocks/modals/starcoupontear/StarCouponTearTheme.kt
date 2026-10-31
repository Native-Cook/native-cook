package com.ngocdevv.nativecook.blocks.modals.starcoupontear

import android.content.Context
import android.graphics.Typeface as AndroidTypeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface

internal object StarCouponTearTheme {
    val page=Color(0xFF15123A)
    val mute=Color(0xFFA49FD0)
    val pill=Color(0xFF262150)
    val line=Color(0xFF3A3470)
    val gold=Color(0xFFFFD23F)
    val goldInk=Color(0xFF2B1E00)
    fun font(context: Context,weight: String) = FontFamily(Typeface(
        AndroidTypeface.createFromAsset(context.assets,"starcoupontear/starcoupontear_Inter-$weight.ttf")))
}
