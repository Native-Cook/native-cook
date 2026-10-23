import {
  BlendMode,
  BlurStyle,
  Canvas,
  Picture,
  Skia,
  TileMode,
  VertexMode,
  type SkCanvas,
  type SkColor,
  type SkImage,
  type SkRect,
  type SkRSXform,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { FadeIn, FadeOut, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  buildTree,
  createParticles,
  createWeather,
  easeInOut,
  easeOutBack,
  type Ground,
  groundY,
  LEAF,
  leafFalls,
  layoutTree,
  localSeason,
  MAX_DEPTH,
  type Particles,
  SNOW,
  stepFalling,
  stepWind,
  type Tree,
  type Weather,
} from './fractalTreeSim';

type Props = {
  /** Grows the tree, blooms it and turns it through the seasons, on a loop. */
  autoPlay?: boolean;
  /** The seed the tree grows from: the same seed grows the same tree. */
  seed?: number;
};

const SEASONS = ['Spring', 'Summer', 'Autumn', 'Winter'];

// Growth, in levels of branches per second (the tree has MAX_DEPTH + 1 levels), and the rate the
// tree draws back into its seed when replanted.
const GROW_RATE = 1.9;
const RECEDE_RATE = 6;
// Blossoms open over BLOOM_TIME s once the tree is grown, each a little after the last.
const BLOOM_TIME = 1.3;
// One season to the next, s.
const SEASON_TIME = 1.8;
// Dragging this far across bends the wind fully; past it the drag gives less and less.
const WIND_DRAG = 150;
const WIND_MAX = 1.6;
// The mound: its height and spread (share of the width), above the bottom inset.
const MOUND_HEIGHT = 26;
const MOUND_SPREAD = 0.42;
const GROUND_ABOVE = 64;

const TICK = 1 / 120;
const MAX_TICKS = 4;

// autoPlay, s.
const LOOP = 17.4;
const GROW_AT = 0.4;
const GUST_FROM = 5.8;
const GUST_TO = 7.0;
const SUMMER_AT = 7.4;
const AUTUMN_AT = 9.6;
const WINTER_AT = 12.0;
const REPLANT_AT = 15.0;
const USER_HOLD = 4;

// Atlas cells (64 pt each): two blossoms, a leaf, a petal and a snowflake.
const CELL = 64;
const BLOSSOM_A = 0;
const BLOSSOM_B = 1;
const LEAF_CELL = 2;
const PETAL_CELL = 3;
const SNOW_CELL = 4;

// Colours by season (spring, summer, autumn, winter): sky top and bottom, far hills, ground, bark.
const SKY_TOP = [
  [0.96, 0.84, 0.82],
  [0.72, 0.86, 0.9],
  [0.93, 0.66, 0.47],
  [0.47, 0.55, 0.66],
];
const SKY_LOW = [
  [0.99, 0.94, 0.89],
  [0.97, 0.95, 0.85],
  [0.99, 0.88, 0.74],
  [0.8, 0.85, 0.9],
];
const HILLS = [
  [0.82, 0.86, 0.74],
  [0.6, 0.74, 0.6],
  [0.82, 0.6, 0.42],
  [0.78, 0.82, 0.88],
];
const GROUND = [
  [0.56, 0.7, 0.45],
  [0.38, 0.56, 0.32],
  [0.62, 0.45, 0.27],
  [0.93, 0.95, 0.97],
];
const BARK = [
  [0.3, 0.22, 0.2],
  [0.27, 0.21, 0.17],
  [0.28, 0.19, 0.15],
  [0.22, 0.2, 0.21],
];
// Leaves: summer greens, then autumn gold, orange and crimson.
const GREEN_A = [0.36, 0.6, 0.3];
const GREEN_B = [0.2, 0.46, 0.27];
const GOLD = [0.93, 0.68, 0.18];
const ORANGE = [0.89, 0.42, 0.13];
const CRIMSON = [0.72, 0.16, 0.14];

/** Whether a time `at` was passed going from `before` to `t` on a looping clock. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return (before < at && t >= at) || (t < before && (at > before || at <= t));
}

/** Linear blend of two colour triples. */
function mix3(a: number[], b: number[], t: number, out: number[]) {
  'worklet';
  out[0] = a[0] + (b[0] - a[0]) * t;
  out[1] = a[1] + (b[1] - a[1]) * t;
  out[2] = a[2] + (b[2] - a[2]) * t;
}

/** A colour from a four-season table at season s (0…4, wrapping), eased between seasons. */
function seasonal(table: number[][], s: number, out: number[]) {
  'worklet';
  const w = s - 4 * Math.floor(s / 4);
  const i = Math.floor(w) % 4;
  const f = w - Math.floor(w);
  const e = f * f * (3 - 2 * f);
  mix3(table[i], table[(i + 1) % 4], e, out);
}

function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

/** "rgba(…)" from a triple and an alpha. */
function css(c: number[], a: number) {
  'worklet';
  return `rgba(${Math.round(c[0] * 255)},${Math.round(c[1] * 255)},${Math.round(c[2] * 255)},${a})`;
}

/** Draws the sprite sheet once, on a CPU surface: blossoms, a leaf, a petal and a snowflake. */
function makeAtlas(): SkImage | null {
  const surface = Skia.Surface.Make(CELL * 5, CELL);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  const c = CELL / 2;
  // Blossoms: five rounded petals around a deep centre with stamens.
  const blossom = (cell: number, outer: string, inner: string, heart: string) => {
    const ox = cell * CELL + c;
    const petal = Skia.Paint();
    petal.setAntiAlias(true);
    // Centred on the blossom: in each petal's own frame (moved out 13 and turned) that is (0, 13).
    petal.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 13 }, 30, [Skia.Color(inner), Skia.Color(outer)], [0.15, 1], TileMode.Clamp));
    for (let k = 0; k < 5; k += 1) {
      const a = (k / 5) * Math.PI * 2 - Math.PI / 2;
      const px = ox + Math.cos(a) * 13;
      const py = c + Math.sin(a) * 13;
      canvas.save();
      canvas.translate(px, py);
      canvas.rotate((a * 180) / Math.PI + 90, 0, 0);
      canvas.drawOval(Skia.XYWHRect(-10, -14, 20, 27), petal);
      canvas.restore();
    }
    const centre = Skia.Paint();
    centre.setAntiAlias(true);
    centre.setColor(Skia.Color(heart));
    canvas.drawCircle(ox, c, 5.5, centre);
    const stamen = Skia.Paint();
    stamen.setAntiAlias(true);
    stamen.setColor(Skia.Color('#f6d36b'));
    for (let k = 0; k < 7; k += 1) {
      const a = (k / 7) * Math.PI * 2;
      canvas.drawCircle(ox + Math.cos(a) * 7.5, c + Math.sin(a) * 7.5, 1.6, stamen);
    }
  };
  blossom(BLOSSOM_A, '#fbe6ee', '#ffffff', '#c2486c');
  blossom(BLOSSOM_B, '#f3a9c0', '#fcd9e4', '#9c2f55');
  // The leaf: an almond with a midrib, in greys so it can be tinted.
  {
    const ox = LEAF_CELL * CELL + c;
    const leaf = Skia.PathBuilder.Make();
    leaf.moveTo(ox, c - 28);
    leaf.cubicTo(ox + 17, c - 14, ox + 15, c + 14, ox, c + 28);
    leaf.cubicTo(ox - 15, c + 14, ox - 17, c - 14, ox, c - 28);
    leaf.close();
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    fill.setShader(Skia.Shader.MakeLinearGradient({ x: ox - 16, y: c }, { x: ox + 16, y: c }, [Skia.Color('#ffffff'), Skia.Color('#b8b8b8')], [0, 1], TileMode.Clamp));
    canvas.drawPath(leaf.build(), fill);
    const rib = Skia.Paint();
    rib.setAntiAlias(true);
    rib.setColor(Skia.Color('rgba(80,80,80,0.55)'));
    rib.setStrokeWidth(1.6);
    canvas.drawLine(ox, c - 24, ox, c + 27, rib);
  }
  // A single petal, white, to be tinted.
  {
    const ox = PETAL_CELL * CELL + c;
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    fill.setShader(Skia.Shader.MakeRadialGradient({ x: ox, y: c + 8 }, 22, [Skia.Color('#ffffff'), Skia.Color('#e9e9e9')], [0, 1], TileMode.Clamp));
    canvas.drawOval(Skia.XYWHRect(ox - 13, c - 18, 26, 36), fill);
  }
  // A snowflake: a soft round flake.
  {
    const ox = SNOW_CELL * CELL + c;
    const fill = Skia.Paint();
    fill.setAntiAlias(true);
    fill.setShader(Skia.Shader.MakeRadialGradient({ x: ox, y: c }, 20, [Skia.Color('rgba(255,255,255,1)'), Skia.Color('rgba(255,255,255,0)')], [0.35, 1], TileMode.Clamp));
    canvas.drawCircle(ox, c, 20, fill);
  }
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image;
}

type Scene = {
  tree: Tree;
  weather: Weather;
  particles: Particles;
  ground: Ground;
  /** Mesh for the branches, reused every frame. */
  positions: { x: number; y: number }[];
  colors: SkColor[];
  indices: number[];
  srcs: SkRect[];
  rootX: number;
  rootY: number;
};

/**
 * A fractal tree on a small mound. It grows from a seed, level by level, sways in a wind that
 * reaches its twigs last, and blossoms; each tap turns the season (summer leaves, autumn colour and
 * falling leaves, winter snow), and dragging across bends the wind.
 */
export function FractalTreeBloom({ autoPlay = false, seed = 27 }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [seasonName, setSeasonName] = useState(0);
  const { width, height } = size;
  // The sprite sheet is small and left to the garbage collector: disposing it in an effect cleanup
  // would free it under a fast refresh (which re-runs effects but keeps the memo) while the UI
  // thread still draws with it.
  const atlas = useMemo(() => makeAtlas(), []);

  const scene = useSharedValue<Scene | null>(null);
  const tick = useSharedValue(0);
  const acc = useSharedValue(0);
  // Growth (0 … MAX_DEPTH + 1), whether it is growing (1), drawing back (−1) or still (0), and the bloom.
  const growth = useSharedValue(auto ? 0 : MAX_DEPTH + 1);
  const growing = useSharedValue(0);
  const bloom = useSharedValue(auto ? 0 : 2);
  // The season, and its move from one to the next.
  const season = useSharedValue(0);
  const seasonFrom = useSharedValue(0);
  const seasonTo = useSharedValue(0);
  const seasonT = useSharedValue(1);
  const shownSeason = useSharedValue(0);
  // The wind's drag.
  const dragging = useSharedValue(false);

  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const looping = useSharedValue(auto);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  const turned = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const planted = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // Helpers the gestures and the frame callback call are named worklet functions, not inline
  // arrows, so the React Compiler cannot hoist them out of the worklets.

  /** Moves on to the next season. */
  function nextSeason() {
    'worklet';
    const to = Math.round(seasonTo.get()) + 1;
    seasonFrom.set(season.get());
    seasonTo.set(to);
    seasonT.set(0);
  }

  /** Draws the tree back into its seed (it grows again from there) and turns the year to spring. */
  function replant() {
    'worklet';
    growing.set(-1);
    const to = Math.ceil(seasonTo.get() / 4 - 1e-6) * 4;
    if (to > seasonTo.get()) {
      seasonFrom.set(season.get());
      seasonTo.set(to);
      seasonT.set(0);
    }
  }

  /** One step of the autoPlay script, from `before` to `t` s into the loop. */
  function script(before: number, t: number) {
    'worklet';
    const s = scene.get();
    if (!s) return;
    if (crossed(before, t, GROW_AT)) {
      growth.set(0);
      growing.set(1);
      bloom.set(0);
    }
    if (t >= GUST_FROM && t < GUST_TO) {
      // A finger draws the wind to the right, then lets go.
      const k = (t - GUST_FROM) / (GUST_TO - GUST_FROM);
      s.weather.target = 0.95 * Math.sin(Math.min(1, k * 1.6) * Math.PI * 0.5);
    }
    if (crossed(before, t, GUST_TO)) s.weather.target = 0;
    if (crossed(before, t, SUMMER_AT) || crossed(before, t, AUTUMN_AT) || crossed(before, t, WINTER_AT)) nextSeason();
    if (crossed(before, t, REPLANT_AT)) replant();
  }

  useFrameCallback((frame) => {
    if (width === 0) return;
    let s = scene.get();
    if (s === null) {
      const tree = buildTree(seed);
      const base = height - insets.bottom - GROUND_ABOVE;
      const ground: Ground = { base, cx: width / 2, bump: MOUND_HEIGHT, spread: width * MOUND_SPREAD };
      const positions: { x: number; y: number }[] = [];
      const colors: SkColor[] = [];
      const indices: number[] = [];
      for (let i = 0; i < tree.count; i += 1) {
        for (let k = 0; k < 4; k += 1) {
          positions.push({ x: 0, y: 0 });
          colors.push(new Float32Array(4));
        }
        const v = i * 4;
        indices.push(v, v + 1, v + 2, v + 1, v + 3, v + 2);
      }
      const srcs: SkRect[] = [];
      for (let k = 0; k < 5; k += 1) srcs.push(Skia.XYWHRect(k * CELL, 0, CELL, CELL));
      s = {
        tree,
        weather: createWeather(seed * 7 + 1),
        particles: createParticles(),
        ground,
        positions,
        colors,
        indices,
        srcs,
        rootX: width / 2,
        rootY: groundY(ground, width / 2) + 2,
      };
      scene.set(s);
    }
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      pending -= TICK;
      ticks += 1;
      if (hold.get() > 0) {
        if (!dragging.get()) hold.set(hold.get() - TICK);
        if (hold.get() <= 0) {
          // Pick the loop up where the tree is: still growing from a replant, at the same point of
          // the growth; otherwise from the replant, drawing it back to grow it again.
          if (growing.get() > 0) clock.set(GROW_AT + 0.01 + growth.get() / GROW_RATE);
          else clock.set(REPLANT_AT - 0.01);
        }
      } else if (looping.get()) {
        const before = clock.get();
        const t = (before + TICK) % LOOP;
        clock.set(t);
        script(before, t);
      }
      // Growth, level by level, and the bloom once it is grown.
      const g = growth.get();
      if (growing.get() > 0) {
        const next = reducedMotion ? MAX_DEPTH + 1 : Math.min(MAX_DEPTH + 1, g + GROW_RATE * TICK);
        growth.set(next);
        if (next >= MAX_DEPTH + 1) growing.set(0);
      } else if (growing.get() < 0) {
        const next = reducedMotion ? 0 : Math.max(0, g - RECEDE_RATE * TICK);
        growth.set(next);
        if (next <= 0) {
          // Back in the seed: it rests a moment, then grows again (unless the script grows it).
          growing.set(looping.get() && hold.get() <= 0 ? 0 : 2);
          s.particles.count = 0;
          bloom.set(0);
        }
      } else if (growing.get() === 2) {
        growing.set(1);
      }
      if (growth.get() >= MAX_DEPTH + 0.6 && growing.get() >= 0) bloom.set(Math.min(2, bloom.get() + TICK / BLOOM_TIME));
      // The season's move.
      if (seasonT.get() < 1) {
        const k = Math.min(1, seasonT.get() + TICK / (reducedMotion ? 0.6 : SEASON_TIME));
        seasonT.set(k);
        season.set(seasonFrom.get() + (seasonTo.get() - seasonFrom.get()) * easeInOut(k));
        if (k >= 1 && seasonTo.get() >= 4) {
          // Whole years change nothing: keep the numbers small.
          const years = Math.floor(seasonTo.get() / 4) * 4;
          seasonTo.set(seasonTo.get() - years);
          seasonFrom.set(seasonFrom.get() - years);
          season.set(season.get() - years);
          for (let i = 0; i < s.tree.sites; i += 1) s.tree.siteSeen[i] = -1;
        }
      }
      stepWind(s.weather, TICK);
      layoutTree(s.tree, s.weather, s.rootX, s.rootY, growth.get(), reducedMotion ? 0 : 1);
      stepFalling(s.tree, s.particles, s.weather, season.get(), TICK, width, s.ground, !reducedMotion && growing.get() >= 0);
    }
    acc.set(Math.min(pending, TICK));
    // Always a valid index into SEASONS, whatever the season number does.
    const name = (((Math.round(season.get()) % 4) + 4) % 4) | 0;
    if (name !== shownSeason.get()) {
      shownSeason.set(name);
      scheduleOnRN(setSeasonName, name);
    }
    tick.set(tick.get() + 1);
  });

  // ─── Gestures ──────────────────────────────────────────────────────────────────────────────────

  function touched() {
    'worklet';
    hold.set(USER_HOLD);
  }

  const wind = Gesture.Pan()
    // One finger bends the wind; a second would jump the pan's averaged position.
    .maxPointers(1)
    .activeOffsetX([-10, 10])
    .onStart(() => {
      touched();
      dragging.set(true);
    })
    .onUpdate((e) => {
      const s = scene.get();
      if (!s) return;
      // Rubber-banded: the further the drag, the less each point adds.
      const raw = e.translationX / WIND_DRAG;
      const sign = raw < 0 ? -1 : 1;
      const a = Math.abs(raw);
      s.weather.target = sign * (a < 1 ? a : 1 + (WIND_MAX - 1) * (1 - Math.exp(-(a - 1))));
    })
    .onEnd((e) => {
      const s = scene.get();
      if (!s) return;
      // Let go: the wind springs back, carrying the finger's speed.
      s.weather.target = 0;
      s.weather.windSpeed += (e.velocityX / WIND_DRAG) * 0.35;
    })
    .onFinalize(() => {
      dragging.set(false);
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((_e, success) => {
      if (!success) return;
      touched();
      nextSeason();
      scheduleOnRN(turned);
    });

  const hold2 = Gesture.LongPress()
    .minDuration(500)
    .onStart(() => {
      touched();
      replant();
      scheduleOnRN(planted);
    });

  // ─── Drawing ───────────────────────────────────────────────────────────────────────────────────

  /** Sky, sun, far hills, the mound and the tree's shade on it. */
  function drawBackdrop(canvas: SkCanvas, s: Scene, sea: number, c1: number[], c2: number[]) {
    'worklet';
    seasonal(SKY_TOP, sea, c1);
    seasonal(SKY_LOW, sea, c2);
    const sky = Skia.Paint();
    sky.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: s.ground.base }, [Skia.Color(css(c1, 1)), Skia.Color(css(c2, 1))], [0, 1], TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), sky);
    // A low, soft sun behind the crown.
    const sun = Skia.Paint();
    sun.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: width * 0.72, y: height * 0.3 },
        width * 0.55,
        [Skia.Color(css(c2, 0.85)), Skia.Color(css(c2, 0))],
        [0, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), sun);
    // Two far hills, paler with distance.
    seasonal(HILLS, sea, c1);
    for (let layer = 0; layer < 2; layer += 1) {
      const top = s.ground.base - (layer === 0 ? 120 : 70);
      const hill = Skia.PathBuilder.Make();
      hill.moveTo(0, height);
      for (let k = 0; k <= 24; k += 1) {
        const x = (k / 24) * width;
        const y = top - (layer === 0 ? 38 : 26) * Math.sin((k / 24) * Math.PI * (layer === 0 ? 1.3 : 2.1) + (layer === 0 ? 0.6 : 2.4));
        hill.lineTo(x, y);
      }
      hill.lineTo(width, height);
      hill.close();
      const paint = Skia.Paint();
      paint.setColor(Skia.Color(css(c1, layer === 0 ? 0.42 : 0.7)));
      canvas.drawPath(hill.build(), paint);
    }
    // The mound, with a lit crest.
    seasonal(GROUND, sea, c1);
    const ground = Skia.PathBuilder.Make();
    ground.moveTo(0, height);
    for (let k = 0; k <= 40; k += 1) {
      const x = (k / 40) * width;
      ground.lineTo(x, groundY(s.ground, x));
    }
    ground.lineTo(width, height);
    ground.close();
    const fill = Skia.Paint();
    fill.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: s.ground.base - s.ground.bump },
        { x: 0, y: height },
        [Skia.Color(css(c1, 1)), Skia.Color(css([c1[0] * 0.72, c1[1] * 0.72, c1[2] * 0.74], 1))],
        [0, 1],
        TileMode.Clamp,
      ),
    );
    const groundPath = ground.build();
    canvas.drawPath(groundPath, fill);
    // The crown's shade on the ground, as wide as the crown and as dark as its foliage.
    const g = Math.min(1, growth.get() / (MAX_DEPTH + 1));
    if (g > 0.05) {
      const shade = Skia.Paint();
      shade.setColor(Skia.Color(`rgba(30,24,20,${0.22 * g})`));
      shade.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
      const r = width * 0.36 * g;
      canvas.drawOval(Skia.XYWHRect(s.rootX - r + 14, s.rootY - 9, r * 2, 22), shade);
    }
  }

  /** The branches: one mesh of tapered quads, lit on one side, with snow along their tops in winter. */
  function drawBranches(canvas: SkCanvas, s: Scene, sea: number) {
    'worklet';
    const tree = s.tree;
    if (growth.get() <= 0.001) return;
    const bark = [0, 0, 0];
    seasonal(BARK, sea, bark);
    const w = sea - 4 * Math.floor(sea / 4);
    const snow = smooth(2.75, 3.2, w) * (1 - smooth(3.6, 4, w));
    const { positions, colors } = s;
    for (let i = 0; i < tree.count; i += 1) {
      const v = i * 4;
      const bx = tree.bx[i];
      const by = tree.by[i];
      let dx = tree.tx[i] - bx;
      let dy = tree.ty[i] - by;
      const len = Math.sqrt(dx * dx + dy * dy);
      if (len < 0.2) {
        for (let k = 0; k < 4; k += 1) {
          positions[v + k].x = bx;
          positions[v + k].y = by;
        }
        continue;
      }
      dx /= len;
      dy /= len;
      // Normal to the left of the branch.
      const nx = dy;
      const ny = -dx;
      const h0 = tree.w0[i] / 2;
      const h1 = tree.w1[i] / 2;
      // Reach back into the parent a little so joints close up.
      const back = tree.depth[i] === 0 ? 0 : h0 * 0.9;
      positions[v].x = bx - dx * back + nx * h0;
      positions[v].y = by - dy * back + ny * h0;
      positions[v + 1].x = bx - dx * back - nx * h0;
      positions[v + 1].y = by - dy * back - ny * h0;
      positions[v + 2].x = tree.tx[i] + nx * h1;
      positions[v + 2].y = tree.ty[i] + ny * h1;
      positions[v + 3].x = tree.tx[i] - nx * h1;
      positions[v + 3].y = tree.ty[i] - ny * h1;
      // Light from the upper left: the side facing it is lighter. Snow lies on the side facing up.
      for (let side = 0; side < 2; side += 1) {
        const sx = side === 0 ? nx : -nx;
        const sy = side === 0 ? ny : -ny;
        const lit = 0.78 + 0.42 * Math.max(0, -0.62 * sx - 0.78 * sy);
        const up = Math.max(0, -sy) * snow * (tree.depth[i] < MAX_DEPTH ? 1 : 0.5);
        const r = bark[0] * lit * (1 - up) + 0.97 * up;
        const g = bark[1] * lit * (1 - up) + 0.98 * up;
        const b = bark[2] * lit * (1 - up) + 1 * up;
        for (let k = 0; k < 2; k += 1) {
          const col = colors[v + side + k * 2];
          col[0] = r;
          col[1] = g;
          col[2] = b;
          col[3] = 1;
        }
      }
    }
    const mesh = Skia.MakeVertices(VertexMode.Triangles, positions, null, colors, s.indices);
    canvas.drawVertices(mesh, BlendMode.Dst, Skia.Paint());
  }

  /** Blossoms and leaves at their sites, each reading the season a little late. */
  function drawFoliage(canvas: SkCanvas, s: Scene, sea: number) {
    'worklet';
    if (!atlas || growth.get() < MAX_DEPTH - 1) return;
    const tree = s.tree;
    const dsts: SkRSXform[] = [];
    const srcs: SkRect[] = [];
    const cols: SkColor[] = [];
    const leaf = [0, 0, 0];
    const autumn = [0, 0, 0];
    const bl = bloom.get();
    // Drawing back into the seed, the foliage goes first.
    const keep = Math.min(1, (growth.get() - (MAX_DEPTH - 1)) / 1.5);
    for (let i = 0; i < tree.sites; i += 1) {
      const b = tree.siteBranch[i];
      if (tree.shown[b] < 0.6) continue;
      const lag = tree.siteLag[i];
      const local = localSeason(sea, lag);
      const x = tree.bx[b] + (tree.tx[b] - tree.bx[b]) * tree.siteAt[i];
      const y = tree.by[b] + (tree.ty[b] - tree.by[b]) * tree.siteAt[i];
      const sway = tree.abs[b];
      // Blossoms open after the tree is grown, each a little after the last, and give way to leaves.
      const open = easeOutBack((bl - lag * 1.6) / 0.45) * keep;
      const blossom = open * (1 - smooth(0.45, 0.95, local) + smooth(3.4, 3.95, local));
      if (blossom > 0.02) {
        const k = tree.siteSize[i] * 0.33 * blossom;
        const a = tree.siteSpin[i] + sway * 0.5;
        const cs = Math.cos(a) * k;
        const sn = Math.sin(a) * k;
        dsts.push(Skia.RSXform(cs, sn, x - (cs * 32 - sn * 32), y - (sn * 32 + cs * 32)));
        srcs.push(s.srcs[tree.siteKind[i] === 0 ? BLOSSOM_A : BLOSSOM_B]);
        cols.push(Float32Array.of(1, 1, 1, Math.min(1, blossom * 1.5)));
      }
      // Leaves: in with summer, coloured in autumn, gone at their moment.
      const fall = tree.siteFall[i];
      const wither = reducedMotion || !leafFalls(tree, i) ? 0.22 : 0.03;
      const on = smooth(0.4, 0.95, local) * (1 - smooth(fall - wither, fall, local)) * open;
      if (on > 0.02 && local < 3.5) {
        const green = tree.siteHue[i] < 0.5 ? 0 : 1;
        mix3(GREEN_A, GREEN_B, green * 0.8 + tree.siteHue[i] * 0.2, leaf);
        const hue = tree.siteHue[i];
        if (hue < 0.34) mix3(GOLD, ORANGE, hue / 0.34 * 0.5, autumn);
        else if (hue < 0.7) mix3(ORANGE, GOLD, (hue - 0.34) * 0.6, autumn);
        else mix3(CRIMSON, ORANGE, (hue - 0.7) * 1.2, autumn);
        mix3(leaf, autumn, smooth(1.4, 1.95, local), leaf);
        const k = tree.siteSize[i] * 0.28 * on;
        const side = tree.siteSide[i] < 0 ? -1 : 1;
        const a = sway + side * 0.95 + tree.siteSpin[i] * 0.15;
        const cs = Math.cos(a) * k;
        const sn = Math.sin(a) * k;
        // The leaf hangs from its stalk end (the sprite's bottom centre) at the site.
        dsts.push(Skia.RSXform(cs, sn, x - (cs * 32 - sn * 60), y - (sn * 32 + cs * 60)));
        srcs.push(s.srcs[LEAF_CELL]);
        cols.push(Float32Array.of(leaf[0], leaf[1], leaf[2], Math.min(1, on * 1.4)));
      }
    }
    if (dsts.length === 0) return;
    canvas.drawAtlas(atlas, srcs, dsts, Skia.Paint(), BlendMode.Modulate, cols);
  }

  /** Falling petals, leaves and snow. */
  function drawParticles(canvas: SkCanvas, s: Scene) {
    'worklet';
    const ps = s.particles;
    if (!atlas || ps.count === 0) return;
    const dsts: SkRSXform[] = [];
    const srcs: SkRect[] = [];
    const cols: SkColor[] = [];
    const autumn = [0, 0, 0];
    // Drawing back into the seed, whatever is still falling fades away with the tree.
    const away = growing.get() < 0 ? Math.min(1, growth.get() / (MAX_DEPTH + 1)) : 1;
    // Each kind belongs to its season: petals fade out with the summer, leaves by spring, snow by spring.
    const sea = season.get() - 4 * Math.floor(season.get() / 4);
    const petals = 1 - smooth(1.5, 1.95, sea);
    const leaves = smooth(1.2, 1.6, sea) * (1 - smooth(3.5, 3.95, sea));
    const flakes = smooth(2.6, 2.9, sea) * (1 - smooth(3.7, 3.98, sea));
    for (let i = 0; i < ps.count; i += 1) {
      const kind = ps.kind[i];
      const restFade = away * (kind === SNOW ? flakes : kind === LEAF ? leaves : petals) * (ps.rest[i] < 0 ? 1 : Math.max(0, 1 - ps.rest[i] / (kind === SNOW ? 1.2 : 2)));
      const k = ps.size[i] * (kind === SNOW ? 0.17 : kind === LEAF ? 0.26 : 0.22);
      // A tumbling flake turns in depth too: its width breathes with the turn.
      const tumble = kind === SNOW ? 1 : 0.55 + 0.45 * Math.abs(Math.cos(ps.rot[i] * 1.7));
      const a = ps.rot[i];
      const cs = Math.cos(a) * k;
      const sn = Math.sin(a) * k;
      dsts.push(Skia.RSXform(cs * tumble, sn * tumble, ps.x[i] - (cs * tumble * 32 - sn * 32), ps.y[i] - (sn * tumble * 32 + cs * 32)));
      if (kind === SNOW) {
        srcs.push(s.srcs[SNOW_CELL]);
        cols.push(Float32Array.of(1, 1, 1, restFade));
      } else if (kind === LEAF) {
        const hue = ps.hue[i];
        if (hue < 0.34) mix3(GOLD, ORANGE, hue / 0.34 * 0.5, autumn);
        else if (hue < 0.7) mix3(ORANGE, GOLD, (hue - 0.34) * 0.6, autumn);
        else mix3(CRIMSON, ORANGE, (hue - 0.7) * 1.2, autumn);
        srcs.push(s.srcs[LEAF_CELL]);
        cols.push(Float32Array.of(autumn[0], autumn[1], autumn[2], restFade));
      } else {
        srcs.push(s.srcs[PETAL_CELL]);
        cols.push(Float32Array.of(0.98, 0.8 + 0.12 * ps.hue[i], 0.86, restFade));
      }
    }
    canvas.drawAtlas(atlas, srcs, dsts, Skia.Paint(), BlendMode.Modulate, cols);
  }

  /** The seed on the mound, until the sprout has taken over. */
  function drawSeed(canvas: SkCanvas, s: Scene) {
    'worklet';
    const g = growth.get();
    if (g > 0.6) return;
    const alpha = 1 - g / 0.6;
    canvas.save();
    canvas.translate(s.rootX, s.rootY - 5);
    canvas.rotate(-18, 0, 0);
    const shadow = Skia.Paint();
    shadow.setColor(Skia.Color(`rgba(40,28,20,${0.3 * alpha})`));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
    canvas.drawOval(Skia.XYWHRect(-9, 1, 20, 8), shadow);
    const body = Skia.Paint();
    body.setShader(
      Skia.Shader.MakeRadialGradient({ x: -3, y: -3 }, 12, [Skia.Color('#b7835a'), Skia.Color('#6b4329'), Skia.Color('#3d2414')], [0, 0.6, 1], TileMode.Clamp),
    );
    body.setAlphaf(alpha);
    canvas.drawOval(Skia.XYWHRect(-10, -6, 20, 12), body);
    const shine = Skia.Paint();
    shine.setColor(Skia.Color(`rgba(255,240,220,${0.55 * alpha})`));
    canvas.drawOval(Skia.XYWHRect(-6, -4.5, 7, 3), shine);
    canvas.restore();
  }

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = scene.get();
    if (!s || width === 0) return recorder.finishRecordingAsPicture();
    const sea = season.get();
    const c1 = [0, 0, 0];
    const c2 = [0, 0, 0];
    drawBackdrop(canvas, s, sea, c1, c2);
    drawBranches(canvas, s, sea);
    drawFoliage(canvas, s, sea);
    drawParticles(canvas, s);
    drawSeed(canvas, s);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Race(wind, hold2, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 22 }]}>
        <Text style={styles.kicker}>{`Seed No. ${seed}`}</Text>
        <Animated.Text key={seasonName} entering={FadeIn.duration(380).delay(160)} exiting={FadeOut.duration(150)} style={styles.title}>
          {SEASONS[seasonName] ?? SEASONS[0]}
        </Animated.Text>
      </View>
      <View pointerEvents="none" style={[styles.footer, { bottom: insets.bottom + 18 }]}>
        <Text style={styles.hint}>Drag to bend the wind · tap for the next season</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#f6e6df', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, height: 64 },
  kicker: { color: 'rgba(52,38,34,0.62)', fontSize: 11, fontWeight: '700', letterSpacing: 1.6, textTransform: 'uppercase' },
  title: { position: 'absolute', top: 18, left: 0, color: '#33241f', fontSize: 30, fontWeight: '600', letterSpacing: -0.3 },
  footer: { position: 'absolute', left: 16, right: 16, alignItems: 'center' },
  hint: { color: 'rgba(40,30,26,0.7)', fontSize: 13, fontWeight: '500', letterSpacing: 0.2 },
});
