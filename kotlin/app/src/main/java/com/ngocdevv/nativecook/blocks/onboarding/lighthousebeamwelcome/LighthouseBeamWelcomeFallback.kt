package com.ngocdevv.nativecook.blocks.onboarding.lighthousebeamwelcome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The scene below Android 13, where there is no RuntimeShader: sky and sea gradients with the warm
 band at the horizon, the beam as a cone path from the lamp (warm, fading along its length, wider
 and shorter when it is foreshortened), and the text bitmap shown at the strength the shader's
 cone and afterglow give at the text block's centre. No fog, stars, waves or grain.
 */

private fun rgb(r: Float, g: Float, b: Float, a: Float = 1f) =
    ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or ((r * 255).roundToInt().coerceIn(0, 255) shl 16) or
        ((g * 255).roundToInt().coerceIn(0, 255) shl 8) or (b * 255).roundToInt().coerceIn(0, 255)

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** The shader's beamAt(p, phi).x: the cone (0 … 1) at a point. */
internal fun lighthouseCone(g: LightGeo, px: Float, py: Float, phi: Float): Float {
    val c = cos(phi)
    val s = sin(phi)
    var dx = c
    var dy = LIGHT_E * s + LIGHT_TILT
    val lam = sqrt(dx * dx + dy * dy)
    dx /= lam
    dy /= lam
    val ddx = px - g.lampX
    val ddy = py - g.lampY
    val along = ddx * dx + ddy * dy
    val across = abs(ddx * dy - ddy * dx)
    val spreadK = 0.56f / max(lam, 0.28f)
    val halfW = 14f * g.u + max(along, 0f) * spreadK
    val k = across / halfW
    val cone = (1 - smoothstep(0.5f, 1.08f, k)) * smoothstep(-6f * g.u, 10f * g.u, along)
    val face = if (s > 0) 1 + 1.3f * s.pow(6) else 1 + 0.5f * s
    val reach = exp(-max(along, 0f) / (560f * g.u * max(lam, 0.32f)))
    return cone * reach * face.coerceIn(0.5f, 1f)
}

internal class LighthouseFallback(private val g: LightGeo, private val content: Bitmap, private val ratio: Float) {
    private val sky = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, g.horizon,
            intArrayOf(rgb(0.039f, 0.078f, 0.188f), rgb(0.137f, 0.192f, 0.369f), rgb(0.325f, 0.251f, 0.478f)),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val band = Paint().apply {
        shader = LinearGradient(0f, g.horizon - 30 * g.u, 0f, g.horizon, rgb(0.30f, 0.16f, 0.20f, 0f), rgb(0.30f, 0.16f, 0.20f, 0.45f), Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val sea = Paint().apply {
        shader = LinearGradient(0f, g.horizon, 0f, g.h, rgb(0.165f, 0.161f, 0.329f), rgb(0.031f, 0.055f, 0.137f), Shader.TileMode.CLAMP)
    }
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val text = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val src = Rect()
    private val dst = RectF()

    fun draw(c: Canvas, phi: FloatArray, page: Float) {
        c.drawRect(0f, 0f, g.w, g.horizon, sky)
        c.drawRect(0f, g.horizon - 30 * g.u, g.w, g.horizon, band)
        c.drawRect(0f, g.horizon, g.w, g.h, sea)

        // The beam: a cone from the lamp.
        val p = phi[0]
        val s = sin(p)
        var dx = cos(p)
        var dy = LIGHT_E * s + LIGHT_TILT
        val lam = sqrt(dx * dx + dy * dy)
        dx /= lam
        dy /= lam
        val len = 560f * g.u * max(lam, 0.32f) * 1.6f
        val spreadK = 0.56f / max(lam, 0.28f)
        val w0 = 14f * g.u * 0.8f
        val w1 = w0 + len * spreadK * 0.8f
        val nx = -dy
        val ny = dx
        path.rewind()
        path.moveTo(g.lampX + nx * w0, g.lampY + ny * w0)
        path.lineTo(g.lampX + dx * len + nx * w1, g.lampY + dy * len + ny * w1)
        path.lineTo(g.lampX + dx * len - nx * w1, g.lampY + dy * len - ny * w1)
        path.lineTo(g.lampX - nx * w0, g.lampY - ny * w0)
        path.close()
        val face = (if (s > 0) 1 + 1.3f * s.pow(6) else 1 + 0.5f * s)
        beam.shader = LinearGradient(
            g.lampX, g.lampY, g.lampX + dx * len, g.lampY + dy * len,
            rgb(1f, 0.90f, 0.68f, min(1f, 0.34f * face)), rgb(1f, 0.90f, 0.68f, 0f),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(path, beam)

        // The text, as strong as the cone and its afterglow are at the block's centre.
        val tx = g.textX + g.textW * 0.4f
        val ty = g.textY + 80 * g.u
        var trail = lighthouseCone(g, tx, ty, phi[1]) * 0.9f
        trail = max(trail, lighthouseCone(g, tx, ty, phi[2]) * 0.66f)
        trail = max(trail, lighthouseCone(g, tx, ty, phi[3]) * 0.46f)
        trail = max(trail, lighthouseCone(g, tx, ty, phi[4]) * 0.26f)
        trail = max(trail, lighthouseCone(g, tx, ty, phi[5]) * 0.10f)
        val reveal = smoothstep(0f, 0.55f, max(lighthouseCone(g, tx, ty, p), trail))
        val left = (page * g.w * ratio).roundToInt()
        src.set(left, 0, left + (g.w * ratio).roundToInt(), content.height)
        dst.set(0f, g.contentY, g.w, g.contentY + content.height / ratio)
        text.alpha = ((0.035f + 0.965f * reveal) * 255).roundToInt().coerceIn(0, 255)
        c.drawBitmap(content, src, dst, text)
    }
}
