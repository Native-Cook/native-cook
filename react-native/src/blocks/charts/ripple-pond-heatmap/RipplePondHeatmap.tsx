import { Canvas, Fill, ImageShader, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { bakePads, BEST_STREAK, detailFor, SUMMARY } from './rippleArt';
import {
  CARD_H,
  CHIPS_H,
  HEADER_H,
  makePondGeo,
  makeSim,
  type PondGeo,
  type PondSim,
  pondUniforms,
  simDown,
  simDrag,
  simDragEnd,
  simStep,
  simTap,
} from './rippleMotion';
import { RIPPLE_SKSL } from './rippleShader';

/*
 Ripple Pond Heatmap — "Stillpond", a meditation habit tracker's month as lily pads on a pond.

 Look: a pond seen from above (sand, pebbles, drifting caustics), October as a 7 × 5 grid of lily
 pads whose green deepens with the minutes practised (pale sage to deep emerald; days over an
 hour carry a water lily; days after today are faint rings). Tap a day and a ripple spreads from
 it: the floor refracts, light focuses into rings, the pads around bob as the wave passes, a ring
 lights the pad and the card below rolls to that day. Drag across the water and a wake follows.
 One full-screen shader (rippleShader.ts) over pads baked once (rippleArt.ts).

 autoPlay: LOOP = 9 s (script in rippleMotion.ts). 2 s after the last touch the water calms
 (0.9 s), the card rolls back to the month and the loop restarts. Reduced motion: still water,
 the month card, no loop.
*/

type Props = {
  /** Plays the 9 s tap-tap-wake loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(RIPPLE_SKSL);
const INK = '#EAF3EC';
const SOFT = 'rgba(234,243,236,0.66)';
const LEVELS = ['#9DB3A2', '#A9CF8E', '#6FB06A', '#3E8C4E', '#1F6A3C'];
const WEEK = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const ROLL = 52;
const EASE = Easing.bezier(0.77, 0, 0.175, 1);

export function RipplePondHeatmap({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [detail, setDetail] = useState(-1);
  const geo = useMemo<PondGeo | null>(
    () => (size ? makePondGeo(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );
  const pads = useMemo(() => (size && geo ? bakePads(size.width, size.height, geo.grid) : null), [size, geo]);
  const sim = useSharedValue<PondSim>(makeSim());
  const tick = useSharedValue(0);

  useEffect(() => {
    sim.set(makeSim());
  }, [auto, sim]);

  useFrameCallback((frame) => {
    if (!geo) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    simStep(sim.get(), dt, geo, auto, reduced);
    tick.set(tick.get() + 1);
  });

  // The card follows the selection (a few times a loop, never per frame).
  useAnimatedReaction(
    () => {
      tick.get();
      return sim.get().frame.detail;
    },
    (now, before) => {
      if (now !== before) scheduleOnRN(setDetail, now);
    },
  );

  const light = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const touch = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      simDown(sim.get(), e.x, e.y);
    })
    .onUpdate((e) => {
      if (Math.hypot(e.translationX, e.translationY) < 10) return;
      simDrag(sim.get(), e.x, e.y, Math.hypot(e.velocityX, e.velocityY));
    })
    .onEnd((e) => {
      if (!geo) return;
      const s = sim.get();
      if (Math.hypot(e.translationX, e.translationY) < 10) {
        if (simTap(s, geo, e.x, e.y) > 0) scheduleOnRN(light);
      } else {
        simDragEnd(s, geo, e.y);
      }
    })
    .onFinalize(() => {
      const s = sim.get();
      s.dragging = false;
    });

  const uniforms = useDerivedValue(() => {
    tick.get();
    return pondUniforms(sim.get().frame, geo ?? makePondGeo(1, 1, 0, 0));
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize({ width, height });
  };

  const W = size?.width ?? 0;
  const H = size?.height ?? 0;

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="October as lily pads on a pond. Tap a day to see it; drag to make a wake.">
          {size && EFFECT && pads ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={EFFECT} uniforms={uniforms}>
                  <ImageShader image={pads} rect={{ x: 0, y: 0, width: W, height: H }} fit="fill" />
                </Shader>
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 10, height: HEADER_H }]}>
        <View style={styles.brandRow}>
          <Svg width={16} height={16} viewBox="0 0 16 16">
            <Path d="M8 14.5 C3 12 2 7 3.5 2.5 C8 3 12.5 6 8 14.5 Z" fill="#8FD19A" />
            <Path d="M8 14 C6.5 10 5.2 7 3.8 3" stroke="#2F6B3D" strokeWidth={1} fill="none" strokeLinecap="round" />
          </Svg>
          <Text style={styles.brand}>STILLPOND</Text>
        </View>
        <View style={styles.titleRow}>
          <Text style={styles.title}>October</Text>
          <Text style={styles.year}>2026</Text>
        </View>
        <Text style={styles.summary} numberOfLines={1} adjustsFontSizeToFit>
          {SUMMARY}
        </Text>
      </View>

      {geo ? (
        <>
          <View pointerEvents="none" style={[styles.week, { top: geo.grid[1] - 26, left: geo.grid[0] }]}>
            {WEEK.map((d, i) => (
              <Text key={i} style={[styles.weekDay, { width: geo.grid[2] }]}>
                {d}
              </Text>
            ))}
          </View>
          <View pointerEvents="none" style={[styles.legend, { top: geo.legendTop }]}>
            <Text style={styles.legendText}>Less</Text>
            <Svg width={92} height={14} viewBox="0 0 92 14">
              {LEVELS.map((c, i) => (
                <Circle key={c} cx={7 + i * 19.5} cy={7} r={4 + i * 0.7} fill={c} opacity={i === 0 ? 0.7 : 1} />
              ))}
            </Svg>
            <Text style={styles.legendText}>More</Text>
            <View style={styles.spacer} />
            <Text style={styles.legendText}>Goal 20 min a day</Text>
          </View>
          <View style={[styles.card, { top: geo.cardTop, height: CARD_H }]}>
            <RollingDetail id={detail} />
          </View>
          <View style={[styles.chips, { top: geo.chipsTop, height: CHIPS_H }]}>
            <Chip label={`Best streak ${BEST_STREAK} days`} />
            <Chip label="Reminder 7:00" />
            <Chip label="Export" />
          </View>
        </>
      ) : null}
    </View>
  );
}

function Chip({ label }: { label: string }) {
  return (
    <View style={styles.chip}>
      <Text style={styles.chipText} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

/**
 The card's two lines, rolled: two slots take turns. A new detail is written into the slot that is
 hidden (opacity 0, clipped), which then rolls up from below while the shown one rolls up and out,
 so old and new lines never sit on each other.
*/
function RollingDetail({ id }: { id: number }) {
  const [slots, setSlots] = useState({ texts: [id, id], front: 0 });
  const yA = useSharedValue(0);
  const yB = useSharedValue(1);
  const shownId = slots.texts[slots.front];

  // A new detail goes into the hidden slot during render (React's "adjust state on prop change").
  if (id !== shownId) {
    const back = 1 - slots.front;
    const texts = slots.texts.slice();
    texts[back] = id;
    setSlots({ texts, front: back });
  }

  useEffect(() => {
    const incoming = slots.front === 0 ? yA : yB;
    const outgoing = slots.front === 0 ? yB : yA;
    if (incoming.get() === 0) return;
    // The incoming slot starts below the window (it was invisible above it), then both roll up.
    incoming.set(1);
    incoming.set(withTiming(0, { duration: 380, easing: EASE }));
    outgoing.set(withTiming(-1, { duration: 380, easing: EASE }));
  }, [slots, yA, yB]);

  const styleA = useAnimatedStyle(() => ({
    opacity: 1 - Math.min(1, Math.abs(yA.get()) * 1.4),
    transform: [{ translateY: yA.get() * ROLL }],
  }));
  const styleB = useAnimatedStyle(() => ({
    opacity: 1 - Math.min(1, Math.abs(yB.get()) * 1.4),
    transform: [{ translateY: yB.get() * ROLL }],
  }));
  const a = detailFor(slots.texts[0]);
  const b = detailFor(slots.texts[1]);
  return (
    <View style={styles.detailClip}>
      <Animated.View style={[styles.detail, styleA]}>
        <Text style={styles.detailTitle} numberOfLines={1} adjustsFontSizeToFit>
          {a.title}
        </Text>
        <Text style={styles.detailSub} numberOfLines={1}>
          {a.sub}
        </Text>
      </Animated.View>
      <Animated.View style={[styles.detail, styleB]}>
        <Text style={styles.detailTitle} numberOfLines={1} adjustsFontSizeToFit>
          {b.title}
        </Text>
        <Text style={styles.detailSub} numberOfLines={1}>
          {b.sub}
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0B2A2E', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20 },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 24 },
  brand: { color: '#9FD8A9', fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  titleRow: { flexDirection: 'row', alignItems: 'baseline', gap: 10, marginTop: 8 },
  title: { color: INK, fontSize: 36, fontWeight: '700', letterSpacing: -0.6 },
  year: { color: SOFT, fontSize: 20, fontWeight: '500', fontVariant: ['tabular-nums'] },
  summary: { color: SOFT, fontSize: 15, fontWeight: '500', marginTop: 4, fontVariant: ['tabular-nums'] },
  week: { position: 'absolute', flexDirection: 'row' },
  weekDay: { textAlign: 'center', color: 'rgba(234,243,236,0.5)', fontSize: 12, fontWeight: '700' },
  legend: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', alignItems: 'center', gap: 8, height: 20 },
  legendText: { color: 'rgba(234,243,236,0.55)', fontSize: 12, fontWeight: '600' },
  spacer: { flex: 1 },
  card: {
    position: 'absolute',
    left: 16,
    right: 16,
    paddingHorizontal: 20,
    justifyContent: 'center',
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(8,30,32,0.66)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(200,240,215,0.2)',
    boxShadow: '0 2px 4px rgba(0,10,12,0.35), 0 20px 40px rgba(0,10,12,0.35), inset 0 1px 0 rgba(255,255,255,0.08)',
  },
  detailClip: { height: 52, overflow: 'hidden' },
  detail: { position: 'absolute', left: 0, right: 0, top: 0, gap: 4 },
  detailTitle: { color: INK, fontSize: 20, fontWeight: '700', fontVariant: ['tabular-nums'] },
  detailSub: { color: SOFT, fontSize: 14, fontWeight: '500' },
  chips: { position: 'absolute', left: 16, right: 16, flexDirection: 'row', gap: 8 },
  chip: {
    height: CHIPS_H,
    paddingHorizontal: 14,
    borderRadius: CHIPS_H / 2,
    justifyContent: 'center',
    backgroundColor: 'rgba(200,240,215,0.1)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(200,240,215,0.2)',
  },
  chipText: { color: INK, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
});
