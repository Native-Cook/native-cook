package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.roundToInt

/**
 * The soft shadow of the React Native block's `shadowColor`, `shadowOpacity`, `shadowRadius` and
 * `shadowOffset`, which iOS draws from the view's shape: a blurred copy of the silhouette in the
 * shadow colour. [sigma] is the Gaussian sigma in the units of the canvas it is drawn on (iOS's
 * `shadowRadius` is twice the sigma). From Android 9 the blur is a mask filter; on 8 and 8.1, where
 * hardware canvases ignore mask filters, a copy and three translucent grown copies approximate its falloff.
 */
internal class DonationFanCarouselSoftShadow(shadowColor: Color, private val sigma: Float) {
    private val baseAlpha = shadowColor.alpha
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = shadowColor.copy(alpha = 1f).toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        } else {
            style = Paint.Style.FILL_AND_STROKE
        }
    }

    /** Draws the shadow of [silhouette] on [canvas], where the silhouette itself would be drawn. */
    fun draw(canvas: Canvas, silhouette: Path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.alpha = (baseAlpha * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawPath(silhouette, paint)
            return
        }
        for (layer in Fallback) {
            paint.strokeWidth = layer.grow * sigma * 2f
            paint.alpha = (baseAlpha * layer.coverage * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawPath(silhouette, paint)
        }
    }

    private class Layer(val grow: Float, val coverage: Float)

    private companion object {
        /** Copies grown by 0, 0.4, 1 and 1.8 sigma, summing to the Gaussian's 0.5 at the edge. */
        val Fallback = listOf(Layer(0f, 0.05f), Layer(0.4f, 0.20f), Layer(1f, 0.16f), Layer(1.8f, 0.10f))
    }
}
