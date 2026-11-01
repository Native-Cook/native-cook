package com.ngocdevv.nativecook.blocks.charts.sproutstats

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.charts.sproutstats.SproutStatsMotion as M

internal class SproutStatsArt(private val bold: Typeface, private val black: Typeface) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path=Path()
    private val rect=RectF()
    private fun paint(color: Int, alpha: Double=1.0, stroke: Double=0.0): Paint {
        p.shader=null; p.color=(0xff000000.toInt() or color); p.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt()
        p.style=if(stroke>0)Paint.Style.STROKE else Paint.Style.FILL; p.strokeWidth=stroke.toFloat(); p.strokeCap=Paint.Cap.ROUND
        return p
    }
    private fun text(c: Canvas, s: String, x: Double, y: Double, size: Double, color: Int, heavy: Boolean=false, center: Boolean=false, alpha: Double=1.0, baseline: Boolean=false, track: Double=0.0) {
        paint(color,alpha); p.typeface=if(heavy)black else bold; p.textSize=size.toFloat()
        p.letterSpacingCompat(s,c,x,y,center,baseline,track)
    }
    private fun Paint.letterSpacingCompat(s: String,c: Canvas,x: Double,y: Double,center: Boolean,baseline: Boolean,track: Double) {
        val w=measureText(s)+track*(s.length-1); var xx=x-if(center)w/2 else 0.0
        val yy=(y+if(baseline)0.0 else -fontMetrics.ascent.toDouble()).toFloat()
        if(track==0.0)c.drawText(s,xx.toFloat(),yy,this)
        else for(ch in s) { val st=ch.toString(); c.drawText(st,xx.toFloat(),yy,this); xx+=measureText(st)+track }
    }
    private fun height(size: Double, heavy: Boolean=false): Double {
        p.typeface=if(heavy)black else bold; p.textSize=size.toFloat(); return ceil((p.fontMetrics.descent-p.fontMetrics.ascent).toDouble())
    }
    private fun rr(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int) {
        rect.set(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()); c.drawRoundRect(rect,r.toFloat(),r.toFloat(),paint(color))
    }
    private fun line(c: Canvas,x: Double,y: Double,x2: Double,y2: Double,color: Int,alpha: Double,width: Double) = c.drawLine(x.toFloat(),y.toFloat(),x2.toFloat(),y2.toFloat(),paint(color,alpha,width))
    private fun circle(c: Canvas,x: Double,y: Double,r: Double,color: Int) = c.drawCircle(x.toFloat(),y.toFloat(),max(0.0,r).toFloat(),paint(color))
    val segmentY get() = 10+height(12.0)+2+height(28.0,true)+4+height(15.0)+16
    fun draw(c: Canvas,w: Double,h: Double,top: Double,bottom: Double,o: M.Frame,reduced: Boolean) {
        c.drawColor(0xfffcf8f1.toInt())
        var y=top+10
        text(c,"THIS WEEK",24.0,y,12.0,0xB09A7A,track=1.3); y+=height(12.0)+2
        text(c,"Mindful minutes",24.0,y,28.0,0x2A2633,heavy=true); y+=height(28.0,true)+4
        val d=if(o.scrub>=0)floor(o.scrub+0.5).toInt() else -1
        text(c,if(d>=0)"${M.days[d]} · ${M.minutes[d]} min" else "128 min · 18% more than last week",24.0,y,15.0,0x7D7488); y+=height(15.0)+16
        rr(c,24.0,y,212.0,40.0,20.0,0xF0E8DC); rr(c,28+M.clamp(o.m)*104,y+4,104.0,32.0,16.0,0xFFFFFF)
        text(c,"Garden",79.0,y+(40-height(14.0))/2,14.0,if(o.m<=0.5)0x2A2633 else 0x9A93A6,center=true)
        text(c,"Chart",181.0,y+(40-height(14.0))/2,14.0,if(o.m>0.5)0x2A2633 else 0x9A93A6,center=true)
        val tw=(w-60)/3; val ty=h-bottom-94
        for(i in 0..2) {
            val x=20+i*(tw+10); val nums=listOf("5","30","18"); val subs=listOf("day streak","best day · Sat","daily average")
            rr(c,x,ty,tw,74.0,20.0,0xFFFFFF)
            val ht=height(22.0,true)+height(12.0)
            text(c,nums[i],x+12,ty+(74-ht)/2,22.0,0x2A2633,heavy=true)
            text(c,subs[i],x+12,ty+(74-ht)/2+height(22.0,true),12.0,0x9A93A6)
        }
        garden(c,w,h,o,reduced)
    }
    private fun garden(c: Canvas,w: Double,h: Double,o: M.Frame,reduced: Boolean) {
        val base=round(h*0.7); val hmax=round(h*0.34); val step=(w-92)/6; val m=o.m
        line(c,16.0,base,w-16,base,0xE7DED0,1.0,M.mix(10.0,2.0,m))
        for(k in 1..3) { val y=base-hmax*k/3; line(c,16.0,y,w-16,y,0xE7DED0,m*0.8,1.0) }
        for(i in 0..6) {
            val x=46+step*i; val ht=M.minutes[i]/30.0*hmax
            val sel=if(o.scrub>=0)M.clamp(1-abs(o.scrub-i)) else 0.0
            val potH=26*(1-m); val sway=if(reduced)0.0 else sin(o.t*2*PI*(if(i%2==1)1 else 2)/M.LOOP+i*1.3)*7*(1-m)
            val topY=base-ht-sel*6*m; val startY=M.mix(base-potH,base-13,m)
            val width=M.mix(5.0,26.0,m); val bend=(1-m)*(if(i%2==1)10 else -10)
            val ck=M.smoother((m-0.15)/0.55); val a=0x5DAE5B; val b=M.colors[i]
            val rgb=listOf(16,8,0).map { M.mix(((a shr it) and 255).toDouble(),((b shr it) and 255).toDouble(),ck).roundToInt() }
            path.reset(); path.moveTo(x.toFloat(),startY.toFloat()); path.cubicTo((x+bend).toFloat(),M.mix(startY,topY,0.35).toFloat(),(x-bend+sway*0.5).toFloat(),M.mix(startY,topY,0.7).toFloat(),(x+sway).toFloat(),(topY+width/2).toFloat())
            c.drawPath(path,paint((rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2],stroke=width))
            val lk=1-M.smoother(m*1.6)
            if(lk>0.01)for(k in 0..1) {
                val q=0.35+k*0.25; val dir=if(k%2==1)1 else -1
                c.save(); c.translate((x+sway*q).toFloat(),M.mix(startY,topY,q).toFloat()); c.rotate((dir * -35-sway).toFloat()); c.scale(lk.toFloat(),lk.toFloat())
                rect.set(if(dir>0)0f else -18f,-5f,if(dir>0)18f else 0f,5f); c.drawOval(rect,paint(0x6CC46A)); c.restore()
            }
            val pk=1-m; val fx=x+sway; val fy=topY+width/2
            if(pk>0.01) {
                for(k in 0..5) { val angle=k/6.0*PI*2+o.t*(PI/9)*(if(reduced)0 else 1); circle(c,fx+cos(angle)*9*pk,fy-6*pk+sin(angle)*9*pk,6.5*pk,M.colors[i]) }
                circle(c,fx,fy-6*pk,5*pk,0xFFE07A)
            }
            if(potH>0.5) {
                path.reset(); path.moveTo((x-17).toFloat(),(base-potH).toFloat()); path.lineTo((x+17).toFloat(),(base-potH).toFloat()); path.lineTo((x+13).toFloat(),base.toFloat()); path.lineTo((x-13).toFloat(),base.toFloat()); path.close(); c.drawPath(path,paint(0xD9825B))
                rr(c,x-19,base-potH-2,38.0,min(8.0,potH),3.0,0xE89A73)
            }
            text(c,listOf("M","T","W","T","F","S","S")[i],x,base+24,14.0,if(sel>0.5)0x2A2633 else 0x9A93A6,center=true,baseline=true)
            val vk=M.clamp((m-0.6)/0.4)
            if(vk>0)text(c,M.minutes[i].toString(),x,topY-8,14.0,0x2A2633,center=true,alpha=vk*(0.55+0.45*sel),baseline=true)
        }
    }
}
