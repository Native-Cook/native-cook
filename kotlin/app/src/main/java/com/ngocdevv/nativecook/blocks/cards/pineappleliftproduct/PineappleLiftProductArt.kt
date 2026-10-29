package com.ngocdevv.nativecook.blocks.cards.pineappleliftproduct

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class PineappleLiftProductResources(
    val top: Bitmap?,
    val bottom: Bitmap?,
    val bottle: Bitmap,
    val flower: Bitmap?,
    val grain: Bitmap,
    val sans: Typeface,
    val semibold: Typeface,
    val bold: Typeface,
    val medium: Typeface,
    val serif: Typeface,
)

/** Layout, artwork and drawing of Pineapple Lift Product, ported from PineappleLiftProductArt.swift. */
object PineappleLiftProductArt {
    private val m = PineappleLiftProductMotion

    // Palette
    const val INK = 0x2A1803
    const val INK2 = 0x3A2304
    const val CREAM = 0xFFF4D6
    const val SKY_TOP = 0xFFCB2E
    const val SKY_MID = 0xFFB506
    const val SKY_LOW = 0xF7A100
    const val HAZE = 0xFFE27A
    const val GLOW = 0xFFF1B8
    const val JUICE = 0xFFE58A
    const val JUICE_RIM = 0xB86A00
    const val SHADE = 0x7A4100
    const val PETAL = 0xC8361E

    fun argb(hex: Int, alpha: Double = 1.0): Int =
        ((alpha.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 24) or (hex and 0xFFFFFF)

    // Artwork (bundled px)
    const val PINE = 0.345 // pt per px of the pineapple halves
    const val TOP_W = 632f
    const val TOP_H = 837f
    const val TOP_PX = 229.6f // centre of its cut face
    const val TOP_PY = 748f
    const val TOP_TILT = 16.7 // degrees, the cut face's tilt in the image
    const val BOTTOM_W = 488.0
    const val BOTTOM_H = 366.0
    const val BOTTOM_FX = 242.4
    const val BOTTOM_FY = 66.0
    const val FACE_RX = 218.0
    const val FACE_RY = 53.0
    const val BOTTLE_W = 276.0
    const val BOTTLE_PX_H = 716.0
    const val BOTTLE_H = 200.0
    val bottleScale get() = BOTTLE_H / BOTTLE_PX_H
    val bottleWidth get() = BOTTLE_W * bottleScale
    const val FLOWER_W = 400.0
    const val FLOWER_H = 392.0

    private fun loadBitmap(context: Context, name: String): Bitmap? = runCatching {
        context.assets.open("pineappleliftproduct/pineappleliftproduct_$name.webp").use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })?.also { it.prepareToDraw() }
        }
    }.getOrNull()

    private fun weight(w: Int, fallbackBold: Boolean): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, w, false)
        else if (fallbackBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    fun load(context: Context): PineappleLiftProductResources {
        val semibold = weight(600, true)
        val medium = weight(500, false)
        val serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val n = 128
        val pixels = IntArray(n * n) { i ->
            val v = (m.hash(i * 0.731 + 0.5) * 255).toInt()
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val grain = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        val bottle = labelledBottle(loadBitmap(context, "bottle"), semibold, medium)
        return PineappleLiftProductResources(
            top = loadBitmap(context, "pineapple-top"),
            bottom = loadBitmap(context, "pineapple-bottom"),
            bottle = bottle,
            flower = loadBitmap(context, "hibiscus"),
            grain = grain,
            sans = Typeface.DEFAULT,
            semibold = semibold,
            bold = weight(700, true),
            medium = medium,
            serif = serif,
        )
    }

    /** The bottle with its label printed once (3×), so a frame draws one bitmap. */
    private fun labelledBottle(bottle: Bitmap?, semibold: Typeface, medium: Typeface): Bitmap {
        val w = bottleWidth
        val h = BOTTLE_H
        val out = Bitmap.createBitmap((w * 3).toInt(), (h * 3).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(3f, 3f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (bottle != null) c.drawBitmap(bottle, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        val cx = w / 2
        drawLine(c, "ISLA BLOOM", cx, 88.0, 5.5, semibold, argb(INK2, 0.9), 1.3, Paint.Align.CENTER)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        paint.color = argb(INK2, 0.5)
        c.drawLine((cx - 12).toFloat(), 94.5f, (cx + 12).toFloat(), 94.5f, paint)
        drawLine(c, "PINEAPPLE", cx, 108.0, 10.5, Typeface.create(Typeface.SERIF, Typeface.BOLD), argb(INK2), 0.4, Paint.Align.CENTER)
        drawLine(c, "body oil", cx, 121.0, 9.0, Typeface.create(Typeface.SERIF, Typeface.ITALIC), argb(INK2), 0.0, Paint.Align.CENTER)
        paint.style = Paint.Style.FILL
        paint.color = argb(PETAL, 0.9)
        for (i in 0 until 5) {
            val a = i / 5.0 * 2 * PI - PI / 2
            c.drawCircle((cx + cos(a) * 2.6).toFloat(), (136 + sin(a) * 2.6).toFloat(), 2.2f, paint)
        }
        paint.color = argb(0xF2C230)
        c.drawCircle(cx.toFloat(), 136f, 1f, paint)
        drawLine(c, "100 ml  ·  3.4 fl oz", cx, 152.0, 5.0, medium, argb(INK2, 0.75), 0.3, Paint.Align.CENTER)
        out.prepareToDraw()
        return out
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    /** One line of text whose line box is centred on [cy]; [tracking] in pt. */
    fun drawLine(
        canvas: Canvas, text: String, x: Double, cy: Double, size: Double, typeface: Typeface,
        color: Int, tracking: Double = 0.0, align: Paint.Align = Paint.Align.LEFT,
    ) {
        val p = textPaint
        p.typeface = typeface
        p.textSize = size.toFloat()
        p.letterSpacing = (tracking / size).toFloat()
        p.color = color
        p.textAlign = align
        val fm = p.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2
        canvas.drawText(text, x.toFloat(), baseline.toFloat(), p)
    }

    // Layout

    class Layout(val height: Double, val top: Double, val bottom: Double) {
        val ctaTop = height - bottom - 12 - 56
        val heroBase = ctaTop - 132
        val barY = top + 26
        val captionY = ctaTop - 106
        val titleY = ctaTop - 76
        val descY = ctaTop - 38
        /** Centre of the bottom half's cut face. */
        val faceX = 196.5
        val faceY = heroBase - BOTTOM_H * PINE + BOTTOM_FY * PINE
        val rx = FACE_RX * PINE
        val ry = FACE_RY * PINE
    }

    class Flower(
        val x: Double, val y: Double, val fromTop: Boolean, val size: Double, val rot: Double,
        val dx: Double, val dy: Double, val drot: Double, val phase: Double,
    )

    val flowers = listOf(
        Flower(64.0, 150.0, true, 128.0, -18.0, -16.0, -26.0, 14.0, 0.0),
        Flower(8.0, -190.0, false, 104.0, 28.0, -18.0, -14.0, -12.0, 2.1),
        Flower(388.0, -150.0, false, 112.0, -36.0, 20.0, -24.0, 16.0, 4.2),
    )

    // Drawing

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val drops = ArrayList<PineappleLiftProductMotion.Drop>(64)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }

    private fun fresh(): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = 0xFF000000.toInt()
        return paint
    }

    /** A radial falloff squashed into an ellipse: full [alpha] at the centre, 0 at the rim. */
    private fun softEllipse(canvas: Canvas, x: Double, y: Double, rx: Double, ry: Double, hex: Int, alpha: Double) {
        if (alpha <= 0.001) return
        val p = fresh()
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.scale(1f, (ry / rx).toFloat())
        p.shader = RadialGradient(0f, 0f, rx.toFloat(), intArrayOf(argb(hex, alpha), argb(hex, alpha * 0.45), argb(hex, 0.0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, rx.toFloat(), p)
        canvas.restore()
    }

    /** Everything above the bottom half's front rim: what comes out of the fruit is drawn there. */
    private fun rimClip(l: Layout, sink: Double): Path {
        path.reset()
        path.moveTo(-60f, -2000f)
        path.lineTo(453f, -2000f)
        path.lineTo(453f, l.faceY.toFloat())
        val n = 36
        for (i in 0..n) {
            val a = i.toDouble() / n * PI
            path.lineTo((l.faceX + cos(a) * l.rx * 0.98).toFloat(), (l.faceY + sin(a) * (l.ry + sink)).toFloat())
        }
        path.lineTo(-60f, l.faceY.toFloat())
        path.close()
        return path
    }

    fun drawBackground(canvas: Canvas, l: Layout, res: PineappleLiftProductResources) {
        val h = l.height.toFloat()
        var p = fresh()
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(argb(SKY_TOP), argb(SKY_MID), argb(SKY_LOW)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 393f, h, p)
        p = fresh()
        p.shader = RadialGradient(l.faceX.toFloat(), (l.faceY - 70).toFloat(), 280f, argb(HAZE, 0.55), argb(HAZE, 0.0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 393f, h, p)
        if (Build.VERSION.SDK_INT >= 29) {
            p = fresh()
            val shader = BitmapShader(res.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            shader.setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
            p.shader = shader
            p.alpha = (0.045 * 255).toInt()
            p.blendMode = BlendMode.OVERLAY
            canvas.drawRect(0f, 0f, 393f, h, p)
        }
    }

    fun drawFlowers(canvas: Canvas, l: Layout, res: PineappleLiftProductResources, drift: Double, ambient: Double) {
        val image = res.flower ?: return
        val p = fresh()
        for (fl in flowers) {
            val a = 2 * PI * ambient / m.LOOP + fl.phase
            val x = fl.x + fl.dx * drift + 3 * sin(a)
            val y = (if (fl.fromTop) l.top else l.heroBase) + fl.y + fl.dy * drift + 2.5 * cos(a)
            val rot = fl.rot + fl.drot * drift + 3 * sin(a + 0.7)
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.rotate(rot.toFloat())
            val h = fl.size * FLOWER_H / FLOWER_W
            rect.set((-fl.size / 2).toFloat(), (-h / 2).toFloat(), (fl.size / 2).toFloat(), (h / 2).toFloat())
            canvas.drawBitmap(image, null, rect, p)
            canvas.restore()
        }
    }

    fun drawHero(canvas: Canvas, l: Layout, res: PineappleLiftProductResources, fr: PineappleLiftProductMotion.Frame, ambient: Double) {
        val pine = PINE
        // Contact shadow on the backdrop.
        softEllipse(canvas, 196.5, l.heroBase - 2, 96.0, 13.0, SHADE, 0.38)

        // The bottle's pose: centre, rotation and scale for the rise, with its hover bob.
        val r = fr.rise
        val hover = m.clamp(r)
        val wave = 2 * PI * 3 * ambient / m.LOOP
        val bx = 196.5 - 10.5 * r
        val by = l.faceY + 135 + (-243) * r + 4 * sin(wave) * hover
        val bAngle = -9 * r + 1.5 * sin(wave + 1.3) * hover
        val bScale = 0.92 + 0.08 * hover

        // Glow behind the bottle.
        if (fr.glow > 0.001) {
            val p = fresh()
            p.shader = RadialGradient(bx.toFloat(), by.toFloat(), 170f, argb(GLOW, 0.6 * fr.glow), argb(GLOW, 0.0), Shader.TileMode.CLAMP)
            canvas.drawCircle(bx.toFloat(), by.toFloat(), 170f, p)
        }

        // Bottom half.
        res.bottom?.let {
            val x = l.faceX - BOTTOM_FX * pine
            val y = l.heroBase - BOTTOM_H * pine
            rect.set(x.toFloat(), y.toFloat(), (x + BOTTOM_W * pine).toFloat(), (y + BOTTOM_H * pine).toFloat())
            canvas.drawBitmap(it, null, rect, fresh())
        }

        // The bottle's shadow on the cut face.
        if (hover > 0.001) {
            canvas.save()
            path.reset()
            rect.set((l.faceX - l.rx).toFloat(), (l.faceY - l.ry).toFloat(), (l.faceX + l.rx).toFloat(), (l.faceY + l.ry).toFloat())
            path.addOval(rect, Path.Direction.CW)
            canvas.clipPath(path)
            softEllipse(canvas, l.faceX - 10, l.faceY + 1.5, 38 - 7 * hover, 8.0, SHADE, 0.34 * hover)
            canvas.restore()
        }

        // Bottle, pulled out of the fruit.
        if (r > -0.2) {
            canvas.save()
            canvas.clipPath(rimClip(l, -2.0))
            canvas.translate(bx.toFloat(), by.toFloat())
            canvas.rotate(bAngle.toFloat())
            canvas.scale(bScale.toFloat(), bScale.toFloat())
            val w = bottleWidth
            rect.set((-w / 2).toFloat(), (-BOTTLE_H / 2).toFloat(), (w / 2).toFloat(), (BOTTLE_H / 2).toFloat())
            if (fr.sheen >= 0) {
                canvas.saveLayer(rect, null)
                canvas.drawBitmap(res.bottle, null, rect, fresh())
                drawSheen(canvas, rect, fr.sheen)
                canvas.restore()
            } else {
                canvas.drawBitmap(res.bottle, null, rect, fresh())
            }
            canvas.restore()
        }

        // Top half.
        res.top?.let {
            val o = fr.open
            val tx = l.faceX + 76 * o
            val ty = l.faceY + 34 + (-226) * o + fr.squash
            val angle = -TOP_TILT * (1 - o)
            canvas.save()
            canvas.clipPath(rimClip(l, 1.0))
            canvas.translate(tx.toFloat(), ty.toFloat())
            canvas.rotate(angle.toFloat())
            canvas.scale(pine.toFloat(), pine.toFloat())
            rect.set(-TOP_PX, -TOP_PY, TOP_W - TOP_PX, TOP_H - TOP_PY)
            canvas.drawBitmap(it, null, rect, fresh())
            canvas.restore()
        }
    }

    /** A soft diagonal band of light crossing the glass from left to right. */
    private fun drawSheen(canvas: Canvas, r: RectF, progress: Double) {
        val alpha = 0.55 * sin(PI * progress)
        val x = (r.left - 50 + (r.width() + 100) * progress).toFloat()
        val band = Path().apply {
            moveTo(x - 14, r.bottom)
            lineTo(x + 10, r.bottom)
            lineTo(x + 50, r.top)
            lineTo(x + 26, r.top)
            close()
        }
        sheenPaint.shader = LinearGradient(x - 14, r.centerY(), x + 50, r.centerY(),
            intArrayOf(argb(0xFFFFFF, 0.0), argb(0xFFFFFF, alpha), argb(0xFFFFFF, 0.0)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(band, sheenPaint)
    }

    fun drawDrops(canvas: Canvas, l: Layout, fr: PineappleLiftProductMotion.Frame) {
        for (b in fr.bursts) {
            // A bright ring flashes off the rim as the burst leaves it.
            if (b.age < 0.35) {
                val e = m.easeOutCubic(b.age / 0.35)
                val s = 1 + 0.7 * e
                val p = fresh()
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.5f
                p.color = argb(0xFFF6C8, 0.6 * (1 - e) * b.strength)
                rect.set((l.faceX - l.rx * s).toFloat(), (l.faceY - l.ry * s).toFloat(), (l.faceX + l.rx * s).toFloat(), (l.faceY + l.ry * s).toFloat())
                canvas.drawOval(rect, p)
            }
            m.drops(b.age, b.strength, l.rx, l.ry, drops)
            for (d in drops) {
                if (d.alpha <= 0.01) continue
                val speed = hypot(d.vx, d.vy)
                val stretch = 1 + min(speed / 900, 1.2)
                val len = d.r * stretch
                val wid = d.r / sqrt(stretch)
                canvas.save()
                canvas.translate((l.faceX + d.x).toFloat(), (l.faceY + d.y).toFloat())
                canvas.rotate(Math.toDegrees(atan2(d.vy, d.vx)).toFloat())
                rect.set(-len.toFloat(), -wid.toFloat(), len.toFloat(), wid.toFloat())
                val p = fresh()
                p.color = argb(JUICE, 0.88 * d.alpha)
                canvas.drawOval(rect, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = argb(JUICE_RIM, 0.7 * d.alpha)
                canvas.drawOval(rect, p)
                canvas.restore()
                val hp = fresh()
                hp.color = argb(0xFFFDF0, 0.9 * d.alpha)
                canvas.drawCircle((l.faceX + d.x - 0.28 * d.r).toFloat(), (l.faceY + d.y - 0.3 * d.r).toFloat(), (0.38 * d.r).toFloat(), hp)
            }
        }
    }

    // UI

    fun drawUi(canvas: Canvas, l: Layout, res: PineappleLiftProductResources) {
        // Top bar.
        drawLine(canvas, "ISLA BLOOM", 24.0, l.barY, 13.0, res.semibold, argb(INK2), 3.0)
        val cx = 349f
        val cy = l.barY.toFloat()
        var p = fresh()
        p.color = argb(INK2, 0.1)
        canvas.drawCircle(cx, cy, 20f, p)
        p = fresh()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = argb(INK2)
        rect.set(cx - 7.5f, cy - 3.5f, cx + 7.5f, cy + 9f)
        canvas.drawRoundRect(rect, 2.5f, 2.5f, p)
        path.reset()
        path.moveTo(cx - 4, cy - 3.5f)
        path.cubicTo(cx - 4, cy - 10, cx + 4, cy - 10, cx + 4, cy - 3.5f)
        canvas.drawPath(path, p)
        p = fresh()
        p.color = argb(INK)
        canvas.drawCircle(cx + 14, cy - 8, 8f, p)
        drawLine(canvas, "1", (cx + 14).toDouble(), l.barY - 8, 10.0, res.bold, argb(CREAM), 0.0, Paint.Align.CENTER)

        // Copy.
        drawLine(canvas, "BODY OIL  ·  NO. 07", 24.0, l.captionY, 11.0, res.semibold, argb(INK2, 0.65), 2.2)
        drawLine(canvas, "Golden Pineapple", 24.0, l.titleY, 34.0, res.serif, argb(INK))
        drawLine(canvas, "Cold-pressed pineapple & coconut for a sunlit glow.", 24.0, l.descY, 14.0, res.sans, argb(INK2, 0.72))
        drawLine(canvas, "$28", 24.0, l.ctaTop + 19, 26.0, res.bold, argb(INK))
        drawLine(canvas, "100 ml", 24.0, l.ctaTop + 43, 12.0, res.medium, argb(INK2, 0.6))

        // Add to bag.
        p = fresh()
        p.color = argb(INK)
        rect.set(169f, l.ctaTop.toFloat(), 369f, (l.ctaTop + 56).toFloat())
        canvas.drawRoundRect(rect, 28f, 28f, p)
        drawLine(canvas, "Add to bag", 269.0, l.ctaTop + 28, 16.0, res.semibold, argb(CREAM), 0.0, Paint.Align.CENTER)
    }
}
