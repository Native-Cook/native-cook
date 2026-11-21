package com.ngocdevv.nativecook.blocks.inputs.elasticstringquiz

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class ElasticStringQuizRenderer(context:Context){
    private val m=ElasticStringQuizMotion
    val words=listOf("drift","muster","roam","moor","amass","stray","fix","collect")
    val answers=listOf("anchor","gather","wander")
    private val cords=intArrayOf(0x9C3A7E,0xA8396B,0xB83A5E);private val beads=intArrayOf(0xBC4388,0xC0437A,0xBC4269)
    private val root="elasticstringquiz/elasticstringquiz_"
    private val semi=Typeface.createFromAsset(context.assets,root+"Barlow_SemiBold.ttf")
    private val medium=Typeface.createFromAsset(context.assets,root+"Barlow_Medium.ttf")
    private val bold=Typeface.createFromAsset(context.assets,root+"Barlow_Bold.ttf")
    private val halo=context.assets.open(root+"halo.png").use{BitmapFactory.decodeStream(it)}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val field=if(Build.VERSION.SDK_INT>=33)Field() else null
    fun widths(value:String,size:Float,font:Typeface=semi):FloatArray{paint.typeface=font;paint.textSize=size;return FloatArray(value.length).also{paint.getTextWidths(value,it)}}
    fun script():ElasticStringQuizMotion.Script{
        val centers=mutableListOf<Double>();for(word in words){val ws=widths(word,52f);var x=201-ws.sum()/2;for(k in 0..<10){centers.add(if(k<ws.size)(x+ws[k]/2).toDouble() else 201.0);if(k<ws.size)x+=ws[k]}}
        return ElasticStringQuizMotion.Script(8.0,mutableListOf(2.0,1.0,2.0,0.0,1.0,2.0,0.0,1.0),words.map{it.length.toDouble()}.toMutableList(),centers,2.0,1.0,0.0,6412.0,0.0,44.0)
    }
    private fun color(a:Int,b:Int=a,k:Double=0.0,alpha:Double=1.0):Int{
        val r=(a shr 16 and 255)+((b shr 16 and 255)-(a shr 16 and 255))*k;val g=(a shr 8 and 255)+((b shr 8 and 255)-(a shr 8 and 255))*k;val blue=(a and 255)+((b and 255)-(a and 255))*k
        return Color.argb((alpha.coerceIn(0.0,1.0)*255).roundToInt(),r.roundToInt().coerceIn(0,255),g.roundToInt().coerceIn(0,255),blue.roundToInt().coerceIn(0,255))
    }
    private data class Cord(var a:Path=Path(),var b:Path=Path(),var pulses:List<Path> =List(3){Path()},var x:Double=0.0,var y:Double=0.0,var px:Double=0.0,var py:Double=0.0,var pulse:Double=0.0,var aColor:Int=0,var bColor:Int=0,var aBody:Int=0,var bBody:Int=0,var bloom:Double=0.0)
    private fun sub(p:ElasticStringQuizMotion.Pts,a:Double,b:Double):Path{val path=Path();if(b-a<.05)return path;val first=m.pointAt(p,a);val last=m.pointAt(p,b);path.moveTo(first.x.toFloat(),first.y.toFloat());for(k in p.x.indices)if(p.s[k]>a&&p.s[k]<b)path.lineTo(p.x[k].toFloat(),p.y[k].toFloat());path.lineTo(last.x.toFloat(),last.y.toFloat());return path}
    private fun geo(f:ElasticStringQuizMotion.Frame,i:Int):Cord{
        val base=cords[i];val line=f.cordA[i];val g=Cord(x=f.bx[i],y=f.by[i])
        if(f.ropeTau[i]>=0){val r=m.ropeAt(m.ROPE[i],f.ropeTau[i],f.e.snapS[i],f.e.snapB[i]);val p=Path();p.moveTo(r.x[0].toFloat(),r.y[0].toFloat());for(k in 1..<r.x.lastIndex)p.quadTo(r.x[k].toFloat(),r.y[k].toFloat(),((r.x[k]+r.x[k+1])/2).toFloat(),((r.y[k]+r.y[k+1])/2).toFloat());p.lineTo(r.x.last().toFloat(),r.y.last().toFloat());g.b=p;g.bColor=color(base,0xFFF8FC,.35*(1-m.seg(f.ropeTau[i],0.0,.3)),.8*line);g.bBody=color(base,alpha=.2*line);g.x=r.x.last();g.y=r.y.last();return g}
        val p=m.cordPoints(i.toDouble(),f.hx,f.hy,f.by[i],f.bend[i],f.e.pl,f.t,f.waveFrom);val length=p.total*f.draw[i];var split=0.0
        if(f.pulseFrac[i]>=0){split=p.total*(1-f.pulseFrac[i]);m.addBump(p,split,f.pulseAmp[i]*m.seg(split,0.0,30.0));val center=m.pointAt(p,split);g.px=center.x;g.py=center.y;g.pulse=f.pulseAmp[i]*line;g.pulses=listOf(sub(p,split-18,split+18),sub(p,split-12,split+12),sub(p,split-6,split+6))}
        val hot=f.heatHot[i];val heat=f.heat[i];g.bloom=.35*max(hot,heat)*line;g.a=if(split>0)sub(p,0.0,min(split,length))else Path();g.b=sub(p,min(split,length),length)
        g.aColor=color(base,0xFFF8FC,hot,(.8+.2*hot)*line);g.bColor=color(base,0xFFF8FC,heat,(.8+.2*heat)*line);g.aBody=color(base,0xFFF8FC,hot,.2*line);g.bBody=color(base,0xFFF8FC,heat,.2*line)
        if(f.tip==1.0){val end=m.pointAt(p,length);g.x=end.x;g.y=end.y};return g
    }
    private fun stroke(c:Canvas,p:Path,width:Double,color:Int,blur:Float=0f){paint.shader=null;paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=width.toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.maskFilter=if(blur>0)BlurMaskFilter(blur,BlurMaskFilter.Blur.NORMAL)else null;c.drawPath(p,paint);paint.maskFilter=null;paint.style=Paint.Style.FILL}
    private fun halo(c:Canvas,x:Double,y:Double,r:Double,alpha:Double){if(r<=0||alpha<=0)return;paint.shader=null;paint.color=Color.WHITE;paint.alpha=(alpha*255).roundToInt().coerceIn(0,255);paint.colorFilter=PorterDuffColorFilter(0xFFFD5082.toInt(),PorterDuff.Mode.SRC_IN);c.drawBitmap(halo,null,RectF((x-r).toFloat(),(y-r).toFloat(),(x+r).toFloat(),(y+r).toFloat()),paint);paint.colorFilter=null;paint.alpha=255}
    private fun text(c:Canvas,value:String,x:Double,baseline:Double,size:Float,tint:Int,font:Typeface=semi,center:Boolean=false,tracking:Double=0.0){
        val ws=widths(value,size,font);var at=x-(if(center)(ws.sum()+tracking*max(0,value.length-1))/2 else 0.0);paint.shader=null;paint.typeface=font;paint.textSize=size;paint.color=tint;paint.textAlign=Paint.Align.LEFT
        for(i in value.indices){c.drawText(value,i,i+1,at.toFloat(),baseline.toFloat(),paint);at+=ws[i]+tracking}
    }
    fun draw(c:Canvas,f:ElasticStringQuizMotion.Frame,top:Double,bottom:Double,hudY:Double,pixels:Double,matched:List<List<String>>){
        if(Build.VERSION.SDK_INT>=33&&field!=null)field.draw(c,f,top,bottom,pixels,paint)else{paint.shader=RadialGradient(f.fx.toFloat(),f.fy.toFloat(),520f,intArrayOf(color(0x752C48),color(0x371737),color(0x2B112C)),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,top.toFloat(),402f,bottom.toFloat(),paint);paint.shader=null}
        val gs=(0..2).map{geo(f,it)}
        for(i in 0..2){val g=gs[i];stroke(c,g.a,f.bodyW[i],g.aBody);stroke(c,g.b,f.bodyW[i],g.bBody)
            if(g.bloom>.001){paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD);stroke(c,g.a,14.0,withAlpha(g.aColor,g.bloom),4f);stroke(c,g.b,14.0,withAlpha(g.bColor,g.bloom),4f);paint.xfermode=null}
            stroke(c,g.a,f.width[i],g.aColor);stroke(c,g.b,f.width[i],g.bColor);halo(c,g.px,g.py,18.0,.6*g.pulse)
            for(j in 0..2)stroke(c,g.pulses[j],(j+2).toDouble(),color(0xFFFFFF,alpha=listOf(.3,.6,1.0)[j]*g.pulse))
        }
        for(i in 0..2){val g=gs[i];val h=f.halo[i];val sc=f.beadSc[i];val r=10*sc;val save=c.saveLayerAlpha(null,(f.beadA[i]*255).roundToInt().coerceIn(0,255))
            halo(c,g.x,g.y,m.mix(10.0,30.0,h)*sc,.55*h)
            if(r>0){val col=color(beads[i],0xFD5082,h);paint.color=Color.WHITE;paint.shader=RadialGradient(g.x.toFloat(),g.y.toFloat(),r.toFloat(),intArrayOf(col,col,color(beads[i],0xFD5082,h,0.0)),floatArrayOf(0f,.74f,1f),Shader.TileMode.CLAMP);c.drawCircle(g.x.toFloat(),g.y.toFloat(),r.toFloat(),paint);paint.shader=null}
            paint.color=Color.WHITE;c.drawCircle(g.x.toFloat(),g.y.toFloat(),(5*h*sc).toFloat(),paint);c.restoreToCount(save)
            val riding=f.mode==6.0;val dx=if(f.ropeTau[i]>=0||riding)g.x-ElasticStringQuizMotion.G.bx[i]else f.bend[i]*.85;val dy=if(riding)g.y-640+52*(f.labelSc[i]-1)else if(f.ropeTau[i]>=0)g.y-640 else f.labelDy[i]
            val label=c.saveLayerAlpha(null,(f.labelA[i]*255).roundToInt().coerceIn(0,255));c.translate((ElasticStringQuizMotion.G.bx[i]+dx).toFloat(),(692+dy).toFloat());c.scale(max(f.labelSc[i],.001).toFloat(),max(f.labelSc[i],.001).toFloat());c.translate(-ElasticStringQuizMotion.G.bx[i].toFloat(),-692f);text(c,answers[i],ElasticStringQuizMotion.G.bx[i],700.0,24f,color(0xFFF6FA),center=true,tracking=.4);c.restoreToCount(label)
        }
        val word=words[f.w.toInt()%words.size];val ws=widths(word,52f);var x=201-ws.sum()/2;val mask=c.save();if(f.roll==1.0)c.clipRect(0f,(top-10).toFloat(),402f,273f)
        for(k in word.indices){val cx=x+ws[k]/2;val letter=c.saveLayerAlpha(null,(f.la[k]*255).roundToInt().coerceIn(0,255));c.translate((cx+f.lx[k]).toFloat(),(239+f.ly[k]).toFloat());c.scale(max(f.ls[k],.01).toFloat(),max(f.ls[k],.01).toFloat());c.translate(-cx,-239f);text(c,word[k].toString(),x.toDouble(),257.0,52f,color(0xFFF6FA));c.restoreToCount(letter);x+=ws[k]};c.restoreToCount(mask)
        paint.shader=null;paint.color=color(0xFFFFFF,alpha=f.hubA);c.drawCircle(f.hx.toFloat(),f.hy.toFloat(),f.ballR.toFloat(),paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=color(0xFFFFFF,alpha=f.shockA*f.hubA);c.drawCircle(f.hx.toFloat(),f.hy.toFloat(),f.shockR.toFloat(),paint)
        val r=(f.ballR+8).toFloat();val oval=RectF(f.hx.toFloat()-r,f.hy.toFloat()-r,f.hx.toFloat()+r,f.hy.toFloat()+r);paint.color=color(0xFFFFFF,alpha=.7*f.arcA*f.hubA);c.drawArc(oval,(-150+f.arcRot*180/PI).toFloat(),(110*f.arcSweep).toFloat(),false,paint);c.drawArc(oval,(40-f.arcRot*180/PI).toFloat(),(70*f.arcSweep).toFloat(),false,paint);paint.style=Paint.Style.FILL
        hud(c,f,hudY);if(f.recapA>.001)recap(c,f.recapA,hudY,matched)
    }
    private fun withAlpha(col:Int,mult:Double)=(col and 0x00FFFFFF) or ((Color.alpha(col)*mult).roundToInt().coerceIn(0,255) shl 24)
    private fun hud(c:Canvas,f:ElasticStringQuizMotion.Frame,y:Double){
        val save=c.saveLayerAlpha(null,(f.scoreA*255).roundToInt());val tint=color(0xE8476F);paint.color=tint;paint.style=Paint.Style.STROKE;paint.strokeWidth=2.03f;c.drawRoundRect(16.875f,(y-6.125).toFloat(),29.125f,(y+6.125).toFloat(),2.625f,2.625f,paint);for(x in listOf(19.85f,23f,26.15f))c.drawLine(x,(y-2.45).toFloat(),x,(y+2.45).toFloat(),paint);paint.style=Paint.Style.FILL
        val slot=.544*15
        for(k in 0..3){val place=(3-k).toDouble();val p=10.0.pow(place);val from=floor(min(f.sF,9999.0)/p)%10;val to=floor(min(f.sTo,9999.0)/p)%10;val delta=(to-from+10)%10
            val q=if(f.sDur>0)m.easeOut(m.seg(f.t,f.sT0+.05*place,f.sT0+f.sDur))else if(f.t>=f.sT0)1.0 else 0.0;val pos=from+delta*q;val d0=floor(pos).toInt()%10;val frac=pos-floor(pos);val x=36+k*slot;val digit=c.save();c.clipRect((x-1).toFloat(),(y-10).toFloat(),(x+slot+1).toFloat(),(y+11).toFloat());text(c,d0.toString(),x+slot/2,y+5.3-frac*18,15f,tint,bold,true);if(frac>.001)text(c,((d0+1)%10).toString(),x+slot/2,y+5.3+(1-frac)*18,15f,tint,bold,true);c.restoreToCount(digit)};c.restoreToCount(save)
        val w=widths(":41",15f,bold).sum();text(c,":41",386-w.toDouble(),y+5.3,15f,color(0xE8476F,alpha=f.hudA),bold)
    }
    private fun recap(c:Canvas,alpha:Double,hudY:Double,matched:List<List<String>>){val save=c.saveLayerAlpha(null,(alpha*255).roundToInt());text(c,"WORDS MATCHED",201.0,207.0,20f,color(0xFFF6FA),bold,true,.8)
        for(i in 0..2){val x=listOf(99.0,201.0,302.0)[i];val y=listOf(304.0,492.0,304.0)[i];val ly=listOf(360.0,549.0,360.0)[i];paint.color=color(beads[i]);c.drawCircle(x.toFloat(),y.toFloat(),7f,paint);text(c,answers[i],x,ly+7,22f,color(0xFFF6FA),medium,true);for((j,w)in matched[i].withIndex())text(c,w,x,ly+49+j*21,13f,color(0x8E6F86),medium,true)}
        text(c,"TAP TO CONTINUE",201.0,767.0,14f,color(0x7A5670),center=true,tracking=.84);val w=widths("PAUSED",13f,bold).sum();text(c,"PAUSED",386-w.toDouble(),hudY+23,13f,color(0xE8476F,alpha=.8),bold);c.restoreToCount(save)
    }
    @RequiresApi(33) private class Field{private val shader=RuntimeShader(ElasticStringQuizShader.FIELD)
        fun draw(c:Canvas,f:ElasticStringQuizMotion.Frame,top:Double,bottom:Double,pixels:Double,p:Paint){shader.setFloatUniform("uP3",0f);shader.setFloatUniform("uC",f.fx.toFloat(),f.fy.toFloat());shader.setFloatUniform("uGain",f.gain.toFloat());shader.setFloatUniform("uCool",f.cool.toFloat());shader.setFloatUniform("uPx",pixels.toFloat());shader.setFloatUniform("uSpan",top.toFloat(),bottom.toFloat());p.color=Color.WHITE;p.shader=shader;c.drawRect(0f,top.toFloat(),402f,bottom.toFloat(),p);p.shader=null}
    }
}
