package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.roundToInt

/** Two texture reads for continuously interpolated, cached Gaussian levels. */
internal class ReferralBlurImages(
    source: Bitmap,
    private val sigmas: FloatArray,
    private val density: Float,
) {
    private val images = Array(sigmas.size) { ReferralBlur.gaussian(source, sigmas[it] * density) }
    private val shaders =
        Array(images.size) {
            BitmapShader(images[it], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
    private val shader =
        if (Build.VERSION.SDK_INT >= 33) {
            RuntimeShader(
                """
        uniform shader low;
        uniform shader high;
        uniform float amount;
        // Premultiplied interpolation keeps transparent edges from acquiring a dark fringe.
        half4 main(float2 p) {
          return mix(low.eval(p), high.eval(p), amount);
        }
    """,
            )
        } else {
            null
        }
    private val scalar = FloatArray(1)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val additive =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }

    fun draw(
        canvas: Canvas,
        sigma: Float,
        alpha: Float = 1f,
    ) {
        val soft = sigma.coerceIn(sigmas.first(), sigmas.last())
        var lower = 0
        while (lower < sigmas.lastIndex - 1 && sigmas[lower + 1] < soft) lower++
        val amount = (soft - sigmas[lower]) / (sigmas[lower + 1] - sigmas[lower])
        canvas.save()
        canvas.scale(1 / density, 1 / density)
        canvas.translate(-images[0].width / 2f, -images[0].height / 2f)
        paint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            shader.setInputShader("low", shaders[lower])
            shader.setInputShader("high", shaders[lower + 1])
            scalar[0] = amount
            ReferralShaderUniforms.set(shader, "amount", scalar)
            paint.shader = shader
            canvas.drawRect(0f, 0f, images[0].width.toFloat(), images[0].height.toFloat(), paint)
            paint.shader = null
        } else {
            val layer =
                canvas.saveLayerAlpha(
                    0f,
                    0f,
                    images[0].width.toFloat(),
                    images[0].height.toFloat(),
                    paint.alpha,
                )
            paint.alpha = ((1 - amount) * 255).roundToInt()
            canvas.drawBitmap(images[lower], 0f, 0f, paint)
            additive.alpha = (amount * 255).roundToInt()
            canvas.drawBitmap(images[lower + 1], 0f, 0f, additive)
            canvas.restoreToCount(layer)
        }
        canvas.restore()
    }
}
