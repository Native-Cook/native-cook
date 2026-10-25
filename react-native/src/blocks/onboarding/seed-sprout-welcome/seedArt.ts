import { Skia, type SkPath } from '@shopify/react-native-skia';

import { STEM, hash, stemPoint } from './seedMotion';

/**
 * Static art for the pot, the can and the plant's parts, in pot coordinates ((0, 0) = middle of
 * the soil) or in each part's own frame. Built once.
 */

/** Terracotta pot: tapered body below a rolled rim. */
export function potBody(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-70, 12);
  b.lineTo(70, 12);
  b.cubicTo(66, 50, 60, 86, 54, 104);
  b.quadTo(52, 112, 44, 112);
  b.lineTo(-44, 112);
  b.quadTo(-52, 112, -54, 104);
  b.cubicTo(-60, 86, -66, 50, -70, 12);
  b.close();
  return b.build();
}

export function potRim(): SkPath {
  return Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(-80, -8, 160, 26), 9, 9)).build();
}

export const SOIL = Skia.XYWHRect(-68, -9, 136, 18);

/** Crumbs of soil on its surface. */
export function soilCrumbs(): SkPath {
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < 34; i += 1) {
    const a = hash(i * 1.9) * Math.PI * 2;
    const r = Math.sqrt(hash(i * 3.7));
    b.addCircle(Math.cos(a) * r * 60, Math.sin(a) * r * 7, 0.8 + 1.4 * hash(i * 5.3));
  }
  return b.build();
}

/** A seed: an almond, about 16 × 9 pt, split into two halves along its length. */
export function seedHalf(dir: number): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-8, 0);
  b.quadTo(0, dir * 10, 8, 0);
  b.close();
  return b.build();
}

/** Leaf pointing along +x from its base, 50 pt long, 22 pt wide. */
export function leafPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, 0);
  b.cubicTo(12, -14, 34, -13, 50, 0);
  b.cubicTo(34, 12, 12, 13, 0, 0);
  b.close();
  return b.build();
}

export function leafVeins(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(2, 0);
  b.quadTo(26, -1.5, 47, 0);
  for (const x of [12, 22, 32]) {
    b.moveTo(x, -0.6);
    b.lineTo(x + 7, -6.5);
    b.moveTo(x + 2, 0.4);
    b.lineTo(x + 8, 6);
  }
  return b.build();
}

/** Petal pointing up (−y) from the flower's centre, 36 pt long, notched at the tip. */
export function petalPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, 0);
  b.cubicTo(-12, -8, -11, -30, -4, -36);
  b.quadTo(0, -32, 4, -36);
  b.cubicTo(11, -30, 12, -8, 0, 0);
  b.close();
  return b.build();
}

/** The bud: a teardrop on the stem's tip, pointing up. */
export function budPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, 2);
  b.cubicTo(-12, -2, -9, -18, 0, -26);
  b.cubicTo(9, -18, 12, -2, 0, 2);
  b.close();
  return b.build();
}

/** Stamens around the flower's centre. */
export function stamens(): SkPath {
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < 14; i += 1) {
    const a = (i / 14) * Math.PI * 2 + hash(i) * 0.3;
    const r = 6 + 3 * hash(i * 2.3);
    b.addCircle(Math.cos(a) * r, Math.sin(a) * r, 1.1);
  }
  return b.build();
}

/** The stem from the soil up to `upTo` (0…1 of its length), sampled, so it really grows. */
export function stemPath(upTo: number, sway: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const n = 26;
  b.moveTo(0, 0);
  for (let k = 1; k <= n; k += 1) {
    const p = stemPoint((upTo * k) / n, sway);
    b.lineTo(p.x, p.y);
  }
  return b.build();
}

// The watering can, in its own frame: (0, 0) is the body's centre.

export function canBody(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-40, -30);
  b.lineTo(40, -30);
  b.cubicTo(44, -30, 46, -27, 46, -22);
  b.lineTo(50, 30);
  b.quadTo(50, 36, 44, 36);
  b.lineTo(-44, 36);
  b.quadTo(-50, 36, -50, 30);
  b.lineTo(-46, -22);
  b.cubicTo(-46, -27, -44, -30, -40, -30);
  b.close();
  return b.build();
}

export function canSpout(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-44, 8);
  b.lineTo(-80, -20);
  b.lineTo(-84, -16);
  b.lineTo(-46, 22);
  b.close();
  return b.build();
}

/** The rose (sprinkler head) at the spout's end, a short flared cap. */
export function canRose(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-78, -24);
  b.lineTo(-92, -34);
  b.quadTo(-100, -26, -94, -14);
  b.lineTo(-84, -12);
  b.close();
  return b.build();
}

export function canHandle(): SkPath {
  return Skia.PathBuilder.Make().moveTo(-18, -30).cubicTo(-14, -66, 30, -66, 36, -30).moveTo(42, -16).cubicTo(66, -14, 66, 18, 48, 22).build();
}

/** Soft daylight from a window, falling across the wall in two slanted panes. */
export function windowLight(w: number, h: number): SkPath {
  const b = Skia.PathBuilder.Make();
  const panes = [
    [w * 0.08, h * 0.08, w * 0.32],
    [w * 0.46, h * 0.04, w * 0.3],
  ];
  for (const [x, y, pw] of panes) {
    b.moveTo(x, y);
    b.lineTo(x + pw, y);
    b.lineTo(x + pw - h * 0.22, y + h * 0.5);
    b.lineTo(x - h * 0.22, y + h * 0.5);
    b.close();
  }
  return b.build();
}

export const STEM_TOP = STEM;

/** Grain over the wall and the shelf: a soft-light hash. */
export const GRAIN_SKSL = `
uniform float amount;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float n = h(floor(p * 1.4)) * 0.6 + h(floor(p * 0.4) + 23.0) * 0.4;
  return half4(half3(n), 1.0) * half(amount);
}
`;
