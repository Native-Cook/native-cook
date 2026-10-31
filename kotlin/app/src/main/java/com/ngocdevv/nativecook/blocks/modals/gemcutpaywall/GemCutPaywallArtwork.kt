package com.ngocdevv.nativecook.blocks.modals.gemcutpaywall

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

@RequiresApi(33)
internal class GemCutPaywallTracer {
    private val shader=RuntimeShader(GemCutPaywallShader.SOURCE.replace("uniform float uPixelRatio;","uniform float uPixelRatio;\nuniform float uDensity;").replace("half4 main(float2 xy) {","half4 main(float2 xy) {\n  xy /= uDensity;"))
    private val paint=Paint()
    fun draw(c: Canvas,l: GemCutPaywallTheme.Layout,f: GemCutPaywallMotion.Frame,density: Float) {
        shader.setFloatUniform("uC",l.gemX.toFloat(),l.gemY.toFloat())
        shader.setFloatUniform("uS",l.gemR.toFloat());shader.setFloatUniform("uCut",f.cut.toFloat())
        shader.setFloatUniform("uYaw",f.yaw.toFloat());shader.setFloatUniform("uPitch",f.pitch.toFloat());shader.setFloatUniform("uSpark",f.spark.toFloat())
        shader.setFloatUniform("uFloor",l.gemX.toFloat(),l.floorY.toFloat());shader.setFloatUniform("uPixelRatio",density);shader.setFloatUniform("uDensity",density)
        paint.shader=shader;c.drawRect(0f,0f,(l.w*density).toFloat(),(l.h*density).toFloat(),paint)
    }
}

internal class GemCutPaywallArtwork(context: Context) {
    private val h=GemCutPaywallTheme
    private val m=GemCutPaywallMotion
    private val faces=listOf("Regular","Medium","SemiBold").associateWith { Typeface.createFromAsset(context.assets,"gemcutpaywall/gemcutpaywall_Inter-$it.ttf") }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val tracer=if(Build.VERSION.SDK_INT>=33)GemCutPaywallTracer() else null
    private fun pen(color: Int,alpha: Double=1.0,stroke: Double=0.0): Paint {
        paint.reset();paint.isAntiAlias=true;paint.color=color;paint.alpha=(alpha.coerceIn(0.0,1.0)*255).roundToInt()
        paint.style=if(stroke>0)Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=stroke.toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        return paint
    }
    private fun textPaint(size: Double,weight: String,color: Int,alpha: Double=1.0): Paint {
        pen(color,alpha);paint.typeface=faces.getValue(weight);paint.textSize=size.toFloat();paint.fontFeatureSettings="tnum";return paint
    }
    private fun text(c: Canvas,s: String,x: Double,y: Double,size: Double=15.0,weight: String="Regular",color: Int=h.INK,alpha: Double=1.0,align: Paint.Align=Paint.Align.CENTER) {
        val p=textPaint(size,weight,color,alpha)
        val baseline=(y-(p.ascent()+p.descent())/2).toFloat()
        if(size==28.0) {
            // Draw contextual glyph runs so the title retains the RN -0.4 pt tracking.
            val tracking= -0.4f
            val total=p.measureText(s)+tracking*(s.length-1)
            val left=x.toFloat()-when(align){Paint.Align.CENTER->total/2;Paint.Align.RIGHT->total;else->0f}
            for(i in s.indices) {
                val advance=p.getRunAdvance(s,0,s.length,0,s.length,false,i)
                c.drawTextRun(s,i,i+1,0,s.length,left+advance+i*tracking,baseline,false,p)
            }
        } else {p.textAlign=align;c.drawText(s,x.toFloat(),baseline,p)}
    }
    private fun width(s: String,size: Double,weight: String)=textPaint(size,weight,h.INK).measureText(s).toDouble()+(if(size==28.0)-0.4*(s.length-1) else 0.0)
    private fun rounded(c: Canvas,x: Double,y: Double,w: Double,height: Double,r: Double,color: Int,stroke: Double=0.0) {c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+height).toFloat(),r.toFloat(),r.toFloat(),pen(color,stroke=stroke))}
    private fun blend(a: Int,b: Int,k: Double): Int {
        fun channel(shift: Int): Int {val x=((a ushr shift)and 255)/255.0;val y=((b ushr shift)and 255)/255.0;return ((x.pow(2.2)*(1-k)+y.pow(2.2)*k).pow(1/2.2)*255).roundToInt().coerceIn(0,255)}
        return Color.rgb(channel(16),channel(8),channel(0))
    }
    private inline fun control(c: Canvas,d: GemCutPaywallDriver,t: Double,i: Int,cx: Double,cy: Double,block: ()->Unit) {
        val save=c.save();val down=d.pressAmount(i,t)
        if(d.reduced) c.saveLayerAlpha(null,((1-0.28*down)*255).roundToInt())
        else {val scale=(1-0.04*down).toFloat();c.translate(cx.toFloat(),cy.toFloat());c.scale(scale,scale);c.translate(-cx.toFloat(),-cy.toFloat())}
        block();c.restoreToCount(save)
    }
    fun draw(c: Canvas,l: GemCutPaywallTheme.Layout,f: GemCutPaywallMotion.Frame,d: GemCutPaywallDriver,t: Double,density: Float) {
        c.drawColor(h.PAGE)
        if(Build.VERSION.SDK_INT>=33)tracer?.draw(c,l,f,density)
        val root=c.save();c.scale(density,density)
        if(Build.VERSION.SDK_INT<33)fallback(c,l,f)
        val gl=m.glints(f.yaw,f.pitch,f.cut,f.spark)
        for(i in 0..8) {
            val alpha=gl[i*3+2];if(alpha<0.001)continue
            val save=c.save();c.translate((l.gemX+gl[i*3]*l.gemR).toFloat(),(l.gemY+gl[i*3+1]*l.gemR).toFloat());c.scale((4+22*alpha).toFloat(),(4+22*alpha).toFloat());c.rotate((0.2*180/PI).toFloat())
            val star=Path().apply {moveTo(0f,-1f);quadTo(0.09f,-0.09f,1f,0f);quadTo(0.09f,0.09f,0f,1f);quadTo(-0.09f,0.09f,-1f,0f);quadTo(-0.09f,-0.09f,0f,-1f);close()}
            val p=pen(Color.WHITE,alpha);p.xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD);p.maskFilter=BlurMaskFilter(0.04f,BlurMaskFilter.Blur.NORMAL);c.drawPath(star,p)
            pen(Color.rgb(220,215,255),alpha*0.9);paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD);paint.maskFilter=BlurMaskFilter(0.12f,BlurMaskFilter.Blur.NORMAL);c.drawCircle(0f,0f,0.16f,paint);c.restoreToCount(save)
        }
        control(c,d,t,0,30.0,l.barTop+22) {
            val cross=Path().apply {moveTo(25f,(l.barTop+17).toFloat());lineTo(35f,(l.barTop+27).toFloat());moveTo(35f,(l.barTop+17).toFloat());lineTo(25f,(l.barTop+27).toFloat())}
            c.drawPath(cross,pen(h.INK,stroke=2.0))
        }
        control(c,d,t,1,l.w-54,l.barTop+22) {text(c,"Restore",l.w-20,l.barTop+22,15.0,"Medium",h.MUTE,align=Paint.Align.RIGHT)}
        val prefixWidth=width("Facet ",28.0,"SemiBold");val freeWidth=width("Free",28.0,"SemiBold");val titleX=(l.w-prefixWidth-freeWidth)/2
        text(c,"Facet ",titleX,l.titleTop+17,28.0,"SemiBold",align=Paint.Align.LEFT)
        for(i in 0..2) {val alpha=d.opacity(i,t);text(c,h.names[i],titleX+prefixWidth,l.titleTop+17,28.0,"SemiBold",alpha=alpha,align=Paint.Align.LEFT)}
        val dw=width("0",17.0,"Medium");val dot=width(".",17.0,"Medium");val dollar=width("$",17.0,"Medium");val per=width(" / month",17.0,"Medium")
        var dx=(l.w-(dollar+3*dw+dot+per))/2
        text(c,"$",dx,l.priceTop+11,17.0,"Medium",align=Paint.Align.LEFT);dx+=dollar
        val digits=d.digits(t)
        for(i in 0..3) {
            if(i==1){text(c,".",dx,l.priceTop+11,17.0,"Medium",align=Paint.Align.LEFT);dx+=dot;continue}
            val save=c.save();c.clipRect(dx.toFloat(),l.priceTop.toFloat(),(dx+dw).toFloat(),(l.priceTop+22).toFloat())
            for(n in 0..9)text(c,n.toString(),dx,l.priceTop+11+(n-digits[i])*22,17.0,"Medium",align=Paint.Align.LEFT)
            c.restoreToCount(save);dx+=dw
        }
        text(c," / month",dx,l.priceTop+11,17.0,"Medium",h.MUTE,align=Paint.Align.LEFT)
        rounded(c,16.0,l.planTop,l.planW,48.0,24.0,h.SURFACE);rounded(c,16.0,l.planTop,l.planW,48.0,24.0,h.LINE,1.0/density)
        rounded(c,20+f.cut*l.thumbRun,l.planTop+4,l.segW,40.0,20.0,h.THUMB)
        for(i in 0..2)text(c,h.names[i],20+l.segW*(i+0.5),l.planTop+24,15.0,"SemiBold",blend(h.MUTE,h.THUMB_INK,max(0.0,1-abs(f.cut*2-i))))
        for(i in 0..2) {
            val from=if(i==2)2.0 else 1.0;val lit=m.clamp01((f.cut*2-(from-0.6))/0.6);val y=l.featTop+i*36
            val save=c.save();c.translate(32f,(y+2).toFloat())
            val check=Path().apply {moveTo(4.5f,10.5f);lineTo(8.5f,14.5f);lineTo(15.5f,6f)};c.drawPath(check,pen(h.INK,lit,2.0))
            val lock=Path().apply {moveTo(6.5f,9f);lineTo(6.5f,7f);arcTo(6.5f,3.5f,13.5f,10.5f,180f,180f,false);lineTo(13.5f,9f)};c.drawPath(lock,pen(h.MUTE,1-lit,1.6));c.drawRoundRect(4f,9f,16f,17f,1f,1f,pen(h.MUTE,1-lit))
            c.restoreToCount(save);text(c,h.features[i],64.0,y+12,15.0,color=blend(h.MUTE,h.INK,lit),align=Paint.Align.LEFT)
        }
        for(i in 0..2){val alpha=d.opacity(i,t);text(c,h.notes[i],l.w/2,l.noteTop+9,13.0,color=h.MUTE,alpha=alpha)}
        control(c,d,t,2,l.w/2,l.ctaTop+28) {
            rounded(c,16.0,l.ctaTop,l.w-32,56.0,28.0,Color.WHITE)
            val free=d.freeOpacity(t)
            text(c,"Continue with Free",l.w/2,l.ctaTop+28,17.0,"SemiBold",h.CTA_INK,free)
            text(c,"Start 7-day free trial",l.w/2,l.ctaTop+28,17.0,"SemiBold",h.CTA_INK,1-free)
        }
        val fingerCut=if(d.manual)d.frozenFingerCut else f.cut
        var fx=20+l.segW/2+fingerCut*l.thumbRun;var fy=l.planTop+24
        if(f.fingerOn==1){fx=l.gemX+m.flickOffset(d.loopTime)*l.gemR;fy=l.gemY+l.gemR*0.05} else if(f.fingerOn>=2)fx=20+l.segW*(if(f.fingerOn==2)1.5 else 0.5)
        val radius=22*(1-0.12*f.press)
        c.drawCircle(fx.toFloat(),fy.toFloat(),radius.toFloat(),pen(Color.WHITE,0.16*f.finger));c.drawCircle(fx.toFloat(),fy.toFloat(),radius.toFloat(),pen(Color.WHITE,0.55*f.finger,1.5))
        c.restoreToCount(root)
    }
    private fun fallback(c: Canvas,l: GemCutPaywallTheme.Layout,f: GemCutPaywallMotion.Frame) {
        val x=l.gemX.toFloat();val y=l.gemY.toFloat();val r=l.gemR.toFloat()
        if(f.cut<0.2) c.drawOval(x-r*1.18f,y-r*0.65f,x+r*1.18f,y+r*0.8f,pen(Color.rgb(171,160,216)))
        else {
            val gem=Path().apply {moveTo(x-r,y-r*0.05f);lineTo(x-r*0.57f,y-r*0.5f);lineTo(x+r*0.57f,y-r*0.5f);lineTo(x+r,y-r*0.05f);lineTo(x,y+r*0.86f);close()}
            c.drawPath(gem,pen(Color.rgb(196,186,239)))
            val facets=Path().apply {moveTo(x-r,y-r*0.05f);lineTo(x+r,y-r*0.05f);moveTo(x-r*0.57f,y-r*0.5f);lineTo(x,y+r*0.86f);lineTo(x+r*0.57f,y-r*0.5f)}
            c.drawPath(facets,pen(Color.rgb(104,86,160),stroke=1.5))
        }
    }
}
