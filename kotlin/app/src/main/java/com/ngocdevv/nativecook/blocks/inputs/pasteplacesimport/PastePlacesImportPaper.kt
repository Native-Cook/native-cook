package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportMotion.Fold
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.MapColors
import com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport.PastePlacesImportTheme.Pin
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The text area as folding paper: four panels, each drawn through the exact homography of motion.ts `panelMatrix`
 * (Matrix.setPolyToPoly maps the panel's corners to where the turned panel puts them), lit by its angle, over the
 * paper's shadow on the page; then the pins' contact shadows, the route and the dots while the map lies flat.
 * The faces (the note as it folds, the empty area, the map) are bitmaps made once, before the first fold.
 */
internal class PastePlacesImportPaper(
    private val layout: Layout,
    drawFace: (Canvas, Double) -> Unit,
) {
    private class Face(val full: Bitmap, val strips: List<Bitmap>)

    private val card = PastePlacesImportTheme.Box.card
    private val cardX = layout.x(card.x)
    private val cardY = layout.y(card.y)
    private val cardWidth = layout.px(card.w)
    private val cardHeight = layout.px(card.h)
    private val bitmapWidth = ceil(cardWidth).toInt()
    private val bitmapHeight = ceil(cardHeight).toInt()
    private val panelHeight = cardHeight / Fold.panels
    private val perspective = (Fold.perspective * layout.k).toFloat()

    private val note = face(screenSpace = true) { canvas -> drawFace(canvas, Fold.close.start) }
    private val rest = face(screenSpace = true) { canvas -> drawFace(canvas, 0.0) }
    private val map = face { canvas -> PastePlacesImportMap.draw(canvas, layout.u) }
    private val mask = face { canvas ->
        val radius = layout.px(PastePlacesImportTheme.Radius.card)
        canvas.drawRoundRect(RectF(0f, 0f, cardWidth, cardHeight), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private val route = PastePlacesImportMap.route(layout.u)
    private val routeLength = PathMeasure(route, false).length
    private val routePart = Path()
    private val matrix = Matrix()
    private val source = FloatArray(8)
    private val target = FloatArray(8)
    private val rect = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val softPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A bitmap of the card's rectangle; `screenSpace` drawers work in screen coordinates, the others card-local. */
    private fun face(screenSpace: Boolean = false, draw: (Canvas) -> Unit): Face {
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        if (screenSpace) canvas.translate(-cardX, -cardY)
        draw(canvas)
        canvas.restore()
        val strips = (0 until Fold.panels).map { index ->
            val top = (bitmapHeight * index / Fold.panels.toFloat()).roundToInt()
            val bottom = (bitmapHeight * (index + 1) / Fold.panels.toFloat()).roundToInt()
            Bitmap.createBitmap(bitmap, 0, top, bitmapWidth, bottom - top)
        }
        return Face(bitmap, strips)
    }

    fun draw(canvas: Canvas, time: Double) {
        if (!PastePlacesImportMotion.panelsAt(time)) return
        val deg = PastePlacesImportMotion.foldAt(time)
        val onMap = PastePlacesImportMotion.mapFaceAt(time)
        val face = if (onMap) map else if (time < 6.0) note else rest
        if (deg == 0.0) {
            canvas.drawBitmap(face.full, cardX, cardY, bitmapPaint)
        } else {
            drawShadow(canvas, deg)
            for (index in 0 until Fold.panels) drawPanel(canvas, face, index, deg)
        }
        if (onMap && abs(deg) < 3.0) drawPins(canvas, time)
    }

    /** The paper's shadow on the page, under the folded stack. */
    private fun drawShadow(canvas: Canvas, deg: Double) {
        val k = layout.k
        val th = Math.toRadians(abs(deg))
        val stack = (Fold.panels * panelHeight * cos(th)).toFloat()
        val alpha = (Fold.shadowOpacity * sin(th)).toFloat()
        rect.set(cardX + 6f * k, cardY + Fold.shadowY.toFloat() * k, cardX + cardWidth - 6f * k, cardY + Fold.shadowY.toFloat() * k + maxOf(stack, 4f * k))
        softShape(canvas, rect, 12f * k, 12f * k, false, alpha, Fold.shadowBlur.toFloat() * k)
    }

    private fun drawPanel(canvas: Canvas, face: Face, index: Int, deg: Double) {
        val frame = PastePlacesImportMotion.panelAt(index, deg, panelHeight.toDouble(), perspective.toDouble())
        val k = PastePlacesImportMotion.panelK(index, deg, perspective.toDouble())
        val cosine = cos(Math.toRadians(deg))
        val centerX = cardX + cardWidth / 2.0
        val centerY = cardY + frame.ty + (index + 0.5) * panelHeight
        val strip = face.strips[index]
        // Panel corners (relative to its centre) -> screen, under y' = cos(th) y / (1 - k y), x' = x / (1 - k y).
        val halfWidth = cardWidth / 2.0
        val halfHeight = panelHeight / 2.0
        val corners = doubleArrayOf(-halfWidth, -halfHeight, halfWidth, -halfHeight, halfWidth, halfHeight, -halfWidth, halfHeight)
        for (corner in 0 until 4) {
            val xl = corners[corner * 2]
            val yl = corners[corner * 2 + 1]
            val denominator = 1.0 - k * yl
            target[corner * 2] = (centerX + xl / denominator).toFloat()
            target[corner * 2 + 1] = (centerY + cosine * yl / denominator).toFloat()
        }
        val w = strip.width.toFloat()
        val h = strip.height.toFloat()
        source[0] = 0f
        source[1] = 0f
        source[2] = w
        source[3] = 0f
        source[4] = w
        source[5] = h
        source[6] = 0f
        source[7] = h
        matrix.setPolyToPoly(source, 0, target, 0, 4)
        canvas.drawBitmap(strip, matrix, bitmapPaint)
        if (frame.shade > 0.002) {
            shadePaint.colorFilter = PorterDuffColorFilter(Color.argb((frame.shade * 255.0).roundToInt(), 0, 0, 0), PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(mask.strips[index], matrix, shadePaint)
            shadePaint.colorFilter = null
        }
    }

    /** Contact shadows, the route and the dots, only while the map lies flat. */
    private fun drawPins(canvas: Canvas, time: Double) {
        val k = layout.k
        val u = layout.u
        canvas.save()
        canvas.translate(cardX, cardY)

        PastePlacesImportTheme.SPOTS.forEachIndexed { index, spot ->
            val alpha = PastePlacesImportMotion.contactAt(time, index)
            if (alpha <= 0.0) return@forEachIndexed
            val h = PastePlacesImportMotion.dropAt(time, index).h
            val sx = spot.x * u
            val sy = spot.y * u
            val rx = Pin.shadowRx * k * (1f + 0.5f * h.toFloat())
            val ry = Pin.shadowRy * k * (1f + 0.5f * h.toFloat())
            rect.set(sx - rx, sy - ry + Pin.dotR * 0.4f * k, sx + rx, sy + ry + Pin.dotR * 0.4f * k)
            softShape(canvas, rect, rx, ry, true, alpha.toFloat(), Pin.shadowBlur * k)
        }

        val end = PastePlacesImportMotion.routeAt(time).toFloat()
        if (end > 0.001f) {
            routePart.reset()
            PathMeasure(route, false).getSegment(0f, routeLength * end, routePart, true)
            strokePaint.color = MapColors.routeCasing
            strokePaint.strokeWidth = Pin.routeCasing * k
            canvas.drawPath(routePart, strokePaint)
            strokePaint.color = MapColors.route
            strokePaint.strokeWidth = Pin.route * k
            canvas.drawPath(routePart, strokePaint)
        }

        PastePlacesImportTheme.SPOTS.forEachIndexed { index, spot ->
            val d = PastePlacesImportMotion.dotAt(time, index).toFloat()
            if (d <= 0f) return@forEachIndexed
            val g = 0.6f + 0.4f * d
            dotPaint.color = withAlpha(MapColors.dotRing, d)
            canvas.drawCircle(spot.x * u, spot.y * u, Pin.dotR * k * g, dotPaint)
            dotPaint.color = withAlpha(MapColors.dot, d)
            canvas.drawCircle(spot.x * u, spot.y * u, Pin.coreR * k * g, dotPaint)
        }
        canvas.restore()
    }

    /**
     * A soft black rounded rectangle or oval: a Gaussian-blurred shape (sigma in pixels). Below API 28 blur mask
     * filters are not accelerated, so a radial falloff (ovals) or concentric shells (rectangles) stand in.
     */
    private fun softShape(canvas: Canvas, bounds: RectF, radiusX: Float, radiusY: Float, oval: Boolean, alpha: Float, sigma: Float) {
        if (alpha <= 0f) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            softPaint.shader = null
            softPaint.color = withAlpha(MapColors.shadow, alpha)
            softPaint.maskFilter = BlurMaskFilter(maxOf(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
            if (oval) canvas.drawOval(bounds, softPaint) else canvas.drawRoundRect(bounds, radiusX, radiusY, softPaint)
            softPaint.maskFilter = null
            return
        }
        softPaint.maskFilter = null
        if (oval) {
            val reach = 2f * sigma
            val rx = bounds.width() / 2f + reach
            val ry = bounds.height() / 2f + reach
            canvas.save()
            canvas.translate(bounds.centerX(), bounds.centerY())
            canvas.scale(1f, ry / rx)
            softPaint.shader = RadialGradient(
                0f,
                0f,
                rx,
                intArrayOf(withAlpha(MapColors.shadow, alpha), withAlpha(MapColors.shadow, alpha * 0.9f), Color.TRANSPARENT),
                floatArrayOf(0f, (bounds.width() / 2f) / rx, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(0f, 0f, rx, softPaint)
            canvas.restore()
            softPaint.shader = null
            return
        }
        val shells = 6
        for (shell in 0 until shells) {
            val expansion = 2f * sigma * (shells - shell).toFloat() / shells - sigma
            softPaint.color = withAlpha(MapColors.shadow, alpha / shells * 1.4f)
            rect.set(bounds.left - expansion, bounds.top - expansion, bounds.right + expansion, bounds.bottom + expansion)
            canvas.drawRoundRect(rect, radiusX + expansion, radiusY + expansion, softPaint)
        }
    }

    private fun withAlpha(color: Int, opacity: Float): Int {
        return Color.argb((opacity.coerceIn(0f, 1f) * Color.alpha(color)).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
