package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.PathParser
import kotlin.math.*

internal class FilamentBulbDimmerArt(private val fonts:Array<Typeface>) {
    private val h=FilamentBulbDimmerTheme
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply{isLinearText=true}
    private val runtime=if(Build.VERSION.SDK_INT>=33)FilamentBulbDimmerRuntime()else null
    private val back=PathParser.createPathFromPathData("M10 2 L2 10 L10 18")!!
    private val clock=PathParser.createPathFromPathData("M8 1.5 a6.5 6.5 0 1 0 .01 0 M8 4.5 V8 L10.5 9.5")!!
    private val bulb=PathParser.createPathFromPathData("M10 3 a6.5 6.5 0 0 0 -3.8 11.8 V17 h7.6 V14.8 A6.5 6.5 0 0 0 10 3 Z M7.2 20 h5.6")!!
    private val rays=PathParser.createPathFromPathData("M10 0 V1.5 M2 3 L3 4 M18 3 L17 4 M0 10 H1.5 M20 10 H18.5")!!
    private val path=Path();private var band=Path();private var lastLayout:FilamentBulbDimmerTheme.Layout?=null
    private val dust=FilamentBulbDimmerDust()
    private fun fill(hex:Int,alpha:Double=1.0):Paint=paint.apply{shader=null;maskFilter=null;style=Paint.Style.FILL;color=(hex and 0xffffff) or((alpha.coerceIn(0.0,1.0)*255).roundToInt() shl 24)}
    private fun stroke(hex:Int,w:Double,alpha:Double=1.0):Paint=fill(hex,alpha).apply{style=Paint.Style.STROKE;strokeWidth=w.toFloat();strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private fun text(c:Canvas,s:String,x:Double,y:Double,size:Float,height:Double,weight:Int=1,color:Int=0xFFFFFF,center:Boolean=false) {
        textPaint.typeface=fonts[weight];textPaint.textSize=size;textPaint.color=color or (255 shl 24);textPaint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        val fm=textPaint.fontMetrics;c.drawText(s,x.toFloat(),(y+(height-fm.ascent-fm.descent)/2).toFloat(),textPaint)
    }
    private fun circle(c:Canvas,x:Double,y:Double,r:Double,hex:Int,a:Double=1.0){c.drawCircle(x.toFloat(),y.toFloat(),r.toFloat(),fill(hex,a))}
    private fun icon(c:Canvas,x:Double,y:Double,on:Boolean) {
        c.save();c.translate((x-13).toFloat(),(y-17).toFloat());c.scale(26f/20,31f/24)
        c.drawPath(bulb,fill(if(on)0xFFF8D6 else 0xFFFFFF,if(on).92 else .32));c.drawPath(bulb,stroke(0xFFFFFF,1.2,if(on).95 else .4));if(on)c.drawPath(rays,stroke(0xFFFFFF,1.2,.9));c.restore()
    }
    fun draw(c:Canvas,l:FilamentBulbDimmerTheme.Layout,f:FilamentBulbDimmerMotion.Frame,density:Float,reduced:Boolean) {
        c.save();c.scale(density,density)
        if(Build.VERSION.SDK_INT>=33 && runtime!=null){paint.shader=runtime.scene(l,f,density);paint.alpha=255;paint.style=Paint.Style.FILL;c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint);paint.shader=null}else fallback(c,l,f)
        if(!reduced)dust.draw(c,l,f)
        c.drawRoundRect(l.tab,32f,32f,fill(0x282832));c.save();c.translate((32*l.sx).toFloat(),l.tab.centerY());c.rotate(-90f);text(c,"Energy Usage",0.0,-13.5,22f,27.0,color=0x3D3C46,center=true);c.restore()
        for((r,label)in arrayOf(l.chipL to "4 kwh",l.chipR to "18° C")){c.drawRoundRect(r,16f,16f,fill(0x2B2A32));text(c,label,r.centerX().toDouble(),r.centerY()-11.0,18f,22.0,color=0xECE8EE,center=true)}
        if(lastLayout!=l){lastLayout=l;val rm=(l.rOut+l.rIn)/2;band=Path().apply{moveTo((l.cx-rm).toFloat(),(l.cy+l.leg).toFloat());lineTo((l.cx-rm).toFloat(),l.cy.toFloat());arcTo(RectF((l.cx-rm).toFloat(),(l.cy-rm).toFloat(),(l.cx+rm).toFloat(),(l.cy+rm).toFloat()),180f,180f,false);lineTo((l.cx+rm).toFloat(),(l.cy+l.leg).toFloat())}}
        val sw=f.colour;val glow=FilamentBulbDimmerMotion.lum(f.heat)/FilamentBulbDimmerMotion.lum(.78);val bw=l.rOut-l.rIn
        val gradient=LinearGradient((l.cx-l.rOut).toFloat(),0f,(l.cx+l.rOut).toFloat(),0f,IntArray(3){h.accent(sw,it)},null,Shader.TileMode.CLAMP)
        val bp=stroke(0xFFFFFF,bw,.42*min(1.4,glow));bp.shader=gradient;bp.maskFilter=BlurMaskFilter(12f,BlurMaskFilter.Blur.OUTER);c.drawPath(band,bp)
        val bandPaint=stroke(0xFFFFFF,bw,.42+.58*min(1.0,glow));bandPaint.shader=gradient;c.drawPath(band,bandPaint)
        val (kx,ky)=l.arcPoint(f.level);val a=atan2(ky-l.cy,kx-l.cx);val dr=46*l.sx
        c.drawLine((l.cx+cos(a)*(l.rIn-6)).toFloat(),(l.cy+sin(a)*(l.rIn-6)).toFloat(),(l.cx+cos(a)*(dr+6)).toFloat(),(l.cy+sin(a)*(dr+6)).toFloat(),stroke(0xFFFFFF,1.2,.35))
        circle(c,kx,ky,17+3*f.knobPress,0xFFFFFF);circle(c,kx,ky,7.5-1.5*f.knobPress,h.accent(sw,4));val dy=l.cy-12*l.k
        circle(c,l.cx,dy,dr,0xFFFFFF);text(c,"Intensity",l.cx,dy-21,11f,14.0,0,0xA3A1A6,true);percent(c,l.cx,dy-7,f.shownPct)
        val rm=(l.rOut+l.rIn)/2;icon(c,l.cx-rm,l.cy+l.leg-6,false);icon(c,l.cx+rm,l.cy+l.leg-6,true)
        val hx=h.dotX(l.sx,f.trail);val y=l.dotsY-42;val hh=l.h-y
        path.reset();path.moveTo((hx-70).toFloat(),(y+hh).toFloat());path.cubicTo((hx-30).toFloat(),(y+hh).toFloat(),(hx-26).toFloat(),y.toFloat(),hx.toFloat(),y.toFloat());path.cubicTo((hx+26).toFloat(),y.toFloat(),(hx+30).toFloat(),(y+hh).toFloat(),(hx+70).toFloat(),(y+hh).toFloat());path.close();c.drawPath(path,fill(0x36363E));c.drawLine(hx.toFloat(),(y+56).toFloat(),hx.toFloat(),l.h.toFloat(),stroke(0xFFFFFF,1.0,.12))
        val lead=h.dotX(l.sx,f.lead);val trail=h.dotX(l.sx,f.trail);c.drawRoundRect(h.rect(min(lead,trail)-11.5,l.dotsY-11.5,abs(lead-trail)+23,23.0),11.5f,11.5f,stroke(h.accent(sw,5),1.2-.35*min(1.0,abs(f.lead-f.trail))))
        for(i in 0..4)circle(c,h.xs[i]*l.sx,l.dotsY,6.0,h.dots[i])
        c.save();c.translate((43*l.sx-6).toFloat(),(l.y(65.0)-10).toFloat());c.drawPath(back,stroke(0xE9E5EB,2.0));c.restore();text(c,"Lamp Details",l.w/2,l.y(65.0)-11,18f,22.0,color=0xE9E5EB,center=true)
        c.drawRoundRect(l.card,24f,24f,fill(0x3D383F));text(c,"Living Room Lamp",54*l.sx,l.y(160.0)-18,29f,36.0,2)
        val t=f.tog;val r=l.toggle;val th=r.height().toDouble();val kd=th-6;val stretch=4*t*(1-t)*12
        val accent=h.accent(sw,3);val off=0x4A444C;val color=(0..2).fold(255 shl 24){v,i->val shift=(2-i)*8;val aa=((off shr shift)and 255)/255.0;val bb=((accent shr shift)and 255)/255.0;v or ((255*(aa.pow(2.2)+(bb.pow(2.2)-aa.pow(2.2))*t.coerceIn(0.0,1.0)).pow(1/2.2)).roundToInt() shl shift)}
        c.drawRoundRect(r,(th/2).toFloat(),(th/2).toFloat(),fill(color));text(c,if(t>.5)"On" else "Off",if(t>.5)r.left+16.0 else r.right-36.0,r.centerY()-9.5,15f,19.0)
        val kr=h.rect(r.left+3+t*(r.width()-kd-6)-stretch/2,r.top+3.0,kd+stretch,kd);c.drawRoundRect(kr,(kd/2).toFloat(),(kd/2).toFloat(),fill(0xFFFFFF));c.save();c.translate(kr.centerX()-7,kr.centerY()-8);c.scale(14f/20,16f/24);c.drawPath(bulb,fill(if(glow>0)accent else 0x8B858D));c.restore()
        circle(c,168*l.sx+17,l.y(198.0)+17,17.0,0x4A444C);c.save();c.translate((168*l.sx+9).toFloat(),(l.y(198.0)+9).toFloat());c.drawPath(clock,stroke(0xD8D3DA,1.4));c.restore();text(c,"9:00 pm to 5:00 am",211*l.sx,l.y(198.0)+8,13f,18.0,color=0xECE8EE)
        if(Build.VERSION.SDK_INT>=33 && runtime!=null){paint.shader=runtime.light(l,f);paint.style=Paint.Style.FILL;paint.alpha=255;c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint);paint.shader=null}
        val (fx,fy)=l.finger(f);val fr=22*(1-.12*f.fingerPress);circle(c,fx,fy,fr,0xFFFFFF,.12*f.finger);c.drawCircle(fx.toFloat(),fy.toFloat(),fr.toFloat(),stroke(0xFFFFFF,1.5,.45*f.finger));c.restore()
    }
    private fun percent(c:Canvas,x:Double,y:Double,value:Double) {
        val places=doubleArrayOf(100.0,10.0,1.0);val widths=places.map{if(it==1.0)15.4 else 15.4*(value-(it-1)).coerceIn(0.0,1.0)};var left=x-(widths.sum()+22)/2
        for((i,p)in places.withIndex()){val v=max(0.0,value);val pos=if(p==1.0)v%10 else floor(v/p)%10+max(0.0,v%p-(p-1));c.save();c.clipRect(left.toFloat(),y.toFloat(),(left+widths[i]).toFloat(),(y+30).toFloat());for(n in 0..10)text(c,"${n%10}",left+widths[i]/2,y+(n-pos)*30,25f,30.0,3,0x141217,true);c.restore();left+=widths[i]};text(c,"%",left+11,y,25f,30.0,3,0x141217,true)
    }
    private fun fallback(c:Canvas,l:FilamentBulbDimmerTheme.Layout,f:FilamentBulbDimmerMotion.Frame) {
        c.drawColor(Color.rgb(38,38,48));val p=l.pose(f);val lc=h.light(f.heat,f.colour);val color=Color.rgb(255,(lc[1].pow(1/2.2)*255).toInt(),(lc[2].pow(1/2.2)*255).toInt());val lum=FilamentBulbDimmerMotion.lum(f.heat)
        paint.shader=RadialGradient(p[2].toFloat(),p[3].toFloat(),(l.r*5).toFloat(),intArrayOf((color and 0xffffff) or ((lum*45).toInt() shl 24),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);paint.style=Paint.Style.FILL;paint.alpha=255;c.drawPaint(paint)
        c.drawLine(l.anchorX.toFloat(),l.anchorY.toFloat(),p[0].toFloat(),p[1].toFloat(),stroke(0x99909A,1.2))
        c.save();c.translate(p[0].toFloat(),p[1].toFloat());c.rotate((-f.th*180/PI).toFloat());val r=l.r
        c.drawRoundRect((-.4*r).toFloat(),0f,(.4*r).toFloat(),(.5*r).toFloat(),3f,3f,fill(0x9C804F));c.drawRect((-.32*r).toFloat(),(.5*r).toFloat(),(.32*r).toFloat(),(1.12*r).toFloat(),fill(0xD4AD67))
        c.drawCircle(0f,(2.74*r).toFloat(),r.toFloat(),fill(color,.06+.12*lum));c.drawCircle(0f,(2.74*r).toFloat(),r.toFloat(),stroke(0xDDD3BC,1.0,.4))
        for(i in 0..3){val x=(-.4+i*.2)*r;c.drawLine(x.toFloat(),(2.95*r).toFloat(),(x+.1*r).toFloat(),(2.35*r).toFloat(),stroke(color,1.5,.2+.8*lum));c.drawLine((x+.1*r).toFloat(),(2.35*r).toFloat(),(x+.2*r).toFloat(),(2.95*r).toFloat(),stroke(color,1.5,.2+.8*lum))};c.restore()
    }
}

@RequiresApi(33)
internal class FilamentBulbDimmerRuntime {
    private val scene=RuntimeShader(FilamentBulbDimmerShader.SOURCE)
    private val light=RuntimeShader(FilamentBulbDimmerShader.LIGHT)
    private fun set(shader:RuntimeShader,name:String,vararg v:Double){shader.setFloatUniform(name,v.map{it.toFloat()}.toFloatArray())}
    fun scene(l:FilamentBulbDimmerTheme.Layout,f:FilamentBulbDimmerMotion.Frame,density:Float):Shader {
        val p=l.pose(f);val lc=FilamentBulbDimmerTheme.light(f.heat,f.colour);val ac=FilamentBulbDimmerTheme.accentLin(f.colour);val lum=FilamentBulbDimmerMotion.lum(f.heat)
        set(scene,"uview",l.w,l.h,density.toDouble(),f.time);set(scene,"uanchor",l.anchorX,l.anchorY,l.r,2.4*l.r);set(scene,"ubulb",p[0],p[1],f.th,f.tw);set(scene,"uglow",p[4],f.heat,lum,30*lum+1.4*f.heat*f.heat);set(scene,"ulight",*lc,f.on);set(scene,"uaccent",*ac,0.0);set(scene,"uarc",l.cx,l.cy,l.rOut,l.rIn);set(scene,"ucard",l.card.left.toDouble(),l.card.top.toDouble(),l.card.width().toDouble(),l.card.height().toDouble());set(scene,"uchips",l.chipL.left.toDouble(),l.chipR.left.toDouble(),l.chipL.top.toDouble(),l.chipL.width().toDouble());set(scene,"umisc",l.chipL.height().toDouble(),46*l.sx,l.leg,2.4);return scene
    }
    fun light(l:FilamentBulbDimmerTheme.Layout,f:FilamentBulbDimmerMotion.Frame):Shader {
        val p=l.pose(f);val lc=FilamentBulbDimmerTheme.light(f.heat,f.colour);val g=FilamentBulbDimmerMotion.lum(f.heat)*2.2
        set(light,"uL",p[2],p[3],2.4*l.r);set(light,"uCol",*lc.map{it.pow(1/2.2)*g}.toDoubleArray())
        for((name,r)in arrayOf("uCard" to l.card,"uChipL" to l.chipL,"uChipR" to l.chipR,"uTab" to l.tab))set(light,name,r.left.toDouble(),r.top.toDouble(),r.width().toDouble(),r.height().toDouble())
        set(light,"uRad",24.0,16.0,32.0,0.0);return light
    }
}
