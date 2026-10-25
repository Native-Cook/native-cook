import { PathOp, Skia, type SkPath } from '@shopify/react-native-skia';

import { hash } from './fortuneMotion';

/**
 * The cookie, drawn in its own coordinates: centre (0, 0), about 230 pt wide, front view of the
 * folded crescent (two lobes, the crease dipping between them, the folded lip underneath). It is
 * split once into two halves along a seeded jagged crack, so each half can swing on its own.
 */

/** One smooth dome on top, two wings below pinched into the fold's notch in the middle. */
function outline(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-116, 32);
  b.cubicTo(-124, -26, -66, -66, 0, -66);
  b.cubicTo(66, -66, 124, -26, 116, 32);
  b.cubicTo(102, 54, 58, 64, 26, 54);
  b.quadTo(10, 42, 0, 4);
  b.quadTo(-10, 42, -26, 54);
  b.cubicTo(-58, 64, -102, 54, -116, 32);
  b.close();
  return b.build();
}

/** The folded-over flaps either side of the notch: the disc's edge, turned out toward us. */
function lip(): SkPath {
  const b = Skia.PathBuilder.Make();
  for (const d of [-1, 1]) {
    b.moveTo(0, 4);
    b.cubicTo(d * 30, 12, d * 82, 8, d * 113, 31);
    b.cubicTo(d * 102, 54, d * 58, 64, d * 26, 54);
    b.quadTo(d * 10, 42, 0, 4);
    b.close();
  }
  return b.build();
}

/** The flaps' rolled edge, which catches the light. */
function rim(): SkPath {
  const b = Skia.PathBuilder.Make();
  for (const d of [-1, 1]) {
    b.moveTo(d * 2, 6);
    b.cubicTo(d * 30, 12, d * 82, 8, d * 112, 31);
  }
  return b.build();
}

/** The crack: from the crease down to the bottom edge, x jitter of ±5 pt every 8 pt. */
function crackPoints() {
  const pts: [number, number][] = [];
  for (let k = 0; k <= 14; k += 1) {
    const y = -74 + k * 8.5;
    const jx = k === 0 ? 0 : (hash(k * 2.7 + 3) - 0.5) * 10;
    pts.push([jx, y]);
  }
  return pts;
}

const CRACK = crackPoints();

function side(dir: number): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(CRACK[0][0], -200);
  for (const [x, y] of CRACK) b.lineTo(x, y);
  b.lineTo(CRACK[CRACK.length - 1][0], 200);
  b.lineTo(dir * 300, 200);
  b.lineTo(dir * 300, -200);
  b.close();
  return b.build();
}

function cut(a: SkPath, b: SkPath) {
  return Skia.Path.MakeFromOp(a, b, PathOp.Intersect) ?? a;
}

export type CookieArt = {
  left: SkPath;
  right: SkPath;
  leftLip: SkPath;
  rightLip: SkPath;
  whole: SkPath;
  wholeLip: SkPath;
  /** The dark pocket seen through the notch, whole and split. */
  inside: SkPath;
  leftInside: SkPath;
  rightInside: SkPath;
  rim: SkPath;
  crease: SkPath;
  /** The crack's main line and two short branches, trimmed by the crack progress. */
  crack: SkPath;
  branches: SkPath;
  /** Pivots the halves swing about: their outer lower tips. */
  pivotL: [number, number];
  pivotR: [number, number];
};

export function makeCookie(): CookieArt {
  const o = outline();
  const pocket = Skia.PathBuilder.Make().addOval(Skia.XYWHRect(-15, 8, 30, 33)).build();
  const l = lip();
  const L = side(-1);
  const R = side(1);
  const crease = Skia.PathBuilder.Make().moveTo(0, 4).quadTo(-2, -14, 0, -36).build();
  const c = Skia.PathBuilder.Make();
  c.moveTo(CRACK[0][0], CRACK[0][1]);
  for (const [x, y] of CRACK) c.lineTo(x, y);
  const br = Skia.PathBuilder.Make();
  br.moveTo(CRACK[3][0], CRACK[3][1]).lineTo(CRACK[3][0] - 12, CRACK[3][1] + 6).lineTo(CRACK[3][0] - 24, CRACK[3][1] + 4);
  br.moveTo(CRACK[6][0], CRACK[6][1]).lineTo(CRACK[6][0] + 10, CRACK[6][1] + 4).lineTo(CRACK[6][0] + 20, CRACK[6][1] + 1);
  return {
    left: cut(o, L),
    right: cut(o, R),
    leftLip: cut(l, L),
    rightLip: cut(l, R),
    whole: o,
    wholeLip: l,
    inside: pocket,
    leftInside: cut(pocket, L),
    rightInside: cut(pocket, R),
    rim: rim(),
    crease,
    crack: c.build(),
    branches: br.build(),
    pivotL: [-102, 36],
    pivotR: [102, 36],
  };
}

/**
 * Baked cookie: golden in the middle, toasted toward the rim and tips, sheen on the two lobes,
 * a fine baked texture (value-noise fbm, three octaves), pits and a few sugar sparkles. Local
 * coordinates, so the texture travels with each half. Output converted from sRGB to Display P3.
 */
export const COOKIE_SKSL = `
uniform float shade;

float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float vn(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = h(i);
  float b = h(i + float2(1.0, 0.0));
  float c = h(i + float2(0.0, 1.0));
  float d = h(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(float2 p) {
  return vn(p) * 0.55 + vn(p * 2.03 + 7.1) * 0.3 + vn(p * 4.11 + 3.7) * 0.15;
}
float3 toP3(float3 s) {
  float3 l = pow(clamp(s, 0.0, 1.0), float3(2.2));
  float3 q = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(q, float3(1.0 / 2.2));
}
half4 main(float2 p) {
  float2 q = p / float2(114.0, 64.0);
  float v = clamp(q.y * 0.5 + 0.5, 0.0, 1.0);
  float3 top = float3(0.97, 0.80, 0.47);
  float3 mid = float3(0.91, 0.66, 0.31);
  float3 bot = float3(0.76, 0.48, 0.19);
  float3 c = v < 0.5 ? mix(top, mid, v * 2.0) : mix(mid, bot, (v - 0.5) * 2.0);
  // A broad sheen on the dome and a tighter glint: the baked sugar is a little glossy.
  float2 a = (p - float2(-34.0, -36.0)) / float2(62.0, 26.0);
  c += float3(0.15, 0.12, 0.07) * exp(-dot(a, a));
  float2 g = (p - float2(-48.0, -46.0)) / float2(22.0, 7.0);
  c += float3(0.2, 0.18, 0.13) * exp(-dot(g, g));
  // Toasted toward the wing tips and the folded bottom.
  float e = length(q * float2(1.0, 1.1));
  c *= 1.0 - 0.36 * smoothstep(0.74, 1.1, e);
  c *= 1.0 - 0.16 * smoothstep(0.15, 0.8, q.y);
  float blot = smoothstep(0.55, 0.82, fbm(p * 0.05 + 11.0));
  c = mix(c, c * float3(0.86, 0.74, 0.62), blot * 0.4);
  c *= 0.93 + 0.12 * fbm(p * 0.12);
  c *= 0.97 + 0.05 * h(floor(p * 1.3));
  c += float3(0.12, 0.1, 0.07) * step(0.994, h(floor(p * 0.9)));
  c *= shade;
  return half4(half3(toP3(c)), 1.0);
}
`;

/** Paper grain for the modal and the slip: a soft-light hash. */
export const GRAIN_SKSL = `
uniform float amount;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float n = h(floor(p * 1.4)) * 0.6 + h(floor(p * 0.35) + 13.0) * 0.4;
  return half4(half3(n), 1.0) * half(amount);
}
`;
