package com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

internal val EtchInk = Color(0xFF1F2328)
internal val EtchMute = Color(0x941F2328) // rgba(31,35,40,0.58)
private val EtchRed = intArrayOf(0xFFE5493D.toInt(), 0xFFCB3129.toInt(), 0xFFA2221C.toInt())

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a).toArgb()

/** The screen's layout in dp, as in the React Native version: the toy fills the space between the parcel details and the buttons. */
internal class EtchGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val fx = 16f
    val fy = top + 124
    val fw = max(200f, w - 32)
    val buttonsTop = h - bottom - 16 - 52
    val fh = max(260f, buttonsTop - 44 - fy)
    val sx = fx + 26
    val sy = fy + 30
    val sw = fw - 52
    val sh = fh - 30 - 92
    val knobY = fy + fh - 46
    val knobLX = fx + 58
    val knobRX = fx + fw - 58
    val sig = EtchSketchSignatureMotion.signature(sx, sy, sw, sh)
    val restX = sig.pts[0]
    val restY = sig.pts[1]

    /** The baked frame's bounds: the toy plus room for its shadows. */
    val artLeft = fx - 80
    val artTop = fy - 62
    val artW = fw + 160
    val artH = fh + 160

    companion object {
        const val KNOB_R = 32f
    }
}

/** Everything that never changes, drawn once off the main thread. */
internal class EtchArt(val frame: Bitmap, val screen: Bitmap?)

/**
 * The red plastic frame (two shadows, a lit edge, a darker lower lip, grain), the bezel, the
 * SIGN HERE label and the knob bodies with their sockets, at [density] pixels per dp, in a
 * bitmap whose top-left is (artLeft, artTop). Below API 33 also the powder screen at rest.
 */
internal fun etchBake(g: EtchGeo, density: Float, withScreen: Boolean): EtchArt {
    val bw = ceil(g.artW * density).toInt()
    val bh = ceil(g.artH * density).toInt()
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    c.translate(-g.artLeft, -g.artTop)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val frame = RectF(g.fx, g.fy, g.fx + g.fw, g.fy + g.fh)

    // Two shadows under the frame.
    for ((sigma, dy, a) in listOf(Triple(17f, 18f, 0.28f), Triple(1.5f, 2f, 0.4f))) {
        p.reset(); p.isAntiAlias = true
        p.color = rgba(90, 20, 15, a)
        p.maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        c.save(); c.translate(0f, dy); c.drawRoundRect(frame, 34f, 34f, p); c.restore()
    }
    p.reset(); p.isAntiAlias = true
    p.shader = LinearGradient(0f, g.fy, 0f, g.fy + g.fh, EtchRed, null, Shader.TileMode.CLAMP)
    c.drawRoundRect(frame, 34f, 34f, p)

    // Plastic grain, mixed in with soft light: h(floor(p * 1.5)) at 0.3.
    val gx0 = floor(g.fx * 1.5f).toInt()
    val gy0 = floor(g.fy * 1.5f).toInt()
    val gw = ceil((g.fx + g.fw) * 1.5f).toInt() - gx0 + 1
    val gh = ceil((g.fy + g.fh) * 1.5f).toInt() - gy0 + 1
    val noise = IntArray(gw * gh)
    for (j in 0 until gh) for (i in 0 until gw) {
        val v = (etchHash((gx0 + i).toFloat(), (gy0 + j).toFloat()) * 255 + 0.5f).toInt()
        noise[j * gw + i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val grain = Bitmap.createBitmap(noise, gw, gh, Bitmap.Config.ARGB_8888)
    p.reset(); p.isAntiAlias = true
    p.shader = BitmapShader(grain, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(1 / 1.5f, 1 / 1.5f); postTranslate(gx0 / 1.5f, gy0 / 1.5f) })
    }
    p.isFilterBitmap = false
    p.alpha = (0.3f * 255).toInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) p.blendMode = BlendMode.SOFT_LIGHT
    else p.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    c.drawRoundRect(frame, 34f, 34f, p)
    grain.recycle()

    p.reset(); p.isAntiAlias = true
    p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = rgba(255, 190, 180, 0.45f)
    c.drawRoundRect(RectF(g.fx + 1, g.fy + 1, g.fx + g.fw - 1, g.fy + g.fh - 1), 33f, 33f, p)
    p.reset(); p.isAntiAlias = true
    p.color = rgba(80, 10, 8, 0.12f)
    c.drawRoundRect(RectF(g.fx + 6, g.fy + g.fh - 30, g.fx + g.fw - 6, g.fy + g.fh - 6), 14f, 14f, p)

    // The bezel around the powder screen.
    p.reset(); p.isAntiAlias = true
    p.shader = LinearGradient(
        0f, g.sy - 7, 0f, g.sy + g.sh + 7,
        intArrayOf(0xFF8E1A16.toInt(), 0xFFB32820.toInt(), 0xFFD5463A.toInt()), null, Shader.TileMode.CLAMP,
    )
    c.drawRoundRect(RectF(g.sx - 7, g.sy - 7, g.sx + g.sw + 7, g.sy + g.sh + 7), 20f, 20f, p)

    // SIGN HERE, tracked 3 dp, centred on the screen's middle, baseline knobY + 4.
    p.reset(); p.isAntiAlias = true
    p.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 700, false) else Typeface.DEFAULT_BOLD
    p.textSize = 11f
    p.color = 0xFFF3CF7A.toInt()
    p.setShadowLayer(sigmaToRadius(0.5f), 0f, 1f, rgba(90, 10, 6, 0.6f))
    val text = "SIGN HERE"
    val widths = FloatArray(text.length)
    p.getTextWidths(text, widths)
    val track = 3f
    var x = g.w / 2 - (widths.sum() + track * (text.length - 1)) / 2
    for (i in text.indices) {
        c.drawText(text, i, i + 1, x, g.knobY + 4, p)
        x += widths[i] + track
    }

    // Knob bodies: a socket shadow, a soft drop shadow, then cream plastic lit from the top left.
    val r = EtchGeo.KNOB_R
    for (kx in floatArrayOf(g.knobLX, g.knobRX)) {
        p.reset(); p.isAntiAlias = true
        p.color = rgba(70, 8, 6, 0.35f)
        c.drawCircle(kx, g.knobY + 3, r + 1, p)
        p.color = rgba(70, 8, 6, 0.4f)
        p.maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
        c.drawCircle(kx, g.knobY + 4, r, p)
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(
            kx - 9, g.knobY - 11, r * 1.5f,
            intArrayOf(0xFFFFFFFC.toInt(), 0xFFEEEAE2.toInt(), 0xFFCFC8BC.toInt()), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(kx, g.knobY, r, p)
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = rgba(255, 255, 255, 0.8f)
        c.drawCircle(kx, g.knobY, r - 9, p)
    }

    val screen = if (withScreen) etchBakeScreen(g.sx, g.sy, g.sw, g.sh, density) else null
    return EtchArt(bmp, screen)
}

/** The knob's grip: 28 notches round the rim, in knob-local dp (x, y pairs for drawLines). */
internal fun etchKnobRidges(r: Float): FloatArray {
    val n = 28
    val out = FloatArray(n * 4)
    for (i in 0 until n) {
        val a = i.toFloat() / n * PI.toFloat() * 2
        val cs = cos(a)
        val sn = sin(a)
        out[4 * i] = cs * (r - 5); out[4 * i + 1] = sn * (r - 5)
        out[4 * i + 2] = cs * (r - 0.8f); out[4 * i + 3] = sn * (r - 0.8f)
    }
    return out
}
