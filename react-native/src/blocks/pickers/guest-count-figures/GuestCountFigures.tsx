import {
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  type SkCanvas,
  type SkColor,
  type SkFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawFigure, drawShadow, hex, LOOKS, mix } from './guestCountArt';
import {
  ADULT_MINUS,
  ADULT_PLUS,
  CHILD_MINUS,
  CHILD_PLUS,
  FIGURE_SCALE,
  type Frame,
  type Geo,
  type GState,
  makeGeo,
  makeSim,
  MAX_PEOPLE,
  NONE,
  PILL,
  type Sim,
  simApply,
  simFrame,
  simState,
  simTouch,
  slot,
  smooth,
} from './guestCountMotion';

/*
 Guest Count Figures — the guests step of a stay-booking flow (a port of GuestCountFigures.swift).

 Look: a warm white screen, "Guests — Add up to 4 people, including yourself.", a row of little
 painted people standing on the floor (drawn in code, guestCountArt.ts), a big rolling numeral
 between − and +, an "Add children" pill, a four-step progress bar and Back / Next. Each guest
 added hops in beside the others with a squash on landing while the group re-centres on a spring
 and greets the newcomer with a small hop; a guest removed crouches, rises and fades. "Add
 children" folds the big counter into two compact rows (Adults, Children); "Adults only" folds it
 back. Everything is drawn into one Skia picture per frame.

 autoPlay: LOOP = 10 s (guestCountMotion.ts has the script).
 Touch: tap − / +, tap the pill, or drag sideways on the big numeral (or on a row) to scrub the
 count, one step every 32 pt. At most 4 people, at least 1 adult. A light haptic on each change.
 2 s after the last touch the screen eases back to one adult and the loop restarts.
 Reduced motion: a still of 2 adults and 2 children in rows; touches still change the counts,
 without motion.
*/

type Props = {
  /** Plays the 10 s add-and-remove loop until touched. */
  autoPlay?: boolean;
};

const BG = 0xfcfbf9;
const INK = 0x222222;
const MUTED = 0x717171;
const LINE = 0xdddddd;

type Fonts = {
  exit: SkFont;
  title: SkFont;
  sub: SkFont;
  label: SkFont;
  small: SkFont;
  pill: SkFont;
  foot: SkFont;
  digit: SkFont;
};

// SF Pro's own tracking at these sizes (Core Text applies it, Skia does not), so the copy keeps
// the Swift widths on iOS.
const IOS = Platform.OS === 'ios';
const TRACK = {
  exit: IOS ? -0.15 : 0,
  sub: IOS ? -0.31 : 0,
  label: IOS ? -0.43 : 0,
  small: IOS ? -0.15 : 0,
  pill: IOS ? -0.23 : 0,
  foot: IOS ? -0.31 : 0,
};

// Grab kinds beyond the five controls.
const GRAB_NONE = -1;
const GRAB_SCRUB_ADULTS = 10;
const GRAB_SCRUB_CHILDREN = 11;

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function textWidth(font: SkFont, s: string, tracking = 0): number {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w + tracking * Math.max(0, widths.length - 1);
}

/** Draws a line of text centred on (x, y), or starting at x when leading. */
function label(
  c: SkCanvas,
  s: string,
  font: SkFont,
  x: number,
  y: number,
  color: SkColor,
  leading = false,
  tracking = 0,
  underline = false,
) {
  'worklet';
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const positions = [];
  let cursor = 0;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cursor, y: 0 });
    cursor += widths[i] + tracking;
  }
  const w = cursor - tracking;
  const m = font.getMetrics();
  const x0 = leading ? x : x - w / 2;
  const base = y - (m.ascent + m.descent) / 2;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  c.drawGlyphs(ids, positions, x0, base, font, p);
  if (underline) {
    const u = Skia.Paint();
    u.setAntiAlias(true);
    u.setColor(color);
    c.drawRect(Skia.XYWHRect(x0, base + 2.2, w, 1), u);
  }
}

function stroke(color: SkColor, width: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

function fill(color: SkColor) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function drawHeader(c: SkCanvas, g: Geo, fonts: Fonts) {
  'worklet';
  const ew = textWidth(fonts.exit, 'Exit', TRACK.exit) + 28;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(20, g.top + 6, ew, 32), 16, 16), stroke(hex(LINE), 1));
  label(c, 'Exit', fonts.exit, 20 + ew / 2, g.top + 22, hex(INK), false, TRACK.exit);
  label(c, 'Guests', fonts.title, g.cx, g.top + 78, hex(INK), false, -0.4);
  label(c, 'Add up to 4 people, including yourself.', fonts.sub, g.cx, g.top + 114, hex(0x6a6a6a), false, TRACK.sub);
}

function drawFigures(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  for (let i = 0; i < f.figures.length; i++) {
    const fig = f.figures[i];
    c.save();
    c.translate(fig.x, g.ground);
    const k = fig.sx * (1 - Math.min(fig.lift / 80, 0.5));
    drawShadow(c, slot(fig.index), k, fig.alpha * (1 - Math.min(fig.lift / 40, 0.8)));
    c.restore();
  }
  for (let i = 0; i < f.figures.length; i++) {
    const fig = f.figures[i];
    const look = LOOKS[fig.index];
    const s = look.scale * FIGURE_SCALE;
    c.save();
    c.translate(fig.x, g.ground - fig.lift);
    c.scale(fig.sx * s, fig.sy * s);
    if (fig.alpha < 0.999) {
      // Fade the figure as one layer, so its overlapping parts don't show through.
      const layer = Skia.Paint();
      layer.setAlphaf(fig.alpha);
      c.saveLayer(layer, Skia.XYWHRect(-60, -150, 120, 160));
      drawFigure(c, look);
      c.restore();
    } else {
      drawFigure(c, look);
    }
    c.restore();
  }
}

/** A digit wheel: the value's neighbours roll past the centre with their alpha and size falling off. */
function drawWheel(c: SkCanvas, v: number, px: number, py: number, scale: number, alpha: number, font: SkFont) {
  'worklet';
  if (alpha <= 0.001) return;
  c.save();
  c.clipRect(Skia.XYWHRect(px - 80 * scale, py - 64 * scale, 160 * scale, 128 * scale), ClipOp.Intersect, true);
  const base = Math.floor(v);
  for (let k = base - 1; k <= base + 2; k++) {
    if (k < 0 || k > 9) continue;
    const o = k - v;
    const a = Math.max(0, 1 - 1.5 * Math.abs(o)) * alpha;
    if (a <= 0.001) continue;
    const s = scale * (1 - 0.25 * Math.min(1, Math.abs(o)));
    c.save();
    c.translate(px, py + o * 72 * scale);
    c.scale(s, s);
    label(c, `${k}`, font, 0, 0, hex(INK, a));
    c.restore();
  }
  c.restore();
}

function drawButton(
  c: SkCanvas,
  px: number,
  py: number,
  d: number,
  plus: boolean,
  disabled: number,
  press: number,
  alpha: number,
) {
  'worklet';
  if (alpha <= 0.001) return;
  const s = 1 - 0.12 * press;
  c.save();
  c.translate(px, py);
  c.scale(s, s);
  if (press > 0.001) c.drawCircle(0, 0, d / 2, fill(hex(0xf2f2f2, press * alpha)));
  c.drawCircle(0, 0, d / 2, stroke(mix(LINE, 0xeeeeee, disabled, alpha), 1));
  const l = d * 0.18;
  const glyph = stroke(mix(INK, 0xd2d2d2, disabled, alpha), d > 40 ? 2 : 1.6);
  c.drawLine(-l, 0, l, 0, glyph);
  if (plus) c.drawLine(0, -l, 0, l, glyph);
  c.restore();
}

function drawCounter(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const m = f.m;
  const mc = Math.min(1, Math.max(0, m));

  // Adults: the big counter folding into its row.
  drawWheel(c, f.adultsV, lerp(g.cx, g.rowNumX, m), lerp(g.numY, g.rowA, m), lerp(1, 0.17, m), 1, fonts.digit);
  const bd = lerp(44, 36, mc);
  drawButton(c, lerp(g.cx - 120, g.rowMinusX, m), lerp(g.numY, g.rowA, m), bd, false, f.disabled[0], f.press[0], 1);
  drawButton(c, lerp(g.cx + 120, g.rowPlusX, m), lerp(g.numY, g.rowA, m), bd, true, f.disabled[1], f.press[1], 1);

  const aw = textWidth(fonts.label, 'Adults', TRACK.label);
  const sw = textWidth(fonts.small, 'Aged 13 or above', TRACK.small);
  label(c, 'Adults', fonts.label, lerp(g.cx - aw / 2, 24, m), lerp(g.top + 468, g.top + 374, m), hex(INK), true, TRACK.label);
  label(c, 'Aged 13 or above', fonts.small, lerp(g.cx - sw / 2, 24, m), lerp(g.top + 490, g.top + 396, m), hex(MUTED), true, TRACK.small);

  // Divider and the children row.
  const dp = smooth((m - 0.3) / 0.5);
  if (dp > 0.001) c.drawLine(24, g.divider, 24 + (g.w - 48) * dp, g.divider, stroke(hex(0xebebeb), 1));
  const ca = smooth((m - 0.35) / 0.65);
  if (ca > 0.001) {
    c.save();
    c.translate(0, 16 * (1 - ca));
    label(c, 'Children', fonts.label, 24, g.top + 452, hex(INK, ca), true, TRACK.label);
    label(c, 'Ages 2–12', fonts.small, 24, g.top + 474, hex(MUTED, ca), true, TRACK.small);
    drawWheel(c, f.childrenV, g.rowNumX, g.rowC, 0.17, ca, fonts.digit);
    drawButton(c, g.rowMinusX, g.rowC, 36, false, f.disabled[2], f.press[2], ca);
    drawButton(c, g.rowPlusX, g.rowC, 36, true, f.disabled[3], f.press[3], ca);
    c.restore();
  }

  // The pill: "Add children" ↔ "Adults only".
  const pw = lerp(textWidth(fonts.pill, 'Add children', TRACK.pill), textWidth(fonts.pill, 'Adults only', TRACK.pill), mc) + 36;
  const ps = 1 - 0.08 * f.press[4];
  c.save();
  c.translate(g.cx, g.pillY);
  c.scale(ps, ps);
  const pill = Skia.RRectXY(Skia.XYWHRect(-pw / 2, -20, pw, 40), 20, 20);
  if (f.press[4] > 0.001) c.drawRRect(pill, fill(hex(0xf2f2f2, f.press[4])));
  c.drawRRect(pill, stroke(hex(LINE), 1));
  // The labels swap with a short roll: the old one leaves 8 pt up, the new one comes from below.
  const xf = smooth((m - 0.3) / 0.4);
  if (xf < 0.999) label(c, 'Add children', fonts.pill, 0, -8 * xf, hex(INK, 1 - xf), false, TRACK.pill);
  if (xf > 0.001) label(c, 'Adults only', fonts.pill, 0, 8 * (1 - xf), hex(INK, xf), false, TRACK.pill);
  c.restore();
}

function drawFooter(c: SkCanvas, g: Geo, fonts: Fonts) {
  'worklet';
  const segW = (g.w - 12) / 4;
  for (let i = 0; i < 4; i++) {
    c.drawRect(Skia.XYWHRect(i * (segW + 4), g.progressY - 2, segW, 4), fill(hex(i < 2 ? INK : 0xe3e3e3)));
  }
  label(c, 'Back', fonts.foot, 24, g.footY, hex(INK), true, TRACK.foot, true);
  const nx = g.w - 24 - 112;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(nx, g.footY - 24, 112, 48), 10, 10), fill(hex(INK)));
  label(c, 'Next', fonts.foot, nx + 56, g.footY, hex(0xffffff), false, TRACK.foot);
}

function drawScreen(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(hex(BG)));
  drawHeader(c, g, fonts);
  drawFigures(c, g, f);
  drawCounter(c, g, f, fonts);
  drawFooter(c, g, fonts);
}

function hit(x: number, y: number, s: GState, g: Geo): number {
  'worklet';
  const near = (cx: number, cy: number, r: number) => Math.hypot(x - cx, y - cy) <= r;
  if (Math.abs(x - g.cx) < 90 && Math.abs(y - g.pillY) < 26) return PILL;
  if (s.rows) {
    if (near(g.rowMinusX, g.rowA, 26)) return ADULT_MINUS;
    if (near(g.rowPlusX, g.rowA, 26)) return ADULT_PLUS;
    if (near(g.rowMinusX, g.rowC, 26)) return CHILD_MINUS;
    if (near(g.rowPlusX, g.rowC, 26)) return CHILD_PLUS;
    if (Math.abs(y - g.rowA) < 36) return GRAB_SCRUB_ADULTS;
    if (Math.abs(y - g.rowC) < 36) return GRAB_SCRUB_CHILDREN;
  } else {
    if (near(g.cx - 120, g.numY, 30)) return ADULT_MINUS;
    if (near(g.cx + 120, g.numY, 30)) return ADULT_PLUS;
    if (Math.abs(x - g.cx) < 96 && Math.abs(y - g.numY) < 70) return GRAB_SCRUB_ADULTS;
  }
  return GRAB_NONE;
}

function clampAdults(n: number, s: GState) {
  'worklet';
  return Math.min(Math.max(n, 1), MAX_PEOPLE - s.children);
}

function clampChildren(n: number, s: GState) {
  'worklet';
  return Math.min(Math.max(n, 0), MAX_PEOPLE - s.adults);
}

export function GuestCountFigures({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo<Geo>(
    () => makeGeo(size?.width ?? 393, size?.height ?? 852, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );
  const fonts = useMemo<Fonts>(() => {
    const family = Platform.select({ ios: 'System', default: 'sans-serif' });
    const f = (fontSize: number, fontWeight: '400' | '500' | '600' | '700') => matchFont({ fontFamily: family, fontSize, fontWeight });
    return {
      exit: f(14, '500'),
      title: f(32, '600'),
      sub: f(16, '400'),
      label: f(17, '500'),
      small: f(14, '400'),
      pill: f(15, '500'),
      foot: f(16, '600'),
      digit: f(100, '700'),
    };
  }, []);

  const sim = useSharedValue<Sim>(makeSim());
  const frame = useSharedValue<Frame>(simFrame(makeSim(), 0, geo.cx, false, reduced));
  const now = useSharedValue(0);
  const grab = useSharedValue(GRAB_NONE);
  const start = useSharedValue<GState>({ adults: 1, children: 0, rows: false });

  useEffect(() => {
    sim.set(makeSim());
  }, [auto, reduced, sim]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    if (!size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    frame.set(simFrame(sim.get(), t, geo.cx, auto, reduced));
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const s = sim.get();
      const st = simState(s, now.get(), auto, reduced);
      start.set(st);
      grab.set(hit(e.x, e.y, st, geo));
      s.touching = true;
      simTouch(s, now.get(), auto, reduced);
    })
    .onUpdate((e) => {
      const s = sim.get();
      const t = now.get();
      const steps = Math.trunc(e.translationX / 32);
      const cur = simState(s, t, auto, reduced);
      const g0 = grab.get();
      let next: GState | null = null;
      if (g0 === GRAB_SCRUB_ADULTS) next = { ...cur, adults: clampAdults(start.get().adults + steps, cur) };
      else if (g0 === GRAB_SCRUB_CHILDREN) next = { ...cur, children: clampChildren(start.get().children + steps, cur) };
      if (next && simApply(s, next, NONE, t, auto, reduced)) scheduleOnRN(feel);
      else simTouch(s, t, auto, reduced);
    })
    .onFinalize((e) => {
      const s = sim.get();
      const t = now.get();
      if (Math.hypot(e.translationX, e.translationY) < 14) {
        const cur = simState(s, t, auto, reduced);
        const g0 = grab.get();
        let next: GState | null = null;
        if (g0 === ADULT_MINUS) next = { ...cur, adults: clampAdults(cur.adults - 1, cur) };
        else if (g0 === ADULT_PLUS) next = { ...cur, adults: clampAdults(cur.adults + 1, cur) };
        else if (g0 === CHILD_MINUS) next = { ...cur, children: clampChildren(cur.children - 1, cur) };
        else if (g0 === CHILD_PLUS) next = { ...cur, children: clampChildren(cur.children + 1, cur) };
        else if (g0 === PILL) next = cur.rows ? { ...cur, rows: false, children: 0 } : { ...cur, rows: true };
        if (next && simApply(s, next, g0, t, auto, reduced)) scheduleOnRN(feel);
      }
      grab.set(GRAB_NONE);
      s.touching = false;
      simTouch(s, t, auto, reduced);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawScreen(canvas, geo, frame.get(), fonts);
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
        accessibilityLabel="Guests. Add up to 4 people, including yourself."
      >
        <StatusBar style="dark" />
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
  screen: { flex: 1, backgroundColor: '#FCFBF9' },
});
