import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, type TextInputProps, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { GREEN, GlowChart } from './GlowChart';
import { LOOP, RANGES, type Range, USER_HOLD, clamp01, dateLabel, filmAt, grouped, priceAt } from './glow-series';
import { RollingPrice } from './RollingPrice';

export type { Range };
export { LOOP };

type Props = {
  symbol?: string;
  name?: string;
  currency?: string;
  /** The letter on the coin's badge. */
  mark?: string;
  disclaimer?: string;
  footnote?: string;
  ranges?: Range[];
  /** The range shown first, by its place in `ranges`. */
  initialRange?: number;
  /** Scrubs the chart and redraws the line by itself, every LOOP seconds. */
  autoPlay?: boolean;
  onScrub?: (price: number | null, time: number | null) => void;
  onRangeChange?: (range: Range, index: number) => void;
  onClose?: () => void;
};

const PAGE = '#1b1b1d';
const WELL = '#242426';
const PILLS = '#2a2a2d';
const PILL_ON = '#3a3a3d';
const INK = '#ffffff';
const SOFT = '#c7c7cc';
const MUTE = '#8a8a8e';
const FAINT = '#6c6c70';
const RED = '#ff5a5f';
const COIN = '#f7931a';

const MARGIN = 16;
const BAR = 44;
const COIN_SIZE = 48;
// The chart runs from the left margin to a gutter on the right that holds the high and low prices.
const GUTTER = 88;
// The opening price's tag sits under its rule, or over it when there is no room below.
const TAG_H = 23;
const TAG_GAP = 8;
const CHART_PAD = 16;
const CHART_MIN = 220;
const PILLS_H = 44;
const PILL_INSET = 4;
// A haptic tick each time the finger travels this far.
const TICK_EVERY = 12;
const HELD_IN = 150;
const HELD_OUT = 250;
const DRAW_FOR = 1000;
const SMOOTH = Easing.inOut(Easing.cubic);

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

/** Text set from the UI thread, so a price that changes every frame costs no React render. */
function LiveText({ text, style }: { text: SharedValue<string>; style: ComponentProps<typeof AnimatedTextInput>['style'] }) {
  const animatedProps = useAnimatedProps(() => ({ text: text.get(), defaultValue: text.get() }) as TextInputProps);
  return (
    <AnimatedTextInput
      editable={false}
      pointerEvents="none"
      accessible={false}
      underlineColorAndroid="transparent"
      style={[styles.live, style]}
      animatedProps={animatedProps}
    />
  );
}

function haptic(kind: 'tick' | 'tap') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A coin's price screen. Press and drag on the chart: a thin cursor follows the finger and where
 * it meets the line the line lights up like a fuse, with a bloom and a halo riding exactly on the
 * curve while the rest of the line steps back. The price rolls, and the change and the date follow.
 */
export function GlowTipScrubber({
  symbol = 'EMB',
  name = 'Ember coin',
  currency = '£',
  mark = 'E',
  disclaimer = 'Prices on this screen are made up for illustration. Crypto prices move fast and you could lose all the money you put in.',
  footnote = 'This is the midpoint between buy and sell rates. The rate you get depends on whether you buy or sell.',
  ranges = RANGES,
  initialRange = 1,
  autoPlay = false,
  onScrub,
  onRangeChange,
  onClose,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [chart, setChart] = useState({ width: 0, height: 0 });
  const [range, setRange] = useState(initialRange);
  const [pillsWidth, setPillsWidth] = useState(0);
  const extremes = useMemo(
    () =>
      ranges.map((r) => {
        let low = Infinity;
        let high = -Infinity;
        for (const p of r.prices) {
          low = Math.min(low, p);
          high = Math.max(high, p);
        }
        return { low, high };
      }),
    [ranges],
  );

  const spans = useMemo(() => ranges.map((r) => r.span), [ranges]);

  const prices = useSharedValue(ranges[initialRange].prices);
  const low = useSharedValue(extremes[initialRange].low);
  const high = useSharedValue(extremes[initialRange].high);
  const times = useSharedValue({ start: ranges[initialRange].start, end: ranges[initialRange].end, hours: ranges[initialRange].hours });
  const tip = useSharedValue(0);
  const held = useSharedValue(0);
  const down = useSharedValue(0);
  const lit = useSharedValue(0);
  const reveal = useSharedValue(1);
  const shown = useSharedValue(1);
  const marks = useSharedValue(1);
  const slot = useSharedValue(initialRange);
  const current = useSharedValue(initialRange);
  const lastTick = useSharedValue(0);
  const auto = useSharedValue(autoPlay);
  const origin = useSharedValue(-1);

  // What the header reads: the price under the finger, or the latest one.
  const price = useDerivedValue(() => {
    const p = prices.get();
    return down.get() ? priceAt(p, tip.get()) : p[p.length - 1];
  });
  const change = useDerivedValue(() => price.get() - prices.get()[0]);
  const changeText = useDerivedValue(() => `${change.get() < 0 ? '−' : '+'}${currency}${grouped(change.get())}`);
  const percentText = useDerivedValue(() => {
    const open = prices.get()[0];
    return `${change.get() < 0 ? '▼' : '▲'} ${Math.abs((change.get() / open) * 100).toFixed(2)}%`;
  });
  const dateText = useDerivedValue(() => {
    const span = times.get();
    return down.get() ? dateLabel(span.start + clamp01(tip.get()) * (span.end - span.start), span.hours) : spans[current.get()];
  });
  const percentStyle = useAnimatedStyle(() => ({ color: change.get() < 0 ? RED : GREEN }));
  const tagStyle = useAnimatedStyle(() => {
    const p = prices.get();
    const span = high.get() - low.get() || 1;
    const rule = CHART_PAD + (1 - (p[0] - low.get()) / span) * (chart.height - CHART_PAD * 2);
    const below = rule + TAG_GAP + TAG_H <= chart.height;
    return { opacity: marks.get(), transform: [{ translateY: below ? rule + TAG_GAP : rule - TAG_GAP - TAG_H }] };
  });
  const highLowStyle = useAnimatedStyle(() => ({ opacity: 0.4 + 0.6 * marks.get() }));
  const segment = (pillsWidth - PILL_INSET * 2) / ranges.length;
  const pillStyle = useAnimatedStyle(() => ({ transform: [{ translateX: PILL_INSET + slot.get() * segment }] }));

  /** A new range; `draw` has its line draw itself in from the left behind the glowing tip. */
  const show = (index: number, draw: boolean) => {
    'worklet';
    const next = ranges[index];
    current.set(index);
    prices.set(next.prices);
    low.set(extremes[index].low);
    high.set(extremes[index].high);
    times.set({ start: next.start, end: next.end, hours: next.hours });
    slot.set(still ? index : withTiming(index, { duration: 220, easing: SMOOTH }));
    shown.set(1);
    if (still || !draw) {
      reveal.set(1);
      return;
    }
    tip.set(0);
    reveal.set(0);
    reveal.set(withTiming(1, { duration: DRAW_FOR, easing: Easing.linear }));
    tip.set(withTiming(1, { duration: DRAW_FOR, easing: Easing.linear }));
    lit.set(1);
    lit.set(withTiming(0, { duration: DRAW_FOR + 250, easing: Easing.in(Easing.cubic) }));
    marks.set(0);
    marks.set(withTiming(1, { duration: DRAW_FOR + 300, easing: Easing.in(Easing.cubic) }));
  };

  useFrameCallback((info) => {
    if (!auto.get()) return;
    if (origin.get() < 0) {
      origin.set(info.timestamp);
      // The loop always plays on the range it started with.
      if (current.get() !== initialRange) {
        show(initialRange, false);
        scheduleOnRN(setRange, initialRange);
      }
    }
    const t = (((info.timestamp - origin.get()) / 1000) % LOOP + LOOP) % LOOP;
    const film = filmAt(t, still);
    // Only what changed is written, so the still stretches of the loop do not repaint.
    if (tip.get() !== film.tip) tip.set(film.tip);
    if (held.get() !== film.held) held.set(film.held);
    if (down.get() !== film.down) down.set(film.down);
    if (lit.get() !== film.lit) lit.set(film.lit);
    if (reveal.get() !== film.reveal) reveal.set(film.reveal);
    if (shown.get() !== film.shown) shown.set(film.shown);
    if (marks.get() !== film.marks) marks.set(clamp01(film.marks));
  });

  // A touch takes over at once; some seconds after the last one the loop plays again, on the
  // range it started with.
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  useEffect(() => {
    if (!autoPlay || touches === 0) return;
    const replay = setTimeout(() => {
      if (down.get()) return;
      origin.set(-1);
      auto.set(true);
    }, USER_HOLD * 1000);
    return () => clearTimeout(replay);
  }, [autoPlay, touches, down, origin, auto]);

  const scrubbed = (value: number | null, time: number | null) => onScrub?.(value, time);

  const pan = Gesture.Pan()
    .minDistance(0)
    .shouldCancelWhenOutside(false)
    .onBegin((event) => {
      auto.set(false);
      // A finger during a redraw finishes it at once.
      reveal.set(1);
      shown.set(1);
      tip.set(clamp01(event.x / chart.width));
      lastTick.set(event.x);
      down.set(1);
      held.set(still ? 1 : withTiming(1, { duration: HELD_IN }));
      lit.set(still ? 1 : withTiming(1, { duration: HELD_IN }));
      marks.set(still ? 0 : withTiming(0, { duration: HELD_IN }));
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      tip.set(clamp01(event.x / chart.width));
      if (Math.abs(event.x - lastTick.get()) >= TICK_EVERY) {
        lastTick.set(event.x);
        scheduleOnRN(haptic, 'tick');
      }
      const span = times.get();
      scheduleOnRN(scrubbed, price.get(), span.start + tip.get() * (span.end - span.start));
    })
    .onFinalize(() => {
      down.set(0);
      held.set(still ? 0 : withTiming(0, { duration: HELD_OUT }));
      lit.set(still ? 0 : withTiming(0, { duration: HELD_OUT }));
      marks.set(still ? 1 : withTiming(1, { duration: HELD_OUT * 2 }));
      scheduleOnRN(scrubbed, null, null);
      scheduleOnRN(touched);
    });

  /** A range picked by hand: autoPlay stops where it is, lifting the finger it may have had down. */
  const choose = (index: number) => {
    'worklet';
    auto.set(false);
    down.set(0);
    held.set(still ? 0 : withTiming(0, { duration: HELD_OUT }));
    show(index, true);
  };

  const pick = (index: number) => {
    touched();
    haptic('tap');
    setRange(index);
    onRangeChange?.(ranges[index], index);
    scheduleOnUI(choose, index);
  };

  const onChartLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setChart({ width: width - GUTTER, height });
  };

  const priceFont = { fontSize: 34, lineHeight: 41, fontWeight: '600' as const, color: INK, letterSpacing: -0.4 };
  const digits = String(Math.floor(extremes[range].high)).length;
  const pattern = `${currency}${'0'.repeat(digits).replace(/\B(?=(0{3})+$)/g, ',')}.00`;

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.bar, { marginTop: insets.top }]}>
        <Pressable hitSlop={10} accessibilityRole="button" accessibilityLabel="Close" onPress={onClose}>
          <Ionicons name="close" size={26} color={INK} />
        </Pressable>
        <View style={styles.actions}>
          <MaterialCommunityIcons name="bell-plus-outline" size={24} color={INK} />
          <Ionicons name="star" size={24} color={INK} />
        </View>
      </View>

      <View style={styles.disclaimer}>
        <Text style={styles.disclaimerText}>{disclaimer}</Text>
      </View>

      <View style={styles.header}>
        <View style={styles.headerText}>
          <Text style={styles.name}>
            {symbol} · {name}
          </Text>
          <View accessible accessibilityLabel={`${name} price`} accessibilityLiveRegion="polite">
            <RollingPrice key={pattern} value={price} pattern={pattern} font={priceFont} still={still} />
          </View>
          <View style={styles.changeRow}>
            <LiveText text={changeText} style={[styles.changeText]} />
            <LiveText text={percentText} style={[styles.changeText, styles.percent, percentStyle]} />
          </View>
          <LiveText text={dateText} style={[styles.changeText, styles.date]} />
        </View>
        <View style={styles.coin}>
          <Text style={styles.coinMark}>{mark}</Text>
        </View>
      </View>

      <GestureDetector gesture={pan}>
        <View style={styles.chart} onLayout={onChartLayout} accessible accessibilityLabel={`${name} price chart, ${ranges[range].span}`}>
          {chart.width > 0 && (
            <>
              <GlowChart
                width={chart.width}
                height={chart.height}
                pad={CHART_PAD}
                prices={prices}
                low={low}
                high={high}
                tip={tip}
                held={held}
                lit={lit}
                reveal={reveal}
                shown={shown}
                marks={marks}
              />
              <Animated.Text style={[styles.extreme, { top: CHART_PAD - 8 }, highLowStyle]}>{grouped(extremes[range].high)}</Animated.Text>
              <Animated.Text style={[styles.extreme, { bottom: CHART_PAD - 8 }, highLowStyle]}>{grouped(extremes[range].low)}</Animated.Text>
              <Animated.View pointerEvents="none" style={[styles.tag, tagStyle]}>
                <Text style={styles.tagText}>{grouped(ranges[range].prices[0])}</Text>
              </Animated.View>
            </>
          )}
        </View>
      </GestureDetector>

      <View style={styles.controls}>
        <View style={styles.pills} onLayout={(event) => setPillsWidth(event.nativeEvent.layout.width)} accessibilityRole="tablist">
          {pillsWidth > 0 && <Animated.View style={[styles.pill, { width: segment }, pillStyle]} />}
          {ranges.map((r, index) => (
            <Pressable key={r.label} style={styles.pillSlot} onPress={() => pick(index)} accessibilityRole="tab" accessibilityState={{ selected: range === index }}>
              <Text style={[styles.pillLabel, range === index && styles.pillLabelOn]}>{r.label}</Text>
            </Pressable>
          ))}
        </View>
        <View style={styles.candles}>
          <MaterialCommunityIcons name="chart-timeline-variant-shimmer" size={20} color={SOFT} />
        </View>
      </View>

      <Text style={[styles.footnote, { marginBottom: Math.max(insets.bottom, 16) }]}>{footnote}</Text>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  bar: {
    height: BAR,
    marginHorizontal: MARGIN + 4,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  actions: {
    flexDirection: 'row',
    gap: 24,
  },
  disclaimer: {
    marginTop: 12,
    marginHorizontal: MARGIN,
    padding: 12,
    borderRadius: 12,
    borderCurve: 'continuous',
    backgroundColor: WELL,
  },
  disclaimerText: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
  },
  header: {
    flexDirection: 'row',
    marginTop: 16,
    marginHorizontal: MARGIN,
  },
  headerText: {
    flex: 1,
  },
  name: {
    color: SOFT,
    fontSize: 15,
    lineHeight: 20,
  },
  changeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  live: {
    padding: 0,
    margin: 0,
  },
  changeText: {
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
    height: 20,
    fontVariant: ['tabular-nums'],
  },
  percent: {
    fontWeight: '500',
  },
  date: {
    marginTop: 2,
  },
  coin: {
    width: COIN_SIZE,
    height: COIN_SIZE,
    borderRadius: COIN_SIZE / 2,
    backgroundColor: COIN,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 4,
  },
  coinMark: {
    color: INK,
    fontSize: 26,
    lineHeight: 31,
    fontWeight: '800',
    transform: [{ rotate: '-12deg' }],
  },
  chart: {
    flex: 1,
    minHeight: CHART_MIN,
    marginTop: 20,
    marginLeft: MARGIN,
  },
  extreme: {
    position: 'absolute',
    right: MARGIN,
    color: MUTE,
    fontSize: 13,
    lineHeight: 16,
    fontVariant: ['tabular-nums'],
  },
  tag: {
    position: 'absolute',
    left: 4,
    top: 0,
    height: TAG_H,
    justifyContent: 'center',
    paddingHorizontal: 8,
    borderRadius: 6,
    backgroundColor: WELL,
  },
  tagText: {
    color: SOFT,
    fontSize: 13,
    lineHeight: 17,
    fontVariant: ['tabular-nums'],
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginTop: 16,
    marginHorizontal: MARGIN,
  },
  pills: {
    flex: 1,
    height: PILLS_H,
    flexDirection: 'row',
    paddingHorizontal: PILL_INSET,
    borderRadius: PILLS_H / 2,
    backgroundColor: PILLS,
  },
  pill: {
    position: 'absolute',
    left: 0,
    top: PILL_INSET,
    bottom: PILL_INSET,
    borderRadius: (PILLS_H - PILL_INSET * 2) / 2,
    backgroundColor: PILL_ON,
  },
  pillSlot: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillLabel: {
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  pillLabelOn: {
    color: INK,
  },
  candles: {
    width: PILLS_H,
    height: PILLS_H,
    borderRadius: PILLS_H / 2,
    backgroundColor: PILLS,
    alignItems: 'center',
    justifyContent: 'center',
  },
  footnote: {
    marginTop: 16,
    marginHorizontal: MARGIN,
    color: FAINT,
    fontSize: 12,
    lineHeight: 16,
  },
});
