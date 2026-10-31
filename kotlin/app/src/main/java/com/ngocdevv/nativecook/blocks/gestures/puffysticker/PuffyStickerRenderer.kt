package com.ngocdevv.nativecook.blocks.gestures.puffysticker

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.*

internal class PuffyStickerRenderer(context: Context) {
    private val regular=Typeface.createFromAsset(context.assets,"puffysticker/puffysticker_Inter-Regular.ttf")
    private val medium=Typeface.createFromAsset(context.assets,"puffysticker/puffysticker_Inter-Medium.ttf")
    private val semibold=Typeface.createFromAsset(context.assets,"puffysticker/puffysticker_Inter-SemiBold.ttf")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader=if(Build.VERSION.SDK_INT>=33) PuffyStickerShaderRenderer() else null
    private val segment=PuffyStickerChannel();private var segmentOn=false
    val doneWidth: Float get() {paint.typeface=semibold;paint.textSize=15f;return paint.measureText("Done")+32f}
    fun draw(canvas: Canvas,l: PuffyStickerTheme.Layout,s: PuffyStickerState,density: Float) {
        val on=s.puff.value>.5f
        if(on!=segmentOn) {segmentOn=on;segment.tween(if(on) 96f else 0f,s.time,.2f,::puffyStickerOutEase)}
        val segmentX=segment.at(s.time)
        canvas.save();canvas.scale(density,density)
        fun round(x: Float,y: Float,w: Float,h: Float,r: Float,color: Int) {
            paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color;canvas.drawRoundRect(x,y,x+w,y+h,r,r,paint)
        }
        fun text(copy: String,x: Float,top: Float,size: Float,color: Int,font: Typeface=medium,center: Boolean=true) {
            paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color;paint.textSize=size;paint.typeface=font;paint.textAlign=if(center) Paint.Align.CENTER else Paint.Align.LEFT
            canvas.drawText(copy,x,top-paint.fontMetrics.ascent,paint)
        }
        text("Cancel",16f,l.barTop+12,16f,PuffyStickerTheme.INK,regular,false)
        text("New sticker",l.w/2,l.barTop+11,17f,PuffyStickerTheme.INK,semibold)
        val doneW=doneWidth
        round(l.w-16-doneW,l.barTop+6,doneW,32f,16f,PuffyStickerTheme.ACCENT)
        text("Done",l.w-16-doneW/2,l.barTop+12,15f,0xFF1A1600.toInt(),semibold)
        text(if(s.puff.value>.5f) "Drag it, or pick another shape" else "Hold the sticker to puff it up",l.w/2,l.hintTop,13f,PuffyStickerTheme.MUTE)
        round(l.w/2-100,l.switchTop,200f,40f,20f,PuffyStickerTheme.SURFACE)
        val press=1-.04f*s.flatPress
        canvas.save();canvas.translate(l.w/2-96+segmentX+48,l.switchTop+20);canvas.scale(press,press)
        round(-48f,-16f,96f,32f,16f,PuffyStickerTheme.ON);canvas.restore()
        text("Flat",l.w/2-48,l.switchTop+11,14f,if(s.puff.value<=.5f) PuffyStickerTheme.INK else PuffyStickerTheme.MUTE)
        text("Puffy",l.w/2+48,l.switchTop+11,14f,if(s.puff.value>.5f) PuffyStickerTheme.INK else PuffyStickerTheme.MUTE)
        for(i in 0..3) {
            val cx=l.trayLeft+i*80+32;val cy=l.trayTop+32
            val scale=if(s.thumbDown==i) .96f else 1-.04f*if(s.tapAt==i) s.tapPress else 0f
            canvas.save();canvas.translate(cx,cy);canvas.scale(scale,scale)
            round(-32f,-32f,64f,64f,18f,PuffyStickerTheme.SURFACE)
            if(s.shape.value.roundToInt().coerceIn(0,3)==i) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=PuffyStickerTheme.INK
                canvas.drawRoundRect(-31f,-31f,31f,31f,17f,17f,paint);paint.style=Paint.Style.FILL
            }
            canvas.restore()
        }
        canvas.restore()
        for(i in 0..3) sticker(canvas,l.trayLeft+i*80+32,l.trayTop+32,64*.34f,.85f,i.toFloat(),0f,0f,0f,0f,density,
            floatArrayOf(l.trayLeft+i*80,l.trayTop,l.trayLeft+i*80+64,l.trayTop+64))
        sticker(canvas,l.stageX+s.x.value,l.stageY+s.y.value,l.sticker,s.puff.value,s.shape.value,s.squish.value,s.axis,s.x.value,s.y.value,density,floatArrayOf(0f,0f,l.w,l.h))
        canvas.save();canvas.scale(density,density)
        val alpha=s.touch.coerceIn(0f,1f);val radius=30*(.8f+.2f*alpha)
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=android.graphics.Color.argb((alpha*.14f*255).toInt(),255,255,255)
        canvas.drawCircle(l.stageX,l.stageY+l.sticker*.15f,radius,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=android.graphics.Color.argb((alpha*.75f*255).toInt(),255,255,255)
        canvas.drawCircle(l.stageX,l.stageY+l.sticker*.15f,radius,paint)
        paint.style=Paint.Style.FILL;canvas.restore()
    }
    private fun sticker(canvas: Canvas,cx: Float,cy: Float,r: Float,puff: Float,shape: Float,squish: Float,axis: Float,dx: Float,dy: Float,density: Float,rect: FloatArray) {
        if(Build.VERSION.SDK_INT>=33 && shader!=null) shader.draw(canvas,cx,cy,r,puff,shape,squish,axis,dx,dy,density,rect)
        else {
            canvas.save();canvas.scale(density,density);canvas.translate(cx,cy)
            canvas.rotate(axis*180/PI.toFloat());canvas.scale(1+squish,1/(1+squish));canvas.rotate(-axis*180/PI.toFloat())
            fallback(canvas,r,shape.roundToInt().coerceIn(0,3));canvas.restore()
        }
    }
    /** Android 8–12: legible flat die-cut artwork, with the same colours and hit targets. */
    private fun fallback(canvas: Canvas,r: Float,index: Int) {
        canvas.save();canvas.scale(r,r)
        val path=Path()
        when(index) {
            0 -> {path.moveTo(0f,.83f);path.cubicTo(-1.35f,-.1f,-.79f,-1.16f,0f,-.51f);path.cubicTo(.79f,-1.16f,1.35f,-.1f,0f,.83f)}
            1 -> {for(i in 0 until 10) {val angle=-PI/2+i*PI/5;val rad=if(i%2==0) .99f else .49f;val x=cos(angle).toFloat()*rad;val y=sin(angle).toFloat()*rad+.06f;if(i==0) path.moveTo(x,y) else path.lineTo(x,y)};path.close()}
            2 -> {path.moveTo(-.83f,.45f);path.cubicTo(-1.1f,-.05f,-.6f,-.48f,-.43f,-.24f);path.cubicTo(-.45f,-.93f,.55f,-.9f,.46f,-.2f);path.cubicTo(1f,-.52f,1.18f,.54f,.65f,.54f);path.lineTo(-.65f,.54f);path.close()}
            else -> path.addCircle(0f,0f,.86f,Path.Direction.CW)
        }
        paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=.15f;paint.strokeJoin=Paint.Join.ROUND;paint.color=0xFFFBFBFC.toInt();canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL;paint.color=PuffyStickerTheme.colours[index];canvas.drawPath(path,paint)
        if(index==3) {
            paint.color=0xFF3B1A08.toInt();canvas.drawOval(-.36f,-.345f,-.18f,-.055f,paint);canvas.drawOval(.18f,-.345f,.36f,-.055f,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=.11f;canvas.drawArc(-.44f,-.5f,.44f,.38f,25f,130f,false,paint);paint.style=Paint.Style.FILL
        }
        canvas.restore()
    }
}

/** Kept behind an API gate so older Android releases never construct RuntimeShader. */
@TargetApi(33)
private class PuffyStickerShaderRenderer {
    private val shader=RuntimeShader(PuffyStickerAgsl)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas: Canvas,cx: Float,cy: Float,r: Float,puff: Float,shape: Float,squish: Float,axis: Float,dx: Float,dy: Float,density: Float,rect: FloatArray) {
        val value=shape.coerceIn(0f,3f);val a=min(floor(value),2f)
        shader.setFloatUniform("uDensity",density);shader.setFloatUniform("uC",cx,cy);shader.setFloatUniform("uS",r)
        shader.setFloatUniform("uPuff",puff);shader.setFloatUniform("uA",a);shader.setFloatUniform("uB",a+1);shader.setFloatUniform("uW",value-a)
        shader.setFloatUniform("uScale",1+squish,1/(1+squish));shader.setFloatUniform("uRot",axis)
        shader.setFloatUniform("uLight",-dx/900,dy/900);shader.setFloatUniform("uPixelRatio",density)
        paint.alpha=255;paint.shader=shader
        canvas.drawRect(rect[0]*density,rect[1]*density,rect[2]*density,rect[3]*density,paint)
    }
}
