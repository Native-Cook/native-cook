package com.ngocdevv.nativecook.blocks.charts.ripplepondheatmap

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.exp

/**
 * Below Android 13 (no RuntimeShader): the pond floor as a gradient, the baked pads drawn still
 * over their soft shadows, each ripple as a pair of expanding rings that fade like the shader's
 * packets (140 dp/s, exp(-age / 1.3)), and the selection ring. No refraction, caustics or bobbing.
 */
internal class PondFallback {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN)
        alpha = 97
    }
    private val rect = RectF()

    fun DrawScope.draw(g: PondGeo, f: PondFrame, pads: PondPads?) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0.10f, 0.27f, 0.28f),
                1f to Color(0.035f, 0.12f, 0.15f),
                startY = 0f,
                endY = g.h,
            ),
            size = Size(g.w, g.h),
        )
        drawRect(
            Brush.verticalGradient(0f to Color(0.62f, 0.78f, 0.80f).copy(alpha = 0.12f), 1f to Color.Transparent, startY = 0f, endY = g.h),
            size = Size(g.w, g.h),
        )
        for (i in 0 until POND_SOURCES) {
            val age = f.src[i * 4 + 2]
            val amp = f.src[i * 4 + 3]
            if (age < 0f || age > 6f || amp <= 0.001f) continue
            val fade = (exp(-age / 1.3f) * amp).coerceIn(0f, 1f)
            val c = Offset(f.src[i * 4], f.src[i * 4 + 1])
            val r = 140f * age
            drawCircle(Color(0.85f, 1f, 0.9f).copy(alpha = 0.35f * fade), radius = r.coerceAtLeast(1f), center = c, style = Stroke(2f))
            if (r > 25f) drawCircle(Color(0.85f, 1f, 0.9f).copy(alpha = 0.2f * fade), radius = r - 25f, center = c, style = Stroke(1.5f))
        }
        if (pads != null) {
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                val h = pads.bitmap.height / POND_ART_SCALE
                rect.set(3f, pads.bandTop + 5f, g.w + 3f, pads.bandTop + 5f + h)
                nc.drawBitmap(pads.bitmap, null, rect, shadowPaint)
                rect.set(0f, pads.bandTop, g.w, pads.bandTop + h)
                nc.drawBitmap(pads.bitmap, null, rect, bitmapPaint)
            }
        }
        if (f.sel[2] > 0.001f) {
            drawCircle(
                Color(0.85f, 1f, 0.82f).copy(alpha = (0.6f * f.sel[2]).coerceIn(0f, 1f)),
                radius = g.grid[2] * 0.5f,
                center = Offset(f.sel[0], f.sel[1]),
                style = Stroke(2f),
            )
        }
    }
}
