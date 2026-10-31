package com.ngocdevv.nativecook.blocks.modals.firstlootreward

import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

internal class FirstLootRewardArt {
    private val m=FirstLootRewardMotion
    private val ro=155/sqrt(3.0)
    private val ri=102/sqrt(3.0)
    private val outer=hex(ro,15.0)
    private val face=hex(ro-9,11.0)
    private val inner=hex(ri,11.0)
    private val rays=if(Build.VERSION.SDK_INT>=33)FirstLootRewardRays() else null
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textCache=mutableMapOf<String,StaticLayout>()
    private val chipTextWidth=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=12.5f
        typeface=if(Build.VERSION.SDK_INT>=28)Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),500,false)
        else Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }.measureText("Announcement").toDouble()
    private val rim: Bitmap
    private val faceRecess: Bitmap
    private val body: Bitmap
    private val lid: Bitmap
    private val imageRect=RectF(-150f,-150f,150f,150f)
    private val bell=Path().apply {
        moveTo(5.671875f,9.703125f)
        quadTo(5.7890625f,9.7265625f,5.98828125f,9.73828125f)
        quadTo(6.1875f,9.75f,6.3046875f,9.703125f)
        quadTo(7.03125f,9.515625f,7.3359375f,8.765625f)
        lineTo(7.3828125f,8.6015625f)
        lineTo(7.640625f,8.484375f)
        quadTo(8.0390625f,8.296875f,8.3203125f,8.0390625f)
        quadTo(9.1640625f,7.3359375f,9.3515625f,6.0f)
        quadTo(9.3515625f,5.8359375f,9.375f,5.203125f)
        quadTo(9.3984375f,4.3359375f,9.515625f,3.890625f)
        quadTo(9.65625f,3.3046875f,10.265625f,2.5546875f)
        quadTo(10.4765625f,2.2734375f,10.5f,1.96875f)
        quadTo(10.5f,1.78125f,10.4296875f,1.640625f)
        quadTo(10.265625f,1.2890625f,9.890625f,1.171875f)
        quadTo(9.796875f,1.125f,6.0f,1.125f)
        lineTo(2.203125f,1.125f)
        lineTo(2.0859375f,1.171875f)
        quadTo(1.734375f,1.3125f,1.5703125f,1.640625f)
        quadTo(1.5f,1.78125f,1.5f,1.96875f)
        quadTo(1.5234375f,2.2734375f,1.734375f,2.5546875f)
        quadTo(2.34375f,3.3046875f,2.484375f,3.890625f)
        quadTo(2.6015625f,4.3125f,2.625f,5.203125f)
        quadTo(2.6484375f,6.09375f,2.765625f,6.515625f)
        quadTo(3.09375f,7.8515625f,4.359375f,8.484375f)
        lineTo(4.6171875f,8.6015625f)
        lineTo(4.6640625f,8.765625f)
        quadTo(4.96875f,9.515625f,5.671875f,9.703125f)
        close()
        moveTo(4.3828125f,0.3515625f)
        quadTo(4.3828125f,0.3515625f,4.3828125f,0.3515625f)
        quadTo(4.40625f,0.375f,6.0f,0.375f)
        quadTo(7.59375f,0.375f,7.6171875f,0.3515625f)
        quadTo(7.6875f,0.28125f,7.5703125f,0.1171875f)
        quadTo(7.171875f,-0.4921875f,6.4453125f,-0.703125f)
        quadTo(6.0f,-0.796875f,5.5546875f,-0.703125f)
        quadTo(4.828125f,-0.4921875f,4.4296875f,0.1171875f)
        quadTo(4.3125f,0.28125f,4.3828125f,0.3515625f)
        close()
    }
    private val swirl=Path().apply {
        moveTo(250f,160f); cubicTo(250f,90f,330f,40f,370f,40f)
        cubicTo(420f,40f,420f,120f,360f,120f); cubicTo(300f,120f,300f,20f,360f,-10f)
    }
    private val sparkles=arrayOf(
        doubleArrayOf(3.0,-106.0,9.0,1.0,0.0),doubleArrayOf(111.0,-12.0,9.0,1.0,1.0),doubleArrayOf(-126.0,20.0,4.5,1.0,1.0),doubleArrayOf(75.0,70.0,4.5,1.0,0.0),
        doubleArrayOf(-75.0,-76.0,3.0,0.0,0.0),doubleArrayOf(93.0,-60.0,3.0,0.0,0.0),doubleArrayOf(-71.0,83.0,3.0,0.0,1.0),doubleArrayOf(-111.0,-87.0,1.5,0.0,1.0),
        doubleArrayOf(50.0,-100.0,1.5,0.0,1.0),doubleArrayOf(105.0,-49.0,1.5,0.0,1.0),doubleArrayOf(119.0,10.0,1.5,0.0,1.0),doubleArrayOf(-89.0,58.0,1.5,0.0,1.0),
        doubleArrayOf(90.0,44.0,1.5,0.0,1.0),doubleArrayOf(-115.0,-2.0,1.0,0.0,1.0),doubleArrayOf(-45.0,-102.0,1.0,0.0,1.0))
    init {
        rim=bitmap { c -> c.drawPath(outer,gradient(0.0,-ro,0.0,ro,intArrayOf(0xe3c9ff,0xc697f8,0xad80ea,0x7f5aca),floatArrayOf(0f,0.12f,0.55f,1f))) }
        faceRecess=bitmap { c ->
            c.drawPath(face,gradient(-ro*0.4,-ro,ro*0.4,ro,intArrayOf(0xb386ef,0x8a62d2)))
            c.drawPath(inner,radial(0.0,6.0,ri*1.05,intArrayOf(col(0x6f4cc4),col(0x3f2a88))))
            c.save(); c.clipPath(inner)
            val g=gradient(0.0,-ri,0.0,ri,intArrayOf(0x1e0c50,0x1e0c50,0xaa8cf0),floatArrayOf(0f,0.55f,1f),floatArrayOf(0.85f,0.15f,0.55f))
            g.style=Paint.Style.STROKE; g.strokeWidth=9f; c.drawPath(inner,g); c.restore()
        }
        body=bitmap { block(it,-27.0,4.0,54.0,19.0,3.0,7.0,0xc8b0fc,0xa587ee) }
        lid=bitmap { c ->
            block(c,-31.0,-25.0,62.0,28.0,15.0,4.0,0xeadfff,0xc7aefb)
            val band=Path().apply { addRoundRect(rect(-32.0,-1.0,64.0,11.0),5f,5f,Path.Direction.CW) }
            c.drawPath(band,stroke(0x4e2aa0,2.6)); c.drawPath(band,gradient(0.0,-1.0,0.0,10.0,intArrayOf(0xf1e9ff,0xddcdff,0xbfa4f6),floatArrayOf(0f,0.5f,1f)))
            c.save(); c.translate(0f,4.5f); c.rotate(45f)
            c.drawRoundRect(rect(-10.0,-10.0,20.0,20.0),6f,6f,stroke(0x4e2aa0,2.8))
            c.drawRoundRect(rect(-10.0,-10.0,20.0,20.0),6f,6f,paint(0xf3ecff))
            c.drawRoundRect(rect(-8.0,-8.0,16.0,16.0),5f,5f,stroke(0xd5c3fb,1.2)); c.restore()
            c.drawPath(star(0.0,4.5,6.4),paint(0x7b4fe0))
        }
    }
    private fun col(v: Int,a: Double=1.0)=((a.coerceIn(0.0,1.0)*255).roundToInt() shl 24) or v
    private fun rect(x: Double,y: Double,w: Double,h: Double)=RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
    private fun paint(color: Int=0xffffff,a: Double=1.0): Paint {
        p.reset(); p.isAntiAlias=true; p.color=col(color,a); return p
    }
    private fun stroke(color: Int,width: Double,a: Double=1.0)=paint(color,a).apply { style=Paint.Style.STROKE; strokeWidth=width.toFloat(); strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private fun gradient(x0: Double,y0: Double,x1: Double,y1: Double,colors: IntArray,stops: FloatArray?=null,alphas: FloatArray?=null)=paint().apply {
        shader=LinearGradient(x0.toFloat(),y0.toFloat(),x1.toFloat(),y1.toFloat(),colors.mapIndexed { i,c -> col(c,alphas?.get(i)?.toDouble()?:1.0) }.toIntArray(),stops,Shader.TileMode.CLAMP)
    }
    private fun radial(x: Double,y: Double,r: Double,colors: IntArray,stops: FloatArray?=null,a: Double=1.0)=paint(0xffffff,a).apply { shader=RadialGradient(x.toFloat(),y.toFloat(),r.toFloat(),colors,stops,Shader.TileMode.CLAMP) }
    private fun circle(c: Canvas,x: Double,y: Double,r: Double,color: Int,a: Double=1.0) { c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),paint(color,a)) }
    private fun blur(color: Int,a: Double,sigma: Double)=paint(color,a).apply { maskFilter=BlurMaskFilter(sigma.toFloat(),BlurMaskFilter.Blur.NORMAL) }
    private fun hex(r: Double,rc: Double)=Path().apply {
        val ri=(r*cos(PI/6)-rc)/cos(PI/6)
        for(i in 0..5) {
            val a=PI/6+i*PI/3
            for(j in 0..10) {
                val n=a-PI/6+j/10.0*PI/3
                val x=(ri*cos(a)+rc*cos(n)).toFloat(); val y=(ri*sin(a)+rc*sin(n)).toFloat()
                if(i==0 && j==0)moveTo(x,y) else lineTo(x,y)
            }
        }; close()
    }
    private fun star(x: Double,y: Double,s: Double)=Path().apply {
        val w=s*0.28*0.35
        moveTo(x.toFloat(),(y-s).toFloat()); quadTo((x+w).toFloat(),(y-w).toFloat(),(x+s).toFloat(),y.toFloat())
        quadTo((x+w).toFloat(),(y+w).toFloat(),x.toFloat(),(y+s).toFloat())
        quadTo((x-w).toFloat(),(y+w).toFloat(),(x-s).toFloat(),y.toFloat()); quadTo((x-w).toFloat(),(y-w).toFloat(),x.toFloat(),(y-s).toFloat()); close()
    }
    private fun bitmap(draw: (Canvas)->Unit): Bitmap {
        val b=Bitmap.createBitmap(900,900,Bitmap.Config.ARGB_8888); val c=Canvas(b)
        c.scale(3f,3f); c.translate(150f,150f); draw(c); return b
    }
    private fun block(c: Canvas,x: Double,y: Double,w: Double,h: Double,rt: Double,rb: Double,top: Int,bot: Int) {
        val shape=Path().apply { addRoundRect(rect(x,y,w,h),floatArrayOf(rt.toFloat(),rt.toFloat(),rt.toFloat(),rt.toFloat(),rb.toFloat(),rb.toFloat(),rb.toFloat(),rb.toFloat()),Path.Direction.CW) }
        c.drawPath(shape,stroke(0x4e2aa0,2.6)); c.drawPath(shape,gradient(x,y,x+w*0.25,y+h,intArrayOf(top,bot)))
        c.save(); c.clipPath(shape)
        for(sx in doubleArrayOf(-12.5,6.5)) {
            c.drawRect(rect(sx,y,6.0,h),gradient(0.0,y,0.0,y+h,intArrayOf(0x9a6ff0,0x7b4fd8)))
            c.drawRect(rect(sx,y,1.2,h),paint(0x4e2aa0,0.35))
        }
        c.drawRect(rect(x+3,y+2,3.5,h-4),paint(0xffffff,0.4)); c.drawRect(rect(-4.5,y+2,2.5,h-4),paint(0xffffff,0.3)); c.drawRect(rect(15.0,y+2,2.5,h-4),paint(0xffffff,0.25))
        c.drawRect(rect(x+w-4,y,4.0,h),paint(0x7b4fd8,0.3)); c.restore()
    }
    fun draw(c: Canvas,w: Double,h: Double,top: Double,bottom: Double,f: FirstLootRewardMotion.Frame) {
        val bx=w/2; val by=top+182
        c.drawColor(col(0x090909))
        if(f.sceneA>0.01) {
            if(Build.VERSION.SDK_INT>=33 && rays!=null) {
                c.drawRect(rect(0.0,0.0,w,h),paint().apply { shader=rays.update(bx.toFloat(),by.toFloat(),f.sceneA.toFloat(),f.tx.toFloat(),f.ty.toFloat()) })
            } else {
                c.save(); c.translate((bx-f.tx*10).toFloat(),(by-f.ty*10).toFloat())
                for(i in 0..13) { c.save(); c.rotate((i*360.0/14).toFloat()); c.drawArc(rect(-470.0,-470.0,940.0,940.0),10.8f,14.4f,true,radial(0.0,0.0,470.0,intArrayOf(col(0x0f0c15,0.0),col(0x0f0c15,f.sceneA),col(0x0f0c15,0.0)),floatArrayOf(0.085f,0.32f,1f))); c.restore() }; c.restore()
            }
        }
        c.drawCircle(bx.toFloat(),by.toFloat(),230f,radial(bx,by,230.0,intArrayOf(col(0x3a206e,0.5),col(0x221242,0.2),col(0x140a28,0.0)),floatArrayOf(0f,0.4f,1f),f.sceneA))
        if(f.sceneA>0.01 && f.sparkA>0.01) for((i,s) in sparkles.withIndex()) {
            val pop=m.out(m.seg(f.sparkA,i*0.04,i*0.04+0.45)); val a=pop*f.sceneA
            if(a<=0.01)continue
            val x=bx+s[0]-f.tx*(4+s[2]); val y=by+s[1]-f.ty*(4+s[2])
            if(s[3]==1.0)c.drawPath(star(x,y,s[2]*(0.6+0.4*pop)),paint(if(s[4]==0.0)0x6a38c8 else 0xcdb9f7,min(1.0,a*1.2)))
            else circle(c,x,y,s[2]*pop,if(s[4]==0.0)0x6a38c8 else 0x7a64a0,min(1.0,a*1.1))
        }
        val o=f.chest.since
        if(o>=0 && o<=1.2) {
            val a=m.smooth(o/0.06)*(1-m.smooth(m.seg(o,0.35,0.8))); val r=150*(0.85+0.15*f.power)
            if(a>0.01)c.drawCircle(bx.toFloat(),by.toFloat(),r.toFloat(),radial(bx,by,r,intArrayOf(col(0xebd7ff),col(0xaf78fa,0.82),col(0x7846dc,0.0)),floatArrayOf(0f,0.5f,1f),a))
        }
        if(f.ring>0.01) {
            c.drawCircle(bx.toFloat(),by.toFloat(),33f,stroke(0xc39bff,4.0,0.35*f.ring).apply { maskFilter=BlurMaskFilter(4f,BlurMaskFilter.Blur.NORMAL) })
            c.drawCircle(bx.toFloat(),by.toFloat(),33f,stroke(0xb9a2e8,1.5,0.9*f.ring))
        }
        badge(c,bx,by,f)
        if(o>=0 && o<=1.6) {
            val rw=m.seg(o,0.08,0.6)
            if(rw>0 && rw<1)c.drawCircle(bx.toFloat(),by.toFloat(),(92+rw*80).toFloat(),stroke(0xcdb9f7,1.4,0.45*(1-rw)))
            for(i in 0..11) {
                val k=(o-0.12-m.hash(i*3.1)*0.45)/0.6
                if(k<=0 || k>=1)continue
                val x=bx+(m.hash(i*7.3)-0.5)*46+sin(k*6+i)*2; val y=by-4-k*(26+m.hash(i*1.9)*22); val a=sin(k*PI)*0.95
                if(i%3==0)c.drawPath(star(x,y,2.4),paint(0xffffff,a)) else circle(c,x,y,0.8+m.hash(i*5.5)*0.7,if(i%2==1)0xcdb9f7 else 0xffffff,a)
            }
        }
        controls(c,w,h,by,bottom,f)
        c.drawRect(rect(0.0,0.0,w,h),paint(0x090909,f.fade))
    }
    private fun badge(c: Canvas,bx: Double,by: Double,f: FirstLootRewardMotion.Frame) {
        if(f.badgeA<=0.005)return
        c.save(); c.translate(bx.toFloat(),by.toFloat()); val sc=(f.badgeS*(1-f.press*0.04)).toFloat(); c.scale(sc,sc); c.skew((f.tx*0.05).toFloat(),(f.ty*0.05).toFloat())
        c.saveLayerAlpha(-160f,-160f,160f,160f,(f.badgeA*255).roundToInt())
        c.drawPath(outer,blur(0x8e5cf0,0.35+0.5*f.charge,20+12*f.charge)); c.drawBitmap(rim,null,imageRect,paint())
        val turn=f.t/4*360+f.tx*120+f.ty*60
        c.drawPath(outer,paint(0xffffff,0.45*min(1.0,abs(f.tx)+abs(f.ty))+0.3*f.charge).apply {
            shader=SweepGradient(0f,0f,intArrayOf(col(0xffb3f0),col(0xb9a2ff),col(0x8fe8ff),col(0xc8ffd9),col(0xffd7a8),col(0xffb3f0)),null).apply { setLocalMatrix(Matrix().apply { setRotate(turn.toFloat()) }) }
        })
        c.drawBitmap(faceRecess,null,imageRect,paint())
        val tilt=abs(f.tx)+abs(f.ty)
        if(tilt>0.02) {
            val pos=f.tx*0.9
            c.drawPath(face,gradient((pos-0.35)*ro,-ro,(pos+0.35)*ro,ro,intArrayOf(0xffffff,0xffffff,0xffffff),floatArrayOf(0f,0.5f,1f),floatArrayOf(0f,0.9f,0f)).apply { alpha=(min(0.4,tilt*0.4)*255).roundToInt() })
            c.save(); c.clipPath(inner); c.drawBitmap(faceRecess,null,imageRect,paint()); c.restore()
        }
        c.save(); c.clipPath(inner); c.drawCircle(0f,4f,26f,blur(0xc39bff,0.18+0.5*f.chest.light,18.0)); c.restore()
        c.save(); c.translate((f.tx*3).toFloat(),(2+f.ty*3).toFloat()); c.scale(0.94f,0.94f)
        val ch=f.chest; val sq=ch.gather*0.07+ch.bump*0.05; val shake=if(ch.gather>0.5)sin(ch.gather*40)*1.6*(ch.gather-0.5) else 0.0
        c.translate(shake.toFloat(),23f); c.scale((1+sq*0.8).toFloat(),(1-sq).toFloat()); c.translate(0f,-23f)
        c.drawBitmap(body,null,imageRect,paint())
        if(ch.light>0.01) {
            c.drawOval(rect(-30.0,-10.0,60.0,26.0),blur(0xffffff,ch.light,6.0)); c.drawOval(rect(-22.0,-1.0,44.0,11.0),paint(0xffffff,ch.light)); c.drawCircle(0f,0f,30f,blur(0xf2e6ff,0.55*ch.light,16.0))
        }
        c.translate((-6*ch.lid).toFloat(),(-15*ch.lid).toFloat()); c.rotate((-24*ch.lid+f.tx*2).toFloat(),0f,6f); c.drawBitmap(lid,null,imageRect,paint())
        c.restore(); c.restore(); c.restore()
    }
    private fun text(c: Canvas,value: String,x: Double,y: Double,width: Double,size: Double,line: Double,track: Double,color: Int=0xffffff,a: Double=1.0,weight: Int=400,center: Boolean=false) {
        val key="$value|$width|$size|$line|$track|$color|$a|$weight|$center"
        val layout=textCache.getOrPut(key) {
            val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize=size.toFloat(); this.color=col(color,a)
                typeface=if(Build.VERSION.SDK_INT>=28)Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),weight,false)
                else if(weight>=600)Typeface.create("sans-serif",Typeface.BOLD)
                else Typeface.create(if(weight>=500)"sans-serif-medium" else "sans-serif",Typeface.NORMAL)
                letterSpacing=(track/size).toFloat()
            }
            StaticLayout.Builder.obtain(value,0,value.length,tp,max(1,width.roundToInt())).setAlignment(if(center)Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing((line-(tp.fontMetrics.descent-tp.fontMetrics.ascent)).toFloat(),1f).build()
        }
        c.save(); c.translate(x.toFloat(),y.toFloat()); layout.draw(c); c.restore()
    }
    private fun controls(c: Canvas,w: Double,h: Double,by: Double,bottom: Double,f: FirstLootRewardMotion.Frame) {
        val title=by+169; val btnY=h-bottom-68; val cardY=btnY-157; val cw=w-40
        fun layer(a: Double,draw: ()->Unit) { c.saveLayerAlpha(null,(a*255).roundToInt()); draw(); c.restore() }
        var a=m.presence(f.t,1.07,0.38)
        layer(a) {
            text(c,"You just earned your",20.0,title+(1-a)*6,w-40,29.5,34.0,-0.45,center=true)
            text(c,"first Loot.",20.0,title+34+(1-a)*6,w-40,29.5,34.0,-0.45,center=true)
        }
        a=m.presence(f.t,1.2,0.32)
        layer(a) { text(c,"Nice start! Keep playing, keep winning, and watch your rewards stack up.",36.0,title+83+(1-a)*6,w-72,14.3,20.0,-0.1,0x9d9d9d,center=true) }
        a=m.presence(f.t,1.33,0.3)
        layer(a) {
            c.save(); c.translate(20f,(cardY+(1-a)*5).toFloat())
            val round=Path().apply { addRoundRect(rect(0.0,0.0,cw,133.0),20f,20f,Path.Direction.CW) }; c.clipPath(round)
            c.drawPath(round,gradient(0.0,133.0,cw,0.0,intArrayOf(0x4d2586,0x6a3fa6,0x8459c3)))
            c.drawPath(swirl,stroke(0xd6beff,24.0,0.16).apply { maskFilter=BlurMaskFilter(0.6f,BlurMaskFilter.Blur.NORMAL) })
            val chipW=8+12+4+chipTextWidth+9
            c.drawRoundRect(rect(16.0,14.0,chipW,23.0),11.5f,11.5f,paint(0xffffff,0.13))
            c.save(); c.translate(24f,29.98828125f); c.scale(1f,-1f); c.drawPath(bell,paint()); c.restore()
            text(c,"Announcement",40.0,17.0,ceil(chipTextWidth),12.5,16.0,0.0,weight=500)
            text(c,"Something big is coming 🎉",16.0,54.0,cw-32,17.0,22.0,-0.2)
            text(c,"Ember Rift drops October 24. Get ready to play, compete, and earn exclusive Loot.",16.0,81.0,cw-32,14.0,18.0,-0.1,0xf0e8ff,0.84)
            c.restore()
        }
        for(i in 0..1) {
            val alpha=m.presence(f.t,if(i==0)1.47 else 1.55,if(i==0)0.2 else 0.25); val bw=(w-48)/2
            layer(alpha) {
                c.save(); c.translate((20+i*(bw+8)+bw/2).toFloat(),(btnY+26+(1-alpha)*10).toFloat())
                val scale=(1-(if(i==0)f.keep else f.view)*0.04).toFloat(); c.scale(scale,scale)
                c.drawRoundRect(rect(-bw/2,-26.0,bw,52.0),26f,26f,paint(if(i==0)0x363638 else 0xfdfdfd))
                text(c,if(i==0)"Keep playing" else "View loot",-bw/2,-10.5,bw,17.0,20.3,-0.2,if(i==0)0xffffff else 0x0b0b0c,weight=600,center=true)
                c.restore()
            }
        }
    }
}
