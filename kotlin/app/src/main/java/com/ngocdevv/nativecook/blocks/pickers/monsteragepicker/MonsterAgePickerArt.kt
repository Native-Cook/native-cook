package com.ngocdevv.nativecook.blocks.pickers.monsteragepicker

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class MonsterAgePickerArt(context: Context) {
    private val black = Typeface.createFromAsset(context.assets,"monsteragepicker/monsteragepicker_Nunito-Black.ttf")
    private val title = Typeface.createFromAsset(context.assets,"monsteragepicker/monsteragepicker_TitanOne-Regular.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val bounds = RectF()
    private var gradient: RadialGradient? = null
    private var gradientW = 0f
    private var gradientH = 0f
    private fun color(hex: Int, alpha: Double = 1.0) {
        paint.shader = null; paint.style = Paint.Style.FILL
        paint.color = hex or 0xFF000000.toInt(); paint.alpha = (255*alpha).roundToInt()
    }
    private fun rect(c: Canvas,x:Double,y:Double,w:Double,h:Double,r:Double,hex:Int,a:Double=1.0) {
        color(hex,a); c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)
    }
    private fun oval(c:Canvas,x:Double,y:Double,w:Double,h:Double,hex:Int,a:Double=1.0) {
        color(hex,a); c.drawOval(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint)
    }
    private fun circle(c:Canvas,x:Double,y:Double,r:Double,hex:Int) {
        color(hex); c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint)
    }
    private fun text(c:Canvas,s:String,size:Double,x:Double,y:Double,hex:Int,a:Double=1.0,heading:Boolean=false) {
        color(hex,a); paint.typeface=if(heading)title else black; paint.textSize=size.toFloat()
        c.drawText(s,(x-paint.measureText(s)/2).toFloat(),y.toFloat(),paint)
    }
    fun draw(c:Canvas,width:Double,height:Double,top:Double,bottom:Double,o:MonsterAgePickerMotion.Frame,chosen:Int,pressed:Boolean) {
        val W=width; val cx=W/2; val cy=round(height*0.5); val R=W*0.5
        color(0x6E3DE8); c.drawPaint(paint)
        for(i in 0..3)rect(c,cx-89+i*46,top+10,40.0,6.0,3.0,if(i<2)0xFFD84D else 0xFFFFFF,if(i<2)1.0 else 0.25)
        oval(c,cx-R*0.8,cy+R*0.95,R*1.6,R*0.22,0x3A1680,0.25)
        circle(c,cx-R*0.72,cy-R*0.62,R*0.2,0x8E62F5)
        circle(c,cx+R*0.72,cy-R*0.62,R*0.2,0x8E62F5)
        if(gradientW!=W.toFloat() || gradientH!=height.toFloat()) {
            gradientW=W.toFloat();gradientH=height.toFloat()
            gradient=RadialGradient((cx-R*0.3).toFloat(),(cy-R*0.5).toFloat(),(R*1.6).toFloat(),intArrayOf(0xFFB996FF.toInt(),0xFF8C5CF6.toInt()),null,Shader.TileMode.CLAMP)
        }
        color(0xFFFFFF);paint.shader=gradient
        c.drawRoundRect((cx-R*0.92).toFloat(),(cy-R*0.85).toFloat(),(cx+R*0.92).toFloat(),(cy+R).toFloat(),(R*0.8).toFloat(),(R*0.8).toFloat(),paint)
        val happy=MonsterAgePickerMotion.clamp((o.jaw-0.4)/0.4)
        for(k in listOf(-1,1)) {
            val ex=cx+k*R*0.34;val ey=cy-R*0.5;val lx=o.look.coerceIn(-1.0,1.0)*R*0.07
            circle(c,ex,ey,R*0.2,0xFFFFFF);circle(c,ex+lx,ey+R*0.03,R*0.095,0x24123F)
            circle(c,ex+lx+R*0.03,ey-R*0.01,R*0.03,0xFFFFFF)
            val lid=max(o.blink,happy)
            if(lid>0) {
                c.save();c.clipRect((ex-R*0.22).toFloat(),(ey-R*0.22).toFloat(),(ex+R*0.22).toFloat(),(ey-R*0.22+R*0.44*lid).toFloat())
                circle(c,ex,ey,R*0.205,0x9D72FA);c.restore()
                if(happy>0) {
                    color(0x24123F,happy);paint.style=Paint.Style.STROKE;paint.strokeWidth=5f;paint.strokeCap=Paint.Cap.ROUND
                    bounds.set((ex-R*0.13).toFloat(),(ey-R*0.02).toFloat(),(ex+R*0.13).toFloat(),(ey+R*0.18).toFloat())
                    c.drawArc(bounds,200f,140f,false,paint)
                }
            }
        }
        val mw=W-76+W*min(1.0,abs(o.v)/12)*0.06
        val mBot=cy+R*0.72;val openTop=cy-R*0.14;val mTop=openTop+(mBot-90-openTop)*o.jaw
        bounds.set((cx-mw/2).toFloat(),mTop.toFloat(),(cx+mw/2).toFloat(),mBot.toFloat())
        path.reset();path.addRoundRect(bounds,46f,46f,Path.Direction.CW)
        color(0x3B1460);c.drawPath(path,paint);c.save();c.clipPath(path)
        oval(c,cx-mw*0.34,mBot-46,mw*0.68,90.0,0xFF7BA6)
        for(i in -3..3) {
            val tx=cx+i*48;path.reset();path.moveTo((tx-18).toFloat(),(mTop-2).toFloat());path.lineTo((tx+18).toFloat(),(mTop-2).toFloat());path.lineTo(tx.toFloat(),(mTop+26).toFloat());path.close()
            color(0xF4EEFF);c.drawPath(path,paint)
        }
        for(i in 0..11) {
            val dx=(i-o.off)*60;if(abs(dx)>mw/2+60)continue
            val near=MonsterAgePickerMotion.clamp(1-abs(i-o.off));val h=54+14*near;val ty=mBot-16-h+dx*dx/2600*0.2
            rect(c,cx+dx-25,ty,50.0,h+30,16.0,if(near>0.5)0xFFFFFF else 0xE9DEFF)
            if(near>0.01)rect(c,cx+dx-25,ty,50.0,h+30,16.0,0xFFF7C2,0.35*near)
            text(c,"${i+3}",if(near>0.5)28.0 else 22.0,cx+dx,ty+34+4*near,if(near>0.5)0x3B1460 else 0x8B74B8)
        }
        c.restore()
        val buttonY=height-bottom-78
        c.save();if(pressed)c.scale(0.96f,0.96f,cx.toFloat(),(buttonY+29).toFloat())
        rect(c,24.0,buttonY,W-48,58.0,29.0,0xE0AE1F)
        c.save();c.clipRect(24f,buttonY.toFloat(),(W-24).toFloat(),(buttonY+54).toFloat());rect(c,24.0,buttonY,W-48,58.0,29.0,0xFFD84D);c.restore()
        c.restore()
    }
}
