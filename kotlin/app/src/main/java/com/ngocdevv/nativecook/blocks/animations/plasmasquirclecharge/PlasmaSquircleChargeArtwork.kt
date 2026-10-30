package com.ngocdevv.nativecook.blocks.animations.plasmasquirclecharge

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The same bundled hub and Skia layers as RN. Blurred masks are rasterized only once. */
internal class PlasmaSquircleChargeArtwork(assets: AssetManager, private val density: Float) {
    companion object {
        const val WIDTH = 232f
        const val HEIGHT = WIDTH * 340f / 540f
        private const val PAD = 84f

        // Android accepts a blur radius; the reference's BlurMask uses sigma.
        private fun radius(sigma: Float) = ((sigma - .5f) / .57735f).coerceAtLeast(.01f)
    }

    private val hub = assets.open("plasmasquirclecharge/plasmasquirclecharge_hub.png").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowBounds = RectF(-PAD, -PAD, WIDTH + PAD, HEIGHT + PAD)
    private val ledBounds = RectF(-21f, -21f, 21f, 21f)
    private val dotBounds = RectF(-24f, -24f, 34f, 34f)
    private val imageBounds = RectF(0f, 0f, WIDTH, HEIGHT)
    private val reflectionBounds = RectF(-20f, HEIGHT + 6, WIDTH + 20, HEIGHT * 2 + 6)
    private val sheenBounds = RectF(18f, 8f, WIDTH - 18f, 8f + HEIGHT * .55f)

    private fun blurred(width: Float, height: Float, draw: (Canvas, Paint) -> Unit): Bitmap {
        return Bitmap.createBitmap(ceil(width * density).toInt(), ceil(height * density).toInt(), Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.scale(density, density)
            draw(canvas, Paint(Paint.ANTI_ALIAS_FLAG))
        }
    }

    private val glow = blurred(WIDTH + PAD * 2, HEIGHT + PAD * 2) { canvas, paint ->
        paint.color = Color.rgb(168, 152, 255)
        paint.maskFilter = BlurMaskFilter(radius(28f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(PAD + 10, PAD + 12, PAD + WIDTH - 10, PAD + HEIGHT - 4, 40f, 40f, paint)
    }
    private val led = blurred(42f, 42f) { canvas, paint ->
        paint.color = Color.WHITE
        paint.maskFilter = BlurMaskFilter(radius(5f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(21f, 21f, 6f, paint)
    }
    private val dot = blurred(58f, 58f) { canvas, paint ->
        paint.color = Color.rgb(168, 152, 255)
        paint.maskFilter = BlurMaskFilter(radius(8f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(29f, 29f, 5f, paint)
    }

    init {
        reflectionPaint.shader = LinearGradient(
            0f, HEIGHT, 0f, HEIGHT * 1.8f,
            intArrayOf(Color.argb(0, 5, 4, 11), Color.rgb(5, 4, 11)), null, Shader.TileMode.CLAMP,
        )
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT * .6f,
            intArrayOf(Color.argb(230, 168, 152, 255), Color.argb(0, 91, 91, 255)), null, Shader.TileMode.CLAMP,
        )
    }

    fun draw(canvas: Canvas, width: Float, height: Float, level: Float) {
        val u = ((level - .6f) / .4f).coerceIn(0f, 1f)
        val charge = u * u * (3 - 2 * u)
        val saved = canvas.save()
        canvas.translate(width / 2 - WIDTH / 2, height * .52f - HEIGHT / 2)

        val reflection = .12f + .16f * charge
        imagePaint.alpha = (reflection * 255).roundToInt()
        val flip = canvas.save()
        canvas.translate(0f, HEIGHT * 2 + 6)
        canvas.scale(1f, -1f)
        canvas.drawBitmap(hub, null, imageBounds, imagePaint)
        canvas.restoreToCount(flip)
        // RN paints this gradient over the reflection, with the same inherited opacity.
        reflectionPaint.alpha = (reflection * 255).roundToInt()
        canvas.drawRect(reflectionBounds, reflectionPaint)

        glowPaint.alpha = ((.15f + .85f * charge) * 255).roundToInt()
        canvas.drawBitmap(glow, null, glowBounds, glowPaint)
        imagePaint.alpha = 255
        canvas.drawBitmap(hub, null, imageBounds, imagePaint)
        sheenPaint.alpha = (.45f * charge * 255).roundToInt()
        canvas.drawRoundRect(sheenBounds, 36f, 36f, sheenPaint)

        canvas.translate(WIDTH / 2, HEIGHT * .865f)
        val status = .25f + .75f * charge
        glowPaint.alpha = (status * 255).roundToInt()
        canvas.drawBitmap(led, null, ledBounds, glowPaint)
        ledPaint.color = Color.WHITE
        ledPaint.alpha = (status * 255).roundToInt()
        canvas.drawCircle(0f, 0f, 2.4f, ledPaint)
        canvas.restoreToCount(saved)
    }

    fun drawDot(canvas: Canvas, light: Float) {
        glowPaint.alpha = (light * 255).roundToInt()
        canvas.drawBitmap(dot, null, dotBounds, glowPaint)
        ledPaint.color = if (light > .5f) Color.rgb(168, 152, 255) else Color.rgb(58, 53, 82)
        canvas.drawCircle(5f, 5f, 5f, ledPaint)
    }
}
