package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import android.content.res.AssetManager
import android.graphics.Typeface

internal class DragMultiSelectAssets(assets: AssetManager) {
    val interLight: Typeface = Typeface.createFromAsset(assets, "$ROOT/dragmultiselect_Inter-Light.ttf")
    val interRegular: Typeface = Typeface.createFromAsset(assets, "$ROOT/dragmultiselect_Inter-Regular.ttf")
    val interMedium: Typeface = Typeface.createFromAsset(assets, "$ROOT/dragmultiselect_Inter-Medium.ttf")
    val interSemibold: Typeface = Typeface.createFromAsset(assets, "$ROOT/dragmultiselect_Inter-SemiBold.ttf")
    val dotoBlack: Typeface = Typeface.createFromAsset(assets, "$ROOT/dragmultiselect_Doto-Black.ttf")

    private companion object {
        const val ROOT = "dragmultiselect"
    }
}
