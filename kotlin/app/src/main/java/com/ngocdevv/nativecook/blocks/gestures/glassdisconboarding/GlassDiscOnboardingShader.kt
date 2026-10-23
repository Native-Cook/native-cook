package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.min

/** API 33 AGSL refraction, with a clipped magnifier and material rim on API 26-32. */
internal class GlassDiscOnboardingShader {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var runtimeShader: RuntimeShader? = null

    fun draw(
        canvas: Canvas,
        map: Bitmap,
        layout: GlassDiscOnboardingLayout,
        frame: GlassDiscOnboardingFrame,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawAgsl(canvas, map, layout, frame)
        } else {
            drawFallback(canvas, map, layout, frame)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawAgsl(
        canvas: Canvas,
        map: Bitmap,
        layout: GlassDiscOnboardingLayout,
        frame: GlassDiscOnboardingFrame,
    ) {
        val shader = runtimeShader ?: RuntimeShader(Agsl).also { runtimeShader = it }
        val lookY = min(0f, (layout.lensY - frame.centerY) * 0.9f) * frame.rim
        shader.setInputShader("map", BitmapShader(map, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        shader.setFloatUniform("center", frame.centerX, frame.centerY)
        shader.setFloatUniform("radius", frame.radius)
        shader.setFloatUniform("mag", frame.magnification)
        shader.setFloatUniform("rim", frame.rim)
        shader.setFloatUniform("lift", 1f)
        shader.setFloatUniform("look", 0f, lookY)
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, layout.width, layout.height, paint)
        paint.shader = null
    }

    private fun drawFallback(
        canvas: Canvas,
        map: Bitmap,
        layout: GlassDiscOnboardingLayout,
        frame: GlassDiscOnboardingFrame,
    ) {
        val radius = frame.radius.coerceAtLeast(1f)
        val lookY = min(0f, (layout.lensY - frame.centerY) * 0.9f) * frame.rim
        val shadowWidth = 18f * layout.density
        val shadowRadius = radius + shadowWidth
        paint.shader = RadialGradient(
            frame.centerX,
            frame.centerY + 4f * layout.density,
            shadowRadius,
            intArrayOf(0x001D2B22, 0x001D2B22, 0x241D2B22, 0x001D2B22),
            floatArrayOf(0f, (radius / shadowRadius).coerceIn(0f, 0.96f), 0.965f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = (frame.rim * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(frame.centerX, frame.centerY + 4f * layout.density, shadowRadius, paint)
        paint.shader = null

        val clip = Path().apply { addCircle(frame.centerX, frame.centerY, radius, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(clip)
        canvas.translate(frame.centerX, frame.centerY)
        canvas.scale(frame.magnification, frame.magnification)
        canvas.translate(-frame.centerX, -frame.centerY - lookY)
        paint.alpha = 255
        canvas.drawBitmap(map, 0f, 0f, paint)
        canvas.restoreToCount(save)

        if (frame.rim > 0.001f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * layout.density
            paint.shader = null
            paint.color = 0xFFFFFFFF.toInt()
            paint.alpha = (frame.rim * 220f).toInt().coerceIn(0, 255)
            canvas.drawArc(
                RectF(frame.centerX - radius, frame.centerY - radius, frame.centerX + radius, frame.centerY + radius),
                198f,
                116f,
                false,
                paint,
            )
            paint.color = 0xFF1D2B22.toInt()
            paint.alpha = (frame.rim * 35f).toInt().coerceIn(0, 255)
            canvas.drawArc(
                RectF(frame.centerX - radius, frame.centerY - radius, frame.centerX + radius, frame.centerY + radius),
                12f,
                150f,
                false,
                paint,
            )
            paint.color = 0xFFE8642E.toInt()
            paint.alpha = (frame.rim * 28f).toInt().coerceIn(0, 255)
            paint.strokeWidth = 2.2f * layout.density
            canvas.drawArc(
                RectF(frame.centerX - radius + 2f, frame.centerY - radius + 2f, frame.centerX + radius - 2f, frame.centerY + radius - 2f),
                25f,
                90f,
                false,
                paint,
            )
            paint.style = Paint.Style.FILL
            paint.alpha = 255
        }
    }

    companion object {
        private const val Agsl = """
            uniform shader map;
            uniform float2 center;
            uniform float radius;
            uniform float mag;
            uniform float rim;
            uniform float lift;
            uniform float2 look;

            half4 main(float2 p) {
              float2 d = p - center;
              float r = length(d);
              float edge = radius - r;
              float2 u = d / max(r, 0.001);
              if (edge < 0.0) {
                float o = -edge;
                float a = rim * lift * 0.16 * exp(-(o / 9.0) * (o / 9.0)) *
                  (0.35 + 0.65 * smoothstep(-0.5, 1.0, u.y));
                return half4(half3(0.11, 0.17, 0.13) * a, a);
              }
              float t = clamp(edge / 18.0, 0.0, 1.0);
              float band = (1.0 - t) * (1.0 - t) * rim;
              float2 q = center + look + d / mag - u * band * 20.0;
              float spread = band * 2.2;
              float3 col = float3(map.eval(q - u * spread).r, map.eval(q).g, map.eval(q + u * spread).b);
              float hair = (1.0 - smoothstep(0.0, 1.4, edge)) * rim;
              float up = pow(max(dot(u, float2(-0.32, -0.95)), 0.0), 1.4);
              float down = pow(max(dot(u, float2(0.25, 0.97)), 0.0), 2.0);
              col = mix(col, float3(1.0), hair * (0.55 + 0.4 * up));
              col += band * 0.1 * up;
              col *= 1.0 - 0.14 * band * (1.0 - up) * (0.4 + 0.6 * down);
              float cov = smoothstep(-0.6, 0.6, edge);
              return half4(half3(col * cov), half(cov));
            }
        """
    }
}
