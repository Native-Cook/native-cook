package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import android.graphics.Bitmap
import android.graphics.Canvas

/** Reference points -> pixels of the current layout. */
internal data class StampDiaryCalendarSpace(val widthPx: Float, val heightPx: Float, val s: Double, val oyPx: Double) {
    fun x(ref: Double): Double = ref * s
    fun y(ref: Double): Double = oyPx + ref * s
}

/** Everything one stamp needs to be drawn. */
internal class StampDiaryCalendarStampArgs(
    val projection: StampDiaryCalendarProjection,
    val halfWidth: Double,
    val halfHeight: Double,
    val art: Bitmap,
    val mark: Bitmap?,
    val w: Double,
    val h: Double,
    val aa: Double,
    val dim: Double,
    val holes: Pair<Int, Int>,
    val shadow: Double = 0.0,
    val curl: StampDiaryCalendarMotion.Curl = StampDiaryCalendarMotion.FLAT,
    val sheenPos: Double = 0.0,
    val sheenAmt: Double = 0.0,
    val inkWet: Double = 0.0,
    val inkOn: Double = 0.0,
    val inkSpread: Double = 0.0,
    val inkThreshold: Double = 1.0,
    val opacity: Double = 1.0,
)

/** Draws the stamps and the actor's drop shadow on a native canvas, by AGSL (API 33+) or a Canvas fallback. */
internal interface StampDiaryCalendarPainter {
    /** Called once at the start of a frame (shaders are pooled per draw call). */
    fun begin()
    fun stamp(canvas: Canvas, space: StampDiaryCalendarSpace, a: StampDiaryCalendarStampArgs)
    fun shadow(canvas: Canvas, space: StampDiaryCalendarSpace, projection: StampDiaryCalendarProjection, w: Double, h: Double, sigma: Double, opacity: Double)
}

/** The part of the art the stamp shows (cover fit), in image pixels: x, y, w, h. */
internal fun stampDiaryCalendarCrop(w: Double, h: Double, side: Double): FloatArray {
    if (w < h) {
        val cw = side * w / h
        return floatArrayOf(((side - cw) / 2).toFloat(), 0f, cw.toFloat(), side.toFloat())
    }
    val ch = side * h / w
    return floatArrayOf(0f, ((side - ch) / 2).toFloat(), side.toFloat(), ch.toFloat())
}
