package com.ngocdevv.nativecook.blocks.pickers.appiconatelier

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

internal class AppIconAtelierArt(context:Context) {
    private val assets=context.assets
    private val wall=assets.open("appiconatelier/appiconatelier_wallpaper.jpg").use{requireNotNull(BitmapFactory.decodeStream(it))}
    private val faces=listOf("Inter-Regular","Inter-Medium","Inter-SemiBold","InstrumentSerif-Regular").associateWith{Typeface.createFromAsset(assets,"appiconatelier/appiconatelier_$it.ttf")}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val runtime=if(Build.VERSION.SDK_INT>=33)AppIconAtelierRuntime(wall) else null
    private val glyphs=listOf("M18 22 H42 M18 30 H42 M18 38 H34","M14 28 C20 22 24 22 30 28 S40 34 46 28 M14 37 C20 31 24 31 30 37 S40 43 46 37","","M20 42 V34 M30 42 V24 M40 42 V30").map{PathParser.createPathFromPathData(it)?:Path()}
    private val heart=requireNotNull(PathParser.createPathFromPathData("M0 24 C-5 17 -27 5 -27 -8 C-27 -29 -3 -30 0 -16 C4 -30 27 -29 27 -8 C27 5 6 18 0 24 Z"))
    private val arrow=requireNotNull(PathParser.createPathFromPathData("M10 2 L2 10 L10 18"))
    private var base:Bitmap?=null
    private var cached:AppIconAtelierTheme.Layout?=null
    private fun color(h:Int,a:Double=1.0)=((a.coerceIn(0.0,1.0)*255).roundToInt() shl 24) or h
    private fun reset(h:Int=0xffffff,a:Double=1.0,stroke:Float=0f){paint.reset();paint.isAntiAlias=true;paint.isFilterBitmap=true;paint.color=color(h,a);if(stroke>0){paint.style=Paint.Style.STROKE;paint.strokeWidth=stroke;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND}}
    private fun label(c:Canvas,s:String,x:Double,top:Double,size:Double,face:String="Inter-Regular",ink:Int=0x1C1630,align:Paint.Align=Paint.Align.LEFT,line:Double=0.0,track:Double=0.0,alpha:Double=1.0){
        textPaint.typeface=faces.getValue(face);textPaint.textSize=size.toFloat();textPaint.color=color(ink,alpha);textPaint.textAlign=align
        val fm=textPaint.fontMetrics;val h=if(line>0)line else (fm.descent-fm.ascent).toDouble();val baseline=top+(h-(fm.descent-fm.ascent))/2-fm.ascent
        if(track==0.0)c.drawText(s,x.toFloat(),baseline.toFloat(),textPaint)
        else {val widths=FloatArray(s.length);textPaint.getTextWidths(s,widths);val total=widths.sum()+track*(s.length-1);var px=if(align==Paint.Align.CENTER)x-total/2 else x;textPaint.textAlign=Paint.Align.LEFT;s.forEachIndexed{i,ch->c.drawText(ch.toString(),px.toFloat(),baseline.toFloat(),textPaint);px+=widths[i]+track}}
    }
    private fun makeBase(L:AppIconAtelierTheme.Layout,density:Double):Bitmap {
        val b=Bitmap.createBitmap((L.w*density).roundToInt(),(L.h*density).roundToInt(),Bitmap.Config.ARGB_8888)
        val c=Canvas(b);c.scale(density.toFloat(),density.toFloat())
        val starts=intArrayOf(0xFFE7A3,0xBFE3FF,0,0xCDEFD9);val ends=intArrayOf(0xF4C04E,0x5E9EF0,0,0x4FB37E)
        for(i in listOf(0,1,3)){
            val x=(L.appX[i]-30).toFloat();val y=(L.appsY-30).toFloat()
            reset(0x3C286E,.18);paint.maskFilter=BlurMaskFilter(5f,BlurMaskFilter.Blur.NORMAL);c.drawRoundRect(x,y+3,x+60,y+63,15f,15f,paint)
            reset();paint.shader=LinearGradient(x,y,x,y+60,intArrayOf(color(starts[i]),color(ends[i])),null,Shader.TileMode.CLAMP);c.drawRoundRect(x,y,x+60,y+60,15f,15f,paint)
            reset(0xffffff,.45,1f);c.drawRoundRect(x+.5f,y+.5f,x+59.5f,y+59.5f,14.5f,14.5f,paint)
            c.save();c.translate(x,y);reset(0xffffff,1.0,4f);c.drawPath(glyphs[i],paint);c.restore()
        }
        for(i in 0..4){
            val x=L.chip(i.toDouble()).toFloat();val y=(L.chipsTop+26).toFloat();val g=AppIconAtelierTheme.glasses[i]
            reset(0x28185A,.14);paint.maskFilter=BlurMaskFilter(4f,BlurMaskFilter.Blur.NORMAL);c.drawCircle(x,y+2,22f,paint)
            reset();paint.shader=RadialGradient(x-22*.35f,y-22*.4f,33f,intArrayOf(color(g.light),color(g.deep)),null,Shader.TileMode.CLAMP);c.drawCircle(x,y,22f,paint)
            reset(0xffffff,.7);paint.maskFilter=BlurMaskFilter(2f,BlurMaskFilter.Blur.NORMAL);c.drawOval(x-22*.55f,y-22*.78f,x-22*.55f+22*.8f,y-22*.78f+22*.42f,paint)
            reset(0xffffff,.55,1f);c.drawCircle(x,y,21.5f,paint);reset(0x1C1630,.1,.5f);c.drawCircle(x,y,22.25f,paint)
        }
        return b
    }
    private fun fallback(c:Canvas,L:AppIconAtelierTheme.Layout,f:AppIconAtelierMotion.Frame){
        reset();paint.shader=LinearGradient(0f,0f,0f,L.h.toFloat(),intArrayOf(color(0xF7F5FB),color(0xEEEAF6)),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,L.w.toFloat(),L.h.toFloat(),paint)
        c.save();c.clipPath(Path().apply{addRoundRect(RectF(16f,L.cardY.toFloat(),(L.w-16).toFloat(),(L.cardY+168).toFloat()),28f,28f,Path.Direction.CW)})
        reset();val scale=max(L.cardW/wall.width,168.0/wall.height);val w=wall.width*scale;val h=wall.height*scale;c.drawBitmap(wall,null,RectF((16+(L.cardW-w)/2).toFloat(),(L.cardY+(168-h)/2).toFloat(),(16+(L.cardW+w)/2).toFloat(),(L.cardY+(168+h)/2).toFloat()),paint);c.restore()
        fun pebble(x:Double,y:Double,s:Double,index:Int){
            val g=AppIconAtelierTheme.glasses[index];c.save();c.translate(x.toFloat(),y.toFloat());c.rotate(-7f);c.scale((s/31.5).toFloat(),(s/31.5).toFloat())
            reset();paint.shader=LinearGradient(-26f,-28f,26f,30f,intArrayOf(color(g.light),color(g.deep)),null,Shader.TileMode.CLAMP);c.drawRoundRect(-29f,-27f,29f,27f,17f,17f,paint);reset(0xffffff,.7,1.5f);c.drawRoundRect(-28f,-26f,28f,26f,16f,16f,paint);reset(0xffffff,.95);c.scale(.63f,.63f);c.drawPath(heart,paint);c.restore()
        }
        if(f.peek<.95)pebble(L.appX[2],L.appsY,31.5,f.applied)
        val at=L.flight(f.peek);pebble(at.first,at.second-f.bob*L.heroS,L.heroS+(31.5-L.heroS)*f.peek,f.shown)
    }
    fun draw(c:Canvas,L:AppIconAtelierTheme.Layout,f:AppIconAtelierMotion.Frame,updated:Boolean,density:Double){
        if(cached!=L){base?.recycle();base=makeBase(L,density);cached=L}
        if(Build.VERSION.SDK_INT>=33&&runtime!=null){reset();paint.shader=runtime.update(L,f,density);c.drawRect(0f,0f,L.w.toFloat(),L.h.toFloat(),paint)}else fallback(c,L,f)
        reset();base?.let{c.drawBitmap(it,null,RectF(0f,0f,L.w.toFloat(),L.h.toFloat()),paint)}
        reset(0x1C1630,1.0,1.5f);c.drawCircle(L.chip(f.sel).toFloat(),(L.chipsTop+26).toFloat(),29f,paint)
        label(c,"App Icon",L.w/2,L.top,17.0,"Inter-SemiBold",align=Paint.Align.CENTER,line=44.0)
        c.save();c.translate(16f,(L.top+12).toFloat());reset(0x1C1630,1.0,2.2f);c.drawPath(arrow,paint);c.restore();label(c,"Settings",34.0,L.top,17.0,line=44.0)
        val alpha=1-.65*AppIconAtelierMotion.clamp(f.peek);val g=AppIconAtelierTheme.glasses[f.shown]
        label(c,"AMORA · ICON ${f.shown+1} OF 5",L.w/2,L.eyebrowTop,12.0,"Inter-Medium",0x8A8399,Paint.Align.CENTER,16.0,1.2,alpha)
        label(c,g.name,L.w/2,L.nameTop,38.0,"InstrumentSerif-Regular",align=Paint.Align.CENTER,line=42.0,alpha=alpha)
        label(c,g.note,L.w/2,L.noteTop,15.0,ink=0x8A8399,align=Paint.Align.CENTER,line=20.0,alpha=alpha)
        label(c,"Home Screen",20.0,L.homeLabelTop,13.0,"Inter-SemiBold",0x8A8399,line=18.0,track=.2)
        label(c,"9:41",36.0,L.cardY+14,13.0,"Inter-SemiBold",0xFFFFFF)
        for(i in 0..3)label(c,listOf("Notes","Tides","Amora","Ledger")[i],L.appX[i],L.appsY+36,11.0,"Inter-Medium",0xFFFFFF,Paint.Align.CENTER)
        val idle=f.shown==f.applied&&!updated
        reset(if(idle)0x1C1630 else 0x1C1630,if(idle).14 else 1.0,if(idle)1f else 0f);c.drawRoundRect(16f,L.ctaTop.toFloat(),(L.w-16).toFloat(),(L.ctaTop+56).toFloat(),28f,28f,paint)
        label(c,if(updated)"Icon Updated" else if(idle)"Current Icon" else "Use This Icon",L.w/2,L.ctaTop,17.0,"Inter-SemiBold",if(idle)0x8A8399 else 0xFBFAFE,Paint.Align.CENTER,56.0)
        if(f.finger>0){val x=if(f.fingerAt==0)L.heroX+f.fingerX*L.heroS else L.chip(f.fingerX);val y=if(f.fingerAt==0)L.heroY+.18*L.heroS else L.chipsTop+26;val r=(22*(1-.12*f.fingerPress)).toFloat();reset(0x1C1630,.1*f.finger);c.drawCircle(x.toFloat(),y.toFloat(),r,paint);reset(0x1C1630,.32*f.finger,1.5f);c.drawCircle(x.toFloat(),y.toFloat(),r,paint)}
    }
}
