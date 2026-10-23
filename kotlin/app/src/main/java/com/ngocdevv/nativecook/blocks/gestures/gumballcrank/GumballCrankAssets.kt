package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import android.content.Context
import android.graphics.Typeface

internal class GumballCrankAssets(context: Context) {
    val serif: Typeface = Typeface.createFromAsset(context.assets, "gumballcrank/gumballcrank_InstrumentSerif-Regular.ttf")
    val regular: Typeface = Typeface.createFromAsset(context.assets, "gumballcrank/gumballcrank_Inter-Regular.ttf")
    val medium: Typeface = Typeface.createFromAsset(context.assets, "gumballcrank/gumballcrank_Inter-Medium.ttf")
    val semibold: Typeface = Typeface.createFromAsset(context.assets, "gumballcrank/gumballcrank_Inter-SemiBold.ttf")
}
