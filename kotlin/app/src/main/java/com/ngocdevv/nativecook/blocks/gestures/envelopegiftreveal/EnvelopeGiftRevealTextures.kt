package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Static linen and paper grain, rendered once instead of recomputing shader noise per frame. */
internal class EnvelopeGiftRevealTextures(
    widthDp: Float,
    heightDp: Float,
    density: Float,
) {
    private val scale = min(density, 2f)
    val linen: Bitmap = createLinen(widthDp, heightDp, scale)
    val envelopeGrain: Bitmap = createPaperGrain(
        EnvelopeGiftRevealLayout.envW,
        EnvelopeGiftRevealLayout.envH,
        scale,
        seed = 3f,
    )
    val flapGrain: Bitmap = createPaperGrain(
        EnvelopeGiftRevealLayout.envW,
        EnvelopeGiftRevealLayout.envH,
        scale,
        seed = 11f,
    )
    val cardFrontGrain: Bitmap = createPaperGrain(
        EnvelopeGiftRevealLayout.cardW,
        EnvelopeGiftRevealLayout.cardH,
        scale,
        seed = 31f,
    )
    val cardBackGrain: Bitmap = createPaperGrain(
        EnvelopeGiftRevealLayout.cardW,
        EnvelopeGiftRevealLayout.cardH,
        scale,
        seed = 77f,
    )

    private fun createLinen(widthDp: Float, heightDp: Float, scale: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(
            max(1, ceil(widthDp * scale).toInt()),
            max(1, ceil(heightDp * scale).toInt()),
            Bitmap.Config.ARGB_8888,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            EnvelopeGiftRevealAgsl.drawLinen(bitmap, scale)
        } else {
            drawLinenFallback(bitmap, scale)
        }
        return bitmap
    }

    private fun createPaperGrain(widthDp: Float, heightDp: Float, scale: Float, seed: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(
            max(1, ceil(widthDp * scale).toInt()),
            max(1, ceil(heightDp * scale).toInt()),
            Bitmap.Config.ARGB_8888,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            EnvelopeGiftRevealAgsl.drawPaper(bitmap, scale, seed)
        } else {
            drawPaperFallback(bitmap, scale, seed.toInt())
        }
        return bitmap
    }

    private fun drawLinenFallback(bitmap: Bitmap, scale: Float) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(EnvelopeGiftRevealColors.table.toArgb())
        val lamp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                bitmap.width * 0.22f,
                bitmap.height * 0.12f,
                max(bitmap.width, bitmap.height) * 0.72f,
                intArrayOf(0x382C2117, 0x14231D16, Color.TRANSPARENT),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), lamp)
        val thread = Paint().apply { strokeWidth = max(0.5f, scale * 0.35f) }
        val step = max(2, (2.4f * scale).toInt())
        var x = 0
        while (x < bitmap.width) {
            val alpha = 7 + ((x * 17) and 7)
            thread.color = Color.argb(alpha, 225, 205, 178)
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), bitmap.height.toFloat(), thread)
            x += step
        }
        var y = 0
        while (y < bitmap.height) {
            val alpha = 6 + ((y * 13) and 7)
            thread.color = Color.argb(alpha, 65, 52, 39)
            canvas.drawLine(0f, y.toFloat(), bitmap.width.toFloat(), y.toFloat(), thread)
            y += step
        }
    }

    private fun drawPaperFallback(bitmap: Bitmap, scale: Float, seed: Int) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val tooth = Paint(Paint.ANTI_ALIAS_FLAG)
        val count = max(280, bitmap.width * bitmap.height / 95)
        for (index in 0 until count) {
            val x = envelopeGiftRevealHash(index, seed + 3) * bitmap.width
            val y = envelopeGiftRevealHash(index, seed + 17) * bitmap.height
            val dark = envelopeGiftRevealHash(index, seed + 29)
            tooth.color = Color.argb((6 + dark * 13).toInt(), 76, 56, 26)
            val radius = scale * (0.25f + envelopeGiftRevealHash(index, seed + 41) * 0.65f)
            canvas.drawCircle(x, y, radius, tooth)
        }
    }
}

private fun envelopeGiftRevealHash(index: Int, salt: Int): Float {
    val value = sin(index * 91.7 + salt * 47.3) * 43758.5453
    return (value - kotlin.math.floor(value)).toFloat()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object EnvelopeGiftRevealAgsl {
    private const val LINEN = """
        uniform float2 uSize;
        uniform float uDensity;
        float h(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float n(float2 p) {
          float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
          return mix(mix(h(i), h(i + float2(1.0, 0.0)), u.x),
                     mix(h(i + float2(0.0, 1.0)), h(i + float2(1.0, 1.0)), u.x), u.y);
        }
        half4 main(float2 xy) {
          float2 q = xy / uSize;
          float2 dp = xy / uDensity;
          float3 base = float3(0.114, 0.106, 0.094);
          float lamp = exp(-dot(q - float2(0.22, 0.12), q - float2(0.22, 0.12)) * 3.2);
          float vig = smoothstep(1.15, 0.35, length(q - float2(0.45, 0.42)));
          float weave = 0.5 + 0.5 * sin(dp.x * 2.6) * sin(dp.y * 2.6);
          float slub = n(float2(dp.x * 0.05, dp.y * 1.4)) * 0.6 + n(float2(dp.x * 1.4, dp.y * 0.05)) * 0.4;
          float3 c = base * (0.78 + 0.32 * vig) + float3(0.17, 0.13, 0.08) * lamp;
          c *= 0.94 + 0.05 * weave + 0.06 * slub + 0.03 * (h(floor(dp * 2.0)) - 0.5);
          return half4(half3(c), 1.0);
        }
    """

    private const val PAPER = """
        uniform float uSeed;
        uniform float uDensity;
        float h(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float n(float2 p) {
          float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
          return mix(mix(h(i), h(i + float2(1.0, 0.0)), u.x),
                     mix(h(i + float2(0.0, 1.0)), h(i + float2(1.0, 1.0)), u.x), u.y);
        }
        half4 main(float2 xy) {
          float2 p = xy / uDensity + uSeed;
          float tooth = h(floor(p * 3.0));
          float mottle = n(p * 0.07) * 0.6 + n(p * 0.21) * 0.4;
          float fine = n(p * 0.6 + 31.0);
          float a = 0.03 * tooth + 0.05 * mottle + 0.025 * fine;
          return half4(0.30, 0.22, 0.10, 1.0) * a;
        }
    """

    fun drawLinen(bitmap: Bitmap, density: Float) {
        val shader = RuntimeShader(LINEN).apply {
            setFloatUniform("uSize", bitmap.width.toFloat(), bitmap.height.toFloat())
            setFloatUniform("uDensity", density)
        }
        Canvas(bitmap).drawRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            Paint().apply { this.shader = shader },
        )
    }

    fun drawPaper(bitmap: Bitmap, density: Float, seed: Float) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val shader = RuntimeShader(PAPER).apply {
            setFloatUniform("uSeed", seed)
            setFloatUniform("uDensity", density)
        }
        Canvas(bitmap).drawRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            Paint().apply { this.shader = shader },
        )
    }
}
