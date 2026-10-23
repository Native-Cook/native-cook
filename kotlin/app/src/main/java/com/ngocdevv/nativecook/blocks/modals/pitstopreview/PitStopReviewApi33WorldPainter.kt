package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class PitStopReviewApi33WorldPainter {
    private val shader = RuntimeShader(PitStopReviewWorldShaderSource.AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@PitStopReviewApi33WorldPainter.shader }

    fun draw(canvas: Canvas, scene: Double) {
        shader.setFloatUniform("res", 402f, 220f)
        shader.setFloatUniform(
            "w0",
            PitStopReviewMotion.worldX(scene).toFloat(),
            PitStopReviewMotion.worldBlur(scene).toFloat(),
            PitStopReviewMotion.haze(scene).toFloat(),
            PitStopReviewMotion.speedLines(scene).toFloat(),
        )
        shader.setFloatUniform(
            "w1",
            (PitStopReviewTheme.Hero.carX + PitStopReviewMotion.carX(scene) + 4.0).toFloat(),
            scene.toFloat(),
            1f,
            0f,
        )
        paint.alpha = 255
        canvas.drawRect(0f, 0f, PitStopReviewTheme.Hero.width, PitStopReviewTheme.Hero.height, paint)
    }
}
