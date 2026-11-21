@file:Suppress("DEPRECATION")

package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

/** Native Gaussian fallback for API 26–30, where RenderEffect is unavailable. */
internal class IslandPrintCameraLegacyBlur(context: Context, private val plate: Bitmap) {
    private val rs = RenderScript.create(context)
    private val filter = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    private val image = Bitmap.createScaledBitmap(plate, 512, 512, true)
    private val imageOut = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    private val input = Allocation.createFromBitmap(rs, image)
    private val output = Allocation.createTyped(rs, input.type)
    private val shadow = Bitmap.createBitmap(366, 407, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawRoundRect(82f, 84f, 284f, 329f, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40120A10 })
    }
    private val shadowOut = Bitmap.createBitmap(366, 407, Bitmap.Config.ARGB_8888)
    private val shadowIn = Allocation.createFromBitmap(rs, shadow)
    private val shadowOutput = Allocation.createTyped(rs, shadowIn.type)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var lastRadius = -1f
    private var lastShadow = -1f

    fun draw(canvas: Canvas, destination: RectF, radius: Double) {
        paint.alpha = 255
        if (radius <= 0) { canvas.drawBitmap(plate, null, destination, paint); return }
        val sampleRadius = (radius * 512 / destination.width()).toFloat().coerceIn(.001f, 25f)
        if (sampleRadius != lastRadius) {
            filter.setRadius(sampleRadius)
            filter.setInput(input)
            filter.forEach(output)
            output.copyTo(imageOut)
            lastRadius = sampleRadius
        }
        canvas.drawBitmap(imageOut, null, destination, paint)
    }

    fun shadow(canvas: Canvas, radius: Float, alpha: Float) {
        if (radius != lastShadow) {
            filter.setRadius(radius.coerceIn(.001f, 25f))
            filter.setInput(shadowIn)
            filter.forEach(shadowOutput)
            shadowOutput.copyTo(shadowOut)
            lastShadow = radius
        }
        paint.alpha = (alpha * 255).toInt()
        canvas.drawBitmap(shadowOut, null, RectF(-80f, -80f, 286f, 327f), paint)
        paint.alpha = 255
    }

    fun close() {
        input.destroy(); output.destroy(); shadowIn.destroy(); shadowOutput.destroy(); filter.destroy(); rs.destroy()
    }
}
