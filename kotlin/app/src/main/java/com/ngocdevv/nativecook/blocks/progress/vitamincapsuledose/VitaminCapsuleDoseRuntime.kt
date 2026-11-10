package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import kotlin.math.PI

@RequiresApi(33)
internal class VitaminCapsuleDoseRuntime {
    private val shader = RuntimeShader(VitaminCapsuleDoseShader.source)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas: Canvas, l: VitaminCapsuleDoseLayout, f: VitaminCapsuleDoseMotion.Frame) {
        shader.setFloatUniform("uView", l.w.toFloat(), l.h.toFloat(), f.t.toFloat(), 0f)
        shader.setFloatUniform("uHero", l.cx.toFloat(), (l.cy + f.bob).toFloat(), l.unit.toFloat(), (l.y(294.0) - l.cy - f.bob).toFloat())
        shader.setFloatUniform("uShape", f.a.toFloat(), f.b.toFloat(), f.k.toFloat(), f.reform.toFloat())
        shader.setFloatUniform("uOpen", f.sep.toFloat(), f.twist.toFloat(), f.hidden.toFloat(), f.tilt.toFloat())
        shader.setFloatUniform("uFx", f.take.toFloat(), f.hold.toFloat(), (f.yaw + 2 * PI * f.k).toFloat(), f.pitch.toFloat())
        paint.color = android.graphics.Color.WHITE; paint.alpha = 255; paint.shader = shader
        canvas.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), paint)
    }
}
