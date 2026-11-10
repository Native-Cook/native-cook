package com.ngocdevv.nativecook.blocks.charts.rainwindowforecast

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class RainWindowForecastArt(context: Context) {
    private val path = "rainwindowforecast/rainwindowforecast_"
    private fun image(context: Context, name: String) = context.assets.open(path + name).use { BitmapFactory.decodeStream(it) }
    private val sharp = image(context, "city.jpg")
    private val blur = image(context, "city-blur.jpg")
    private val regular = Typeface.createFromAsset(context.assets, path + "Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets, path + "Inter-Medium.ttf")
    private val semi = Typeface.createFromAsset(context.assets, path + "Inter-SemiBold.ttf")
    private val display = Typeface.createFromAsset(context.assets, path + "InstrumentSerif-Regular.ttf")
    private val runtime = if (Build.VERSION.SDK_INT >= 33) RainWindowForecastRuntime(sharp, blur) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = RectF()
    private var overlay: Bitmap? = null
    private var key = ""
    private var fallback: Bitmap? = null
    private val minutes = floatArrayOf(.62f, .7f, .78f, .66f, .5f, .36f, .22f, .12f, .05f, 0f, 0f, 0f)
    private fun cover(canvas: Canvas, image: Bitmap, w: Float, h: Float) {
        val scale = max(w / image.width, h / image.height)
        val iw = image.width * scale
        val ih = image.height * scale
        shape.set((w - iw) / 2, (h - ih) / 2, (w + iw) / 2, (h + ih) / 2)
        paint.shader = null
        paint.alpha = 255
        canvas.drawBitmap(image, null, shape, paint)
    }
    private fun staticArt(w: Float, h: Float, top: Float, bottom: Float, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(ceil(w * density).toInt(), ceil(h * density).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(density, density)
        fun text(s: String, face: Typeface, size: Float, x: Float, y: Float, line: Float = size * 1.22f,
                 alpha: Int = 255, align: Paint.Align = Paint.Align.CENTER, tracking: Float = 0f, shadow: Boolean = true) {
            textPaint.typeface = face
            textPaint.textSize = size
            textPaint.color = Color.argb(alpha, 255, 255, 255)
            textPaint.textAlign = align
            if (shadow) textPaint.setShadowLayer(8f, 0f, 1f, Color.argb(115, 8, 12, 28)) else textPaint.clearShadowLayer()
            val fm = textPaint.fontMetrics
            val baseline = y + (line - (fm.descent - fm.ascent)) / 2 - fm.ascent
            if (tracking == 0f) c.drawText(s, x, baseline, textPaint)
            else {
                var tx = x
                s.forEach { ch ->
                    val glyph = ch.toString()
                    c.drawText(glyph, tx, baseline, textPaint)
                    tx += textPaint.measureText(glyph) + tracking
                }
            }
        }
        val city = top + 24
        text("Aurenne", medium, 17f, w / 2, city, 24f)
        text("14°", display, 104f, w / 2, city + 24, 108f)
        text("Light rain · stops in 25 min", medium, 15f, w / 2, city + 132, 20f)
        text("H 17°  L 11°", regular, 13f, w / 2, city + 154, 20f, 184)
        val hint = h - max(bottom, 16f) - 28
        val card = hint - 184
        paint.shader = null
        paint.color = Color.argb(107, 16, 22, 40)
        c.drawRoundRect(24f, card, w - 24, card + 168, 28f, 28f, paint)
        paint.color = Color.argb(46, 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .5f
        c.drawRoundRect(24.25f, card + .25f, w - 24.25f, card + 167.75f, 27.75f, 27.75f, paint)
        paint.style = Paint.Style.FILL
        text("NEXT HOUR", semi, 11f, 40f, card + 18, 16f, 184, Paint.Align.LEFT, 1.4f, false)
        text("Stops 9:25", medium, 13f, w - 40, card + 16, 16f, 255, Paint.Align.RIGHT, shadow = false)
        val barBottom = card + 16 + 16 + 12 + 72
        val barWidth = (w - 48 - 32 - 66) / 12
        minutes.forEachIndexed { i, value ->
            paint.color = Color.argb(if (value > 0) 242 else 89, 196, 214, 255)
            val x = 40 + i * (barWidth + 6)
            c.drawRoundRect(x, barBottom - max(3f, value * 64), x + barWidth, barBottom, 3f, 3f, paint)
        }
        textPaint.typeface = regular
        textPaint.textSize = 11f
        val ticks = listOf("Now", "15", "30", "45", "60")
        val widths = ticks.map { textPaint.measureText(it) }
        val gap = (w - 80 - widths.sum()) / 4
        var x = 40f
        ticks.forEachIndexed { i, s ->
            text(s, regular, 11f, x, barBottom + 8, 14f, 184, Paint.Align.LEFT, shadow = false)
            x += widths[i] + gap
        }
        text("Wipe the glass to look outside", regular, 13f, w / 2, hint, 20f, 184)
        return bitmap
    }
    fun draw(c: Canvas, w: Float, h: Float, top: Float, bottom: Float, density: Float, t: Double, f: RainWindowForecastMotion.Frame) {
        val nextKey = "$w/$h/$top/$bottom/$density"
        if (key != nextKey) {
            key = nextKey
            overlay?.recycle()
            overlay = staticArt(w, h, top, bottom, density)
            if (Build.VERSION.SDK_INT < 33) {
                fallback?.recycle()
                fallback = Bitmap.createBitmap(ceil(w).toInt(), ceil(h).toInt(), Bitmap.Config.ARGB_8888).also { b ->
                    val bc = Canvas(b)
                    cover(bc, blur, w, h)
                    bc.drawColor(Color.argb(95, 51, 56, 71))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
            paint.color = Color.WHITE
            paint.shader = runtime.update(w, h, t, f)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        } else {
            paint.alpha = 255
            fallback?.let { c.drawBitmap(it, 0f, 0f, paint) }
            // A simple drawn glass/wipe fallback on Android 8–12.
            c.save()
            val wipePath = Path()
            f.wipe.zipWithNext().forEach { (a, b) ->
                if (a.age >= 0 && b.age >= 0 && min(a.age, b.age) < 5) {
                    val radius = (46 * (1 - RainWindowForecastMotion.seg((a.age + b.age) / 2, 1.2, 5.0))).toFloat()
                    wipePath.addCircle(a.x.toFloat(), a.y.toFloat(), max(.5f, radius), Path.Direction.CW)
                }
            }
            c.clipPath(wipePath)
            cover(c, sharp, w, h)
            c.restore()
            for (i in 0..20) {
                val x = (i * 39.7f + 17) % w
                val y = ((i * 67.3 + t / 10 * (h + 160)) % (h + 160) - 80).toFloat()
                paint.color = Color.argb(70, 220, 232, 255)
                c.drawOval(x - 3, y - 5, x + 3, y + 5, paint)
            }
        }
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 255
        overlay?.let { c.drawBitmap(it, null, RectF(0f, 0f, w, h), paint) }
        val r = (22 * (1 - .12 * f.press)).toFloat()
        paint.color = Color.argb((255 * .14 * f.finger).toInt(), 255, 255, 255)
        c.drawCircle(f.fx.toFloat(), f.fy.toFloat(), r, paint)
        paint.color = Color.argb((255 * .5 * f.finger).toInt(), 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        c.drawCircle(f.fx.toFloat(), f.fy.toFloat(), r, paint)
        paint.style = Paint.Style.FILL
    }
}
