import { BlurStyle, ClipOp, FillType, type SkCanvas, type SkFont, type SkPaint, Skia, TileMode } from '@shopify/react-native-skia';

import { BARS_PER_TURN, type Pose, bar, hash, intensity, isKick } from './motion';

/** The two invented records and everything drawn in the block's canvas, ported from VinylSwapScratchArt.swift. */

type RGB = [number, number, number];

function rgb(hex: number): RGB {
  return [((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255];
}

export type Album = { title: string; artist: string; top: RGB; bottom: RGB; glow: RGB; accent: RGB };

export const ALBUMS: Album[] = [
  { title: 'Slow Satellites', artist: 'The Amber Rooms', top: rgb(0x8c6a1f), bottom: rgb(0x15110a), glow: rgb(0xf2b632), accent: rgb(0xffc845) },
  { title: 'Glass Harbor', artist: 'Noa Lind', top: rgb(0x2f6e7a), bottom: rgb(0x0a1416), glow: rgb(0x7fd1c7), accent: rgb(0x7fe3d6) },
];

function mixRGB(a: RGB, b: RGB, t: number): RGB {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function col(c: RGB, a = 1) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
}

function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function white(a: number) {
  'worklet';
  return Float32Array.of(1, 1, 1, a);
}

/** The screen's geometry, from its size and safe area. */
export type Geo = {
  w: number;
  h: number;
  r: number;
  labelR: number;
  cx: number;
  cy: number;
  px: number;
  py: number;
  armLength: number;
  /** The arm's angle from straight down (positive towards the left), in the groove and swung out. */
  armRest: number;
  armOut: number;
  /** How far a record slides to leave the screen on either side. */
  slide: number;
  stripTop: number;
  titleTop: number;
  controlsTop: number;
};

export const STRIP_H = 56;
/** Where the scripted finger lands, as an angle on the record. */
export const CONTACT = -50;

export function makeGeo(w: number, h: number, bottom: number): Geo {
  const r = 0.56 * w;
  const cx = 0.29 * w;
  const cy = 0.355 * h;
  const px = 0.86 * w;
  const py = 0.165 * h;
  // Where the stylus rests: 80 % out on the record, 35° below its right.
  const a = (35 * Math.PI) / 180;
  const sx = cx + 0.8 * r * Math.cos(a);
  const sy = cy + 0.8 * r * Math.sin(a);
  const armRest = Math.atan2(-(sx - px), sy - py);
  const stripTop = cy + r + 26;
  const titleTop = stripTop + STRIP_H + 28;
  return {
    w,
    h,
    r,
    labelR: 0.42 * r,
    cx,
    cy,
    px,
    py,
    armLength: Math.hypot(sx - px, sy - py),
    armRest,
    armOut: armRest - 0.42,
    slide: Math.max(cx + r + 40, w - cx + r + 40),
    stripTop,
    titleTop,
    controlsTop: Math.max(titleTop + 76, h - Math.max(bottom, 16) - 66),
  };
}

export type Fonts = { heavyA: SkFont; heavyB: SkFont; mono: SkFont; pill: SkFont };

/** Text centred on (x, y), with extra space between letters. */
function text(canvas: SkCanvas, s: string, x: number, y: number, font: SkFont, paint: SkPaint, tracking: number) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let total = tracking * (widths.length - 1);
  for (let i = 0; i < widths.length; i++) total += widths[i];
  const m = font.getMetrics();
  const baseline = y - (m.ascent + m.descent) / 2;
  if (tracking === 0) {
    canvas.drawText(s, x - total / 2, baseline, paint, font);
    return;
  }
  let cx = x - total / 2;
  const chars = Array.from(s);
  for (let i = 0; i < chars.length; i++) {
    canvas.drawText(chars[i], cx, baseline, paint, font);
    cx += (widths[i] ?? 0) + tracking;
  }
}

/** A soft dark shape (Gaussian of sigma). */
function shadowPaint(sigma: number, alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Float32Array.of(0, 0, 0, alpha));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function fill(color: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function stroke(color: Float32Array, width: number) {
  'worklet';
  const p = fill(color);
  p.setStyle(1);
  p.setStrokeWidth(width);
  return p;
}

// Background.

export function drawBackground(canvas: SkCanvas, g: Geo, mix: number) {
  'worklet';
  const a = ALBUMS[0];
  const b = ALBUMS[1];
  const top = mixRGB(a.top, b.top, mix);
  const bottom = mixRGB(a.bottom, b.bottom, mix);
  const glow = mixRGB(a.glow, b.glow, mix);
  const rect = Skia.XYWHRect(0, 0, g.w, g.h);
  const p = Skia.Paint();
  p.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: g.h }, [col(top), col(mixRGB(top, bottom, 0.55)), col(bottom)], [0, 0.45, 0.8], TileMode.Clamp),
  );
  canvas.drawRect(rect, p);
  // The record's label, as a huge blurred light behind it.
  p.setShader(Skia.Shader.MakeRadialGradient({ x: g.cx, y: g.cy }, g.w * 1.05, [col(glow, 0.42), col(glow, 0.12), col(glow, 0)], null, TileMode.Clamp));
  canvas.drawRect(rect, p);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: g.w, y: g.h * 0.62 }, g.w * 0.8, [col(glow, 0.16), col(glow, 0)], null, TileMode.Clamp));
  canvas.drawRect(rect, p);
}

// Record.

/** The label, drawn around the origin of an already rotated canvas. */
function drawLabel(canvas: SkCanvas, album: number, r: number, fonts: Fonts) {
  'worklet';
  canvas.save();
  const clip = Skia.Path.Make();
  clip.addCircle(0, 0, r);
  canvas.clipPath(clip, ClipOp.Intersect, true);
  if (album === 0) {
    canvas.drawCircle(0, 0, r, fill(hexA(0xf2b632)));
    // Sunburst.
    const rays = Skia.Path.Make();
    for (let i = 0; i < 16; i++) {
      const a0 = (i * Math.PI) / 8;
      const a1 = a0 + Math.PI / 16;
      rays.moveTo(0, 0);
      rays.lineTo(2 * r * Math.cos(a0), 2 * r * Math.sin(a0));
      rays.lineTo(2 * r * Math.cos(a1), 2 * r * Math.sin(a1));
      rays.close();
    }
    canvas.drawPath(rays, fill(hexA(0xe0951c)));
    canvas.drawCircle(0, 0, 0.88 * r, stroke(hexA(0x5a1a0b, 0.45), 1));
    canvas.drawCircle(0.37 * r, -0.37 * r, 0.25 * r, fill(hexA(0x8e2a12)));
    text(canvas, 'THE AMBER ROOMS', 0, 0.5 * r, fonts.heavyA, fill(hexA(0x5a1a0b)), 1.2);
    text(canvas, 'SIDE A · 33⅓', 0, 0.68 * r, fonts.mono, fill(hexA(0x5a1a0b, 0.8)), 0);
  } else {
    canvas.drawCircle(0, 0, r, fill(hexA(0xede6d3)));
    const fs = [0.28, 0.5, 0.72, 0.94];
    for (let i = 0; i < fs.length; i++) {
      canvas.drawCircle(-0.28 * r, 0.22 * r, fs[i] * r, stroke(hexA(0x1f6f78, 1 - i * 0.18), r * 0.06));
    }
    canvas.drawCircle(0.38 * r, -0.44 * r, 0.18 * r, fill(hexA(0xff6b4a)));
    text(canvas, 'NOA LIND', 0.18 * r, 0.56 * r, fonts.heavyB, fill(hexA(0x0e3b40)), 2);
    text(canvas, 'SIDE B · 33⅓', 0.18 * r, 0.72 * r, fonts.mono, fill(hexA(0x0e3b40, 0.8)), 0);
  }
  canvas.restore();
  canvas.drawCircle(0, 0, r - 0.5, stroke(Float32Array.of(0, 0, 0, 0.35), 1));
}

export function drawDisc(canvas: SkCanvas, g: Geo, album: number, cx: number, cy: number, angle: number, alpha: number, fonts: Fonts) {
  'worklet';
  if (alpha <= 0.001) return;
  const r = g.r;
  const layer = Skia.Paint();
  layer.setAlphaf(alpha);
  canvas.saveLayer(layer);
  // Shadow on the plinth.
  canvas.drawCircle(cx + 4, cy + 14, r, shadowPaint(18, 0.55));
  canvas.drawCircle(cx, cy, r, fill(hexA(0x0b0b0c)));
  // Grooves: hairlines of alternating brightness, two wider gaps between tracks.
  const inner = g.labelR + 8;
  const outer = r - 7;
  const groove = stroke(white(0.035), 0.7);
  for (let i = 0; i < 46; i++) {
    groove.setColor(white(i % 2 === 0 ? 0.035 : 0.065));
    canvas.drawCircle(cx, cy, inner + ((outer - inner) * i) / 45, groove);
  }
  const gap = stroke(hexA(0x17171a), 3);
  canvas.drawCircle(cx, cy, inner + (outer - inner) * 0.34, gap);
  canvas.drawCircle(cx, cy, inner + (outer - inner) * 0.68, gap);
  canvas.drawCircle(cx, cy, r - 0.75, stroke(white(0.12), 1.5));
  // The sheen: two soft wedges fixed to the light, so the spin reads as gloss.
  const ring = Skia.Path.Make();
  ring.addCircle(cx, cy, r - 2);
  ring.addCircle(cx, cy, g.labelR + 2);
  ring.setFillType(FillType.EvenOdd);
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeSweepGradient(
      cx,
      cy,
      [white(0), white(0.13), white(0), white(0), white(0.09), white(0), white(0)],
      [0, 0.07, 0.17, 0.5, 0.57, 0.67, 1],
      TileMode.Clamp,
    ),
  );
  canvas.save();
  canvas.rotate(-70, cx, cy);
  canvas.drawPath(ring, sheen);
  canvas.restore();
  // Everything below turns with the platter.
  canvas.save();
  canvas.translate(cx, cy);
  canvas.rotate(angle + (album === 1 ? 140 : 0), 0, 0);
  // Dust on the vinyl.
  const dust = fill(white(0.28));
  for (let i = 0; i < 6; i++) {
    const a = i * 1.9 + 0.4;
    const d = g.labelR + 14 + (r - g.labelR - 30) * hash(i, 9);
    canvas.drawCircle(d * Math.cos(a), d * Math.sin(a), 0.8, dust);
  }
  drawLabel(canvas, album, g.labelR, fonts);
  canvas.restore();
  // Spindle.
  const spindle = Skia.Paint();
  spindle.setAntiAlias(true);
  spindle.setShader(Skia.Shader.MakeRadialGradient({ x: cx - 1, y: cy - 1 }, 4.5, [hexA(0xebebeb), hexA(0x808080)], null, TileMode.Clamp));
  canvas.drawCircle(cx, cy, 4.5, spindle);
  canvas.drawCircle(cx, cy, 1.6, fill(hexA(0x333333)));
  canvas.restore();
}

// Tonearm.

export function drawArm(canvas: SkCanvas, g: Geo, out: number, lift: number) {
  'worklet';
  const theta = g.armRest + (g.armOut - g.armRest) * out;
  const len = g.armLength;
  const base = 0.095 * g.w;
  const px = g.px;
  const py = g.py;
  // The pivot's round base.
  canvas.drawCircle(px + 3, py + 8, base, shadowPaint(10, 0.45));
  const plate = Skia.Paint();
  plate.setAntiAlias(true);
  plate.setShader(
    Skia.Shader.MakeRadialGradient({ x: px - base * 0.3, y: py - base * 0.4 }, base * 1.3, [hexA(0x2c2c2f), hexA(0x141416)], null, TileMode.Clamp),
  );
  canvas.drawCircle(px, py, base, plate);
  canvas.drawCircle(px, py, base - 0.5, stroke(white(0.09), 1));

  const deg = (theta * 180) / Math.PI;
  const s = 1 + 0.04 * lift;
  const rr = (x: number, y: number, w: number, h: number, r: number) => Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
  // The arm's shadow: the same outline in black, offset and softened as it rises.
  const sp = shadowPaint(3 + 6 * lift, 0.4 - 0.12 * lift);
  canvas.save();
  canvas.translate(px + 4 + 10 * lift, py + 8 + 16 * lift);
  canvas.rotate(deg, 0, 0);
  canvas.scale(s, s);
  canvas.drawRRect(rr(-10, -50, 20, 36, 4), sp);
  canvas.drawRRect(rr(-3, 0, 6, len - 26, 3), sp);
  canvas.drawRRect(rr(6, len - 30, 14, 3, 1.5), sp);
  canvas.drawRRect(rr(-8, len - 34, 16, 36, 3), sp);
  canvas.drawCircle(0, 0, 13, sp);
  canvas.restore();

  canvas.save();
  canvas.translate(px, py);
  canvas.rotate(deg, 0, 0);
  canvas.scale(s, s);
  // Counterweight.
  const weight = Skia.Paint();
  weight.setAntiAlias(true);
  weight.setShader(Skia.Shader.MakeLinearGradient({ x: -10, y: 0 }, { x: 10, y: 0 }, [hexA(0x3a3a3e), hexA(0x76767c), hexA(0x2a2a2d)], null, TileMode.Clamp));
  canvas.drawRRect(rr(-10, -50, 20, 36, 4), weight);
  const groove = fill(Float32Array.of(0, 0, 0, 0.5));
  canvas.drawRect(Skia.XYWHRect(-10, -42, 20, 1.2), groove);
  canvas.drawRect(Skia.XYWHRect(-10, -24, 20, 1.2), groove);
  // Tube.
  const tube = Skia.Paint();
  tube.setAntiAlias(true);
  tube.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: -3, y: 0 },
      { x: 3, y: 0 },
      [hexA(0x86868c), hexA(0xf5f5f7), hexA(0xa6a6ab), hexA(0x5e5e63)],
      [0, 0.4, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(rr(-3, 0, 6, len - 26, 3), tube);
  // Headshell, with its finger lift.
  canvas.drawRRect(rr(6, len - 30, 14, 3, 1.5), fill(hexA(0x8a8a90)));
  const shell = Skia.Paint();
  shell.setAntiAlias(true);
  shell.setShader(Skia.Shader.MakeLinearGradient({ x: -8, y: 0 }, { x: 8, y: 0 }, [hexA(0x2a2a2e), hexA(0x111113)], null, TileMode.Clamp));
  canvas.drawRRect(rr(-8, len - 34, 16, 36, 3), shell);
  canvas.drawRRect(rr(-8, len - 34, 16, 36, 3), stroke(white(0.12), 0.8));
  canvas.drawCircle(0, len - 6.4, 1.6, fill(hexA(0xffb13b)));
  // Pivot cap.
  const cap = Skia.Paint();
  cap.setAntiAlias(true);
  cap.setShader(Skia.Shader.MakeRadialGradient({ x: -4, y: -5 }, 16, [hexA(0xf2f2f4), hexA(0x7c7c82), hexA(0x3a3a3e)], null, TileMode.Clamp));
  canvas.drawCircle(0, 0, 13, cap);
  canvas.drawCircle(0, 0, 4.5, fill(hexA(0x1a1a1c)));
  canvas.restore();
}

// Waveform.

export function drawWaveform(canvas: SkCanvas, g: Geo, p: Pose) {
  'worklet';
  const x0 = 24;
  const w = g.w - 48;
  const top = g.stripTop;
  const h = STRIP_H;
  const strip = Skia.RRectXY(Skia.XYWHRect(x0, top, w, h), 14, 14);
  canvas.drawRRect(strip, fill(Float32Array.of(0, 0, 0, 0.26)));
  canvas.drawRRect(strip, stroke(white(0.07), 1));
  const pitch = 6;
  const pos = (p.angle / 360) * BARS_PER_TURN;
  const mid = x0 + w / 2;
  const speed = (p.speed / 360) * BARS_PER_TURN * pitch;
  const k0 = intensity(p.speed);
  const smear = Math.min(9, Math.max(0, Math.abs(speed) - 70) * 0.06);
  const accent = mixRGB(ALBUMS[0].accent, ALBUMS[1].accent, p.mix);
  const loud = 0.12 + 0.88 * p.needle;
  const n = Math.floor(w / 2 / pitch) + 2;
  const first = Math.floor(pos) - n;
  canvas.save();
  canvas.clipRRect(strip, ClipOp.Intersect, true);
  const paint = fill(white(1));
  for (let k = first; k <= first + 2 * n + 1; k++) {
    const x = mid + (k - pos) * pitch;
    if (x < x0 + 6 || x > x0 + w - 6) continue;
    const edge = Math.min(1, Math.min(x - x0 - 6, x0 + w - 6 - x) / 26);
    const hv = bar(0, k) + (bar(1, k) - bar(0, k)) * p.waveMix;
    const bh = 4 + (h - 16) * hv * loud;
    const played = k < pos;
    let color = white((played ? 0.92 : 0.32) * edge);
    if (isKick(k) && played) color = col(accent, edge);
    if (k0 > 0) color = played || isKick(k) ? col(accent, edge * (0.92 + 0.08 * k0)) : col(accent, edge * (0.32 + 0.4 * k0));
    // Bars smear behind their motion when the record is pushed fast.
    const bw = 3 + smear;
    const bx = speed >= 0 ? x - 1.5 : x + 1.5 - bw;
    if (smear > 0) color[3] *= Math.min(1, 3 / bw + 0.25);
    paint.setColor(color);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, top + h / 2 - bh / 2, bw, bh), 1.5, 1.5), paint);
  }
  // Playhead.
  canvas.drawRect(Skia.XYWHRect(mid - 0.75, top + 6, 1.5, h - 12), fill(white(1)));
  canvas.restore();
  canvas.drawCircle(mid, top, 3, fill(white(1)));
}

// Hand.

export function drawScratchMarks(canvas: SkCanvas, g: Geo, p: Pose) {
  'worklet';
  const k = intensity(p.speed);
  if (k <= 0.01) return;
  const paint = stroke(white(1), 1.4);
  paint.setStrokeCap(1);
  for (let j = 0; j < 5; j++) {
    const flick = 0.5 + 0.5 * Math.sin(p.clock * 47 + j * 2.3);
    const rad = g.r + 8 + (j % 3) * 6;
    const mid = CONTACT + (j - 2) * 16 + p.fingerTurn * 0.4;
    paint.setColor(white(0.7 * k * flick));
    const arc = Skia.Path.Make();
    arc.addArc(Skia.XYWHRect(g.cx - rad, g.cy - rad, rad * 2, rad * 2), mid - 7, 14);
    canvas.drawPath(arc, paint);
  }
}

export function drawFinger(canvas: SkCanvas, g: Geo, p: Pose) {
  'worklet';
  if (p.fingerAlpha <= 0.001) return;
  const a = ((CONTACT + p.fingerTurn) * Math.PI) / 180;
  const x = g.cx + 0.72 * g.r * Math.cos(a);
  const y = g.cy + 0.72 * g.r * Math.sin(a);
  const r = 22 * p.fingerScale;
  canvas.drawCircle(x, y, r, fill(white(0.2 * p.fingerAlpha)));
  canvas.drawCircle(x, y, r - 0.75, stroke(white(0.6 * p.fingerAlpha), 1.5));
}

// Controls: the three pills are drawn here; the screen puts invisible buttons over them.

export const PILLS = [
  { label: 'PAUSE', width: 72 },
  { label: 'PREV', width: 60 },
  { label: 'NEXT', width: 60 },
];

/** Each pill's left edge. */
export function pillX(g: Geo, i: number) {
  'worklet';
  const right = g.w - 24;
  return i === 0 ? 24 : i === 1 ? right - 60 - 12 - 60 : right - 60;
}

export function drawControls(canvas: SkCanvas, g: Geo, press: number[], pressed: number[], playing: boolean, font: SkFont) {
  'worklet';
  const ink = fill(white(0.92));
  for (let i = 0; i < 3; i++) {
    const w = PILLS[i].width;
    const x = pillX(g, i);
    const y = g.controlsTop;
    const p = press[i];
    canvas.save();
    // The whole pill and its label shrink while held.
    const held = pressed[i] > 0 ? 0.94 : 1;
    canvas.translate(x + w / 2, y + 28);
    canvas.scale(held, held);
    canvas.translate(-(x + w / 2), -(y + 28));
    canvas.save();
    const s = 1 - 0.08 * p;
    canvas.translate(x + w / 2, y + 18);
    canvas.scale(s, s);
    canvas.translate(-(x + w / 2), -(y + 18));
    const rr = Skia.RRectXY(Skia.XYWHRect(x, y, w, 36), 18, 18);
    canvas.drawRRect(rr, fill(white(0.22 * p)));
    canvas.drawRRect(rr, fill(Float32Array.of(0, 0, 0, 0.34 - 0.14 * p)));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, y + 0.5, w - 1, 35), 17.5, 17.5), stroke(white(0.1 + 0.2 * p), 1));
    const cx = x + w / 2;
    const cy = y + 18;
    const tri = (x0: number, x1: number) => {
      const path = Skia.Path.Make();
      path.moveTo(cx + x0, cy - 6);
      path.lineTo(cx + x1, cy);
      path.lineTo(cx + x0, cy + 6);
      path.close();
      canvas.drawPath(path, ink);
    };
    if (i === 0 && playing) {
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 5, cy - 6, 3.5, 12), 1, 1), ink);
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx + 1.5, cy - 6, 3.5, 12), 1, 1), ink);
    } else if (i === 0) {
      tri(-4.5, 6);
    } else if (i === 1) {
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 6, cy - 6, 2.5, 12), 1, 1), ink);
      tri(6, -3.5);
    } else {
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx + 3.5, cy - 6, 2.5, 12), 1, 1), ink);
      tri(-6, 3.5);
    }
    canvas.restore();
    const label = i === 0 ? (playing ? 'PAUSE' : 'PLAY') : PILLS[i].label;
    text(canvas, label, cx, y + 36 + 8 + 6, font, fill(white(0.55)), 1.2);
    canvas.restore();
  }
}
