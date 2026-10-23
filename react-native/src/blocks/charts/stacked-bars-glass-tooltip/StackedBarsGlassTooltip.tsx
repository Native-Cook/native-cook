import { Canvas, Picture } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeInDown,
  FadeOut,
  FadeOutUp,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type MacroRange, type Paints, RANGES, calories, columnLayout, drawBars, stackTop, toBars } from './macros';

export type { MacroDay, MacroRange } from './macros';

type Props = {
  title?: string;
  heading?: string;
  hint?: string;
  /** The periods of the segmented control, each with its columns. */
  ranges?: MacroRange[];
  /** The range shown at rest; autoPlay switches to the next one and back. */
  initialRange?: number;
  carbsLabel?: string;
  fatLabel?: string;
  proteinLabel?: string;
  /** Plays a scrub across the week and a switch to the month by itself, every LOOP seconds. */
  autoPlay?: boolean;
  onRangeChange?: (range: MacroRange) => void;
  /** Called with the column under the finger, or null when it lifts. */
  onSelect?: (index: number | null) => void;
};

const SKY = ['#fde8f2', '#e9e4ff', '#ddebff'] as const;
const INK = '#1b1b2f';
const MUTE = '#8a8aa0';
const SOFT = '#6b6b85';
const LINE = '#efeff5';
const CARD = '#ffffff';
const UP = '#ff5c5c';
const UP_FILL = '#ffecec';
// Bottom to top, each a gradient from its top to its bottom.
const PROTEIN = ['#2d8bff', '#00b7ff'];
const FAT = ['#c77dff', '#d99aff'];
const CARBS = ['#ffd95a', '#ffc130'];
const SWATCH = { carbs: '#ffb800', fat: '#c77dff', protein: '#2d8bff' };

const MARGIN = 16;
const CARD_PADDING = 20;
const CARD_RADIUS = 28;
const AXIS = 30;
const LABELS = 22;
const GRID_LINES = 5;
const COLUMN = 28;
// A column takes this share of its slot, up to COLUMN wide.
const COLUMN_SHARE = 0.62;
const SEGMENT_GAP = 2;
const COLUMN_RADIUS = 10;
const LIFT = 6;
const DIM = 0.45;
const TIP_WIDTH = 140;
const TIP_HEIGHT = 96;
const TIP_RADIUS = 16;
// The tooltip floats this far above the hovered column, and may rise this far over the legend.
const TIP_GAP = 10;
const TIP_RISE = 44;
const FINGER = 40;
const SEGMENTED = 32;
const SEGMENT_WIDTH = 64;
const IOS = Platform.OS === 'ios';

const MORPH = 0.8;
const FOCUS_IN = 0.15;
const FOCUS_OUT = 0.2;
// The tooltip glides to a new column; the finger ring trails the touch.
const GLIDE = { duration: 300, dampingRatio: 0.82 };
const TRAIL = { duration: 380, dampingRatio: 1 };

/**
 * autoPlay, in seconds from the start of a loop: the finger goes down on a column, drags right,
 * rests, drags back and lifts; then the control switches to the next range and back.
 */
export const LOOP = 9;
const SCRUB = [
  // time, column (fractions glide between columns)
  [0.8, 1],
  [1.2, 1],
  [2.9, 6],
  [3.3, 6],
  [4.5, 2],
  [5.0, 2],
];
const LIFT_AT = 5.0;
const SWITCH_AT = 5.6;
const SWITCH_BACK_AT = 7.6;
/** After the last touch autoPlay waits this long, then comes back to rest. */
const USER_HOLD = 3;

const AUTO = 0;
const HAND = 1;
const BUSY = -2;
const RELEASED = -1;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** 2500 → "2.5k", 450 → "450". */
function axisLabel(value: number) {
  return value >= 1000 ? `${Number((value / 1000).toFixed(1))}k` : String(Math.round(value));
}

function grouped(value: number) {
  return Math.round(value).toLocaleString('en-US');
}

/** The scripted finger's column at a time of the loop, eased between the rows of SCRUB. */
function scriptedColumn(time: number) {
  'worklet';
  if (time <= SCRUB[0][0]) return SCRUB[0][1];
  for (let k = 1; k < SCRUB.length; k++) {
    if (time < SCRUB[k][0]) {
      const a = SCRUB[k - 1];
      const b = SCRUB[k];
      const u = (time - a[0]) / (b[0] - a[0]);
      return a[1] + (b[1] - a[1]) * u * u * (3 - 2 * u);
    }
  }
  return SCRUB[SCRUB.length - 1][1];
}

/** A number that rolls up when it changes: the old one leaves upward as the new one comes in. */
function Rolling({ value, style }: { value: string; style: object }) {
  return (
    <View style={styles.rolling}>
      <Animated.Text key={value} entering={FadeInDown.duration(220)} exiting={FadeOutUp.duration(180)} style={style} numberOfLines={1}>
        {value}
      </Animated.Text>
    </View>
  );
}

/**
 * A macros screen: a week of stacked columns of protein, fat and carbs on a white card. Press and
 * drag across the chart: the column under the finger lifts while the others dim, and a frosted
 * glass tooltip glides above it with that day's grams. Day, Week and Month morph the columns
 * into each other.
 */
export function StackedBarsGlassTooltip({
  title = 'Macros',
  heading = 'How your intake changed over time',
  hint = 'Press and drag across the chart',
  ranges = RANGES,
  initialRange = 1,
  carbsLabel = 'Carbs',
  fatLabel = 'Fat',
  proteinLabel = 'Protein',
  autoPlay = false,
  onRangeChange,
  onSelect,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [chart, setChart] = useState({ width: window.width - 2 * (MARGIN + CARD_PADDING) - AXIS, height: 260 });
  const [range, setRange] = useState(initialRange);
  const [hovered, setHovered] = useState<number | null>(null);

  const bars = useMemo(() => ranges.map(toBars), [ranges]);
  const paints: Paints = {
    colors: [PROTEIN, FAT, CARBS],
    gap: SEGMENT_GAP,
    radius: COLUMN_RADIUS,
    lift: LIFT,
    dim: DIM,
    card: CARD,
    widest: COLUMN,
    share: COLUMN_SHARE,
  };

  const from = useSharedValue(bars[initialRange]);
  const to = useSharedValue(bars[initialRange]);
  const toIndex = useSharedValue(initialRange);
  const morph = useSharedValue(1);
  const fingerOn = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const focus = useSharedValue(0);
  const focusX = useSharedValue(0);
  const trailX = useSharedValue(0);
  const trailY = useSharedValue(0);
  const mode = useSharedValue(autoPlay ? AUTO : HAND);
  const origin = useSharedValue(-1);
  const lastTime = useSharedValue(0);
  const touchedAt = useSharedValue(RELEASED);

  const startMorph = (index: number, next: (typeof bars)[number]) => {
    'worklet';
    // The switch starts from the range drawn now and its clock restarts on the same thread.
    from.set(to.get());
    to.set(next);
    toIndex.set(index);
    morph.set(0);
    morph.set(withTiming(1, { duration: (still ? 0.3 : MORPH) * 1000, easing: Easing.inOut(Easing.cubic), reduceMotion: ReduceMotion.Never }));
  };

  const rangeRef = useRef(initialRange);
  const select = (index: number, byHand: boolean) => {
    if (index === rangeRef.current) return;
    rangeRef.current = index;
    setRange(index);
    if (byHand) tick();
    scheduleOnUI(startMorph, index, bars[index]);
    onRangeChange?.(ranges[index]);
  };

  const hover = (index: number, byHand: boolean) => {
    setHovered(index < 0 ? null : index);
    if (byHand && index >= 0) tick();
    onSelect?.(index < 0 ? null : index);
  };

  const chartWidth = chart.width;
  const chartHeight = chart.height;
  const restRange = initialRange;
  const otherRange = (initialRange + 1) % ranges.length;

  useFrameCallback((frame) => {
    if (!autoPlay) return;
    const now = frame.timestamp;
    if (mode.get() === HAND) {
      if (touchedAt.get() === RELEASED) touchedAt.set(now);
      if (touchedAt.get() < 0 || now - touchedAt.get() < USER_HOLD * 1000) return;
      // Back to rest: the resting range first, then the loop from its start.
      let wait = 0;
      if (toIndex.get() !== restRange) {
        scheduleOnRN(select, restRange, false);
        wait = MORPH * 1000;
      }
      origin.set(now + wait);
      lastTime.set(-1);
      mode.set(AUTO);
      return;
    }
    if (origin.get() < 0) origin.set(now);
    const elapsed = (now - origin.get()) / 1000;
    if (elapsed < 0) return;
    const time = elapsed % LOOP;
    const before = lastTime.get();
    lastTime.set(time);
    // The scripted finger, through the same values a touch sets.
    const [pitch] = columnLayout(to.get().kcal.length, chartWidth, COLUMN, COLUMN_SHARE);
    fingerX.set((scriptedColumn(time) + 0.5) * pitch);
    fingerY.set(chartHeight * 0.55);
    fingerOn.set(time >= SCRUB[0][0] && time < LIFT_AT ? 1 : 0);
    if (before < SWITCH_AT && time >= SWITCH_AT) scheduleOnRN(select, otherRange, false);
    if (before < SWITCH_BACK_AT && time >= SWITCH_BACK_AT) scheduleOnRN(select, restRange, false);
  });

  // The column under the finger, or -1.
  useAnimatedReaction(
    () => {
      if (fingerOn.get() === 0) return -1;
      const n = to.get().kcal.length;
      return Math.min(n - 1, Math.max(0, Math.floor(fingerX.get() / (chartWidth / n))));
    },
    (now, old) => {
      if (now === old) return;
      if (now >= 0) {
        const n = to.get().kcal.length;
        const x = (now + 0.5) * (chartWidth / n);
        // A fresh touch starts at its column; moving on, the focus glides.
        focusX.set(old === null || old < 0 || still ? x : withSpring(x, GLIDE));
      }
      scheduleOnRN(hover, now, mode.get() === HAND);
    },
  );

  useAnimatedReaction(
    () => fingerOn.get(),
    (on, old) => {
      if (on === old) return;
      focus.set(withTiming(on, { duration: (on ? FOCUS_IN : FOCUS_OUT) * 1000, reduceMotion: ReduceMotion.Never }));
    },
  );

  useAnimatedReaction(
    () => [fingerX.get(), fingerY.get(), fingerOn.get()],
    ([x, y, on], old) => {
      const fresh = old === null || old[2] === 0;
      trailX.set(fresh || still || !on ? x : withSpring(x, TRAIL));
      trailY.set(fresh || still || !on ? y : withSpring(y, TRAIL));
    },
  );

  const picture = useDerivedValue(() => {
    const m = morph.get();
    // Reduced motion swaps the columns halfway through a short fade instead of moving them.
    const shown = still ? (m < 0.5 ? 0 : 1) : m;
    return drawBars(from.get(), to.get(), shown, focusX.get(), focus.get(), chartWidth, chartHeight, paints);
  });
  const chartStyle = useAnimatedStyle(() => ({ opacity: still ? 1 - Math.sin(Math.PI * morph.get()) : 1 }));

  const tipStyle = useAnimatedStyle(() => {
    const x = focusX.get();
    const f = focus.get();
    const top = chartHeight - stackTop(to.get(), x, chartWidth, chartHeight) - LIFT;
    return {
      opacity: f,
      transform: [
        { translateX: AXIS + Math.min(chartWidth - TIP_WIDTH, Math.max(-AXIS, x - TIP_WIDTH / 2)) },
        { translateY: Math.max(-TIP_RISE, top - TIP_GAP - TIP_HEIGHT) },
        { scale: still ? 1 : 0.94 + 0.06 * f },
      ],
    };
  });
  // A hairline from the tooltip down to the top of the column, when there is room for one.
  const connectorStyle = useAnimatedStyle(() => {
    const x = focusX.get();
    const top = chartHeight - stackTop(to.get(), x, chartWidth, chartHeight) - LIFT;
    const from = Math.max(-TIP_RISE, top - TIP_GAP - TIP_HEIGHT) + TIP_HEIGHT;
    return {
      opacity: focus.get(),
      height: Math.max(0, top - from),
      transform: [{ translateX: AXIS + x - 0.5 }, { translateY: from }],
    };
  });
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: focus.get(),
    transform: [{ translateX: AXIS + trailX.get() - FINGER / 2 }, { translateY: trailY.get() - FINGER / 2 }],
  }));
  const pillStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: withTiming(range * SEGMENT_WIDTH, { duration: still ? 0 : 260, easing: Easing.out(Easing.cubic) }) }],
  }));

  // A touch takes over from autoPlay; the loop comes back USER_HOLD seconds after the last one.
  const touch = () => {
    'worklet';
    mode.set(HAND);
    touchedAt.set(BUSY);
  };
  const release = () => {
    'worklet';
    touchedAt.set(RELEASED);
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      touch();
      fingerX.set(Math.min(chartWidth - 1, Math.max(0, event.x - AXIS)));
      fingerY.set(Math.min(chartHeight, Math.max(0, event.y)));
      fingerOn.set(1);
    })
    .onUpdate((event) => {
      fingerX.set(Math.min(chartWidth - 1, Math.max(0, event.x - AXIS)));
      fingerY.set(Math.min(chartHeight, Math.max(0, event.y)));
    })
    .onFinalize(() => {
      fingerOn.set(0);
      release();
    });

  const pressRange = (index: number) => {
    scheduleOnUI(() => {
      'worklet';
      touch();
      fingerOn.set(0);
      release();
    });
    select(index, true);
  };

  useEffect(() => {
    rangeRef.current = range;
  }, [range]);

  const onChartLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setChart({ width: width - AXIS, height: height - LABELS });
  };

  const current = ranges[range];
  const columns = current.columns;
  const n = columns.length;
  const average = (key: 'carbs' | 'fat' | 'protein') => columns.reduce((sum, day) => sum + day[key], 0) / n;
  const averageKcal = columns.reduce((sum, day) => sum + calories(day), 0) / n;
  const [pitch] = columnLayout(n, chart.width, COLUMN, COLUMN_SHARE);
  const day = hovered === null ? null : columns[Math.min(hovered, n - 1)];
  const legend = [
    { key: 'carbs', label: carbsLabel, color: SWATCH.carbs },
    { key: 'fat', label: fatLabel, color: SWATCH.fat },
    { key: 'protein', label: proteinLabel, color: SWATCH.protein },
  ] as const;

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <LinearGradient colors={SKY} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      <View style={[styles.body, { paddingTop: insets.top + 8, paddingBottom: Math.max(insets.bottom, 16) }]}>
        <View style={styles.titleRow}>
          <View style={styles.titleText}>
            <Text style={styles.title}>{title}</Text>
            <Animated.Text key={current.key} entering={FadeIn.duration(200)} style={styles.period} numberOfLines={1}>
              {current.period}
            </Animated.Text>
          </View>
          <View style={styles.segmented}>
            <Animated.View style={[styles.segmentPill, pillStyle]} />
            {ranges.map((item, index) => (
              <Pressable
                key={item.key}
                style={styles.segment}
                onPress={() => pressRange(index)}
                accessibilityRole="tab"
                accessibilityState={{ selected: index === range }}
              >
                <Text style={[styles.segmentLabel, index === range && styles.segmentLabelOn]}>{item.name}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.heading}>{heading}</Text>
          <View style={styles.summary}>
            <Text style={styles.total}>
              {grouped(averageKcal)}
              <Text style={styles.totalUnit}> cal</Text>
            </Text>
            <View style={styles.tag}>
              <Text style={styles.tagLabel}>↑ {current.change.toFixed(1)}%</Text>
            </View>
          </View>
          <View style={styles.legend}>
            {legend.map((item) => (
              <View key={item.key} style={styles.legendItem}>
                <Rolling value={`${Math.round(average(item.key))} g`} style={[styles.legendValue, { color: item.color }]} />
                <Text style={styles.legendLabel}>Avg. {item.label.toLowerCase()}</Text>
              </View>
            ))}
          </View>

          <GestureDetector gesture={pan}>
            <View style={styles.chart} onLayout={onChartLayout} accessibilityLabel={`${heading}, ${current.period}`}>
              {Array.from({ length: GRID_LINES + 1 }, (_, k) => (
                <View key={k} style={[styles.gridLine, k === 0 && styles.baseline, { top: chart.height * (1 - k / GRID_LINES) }]} />
              ))}
              <Animated.View key={`axis-${current.key}`} entering={FadeIn.duration(220)} style={styles.axis}>
                {Array.from({ length: GRID_LINES + 1 }, (_, k) => (
                  <Text key={k} style={[styles.axisLabel, { top: chart.height * (1 - k / GRID_LINES) - 6 }]} numberOfLines={1}>
                    {axisLabel((current.max * k) / GRID_LINES)}
                  </Text>
                ))}
              </Animated.View>

              <Animated.View style={[styles.canvas, { width: chart.width, height: chart.height }, chartStyle]}>
                <Canvas style={StyleSheet.absoluteFill}>
                  <Picture picture={picture} />
                </Canvas>
              </Animated.View>

              <Animated.View key={current.key} entering={FadeIn.duration(220).delay(MORPH * 500)} exiting={FadeOut.duration(150)} style={[styles.xLabels, { top: chart.height }]}>
                {columns.map((column, index) =>
                  column.label ? (
                    <Text key={column.title} style={[styles.xLabel, { left: AXIS + (index + 0.5) * pitch - 30 }]} numberOfLines={1}>
                      {column.label}
                    </Text>
                  ) : null,
                )}
              </Animated.View>

              <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />

              <Animated.View pointerEvents="none" style={[styles.connector, connectorStyle]} />
              <Animated.View pointerEvents="none" style={[styles.tip, tipStyle]}>
                {IOS ? <BlurView intensity={30} tint="systemUltraThinMaterialLight" style={StyleSheet.absoluteFill} /> : null}
                <View style={[StyleSheet.absoluteFill, IOS ? styles.glass : styles.glassFallback]} />
                {day ? (
                  <View style={styles.tipBody}>
                    <Rolling value={day.title} style={styles.tipTitle} />
                    {legend.map((item) => (
                      <View key={item.key} style={styles.tipRow}>
                        <View style={[styles.dot, { backgroundColor: item.color }]} />
                        <Text style={styles.tipLabel}>{item.label}</Text>
                        <Rolling value={`${day[item.key]} g`} style={styles.tipValue} />
                      </View>
                    ))}
                  </View>
                ) : null}
              </Animated.View>
            </View>
          </GestureDetector>
        </View>

        <Text style={styles.hint}>{hint}</Text>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SKY[1],
  },
  body: {
    flex: 1,
    paddingHorizontal: MARGIN,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 8,
    marginBottom: 20,
  },
  titleText: {
    flexShrink: 1,
    marginRight: 12,
  },
  title: {
    color: INK,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  period: {
    marginTop: 2,
    color: SOFT,
    fontSize: 13,
    lineHeight: 18,
  },
  segmented: {
    flexDirection: 'row',
    height: SEGMENTED,
    borderRadius: SEGMENTED / 2,
    backgroundColor: 'rgba(255,255,255,0.7)',
  },
  segmentPill: {
    position: 'absolute',
    top: 3,
    left: 3,
    width: SEGMENT_WIDTH - 6,
    height: SEGMENTED - 6,
    borderRadius: (SEGMENTED - 6) / 2,
    backgroundColor: CARD,
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 8, color: 'rgba(60,40,120,0.14)' }],
  },
  segment: {
    width: SEGMENT_WIDTH,
    height: SEGMENTED,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentLabel: {
    color: SOFT,
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '500',
  },
  segmentLabelOn: {
    color: INK,
    fontWeight: '600',
  },
  card: {
    flex: 1,
    maxHeight: 620,
    padding: CARD_PADDING,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    borderWidth: 1,
    borderColor: '#ffffff',
    boxShadow: [{ offsetX: 0, offsetY: 16, blurRadius: 40, color: 'rgba(60,40,120,0.12)' }],
  },
  heading: {
    color: INK,
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '600',
  },
  summary: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 12,
  },
  total: {
    color: INK,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '600',
    letterSpacing: -0.6,
    fontVariant: ['tabular-nums'],
  },
  totalUnit: {
    color: MUTE,
    fontSize: 14,
    fontWeight: '500',
    letterSpacing: 0,
  },
  tag: {
    marginLeft: 10,
    height: 22,
    paddingHorizontal: 8,
    borderRadius: 11,
    backgroundColor: UP_FILL,
    justifyContent: 'center',
  },
  tagLabel: {
    color: UP,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
  },
  legend: {
    flexDirection: 'row',
    marginTop: 14,
    marginBottom: 12,
    gap: 24,
  },
  legendItem: {
    minWidth: 64,
  },
  legendValue: {
    fontSize: 20,
    lineHeight: 25,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  legendLabel: {
    color: MUTE,
    fontSize: 11,
    lineHeight: 14,
  },
  rolling: {
    overflow: 'hidden',
  },
  chart: {
    flex: 1,
    marginTop: 8,
  },
  axis: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: AXIS,
  },
  axisLabel: {
    position: 'absolute',
    left: 0,
    width: AXIS - 4,
    color: MUTE,
    fontSize: 10,
    lineHeight: 12,
    fontVariant: ['tabular-nums'],
  },
  gridLine: {
    position: 'absolute',
    left: AXIS,
    right: 0,
    height: 1,
    backgroundColor: LINE,
  },
  baseline: {
    height: 1,
    backgroundColor: '#e5e5f0',
  },
  canvas: {
    position: 'absolute',
    left: AXIS,
    top: 0,
  },
  xLabels: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: LABELS,
  },
  xLabel: {
    position: 'absolute',
    top: 6,
    width: 60,
    textAlign: 'center',
    color: MUTE,
    fontSize: 10,
    lineHeight: 13,
  },
  finger: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: FINGER,
    height: FINGER,
    borderRadius: FINGER / 2,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
    backgroundColor: 'rgba(255,255,255,0.12)',
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 10, color: 'rgba(60,40,120,0.18)' }],
  },
  connector: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 1,
    backgroundColor: 'rgba(255,255,255,0.9)',
    boxShadow: [{ offsetX: 0, offsetY: 0, blurRadius: 3, color: 'rgba(60,40,120,0.25)' }],
  },
  tip: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: TIP_WIDTH,
    height: TIP_HEIGHT,
    borderRadius: TIP_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(60,40,120,0.16)' }],
  },
  glass: {
    backgroundColor: 'rgba(255,255,255,0.4)',
  },
  glassFallback: {
    backgroundColor: 'rgba(255,255,255,0.85)',
  },
  tipBody: {
    flex: 1,
    paddingHorizontal: 12,
    paddingTop: 10,
    paddingBottom: 10,
    justifyContent: 'space-between',
  },
  tipTitle: {
    color: INK,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
  },
  tipRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 6,
  },
  tipLabel: {
    flex: 1,
    color: SOFT,
    fontSize: 12,
    lineHeight: 16,
  },
  tipValue: {
    color: INK,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    textAlign: 'right',
  },
  hint: {
    marginTop: 16,
    color: SOFT,
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'center',
  },
});
