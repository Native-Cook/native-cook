package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

import android.graphics.Matrix
import android.graphics.Paint

internal object OwlAlarmRulerPaintCache {
    val bitmap = Paint(
        Paint.ANTI_ALIAS_FLAG or
            Paint.FILTER_BITMAP_FLAG or
            Paint.DITHER_FLAG,
    )
    val matrix = Matrix()
    val matrixValues = FloatArray(9)
}
