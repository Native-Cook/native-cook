import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
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
import { scheduleOnRN } from 'react-native-worklets';

import { Roller } from './Roller';
import { type ChartBox, StepsChart, SWITCH_FOR } from './StepsChart';
import { type StepsView, VIEWS, axisLabel, grouped, statsOf } from './steps-data';

const FIELD_TOP = '#2B1BFF';
const FIELD_BOTTOM = '#3A2BFF';
const SHEET = '#101012';
const INK = '#FFFFFF';
const FIELD_MUTE = 'rgba(255,255,255,0.45)';
const FIELD_FAINT = 'rgba(255,255,255,0.3)';
const MUTE = '#8A8A8E';
const DOWN = '#FF5A4E';
const UP = '#3DDC97';
const SWITCH_TRACK = '#1E1E22';
const BUTTON_DIM = '#1E1E22';
// The big number is white text tinted by this gradient laid over it with `darken`, which leaves the
// darker sheet around the digits as it is.
const NUMBER_TINT = ['#7C8CFF', '#C6CCFF'] as const;

// Layout, in points.
const MARGIN = 20;
// The blue field takes this share of the screen; the sheet over the rest has rounded top corners.
const FIELD_SHARE = 0.56;
const SHEET_RADIUS = 28;
const AXIS_WIDTH = 34;
const X_LABELS = 22;
const TICKS = 9;
const KEY = 32;
const NAV_HEIGHT = 48;

// Motion.
const THUMB = { damping: 20, stiffness: 260, mass: 0.7 };

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 8;
const SWITCHES = [
  { at: 1.0, view: 1 },
  { at: 2.8, view: 2 },
  { at: 5.8, view: 0 },
];
// A finger drawn over the month's spikes, by bar.
const DRAG = { down: 4.0, from: 1, to: 11, start: 4.1, end: 5.0, up: 5.25 };
// Seconds after the last touch before the chart returns to the days and the loop starts again.
const USER_HOLD = 3;

type Props = {
  views?: StepsView[];
  unitLabel?: string;
  /** Switches day, week and month by itself every LOOP seconds, with a drag over the month. */
  autoPlay?: boolean;
  onViewChange?: (view: StepsView, index: number) => void;
  /** Called with a bar's index and steps while a finger is on it. */
  onSelect?: (index: number, steps: number) => void;
};

function glide(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
}

function haptic(kind: 'step' | 'press') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'step') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A generic round monogram standing in for the app's mark. */
function Monogram() {
  return (
    <View style={styles.logo}>
      <Text style={styles.logoText}>GO</Text>
    </View>
  );
}

/**
 * A steps screen: bars on an electric-blue field over a black sheet with the total and three
 * stats. The D / W / M switch sinks the bars and grows the next set in a stagger, from seven soft
 * bars to a month of hairline spikes, while every number rolls to its new value. Dragging over the
 * bars lights the one under the finger and shows its steps.
 */
export function StepsBarsToSpikes({ views = VIEWS, unitLabel = 'Steps', autoPlay = false, onViewChange, onSelect }: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [viewIndex, setViewIndex] = useState(0);
  const [picked, setPicked] = useState(-1);
  const [touches, setTouches] = useState(0);
  const [screen, setScreen] = useState({ width: 0, height: 0 });
  const [chart, setChart] = useState({ width: 0, height: 0 });

  const from = useSharedValue(0);
  const to = useSharedValue(0);
  const since = useSharedValue(SWITCH_FOR);
  const finger = useSharedValue(-1);
  const holding = useSharedValue(false);
  const thumb = useSharedValue(0);
  // Seconds into the autoPlay loop, -1 when it is not running; `seen` is the time it last acted on.
  const loop = useSharedValue(-1);
  const seen = useSharedValue(-1);

  const view = views[viewIndex];
  const box: ChartBox = { left: AXIS_WIDTH, width: Math.max(0, chart.width - AXIS_WIDTH), base: chart.height - X_LABELS, height: chart.height - X_LABELS - 12 };
  const count = view.values.length;

  const choose = (index: number, auto: boolean) => {
    if (index === viewIndex) return;
    if (!auto) haptic('press');
    setViewIndex(index);
    onViewChange?.(views[index], index);
    from.set(viewIndex);
    to.set(index);
    since.set(0);
    since.set(withTiming(SWITCH_FOR, { duration: (reducedMotion ? 0 : SWITCH_FOR) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    thumb.set(reducedMotion ? index : withSpring(index, { ...THUMB, reduceMotion: ReduceMotion.Never }));
  };

  // Timers and worklets call into the latest render.
  const latest = useRef({ choose });
  useEffect(() => {
    latest.current = { choose };
  });
  const step = (index: number) => latest.current.choose(index, true);

  const lit = (index: number, hand: boolean) => {
    setPicked(index);
    if (index >= 0) {
      if (hand) haptic('step');
      onSelect?.(index, view.values[index]);
    }
  };

  useAnimatedReaction(
    () => finger.get(),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(lit, now, holding.get());
    },
  );

  // The autoPlay loop presses the switch and drags a finger that is not there.
  useAnimatedReaction(
    () => loop.get(),
    (t) => {
      if (t < 0) return;
      const was = seen.get() > t ? -1 : seen.get();
      seen.set(t);
      for (let i = 0; i < SWITCHES.length; i++) {
        if (was < SWITCHES[i].at && t >= SWITCHES[i].at) scheduleOnRN(step, SWITCHES[i].view);
      }
      if (t >= DRAG.down && t < DRAG.up) finger.set(Math.round(DRAG.from + (DRAG.to - DRAG.from) * glide((t - DRAG.start) / (DRAG.end - DRAG.start))));
      if (was < DRAG.up && t >= DRAG.up) finger.set(-1);
    },
  );

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const begin = () => {
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
      if (holding.get()) return;
      finger.set(-1);
      latest.current.choose(0, true);
      timers.push(setTimeout(begin, SWITCH_FOR * 1000 + 300));
    };
    if (touches === 0) begin();
    else timers.push(setTimeout(resume, USER_HOLD * 1000));
    return () => {
      timers.forEach(clearTimeout);
      cancelAnimation(loop);
      loop.set(-1);
    };
  }, [autoPlay, touches, finger, holding, loop, seen]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    if (!holding.get()) finger.set(-1);
    setTouches((n) => n + 1);
  };

  const barAt = (x: number) => {
    'worklet';
    if (box.width <= 0) return -1;
    return Math.min(count - 1, Math.max(0, Math.floor(((x - box.left) / box.width) * count)));
  };

  const drag = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      cancelAnimation(loop);
      loop.set(-1);
      holding.set(true);
      finger.set(barAt(event.x));
      scheduleOnRN(touched);
    })
    .onChange((event) => {
      finger.set(barAt(event.x));
    })
    .onFinalize(() => {
      holding.set(false);
      finger.set(-1);
      scheduleOnRN(touched);
    });

  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: thumb.get() * KEY }] }));
  const badgeOpacity = useDerivedValue(() => (to.get() === 2 ? Math.min(1, since.get() / 0.3) : from.get() === 2 ? Math.max(0, 1 - since.get() / 0.2) : 0));
  const badgeStyle = useAnimatedStyle(() => ({ opacity: badgeOpacity.get() }));

  const shownSteps = picked >= 0 ? view.values[picked] : view.total;
  const stats = statsOf(shownSteps);
  const change = Math.round(((view.total - view.previous) / Math.max(1, view.previous)) * 100);
  const fieldHeight = screen.height * FIELD_SHARE;
  const ticks = Array.from({ length: TICKS }, (_, i) => (view.max * (TICKS - i)) / TICKS);

  const onScreenLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setScreen({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onScreenLayout}>
      <StatusBar style="light" />
      <LinearGradient colors={[FIELD_TOP, FIELD_BOTTOM]} style={[styles.field, { height: fieldHeight + SHEET_RADIUS }]} />

      <View style={[styles.fieldContent, { paddingTop: insets.top + 8, height: fieldHeight }]}>
        <View style={styles.header}>
          <Animated.View key={`note${viewIndex}`} entering={FadeIn.duration(250)} exiting={FadeOut.duration(150)} style={styles.note}>
            <View style={styles.noteRow}>
              <Ionicons name="remove-circle" size={15} color={FIELD_MUTE} />
              <Text style={styles.noteValue}>{grouped(view.note)}</Text>
            </View>
            <Text style={styles.noteLabel}>{view.noteLabel}</Text>
          </Animated.View>
          <View style={styles.chip}>
            <Ionicons name={change < 0 ? 'arrow-down-circle' : 'arrow-up-circle'} size={13} color={change < 0 ? DOWN : UP} />
            <Text style={[styles.chipValue, { color: change < 0 ? DOWN : UP }]}>{Math.abs(change)}%</Text>
            <Text style={styles.chipLabel}>
              {change < 0 ? 'lower' : 'higher'} {view.changeLabel}
            </Text>
          </View>
        </View>

        <GestureDetector gesture={drag}>
          <View
            style={styles.chart}
            onLayout={(event) => setChart({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height })}
            accessibilityLabel={`${unitLabel} by ${view.key === 'D' ? 'day' : view.key === 'W' ? 'week' : 'day of the month'}`}
          >
            {chart.width > 0 && (
              <>
                <StepsChart width={chart.width} height={chart.height} box={box} views={views} from={from} to={to} since={since} picked={finger} />
                {ticks.map((value, i) => (
                  <Animated.Text
                    key={`${viewIndex}-y${i}`}
                    entering={FadeIn.duration(250).delay(150)}
                    exiting={FadeOut.duration(150)}
                    style={[styles.axis, { top: box.base - (box.height * (TICKS - i)) / TICKS - 7 }]}
                  >
                    {axisLabel(value)}
                  </Animated.Text>
                ))}
                {view.labels.map((label, i) =>
                  label ? (
                    <Animated.Text
                      key={`${viewIndex}-x${i}`}
                      entering={FadeIn.duration(250).delay(150)}
                      exiting={FadeOut.duration(150)}
                      style={[styles.day, { left: box.left + (box.width / count) * (i + 0.5) - 20, top: box.base + 6 }]}
                    >
                      {label}
                    </Animated.Text>
                  ) : null,
                )}
                {view.badge !== undefined && (
                  <Animated.Text style={[styles.badge, badgeStyle]}>{view.badge}</Animated.Text>
                )}
              </>
            )}
          </View>
        </GestureDetector>
      </View>

      <View style={[styles.sheet, { top: fieldHeight, paddingBottom: Math.max(insets.bottom, 12) }]}>
        <View style={styles.sheetTop}>
          <Monogram />
          <View style={styles.switch}>
            <Animated.View style={[styles.thumb, thumbStyle]} />
            {views.map((item, index) => (
              <Pressable
                key={item.key}
                style={styles.key}
                accessibilityRole="tab"
                accessibilityState={{ selected: index === viewIndex }}
                onPress={() => {
                  touched();
                  choose(index, false);
                }}
              >
                <Text style={[styles.keyLabel, index === viewIndex && styles.keyOn]}>{item.key}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        <View style={styles.total}>
          <View>
            <Roller text={grouped(shownSteps)} style={styles.big} still={reducedMotion} />
            <LinearGradient
              pointerEvents="none"
              colors={NUMBER_TINT}
              start={{ x: 0, y: 0 }}
              end={{ x: 0.4, y: 1 }}
              style={[StyleSheet.absoluteFill, styles.tint]}
            />
          </View>
          <Text style={styles.unit}>{picked >= 0 ? `${unitLabel} · ${view.names[picked]}` : unitLabel}</Text>
        </View>

        <View style={styles.stats}>
          {[
            { value: stats.distance, unit: 'km', label: 'Distance', share: 1 },
            { value: stats.calories, unit: 'kcal', label: 'Calories', share: 1.35 },
            { value: stats.floors, unit: '', label: 'Floors', share: 0.65 },
          ].map((stat) => (
            <View key={stat.label} style={{ flex: stat.share }}>
              <View style={styles.statValue}>
                <Roller text={stat.value} style={styles.statNumber} still={reducedMotion} />
                {stat.unit !== '' && <Text style={styles.statUnit}>{stat.unit}</Text>}
              </View>
              <Text style={styles.statLabel}>{stat.label}</Text>
            </View>
          ))}
        </View>

        <View style={styles.nav}>
          <View style={styles.navPill}>
            <Ionicons name="footsteps" size={18} color={SHEET} />
            <Text style={styles.navLabel}>{unitLabel}</Text>
          </View>
          <View style={styles.navRound}>
            <Ionicons name="sparkles" size={17} color={MUTE} />
          </View>
          <View style={styles.navRound}>
            <Ionicons name="settings-sharp" size={17} color={MUTE} />
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SHEET,
  },
  field: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  fieldContent: {
    paddingHorizontal: MARGIN,
  },
  header: {
    height: 44,
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
  },
  note: {
    paddingLeft: 30,
  },
  noteRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginLeft: -20,
  },
  noteValue: {
    color: FIELD_MUTE,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  noteLabel: {
    color: FIELD_FAINT,
    fontSize: 11,
    lineHeight: 14,
  },
  chip: {
    height: 30,
    paddingHorizontal: 12,
    borderRadius: 15,
    backgroundColor: 'rgba(255,255,255,0.12)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  chipValue: {
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
  },
  chipLabel: {
    color: INK,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '500',
  },
  chart: {
    flex: 1,
    marginTop: 8,
    marginBottom: 12,
  },
  axis: {
    position: 'absolute',
    left: 0,
    color: FIELD_MUTE,
    fontSize: 10,
    lineHeight: 14,
    fontVariant: ['tabular-nums'],
  },
  day: {
    position: 'absolute',
    width: 40,
    textAlign: 'center',
    color: FIELD_MUTE,
    fontSize: 11,
    lineHeight: 14,
  },
  badge: {
    position: 'absolute',
    right: 0,
    top: 0,
    color: 'rgba(255,255,255,0.7)',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
    letterSpacing: 0.6,
  },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: MARGIN,
    paddingTop: 20,
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: SHEET,
  },
  sheetTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  logo: {
    width: 48,
    height: 30,
    borderRadius: 15,
    borderWidth: 2.5,
    borderColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoText: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  switch: {
    flexDirection: 'row',
    padding: 3,
    borderRadius: (KEY + 6) / 2,
    backgroundColor: SWITCH_TRACK,
  },
  thumb: {
    position: 'absolute',
    left: 3,
    top: 3,
    width: KEY,
    height: KEY,
    borderRadius: KEY / 2,
    backgroundColor: INK,
  },
  key: {
    width: KEY,
    height: KEY,
    alignItems: 'center',
    justifyContent: 'center',
  },
  keyLabel: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
  },
  keyOn: {
    color: SHEET,
    fontWeight: '800',
  },
  total: {
    marginTop: 16,
    alignItems: 'flex-start',
  },
  big: {
    color: INK,
    fontSize: 60,
    lineHeight: 68,
    fontWeight: '800',
    letterSpacing: -1.5,
    fontVariant: ['tabular-nums'],
  },
  tint: {
    mixBlendMode: 'darken',
  },
  unit: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
  },
  stats: {
    flexDirection: 'row',
    marginTop: 22,
  },
  statValue: {
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  statNumber: {
    color: INK,
    fontSize: 22,
    lineHeight: 26,
    fontWeight: '700',
    letterSpacing: -0.5,
    fontVariant: ['tabular-nums'],
  },
  statUnit: {
    marginLeft: 2,
    marginBottom: 2,
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '700',
  },
  statLabel: {
    marginTop: 2,
    color: MUTE,
    fontSize: 11,
    lineHeight: 14,
  },
  nav: {
    marginTop: 'auto',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 6,
    borderRadius: (NAV_HEIGHT + 12) / 2,
    backgroundColor: '#18181B',
  },
  navPill: {
    height: NAV_HEIGHT,
    paddingHorizontal: 24,
    borderRadius: NAV_HEIGHT / 2,
    backgroundColor: INK,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  navLabel: {
    color: SHEET,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
  },
  navRound: {
    width: NAV_HEIGHT,
    height: NAV_HEIGHT,
    borderRadius: NAV_HEIGHT / 2,
    backgroundColor: BUTTON_DIM,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
