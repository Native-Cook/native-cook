package com.ngocdevv.nativecook.blocks.modals.lanternwishsend

import android.content.Context
import android.graphics.*
import kotlin.math.*

internal class LanternWishSendArt(private val context: Context) {
    private fun load(name: String) = context.assets.open("lanternwishsend/lanternwishsend_$name").use { BitmapFactory.decodeStream(it) }
    private val sky = load("sky.webp")
    private val lit = load("lantern.png")
    private val packet = load("lantern-folded.png")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val target = RectF()
    private val distantGlow = glow(0.55f, 0.5f)
    private val heroGlow = glow(0.6f, 0.45f)
    private fun glow(radius: Float, sigma: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255,180,90)
            maskFilter = BlurMaskFilter(sigma * 100, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(bitmap).drawCircle(300f, 300f, radius * 100, p)
        return bitmap
    }
    private fun image(c: Canvas, b: Bitmap, x: Double, y: Double, w: Double, h: Double, alpha: Double = 1.0) {
        paint.alpha = (alpha * 255).roundToInt().coerceIn(0,255)
        target.set(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat())
        c.drawBitmap(b, null, target, paint)
    }
    fun draw(c: Canvas, w: Double, h: Double, f: LanternWishSendMotion.Frame, reduced: Boolean) {
        image(c, sky, 0.0, 0.0, w, h)
        for (i in 0 until 22) {
            val depth = 0.25 + ((i * 37) % 100) / 100.0 * 0.75
            val y0 = ((i * 211) % 997) / 997.0
            val y = h * 1.05 - ((y0 + if (reduced) 0.0 else f.t / 9) % 1) * h * 1.2
            val hx = sin(i * 12.9898) * 43758.5453
            val x = (hx - floor(hx)) * w + if (reduced) 0.0 else sin(f.t * 2 * PI / 9 + i) * 10 * depth
            val sz = 20 + 44 * depth * depth
            image(c, distantGlow, x-sz*3, y-sz*3, sz*6, sz*6, 0.18 * depth)
            image(c, lit, x-sz*0.375, y-sz*0.5, sz*0.75, sz, 0.45 + 0.55 * depth)
        }
        if (f.sending) {
            val sz = LanternWishSendMotion.mix(170.0, 26.0, LanternWishSendMotion.smoother(f.rise))
            val y = LanternWishSendMotion.mix(round(h * 0.6), h * 0.12, f.rise) - f.flick * 40
            val x = w / 2 + if (reduced) 0.0 else sin(f.rise * 7) * 22 * f.rise
            val width = sz * 0.75
            val stretch = if (f.light > 0 && f.light < 1) 1 + 0.08 * sin(f.light * PI) else 1.0
            if (f.light > 0) image(c, heroGlow, x-sz*3, y-sz*3, sz*6, sz*6, 0.5*f.light)
            val ph = sz * (0.4 + 0.6 * f.fold)
            image(c, packet, x-width/2, y-ph/2, width, ph, 1-f.light)
            image(c, lit, x-width/2, y-sz*stretch/2, width, sz*stretch, f.light)
        }
    }
}
