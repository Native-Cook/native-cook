package com.ngocdevv.nativecook.blocks.cards.sunrisecocktailpour

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class SunriseCocktailPourArt {
    private val runtime=if(Build.VERSION.SDK_INT>=33) SunriseCocktailPourRuntime() else null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    fun draw(c: Canvas,l: SunriseCocktailPourLayout,f: SunriseCocktailPourMotion.Frame,density: Float) {
        if(Build.VERSION.SDK_INT>=33 && runtime!=null) {
            paint.shader=runtime.update(l,f,density)
            c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint)
            paint.shader=null
        } else fallback(c,l,f)
    }
    private fun fallback(c: Canvas,l: SunriseCocktailPourLayout,f: SunriseCocktailPourMotion.Frame) {
        c.drawColor(Color.rgb(16,13,11))
        val s=l.heroS.toFloat(); val x=(l.heroX+f.shift*l.heroS).toFloat(); val y=l.heroY.toFloat()
        paint.color=Color.rgb(28,21,16); c.drawRect(0f,y+s*1.24f,l.w.toFloat(),l.h.toFloat(),paint)
        c.save(); c.translate(x,y)
        path.reset(); path.addRoundRect(-s*0.54f,-s*1.05f,s*0.54f,s*0.95f,s*0.04f,s*0.04f,Path.Direction.CW)
        c.save(); c.clipPath(path)
        val orange=Color.rgb(255,173,56); val red=Color.rgb(217,31,51); val coral=Color.rgb(255,143,92)
        fun blend(a: Int,b: Int,t: Double)=Color.rgb((Color.red(a)+(Color.red(b)-Color.red(a))*t).toInt(),(Color.green(a)+(Color.green(b)-Color.green(a))*t).toInt(),(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t).toInt())
        paint.shader=LinearGradient(0f,(-f.level*s).toFloat(),0f,s*0.95f,intArrayOf(blend(orange,coral,f.mix),blend(blend(orange,red,f.red),coral,f.mix)),null,Shader.TileMode.CLAMP)
        c.drawRect(-s*0.54f,(-f.level*s).toFloat(),s*0.54f,s*0.95f,paint); paint.shader=null
        paint.color=0x88F5EEE4.toInt()
        repeat(3) { i->
            val a=f.swirl*(0.8+0.3*i)+i*2.1
            val ix=(cos(a)*0.24*s).toFloat(); val iy=(-f.level*s+0.12*s+0.05*i*s).toFloat()
            c.save(); c.rotate((a*180/PI).toFloat(),ix,iy)
            c.drawRoundRect(ix-s*0.15f,iy-s*0.15f,ix+s*0.15f,iy+s*0.15f,s*0.04f,s*0.04f,paint);c.restore()
        }
        c.restore()
        paint.style=Paint.Style.STROKE; paint.strokeWidth=s*0.06f;paint.color=0x88F5EEE4.toInt()
        c.drawRoundRect(-s*0.62f,-s*1.1f,s*0.62f,s*1.2f,s*0.08f,s*0.08f,paint)
        c.drawOval(-s*0.62f,-s*1.17f,s*0.62f,-s*1.03f,paint)
        paint.style=Paint.Style.FILL
        if(f.stream>0) { paint.color=red; c.drawRect(s*0.068f,-s*2.6f,s*0.092f,(-f.level*s).toFloat(),paint) }
        c.restore()
    }
}

@RequiresApi(33)
internal class SunriseCocktailPourRuntime {
    private val shader=RuntimeShader(SunriseCocktailPourShader.source)
    fun update(l: SunriseCocktailPourLayout,f: SunriseCocktailPourMotion.Frame,density: Float): Shader {
        shader.setFloatUniform("uRes",l.w.toFloat(),l.h.toFloat())
        shader.setFloatUniform("uC",l.heroX.toFloat(),l.heroY.toFloat())
        shader.setFloatUniform("uS",l.heroS.toFloat());shader.setFloatUniform("uPx",1/density)
        shader.setFloatUniform("uShift",f.shift.toFloat());shader.setFloatUniform("uLevel",f.level.toFloat())
        shader.setFloatUniform("uRed",f.red.toFloat());shader.setFloatUniform("uRise",f.rise.toFloat())
        shader.setFloatUniform("uMix",f.mix.toFloat());shader.setFloatUniform("uStream",f.stream.toFloat())
        shader.setFloatUniform("uSwirl",f.swirl.toFloat());shader.setFloatUniform("uPhase",f.phase.toFloat())
        return shader
    }
}
