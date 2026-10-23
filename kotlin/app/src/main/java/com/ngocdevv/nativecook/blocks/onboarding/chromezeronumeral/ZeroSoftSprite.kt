package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import android.graphics.*
import android.os.Build
import kotlin.math.ceil

internal class ZeroSoftSprite(private val bitmap: Bitmap, private val additive: Boolean = false) {
    private val nodes = mutableMapOf<Pair<Float,Float>, Any>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun draw(canvas: Canvas, x: Float, y: Float, size: Float, sigma: Float, opacity: Double) {
        if (opacity <= 0) return
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
            val pad = ceil(sigma * 3)
            val node = nodes.getOrPut(size to sigma) {
                RenderNode("Chrome zero soft sprite").apply {
                    val extent = ceil(size+2*pad).toInt()
                    setPosition(0,0,extent,extent)
                    val recorder = beginRecording(extent,extent)
                    recorder.drawBitmap(bitmap,null,RectF(pad,pad,pad+size,pad+size),paint)
                    endRecording()
                    setRenderEffect(RenderEffect.createBlurEffect(sigma,sigma,Shader.TileMode.DECAL))
                    if (additive) setUseCompositingLayer(true,Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                    })
                }
            } as RenderNode
            node.alpha = opacity.toFloat().coerceIn(0f,1f)
            canvas.save()
            canvas.translate(x-pad,y-pad)
            canvas.drawRenderNode(node)
            canvas.restore()
        } else {
            paint.xfermode = if (additive) PorterDuffXfermode(PorterDuff.Mode.ADD) else null
            paint.alpha = (255*opacity).toInt().coerceIn(0,255)
            canvas.drawBitmap(bitmap,null,RectF(x,y,x+size,y+size),paint)
            paint.alpha = 255
        }
    }
}
