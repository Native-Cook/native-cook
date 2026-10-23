import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
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
import { scheduleOnRN } from 'react-native-worklets';

import { Roller } from './Roller';
import { NIGHTS, STAGE_COLORS, type Night, clock, mean, spread } from './sleep-data';
import { type ChartFrame, SleepColumns, yOf } from './SleepColumns';

const SKY_TOP = '#2A0F3A';
const SKY_BOTTOM = '#120818';
const PANEL = '#1D1027';
const INK = '#FFFFFF';
const MUTE = '#9C8BA8';
const AXIS = '#8A8A8E';
const WEEKEND = '#5E5068';
const BUTTON = 'rgba(255,255,255,0.08)';
const TRACK = '#2D1B3C';
const THUMB = '#4A3560';
const GOOD = '#3DDC97';
const BADGE_INK = '#111111';
const TAB_BAR = '#1A1220';
const TAB_ACTIVE = '#FF2D55';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

// Layout, in points.
const MARGIN = 20;
const NAV = 56;
const ROUND_BUTTON = 40;
const SEGMENT = 36;
const COLUMN = 14;
// The chart: room above the columns for the floating label, right for the time axis, below for days.
const LABEL_SPACE = 44;
const AXIS_WIDTH = 50;
const DAYS = 46;
const DAY_BADGE = { width: 28, height: 38 };
const TIME_LABEL = { width: 104, height: 22 };
const TIME_LABEL_GAP = 7;
const TAB_HEIGHT = 60;
// The time axis: 23:30 to 09:00, labelled every two hours.
const AXIS_FROM = -30;
const AXIS_TO = 540;
const GRID = [0, 120, 240, 360, 480];
const IDEAL_ASLEEP: [number, number] = [-15, 45];
const USUAL_WAKE: [number, number] = [420, 510];

// Motion.
const FOLLOW = { damping: 22, stiffness: 300, mass: 0.6 };
const PRESS = { damping: 18, stiffness: 260, mass: 0.6 };

// The autoPlay timeline, in seconds from the start of each loop; the finger's place is in columns.
export const LOOP = 9;
const SWEEPS = [
  { down: 0.8, up: 4.4, moves: [{ from: 13, to: 0, start: 0.95, end: 4.0 }] },
  {
    down: 4.9,
    up: 8.1,
    moves: [
      { from: 11, to: 3, start: 5.0, end: 6.6 },
      { from: 3, to: 13, start: 6.8, end: 7.9 },
    ],
  },
];
// Seconds after the last touch before the chart returns to tonight and the loop starts again.
const USER_HOLD = 3;

const MODES = ['Duration', 'Consistency', 'Stages'];

type Props = {
  title?: string;
  nights?: Night[];
  /** The night shown when the screen opens, by its place in `nights`; the last one by default. */
  initialNight?: number;
  modes?: string[];
  initialMode?: number;
  asleepStatus?: string;
  variabilityStatus?: string;
  /** Moves a finger over the nights by itself every LOOP seconds and comes back to the last one. */
  autoPlay?: boolean;
  onSelect?: (night: Night, index: number) => void;
  onModeChange?: (mode: string, index: number) => void;
  onBack?: () => void;
};

/** Smooth start and stop over 0…1. */
function glide(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function Check() {
  return (
    <View style={styles.check}>
      <Ionicons name="checkmark" size={11} color={BADGE_INK} />
    </View>
  );
}

/** Two lines under a column: weekday and day of the month. */
function DayLabel({ night, color, width }: { night: Night; color: string; width: number }) {
  return (
    <View style={[styles.day, { width }]}>
      <Text style={[styles.dayText, { color }]}>{night.weekday}</Text>
      <Text style={[styles.dayText, { color }]}>{night.day}</Text>
    </View>
  );
}

/**
 * A sleep consistency chart: one column per night from falling asleep to waking, striped with its
 * sleep stages. Dragging across the chart widens and rings the night under the finger, dims the
 * others, slides the white day badge to it and moves a label above it whose times roll.
 */
export function SleepColumnScrubber({
  title = 'Sleep',
  nights = NIGHTS,
  initialNight,
  modes = MODES,
  initialMode = 1,
  asleepStatus = 'STABLE',
  variabilityStatus = 'NORMAL',
  autoPlay = false,
  onSelect,
  onModeChange,
  onBack,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const home = initialNight ?? nights.length - 1;
  const [shown, setShown] = useState(home);
  const [mode, setMode] = useState(initialMode);
  const [touches, setTouches] = useState(0);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [modeWidth, setModeWidth] = useState(0);

  const averageAsleep = mean(nights.map((night) => night.asleep));
  const averageAwake = mean(nights.map((night) => night.awake));
  const variability = Math.round(spread(nights.map((night) => night.asleep)));

  const plotWidth = Math.max(0, size.width - AXIS_WIDTH);
  const step = nights.length ? plotWidth / nights.length : 0;
  const frame: ChartFrame = {
    width: size.width,
    top: LABEL_SPACE,
    bottom: Math.max(LABEL_SPACE, size.height - DAYS - 8),
    from: AXIS_FROM,
    to: AXIS_TO,
    first: step / 2,
    step,
    column: Math.min(COLUMN, step * 0.65),
    plotWidth,
  };
  const bands = { ideal: IDEAL_ASLEEP, wake: USUAL_WAKE, grid: GRID, averageAsleep, averageAwake };
  const tops = nights.map((night) => yOf(frame, night.asleep));

  // The finger's place in columns, and whether it is down.
  const scrubAt = useSharedValue(home);
  const touching = useSharedValue(0);
  const holding = useSharedValue(false);
  const byHand = useSharedValue(false);
  // Seconds into the autoPlay loop, -1 when it is not running; `seen` is the time it last acted on.
  const loop = useSharedValue(-1);
  const seen = useSharedValue(-1);

  const last = nights.length - 1;
  const selected = useDerivedValue(() => Math.min(last, Math.max(0, Math.round(scrubAt.get()))));
  const press = useDerivedValue(() =>
    reducedMotion ? touching.get() : withSpring(touching.get(), { ...PRESS, reduceMotion: ReduceMotion.Never }),
  );
  const columnX = useDerivedValue(() => {
    const x = frame.first + frame.step * selected.get();
    return reducedMotion ? x : withSpring(x, { ...FOLLOW, reduceMotion: ReduceMotion.Never });
  });
  const columnTop = useDerivedValue(() => {
    const y = tops[selected.get()] ?? 0;
    return reducedMotion ? y : withSpring(y, { ...FOLLOW, reduceMotion: ReduceMotion.Never });
  });

  const picked = (index: number, hand: boolean) => {
    setShown(index);
    if (hand) tick();
    onSelect?.(nights[index], index);
  };

  useAnimatedReaction(
    () => selected.get(),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(picked, now, byHand.get() && touching.get() === 1);
    },
  );

  // The autoPlay loop moves a finger that is not there, through the same values a touch sets.
  useAnimatedReaction(
    () => loop.get(),
    (t) => {
      if (t < 0) return;
      const was = seen.get() > t ? -1 : seen.get();
      seen.set(t);
      for (let i = 0; i < SWEEPS.length; i++) {
        const sweep = SWEEPS[i];
        if (was < sweep.down && t >= sweep.down) {
          scrubAt.set(sweep.moves[0].from);
          touching.set(1);
        }
        for (let j = 0; j < sweep.moves.length; j++) {
          const move = sweep.moves[j];
          if (t >= move.start && t <= sweep.up && (j + 1 === sweep.moves.length || t < sweep.moves[j + 1].start)) {
            scrubAt.set(move.from + (move.to - move.from) * glide((t - move.start) / (move.end - move.start)));
          }
        }
        if (was < sweep.up && t >= sweep.up) touching.set(0);
      }
    },
  );

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const begin = () => {
      byHand.set(false);
      seen.set(-1);
      loop.set(0);
      loop.set(
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
      touching.set(0);
      scrubAt.set(home);
      timers.push(setTimeout(begin, 400));
    };
    if (touches === 0) begin();
    else timers.push(setTimeout(resume, USER_HOLD * 1000));
    return () => {
      timers.forEach(clearTimeout);
      cancelAnimation(loop);
      loop.set(-1);
    };
  }, [autoPlay, touches, home, byHand, holding, loop, scrubAt, seen, touching]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    if (!holding.get()) touching.set(0);
    setTouches((n) => n + 1);
  };

  const columnAt = (x: number) => {
    'worklet';
    return frame.step > 0 ? (x - frame.first) / frame.step : 0;
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      // The loop stops here on the UI thread, before the finger's first move.
      cancelAnimation(loop);
      loop.set(-1);
      byHand.set(true);
      holding.set(true);
      touching.set(1);
      scrubAt.set(columnAt(event.x));
      scheduleOnRN(touched);
    })
    .onChange((event) => {
      scrubAt.set(columnAt(event.x));
    })
    .onFinalize(() => {
      holding.set(false);
      touching.set(0);
      scheduleOnRN(touched);
    });

  const badgeStyle = useAnimatedStyle(() => ({ transform: [{ translateX: columnX.get() - DAY_BADGE.width / 2 }] }));
  const badgeRowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: DAY_BADGE.width / 2 - columnX.get() }] }));
  // The label stays on screen; its stem stays on the column.
  const labelMin = 8 - MARGIN;
  const labelMax = size.width + MARGIN - 8 - TIME_LABEL.width;
  const labelStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: Math.min(labelMax, Math.max(labelMin, columnX.get() - TIME_LABEL.width / 2)) },
      { translateY: columnTop.get() - TIME_LABEL_GAP - TIME_LABEL.height },
    ],
  }));
  const stemStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: columnX.get() - 0.5 }, { translateY: columnTop.get() - TIME_LABEL_GAP }],
  }));

  const thumbX = useDerivedValue(() =>
    reducedMotion ? mode * (modeWidth / modes.length) : withSpring(mode * (modeWidth / modes.length), { ...PRESS, reduceMotion: ReduceMotion.Never }),
  );
  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: thumbX.get() }] }));

  const night = nights[shown];
  const axisLabels = [
    ...GRID.filter((minutes) => Math.abs(minutes - averageAsleep) > 22 && Math.abs(minutes - averageAwake) > 22).map((minutes) => ({
      minutes,
      strong: false,
    })),
    { minutes: averageAsleep, strong: true },
    { minutes: averageAwake, strong: true },
  ];

  const onChartLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <LinearGradient colors={[SKY_TOP, SKY_BOTTOM]} style={StyleSheet.absoluteFill} />

      <View style={[styles.nav, { marginTop: insets.top }]}>
        <Pressable style={styles.round} accessibilityRole="button" accessibilityLabel="Back" onPress={onBack}>
          <Ionicons name="chevron-back" size={20} color={INK} />
        </Pressable>
        <Text style={styles.navTitle}>{title}</Text>
        <View style={styles.navRight}>
          <View style={styles.round}>
            <Ionicons name="calendar-outline" size={18} color={INK} />
          </View>
          <View style={styles.round}>
            <Ionicons name="options-outline" size={20} color={INK} />
          </View>
        </View>
      </View>

      <View style={styles.panel}>
        <Text style={styles.heading}>Overview</Text>
        <View style={styles.segments} onLayout={(event) => setModeWidth(event.nativeEvent.layout.width - 6)}>
          {modeWidth > 0 && <Animated.View style={[styles.thumb, { width: modeWidth / modes.length }, thumbStyle]} />}
          {modes.map((label, index) => (
            <Pressable
              key={label}
              style={styles.segment}
              accessibilityRole="tab"
              accessibilityState={{ selected: index === mode }}
              onPress={() => {
                setMode(index);
                onModeChange?.(label, index);
              }}
            >
              <Text style={[styles.segmentLabel, index === mode && styles.segmentOn]}>{label}</Text>
            </Pressable>
          ))}
        </View>

        <View style={styles.stats}>
          <View style={styles.stat}>
            <Text style={styles.caption}>Avg. fall asleep</Text>
            <Text style={styles.big}>{clock(averageAsleep)}</Text>
            <View style={styles.status}>
              <Check />
              <Text style={styles.statusText}>{asleepStatus}</Text>
            </View>
          </View>
          <View style={styles.stat}>
            <Text style={styles.caption}>Fall asleep variability</Text>
            <Text style={styles.big}>
              {variability}
              <Text style={styles.small}>m</Text>
            </Text>
            <View style={styles.status}>
              <Check />
              <Text style={styles.statusText}>{variabilityStatus}</Text>
            </View>
          </View>
        </View>

        <GestureDetector gesture={pan}>
          <View
            style={styles.chart}
            onLayout={onChartLayout}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={`${night.weekday} ${night.day}, ${clock(night.asleep)} to ${clock(night.awake)}`}
          >
            {size.width > 0 && (
              <>
                <SleepColumns frame={frame} nights={nights} bands={bands} selected={selected} press={press} still={reducedMotion} />
                {axisLabels.map(({ minutes, strong }) => (
                  <Text
                    key={minutes}
                    style={[styles.axis, strong && styles.axisStrong, { left: plotWidth + 6, top: yOf(frame, minutes) - 7 }]}
                  >
                    {clock(minutes)}
                  </Text>
                ))}
                <View style={[styles.days, { top: frame.bottom + 10 }]}>
                  {nights.map((item, index) => (
                    <DayLabel key={index} night={item} width={step} color={item.weekday === 'Su' ? WEEKEND : AXIS} />
                  ))}
                </View>
                <Animated.View style={[styles.badge, { top: frame.bottom + 10 - 3 }, badgeStyle]}>
                  <Animated.View style={[styles.days, styles.badgeRow, badgeRowStyle]}>
                    {nights.map((item, index) => (
                      <DayLabel key={index} night={item} width={step} color={BADGE_INK} />
                    ))}
                  </Animated.View>
                </Animated.View>
                <Animated.View style={[styles.stem, { height: TIME_LABEL_GAP }, stemStyle]} />
                <Animated.View style={[styles.label, labelStyle]} pointerEvents="none">
                  <Roller text={`${clock(night.asleep)}—${clock(night.awake)}`} style={styles.labelText} still={reducedMotion} />
                </Animated.View>
              </>
            )}
          </View>
        </GestureDetector>

        <View style={styles.legend}>
          <View style={styles.legendItem}>
            <View style={[styles.swatch, styles.swatchIdeal]} />
            <Text style={styles.legendText}>Ideal fall asleep</Text>
          </View>
          <View style={styles.legendItem}>
            <View style={[styles.swatch, styles.swatchWake]} />
            <Text style={styles.legendText}>Usual wake-up</Text>
          </View>
          <View style={styles.legendItem}>
            <View style={styles.legendLine} />
            <Text style={styles.legendText}>Avg. fall asleep</Text>
          </View>
        </View>
        <View style={styles.stages}>
          {(['awake', 'rem', 'core', 'deep'] as const).map((stage) => (
            <View key={stage} style={styles.legendItem}>
              <View style={[styles.dot, { backgroundColor: STAGE_COLORS[stage] }]} />
              <Text style={styles.legendText}>{stage === 'rem' ? 'REM' : stage[0].toUpperCase() + stage.slice(1)}</Text>
            </View>
          ))}
        </View>

        <View style={[styles.tabBar, { marginBottom: Math.max(insets.bottom, 12) }]}>
          <View style={[styles.tab, styles.tabOn]}>
            <Ionicons name="today" size={20} color={TAB_ACTIVE} />
            <Text style={[styles.tabLabel, { color: TAB_ACTIVE }]}>Today</Text>
          </View>
          <View style={styles.tab}>
            <Ionicons name="trending-up" size={20} color={INK} />
            <Text style={styles.tabLabel}>Progress</Text>
          </View>
          <View style={styles.tab}>
            <Ionicons name="barbell-outline" size={20} color={INK} />
            <Text style={styles.tabLabel}>Workouts</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SKY_BOTTOM,
  },
  nav: {
    height: NAV,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  navTitle: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  navRight: {
    flexDirection: 'row',
    gap: 8,
  },
  round: {
    width: ROUND_BUTTON,
    height: ROUND_BUTTON,
    borderRadius: ROUND_BUTTON / 2,
    backgroundColor: BUTTON,
    alignItems: 'center',
    justifyContent: 'center',
  },
  panel: {
    flex: 1,
    marginTop: 12,
    paddingTop: 20,
    paddingHorizontal: MARGIN,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: PANEL,
  },
  heading: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  segments: {
    marginTop: 14,
    height: SEGMENT,
    padding: 3,
    borderRadius: SEGMENT / 2,
    backgroundColor: TRACK,
    flexDirection: 'row',
  },
  thumb: {
    position: 'absolute',
    left: 3,
    top: 3,
    bottom: 3,
    borderRadius: (SEGMENT - 6) / 2,
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
  segmentOn: {
    color: INK,
    fontWeight: '600',
  },
  stats: {
    flexDirection: 'row',
    marginTop: 18,
  },
  stat: {
    flex: 1,
  },
  caption: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
  },
  big: {
    marginTop: 2,
    color: INK,
    fontFamily: MONO,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '700',
  },
  small: {
    color: MUTE,
    fontFamily: MONO,
    fontSize: 13,
    fontWeight: '500',
  },
  status: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginTop: 2,
  },
  check: {
    width: 14,
    height: 14,
    borderRadius: 4,
    backgroundColor: GOOD,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusText: {
    color: INK,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
    letterSpacing: 0.4,
  },
  chart: {
    flex: 1,
    marginTop: 4,
  },
  axis: {
    position: 'absolute',
    color: AXIS,
    fontSize: 10,
    lineHeight: 14,
    fontVariant: ['tabular-nums'],
  },
  axisStrong: {
    color: INK,
    fontWeight: '600',
  },
  days: {
    position: 'absolute',
    left: 0,
    flexDirection: 'row',
  },
  day: {
    alignItems: 'center',
  },
  dayText: {
    fontSize: 11,
    lineHeight: 15,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  badge: {
    position: 'absolute',
    left: 0,
    width: DAY_BADGE.width,
    height: DAY_BADGE.height,
    borderRadius: 8,
    borderCurve: 'continuous',
    backgroundColor: INK,
    overflow: 'hidden',
  },
  badgeRow: {
    top: 3,
  },
  stem: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 1,
    backgroundColor: INK,
  },
  label: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: TIME_LABEL.width,
    height: TIME_LABEL.height,
    borderRadius: TIME_LABEL.height / 2,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 4, blurRadius: 12, color: 'rgba(0,0,0,0.35)' }],
  },
  labelText: {
    color: BADGE_INK,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  legend: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  stages: {
    flexDirection: 'row',
    gap: 16,
    marginTop: 8,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  legendText: {
    color: MUTE,
    fontSize: 11,
    lineHeight: 14,
  },
  swatch: {
    width: 12,
    height: 12,
    borderRadius: 3,
  },
  swatchIdeal: {
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  swatchWake: {
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: 'rgba(255,255,255,0.35)',
  },
  legendLine: {
    width: 12,
    height: 1.5,
    backgroundColor: INK,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  tabBar: {
    marginTop: 16,
    alignSelf: 'center',
    flexDirection: 'row',
    padding: 4,
    borderRadius: (TAB_HEIGHT + 8) / 2,
    backgroundColor: TAB_BAR,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  tab: {
    width: 92,
    height: TAB_HEIGHT,
    borderRadius: TAB_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
  },
  tabOn: {
    backgroundColor: 'rgba(255,255,255,0.07)',
  },
  tabLabel: {
    color: INK,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
  },
});
