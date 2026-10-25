package com.ngocdevv.nativecook.blocks.onboarding.hotairballoonwelcome

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Hot Air Balloon Welcome — everything drawn per frame, as in balloonArt.ts: the baked layers at
// their parallax offsets, and the balloon itself (gores, seams, light, the burner's glow and
// flame, rig and basket). In dp, on the screen's (hardware) canvas.

private fun argb(r: Int, g: Int, b: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
private fun hex(c: Long): Int = c.toInt()
private fun blur(sigma: Float) = BlurMaskFilter(max(0.01f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private val GoreRgb = arrayOf(intArrayOf(243, 230, 207), intArrayOf(229, 102, 79), intArrayOf(47, 127, 134), intArrayOf(232, 176, 74))

private fun rgb(c: IntArray, k: Float): Int = argb(min(255f, c[0] * k).roundToInt(), min(255f, c[1] * k).roundToInt(), min(255f, c[2] * k).roundToInt(), 1f)

internal class BalloonPainter(private val g: BalloonGeo, private val density: Float) {
    private val outline = Path()
    private val gore = Path()
    private val work = Path()
    private val rect = RectF()
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
        color = argb(50, 24, 20, 0.28f)
    }
    private val shadow = Paint().apply {
        shader = LinearGradient(-g.r, 0f, g.r * 0.4f, 0f, intArrayOf(argb(40, 30, 80, 0.5f), argb(40, 30, 80, 0.12f), argb(40, 30, 80, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val highlight = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        shader = RadialGradient(g.r * 0.36f, -g.e * 0.7f, g.r * 0.8f, intArrayOf(argb(255, 236, 214, 0.42f), argb(255, 220, 190, 0.1f), argb(255, 220, 190, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val tape = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = argb(60, 30, 24, 0.22f)
    }
    private val tapes = Path().apply {
        for (v in floatArrayOf(0.22f, 0.38f, 0.55f, 0.7f, 0.84f)) {
            val r = radiusAt(v)
            val y = -g.e * (1f - v)
            for (j in 0..16) {
                val x = -r + 2f * r * j / 16f
                val yy = y + 0.07f * r * sqrt(max(0f, 1f - (x / r) * (x / r)))
                if (j == 0) moveTo(x, yy) else lineTo(x, yy)
            }
        }
    }
    private val skirt = Paint().apply { color = argb(48, 28, 30, 0.6f) }
    private val add = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        shader = LinearGradient(-g.r, 0f, g.r, 0f, intArrayOf(argb(255, 200, 160, 0f), argb(255, 200, 160, 0.1f), argb(255, 214, 170, 0.75f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groundShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cable = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.9f
        color = argb(40, 26, 30, 0.85f)
    }
    private val steel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-9f, 0f, 9f, 0f, intArrayOf(hex(0xFF4A4E55), hex(0xFFC8CDD2), hex(0xFF3A3D42)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val wicker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-15f, 0f, 15f, 0f, intArrayOf(hex(0xFF6B4420), hex(0xFFA8763E), hex(0xFFC08A4C), hex(0xFF5E3B1C)), floatArrayOf(0f, 0.45f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }
    private val weave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 0.6f
        color = argb(50, 28, 10, 0.4f)
    }
    private val basket = Path().apply { addRoundRect(RectF(-15f, 44f, 15f, 66f), 4f, 4f, Path.Direction.CW) }
    private val hi = Paint().apply {
        strokeWidth = 0.8f
        color = argb(255, 210, 170, 0.35f)
    }

    init {
        // The envelope's outline, from the crown down the right side and back up the left.
        val n = 28
        outline.moveTo(0f, -g.e)
        for (i in 1..n) {
            val v = i.toFloat() / n
            outline.lineTo(radiusAt(v), -g.e * (1f - v))
        }
        for (i in n downTo 0) {
            val v = i.toFloat() / n
            outline.lineTo(-radiusAt(v), -g.e * (1f - v))
        }
        outline.close()
    }

    /** Envelope radius at v (0 = crown, 1 = mouth). */
    private fun radiusAt(v: Float): Float {
        if (v <= 0.55f) {
            val u = (0.55f - v) / 0.55f
            return g.r * sqrt(max(0f, 1f - u * u))
        }
        val u = (v - 0.55f) / 0.45f
        return g.r - (g.r - g.rm) * u.pow(1.25f)
    }

    private fun meridian(path: Path, a: Float, reverse: Boolean, start: Boolean) {
        val n = 20
        for (j in 0..n) {
            val i = if (reverse) n - j else j
            val v = i.toFloat() / n
            val x = radiusAt(v) * sin(a)
            val y = -g.e * (1f - v)
            if (start && j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    private fun drawEnvelope(canvas: Canvas, turn: Float, heat: Float) {
        val step = 2f * PI.toFloat() / GORES
        val light = 0.75f
        val tau = 2f * PI.toFloat()
        var seamsCount = 0
        val seams = FloatArray(GORES)
        for (k in 0 until GORES) {
            var a = k * step + turn
            a -= tau * floor((a + PI.toFloat()) / tau)
            var b = a + step
            val lo = max(a, -PI.toFloat() / 2f)
            val hiA = min(b, PI.toFloat() / 2f)
            if (hiA <= lo) continue
            a = lo
            b = hiA
            val mid = (a + b) / 2f
            val shade = 0.5f + 0.62f * max(0f, cos(mid - light)) + 0.08f * cos(mid)
            val col = GoreRgb[k % PATTERN]
            gorePaint.shader = LinearGradient(0f, -g.e, 0f, 0f, intArrayOf(rgb(col, shade * 1.06f), rgb(col, shade), rgb(col, shade * 0.62f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            gore.reset()
            meridian(gore, a, reverse = false, start = true)
            meridian(gore, b, reverse = true, start = false)
            gore.close()
            canvas.drawPath(gore, gorePaint)
            if (a > -PI.toFloat() / 2f + 0.01f) seams[seamsCount++] = a
        }
        work.reset()
        for (k in 0 until seamsCount) meridian(work, seams[k], reverse = false, start = true)
        canvas.drawPath(work, seam)
        canvas.save()
        canvas.clipPath(outline)
        // Cool shadow on the side away from the sun, warm highlight toward it.
        rect.set(-g.r - 2f, -g.e - 2f, g.r + 2f, 2f)
        canvas.drawRect(rect, shadow)
        canvas.drawRect(rect, highlight)
        // Load tapes round the envelope and the dark skirt at the mouth.
        canvas.drawPath(tapes, tape)
        canvas.drawRect(-g.r, -g.e * 0.09f, g.r, -g.e * 0.09f + g.e * 0.1f, skirt)
        // The burner lights the fabric from inside.
        if (heat > 0.01f) {
            add.shader = RadialGradient(0f, -g.e * 0.12f, g.e * 0.9f, intArrayOf(argb(255, 160, 70, 0.55f * heat), argb(255, 120, 50, 0.18f * heat), argb(255, 110, 40, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, add)
        }
        canvas.restore()
        // Rim light on the sunward edge and the mouth.
        canvas.drawPath(outline, rim)
        solid.color = if (heat > 0.01f) argb((60 + 180 * heat).roundToInt(), (30 + 90 * heat).roundToInt(), (30 + 20 * heat).roundToInt(), 1f) else hex(0xFF2A1A1C)
        rect.set(-g.rm, -3f, g.rm, 3f)
        canvas.drawOval(rect, solid)
    }

    private fun drawFlame(canvas: Canvas, heat: Float, time: Float) {
        if (heat < 0.01f) return
        val base = 30f
        val f = balloonFlicker(time)
        val hf = (8f + 42f * heat) * f
        val wf = 4f + 4f * heat
        val br = 26f + 46f * heat
        add.shader = RadialGradient(0f, base - hf * 0.5f, br, intArrayOf(argb(255, 180, 90, 0.55f * heat), argb(255, 130, 60, 0.18f * heat), argb(255, 120, 50, 0f)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, base - hf * 0.5f, br, add)
        work.reset()
        work.moveTo(-wf, base)
        work.cubicTo(-wf * 1.3f, base - hf * 0.45f, -wf * 0.2f, base - hf * 0.75f, 0f, base - hf)
        work.cubicTo(wf * 0.2f, base - hf * 0.75f, wf * 1.3f, base - hf * 0.45f, wf, base)
        work.close()
        add.shader = LinearGradient(0f, base, 0f, base - hf, intArrayOf(argb(255, 120, 40, 0.95f), argb(255, 190, 80, 0.85f), argb(255, 220, 140, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(work, add)
        val hc = hf * 0.55f
        work.reset()
        work.moveTo(-wf * 0.45f, base)
        work.cubicTo(-wf * 0.6f, base - hc * 0.5f, -wf * 0.1f, base - hc * 0.8f, 0f, base - hc)
        work.cubicTo(wf * 0.1f, base - hc * 0.8f, wf * 0.6f, base - hc * 0.5f, wf * 0.45f, base)
        work.close()
        core.shader = LinearGradient(0f, base, 0f, base - hc, intArrayOf(argb(150, 190, 255, 0.95f), argb(255, 250, 230, 0.95f), argb(255, 240, 200, 0f)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(work, core)
    }

    private fun drawRig(canvas: Canvas) {
        canvas.drawLine(-g.rm, 0f, -14f, 43f, cable)
        canvas.drawLine(g.rm, 0f, 14f, 43f, cable)
        canvas.drawLine(-g.rm * 0.4f, 1f, -5f, 43f, cable)
        canvas.drawLine(g.rm * 0.4f, 1f, 5f, 43f, cable)
        // Burner frame.
        rect.set(-9f, 30f, 9f, 36f)
        canvas.drawRoundRect(rect, 2f, 2f, steel)
        canvas.drawRect(-1.2f, 36f, 1.2f, 43f, steel)
        // Two passengers and the wicker basket with a leather rim.
        solid.color = hex(0xFF3A2A2E)
        canvas.drawCircle(-6f, 41f, 3.2f, solid)
        canvas.drawCircle(5.5f, 40.5f, 3f, solid)
        canvas.drawPath(basket, wicker)
        canvas.save()
        canvas.clipPath(basket)
        var y = 47f
        while (y < 66f) {
            canvas.drawLine(-15f, y, 15f, y, weave)
            y += 3f
        }
        var x = -13f
        while (x < 15f) {
            var yy = 44f
            while (yy < 66f) {
                val o = (yy / 6f) % 2f * 2f
                canvas.drawLine(x + o, yy, x + o, yy + 3f, weave)
                yy += 6f
            }
            x += 4f
        }
        canvas.restore()
        solid.color = hex(0xFF4B2C18)
        rect.set(-16f, 42f, 16f, 46f)
        canvas.drawRoundRect(rect, 2f, 2f, solid)
        canvas.drawLine(-14f, 42.6f, 14f, 42.6f, hi)
    }

    private fun drawImageAt(canvas: Canvas, bmp: Bitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        image.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        rect.set(x, y, x + w, y + h)
        canvas.drawBitmap(bmp, null, rect, image)
    }

    private fun drawClouds(canvas: Canvas, art: BalloonArt?, p: BalloonPose, front: Boolean) {
        if (art == null) return
        val w = 2f * PI.toFloat() / LOOP
        for (c in BalloonClouds) {
            if (c.front != front) continue
            val cw = 160f * c.scale
            val ch = 70f * c.scale
            val x = c.x * g.w - cw / 2f + c.amp * sin(w * p.time + c.phase)
            val y = c.y + p.alt * g.scroll * c.par
            if (y > g.h || y + ch < 0f) continue
            drawImageAt(canvas, art.clouds[c.sprite], x, y, cw, ch, c.alpha)
        }
    }

    private fun drawHill(canvas: Canvas, art: BalloonArt?, p: BalloonPose, i: Int) {
        val y = balloonHillY(g, i) + p.alt * g.scroll * HillPar[i]
        val height = balloonHillHeight(g, i)
        if (art != null) {
            drawImageAt(canvas, art.hills[i].image, 0f, y, g.w, height, 1f)
        } else {
            // Before the bake lands: the same vector drawing, straight onto the screen.
            canvas.save()
            canvas.translate(0f, y)
            drawBalloonHill(canvas, g.w, height, i)
            canvas.restore()
        }
    }

    /** The whole scene for one frame. [art] is null until the bake is ready. */
    fun draw(canvas: Canvas, art: BalloonArt?, p: BalloonPose) {
        if (art != null) {
            drawImageAt(canvas, art.sky, 0f, 0f, g.w, g.h, 1f)
        } else {
            drawBalloonSky(canvas, g.w, g.h, g.ground + HillTops[0] + 60f, density)
        }
        for (i in 0 until 2) drawHill(canvas, art, p, i)
        drawClouds(canvas, art, p, false)
        for (i in 2 until 4) drawHill(canvas, art, p, i)
        // The balloon: its mouth at (bx, by); it rises on screen while the world scrolls down.
        val bx = g.w * 0.5f
        val by = g.ground - BELOW - p.alt * g.rise + balloonBob(p.time, p.alt)
        val ground = g.ground + p.alt * g.scroll
        if (ground < g.h + 20f && canBlur) {
            val k = balloonClamp01(p.alt)
            groundShadow.color = argb(10, 14, 20, 0.4f * (1f - k))
            groundShadow.maskFilter = blur(3f + 10f * k)
            rect.set(bx - 20f - 30f * k, ground - 3f, bx + 20f + 30f * k, ground + 3f + 4f * k)
            canvas.drawOval(rect, groundShadow)
        }
        canvas.save()
        canvas.translate(bx, by)
        canvas.rotate(balloonSwayAngle(p.time, p.alt), 0f, -g.e * 0.5f)
        canvas.save()
        canvas.scale(1f + 0.03f * p.heat, 1f + 0.015f * p.heat)
        drawEnvelope(canvas, p.turn, p.heat)
        canvas.restore()
        drawRig(canvas)
        drawFlame(canvas, p.heat, p.time)
        canvas.restore()
        drawClouds(canvas, art, p, true)
        // A bloom of warm light round the burner reaches the screen at full heat.
        if (p.heat > 0.01f) {
            add.shader = RadialGradient(bx, by + 20f, 150f, intArrayOf(argb(255, 150, 70, 0.16f * p.heat), argb(255, 150, 70, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(bx, by + 20f, 150f, add)
        }
    }
}
