package com.ngocdevv.nativecook.blocks.cards.lenticulareditionpass

import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class LenticularEditionPassPainter(private val resources: LenticularEditionPassResources) {
    private val lens=if(Build.VERSION.SDK_INT>=33) LenticularEditionPassLens(resources) else null
    private val fallback=if(Build.VERSION.SDK_INT<33) LenticularEditionPassFallback(resources) else null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect=RectF();private val outline=Path()
    private val ambientBlur=BlurMaskFilter(((27f-.5f)/.57735f),BlurMaskFilter.Blur.NORMAL)
    private val contactBlur=BlurMaskFilter(((2.5f-.5f)/.57735f),BlurMaskFilter.Blur.NORMAL)
    private val typefaces=listOf(400,500,600).associateWith {weight ->
        if(Build.VERSION.SDK_INT>=28) Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),weight,false)
        else Typeface.create(if(weight>=500) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
    }
    private val restSpan=LenticularEditionPassGeometry.corners(LenticularEditionPassPose.Rest).let {p -> p.maxOf {it.x}-p.minOf {it.x}}
    private fun ink(hex: Int,alpha: Double=1.0,style: Paint.Style=Paint.Style.FILL,width: Float=1f) {
        paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true;paint.color=hex or (0xff shl 24)
        paint.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt();paint.style=style;paint.strokeWidth=width
    }
    private fun bitmap(c: Canvas,b: Bitmap,x: Float,y: Float,w: Float,h: Float) {ink(0xffffff);rect.set(x,y,x+w,y+h);c.drawBitmap(b,null,rect,paint)}
    private fun label(c: Canvas,value: String,x: Double,y: Double,size: Double,hex: Int=0x192C35,weight: Int=400,alpha: Double=1.0,tracking: Double=0.0,center: Boolean=false) {
        if(alpha<=0) return
        text.typeface=typefaces[weight];text.textSize=size.toFloat();text.color=hex or (0xff shl 24);text.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt()
        text.letterSpacingCompat(value,x,y,tracking,center,c)
    }
    private fun Paint.letterSpacingCompat(value: String,x: Double,y: Double,tracking: Double,center: Boolean,c: Canvas) {
        val metrics=fontMetrics;val baseline=(y-(metrics.ascent+metrics.descent)/2).toFloat()
        if(tracking==0.0) {c.drawText(value,(if(center) x-measureText(value)/2 else x).toFloat(),baseline,this);return}
        val widths=FloatArray(value.length);getTextWidths(value,widths)
        var at=(x-if(center) (widths.sum()+(value.length-1)*tracking)/2 else 0.0).toFloat()
        for(i in value.indices) {c.drawText(value,i,i+1,at,baseline,this);at+=widths[i]+tracking.toFloat()}
    }
    private fun shadows(c: Canvas,frame: LenticularEditionPassFrame) {
        val corners=LenticularEditionPassGeometry.corners(frame.pose)
        val span=corners.maxOf {it.x}-corners.minOf {it.x};val center=LenticularEditionPassGeometry.project(LenticularEditionPassPoint(),frame.pose)
        val width=250*(span/restSpan).coerceIn(.92,1.04);val x=196.5+.18*(center.x-196.5)
        ink(0x192C35,.14);paint.maskFilter=ambientBlur;c.drawOval((x-width/2).toFloat(),487f,(x+width/2).toFloat(),517f,paint)
        outline.rewind()
        val centers=arrayOf(160.5 to -99.5,160.5 to 99.5,-160.5 to 99.5,-160.5 to -99.5)
        for(corner in 0..3) for(step in 0..12) {
            val a=(-90+corner*90+step*7.5)*PI/180
            val p=LenticularEditionPassGeometry.project(LenticularEditionPassPoint(centers[corner].first+6*cos(a),centers[corner].second+6*sin(a)),frame.pose)
            if(corner==0&&step==0) outline.moveTo(p.x.toFloat(),p.y.toFloat()+1.5f) else outline.lineTo(p.x.toFloat(),p.y.toFloat()+1.5f)
        }
        outline.close();ink(0x192C35,.23);paint.maskFilter=contactBlur;c.drawPath(outline,paint);paint.maskFilter=null
    }
    fun draw(c: Canvas,frame: LenticularEditionPassFrame,height: Float,bottom: Float) {
        c.drawColor(0xffEDEFE9.toInt());bitmap(c,resources.paper,0f,0f,393f,1024f)
        shadows(c,frame)
        c.save();c.clipRect(21.5f,242.5f,371.5f,507.5f)
        if(Build.VERSION.SDK_INT>=33) lens!!.draw(c,frame) else fallback!!.draw(c,frame)
        c.restore()
        controls(c,frame,bottom)
        @Suppress("UNUSED_VARIABLE") val fullHeight=height
    }
    private fun controls(c: Canvas,current: LenticularEditionPassFrame,bottom: Float) {
        val viewB=LenticularEditionPassGeometry.meanViewB(current)>.5
        ink(0x192C35,style=Paint.Style.STROKE,width=1.6f);paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        c.drawLine(27f,92f,45f,92f,paint);c.drawLine(27f,92f,34f,85f,paint);c.drawLine(27f,92f,34f,99f,paint)
        label(c,"EDITION / 07",62.0,91.0,10.5,weight=500,tracking=1.6)
        label(c,"Between",24.0,138.5,32.0,tracking=-.8)
        label(c,"two views",24.0,173.5,32.0,tracking=-.8)
        ink(0xCDD4D0,style=Paint.Style.STROKE,width=.8f);c.drawRoundRect(275f,210f,369f,235f,12.5f,12.5f,paint)
        label(c,"02 VIEWS",322.0,222.5,10.0,weight=500,tracking=1.0,center=true)
        label(c,"Quiet Orbit",24.0,564.5,23.0,weight=500)
        label(c,"No. 07 of 128",24.0,595.5,12.5,0x78868B)
        label(c,"An angle changes the story.",24.0,617.5,12.5,0x78868B)
        for(view in 0..1) {
            val x=24f+view*70;c.save();outline.rewind();outline.addRoundRect(x,651f,x+58,688f,3f,3f,Path.Direction.CW);c.clipPath(outline)
            bitmap(c,resources.art[view],x,651f,58f,37f);c.restore()
            ink(if((view==1)==viewB) 0x192C35 else 0xCDD4D0,style=Paint.Style.STROKE,width=if((view==1)==viewB) 1.2f else .6f)
            c.drawRoundRect(x,651f,x+58,688f,3f,3f,paint)
        }
        label(c,"Turn it in your hands.",24.0,720.5,12.0,0x78868B)
        ink(0x20343E);c.drawRoundRect(24f,755f-bottom,369f,809f-bottom,27f,27f,paint)
        label(c,"Add to collection",196.5,782.0-bottom,15.5,0xF2F2E9,600,1-current.collectionMix,center=true)
        text.typeface=typefaces[600];text.textSize=15.5f;val titleWidth=text.measureText("Collected")
        val left=196.5f-(titleWidth+21)/2
        ink(0xF2F2E9,current.collectionMix,Paint.Style.STROKE,1.7f);paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        c.drawLine(left,782f-bottom,left+4,786f-bottom,paint);c.drawLine(left+4,786f-bottom,left+11,777.5f-bottom,paint)
        label(c,"Collected",(left+21).toDouble(),782.0-bottom,15.5,0xF2F2E9,600,current.collectionMix)
    }
}
