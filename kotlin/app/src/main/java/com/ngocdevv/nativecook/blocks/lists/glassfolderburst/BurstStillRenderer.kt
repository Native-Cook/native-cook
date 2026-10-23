package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import android.graphics.*

/** API 26–32: stationary compositions and fades, with a smoked glass illustration. */
internal class BurstStillRenderer(private val layout: BurstLayout, private val rows: BurstRowPainter) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(canvas: Canvas, s: BurstScene) {
        val f = BurstPose.folder(s, layout)
        val o = BurstPose.orb(s, layout)
        paint.shader = RadialGradient((layout.width * .35).toFloat(), (layout.height * .76).toFloat(),
            (layout.width * .9).toFloat(), Color.rgb(0, 20, 10), Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, layout.width.toFloat(), layout.height.toFloat(), paint)
        canvas.save()
        canvas.translate(f.x.toFloat(), f.y.toFloat())
        canvas.scale(f.sx.toFloat(), f.sy.toFloat())
        paint.shader = LinearGradient(0f, -52f, 0f, 52f, Color.rgb(16, 52, 35), Color.rgb(5, 18, 12), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(-62f, -52f, 62f, 52f, 16f, 16f, paint)
        canvas.restore()
        if (s.expanded) {
            paint.shader = RadialGradient((o.x - o.r * .3).toFloat(), (o.y - o.r * .4).toFloat(), (o.r * 1.6).toFloat(),
                Color.rgb(182, 255, 214), Color.rgb(20, 210, 110), Shader.TileMode.CLAMP)
            canvas.drawCircle(o.x.toFloat(), o.y.toFloat(), o.r.toFloat(), paint)
            paint.shader = null
            paint.color = Color.rgb(4, 20, 11)
            val heights = floatArrayOf(.5f, .95f, .7f, .4f)
            heights.forEachIndexed { i, h ->
                val x = (o.x + (i - 1.5) * .28 * o.r).toFloat()
                canvas.drawRoundRect(x - 1.6f, (o.y - h * o.r / 2).toFloat(), x + 1.6f,
                    (o.y + h * o.r / 2).toFloat(), 1.6f, 1.6f, paint)
            }
        }
        rows.rows(canvas, s, f)
        BurstGeometry.front(f, path)
        paint.shader = LinearGradient(0f, (f.y - 46 * f.sy).toFloat(), 0f, (f.y + 52 * f.sy).toFloat(),
            Color.rgb(12, 64, 41), Color.rgb(1, 17, 10), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        if (!s.expanded) {
            canvas.save()
            canvas.clipPath(path)
            paint.shader = RadialGradient(o.x.toFloat(), o.y.toFloat(), (o.r * 1.4).toFloat(),
                intArrayOf(Color.rgb(0, 215, 100), Color.rgb(0, 180, 80), Color.TRANSPARENT),
                floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(o.x.toFloat(), o.y.toFloat(), (o.r * 1.4).toFloat(), paint)
            canvas.restore()
        }
        paint.shader = LinearGradient(0f, (f.y - 46 * f.sy).toFloat(), 0f, (f.y + 52 * f.sy).toFloat(),
            Color.rgb(90, 157, 126), Color.rgb(26, 63, 42), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        rows.header(canvas, s)
    }
}
