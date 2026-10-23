package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.ngocdevv.nativecook.blocks.gestures.cassettedeck.CassetteDeckMotion.Chassis
import kotlin.math.roundToInt

/**
 * The five keys (RN `DeckKey`): an up cap and a down cap cross-fade as the key travels, and the whole row dips with
 * the chassis. Dp units.
 */
internal class CassetteDeckKeyPainter(
    private val geometry: CassetteDeckGeometry,
    private val assets: CassetteDeckAssets,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    fun draw(canvas: Canvas, sim: CassetteDeckSim) {
        val travel = CassetteDeckTheme.keys.travel
        val s = geometry.s
        val dip = sim.dip * Chassis.dipTravel * s
        for (index in 0 until CassetteDeckSim.KEYS) {
            val depth = sim.d[index]
            val red = index == 0
            val up = if (red) assets.keyRed else assets.keyDark
            val down = if (red) assets.keyRedDown else assets.keyDarkDown
            val upShift = if (sim.reduced) 0.0 else depth * travel * s + dip
            val downShift = if (sim.reduced) 0.0 else (depth - 1.0) * travel * s + dip
            drawCap(canvas, up, index, upShift, 1.0 - depth)
            drawCap(canvas, down, index, downShift, depth)
        }
    }

    private fun drawCap(canvas: Canvas, bitmap: android.graphics.Bitmap, index: Int, shift: Double, opacity: Double) {
        val alpha = (opacity * 255.0).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        val left = geometry.keyLeft(index).toFloat()
        val top = (geometry.keyTop + shift).toFloat()
        rect.set(left, top, left + geometry.keyWidth.toFloat(), top + geometry.keyHeight.toFloat())
        paint.alpha = alpha
        canvas.drawBitmap(bitmap, null, rect, paint)
    }
}
