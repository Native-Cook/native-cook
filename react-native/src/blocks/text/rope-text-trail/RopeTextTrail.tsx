import { Canvas, Picture, Skia, type SkFont, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Rope Text Trail — a sentence that behaves like a rope of letters on grained paper. A port of the
 SwiftUI block (RopeTextTrail.swift); the numbers are the same.

 The sentence is a chain: its head (a small orange dot just ahead of the closing dash) moves, and every
 letter sits on the path the head has drawn, its centre placed by its own advance behind the head and
 turned to the path's tangent. The baseline runs half an x-height under the path, so the path threads
 through the middle of the lowercase letters.

 Resting curve: y = cy − A·sin(2πu) from x = 20 to x = W − 20 (u = 0 … 1, cy = 50 % of the height),
 A solved so the curve is the rope's length (sentence + 9 pt gap + dot) plus 20 pt, the rope centred on it.

 autoPlay timeline (LOOP = 10 s, e = seconds into the loop):
   0.0 … 1.5   rest. The rope sways once over the rest window (7.7 → 11.5, wrapping): the middle of the
               curve sags up to 9 pt and lifts back, a(φ) = sin(2πφ)·sin(πφ) / 0.77, so it starts and stops still.
   1.5 … 7.7   the head draws its path: up the right side, a loop over the top leftwards, down across the
               middle, a loop along the bottom leftwards, up the left edge, then along the resting curve itself
               back to its place. Arc length follows easeInOutSine, so it starts and arrives at rest. Speed m =
               speed / 700 pt/s (≤ 1) stretches the spacing by up to 10 %, and the tail letters shrink by up to
               18 % and fade by up to 40 % (linearly less towards the head).
   7.7 … 10.0  rest; t = 10 is the same frame as t = 0 (the sway is zero at both ends of its window).

 Touch: dragging anywhere grabs the head; it glides onto the finger (time constant 0.14 s) and the rope
 follows the path it draws (a light haptic on grab). On release every letter springs to its place on the
 resting curve (0.9 s each, damped spring with a 4 % overshoot, the head first and the tail 0.3 s later,
 a soft haptic). 2 s after the release the loop starts again from rest.
 Reduced motion: the rope rests on the curve without swaying and the loop is off; a drag still pulls it,
 and on release it fades back onto the curve (0.35 s).
 */

const LOOP = 10;
const MOVE_START = 1.5;
const MOVE_END = 7.7;
const FONT_SIZE = 32;
const DOT_GAP = 9;
const MARGIN = 20;
const RELAX_SPAN = 1.2;
const FOLLOW = 0.14;
const PAPER = '#F2EFE8';
const INK = '#1A1814';
const ACCENT = '#FF4D1F';
const INK_RGB = [0x1a / 255, 0x18 / 255, 0x14 / 255];
const ACCENT_RGB = [1, 0x4d / 255, 0x1f / 255];
const REGULAR_FILE = require('./assets/InstrumentSerif-Regular.ttf');
const ITALIC_FILE = require('./assets/InstrumentSerif-Italic.ttf');
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

/** A polyline measured by arc length. */
type Poly = { xs: number[]; ys: number[]; lengths: number[] };

type Layout = {
  chars: string[];
  accent: boolean[];
  advances: number[];
  /** Each letter's centre, measured from the start of the sentence. */
  centers: number[];
  textLength: number;
  xHeight: number;
  x0: number;
  x1: number;
  cy: number;
  amp: number;
  /** Arc length on the resting curve where the sentence starts, and where the head rests. */
  tailStart: number;
  headRest: number;
  rest: Poly;
  /** The autoplay path: a lead-in, the resting curve up to the head, the drawn loops, the resting curve again. */
  master: Poly;
  masterStart: number;
  masterEnd: number;
};

/** Every letter's centre, angle, scale and opacity, and the head. */
type Rope = { xs: number[]; ys: number[]; angles: number[]; scales: number[]; opacities: number[]; hx: number; hy: number };

function makePoly(xs: number[], ys: number[]): Poly {
  'worklet';
  const lengths = [0];
  for (let i = 1; i < xs.length; i++) lengths.push(lengths[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]));
  return { xs, ys, lengths };
}

function polyLength(p: Poly) {
  'worklet';
  return p.lengths.length ? p.lengths[p.lengths.length - 1] : 0;
}

function polyPoint(p: Poly, s0: number): [number, number] {
  'worklet';
  if (p.xs.length < 2) return [p.xs[0] ?? 0, p.ys[0] ?? 0];
  const s = Math.min(Math.max(s0, 0), polyLength(p));
  let lo = 0;
  let hi = p.lengths.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (p.lengths[mid] <= s) lo = mid;
    else hi = mid;
  }
  const span = p.lengths[hi] - p.lengths[lo];
  const t = span > 0 ? (s - p.lengths[lo]) / span : 0;
  return [p.xs[lo] + (p.xs[hi] - p.xs[lo]) * t, p.ys[lo] + (p.ys[hi] - p.ys[lo]) * t];
}

/** The point at arc length `s` and the tangent's angle, taken over ±4 pt so corners stay smooth. */
function polySample(p: Poly, s: number): [number, number, number] {
  'worklet';
  const c = polyPoint(p, s);
  const a = polyPoint(p, s - 4);
  const b = polyPoint(p, s + 4);
  return [c[0], c[1], Math.atan2(b[1] - a[1], b[0] - a[0])];
}

function curve(x0: number, x1: number, cy: number, amp: number, sway: number, count: number): Poly {
  'worklet';
  const xs: number[] = [];
  const ys: number[] = [];
  for (let i = 0; i <= count; i++) {
    const u = i / count;
    xs.push(x0 + (x1 - x0) * u);
    ys.push(cy - amp * Math.sin(2 * Math.PI * u) + 9 * sway * Math.sin(Math.PI * u));
  }
  return makePoly(xs, ys);
}

/** The sway's amount over its window, φ = 0 … 1: a sag and a lift that start and end still. */
function sway(phi: number) {
  'worklet';
  return (Math.sin(2 * Math.PI * phi) * Math.sin(Math.PI * phi)) / 0.77;
}

function easeInOutSine(t: number) {
  'worklet';
  return -(Math.cos(Math.PI * t) - 1) / 2;
}

/** A damped spring from 0 to 1 over x = 0 … 1, with a 4 % overshoot; exactly 0 before and 1 after. */
function spring(x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  return 1 - Math.exp(-6 * x) * Math.cos(5.65 * x);
}

function catmullRom(p0: number[], p1: number[], p2: number[], p3: number[], t: number): number[] {
  const t2 = t * t;
  const t3 = t2 * t;
  const f = (a: number, b: number, c: number, d: number) =>
    0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
  return [f(p0[0], p1[0], p2[0], p3[0]), f(p0[1], p1[1], p2[1], p3[1])];
}

function buildLayout(text: string, accent: string, width: number, height: number, regular: SkFont, italic: SkFont): Layout {
  const chars = Array.from(text);
  const mask = chars.map(() => false);
  const from = text.indexOf(accent);
  if (from >= 0) for (let i = from; i < from + Array.from(accent).length; i++) mask[i] = true;
  const advances = chars.map((ch, i) => {
    const font = mask[i] ? italic : regular;
    return font.getGlyphWidths(font.getGlyphIDs(ch))[0] ?? 0;
  });
  const centers: number[] = [];
  let s = 0;
  for (const a of advances) {
    centers.push(s + a / 2);
    s += a;
  }
  // The x-height from the bounds of an x (their top is above the baseline, so negative).
  const xHeight = -regular.measureText('x').y || FONT_SIZE * 0.51;
  const x0 = MARGIN;
  const x1 = width - MARGIN;
  const cy = height * 0.5;
  const ropeLength = s + DOT_GAP + 4;
  // Solve the amplitude so the curve is the rope's length plus 20 pt.
  const target = ropeLength + 20;
  let lo = 0;
  let hi = 160;
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    if (polyLength(curve(x0, x1, 0, mid, 0, 400)) < target) lo = mid;
    else hi = mid;
  }
  const amp = (lo + hi) / 2;
  const rest = curve(x0, x1, cy, amp, 0, 200);
  const tailStart = (polyLength(rest) - ropeLength) / 2;
  const headRest = tailStart + s + DOT_GAP;
  const onRest = (d: number) => polyPoint(rest, d) as number[];

  const pts: number[][] = [];
  const a0 = polySample(rest, 0)[2];
  for (let k = 240; k > 0; k -= 20) pts.push([rest.xs[0] - Math.cos(a0) * k, rest.ys[0] - Math.sin(a0) * k]);
  for (let i = 0; i < rest.xs.length; i++) if (rest.lengths[i] < headRest) pts.push([rest.xs[i], rest.ys[i]]);
  pts.push(onRest(headRest));
  const length = () =>
    polyLength(
      makePoly(
        pts.map((p) => p[0]),
        pts.map((p) => p[1]),
      ),
    );
  const masterStart = length();
  const w = width;
  const h = height;
  const controls = [
    onRest(headRest - 40), onRest(headRest),
    [0.89 * w, 0.375 * h], [0.78 * w, 0.235 * h],
    [0.56 * w, 0.18 * h], [0.34 * w, 0.22 * h],
    [0.22 * w, 0.33 * h], [0.3 * w, 0.45 * h],
    [0.55 * w, 0.535 * h], [0.76 * w, 0.63 * h],
    [0.78 * w, 0.76 * h], [0.62 * w, 0.835 * h],
    [0.38 * w, 0.82 * h], [0.19 * w, 0.73 * h],
    [0.11 * w, 0.615 * h],
    onRest(0), onRest(40),
  ];
  for (let seg = 1; seg < controls.length - 2; seg++) {
    for (let k = 1; k <= 24; k++) pts.push(catmullRom(controls[seg - 1], controls[seg], controls[seg + 1], controls[seg + 2], k / 24));
  }
  // The drawn part ends on the resting curve's first point, so from there the arc runs as on the curve.
  const drawn = length();
  for (let i = 1; i < rest.xs.length; i++) if (rest.lengths[i] < headRest + 60) pts.push([rest.xs[i], rest.ys[i]]);
  const master = makePoly(
    pts.map((p) => p[0]),
    pts.map((p) => p[1]),
  );
  return {
    chars, accent: mask, advances, centers, textLength: s, xHeight,
    x0, x1, cy, amp, tailStart, headRest, rest, master, masterStart, masterEnd: drawn + headRest,
  };
}

/** The rope lying on the resting curve, swayed by `amount` (−1 … 1). */
function restRope(L: Layout, amount: number): Rope {
  'worklet';
  const line = amount === 0 ? L.rest : curve(L.x0, L.x1, L.cy, L.amp, amount, 120);
  const r: Rope = { xs: [], ys: [], angles: [], scales: [], opacities: [], hx: 0, hy: 0 };
  for (const c of L.centers) {
    const p = polySample(line, L.tailStart + c);
    r.xs.push(p[0]);
    r.ys.push(p[1]);
    r.angles.push(p[2]);
    r.scales.push(1);
    r.opacities.push(1);
  }
  const head = polyPoint(line, L.headRest);
  r.hx = head[0];
  r.hy = head[1];
  return r;
}

/** The rope along a drawn line whose head is at arc length `head`, moving with `m` (0 … 1). */
function ropeOn(L: Layout, line: Poly, head: number, m: number): Rope {
  'worklet';
  const stretch = 1 + 0.1 * m;
  const n = Math.max(L.chars.length - 1, 1);
  const r: Rope = { xs: [], ys: [], angles: [], scales: [], opacities: [], hx: 0, hy: 0 };
  for (let i = 0; i < L.centers.length; i++) {
    const p = polySample(line, head - DOT_GAP - (L.textLength - L.centers[i]) * stretch);
    const tail = 1 - i / n;
    r.xs.push(p[0]);
    r.ys.push(p[1]);
    r.angles.push(p[2]);
    r.scales.push(1 - 0.18 * m * tail);
    r.opacities.push(1 - 0.4 * m * tail);
  }
  const h = polyPoint(line, head);
  r.hx = h[0];
  r.hy = h[1];
  return r;
}

/** The rope at `e` seconds into the autoplay loop. */
function autoRope(L: Layout, e: number): Rope {
  'worklet';
  if (e < MOVE_START || e >= MOVE_END) {
    const phi = (e >= MOVE_END ? e - MOVE_END : e + LOOP - MOVE_END) / (LOOP - (MOVE_END - MOVE_START));
    return restRope(L, sway(phi));
  }
  const tau = (e - MOVE_START) / (MOVE_END - MOVE_START);
  const head = L.masterStart + easeInOutSine(tau) * (L.masterEnd - L.masterStart);
  const speed = ((L.masterEnd - L.masterStart) * Math.PI) / 2 * Math.sin(Math.PI * tau) / (MOVE_END - MOVE_START);
  return ropeOn(L, L.master, head, Math.min(1, speed / 700));
}

/** The rope springing back from the release snapshot to `rest`, the head first. */
function relaxRope(from: Rope, rest: Rope, elapsed: number): Rope {
  'worklet';
  const n = Math.max(rest.xs.length - 1, 1);
  const r: Rope = { xs: [], ys: [], angles: [], scales: [], opacities: [], hx: 0, hy: 0 };
  for (let i = 0; i < rest.xs.length; i++) {
    const delay = 0.3 * (1 - i / n);
    const p = spring((elapsed - delay) / 0.9);
    const q = Math.min(1, p);
    let da = rest.angles[i] - from.angles[i];
    da = Math.atan2(Math.sin(da), Math.cos(da));
    r.xs.push(from.xs[i] + (rest.xs[i] - from.xs[i]) * p);
    r.ys.push(from.ys[i] + (rest.ys[i] - from.ys[i]) * p);
    r.angles.push(from.angles[i] + da * p);
    r.scales.push(from.scales[i] + (1 - from.scales[i]) * q);
    r.opacities.push(from.opacities[i] + (1 - from.opacities[i]) * q);
  }
  const ph = spring(elapsed / 0.9);
  r.hx = from.hx + (rest.hx - from.hx) * ph;
  r.hy = from.hy + (rest.hy - from.hy) * ph;
  return r;
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  touching: SharedValue<boolean>;
  /** The line the head has drawn, flat [x, y, x, y, …]. */
  trail: SharedValue<number[]>;
  finger: SharedValue<number[]>;
  grab: SharedValue<number[]>;
  grabTime: SharedValue<number>;
  lastTime: SharedValue<number>;
  speed: SharedValue<number>;
  relaxStart: SharedValue<number>;
  relaxFrom: SharedValue<Rope | null>;
  restSince: SharedValue<number>;
  fadeStart: SharedValue<number>;
  resumeAt: SharedValue<number>;
};

function trailPoly(trail: number[]): Poly {
  'worklet';
  const xs: number[] = [];
  const ys: number[] = [];
  for (let i = 0; i + 1 < trail.length; i += 2) {
    xs.push(trail[i]);
    ys.push(trail[i + 1]);
  }
  return makePoly(xs, ys);
}

/** The rope while a finger holds it (the trail is grown by the frame callback). */
function touchRope(m: Motion, L: Layout): Rope {
  'worklet';
  const trail = m.trail.get();
  if (trail.length < 4) return restRope(L, 0);
  const line = trailPoly(trail);
  return ropeOn(L, line, polyLength(line), Math.min(1, m.speed.get() / 700));
}

function ropeAt(m: Motion, L: Layout, now: number, reduced: boolean, autoPlay: boolean): Rope {
  'worklet';
  if (m.touching.get()) return touchRope(m, L);
  const relax = m.relaxStart.get();
  if (relax >= 0) {
    const rest = restRope(L, 0);
    if (reduced) return rest;
    const from = m.relaxFrom.get();
    if (from && now - relax < RELAX_SPAN) return relaxRope(from, rest, now - relax);
  }
  const start = m.autoStart.get();
  if (start >= 0 && now >= start && !reduced) return autoRope(L, (now - start) % LOOP);
  if (!autoPlay && !reduced) {
    // Without the loop the resting rope keeps swaying, one sway every 3.8 s.
    const phi = ((now - m.restSince.get()) / 3.8) % 1;
    return restRope(L, sway(phi));
  }
  return restRope(L, 0);
}

function grabFeedback() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function releaseFeedback() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
}

type Props = {
  text?: string;
  accent?: string;
  label?: string;
  issue?: string;
  hint?: string;
  autoPlay?: boolean;
};

export function RopeTextTrail({
  text = 'words follow you wherever you go —',
  accent = 'you',
  label = 'A LINE OF THOUGHT',
  issue = 'No. 07',
  hint = 'Drag to write in the air',
  autoPlay = false,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const regular = useFont(REGULAR_FILE, FONT_SIZE);
  const italic = useFont(ITALIC_FILE, FONT_SIZE);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const layout = useMemo(
    () => (regular && italic && size.width > 0 ? buildLayout(text, accent, size.width, size.height, regular, italic) : null),
    [regular, italic, size.width, size.height, text, accent],
  );

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    touching: useSharedValue(false),
    trail: useSharedValue<number[]>([]),
    finger: useSharedValue<number[]>([0, 0]),
    grab: useSharedValue<number[]>([0, 0]),
    grabTime: useSharedValue(0),
    lastTime: useSharedValue(0),
    speed: useSharedValue(0),
    relaxStart: useSharedValue(-1),
    relaxFrom: useSharedValue<Rope | null>(null),
    restSince: useSharedValue(0),
    fadeStart: useSharedValue(-10),
    resumeAt: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0) {
      m.restSince.set(now);
      if (autoPlay && !reduced) m.autoStart.set(now);
    }
    m.clock.set(now);
    // While a finger holds the rope, the head glides onto it and the line grows behind it.
    if (m.touching.get()) {
      const k = Math.exp(-Math.max(0, now - m.grabTime.get()) / FOLLOW);
      const f = m.finger.get();
      const g = m.grab.get();
      const hx = f[0] - g[0] * k;
      const hy = f[1] - g[1] * k;
      const trail = m.trail.get();
      const n = trail.length;
      const dist = n >= 2 ? Math.hypot(hx - trail[n - 2], hy - trail[n - 1]) : 0;
      const dt = now - m.lastTime.get();
      if (dt > 0) {
        m.speed.set(m.speed.get() + (dist / dt - m.speed.get()) * Math.min(1, dt / 0.12));
        m.lastTime.set(now);
      }
      if (dist > 0.75 && layout) {
        m.trail.modify((t) => {
          'worklet';
          t.push(hx, hy);
          // Keep only what the rope can reach.
          const keep = layout.textLength * 1.3 + 400;
          const line = trailPoly(t);
          if (polyLength(line) > keep * 1.5) {
            let drop = 0;
            while (drop < line.xs.length - 2 && polyLength(line) - line.lengths[drop] > keep) drop++;
            t.splice(0, drop * 2);
          }
          return t;
        });
      }
    }
    // 2 s after the last touch the loop starts again.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      m.relaxStart.set(-1);
      m.autoStart.set(now);
    }
  });

  const gesture = useMemo(() => {
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        if (!layout) return;
        const now = m.clock.get();
        const rope = ropeAt(m, layout, now, reduced, autoPlay);
        m.resumeAt.set(-1);
        m.autoStart.set(-1);
        m.relaxStart.set(-1);
        m.relaxFrom.set(null);
        // The line so far: behind the tail along its direction, through the letters, to the head.
        const t: number[] = [];
        const back = rope.angles[0] + Math.PI;
        for (let k = 300; k > 0; k -= 20) t.push(rope.xs[0] + Math.cos(back) * k, rope.ys[0] + Math.sin(back) * k);
        for (let i = 0; i < rope.xs.length; i++) t.push(rope.xs[i], rope.ys[i]);
        t.push(rope.hx, rope.hy);
        m.trail.set(t);
        m.finger.set([e.x, e.y]);
        m.grab.set([e.x - rope.hx, e.y - rope.hy]);
        m.grabTime.set(now);
        m.lastTime.set(now);
        m.speed.set(0);
        m.touching.set(true);
        scheduleOnRN(grabFeedback);
      })
      .onUpdate((e) => {
        m.finger.set([e.x, e.y]);
      })
      .onFinalize(() => {
        if (!layout || !m.touching.get()) return;
        const now = m.clock.get();
        m.relaxFrom.set(touchRope(m, layout));
        m.touching.set(false);
        m.relaxStart.set(now);
        m.restSince.set(now + RELAX_SPAN);
        m.fadeStart.set(now);
        scheduleOnRN(releaseFeedback);
        if (autoPlay && !reduced) m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layout, reduced, autoPlay]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (!layout || !regular || !italic) return recorder.finishRecordingAsPicture();
    const now = m.clock.get();
    const rope = ropeAt(m, layout, now, reduced, autoPlay);
    let fade = 1;
    if (reduced && !m.touching.get()) {
      const t = now - m.fadeStart.get();
      fade = t < 0.35 ? Math.max(0, t / 0.35) : 1;
    }
    const paint = Skia.Paint();
    for (let i = 0; i < rope.xs.length; i++) {
      const ch = layout.chars[i];
      if (ch === ' ') continue;
      const rgb = layout.accent[i] ? ACCENT_RGB : INK_RGB;
      paint.setColor(Float32Array.of(rgb[0], rgb[1], rgb[2], rope.opacities[i] * fade));
      canvas.save();
      canvas.translate(rope.xs[i], rope.ys[i]);
      canvas.rotate((rope.angles[i] * 180) / Math.PI, 0, 0);
      canvas.scale(rope.scales[i], rope.scales[i]);
      canvas.drawText(ch, -layout.advances[i] / 2, layout.xHeight / 2, paint, layout.accent[i] ? italic : regular);
      canvas.restore();
    }
    // The head: an orange dot with a soft halo.
    paint.setColor(Float32Array.of(ACCENT_RGB[0], ACCENT_RGB[1], ACCENT_RGB[2], 0.16 * fade));
    canvas.drawCircle(rope.hx, rope.hy, 9, paint);
    paint.setColor(Float32Array.of(ACCENT_RGB[0], ACCENT_RGB[1], ACCENT_RGB[2], fade));
    canvas.drawCircle(rope.hx, rope.hy, 3.5, paint);
    return recorder.finishRecordingAsPicture();
  });

  // Fine paper grain: a fixed scatter of tiny ink dots, recorded once.
  const grain = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    let seed = BigInt(0x9e3779b9);
    const mask = (BigInt(1) << BigInt(64)) - BigInt(1);
    const next = () => {
      seed = (seed * BigInt('6364136223846793005') + BigInt('1442695040888963407')) & mask;
      return Number(seed >> BigInt(33)) / 2 ** 31;
    };
    const paint = Skia.Paint();
    const count = Math.floor((size.width * size.height) / 110);
    for (let i = 0; i < count; i++) {
      const x = next() * size.width;
      const y = next() * size.height;
      const r = 0.35 + next() * 0.55;
      const a = 0.04 + next() * 0.07;
      paint.setColor(Float32Array.of(INK_RGB[0], INK_RGB[1], INK_RGB[2], a));
      canvas.drawCircle(x, y, r, paint);
    }
    return recorder.finishRecordingAsPicture();
  }, [size.width, size.height]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const bottom = Math.max(insets.bottom, 20);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {size.width > 0 ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={grain} />
        </Canvas>
      ) : null}
      {layout ? (
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={text}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View style={[styles.header, { top: insets.top + 16 }]} pointerEvents="none">
        <View style={styles.headerRow}>
          <Text style={styles.label} numberOfLines={1}>
            {label}
          </Text>
          <Text style={styles.label} numberOfLines={1}>
            {issue}
          </Text>
        </View>
        <View style={styles.rule} />
      </View>

      <View style={[styles.hint, { top: size.height - bottom - 32 }]} pointerEvents="none">
        <View style={styles.dot} />
        <Text style={styles.hintText} numberOfLines={1}>
          {hint}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER },
  header: { position: 'absolute', left: 24, right: 24, gap: 12 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between' },
  label: { fontFamily: MONO, fontWeight: '500', fontSize: 11, letterSpacing: 1.6, color: INK, opacity: 0.6 },
  rule: { height: 0.5, backgroundColor: INK, opacity: 0.18 },
  hint: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 10 },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: ACCENT },
  hintText: { fontFamily: MONO, fontWeight: '500', fontSize: 12, letterSpacing: 0.4, color: INK, opacity: 0.6 },
});
