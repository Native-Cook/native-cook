import { BlurMask, Canvas, Path, Picture, Skia, TextBlob, matchFont, type SkFont, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useMemo } from 'react';
import { Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PALETTE, makeBackground, makeLeaf, makeStreaks, ridgeY } from './windScatterArt';
import {
  FLYING_LEAVES,
  GROUND_LEAVES,
  type Home,
  LOOP,
  type Sim,
  type Wind,
  autoOffset,
  createSim,
  depthOf,
  easeInOutCubic,
  flyingLeaf,
  gustAt,
  hash,
  seg,
  simEnergy,
  stepSim,
} from './windScatterMotion';

/*
 Wind Scatter Letters — the hero of an autumn weather edition: the headline "Let it drift" blows
 apart like leaves in a gust and settles back. Each glyph is measured at runtime and drawn from
 one RSXform text blob (position, rotation and scale per glyph); every third glyph sits further
 back, smaller and softened. Leaves drawn in code lie on the ridge and cross the sky with the gust.
 A swipe is a gust in that direction at that speed: glyphs and leaves near the finger scatter
 most, and soft springs bring them home.

 One Canvas: the recorded sky, wind streaks, four text blobs (two motion trails, far, near) and
 the leaves in three colour batches, all rebuilt on the UI thread per frame. Copy and controls
 are views. The script is in windScatterMotion.ts.
*/

type Props = {
  /** Plays the gust: build, scatter, return, every LOOP seconds. */
  autoPlay?: boolean;
};

const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const LINES = ['Let it', 'drift'];
const MAX_SIZE = 96;
const IDLE = 2;
const TRAIL_LAG = [0.045, 0.09];

type Layout = {
  font: SkFont;
  ids: number[];
  widths: number[];
  /** Pivot of each glyph relative to its origin (centre of the glyph box). */
  px: number[];
  py: number[];
  homes: Home[];
  glyphs: number;
  size: number;
  b1: number;
  b2: number;
};

function makeLayout(W: number, H: number, insetTop: number): Layout {
  let size = MAX_SIZE;
  let font = matchFont({ fontFamily: SERIF, fontSize: size, fontWeight: 'bold', fontStyle: 'italic' });
  const measure = (f: SkFont, s: string) => f.getGlyphWidths(f.getGlyphIDs(s)).reduce((a, b) => a + b, 0);
  const widest = Math.max(measure(font, LINES[0]), measure(font, LINES[1]));
  if (widest > W - 48) {
    size = Math.floor((size * (W - 48)) / widest);
    font = matchFont({ fontFamily: SERIF, fontSize: size, fontWeight: 'bold', fontStyle: 'italic' });
  }
  const b1 = insetTop + 92 + size * 0.95;
  const b2 = b1 + size * 0.98;
  const ids: number[] = [];
  const widths: number[] = [];
  const px: number[] = [];
  const py: number[] = [];
  const homes: Home[] = [];
  LINES.forEach((line, li) => {
    const lineIds = font.getGlyphIDs(line);
    const lineW = font.getGlyphWidths(lineIds);
    let x = 24;
    for (let k = 0; k < line.length; k += 1) {
      if (line[k] !== ' ') {
        ids.push(lineIds[k]);
        widths.push(lineW[k]);
        px.push(lineW[k] / 2);
        py.push(-size * 0.34);
        homes.push({ x: x + lineW[k] / 2, y: (li === 0 ? b1 : b2) - size * 0.34, reach: 1 });
      }
      x += lineW[k];
    }
  });
  const glyphs = ids.length;
  for (let j = 0; j < GROUND_LEAVES; j += 1) {
    const x = W * [0.07, 0.2, 0.35, 0.6, 0.79, 0.93][j];
    homes.push({ x, y: ridgeY(x, W, H) - 3, reach: 0.5 });
  }
  return { font, ids, widths, px, py, homes, glyphs, size, b1, b2 };
}

/** The four blobs' transforms: [trail far, trail near, far, near] → RSXform lists over glyph ids. */
function blobFor(L: Layout, poses: number[][], pick: number) {
  'worklet';
  const ids: number[] = [];
  const xf = [];
  for (let i = 0; i < L.glyphs; i += 1) {
    const far = depthOf(i) < 0;
    if (pick === 0 && !far) continue;
    if (pick === 1 && far) continue;
    const p = poses[i];
    const h = L.homes[i];
    const c = Math.cos(p[2]) * p[3];
    const s = Math.sin(p[2]) * p[3];
    const X = h.x + p[0];
    const Y = h.y + p[1];
    xf.push(Skia.RSXform(c, s, X - c * L.px[i] + s * L.py[i], Y - s * L.px[i] - c * L.py[i]));
    ids.push(L.ids[i]);
  }
  if (ids.length === 0) return Skia.TextBlob.MakeFromRSXformGlyphs([L.ids[0]], [Skia.RSXform(0, 0, -999, -999)], L.font);
  return Skia.TextBlob.MakeFromRSXformGlyphs(ids, xf, L.font);
}

function leafBatch(blade: SkPath, rib: SkPath, items: number[][]) {
  'worklet';
  const colors = [Skia.PathBuilder.Make(), Skia.PathBuilder.Make(), Skia.PathBuilder.Make()];
  const ribs = Skia.PathBuilder.Make();
  for (let j = 0; j < items.length; j += 1) {
    const it = items[j];
    if (it[0] < -100) continue;
    const m = Skia.Matrix();
    m.translate(it[0], it[1]);
    m.rotate(it[2]);
    m.scale(it[3] * it[4], it[4]);
    colors[j % 3].addPath(blade, m);
    ribs.addPath(rib, m);
  }
  return [colors[0].build(), colors[1].build(), colors[2].build(), ribs.build()];
}

function gustTick() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function WindScatterLetters({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const L = useMemo(() => makeLayout(W, H, insets.top), [W, H, insets.top]);
  const background = useMemo(() => makeBackground(W, H), [W, H]);
  const leaf = useMemo(() => makeLeaf(), []);
  const streaks = useMemo(() => makeStreaks(W, H), [W, H]);
  const bodies = L.homes.length;

  const clock = useSharedValue(0);
  const mode = useSharedValue(0);
  const idleFor = useSharedValue(0);
  const sim = useSharedValue<Sim | null>(null);
  const wind = useSharedValue<Wind>({ fx: 0, fy: 0, fvx: 0, fvy: 0, on: 0 });
  const frame = useSharedValue(0);
  const ticked = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min(f.timeSincePreviousFrame ?? 16, 50) / 1000;
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    const s = sim.get();
    if (!s) return;
    const w = wind.get();
    if (w.on === 0) idleFor.set(idleFor.get() + dt);
    const settle = idleFor.get() > IDLE ? 1 : 0;
    const steps = dt > 1 / 90 ? 2 : 1;
    for (let k = 0; k < steps; k += 1) stepSim(s, L.homes, w, dt / steps, settle);
    sim.set(s);
    frame.set(frame.get() + 1);
    if (settle && simEnergy(s) < 0.4) {
      clock.set(0);
      mode.set(0);
    }
  });

  /** Every body's [dx, dy, rotation, scale] now, and at two earlier moments for the trails. */
  const poses = useDerivedValue(() => {
    frame.get();
    const m = mode.get();
    const out: number[][][] = [[], [], []];
    if (m === 0) {
      const t = clock.get();
      const times = [t, t - TRAIL_LAG[0], t - TRAIL_LAG[1]];
      for (let k = 0; k < 3; k += 1) {
        for (let i = 0; i < bodies; i += 1) out[k].push(auto ? autoOffset(Math.max(0, times[k]), i, L.homes[i], W, H) : [0, 0, 0, 1]);
      }
      return out;
    }
    const s = sim.get();
    for (let i = 0; i < bodies; i += 1) {
      const sc = (x: number, y: number) => 1 + depthOf(i) * Math.min(1, Math.hypot(x, y) / 220);
      out[0].push(s ? [s.x[i], s.y[i], s.r[i], sc(s.x[i], s.y[i])] : [0, 0, 0, 1]);
      out[1].push(s ? [s.px[0][i], s.py[0][i], s.r[i], sc(s.px[0][i], s.py[0][i])] : [0, 0, 0, 1]);
      out[2].push(s ? [s.px[1][i], s.py[1][i], s.r[i], sc(s.px[1][i], s.py[1][i])] : [0, 0, 0, 1]);
    }
    return out;
  });

  const nearBlob = useDerivedValue(() => blobFor(L, poses.get()[0], 1));
  const farBlob = useDerivedValue(() => blobFor(L, poses.get()[0], 0));
  const trail1 = useDerivedValue(() => blobFor(L, poses.get()[1], 2));
  const trail2 = useDerivedValue(() => blobFor(L, poses.get()[2], 2));

  /** How much the air is moving now (0…1): streaks, trails and the far glyphs' softness follow it. */
  const air = useDerivedValue(() => {
    if (mode.get() === 0) return auto ? gustAt(clock.get()) : 0;
    const s = sim.get();
    if (!s) return 0;
    let v = 0;
    for (let i = 0; i < L.glyphs; i += 1) v = Math.max(v, Math.hypot(s.vx[i], s.vy[i]));
    return Math.min(1, v / 600);
  });
  const trailOpacity1 = useDerivedValue(() => 0.2 * air.get());
  const trailOpacity2 = useDerivedValue(() => 0.09 * air.get());
  const farBlur = useDerivedValue(() => 0.3 + 2.2 * air.get());

  const leaves = useDerivedValue(() => {
    const p = poses.get()[0];
    const t = clock.get();
    const items: number[][] = [];
    for (let j = 0; j < GROUND_LEAVES; j += 1) {
      const i = L.glyphs + j;
      const h = L.homes[i];
      const rest = (hash(j * 2.3 + 40) - 0.5) * 1.2 + (j % 2 ? Math.PI : 0);
      items.push([h.x + p[i][0], h.y + p[i][1], rest + p[i][2] * 1.6, Math.cos(p[i][2] * 2.2), 0.9 + 0.3 * hash(j + 50)]);
    }
    for (let j = 0; j < FLYING_LEAVES; j += 1) items.push(mode.get() === 0 && auto ? flyingLeaf(t, j, W, H) : [-200, -200, 0, 1, 1]);
    return leafBatch(leaf.blade, leaf.rib, items);
  });
  const leaf0 = useDerivedValue(() => leaves.get()[0]);
  const leaf1 = useDerivedValue(() => leaves.get()[1]);
  const leaf2 = useDerivedValue(() => leaves.get()[2]);
  const ribs = useDerivedValue(() => leaves.get()[3]);

  const streakOpacity = useDerivedValue(() => 0.32 * air.get());

  // ---- Touch: a swipe is a gust ----------------------------------------------------------------
  function takeOver() {
    'worklet';
    if (mode.get() === 0) {
      const s = createSim(bodies);
      const t = clock.get();
      for (let i = 0; i < bodies; i += 1) {
        const o = auto ? autoOffset(t, i, L.homes[i], W, H) : [0, 0, 0, 1];
        s.x[i] = o[0];
        s.y[i] = o[1];
        s.r[i] = o[2];
        s.px[0][i] = o[0];
        s.py[0][i] = o[1];
        s.px[1][i] = o[0];
        s.py[1][i] = o[1];
      }
      sim.set(s);
      mode.set(1);
    }
    idleFor.set(0);
  }

  const gust = Gesture.Pan()
    .minDistance(4)
    .onBegin((e) => {
      takeOver();
      wind.set({ fx: e.x, fy: e.y, fvx: 0, fvy: 0, on: 1 });
      ticked.set(0);
    })
    .onUpdate((e) => {
      wind.set({ fx: e.x, fy: e.y, fvx: e.velocityX, fvy: e.velocityY, on: 1 });
      idleFor.set(0);
      if (ticked.get() === 0 && Math.hypot(e.velocityX, e.velocityY) > 1400) {
        ticked.set(1);
        scheduleOnRN(gustTick);
      }
    })
    .onFinalize(() => {
      wind.set({ fx: 0, fy: 0, fvx: 0, fvy: 0, on: 0 });
      idleFor.set(0);
    });

  return (
    <View style={[styles.root, { backgroundColor: PALETTE.sky[1] }]}>
      <GestureDetector gesture={gust}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityRole="header" accessibilityLabel="Let it drift. Swipe across the headline to blow the letters away.">
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={background} />
            {streaks.map((p, i) => (
              <Streak key={i} path={p} index={i} clock={clock} mode={mode} auto={auto} opacity={streakOpacity} />
            ))}
            <TextBlob blob={trail2} x={0} y={0} color={PALETTE.cream} opacity={trailOpacity2} />
            <TextBlob blob={trail1} x={0} y={0} color={PALETTE.cream} opacity={trailOpacity1} />
            <TextBlob blob={farBlob} x={0} y={0} color="rgba(236,214,190,0.82)">
              <BlurMask blur={farBlur} style="normal" />
            </TextBlob>
            <TextBlob blob={nearBlob} x={0} y={0} color={PALETTE.cream} />
            <Path path={leaf0} color={PALETTE.leaves[0]} />
            <Path path={leaf1} color={PALETTE.leaves[1]} />
            <Path path={leaf2} color={PALETTE.leaves[2]} />
            <Path path={ribs} style="stroke" strokeWidth={0.8} color="rgba(60,24,12,0.55)" />
          </Canvas>
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.masthead, { top: insets.top + 24 }]}>
        <Text style={styles.kicker}>AUTUMN EDITION</Text>
        <Text style={styles.issue}>No. 14 · Sat 25 Oct</Text>
      </View>
      <View pointerEvents="none" style={[styles.deck, { top: L.b2 + 34 }]}>
        <Text style={styles.deckText}>Gusts up to 48 km/h from the west. Leaves down by noon, clear skies by four.</Text>
        <View style={styles.stats}>
          <Stat label="Wind" value="32" unit="km/h W" />
          <Stat label="Gusts" value="48" unit="km/h" />
          <Stat label="Feels" value="9°" unit="at 15:00" />
        </View>
        {L.b2 + 34 + 292 <= H * 0.78 ? <Hourly /> : null}
      </View>
      <View style={[styles.ctaWrap, { bottom: insets.bottom + 20 }]}>
        <View style={styles.cta}>
          <LinearGradient colors={['#f7ead6', '#efd8b8', '#e2c196']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
          <SymbolView name={{ ios: 'wind', android: 'air', web: 'air' }} size={17} tintColor="#3a2418" />
          <Text style={styles.ctaText}>Read the forecast</Text>
        </View>
      </View>
    </View>
  );
}

const HOURS = [
  { at: '12:00', kmh: 24 },
  { at: '13:00', kmh: 31 },
  { at: '14:00', kmh: 40 },
  { at: '15:00', kmh: 48 },
  { at: '16:00', kmh: 37 },
  { at: '17:00', kmh: 22 },
];

/** Gusts over the afternoon: one bar per hour, the peak in ember. */
function Hourly() {
  return (
    <View style={styles.hourly}>
      <Text style={styles.statLabel}>GUSTS THIS AFTERNOON</Text>
      <View style={styles.hours}>
        {HOURS.map((h) => (
          <View key={h.at} style={styles.hour}>
            <Text style={styles.hourValue}>{h.kmh}</Text>
            <View style={[styles.hourBar, { height: 6 + (h.kmh / 48) * 26, backgroundColor: h.kmh === 48 ? PALETTE.ember : 'rgba(244,231,212,0.32)' }]} />
            <Text style={styles.hourAt}>{h.at}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function Stat({ label, value, unit }: { label: string; value: string; unit: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.statLabel}>{label.toUpperCase()}</Text>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statUnit}>{unit}</Text>
    </View>
  );
}

type StreakProps = {
  path: SkPath;
  index: number;
  clock: SharedValue<number>;
  mode: SharedValue<number>;
  auto: boolean;
  opacity: SharedValue<number>;
};

/** One wind streak: a short trimmed segment that blows across with the gust, twice. */
function Streak({ path, index, clock, mode, auto, opacity }: StreakProps) {
  // A segment 0.32 of the path long runs along it, in on the left and out on the right, twice.
  const travel = useDerivedValue(() => {
    if (mode.get() !== 0 || !auto) return 0;
    const t = clock.get();
    const a = 1.6 + index * 0.13;
    const p1 = seg(t, a, a + 1.1);
    const p = p1 < 1 ? p1 : seg(t, a + 1.4, a + 2.5);
    return easeInOutCubic(p) * 1.32;
  });
  const head = useDerivedValue(() => Math.min(1, travel.get()));
  const start = useDerivedValue(() => Math.min(1, Math.max(0, travel.get() - 0.32)));
  const o = useDerivedValue(() => opacity.get() * (0.5 + 0.5 * hash(index + 3)));
  return <Path path={path} start={start} end={head} style="stroke" strokeWidth={1.2} strokeCap="round" color="rgba(255,228,196,1)" opacity={o} />;
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  masthead: { position: 'absolute', left: 24, right: 24, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  kicker: { color: PALETTE.ember, fontSize: 11, fontWeight: '700', letterSpacing: 3 },
  issue: { color: 'rgba(244,231,212,0.55)', fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  deck: { position: 'absolute', left: 24, right: 24, gap: 20 },
  deckText: { color: PALETTE.creamSoft, fontSize: 17, lineHeight: 24, fontWeight: '400' },
  stats: { flexDirection: 'row', gap: 8 },
  stat: {
    flex: 1,
    paddingVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,236,214,0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,236,214,0.16)',
    gap: 2,
  },
  statLabel: { color: 'rgba(244,231,212,0.5)', fontSize: 10, fontWeight: '700', letterSpacing: 1.4 },
  statValue: { color: PALETTE.cream, fontSize: 22, fontWeight: '600', fontVariant: ['tabular-nums'] },
  statUnit: { color: 'rgba(244,231,212,0.55)', fontSize: 12, fontWeight: '500' },
  hourly: { gap: 10 },
  hours: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
  hour: { alignItems: 'center', gap: 4, width: 44 },
  hourValue: { color: 'rgba(244,231,212,0.8)', fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  hourBar: { width: 6, borderRadius: 3 },
  hourAt: { color: 'rgba(244,231,212,0.45)', fontSize: 11, fontWeight: '500', fontVariant: ['tabular-nums'] },
  ctaWrap: { position: 'absolute', left: 24, right: 24 },
  cta: {
    height: 52,
    borderRadius: 999,
    overflow: 'hidden',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 12px 30px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,255,255,0.7)',
  },
  ctaText: { color: '#3a2418', fontSize: 17, fontWeight: '600' },
});
