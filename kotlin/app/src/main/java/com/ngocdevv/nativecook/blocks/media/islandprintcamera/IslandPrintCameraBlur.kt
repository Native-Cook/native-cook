package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import androidx.annotation.RequiresApi
import kotlin.math.ceil

/** Keep the animated Gaussian on the GPU at the display's native pixel density. */
@RequiresApi(31)
internal class IslandPrintCameraBlur {
    private val node = RenderNode("IslandPrintCameraLiveView")
    private val shadow = RenderNode("IslandPrintCameraPaperShadow")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, plate: Bitmap, destination: RectF, blur: Double, pixelsPerPoint: Float) {
        paint.color = -1
        val width = ceil(destination.width() * pixelsPerPoint).toInt()
        val height = ceil(destination.height() * pixelsPerPoint).toInt()
        node.setPosition(0, 0, width, height)
        val recording = node.beginRecording(width, height)
        recording.scale(pixelsPerPoint, pixelsPerPoint)
        recording.drawBitmap(plate, null, RectF(0f, 0f, destination.width(), destination.height()), paint)
        node.endRecording()
        val radius = (blur * pixelsPerPoint).toFloat()
        node.setRenderEffect(if (radius > 0) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null)
        val save = canvas.save()
        canvas.translate(destination.left, destination.top)
        canvas.scale(1 / pixelsPerPoint, 1 / pixelsPerPoint)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }

    fun shadow(canvas: Canvas, blur: Float, alpha: Float, pixelsPerPoint: Float) {
        val margin = blur * 4
        val width = ceil((206 + 2 * margin) * pixelsPerPoint).toInt()
        val height = ceil((247 + 2 * margin) * pixelsPerPoint).toInt()
        shadow.setPosition(0, 0, width, height)
        val recording = shadow.beginRecording(width, height)
        recording.scale(pixelsPerPoint, pixelsPerPoint)
        recording.translate(margin, margin)
        paint.color = 0x40120A10
        recording.drawRoundRect(2f, 4f, 204f, 249f, 3f, 3f, paint)
        shadow.endRecording()
        shadow.alpha = alpha
        shadow.setRenderEffect(RenderEffect.createBlurEffect(blur * pixelsPerPoint, blur * pixelsPerPoint, Shader.TileMode.DECAL))
        val save = canvas.save()
        canvas.translate(-margin, -margin)
        canvas.scale(1 / pixelsPerPoint, 1 / pixelsPerPoint)
        canvas.drawRenderNode(shadow)
        canvas.restoreToCount(save)
    }
}
