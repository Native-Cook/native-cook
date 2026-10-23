import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DotLineChart, type Plot } from './DotLineChart';
import {
  METRICS,
  RANGES,
  SESSIONS,
  type Metric,
  type Range,
  type Session,
  axisValue,
  layoutPoints,
  longDate,
  rangeStart,
  shortDate,
} from './liftlog-data';
import { Roller } from './Roller';

const BACKGROUND = '#0E0E0F';
const INK = '#FFFFFF';
const MUTE = '#8A8A8E';
const BUTTON = '#1E1E21';
const PILL = '#26262A';
const PILL_OPEN = '#D6D6DA';
const PILL_OPEN_INK = '#141416';
const CARD = '#17171A';
const TOOLTIP = '#2A2A2E';
const TOOLTIP_EDGE = 'rgba(255,255,255,0.07)';
const CAPTION = '#B5B5BA';
const MENU = '#242428';
const MENU_PRESSED = '#34343A';
const SEPARATOR = '#36363C';
const TAB_IDLE = '#6B6B70';

// Layout, in points.
const MARGIN = 20;
const CARD_INSET = 16;
const HEADER_BUTTON = 40;
const PILL_HEIGHT = 40;
const PILL_GAP = 10;
const PILLS_GAP = 22;
const CARD_GAP = 24;
const CARD_RADIUS = 24;
// The plot inside the card: room above for the tooltip, below for dates, right for the value axis.
const PLOT_TOP = 62;
const PLOT_BOTTOM = 40;
const PLOT_LEFT = 16;
const PLOT_RIGHT = 60;
const GRID = [0, 0.25, 0.5, 0.75, 1];
const TOOLTIP_WIDTH = 184;
const TOOLTIP_HEIGHT = 88;
// The tooltip hangs over the top of the card and the pills, as in the design.
const TOOLTIP_RISE = 46;
const MENU_WIDTH = 220;
const MENU_ITEM = 50;
const MENU_GAP = 8;
const TAB_BAR = 56;

// Motion.
const MORPH = 500;
const RESET_MORPH = 800;
const SCRUB_FADE = 160;
const MENU_IN = 200;
const LABEL_FADE = 220;
const JUMP_DIP = 0.35;
const JUMP_FADE = 220;
const FOLLOW = { damping: 24, stiffness: 340, mass: 0.7 };

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 10;
const FIRST_DOWN = 0.8;
const FIRST_MOVE = [1.0, 3.2];
const FIRST_PATH = [0.06, 0.94];
const FIRST_UP = 3.5;
const RANGE_OPEN = 3.9;
const RANGE_HOVER = 4.45;
const RANGE_PICK = 4.65;
const METRIC_OPEN = 5.4;
const METRIC_HOVER = 5.95;
const METRIC_PICK = 6.15;
const SECOND_DOWN = 7.0;
const SECOND_MOVE = [7.15, 8.4];
const SECOND_PATH = [0.92, 0.12];
const SECOND_UP = 8.65;
const RESET = 9.05;
// What the loop picks: "3 months", then "Heaviest weight".
const AUTO_RANGE = 1;
const AUTO_METRIC = 1;
// Seconds after the last touch before the screen returns to rest and the loop starts again.
const USER_HOLD = 3;

const STEPS = [
  { at: RANGE_OPEN, run: 'openRange' },
  { at: RANGE_HOVER, run: 'hoverRange' },
  { at: RANGE_PICK, run: 'pickRange' },
  { at: METRIC_OPEN, run: 'openMetric' },
  { at: METRIC_HOVER, run: 'hoverMetric' },
  { at: METRIC_PICK, run: 'pickMetric' },
  { at: RESET, run: 'reset' },
] as const;
type Step = (typeof STEPS)[number]['run'];
const STEP_TIMES = STEPS.map((step) => step.at);

type Menu = 'metric' | 'range';

type Props = {
  title?: string;
  subtitle?: string;
  sessions?: Session[];
  metrics?: Metric[];
  ranges?: Range[];
  /** The metric and range the screen opens on, by their place in `metrics` and `ranges`. */
  initialMetric?: number;
  initialRange?: number;
  /** The label under the last session. */
  todayLabel?: string;
  /** Scrubs, switches range and metric, scrubs again and comes back, every LOOP seconds. */
  autoPlay?: boolean;
  onMetricChange?: (metric: Metric, index: number) => void;
  onRangeChange?: (range: Range, index: number) => void;
  /** Called with the session under the finger while the chart is scrubbed. */
  onScrub?: (session: Session, value: number) => void;
  onHeaderPress?: () => void;
};

/** Smooth start and stop over 0…1. */
function glide(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
}

function format(value: number, decimals: number) {
  return value.toFixed(decimals);
}

function tick(kind: 'step' | 'press') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'step') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A workout's progress chart: a line of dots, one per session, with a metric and a range picked
 * from two dropdowns. Dragging over the chart lifts the nearest dot, dims the rest and shows its
 * value in a tooltip whose digits roll; a new metric or range morphs every dot to its new place.
 */
export function MetricDropdownLineScrub({
  title = 'Deadlift',
  subtitle = "You're viewing demo data. This section will fill up with your data as you log workouts.",
  sessions = SESSIONS,
  metrics = METRICS,
  ranges = RANGES,
  initialMetric = 0,
  initialRange = 3,
  todayLabel = 'Today',
  autoPlay = false,
  onMetricChange,
  onRangeChange,
  onScrub,
  onHeaderPress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [metricIndex, setMetricIndex] = useState(initialMetric);
  const [rangeIndex, setRangeIndex] = useState(initialRange);
  const [menu, setMenu] = useState<Menu | null>(null);
  // The menu row the autoPlay loop's finger is on, drawn pressed.
  const [hover, setHover] = useState<number | null>(null);
  // The scrubbed session, counted from the first one the range shows.
  const [picked, setPicked] = useState(0);
  const [touches, setTouches] = useState(0);
  const [screenWidth, setScreenWidth] = useState(0);
  const [pillsY, setPillsY] = useState(0);
  const [card, setCard] = useState({ x: CARD_INSET, y: 0, width: 0, height: 0 });

  const slots = sessions.length;
  const metric = metrics[metricIndex];
  const range = ranges[rangeIndex];
  const start = rangeStart(sessions, range);
  const count = sessions.length - start;
  const plot: Plot = {
    left: PLOT_LEFT,
    top: PLOT_TOP,
    width: Math.max(0, card.width - PLOT_LEFT - PLOT_RIGHT),
    height: Math.max(0, card.height - PLOT_TOP - PLOT_BOTTOM),
  };

  // The points before and after the last change, in 0…1, and how far the morph between them is.
  const [opening] = useState(() => {
    const first = rangeStart(sessions, ranges[initialRange]);
    const to = layoutPoints(metrics[initialMetric], first, slots);
    return { from: to, to, count: sessions.length - first };
  });
  const shape = useSharedValue(opening);
  const morph = useSharedValue(1);
  const jump = useSharedValue(1);
  // The finger on the chart, as a fraction of the plot's width, and whether it is down.
  const scrubAt = useSharedValue(0);
  const scrubbing = useSharedValue(0);
  const holding = useSharedValue(false);
  const byHand = useSharedValue(false);
  // Seconds into the autoPlay loop; -1 when it is not running. `seen` is the time the loop last
  // acted on, kept here rather than taken from the reaction, which starts over when it is set up again.
  const clock = useSharedValue(-1);
  const seen = useSharedValue(-1);

  const points = useDerivedValue(() => {
    const { from, to } = shape.get();
    const p = morph.get();
    const out: number[] = [];
    for (let i = 0; i + 1 < to.length; i += 2) {
      const x = from[i] + (to[i] - from[i]) * p;
      const y = from[i + 1] + (to[i + 1] - from[i + 1]) * p;
      out.push(plot.left + x * plot.width, plot.top + (1 - y) * plot.height);
    }
    return out;
  });

  // The session nearest the finger, and the slot that draws it.
  const pickedIndex = useDerivedValue(() => {
    const n = shape.get().count;
    return n <= 1 ? 0 : Math.round(Math.min(1, Math.max(0, scrubAt.get())) * (n - 1));
  });
  const pickedSlot = useDerivedValue(() => {
    const n = shape.get().count;
    return n <= 1 ? 0 : Math.round((pickedIndex.get() * (slots - 1)) / (n - 1));
  });
  const pickedX = useDerivedValue(() => points.get()[pickedSlot.get() * 2] ?? 0);
  const pickedY = useDerivedValue(() => points.get()[pickedSlot.get() * 2 + 1] ?? 0);
  const scrub = useDerivedValue(() =>
    withTiming(scrubbing.get(), { duration: reducedMotion ? 0 : SCRUB_FADE, reduceMotion: ReduceMotion.Never }),
  );

  // The tooltip glides after the scrubbed dot, but appears right over it.
  const tooltipX = useSharedValue(0);
  useAnimatedReaction(
    () => ({ x: pickedX.get(), on: scrubbing.get() }),
    (now, before) => {
      if (reducedMotion || now.on === 0 || before === null || before.on === 0) tooltipX.set(now.x);
      else tooltipX.set(withSpring(now.x, { ...FOLLOW, reduceMotion: ReduceMotion.Never }));
    },
  );

  const scrubbed = (index: number, hand: boolean) => {
    setPicked(index);
    if (hand) tick('step');
    const session = start + Math.min(index, count - 1);
    onScrub?.(sessions[session], metric.values[session]);
  };

  useAnimatedReaction(
    () => pickedIndex.get(),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(scrubbed, now, byHand.get() && scrubbing.get() === 1);
    },
  );

  /** Moves every point from where it is now to `to`, over `ms` (a jump with a fade under reduced motion). */
  const retarget = (to: number[], n: number, ms: number, still: boolean) => {
    'worklet';
    const { from, to: was } = shape.get();
    const p = morph.get();
    const now: number[] = [];
    for (let i = 0; i < was.length; i++) now.push(from[i] + (was[i] - from[i]) * p);
    shape.set({ from: now, to, count: n });
    if (still) {
      morph.set(1);
      jump.set(JUMP_DIP);
      jump.set(withTiming(1, { duration: JUMP_FADE, reduceMotion: ReduceMotion.Never }));
    } else {
      morph.set(0);
      morph.set(withTiming(1, { duration: ms, easing: Easing.inOut(Easing.cubic), reduceMotion: ReduceMotion.Never }));
    }
  };

  const show = (nextMetric: number, nextRange: number, ms: number) => {
    const from = rangeStart(sessions, ranges[nextRange]);
    setMetricIndex(nextMetric);
    setRangeIndex(nextRange);
    setPicked((index) => Math.min(index, sessions.length - from - 1));
    scheduleOnUI(retarget, layoutPoints(metrics[nextMetric], from, slots), sessions.length - from, ms, reducedMotion);
  };

  const choose = (kind: Menu, index: number, auto: boolean) => {
    setMenu(null);
    setHover(null);
    if (!auto) tick('press');
    if (kind === 'metric' ? index === metricIndex : index === rangeIndex) return;
    if (kind === 'metric') {
      show(index, rangeIndex, MORPH);
      onMetricChange?.(metrics[index], index);
    } else {
      show(metricIndex, index, MORPH);
      onRangeChange?.(ranges[index], index);
    }
  };

  /** Back to the metric and range the screen opened on, with nothing open or scrubbed. */
  const rest = () => {
    setMenu(null);
    setHover(null);
    scrubbing.set(0);
    if (metricIndex !== initialMetric || rangeIndex !== initialRange) show(initialMetric, initialRange, RESET_MORPH);
  };

  const run = (step: Step) => {
    if (step === 'openRange') setMenu('range');
    else if (step === 'hoverRange') setHover(AUTO_RANGE);
    else if (step === 'pickRange') choose('range', AUTO_RANGE, true);
    else if (step === 'openMetric') setMenu('metric');
    else if (step === 'hoverMetric') setHover(AUTO_METRIC);
    else if (step === 'pickMetric') choose('metric', AUTO_METRIC, true);
    else rest();
  };

  // Timers and worklets call into the latest render.
  const latest = useRef({ run, rest });
  useEffect(() => {
    latest.current = { run, rest };
  });
  const runStep = (step: Step) => latest.current.run(step);

  // The autoPlay loop: the clock moves a finger that is not there and presses what the touch would.
  useAnimatedReaction(
    () => clock.get(),
    (t) => {
      if (t < 0) return;
      const was = seen.get() > t ? -1 : seen.get();
      seen.set(t);
      if (was < FIRST_DOWN && t >= FIRST_DOWN) {
        scrubAt.set(FIRST_PATH[0]);
        scrubbing.set(1);
      }
      if (t >= FIRST_MOVE[0] && t <= FIRST_UP) {
        const k = glide((t - FIRST_MOVE[0]) / (FIRST_MOVE[1] - FIRST_MOVE[0]));
        scrubAt.set(FIRST_PATH[0] + (FIRST_PATH[1] - FIRST_PATH[0]) * k);
      }
      if (was < FIRST_UP && t >= FIRST_UP) scrubbing.set(0);
      if (was < SECOND_DOWN && t >= SECOND_DOWN) {
        scrubAt.set(SECOND_PATH[0]);
        scrubbing.set(1);
      }
      if (t >= SECOND_MOVE[0] && t <= SECOND_UP) {
        const k = glide((t - SECOND_MOVE[0]) / (SECOND_MOVE[1] - SECOND_MOVE[0]));
        scrubAt.set(SECOND_PATH[0] + (SECOND_PATH[1] - SECOND_PATH[0]) * k);
      }
      if (was < SECOND_UP && t >= SECOND_UP) scrubbing.set(0);
      for (let i = 0; i < STEP_TIMES.length; i++) {
        if (was < STEP_TIMES[i] && t >= STEP_TIMES[i]) scheduleOnRN(runStep, STEPS[i].run);
      }
    },
  );

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, then: () => void) => timers.push(setTimeout(then, ms));
    const begin = () => {
      byHand.set(false);
      seen.set(-1);
      clock.set(0);
      clock.set(
        withRepeat(
          withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }),
          -1,
          false,
          undefined,
          ReduceMotion.Never,
        ),
      );
    };
    const resume = () => {
      // A finger still on the chart puts the loop off again when it lifts.
      if (holding.get()) return;
      latest.current.rest();
      after(RESET_MORPH + 200, begin);
    };
    if (touches === 0) begin();
    else after(USER_HOLD * 1000, resume);
    return () => {
      timers.forEach(clearTimeout);
      cancelAnimation(clock);
      clock.set(-1);
    };
  }, [autoPlay, touches, byHand, clock, seen, holding]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setHover(null);
    if (!holding.get()) scrubbing.set(0);
    setTouches((n) => n + 1);
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      // The loop stops here on the UI thread, before the finger's first move.
      cancelAnimation(clock);
      clock.set(-1);
      byHand.set(true);
      holding.set(true);
      scrubAt.set(plot.width > 0 ? (event.x - plot.left) / plot.width : 0);
      scrubbing.set(1);
      scheduleOnRN(touched);
    })
    .onChange((event) => {
      scrubAt.set(plot.width > 0 ? (event.x - plot.left) / plot.width : 0);
    })
    .onFinalize(() => {
      holding.set(false);
      scrubbing.set(0);
      scheduleOnRN(touched);
    });

  const toggle = (kind: Menu) => {
    touched();
    tick('press');
    setMenu((open) => (open === kind ? null : kind));
  };

  const tooltipLeft = card.x - TOOLTIP_WIDTH / 2;
  const tooltipMin = CARD_INSET;
  const tooltipMax = screenWidth - CARD_INSET - TOOLTIP_WIDTH;
  const tooltipStyle = useAnimatedStyle(() => ({
    opacity: scrub.get(),
    transform: [
      { translateX: Math.min(tooltipMax, Math.max(tooltipMin, tooltipLeft + tooltipX.get())) },
      { scale: 0.96 + 0.04 * scrub.get() },
    ],
  }));
  const chartOpacity = useDerivedValue(() => jump.get());

  const session = start + Math.min(picked, count - 1);
  const pillWidth = (screenWidth - MARGIN * 2 - PILL_GAP) / 2;
  const menuTop = pillsY + PILL_HEIGHT + MENU_GAP;
  const menuLeft = (kind: Menu) =>
    kind === 'metric' ? MARGIN : Math.min(MARGIN + pillWidth + PILL_GAP, screenWidth - MARGIN - MENU_WIDTH);
  const middle = start + Math.round((count - 1) / 2);

  const onCardLayout = (event: LayoutChangeEvent) => {
    const { x, y, width, height } = event.nativeEvent.layout;
    setCard({ x, y, width, height });
  };

  return (
    <View style={styles.screen} onLayout={(event) => setScreenWidth(event.nativeEvent.layout.width)}>
      <StatusBar style="light" />
      <View style={[styles.header, { paddingTop: insets.top + 16 }]}>
        <View style={styles.headerText}>
          <Text style={styles.title} numberOfLines={1}>
            {title}
          </Text>
          <Text style={styles.subtitle} numberOfLines={2} lineBreakStrategyIOS="standard">
            {subtitle}
          </Text>
        </View>
        <Pressable
          style={styles.headerButton}
          accessibilityRole="button"
          accessibilityLabel="More"
          onPress={() => {
            touched();
            onHeaderPress?.();
          }}
        >
          <Ionicons name="chevron-down" size={20} color={INK} />
        </Pressable>
      </View>

      <View style={styles.pills} onLayout={(event) => setPillsY(event.nativeEvent.layout.y)}>
        {(['metric', 'range'] as const).map((kind) => {
          const open = menu === kind;
          return (
            <Pressable
              key={kind}
              style={styles.pillSlot}
              accessibilityRole="button"
              accessibilityState={{ expanded: open }}
              onPress={() => toggle(kind)}
            >
              <Animated.View
                style={[
                  styles.pill,
                  { backgroundColor: open ? PILL_OPEN : PILL, transitionProperty: 'backgroundColor', transitionDuration: MENU_IN },
                ]}
              >
                <Animated.Text
                  key={kind === 'metric' ? metric.label : range.label}
                  entering={FadeIn.duration(LABEL_FADE)}
                  style={[styles.pillLabel, { color: open ? PILL_OPEN_INK : INK }]}
                  numberOfLines={1}
                >
                  {kind === 'metric' ? metric.label : range.label}
                </Animated.Text>
                <Animated.View
                  style={{
                    transform: [{ rotate: open ? '180deg' : '0deg' }],
                    transitionProperty: 'transform',
                    transitionDuration: reducedMotion ? 0 : MENU_IN,
                  }}
                >
                  <Ionicons name="chevron-down" size={16} color={open ? PILL_OPEN_INK : INK} />
                </Animated.View>
              </Animated.View>
            </Pressable>
          );
        })}
      </View>

      <GestureDetector gesture={pan}>
        <View style={styles.card} onLayout={onCardLayout} accessibilityLabel={`${metric.label}, ${range.label}`}>
          {card.width > 0 && (
            <DotLineChart
              width={card.width}
              height={card.height}
              plot={plot}
              grid={GRID}
              points={points}
              pickedX={pickedX}
              pickedY={pickedY}
              scrub={scrub}
              hairlineTop={TOOLTIP_HEIGHT - TOOLTIP_RISE}
              opacity={chartOpacity}
            />
          )}
          {GRID.map((at, i) => (
            <Animated.Text
              key={`${metricIndex}-${i}`}
              entering={FadeIn.duration(LABEL_FADE)}
              exiting={FadeOut.duration(LABEL_FADE)}
              style={[styles.axisLabel, { top: plot.top + plot.height * (1 - at) - 7, right: 14 }]}
            >
              {axisValue(metric.min + (metric.max - metric.min) * at)} {metric.unit}
            </Animated.Text>
          ))}
          {[start, middle, sessions.length - 1].map((index, i) => (
            <Animated.Text
              key={`${rangeIndex}-${i}`}
              entering={FadeIn.duration(LABEL_FADE)}
              exiting={FadeOut.duration(LABEL_FADE)}
              style={[
                styles.dateLabel,
                i === 0 && { left: plot.left },
                i === 1 && { left: plot.left + plot.width / 2 - 30, width: 60, textAlign: 'center' },
                i === 2 && { right: PLOT_RIGHT - 8 },
              ]}
            >
              {i === 2 ? todayLabel : shortDate(sessions[index])}
            </Animated.Text>
          ))}
        </View>
      </GestureDetector>

      <View style={[styles.tabBar, { paddingBottom: insets.bottom }]}>
        {(['grid-outline', 'calendar-clear-outline', 'stats-chart', 'chatbubble-outline'] as const).map((icon) => (
          <View key={icon} style={styles.tab}>
            <Ionicons name={icon} size={22} color={icon === 'stats-chart' ? INK : TAB_IDLE} />
            {icon === 'chatbubble-outline' && <View style={styles.badge} />}
          </View>
        ))}
      </View>

      <Animated.View
        pointerEvents="none"
        style={[styles.tooltip, { top: card.y - TOOLTIP_RISE }, tooltipStyle]}
        accessibilityLiveRegion="polite"
      >
        <View style={styles.valueRow}>
          <Roller
            text={format(metric.values[session], metric.decimals)}
            style={styles.value}
            still={reducedMotion}
          />
          <Text style={styles.unit}>{metric.unit}</Text>
        </View>
        <Text style={styles.caption} numberOfLines={1}>
          {metric.caption}
        </Text>
        <Text style={styles.when} numberOfLines={1}>
          {longDate(sessions[session])}
        </Text>
      </Animated.View>

      {menu !== null && (
        <Pressable style={StyleSheet.absoluteFill} accessibilityLabel="Close menu" onPress={() => toggle(menu)} />
      )}
      {(['metric', 'range'] as const).map((kind) => {
        const open = menu === kind;
        const items = kind === 'metric' ? metrics.map((m) => m.label) : ranges.map((r) => r.label);
        const current = kind === 'metric' ? metricIndex : rangeIndex;
        return (
          <Animated.View
            key={kind}
            pointerEvents={open ? 'auto' : 'none'}
            accessibilityElementsHidden={!open}
            importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}
            style={[
              styles.menu,
              {
                top: menuTop,
                left: menuLeft(kind),
                opacity: open ? 1 : 0,
                transform: [{ translateY: open || reducedMotion ? 0 : -6 }, { scale: open || reducedMotion ? 1 : 0.94 }],
                transitionProperty: ['opacity', 'transform'],
                transitionDuration: MENU_IN,
                transitionTimingFunction: 'ease-out',
              },
            ]}
          >
            {items.map((label, i) => (
              <Pressable
                key={label}
                accessibilityRole="menuitem"
                accessibilityState={{ selected: i === current }}
                onPress={() => {
                  touched();
                  choose(kind, i, false);
                }}
              >
                {({ pressed }) => (
                  <View style={[styles.item, (pressed || (open && hover === i)) && styles.itemPressed]}>
                    <Ionicons
                      name={kind === 'metric' ? 'bar-chart-outline' : 'calendar-clear-outline'}
                      size={17}
                      color={i === current ? INK : CAPTION}
                    />
                    <Text style={[styles.itemLabel, i === current && styles.itemCurrent]} numberOfLines={1}>
                      {label}
                    </Text>
                    {i === current && <Ionicons name="checkmark" size={16} color={INK} />}
                    {i < items.length - 1 && <View style={styles.separator} />}
                  </View>
                )}
              </Pressable>
            ))}
          </Animated.View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingHorizontal: MARGIN,
  },
  headerText: {
    flex: 1,
    paddingRight: 16,
  },
  title: {
    color: INK,
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  subtitle: {
    marginTop: 6,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
  },
  headerButton: {
    marginTop: 18,
    width: HEADER_BUTTON,
    height: HEADER_BUTTON,
    borderRadius: HEADER_BUTTON / 2,
    backgroundColor: BUTTON,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pills: {
    flexDirection: 'row',
    gap: PILL_GAP,
    marginTop: PILLS_GAP,
    paddingHorizontal: MARGIN,
  },
  pillSlot: {
    flex: 1,
  },
  pill: {
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    paddingLeft: 18,
    paddingRight: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  pillLabel: {
    flex: 1,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  card: {
    flex: 1,
    marginTop: CARD_GAP,
    marginHorizontal: CARD_INSET,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    overflow: 'hidden',
  },
  axisLabel: {
    position: 'absolute',
    color: MUTE,
    fontSize: 11,
    lineHeight: 14,
    fontVariant: ['tabular-nums'],
  },
  dateLabel: {
    position: 'absolute',
    bottom: 14,
    color: MUTE,
    fontSize: 11,
    lineHeight: 14,
  },
  tabBar: {
    flexDirection: 'row',
    marginTop: 8,
  },
  tab: {
    flex: 1,
    height: TAB_BAR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    position: 'absolute',
    top: 15,
    left: '50%',
    marginLeft: 7,
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: INK,
  },
  tooltip: {
    position: 'absolute',
    left: 0,
    width: TOOLTIP_WIDTH,
    height: TOOLTIP_HEIGHT,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderRadius: 16,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: TOOLTIP_EDGE,
    backgroundColor: TOOLTIP,
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(0,0,0,0.45)' }],
  },
  valueRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  value: {
    color: INK,
    fontSize: 26,
    lineHeight: 30,
    fontWeight: '600',
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
  },
  unit: {
    marginLeft: 4,
    marginBottom: 4,
    color: CAPTION,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
  },
  caption: {
    marginTop: 2,
    color: CAPTION,
    fontSize: 12,
    lineHeight: 16,
  },
  when: {
    marginTop: 2,
    color: MUTE,
    fontSize: 10,
    lineHeight: 14,
  },
  menu: {
    position: 'absolute',
    width: MENU_WIDTH,
    paddingVertical: 6,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: MENU,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: TOOLTIP_EDGE,
    transformOrigin: 'top',
    boxShadow: [{ offsetX: 0, offsetY: 12, blurRadius: 32, color: 'rgba(0,0,0,0.5)' }],
  },
  item: {
    height: MENU_ITEM,
    marginHorizontal: 6,
    paddingHorizontal: 12,
    borderRadius: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  itemPressed: {
    backgroundColor: MENU_PRESSED,
  },
  itemLabel: {
    flex: 1,
    color: CAPTION,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  itemCurrent: {
    color: INK,
  },
  separator: {
    position: 'absolute',
    left: 41,
    right: 12,
    bottom: 0,
    height: StyleSheet.hairlineWidth,
    backgroundColor: SEPARATOR,
  },
});
