package com.ngocdevv.nativecook.blocks.media.prismspectrumplayer

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The hero below Android 13, where there is no RuntimeShader: the same scene drawn with gradients
 and paths in dp. The indigo room and its glow, the white beam (a wide soft stroke under a thin
 core), the twelve spectral bars as wedges from the exit point with the shader's levels, the glass
 triangle with its outline and flares. Light is added (PorterDuff ADD) as in the shader, without
 the soft clip, the refraction, the dust or the grain.
 */

private const val PIF = PI.toFloat()

private fun fract(x: Float) = x - floor(x)

/** The shader's spectral(): red (0) to violet (1), softened, as an opaque colour int scaled by k. */
private fun spectral(x: Float, k: Float): Int {
    val hue = x * 0.79f
    val ch = FloatArray(3)
    val offs = floatArrayOf(0f, 0.6666667f, 0.3333333f)
    for (i in 0 until 3) {
        var c = (abs(fract(hue + offs[i]) * 6f - 3f) - 1f).coerceIn(0f, 1f)
        c = c * c * (3f - 2f * c)
        ch[i] = c + (1f - c) * 0.14f
    }
    val r = (ch[0] * k * 255).roundToInt().coerceIn(0, 255)
    val g = (ch[1] * k * 255).roundToInt().coerceIn(0, 255)
    val b = (ch[2] * k * 255).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun rgb(r: Float, g: Float, b: Float, k: Float = 1f) =
    (0xFF shl 24) or ((r * k * 255).roundToInt().coerceIn(0, 255) shl 16) or ((g * k * 255).roundToInt().coerceIn(0, 255) shl 8) or (b * k * 255).roundToInt().coerceIn(0, 255)

internal class PrismFallback(private val g: PrismGeo) {
    private val room = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, g.h,
            intArrayOf(rgb(0.050f, 0.058f, 0.122f), rgb(0.090f, 0.090f, 0.190f), rgb(0.118f, 0.078f, 0.160f)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = add }
    private val beamHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 22 * g.u
        xfermode = add
    }
    private val beamCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4.4f * g.u
        xfermode = add
    }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = add }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * g.u
        strokeJoin = Paint.Join.ROUND
    }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * g.u
        xfermode = add
    }
    private val path = Path()
    private val ports = FloatArray(4)

    fun draw(c: Canvas, pose: PrismPose, clock: Float) {
        val u = g.u
        val beam = pose.beam
        c.drawRect(0f, 0f, g.w, g.h, room)
        // The room's glow round the prism.
        val gr = 170 * u * 2
        glow.shader = RadialGradient(g.cx, g.cy, gr, rgb(0.09f, 0.10f, 0.20f, 0.35f + 0.65f * beam), 0, Shader.TileMode.CLAMP)
        c.drawCircle(g.cx, g.cy, gr, glow)
        prismPorts(g, pose.rot, ports)
        val inX = ports[0]
        val inY = ports[1]
        val outX = ports[2]
        val outY = ports[3]

        // The incoming beam, from the lower left to the entry point.
        val dx = 1f
        val dy = -0.42f
        val reach = g.w + g.h
        beamHalo.color = rgb(1f, 0.97f, 0.92f, 0.22f * beam)
        c.drawLine(inX - dx * reach, inY - dy * reach, inX, inY, beamHalo)
        beamCore.color = rgb(1f, 0.97f, 0.92f, beam)
        c.drawLine(inX - dx * reach, inY - dy * reach, inX, inY, beamCore)

        // The fan: twelve bars from the exit point.
        val dir = prismFanDir(pose.fan)
        val spread = prismFanSpread(pose.fan, pose.play)
        val a0 = dir - spread / 2
        val step = spread / 12
        for (i in 0 until 12) {
            val len = g.lmin + (g.lmax - g.lmin) * prismLevel(i, clock, pose.play)
            val from = a0 + step * (i + 0.12f)
            val to = a0 + step * (i + 0.88f)
            path.rewind()
            path.moveTo(outX, outY)
            for (k in 0..4) {
                val a = from + (to - from) * k / 4
                path.lineTo(outX + cos(a) * len, outY + sin(a) * len)
            }
            path.close()
            band.color = spectral(i / 11f, 0.9f * beam)
            c.drawPath(path, band)
        }

        // The glass: a cool translucent triangle, its outline, the beam inside it.
        path.rewind()
        for (k in 0 until 3) {
            val a = pose.rot + 2 * PIF * k / 3 - PIF / 2
            val x = g.cx + cos(a) * g.r
            val y = g.cy + sin(a) * g.r
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        glass.color = 0x55223050
        c.drawPath(path, glass)
        outline.color = rgb(0.86f, 0.90f, 1f, 0.45f + 0.35f * beam)
        c.drawPath(path, outline)
        inner.color = rgb(1f, 0.97f, 0.92f, 0.7f * beam)
        c.drawLine(inX, inY, outX, outY, inner)

        // Flares where the beam meets the glass, and at the exit.
        flare(c, inX, inY, 22 * u, rgb(1f, 0.97f, 0.9f, 0.5f * beam))
        flare(c, outX, outY, 12 * u, rgb(1f, 0.95f, 0.98f, 0.8f * beam))
    }

    private fun flare(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        glow.shader = RadialGradient(x, y, max(1f, r), color, 0, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, glow)
    }
}
