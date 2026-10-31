package com.ngocdevv.nativecook.blocks.cards.glassorbshelf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassOrbShelfOrb {
    private val shader=RuntimeShader(GlassOrbShelfShader.SOURCE)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader=this@GlassOrbShelfOrb.shader }
    fun draw(canvas: Canvas,l: GlassOrbShelfLayout,density: Float,driver: GlassOrbShelfDriver) {
        val o=driver.offset
        val frac=o-GlassOrbShelfMotion.rounded(o)
        val radius=l.orbR*(1-0.03*sin(min(1.0,abs(frac)*2)*PI*0.5))
        shader.setFloatUniform("uCentre",l.orbX*density,l.orbY*density)
        shader.setFloatUniform("uRadius",(radius*density).toFloat())
        // AGSL works in layer pixels; multiplying all point coordinates by density makes the
        // original radius * pixelRatio antialiasing denominator identical with pixelRatio = 1.
        shader.setFloatUniform("uPixelRatio",1f)
        shader.setFloatUniform("uScene",(if (driver.reduced) GlassOrbShelfMotion.rounded(max(0.0,o)) else o.coerceIn(0.0,3.0)).toFloat())
        shader.setFloatUniform("uSpin",driver.spin.toFloat())
        shader.setFloatUniform("uTilt",driver.lean.toFloat(),(0.12+driver.lean*0.3).toFloat())
        shader.setFloatUniform("uPop",driver.pop.toFloat())
        shader.setFloatUniform("uTime",(driver.clock%GlassOrbShelfMotion.LOOP).toFloat())
        shader.setFloatUniform("uPage",13/255f,14/255f,17/255f)
        canvas.drawRect((l.orbX-l.orbR*1.3f)*density,(l.orbY-l.orbR*1.25f)*density,
            (l.orbX+l.orbR*1.3f)*density,(l.orbY+l.orbR*1.35f)*density,paint)
    }
}
