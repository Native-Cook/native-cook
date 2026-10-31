package com.ngocdevv.nativecook.blocks.onboarding.wavyframeauction

import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class WavyFrameAuctionArt(private val title: Typeface, private val medium: Typeface, private val bold: Typeface) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader = if (Build.VERSION.SDK_INT >= 33) WavyFrameAuctionShader() else null
    private val blur = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
    private val m = WavyFrameAuctionMotion
    private fun ink(color: Int, alpha: Double = 1.0) { p.reset(); p.isAntiAlias = true; p.color = color; p.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt() }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, face: Typeface, color: Int, alpha: Double = 1.0, center: Boolean = false) {
        ink(color, alpha); p.typeface = face; p.textSize = size
        p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(value, x, y - p.fontMetrics.ascent, p)
    }
    fun draw(c: Canvas, w: Double, h: Double, top: Double, bottom: Double, s: Double, ph: Double, lift: Double, secs: Int) {
        c.drawColor(0xff1a1d18.toInt())
        val f = m.frame(s, w, h, top); val r = m.mix(30.0, 28.0, m.smooth(s))
        val amp = m.mix(9.0, 0.0, m.smooth(s * 1.25))
        c.save(); c.rotate((-2.2 * lift).toFloat(), f.centerX(), f.centerY()); c.scale((1 + 0.025 * lift).toFloat(), (1 + 0.025 * lift).toFloat(), f.centerX(), f.centerY())
        if (lift > 0.01) { ink(Color.BLACK, 0.55 * lift); p.maskFilter = blur; c.drawRoundRect(f.left, f.top + (18 * lift).toFloat(), f.right, f.bottom + (18 * lift).toFloat(), 30f, 30f, p) }
        val outer = RectF(f).apply { inset(-12f, -12f) }
        val path = m.wavy(outer, r + 10, amp * 1.15, ph * 2)
        ink(0xff2b2f29.toInt()); c.drawPath(path, p)
        ink(0xff4e515c.toInt()); p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; c.drawPath(path, p)
        c.save(); c.clipPath(m.wavy(f, r, amp, ph * 2 + 0.6))
        ink(0xff7862f8.toInt())
        if (Build.VERSION.SDK_INT >= 33) p.shader = shader?.at(f.centerX(), f.centerY(), f.width(), f.height(), ph.toFloat(), s.toFloat())
        else p.shader = LinearGradient(f.left, f.top, f.right, f.bottom, intArrayOf(0xff7862f8.toInt(), 0xffff70b5.toInt(), 0xffc8f560.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(f.left - 20, f.top - 20, f.right + 20, f.bottom + 20, p); c.restore(); c.restore()
        val (tab, ty) = m.tab(w, h, bottom, s, ph)
        ink(0xffc8f560.toInt()); c.drawPath(tab, p)
        val ch = 1 - m.smooth(s * 2.5)
        if (ch > 0.01) {
            ink(0xff21251f.toInt(), ch); p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
            for (j in 0..1) {
                val y = (ty + 28 + j * 10 + sin(ph * 4) * 2).toFloat(); val x = (w / 2).toFloat()
                c.drawPath(Path().apply { moveTo(x - 14, y + 6); lineTo(x, y); lineTo(x + 14, y + 6) }, p)
            }
        }
        val intro = 1 - m.smooth(s * 2.2)
        if (intro > 0.0) {
            val y = top + 430 + (1 - intro) * 24
            ink(Color.WHITE); p.typeface = title; p.textSize = 32f
            val first = " Perhaps The"; val x = (w - p.measureText(first)) / 2
            val prefix = p.measureText(" Perh"); val baseline = y + (40 - (p.fontMetrics.descent - p.fontMetrics.ascent)) / 2
            ink(0xffc8f560.toInt(), intro); c.drawRect(x.toFloat(), baseline.toFloat(), (x + prefix).toFloat(), (baseline + 40).toFloat(), p)
            text(c, " Perh", x.toFloat(), baseline.toFloat(), 32f, title, 0xff21251f.toInt(), intro)
            text(c, "aps The", (x + prefix).toFloat(), baseline.toFloat(), 32f, title, Color.WHITE, intro)
            ink(Color.WHITE); p.typeface = title; p.textSize = 32f
            val secondX = (w - p.measureText("Most Convenient")) / 2; val most = p.measureText("Most ")
            text(c, "Most ", secondX.toFloat(), (baseline + 40).toFloat(), 32f, title, Color.WHITE, intro)
            text(c, "Convenient", (secondX + most).toFloat(), (baseline + 40).toFloat(), 32f, title, 0xffc8f560.toInt(), intro)
            text(c, "NFT Auction", (w / 2).toFloat(), (baseline + 80).toFloat(), 32f, title, Color.WHITE, intro, true)
        }
        val detail = m.smooth((s - 0.55) / 0.45)
        if (detail > 0.0) {
            val y = (top + 64 + h * 0.56 + 22 + (1 - detail) * 20).toFloat()
            ink(0xff7862f8.toInt(), detail); c.drawCircle(43f, y + 21, 21f, p)
            ink(0xffc8f560.toInt(), detail); p.style = Paint.Style.STROKE; p.strokeWidth = 2f; c.drawCircle(43f, y + 21, 20f, p)
            text(c, "Lumen Tide #042", 76f, y, 22f, title, Color.WHITE, detail)
            text(c, "@nadia.studio", 76f, y + 29, 13f, medium, Color.WHITE, detail * 0.55)
            var x = 22f
            for ((label, fg, bg) in listOf(Triple("0.34 ETH", 0xff21251f.toInt(), 0xffc8f560.toInt()), Triple("$1,284", Color.WHITE, 0xff4e515c.toInt()))) {
                ink(fg); p.typeface = bold; p.textSize = 14f; val width = p.measureText(label) + 24
                ink(bg, detail); c.drawRoundRect(x, y + 62, x + width, y + 94, 16f, 16f, p)
                text(c, label, x + 12, y + 69, 14f, bold, fg, detail); x += width + 8
            }
            val countdown = "Ends in %02d : %02d : %02d".format(secs / 3600, secs % 3600 / 60, secs % 60)
            ink(Color.WHITE); p.typeface = medium; p.textSize = 13f; val width = p.measureText(countdown)
            text(c, countdown, w.toFloat() - 22 - width, y + 70, 13f, medium, Color.WHITE, detail * 0.75)
        }
        val bid = m.smooth((s - 0.7) / 0.3)
        text(c, "Place a Bid", (w / 2).toFloat(), (h - bottom - 61).toFloat(), 17f, bold, 0xff21251f.toInt(), bid, true)
    }
}
