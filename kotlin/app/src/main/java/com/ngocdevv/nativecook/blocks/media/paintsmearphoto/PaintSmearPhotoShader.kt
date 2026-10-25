package com.ngocdevv.nativecook.blocks.media.paintsmearphoto

// The photo as wet oil paint, in one pass over the photo card: the React Native SkSL
// (paintSmearShader.ts) without its Display P3 conversions (Android hands the photo to AGSL and
// draws the output in sRGB). Coordinates are card dp: the canvas is scaled by the density and the
// photo's BitmapShader carries the cover-fit matrix.
//
// The smear is a displacement field: each trail point (x, y, drag dx, dy) adds its drag weighted
// by a Gaussian of the brush's radius; a pixel shows the paint from upstream (p − D), averaged
// with more taps along D so the colours pull into streaks. Bristle lines run along the stroke;
// impasto ridges are lit from the upper left where paint was moved. `flow` scales the field.

internal const val PAINT_SMEAR_AGSL = """
uniform shader photo;
uniform float2 size;
uniform float4 trail[24];
uniform float sigma;
uniform float dry;
uniform float flow;
uniform float radius;

float hash1(float x) {
  return fract(sin(x * 12.9898) * 43758.5453);
}

float noise1(float x) {
  float i = floor(x);
  float f = fract(x);
  float u = f * f * (3.0 - 2.0 * f);
  return mix(hash1(i), hash1(i + 1.0), u);
}

float2 field(float2 p) {
  float2 d = float2(0.0);
  float k = -0.5 / (sigma * sigma);
  for (int i = 0; i < 24; i++) {
    float4 t = trail[i];
    if (abs(t.z) + abs(t.w) > 0.01) {
      float2 q = p - t.xy;
      d += t.zw * exp(dot(q, q) * k);
    }
  }
  d *= flow;
  float len = length(d);
  return len > 0.001 ? d / len * (130.0 * len / (130.0 + len)) : float2(0.0);
}

float bristles(float2 p, float2 d) {
  float len = length(d);
  if (len < 0.01) return 0.5;
  float2 across = float2(-d.y, d.x) / len;
  float x = dot(p, across);
  return noise1(x * 0.16) * 0.65 + noise1(x * 0.47 + 11.0) * 0.35;
}

float3 paint(float2 q) {
  return float3(photo.eval(clamp(q, float2(0.5), size - 0.5)).rgb);
}

float sdRoundRect(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 p) {
  float sd = sdRoundRect(p - size * 0.5, size * 0.5, radius);
  float alpha = clamp(0.5 - sd, 0.0, 1.0);
  if (alpha <= 0.0) return half4(0.0);

  float2 d = field(p);
  float len = length(d);
  float3 col;
  if (len < 0.05) {
    col = paint(p);
  } else {
    float b = bristles(p, d);
    float streak = mix(0.25, 0.7, dry);
    float2 dd = d * mix(1.0, 0.72, dry) * (1.0 - streak * 0.5 + streak * b);
    col = paint(p - dd) * 0.4 + paint(p - dd * 0.6) * 0.25 + paint(p - dd * 1.3) * 0.2 + paint(p - dd * 0.25) * 0.15;
    float2 dx = field(p + float2(1.5, 0.0));
    float2 dy = field(p + float2(0.0, 1.5));
    float h0 = len * (0.75 + 0.35 * b);
    float hx = length(dx) * (0.75 + 0.35 * bristles(p + float2(1.5, 0.0), dx));
    float hy = length(dy) * (0.75 + 0.35 * bristles(p + float2(0.0, 1.5), dy));
    float3 n = normalize(float3(-(hx - h0) / 1.5 * 0.35, -(hy - h0) / 1.5 * 0.35, 1.0));
    float3 L = normalize(float3(-0.5, -0.7, 0.6));
    float m = smoothstep(0.5, 8.0, len);
    col *= 1.0 + (dot(n, L) - L.z) * 0.8 * m;
    float3 hv = normalize(L + float3(0.0, 0.0, 1.0));
    col += float3(1.0, 0.97, 0.9) * pow(max(dot(n, hv), 0.0), 36.0) * 0.22 * m * (1.0 - dry * 0.6);
  }

  float rim = smoothstep(1.6, 0.0, abs(sd + 1.0));
  float vy = p.y / size.y;
  col += float3(1.0) * rim * 0.12 * (1.0 - vy);
  col *= 1.0 - rim * 0.25 * vy;

  col = clamp(col, 0.0, 1.0);
  return half4(half3(col) * half(alpha), half(alpha));
}
"""
