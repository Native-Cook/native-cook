package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A shape blurred once into a bitmap, drawn with only its opacity changing (RN: a Skia `BlurMask` with a static blur
 * and an animated `opacity`). Rendering into a software bitmap keeps the blur identical on every API level, since
 * hardware canvases honour mask filters only from API 28.
 *
 * Positions are in dp, in the coordinate space the shape was defined in.
 */
internal class CassetteDeckGlow private constructor(
    private val bitmap: Bitmap,
    private val bounds: RectF,
    private val baseAlpha: Double,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, opacity: Double) {
        val alpha = (baseAlpha * opacity * 255.0).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        paint.alpha = alpha
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    companion object {
        /** A blurred disc (RN `Circle` with `BlurMask`). */
        fun circle(cx: Double, cy: Double, radius: Double, sigma: Double, color: Int, density: Float): CassetteDeckGlow {
            val pad = ceil(sigma * 3.0 + 1.0)
            val bounds = RectF(
                (cx - radius - pad).toFloat(), (cy - radius - pad).toFloat(),
                (cx + radius + pad).toFloat(), (cy + radius + pad).toFloat(),
            )
            return render(bounds, sigma, color, density) { canvas, paint ->
                canvas.drawCircle((cx * density - bounds.left * density).toFloat(), (cy * density - bounds.top * density).toFloat(), (radius * density).toFloat(), paint)
            }
        }

        /** A blurred filled path (RN `Path` with `BlurMask`). */
        fun path(path: Path, sigma: Double, color: Int, density: Float): CassetteDeckGlow {
            val box = RectF()
            path.computeBounds(box, true)
            val pad = ceil(sigma * 3.0 + 1.0).toFloat()
            val bounds = RectF(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)
            return render(bounds, sigma, color, density) { canvas, paint ->
                val toPixels = Matrix().apply {
                    postTranslate(-bounds.left, -bounds.top)
                    postScale(density, density)
                }
                val shape = Path()
                path.transform(toPixels, shape)
                canvas.drawPath(shape, paint)
            }
        }

        private fun render(
            bounds: RectF,
            sigma: Double,
            color: Int,
            density: Float,
            drawShape: (Canvas, Paint) -> Unit,
        ): CassetteDeckGlow {
            val widthPx = ceil(bounds.width() * density).toInt()
            val heightPx = ceil(bounds.height() * density).toInt()
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // The colour's alpha is applied when drawing, so the bitmap holds only the blurred coverage.
                this.color = (0xFF shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
                maskFilter = BlurMaskFilter(sigmaToRadius((sigma * density).toFloat()), BlurMaskFilter.Blur.NORMAL)
            }
            drawShape(Canvas(bitmap), paint)
            // Whole pixels: the bitmap is drawn 1:1 on screen.
            val drawn = RectF(bounds.left, bounds.top, bounds.left + widthPx / density, bounds.top + heightPx / density)
            return CassetteDeckGlow(bitmap, drawn, color.alpha / 255.0)
        }
    }
}
