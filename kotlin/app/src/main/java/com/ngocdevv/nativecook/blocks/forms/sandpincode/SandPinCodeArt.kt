package com.ngocdevv.nativecook.blocks.forms.sandpincode

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/*
 Sand PIN Code — the rake and the stylus, pre-rendered once into bitmaps (with their soft
 shadows), and the paints of a carved groove.
*/

internal class SandSprite(val bitmap: Bitmap, val rect: RectF)

private fun sigmaRadius(sigma: Float) = max(0.5f, (sigma - 0.5f) / 0.57735f)

private fun sprite(rect: RectF, px: Float, draw: (Canvas) -> Unit): SandSprite {
    val bw = max(1, (rect.width() * px).toInt() + 1)
    val bh = max(1, (rect.height() * px).toInt() + 1)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(px, px)
    c.translate(-rect.left, -rect.top)
    draw(c)
    bmp.prepareToDraw()
    return SandSprite(bmp, RectF(rect.left, rect.top, rect.left + bw / px, rect.top + bh / px))
}

private fun stroke(color: Int, width: Float, blur: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    this.color = color
    if (blur > 0f) maskFilter = BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL)
}

private fun fill(color: Int, blur: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    if (blur > 0f) maskFilter = BlurMaskFilter(sigmaRadius(blur), BlurMaskFilter.Blur.NORMAL)
}

/** The rake at x = 0: a bamboo crossbar with seven tines and its handle running off the bottom right. */
internal fun sandMakeRake(y: Float, px: Float) = sprite(RectF(-40f, y - 80f, 190f, y + 470f), px) { c ->
    val handle = Path().apply { moveTo(-2f, y + 10f); lineTo(140f, y + 420f) }
    c.save(); c.translate(12f, 10f); c.drawPath(handle, stroke(0x403C280F, 10f, 6f)); c.restore()
    c.drawRoundRect(RectF(0f, y - 46f, 16f, y + 62f), 6f, 6f, fill(0x473C280F, 6f))
    c.drawPath(handle, stroke(0xFFA87E45.toInt(), 9f))
    c.save(); c.translate(-2f, 0f); c.drawPath(handle, stroke(0x80FFE8BE.toInt(), 2f)); c.restore()
    val tine = fill(0xFF6F5129.toInt())
    for (k in 0 until 7) c.drawRoundRect(RectF(6f, y - 45f + k * 14f, 15f, y - 41f + k * 14f), 2f, 2f, tine)
    val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -8f, 0f, 8f, 0f,
            intArrayOf(0xFF7E5A2A.toInt(), 0xFFC99B5E.toInt(), 0xFFE2BB7D.toInt(), 0xFF8C6531.toInt()),
            floatArrayOf(0f, 0.35f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRoundRect(RectF(-8f, y - 54f, 8f, y + 54f), 6f, 6f, bar)
    c.drawRoundRect(RectF(-5f, y - 50f, -2f, y + 50f), 1.5f, 1.5f, fill(0x73FFECC8))
}

/** The bamboo stylus, its tip at (0, 0). */
internal fun sandMakeStylus(px: Float) = sprite(RectF(-16f, -84f, 60f, 22f), px) { c ->
    val art = Path().apply { moveTo(0f, 0f); lineTo(34f, -66f) }
    c.save(); c.translate(9f, 6f); c.drawPath(art, stroke(0x38462E14, 5f, 3f)); c.restore()
    c.drawPath(art, stroke(0xFFB38A52.toInt(), 5f))
    c.save(); c.translate(-1.2f, -0.4f); c.drawPath(art, stroke(0x8CFFECC4.toInt(), 1.4f)); c.restore()
    c.save()
    c.rotate((-1.1 * 180 / Math.PI).toFloat(), 16f, -26f)
    c.drawRoundRect(RectF(13f, -28f, 20f, -25f), 1f, 1f, fill(0xFF7A5A2C.toInt()))
    c.restore()
}

/** The five strokes of a groove: berm, lit lower wall, floor, shadowed upper wall, unlock glow. */
internal class SandGroovePaints {
    val berm = stroke(0x2EFFF4DE, 10f, 2.5f)
    val lit = stroke(0xB3FFF6E4.toInt(), 6.2f)
    val floor = stroke(0xFFA68B66.toInt(), 5f)
    val shade = stroke(0xBF624A2C.toInt(), 2.6f, 0.8f)
    val glow = stroke(0xFFFFCE78.toInt(), 4f, 3f)
}
