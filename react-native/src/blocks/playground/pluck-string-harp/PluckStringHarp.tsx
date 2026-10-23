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
  type SkPath,
  type SkPicture,
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
import { type EdgeInsets, useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  displacement,
  envelope,
  fingerDown,
  fingerMove,
  fingerUp,
  type HarpGeometry,
  type HarpSim,
  makeHarpSim,
  NOTE_LIFE,
  NOTE_NAMES,
  NOTE_POOL,
  STRINGS,
  stepHarp,
  TRAIL,
  TRAIL_LIFE,
} from './PluckStringHarpSim';

type Props = {
  /** Plays an arpeggio, a strum down and a strum up, on a loop. Off by default. */
  autoPlay?: boolean;
  /** The small line above the harp, and the hint below it. */
  title?: string;
  hint?: string;
};

// Palette: a dark room, warm varnished wood, gilt, and strings that glow like filaments.
const ROOM = ['#22170f', '#0e0a07', '#050403'];
const WOOD = '#5b3419';
const WOOD_LIGHT = '#c98f55';
const WOOD_DARK = '#1d0e05';
const GRAIN = '#2a1407';
const GILT = '#d6ad66';
const STRING_IDLE = '#a8804f';
const STRING_HOT = '#fff4df';
const GLOW = '#ffb257';
const GLOW_CORE = '#ffd49a';
const NOTE_INK = '#ffe8c4';
const LABEL = '#b08a5a';

// Strings span this share of the width, the lowest on the left.
const STRING_LEFT = 0.27;
const STRING_RIGHT = 0.8;
// Thickness of the lowest and highest string (pt).
const GAUGE_LOW = 2.6;
const GAUGE_HIGH = 1.35;
// Points sampled along each string when it is drawn.
const STRING_SAMPLES = 30;
// Glow around a sounding string: a wide haze and a tight core.
const HAZE_WIDTH = 16;
const HAZE_BLUR = 11;
const CORE_WIDTH = 5;
const CORE_BLUR = 3.5;
// Note names rise this far (pt) over their life.
const NOTE_RISE = 74;
const NOTE_SIZE = 26;

type Point = { x: number; y: number };
/** A band of wood: its centre line, a width at each point, and the unit normal there. */
type Ribbon = { centre: Point[]; width: number[]; normal: Point[]; path: SkPath };

type Layout = {
  geometry: HarpGeometry;
  frame: SkPicture;
  centreX: number;
  centreY: number;
};

function cubic(p0: Point, p1: Point, p2: Point, p3: Point, t: number): Point {
  const u = 1 - t;
  return {
    x: u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
    y: u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y,
  };
}

/** A band along `centre`, `width(t)` wide, with normals turned towards `up` (−1 up, +1 down). */
function ribbon(centre: Point[], width: (t: number) => number, up: number): Ribbon {
  const n = centre.length;
  const normal: Point[] = [];
  const widths: number[] = [];
  for (let i = 0; i < n; i += 1) {
    const a = centre[Math.max(0, i - 1)];
    const b = centre[Math.min(n - 1, i + 1)];
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const len = Math.hypot(dx, dy) || 1;
    let nx = -dy / len;
    let ny = dx / len;
    if (ny * up < 0 || (ny === 0 && nx * up < 0)) {
      nx = -nx;
      ny = -ny;
    }
    normal.push({ x: nx, y: ny });
    widths.push(width(i / (n - 1)));
  }
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < n; i += 1) {
    const p = offset(centre[i], normal[i], widths[i] / 2);
    if (i === 0) builder.moveTo(p.x, p.y);
    else builder.lineTo(p.x, p.y);
  }
  for (let i = n - 1; i >= 0; i -= 1) {
    const p = offset(centre[i], normal[i], -widths[i] / 2);
    builder.lineTo(p.x, p.y);
  }
  builder.close();
  return { centre, width: widths, normal, path: builder.build() };
}

function offset(p: Point, n: Point, d: number): Point {
  return { x: p.x + n.x * d, y: p.y + n.y * d };
}

/** A line along the band at `f` (−1 one edge … 1 the other), wobbling by `wave` of the width. */
function alongRibbon(r: Ribbon, f: number, wave = 0, phase = 0, from = 0, to = 1): SkPath {
  const builder = Skia.PathBuilder.Make();
  const n = r.centre.length;
  const first = Math.round(from * (n - 1));
  const last = Math.round(to * (n - 1));
  for (let i = first; i <= last; i += 1) {
    const t = i / (n - 1);
    const g = f + wave * Math.sin(t * 9 + phase) + wave * 0.6 * Math.sin(t * 23 + phase * 2.3);
    const p = offset(r.centre[i], r.normal[i], (r.width[i] / 2) * g);
    if (i === first) builder.moveTo(p.x, p.y);
    else builder.lineTo(p.x, p.y);
  }
  return builder.build();
}

/** y of a polyline at `x` (the polyline runs left to right). */
function yAt(points: Point[], x: number) {
  for (let i = 0; i < points.length - 1; i += 1) {
    const a = points[i];
    const b = points[i + 1];
    if ((x >= a.x && x <= b.x) || (x <= a.x && x >= b.x)) {
      const t = b.x === a.x ? 0 : (x - a.x) / (b.x - a.x);
      return a.y + (b.y - a.y) * t;
    }
  }
  return points[points.length - 1].y;
}

function paintOf(color: string, alpha = 1) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  return p;
}

function strokeOf(color: string, width: number, alpha = 1, blur = 0) {
  const p = paintOf(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

/** One piece of the frame: varnished wood with a lit edge, a shaded edge and grain that follows it. */
function drawWood(canvas: SkCanvas, r: Ribbon, seed: number) {
  canvas.drawPath(r.path, paintOf(WOOD));
  canvas.save();
  canvas.clipPath(r.path, ClipOp.Intersect, true);
  // Shade on the far side, light on the near side: the piece reads as round.
  canvas.drawPath(alongRibbon(r, 0.85), strokeOf(WOOD_DARK, 16, 0.85, 9));
  canvas.drawPath(alongRibbon(r, -0.55), strokeOf(WOOD_LIGHT, 9, 0.42, 7));
  // Grain: thin lines that drift across the band like the figure of the wood.
  for (let k = 0; k < 9; k += 1) {
    const f = -0.85 + (k / 8) * 1.7;
    canvas.drawPath(alongRibbon(r, f, 0.05, seed + k * 1.7), strokeOf(GRAIN, 0.8, 0.32));
  }
  for (let k = 0; k < 4; k += 1) {
    const f = -0.6 + k * 0.32;
    canvas.drawPath(alongRibbon(r, f, 0.07, seed * 2 + k), strokeOf(WOOD_LIGHT, 0.6, 0.12));
  }
  // The varnish: a narrow specular line along the lit side.
  canvas.drawPath(alongRibbon(r, -0.42), strokeOf('#ffe2b8', 1.3, 0.38, 1));
  canvas.restore();
  canvas.drawPath(r.path, strokeOf(WOOD_DARK, 1, 0.7));
}

/** A round carved boss with gilt rings, like the scroll at the top of a harp's column. */
function drawScroll(canvas: SkCanvas, c: Point, radius: number) {
  const light = Skia.Paint();
  light.setAntiAlias(true);
  light.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(c.x - radius * 0.32, c.y - radius * 0.36),
      radius * 1.25,
      [Skia.Color('#cf9a60'), Skia.Color(WOOD), Skia.Color(WOOD_DARK)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(c.x, c.y, radius, light);
  canvas.drawCircle(c.x, c.y, radius, strokeOf(WOOD_DARK, 1, 0.7));
  canvas.drawCircle(c.x, c.y, radius * 0.66, strokeOf(GILT, 1.4, 0.9));
  canvas.drawCircle(c.x, c.y, radius * 0.36, strokeOf(GILT, 1.1, 0.7));
  drawPin(canvas, c.x, c.y, Math.max(2.4, radius * 0.17));
}

/** A brass tuning pin or bridge pin, lit from the top left. */
function drawPin(canvas: SkCanvas, x: number, y: number, radius: number) {
  const shadow = paintOf('#000000', 0.55);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  canvas.drawCircle(x + 1.2, y + 2, radius + 0.6, shadow);
  const brass = Skia.Paint();
  brass.setAntiAlias(true);
  brass.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(x - radius * 0.35, y - radius * 0.4),
      radius * 1.4,
      [Skia.Color('#fff3cf'), Skia.Color('#d9a957'), Skia.Color('#5e3d14')],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(x, y, radius, brass);
}

/** Lays out the harp for a screen and paints everything that never moves into one picture. */
function layoutHarp(width: number, height: number, insets: EdgeInsets): Layout {
  const top = insets.top + 62;
  const bottom = height - insets.bottom - 64;
  const span = bottom - top;
  const W = width;

  // The neck: a swan curve from the top of the pillar down to the shoulder.
  const neckLine: Point[] = [];
  const n0 = { x: W * 0.115, y: top + span * 0.08 };
  const n1 = { x: W * 0.4, y: top - span * 0.07 };
  const n2 = { x: W * 0.62, y: top + span * 0.33 };
  const n3 = { x: W * 0.925, y: top + span * 0.35 };
  for (let i = 0; i <= 80; i += 1) neckLine.push(cubic(n0, n1, n2, n3, i / 80));
  const neck = ribbon(neckLine, (t) => 38 - 14 * t + 7 * Math.sin(t * Math.PI), 1);

  // The soundbox: a tapered box rising from the foot to the shoulder.
  const boxLine: Point[] = [];
  const b0 = { x: W * 0.185, y: bottom - span * 0.035 };
  // The top of the box tucks under the end of the neck, so the two read as one joint.
  const b1 = { x: n3.x - 6, y: n3.y + 8 };
  for (let i = 0; i <= 40; i += 1) {
    const t = i / 40;
    // A slight belly, so the box is not a ruler.
    const bow = Math.sin(t * Math.PI) * span * 0.018;
    boxLine.push({ x: b0.x + (b1.x - b0.x) * t + bow * 0.7, y: b0.y + (b1.y - b0.y) * t + bow });
  }
  const box = ribbon(boxLine, (t) => 74 - 46 * t, -1);

  // The pillar: a column from the foot up to the neck, swelling at the base and the crown.
  const pillarLine: Point[] = [];
  const p0 = { x: W * 0.13, y: bottom + 4 };
  const p1 = { x: W * 0.11, y: top + span * 0.09 };
  for (let i = 0; i <= 40; i += 1) {
    const t = i / 40;
    pillarLine.push({ x: p0.x + (p1.x - p0.x) * t, y: p0.y + (p1.y - p0.y) * t });
  }
  const pillar = ribbon(pillarLine, (t) => 22 + 14 * Math.pow(1 - t, 6) + 8 * Math.pow(t, 8), 1);

  // The strings hang from the underside of the neck to the face of the soundbox.
  const neckUnder = neck.centre.map((p, i) => offset(p, neck.normal[i], neck.width[i] * 0.32));
  const boxFace = box.centre.map((p, i) => offset(p, box.normal[i], box.width[i] * 0.34));
  const xs: number[] = [];
  const tops: number[] = [];
  const bottoms: number[] = [];
  for (let i = 0; i < STRINGS; i += 1) {
    const x = W * (STRING_LEFT + ((STRING_RIGHT - STRING_LEFT) * i) / (STRINGS - 1));
    xs.push(x);
    tops.push(yAt(neckUnder, x));
    bottoms.push(yAt(boxFace, x));
  }

  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const room = Skia.Paint();
  room.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(W * 0.52, top + span * 0.42),
      Math.max(width, height) * 0.78,
      ROOM.map((c) => Skia.Color(c)),
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), room);

  // One soft shadow for the whole frame, cast down and to the right.
  const shadow = paintOf('#000000', 0.7);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
  canvas.save();
  canvas.translate(10, 18);
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(-20, -20, width + 40, bottom - 20), ClipOp.Intersect, true);
  canvas.drawPath(box.path, shadow);
  canvas.restore();
  canvas.drawPath(pillar.path, shadow);
  canvas.drawPath(neck.path, shadow);
  canvas.restore();

  // The box is cut flat where it stands on the plinth.
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(0, 0, width, bottom - 2), ClipOp.Intersect, true);
  drawWood(canvas, box, 1.3);
  // Gilt along the soundboard, where the strings land.
  canvas.drawPath(alongRibbon(box, 0.62, 0, 0, 0.04, 0.97), strokeOf(GILT, 1.3, 0.85));
  canvas.drawPath(alongRibbon(box, -0.7, 0, 0, 0.05, 0.95), strokeOf(GILT, 0.8, 0.35));
  canvas.restore();
  drawWood(canvas, pillar, 4.1);
  // Gilt rings on the column.
  for (const t of [0.07, 0.12, 0.9]) {
    const i = Math.round(t * (pillar.centre.length - 1));
    const c = pillar.centre[i];
    const half = pillar.width[i] / 2 + 0.5;
    const ringPaint = strokeOf(GILT, 2, 0.9);
    canvas.drawLine(c.x - half, c.y, c.x + half, c.y, ringPaint);
  }
  drawWood(canvas, neck, 7.7);
  canvas.drawPath(alongRibbon(neck, -0.1, 0, 0, 0.06, 0.96), strokeOf(GILT, 1.1, 0.8));

  // Carved scrolls where the neck meets the column and the shoulder.
  drawScroll(canvas, neck.centre[0], 22);
  drawScroll(canvas, neck.centre[neck.centre.length - 1], 14);

  // The plinth the column and the box stand on.
  const foot = Skia.RRectXY(Skia.XYWHRect(W * 0.045, bottom - 4, W * 0.3, 20), 6, 6);
  const footPaint = Skia.Paint();
  footPaint.setAntiAlias(true);
  footPaint.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(0, bottom - 4),
      vec(0, bottom + 16),
      [Skia.Color('#9a6434'), Skia.Color(WOOD), Skia.Color(WOOD_DARK)],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(foot, footPaint);
  canvas.drawLine(W * 0.045 + 6, bottom - 2.5, W * 0.345 - 6, bottom - 2.5, strokeOf(GILT, 1.2, 0.8));

  for (let i = 0; i < STRINGS; i += 1) {
    drawPin(canvas, xs[i], tops[i] - 1, 3.6);
    // The eyelet in the soundboard.
    canvas.drawCircle(xs[i], bottoms[i] + 1, 3.4, paintOf('#120904'));
    canvas.drawCircle(xs[i], bottoms[i] + 1, 3.4, strokeOf(GILT, 1, 0.75));
  }

  return {
    geometry: { x: xs, top: tops, bottom: bottoms },
    frame: recorder.finishRecordingAsPicture(),
    centreX: (xs[0] + xs[STRINGS - 1]) / 2,
    centreY: (tops[0] + bottoms[0]) / 2,
  };
}

function mixColor(a: number[], b: number[], t: number) {
  'worklet';
  return Skia.Color(
    `rgba(${Math.round(a[0] + (b[0] - a[0]) * t)}, ${Math.round(a[1] + (b[1] - a[1]) * t)}, ${Math.round(a[2] + (b[2] - a[2]) * t)}, 1)`,
  );
}

function hexRgb(hex: string) {
  const v = parseInt(hex.slice(1), 16);
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
}

function blurred(color: string, alpha: number, width: number, blur: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

/** Draws every string, its glow and its vibration, the floating notes and the finger's light. */
function drawLive(
  canvas: SkCanvas,
  sim: HarpSim,
  g: HarpGeometry,
  font: SkFont,
  noteWidths: number[],
  idle: number[],
  hot: number[],
  centreX: number,
  centreY: number,
  still: boolean,
) {
  'worklet';
  // The room warms with the music.
  let energy = 0;
  for (let i = 0; i < STRINGS; i += 1) energy += sim.heat[i];
  energy = Math.min(1, energy / 3);
  if (energy > 0.005) {
    const warm = Skia.Paint();
    warm.setShader(
      Skia.Shader.MakeRadialGradient(
        vec(centreX, centreY),
        300,
        [Skia.Color('rgba(255, 170, 80, 0.16)'), Skia.Color('rgba(255, 170, 80, 0)')],
        null,
        TileMode.Clamp,
      ),
    );
    warm.setAlphaf(energy);
    canvas.drawCircle(centreX, centreY, 300, warm);
  }

  for (let i = 0; i < STRINGS; i += 1) {
    const x = g.x[i];
    const y0 = g.top[i];
    const length = g.bottom[i] - y0;
    const heat = sim.heat[i];
    const line = Skia.PathBuilder.Make();
    let widest = 0;
    for (let k = 0; k <= STRING_SAMPLES; k += 1) {
      const s = k / STRING_SAMPLES;
      const px = x + displacement(sim, i, s);
      const py = y0 + length * s;
      if (k === 0) line.moveTo(px, py);
      else line.lineTo(px, py);
      widest = Math.max(widest, envelope(sim, i, s));
    }
    const path = line.build();

    // The blur of a vibrating string: the whole width it sweeps, faintly lit.
    if (widest > 0.5) {
      const band = Skia.PathBuilder.Make();
      for (let k = 0; k <= STRING_SAMPLES; k += 1) {
        const s = k / STRING_SAMPLES;
        const e = envelope(sim, i, s);
        if (k === 0) band.moveTo(x + e, y0);
        else band.lineTo(x + e, y0 + length * s);
      }
      for (let k = STRING_SAMPLES; k >= 0; k -= 1) {
        const s = k / STRING_SAMPLES;
        band.lineTo(x - envelope(sim, i, s), y0 + length * s);
      }
      band.close();
      const fill = Skia.Paint();
      fill.setAntiAlias(true);
      fill.setColor(Skia.Color(GLOW));
      fill.setAlphaf(0.2 * Math.min(1, widest / 5));
      fill.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
      canvas.drawPath(band.build(), fill);
    }
    if (heat > 0.01) {
      canvas.drawPath(path, blurred(GLOW, 0.3 * heat, HAZE_WIDTH, HAZE_BLUR));
      canvas.drawPath(path, blurred(GLOW_CORE, 0.8 * heat, CORE_WIDTH, CORE_BLUR));
    }
    const gauge = GAUGE_LOW + ((GAUGE_HIGH - GAUGE_LOW) * i) / (STRINGS - 1);
    const core = Skia.Paint();
    core.setAntiAlias(true);
    core.setStyle(PaintStyle.Stroke);
    core.setStrokeWidth(gauge);
    core.setStrokeCap(StrokeCap.Round);
    core.setStrokeJoin(StrokeJoin.Round);
    core.setColor(mixColor(idle, hot, Math.min(1, heat * 1.15)));
    canvas.drawPath(path, core);
    // A hairline of light along the string's lit side.
    canvas.save();
    canvas.translate(-gauge * 0.22, 0);
    canvas.drawPath(path, blurred('#fff6e6', 0.18 + 0.55 * heat, 0.7, 0));
    canvas.restore();
  }

  // Note names drifting up from where each string was plucked.
  for (let n = 0; n < NOTE_POOL; n += 1) {
    const age = sim.noteAge[n];
    if (age < 0) continue;
    const t = age / NOTE_LIFE;
    const alpha = Math.min(1, age / 0.12) * Math.pow(1 - t, 1.5);
    const string = sim.noteString[n];
    const name = NOTE_NAMES[string];
    const rise = still ? 0 : NOTE_RISE * easeOut(t);
    const nx = sim.noteX[n] + 16 + (still ? 0 : Math.sin(t * 3 + string) * 5);
    const ny = sim.noteY[n] - 10 - rise;
    const halo = Skia.Paint();
    halo.setColor(Skia.Color(GLOW));
    halo.setAlphaf(alpha * 0.7);
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    canvas.drawText(name, nx - noteWidths[string] / 2, ny, halo, font);
    const ink = Skia.Paint();
    ink.setColor(Skia.Color(NOTE_INK));
    ink.setAlphaf(alpha);
    canvas.drawText(name, nx - noteWidths[string] / 2, ny, ink, font);
  }

  // The finger's light, and a short trail behind it.
  for (let k = 0; k < TRAIL; k += 1) {
    const age = sim.time - sim.trailT[k];
    if (age > TRAIL_LIFE || age < 0) continue;
    const t = age / TRAIL_LIFE;
    const dot = Skia.Paint();
    dot.setColor(Skia.Color(GLOW_CORE));
    dot.setAlphaf(0.32 * (1 - t) * (1 - t));
    dot.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true));
    canvas.drawCircle(sim.trailX[k], sim.trailY[k], 10 * (1 - t * 0.5), dot);
  }
  if (sim.down) {
    const glow = Skia.Paint();
    glow.setColor(Skia.Color(GLOW));
    glow.setAlphaf(0.4);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
    canvas.drawCircle(sim.fx, sim.fy, 20, glow);
    const tip = Skia.Paint();
    tip.setColor(Skia.Color('#fff4e2'));
    tip.setAlphaf(0.85);
    tip.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
    canvas.drawCircle(sim.fx, sim.fy, 5, tip);
  }
}

/**
 * A harp of eight glowing strings on a carved wooden frame, in a dark room. Drag across the
 * strings to pluck them: each one is caught by the finger, pulled aside, and slips free into a
 * ringing standing wave whose glow brightens with its swing. Neighbours shimmer in sympathy, note
 * names float up and fade, and a quick strum leaves a trail of light.
 */
export function PluckStringHarp({ autoPlay = false, title = 'Eight strings · C major', hint = 'Drag across the strings' }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const font = useMemo(
    () => matchFont({ fontFamily: process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif', fontSize: NOTE_SIZE, fontStyle: 'italic' }),
    [],
  );
  const noteWidths = useMemo(() => NOTE_NAMES.map((name) => font.measureText(name).width), [font]);
  const layout = useMemo(
    () => (size ? layoutHarp(size.width, size.height, insets) : null),
    [size, insets],
  );
  const idle = useMemo(() => hexRgb(STRING_IDLE), []);
  const hot = useMemo(() => hexRgb(STRING_HOT), []);

  const sim = useSharedValue<HarpSim>(makeHarpSim(auto, reducedMotion));
  // Plucks already answered with a haptic.
  const answered = useSharedValue(0);

  useEffect(() => {
    sim.modify((s) => {
      'worklet';
      s.looping = auto;
      s.still = reducedMotion;
      s.clock = 0;
      return s;
    });
  }, [auto, reducedMotion, sim]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const geometry = layout?.geometry;
  useFrameCallback((frame) => {
    if (!geometry) return;
    const dt = (frame.timeSincePreviousFrame ?? 16) / 1000;
    sim.modify((s) => {
      'worklet';
      stepHarp(s, geometry, dt);
      return s;
    });
    const plucks = sim.get().userPlucks;
    if (plucks > answered.get()) {
      answered.set(plucks);
      scheduleOnRN(tick);
    }
  });

  // One finger plays: a second finger would move the pan's averaged point and pluck strings at random.
  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      sim.modify((s) => {
        'worklet';
        fingerDown(s, e.x, e.y);
        return s;
      });
    })
    .onUpdate((e) => {
      sim.modify((s) => {
        'worklet';
        fingerMove(s, e.x, e.y);
        return s;
      });
    })
    .onFinalize(() => {
      sim.modify((s) => {
        'worklet';
        fingerUp(s);
        return s;
      });
    });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const centreX = layout?.centreX ?? 0;
  const centreY = layout?.centreY ?? 0;
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (geometry) drawLive(canvas, sim.get(), geometry, font, noteWidths, idle, hot, centreX, centreY, reducedMotion);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel="A harp with eight strings. Drag across the strings to play."
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {layout && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={layout.frame} />
            <Picture picture={picture} />
          </Canvas>
        )}
        <Text style={[styles.title, { top: insets.top + 22 }]}>{title.toUpperCase()}</Text>
        <Text style={[styles.hint, { bottom: insets.bottom + 26 }]}>{hint}</Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: ROOM[2],
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: LABEL,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 2.4,
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: LABEL,
    opacity: 0.75,
    fontFamily: process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif',
    fontStyle: 'italic',
    fontSize: 15,
  },
});
