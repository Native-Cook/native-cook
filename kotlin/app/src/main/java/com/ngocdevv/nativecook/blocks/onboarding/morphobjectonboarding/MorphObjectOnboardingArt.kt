package com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding

import android.content.Context
import android.graphics.*
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.Live
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.mix
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.smooth
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.pageRound
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingTheme as U

/** Cached sharp assets and glyph masks; native Canvas shares Compose's Skia renderer. */
internal class MorphObjectOnboardingArt(context: Context) {
    private val atlas = image(context, "morph-atlas.webp")
    private val keys = listOf("clock", "laptop", "book").map { image(context, "morph-$it.webp") }
    private val faces = listOf("Regular", "Medium", "Bold").map { weight ->
        Typeface.createFromAsset(context.assets, "morphobjectonboarding/morphobjectonboarding_Poppins-$weight.ttf")
    }
    private val star = requireNotNull(PathParser.createPathFromPathData("M0 -10 C1.1 -3.4 3.4 -1.1 10 0 C3.4 1.1 1.1 3.4 0 10 C-1.1 3.4 -3.4 1.1 -10 0 C-3.4 -1.1 -1.1 -3.4 0 -10 Z"))
    private val masks = mutableMapOf<String, Bitmap>()
    private val shadow = Bitmap.createBitmap(240, 100, Bitmap.Config.ARGB_8888)
    private val shadowCanvas = Canvas(shadow)
    private val shadowPaint = paint(0).apply { maskFilter = blur(9.0) }
    private val tau = 2 * PI
    private fun image(context: Context, name: String) = context.assets.open("morphobjectonboarding/morphobjectonboarding_$name").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    private fun color(rgb: Int, alpha: Double = 1.0) = (alpha.coerceIn(0.0, 1.0).times(255).roundToInt() shl 24) or rgb
    private fun paint(rgb: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = color(rgb, alpha) }
    // Android's radius maps to Skia sigma as radius * 0.57735 + 0.5.
    private fun blur(sigma: Double) = BlurMaskFilter(max(0.001, (sigma - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
    private fun blend(a: Int, b: Int, k: Double): Int {
        return ((mix(((a shr 16) and 255).toDouble(), ((b shr 16) and 255).toDouble(), k).roundToInt()) shl 16) or
        ((mix(((a shr 8) and 255).toDouble(), ((b shr 8) and 255).toDouble(), k).roundToInt()) shl 8) or
        mix((a and 255).toDouble(), (b and 255).toDouble(), k).roundToInt()
    }

    private fun label(
        c: Canvas,
        text: String,
        face: Int,
        size: Double,
        x: Double,
        cy: Double,
        anchor: Double,
        rgb: Int,
        alpha: Double = 1.0,
        sigma: Double = 0.0,
    ) {
        val p = paint(rgb, alpha).apply {
            typeface = faces[face]
            textSize = size.toFloat()
            isSubpixelText = true
            isLinearText = true
            fontFeatureSettings = "'kern' 0, 'liga' 0"
        }

        val width = p.measureText(text)
        if (sigma <= 0.05) {
            c.drawText(text, (x - width * anchor).toFloat(), (cy + size * 0.36).toFloat(), p)
            return
        }

        // MaskFilter is ignored on hardware Canvas. A reusable software surface preserves blur on API 26+.
        val key = "$face:$size:$text"
        val bitmap = masks.getOrPut(key) {
            Bitmap.createBitmap(ceil(width + 64).toInt(), ceil(size * 2 + 64).toInt(), Bitmap.Config.ARGB_8888)
        }

        bitmap.eraseColor(Color.TRANSPARENT)
        val software = Canvas(bitmap)
        p.maskFilter = blur(sigma)
        software.drawText(text, 32f, (32 + size).toFloat(), p)
        c.drawBitmap(bitmap, (x - width * anchor - 32).toFloat(), (cy + size * 0.36 - 32 - size).toFloat(), null)
    }

    private fun sparkle(c: Canvas, x: Double, y: Double, half: Double, rot: Double, rgb: Int, alpha: Double) {
        if (half < 0.3) return
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        c.rotate(rot.toFloat())
        c.scale((half / 10).toFloat(), (half / 10).toFloat())
        c.drawPath(star, paint(rgb, alpha))
        c.restore()
    }

    fun drawScene(c: Canvas, width: Float, height: Float, live: Live) {
        c.drawColor(color(U.BG))
        val s = width / U.REF_W
        val dy = height - U.REF_H * s
        c.save()
        c.translate(0f, dy.toFloat())
        c.scale(s.toFloat(), s.toFloat())
        val p = live.p.coerceIn(0.0, 2.0)
        val i = min(1, floor(p).toInt())
        val glow = blend(U.glows[i], U.glows[i + 1], p - i)
        val cx = U.OBJECT_X + U.OBJECT_SIZE / 2
        val cy = U.OBJECT_Y + U.OBJECT_SIZE / 2
        val gp = paint(glow).apply {
            shader = RadialGradient(
                cx.toFloat(), cy.toFloat(), 190f,
                intArrayOf(color(glow, 0.16 + 0.04 * sin(tau * 2 * live.amb / 9)), color(glow, 0.0)),
                null, Shader.TileMode.CLAMP,
            )
        }

        c.drawCircle(cx.toFloat(), cy.toFloat(), 190f, gp)
        val w0 = 1 - smooth(p / 0.45)
        val tw = 0.5 + 0.5 * sin(tau * 3 * live.amb / 9)
        sparkle(c, U.sparkleX, U.sparkleY, 30 * (0.82 + 0.18 * tw) * w0, 8 * sin(tau * live.amb / 9), U.SPARKLE, w0)
        if (live.arrive >= 0 && live.arrive < 0.75) {
            val q = live.arrive / 0.75
            val page = pageRound(p).toInt()
            for (j in 0..6) {
                val a = tau * j / 7 + page
                val dist = (95 + 25.0 * ((j * 37) % 7) / 7) * (1 - (1 - q).pow(3))
                val life = sin(PI * q)
                sparkle(c, cx + cos(a) * dist, cy + sin(a) * dist * 0.85, (4 + (j % 3) * 2) * life, 90 * q, blend(U.glows[page], 0xFFFFFF, 0.5), life)
            }
        }

        drawObject(c, live)
        var dx = U.dotsX
        for (j in 0..2) {
            val weight = smooth(1 - min(1.0, abs(p - j)))
            val w = mix(3.8, 25.0, weight)
            val h = mix(3.8, 3.6, weight)
            c.drawRoundRect(rect(dx, U.TOP_Y - h / 2, w, h), (h / 2).toFloat(), (h / 2).toFloat(), paint(blend(U.DOT, 0xFFFFFF, weight)))
            dx += w + 8.2
        }

        label(c, "Skip", 0, 13.5, U.skipX, U.TOP_Y, 1.0, 0xFFFFFF, 1 - smooth((p - 1.4) / 0.5))
        drawCard(c, live, s)
        c.restore()
    }

    private fun drawObject(c: Canvas, live: Live) {
        val p = live.p.coerceIn(0.0, 2.0)
        val over = live.p - p
        val bob = 4 * sin(tau * 2 * live.amb / 9)
        val tilt = 2 * sin(tau * live.amb / 9 + 0.6)
        var hop = 0.0
        var squash = 0.0
        var twist = 0.0
        if (live.hop >= 0) {
            val q = live.hop / 0.5
            if (q < 1) {
                hop = 16 * sin(PI * q)
                twist = 10 * sin(tau * q)
            } else squash = 0.08 * exp(-(live.hop - 0.5) / 0.14) * cos(tau * (live.hop - 0.5) / 0.22)
        }

        if (live.arrive >= 0) squash += 0.05 * exp(-live.arrive / 0.18) * cos(tau * live.arrive / 0.3)
        val cx = U.OBJECT_X + U.OBJECT_SIZE / 2
        val cy = U.OBJECT_Y + U.OBJECT_SIZE / 2
        val sw = 150 * (1 - hop / 60) * (1 - bob / 80)
        shadow.eraseColor(Color.TRANSPARENT)
        shadowCanvas.drawOval(rect(120 - sw / 2, 42.0, sw, 16.0), shadowPaint)
        c.drawBitmap(shadow, (cx - 120).toFloat(), (U.OBJECT_Y + U.OBJECT_SIZE * 0.86 - 42).toFloat(), paint(0, 0.35 * (1 - hop / 30)))
        c.save()
        c.translate((cx - over * 26).toFloat(), (cy + bob - hop).toFloat())
        c.rotate((tilt + twist - over * 14).toFloat())
        c.scale((1 + squash + abs(over) * 0.06).toFloat(), (1 - squash - abs(over) * 0.04).toFloat())
        c.translate((-U.OBJECT_SIZE / 2).toFloat(), (-U.OBJECT_SIZE / 2).toFloat())
        val dst = rect(0.0, 0.0, U.OBJECT_SIZE, U.OBJECT_SIZE)
        val fr = p * U.PER_PAGE
        val f0 = floor(fr).toInt()
        val f1 = min(U.FRAMES - 1, f0 + 1)
        val k = fr - f0
        val near = pageRound(p).toInt()
        val sharp = 1 - smooth(abs(p - near) / 0.07)
        fun source(frame: Int): Rect {
            val x = (frame % U.COLS) * U.FRAME_SIZE
            val y = (frame / U.COLS) * U.FRAME_SIZE
            return Rect(x, y, x + U.FRAME_SIZE, y + U.FRAME_SIZE)
        }

        if (sharp < 0.999) {
            c.drawBitmap(atlas, source(f0), dst, paint(0xFFFFFF))
            if (k > 0.002) c.drawBitmap(atlas, source(f1), dst, paint(0xFFFFFF, k))
        }

        if (sharp > 0.001) c.drawBitmap(keys[near], null, dst, paint(0xFFFFFF, sharp))
        c.restore()
        val mid = sin(PI * (p - floor(p)))
        if (mid > 0.02) {
            val flare = paint(0xffffff).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                shader = RadialGradient(cx.toFloat(), (cy + bob - hop).toFloat(), 120f, intArrayOf(color(0xffffff, 0.22 * mid), color(0xffffff, 0.0)), null, Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx.toFloat(), (cy + bob - hop).toFloat(), 120f, flare)
            for (k2 in 0 until 14) {
                val angle = tau * k2 / 14 + p * 3.2 + k2 * 0.4
                val radius = 70 + 30 * sin(k2 * 1.7) + 26 * mid
                val mote = paint(0xfffae6, 0.75 * mid * (0.5 + 0.5 * sin(k2 * 2.3))).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
                c.drawCircle((cx + cos(angle) * radius).toFloat(), (cy + bob - hop + sin(angle) * radius * 0.62).toFloat(), (1.4 + 1.2 * ((k2 * 0.37) % 1)).toFloat(), mote)
            }
        }
    }

    private fun drawCard(c: Canvas, live: Live, s: Double) {
        val p = live.p.coerceIn(0.0, 2.0)
        val bump = if (live.arrive >= 0) 0.008 * exp(-live.arrive / 0.2) * cos(tau * live.arrive / 0.35) else 0.0
        c.save()
        c.scale((1 + bump).toFloat(), (1 + bump).toFloat(), (U.REF_W / 2).toFloat(), U.cardY.toFloat())
        c.drawRoundRect(rect(0.0, U.cardY, U.REF_W, 874 - U.cardY + 60 / s), U.CARD_R.toFloat(), U.CARD_R.toFloat(), paint(0xFFFFFF))
        c.restore()
        for (i in 0..2) {
            val d = i - p
            val a = 1 - min(1.0, abs(d) * 1.7)
            if (a <= 0.003) continue
            for (line in 0..1) {
                label(c, U.titles[i][line], 2, 26.0, 201 + d * (70 + line * 14), U.titleY[line], 0.5, U.INK, a, abs(d) * 7)
                label(c, U.subtitles[i][line], 0, 15.6, 201 + d * (96 + line * 10), U.subY[line], 0.5, U.SUB, a, abs(d) * 7)
            }
        }

        c.save()
        c.translate((U.buttonX + U.buttonW / 2).toFloat(), (U.buttonY + U.buttonH / 2).toFloat())
        val bs = (1 - 0.04 * live.press).toFloat()
        c.scale(bs, bs)
        c.drawRoundRect(rect(-U.buttonW / 2, -U.buttonH / 2, U.buttonW, U.buttonH), (U.buttonH / 2).toFloat(), (U.buttonH / 2).toFloat(), paint(U.BG))
        val k = smooth((p - 1.35) / 0.55)
        if (k < 0.999) label(c, "Continue Now", 1, 16.0, 0.0, -8 * k, 0.5, 0xFFFFFF, 1 - k)
        if (k > 0.001) label(c, "Let's start", 1, 16.0, 0.0, 8 * (1 - k), 0.5, 0xFFFFFF, k)
        c.restore()
    }
}
