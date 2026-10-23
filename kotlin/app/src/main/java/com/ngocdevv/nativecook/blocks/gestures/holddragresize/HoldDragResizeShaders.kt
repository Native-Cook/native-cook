package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.max
import kotlin.math.min

internal class HoldDragResizeGlass(private val backdrop: HoldDragResizeBackdrop) {
    private var cursor = 0
    private val runtime: Array<Any?> = Array(20) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(GlassAgsl) else null
    }
    private val backgroundShader: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        BitmapShader(backdrop.frosted, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
    } else {
        null
    }

    fun beginFrame() {
        cursor = 0
    }

    fun draw(
        canvas: Canvas,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        radius: Double,
        tint: FloatArray,
        thick: Double = 0.0,
        glow: Double = 0.0,
        glowX: Double = 0.0,
        glowY: Double = 0.0,
        alpha: Double = 1.0,
    ) {
        if (alpha <= 0.004 || width <= 0 || height <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cursor < runtime.size) {
            drawRuntime(canvas, runtime[cursor++] as RuntimeShader, x, y, width, height, radius, tint, thick, glow, glowX, glowY, alpha)
        } else {
            drawFallback(canvas, x, y, width, height, radius, tint, glow, glowX, glowY, alpha)
        }
    }

    private fun drawFallback(
        canvas: Canvas,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        radius: Double,
        tint: FloatArray,
        glow: Double,
        glowX: Double,
        glowY: Double,
        alpha: Double,
    ) {
        val rect = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
        val save = canvas.save()
        canvas.holdDragResizeClipRoundRect(rect, radius.toFloat())
        canvas.drawBitmap(
            backdrop.frosted,
            null,
            RectF(0f, 0f, backdrop.width, backdrop.height),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (alpha * 215).toInt().coerceIn(0, 255) },
        )
        val tintAlpha = (255 * alpha * (tint[3] * 0.85f + 0.05f)).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(
            rect,
            radius.toFloat(),
            radius.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(tintAlpha, (tint[0] * 255).toInt(), (tint[1] * 255).toInt(), (tint[2] * 255).toInt())
            },
        )
        if (glow > 0.004) {
            canvas.drawRect(
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        glowX.toFloat(),
                        glowY.toFloat(),
                        92f,
                        intArrayOf(Color.argb((glow * alpha * 42).toInt().coerceIn(0, 255), 255, 255, 255), 0x00FFFFFF),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }
        canvas.restoreToCount(save)
        canvas.drawRoundRect(
            rect,
            radius.toFloat(),
            radius.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = Color.argb((alpha * 82).toInt().coerceIn(0, 255), 255, 255, 255)
                shader = LinearGradient(0f, y.toFloat(), 0f, (y + height).toFloat(), 0x70FFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP)
            },
        )
    }

    private fun drawRuntime(
        canvas: Canvas,
        shader: RuntimeShader,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        radius: Double,
        tint: FloatArray,
        thick: Double,
        glow: Double,
        glowX: Double,
        glowY: Double,
        alpha: Double,
    ) {
        shader.setInputShader("bg", requireNotNull(backgroundShader))
        shader.setFloatUniform("uRect", x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
        shader.setFloatUniform("uRadius", radius.toFloat())
        shader.setFloatUniform("uTint", tint)
        shader.setFloatUniform("uThick", thick.toFloat())
        shader.setFloatUniform("uGlow", glowX.toFloat(), glowY.toFloat(), glow.toFloat())
        shader.setFloatUniform("uAlpha", alpha.toFloat())
        shader.setFloatUniform("uBgScale", backdrop.frostScale)
        canvas.drawRect(
            (x - 1).toFloat(),
            (y - 1).toFloat(),
            (x + width + 1).toFloat(),
            (y + height + 1).toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
        )
    }
}

internal class HoldDragResizeShadow {
    private var cursor = 0
    private val runtime: Array<Any?> = Array(18) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(ShadowAgsl) else null
    }

    fun beginFrame() {
        cursor = 0
    }

    fun draw(canvas: Canvas, rect: RectF, elevation: Double, opacity: Double, radius: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cursor < runtime.size) {
            val shader = runtime[cursor++] as RuntimeShader
            shader.setFloatUniform("uRect", rect.left, rect.top, rect.width(), rect.height())
            shader.setFloatUniform("uParam", elevation.toFloat(), opacity.toFloat(), radius.toFloat(), 0f)
            shader.setFloatUniform("uShade", 0f, 0.01f, 0.03f)
            val reach = 3 * (0.5 + elevation) + 0.4 + 0.75 * elevation + 2
            canvas.drawRect(
                (rect.left - reach).toFloat(),
                (rect.top - reach).toFloat(),
                (rect.right + reach).toFloat(),
                (rect.bottom + reach).toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
            )
            return
        }
        val ambientBlur = (0.6 + 0.35 * elevation).toFloat()
        val keyBlur = (0.5 + elevation).toFloat()
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + (0.3 + 0.15 * elevation).toFloat(), rect.right, rect.bottom + (0.3 + 0.15 * elevation).toFloat()),
            radius.toFloat(),
            radius.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((255 * 0.075 * opacity).toInt().coerceIn(0, 255), 0, 3, 8)
                maskFilter = BlurMaskFilter(ambientBlur, BlurMaskFilter.Blur.NORMAL)
            },
        )
        val insetX = (0.35 * elevation).toFloat()
        val insetY = (0.2 * elevation).toFloat()
        val key = RectF(
            rect.left + insetX,
            rect.top + insetY + (0.4 + 0.75 * elevation).toFloat(),
            rect.right - insetX,
            rect.bottom - insetY + (0.4 + 0.75 * elevation).toFloat(),
        )
        canvas.drawRoundRect(
            key,
            min(radius, min(key.width(), key.height()).toDouble() / 2).toFloat(),
            min(radius, min(key.width(), key.height()).toDouble() / 2).toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((255 * min(0.06 + 0.016 * elevation, 0.24) * opacity).toInt().coerceIn(0, 255), 0, 3, 8)
                maskFilter = BlurMaskFilter(keyBlur, BlurMaskFilter.Blur.NORMAL)
            },
        )
    }
}

internal fun Canvas.holdDragResizeClipRoundRect(rect: RectF, radius: Float) {
    clipPath(
        Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        },
    )
}

private const val GlassAgsl = """
uniform shader bg;
uniform float4 uRect;
uniform float uRadius;
uniform float4 uTint;
uniform float uThick;
uniform float3 uGlow;
uniform float uAlpha;
uniform float uBgScale;

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 xy) {
  float2 b = uRect.zw * 0.5;
  float2 p = xy - (uRect.xy + b);
  float r = min(uRadius, min(b.x, b.y));
  float d = sdBox(p, b, r);
  float cov = 1.0 - smoothstep(-0.5, 0.5, d);
  if (cov <= 0.0) return half4(0.0);
  float e = 0.5;
  float2 n = float2(
    sdBox(p + float2(e, 0.0), b, r) - sdBox(p - float2(e, 0.0), b, r),
    sdBox(p + float2(0.0, e), b, r) - sdBox(p - float2(0.0, e), b, r)
  );
  n = n / max(length(n), 0.0001);
  float bevel = min(12.0, min(b.x, b.y));
  float t = clamp(-d / bevel, 0.0, 1.0);
  float lens = (1.0 - t) * (1.0 - t);
  float bend = 7.0 + 7.0 * uThick;
  float2 s = xy - n * lens * bend - p * 0.025;
  float spread = lens * (1.2 + uThick);
  float3 col = float3(
    bg.eval((s - n * spread) * uBgScale).r,
    bg.eval(s * uBgScale).g,
    bg.eval((s + n * spread) * uBgScale).b
  );
  col = col * 0.8 + 0.03;
  col = mix(col, uTint.rgb, uTint.a * 0.55) + uTint.rgb * uTint.a * 0.32;
  float rim = 1.0 - smoothstep(0.0, 1.2, -d);
  float band = (1.0 - smoothstep(0.0, 7.0, -d)) * lens;
  float up = pow(max(dot(n, float2(-0.34, -0.94)), 0.0), 1.4);
  float down = pow(max(dot(n, float2(0.3, 0.95)), 0.0), 2.0);
  float lit = 0.12 + 0.6 * up + 0.24 * down;
  col += rim * lit * (0.9 + 0.3 * uThick);
  col += band * lit * 0.16 * (1.0 + uThick);
  col += mix(uTint.rgb, float3(1.0), 0.5) * rim * uTint.a * 0.6;
  col += (1.0 - smoothstep(-b.y, -b.y + min(28.0, b.y), p.y)) * 0.03;
  col *= 1.0 - 0.1 * smoothstep(1.6, 5.0, -d) * (1.0 - smoothstep(5.0, 10.0, -d));
  float2 finger = xy - uGlow.xy;
  col += uGlow.z * 0.16 * exp(-dot(finger, finger) / (2.0 * 46.0 * 46.0));
  float alpha = cov * uAlpha;
  return half4(half3(col * alpha), half(alpha));
}
"""

private const val ShadowAgsl = """
uniform float4 uRect;
uniform float4 uParam;
uniform float3 uShade;

float2 erf2(float2 x) {
  float2 s = sign(x);
  float2 a = abs(x);
  float2 r = 1.0 + (0.278393 + (0.230389 + 0.078108 * (a * a)) * a) * a;
  r *= r;
  return s - s / (r * r);
}

float gauss(float x, float sigma) {
  return exp(-(x * x) / (2.0 * sigma * sigma)) / (2.50662827 * sigma);
}

float boxShadow(float2 p, float2 halfSize, float radius, float sigma) {
  float low = clamp(-3.0 * sigma, p.y - halfSize.y, p.y + halfSize.y);
  float high = clamp(3.0 * sigma, p.y - halfSize.y, p.y + halfSize.y);
  float stepY = (high - low) / 4.0;
  float y = low + stepY * 0.5;
  float value = 0.0;
  for (int i = 0; i < 4; i++) {
    float py = p.y - y;
    float d = min(halfSize.y - radius - abs(py), 0.0);
    float curved = halfSize.x - radius + sqrt(max(0.0, radius * radius - d * d));
    float2 span = 0.5 + 0.5 * erf2((p.x + float2(-curved, curved)) * (0.70710678 / sigma));
    value += (span.y - span.x) * gauss(y, sigma) * stepY;
    y += stepY;
  }
  return value;
}

half4 main(float2 xy) {
  float elevation = uParam.x;
  float opacity = uParam.y;
  float2 halfSize = uRect.zw * 0.5;
  float2 p = xy - (uRect.xy + halfSize);
  float radius = min(uParam.z, min(halfSize.x, halfSize.y));
  float ambientSigma = 0.6 + 0.35 * elevation;
  float ambient = 0.075 * boxShadow(p - float2(0.0, 0.3 + 0.15 * elevation), halfSize, radius, ambientSigma);
  float keySigma = 0.5 + elevation;
  float keyY = 0.4 + 0.75 * elevation;
  float2 keyHalf = max(halfSize - float2(0.35 * elevation, 0.2 * elevation), float2(1.0));
  float key = min(0.06 + 0.016 * elevation, 0.24) * boxShadow(
    p - float2(0.0, keyY), keyHalf, min(radius, min(keyHalf.x, keyHalf.y)), keySigma
  );
  float alpha = 1.0 - (1.0 - ambient * opacity) * (1.0 - key * opacity);
  return half4(half3(uShade) * alpha, alpha);
}
"""
