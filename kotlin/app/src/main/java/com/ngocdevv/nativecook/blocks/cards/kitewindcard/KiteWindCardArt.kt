package com.ngocdevv.nativecook.blocks.cards.kitewindcard

import android.content.Context
import android.graphics.*
import kotlin.math.*

/** Canvas coordinates are dp; only vertical anchors scale with height, with centred 393 dp content. */
internal class KiteWindCardArt(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val kite = context.assets.open("kitewindcard/kitewindcard_kite.webp").use { BitmapFactory.decodeStream(it) }
    private val clouds = context.assets.open("kitewindcard/kitewindcard_clouds.webp").use { BitmapFactory.decodeStream(it) }
    private val display = Typeface.createFromAsset(context.assets, "kitewindcard/kitewindcard_InterDisplay-Bold.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "kitewindcard/kitewindcard_Inter-Medium.ttf")
    private val semi = Typeface.createFromAsset(context.assets, "kitewindcard/kitewindcard_Inter-SemiBold.ttf")
    private fun shadow(width: Float, height: Float, sigma: Float, draw: (Canvas, Paint) -> Unit): Bitmap {
        val source = Bitmap.createBitmap(ceil(width*3).toInt(), ceil(height*3).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(source); c.scale(3f, 3f)
        draw(c, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val offset = IntArray(2)
        // Android converts radius to sigma as radius * 0.57735 + 0.5. Invert it at 3×.
        val alpha = source.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter((sigma*3-0.5f)/0.57735f, BlurMaskFilter.Blur.NORMAL) }, offset)
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(alpha, offset[0].toFloat(), offset[1].toFloat(), Paint().apply { color = Color.rgb(0, 60, 140) })
        alpha.recycle(); source.recycle()
        return result
    }
    private val kiteShadow = shadow(142f, 185.2f, 2f) { c, p ->
        p.alpha = 64; c.drawBitmap(kite, null, RectF(12f, 12f, 130f, 173.2f), p)
    }
    private val cardShadow = shadow(393f, 168f, 13f/3) { c, p ->
        p.color = Color.argb(46, 0, 60, 140); c.drawRoundRect(20f, 20f, 373f, 148f, 26f, 26f, p)
    }
    private fun color(hex: Int, alpha: Double = 1.0) = (KiteWindMotion.rounded(alpha*255) shl 24) or hex
    private fun fill(hex: Int, alpha: Double = 1.0) {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; paint.color = color(hex, alpha)
    }
    private fun text(c: Canvas, copy: String, face: Typeface, size: Double, x: Double, y: Double, hex: Int, tracking: Double = 0.0, align: Double = 0.0) {
        fill(hex); paint.typeface = face; paint.textSize = size.toFloat(); paint.isSubpixelText = true
        val widths = FloatArray(copy.length); paint.getTextWidths(copy, widths)
        var cx = x-(widths.sum()+max(0, copy.length-1)*tracking)*align
        for (i in copy.indices) { c.drawText(copy, i, i+1, cx.toFloat(), y.toFloat(), paint); cx += widths[i]+tracking }
    }
    private fun bitmap(c: Canvas, bitmap: Bitmap, x: Double, y: Double, width: Double, height: Double) {
        fill(0xFFFFFF); rect.set(x.toFloat(), y.toFloat(), (x+width).toFloat(), (y+height).toFloat()); c.drawBitmap(bitmap, null, rect, paint)
    }
    private fun round(c: Canvas, x: Double, y: Double, width: Double, height: Double, radius: Float) {
        c.drawRoundRect(x.toFloat(), y.toFloat(), (x+width).toFloat(), (y+height).toFloat(), radius, radius, paint)
    }
    fun pose(width: Double, height: Double, f: KiteWindMotion.Frame): Triple<Double, Double, Double> {
        val wn = (KiteWindMotion.wind(f.p)-10)/16; val ph = 2*PI*f.t/8; val s = 0.25+0.75*wn
        return Triple((width-393)/2+300+10*s*sin(3*ph)+4*sin(7*ph)+f.x,
            (196-18*wn+8*s*sin(5*ph+1)+3*cos(4*ph))*height/852+f.y,
            (14+6*s*sin(3*ph+0.6)+2*sin(8*ph))*PI/180)
    }
    fun draw(c: Canvas, width: Double, height: Double, f: KiteWindMotion.Frame) {
        val ox = (width-393)/2; val fy = height/852; val w = KiteWindMotion.wind(f.p); val wn = (w-10)/16; val ph = 2*PI*f.t/8
        fill(0xFFFFFF)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), intArrayOf(color(0x1E8EF0), color(0x3FB0FF), color(0x8FD3FF)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val (kx, ky, rot) = pose(width, height, f)
        fun point(y: Double) = kx-y*sin(rot) to ky+y*cos(rot)
        val bridle = point(8.0); val bottom = point(161.2/2-2); val ax = ox-30; val ay = 820*fy; val sag = 60*(1-wn)+18
        fill(0xFFFFFF, 0.85); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        path.reset(); path.moveTo(bridle.first.toFloat(), bridle.second.toFloat())
        path.quadTo(((bridle.first+ax)/2+sag*0.6).toFloat(), ((bridle.second+ay)/2+sag).toFloat(), ax.toFloat(), ay.toFloat()); c.drawPath(path, paint)
        val xs = DoubleArray(11); val ys = DoubleArray(11); xs[0] = bottom.first; ys[0] = bottom.second
        val ang = rot+PI/2-0.15+0.25*(1-wn)
        for (i in 1..10) {
            val d = i*21.0; val wave = (7+12*wn)*sin(ph*5-i*0.75)*(i/10.0+0.3)
            xs[i] = bottom.first+cos(ang)*d+cos(ang+PI/2)*wave
            ys[i] = bottom.second+sin(ang)*d+sin(ang+PI/2)*wave
        }
        fill(0xFFFFFF, 0.9); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f
        path.reset(); path.moveTo(xs[0].toFloat(), ys[0].toFloat()); for (i in 1..10) path.lineTo(xs[i].toFloat(), ys[i].toFloat()); c.drawPath(path, paint)
        path.reset()
        for (sign in doubleArrayOf(-1.0, 1.0)) { path.moveTo(0f, 0f); path.lineTo((sign*9).toFloat(), -5.5f); path.lineTo((sign*9).toFloat(), 5.5f); path.close() }
        for (i in 1..10) {
            val a = atan2(ys[i]-ys[i-1], xs[i]-xs[i-1])+PI/2+0.4*sin(ph*5-i)
            fill(if (i%2 == 1) 0xFFD21E else 0xFFB800)
            c.save(); c.translate(xs[i].toFloat(), ys[i].toFloat()); c.rotate((a*180/PI).toFloat()); c.drawPath(path, paint); c.restore()
        }
        // The translation is screen-down before rotating the silhouette.
        c.save(); c.translate(kx.toFloat(), (ky+8.0/3).toFloat()); c.rotate((rot*180/PI).toFloat())
        bitmap(c, kiteShadow, -71.0, -92.6, 142.0, 185.2); c.restore()
        c.save(); c.translate(kx.toFloat(), ky.toFloat()); c.rotate((rot*180/PI).toFloat())
        bitmap(c, kite, -59.0, -80.6, 118.0, 161.2); c.restore()
        bitmap(c, clouds, ox-50+8*sin(ph), 590*fy, 491.0, 221.8)
        text(c, "HARBOUR PARK · NE WIND", semi, 13.0, ox+30, 92*fy, 0xDFF1FF, 4.0)
        text(c, "${KiteWindMotion.rounded(w)}", display, 132.0, ox+26, 214*fy, 0xFFFFFF, -7.0)
        text(c, "km/h · gusts ${KiteWindMotion.rounded(w*1.4)}", display, 24.0, ox+30, 250*fy, 0xFFFFFF)
        text(c, if (w < 24) "Good kite weather until 6 pm." else "Strong gusts: hold on tight.", medium, 16.0, ox+30, 280*fy, 0xE6F4FF)
        val top = 694*fy
        fun y(v: Double) = top+v-694
        bitmap(c, cardShadow, ox, top-20+8.0/3, 393.0, 168.0)
        fill(0xF6FBFF); round(c, ox+20, top, 353.0, 128.0, 26f)
        text(c, "Today", semi, 15.0, ox+40, y(724.0), 0x0E3A66)
        text(c, "${KiteWindMotion.hour(f.p)} · ${KiteWindMotion.rounded(w)} km/h", medium, 13.0, ox+353, y(724.0), 0x5D86AE, align = 1.0)
        for (i in 0..9) {
            val h = 8+KiteWindMotion.winds[i]*1.5
            fill(if (abs(i-f.p) < 0.5) 0x1E8EF0 else 0xCFE6FA); round(c, ox+46+i*31.4, y(792.0)-h, 12.0, h, 4f)
        }
        val sx = ox+52+f.p*31.4
        fill(0x1E8EF0); paint.strokeWidth = 2f; c.drawLine(sx.toFloat(), y(742.0).toFloat(), sx.toFloat(), y(796.0).toFloat(), paint)
        c.drawCircle(sx.toFloat(), y(742.0).toFloat(), 5f, paint)
        for (i in doubleArrayOf(0.0, 3.0, 6.0, 9.0)) text(c, KiteWindMotion.hour(i), medium, 11.0, ox+52+i*31.4, y(812.0), 0x5D86AE, align = 0.5)
        if (f.finger > 0) { fill(0x0E3A66, 0.2*f.finger); c.drawCircle(sx.toFloat(), y(768.0).toFloat(), 22f, paint) }
    }
}
