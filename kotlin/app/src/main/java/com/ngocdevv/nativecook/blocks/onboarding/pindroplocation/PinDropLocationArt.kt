package com.ngocdevv.nativecook.blocks.onboarding.pindroplocation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.text.TextPaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The complete native Canvas renderer. It owns no network or bundled artwork. */
internal class PinDropLocationArt {
    companion object {
        const val TITLE = "LOCATION"
        const val BODY = "Please enable location access so\nwe could provide you accurate results of\nnearest restaurants and bars"
        const val ALLOW = "Allow"
        const val DONE = "Location on"
        const val SKIP = "Skip"
        val PLACE_NAMES = arrayOf("Corner Coffee", "Juniper Books", "Night Market")
        val PLACE_X = doubleArrayOf(-0.58, 0.60, 0.35)
        val PLACE_Y = doubleArrayOf(0.30, -0.25, 0.72)
        val PLACE_SIDE = intArrayOf(-1, 1, 1)
        const val SPARKS = 240
    }

    private val regular = TypefaceCompat.create("sans-serif", false)
    private val semibold = TypefaceCompat.create("sans-serif-medium", false)
    private val bold = TypefaceCompat.create("sans-serif", true)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
    private val runtime = if (Build.VERSION.SDK_INT >= 33) runCatching { PinDropLocationRuntime() }.getOrNull() else null
    private val widthCache = mutableMapOf<String, FloatArray>()
    private val bodyLines = BODY.split('\n')
    private var cachedLayout: PinDropLocationLayout? = null
    private var backgroundGradient: RadialGradient? = null
    private val flatClip = Path()
    private val flatLit = Path()
    private val flatRoad = Path()
    private val checkPath = Path().apply {
        moveTo(7.4f, 0.4f)
        lineTo(10.6f, 3.4f)
        lineTo(16.8f, -3.2f)
    }
    private var pinHeight = -1.0
    private var pinPath = Path()

    private fun fill(color: Int, alpha: Double = 1.0): Paint {
        paint.shader = null
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 1f
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
        paint.color = color
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        return paint
    }

    private fun stroke(color: Int, width: Double, alpha: Double = 1.0): Paint {
        fill(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        return paint
    }

    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(
        x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()
    )

    private fun setupText(face: android.graphics.Typeface, size: Double, color: Int, alpha: Double = 1.0) {
        text.typeface = face
        text.textSize = size.toFloat()
        text.color = color
        text.alpha = (alpha.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        text.textAlign = Paint.Align.LEFT
        text.shader = null
    }

    private fun centeredText(canvas: Canvas, value: String, face: android.graphics.Typeface, size: Double, color: Int, cx: Double, top: Double, height: Double, alpha: Double = 1.0) {
        setupText(face, size, color, alpha)
        val fm = text.fontMetrics
        val y = top + (height - (fm.descent - fm.ascent)) * 0.5 - fm.ascent
        text.textAlign = Paint.Align.CENTER
        canvas.drawText(value, cx.toFloat(), y.toFloat(), text)
    }

    private fun trackedCentered(canvas: Canvas, value: String, face: android.graphics.Typeface, size: Double, color: Int, cx: Double, top: Double, height: Double, tracking: Double) {
        setupText(face, size, color)
        val widths = widthCache.getOrPut("$value/$size") {
            FloatArray(value.length).also { text.getTextWidths(value, it) }
        }
        val total = widths.sum().toDouble() + tracking * max(0, value.length - 1)
        val fm = text.fontMetrics
        val baseline = top + (height - (fm.descent - fm.ascent)) * 0.5 - fm.ascent
        var x = cx - total * 0.5
        for (i in value.indices) {
            canvas.drawText(value, i, i + 1, x.toFloat(), baseline.toFloat(), text)
            x += widths[i] + tracking
        }
    }

    private fun pinPath(height: Double): Path {
        if (pinHeight == height) return pinPath
        pinHeight = height
        val cy = 0.62 * height
        val r = 0.36 * height
        val sa = r / cy
        val ca = sqrt(1.0 - sa * sa)
        val length = sqrt(cy * cy - r * r)
        val startAngle = Math.toDegrees(kotlin.math.atan2(-length * ca + cy, length * sa)).toFloat()
        pinPath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            moveTo(0f, 0f)
            lineTo((length * sa).toFloat(), (-length * ca).toFloat())
            arcTo(RectF((-r).toFloat(), (-cy - r).toFloat(), (r).toFloat(), (-cy + r).toFloat()), startAngle, -(180f + 2f * startAngle), false)
            lineTo(0f, 0f)
            close()
            addCircle(0f, (-cy).toFloat(), (0.135 * height).toFloat(), Path.Direction.CW)
        }
        return pinPath
    }

    fun draw(
        canvas: Canvas,
        layout: PinDropLocationLayout,
        frame: PinDropLocationMotion.Frame,
        allowed: Boolean,
        reduced: Boolean,
        density: Float,
    ) {
        prepare(layout)
        canvas.save()
        canvas.scale(density, density)
        drawBackground(canvas, layout)
        if (runtime != null && !reduced) {
            paint.shader = runtime.shader(layout, frame)
            canvas.drawRect(0f, 0f, layout.w.toFloat(), layout.h.toFloat(), paint)
            paint.shader = null
            drawParticles(canvas, layout, frame)
        } else {
            drawFlatMap(canvas, layout)
            drawFlatPin(canvas, layout, frame)
        }
        drawPlaces(canvas, layout, frame)
        drawControls(canvas, layout, frame, allowed, reduced)
        drawFinger(canvas, layout, frame)
        canvas.restore()
    }

    private fun prepare(layout: PinDropLocationLayout) {
        if (cachedLayout == layout) return
        cachedLayout = layout
        val cx = layout.discX
        val cy = layout.discY
        val radius = layout.discR
        backgroundGradient = RadialGradient(cx.toFloat(), (cy + 40.0).toFloat(),
            (layout.w * 0.9).toFloat(), intArrayOf(COLOR_GLOW, COLOR_PAGE), null, Shader.TileMode.CLAMP)
        flatClip.reset()
        flatClip.addCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), Path.Direction.CW)
        val k = 0.03 / 2.0
        flatLit.reset()
        flatLit.moveTo((cx + (-1.5 + k) * radius).toFloat(), (cy + (-1.5 - k) * radius + 3.0 * radius).toFloat())
        flatLit.lineTo((cx + (1.5 + k) * radius).toFloat(), (cy + (-1.5 - k) * radius).toFloat())
        flatLit.lineTo((cx + 2.0 * radius).toFloat(), (cy + 2.0 * radius).toFloat())
        flatLit.close()
        flatRoad.reset()
        flatRoad.moveTo((cx - 0.5 * radius).toFloat(), (cy + 0.86 * radius).toFloat())
        flatRoad.quadTo((cx + 0.05 * radius).toFloat(), (cy + 0.05 * radius).toFloat(), (cx + 0.9 * radius).toFloat(), (cy - 0.5 * radius).toFloat())
    }

    private fun drawBackground(canvas: Canvas, layout: PinDropLocationLayout) {
        fill(COLOR_PAGE)
        paint.shader = backgroundGradient
        canvas.drawRect(0f, 0f, layout.w.toFloat(), layout.h.toFloat(), paint)
        paint.shader = null
    }

    /** API <33 and reduced motion use the RN flat map. Static paths are prepared once per size. */
    private fun drawFlatMap(canvas: Canvas, layout: PinDropLocationLayout) {
        canvas.save()
        canvas.clipPath(flatClip)
        canvas.drawCircle(layout.discX.toFloat(), layout.discY.toFloat(), layout.discR.toFloat(), fill(COLOR_DISC_A))
        canvas.drawPath(flatLit, fill(COLOR_DISC_B))
        canvas.drawPath(flatRoad, stroke(COLOR_ROAD, 4.0))
        canvas.restore()
    }

    private fun drawFlatPin(canvas: Canvas, layout: PinDropLocationLayout, frame: PinDropLocationMotion.Frame) {
        val height = layout.discR * 1.23
        val y = layout.pinTipY - (1.0 - frame.fall) * (layout.pinTipY + 140.0)
        canvas.save()
        canvas.translate(layout.discX.toFloat(), y.toFloat())
        canvas.drawPath(pinPath(height), fill(COLOR_PINK))
        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas, layout: PinDropLocationLayout, frame: PinDropLocationMotion.Frame) {
        val since = frame.since
        if (since < 0.0 || since > 0.65) return
        val tipX = layout.discX
        val tipY = layout.pinTipY
        for (index in 0 until SPARKS) {
            val i = index.toDouble()
            val life = 0.35 + 0.3 * hash1(i + 1.0)
            if (since > life) continue
            val angle = hash1(i + 2.0) * 2.0 * PI
            val speed = 120.0 + 260.0 * hash1(i + 3.0)
            val k = 1.0 - exp(-since * 6.0)
            val x = tipX + cos(angle) * speed * k / 6.0
            val y = tipY + sin(angle) * 0.5 * speed * k / 6.0 - 26.0 * k * hash1(i + 4.0)
            val progress = since / life
            val alpha = (1.0 - progress) * (1.0 - progress) * smooth(0.0, 0.04, since)
            if (alpha <= 0.001) continue
            canvas.drawCircle(x.toFloat(), y.toFloat(), (2.5 * (0.7 + 0.9 * hash1(i + 5.0))).toFloat(), fill(COLOR_SPARK, alpha))
        }
    }

    private fun drawPlaces(canvas: Canvas, layout: PinDropLocationLayout, frame: PinDropLocationMotion.Frame) {
        for (index in 0..2) {
            val opacity = (frame.places * 1.6 - index * 0.25).coerceIn(0.0, 1.0)
            if (opacity <= 0.0) continue
            val x = layout.discX + PLACE_X[index] * layout.discR
            val y = layout.discY + PLACE_Y[index] * layout.discR
            setupText(semibold, 12.0, COLOR_TITLE, opacity)
            val width = text.measureText(PLACE_NAMES[index]).toDouble() + 20.0
            val left = if (PLACE_SIDE[index] < 0) x - 10.0 - width else x + 10.0
            val top = y - 13.0 + (1.0 - opacity) * 8.0
            canvas.drawRoundRect(rect(left, top, width, 26.0), 13f, 13f, fill(COLOR_ALLOW_BG, 0.88 * opacity))
            canvas.drawRoundRect(rect(left + 0.5, top + 0.5, width - 1.0, 25.0), 12.5f, 12.5f, stroke(COLOR_BORDER, 0.75, 0.35 * opacity))
            setupText(semibold, 12.0, COLOR_TITLE, opacity)
            canvas.drawText(PLACE_NAMES[index], (left + 10.0).toFloat(), (top + 17.5).toFloat(), text)
        }
    }

    private fun drawControls(canvas: Canvas, layout: PinDropLocationLayout, frame: PinDropLocationMotion.Frame, allowed: Boolean, reduced: Boolean) {
        canvas.drawRoundRect(rect(layout.skipX, layout.skipY, layout.skipW, layout.skipH), 21f, 21f, fill(COLOR_SKIP_BG))
        centeredText(canvas, SKIP, semibold, 16.0, COLOR_SKIP, layout.skipX + layout.skipW * 0.5, layout.skipY, layout.skipH)

        trackedCentered(canvas, TITLE, bold, 15.0, COLOR_TITLE, layout.w * 0.5, layout.titleY - 11.0, 22.0, 2.2)
        setupText(regular, 17.0, COLOR_BODY)
        val fm = text.fontMetrics
        val bodyTop = layout.bodyY - 14.0
        text.textAlign = Paint.Align.CENTER
        for (index in bodyLines.indices) {
            val baseline = bodyTop + index * 28.0 - fm.ascent
            canvas.drawText(bodyLines[index], (layout.w * 0.5).toFloat(), baseline.toFloat(), text)
        }

        canvas.drawRoundRect(rect(layout.allowX, layout.allowY, layout.allowW, layout.allowH), 14f, 14f, fill(COLOR_ALLOW_BG))
        val label = if (allowed) DONE else ALLOW
        setupText(semibold, 17.0, COLOR_ALLOW)
        val textWidth = text.measureText(label).toDouble()
        val groupWidth = 24.0 + 14.0 + textWidth
        val groupX = layout.w * 0.5 - groupWidth * 0.5
        val centerY = layout.allowY + layout.allowH * 0.5
        canvas.drawCircle((groupX + 12.0).toFloat(), centerY.toFloat(), 10f, stroke(COLOR_PINK, 2.0))
        canvas.save()
        canvas.translate(groupX.toFloat(), centerY.toFloat())
        canvas.drawPath(checkPath, stroke(COLOR_PINK, 2.0))
        canvas.restore()
        text.textAlign = Paint.Align.LEFT
        val baseline = centerY - (text.fontMetrics.ascent + text.fontMetrics.descent) * 0.5
        canvas.drawText(label, (groupX + 24.0 + 14.0).toFloat(), baseline.toFloat(), text)
    }

    private fun drawFinger(canvas: Canvas, layout: PinDropLocationLayout, frame: PinDropLocationMotion.Frame) {
        if (frame.finger <= 0.0) return
        val x: Double
        val y: Double
        if (frame.fingerAt > 0.5) {
            x = layout.allowX + layout.allowW * 0.5
            y = layout.allowY + layout.allowH * 0.5
        } else if (frame.fingerU < 0.0) {
            x = layout.discX + 60.0
            y = layout.discY - 40.0 + 60.0 * (frame.fingerU + 1.0)
        } else {
            x = layout.discX + 60.0 - 120.0 * frame.fingerU
            y = layout.discY + 20.0 + 22.0 * sin(PI * frame.fingerU)
        }
        val radius = 22.0 * (1.0 - 0.12 * frame.press)
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), fill(Color.WHITE, 0.08 * frame.finger))
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), stroke(Color.WHITE, 1.5, 0.55 * frame.finger))
    }

    private fun hash1(value: Double): Double = fract(sin(value * 91.3458) * 47453.5453)

    private fun fract(value: Double): Double = value - kotlin.math.floor(value)

    private fun smooth(edge0: Double, edge1: Double, value: Double): Double {
        val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private object TypefaceCompat {
        fun create(family: String, bold: Boolean): android.graphics.Typeface =
            android.graphics.Typeface.create(family, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private val COLOR_PAGE = 0xFF0A071A.toInt()
    private val COLOR_GLOW = 0xFF17112F.toInt()
    private val COLOR_SKIP_BG = 0xFF1E1B33.toInt()
    private val COLOR_SKIP = 0xFFB9B3E0.toInt()
    private val COLOR_TITLE = 0xFFECE9F7.toInt()
    private val COLOR_BODY = 0xFFDEDBEA.toInt()
    private val COLOR_ALLOW_BG = 0xFF16132A.toInt()
    private val COLOR_ALLOW = 0xFFABA6D6.toInt()
    private val COLOR_PINK = 0xFFFF4A78.toInt()
    private val COLOR_DISC_A = 0xFF2A2147.toInt()
    private val COLOR_DISC_B = 0xFF4F4B9E.toInt()
    private val COLOR_ROAD = 0xFFC9C3F1.toInt()
    private val COLOR_BORDER = 0xFFC9C3EA.toInt()
    private val COLOR_SPARK = 0xFFFFCCD9.toInt()
}
