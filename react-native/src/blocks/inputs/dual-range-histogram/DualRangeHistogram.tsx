import { Canvas, Group, LinearGradient, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, type AccessibilityActionEvent } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  BAR_GAP,
  BAR_RADIUS,
  clamp,
  constrain,
  filmGhost,
  filmGhostSide,
  filmHigh,
  filmLow,
  filmPressHigh,
  filmPressLow,
  FLING_S,
  GRAB_R,
  IDLE_S,
  LIFT_PT,
  LIFT_SIGMA,
  LOOP,
  makeFilm,
  mix,
  MODE_AUTO,
  MODE_MANUAL,
  MODE_RETURN,
  PRESS_SPRING,
  rangeCount,
  RELEASE_SPRING,
  RETURN_S,
  seg,
  shownHigh,
  shownLow,
  SINK,
  smooth,
  SQUASH_X,
  SQUASH_Y,
  TAG_GAP,
  TAG_H,
  TAG_W,
  TAP_SLOP,
  THUMB,
  TRACK_AREA,
  TRACK_H,
} from './dualRangeMotion';
import { RollingDigit } from './RollingDigit';

const INSET = THUMB / 2;
const MORPH_MS = 420;
const COUNT_MS = 220;
const TICK_PRICE = 250;
const TICK_MIN_S = 0.07;
const EASE_MORPH = Easing.bezier(0.65, 0, 0.35, 1);

const C = {
  muted: '#ddd6ca',
  track: '#e4ddd1',
  fillTop: '#e8703f',
  fillMid: '#d9542f',
  fillLow: '#b83f2a',
  thumb: '#fffdf9',
  thumbEdge: '#e6dfd3',
  ink: '#2a2420',
  tag: '#2a2420',
  tagInk: '#fbf6ee',
  ghost: 'rgba(42,36,32,0.2)',
};

export type DualRangeHistogramProps = {
  /** Listings per price bin, evenly spaced from `min` to `max`. A new array morphs the bars. */
  bins: readonly number[];
  min: number;
  max: number;
  /** Thumbs land on multiples of this. */
  step: number;
  /** The smallest allowed distance between the thumbs, in price. */
  minGap: number;
  defaultValue: readonly [number, number];
  width: number;
  chartHeight: number;
  autoPlay?: boolean;
  /** Receives the number of listings in the range, rolled to whole numbers. */
  count?: SharedValue<number>;
  /** Change it to send the thumbs back to `defaultValue`. */
  resetKey?: number;
  onChange?: (low: number, high: number) => void;
};

function tickLight() {
  void Haptics.selectionAsync();
}

function pressLight() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function put(sv: SharedValue<number>, v: number) {
  'worklet';
  if (sv.get() !== v) sv.set(v);
}

function retargetBins(from: SharedValue<number[]>, to: SharedValue<number[]>, morph: SharedValue<number>, next: number[]) {
  'worklet';
  const a = from.get();
  const b = to.get();
  const k = morph.get();
  const current: number[] = [];
  for (let i = 0; i < next.length; i += 1) current.push(mix(a[i] ?? 0, b[i] ?? 0, k));
  from.set(current);
  to.set(next);
  morph.set(0);
  morph.set(withTiming(1, { duration: MORPH_MS, easing: EASE_MORPH }));
}

function buildBars(
  a: readonly number[],
  b: readonly number[],
  morph: number,
  low: number,
  high: number,
  pressLow: number,
  pressHigh: number,
  width: number,
  chartH: number,
) {
  'worklet';
  const n = b.length;
  const span = width - INSET * 2;
  const slot = span / n;
  const barW = Math.max(1, slot - BAR_GAP);
  const lowX = INSET + low * span;
  const highX = INSET + high * span;
  let peak = 1;
  for (let i = 0; i < n; i += 1) peak = Math.max(peak, mix(a[i] ?? 0, b[i] ?? 0, morph));
  const room = chartH - LIFT_PT - 2;
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < n; i += 1) {
    const cx = INSET + (i + 0.5) * slot;
    const inside = smooth(seg(cx, lowX - slot, lowX + slot)) * (1 - smooth(seg(cx, highX - slot, highX + slot)));
    const dl = (cx - lowX) / LIFT_SIGMA;
    const dh = (cx - highX) / LIFT_SIGMA;
    const lift = LIFT_PT * (pressLow * Math.exp(-dl * dl) + pressHigh * Math.exp(-dh * dh));
    const value = mix(a[i] ?? 0, b[i] ?? 0, morph) / peak;
    const h = Math.max(3, room * value * mix(SINK, 1, inside) + lift);
    const r = Math.min(BAR_RADIUS, barW / 2);
    builder.addRRect(Skia.RRectXY(Skia.XYWHRect(cx - barW / 2, chartH - h, barW, h), r, r));
  }
  return builder.detach();
}

/**
 * A price histogram over a two-thumb range. Bars inside the range take the colour, bars outside drain and sink, the
 * labels ride the thumbs and roll their digits, and the thumbs squash when held and refuse to cross with a rubber band.
 */
export function DualRangeHistogram({
  bins,
  min,
  max,
  step,
  minGap,
  defaultValue,
  width,
  chartHeight,
  autoPlay = true,
  count,
  resetKey = 0,
  onChange,
}: DualRangeHistogramProps) {
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const range = max - min;
  const gap = minGap / range;
  const stepU = step / range;
  const restLow = (defaultValue[0] - min) / range;
  const restHigh = (defaultValue[1] - min) / range;
  const plan = useMemo(() => makeFilm(restLow, restHigh, gap), [restLow, restHigh, gap]);
  const span = width - INSET * 2;

  const [settled, setSettled] = useState<readonly [number, number]>(defaultValue);

  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const holding = useSharedValue(0);

  const low = useSharedValue(restLow);
  const high = useSharedValue(restHigh);
  const pressLow = useSharedValue(0);
  const pressHigh = useSharedValue(0);
  const ghost = useSharedValue(0);
  const ghostSide = useSharedValue(0);

  const grabbed = useSharedValue(-1); // 0 low, 1 high, -1 none yet
  const nearest = useSharedValue(0);
  const startX = useSharedValue(0);
  const grabOffset = useSharedValue(0);
  const lastTick = useSharedValue(0);

  const binsFrom = useSharedValue<number[]>([...bins]);
  const binsTo = useSharedValue<number[]>([...bins]);
  const morph = useSharedValue(1);
  const firstBins = useRef(true);

  useEffect(() => {
    if (firstBins.current) {
      firstBins.current = false;
      return;
    }
    scheduleOnUI(retargetBins, binsFrom, binsTo, morph, [...bins]);
  }, [bins, binsFrom, binsTo, morph]);

  const firstReset = useRef(true);
  useEffect(() => {
    if (firstReset.current) {
      firstReset.current = false;
      return;
    }
    low.set(withSpring(restLow, RELEASE_SPRING));
    high.set(withSpring(restHigh, RELEASE_SPRING));
    setSettled([min + restLow * range, min + restHigh * range]);
  }, [resetKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const report = useCallback(
    (a: number, b: number) => {
      setSettled([a, b]);
      onChange?.(a, b);
    },
    [onChange],
  );

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp / 1000;
    clock.set(now);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        put(ghost, 0);
        put(pressLow, 0);
        put(pressHigh, 0);
        low.set(restLow);
        high.set(restHigh);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      const t = (now - loopStart.get()) % LOOP;
      put(low, filmLow(t, plan));
      put(high, filmHigh(t, plan));
      put(pressLow, filmPressLow(t));
      put(pressHigh, filmPressHigh(t));
      put(ghost, filmGhost(t));
      put(ghostSide, filmGhostSide(t));
    } else if (m === MODE_MANUAL) {
      if (film && holding.get() === 0 && now - lastTouch.get() > IDLE_S) {
        mode.set(MODE_RETURN);
        returnAt.set(now + RETURN_S);
        low.set(withSpring(restLow, RELEASE_SPRING));
        high.set(withSpring(restHigh, RELEASE_SPRING));
        scheduleOnRN(report, min + restLow * range, min + restHigh * range);
      }
    } else if (now >= returnAt.get()) {
      cancelAnimation(low);
      cancelAnimation(high);
      low.set(restLow);
      high.set(restHigh);
      loopStart.set(now);
      mode.set(MODE_AUTO);
    }
  });

  const lowU = useDerivedValue(() => shownLow(low.get(), high.get(), gap));
  const highU = useDerivedValue(() => shownHigh(low.get(), high.get(), gap));
  const lowPrice = useDerivedValue(() => min + clamp(lowU.get(), 0, 1) * range);
  const highPrice = useDerivedValue(() => min + clamp(highU.get(), 0, 1) * range);

  const fallbackCount = useSharedValue(0);
  const countOut = count ?? fallbackCount;
  useAnimatedReaction(
    () => Math.round(rangeCount(binsFrom.get(), binsTo.get(), morph.get(), lowU.get(), highU.get())),
    (v, prev) => {
      if (prev === null) countOut.set(v);
      else if (v !== prev) countOut.set(withTiming(v, { duration: COUNT_MS, easing: EASE_MORPH }));
    },
  );

  // A selection tick each $250 under a held thumb and one at the limit.
  useAnimatedReaction(
    () => {
      if (holding.get() === 0 || mode.get() !== MODE_MANUAL) return -1;
      const side = grabbed.get();
      if (side < 0) return -1;
      const atLimit = side === 0 ? low.get() < 0 || low.get() > high.get() - gap : high.get() > 1 || high.get() < low.get() + gap;
      const price = side === 0 ? lowPrice.get() : highPrice.get();
      return Math.floor(price / TICK_PRICE) * 2 + (atLimit ? 1 : 0);
    },
    (key, prev) => {
      if (key < 0 || prev === null || prev < 0 || key === prev) return;
      const now = clock.get();
      if (now - lastTick.get() < TICK_MIN_S) return;
      lastTick.set(now);
      scheduleOnRN(tickLight);
    },
  );

  const bars = useDerivedValue(() =>
    buildBars(
      binsFrom.get(),
      binsTo.get(),
      morph.get(),
      lowU.get(),
      highU.get(),
      pressLow.get(),
      pressHigh.get(),
      width,
      chartHeight,
    ),
  );
  const clip = useDerivedValue(() => {
    const x = INSET + lowU.get() * span;
    return Skia.XYWHRect(x, 0, Math.max(0, INSET + highU.get() * span - x), chartHeight);
  });
  const fillX = useDerivedValue(() => INSET + lowU.get() * span);
  const fillW = useDerivedValue(() => Math.max(0, (highU.get() - lowU.get()) * span));

  const trackY = chartHeight + TRACK_AREA / 2 - TRACK_H / 2;

  const lowThumb = useAnimatedStyle(() => {
    const p = clamp(pressLow.get(), 0, 1);
    return {
      transform: [
        { translateX: INSET + lowU.get() * span - THUMB / 2 },
        { scaleX: mix(1, SQUASH_X, p) },
        { scaleY: mix(1, SQUASH_Y, p) },
      ],
    };
  });
  const highThumb = useAnimatedStyle(() => {
    const p = clamp(pressHigh.get(), 0, 1);
    return {
      transform: [
        { translateX: INSET + highU.get() * span - THUMB / 2 },
        { scaleX: mix(1, SQUASH_X, p) },
        { scaleY: mix(1, SQUASH_Y, p) },
      ],
    };
  });

  // The labels ride their thumbs and move apart instead of overlapping.
  const tagLow = useDerivedValue(() => {
    const l = INSET + lowU.get() * span - TAG_W / 2;
    const r = INSET + highU.get() * span - TAG_W / 2;
    const left = r - l < TAG_W + TAG_GAP ? (l + r) / 2 - (TAG_W + TAG_GAP) / 2 : l;
    return clamp(left, 0, width - TAG_W * 2 - TAG_GAP);
  });
  const tagHigh = useDerivedValue(() => {
    const l = INSET + lowU.get() * span - TAG_W / 2;
    const r = INSET + highU.get() * span - TAG_W / 2;
    const right = r - l < TAG_W + TAG_GAP ? (l + r) / 2 + (TAG_W + TAG_GAP) / 2 : r;
    return clamp(right, tagLow.get() + TAG_W + TAG_GAP, width - TAG_W);
  });
  const tagLowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: tagLow.get() }] }));
  const tagHighStyle = useAnimatedStyle(() => ({ transform: [{ translateX: tagHigh.get() }] }));

  const ghostStyle = useAnimatedStyle(() => {
    const u = ghostSide.get() === 0 ? lowU.get() : highU.get();
    const p = ghostSide.get() === 0 ? pressLow.get() : pressHigh.get();
    return {
      opacity: clamp(ghost.get(), 0, 1) * 0.9,
      transform: [{ translateX: INSET + u * span - 23 }, { scale: mix(1.12, 0.94, clamp(p, 0, 1)) }],
    };
  });

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      mode.set(MODE_MANUAL);
      holding.set(1);
      lastTouch.set(clock.get());
      ghost.set(0);
      cancelAnimation(low);
      cancelAnimation(high);
      const lx = INSET + lowU.get() * span;
      const hx = INSET + highU.get() * span;
      const dl = Math.abs(e.x - lx);
      const dh = Math.abs(e.x - hx);
      // Thumbs in contact: the side the finger is on decides.
      const side = Math.abs(dl - dh) < 2 ? (e.x > (lx + hx) / 2 ? 1 : 0) : dl < dh ? 0 : 1;
      nearest.set(side);
      startX.set(e.x);
      pressLow.set(0);
      pressHigh.set(0);
      if (Math.min(dl, dh) <= GRAB_R) {
        grabbed.set(side);
        grabOffset.set((side === 0 ? lx : hx) - e.x);
        (side === 0 ? pressLow : pressHigh).set(withSpring(1, PRESS_SPRING));
        scheduleOnRN(pressLight);
      } else {
        grabbed.set(-1);
        grabOffset.set(0);
      }
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (grabbed.get() < 0) {
        if (Math.abs(e.x - startX.get()) <= TAP_SLOP) return;
        const side = nearest.get();
        grabbed.set(side);
        (side === 0 ? pressLow : pressHigh).set(withSpring(1, PRESS_SPRING));
        scheduleOnRN(pressLight);
      }
      const u = (e.x + grabOffset.get() - INSET) / span;
      if (grabbed.get() === 0) low.set(constrain(u, 0, high.get() - gap));
      else high.set(constrain(u, low.get() + gap, 1));
    })
    .onFinalize((e) => {
      holding.set(0);
      lastTouch.set(clock.get());
      pressLow.set(withSpring(0, PRESS_SPRING));
      pressHigh.set(withSpring(0, PRESS_SPRING));
      let side = grabbed.get();
      let target: number;
      let velocity = 0;
      if (side < 0) {
        // A tap on the chart: the nearest thumb jumps to the bar under the finger.
        side = nearest.get();
        const n = binsTo.get().length;
        const bin = clamp(Math.floor(((e.x - INSET) / span) * n), 0, n - 1);
        target = (bin + 0.5) / n;
        scheduleOnRN(pressLight);
      } else {
        velocity = e.velocityX / span;
        target = (side === 0 ? low.get() : high.get()) + velocity * FLING_S;
      }
      target = Math.round(target / stepU) * stepU;
      const lowNow = low.get();
      const highNow = high.get();
      if (side === 0) {
        target = clamp(target, 0, Math.floor((highNow - gap) / stepU + 1e-6) * stepU);
        low.set(withSpring(target, { ...RELEASE_SPRING, velocity }));
        scheduleOnRN(report, min + target * range, min + highNow * range);
      } else {
        target = clamp(target, Math.ceil((lowNow + gap) / stepU - 1e-6) * stepU, 1);
        high.set(withSpring(target, { ...RELEASE_SPRING, velocity }));
        scheduleOnRN(report, min + lowNow * range, min + target * range);
      }
      grabbed.set(-1);
    });

  const nudge = useCallback(
    (side: 0 | 1, direction: number) => {
      mode.set(MODE_MANUAL);
      lastTouch.set(clock.get());
      const a = low.get();
      const b = high.get();
      if (side === 0) {
        const next = clamp(a + direction * stepU * 2, 0, b - gap);
        low.set(withSpring(next, RELEASE_SPRING));
        report(min + next * range, min + b * range);
      } else {
        const next = clamp(b + direction * stepU * 2, a + gap, 1);
        high.set(withSpring(next, RELEASE_SPRING));
        report(min + a * range, min + next * range);
      }
    },
    [clock, gap, high, lastTouch, low, min, mode, range, report, stepU],
  );
  const onLowAction = useCallback(
    (e: AccessibilityActionEvent) => nudge(0, e.nativeEvent.actionName === 'increment' ? 1 : -1),
    [nudge],
  );
  const onHighAction = useCallback(
    (e: AccessibilityActionEvent) => nudge(1, e.nativeEvent.actionName === 'increment' ? 1 : -1),
    [nudge],
  );

  const height = chartHeight + TRACK_AREA + TAG_H + 4;
  const thumbTop = chartHeight + (TRACK_AREA - THUMB) / 2;
  const actions = [{ name: 'increment' }, { name: 'decrement' }];

  return (
    <GestureDetector gesture={pan}>
      <View style={{ width, height }}>
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Canvas style={{ width, height: chartHeight + TRACK_AREA }}>
            <Path color={C.muted} path={bars} />
            <Group clip={clip}>
              <Path path={bars}>
                <LinearGradient colors={[C.fillTop, C.fillMid, C.fillLow]} end={vec(0, chartHeight)} positions={[0, 0.55, 1]} start={vec(0, 0)} />
              </Path>
            </Group>
            <RoundedRect color={C.track} height={TRACK_H} r={TRACK_H / 2} width={span} x={INSET} y={trackY} />
            <RoundedRect color={C.fillMid} height={TRACK_H} r={TRACK_H / 2} width={fillW} x={fillX} y={trackY} />
          </Canvas>
        </View>

        <Animated.View
          accessibilityActions={actions}
          accessibilityLabel="Minimum price"
          accessibilityRole="adjustable"
          accessibilityValue={{ text: `$${Math.round(settled[0]).toLocaleString('en-US')}` }}
          accessible
          onAccessibilityAction={onLowAction}
          style={[styles.thumb, { top: thumbTop }, lowThumb]}
        >
          <View style={styles.thumbDot} />
        </Animated.View>
        <Animated.View
          accessibilityActions={actions}
          accessibilityLabel="Maximum price"
          accessibilityRole="adjustable"
          accessibilityValue={{ text: `$${Math.round(settled[1]).toLocaleString('en-US')}` }}
          accessible
          onAccessibilityAction={onHighAction}
          style={[styles.thumb, { top: thumbTop }, highThumb]}
        >
          <View style={styles.thumbDot} />
        </Animated.View>

        <Animated.View pointerEvents="none" style={[styles.ghost, { top: chartHeight + (TRACK_AREA - 46) / 2 }, ghostStyle]} />

        <Animated.View pointerEvents="none" style={[styles.tag, { top: chartHeight + TRACK_AREA + 2 }, tagLowStyle]}>
          <PriceDigits value={lowPrice} />
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.tag, { top: chartHeight + TRACK_AREA + 2 }, tagHighStyle]}>
          <PriceDigits value={highPrice} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const DIGIT_H = 18;
const DIGIT_W = 8.6;

function PriceDigits({ value }: { value: SharedValue<number> }) {
  return (
    // The thumbs carry the price for VoiceOver; the odometer columns would read as dozens of loose digits.
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.digits}>
      <Text allowFontScaling={false} style={styles.tagText}>$</Text>
      <RollingDigit height={DIGIT_H} place={1000} textStyle={styles.tagText} value={value} width={DIGIT_W} />
      <Text allowFontScaling={false} style={styles.tagText}>,</Text>
      <RollingDigit height={DIGIT_H} place={100} textStyle={styles.tagText} value={value} width={DIGIT_W} />
      <RollingDigit height={DIGIT_H} lowest place={10} textStyle={styles.tagText} value={value} width={DIGIT_W} />
      <Text allowFontScaling={false} style={styles.tagText}>0</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  thumb: {
    position: 'absolute',
    left: 0,
    width: THUMB,
    height: THUMB,
    borderRadius: THUMB / 2,
    backgroundColor: C.thumb,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: C.thumbEdge,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 3px rgba(60,40,25,0.28), 0 6px 16px rgba(60,40,25,0.16)',
  },
  thumbDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: C.fillMid },
  ghost: { position: 'absolute', left: 0, width: 46, height: 46, borderRadius: 23, backgroundColor: C.ghost },
  tag: {
    position: 'absolute',
    left: 0,
    width: TAG_W,
    height: TAG_H,
    borderRadius: TAG_H / 2,
    backgroundColor: C.tag,
    alignItems: 'center',
    justifyContent: 'center',
  },
  digits: { flexDirection: 'row', alignItems: 'center', height: DIGIT_H },
  tagText: { color: C.tagInk, fontSize: 13.5, fontWeight: '600', lineHeight: DIGIT_H, fontVariant: ['tabular-nums'] },
});
