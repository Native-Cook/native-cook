package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import android.graphics.*
import kotlin.math.*

/** API 26–32: still faceted illustration. Controls and the reduced-motion reveal remain usable. */
internal class CrystalTrophyFallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(canvas: Canvas, f: CrystalTrophyFrame, s: CrystalTrophyState) {
        canvas.drawColor(Color.rgb(2, 3, 11))
        for (i in 0 until 24) {
            val x = i * f.width / 24
            paint.shader = LinearGradient(x.toFloat(), 0f, (x + f.width / 24).toFloat(), 0f,
                intArrayOf(0xff050a20.toInt(), 0xff1d3683.toInt(), 0xff060c28.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(x.toFloat(), 0f, (x + f.width / 24 + 1).toFloat(), f.height.toFloat(), paint)
        }
        paint.shader = LinearGradient(0f, (f.originY - 40).toFloat(), 0f, (f.originY + 100).toFloat(),
            Color.TRANSPARENT, Color.rgb(2, 3, 11), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, f.width.toFloat(), f.height.toFloat(), paint)
        if (s.open > 0.5) {
            val outline = arrayOf(doubleArrayOf(.05, 2.9), doubleArrayOf(.76, 2.46), doubleArrayOf(.86, 1.36),
                doubleArrayOf(.4, 0.0), doubleArrayOf(-.4, 0.0), doubleArrayOf(-.86, 1.34), doubleArrayOf(-.74, 2.52))
            path.reset()
            for ((i, v) in outline.withIndex()) {
                val p = f.project(v[0], v[1], 0.0, s.yaw)
                if (i == 0) path.moveTo(p[0].toFloat(), p[1].toFloat()) else path.lineTo(p[0].toFloat(), p[1].toFloat())
            }
            path.close()
            paint.shader = LinearGradient((f.originX - f.unit).toFloat(), 0f, (f.originX + f.unit).toFloat(), 0f,
                intArrayOf(0xff728fd4.toInt(), 0xff243f94.toInt(), 0xffb4dcf2.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
            paint.shader = null
            paint.color = Color.rgb(200, 236, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = (72 * f.k).toFloat()
            paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            canvas.drawText("30", (f.originX - paint.measureText("30") / 2).toFloat(), (f.originY - 1.9 * f.unit).toFloat(), paint)
            path.reset()
            for (i in 0 until 8) {
                val a = PI / 8 + i * PI / 4
                val p = f.project(cos(a) * 1.017, 0.0, sin(a) * 1.017, s.yaw)
                if (i == 0) path.moveTo(p[0].toFloat(), p[1].toFloat()) else path.lineTo(p[0].toFloat(), p[1].toFloat())
            }
            path.close()
            paint.color = Color.rgb(220, 229, 243)
            canvas.drawPath(path, paint)
        } else {
            paint.shader = LinearGradient(f.originX.toFloat() - 12, 0f, f.originX.toFloat() + 12, 0f,
                intArrayOf(Color.TRANSPARENT, 0xff9bc7ff.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawRect((f.originX - 12).toFloat(), 0f, (f.originX + 12).toFloat(), f.height.toFloat(), paint)
            paint.shader = null
        }
    }
}
