// The reusable piece: a first-run tour over any screen. A dim layer with one soft cutout that morphs from
// target to target (one Skia path driven by one progress value), and a tooltip bubble that slides after it,
// re-points its arrow, and carries step dots, Skip and Next. Next, a tap on the highlighted element or a
// swipe of the tooltip moves the tour; Skip / Done dismisses it.
import { BlurMask, Canvas, FillType, Path, rect, RoundedRect, rrect, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { memo, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import type { CoachStep } from './data';
import {
  along,
  ARROW_H,
  ARROW_W,
  clamp01,
  cutoutAt,
  DIM,
  DRAG_SPRING,
  IRIS_SPRING,
  MORPH_SPRING,
  mix,
  PULSE_ALPHA,
  PULSE_GROW,
  RING_INSET,
  SOFT_EDGE,
  springConfig,
  SWIPE_RUBBER,
  SWIPE_STEP,
  SWIPE_VELOCITY,
  TIP_FADE_SCALE,
  TIP_GAP,
  TIP_H,
  TIP_LAG,
  TIP_MAX_W,
  TIP_OPACITY_GAIN,
  TIP_SPRING,
  type Box,
} from './motion';

export const BUBBLE = '#FFFDF9';
const INK = '#1E2420';
const MUTE = '#6E746F';
const ACCENT = '#2F7D5B';
export const NEXT_W = 84;
export const SKIP_W = 56;
export const FOOT_H = 36;
export const TIP_PAD = 18;

export type CoachMotion = {
  /** Fractional step of the cutout: 0 first target, 1 second, … */
  p: SharedValue<number>;
  /** The tooltip's own copy of p, a beat behind (follow-through). */
  tipP: SharedValue<number>;
  /** Swipe offset of the tooltip, pt. */
  tipX: SharedValue<number>;
  /** Dim layer and ring: 0 hidden, 1 shown. */
  shown: SharedValue<number>;
  /** Tooltip: 0 hidden (scaled down), 1 shown. */
  tipShow: SharedValue<number>;
  /** 1 = the cutout is a circle larger than the screen around the first target (the tour opening). */
  iris: SharedValue<number>;
  /** The ring breathing out of the cutout: phase 0..1 and how much of it shows. */
  pulse: SharedValue<number>;
  pulseGate: SharedValue<number>;
  /** Press feedback on Next, Skip and the highlighted element (0..1). */
  nextPress: SharedValue<number>;
  skipPress: SharedValue<number>;
  targetPress: SharedValue<number>;
  /** The step a finger asked for. */
  step: SharedValue<number>;
  /** 1 while the tour takes touches (shown), 0 lets them through to the app. */
  visible: SharedValue<number>;
  /** 1 while a script poses the tour; the first touch hands it to the finger. */
  driven: SharedValue<number>;
  held: SharedValue<number>;
  touches: SharedValue<number>;
  /** Reduce Motion: cuts and fades instead of travel. */
  calm: SharedValue<number>;
};

export function useCoachMotion(): CoachMotion {
  const p = useSharedValue(0);
  const tipP = useSharedValue(0);
  const tipX = useSharedValue(0);
  const shown = useSharedValue(1);
  const tipShow = useSharedValue(1);
  const iris = useSharedValue(0);
  const pulse = useSharedValue(0);
  const pulseGate = useSharedValue(0);
  const nextPress = useSharedValue(0);
  const skipPress = useSharedValue(0);
  const targetPress = useSharedValue(0);
  const step = useSharedValue(0);
  const visible = useSharedValue(1);
  const driven = useSharedValue(0);
  const held = useSharedValue(0);
  const touches = useSharedValue(0);
  const calm = useSharedValue(0);
  return useMemo(
    () => ({ p, tipP, tipX, shown, tipShow, iris, pulse, pulseGate, nextPress, skipPress, targetPress, step, visible, driven, held, touches, calm }),
    [p, tipP, tipX, shown, tipShow, iris, pulse, pulseGate, nextPress, skipPress, targetPress, step, visible, driven, held, touches, calm],
  );
}

export type TipPlace = { x: number; y: number; arrowX: number; below: number };

/** Where the tooltip sits for each target: under it in the top half of the screen, above it otherwise. */
export function tipPlaces(targets: Box[], width: number, height: number, tipW: number): TipPlace[] {
  return targets.map(b => {
    const cx = b.x + b.w / 2;
    const below = b.y + b.h / 2 < height / 2 ? 1 : 0;
    const x = Math.min(Math.max(cx - tipW / 2, 16), width - 16 - tipW);
    const y = below ? b.y + b.h + TIP_GAP + ARROW_H : b.y - TIP_GAP - ARROW_H - TIP_H;
    const arrowX = Math.min(Math.max(cx - x, 28), tipW - 28);
    return { x, y, arrowX, below };
  });
}

function lightTap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function springTo(value: SharedValue<number>, to: number, s: { response: number; ratio: number }, calm: number, velocity: number, delay: number) {
  'worklet';
  if (calm === 1) {
    value.set(to);
    return;
  }
  const spring = withSpring(to, { ...springConfig(s), velocity });
  value.set(delay > 0 ? withDelay(delay, spring) : spring);
}

/** Dismisses the tour (Skip, Done): the dim lifts, the tooltip shrinks away, touches reach the app. */
export function hideTour(m: CoachMotion) {
  'worklet';
  const calm = m.calm.get();
  m.shown.set(withTiming(0, { duration: calm === 1 ? 200 : 360, easing: Easing.inOut(Easing.cubic) }));
  m.tipShow.set(withTiming(0, { duration: calm === 1 ? 160 : 240, easing: Easing.in(Easing.cubic) }));
  springTo(m.tipX, 0, DRAG_SPRING, calm, 0, 0);
  m.visible.set(0);
}

/** Moves the tour to step s (past the last one dismisses it); the tooltip follows TIP_LAG later. */
export function goToStep(m: CoachMotion, s: number, count: number, velocity: number) {
  'worklet';
  if (s < 0) {
    springTo(m.tipX, 0, DRAG_SPRING, m.calm.get(), velocity, 0);
    return;
  }
  if (s >= count) {
    hideTour(m);
    scheduleOnRN(lightTap);
    return;
  }
  const calm = m.calm.get();
  m.step.set(s);
  springTo(m.p, s, MORPH_SPRING, calm, 0, 0);
  springTo(m.tipP, s, TIP_SPRING, calm, 0, TIP_LAG * 1000);
  springTo(m.tipX, 0, DRAG_SPRING, calm, velocity, 0);
  scheduleOnRN(lightTap);
}

/** Opens the tour from the start: the dim fades in while the cutout irises down onto the first target. */
export function openTour(m: CoachMotion) {
  'worklet';
  const calm = m.calm.get();
  m.step.set(0);
  m.visible.set(1);
  m.tipX.set(0);
  if (m.shown.get() < 0.5) {
    m.p.set(0);
    m.tipP.set(0);
    m.iris.set(calm === 1 ? 0 : 1);
    m.shown.set(withTiming(1, { duration: 400, easing: Easing.out(Easing.cubic) }));
    if (calm === 0) m.iris.set(withSpring(0, springConfig(IRIS_SPRING)));
    m.tipShow.set(withDelay(calm === 1 ? 0 : 500, withTiming(1, { duration: 400, easing: Easing.out(Easing.cubic) })));
  } else {
    springTo(m.p, 0, MORPH_SPRING, calm, 0, 0);
    springTo(m.tipP, 0, TIP_SPRING, calm, 0, TIP_LAG * 1000);
    m.shown.set(withTiming(1, { duration: 200 }));
    m.tipShow.set(withTiming(1, { duration: 200 }));
    m.iris.set(withTiming(0, { duration: 200 }));
  }
}

/** A script let go: the tour settles on the step nearest to where it was, or stays dismissed. */
export function adoptTour(m: CoachMotion, count: number) {
  'worklet';
  if (m.driven.get() === 0) return;
  m.driven.set(0);
  const calm = m.calm.get();
  m.nextPress.set(withTiming(0, { duration: 120 }));
  m.skipPress.set(withTiming(0, { duration: 120 }));
  m.targetPress.set(withTiming(0, { duration: 120 }));
  if (m.shown.get() < 0.5 || m.tipShow.get() < 0.5) {
    hideTour(m);
    return;
  }
  const s = Math.min(Math.max(Math.round(m.p.get()), 0), count - 1);
  m.step.set(s);
  m.visible.set(1);
  springTo(m.p, s, MORPH_SPRING, calm, 0, 0);
  springTo(m.tipP, s, TIP_SPRING, calm, 0, 0);
  springTo(m.tipX, 0, DRAG_SPRING, calm, 0, 0);
  m.shown.set(withTiming(1, { duration: 160 }));
  m.tipShow.set(withTiming(1, { duration: 160 }));
  m.iris.set(withTiming(0, { duration: 160 }));
}

/** True once the tour rests on its first step, fully shown and still. */
export function tourAtRest(m: CoachMotion) {
  'worklet';
  return (
    Math.abs(m.p.get()) < 0.003 &&
    Math.abs(m.tipP.get()) < 0.003 &&
    m.shown.get() > 0.997 &&
    m.tipShow.get() > 0.997 &&
    Math.abs(m.iris.get()) < 0.003 &&
    Math.abs(m.tipX.get()) < 0.5
  );
}

function grab(m: CoachMotion, count: number) {
  'worklet';
  m.touches.set(m.touches.get() + 1);
  m.held.set(1);
  adoptTour(m, count);
}

function letGo(m: CoachMotion) {
  'worklet';
  m.held.set(0);
}

/** Swipe resistance: free in a direction that moves the tour, rubbery in one that does not. */
function dragOffset(dx: number, step: number) {
  'worklet';
  const free = dx > 0 ? step > 0 : true;
  if (free) return dx;
  const a = Math.abs(dx);
  return Math.sign(dx) * ((a * SWIPE_RUBBER) / (1 + a / 160));
}

function hitBox(b: Box, x: number, y: number) {
  'worklet';
  return x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h;
}

/** The pulse ring eases out as it grows. */
function easeOutPulse(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u;
}

type StepTextProps = { step: CoachStep; index: number; count: number; m: CoachMotion };

const StepText = memo(function StepText({ step, index, count, m }: StepTextProps) {
  const style = useAnimatedStyle(() => {
    const d = m.tipP.get() - index;
    return { opacity: clamp01(1 - Math.abs(d) * 2), transform: [{ translateY: -d * 8 }] };
  });
  return (
    <Animated.View style={[styles.stepText, style]} pointerEvents="none">
      <Text style={styles.counter}>{`${index + 1} of ${count}`}</Text>
      <Text style={styles.title} numberOfLines={1}>{step.title}</Text>
      <Text style={styles.body} numberOfLines={2}>{step.body}</Text>
    </Animated.View>
  );
});

function Dot({ index, m }: { index: number; m: CoachMotion }) {
  const style = useAnimatedStyle(() => {
    const on = clamp01(1 - Math.abs(m.tipP.get() - index));
    return { width: 6 + 14 * on };
  });
  const fill = useAnimatedStyle(() => ({ opacity: clamp01(1 - Math.abs(m.tipP.get() - index)) }));
  return (
    <Animated.View style={[styles.dot, style]}>
      <Animated.View style={[styles.dotOn, fill]} />
    </Animated.View>
  );
}

function Arrow({ down }: { down: boolean }) {
  return (
    <Svg width={ARROW_W} height={ARROW_H + 1} viewBox={`0 0 ${ARROW_W} ${ARROW_H + 1}`}>
      <SvgPath
        d={
          down
            ? `M0 0 H${ARROW_W} L${ARROW_W / 2 + 2} ${ARROW_H - 1} Q${ARROW_W / 2} ${ARROW_H + 0.6} ${ARROW_W / 2 - 2} ${ARROW_H - 1} Z`
            : `M0 ${ARROW_H + 1} H${ARROW_W} L${ARROW_W / 2 + 2} 2 Q${ARROW_W / 2} 0.4 ${ARROW_W / 2 - 2} 2 Z`
        }
        fill={BUBBLE}
      />
    </Svg>
  );
}

export type CoachMarksProps = {
  steps: CoachStep[];
  /** One box per step, in screen points, already padded; r is the corner radius (w / 2 for a circle). */
  targets: Box[];
  motion: CoachMotion;
  width: number;
  height: number;
  reducedMotion?: boolean;
};

/** The tour layer. Put it over the screen it explains, full size. */
export function CoachMarks({ steps, targets, motion: m, width, height, reducedMotion = false }: CoachMarksProps) {
  const count = steps.length;
  const tipW = Math.min(width - 32, TIP_MAX_W);
  const places = useMemo(() => tipPlaces(targets, width, height, tipW), [height, targets, tipW, width]);
  const xs = useMemo(() => places.map(pl => pl.x), [places]);
  const ys = useMemo(() => places.map(pl => pl.y), [places]);
  const arrowXs = useMemo(() => places.map(pl => pl.arrowX), [places]);
  const belows = useMemo(() => places.map(pl => pl.below), [places]);
  const [visible, setVisible] = useState(true);
  const startX = useSharedValue(0);

  useAnimatedReaction(
    () => m.visible.get(),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setVisible, v === 1);
    },
  );
  useAnimatedReaction(
    () => (reducedMotion ? 1 : 0),
    calm => m.calm.set(calm),
  );

  const box = useDerivedValue(() => cutoutAt(targets, m.p.get(), m.iris.get()));

  const dimPath = useDerivedValue(() => {
    const b = box.get();
    const builder = Skia.PathBuilder.Make();
    builder.addRect(rect(-40, -40, width + 80, height + 80));
    builder.addRRect(rrect(rect(b.x, b.y, b.w, b.h), b.r, b.r));
    builder.setFillType(FillType.EvenOdd); // the cutout is a hole
    return builder.build();
  });
  const ring = useDerivedValue(() => {
    const b = box.get();
    const g = RING_INSET;
    return rrect(rect(b.x - g, b.y - g, b.w + 2 * g, b.h + 2 * g), b.r + g, b.r + g);
  });
  const pulseRing = useDerivedValue(() => {
    const b = box.get();
    const g = RING_INSET + PULSE_GROW * easeOutPulse(m.pulse.get());
    return rrect(rect(b.x - g, b.y - g, b.w + 2 * g, b.h + 2 * g), b.r + g, b.r + g);
  });
  const dimOpacity = useDerivedValue(() => m.shown.get());
  const ringOpacity = useDerivedValue(() => m.shown.get() * (0.42 + 0.4 * m.targetPress.get()) * clamp01(1 - m.iris.get() * 3));
  const pulseOpacity = useDerivedValue(() => PULSE_ALPHA * (1 - m.pulse.get()) * m.pulseGate.get() * m.shown.get() * clamp01(1 - m.iris.get() * 3));

  const tap = useMemo(
    () =>
      Gesture.Tap()
        .maxDistance(12)
        .onBegin(() => grab(m, count))
        .onEnd(ev => {
          if (m.visible.get() === 0) return;
          const b = cutoutAt(targets, m.p.get(), m.iris.get());
          if (hitBox(b, ev.x, ev.y)) goToStep(m, m.step.get() + 1, count, 0);
        })
        .onFinalize(() => letGo(m)),
    [count, m, targets],
  );

  const swipe = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX([-10, 10])
        .failOffsetY([-16, 16])
        .onBegin(() => grab(m, count))
        .onStart(() => startX.set(m.tipX.get()))
        .onUpdate(ev => {
          m.tipX.set(dragOffset(startX.get() + ev.translationX, m.step.get()));
        })
        .onEnd(ev => {
          const dx = m.tipX.get();
          const s = m.step.get();
          if ((dx > SWIPE_STEP || ev.velocityX > SWIPE_VELOCITY) && s > 0) goToStep(m, s - 1, count, ev.velocityX);
          else if (dx < -SWIPE_STEP || ev.velocityX < -SWIPE_VELOCITY) goToStep(m, s + 1, count, ev.velocityX);
          else springTo(m.tipX, 0, DRAG_SPRING, m.calm.get(), ev.velocityX, 0);
        })
        .onFinalize(() => letGo(m)),
    [count, m, startX],
  );

  const tipStyle = useAnimatedStyle(() => {
    const q = m.tipP.get();
    const show = m.tipShow.get();
    const below = along(belows, q);
    return {
      opacity: clamp01(show * TIP_OPACITY_GAIN),
      transform: [
        { translateX: along(xs, q) + m.tipX.get() },
        { translateY: along(ys, q) + (1 - show) * (below > 0.5 ? -14 : 14) },
        { scale: mix(show, TIP_FADE_SCALE, 1) },
      ],
    };
  });
  const upArrow = useAnimatedStyle(() => {
    const q = m.tipP.get();
    const on = clamp01((along(belows, q) - 0.5) * 2);
    return { opacity: on, transform: [{ translateX: along(arrowXs, q) - ARROW_W / 2 }, { scaleY: on }] };
  });
  const downArrow = useAnimatedStyle(() => {
    const q = m.tipP.get();
    const on = clamp01((0.5 - along(belows, q)) * 2);
    return { opacity: on, transform: [{ translateX: along(arrowXs, q) - ARROW_W / 2 }, { scaleY: on }] };
  });
  const nextLabel = useAnimatedStyle(() => ({ opacity: clamp01((count - 1.5 - m.tipP.get()) * 2) }));
  const doneLabel = useAnimatedStyle(() => ({ opacity: clamp01((m.tipP.get() - (count - 1.5)) * 2) }));
  const nextStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.07 * m.nextPress.get() }] }));
  const skipStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.45 * m.skipPress.get() }));

  const onNext = () => scheduleOnUI(goToStep, m, m.step.get() + 1, count, 0);
  const onSkip = () => scheduleOnUI(hideTour, m);

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Path path={dimPath} color={DIM} opacity={dimOpacity}>
          <BlurMask blur={SOFT_EDGE} style="normal" />
        </Path>
        <RoundedRect rect={pulseRing} color="#FFFFFF" style="stroke" strokeWidth={1.5} opacity={pulseOpacity} />
        <RoundedRect rect={ring} color="#FFFFFF" style="stroke" strokeWidth={1.5} opacity={ringOpacity} />
      </Canvas>

      <GestureDetector gesture={tap}>
        <View style={StyleSheet.absoluteFill} pointerEvents={visible ? 'auto' : 'none'} accessible={false} />
      </GestureDetector>

      <GestureDetector gesture={swipe}>
        <Animated.View
          style={[styles.tip, { width: tipW }, tipStyle]}
          pointerEvents={visible ? 'auto' : 'none'}
          accessibilityViewIsModal={visible}
        >
          <Animated.View style={[styles.arrowUp, upArrow]} pointerEvents="none">
            <Arrow down={false} />
          </Animated.View>
          <Animated.View style={[styles.arrowDown, downArrow]} pointerEvents="none">
            <Arrow down />
          </Animated.View>
          {steps.map((step, i) => (
            <StepText key={step.title} step={step} index={i} count={count} m={m} />
          ))}
          <View style={styles.foot}>
            <View style={styles.dots}>
              {steps.map((step, i) => (
                <Dot key={step.title} index={i} m={m} />
              ))}
            </View>
            <Pressable onPress={onSkip} hitSlop={8} accessibilityRole="button" accessibilityLabel="Skip the tour">
              <Animated.View style={[styles.skip, skipStyle]}>
                <Text style={styles.skipLabel}>Skip</Text>
              </Animated.View>
            </Pressable>
            <Pressable onPress={onNext} hitSlop={6} accessibilityRole="button" accessibilityLabel="Next tip">
              <Animated.View style={[styles.next, nextStyle]}>
                <Animated.Text style={[styles.nextLabel, nextLabel]}>Next</Animated.Text>
                <Animated.Text style={[styles.nextLabel, styles.nextOver, doneLabel]}>Done</Animated.Text>
              </Animated.View>
            </Pressable>
          </View>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  tip: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: TIP_H,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: BUBBLE,
    boxShadow: '0 2px 6px rgba(8,12,18,0.18), 0 18px 40px rgba(8,12,18,0.28), inset 0 1px 0 rgba(255,255,255,0.95)',
  },
  arrowUp: { position: 'absolute', left: 0, top: -ARROW_H, transformOrigin: 'center bottom' },
  arrowDown: { position: 'absolute', left: 0, top: TIP_H - 1, transformOrigin: 'center top' },
  stepText: { position: 'absolute', left: TIP_PAD, right: TIP_PAD, top: TIP_PAD },
  counter: { height: 16, fontSize: 13, lineHeight: 16, color: MUTE, fontVariant: ['tabular-nums'] },
  title: { marginTop: 6, height: 24, fontSize: 18, lineHeight: 24, fontWeight: '700', color: INK },
  body: { marginTop: 4, height: 40, fontSize: 15, lineHeight: 20, color: '#3D443F' },
  foot: {
    position: 'absolute',
    left: TIP_PAD,
    right: TIP_PAD,
    bottom: TIP_PAD,
    height: FOOT_H,
    flexDirection: 'row',
    alignItems: 'center',
  },
  dots: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { height: 6, borderRadius: 3, backgroundColor: 'rgba(30,36,32,0.16)', overflow: 'hidden' },
  dotOn: { ...StyleSheet.absoluteFill, backgroundColor: ACCENT },
  skip: { width: SKIP_W, height: FOOT_H, alignItems: 'center', justifyContent: 'center' },
  skipLabel: { fontSize: 15, fontWeight: '600', color: MUTE },
  next: {
    marginLeft: 6,
    width: NEXT_W,
    height: FOOT_H,
    borderRadius: FOOT_H / 2,
    backgroundColor: ACCENT,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(20,60,42,0.25), inset 0 1px 0 rgba(255,255,255,0.22)',
  },
  nextLabel: { fontSize: 15, fontWeight: '700', color: '#F4FBF7' },
  nextOver: { position: 'absolute' },
});
