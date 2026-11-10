package com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class PasskeyGlassSealArt {
    data class Layout(val w:Float,val h:Float,val safeTop:Float,val safeBottom:Float){
        val top=max(safeTop,44f);val bottom=max(safeBottom,20f)
        val sx=w/402f;val k=(h-top-bottom)/778f
        fun y(v:Float)=if(v<62)v*top/62 else top+(v-62)*k
        val s=min(sx,k);val cw=327*s;val ch=324*s
        val x=(35+327f/2)*sx-cw/2;val cy=y(276+324f/2)-ch/2
        val px=165*s;val py=161*s
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val light=Typeface.create("sans-serif-light",Typeface.NORMAL)
    private val regular=Typeface.create("sans-serif",Typeface.NORMAL)
    private val runtime=if(Build.VERSION.SDK_INT>=33)PasskeyGlassSealRuntime()else null
    private var cached:Layout?=null
    private var page:Shader?=null
    private var halo:Shader?=null
    private var card:Shader?=null
    private val outline=Path()
    private val mark=Path().apply{addArc(RectF(6f,6f,48f,48f),-90f,-270f)}
    private val triangle=Path().apply{moveTo(37f,5f);lineTo(49f,5f);lineTo(49f,17f);close()}
    private val check=Path().apply{moveTo(20f,33f);lineTo(28f,41f);lineTo(44f,24f)}
    private val ridges=Path().apply{PasskeyGlassSealPrint.ridges.forEach{r->r.pts.forEachIndexed{i,p->if(i==0)moveTo(p.x.toFloat(),p.y.toFloat())else lineTo(p.x.toFloat(),p.y.toFloat())}}}
    private data class Label(val chars:List<String>,val widths:List<Float>,val width:Float)
    private val labels=mutableMapOf<String,Label>()
    private fun color(hex:Int,alpha:Double=1.0)=Color.argb((alpha.coerceIn(0.0,1.0)*255).roundToInt(),hex shr 16 and 255,hex shr 8 and 255,hex and 255)
    private fun label(c:Canvas,text:String,size:Float,spacing:Float,x:Float,top:Float,alpha:Double=1.0,center:Boolean=true,bold:Boolean=false){
        paint.shader=null;paint.style=Paint.Style.FILL;paint.typeface=if(bold)regular else light;paint.textSize=size
        paint.color=color(0xFFFFFF,alpha)
        val key="$text|$size|$spacing|$bold"
        val data=labels.getOrPut(key){val chars=text.map{it.toString()};val widths=chars.map{paint.measureText(it)+spacing};Label(chars,widths,widths.sum())}
        var at=x-(if(center)data.width/2 else 0f)
        val baseline=top-paint.fontMetrics.top
        data.chars.forEachIndexed{i,s->c.drawText(s,at,baseline,paint);at+=data.widths[i]}
    }
    private fun prepare(l:Layout){
        if(l==cached)return
        cached=l
        page=LinearGradient(0f,0f,0f,l.h,intArrayOf(color(0xD2668A),color(0x8F5076),color(0x4D3B5D)),floatArrayOf(0f,0.5f,1f),Shader.TileMode.CLAMP)
        halo=RadialGradient(l.w*0.5f,-l.h*0.05f,l.w*0.9f,intArrayOf(color(0xE67092,0.55),color(0xE67092,0.0)),null,Shader.TileMode.CLAMP)
        card=LinearGradient(0f,0f,0f,l.ch,color(0x93537A),color(0x784A6C),Shader.TileMode.CLAMP)
        outline.reset();outline.addRoundRect(0f,0f,l.cw,l.ch,12f,12f,Path.Direction.CW)
    }
    fun draw(c:Canvas,l:Layout,f:DoubleArray,time:Double,reduced:Boolean){
        prepare(l)
        paint.style=Paint.Style.FILL;paint.alpha=255;paint.shader=page;c.drawRect(0f,0f,l.w,l.h,paint)
        paint.shader=halo;c.drawCircle(l.w/2,-l.h*0.05f,l.w*0.9f,paint);paint.shader=null
        c.save();c.translate(120*l.sx-27,l.y(170f)-27)
        paint.color=Color.WHITE;paint.style=Paint.Style.STROKE;paint.strokeWidth=7f;paint.strokeCap=Paint.Cap.BUTT;c.drawPath(mark,paint)
        paint.style=Paint.Style.FILL;c.drawPath(triangle,paint);c.drawCircle(27f,27f,5f,paint)
        label(c,"ORVANE",19f,10.5f,54+29*l.sx,15f,center=false)
        c.restore()
    }
    fun drawCard(c:Canvas,l:Layout,f:DoubleArray,time:Double,reduced:Boolean){
        prepare(l)
        val flip=f[4];val a=flip-PasskeyGlassSealMotion.nearestFace(flip)
        val lift=if(reduced)0.0 else abs(sin(PI*a))
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color(0x93537A)
        paint.setShadowLayer((22+18*lift).toFloat(),0f,(12+22*lift).toFloat(),color(0x2B1430,0.5-0.2*lift));c.drawPath(outline,paint);paint.clearShadowLayer()
        c.save()
        c.clipPath(outline)
        val front=if(reduced)1-flip else if(PasskeyGlassSealMotion.faceOf(flip)==0)1.0 else 0.0
        if(front>0){
            val save=c.saveLayerAlpha(0f,0f,l.cw,l.ch,(255*front).toInt())
            paint.shader=card;paint.alpha=255;c.drawRect(0f,0f,l.cw,l.ch,paint);paint.shader=null
            if(Build.VERSION.SDK_INT>=33&&runtime!=null&&!reduced){
                paint.shader=runtime.update(l,f,time);c.drawRect(0f,0f,l.cw,l.ch,paint);paint.shader=null
                particles(c,l,f)
            }else{
                c.save();c.translate(l.px,l.py);c.scale(l.s,l.s)
                paint.color=color(0xFFFFFF,0.62+0.38*f[0]);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.8f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
                c.drawPath(ridges,paint);c.restore();paint.style=Paint.Style.FILL
                val sy=l.py+60*sin(2*PI*time/2.5+asin(26.0/60))*l.s
                paint.color=color(0xFF8D5C,f[5]);c.drawRect(20f,sy.toFloat(),l.cw-20,sy.toFloat()+2,paint)
            }
            c.restoreToCount(save)
        }
        val back=if(reduced)flip else if(PasskeyGlassSealMotion.faceOf(flip)==1)1.0 else 0.0
        if(back>0){
            val save=c.saveLayerAlpha(0f,0f,l.cw,l.ch,(255*back).toInt())
            paint.style=Paint.Style.FILL;paint.color=color(0x93537A);c.drawRect(0f,0f,l.cw,l.ch,paint)
            val y=(l.ch-169)/2
            c.save();c.translate((l.cw-64)/2,y)
            paint.color=color(0xFFFFFF,0.08);c.drawCircle(32f,32f,30f,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;paint.color=color(0xFFFFFF,0.85);c.drawCircle(32f,32f,30f,paint)
            paint.strokeWidth=2.4f;paint.color=Color.WHITE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;c.drawPath(check,paint);c.restore()
            label(c,"Welcome back",15f,2.5f,l.cw/2,y+84,0.7)
            label(c,"Mira Okafor",26f,0f,l.cw/2,y+108)
            label(c,"Passkey saved on this iPhone",13f,0f,l.cw/2,y+153,0.72,bold=true)
            c.restoreToCount(save)
        }
        paint.shader=null;paint.style=Paint.Style.FILL
        paint.color=color(0x1E0E22,if(reduced)0.0 else 0.42*(1-cos(PI*a)))
        c.drawRect(0f,0f,l.cw,l.ch,paint)
        val gx=l.cw*(0.5-1.9*sin(PI*a+0.42))
        val glint=if(reduced)0.0 else 0.5*max(0.0,cos(PI*a+0.42)).pow(6)*min(1.0,6*abs(sin(PI*a)))
        paint.alpha=255
        paint.shader=LinearGradient((gx-0.45*l.cw).toFloat(),0f,(gx+0.45*l.cw).toFloat(),0.35f*l.ch,
            intArrayOf(color(0xFFECF4,0.0),color(0xFFECF4,0.9*glint),color(0xFFECF4,0.0)),floatArrayOf(0.3f,0.5f,0.7f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,l.cw,l.ch,paint);paint.shader=null
        c.restore()
    }
    fun drawOverlay(c:Canvas,l:Layout,f:DoubleArray){
        label(c,PasskeyGlassSealMotion.status(f),20f,0.5f,l.w/2,l.y(730f)-13,0.56)
        // Continue is overlaid as a real accessible Compose button.
        val r=(22*(1-0.12*f[7])).toFloat()
        paint.style=Paint.Style.FILL;paint.color=color(0xFFFFFF,0.1*f[6]);c.drawCircle(l.x+l.px,l.cy+l.py,r,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;paint.color=color(0xFFFFFF,0.55*f[6]);c.drawCircle(l.x+l.px,l.cy+l.py,r,paint)
    }
    private fun particles(c:Canvas,l:Layout,f:DoubleArray){
        paint.shader=null;paint.style=Paint.Style.FILL
        for(p in PasskeyGlassSealPrint.points){
            val stagger=(1-p[4])*0.45+p[6]*0.03
            val raw=((f[1]-stagger)/0.47).coerceIn(0.0,1.0);val e=raw*raw*(3-2*raw)
            val ax=p[0]+(p[2]-p[0])*e;val ay=p[1]+(p[3]-p[1])*e+2
            val angle=(1-e)*e*4*(0.9+0.5*p[4])
            val x=l.px+(cos(angle)*ax-sin(angle)*ay)*l.s
            val y=l.py+(sin(angle)*ax+cos(angle)*ay-2)*l.s
            val front=f[0]*1.25;val lit=((front-p[5])/0.18).coerceIn(0.0,1.0)
            val flash=if(f[0]>=0.001&&front<1.2)exp(-((front-p[5])/0.06).pow(2))else 0.0
            val mix=(1-e)*e*4
            val red=(1.0*(0.75+0.5*lit)+flash*1.6)*(1-mix)+1.3*mix
            val green=((1-0.14*lit)*(0.75+0.5*lit)+0.55*flash*1.6)*(1-mix)+1.04*mix
            val blue=((1-0.28*lit)*(0.75+0.5*lit)+0.32*flash*1.6)*(1-mix)+0.858*mix
            fun encode(v:Double):Int{val lin=min(v,1.6).pow(2.2);return (255*(lin/(1+max(lin-0.85,0.0)*0.6)).coerceIn(0.0,1.0).pow(1/2.2)).roundToInt()}
            val a=(0.62+0.38*lit)*(1-f[2])
            val r=(1.05+0.5*flash+0.25*mix)*l.s
            paint.color=Color.argb((255*a).roundToInt().coerceIn(0,255),encode(red),encode(green),encode(blue))
            c.drawCircle(x.toFloat(),y.toFloat(),(r*0.72).toFloat(),paint)
        }
    }
}

@RequiresApi(33)
private class PasskeyGlassSealRuntime {
    private val shader=RuntimeShader(PasskeyGlassSealShader)
    fun update(l:PasskeyGlassSealArt.Layout,f:DoubleArray,time:Double):RuntimeShader{
        val sy=l.py+60*sin(2*PI*time/2.5+asin(26.0/60))*l.s
        shader.setFloatUniform("uView",l.cw,l.ch,1f,time.toFloat())
        shader.setFloatUniform("uPrint",l.px,l.py,l.s,sy.toFloat())
        shader.setFloatUniform("uGlow",f[0].toFloat(),f[5].toFloat(),0f,0f)
        shader.setFloatUniform("uKey",f[1].toFloat(),f[2].toFloat(),PasskeyGlassSealMotion.turnAt(f[3]).toFloat(),0f)
        return shader
    }
}
