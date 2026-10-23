/**
 * Vitals Chip Morph — the liquid material. One SkSL image filter over the hero's layer: while the
 * shape morphs it turns into liquid glass (a bevelled height field from the blurred silhouette →
 * normals → refraction of its own colours, a studio reflection weighted by Fresnel, a key-light
 * glint and a caustic on the far rim), its edges wobble like liquid, nearby parts melt together
 * (blur + threshold, so the arc's caps neck and merge and the inner rings bud off the outer one), and
 * each heartbeat sends a ripple out from the heart's centre.
 *
 * Compiled lazily by `liquidEffect()`; without it the hero draws the flat morph.
 *
 * Every pixel is a pure function of the uniforms. With `liquid`, `wobble`, `goo` and `ripA` at 0 the
 * filter returns its input untouched, so the rest frame is the replica pixel for pixel.
 * Lengths are in the hero's ref px (1206 = screen width). iOS treats a runtime effect's output as
 * Display P3: the tinted constants are converted from sRGB in JS; the child images arrive in the
 * working space already.
 */

import { Skia, type SkRuntimeEffect } from '@shopify/react-native-skia';

import { liquidLook } from './theme';

/** sRGB (0–255) → the same colour's Display P3 coordinates, as an SkSL float3 literal. */
function p3(c: readonly number[]) {
  const lin = c.map((v) => {
    const x = v / 255;
    return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
  });
  const m = [
    [0.8225, 0.1774, 0.0],
    [0.0332, 0.9669, 0.0],
    [0.0171, 0.0724, 0.9108],
  ];
  const out = m.map((r) => {
    const l = Math.max(0, r[0] * lin[0] + r[1] * lin[1] + r[2] * lin[2]);
    return l <= 0.0031308 ? 12.92 * l : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
  });
  return `float3(${out.map((v) => v.toFixed(4)).join(', ')})`;
}

const L = liquidLook;

// Identifiers stay clear of WGSL builtin names (no `distance`, `select`, `length` as a name, …).
const SKSL = `
uniform shader image;
uniform shader soft;

uniform float clock;
uniform float liquid;
uniform float wobble;
uniform float goo;
uniform float2 gooC;
uniform float2 heartC;
uniform float ripT;
uniform float ripA;

const float3 SKY_TOP = ${p3(L.skyTop)};
const float3 SKY_LOW = ${p3(L.skyLow)};
const float3 HORIZON = ${p3(L.horizon)};
const float3 FLOOR_NEAR = ${p3(L.floorNear)};
const float3 FLOOR_FAR = ${p3(L.floorFar)};
const float3 KEY = float3(-0.47, -0.74, 0.48);

float sat1(float v) { return clamp(v, 0.0, 1.0); }

// Low-frequency liquid displacement (ref px).
float2 flowAt(float2 p) {
  float a = sin(p.y * 0.021 + clock * 5.3 + sin(p.x * 0.013 - clock * 3.1) * 1.7);
  float b = sin(p.x * 0.019 - clock * 4.7 + sin(p.y * 0.017 + clock * 2.3) * 1.5);
  return float2(a, b) * ${L.wobblePx.toFixed(1)} * wobble;
}

// The heartbeat's ripple: a damped ring wave travelling out from the heart's centre.
// Returns (height slope along the radius, envelope) and writes the radial direction.
float2 rippleAt(float2 p, out float2 dir) {
  float2 d = p - heartC;
  float r = max(sqrt(dot(d, d)), 1.0);
  dir = d / r;
  if (ripA <= 0.0) return float2(0.0);
  float x = r - ripT * ${L.ripSpeed.toFixed(1)};
  float env = exp(-x * x / ${(2 * L.ripWidth * L.ripWidth).toFixed(1)}) * exp(-ripT / ${L.ripDecay.toFixed(3)}) * ripA;
  // Fade in over the first wavelength so the ring is born at the centre without a pop.
  env *= sat1(r / ${L.ripBirth.toFixed(1)});
  return float2(env * cos(x * ${L.ripK.toFixed(4)}), env);
}

// A studio around the shape: bright soft box above, a darker horizon band, a pale floor.
float3 studio(float3 rv) {
  float t = rv.y;
  float3 sky = mix(SKY_TOP, SKY_LOW, smoothstep(-0.95, 0.0, t));
  float3 flo = mix(FLOOR_NEAR, FLOOR_FAR, smoothstep(0.0, 0.85, t));
  float3 c = mix(sky, flo, step(0.0, t));
  c = mix(c, HORIZON, exp(-t * t / 0.006) * 0.75);
  // A tall strip light up and to the left.
  c += float3(0.35) * smoothstep(0.09, 0.02, abs(rv.x + 0.38)) * smoothstep(-0.15, -0.55, t);
  return c;
}

float3 straight(half4 c) { return c.a > 0.0005 ? float3(c.rgb) / float(c.a) : float3(0.0); }

half4 main(float2 p) {
  half4 rest = image.eval(p);
  float2 dir;
  float2 rip = rippleAt(p, dir);
  float mat = max(max(liquid, wobble), rip.y);
  if (mat <= 0.0005) return rest;

  float2 q = p + flowAt(p) + dir * rip.x * ${L.ripPx.toFixed(1)};
  half4 base = image.eval(q);
  half4 s0 = soft.eval(q);
  // Empty here and too far from the shape for the goo to reach (its threshold starts at 0.5 − 0.3): the result below would be
  // this same clear pixel, so skip the four slope samples. Most of the hero is such pixels.
  if (float(base.a) <= 0.001 && float(s0.a) < 0.2) return mix(rest, half4(0.0), half(mat));

  // Height field = the blurred silhouette; its slope gives the bevel's normal.
  float e = ${L.slopeStep.toFixed(1)};
  float ax = float(soft.eval(q + float2(e, 0.0)).a - soft.eval(q - float2(e, 0.0)).a);
  float ay = float(soft.eval(q + float2(0.0, e)).a - soft.eval(q - float2(0.0, e)).a);
  float2 g = float2(ax, ay) / (2.0 * e);

  // Liquid edge: the sharp shape, plus the blurred one thresholded (parts close together melt).
  float aw = clamp(sqrt(dot(g, g)) * 1.4, 0.015, 0.3);
  // Only where the shape closes or opens (the caps meeting, the heart's tip): elsewhere the rings'
  // thin gaps must stay open.
  float2 gd = (q - gooC) / ${L.gooRadius.toFixed(1)};
  float gooHere = goo * (1.0 - smoothstep(0.55, 1.0, sqrt(dot(gd, gd))));
  // A bridge only grows outside the sharp shape, so a half-filled shape keeps its own alpha.
  float gooA = smoothstep(0.5 - aw, 0.5 + aw, float(s0.a)) * gooHere * (1.0 - smoothstep(0.0, 0.08, float(base.a)));
  float a = max(float(base.a), gooA);
  if (a <= 0.001) return mix(rest, half4(0.0), half(mat));

  float inside = sat1(float(base.a) * 1.6);
  float3 col = mix(straight(s0), straight(base), inside);

  float lit = max(liquid, rip.y * ${L.ripLight.toFixed(2)});
  float2 slope = -g * ${L.slope.toFixed(1)} * liquid - dir * rip.x * ${L.ripSlope.toFixed(2)} * a;
  float3 n = normalize(float3(slope, 1.0));
  float tilt = 1.0 - n.z;

  // Refraction: the rim magnifies the colours inside it.
  half4 bent = image.eval(q + slope * ${L.refractPx.toFixed(1)} * -1.0);
  if (bent.a > 0.2) col = mix(col, straight(bent), sat1(tilt * 3.0));

  float3 ld = normalize(KEY);
  float ndl = dot(n, ld);
  float3 h = normalize(ld + float3(0.0, 0.0, 1.0));
  float nh = max(dot(n, h), 0.0);
  float spec = pow(nh, 90.0) * 1.15 + pow(nh, 14.0) * 0.10;
  float fres = 0.05 + 0.95 * pow(tilt, 2.2);
  float3 rv = float3(2.0 * n.z * n.x, 2.0 * n.z * n.y, 2.0 * n.z * n.z - 1.0);
  float2 out2 = sqrt(dot(n.xy, n.xy)) > 0.0001 ? normalize(n.xy) : float2(0.0);
  float caustic = max(dot(out2, -normalize(ld.xy)), 0.0) * smoothstep(0.02, 0.35, tilt);

  float3 body = col * (0.86 + 0.22 * ndl) * (1.0 - 0.22 * smoothstep(0.25, 0.85, tilt));
  body += col * caustic * 0.55;
  float3 shaded = mix(body, studio(rv), sat1(fres * 0.9)) + float3(spec);
  float3 outC = mix(col, shaded, lit);

  half4 wet = half4(half3(outC * a), half(a));
  return mix(rest, wet, half(sat1(mat * 4.0)));
}
`;

let compiled: SkRuntimeEffect | null | undefined;

/**
 * The liquid effect, compiled on first use (not at import) and cached. Null when the GPU backend refuses it: the hero then
 * keeps the flat morph (the shape, colours and timing without the glass), and the screen still works.
 */
export function liquidEffect(): SkRuntimeEffect | null {
  if (compiled !== undefined) return compiled;
  try {
    compiled = Skia.RuntimeEffect.Make(SKSL);
  } catch {
    compiled = null;
  }
  if (!compiled && __DEV__) console.warn('vitals-chip-morph: the liquid shader did not compile; drawing the flat morph');
  return compiled;
}
