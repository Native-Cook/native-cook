package com.ngocdevv.nativecook.blocks.onboarding.cashhandoffonboarding

import android.graphics.*
import kotlin.math.*

internal class CashHandoffOnboardingArt(private val r: CashHandoffOnboardingResources) {
    private val m = CashHandoffOnboardingMotion
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val rect=RectF()
    private fun color(hex: Int) = hex or (0xff shl 24)
    private fun fill(hex: Int, alpha: Double=1.0) { paint.shader=null; paint.style=Paint.Style.FILL; paint.color=color(hex); paint.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt() }
    private fun stroke(hex: Int, width: Double, alpha: Double=1.0) { fill(hex,alpha); paint.style=Paint.Style.STROKE; paint.strokeWidth=width.toFloat(); paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND }
    private fun rr(c: Canvas, x: Double,y: Double,w: Double,h: Double,r: Double) { rect.set(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()); c.drawRoundRect(rect,r.toFloat(),r.toFloat(),paint) }
    private fun circle(c: Canvas,x: Double,y: Double,r: Double) { c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint) }
    private fun text(c: Canvas,s: String,x: Double,y: Double,size: Double,font: Typeface,hex: Int,alpha: Double=1.0) { fill(hex,alpha); paint.typeface=font; paint.textSize=size.toFloat(); c.drawText(s,x.toFloat(),y.toFloat(),paint) }
    private fun finger(c: Canvas,x: Double,y: Double,a: Double,l1: Double,l2: Double,bend: Double,w: Double,hex: Int) {
        val x1=x+cos(a)*l1; val y1=y+sin(a)*l1
        path.reset(); path.moveTo(x.toFloat(),y.toFloat()); path.lineTo(x1.toFloat(),y1.toFloat()); path.lineTo((x1+cos(a+bend)*l2).toFloat(),(y1+sin(a+bend)*l2).toFloat())
        stroke(0x2a170c,w+3); c.drawPath(path,paint); stroke(hex,w); c.drawPath(path,paint)
    }
    private fun hand(c: Canvas,x: Double,y: Double,dir: Double,curl: Double,up: Boolean,layer: Int) {
        c.save(); c.translate(x.toFloat(),y.toFloat()); c.scale(dir.toFloat(),1f)
        val s=if(up)-1.0 else 1.0
        if(layer==0) {
            path.reset();path.moveTo(-260f,-20f);path.lineTo(0f,-22f);path.lineTo(0f,22f);path.lineTo(-260f,26f);path.close()
            fill(0x8a5636);c.drawPath(path,paint);stroke(0x2a170c,3.0);c.drawPath(path,paint)
            fill(0x8a5636);rr(c,-6.0,-26.0,52.0,50.0,20.0);stroke(0x2a170c,3.0);rr(c,-6.0,-26.0,52.0,50.0,20.0)
            fill(0x6a3f26); val sy=if(up)-8f else 2f; rect.set(8f,sy,38f,sy+12);c.drawOval(rect,paint)
            finger(c,14.0,if(up)-18.0 else 18.0,(if(up)-0.9 else 0.9)+curl*(if(up)0.5 else -0.5),20.0,15.0,s*curl*0.9,13.0,0x8a5636)
        } else for(i in 0..3) {
            val len=if(i==0 || i==3)0.85 else 1.0
            finger(c,42.0,-18.0+i*12,(i-1.5)*0.06+s*curl*1.1,22*len,18*len,s*curl*1.4,12.0,if(i%2==0)0x8a5636 else 0x93603e)
        }
        c.restore()
    }
    private fun cash(c: Canvas,x: Double,y: Double,rot: Double,fan: Double) {
        c.save();c.translate(x.toFloat(),y.toFloat());c.rotate(rot.toFloat())
        for(i in 0..2) {
            c.save();c.rotate(((i-1)*(6+10*fan)).toFloat());c.translate((i-1)*3f,(i-1)*-3f)
            fill(if(i==2)0x9bd48a else 0x86c574);rr(c,-46.0,-23.0,92.0,46.0,5.0)
            stroke(0x2f5a26,2.6);rr(c,-46.0,-23.0,92.0,46.0,5.0);stroke(0x4f8a42,1.4);rr(c,-40.0,-17.0,80.0,34.0,3.0)
            if(i==2) { fill(0xc7ecb8);circle(c,0.0,0.0,13.0);stroke(0x2f5a26,1.6);circle(c,0.0,0.0,13.0);text(c,"$",-5.0,6.0,15.0,r.bold,0x2f5a26);fill(0xc7ecb8);circle(c,-32.0,0.0,4.0);circle(c,32.0,0.0,4.0) }
            c.restore()
        }
        c.restore()
    }
    fun draw(c: Canvas,l: CashHandoffOnboardingMotion.Layout,sc: CashHandoffOnboardingMotion.Scene,t: Double,now: Double,pressed: Boolean) {
        c.drawColor(Color.WHITE)
        fill(0xc391f4)
        path.reset();rect.set(0f,0f,l.width.toFloat(),l.panel.toFloat());path.addRoundRect(rect,floatArrayOf(0f,0f,0f,0f,34f,34f,34f,34f),Path.Direction.CW);c.drawPath(path,paint)
        text(c,"RECEIVE",24.0,l.top+40+62,64.0,r.display,0xffffff)
        text(c,"Accept money directly from friends or family",24.0,l.top+40+72+4+15,14.0,r.medium,0xffffff,0.92)
        text(c,"without the usual bank delays.",24.0,l.top+40+72+4+35,14.0,r.medium,0xffffff,0.92)
        c.save(); c.clipRect(0f,0f,l.width.toFloat(),l.panel.toFloat())
        val since=now-sc.caughtAt
        if(sc.caughtAt > -5 && since>=0 && since<1.8) {
            c.save();path.reset();rect.set(0f,-40f,l.width.toFloat(),l.panel.toFloat());path.addRoundRect(rect,34f,34f,Path.Direction.CW);c.clipPath(path)
            fill(0xffffff);paint.shader=RadialGradient((l.px+sc.rOff).toFloat(),l.py.toFloat(),(120+260*m.smooth(since/0.9)).toFloat(),intArrayOf(0xf296ff8c.toInt(),0x736eeb96,0x006eeb96),floatArrayOf(0f,0.45f,1f),Shader.TileMode.CLAMP)
            paint.alpha=(min(1.0,since/0.15)*(1-m.smooth((since-0.5)/1.3))*255).roundToInt();c.drawRect(0f,0f,l.width.toFloat(),l.panel.toFloat(),paint);paint.shader=null;c.restore()
        }
        val bob=sin(2*PI*2*t/m.LOOP)*2
        hand(c,70+sc.gOff,l.top+300+bob,1.0,sc.gCurl,false,0);hand(c,l.width-70+sc.rOff,l.top+372-bob,-1.0,sc.rCurl,true,0)
        cash(c,sc.cx,sc.cy+(if(sc.caughtAt<0)bob else -bob),sc.rot,sc.fan)
        hand(c,70+sc.gOff,l.top+300+bob,1.0,sc.gCurl,false,1);hand(c,l.width-70+sc.rOff,l.top+372-bob,-1.0,sc.rCurl,true,1)
        if(sc.caughtAt > -5 && since>=0 && since<1.6) {
            val d=30+(1-exp(-since*6))*46
            stroke(0xffffff,2.0,1-m.smooth((since-0.4)/0.6))
            for(i in 0..7) { val a=i/8.0*PI*2;circle(c,l.px+sc.rOff+cos(a)*d,l.py+sin(a)*d,3.0+i%2*2) }
            text(c,"+$50",l.px+sc.rOff-26,l.py-60-m.easeOut(since/1.6)*40,22.0,r.bold,0xffffff,sin(m.clamp(since/1.6)*PI))
        }
        c.restore()
        val mid=l.width/2
        fill(0xc9c9cf);rr(c,mid-11,l.panel+39,4.0,16.0,2.0);rr(c,mid+7,l.panel+39,4.0,16.0,2.0)
        fill(0x2a2a2e);rr(c,mid-2,l.panel+34,4.0,26.0,2.0)
        val buttonY=l.height-l.bottom-16-89
        c.save(); if(pressed) { c.scale(0.97f,0.97f,mid.toFloat(),(buttonY+28).toFloat()) }
        fill(0x2c2c30);rr(c,20.0,buttonY,l.width-40,56.0,28.0)
        paint.typeface=r.semiBold;paint.textSize=16f
        val ctaW=paint.measureText("Get Started").toDouble();text(c,"Get Started",mid-ctaW/2,buttonY+34,16.0,r.semiBold,0xffffff);c.restore()
        paint.typeface=r.medium;paint.textSize=14f;val first=paint.measureText("Already have an account? ")
        paint.typeface=r.semiBold;val second=paint.measureText("Log in");val left=mid-(first+second)/2
        text(c,"Already have an account? ",left,buttonY+86,14.0,r.medium,0x3a3a40);text(c,"Log in",left+first,buttonY+86,14.0,r.semiBold,0x111111)
    }
}
