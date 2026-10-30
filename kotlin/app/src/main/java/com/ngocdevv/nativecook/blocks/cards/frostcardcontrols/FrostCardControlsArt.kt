package com.ngocdevv.nativecook.blocks.cards.frostcardcontrols

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsMotion.Frame
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsMotion.mix
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsMotion.smooth
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsTheme.C
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsTheme.COPY
import com.ngocdevv.nativecook.blocks.cards.frostcardcontrols.FrostCardControlsTheme.UI

/** Static card/page are baked once; only transforms, ice uniforms and water change per frame. */
internal class FrostCardControlsArt(context: Context) {
    private val faces = listOf("Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold", "Poppins-SemiBold").associateWith {
        Typeface.createFromAsset(context.assets, "frostcardcontrols/frostcardcontrols_$it.ttf")
    }
    private val textWidths = mutableMapOf<String, Float>()
    private val w = UI.cardW
    private val h = UI.cardH
    private val cardRect = rect(0, 0, w, h)
    private val cardPath = Path().apply {
        addRoundRect(cardRect, UI.cardR.toFloat(), UI.cardR.toFloat(), Path.Direction.CW)
    }
    private val card = bake(w, h) {
        drawCard(it)
    }
    private val frosted = blur(card, 21.0)
    private val shadow = bake(w + 100, h + 100) { c ->
        c.drawRoundRect(rect(56, 62, w - 12, h), 14f, 14f, paint(0x1A1F33, 0.16).apply {
            maskFilter = BlurMaskFilter((14f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        })
    }
    private val page = Picture().apply {
        val c = beginRecording(402, 1100)
        drawPage(c)
        endRecording()
    }
    private val runtime = if (Build.VERSION.SDK_INT >= 33) FrostCardControlsRuntime() else null
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Mask filters need a software canvas; reuse this small strip for the moving mist.
    private val mistBitmap = Bitmap.createBitmap(ceil(w + 100).toInt(), 130, Bitmap.Config.ARGB_8888)
    private val mistCanvas = Canvas(mistBitmap)
    private val mistPaint = paint().apply {
        maskFilter = BlurMaskFilter((10f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
    }
    private val tileFont = font("Inter-SemiBold", 15.0, C.ink)
    private val badgeFont = font("Inter-SemiBold", 12.0, 0x2E5DD8)
    private fun color(v: Int, a: Double = 1.0) = ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or v
    private fun paint(v: Int = 0xFFFFFF, a: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = color(v, a)
    }
    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble() + w.toDouble()).toFloat(), (y.toDouble() + h.toDouble()).toFloat())
    private fun linear(x1: Number, y1: Number, x2: Number, y2: Number, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = LinearGradient(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), colors, stops, Shader.TileMode.CLAMP)
    }
    private fun font(face: String, size: Double, v: Int) = paint(v).apply {
        typeface = faces.getValue(face)
        textSize = size.toFloat()
        fontFeatureSettings = "'kern' 0, 'liga' 0"
    }
    /** SkFont.measureText returns ink bounds; Paint.measureText returns an advance instead. */
    private fun inkWidth(paint: Paint, text: String): Float {
        val key = "${paint.typeface.hashCode()}:${paint.textSize}:$text"
        return textWidths.getOrPut(key) {
            val path = Path()
            val bounds = RectF()
            paint.getTextPath(text, 0, text.length, 0f, 0f, path)
            if (Build.VERSION.SDK_INT >= 36) path.computeBounds(bounds)
            else {
                @Suppress("DEPRECATION") // The overload without the ignored flag starts at API 36.
                path.computeBounds(bounds, true)
            }
            bounds.width()
        }
    }
    private fun text(c: Canvas, str: String, x: Number, y: Number, p: Paint) = c.drawText(str, x.toFloat(), y.toFloat(), p)
    private fun rr(c: Canvas, x: Number, y: Number, w: Number, h: Number, r: Number, p: Paint) = c.drawRoundRect(rect(x, y, w, h), r.toFloat(), r.toFloat(), p)
    private fun line(c: Canvas, x: Number, y: Number, x2: Number, y2: Number, p: Paint) = c.drawLine(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), p)
    private fun circle(c: Canvas, x: Number, y: Number, r: Number, p: Paint) = c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)
    private fun bake(w: Double, h: Double, draw: (Canvas) -> Unit): Bitmap = Bitmap.createBitmap(ceil(w * 3).toInt(), ceil(h * 3).toInt(), Bitmap.Config.ARGB_8888).also {
        val c = Canvas(it)
        c.scale(3f, 3f)
        draw(c)
    }
    /** Separable Gaussian over premultiplied pixels, clamped at the bitmap edges like RN's bake. */
    private fun blur(source: Bitmap, sigma: Double): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val radius = ceil(sigma * 3).toInt()
        val weights = DoubleArray(radius * 2 + 1) {
            exp(-((it - radius) * (it - radius)).toDouble() / (2 * sigma * sigma))
        }
        val sum = weights.sum()
        for (i in weights.indices) weights[i] /= sum
        var input = pixels
        var output = IntArray(pixels.size)
        for (pass in 0..1) {
            for (y in 0 until height) for (x in 0 until width) {
                var a = 0.0
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (k in -radius..radius) {
                    val xx = if (pass == 0) (x + k).coerceIn(0, width - 1) else x
                    val yy = if (pass == 1) (y + k).coerceIn(0, height - 1) else y
                    val p = input[yy * width + xx]
                    val weight = weights[k + radius]
                    val alpha = (p ushr 24) / 255.0
                    a += (p ushr 24) * weight
                    r += ((p ushr 16) and 255) * alpha * weight
                    g += ((p ushr 8) and 255) * alpha * weight
                    b += (p and 255) * alpha * weight
                }
                val unpremul = if (a > 0) 255 / a else 0.0
                output[y * width + x] = (a.roundToInt().coerceIn(0, 255) shl 24) or ((r * unpremul).roundToInt().coerceIn(0, 255) shl 16) or
                ((g * unpremul).roundToInt().coerceIn(0, 255) shl 8) or (b * unpremul).roundToInt().coerceIn(0, 255)
            }
            val swap = input
            input = output
            output = swap
        }
        return Bitmap.createBitmap(input, width, height, Bitmap.Config.ARGB_8888)
    }
    private fun drawCard(c: Canvas) {
        c.save()
        c.clipPath(cardPath)
        c.drawRect(cardRect, linear(0, 0, w, h, intArrayOf(color(0xF4F4F6), color(0xD9DADF), color(0xEDEEF1), color(0xC9CBD1)), floatArrayOf(0f, 0.4f, 0.7f, 1f)))
        val grain = paint().apply {
            strokeWidth = 0.5f
        }
        var y = 0.0
        while (y < h) {
            grain.color = color(0xFFFFFF, max(0.0, 0.03 + 0.03 * sin(y * 3.7) * sin(y * 0.41)))
            line(c, 0, y, w, y, grain)
            y += 1.6
        }
        c.drawRect(cardRect, paint().apply {
            shader = RadialGradient((w * 0.42).toFloat(), (h * 0.55).toFloat(), (w * 0.6).toFloat(), intArrayOf(color(0xFFFFFF, 0.55), color(0xFFFFFF, 0.0)), null, Shader.TileMode.CLAMP)
        })
        val brand = font("Poppins-SemiBold", 100.0, C.brandA)
        val size = 100 * (w + 6) / inkWidth(brand, COPY.brand)
        brand.textSize = size.toFloat()
        brand.shader = linear(0, 0, w, 60, intArrayOf(color(C.brandA), color(C.brandB), color(C.brandA)), floatArrayOf(0f, 0.55f, 1f)).shader
        text(c, COPY.brand, -3, size * 0.6, brand)
        text(c, COPY.physical, 21.9, 162.7, font("Inter-Regular", 15.0, 0x8F9097))
        text(c, COPY.last4, 21.9, 183.6, font("Inter-Medium", 17.0, 0x26272B))
        val ring = linear(264.4, 146.8, 294.4, 178.8, intArrayOf(color(0xFFB020), color(0xFF6A1A))).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        circle(c, 278.4, 162.8, 13, ring)
        circle(c, 262.4, 162.8, 11, paint(0xE5262B))
        c.restore()
        rr(c, 0, 0, w, h, 14, paint(0, 0.08).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        })
    }
    private fun drawPage(c: Canvas) {
        text(c, COPY.balance, 41.5, 100.5, font("Inter-Bold", 25.0, C.ink))
        text(c, COPY.noLimit, 41.5, 127.8, font("Inter-Regular", 16.0, C.ink))
        val set = font("Inter-SemiBold", 16.0, C.blue)
        text(c, COPY.setLimit, 362.7 - inkWidth(set, COPY.setLimit), 127.8, set)
        val hint = font("Inter-Medium", 15.5, 0x7E7F86)
        val hx = 202 - inkWidth(hint, COPY.hint) / 2 + 12
        text(c, COPY.hint, hx, 432.6, hint)
        val hand = requireNotNull(PathParser.createPathFromPathData("M2 9 L2 1.5 C2 0.4 3.6 0.4 3.6 1.5 L3.6 6 L3.6 4.6 C3.6 3.5 5.2 3.5 5.2 4.6 L5.2 6.2 L5.2 5.2 C5.2 4.1 6.8 4.1 6.8 5.2 L6.8 6.6 L6.8 6 C6.8 4.9 8.4 4.9 8.4 6 L8.4 10 C8.4 12.6 6.6 14 4.6 14 L4 14 C2.6 14 1.8 13.2 1 12 L-0.6 9.2 C-1.2 8.2 0.2 7.2 1 8.2 Z"))
        hand.offset((hx - 18).toFloat(), 420f)
        c.drawPath(hand, paint(0x7E7F86).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            strokeJoin = Paint.Join.ROUND
        })
        rr(c, 20.8, 487.5, 363.2, 57.9, 16, paint(C.button))
        val wallet = font("Inter-SemiBold", 20.0, 0xFFFFFF)
        val tx = 201 - inkWidth(wallet, COPY.wallet) / 2 + 18
        val cy = 487.5 + 57.9 / 2
        text(c, COPY.wallet, tx, cy + 7, wallet)
        val ix = tx - 44
        rr(c, ix, cy - 11, 30, 22, 4, paint(0xE9E2D2))
        val bands = intArrayOf(0xF04E3E, 0xF9B233, 0x3DB36B, 0x3B82F6)
        for (i in 0..3) c.drawRect(rect(ix + 2, cy - 9 + i * 3.2, 26, 3.2), paint(bands[i]))
        rr(c, ix, cy - 1, 30, 12, 3, paint(0xE9E2D2))
        rr(c, 20.8, 678, 363.2, 220, 20, paint())
        text(c, COPY.transactions, 41.5, 719, font("Inter-Bold", 22.0, C.ink))
        text(c, COPY.nothing, 41.5, 742.8, font("Inter-Regular", 16.0, C.ink))
        circle(c, 340, 724, 27, paint(0xEEF0F5))
    }
    private fun drawIcon(c: Canvas, i: Int, x: Double, y: Double, spin: Double) {
        val p = paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.4f
        }
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        if (i == 0) {
            c.rotate(spin.toFloat())
            p.shader = linear(-12, -12, 12, 12, intArrayOf(color(0x4B2BE0), color(0x8B46FF))).shader
            for (k in 0..5) {
                c.save()
                c.rotate(k * 60f)
                line(c, 0, 0, 0, -12, p)
                line(c, 0, -7.5, -3.6, -10.6, p)
                line(c, 0, -7.5, 3.6, -10.6, p)
                c.restore()
            }
        } else if (i == 1) {
            p.color = color(0xFF6A1A)
            c.drawArc(rect(-11, -11, 22, 22), 200f, 130f, false, p)
            p.color = color(0x5B2BE6)
            c.drawArc(rect(-11, -11, 22, 22), 20f, 130f, false, p)
            p.color = color(0xFF6A1A)
            c.drawPath(Path().apply {
                moveTo(6.5f, -12.5f)
                lineTo(10.5f, -8f)
                lineTo(4.5f, -6.5f)
            }, p)
            p.color = color(0x5B2BE6)
            c.drawPath(Path().apply {
                moveTo(-6.5f, 12.5f)
                lineTo(-10.5f, 8f)
                lineTo(-4.5f, 6.5f)
            }, p)
        } else {
            p.shader = linear(-12, -12, 12, 12, intArrayOf(color(0x5B2BE6), color(0xFF6A1A))).shader
            circle(c, 0, 0, 11, p)
            line(c, -8, -8, 8, 8, p)
        }
        c.restore()
    }
    private fun drawTiles(c: Canvas, f: Frame) {
        for (i in 0..2) {
            val x = UI.tileXs[i]
            val scale = (1 - 0.04 * f.press[i]).toFloat()
            c.save()
            c.scale(scale, scale, (x + UI.tileW / 2).toFloat(), (UI.tileY + UI.tileH / 2).toFloat())
            val bg = if (i == 0) Color.rgb((mix(1.0, 0.9, f.frozenLabel) * 255).roundToInt(), (mix(1.0, 0.95, f.frozenLabel) * 255).roundToInt(), 255) else color(0xFFFFFF)
            rr(c, x, UI.tileY, UI.tileW, UI.tileH, UI.tileR, paint().apply {
                color = bg
            })
            drawIcon(c, i, x + UI.tileW / 2, UI.iconY, if (i == 0) f.spin else 0.0)
            fun label(s: String, y: Double, a: Double) {
                tileFont.color = color(C.ink, a)
                text(c, s, x + UI.tileW / 2 - inkWidth(tileFont, s) / 2, y, tileFont)
            }
            if (i == 0) {
                val k = f.frozenLabel
                label(COPY.tiles[0], UI.labelY + 5.4 - 6 * k, 1 - k)
                label(COPY.unfreeze, UI.labelY + 5.4 + 6 * (1 - k), k)
            } else label(COPY.tiles[i], UI.labelY + 5.4, 1.0)
            c.restore()
        }
    }
    private fun image(c: Canvas, b: Bitmap, dest: RectF, p: Paint? = imagePaint) = c.drawBitmap(b, null, dest, p)
    private fun drawCardLayer(c: Canvas, f: Frame, amb: Double, tiltX: Double) {
        if (f.shred >= 0) {
            val n = 14
            val sw = w / n
            val kx = card.width / w
            for (i in 0 until n) {
                val order = (i * 7) % n
                val u = max(0.0, (f.shred - 0.18 - order * 0.025) / 0.6)
                val gap = smooth(f.shred / 0.18) * (i - (n - 1) / 2.0) * 0.9
                val fall = 520 * u * u
                val rot = (if (i % 2 == 0) 1 else -1) * 18 * u
                val a = 1 - smooth((u - 0.6) / 0.4)
                if (a <= 0.01) continue
                c.save()
                c.translate((i * sw + sw / 2 + gap).toFloat(), (h / 2 + fall).toFloat())
                c.rotate(rot.toFloat())
                // BitmapShader preserves fractional source edges; integer source rectangles do not.
                val p = paint(0xFFFFFF, a).apply {
                    shader = BitmapShader(card, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shader ->
                        shader.setLocalMatrix(Matrix().apply {
                            setScale((1 / kx).toFloat(), (h / card.height).toFloat())
                            postTranslate((-i * sw - sw / 2).toFloat(), (-h / 2).toFloat())
                        })
                    }
                }
                c.drawRect(rect(-sw / 2, -h / 2, sw, h), p)
                c.restore()
            }
            return
        }
        image(c, shadow, rect(-50, -50, w + 100, h + 100))
        image(c, card, cardRect)
        val sx = w * (0.5 + 0.45 * tiltX) + (if (f.shine >= 0) mix(-w, w, f.shine) else 0.0)
        val sheen = linear(sx - 60, 0, sx + 60, h, intArrayOf(color(0xFFFFFF, 0.0), color(0xFFFFFF, if (f.shine >= 0) 0.5 else 0.18), color(0xFFFFFF, 0.0))).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
        c.save()
        c.clipPath(cardPath)
        c.drawRect(cardRect, sheen)
        if (f.frost > 0.001) {
            if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
                runtime.update(f, amb)
                c.saveLayer(cardRect, null)
                image(c, frosted, cardRect)
                c.drawRect(cardRect, paint().apply {
                    shader = runtime.mask
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                })
                c.restore()
                c.drawRect(cardRect, paint(0x8CBFFF, 0.12 * min(1.0, f.frost) * (1 - f.melt)))
                c.drawRect(cardRect, paint().apply {
                    shader = runtime.ice
                })
            } else {
                // Below API 33 retain the frosted copy and cold tint without runtime crystals.
                val coverage = min(1.0, f.frost) * (1 - f.melt)
                image(c, frosted, cardRect, paint(0xFFFFFF, coverage))
                c.drawRect(cardRect, paint(C.ice, 0.48 * coverage))
            }
        }
        val iced = smooth((f.frost - 0.85) / 0.3) * (1 - f.melt)
        if (f.frost > 0.001 && iced > 0.01) {
            for (k in 0 until 9) {
                val hx = sin((k + 1) * 78.233) * 43758.5453
                val hy = sin((k + 1) * 12.9898) * 43758.5453
                val px = (hx - floor(hx)) * (w - 30) + 15
                val py = (hy - floor(hy)) * (h - 30) + 15
                val tw = max(0.0, sin(2 * PI * (amb / 3 + k * 0.37) + tiltX * 3))
                val a = iced * tw * tw * tw
                if (a < 0.02) continue
                val arm = 3 + 5 * tw
                val sp = paint(0xFFFFFF, 0.85 * a).apply {
                    strokeWidth = 1.3f
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
                line(c, px - arm, py, px + arm, py, sp)
                line(c, px, py - arm, px, py + arm, sp)
                circle(c, px, py, 1.6, sp)
            }
        }
        if (f.drips >= 0) {
            for (k in 0..6) {
                val x = 24 + ((k * 53) % (w - 40))
                val st = 0.12 * ((k * 3) % 5)
                val u = max(0.0, (f.drips - st) / (1 - st))
                if (u <= 0) continue
                val len = 26 + 34 * u
                val yb = 20 + (h + 10) * u * u
                val a = 0.5 * (1 - smooth((u - 0.75) / 0.25))
                val p = linear(x, yb - len, x, yb, intArrayOf(color(0xCFE6FF, 0.0), color(0xBFDDFF, a))).apply {
                    strokeWidth = 3f
                    strokeCap = Paint.Cap.ROUND
                }
                line(c, x, yb - len, x, yb, p)
                circle(c, x, yb + 1, 2.6, paint(0xE8F4FF, a * 1.4))
            }
        }
        c.restore()
        val cold = smooth((f.frost - 0.85) / 0.3) * (1 - f.melt)
        if (cold > 0.01) {
            mistBitmap.eraseColor(Color.TRANSPARENT)
            for (k in 0 until 5) {
                val u = (amb / 4.5 + k / 5.0) % 1
                val x = w * (0.12 + 0.19 * k) + 10 * sin(2 * PI * (u + k * 0.3))
                val y = h - 6 + 46 * u
                val a = cold * 0.32 * sin(PI * u)
                mistPaint.color = Color.argb((a * 255).roundToInt(), (0.86 * 255).roundToInt(), (0.93 * 255).roundToInt(), 255)
                mistCanvas.drawOval(rect(x - 26 - 14 * u + 50, y - h + 40 - 7, 52 + 28 * u, 14 + 6 * u), mistPaint)
            }
            image(c, mistBitmap, rect(-50, h - 40, w + 100, 130))
        }
        val k = f.frozenLabel * (1 - f.melt)
        if (k > 0.01) {
            c.save()
            c.translate((w - 58).toFloat(), 16f)
            c.scale((0.9 + 0.1 * k).toFloat(), (0.9 + 0.1 * k).toFloat())
            rr(c, -4, -3, 52, 22, 11, paint(0xFFFFFF, 0.75 * k))
            badgeFont.color = color(0x2E5DD8, k)
            text(c, COPY.frozen, 5, 12.5, badgeFont)
            c.restore()
        }
    }
    fun drawScene(c: Canvas, width: Float, f: Frame, amb: Double, tiltX: Double, tiltY: Double) {
        c.drawColor(color(C.bg))
        c.save()
        val scale = width / 402f
        c.scale(scale, scale)
        c.drawPicture(page)
        drawTiles(c, f)
        if (f.card > 0.5) {
            c.save()
            c.translate((UI.cardX + w / 2 + f.out * 460).toFloat(), (UI.cardY + h / 2 + (1 - f.drop) * -330).toFloat())
            c.rotate((f.out * 24 + (1 - f.drop) * -8).toFloat())
            val sc = (0.94 + 0.06 * f.drop).toFloat()
            c.scale(sc, sc)
            if (abs(tiltX) > 0.001 || abs(tiltY) > 0.001) {
                // Project Ry(x*0.28) Rx(-y*0.22) with the same 700 pt perspective.
                val ry = tiltX * 0.28
                val rx = -tiltY * 0.22
                val matrix = Matrix().apply {
                    setValues(floatArrayOf(cos(ry).toFloat(), (sin(ry) * sin(rx)).toFloat(), 0f, 0f, cos(rx).toFloat(), 0f, (sin(ry) / 700).toFloat(), (-cos(ry) * sin(rx) / 700).toFloat(), 1f))
                }
                c.concat(matrix)
            }
            c.translate((-w / 2).toFloat(), (-h / 2).toFloat())
            drawCardLayer(c, f, amb, tiltX)
            c.restore()
        }
        c.restore()
    }
}

@RequiresApi(33)
private class FrostCardControlsRuntime {
    val mask = RuntimeShader(FrostCardControlsShaders.FROST_AGSL)
    val ice = RuntimeShader(FrostCardControlsShaders.FROST_AGSL)
    init {
        mask.setFloatUniform("maskOnly", 1f)
        ice.setFloatUniform("maskOnly", 0f)
    }
    fun update(f: FrostCardControlsMotion.Frame, amb: Double) {
        for (shader in arrayOf(mask, ice)) {
            shader.setFloatUniform("size", UI.cardW.toFloat(), UI.cardH.toFloat())
            shader.setFloatUniform("center", f.cx.toFloat(), f.cy.toFloat())
            shader.setFloatUniform("grow", f.frost.toFloat())
            shader.setFloatUniform("melt", f.melt.toFloat())
            shader.setFloatUniform("time", amb.toFloat())
        }
    }
}
