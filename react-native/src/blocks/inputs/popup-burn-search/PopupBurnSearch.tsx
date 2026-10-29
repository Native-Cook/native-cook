import {
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
  type SkPath,
  type SkPoint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  ANSWERS,
  baseStory,
  BURN_LENGTH,
  burnAll,
  burnAt,
  burnFront,
  burstPoints,
  barY,
  buttonHit,
  buttonX,
  type Box,
  type Driver,
  driverFrame,
  easeInOutSine,
  easeOutBack,
  easeOutCubic,
  edgePoints,
  embers,
  flameContour,
  flameGrow,
  type Frame,
  hash,
  makeDriver,
  makeFrame,
  type Pt,
  QUERY,
  search,
  seg,
  STATUSES,
  takeOver,
  TITLE_H,
  windowRect,
} from './popupBurnModel';

/*
 Popup Burn Search — SupperSeek 99, an invented retro "ask the kitchen" search on a deep-blue desktop
 (a port of PopupBurnSearch.swift).

 Look: a black slanted search bar with a cream search button, a red comic starburst behind it, white answer
 pop-ups with lilac title bars, a dock with the app's name and a clock. All drawn in code with Skia.
 Hero: the answer pop-ups pile up, then burn away bottom first: a jagged burn front climbs each window, the
 paper is gone below it, a char rim and scorch sit above it and the rest darkens, a four-layer comic flame
 crown rides the front and boils at 12 fps, embers and ash rise, and a warm glow lights the desktop.
 Supporting: the query types with a caret, the starburst springs in, the cursor glides in and clicks with a
 squish, windows scale in with an overshoot and a settling jitter, the status line counts the answers down.

 autoPlay: LOOP = 9 s (popupBurnModel.ts); rest on the empty bar → type → burst → click → six windows → burn →
 select-all and clear → rest. Every frame is a function of loop time; frame 9 s equals frame 0.
 Touch: tap the search button to spawn the windows, tap a window to burn it, long-press to burn them all.
 A touch takes over where the loop is; 2 s after the last touch the rest burns, the query clears, the loop restarts.
 Reduced motion: no loop; the query, burst and six windows stand still; taps fade windows out and back in.
*/

type Props = { autoPlay?: boolean };

/** Glyphs laid out with tracking; widths from getGlyphWidths (measureText rounds per glyph). */
type Run = { ids: number[]; pos: SkPoint[]; w: number; mid: number; font: SkFont };

type Texts = {
  app: Run;
  version: Run;
  title: Run;
  query: Run[];
  statuses: Run[];
  answers: Run[][];
  dockName: Run;
  clock: Run;
  dots: SkPath;
  arrow: SkPath;
};

const MONO = Platform.OS === 'ios' ? 'Menlo' : 'monospace';

function run(font: SkFont, s: string, track: number): Run {
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i] + track;
  }
  const m = font.getMetrics();
  // Baseline offset that centres the line box (ascent to descent) on a y, like SwiftUI's .leading anchor.
  return { ids, pos, w: Math.max(0, x - (ids.length > 0 ? track : 0)), mid: -(m.ascent + m.descent) / 2, font };
}

function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, Math.min(1, Math.max(0, a)));
}

function solid(v: number, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(v, a));
  return p;
}

function stroke(v: number, a: number, width: number, join: StrokeJoin): SkPaint {
  'worklet';
  const p = solid(v, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeJoin(join);
  return p;
}

function poly(pts: Pt[], dx: number, dy: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  for (let i = 0; i < pts.length; i++) {
    if (i === 0) p.moveTo(pts[i].x + dx, pts[i].y + dy);
    else p.lineTo(pts[i].x + dx, pts[i].y + dy);
  }
  p.close();
  return p;
}

function polyline(pts: Pt[], dy: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  for (let i = 0; i < pts.length; i++) {
    if (i === 0) p.moveTo(pts[i].x, pts[i].y + dy);
    else p.lineTo(pts[i].x, pts[i].y + dy);
  }
  return p;
}

function textLeading(c: SkCanvas, r: Run, x: number, cy: number, color: number, a: number) {
  'worklet';
  if (r.ids.length === 0) return;
  c.drawGlyphs(r.ids, r.pos, x, cy + r.mid, r.font, solid(color, a));
}

function textTrailing(c: SkCanvas, r: Run, x: number, cy: number, color: number, a: number) {
  'worklet';
  textLeading(c, r, x - r.w, cy, color, a);
}

function drawBackground(c: SkCanvas, w: number, h: number, tx: Texts) {
  'worklet';
  const rect = Skia.XYWHRect(0, 0, w, h);
  const lin = Skia.Paint();
  lin.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: w, y: h },
      [hex(0x0b1c3a, 1), hex(0x1c4890, 1), hex(0x3f86e2, 1)],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(rect, lin);
  const rad = Skia.Paint();
  rad.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: w * 0.95, y: h * 0.3 },
      w * 0.8,
      [hex(0x6fb6ff, 0.35), hex(0x6fb6ff, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawRect(rect, rad);
  c.drawPath(tx.dots, solid(0xffffff, 0.07));
}

function drawGlows(c: SkCanvas, f: Frame, w: number, h: number) {
  'worklet';
  if (f.reduced) return;
  for (let i = 0; i < 6; i++) {
    const a = f.burnAge[i];
    if (a < 0 || a >= BURN_LENGTH) continue;
    const r = windowRect(i, w, h);
    const g = flameGrow(a);
    const cy = Math.min(Math.max(burnFront(a, r), r.y), r.y + r.h);
    const p = Skia.Paint();
    p.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: r.x + r.w / 2, y: cy },
        150,
        [hex(0xff7a1a, 0.28 * g), hex(0xff7a1a, 0)],
        null,
        TileMode.Clamp,
      ),
    );
    c.drawCircle(r.x + r.w / 2, cy, 150, p);
  }
}

const PIXEL_ICON = ['.###...', '#...#..', '#...#..', '#...#..', '.###...', '....##.', '.....##'];

function drawHeader(c: SkCanvas, w: number, top: number, tx: Texts) {
  'worklet';
  const y = top + 22;
  const yellow = solid(0xffe03d, 1);
  for (let row = 0; row < PIXEL_ICON.length; row++) {
    const s = PIXEL_ICON[row];
    for (let col = 0; col < s.length; col++) {
      if (s[col] === '#') c.drawRect(Skia.XYWHRect(20 + col * 2, y - 7 + row * 2, 2, 2), yellow);
    }
  }
  textLeading(c, tx.app, 42, y, 0xffffff, 1);
  textTrailing(c, tx.version, w - 20, y, 0xffffff, 0.55);
}

function drawDock(c: SkCanvas, w: number, h: number, bottom: number, tx: Texts) {
  'worklet';
  const x = 16;
  const y = h - bottom - 46;
  const rw = w - 32;
  const mid = y + 18;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, rw, 36), 4, 4), solid(0x0a1838, 0.6));
  c.drawRect(Skia.XYWHRect(x + 4, y, rw - 8, 1), solid(0xffffff, 0.12));
  const bx = x + 12;
  c.drawRect(Skia.XYWHRect(bx, mid - 5, 10, 10), stroke(0xffffff, 0.8, 1.5, StrokeJoin.Miter));
  c.drawRect(Skia.XYWHRect(bx + 3, mid - 2, 4, 4), solid(0xffe03d, 1));
  textLeading(c, tx.dockName, x + 30, mid, 0xffffff, 0.8);
  textTrailing(c, tx.clock, x + rw - 12, mid, 0xffffff, 0.8);
}

function drawSearchBar(c: SkCanvas, f: Frame, w: number, h: number, tx: Texts) {
  'worklet';
  const cx = w / 2;
  const y = barY(h);
  const bx = buttonX(w);

  textLeading(c, tx.title, cx - 164, y - 72, 0xffffff, 1);

  if (f.burstScale > 0.001) {
    c.save();
    c.translate(bx + 4, y + 2);
    c.rotate(f.burstAngle, 0, 0);
    c.scale(f.burstScale, f.burstScale);
    c.drawPath(poly(burstPoints(f.burstQ), 0, 0), solid(0xef3b55, 1));
    c.restore();
  }

  const body = poly(
    [
      { x: cx - 166, y: y - 16 },
      { x: cx + 104, y: y - 16 },
      { x: cx + 112, y: y + 16 },
      { x: cx - 158, y: y + 16 },
    ],
    0,
    0,
  );
  c.drawPath(body, solid(0x08080c, 1));

  const x0 = cx - 148;
  const q = tx.query[f.typed];
  const tw = f.typed === 0 ? 0 : q.w;
  if (f.selected && tw > 0) c.drawRect(Skia.XYWHRect(x0 - 3, y - 9, tw + 6, 18), solid(0xfbf0d2, 1));
  if (f.typed > 0) textLeading(c, q, x0, y, f.selected ? 0x0b1a3a : 0xffffff, 1);
  if (f.caret) c.drawRect(Skia.XYWHRect(x0 + tw + 3, y - 6.5, 2, 13), solid(0xffffff, 1));

  const by = y + 2 * f.press;
  const button = poly(
    [
      { x: bx - 37, y: by - 24 },
      { x: bx + 25, y: by - 24 },
      { x: bx + 37, y: by + 24 },
      { x: bx - 25, y: by + 24 },
    ],
    0,
    0,
  );
  c.drawPath(button, solid(0xfbf0d2, 1));
  if (f.press > 0) c.drawPath(button, solid(0x000000, 0.12 * f.press));
  c.drawPath(button, stroke(0x08080c, 1, 1, StrokeJoin.Miter));
  c.drawCircle(bx - 2, by - 2, 6.5, stroke(0x121212, 1, 2.2, StrokeJoin.Miter));
  const handle = stroke(0x121212, 1, 2.6, StrokeJoin.Round);
  handle.setStrokeCap(StrokeCap.Round);
  c.drawLine(bx + 2.8, by + 2.8, bx + 7, by + 7, handle);

  textLeading(c, tx.statuses[f.status], cx - 160, y + 40, 0xffffff, 0.72);
}

function drawTitleButtons(c: SkCanvas, r: Box) {
  'worklet';
  const ink = 0x1b1b3a;
  const white = solid(0xffffff, 1);
  const line = stroke(ink, 1, 1, StrokeJoin.Miter);
  for (let k = 0; k < 3; k++) {
    const x = r.x + r.w - 4 - 10 - (2 - k) * 12;
    const y = r.y + 4;
    c.drawRect(Skia.XYWHRect(x, y, 10, 10), white);
    c.drawRect(Skia.XYWHRect(x + 0.5, y + 0.5, 9, 9), line);
    if (k === 0) {
      c.drawRect(Skia.XYWHRect(x + 2.5, y + 6.5, 5, 1.2), solid(ink, 1));
    } else if (k === 1) {
      c.drawRect(Skia.XYWHRect(x + 2.5, y + 2.5, 5, 5), line);
    } else {
      const xl = stroke(ink, 1, 1.2, StrokeJoin.Miter);
      c.drawLine(x + 2.5, y + 2.5, x + 7.5, y + 7.5, xl);
      c.drawLine(x + 7.5, y + 2.5, x + 2.5, y + 7.5, xl);
    }
  }
}

function drawPaper(c: SkCanvas, i: number, r: Box, a: number, edge: Pt[], burning: boolean, tx: Texts) {
  'worklet';
  c.drawRect(Skia.XYWHRect(r.x + 5, r.y + 5, r.w, r.h), solid(0x071226, 0.4));
  c.drawRect(Skia.XYWHRect(r.x, r.y, r.w, r.h), solid(0xfbfbfe, 1));
  c.drawRect(Skia.XYWHRect(r.x, r.y, r.w, TITLE_H), solid(0x6c63e6, 1));
  c.drawRect(Skia.XYWHRect(r.x, r.y, r.w, 1), solid(0x9a94ff, 1));
  drawTitleButtons(c, r);
  const lines = tx.answers[i];
  for (let k = 0; k < lines.length; k++) textLeading(c, lines[k], r.x + 16, r.y + TITLE_H + 30 + k * 30, 0x111118, 1);
  c.drawRect(Skia.XYWHRect(r.x + 0.5, r.y + 0.5, r.w - 1, r.h - 1), stroke(0x1b1b3a, 0.4, 1, StrokeJoin.Miter));
  if (burning) {
    const u = easeInOutSine(seg(a, 0.15, 1.6));
    const front = burnFront(a, r);
    const reach = 26 + 30 * u;
    const sc = Skia.Paint();
    sc.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: front - reach },
        { x: 0, y: front },
        [hex(0x6b3a16, 0), hex(0x4a2410, 0.9)],
        null,
        TileMode.Clamp,
      ),
    );
    c.drawRect(Skia.XYWHRect(r.x, front - reach, r.w, reach + 20), sc);
    const char = 0.85 * seg(a, 0.7, 1.5);
    if (char > 0) c.drawRect(Skia.XYWHRect(r.x, r.y, r.w, r.h), solid(0x120a06, char));
    c.drawPath(polyline(edge, 0), stroke(0x1a0d06, 1, 14, StrokeJoin.Miter));
  }
}

function drawWindow(c: SkCanvas, i: number, f: Frame, w: number, h: number, tx: Texts) {
  'worklet';
  const s = f.spawnAge[i];
  const a = f.burnAge[i];
  if (s < 0 || a >= BURN_LENGTH) return;
  const r = windowRect(i, w, h);
  let alpha = 1;
  let scale = 1;
  let ox = 0;
  let oy = 0;
  if (f.reduced) {
    alpha = seg(s, 0, 0.25) * (a >= 0 ? 1 - seg(a, 0, 0.3) : 1);
  } else {
    alpha = seg(s, 0, 0.06);
    scale = 0.55 + 0.45 * easeOutBack(seg(s, 0, 0.26));
    const settleK = 1 - easeOutCubic(seg(s, 0, 0.3));
    ox = (hash(i * 5.1 + 1) - 0.5) * 20 * settleK;
    oy = (hash(i * 5.1 + 2) - 0.5) * 16 * settleK;
  }
  if (alpha <= 0.001) return;
  const burning = !f.reduced && a >= 0;
  const edge = burning ? edgePoints(i, a, r) : [];
  const lp = Skia.Paint();
  lp.setAlphaf(alpha);
  c.saveLayer(lp, Skia.XYWHRect(r.x - 40, r.y - 40, r.w + 80, r.h + 80));
  c.translate(r.x + r.w / 2 + ox, r.y + r.h / 2 + oy);
  c.scale(scale, scale);
  c.translate(-(r.x + r.w / 2), -(r.y + r.h / 2));
  if (burning) {
    const clip: Pt[] = [
      { x: r.x - 2, y: r.y - 2 },
      { x: r.x + r.w + 14, y: r.y - 2 },
    ];
    for (let k = edge.length - 1; k >= 0; k--) clip.push(edge[k]);
    c.clipPath(poly(clip, 0, 0), ClipOp.Intersect, true);
  }
  drawPaper(c, i, r, a, edge, burning, tx);
  c.restore();

  if (burning) {
    const inside: Pt[] = [];
    for (let k = 0; k < edge.length; k++) {
      const p = edge[k];
      if (p.x >= r.x - 1 && p.x <= r.x + r.w + 1 && p.y > r.y - 1) inside.push(p);
    }
    if (inside.length > 1) c.drawPath(polyline(inside, 1), stroke(0xff8c1a, 1, 2.5, StrokeJoin.Round));
  }
}

const FLAME_COLORS = [0xa80f28, 0xe8203a, 0xff8c1a, 0xffe03d];

function drawFlames(c: SkCanvas, i: number, f: Frame, w: number, h: number) {
  'worklet';
  const a = f.burnAge[i];
  if (f.spawnAge[i] < 0 || a < 0 || a >= BURN_LENGTH) return;
  const r = windowRect(i, w, h);
  if (flameGrow(a) > 0.01) {
    for (let l = 0; l < 4; l++) {
      const off = l === 0 ? 3 : 0;
      c.drawPath(poly(flameContour(i, l, a, r), off, off), solid(FLAME_COLORS[l], 1));
    }
  }
  const ps = embers(i, a, r);
  for (let k = 0; k < ps.length; k++) {
    const p = ps[k];
    c.save();
    c.translate(p.x, p.y);
    if (p.angle !== 0) c.rotate((p.angle * 180) / Math.PI, 0, 0);
    c.drawRect(Skia.XYWHRect(-p.size / 2, -p.size / 2, p.size, p.size), solid(p.color, p.alpha));
    c.restore();
  }
}

function drawCursor(c: SkCanvas, f: Frame, w: number, h: number, tx: Texts) {
  'worklet';
  if (f.cursorAlpha <= 0.001) return;
  const s = 1.4 * f.cursorSquish;
  c.save();
  c.translate(buttonX(w) + f.cursorX, barY(h) + f.cursorY);
  c.scale(s, s);
  c.drawPath(tx.arrow, solid(0xffffff, f.cursorAlpha));
  c.drawPath(tx.arrow, stroke(0x08080c, f.cursorAlpha, 1.6 / 1.4, StrokeJoin.Round));
  c.restore();
}

function drawScreen(c: SkCanvas, f: Frame, w: number, h: number, top: number, bottom: number, tx: Texts) {
  'worklet';
  drawBackground(c, w, h, tx);
  drawGlows(c, f, w, h);
  drawHeader(c, w, top, tx);
  drawDock(c, w, h, bottom, tx);
  drawSearchBar(c, f, w, h, tx);
  for (let i = 0; i < 6; i++) drawWindow(c, i, f, w, h, tx);
  if (!f.reduced) for (let i = 0; i < 6; i++) drawFlames(c, i, f, w, h);
  drawCursor(c, f, w, h, tx);
}

const ARROW = [
  { x: 0, y: 0 },
  { x: 0, y: 17 },
  { x: 4.2, y: 13.2 },
  { x: 7, y: 19.6 },
  { x: 9.8, y: 18.4 },
  { x: 7.1, y: 12.2 },
  { x: 12.4, y: 12.2 },
];

export function PopupBurnSearch({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const top = insets.top;
  const bottom = insets.bottom;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const w = size?.width ?? 1;
  const h = size?.height ?? 1;

  const texts = useMemo<Texts>(() => {
    const app = matchFont({ fontFamily: 'System', fontSize: 15, fontWeight: '700' });
    const mono11 = matchFont({ fontFamily: MONO, fontSize: 11, fontWeight: '700' });
    const mono10 = matchFont({ fontFamily: MONO, fontSize: 10, fontWeight: '700' });
    const title = matchFont({ fontFamily: 'System', fontSize: 34, fontWeight: '800' });
    const query = matchFont({ fontFamily: 'System', fontSize: 11, fontWeight: '800' });
    const answer = matchFont({ fontFamily: 'System', fontSize: 26, fontWeight: '400' });
    const queries: Run[] = [];
    for (let n = 0; n <= QUERY.length; n++) queries.push(run(query, QUERY.slice(0, n), 0.3));
    const dots = Skia.Path.Make();
    for (let y = 12; y < h; y += 24) for (let x = 12; x < w; x += 24) dots.addRect(Skia.XYWHRect(x - 0.75, y - 0.75, 1.5, 1.5));
    const arrow = Skia.Path.Make();
    ARROW.forEach((p, i) => (i === 0 ? arrow.moveTo(p.x, p.y) : arrow.lineTo(p.x, p.y)));
    arrow.close();
    return {
      app: run(app, 'SupperSeek 99', 0),
      version: run(mono11, 'v1.0', 0),
      title: run(title, 'Ask the kitchen.', 0),
      query: queries,
      statuses: STATUSES.map((s) => run(mono10, s, 0)),
      answers: ANSWERS.map((lines) => lines.map((s) => run(answer, s, 0))),
      dockName: run(mono11, 'SUPPERSEEK', 0),
      clock: run(mono11, '7:42 PM', 0),
      dots,
      arrow,
    };
  }, [w, h]);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(makeFrame(baseStory(), 0, false));
  const now = useSharedValue(0);
  const longFired = useSharedValue(false);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    now.set(t);
    frame.set(driverFrame(driver.get(), t, auto, reduced));
  });

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      longFired.set(false);
      takeOver(driver.get(), now.get(), auto);
    })
    .onFinalize((e) => {
      const d = driver.get();
      const t = now.get();
      takeOver(d, t, auto);
      if (longFired.get()) return;
      if (Math.hypot(e.translationX, e.translationY) >= 10) return;
      if (buttonHit(e.x, e.y, w, h)) {
        if (search(d, t)) scheduleOnRN(feel);
      } else if (burnAt(d, e.x, e.y, t, w, h)) {
        scheduleOnRN(feel);
      }
    });

  const hold = Gesture.LongPress()
    .minDuration(450)
    .onStart(() => {
      const d = driver.get();
      const t = now.get();
      longFired.set(true);
      takeOver(d, t, auto);
      if (burnAll(d, t)) scheduleOnRN(feel);
    });

  const touch = Gesture.Simultaneous(pan, hold);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    drawScreen(c, frame.get(), w, h, top, bottom, texts);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={touch}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityRole="button"
        accessibilityLabel="SupperSeek 99. What should I cook tonight? Six answers."
        accessibilityHint="Search shows the answers; tap one to burn it, or long-press to burn them all."
      >
        <StatusBar style="light" />
        {size ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        ) : null}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#12306a' },
});
