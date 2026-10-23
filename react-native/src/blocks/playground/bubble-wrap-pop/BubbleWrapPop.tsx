import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkRuntimeEffect,
  StrokeCap,
  StrokeJoin,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  createSheet,
  EVENT_POP,
  EVENT_SWAP,
  POP_LIFE,
  type Sheet,
  sheetCancelScripted,
  sheetDown,
  sheetMove,
  sheetUp,
  sheetUserFingers,
  stepSheet,
} from './BubbleWrapPopSheet';

type Props = {
  /** Pops a smiley, sweeps the rest, and lets a fresh sheet slide in, on a loop. */
  autoPlay?: boolean;
  /** Called with the running count after each pop by hand. */
  onPop?: (popped: number, total: number) => void;
};

// Layout: the sheet sits SIDE pt from the screen's sides, between the counter and the hint.
const SIDE = 18;
const HEADER = 118;
const FOOTER = 64;
const SHEET_RADIUS = 26;

// The backdrop: pastel light pooling behind the film, drifting once per LOOP.
const INK = '#2A2433';
const INK_SOFT = '#7A6F86';
// The collapse from dome to flat film after a pop, s.
const COLLAPSE = 0.12;
// The air rings: AIR_RINGS per pop, thrown at AIR_SPEED pt/s and slowed with AIR_DRAG s.
const AIR_RINGS = 7;
const AIR_SPEED = 170;
const AIR_DRAG = 0.15;
const SHOCK_TIME = 0.24;

// autoPlay (seconds): eight presses pop a smiley, a finger sweeps every row, and once the sheet
// is empty a fresh one slides in before the loop ends.
const LOOP = 12;
const SMILE_AT = 0.5;
const SMILE_STEP = 0.6;
const PRESS_HOLD = 0.56;
const SWEEP_AT = 5.75;
const SWEEP_ROW = 0.3;
const SCRIPT_ID = 1000;
const USER_HOLD = 3;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

const BACKDROP_SKSL = `
uniform float2 res;
uniform float phase;
float blob(float2 p, float2 c, float r) { float2 d = p - c; return exp(-dot(d, d) / (r * r)); }
half4 main(float2 p) {
  float2 uv = p / res.y;
  float ax = res.x / res.y;
  float3 col = float3(0.99, 0.958, 0.93);
  col = mix(col, float3(1.0, 0.75, 0.62), 0.9 * blob(uv, float2(ax * (0.16 + 0.08 * sin(phase)), 0.2 + 0.05 * cos(phase)), 0.3));
  col = mix(col, float3(0.98, 0.62, 0.76), 0.8 * blob(uv, float2(ax * (0.88 + 0.06 * cos(phase)), 0.4 + 0.06 * sin(phase)), 0.29));
  col = mix(col, float3(0.72, 0.66, 1.0), 0.85 * blob(uv, float2(ax * (0.28 + 0.07 * cos(phase)), 0.68 + 0.05 * sin(phase)), 0.34));
  col = mix(col, float3(0.6, 0.83, 1.0), 0.8 * blob(uv, float2(ax * (0.82 + 0.05 * sin(phase)), 0.88 + 0.04 * cos(phase)), 0.3));
  float g = fract(sin(dot(floor(p * 2.0), float2(12.9898, 78.233))) * 43758.5453) - 0.5;
  return half4(half3(col + g * 0.02), 1.0);
}`;

// Crinkles in the film: long, thin crease highlights from stretched value noise.
const CRINKLE_SKSL = `
uniform float strength;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float2 q = float2(p.x * 0.82 + p.y * 0.57, -p.x * 0.57 + p.y * 0.82);
  float n = noise(q * float2(0.06, 0.008)) * 0.7 + noise(q * float2(0.02, 0.03) + 7.0) * 0.3;
  float ridge = pow(1.0 - abs(n * 2.0 - 1.0), 14.0);
  float a = ridge * strength;
  return half4(a, a, a, a);
}`;

type Stroke = { at: number; dur: number; pts: number[] };

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

function easeInOut(t: number) {
  'worklet';
  const x = clamp01(t);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function hash(a: number, b: number) {
  'worklet';
  const v = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
  return v - Math.floor(v);
}

function white(a: number) {
  'worklet';
  return new Float32Array([1, 1, 1, a]);
}

function plum(a: number) {
  'worklet';
  return new Float32Array([0.25, 0.16, 0.36, a]);
}

/** Where autoPlay's fingers go on a sheet at rest: a smiley of presses, then a sweep. */
function buildScript(s: Sheet): Stroke[] {
  'worklet';
  const w = s.right - s.left;
  const h = s.bottom - s.top;
  const targets = [
    [0.33, 0.3],
    [0.67, 0.3],
  ];
  for (let k = 0; k < 6; k += 1) {
    const a = ((20 + k * 28) * Math.PI) / 180;
    targets.push([0.5 - Math.cos(a) * 0.27, 0.52 + Math.sin(a) * 0.2]);
  }
  const used: number[] = [];
  const out: Stroke[] = [];
  for (let k = 0; k < targets.length; k += 1) {
    let best = -1;
    let bestD = Infinity;
    for (let i = 0; i < s.count; i += 1) {
      if (used.indexOf(i) >= 0) continue;
      const d = Math.hypot(s.bx[i] - targets[k][0] * w, s.by[i] - targets[k][1] * h);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    used.push(best);
    out.push({ at: SMILE_AT + k * SMILE_STEP, dur: PRESS_HOLD, pts: [s.left + s.bx[best], s.top + s.by[best]] });
  }
  // The sweep: from the first bubble of the top row, across each row and down, side to side.
  const rows = s.rowOf[s.count - 1] + 1;
  const pts: number[] = [];
  const x0 = s.left + s.bx[0] - s.radius * 0.6;
  const x1 = s.left + s.bx[s.count - 1] + s.radius * 0.6;
  for (let r = 0; r < rows; r += 1) {
    const y = s.top + s.by[r * 6];
    const leftToRight = r % 2 === 0;
    pts.push(leftToRight ? x0 : x1, y, leftToRight ? x1 : x0, y);
  }
  out.push({ at: SWEEP_AT, dur: rows * SWEEP_ROW, pts });
  return out;
}

/** A point along a stroke's polyline, `u` of the way by length. */
function along(pts: number[], u: number) {
  'worklet';
  const n = pts.length / 2;
  if (n === 1) return { x: pts[0], y: pts[1] };
  let total = 0;
  for (let i = 1; i < n; i += 1) total += Math.hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1]);
  let d = clamp01(u) * total;
  for (let i = 1; i < n; i += 1) {
    const l = Math.hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1]);
    if (d <= l || i === n - 1) {
      const t = l > 0 ? Math.min(1, d / l) : 0;
      return { x: pts[2 * i - 2] + (pts[2 * i] - pts[2 * i - 2]) * t, y: pts[2 * i - 1] + (pts[2 * i + 1] - pts[2 * i - 1]) * t };
    }
    d -= l;
  }
  return { x: pts[pts.length - 2], y: pts[pts.length - 1] };
}

/** An intact dome: its shadow and caustic are drawn first, under the film, by `drawUnder`. */
function drawUnder(canvas: SkCanvas, x: number, y: number, r: number, squash: number) {
  'worklet';
  const sh = Skia.Paint();
  sh.setColor(plum(0.13 * (1 - squash * 0.5)));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.5, true));
  canvas.drawCircle(x + r * 0.12, y + r * 0.2, r * (0.96 + squash * 0.12), sh);
  // Light focused by the dome onto what is behind it.
  const caustic = Skia.Paint();
  caustic.setColor(white(0.42 * (1 - squash)));
  caustic.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  canvas.drawOval(Skia.XYWHRect(x + r * 0.05, y + r * 0.32, r * 0.7, r * 0.34), caustic);
}

function drawDome(
  canvas: SkCanvas,
  backdrop: SkRuntimeEffect,
  uniforms: number[],
  x: number,
  y: number,
  r: number,
  squash: number,
  leanX: number,
  leanY: number,
  alpha: number,
) {
  'worklet';
  // Squashed, the dome flattens and bulges out, away from the finger.
  const height = 1 - 0.6 * squash;
  const grow = 1 + 0.17 * squash;
  const cx = x - leanX * r * 0.14 * squash;
  const cy = y - leanY * r * 0.14 * squash;
  const rx = r * grow * (1 + Math.abs(leanY) * 0.05 * squash);
  const ry = r * grow * (1 + Math.abs(leanX) * 0.05 * squash);
  const oval = Skia.XYWHRect(cx - rx, cy - ry, rx * 2, ry * 2);
  // Through the dome the backdrop is magnified and pushed toward the light.
  const m = 1 + 0.26 * height;
  const lens = Skia.Paint();
  lens.setAntiAlias(true);
  lens.setShader(
    backdrop.makeShader(uniforms, Skia.Matrix().translate(cx + r * 0.18, cy + r * 0.22).scale(m, m).translate(-cx - r * 0.18, -cy - r * 0.22)),
  );
  lens.setAlphaf(alpha);
  canvas.drawOval(oval, lens);
  // The plastic is thicker toward the rim and catches more light there.
  const fresnel = Skia.Paint();
  fresnel.setShader(
    Skia.Shader.MakeRadialGradient(vec(cx, cy), Math.max(rx, ry), [white(0.03 * alpha), white(0.06 * alpha), white(0.34 * alpha), white(0.1 * alpha)], [0, 0.6, 0.93, 1], TileMode.Clamp),
  );
  canvas.drawOval(oval, fresnel);
  // Rim: lit on the upper left, shadowed on the lower right.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.1);
  rim.setShader(
    Skia.Shader.MakeLinearGradient(vec(cx - rx, cy - ry), vec(cx + rx, cy + ry), [white(0.75 * alpha), white(0.12 * alpha), plum(0.16 * alpha)], [0, 0.6, 1], TileMode.Clamp),
  );
  canvas.drawOval(Skia.XYWHRect(cx - rx + 0.6, cy - ry + 0.6, rx * 2 - 1.2, ry * 2 - 1.2), rim);
  // Pressure shows as a tight bright band along the rim.
  if (squash > 0.02) {
    const band = Skia.Paint();
    band.setAntiAlias(true);
    band.setStyle(PaintStyle.Stroke);
    band.setStrokeWidth(2.2);
    band.setColor(white(0.4 * squash * alpha));
    band.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.4, true));
    canvas.drawOval(Skia.XYWHRect(cx - rx + 2, cy - ry + 2, rx * 2 - 4, ry * 2 - 4), band);
  }
  // Specular: a sharp window on the upper left that flattens and slides as the dome squashes,
  // and a small glint low on the right.
  canvas.save();
  canvas.translate(cx - rx * (0.36 + 0.12 * squash), cy - ry * (0.4 - 0.1 * squash));
  canvas.rotate(-36, 0, 0);
  const spec = Skia.Paint();
  spec.setAntiAlias(true);
  spec.setColor(white(0.9 * alpha * (0.55 + 0.45 * height)));
  spec.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.9, true));
  const sw = r * (0.42 + 0.2 * squash);
  const shh = r * 0.2 * height;
  canvas.drawOval(Skia.XYWHRect(-sw / 2, -shh / 2, sw, shh), spec);
  canvas.restore();
  const glint = Skia.Paint();
  glint.setColor(white(0.55 * alpha * height));
  glint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2, true));
  canvas.drawCircle(cx + rx * 0.4, cy + ry * 0.42, r * 0.08, glint);
}

/** A popped bubble: the film it leaves, crumpled flat with a few creases. */
function drawWrinkle(canvas: SkCanvas, x: number, y: number, r: number, seed: number, alpha: number) {
  'worklet';
  if (alpha < 0.01) return;
  // A soft, lumpy outline: two seeded waves around the circle, joined with smooth curves.
  const b = Skia.PathBuilder.Make();
  const n = 18;
  const ph1 = hash(seed, 1) * Math.PI * 2;
  const ph2 = hash(seed, 2) * Math.PI * 2;
  const px: number[] = [];
  const py: number[] = [];
  for (let i = 0; i < n; i += 1) {
    const a = (i / n) * Math.PI * 2;
    const rr = r * (0.86 + 0.07 * Math.sin(a * 3 + ph1) + 0.05 * Math.sin(a * 5 + ph2) + 0.03 * (hash(i, seed) - 0.5));
    px.push(x + Math.cos(a) * rr);
    py.push(y + Math.sin(a) * rr);
  }
  b.moveTo((px[n - 1] + px[0]) / 2, (py[n - 1] + py[0]) / 2);
  for (let i = 0; i < n; i += 1) {
    const j = (i + 1) % n;
    b.quadTo(px[i], py[i], (px[i] + px[j]) / 2, (py[i] + py[j]) / 2);
  }
  b.close();
  const film = b.build();
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(
    Skia.Shader.MakeRadialGradient(vec(x - r * 0.2, y - r * 0.25), r * 1.2, [white(0.34 * alpha), white(0.12 * alpha)], null, TileMode.Clamp),
  );
  canvas.drawPath(film, fill);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeJoin(StrokeJoin.Round);
  edge.setStrokeWidth(0.9);
  edge.setColor(white(0.7 * alpha));
  canvas.drawPath(film, edge);
  // Creases: a light line with a faint shadow beside it.
  const crease = Skia.PathBuilder.Make();
  for (let k = 0; k < 5; k += 1) {
    const a = hash(seed + k, 3.1) * Math.PI * 2;
    const from = r * (0.05 + 0.25 * hash(k, seed + 1));
    const to = r * (0.5 + 0.3 * hash(seed + 2, k));
    const bend = (hash(seed, k + 9) - 0.5) * 0.5;
    crease.moveTo(x + Math.cos(a) * from, y + Math.sin(a) * from);
    crease.lineTo(x + Math.cos(a + bend) * (from + to) * 0.55, y + Math.sin(a + bend) * (from + to) * 0.55);
    crease.lineTo(x + Math.cos(a) * to, y + Math.sin(a) * to);
  }
  const lines = crease.build();
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setStyle(PaintStyle.Stroke);
  ink.setStrokeCap(StrokeCap.Round);
  ink.setStrokeJoin(StrokeJoin.Round);
  ink.setStrokeWidth(0.8);
  canvas.save();
  canvas.translate(0.6, 0.7);
  ink.setColor(plum(0.14 * alpha));
  canvas.drawPath(lines, ink);
  canvas.restore();
  ink.setColor(white(0.85 * alpha));
  canvas.drawPath(lines, ink);
}

/** The burst: a shock ring and small rings of air thrown out and fading. */
function drawBurst(canvas: SkCanvas, x: number, y: number, r: number, age: number, seed: number) {
  'worklet';
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  const k = age / SHOCK_TIME;
  if (k < 1) {
    const e = 1 - (1 - k) * (1 - k);
    ring.setStrokeWidth(2.4 * (1 - k) + 0.4);
    ring.setColor(white(0.7 * (1 - k)));
    canvas.drawCircle(x, y, r * (0.85 + 1.25 * e), ring);
  }
  const life = clamp01(age / POP_LIFE);
  if (life >= 1) return;
  const fade = Math.pow(1 - life, 1.4);
  ring.setStrokeWidth(1.1);
  for (let j = 0; j < AIR_RINGS; j += 1) {
    const a = (j / AIR_RINGS) * Math.PI * 2 + hash(seed, j) * 0.8;
    const v = AIR_SPEED * (0.6 + 0.8 * hash(j, seed));
    const d = r * 0.7 + v * AIR_DRAG * (1 - Math.exp(-age / AIR_DRAG));
    const rr = 1.2 + 3.2 * life * (0.6 + 0.6 * hash(seed + j, 2));
    ring.setColor(white(0.85 * fade));
    canvas.drawCircle(x + Math.cos(a) * d, y + Math.sin(a) * d - 10 * life, rr, ring);
  }
}

function drawSheet(
  canvas: SkCanvas,
  s: Sheet,
  backdrop: SkRuntimeEffect,
  uniforms: number[],
  crinkle: SkRuntimeEffect,
  ox: number,
  oy: number,
  /** 1 draws every bubble popped long ago, 2 every bubble whole; 0 the sheet as it is. */
  look: number,
) {
  'worklet';
  const l = s.left + ox;
  const t = s.top + oy;
  const w = s.right - s.left;
  const h = s.bottom - s.top;
  const rect = Skia.RRectXY(Skia.XYWHRect(l, t, w, h), SHEET_RADIUS, SHEET_RADIUS);
  const shadow = Skia.Paint();
  shadow.setColor(plum(0.16));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 20, true));
  canvas.save();
  canvas.translate(0, 14);
  canvas.drawRRect(rect, shadow);
  canvas.restore();
  // Clear film over the backdrop: a little whitening, the backdrop redrawn so the shadow above
  // only shows around the edges.
  canvas.save();
  canvas.clipRRect(rect, ClipOp.Intersect, true);
  const behind = Skia.Paint();
  behind.setShader(backdrop.makeShader(uniforms));
  canvas.drawRect(Skia.XYWHRect(l, t, w, h), behind);
  const r = s.radius;
  for (let i = 0; i < s.count; i += 1) {
    const popped = look === 1 || (look === 0 && s.popped[i]);
    if (!popped) drawUnder(canvas, l + s.bx[i], t + s.by[i], r, look === 0 ? clamp01(s.pressure[i]) : 0);
  }
  const film = Skia.Paint();
  film.setShader(
    Skia.Shader.MakeLinearGradient(vec(l, t), vec(l + w, t + h), [white(0.2), white(0.1), white(0.18)], [0, 0.55, 1], TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(l, t, w, h), film);
  const crease = Skia.Paint();
  crease.setShader(crinkle.makeShader([0.13], Skia.Matrix().translate(l, t)));
  canvas.drawRect(Skia.XYWHRect(l, t, w, h), crease);
  // A broad sheen across the film that slides as the film is tugged.
  const sheen = Skia.Paint();
  const sx = l + w * 0.3 + s.tugX * 2.2;
  sheen.setShader(
    Skia.Shader.MakeLinearGradient(vec(sx - 90, t), vec(sx + 90, t + h * 0.6), [white(0), white(0.16), white(0)], null, TileMode.Clamp),
  );
  canvas.drawRect(Skia.XYWHRect(l, t, w, h), sheen);

  for (let i = 0; i < s.count; i += 1) {
    const x = l + s.bx[i];
    const y = t + s.by[i];
    if (look === 2 || (look === 0 && !s.popped[i])) {
      let squash = look === 0 ? clamp01(s.pressure[i]) : 0;
      // Close to bursting it trembles.
      if (squash > 0.7 && !s.calm) squash += Math.sin(s.time * 95 + i) * 0.04 * ((squash - 0.7) / 0.3);
      drawDome(canvas, backdrop, uniforms, x, y, r, squash, s.leanX[i], s.leanY[i], 1);
      continue;
    }
    const age = look === 1 ? 10 : s.popAge[i];
    const k = clamp01(age / COLLAPSE);
    if (k < 1) drawDome(canvas, backdrop, uniforms, x, y, r * (1 - 0.25 * k), 1, s.leanX[i], s.leanY[i], 1 - k);
    drawWrinkle(canvas, x, y, r, i * 1.7 + 0.3, k);
  }
  // The film's edge.
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1.2);
  edge.setShader(Skia.Shader.MakeLinearGradient(vec(l, t), vec(l, t + h), [white(0.85), white(0.35)], null, TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(l + 0.6, t + 0.6, w - 1.2, h - 1.2), SHEET_RADIUS - 0.6, SHEET_RADIUS - 0.6), edge);
  canvas.restore();
  // Bursts fly past the film's edge.
  if (look === 0) {
    for (let i = 0; i < s.count; i += 1) {
      if (s.popped[i] && s.popAge[i] >= 0 && s.popAge[i] < POP_LIFE) drawBurst(canvas, l + s.bx[i], t + s.by[i], r, s.popAge[i], i * 3.3 + 1);
    }
  }
}

/** The count, as an odometer: each digit rolls through to the next. */
function drawCounter(canvas: SkCanvas, s: Sheet, x: number, baseline: number, big: SkFont, small: SkFont) {
  'worklet';
  const cell = big.measureText('0').width;
  const lineH = 50;
  const shown = Math.max(0, s.shown);
  const paint = Skia.Paint();
  paint.setColor(Skia.Color(INK));
  canvas.save();
  const places = s.count >= 100 ? 3 : 2;
  canvas.clipRect(Skia.XYWHRect(x - 4, baseline - lineH + 6, cell * places + 8, lineH + 2), ClipOp.Intersect, true);
  for (let place = 0; place < places; place += 1) {
    const unit = Math.pow(10, places - 1 - place);
    // A higher digit turns only while the digit below rolls from 9 to 10.
    const below = shown % unit;
    const pos = Math.floor(shown / unit) + (unit > 1 ? Math.max(0, below - (unit - 1)) : 0);
    const whole = Math.floor(pos);
    const frac = pos - whole;
    for (let k = 0; k < 2; k += 1) {
      const digit = (((whole + k) % 10) + 10) % 10;
      const y = baseline - (frac - k) * lineH;
      const glyph = `${digit}`;
      const gw = big.measureText(glyph).width;
      const dx = x + place * cell + (cell - gw) / 2;
      canvas.drawText(glyph, dx, y, paint, big);
    }
  }
  canvas.restore();
  const soft = Skia.Paint();
  soft.setColor(Skia.Color(INK_SOFT));
  canvas.drawText(`/ ${s.count}`, x + cell * places + 10, baseline, soft, small);
}

export function BubbleWrapPop({ autoPlay = false, onPop }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo(
    () => ({
      big: matchFont({ fontFamily: FAMILY, fontSize: 46, fontWeight: '400' }),
      small: matchFont({ fontFamily: FAMILY, fontSize: 15, fontWeight: '400' }),
    }),
    [],
  );
  const backdrop = useMemo(() => Skia.RuntimeEffect.Make(BACKDROP_SKSL)!, []);
  const crinkle = useMemo(() => Skia.RuntimeEffect.Make(CRINKLE_SKSL)!, []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const sheetTop = insets.top + HEADER;
  const sheetBottom = height - insets.bottom - FOOTER;

  const sheet = useSharedValue<Sheet | null>(null);
  const script = useSharedValue<Stroke[]>([]);
  const tick = useSharedValue(0);
  const stage = useSharedValue({ width: 0, height: 0, top: 0, bottom: 0 });
  const elapsed = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const strokeDown = useSharedValue<boolean[]>([]);

  useEffect(() => {
    if (width > 0) stage.set({ width, height, top: sheetTop, bottom: sheetBottom });
  }, [width, height, sheetTop, sheetBottom, stage]);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const popped = (count: number, total: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    onPop?.(count, total);
  };
  const renewed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  useFrameCallback((frame) => {
    // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 1000 / 30) / 1000;
    const st = stage.get();
    if (st.width === 0) return;
    elapsed.set(elapsed.get() + dt);
    let s = sheet.get();
    if (!s || s.width !== st.width || s.top !== st.top || s.bottom !== st.bottom) {
      s = createSheet(st.width, SIDE, st.top, st.width - SIDE, st.bottom, reducedMotion);
      sheet.set(s);
      const sc = buildScript(s);
      script.set(sc);
      const down: boolean[] = [];
      for (let i = 0; i < sc.length; i += 1) down.push(false);
      strokeDown.set(down);
    }
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get()) {
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      const sc = script.get();
      const down = strokeDown.get();
      for (let i = 0; i < sc.length; i += 1) {
        const k = sc[i];
        const u = (t - k.at) / k.dur;
        const p = along(k.pts, u);
        if (u >= 0 && u < 1) {
          if (!down[i]) {
            sheetDown(s, SCRIPT_ID + i, p.x, p.y, true);
            down[i] = true;
          } else sheetMove(s, SCRIPT_ID + i, p.x, p.y);
        } else if (down[i]) {
          sheetUp(s, SCRIPT_ID + i);
          down[i] = false;
        }
      }
    }
    stepSheet(s, dt);
    if (s.events !== 0) {
      if (s.eventsByUser && s.events & EVENT_POP) scheduleOnRN(popped, s.pops, s.count);
      if (s.events & EVENT_SWAP && sheetUserFingers(s) === 0 && hold.get() > 0) scheduleOnRN(renewed);
      s.events = 0;
      s.eventsByUser = false;
    }
    tick.set(tick.get() + 1);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    const s = sheet.get();
    if (!s) return;
    sheetCancelScripted(s);
    const down = strokeDown.get();
    for (let i = 0; i < down.length; i += 1) down[i] = false;
  };

  // A manual gesture: a press must start building the moment the finger lands.
  const touch = Gesture.Manual()
    .onTouchesDown((event, manager) => {
      manager.activate();
      touched();
      const s = sheet.get();
      if (!s) return;
      for (const t of event.changedTouches) sheetDown(s, t.id, t.x, t.y, false);
    })
    .onTouchesMove((event) => {
      const s = sheet.get();
      if (!s) return;
      hold.set(USER_HOLD);
      for (const t of event.changedTouches) sheetMove(s, t.id, t.x, t.y);
    })
    .onTouchesUp((event, manager) => {
      const s = sheet.get();
      if (s) for (const t of event.changedTouches) sheetUp(s, t.id);
      if (!s || sheetUserFingers(s) === 0) manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      const s = sheet.get();
      if (s) for (const t of event.changedTouches) sheetUp(s, t.id);
      manager.end();
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = sheet.get();
    const uniforms = [width, height, ((elapsed.get() % LOOP) / LOOP) * Math.PI * 2];
    // A light camera shake moves the whole scene; the backdrop overhangs the screen for it.
    canvas.save();
    if (s) canvas.translate(s.shakeX, s.shakeY);
    const bg = Skia.Paint();
    bg.setShader(backdrop.makeShader(uniforms));
    canvas.drawRect(Skia.XYWHRect(-12, -12, width + 24, height + 24), bg);
    if (s && width > 0) {
      if (s.swap > 0) {
        const e = easeInOut(s.swap);
        const away = width * 1.1;
        drawSheet(canvas, s, backdrop, uniforms, crinkle, -e * away, 0, 1);
        drawSheet(canvas, s, backdrop, uniforms, crinkle, (1 - e) * away, 0, 2);
      } else {
        drawSheet(canvas, s, backdrop, uniforms, crinkle, s.tugX, s.tugY, 0);
      }
      drawCounter(canvas, s, 24, insets.top + 92, fonts.big, fonts.small);
    }
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  return (
    <View style={[styles.root, { backgroundColor: '#FBF3EA' }]} onLayout={onLayout}>
      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill}>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
        <Text style={styles.kicker}>POPPED</Text>
        <Text style={styles.kicker}>BUBBLE WRAP · A4</Text>
      </View>
      <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 26 }]}>
        Press to pop · Slide across a row · Tug the edge
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', justifyContent: 'space-between' },
  kicker: { color: INK_SOFT, fontSize: 11, fontWeight: '700', letterSpacing: 2.4 },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: INK_SOFT, fontSize: 12, fontWeight: '500', letterSpacing: 0.3 },
});
