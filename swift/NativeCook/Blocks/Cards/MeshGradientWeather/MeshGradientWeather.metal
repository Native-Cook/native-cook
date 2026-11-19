#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The sky behind Soft Weather: a 4 x 4 Catmull-Rom mesh gradient in OKLab, and rain on the glass (drizzle
// streaks, beads, sliding drops and their trails, each drop refracting the mesh through a lens), in one pass.
// A port of the React Native `weatherShader.ts`.
//
// u: size(2) rain phase dpr | off[16] (x, y) | lab[16] (L, a, b)
// The SwiftUI layer is non-linear sRGB; see encode() for the P3 step.

namespace meshGradientWeather {

constant int N = 4;
constant float G = 3.0;
constant int OFF = 5;
constant int LAB = OFF + 32;

float cr(float x) {
  x = abs(x);
  if (x < 1.0) return (1.5 * x - 2.5) * x * x + 1.0;
  if (x < 2.0) return ((-0.5 * x + 2.5) * x - 4.0) * x + 2.0;
  return 0.0;
}

float wt(float g, int i) {
  float w = cr(g - float(i));
  if (i == 0) w += cr(g + 1.0);
  if (i == N - 1) w += cr(g - float(N));
  return w;
}

float2 disp(float2 uv, device const float *u) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[4];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float2 acc = float2(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) {
      int k = OFF + (j * N + i) * 2;
      acc += (wx[i] * wy) * float2(u[k], u[k + 1]);
    }
  }
  return acc;
}

float3 meshLab(float2 uv, device const float *u) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[4];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float3 acc = float3(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) {
      int k = LAB + (j * N + i) * 3;
      acc += (wx[i] * wy) * float3(u[k], u[k + 1], u[k + 2]);
    }
  }
  return acc;
}

float3 meshAt(float2 q, device const float *u) {
  float2 uv = q - disp(q, u);
  uv = q - disp(uv, u);
  return meshLab(uv, u);
}

float3 labToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return float3(
     4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

float3 encode(float3 lin) {
  lin = clamp(lin, 0.0, 1.0);
  // The React Native build writes Display P3 numbers that end up shown as sRGB; the reference video was
  // measured to match this exactly, so the same matrix is applied here.
  lin = float3(
    dot(lin, float3(0.8225, 0.1774, 0.0)),
    dot(lin, float3(0.0332, 0.9669, 0.0)),
    dot(lin, float3(0.0171, 0.0724, 0.9108)));
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

constant float TAU = 6.2831853;

float4 lensDrop(float2 p, float2 c, float r, float sy, float pres) {
  float2 d = (p - c) / float2(r, r * sy);
  return float4(d, r, pres);
}

} // namespace meshGradientWeather

[[ stitchable ]] half4 meshGradientWeatherSky(float2 p, device const float *u, int count) {
  using namespace meshGradientWeather;
  float2 size = float2(u[0], u[1]);
  float rain = u[2];
  float phase = u[3];
  float dpr = u[4];
  float2 q = p / size;
  float3 lin = labToLinear(meshAt(q, u));

  if (rain > 0.002) {
    // drizzle behind the glass: thin streaks falling a little slanted, in loops of the clock
    float slant = p.x + p.y * 0.16;
    float col = floor(slant / 6.0);
    float hc = h21(float2(col, 3.7));
    float speed = 3.0 + floor(hc * 3.0);
    float y = p.y / size.y - phase * speed + hc * 7.0;
    float sg = fract(y * 5.0);
    float streak = smoothstep(0.0, 0.04, sg) * (1.0 - smoothstep(0.08, 0.30, sg));
    float across = 1.0 - abs(fract(slant / 6.0) - 0.5) * 2.0;
    streak *= smoothstep(0.55, 1.0, across) * step(0.45, hc);
    lin += float3(streak * 0.055 * rain);

    // drops on the glass: beads that come and go, a few that slide down, and the trail they leave
    float2 cell = float2(30.0, 30.0);
    float2 ci = floor(p / cell);
    float hb = h21(ci);
    float hb2 = h21(ci + 17.3);
    float life = fract(phase * 2.0 + hb * 5.0);
    float vis = smoothstep(0.0, 0.1, life) * (1.0 - smoothstep(0.75, 1.0, life));
    float2 cb = (ci + 0.25 + 0.5 * float2(hb, fract(hb2 * 7.0))) * cell;
    float4 best = lensDrop(p, cb, 2.2 + 3.0 * fract(hb * 13.0), 1.0, vis * step(hb2, 0.6 * rain));

    float colW = 52.0;
    float cx = floor(p.x / colW);
    float hs = h21(float2(cx, 9.1));
    float turns = 1.0 + floor(hs * 2.0);
    float fy = fract(phase * turns + hs * 3.0);
    float sy = -60.0 + fy * (size.y + 120.0);
    float slide = step(0.3, hs) * smoothstep(0.25, 0.6, rain);
    float sx = (cx + 0.3 + 0.4 * hs) * colW + sin(sy * 0.03 + hs * TAU) * 5.0;
    float4 sd = lensDrop(p, float2(sx, sy), 5.0 + 3.0 * fract(hs * 11.0), 1.18, slide);
    if (sd.w > 0.0 && length(sd.xy) < length(best.xy) + 0.6) best = sd;

    float ty = floor(p.y / 13.0);
    float ht = h21(float2(cx, ty));
    float tcy = (ty + 0.5) * 13.0;
    float behind = sy - tcy;
    float tx = (cx + 0.3 + 0.4 * hs) * colW + sin(tcy * 0.03 + hs * TAU) * 5.0 + (ht - 0.5) * 4.0;
    float tr = (1.0 + 1.6 * ht) * (1.0 - clamp(behind / 190.0, 0.0, 1.0));
    float tpres = slide * step(ht, 0.6) * step(12.0, behind) * step(behind, 190.0);
    float4 td = lensDrop(p, float2(tx, tcy), max(tr, 0.4), 1.0, tpres);
    if (td.w > 0.0 && length(td.xy) < length(best.xy)) best = td;

    float len = length(best.xy);
    float r = best.z;
    float cover = (1.0 - smoothstep(1.0 - 1.2 / r, 1.0, len)) * best.w;
    float2 dsh = best.xy - float2(0.0, 0.32);
    float shade = (1.0 - smoothstep(0.9, 1.35, length(dsh))) * best.w * (1.0 - cover);
    lin *= 1.0 - 0.10 * shade;
    if (cover > 0.001) {
      float2 rq = (p - best.xy * r * 7.0) / size;
      float3 seen = labToLinear(meshAt(rq, u));
      float rim = smoothstep(0.62, 1.0, len);
      float top = clamp(-best.y, 0.0, 1.0);
      float low = clamp(best.y, 0.0, 1.0);
      float3 glass = seen * (1.1 - 0.42 * rim * top) + float3(0.10 * smoothstep(0.35, 0.95, len) * low);
      float2 hl = best.xy - float2(-0.34, -0.44);
      glass += float3(0.75 * exp(-dot(hl, hl) * 38.0));
      lin = mix(lin, glass, cover);
    }
  }

  float3 rgb = encode(lin);
  float2 px = p * dpr;
  float n = ign(px) + ign(px + float2(41.0, 17.0)) - 1.0;
  float g = h21(floor(px)) - 0.5;
  rgb += n / 255.0 + g * 0.022;
  return half4(half3(rgb), 1.0);
}
