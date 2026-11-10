package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose.VitaminCapsuleDoseTheme as C

/** All geometry uses RN device points, after the Canvas is scaled once by display density. */
internal class VitaminCapsuleDoseArt(context: Context) {
    private val fonts = listOf("Regular", "Medium", "SemiBold", "Bold").map {
        Typeface.createFromAsset(context.assets, "vitamincapsuledose/vitamincapsuledose_Poppins-$it.ttf")
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallback = VitaminCapsuleDoseFallback()
    private val runtime = if (Build.VERSION.SDK_INT >= 33) runCatching { VitaminCapsuleDoseRuntime() }
        .onFailure { Log.w("VitaminCapsuleDose", "AGSL unavailable; showing the flat hero", it) }.getOrNull() else null
    private val back = Path().apply { moveTo(8f,2f);lineTo(2f,8f);lineTo(8f,14f) }
    private val edit = Path().apply {
        moveTo(4f,15.5f);lineTo(4.8f,12f);lineTo(13.8f,3f);cubicTo(15.7f,1.1f,18.5f,3.9f,16.6f,5.8f)
        lineTo(7.6f,14.8f);close();moveTo(12.3f,4.6f);lineTo(15.2f,7.5f);moveTo(3.5f,19f);lineTo(18f,19f)
    }
    private val logs = Path().apply {
        addRoundRect(3.5f,3.5f,18.5f,18.5f,2.5f,2.5f,Path.Direction.CW)
        moveTo(7.5f,11f);lineTo(10f,13.5f);lineTo(14.5f,8.5f)
        moveTo(7.5f,2f);lineTo(7.5f,5f);moveTo(14.5f,2f);lineTo(14.5f,5f)
    }
    private val tick = Path().apply { moveTo(-4f,0.1f);lineTo(-1.3f,2.7f);lineTo(4f,-2.9f) }
    private fun fill(color: Int) { paint.reset(); paint.isAntiAlias = true; paint.color = color; paint.style = Paint.Style.FILL }
    private fun line(color: Int, width: Float) { fill(color); paint.style=Paint.Style.STROKE;paint.strokeWidth=width;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND }
    private fun text(canvas: Canvas, text: String, size: Float, font: Int, x: Double, top: Double, color: Int, line: Float, center: Boolean = false) {
        fill(color);paint.typeface=fonts[font];paint.textSize=size
        val fm=paint.fontMetrics
        val baseline=top+(line-(fm.descent-fm.ascent))/2-fm.ascent
        canvas.drawText(text,(x-if(center)paint.measureText(text).toDouble()/2 else 0.0).toFloat(),baseline.toFloat(),paint)
    }
    private fun round(canvas: Canvas, x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int) {
        fill(color);canvas.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),radius.toFloat(),radius.toFloat(),paint)
    }
    private fun icon(canvas: Canvas, path: Path, x: Double, y: Double, color: Int, width: Float) {
        canvas.save();canvas.translate(x.toFloat(),y.toFloat());line(color,width);canvas.drawPath(path,paint);canvas.restore()
    }
    fun draw(canvas: Canvas, l: VitaminCapsuleDoseLayout, d: VitaminCapsuleDoseDriver) {
        val f=d.frame;val sx=l.sx;val med=C.meds[d.med]
        canvas.drawColor(Color.BLACK)
        round(canvas,14*sx,l.y(72.0),374*sx,120.0,26.0,0xFFB8B9C0.toInt())
        round(canvas,0.0,l.y(88.0),l.w,l.h-l.y(88.0)+26,26.0,Color.WHITE)
        // Back and header.
        icon(canvas,back,35*sx-4,l.y(124.0)-8,C.PINK_TEXT,2.2f)
        text(canvas,"Back",16f,2,35*sx+16,l.y(124.0)-11,C.PINK_TEXT,22f)
        text(canvas,med.name,32f,3,25*sx,l.y(338.0)-20,C.INK,42f)
        text(canvas,"${med.kind}  •  ${med.dose}",14.3f,0,25*sx,l.y(338.0)+26,C.SUB,20f)
        fill(Color.WHITE);paint.setShadowLayer(18f,0f,6f,0x1A1E2246)
        canvas.drawCircle((353*sx).toFloat(),l.y(349.0).toFloat(),24f,paint);paint.clearShadowLayer()
        icon(canvas,edit,353*sx-11,l.y(349.0)-11,C.PINK_TEXT,1.7f)
        // The dosage and frequency chips.
        for(i in 0..1) {
            val x=(if(i==0)25 else 210)*sx;val y=l.chipTop;val h=l.chipH
            round(canvas,x,y,168*sx,h,14.0,C.FILL)
            canvas.save();canvas.translate((x+16).toFloat(),(y+h/2-11).toFloat())
            if(i==0) {
                canvas.save();canvas.translate(10f,10f);canvas.rotate(-45f)
                fill(0xFFEE3A8F.toInt());canvas.save();canvas.clipRect(-10f,-5f,0f,5f);canvas.drawRoundRect(-10f,-4.6f,10f,4.6f,4.6f,4.6f,paint);canvas.restore()
                paint.alpha=140;canvas.save();canvas.clipRect(0f,-5f,10f,5f);canvas.drawRoundRect(-10f,-4.6f,10f,4.6f,4.6f,4.6f,paint);canvas.restore();canvas.restore()
            } else {
                fill(0xFF1D74DB.toInt());canvas.drawCircle(11f,11f,10f,paint)
                line(Color.WHITE,1.6f);canvas.drawLine(11f,5.5f,11f,11f,paint);canvas.drawLine(11f,11f,14f,13f,paint)
            }
            canvas.restore()
            text(canvas,if(i==0)med.daily else med.each,18f,2,x+51,y+9+l.chipPad,C.INK,26f)
            text(canvas,if(i==0)"Daily Dosage" else "Each Day",14.4f,0,x+51,y+35+l.chipPad,C.SUB,22f)
        }
        // Schedule and the three independent checkboxes.
        text(canvas,"Schedule",20f,2,25*sx,l.y(512.0)-15,C.INK,30f)
        fill(C.PINK_TEXT);paint.typeface=fonts[2];paint.textSize=16f
        val logsX=(368+12)*sx-22
        text(canvas,"All Logs",16f,2,logsX-8-paint.measureText("All Logs"),l.y(512.0)-11,C.PINK_TEXT,22f)
        icon(canvas,logs,logsX,l.y(512.0)-11,C.PINK_TEXT,1.6f)
        round(canvas,25*sx,l.y(540.0),353*sx,132*l.k,16.0,C.FILL)
        for(r in 0..2) {
            val y=l.rowsY[r];val on=d.checks[d.med*3+r]==1
            text(canvas,med.times[r],16f,2,41*sx,y-13,C.PINK_TEXT,26f)
            line(C.RING,1.6f);canvas.drawCircle(l.checkX.toFloat(),y.toFloat(),9.2f,paint)
            if(on) { fill(C.GREEN);canvas.drawCircle(l.checkX.toFloat(),y.toFloat(),10.5f,paint)
                icon(canvas,tick,l.checkX,y,Color.WHITE,1.8f) }
        }
        round(canvas,25*sx,l.y(689.0),353*sx,49*l.k,14.0,C.FILL)
        text(canvas,"Export to PDF",16f,2,l.w/2,l.y(689.0)+(49*l.k-22)/2,C.INK,22f,true)
        // A held button fills from the left, clipped to its pill silhouette.
        val bx=25*sx;val by=l.y(777.0);val bw=353*sx;val bh=56*l.k
        val buttonScale=1-0.02*min(1.0,f.hold*8)
        canvas.save();canvas.scale(buttonScale.toFloat(),buttonScale.toFloat(),(bx+bw/2).toFloat(),(by+bh/2).toFloat())
        round(canvas,bx,by,bw,bh,bh/2,C.PINK)
        if(f.hold>0) {
            val clip=Path().apply{addRoundRect(bx.toFloat(),by.toFloat(),(bx+bw).toFloat(),(by+bh).toFloat(),(bh/2).toFloat(),(bh/2).toFloat(),Path.Direction.CW)}
            canvas.save();canvas.clipPath(clip);fill(C.PINK_DEEP)
            canvas.drawRect(bx.toFloat(),by.toFloat(),(bx+bw*f.hold).toFloat(),(by+bh).toFloat(),paint);canvas.restore()
        }
        text(canvas,"Log Medication",16f,2,l.w/2,by+(bh-22)/2,Color.WHITE,22f,true);canvas.restore()
        // GPU pass sits over the screen, exactly as the RN transparent WebGPU surface does.
        if(Build.VERSION.SDK_INT>=33 && runtime!=null && !d.reduced) runtime.draw(canvas,l,f)
        else fallback.draw(canvas,l,f)
        if(Build.VERSION.SDK_INT>=33 && runtime!=null && !d.reduced) d.pearls.draw(canvas,l,f,l.rowsY[d.targetRow])
        if(f.finger>0) {
            val at=when(f.fingerAt) {
                0 -> { val k=.45+.55*f.fingerX; Pair(l.cx+l.unit*.7*k,l.cy-l.unit*.7*k) }
                1 -> Pair(l.w/2,l.logY)
                2 -> Pair(l.checkX,l.rowsY[1])
                else -> Pair(l.w*(.12+.72*f.fingerX),l.floor-6)
            }
            val r=(22*(1-.12*f.press)).toFloat()
            fill(Color.argb((.08*f.finger*255).toInt(),20,24,60));canvas.drawCircle(at.first.toFloat(),at.second.toFloat(),r,paint)
            line(Color.argb((.35*f.finger*255).toInt(),20,24,60),1.5f);canvas.drawCircle(at.first.toFloat(),at.second.toFloat(),r,paint)
        }
    }
}
