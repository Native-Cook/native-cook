package com.ngocdevv.nativecook.blocks.animations.superchargecard

import android.graphics.*

/** API 26–32: still, textured materials with a live gauge; no unsupported shader APIs. */
internal class SuperchargeFallback(strip: Bitmap) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paper: Bitmap
    private val bolt =
        Path().apply {
            moveTo(240f, 220f)
            lineTo(222f, 273f)
            lineTo(281f, 271f)
            lineTo(156f, 421f)
            lineTo(193f, 320f)
            lineTo(149f, 327f)
            close()
        }

    init {
        val width = strip.width / 3
        val pixels = IntArray(width * strip.height)
        for (y in 0 until strip.height) for (x in 0 until width) {
            val coverage = Color.red(strip.getPixel(x, y))
            val relief = Color.red(strip.getPixel(x + width, y)) / 255f
            val ink = Color.red(strip.getPixel(x + width * 2, y)) / 255f
            val down = (y / 3f - 6) / 180f
            val shade =
                (1.30f * (1 - down) + .68f * down) *
                    (.98f + (relief - .5f) * .18f) *
                    (.07f + .93f * ink)
            fun channel(offset: Float): Int =
                ((1 - kotlin.math.exp(-(shade + offset))) * 255).toInt().coerceIn(0, 255)
            pixels[y * width + x] = Color.argb(coverage, channel(0f), channel(.035f), channel(.08f))
        }
        paper = Bitmap.createBitmap(pixels, width, strip.height, Bitmap.Config.ARGB_8888)
    }

    fun draw(canvas: Canvas, s: SuperchargeState) {
        val level = s.energy.toFloat()
        canvas.save()
        canvas.translate(0f, s.sink.toFloat())
        paint.shader =
            LinearGradient(
                65f,
                288f,
                337f,
                628f,
                intArrayOf(
                    Color.rgb(32 + (level * 80).toInt(), 31 + (level * 65).toInt(), 20),
                    Color.rgb(18, 18, 15),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(65f, 288f, 337f, 622f, 18f, 18f, paint)
        paint.shader = null
        paint.color = if (level > .1f) Color.rgb(248, 242, 125) else Color.rgb(49, 36, 23)
        canvas.drawPath(bolt, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .8f
        paint.color = if (level > .1f) Color.rgb(255, 255, 212) else Color.rgb(129, 108, 74)
        canvas.drawPath(bolt, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
        canvas.drawBitmap(paper, null, RectF(59f, 442f, 347f, 634f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.5f
        paint.color = Color.rgb(30, 33, 24)
        canvas.drawCircle(268f, 466f, 30f, paint)
        paint.color = Color.rgb(255, 247, 123)
        canvas.drawArc(238f, 436f, 298f, 496f, -90f, 360 * level, false, paint)
        paint.style = Paint.Style.FILL
    }
}
