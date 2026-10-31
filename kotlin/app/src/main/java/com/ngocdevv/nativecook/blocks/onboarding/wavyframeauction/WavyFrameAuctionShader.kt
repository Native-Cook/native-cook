package com.ngocdevv.nativecook.blocks.onboarding.wavyframeauction

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class WavyFrameAuctionShader {
    private val art = RuntimeShader(SOURCE)
    fun at(x: Float, y: Float, w: Float, h: Float, ph: Float, flow: Float): RuntimeShader {
        art.setFloatUniform("o", x, y)
        art.setFloatUniform("sz", w, h)
        art.setFloatUniform("ph", ph)
        art.setFloatUniform("flow", flow)
        return art
    }
    private companion object {
        const val SOURCE = """
uniform float2 o;
uniform float2 sz;
uniform float ph;
uniform float flow;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { s += a * vnoise(p); p = p * 2.02 + 3.1; a *= 0.5; } return s; }
half4 main(float2 xy) {
  float2 uv = (xy - o) / sz.y * 2.4;
  float2 drift = float2(cos(ph), sin(ph)) * (0.5 + 0.6 * flow);
  float2 q = float2(fbm(uv + drift), fbm(uv + float2(5.2, 1.3) - drift));
  float2 r = float2(fbm(uv + 3.0 * q + float2(1.7, 9.2) + drift * 0.6), fbm(uv + 3.0 * q + float2(8.3, 2.8)));
  float f = fbm(uv + 3.5 * r);
  float3 deep = float3(0.13, 0.145, 0.12);
  float3 violet = float3(0.47, 0.38, 0.97);
  float3 lime = float3(0.78, 0.96, 0.38);
  float3 pink = float3(1.0, 0.44, 0.71);
  float3 amber = float3(1.0, 0.7, 0.3);
  float3 col = mix(deep, violet, smoothstep(0.2, 0.6, f));
  col = mix(col, pink, smoothstep(0.45, 0.8, length(q)) * 0.75);
  col = mix(col, lime, smoothstep(0.62, 0.9, r.x) * 0.85);
  col = mix(col, amber, smoothstep(0.7, 0.95, f * r.y * 1.6) * 0.6);
  // flowing contour lines
  col += 0.08 * smoothstep(0.45, 0.5, fract(f * 9.0)) * (1.0 - smoothstep(0.5, 0.55, fract(f * 9.0)));
  col += (hash(floor(xy)) - 0.5) * 0.06;
  return half4(half3(col), 1.0);
}"""
    }
}
