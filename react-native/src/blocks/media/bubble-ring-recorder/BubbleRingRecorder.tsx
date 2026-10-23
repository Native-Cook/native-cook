import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, matchFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
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
  BREATH,
  type Clock,
  DOT_RADIUS,
  type Frame,
  GHOST_LAG,
  LOOP,
  STOP_AT,
  TAKE,
  autoClock,
  bars,
  easeOut,
  frameAt,
  shape,
  smoothstep,
  staticBar,
} from './motion';

/*
 Bubble Ring Recorder — a voice recorder whose outline wobbles like a soap bubble with the voice.
 A port of the SwiftUI block (BubbleRingRecorder.swift); the numbers are the same.

 Look: a charcoal screen (#141416, a soft lighter pool behind the orb). Top left a big thin timer
 (Barlow Condensed ExtraLight 104 pt, the minutes in white 42 %, ":ss" in white) with a caption
 under it; a three-line menu top right. In the middle a glowing red record orb (radius 20 % of the
 width, #FF5C7C → #FF1F4B → #D4093A, "RECORD" set vertically in it) inside a faint 1 pt circle
 (radius 29 % of the width). Under it three small glyphs (note, waveform, list), then a list of
 three takes: "Untitled take" (the last take, with its waveform), "Chorus hum", "Kitchen demo".

 Recording: the orb collapses into a 9 pt red dot that pulses with the level; the faint circle
 becomes a 2.25 pt white line whose radius is pushed in and out by four low harmonics scaled by
 the level (up to ±14 %), with a thinner grey ghost of the shape 0.22 s behind and a soft glow.
 The timer counts, the first row turns into a live "Recording" row whose waveform fills left to
 right. Stopping: the orb springs back out of the dot (stiffness 170, damping 16, ~8 % overshoot),
 the ring gets a jelly kick (radius −7 %, wobble 8 %, e^(−4.5u)·sin(11u)) and fades back to the
 faint circle over 0.6 s; the row shows the new take.

 autoPlay timeline (LOOP = 9 s):
   0.0 – 1.2   rest; the orb's glow breathes (period 2.25 s, four per loop).
   1.2         tap: a ripple leaves the orb (0.7 s); the orb collapses into the dot (0.5 s,
               ease-in-out) while the ring turns white (0.45 s).
   1.2 – 6.4   a scripted 5.2 s take: nine syllables of a voice, a quiet floor between them.
   6.4         tap on the dot: a red ripple (0.6 s), the orb springs back, the ring jiggles out.
   7.4 – 9.0   rest; at 9.0 the frame is the one at 0.0.

 Touch: tap the orb (or the dot) to start or stop a take. Hold anywhere while recording and the
 ring bulges towards the finger and the voice gets louder. 2 s after the last touch the loop
 starts again from rest (a take left running stops itself 8 s after the last touch).
 Reduced motion: no breathing, no wobble; start and stop cross-fade the orb and the dot over
 0.25 s and the ring is a plain circle while recording.
 */

const FONT_NAME = 'BubbleRingRecorder-BarlowCondensed-ExtraLight';
const FONT_FILE = require('./assets/BarlowCondensed-ExtraLight.ttf');
const LETTER_FAMILY = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });
const ROW_HEIGHT = 68;
const BAR_COUNT = 30;
const BACKGROUND = '#141416';
const RED = [1.0, 0.122, 0.294];
const RED_TEXT = '#FF4D6D';

const ROWS = [
  { title: 'Chorus hum', detail: '0:14 · Oct 12', seed: 1.3 },
  { title: 'Kitchen demo', detail: '0:34 · Oct 10', seed: 4.1 },
];

type Layout = { w: number; h: number; top: number; orb: number; ring: number; rowsTop: number; iconsY: number; cx: number; cy: number };

function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const timerBottom = top + 4 + 146;
  const listBottom = h - Math.max(bottom, 16) - 4;
  const rowsTop = listBottom - 3 * ROW_HEIGHT;
  const iconsY = rowsTop - 66;
  return { w, h, top, orb: 0.2 * w, ring: 0.29 * w, rowsTop, iconsY, cx: w / 2, cy: (timerBottom + iconsY - 12) / 2 };
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  now: SharedValue<number>;
  appear: SharedValue<number>;
  autoStart: SharedValue<number>;
  firstAutoStop: SharedValue<number>;
  recStart: SharedValue<number>;
  recStop: SharedValue<number>;
  lastTake: SharedValue<number>;
  /** A finger held on the screen: [x, y, when], or []. */
  press: SharedValue<number[]>;
  /** The release: [x, y, when, how far the pull had got], or []. */
  released: SharedValue<number[]>;
  resumeAt: SharedValue<number>;
  autoStopAt: SharedValue<number>;
};

type Shown = { f: Frame; bulge: number; bulgeAngle: number; lastTake: number };

function clockAt(m: Motion, now: number): Clock {
  'worklet';
  const c = now - m.appear.get();
  const start = m.autoStart.get();
  const lastTake = m.lastTake.get();
  if (start >= 0 && now >= start) {
    const t = now - start;
    const e = t - Math.floor(t / LOOP) * LOOP;
    return autoClock(e, c, lastTake, now >= m.firstAutoStop.get());
  }
  const rs = m.recStart.get();
  if (rs < 0) return { sinceStart: -1, sinceStop: -1, lastTake, clock: c };
  const s = now - rs;
  const stop = m.recStop.get();
  if (stop >= 0) {
    const u = now - stop;
    if (u > 3) return { sinceStart: -1, sinceStop: -1, lastTake, clock: c };
    return { sinceStart: s, sinceStop: u, lastTake, clock: c };
  }
  return { sinceStart: s, sinceStop: -1, lastTake, clock: c };
}

/** The finger's pull on the ring: [amount 0…1, x, y], or null. */
function pullAt(m: Motion, now: number) {
  'worklet';
  const p = m.press.get();
  if (p.length === 3) return [easeOut((now - p[2]) / 0.2), p[0], p[1]];
  const r = m.released.get();
  if (r.length === 4) {
    const a = r[3] * (1 - easeOut((now - r[2]) / 0.35));
    return a > 0.001 ? [a, r[0], r[1]] : null;
  }
  return null;
}

function shownAt(m: Motion, now: number, l: Layout, reduced: boolean): Shown {
  'worklet';
  const k = clockAt(m, now);
  const f = frameAt(k, l.orb, reduced);
  let bulge = 0;
  let angle = 0;
  const p = pullAt(m, now);
  if (!reduced && p && f.presence > 0) {
    const dx = p[1] - l.cx;
    const dy = p[2] - l.cy;
    const reach = smoothstep(0.3 * l.ring, 0.9 * l.ring, Math.hypot(dx, dy));
    bulge = 0.16 * p[0] * f.presence * reach;
    angle = Math.atan2(dy, dx);
    // A finger on the screen is a louder voice.
    f.wobble += 0.04 * p[0] * f.presence;
    f.ringScale += 0.03 * p[0] * f.presence;
    f.level = Math.min(1, f.level + 0.35 * p[0]);
    if (f.recording > 0) f.orbRadius = DOT_RADIUS * (1 + 0.35 * f.level);
  }
  return { f, bulge, bulgeAngle: angle, lastTake: k.lastTake };
}

/** 0 at rest, 1 while a take records (follows the ring's presence). */
function liveness(f: Frame) {
  'worklet';
  return f.recording > 0 ? f.presence : 0;
}

function time(seconds: number) {
  'worklet';
  const s = Math.floor(seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function tactile(started: boolean) {
  Haptics.impactAsync(started ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

function rgba(c: number[], a: number) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], Math.min(1, Math.max(0, a)));
}

/** A closed outline of 180 points: radius × (1 + wobble × shape), plus a bump towards a finger. */
function ringPath(cx: number, cy: number, radius: number, wobble: number, t: number, bulge: number, bulgeAngle: number) {
  'worklet';
  const p = Skia.Path.Make();
  const n = 180;
  for (let i = 0; i <= n; i++) {
    const th = (2 * Math.PI * (i % n)) / n;
    let r = radius * (1 + wobble * shape(th, t));
    if (bulge > 0) {
      let d = th - bulgeAngle;
      d = Math.atan2(Math.sin(d), Math.cos(d));
      r += bulge * Math.exp(-(d / 0.55) * (d / 0.55));
    }
    const x = cx + r * Math.cos(th);
    const y = cy + r * Math.sin(th);
    if (i === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  p.close();
  return p;
}

export function BubbleRingRecorder({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [fontsLoaded] = useFonts({ [FONT_NAME]: FONT_FILE });
  const letterFont = useMemo(() => matchFont({ fontFamily: LETTER_FAMILY, fontSize: 11, fontWeight: '600' }), []);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [secs, setSecs] = useState(0);
  const [lastTake, setLastTake] = useState(time(TAKE));
  const layout = useMemo(
    () => (size.width > 0 ? makeLayout(size.width, size.height, insets.top, insets.bottom) : null),
    [size.width, size.height, insets.top, insets.bottom],
  );

  const m: Motion = {
    now: useSharedValue(-1),
    appear: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    firstAutoStop: useSharedValue(1e12),
    recStart: useSharedValue(-1),
    recStop: useSharedValue(-1),
    lastTake: useSharedValue(TAKE),
    press: useSharedValue<number[]>([]),
    released: useSharedValue<number[]>([]),
    resumeAt: useSharedValue(-1),
    autoStopAt: useSharedValue(-1),
  };

  const recording = () => {
    'worklet';
    return m.recStart.get() >= 0 && m.recStop.get() < 0;
  };

  /** Starts or stops a take; true when it started one. */
  const toggle = (now: number) => {
    'worklet';
    if (recording()) {
      m.lastTake.set(now - m.recStart.get());
      m.recStop.set(now);
      return false;
    }
    m.recStart.set(now);
    m.recStop.set(-1);
    return true;
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.appear.get() < 0) {
      m.appear.set(now);
      if (autoPlay && !reduced) {
        m.autoStart.set(now);
        m.firstAutoStop.set(now + STOP_AT);
      }
    }
    m.now.set(now);
    // A take left running stops itself 8 s after the last touch.
    const stopAt = m.autoStopAt.get();
    if (stopAt >= 0 && now >= stopAt) {
      m.autoStopAt.set(-1);
      if (recording()) scheduleOnRN(tactile, toggle(now));
      m.resumeAt.set(now + 2);
    }
    // 2 s after the last touch, the loop starts again inside its rest, in step with the breathing glow.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      if (recording()) {
        m.autoStopAt.set(now + 6);
      } else {
        const appear = m.appear.get();
        const k = Math.floor((now - appear - 7.4) / BREATH);
        const start = appear + k * BREATH;
        m.autoStart.set(start);
        m.firstAutoStop.set(start + LOOP + STOP_AT);
        m.recStart.set(-1);
        m.recStop.set(-1);
      }
    }
  });

  const shown = useDerivedValue(() => (layout && m.now.get() >= 0 ? shownAt(m, m.now.get(), layout, reduced) : null));

  // The timer and the last take change once a second; they are text on the JS side.
  useAnimatedReaction(
    () => {
      const s = shown.get();
      return s ? Math.floor(s.f.seconds) : 0;
    },
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setSecs, v);
    },
  );
  useAnimatedReaction(
    () => {
      const s = shown.get();
      return s ? time(s.lastTake) : time(TAKE);
    },
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setLastTake, v);
    },
  );

  const gesture = useMemo(() => {
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const now = m.now.get();
        m.resumeAt.set(-1);
        m.autoStopAt.set(-1);
        // Freeze whatever autoPlay shows into the hand-made state.
        if (m.autoStart.get() >= 0) {
          const k = clockAt(m, now);
          m.recStart.set(k.sinceStart >= 0 ? now - k.sinceStart : -1);
          m.recStop.set(k.sinceStop >= 0 ? now - k.sinceStop : -1);
          m.lastTake.set(k.lastTake);
          m.autoStart.set(-1);
        }
        m.press.set([e.x, e.y, now]);
        m.released.set([]);
      })
      .onUpdate((e) => {
        const p = m.press.get();
        if (p.length === 3) m.press.set([e.x, e.y, p[2]]);
      })
      .onFinalize((e) => {
        if (!layout) return;
        const now = m.now.get();
        const p = m.press.get();
        const pressedAt = p.length === 3 ? p[2] : now;
        const pull = pullAt(m, now);
        m.released.set([e.x, e.y, now, pull ? pull[0] : 0]);
        m.press.set([]);
        const sx = e.x - e.translationX;
        const sy = e.y - e.translationY;
        const moved = Math.hypot(e.translationX, e.translationY);
        const d = Math.hypot(sx - layout.cx, sy - layout.cy);
        if (moved < 10 && now - pressedAt < 0.4 && d < layout.ring * 1.15) scheduleOnRN(tactile, toggle(now));
        if (autoPlay && !reduced) m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layout, reduced, autoPlay]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const w = size.width;
    const h = size.height;
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const s = shown.get();
    const l = layout;
    if (!s || !l) return recorder.finishRecordingAsPicture();
    const f = s.f;
    const cx = l.cx;
    const cy = l.cy;
    const full = Skia.XYWHRect(0, 0, w, h);
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(Skia.Color(BACKGROUND));
    canvas.drawRect(full, paint);
    // A lighter pool behind the orb.
    const pool = Skia.Paint();
    pool.setShader(
      Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, w * 0.85, [Float32Array.of(0.16, 0.16, 0.17, 1), Float32Array.of(0.16, 0.16, 0.17, 0)], null, TileMode.Clamp),
    );
    canvas.drawRect(full, pool);

    // The orb's glow, and the dot's.
    const r = f.orbRadius;
    const glow = Skia.Paint();
    glow.setAntiAlias(true);
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 26, true));
    glow.setColor(rgba(RED, (0.42 + 0.22 * f.glow) * f.orbness));
    canvas.drawCircle(cx, cy, r * 1.12, glow);
    if (f.orbness < 1) {
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
      glow.setColor(rgba(RED, 0.55 * (1 - f.orbness)));
      canvas.drawCircle(cx, cy, r * (2.0 + 0.8 * f.level), glow);
    }
    // The orb itself.
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: cx - 0.3 * r, y: cy - 0.35 * r },
        Math.max(0.01, 1.45 * r),
        [Float32Array.of(1.0, 0.361, 0.486, 1), Float32Array.of(1.0, 0.122, 0.294, 1), Float32Array.of(0.831, 0.035, 0.227, 1)],
        [0, 0.55, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawCircle(cx, cy, r, body);
    // "RECORD", set vertically, shrinking and fading with the orb.
    const scale = r / l.orb;
    const textAlpha = smoothstep(0.55, 0.95, f.orbness);
    if (textAlpha > 0.01) {
      const step = 0.19 * l.orb * scale;
      const tp = Skia.Paint();
      tp.setAntiAlias(true);
      tp.setColor(Float32Array.of(1, 1, 1, 0.88 * textAlpha));
      const metrics = letterFont.getMetrics();
      const mid = (metrics.ascent + metrics.descent) / 2;
      const letters = 'RECORD';
      for (let i = 0; i < letters.length; i++) {
        const ch = letters[i];
        const lw = letterFont.measureText(ch).width;
        canvas.save();
        canvas.translate(cx, cy + (i - 2.5) * step);
        canvas.scale(scale, scale);
        canvas.drawText(ch, -lw / 2, -mid, tp, letterFont);
        canvas.restore();
      }
    }

    // The ring: a faint circle at rest, a wobbling white outline while recording.
    const stroke = Skia.Paint();
    stroke.setAntiAlias(true);
    stroke.setStyle(PaintStyle.Stroke);
    stroke.setStrokeCap(StrokeCap.Round);
    stroke.setStrokeJoin(StrokeJoin.Round);
    const main = ringPath(cx, cy, l.ring * f.ringScale, f.wobble, f.shapeTime, s.bulge * l.ring, s.bulgeAngle);
    if (f.presence > 0.01) {
      const ghost = ringPath(cx, cy, l.ring * (f.ringScale - 0.01), f.ghostWobble, f.shapeTime - GHOST_LAG, s.bulge * l.ring * 0.8, s.bulgeAngle);
      stroke.setStrokeWidth(1.25);
      stroke.setColor(Float32Array.of(1, 1, 1, 0.32 * f.presence));
      canvas.drawPath(ghost, stroke);
      const halo = stroke.copy();
      halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
      halo.setStrokeWidth(5);
      halo.setColor(Float32Array.of(1, 1, 1, 0.35 * f.presence));
      canvas.drawPath(main, halo);
    }
    stroke.setStrokeWidth(1 + 1.25 * f.presence);
    stroke.setColor(Float32Array.of(1, 1, 1, Math.min(1, 0.1 + 0.9 * f.presence)));
    canvas.drawPath(main, stroke);

    // A tap's ripple: white out of the orb when a take starts, red out of the dot when it stops.
    if (f.ripple >= 0) {
      const e = easeOut(f.ripple);
      const rr = f.rippleIsStop ? DOT_RADIUS + (l.orb * 1.3 - DOT_RADIUS) * e : l.orb * (1 + 0.45 * e);
      stroke.setStrokeWidth(1.5);
      stroke.setColor(rgba(f.rippleIsStop ? RED : [1, 1, 1], 0.45 * (1 - f.ripple)));
      canvas.drawCircle(cx, cy, rr, stroke);
    }

    // The three glyphs under the orb.
    const gx = w / 2;
    const gy = l.iconsY;
    const dim = Float32Array.of(1, 1, 1, 0.45);
    stroke.setStrokeWidth(1.6);
    stroke.setColor(dim);
    const note = Skia.Path.Make();
    const nx = gx - 44;
    note.moveTo(nx + 2, gy + 6);
    note.lineTo(nx + 2, gy - 9);
    note.lineTo(nx + 7, gy - 6);
    canvas.drawPath(note, stroke);
    paint.setColor(dim);
    canvas.drawOval(Skia.XYWHRect(nx - 4, gy + 3, 6.5, 5.5), paint);
    stroke.setStrokeWidth(1.5);
    stroke.setColor(Float32Array.of(1, 1, 1, 1));
    [4, 10, 16, 8, 18, 10, 5].forEach((bh, i) => {
      const bx = gx + (i - 3) * 3.4;
      canvas.drawLine(bx, gy - bh / 2, bx, gy + bh / 2, stroke);
    });
    stroke.setStrokeWidth(1.6);
    stroke.setColor(dim);
    const lx = gx + 44;
    [14, 10, 14].forEach((lw, i) => {
      const ly = gy - 6 + i * 6;
      canvas.drawLine(lx - 7 + (14 - lw), ly, lx + 7, ly, stroke);
    });

    // The list: dividers, icons, waveforms and menus (the titles are text views).
    const live = liveness(f);
    paint.setColor(Float32Array.of(1, 1, 1, 0.07));
    for (let i = 0; i <= 3; i++) canvas.drawRect(Skia.XYWHRect(24, l.rowsTop + i * ROW_HEIGHT, w - 48, 1), paint);
    for (let i = 0; i < 3; i++) {
      const mid = l.rowsTop + i * ROW_HEIGHT + ROW_HEIGHT / 2;
      const ix = 42;
      const liveRow = i === 0 ? live : 0;
      // The icon: a play button, or a pulsing red dot while the first row records.
      stroke.setStrokeWidth(1);
      stroke.setColor(Float32Array.of(1, 1, 1, 0.18 * (1 - liveRow)));
      canvas.drawCircle(ix, mid, 17.5, stroke);
      if (liveRow > 0) {
        stroke.setColor(rgba(RED, 0.5 * liveRow));
        canvas.drawCircle(ix, mid, 17.5, stroke);
        paint.setColor(rgba(RED, liveRow));
        canvas.drawCircle(ix, mid, 5 * (1 + 0.3 * f.level), paint);
      }
      paint.setColor(Float32Array.of(1, 1, 1, 1 - liveRow));
      const tri = Skia.Path.Make();
      const tx = ix - 5 + 1.5;
      tri.moveTo(tx, mid - 6);
      tri.lineTo(tx + 10, mid);
      tri.lineTo(tx, mid + 6);
      tri.close();
      canvas.drawPath(tri, paint);
      // The waveform: 30 bars, 2 pt wide, 4 pt apart, 3…22 pt tall; dots where nothing is recorded yet.
      const wx = w - 168;
      const values = i === 0 ? bars(BAR_COUNT, f.recording > 0 ? f.seconds : s.lastTake) : null;
      const color = i === 0 ? Float32Array.of(1, 1, 1, 0.55 + 0.35 * live) : Float32Array.of(1, 1, 1, 0.3);
      for (let j = 0; j < BAR_COUNT; j++) {
        const v = values ? values[j] : staticBar(j, ROWS[i - 1].seed);
        const bx = wx + j * 4;
        if (v >= 0) {
          const bh = 3 + 19 * v;
          paint.setColor(color);
          canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, mid - bh / 2, 2, bh), 1, 1), paint);
        } else {
          paint.setColor(Float32Array.of(1, 1, 1, 0.15));
          canvas.drawCircle(bx + 1, mid, 1, paint);
        }
      }
      // The row's menu.
      paint.setColor(Float32Array.of(1, 1, 1, 0.45));
      for (let k = -1; k <= 1; k++) canvas.drawCircle(w - 30, mid + k * 6, 1.5, paint);
    }
    // The screen's menu, top right: 20, 20 and 13 pt lines, 1.6 pt thick, right-aligned.
    paint.setColor(Float32Array.of(1, 1, 1, 0.85));
    [20, 20, 13].forEach((lw, i) => {
      const y = l.top + 37.4 + i * 6.6;
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(w - 26 - lw, y - 0.8, lw, 1.6), 0.8, 0.8), paint);
    });
    return recorder.finishRecordingAsPicture();
  });

  const restStyle = useAnimatedStyle(() => {
    const s = shown.get();
    return { opacity: 1 - (s ? liveness(s.f) : 0) };
  });
  const liveStyle = useAnimatedStyle(() => {
    const s = shown.get();
    return { opacity: s ? liveness(s.f) : 0 };
  });
  const dotStyle = useAnimatedStyle(() => {
    const s = shown.get();
    return { opacity: 0.55 + 0.45 * (s ? s.f.level : 0) };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const textWidth = size.width - 256;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {fontsLoaded && layout ? (
        <>
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityRole="button" accessibilityLabel="Record">
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          {/* Timer and caption. */}
          <View style={[styles.timerBlock, { top: insets.top + 4 }]} pointerEvents="none">
            <View style={styles.timerRow}>
              <Text style={[styles.timer, styles.minutes]}>{Math.floor(secs / 60)}</Text>
              <Text style={styles.timer}>{`:${String(secs % 60).padStart(2, '0')}`}</Text>
            </View>
            <View style={styles.caption}>
              <Animated.Text style={[styles.captionText, styles.captionRest, restStyle]} numberOfLines={1}>
                Tap the orb to record
              </Animated.Text>
              <Animated.View style={[styles.captionLive, liveStyle]}>
                <Animated.View style={[styles.captionDot, dotStyle]} />
                <Text style={[styles.captionText, { color: RED_TEXT }]} numberOfLines={1}>
                  Recording · 48 kHz
                </Text>
              </Animated.View>
            </View>
          </View>

          {/* The list's titles; the icons, waveforms and dividers are drawn on the canvas. */}
          <Text style={[styles.section, { top: layout.rowsTop - 26 }]} pointerEvents="none">
            RECORDINGS
          </Text>
          <View style={[styles.titles, { top: layout.rowsTop, width: textWidth }]} pointerEvents="none">
            <Animated.View style={[StyleSheet.absoluteFill, styles.titleBox, restStyle]}>
              <Titles title="Untitled take" detail={`${lastTake} · Today, 9:41`} />
            </Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, styles.titleBox, liveStyle]}>
              <Titles title="Recording" detail={`${time(secs)} · Now`} tint={RED_TEXT} />
            </Animated.View>
          </View>
          {ROWS.map((row, i) => (
            <View
              key={row.title}
              style={[styles.titles, styles.titleBox, { top: layout.rowsTop + (i + 1) * ROW_HEIGHT, width: textWidth }]}
              pointerEvents="none"
            >
              <Titles title={row.title} detail={row.detail} />
            </View>
          ))}
        </>
      ) : null}
    </View>
  );
}

function Titles({ title, detail, tint = '#FFFFFF' }: { title: string; detail: string; tint?: string }) {
  return (
    <>
      <Text style={[styles.title, { color: tint }]} numberOfLines={1}>
        {title}
      </Text>
      <Text style={styles.detail} numberOfLines={1}>
        {detail}
      </Text>
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  timerBlock: { position: 'absolute', left: 24 },
  timerRow: { height: 112, flexDirection: 'row', alignItems: 'center' },
  // React Native sets this font's line 6.5 pt lower in the row than SwiftUI does.
  timer: { fontFamily: FONT_NAME, fontSize: 104, color: '#FFFFFF', includeFontPadding: false, transform: [{ translateY: -6.5 }] },
  minutes: { color: 'rgba(255,255,255,0.42)' },
  caption: { height: 24, justifyContent: 'center' },
  captionText: { fontSize: 15, fontWeight: '500' },
  captionRest: { position: 'absolute', left: 0, color: 'rgba(255,255,255,0.5)' },
  captionLive: { flexDirection: 'row', alignItems: 'center' },
  captionDot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: '#FF1F4B', marginRight: 8 },
  section: {
    position: 'absolute',
    left: 24,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.4,
    color: 'rgba(255,255,255,0.4)',
  },
  titles: { position: 'absolute', left: 74, height: ROW_HEIGHT },
  titleBox: { justifyContent: 'center' },
  title: { fontSize: 16, fontWeight: '500' },
  detail: { marginTop: 3, fontSize: 12.5, color: 'rgba(255,255,255,0.45)', fontVariant: ['tabular-nums'] },
});
