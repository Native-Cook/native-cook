package com.ngocdevv.nativecook.blocks.animations.plasmasquirclecharge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A cached, vector approximation of the plasma field for Android versions before API 33.
 * Coordinates are dp; the caller applies the density transform to the native canvas.
 */
internal class PlasmaSquircleChargeFallback {
    private companion object {
        const val SAMPLES = 512
        const val BUCKETS = 20
        const val HALF_X = 156f
        const val HALF_Y = 116f
        const val CORNER = 74f
        const val BLOOM_HALF = 320f
        const val BLOOM_PIXELS = 512
        const val TAU = 6.28318530718f

        fun smooth(value: Float): Float {
            val u = value.coerceIn(0f, 1f)
            return u * u * (3 - 2 * u)
        }

        fun mapped(value: Float) = ((1 - exp(-value * 1.4f)) * 255).roundToInt().coerceIn(0, 255)

        fun distance(x: Float, y: Float): Float {
            val qx = abs(x) - HALF_X + CORNER
            val qy = abs(y) - HALF_Y + CORNER
            val ox = max(qx, 0f)
            val oy = max(qy, 0f)
            return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - CORNER
        }
    }

    private val background = Color.rgb(mapped(.020f), mapped(.016f), mapped(.043f))
    private val bounds = RectF(-HALF_X, -HALF_Y, HALF_X, HALF_Y)
    private val bloomBounds = RectF(-BLOOM_HALF, -BLOOM_HALF, BLOOM_HALF, BLOOM_HALF)
    private val stagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val restPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f
        color = Color.rgb(243, 237, 255)
        alpha = 46
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paths = Array(BUCKETS + 1) { Path() }
    private val baseX = FloatArray(SAMPLES)
    private val baseY = FloatArray(SAMPLES)
    private val normalX = FloatArray(SAMPLES)
    private val normalY = FloatArray(SAMPLES)
    private val sideAngle = FloatArray(SAMPLES)
    private val frequencies = intArrayOf(7, 13, 29, 41, 17)
    private val turns = floatArrayOf(3f, -5f, 11f, 23f, -17f)
    private val sine = Array(frequencies.size) { FloatArray(SAMPLES) }
    private val cosine = Array(frequencies.size) { FloatArray(SAMPLES) }
    private val timeSine = FloatArray(frequencies.size)
    private val timeCosine = FloatArray(frequencies.size)
    private val x = FloatArray(SAMPLES + 1)
    private val y = FloatArray(SAMPLES + 1)
    private val brightness = FloatArray(SAMPLES + 1)
    private val haloWidths = floatArrayOf(71f, 44.4f, 28.9f, 20f, 13.3f, 7.8f, 4.4f)
    private val haloAlpha = floatArrayOf(.008f, .025f, .06f, .09f, .14f, .20f, .20f)

    // Cache the two stage-light endpoints and the blue bloom contribution. Their opacities
    // follow charge and charge squared without running a pixel loop during animation.
    private val restStage = rasterField(0)
    private val brightStage = rasterField(1)
    private val bloom = rasterField(2)

    private fun rasterField(layer: Int) = Bitmap.createBitmap(BLOOM_PIXELS, BLOOM_PIXELS, Bitmap.Config.ARGB_8888).apply {
        val pixels = IntArray(BLOOM_PIXELS * BLOOM_PIXELS)
        val step = 2 * BLOOM_HALF / BLOOM_PIXELS
        for (row in 0 until BLOOM_PIXELS) {
            val py = (row + .5f) * step - BLOOM_HALF
            for (column in 0 until BLOOM_PIXELS) {
                val px = (column + .5f) * step - BLOOM_HALF
                val d = distance(px, py) / 40f
                val edge = 1 - smooth((max(abs(px), abs(py)) - (BLOOM_HALF - 32)) / 32)
                val sx = px / 230f
                val sy = (py - 40f) / 150f
                val stage = exp(-(sx * sx + sy * sy) * 1.6f) * edge
                val stageLevel = if (layer == 0) .55f else 1f
                val red = .020f + .10f * stage * stageLevel
                val green = .016f + .07f * stage * stageLevel
                val blue = .043f + .24f * stage * stageLevel
                pixels[row * BLOOM_PIXELS + column] = if (layer < 2) {
                    Color.rgb(mapped(red), mapped(green), mapped(blue))
                } else {
                    val value = .38f * edge / (1 + d * d)
                    Color.rgb(
                        mapped(red + .30f * value) - mapped(red),
                        mapped(green + .28f * value) - mapped(green),
                        mapped(blue + .95f * value) - mapped(blue),
                    )
                }
            }
        }
        setPixels(pixels, 0, BLOOM_PIXELS, 0, 0, BLOOM_PIXELS, BLOOM_PIXELS)
    }

    init {
        val insetX = HALF_X - CORNER
        val insetY = HALF_Y - CORNER
        for (i in 0 until SAMPLES) {
            val a = TAU * i / SAMPLES
            val ux = cos(a)
            val uy = sin(a)
            val ax = abs(ux)
            val ay = abs(uy)
            val radius = when {
                ay * HALF_X <= ax * insetY -> HALF_X / ax
                ax * HALF_Y <= ay * insetX -> HALF_Y / ay
                else -> {
                    val projection = insetX * ax + insetY * ay
                    projection + sqrt(projection * projection - insetX * insetX - insetY * insetY + CORNER * CORNER)
                }
            }
            val px = ux * radius
            val py = uy * radius
            baseX[i] = px
            baseY[i] = py
            val nx = max(abs(px) - insetX, 0f) * if (px < 0) -1 else 1
            val ny = max(abs(py) - insetY, 0f) * if (py < 0) -1 else 1
            val length = sqrt(nx * nx + ny * ny)
            normalX[i] = nx / length
            normalY[i] = ny / length
            sideAngle[i] = abs((a + .785398f) % 1.570796f - .785398f)
            frequencies.forEachIndexed { wave, frequency ->
                sine[wave][i] = sin(a * frequency)
                cosine[wave][i] = cos(a * frequency)
            }
        }
    }

    fun draw(canvas: Canvas, width: Float, height: Float, level: Float, phase: Float) {
        val cover = smooth(level / .6f)
        val charge = .55f * cover + .45f * smooth((level - .6f) / .4f)
        canvas.drawColor(background)
        val saved = canvas.save()
        canvas.translate(width / 2, height * .52f)
        canvas.drawBitmap(restStage, null, bloomBounds, null)
        stagePaint.alpha = (255 * charge).roundToInt()
        if (stagePaint.alpha > 0) canvas.drawBitmap(brightStage, null, bloomBounds, stagePaint)
        bloomPaint.alpha = (255 * charge * charge).roundToInt()
        if (bloomPaint.alpha > 0) canvas.drawBitmap(bloom, null, bloomBounds, bloomPaint)
        canvas.drawRoundRect(bounds, CORNER, CORNER, restPaint)
        if (charge > 0f && cover >= .001f) {
            trace(cover, charge, phase)
            // Broad violet strokes replace the shader's exponential halo. The thinner,
            // brighter strokes at the end give the moving filament a white-hot centre.
            for (layer in haloWidths.indices) {
                stroke(canvas, haloWidths[layer], haloAlpha[layer], Color.rgb(153, 122, 255))
            }
            stroke(canvas, 4.15f, .12f, Color.rgb(242, 237, 255))
            stroke(canvas, 2.3f, .48f, Color.rgb(242, 237, 255))
            stroke(canvas, 1.1f, .70f, Color.rgb(242, 237, 255))
        }
        canvas.restoreToCount(saved)
    }

    private fun trace(cover: Float, charge: Float, phase: Float) {
        paths.forEach { it.rewind() }
        val time = TAU * phase
        for (wave in turns.indices) {
            timeSine[wave] = sin(time * turns[wave])
            timeCosine[wave] = cos(time * turns[wave])
        }
        fun wave(w: Int, i: Int) = sine[w][i] * timeCosine[w] + cosine[w][i] * timeSine[w]
        val amplitude = 1.2f + 3.4f * charge
        val reach = cover * .86f
        for (i in 0 until SAMPLES) {
            val wobble = amplitude * (.5f * wave(0, i) + .3f * wave(1, i) + .2f * wave(2, i))
            x[i] = baseX[i] - normalX[i] * wobble
            y[i] = baseY[i] - normalY[i] * wobble
            val lit = if (cover >= .999f) 1f else smooth((reach + .05f - sideAngle[i]) / .1f)
            val crackle = .72f + .28f * wave(3, i) * wave(4, i)
            brightness[i] = charge * lit * crackle
        }
        x[SAMPLES] = x[0]
        y[SAMPLES] = y[0]
        brightness[SAMPLES] = brightness[0]
        for (i in 0 until SAMPLES) {
            val power = (brightness[i] + brightness[i + 1]) * .5f
            if (power <= .001f) continue
            val bucket = (power * BUCKETS).roundToInt().coerceIn(1, BUCKETS)
            paths[bucket].moveTo(x[i], y[i])
            paths[bucket].lineTo(x[i + 1], y[i + 1])
        }
    }

    private fun stroke(canvas: Canvas, width: Float, opacity: Float, color: Int) {
        arcPaint.strokeWidth = width
        arcPaint.color = color
        for (bucket in 1..BUCKETS) {
            if (paths[bucket].isEmpty) continue
            arcPaint.alpha = (255 * opacity * bucket / BUCKETS).roundToInt()
            canvas.drawPath(paths[bucket], arcPaint)
        }
    }
}
