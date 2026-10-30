package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

import android.graphics.Path

/** Keep double-precision formulae until the native Canvas boundary. */
internal class GreatWaveMorningPath {
    val value = Path()

    fun moveTo(x: Number, y: Number) = value.moveTo(x.toFloat(), y.toFloat())

    fun lineTo(x: Number, y: Number) = value.lineTo(x.toFloat(), y.toFloat())

    fun cubicTo(x1: Number, y1: Number, x2: Number, y2: Number, x3: Number, y3: Number) = value.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat())

    fun close() = value.close()
}
