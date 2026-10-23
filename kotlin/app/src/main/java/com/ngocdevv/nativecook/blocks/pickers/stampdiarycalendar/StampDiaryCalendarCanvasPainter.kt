package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * The fallback below API 33 (no AGSL): the perforated stamp as a bitmap, mapped through the same
 * perspective matrix, with a blurred drop shadow and the postmark tinted on top. The page curl, the
 * gummed back and the paper shadow need the shader, so below API 33 a peeling stamp stays flat.
 */
internal class StampDiaryCalendarCanvasPainter : StampDiaryCalendarPainter {
    private val perforated = HashMap<String, Bitmap>()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val matrix = Matrix()

    override fun begin() = Unit

    /** The stamp's picture cropped to w : h with the half-round holes cut along its edges, 8 px per point. */
    private fun perforatedBitmap(a: StampDiaryCalendarStampArgs): Bitmap {
        val width = ((a.w * 4).roundToInt() * 2).coerceAtLeast(16)
        val height = ((a.h * 4).roundToInt() * 2).coerceAtLeast(16)
        val key = "${System.identityHashCode(a.art)}-$width-$height-${a.holes.first}-${a.holes.second}"
        return perforated.getOrPut(key) {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val crop = stampDiaryCalendarCrop(a.w, a.h, a.art.width.toDouble())
            val src = android.graphics.Rect(crop[0].toInt(), crop[1].toInt(), (crop[0] + crop[2]).toInt(), (crop[1] + crop[3]).toInt())
            canvas.drawBitmap(a.art, src, android.graphics.Rect(0, 0, width, height), bitmapPaint)
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            val px = width.toFloat() / a.holes.first
            val py = height.toFloat() / a.holes.second
            val radius = (StampDiaryCalendarTheme.PERFORATION_RADIUS * minOf(a.w / a.holes.first, a.h / a.holes.second) * (width / a.w)).toFloat()
            for (i in 0 until a.holes.first) {
                val x = (i + 0.5f) * px
                canvas.drawCircle(x, 0f, radius, clear)
                canvas.drawCircle(x, height.toFloat(), radius, clear)
            }
            for (j in 0 until a.holes.second) {
                val y = (j + 0.5f) * py
                canvas.drawCircle(0f, y, radius, clear)
                canvas.drawCircle(width.toFloat(), y, radius, clear)
            }
            result
        }
    }

    private fun mapTo(projection: StampDiaryCalendarProjection, halfW: Double, halfH: Double, space: StampDiaryCalendarSpace, w: Float, h: Float): Matrix {
        val c = projection.corners(halfW, halfH)
        val dst = FloatArray(8)
        for (i in 0 until 4) {
            dst[i * 2] = space.x(c[i][0]).toFloat()
            dst[i * 2 + 1] = space.y(c[i][1]).toFloat()
        }
        matrix.reset()
        matrix.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4)
        return matrix
    }

    /** Maps stamp-centred points (the rectangle +-halfW, +-halfH) to screen pixels. */
    private fun centredToScreen(projection: StampDiaryCalendarProjection, halfW: Double, halfH: Double, space: StampDiaryCalendarSpace): Matrix {
        val c = projection.corners(halfW, halfH)
        val dst = FloatArray(8)
        for (i in 0 until 4) {
            dst[i * 2] = space.x(c[i][0]).toFloat()
            dst[i * 2 + 1] = space.y(c[i][1]).toFloat()
        }
        val src = floatArrayOf(-halfW.toFloat(), -halfH.toFloat(), halfW.toFloat(), -halfH.toFloat(), halfW.toFloat(), halfH.toFloat(), -halfW.toFloat(), halfH.toFloat())
        val result = Matrix()
        result.setPolyToPoly(src, 0, dst, 0, 4)
        return result
    }

    override fun stamp(canvas: Canvas, space: StampDiaryCalendarSpace, a: StampDiaryCalendarStampArgs) {
        val bitmap = perforatedBitmap(a)
        val m = mapTo(a.projection, a.w / 2, a.h / 2, space, bitmap.width.toFloat(), bitmap.height.toFloat())
        val level = (255 * a.dim * a.opacity).toInt().coerceIn(0, 255)
        bitmapPaint.colorFilter = PorterDuffColorFilter(Color.argb(255, level, level, level), PorterDuff.Mode.MULTIPLY)
        bitmapPaint.alpha = (255 * a.opacity).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.concat(m)
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
        canvas.restore()
        bitmapPaint.colorFilter = null
        val mark = a.mark
        if (mark != null && a.inkOn > 0.5) {
            // The postmark's alpha field, tinted with the ink and faded in by the spread.
            val p = StampDiaryCalendarPostmarkSpec
            val scale = (StampDiaryCalendarInkMark.RADIUS * a.w / p.OUTER)
            val ox = StampDiaryCalendarInkMark.X * a.w
            val oy = StampDiaryCalendarInkMark.Y * a.h
            val local = Matrix()
            local.postTranslate((-p.RING_X).toFloat(), (-p.RING_Y).toFloat())
            local.postScale(scale.toFloat(), scale.toFloat())
            local.postRotate(Math.toDegrees(StampDiaryCalendarInkMark.ROTATION).toFloat())
            local.postTranslate(ox.toFloat(), oy.toFloat())
            val pixelsToScreen = Matrix(centredToScreen(a.projection, a.w / 2, a.h / 2, space))
            pixelsToScreen.preConcat(local)
            val ink = Color.rgb(
                (255 * (0.13 + (0.16 - 0.13) * a.inkWet)).toInt(),
                (255 * (0.14 + (0.2 - 0.14) * a.inkWet)).toInt(),
                (255 * (0.2 + (0.38 - 0.2) * a.inkWet)).toInt(),
            )
            inkPaint.colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN)
            inkPaint.alpha = (255 * 0.93 * a.inkSpread.coerceIn(0.0, 1.0) * (1.4 - a.inkThreshold).coerceIn(0.0, 1.0)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.clipRect(RectF(0f, 0f, space.widthPx, space.heightPx))
            canvas.concat(pixelsToScreen)
            canvas.drawBitmap(mark, 0f, 0f, inkPaint)
            canvas.restore()
        }
    }

    override fun shadow(canvas: Canvas, space: StampDiaryCalendarSpace, projection: StampDiaryCalendarProjection, w: Double, h: Double, sigma: Double, opacity: Double) {
        val radiusPx = (sigma * space.s * 1.7).toFloat().coerceAtLeast(1f)
        shadowPaint.maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
        shadowPaint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
        val c = projection.corners(w / 2, h / 2)
        val path = android.graphics.Path()
        path.moveTo(space.x(c[0][0]).toFloat(), space.y(c[0][1]).toFloat())
        for (i in 1 until 4) path.lineTo(space.x(c[i][0]).toFloat(), space.y(c[i][1]).toFloat())
        path.close()
        canvas.drawPath(path, shadowPaint)
    }
}
