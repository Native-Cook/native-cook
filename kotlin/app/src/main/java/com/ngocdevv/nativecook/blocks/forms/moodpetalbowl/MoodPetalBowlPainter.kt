package com.ngocdevv.nativecook.blocks.forms.moodpetalbowl

import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class MoodPetalBowlPainter(val resources: MoodPetalBowlResources) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect=RectF()
    private val trend=Path().apply { moveTo(0f,686f);cubicTo(18f,666f,28f,644f,43f,660f);cubicTo(58f,676f,80f,712f,104f,712f);lineTo(393f,712f) }
    private val lower=Path().apply { moveTo(43.5f,273f);cubicTo(43.5f,357.499f,112.001f,426f,196.5f,426f);cubicTo(280.999f,426f,349.5f,357.499f,349.5f,273f) }
    val disk=Path().apply {addOval(34.5f,9f,340.5f,315f,Path.Direction.CW)}
    val sharpVisibility=Path().apply {addRect(0f,0f,375f,162f,Path.Direction.CW);addPath(disk)}
    private val typefaces=listOf(400,600).associateWith { weight ->
        if(Build.VERSION.SDK_INT>=28) Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),weight,false)
        else Typeface.create(if(weight>=600) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
    }
    private val titleAdvances=MoodPetalBowlMood.entries.associate { mood ->
        text.typeface=typefaces[400];text.textSize=49.5f
        mood.title to FloatArray(mood.title.length).also {text.getTextWidths(mood.title,it)}
    }
    private fun ink(hex: Int,alpha: Double=1.0,style: Paint.Style=Paint.Style.FILL,width: Float=1f) {
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true;paint.color=hex or (0xff shl 24)
        paint.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt();paint.style=style;paint.strokeWidth=width
    }
    private fun bitmap(c: Canvas,b: Bitmap,x: Float,y: Float,w: Float,h: Float) {ink(0xffffff);rect.set(x,y,x+w,y+h);c.drawBitmap(b,null,rect,paint)}
    fun background(c: Canvas,height: Float) {
        c.drawColor(0xffF4F1EA.toInt());bitmap(c,resources.paper,0f,0f,393f,1024f);bitmap(c,resources.header,0f,0f,393f,128f)
        ink(0x827F77,.18,Paint.Style.STROKE,.7f);paint.pathEffect=DashPathEffect(floatArrayOf(3f,4f),0f);c.drawLine(0f,682f,393f,682f,paint)
        ink(0x191A17,style=Paint.Style.STROKE,width=1.2f);c.drawPath(trend,paint)
        c.save();c.translate(9f,111f);c.clipPath(disk)
        val layer=c.saveLayer(0f,0f,375f,345f,null)
        ink(0xE9E0D4,.44);c.drawRect(0f,0f,375f,345f,paint)
        mask(c,true);c.restoreToCount(layer);c.restore()
        @Suppress("UNUSED_VARIABLE") val fullHeight=height
    }
    /** One opaque-ordered pile, shared unchanged by sharp and aggregate-soft passes. */
    fun pile(c: Canvas,scene: MoodPetalBowlScene) {
        for(id in MoodPetalBowlGeometry.painterOrder) {
            val p=scene.tokens[id];if(p.alpha<=0) continue
            c.save();c.translate((p.center.x-9).toFloat(),(p.center.y-111).toFloat());c.rotate(p.angle.toFloat())
            val side=(104*MoodPetalBowlGeometry.radius(id,p.mood)/43).toFloat()
            ink(0xffffff,p.alpha);rect.set(-side/2,-side/2,side/2,side/2);c.drawBitmap(resources.tokens[p.mood.ordinal],null,rect,paint);c.restore()
        }
    }
    fun mask(c: Canvas,soft: Boolean) {
        ink(0xffffff);paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        rect.set(0f,0f,375f,345f);c.drawBitmap(if(soft) resources.softStrip else resources.sharpStrip,null,rect,paint);paint.xfermode=null
    }
    fun foreground(c: Canvas,s: MoodPetalBowlScene,bottom: Float) {
        ink(0xFCF8F0,.30,Paint.Style.STROKE,.8f);c.drawPath(lower,paint)
        // A one-point lip remains visible after the aggregate material.
        ink(0xFCF8F0,.40,Paint.Style.STROKE,1.2f);paint.maskFilter=BlurMaskFilter(.6f,BlurMaskFilter.Blur.NORMAL);c.drawLine(43.5f,273f,349.5f,273f,paint);paint.maskFilter=null
        label(c,"Today I feel",196.5,181.5,16.0)
        for(mood in MoodPetalBowlMood.entries) label(c,mood.title,196.5+s.labelOffsets[mood.ordinal],478.5,49.5,alpha=s.labelWeights[mood.ordinal],tracking=.6)
        for(right in listOf(false,true)) {
            val x=if(right) 350f else 43f;val path=Path();val base=if(right) x-3.25f else x+3.25f;val tip=if(right) x+3.25f else x-3.25f
            path.moveTo(base,474.5f);path.lineTo(tip,479.5f);path.lineTo(base,484.5f);path.close();ink(0x191A17);c.drawPath(path,paint)
        }
        label(c,"Saved for ${MoodPetalBowlMath.days[s.captionDay]}",196.5,526.0,11.5,0x827F77,alpha=s.captionAlpha)
        ink(0xEDE8DE);c.drawRoundRect((s.capsuleX-22).toFloat(),559f,(s.capsuleX+22).toFloat(),647f,22f,22f,paint)
        val shorts=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
        for(day in 0..6) {val x=MoodPetalBowlMath.dateX[day];val dot=s.dots[day]
            ink(Color.rgb((dot.r*255).roundToInt(),(dot.g*255).roundToInt(),(dot.b*255).roundToInt()),dot.a);c.drawCircle(x.toFloat(),574f,3f,paint)
            label(c,shorts[day],x,596.0,14.0,0x827F77);label(c,"${12+day}",x,619.2,20.0,weight=600)
        }
        ink(0x1D1E19);c.drawRoundRect(24f,754f-bottom,369f,816f-bottom,31f,31f,paint)
        ink(0x10A97B,style=Paint.Style.STROKE,width=1.8f);paint.strokeCap=Paint.Cap.ROUND
        c.drawLine(74f,785f-bottom,90f,785f-bottom,paint);c.drawLine(82f,777f-bottom,82f,793f-bottom,paint)
        c.save();c.translate(147f,773f-bottom);ink(0xF3F1E7,style=Paint.Style.STROKE,width=1.5f);paint.strokeCap=Paint.Cap.ROUND
        c.drawLine(3f,8f,21f,8f,paint);c.drawLine(3f,16f,21f,16f,paint);paint.style=Paint.Style.FILL;c.drawCircle(9f,8f,2.2f,paint);c.drawCircle(15f,16f,2.2f,paint);c.restore()
        c.save();c.translate(224f,773f-bottom);ink(0xF3F1E7);c.drawOval(5.5f,5f,10.5f,10f,paint);c.drawOval(14f,6.2f,18f,10.2f,paint)
        val busts=Path().apply {moveTo(2f,19f);cubicTo(2f,11.6f,14f,11.6f,14f,19f);close();moveTo(13.8f,18f);cubicTo(13f,12f,22f,12f,22f,18f);close()};c.drawPath(busts,paint);c.restore()
        ink(0xF3F1E7,style=Paint.Style.STROKE,width=1.5f);paint.strokeCap=Paint.Cap.ROUND
        listOf(6f to 13f,10f to 20f,14f to 16f,18f to 7f).forEach { (x,h) -> c.drawLine(301f+x,794.5f-bottom,301f+x,794.5f-h-bottom,paint) }
    }
    private fun label(c: Canvas,value: String,x: Double,y: Double,size: Double,hex: Int=0x191A17,weight: Int=400,alpha: Double=1.0,tracking: Double=0.0) {
        if(alpha<=0) return
        text.typeface=typefaces[weight];text.textSize=size.toFloat();text.color=hex or (0xff shl 24);text.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt()
        val metrics=text.fontMetrics;val baseline=(y-(metrics.ascent+metrics.descent)/2).toFloat()
        val advances=titleAdvances[value]
        if(tracking!=0.0&&advances!=null) {
            var at=(x-(advances.sum()+(value.length-1)*tracking)/2).toFloat()
            for(i in value.indices) {c.drawText(value,i,i+1,at,baseline,text);at+=advances[i]+tracking.toFloat()}
        } else c.drawText(value,(x-text.measureText(value)/2).toFloat(),baseline,text)
    }
}
