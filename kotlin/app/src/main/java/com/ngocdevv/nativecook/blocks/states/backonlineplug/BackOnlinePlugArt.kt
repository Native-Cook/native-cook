package com.ngocdevv.nativecook.blocks.states.backonlineplug

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class BackOnlinePlugArt(context: Context) {
    data class Layout(val width: Float, val height: Float) {
        val ox = (width-393)/2; val fy = height/852; val sx = ox+196.5; val sy = 560*fy
        fun geometry(f: BackOnlinePlugMotion.Frame): DoubleArray {
            val scale = 1+.42*max(f.z,0.0)+.3*min(f.z,0.0)
            return doubleArrayOf(sx-64*f.a+4*f.sway,sy+92*f.a+2*f.sway,138*scale)
        }
        fun plugHit(x: Float,y: Float,f: BackOnlinePlugMotion.Frame): Boolean {
            val g = geometry(f)
            return hypot(x-g[0],y-g[1]) <= g[2]/2+16
        }
        fun buttonHit(x: Float,y: Float) = x >= ox+32 && x <= ox+361 && y >= 772*fy-6 && y <= 772*fy+64
    }
    private val assets = context.assets
    private fun bitmap(name: String) = assets.open("backonlineplug/backonlineplug_$name.webp").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    private val plug = bitmap("plug-back"); private val socket = bitmap("socket-front")
    private val title = Typeface.createFromAsset(assets,"backonlineplug/backonlineplug_BarlowCondensed-ExtraBold.ttf")
    private val medium = Typeface.createFromAsset(assets,"backonlineplug/backonlineplug_Inter-Medium.ttf")
    private val semi = Typeface.createFromAsset(assets,"backonlineplug/backonlineplug_Inter-SemiBold.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val plateShadow = Bitmap.createBitmap(242,242,Bitmap.Config.ARGB_8888).also {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(71,0,0,0); maskFilter = BlurMaskFilter(10f/3,BlurMaskFilter.Blur.NORMAL) }
        Canvas(it).drawRoundRect(16f,16f,226f,226f,18f,18f,p)
    }
    private val plugShadows = mutableMapOf<Int,Bitmap>()
    // Cached normalized software masks also work on a hardware Canvas below API 33.
    private fun plugShadow(depth: Double): Bitmap {
        val key = (depth*60).roundToInt()
        return plugShadows.getOrPut(key) {
            val z = key/60.0; val radius = 138*(1+.42*z)/2*.98
            Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).also {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    maskFilter = BlurMaskFilter(((3+16*z)/3/radius*100).toFloat(),BlurMaskFilter.Blur.NORMAL)
                }
                Canvas(it).drawCircle(128f,128f,100f,p)
            }
        }
    }
    private fun ink(rgb: DoubleArray, k: Double = 1.0, a: Double = 1.0) = Color.argb(
        (a*255).roundToInt().coerceIn(0,255),(rgb[0]*k).roundToInt().coerceIn(0,255),
        (rgb[1]*k).roundToInt().coerceIn(0,255),(rgb[2]*k).roundToInt().coerceIn(0,255))
    private fun white(a: Double = 1.0) = Color.argb((a*255).roundToInt().coerceIn(0,255),255,255,255)
    private fun reset() { paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; paint.isSubpixelText = true }
    private fun text(c: Canvas, font: Typeface, size: Float, s: String, x: Float, y: Float, color: Int, tracking: Float = 0f) {
        reset(); paint.typeface = font; paint.textSize = size; paint.color = color
        if(tracking == 0f) c.drawText(s,x,y,paint) else {
            val widths = FloatArray(s.length); paint.getTextWidths(s,widths); var dx = x
            s.forEachIndexed { i,ch -> c.drawText(ch.toString(),dx,y,paint); dx += widths[i]+tracking }
        }
    }
    fun draw(c: Canvas, l: Layout, f: BackOnlinePlugMotion.Frame) {
        reset(); paint.shader = RadialGradient(l.sx.toFloat(),l.sy,620f,
            intArrayOf(ink(f.wall,1.18),ink(f.wall,.78)),floatArrayOf(20f/620,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,l.width,l.height,paint)
        reset(); c.drawBitmap(plateShadow,(l.sx-105+6-16).toFloat(),l.sy-105+10-16,paint)
        c.drawBitmap(socket,null,RectF((l.sx-105).toFloat(),l.sy-105,(l.sx+105).toFloat(),l.sy-105+210f*639/640),paint)
        val g = l.geometry(f); val x = g[0]; val y = g[1]; val face = g[2]
        val top = y-face/2; val h = face*723/419; val depth = max(f.z,0.0)
        val maskScale = face/2*.98/100
        val shadowX = x+4+18*depth-128*maskScale; val shadowY = y+6+28*depth-128*maskScale
        reset(); paint.alpha = ((.45-.2*depth)*255).roundToInt()
        c.drawBitmap(plugShadow(depth),null,RectF(shadowX.toFloat(),shadowY.toFloat(),(shadowX+256*maskScale).toFloat(),(shadowY+256*maskScale).toFloat()),paint)
        val cw = face*54/419; val ccx = x-face/2+face*208.5/419; val cableTop = top+h-4
        if(cableTop < l.height) {
            reset(); paint.shader = LinearGradient((ccx-cw/2).toFloat(),0f,(ccx+cw/2).toFloat(),0f,
                intArrayOf(Color.rgb(217,217,215),Color.rgb(244,244,242),Color.rgb(189,189,187)),floatArrayOf(0f,.35f,1f),Shader.TileMode.CLAMP)
            c.drawRect((ccx-cw/2).toFloat(),cableTop.toFloat(),(ccx+cw/2).toFloat(),l.height,paint)
        }
        reset(); c.drawBitmap(plug,null,RectF((x-face/2).toFloat(),top.toFloat(),(x+face/2).toFloat(),(top+h).toFloat()),paint)
        if(f.spark > 0 && f.spark < 1) {
            val sp = f.spark
            reset(); paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); paint.style = Paint.Style.STROKE
            paint.color = ink(doubleArrayOf(255.0,246.0,176.0),a=.9*(1-sp)); paint.strokeWidth = (3*(1-sp)+.5).toFloat()
            c.drawCircle(x.toFloat(),y.toFloat(),(face/2+4+22*sp).toFloat(),paint)
            paint.color = ink(doubleArrayOf(255.0,246.0,176.0),a=1-sp); paint.strokeWidth = 2f; paint.strokeCap = Paint.Cap.ROUND
            val r0 = face/2+6+16*sp; val r1 = r0+14*(1-sp)+3
            repeat(10) {
                val a = it/10.0*6.283+.2
                c.drawLine((x+cos(a)*r0).toFloat(),(y+sin(a)*r0).toFloat(),(x+cos(a)*r1).toFloat(),(y+sin(a)*r1).toFloat(),paint)
            }
        }
        repeat(3) { i ->
            val u = BackOnlinePlugMotion.clamp(f.flip*1.6-i*.3); val a = abs(u-.5)*2
            if(a > 0) {
                c.save(); c.translate(l.ox+30,floatArrayOf(150f,224f,298f)[i]*l.fy); c.scale(1f,max(.05,a).toFloat())
                text(c,title,78f,(if(u >= .5) arrayOf("BACK","ON","LINE.") else arrayOf("STILL","OFF","LINE."))[i],0f,0f,white(a),4f); c.restore()
            }
        }
        val on = f.flip >= .5
        // The designer correction ties subtitle ink to its text swap.
        val color = if(on) Color.rgb(166,215,182) else Color.rgb(169,181,174)
        text(c,medium,16f,if(on) "Your 3 drafts are syncing now." else "Plug back in to sync 3 drafts.",l.ox+32,350*l.fy,color)
        val controlY = 772*l.fy; reset(); paint.color = white(if(on) .14 else 1.0)
        val radius = if(on) 22f else 29f
        c.drawRoundRect(l.ox+32,controlY,l.ox+32+if(on) 160f else 329f,controlY+if(on) 44f else 58f,radius,radius,paint)
        if(on) text(c,semi,15f,"Syncing ${f.chip} of 3",l.ox+54,controlY+28,Color.WHITE) else {
            paint.typeface = semi; paint.textSize = 17f; val width = paint.measureText("Try again")
            text(c,semi,17f,"Try again",l.ox+196.5f-width/2,controlY+35,Color.rgb(31,58,42))
        }
    }
}
