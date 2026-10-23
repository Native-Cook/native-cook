import { Canvas, Picture, Skia, createPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
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
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

export type RevenueGroup = {
  /** The caption under the group, before its total: "JUN". */
  label: string;
  total: number;
  /** Dots in each column, baseline included (at least 1). */
  heights: number[];
};

export type RevenueRange = {
  /** The tab's label. */
  name: string;
  /** The headline: how the second group compares with the first, in percent. */
  change: number;
  /** The earlier and the later period, drawn left and right of a one-column gap. */
  groups: [RevenueGroup, RevenueGroup];
};

type Props = {
  title?: string;
  ranges?: RevenueRange[];
  /** The tab shown at rest. */
  initialRange?: number;
  symbol?: string;
  /** Switches to the weekly range and back, and highlights the earlier group, every LOOP seconds. */
  autoPlay?: boolean;
  onRangeChange?: (index: number, range: RevenueRange) => void;
  /** Called with 0 or 1 when a touch on the chart highlights a group. */
  onSelectGroup?: (group: number, range: RevenueRange) => void;
};

const RANGES: RevenueRange[] = [
  {
    name: 'DAILY',
    change: 33,
    groups: [
      { label: 'MON', total: 412, heights: [1, 1, 1, 2, 3, 5, 4, 3, 4, 6, 5, 3, 2, 2, 3, 4, 3, 2, 2, 1, 1, 1, 1] },
      { label: 'TUE', total: 548, heights: [1, 1, 2, 3, 4, 7, 6, 4, 5, 8, 9, 6, 4, 3, 4, 6, 5, 4, 3, 2, 2, 1, 1] },
    ],
  },
  {
    name: 'WEEKLY',
    change: 84,
    groups: [
      { label: 'WK 24', total: 2910, heights: [2, 3, 5, 4, 6, 3, 2, 1, 3, 4, 7, 5, 3, 2, 2, 4, 5, 3, 2, 1, 2, 3, 2] },
      { label: 'WK 25', total: 5336, heights: [3, 5, 8, 6, 4, 9, 11, 7, 5, 3, 4, 6, 8, 10, 7, 5, 3, 2, 4, 6, 5, 3, 2] },
    ],
  },
  {
    name: 'MONTHLY',
    change: 326,
    groups: [
      { label: 'MAY', total: 3250, heights: [1, 2, 4, 8, 3, 3, 4, 6, 3, 1, 2, 2, 3, 3, 4, 3, 3, 1, 2, 2, 2] },
      {
        label: 'JUN',
        total: 12392,
        heights: [6, 7, 12, 10, 8, 9, 6, 5, 4, 5, 2, 1, 3, 2, 1, 5, 2, 1, 1, 4, 3, 5, 3, 4, 2],
      },
    ],
  },
];

const PAGE = '#eeeeee';
const CARD = '#f7f7f7';
const LINE = '#e4e4e4';
const INK = '#111111';
const MUTE = '#a0a0a0';
// Dots of the highlighted group and of the other one, and the steps mixed between them.
const DARK = '#4a4a4a';
const LIGHT = '#c9c9c9';
const SHADES = 16;
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

const MARGIN = 16;
const MAX_CARD = 420;
const RADIUS = 32;
const PAD = 22;
const HEADER = 98;
// The chart: rows of dots above the captions, with room on top for dots that drop in.
const ROWS = 14;
const CHART_TOP = 18;
const DOT = 0.46;
// The last columns fade into the card's right edge.
const EDGE_COLUMNS = 4;
const EDGE_ALPHA = 0.2;

// Seconds. A range switch walks left to right, one column every COLUMN_STAGGER: surplus dots rise
// and fade top first, new dots drop in bottom first and land with a small overshoot.
const COLUMN_STAGGER = 0.018;
const DOT_STAGGER = 0.04;
const LEAVE_FOR = 0.26;
const LEAVE_RISE = 14;
const DROP_FOR = 0.42;
const DROP = 40;
const OVERSHOOT = 1.9;
// A highlight travels out from the touched column; dots near its front lift and drop back.
const WAVE_STAGGER = 0.025;
const SHADE_FOR = 0.14;
const LIFT = 3;
const LIFT_FOR = 0.34;
const ROW_LAG = 0.008;
// Once a switch has landed a gentle ripple runs across all the dots.
const RIPPLE_AFTER = 0.95;
const RIPPLE_STAGGER = 0.016;
const REDUCED_FADE = 0.16;
// The headline and the captions count to their new values.
const COUNT = 700;
const UNDERLINE = 260;

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 9;
const HIGHLIGHT_AT = 0.8;
// The column the autoPlay finger touches to highlight the earlier group.
const HIGHLIGHT_COLUMN = 9;
const WEEKLY_AT = 2.6;
const WEEKLY = 1;
const BACK_AT = 5.2;
// After a touch the block waits, then returns to rest and loops again once it has settled.
const USER_HOLD = 3;
const SETTLE = 2;

function clamp(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function backOut(t: number) {
  'worklet';
  const x = t - 1;
  return 1 + (OVERSHOOT + 1) * x * x * x + OVERSHOOT * x * x;
}

function mix(a: string, b: string, t: number) {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
  return `#${pa.map((v, k) => Math.round(v + (pb[k] - v) * t).toString(16).padStart(2, '0')).join('')}`;
}

function money(symbol: string, value: number) {
  return `${symbol}${Math.round(value).toLocaleString('en-US')}`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** One range laid out on the grid: dots per column and the group each column belongs to (-1: none). */
function layout(range: RevenueRange, columns: number) {
  const [early, late] = range.groups;
  const heights = Array.from({ length: columns }, () => 0);
  const groups = Array.from({ length: columns }, () => -1);
  early.heights.forEach((h, c) => {
    heights[c] = h;
    groups[c] = 0;
  });
  late.heights.forEach((h, c) => {
    heights[early.heights.length + 1 + c] = h;
    groups[early.heights.length + 1 + c] = 1;
  });
  return { heights, groups, start: [0, early.heights.length + 1] };
}

/**
 * A revenue card whose histogram is a matrix of dots: the earlier period in light grey, the later
 * one dark. A touch on the chart moves the highlight to the group under it as a wave with a small
 * lift; the range tabs re-flow the dots column by column, surplus dots rising away and new ones
 * dropping in, while the headline and the captions count to the new values.
 */
export function DotMatrixRevenue({
  title = 'REVENUE',
  ranges = RANGES,
  initialRange = ranges.length - 1,
  symbol = '$',
  autoPlay = false,
  onRangeChange,
  onSelectGroup,
}: Props) {
  const reduced = useReducedMotion();
  const window = useWindowDimensions();
  const cardWidth = Math.min(MAX_CARD, window.width - MARGIN * 2);

  const columns = useMemo(
    () => Math.max(...ranges.map((r) => r.groups[0].heights.length + 1 + r.groups[1].heights.length)),
    [ranges],
  );
  const laid = useMemo(() => ranges.map((r) => layout(r, columns)), [ranges, columns]);
  const heightTable = useMemo(() => laid.map((l) => l.heights), [laid]);
  const groupTable = useMemo(() => laid.map((l) => l.groups), [laid]);
  const shades = useMemo(() => Array.from({ length: SHADES + 1 }, (_, i) => mix(LIGHT, DARK, i / SHADES)), []);

  const pitch = (cardWidth - PAD) / columns;
  const chartHeight = ROWS * pitch + CHART_TOP;
  const rest = Math.min(Math.max(0, initialRange), ranges.length - 1);

  const clock = useSharedValue(0);
  const origin = useSharedValue(-1);
  const drawTime = useSharedValue(0);
  const busyUntil = useSharedValue(0);
  const range = useSharedValue(rest);
  const active = useSharedValue(1);
  // Per column: dots before and after the last switch and when it reaches the column; the shade
  // (0 light, 1 dark) before and after the last highlight and when it reaches the column; when the
  // column last lifted.
  const grid = useSharedValue({
    from: heightTable[rest].slice(),
    to: heightTable[rest].slice(),
    flowAt: heightTable[rest].map(() => -100),
    shadeFrom: groupTable[rest].map((g): number => (g === 1 ? 1 : 0)),
    shadeTo: groupTable[rest].map((g): number => (g === 1 ? 1 : 0)),
    shadeAt: heightTable[rest].map(() => -100),
    liftAt: heightTable[rest].map(() => -100),
  });
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(-USER_HOLD);
  const previousLocal = useSharedValue(-1);
  const dragging = useSharedValue(false);

  const [shown, setShown] = useState({ range: rest, active: 1 });
  const [counts, setCounts] = useState({ change: ranges[rest].change, early: ranges[rest].groups[0].total, late: ranges[rest].groups[1].total });
  const change = useSharedValue(ranges[rest].change);
  const counted = useSharedValue(1);
  const [tabs, setTabs] = useState<{ x: number; width: number }[]>([]);

  /** The shade a column shows at `t`, so that a new highlight starts from where the old one is. */
  const shadeNow = (g: { shadeFrom: number[]; shadeTo: number[]; shadeAt: number[] }, c: number, t: number) => {
    'worklet';
    const u = clamp((t - g.shadeAt[c]) / (reduced ? REDUCED_FADE : SHADE_FOR));
    return g.shadeFrom[c] + (g.shadeTo[c] - g.shadeFrom[c]) * u;
  };

  /** Highlights the group under column `x`; the change travels out from it. */
  const highlight = (x: number, byHand: boolean, t: number) => {
    'worklet';
    const column = Math.min(columns - 1, Math.max(0, Math.round(x)));
    const group = groupTable[range.get()][column];
    if (group < 0 || group === active.get()) return;
    const g = grid.get();
    const shadeFrom = g.shadeFrom.slice();
    const shadeTo = g.shadeTo.slice();
    const shadeAt = g.shadeAt.slice();
    const liftAt = g.liftAt.slice();
    const groups = groupTable[range.get()];
    let last = t;
    for (let c = 0; c < columns; c++) {
      shadeFrom[c] = shadeNow(g, c, t);
      shadeTo[c] = groups[c] === group ? 1 : 0;
      shadeAt[c] = t + (reduced ? 0 : Math.abs(c - column) * WAVE_STAGGER);
      if (!reduced) liftAt[c] = shadeAt[c];
      last = Math.max(last, shadeAt[c]);
    }
    grid.set({ ...g, shadeFrom, shadeTo, shadeAt, liftAt });
    active.set(group);
    busyUntil.set(Math.max(busyUntil.get(), last + LIFT_FOR + ROWS * ROW_LAG));
    drawTime.set(t);
    scheduleOnRN(setShown, { range: range.get(), active: group });
    if (byHand) {
      scheduleOnRN(tick);
      if (onSelectGroup) scheduleOnRN(onSelectGroup, group, ranges[range.get()]);
    }
  };

  /** Re-flows the dots into another range, with its later group highlighted. */
  const switchRange = (index: number, byHand: boolean, t: number) => {
    'worklet';
    if (index === range.get()) return;
    const g = grid.get();
    const heights = heightTable[index];
    const groups = groupTable[index];
    const from = g.to.slice();
    const flowAt = g.flowAt.slice();
    const shadeFrom = g.shadeFrom.slice();
    const shadeTo = g.shadeTo.slice();
    const shadeAt = g.shadeAt.slice();
    const liftAt = g.liftAt.slice();
    let last = t;
    for (let c = 0; c < columns; c++) {
      flowAt[c] = t + (reduced ? 0 : c * COLUMN_STAGGER);
      shadeFrom[c] = shadeNow(g, c, t);
      shadeTo[c] = groups[c] === 1 ? 1 : 0;
      shadeAt[c] = flowAt[c];
      if (!reduced) liftAt[c] = flowAt[c] + RIPPLE_AFTER + c * (RIPPLE_STAGGER - COLUMN_STAGGER);
      last = Math.max(last, reduced ? flowAt[c] + REDUCED_FADE : liftAt[c] + LIFT_FOR + ROWS * ROW_LAG);
    }
    grid.set({ from, to: heights.slice(), flowAt, shadeFrom, shadeTo, shadeAt, liftAt });
    range.set(index);
    active.set(1);
    change.set(reduced ? ranges[index].change : withTiming(ranges[index].change, { duration: COUNT, easing: Easing.out(Easing.cubic) }));
    counted.set(0);
    counted.set(reduced ? 1 : withTiming(1, { duration: COUNT, easing: Easing.out(Easing.cubic) }));
    busyUntil.set(Math.max(busyUntil.get(), last));
    drawTime.set(t);
    scheduleOnRN(setShown, { range: index, active: 1 });
    if (byHand) {
      scheduleOnRN(tick);
      if (onRangeChange) scheduleOnRN(onRangeChange, index, ranges[index]);
    }
  };

  const touch = () => {
    'worklet';
    loopStart.set(-1);
    lastTouch.set(clock.get());
  };

  useFrameCallback((frame) => {
    if (origin.get() < 0) origin.set(frame.timestamp);
    const t = (frame.timestamp - origin.get()) / 1000;
    clock.set(t);
    if (autoPlay && !dragging.get()) {
      let start = loopStart.get();
      if (start < 0 && t - lastTouch.get() >= USER_HOLD) {
        // Back to rest first: the resting range with its later group highlighted.
        const atRest = range.get() === rest && active.get() === 1;
        if (range.get() !== rest) switchRange(rest, false, t);
        else if (active.get() !== 1) highlight(laid[rest].start[1], false, t);
        start = t + (atRest ? 0 : SETTLE);
        loopStart.set(start);
        previousLocal.set(-1);
      }
      if (start >= 0 && t >= start) {
        const local = (t - start) % LOOP;
        const before = previousLocal.get();
        // A step runs when the loop's time passes it, the wrap from the end to the start included.
        const passed = (at: number) => (before <= local ? before < at && at <= local : before < at || at <= local);
        if (passed(HIGHLIGHT_AT)) highlight(HIGHLIGHT_COLUMN, false, t);
        if (passed(WEEKLY_AT)) switchRange(Math.min(WEEKLY, ranges.length - 1), false, t);
        if (passed(BACK_AT)) switchRange(rest, false, t);
        previousLocal.set(local);
      }
    }
    if (t <= busyUntil.get()) drawTime.set(t);
  });

  useAnimatedReaction(
    () => ({ change: Math.round(change.get()), counted: Math.round(counted.get() * 200) }),
    (now, before) => {
      if (before && now.change === before.change && now.counted === before.counted) return;
      const groups = ranges[range.get()].groups;
      // The step the count is at, not the live value, so that the last update lands on the total.
      const share = now.counted / 200;
      scheduleOnRN(setCounts, { change: now.change, early: groups[0].total * share, late: groups[1].total * share });
    },
  );

  const picture = useDerivedValue(() => {
    const now = drawTime.get();
    const g = grid.get();
    return createPicture((canvas) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const colors = [];
      for (let i = 0; i <= SHADES; i++) colors.push(Skia.Color(shades[i]));
      const radius = (pitch * DOT) / 2;
      const floor = chartHeight - pitch / 2;
      for (let c = 0; c < columns; c++) {
        const from = g.from[c];
        const to = g.to[c];
        const count = Math.max(from, to);
        if (count === 0) continue;
        const since = now - g.flowAt[c];
        const shade = shadeNow(g, c, now);
        const edge = c >= columns - EDGE_COLUMNS ? 1 - ((c - (columns - EDGE_COLUMNS) + 1) / EDGE_COLUMNS) * (1 - EDGE_ALPHA) : 1;
        paint.setColor(colors[Math.round(shade * SHADES)]);
        const x = PAD + c * pitch + pitch / 2;
        for (let r = 0; r < count; r++) {
          let alpha = 1;
          let dy = 0;
          if (r >= to) {
            // Leaving, top dot first.
            const u = clamp((since - (from - 1 - r) * (reduced ? 0 : DOT_STAGGER)) / (reduced ? REDUCED_FADE : LEAVE_FOR));
            alpha = 1 - u;
            if (!reduced) dy = -LEAVE_RISE * (1 - (1 - u) * (1 - u));
          } else if (r >= from) {
            // Arriving, bottom dot first.
            if (reduced) alpha = clamp(since / REDUCED_FADE);
            else {
              const u = clamp((since - (r - from) * DOT_STAGGER) / DROP_FOR);
              alpha = clamp(u * 3);
              dy = -DROP * (1 - backOut(u));
            }
          }
          if (alpha <= 0.01) continue;
          const lifted = (now - g.liftAt[c] - r * ROW_LAG) / LIFT_FOR;
          if (lifted > 0 && lifted < 1) dy -= LIFT * Math.sin(Math.PI * lifted);
          paint.setAlphaf(alpha * edge);
          canvas.drawCircle(x, floor - r * pitch + dy, radius, paint);
        }
      }
    });
  });

  const underlineStyle = useAnimatedStyle(() => {
    const tab = tabs[shown.range];
    if (!tab) return { opacity: 0 };
    const glide = { duration: reduced ? 0 : UNDERLINE, easing: Easing.out(Easing.cubic) };
    return { opacity: 1, width: withTiming(tab.width, glide), transform: [{ translateX: withTiming(tab.x, glide) }] };
  });

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      dragging.set(true);
      touch();
      highlight((e.x - PAD) / pitch - 0.5, true, clock.get());
    })
    .onUpdate((e) => {
      touch();
      highlight((e.x - PAD) / pitch - 0.5, true, clock.get());
    })
    .onFinalize(() => {
      dragging.set(false);
      touch();
    });

  const pickRange = (index: number) => {
    scheduleOnUI(() => {
      'worklet';
      touch();
      switchRange(index, true, clock.get());
    });
  };

  const onTabLayout = (index: number) => (event: LayoutChangeEvent) => {
    const { x, width } = event.nativeEvent.layout;
    setTabs((list) => {
      const next = list.slice();
      next[index] = { x, width };
      return next;
    });
  };

  const current = ranges[shown.range];
  const starts = laid[shown.range].start;
  const amounts = [counts.early, counts.late];

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.card, { width: cardWidth }]}>
        <View style={styles.header}>
          <View>
            <Text style={styles.title}>{title}</Text>
            <View style={styles.headline} accessible accessibilityLabel={`${title} ${current.change >= 0 ? 'up' : 'down'} ${Math.abs(current.change)} percent`}>
              <Text style={styles.change}>
                {counts.change >= 0 ? '+' : '−'}
                {Math.abs(counts.change)}
              </Text>
              <Text style={styles.percent}>%</Text>
            </View>
          </View>
          <View style={styles.tabs} accessibilityRole="tablist">
            {ranges.map((r, i) => (
              <Pressable
                key={r.name}
                onLayout={onTabLayout(i)}
                onPress={() => pickRange(i)}
                hitSlop={{ top: 12, bottom: 12, left: 4, right: 4 }}
                accessibilityRole="tab"
                accessibilityState={{ selected: i === shown.range }}
              >
                <Animated.Text
                  style={[
                    styles.tab,
                    { color: i === shown.range ? INK : MUTE, transitionProperty: 'color', transitionDuration: reduced ? 0 : 200 },
                  ]}
                >
                  {r.name}
                </Animated.Text>
              </Pressable>
            ))}
            <Animated.View pointerEvents="none" style={[styles.underline, underlineStyle]} />
          </View>
        </View>
        <View style={styles.divider} />

        <GestureDetector gesture={gesture}>
          <View
            style={{ height: chartHeight }}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Revenue chart"
            accessibilityValue={{ text: `${current.groups[shown.active].label} highlighted` }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(e) => {
              const group = e.nativeEvent.actionName === 'increment' ? 1 : 0;
              scheduleOnUI(() => {
                'worklet';
                touch();
                highlight(laid[range.get()].start[group], true, clock.get());
              });
            }}
          >
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>

        <View style={styles.captions}>
          {current.groups.map((group, i) => (
            <Animated.Text
              key={i}
              numberOfLines={1}
              style={[
                styles.caption,
                {
                  left: PAD + starts[i] * pitch + pitch / 2 - (pitch * DOT) / 2,
                  color: i === shown.active ? INK : MUTE,
                  transitionProperty: 'color',
                  transitionDuration: reduced ? 0 : 200,
                },
              ]}
            >
              {group.label} {money(symbol, amounts[i])}
            </Animated.Text>
          ))}
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PAGE,
  },
  card: {
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: LINE,
    backgroundColor: CARD,
    overflow: 'hidden',
  },
  header: {
    height: HEADER,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: 24,
    paddingLeft: PAD,
    paddingRight: PAD,
  },
  title: {
    color: INK,
    fontFamily: MONO,
    fontSize: 13,
    lineHeight: 18,
    letterSpacing: 0.5,
  },
  headline: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginTop: 4,
  },
  change: {
    color: INK,
    fontSize: 40,
    lineHeight: 46,
    fontWeight: '300',
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
  },
  percent: {
    marginLeft: 4,
    color: INK,
    fontSize: 20,
    lineHeight: 24,
    fontWeight: '300',
  },
  tabs: {
    flexDirection: 'row',
    gap: 14,
    height: 26,
  },
  tab: {
    fontFamily: MONO,
    fontSize: 12,
    lineHeight: 18,
    letterSpacing: 0.3,
  },
  underline: {
    position: 'absolute',
    left: 0,
    top: 21,
    height: 1,
    backgroundColor: INK,
  },
  divider: {
    height: 1,
    backgroundColor: LINE,
  },
  captions: {
    height: 52,
  },
  caption: {
    position: 'absolute',
    top: 10,
    fontFamily: MONO,
    fontSize: 13,
    lineHeight: 18,
  },
});
