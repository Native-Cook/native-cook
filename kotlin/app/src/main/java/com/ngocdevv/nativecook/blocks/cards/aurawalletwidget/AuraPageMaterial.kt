package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

// The page shader is configured once for each layout and never reads the drive.
@RequiresApi(33)
internal class AuraPageMaterial(cardX: Float, cardY: Float, cardWidth: Float, private val density: Float) {
    private val shader = RuntimeShader(auraPageAgsl).apply {
        auraLightUniforms(this)
        setFloatUniform("origin", cardX, cardY)
        setFloatUniform("cardWidth", cardWidth)
        setFloatUniform("pixelRatio", density)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@AuraPageMaterial.shader }

    fun draw(canvas: Canvas, width: Float, height: Float) {
        canvas.save()
        canvas.scale(density, density)
        canvas.drawRect(0f, 0f, width / density, height / density, paint)
        canvas.restore()
    }
}
