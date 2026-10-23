package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.content.res.AssetManager
import android.graphics.Typeface

internal class HoldDragResizeAssets(assets: AssetManager) {
    val regular: Typeface = Typeface.createFromAsset(assets, "$Root/holddragresize_Inter-Regular.ttf")
    val medium: Typeface = Typeface.createFromAsset(assets, "$Root/holddragresize_Inter-Medium.ttf")
    val semibold: Typeface = Typeface.createFromAsset(assets, "$Root/holddragresize_Inter-SemiBold.ttf")
    val mono: Typeface = Typeface.createFromAsset(assets, "$Root/holddragresize_JetBrainsMono-Medium.ttf")
    val monoBold: Typeface = Typeface.createFromAsset(assets, "$Root/holddragresize_JetBrainsMono-Bold.ttf")

    private companion object {
        const val Root = "holddragresize"
    }
}
