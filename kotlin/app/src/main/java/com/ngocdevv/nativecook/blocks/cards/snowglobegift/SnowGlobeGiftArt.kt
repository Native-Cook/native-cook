package com.ngocdevv.nativecook.blocks.cards.snowglobegift

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class SnowGlobeGiftArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val runtime = if (Build.VERSION.SDK_INT >= 33) SnowGlobeGiftRuntime() else null
    private var layout: SnowGlobeGiftLayout? = null
    private var page: Shader? = null
    private var glass: Shader? = null
    private var gold: Shader? = null
    private val sphere = Path()
    private val flakes = List(56) { i ->
        fun hash(n: Double): Double { val x = sin(n*12.9898)*43758.5453; return x-floor(x) }
        doubleArrayOf(hash(i.toDouble()),hash(i+7.1),hash(i+3.3),hash(i+9.7))
    }
    fun draw(canvas: Canvas,l: SnowGlobeGiftLayout,f: SnowGlobeGiftMotion.Frame,pixelRatio: Float) {
        if (runtime != null && Build.VERSION.SDK_INT >= 33) {
            paint.shader = runtime.update(l,f,pixelRatio)
            canvas.drawRect(0f,0f,l.w,l.h,paint)
            paint.shader = null
        } else fallback(canvas,l,f)
    }
    private fun fallback(c: Canvas,l: SnowGlobeGiftLayout,f: SnowGlobeGiftMotion.Frame) {
        if (layout != l) {
            layout = l
            page = LinearGradient(0f,0f,0f,l.h,intArrayOf(0xFFF8F1EF.toInt(),0xFFF1E8E6.toInt()),null,Shader.TileMode.CLAMP)
            glass = RadialGradient(l.heroX-l.heroS*0.3f,l.heroY-l.heroS*0.3f,l.heroS*1.3f,intArrayOf(0xFFEDF0F3.toInt(),0xFFD8E1EB.toInt(),0xFF8F9CAA.toInt()),null,Shader.TileMode.CLAMP)
            gold = LinearGradient(l.heroX-l.heroS,0f,l.heroX+l.heroS,0f,intArrayOf(0xFF9A763E.toInt(),0xFFF2DBA5.toInt(),0xFFB28D54.toInt(),0xFFE5C385.toInt()),null,Shader.TileMode.CLAMP)
            sphere.reset(); sphere.addCircle(l.heroX,l.heroY,l.heroS,Path.Direction.CW)
        }
        paint.shader = page; c.drawRect(0f,0f,l.w,l.h,paint); paint.shader = null
        val s = l.heroS
        c.save(); c.rotate((-f.wobble*180/PI).toFloat(),l.heroX,l.heroY+s*1.3f)
        paint.shader = gold
        c.drawRoundRect(l.heroX-s*0.8f,l.heroY+s*0.9f,l.heroX+s*0.8f,l.heroY+s*1.34f,4f,4f,paint)
        paint.shader = glass; c.drawCircle(l.heroX,l.heroY,s,paint); paint.shader = null
        c.save(); c.clipPath(sphere)
        paint.color = 0xFFF7F7FE.toInt(); c.drawOval(l.heroX-s,l.heroY+s*0.5f,l.heroX+s,l.heroY+s*0.95f,paint)
        repeat(56) { i ->
            val flake = flakes[i]
            val a = flake[0]; val b = flake[1]; val z = flake[2]
            val angle = a*2*PI+f.spin*(0.6+0.8*z)
            val k0 = (f.swirl*1.3-z*0.3).coerceIn(0.0,1.0); val k = k0*k0*(3-2*k0)
            val x = (a-0.5)*1.2*(1-k)+cos(angle)*(0.15+0.62*b)*k
            val y = (-0.6+0.06*b)*(1-k)+(-0.5+1.15*z+0.12*sin(f.spin*1.7+i))*k
            c.drawCircle(l.heroX+(x*s).toFloat(),l.heroY-(y*s).toFloat(),s*(0.018+0.016*flake[3]).toFloat(),paint)
        }
        paint.color = 0xFFE1B567.toInt()
        c.drawRoundRect(l.heroX-s*0.24f,l.heroY+s*0.16f,l.heroX+s*0.24f,l.heroY+s*0.56f,3f,3f,paint)
        paint.color = 0xFFD12937.toInt(); c.drawRect(l.heroX-s*0.04f,l.heroY+s*0.16f,l.heroX+s*0.04f,l.heroY+s*0.56f,paint)
        c.save(); c.translate(0f,(-f.lid*0.32*s).toFloat()); c.rotate((f.lid*0.5*180/PI).toFloat(),l.heroX+s*0.26f,l.heroY+s*0.13f)
        paint.color = 0xFFF0C573.toInt(); c.drawRoundRect(l.heroX-s*0.27f,l.heroY+s*0.08f,l.heroX+s*0.27f,l.heroY+s*0.18f,2f,2f,paint)
        paint.color = 0xFFD12937.toInt(); c.drawRect(l.heroX-s*0.04f,l.heroY+s*0.08f,l.heroX+s*0.04f,l.heroY+s*0.18f,paint)
        c.restore(); c.restore()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = 0xB3FFFFFF.toInt(); c.drawCircle(l.heroX,l.heroY,s-1f,paint); paint.style = Paint.Style.FILL
        c.restore()
    }
}

@RequiresApi(33)
internal class SnowGlobeGiftRuntime {
    private val shader = android.graphics.RuntimeShader(SnowGlobeGiftShader.SOURCE)
    fun update(l: SnowGlobeGiftLayout,f: SnowGlobeGiftMotion.Frame,pixelRatio: Float): Shader {
        shader.setFloatUniform("uRes",l.w,l.h); shader.setFloatUniform("uC",l.heroX,l.heroY)
        shader.setFloatUniform("uS",l.heroS); shader.setFloatUniform("uWobble",f.wobble.toFloat())
        shader.setFloatUniform("uSwirl",f.swirl.toFloat()); shader.setFloatUniform("uSpin",f.spin.toFloat())
        shader.setFloatUniform("uLid",f.lid.toFloat()); shader.setFloatUniform("uGlow",f.glow.toFloat())
        shader.setFloatUniform("uPx",1f/pixelRatio)
        return shader
    }
}
