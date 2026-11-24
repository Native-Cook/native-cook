#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The water under Floating Letters Stir: one pass over the screen. A port of the React Native
// `floatingLettersShader.ts`.
//
// A pale, shallow pool seen from above: a depth gradient (lighter at the top, a deeper teal toward the bottom and
// the corners), two layers of caustics (the second larger, dimmer and running the other way), and the rings a
// finger leaves. Each ring is a travelling wave packet: a sine of wavelength 34 pt under a Gaussian of width 30 pt
// whose centre runs outward at 150 pt/s. Its slope refracts the caustics and lights the side that faces the light,
// top left. The same ring maths, in FloatingLettersStirMotion.swift, bobs the letters.
//
// Time enters only as `phase` = 2*pi * ambient / LOOP with whole multipliers, so the water at LOOP is the water at 0.
//
// u: size(2) focus(2) phase touch(3: x, y, presence) dpr | rip[8] (x, y, age, strength)

namespace floatingLettersStir {

constant int RIPPLES = 8;
constant int RIP = 9;
constant float TAU = 6.2831853;
constant float SPEED = 150.0;
constant float LIFE = 2.8;
constant float WIDTH = 30.0;
constant float KW = 0.18480;

float3 lin(float3 c) { return pow(c, float3(2.2)); }

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

// Height (x) and slope / wave number (yz) of every ring at p.
float3 rings(float2 p, device const float *u) {
  float3 acc = float3(0.0);
  for (int k = 0; k < RIPPLES; k++) {
    float4 r = float4(u[RIP + k * 4], u[RIP + k * 4 + 1], u[RIP + k * 4 + 2], u[RIP + k * 4 + 3]);
    float2 dv = p - r.xy;
    float d = max(length(dv), 0.001);
    float off = d - SPEED * r.z;
    float env = r.w * smoothstep(0.0, 0.15, r.z) * pow(1.0 - clamp(r.z / LIFE, 0.0, 1.0), 1.5);
    float g = exp(-(off * off) / (WIDTH * WIDTH));
    float sn = sin(off * KW);
    float cs = cos(off * KW);
    float dh = env * g * (cs - (2.0 * off / (WIDTH * WIDTH * KW)) * sn);
    acc += float3(env * g * sn, dh * dv / d);
  }
  return acc;
}

// The caustic net: a few rounds of a domain warp, bright where the warped coordinates bunch up.
// uv tiles every 1.0; turn is the animation angle and takes whole multipliers so it loops.
float caustic(float2 uv, float turn) {
  float2 w = uv * TAU;
  float2 p = (w - TAU * floor(w / TAU)) - 250.0;
  float2 i = p;
  float c = 1.0;
  const float inten = 0.005;
  for (int n = 0; n < 4; n++) {
    float m = n == 0 ? -2.0 : (n == 1 ? -1.0 : (n == 2 ? 1.0 : 2.0));
    float t = turn * m + float(n) * 1.7;
    i = p + float2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
    float2 q = float2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten));
    c += 1.0 / max(length(q), 0.0001);
  }
  c /= 4.0;
  c = 1.17 - pow(max(c, 0.0), 1.4);
  return pow(abs(c), 8.0);
}

// Linear sRGB to the sRGB the SwiftUI layer expects. The React Native build converts to Display P3 first because
// Skia tags its output as P3; measured against its recording, the result on screen is these sRGB numbers.
float3 encode(float3 l) {
  l = clamp(l, 0.0, 1.0);
  float3 lo = l * 12.92;
  float3 hi = 1.055 * pow(l, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), l));
}

} // namespace floatingLettersStir

[[ stitchable ]] half4 floatingLettersStirWater(float2 p, device const float *u, int count) {
  using namespace floatingLettersStir;
  float2 size = float2(u[0], u[1]);
  float2 focus = float2(u[2], u[3]);
  float phase = u[4];
  float3 touch = float3(u[5], u[6], u[7]);
  float dpr = u[8];
  float2 uv = p / size;

  // Depth: three stops down the screen, a lighter pool around the phrase, deeper corners.
  float3 top = lin(float3(0.914, 0.957, 0.945));
  float3 mid = lin(float3(0.772, 0.890, 0.882));
  float3 low = lin(float3(0.604, 0.800, 0.812));
  float3 col = mix(top, mid, smoothstep(0.0, 0.55, uv.y));
  col = mix(col, low, smoothstep(0.45, 1.0, uv.y));
  float2 fd = (p - focus) / size.x;
  col += lin(float3(0.93, 0.98, 0.96)) * 0.10 * exp(-dot(fd, fd) * 3.2);
  float2 vd = (uv - 0.5) * float2(0.72, 1.0);
  col = mix(col, lin(float3(0.42, 0.66, 0.70)), 0.36 * smoothstep(0.30, 0.95, dot(vd, vd) * 2.4));

  // Rings, and the dimple under the finger.
  float3 rg = rings(p, u);
  float2 td = p - touch.xy;
  float dimple = touch.z * exp(-dot(td, td) / (2.0 * 24.0 * 24.0));
  float2 slope = rg.yz + td / 24.0 * dimple * 0.6;

  // Caustics on the floor, seen through the surface: the slope shifts where we look.
  float2 fq = p + slope * 12.0;
  float aspect = 300.0;
  float c1 = caustic(fq / aspect + float2(0.13, 0.31), phase);
  float c2 = caustic(fq / (aspect * 1.9) + float2(0.57, 0.08), -phase);
  float depth = mix(0.75, 1.15, uv.y);
  col += lin(float3(1.0, 1.0, 0.94)) * (c1 * 0.15 + c2 * 0.07) * depth;

  // Light from the top left catches the near side of every ring; the far side falls into shade.
  float2 L = normalize(float2(-0.55, -0.83));
  float facing = dot(slope, -L);
  col += float3(1.0) * pow(max(facing, 0.0), 2.0) * 0.26;
  col *= 1.0 - 0.13 * max(-facing, 0.0);
  col *= 1.0 - 0.05 * dimple;
  col += float3(1.0) * 0.035 * rg.x;

  float3 rgb = encode(col);
  float2 px = p * dpr;
  float n = ign(px) + ign(px + float2(41.0, 17.0)) - 1.0;
  float g = h21(floor(px)) - 0.5;
  rgb += n / 255.0 + g * 0.018;
  return half4(half3(rgb), 1.0h);
}
