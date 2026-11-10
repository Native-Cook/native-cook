package com.ngocdevv.nativecook.blocks.cards.smokedclochereveal

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** Transparent fullscreen translation of scr-wgsl.ts clocheFs, with the actual bowl as its input. */
@RequiresApi(33)
internal class SmokedClocheRevealShader(dish: Bitmap) {
    private val image = BitmapShader(dish, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val width = dish.width.toFloat()
    private val height = dish.height.toFloat()
    private val effect = RuntimeShader(SOURCE)

    fun update(l: SmokedClocheRevealLayout, f: SmokedClocheRevealMotion.Frame): Shader {
        matrix.setScale(l.half * 2 / width, l.half * 2 / height)
        matrix.postTranslate(l.cx - l.half, l.cy - l.half)
        image.setLocalMatrix(matrix)
        effect.setInputShader("uDish", image)
        effect.setFloatUniform("uBowl", l.cx, l.cy, l.half, l.radius)
        effect.setFloatUniform("uLift", f.lift.toFloat())
        return effect
    }

    private companion object {
        val SOURCE = """
uniform shader uDish;
uniform float4 uBowl;
uniform float uLift;
const float TAU = 6.2831853;
float sq(float v) { return v * v; }
float3 lin(float3 c) { return pow(c, float3(2.2)); }

float4 dishAt(float2 s) {
    float2 uv = (s - uBowl.xy) / (2.0 * uBowl.z) + 0.5;
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) return float4(0.0);
    float4 seen = uDish.eval(s);
    // Android shader inputs are premultiplied; WGSL samples the unpremultiplied PNG.
    if (seen.a > 0.0) seen.rgb /= seen.a;
    return seen;
}

float3 studio(float3 r) {
    float3 c = mix(lin(float3(0.55, 0.52, 0.5)), lin(float3(0.95, 0.94, 0.93)), smoothstep(-0.4, 0.8, r.y));
    float3 k = normalize(float3(-0.5, 0.62, 0.6));
    float3 u = normalize(cross(k, float3(0.0, 0.0, 1.0)));
    float3 w = cross(u, k);
    float2 a = float2(dot(r, u), dot(r, w)) / max(dot(r, k), 0.05);
    c += float3(1.0, 0.99, 0.97) * 3.0 * (1.0 - smoothstep(0.16, 0.22, abs(a.x))) * (1.0 - smoothstep(0.3, 0.36, abs(a.y))) * step(0.0, dot(r, k)) * smoothstep(0.012, 0.03, abs(a.x));
    float3 f = normalize(float3(0.7, -0.35, 0.6));
    c += float3(1.0, 0.92, 0.82) * 0.7 * smoothstep(0.9, 0.98, dot(r, f));
    return c;
}

float3 encode(float3 c) {
    return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2));
}

half4 main(float2 s) {
    float lift = uLift;
    float fade = 1.0 - smoothstep(0.5, 0.98, lift);
    if (fade <= 0.0) return half4(0.0);
    float scale = 1.0 + 0.35 * lift * lift;
    float R = uBowl.w * scale;
    float2 c = uBowl.xy + float2(0.0, -18.0 * lift);
    float2 q = (s - c) / R;
    float r = length(q);
    if (r > 1.02) return half4(0.0);
    float h = sqrt(max(1.0 - r * r, 0.0));
    float3 n = normalize(float3(q.x, -q.y, h + 0.0001));
    float mag = 1.0 - 0.08 * h - 0.06 * (1.0 - h);
    float2 src = uBowl.xy + (s - uBowl.xy) * mag - q * 10.0 * sq(1.0 - h);
    float blur = 0.6 + 6.0 * lift;
    float rot = fract(sin(dot(s, float2(12.9898, 78.233))) * 43758.5453) * TAU;
    float4 seen = float4(0.0);
    for (int k = 0; k < 8; k++) {
        float a = rot + float(k) * TAU / 8.0;
        seen += dishAt(src + float2(cos(a), sin(a)) * blur * (0.5 + 0.5 * float(k - (k / 2) * 2)));
    }
    seen *= 0.125;
    float4 table = float4(lin(float3(0.86, 0.83, 0.8)), 1.0);
    float4 under = float4(lin(seen.rgb), seen.a) * seen.a + table * (1.0 - seen.a);
    float fr = 0.04 + 0.96 * pow(1.0 - h, 5.0);
    float3 refl = studio(reflect(float3(0.0, 0.0, -1.0), n));
    float3 col = under.rgb * (0.94 + 0.06 * h) * (1.0 - fr) + refl * fr;
    float rim = smoothstep(0.9, 0.97, r) * (1.0 - smoothstep(0.985, 1.02, r));
    col = mix(col, col * 0.82 + refl * 0.25, rim);
    col += float3(1.0) * 0.35 * exp(-sq((r - 0.965) / 0.008));
    float2 kq = (s - c) / (0.11 * R);
    float kr = length(kq);
    if (kr < 1.0) {
        float kh = sqrt(1.0 - kr * kr);
        float3 kn = normalize(float3(kq.x, -kq.y, kh));
        float kf = 0.04 + 0.96 * pow(1.0 - kh, 5.0);
        float3 kref = studio(reflect(float3(0.0, 0.0, -1.0), kn));
        float3 kunder = dishAt(c + (s - c) * -0.6).rgb;
        col = mix(col, lin(kunder) * 0.85 * (1.0 - kf) + kref * kf + float3(1.0) * pow(max(dot(reflect(-normalize(float3(-0.5, 0.62, 0.6)), kn), float3(0.0, 0.0, 1.0)), 0.0), 60.0), 1.0 - smoothstep(0.92, 1.0, kr));
    } else {
        col *= 1.0 - 0.18 * exp(-sq((kr - 1.0) / 0.5)) * smoothstep(-0.2, 0.6, dot(kq, float2(0.6, 0.8)));
    }
    float a = (1.0 - smoothstep(0.995, 1.02, r)) * fade;
    return half4(half3(encode(col) * a), half(a));
}
""".trimIndent()
    }
}
