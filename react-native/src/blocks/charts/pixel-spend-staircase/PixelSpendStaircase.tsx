import { BlurStyle, Canvas, Path, Picture, Skia, createPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
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
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = {
  /** What was spent on each day of this month so far; the last entry is today. */
  spending?: number[];
  /** What was spent on each day of last month, drawn as the grey staircase behind. */
  previousSpending?: number[];
  month?: string;
  previousMonth?: string;
  symbol?: string;
  todayLabel?: string;
  /** Shown under the chart while this month's total is below last month's on the same day. */
  lessLabel?: string;
  moreLabel?: string;
  /** Scrubs back to an early day and lights the month up again every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the day (1-based), that day's spend and the month's total up to it. */
  onSelect?: (day: number, spend: number, total: number) => void;
  /** The chevron under the chart: where a detail sheet would open. */
  onOpenDetails?: () => void;
};

// May: steady days, rent on the 18th, and $123 today. April ran ahead of it from the 6th on.
const SPENDING = [620, 560, 70, 60, 250, 590, 90, 780, 70, 290, 320, 310, 70, 60, 480, 80, 320, 3030, 470, 613, 123];
const PREVIOUS = [
  400, 500, 100, 150, 250, 1160, 60, 1080, 60, 840, 50, 250, 300, 300, 700, 1500, 80, 820, 600, 60, 40, 40, 60, 50, 60, 57,
];

const BACKGROUND = '#1a1a1a';
const INK = '#f2f2f2';
const MUTE = '#8a8a8a';
const FAINT = '#6f6f6f';
const CHEVRON = '#4a4a4a';
// Cells by height, bottom to top: the bottom rows sink into the screen.
const GREY_STOPS: [number, string][] = [
  [0, '#1f1f1f'],
  [0.14, '#2a2a2a'],
  [1, '#2c2c2c'],
];
const EMBER_STOPS: [number, string][] = [
  [0, '#2e1d15'],
  [0.1, '#47291a'],
  [0.4, '#8e4321'],
  [0.7, '#d2552a'],
  [0.86, '#ee6430'],
  [1, '#f56b32'],
];
const CURSOR = '#ff4d26';
const FLASH = '#ffd9c2';

// The grid: cells fill the width with a hairline gap; rows are as many as fit, up to MAX_ROWS.
const SIDE = 4;
const GAP = 1.5;
const RADIUS = 1.5;
const MAX_ROWS = 31;
// Room above the tallest column for the floating labels. Last month's label sits this many rows
// higher than the cursor's would, so the two never meet at the end of the month.
const LABEL_SPACE = 76;
const PREVIOUS_RISE = 2;
const LABEL_GAP = 8;
const LABEL_WIDTH = 180;
const LABEL_LINE = 20;
// The glow behind the cursor cell, in cells, and its blur in points.
const GLOW = 1.7;
const GLOW_BLUR = 5;
const GLOW_ALPHA = 0.32;

// Seconds. A column lights from the bottom up, one row every ROW_ON, and goes dark from the top.
const ROW_ON = 0.012;
const ROW_OFF = 0.02;
const CELL_IN = 0.09;
const CELL_OUT = 0.12;
// Columns passed in one jump (a tap far from the cursor) start one after the other.
const COLUMN_STAGGER = 0.035;
// The top cell of a column flashes when it lights.
const FLASH_FOR = 0.35;
// A brighter band runs along the tops of the lit columns once the cursor is back at today.
const SHIMMER_AFTER = 0.15;
const SHIMMER_FOR = 1.2;
const SHIMMER_DEPTH = 4;
const SHIMMER_WIDTH = 1.4;
const SHIMMER_PEAK = 0.22;
const REDUCED_FADE = 0.16;
// The amounts roll to their new value, and the label glides to its column.
const ROLL = 320;
const LABEL_GLIDE = 180;

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 9;
const SCRUB_TO = 4;
const LEFT_AT = 0.8;
const LEFT_FOR = 1.8;
const RIGHT_AT = 3.3;
const RIGHT_FOR = 2.4;
// After a touch the block waits this long, then glides back to today and loops again.
const USER_HOLD = 3;
const RETURN_FOR = 0.9;

const HEADER_TOP = 36;
const AMOUNT = 72;

function clamp(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function easeInOut(t: number) {
  'worklet';
  const x = clamp(t);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** The cursor column the autoPlay finger is over at `t` seconds into the loop. */
function film(t: number, today: number, low: number, reduced: boolean) {
  'worklet';
  // Under reduced motion the cursor jumps instead of travelling.
  if (reduced) return t >= LEFT_AT && t < RIGHT_AT ? low : today;
  if (t < LEFT_AT) return today;
  if (t < LEFT_AT + LEFT_FOR) return today + (low - today) * easeInOut((t - LEFT_AT) / LEFT_FOR);
  if (t < RIGHT_AT) return low;
  return low + (today - low) * easeInOut((t - RIGHT_AT) / RIGHT_FOR);
}

function hex(color: string) {
  return [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16));
}

/** The colour at `t` along a list of stops. */
function along(stops: [number, string][], t: number) {
  let i = 0;
  while (i < stops.length - 2 && t > stops[i + 1][0]) i++;
  const [t0, c0] = stops[i];
  const [t1, c1] = stops[i + 1];
  const u = Math.min(1, Math.max(0, (t - t0) / (t1 - t0)));
  const a = hex(c0);
  const b = hex(c1);
  return `#${a.map((v, k) => Math.round(v + (b[k] - v) * u).toString(16).padStart(2, '0')).join('')}`;
}

function running(values: number[]) {
  let sum = 0;
  return values.map((value) => (sum += value));
}

function money(symbol: string, value: number) {
  return `${symbol}${Math.round(value).toLocaleString('en-US')}`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

// A wide, shallow chevron in a 56 × 16 box.
const CHEVRON_PATH = 'M4 4L28 12L52 4';

/**
 * A month's spending as a staircase of pixels: each column is a day, lit from the bottom up to the
 * month's running total, over last month's staircase in grey. Dragging moves the cursor (the bright
 * top cell): days after it go dark from the top, and moving forward lights them again in a rising
 * pixel sweep, while the amount and the label above the cursor roll to the day.
 */
export function PixelSpendStaircase({
  spending = SPENDING,
  previousSpending = PREVIOUS,
  month = 'May',
  previousMonth = 'Apr',
  symbol = '$',
  todayLabel = 'today',
  lessLabel = "You’re spending less…",
  moreLabel = "You’re spending more…",
  autoPlay = false,
  onSelect,
  onOpenDetails,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [chart, setChart] = useState({ width: 0, height: 0 });

  const today = spending.length - 1;
  const columns = Math.max(spending.length, previousSpending.length);
  const totals = useMemo(() => running(spending), [spending]);
  const previousTotals = useMemo(() => running(previousSpending), [previousSpending]);

  const pitch = chart.width > 0 ? (chart.width - SIDE * 2) / columns : 0;
  const rows = pitch > 0 ? Math.max(4, Math.min(MAX_ROWS, Math.floor((chart.height - LABEL_SPACE) / pitch))) : MAX_ROWS;
  const left = (chart.width - pitch * columns) / 2;
  const floor = chart.height;

  // Column heights in cells: the taller month sets the scale.
  const { lit, ghost, grey, ember } = useMemo(() => {
    const top = Math.max(totals[totals.length - 1] ?? 1, previousTotals[previousTotals.length - 1] ?? 1);
    const cells = (value: number) => (value > 0 ? Math.max(1, Math.round((value / top) * rows)) : 0);
    const shades = (stops: [number, string][]) => Array.from({ length: rows }, (_, r) => along(stops, r / (rows - 1)));
    return {
      lit: totals.map(cells),
      ghost: Array.from({ length: columns }, (_, c) => cells(previousTotals[c] ?? 0)),
      grey: shades(GREY_STOPS),
      ember: shades(EMBER_STOPS),
    };
  }, [totals, previousTotals, rows, columns]);

  // Seconds since the first frame; the picture's own time only advances while something moves.
  const clock = useSharedValue(0);
  const origin = useSharedValue(-1);
  const drawTime = useSharedValue(0);
  const busyUntil = useSharedValue(0);
  const selected = useSharedValue(today);
  // Per column: lit (1) or dark (0), and when it last changed.
  const state = useSharedValue({
    on: Array.from({ length: columns }, (_, c) => (c <= today ? 1 : 0)),
    at: Array.from({ length: columns }, () => -100),
  });
  const shimmerAt = useSharedValue(-100);
  // The loop's start in clock seconds, or -1 while a touch is in charge.
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(-USER_HOLD);
  const returnFrom = useSharedValue(today);
  const dragging = useSharedValue(false);

  const [shown, setShown] = useState({ day: today, spend: spending[today] ?? 0, total: totals[today] ?? 0 });
  const amount = useSharedValue(spending[today] ?? 0);
  const total = useSharedValue(totals[today] ?? 0);

  /** Moves the cursor to the column nearest `x`; columns passed light up or go dark. */
  const scrub = (x: number, byHand: boolean, t: number) => {
    'worklet';
    const next = Math.min(today, Math.max(0, Math.round(x)));
    const old = selected.get();
    if (next === old) return;
    const current = state.get();
    const on = current.on.slice();
    const at = current.at.slice();
    const stagger = reduced ? 0 : COLUMN_STAGGER;
    let last = t;
    if (next > old) {
      for (let c = old + 1; c <= next; c++) {
        on[c] = 1;
        at[c] = t + (c - old - 1) * stagger;
        last = at[c];
      }
    } else {
      for (let c = old; c > next; c--) {
        on[c] = 0;
        at[c] = t + (old - c) * stagger;
        last = at[c];
      }
    }
    state.set({ on, at });
    selected.set(next);
    let busy = last + rows * ROW_OFF + CELL_OUT + FLASH_FOR;
    if (next === today && !reduced) {
      const start = at[today] + (lit[today] - 1) * ROW_ON + SHIMMER_AFTER;
      shimmerAt.set(start);
      busy = Math.max(busy, start + SHIMMER_FOR);
    }
    busyUntil.set(Math.max(busyUntil.get(), busy));
    drawTime.set(t);
    if (byHand) {
      scheduleOnRN(tick);
      if (onSelect) scheduleOnRN(onSelect, next + 1, spending[next], totals[next]);
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
    if (autoPlay && !dragging.get() && pitch > 0) {
      let start = loopStart.get();
      if (start < 0 && t - lastTouch.get() >= USER_HOLD) {
        const from = selected.get();
        returnFrom.set(from);
        start = t + (from === today || reduced ? 0 : RETURN_FOR);
        loopStart.set(start);
      }
      if (start >= 0) {
        const from = returnFrom.get();
        const x =
          t < start
            ? from + (today - from) * easeInOut(1 - (start - t) / RETURN_FOR)
            : film((t - start) % LOOP, today, Math.min(SCRUB_TO, today), reduced);
        scrub(x, false, t);
      }
    }
    if (t <= busyUntil.get()) drawTime.set(t);
  });

  // The amounts roll to the selected day, and the text follows on the JS side.
  useAnimatedReaction(
    () => selected.get(),
    (day, previous) => {
      if (previous === null || day === previous) return;
      const spend = spending[day];
      const sum = totals[day];
      amount.set(reduced ? spend : withTiming(spend, { duration: ROLL, easing: Easing.out(Easing.cubic) }));
      total.set(reduced ? sum : withTiming(sum, { duration: ROLL, easing: Easing.out(Easing.cubic) }));
    },
  );
  useAnimatedReaction(
    () => ({ day: selected.get(), spend: Math.round(amount.get()), total: Math.round(total.get()) }),
    (now, previous) => {
      if (previous && now.day === previous.day && now.spend === previous.spend && now.total === previous.total) return;
      scheduleOnRN(setShown, now);
    },
  );

  const picture = useDerivedValue(() => {
    const now = drawTime.get();
    const { on, at } = state.get();
    const cursor = selected.get();
    const shimmerStart = shimmerAt.get();
    return createPicture((canvas) => {
      if (pitch <= 0) return;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const size = pitch - GAP;
      const greys = [];
      const embers = [];
      for (let r = 0; r < rows; r++) {
        greys.push(Skia.Color(grey[r]));
        embers.push(Skia.Color(ember[r]));
      }
      const flash = Skia.Color(FLASH);
      const cell = (c: number, r: number) => Skia.RRectXY(Skia.XYWHRect(left + c * pitch + GAP / 2, floor - (r + 1) * pitch + GAP / 2, size, size), RADIUS, RADIUS);
      const shimmer = (now - shimmerStart) / SHIMMER_FOR;
      const band = shimmer >= 0 && shimmer <= 1 ? -2 + shimmer * (today + 4) : -100;
      let cursorLit = 0;

      for (let c = 0; c < columns; c++) {
        const own = c <= today ? lit[c] : 0;
        const height = Math.max(own, ghost[c]);
        const since = now - at[c];
        for (let r = 0; r < height; r++) {
          paint.setColor(greys[r]);
          paint.setAlphaf(1);
          canvas.drawRRect(cell(c, r), paint);
          if (r >= own) continue;
          let level;
          if (reduced) level = on[c] ? clamp(since / REDUCED_FADE) : 1 - clamp(since / REDUCED_FADE);
          else if (on[c]) level = clamp((since - r * ROW_ON) / CELL_IN);
          else level = 1 - clamp((since - (own - 1 - r) * ROW_OFF) / CELL_OUT);
          if (level <= 0) continue;
          paint.setColor(embers[r]);
          paint.setAlphaf(level);
          canvas.drawRRect(cell(c, r), paint);
          // The top cell flashes as it lights, and the shimmer brightens the top few rows.
          let glint = 0;
          if (r === own - 1 && on[c] && !reduced) {
            const lighted = since - r * ROW_ON;
            if (lighted >= 0) glint = 0.75 * (1 - clamp(lighted / FLASH_FOR));
          }
          const depth = own - 1 - r;
          if (depth < SHIMMER_DEPTH && band > -100) {
            const d = (c - band) / SHIMMER_WIDTH;
            glint = Math.max(glint, SHIMMER_PEAK * Math.exp(-d * d) * (1 - depth / SHIMMER_DEPTH));
          }
          if (glint > 0.005) {
            paint.setColor(flash);
            paint.setAlphaf(glint * level);
            canvas.drawRRect(cell(c, r), paint);
          }
          if (c === cursor && r === own - 1) cursorLit = level;
        }
      }

      // The cursor: the top cell of the selected day, red, in a soft square glow.
      const top = lit[cursor] - 1;
      if (cursorLit > 0 && top >= 0) {
        const halo = pitch * GLOW;
        const cx = left + cursor * pitch + pitch / 2;
        const cy = floor - (top + 0.5) * pitch;
        const glow = Skia.Paint();
        glow.setColor(Skia.Color(CURSOR));
        glow.setAlphaf(GLOW_ALPHA * cursorLit);
        glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, GLOW_BLUR, true));
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - halo / 2, cy - halo / 2, halo, halo), 3, 3), glow);
        paint.setColor(Skia.Color(CURSOR));
        paint.setAlphaf(cursorLit);
        canvas.drawRRect(cell(cursor, top), paint);
      }
    });
  });

  // The label rides above the cursor's column, clear of last month's staircase.
  const labelStyle = useAnimatedStyle(() => {
    const c = selected.get();
    const height = Math.max(lit[c], ghost[c]);
    const x = Math.min(chart.width - LABEL_WIDTH / 2 - SIDE, Math.max(LABEL_WIDTH / 2 + SIDE, left + c * pitch + pitch / 2));
    const y = floor - height * pitch - LABEL_GAP - LABEL_LINE;
    const glide = { duration: LABEL_GLIDE, easing: Easing.out(Easing.cubic) };
    return {
      transform: [
        { translateX: reduced ? x - LABEL_WIDTH / 2 : withTiming(x - LABEL_WIDTH / 2, glide) },
        { translateY: reduced ? y : withTiming(y, glide) },
      ],
    };
  });

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      dragging.set(true);
      touch();
      scrub((e.x - left) / pitch - 0.5, true, clock.get());
    })
    .onUpdate((e) => {
      touch();
      scrub((e.x - left) / pitch - 0.5, true, clock.get());
    })
    .onFinalize(() => {
      dragging.set(false);
      touch();
    });

  const step = (direction: number) => {
    scheduleOnUI(() => {
      'worklet';
      touch();
      scrub(selected.get() + direction, true, clock.get());
    });
  };

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setChart({ width, height });
  };

  const isToday = shown.day === today;
  const previousAtDay = previousTotals[Math.min(shown.day, previousTotals.length - 1)] ?? 0;
  const lastGhost = ghost[columns - 1] ?? 0;
  const dayName = `${month} ${shown.day + 1}`;

  return (
    <GestureHandlerRootView style={[styles.screen, { paddingTop: insets.top }]}>
      <StatusBar style="light" />
      <View style={styles.header}>
        <Text style={styles.amount} numberOfLines={1} adjustsFontSizeToFit>
          {money(symbol, shown.spend)}
        </Text>
        <Text style={styles.subtitle}>{isToday ? todayLabel : dayName}</Text>
      </View>

      <GestureDetector gesture={gesture}>
        <View
          style={styles.chart}
          onLayout={onLayout}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`Spending in ${month}`}
          accessibilityValue={{ text: `${dayName}: ${money(symbol, shown.spend)}, ${money(symbol, shown.total)} so far` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => step(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
          {pitch > 0 && (
            <>
              <Text
                style={[styles.previous, { right: SIDE + 2, top: floor - (lastGhost + PREVIOUS_RISE) * pitch - LABEL_GAP - LABEL_LINE }]}
                numberOfLines={1}
              >
                {previousMonth}: {money(symbol, previousTotals[previousTotals.length - 1] ?? 0)}
              </Text>
              <Animated.View pointerEvents="none" style={[styles.label, labelStyle]}>
                <Text style={styles.labelText} numberOfLines={1}>
                  {isToday ? month : dayName}: {money(symbol, shown.total)}
                </Text>
              </Animated.View>
            </>
          )}
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        <Text style={styles.verdict} numberOfLines={1}>
          {shown.total > previousAtDay ? moreLabel : lessLabel}
        </Text>
        <Pressable style={styles.chevron} hitSlop={12} accessibilityRole="button" accessibilityLabel="Details" onPress={onOpenDetails}>
          <Canvas style={styles.chevronCanvas}>
            <Path path={CHEVRON_PATH} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color={CHEVRON} />
          </Canvas>
        </Pressable>
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
    marginTop: HEADER_TOP,
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  amount: {
    color: INK,
    fontSize: AMOUNT,
    lineHeight: 84,
    fontWeight: '500',
    letterSpacing: -1,
    fontVariant: ['tabular-nums'],
  },
  subtitle: {
    marginTop: -6,
    color: MUTE,
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '600',
  },
  chart: {
    flex: 1,
    marginTop: 8,
  },
  previous: {
    position: 'absolute',
    color: FAINT,
    fontSize: 15,
    lineHeight: LABEL_LINE,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  label: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: LABEL_WIDTH,
    alignItems: 'center',
  },
  labelText: {
    color: MUTE,
    fontSize: 15,
    lineHeight: LABEL_LINE,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  footer: {
    alignItems: 'center',
    paddingTop: 14,
  },
  verdict: {
    color: MUTE,
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '500',
  },
  chevron: {
    marginTop: 22,
    marginBottom: 6,
  },
  chevronCanvas: {
    width: 56,
    height: 16,
  },
});
