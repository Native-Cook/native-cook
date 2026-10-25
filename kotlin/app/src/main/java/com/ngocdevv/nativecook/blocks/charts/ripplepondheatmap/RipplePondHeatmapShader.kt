package com.ngocdevv.nativecook.blocks.charts.ripplepondheatmap

// Ripple Pond Heatmap in one full-screen AGSL pass (API 33+): the pond floor (sand, pebbles,
// drifting caustics) seen through a surface carrying up to eight ripples, and the lily pads
// (`pads`, baked once by RipplePondHeatmapArt.kt and set as a bitmap shader in dp coordinates)
// lifted and tilted by the water under each one. The React Native SkSL (rippleShader.ts) with the
// maths unchanged; the Display P3 conversion is dropped. Coordinates are dp: the canvas is scaled
// by the density before the shader is drawn.

internal const val RIPPLE_POND_AGSL = """
uniform shader pads;
uniform float2 size;
uniform float4 grid;    // left, top, cell width, row height
uniform float4 s0;      // ripple: x, y, age (s), amplitude
uniform float4 s1;
uniform float4 s2;
uniform float4 s3;
uniform float4 s4;
uniform float4 s5;
uniform float4 s6;
uniform float4 s7;
uniform float4 sel;     // selected pad x, y, glow 0..1, 0
uniform float phase;    // loop clock 0..1

const float TAU = 6.2831853;
const float SPEED = 140.0;
const float K = 0.25;      // 2π / 25 pt

float hsh(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hsh(i), hsh(i + float2(1.0, 0.0)), u.x), mix(hsh(i + float2(0.0, 1.0)), hsh(i + float2(1.0, 1.0)), u.x), u.y);
}

// One source's height (pt) and slope at p: (h, dh/dx, dh/dy).
float3 wave(float2 p, float4 s) {
  if (s.w <= 0.001 || s.z < 0.0 || s.z > 6.0) return float3(0.0);
  float2 d = p - s.xy;
  float r = length(d) + 0.001;
  float front = SPEED * s.z;
  float width = 22.0 + 26.0 * s.z;
  float x = r - front;
  if (abs(x) > width * 3.0) return float3(0.0);
  float env = exp(-(x * x) / (width * width));
  float fade = exp(-s.z / 1.3) * s.w * 7.0 / sqrt(1.0 + r / 30.0);
  // The packet trails behind its front: the crests sit inside the ring, not ahead of it.
  float ph = K * x + 1.2;
  float h = fade * env * sin(ph);
  float dhdr = fade * env * (K * cos(ph) - 2.0 * x / (width * width) * sin(ph));
  return float3(h, d / r * dhdr);
}

float3 waves(float2 p) {
  return wave(p, s0) + wave(p, s1) + wave(p, s2) + wave(p, s3) + wave(p, s4) + wave(p, s5) + wave(p, s6) + wave(p, s7);
}

// The pond floor: dark teal deepening downward, sand and pebbles, slow caustics.
float3 floorAt(float2 p, float focus) {
  float2 uv = p / size;
  float3 col = mix(float3(0.10, 0.27, 0.28), float3(0.035, 0.12, 0.15), smoothstep(0.0, 1.0, uv.y));
  col = mix(col, float3(0.05, 0.16, 0.19), smoothstep(0.3, 0.9, uv.x) * 0.4);
  float sand = vnoise(p / 5.0) * 0.5 + vnoise(p / 23.0) * 0.5;
  col *= 0.86 + 0.24 * sand;
  float2 cell = floor(p / 38.0);
  float2 pc = (cell + 0.3 + 0.4 * float2(hsh(cell), hsh(cell + 4.0))) * 38.0;
  float pr = 4.0 + 8.0 * hsh(cell + 9.0);
  float pd = length(p - pc);
  if (hsh(cell + 2.0) > 0.72) {
    float pebble = 1.0 - smoothstep(pr - 2.5, pr + 1.0, pd);
    col = mix(col, col * (1.12 + 0.2 * hsh(cell + 6.0)), pebble * 0.5);
  }
  // Caustics: two drifting interference nets that loop with the clock.
  float a = phase * TAU;
  float2 q = p / 46.0;
  float n1 = vnoise(q + float2(cos(a), sin(a)) * 0.8);
  float n2 = vnoise(q * 1.3 + 5.0 + float2(sin(a), -cos(a)) * 0.8);
  float net = abs(sin((q.x + n1 * 2.2) * 3.1)) * abs(sin((q.y + n2 * 2.2) * 2.7));
  float caustic = pow(1.0 - net, 12.0);
  col += float3(0.30, 0.45, 0.38) * caustic * (0.12 + 0.7 * max(focus, 0.0));
  return col;
}

half4 main(float2 pos) {
  float2 p = pos;
  float3 w = waves(p);
  float2 slope = w.yz;
  // Refraction: the floor is seen through the tilted surface.
  float3 col = floorAt(p - slope * 34.0, w.x * 0.18 - (slope.x + slope.y) * 0.4);
  // The water: a little sky caught at a slant, brighter toward the top of the screen.
  float2 uv = p / size;
  float3 skyC = float3(0.62, 0.78, 0.80);
  float fres = 0.05 + 0.18 * smoothstep(1.0, 0.0, uv.y) + clamp(length(slope) * 1.2, 0.0, 0.35);
  col = mix(col, skyC, fres * 0.55);
  float glint = pow(clamp(dot(normalize(float3(-slope, 1.0)), normalize(float3(-0.35, -0.6, 0.72))), 0.0, 1.0), 220.0);
  col += float3(1.0, 0.98, 0.92) * glint * 0.8 * clamp(length(slope) * 6.0, 0.0, 1.0);

  // The pads: each sampled around its own cell's centre, lifted and tilted by the water there.
  float2 gq = (p - grid.xy) / grid.zw;
  float2 cell = floor(gq);
  if (cell.x >= -0.5 && cell.x < 6.5 && cell.y >= -0.5 && cell.y < 4.5) {
    float2 c = grid.xy + (cell + 0.5) * grid.zw;
    float3 wc = waves(c);
    float lift = 1.0 + wc.x * 0.012;
    float2 shift = float2(wc.y, wc.z) * 6.0 + float2(0.0, -wc.x * 0.35);
    float2 sp = c + (p - c - shift) / lift;
    // A pad's shadow on the floor, a little below and to the right of where it floats.
    float4 sh = pads.eval(sp - float2(3.0, 5.0));
    col *= 1.0 - 0.38 * sh.a;
    float4 pad = pads.eval(sp);
    if (pad.a > 0.001) {
      float3 pc = pad.rgb / pad.a;
      // Wet sheen where the pad tilts toward the light.
      pc += float3(0.8, 0.95, 0.8) * clamp(dot(float2(wc.y, wc.z), float2(-0.5, -0.7)) * 0.9, 0.0, 0.22);
      pc *= 1.0 - clamp(dot(float2(wc.y, wc.z), float2(0.5, 0.7)) * 0.7, 0.0, 0.18);
      col = mix(col, pc, pad.a);
    }
  }
  // The selected day: a soft ring of light around its pad.
  if (sel.z > 0.001) {
    float rd = abs(length(p - sel.xy) - grid.z * 0.5);
    col += float3(0.85, 1.0, 0.82) * exp(-rd * rd / 4.0) * sel.z * 0.6;
    col += float3(0.5, 0.8, 0.55) * exp(-rd * rd / 60.0) * sel.z * 0.12;
  }

  col *= 1.0 - 0.3 * dot(uv - 0.5, uv - 0.5);
  col += (hsh(pos) - 0.5) / 255.0;
  return half4(half3((col)), 1.0);
}
"""
