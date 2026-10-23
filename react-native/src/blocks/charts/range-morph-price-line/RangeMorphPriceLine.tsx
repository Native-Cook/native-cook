import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Canvas, LinearGradient, Path, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { CurrencyDisc } from './CurrencyDisc';
import { POINTS, RANGES, type Range, normalized } from './price-series';

export type { Range };

/** autoPlay: 1D rests, then every range in turn, a hold on the last, and a slower morph home. */
export const LOOP = 9;
const FIRST_STEP_AT = 0.9;
const LAST_STEP_AT = 6.15;
const HOME_AT = 7.5;
/** After a touch autoPlay waits this long, then morphs home and plays again. */
const USER_HOLD = 3;

// One morph: every point moves in y while x stays put; going home is a little slower.
const MORPH = 450;
const HOME_MORPH = 600;
const SLIDE = 200;
const COLOR_FOR = 300;
// The old change fades out before the new one fades in, so the two never read over each other.
const DELTA_OUT = 120;
const DELTA_IN = 180;
const SWIPE = 40;
const SMOOTH = Easing.inOut(Easing.cubic);

const BACKGROUND = '#000000';
const INK = '#ffffff';
const MUTE = '#8e8e93';
const RED = '#e0102b';
const GREEN = '#5be02b';
const RED_TEXT = '#ff2d3d';
// The area under the line: the line's hue, dark, deepening to black at the base.
const FILL_RED = ['#3d0008', '#1a0004'];
const FILL_GREEN = ['#1f3d0a', '#0c1804'];
const BADGE = '#4cd91f';
const BUTTON = '#3fd62b';
const SELECTOR = '#1c1c1e';
const SELECTED = '#3a3a3c';

const MARGIN = 24;
const SELECTOR_SIDE = 26;
const SELECTOR_H = 30;
const SELECTOR_INSET = 2;
const SEPARATOR_H = 14;
const LINE = 2;
// The line keeps to this band of the chart's height (from the top); the area runs to its base.
const BAND_TOP = 0.16;
const BAND_BOTTOM = 0.8;
const BUTTON_H = 56;
const BADGE_SIZE = 40;

type Props = {
  title?: string;
  /** The rate now, as shown under the title. */
  price?: string;
  /** The ranges of the selector, each with its own series; every one is drawn with the same point count. */
  ranges?: Range[];
  /** The two buttons at the bottom. */
  from?: string;
  to?: string;
  /** Steps through the ranges by itself, every LOOP seconds. */
  autoPlay?: boolean;
  onRangeChange?: (range: Range, index: number) => void;
  onCurrency?: (code: string) => void;
};

function haptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function Segment({ label, index, slot, onPress }: { label: string; index: number; slot: SharedValue<number>; onPress: () => void }) {
  const style = useAnimatedStyle(() => ({
    color: interpolateColor(Math.max(0, 1 - Math.abs(slot.get() - index)), [0, 1], [MUTE, INK], 'RGB', { gamma: 1 }),
  }));
  return (
    <Pressable style={styles.segment} onPress={onPress} accessibilityRole="tab" accessibilityLabel={label}>
      <Animated.Text style={[styles.segmentLabel, style]}>{label}</Animated.Text>
    </Pressable>
  );
}

function Separator({ index, slot, left }: { index: number; slot: SharedValue<number>; left: number }) {
  // Hidden next to the highlight, as in the system's segmented control.
  const style = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.abs(slot.get() - index), Math.abs(slot.get() - index - 1)),
  }));
  return <Animated.View pointerEvents="none" style={[styles.separator, { left }, style]} />;
}

/**
 * An exchange-rate screen: a jagged price line over a dark area, and a range selector under it.
 * Choosing a range morphs the line into the next series point by point while the area follows,
 * the highlight slides, and the line turns red or green with the sign of the change.
 */
export function RangeMorphPriceLine({
  title = 'USD per 1 EUR',
  price = '$1.0835',
  ranges = RANGES,
  from = 'EUR',
  to = 'USD',
  autoPlay = false,
  onRangeChange,
  onCurrency,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [chart, setChart] = useState({ width: 0, height: 0 });
  const [selectorWidth, setSelectorWidth] = useState(0);
  const [range, setRange] = useState(0);

  const series = useMemo(() => ranges.map((r) => normalized(r.series)), [ranges]);
  const rising = useMemo(() => ranges.map((r) => (r.change >= 0 ? 1 : 0)), [ranges]);
  // autoPlay's steps: when, which range, and how long its morph takes.
  const steps = useMemo(() => {
    const at = [0];
    const index = [0];
    const time = [HOME_MORPH];
    const gap = ranges.length > 2 ? (LAST_STEP_AT - FIRST_STEP_AT) / (ranges.length - 2) : 0;
    for (let i = 1; i < ranges.length; i++) {
      at.push(FIRST_STEP_AT + (i - 1) * gap);
      index.push(i);
      time.push(MORPH);
    }
    at.push(HOME_AT);
    index.push(0);
    time.push(HOME_MORPH);
    return { at, index, time };
  }, [ranges]);

  // The line morphs from `shown` to `target`; a new range starts from wherever the line is.
  const shown = useSharedValue(series[0]);
  const target = useSharedValue(series[0]);
  const progress = useSharedValue(1);
  const slot = useSharedValue(0);
  const up = useSharedValue(rising[0]);
  const selected = useSharedValue(0);
  const auto = useSharedValue(autoPlay);
  const origin = useSharedValue(-1);

  const chosen = (index: number) => {
    setRange(index);
    onRangeChange?.(ranges[index], index);
  };

  const select = (index: number, duration: number) => {
    'worklet';
    const p = progress.get();
    const a = shown.get();
    const b = target.get();
    const now: number[] = [];
    for (let i = 0; i < POINTS; i++) now.push(a[i] + (b[i] - a[i]) * p);
    shown.set(now);
    target.set(series[index]);
    progress.set(0);
    progress.set(still ? 1 : withTiming(1, { duration, easing: SMOOTH }));
    slot.set(still ? index : withTiming(index, { duration: SLIDE, easing: SMOOTH }));
    up.set(still ? rising[index] : withTiming(rising[index], { duration: COLOR_FOR }));
    selected.set(index);
    scheduleOnRN(chosen, index);
  };

  useFrameCallback((info) => {
    if (!auto.get()) return;
    if (origin.get() < 0) origin.set(info.timestamp);
    const t = (((info.timestamp - origin.get()) / 1000) % LOOP + LOOP) % LOOP;
    // The range due now; a step only fires when the line is not already on it.
    let k = 0;
    for (let i = 0; i < steps.at.length; i++) if (steps.at[i] <= t) k = i;
    if (steps.index[k] !== selected.get()) select(steps.index[k], steps.time[k]);
  });

  // A touch takes over at once; some seconds after the last one the loop starts again, and its
  // first step morphs the line home to the first range.
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  useEffect(() => {
    if (!autoPlay || touches === 0) return;
    const replay = setTimeout(() => {
      origin.set(-1);
      auto.set(true);
    }, USER_HOLD * 1000);
    return () => clearTimeout(replay);
  }, [autoPlay, touches, origin, auto]);

  const tap = (index: number) => {
    auto.set(false);
    touched();
    haptic();
    scheduleOnUI(select, index, MORPH);
  };

  const pan = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .failOffsetY([-16, 16])
    .onStart(() => {
      auto.set(false);
    })
    .onEnd((event) => {
      // Swiping left goes on to a longer range, as the selector reads left to right.
      const step = event.translationX < -SWIPE ? 1 : event.translationX > SWIPE ? -1 : 0;
      const next = Math.min(series.length - 1, Math.max(0, selected.get() + step));
      if (next !== selected.get()) {
        select(next, MORPH);
        scheduleOnRN(haptic);
      }
    })
    .onFinalize(() => {
      scheduleOnRN(touched);
    });

  const { width, height } = chart;
  const path = useDerivedValue(() => {
    const a = shown.get();
    const b = target.get();
    const p = progress.get();
    const builder = Skia.PathBuilder.Make();
    const dx = width / (POINTS - 1);
    const top = height * BAND_TOP;
    const span = height * (BAND_BOTTOM - BAND_TOP);
    for (let i = 0; i < POINTS; i++) {
      const y = top + (1 - (a[i] + (b[i] - a[i]) * p)) * span;
      if (i === 0) builder.moveTo(0, y);
      else builder.lineTo(i * dx, y);
    }
    return builder.build();
  });
  const area = useDerivedValue(() => {
    const line = path.get();
    const builder = Skia.PathBuilder.Make();
    builder.addPath(line);
    builder.lineTo(width, height).lineTo(0, height).close();
    return builder.build();
  });
  const lineColor = useDerivedValue(() => interpolateColor(up.get(), [0, 1], [RED, GREEN], 'RGB', { gamma: 1 }));
  const fillColors = useDerivedValue(() => [
    interpolateColor(up.get(), [0, 1], [FILL_RED[0], FILL_GREEN[0]], 'RGB', { gamma: 1 }),
    interpolateColor(up.get(), [0, 1], [FILL_RED[1], FILL_GREEN[1]], 'RGB', { gamma: 1 }),
  ]);

  const segment = (selectorWidth - SELECTOR_INSET * 2) / ranges.length;
  const highlight = useAnimatedStyle(() => ({ transform: [{ translateX: SELECTOR_INSET + slot.get() * segment }] }));

  const current = ranges[range];
  const rose = current.change >= 0;
  const delta = `${rose ? '↑' : '↓'} ${Math.abs(current.change).toFixed(2)}%`;

  const onChartLayout = (event: LayoutChangeEvent) => {
    const layout = event.nativeEvent.layout;
    setChart({ width: layout.width, height: layout.height });
  };

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.header, { paddingTop: insets.top + 20 }]}>
        <View style={styles.titleRow}>
          <Text style={styles.title} numberOfLines={1}>
            {title}
          </Text>
          <View style={styles.badge}>
            <MaterialCommunityIcons name="sine-wave" size={24} color={INK} />
          </View>
        </View>
        <View style={styles.priceRow}>
          <Text style={styles.price}>{price}</Text>
          <View style={styles.deltaSlot} accessible accessibilityLabel={`${rose ? 'Up' : 'Down'} ${Math.abs(current.change)} percent over ${current.label}`}>
            <Animated.Text
              key={current.label}
              entering={FadeIn.duration(still ? 0 : DELTA_IN).delay(still ? 0 : DELTA_OUT)}
              exiting={FadeOut.duration(still ? 0 : DELTA_OUT)}
              style={[styles.delta, { color: rose ? GREEN : RED_TEXT }]}
            >
              {delta}
            </Animated.Text>
          </View>
        </View>
      </View>

      <GestureDetector gesture={pan}>
        <View style={styles.chart} onLayout={onChartLayout} accessible accessibilityLabel={`${title}, ${current.label}`}>
          {width > 0 && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Path path={area}>
                <LinearGradient start={vec(0, height * BAND_TOP)} end={vec(0, height)} colors={fillColors} />
              </Path>
              <Path path={path} style="stroke" strokeWidth={LINE} strokeJoin="round" strokeCap="round" color={lineColor} />
            </Canvas>
          )}
        </View>
      </GestureDetector>

      <View style={styles.selector} onLayout={(event) => setSelectorWidth(event.nativeEvent.layout.width)} accessibilityRole="tablist">
        {selectorWidth > 0 && (
          <>
            <Animated.View style={[styles.highlight, { width: segment }, highlight]} />
            {ranges.slice(1).map((r, i) => (
              <Separator key={r.label} index={i} slot={slot} left={SELECTOR_INSET + (i + 1) * segment} />
            ))}
          </>
        )}
        {ranges.map((r, index) => (
          <Segment key={r.label} label={r.label} index={index} slot={slot} onPress={() => tap(index)} />
        ))}
      </View>

      <View style={[styles.buttons, { paddingBottom: Math.max(insets.bottom, 16) + 8 }]}>
        {[from, to].map((code) => (
          <Pressable
            key={code}
            style={styles.buttonSlot}
            accessibilityRole="button"
            onPress={() => {
              auto.set(false);
              touched();
              onCurrency?.(code);
            }}
          >
            {({ pressed }) => (
              <View style={[styles.button, { opacity: pressed ? 0.7 : 1 }]}>
                <CurrencyDisc kind={code === 'USD' ? 'usd' : 'eur'} />
                <Text style={styles.buttonLabel}>{code}</Text>
              </View>
            )}
          </Pressable>
        ))}
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  header: {
    paddingHorizontal: MARGIN,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    flex: 1,
    color: INK,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  badge: {
    width: BADGE_SIZE,
    height: BADGE_SIZE,
    borderRadius: BADGE_SIZE / 2,
    backgroundColor: BADGE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 6,
  },
  price: {
    color: MUTE,
    fontSize: 20,
    lineHeight: 25,
    fontVariant: ['tabular-nums'],
  },
  deltaSlot: {
    marginLeft: 16,
    height: 25,
    minWidth: 100,
  },
  delta: {
    position: 'absolute',
    left: 0,
    top: 0,
    fontSize: 20,
    lineHeight: 25,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  chart: {
    flex: 1,
    marginTop: 24,
  },
  selector: {
    flexDirection: 'row',
    height: SELECTOR_H,
    marginTop: 28,
    marginHorizontal: SELECTOR_SIDE,
    paddingHorizontal: SELECTOR_INSET,
    borderRadius: 8,
    borderCurve: 'continuous',
    backgroundColor: SELECTOR,
  },
  highlight: {
    position: 'absolute',
    top: SELECTOR_INSET,
    bottom: SELECTOR_INSET,
    left: 0,
    borderRadius: 6,
    borderCurve: 'continuous',
    backgroundColor: SELECTED,
  },
  separator: {
    position: 'absolute',
    top: (SELECTOR_H - SEPARATOR_H) / 2,
    width: StyleSheet.hairlineWidth * 2,
    height: SEPARATOR_H,
    backgroundColor: SELECTED,
  },
  segment: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentLabel: {
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '500',
  },
  buttons: {
    flexDirection: 'row',
    gap: 20,
    paddingTop: 28,
    paddingHorizontal: SELECTOR_SIDE,
  },
  buttonSlot: {
    flex: 1,
  },
  button: {
    height: BUTTON_H,
    borderRadius: BUTTON_H / 2,
    backgroundColor: BUTTON,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  buttonLabel: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
});
