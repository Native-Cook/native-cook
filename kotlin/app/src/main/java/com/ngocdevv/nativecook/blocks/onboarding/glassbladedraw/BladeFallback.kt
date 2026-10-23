package com.ngocdevv.nativecook.blocks.onboarding.glassbladedraw

import android.graphics.*
import kotlin.math.*

/** API 26–32: a still, projected glass fan with native gradients and bevels. */
internal class BladeFallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    fun draw(canvas: Canvas, l: BladeLayout, frame: BladeFrame) {
        canvas.drawColor(Color.BLACK)
        val still = frame.copy(pos = floor(frame.pos + .5), intro = 1.0, still = 1.0)
        val uniforms = BladeModel.uniforms(l, still, 0.0, 1.0)
        val centres = uniforms.getValue("uCentre")
        val axesX = uniforms.getValue("uAxisX")
        val axesY = uniforms.getValue("uAxisY")
        val cards = uniforms.getValue("uCard")
        val colours = uniforms.getValue("uColour")
        val eye = uniforms.getValue("uEye")
        val halfW = l.cardW.toFloat() / 2
        val halfH = l.cardH.toFloat() / 2
        val source = floatArrayOf(-halfW, -halfH, halfW, -halfH, halfW, halfH, -halfW, halfH)
        for (i in 10 downTo 0) {
            if (cards[i * 4 + 2] < .001f) continue
            val destination = FloatArray(8)
            for (corner in 0..3) {
                val x = source[corner * 2]
                val y = source[corner * 2 + 1]
                val perspective = eye[2] / (eye[2] - centres[i * 3 + 2] - axesX[i * 3 + 2] * x)
                destination[corner * 2] = eye[0] + (centres[i * 3] + axesX[i * 3] * x + axesY[i * 3] * y - eye[0]) * perspective
                destination[corner * 2 + 1] = eye[1] + (centres[i * 3 + 1] + axesX[i * 3 + 1] * x + axesY[i * 3 + 1] * y - eye[1]) * perspective
            }
            matrix.setPolyToPoly(source, 0, destination, 0, 4)
            canvas.save()
            canvas.concat(matrix)
            val light = cards[i * 4 + 1].coerceIn(0f, 1f)
            val hue = Color.rgb(
                .12f + (colours[i * 3] - .12f) * light,
                .17f + (colours[i * 3 + 1] - .17f) * light,
                .12f + (colours[i * 3 + 2] - .12f) * light,
            )
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, -halfH, 0f, halfH,
                intArrayOf(0xFF323632.toInt(), 0xFF272C26.toInt(), if (light > .01f) hue else 0xFF294031.toInt()),
                floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            paint.alpha = (cards[i * 4 + 2] * 220).toInt()
            canvas.drawRoundRect(-halfW, -halfH, halfW, halfH, l.cornerR.toFloat(), l.cornerR.toFloat(), paint)
            paint.shader = null
            paint.color = 0xFFA2A59B.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f
            canvas.drawRoundRect(-halfW, -halfH, halfW, halfH, l.cornerR.toFloat(), l.cornerR.toFloat(), paint)
            paint.style = Paint.Style.FILL
            if (light > .01f) {
                paint.color = 0xFF172007.toInt()
                paint.textSize = 32f
                paint.typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                canvas.drawText((cards[i * 4] * light).toInt().toString(), -halfW + 8, halfH - 10, paint)
            }
            canvas.restore()
        }
        paint.shader = LinearGradient(0f, l.ctaY.toFloat(), 0f, (l.ctaY + 60).toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, l.ctaY.toFloat(), l.width.toFloat(), l.height.toFloat(), paint)
        paint.shader = null
        paint.color = 0xFFDCFF1F.toInt()
        paint.alpha = 255
        canvas.drawRoundRect(l.ctaX.toFloat(), l.ctaY.toFloat(), (l.ctaX + l.ctaW).toFloat(),
            (l.ctaY + l.ctaH).toFloat(), 16f, 16f, paint)
    }
}
