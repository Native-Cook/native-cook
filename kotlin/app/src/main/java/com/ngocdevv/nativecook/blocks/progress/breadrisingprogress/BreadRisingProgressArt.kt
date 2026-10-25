package com.ngocdevv.nativecook.blocks.progress.breadrisingprogress

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BASE_Y
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BOULE_CY
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BOWL_RX
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BOWL_RY
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.BUBBLES
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.HALF
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.LOOP
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.RIM_Y
import com.ngocdevv.nativecook.blocks.progress.breadrisingprogress.BreadRisingProgressMotion.hash
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The bread's art, ported from breadArt.ts and the Skia tree of BreadRisingProgress.tsx. Everything
 that does not change with the timeline (backdrop with its grain, oven, cloth, stone, both walls of
 the glass bowl, the ready badge) is baked once, off the main thread, into bitmaps at screen
 resolution; a frame only blits those with an opacity and an offset and draws the dough, its
 bubbles and score, and the steam. Blur sigmas are Skia's (converted with sigmaToRadius).
 */

private fun argb(r: Int, g: Int, b: Int, a: Double) = ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
private fun hex(v: Long) = (0xFF000000L or v).toInt()

private fun blur(sigma: Float, style: BlurMaskFilter.Blur = BlurMaskFilter.Blur.NORMAL) = BlurMaskFilter(sigmaToRadius(sigma), style)

private fun fill(color: Int = 0xFF000000.toInt()) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
private fun stroke(width: Float, color: Int, round: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    this.color = color
    if (round) {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
}
private fun vGrad(y0: Float, y1: Float, vararg colors: Int) = LinearGradient(0f, y0, 0f, y1, colors, null, Shader.TileMode.CLAMP)

internal object BreadArt {
    private val rimRect = RectF(-BOWL_RX, RIM_Y - RIM_RY, BOWL_RX, RIM_Y + RIM_RY)

    val back = Path().apply { addArc(rimRect, 180f, 180f) }
    val front = Path().apply {
        moveTo(-BOWL_RX, RIM_Y)
        cubicTo(-BOWL_RX, RIM_Y + BOWL_RY * 0.75f, -BOWL_RX * 0.55f, RIM_Y + BOWL_RY, 0f, RIM_Y + BOWL_RY)
        cubicTo(BOWL_RX * 0.55f, RIM_Y + BOWL_RY, BOWL_RX, RIM_Y + BOWL_RY * 0.75f, BOWL_RX, RIM_Y)
        addArc(rimRect, 0f, 180f)
    }
    val rim = Path().apply { addArc(rimRect, 0f, 180f) }
    val streaks = Path().apply {
        moveTo(-BOWL_RX + 14, RIM_Y + 18)
        cubicTo(-BOWL_RX + 12, RIM_Y + 70, -BOWL_RX + 34, RIM_Y + 112, -BOWL_RX * 0.42f, RIM_Y + BOWL_RY - 14)
        moveTo(BOWL_RX - 22, RIM_Y + 24)
        cubicTo(BOWL_RX - 22, RIM_Y + 52, BOWL_RX - 30, RIM_Y + 74, BOWL_RX - 44, RIM_Y + 96)
    }
    val foot = Path().apply { addOval(RectF(-48f, RIM_Y + BOWL_RY - 9, 48f, RIM_Y + BOWL_RY + 7), Path.Direction.CW) }

    val cloth = Path().apply {
        val top = BOWL_RY - 12
        moveTo(-158f, top)
        lineTo(158f, top)
        cubicTo(166f, top + 20, 176f, top + 40, 182f, top + 64)
        val hem = top + 64
        for (k in 0 until 6) {
            val x0 = 182f - k * 60.7f
            quadTo(x0 - 30, hem + if (k % 2 == 0) 7 else -3, x0 - 60.7f, hem)
        }
        cubicTo(-176f, top + 40, -166f, top + 20, -158f, top)
        close()
    }
    val folds = Path().apply {
        for (x in floatArrayOf(-120f, -52f, 30f, 104f)) {
            moveTo(x, BOWL_RY + 14)
            quadTo(x + 6, BOWL_RY + 34, x + 2, BOWL_RY + 56)
        }
    }

    /** Seeded speckles (flour on raw dough, blisters on the crust) over the dough's area. */
    private fun speckles(seed: Double, count: Int, rMin: Double, rMax: Double) = Path().apply {
        for (i in 0 until count) {
            val x = (hash(seed + i * 1.37) - 0.5) * 240
            val y = -80 + hash(seed + i * 2.71) * (BOULE_CY + 10)
            addCircle(x.toFloat(), y.toFloat(), (rMin + (rMax - rMin) * hash(seed + i * 4.13)).toFloat(), Path.Direction.CW)
        }
    }
    val flour = speckles(3.0, 70, 0.6, 1.4)
    val blisters = speckles(41.0, 90, 0.8, 2.2)

    val check = Path().apply {
        moveTo(-6.5f, 0.5f)
        lineTo(-2f, 5f)
        lineTo(7f, -5f)
    }

    const val RIM_RY = 15f
}

/** Grain for the backdrop: a soft-light hash, the same maths as the RN shader, at one pixel per cell. */
private object BreadGrain {
    private fun h(x: Double, y: Double): Double {
        val s = sin(x * 12.9898 + y * 78.233) * 43758.5453
        return s - floor(s)
    }

    /** Grain of the region [x0, x0 + w] × [y0, y0 + h] (local units) at 1.4 pixels a unit, opaque grey. */
    fun bitmap(x0: Float, y0: Float, w: Float, h: Float): Bitmap {
        val k = 1.4
        val bw = ceil(w * k).toInt().coerceAtLeast(1)
        val bh = ceil(h * k).toInt().coerceAtLeast(1)
        val c0 = floor(x0 * k).toInt()
        val r0 = floor(y0 * k).toInt()
        val px = IntArray(bw * bh)
        for (j in 0 until bh) {
            val cy = (r0 + j).toDouble()
            val py = (cy + 0.5) / k
            for (i in 0 until bw) {
                val cx = (c0 + i).toDouble()
                val pxl = (cx + 0.5) / k
                val n = h(cx, cy) * 0.6 + h(floor(pxl * 0.4) + 19.0, floor(py * 0.4) + 19.0) * 0.4
                val g = (n * 255).roundToInt().coerceIn(0, 255)
                px[j * bw + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
        return Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
    }

    /** Draws grain over [region] of [canvas] (local units) with soft light at [amount]. */
    fun softLight(canvas: Canvas, region: RectF, amount: Float) {
        val bmp = bitmap(region.left, region.top, region.width(), region.height())
        val k = 1.4f
        val c0 = floor(region.left * k)
        val r0 = floor(region.top * k)
        val dst = RectF(c0 / k, r0 / k, (c0 + bmp.width) / k, (r0 + bmp.height) / k)
        val paint = Paint().apply {
            alpha = (amount * 255).roundToInt()
            isFilterBitmap = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.SOFT_LIGHT
            else xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        }
        canvas.drawBitmap(bmp, null, dst, paint)
        bmp.recycle()
    }
}

/** A static picture in hero units: its bitmap and the hero rectangle it covers. */
internal class BreadLayer(val bitmap: Bitmap, val bounds: RectF)

/** Every baked picture of the block, for one layout. */
internal class BreadPictures(
    val backdrop: Bitmap,
    val oven: BreadLayer,
    val cloth: BreadLayer,
    val stone: BreadLayer,
    val bowlBack: BreadLayer,
    val bowlFront: BreadLayer,
    val badge: BreadLayer,
)

internal object BreadBake {
    /** Bakes a hero-space picture covering [bounds] at [scale] pixels a unit. */
    private fun layer(bounds: RectF, scale: Float, draw: (Canvas) -> Unit): BreadLayer {
        val w = ceil(bounds.width() * scale).toInt().coerceAtLeast(1)
        val h = ceil(bounds.height() * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(-bounds.left, -bounds.top)
        draw(c)
        bmp.prepareToDraw()
        return BreadLayer(bmp, RectF(bounds.left, bounds.top, bounds.left + w / scale, bounds.top + h / scale))
    }

    /** All the static art for a screen of [w] × [h] dp, the counter at [counterY], at [density] px a dp. */
    fun bake(w: Float, h: Float, counterY: Float, heroScale: Float, density: Float): BreadPictures {
        // Backdrop, in screen dp.
        val bw = (w * density).roundToInt().coerceAtLeast(1)
        val bh = (h * density).roundToInt().coerceAtLeast(1)
        val backdrop = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        Canvas(backdrop).apply {
            scale(density, density)
            drawRect(0f, 0f, w, h, fill().apply {
                shader = LinearGradient(0f, 0f, w * 0.35f, h, intArrayOf(hex(0xF7F0E5), hex(0xF0E5D3), hex(0xE7D7BE)), null, Shader.TileMode.CLAMP)
            })
            drawRect(0f, counterY, w, h, fill().apply { shader = vGrad(counterY, h, hex(0xDCC8A8), hex(0xD2BC98), hex(0xC4AB84)) })
            drawRect(0f, counterY, w, counterY + 1.5f, fill(argb(255, 250, 240, 0.7)))
            BreadGrain.softLight(this, RectF(0f, 0f, w, h), 0.45f)
        }
        backdrop.prepareToDraw()

        val s = density * heroScale

        val oven = layer(RectF(-260f, -265f, 260f, 290f), s) { c ->
            val box = RectF(-172f, -170f, 172f, BASE_Y + 26)
            c.drawRoundRect(box, 30f, 30f, fill().apply {
                shader = vGrad(-170f, BASE_Y + 26, hex(0x2B1810), hex(0x3A2015), hex(0x24140D))
                setShadowLayer(sigmaToRadius(30f), 0f, 14f, argb(60, 30, 10, 0.35))
            })
            c.drawCircle(0f, -10f, 170f, fill().apply {
                shader = RadialGradient(0f, -10f, 170f, intArrayOf(argb(255, 150, 70, 0.42), argb(255, 110, 40, 0.14), argb(255, 90, 30, 0.0)), null, Shader.TileMode.CLAMP)
            })
            for (y in floatArrayOf(-150f, -139f)) {
                c.drawRoundRect(RectF(-136f, y, 136f, y + 4), 2f, 2f, fill(hex(0xFF7A2E)).apply { maskFilter = blur(7f, BlurMaskFilter.Blur.SOLID) })
                c.drawRoundRect(RectF(-136f, y + 1, 136f, y + 3), 1f, 1f, fill(hex(0xFFD3A0)))
            }
            c.drawRoundRect(RectF(-171f, -169f, 171f, BASE_Y + 25), 29f, 29f, stroke(2f, argb(255, 220, 180, 0.16)))
        }

        val cloth = layer(RectF(-215f, BOWL_RY - 40, 215f, BOWL_RY + 100), s) { c ->
            val a = BreadArt
            c.drawPath(a.cloth, fill().apply {
                shader = vGrad(BOWL_RY - 14, BOWL_RY + 62, hex(0xF4EFE6), hex(0xE8E0D2), hex(0xD6CBB8))
                setShadowLayer(sigmaToRadius(10f), 0f, 6f, argb(80, 55, 25, 0.25))
            })
            c.save()
            c.clipPath(a.cloth)
            c.drawRect(-200f, BOWL_RY + 38, 200f, BOWL_RY + 42, fill(argb(84, 118, 148, 0.6)))
            c.drawRect(-200f, BOWL_RY + 46, 200f, BOWL_RY + 48, fill(argb(84, 118, 148, 0.5)))
            c.drawPath(a.folds, stroke(10f, argb(120, 95, 60, 0.14)).apply { maskFilter = blur(6f) })
            BreadGrain.softLight(c, RectF(-185f, BOWL_RY - 14, 185f, BOWL_RY + 62), 0.28f)
            c.restore()
            c.drawOval(RectF(-112f, BOWL_RY - 6, 112f, BOWL_RY + 16), fill(argb(80, 50, 20, 0.26)).apply { maskFilter = blur(12f) })
            c.drawOval(RectF(-58f, BOWL_RY - 5, 58f, BOWL_RY + 6), fill(argb(60, 38, 16, 0.42)).apply { maskFilter = blur(4f) })
        }

        val stone = layer(RectF(-200f, BASE_Y - 50, 200f, BASE_Y + 85), s) { c ->
            c.drawOval(RectF(-150f, BASE_Y - 6, 150f, BASE_Y + 28), fill().apply {
                shader = vGrad(BASE_Y - 6, BASE_Y + 28, hex(0x57504B), hex(0x3B3532), hex(0x2A2522))
                setShadowLayer(sigmaToRadius(16f), 0f, 8f, argb(40, 24, 10, 0.35))
            })
            c.drawOval(RectF(-120f, BASE_Y + 2, 120f, BASE_Y + 18), fill(argb(20, 10, 4, 0.45)).apply { maskFilter = blur(6f) })
        }

        val glass = RectF(-150f, -35f, 150f, BOWL_RY + 25)
        val bowlBack = layer(glass, s) { c ->
            val a = BreadArt
            c.drawPath(a.front, fill().apply { shader = vGrad(0f, BOWL_RY, argb(96, 140, 132, 0.22), argb(120, 160, 152, 0.14), argb(70, 110, 104, 0.26)) })
            c.drawPath(a.back, stroke(9f, argb(50, 85, 80, 0.18)).apply { maskFilter = blur(5f) })
            c.drawPath(a.back, stroke(2.4f, argb(255, 255, 255, 0.7)))
            c.drawPath(a.back, stroke(1f, argb(80, 125, 118, 0.5)))
        }

        val bowlFront = layer(glass, s) { c ->
            val a = BreadArt
            c.drawPath(a.front, fill().apply {
                shader = LinearGradient(
                    -BOWL_RX, 0f, BOWL_RX, 0f,
                    intArrayOf(argb(120, 170, 160, 0.5), argb(210, 236, 230, 0.16), argb(255, 255, 255, 0.04), argb(200, 230, 224, 0.14), argb(110, 160, 150, 0.46)),
                    floatArrayOf(0f, 0.16f, 0.5f, 0.84f, 1f), Shader.TileMode.CLAMP,
                )
            })
            c.drawPath(a.streaks, stroke(5f, argb(255, 255, 255, 0.55), round = true).apply { maskFilter = blur(2f) })
            c.drawPath(a.front, stroke(2.2f, argb(70, 115, 108, 0.55)))
            c.drawPath(a.front, stroke(1f, argb(255, 255, 255, 0.75)))
            c.drawPath(a.rim, stroke(6f, argb(150, 195, 186, 0.45)))
            c.drawPath(a.rim, stroke(3f, argb(255, 255, 255, 0.9)))
            c.save()
            c.translate(0f, 3f)
            c.drawPath(a.rim, stroke(1f, argb(60, 105, 98, 0.55)))
            c.restore()
            c.drawPath(a.foot, stroke(2f, argb(255, 255, 255, 0.6)))
        }

        // The badge overshoots its size a little, so it is baked a touch sharper.
        val badge = layer(RectF(-46f, -46f, 46f, 56f), s * 1.25f) { c ->
            c.drawCircle(0f, 0f, 20f, fill().apply {
                shader = vGrad(-20f, 20f, hex(0x6FAE6E), hex(0x4E8F52), hex(0x3D7442))
                setShadowLayer(sigmaToRadius(8f), 0f, 4f, argb(30, 60, 30, 0.35))
            })
            c.drawPath(BreadArt.check, stroke(3f, hex(0xF4FBF2), round = true))
        }

        return BreadPictures(backdrop, oven, cloth, stone, bowlBack, bowlFront, badge)
    }
}

/** Draws what changes every frame: the dough with its sheen, speckles, bubbles and score, and the steam. */
internal class BreadPainter {
    private val raw = intArrayOf(hex(0xF8E6C8), hex(0xEDCFA2), hex(0xD6AE7C))
    private val baked = intArrayOf(hex(0xD99A52), hex(0xB06A2E), hex(0x7A431B))
    private val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private val points = FloatArray(HALF * 4)
    private val bubble = FloatArray(5)
    private val dough = Path()
    private val bubbles = Path()
    private val rings = Path()
    private val score = Path()
    private val ear = Path()
    private val steam = Path()

    private val doughPaint = fill()
    private val sheenPaint = fill()
    private val flourPaint = fill(hex(0xFFFDF8))
    private val blisterPaint = fill(hex(0x4A250C))
    private val innerPaint = stroke(14f, argb(130, 74, 28, 0.2)).apply { if (blurOk) maskFilter = blur(8f) }
    private val bubblePaint = fill(argb(255, 250, 238, 0.85)).apply { if (blurOk) setShadowLayer(sigmaToRadius(0.8f), 0f, 0.6f, argb(150, 110, 60, 0.6)) }
    private val ringPaint = stroke(1f, argb(170, 130, 80, 0.55))
    private val scorePaint = fill().apply { shader = vGrad(BOULE_CY - 90, BOULE_CY - 60, hex(0xF6E2BC), hex(0xE9C890), hex(0xD8AE70)) }
    private val earPaint = stroke(3f, argb(255, 230, 180, 0.75), round = true).apply { if (blurOk) setShadowLayer(sigmaToRadius(2f), 0f, 2f, argb(70, 30, 8, 0.45)) }
    private val steamWide = stroke(12f, argb(255, 250, 244, 0.22), round = true).apply { if (blurOk) maskFilter = blur(9f) }
    private val steamThin = stroke(3f, argb(255, 250, 244, 0.18), round = true).apply { if (blurOk) maskFilter = blur(3f) }
    // Without blur (before API 28) the steam is a few wider, fainter strokes.
    private val steamSoft = intArrayOf(26, 18, 10)
    private val steamSoftPaint = stroke(1f, argb(255, 250, 244, 0.07), round = true)
    private val layerBounds = RectF()

    private fun mix(i: Int, t: Float): Int {
        val a = raw[i]
        val b = baked[i]
        fun ch(sh: Int) = (((a shr sh) and 255) * (1 - t) + ((b shr sh) and 255) * t).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** A closed path through the points, smoothed with quadratic segments through midpoints. */
    private fun outline(p: FloatArray) {
        val n = p.size / 2
        fun mx(i: Int) = (p[2 * (i % n)] + p[2 * ((i + 1) % n)]) / 2
        fun my(i: Int) = (p[2 * (i % n) + 1] + p[2 * ((i + 1) % n) + 1]) / 2
        dough.rewind()
        dough.moveTo(mx(0), my(0))
        for (i in 1..n) dough.quadTo(p[2 * (i % n)], p[2 * (i % n) + 1], mx(i), my(i))
        dough.close()
    }

    /** In hero units. */
    fun dough(c: Canvas, b: BreadRisingProgressMotion.Bread) {
        BreadRisingProgressMotion.doughPoints(b.rise, b.shape, points)
        outline(points)
        val top = BreadRisingProgressMotion.doughTop(b.rise, b.shape)
        val bake = b.bake.toFloat()
        doughPaint.shader = LinearGradient(0f, top, 0f, BOULE_CY + 24, intArrayOf(mix(0, bake), mix(1, bake), mix(2, bake)), null, Shader.TileMode.CLAMP)
        c.drawPath(dough, doughPaint)

        c.save()
        c.clipPath(dough)
        val sy = top + 30
        sheenPaint.shader = RadialGradient(-38f, sy, 96f, intArrayOf(argb(255, 250, 238, 0.75), argb(255, 246, 228, 0.25), argb(255, 246, 228, 0.0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(-38f, sy, 96f, sheenPaint)
        val fa = 0.55 * (1 - b.bake)
        if (fa > 0.003) {
            flourPaint.alpha = (fa * 255).roundToInt()
            c.drawPath(BreadArt.flour, flourPaint)
        }
        val ba = 0.5 * b.bake
        if (ba > 0.003) {
            blisterPaint.alpha = (ba * 255).roundToInt()
            c.drawPath(BreadArt.blisters, blisterPaint)
        }
        if (!blurOk) innerPaint.alpha = (0.12 * 255).roundToInt()
        c.drawPath(dough, innerPaint)
        c.restore()

        // Bubbles swell and pop on the rising dome.
        bubbles.rewind()
        rings.rewind()
        var anyFill = false
        var anyRing = false
        for (i in 0 until BUBBLES) {
            BreadRisingProgressMotion.bubbleAt(i, b.t, b.rise, bubble)
            if (bubble[2] > 0.2f) {
                bubbles.addCircle(bubble[0], bubble[1], bubble[2], Path.Direction.CW)
                anyFill = true
            }
            if (bubble[4] > 0.02f) {
                rings.addCircle(bubble[0], bubble[1], bubble[3], Path.Direction.CW)
                anyRing = true
            }
        }
        if (anyFill) c.drawPath(bubbles, bubblePaint)
        if (anyRing) c.drawPath(rings, ringPaint)

        // The score blooms open as the crust bakes.
        val scoreA = (min(1.0, max(0.0, (b.shape - 0.9) * 10)) * b.stone).toFloat()
        if (scoreA > 0.003f) {
            val y0 = top + 38
            val w = 1 + 13 * bake
            score.rewind()
            score.moveTo(-62f, y0 + 18)
            score.cubicTo(-30f, y0 - 6 - w * 0.6f, 30f, y0 - 14 - w * 0.6f, 64f, y0 - 2)
            score.cubicTo(30f, y0 - 14 + w, -30f, y0 - 6 + w, -62f, y0 + 18)
            score.close()
            ear.rewind()
            ear.moveTo(-60f, y0 + 17)
            ear.cubicTo(-30f, y0 - 6 - w * 0.6f, 30f, y0 - 14 - w * 0.6f, 62f, y0 - 2)
            layerBounds.set(-80f, y0 - 40, 80f, y0 + 40)
            val save = if (scoreA < 0.999f) c.saveLayerAlpha(layerBounds, (scoreA * 255).roundToInt()) else c.save()
            c.drawPath(score, scorePaint)
            c.drawPath(ear, earPaint)
            c.restoreToCount(save)
        }
    }

    /** Three steam wisps rising from the crust; their waves turn a whole number of times a loop. */
    fun steam(c: Canvas, b: BreadRisingProgressMotion.Bread) {
        val a = b.steam.toFloat()
        if (a <= 0.001f) return
        val top = BreadRisingProgressMotion.doughTop(b.rise, b.shape)
        steam.rewind()
        for (w in 0 until 3) {
            val x0 = (w - 1) * 44f + 6
            val ph = 2 * PI * (3 + w) * b.t / LOOP + w * 1.7
            for (j in 0..12) {
                val y = top - 6 - j * 11
                val x = x0 + (4 + j * 0.9f) * sin(j * 0.55 - ph).toFloat() + j * (w - 1) * 1.2f
                if (j == 0) steam.moveTo(x, y) else steam.lineTo(x, y)
            }
        }
        layerBounds.set(-130f, top - 175, 130f, top + 20)
        val save = if (a < 0.999f) c.saveLayerAlpha(layerBounds, (a * 255).roundToInt()) else c.save()
        if (blurOk) {
            c.drawPath(steam, steamWide)
            c.drawPath(steam, steamThin)
        } else {
            for (wd in steamSoft) {
                steamSoftPaint.strokeWidth = wd.toFloat()
                c.drawPath(steam, steamSoftPaint)
            }
        }
        c.restoreToCount(save)
    }
}

/** Blits a baked layer at its hero rectangle, moved down by [dy] units, at [alpha]. */
internal fun Canvas.drawBreadLayer(layer: BreadLayer, alpha: Float, dy: Float, paint: Paint, dst: RectF, src: Rect) {
    if (alpha <= 0.003f) return
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
    src.set(0, 0, layer.bitmap.width, layer.bitmap.height)
    dst.set(layer.bounds.left, layer.bounds.top + dy, layer.bounds.right, layer.bounds.bottom + dy)
    drawBitmap(layer.bitmap, src, dst, paint)
}
