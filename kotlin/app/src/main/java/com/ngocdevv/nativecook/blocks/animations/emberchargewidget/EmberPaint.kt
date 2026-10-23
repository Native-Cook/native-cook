package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.Paint
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberTone

/** One reusable native paint per drawing subject; reset prevents layer state leaking. */
internal class EmberPaint {
    val value = Paint(Paint.ANTI_ALIAS_FLAG)
    fun reset(color: Int = emberTone.ink, alpha: Float = 1f, shader: Shader? = null, stroke: Float = 0f) {
        value.reset()
        value.isAntiAlias = true
        value.color = color
        value.alpha =
            (255 * alpha.coerceIn(0f, 1f)).toInt()
        value.shader = shader
        if (stroke > 0) {
            value.style = Paint.Style.STROKE
            value.strokeWidth = stroke
            value.strokeCap =
                Paint.Cap.ROUND
            value.strokeJoin = Paint.Join.ROUND
        }
    }
}
