package com.ngocdevv.nativecook.blocks.cards.ingotcoinswitch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build

/*
 Ingot Coin Switch — the embossed marks, drawn once into bitmaps (a port of IngotCoinSwitchArt.swift).

 An ingot's top face carries "FINE", the metal's name, "999.9", "NET WT" and "1000 g"; a coin's faces
 carry a groove ring, a big letter and the invented ticker. Every line is stamped three times: a light
 edge up-left, a dark edge down-right and the letters in a deeper shade of the metal, so they read as
 raised. The bitmaps are in the face's own units (× 3), drawn through the face's projection.
*/
internal object IcsArt {
    const val INGOT_W = 70f
    const val INGOT_H = 190f
    const val COIN_W = 136f
    private const val SCALE = 3f

    val ingotMarks: List<Bitmap> by lazy { (0 until IcsModel.STYLES).map { ingot(it) } }
    val coinMarks: List<Bitmap> by lazy { (0 until IcsModel.STYLES).map { coin(it) } }

    private fun argb(c: IcsRgb, a: Double = 1.0) = android.graphics.Color.argb(
        (a * 255).toInt(), (c.r * 255).toInt().coerceIn(0, 255), (c.g * 255).toInt().coerceIn(0, 255), (c.b * 255).toInt().coerceIn(0, 255),
    )

    private fun typeface(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
        else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    /** One line of embossed text centred on (x, y), shrunk to fit `maxWidth`. */
    private fun Canvas.stamp(s: String, size: Float, weight: Int, kern: Float, x: Float, y: Float, maxWidth: Float, ink: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = typeface(weight)
        p.textSize = size
        p.letterSpacing = kern / size
        var w = p.measureText(s) - kern
        if (w > maxWidth) {
            p.textSize = size * maxWidth / w
            p.letterSpacing = kern / p.textSize
            w = p.measureText(s) - kern
        }
        val fm = p.fontMetrics
        val baseline = y - (fm.ascent + fm.descent) / 2
        val left = x - w / 2
        val e = maxOf(0.5f, size / 28f)
        p.color = android.graphics.Color.argb(153, 255, 255, 255)
        drawText(s, left - e, baseline - e, p)
        p.color = android.graphics.Color.argb(71, 0, 0, 0)
        drawText(s, left + e, baseline + e * 1.2f, p)
        p.color = ink
        drawText(s, left, baseline, p)
    }

    private fun render(w: Float, h: Float, body: Canvas.() -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap((w * SCALE).toInt(), (h * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)
        c.body()
        return bmp
    }

    private fun ingot(style: Int): Bitmap {
        val ink = argb(IcsRgb.hex(IcsModel.INGOT_HEX[style]) * 0.72)
        val cx = INGOT_W / 2
        return render(INGOT_W, INGOT_H) {
            // Bitmap y is down; the face's centre is at 95.
            stamp("FINE", 7f, 700, 1.2f, cx, 95f - 58f, 50f, ink)
            stamp(IcsModel.METALS[style].uppercase(), 15f, 900, 0.2f, cx, 95f - 42f, 52f, ink)
            stamp("999.9", 7.5f, 600, 0.6f, cx, 95f - 27f, 50f, ink)
            stamp("NET WT", 6.5f, 700, 0.8f, cx, 95f + 48f, 50f, ink)
            stamp("1000 g", 7.5f, 600, 0.4f, cx, 95f + 59f, 50f, ink)
        }
    }

    private fun coin(style: Int): Bitmap {
        val ink = argb(IcsRgb.hex(IcsModel.COIN_HEX[style]) * 0.7)
        val c = COIN_W / 2
        return render(COIN_W, COIN_W) {
            // The groove: a dark ring with a light ring just inside it.
            val r = 58f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.3f
            p.color = android.graphics.Color.argb(46, 0, 0, 0)
            drawOval(RectF(c - r, c - r, c + r, c + r), p)
            p.strokeWidth = 1f
            p.color = android.graphics.Color.argb(140, 255, 255, 255)
            drawOval(RectF(c - r + 1.3f, c - r + 1.3f, c + r - 1.3f, c + r - 1.3f), p)
            stamp(IcsModel.MARKS[style], 62f, 900, 0f, c, c - 4f, 90f, ink)
            stamp(IcsModel.TICKERS[style], 9f, 700, 1.6f, c, c + 38f, 70f, ink)
        }
    }
}
