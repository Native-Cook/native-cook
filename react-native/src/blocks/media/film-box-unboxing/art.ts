import { ClipOp, FillType, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkPaint } from '@shopify/react-native-skia';

import { DIVE_SCALE } from './timeline';

/**
 * The drawn objects: the film box (a cuboid projected in perspective, with two flaps on hinges),
 * the canister (a cylinder drawn in thin strips so its label can turn), and the spinner. The
 * lettering on both is cut from the glyph outlines below, the same in the three stacks.
 */

// The box, in points: long side, height, depth.
export const BOX_W = 150;
export const BOX_H = 96;
export const BOX_D = 84;
/** Distance from the eye to the screen, in points: the strength of the perspective. */
const FOCAL = 700;
/** The box's resting turn: from the left, from above, and tipped up to the right (degrees). */
export const BOX_YAW = -30;
export const BOX_PITCH = 22;
export const BOX_ROLL = -20;
/** The gap between the two closed flaps, showing the inside. */
const SEAM = 0.8;

// The canister, in points: length, radius of the body and of the end caps, length of a cap.
const CAN_LEN = 156;
const CAN_R = 44;
const CAP_R = 48;
const CAP_LEN = 13;
/** How much of an end the eye sees: the width of an end's ellipse over its height. */
const CAN_VIEW = 0.4;
/** The canister lies on a diagonal, its open end up and to the left (degrees). */
export const CAN_TILT = 30;
const STRIPS = 36;
// Where things sit round the canister, in radians from the middle of the label.
const LABEL_FROM = -1.2;
const STRIPE_FROM = 0.8;
const STRIPE_TO = 1.15;
const FILM_STEP = 0.21;
/** "ALIEN" is this many points per glyph unit (a glyph is 10 units tall), centred at TEXT_AT. */
const TEXT_SCALE = 2.6;
const TEXT_AT = -0.2;

// Colours as 0–255 channels, mixed and lit per face.
const KRAFT = [217, 164, 65];
const KRAFT_INSIDE = [104, 72, 30];
const AMBER = [255, 184, 0];
const INK = [22, 18, 12];
const RED = [214, 58, 46];
const PAPER = [246, 241, 228];
const MINT = [168, 230, 192];
const MINT_RIM = [207, 244, 222];
const GLASS = [10, 32, 22];
const SILVER = [233, 236, 234];
const LABEL_STRIPE = [20, 20, 20];
const FILM_DARK = [14, 52, 35];
const FILM_LIGHT = [34, 104, 68];
const FILM_LIME = [124, 214, 155];
const SPINNER = [138, 138, 138];
/** Light falls from the upper left, in front: the direction towards it. */
const LIGHT = [-0.3, -0.8, -0.5];
const LIGHT_LENGTH = Math.hypot(LIGHT[0], LIGHT[1], LIGHT[2]);

// Block capitals, 10 units tall, as outlines (x, y pairs); a second outline is a hole.
const GLYPHS: Record<string, { w: number; c: number[][] }> = {
  A: { w: 8.5, c: [[2.6, 0, 5.9, 0, 8.5, 10, 5.6, 10, 5.2, 8.2, 3.3, 8.2, 2.9, 10, 0, 10], [3.75, 5.8, 4.75, 5.8, 4.25, 3.4]] },
  C: { w: 7.5, c: [[2.4, 0, 7.5, 0, 7.5, 2.6, 3.6, 2.6, 3, 3.2, 3, 6.8, 3.6, 7.4, 7.5, 7.4, 7.5, 10, 2.4, 10, 0, 7.6, 0, 2.4]] },
  E: { w: 6.5, c: [[0, 0, 6.5, 0, 6.5, 2.5, 3, 2.5, 3, 3.8, 6, 3.8, 6, 6.2, 3, 6.2, 3, 7.5, 6.5, 7.5, 6.5, 10, 0, 10]] },
  F: { w: 7, c: [[0, 0, 7, 0, 7, 2.6, 3, 2.6, 3, 4, 6.2, 4, 6.2, 6.4, 3, 6.4, 3, 10, 0, 10]] },
  G: {
    w: 7.5,
    c: [[2.4, 0, 7.5, 0, 7.5, 2.6, 3.6, 2.6, 3, 3.2, 3, 6.8, 3.6, 7.4, 4.8, 7.4, 4.8, 6.4, 4, 6.4, 4, 4.4, 7.5, 4.4, 7.5, 10, 2.4, 10, 0, 7.6, 0, 2.4]],
  },
  I: { w: 3, c: [[0, 0, 3, 0, 3, 10, 0, 10]] },
  L: { w: 6.5, c: [[0, 0, 3, 0, 3, 7.4, 6.5, 7.4, 6.5, 10, 0, 10]] },
  M: { w: 10, c: [[0, 0, 3, 0, 5, 3.6, 7, 0, 10, 0, 10, 10, 7.2, 10, 7.2, 5, 5, 8.4, 2.8, 5, 2.8, 10, 0, 10]] },
  N: { w: 8, c: [[0, 0, 3, 0, 5.2, 4.6, 5.2, 0, 8, 0, 8, 10, 5, 10, 2.8, 5.4, 2.8, 10, 0, 10]] },
  O: {
    w: 8,
    c: [
      [2.4, 0, 5.6, 0, 8, 2.4, 8, 7.6, 5.6, 10, 2.4, 10, 0, 7.6, 0, 2.4],
      [3.4, 2.6, 4.6, 2.6, 5.2, 3.2, 5.2, 6.8, 4.6, 7.4, 3.4, 7.4, 2.8, 6.8, 2.8, 3.2],
    ],
  },
  U: { w: 7.5, c: [[0, 0, 3, 0, 3, 7.2, 4.5, 7.2, 4.5, 0, 7.5, 0, 7.5, 7.6, 5.1, 10, 2.4, 10, 0, 7.6]] },
  '.': { w: 3, c: [[0, 7.2, 3, 7.2, 3, 10, 0, 10]] },
};
const TRACK = 0.8;
const SPACE = 3;
/** Units the top of a letter leans right over its height. */
const ITALIC = 0.2;

/** A word's outlines at (x, y), `scale` points per unit, leaning. */
function lettering(text: string, scale: number, x: number, y: number) {
  const out: number[][] = [];
  let pen = 0;
  for (const ch of text) {
    if (ch === ' ') {
      pen += SPACE;
      continue;
    }
    const glyph = GLYPHS[ch];
    for (const contour of glyph.c) {
      const points: number[] = [];
      for (let i = 0; i < contour.length; i += 2) {
        points.push(x + (pen + contour[i] + (10 - contour[i + 1]) * ITALIC) * scale, y + contour[i + 1] * scale);
      }
      out.push(points);
    }
    pen += glyph.w + TRACK;
  }
  return { contours: out, width: (pen - TRACK) * scale };
}

function rect(x: number, y: number, w: number, h: number) {
  return [x, y, x + w, y, x + w, y + h, x, y + h];
}

function disc(x: number, y: number, r: number) {
  const points: number[] = [];
  for (let i = 0; i < 20; i++) {
    const a = (i / 20) * Math.PI * 2;
    points.push(x + r * Math.cos(a), y + r * Math.sin(a));
  }
  return points;
}

const FRONT = 0;
const LEFT = 2;

// The six faces as corner indices (clockwise seen from outside), the outward normal, and a
// frame for the print on it: origin, then unit vectors along and down the face.
const A = BOX_W / 2;
const B = BOX_H / 2;
const C = BOX_D / 2;
const CORNERS = [
  [-A, -B, -C], [A, -B, -C], [A, B, -C], [-A, B, -C],
  [-A, -B, C], [A, -B, C], [A, B, C], [-A, B, C],
];
const FACES = [
  { idx: [0, 1, 2, 3], n: [0, 0, -1], o: [-A, -B, -C], u: [1, 0, 0], v: [0, 1, 0] },
  { idx: [5, 4, 7, 6], n: [0, 0, 1], o: [A, -B, C], u: [-1, 0, 0], v: [0, 1, 0] },
  { idx: [4, 0, 3, 7], n: [-1, 0, 0], o: [-A, -B, C], u: [0, 0, -1], v: [0, 1, 0] },
  { idx: [1, 5, 6, 2], n: [1, 0, 0], o: [A, -B, -C], u: [0, 0, 1], v: [0, 1, 0] },
  { idx: [3, 2, 6, 7], n: [0, 1, 0], o: [-A, B, -C], u: [1, 0, 0], v: [0, 0, 1] },
];

// The print: on the front a tab, the wordmark, a line of small capitals, the eye badge and a
// barcode sticker; on the left end three lines, a small wordmark and a sticker.
const wordmark = lettering('FILM CO.', 1.9, 28, 36);
const strapline = lettering('ANALOGUE FILM', 0.5, 30, 61);
const sideMark = lettering('FILM CO.', 0.9, 18, 54);
const barcode: number[][] = [];
const BARS = [12.5, 15, 18, 20, 23.5, 26, 29.5];
for (const x of BARS) barcode.push(rect(x, 76, 1.2, 10));
const PRINT = [
  { face: FRONT, color: INK, contours: [rect(10, 12, 9, 38)] },
  { face: FRONT, color: INK, contours: wordmark.contours },
  { face: FRONT, color: INK, contours: strapline.contours },
  { face: FRONT, color: RED, contours: [disc(131, 18, 9.5)] },
  { face: FRONT, color: PAPER, contours: [disc(130.4, 18, 5)] },
  { face: FRONT, color: INK, contours: [disc(131, 18.4, 2.4)] },
  { face: FRONT, color: PAPER, contours: [rect(10, 74, 24, 14)] },
  { face: FRONT, color: INK, contours: barcode },
  { face: LEFT, color: INK, contours: [rect(18, 30, 46, 2.4), rect(18, 36, 46, 2.4), rect(18, 42, 30, 2.4)] },
  { face: LEFT, color: INK, contours: sideMark.contours },
  { face: LEFT, color: PAPER, contours: [rect(18, 72, 22, 14)] },
];

const ALIEN = lettering('ALIEN', 1, 0, 0);

function setRgb(paint: SkPaint, c: number[], light: number, alpha: number) {
  'worklet';
  const r = Math.min(255, c[0] * light);
  const g = Math.min(255, c[1] * light);
  const b = Math.min(255, c[2] * light);
  paint.setColor(new Float32Array([r / 255, g / 255, b / 255, alpha]));
}

function mix(a: number[], b: number[], t: number) {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function polygon(points: number[]) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  builder.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) builder.lineTo(points[i], points[i + 1]);
  builder.close();
  return builder.build();
}

/** The rotation the box is drawn with: roll · pitch · yaw, as nine numbers row by row. */
function rotation(yaw: number, pitch: number, roll: number) {
  'worklet';
  const cy = Math.cos(yaw);
  const sy = Math.sin(yaw);
  const cp = Math.cos(pitch);
  const sp = Math.sin(pitch);
  const cr = Math.cos(roll);
  const sr = Math.sin(roll);
  return [
    cr * cy - sr * sp * sy, -sr * cp, cr * sy + sr * sp * cy,
    sr * cy + cr * sp * sy, cr * cp, sr * sy - cr * sp * cy,
    -cp * sy, sp, cp * cy,
  ];
}

function turn(m: number[], x: number, y: number, z: number) {
  'worklet';
  return [m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z];
}

/** A turned point on the screen, the box centred at (cx, cy). */
function onScreen(p: number[], cx: number, cy: number) {
  'worklet';
  const k = FOCAL / (FOCAL + p[2]);
  return [cx + p[0] * k, cy + p[1] * k];
}

/** True when the outside of a surface through `p` with normal `n` (both turned) faces the eye. */
function facing(n: number[], p: number[]) {
  'worklet';
  return n[0] * p[0] + n[1] * p[1] + n[2] * (p[2] + FOCAL) < 0;
}

function lit(n: number[]) {
  'worklet';
  return Math.max(0, (n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]) / LIGHT_LENGTH);
}

type Surface = { depth: number; points: number[]; color: number[]; light: number; face: number };

/** Sorts far to near, so that nearer surfaces paint over farther ones. */
function sortFar(list: Surface[]) {
  'worklet';
  for (let i = 1; i < list.length; i++) {
    const item = list[i];
    let j = i - 1;
    while (j >= 0 && list[j].depth < item.depth) {
      list[j + 1] = list[j];
      j--;
    }
    list[j + 1] = item;
  }
}

export type BoxPose = {
  /** Degrees added to the resting turn. */
  yaw: number;
  pitch: number;
  roll: number;
  /** Degrees the flaps stand open. */
  flap: number;
  glow: number;
  sweep: number;
  dive: number;
};

/** The box centred at (cx, cy); `dive` zooms into its opening and towards (fx, fy). */
export function drawBox(canvas: SkCanvas, paint: SkPaint, cx: number, cy: number, fx: number, fy: number, pose: BoxPose) {
  'worklet';
  const rad = Math.PI / 180;
  const m = rotation((BOX_YAW + pose.yaw) * rad, (BOX_PITCH + pose.pitch) * rad, (BOX_ROLL + pose.roll) * rad);
  const turned: number[][] = [];
  const screen: number[][] = [];
  for (let i = 0; i < 8; i++) {
    const p = turn(m, CORNERS[i][0], CORNERS[i][1], CORNERS[i][2]);
    turned.push(p);
    screen.push(onScreen(p, cx, cy));
  }
  // The opening: its middle is where the dive goes.
  const top = [0, 1, 5, 4];
  let ox = 0;
  let oy = 0;
  for (let i = 0; i < 4; i++) {
    ox += screen[top[i]][0] / 4;
    oy += screen[top[i]][1] / 4;
  }

  canvas.save();
  if (pose.dive > 0) {
    const scale = 1 + pose.dive * (DIVE_SCALE - 1);
    canvas.translate(ox + (fx - ox) * pose.dive, oy + (fy - oy) * pose.dive);
    canvas.scale(scale, scale);
    canvas.translate(-ox, -oy);
  }
  paint.setStyle(PaintStyle.Fill);
  paint.setShader(null);

  const inside: Surface[] = [];
  const outside: Surface[] = [];
  for (let f = 0; f < FACES.length; f++) {
    const face = FACES[f];
    const n = turn(m, face.n[0], face.n[1], face.n[2]);
    const mid = turn(m, face.n[0] * A, face.n[1] * B, face.n[2] * C);
    const points: number[] = [];
    let depth = 0;
    for (let k = 0; k < 4; k++) {
      const c = face.idx[k];
      points.push(screen[c][0], screen[c][1]);
      depth += turned[c][2] / 4;
    }
    if (facing(n, mid)) outside.push({ depth, points, color: KRAFT, light: 0.58 + 0.5 * lit(n), face: f });
    else {
      // Inside, the floor takes the most of the light that comes out of the box.
      const glow = pose.glow * (f === 4 ? 0.85 : 0.6);
      inside.push({ depth, points, color: mix(KRAFT_INSIDE, AMBER, glow), light: 0.35 + 0.4 * lit([-n[0], -n[1], -n[2]]) + glow, face: -1 });
    }
  }
  sortFar(inside);
  sortFar(outside);

  for (let i = 0; i < inside.length; i++) {
    setRgb(paint, inside[i].color, inside[i].light, 1);
    canvas.drawPath(polygon(inside[i].points), paint);
  }

  const opening = [screen[0][0], screen[0][1], screen[1][0], screen[1][1], screen[5][0], screen[5][1], screen[4][0], screen[4][1]];
  if (pose.glow > 0) {
    canvas.save();
    canvas.clipPath(polygon(opening), ClipOp.Intersect, true);
    paint.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: ox, y: oy },
        BOX_W * 0.6,
        [new Float32Array([1, 0.72, 0, 0.85 * pose.glow]), new Float32Array([1, 0.72, 0, 0])],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawPath(polygon(opening), paint);
    paint.setShader(null);
    canvas.restore();
  }

  const walls = Skia.PathBuilder.Make();
  for (let i = 0; i < outside.length; i++) {
    const s = outside[i];
    setRgb(paint, s.color, s.light, 1);
    canvas.drawPath(polygon(s.points), paint);
    walls.addPoly(
      [
        { x: s.points[0], y: s.points[1] },
        { x: s.points[2], y: s.points[3] },
        { x: s.points[4], y: s.points[5] },
        { x: s.points[6], y: s.points[7] },
      ],
      true,
    );
    // The print on this face, lit like the face.
    const face = FACES[s.face];
    for (let d = 0; d < PRINT.length; d++) {
      const print = PRINT[d];
      if (print.face !== s.face) continue;
      const builder = Skia.PathBuilder.Make();
      for (let c = 0; c < print.contours.length; c++) {
        const contour = print.contours[c];
        for (let k = 0; k < contour.length; k += 2) {
          const u = contour[k];
          const v = contour[k + 1];
          const p = turn(
            m,
            face.o[0] + u * face.u[0] + v * face.v[0],
            face.o[1] + u * face.u[1] + v * face.v[1],
            face.o[2] + u * face.u[2] + v * face.v[2],
          );
          const q = onScreen(p, cx, cy);
          if (k === 0) builder.moveTo(q[0], q[1]);
          else builder.lineTo(q[0], q[1]);
        }
        builder.close();
      }
      builder.setFillType(FillType.EvenOdd);
      setRgb(paint, print.color, Math.min(1.05, s.light), 1);
      canvas.drawPath(builder.build(), paint);
    }
  }

  // A streak of light crosses the walls while the box opens.
  if (pose.sweep > 0 && pose.sweep < 1) {
    let left = screen[0][0];
    let right = screen[0][0];
    for (let i = 1; i < 8; i++) {
      left = Math.min(left, screen[i][0]);
      right = Math.max(right, screen[i][0]);
    }
    const t = pose.sweep;
    const x = left - 40 + (right - left + 80) * (t * t * (3 - 2 * t));
    const alpha = 0.6 * Math.sin(Math.PI * t);
    canvas.save();
    canvas.clipPath(walls.build(), ClipOp.Intersect, true);
    canvas.rotate(20, x, cy);
    paint.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: x - 26, y: 0 },
        { x: x + 26, y: 0 },
        [new Float32Array([1, 0.72, 0, 0]), new Float32Array([1, 0.72, 0, alpha]), new Float32Array([1, 0.72, 0, 0])],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawRect({ x: x - 26, y: cy - 220, width: 52, height: 440 }, paint);
    paint.setShader(null);
    canvas.restore();
  }

  // The flaps, hinged on the long top edges. Closed, they are the lid.
  const a = pose.flap * rad;
  const d = C - SEAM;
  const lift = d * Math.sin(a);
  const reach = d * Math.cos(a);
  const flaps: Surface[] = [];
  const shapes = [
    [[-A, -B, -C], [A, -B, -C], [A, -B - lift, -C + reach], [-A, -B - lift, -C + reach]],
    [[A, -B, C], [-A, -B, C], [-A, -B - lift, C - reach], [A, -B - lift, C - reach]],
  ];
  const normals = [
    [0, -Math.cos(a), -Math.sin(a)],
    [0, -Math.cos(a), Math.sin(a)],
  ];
  for (let f = 0; f < 2; f++) {
    const points: number[] = [];
    let depth = 0;
    let mid = [0, 0, 0];
    for (let k = 0; k < 4; k++) {
      const p = turn(m, shapes[f][k][0], shapes[f][k][1], shapes[f][k][2]);
      const q = onScreen(p, cx, cy);
      points.push(q[0], q[1]);
      depth += p[2] / 4;
      mid = [mid[0] + p[0] / 4, mid[1] + p[1] / 4, mid[2] + p[2] / 4];
    }
    const n = turn(m, normals[f][0], normals[f][1], normals[f][2]);
    if (facing(n, mid)) flaps.push({ depth, points, color: KRAFT, light: 0.58 + 0.5 * lit(n), face: -1 });
    else flaps.push({ depth, points, color: mix(KRAFT_INSIDE, AMBER, pose.glow * 0.4), light: 0.5 + 0.4 * lit([-n[0], -n[1], -n[2]]), face: -1 });
  }
  sortFar(flaps);
  for (let i = 0; i < flaps.length; i++) {
    setRgb(paint, flaps[i].color, flaps[i].light, 1);
    canvas.drawPath(polygon(flaps[i].points), paint);
  }

  // Light spills out of the opening.
  if (pose.glow > 0) {
    paint.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: ox, y: oy },
        BOX_W * 0.9,
        [new Float32Array([1, 0.72, 0, 0.35 * pose.glow]), new Float32Array([1, 0.72, 0, 0])],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawCircle(ox, oy, BOX_W * 0.9, paint);
    paint.setShader(null);
  }
  canvas.restore();
}

/** Wraps an angle into -π…π. */
function wrap(a: number) {
  'worklet';
  const tau = Math.PI * 2;
  return a - tau * Math.round(a / tau);
}

/** What is printed round the canister `s` radians from the middle of the label. */
function surface(s: number) {
  'worklet';
  const a = wrap(s);
  if (a >= LABEL_FROM && a < STRIPE_FROM) return SILVER;
  if (a >= STRIPE_FROM && a < STRIPE_TO) return LABEL_STRIPE;
  // The film behind the window: bands along the axis, every third a bright one.
  let past = a - STRIPE_TO;
  if (past < 0) past += Math.PI * 2;
  const band = Math.floor(past / FILM_STEP);
  if (band % 6 === 3) return FILM_LIME;
  return band % 2 === 0 ? FILM_DARK : FILM_LIGHT;
}

/** Light on the round side, ψ radians from the line facing the eye, with a highlight above. */
function roundLight(psi: number) {
  'worklet';
  return 0.42 + 0.58 * Math.max(0, Math.cos(psi + 0.45));
}

function shine(psi: number) {
  'worklet';
  const k = (psi + 0.75) / 0.2;
  return Math.exp(-k * k);
}

/**
 * A drum from x0 to x1 of radius r, seen a little from its left end, in strips round its front
 * half. `spin` turns the print; `printed` is false for a plain mint cap.
 */
function drum(canvas: SkCanvas, paint: SkPaint, x0: number, x1: number, r: number, spin: number, printed: boolean) {
  'worklet';
  const e = r * CAN_VIEW;
  const step = Math.PI / STRIPS;
  for (let i = 0; i < STRIPS; i++) {
    // Each strip reaches a little into the next, so no seam shows between them.
    const a = -Math.PI / 2 + i * step - 0.01;
    const b = a + step + 0.02;
    const mid = a + step / 2 + 0.01;
    const color = printed ? surface(mid + spin) : MINT;
    const light = roundLight(mid);
    const gloss = shine(mid) * (printed ? 0.55 : 0.4);
    const base = [Math.min(255, color[0] * light), Math.min(255, color[1] * light), Math.min(255, color[2] * light)];
    setRgb(paint, mix(base, [255, 255, 255], gloss), 1, 1);
    const ca = e * Math.cos(a);
    const cb = e * Math.cos(b);
    canvas.drawPath(
      polygon([x0 + ca, r * Math.sin(a), x1 + ca, r * Math.sin(a), x1 + cb, r * Math.sin(b), x0 + cb, r * Math.sin(b)]),
      paint,
    );
  }
}

/** The canister centred at (cx, cy), `spin` radians round its axis. */
export function drawCanister(canvas: SkCanvas, paint: SkPaint, cx: number, cy: number, scale: number, spin: number) {
  'worklet';
  const half = CAN_LEN / 2;
  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(CAN_TILT, 0, 0);
  canvas.scale(scale, scale);
  paint.setStyle(PaintStyle.Fill);

  // A faint mint glow behind it.
  paint.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: 0, y: 0 },
      half + 40,
      [new Float32Array([0.66, 0.9, 0.75, 0.12]), new Float32Array([0.66, 0.9, 0.75, 0])],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(0, 0, half + 40, paint);
  paint.setShader(null);

  drum(canvas, paint, half - CAP_LEN, half, CAP_R, spin, false);
  drum(canvas, paint, -half + CAP_LEN, half - CAP_LEN, CAN_R, spin, true);

  // "ALIEN" wraps round the side: each point goes to its angle on the round surface, folded
  // flat at the edge it turns behind.
  const centre = wrap(TEXT_AT - spin);
  if (Math.cos(centre) > 0.15) {
    const e = CAN_R * CAN_VIEW;
    const builder = Skia.PathBuilder.Make();
    for (let c = 0; c < ALIEN.contours.length; c++) {
      const contour = ALIEN.contours[c];
      const count = contour.length / 2;
      for (let k = 0; k < count; k++) {
        const x0 = contour[k * 2];
        const y0 = contour[k * 2 + 1];
        const x1 = contour[((k + 1) % count) * 2];
        const y1 = contour[((k + 1) % count) * 2 + 1];
        // Three steps along each edge keep the long strokes bent round the drum.
        for (let j = 0; j < 3; j++) {
          const t = j / 3;
          const gx = x0 + (x1 - x0) * t;
          const gy = y0 + (y1 - y0) * t;
          let psi = wrap(TEXT_AT + ((gy - 5) * TEXT_SCALE) / CAN_R - spin);
          psi = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, psi));
          const x = (gx - ALIEN.width / 2) * TEXT_SCALE + e * Math.cos(psi);
          const y = CAN_R * Math.sin(psi);
          if (k === 0 && j === 0) builder.moveTo(x, y);
          else builder.lineTo(x, y);
        }
      }
      builder.close();
    }
    builder.setFillType(FillType.EvenOdd);
    setRgb(paint, INK, 1, 1);
    canvas.drawPath(builder.build(), paint);
  }

  drum(canvas, paint, -half, -half + CAP_LEN, CAP_R, spin, false);

  // The open end: a mint rim round dark glass, and the spool's hub inside.
  const er = CAP_R * CAN_VIEW;
  setRgb(paint, MINT_RIM, 1, 1);
  canvas.drawOval({ x: -half - er, y: -CAP_R, width: er * 2, height: CAP_R * 2 }, paint);
  setRgb(paint, GLASS, 1, 1);
  canvas.drawOval({ x: -half - er * 0.74, y: -CAP_R * 0.74, width: er * 1.48, height: CAP_R * 1.48 }, paint);
  setRgb(paint, MINT, 1, 0.85);
  canvas.drawOval({ x: -half - er * 0.26, y: -CAP_R * 0.26, width: er * 0.52, height: CAP_R * 0.52 }, paint);
  setRgb(paint, GLASS, 1, 1);
  canvas.drawOval({ x: -half - er * 0.1, y: -CAP_R * 0.1, width: er * 0.2, height: CAP_R * 0.2 }, paint);
  canvas.restore();
}

/** Eight ticks round a point, the brightest one stepping round ten times a second. */
export function drawSpinner(canvas: SkCanvas, paint: SkPaint, x: number, y: number, time: number, alpha: number) {
  'worklet';
  const head = Math.floor(time * 10) % 8;
  paint.setShader(null);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1.6);
  paint.setStrokeCap(StrokeCap.Round);
  for (let i = 0; i < 8; i++) {
    const behind = (head - i + 8) % 8;
    const a = (i / 8) * Math.PI * 2 - Math.PI / 2;
    setRgb(paint, SPINNER, 1, alpha * Math.max(0.2, 1 - behind * 0.12));
    canvas.drawLine(x + 3.6 * Math.cos(a), y + 3.6 * Math.sin(a), x + 7.2 * Math.cos(a), y + 7.2 * Math.sin(a), paint);
  }
  paint.setStyle(PaintStyle.Fill);
}
