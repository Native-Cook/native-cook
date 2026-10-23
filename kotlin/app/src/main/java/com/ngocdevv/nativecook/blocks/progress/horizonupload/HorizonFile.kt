// The three file layers, independent float/parallax, engraved extension and perspective flip.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class HorizonFile(private val assets: HorizonAssets) {
    private val sheet = if (Build.VERSION.SDK_INT >= 33) HorizonFileMaterial(assets.sheet, true) else null
    private val front = if (Build.VERSION.SDK_INT >= 33) HorizonFileMaterial(assets.badge, false) else null
    private val back = if (Build.VERSION.SDK_INT >= 33) HorizonFileMaterial(assets.badgeDone, false) else null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 800, false)
            else Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 20f; letterSpacing = .2f / 20; textAlign = Paint.Align.CENTER
    }
    private val destination = RectF()
    private val camera = Camera().apply { setLocation(0f, 0f, -420f / 72f) }
    private val matrix = Matrix()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E000000; maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL) }
    // The fallback flattens material alpha once, then uses cached relighting matrices.
    private val plainSheet = if (Build.VERSION.SDK_INT < 33) flatten(assets.sheet, true) else assets.sheet
    private val plainFront = if (Build.VERSION.SDK_INT < 33) flatten(assets.badge, false) else assets.badge
    private val plainBack = if (Build.VERSION.SDK_INT < 33) flatten(assets.badgeDone, false) else assets.badgeDone
    private val filters = if (Build.VERSION.SDK_INT < 33) Array(65) { index ->
        val k = sqrt(index / 64f)
        val mix = .5f + .5f * k; val brightness = .6f + .4f * k
        val coefficients = floatArrayOf(.2126f, .7152f, .0722f)
        val tint = floatArrayOf(.87f, .94f, 1.12f)
        val values = FloatArray(20)
        for (row in 0..2) for (col in 0..2) values[row * 5 + col] = brightness * ((1 - mix) * tint[row] * coefficients[col] + if (row == col) mix else 0f)
        values[18] = 1f
        ColorMatrixColorFilter(values)
    } else emptyArray()

    fun draw(canvas: Canvas, state: HorizonState, g: HorizonGeometry) {
        val parallaxX = state.shiftX.value * .07f; val parallaxY = state.shiftY.value * .07f
        canvas.save(); canvas.translate(-parallaxX, -parallaxY)
        val shadowY = 24 + 112 * (1 - g.fileScale) / 2 + 6 + 34 * g.fileScale
        canvas.drawRoundRect(24 + 26 * g.fileScale, shadowY, 24 + 110 * g.fileScale, shadowY + 78 * g.fileScale, 12f, 12f, shadow)
        canvas.restore()
        canvas.save(); canvas.translate(24 + parallaxX, 24 + parallaxY + 56 * (1 - g.fileScale)); canvas.scale(g.fileScale, g.fileScale)
        val amplitude = HorizonMath.part(state.power.value, .06f, 1f)
        val angle = (state.now % 3200 / 3200 * PI * 2).toFloat()
        val crest = state.crest.value
        val rise = HorizonMath.part(crest, .4f, .51f)
        val settle = HorizonMath.part(crest, .62f, 1f)
        val lift = -4 * sin(rise * PI.toFloat() / 2) * (1 - settle).pow(2)
        val sheetY = if (state.reduced) 0f else 2 * amplitude * sin(angle) + lift
        val badgeY = if (state.reduced) 0f else amplitude * sin(angle - PI.toFloat() / 2)
        canvas.translate(0f, sheetY)
        canvas.save(); canvas.translate(18f, -8f)
        drawLayer(canvas, sheet, plainSheet, true, state, 1f); canvas.restore()
        canvas.translate(0f, 41.2f + badgeY)
        val turn = if (state.reduced || crest <= 0 || crest >= 1) state.done.value.coerceIn(0f, 1f)
            else HorizonMath.smooth(HorizonMath.part(crest, .4f, .62f))
        if (state.reduced) {
            drawFace(canvas, state, false, 0f, 1 - turn); drawFace(canvas, state, true, 0f, turn)
        } else if (turn < .5f) drawFace(canvas, state, false, turn * 180, 1f)
        else drawFace(canvas, state, true, (turn - 1) * 180, 1f)
        canvas.restore()
    }
    private fun drawFace(canvas: Canvas, state: HorizonState, complete: Boolean, rotation: Float, alpha: Float) {
        if (alpha <= 0) return
        canvas.save()
        if (rotation != 0f) {
            camera.save(); camera.rotateX(rotation); camera.getMatrix(matrix); camera.restore()
            matrix.preTranslate(-41f, -26f); matrix.postTranslate(41f, 26f); canvas.concat(matrix)
        }
        canvas.save(); canvas.translate(-8f, -8f)
        drawLayer(canvas, if (complete) back else front, if (complete) plainBack else plainFront, false, state, alpha)
        canvas.restore()
        if (!complete) {
            val opacity = alpha * (.72f + .28f * HorizonMath.part(state.power.value, .06f, 1f))
            // Centre the system face in the 26pt stamp line without Android's extra font padding.
            val baseline = 26 - (text.ascent() + text.descent()) / 2
            text.color = 0xFFCEE0FF.toInt(); text.alpha = (opacity * .48f * 255).toInt(); canvas.drawText("TXT", 41f, baseline + .5f, text)
            text.color = 0xFF1B2647.toInt(); text.alpha = (opacity * 255).toInt(); canvas.drawText("TXT", 41f, baseline, text)
        }
        canvas.restore()
    }
    private fun drawLayer(canvas: Canvas, material: HorizonFileMaterial?, bitmap: Bitmap, isSheet: Boolean, state: HorizonState, alpha: Float) {
        if (Build.VERSION.SDK_INT >= 33 && material != null) material.draw(canvas, state, alpha)
        else {
            paint.alpha = (alpha * 255).toInt(); paint.colorFilter = filters[(HorizonMath.part(state.power.value, .06f, 1f) * 64).roundToInt()]
            destination.set(0f, 0f, if (isSheet) 106f else 98f, if (isSheet) 128f else 68f)
            canvas.drawBitmap(bitmap, null, destination, paint)
        }
    }
    private fun flatten(bitmap: Bitmap, isSheet: Boolean): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val color = pixels[i]; val a = Color.alpha(color) / 255f
            if (a < .001f) continue
            val body = HorizonMath.smooth(HorizonMath.part(a, if (isSheet) .015f else .3f, if (isSheet) .2f else .7f))
            val outAlpha = a + (1 - a) * body
            fun channel(v: Int, pane: Int) = (v + ((v * a + pane * (1 - a)) - v) * body).roundToInt().coerceIn(0, 255)
            pixels[i] = Color.argb((outAlpha * 255).roundToInt(), channel(Color.red(color), 13), channel(Color.green(color), 20), channel(Color.blue(color), 38))
        }
        return Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    }
}
