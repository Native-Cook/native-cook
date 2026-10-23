package com.ngocdevv.nativecook.blocks.lists.cardbalancewallet

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build

internal class CardBalanceWalletCardShader(
    private val field: Bitmap,
    private val density: Float,
) {
    private val gpu = if (Build.VERSION.SDK_INT >= 33) Gpu(field, density) else null
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fallbackReed = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        shift: Float,
        spec: Float,
        light: Float,
    ) {
        val renderer = gpu
        if (renderer != null) {
            renderer.draw(canvas, width, height, shift, spec, light)
            return
        }
        canvas.drawBitmap(field, null, RectF(0f, 0f, width, height), fallbackPaint)
        val start = width * 0.2f
        val end = width * 0.84f
        val pitch = 11.8f * density
        var x = start - ((start - 85.2f * density + shift * 40f * density) % pitch)
        while (x < end) {
            fallbackReed.shader = LinearGradient(
                x,
                0f,
                x + pitch,
                0f,
                intArrayOf(Color.argb(14, 0, 0, 0), Color.argb(20, 255, 255, 255), Color.argb(12, 0, 0, 0)),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(x, 0f, minOf(x + pitch, end), height, fallbackReed)
            x += pitch
        }
        if (spec > 0f) {
            val center = light * width
            fallbackReed.shader = LinearGradient(
                center - 42f * density,
                0f,
                center + 42f * density,
                height,
                intArrayOf(Color.TRANSPARENT, Color.argb((spec * 120f).toInt().coerceIn(0, 255), 255, 255, 255), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width, height, fallbackReed)
        }
    }

    @TargetApi(33)
    private class Gpu(
        bitmap: Bitmap,
        private val density: Float,
    ) {
        private val shader = RuntimeShader(Source)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@Gpu.shader }
        private val fieldShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
        private val matrix = Matrix()

        init {
            shader.setInputShader("field", fieldShader)
        }

        fun draw(canvas: Canvas, width: Float, height: Float, shift: Float, spec: Float, light: Float) {
            matrix.reset()
            matrix.setScale(width / 16f, height / 10f)
            fieldShader.setLocalMatrix(matrix)
            shader.setFloatUniform("size", width / density, height / density)
            shader.setFloatUniform("density", density)
            shader.setFloatUniform("shift", shift)
            shader.setFloatUniform("spec", spec)
            shader.setFloatUniform("light", light)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }

    private companion object {
        private const val Source = """
            uniform shader field;
            uniform float2 size;
            uniform float density;
            uniform float shift;
            uniform float spec;
            uniform float light;

            half4 main(float2 xy) {
              float2 point = xy / density;
              float2 uv = point / size;
              float px = uv.x * size.x - 85.2 + shift * 40.0;
              float flute = fract(px / 11.8);
              float reach = smoothstep(0.2, 0.25, uv.x) * (1.0 - smoothstep(0.78, 0.84, uv.x));
              float2 samplePoint = uv;
              samplePoint.x += (flute - 0.5) * 0.035 * reach;
              float3 colour = field.eval(clamp(samplePoint, 0.0, 1.0) * size * density).rgb;
              colour += (flute - 0.5) * 0.06 * reach;
              float distanceToLight = uv.x - light + (uv.y - 0.5) * 0.36 * size.y / size.x;
              colour += spec * exp(-distanceToLight * distanceToLight / 0.006) * (0.75 + 0.5 * flute * reach);
              float grain = fract(sin(dot(point, float2(12.9898, 78.233))) * 43758.5453);
              colour += (grain - 0.5) * 0.02;
              return half4(half3(colour), 1.0);
            }
        """
    }
}
