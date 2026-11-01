package com.ngocdevv.nativecook.blocks.cards.cashbackcloudcard

import android.content.Context
import android.graphics.*
import java.util.Locale
import kotlin.math.*

internal class CashbackCloudCardArt(context: Context) {
    private val assets = context.assets
    private fun bitmap(name: String) = assets.open("cashbackcloudcard/cashbackcloudcard_$name.png").use { BitmapFactory.decodeStream(it) }
    private val cloud = bitmap("cloud")
    private val coin = bitmap("coin")
    private val edge = bitmap("coin-edge")
    private val regular = Typeface.createFromAsset(assets, "cashbackcloudcard/cashbackcloudcard_Inter-Regular.ttf")
    private val semibold = Typeface.createFromAsset(assets, "cashbackcloudcard/cashbackcloudcard_Inter-SemiBold.ttf")
    private val bold = Typeface.createFromAsset(assets, "cashbackcloudcard/cashbackcloudcard_Inter-Bold.ttf")
    private val icons = Typeface.createFromAsset(assets, "cashbackcloudcard/cashbackcloudcard_Ionicons.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dest = RectF()
    private val clip = Path()
    private fun c(hex: Int) = hex or 0xFF000000.toInt()
    private fun rect(cv: Canvas, x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int) {
        paint.shader = null; paint.color = c(color); paint.alpha = 255
        dest.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        cv.drawRoundRect(dest, radius.toFloat(), radius.toFloat(), paint)
    }
    private fun text(cv: Canvas, value: String, x: Double, y: Double, sz: Double, face: Typeface, color: Int) {
        paint.shader = null; paint.color = c(color); paint.alpha = 255
        paint.typeface = face; paint.textSize = sz.toFloat()
        cv.drawText(value, x.toFloat(), y.toFloat() - paint.fontMetrics.ascent, paint)
    }
    private fun icon(cv: Canvas, glyph: Int, x: Double, y: Double, sz: Double, color: Int) = text(cv, glyph.toChar().toString(), x, y, sz, icons, color)
    private fun image(cv: Canvas, img: Bitmap, x: Double, y: Double, w: Double, h: Double, alpha: Double = 1.0) {
        paint.shader = null; paint.color = Color.WHITE; paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        dest.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        cv.drawBitmap(img, null, dest, paint)
    }
    fun draw(cv: Canvas, width: Double, height: Double, top: Double, f: CashbackCloudCardMotion.Frame, reduced: Boolean) {
        val w = width - 32
        val y = top + 120
        rect(cv, 0.0, 0.0, width, height, 0.0, 0xEEF3FF)
        text(cv, "Rewards", 20.0, top + 15.5, 30.0, bold, 0x1E2A4A)
        rect(cv, width - 64, top + 12, 44.0, 44.0, 22.0, 0xFFFFFF)
        icon(cv, 62591, width - 52.5, top + 23.5, 21.0, 0x1E2A4A)
        text(cv, "Main balance", 20.0, top + 70, 14.0, regular, 0x5A6B8F)
        text(cv, String.format(Locale.US, "$%,.2f", 1284.5 + 0.36 * f.fraction), 118.0, top + 68, 16.0, semibold, 0x1E2A4A)
        rect(cv, 16.0, y, w, 430.0, 30.0, 0x3D7BFF)
        cv.save()
        clip.reset(); clip.addRoundRect(16f, y.toFloat(), (width - 16).toFloat(), (y + 430).toFloat(), 30f, 30f, Path.Direction.CW)
        cv.clipPath(clip)
        rect(cv, 16.0, y + 280, w, 190.0, 40.0, 0xFFFFFF)
        cv.restore()
        rect(cv, width - 109, y + 18, 57.0, 30.0, 15.0, 0xD7F5E2)
        text(cv, "New", width - 95, y + 24.5, 14.0, semibold, 0x1F7A45)
        text(cv, String.format(Locale.US, "$%.2f", 0.36 * (1 - f.fraction)), 42.0, y + 306, 44.0, bold, 0x1E2A4A)
        text(cv, "Cashback this week", 42.0, y + 363, 16.0, regular, 0x5A6B8F)
        val base = y + 262
        if (f.rainU >= 0) for (i in 0 until 6) {
            val k = CashbackCloudCardMotion.coinK(f.rainU, i)
            if (k <= 0 || k >= 1) continue
            val cx = 16 + w * (0.36 + (i * 37 % 30) / 100.0)
            val cy = base - 30 + 78 * k * k
            val spin = if (reduced) 1.0 else cos(k * PI * 3 + i)
            val sx = if (abs(spin) < 0.3) 1.0 else abs(spin)
            image(cv, if (abs(spin) < 0.3) edge else coin, cx - 23 * sx, cy - 23, 46 * sx, 46.0,
                if (reduced) 1 - k else 1 - ((k - 0.85) / 0.15).coerceIn(0.0, 1.0))
        }
        val sq = if (reduced) 0.0 else 0.16 * f.squeeze + f.release * 0.16
        val cw = w * 0.92 * (1 + sq * 0.6)
        val ch = cw * (560.0 / 900) * (1 - sq)
        image(cv, cloud, 16 + w / 2 - cw / 2, base - ch, cw, ch)
        val a = max(if (f.rainU >= 0) 0.0 else 1.0, f.refill)
        if (a > 0) {
            val sz = 64 * (0.6 + 0.4 * a)
            image(cv, coin, 16 + w * 0.6 - sz / 2, base - ch * 0.92 - sz * 0.2, sz, sz, a)
        }
        paint.typeface = semibold; paint.textSize = 14f
        val hx = (width - paint.measureText("Squeeze the cloud to collect") - 26) / 2
        icon(cv, 62300, hx, y + 448, 18.0, 0x5A6B8F)
        text(cv, "Squeeze the cloud to collect", hx + 26, y + 448, 14.0, semibold, 0x5A6B8F)
        for (i in 0 until 2) {
            val ry = y + 488 + i * 70
            rect(cv, 16.0, ry, w, 60.0, 18.0, 0xFFFFFF)
            rect(cv, 30.0, ry + 12, 36.0, 36.0, 12.0, 0xE6EEFF)
            icon(cv, if (i == 0) 61898 else 61955, 39.0, ry + 21, 18.0, 0x3D7BFF)
            text(cv, if (i == 0) "Corner Café" else "Greenleaf Market", 78.0, ry + 21, 15.0, semibold, 0x1E2A4A)
            val value = if (i == 0) "+$0.12" else "+$0.24"
            paint.typeface = semibold; paint.textSize = 15f
            val tw = paint.measureText(value)
            text(cv, value, width - 30 - tw, ry + 21, 15.0, semibold, 0x1F7A45)
        }
    }
}
