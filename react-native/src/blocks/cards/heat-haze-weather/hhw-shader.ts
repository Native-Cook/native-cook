// Heat Haze Weather — the scene, one Skia runtime effect over the whole screen.
//
// The scene is drawn in code: a sky in three stops with the sun and its glow, a far ridge and a mesa,
// a desert floor in perspective with scrub, a road with its lines and a row of utility poles (the
// straight lines that show the haze best). Heat haze is a displacement of the sampling point: two
// layers of value noise rise through a band around the horizon; they cross-fade on a FLOW period so
// the field loops (each layer is invisible when it wraps). Near the horizon the road turns into a
// mirage: the sky, mirrored, shimmering. A finger adds its own local shimmer.
//
// Uniforms come from hhw-motion.ts (sun, heat, dusk) and the ticker (phase, touch). Colours are
// written as sRGB; with `wide` = 1 the result is converted to Display P3, since Skia treats runtime
// effect output as P3 on iOS (AGENTS, Mistakes).

import { HAZE, HORIZON } from './hhw-motion';

const f = (n: number) => n.toFixed(4);
const c3 = (hex: string) => {
  const v = parseInt(hex.slice(1), 16);
  return `float3(${f(((v >> 16) & 255) / 255)}, ${f(((v >> 8) & 255) / 255)}, ${f((v & 255) / 255)})`;
};

/** the palette, noon → dusk */
export const PALETTE = {
  skyTop: ['#2C69AE', '#25284F'],
  skyMid: ['#8DB5D6', '#8A5A7E'],
  skyLow: ['#F2E4C6', '#F29A5E'],
  sun: ['#FFF4DC', '#FFB46E'],
  ridge: ['#A7A9B4', '#5E4868'],
  mesa: ['#B48C6C', '#4A3044'],
  sandFar: ['#DDBF90', '#A06A52'],
  sandNear: ['#BC915F', '#4F3631'],
  asphalt: ['#4C4744', '#2B2528'],
  paint: ['#EDE6D5', '#C9AFA0'],
  pole: ['#3C3431', '#22191F'],
} as const;

const mixed = (key: keyof typeof PALETTE) => `mix(${c3(PALETTE[key][0])}, ${c3(PALETTE[key][1])}, dusk)`;

// Identifiers avoid GLSL/WGSL reserved words (smooth, sample, …): Skia may translate this.
export const HHW_SKSL = `
uniform float2 size;     // pt
uniform float2 sun;      // pt
uniform float heat;      // 0…1
uniform float dusk;      // 0…1
uniform float phase;     // flow phase 0…1
uniform float3 touch;    // x, y (pt), strength
uniform float wide;      // 1: write Display P3
uniform float dpr;       // pixels per pt

float hsh(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 q = fract(p);
  float2 u = q * q * (3.0 - 2.0 * q);
  float a = hsh(i);
  float b = hsh(i + float2(1.0, 0.0));
  float c = hsh(i + float2(0.0, 1.0));
  float d = hsh(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float horizonY() { return size.y * ${f(HORIZON)}; }

float3 skyAt(float2 p) {
  float v = clamp(p.y / horizonY(), 0.0, 1.0);
  float3 top = ${mixed('skyTop')};
  float3 mid = ${mixed('skyMid')};
  float3 low = ${mixed('skyLow')};
  float3 col = v < 0.6 ? mix(top, mid, smoothstep(0.0, 0.6, v)) : mix(mid, low, smoothstep(0.6, 1.0, v));
  float d = length(p - sun);
  float3 sc = ${mixed('sun')};
  float r = size.x * 0.046;
  col += sc * (0.55 * exp(-d / (size.x * 0.075)) + (0.18 + 0.22 * dusk) * exp(-d / (size.x * 0.42)));
  col = mix(col, sc, smoothstep(r + 0.8, r - 0.8, d));
  return col;
}

float ridgeTop(float x) {
  float u = x / size.x;
  return size.y * (${f(HORIZON)} - 0.03 - 0.014 * sin(u * 5.3 + 1.1) - 0.008 * sin(u * 13.1 + 0.3));
}

// a butte: a flat top, a cliff, then a long talus slope down to the plain
float butte(float u, float centre, float top, float base, float h) {
  float d = abs(u - centre);
  if (d >= base) { return 0.0; }
  if (d <= top) { return h; }
  return h * (1.0 - pow((d - top) / (base - top), 0.55));
}

float mesaTop(float x) {
  float u = x / size.x;
  float h = max(butte(u, 0.185, 0.07, 0.15, 0.07), butte(u, 0.865, 0.022, 0.06, 0.042));
  h += step(0.0005, h) * 0.003 * (vnoise(float2(x / 3.0, 7.0)) - 0.5);
  return size.y * (${f(HORIZON)} - h) + step(h, 0.0) * size.y;
}

float3 groundAt(float2 p) {
  float hy = horizonY();
  float span = size.y - hy;
  float dy = max(p.y - hy, 0.0001);
  float z = span / dy;                 // depth: 1 at the bottom edge
  float wb = size.x * 0.62;            // the road's half width at depth 1, pt
  float xw = (p.x - size.x * 0.5) * z / wb;
  float perPt = z / wb;                // world x per pt, for the edges' antialiasing
  float nearK = clamp(dy / span, 0.0, 1.0);
  float3 col = mix(${mixed('sandFar')}, ${mixed('sandNear')}, sqrt(nearK));
  float scrub = vnoise(float2(xw * 7.0, z * 2.6));
  col *= 1.0 - 0.3 * smoothstep(0.8, 0.9, scrub) * smoothstep(0.02, 0.12, nearK) * (1.0 - 0.5 * smoothstep(0.5, 1.0, nearK));
  col *= 0.95 + 0.1 * vnoise(float2(xw * 2.0, z * 0.7));
  col *= 0.96 + 0.08 * vnoise(float2(xw * 9.0, z * 4.0));

  float ax = abs(xw);
  float onRoad = 1.0 - smoothstep(0.95 - perPt, 0.95 + perPt, ax);
  float3 road = ${mixed('asphalt')} * (0.94 + 0.1 * vnoise(float2(xw * 14.0, z * 6.0)));
  float edge = smoothstep(0.82 - perPt, 0.82 + perPt, ax) * (1.0 - smoothstep(0.87 - perPt, 0.87 + perPt, ax));
  float dash = (1.0 - smoothstep(0.022 - perPt, 0.022 + perPt, ax)) * step(fract(z * 0.35), 0.5);
  road = mix(road, ${mixed('paint')}, clamp(edge + dash, 0.0, 1.0) * 0.85);
  col = mix(col, road, onRoad);

  // far ground melts into the low sky
  col = mix(col, ${mixed('skyLow')}, exp(-dy / (size.y * 0.035)) * 0.62);
  return col;
}

float3 scene(float2 p) {
  float hy = horizonY();
  float3 col;
  if (p.y < hy) {
    col = skyAt(p);
    float rt = ridgeTop(p.x);
    float3 ridge = mix(${mixed('ridge')}, ${mixed('skyLow')}, 0.35);
    col = mix(col, ridge, smoothstep(rt - 0.6, rt + 0.6, p.y));
    float mt = mesaTop(p.x);
    // horizontal strata, a dustier talus and the shadowed side away from the sun
    float strata = vnoise(float2(p.x / 46.0, p.y / 2.6));
    float3 mesa = ${mixed('mesa')} * (0.84 + 0.2 * strata);
    float talus = smoothstep(mt + size.y * 0.018, hy, p.y);
    mesa = mix(mesa, mix(${mixed('sandFar')}, ${mixed('mesa')}, 0.5), talus * 0.5);
    float cx0 = p.x < size.x * 0.5 ? size.x * 0.185 : size.x * 0.865;
    float halfW = p.x < size.x * 0.5 ? size.x * 0.11 : size.x * 0.04;
    float lit = smoothstep(-halfW, halfW, (p.x - cx0) * sign(sun.x - cx0));
    mesa *= 0.78 + 0.24 * lit;
    col = mix(col, mesa, smoothstep(mt - 0.6, mt + 0.6, p.y));
  } else {
    col = groundAt(p);
    // the mirage: the sky, mirrored at the horizon, over the road and the sand nearest it
    float band = size.y * 0.05;
    float k = heat * (1.0 - smoothstep(0.0, band, p.y - hy));
    if (k > 0.002) {
      // sampled on a circle in phase, so the shimmer loops with the flow
      float2 orbit = float2(cos(6.2831853 * phase), sin(6.2831853 * phase)) * 0.9;
      float wob = vnoise(float2(p.x / 9.0, (p.y - hy) / 2.0) + orbit);
      float3 mirrored = skyAt(float2(p.x, hy - (p.y - hy) * 1.7));
      col = mix(col, mirrored, k * (0.55 + 0.35 * wob));
    }
  }

  // utility poles on the right, doubling in depth, with two wires between them
  float span = size.y - hy;
  float wb = size.x * 0.62;
  float z = 1.4;
  float px0 = 0.0;
  float py0 = 0.0;
  float pb0 = 0.0;
  for (int j = 0; j < 6; j++) {
    float base = hy + span / z;
    float xs = size.x * 0.5 + 1.55 * wb / z;
    float ph = 2.3 * span / z;
    float pw = max(0.016 * wb / z, 0.45);
    float barY = base - ph * 0.92;
    float bw = 0.3 * wb / z;
    float bh = max(0.02 * span / z, 0.45);
    float inX = 1.0 - smoothstep(pw - 0.5, pw + 0.5, abs(p.x - xs));
    float inY = step(base - ph, p.y) * (1.0 - smoothstep(base - 0.5, base + 0.5, p.y));
    float inBar = (1.0 - smoothstep(bh - 0.5, bh + 0.5, abs(p.y - barY))) * (1.0 - smoothstep(bw - 0.5, bw + 0.5, abs(p.x - xs)));
    float wire = 0.0;
    if (j > 0 && p.x > xs - bw && p.x < px0 + pb0) {
      for (int k = 0; k < 2; k++) {
        float side = k == 0 ? -0.8 : 0.8;
        float xa = xs + side * bw;
        float xb = px0 + side * pb0;
        float u = clamp((p.x - xa) / max(xb - xa, 0.001), 0.0, 1.0);
        float yw = mix(barY, py0, u) + 0.09 * span / z * 4.0 * u * (1.0 - u);
        wire = max(wire, 1.0 - smoothstep(0.25, 0.85, abs(p.y - yw)));
      }
    }
    float fog = exp(-(base - hy) / (size.y * 0.05));
    float3 pole = mix(${mixed('pole')}, ${mixed('skyLow')}, 0.75 * fog);
    col = mix(col, pole, max(max(inX * inY, inBar), wire * 0.7));
    px0 = xs;
    py0 = barY;
    pb0 = bw;
    z *= 1.9;
  }
  return col;
}

half4 main(float2 p) {
  float hy = horizonY();
  float w = exp(-abs(p.y - hy - size.y * ${f(HAZE.bandOffset)}) / (size.y * ${f(HAZE.bandFold)}));
  float2 tv = p - touch.xy;
  float poke = touch.z * exp(-dot(tv, tv) / ${f(HAZE.touchRadius * HAZE.touchRadius)});
  float amp = heat * ${f(HAZE.amp)} * w + poke * ${f(HAZE.touchAmp)};
  float2 off = float2(0.0);
  if (amp > 0.01) {
    // two layers rising through the band; each is invisible as it wraps
    float wa = 1.0 - abs(1.0 - 2.0 * phase);
    float norm = 1.0 / sqrt(wa * wa + (1.0 - wa) * (1.0 - wa));
    float2 q = float2(p.x / ${f(HAZE.cellX)}, p.y / ${f(HAZE.cellY)});
    float2 qa = q + float2(0.0, phase * ${f(HAZE.rise)});
    float2 qb = q + float2(3.7, fract(phase + 0.5) * ${f(HAZE.rise)});
    float na = vnoise(qa) + 0.5 * vnoise(qa * 2.3 + 5.1);
    float nb = vnoise(qb) + 0.5 * vnoise(qb * 2.3 + 5.1);
    float ma = vnoise(qa * 0.7 + 11.0);
    float mb = vnoise(qb * 0.7 + 11.0);
    float nx = (mix(nb, na, wa) / 1.5 - 0.5) * norm;
    float ny = (mix(mb, ma, wa) - 0.5) * norm;
    off = float2(nx * 2.0, ny) * amp;
  }
  float3 col = scene(p + off);
  // the air itself: a little paler where it is hottest, a little brighter under the finger
  col = mix(col, ${mixed('skyLow')}, heat * 0.1 * w);
  col += poke * 0.035;

  float2 c = (p / size - 0.5) * float2(0.9, 1.1);
  col *= 1.0 - 0.14 * dot(c, c) * 2.0;
  col += (hsh(floor(p * dpr)) - 0.5) * 0.024;
  col = clamp(col, 0.0, 1.0);

  if (wide > 0.5) {
    float3 lin = pow(col, float3(2.2));
    float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
    col = pow(max(p3, float3(0.0)), float3(1.0 / 2.2));
  }
  return half4(half3(col), 1.0);
}
`;
