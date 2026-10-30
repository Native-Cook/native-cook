package com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveTheme.C
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveTheme.Copy
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveTheme.UI
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveTheme.cats
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.Frame
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.FLY
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.LOOP
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.NEVER
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.clamp01
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.mix
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.smooth
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.spring
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.easeOut
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.easeInOut
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.panelK
import com.ngocdevv.nativecook.blocks.pickers.cashbackpickfive.CashbackPickFiveMotion.slotPos

/** Geometry stays in the reference's 736-pixel pin coordinates; only the outer Canvas scales. */
internal class CashbackPickFiveArt(context: Context) {
    private val regular = Typeface.createFromAsset(context.assets, "cashbackpickfive/cashbackpickfive_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "cashbackpickfive/cashbackpickfive_Inter-Medium.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "cashbackpickfive/cashbackpickfive_Inter-Bold.ttf")
    private val heavy = Typeface.createFromAsset(context.assets, "cashbackpickfive/cashbackpickfive_Inter-ExtraBold.ttf")
    private val fonts = mapOf(
        "brand" to font(heavy, 33f),
        "title" to font(bold, 44f),
        "row" to font(regular, 29f),
        "sub" to font(regular, 21f),
        "banner" to font(regular, 30f),
        "chip" to font(regular, 21f),
        "section" to font(medium, 30f),
        "button" to font(medium, 32f),
        "connect" to font(regular, 25f),
        "fine" to font(regular, 17f),
        "glyph" to font(bold, 26f)
    )
    private lateinit var canvas: Canvas
    private val textWidths = mutableMapOf<Pair<String, String>, Double>()
    // Blur masks are rasterized once: hardware Canvas ignores BlurMaskFilter for primitives.
    private val shadowMasks = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 48
    }
    private val shadowNodes = mutableListOf<RenderNode>()
    private var shadowIndex = 0

    private fun font(face: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        isSubpixelText = true
    }

    private fun argb(color: Int, alpha: Double = 1.0) = (clamp01(alpha) * 255).roundToInt().shl(24) or color

    private fun paint(color: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = argb(color, alpha)
    }

    private fun stroke(color: Int, width: Number, alpha: Double = 1.0) = paint(color, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble() + w.toDouble()).toFloat(), (y.toDouble() + h.toDouble()).toFloat())

    private fun rr(x: Number, y: Number, w: Number, h: Number, r: Number, fill: Paint) {
        canvas.drawRoundRect(rect(x, y, w, h), r.toFloat(), r.toFloat(), fill)
    }

    private fun circle(x: Number, y: Number, r: Number, fill: Paint) {
        canvas.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), fill)
    }

    private fun line(x: Number, y: Number, x2: Number, y2: Number, linePaint: Paint) {
        canvas.drawLine(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), linePaint)
    }

    private fun translate(x: Number, y: Number) {
        canvas.translate(x.toFloat(), y.toFloat())
    }

    private fun scale(x: Number, y: Number = x) {
        canvas.scale(x.toFloat(), y.toFloat())
    }

    private fun text(s: String, x: Number, cy: Number, font: String, textPaint: Paint, ax: Double = 0.0): Double {
        val fontPaint = Paint(requireNotNull(fonts[font]))
        fontPaint.color = textPaint.color
        val w = width(s, font)
        canvas.drawText(s, (x.toDouble() - w * ax).toFloat(), (cy.toDouble() + fontPaint.textSize * 0.36).toFloat(), fontPaint)
        return w
    }

    @Suppress("DEPRECATION") // The bounds overload is available on the API 26 fallback.
    private fun width(s: String, font: String): Double = textWidths.getOrPut(Pair(s, font)) {
        // SkFont.measureText returns ink bounds, rather than the text advance.
        val path = Path()
        requireNotNull(fonts[font]).getTextPath(s, 0, s.length, 0f, 0f, path)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        bounds.width().toDouble()
    }

    private fun mixColor(a: Int, b: Int, k: Double, alpha: Double = 1.0): Paint {
        val red = mix(((a shr 16) and 255).toDouble(), ((b shr 16) and 255).toDouble(), k).roundToInt().coerceIn(0, 255)
        val green = mix(((a shr 8) and 255).toDouble(), ((b shr 8) and 255).toDouble(), k).roundToInt().coerceIn(0, 255)
        val blue = mix((a and 255).toDouble(), (b and 255).toDouble(), k).roundToInt().coerceIn(0, 255)
        return paint((red shl 16) or (green shl 8) or blue, alpha)
    }

    private fun gradient(x1: Number, y1: Number, x2: Number, y2: Number, colors: IntArray, stops: FloatArray? = null) = paint(0xFFFFFF).apply {
        shader = LinearGradient(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), colors, stops, Shader.TileMode.CLAMP)
    }

    private fun star4(x: Double, y: Double, r: Double, starPaint: Paint) {
        canvas.drawPath(Path().apply {
                moveTo(x.toFloat(), (y - r).toFloat())
                quadTo(x.toFloat(), y.toFloat(), (x + r).toFloat(), y.toFloat())
                quadTo(x.toFloat(), y.toFloat(), x.toFloat(), (y + r).toFloat())
                quadTo(x.toFloat(), y.toFloat(), (x - r).toFloat(), y.toFloat())
                quadTo(x.toFloat(), y.toFloat(), x.toFloat(), (y - r).toFloat())
                close()
            }, starPaint)
    }

    private fun plusMark(x: Double, y: Double, r: Double) {
        circle(x, y, r, gradient(x - r, y - r, x + r, y + r, intArrayOf(argb(0xFF7EB3), argb(0xB04CFF), argb(0x6A3BFF)), floatArrayOf(0f, 0.55f, 1f)))
        val w = stroke(0xFFFFFF, r * 0.26)
        canvas.save()
        translate(x, y)
        canvas.rotate(-18f)
        line(-r * 0.45, 0, r * 0.45, 0, w)
        line(0, -r * 0.45, 0, r * 0.45, w)
        canvas.restore()
    }

    private fun shadow(x: Double, y: Double, w: Double, h: Double, r: Double, sigma: Double, alpha: Double, round: Boolean) {
        val pad = ceil(sigma * 3).toInt()
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
            val index = shadowIndex++
            val node = if (index < shadowNodes.size) shadowNodes[index] else RenderNode("CashbackPickFive shadow").also {
                shadowNodes.add(it)
            }
            val left = floor(x).toInt() - pad
            val top = floor(y).toInt() - pad
            node.setPosition(left, top, ceil(x + w).toInt() + pad, ceil(y + h).toInt() + pad)
            node.setClipToBounds(false)
            node.setRenderEffect(RenderEffect.createBlurEffect(sigma.toFloat(), sigma.toFloat(), Shader.TileMode.DECAL))
            val shadowCanvas = node.beginRecording()
            shadowCanvas.translate(-left.toFloat(), -top.toFloat())
            val shadowPaint = paint(0x2A2F45, alpha)
            if (round) {
                shadowCanvas.drawRoundRect(rect(x, y, w, h), r.toFloat(), r.toFloat(), shadowPaint)
            }
            else shadowCanvas.drawCircle((x + w / 2).toFloat(), (y + h / 2).toFloat(), r.toFloat(), shadowPaint)
            node.endRecording()
            canvas.drawRenderNode(node)
            return
        }
        val key = "$w:$h:$r:$sigma:$round"
        val image = shadowMasks.getOrPut(key) {
            val bitmap = Bitmap.createBitmap(ceil(w).toInt() + pad * 2, ceil(h).toInt() + pad * 2, Bitmap.Config.ARGB_8888)
            val shadowCanvas = Canvas(bitmap)
            val shadowPaint = paint(0x2A2F45).apply {
                maskFilter = BlurMaskFilter(((sigma - 0.5) / 0.57735).toFloat().coerceAtLeast(0.001f), BlurMaskFilter.Blur.NORMAL)
            }
            if (round) {
                shadowCanvas.drawRoundRect(rect(pad, pad, w, h), r.toFloat(), r.toFloat(), shadowPaint)
            }
            else shadowCanvas.drawCircle((pad + w / 2).toFloat(), (pad + h / 2).toFloat(), r.toFloat(), shadowPaint)
            bitmap
        }
        canvas.drawBitmap(image, (x - pad).toFloat(), (y - pad).toFloat(), paint(0xFFFFFF, alpha))
    }

    private fun catIcon(i: Int, x: Double, y: Double, r: Double) {
        circle(x, y, r, paint(cats[i].color))
        val k = r / 32
        val w = paint(0xFFFFFF)
        val ws = stroke(0xFFFFFF, 3.4 * k)
        canvas.save()
        translate(x, y)
        scale(k)
        when (i) {
            0 -> {
                rr(-11, -15, 22, 30, 5, stroke(0xFFFFFF, 3))
                text("%", 0, 1, "glyph", paint(0x7A3F8E), 0.5)
            }
            1 -> {
                line(-7, -14, -7, 14, ws)
                line(-11, -14, -11, -4, stroke(0xFFFFFF, 2.4))
                line(-3, -14, -3, -4, stroke(0xFFFFFF, 2.4))
                canvas.drawPath(Path().apply {
                        moveTo(7f, 14f)
                        lineTo(7f, -14f)
                        quadTo(14f, -8f, 12f, 2f)
                        lineTo(7f, 2f)
                    }, w)
                line(7, 0, 7, 14, ws)
            }
            2 -> {
                canvas.drawPath(Path().apply {
                        moveTo(0f, -6f)
                        cubicTo(-16f, -14f, -18f, 8f, -6f, 14f)
                        quadTo(0f, 11f, 6f, 14f)
                        cubicTo(18f, 8f, 16f, -14f, 0f, -6f)
                        close()
                    }, w)
                line(1, -7, 4, -15, stroke(0xFFFFFF, 2.6))
            }
            3 -> canvas.drawPath(Path().apply {
                    moveTo(-6f, -13f)
                    quadTo(0f, -9f, 6f, -13f)
                    lineTo(15f, -7f)
                    lineTo(11f, -1f)
                    lineTo(8f, -3f)
                    lineTo(8f, 13f)
                    lineTo(-8f, 13f)
                    lineTo(-8f, -3f)
                    lineTo(-11f, -1f)
                    lineTo(-15f, -7f)
                    close()
                }, w)
            4 -> {
                canvas.save()
                canvas.clipPath(Path().apply {
                        addCircle(0f, 0f, 32f, Path.Direction.CW)
                    })
                canvas.drawPath(Path().apply {
                        moveTo(-32f, 32f)
                        lineTo(32f, 32f)
                        lineTo(-32f, -2f)
                        close()
                    }, paint(0x16161A))
                for (q in 0..2) {
                    canvas.drawRect(rect(-26 + q * 12, 18, 6, 6), paint(0xFFD60A))
                }
                canvas.restore()
            }
            5 -> {
                rr(-14, -6, 28, 18, 3, paint(0xFF7A1A))
                canvas.drawPath(Path().apply {
                        moveTo(-14f, -9f)
                        lineTo(14f, -15f)
                        lineTo(15f, -10f)
                        lineTo(-13f, -4f)
                        close()
                    }, paint(0xFF7A1A))
                line(-6, -10, -2, -6, stroke(0x16161A, 2))
                line(3, -12, 7, -8, stroke(0x16161A, 2))
            }
            6 -> {
                rr(-4.5, -14, 9, 28, 2, w)
                rr(-14, -4.5, 28, 9, 2, w)
            }
            7 -> {
                rr(-6, -2, 12, 16, 2, w)
                canvas.drawPath(Path().apply {
                        moveTo(-5f, -2f)
                        lineTo(-5f, -10f)
                        lineTo(5f, -16f)
                        lineTo(5f, -2f)
                        close()
                    }, w)
            } else -> canvas.drawPath(Path().apply {
                    moveTo(0f, -15f)
                    cubicTo(6f, -6f, 12f, 0f, 12f, 6f)
                    cubicTo(12f, 13f, 6f, 16f, 0f, 16f)
                    cubicTo(-6f, 16f, -12f, 13f, -12f, 6f)
                    cubicTo(-12f, 0f, -6f, -6f, 0f, -15f)
                    close()
                }, w)
        }
        canvas.restore()
    }

    private fun checkbox(x: Double, y: Double, on: Double, at: Double, now: Double, nope: Double) {
        val pop = if (at > NEVER) sin(PI * clamp01((now - at) / 0.3)) * (1 - clamp01((now - at) / 0.3)) else 0.0
        val s = 1 + 0.18 * pop
        val shake = if (nope > 0) 7 * sin(nope * 40) * exp(-nope * 6) else 0.0
        canvas.save()
        translate(x + shake, y)
        scale(s)
        rr(-24, -24, 48, 48, 10, mixColor(C.box, C.boxOn, on))
        if (nope > 0 && nope < 0.5) {
            rr(-24, -24, 48, 48, 10, stroke(0xFF5A5A, 3, 1 - nope * 2))
        }
        if (on > 0.02) {
            val k = clamp01(on * 1.2)
            val l1 = hypot(7.0, 8.0)
            val l2 = hypot(12.0, -16.0)
            val d = k * (l1 + l2)
            val path = Path().apply {
                moveTo(-9f, 0f)
                if (d <= l1) {
                    lineTo(mix(-9.0, -2.0, d / l1).toFloat(), mix(0.0, 8.0, d / l1).toFloat())
                } else {
                    lineTo(-2f, 8f)
                    lineTo(mix(-2.0, 10.0, (d - l1) / l2).toFloat(), mix(8.0, -8.0, (d - l1) / l2).toFloat())
                }
            }
            canvas.drawPath(path, stroke(0xFFFFFF, 3.6))
        }
        canvas.restore()
    }

    fun drawScene(targetCanvas: Canvas, width: Float, height: Float, frame: Frame) {
        canvas = targetCanvas
        shadowIndex = 0
        val s = frame.s
        val now = frame.now
        val scale = width / 736.0
        val h = height / scale
        canvas.drawColor(argb(C.bg))
        canvas.save()
        scale(scale)
        // The page scrolls under the fixed brand bar and bottom panel.
        canvas.save()
        canvas.clipRect(rect(0, 222, 736, h))
        translate(0, -frame.scroll)
        for (l in 0..1) {
            text(Copy.title[l], UI.titleX, UI.titleY[l], "title", paint(C.ink))
        }
        rr(UI.bannerX, UI.bannerY, UI.bannerW, UI.bannerH, 30, gradient(UI.bannerX, 0, UI.bannerX + UI.bannerW, 0, intArrayOf(argb(C.bannerA), argb(C.bannerB))))
        plusMark(91.0, UI.bannerY + 67, 33.0)
        val bw = text(Copy.banner, 150, UI.bannerY + 50, "banner", paint(C.ink))
        circle(150 + bw + 18, UI.bannerY + 50, 10, stroke(C.grey, 2))
        val cw = width(Copy.bannerSub, "chip")
        rr(146, UI.bannerY + 70, cw + 16, 32, 10, paint(C.chip))
        text(Copy.bannerSub, 154, UI.bannerY + 86, "chip", paint(0x5C5470))
        rr(630, UI.bannerY + 43, 48, 48, 10, paint(0xE7E2F6))
        val tw = 2 * PI * frame.amb / LOOP
        val stars = arrayOf(doubleArrayOf(520.0, UI.bannerY + 24, 0.0), doubleArrayOf(600.0, UI.bannerY + 104, 1.7), doubleArrayOf(460.0, UI.bannerY + 112, 3.1), doubleArrayOf(640.0, UI.bannerY + 22, 4.4), doubleArrayOf(570.0, UI.bannerY + 58, 5.6))
        for (i in stars.indices) {
            val tk = max(0.0, sin(tw * 2 + stars[i][2]))
            val burst = if (frame.sparkle >= 0) sin(PI * clamp01((frame.sparkle - i * 0.08) / 0.7)) else 0.0
            val a = 0.35 * tk * tk + burst
            if (a > 0.02) {
                star4(stars[i][0], stars[i][1], 6 + 8 * burst + 2 * tk, paint(C.star, min(1.0, a)))
            }
        }
        val sw = text(Copy.section, 44, UI.sectionY, "section", paint(C.ink))
        rr(44 + sw + 14, UI.sectionY - 17, 34, 34, 8, paint(C.pay))
        text("O", 44 + sw + 31, UI.sectionY, "glyph", paint(0xFFFFFF), 0.5)
        text(Copy.pay, 44 + sw + 58, UI.sectionY, "section", paint(C.ink))
        rr(UI.listX, UI.listY, UI.listW, cats.size * UI.pitch + 40, 30, paint(C.card))
        for (i in cats.indices) {
            val cy = UI.listTop + i * UI.pitch
            val cat = cats[i]
            val ty = if (cat.sub != null) cy - 12 else cy
            catIcon(i, UI.iconX, cy, UI.iconR)
            val pw = text("${cat.pct} ${cat.name}", UI.textX, ty, "row", paint(C.ink))
            circle(UI.textX + pw + 18, ty, 10, stroke(0xB8BCC8, 2))
            text("?", UI.textX + pw + 18, ty, "sub", paint(0xB8BCC8), 0.5)
            cat.sub?.let {
                text(it, UI.textX, cy + 20, "sub", paint(C.grey))
            }
            val on = if (s.checked[i]) smooth((now - s.checkAt[i]) / 0.18) else 1 - smooth((now - s.checkAt[i]) / 0.18)
            val nope = if (s.nopeRow == i) now - s.nopeAt else -1.0
            checkbox(UI.boxX, cy, if (s.checkAt[i] <= NEVER) (if (s.checked[i]) 1.0 else 0.0) else on, s.checkAt[i], now, nope)
        }
        canvas.restore()
        val back = stroke(C.ink, 3.6)
        line(UI.backX - 14, UI.backY, UI.backX + 14, UI.backY, back)
        line(UI.backX - 14, UI.backY, UI.backX - 4, UI.backY - 10, back)
        line(UI.backX - 14, UI.backY, UI.backX - 4, UI.backY + 10, back)
        val brandW = width(Copy.brand, "brand")
        plusMark(UI.logoX - brandW / 2 - 8, UI.logoY, 20.0)
        text(Copy.brand, UI.logoX - brandW / 2 + 18, UI.logoY, "brand", paint(C.ink))
        val k = panelK(frame)
        if (k < 0.999) {
            canvas.drawRect(rect(0, UI.disabledY - 40, 736, h), paint(C.bg, 1 - k))
        }
        if (k > 0.001) {
            val top = mix(h + 40, UI.panelY, k)
            shadow(0.0, top, 736.0, h - top + 60, 36.0, 18.0, 0.12, true)
            rr(0, top, 736, h - top + 60, 36, paint(C.panel))
            val ca = smooth((k - 0.4) / 0.6)
            val bx = 64.0
            val by = UI.toggleY + (1 - k) * 60
            canvas.drawPath(Path().apply {
                    moveTo((bx + 4).toFloat(), (by - 18).toFloat())
                    lineTo((bx - 8).toFloat(), (by + 3).toFloat())
                    lineTo(bx.toFloat(), (by + 3).toFloat())
                    lineTo((bx - 4).toFloat(), (by + 18).toFloat())
                    lineTo((bx + 8).toFloat(), (by - 3).toFloat())
                    lineTo(bx.toFloat(), (by - 3).toFloat())
                    close()
                }, paint(C.purple, ca))
            for (l in 0..1) {
                text(Copy.connect[l], 104, by - 14 + l * 30, "connect", paint(C.ink, ca))
            }
            rr(612, by - 23, 82, 46, 23, paint(C.purple, ca))
            circle(671, by, 18, paint(0xFFFFFF, ca))
            for (l in 0..1) {
                text(Copy.fine[l], 368, UI.fineY[l] + (1 - k) * 60, "fine", paint(C.fine, ca), 0.5)
            }
        }
        drawButton(frame, k)
        for (fl in s.flights) {
            val p = (now - fl.at) / FLY
            if (p < 0 || p >= 1) {
                continue
            }
            val e = easeInOut(p)
            val rowP = Pair(UI.iconX, UI.listTop + fl.row * UI.pitch - frame.scroll)
            val slot = slotPos(if (fl.dir == 1) fl.to else fl.from, k)
            val a = if (fl.dir == 1) rowP else slot
            val b = if (fl.dir == 1) slot else rowP
            val cx = (a.first + b.first) / 2
            val cy = min(a.second, b.second) - 240
            val x = (1 - e) * (1 - e) * a.first + 2 * (1 - e) * e * cx + e * e * b.first
            val y = (1 - e) * (1 - e) * a.second + 2 * (1 - e) * e * cy + e * e * b.second
            // a fading trail of the icon's own colour behind it
            for (g in 3 downTo 1) {
                val eg = easeInOut(max(0.0, p - g * 0.055))
                val gx = (1 - eg) * (1 - eg) * a.first + 2 * (1 - eg) * eg * cx + eg * eg * b.first
                val gy = (1 - eg) * (1 - eg) * a.second + 2 * (1 - eg) * eg * cy + eg * eg * b.second
                val gr = (if (fl.dir == 1) mix(32.0, 19.0, eg) else mix(19.0, 32.0, eg)) * (1 - g * 0.12)
                circle(gx, gy, gr, paint(cats[fl.row].color, 0.16 * (4 - g) * sin(PI * p)))
            }
            val rr = if (fl.dir == 1) mix(32.0, 19.0, e) else mix(19.0, 32.0, e)
            val lift = sin(PI * p)
            canvas.save()
            translate(x, y)
            canvas.rotate((fl.dir * 360 * easeOut(p) * 0.5).toFloat())
            scale(1 + 0.25 * lift)
            shadow(-rr, 14 * lift - rr, rr * 2, rr * 2, rr, 10.0, 0.18 * lift, false)
            catIcon(fl.row, 0.0, 0.0, rr)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawButton(frame: Frame, k: Double) {
        val s = frame.s
        val now = frame.now
        val bx = mix(UI.disabledX, UI.buttonX, k)
        val by = mix(UI.disabledY, UI.buttonY, k)
        val nopeT = if (s.nopeAt > NEVER) now - s.nopeAt else -1.0
        val shake = if (nopeT >= 0) 11 * sin(nopeT * 42) * exp(-nopeT * 6) else 0.0
        canvas.save()
        translate(shake, 0)
        rr(bx, by, UI.buttonW, UI.buttonH, 30, mixColor(C.disabled, C.button, k))
        if (s.picks.size == 5 && s.countAt > NEVER && now - s.countAt < 1.2) {
            val q = (now - s.countAt) / 1.2
            canvas.save()
            canvas.clipPath(Path().apply {
                    addRoundRect(rect(bx, by, UI.buttonW, UI.buttonH), 30f, 30f, Path.Direction.CW)
                })
            val gx = mix(bx - 120, bx + UI.buttonW + 120, easeInOut(q))
            val gp = gradient(gx - 90, 0, gx + 90, 0, intArrayOf(argb(0xFFFFFF, 0.0), argb(0xB48CFF, 0.35), argb(0xFFFFFF, 0.0)))
            canvas.drawRect(rect(gx - 90, by, 180, UI.buttonH), gp)
            canvas.restore()
        }
        val bcy = by + UI.buttonH / 2
        if (k < 0.5) {
            text(Copy.choose, bx + UI.buttonW / 2, bcy, "button", paint(C.disabledText, 1 - k * 2), 0.5)
        }
        if (k > 0.2) {
            val n = s.picks.size
            val roll = if (s.countAt > NEVER) smooth((now - s.countAt) / 0.28) else 1.0
            val from = s.countFrom
            val tp = paint(0xFFFFFF, smooth((k - 0.2) / 0.5))
            val lead = "Choose"
            val tail = "of 5"
            val gap = width(" ", "button").let { if (it > 0) it else requireNotNull(fonts["button"]).textSize * 0.28 }
            val lw = width(lead, "button") + gap
            val nw = width("0", "button")
            val tw = width(tail, "button") + gap
            val tx = bx + 200 - (lw + nw + tw) / 2
            text(lead, tx, bcy, "button", tp)
            canvas.save()
            canvas.clipRect(rect(tx + lw - 2, bcy - 26, nw + 4, 52))
            val dir = if (n > from) 1 else -1
            if (roll < 1 && n != from) {
                text(max(0, from).toString(), tx + lw, bcy - dir * 40 * roll, "button", tp)
                text(n.toString(), tx + lw, bcy + dir * 40 * (1 - roll), "button", tp)
            } else {
                text(max(1, n).toString(), tx + lw, bcy, "button", tp)
            }
            canvas.restore()
            text(tail, tx + lw + nw + gap, bcy, "button", tp)
            for (i in 0..4) {
                val sp = slotPos(i, k)
                circle(sp.first, sp.second, 21, stroke(0xFFFFFF, 2, 0.28 * smooth((k - 0.3) / 0.7)))
            }
            for ((q, row) in s.picks.withIndex()) {
                if (s.flights.any {
                        it.row == row && it.dir == 1 && now - it.at < FLY
                    }) {
                    continue
                }
                val slide = if (s.slotAt[row] > NEVER) spring(now - s.slotAt[row], 0.4, 0.75) else 1.0
                val fromP = slotPos(s.slotFrom[row], k)
                val to = slotPos(q, k)
                var land = 0.0
                for (fl in s.flights) {
                    if (fl.row == row && fl.dir == 1) {
                        land = now - fl.at - FLY
                    }
                }
                val sq = if (land >= 0 && land < 0.35) sin(PI * (land / 0.35)) * (1 - land / 0.35) else 0.0
                // the slot flashes a ring as it catches the icon
                if (land >= 0 && land < 0.45) {
                    val lq = land / 0.45
                    circle(to.first, to.second, 21 + 14 * easeOut(lq), stroke(0xB48CFF, 3 * (1 - lq), 0.9 * (1 - lq)))
                }
                canvas.save()
                translate(mix(fromP.first, to.first, slide), mix(fromP.second, to.second, slide))
                scale(1 + 0.25 * sq, 1 - 0.18 * sq)
                catIcon(row, 0.0, 0.0, 19.0)
                canvas.restore()
            }
        }
        canvas.restore()
    }
}
