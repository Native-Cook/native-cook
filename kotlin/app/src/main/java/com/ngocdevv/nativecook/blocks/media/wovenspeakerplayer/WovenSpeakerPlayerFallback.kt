package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerTheme as Theme
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Below API 33 there is no weave shader: the dome is drawn as two families of strands (polylines wound round the hemisphere with the shot's winding
 * Theta(phi), turned by the two layers' angles so they still shear), the black hole between them, the red lamp at the arc head and round the
 * display, the travelling rings, the finger's dent and the bright rim. No over / under crossings, fibre irregularity or motion blur.
 */
internal class WovenSpeakerPlayerFallback {
    private val strand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val clip = Path()

    private fun wind(phi: Double) = phi * (0.425 + 0.0393 * phi * phi)

    private fun a(c: Int, alpha: Double) = Color.argb((alpha.coerceIn(0.0, 1.0) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    fun draw(c: Canvas, cx: Float, cy: Float, s: Float, look: WovenSpeakerPlayerLook, duration: Double, accent: Int, strands: Int) {
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        clip.rewind()
        clip.addCircle(0f, 0f, Theme.DOME_R.toFloat(), Path.Direction.CW)
        c.clipPath(clip)
        // the black hole between the strands, a little paler towards the rim
        fill.shader = RadialGradient(0f, 0f, Theme.DOME_R.toFloat(), intArrayOf(Color.rgb(5, 5, 7), Color.rgb(5, 5, 7), Color.rgb(12, 12, 15)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, Theme.DOME_R.toFloat(), fill)
        fill.shader = null
        // the strands: theta = layer angle +- winding(phi), one every 2 pi / N; the shear shows as the layers' different angles
        val steps = 9
        for (fam in 0..1) {
            val psi = if (fam == 0) look.psiO else look.psiI
            val sign = if (fam == 0) 1.0 else -1.0
            val bright = if (fam == 0) 70 else 58
            strand.color = Color.rgb(bright, bright, bright + 3)
            strand.strokeWidth = 1.0f
            for (i in 0 until strands) {
                val base = psi + 2 * PI * i / strands
                path.rewind()
                for (k in 0..steps) {
                    val r = Theme.DISPLAY_R + 2 + (Theme.DOME_R - Theme.DISPLAY_R - 2) * k / steps
                    val th = base + sign * wind(asin(r / Theme.DOME_R))
                    val x = (r * cos(th)).toFloat()
                    val y = (r * sin(th)).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                c.drawPath(path, strand)
            }
        }
        // the red lamp at the arc head's bearing on the display's edge, and a faint ring round the display
        val head = -PI / 2 + (look.pos / duration) * 2 * PI
        val hx = (cos(head) * 105).toFloat()
        val hy = (sin(head) * 105).toFloat()
        val reach = (look.reach * (1 + look.turn * 0.5)).toFloat()
        fill.shader = RadialGradient(hx, hy, reach * 1.6f, a(accent, 0.55 * look.lamp), a(accent, 0.0), Shader.TileMode.CLAMP)
        c.drawCircle(hx, hy, reach * 1.6f, fill)
        fill.shader = null
        strand.color = a(accent, 0.3 * look.lamp)
        strand.strokeWidth = 5f
        c.drawCircle(0f, 0f, 107f, strand)
        // the travelling pulses
        strand.color = Color.WHITE
        for (i in 0 until 4) {
            val r = when (i) { 0 -> look.r0; 1 -> look.r1; 2 -> look.r2; else -> look.r3 }
            val amp = when (i) { 0 -> look.a0; 1 -> look.a1; 2 -> look.a2; else -> look.a3 }
            if (amp <= 0) continue
            strand.color = Color.argb((amp * 0.07 * 255).toInt().coerceIn(0, 255), 255, 255, 255)
            strand.strokeWidth = 7f
            c.drawCircle(0f, 0f, r.toFloat(), strand)
        }
        // the finger's dent
        if (look.dent > 0.05) {
            fill.shader = RadialGradient(look.dentX.toFloat(), look.dentY.toFloat(), 40f, a(Color.BLACK, 0.3 * look.dent / 5.0), a(Color.BLACK, 0.0), Shader.TileMode.CLAMP)
            c.drawCircle(look.dentX.toFloat(), look.dentY.toFloat(), 40f, fill)
            fill.shader = null
        }
        c.restore()
        // the bright rim line
        strand.color = Color.argb(46, 237, 242, 255)
        strand.strokeWidth = 1.2f * s
        c.drawCircle(cx, cy, (Theme.DOME_R - 0.7).toFloat() * s, strand)
    }
}
