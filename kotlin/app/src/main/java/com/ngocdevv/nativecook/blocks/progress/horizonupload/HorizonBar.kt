// Progress tube: exact AGSL on API 33+, a plain track/fill on older Android.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.*
import android.os.Build
import androidx.core.graphics.withTranslation
import androidx.annotation.RequiresApi

internal class HorizonBar(private val g: HorizonGeometry) {
    private val shader = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(horizonBarAgsl) else null
    private val uniforms = if (Build.VERSION.SDK_INT >= 33) HorizonShaderUniforms() else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            val u = requireNotNull(uniforms)
            u.set(shader, "uBarWidth", g.barWidth); u.set(shader, "uScale", g.density)
            u.set(shader, "uThickness", 4f); u.set(shader, "uBleed", 18f)
            u.set(shader, "uTrackRGB", 44 / 255f, 56 / 255f, 72 / 255f)
            u.set(shader, "uFillRGB", 253 / 255f, 252 / 255f, 254 / 255f)
            u.set(shader, "uBloomRGB", 140 / 255f, 166 / 255f, 237 / 255f)
            u.set(shader, "uDeepRGB", 49 / 255f, 72 / 255f, 146 / 255f)
        }
    }
    fun draw(canvas: Canvas, state: HorizonState) {
        val progress = if (state.label == HorizonPhase.Idle || state.label == HorizonPhase.Stopped) 0f else state.progress
        if (Build.VERSION.SDK_INT >= 33 && shader != null) drawShader(canvas, state, progress)
        else {
            paint.color = 0xFF182131.toInt(); canvas.drawRoundRect(36f, 200f, g.cardWidth - 36, 204f, 2f, 2f, paint)
            paint.color = 0xFFFDFCFE.toInt(); paint.alpha = ((1 - .45f * state.paused.value) * 255).toInt()
            canvas.drawRoundRect(36f, 200f, 36 + g.barWidth * progress, 204f, 2f, 2f, paint)
        }
    }
    @RequiresApi(33)
    private fun drawShader(canvas: Canvas, state: HorizonState, progress: Float) {
        val shader = requireNotNull(shader); val u = requireNotNull(uniforms)
        u.set(shader, "uProgress", progress); u.set(shader, "uPower", state.power.value)
        u.set(shader, "uPulse", state.pulse); u.set(shader, "uPaused", state.paused.value)
        u.set(shader, "uCalm", state.calm.value); u.set(shader, "uShine", state.shine.value)
        u.set(shader, "uMotion", if (state.reduced) 0f else 1f)
        u.set(shader, "uDone", if (state.label == HorizonPhase.Done) 1f else 0f)
        paint.shader = shader; paint.alpha = 255
        canvas.withTranslation(18f, 182f) {
            drawRect(0f, 0f, g.barWidth + 36, 40f, paint)
        }
    }
}
