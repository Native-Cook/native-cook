package com.ngocdevv.nativecook.blocks.cards.smokedclochereveal

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.Log
import kotlin.math.*

internal class SmokedClocheRevealArt(context: Context) {
    private val assets = context.assets
    private fun bitmap(name: String): Bitmap = assets.open("smokedclochereveal/smokedclochereveal_$name").use {
        requireNotNull(BitmapFactory.decodeStream(it)) { "Missing cloche artwork: $name" }
    }
    private val dish = bitmap("scr-bowl.png")
    private val leaves = listOf(bitmap("scr-leaf-a.png"), bitmap("scr-leaf-b.png"))
    private val faces = listOf("Regular", "Medium", "SemiBold", "Bold").associateWith {
        Typeface.createFromAsset(context.assets, "smokedclochereveal/smokedclochereveal_Outfit-$it.ttf")
    }
    private val glass = if (Build.VERSION.SDK_INT >= 33) runCatching { SmokedClocheRevealShader(dish) }.getOrElse {
        Log.i("SmokedClocheReveal", "Glass shader unavailable; using the reference's flat fallback", it)
        null
    } else null
    private val smoke = SmokedClocheRevealSmoke()
    private var smokeEnabled = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val labels = mutableMapOf<String, Paint>()
    private val rect = RectF()
    private val path = Path()
    private var lastLayout: SmokedClocheRevealLayout? = null
    private var lastDensity = 0f
    private var background: Bitmap? = null
    private var copy: Bitmap? = null
    private val ink = 0xff1d1b19.toInt()
    private val white = 0xffffffff.toInt()

    private fun p(color: Int, alpha: Double = 1.0): Paint {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = (Color.alpha(color) * alpha.coerceIn(0.0, 1.0)).roundToInt()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        return paint
    }

    private fun label(face: String, size: Float, color: Int): Paint = labels.getOrPut("$face:$size:$color") {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = faces[face]; textSize = size; this.color = color }
    }

    private fun text(c: Canvas, value: String, face: String, size: Float, color: Int, x: Float, top: Float, height: Float = size * 1.2f) {
        val p = label(face, size, color)
        val fm = p.fontMetrics
        c.drawText(value, x, top + (height - (fm.descent - fm.ascent)) / 2 - fm.ascent, p)
    }

    private fun stroke(c: Canvas, width: Float, color: Int = ink, alpha: Double = 1.0, draw: (Paint) -> Unit) {
        val p = p(color, alpha)
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        draw(p)
    }

    private fun cached(l: SmokedClocheRevealLayout, density: Float, draw: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(max(1, (l.w * density).roundToInt()), max(1, (l.h * density).roundToInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(density, density)
        draw(c)
        return bitmap
    }

    private fun prepare(l: SmokedClocheRevealLayout, density: Float) {
        if (lastLayout == l && lastDensity == density) return
        lastLayout = l
        lastDensity = density
        background?.recycle()
        copy?.recycle()
        background = cached(l, density) { c ->
            val page = p(white)
            page.shader = LinearGradient(0f, 0f, l.w * 0.3f, l.h, intArrayOf(0xffd9d4ce.toInt(), 0xffddd6ce.toInt(), 0xffedd7bf.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, l.w, l.h, page)
            val glow = p(white)
            glow.shader = RadialGradient(-20f, l.h * 0.95f, l.w * 0.7f, intArrayOf(0x8cf3c9a0.toInt(), 0x00f3c9a0), null, Shader.TileMode.CLAMP)
            c.drawCircle(-20f, l.h * 0.95f, l.w * 0.7f, glow)
            listOf(floatArrayOf(18f, 560f, 64f, -20f), floatArrayOf(380f, 425f, 58f, 35f)).forEachIndexed { i, d ->
                c.save()
                c.translate(d[0] * l.sx, l.y(d[1]))
                c.rotate(d[3])
                rect.set(-d[2] / 2, -d[2] / 2, d[2] / 2, d[2] / 2)
                c.drawBitmap(leaves[i], null, rect, p(white))
                c.restore()
            }
        }
        copy = cached(l, density) { c ->
            text(c, "Eggs Curry", "Bold", 28f, ink, 33f * l.sx, l.y(636f) - 20, 36f)
            val time = "25 Mins"
            val tw = label("Medium", 16.5f, ink).measureText(time)
            val tx = 360f * l.sx - tw - 26f
            val ty = l.y(637f) - 12
            stroke(c, 1.5f) { c.drawCircle(tx + 9f, ty + 9f, 7.6f, it) }
            stroke(c, 1.5f) {
                path.reset(); path.moveTo(tx + 9, ty + 4.8f); path.lineTo(tx + 9, ty + 9); path.lineTo(tx + 11.8f, ty + 10.6f)
                c.drawPath(path, it)
            }
            text(c, time, "Medium", 16.5f, ink, tx + 26, ty, 19.8f)
            val body = "Eggs Curry with tomato and cucumbers, our chefs’ healthy, low-fat dish, smoked over applewood before it reaches you."
            val bp = label("Regular", 14.2f, 0xff4a4541.toInt())
            var line = ""
            var top = l.y(682f) - 11
            for (word in body.split(' ')) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && bp.measureText(next) > 330f * l.sx) {
                    text(c, line, "Regular", 14.2f, 0xff4a4541.toInt(), 33f * l.sx, top, 22.5f)
                    top += 22.5f
                    line = word
                } else line = next
            }
            text(c, line, "Regular", 14.2f, 0xff4a4541.toInt(), 33f * l.sx, top, 22.5f)
            text(c, "Total Price", "Regular", 13.2f, 0xff5e5853.toInt(), 33f * l.sx, l.y(780f) - 9)
            text(c, "$ 15.00", "Bold", 20.5f, ink, 33f * l.sx, l.y(812f) - 14)
        }
    }

    fun advance(l: SmokedClocheRevealLayout, f: SmokedClocheRevealMotion.Frame, reduced: Boolean) {
        if (!reduced && glass != null) {
            if (!smokeEnabled) smoke.reset()
            smokeEnabled = true
            smoke.step(l, f)
        }
    }

    fun draw(c: Canvas, l: SmokedClocheRevealLayout, f: SmokedClocheRevealMotion.Frame, density: Float, presses: FloatArray, count: Int, reduced: Boolean) {
        prepare(l, density)
        if (reduced) smokeEnabled = false
        c.save()
        c.scale(density, density)
        rect.set(0f, 0f, l.w, l.h)
        background?.let { c.drawBitmap(it, null, rect, p(white)) }
        val cart = f.cart.toFloat()
        c.save()
        c.translate(l.cx + (l.ctaX - l.cx) * cart, l.cy + (l.ctaY - l.cy) * cart - sin(PI * cart).toFloat() * 120)
        c.scale(1 - 0.88f * cart, 1 - 0.88f * cart)
        c.rotate(cart * 160)
        rect.set(-l.half, -l.half, l.half, l.half)
        c.drawBitmap(dish, null, rect, p(white, 1 - max(0.0, (f.cart - 0.85) / 0.15)))
        c.restore()

        val shader = glass
        if (!reduced && Build.VERSION.SDK_INT >= 33 && shader != null) {
            val glassPaint = p(white)
            glassPaint.shader = shader.update(l, f)
            c.drawRect(0f, 0f, l.w, l.h, glassPaint)
            glassPaint.shader = null
            smoke.draw(c)
        } else flat(c, l, f)

        rect.set(0f, 0f, l.w, l.h)
        copy?.let { c.drawBitmap(it, null, rect, p(white)) }
        control(c, 37f * l.sx, l.y(63f), presses[0], reduced) {
            stroke(c, 2f, 0xff3a3632.toInt()) {
                path.reset(); path.moveTo(4f, -9f); path.lineTo(-4f, 0f); path.lineTo(4f, 9f)
                c.drawPath(path, it)
            }
        }
        control(c, 364f * l.sx, l.y(63f), presses[1], reduced) {
            for (y in listOf(-7f, 0f, 7f)) c.drawCircle(0f, y, 2.4f, p(0xff3a3632.toInt()))
        }
        control(c, 200f * l.sx, l.y(808f), presses[2], reduced) {
            c.drawCircle(0f, 0f, 23f, p(0xffe3dcd5.toInt()))
            c.save(); c.translate(-11f, -10f)
            path.reset(); path.moveTo(11f, 18.2f)
            path.cubicTo(4f, 13.4f, 1.6f, 10.2f, 1.6f, 6.6f)
            path.cubicTo(1.6f, 1.3f, 8f, 0.2f, 11f, 4.6f)
            path.cubicTo(14f, 0.2f, 20.4f, 1.3f, 20.4f, 6.6f)
            path.cubicTo(20.4f, 10.2f, 18f, 13.4f, 11f, 18.2f); path.close()
            stroke(c, 1.7f) { c.drawPath(path, it) }
            c.restore()
        }
        val x = 236f * l.sx
        val y = l.y(772f)
        val w = 190f * l.sx
        val h = 70f * l.k
        control(c, x + w / 2, y + h / 2, presses[3], reduced) {
            c.translate(-w / 2, -h / 2)
            path.reset()
            rect.set(0f, 0f, w, h)
            path.addRoundRect(rect, floatArrayOf(26f, 26f, 0f, 0f, 0f, 0f, 26f, 26f), Path.Direction.CW)
            c.drawPath(path, p(0xff050505.toInt()))
            text(c, "Add to Cart", "SemiBold", 15f, white, 22f, (h - 18f) / 2, 18f)
            val px = 22f + label("SemiBold", 15f, white).measureText("Add to Cart") + 12f + 16f
            c.drawCircle(px, h / 2, 16f, p(white))
            stroke(c, 2f, 0xff050505.toInt()) {
                c.drawLine(px, h / 2 - 5.5f, px, h / 2 + 5.5f, it)
                c.drawLine(px - 5.5f, h / 2, px + 5.5f, h / 2, it)
            }
            if (count > 0) {
                val value = count.toString()
                val bw = max(18f, label("SemiBold", 11f, white).measureText(value) + 8)
                rect.set(px + 22 - bw, h / 2 - 22, px + 22, h / 2 - 4)
                c.drawRoundRect(rect, 9f, 9f, p(0xfff26b3a.toInt()))
                text(c, value, "SemiBold", 11f, white, px + 22 - bw / 2 - label("SemiBold", 11f, white).measureText(value) / 2, h / 2 - 22, 18f)
            }
        }
        val radius = (22 * (1 - 0.12 * f.press)).toFloat()
        c.drawCircle(f.fingerX.toFloat(), f.fingerY.toFloat(), radius, p(0xff141414.toInt(), 0.08 * f.finger))
        stroke(c, 1.5f, white, 0.8 * f.finger) { c.drawCircle(f.fingerX.toFloat(), f.fingerY.toFloat(), radius, it) }
        c.restore()
    }

    private fun control(c: Canvas, cx: Float, cy: Float, press: Float, reduced: Boolean, draw: () -> Unit) {
        c.save()
        c.translate(cx, cy)
        if (reduced) c.saveLayerAlpha(null, (press * 255).roundToInt()) else c.scale(press, press)
        draw()
        if (reduced) c.restore()
        c.restore()
    }

    private fun flat(c: Canvas, l: SmokedClocheRevealLayout, f: SmokedClocheRevealMotion.Frame) {
        val opacity = 1 - f.lift
        c.drawCircle(l.cx, l.cy, l.radius, p(white, 0.18 * opacity))
        stroke(c, 3f, white, 0.7 * opacity) { c.drawCircle(l.cx, l.cy, l.radius, it) }
        val smokeAlpha = if (f.mode == 1) 0.6 * (1 - f.lift) else 0.6
        val cloud = p(white, smokeAlpha)
        cloud.shader = RadialGradient(l.cx, l.cy, l.radius * 0.92f, intArrayOf(0xb3f0eeea.toInt(), 0x40f0eeea), null, Shader.TileMode.CLAMP)
        c.drawCircle(l.cx, l.cy, l.radius * 0.92f, cloud)
        cloud.shader = null
    }

    fun close() {
        background?.recycle(); copy?.recycle(); dish.recycle(); leaves.forEach { it.recycle() }; smoke.close()
    }
}
