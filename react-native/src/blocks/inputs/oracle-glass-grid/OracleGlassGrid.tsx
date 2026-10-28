import {
  BlurStyle,
  Canvas,
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
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { LAVA_EFFECT } from './lavaShader';
import {
  ASK,
  ASK_H,
  ASK_W,
  AUTO_CHOICE,
  BACKDROP,
  CAMERA,
  CARD_H,
  CARD_R,
  CARD_W,
  CARD_WHITE,
  CARET_H,
  CARET_W,
  CENTER_SCALE,
  closeField,
  type Driver,
  driverFrame,
  easeOut,
  FIELD_H,
  FIELD_MARGIN,
  frameAt,
  type Frame,
  GAP,
  ICON_LIFT,
  ICON_SCALE,
  ICONS,
  INK,
  isRest,
  LABELS,
  lerp,
  LOOP,
  makeDriver,
  markAlpha,
  pick,
  PLACEHOLDER,
  SAMPLE,
  seg,
  setReduced,
  slotX,
  slotY,
  takeOver,
  TITLE_FIELD,
  TITLE_GRID,
  WHITE,
} from './oracleModel';

/*
 Oracle Glass Grid — the "ask the cards" screen of Sibyl, an invented oracle app (a port of OracleGlassGrid.swift).

 Look: a live lava gradient (purple, maroon, deep green, teal; lavaShader.ts) behind a 3 × 3 grid of frosted
 glass cards, each with a thin glowing line icon drawn in code that shimmers, under "What answers do you
 seek?" and above an "ASK FOR ME" pill.
 Hero: the chosen card turns solid white while the other eight gather into a stack behind it; the stack rides
 to the centre and the others vanish; the white card flips about Y until it is edge-on, a glowing line that
 shrinks into a caret, and the caret opens sideways into the question field "Tap to type". The return plays
 it backwards and the grid re-deals from the stack with a small overshoot.

 autoPlay: LOOP = 10 s (frameAt in oracleModel.ts); picks Career, types "Should I take the leap?", folds back.
 Touch: tap any card to open it into the field; tap the back arrow or swipe down to fold it back. A touch
 during the story takes it over where it is. 2 s after the last touch an open field folds back and, at rest,
 the loop starts again.
 Reduced motion: no loop, lava and icons still; a tap crossfades between the grid and the field.
*/

type Props = { autoPlay?: boolean };

/** Glyphs laid out with tracking; widths from getGlyphWidths (measureText rounds per glyph). */
type Run = { ids: number[]; pos: SkPoint[]; w: number };

type Texts = {
  label: SkFont;
  title: SkFont;
  field: SkFont;
  ask: SkFont;
  labels: Run[][];
  titleGrid: Run;
  titleField: Run;
  placeholder: Run;
  askRun: Run;
  typed: Run[];
  icons: SkPath[][];
};

function run(font: SkFont, s: string, track: number): Run {
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i] + track;
  }
  return { ids, pos, w: x };
}

function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, Math.min(1, Math.max(0, a)));
}

function mixHex(a: number, b: number, t: number) {
  'worklet';
  const r = Math.round(((a >> 16) & 0xff) + (((b >> 16) & 0xff) - ((a >> 16) & 0xff)) * t);
  const g = Math.round(((a >> 8) & 0xff) + (((b >> 8) & 0xff) - ((a >> 8) & 0xff)) * t);
  const bl = Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * t);
  return (r << 16) | (g << 8) | bl;
}

function solid(v: number, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(v, a));
  return p;
}

function stroke(v: number, a: number, width: number): SkPaint {
  'worklet';
  const p = solid(v, a);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

/** SwiftUI's blur radius as the sigma Android's BlurMaskFilter gives (1.5 × radius), so the stacks soften alike. */
function blurred(p: SkPaint, radius: number): SkPaint {
  'worklet';
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.57735 * radius * 1.5 + 0.5, true));
  return p;
}

function rrect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function circle(cx: number, cy: number, r: number) {
  'worklet';
  return Skia.XYWHRect(cx - r, cy - r, 2 * r, 2 * r);
}

/** A run centred on x with its cap middle on y. */
function centered(c: SkCanvas, r: Run, font: SkFont, x: number, y: number, capHalf: number, color: number, a: number) {
  'worklet';
  if (a <= 0.001) return;
  c.drawGlyphs(r.ids, r.pos, x - r.w / 2, y + capHalf, font, solid(color, a));
}

function sheenOffset(s: number, sx: number, sy: number, gx: number, gy: number) {
  'worklet';
  if (s <= 0 || s >= 1) return Number.NaN;
  const span = 820;
  const band = -span / 2 + span * s;
  return band - ((sx - gx) + 0.5 * (sy - gy));
}

function drawCard(
  c: SkCanvas,
  i: number,
  cx: number,
  cy: number,
  scale: number,
  degrees: number,
  alpha: number,
  white: number,
  iconDim: number,
  shade: number,
  sheen: number,
  f: Frame,
  tx: Texts,
) {
  'worklet';
  if (alpha <= 0.001) return;
  const cw = CARD_W;
  const ch = CARD_H;
  c.save();
  c.translate(cx, cy);
  if (degrees > 0.01) {
    // Rotation about the card's vertical axis seen from a camera 600 pt away: the right edge recedes.
    const th = (degrees * Math.PI) / 180;
    c.concat(Skia.Matrix([Math.cos(th), 0, 0, 0, 1, 0, Math.sin(th) / CAMERA, 0, 1]));
  }
  c.scale(scale, scale);
  c.translate(-cw / 2, -ch / 2);
  if (alpha < 0.999) {
    const lp = Skia.Paint();
    lp.setAlphaf(alpha);
    c.saveLayer(lp);
  }
  const shape = rrect(0, 0, cw, ch, CARD_R);
  const glass = 1 - white;
  c.drawRRect(shape, solid(WHITE, 0.1 * glass));
  const top = Skia.Paint();
  top.setAntiAlias(true);
  top.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: ch * 0.45 }, [hex(WHITE, 0.16 * glass), hex(WHITE, 0)], null, TileMode.Clamp),
  );
  c.drawRRect(shape, top);
  if (white > 0.001) c.drawRRect(shape, solid(CARD_WHITE, white));
  if (!Number.isNaN(sheen)) {
    const dx = 1 / 1.25;
    const dy = 0.5 / 1.25;
    const sp = Skia.Paint();
    sp.setAntiAlias(true);
    sp.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: cw / 2 + dx * (sheen - 70), y: ch / 2 + dy * (sheen - 70) },
        { x: cw / 2 + dx * (sheen + 70), y: ch / 2 + dy * (sheen + 70) },
        [hex(WHITE, 0), hex(WHITE, 0.16 * glass), hex(WHITE, 0)],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    c.drawRRect(shape, sp);
  }
  c.drawRRect(rrect(0.6, 0.6, cw - 1.2, ch - 1.2, CARD_R - 0.6), stroke(WHITE, 0.8 + 0.2 * white, 1.2));
  if (glass > 0.001) c.drawRRect(rrect(2, 2, cw - 4, ch - 4, CARD_R - 2), stroke(WHITE, 0.18 * glass, 0.6));

  // Icon.
  const ink = mixHex(WHITE, INK, white);
  const marks = ICONS[i];
  const paths = tx.icons[i];
  c.save();
  c.translate(cw / 2, ch / 2 - ICON_LIFT);
  c.scale(ICON_SCALE, ICON_SCALE);
  c.translate(-28, -28);
  if (glass > 0.001) {
    for (let j = 0; j < marks.length; j++) {
      const m = marks[j];
      if (!m.glow) continue;
      const a = markAlpha(m, f.clock) * iconDim * glass * 0.9;
      if (m.fill) c.drawPath(paths[j], blurred(solid(WHITE, a), 3.2));
      c.drawPath(paths[j], blurred(stroke(WHITE, a, 2.4), 3.2));
    }
  }
  for (let j = 0; j < marks.length; j++) {
    const m = marks[j];
    const a = (m.k > 0 ? markAlpha(m, f.clock) * glass + m.dim * white : m.dim) * iconDim;
    c.drawPath(paths[j], m.fill ? solid(ink, a) : stroke(ink, a, 1.3));
  }
  c.restore();

  // Label.
  const lines = tx.labels[i];
  const labelAlpha = iconDim > 0.99 ? 1 : 0.6 + 0.4 * iconDim;
  for (let j = 0; j < lines.length; j++) {
    const r = lines[j];
    const baseline = ch - 13 - 13.5 * (lines.length - 1 - j);
    c.drawGlyphs(r.ids, r.pos, (cw - r.w) / 2, baseline, tx.label, solid(ink, labelAlpha));
  }

  // Turning away: the face darkens and a specular band slides across it.
  if (shade > 0.001) {
    c.drawRRect(shape, solid(0x000000, 0.25 * shade));
    const x = cw * (1.2 - 1.6 * shade);
    const bp = Skia.Paint();
    bp.setAntiAlias(true);
    bp.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: x - 30, y: 0 },
        { x: x + 30, y: 0 },
        [hex(WHITE, 0), hex(WHITE, 0.45 * Math.sin(Math.PI * Math.min(shade * 1.2, 1))), hex(WHITE, 0)],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    c.drawRRect(shape, bp);
  }
  if (alpha < 0.999) c.restore();
  c.restore();
}

function drawField(c: SkCanvas, f: Frame, px: number, py: number, w: number, tx: Texts) {
  'worklet';
  if (!(f.flip > 0.5 || f.reducedField) || f.fieldAlpha <= 0.001) return;
  const lp = Skia.Paint();
  lp.setAlphaf(f.fieldAlpha);
  c.saveLayer(lp);
  const o = f.open;
  const fieldW = w - 2 * FIELD_MARGIN;
  if (o > 0.001) {
    const fw = lerp(CARET_W, fieldW, o);
    const fh = lerp(CARET_H, FIELD_H, easeOut(Math.min(Math.max(o * 2, 0), 1)));
    const rx = px - fw / 2;
    const ry = py - fh / 2;
    const shape = rrect(rx, ry, fw, fh, fh / 2);
    const a = seg(o, 0, 0.12);
    c.drawRRect(shape, solid(WHITE, 0.07 * a));
    c.drawRRect(shape, blurred(stroke(WHITE, 0.45 * a, 2.5), 6));
    c.drawRRect(shape, stroke(WHITE, 0.92 * a, 1.4));
    if (fw - 7 > 8) {
      c.drawRRect(rrect(rx + 3.5, ry + 3.5, fw - 7, fh - 7, (fh - 7) / 2), stroke(WHITE, 0.3 * seg(o, 0.3, 0.8), 0.8));
    }
    if (f.rings > 0.001) {
      const r = 4.5 * lerp(0.4, 1, f.rings);
      for (let k = 0; k < 2; k++) {
        const x = k === 0 ? rx : rx + fw;
        c.drawOval(circle(x, py, r), solid(0x6e5a78, 0.9 * f.rings));
        c.drawOval(circle(x, py, r), stroke(WHITE, 0.95 * f.rings, 1.2));
      }
    }
  }

  // Text, placeholder and the caret.
  let caretX = px;
  if (f.typed > 0) {
    const r = tx.typed[Math.min(f.typed, tx.typed.length) - 1];
    centered(c, r, tx.field, px, py, 6, WHITE, f.textAlpha);
    caretX = lerp(px, px + r.w / 2 + 3, f.textAlpha);
  } else {
    centered(c, tx.placeholder, tx.field, px, py, 6, WHITE, 0.62 * f.placeholder);
    caretX = lerp(px, px - tx.placeholder.w / 2 - 6, f.toPlaceholder);
  }

  const lineA = f.lineAlpha * f.caret;
  if (lineA > 0.001) {
    const lh = lerp(CARD_H * CENTER_SCALE, CARET_H, f.line);
    const bloom = lineA * (1 - seg(o, 0, 0.6));
    if (bloom > 0.001) {
      const bp = Skia.Paint();
      bp.setAntiAlias(true);
      bp.setShader(Skia.Shader.MakeRadialGradient({ x: caretX, y: py }, 34, [hex(WHITE, 0.3 * bloom), hex(WHITE, 0)], null, TileMode.Clamp));
      c.drawOval(circle(caretX, py, 34), bp);
    }
    c.drawRRect(
      rrect(caretX - CARET_W / 2 - 1.5, py - lh / 2, CARET_W + 3, lh, 2),
      blurred(solid(WHITE, (0.95 - 0.45 * seg(o, 0.5, 1)) * lineA), 5),
    );
    c.drawRRect(rrect(caretX - CARET_W / 2, py - lh / 2, CARET_W, lh, CARET_W / 2), solid(WHITE, lineA));
  }
  c.restore();
}

function drawChrome(c: SkCanvas, f: Frame, w: number, h: number, top: number, bottom: number, tx: Texts) {
  'worklet';
  const y = top + 24;
  const cx = w / 2;
  // Sibyl's mark: a ring with a four-point star and an orbiting dot.
  c.drawOval(circle(cx, y, 15), blurred(stroke(WHITE, 0.55, 2), 4));
  c.drawOval(circle(cx, y, 15), stroke(WHITE, 0.95, 1.4));
  const star = Skia.Path.Make();
  star.moveTo(cx, y - 8);
  star.lineTo(cx + 1.9, y - 1.9);
  star.lineTo(cx + 8, y);
  star.lineTo(cx + 1.9, y + 1.9);
  star.lineTo(cx, y + 8);
  star.lineTo(cx - 1.9, y + 1.9);
  star.lineTo(cx - 8, y);
  star.lineTo(cx - 1.9, y - 1.9);
  star.close();
  c.drawPath(star, solid(WHITE, 0.95));
  c.drawCircle(cx + 8.5, y - 8.5, 1.6, solid(WHITE, 0.95));

  // Info and More.
  c.drawOval(circle(w - 66, y, 12), stroke(WHITE, 0.9, 1.3));
  c.drawCircle(w - 66, y - 5, 1.1, solid(WHITE, 0.95));
  c.drawRRect(rrect(w - 66 - 0.8, y - 2.4, 1.6, 8, 0.8), solid(WHITE, 0.95));
  c.drawOval(circle(w - 34, y, 12), stroke(WHITE, 0.9, 1.3));
  for (let k = -1; k <= 1; k++) c.drawCircle(w - 34 + 5 * k, y, 1.2, solid(WHITE, 0.95));

  // Back arrow (field only).
  if (f.titleField > 0.001) {
    const a = Skia.Path.Make();
    a.moveTo(41, y);
    a.lineTo(23, y);
    a.moveTo(30, y - 7);
    a.lineTo(23, y);
    a.lineTo(30, y + 7);
    c.drawPath(a, stroke(WHITE, 0.95 * f.titleField, 1.6));
  }

  // Titles.
  const ty = top + 86;
  centered(c, tx.titleGrid, tx.title, w / 2, ty + f.titleGridY, 7, WHITE, 0.96 * Math.min(1, f.titleGrid));
  centered(c, tx.titleField, tx.title, w / 2, ty + f.titleFieldY, 7, WHITE, 0.96 * f.titleField);

  // ASK FOR ME.
  const askA = Math.min(Math.max(f.askAlpha, 0), 1);
  if (askA > 0.001) {
    const ay = h - bottom - 44;
    const shape = rrect(w / 2 - ASK_W / 2, ay - ASK_H / 2, ASK_W, ASK_H, ASK_H / 2);
    c.drawRRect(shape, solid(WHITE, 0.15 * askA));
    c.drawRRect(shape, stroke(WHITE, 0.2 * askA, 1));
    centered(c, tx.askRun, tx.ask, w / 2 + 0.7, ay, 4.3, WHITE, 0.95 * askA);
    const nudge = 1.6 * Math.sin((2 * Math.PI * 5 * f.clock) / LOOP);
    for (let k = 0; k < 2; k++) {
      const s = k === 0 ? -1 : 1;
      const chx = w / 2 + s * (82 - nudge);
      const chev = Skia.Path.Make();
      chev.moveTo(chx + s * 2, ay - 4);
      chev.lineTo(chx - s * 2, ay);
      chev.lineTo(chx + s * 2, ay + 4);
      c.drawPath(chev, stroke(WHITE, 0.8 * askA, 1.4));
      c.drawCircle(w / 2 + s * 64, ay, 1.6, solid(WHITE, 0.8 * askA));
    }
  }
}

function drawScreen(c: SkCanvas, f: Frame, w: number, h: number, top: number, bottom: number, tx: Texts) {
  'worklet';
  const gx = w / 2;
  const gy = top + (h - top - bottom) / 2 + 10;
  const fx = w / 2;
  const fy = h / 2;
  const ch = f.chosen;
  const chosenX = slotX(ch, gx);
  const chosenY = slotY(ch, gy);
  const groupX = lerp(chosenX, fx, f.center);
  const groupY = lerp(chosenY, fy, f.center);
  const groupScale = lerp(1, CENTER_SCALE, f.center);
  const chosenScale = groupScale * (1 - 0.05 * f.press);

  // Halo behind the chosen card.
  if (f.halo > 0.01) {
    const r = CARD_H * chosenScale * 1.05;
    const hp = Skia.Paint();
    hp.setAntiAlias(true);
    hp.setShader(Skia.Shader.MakeRadialGradient({ x: groupX, y: groupY }, r, [hex(WHITE, 0.42 * f.halo), hex(WHITE, 0)], null, TileMode.Clamp));
    c.drawOval(circle(groupX, groupY, r), hp);
  }

  // Cards: farthest first, the chosen one on top.
  const order: number[] = [];
  for (let r = 8; r >= 1; r--) {
    for (let i = 0; i < 9; i++) if (i !== ch && f.ranks[i] === r) order.push(i);
  }
  order.push(ch);
  for (let n = 0; n < order.length; n++) {
    const i = order[n];
    if (i === ch) {
      if (f.flip >= 0.999) continue;
      drawCard(c, i, groupX, groupY, chosenScale, 90 * f.flip, f.cardAlpha, f.pick, 1, f.flip, Number.NaN, f, tx);
    } else {
      const k = Math.min(f.ranks[i], 3);
      const g = f.gather[i];
      const spread = groupScale * (1 - f.vanish);
      const tx0 = groupX + 9 * k * spread;
      const ty0 = groupY - 7 * k * spread;
      const sx = slotX(i, gx);
      const sy = slotY(i, gy);
      const s = lerp(1, groupScale * (1 - 0.03 * k), g) * (1 - 0.08 * f.vanish);
      drawCard(
        c,
        i,
        lerp(sx, tx0, g),
        lerp(sy, ty0, g),
        s,
        0,
        (1 - f.vanish) * f.cardAlpha,
        0,
        1 - 0.5 * Math.min(Math.max(g, 0), 1),
        0,
        sheenOffset(f.sheen, sx, sy, gx, gy),
        f,
        tx,
      );
    }
  }

  drawField(c, f, fx, fy, w, tx);
  drawChrome(c, f, w, h, top, bottom, tx);
}

function cardAt(x: number, y: number, gx: number, gy: number) {
  'worklet';
  for (let i = 0; i < 9; i++) {
    if (Math.abs(x - slotX(i, gx)) <= CARD_W / 2 + GAP / 2 && Math.abs(y - slotY(i, gy)) <= CARD_H / 2 + GAP / 2) return i;
  }
  return -1;
}

export function OracleGlassGrid({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const top = insets.top;
  const bottom = insets.bottom;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const w = size?.width ?? 1;
  const h = size?.height ?? 1;

  const texts = useMemo<Texts>(() => {
    const label = matchFont({ fontFamily: 'System', fontSize: 12, fontWeight: '600', fontStyle: 'italic' });
    const title = matchFont({ fontFamily: 'System', fontSize: 20, fontWeight: '600' });
    const field = matchFont({ fontFamily: 'System', fontSize: 17, fontWeight: '600' });
    const ask = matchFont({ fontFamily: 'System', fontSize: 12, fontWeight: '700' });
    const typed: Run[] = [];
    for (let n = 1; n <= SAMPLE.length; n++) typed.push(run(field, SAMPLE.slice(0, n), 0));
    return {
      label,
      title,
      field,
      ask,
      labels: LABELS.map((lines) => lines.map((s) => run(label, s, 0))),
      titleGrid: run(title, TITLE_GRID, 0),
      titleField: run(title, TITLE_FIELD, 0),
      placeholder: run(field, PLACEHOLDER, 0),
      askRun: run(ask, ASK, 1.4),
      typed,
      icons: ICONS.map((list) => list.map((m) => Skia.Path.MakeFromSVGString(m.d) ?? Skia.Path.Make())),
    };
  }, []);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(frameAt(0, AUTO_CHOICE, -1, -1, 0));
  const now = useSharedValue(0);

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

  const touch = Gesture.Pan()
    .minDistance(0)
    .onFinalize((e) => {
      const d = driver.get();
      const t = now.get();
      const moved = Math.hypot(e.translationX, e.translationY);
      const swipeDown = e.translationY > 60 && Math.abs(e.translationY) > Math.abs(e.translationX);
      const backHit = e.x < 72 && Math.abs(e.y - (top + 24)) < 30;
      const gx = w / 2;
      const gy = top + (h - top - bottom) / 2 + 10;
      if (reduced) {
        if (d.shown > 0.5) {
          if (swipeDown || (moved < 10 && backHit)) {
            setReduced(d, false, -1, t);
            scheduleOnRN(feel);
          }
        } else if (moved < 10) {
          const i = cardAt(e.x, e.y, gx, gy);
          if (i >= 0) {
            setReduced(d, true, i, t);
            scheduleOnRN(feel);
          }
        }
        return;
      }
      if (isRest(d, t, auto)) {
        const i = moved < 10 ? cardAt(e.x, e.y, gx, gy) : -1;
        if (i >= 0) {
          pick(d, i, t);
          scheduleOnRN(feel);
        } else {
          takeOver(d, t, auto);
        }
      } else {
        takeOver(d, t, auto);
        if (swipeDown || (moved < 10 && backHit)) {
          if (closeField(d, t)) scheduleOnRN(feel);
        }
      }
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const f = frame.get();
    if (LAVA_EFFECT) {
      const lp = Skia.Paint();
      lp.setShader(LAVA_EFFECT.makeShader([w, h, (2 * Math.PI * f.clock) / LOOP]));
      c.drawRect(Skia.XYWHRect(0, 0, w, h), lp);
    } else {
      c.drawRect(Skia.XYWHRect(0, 0, w, h), solid(BACKDROP, 1));
    }
    drawScreen(c, f, w, h, top, bottom, texts);
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
        accessibilityLabel="Sibyl. What answers do you seek? Nine topic cards."
        accessibilityHint="Opens a card into the question field."
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
  screen: { flex: 1, backgroundColor: '#1d3a2b' },
});
