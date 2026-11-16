package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceMotion as M
import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceTheme as Theme
import kotlin.math.cos

/**
 * Below API 33 there is no particle shader: the ribbon is drawn as its centre line in the gold, green and cyan ramp (the travelling sine, the flick's
 * bump, the thread) with a soft glow, a bright edge-on core where the sheet is thin, and the twisting sheet's two edges as faint lines whose distance
 * follows `Wd cos(phi)`. No sparks, out-of-focus dust or beads, so it reads as the same ribbon but not as the same material.
 */
internal class ParticleRibbonVoiceFallback {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val glow = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)

    private fun twist(u: Double, f: PrvFrame) = M.twistBase(u, f)

    fun draw(c: Canvas, w: Float, s: Float, f: PrvFrame) {
        val ramp = intArrayOf(Theme.gold, Theme.green, Theme.cyan)
        val level = f.sheet[1].toFloat().coerceIn(0f, 1f)
        val strokeShader = LinearGradient(0f, 0f, w, 0f, ramp, floatArrayOf(0.3f, 0.5f, 0.7f), Shader.TileMode.CLAMP)
        fun line(offset: (Double) -> Double): Path {
            path.rewind()
            var x = 0.0
            while (x <= 402.0) {
                val y = (M.lineY(x, f) + offset(x) - Theme.STRIP_TOP) * s
                if (x == 0.0) path.moveTo((x * s).toFloat(), y.toFloat()) else path.lineTo((x * s).toFloat(), y.toFloat())
                x += 3.0
            }
            return path
        }
        val wd = f.sheet[0]
        stroke.shader = strokeShader
        // the glow
        stroke.maskFilter = glow
        stroke.alpha = (255 * (0.35 + 0.3 * level)).toInt()
        stroke.strokeWidth = (10 * s)
        c.drawPath(line { 0.0 }, stroke)
        stroke.maskFilter = null
        // the twisting sheet's two edges
        stroke.alpha = (255 * 0.45).toInt()
        stroke.strokeWidth = 1.2f * s
        c.drawPath(line { x -> wd * cos(twist(x, f)) }, stroke)
        stroke.alpha = (255 * 0.2).toInt()
        c.drawPath(line { x -> -wd * cos(twist(x, f)) * 0.4 }, stroke)
        // the bright core, brighter where the sheet is edge-on
        stroke.shader = null
        stroke.color = Color.argb((255 * (0.55 + 0.3 * level)).toInt(), 255, 255, 255)
        stroke.strokeWidth = 1.4f * s
        c.drawPath(line { 0.0 }, stroke)
    }
}
