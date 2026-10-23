import {
  BlendMode,
  BlurStyle,
  Canvas,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkColor,
  type SkImage,
  type SkPicture,
  type SkRect,
  type SkRSXform,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Gathers the swarm on a figure-eight, flicks it apart, syncs its blinking, lets it settle, on a loop. */
  autoPlay?: boolean;
  /** The line of hints along the bottom edge. */
  hint?: string[];
};

// autoPlay, one LOOP in seconds. Every periodic thing (blinks, wind, the hover of a resting
// firefly, the stars) repeats a whole number of times per LOOP, so the loop closes cleanly.
const LOOP = 20;
const TAU = Math.PI * 2;
const W0 = TAU / LOOP;

// The swarm: COUNT fireflies, simulated at a fixed STEP (s), at most MAX_STEPS per frame.
const COUNT = 150;
const STEP = 1 / 60;
const MAX_STEPS = 3;
// Boids: neighbours within SIGHT pt, kept SPACE pt apart. Free flight tops out at CRUISE pt/s,
// steering at TURN pt/s². Weights for separation, alignment, cohesion and wander.
const SIGHT = 46;
const SPACE = 16;
const CRUISE = 58;
const TURN = 90;
const SEPARATE = 1.6;
const ALIGN = 0.5;
const COHERE = 0.35;
const WANDER = 0.7;
// They keep to the air over the meadow: between BAND_TOP and BAND_BOTTOM of the height, and
// MARGIN pt in from the sides, pushed back at EDGE_PUSH per pt over.
const BAND_TOP = 0.28;
const BAND_BOTTOM = 0.84;
const MARGIN = 36;
const EDGE_PUSH = 1.6;
// A finger: they orbit it at ORBIT_SPEED pt/s on their own radius, up to RUSH pt/s, gathering in
// over GATHER per s. A release faster than FLICK pt/s scatters them with up to BLAST pt/s.
const ORBIT_SPEED = 150;
const RUSH = 240;
const GATHER = 4;
const FLICK = 900;
const BLAST = 520;
const BLAST_REACH = 170;
// Holding still for HOLD_MS calms them (HUSH damping per s) and pulls their blinks into a wave
// rolling out from the finger: one pulse every 1 / SYNC_RATE s, travelling at WAVE_SPEED pt/s.
const HOLD_MS = 450;
const HUSH = 1.3;
const HELD_PULL = 0.35;
const HELD_REACH = 150;
const SYNC_RATE = 1;
const WAVE_SPEED = 240;
const SYNC_PULL = 7;
const SYNC_IN = 1.6;
const SYNC_OUT = 0.45;
const DRIFT_BACK = 1.2;
// Resting (autoPlay only): each firefly hovers over its own spot in the meadow, held there by a
// spring of HOME_OMEGA rad/s.
const HOME_OMEGA = 3.2;
// A touch pauses autoPlay; it comes back RESUME s after the finger lifts, with the swarm
// drifting home.
const RESUME = 3;

// The script.
const EIGHT_FROM = 1.6;
const EIGHT_TIME = 6.6;
const EIGHT_WIDTH = 0.22;
const EIGHT_HEIGHT = 0.14;
const EIGHT_Y = 0.54;
const SWIPE_TIME = 0.22;
const SWIPE_TO = { x: 0.82, y: 0.4 };
const PRESS_FROM = 9.4;
const PRESS_TIME = 3.8;
const PRESS_AT = { x: 0.46, y: 0.5 };
const HOME_FROM = 14.4;
const HOME_TIME = 2.6;

// Glow sprites: a soft halo and a hot core, drawn additively from one atlas.
const SPRITE = 64;
const EMBER = 0.1;
const HALO = [0.62, 0.95, 0.3];
const CORE = [1.0, 0.98, 0.74];

// Meadow: three layers of grass blades (count, base as a share of the height, height range in pt,
// width range, colour), swaying in a wind that repeats per LOOP and bending where fireflies pass.
type GrassLayer = { count: number; base: number; low: number; high: number; thin: number; thick: number; color: string; tip: string; give: number };
const GRASS: GrassLayer[] = [
  { count: 90, base: 0.74, low: 22, high: 60, thin: 2.5, thick: 4.5, color: '#0f1028', tip: '#4b4579', give: 0.5 },
  { count: 70, base: 0.86, low: 50, high: 120, thin: 3, thick: 6, color: '#08091b', tip: '#3a3664', give: 0.8 },
  { count: 48, base: 1.01, low: 96, high: 210, thin: 4, thick: 8.5, color: '#030409', tip: '#2b2950', give: 1 },
];
const BUCKET = 24;
const BEND_OMEGA = 6;
const BEND_DAMPING = 0.35;
const BEND_GAIN = 0.0045;
const BEND_MAX = 0.5;
const STARS = 56;
const MEADOW_TOP = '#14142e';

const HINT = ['Touch to gather', 'Flick to scatter', 'Hold to sync'];

/** A small seeded generator, so the meadow and the swarm are the same every time. */
function seeded(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Each firefly's fixed traits: where it rests and how it hovers there, its blink (a whole number
// of flashes per LOOP), its depth, its orbit and its wander.
type Traits = {
  homeX: number[];
  homeY: number[];
  bobX: number[];
  bobY: number[];
  bobXK: number[];
  bobYK: number[];
  bobXP: number[];
  bobYP: number[];
  blinkK: number[];
  blinkP: number[];
  depth: number[];
  orbit: number[];
  spin: number[];
  wanderA: number[];
  wanderB: number[];
};
const TRAITS: Traits = (() => {
  const r = seeded(11);
  const t: Traits = {
    homeX: [], homeY: [], bobX: [], bobY: [], bobXK: [], bobYK: [], bobXP: [], bobYP: [],
    blinkK: [], blinkP: [], depth: [], orbit: [], spin: [], wanderA: [], wanderB: [],
  };
  for (let i = 0; i < COUNT; i += 1) {
    t.homeX.push(0.05 + 0.9 * r());
    t.homeY.push(0.36 + 0.48 * Math.pow(r(), 0.8));
    t.bobX.push(6 + 12 * r());
    t.bobY.push(4 + 9 * r());
    t.bobXK.push(1 + Math.floor(r() * 2));
    t.bobYK.push(1 + Math.floor(r() * 3));
    t.bobXP.push(r() * TAU);
    t.bobYP.push(r() * TAU);
    t.blinkK.push(7 + Math.floor(r() * 7));
    t.blinkP.push(r());
    t.depth.push(0.55 + 0.65 * r());
    t.orbit.push(30 + 95 * Math.pow(r(), 0.7));
    t.spin.push(r() < 0.82 ? 1 : -1);
    t.wanderA.push(r() * TAU);
    t.wanderB.push(r() * TAU);
  }
  return t;
})();

// Blades: x as a share of the width, height and width in pt, a phase for the wind.
type Blades = { x: number[]; h: number[]; w: number[]; p: number[]; lean: number[] };
const BLADES: Blades[] = GRASS.map((layer, k) => {
  const r = seeded(31 + k);
  const b: Blades = { x: [], h: [], w: [], p: [], lean: [] };
  for (let i = 0; i < layer.count; i += 1) {
    b.x.push((i + r() * 0.9) / layer.count * 1.08 - 0.04);
    b.h.push(layer.low + (layer.high - layer.low) * Math.pow(r(), 1.4));
    b.w.push(layer.thin + (layer.thick - layer.thin) * r());
    b.p.push(r() * TAU);
    b.lean.push((r() - 0.5) * 0.35);
  }
  return b;
});

const STAR_FIELD = (() => {
  const r = seeded(5);
  const s: { x: number[]; y: number[]; size: number[]; k: number[]; p: number[] } = { x: [], y: [], size: [], k: [], p: [] };
  for (let i = 0; i < STARS; i += 1) {
    s.x.push(r());
    s.y.push(0.02 + 0.42 * Math.pow(r(), 1.3));
    s.size.push(0.5 + 1.1 * Math.pow(r(), 3));
    s.k.push(1 + Math.floor(r() * 5));
    s.p.push(r() * TAU);
  }
  return s;
})();

// The far tree line's crowns, drawn from a fixed seed.
const TREE_LINE = (() => {
  const r = seeded(77);
  return Array.from({ length: 400 }, () => r());
})();

function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

function smooth(t: number) {
  'worklet';
  const x = clamp01(t);
  return x * x * (3 - 2 * x);
}

/** A firefly's flash at blink phase `p`: a quick rise, a slower fade, an ember between. */
function flash(p: number) {
  'worklet';
  const f = p - Math.floor(p);
  if (f < 0.1) return EMBER + (1 - EMBER) * smooth(f / 0.1);
  return EMBER + (1 - EMBER) * Math.exp(-(f - 0.1) * 8);
}

/** Steering towards a desired velocity, capped at TURN. */
function steer(dx: number, dy: number, vx: number, vy: number, speed: number, out: number[]) {
  'worklet';
  const l = Math.hypot(dx, dy);
  if (l < 1e-6) {
    out[0] = 0;
    out[1] = 0;
    return;
  }
  let sx = (dx / l) * speed - vx;
  let sy = (dy / l) * speed - vy;
  const m = Math.hypot(sx, sy);
  if (m > TURN) {
    sx = (sx / m) * TURN;
    sy = (sy / m) * TURN;
  }
  out[0] = sx;
  out[1] = sy;
}

/** Where autoPlay's finger is at `t` s into the loop: down or not, held still or moving. */
function scriptFinger(t: number, w: number, h: number) {
  'worklet';
  const eight = (t - EIGHT_FROM) / EIGHT_TIME;
  if (eight >= 0 && eight <= 1) {
    const a = eight * TAU;
    return { down: true, hold: false, x: w * (0.5 + EIGHT_WIDTH * Math.sin(a)), y: h * (EIGHT_Y + EIGHT_HEIGHT * Math.sin(2 * a)) };
  }
  const swipe = (t - EIGHT_FROM - EIGHT_TIME) / SWIPE_TIME;
  if (swipe > 0 && swipe <= 1) {
    const e = swipe * swipe;
    return { down: true, hold: false, x: w * (0.5 + (SWIPE_TO.x - 0.5) * e), y: h * (EIGHT_Y + (SWIPE_TO.y - EIGHT_Y) * e) };
  }
  const press = (t - PRESS_FROM) / PRESS_TIME;
  if (press >= 0 && press <= 1) {
    return { down: true, hold: t - PRESS_FROM > HOLD_MS / 1000, x: w * PRESS_AT.x, y: h * PRESS_AT.y };
  }
  return { down: false, hold: false, x: 0, y: 0 };
}

/** The glow atlas: a soft halo on the left, a hot core on the right, white on clear. */
function makeAtlas(): SkImage | null {
  'worklet';
  const surface = Skia.Surface.MakeOffscreen(SPRITE * 2, SPRITE);
  if (!surface) return null;
  const c = surface.getCanvas();
  const r = SPRITE / 2;
  const halo = Skia.Paint();
  const haloStops = [0, 0.12, 0.3, 0.55, 1];
  const haloColors = [1, 0.62, 0.3, 0.1, 0].map((a) => Float32Array.of(1, 1, 1, a));
  halo.setShader(Skia.Shader.MakeRadialGradient({ x: r, y: r }, r, haloColors, haloStops, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, SPRITE, SPRITE), halo);
  const core = Skia.Paint();
  const coreColors = [1, 0.9, 0.35, 0].map((a) => Float32Array.of(1, 1, 1, a));
  core.setShader(Skia.Shader.MakeRadialGradient({ x: SPRITE + r, y: r }, r, coreColors, [0, 0.22, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(SPRITE, 0, SPRITE, SPRITE), core);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** A tree line along `base`: rounded crowns with now and then a tall pointed conifer. */
function treeLine(w: number, base: number, scale: number, seed: number) {
  'worklet';
  const pick = (k: number) => TREE_LINE[(seed + k) % TREE_LINE.length];
  const line = Skia.PathBuilder.Make();
  line.moveTo(-10, base + 12);
  line.lineTo(-10, base - 6 * scale);
  let x = -10;
  let n = 0;
  while (x < w + 20) {
    if (pick(n) < 0.22) {
      // A conifer: narrow, tall, pointed, its sides gently concave.
      const wide = (13 + pick(n + 1) * 10) * scale;
      const tall = (24 + pick(n + 2) * 22) * scale;
      line.quadTo(x + wide * 0.42, base - tall * 0.45, x + wide * 0.5, base - tall);
      line.quadTo(x + wide * 0.58, base - tall * 0.45, x + wide, base - 6 * scale);
      x += wide;
    } else {
      const crown = (12 + pick(n + 1) * 24) * scale;
      const top = base - (12 + pick(n + 2) * 22) * scale;
      line.cubicTo(x, top, x + crown, top, x + crown, base - (4 + pick(n + 3) * 6) * scale);
      x += crown * 0.8;
    }
    n += 4;
  }
  line.lineTo(w + 20, base + 12);
  line.close();
  return line.build();
}

/** The still part of the dusk: sky, stars' field glow, moon, the far tree line and the meadow floor. */
function makeBackdrop(w: number, h: number): SkPicture {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
  const sky = Skia.Paint();
  sky.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: h * 0.6 },
      [Skia.Color('#070a1d'), Skia.Color('#121741'), Skia.Color('#2b2a5e'), Skia.Color('#6b4a76'), Skia.Color('#c97a5c')],
      [0, 0.32, 0.6, 0.82, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), sky);
  // The moon: a thin crescent with a soft halo.
  const mx = w * 0.78;
  const my = h * 0.13;
  // (A blurred disc rather than a gradient: a near-clear gradient dithers its whole bounding box,
  // which shows as a faint rectangle on the dark sky.)
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setColor(Skia.Color('rgba(214, 208, 255, 0.13)'));
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 34, true));
  c.drawCircle(mx, my, 48, glow);
  const disc = Skia.PathBuilder.Make().addCircle(mx, my, 15).build();
  const bite = Skia.PathBuilder.Make().addCircle(mx + 6.5, my - 4, 13.5).build();
  const crescent = Skia.Path.MakeFromOp(disc, bite, 0);
  if (crescent) {
    const moon = Skia.Paint();
    moon.setAntiAlias(true);
    moon.setColor(Skia.Color('#f2ead6'));
    c.drawPath(crescent, moon);
  }
  // Two tree lines, the far one paler in the haze, mist lying between them, then the meadow.
  const base = h * 0.6;
  const far = Skia.Paint();
  far.setAntiAlias(true);
  far.setColor(Skia.Color('#3a2f5e'));
  c.drawPath(treeLine(w, base - 12, 0.7, 0), far);
  const mist = Skia.Paint();
  mist.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: base - 40 },
      { x: 0, y: base + 10 },
      [Skia.Color('rgba(196, 128, 140, 0)'), Skia.Color('rgba(176, 118, 146, 0.35)'), Skia.Color('rgba(120, 90, 140, 0.1)')],
      [0, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, base - 40, w, 50), mist);
  const near = Skia.Paint();
  near.setAntiAlias(true);
  near.setColor(Skia.Color(MEADOW_TOP));
  c.drawPath(treeLine(w, base, 1, 97), near);
  const floor = Skia.Paint();
  floor.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: base + 6 },
      { x: 0, y: h },
      [Skia.Color(MEADOW_TOP), Skia.Color('#0c0e22'), Skia.Color('#04050b')],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, base + 6, w, h - base - 6), floor);
  return recorder.finishRecordingAsPicture();
}

/** One layer of grass: each blade a tapered curve bent by the wind and the swarm. */
function drawGrass(canvas: SkCanvas, layer: GrassLayer, blades: Blades, w: number, h: number, t: number, bend: number[]) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const y0 = layer.base * h;
  for (let i = 0; i < blades.x.length; i += 1) {
    const x0 = blades.x[i] * w;
    const height = blades.h[i];
    const half = blades.w[i] / 2;
    // Wind: two gusts that repeat per LOOP, travelling across the meadow.
    const wind = 0.07 * Math.sin(3 * W0 * t - x0 * 0.011 + blades.p[i] * 0.3) + 0.035 * Math.sin(7 * W0 * t + blades.p[i]);
    const k = Math.min(bend.length - 1, Math.max(0, Math.floor(x0 / BUCKET)));
    const angle = blades.lean[i] + wind + bend[k] * layer.give;
    const tipX = x0 + Math.sin(angle) * height;
    const tipY = y0 - Math.cos(angle) * height;
    const midX = x0 + Math.sin(angle * 0.45) * height * 0.55;
    const midY = y0 - Math.cos(angle * 0.45) * height * 0.55;
    b.moveTo(x0 - half, y0);
    b.quadTo(midX - half * 0.7, midY, tipX, tipY);
    b.quadTo(midX + half * 0.7, midY, x0 + half, y0);
    b.close();
  }
  // The bank the blades stand on, then the blades lit faintly from the sky at their tips.
  b.addRect(Skia.XYWHRect(-10, y0 - 1, w + 20, h - y0 + 12));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: y0 - layer.high },
      { x: 0, y: y0 },
      [Skia.Color(layer.tip), Skia.Color(layer.color)],
      [0, 0.75],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(b.build(), paint);
}

type Swarm = { x: number[]; y: number[]; vx: number[]; vy: number[]; drift: number[] };

/** Glows: halos and cores of one depth layer in a single additive atlas pass. */
function drawFlies(canvas: SkCanvas, sprites: SkImage, s: Swarm, now: number, near: boolean) {
  'worklet';
  const rects: SkRect[] = [];
  const xforms: SkRSXform[] = [];
  const colors: SkColor[] = [];
  const haloSrc = Skia.XYWHRect(0, 0, SPRITE, SPRITE);
  const coreSrc = Skia.XYWHRect(SPRITE, 0, SPRITE, SPRITE);
  for (let i = 0; i < COUNT; i += 1) {
    const depth = TRAITS.depth[i];
    if (depth >= 0.85 !== near) continue;
    const own = TRAITS.blinkP[i] + (TRAITS.blinkK[i] / LOOP) * now;
    const glow = flash(own + s.drift[i]);
    const halo = ((14 + 62 * glow) * depth) / SPRITE;
    const core = ((4 + 6 * glow) * depth) / SPRITE;
    const fade = 0.55 + 0.45 * clamp01((depth - 0.55) / 0.6);
    const hot = Math.min(1, (0.3 + glow) * fade);
    const warm = Math.min(1, glow * 0.85 * fade);
    rects.push(haloSrc);
    xforms.push(Skia.RSXform(halo, 0, s.x[i] - (SPRITE / 2) * halo, s.y[i] - (SPRITE / 2) * halo));
    colors.push(Float32Array.of(HALO[0] * warm, HALO[1] * warm, HALO[2] * warm, 1));
    rects.push(coreSrc);
    xforms.push(Skia.RSXform(core, 0, s.x[i] - (SPRITE / 2) * core, s.y[i] - (SPRITE / 2) * core));
    colors.push(Float32Array.of(CORE[0] * hot, CORE[1] * hot, CORE[2] * hot, 1));
  }
  if (rects.length === 0) return;
  const paint = Skia.Paint();
  paint.setBlendMode(BlendMode.Plus);
  canvas.drawAtlas(sprites, rects, xforms, paint, BlendMode.Modulate, colors);
}


export function FireflySwarm({ autoPlay = false, hint = HINT }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const { width, height } = size;

  const stage = useSharedValue({ width: 0, height: 0 });
  const swarm = useSharedValue<Swarm>({ x: [], y: [], vx: [], vy: [], drift: [] });
  const atlas = useSharedValue<SkImage | null>(null);
  const backdrop = useSharedValue<SkPicture | null>(null);
  // The size the backdrop and the grass buckets were built for: rebuilt when the stage resizes.
  const built = useSharedValue({ width: 0, height: 0 });
  const bend = useSharedValue<number[]>([]);
  const bendSpeed = useSharedValue<number[]>([]);
  const elapsed = useSharedValue(0);
  const spare = useSharedValue(0);
  const tick = useSharedValue(0);

  // The finger: down or not, where, how strongly it pulls (0–1), and whether it is held still.
  const fingerDown = useSharedValue(false);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const pull = useSharedValue(0);
  const holding = useSharedValue(false);
  // Synchronised blinking: its strength (0–1) and the point its wave rolls out from.
  const sync = useSharedValue(0);
  const syncX = useSharedValue(0);
  const syncY = useSharedValue(0);
  // How strongly the fireflies are held to their resting spots (autoPlay).
  const home = useSharedValue(autoPlay && !reducedMotion ? 1 : 0);
  // autoPlay's clock, its finger, and how long a touch still holds it.
  const clock = useSharedValue(0);
  const autoFinger = useSharedValue(false);
  const hold = useSharedValue(0);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
    stage.set({ width: w, height: h });
  };

  const gathered = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const scattered = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };
  const synced = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  // Helpers called from worklets are named functions, not inline arrows, so the React Compiler
  // cannot hoist them out of the worklets.

  /** Throws every firefly away from (x, y), harder the closer it is, a little along (dx, dy). */
  function scatter(x: number, y: number, dx: number, dy: number) {
    'worklet';
    const s = swarm.get();
    const l = Math.hypot(dx, dy) || 1;
    for (let i = 0; i < COUNT; i += 1) {
      const ox = s.x[i] - x;
      const oy = s.y[i] - y;
      const r = Math.hypot(ox, oy) || 1;
      const power = BLAST * Math.exp(-r / BLAST_REACH) * (reducedMotion ? 0.3 : 1);
      s.vx[i] += (ox / r) * power + (dx / l) * power * 0.35;
      s.vy[i] += (oy / r) * power + (dy / l) * power * 0.35;
    }
    pull.set(0);
  }

  function startSync(x: number, y: number) {
    'worklet';
    holding.set(true);
    syncX.set(x);
    syncY.set(y);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    if (dt <= 0) return;
    const { width: w, height: h } = stage.get();
    if (w === 0) return;
    let s = swarm.get();
    if (s.x.length !== COUNT) {
      // Everyone starts on their resting spot, moving as the hover moves.
      const fresh: Swarm = { x: [], y: [], vx: [], vy: [], drift: [] };
      for (let i = 0; i < COUNT; i += 1) {
        fresh.x.push(TRAITS.homeX[i] * w + TRAITS.bobX[i] * Math.sin(TRAITS.bobXP[i]));
        fresh.y.push(TRAITS.homeY[i] * h + TRAITS.bobY[i] * Math.sin(TRAITS.bobYP[i]));
        fresh.vx.push(TRAITS.bobX[i] * TRAITS.bobXK[i] * W0 * Math.cos(TRAITS.bobXP[i]));
        fresh.vy.push(TRAITS.bobY[i] * TRAITS.bobYK[i] * W0 * Math.cos(TRAITS.bobYP[i]));
        fresh.drift.push(0);
      }
      swarm.set(fresh);
      s = fresh;
    }
    // The grass buckets and the still backdrop follow the stage's size (built once, and again if
    // the block is resized), so the bend index and the drawing always match the width.
    const size = built.get();
    if (size.width !== w || size.height !== h) {
      const buckets: number[] = [];
      for (let k = 0; k <= Math.ceil(w / BUCKET); k += 1) buckets.push(0);
      bend.set(buckets);
      bendSpeed.set(buckets.slice());
      backdrop.set(makeBackdrop(w, h));
      built.set({ width: w, height: h });
      return;
    }
    // The glow sprites, made once (and tried again if the offscreen surface was not ready).
    if (atlas.get() === null) atlas.set(makeAtlas());

    // autoPlay moves a finger along the script; a touch hands control back for a while.
    if (hold.get() > 0) {
      if (!fingerDown.get()) hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(HOME_FROM);
    } else if (auto) {
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      const f = scriptFinger(t, w, h);
      if (f.down) {
        if (!autoFinger.get()) {
          autoFinger.set(true);
          fingerDown.set(true);
        }
        fingerX.set(f.x);
        fingerY.set(f.y);
        if (f.hold && !holding.get()) startSync(f.x, f.y);
      } else if (autoFinger.get()) {
        autoFinger.set(false);
        fingerDown.set(false);
        holding.set(false);
        // The script's swipe ends in a flick.
        if (t < PRESS_FROM) scatter(SWIPE_TO.x * w, SWIPE_TO.y * h, SWIPE_TO.x - 0.5, SWIPE_TO.y - EIGHT_Y);
      }
      // Held home while resting; let go as the finger arrives; called home again at the end.
      let held = 0;
      if (t < EIGHT_FROM) held = 1;
      else if (t < EIGHT_FROM + 1) held = 1 - smooth(t - EIGHT_FROM);
      else if (t >= HOME_FROM) held = smooth((t - HOME_FROM) / HOME_TIME);
      home.set(held);
    }

    // The finger's pull and the sync come and go smoothly.
    // Held still, the finger keeps a gentle pull so the calmed swarm drifts in round it.
    const pulling = fingerDown.get() ? (holding.get() ? HELD_PULL : 1) : 0;
    pull.set(pull.get() + (pulling - pull.get()) * Math.min(1, GATHER * dt));
    sync.set(sync.get() + ((holding.get() ? 1 : 0) - sync.get()) * Math.min(1, (holding.get() ? SYNC_IN : SYNC_OUT) * dt));

    // Fixed steps of the swarm.
    let acc = spare.get() + dt;
    let steps = 0;
    const grid = new Map<number, number[]>();
    const out = [0, 0];
    while (acc >= STEP && steps < MAX_STEPS) {
      acc -= STEP;
      steps += 1;
      const now = elapsed.get() + STEP;
      elapsed.set(now);
      const A = reducedMotion ? pull.get() * 0.4 : pull.get();
      const H = home.get();
      const hush = holding.get() ? HUSH : 0;
      const fx = fingerX.get();
      const fy = fingerY.get();
      // Bin everyone by SIGHT-sized cells for the neighbour search.
      grid.clear();
      const cols = Math.ceil(w / SIGHT) + 2;
      for (let i = 0; i < COUNT; i += 1) {
        const key = Math.floor(s.y[i] / SIGHT + 1) * cols + Math.floor(s.x[i] / SIGHT + 1);
        const cell = grid.get(key);
        if (cell) cell.push(i);
        else grid.set(key, [i]);
      }
      const maxSpeed = CRUISE + (RUSH - CRUISE) * A;
      for (let i = 0; i < COUNT; i += 1) {
        const x = s.x[i];
        const y = s.y[i];
        const vx = s.vx[i];
        const vy = s.vy[i];
        let sepX = 0;
        let sepY = 0;
        let aliX = 0;
        let aliY = 0;
        let cohX = 0;
        let cohY = 0;
        let seen = 0;
        const cx = Math.floor(x / SIGHT + 1);
        const cy = Math.floor(y / SIGHT + 1);
        for (let oy = -1; oy <= 1; oy += 1) {
          for (let ox = -1; ox <= 1; ox += 1) {
            const cell = grid.get((cy + oy) * cols + cx + ox);
            if (!cell) continue;
            for (let n = 0; n < cell.length; n += 1) {
              const j = cell[n];
              if (j === i) continue;
              const dx = x - s.x[j];
              const dy = y - s.y[j];
              const d2 = dx * dx + dy * dy;
              if (d2 > SIGHT * SIGHT || d2 < 1e-6) continue;
              seen += 1;
              aliX += s.vx[j];
              aliY += s.vy[j];
              cohX += s.x[j];
              cohY += s.y[j];
              if (d2 < SPACE * SPACE) {
                sepX += dx / d2;
                sepY += dy / d2;
              }
            }
          }
        }
        let ax = 0;
        let ay = 0;
        const flock = 1 - 0.6 * A;
        if (sepX !== 0 || sepY !== 0) {
          steer(sepX, sepY, vx, vy, maxSpeed, out);
          ax += out[0] * SEPARATE;
          ay += out[1] * SEPARATE;
        }
        if (seen > 0) {
          steer(aliX / seen, aliY / seen, vx, vy, maxSpeed, out);
          ax += out[0] * ALIGN * flock;
          ay += out[1] * ALIGN * flock;
          steer(cohX / seen - x, cohY / seen - y, vx, vy, maxSpeed, out);
          ax += out[0] * COHERE * flock;
          ay += out[1] * COHERE * flock;
        }
        // Wander: a heading that drifts on two slow sines of its own.
        const heading = TRAITS.wanderA[i] + 1.7 * Math.sin(2 * W0 * now + TRAITS.wanderB[i]) + 0.9 * Math.sin(5 * W0 * now + TRAITS.wanderA[i]);
        steer(Math.cos(heading), Math.sin(heading) * 0.7, vx, vy, CRUISE, out);
        ax += out[0] * WANDER * (1 - A);
        ay += out[1] * WANDER * (1 - A);
        // Stay over the meadow.
        if (y < BAND_TOP * h) ay += (BAND_TOP * h - y) * EDGE_PUSH;
        if (y > BAND_BOTTOM * h) ay -= (y - BAND_BOTTOM * h) * EDGE_PUSH;
        if (x < MARGIN) ax += (MARGIN - x) * EDGE_PUSH;
        if (x > w - MARGIN) ax -= (x - (w - MARGIN)) * EDGE_PUSH;
        // Orbit the finger, each on its own radius.
        if (A > 0.001 && hush === 0) {
          const dx = x - fx;
          const dy = y - fy;
          const r = Math.hypot(dx, dy) || 1;
          const spin = TRAITS.spin[i];
          const radial = Math.max(-RUSH, Math.min(RUSH, (TRAITS.orbit[i] - r) * 2.4));
          const wantX = (-dy / r) * ORBIT_SPEED * spin + (dx / r) * radial;
          const wantY = (dx / r) * ORBIT_SPEED * spin + (dy / r) * radial;
          ax += (wantX - vx) * 3 * A;
          ay += (wantY - vy) * 3 * A;
        } else if (hush > 0) {
          // Held still: only the far ones drift in, so the swarm stays spread for the wave.
          const dx = fx - x;
          const dy = fy - y;
          const r = Math.hypot(dx, dy) || 1;
          const inward = Math.max(0, r - HELD_REACH) * 0.6;
          ax += (dx / r) * inward;
          ay += (dy / r) * inward;
        }
        ax -= vx * hush;
        ay -= vy * hush;
        // Held home: a critically damped spring to the hovering spot.
        if (H > 0.001) {
          const kx = TRAITS.bobXK[i] * W0;
          const ky = TRAITS.bobYK[i] * W0;
          const tx = TRAITS.homeX[i] * w + TRAITS.bobX[i] * Math.sin(kx * now + TRAITS.bobXP[i]);
          const ty = TRAITS.homeY[i] * h + TRAITS.bobY[i] * Math.sin(ky * now + TRAITS.bobYP[i]);
          const tvx = TRAITS.bobX[i] * kx * Math.cos(kx * now + TRAITS.bobXP[i]);
          const tvy = TRAITS.bobY[i] * ky * Math.cos(ky * now + TRAITS.bobYP[i]);
          const hx = HOME_OMEGA * HOME_OMEGA * (tx - x) + 2 * HOME_OMEGA * (tvx - vx);
          const hy = HOME_OMEGA * HOME_OMEGA * (ty - y) + 2 * HOME_OMEGA * (tvy - vy);
          ax = ax * (1 - H) + hx * H;
          ay = ay * (1 - H) + hy * H;
        }
        let nvx = vx + ax * STEP;
        let nvy = vy + ay * STEP;
        // A flick may throw them faster for a moment; drag brings them back under the cap.
        const speed = Math.hypot(nvx, nvy);
        const cap = maxSpeed + (1000 - maxSpeed) * H;
        if (speed > cap) {
          const k = Math.max(cap / speed, Math.exp(-3 * STEP));
          nvx *= k;
          nvy *= k;
        }
        s.vx[i] = nvx;
        s.vy[i] = nvy;
        s.x[i] = x + nvx * STEP;
        s.y[i] = y + nvy * STEP;
        // Blink drift: pulled towards the travelling wave while synced, back to its own rhythm after.
        const own = TRAITS.blinkP[i] + (TRAITS.blinkK[i] / LOOP) * now;
        const S = sync.get();
        let d = s.drift[i];
        if (S > 0.001) {
          const dist = Math.hypot(x - syncX.get(), y - syncY.get());
          const wave = SYNC_RATE * now - dist / (WAVE_SPEED / SYNC_RATE) * SYNC_RATE;
          d += (S * SYNC_PULL * Math.sin(TAU * (wave - own - d))) / TAU * STEP;
        }
        d -= (1 - S) * DRIFT_BACK * (d - Math.round(d)) * STEP;
        s.drift[i] = d;
      }

      // The grass under the swarm bends with the fireflies passing low over it.
      const b = bend.get();
      const bv = bendSpeed.get();
      const push: number[] = [];
      for (let k = 0; k < b.length; k += 1) push.push(0);
      for (let i = 0; i < COUNT; i += 1) {
        const low = clamp01((s.y[i] - h * 0.6) / (h * 0.2));
        if (low <= 0) continue;
        const k = Math.floor(s.x[i] / BUCKET);
        if (k < 0 || k >= b.length) continue;
        push[k] += s.vx[i] * low * BEND_GAIN;
      }
      for (let k = 0; k < b.length; k += 1) {
        const target = Math.max(-BEND_MAX, Math.min(BEND_MAX, push[k] + 0.4 * ((push[k - 1] ?? 0) + (push[k + 1] ?? 0))));
        bv[k] += (-BEND_OMEGA * BEND_OMEGA * (b[k] - target) - 2 * BEND_DAMPING * BEND_OMEGA * bv[k]) * STEP;
        b[k] += bv[k] * STEP;
      }
    }
    spare.set(acc);
    if (steps > 0) tick.set(tick.get() + 1);
  });

  function touched() {
    'worklet';
    hold.set(RESUME);
    home.set(0);
    if (autoFinger.get()) {
      autoFinger.set(false);
      holding.set(false);
    }
  }

  const drag = Gesture.Pan()
    // One finger leads the swarm; a second would jump the pan's averaged position.
    .maxPointers(1)
    .minDistance(2)
    .onBegin((e) => {
      touched();
      fingerDown.set(true);
      fingerX.set(e.x);
      fingerY.set(e.y);
      scheduleOnRN(gathered);
    })
    .onUpdate((e) => {
      fingerX.set(e.x);
      fingerY.set(e.y);
    })
    .onEnd((e) => {
      const speed = Math.hypot(e.velocityX, e.velocityY);
      if (speed > FLICK && !holding.get()) {
        scatter(e.x, e.y, e.velocityX, e.velocityY);
        scheduleOnRN(scattered);
      }
    })
    .onFinalize(() => {
      fingerDown.set(false);
      holding.set(false);
    });

  const press = Gesture.LongPress()
    .minDuration(HOLD_MS)
    .maxDistance(14)
    .onStart((e) => {
      startSync(e.x, e.y);
      scheduleOnRN(synced);
    })
    .onFinalize(() => {
      holding.set(false);
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const back = backdrop.get();
    const sprites = atlas.get();
    const s = swarm.get();
    if (!back || !sprites || width === 0 || s.x.length !== COUNT) return recorder.finishRecordingAsPicture();
    const now = elapsed.get();
    canvas.drawPicture(back);

    // Stars, each twinkling a whole number of times per LOOP.
    const star = Skia.Paint();
    star.setAntiAlias(true);
    for (let i = 0; i < STARS; i += 1) {
      const a = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(STAR_FIELD.k[i] * W0 * now + STAR_FIELD.p[i]));
      star.setColor(Float32Array.of(0.92, 0.9, 1, a * 0.8));
      canvas.drawCircle(STAR_FIELD.x[i] * width, STAR_FIELD.y[i] * height, STAR_FIELD.size[i], star);
    }

    // Faint rings show the sync wave rolling out, one per pulse.
    const S = sync.get();
    if (S > 0.01) {
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(2);
      ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
      const phase = (SYNC_RATE * now) % 1;
      for (let k = 0; k < 3; k += 1) {
        const r = (phase + k) * WAVE_SPEED;
        ring.setColor(Float32Array.of(0.85, 1, 0.55, 0.1 * S * (1 - (phase + k) / 3)));
        canvas.drawCircle(syncX.get(), syncY.get(), r, ring);
      }
    }

    const b = bend.get();
    // Not built for this size yet: the bend index would read past the buckets.
    if (b.length === 0) return recorder.finishRecordingAsPicture();
    drawGrass(canvas, GRASS[0], BLADES[0], width, height, now, b);
    drawFlies(canvas, sprites, s, now, false);
    drawGrass(canvas, GRASS[1], BLADES[1], width, height, now, b);
    drawGrass(canvas, GRASS[2], BLADES[2], width, height, now, b);
    drawFlies(canvas, sprites, s, now, true);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Simultaneous(drag, press)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.hints, { bottom: insets.bottom + 14 }]}>
        {hint.map((line, i) => (
          <View key={line} style={styles.hintItem}>
            {i > 0 ? <View style={styles.dot} /> : null}
            <Text style={styles.hint} numberOfLines={1}>
              {line}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#070a1d' },
  hints: {
    position: 'absolute',
    left: 16,
    right: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 10,
  },
  hintItem: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  dot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: '#e8e2c8', opacity: 0.3 },
  hint: { color: '#e8e2c8', opacity: 0.5, fontSize: 12, letterSpacing: 0.2, fontWeight: '500' },
});
