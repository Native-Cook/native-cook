package com.ngocdevv.nativecook.blocks.text.texteffectkeys
import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class TextEffectKeysRenderer(context:Context){
    private val m=TextEffectKeysMotion
    val labels=listOf("Big","Small","Shake","Nod","Explode","Ripple","Bloom","Jitter")
    private val fonts=listOf("Regular","Medium","SemiBold","Bold","Italic").associateWith{Typeface.createFromAsset(context.assets,"texteffectkeys/texteffectkeys_Geist-$it.ttf")}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    data class Run(val paths:List<Path>,val xs:List<Double>,val ws:List<Double>,val width:Double,val size:Double)
    private val runs=mutableMapOf<String,Run>()
    private fun run(value:String,face:String="Regular",size:Double=15.0):Run=runs.getOrPut("$value/$face/$size"){
        paint.typeface=fonts.getValue(face);paint.textSize=size.toFloat();val widths=FloatArray(value.length);val total=paint.getTextRunAdvances(value.toCharArray(),0,value.length,0,value.length,false,widths,0)
        var pen=0.0;val xs=widths.map{val x=pen;pen+=it;x};val paths=value.indices.map{i->Path().also{paint.getTextPath(value,i,i+1,0f,0f,it)}};Run(paths,xs,widths.map{it.toDouble()},total.toDouble(),size)
    }
    fun geo():TextEffectKeysMotion.Geo{val a=run("Whaaaaat");val b=run("Whaaaaat","Medium");val r0=run("guess who got us front row");val r1=run("FRONT. ROW.");val old=run("no way","Medium")
        val g=m.makeGeo(a.ws.toMutableList(),b.ws.toMutableList(),mutableListOf(10.65,10.65,8.16,8.16,8.16,8.16,8.16,9.86),mutableListOf(mutableListOf(16.0,16+r0.width+24,300.0),mutableListOf(16.0,16+r1.width+24,340.0),mutableListOf(384-old.width-22,384.0,443.0)))
        g.fieldX=a.xs.indices.map{69+a.xs[it]+a.ws[it]/2}.toMutableList();g.bubbleX=b.xs.indices.map{g.bubbleL+11+b.xs[it]+b.ws[it]/2}.toMutableList();return g
    }
    private fun color(h:Int,alpha:Double=1.0)=(h and 0xFFFFFF) or ((alpha.coerceIn(0.0,1.0)*255).roundToInt() shl 24)
    private fun mix(a:Int,b:Int,k:Double):Int{val rgb=listOf(16,8,0).map{m.mix((a shr it and 255).toDouble(),(b shr it and 255).toDouble(),k).roundToInt().coerceIn(0,255)};return Color.rgb(rgb[0],rgb[1],rgb[2])}
    private fun fill(c:Canvas,p:Path,col:Int){paint.shader=null;paint.style=Paint.Style.FILL;paint.color=col;c.drawPath(p,paint)}
    private fun rect(x:Double,y:Double,w:Double,h:Double,r:Double)=Path().apply{addRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),Path.Direction.CW)}
    private fun stroke(c:Canvas,p:Path,col:Int,width:Double){paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=width.toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.color=col;c.drawPath(p,paint);paint.style=Paint.Style.FILL}
    private fun bubble(x:Double,y:Double,w:Double,h:Double,left:Boolean,tail:Double=5.0):Path{val r=max(0.0,min(16.0,min(w,h)/2)).toFloat();val t=tail.toFloat().coerceIn(0f,r);return Path().apply{addRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),floatArrayOf(r,r,r,r,if(left)r else t,if(left)r else t,if(left)t else r,if(left)t else r),Path.Direction.CW)}}
    private fun text(c:Canvas,r:Run,x:Double,baseline:Double,col:Int){for(i in r.paths.indices){val save=c.save();c.translate((x+r.xs[i]).toFloat(),baseline.toFloat());fill(c,r.paths[i],col);c.restoreToCount(save)}}
    fun draw(c:Canvas,f:TextEffectKeysMotion.Frame,g:TextEffectKeysMotion.Geo,oy:Double,navY:Double,conversation:Double,height:Double){
        c.drawColor(color(0x0F0D0B));val back=Path();back.moveTo(22.8f,(navY+9.2).toFloat());back.lineTo(16f,(navY+16).toFloat());back.lineTo(22.8f,(navY+22.8).toFloat());stroke(c,back,color(0xF4F1EA),2.0)
        paint.color=color(0x2B2723);c.drawCircle(201f,(navY+16).toFloat(),16f,paint);val mono=run("O","Medium",18.0);text(c,mono,201-mono.width/2,navY+23.5,color(0xF4F1EA));val name=run("Odile","SemiBold",13.0);text(c,name,201-name.width/2,navY+44.615,color(0xF4F1EA))
        val scene=c.save();c.translate(0f,oy.toFloat());composer(c,f);val thread=c.save();c.translate(0f,(505*(1-conversation)).toFloat());c.scale(1f,conversation.toFloat());drawThread(c,f,g);c.restoreToCount(thread)
        fill(c,rect(0.0,554.0,402.0,height+874,0.0),color(0x1A1714));fill(c,rect(0.0,554.0,402.0,1.0,0.0),color(0xFFFFFF,.06))
        for(k in 0..3){val r=run("Aa",listOf("Bold","Italic","Regular","Regular")[k]);val x=listOf(60.0,153.0,248.0,341.0)[k]-r.width/2;val base=584.325;text(c,r,x,base,color(0xF4F1EA));if(k==2)fill(c,rect(x,base+2.2,r.width,1.3,0.0),color(0xF4F1EA));if(k==3)fill(c,rect(x-1,base-4.6,r.width+2,1.3,0.0),color(0xF4F1EA))}
        keys(c,f)
        for(i in f.hero.indices){val h=f.hero[i];val r=run("Whaaaaat",if(h.font>.5)"Medium" else "Regular");val hg=g.h[i];val y=h.y-hg*h.s/2;val cy=if(y<505)505+(y-505)*conversation else y;val ys=m.mix(conversation,1.0,m.seg(y,475.0,505.0));val tint=mix(mix(0xF4F1EA,0x16190A,h.home),0xD8F34A,h.lime)
            val sigma=min(hypot(h.vx,h.vy)*.008,14.0)*m.seg(h.s,1.5,3.0)*h.mb+h.blur;val angle=atan2(h.vy,h.vx)
            if(sigma>.6){gaussian(c,r.paths[i],r.ws[i],hg,h,h.x,cy,ys,tint,sigma,angle,-sigma*1.1,.7);gaussian(c,r.paths[i],r.ws[i],hg,h,h.x,cy,ys,tint,sigma*.22,angle,0.0,1.0)}else glyph(c,r.paths[i],h.x,cy,h.rot,h.s,ys,-r.ws[i]/2,hg/2,tint,h.w)
        };c.restoreToCount(scene)
    }
    private fun glyph(c:Canvas,path:Path,x:Double,y:Double,rot:Double,scale:Double,ys:Double,pen:Double,base:Double,col:Int,weight:Double=0.0){val save=c.save();c.translate(x.toFloat(),y.toFloat());c.scale(1f,ys.toFloat());c.rotate(rot.toFloat());c.scale(scale.toFloat(),scale.toFloat());c.translate(pen.toFloat(),base.toFloat());fill(c,path,col);if(weight>.05)stroke(c,path,col,weight/max(scale,.001));c.restoreToCount(save)}
    private fun gaussian(c:Canvas,path:Path,width:Double,height:Double,h:TextEffectKeysMotion.Hero,x:Double,y:Double,ys:Double,col:Int,sigma:Double,angle:Double,offset:Double,alpha:Double){
        if(sigma<.4){glyph(c,path,x,y,h.rot,h.s,ys,-width/2,height/2,col,h.w);return};val layer=c.saveLayer(null,null);paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD);val total=(-12..12).sumOf{exp(-.5*(it/3.0).pow(2))}
        for(j in -12..12){val q=j/3.0;val a=alpha*exp(-.5*q*q)/total;val tint=(col and 0xFFFFFF)or((a*255).roundToInt().coerceIn(0,255) shl 24);val d=q*sigma+offset;glyph(c,path,x+cos(angle)*d,y+sin(angle)*d,h.rot,h.s,ys,-width/2,height/2,tint,h.w)};paint.xfermode=null;c.restoreToCount(layer)
    }
    private fun drawThread(c:Canvas,f:TextEffectKeysMotion.Frame,g:TextEffectKeysMotion.Geo){val rise=f.rise*44;val stamp=run("Today 21:47","Medium",13.0);text(c,stamp,201-stamp.width/2,424-rise+4.615,color(0x7E786F))
        for(j in 0..1){val r=run(listOf("guess who got us front row","FRONT. ROW.")[j]);val y=listOf(300.0,340.0)[j]-rise+f.dips[j];fill(c,bubble(16.0,y,r.width+24,32.0,true),color(0x1E1B18));text(c,r,28.0,y+21.325,color(0xF4F1EA))}
        val old=run("no way","Medium");val w=old.width+22;val y=443-rise+f.dips[2];fill(c,bubble(384-w,y,w,32.0,false),color(0xD8F34A));text(c,old,395-w,y+21.325,color(0x16190A));val seen=run("Seen","SemiBold",13.0);text(c,seen,384-seen.width,491.615,color(0x7E786F))
        if(f.body>.002){val k=f.body;val cx=m.mix(366.5,g.bubbleL+g.bubbleW/2,k);val cy=m.mix(522.5,459.0,k);val w=m.mix(25.0,g.bubbleW,k)*(1+f.squash)*(1+f.blip);val h=m.mix(25.0,32.0,k)*(1-f.squash)*(1+f.blip);val full=m.seg(f.shell,.4,1.0);fill(c,bubble(cx-w/2,cy-h/2,w,h,false,m.mix(12.5,5.0,m.seg(k,0.0,1.0))),color(0xD8F34A,m.mix(.1,1.0,full)));if(full<.99)stroke(c,bubble(cx-w/2+.75,cy-h/2+.75,w-1.5,h-1.5,false),color(0xD8F34A,.9*(1-full)),1.5)}
    }
    private fun composer(c:Canvas,f:TextEffectKeysMotion.Frame){paint.color=color(0x1E1B18);c.drawCircle(28.5f,521.5f,15.5f,paint);val plus=Path();plus.moveTo(28.5f,515f);plus.lineTo(28.5f,528f);plus.moveTo(22f,521.5f);plus.lineTo(35f,521.5f);stroke(c,plus,color(0x8C857B),1.5);fill(c,rect(56.0,505.0,328.0,34.0,17.0),color(0x0F0D0B));stroke(c,rect(56.5,505.5,327.0,33.0,17.0),color(0x2B2723),1.0)
        val word=run("Whaaaaat");val p=run("Message");if(f.select>.01){val save=c.save();c.clipRect(67f,0f,(67+(word.width+4)*f.select).toFloat(),2000f);fill(c,rect(67.0,m.FIELD_BASE-13.5,word.width+4,17.5,3.0),color(0xD8F34A,.22));c.restoreToCount(save)};if(f.placeholder>.01){val save=c.save();c.clipRect(67f,0f,(67+(p.width+4)*f.placeholder).toFloat(),2000f);text(c,p,69.0,m.FIELD_BASE,color(0x7E786F));c.restoreToCount(save)}
        val save=c.save();c.translate(366.5f,522.5f);c.scale(f.sendS.toFloat(),f.sendS.toFloat());paint.color=color(0xD8F34A);c.drawCircle(0f,0f,12.5f,paint);val arrow=Path();arrow.moveTo(0f,(6+f.arrowY).toFloat());arrow.lineTo(0f,(-5.5+f.arrowY).toFloat());arrow.moveTo(-4.6f,(-.9+f.arrowY).toFloat());arrow.lineTo(0f,(-5.5+f.arrowY).toFloat());arrow.lineTo(4.6f,(-.9+f.arrowY).toFloat());stroke(c,arrow,color(0x16190A),2.0);c.restoreToCount(save)
    }
    private fun keys(c:Canvas,f:TextEffectKeysMotion.Frame){for(k in 0..7){val x=listOf(15.0,206.0)[k%2];val base=listOf(606.0,665.0,724.0,783.0)[k/2];val d=min(f.depth[k],1.2);val y=base+4*d
        if(d>.01){paint.maskFilter=BlurMaskFilter(6f,BlurMaskFilter.Blur.NORMAL);fill(c,rect(x-2,y-1,185.0,49.0,16.0),color(0xD8F34A,.6*min(d,1.0)));paint.maskFilter=null};paint.maskFilter=BlurMaskFilter(6f,BlurMaskFilter.Blur.NORMAL);fill(c,rect(x,base+4,181.0,47.0,14.0),color(0x000000,.1));paint.maskFilter=null;fill(c,rect(x,base+4,181.0,43.0,14.0),color(0x14110F))
        paint.shader=LinearGradient(0f,y.toFloat(),0f,(y+43).toFloat(),intArrayOf(mix(0x34302B,0x2A2622,.45*d),color(0x2A2622)),null,Shader.TileMode.CLAMP);paint.color=Color.WHITE;c.drawPath(rect(x,y,181.0,43.0,14.0),paint);paint.shader=null
        val save=c.save();c.clipPath(rect(x,y,181.0,43.0,14.0));paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.shader=LinearGradient(0f,y.toFloat(),0f,(y+5).toFloat(),intArrayOf(color(0xFFFFFF,.14-.1*min(d,1.0)),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawPath(rect(x,y,181.0,43.0,14.0),paint);paint.strokeWidth=2f;paint.shader=LinearGradient(0f,(y+39).toFloat(),0f,(y+43).toFloat(),intArrayOf(Color.TRANSPARENT,color(0,.35)),null,Shader.TileMode.CLAMP);c.drawPath(rect(x,y,181.0,43.0,14.0),paint);paint.shader=null;paint.style=Paint.Style.FILL;c.restoreToCount(save)
        label(c,f,k,x+90.5,y+21.5)
    }}
    private fun label(c:Canvas,f:TextEffectKeysMotion.Frame,k:Int,cx:Double,cy:Double){val r=run(labels[k],"Medium",17.0);val n=r.paths.size;val ch=17*.71;val still=f.still==1.0;val poses=listOf(.5 to .4,.57 to .57,0.0 to 0.0,0.0 to 0.0,.215 to 1.0,.27 to 1.0,0.0 to 0.0,0.0 to 0.0);val p=if(still)poses[k].first else f.keyP;val amp=if(still)poses[k].second else f.amp*(1-f.active[k]);val tint=mix(0xF4F1EA,0xD8F34A,f.active[k])
        for(i in 0..<n){val a=m.effectPose(k.toDouble(),p,i.toDouble(),n.toDouble(),17/15.0,false);var dx=a.dx*amp;var dy=a.dy*amp;var rot=a.rot*amp;var sc=a.sc*amp;var w=a.w*amp;var blur=if(still)0.0 else a.blur*amp;var alpha=if(k==4&&!still)m.mix(1.0,m.explodeAlpha(p),1-f.active[k])else 1.0
            if(!still&&f.kick[k]>=0&&f.kick[k]<1){val pp=.05+.9*f.kick[k];val b=m.effectPose(k.toDouble(),pp,i.toDouble(),n.toDouble(),17/15.0,false);dx+=b.dx;dy+=b.dy;rot+=b.rot;sc+=b.sc;w+=b.w;blur=max(blur,b.blur);if(k==4)alpha=min(alpha,m.explodeAlpha(pp))};if(alpha<.01)continue
            val gx=cx-r.width/2+r.xs[i]+r.ws[i]/2;val px=cx+(gx-cx)*(1+sc)+dx;paint.maskFilter=if(blur>.3)BlurMaskFilter(blur.toFloat(),BlurMaskFilter.Blur.NORMAL)else null;glyph(c,r.paths[i],px,cy+dy,rot,1+sc,1.0,-r.ws[i]/2,ch/2,withAlpha(tint,alpha),w);paint.maskFilter=null
        }
    }
    private fun withAlpha(col:Int,a:Double)=(col and 0xFFFFFF)or((a.coerceIn(0.0,1.0)*255).roundToInt() shl 24)
}
