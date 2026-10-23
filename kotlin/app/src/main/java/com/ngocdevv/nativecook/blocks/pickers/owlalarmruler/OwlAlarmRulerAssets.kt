package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal class OwlAlarmRulerAssets(context: Context) {
    val mascot: Bitmap = context.assets.open("owlalarmruler/owlalarmruler_mascot.png").use { stream ->
        BitmapFactory.decodeStream(stream)
    }
    val pupil: Bitmap = context.assets.open("owlalarmruler/owlalarmruler_mascot_pupil.png").use { stream ->
        BitmapFactory.decodeStream(stream)
    }
}
