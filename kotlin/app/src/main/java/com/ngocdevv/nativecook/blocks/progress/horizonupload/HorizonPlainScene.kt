// API 26–32: ordinary gradients and a cached blurred arc under an honest opaque surface.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.*
import androidx.core.graphics.withTranslation
import androidx.core.graphics.createBitmap
import kotlin.math.*

internal class HorizonPlainScene(private val g: HorizonGeometry) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sky = LinearGradient(0f, 0f, 0f, g.height * .52f, 0xFF020714.toInt(), 0xFF051022.toInt(), Shader.TileMode.CLAMP)
    private val aura = RadialGradient(g.cx, g.apex, 180f, intArrayOf(0xFF8CA6ED.toInt(), 0xAA314892.toInt(), 0x0015255C), null, Shader.TileMode.CLAMP)
    private val sun = RadialGradient(g.cx, g.apex, 16f,
        intArrayOf(-1, 0xEEDDE4FF.toInt(), 0x778CA6ED, 0x008CA6ED), floatArrayOf(0f, .08f, .25f, 1f), Shader.TileMode.CLAMP)
    private val planet = RectF(g.cx - 560, g.apex, g.cx + 560, g.apex + 1120)
    private val glowHeight = 96 + 560 - sqrt(max(0f, 560f * 560 - g.width * g.width / 4))
    private val glowDestination = RectF(0f, g.apex - 48, g.width, g.apex - 48 + glowHeight)
    // Software blur only at layout time, in a narrow bitmap strip. Rendering reuses it;
    // API 26's hardware canvas cannot reliably blur arbitrary stroked paths itself.
    private val limb = createBitmap(ceil(g.width * g.density).toInt(), ceil(glowHeight * g.density).toInt()).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.scale(g.density, g.density); canvas.translate(0f, -glowDestination.top)
        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF8CA6ED.toInt() }
        brush.strokeWidth = 8f; brush.alpha = 150; brush.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawArc(planet, 180f, 180f, false, brush)
        brush.strokeWidth = 3f; brush.alpha = 180; brush.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawArc(planet, 180f, 180f, false, brush)
        brush.strokeWidth = 1.2f; brush.color = 0xFFDDE4FF.toInt(); brush.maskFilter = null
        canvas.drawArc(planet, 180f, 180f, false, brush)
    }
    fun draw(canvas: Canvas, state: HorizonState) {
        paint.shader = sky; paint.alpha = 255; canvas.drawRect(0f, 0f, g.width, g.height, paint)
        val light = state.power.value * sqrt(state.progress)
        paint.shader = aura; paint.alpha = (light * 175).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, g.width, g.height, paint)
        paint.shader = null; paint.color = 0xFF02060F.toInt(); canvas.drawOval(planet, paint)
        paint.alpha = ((.07f + light) * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(limb, null, glowDestination, paint)
        paint.shader = sun; paint.alpha = (state.done.value.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawRect(g.cx - 16, g.apex - 16, g.cx + 16, g.apex + 16, paint)
        paint.shader = null; paint.alpha = 255
        canvas.withTranslation(g.x + state.shiftX.value, g.y + state.shiftY.value - state.lift * 46) {
            canvas.scale(state.scale, state.scale, g.cardWidth / 2, 120f)
            paint.color = 0xFF0B1224.toInt(); canvas.drawRoundRect(0f, 0f, g.cardWidth, 240f, 28f, 28f, paint)
            paint.color = 0x1FFFFFFF; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
            canvas.drawRoundRect(.5f, .5f, g.cardWidth - .5f, 239.5f, 27.5f, 27.5f, paint)
            paint.style = Paint.Style.FILL; paint.color = 0xFF060B18.toInt()
            canvas.drawRoundRect(24f, 158f, g.cardWidth - 24, 216f, 18f, 18f, paint)
            paint.color = 0xFF343B49.toInt(); val radius = 16 * (1 - .08f * state.closePress.value)
            canvas.drawCircle(g.cardWidth - 26, 26f, radius, paint)
            paint.color = 0x2EFFFFFF; paint.style = Paint.Style.STROKE
            canvas.drawCircle(g.cardWidth - 26, 26f, radius - .5f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
