// Sketch Coffee Splash: the launch of an invented coffee shop, Inkpot Coffee Co., drawn in white ink on
// ultramarine. Every line is a hand-drawn ribbon (uneven width, tapered ends) that "boils": it is redrawn
// with a slightly different wobble 8 times a second, from four fixed variants.
//
// Timeline of one 10 s loop (autoPlay), the same as the Swift version, t in seconds:
//   0.00-1.20  the welcome at rest: title, a tilted paper cup with coffee splashing out, drops bobbing.
//   1.20-1.90  it leaves: button shrinks to a dot and goes, title slides off right, notes left, the cup
//              drops away to the bottom right, turning.
//   2.20-2.60  a top-view cup pops up from a dot (back-out), "0%" fades in.
//   2.50-3.10  the arm slides in from the bottom right holding a spoon.
//   2.95-6.95  the spoon stirs (0.85 s a turn, eased in and out over 0.4 s); the percentage counts 1 to 100
//              from 3.0 to 6.55.
//   6.70-7.15  the hand pulls out; 7.00-7.30 the cup shrinks away.
//   7.30-7.68  the button comes back; 7.40-8.20 the title and notes wipe back in.
//   7.50-8.40  the paper cup bounces in on a damped spring, the splash grows out and the drops fly out.
//   8.40-10.0  at rest again, identical to t = 0.
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, useWindowDimensions, View, type TextStyle } from 'react-native';
import {
  Canvas,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  useFont,
  type SkCanvas,
  type SkFont,
  type SkPicture,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import {
  ARM_AWAY,
  ARM_DIRECTION,
  CUP,
  CUP_RADIUS,
  DROPS,
  HAND,
  PINCH,
  SPLASH,
  STIR_RADIUS,
  SWIRL,
  THUMB,
  TOP,
  VARIANTS,
  ribbon,
  type SketchStroke,
} from './sketchCoffeeArt';

export const LOOP = 10;

const GROUND = '#2641A8';
const INK = '#FFFFFF';
const SHADOW = '#B2D6F6';
const HAND_FONT = 'SketchCoffeeSplash-CoveredByYourGrace';
const PEN_FONT = 'SketchCoffeeSplash-ReenieBeanie';

const EXIT_AT = 1.2;
const CUP_IN_AT = 2.2;
const HAND_IN_AT = 2.5;
const STIR_FROM = 2.95;
const STIR_TO = 6.55;
const RAMP = 0.4;
const PCT_FROM = 3.0;
const PCT_TO = 6.55;
const SCRUB_FROM = 3.1;
const SKIP_TO = 6.6;
const HAND_OUT_AT = 6.7;
const CUP_OUT_AT = 7.0;
const BUTTON_IN_AT = 7.3;
const TITLE_IN_AT = 7.4;
const NOTES_IN_AT = 7.45;
const CUP_BACK_AT = 7.5;
const SPLASH_AT = 7.75;
const DROPS_AT = 7.8;
const REST_AT = 8.4;
/** The spoon's speed, radians a second (0.85 s a turn). */
const OMEGA = (2 * Math.PI) / 0.85;
const BOIL_RATE = 8;
const REST_TILT = -14;
const DEG = Math.PI / 180;

// ---- Easing (worklets, declared before anything that calls them) ----

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}
function span(t: number, from: number, length: number) {
  'worklet';
  return clamp01((t - from) / length);
}
function easeIn(u: number) {
  'worklet';
  return u * u * u;
}
function easeOut(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 3);
}
function easeOutQuint(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 5);
}
function backOut(u: number, s = 1.7) {
  'worklet';
  const v = u - 1;
  return 1 + (s + 1) * v * v * v + s * v * v;
}
function backIn(u: number, s = 1.7) {
  'worklet';
  return (s + 1) * u * u * u - s * u * u;
}
/** A damped spring from 0 to 1 that overshoots by about 8% and settles exactly at u = 1. */
function spring(u: number) {
  'worklet';
  const c = clamp01(u);
  return 1 - Math.exp(-5 * c) * Math.cos(2.5 * Math.PI * c) * (1 - c);
}
/** How far the spoon has turned (seconds at full speed), with its speed ramped in and out. */
function stirTravel(t: number) {
  'worklet';
  const a = STIR_FROM;
  const b = STIR_TO;
  const r = RAMP;
  if (t < a) return 0;
  if (t < a + r) return ((t - a) * (t - a)) / (2 * r);
  if (t < b) return r / 2 + (t - a - r);
  if (t < b + r) return r / 2 + (b - a - r) + (t - b) - ((t - b) * (t - b)) / (2 * r);
  return b - a;
}
function stirSpeed(t: number) {
  'worklet';
  return clamp01((t - STIR_FROM) / RAMP) * (1 - clamp01((t - STIR_TO) / RAMP));
}
function percent(t: number) {
  'worklet';
  return 100 * clamp01((t - PCT_FROM) / (PCT_TO - PCT_FROM));
}
/** How far a block of text is pushed off screen, leaving and coming back. */
function slide(t: number, outAt: number, inAt: number, distance: number) {
  'worklet';
  if (t < 5) return distance * easeIn(span(t, outAt, 0.5));
  return distance * (1 - easeOutQuint(span(t, inAt, 0.75)));
}

// ---- Ink, recorded once per group and boil variant ----

type Pics = {
  cup: SkPicture[];
  splash: SkPicture[];
  drops: SkPicture[][];
  top: SkPicture[];
  swirl: SkPicture[];
  hand: SkPicture[];
  thumb: SkPicture[];
  centres: number[];
};

function pathOf(points: number[]) {
  const p = Skia.Path.Make();
  p.moveTo(points[0], points[1]);
  for (let i = 2; i + 1 < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
  p.close();
  return p;
}

function record(strokes: SketchStroke[], variant: number): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(-1200, -1200, 2400, 2400));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  for (const s of strokes) {
    const r = ribbon(s.points, s.closed, s.width, s.seed, variant, s.jitter);
    if (s.fill !== 0 && r.centre.length > 4) {
      paint.setColor(Skia.Color(s.fill === 1 ? GROUND : INK));
      c.drawPath(pathOf(r.centre), paint);
    }
    if (s.width >= 0.1 && r.outline.length > 4) {
      paint.setColor(Skia.Color(INK));
      c.drawPath(pathOf(r.outline), paint);
    }
  }
  return recorder.finishRecordingAsPicture();
}

function buildPics(): Pics {
  const variants = Array.from({ length: VARIANTS }, (_, v) => v);
  const centres: number[] = [];
  for (const d of DROPS) {
    let x = 0;
    let y = 0;
    const n = d.points.length / 2;
    for (let i = 0; i < n; i++) {
      x += d.points[2 * i];
      y += d.points[2 * i + 1];
    }
    centres.push(x / n, y / n);
  }
  return {
    cup: variants.map((v) => record(CUP, v)),
    splash: variants.map((v) => record(SPLASH, v)),
    drops: variants.map((v) => DROPS.map((d) => record([d], v))),
    top: variants.map((v) => record(TOP, v)),
    swirl: variants.map((v) => record(SWIRL, v)),
    hand: variants.map((v) => record(HAND, v)),
    thumb: variants.map((v) => record(THUMB, v)),
    centres,
  };
}

// ---- Drawing (worklets) ----

type Layout = { width: number; height: number; top: number };

function fillPaint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(clamp01(alpha));
  return p;
}

function drawWelcomeArt(c: SkCanvas, t: number, ambient: number, variant: number, l: Layout, pics: Pics) {
  'worklet';
  if (t >= 1.95 && t < CUP_BACK_AT) return;
  const rimX = l.width * 0.656;
  const rimY = l.height - 258;
  let x: number;
  let y: number;
  let tilt: number;
  if (t < 5) {
    const u = easeIn(span(t, EXIT_AT, 0.65));
    const lift = 14 * Math.sin(Math.PI * span(t, EXIT_AT, 0.3));
    x = rimX + l.width * 0.8 * u;
    y = rimY + l.height * 0.34 * u - lift;
    tilt = REST_TILT + 30 * u;
  } else {
    const p = spring(span(t, CUP_BACK_AT, 0.9));
    const fx = rimX + l.width * 0.62;
    const fy = rimY + l.height * 0.36;
    x = fx + (rimX - fx) * p;
    y = fy + (rimY - fy) * p;
    tilt = REST_TILT + 32 * (1 - p);
  }
  const back = t >= 5;
  c.save();
  c.translate(x, y);
  c.rotate(tilt, 0, 0);

  // The splash grows out of the cup, from its left part, on its way back in.
  const grow = back ? spring(span(t, SPLASH_AT, 0.8)) : 1;
  if (grow > 0.001) {
    c.save();
    c.translate(-60, -6);
    c.scale(0.55 + 0.45 * grow, grow);
    c.translate(60, 6);
    c.drawPicture(pics.splash[variant]);
    c.restore();
  }

  // Drops: thrown out of the cup to their places, then bobbing.
  const lx = -40;
  const ly = -20;
  const count = pics.centres.length / 2;
  for (let i = 0; i < count; i++) {
    const u = back ? span(t, DROPS_AT + 0.04 * i, 0.6) : 1;
    if (u <= 0) continue;
    const cx = pics.centres[2 * i];
    const cy = pics.centres[2 * i + 1];
    const q = backOut(u, 1.4);
    const bob = 2.4 * Math.sin((2 * Math.PI * ambient) / 2.5 + i * 1.7);
    const s = 0.35 + 0.65 * easeOut(u);
    c.save();
    c.translate(lx + (cx - lx) * q, ly + (cy - ly) * q + bob);
    c.scale(s, s);
    c.translate(-cx, -cy);
    c.drawPicture(pics.drops[variant][i]);
    c.restore();
  }

  c.drawPicture(pics.cup[variant]);
  c.restore();
}

function drawButton(c: SkCanvas, t: number, l: Layout) {
  'worklet';
  let scale: number;
  let alpha: number;
  if (t < 5) {
    scale = 1 - 0.8 * easeIn(span(t, EXIT_AT, 0.22));
    alpha = 1 - span(t, EXIT_AT + 0.22, 0.13);
  } else {
    alpha = span(t, BUTTON_IN_AT, 0.08);
    scale = 0.2 + 0.8 * backOut(span(t, BUTTON_IN_AT + 0.08, 0.3), 2.2);
  }
  if (alpha <= 0.001 || scale <= 0.001) return;
  const cx = 38;
  const cy = l.height - 52;
  c.drawCircle(cx, cy, 24 * scale, fillPaint(INK, alpha));
  const chevron = clamp01((scale - 0.6) / 0.4);
  if (chevron > 0) {
    const p = Skia.Path.Make();
    p.moveTo(cx - 4 * scale, cy - 9 * scale);
    p.lineTo(cx + 5 * scale, cy);
    p.lineTo(cx - 4 * scale, cy + 9 * scale);
    const paint = fillPaint(GROUND, alpha * chevron);
    paint.setStyle(PaintStyle.Stroke);
    paint.setStrokeWidth(2.2);
    paint.setStrokeCap(StrokeCap.Round);
    paint.setStrokeJoin(StrokeJoin.Round);
    c.drawPath(p, paint);
  }
}

/** Draws text centred at x with 2 pt between glyphs, its box's vertical centre at y. */
function drawSpaced(c: SkCanvas, text: string, x: number, y: number, font: SkFont, alpha: number) {
  'worklet';
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const spacing = 2;
  let total = 0;
  for (const w of widths) total += w + spacing;
  const m = font.getMetrics();
  const baseline = y - (m.ascent + m.descent) / 2;
  const paint = fillPaint(INK, alpha);
  // The pen line is thin: a second pass a hair to the right gives it a marker's weight.
  for (const dx of [0, 0.8]) {
    let at = x - total / 2 + dx;
    for (let i = 0; i < text.length; i++) {
      c.drawText(text[i], at, baseline + (dx > 0 ? 0.3 : 0), paint, font);
      at += widths[i] + spacing;
    }
  }
}

function drawLoader(c: SkCanvas, t: number, variant: number, l: Layout, pics: Pics, font: SkFont) {
  'worklet';
  if (t < CUP_IN_AT || t >= CUP_OUT_AT + 0.32) return;
  const cx = l.width / 2;
  const cy = l.height * 0.5;
  const grow = backOut(span(t, CUP_IN_AT, 0.4), 2.0);
  const shrink = 1 - backIn(span(t, CUP_OUT_AT, 0.3), 2.2);
  const scale = Math.max(0, grow * shrink);

  // The percentage.
  const pctAlpha = span(t, CUP_IN_AT + 0.15, 0.25) * (1 - span(t, CUP_OUT_AT, 0.25));
  if (pctAlpha > 0) drawSpaced(c, `${Math.floor(percent(t))}%`, cx, cy + 96, font, pctAlpha);
  if (scale <= 0.001) return;

  // The cup: a pale shadow, the cup itself (ground inside), its sketched ring and handle.
  const r = CUP_RADIUS;
  c.save();
  c.translate(cx, cy);
  c.scale(scale, scale);
  const shadow = fillPaint(SHADOW);
  c.drawOval(Skia.XYWHRect(-r - 2, -r + 6, 2 * r, 2 * r), shadow);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(r - 4, -2, 22, 13), 5, 5), shadow);
  const ground = fillPaint(GROUND);
  c.drawOval(Skia.XYWHRect(-r, -r, 2 * r, 2 * r), ground);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(r - 2, -6, 20, 12), 5, 5), ground);
  c.drawPicture(pics.top[variant]);

  // The stir.
  const theta = Math.PI + OMEGA * stirTravel(t);
  const swirlAlpha = stirSpeed(t);
  if (swirlAlpha > 0.01) {
    c.save();
    c.rotate(theta / DEG, 0, 0);
    c.saveLayer(fillPaint(INK, swirlAlpha));
    c.drawPicture(pics.swirl[variant]);
    c.restore();
    c.restore();
  }
  c.restore();

  // The hand, arriving and leaving along its arm.
  const enter = 1 - easeOut(span(t, HAND_IN_AT, 0.6));
  const leave = easeIn(span(t, HAND_OUT_AT, 0.45));
  if (enter + leave >= 1) return;
  const away = ARM_AWAY * (enter + leave);
  const relX = STIR_RADIUS * Math.cos(theta);
  const relY = STIR_RADIUS * Math.sin(theta);
  const tipX = cx + relX + ARM_DIRECTION.x * away;
  const tipY = cy + relY + ARM_DIRECTION.y * away;
  const pinchX = cx + PINCH.x + 0.35 * relX + ARM_DIRECTION.x * away;
  const pinchY = cy + PINCH.y + 0.35 * relY + ARM_DIRECTION.y * away;
  // Keep the arm pointing at the same shoulder off screen.
  const shoulderX = cx + PINCH.x + ARM_DIRECTION.x * 420;
  const shoulderY = cy + PINCH.y + ARM_DIRECTION.y * 420;
  const restAngle = Math.atan2(ARM_DIRECTION.y, ARM_DIRECTION.x);
  const nowAngle = Math.atan2(shoulderY - pinchY, shoulderX - pinchX);
  const turn = ((nowAngle - restAngle) * (1 - enter - leave)) / DEG;
  c.save();
  c.translate(pinchX, pinchY);
  c.rotate(turn, 0, 0);
  c.drawPicture(pics.hand[variant]);
  c.restore();

  // The spoon: its handle into the fist, and a small bowl in the coffee.
  const ink = fillPaint(INK);
  const spoon = ribbon([tipX, tipY, pinchX, pinchY], false, 4.2, 98, variant, 0.35).outline;
  const sp = Skia.Path.Make();
  sp.moveTo(spoon[0], spoon[1]);
  for (let i = 2; i + 1 < spoon.length; i += 2) sp.lineTo(spoon[i], spoon[i + 1]);
  sp.close();
  c.drawPath(sp, ink);
  c.save();
  c.translate(tipX, tipY);
  c.rotate(Math.atan2(pinchY - tipY, pinchX - tipX) / DEG, 0, 0);
  c.drawOval(Skia.XYWHRect(-5.5, -4.2, 11, 8.4), ink);
  c.restore();
  c.save();
  c.translate(pinchX, pinchY);
  c.rotate(turn, 0, 0);
  c.drawPicture(pics.thumb[variant]);
  c.restore();
}

// ---- Text that slides ----

type LineProps = {
  text: string;
  x: number;
  y: number;
  right?: boolean;
  outAt: number;
  inAt: number;
  distance: number;
  style: TextStyle;
  time: SharedValue<number>;
  width: number;
};

/** One line of the welcome, its box centred on y, left- or right-aligned at x, sliding with the script. */
function Line({ text, x, y, right, outAt, inAt, distance, style, time, width }: LineProps) {
  const animated = useAnimatedStyle(() => {
    const t = time.get();
    const hidden = t >= 1.95 && t < TITLE_IN_AT;
    return { opacity: hidden ? 0 : 1, transform: [{ translateX: slide(t, outAt, inAt, distance) }] };
  });
  const place = right ? { right: width - x, alignItems: 'flex-end' as const } : { left: x };
  return (
    <Animated.View pointerEvents="none" style={[styles.line, { top: y - 60 }, place, animated]}>
      <Animated.Text style={[styles.text, style]} numberOfLines={1}>
        {text}
      </Animated.Text>
    </Animated.View>
  );
}

// ---- The screen ----

export function SketchCoffeeSplash({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({
    [HAND_FONT]: require('./assets/CoveredByYourGrace.ttf'),
    [PEN_FONT]: require('./assets/ReenieBeanie.ttf'),
  });
  const pctFont = useFont(require('./assets/ReenieBeanie.ttf'), 40);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const pics = useMemo(() => buildPics(), []);
  const layout = useMemo<Layout>(() => ({ width, height, top: insets.top }), [width, height, insets.top]);

  // The clock: the script's time is worked out from a start time, so the loop keeps a constant period;
  // a touch moves the start or holds the time.
  const now = useSharedValue(0);
  const origin = useSharedValue(-1);
  const running = useSharedValue(false);
  const held = useSharedValue(-1);
  const scrubbing = useSharedValue(false);
  const lastAngle = useSharedValue(0);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const time = useSharedValue(0);
  const ambient = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const s = info.timestamp / 1000;
    now.set(s);
    if (origin.get() < 0) origin.set(s);
    let t: number;
    if (held.get() >= 0) t = held.get();
    else {
      const e = Math.max(0, s - origin.get());
      t = autoPlay ? e % LOOP : running.get() && e < LOOP ? e : 0;
    }
    time.set(t);
    // The boil runs on the script's clock while it loops, so a recorded loop repeats exactly.
    ambient.set(autoPlay || held.get() >= 0 ? t : s % 1000);
  }, false);
  // The clock starts once the fonts are in (until then the screen is empty), so the welcome gets its full rest.
  const ready = fontsLoaded && pctFont !== null;
  useEffect(() => {
    if (ready && !reduced) origin.set(-1);
  }, [ready, reduced, origin]);
  useEffect(() => {
    frame.setActive(active && !reduced && ready);
    if (reduced) {
      time.set(0);
      ambient.set(0);
    }
    return () => frame.setActive(false);
  }, [active, reduced, ready, frame, time, ambient]);

  const tap = () => {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  };
  const tick = () => {
    void Haptics.selectionAsync();
  };
  const cx = width / 2;
  const cy = height * 0.5;
  const pan = Gesture.Pan()
    .minDistance(0)
    .enabled(!reduced)
    .onBegin((e) => {
      startX.set(e.x);
      startY.set(e.y);
      scrubbing.set(false);
    })
    .onUpdate((e) => {
      const t = time.get();
      if (!scrubbing.get()) {
        const near = Math.hypot(startX.get() - cx, startY.get() - cy) < 150;
        if (!near || t < SCRUB_FROM || t >= PCT_TO || Math.hypot(e.x - startX.get(), e.y - startY.get()) <= 6) return;
        scrubbing.set(true);
        held.set(t);
        lastAngle.set(Math.atan2(e.y - cy, e.x - cx));
        return;
      }
      const a = Math.atan2(e.y - cy, e.x - cx);
      let d = a - lastAngle.get();
      lastAngle.set(a);
      if (d > Math.PI) d -= 2 * Math.PI;
      if (d < -Math.PI) d += 2 * Math.PI;
      if (d <= 0) return;
      const h = held.get();
      const next = Math.min(PCT_TO, h + d / OMEGA);
      held.set(next);
      if (Math.floor(percent(next) / 10) !== Math.floor(percent(h) / 10)) scheduleOnRN(tick);
    })
    // onFinalize, not onEnd: a tap never activates the pan (it does not move), and onEnd only runs after a drag.
    .onFinalize((e) => {
      const s = now.get();
      if (scrubbing.get()) {
        scrubbing.set(false);
        origin.set(s - held.get());
        running.set(true);
        held.set(-1);
        return;
      }
      if (Math.hypot(e.translationX, e.translationY) >= 10) return;
      const t = time.get();
      let target = -1;
      if (t < EXIT_AT || t >= REST_AT) target = EXIT_AT;
      else if (t >= CUP_IN_AT && t < SKIP_TO) target = SKIP_TO;
      if (target < 0) return;
      origin.set(s - target);
      running.set(true);
      scheduleOnRN(tap);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    c.drawColor(Skia.Color(GROUND));
    const t = time.get();
    const variant = reduced ? 0 : Math.floor(ambient.get() * BOIL_RATE) % VARIANTS;
    drawWelcomeArt(c, t, reduced ? 0 : ambient.get(), variant, layout, pics);
    drawButton(c, t, layout);
    if (pctFont) drawLoader(c, t, variant, layout, pics, pctFont);
    return recorder.finishRecordingAsPicture();
  }, [layout, pics, pctFont, reduced]);

  if (!fontsLoaded || !pctFont) return <View style={styles.screen} />;

  const top = insets.top;
  const w = width;
  const h = height;
  const title = (i: number) => ({ outAt: EXIT_AT + 0.05 + 0.04 * i, inAt: TITLE_IN_AT + 0.05 * i, distance: w * 1.1 });
  const note = (i: number) => ({ outAt: EXIT_AT + 0.1 + 0.03 * i, inAt: NOTES_IN_AT + 0.04 * i, distance: -w * 0.6 });
  const shared = { time, width: w };
  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="button"
        accessibilityLabel="Inkpot Coffee Co. Welcome to our coffee haven! 12 Linden Row, Old Town. Open 7 am to 8 pm: coffee, milkshakes, desserts."
      >
        <StatusBar style="light" />
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
        <Line text="Welcome to" x={20} y={top + 66} style={styles.title} {...title(0)} {...shared} />
        <Line text="our coffee" x={w - 22} y={top + 116} right style={styles.title} {...title(1)} {...shared} />
        <Line text="haven!" x={w - 18} y={top + 176} right style={styles.haven} {...title(2)} {...shared} />
        <Line text="Inkpot Coffee Co." x={w - 203} y={top + 230} style={styles.brand} {...title(3)} {...shared} />
        <Line text="12 Linden Row, Old Town" x={w - 203} y={top + 250} style={styles.address} {...title(4)} {...shared} />
        <Line text="7 am - 8 pm" x={20} y={h - 232} style={styles.hours} {...note(0)} {...shared} />
        <Line text="7 am - 8 pm" x={20.7} y={h - 231.7} style={styles.hours} {...note(0)} {...shared} />
        <Line text="Coffee" x={20} y={h - 203} style={styles.note} {...note(1)} {...shared} />
        <Line text="Milkshakes" x={20} y={h - 179} style={styles.note} {...note(2)} {...shared} />
        <Line text="Desserts" x={20} y={h - 155} style={styles.note} {...note(3)} {...shared} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: GROUND, overflow: 'hidden' },
  line: { position: 'absolute', height: 120, justifyContent: 'center' },
  text: { color: INK, includeFontPadding: false },
  title: { fontSize: 50, fontWeight: '500', letterSpacing: -1 },
  haven: { fontFamily: HAND_FONT, fontSize: 78 },
  brand: { fontSize: 14, fontWeight: '500', opacity: 0.9 },
  address: { fontSize: 14, opacity: 0.8 },
  hours: { fontFamily: PEN_FONT, fontSize: 26 },
  note: { fontSize: 15, opacity: 0.9 },
});
