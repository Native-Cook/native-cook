import { Skia, type SkPath } from '@shopify/react-native-skia';

/** The knob's grip: notches round the rim, and a dot that shows how far it has turned. */
export function knobRidges(r: number): SkPath {
  const b = Skia.PathBuilder.Make();
  const n = 28;
  for (let i = 0; i < n; i += 1) {
    const a = (i / n) * Math.PI * 2;
    const c = Math.cos(a);
    const s = Math.sin(a);
    b.moveTo(c * (r - 5), s * (r - 5));
    b.lineTo(c * (r - 0.8), s * (r - 0.8));
  }
  return b.build();
}

/** The drawn line, from the signature's points up to running length `drawn`. */
export function linePath(pts: number[], run: number[], drawn: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  if (drawn <= 0.5) return b.build();
  b.moveTo(pts[0], pts[1]);
  const n = run.length;
  for (let k = 1; k < n; k += 1) {
    if (run[k] <= drawn) {
      b.lineTo(pts[2 * k], pts[2 * k + 1]);
      continue;
    }
    const f = (drawn - run[k - 1]) / Math.max(0.0001, run[k] - run[k - 1]);
    b.lineTo(pts[2 * k - 2] + (pts[2 * k] - pts[2 * k - 2]) * f, pts[2 * k - 1] + (pts[2 * k + 1] - pts[2 * k - 1]) * f);
    break;
  }
  return b.build();
}

/** A finger's line: the points it has scraped so far (x, y interleaved). */
export function freePath(pts: number[]): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  if (pts.length < 4) return b.build();
  b.moveTo(pts[0], pts[1]);
  for (let i = 2; i < pts.length; i += 2) b.lineTo(pts[i], pts[i + 1]);
  return b.build();
}

const P3 = `
float3 toP3(float3 s) {
  float3 l = pow(clamp(s, 0.0, 1.0), float3(2.2));
  float3 q = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(q, float3(1.0 / 2.2));
}
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
`;

/**
 * The screen: aluminium powder on glass. A cool grey with a fine grain, a few flakes that glint
 * as the toy tilts, a soft inner shadow under the bezel, and a slightly brighter band where the
 * powder is resettling. Converted from sRGB to Display P3.
 */
export const SCREEN_SKSL = `
uniform float4 rect;
uniform float tilt;
uniform float sweep;
uniform float band;
${P3}
float box(float2 p, float2 c, float2 e, float r) {
  float2 q = abs(p - c) - e + r;
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}
half4 main(float2 p) {
  float2 uv = (p - rect.xy) / rect.zw;
  // Brushed silver: bright at the top, deeper toward the bottom, a cool cast.
  float3 top = float3(0.89, 0.9, 0.91);
  float3 mid = float3(0.78, 0.79, 0.81);
  float3 bot = float3(0.62, 0.635, 0.665);
  float3 c = uv.y < 0.5 ? mix(top, mid, uv.y * 2.0) : mix(mid, bot, (uv.y - 0.5) * 2.0);
  c *= float3(0.985, 0.995, 1.02);
  // A soft diagonal sheen that slides as the toy tilts.
  float sh = (uv.x * 0.8 + uv.y * 0.6 - 0.38 - tilt * 4.0) / 0.13;
  c += 0.07 * exp(-sh * sh);
  // Powder: fine grain, and flakes that catch the light (more of them as it tilts).
  float g = h(floor(p * 2.0));
  float g2 = h(floor(p * 0.6) + 7.0);
  c *= 0.92 + 0.08 * g + 0.05 * (g2 - 0.5);
  float flake = step(0.9, h(floor(p * 1.4) + 13.0));
  float ang = h(floor(p * 1.4) + 29.0) * 6.2831;
  c += 0.32 * flake * pow(max(0.0, cos(ang - tilt * 45.0 - 0.8)), 10.0);
  c += 0.045 * step(0.985, h(floor(p * 2.0) + 41.0));
  float d = box(p, rect.xy + rect.zw * 0.5, rect.zw * 0.5, 14.0);
  c *= 1.0 - 0.4 * smoothstep(-18.0, 0.0, d);
  c *= 1.0 - 0.12 * smoothstep(0.06, 0.0, uv.y);
  float k = (p.y - sweep) / 22.0;
  c += 0.05 * band * exp(-k * k) * (0.4 + g);
  return half4(half3(toP3(c)), 1.0);
}
`;

/**
 * The scraped line (drawn twice: a dark graphite core and a faint light lip just below it, so it
 * reads as a groove), a little uneven, and erased above a ragged front as the powder resettles.
 */
export const LINE_SKSL = `
uniform float sweep;
uniform float4 ink;
${P3}
half4 main(float2 p) {
  float g = h(floor(p * 2.0));
  float n = h(floor(p * 0.25) + 3.0);
  float edge = sweep + (n - 0.5) * 26.0 + (g - 0.5) * 8.0;
  float keep = smoothstep(edge - 2.0, edge + 2.0, p.y);
  float a = ink.a * (0.82 + 0.18 * g) * keep;
  return half4(half3(toP3(ink.rgb)) * a, a);
}
`;

/** Plastic grain for the red frame. */
export const GRAIN_SKSL = `
uniform float amount;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  return half4(half3(h(floor(p * 1.5))), 1.0) * half(amount);
}
`;
