package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.content.Context
import android.graphics.Typeface

/** The exact OFL typefaces bundled by the React Native block. */
internal class CassetteDeckAssets(context: Context) {
    val regular: Typeface = load(context, "cassettedeck_Inter-Regular.ttf")
    val medium: Typeface = load(context, "cassettedeck_Inter-Medium.ttf")
    val semibold: Typeface = load(context, "cassettedeck_Inter-SemiBold.ttf")
    val handwriting: Typeface = load(context, "cassettedeck_CoveredByYourGrace.ttf")

    private fun load(context: Context, name: String): Typeface {
        return Typeface.createFromAsset(context.assets, "cassettedeck/$name")
    }
}
