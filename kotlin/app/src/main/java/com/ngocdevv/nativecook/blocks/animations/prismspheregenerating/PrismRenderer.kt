package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import android.graphics.*
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.*

internal class PrismRenderer {
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu() else null
    private val fallback = if (gpu == null) PrismFallback() else null
    private val motes = PrismMotes()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = PrismText()
    private val enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    fun draw(canvas: Canvas, width: Float, height: Float, motion: PrismMotion, elapsed: Double) {
        canvas.drawColor(Color.BLACK)
        val k = min(width / 402f, height / 874f)
        val left = (width - 402 * k) / 2
        val top = (height - 874 * k) / 2
        val entrance = if (motion.reduced) 1.0 else enter.transform((elapsed / 0.8).coerceIn(0.0, 1.0).toFloat()).toDouble()
        val scale = motion.values.scale * (0.94 + 0.06 * entrance)
        val opacity = (elapsed / 0.6).coerceIn(0.0, 1.0)
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(k, k)
        canvas.saveLayerAlpha(-left / k, -top / k, (width - left) / k, (height - top) / k, (255 * opacity).toInt())
        if (gpu != null) gpu.backdrop(canvas, paint, width / k, height / k, left / k, top / k, motion, scale)
        else fallback?.backdrop(canvas)
        motes.draw(canvas, true, motion, scale)
        if (gpu != null) gpu.sphere(canvas, paint, width / k, height / k, motion, scale)
        else fallback?.sphere(canvas, motion.values.flash)
        motes.draw(canvas, false, motion, scale)
        canvas.restore()

        text.draw(canvas, k, motion, elapsed, opacity)
        canvas.restore()
    }

    @android.annotation.TargetApi(33)
    private class Gpu {
        private val backdrop = RuntimeShader(PrismShaders.BACKDROP)
        private val sphere = RuntimeShader(PrismShaders.SPHERE)
        private val matrix = FloatArray(9)

        private fun uniforms(shader: RuntimeShader, width: Float, height: Float, motion: PrismMotion, scale: Double) {
            shader.setFloatUniform("uSize", width, height)
            shader.setFloatUniform("uCentre", 201f, 472f)
            shader.setFloatUniform("uRadius", 124f)
            shader.setFloatUniform("uScale", scale.toFloat())
            shader.setFloatUniform("uClock", if (motion.reduced) 0f else motion.clock.toFloat())
            shader.setFloatUniform("uEnergy", motion.values.energy.toFloat())
            shader.setFloatUniform("uCharge", motion.values.charge.toFloat())
            shader.setFloatUniform("uFlash", motion.values.flash.toFloat())
        }

        fun backdrop(canvas: Canvas, paint: Paint, width: Float, height: Float, left: Float, top: Float, motion: PrismMotion, scale: Double) {
            uniforms(backdrop, width, height, motion, scale)
            backdrop.setFloatUniform("uCentre", 201f + left, 472f + top)
            backdrop.setFloatUniform("uRing", motion.values.ring.toFloat())
            paint.shader = backdrop
            canvas.save()
            canvas.translate(-left, -top)
            canvas.drawRect(0f, 0f, width, height, paint)
            canvas.restore()
            paint.shader = null
        }

        fun sphere(canvas: Canvas, paint: Paint, width: Float, height: Float, motion: PrismMotion, scale: Double) {
            uniforms(sphere, width, height, motion, scale)
            for (i in 0..8) matrix[i] = motion.rotation[i % 3 * 3 + i / 3].toFloat()
            sphere.setFloatUniform("uRot", matrix)
            sphere.setFloatUniform("uFan", motion.values.fan.toFloat())
            sphere.setFloatUniform("uSweep", motion.values.sweep.toFloat())
            sphere.setFloatUniform("uSweepPower", motion.values.sweepPower.toFloat())
            sphere.setFloatUniform("uLight", motion.light)
            paint.shader = sphere
            val extent = 124f * 1.12f * 1.05f
            canvas.drawRect(201 - extent, 472 - extent, 201 + extent, 472 + extent, paint)
            paint.shader = null
        }
    }
}
