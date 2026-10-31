package com.ngocdevv.nativecook.blocks.cards.liquidmetalswap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.liquidmetalswap.LiquidMetalSwapMotion as M
import com.ngocdevv.nativecook.blocks.cards.liquidmetalswap.LiquidMetalSwapTheme as C

@RequiresApi(33)
internal class LiquidMetalSwapRuntime {
    private val shader=RuntimeShader(LiquidMetalSwapShader)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader=this@LiquidMetalSwapRuntime.shader }
    fun draw(canvas: Canvas,l: LiquidMetalSwapLayout,driver: LiquidMetalSwapDriver,density: Float) {
        val k=M.look(driver.frame.m)
        val sway=if(driver.reduced) 0.0 else M.sway(if(driver.mode==0) (driver.clock-driver.autoStart)%M.LOOP else driver.clock)
        shader.setFloatUniform("uDensity",density)
        shader.setFloatUniform("uStage",l.w.toFloat(),l.stageH.toFloat(),density,driver.clock.toFloat())
        shader.setFloatUniform("uCoin",k.kind.toFloat(),k.sink.toFloat(),k.soft.toFloat(),(sway+driver.frame.yaw).toFloat())
        shader.setFloatUniform("uPool",k.poolR.toFloat(),k.poolH.toFloat(),k.wobble.toFloat(),if(driver.reduced) 0f else k.drops.toFloat())
        shader.setFloatUniform("uLight",0f,driver.lift.toFloat())
        val a=C.tokens[0].tint; val b=C.tokens[1].tint
        shader.setFloatUniform("uTint",(a[0]+(b[0]-a[0])*k.gold).toFloat(),(a[1]+(b[1]-a[1])*k.gold).toFloat(),(a[2]+(b[2]-a[2])*k.gold).toFloat())
        shader.setFloatUniform("uBg",11/255f,12/255f,16/255f)
        canvas.save(); canvas.translate(0f,(l.stageTop*density).toFloat())
        canvas.drawRect(0f,0f,(l.w*density).toFloat(),(l.stageH*density).toFloat(),paint); canvas.restore()
    }
}

// Static fonts and drawing resources are created once. Only this draw pass reads the frame.
internal class LiquidMetalSwapArtwork(context: Context) {
    private val regular=Typeface.createFromAsset(context.assets,"liquidmetalswap/liquidmetalswap_Inter-Regular.ttf")
    private val medium=Typeface.createFromAsset(context.assets,"liquidmetalswap/liquidmetalswap_Inter-Medium.ttf")
    private val semibold=Typeface.createFromAsset(context.assets,"liquidmetalswap/liquidmetalswap_Inter-SemiBold.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds=RectF()
    private val path=Path()
    private val runtime=if(Build.VERSION.SDK_INT>=33) LiquidMetalSwapRuntime() else null
    private fun colour(color: Int,alpha: Double=1.0) {
        paint.shader=null; paint.style=Paint.Style.FILL; paint.color=color
        paint.alpha=((color ushr 24)*alpha.coerceIn(0.0,1.0)).roundToInt()
    }
    private fun round(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int,alpha: Double=1.0) {
        colour(color,alpha); bounds.set(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()); c.drawRoundRect(bounds,r.toFloat(),r.toFloat(),paint)
    }
    private fun border(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,color: Int,stroke: Double) {
        colour(color); paint.style=Paint.Style.STROKE; paint.strokeWidth=stroke.toFloat()
        val inset=stroke/2; bounds.set((x+inset).toFloat(),(y+inset).toFloat(),(x+w-inset).toFloat(),(y+h-inset).toFloat())
        c.drawRoundRect(bounds,(r-inset).toFloat(),(r-inset).toFloat(),paint); paint.style=Paint.Style.FILL
    }
    private fun measure(value: String,size: Double,font: Int): Double {
        paint.typeface=when(font) { 1->medium; 2->semibold; else->regular }; paint.textSize=size.toFloat()
        paint.fontFeatureSettings="tnum"; return paint.measureText(value).toDouble()
    }
    private fun text(c: Canvas,value: String,x: Double,y: Double,size: Double,font: Int,color: Int,align: Int=0,alpha: Double=1.0,line: Double?=null,spacing: Double=0.0) {
        colour(color,alpha); val width=measure(value,size,font)+spacing*max(0,value.length-1)
        val fm=paint.fontMetrics; val height=line ?: (fm.descent-fm.ascent).toDouble()
        val baseline=y+(height-(fm.descent-fm.ascent))/2-fm.ascent
        var left=x-if(align==1) width/2 else if(align==2) width else 0.0
        if(spacing==0.0) c.drawText(value,left.toFloat(),baseline.toFloat(),paint)
        else {
            val advances=FloatArray(value.length); paint.getTextWidths(value,advances)
            for(i in value.indices) { c.drawText(value,i,i+1,left.toFloat(),baseline.toFloat(),paint); left+=advances[i]+spacing }
        }
    }
    private fun strokedPath(c: Canvas,color: Int,stroke: Double,alpha: Double=1.0) {
        colour(color,alpha); paint.style=Paint.Style.STROKE; paint.strokeWidth=stroke.toFloat()
        paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND; c.drawPath(path,paint); paint.style=Paint.Style.FILL
    }
    fun draw(c: Canvas,l: LiquidMetalSwapLayout,driver: LiquidMetalSwapDriver,density: Float) {
        val s=driver.frame; val k=M.look(s.m); val role=if(s.flip>0.5) 1 else 0
        c.drawColor(C.PAGE)
        if(Build.VERSION.SDK_INT>=33) runtime?.draw(c,l,driver,density)
        c.save(); c.scale(density,density)
        if(Build.VERSION.SDK_INT<33) fallback(c,l,k)
        text(c,"Swap",l.side,l.barTop,28.0,2,C.INK,line=44.0)
        val pillW=measure("Slippage 0.5%",13.0,1)+24
        round(c,l.w-l.side-pillW,l.barTop+6,pillW,32.0,16.0,C.CARD)
        border(c,l.w-l.side-pillW,l.barTop+6,pillW,32.0,16.0,C.EDGE,1.0)
        text(c,"Slippage 0.5%",l.w-l.side-pillW/2,l.barTop+6,13.0,1,C.MUTE,align=1,line=32.0)
        val name=if(k.kind<0.5) "Prism · PRSM" else "Spark · SPRK"
        val nameH=run { measure(name,13.0,1); (paint.fontMetrics.descent-paint.fontMetrics.ascent).toDouble() }
        text(c,name,l.w/2,l.stageTop+l.stageH-12-nameH,13.0,1,C.MUTE,align=1,alpha=1-k.sink,spacing=0.4)
        for(i in 0..1) {
            val y=l.cardsTop+i*120
            round(c,l.side,y,l.cardW,112.0,24.0,C.CARD)
            border(c,l.side,y,l.cardW,112.0,24.0,C.EDGE,1.0)
            text(c,if(i==0) "You pay" else "You receive",l.side+16,y+12,13.0,1,C.MUTE)
        }
        val rowAlpha=1-0.92*sin(PI*s.flip)
        row(c,l,l.cardsTop+s.flip*120,C.tokens[0],rowAlpha)
        row(c,l,l.cardsTop+(1-s.flip)*120,C.tokens[1],rowAlpha)
        val dy=l.cardsTop+98; val dx=l.w/2-18
        round(c,dx,dy,36.0,36.0,18.0,C.CARD); border(c,dx,dy,36.0,36.0,18.0,C.PAGE,4.0)
        path.reset(); path.moveTo((dx+18).toFloat(),(dy+12.5).toFloat()); path.lineTo((dx+18).toFloat(),(dy+23.5).toFloat())
        path.moveTo((dx+13.5).toFloat(),(dy+19).toFloat()); path.lineTo((dx+18).toFloat(),(dy+23.5).toFloat()); path.lineTo((dx+22.5).toFloat(),(dy+19).toFloat())
        strokedPath(c,C.MUTE,1.8)
        for((i,pair) in listOf("Rate" to C.rates[role],"Price impact" to "0.08%","Network fee" to "$0.12").withIndex()) {
            val y=l.rowsTop+i*36
            text(c,pair.first,l.side+4,y,14.0,0,C.MUTE,line=36.0)
            text(c,pair.second,l.w-l.side-4,y,14.0,1,C.INK,align=2,line=36.0)
        }
        button(c,l,s,driver.reduced)
        if(s.finger>0) {
            val x=if(s.fingerAt==1.0) l.w/2-40+90*s.fingerX else l.w/2
            val y=if(s.fingerAt==1.0) l.stageTop+l.stageH*0.42 else l.buttonTop+28
            c.save(); c.scale((1-0.12*s.press).toFloat(),(1-0.12*s.press).toFloat(),x.toFloat(),y.toFloat())
            round(c,x-22,y-22,44.0,44.0,22.0,0x59FFFFFF,0.85*s.finger)
            colour(0xB3FFFFFF.toInt(),0.85*s.finger); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
            c.drawCircle(x.toFloat(),y.toFloat(),21.25f,paint); paint.style=Paint.Style.FILL; c.restore()
        }
        c.restore()
    }
    private fun row(c: Canvas,l: LiquidMetalSwapLayout,y: Double,token: LiquidMetalSwapToken,alpha: Double) {
        val left=l.side+16; val right=l.w-l.side-16
        text(c,token.amount,left,y+34,32.0,2,C.INK,alpha=alpha,line=38.0)
        text(c,token.fiat,left,y+74,13.0,0,C.MUTE,alpha=alpha)
        val pillW=6+24+8+measure(token.symbol,15.0,2)+12
        round(c,right-pillW,y+34,pillW,36.0,18.0,0xFF20232B.toInt(),alpha)
        round(c,right-pillW+6,y+40,24.0,24.0,12.0,token.colour,alpha)
        text(c,token.symbol,right-12,y+34,15.0,2,C.INK,align=2,alpha=alpha,line=36.0)
        text(c,token.balance,right,y+78,13.0,0,C.MUTE,align=2,alpha=alpha)
    }
    private fun buttonLabel(c: Canvas,l: LiquidMetalSwapLayout,swapped: Boolean,dark: Boolean,alpha: Double) {
        val caption=if(swapped) "Swapped" else "Hold to swap"
        val textW=measure(caption,17.0,2)
        val center=l.w/2; val textX=if(swapped) center+13 else center
        text(c,caption,textX,l.buttonTop,17.0,2,if(dark) C.PAGE else C.INK,align=1,alpha=alpha,line=56.0)
        if(swapped) {
            val x=center-(textW+26)/2; val y=l.buttonTop+19
            path.reset(); path.moveTo((x+4).toFloat(),(y+9.5).toFloat()); path.lineTo((x+7.2).toFloat(),(y+12.7).toFloat()); path.lineTo((x+14).toFloat(),(y+5.8).toFloat())
            strokedPath(c,C.PAGE,2.2,alpha)
        }
    }
    private fun button(c: Canvas,l: LiquidMetalSwapLayout,s: M.Frame,reduced: Boolean) {
        c.save(); val scale=if(reduced) 1.0 else 1-0.04*s.press
        c.scale(scale.toFloat(),scale.toFloat(),(l.w/2).toFloat(),(l.buttonTop+28).toFloat())
        path.reset(); bounds.set(l.side.toFloat(),l.buttonTop.toFloat(),(l.side+l.cardW).toFloat(),(l.buttonTop+56).toFloat())
        path.addRoundRect(bounds,28f,28f,Path.Direction.CW); c.clipPath(path)
        round(c,l.side,l.buttonTop,l.cardW,56.0,28.0,0xFF23262E.toInt())
        round(c,l.side,l.buttonTop,l.cardW,56.0,28.0,if(s.start==1.0) C.GOLD else C.SILVER,s.swapped)
        buttonLabel(c,l,false,false,1-s.swapped)
        c.save(); c.clipRect(l.side.toFloat(),l.buttonTop.toFloat(),(l.side+M.clamp01(s.fill)*l.cardW).toFloat(),(l.buttonTop+56).toFloat())
        round(c,l.side,l.buttonTop,l.cardW,56.0,0.0,if(s.start==0.0) C.GOLD else C.SILVER,1-s.swapped)
        buttonLabel(c,l,false,true,1-s.swapped); c.restore()
        buttonLabel(c,l,false,true,min(s.swapped,1-s.doneText)); buttonLabel(c,l,true,true,min(s.swapped,s.doneText))
        c.restore()
    }
    private fun fallback(c: Canvas,l: LiquidMetalSwapLayout,k: M.Look) {
        val color=android.graphics.Color.rgb((230+(232-230)*k.gold).toInt(),(233+(194-233)*k.gold).toInt(),(239+(122-239)*k.gold).toInt())
        val floor=l.stageTop+l.stageH/2+83
        colour(color); c.drawOval((l.w/2-k.poolR*1.32).toFloat(),(floor-k.poolH).toFloat(),(l.w/2+k.poolR*1.32).toFloat(),(floor+k.poolH).toFloat(),paint)
        c.save(); c.clipRect(0f,l.stageTop.toFloat(),l.w.toFloat(),floor.toFloat())
        val cy=l.stageTop+l.stageH/2-20+k.sink*240
        round(c,l.w/2-103,cy-103,206.0,206.0,103.0,color)
        border(c,l.w/2-91,cy-91,182.0,182.0,91.0,C.PAGE,2.0)
        text(c,if(k.kind<0.5) "△" else "✦",l.w/2,cy-35,52.0,2,C.PAGE,align=1,line=70.0)
        c.restore()
    }
}
