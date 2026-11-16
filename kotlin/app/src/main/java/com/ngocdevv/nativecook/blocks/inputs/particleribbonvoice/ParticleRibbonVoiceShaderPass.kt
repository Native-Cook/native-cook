package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceTheme as Theme

/** The ribbon pass (API 33+): RN `ribbonUniforms`, drawn over the strip (the canvas is translated to the strip's top). */
@RequiresApi(33)
internal class ParticleRibbonVoiceShaderPass {
    private val shader = RuntimeShader(ParticleRibbonVoiceSource)
    private val paint = Paint()
    private val ramp = arrayOf(Theme.rgbOf(Theme.gold), Theme.rgbOf(Theme.green), Theme.rgbOf(Theme.cyan))
    private val bg = Theme.rgbOf(Theme.page)

    private fun f4(name: String, a: DoubleArray, o: Int = 0) =
        shader.setFloatUniform(name, a[o].toFloat(), a[o + 1].toFloat(), a[o + 2].toFloat(), a[o + 3].toFloat())

    private fun arr(a: DoubleArray) = FloatArray(a.size) { a[it].toFloat() }

    fun draw(c: Canvas, w: Float, h: Float, s: Float, f: PrvFrame) {
        shader.setFloatUniform("uRes", w, h)
        shader.setFloatUniform("uS", s)
        shader.setFloatUniform("uOY", Theme.STRIP_TOP.toFloat())
        shader.setFloatUniform("uPh", f.phase.toFloat())
        f4("uLine", f.line)
        shader.setFloatUniform("uSheet", f.sheet[0].toFloat(), f.sheet[1].toFloat(), f.sheet[2].toFloat(), f.sheet[3].toFloat())
        f4("uTwA", f.twA)
        f4("uTwB", f.twB)
        f4("uThink", f.think)
        shader.setFloatUniform("uPul", arr(f.pul))
        shader.setFloatUniform("uWin", arr(f.win))
        shader.setFloatUniform("uWinB", arr(f.winB))
        shader.setFloatUniform("uBump", arr(f.bump))
        shader.setFloatUniform("uOpt", f.opt[0].toFloat(), f.opt[1].toFloat(), 0f, f.opt[3].toFloat())
        shader.setFloatUniform("uExtra", f.extra[0].toFloat(), bg[0], bg[1], bg[2])
        shader.setFloatUniform("uC0", ramp[0][0], ramp[0][1], ramp[0][2])
        shader.setFloatUniform("uC1", ramp[1][0], ramp[1][1], ramp[1][2])
        shader.setFloatUniform("uC2", ramp[2][0], ramp[2][1], ramp[2][2])
        shader.setFloatUniform("uDbg", 0f)
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}
