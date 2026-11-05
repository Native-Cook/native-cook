package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.GL
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingTheme as T
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingTheme.Layout

/** Native Canvas owns every visual layer; frame state is read only from the draw pass. */
internal class AuroraTeammatesOnboardingArt(context:Context) {
    private val fonts=arrayOf("Inter-Regular.ttf","Inter-SemiBold.ttf","InterDisplay-Bold.ttf").map{Typeface.createFromAsset(context.assets,T.ASSETS+it)}
    private val bitmaps=arrayOf("ato-icon-purple.png","ato-icon-slate.png","ato-logo-daybook.png","ato-logo-huddle.png","ato-logo-parley.png","ato-logo-inkwell.png","ato-logo-postbox.png","ato-logo-leadflow.png").map{context.assets.open(T.ASSETS+it).use { stream->requireNotNull(BitmapFactory.decodeStream(stream))}}
    private val icons=T.icons.map{requireNotNull(PathParser.createPathFromPathData(it))}
    private val sparkTop=requireNotNull(PathParser.createPathFromPathData(T.sparkTop))
    private val sparkBottom=requireNotNull(PathParser.createPathFromPathData(T.sparkBottom))
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply{isLinearText=true}
    private val camera=Camera()
    private val matrix=Matrix()
    private val runtime=if(Build.VERSION.SDK_INT>=33)AuroraTeammatesOnboardingRuntime()else null
    private var titleLayout:AuroraTeammatesOnboardingSheet?=null
    private var lastLayout:Layout?=null
    private val tmp=DoubleArray(4)
    private val dust=AuroraTeammatesOnboardingDust()
    private fun fill(hex:Int,a:Double=1.0):Paint=paint.apply{shader=null;maskFilter=null;style=Paint.Style.FILL;color=(hex and 0xffffff)or((M.clamp(a)*255).roundToInt()shl 24);strokeWidth=1f}
    private fun stroke(hex:Int,w:Double,a:Double=1.0)=fill(hex,a).apply{style=Paint.Style.STROKE;strokeWidth=w.toFloat();strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private fun rect(x:Double,y:Double,w:Double,h:Double)=RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat())
    private fun round(c:Canvas,x:Double,y:Double,w:Double,h:Double,r:Double,p:Paint){c.drawRoundRect(rect(x,y,w,h),r.toFloat(),r.toFloat(),p)}
    private fun label(c:Canvas,s:String,x:Double,y:Double,size:Double,font:Int=0,hex:Int=0xffffff,a:Double=1.0,center:Boolean=false,blur:Double=0.0){
        text.typeface=fonts[font];text.textSize=size.toFloat();text.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT;text.letterSpacing=0f;text.shader=null;text.color=(hex and 0xffffff)or((M.clamp(a)*255).roundToInt()shl 24)
        text.maskFilter=if(blur>.05)BlurMaskFilter(blur.toFloat(),BlurMaskFilter.Blur.NORMAL)else null;c.drawText(s,x.toFloat(),y.toFloat(),text);text.maskFilter=null
    }
    private fun objectTransform(c:Canvas,sc:DoubleArray,o:Int){
        c.translate(sc[o].toFloat(),sc[o+1].toFloat());camera.save();camera.setLocation(0f,0f,-8f);camera.rotateX((-sc[o+3]*180/PI).toFloat());camera.rotateY((sc[o+9]*180/PI).toFloat());camera.getMatrix(matrix);camera.restore();c.concat(matrix)
        c.rotate((sc[o+2]*180/PI).toFloat());c.scale(sc[o+4].toFloat(),sc[o+4].toFloat())
    }
    fun draw(c:Canvas,l:Layout,sc:DoubleArray,buttonPress:Double,density:Float,reduced:Boolean){
        c.save();c.scale(density,density)
        if(lastLayout!=l){lastLayout=l;text.typeface=fonts[2];text.textSize=l.titleSize.toFloat();text.letterSpacing=-.036f;titleLayout=AuroraTeammatesOnboardingSheet(l,text)}
        if(Build.VERSION.SDK_INT>=33&&runtime!=null){val p=fill(0xffffff);p.shader=runtime.background(l,sc);c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),p);p.shader=null}
        else {c.drawColor(Color.rgb(11,13,69));val p=fill(0xffffff);p.shader=RadialGradient(sc[GL.coreX].toFloat(),sc[GL.coreY].toFloat(),(l.w*.8).toFloat(),intArrayOf(0xffbde6e8.toInt(),0xff50547d.toInt(),0xff0b0d45.toInt()),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),p)}
        for(i in 0..12){val o=i*M.STRIDE;val alpha=sc[o+5];if(alpha<.001)continue
            c.save();objectTransform(c,sc,o)
            if(i<2){val side=(if(i==0)117.5 else 111.6)*l.s;c.drawBitmap(bitmaps[i],null,rect(-side/2,-side/2,side,side),fill(0xffffff,alpha))}
            else {
                val hw=max(0.0,sc[o+6]);val hh=max(0.0,sc[o+7]);val r=(if(i<7)7 else 9)*l.s;val box=rect(-hw,-hh,2*hw,2*hh)
                val panel=fill(0x45416e,alpha)
                if(Build.VERSION.SDK_INT>=33&&runtime!=null)panel.shader=runtime.panel(sc,o,r)
                c.drawRoundRect(box,r.toFloat(),r.toFloat(),panel)
                val overlay=fill(0xffffff,alpha);overlay.shader=LinearGradient(0f,hh.toFloat(),0f,-hh.toFloat(),if(i<7)intArrayOf(0x47beb0ff,0x2ba092ff)else intArrayOf(0x52c8d6ff,0x29aabeff),null,Shader.TileMode.CLAMP)
                c.drawRoundRect(box,r.toFloat(),r.toFloat(),overlay);c.drawRoundRect(box,r.toFloat(),r.toFloat(),stroke(0xffffff,.8,.20*alpha))
                val ca=alpha*sc[o+8];if(ca>.001){if(i<7)card(c,l,i-2,ca)else tile(c,l,i-7,ca)}
            };c.restore()
        }
        val flare=sc[GL.flare]
        if(flare>.001){val p=fill(0xffffff,.45*flare);p.shader=RadialGradient(sc[GL.coreX].toFloat(),sc[GL.coreY].toFloat(),130f,intArrayOf(0xf2ebf5ff.toInt(),0x59aabeff,0x00788cff),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP);c.drawCircle(sc[GL.coreX].toFloat(),sc[GL.coreY].toFloat(),(60+70*flare).toFloat(),p)}
        drawSheet(c,l,sc)
        drawWords(c,l,sc,reduced)
        drawDots(c,l,sc)
        drawButton(c,l,sc,buttonPress,reduced)
        if(!reduced)dust.draw(c,l,sc)
        c.restore()
    }
    private fun card(c:Canvas,l:Layout,k:Int,a:Double){
        val s=l.s;val ts=s/(402.0/280);val left=-218.75*s/2;val top=-43*s/2;val tile=30.5*s;val x=left+5.5*s
        round(c,x,-tile/2,tile,tile,7*s,fill(0xffffff,.12*a));round(c,x,-tile/2,tile,tile,7*s,stroke(0xffffff,.7,.16*a))
        val iconK=tile*.6/24;c.save();c.translate((x+tile/2-12*iconK).toFloat(),(-12*iconK).toFloat());c.scale(iconK.toFloat(),iconK.toFloat());c.drawPath(icons[k],stroke(0xffffff,1.7,a));c.drawPath(if(k<3)sparkTop else sparkBottom,fill(0xffffff,a));c.restore()
        label(c,T.agents[k],left+45.75*s,top+20*s,13.8*ts,1,a=a);label(c,T.agentSubs[k],left+45.75*s,top+35.75*s,9.8*ts,a=.62*a)
        val pw=37.5*s;val ph=15*s;val px=218.75*s/2-8.5*s-pw;round(c,px,-ph/2,pw,ph,ph/2,fill(0xffffff,.22*a));label(c,"Active",px+pw/2,3.8*ts,10.4*ts,1,a=.92*a,center=true)
    }
    private fun tile(c:Canvas,l:Layout,k:Int,a:Double){
        val s=l.s;val ts=s/(402.0/280);val left=-116.5*s/2;val top=-75*s/2
        c.drawBitmap(bitmaps[k+2],null,rect(left+8*s,top+8*s,22*s,22*s),fill(0xffffff,a))
        label(c,T.integrations[k],left+8*s,top+54*s,13.6*ts,1,a=a)
        label(c,if(k==0||k==4)"• Connected"else"Connect",left+8*s,top+66*s,10*ts,a=.56*a)
    }
    private fun drawSheet(c:Canvas,l:Layout,sc:DoubleArray){
        val top=sc[GL.sheetTop];val dip=sc[GL.dip];val cx=sc[GL.coreX];val path=AuroraTeammatesOnboardingSheet.path(top,dip,cx,l,true)
        val p=fill(0xffffff);p.shader=LinearGradient(0f,0f,0f,l.h.toFloat(),intArrayOf(0xff0e0c12.toInt(),0xff0e0c16.toInt(),0xff0f0d1d.toInt(),0xff0f0e22.toInt(),0xff0f0e28.toInt(),0xff100f2f.toInt(),0xff101037.toInt(),0xff10103e.toInt(),0xff111250.toInt(),0xff12114e.toInt()),doubleArrayOf(0.0,380.0,420.0,440.0,460.0,480.0,500.0,520.0,580.0,600.0).map{(it*l.s/l.h).toFloat()}.toFloatArray(),Shader.TileMode.CLAMP);c.drawPath(path,p)
        val vignette=fill(0xffffff);vignette.shader=LinearGradient(0f,0f,l.w.toFloat(),0f,intArrayOf(0x57060516,0x00060516,0x00060516,0x57060516),floatArrayOf(0f,.32f,.68f,1f),Shader.TileMode.CLAMP);c.drawPath(path,vignette)
        val rim=stroke(0xffffff,1.1,.35+.65*sc[GL.flare]);rim.shader=LinearGradient((cx-l.cx).toFloat(),0f,(cx-l.cx+l.w).toFloat(),0f,intArrayOf(0xe64e56be.toInt(),0xf2606ad6.toInt(),0xffcdd7ff.toInt(),0xf2606ad6.toInt(),0xe64e56be.toInt()),floatArrayOf(0f,.28f,.5f,.72f,1f),Shader.TileMode.CLAMP)
        c.drawPath(AuroraTeammatesOnboardingSheet.path(top,dip,cx,l,false),rim)
    }
    private fun drawWords(c:Canvas,l:Layout,sc:DoubleArray,reduced:Boolean){
        val layout=titleLayout?:return
        for(i in 0..7){layout.token(i,sc,reduced,tmp);val a=tmp[2];if(a<.001)continue
            val x=tmp[0];val y=tmp[1];text.typeface=fonts[2];text.textSize=l.titleSize.toFloat();text.letterSpacing=-.036f;text.textAlign=Paint.Align.LEFT;text.color=Color.WHITE;text.alpha=(a*255).roundToInt();text.maskFilter=if(tmp[3]>.05)BlurMaskFilter(tmp[3].toFloat(),BlurMaskFilter.Blur.NORMAL)else null
            text.shader=if(i>=6)LinearGradient(x.toFloat(),0f,(x+layout.widths[i]).toFloat(),0f,T.rainbow,T.rainbowStops,Shader.TileMode.CLAMP)else LinearGradient((l.cx-150*l.s/1.4357).toFloat(),(l.tops[0]+l.titleTop-l.titleCap).toFloat(),(l.cx+150*l.s/1.4357).toFloat(),(l.tops[0]+l.titleTop-l.titleCap+3*l.titleStep+40).toFloat(),intArrayOf(Color.WHITE,Color.WHITE,0xffa9a9af.toInt()),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
            c.drawText(T.tokens[i],x.toFloat(),y.toFloat(),text)
            if(i>=6&&!reduced&&sc[GL.u]>0){val q=M.clamp((sc[GL.u]-.56)/.4);val gx=x-.4*layout.widths[i]+1.8*layout.widths[i]*q;text.alpha=(.85*sin(PI*q)*a*255).roundToInt();text.shader=LinearGradient(gx.toFloat(),0f,(gx+.4*layout.widths[i]).toFloat(),0f,intArrayOf(0x00ffffff,0xe6ffffff.toInt(),0x00ffffff),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP);c.drawText(T.tokens[i],x.toFloat(),y.toFloat(),text)}
        }
        for(p in 0..2){val u=sc[GL.u];val base=sc[GL.sheetTop]+l.subBase[p];var y=base;var alpha=0.0;var blur=0.0
            if(p==sc[GL.from].toInt()){val q=if(u<=0)0.0 else if(reduced)M.smooth(0.0,.45,u)else M.smooth(.02,.3,u);y=base-(if(reduced)0.0 else 6*q);alpha=1-q;blur=if(reduced)0.0 else 5*q}
            else if(p==sc[GL.to].toInt()&&u>0){val q=if(reduced)M.smooth(.5,1.0,u)else M.clamp((u-.42)/.38);y=base+(if(reduced)0.0 else 8*(1-M.spring(q,.8,10.0)));alpha=if(reduced)q else M.smooth(0.0,.8,q);blur=if(reduced)0.0 else 5*(1-q)}
            if(alpha>.001)T.subtitles[p].forEachIndexed { j,s->label(c,s,l.cx,y+j*l.subStep,l.subSize,hex=0xbebdc4,a=alpha,center=true,blur=blur) }
        }
    }
    private fun drawDots(c:Canvas,l:Layout,sc:DoubleArray){
        val s=l.s;val trackY=sc[GL.dotsY]-7.5*s;val left=l.cx-l.trackW/2;round(c,left,trackY,l.trackW,15*s,7.5*s,fill(0x1f1e3e))
        val pos=sc[GL.dotPos];var x=6.75*s;var fillX=x
        for(i in 0..3){val w=max(0.0,1-abs(pos-i));val width=(4+9*w)*s;val height=(4+w)*s
            if(i==sc[GL.from].toInt())fillX=x
            val dim=sc[GL.dotDim];val pr=M.mix(174.0,94.0,dim);val pg=M.mix(175.0,93.0,dim);val pb=M.mix(204.0,132.0,dim)
            val col=Color.rgb(M.mix(58.0,pr,w).roundToInt().coerceIn(0,255),M.mix(57.0,pg,w).roundToInt().coerceIn(0,255),M.mix(89.0,pb,w).roundToInt().coerceIn(0,255))
            round(c,left+x,trackY+(15*s-height)/2,width,height,height/2,fill(col));x+=width+5.8*s
        }
        round(c,left+fillX,trackY+5*s,(5+8*sc[GL.dotFill])*s,5*s,2.5*s,fill(0xaeafcc,sc[GL.dotFillA]))
    }
    private fun drawButton(c:Canvas,l:Layout,sc:DoubleArray,press:Double,reduced:Boolean){
        val y=sc[GL.sinkY];val glow=min(1.0,sc[GL.finGlow]);c.save();c.translate(l.cx.toFloat(),y.toFloat())
        if(glow>.001){c.save();val k=.85+.35*glow;c.scale(((l.buttonW/l.buttonH)*.62*k).toFloat(),k.toFloat());val p=fill(0xffffff,glow);p.shader=RadialGradient(0f,0f,(l.buttonH*1.4).toFloat(),intArrayOf(0xffe2eaff.toInt(),0x80a0b0ff.toInt(),0x00786eff),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP);c.drawCircle(0f,0f,(l.buttonH*1.4).toFloat(),p);c.restore()}
        val k=1-.03*press+sc[GL.finPop];c.scale(k.toFloat(),k.toFloat());val path=AuroraTeammatesOnboardingSheet.pill(l.buttonW,l.buttonH,if(reduced)0.0 else abs(press)*1.1*l.s,sc[GL.phase]*7)
        val p=fill(0xffffff);p.shader=LinearGradient((-l.buttonW*.15).toFloat(),(l.buttonH/2).toFloat(),(l.buttonW/2).toFloat(),(-l.buttonH*.4).toFloat(),intArrayOf(Color.WHITE,Color.WHITE,0xffbdbdbf.toInt()),floatArrayOf(0f,.58f,1f),Shader.TileMode.CLAMP);c.drawPath(path,p)
        val fs=sc[GL.finSheen];val sheen=if(fs>0).9*sin(PI*fs)else .8*sc[GL.flare]
        if(sheen>.001){c.save();c.clipPath(path);c.translate((-l.buttonW*.7+l.buttonW*1.4*(if(fs>0)fs else sc[GL.u])).toFloat(),0f);c.rotate(18f);val sp=fill(0xffffff,sheen);sp.shader=LinearGradient((-l.buttonH*.45).toFloat(),0f,(l.buttonH*.45).toFloat(),0f,intArrayOf(0x00ffffff,0xf2ffffff.toInt(),0x00ffffff),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP);c.drawRect(rect(-l.buttonH*.45,-l.buttonH*1.2,l.buttonH*.9,l.buttonH*2.4),sp);c.restore()}
        val ck=sc[GL.finCheck];label(c,"Next →",0.0,l.label*.36-5*ck,l.label,1,0x0e0e18,1-ck,true)
        if(ck>.001){text.typeface=fonts[1];text.textSize=l.label.toFloat();text.letterSpacing=0f;val check=l.label*.95;val doneX=-(text.measureText("You're all set")+check+l.label*.45)/2;val dy=5*(1-ck)
            val tick=Path().apply{moveTo((doneX+check*.12).toFloat(),(-check*.02+dy).toFloat());lineTo((doneX+check*.4).toFloat(),(check*.28+dy).toFloat());lineTo((doneX+check*.92).toFloat(),(-check*.36+dy).toFloat())};c.drawPath(tick,stroke(0x0e0e18,l.label*.16,ck));label(c,"You're all set",doneX+check+l.label*.45,l.label*.36+dy,l.label,1,0x0e0e18,ck)
        };c.restore()
    }
}
