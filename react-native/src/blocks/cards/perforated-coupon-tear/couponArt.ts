import { Skia, type SkPath } from '@shopify/react-native-skia';

import { CARD_H, CORNER, HOLE_GAP, NOTCH_R, hash, holeCount } from './couponMotion';

/**
 * Paths of the coupon. The tear line is one seeded polyline down the perforation: the body's
 * right edge and the stub's left edge both follow it above the tear front (so the two halves
 * fit exactly) and run straight below it, where the paper is still whole.
 */

export const HOLE_Y0 = NOTCH_R + 4;

/** y of every edge sample, three per hole gap, hole centres included; jitter is 0 on a hole. */
function edgeSamples() {
  const ys: number[] = [];
  const js: number[] = [];
  const step = HOLE_GAP / 3;
  const first = Math.ceil(-HOLE_Y0 / step);
  const last = Math.floor((CARD_H - HOLE_Y0) / step);
  ys.push(0);
  js.push(0);
  for (let j = first; j <= last; j += 1) {
    const y = HOLE_Y0 + j * step;
    if (y <= 0.5 || y >= CARD_H - 0.5) continue;
    ys.push(y);
    js.push(j % 3 === 0 ? 0 : (hash(j * 3.7 + 1) - 0.5) * 3.6);
  }
  ys.push(CARD_H);
  js.push(0);
  return { ys, js };
}

export const EDGE = edgeSamples();

/** The body: rounded on the left, its right edge on the tear line. */
export function bodyPath(px: number, front: number, ys: number[], js: number[]): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(CORNER, 0);
  b.lineTo(px, 0);
  for (let i = 1; i < ys.length - 1; i += 1) b.lineTo(px + (ys[i] < front ? js[i] : 0), ys[i]);
  b.lineTo(px, CARD_H);
  b.lineTo(CORNER, CARD_H);
  b.quadTo(0, CARD_H, 0, CARD_H - CORNER);
  b.lineTo(0, CORNER);
  b.quadTo(0, 0, CORNER, 0);
  b.close();
  return b.build();
}

/** The stub: rounded on the right, its left edge on the same tear line. */
export function stubPath(px: number, cw: number, front: number, ys: number[], js: number[]): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(px, 0);
  b.lineTo(cw - CORNER, 0);
  b.quadTo(cw, 0, cw, CORNER);
  b.lineTo(cw, CARD_H - CORNER);
  b.quadTo(cw, CARD_H, cw - CORNER, CARD_H);
  b.lineTo(px, CARD_H);
  for (let i = ys.length - 2; i >= 1; i -= 1) b.lineTo(px + (ys[i] < front ? js[i] : 0), ys[i]);
  b.close();
  return b.build();
}

/**
 * The torn edge's paper core (a light line along the jag) and its fibres: short hairs that
 * stick out of the torn part, `dir` = +1 for the body (into the gap) or -1 for the stub.
 */
export function fibrePath(px: number, front: number, ys: number[], js: number[], dir: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  let started = false;
  for (let i = 0; i < ys.length; i += 1) {
    if (ys[i] > front) break;
    const x = px + js[i];
    if (!started) {
      b.moveTo(x, ys[i]);
      started = true;
    } else b.lineTo(x, ys[i]);
  }
  for (let i = 1; i < ys.length - 1; i += 1) {
    if (ys[i] > front - 1) break;
    const h = hash(i * 9.1 + (dir > 0 ? 3 : 17));
    if (h < 0.35) continue;
    const len = 1.4 + 2.6 * hash(i * 5.3 + dir);
    const ang = (hash(i * 2.9 + dir * 7) - 0.5) * 1.3;
    const x = px + js[i];
    b.moveTo(x, ys[i]);
    b.lineTo(x + dir * len * Math.cos(ang), ys[i] + len * Math.sin(ang));
  }
  return b.build();
}

/** Perforation holes and the two notches, as one path. */
export function holesPath(px: number): SkPath {
  const b = Skia.PathBuilder.Make();
  const n = holeCount();
  for (let k = 0; k < n; k += 1) b.addCircle(px, HOLE_Y0 + k * HOLE_GAP, 2.7);
  b.addCircle(px, 0, NOTCH_R);
  b.addCircle(px, CARD_H, NOTCH_R);
  return b.build();
}

/** A barcode of horizontal bars across the stub, seeded. */
export function barcodePath(x: number, y: number, w: number, h: number): SkPath {
  const b = Skia.PathBuilder.Make();
  let yy = y;
  let i = 0;
  while (yy < y + h) {
    const t = 1 + Math.floor(hash(i * 1.71 + 4) * 3);
    if (i % 2 === 0) b.addRect(Skia.XYWHRect(x, yy, w, t));
    yy += t + (i % 2 === 0 ? 0 : 1);
    i += 1;
  }
  return b.build();
}

/** A small cup on a saucer, centred on (cx, cy), about 14 pt wide (the stamp-card mark). */
export function cupPath(cx: number, cy: number): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(cx - 6, cy - 4);
  b.lineTo(cx + 4, cy - 4);
  b.lineTo(cx + 3.2, cy + 2.5);
  b.quadTo(cx + 2.8, cy + 4.5, cx + 0.5, cy + 4.5);
  b.lineTo(cx - 3.5, cy + 4.5);
  b.quadTo(cx - 5.8, cy + 4.5, cx - 5.4, cy + 2.5);
  b.close();
  b.addRRect(Skia.RRectXY(Skia.XYWHRect(cx - 8, cy + 5.6, 14, 1.8), 0.9, 0.9));
  return b.build();
}

/** The cup's handle, stroked. */
export function cupHandlePath(cx: number, cy: number): SkPath {
  return Skia.PathBuilder.Make().addCircle(cx + 4.6, cy - 0.6, 2.2).build();
}

/** Coffee-bean outlines tiled faintly over the body (built once). */
export function beansPath(w: number, h: number): SkPath {
  const b = Skia.PathBuilder.Make();
  for (let row = 0; row < 7; row += 1) {
    for (let col = 0; col < 10; col += 1) {
      const x = col * 30 + (row % 2) * 15 + (hash(row * 11 + col) - 0.5) * 6;
      const y = row * 30 + 8 + (hash(row * 7 + col * 3) - 0.5) * 6;
      if (x > w - 6 || y > h - 6) continue;
      b.addOval(Skia.XYWHRect(x - 5, y - 7, 10, 14));
      b.moveTo(x, y - 6);
      b.quadTo(x - 2.5, y, x, y + 6);
    }
  }
  return b.build();
}

/** Grain: a per-point hash, mixed in with soft light over flat paper and leather. */
export const GRAIN_SKSL = `
uniform float amount;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float n = h(floor(p * 1.5)) * 0.65 + h(floor(p * 0.5) + 31.0) * 0.35;
  return half4(half3(n), 1.0) * half(amount);
}
`;

/** Stamp ink: deep red that skips where the paper's tooth did not take it. */
export const INK_SKSL = `
uniform float4 ink;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float a = smoothstep(0.08, 0.5, h(floor(p * 1.2)) * 0.6 + h(floor(p * 0.25) + 7.0) * 0.6);
  float3 s = ink.rgb;
  float3 l = pow(s, float3(2.2));
  float3 q = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  float3 c = pow(q, float3(1.0 / 2.2));
  float al = ink.a * a;
  return half4(half3(c * al), half(al));
}
`;
