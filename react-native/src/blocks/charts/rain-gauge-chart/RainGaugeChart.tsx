import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { LIFT_PT, LIFT_SCALE, type RainGeo, drawScene, levelY, makeRainGeo, recordBackdrop, recordRackFront } from './rainGaugeArt';
import { DecimalOdometer } from './RainGaugeLabels';
import { CAPACITY, DAYS, FULL_DAYS, LOOP, LOOP_LIFT, MM, NOTES, type RainPose, TUBES, kick, loopPose, totalOf } from './rainGaugeMotion';

/*
 Rain Gauge Chart — "Plot 7", a garden app's week of rainfall as seven glass test-tube gauges in a
 wooden rack, one per day (Mon 3.2 … Sun 6.6 mm, 38.6 mm in all; a tube holds 15 mm). Everything
 that moves is Skia drawing in one Canvas (rainGaugeArt.ts): glass with a bright stripe and ticks,
 water with a meniscus that climbs the walls and tilts as it sloshes, streaked rain with splashes on
 the boards and ripples in the tubes. Text is views.

 autoPlay (LOOP = 9 s, rainGaugeMotion.ts):
   0.0 – 1.4   rest: every tube holds its day's rain.
   1.4 – 2.4   the tubes drain (ease-in-out, 40 ms apart); the total rolls down.
   2.4 – 6.0   rain sweeps across the rack from Monday to Sunday (a band 1.25 tubes wide), and each
               tube fills in turn (0.45 s apart, 0.9 s each, ease-out), rocking where it is hit.
   6.0 – 7.4   Thursday lifts out of the rack (40 pt, scale 1.1, ease-out 0.45 s) with a slosh,
               its detail card rises above it, and it settles back (7.0 – 7.4).
   7.4 – 9.0   rest; the frame at 9.0 is the frame at 0.0.

 Touch: tap a tube to lift it and see its day (spring 420 ms, damping 0.7, a light haptic); tap it
 again to put it back. Drag in the sky above the rack to rain on the tubes under the finger: they
 fill (1.1 mm/s, up to 15 mm) and rock. 2 s after the last touch everything eases back to rest
 (800 ms) and the loop restarts. Reduced motion: the rest frame, no rain; a tap still shows a day.
 */

const INK = '#EAF2F0';
const MUTED = 'rgba(234, 242, 240, 0.58)';
const ACCENT = '#8FD3E8';
const RETURN_MS = 800;
const IDLE_S = 2;

function lightTap() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function mixPose(levels: number[], slosh: number[], liftIdx: number, lift: number, cursor: number, rain: number, rainT: number, back: number): RainPose {
  'worklet';
  const l: number[] = [];
  const s: number[] = [];
  for (let i = 0; i < TUBES; i++) {
    l.push(levels[i] + (MM[i] - levels[i]) * back);
    s.push(slosh[i] * (1 - back));
  }
  return { levels: l, slosh: s, liftIdx, lift: lift * (1 - back), cursor, rain: rain * (1 - back), rainT };
}

function DayLabel({ i, g, pose }: { i: number; g: RainGeo; pose: SharedValue<RainPose> }) {
  const lit = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.liftIdx === i ? p.lift : 0 };
  });
  const x = g.cx[i] - g.pitch / 2;
  return (
    <View style={[styles.day, { left: x, top: g.ground + 6, width: g.pitch }]}>
      <Text style={styles.dayName}>{DAYS[i]}</Text>
      <Text style={styles.dayValue}>{MM[i].toFixed(1)}</Text>
      <Animated.View style={[StyleSheet.absoluteFill, styles.dayLit, lit]}>
        <Text style={[styles.dayName, { color: ACCENT }]}>{DAYS[i]}</Text>
        <Text style={[styles.dayValue, { color: INK }]}>{MM[i].toFixed(1)}</Text>
      </Animated.View>
    </View>
  );
}

export function RainGaugeChart({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const { width: screenW, height: screenH } = useWindowDimensions();
  const [hero, setHero] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const [callout, setCallout] = useState(LOOP_LIFT);
  const g = useMemo(() => (hero ? makeRainGeo(hero.x, hero.y, hero.w, hero.h) : null), [hero]);
  const front = useMemo(() => (g ? recordRackFront(g, screenW, screenH) : null), [g, screenW, screenH]);
  const backdrop = useMemo(() => (g ? recordBackdrop(g, screenW, screenH) : null), [g, screenW, screenH]);

  const t = useSharedValue(0);
  const manual = useSharedValue(auto ? 0 : 1);
  const clock = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  // The finger's state (used while manual = 1).
  const levels = useSharedValue<number[]>(MM.slice());
  const kicks = useSharedValue<number[]>([-10, -10, -10, -10, -10, -10, -10]);
  const liftIdx = useSharedValue(-1);
  const lift = useSharedValue(0);
  const cursor = useSharedValue(0);
  const rain = useSharedValue(0);
  const rainT = useSharedValue(0);
  const back = useSharedValue(0);
  const touchMode = useSharedValue(0); // 1 = a tube under the finger, 2 = rain under the finger
  const start = useSharedValue<number[]>([0, 0]);

  function backToRest() {
    'worklet';
    back.set(
      withTiming(1, { duration: RETURN_MS }, (finished) => {
        if (finished && auto) {
          t.set(0);
          manual.set(0);
          back.set(0);
          lift.set(0);
          liftIdx.set(-1);
          rain.set(0);
          levels.set(MM.slice());
          scheduleOnRN(setCallout, LOOP_LIFT);
        }
      }),
    );
  }

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    clock.set(clock.get() + dt);
    if (manual.get() === 0) {
      t.set((t.get() + dt) % LOOP);
      return;
    }
    rainT.set((rainT.get() + dt) % LOOP);
    const r = rain.get();
    if (r > 0.01 && back.get() === 0) {
      const l = levels.get().slice();
      for (let i = 0; i < TUBES; i++) {
        const near = Math.exp(-Math.pow((i - cursor.get()) / 0.7, 2));
        l[i] = Math.min(CAPACITY, l[i] + dt * 1.1 * r * near);
      }
      levels.set(l);
    }
    const at = resumeAt.get();
    if (at > 0 && clock.get() >= at) {
      resumeAt.set(-1);
      backToRest();
    }
  });

  const pose = useDerivedValue<RainPose>(() => {
    if (manual.get() === 0) return loopPose(t.get());
    const now = clock.get();
    const k = kicks.get();
    const slosh: number[] = [];
    for (let i = 0; i < TUBES; i++) {
      const near = Math.exp(-Math.pow((i - cursor.get()) / 0.9, 2)) * rain.get();
      slosh.push(kick(now - k[i], 3.2) + 1.6 * near * Math.sin(now * 2 * Math.PI * 1.25 + i * 1.3));
    }
    return mixPose(levels.get(), slosh, liftIdx.get(), lift.get(), cursor.get(), rain.get(), rainT.get(), back.get());
  });

  const total = useDerivedValue(() => Math.round(totalOf(pose.get().levels) * 10 * 1000) / 1000);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, screenW, screenH));
    if (g && front) drawScene(canvas, g, pose.get(), front);
    return recorder.finishRecordingAsPicture();
  });

  // Touch ----------------------------------------------------------------------------------------

  function takeOver() {
    'worklet';
    resumeAt.set(-1);
    back.set(0);
    if (manual.get() === 0) {
      const p = loopPose(t.get());
      levels.set(p.levels.slice());
      liftIdx.set(p.lift > 0.001 ? p.liftIdx : -1);
      lift.set(p.lift);
      cursor.set(p.cursor);
      rain.set(p.rain);
      rainT.set(p.rainT);
      manual.set(1);
    }
  }

  function tubeAt(x: number) {
    'worklet';
    if (!g) return -1;
    for (let i = 0; i < TUBES; i++) if (Math.abs(x - g.cx[i]) < g.pitch / 2) return i;
    return -1;
  }

  function tapTube(i: number) {
    'worklet';
    const k = kicks.get().slice();
    k[i] = clock.get();
    if (liftIdx.get() === i && lift.get() > 0.5) {
      lift.set(reduced ? withTiming(0, { duration: 200 }) : withSpring(0, { duration: 420, dampingRatio: 0.85 }));
    } else {
      if (liftIdx.get() >= 0 && liftIdx.get() !== i) k[liftIdx.get()] = clock.get();
      liftIdx.set(i);
      lift.set(0);
      lift.set(reduced ? withTiming(1, { duration: 200 }) : withSpring(1, { duration: 420, dampingRatio: 0.7 }));
      scheduleOnRN(setCallout, i);
    }
    kicks.set(k);
    scheduleOnRN(lightTap);
  }

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin((e) => {
          if (!g) return;
          takeOver();
          start.set([e.x, e.y]);
          const y = e.y + g.oy;
          if (y > g.board - 24 || reduced) {
            touchMode.set(1);
          } else {
            touchMode.set(2);
            cursor.set(Math.max(-0.5, Math.min(TUBES - 0.5, (e.x + g.ox - g.cx[0]) / g.pitch)));
            rain.set(withTiming(1, { duration: 250 }));
          }
        })
        .onUpdate((e) => {
          if (!g) return;
          if (touchMode.get() === 2) cursor.set(Math.max(-0.5, Math.min(TUBES - 0.5, (e.x + g.ox - g.cx[0]) / g.pitch)));
        })
        .onFinalize((e) => {
          if (!g) return;
          const s = start.get();
          if (touchMode.get() === 1 && Math.hypot(e.x - s[0], e.y - s[1]) < 14) {
            const i = tubeAt(e.x + g.ox);
            if (i >= 0) tapTube(i);
          }
          if (touchMode.get() === 2) rain.set(withTiming(0, { duration: 600 }));
          touchMode.set(0);
          if (auto) resumeAt.set(clock.get() + IDLE_S);
        }),
    // The shared values are stable; the gesture depends on the geometry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [g, reduced, auto],
  );

  const card = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.lift, transform: [{ translateY: 10 * (1 - p.lift) }, { scale: 0.96 + 0.04 * p.lift }] };
  });

  const onHero = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    if (!hero || hero.w !== width || hero.h !== height || hero.y !== y) setHero({ x, y, w: width, h: height });
  };

  const cardW = 176;
  const cardTop = g ? g.y0 - LIFT_PT - (g.y1 - g.y0) * LIFT_SCALE - 84 : 0;
  const cardLeft = g ? Math.max(16, Math.min(screenW - cardW - 16, g.cx[callout] - cardW / 2)) : 0;

  return (
    <View style={styles.screen}>
      <LinearGradient colors={['#26393F', '#1D2D32', '#152124']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
      {g && backdrop ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={backdrop} />
          <Picture picture={picture} />
        </Canvas>
      ) : null}

      <View style={[styles.content, { paddingTop: insets.top + 10, paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.header}>
          <View style={styles.topRow}>
            <Text style={styles.kicker}>PLOT 7 · ALDER LANE</Text>
            <View style={styles.pill}>
              <Text style={styles.pillText}>Week 43 · Oct 20 – 26</Text>
            </View>
          </View>
          <Text style={styles.title}>Rainfall this week</Text>
          <View style={styles.totalRow}>
            <DecimalOdometer value={total} digits={3} suffix=" mm" height={26} style={styles.total} />
            <Text style={styles.totalNote}>+12% on last week</Text>
          </View>
        </View>

        <GestureDetector gesture={pan}>
          <View
            style={styles.hero}
            onLayout={onHero}
            accessible
            accessibilityLabel="Seven rain gauges, Monday to Sunday. Tap a gauge for its day, drag above the rack to make it rain."
          />
        </GestureDetector>

        <View style={styles.card}>
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Wettest</Text>
            <Text style={styles.statValue}>Thu · 11.6 mm</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Dry spells</Text>
            <Text style={styles.statValue}>1 day, Tue</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Watering</Text>
            <Text style={styles.statValue}>Skip till Sat</Text>
          </View>
        </View>
      </View>

      {g ? (
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <Text style={[styles.axisUnit, { left: g.ox + 10, top: levelY(g, CAPACITY) - 30 }]}>mm</Text>
          {[15, 10, 5, 0].map((mm) => (
            <Text key={mm} style={[styles.axis, { left: g.ox + 6, top: levelY(g, mm) - 8 }]}>
              {mm}
            </Text>
          ))}
          {DAYS.map((d, i) => (
            <DayLabel key={d} i={i} g={g} pose={pose} />
          ))}
          <Animated.View style={[styles.callout, { left: cardLeft, top: cardTop, width: cardW }, card]}>
            <Text style={styles.calloutDay}>{FULL_DAYS[callout]}</Text>
            <Text style={styles.calloutValue}>{MM[callout].toFixed(1)} mm</Text>
            <Text style={styles.calloutNote} numberOfLines={1}>
              {NOTES[callout]}
            </Text>
          </Animated.View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1D2D32', overflow: 'hidden' },
  content: { flex: 1, paddingHorizontal: 24 },
  header: { gap: 6 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kicker: { color: ACCENT, fontSize: 12, fontWeight: '700', letterSpacing: 2.2 },
  pill: {
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    justifyContent: 'center',
    backgroundColor: 'rgba(226, 244, 248, 0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(226, 244, 248, 0.18)',
  },
  pillText: { color: MUTED, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  title: { color: INK, fontSize: 32, fontWeight: '700', letterSpacing: -0.6, marginTop: 4 },
  totalRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  total: { color: INK, fontSize: 22, fontWeight: '600', fontVariant: ['tabular-nums'] },
  totalNote: { color: MUTED, fontSize: 14 },
  hero: { flex: 1, marginHorizontal: -24, minHeight: 360 },
  axis: { position: 'absolute', width: 22, textAlign: 'right', color: MUTED, fontSize: 10, fontWeight: '600', fontVariant: ['tabular-nums'] },
  axisUnit: { position: 'absolute', color: MUTED, fontSize: 10, fontWeight: '600' },
  day: { position: 'absolute', alignItems: 'center', gap: 1 },
  dayLit: { alignItems: 'center', gap: 1 },
  dayName: { color: MUTED, fontSize: 12, fontWeight: '600' },
  dayValue: { color: 'rgba(234, 242, 240, 0.42)', fontSize: 11, fontWeight: '500', fontVariant: ['tabular-nums'] },
  callout: {
    position: 'absolute',
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(30, 50, 56, 0.92)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(170, 225, 240, 0.35)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 14px 30px rgba(0,0,0,0.3)',
    gap: 2,
  },
  calloutDay: { color: ACCENT, fontSize: 12, fontWeight: '700', letterSpacing: 0.4 },
  calloutValue: { color: INK, fontSize: 22, fontWeight: '700', fontVariant: ['tabular-nums'] },
  calloutNote: { color: MUTED, fontSize: 12 },
  card: {
    flexDirection: 'row',
    marginTop: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(226, 244, 248, 0.05)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(226, 244, 248, 0.14)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 12px 32px rgba(0,0,0,0.25)',
  },
  stat: { flex: 1, gap: 4 },
  statLabel: { color: MUTED, fontSize: 11, fontWeight: '600', letterSpacing: 0.4 },
  statValue: { color: INK, fontSize: 14, fontWeight: '600', fontVariant: ['tabular-nums'] },
  divider: { width: StyleSheet.hairlineWidth, backgroundColor: 'rgba(226, 244, 248, 0.14)', marginHorizontal: 12 },
});
