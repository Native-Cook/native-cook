import { ClipOp, type SkCanvas, type SkFont, Skia } from '@shopify/react-native-skia';

import { EDGE, HALF, INNER, type Pose, ROUND, TABLE, TO_LIGHT, type V3, apply, clamp, project, support, toCamera } from './diceMath';
import { fill, grain, linear, radial, rgba, stroke, textWidth } from './dicePaint';

/*
 The dice table's art. Baked once: the dark games-room background and the walnut tray with its
 green felt. Per frame: each die (its shadow on the felt, a rounded silhouette, the lit faces and
 their concave pips, all projected from 3D), the players' avatars and the result line.
 */

export type Fonts = { initials: SkFont; name: SkFont; range: SkFont; result: SkFont; tag: SkFont };

export type Player = { name: string; initials: string; range: string; sums: number[]; colors: number[] };

export const PLAYERS: Player[] = [
  { name: 'Tomás', initials: 'T', range: '2–3', sums: [2, 3], colors: [0x7ec4cf, 0x3f8ea0, 0x245766] },
  { name: 'June', initials: 'J', range: '4–5', sums: [4, 5], colors: [0xf3b6a0, 0xd77a62, 0x8f4433] },
  { name: 'Kofi', initials: 'K', range: '6', sums: [6], colors: [0xc9d98a, 0x8fa64a, 0x56682a] },
  { name: 'Mira', initials: 'M', range: '7', sums: [7], colors: [0xd3b8f0, 0x9a7cc8, 0x5e4686] },
  { name: 'Elias', initials: 'E', range: '8–9', sums: [8, 9], colors: [0xf2d58a, 0xd0a23f, 0x8a6620] },
  { name: 'Priya', initials: 'P', range: '10–12', sums: [10, 11, 12], colors: [0xf5a9c4, 0xd9668f, 0x8e3258] },
];

export function playerFor(sum: number) {
  'worklet';
  for (let i = 0; i < PLAYERS.length; i += 1) if (PLAYERS[i].sums.indexOf(sum) >= 0) return i;
  return 0;
}

export type Geo = { w: number; h: number; cx: number; cy: number; avatarsTop: number; resultBase: number; ctaTop: number };

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const ctaTop = h - Math.max(bottom, 16) - 8 - 56;
  const avatarsTop = ctaTop - 20 - 92;
  const resultBase = avatarsTop - 22;
  const headerBottom = top + 112;
  // The tray's centre sits midway between the header and the result line, nudged down a little
  // since the far rim draws higher than the near one.
  const cy = (headerBottom + resultBase - 30) / 2 + 26;
  return { w, h, cx: w / 2, cy, avatarsTop, resultBase, ctaTop };
}

// ——— Baked: room, tray, felt ———

function quad(g: Geo, pts: V3[]) {
  const b = Skia.PathBuilder.Make();
  pts.forEach((p, i) => {
    const s = project(g.cx, g.cy, p);
    if (i === 0) b.moveTo(s[0], s[1]);
    else b.lineTo(s[0], s[1]);
  });
  return b.close().build();
}

export function drawRoom(canvas: SkCanvas, g: Geo) {
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), linear(0, 0, 0, g.h, [rgba(0x231c17), rgba(0x1a1512), rgba(0x110e0c)], [0, 0.5, 1]));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), radial(g.cx, g.cy - 40, g.w * 1.1, [rgba(0x6a5236, 0.35), rgba(0x3b2c1f, 0.12), rgba(0x000000, 0)], [0, 0.55, 1]));
  const { x0, x1, y0, y1, rim, wall } = TABLE;
  // Tray: outer top surface at the wall's height, then the inner walls, then the felt.
  const o: V3[] = [
    [x0 - rim, y0 - rim, wall],
    [x1 + rim, y0 - rim, wall],
    [x1 + rim, y1 + rim, wall],
    [x0 - rim, y1 + rim, wall],
  ];
  const near = project(g.cx, g.cy, [0, y0 - rim, 0]);
  canvas.drawPath(quad(g, [[x0 - rim, y0 - rim, 0], [x1 + rim, y0 - rim, 0], [x1 + rim, y0 - rim, wall], [x0 - rim, y0 - rim, wall]]), fill(0x000000, 0.5, 22));
  canvas.drawOval(Skia.XYWHRect(g.cx - g.w * 0.48, near[1] - 6, g.w * 0.96, 30), fill(0x000000, 0.55, 14));
  // Front face of the tray (its outer side, toward the player).
  canvas.drawPath(quad(g, [[x0 - rim, y0 - rim, 0], [x1 + rim, y0 - rim, 0], [x1 + rim, y0 - rim, wall], [x0 - rim, y0 - rim, wall]]), linear(0, near[1] - 18, 0, near[1], [rgba(0x6b3f22), rgba(0x4a2a15), rgba(0x2e1a0c)], [0, 0.5, 1]));
  const topP = quad(g, o);
  const ot = project(g.cx, g.cy, o[3]);
  const ob = project(g.cx, g.cy, o[0]);
  canvas.drawPath(topP, linear(0, ot[1], 0, ob[1], [rgba(0x8a5530), rgba(0x6e3f20), rgba(0x8f5a33), rgba(0x5a3218)], [0, 0.35, 0.7, 1]));
  // Wood grain along the rails.
  canvas.save();
  canvas.clipPath(topP, ClipOp.Intersect, true);
  const grainP = Skia.Paint();
  grainP.setShader(Skia.Shader.MakeFractalNoise(0.006, 0.35, 3, 9, 0, 0));
  grainP.setAlphaf(0.16);
  canvas.drawRect(Skia.XYWHRect(0, ot[1] - 20, g.w, ob[1] - ot[1] + 40), grainP);
  canvas.restore();
  // Inner walls (far and sides), shaded, then the felt.
  canvas.drawPath(quad(g, [[x0, y1, 0], [x1, y1, 0], [x1, y1, wall], [x0, y1, wall]]), linear(0, project(g.cx, g.cy, [0, y1, wall])[1], 0, project(g.cx, g.cy, [0, y1, 0])[1], [rgba(0x5a3519), rgba(0x3a210f)], null));
  canvas.drawPath(quad(g, [[x0, y0, 0], [x0, y1, 0], [x0, y1, wall], [x0, y0, wall]]), fill(0x4d2d15));
  canvas.drawPath(quad(g, [[x1, y0, 0], [x1, y1, 0], [x1, y1, wall], [x1, y0, wall]]), fill(0x2f1b0c));
  const felt = quad(g, [[x0, y0, 0], [x1, y0, 0], [x1, y1, 0], [x0, y1, 0]]);
  const fc = project(g.cx, g.cy, [0, 20, 0]);
  canvas.drawPath(felt, radial(fc[0], fc[1], g.w * 0.62, [rgba(0x2f7a59), rgba(0x22634a), rgba(0x174433)], [0, 0.55, 1]));
  canvas.save();
  canvas.clipPath(felt, ClipOp.Intersect, true);
  const fuzz = Skia.Paint();
  fuzz.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 3, 21, 0, 0));
  fuzz.setAlphaf(0.18);
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fuzz);
  // Shade along the walls: the felt meets the rail in a soft shadow.
  canvas.drawPath(felt, stroke(0x0a1f16, 18, 0.5, 9));
  canvas.restore();
  // Rail highlights: the inner and outer lips catch the lamp.
  const lip = (a: V3, b: V3, c: number, alpha: number) => {
    const p = project(g.cx, g.cy, a);
    const q = project(g.cx, g.cy, b);
    canvas.drawLine(p[0], p[1], q[0], q[1], stroke(c, 1.2, alpha));
  };
  lip([x0, y1, wall], [x1, y1, wall], 0xf2c08a, 0.5);
  lip([x0 - rim, y0 - rim, wall], [x1 + rim, y0 - rim, wall], 0xf2c08a, 0.45);
  lip([x0, y0, wall], [x1, y0, wall], 0x1a0d05, 0.6);
  grain(canvas, 0, 0, g.w, g.h, 0.06, 4);
  // Embossed table name on the near rail.
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), radial(g.cx, g.h * 0.45, g.h * 0.72, [rgba(0x000000, 0), rgba(0x000000, 0.08), rgba(0x000000, 0.5)], [0, 0.65, 1]));
}

// ——— Per frame: dice ———

const PIPS: number[][][] = [
  [],
  [[0, 0]],
  [[-1, -1], [1, 1]],
  [[-1, -1], [0, 0], [1, 1]],
  [[-1, -1], [1, -1], [-1, 1], [1, 1]],
  [[-1, -1], [1, -1], [-1, 1], [1, 1], [0, 0]],
  [[-1, -1], [-1, 0], [-1, 1], [1, -1], [1, 0], [1, 1]],
];
const FACE_POS = [3, 2, 1];
const FACE_NEG = [4, 5, 6];
const IVORY = [0.965, 0.935, 0.86];

function hull(pts: number[][]) {
  'worklet';
  const p = pts.slice().sort((a, b) => (a[0] === b[0] ? a[1] - b[1] : a[0] - b[0]));
  const cross = (o: number[], a: number[], b: number[]) => (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  const lower: number[][] = [];
  for (const q of p) {
    while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], q) <= 0) lower.pop();
    lower.push(q);
  }
  const upper: number[][] = [];
  for (let i = p.length - 1; i >= 0; i -= 1) {
    const q = p[i];
    while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], q) <= 0) upper.pop();
    upper.push(q);
  }
  upper.pop();
  lower.pop();
  const all = lower.concat(upper);
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < all.length; i += 1) {
    if (i === 0) b.moveTo(all[i][0], all[i][1]);
    else b.lineTo(all[i][0], all[i][1]);
  }
  return b.close().build();
}

function shade(n: V3, at: V3, k: number) {
  'worklet';
  const diff = Math.max(0, n[0] * TO_LIGHT[0] + n[1] * TO_LIGHT[1] + n[2] * TO_LIGHT[2]);
  const v = toCamera(at);
  const hx = TO_LIGHT[0] + v[0];
  const hy = TO_LIGHT[1] + v[1];
  const hz = TO_LIGHT[2] + v[2];
  const hl = Math.hypot(hx, hy, hz);
  const spec = Math.pow(Math.max(0, (n[0] * hx + n[1] * hy + n[2] * hz) / hl), 36) * 0.28;
  const l = (0.5 + 0.55 * diff) * k;
  return Float32Array.of(Math.min(1, IVORY[0] * l + spec), Math.min(1, IVORY[1] * l + spec), Math.min(1, IVORY[2] * l + spec * 0.95), 1);
}

/** The die's shadow on the felt (drawn for both dice before either die). */
export function drawDieShadow(canvas: SkCanvas, g: Geo, pose: Pose) {
  'worklet';
  const m = pose.m;
  const zc = pose.hop + support(m);
  const pts: number[][] = [];
  for (let i = 0; i < 8; i += 1) {
    const l: V3 = [i & 1 ? INNER : -INNER, i & 2 ? INNER : -INNER, i & 4 ? INNER : -INNER];
    const r = apply(m, l);
    const z = zc + r[2];
    const s = project(g.cx, g.cy, [pose.x + r[0] - (TO_LIGHT[0] / TO_LIGHT[2]) * z, pose.y + r[1] - (TO_LIGHT[1] / TO_LIGHT[2]) * z, 0]);
    pts.push([s[0], s[1]]);
  }
  const lift = clamp(pose.hop / 90, 0, 1);
  const shadow = hull(pts);
  canvas.drawPath(shadow, stroke(0x02100a, ROUND * 2, 0.5 - 0.3 * lift, 6 + lift * 10));
  canvas.drawPath(shadow, fill(0x02100a, 0.55 - 0.35 * lift, 6 + lift * 10));
}

export function drawDie(canvas: SkCanvas, g: Geo, pose: Pose) {
  'worklet';
  const m = pose.m;
  const zc = pose.hop + support(m);
  const C: V3 = [pose.x, pose.y, zc];
  const pc = project(g.cx, g.cy, C);
  const pts: number[][] = [];
  for (let i = 0; i < 8; i += 1) {
    const r = apply(m, [i & 1 ? INNER : -INNER, i & 2 ? INNER : -INNER, i & 4 ? INNER : -INNER]);
    const s = project(g.cx, g.cy, [C[0] + r[0], C[1] + r[1], C[2] + r[2]]);
    pts.push([s[0], s[1]]);
  }
  // Rounded silhouette: the inner cube's outline, stroked round by the corner radius.
  const sil = hull(pts);
  const edge = shade([0, -0.35, 0.94], C, 0.82);
  const sp = Skia.Paint();
  sp.setAntiAlias(true);
  sp.setColor(edge);
  canvas.drawPath(sil, sp);
  const ss = stroke(0, ROUND * 2 * pc[2], 1);
  ss.setColor(edge);
  canvas.drawPath(sil, ss);
  for (let k = 0; k < 3; k += 1) {
    for (let sgn = -1; sgn <= 1; sgn += 2) {
      const col: V3 = [m[k] * sgn, m[3 + k] * sgn, m[6 + k] * sgn];
      const fcW: V3 = [C[0] + col[0] * HALF, C[1] + col[1] * HALF, C[2] + col[2] * HALF];
      const view = toCamera(fcW);
      const facing = col[0] * view[0] + col[1] * view[1] + col[2] * view[2];
      if (facing <= 0.01) continue;
      const ku = (k + 1) % 3;
      const kv = (k + 2) % 3;
      const u: V3 = [m[ku], m[3 + ku], m[6 + ku]];
      const v: V3 = [m[kv], m[3 + kv], m[6 + kv]];
      const corner = (a: number, b: number) => project(g.cx, g.cy, [fcW[0] + (u[0] * a + v[0] * b) * INNER, fcW[1] + (u[1] * a + v[1] * b) * INNER, fcW[2] + (u[2] * a + v[2] * b) * INNER]);
      const q = [corner(-1, -1), corner(1, -1), corner(1, 1), corner(-1, 1)];
      const face = Skia.PathBuilder.Make().moveTo(q[0][0], q[0][1]).lineTo(q[1][0], q[1][1]).lineTo(q[2][0], q[2][1]).lineTo(q[3][0], q[3][1]).close().build();
      const lit = shade(col, fcW, 1);
      const fp = Skia.Paint();
      fp.setAntiAlias(true);
      fp.setColor(lit);
      canvas.drawPath(face, fp);
      // The flat face blends into the rounded edge: a soft stroke in the face's own colour.
      const blend = stroke(0, ROUND * 0.9 * pc[2], 0.55, ROUND * 0.35);
      blend.setColor(Float32Array.of(lit[0], lit[1], lit[2], 0.55));
      canvas.drawPath(face, blend);
      // Pips: a projected circle each, darker floor offset away from the light (concave).
      const value = sgn > 0 ? FACE_POS[k] : FACE_NEG[k];
      const layout = PIPS[value];
      const spread = EDGE * 0.245;
      const pr = EDGE * (value === 1 ? 0.13 : 0.082);
      const lu = TO_LIGHT[0] * u[0] + TO_LIGHT[1] * u[1] + TO_LIGHT[2] * u[2];
      const lv = TO_LIGHT[0] * v[0] + TO_LIGHT[1] * v[1] + TO_LIGHT[2] * v[2];
      const alpha = clamp((facing - 0.01) / 0.25, 0, 1);
      for (let p = 0; p < layout.length; p += 1) {
        const a = layout[p][0] * spread;
        const b = layout[p][1] * spread;
        const o: V3 = [fcW[0] + u[0] * a + v[0] * b, fcW[1] + u[1] * a + v[1] * b, fcW[2] + u[2] * a + v[2] * b];
        const p0 = project(g.cx, g.cy, o);
        const pu = project(g.cx, g.cy, [o[0] + u[0] * pr, o[1] + u[1] * pr, o[2] + u[2] * pr]);
        const pv = project(g.cx, g.cy, [o[0] + v[0] * pr, o[1] + v[1] * pr, o[2] + v[2] * pr]);
        canvas.save();
        canvas.concat(Skia.Matrix([pu[0] - p0[0], pv[0] - p0[0], p0[0], pu[1] - p0[1], pv[1] - p0[1], p0[1], 0, 0, 1]));
        const red = value === 1;
        canvas.drawCircle(0, 0, 1, fill(red ? 0x9e1f1a : 0x2b1d17, alpha));
        canvas.drawCircle(-lu * 0.28, -lv * 0.28, 0.78, fill(red ? 0x6e100c : 0x140c09, alpha));
        canvas.drawCircle(lu * 0.42, lv * 0.42, 0.45, fill(red ? 0xd65a4c : 0x6b5a4c, alpha * 0.35, 0.2));
        canvas.restore();
      }
    }
  }
  // A thin bright rim on the lit side of the silhouette.
  const rim = stroke(0xfffaf0, 1, 0.22);
  canvas.save();
  canvas.translate(-0.6, -0.8);
  canvas.drawPath(sil, rim);
  canvas.restore();
}

// ——— Per frame: avatars and the result line ———

/** pop: per player 0 … 1.3 (spring overshoot); the chosen one also wears the "Starts" tag. */
export function drawAvatars(canvas: SkCanvas, g: Geo, f: Fonts, pop: number[], chosen: number) {
  'worklet';
  const n = PLAYERS.length;
  const cell = (g.w - 32) / n;
  const r = Math.min(24, cell * 0.4);
  const cy = g.avatarsTop + r + 14;
  for (let i = 0; i < n; i += 1) {
    const p = PLAYERS[i];
    const x = 16 + cell * (i + 0.5);
    const k = pop[i];
    const s = 1 + 0.16 * k;
    const lift = 6 * k;
    canvas.save();
    canvas.translate(x, cy - lift);
    canvas.scale(s, s);
    canvas.drawCircle(0, 3 + lift * 0.6, r, fill(0x000000, 0.35 + 0.1 * k, 4 + 6 * k));
    if (k > 0.01) canvas.drawCircle(0, 0, r + 3.5, stroke(0xf2c46b, 2.2, clamp(k, 0, 1)));
    canvas.drawCircle(0, 0, r, radial(-r * 0.3, -r * 0.4, r * 1.4, [rgba(p.colors[0]), rgba(p.colors[1]), rgba(p.colors[2])], [0, 0.55, 1]));
    canvas.drawCircle(0, 0, r - 0.5, stroke(0xffffff, 1, 0.25));
    const iw = textWidth(f.initials, p.initials);
    canvas.drawText(p.initials, -iw / 2, 6.5, fill(0xfffaf2, 0.95), f.initials);
    canvas.restore();
    const nw = textWidth(f.name, p.name);
    canvas.drawText(p.name, x - nw / 2, cy + r + 20, fill(0xf3eadb, 0.6 + 0.4 * clamp(k, 0, 1)), f.name);
    const rw = textWidth(f.range, p.range);
    canvas.drawText(p.range, x - rw / 2, cy + r + 36, fill(0xf3eadb, 0.38), f.range);
    if (i === chosen && pop[i] > 0.02) {
      const a = clamp(pop[i], 0, 1);
      const tw = textWidth(f.tag, 'STARTS') + 14;
      const ty = cy - r * s - lift - 26 + (1 - a) * 8;
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - tw / 2, ty, tw, 18), 9, 9), fill(0xf2c46b, a));
      canvas.drawText('STARTS', x - tw / 2 + 7, ty + 12.5, fill(0x3a2610, a), f.tag);
    }
  }
}

/** The result line: rolls from the prompt (k = 0) to "11 · Priya starts" (k = 1). */
export function drawResult(canvas: SkCanvas, g: Geo, f: Fonts, text: string, k: number) {
  'worklet';
  const lines = ['Flick the dice to pick who starts', text];
  const travel = 22;
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(0, g.resultBase - 26, g.w, 34), ClipOp.Intersect, true);
  for (let j = 0; j < 2; j += 1) {
    const a = 1 - Math.min(1, Math.abs(j - k));
    if (a <= 0.01) continue;
    const font = j === 0 ? f.name : f.result;
    const w = textWidth(font, lines[j]);
    canvas.drawText(lines[j], g.cx - w / 2, g.resultBase + (j - k) * travel, fill(j === 0 ? 0xf3eadb : 0xfff4dc, j === 0 ? a * 0.6 : a), font);
  }
  canvas.restore();
}
