import { BlendMode, Canvas, Picture, Rect, Shader, Skia, TileMode } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
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

import { type TreeGeo, drawGuides, drawLeader, makeTreeGeo, selectedMid } from './treeRingArt';
import { Odometer, RollStack } from './TreeRingLabels';
import {
  AMOUNTS,
  DELTAS,
  LABELS,
  LOOP,
  REST_SEL,
  RING_COUNT,
  YEARS,
  clamp01,
  fullRadii,
  grownOf,
  loopPose,
  ringRadii,
  sOfRadius,
  totalOf,
  touchPose,
} from './treeRingMotion';
import { TREE_RING_SKSL } from './treeRingShader';

/*
 Tree Ring Chart — "Heartwood", a savings app that shows seven years of saving as the cross-section
 of a cut tree. Each yearly ring is as wide as that year's savings (2019 $3,120 … 2025 $9,380,
 $44,650 in all). The slice is one runtime shader (treeRingShader.ts); the dashed guides, the table's
 pool of light and the leader line are Skia drawing in the same Canvas; the labels are views.

 autoPlay (LOOP = 9 s, treeRingMotion.ts):
   0.0 – 1.4   rest: the full slice, 2021 selected ("2021 · $6,240").
   1.4 – 2.5   the rings shrink back to the pith over a faint full-size ghost of the slice,
               outermost first (0.08 s apart, 0.5 s each, ease-in-out; fully shrunk for 0.12 s);
               the selection follows inward and the glow dims.
   2.5 – 6.4   they regrow year by year (0.557 s apart, 0.62 s each, back-out swell ~7 %); the
               newest ring is selected, the label and the total roll with it.
   6.4 – 7.4   the selection travels back from 2025 to 2021 (ease-in-out) and the glow returns.
   7.4 – 9.0   rest; the frame at 9.0 is the frame at 0.0.

 Touch: drag across the slice from the pith outward: the slice grows to the finger (a spring of
 260 ms, no bounce), the ring under the finger swells and is selected (a selection haptic per ring);
 releasing completes that ring (spring 450 ms, damping 0.7). The year row under the chart scrubs
 the selection too. 2 s after the last touch the slice eases back to rest (800 ms) and the loop
 starts again. Reduced motion: the rest frame, no loop; touches still select, without springs.
 */

const INK = '#F4E7D4';
const MUTED = 'rgba(244, 231, 212, 0.58)';
const AMBER = '#F2B86A';
const P3 = Platform.OS === 'ios' ? 1 : 0;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const RETURN_MS = 800;
const IDLE_S = 2;

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

function recordBackdrop(width: number, height: number, g: TreeGeo) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  // Grain over the whole screen (static), then the guides and the pool of light around the slice.
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 7, 0, 0));
  grain.setAlphaf(0.06);
  grain.setBlendMode(BlendMode.Overlay);
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), grain);
  const vignette = Skia.Paint();
  vignette.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: width / 2, y: height * 0.42 },
      Math.max(width, height) * 0.72,
      [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(8,4,2,0.18)'), Skia.Color('rgba(8,4,2,0.5)')],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), vignette);
  drawGuides(canvas, g, fullRadii(g.pith, g.wood), width, height);
  return recorder.finishRecordingAsPicture();
}

export function TreeRingChart({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const { width: screenW, height: screenH } = useWindowDimensions();
  const effect = useMemo(() => Skia.RuntimeEffect.Make(TREE_RING_SKSL), []);
  const [hero, setHero] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const g = useMemo(() => (hero ? makeTreeGeo(hero.x, hero.y, hero.w, hero.h) : null), [hero]);
  const backdrop = useMemo(() => (g ? recordBackdrop(screenW, screenH, g) : null), [g, screenW, screenH]);

  const rowW = screenW - 48;
  const step = rowW / RING_COUNT;

  // Loop time, the finger's state, and the clock that times the return to rest.
  const t = useSharedValue(0);
  const manual = useSharedValue(auto ? 0 : 1);
  const s = useSharedValue(RING_COUNT);
  const sel = useSharedValue(REST_SEL);
  const picked = useSharedValue(-1);
  const clock = useSharedValue(0);
  const resumeAt = useSharedValue(-1);

  function backToRest() {
    'worklet';
    s.set(withTiming(RING_COUNT, { duration: RETURN_MS, easing: EASE_IN_OUT }));
    sel.set(
      withTiming(REST_SEL, { duration: RETURN_MS, easing: EASE_IN_OUT }, (finished) => {
        if (finished && auto) {
          t.set(0);
          manual.set(0);
          picked.set(-1);
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
    const r = resumeAt.get();
    if (r > 0 && clock.get() >= r) {
      resumeAt.set(-1);
      backToRest();
    }
  });

  const pose = useDerivedValue(() => (manual.get() === 1 ? touchPose(s.get(), sel.get()) : loopPose(t.get())));
  const radii = useDerivedValue(() => (g ? ringRadii(pose.get(), g.pith, g.wood) : [0, 0, 0, 0, 0, 0, 0]));
  const selIndex = useDerivedValue(() => pose.get().sel);
  const total = useDerivedValue(() => totalOf(pose.get()));

  const uniforms = useDerivedValue(() => {
    const r = radii.get();
    const p = pose.get();
    if (!g) return { center: [0, 0], rA: [0, 0, 0, 0], rB: [0, 0, 0, 0], look: [0, 0, 0, 1], full: [0, 0], p3: P3 };
    const grown = clamp01((r[RING_COUNT - 1] - g.pith) / (g.wood - g.pith));
    return {
      center: [g.cx, g.cy],
      rA: [r[0], r[1], r[2], r[3]],
      rB: [r[4], r[5], r[6], g.pith],
      look: [p.sel, p.glow, g.bark * (0.55 + 0.45 * grown), g.unit],
      full: [g.wood, g.bark],
      p3: P3,
    };
  });

  const leader = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, screenW, screenH));
    if (g) {
      const p = pose.get();
      drawLeader(canvas, g, selectedMid(radii.get(), g.pith, p.sel), p.glow);
    }
    return recorder.finishRecordingAsPicture();
  });

  // Touch ----------------------------------------------------------------------------------------

  function takeOver() {
    'worklet';
    resumeAt.set(-1);
    if (manual.get() === 0) {
      const p = loopPose(t.get());
      s.set(grownOf(p));
      sel.set(p.sel);
      manual.set(1);
    }
  }

  function pick(target: number) {
    'worklet';
    if (target === picked.get()) return;
    picked.set(target);
    sel.set(reduced ? withTiming(target, { duration: 180 }) : withSpring(target, { duration: 380, dampingRatio: 0.82 }));
    scheduleOnRN(tick);
  }

  function followSlice(x: number, y: number) {
    'worklet';
    if (!g) return;
    const reach = sOfRadius(Math.hypot(x - (g.cx - g.ox), y - (g.cy - g.oy)), g.pith, g.wood);
    s.set(reduced ? reach : withSpring(reach, { duration: 260, dampingRatio: 1 }));
    pick(Math.max(0, Math.min(RING_COUNT - 1, Math.floor(reach))));
  }

  function release() {
    'worklet';
    const target = Math.max(0, picked.get());
    s.set(reduced ? target + 1 : withSpring(Math.min(RING_COUNT, target + 1), { duration: 450, dampingRatio: 0.7 }));
    if (auto) resumeAt.set(clock.get() + IDLE_S);
  }

  function followRow(x: number) {
    'worklet';
    s.set(reduced ? RING_COUNT : withSpring(RING_COUNT, { duration: 450, dampingRatio: 0.85 }));
    pick(Math.max(0, Math.min(RING_COUNT - 1, Math.floor(x / step))));
  }

  function releaseRow() {
    'worklet';
    if (auto) resumeAt.set(clock.get() + IDLE_S);
  }

  const slicePan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin((e) => {
          takeOver();
          followSlice(e.x, e.y);
        })
        .onUpdate((e) => {
          followSlice(e.x, e.y);
        })
        .onFinalize(() => {
          release();
        }),
    // The shared values are stable; the gesture depends on the geometry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [g, reduced, auto],
  );

  const rowPan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin((e) => {
          takeOver();
          followRow(e.x);
        })
        .onUpdate((e) => {
          followRow(e.x);
        })
        .onFinalize(() => {
          releaseRow();
        }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [step, reduced, auto],
  );

  const marker = useAnimatedStyle(() => ({ transform: [{ translateX: selIndex.get() * step }] }));

  const onHero = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    if (!hero || hero.w !== width || hero.h !== height || hero.y !== y) setHero({ x, y, w: width, h: height });
  };

  const maxAmount = Math.max(...AMOUNTS);

  return (
    <View style={styles.screen}>
      <LinearGradient colors={['#2C1E15', '#21160F', '#150E0A']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
      {g && backdrop && effect ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={backdrop} />
          <Rect x={g.ox} y={g.oy} width={g.w} height={g.h}>
            <Shader source={effect} uniforms={uniforms} />
          </Rect>
          <Picture picture={leader} />
        </Canvas>
      ) : null}

      <View style={[styles.content, { paddingTop: insets.top + 10, paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.header}>
          <View style={styles.topRow}>
            <Text style={styles.kicker}>HEARTWOOD</Text>
            <View style={styles.pill}>
              <Text style={styles.pillText}>Savings · 2019 – 2025</Text>
            </View>
          </View>
          <Text style={styles.title}>7 years of saving</Text>
          <View style={styles.totalRow}>
            <Odometer value={total} digits={5} prefix="$" height={24} style={styles.total} />
            <Text style={styles.totalNote}>put away, one ring a year</Text>
          </View>
        </View>

        <GestureDetector gesture={slicePan}>
          <View
            style={styles.hero}
            onLayout={onHero}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Tree ring chart of yearly savings. Drag from the centre outward to pick a year."
          />
        </GestureDetector>

        <View style={styles.labelBlock} pointerEvents="none">
          <RollStack items={LABELS} index={selIndex} height={32} style={styles.label} />
          <RollStack items={DELTAS} index={selIndex} height={18} style={styles.delta} />
        </View>

        <GestureDetector gesture={rowPan}>
          <View style={[styles.row, { width: rowW }]} accessibilityLabel="Years">
            <Animated.View style={[styles.marker, { width: step - 6, left: 3 }, marker]} />
            {YEARS.map((year, i) => (
              <View key={year} style={[styles.slot, { width: step }]}>
                <View style={[styles.bar, { height: 8 + (30 * AMOUNTS[i]) / maxAmount }]} />
                <Text style={styles.year}>{year}</Text>
              </View>
            ))}
          </View>
        </GestureDetector>

        <View style={styles.card}>
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Best year</Text>
            <Text style={styles.statValue}>2025 · $9,380</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Average</Text>
            <Text style={styles.statValue}>$6,379 a year</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.stat}>
            <Text style={styles.statLabel}>Next ring</Text>
            <Text style={styles.statValue}>Goal $10,000</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1B130E', overflow: 'hidden' },
  content: { flex: 1, paddingHorizontal: 24 },
  header: { gap: 6 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kicker: { color: AMBER, fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  pill: {
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 236, 210, 0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255, 236, 210, 0.18)',
  },
  pillText: { color: MUTED, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  title: { color: INK, fontSize: 32, fontWeight: '700', letterSpacing: -0.6, marginTop: 4 },
  totalRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  total: { color: INK, fontSize: 20, fontWeight: '600', fontVariant: ['tabular-nums'] },
  totalNote: { color: MUTED, fontSize: 14 },
  hero: { flex: 1, marginHorizontal: -24, minHeight: 300 },
  labelBlock: { alignItems: 'center', gap: 2, marginTop: 4 },
  label: { color: INK, fontSize: 26, fontWeight: '700', letterSpacing: -0.4, fontVariant: ['tabular-nums'] },
  delta: { color: MUTED, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  row: { flexDirection: 'row', alignSelf: 'center', height: 76, marginTop: 16, alignItems: 'flex-end' },
  marker: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(242, 184, 106, 0.13)',
    borderWidth: 1,
    borderColor: 'rgba(242, 184, 106, 0.4)',
  },
  slot: { alignItems: 'center', justifyContent: 'flex-end', paddingBottom: 8, gap: 6, height: 76 },
  bar: { width: 14, borderRadius: 5, backgroundColor: 'rgba(226, 178, 118, 0.42)' },
  year: { color: MUTED, fontSize: 11, fontWeight: '600', fontVariant: ['tabular-nums'] },
  card: {
    flexDirection: 'row',
    marginTop: 16,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255, 238, 214, 0.05)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255, 238, 214, 0.14)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 12px 32px rgba(0,0,0,0.25)',
  },
  stat: { flex: 1, gap: 4 },
  statLabel: { color: MUTED, fontSize: 11, fontWeight: '600', letterSpacing: 0.4 },
  statValue: { color: INK, fontSize: 14, fontWeight: '600', fontVariant: ['tabular-nums'] },
  divider: { width: StyleSheet.hairlineWidth, backgroundColor: 'rgba(255, 238, 214, 0.14)', marginHorizontal: 12 },
});
