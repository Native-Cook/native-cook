// Label drift/blur, five original status coins and the sequenced close/retry glyph swap.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import android.graphics.*
import androidx.core.graphics.withTranslation
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.*

internal class HorizonPanel(assets: HorizonAssets, private val g: HorizonGeometry, strings: HorizonStrings) {
    private val assets = assets
    private val bar = HorizonBar(g)
    private val digits = HorizonDigits(g)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = assets.medium; textSize = 15 * g.typeScale; color = 0xFFF4F7FF.toInt() }
    private val labels = Array(5) { TextUtils.ellipsize(strings.labels[it], text, max(g.labelWidth, 0f), TextUtils.TruncateAt.END).toString() }
    private val blur = Array(17) { if (it == 0) null else BlurMaskFilter(it / 4f / g.density, BlurMaskFilter.Blur.NORMAL) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    fun draw(canvas: Canvas, state: HorizonState) {
        for (i in labels.indices) {
            val show = state.labels[i].value.coerceIn(0f, 1f)
            if (show > .001f) {
                text.alpha = (show * (if (i == 3) 1f else .82f) * 255).toInt()
                text.maskFilter = if (state.reduced) null else blur[((1 - show) * 16).roundToInt()]
                val dy = if (state.reduced) 0f else (if (state.label.ordinal == i) 1 else -1) * (1 - show) * 4
                canvas.drawText(labels[i], 64f, 158 + g.baseline + dy, text)
            }
            val opacity = state.icons[i].value * (.7f + .3f * HorizonMath.part(state.power.value, .1f, 1f))
            if (opacity > .001f) {
                val cx = 45f; val cy = 158 + g.glyphTop + 9
                canvas.withTranslation(cx, cy) {
                val scale = if (state.reduced) 1f else state.iconScale[i].value
                canvas.scale(scale, scale)
                if (i == 1 && !state.reduced) canvas.rotate(state.rotation)
                rect.set(-14.4f, -14.4f, 14.4f, 14.4f)
                paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(assets.status[i], null, rect, paint)
                }
            }
        }
        digits.draw(canvas, state)
        bar.draw(canvas, state)
        val out = (state.swap.value * 2).coerceIn(0f, 1f)
        val into = (state.swap.value * 2 - 1).coerceIn(0f, 1f)
        val pressScale = 1 - .08f * state.closePress.value.coerceIn(0f, 1f)
        glyph(canvas, assets.close, 1 - out, pressScale * if (state.reduced) 1f else 1 - .2f * out)
        glyph(canvas, assets.retry, into, pressScale * if (state.reduced) 1f else .8f + .2f * into)
    }
    private fun glyph(canvas: Canvas, bitmap: Bitmap, alpha: Float, scale: Float) {
        if (alpha <= 0) return
        canvas.withTranslation(g.cardWidth - 26, 26f) {
        canvas.scale(scale, scale)
        paint.alpha = (alpha * 255).toInt(); rect.set(-22f, -22f, 22f, 22f)
        canvas.drawBitmap(bitmap, null, rect, paint)
        }
    }
}
