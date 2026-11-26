package com.ngocdevv.nativecook.blocks.splash.paintpoursplash

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** Raw encoded-sRGB image input, encoded-sRGB window output. No P3 matrix or implicit input conversion. */
@RequiresApi(33)
internal class PaintPourSplashFlow(bitmap: Bitmap) {
    private val effect = RuntimeShader(SOURCE)
    init {
        val input = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        effect.setInputBuffer("image", input)
        effect.setFloatUniform("img", bitmap.width.toFloat(), bitmap.height.toFloat())
    }
    fun resize(w: Float, h: Float) {
        val fitW = h * 708f / 1536f
        effect.setFloatUniform("fit", (w - fitW) / 2, 0f, fitW, h)
    }
    fun at(time: Float): Shader { effect.setFloatUniform("time", time); return effect }

    private companion object {
        const val SOURCE = """
uniform shader image;
uniform float4 fit;
uniform float2 img;
uniform float time;
float3 tex(float2 uv) {
    float2 q = clamp(clamp(uv, 0.0, 1.0) * img, float2(0.5), img - 0.5);
    return pow(max(image.eval(q).rgb, 0.0), float3(2.2));
}
float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(float2 p) {
    float2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1, 0)), f.x),
               mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), f.x), f.y);
}
half4 main(float2 pos) {
    float2 uv = (pos - fit.xy) / fit.zw;
    float3 base = tex(uv);
    float sat = max(max(base.r, base.g), base.b) - min(min(base.r, base.g), base.b);
    float stream = smoothstep(0.12, 0.3, sat) * (1 - smoothstep(0.78, 0.83, uv.y)) * smoothstep(0.12, 0.2, uv.y);
    float pool = smoothstep(0.12, 0.3, sat) * smoothstep(0.78, 0.84, uv.y);
    float ph0 = fract(time / 2.0), ph1 = fract(time / 2.0 + 0.5);
    float w0 = 1 - abs(2 * ph0 - 1);
    float2 nuv = uv * float2(18, 6);
    float2 n0 = float2(noise(nuv + ph0 * 3) - 0.5, noise(nuv + 7 - ph0 * 3) - 0.5) * 0.004;
    float2 n1 = float2(noise(nuv + ph1 * 3 + 3.1) - 0.5, noise(nuv + 11 - ph1 * 3) - 0.5) * 0.004;
    float3 a = tex(uv + (float2(0.08, -1) * ph0 * 0.045 + n0) * stream);
    float3 b = tex(uv + (float2(0.08, -1) * ph1 * 0.045 + n1) * stream);
    float3 col = mix(b, a, w0);
    float2 d = uv - float2(0.52, 0.92);
    float r = length(d * float2(1, 3));
    float2 rip = normalize(d + 1e-4) * sin(r * 80 - 6.2832 * time) * 0.0025 * pool;
    col = mix(col, tex(uv + rip), pool);
    return half4(pow(clamp(col, 0.0, 1.0), float3(1.0 / 2.2)), 1);
}
"""
    }
}
