package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import kotlin.math.*

/** Distinct instances preserve each recorded Canvas pass's uniforms until GPU submission. */
@RequiresApi(33)
internal class GlassPayMorphShaderPass {
    private val background = RuntimeShader(GlassPayMorphShaderSource)
    private val overlay = RuntimeShader(GlassPayMorphShaderSource)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun uniforms(shader: RuntimeShader, s: GlassPayMorphMotion.Scene, px: Float, layer: Float) {
        shader.setFloatUniform("uPx", px, 1f)
        shader.setFloatUniform("uA", s.a.map { it.toFloat() }.toFloatArray())
        shader.setFloatUniform("uR", s.rd.map { it.toFloat() }.toFloatArray())
        val bothFront = (1 - s.frostA) * (1 - s.frostR)
        shader.setFloatUniform("uLt", s.frostA.toFloat(), s.frostR.toFloat(), s.dock.toFloat(), (18 * bothFront).toFloat())
        val inverse = GlassPayMorphMotion.cardInverse(s)
        shader.setFloatUniform("uH0", inverse[0].toFloat(), inverse[1].toFloat(), inverse[2].toFloat())
        shader.setFloatUniform("uH1", inverse[3].toFloat(), inverse[4].toFloat(), inverse[5].toFloat())
        shader.setFloatUniform("uH2", inverse[6].toFloat(), inverse[7].toFloat(), inverse[8].toFloat())
        shader.setFloatUniform("uCard", (s.w / 2).toFloat(), (s.h / 2).toFloat(), s.r.toFloat(), s.tint.toFloat())
        shader.setFloatUniform("uFx", s.flash.toFloat(), s.bright.toFloat(), s.sheen.toFloat(), s.glint.toFloat())
        val turn = abs(s.turn)
        val edge = if (turn > 35 && turn < 145) sin((turn - 35) / 110 * PI) else 0.0
        shader.setFloatUniform("uFx2", s.crawl.toFloat(), edge.toFloat(), 0.35f, 1f)
        shader.setFloatUniform("uLayer", layer, 0f)
    }

    fun background(c: Canvas, s: GlassPayMorphMotion.Scene, px: Float, top: Float, height: Float) {
        uniforms(background, s, px, 0f)
        paint.shader = background
        paint.alpha = 255
        c.drawRect(0f, top, 402f, top + height, paint)
    }

    fun overlay(c: Canvas, s: GlassPayMorphMotion.Scene, px: Float) {
        var left = Double.POSITIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        fun take(trail: DoubleArray, enabled: Boolean) {
            if (!enabled) return
            for (i in 0 until GlassPayMorphMotion.TRAIL) {
                val r = trail[i * 3 + 2] + 3
                left = min(left, trail[i * 3] - r)
                top = min(top, trail[i * 3 + 1] - r)
                right = max(right, trail[i * 3] + r)
                bottom = max(bottom, trail[i * 3 + 1] + r)
            }
        }
        take(s.a, s.frostA < 0.999)
        take(s.rd, s.frostR < 0.999)
        if (right <= left) return
        uniforms(overlay, s, px, 1f)
        paint.shader = overlay
        paint.alpha = 255
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
    }
}
