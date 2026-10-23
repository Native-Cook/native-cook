package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal class GumballCrankRenderer(private val assets: GumballCrankAssets) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val source = Rect()
    private val back = Bitmap.createBitmap(804, 1556, Bitmap.Config.ARGB_8888)
    private val front = Bitmap.createBitmap(804, 1556, Bitmap.Config.ARGB_8888)
    private val ballSprites = GumballCrankColor.Balls.map(::makeBallSprite)

    init {
        Canvas(back).apply { scale(2f, 2f); drawStaticBack(this) }
        Canvas(front).apply { scale(2f, 2f); drawStaticFront(this) }
    }

    fun close() {
        back.recycle(); front.recycle(); ballSprites.forEach(Bitmap::recycle)
    }

    fun draw(canvas: Canvas, model: GumballCrankSimulation, layout: GumballCrankLayout, width: Float, height: Float) {
        fill(canvas, GumballCrankColor.WallTop, 0f, 0f, width, height)
        canvas.save()
        canvas.translate(layout.ox, layout.oy)
        canvas.scale(layout.scale, layout.scale)
        drawBitmap(canvas, back, 0f, 0f, 402f, 778f)
        fill(canvas, GumballCrankColor.TableFront, -layout.ox / layout.scale, 777f, width / layout.scale, height / layout.scale + 2f)
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(201f, 254f, 121f, Path.Direction.CW) })
        drawBalls(canvas, model)
        canvas.restore()
        drawBitmap(canvas, front, 0f, 0f, 402f, 778f)
        drawHandle(canvas, model)
        drawCapsule(canvas, model)
        drawFlap(canvas, model)
        if (model.scrim > 0.005) {
            fill(canvas, withAlpha(GumballCrankColor.Ink, (model.scrim * 0.42f).toFloat()), -layout.ox / layout.scale, -layout.oy / layout.scale, width / layout.scale, height / layout.scale)
            drawCapsule(canvas, model)
        }
        drawCard(canvas, model)
        drawFlyingCoins(canvas, model)
        drawWallet(canvas, model)
        drawHint(canvas, model)
        if (model.ghostAlpha > 0.01) {
            val r = 22f * (1f - 0.12f * model.ghostPress.toFloat())
            circle(canvas, model.ghostX.toFloat(), model.ghostY.toFloat(), r, withAlpha(GumballCrankColor.Ink, (0.14 * model.ghostAlpha).toFloat()))
            strokeCircle(canvas, model.ghostX.toFloat(), model.ghostY.toFloat(), r - 0.5f, withAlpha(GumballCrankColor.Ink, (0.36 * model.ghostAlpha).toFloat()), 1f)
        }
        canvas.restore()
    }

    private fun drawStaticBack(c: Canvas) {
        paint.resetFill()
        paint.shader = LinearGradient(0f, 0f, 0f, 614f, GumballCrankColor.WallTop, GumballCrankColor.WallBottom, Shader.TileMode.CLAMP)
        c.drawRect(-200f, -200f, 602f, 614f, paint)
        paint.shader = RadialGradient(60f, 120f, 420f, intArrayOf(0xBFFFFAEE.toInt(), 0x00FFFAEE), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(-200f, -200f, 602f, 814f, paint)
        paint.shader = LinearGradient(0f, 614f, 0f, 778f, intArrayOf(GumballCrankColor.TableEdge, GumballCrankColor.TableTop, GumballCrankColor.TableFront), floatArrayOf(0f, 0.18f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(-200f, 614f, 602f, 1100f, paint)
        repeat(26) { k ->
            paint.resetStroke(withAlpha(GumballCrankColor.TableDeep, 0.05f + 0.06f * ((k * 37) % 5) / 5f), 0.8f)
            val y = 622f + k * 6.3f + sin(k * 1.7).toFloat() * 2f
            path.reset(); path.moveTo(-20f, y)
            var x = 0f
            while (x <= 442f) { path.quadTo(x + 20f, y + sin(k * 0.9 + x * 0.013).toFloat() * 2.4f, x + 40f, y + sin(k * 0.6 + x * 0.02).toFloat() * 1.2f); x += 40f }
            c.drawPath(path, paint)
        }
        paint.resetFill(withAlpha(0xFF5B3A1C.toInt(), 0.22f)); paint.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL); c.drawRect(-200f, 612f, 602f, 622f, paint)
        paint.resetFill(withAlpha(0xFF3D2410.toInt(), 0.38f)); paint.maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL); c.drawOval(RectF(69f, 604f, 359f, 634f), paint)
        drawBody(c)
        paint.resetFill(); paint.shader = RadialGradient(181f, 224f, 128f, intArrayOf(0x1AFFFFFF, 0x19786450, 0x47784623), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP); c.drawCircle(201f, 254f, 122f, paint)
        drawText(c, GumballCrankCopy.Title, 16f, 40f, 34f, assets.serif, GumballCrankColor.Ink)
        drawText(c, GumballCrankCopy.Subtitle, 16f, 64f, 15f, assets.regular, GumballCrankColor.Mute)
    }

    private fun drawBody(c: Canvas) {
        path.reset(); path.moveTo(115f, 392f); path.lineTo(287f, 392f)
        path.cubicTo(293f, 472f, 315f, 530f, 315f, 588f); path.quadTo(315f, 600f, 303f, 600f)
        path.lineTo(99f, 600f); path.quadTo(87f, 600f, 87f, 588f); path.cubicTo(87f, 530f, 109f, 472f, 115f, 392f); path.close()
        paint.resetFill(); paint.shader = LinearGradient(87f, 0f, 315f, 0f, intArrayOf(GumballCrankColor.EnamelDeep, GumballCrankColor.Enamel, GumballCrankColor.EnamelLight, GumballCrankColor.Enamel, 0xFF9E1C25.toInt(), GumballCrankColor.EnamelDeep), floatArrayOf(0f, .1f, .27f, .48f, .8f, 1f), Shader.TileMode.CLAMP); c.drawPath(path, paint)
        c.save(); c.clipPath(path)
        paint.resetFill(withAlpha(Color.WHITE, .28f)); paint.maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL); c.drawRect(139f, 386f, 161f, 606f, paint)
        paint.resetFill(); paint.shader = LinearGradient(0f, 560f, 0f, 600f, 0x00280004, 0x59280004, Shader.TileMode.CLAMP); c.drawRect(87f, 560f, 315f, 600f, paint)
        c.restore()
        paint.resetFill(); paint.shader = RadialGradient(184f, 441f, 76f, intArrayOf(GumballCrankColor.ChromeHi, GumballCrankColor.ChromeMid, GumballCrankColor.ChromeLow, GumballCrankColor.ChromeDeep), floatArrayOf(0f, .35f, .75f, 1f), Shader.TileMode.CLAMP); c.drawCircle(201f, 462f, 42f, paint)
        roundRect(c, 198f, 430f, 6f, 18f, 3f, GumballCrankColor.Slot)
        paint.resetFill(); paint.shader = LinearGradient(0f, 527f, 0f, 587f, intArrayOf(GumballCrankColor.ChromeHi, GumballCrankColor.ChromeMid, GumballCrankColor.ChromeDeep), null, Shader.TileMode.CLAMP); c.drawRoundRect(RectF(146f, 527f, 256f, 587f), 23f, 23f, paint)
        paint.resetFill(); paint.shader = LinearGradient(0f, 532f, 0f, 582f, intArrayOf(0xFF140A08.toInt(), 0xFF2B1612.toInt(), 0xFF3A1E17.toInt()), null, Shader.TileMode.CLAMP); c.drawRoundRect(RectF(151f, 532f, 251f, 582f), 18f, 18f, paint)
        paint.resetFill(); paint.shader = chrome(79f, 323f); c.drawRoundRect(RectF(79f, 600f, 323f, 614f), 6f, 6f, paint)
    }

    private fun drawStaticFront(c: Canvas) {
        paint.resetStroke(withAlpha(Color.WHITE, .5f), 16f); paint.strokeCap = Paint.Cap.ROUND; paint.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL); c.drawArc(RectF(93f, 146f, 309f, 362f), 196f, 62f, false, paint)
        paint.resetStroke(withAlpha(Color.WHITE, .85f), 3f); paint.strokeCap = Paint.Cap.ROUND; paint.maskFilter = BlurMaskFilter(.8f, BlurMaskFilter.Blur.NORMAL); c.drawArc(RectF(89f, 142f, 313f, 366f), 202f, 40f, false, paint)
        strokeCircle(c, 201f, 254f, 121.2f, withAlpha(Color.WHITE, .55f), 1.6f)
        paint.resetFill(); paint.shader = chrome(103f, 299f); c.drawRoundRect(RectF(103f, 370f, 299f, 392f), 8f, 8f, paint)
        val top = 110f
        path.reset(); path.moveTo(126f, 144f); path.cubicTo(126f, 114f, 163f, 110f, 201f, 110f); path.cubicTo(239f, 110f, 276f, 114f, 276f, 144f); path.close()
        paint.resetFill(); paint.shader = chrome(126f, 276f); c.drawPath(path, paint)
        paint.resetFill(); paint.shader = chrome(122f, 280f); c.drawRoundRect(RectF(122f, 138f, 280f, 147f), 4.5f, 4.5f, paint)
        paint.resetFill(); paint.shader = RadialGradient(197f, 91f, 19f, intArrayOf(GumballCrankColor.ChromeHi, GumballCrankColor.ChromeMid, GumballCrankColor.ChromeDeep), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP); c.drawCircle(201f, 101f, 12f, paint)
        circle(c, 197f, 97f, 3f, withAlpha(Color.WHITE, .9f))
    }

    private fun drawBalls(c: Canvas, model: GumballCrankSimulation) {
        source.set(0, 0, 96, 96)
        for (i in model.ballX.indices) {
            val r = model.ballR[i].toFloat(); rect.set((model.ballX[i] - r).toFloat(), (model.ballY[i] - r).toFloat(), (model.ballX[i] + r).toFloat(), (model.ballY[i] + r).toFloat())
            paint.resetFill(); paint.isFilterBitmap = true; c.drawBitmap(ballSprites[model.ballColor[i]], source, rect, paint)
        }
    }

    private fun drawHandle(c: Canvas, model: GumballCrankSimulation) {
        val a = model.angle + model.wobble * .06; val gx = 201f + 34f * sin(a).toFloat(); val gy = 462f - 34f * cos(a).toFloat()
        paint.resetStroke(withAlpha(0xFF1A0A06.toInt(), .32f), 9f); paint.strokeCap = Paint.Cap.ROUND; paint.maskFilter = BlurMaskFilter(2.5f, BlurMaskFilter.Blur.NORMAL); c.drawLine(204f, 466f, gx + 3f, gy + 4f, paint)
        paint.resetStroke(GumballCrankColor.ChromeMid, 9f); paint.strokeCap = Paint.Cap.ROUND; c.drawLine(201f, 462f, gx, gy, paint)
        paint.resetStroke(withAlpha(Color.WHITE, .85f), 2.2f); paint.strokeCap = Paint.Cap.ROUND; c.drawLine(201f - cos(a).toFloat() * 2.2f, 462f - sin(a).toFloat() * 2.2f, gx - cos(a).toFloat() * 2.2f, gy - sin(a).toFloat() * 2.2f, paint)
        paint.resetFill(); paint.shader = RadialGradient(197f, 457f, 23f, intArrayOf(GumballCrankColor.ChromeHi, GumballCrankColor.ChromeMid, GumballCrankColor.ChromeDeep), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP); c.drawCircle(201f, 462f, 13f, paint)
        circle(c, 201f, 462f, 3.5f, GumballCrankColor.ChromeDeep)
        paint.resetFill(); paint.shader = RadialGradient(gx - 4f, gy - 4.5f, 17f, intArrayOf(0xFFFF8A84.toInt(), GumballCrankColor.EnamelLight, GumballCrankColor.Enamel, GumballCrankColor.EnamelDeep), floatArrayOf(0f, .2f, .55f, 1f), Shader.TileMode.CLAMP); c.drawCircle(gx, gy, 10f, paint)
        circle(c, gx - 3.6f, gy - 4f, 2.2f, withAlpha(Color.WHITE, .85f))
    }

    private fun drawFlap(c: Canvas, model: GumballCrankSimulation) {
        var open = 0.0
        if (model.phase == GumballCrankPhase.Dispensing && !model.reduced) open = if (model.phaseTime < .5) ease(span(model.phaseTime, .22, .5)) else 1 - ease(span(model.phaseTime, .75, 1.15))
        val height = max(2f, (50 * cos(open * 1.25)).toFloat() - 4f)
        paint.resetFill(); paint.shader = LinearGradient(0f, 534f, 0f, 534f + height, intArrayOf(GumballCrankColor.ChromeHi, GumballCrankColor.ChromeMid, GumballCrankColor.ChromeLow), null, Shader.TileMode.CLAMP); c.drawRoundRect(RectF(154f, 534f, 248f, 534f + height), minOf(15f, height / 2), minOf(15f, height / 2), paint)
    }

    private fun drawCapsule(c: Canvas, model: GumballCrankSimulation) {
        val p = model.capsulePose(); if (p.alpha <= .01) return
        val r = (30 * p.scale).toFloat(); val alpha = p.alpha.toFloat()
        if (p.y > 600) { paint.resetFill(withAlpha(0xFF3D2410.toInt(), .4f * alpha)); paint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL); c.drawOval(RectF((p.x - r * 1.1 + 4).toFloat(), (p.y + r * .62).toFloat(), (p.x + r * 1.3 + 4).toFloat(), (p.y + r * 1.17).toFloat()), paint) }
        c.save(); c.translate(p.x.toFloat(), p.y.toFloat()); c.rotate((p.rotation * 180 / PI).toFloat())
        path.reset(); path.moveTo(-r, 0f); path.arcTo(RectF(-r, -r, r, r), 180f, -180f); path.close()
        paint.resetFill(); paint.shader = RadialGradient(-r * .4f, r * .1f, r * 1.8f, intArrayOf(GumballCrankColor.MintLight, GumballCrankColor.Mint, GumballCrankColor.MintDeep), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP); paint.alpha = (alpha * 255).roundToInt(); c.drawPath(path, paint)
        drawCoin(c, 0f, -r * .08f, r * .42f, alpha)
        c.save(); c.translate((-p.split * 62 * p.scale).toFloat(), ((-sin(p.split * PI) * 34 + p.split * 4) * p.scale).toFloat()); c.rotate((-p.split * 2.75 * 180 / PI).toFloat())
        path.reset(); path.moveTo(-r, 0f); path.arcTo(RectF(-r, -r, r, r), 180f, 180f); path.close()
        paint.resetFill(); paint.shader = RadialGradient(-r * .2f, -r * .35f, r * 1.2f, intArrayOf(0x2EFFFFFF, 0x19FFFFFF, 0x477A3C28), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP); paint.alpha = (alpha * 255).roundToInt(); c.drawPath(path, paint)
        paint.resetStroke(withAlpha(Color.WHITE, .8f * alpha), 1.2f); c.drawPath(path, paint)
        c.restore(); c.restore()
    }

    private fun drawCard(c: Canvas, model: GumballCrankSimulation) {
        var k = model.card; var alpha = minOf(1.0, k * 1.6); var drop = 0.0
        if (model.phase == GumballCrankPhase.Collecting) { val u = span(model.phaseTime, .35, .75); alpha = 1 - u; drop = if (model.reduced) 0.0 else 50 * ease(u); k = 1.0 }
        if (alpha <= .01) return
        val rise = if (model.reduced) 0.0 else (1 - k) * 120; val scale = if (model.reduced) 1.0 else .72 + .28 * k
        c.save(); c.translate(201f, (656 + rise + drop).toFloat()); c.scale(scale.toFloat(), scale.toFloat()); c.translate(-201f, -656f)
        paint.resetFill(withAlpha(0xFF1A0C04.toInt(), (.35 * alpha).toFloat())); paint.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL); c.drawRoundRect(RectF(46f, 438f, 356f, 666f), 24f, 24f, paint)
        paint.resetFill(); paint.shader = LinearGradient(0f, 420f, 0f, 656f, GumballCrankColor.Card, 0xFFFBF4E8.toInt(), Shader.TileMode.CLAMP); paint.alpha = (alpha * 255).roundToInt(); c.drawRoundRect(RectF(42f, 420f, 360f, 656f), 24f, 24f, paint)
        drawSpaced(c, GumballCrankCopy.Eyebrow, 201f, 454f, 11f, assets.semibold, GumballCrankColor.Mute, 1.6f, alpha.toFloat())
        val prizeWidth = measure(GumballCrankCopy.Prize, 64f, assets.serif); val unitWidth = measure(GumballCrankCopy.Unit, 26f, assets.serif); val total = 30 + 10 + prizeWidth + 8 + unitWidth; val x0 = 201 - total / 2
        drawCoin(c, x0 + 15, 504f, 15f, alpha.toFloat())
        drawText(c, GumballCrankCopy.Prize, x0 + 40, 524f, 64f, assets.serif, GumballCrankColor.Ink, alpha.toFloat())
        drawText(c, GumballCrankCopy.Unit, x0 + 40 + prizeWidth + 8, 524f, 26f, assets.serif, GumballCrankColor.Ink, alpha.toFloat())
        drawText(c, GumballCrankCopy.CardSubtitle, 201f, 554f, 14f, assets.regular, GumballCrankColor.Mute, alpha.toFloat(), Paint.Align.CENTER)
        val bs = 1 - .03f * model.press.toFloat(); c.save(); c.translate(201f, 606f); c.scale(bs, bs); c.translate(-201f, -606f)
        roundRect(c, 66f, 580f, 270f, 52f, 26f, withAlpha(GumballCrankColor.Ink, (alpha * (1 - .12 * model.press)).toFloat()))
        drawText(c, GumballCrankCopy.Collect, 201f, 612f, 16f, assets.semibold, GumballCrankColor.Card, alpha.toFloat(), Paint.Align.CENTER)
        c.restore(); c.restore()
    }

    private fun drawFlyingCoins(c: Canvas, model: GumballCrankSimulation) {
        if (model.phase != GumballCrankPhase.Collecting || model.reduced) return
        repeat(8) { i -> val u = span(model.phaseTime, i * .05, i * .05 + .5); if (u > 0 && u < 1) { val e = ease(u); val x = 131 + (313 - 131) * e + sin(u * PI) * if (i % 2 == 1) 18 else -10; val y = 504 + (32 - 504) * e - sin(u * PI) * (70 + i * 6); drawCoin(c, x.toFloat(), y.toFloat(), (13 - 4 * e).toFloat(), 1f) } }
    }

    private fun drawWallet(c: Canvas, model: GumballCrankSimulation) {
        val scale = 1f + .06f * model.walletBump.toFloat(); c.save(); c.translate(340f, 32f); c.scale(scale, scale); c.translate(-340f, -32f)
        paint.resetFill(withAlpha(0xFF5B3A1C.toInt(), .18f)); paint.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL); c.drawRoundRect(RectF(294f, 15f, 386f, 55f), 20f, 20f, paint)
        roundRect(c, 294f, 12f, 92f, 40f, 20f, 0xDBFFFCF6.toInt()); strokeRoundRect(c, 294.5f, 12.5f, 91f, 39f, 19.5f, Color.WHITE, 1f)
        drawCoin(c, 315f, 32f, 9f, 1f)
        c.save(); c.clipRect(294f, 12f, 386f, 52f)
        if (model.walletMix < 1) drawText(c, grouped(model.walletPrevious), 374f, (37 - 10 * model.walletMix).toFloat(), 15f, assets.semibold, GumballCrankColor.Ink, (1 - model.walletMix).toFloat(), Paint.Align.RIGHT)
        drawText(c, grouped(model.wallet), 374f, (37 + 10 * (1 - model.walletMix)).toFloat(), 15f, assets.semibold, GumballCrankColor.Ink, model.walletMix.toFloat(), Paint.Align.RIGHT)
        c.restore(); c.restore()
    }

    private fun drawHint(c: Canvas, model: GumballCrankSimulation) {
        val copy = arrayOf(GumballCrankCopy.Hint, GumballCrankCopy.Busy, GumballCrankCopy.Done); val under = 1 - model.scrim
        if (model.hintMix < 1) drawText(c, copy[model.hintFrom], 201f, 752f, 15f, assets.medium, GumballCrankColor.Ink, ((1 - model.hintMix) * .85 * under).toFloat(), Paint.Align.CENTER)
        drawText(c, copy[model.hint], 201f, 752f, 15f, assets.medium, GumballCrankColor.Ink, (model.hintMix * .85 * under).toFloat(), Paint.Align.CENTER)
        val show = if (model.hint == 0) model.hintMix else if (model.hintFrom == 0) 1 - model.hintMix else 0.0
        repeat(4) { i -> circle(c, (201 + (i - 1.5) * 14).toFloat(), 722f, 3.5f, withAlpha(if (i < model.detent) GumballCrankColor.Enamel else GumballCrankColor.Ink, ((if (i < model.detent) 1.0 else .2) * show).toFloat())) }
    }

    private fun makeBallSprite(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888); val c = Canvas(bitmap); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(30f, 26f, 84f, intArrayOf(Color.WHITE, color, color, Color.BLACK), floatArrayOf(0f, .22f, .62f, 1f), Shader.TileMode.CLAMP); c.drawCircle(48f, 48f, 47.5f, p)
        p.shader = null; p.color = withAlpha(Color.WHITE, .85f); c.drawOval(RectF(22f, 18f, 40f, 30f), p); return bitmap
    }
    private fun drawCoin(c: Canvas, x: Float, y: Float, r: Float, alpha: Float) {
        if (alpha <= .01f) return
        paint.resetFill(); paint.shader = RadialGradient(x - r * .35f, y - r * .4f, r * 1.5f, intArrayOf(GumballCrankColor.CoinLight, GumballCrankColor.Coin, GumballCrankColor.CoinDeep), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP); paint.alpha = (alpha * 255).roundToInt(); c.drawCircle(x, y, r, paint)
        strokeCircle(c, x, y, r * .72f, withAlpha(GumballCrankColor.CoinDeep, .7f * alpha), max(1f, r * .12f)); circle(c, x - r * .3f, y - r * .32f, r * .18f, withAlpha(Color.WHITE, .7f * alpha))
    }
    private fun drawText(c: Canvas, value: String, x: Float, baseline: Float, size: Float, face: android.graphics.Typeface, color: Int, alpha: Float = 1f, align: Paint.Align = Paint.Align.LEFT) { paint.resetFill(withAlpha(color, alpha)); paint.typeface = face; paint.textSize = size; paint.textAlign = align; c.drawText(value, x, baseline, paint) }
    private fun drawSpaced(c: Canvas, value: String, center: Float, baseline: Float, size: Float, face: android.graphics.Typeface, color: Int, tracking: Float, alpha: Float) { paint.resetFill(withAlpha(color, alpha)); paint.typeface = face; paint.textSize = size; val widths = value.map { paint.measureText(it.toString()) }; var x = center - (widths.sum() + tracking * (value.length - 1)) / 2; value.forEachIndexed { i, ch -> c.drawText(ch.toString(), x, baseline, paint); x += widths[i] + tracking } }
    private fun measure(value: String, size: Float, face: android.graphics.Typeface): Float { paint.resetFill(); paint.typeface = face; paint.textSize = size; return paint.measureText(value) }
    private fun grouped(value: Double) = "% ,d".trim().format(value.roundToInt()).replace(" ", "")
    private fun chrome(start: Float, end: Float) = LinearGradient(start, 0f, end, 0f, intArrayOf(GumballCrankColor.ChromeLow, GumballCrankColor.ChromeHi, GumballCrankColor.ChromeMid, GumballCrankColor.ChromeDeep, GumballCrankColor.ChromeMid, GumballCrankColor.ChromeHi, GumballCrankColor.ChromeLow), floatArrayOf(0f, .16f, .34f, .52f, .72f, .88f, 1f), Shader.TileMode.CLAMP)
    private fun drawBitmap(c: Canvas, bitmap: Bitmap, x: Float, y: Float, w: Float, h: Float) { source.set(0, 0, bitmap.width, bitmap.height); rect.set(x, y, x + w, y + h); paint.resetFill(); paint.isFilterBitmap = true; c.drawBitmap(bitmap, source, rect, paint) }
    private fun fill(c: Canvas, color: Int, x: Float, y: Float, w: Float, h: Float) { paint.resetFill(color); c.drawRect(x, y, x + w, y + h, paint) }
    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) { paint.resetFill(color); c.drawCircle(x, y, r, paint) }
    private fun strokeCircle(c: Canvas, x: Float, y: Float, r: Float, color: Int, width: Float) { paint.resetStroke(color, width); c.drawCircle(x, y, r, paint) }
    private fun roundRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) { paint.resetFill(color); c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint) }
    private fun strokeRoundRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, width: Float) { paint.resetStroke(color, width); c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint) }
    private fun Paint.resetFill(color: Int = Color.WHITE) { reset(); isAntiAlias = true; isFilterBitmap = true; style = Paint.Style.FILL; this.color = color; alpha = 255 }
    private fun Paint.resetStroke(color: Int, width: Float) { resetFill(color); style = Paint.Style.STROKE; strokeWidth = width }
    private fun withAlpha(color: Int, alpha: Float) = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)
    private fun span(value: Double, start: Double, end: Double) = ((value - start) / (end - start)).coerceIn(0.0, 1.0)
    private fun ease(u: Double): Double { val k = u.coerceIn(0.0, 1.0); val x1=.23; val y1=1.0; val x2=.32; val y2=1.0; val cx=3*x1; val bx=3*(x2-x1)-cx; val ax=1-cx-bx; val cy=3*y1; val by=3*(y2-y1)-cy; val ay=1-cy-by; var t=k; repeat(8){ val x=((ax*t+bx)*t+cx)*t-k; val dx=(3*ax*t+2*bx)*t+cx; if(kotlin.math.abs(dx)>1e-6)t-=x/dx }; t=t.coerceIn(0.0,1.0); return ((ay*t+by)*t+cy)*t }
}
