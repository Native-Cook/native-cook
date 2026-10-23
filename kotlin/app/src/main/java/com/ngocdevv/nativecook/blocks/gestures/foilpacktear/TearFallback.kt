package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import android.graphics.*
import kotlin.math.*

/** API 26–32: still relief from the shipping normal map, original print and card artwork. */
internal class TearFallback(private val assets: TearAssets) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val geometry = TearGeometry()
    private val foil = bakeFoil()
    private val cardClip = Path()

    fun draw(canvas: Canvas, p: TearPose, width: Float, height: Float) {
        geometry.update(p)
        val scale = min(width / 402f, height / 874f)
        canvas.save()
        canvas.translate((width - scale * 402) / 2, (height - scale * 874) / 2)
        canvas.scale(scale, scale)
        paint.shader = RadialGradient(201f, 370f, 450f, Color.rgb(6, 9, 19), Color.rgb(1, 2, 7), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 402f, 874f, paint)
        val colours = if (p.frameHeat > 0.5) intArrayOf(0xffb3f2ff.toInt(), 0xffffbf59.toInt(), 0xffff99c2.toInt())
            else intArrayOf(0xff29e0ff.toInt(), 0xff4866e6.toInt(), 0xff733dff.toInt())
        paint.shader = LinearGradient(48f, 128f, 354f, 620f, colours, null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        for (widthAt in listOf(24f, 17f, 12f, 2f)) {
            paint.strokeWidth = widthAt
            paint.alpha = if (widthAt > 12) 18 else if (widthAt == 12f) 125 else 210
            canvas.drawRoundRect(54f, 134f, 348f, 614f, 24f, 24f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        paint.color = Color.rgb(16, 23, 53)
        val tab = Path().apply {
            moveTo(137f, 590f)
            lineTo(265f, 590f)
            lineTo(287f, 626f)
            lineTo(115f, 626f)
            close()
        }
        canvas.drawPath(tab, paint)
        paint.color = Color.WHITE
        canvas.drawBitmap(assets.tabLabel, null, RectF(141f, 601f, 261f, 615f), paint)
        if (p.cardFlip > 0.02) pack(canvas, p)
        if (p.cardOpacity > 0) {
            canvas.save()
            canvas.translate(p.cardX.toFloat(), p.cardY.toFloat())
            canvas.scale((p.cardScale * max(0.015, abs(cos(p.cardFlip + p.cardTiltY)))).toFloat(), p.cardScale.toFloat())
            cardClip.rewind()
            cardClip.addRoundRect(-90f, -126f, 90f, 126f, 9f, 9f, Path.Direction.CW)
            canvas.clipPath(cardClip)
            paint.alpha = (clamp(p.cardOpacity) * 255).toInt()
            val front = cos(p.cardFlip) < 0
            canvas.drawBitmap(if (front) assets.cardFace else assets.cardBack,
                if (front) Rect(24, 24, 726, 1026) else null, RectF(-90f, -126f, 90f, 126f), paint)
            canvas.restore()
        }
        if (p.cardFlip <= 0.02) pack(canvas, p)
        paint.alpha = 255
        canvas.restore()
    }

    private fun pack(canvas: Canvas, p: TearPose) {
        if (p.packOpacity <= 0) return
        canvas.save()
        canvas.concat(geometry.pack)
        canvas.clipPath(geometry.cut)
        paint.alpha = (clamp(p.packOpacity) * 255).toInt()
        canvas.drawBitmap(foil, null, RectF(0f, 0f, 216f, 376f), paint)
        canvas.restore()
    }

    private fun bakeFoil(): Bitmap {
        val image = Bitmap.createBitmap(648, 1128, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(648 * 1128)
        for (y in 0 until 1128) for (x in 0 until 648) {
            val nx = (Color.red(assets.packMaps.getPixel(x, y)) - 128) / 128.0
            val ny = (Color.red(assets.packMaps.getPixel(x + 648, y)) - 128) / 128.0
            val cavity = Color.red(assets.packMaps.getPixel(x + 2592, y)) / 255.0
            val alpha = Color.red(assets.packMaps.getPixel(x + 1944, y))
            val shine = exp(-((nx * 1.7 + 0.42) / 0.23).pow(2)) * 1.8 + exp(-((ny * 1.7 + 0.36) / 0.08).pow(2)) * 0.65
            val light = (0.26 + shine) * (0.78 + cavity * 0.22)
            pixels[y * 648 + x] = Color.argb(alpha, (255 * min(1.0, 0.026 * light + shine * 0.1)).toInt(),
                (255 * min(1.0, 0.42 * light + shine * 0.13)).toInt(), (255 * min(1.0, 0.35 * light + shine * 0.12)).toInt())
        }
        image.setPixels(pixels, 0, 648, 0, 0, 648, 1128)
        Canvas(image).drawBitmap(assets.packPrint, 0f, 0f, paint)
        return image
    }
}
