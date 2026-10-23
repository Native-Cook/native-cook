import {
  BlurStyle,
  Canvas,
  ClipOp,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  type SkCanvas,
  type SkFont,
  type SkPath,
  TileMode,
  matchFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  AURORA,
  type Frame,
  LOOP,
  MARKED,
  type Mark,
  SLIDES,
  arc,
  autoFrame,
  clamp,
  easeInOut,
  easeOutBack,
  emptyFrame,
  hash,
  smoothstep,
} from './model';

/*
 Light Table Loupe — an original Native Cook design: a glowing light table of 35 mm slides and a loupe
 that magnifies the slide under it. A port of the SwiftUI block (LightTableLoupe.swift); the numbers are
 the same.

 Look: a black header ("CONTACT 04 · 12 FRAMES" in 12 pt mono caps, a red "1 MARKED" at the right when a
 slide is marked). The rest is the lit table: #EEF1F4 at the middle to #CFD6DD at the corners, a fine
 deterministic grain, and a soft sheen that crosses it. Twelve slide mounts in 3 × 4 (cardboard white
 #F7F6F2, 3 pt corners, 82 % as tall as wide, a window 74 % × 64 % of the mount with the frame number and
 "OCT 84" printed under it in 6.5 pt mono; 3 columns 14 pt apart, rows 20 pt apart) each hold a backlit
 transparency drawn as gradient art (model.ts), its colour spilling onto the table. Below the grid, side
 by side and centred in the space left: a slide viewer (a dark body with a 1.4 : 1 screen that projects
 the slide under the loupe, with a faint flicker, or a dim lamp when the gate is empty; its caption under
 it) and the loupe's home, a dashed ring centred on the viewer's middle.

 The loupe: a black housing (128 pt across: a 9 pt ring, a 2 pt chrome lip) over a lens of 52 pt radius
 that shows the table 2.2 × larger about its centre, with grain, a vignette, a red and a blue fringe at the
 rim and a glass highlight; its shadow grows when it is lifted. Landing on a new slide pulls focus: 6 → 0
 pt of blur in 0.25 s. A mark is a red (#E5342C) grease-pencil circle drawn round the slide in 0.4 s; the
 marked slide shifts 3 pt and tilts 1.5°.

 autoPlay: LOOP = 9 s, timeline in model.ts.
 Touch: drag the loupe (grabbed where it was touched; a drag that starts elsewhere brings it there, 64 pt
 above the finger, in 0.25 s); a light haptic at each new slide under it. Tap a slide, or the loupe, to mark
 or unmark the slide (medium haptic). 2 s after the last touch the loupe glides home, the marks are wiped
 and the loop starts again.
 Reduced motion: no loop, no sheen or breathing light, no focus pull; marks appear at once.
 */

const LENS = 52;
const ZOOM = 2.2;
const RED = '#E5342C';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

type Layout = {
  width: number;
  height: number;
  header: number;
  mountW: number;
  mountH: number;
  gapX: number;
  gapY: number;
  gridX: number;
  gridY: number;
  parkX: number;
  parkY: number;
  /** The slide viewer at the lower left, its screen, and where the caption under it starts. */
  viewer: { x: number; y: number; width: number; height: number };
  screen: { x: number; y: number; width: number; height: number };
  captionY: number;
  centers: number[][];
};

function layoutFor(width: number, height: number, top: number, bottomInset: number): Layout {
  const header = top + 40;
  const mountW = Math.floor((width - 40 - 28) / 3);
  const mountH = Math.round(mountW * 0.82);
  const gapX = 14;
  const gapY = 20;
  const gridX = (width - 3 * mountW - 2 * gapX) / 2;
  const gridY = header + 22;
  const gridBottom = gridY + 4 * mountH + 3 * gapY;
  const bottom = height - Math.max(bottomInset, 20);
  // The lower area: the viewer and its caption on the left, the loupe's home on the right, centred
  // together between the grid and the bottom.
  const parkX = width - 20 - 68;
  const vw = parkX - 68 - 16 - 20;
  const sh = Math.round((vw - 20) / 1.4);
  const block = sh + 20 + 10 + 40;
  const vy = gridBottom + 24 + Math.max(0, (bottom - gridBottom - 24 - block) / 2);
  const viewer = { x: 20, y: vy, width: vw, height: sh + 20 };
  const screen = { x: 30, y: vy + 10, width: vw - 20, height: sh };
  const parkY = vy + (sh + 20) / 2;
  const centers: number[][] = [];
  for (let i = 0; i < 12; i++) {
    centers.push([gridX + (i % 3) * (mountW + gapX) + mountW / 2, gridY + Math.floor(i / 3) * (mountH + gapY) + mountH / 2]);
  }
  return { width, height, header, mountW, mountH, gapX, gapY, gridX, gridY, parkX, parkY, viewer, screen, captionY: vy + sh + 30, centers };
}

function originOf(l: Layout, i: number) {
  'worklet';
  return { x: l.gridX + (i % 3) * (l.mountW + l.gapX), y: l.gridY + Math.floor(i / 3) * (l.mountH + l.gapY) };
}

function windowOf(l: Layout, i: number) {
  'worklet';
  const o = originOf(l, i);
  const ww = l.mountW * 0.74;
  const wh = l.mountH * 0.64;
  return Skia.XYWHRect(o.x + (l.mountW - ww) / 2, o.y + l.mountH * 0.12, ww, wh);
}

function slideAt(l: Layout, x: number, y: number) {
  'worklet';
  for (let i = 0; i < 12; i++) {
    const o = originOf(l, i);
    if (x >= o.x - l.gapX / 2 && x <= o.x + l.mountW + l.gapX / 2 && y >= o.y - l.gapY / 2 && y <= o.y + l.mountH + l.gapY / 2) return i;
  }
  return -1;
}

/** Colour with opacity, from #RRGGBB. */
function rgba(hex: string, a: number) {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, Math.min(1, Math.max(0, a)));
}

type Grain = { dark: SkPath; light: SkPath; lensDark: SkPath; lensLight: SkPath };

function art(canvas: SkCanvas, r: { x: number; y: number; width: number; height: number }, index: number) {
  'worklet';
  const s = SLIDES[index];
  const rect = Skia.XYWHRect(r.x, r.y, r.width, r.height);
  canvas.save();
  canvas.clipRect(rect, ClipOp.Intersect, true);
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeLinearGradient({ x: r.x + r.width / 2, y: r.y }, { x: r.x + r.width / 2, y: r.y + r.height }, [rgba(s.top, 1), rgba(s.bottom, 1)], null, TileMode.Clamp));
  canvas.drawRect(rect, p);
  if (index === AURORA) {
    p.setShader(Skia.Shader.MakeRadialGradient({ x: r.x + 0.35 * r.width, y: r.y + 0.4 * r.height }, 0.5 * r.width, [rgba('#38F2A0', 0.55), rgba('#38F2A0', 0)], null, TileMode.Clamp));
    canvas.drawRect(rect, p);
    p.setShader(Skia.Shader.MakeRadialGradient({ x: r.x + 0.72 * r.width, y: r.y + 0.22 * r.height }, 0.42 * r.width, [rgba('#8F5BFF', 0.4), rgba('#8F5BFF', 0)], null, TileMode.Clamp));
    canvas.drawRect(rect, p);
  }
  if (s.sun.r > 0) {
    const cx = r.x + s.sun.x * r.width;
    const cy = r.y + s.sun.y * r.height;
    const rad = s.sun.r * r.width;
    p.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, rad * 3, [rgba(s.sun.color, s.sun.glow), rgba(s.sun.color, 0)], null, TileMode.Clamp));
    canvas.drawRect(rect, p);
    const disc = Skia.Paint();
    disc.setColor(rgba(s.sun.color, 1));
    canvas.drawCircle(cx, cy, rad, disc);
  }
  for (const rd of s.ridges) {
    const path = Skia.Path.Make();
    path.moveTo(r.x, r.y + r.height);
    for (let k = 0; k <= 24; k++) {
      const u = k / 24;
      const y = rd.base + rd.amp * Math.sin(2 * Math.PI * rd.freq * u + rd.phase);
      path.lineTo(r.x + u * r.width, r.y + y * r.height);
    }
    path.lineTo(r.x + r.width, r.y + r.height);
    path.close();
    const f = Skia.Paint();
    f.setColor(rgba(rd.color, 1));
    canvas.drawPath(path, f);
  }
  // Backlight: brighter in the middle, a soft dark edge where the film meets the mount.
  p.setShader(
    Skia.Shader.MakeRadialGradient({ x: r.x + r.width / 2, y: r.y + r.height / 2 }, Math.hypot(r.width, r.height) * 0.5, [Float32Array.of(1, 1, 1, 0.22), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp),
  );
  canvas.drawRect(rect, p);
  const edge = Skia.Paint();
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1.5);
  edge.setColor(Float32Array.of(0, 0, 0, 0.22));
  canvas.drawRect(Skia.XYWHRect(r.x + 0.75, r.y + 0.75, r.width - 1.5, r.height - 1.5), edge);
  edge.setStrokeWidth(3);
  edge.setColor(Float32Array.of(0, 0, 0, 0.08));
  canvas.drawRect(Skia.XYWHRect(r.x + 1.5, r.y + 1.5, r.width - 3, r.height - 3), edge);
  canvas.restore();
}

/** The grease-pencil circle: two rough turns starting at the upper left. */
function markPath(l: Layout, i: number, tilt: number) {
  'worklet';
  const c0 = l.centers[i];
  const cx = c0[0] + 3 * tilt;
  const cy = c0[1] - 2 * tilt;
  const rx = l.mountW / 2 + 9;
  const ry = l.mountH / 2 + 9;
  const p = Skia.Path.Make();
  const n = 72;
  for (let k = 0; k <= n; k++) {
    const th = (2 * Math.PI * 1.12 * k) / n;
    const a = Math.PI * 1.15 + th;
    const j = 1 + 0.035 * Math.sin(3 * th + 0.7) + 0.02 * Math.sin(7 * th + 1.3) + (th / (2 * Math.PI)) * 0.05;
    const x = cx + rx * j * Math.cos(a);
    const y = cy + ry * j * Math.sin(a);
    if (k === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  return p;
}

/** The slide viewer: a dark body, its screen projecting the slide under the loupe. */
function viewer(canvas: SkCanvas, l: Layout, f: Frame) {
  'worklet';
  const v = l.viewer;
  const sc = l.screen;
  const body = Skia.RRectXY(Skia.XYWHRect(v.x, v.y, v.width, v.height), 12, 12);
  const shadow = Skia.Paint();
  shadow.setColor(Float32Array.of(0, 0, 0, 0.25));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  canvas.save();
  canvas.translate(0, 6);
  canvas.drawRRect(body, shadow);
  canvas.restore();
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeLinearGradient({ x: v.x + v.width / 2, y: v.y }, { x: v.x + v.width / 2, y: v.y + v.height }, [rgba('#24272D', 1), rgba('#121418', 1)], null, TileMode.Clamp));
  canvas.drawRRect(body, p);
  const line = Skia.Paint();
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(1);
  line.setColor(Float32Array.of(1, 1, 1, 0.08));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(v.x + 0.5, v.y + 0.5, v.width - 1, v.height - 1), 12, 12), line);
  const screenRect = Skia.XYWHRect(sc.x, sc.y, sc.width, sc.height);
  const screen = Skia.RRectXY(screenRect, 4, 4);
  const flat = Skia.Paint();
  flat.setColor(rgba('#0A0B0D', 1));
  canvas.drawRRect(screen, flat);
  canvas.save();
  canvas.clipRRect(screen, ClipOp.Intersect, true);
  // A dim lamp when the gate is empty.
  p.setShader(Skia.Shader.MakeRadialGradient({ x: sc.x + sc.width / 2, y: sc.y + sc.height / 2 }, sc.width * 0.6, [Float32Array.of(1, 1, 1, 0.1), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp));
  canvas.drawRect(screenRect, p);
  const a = f.focus >= 0 ? f.caption : 0;
  if (a > 0.001) {
    const layer = Skia.Paint();
    layer.setAlphaf(a);
    canvas.saveLayer(layer);
    art(canvas, sc, f.focus);
    canvas.restore();
  }
  // Lamp flicker and the projector's falloff.
  flat.setColor(Float32Array.of(1, 1, 1, 0.01 + 0.01 * f.flicker));
  canvas.drawRect(screenRect, flat);
  p.setShader(
    Skia.Shader.MakeRadialGradient({ x: sc.x + sc.width / 2, y: sc.y + sc.height / 2 }, Math.hypot(sc.width, sc.height) / 2, [Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0.45)], [0, 0.55, 1], TileMode.Clamp),
  );
  canvas.drawRect(screenRect, p);
  canvas.restore();
  line.setColor(rgba('#2E3238', 1));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(sc.x - 0.5, sc.y - 0.5, sc.width + 1, sc.height + 1), 4.5, 4.5), line);
}

function table(canvas: SkCanvas, l: Layout, f: Frame, grain: Grain, font: SkFont) {
  'worklet';
  const panel = Skia.XYWHRect(0, l.header, l.width, l.height - l.header);
  const p = Skia.Paint();
  p.setShader(
    Skia.Shader.MakeRadialGradient({ x: l.width / 2, y: l.header + panel.height / 2 }, Math.hypot(panel.width, panel.height) / 2, [rgba('#EEF1F4', 1), rgba('#CFD6DD', 1)], null, TileMode.Clamp),
  );
  canvas.drawRect(panel, p);
  // The light breathes ±1.5 %.
  const flat = Skia.Paint();
  flat.setColor(Float32Array.of(1, 1, 1, 0.03 + 0.03 * f.pulse));
  canvas.drawRect(panel, flat);
  flat.setColor(Float32Array.of(0, 0, 0, 0.05));
  canvas.drawPath(grain.dark, flat);
  flat.setColor(Float32Array.of(1, 1, 1, 0.5));
  canvas.drawPath(grain.light, flat);
  // Colour spilling through each slide onto the table.
  for (let i = 0; i < 12; i++) {
    const c = l.centers[i];
    const r = l.mountW * 0.85;
    const col = SLIDES[i].bottom;
    p.setShader(Skia.Shader.MakeRadialGradient({ x: c[0], y: c[1] }, r, [rgba(col, 0.16), rgba(col, 0)], null, TileMode.Clamp));
    canvas.drawCircle(c[0], c[1], r, p);
  }
  // The parking ring.
  const ring = Skia.Paint();
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1);
  ring.setColor(rgba('#8E98A3', 0.7));
  ring.setPathEffect(Skia.PathEffect.MakeDash([4, 5], 0));
  canvas.drawCircle(l.parkX, l.parkY, 68, ring);
  viewer(canvas, l, f);
  // Slides.
  const mount = Skia.Paint();
  mount.setColor(rgba('#F7F6F2', 1));
  const shadow = Skia.Paint();
  shadow.setColor(Float32Array.of(0, 0, 0, 0.14));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.25, true));
  const line = Skia.Paint();
  line.setStyle(PaintStyle.Stroke);
  const ink = Skia.Paint();
  ink.setColor(rgba('#8A867C', 1));
  for (let i = 0; i < 12; i++) {
    const o = originOf(l, i);
    const m = f.marks[i];
    const tilt = m ? m.tilt : 0;
    canvas.save();
    if (tilt !== 0) {
      canvas.translate(3 * tilt, -2 * tilt);
      canvas.rotate(1.5 * tilt, o.x + l.mountW / 2, o.y + l.mountH / 2);
    }
    const rr = Skia.RRectXY(Skia.XYWHRect(o.x, o.y, l.mountW, l.mountH), 3, 3);
    canvas.save();
    canvas.translate(0, 1.5);
    canvas.drawRRect(rr, shadow);
    canvas.restore();
    canvas.drawRRect(rr, mount);
    line.setStrokeWidth(0.5);
    line.setColor(Float32Array.of(0, 0, 0, 0.08));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(o.x + 0.25, o.y + 0.25, l.mountW - 0.5, l.mountH - 0.5), 3, 3), line);
    const w = windowOf(l, i);
    line.setStrokeWidth(2);
    line.setColor(rgba('#E2DED3', 1));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(w.x - 1.5, w.y - 1.5, w.width + 3, w.height + 3), 2, 2), line);
    art(canvas, w, i);
    // Printed on the mount.
    const y = o.y + l.mountH - (o.y + l.mountH - (w.y + w.height)) / 2;
    const metrics = font.getMetrics();
    const base = y - (metrics.ascent + metrics.descent) / 2;
    const num = String(i + 1).padStart(2, '0');
    canvas.drawText(num, w.x, base, ink, font);
    const date = 'OCT 84';
    canvas.drawText(date, w.x + w.width - font.measureText(date).width, base, ink, font);
    canvas.restore();
  }
  // Marks.
  const pen = Skia.Paint();
  pen.setStyle(PaintStyle.Stroke);
  pen.setStrokeCap(StrokeCap.Round);
  pen.setStrokeJoin(StrokeJoin.Round);
  for (const key of Object.keys(f.marks)) {
    const i = Number(key);
    const m: Mark = f.marks[i];
    if (m.draw <= 0 || m.alpha <= 0) continue;
    const path = markPath(l, i, m.tilt);
    path.trim(0, m.draw, false);
    pen.setStrokeWidth(3);
    pen.setColor(rgba(RED, 0.9 * m.alpha));
    canvas.drawPath(path, pen);
    canvas.save();
    canvas.translate(0.8, 0.6);
    pen.setStrokeWidth(1.2);
    pen.setColor(rgba(RED, 0.45 * m.alpha));
    canvas.drawPath(path, pen);
    canvas.restore();
  }
  // A soft sheen crossing the glass.
  if (f.sheen >= 0) {
    const x = -0.5 * l.width + 2 * l.width * f.sheen;
    const a = 0.2 * Math.sin(Math.PI * f.sheen);
    p.setShader(
      Skia.Shader.MakeLinearGradient({ x: x - 90, y: l.header }, { x: x + 90, y: l.header + 60 }, [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, a), Float32Array.of(1, 1, 1, 0)], [0, 0.5, 1], TileMode.Clamp),
    );
    canvas.drawRect(panel, p);
  }
}

function drawScene(l: Layout, f: Frame, grain: Grain, font: SkFont) {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.width, l.height));
  table(canvas, l, f, grain, font);
  const s = 1 + 0.04 * f.lift - 0.04 * f.press;
  const cx = f.x;
  const cy = f.y;
  const lens = LENS * s;
  // Shadow, larger and softer when lifted.
  const shadow = Skia.Paint();
  shadow.setColor(Float32Array.of(0, 0, 0, 0.3 + 0.08 * f.lift));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3 + 4 * f.lift, true));
  canvas.drawCircle(cx + 4 + 6 * f.lift, cy + 8 + 10 * f.lift, 64 * s, shadow);
  // The table, 2.2 × larger under the glass, blurred while focus is pulled.
  const clip = Skia.Path.Make();
  clip.addCircle(cx, cy, lens);
  canvas.save();
  canvas.clipPath(clip, ClipOp.Intersect, true);
  if (f.blur > 0.1) {
    const blur = Skia.Paint();
    blur.setImageFilter(Skia.ImageFilter.MakeBlur(f.blur / 2, f.blur / 2, TileMode.Decal, null));
    canvas.saveLayer(blur);
  } else {
    canvas.save();
  }
  canvas.translate(cx, cy);
  canvas.scale(ZOOM, ZOOM);
  canvas.translate(-cx, -cy);
  table(canvas, l, f, grain, font);
  canvas.restore();
  // Film grain, vignette, a glass highlight.
  canvas.save();
  canvas.translate(cx, cy);
  canvas.scale(s, s);
  const flat = Skia.Paint();
  flat.setColor(Float32Array.of(0, 0, 0, 0.12));
  canvas.drawPath(grain.lensDark, flat);
  flat.setColor(Float32Array.of(1, 1, 1, 0.18));
  canvas.drawPath(grain.lensLight, flat);
  canvas.restore();
  const vig = Skia.Paint();
  vig.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, lens, [Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0), Float32Array.of(0, 0, 0, 0.35)], [0, 0.7, 1], TileMode.Clamp));
  canvas.drawCircle(cx, cy, lens, vig);
  canvas.save();
  canvas.translate(cx - 17 * s, cy - 23 * s);
  canvas.rotate(-30, 0, 0);
  const hl = Skia.Paint();
  hl.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -12 * s }, { x: 0, y: 12 * s }, [Float32Array.of(1, 1, 1, 0.32), Float32Array.of(1, 1, 1, 0)], null, TileMode.Clamp));
  canvas.drawOval(Skia.XYWHRect(-23 * s, -12 * s, 46 * s, 24 * s), hl);
  canvas.restore();
  canvas.restore();
  // Colour fringes at the rim.
  const ring = Skia.Paint();
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1.2);
  ring.setColor(Float32Array.of(1, 0.2, 0.2, 0.35));
  canvas.drawCircle(cx - 0.6, cy - 0.2, lens - 0.8, ring);
  ring.setColor(Float32Array.of(0.2, 0.4, 1, 0.35));
  canvas.drawCircle(cx + 0.6, cy - 0.2, lens - 0.8, ring);
  // Chrome lip and black housing.
  ring.setStrokeWidth(2.2);
  ring.setShader(Skia.Shader.MakeLinearGradient({ x: cx - 54, y: cy - 54 }, { x: cx + 54, y: cy + 54 }, [rgba('#F2F2F2', 1), rgba('#7A7A7A', 1)], null, TileMode.Clamp));
  canvas.drawCircle(cx, cy, 53.5 * s, ring);
  ring.setShader(null);
  ring.setStrokeWidth(9);
  ring.setColor(rgba('#141414', 1));
  canvas.drawCircle(cx, cy, 59 * s, ring);
  ring.setStrokeWidth(0.8);
  ring.setShader(Skia.Shader.MakeLinearGradient({ x: cx, y: cy - 64 }, { x: cx, y: cy + 64 }, [rgba('#5A5A5A', 1), rgba('#1A1A1A', 1)], null, TileMode.Clamp));
  canvas.drawCircle(cx, cy, 63.6 * s, ring);
  return recorder.finishRecordingAsPicture();
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  /** The manual loupe, [] for the parking place. */
  loupe: SharedValue<number[]>;
  /** [date, fromX, fromY, toX, toY, duration] or []. */
  glide: SharedValue<number[]>;
  grab: SharedValue<number[]>;
  grabbing: SharedValue<boolean>;
  liftDate: SharedValue<number>;
  liftUp: SharedValue<boolean>;
  focus: SharedValue<number>;
  focusDate: SharedValue<number>;
  /** Slide → [date, on (1) or off (0)]. */
  marks: SharedValue<Record<number, number[]>>;
  moved: SharedValue<boolean>;
  away: SharedValue<boolean>;
  active: SharedValue<boolean>;
  resumeAt: SharedValue<number>;
  joinAt: SharedValue<number>;
};

function frameAt(m: Motion, at: number, l: Layout, reduced: boolean): Frame {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) {
    const f = autoFrame((at - start) % LOOP, l.parkX, l.parkY, l.centers);
    if (f.markAlpha > 0) f.marks[MARKED] = { draw: f.markDraw, alpha: f.markAlpha, tilt: f.markTilt };
    f.markCount = f.markAlpha > 0 ? 1 : 0;
    if (reduced) {
      f.blur = 0;
      f.sheen = -1;
      f.pulse = 0;
    }
    return f;
  }
  const f = emptyFrame();
  const p = m.loupe.get();
  f.x = p.length === 2 ? p[0] : l.parkX;
  f.y = p.length === 2 ? p[1] : l.parkY;
  const g = m.glide.get();
  if (g.length === 6) {
    const u = clamp((at - g[0]) / g[5]);
    const q = arc(g[1], g[2], g[3], g[4], u, g[5] > 0.5 ? -0.18 : 0);
    f.x = q.x;
    f.y = q.y;
  }
  const lu = clamp((at - m.liftDate.get()) / 0.15);
  f.lift = m.liftUp.get() ? lu : 1 - lu;
  const focus = m.focus.get();
  if (focus >= 0) {
    f.focus = focus;
    const since = at - m.focusDate.get();
    f.blur = reduced ? 0 : 6 * (1 - smoothstep(0, 0.25, since));
    f.caption = smoothstep(0, 0.25, since);
  }
  f.hint = focus >= 0 ? 0 : 1;
  let count = 0;
  const marks = m.marks.get();
  for (const key of Object.keys(marks)) {
    const i = Number(key);
    const mk = marks[i];
    const since = at - mk[0];
    if (mk[1] > 0) {
      f.marks[i] = { draw: reduced ? 1 : clamp(since / 0.4), alpha: reduced ? clamp(since / 0.2) : 1, tilt: reduced ? 0 : easeOutBack(since / 0.3) };
      count++;
    } else {
      const a = 1 - clamp(since / 0.25);
      if (a > 0) f.marks[i] = { draw: 1, alpha: a, tilt: reduced ? 0 : 1 - easeInOut(since / 0.6) };
    }
  }
  f.counter = Math.min(1, count);
  f.markCount = count;
  f.flicker = Math.sin(2 * Math.PI * 2 * (at % 1000));
  return f;
}

function tactile(strong: boolean) {
  Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function LightTableLoupe({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const font = useMemo(() => matchFont({ fontFamily: MONO, fontSize: 6.5, fontWeight: '500' }), []);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const l = useMemo(() => layoutFor(size.width, size.height, insets.top, insets.bottom), [size.width, size.height, insets.top, insets.bottom]);
  const grain = useMemo<Grain>(() => {
    const dark = Skia.Path.Make();
    const light = Skia.Path.Make();
    for (let i = 0; i < 700; i++) {
      const x = hash(i, 3) * l.width;
      const y = l.header + hash(i, 4) * (l.height - l.header);
      (hash(i, 5) > 0.5 ? dark : light).addOval(Skia.XYWHRect(x - 0.6, y - 0.6, 1.2, 1.2));
    }
    const lensDark = Skia.Path.Make();
    const lensLight = Skia.Path.Make();
    for (let i = 0; i < 400; i++) {
      const a = hash(i, 7) * 2 * Math.PI;
      const d = Math.sqrt(hash(i, 8)) * LENS;
      (hash(i, 9) > 0.5 ? lensDark : lensLight).addOval(Skia.XYWHRect(d * Math.cos(a) - 0.5, d * Math.sin(a) - 0.5, 1, 1));
    }
    return { dark, light, lensDark, lensLight };
  }, [l]);

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    loupe: useSharedValue<number[]>([]),
    glide: useSharedValue<number[]>([]),
    grab: useSharedValue<number[]>([0, 0]),
    grabbing: useSharedValue(false),
    liftDate: useSharedValue(-10),
    liftUp: useSharedValue(false),
    focus: useSharedValue(-1),
    focusDate: useSharedValue(-10),
    marks: useSharedValue<Record<number, number[]>>({}),
    moved: useSharedValue(false),
    away: useSharedValue(false),
    active: useSharedValue(false),
    resumeAt: useSharedValue(-1),
    joinAt: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0) {
      if (autoPlay && !reduced) m.autoStart.set(now);
      m.clock.set(now);
    }
    // 2 s after the last touch: the loupe glides home (0.8 s), the marks are wiped, then the loop.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt && size.width > 0) {
      m.resumeAt.set(-1);
      const f = frameAt(m, now, l, reduced);
      m.glide.set([now, f.x, f.y, l.parkX, l.parkY, 0.8]);
      m.loupe.set([l.parkX, l.parkY]);
      m.focus.set(-1);
      m.focusDate.set(now);
      const marks = m.marks.get();
      const next: Record<number, number[]> = {};
      for (const key of Object.keys(marks)) {
        const mk = marks[Number(key)];
        next[Number(key)] = mk[1] > 0 ? [now, 0] : mk;
      }
      m.marks.set(next);
      m.joinAt.set(now + 0.8);
    }
    const joinAt = m.joinAt.get();
    if (joinAt >= 0 && now >= joinAt) {
      m.joinAt.set(-1);
      m.glide.set([]);
      m.marks.set({});
      if (autoPlay && !reduced) m.autoStart.set(now - 8.2);
      else m.active.set(false);
    }
    if ((m.autoStart.get() >= 0 && !reduced) || m.active.get()) m.clock.set(now);
  });

  const frame = useDerivedValue(() => (size.width > 0 ? frameAt(m, m.clock.get(), l, reduced) : emptyFrame()));
  const picture = useDerivedValue(() => {
    if (size.width === 0) {
      // An empty picture: finishing a recorder that never began recording crashes the app.
      const recorder = Skia.PictureRecorder();
      recorder.beginRecording(Skia.XYWHRect(0, 0, 1, 1));
      return recorder.finishRecordingAsPicture();
    }
    return drawScene(l, frame.get(), grain, font);
  });

  const gesture = useMemo(() => {
    const takeOver = (now: number) => {
      'worklet';
      m.resumeAt.set(-1);
      m.joinAt.set(-1);
      m.active.set(true);
      const start = m.autoStart.get();
      if (start < 0) return;
      const f = autoFrame((now - start) % LOOP, l.parkX, l.parkY, l.centers);
      m.autoStart.set(-1);
      m.loupe.set([f.x, f.y]);
      m.glide.set([]);
      m.marks.set(f.markAlpha > 0.5 ? { [MARKED]: [now - 1, 1] } : {});
      if (f.caption > 0.5) {
        m.focus.set(f.focus);
        m.focusDate.set(now - 1);
      } else {
        m.focus.set(-1);
        m.focusDate.set(-10);
      }
      m.liftDate.set(now - 1);
      m.liftUp.set(f.lift > 0.5);
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const now = m.clock.get();
        takeOver(now);
        const cur = frameAt(m, now, l, reduced);
        m.moved.set(false);
        const away = Math.hypot(e.x - cur.x, e.y - cur.y) > 70;
        m.away.set(away);
        if (away) {
          // A drag that starts away from the loupe brings it over, 64 pt above the finger.
          m.grab.set([0, -64]);
        } else {
          m.grab.set([cur.x - e.x, cur.y - e.y]);
          m.liftDate.set(now);
          m.liftUp.set(true);
        }
        m.grabbing.set(true);
      })
      .onUpdate((e) => {
        if (!m.grabbing.get()) return;
        const now = m.clock.get();
        const g = m.grab.get();
        const tx = e.x + g[0];
        const ty = e.y + g[1];
        if (!m.moved.get()) {
          if (Math.hypot(e.translationX, e.translationY) <= 6) return;
          m.moved.set(true);
          if (m.away.get()) {
            const f = frameAt(m, now, l, reduced);
            m.glide.set([now, f.x, f.y, tx, ty, 0.25]);
            m.liftDate.set(now);
            m.liftUp.set(true);
          }
        }
        const gl = m.glide.get();
        if (gl.length === 6 && now - gl[0] < gl[5]) {
          m.glide.set([gl[0], gl[1], gl[2], tx, ty, gl[5]]);
        } else {
          m.glide.set([]);
          m.loupe.set([tx, ty]);
        }
        // A light haptic at each new slide under the loupe.
        const i = slideAt(l, tx, ty);
        if (i !== m.focus.get()) {
          m.focus.set(i);
          m.focusDate.set(now);
          if (i >= 0) scheduleOnRN(tactile, false);
        }
      })
      .onFinalize((e) => {
        if (!m.grabbing.get()) return;
        m.grabbing.set(false);
        const now = m.clock.get();
        if (m.liftUp.get()) {
          m.liftDate.set(now);
          m.liftUp.set(false);
        }
        if (!m.moved.get()) {
          // A tap marks the slide under the finger, or under the loupe when the loupe was tapped.
          const f = frameAt(m, now, l, reduced);
          const i = m.away.get() ? slideAt(l, e.x, e.y) : slideAt(l, f.x, f.y);
          if (i >= 0) {
            const marks = { ...m.marks.get() };
            const on = !(marks[i] && marks[i][1] > 0);
            marks[i] = [now, on ? 1 : 0];
            m.marks.set(marks);
            scheduleOnRN(tactile, true);
          }
        }
        const gl = m.glide.get();
        if (gl.length === 6) {
          // Let a trip still under way finish where the finger left the loupe.
          m.loupe.set([gl[3], gl[4]]);
          if (now - gl[0] >= gl[5]) m.glide.set([]);
        }
        m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [l, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const ready = size.width > 0;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {ready ? (
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill}>
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none" accessible accessibilityLabel="Light table with twelve slides and a loupe">
              <Picture picture={picture} />
            </Canvas>
            <Labels frame={frame} l={l} />
          </View>
        </GestureDetector>
      ) : null}
    </View>
  );
}

/**
 * The header's count and the viewer's caption. They live in their own component so that a change
 * of text renders only this, not the screen (whose worklets would then be rebuilt mid-motion).
 */
function Labels({ frame, l }: { frame: SharedValue<Frame>; l: Layout }) {
  const counterStyle = useAnimatedStyle(() => ({ opacity: frame.get().counter }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: clamp(frame.get().hint) }));
  const captionStyle = useAnimatedStyle(() => {
    const f = frame.get();
    return { opacity: f.focus >= 0 ? f.caption : 0 };
  });
  // The caption's text changes only when another slide is under the loupe.
  const [shown, setShown] = useState({ index: 0, count: 1 });
  useAnimatedReaction(
    () => {
      const f = frame.get();
      return Math.max(0, f.focus) * 100 + Math.max(1, f.markCount);
    },
    (k, previous) => {
      if (k !== previous) scheduleOnRN(setShown, { index: Math.floor(k / 100), count: k % 100 });
    },
  );

  return (
    <>
      <View style={[styles.header, { height: l.header }]} pointerEvents="none">
        <View style={styles.headerRow}>
          <Text style={styles.headerText}>CONTACT 04 · 12 FRAMES</Text>
          <Animated.View style={[styles.counter, counterStyle]}>
            <View style={styles.redDot} />
            <Text style={styles.headerCount}>{shown.count} MARKED</Text>
          </Animated.View>
        </View>
      </View>
      <Animated.View style={[styles.caption, { top: l.captionY, left: l.viewer.x, width: l.viewer.width }, hintStyle]} pointerEvents="none">
        <Text style={styles.kicker}>VIEWER · GATE EMPTY</Text>
        <Text style={styles.hint} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
          Drag the loupe over a slide
        </Text>
      </Animated.View>
      <Animated.View style={[styles.caption, { top: l.captionY, left: l.viewer.x, width: l.viewer.width }, captionStyle]} pointerEvents="none">
        <Text style={styles.kicker}>FRAME {String(shown.index + 1).padStart(2, '0')} · OCT 84</Text>
        <Text style={styles.title} numberOfLines={1}>
          {SLIDES[shown.index].title}
        </Text>
      </Animated.View>
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#DDE2E7' },
  header: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: '#0F0F12', justifyContent: 'flex-end' },
  headerRow: { height: 40, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerText: { fontFamily: MONO, fontSize: 12, fontWeight: '600', letterSpacing: 0.6, color: 'rgba(255,255,255,0.92)' },
  counter: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  redDot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: RED },
  headerCount: { fontFamily: MONO, fontSize: 12, fontWeight: '600', color: 'rgba(255,255,255,0.92)' },
  caption: { position: 'absolute', gap: 3 },
  kicker: { fontFamily: MONO, fontSize: 11, fontWeight: '600', color: 'rgba(26,28,33,0.45)' },
  hint: { fontSize: 15, fontWeight: '600', color: 'rgba(26,28,33,0.8)' },
  title: { fontSize: 17, fontWeight: '600', color: '#1A1C21' },
});
