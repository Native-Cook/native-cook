import {
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  Line,
  Oval,
  Path,
  RoundedRect,
  Skia,
  type SkMatrix,
  type SkPath,
  type SkPathBuilder,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StatusBar, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';

/** The self-playing story, in seconds: plane → taxi → hotel → plane, one row ticked per leg. */
export const LOOP = 8.0;
/** Finger travel for one whole loop when scrubbing the hero, in pt. */
const SCRUB_SPAN = 320;
/** Seconds after the last touch at which time eases back to the nearer loop end. */
const IDLE = 2;
/** SwiftUI spring(response: 0.4, dampingFraction: 0.7) as mass / stiffness / damping. */
const LEAN_SPRING = { mass: 1, stiffness: (2 * Math.PI / 0.4) ** 2, damping: (4 * Math.PI * 0.7) / 0.4 };

// Playback modes, as TripLineLoaderPlayback in the SwiftUI reference.
const PLAYING = 0;
const HELD = 1;
const RETURNING = 2;
/** No autoplay tick bucket yet (Swift's nil): the first frame after a restart never buzzes. */
const NO_TICK = -9999;

// Palette, the same sRGB hex values as the SwiftUI reference.
const BG = '#FFFBF8';
const CARD = '#FFFFFF';
const CARD_BORDER = '#F1E6E1';
const CORAL = '#E64C3B';
const CORAL_SOFT = '#FCE9E6';
const INK = '#2A1F1D';
const MUTED = '#8D7F7A';
const TRACK = '#E9DDD8';

// Layout, pt from the top of the safe area (the same numbers as Swift).
const HERO_Y = 150;
const HERO_H = 250;
const ROUTE_Y = 416;
const ROW_Y = 476;
const ROW_STEP = 78;
const ROW_H = 68;
const MARGIN = 24;
/** SF Pro's ascender per pt of size: the caption's baseline sits 24 pt above the safe area's bottom. */
const ASCENDER = 0.952;

const ROWS = [
  { title: 'Flight', detail: 'LIS → KIX · 1 stop · 16h 05m', price: '€612' },
  { title: 'Transfer', detail: 'Airport taxi to Gion · 48 min', price: '€38' },
  { title: 'Stay', detail: 'Kamo River Inn · 6 nights', price: '€486' },
];

// Icons and stroke parts (TripLineLoaderArtwork.Icon / .Part).
const PLANE = 0;
const TAXI = 1;
const HOTEL = 2;
const PLAIN = 0;
const PLANE_CLOUD = 1;
const TAXI_CLOUD = 2;
const HOTEL_CLOUD = 3;
const WHEEL = 4;
const SPOKE = 5;
const TREE = 6;
const WINDOW = 7;

// MARK: Motion (TripLineLoaderMotion)

function clamp(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a));
}
function out(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function into(x: number) {
  'worklet';
  return x * x * x;
}
function inOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function back(x: number) {
  'worklet';
  return 1 + 2.70158 * Math.pow(x - 1, 3) + 1.70158 * Math.pow(x - 1, 2);
}
function softBack(x: number) {
  'worklet';
  const e = back(x);
  return e <= 1 ? e : 1 + (e - 1) * 0.4;
}
function mix(a: number, b: number, x: number) {
  'worklet';
  return a + (b - a) * x;
}
function wrap(t: number) {
  'worklet';
  const r = t % 8;
  return r < 0 ? r + 8 : r;
}
/** The three tick moments: 1.62, 3.92, 6.22 s. */
function tickAt(i: number) {
  'worklet';
  return i === 0 ? 1.62 : i === 1 ? 3.92 : 6.22;
}
/** How many ticks lie behind an unwrapped time: it changes only at a tick, never at the loop seam. */
function tickBucket(t: number) {
  'worklet';
  return Math.floor((t - 1.62) / 8) + Math.floor((t - 3.92) / 8) + Math.floor((t - 6.22) / 8);
}

type RowState = { active: number; done: number; waiting: number; searching: number; pop: number; check: number };

function row(i: number, t: number, reduced: boolean): RowState {
  'worklet';
  if (reduced) return { active: 0, done: 1, waiting: 0, searching: 0, pop: 1, check: 1 };
  const tick = tickAt(i);
  const reset = 6.9 + (2 - i) * 0.06;
  const fade = inOut(seg(t, reset, reset + 0.2));
  const done = out(seg(t, tick, tick + 0.15)) * (1 - fade);
  const arrival = i === 0 ? 1 : out(seg(t, tickAt(i - 1), tickAt(i - 1) + 0.2));
  const departure = out(seg(t, tick, tick + 0.15));
  const active = arrival * (1 - departure) + (i === 0 ? inOut(seg(t, 7.1, 7.3)) : 0);
  const pop = t < tick ? 0.6 : 0.6 + 0.4 * back(seg(t, tick, tick + 0.3));
  return { active, done, waiting: Math.max(0, 1 - active - done), searching: active, pop, check: out(seg(t, tick + 0.08, tick + 0.3)) };
}

function route(t: number, reduced: boolean) {
  'worklet';
  if (reduced) return { from: 0, to: 1 };
  if (t >= 7.35) return { from: 0, to: 0 };
  const to = 0.6 * inOut(seg(t, 1.62, 2.12)) + 0.2 * inOut(seg(t, 3.92, 4.42)) + 0.2 * inOut(seg(t, 6.22, 6.72));
  return { from: inOut(seg(t, 6.85, 7.35)), to };
}

// MARK: Drawing (TripLineLoaderDrawing)

/** Each silhouette leaves a narrower contact shadow as it gathers into the pill. */
function shadow(t: number, reduced: boolean) {
  'worklet';
  let factor = 1;
  let opacity = 1;
  if (!reduced) {
    // (start, end, draw, live, next) per gather.
    const phases = [1.4, 1.8, 1.9, 2.26, 0.9, 3.7, 4.1, 4.2, 4.56, 0.7, 6.0, 6.4, 6.5, 6.86, 1.0];
    for (let p = 0; p < 15; p += 5) {
      if (t < phases[p]) break;
      const gather = into(seg(t, phases[p], phases[p + 1]));
      const appear = out(seg(t, phases[p + 2], phases[p + 3]));
      factor = mix(mix(factor, 0.45, gather), phases[p + 4], appear);
      opacity = mix(1 - 0.5 * gather, 1, appear);
    }
    const planePresence = t < 1.8 ? 1 - into(seg(t, 1.4, 1.8)) : out(seg(t, 6.5, 6.86));
    factor *= 1 - 0.04 * Math.sin(Math.PI * t) * planePresence;
  }
  return { factor, opacity };
}

type IconFrame = {
  /** The icon is drawn at all in this phase. */
  on: boolean;
  t: number;
  elapsed: number;
  gather: number;
  build: number;
  anticipation: number;
  bob: number;
  pitch: number;
  appear: number;
  /** Group opacity: 0 while the icon is off. */
  opacity: number;
  /** Trim start shared by every stroke of the icon while it gathers. */
  from: number;
};

/** Cruise / build, gather and draw-on share these values for autoplay and scrubbing. */
function iconFrame(kind: number, time: number, reduced: boolean): IconFrame {
  'worklet';
  let on: boolean;
  let t = time;
  let elapsed: number;
  let gather: number;
  if (reduced) {
    on = kind === PLANE;
    t = 0;
    elapsed = 10;
    gather = 0;
  } else if (kind === PLANE) {
    on = t < 1.8 || t >= 6.5;
    const clock = t < 1.8 ? t + 8 : t;
    elapsed = clock - 6.5;
    gather = into(seg(clock, 9.4, 9.8));
  } else if (kind === TAXI) {
    on = t >= 1.9 && t < 4.1;
    elapsed = t - 1.9;
    gather = into(seg(t, 3.7, 4.1));
  } else {
    on = t >= 4.2 && t < 6.4;
    elapsed = t - 4.2;
    gather = into(seg(t, 6.0, 6.4));
  }
  const plane = kind === PLANE;
  const build = plane && t < 1.8 && !reduced ? inOut(seg(t, 0.8, 1.3)) : 0;
  const anticipation = plane && t < 1.8 && !reduced ? out(seg(t, 1.3, 1.4)) : 0;
  const bob = reduced ? 0 : plane ? 2 * Math.sin(Math.PI * t) : kind === TAXI ? 0.8 * Math.abs(Math.sin(4 * Math.PI * t)) : 0;
  const pitch = plane && !reduced ? 1.2 * Math.sin(Math.PI * t + Math.PI / 2) : 0;
  return {
    on,
    t,
    elapsed,
    gather,
    build,
    anticipation,
    bob,
    pitch,
    appear: seg(elapsed, 0, 0.36),
    opacity: on ? 1 - seg(gather, 0.7, 1) : 0,
    from: 0.5 * 0.85 * gather,
  };
}

/** The icon's context transform, call for call as in TripLineLoaderDrawing.icon. */
function iconMatrix(kind: number, f: IconFrame, width: number) {
  'worklet';
  const plane = kind === PLANE;
  const hotel = kind === HOTEL;
  const m = Skia.Matrix();
  m.translate(width / 2, 112 + (plane ? f.bob : 0));
  m.rotate((f.pitch * Math.PI) / 180);
  m.scale(1.4, 1.4);
  m.translate(-88 + 5 * f.build * (1 - f.gather), -60);
  m.translate(88, 60);
  m.scale(mix(1 - 0.03 * f.anticipation, 0.55, f.gather), mix(1 + 0.05 * f.anticipation, 0.06, f.gather));
  m.translate(-88, -60);
  const anchorY = hotel ? 120 : 60;
  m.translate(88, anchorY);
  m.scale(hotel ? 1 : mix(1.15, 1, out(f.appear)), mix(0.3, 1, hotel ? out(f.appear) : softBack(f.appear)));
  m.translate(-88, -anchorY);
  return m;
}

/** A stroke's trim end: its draw-on slot, capped by the gather's trim start. */
function strokeEnd(f: IconFrame, delay: number, duration: number) {
  'worklet';
  return Math.min(out(seg(f.elapsed, delay, delay + duration)), 1 - f.from);
}

/** The per-part layer transform inside the icon (clouds drift, spokes spin, the tree sways). */
function partMatrix(part: number, x: number, t: number, reduced: boolean) {
  'worklet';
  const m = Skia.Matrix();
  if (part === PLANE_CLOUD) {
    const u = wrap(t + 1.5);
    m.translate(reduced ? 0 : (-8 * Math.min(u, 3.3)) / 3.3, 0);
  } else if (part === TAXI_CLOUD) {
    m.translate(-10 * seg(t, 2.55, 3.7), 0);
  } else if (part === HOTEL_CLOUD) {
    m.translate(-8 * seg(t, 4.9, 6.0), 0);
  } else if (part === SPOKE) {
    m.translate(x, 86);
    m.rotate(4 * Math.PI * t);
    m.translate(-x, -86);
  } else if (part === TREE) {
    m.translate(25, 116);
    m.rotate((3 * Math.sin(2 * Math.PI * t) * Math.PI) / 180);
    m.translate(-25, -116);
  }
  return m;
}

/**
 * One lane of speed or road dashes: a long/short pair repeats every 0.5 s and every dash is trimmed
 * like its own line (`from…to` of its length), so the lane draws on and gathers with its icon.
 */
function dashLane(y: number, start: number, long: number, gap: number, phase: number, lengthScale: number, from: number, to: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  if (to > from) {
    const period = (long + 5) * lengthScale + 2 * gap;
    const offset = phase * period;
    for (let i = -1; i <= 6; i += 1) {
      const x = start + i * period - offset;
      const first = long * lengthScale;
      b.moveTo(x + first * from, y);
      b.lineTo(x + first * to, y);
      const a = x + first + gap;
      const second = 5 * lengthScale;
      b.moveTo(a + second * from, y);
      b.lineTo(a + second * to, y);
    }
  }
  return b.detach();
}

/** The pill bridging the disappearing strokes and the next drawing: grows, stretches, trims from both ends. */
function pill(t: number, start: number, length: number, width: number) {
  'worklet';
  if (t < start || t >= start + 0.72) return { x1: 0, x2: 0, on: 0 };
  const grown = length * out(seg(t, start, start + 0.24));
  const stretched = grown + 40 * out(seg(t, start + 0.24, start + 0.42));
  const trim = 0.5 * into(seg(t, start + 0.42, start + 0.72));
  if (!(stretched > 0 && trim < 0.5)) return { x1: 0, x2: 0, on: 0 };
  const left = width / 2 - stretched * 0.6;
  const right = width / 2 + stretched * 0.6;
  const span = right - left;
  return { x1: left + span * trim, x2: right - span * trim, on: 1 };
}

// MARK: Artwork (TripLineLoaderArtwork), design units in the 176×120 icon box

type Point = [number, number];
type Stroke = { path: SkPath; delay: number; width: number; part: number; x: number; index: number };

function stroke(path: SkPath, slot: number, options: { extra?: number; width?: number; part?: number; x?: number; index?: number } = {}): Stroke {
  return {
    path,
    delay: slot * 0.035 + (options.extra ?? 0),
    width: options.width ?? 2.2,
    part: options.part ?? PLAIN,
    x: options.x ?? 0,
    index: options.index ?? 0,
  };
}

function polyline(points: Point[]) {
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i < points.length; i += 1) {
    if (i === 0) b.moveTo(points[i][0], points[i][1]);
    else b.lineTo(points[i][0], points[i][1]);
  }
  return b.detach();
}
function line(x1: number, y1: number, x2: number, y2: number) {
  return polyline([[x1, y1], [x2, y2]]);
}
function quad(x: number, y: number, cx: number, cy: number, ex: number, ey: number) {
  return Skia.PathBuilder.Make().moveTo(x, y).quadTo(cx, cy, ex, ey).detach();
}
function cloud(x: number, y: number, w: number, h: number) {
  return Skia.PathBuilder.Make()
    .moveTo(x - w * 0.32, y + h / 2)
    .lineTo(x + w * 0.32, y + h / 2)
    .cubicTo(x + w * 0.58, y + h / 2, x + w * 0.56, y - h * 0.1, x + w * 0.29, y - h * 0.12)
    .cubicTo(x + w * 0.16, y - h * 0.7, x - w * 0.17, y - h * 0.7, x - w * 0.24, y - h * 0.14)
    .cubicTo(x - w * 0.54, y - h * 0.28, x - w * 0.61, y + h / 2, x - w * 0.32, y + h / 2)
    .close()
    .detach();
}
function inset(p: Point, q: Point, radius: number): Point {
  const dx = q[0] - p[0];
  const dy = q[1] - p[1];
  const distance = Math.hypot(dx, dy);
  const fraction = Math.min(radius, distance / 2) / Math.max(distance, 0.001);
  return [p[0] + dx * fraction, p[1] + dy * fraction];
}
/** Round each specified corner with short quadratic tangents, retaining the design vertices. */
function appendRounded(points: Point[], radius: number, b: SkPathBuilder, closeAt?: Point) {
  if (points.length <= 1) return;
  const limit = closeAt === undefined ? points.length - 1 : points.length - 2;
  for (let i = 1; i < limit; i += 1) {
    const p = points[i];
    const before = inset(p, points[i - 1], radius);
    const after = inset(p, points[i + 1], radius);
    b.lineTo(before[0], before[1]);
    b.quadTo(p[0], p[1], after[0], after[1]);
  }
  if (closeAt !== undefined) b.lineTo(closeAt[0], closeAt[1]);
  else b.lineTo(points[points.length - 1][0], points[points.length - 1][1]);
}
function rounded(points: Point[], radius: number, closed = false) {
  const b = Skia.PathBuilder.Make();
  const first = points[0];
  if (closed && points.length > 2) {
    const before = inset(first, points[points.length - 1], radius);
    const after = inset(first, points[1], radius);
    b.moveTo(before[0], before[1]);
    b.quadTo(first[0], first[1], after[0], after[1]);
    appendRounded([...points, first, points[1]], radius, b, before);
    b.close();
  } else {
    b.moveTo(first[0], first[1]);
    appendRounded(points, radius, b);
  }
  return b;
}

type Artwork = { plane: Stroke[]; taxi: Stroke[]; hotel: Stroke[]; check: SkPath; arc: SkPath };
let artwork: Artwork | null = null;

/** Built once, on first render, on the JS thread. */
function getArtwork(): Artwork {
  if (artwork) return artwork;
  const outline = rounded([[17.5, 33.2], [26.6, 32.6], [44.9, 44.8], [151.3, 44.8]], 3);
  outline.quadTo(166, 45, 165.9, 56.9);
  appendRounded([[165.9, 56.9], [157.4, 63.6], [114.8, 63.6], [73.8, 94.9], [55.5, 95.8], [78.9, 63.6], [35.2, 63.6], [17.5, 33.2]], 3, outline);
  outline.close();
  const plane = [
    stroke(outline.detach(), 0),
    stroke(rounded([[76.8, 44.8], [54, 20.4], [73.8, 18.9], [107.2, 44.8]], 3).detach(), 1),
    stroke(line(29.7, 52.4, 44, 52.4), 2),
    stroke(line(50.5, 52.4, 50.6, 52.4), 2),
    stroke(line(128.5, 52.4, 146, 52.4), 3),
    stroke(line(152.3, 52.4, 156.9, 52.4), 3),
    stroke(cloud(152, 16, 22, 12), 4, { part: PLANE_CLOUD }),
    stroke(cloud(128, 104, 30, 15), 5, { part: PLANE_CLOUD }),
  ];

  const taxi = [
    stroke(rounded([[17.2, 87.4], [21.5, 65.7], [38.7, 55.8], [137.6, 55.8], [155.6, 70], [161.2, 87.4]], 3, true).detach(), 0),
    stroke(rounded([[36.5, 55.8], [51.6, 33.4], [96.8, 31.3], [114, 55.8]], 3).detach(), 1),
    stroke(line(68.8, 32, 68.8, 79.9), 2),
    stroke(line(69.5, 83, 69.6, 83), 2),
    stroke(line(80.8, 67.8, 88.1, 67.8), 3),
    stroke(quad(19, 72, 24, 58, 38.7, 56), 4),
    stroke(quad(137.6, 56, 156, 58, 160, 80), 5),
  ];
  const wheels = [43.8, 128];
  for (let i = 0; i < wheels.length; i += 1) {
    const x = wheels[i];
    // A full circle from the top, clockwise on screen: Swift's arc from −90° to 270°.
    const wheel = Skia.PathBuilder.Make().addOval(Skia.XYWHRect(x - 11, 75, 22, 22), false, 0).detach();
    taxi.push(stroke(wheel, 6 + i, { part: WHEEL, x }));
    taxi.push(stroke(line(x, 79, x, 83), 6 + i, { part: SPOKE, x }));
  }
  taxi.push(stroke(cloud(128, 14, 32, 16), 9, { part: TAXI_CLOUD }));

  const hotel = [
    stroke(line(14, 116, 162, 116), 0),
    stroke(polyline([[43, 116], [43, 14], [132.5, 14], [132.5, 116]]), 1),
    stroke(line(62, 9, 113, 9), 2, { width: 3.1 }),
  ];
  const dots = [60, 87.75, 115.5];
  for (let i = 0; i < dots.length; i += 1) hotel.push(stroke(line(dots[i], 24, dots[i] + 0.1, 24), 3));
  hotel.push(stroke(polyline([[77, 116], [77, 92], [98, 92], [98, 116]]), 4));
  const rowsY = [72, 56, 40];
  const columnsX = [62, 87.75, 113.5];
  for (let r = 0; r < 3; r += 1) {
    for (let c = 0; c < 3; c += 1) {
      const i = r * 3 + c;
      const rect = Skia.XYWHRect(columnsX[c] - 4.5, rowsY[r] - 3.5, 9, 7);
      const window = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(rect, 1.5, 1.5)).detach();
      hotel.push(stroke(window, 5, { extra: i * 0.03, part: WINDOW, index: i }));
    }
  }
  hotel.push(
    stroke(Skia.PathBuilder.Make().addOval(Skia.XYWHRect(17, 82, 16, 22), false, 1).detach(), 6, { part: TREE }),
    stroke(line(25, 96, 25, 116), 6, { part: TREE }),
    stroke(line(25, 104, 29, 100), 6, { part: TREE }),
    stroke(cloud(148, 112, 22, 9), 7),
    stroke(cloud(154, 4, 28, 13), 8, { part: HOTEL_CLOUD }),
  );

  artwork = {
    plane,
    taxi,
    hotel,
    check: polyline([[7, 12.5], [10.5, 16], [17, 9]]),
    // The status spinner's 90° arc from 12 o'clock; it turns once a second.
    arc: Skia.PathBuilder.Make().addArc(Skia.XYWHRect(1, 1, 22, 22), -90, 90).detach(),
  };
  return artwork;
}

function haptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

// MARK: Screen

/** A trip drawn one leg at a time; drag the line artwork to scrub its eight-second story. */
export function TripLineLoader({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const width = size?.width ?? window.width - insets.left - insets.right;
  const height = size?.height ?? window.height - insets.top - insets.bottom;
  const rowWidth = width - 2 * MARGIN;
  const art = getArtwork();

  /** Loop time t ∈ [0, 8): every frame is derived from it. */
  const time = useSharedValue(0);
  const red = useSharedValue(reduced);
  const auto = useSharedValue(autoPlay);
  const mode = useSharedValue(PLAYING);
  const origin = useSharedValue(0);
  const originPending = useSharedValue(true);
  const held = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnTo = useSharedValue(0);
  const returnStart = useSharedValue(-1);
  const returnFor = useSharedValue(0);
  const lastTick = useSharedValue(NO_TICK);
  const dragging = useSharedValue(false);
  const dragStart = useSharedValue(0);
  const previousScrub = useSharedValue(0);
  const lean = useSharedValue(0);
  /** Bumped by every touch, restart and unmount: a timer whose token is stale does nothing. */
  const generation = useSharedValue(0);
  const lastHaptic = useSharedValue(0);

  const impact = () => {
    const now = Date.now();
    if (red.get() || now - lastHaptic.get() < 100) return;
    lastHaptic.set(now);
    haptic();
  };

  useFrameCallback((info) => {
    if (red.get()) {
      if (time.get() !== 0) time.set(0);
      return;
    }
    const now = info.timestamp / 1000;
    if (originPending.get()) {
      originPending.set(false);
      origin.set(now);
      lastTick.set(NO_TICK);
    }
    const m = mode.get();
    let t = 0;
    if (m === PLAYING) {
      if (auto.get()) {
        const elapsed = Math.max(0, now - origin.get());
        t = wrap(elapsed);
        // The bucket changes only at the three ticks, never at the loop seam.
        const bucket = tickBucket(elapsed);
        const before = lastTick.get();
        if (before !== NO_TICK && bucket > before) scheduleOnRN(impact);
        lastTick.set(bucket);
      } else {
        lastTick.set(NO_TICK);
      }
    } else if (m === HELD) {
      t = wrap(held.get());
      lastTick.set(NO_TICK);
    } else {
      if (returnStart.get() < 0) returnStart.set(now);
      const e = inOut(seg(now - returnStart.get(), 0, returnFor.get()));
      t = wrap(returnFrom.get() + (returnTo.get() - returnFrom.get()) * e);
      lastTick.set(NO_TICK);
    }
    if (t !== time.get()) time.set(t);
  });

  // 2 s after the last touch, time eases to the nearer loop end, then autoplay restarts at t = 0.
  const returnAfterIdle = (token: number) => {
    setTimeout(() => {
      if (generation.get() !== token || dragging.get() || red.get() || mode.get() !== HELD) return;
      const from = wrap(held.get());
      const to = from < 4 ? 0 : LOOP;
      const duration = Math.min(0.9, 0.35 + 0.08 * Math.abs(to - from));
      returnFrom.set(from);
      returnTo.set(to);
      returnFor.set(duration);
      returnStart.set(-1);
      mode.set(RETURNING);
      setTimeout(() => {
        if (generation.get() !== token || dragging.get() || red.get()) return;
        originPending.set(true);
        mode.set(PLAYING);
      }, duration * 1000);
    }, IDLE * 1000);
  };

  // Restart on mount, on a Reduce Motion or autoPlay change and on every app state change.
  useEffect(() => {
    const restart = () => {
      generation.set(generation.get() + 1);
      red.set(reduced);
      auto.set(autoPlay);
      dragging.set(false);
      lean.set(0);
      originPending.set(true);
      mode.set(PLAYING);
      if (reduced) time.set(0);
    };
    restart();
    const subscription = AppState.addEventListener('change', restart);
    return () => {
      subscription.remove();
      generation.set(generation.get() + 1);
    };
  }, [reduced, autoPlay, generation, red, auto, dragging, lean, originPending, mode, time]);

  const scrub = Gesture.Pan()
    .minDistance(0)
    .enabled(!reduced)
    .onBegin(() => {
      if (red.get() || dragging.get()) return;
      // Time freezes where it is; the finger takes it over from there.
      const frozen = time.get();
      dragging.set(true);
      dragStart.set(frozen);
      previousScrub.set(frozen);
      held.set(frozen);
      mode.set(HELD);
      // Any pending return is stale from now on.
      generation.set(generation.get() + 1);
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      const next = dragStart.get() + (event.translationX * LOOP) / SCRUB_SPAN;
      if (tickBucket(next) !== tickBucket(previousScrub.get())) scheduleOnRN(impact);
      previousScrub.set(next);
      held.set(next);
      lean.set(Math.min(1, Math.max(-1, event.velocityX / 900)) * 6);
    })
    .onFinalize(() => {
      // A lift and a cancelled touch both end here, so the idle timer cannot get stranded.
      if (!dragging.get()) return;
      dragging.set(false);
      lean.set(withSpring(0, LEAN_SPRING));
      const token = generation.get() + 1;
      generation.set(token);
      scheduleOnRN(returnAfterIdle, token);
    });

  const rowA = useDerivedValue(() => row(0, time.get(), red.get()));
  const rowB = useDerivedValue(() => row(1, time.get(), red.get()));
  const rowC = useDerivedValue(() => row(2, time.get(), red.get()));
  const rows = [rowA, rowB, rowC];

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    if (!size || size.width !== w || size.height !== h) setSize({ width: w, height: h });
  };

  return (
    <View style={styles.screen}>
      <StatusBar barStyle="dark-content" />
      <View
        style={[styles.safe, { top: insets.top, left: insets.left, right: insets.right, bottom: insets.bottom }]}
        onLayout={onLayout}
      >
        <Canvas style={[styles.canvas, { width, height }]} pointerEvents="none">
          <Group transform={[{ translateY: HERO_Y }]}>
            <TripLineLoaderShadow time={time} red={red} width={width} />
            <TripLineLoaderLean lean={lean} width={width}>
              <TripLineLoaderIcon kind={PLANE} strokes={art.plane} time={time} red={red} width={width} />
              <TripLineLoaderIcon kind={TAXI} strokes={art.taxi} time={time} red={red} width={width} />
              <TripLineLoaderIcon kind={HOTEL} strokes={art.hotel} time={time} red={red} width={width} />
              <TripLineLoaderPill start={1.56} length={64} time={time} width={width} />
              <TripLineLoaderPill start={3.86} length={56} time={time} width={width} />
              <TripLineLoaderPill start={6.16} length={64} time={time} width={width} />
            </TripLineLoaderLean>
          </Group>
          <TripLineLoaderRoute time={time} red={red} last={rowC} width={width} />
          {rows.map((state, index) => (
            <TripLineLoaderRowCard key={index} index={index} state={state} time={time} width={rowWidth} check={art.check} arc={art.arc} />
          ))}
        </Canvas>

        {/* Chrome */}
        <View pointerEvents="none" style={[styles.topBar, { width: rowWidth }]}>
          <Text style={styles.wordmark}>Farewind</Text>
          <Text style={styles.cancel}>Cancel</Text>
        </View>
        <View pointerEvents="none" style={[styles.titleBox, { width: rowWidth }]}>
          <Text style={styles.title}>Building your trip</Text>
        </View>
        <View pointerEvents="none" style={[styles.subtitleBox, { width: rowWidth }]}>
          <Text style={styles.subtitle}>Lisbon to Kyoto · 12–18 Mar · 2 guests</Text>
        </View>
        <Text pointerEvents="none" style={[styles.caption, { width, top: height - 24 - 13 * ASCENDER }]}>
          Matching fares, rides and rooms
        </Text>

        {/* Route labels */}
        <View pointerEvents="none" style={[styles.city, { left: MARGIN, alignItems: 'flex-start' }]}>
          <View style={styles.codeBox}>
            <Text style={styles.code}>LIS</Text>
          </View>
          <Text style={styles.cityName}>Lisbon</Text>
        </View>
        <View pointerEvents="none" style={[styles.city, { right: MARGIN, alignItems: 'flex-end' }]}>
          <View style={styles.codeBox}>
            <Text style={styles.code}>KIX</Text>
          </View>
          <Text style={styles.cityName}>Kyoto</Text>
        </View>

        {rows.map((state, index) => (
          <TripLineLoaderRowText key={index} index={index} state={state} width={rowWidth} />
        ))}

        <GestureDetector gesture={scrub}>
          <View
            style={[styles.hero, { width }]}
            accessible
            accessibilityLabel="Trip loading animation. Drag sideways to scrub."
          />
        </GestureDetector>
      </View>
    </View>
  );
}

// MARK: Hero

function TripLineLoaderShadow({ time, red, width }: { time: SharedValue<number>; red: SharedValue<boolean>; width: number }) {
  const state = useDerivedValue(() => shadow(time.get(), red.get()));
  const rect = useDerivedValue(() => {
    const factor = state.get().factor;
    return Skia.XYWHRect(width / 2 - 65 * factor, 209.5, 130 * factor, 9);
  });
  const opacity = useDerivedValue(() => 0.12 * state.get().opacity);
  return (
    <Oval rect={rect} color={CORAL} opacity={opacity}>
      <BlurMask blur={4} style="normal" />
    </Oval>
  );
}

/** The icon leans with the drag's velocity, about the icon centre (hero x / 2, 112). */
function TripLineLoaderLean({ lean, width, children }: { lean: SharedValue<number>; width: number; children: ReactNode }) {
  const matrix = useDerivedValue(() => {
    const m = Skia.Matrix();
    m.translate(width / 2, 112);
    m.rotate((lean.get() * Math.PI) / 180);
    m.translate(-width / 2, -112);
    return m;
  });
  return <Group matrix={matrix}>{children}</Group>;
}

type IconProps = { kind: number; strokes: Stroke[]; time: SharedValue<number>; red: SharedValue<boolean>; width: number };

function TripLineLoaderIcon({ kind, strokes, time, red, width }: IconProps) {
  const frame = useDerivedValue(() => iconFrame(kind, time.get(), red.get()));
  const matrix = useDerivedValue(() => iconMatrix(kind, frame.get(), width));
  const opacity = useDerivedValue(() => frame.get().opacity);
  const from = useDerivedValue(() => frame.get().from);
  // The car bounces above a steady road; its cloud only drifts sideways.
  const bob = useDerivedValue(() => Skia.Matrix().translate(0, frame.get().bob / 1.4));
  const duration = kind === HOTEL ? 0.3 : 0.32;
  return (
    <Group matrix={matrix} opacity={opacity}>
      {strokes.map((item, index) => (
        <TripLineLoaderPart
          key={index}
          item={item}
          duration={duration}
          frame={frame}
          from={from}
          red={red}
          bob={kind === TAXI && item.part !== TAXI_CLOUD ? bob : undefined}
        />
      ))}
      {/* Dashes are individual segments, sharing their lane's draw slot and gather amount. */}
      {kind === PLANE ? (
        <>
          <TripLineLoaderDashes kind={PLANE} y={20} start={0} end={44} long={18} gap={8} slot={6} frame={frame} />
          <TripLineLoaderDashes kind={PLANE} y={76} start={0} end={30} long={18} gap={8} slot={6} frame={frame} />
          <TripLineLoaderDashes kind={PLANE} y={98} start={0} end={48} long={18} gap={8} slot={6} frame={frame} />
        </>
      ) : null}
      {kind === TAXI ? <TripLineLoaderDashes kind={TAXI} y={102} start={4} end={172} long={16} gap={9} slot={8} frame={frame} /> : null}
    </Group>
  );
}

type PartProps = {
  item: Stroke;
  duration: number;
  frame: SharedValue<IconFrame>;
  from: SharedValue<number>;
  red: SharedValue<boolean>;
  bob?: SharedValue<SkMatrix>;
};

function TripLineLoaderPart({ item, duration, frame, from, red, bob }: PartProps) {
  const moving = item.part !== PLAIN && item.part !== WHEEL && item.part !== WINDOW;
  let content = (
    <>
      {item.part === WHEEL ? <TripLineLoaderWheelFill x={item.x} delay={item.delay} frame={frame} /> : null}
      {item.part === WINDOW ? <TripLineLoaderWindowFill path={item.path} index={item.index} frame={frame} /> : null}
      <TripLineLoaderStroke item={item} duration={duration} frame={frame} from={from} />
    </>
  );
  if (moving) {
    content = (
      <TripLineLoaderPartGroup part={item.part} x={item.x} frame={frame} red={red}>
        {content}
      </TripLineLoaderPartGroup>
    );
  }
  return bob ? <Group matrix={bob}>{content}</Group> : content;
}

function TripLineLoaderPartGroup({ part, x, frame, red, children }: { part: number; x: number; frame: SharedValue<IconFrame>; red: SharedValue<boolean>; children: ReactNode }) {
  const matrix = useDerivedValue(() => partMatrix(part, x, frame.get().t, red.get()));
  return <Group matrix={matrix}>{children}</Group>;
}

/** One stroke of the icon, trimmed `from…to` like Swift's trimmedPath. */
function TripLineLoaderStroke({ item, duration, frame, from }: { item: Stroke; duration: number; frame: SharedValue<IconFrame>; from: SharedValue<number> }) {
  const { delay } = item;
  const end = useDerivedValue(() => strokeEnd(frame.get(), delay, duration));
  // Nothing is drawn until the trim is non-empty (an empty trim would otherwise draw the whole path).
  const opacity = useDerivedValue(() => (end.get() > from.get() ? 1 : 0));
  return (
    <Path
      path={item.path}
      style="stroke"
      strokeWidth={item.width}
      strokeCap="round"
      strokeJoin="round"
      color={CORAL}
      start={from}
      end={end}
      opacity={opacity}
    />
  );
}

/** Opaque wheel centres hide the body edge before the circle and spoke draw on. */
function TripLineLoaderWheelFill({ x, delay, frame }: { x: number; delay: number; frame: SharedValue<IconFrame> }) {
  const opacity = useDerivedValue(() => out(seg(frame.get().elapsed, delay, delay + 0.32)));
  return <Circle cx={x} cy={86} r={11} color={BG} opacity={opacity} />;
}

/** The hotel's windows light one by one, bottom row first, and fade with the gather. */
function TripLineLoaderWindowFill({ path, index, frame }: { path: SkPath; index: number; frame: SharedValue<IconFrame> }) {
  const opacity = useDerivedValue(() => {
    const f = frame.get();
    const light = out(seg(f.t, 4.95 + index * 0.1, 5.1 + index * 0.1));
    return 0.85 * light * (1 - f.gather);
  });
  return <Path path={path} color={CORAL} opacity={opacity} />;
}

type DashesProps = { kind: number; y: number; start: number; end: number; long: number; gap: number; slot: number; frame: SharedValue<IconFrame> };

function TripLineLoaderDashes({ kind, y, start, end, long, gap, slot, frame }: DashesProps) {
  const clip = useMemo(() => Skia.XYWHRect(start, y - 3, end - start, 6), [start, end, y]);
  const path = useDerivedValue(() => {
    const f = frame.get();
    const plane = kind === PLANE;
    // A full long/short pair repeats every 0.5 s; both lengths and both gaps stay intact.
    const phase = (f.t % 0.5) / 0.5;
    const lengthScale = plane ? 1 + 0.5 * f.build : 1;
    const delay = slot * 0.035;
    const drawn = out(seg(f.elapsed, delay, delay + 0.32));
    return dashLane(y, start, long, gap, phase, lengthScale, f.from, Math.min(drawn, 1 - f.from));
  });
  const opacity = useDerivedValue(() => (kind === PLANE ? 0.55 + 0.45 * frame.get().build : 1));
  return (
    <Group clip={clip}>
      <Path path={path} style="stroke" strokeWidth={2.2} strokeCap="round" color={CORAL} opacity={opacity} />
    </Group>
  );
}

function TripLineLoaderPill({ start, length, time, width }: { start: number; length: number; time: SharedValue<number>; width: number }) {
  const state = useDerivedValue(() => pill(time.get(), start, length, width));
  const p1 = useDerivedValue(() => ({ x: state.get().x1, y: 112 }));
  const p2 = useDerivedValue(() => ({ x: state.get().x2, y: 112 }));
  const opacity = useDerivedValue(() => state.get().on);
  return <Line p1={p1} p2={p2} style="stroke" strokeWidth={3} strokeCap="round" color={CORAL} opacity={opacity} />;
}

// MARK: Route and rows

type RouteProps = { time: SharedValue<number>; red: SharedValue<boolean>; last: SharedValue<RowState>; width: number };

function TripLineLoaderRoute({ time, red, last, width }: RouteProps) {
  const path = useMemo(() => line(68, 8, width - 68, 8), [width]);
  const fill = useDerivedValue(() => route(time.get(), red.get()));
  const from = useDerivedValue(() => fill.get().from);
  const to = useDerivedValue(() => fill.get().to);
  const on = useDerivedValue(() => (fill.get().to > fill.get().from ? 1 : 0));
  // The KIX dot pops as the stay is booked and fills with the last row.
  const r = useDerivedValue(() => 3.5 * (1 + (red.get() ? 0 : Math.sin(Math.PI * seg(time.get(), 6.22, 6.52)) * 0.4)));
  const done = useDerivedValue(() => last.get().done);
  return (
    <Group transform={[{ translateY: ROUTE_Y }]}>
      <Path path={path} style="stroke" strokeWidth={2} color={TRACK}>
        <DashPathEffect intervals={[4, 6]} />
      </Path>
      <Path path={path} style="stroke" strokeWidth={2.5} strokeCap="round" color={CORAL} start={from} end={to} opacity={on} />
      <Circle cx={68} cy={8} r={3.5} color={TRACK} />
      <Circle cx={68} cy={8} r={3.5} color={CORAL} />
      <Circle cx={width - 68} cy={8} r={r} color={TRACK} />
      <Circle cx={width - 68} cy={8} r={r} color={CORAL} opacity={done} />
    </Group>
  );
}

type RowCardProps = { index: number; state: SharedValue<RowState>; time: SharedValue<number>; width: number; check: SkPath; arc: SkPath };

function TripLineLoaderRowCard({ index, state, time, width, check, arc }: RowCardProps) {
  const active = useDerivedValue(() => state.get().active);
  const border = useDerivedValue(() => 0.35 * state.get().active);
  const done = useDerivedValue(() => state.get().done);
  const end = useDerivedValue(() => state.get().check);
  const checkOn = useDerivedValue(() => (state.get().check > 0 ? 1 : 0));
  // The spinner's arc turns once a second: from 360·t − 90° to +90°.
  const spin = useDerivedValue(() => {
    const m = Skia.Matrix();
    m.translate(12, 12);
    m.rotate(2 * Math.PI * time.get());
    m.translate(-12, -12);
    return m;
  });
  const pop = useDerivedValue(() => {
    const s = state.get().pop;
    const m = Skia.Matrix();
    m.translate(12, 12);
    m.scale(s, s);
    m.translate(-12, -12);
    return m;
  });
  return (
    <Group transform={[{ translateX: MARGIN }, { translateY: ROW_Y + index * ROW_STEP }]}>
      <RoundedRect x={0} y={0} width={width} height={ROW_H} r={16} color={CARD} />
      <RoundedRect x={0} y={0} width={width} height={ROW_H} r={16} color={CORAL_SOFT} opacity={active} />
      <RoundedRect x={0.5} y={0.5} width={width - 1} height={ROW_H - 1} r={15.5} style="stroke" strokeWidth={1} color={CARD_BORDER} />
      <RoundedRect x={0.5} y={0.5} width={width - 1} height={ROW_H - 1} r={15.5} style="stroke" strokeWidth={1} color={CORAL} opacity={border} />
      <Group transform={[{ translateX: 16 }, { translateY: 22 }]}>
        <Circle cx={12} cy={12} r={11} style="stroke" strokeWidth={2} color={TRACK} />
        <Group matrix={spin}>
          <Path path={arc} style="stroke" strokeWidth={2} strokeCap="round" color={CORAL} opacity={active} />
        </Group>
        <Group matrix={pop} opacity={done}>
          <Circle cx={12} cy={12} r={12} color={CORAL} />
          <Path path={check} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#FFFFFF" start={0} end={end} opacity={checkOn} />
        </Group>
      </Group>
    </Group>
  );
}

function TripLineLoaderRowText({ index, state, width }: { index: number; state: SharedValue<RowState>; width: number }) {
  const copy = ROWS[index];
  const waiting = useAnimatedStyle(() => ({ opacity: state.get().waiting }));
  const searching = useAnimatedStyle(() => ({ opacity: state.get().searching }));
  const done = useAnimatedStyle(() => ({ opacity: state.get().done }));
  return (
    <View
      pointerEvents="none"
      style={[styles.row, { top: ROW_Y + index * ROW_STEP, width }]}
      accessible
      accessibilityLabel={`${copy.title}, ${copy.detail}`}
    >
      <View style={[styles.rowTexts, { width: width - 56 - 16 - 82 - 8 }]}>
        <Text style={styles.rowTitle}>{copy.title}</Text>
        <Text style={styles.rowDetail} numberOfLines={1} ellipsizeMode="tail">
          {copy.detail}
        </Text>
      </View>
      <View style={[styles.trailing, { left: width - 98 }]}>
        <Animated.View style={[styles.status, waiting]}>
          <Text style={styles.waiting}>Waiting</Text>
        </Animated.View>
        <Animated.View style={[styles.status, searching]}>
          <Text style={styles.searching}>Searching…</Text>
        </Animated.View>
        <Animated.View style={[styles.status, done]}>
          <Text style={styles.price}>{copy.price}</Text>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG, overflow: 'hidden' },
  safe: { position: 'absolute' },
  canvas: { position: 'absolute', left: 0, top: 0 },
  topBar: { position: 'absolute', left: MARGIN, top: 8, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  wordmark: { color: CORAL, fontSize: 17, fontWeight: '600', letterSpacing: 0.2 },
  cancel: { color: MUTED, fontSize: 15 },
  titleBox: { position: 'absolute', left: MARGIN, top: 68, height: 36, alignItems: 'center', justifyContent: 'center' },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.4 },
  subtitleBox: { position: 'absolute', left: MARGIN, top: 112, height: 20, alignItems: 'center', justifyContent: 'center' },
  subtitle: { color: MUTED, fontSize: 15 },
  caption: { position: 'absolute', left: 0, textAlign: 'center', color: MUTED, fontSize: 13 },
  city: { position: 'absolute', top: ROUTE_Y, gap: 2 },
  codeBox: { height: 16, justifyContent: 'center' },
  code: { color: INK, fontSize: 13, fontWeight: '600', letterSpacing: 0.6 },
  cityName: { color: MUTED, fontSize: 12 },
  row: { position: 'absolute', left: MARGIN, height: ROW_H },
  rowTexts: { position: 'absolute', left: 56, top: 14, gap: 4 },
  rowTitle: { color: INK, fontSize: 16, fontWeight: '600' },
  rowDetail: { color: MUTED, fontSize: 13 },
  trailing: { position: 'absolute', top: 0, width: 82, height: ROW_H },
  status: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, alignItems: 'flex-end', justifyContent: 'center' },
  waiting: { color: MUTED, fontSize: 13 },
  searching: { color: CORAL, fontSize: 13, fontWeight: '500' },
  price: { color: INK, fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
  hero: { position: 'absolute', left: 0, top: HERO_Y, height: HERO_H },
});
