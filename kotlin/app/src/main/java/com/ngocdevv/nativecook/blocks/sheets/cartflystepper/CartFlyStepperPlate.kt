package com.ngocdevv.nativecook.blocks.sheets.cartflystepper

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.max
import kotlin.math.roundToInt

// The dish, drawn from vectors in units of the plate's diameter, centred on 0, 0.
private const val PLATE_LIGHT = 0xF6F2EA
private const val PLATE_DARK = 0xE1D9CB
private const val PLATE_RIM = 0xD3CABA
private const val RICE = 0xFBF8F1
private const val SALMON_LIGHT = 0xE2703F
private const val SALMON_DARK = 0xA1401C
private const val GLAZE = 0x7A2E14
private const val SEED = 0xFFF8EA
private const val CUCUMBER = 0xD6E8B0
private const val CUCUMBER_RIND = 0x6F9A3E

// Edamame and herbs: x, y, radius, and which of the two greens.
private val GREENS = listOf(
    floatArrayOf(-0.27f, -0.12f, 0.045f, 0f),
    floatArrayOf(-0.2f, 0.17f, 0.04f, 1f),
    floatArrayOf(-0.06f, 0.27f, 0.045f, 0f),
    floatArrayOf(0.14f, 0.24f, 0.038f, 1f),
    floatArrayOf(0.27f, 0.08f, 0.045f, 0f),
    floatArrayOf(0.24f, -0.17f, 0.04f, 1f),
    floatArrayOf(0.05f, -0.28f, 0.042f, 0f),
    floatArrayOf(-0.14f, -0.25f, 0.036f, 1f),
    floatArrayOf(-0.3f, 0.05f, 0.034f, 1f),
)
private val GREEN_COLORS = intArrayOf(0x8DB255, 0x5F8F34)
// Cucumber slices: x, y, radius.
private val SLICES = listOf(floatArrayOf(0.2f, 0.15f, 0.07f), floatArrayOf(-0.22f, -0.02f, 0.065f))
// Sesame seeds on the fillet, in the fillet's own turned frame: x, y, angle in degrees.
private val SEEDS = listOf(
    floatArrayOf(-0.13f, -0.04f, 20f),
    floatArrayOf(-0.07f, 0.03f, -30f),
    floatArrayOf(-0.02f, -0.05f, 60f),
    floatArrayOf(0.04f, 0.04f, 10f),
    floatArrayOf(0.09f, -0.03f, -50f),
    floatArrayOf(0.14f, 0.02f, 35f),
    floatArrayOf(-0.1f, 0.06f, 80f),
    floatArrayOf(0.01f, 0.0f, -10f),
)
private const val FILLET_TURN = -32f

internal fun cartArgb(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (rgb and 0xFFFFFF)

/** Paints for the plate, reused from frame to frame. Mask-filter blurs need API 28 on hardware canvases. */
internal class CartFlyStepperPlate {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowSigma = -1f

    private fun fill(color: Int) = fill.apply { shader = null; this.color = color }
    private fun fill(shader: Shader) = fill.apply { color = android.graphics.Color.BLACK; this.shader = shader }

    /** The plate of salmon, rice and greens, [d] dp across, centred on [cx], [cy]. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, d: Float, alpha: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sigma = max(0.035f * d, 0.5f)
            if (sigma != shadowSigma) {
                shadow.maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
                shadowSigma = sigma
            }
            shadow.color = cartArgb(0x000000, 0.14f * alpha)
            canvas.drawCircle(cx, cy + 0.04f * d, 0.48f * d, shadow)
        }
        canvas.drawCircle(
            cx, cy, 0.5f * d,
            fill(RadialGradient(cx - 0.12f * d, cy - 0.14f * d, 0.62f * d, cartArgb(PLATE_LIGHT, alpha), cartArgb(PLATE_DARK, alpha), Shader.TileMode.CLAMP)),
        )
        line.strokeWidth = max(0.012f * d, 0.5f)
        line.color = cartArgb(PLATE_RIM, 0.8f * alpha)
        canvas.drawCircle(cx, cy, 0.42f * d, line)
        canvas.drawCircle(cx, cy, 0.36f * d, fill(cartArgb(RICE, alpha)))

        for ((x, y, r) in SLICES) {
            canvas.drawCircle(cx + x * d, cy + y * d, r * d, fill(cartArgb(CUCUMBER_RIND, alpha)))
            canvas.drawCircle(cx + x * d, cy + y * d, r * d * 0.8f, fill(cartArgb(CUCUMBER, alpha)))
        }
        for ((x, y, r, k) in GREENS) {
            canvas.drawCircle(cx + x * d, cy + y * d, r * d, fill(cartArgb(GREEN_COLORS[k.toInt()], alpha)))
        }

        // The fillet, turned, with a glaze and sesame seeds.
        canvas.save()
        canvas.translate(cx + 0.02f * d, cy - 0.02f * d)
        canvas.rotate(FILLET_TURN)
        val w = 0.44f * d
        val h = 0.2f * d
        canvas.drawRoundRect(
            RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), 0.09f * d, 0.09f * d,
            fill(LinearGradient(0f, -h / 2f, 0f, h / 2f, cartArgb(SALMON_LIGHT, alpha), cartArgb(SALMON_DARK, alpha), Shader.TileMode.CLAMP)),
        )
        line.strokeWidth = max(0.012f * d, 0.4f)
        line.color = cartArgb(GLAZE, 0.45f * alpha)
        for (k in -2..2) {
            val x = k * 0.07f * d
            canvas.drawLine(x - 0.03f * d, -h * 0.38f, x + 0.03f * d, h * 0.38f, line)
        }
        val seed = fill(cartArgb(SEED, alpha))
        for ((x, y, a) in SEEDS) {
            canvas.save()
            canvas.translate(x * d, y * d)
            canvas.rotate(a)
            canvas.drawOval(-0.014f * d, -0.007f * d, 0.014f * d, 0.007f * d, seed)
            canvas.restore()
        }
        canvas.restore()
    }
}
