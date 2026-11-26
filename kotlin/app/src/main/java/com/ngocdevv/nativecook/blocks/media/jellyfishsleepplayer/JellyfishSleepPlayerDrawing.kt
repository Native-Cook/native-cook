package com.ngocdevv.nativecook.blocks.media.jellyfishsleepplayer

import android.content.Context
import android.graphics.*
import android.graphics.text.TextRunShaper
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

internal fun jellyfishSleepPlayerPhoto(context: Context): Bitmap = context.assets.open("jellyfishsleepplayer/jellyfish.webp").use {
    BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {
        inPreferredConfig=Bitmap.Config.ARGB_8888; inScaled=false
        inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
    })!!
}

@RequiresApi(33)
internal class JellyfishSleepPlayerSea(context: Context): View(context) {
    private val shader=RuntimeShader(JELLYFISH_SLEEP_PLAYER_AGSL)
    private val paint=Paint()
    private var frame=JellyfishSleepPlayerMotion.Frame()
    init {
        val image=BitmapShader(jellyfishSleepPlayerPhoto(context),Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
        image.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        // setInputBuffer preserves gamma-encoded image bytes, without another colour-space conversion.
        shader.setInputBuffer("uImg",image); paint.shader=shader; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun show(f: JellyfishSleepPlayerMotion.Frame) { frame=f; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val d=resources.displayMetrics.density; val w=width/d; val h=height/d; val ox=(w-393)/2
        val f=frame
        shader.setFloatUniform("uT",f.t.toFloat()); shader.setFloatUniform("uM",f.m.toFloat()); shader.setFloatUniform("uC",f.c.toFloat())
        shader.setFloatUniform("uH1",f.a.toFloat()); shader.setFloatUniform("uH2",f.b.toFloat()); shader.setFloatUniform("uK",f.k.toFloat())
        shader.setFloatUniform("uWave",f.wave.toFloat()); shader.setFloatUniform("uLit",f.lit.toFloat())
        shader.setFloatUniform("uOrigin",ox-0.41f,34*h/852); shader.setFloatUniform("uOff",ox,0f)
        canvas.save(); canvas.scale(d,d); canvas.drawRect(0f,0f,w,h,paint); canvas.restore()
    }
}

internal class JellyfishSleepPlayerChrome(context: Context): View(context) {
    companion object {
        val names=arrayOf("Moon Jelly","Glass Tide","Amber Drift")
        val metas=arrayOf("Ocean hum · 42 min · rain under it","Slow surf · 38 min · far gulls","Warm drone · 45 min · low wind")
        val colours=intArrayOf(0xFFFF8AD8.toInt(),0xFF6FE6FF.toInt(),0xFFFFB45A.toInt())
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { isLinearText=true; isSubpixelText=true }
    private fun font(name: String)=Typeface.createFromAsset(context.assets,"jellyfishsleepplayer/$name.ttf")
    private val serif=font("InstrumentSerif-Regular"); private val display=font("InterDisplay-Bold")
    private val medium=font("Inter-Medium"); private val semi=font("Inter-SemiBold")
    private inner class Line(val text: String,val font: Typeface,val size: Float,val tracking: Float=0f) {
        val width: Float
        private val glyphs: Any?
        private val ids: IntArray
        private val positions: FloatArray
        init {
            paint.typeface=font; paint.textSize=size; paint.letterSpacing=0f
            glyphs=if(Build.VERSION.SDK_INT>=31) TextRunShaper.shapeTextRun(text,0,text.length,0,text.length,0f,0f,false,paint) else null
            if(Build.VERSION.SDK_INT>=31) {
                val g=glyphs as android.graphics.text.PositionedGlyphs
                ids=IntArray(g.glyphCount()) { g.getGlyphId(it) }
                positions=FloatArray(g.glyphCount()*2) { if(it%2==0) g.getGlyphX(it/2)+tracking*(it/2) else g.getGlyphY(it/2) }
            } else { ids=IntArray(0); positions=FloatArray(0) }
            width=if(Build.VERSION.SDK_INT>=31) (glyphs as android.graphics.text.PositionedGlyphs).advance+glyphs.glyphCount()*tracking
                else { paint.letterSpacing=tracking/size; paint.measureText(text)+tracking }
        }
        fun draw(canvas: Canvas,x: Float,y: Float,color: Int,alpha: Double=1.0) {
            paint.reset(); paint.isAntiAlias=true; paint.isSubpixelText=true; paint.isLinearText=true
            paint.typeface=font; paint.textSize=size; paint.color=color; paint.alpha=(alpha*255).toInt().coerceIn(0,255)
            if(Build.VERSION.SDK_INT>=31) {
                val g=glyphs as android.graphics.text.PositionedGlyphs
                canvas.save(); canvas.translate(x,y)
                canvas.drawGlyphs(ids,0,positions,0,ids.size,g.getFont(0),paint); canvas.restore()
            } else {
                paint.letterSpacing=tracking/size
                canvas.drawTextRun(text,0,text.length,0,text.length,x,y,false,paint)
            }
        }
    }
    private val eyebrow=Line("DRIFT · SLEEP SOUNDS",semi,12f,4f)
    private val headline=Line("Drift off.",serif,86f,-2f)
    private val titles=names.map { Line(it,display,26f,-0.5f) }
    private val metadata=metas.map { Line(it,medium,15f) }
    private val start=Line("16:02",medium,12f); private val end=Line("42:00",medium,12f)
    private var frame=JellyfishSleepPlayerMotion.Frame()
    private val density=resources.displayMetrics.density
    private val glowPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowColours=colours.map { PorterDuffColorFilter(it,PorterDuff.Mode.SRC_IN) }
    // Rasterize the soft masks once. Text and chrome stay on the hardware Canvas, without a
    // screen-sized software bitmap upload every frame. The progress mask's middle is uniform.
    private fun glow(w: Float,h: Float,sigma: Float): Bitmap {
        val pad=sigma*3
        val bitmap=Bitmap.createBitmap(kotlin.math.ceil((w+pad*2)*density).toInt(),kotlin.math.ceil((h+pad*2)*density).toInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap); c.scale(density,density)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; maskFilter=BlurMaskFilter(sigma,BlurMaskFilter.Blur.NORMAL) }
        c.drawRoundRect(pad,pad,pad+w,pad+h,h/2,h/2,p)
        return bitmap
    }
    private val pillGlow=glow(80f,56f,17f)
    private val progressGlow=glow(80f,3f,5f)
    private val collapsedGlow=glow(329*0.02f,3f,5f)
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun show(f: JellyfishSleepPlayerMotion.Frame) { frame=f; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val d=resources.displayMetrics.density; val w=width/d; val h=height/d; val ox=(w-393)/2; val sy=h/852; val f=frame
        canvas.save(); canvas.scale(d,d)
        paint.reset(); paint.isAntiAlias=true
        paint.shader=LinearGradient(0f,540*sy,0f,h,intArrayOf(0x00020C1C,0xE0020C1C.toInt(),0xFF020C1C.toInt()),floatArrayOf(0f,0.45f,1f),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,540*sy,w,h,paint); paint.shader=null
        eyebrow.draw(canvas,w/2-eyebrow.width/2,90*sy,colours[f.a],1-f.k)
        if(f.k>0) eyebrow.draw(canvas,w/2-eyebrow.width/2,90*sy,colours[f.b],f.k)
        headline.draw(canvas,w/2-headline.width/2,170*sy,0xFFFFF4FB.toInt())
        fun track(i: Int,dx: Double,alpha: Double) {
            titles[i].draw(canvas,(ox+32+dx).toFloat(),700*sy,Color.WHITE,alpha)
            metadata[i].draw(canvas,(ox+32+dx).toFloat(),727*sy,0xFFD7C6FF.toInt(),alpha)
        }
        if(f.k>0) {
            val k1=minOf(1.0,f.k*2); val k2=maxOf(0.0,f.k*2-1)
            if(k1<1) track(f.a,-30*k1,1-k1)
            if(k2>0) track(f.b,30*(1-k2),k2)
        } else track(f.a,0.0,1.0)
        val cc=colours[if(f.k>0.5) f.b else f.a]
        paint.reset(); paint.isAntiAlias=true; paint.color=0x2EFFFFFF
        canvas.drawRoundRect(ox+32,748*sy,ox+361,748*sy+3,1.5f,1.5f,paint)
        val right=ox+32+(329*maxOf(0.02,f.prog)).toFloat()
        glowPaint.colorFilter=glowColours[if(f.k>0.5) f.b else f.a]
        val fillWidth=right-(ox+32)
        if(fillWidth<30f) canvas.drawBitmap(collapsedGlow,null,RectF(ox+32-15,748*sy-15,right+15,748*sy+18),glowPaint)
        else {
            val left=(30*density).toInt(); val rightStart=progressGlow.width-left
            val y=748*sy-15; val x=ox+32-15
            for((src,dst) in listOf(
                Rect(0,0,left,progressGlow.height) to RectF(x,y,x+30,y+33),
                Rect(left,0,rightStart,progressGlow.height) to RectF(x+30,y,right-15,y+33),
                Rect(rightStart,0,progressGlow.width,progressGlow.height) to RectF(right-15,y,right+15,y+33))) canvas.drawBitmap(progressGlow,src,dst,glowPaint)
        }
        paint.color=cc; canvas.drawRoundRect(ox+32,748*sy,right,748*sy+3,1.5f,1.5f,paint)
        start.draw(canvas,ox+32,770*sy,0xFFB7A8E0.toInt()); end.draw(canvas,ox+361-end.width,770*sy,0xFFB7A8E0.toInt())
        val top=776*sy
        canvas.drawBitmap(pillGlow,null,RectF(ox+156.5f-51,top-51,ox+236.5f+51,top+56+51),glowPaint)
        paint.reset(); paint.isAntiAlias=true; paint.color=0xFFFFF4FB.toInt()
        canvas.drawRoundRect(ox+156.5f,top,ox+236.5f,top+56,28f,28f,paint)
        paint.color=0xFF2A0F3D.toInt()
        if(f.playing) for(x in floatArrayOf(187f,197f)) canvas.drawRoundRect(ox+x,top+18,ox+x+6,top+38,2f,2f,paint)
        else {
            val path=Path().apply { moveTo(ox+187.5f,top+17); lineTo(ox+205.5f,top+28); lineTo(ox+187.5f,top+39); close() }
            canvas.drawPath(path,paint); paint.style=Paint.Style.STROKE; paint.strokeWidth=3f; paint.strokeJoin=Paint.Join.ROUND
            canvas.drawPath(path,paint); paint.style=Paint.Style.FILL
        }
        paint.color=0xE6FFFFFF.toInt()
        for((base,tip,bar) in listOf(Triple(108f,94f,89f),Triple(285f,299f,301.5f))) {
            val path=Path().apply { moveTo(ox+base,top+16); lineTo(ox+base,top+42); lineTo(ox+tip,top+29); close() }
            canvas.drawPath(path,paint); canvas.drawRect(ox+bar,top+16,ox+bar+2.5f,top+42,paint)
        }
        canvas.restore()
    }
}
