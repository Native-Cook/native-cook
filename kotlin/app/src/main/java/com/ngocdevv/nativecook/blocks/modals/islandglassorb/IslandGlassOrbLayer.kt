package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import android.graphics.*
import android.os.Build
import kotlin.math.roundToInt

/** Small retained content layer; native Gaussian blur on API 31+, cached raster blur below it. */
internal class IslandGlassOrbLayer(private val w: Int,private val h: Int) {
    private val node=if(Build.VERSION.SDK_INT>=29)RenderNode("IslandGlassOrb layer").apply { setPosition(0,0,w,h) } else null
    private var raster: Bitmap?=null
    private val blurred=mutableMapOf<Int,Bitmap>()
    private var lastBlur=-1f
    fun record(draw: (Canvas)->Unit) {
        if(Build.VERSION.SDK_INT>=29) {
            val n=requireNotNull(node); val c=n.beginRecording(w,h); draw(c); n.endRecording()
        }
        if(Build.VERSION.SDK_INT<31) {
            val b=raster?:Bitmap.createBitmap(w*3,h*3,Bitmap.Config.ARGB_8888).also { raster=it }
            b.eraseColor(Color.TRANSPARENT); val c=Canvas(b); c.scale(3f,3f); draw(c)
            blurred.values.forEach { it.recycle() }; blurred.clear()
        }
    }
    fun draw(c: Canvas,x: Double,y: Double,alpha: Double=1.0,blur: Double=0.0) {
        if(alpha<=0)return
        val save=c.save(); c.translate(x.toFloat(),y.toFloat())
        if(Build.VERSION.SDK_INT>=31 && c.isHardwareAccelerated) {
            val n=requireNotNull(node); n.alpha=alpha.coerceIn(0.0,1.0).toFloat()
            val r=blur.toFloat().coerceAtLeast(0f)
            if(r!=lastBlur) { n.setRenderEffect(if(r>.01f)RenderEffect.createBlurEffect(r,r,Shader.TileMode.DECAL) else null); lastBlur=r }
            c.drawRenderNode(n)
        } else if(Build.VERSION.SDK_INT>=29 && c.isHardwareAccelerated && blur<=.01) {
            val n=requireNotNull(node); n.alpha=alpha.coerceIn(0.0,1.0).toFloat(); c.drawRenderNode(n)
        } else {
            val src=raster
            if(src!=null) {
                val k=(blur*2).roundToInt().coerceAtLeast(0)
                val image=if(k==0)src else blurred.getOrPut(k) { IslandGlassOrbBlur.apply(src,k*1.5) }
                c.drawBitmap(image,null,RectF(0f,0f,w.toFloat(),h.toFloat()),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt() })
            }
        }
        c.restoreToCount(save)
    }
}
