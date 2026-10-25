package com.ngocdevv.nativecook.blocks.pickers.paintmixerpicker

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.exp
import kotlin.math.roundToInt

/*
 The bowl below Android 13, where there is no RuntimeShader: its two shadows and glazed rim as
 radial gradients, the paint as a disc of the evenly mixed colour, each drop as a soft blob of its
 own colour that spreads and fades into the mix as it is stirred (the shader's spiral arms, noisy
 edges, ripples and gloss are left out), and the wash-back as a growing disc of white paint.
 */

private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

internal class PaintFallback(private val g: PaintGeo) {
    private val u = g.u
    private val amb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(g.cx + 7 * u, g.cy + 14 * u, g.rim + 26 * u, intArrayOf(argb(0.18f, 0, 0, 0), argb(0.18f, 0, 0, 0), 0), floatArrayOf(0f, (g.rim - 6 * u) / (g.rim + 26 * u), 1f), Shader.TileMode.CLAMP)
    }
    private val contact = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(g.cx + 1 * u, g.cy + 2.5f * u, g.rim + 5 * u, intArrayOf(argb(0.22f, 0, 0, 0), argb(0.22f, 0, 0, 0), 0), floatArrayOf(0f, (g.rim - 2 * u) / (g.rim + 5 * u), 1f), Shader.TileMode.CLAMP)
    }
    private val glaze = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val p = g.paint / g.rim
        val lip = 0.88f
        shader = RadialGradient(
            g.cx, g.cy, g.rim,
            intArrayOf(0xFFC9C3B8.toInt(), 0xFFD8D2C7.toInt(), 0xFFF4EFE6.toInt(), 0xFFFBF8F2.toInt(), 0xFFD6CFC3.toInt()),
            floatArrayOf(p, p + 0.02f, lip, lip + 0.05f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fresh = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mixed = FloatArray(3)

    fun draw(c: Canvas, m: PaintMix) {
        c.drawCircle(g.cx, g.cy, g.rim + 26 * u, amb)
        c.drawCircle(g.cx, g.cy, g.rim + 5 * u, contact)
        c.drawCircle(g.cx, g.cy, g.rim, glaze)
        // The base paint (what the bowl holds before the newest drops), then each drop as a blob.
        var r = m.baseA[0]
        var gg = m.baseA[1]
        var b = m.baseA[2]
        base.color = argb(1f, (exp(-r) * 255).roundToInt().coerceIn(0, 255), (exp(-gg) * 255).roundToInt().coerceIn(0, 255), (exp(-b) * 255).roundToInt().coerceIn(0, 255))
        // Evenly mixed share of all drops, laid over the base as the stirring goes on.
        paintMixColor(m, mixed)
        var share = 0f
        for (d in m.drops) share = maxOf(share, d.m)
        c.drawCircle(g.cx, g.cy, g.paint, base)
        base.color = paintRgbInt(mixed)
        base.alpha = (share * 255).roundToInt().coerceIn(0, 255)
        c.drawCircle(g.cx, g.cy, g.paint, base)
        c.save()
        c.clipRect(g.cx - g.paint, g.cy - g.paint, g.cx + g.paint, g.cy + g.paint)
        for (d in m.drops) {
            val sig = (13 + 40 * d.m) * u
            r = exp(-d.a[0])
            gg = exp(-d.a[1])
            b = exp(-d.a[2])
            val col = argb(1f, (r * 255).roundToInt(), (gg * 255).roundToInt(), (b * 255).roundToInt())
            val a = (d.amt * m.fade * (1 - d.m) * 0.9f).coerceIn(0f, 1f)
            if (a < 0.01f) continue
            blob.shader = RadialGradient(d.x, d.y, sig * 2.2f, intArrayOf(col, col and 0x00FFFFFF), floatArrayOf(0.25f, 1f), Shader.TileMode.CLAMP)
            blob.alpha = (a * 255).roundToInt()
            c.drawCircle(d.x, d.y, sig * 2.2f, blob)
        }
        c.restore()
        // Wash-back: fresh white paint spreading from the middle.
        if (m.washFront > 0) {
            fresh.color = paintRgbInt(PAINT_BASE_RGB)
            c.drawCircle(g.cx, g.cy, minOf(g.paint, m.washFront), fresh)
        }
    }
}
