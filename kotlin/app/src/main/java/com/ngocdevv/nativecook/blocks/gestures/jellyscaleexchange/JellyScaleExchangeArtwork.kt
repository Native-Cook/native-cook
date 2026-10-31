package com.ngocdevv.nativecook.blocks.gestures.jellyscaleexchange

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class JellyScaleExchangeArtwork(private val fonts: JellyScaleExchangeTheme) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader=if(Build.VERSION.SDK_INT>=33) JellyScaleExchangeStage() else null
    private fun round(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int) {
        paint.shader=null; paint.style=Paint.Style.FILL; paint.color=color
        c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)
    }
    private fun label(c: Canvas,s: String,x: Double,y: Double,size: Double,font: Typeface,color: Int,center: Boolean=false,spacing: Double=0.0,tabular: Boolean=false) {
        paint.shader=null; paint.style=Paint.Style.FILL; paint.typeface=font; paint.textSize=size.toFloat(); paint.color=color; paint.fontFeatureSettings=if(tabular) "tnum" else null
        val widths=FloatArray(s.length); paint.getTextWidths(s,widths)
        var px=if(center) x-(widths.sum()+spacing*max(0,s.length-1))/2 else x
        val baseline=y-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2
        for(i in s.indices) { c.drawText(s[i].toString(),px.toFloat(),baseline.toFloat(),paint); px+=widths[i]+spacing }
    }
    private fun star(c: Canvas,x: Double,y: Double,r: Double,color: Int) {
        val path=Path()
        for(i in 0..9) {
            val a=-PI/2+i*PI/5; val rr=if(i%2==0) r else r*0.46
            val px=(x+cos(a)*rr).toFloat(); val py=(y+sin(a)*rr).toFloat()
            if(i==0) path.moveTo(px,py) else path.lineTo(px,py)
        }
        path.close(); paint.color=color; paint.style=Paint.Style.FILL; c.drawPath(path,paint)
    }
    fun draw(c: Canvas,l: JellyScaleExchangeMotion.Layout,f: JellyScaleExchangeMotion.Frame,density: Float,reduced: Boolean) {
        c.drawColor(JellyScaleExchangeTheme.PAGE)
        c.save(); c.scale(density,density)
        label(c,"Jelly shop",20.0,l.top+26,26.0,fonts.semibold,JellyScaleExchangeTheme.INK)
        label(c,"Swap jellies for points",20.0,l.top+54,14.0,fonts.regular,JellyScaleExchangeTheme.MUTE)
        val wobble=JellyScaleExchangeMotion.wobbleAt(f.wobbleU)
        c.save(); c.scale((1+0.12*wobble).toFloat(),(1-0.1*wobble).toFloat(),l.pillX.toFloat(),l.pillY.toFloat())
        round(c,l.pillX-60,l.pillY-20,120.0,40.0,20.0,JellyScaleExchangeTheme.LINE)
        round(c,l.pillX-59,l.pillY-19,118.0,38.0,19.0,JellyScaleExchangeTheme.CARD)
        // Icon and text are centered as one row, with the reference's eight-point gap.
        paint.typeface=fonts.semibold; paint.textSize=15f
        val rowW=22+8+paint.measureText("Points"); val iconX=l.pillX-rowW/2+11
        paint.color=JellyScaleExchangeTheme.ACCENT; c.drawCircle(iconX.toFloat(),l.pillY.toFloat(),10f,paint)
        val iconStar=Path().apply {
            moveTo(iconX.toFloat(),(l.pillY-5.5).toFloat())
            for((sx,sy) in listOf(1.6 to -2.1,5.3 to -1.7,2.5 to 0.8,3.3 to 4.5,0.0 to 2.6,-3.3 to 4.5,-2.5 to 0.8,-5.3 to -1.7,-1.6 to -2.1))
                lineTo((iconX+sx).toFloat(),(l.pillY+sy).toFloat())
            close()
        }
        paint.color=0xFFB97A00.toInt(); c.drawPath(iconStar,paint)
        label(c,"Points",iconX+19,l.pillY,15.0,fonts.semibold,JellyScaleExchangeTheme.INK)
        c.restore()
        c.saveLayerAlpha(null,(255*sin(PI*f.plus)).toInt().coerceIn(0,255))
        paint.typeface=fonts.semibold; paint.textSize=16f
        label(c,"+80",l.pillX-110+44-paint.measureText("+80"),l.pillY+14-26*f.plus,16.0,fonts.semibold,JellyScaleExchangeTheme.ACCENT)
        c.restore(); c.restore()
        if(Build.VERSION.SDK_INT>=33&&shader!=null) shader.draw(c,l,f,density,reduced)
        else { c.save(); c.scale(density,density); fallback(c,l,f,reduced); c.restore() }
        c.save(); c.scale(density,density)
        val count=if(f.coin>0.6) 0 else floor(f.pour+0.0001).toInt()
        label(c,count.toString().padStart(2,'0'),l.w/2,l.lcdY,l.lcdH*0.62,fonts.semibold,JellyScaleExchangeTheme.LCD,true,2.0,true)
        val bw=(l.w-52)/2
        fun button(x: Double,press: Double,swap: Boolean) {
            c.save(); c.scale((1-0.04*press).toFloat(),(1-0.04*press).toFloat(),(x+bw/2).toFloat(),(l.buttonsTop+28).toFloat())
            if(swap) c.saveLayerAlpha(null,if(f.pour>0.5||f.melt>0) 255 else 115)
            round(c,x,l.buttonsTop,bw,56.0,28.0,if(swap) JellyScaleExchangeTheme.ACCENT else JellyScaleExchangeTheme.LINE)
            if(!swap) round(c,x+1,l.buttonsTop+1,bw-2,54.0,27.0,JellyScaleExchangeTheme.CARD)
            label(c,if(swap) "Exchange" else "Hold to pour",x+bw/2,l.buttonsTop+28,16.0,fonts.semibold,if(swap) JellyScaleExchangeTheme.ACCENT_INK else JellyScaleExchangeTheme.INK,true)
            if(swap) c.restore(); c.restore()
        }
        button(20.0,f.pressPour,false); button(l.w-20-bw,f.pressSwap,true)
        if(f.finger>0) {
            val x=if(f.fingerAt==0.0) 20+(l.w-52)/4 else l.w-20-(l.w-52)/4
            paint.color=android.graphics.Color.argb((255*0.35*0.85*f.finger).toInt(),255,255,255); paint.style=Paint.Style.FILL
            c.drawCircle(x.toFloat(),(l.buttonsTop+28).toFloat(),22f,paint)
            paint.color=android.graphics.Color.argb((255*0.8*0.85*f.finger).toInt(),255,255,255); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
            c.drawCircle(x.toFloat(),(l.buttonsTop+28).toFloat(),21.25f,paint); paint.style=Paint.Style.FILL
        }
        c.restore()
    }
    private fun fallback(c: Canvas,l: JellyScaleExchangeMotion.Layout,f: JellyScaleExchangeMotion.Frame,reduced: Boolean) {
        val p=l.ppu; val cx=l.w/2; val cy=l.stageY
        round(c,cx-p*1.02,cy+p*0.3,p*2.04,p*0.65,p*0.24,0xFF7385FF.toInt())
        round(c,cx-p*0.5,l.lcdY-p*0.15,p,p*0.3,p*0.06,0xFF0F1429.toInt())
        paint.color=0xFFDCE0F2.toInt(); c.drawOval((cx-p*0.68).toFloat(),(cy-p*0.05).toFloat(),(cx+p*0.68).toFloat(),(cy+p*0.4).toFloat(),paint)
        paint.color=0xFFF7F7FC.toInt(); c.drawOval((cx-p*0.72).toFloat(),(cy-p*0.24).toFloat(),(cx+p*0.72).toFloat(),(cy+p*0.18).toFloat(),paint)
        val colours=intArrayOf(0xFFFF739E.toInt(),0xFFB38CFF.toInt(),0xFF66E0B3.toInt(),0xFFFFDB59.toInt(),0xFF73B8FF.toInt())
        if(f.melt<0.999) for(i in 0..15) {
            val u=(f.pour-i).coerceIn(0.0,1.0); if(u<=0) continue
            val a=i*2.39996; val rr=0.5*sqrt((i+0.5)/16)
            val fall=if(reduced) 0.0 else (1-u).pow(2)*1.7
            val pull=JellyScaleExchangeMotion.smooth(0.1,0.9,f.melt)*0.85
            val x=cx+cos(a)*rr*p*(1-pull); val y=cy-p*(0.1+sin(a)*rr*0.35+fall)*(1-pull)
            round(c,x-p*0.2,y-p*0.085,p*0.4,p*0.17,p*0.085,colours[i%5])
        }
        if(f.melt>0.55) {
            val rise=JellyScaleExchangeMotion.smooth(0.0,0.6,f.coin)
            val fScale=1-0.78*f.fly
            val x=cx+(l.pillX-28-cx)*f.fly
            val y=cy-p*0.77*rise+(l.pillY-cy+p*0.77)*f.fly-p*0.6*sin(PI*f.fly)
            c.saveLayerAlpha(null,(255*(1-JellyScaleExchangeMotion.smooth(0.85,1.0,f.fly))).toInt())
            paint.color=if(f.coin>0.5) JellyScaleExchangeTheme.ACCENT else 0xFFFF73A8.toInt()
            c.drawCircle(x.toFloat(),y.toFloat(),(p*0.46*fScale).toFloat(),paint)
            if(f.coin>0.5) star(c,x,y,p*0.2*fScale,0xFFB97A00.toInt())
            c.restore()
        }
    }
}

@RequiresApi(33)
private class JellyScaleExchangeStage {
    private val shader=RuntimeShader(JellyScaleExchangeShader.SOURCE)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(c: Canvas,l: JellyScaleExchangeMotion.Layout,f: JellyScaleExchangeMotion.Frame,density: Float,reduced: Boolean) {
        val tilt=JellyScaleExchangeMotion.TILT
        val riseSy=1.05*cos(tilt)-0.25*sin(tilt)
        val targetSx=(l.pillX-28-l.w/2)/l.ppu; val targetSy=-(l.pillY-l.stageY)/l.ppu
        val dx=targetSx*f.fly; val dy=(targetSy-riseSy)*f.fly/cos(tilt)+sin(PI*f.fly)*0.6
        shader.setFloatUniform("uDensity",density)
        shader.setFloatUniform("uView",l.w.toFloat(),l.h.toFloat(),l.ppu.toFloat(),density)
        shader.setFloatUniform("uPour",(if(reduced) ceil(f.pour-0.001) else f.pour).toFloat(),f.melt.toFloat(),f.coin.toFloat(),f.spin.toFloat())
        shader.setFloatUniform("uCoin",dx.toFloat(),dy.toFloat(),(1-0.78*f.fly).toFloat(),(1-JellyScaleExchangeMotion.smooth(0.85,1.0,f.fly)).toFloat())
        shader.setFloatUniform("uScale",JellyScaleExchangeMotion.sinkOf(f.pour,f.coin).toFloat(),tilt.toFloat(),0f,l.stageY.toFloat())
        paint.color=android.graphics.Color.WHITE; paint.shader=shader
        c.drawRect(0f,0f,(l.w*density).toFloat(),(l.h*density).toFloat(),paint)
    }
}
