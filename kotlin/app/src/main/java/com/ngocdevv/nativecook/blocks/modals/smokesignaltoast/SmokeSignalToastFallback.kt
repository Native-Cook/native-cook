package com.ngocdevv.nativecook.blocks.modals.smokesignaltoast

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun hsh(x: Float, y: Float): Float {
    val v = sin(x * 127.1f + y * 311.7f) * 43758.5453f
    return v - floor(v)
}

/**
 * Below Android 13 (no RuntimeShader): the sky, the treeline, the fire and the message drawn with
 * gradients, paths and the baked bitmaps in dp units. The treeline and the stars are built once
 * per size; the flame is a flickering teardrop, the puff a soft grey cloud, and the message the
 * sharp and blurred masks cross-faded by how formed it is, lifted while it forms and slid along
 * the wind as it tears. No noise-driven smoke or displacement.
 */
internal class SmokeFallback {
    private var key: SmokeGeo? = null
    private val trees = Path()
    private val stars = ArrayList<FloatArray>()
    private val flame = Path()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    private fun build(g: SmokeGeo) {
        key = g
        trees.reset()
        val colW = 22f
        var id = -1f
        while (id * colW < g.w + colW) {
            val h = 40f + 70f * hsh(id, 4f)
            val cx = (id + 0.5f + (hsh(id, 8f) - 0.5f) * 0.6f) * colW
            val w = 16f + 10f * hsh(id, 2f)
            val top = g.treeY - h
            // Four tiers of branches widening downward, as the shader's saw-tooth.
            trees.moveTo(cx, top)
            for (tier in 0 until 4) {
                val y1 = top + h * (tier + 1) / 4f
                val half = w * (0.25f + 0.75f * (tier + 1) / 4f)
                trees.lineTo(cx + half, y1)
                trees.lineTo(cx + half * 0.7f, y1)
            }
            trees.lineTo(cx - w * 0.7f, g.treeY)
            for (tier in 3 downTo 0) {
                val y1 = top + h * (tier + 1) / 4f
                val half = w * (0.25f + 0.75f * (tier + 1) / 4f)
                trees.lineTo(cx - half * 0.7f, y1)
                trees.lineTo(cx - half, y1)
            }
            trees.close()
            id += 1f
        }
        trees.addRect(androidx.compose.ui.geometry.Rect(0f, g.treeY, g.w, g.h))
        stars.clear()
        var y = 0f
        while (y < g.h * 0.6f) {
            var x = 0f
            while (x < g.w) {
                val h = hsh(x / 7f, y / 7f)
                if (h > 0.965f) stars.add(floatArrayOf(x + 3.5f, y + 3.5f, 0.5f + 0.7f * hsh(x, y + 7f), (h - 0.965f) * 22f, h))
                x += 7f
            }
            y += 7f
        }
    }

    fun DrawScope.draw(g: SmokeGeo, f: SmokeFrame, art: SmokeArt?) {
        if (key !== g) build(g)
        val pi = PI.toFloat()
        drawRect(
            Brush.verticalGradient(
                0f to Color(0.035f, 0.05f, 0.10f),
                0.7f to Color(0.08f, 0.085f, 0.15f),
                0.85f to Color(0.12f, 0.10f, 0.135f),
                startY = 0f,
                endY = g.h,
            ),
            size = Size(g.w, g.h),
        )
        for (s in stars) {
            val tw = 0.6f + 0.4f * sin(2f * pi * (f.phase * (1f + floor(s[4] * 40f) % 38f) + s[4] * 9f))
            drawCircle(Color(0.85f, 0.88f, 1f).copy(alpha = (s[3] * tw).coerceIn(0f, 1f)), radius = s[2], center = Offset(s[0], s[1]))
        }
        val flare = f.flare
        val fb = Offset(g.fireX, g.fireY)
        val glowR = 220f * g.fireS * (0.85f + 0.25f * flare)
        drawPath(trees, Color(0.022f, 0.035f, 0.042f))
        // The warm pool of firelight on the ground and in the air.
        drawOval(
            Brush.radialGradient(
                listOf(Color(0.42f, 0.17f, 0.05f).copy(alpha = 0.8f), Color.Transparent),
                center = fb,
                radius = glowR * 0.8f,
            ),
            topLeft = Offset(fb.x - glowR * 0.8f, fb.y - glowR * 0.22f),
            size = Size(glowR * 1.6f, glowR * 0.44f),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0.30f, 0.12f, 0.03f).copy(alpha = 0.5f + 0.3f * flare), Color.Transparent),
                center = fb - Offset(0f, 30f * g.fireS),
                radius = glowR * 0.9f,
            ),
            radius = glowR * 0.9f,
            center = fb - Offset(0f, 30f * g.fireS),
        )
        // Stones and logs.
        val s = g.fireS
        for (i in 0 until 7) {
            val a = pi * (0.05f + 0.9f * i / 6f)
            val c = fb + Offset(cos(a) * 46f * s, (6f + sin(a) * 9f) * s)
            drawOval(Color(0.16f, 0.15f, 0.15f), topLeft = c - Offset(10f * s, 7f * s), size = Size(20f * s, 14f * s))
        }
        for (a in floatArrayOf(18.3f, -18.3f)) {
            rotate(a, pivot = fb + Offset(0f, 2f * s)) {
                drawRect(Color(0.18f, 0.10f, 0.06f), topLeft = fb + Offset(-36f * s, -3.5f * s), size = Size(72f * s, 11f * s))
            }
        }
        // The flame: a teardrop that flickers with the loop.
        val fh = 74f * s * (0.9f + 0.2f * flare) * (0.92f + 0.08f * sin(f.phase * 2f * pi * 9f))
        val fw = 26f * s
        flame.reset()
        flame.moveTo(fb.x - fw, fb.y)
        flame.cubicTo(fb.x - fw, fb.y - fh * 0.45f, fb.x - fw * 0.2f, fb.y - fh * 0.7f, fb.x + fw * 0.08f * sin(f.phase * 2f * pi * 7f), fb.y - fh)
        flame.cubicTo(fb.x + fw * 0.2f, fb.y - fh * 0.7f, fb.x + fw, fb.y - fh * 0.45f, fb.x + fw, fb.y)
        flame.close()
        drawPath(
            flame,
            Brush.verticalGradient(
                0f to Color(0.70f, 0.12f, 0.03f),
                0.45f to Color(1f, 0.52f, 0.12f),
                1f to Color(1f, 0.92f, 0.62f),
                startY = fb.y - fh,
                endY = fb.y,
            ),
        )
        // Sparks.
        for (i in 0 until 12) {
            val h1 = hsh(i.toFloat(), 1f)
            val h2 = hsh(i.toFloat(), 2f)
            val speed = 2f + floor(h1 * 3f)
            val v = f.phase * speed + h2
            val life = v - floor(v)
            val sp = fb + Offset((h1 - 0.5f) * 30f * s + sin(life * 9f + i) * 10f * life, -20f * s - life * 150f * s)
            val fade = (1f - life) * min(1f, life / 0.08f)
            drawCircle(Color(1f, 0.6f, 0.2f).copy(alpha = fade.coerceIn(0f, 1f)), radius = 1.3f, center = sp)
        }
        // The puff.
        if (f.puff[3] > 0.001f) {
            val c = Offset(f.puff[0], f.puff[1])
            val rx = f.puff[2] * 1.25f
            val ry = f.puff[2] * 0.8f
            drawOval(
                Brush.radialGradient(
                    0f to Color(0.66f, 0.64f, 0.66f).copy(alpha = 0.72f * f.puff[3]),
                    0.6f to Color(0.62f, 0.62f, 0.68f).copy(alpha = 0.5f * f.puff[3]),
                    1f to Color.Transparent,
                    center = c,
                    radius = max(rx, ry),
                ),
                topLeft = c - Offset(rx, ry),
                size = Size(rx * 2f, ry * 2f),
            )
        }
        // The message.
        if (art != null && f.text[3] > 0.001f) {
            val gust = f.wind[2]
            val a = (f.text[3] * (1f - gust * gust * 0.85f)).coerceIn(0f, 1f)
            val crisp = f.text[0] * (1f - gust)
            val dx = f.wind[0] * gust * 170f
            val dy = f.wind[1] * gust * 170f - f.text[2]
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                rect.set(dx, art.bandTop + dy, g.w + dx, art.bandTop + dy + art.sharp.height / SMOKE_ART_SCALE)
                bitmapPaint.alpha = (a * (1f - crisp * 0.6f) * 255f).roundToInt().coerceIn(0, 255)
                bitmapPaint.colorFilter = tint
                nc.drawBitmap(art.soft, null, rect, bitmapPaint)
                bitmapPaint.alpha = (a * crisp * 0.9f * 255f).roundToInt().coerceIn(0, 255)
                nc.drawBitmap(art.sharp, null, rect, bitmapPaint)
            }
        }
    }

    // Warm grey smoke, never pure white.
    private val tint = android.graphics.PorterDuffColorFilter(
        android.graphics.Color.rgb(219, 209, 194),
        android.graphics.PorterDuff.Mode.SRC_IN,
    )
}
