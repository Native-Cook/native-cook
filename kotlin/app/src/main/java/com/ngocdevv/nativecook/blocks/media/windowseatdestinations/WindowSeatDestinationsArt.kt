package com.ngocdevv.nativecook.blocks.media.windowseatdestinations

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/** Images and Gaussian masks are decoded/rasterized once, never on a frame. Units are dp. */
internal class WindowSeatDestinationsArt(assets: AssetManager, private val density: Float) {
    private val directory = "windowseatdestinations/windowseatdestinations_"
    val serif: Typeface = Typeface.createFromAsset(assets, "${directory}InstrumentSerif-Regular.ttf")
    val italic: Typeface = Typeface.createFromAsset(assets, "${directory}InstrumentSerif-Italic.ttf")
    private val images = listOf("alps", "lagoon", "city", "dunes").map { name ->
        assets.open("${directory}view-$name.jpg").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })!!
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val opening = Path()
    private val shadow = blurred(328f, 452f, 154f, 10f, 0x1A13243A, oval = false)
    private val cloudA = blurred(170f, 54f, 0f, 16f, Color.WHITE, oval = true)
    private val cloudB = blurred(210f, 64f, 0f, 18f, Color.WHITE, oval = true)
    private val outerGradient = LinearGradient(0f, 0f, 300f, 424f,
        intArrayOf(Color.WHITE, 0xFFE2E8EE.toInt(), 0xFFBFC9D3.toInt()), null, Shader.TileMode.CLAMP)
    private val innerGradient = LinearGradient(0f, 0f, 0f, 368f,
        intArrayOf(0xFF9EABB8.toInt(), 0xFFE9EEF2.toInt()), null, Shader.TileMode.CLAMP)
    private val glareGradient = LinearGradient(0f, 0f, 244f, 368f * .6f,
        intArrayOf(0x59FFFFFF, 0x00FFFFFF, 0x1FFFFFFF), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    private val shadeGradient = LinearGradient(0f, 0f, 244f, 0f,
        intArrayOf(0xFFDDE3E9.toInt(), 0xFFEEF1F4.toInt(), 0xFFDDE3E9.toInt()), null, Shader.TileMode.CLAMP)
    private var wallHeight = -1f
    private var wallGradient: LinearGradient? = null

    private data class Blur(val bitmap: Bitmap, val padding: Float, val width: Float, val height: Float)

    private fun blurred(w: Float, h: Float, radius: Float, sigma: Float, color: Int, oval: Boolean): Blur {
        val padding = sigma * 3
        val bitmap = Bitmap.createBitmap(ceil((w + 2 * padding) * density).toInt(),
            ceil((h + 2 * padding) * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            // RN/Skia specifies Gaussian sigma; Android's constructor accepts a radius.
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
        val bounds = RectF(padding, padding, padding + w, padding + h)
        if (oval) canvas.drawOval(bounds, p) else canvas.drawRoundRect(bounds, radius, radius, p)
        return Blur(bitmap, padding, w, h)
    }

    private fun reset(color: Int = Color.WHITE) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = Color.alpha(color)
    }

    private fun gradient(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, shader: Shader) {
        canvas.save()
        canvas.translate(x, y)
        reset()
        paint.shader = shader
        rect.set(0f, 0f, w, h)
        if (r > 0) canvas.drawRoundRect(rect, r, r, paint) else canvas.drawRect(rect, paint)
        canvas.restore()
    }

    private fun blur(canvas: Canvas, image: Blur, x: Float, y: Float, alpha: Float = 1f) {
        reset()
        paint.alpha = (alpha * 255).toInt()
        rect.set(x - image.padding, y - image.padding, x + image.width + image.padding, y + image.height + image.padding)
        canvas.drawBitmap(image.bitmap, null, rect, paint)
    }

    fun draw(canvas: Canvas, width: Float, height: Float, clock: Double, shade: Float, view: Int, pan: Float) {
        if (wallHeight != height) {
            wallHeight = height
            wallGradient = LinearGradient(0f, 0f, 0f, height,
                intArrayOf(0xFFF1F4F7.toInt(), 0xFFC9D3DC.toInt()), null, Shader.TileMode.CLAMP)
        }
        gradient(canvas, 0f, 0f, width, height, 0f, wallGradient!!)
        val cx = width / 2
        val cy = height * .49f
        val fx = cx - 150
        val fy = cy - 212
        val ox = cx - 122
        val oy = cy - 184
        reset(0x0F13243A)
        canvas.drawRect(cx - 184, 0f, cx - 183, height, paint)
        canvas.drawRect(cx + 184, 0f, cx + 185, height, paint)
        canvas.save()
        canvas.translate(0f, (1.2 * sin(clock / 8 * 2 * PI * 5)).toFloat())
        blur(canvas, shadow, fx - 14, fy - 14)
        gradient(canvas, fx, fy, 300f, 424f, 140f, outerGradient)
        // The reference's inner gradient begins 8 dp below the inner bevel's top.
        canvas.save()
        canvas.translate(ox, oy)
        reset()
        paint.shader = innerGradient
        rect.set(-8f, -8f, 252f, 376f)
        canvas.drawRoundRect(rect, 122f, 122f, paint)
        canvas.restore()
        opening.rewind()
        rect.set(ox, oy, ox + 244, oy + 368)
        opening.addRoundRect(rect, 114f, 114f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(opening)
        reset(0xFF8FA6BC.toInt())
        canvas.drawRect(rect, paint)
        val viewX = ox - (368 - 244) / 2 + 35 - 70 * pan
        reset()
        rect.set(viewX, oy, viewX + 368, oy + 368)
        canvas.drawBitmap(images[view], null, rect, paint)
        val a = ox + 244 + 60 - ((clock / 8 * 3) % 1).toFloat() * (244 + 220)
        val b = ox + 244 + 60 - ((clock / 8 * 2 + .55) % 1).toFloat() * (244 + 220)
        blur(canvas, cloudA, a, oy + 70, .75f)
        blur(canvas, cloudB, b, oy + 210, .75f)
        gradient(canvas, ox, oy, 244f, 368f, 0f, glareGradient)
        gradient(canvas, ox, oy, 244f, 368 * shade, 0f, shadeGradient)
        reset(0xFFC3CCD5.toInt())
        rect.set(cx - 26, oy + 368 * shade - 12, cx + 26, oy + 368 * shade - 2)
        canvas.drawRoundRect(rect, 5f, 5f, paint)
        canvas.restore()
        reset(0x4013243A)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawPath(opening, paint)
        canvas.restore()
    }
}
