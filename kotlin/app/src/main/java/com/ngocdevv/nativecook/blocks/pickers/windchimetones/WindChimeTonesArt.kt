package com.ngocdevv.nativecook.blocks.pickers.windchimetones

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.PathParser

@RequiresApi(33)
internal class WindChimeTonesRuntime {
    val shader=RuntimeShader(WindChimeTonesShader.source)
    fun update(l: WindChimeTonesLayout,f: WindChimeTonesMotion.Frame,reduced: Boolean) {
        shader.setFloatUniform("uSpeaker",l.cx.toFloat(),l.cy.toFloat(),l.radius.toFloat(),f.kick.toFloat())
        shader.setFloatUniform("uState",f.power.toFloat(),f.glow.toFloat())
        shader.setFloatUniform("uTime",f.time.toFloat())
        val rings=WindChimeTonesMotion.ringsAt(f.music,f.cut,f.laps)
        if(reduced) for(i in 1..15 step 2) rings[i]*=.4f
        repeat(4) { i -> shader.setFloatUniform("uRings$i",rings[i*4],rings[i*4+1],rings[i*4+2],rings[i*4+3]) }
    }
}

internal class WindChimeTonesArt {
    private val runtime=if(Build.VERSION.SDK_INT>=33) WindChimeTonesRuntime() else null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(c: Canvas,l: WindChimeTonesLayout,f: WindChimeTonesMotion.Frame,density: Float,reduced: Boolean) {
        c.save(); c.scale(density,density)
        paint.alpha=255; paint.style=Paint.Style.FILL
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
            runtime.update(l,f,reduced); paint.shader=runtime.shader
            c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint); paint.shader=null
        } else {
            c.drawColor(0xff2a2a2b.toInt())
            paint.color=0xff1a1a1b.toInt()
            c.drawCircle(l.cx.toFloat(),l.cy.toFloat(),l.radius.toFloat(),paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=1f; paint.color=0xff555555.toInt()
            c.drawCircle(l.cx.toFloat(),l.cy.toFloat(),(l.radius-.5).toFloat(),paint)
        }
        c.restore()
    }
}

internal object WindChimeTonesIcons {
    data class Mark(val path: Path,val stroke: Float=0f,val color: Int=0xffffffff.toInt())
    data class Icon(val w: Float,val h: Float,val marks: List<Mark>)
    private fun path(s: String)=PathParser.createPathFromPathData(s)!!
    private fun filled(s: String)=Mark(path(s))
    private fun stroked(s: String,w: Float)=Mark(path(s),w)
    private fun roundRect(x: Float,y: Float,w: Float,h: Float,r: Float)=Path().apply { addRoundRect(x,y,x+w,y+h,r,r,Path.Direction.CW) }
    val back=Icon(10f,16f,listOf(stroked("M8 1.5 L1.8 8 L8 14.5",1.9f)))
    val more=Icon(4f,16f,listOf(3f,13f).map { y -> Mark(Path().apply { addCircle(2f,y,1.8f,Path.Direction.CW) }) })
    val power=Icon(22f,22f,listOf(stroked("M11 2.5 V10.5 M6.2 5.3 A7.5 7.5 0 1 0 15.8 5.3",2f)))
    val airPlay=Icon(22f,20f,listOf(Mark(roundRect(1.5f,1.5f,19f,13f,2f),1.6f),filled("M11 11 L16 18.5 H6 Z")))
    val eq=Icon(24f,20f,(0..5).map { i -> val x=2+i*4; val h=intArrayOf(3,6,9,5,7,2)[i]; stroked("M$x ${10-h} V${10+h}",1.8f) })
    val pair=Icon(20f,20f,listOf(stroked("M8.5 11.5 L11.5 8.5 M7 8 L5 10 A3.5 3.5 0 0 0 10 15 L12 13 M13 12 L15 10 A3.5 3.5 0 0 0 10 5 L8 7",1.7f)))
    val speaker=Icon(30f,30f,listOf(Mark(path("M4 11 H9 L16 5 V25 L9 19 H4 Z"),color=0xffc9c9cc.toInt()),Mark(path("M20 10 A6 6 0 0 1 20 20 M23 7 A10 10 0 0 1 23 23"),1.8f,0xffc9c9cc.toInt())))
    val previous=Icon(30f,22f,listOf(filled("M14 2 L3 11 L14 20 Z M27 2 L16 11 L27 20 Z")))
    val next=Icon(30f,22f,listOf(filled("M3 2 L14 11 L3 20 Z M16 2 L27 11 L16 20 Z")))
    val play=Icon(24f,30f,listOf(filled("M3 2 L22 15 L3 28 Z")))
    val pause=Icon(24f,28f,listOf(Mark(roundRect(3f,2f,6f,24f,1.5f)),Mark(roundRect(15f,2f,6f,24f,1.5f))))
    fun draw(c: Canvas,icon: Icon,paint: Paint,tint: Int?) {
        for(mark in icon.marks) {
            paint.shader=null; paint.color=tint?:mark.color; paint.alpha=255
            paint.style=if(mark.stroke>0) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth=mark.stroke; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
            c.drawPath(mark.path,paint)
        }
    }
}
