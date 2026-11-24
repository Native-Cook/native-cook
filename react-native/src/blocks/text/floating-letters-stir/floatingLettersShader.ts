// The water under Floating Letters Stir: one Skia RuntimeEffect pass over the screen.
//
// A pale, shallow pool seen from above: a depth gradient (lighter at the top, a deeper teal toward the
// bottom and the corners), two layers of caustics (the bright net sunlight draws on a pool floor, the
// second larger, dimmer and slower for depth), and the rings a finger leaves. Each ring is a travelling
// wave packet: a sine of wavelength LAMBDA under a Gaussian of width WIDTH whose centre runs outward at
// SPEED. Its slope refracts the caustics (the floor shifts under a wave) and lights the side that faces
// the light, top left. The same ring maths, in floatingLettersMotion.ts, bobs the letters.
//
// Time enters only as `phase` = 2*pi * ambient / LOOP with whole multipliers, so the water at LOOP is the
// water at 0.
//
// Output: Skia treats runtime-effect output as Display P3 on iOS (AGENTS "Mistakes"), so with `wide` = 1
// the linear sRGB result is converted to P3 before encoding. Then a triangular dither and a static grain.

import { RIPPLE_LAMBDA, RIPPLE_LIFE, RIPPLE_SPEED, RIPPLE_WIDTH, RIPPLES } from './floatingLettersMotion';

export const WATER_SKSL = `
uniform float2 size;
uniform float2 focus;      // centre of the phrase, pt
uniform float phase;       // 0..2*pi over one LOOP
uniform float4 rip[${RIPPLES}];      // x, y, age (s), strength (0 = unused)
uniform float3 touch;      // finger x, y, presence 0..1
uniform float wide;        // 1: write Display P3 (iOS)
uniform float dpr;

const float TAU = 6.2831853;
const float SPEED = ${RIPPLE_SPEED.toFixed(1)};
const float LIFE = ${RIPPLE_LIFE.toFixed(2)};
const float WIDTH = ${RIPPLE_WIDTH.toFixed(1)};
const float KW = ${((2 * Math.PI) / RIPPLE_LAMBDA).toFixed(5)};

float3 lin(float3 c) { return pow(c, float3(2.2)); }

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

// Height (x) and slope / wave number (yz) of every ring at p.
float3 rings(float2 p) {
  float3 acc = float3(0.0);
  for (int k = 0; k < ${RIPPLES}; k++) {
    float4 r = rip[k];
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
  float2 p = mod(uv * TAU, TAU) - 250.0;
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
  c = 1.17 - pow(c, 1.4);
  return pow(abs(c), 8.0);
}

float3 encode(float3 l) {
  l = clamp(l, 0.0, 1.0);
  if (wide > 0.5) {
    l = float3(
      dot(l, float3(0.8225, 0.1774, 0.0)),
      dot(l, float3(0.0332, 0.9669, 0.0)),
      dot(l, float3(0.0171, 0.0724, 0.9108)));
  }
  float3 lo = l * 12.92;
  float3 hi = 1.055 * pow(l, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), l));
}

half4 main(float2 p) {
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
  float3 rg = rings(p);
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
  return half4(half3(rgb), 1.0);
}
`;
