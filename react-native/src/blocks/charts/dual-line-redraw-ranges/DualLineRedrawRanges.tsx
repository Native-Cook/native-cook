import { BlurMask, Canvas, Circle, Group, Path, Text as SkiaText, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeInDown,
  FadeOut,
  FadeOutUp,
  ReduceMotion,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type LineRange, RANGES, linePart, pointAt } from './series';

export type { LineRange } from './series';

type Props = {
  title?: string;
  /** The ranges of the segmented control, each with its two series. */
  ranges?: LineRange[];
  /** The range shown at rest; autoPlay goes through the others and back to it. */
  initialRange?: number;
  highLabel?: string;
  lowLabel?: string;
  highColor?: string;
  lowColor?: string;
  /** Switches ranges by itself, every LOOP seconds, and comes back to rest. */
  autoPlay?: boolean;
  onRangeChange?: (range: LineRange) => void;
};

const BACKGROUND = '#101010';
const INK = '#f2f2f2';
const MUTE = '#6b6b70';
const GRID = '#2a2a2e';
const TRACK = '#232326';
const THUMB = '#f2f2f2';
const THUMB_INK = '#111111';
const TILE = '#18181b';

const MARGIN = 20;
const AXIS = 40;
const RIGHT_PAD = 10;
const TICKS_HEIGHT = 28;
const GRID_STEPS = 5;
const LINE_WIDTH = 2;
const HEAD = 3.5;
const HEAD_GLOW = 6;
const SEGMENTED = 34;
const LABEL_SIZE = 12;
// The end labels sit this far from the dot: above it for the high series, below for the low.
const LABEL_RISE = 10;
const LABEL_DROP = 20;
const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

// A switch, in seconds: the old lines wipe out from the left, then the new ones race in from it,
// the low line a beat behind, and their end values fade in.
const WIPE = 0.35;
const DRAW = 0.9;
const STAGGER = 0.12;
const LABEL_IN = 0.2;
const TOTAL = WIPE + STAGGER + DRAW + LABEL_IN;
// Reduced motion cross-fades the lines instead.
const FADE = 0.25;
const THUMB_SLIDE = 200;
// A swipe this long on the chart changes the range.
const SWIPE = 48;

/** autoPlay: the range switches at these times of each loop; the last returns to rest. */
export const LOOP = 9;
const SWITCHES = [0.9, 3.9, 6.9];
const USER_HOLD = 3;

const AUTO = 0;
const HAND = 1;
const BUSY = -2;
const RELEASED = -1;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/**
 * What one line shows at a time into a switch: whether it is the new series (1) or the old (0),
 * the fractions of the width it spans, and its opacity. `fromEnd` is how far the old line had got.
 */
function lineWindow(line: number, phase: number, fromEnd: number, still: boolean) {
  'worklet';
  if (still) {
    if (phase < FADE) return [0, 0, fromEnd, 1 - phase / FADE];
    return [1, 0, 1, clamp01((phase - FADE) / FADE)];
  }
  if (phase < WIPE) {
    // Ease-in: the wipe gathers speed toward the right edge.
    const u = phase / WIPE;
    return [0, fromEnd * u * u, fromEnd, 1];
  }
  const u = clamp01((phase - WIPE - line * STAGGER) / DRAW);
  return [1, 0, u * u * (3 - 2 * u), 1];
}

/**
 * A dark chart of two noisy series, the average high and low move of a price, with Day, Week and
 * Year. A switch wipes the old lines out from the left and races the new ones in from it, the
 * gold line a beat ahead of the violet one, each with a dot riding its tip; the last values fade
 * in at the right edge. Tap a range or swipe across the chart.
 */
export function DualLineRedrawRanges({
  title = 'Average % move of high / low',
  ranges = RANGES,
  initialRange = 1,
  highLabel = 'High',
  lowLabel = 'Low',
  highColor = '#e9c46a',
  lowColor = '#8b7cf6',
  autoPlay = false,
  onRangeChange,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width - 2 * MARGIN - AXIS, height: 360 });
  const [range, setRange] = useState(initialRange);
  const [segmentWidth, setSegmentWidth] = useState((window.width - 2 * MARGIN) / ranges.length);

  const font = useMemo(() => matchFont({ fontFamily: FONT, fontSize: LABEL_SIZE, fontWeight: '600' }), []);

  const fromIndex = useSharedValue(initialRange);
  const toIndex = useSharedValue(initialRange);
  // Seconds since the last switch began; at rest it is past the end of one.
  const phase = useSharedValue(TOTAL);
  const fromEnds = useSharedValue([1, 1]);
  const mode = useSharedValue(autoPlay ? AUTO : HAND);
  const origin = useSharedValue(-1);
  const lastTime = useSharedValue(0);
  const touchedAt = useSharedValue(RELEASED);

  const width = size.width - RIGHT_PAD;
  const height = size.height;
  const series = ranges.map((r) => [r.high, r.low]);
  // The end labels' widths, measured here: the UI thread only places them.
  const labelWidths = ranges.map((r) => [r.high, r.low].map((values) => font.measureText(`${Math.round(values[values.length - 1])} %`).width));

  const startSwitch = (index: number) => {
    'worklet';
    // The old lines wipe out from where they had got to, and the clock restarts on this thread.
    const p = phase.get();
    const ends = [lineWindow(0, p, fromEnds.get()[0], still), lineWindow(1, p, fromEnds.get()[1], still)];
    fromIndex.set(ends[0][0] === 1 ? toIndex.get() : fromIndex.get());
    fromEnds.set([ends[0][2], ends[1][2]]);
    toIndex.set(index);
    phase.set(0);
    phase.set(withTiming(TOTAL, { duration: TOTAL * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  const applied = (index: number, byHand: boolean) => {
    setRange(index);
    if (byHand) tick();
    onRangeChange?.(ranges[index]);
  };

  /** Every switch, by touch or by autoPlay, goes through here on the UI thread. */
  const select = (index: number, byHand: boolean) => {
    'worklet';
    if (index < 0 || index >= series.length || index === toIndex.get()) return;
    startSwitch(index);
    scheduleOnRN(applied, index, byHand);
  };

  const restRange = initialRange;
  const order = useMemo(() => {
    // autoPlay visits every other range, then comes back to the resting one.
    const list: number[] = [];
    for (let k = 1; k < ranges.length; k++) list.push((initialRange + k) % ranges.length);
    list.push(initialRange);
    return list;
  }, [ranges.length, initialRange]);

  useFrameCallback((frame) => {
    if (!autoPlay) return;
    const now = frame.timestamp;
    if (mode.get() === HAND) {
      if (touchedAt.get() === RELEASED) touchedAt.set(now);
      if (touchedAt.get() < 0 || now - touchedAt.get() < USER_HOLD * 1000) return;
      let wait = 0;
      if (toIndex.get() !== restRange) {
        select(restRange, false);
        wait = TOTAL * 1000;
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
    for (let k = 0; k < SWITCHES.length && k < order.length; k++) {
      if (before < SWITCHES[k] && time >= SWITCHES[k]) select(order[k], false);
    }
  });

  const touch = () => {
    'worklet';
    mode.set(HAND);
    touchedAt.set(RELEASED);
  };

  const swipe = Gesture.Pan()
    .activeOffsetX([-16, 16])
    .failOffsetY([-24, 24])
    .onStart(() => {
      mode.set(HAND);
      touchedAt.set(BUSY);
    })
    .onEnd((event) => {
      if (Math.abs(event.translationX) >= SWIPE) select(toIndex.get() + (event.translationX < 0 ? 1 : -1), true);
    })
    .onFinalize(() => {
      touchedAt.set(RELEASED);
    });

  const pressRange = (index: number) => {
    scheduleOnUI(() => {
      'worklet';
      touch();
      select(index, true);
    });
  };

  /** Everything one line needs at this moment: its path, its tip, and its end label. */
  const lineState = (line: number, p: number, ends: number[], from: number, to: number) => {
    'worklet';
    const w = lineWindow(line, p, ends[line], still);
    const values = series[w[0] === 1 ? to : from][line];
    const [hx, hy] = pointAt(values, w[2], width, height);
    let tip = w[3];
    let label = 0;
    if (w[0] === 0) {
      tip *= still ? 1 : 1 - smooth(p, WIPE * 0.75, WIPE);
      label = still ? w[3] : 1 - smooth(p, 0, WIPE * 0.5);
    } else {
      if (w[2] <= 0) tip = 0;
      const drawn = still ? FADE : WIPE + line * STAGGER + DRAW;
      label = still ? w[3] : smooth(p, drawn, drawn + LABEL_IN);
    }
    const last = values[values.length - 1];
    const text = `${Math.round(last)} %`;
    return {
      path: linePart(values, w[1], w[2], width, height),
      opacity: w[3],
      hx,
      hy,
      tip,
      label,
      text,
      lx: width - labelWidths[w[0] === 1 ? to : from][line] - 6,
      ly: line === 0 ? hy - LABEL_RISE : hy + LABEL_DROP,
    };
  };

  const high = useDerivedValue(() => lineState(0, phase.get(), fromEnds.get(), fromIndex.get(), toIndex.get()));
  const low = useDerivedValue(() => lineState(1, phase.get(), fromEnds.get(), fromIndex.get(), toIndex.get()));
  const highPath = useDerivedValue(() => high.get().path);
  const lowPath = useDerivedValue(() => low.get().path);
  const highOpacity = useDerivedValue(() => high.get().opacity);
  const lowOpacity = useDerivedValue(() => low.get().opacity);
  const highX = useDerivedValue(() => high.get().hx);
  const highY = useDerivedValue(() => high.get().hy);
  const lowX = useDerivedValue(() => low.get().hx);
  const lowY = useDerivedValue(() => low.get().hy);
  const highTip = useDerivedValue(() => high.get().tip);
  const lowTip = useDerivedValue(() => low.get().tip);
  const highLabelOpacity = useDerivedValue(() => high.get().label);
  const lowLabelOpacity = useDerivedValue(() => low.get().label);
  const highText = useDerivedValue(() => high.get().text);
  const lowText = useDerivedValue(() => low.get().text);
  const highLX = useDerivedValue(() => high.get().lx);
  const highLY = useDerivedValue(() => high.get().ly);
  const lowLX = useDerivedValue(() => low.get().lx);
  const lowLY = useDerivedValue(() => low.get().ly);

  const gridPath = useMemo(() => {
    let d = '';
    for (let k = 0; k <= GRID_STEPS; k++) {
      const y = Math.round(height * (k / GRID_STEPS)) + 0.5;
      d += `M0 ${y}H${size.width}`;
    }
    return d;
  }, [height, size.width]);

  const thumbStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: withTiming(range * segmentWidth, { duration: still ? 0 : THUMB_SLIDE, easing: Easing.out(Easing.cubic) }) }],
  }));

  const onChartLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setSize({ width: w - AXIS, height: h - TICKS_HEIGHT });
  };

  const current = ranges[range];
  const average = (values: number[]) => Math.round(values.reduce((sum, v) => sum + v, 0) / values.length);
  const stats = [
    { key: 'high', label: highLabel, color: highColor, values: current.high },
    { key: 'low', label: lowLabel, color: lowColor, values: current.low },
  ];

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.body, { paddingTop: insets.top + 16, paddingBottom: Math.max(insets.bottom, 20) }]}>
        <Text style={styles.title}>{title}</Text>
        <View style={styles.captionRow}>
          <Animated.Text key={current.key} entering={FadeIn.duration(200)} style={styles.caption}>
            {current.caption}
          </Animated.Text>
          <View style={styles.info}>
            <Text style={styles.infoMark}>i</Text>
          </View>
        </View>

        <View
          style={styles.segmented}
          onLayout={(event) => setSegmentWidth(event.nativeEvent.layout.width / ranges.length)}
          accessibilityRole="tablist"
        >
          <Animated.View style={[styles.thumb, { width: segmentWidth - 6 }, thumbStyle]} />
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

        <View style={styles.legend}>
          {stats.map((item) => (
            <View key={item.key} style={styles.chip}>
              <View style={[styles.sample, { backgroundColor: item.color }]} />
              <Text style={styles.chipLabel}>{item.label}</Text>
            </View>
          ))}
        </View>

        <GestureDetector gesture={swipe}>
          <View style={styles.chart} onLayout={onChartLayout} accessibilityLabel={`${title}, ${current.caption}`}>
            {Array.from({ length: GRID_STEPS + 1 }, (_, k) => (
              <Text key={k} style={[styles.yLabel, { top: height * (k / GRID_STEPS) - 7 }]}>
                {Math.round(100 * (1 - k / GRID_STEPS))} %
              </Text>
            ))}
            <Canvas style={[styles.canvas, { width: size.width, height }]}>
              <Path path={gridPath} style="stroke" strokeWidth={1} color={GRID} />
              <Group opacity={highOpacity}>
                <Path path={highPath} style="stroke" strokeWidth={LINE_WIDTH} strokeJoin="round" strokeCap="round" color={highColor} />
              </Group>
              <Group opacity={lowOpacity}>
                <Path path={lowPath} style="stroke" strokeWidth={LINE_WIDTH} strokeJoin="round" strokeCap="round" color={lowColor} />
              </Group>
              <Group opacity={highTip}>
                <Circle cx={highX} cy={highY} r={HEAD_GLOW} color={highColor} opacity={0.45}>
                  <BlurMask blur={5} style="normal" />
                </Circle>
                <Circle cx={highX} cy={highY} r={HEAD} color={highColor} />
              </Group>
              <Group opacity={lowTip}>
                <Circle cx={lowX} cy={lowY} r={HEAD_GLOW} color={lowColor} opacity={0.45}>
                  <BlurMask blur={5} style="normal" />
                </Circle>
                <Circle cx={lowX} cy={lowY} r={HEAD} color={lowColor} />
              </Group>
              <SkiaText font={font} text={highText} x={highLX} y={highLY} color={highColor} opacity={highLabelOpacity} />
              <SkiaText font={font} text={lowText} x={lowLX} y={lowLY} color={lowColor} opacity={lowLabelOpacity} />
            </Canvas>
            <Animated.View
              key={current.key}
              entering={FadeIn.duration(220).delay(still ? 0 : WIPE * 1000)}
              exiting={FadeOut.duration(still ? 120 : WIPE * 1000)}
              style={[styles.ticks, { top: height }]}
            >
              {current.ticks.map((label, k) => (
                <Text
                  key={`${label}-${k}`}
                  style={[
                    styles.tick,
                    { left: AXIS + (width * k) / (current.ticks.length - 1) - 30 },
                    k === 0 && styles.tickFirst,
                    k === current.ticks.length - 1 && styles.tickLast,
                  ]}
                  numberOfLines={1}
                >
                  {label}
                </Text>
              ))}
            </Animated.View>
          </View>
        </GestureDetector>

        <View style={styles.stats}>
          {stats.map((item) => (
            <View key={item.key} style={styles.tile}>
              <Text style={styles.tileLabel}>
                {item.label} · average
              </Text>
              <View style={styles.rolling}>
                <Animated.Text
                  key={`${current.key}-${item.key}`}
                  entering={FadeInDown.duration(240).delay(still ? 0 : WIPE * 1000)}
                  exiting={FadeOutUp.duration(180)}
                  style={[styles.tileValue, { color: item.color }]}
                >
                  {average(item.values)} %
                </Animated.Text>
              </View>
            </View>
          ))}
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  body: {
    flex: 1,
    paddingHorizontal: MARGIN,
  },
  title: {
    color: INK,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '600',
    letterSpacing: -0.3,
  },
  captionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
  caption: {
    color: MUTE,
    fontSize: 14,
    lineHeight: 19,
  },
  info: {
    width: 14,
    height: 14,
    marginLeft: 6,
    borderRadius: 7,
    borderWidth: 1,
    borderColor: MUTE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  infoMark: {
    color: MUTE,
    fontSize: 9,
    lineHeight: 11,
    fontWeight: '700',
  },
  segmented: {
    flexDirection: 'row',
    height: SEGMENTED,
    marginTop: 24,
    borderRadius: SEGMENTED / 2,
    backgroundColor: TRACK,
  },
  thumb: {
    position: 'absolute',
    top: 3,
    left: 3,
    height: SEGMENTED - 6,
    borderRadius: (SEGMENTED - 6) / 2,
    backgroundColor: THUMB,
  },
  segment: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentLabel: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '500',
  },
  segmentLabelOn: {
    color: THUMB_INK,
    fontWeight: '600',
  },
  legend: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 20,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: TILE,
  },
  sample: {
    width: 14,
    height: 2,
    borderRadius: 1,
    marginRight: 8,
  },
  chipLabel: {
    color: INK,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  chart: {
    flex: 1,
    marginTop: 24,
    marginBottom: 8,
  },
  yLabel: {
    position: 'absolute',
    left: 0,
    width: AXIS - 8,
    color: MUTE,
    fontSize: 10,
    lineHeight: 14,
    fontVariant: ['tabular-nums'],
  },
  canvas: {
    position: 'absolute',
    left: AXIS,
    top: 0,
  },
  ticks: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: TICKS_HEIGHT,
  },
  tick: {
    position: 'absolute',
    top: 10,
    width: 60,
    textAlign: 'center',
    color: MUTE,
    fontSize: 10,
    lineHeight: 13,
  },
  tickFirst: {
    textAlign: 'left',
    transform: [{ translateX: 30 }],
  },
  tickLast: {
    textAlign: 'right',
    transform: [{ translateX: -30 }],
  },
  stats: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 12,
  },
  tile: {
    flex: 1,
    padding: 16,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: TILE,
  },
  tileLabel: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
  },
  rolling: {
    marginTop: 6,
    height: 32,
    overflow: 'hidden',
  },
  tileValue: {
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
});
