package com.ngocdevv.nativecook.blocks.forms.selfieverifybadge

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

internal class SelfieVerifyBadgeTheme(context: Context) {
    val regular=Typeface.createFromAsset(context.assets,"selfieverifybadge/selfieverifybadge_Inter-Regular.ttf")
    val semibold=Typeface.createFromAsset(context.assets,"selfieverifybadge/selfieverifybadge_Inter-SemiBold.ttf")
    val bold=Typeface.createFromAsset(context.assets,"selfieverifybadge/selfieverifybadge_Inter-Bold.ttf")
    companion object {
        val DARK=Color.rgb(18,18,18); val TITLE=Color.rgb(245,245,245);val BODY=Color.rgb(154,154,154)
        val INK=Color.rgb(20,20,20);val INK_BODY=Color.rgb(107,107,112);val LIME=Color.rgb(166,242,61)
        val LIME_INK=Color.rgb(22,33,10);val BUTTON=Color.rgb(42,42,42);val BRACKET=Color.rgb(232,232,232)
    }
}
