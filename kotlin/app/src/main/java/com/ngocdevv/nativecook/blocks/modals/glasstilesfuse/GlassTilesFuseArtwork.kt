package com.ngocdevv.nativecook.blocks.modals.glasstilesfuse

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.RuntimeShader
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import kotlin.math.*


internal class GlassTilesFuseArtwork(private val fonts:GlassTilesFuseTheme) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val text=TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tiles=if(Build.VERSION.SDK_INT>=33) GlassTilesFuseTiles() else null
    private fun round(c:Canvas,x:Double,y:Double,w:Double,h:Double,r:Double,color:Int) {
        paint.shader=null; paint.color=color; paint.style=Paint.Style.FILL
        c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)
    }
    private fun label(c:Canvas,s:String,x:Double,y:Double,size:Double,font:Typeface,color:Int,spacing:Double=0.0,center:Boolean=false) {
        text.typeface=font; text.textSize=size.toFloat(); text.color=color
        // Canvas Paint has no tracking property: use positioned glyphs for the short labels.
        val widths=FloatArray(s.length); text.getTextWidths(s,widths)
        var px=if(center) x-(widths.sum()+spacing*max(0,s.length-1))/2 else x
        s.forEachIndexed { i,ch -> c.drawText(ch.toString(),px.toFloat(),(y-text.fontMetrics.ascent).toFloat(),text); px+=widths[i]+spacing }
    }
    private fun paragraph(c:Canvas,s:String,l:GlassTilesFuseMotion.Layout) {
        text.typeface=fonts.regular; text.textSize=15f; text.color=GlassTilesFuseTheme.BODY; text.textScaleX=1f
        val layout=StaticLayout.Builder.obtain(s,0,s.length,text,(l.w-64).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false)
            .setLineSpacing(20f-(text.fontMetrics.descent-text.fontMetrics.ascent),1f).build()
        c.save(); c.translate(32f,(l.textTop+30).toFloat()); layout.draw(c); c.restore()
    }
    fun draw(c:Canvas,l:GlassTilesFuseMotion.Layout,f:GlassTilesFuseDriver.Frame,density:Float) {
        val r=GlassTilesFuseMotion.rest(l,f.restTime).let { it.copy(gx=it.gx+f.dragX,gy=it.gy+f.dragY,ga=it.ga+f.dragX*0.0015) }
        val p=GlassTilesFuseMotion.linked(l,r,f.link)
        val connected=f.link>0.56
        c.save(); c.scale(density,density)
        c.drawColor(GlassTilesFuseTheme.PAGE)
        label(c,"Connections",20.0,l.titleTop,30.0,fonts.bold,GlassTilesFuseTheme.TITLE,-0.8)
        label(c,"Let the assistant use what you already have.",20.0,l.titleTop+40,15.0,fonts.regular,GlassTilesFuseTheme.ROW_SUB)
        for((i,row) in GlassTilesFuseTheme.ROWS.withIndex()) {
            val y=l.rowsTop+i*l.rowH
            c.save(); c.translate(20f,(y+13.5).toFloat()); icon(c,i); c.restore()
            label(c,row.first,70.0,y+12,16.0,fonts.semibold,GlassTilesFuseTheme.ROW_NAME)
            label(c,row.second,70.0,y+33,13.0,fonts.regular,GlassTilesFuseTheme.ROW_SUB)
            text.typeface=fonts.semibold; text.textSize=13f
            val pw=text.measureText("Connect")+28.0
            round(c,l.w-20-pw,y+16.5,pw,30.0,15.0,GlassTilesFuseTheme.PILL)
            label(c,"Connect",l.w-20-pw/2,y+23,13.0,fonts.semibold,GlassTilesFuseTheme.PILL_INK,center=true)
        }
        paint.color=0x6B000000; paint.style=Paint.Style.FILL
        c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint)
        val sheet=Path().apply { addRoundRect(RectF(0f,l.sheetTop.toFloat(),l.w.toFloat(),(l.h+40).toFloat()),floatArrayOf(28f,28f,28f,28f,0f,0f,0f,0f),Path.Direction.CW) }
        c.save(); c.clipPath(sheet)
        paint.color=GlassTilesFuseTheme.SHEET; c.drawRect(0f,l.sheetTop.toFloat(),l.w.toFloat(),(l.h+40).toFloat(),paint)
        round(c,l.w/2-18,l.sheetTop+8,36.0,5.0,3.0,GlassTilesFuseTheme.GRABBER)
        c.restore(); c.restore()
        // The shader is drawn in physical pixels, with point-space uniforms and a density uniform.
        c.save()
        val physicalSheet=Path().apply { addRoundRect(RectF(0f,(l.sheetTop*density).toFloat(),(l.w*density).toFloat(),((l.h+40)*density).toFloat()),floatArrayOf(28*density,28*density,28*density,28*density,0f,0f,0f,0f),Path.Direction.CW) }
        c.clipPath(physicalSheet); c.translate(0f,(l.sheetTop*density).toFloat())
        if(Build.VERSION.SDK_INT>=33 && tiles!=null) tiles.draw(c,l,p,f,density)
        else { c.scale(density,density); fallback(c,l,p,f) }
        c.restore()
        c.save(); c.scale(density,density)
        label(c,if(connected) "Mailbox connected" else "Mailbox",l.w/2,l.textTop,18.0,fonts.semibold,GlassTilesFuseTheme.TITLE,-0.3,true)
        paragraph(c,if(connected) "Mina’s inbox is ready to use across your chats and apps." else "Connect a Mailbox account for Orbit to use across your chats and apps.",l)
        c.save(); c.scale((1-0.04*f.press).toFloat(),(1-0.04*f.press).toFloat(),(l.w/2).toFloat(),(l.buttonTop+26).toFloat())
        round(c,32.0,l.buttonTop,l.w-64,52.0,26.0,GlassTilesFuseTheme.BUTTON)
        text.typeface=fonts.semibold; text.textSize=16f
        val lineH=text.fontMetrics.descent-text.fontMetrics.ascent
        label(c,if(connected) "Done" else "Connect Mailbox",l.w/2,l.buttonTop+(52-lineH)/2,16.0,fonts.semibold,GlassTilesFuseTheme.BUTTON_INK,-0.2,true)
        c.restore()
        if(f.finger>0) {
            val x=if(f.fingerOn==1) p.gx+f.pull else l.w/2
            val y=if(f.fingerOn==1) p.gy else l.buttonTop+26
            val radius=22*(1-0.12*f.press)
            paint.style=Paint.Style.FILL; paint.color=android.graphics.Color.argb((255*0.35*0.85*f.finger).toInt(),255,255,255)
            c.drawCircle(x.toFloat(),y.toFloat(),radius.toFloat(),paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
            paint.color=android.graphics.Color.argb((255*0.7*0.85*f.finger).toInt(),255,255,255)
            c.drawCircle(x.toFloat(),y.toFloat(),(radius-0.75).toFloat(),paint); paint.style=Paint.Style.FILL
        }
        c.restore()
    }
    private fun icon(c:Canvas,i:Int) {
        fun poly(points:FloatArray,color:Int,stroke:Float=0f) {
            val path=Path(); path.moveTo(points[0],points[1]); for(j in 2 until points.size step 2) path.lineTo(points[j],points[j+1])
            paint.color=color
            if(stroke>0) { paint.style=Paint.Style.STROKE; paint.strokeWidth=stroke; paint.strokeJoin=Paint.Join.ROUND; paint.strokeCap=Paint.Cap.ROUND }
            else { path.close(); paint.style=Paint.Style.FILL }
            c.drawPath(path,paint); paint.style=Paint.Style.FILL
        }
        when(i) {
            0->{
                round(c,0.0,0.0,36.0,36.0,9.0,0xFF8E8E93.toInt()); paint.color=0xFFF2F2F2.toInt(); c.drawCircle(18f,14f,5.5f,paint)
                val p=Path().apply { moveTo(8f,29f); cubicTo(9.5f,23.5f,13.5f,21f,18f,21f); cubicTo(22.5f,21f,26.5f,23.5f,28f,29f); close() }; c.drawPath(p,paint)
            }
            1->{ round(c,0.0,0.0,36.0,36.0,9.0,0xFF2F6BFF.toInt()); round(c,8.0,11.0,20.0,14.0,2.5,0xFFFFFFFF.toInt()); poly(floatArrayOf(8.8f,12f,18f,19f,27.2f,12f),0xFF2F6BFF.toInt(),1.8f) }
            2->{
                round(c,0.0,0.0,36.0,36.0,9.0,0xFFFFFFFF.toInt()); round(c,0.0,0.0,36.0,10.0,0.0,0xFFF2554D.toInt())
                val p=Path().apply { moveTo(13f,17f); lineTo(17f,17f); lineTo(14.5f,20.2f); cubicTo(16.1f,20.2f,17.2f,21.2f,17.2f,22.6f); cubicTo(17.2f,24.1f,16f,25.2f,14.4f,25.2f); cubicTo(13.3f,25.2f,12.4f,24.7f,12f,23.9f); moveTo(21f,18.5f); lineTo(23.4f,17f); lineTo(23.4f,25f) }
                paint.color=0xFF1C1C1E.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.7f; c.drawPath(p,paint); paint.style=Paint.Style.FILL
            }
            else->{
                round(c,0.0,0.0,36.0,36.0,9.0,0xFF1F1F21.toInt()); poly(floatArrayOf(18f,9f,27f,24.5f,9f,24.5f),0xFF4BC27D.toInt())
                poly(floatArrayOf(18f,9f,22.5f,16.8f,13.5f,16.8f),0xFFF4C443.toInt()); poly(floatArrayOf(9f,24.5f,27f,24.5f,24.4f,28f,11.6f,28f),0xFF3D8BFF.toInt())
            }
        }
    }
    private fun fallback(c:Canvas,l:GlassTilesFuseMotion.Layout,p:GlassTilesFuseMotion.Poses,f:GlassTilesFuseDriver.Frame) {
        round(c,0.0,0.0,l.w,l.stageH+12,0.0,GlassTilesFuseTheme.SHEET)
        fun tile(x:Double,y:Double,sz:Double,angle:Double,color:Int,mail:Boolean) {
            c.save(); c.translate(x.toFloat(),y.toFloat()); c.rotate((angle*180/PI).toFloat())
            round(c,-sz/2,-sz/2,sz,sz,sz*0.27,color)
            if(mail) {
                paint.style=Paint.Style.STROKE; paint.strokeWidth=(sz*0.04).toFloat(); paint.color=0xFFFFB338.toInt()
                c.drawRoundRect((-sz*0.27).toFloat(),(-sz*0.17).toFloat(),(sz*0.27).toFloat(),(sz*0.21).toFloat(),(sz*0.05).toFloat(),(sz*0.05).toFloat(),paint)
                val mark=Path().apply { moveTo((-sz*0.25).toFloat(),(-sz*0.15).toFloat()); lineTo(0f,(sz*0.02).toFloat()); lineTo((sz*0.25).toFloat(),(-sz*0.15).toFloat()) }; c.drawPath(mark,paint)
            } else {
                paint.style=Paint.Style.FILL; paint.color=0xFF181818.toInt()
                for((x0,y0) in listOf(-0.16 to -0.05,0.07 to -0.13,-0.07 to 0.14,0.16 to 0.06)) c.drawCircle((sz*x0).toFloat(),(sz*y0).toFloat(),(sz*0.105).toFloat(),paint)
            }
            paint.style=Paint.Style.FILL; c.restore()
        }
        tile(p.wx+f.recoil,p.wy-l.sheetTop,l.white*p.size,p.wa,0xFFE8E8E6.toInt(),false)
        tile(p.gx+f.pull,p.gy+f.pullY-l.sheetTop-6*f.lift,l.glass*p.size*(1+0.06*f.lift),p.ga+f.pull*0.0008,0xCC333338.toInt(),true)
        if(p.check>0) {
            val x=(p.wx+f.recoil+p.gx+f.pull)/2; val y=(p.wy+p.gy+f.pullY)/2-l.sheetTop
            paint.color=0xFF33C76B.toInt(); c.drawCircle(x.toFloat(),y.toFloat(),(15*p.check).toFloat(),paint)
            if(p.check>0.5) {
                val ck=Path().apply { moveTo((x-4.5).toFloat(),y.toFloat()); lineTo((x-1.2).toFloat(),(y+3.6).toFloat()); lineTo((x+4.8).toFloat(),(y-3).toFloat()) }
                paint.color=0xFFFFFFFF.toInt(); paint.style=Paint.Style.STROKE; paint.strokeWidth=2.55f; c.drawPath(ck,paint); paint.style=Paint.Style.FILL
            }
        }
    }
}

@RequiresApi(33)
private class GlassTilesFuseTiles {
    private val shader=RuntimeShader(GlassTilesFuseShader.SOURCE)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(c:Canvas,l:GlassTilesFuseMotion.Layout,p:GlassTilesFuseMotion.Poses,f:GlassTilesFuseDriver.Frame,density:Float) {
        shader.setFloatUniform("uWhite",(p.wx+f.recoil).toFloat(),(p.wy-l.sheetTop).toFloat(),(l.white*p.size).toFloat(),p.wa.toFloat())
        shader.setFloatUniform("uGlass",(p.gx+f.pull).toFloat(),(p.gy+f.pullY-l.sheetTop-6*f.lift).toFloat(),(l.glass*p.size*(1+0.06*f.lift)).toFloat(),(p.ga+f.pull*0.0008).toFloat())
        shader.setFloatUniform("uFuse",(p.fuse*f.stick).toFloat()); shader.setFloatUniform("uSeam",p.seam.toFloat())
        shader.setFloatUniform("uCheck",p.check.toFloat()); shader.setFloatUniform("uLift",f.lift.toFloat()); shader.setFloatUniform("uDensity",density)
        // uPixelRatio is declared but unused in the reference, so RuntimeShader may optimize it out.
        paint.shader=shader; paint.alpha=255
        c.drawRect(0f,0f,(l.w*density).toFloat(),((l.stageH+12)*density).toFloat(),paint)
    }
}
