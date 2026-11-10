package com.ngocdevv.nativecook.blocks.modals.champagnersvp

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.*

internal class ChampagneRsvpArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val runtime = if (Build.VERSION.SDK_INT >= 33) ChampagneRsvpRuntime() else null
    private val bowl = Path().apply {
        moveTo(-0.06f, -1.25f)
        cubicTo(-0.31f, -1.45f, -0.49f, -2.18f, -0.25f, -2.65f)
        lineTo(0.25f, -2.65f)
        cubicTo(0.49f, -2.18f, 0.31f, -1.45f, 0.06f, -1.25f)
        close()
    }
    private val wine = Path().apply {
        moveTo(-0.30f, -2.05f); lineTo(0.30f, -2.05f)
        cubicTo(0.25f, -1.6f, 0.12f, -1.37f, 0f, -1.37f)
        cubicTo(-0.12f, -1.37f, -0.25f, -1.6f, -0.30f, -2.05f)
        close()
    }
    fun draw(canvas: Canvas, l: ChampagneRsvpLayout, f: ChampagneRsvpMotion.Frame, density: Float) {
        if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
            paint.shader = runtime.shader(l, f, density)
            paint.alpha = 255
            canvas.drawRect(0f, 0f, l.w, l.h, paint)
            paint.shader = null
        } else fallback(canvas, l, f)
    }
    private fun fallback(canvas: Canvas, l: ChampagneRsvpLayout, f: ChampagneRsvpMotion.Frame) {
        paint.color = Color.rgb(250, 243, 241)
        canvas.drawRect(0f, 0f, l.w, l.h, paint)
        for (k in 0..1) {
            canvas.save()
            canvas.translate(if (k == 0) l.left else l.right, l.feetY)
            canvas.rotate((f.lean * (if (k == 0) 1 else -1) * 180 / PI).toFloat())
            canvas.scale(l.heroS, l.heroS)
            paint.style = Paint.Style.FILL
            paint.color = 0x254F3730
            canvas.drawOval(-0.45f, -0.03f, 0.45f, 0.09f, paint)
            paint.color = 0x75FFFFFF
            canvas.drawPath(bowl, paint)
            paint.color = 0xB2E8BC63.toInt()
            canvas.drawPath(wine, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.018f
            paint.color = 0xBBFFFFFF.toInt()
            canvas.drawPath(bowl, paint)
            canvas.drawOval(-0.25f, -2.68f, 0.25f, -2.62f, paint)
            canvas.drawLine(0f, -1.25f, 0f, -0.05f, paint)
            canvas.drawOval(-0.42f, -0.07f, 0.42f, 0.02f, paint)
            for (i in 0..8) {
                val rise = ((i / 9.0 + f.phase / 2) % 1).toFloat()
                canvas.drawCircle(0.08f * sin(i * 2.4f), -1.4f - rise * 0.6f, 0.012f, paint)
            }
            paint.style = Paint.Style.FILL
            canvas.restore()
        }
        if (f.clink > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.argb(((1 - f.clink) * 160).toInt(), 255, 242, 216)
            canvas.drawCircle(l.w / 2, l.feetY - cos(f.lean).toFloat() * 2.62f * l.heroS,
                l.heroS * (0.05 + 0.9 * f.clink).toFloat(), paint)
            paint.style = Paint.Style.FILL
        }
    }
}

@RequiresApi(33)
private class ChampagneRsvpRuntime {
    private val effect = RuntimeShader(ChampagneRsvpShader.source)
    fun shader(l: ChampagneRsvpLayout, f: ChampagneRsvpMotion.Frame, density: Float): RuntimeShader {
        effect.setFloatUniform("uRes", l.w, l.h)
        effect.setFloatUniform("uL", l.left, l.feetY)
        effect.setFloatUniform("uR", l.right, l.feetY)
        effect.setFloatUniform("uS", l.heroS)
        effect.setFloatUniform("uRot", f.lean.toFloat(), -f.lean.toFloat())
        effect.setFloatUniform("uClink", f.clink.toFloat())
        effect.setFloatUniform("uClinkAt", l.w / 2, l.feetY - cos(f.lean).toFloat() * 2.62f * l.heroS)
        effect.setFloatUniform("uBurst", f.burst.toFloat())
        effect.setFloatUniform("uPhase", f.phase.toFloat())
        effect.setFloatUniform("uPx", 1 / density)
        return effect
    }
}
