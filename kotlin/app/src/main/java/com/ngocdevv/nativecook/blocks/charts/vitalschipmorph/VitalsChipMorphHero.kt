package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Android Canvas rendering of the single shared Readiness → Fitness → Heart curve.
 *
 * The host scales the canvas into the 1206 px reference coordinate system and clips it to the
 * hero bounds before calling [draw]. Consequently every coordinate below is deliberately kept in
 * the source's screen-reference pixels.
 */
internal class VitalsChipMorphHero(context: Context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val accent = Path()
    private val glyphs = glyphPath()
    private val halo = haloPath()
    private val points = DoubleArray(VitalsChipMorphMotion.N * 2)
    private val firstPoint = DoubleArray(2)
    private val lastPoint = DoubleArray(2)
    private val tempPoint = DoubleArray(2)
    private val bandX = DoubleArray(VitalsChipMorphMotion.N + 2)
    private val bandY = DoubleArray(VitalsChipMorphMotion.N + 2)
    private val bandNormalX = DoubleArray(VitalsChipMorphMotion.N + 2)
    private val bandNormalY = DoubleArray(VitalsChipMorphMotion.N + 2)
    private val frame = VitalsChipMorphMotion.Hero()
    private val ripple = VitalsChipMorphMotion.Ripple()
    private val haloBitmap = makeHaloBitmap()
    private val dotGlowBitmap = Bitmap.createBitmap(DOT_BITMAP_SIZE, DOT_BITMAP_SIZE, Bitmap.Config.ARGB_8888)
    private val dotGlowCanvas = Canvas(dotGlowBitmap)
    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BLUE_GLOW
        maskFilter = blur(DOT_BLUR)
    }
    /** API 26 draws [BlurMaskFilter] reliably only into this reusable software bitmap. */
    private val accentBitmap = Bitmap.createBitmap(REF_W.toInt(), HERO_BOTTOM.toInt(), Bitmap.Config.ARGB_8888)
    private val accentCanvas = Canvas(accentBitmap)
    private val heart: Bitmap? = runCatching {
        context.assets.open("vitalschipmorph/vitalschipmorph_heart.png").use(BitmapFactory::decodeStream)
    }.getOrNull()
    private val heartDst = RectF()
    private val haloDst = RectF(0f, 0f, REF_W.toFloat(), HERO_BOTTOM.toFloat())
    private val dotDst = RectF()
    private val ringOval = RectF()
    private val gradientMatrix = Matrix()
    private val accentPositions = floatArrayOf(0f, .75f, 1f)
    /** Runtime colours are 8-bit; cache every representable mix instead of allocating a shader per frame. */
    private val accentGradients = Array(256) { index ->
        val mix = index / 255.0
        val end = gammaMix(SEGMENT, MOVE_END, mix)
        sweepGradient(
            intArrayOf(gammaMix(SEGMENT, MOVE_START, mix), end, end),
            accentPositions,
        )
    }
    private val accentBlurFilters = Array(256) { index ->
        blur(max(mix(5.0, 0.0, index / 255.0), .01))
    }
    private val exerciseGradient = sweepGradient(
        intArrayOf(EXERCISE_START, EXERCISE_END, EXERCISE_END),
        floatArrayOf(0f, .63f, 1f),
    )
    private val standGradient = sweepGradient(
        intArrayOf(STAND_START, STAND_END, STAND_END),
        floatArrayOf(0f, .33f, 1f),
    )
    private var accentLo = -1
    private var accentHi = -1
    private var accentU = Double.NaN
    private var liquid: VitalsChipMorphLiquid? = null
    private var liquidUnavailable = false

    fun draw(
        canvas: Canvas,
        lo: Int,
        hi: Int,
        u: Double,
        beat: Double,
        quiet: Boolean,
        fxTime: Double,
        speed: Double,
        beatCount: Double,
    ) {
        val drawLo: Int
        val drawHi: Int
        val drawU: Double
        val drawAlpha: Double
        if (quiet) {
            val screen = if (u < .5) lo else hi
            drawLo = screen
            drawHi = screen
            drawU = 0.0
            drawAlpha = kotlin.math.abs(1 - 2 * u)
        } else {
            drawLo = lo
            drawHi = hi
            drawU = u
            drawAlpha = 1.0
        }
        VitalsChipMorphMotion.heroFrame(drawLo, drawHi, drawU, frame)
        VitalsChipMorphMotion.heroPoints(drawLo, drawHi, frame.sh, points)
        val closedness = mix(VitalsChipMorphMotion.CLOSED[drawLo], VitalsChipMorphMotion.CLOSED[drawHi], frame.sh)
        buildMainPath(closedness)

        val mainColor = gammaMix(
            VitalsChipMorphMotion.MAIN_COLOUR[drawLo].toArgb(),
            VitalsChipMorphMotion.MAIN_COLOUR[drawHi].toArgb(),
            frame.colourMix,
        )
        val mainAlpha = drawAlpha
        val beatScale = 1.0 + beat
        canvas.save()
        canvas.scale(beatScale.toFloat(), beatScale.toFloat(), HEART_CX, HEART_CY)
        val haloAlpha = frame.halo * mainAlpha * (HALO_OPACITY + 2.5 * beat)
        if (haloAlpha > .0005) {
            solid(haloPaint, Color.WHITE, haloAlpha)
            canvas.drawBitmap(haloBitmap, null, haloDst, haloPaint)
        }
        canvas.restore()
        val melt = if (quiet) 0.0 else VitalsChipMorphMotion.liquidAmount(drawLo, drawHi, drawU)
        val wobble = if (quiet) 0.0 else VitalsChipMorphMotion.wobbleAmount(speed)
        VitalsChipMorphMotion.rippleOf(if (quiet) -1.0 else beatCount, ripple)
        val rippleAlpha = ripple.amp * frame.heartImage
        val materialActive = melt > .0005 || wobble > .0005 || rippleAlpha > 0.0
        val liquidLayer = if (materialActive && canvas.isHardwareAccelerated) liquidLayer() else null
        val contentCanvas = liquidLayer?.begin(fxTime, melt, wobble, ripple.age, rippleAlpha) ?: canvas
        contentCanvas.save()
        contentCanvas.scale(beatScale.toFloat(), beatScale.toFloat(), HEART_CX, HEART_CY)
        val fillAlpha = frame.fill * (1 - frame.heartImage) * mainAlpha
        if (fillAlpha > .0005) {
            solid(fill, mainColor, fillAlpha)
            contentCanvas.drawPath(path, fill)
        }
        if (frame.width > .5 && mainAlpha > .0005) {
            solidStroke(stroke, mainColor, mainAlpha, frame.width.toFloat())
            contentCanvas.drawPath(path, stroke)
        }
        if (frame.heartImage > .0005 && heart != null) {
            solid(bitmapPaint, Color.WHITE, frame.heartImage * mainAlpha)
            heartDst.set(213f, 488f, 993f, 1238f)
            contentCanvas.drawBitmap(heart, null, heartDst, bitmapPaint)
        }
        contentCanvas.restore()

        drawInnerRing(contentCanvas, 0, frame.ringR0, frame.ringO0 * mainAlpha, frame.ringP0)
        drawInnerRing(contentCanvas, 1, frame.ringR1, frame.ringO1 * mainAlpha, frame.ringP1)

        val accentAlpha = frame.accentOpacity * mainAlpha
        if (accentAlpha > .0005 && frame.accentWidth > .01) {
            if (accentLo != drawLo || accentHi != drawHi || accentU != drawU) {
                buildBandPath(
                    frame.t0,
                    frame.t1,
                    closedness,
                    frame.accentWidth / 2,
                    frame.accentWidth / 2 * frame.accentRed,
                    mix(40.0, frame.accentWidth / 2, frame.accentRed),
                )
                val red = frame.accentRed
                val gradientIndex = (red.coerceIn(0.0, 1.0) * 255).roundToInt()
                resetForFill(blurPaint, accentGradients[gradientIndex], 1.0)
                blurPaint.maskFilter = accentBlurFilters[gradientIndex]
                accentBitmap.eraseColor(Color.TRANSPARENT)
                accentCanvas.drawPath(accent, blurPaint)
                accentLo = drawLo
                accentHi = drawHi
                accentU = drawU
            }
            solid(bitmapPaint, Color.WHITE, accentAlpha)
            contentCanvas.drawBitmap(accentBitmap, 0f, 0f, bitmapPaint)
        }

        val dotAlpha = frame.dotOpacity * mainAlpha
        if (dotAlpha > .0005) {
            pointAt(max(frame.t1 - DOT_LAG * (1 - frame.accentRed), frame.t0), closedness, tempPoint)
            val dotGlow = dotGlow(frame.dotD)
            val spriteRadius = dotGlow.width / 2.0
            dotDst.set(
                (tempPoint[0] - spriteRadius).toFloat(),
                (tempPoint[1] - spriteRadius).toFloat(),
                (tempPoint[0] + spriteRadius).toFloat(),
                (tempPoint[1] + spriteRadius).toFloat(),
            )
            solid(bitmapPaint, Color.WHITE, .9 * dotAlpha)
            contentCanvas.drawBitmap(dotGlow, null, dotDst, bitmapPaint)
            solid(fill, BLUE, dotAlpha)
            contentCanvas.drawCircle(tempPoint[0].toFloat(), tempPoint[1].toFloat(), (frame.dotD / 2).toFloat(), fill)
        }

        val glyphAlpha = .86 * frame.arrows * mainAlpha
        if (glyphAlpha > .0005) {
            solidStroke(stroke, GLYPH, glyphAlpha, 7.5f)
            contentCanvas.drawPath(glyphs, stroke)
        }
        liquidLayer?.end(canvas)
    }

    private fun drawInnerRing(canvas: Canvas, index: Int, radiusScale: Double, opacity: Double, progress: Double) {
        if (opacity <= .0005 || radiusScale <= 0.0) return
        val r = OUTER_RADIUS * radiusScale
        solidStroke(stroke, if (index == 0) EXERCISE_TRACK else STAND_TRACK, opacity, RING_STROKE)
        canvas.drawCircle(RING_CX, RING_CY, r.toFloat(), stroke)
        if (progress <= .0005) return
        resetForStroke(stroke, if (index == 0) exerciseGradient else standGradient, opacity, RING_STROKE)
        ringOval.set((RING_CX - r).toFloat(), (RING_CY - r).toFloat(), (RING_CX + r).toFloat(), (RING_CY + r).toFloat())
        canvas.drawArc(ringOval, -90f, (360 * min(progress, .9999)).toFloat(), false, stroke)
    }

    private fun buildMainPath(closedness: Double) {
        path.rewind()
        path.moveTo(points[0].toFloat(), points[1].toFloat())
        for (i in 1 until VitalsChipMorphMotion.N) path.lineTo(points[2 * i].toFloat(), points[2 * i + 1].toFloat())
        if (closedness > .999) path.close()
    }

    /** Exact port of Hero.tsx's bandPath, including its rational-conic rounded corners. */
    private fun buildBandPath(t0: Double, t1: Double, closedness: Double, half: Double, extend: Double, corner: Double) {
        accent.rewind()
        if (t1 - t0 <= .0005 || half <= .01) return
        val span = VitalsChipMorphMotion.N - 1 + closedness
        var count = 0
        pointAt(t0, closedness, firstPoint)
        bandX[count] = firstPoint[0]
        bandY[count] = firstPoint[1]
        count += 1
        val first = floor(t0 * span).toInt() + 1
        val last = kotlin.math.ceil(t1 * span).toInt() - 1
        for (k in first..last) {
            pointAt(k / span, closedness, tempPoint)
            bandX[count] = tempPoint[0]
            bandY[count] = tempPoint[1]
            count += 1
        }
        pointAt(t1, closedness, lastPoint)
        bandX[count] = lastPoint[0]
        bandY[count] = lastPoint[1]
        count += 1
        if (count < 2) return
        for (i in 0 until count) {
            val i0 = max(i - 1, 0)
            val i1 = min(i + 1, count - 1)
            var dx = bandX[i1] - bandX[i0]
            var dy = bandY[i1] - bandY[i0]
            val magnitude = hypot(dx, dy)
            val length = if (magnitude > 0.0) magnitude else 1.0
            dx /= length
            dy /= length
            bandNormalX[i] = -dy
            bandNormalY[i] = dx
            if (i == 0) {
                bandX[0] -= dx * extend
                bandY[0] -= dy * extend
            }
            if (i == count - 1) {
                bandX[i] += dx * extend
                bandY[i] += dy * extend
            }
        }
        val c = corner.coerceIn(0.0, half)
        val dx0 = bandNormalY[0]
        val dy0 = -bandNormalX[0]
        val dx1 = bandNormalY[count - 1]
        val dy1 = -bandNormalX[count - 1]
        val lx0 = bandX[0] + bandNormalX[0] * half
        val ly0 = bandY[0] + bandNormalY[0] * half
        accent.moveTo((lx0 + dx0 * c).toFloat(), (ly0 + dy0 * c).toFloat())
        for (i in 1 until count - 1) accent.lineTo((bandX[i] + bandNormalX[i] * half).toFloat(), (bandY[i] + bandNormalY[i] * half).toFloat())
        val lx1 = bandX[count - 1] + bandNormalX[count - 1] * half
        val ly1 = bandY[count - 1] + bandNormalY[count - 1] * half
        val rx1 = bandX[count - 1] - bandNormalX[count - 1] * half
        val ry1 = bandY[count - 1] - bandNormalY[count - 1] * half
        accent.lineTo((lx1 - dx1 * c).toFloat(), (ly1 - dy1 * c).toFloat())
        conicTo(accent, lx1 - dx1 * c, ly1 - dy1 * c, lx1, ly1, lx1 - bandNormalX[count - 1] * c, ly1 - bandNormalY[count - 1] * c)
        accent.lineTo((rx1 + bandNormalX[count - 1] * c).toFloat(), (ry1 + bandNormalY[count - 1] * c).toFloat())
        conicTo(accent, rx1 + bandNormalX[count - 1] * c, ry1 + bandNormalY[count - 1] * c, rx1, ry1, rx1 - dx1 * c, ry1 - dy1 * c)
        for (i in count - 2 downTo 1) accent.lineTo((bandX[i] - bandNormalX[i] * half).toFloat(), (bandY[i] - bandNormalY[i] * half).toFloat())
        val rx0 = bandX[0] - bandNormalX[0] * half
        val ry0 = bandY[0] - bandNormalY[0] * half
        accent.lineTo((rx0 + dx0 * c).toFloat(), (ry0 + dy0 * c).toFloat())
        conicTo(accent, rx0 + dx0 * c, ry0 + dy0 * c, rx0, ry0, rx0 + bandNormalX[0] * c, ry0 + bandNormalY[0] * c)
        accent.lineTo((lx0 - bandNormalX[0] * c).toFloat(), (ly0 - bandNormalY[0] * c).toFloat())
        conicTo(
            accent,
            lx0 - bandNormalX[0] * c,
            ly0 - bandNormalY[0] * c,
            lx0,
            ly0,
            lx0 + dx0 * c,
            ly0 + dy0 * c,
        )
        accent.close()
    }

    /** Android 14 exposes Skia's rational conic; API 26 samples the same rational equation. */
    private fun conicTo(path: Path, sx: Double, sy: Double, cx: Double, cy: Double, ex: Double, ey: Double) {
        if (Build.VERSION.SDK_INT >= 34) {
            path.conicTo(cx.toFloat(), cy.toFloat(), ex.toFloat(), ey.toFloat(), SQRT_HALF.toFloat())
            return
        }
        // Path.conicTo has no API 26 equivalent. 128 samples bound the 40 px source corners'
        // chord error below 0.001 ref px while retaining the exact rational conic evaluator.
        for (i in 1..128) {
            val t = i / 128.0
            val mt = 1.0 - t
            val denominator = mt * mt + 2 * SQRT_HALF * mt * t + t * t
            val x = (mt * mt * sx + 2 * SQRT_HALF * mt * t * cx + t * t * ex) / denominator
            val y = (mt * mt * sy + 2 * SQRT_HALF * mt * t * cy + t * t * ey) / denominator
            path.lineTo(x.toFloat(), y.toFloat())
        }
    }

    private fun pointAt(t: Double, closedness: Double, out: DoubleArray) {
        val span = VitalsChipMorphMotion.N - 1 + closedness
        var f = t * span
        if (closedness > .5) f = ((f % VitalsChipMorphMotion.N) + VitalsChipMorphMotion.N) % VitalsChipMorphMotion.N
        else f = f.coerceIn(0.0, (VitalsChipMorphMotion.N - 1).toDouble())
        val i0 = floor(f).toInt()
        val i1 = (i0 + 1) % VitalsChipMorphMotion.N
        val w = f - i0
        out[0] = points[2 * i0] + (points[2 * i1] - points[2 * i0]) * w
        out[1] = points[2 * i0 + 1] + (points[2 * i1 + 1] - points[2 * i0 + 1]) * w
    }

    private fun sweepGradient(colors: IntArray, positions: FloatArray): SweepGradient {
        val shader = SweepGradient(RING_CX, RING_CY, colors, positions)
        gradientMatrix.setRotate(-90f, RING_CX, RING_CY)
        shader.setLocalMatrix(gradientMatrix)
        return shader
    }

    private fun makeHaloBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(REF_W.toInt(), HERO_BOTTOM.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HEART_HALO
            maskFilter = blur(HALO_BLUR)
        }
        canvas.drawPath(halo, paint)
        return bitmap
    }

    private fun dotGlow(diameter: Double): Bitmap {
        dotGlowBitmap.eraseColor(Color.TRANSPARENT)
        val radius = DOT_BITMAP_SIZE / 2f
        dotGlowCanvas.drawCircle(radius, radius, (diameter / DOT_D * DOT_GLOW / 2).toFloat(), dotGlowPaint)
        return dotGlowBitmap
    }

    private fun haloPath(): Path = Path().apply {
        for (i in 0 until VitalsChipMorphMotion.N) {
            val x = HEART_CX + (VitalsChipMorphMotion.HEART[2 * i] - HEART_CX) * HALO_SCALE
            val y = HEART_CY + (VitalsChipMorphMotion.HEART[2 * i + 1] - HEART_CY) * HALO_SCALE
            if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
        }
        close()
    }

    private fun glyphPath(): Path = Path().apply {
        var y = RING_CY - OUTER_RADIUS.toFloat()
        moveTo(RING_CX - 14f, y)
        lineTo(RING_CX + 14f, y)
        moveTo(RING_CX + 3f, y - 11f)
        lineTo(RING_CX + 14f, y)
        lineTo(RING_CX + 3f, y + 11f)
        y = RING_CY - MIDDLE_RADIUS
        moveTo(RING_CX - 14f, y - 11f)
        lineTo(RING_CX - 3f, y)
        lineTo(RING_CX - 14f, y + 11f)
        moveTo(RING_CX + 1f, y - 11f)
        lineTo(RING_CX + 12f, y)
        lineTo(RING_CX + 1f, y + 11f)
        y = RING_CY - INNER_RADIUS
        moveTo(RING_CX, y + 14f)
        lineTo(RING_CX, y - 13f)
        moveTo(RING_CX - 11f, y - 2f)
        lineTo(RING_CX, y - 13f)
        lineTo(RING_CX + 11f, y - 2f)
    }

    private fun liquidLayer(): VitalsChipMorphLiquid? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || liquidUnavailable) return null
        liquid?.let { return it }
        val compiled = runCatching { VitalsChipMorphLiquid() }.getOrNull()
        if (compiled == null) {
            liquidUnavailable = true
            return null
        }
        liquid = compiled
        return compiled
    }

    private fun resetForFill(paint: Paint, shader: Shader, alpha: Double) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        paint.shader = shader
        paint.alpha = alpha255(alpha)
    }

    private fun resetForStroke(paint: Paint, shader: Shader, alpha: Double, width: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = width
        paint.shader = shader
        paint.alpha = alpha255(alpha)
    }

    private fun solid(paint: Paint, color: Int, alpha: Double) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha255(alpha)
    }

    private fun solidStroke(paint: Paint, color: Int, alpha: Double, width: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = width
        paint.color = color
        paint.alpha = alpha255(alpha)
    }

    private fun alpha255(alpha: Double) = (alpha.coerceIn(0.0, 1.0) * Color.alpha(Color.WHITE)).roundToInt()
    private fun blur(sigma: Double) = BlurMaskFilter(((sigma - .5) / .57735).coerceAtLeast(.01).toFloat(), BlurMaskFilter.Blur.NORMAL)

    private fun gammaMix(a: Int, b: Int, t: Double): Int {
        val p = t.coerceIn(0.0, 1.0)
        val aa = Color.alpha(a) + ((Color.alpha(b) - Color.alpha(a)) * p).roundToInt()
        return Color.argb(
            aa,
            gammaChannel(a, b, p, 16),
            gammaChannel(a, b, p, 8),
            gammaChannel(a, b, p, 0),
        )
    }

    private fun gammaChannel(a: Int, b: Int, p: Double, shift: Int): Int {
        val from = ((a ushr shift) and 0xff) / 255.0
        val to = ((b ushr shift) and 0xff) / 255.0
        val linearFrom = from.pow(2.2)
        val linearTo = to.pow(2.2)
        return (255 * (linearFrom + (linearTo - linearFrom) * p).pow(1 / 2.2))
            .roundToInt()
            .coerceIn(0, 255)
    }

    private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    private companion object {
        const val REF_W = 1206.0
        const val HERO_BOTTOM = 1420.0
        const val RING_CX = 608f
        const val RING_CY = 851.5f
        const val OUTER_RADIUS = 359.0
        const val MIDDLE_RADIUS = 262f
        const val INNER_RADIUS = 165f
        const val RING_STROKE = 88f
        const val HEART_CX = 601.5f
        const val HEART_CY = 863f
        const val HALO_SCALE = 1.22
        const val HALO_OPACITY = .72
        const val HALO_BLUR = 30.0
        const val DOT_LAG = .0335
        const val DOT_D = 76.0
        const val DOT_GLOW = 150.0
        const val DOT_BLUR = 10.0
        const val DOT_BITMAP_SIZE = 224
        const val SQRT_HALF = .7071067811865476
        const val SEGMENT = 0xFFB2D3F2.toInt()
        const val MOVE_START = 0xFFFA0232.toInt()
        const val MOVE_END = 0xFFFD0172.toInt()
        const val BLUE = 0xFF1592EA.toInt()
        const val BLUE_GLOW = 0xFFD4E9FB.toInt()
        const val EXERCISE_START = 0xFF05DD04.toInt()
        const val EXERCISE_END = 0xFF78E402.toInt()
        const val EXERCISE_TRACK = 0xFFC4EDC4.toInt()
        const val STAND_START = 0xFF02C1DB.toInt()
        const val STAND_END = 0xFF02D4B9.toInt()
        const val STAND_TRACK = 0xFFB5E5F1.toInt()
        const val GLYPH = 0xFF14060B.toInt()
        const val HEART_HALO = 0xFFF7C3CF.toInt()
    }
}
