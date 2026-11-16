package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.ngocdevv.nativecook.blocks.cards.coinpilecollect.CpcTheme as T

/**
 * cpc-art.ts as android.graphics: the page, the flat tiles, the type, the pile (one ray-cast AGSL pass on API 33+, a path-drawn pile
 * below), the pill, the amount's odometer and the exchange coin. The flat parts are drawn once into a bitmap per layout.
 */
internal class CoinPileCollectRenderer(assets: AssetManager, systemFontScale: Float) {
    private val fs = T.fontScaleOf(systemFontScale)
    private val extraBold = Typeface.createFromAsset(assets, "coinpilecollect/coinpilecollect_Onest-ExtraBold.ttf")
    private val semiBold = Typeface.createFromAsset(assets, "coinpilecollect/coinpilecollect_Onest-SemiBold.ttf")
    private val modern = Build.VERSION.SDK_INT >= 33

    // paths in design points
    private val tealPath = squircle(0.0, 0.0, T.TEAL_W, T.TEAL_H, T.R_TEAL)
    private val limePath = squircle(T.LIME_X, T.LIME_Y, T.LIME_W, T.LIME_H, T.R_TILE)
    private val magentaPath = squircle(T.MAG_X, T.MAG_Y, T.MAG_W, T.MAG_H, T.R_TILE)

    private var staticBitmap: Bitmap? = null
    private var staticKey = ""

    private val atlas: Bitmap by lazy { makeAtlas() }
    private val pile: PileShader? by lazy { if (modern) PileShader(atlas) else null }
    private val xcoin: XCoinShader? by lazy { if (modern) XCoinShader(atlas) else null }
    private val page: PageShader? by lazy { if (modern) PageShader() else null }

    // ---- small helpers ------------------------------------------------------------------------------------------------

    private fun argb(hex: Long, alpha: Float = 1f): Int = ((alpha * 255f + 0.5f).toInt() shl 24) or (hex.toInt() and 0xFFFFFF)
    private fun blur(sigma: Float) = BlurMaskFilter(max((sigma - 0.5f) / 0.57735f, 0.1f), BlurMaskFilter.Blur.NORMAL)
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun text(tf: Typeface, size: Float, tracking: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        textSize = size * fs
        letterSpacing = tracking
        this.color = color
    }

    private fun squircle(x: Double, y: Double, w: Double, h: Double, r: Double): Path {
        val n = 4.6
        val e = 2 / n
        val p = Path()
        val seg = 18
        fun corner(cx: Double, cy: Double, sx: Double, sy: Double, from: Double, to: Double) {
            for (i in 0..seg) {
                val t = from + (to - from) * i / seg
                val u = abs(cos(t)).pow(e)
                val v = abs(sin(t)).pow(e)
                p.lineTo((cx + sx * r * u).toFloat(), (cy + sy * r * v).toFloat())
            }
        }
        val q = Math.PI / 2
        p.moveTo((x + r).toFloat(), y.toFloat())
        p.lineTo((x + w - r).toFloat(), y.toFloat())
        corner(x + w - r, y + r, 1.0, -1.0, q, 0.0)
        p.lineTo((x + w).toFloat(), (y + h - r).toFloat())
        corner(x + w - r, y + h - r, 1.0, 1.0, 0.0, q)
        p.lineTo((x + r).toFloat(), (y + h).toFloat())
        corner(x + r, y + h - r, -1.0, 1.0, q, 0.0)
        p.lineTo(x.toFloat(), (y + r).toFloat())
        corner(x + r, y + r, -1.0, -1.0, 0.0, q)
        p.close()
        return p
    }

    /** the emboss atlas: three cells (the H mark, "$", "€") of 192 px, alpha = ridge height */
    private fun makeAtlas(): Bitmap {
        val cell = CpcEMBOSS_CELL
        val bmp = Bitmap.createBitmap(cell * 3, cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun stroke(w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            strokeJoin = Paint.Join.ROUND
            color = 0xFFFFFFFF.toInt()
            maskFilter = BlurMaskFilter(1.3f, BlurMaskFilter.Blur.NORMAL)
        }
        val pts = arrayOf(
            46f to 40f, 76f to 40f, 76f to 82f, 116f to 82f, 116f to 40f, 146f to 40f,
            146f to 152f, 116f to 152f, 116f to 110f, 76f to 110f, 76f to 152f, 46f to 152f,
        )
        val h = Path()
        pts.forEachIndexed { i, (x, y) -> if (i == 0) h.moveTo(x, y) else h.lineTo(x, y) }
        h.close()
        c.drawPath(h, stroke(5.2f))
        val tp = stroke(4.6f).apply {
            typeface = extraBold
            textSize = 168f
        }
        val cap = -tp.fontMetrics.ascent * 0.7f
        listOf(1 to "$", 2 to "€").forEach { (k, ch) ->
            val wd = tp.measureText(ch)
            c.drawText(ch, k * cell + (cell - wd) / 2f, cell / 2f + cap / 2f, tp)
        }
        return bmp
    }

    private fun textWidth(p: Paint, s: String) = p.measureText(s)

    // ---- the flat layer -------------------------------------------------------------------------------------------------

    private fun drawStatic(c: Canvas, bottom: Double) {
        drawAvatars(c)
        drawBack(c)
        drawTitle(c)
        drawLime(c)
        drawMagentaBase(c)
        drawNavy(c, bottom)
        drawPremium(c)
    }

    private fun drawAvatars(c: Canvas) {
        val cy = (T.AV_Y + T.AV_R).toFloat()
        val cols = longArrayOf(T.DISC_C, T.DISC_B, T.DISC_A)
        val mono = text(extraBold, 16f, 0f, 0)
        for (k in 0 until 3) {
            val cx = (402 - T.AV_RIGHT - T.AV_R - k * T.AV_PITCH).toFloat()
            val r = T.AV_R.toFloat()
            c.drawCircle(cx, cy, r + 2.5f, fill(argb(T.PAGE)))
            c.drawCircle(cx, cy, r, fill(argb(cols[k])))
            val g = Paint(Paint.ANTI_ALIAS_FLAG)
            g.shader = LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                intArrayOf(0x47FFFFFF, 0x00FFFFFF, 0x2E000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r, g)
            mono.color = argb(if (k == 0) T.INK else T.DISC_INK)
            val s = T.MONOGRAMS[2 - k]
            val wd = textWidth(mono, s)
            val shift = if (k == 0) 0f else ((2 * T.AV_R - T.AV_PITCH) / 2).toFloat()
            c.drawText(s, cx - wd / 2 + shift, cy + 5.6f, mono)
        }
    }

    private fun drawBack(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(T.INK)
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val bx = T.BACK_X.toFloat()
        val by = T.BACK_Y.toFloat()
        val path = Path()
        path.moveTo(bx + 20, by + 12)
        path.lineTo(bx + 3, by + 12)
        path.moveTo(bx + 10, by + 5)
        path.lineTo(bx + 3, by + 12)
        path.lineTo(bx + 10, by + 19)
        c.drawPath(path, p)
    }

    private fun drawTitle(c: Canvas) {
        val ink = text(extraBold, T.TITLE_SIZE, T.TITLE_TR, argb(T.INK))
        val cap = T.TITLE_SIZE * 0.708f * fs
        val base = T.TITLE_CAPTOP.toFloat() + cap
        c.drawText(T.TITLE_1, T.TITLE_X.toFloat(), base, ink)
        c.drawText(T.TITLE_2, T.TITLE_X.toFloat(), base + T.TITLE_LH * fs, ink)
    }

    private fun drawLime(c: Canvas) {
        c.drawPath(limePath, fill(argb(T.LIME)))
        c.save()
        c.clipPath(limePath)
        c.translate(T.LIME_X.toFloat(), T.LIME_Y.toFloat())
        val ink = text(extraBold, T.TILE_SIZE, T.TILE_TR, argb(T.LIME_INK))
        val lh = T.TILE_LH * fs
        val first = 18f + T.TILE_SIZE * 0.708f * fs
        c.drawText(T.PROTECT_1, 18f, first, ink)
        c.drawText(T.PROTECT_2, 18f, first + lh, ink)

        val cx = 18f
        val cy = 88f
        val cw = 136f
        val ch = 80f
        c.drawRoundRect(RectF(cx, cy + 5, cx + cw, cy + 5 + ch), 11f, 11f, fill(argb(T.SHADOW, 0.42f)).apply { maskFilter = blur(5f) })
        val card = RectF(cx, cy, cx + cw, cy + ch)
        c.drawRoundRect(card, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx, cy, cx + cw, cy + ch, argb(T.CARD_TOP), argb(T.CARD_BOTTOM), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(card, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, cy, cx + cw * 0.9f, cy + ch,
                intArrayOf(0x33FFFFFF, 0x00FFFFFF, 0x29788CFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        })
        c.drawText(T.BANK, cx + 12, cy + 26, text(extraBold, 15f, -0.01f, argb(T.INK, 0.82f)))
        val url = text(semiBold, 7f, 0.06f, argb(T.INK, 0.5f))
        c.drawText(T.CARD_URL, cx + cw - 12 - textWidth(url, T.CARD_URL), cy + ch - 10, url)
        val film = RectF(cx - 5, cy - 5, cx + cw + 5, cy + ch + 5)
        c.drawRoundRect(film, 15f, 15f, fill(0x12EBF5FF))
        c.drawRoundRect(film, 15f, 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8CF0F8FF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
        })
        val crease = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x38FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            strokeCap = Paint.Cap.ROUND
        }
        val pb = Path()
        pb.moveTo(cx - 3, cy + 14)
        pb.cubicTo(cx + 22, cy + 6, cx + 40, cy + 22, cx + 64, cy - 2)
        pb.moveTo(cx + cw + 3, cy + ch - 18)
        pb.cubicTo(cx + cw - 22, cy + ch - 8, cx + cw - 40, cy + ch - 24, cx + cw - 70, cy + ch + 3)
        pb.moveTo(cx + 6, cy + ch + 3)
        pb.cubicTo(cx + 18, cy + ch - 10, cx + 30, cy + ch - 4, cx + 44, cy + ch + 3)
        c.drawPath(pb, crease)
        c.drawOval(RectF(cx + 6, cy + 4, cx + 40, cy + 14), fill(argb(0xFFFFFF, 0.8f * 0.35f)).apply { maskFilter = blur(3f) })
        c.restore()
    }

    private fun drawMagentaBase(c: Canvas) {
        c.drawPath(magentaPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                T.MAG_X.toFloat(), T.MAG_Y.toFloat(), (T.MAG_X + T.MAG_W * 0.25).toFloat(), (T.MAG_Y + T.MAG_H).toFloat(),
                argb(T.MAGENTA_TOP), argb(T.MAGENTA_BOTTOM), Shader.TileMode.CLAMP,
            )
        })
        c.save()
        c.clipPath(magentaPath)
        c.translate(T.MAG_X.toFloat(), T.MAG_Y.toFloat())
        val ink = text(semiBold, T.SMALL_SIZE, T.SMALL_TR, argb(T.INK))
        val lh = T.SMALL_LH * fs
        val base2 = T.MAG_H.toFloat() - 12f
        c.drawText(T.EXCHANGE_1, 16f, base2 - lh, ink)
        c.drawText(T.EXCHANGE_2, 16f, base2, ink)
        c.restore()
    }

    private fun drawNavy(c: Canvas, bottom: Double) {
        val h = max(bottom + 48 - T.NAVY_Y, T.NAVY_H)
        val path = squircle(T.NAVY_X, T.NAVY_Y, T.NAVY_W, h, T.R_TILE)
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                T.NAVY_X.toFloat(), T.NAVY_Y.toFloat(), (T.NAVY_X + T.NAVY_W * 0.3).toFloat(), (T.NAVY_Y + 260).toFloat(),
                argb(T.NAVY_TOP), argb(T.NAVY_BOTTOM), Shader.TileMode.CLAMP,
            )
        })
        c.save()
        c.clipPath(path)
        c.translate(T.NAVY_X.toFloat(), T.NAVY_Y.toFloat())
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1A96AAFF
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val gb = Path()
        val gh = 640f
        for (i in -48 until 22) {
            gb.moveTo(i * 12f, 0f)
            gb.lineTo(i * 12f + gh * 0.9f, gh)
            gb.moveTo(i * 12f + gh * 0.9f, 0f)
            gb.lineTo(i * 12f, gh)
        }
        c.drawPath(gb, g)
        val w = text(extraBold, 30f, -0.02f, argb(T.INK, 0.5f)).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        c.drawText(T.BANK, 18f, (T.NAVY_H - 22).toFloat(), w)
        c.restore()
    }

    private fun drawPremium(c: Canvas) {
        val ink = text(extraBold, T.PREMIUM_SIZE, T.PREMIUM_TR, argb(T.INK))
        val lh = T.PREMIUM_LH * fs
        val first = (T.PREMIUM_Y + 6).toFloat() + T.PREMIUM_SIZE * 0.708f * fs
        c.drawText(T.PREMIUM_1, T.PREMIUM_X.toFloat(), first, ink)
        c.drawText(T.PREMIUM_2, T.PREMIUM_X.toFloat(), first + lh, ink)
        c.drawText(T.PREMIUM_3, T.PREMIUM_X.toFloat(), first + 2 * lh, ink)
    }

    // ---- the live layer -------------------------------------------------------------------------------------------------

    private val xcoinShadow = fill(argb(T.COIN_SHADOW, 0.45f)).apply { maskFilter = blur(9f) }
    private val xcoinPaint = Paint()
    private val pilePaint = Paint()
    private val bloomShader by lazy {
        val gr = (T.COIN_R * 2.3).toFloat()
        RadialGradient(0f, 0f, gr, intArrayOf(argb(T.CYAN, 0.28f), argb(T.CYAN, 0f)), null, Shader.TileMode.CLAMP)
    }
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val pillShadowPaint = fill(0).apply { maskFilter = blur(7f) }
    private val pillBody by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, T.PILL_Y.toFloat(), 0f, (T.PILL_Y + T.PILL_H).toFloat(), argb(T.PILL_TOP), argb(T.PILL_BOTTOM), Shader.TileMode.CLAMP)
        }
    }
    private val pillLine by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(
                (T.PILL_X + 22).toFloat(), 0f, (T.PILL_X + T.PILL_W - 22).toFloat(), 0f,
                intArrayOf(argb(T.CYAN, 0f), argb(T.CYAN, 1f), argb(T.CYAN, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }
    private val white = text(semiBold, T.COPY_SIZE, T.COPY_TR, argb(T.INK))
    private val amountPaint = text(extraBold, T.AMOUNT_SIZE, 0f, 0)
    private val collectPaint = text(semiBold, T.PILL_SIZE, T.PILL_TR, argb(T.PILL_INK))
    private val digitAdv = 0.674f * T.AMOUNT_SIZE * fs + T.AMOUNT_TR * T.AMOUNT_SIZE * fs
    private val dollarAdv = 0.666f * T.AMOUNT_SIZE * fs + T.AMOUNT_TR * T.AMOUNT_SIZE * fs
    private val dotAdv = 0.338f * T.AMOUNT_SIZE * fs + T.AMOUNT_TR * T.AMOUNT_SIZE * fs
    private val amountSize = T.AMOUNT_SIZE * fs

    private fun drawDigit(c: Canvas, q: Double, from: Double, x: Float, base: Float, alpha: Double, color: Int) {
        if (alpha < 0.01) return
        val fl = floor(q)
        val frac = q - fl
        val roll = if (from >= 1) 0.0 else cpcSmooth(from, 1.0, frac)
        val d0 = (((fl % 10) + 10) % 10).toInt()
        val d1 = (d0 + 1) % 10
        val hh = amountSize * 0.3f
        val a0 = alpha * (1 - cpcSmooth(0.0, 0.6, roll))
        val a1 = alpha * cpcSmooth(0.4, 1.0, roll)
        val rgb = color and 0xFFFFFF
        if (a0 > 0.003) {
            amountPaint.color = ((a0 * 255).toInt().coerceIn(0, 255) shl 24) or rgb
            amountPaint.maskFilter = if (roll > 0.02) blur((roll * amountSize * 0.05).toFloat()) else null
            c.drawText(d0.toString(), x, base - (roll * hh).toFloat(), amountPaint)
        }
        if (a1 > 0.003) {
            amountPaint.color = ((a1 * 255).toInt().coerceIn(0, 255) shl 24) or rgb
            amountPaint.maskFilter = if (roll < 0.98) blur(((1 - roll) * amountSize * 0.05).toFloat()) else null
            c.drawText(d1.toString(), x, base + ((1 - roll) * hh).toFloat(), amountPaint)
        }
        amountPaint.maskFilter = null
    }

    private fun drawAmount(c: Canvas, cents: Double, x0: Float, base: Float, color: Int) {
        val v = max(cents, 0.0)
        val dollars = v / 100
        val nd = 1 + cpcSmooth(9.5, 10.0, dollars) + cpcSmooth(99.5, 100.0, dollars) + cpcSmooth(999.5, 1000.0, dollars)
        val xDot = x0 + dollarAdv + nd.toFloat() * digitAdv
        amountPaint.color = color
        amountPaint.letterSpacing = 0f
        c.drawText("$", x0, base, amountPaint)
        c.drawText(".", xDot, base, amountPaint)
        for (k in 0 until 6) {
            val place = 10.0.pow(k)
            val x = if (k < 2) xDot + dotAdv + (1 - k) * digitAdv else xDot - (k - 1) * digitAdv
            val alpha = when (k) {
                3 -> cpcSmooth(9.5, 10.0, dollars)
                4 -> cpcSmooth(99.5, 100.0, dollars)
                5 -> cpcSmooth(999.5, 1000.0, dollars)
                else -> 1.0
            }
            drawDigit(c, v / place, if (k == 0) 1.0 else if (k == 1) 0.7 else 0.97, x, base, alpha, color)
        }
    }

    private fun xcoinPose(theta: Double, out: DoubleArray) {
        val s = sin(theta)
        val k = cos(theta)
        val lean = CpcXCoin.tilt
        var ax = s
        var ay = -k * sin(lean)
        val az = k * cos(lean)
        var face = 0.0
        if (az < 0) {
            ax = -ax
            ay = -ay
            face = 1.0
        }
        out[0] = ax
        out[1] = ay
        out[2] = face
    }

    private val pose = DoubleArray(3)

    private fun drawMagentaCoin(c: Canvas, theta: Double, upx: Float) {
        c.save()
        c.clipPath(magentaPath)
        c.translate(T.MAG_X.toFloat(), T.MAG_Y.toFloat())
        xcoinPose(theta, pose)
        val wide = (abs(cos(theta)) * T.X_R + T.X_H).toFloat()
        c.save()
        c.translate((T.X_CX + 12).toFloat(), (T.X_CY + 16).toFloat())
        c.drawOval(RectF(-wide, -T.X_R.toFloat(), wide, T.X_R.toFloat()), xcoinShadow)
        c.restore()
        val xs = xcoin
        if (xs != null) {
            xs.apply(upx, T.X_CX.toFloat(), T.X_CY.toFloat(), T.X_H.toFloat(), pose[0].toFloat(), pose[1].toFloat(), pose[2].toFloat())
            xcoinPaint.shader = xs.shader
            c.drawRect(0f, 0f, T.MAG_W.toFloat(), T.MAG_H.toFloat(), xcoinPaint)
        } else {
            val face = if (pose[2] > 0.5) T.COPPER else T.PINK
            drawFallbackCoin(c, T.X_CX.toFloat(), T.X_CY.toFloat(), T.X_R.toFloat(), T.X_H.toFloat(), pose[0].toFloat(), pose[1].toFloat(), face, T.PINK_RIM, 0f, false)
        }
        c.restore()
    }

    private fun drawFallbackCoin(
        c: Canvas, x: Float, y: Float, r: Float, hh: Float, ax: Float, ay: Float, faceHex: Long, rimHex: Long, glow: Float, mesh: Boolean,
    ) {
        val az = sqrt(max(1f - ax * ax - ay * ay, 0.0004f))
        val ang = Math.toDegrees(Math.atan2(ay.toDouble(), ax.toDouble())).toFloat()
        val sinT = sqrt(ax * ax + ay * ay)
        c.save()
        c.translate(x, y)
        // the wall: the cap's silhouette pushed back along the axis
        val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(rimHex) }
        c.save()
        c.rotate(ang)
        val back = -hh * 2f * sinT
        c.drawOval(RectF(back - r * az, -r, back + r * az, r), wall)
        c.drawOval(RectF(-r * az, -r, r * az, r), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(-r * az, -r, r * az, r, argb(faceHex), argb(if (mesh) faceHex else rimHex, 1f), Shader.TileMode.CLAMP)
        })
        if (glow > 0.02f) c.drawOval(RectF(-r * az, -r, r * az, r), fill(argb(T.CYAN, min(glow, 1f) * 0.7f)))
        c.restore()
        c.restore()
    }

    private fun drawPileFallback(c: Canvas, a: DoubleArray, cfg: CoinPileCollectCfg) {
        val bg = Paint().apply { color = argb(T.TEAL) }
        c.drawRect(0f, 0f, T.TEAL_W.toFloat(), T.TEAL_H.toFloat(), bg)
        for (k in 0 until cfg.n) {
            val i = cfg.order[k]
            val o = i * CpcSTRIDE
            if (a[o + CpcALIVE] < 0.5) continue
            val x = a[o + CpcX].toFloat()
            val y = a[o + CpcY].toFloat()
            val z = a[o + CpcZ].toFloat()
            c.drawOval(
                RectF(x + 4 + z * 0.15f - 30f, y + 5 + z * 0.2f - 28f, x + 4 + z * 0.15f + 30f, y + 5 + z * 0.2f + 28f),
                fill(argb(T.SHADOW, 0.3f)).apply { maskFilter = blur(5f) },
            )
            val face = a[o + CpcFACE] > 0.5
            drawFallbackCoin(
                c, x, y - z * 0.1f, T.COIN_R.toFloat(), T.COIN_H.toFloat(), a[o + CpcAX].toFloat(), a[o + CpcAY].toFloat(),
                if (face) T.COIN_FACE_B else T.COIN_FACE_A, T.COIN_RIM, a[o + CpcGLOW].toFloat(), face,
            )
        }
    }

    private fun drawHopper(c: Canvas, a: DoubleArray, cfg: CoinPileCollectCfg, upx: Float) {
        c.save()
        c.clipPath(tealPath)
        val ps = pile
        if (ps != null) {
            ps.apply(a, cfg.n, upx)
            pilePaint.shader = ps.shader
            c.drawRect(0f, 0f, T.TEAL_W.toFloat(), T.TEAL_H.toFloat(), pilePaint)
        } else {
            drawPileFallback(c, a, cfg)
        }
        // the haze of the lit coins
        for (i in 0 until cfg.n) {
            val o = i * CpcSTRIDE
            val g = a[o + CpcGLOW]
            if (g < 0.06 || a[o + CpcALIVE] < 0.5) continue
            c.save()
            c.translate(a[o + CpcX].toFloat(), a[o + CpcY].toFloat())
            bloomPaint.shader = bloomShader
            bloomPaint.alpha = (min(g, 1.0) * 255).toInt()
            c.drawCircle(0f, 0f, (T.COIN_R * 2.3).toFloat(), bloomPaint)
            c.restore()
        }

        val ctl = CPC_CTL
        val press = a[ctl + C_PRESS]
        val bulge = a[ctl + C_BULGE]
        val pillA = a[ctl + C_PILLA]
        val lit = a[ctl + C_LIT]
        val flow = a[ctl + C_FLOW]
        val cents = a[ctl + C_CENTS]

        c.drawText(T.EARNED, T.COPY_X.toFloat(), T.EARNED_BASE.toFloat(), white)
        val kLit = cpcClamp01(lit)
        fun mixCh(h0: Long, h1: Long, sh: Int): Int = ((cpcMix(((h0 shr sh) and 255).toDouble(), ((h1 shr sh) and 255).toDouble(), kLit)).toInt()) and 255
        val amberRgb = 0xFF000000.toInt() or (mixCh(T.AMBER, T.AMBER_LIT, 16) shl 16) or (mixCh(T.AMBER, T.AMBER_LIT, 8) shl 8) or mixCh(T.AMBER, T.AMBER_LIT, 0)
        drawAmount(c, cents, T.COPY_X.toFloat(), T.AMOUNT_BASE.toFloat(), amberRgb)
        c.drawText(T.PERIOD, T.COPY_X.toFloat(), T.PERIOD_BASE.toFloat(), white)

        val cx = (T.PILL_X + T.PILL_W / 2).toFloat()
        val cy = (T.PILL_Y + T.PILL_H / 2).toFloat()
        val sx = (1 - (1 - CpcPill.pressScale) * press).toFloat()
        val sy = (sx * (1 + bulge)).toFloat()
        c.save()
        c.translate(cx, cy)
        c.scale(sx, sy)
        c.translate(-cx, -cy)
        val px = T.PILL_X.toFloat()
        val py = T.PILL_Y.toFloat()
        val pw = T.PILL_W.toFloat()
        val ph = T.PILL_H.toFloat()
        pillShadowPaint.color = argb(T.SHADOW, (0.5 * pillA * (1 - 0.6 * press)).toFloat())
        c.drawRoundRect(RectF(px + 2, py + 6, px + 2 + pw - 4, py + 6 + ph - 2), ph / 2, ph / 2, pillShadowPaint)
        pillBody.alpha = (pillA * 255).toInt().coerceIn(0, 255)
        c.drawRoundRect(RectF(px, py, px + pw, py + ph), ph / 2, ph / 2, pillBody)
        if (flow > 0.01 && lit > 0.01) {
            pillLine.alpha = (CpcPill.lineMax * flow * lit * 255).toInt().coerceIn(0, 255)
            c.drawLine(px + 22, py + 0.5f, px + pw - 22, py + 0.5f, pillLine)
        }
        collectPaint.alpha = (pillA * 255).toInt().coerceIn(0, 255)
        c.drawText(T.COLLECT, cx - textWidth(collectPaint, T.COLLECT) / 2, cy + (T.PILL_SIZE * 0.708f * fs) / 2, collectPaint)
        c.restore()
        c.restore()
    }

    // ---- the window -----------------------------------------------------------------------------------------------------

    private fun pageFallback(c: Canvas, w: Float, h: Float) {
        c.drawRect(0f, 0f, w, h, Paint().apply { color = argb(T.PAGE) })
        val r = 0.87f * h
        val stops = FloatArray(9) { it / 8f }
        val cols = IntArray(9) { i ->
            val k = i / 8f
            val g = Math.exp(-((1.5f * k).toDouble().pow(2.0))).toFloat()
            val p0 = T.f3(T.PAGE)
            val p1 = T.f3(T.PAGE_GLOW)
            val ch = { j: Int -> ((p0[j] + (p1[j] - p0[j]) * g) * 255f).toInt().coerceIn(0, 255) }
            0xFF000000.toInt() or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
        }
        c.drawRect(0f, 0f, w, h, Paint().apply {
            shader = RadialGradient(-0.05f * w, 0.86f * h, r, cols, stops, Shader.TileMode.CLAMP)
        })
    }

    private var pageFade: Paint? = null
    private var pageFadeKey = ""

    fun draw(c: Canvas, l: T.Layout, a: DoubleArray, cfg: CoinPileCollectCfg, theta: Double, density: Float) {
        val w = l.w.toFloat()
        val h = l.h.toFloat()
        // page
        val pg = page
        if (pg != null) {
            pg.apply(w, h)
            c.drawRect(0f, 0f, w, h, pg.paint)
        } else pageFallback(c, w, h)

        // flat layer, cached per layout
        val bottom = (l.h - l.oy) / max(l.s, 0.01)
        val key = "${l.w}|${l.h}|${l.s}|${l.ox}|${l.oy}|$density|$fs"
        var sb = staticBitmap
        if (sb == null || key != staticKey) {
            sb?.recycle()
            val bw = max((w * density).toInt(), 1)
            val bh = max((h * density).toInt(), 1)
            sb = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val sc = Canvas(sb)
            sc.scale(density, density)
            sc.translate(l.ox.toFloat(), l.oy.toFloat())
            sc.scale(l.s.toFloat(), l.s.toFloat())
            drawStatic(sc, bottom)
            staticBitmap = sb
            staticKey = key
        }
        c.save()
        c.scale(1f / density, 1f / density)
        c.drawBitmap(sb, 0f, 0f, null)
        c.restore()

        val upx = (1.0 / (max(l.s, 0.01) * density)).toFloat()
        c.save()
        c.translate(l.ox.toFloat(), l.oy.toFloat())
        c.scale(l.s.toFloat(), l.s.toFloat())
        drawMagentaCoin(c, theta, upx)
        c.save()
        c.translate(T.TEAL_X.toFloat(), T.TEAL_Y.toFloat())
        drawHopper(c, a, cfg, upx)
        c.restore()
        c.restore()

        // scroll edge: the page itself fades in over the last 96 pt
        val copyBottom = l.oy + (T.PREMIUM_Y + 6 + T.PREMIUM_SIZE * 0.708 * fs + 2 * T.PREMIUM_LH * fs + 8) * l.s
        val fadeH = max(16 * l.s, min(96 * l.s, l.h - copyBottom)).toFloat()
        val fkey = "$w|$h|$fadeH"
        var fp = pageFade
        if (fp == null || fkey != pageFadeKey) {
            val grad = LinearGradient(
                0f, h - fadeH, 0f, h, intArrayOf(0x00FFFFFF, 0xB8FFFFFF.toInt(), 0xFFFFFFFF.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            fp = Paint().apply {
                shader = if (pg != null) ComposeShader(pg.shader, grad, PorterDuff.Mode.DST_IN) else grad
                if (pg == null) {
                    // below API 33: the plain page colour under the same ramp
                    shader = ComposeShader(LinearGradient(0f, 0f, 0f, h, argb(T.PAGE), argb(T.PAGE), Shader.TileMode.CLAMP), grad, PorterDuff.Mode.DST_IN)
                }
            }
            pageFade = fp
            pageFadeKey = fkey
        }
        c.drawRect(0f, h - fadeH, w, h, fp)
    }

    // ---- shaders ----------------------------------------------------------------------------------------------------------


    @RequiresApi(33)
    private fun RuntimeShader.common(upx: Float, r: Float, hh: Float, k: Float, albA: Long, albB: Long, albRim: Long, key: FloatArray, bounce: FloatArray, room: FloatArray) {
        setFloatUniform("uCell", CpcEMBOSS_CELL.toFloat())
        setFloatUniform("uP3", 0f)
        setFloatUniform("uPx", upx)
        setFloatUniform("uR", r)
        setFloatUniform("uH", hh)
        setFloatUniform("uK", k)
        setFloatUniform("uAlbA", T.f3(albA))
        setFloatUniform("uAlbB", T.f3(albB))
        setFloatUniform("uAlbRim", T.f3(albRim))
        setFloatUniform("uEnvKey", key)
        setFloatUniform("uEnvBounce", bounce)
        setFloatUniform("uEnvRoom", room)
    }

    @RequiresApi(33)
    private inner class PileShader(atlas: Bitmap) {
        val shader = RuntimeShader(cpcPileSource())
        private val coinA = FloatArray(4)
        private val coinB = FloatArray(4)

        init {
            shader.setInputShader("emb", BitmapShader(atlas, Shader.TileMode.DECAL, Shader.TileMode.DECAL))
            shader.setFloatUniform("uBg", T.f3(T.TEAL))
            shader.setFloatUniform("uTile", T.TEAL_W.toFloat(), T.TEAL_H.toFloat())
            shader.setFloatUniform("uAmp", 1f)
        }

        fun apply(a: DoubleArray, n: Int, upx: Float) {
            shader.common(
                upx, T.COIN_R.toFloat(), T.COIN_H.toFloat(), 1f, T.COIN_FACE_A, T.COIN_FACE_B, T.COIN_RIM,
                floatArrayOf(0.72f, 0.95f, 1.0f), floatArrayOf(0.14f, 0.3f, 0.8f), floatArrayOf(0.01f, 0.04f, 0.06f),
            )
            shader.setFloatUniform("uN", n.toFloat())
            for (i in 0 until 32) {
                if (i < n) {
                    val o = i * CpcSTRIDE
                    coinA[0] = a[o + CpcX].toFloat()
                    coinA[1] = a[o + CpcY].toFloat()
                    coinA[2] = a[o + CpcZ].toFloat()
                    coinA[3] = (a[o + CpcGLOW] + 2 * a[o + CpcFACE]).toFloat()
                    coinB[0] = a[o + CpcAX].toFloat()
                    coinB[1] = a[o + CpcAY].toFloat()
                    coinB[2] = a[o + CpcPHI].toFloat()
                    coinB[3] = a[o + CpcALIVE].toFloat()
                } else {
                    coinA.fill(0f)
                    coinB.fill(0f)
                }
                shader.setFloatUniform("coinA$i", coinA)
                shader.setFloatUniform("coinB$i", coinB)
            }
        }
    }

    @RequiresApi(33)
    private inner class XCoinShader(atlas: Bitmap) {
        val shader = RuntimeShader(cpcXCoinSource())

        init {
            shader.setInputShader("emb", BitmapShader(atlas, Shader.TileMode.DECAL, Shader.TileMode.DECAL))
            shader.setFloatUniform("uShadowOn", 0f)
        }

        fun apply(upx: Float, cx: Float, cy: Float, hh: Float, ax: Float, ay: Float, face: Float) {
            shader.common(
                upx, T.X_R.toFloat(), T.X_H.toFloat(), (T.X_R / T.COIN_R).toFloat(), T.PINK, T.COPPER, T.PINK_RIM,
                floatArrayOf(1.0f, 0.86f, 0.94f), floatArrayOf(0.5f, 0.2f, 0.62f), floatArrayOf(0.12f, 0.04f, 0.12f),
            )
            shader.setFloatUniform("uCoin", cx, cy, hh, 1f)
            shader.setFloatUniform("uAxis", ax, ay, 0f, face)
        }
    }

    @RequiresApi(33)
    private inner class PageShader {
        val shader = RuntimeShader(cpcPageSource())
        val paint = Paint().apply { shader = this@PageShader.shader }
        private var w = -1f
        private var h = -1f

        init {
            shader.setFloatUniform("uP3", 0f)
            shader.setFloatUniform("uPage", T.f3(T.PAGE))
            shader.setFloatUniform("uGlow", T.f3(T.PAGE_GLOW))
            shader.setFloatUniform("uGlow2", T.f3(T.PAGE_GLOW2))
        }

        fun apply(width: Float, height: Float) {
            if (width == w && height == h) return
            w = width
            h = height
            shader.setFloatUniform("uSize", width, height)
        }
    }
}
