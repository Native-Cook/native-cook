package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.blocks.charts.vitalschipmorph.VitalsChipMorphTheme as T
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

/** Hardware backdrop blur on API 31+ with a throttled software fallback down to API 26. */
internal class VitalsChipMorphGlass {
    private val tab = T.layout.tabBar
    private val downsample = 3.0
    private val padding = 84.0
    private val width = ceil((tab.w + padding * 2) / downsample).toInt()
    private val height = ceil((tab.h + padding * 2) / downsample).toInt()
    // UIKit intensity is not a sigma; use the reference intensity 28 as ref-pixel sigma.
    private val sigma = 28.0 / downsample
    private val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val sourceCanvas = Canvas(source)
    private val pixels = IntArray(width * height)
    private val temporary = IntArray(width * height)
    private val output = IntArray(width * height)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destination = RectF()
    private val lensBounds = RectF()
    private val lensRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val hardwareBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        VitalsChipMorphTabBlur()
    } else {
        null
    }
    private var lastLo = -1
    private var lastHi = -1
    private var lastU = Double.NaN
    private var lastQuiet = false
    private var lastUpdateClock = Double.NEGATIVE_INFINITY
    private val radius = ceil(sigma * 3).toInt()
    private val weights = DoubleArray(radius * 2 + 1) { index -> exp(-((index - radius) * (index - radius)) / (2 * sigma * sigma)) }.also { kernel ->
        val sum = kernel.sum()
        for (index in kernel.indices) kernel[index] /= sum
    }
    private val shadowBitmap = Bitmap.createBitmap(width + 80, height + 80, Bitmap.Config.ARGB_8888).also { image ->
        val canvas = Canvas(image)
        canvas.scale((1 / downsample).toFloat(), (1 / downsample).toFloat())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(26, 40, 40, 80)
            maskFilter = BlurMaskFilter(((27.0 - .5) / .57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
        val x = padding + 120
        val y = padding + 120 + 18
        canvas.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + tab.w).toFloat(), (y + tab.h).toFloat()), T.radius.tabBar.toFloat(), T.radius.tabBar.toFloat(), p)
    }

    fun shadow(canvas: Canvas) {
        val x = tab.x - padding - 120
        val y = tab.y - padding - 120
        destination.set(
            x.toFloat(),
            y.toFloat(),
            (x + shadowBitmap.width * downsample).toFloat(),
            (y + shadowBitmap.height * downsample).toFloat(),
        )
        canvas.drawBitmap(shadowBitmap, null, destination, bitmapPaint)
    }

    fun draw(canvas: Canvas, controller: VitalsChipMorphController) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            canvas.isHardwareAccelerated
        ) {
            hardwareBlur?.draw(canvas, controller)
            return
        }
        val changed =
            controller.lo != lastLo ||
                controller.hi != lastHi ||
                controller.u != lastU ||
                controller.quiet != lastQuiet
        val updateAllowed =
            controller.lo == controller.hi ||
                controller.clock - lastUpdateClock >= FALLBACK_FRAME
        if (changed && updateAllowed) {
            source.eraseColor(T.colors.page.toArgb())
            sourceCanvas.save()
            sourceCanvas.scale((1 / downsample).toFloat(), (1 / downsample).toFloat())
            sourceCanvas.translate((-tab.x + padding).toFloat(), (-tab.y + padding).toFloat())
            VitalsChipMorphPages.draw(sourceCanvas, controller.lo, controller.hi, controller.u, controller.quiet)
            sourceCanvas.restore()
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            blurPass(pixels, temporary, true)
            blurPass(temporary, output, false)
            blurred.setPixels(output, 0, width, 0, 0, width, height)
            lastLo = controller.lo
            lastHi = controller.hi
            lastU = controller.u
            lastQuiet = controller.quiet
            lastUpdateClock = controller.clock
        }
        destination.set(
            (tab.x - padding).toFloat(),
            (tab.y - padding).toFloat(),
            (tab.x - padding + width * downsample).toFloat(),
            (tab.y - padding + height * downsample).toFloat(),
        )
        canvas.drawBitmap(blurred, null, destination, bitmapPaint)
    }

    /** Software Liquid Glass fallback for the travelling chip selection on every supported API. */
    fun drawChipLens(canvas: Canvas, x: Double, y: Double, w: Double, h: Double, travel: Double) {
        val alpha = travel.coerceIn(0.0, 1.0)
        lensBounds.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        lensPaint.reset()
        lensPaint.isAntiAlias = true
        lensPaint.color = android.graphics.Color.argb(
            (255 * T.lens.tint.alpha * alpha).roundToInt(),
            255,
            255,
            255,
        )
        canvas.drawRoundRect(lensBounds, T.radius.chip.toFloat(), T.radius.chip.toFloat(), lensPaint)

        lensRimPaint.color = android.graphics.Color.argb((255 * 0.9 * alpha).roundToInt(), 255, 255, 255)
        val inset = lensRimPaint.strokeWidth / 2f
        lensBounds.inset(inset, inset)
        canvas.drawRoundRect(lensBounds, T.radius.chip.toFloat() - inset, T.radius.chip.toFloat() - inset, lensRimPaint)
    }

    private fun blurPass(input: IntArray, result: IntArray, horizontal: Boolean) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                var red = 0.0
                var green = 0.0
                var blue = 0.0
                for (offset in -radius..radius) {
                    val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val pixel = input[sy * width + sx]
                    val weight = weights[offset + radius]
                    red += (pixel shr 16 and 255) * weight
                    green += (pixel shr 8 and 255) * weight
                    blue += (pixel and 255) * weight
                }
                result[y * width + x] = android.graphics.Color.rgb(red.roundToInt(), green.roundToInt(), blue.roundToInt())
            }
        }
    }

    private companion object {
        const val FALLBACK_FRAME: Double = 1.0 / 15.0
    }
}

/** GPU backdrop blur for the tab bar on API 31+; the renderer's clip keeps only the bar visible. */
@TargetApi(Build.VERSION_CODES.S)
@RequiresApi(Build.VERSION_CODES.S)
private class VitalsChipMorphTabBlur {
    private val sourceNode = RenderNode("VitalsChipMorphTabSource")
    private val blurredNode = RenderNode("VitalsChipMorphTabBlurred")
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = T.colors.page.toArgb()
    }

    init {
        sourceNode.setPosition(0, 0, WIDTH, HEIGHT)
        blurredNode.setPosition(0, 0, WIDTH, HEIGHT)
        val sigma = 28f
        val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
        blurredNode.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
    }

    fun draw(canvas: Canvas, controller: VitalsChipMorphController) {
        val source = sourceNode.beginRecording(WIDTH, HEIGHT)
        source.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)
        VitalsChipMorphPages.draw(source, controller.lo, controller.hi, controller.u, controller.quiet)
        sourceNode.endRecording()

        val blurred = blurredNode.beginRecording(WIDTH, HEIGHT)
        blurred.drawRenderNode(sourceNode)
        blurredNode.endRecording()
        canvas.drawRenderNode(blurredNode)
    }

    private companion object {
        const val WIDTH = 1206
        const val HEIGHT = 2622
    }
}
