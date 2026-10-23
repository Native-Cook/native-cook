import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import { Canvas, Circle, DashPathEffect, Group, Line, LinearGradient, Path, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
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
import { scheduleOnRN } from 'react-native-worklets';

import {
  type Frame,
  LOOP,
  MORPH_FOR,
  RETURN_FOR,
  USER_HOLD,
  clamp01,
  curvePath,
  dayX,
  filmAt,
  mixSeries,
  valueAt,
  yOf,
} from './energy-chart';
import { RollingNumber } from './RollingNumber';
import { SpotIcon } from './SpotIcon';

export { LOOP };

type Props = {
  /** kW through the week, evenly spaced from the left edge of the chart to the right. */
  data?: number[];
  /** The same week with Eco mode on; as many values as `data`. */
  ecoData?: number[];
  /** The values the chart's height spans. */
  range?: [number, number];
  /** What the home draws now, with Eco mode off and on, shown while nobody scrubs. */
  current?: number;
  ecoCurrent?: number;
  /** Dollars per kWh, for the cost under the number. */
  rate?: number;
  days?: string[];
  title?: string;
  subtitle?: string;
  mode?: string;
  place?: string;
  unit?: string;
  ecoLabel?: string;
  ecoHint?: string;
  actionLabel?: string;
  /** Glides the cursor across the week and toggles Eco mode by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the scrubbed kW while a finger is on the chart, and null when it lifts. */
  onScrub?: (value: number | null) => void;
  onEcoChange?: (on: boolean) => void;
  onAction?: () => void;
};

const DATA = [12.6, 11.6, 7.4, 6.2, 10.8, 11.4, 10.2, 15.6, 17.2, 14.4, 14.0, 16.0, 18.0];
// Eco mode flattens the week: a little more than half of each value, on a raised floor.
const ECO_SCALE = 0.55;
const ECO_FLOOR = 2;
const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

const PAGE = '#f6f6f6';
const CARD = '#fcfcfc';
const INK = '#111111';
const BODY = '#4b5563';
const MUTE = '#6b7280';
const AXIS = '#9ca3af';
const RULE = '#e5e7eb';
const ORANGE = '#ee480e';
const ORANGE_SOFT = '#fdede6';
const TRACK = '#edeef2';
const PILL = '#f1f3f7';
// The area under the line: orange fading to nothing at the baseline.
const FILL = ['rgba(240,96,40,0.2)', 'rgba(240,96,40,0.07)', 'rgba(240,96,40,0)'];
const FILL_AT = [0, 0.55, 1];

const SIDE = 16;
const PAD = 24;
const RADIUS = 40;
// The chart: room above the highest value for the line's stroke, the value range, then the baseline,
// the day ticks and their labels.
const CHART_TOP = 12;
const PLOT = 96;
const BASE_GAP = 10;
const TICK_GAP = 9;
const TICK = 5;
const LABEL_GAP = 6;
const LINE = 2.5;
const DOT = 5;
const HALO = 11;
const DASH = [4, 4];
// The bubble rides 12 points beside the dot and 8 above the curve under it.
const BUBBLE_W = 88;
const BUBBLE_H = 34;
const BUBBLE_GAP = 12;
const BUBBLE_LIFT = 8;
const SIDE_FLIP = 220;
const TOGGLE_W = 74;
const TOGGLE_H = 40;
const KNOB = 32;
const KNOB_INSET = (TOGGLE_H - KNOB) / 2;
const KNOB_TRAVEL = TOGGLE_W - KNOB - KNOB_INSET * 2;
const KNOB_SPRING = { damping: 14, stiffness: 220, mass: 0.8 };
const DOT_SPRING = { damping: 11, stiffness: 320 };
const ROW = 56;

const SMOOTH = Easing.inOut(Easing.cubic);

function haptic(kind: 'tick' | 'tap') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A home-energy card: this week's draw as a smooth orange area, scrubbed with a finger. A dashed
 * cursor and a dot follow the curve exactly, a bubble rides beside them with the value on rolling
 * reels, and the header and the cost follow too. Eco mode morphs the line into a flatter week.
 */
export function EnergyScrubAreaCard({
  data = DATA,
  ecoData,
  range = [0, 20],
  current = 1.2,
  ecoCurrent = 0.7,
  rate = 0.225,
  days = DAYS,
  title = 'Energy',
  subtitle = 'This week',
  mode = 'Comfort',
  place = 'Home',
  unit = 'kW',
  ecoLabel = 'Eco mode',
  ecoHint = 'Maximize savings',
  actionLabel = 'Spot savings',
  autoPlay = false,
  onScrub,
  onEcoChange,
  onAction,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const width = window.width - SIDE * 2 - PAD * 2;
  const base = CHART_TOP + PLOT + BASE_GAP;
  const height = base + TICK_GAP + TICK;
  const frame: Frame = { width, top: CHART_TOP, bottom: CHART_TOP + PLOT, min: range[0], max: range[1] };
  const eco = ecoData && ecoData.length === data.length ? ecoData : data.map((v) => v * ECO_SCALE + ECO_FLOOR);

  const [ecoOn, setEcoOn] = useState(false);

  // Shared by touch and autoPlay: what the film or a finger sets, the drawing reads.
  const cursor = useSharedValue(dayX(0));
  const held = useSharedValue(0);
  const down = useSharedValue(0);
  const morph = useSharedValue(0);
  const knob = useSharedValue(0);
  const side = useSharedValue(0);
  const lastDay = useSharedValue(-1);
  // autoPlay's clock: the first frame's timestamp, or -1 to take the next one.
  const auto = useSharedValue(autoPlay);
  const origin = useSharedValue(-1);

  useFrameCallback((info) => {
    if (!auto.get()) return;
    if (origin.get() < 0) origin.set(info.timestamp);
    const t = (((info.timestamp - origin.get()) / 1000) % LOOP + LOOP) % LOOP;
    const film = filmAt(t, still);
    // Only what changed is written, so a still stretch of the loop does not repaint.
    if (cursor.get() !== film.cursor) cursor.set(film.cursor);
    if (held.get() !== film.held) held.set(film.held);
    if (down.get() !== film.down) down.set(film.down);
    if (morph.get() !== film.eco) morph.set(film.eco);
    if (knob.get() !== film.knob) knob.set(film.knob);
  });

  const values = useDerivedValue(() => mixSeries(data, eco, morph.get()));
  const line = useDerivedValue(() => curvePath(values.get(), frame, null));
  const area = useDerivedValue(() => curvePath(values.get(), frame, base));
  const scrubbed = useDerivedValue(() => valueAt(values.get(), cursor.get()));
  const dot = useDerivedValue(() => vec(cursor.get() * width, yOf(scrubbed.get(), frame)));
  const cursorTop = useDerivedValue(() => vec(cursor.get() * width, CHART_TOP));
  const cursorBottom = useDerivedValue(() => vec(cursor.get() * width, base - 2));
  const shown = useDerivedValue(() => clamp01(held.get()));
  const dotRadius = useDerivedValue(() => DOT * Math.max(0, held.get()));
  const haloRadius = useDerivedValue(() => HALO * Math.max(0, held.get()));
  // The header and the cost follow the finger, and otherwise show what the home draws now.
  const headline = useDerivedValue(() => (down.get() ? scrubbed.get() : current + (ecoCurrent - current) * morph.get()));
  const cost = useDerivedValue(() => headline.get() * rate);

  // The bubble flips to the left of the dot where it would leave the chart on the right.
  useAnimatedReaction(
    () => (cursor.get() * width + BUBBLE_GAP + BUBBLE_W > width ? 1 : 0),
    (next, prev) => {
      if (prev === null) side.set(next);
      else if (next !== prev) side.set(still ? next : withTiming(next, { duration: SIDE_FLIP, easing: SMOOTH }));
    },
  );

  const bubbleStyle = useAnimatedStyle(() => {
    const x = cursor.get() * width;
    const left = x + BUBBLE_GAP + (-BUBBLE_W - BUBBLE_GAP * 2) * side.get();
    // Above the highest point of the curve under the bubble, and never over the cursor's dot.
    const v = values.get();
    let top = yOf(scrubbed.get(), frame);
    for (let i = 0; i <= 4; i++) top = Math.min(top, yOf(valueAt(v, (left + (BUBBLE_W * i) / 4) / width), frame));
    return {
      opacity: shown.get(),
      transform: [{ translateX: left }, { translateY: top - BUBBLE_LIFT - BUBBLE_H }, { scale: 0.92 + 0.08 * shown.get() }],
    };
  });

  const trackStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(clamp01(knob.get()), [0, 1], [TRACK, ORANGE], 'RGB', { gamma: 1 }),
  }));
  const knobStyle = useAnimatedStyle(() => ({ transform: [{ translateX: KNOB_TRAVEL * knob.get() }] }));
  const offGlyph = useAnimatedStyle(() => ({ opacity: 1 - clamp01(knob.get()) }));
  const onGlyph = useAnimatedStyle(() => ({ opacity: clamp01(knob.get()) }));

  // A touch takes over from autoPlay at once; some seconds after the last one the card settles and
  // plays again. A finger still on the chart keeps it waiting: its lift counts as a touch.
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  useEffect(() => {
    if (!autoPlay || touches === 0) return;
    const settle = setTimeout(() => {
      if (down.get()) return;
      const timing = { duration: still ? 0 : RETURN_FOR * 1000, easing: SMOOTH };
      held.set(withTiming(0, timing));
      morph.set(withTiming(0, timing));
      knob.set(withTiming(0, timing));
      cursor.set(withTiming(dayX(0), timing));
      setEcoOn(false);
    }, USER_HOLD * 1000);
    const replay = setTimeout(() => {
      if (down.get()) return;
      origin.set(-1);
      auto.set(true);
    }, (USER_HOLD + RETURN_FOR) * 1000);
    return () => {
      clearTimeout(settle);
      clearTimeout(replay);
    };
  }, [autoPlay, touches, still, down, held, morph, knob, cursor, origin, auto]);

  const scrub = (value: number | null) => onScrub?.(value);

  const pan = Gesture.Pan()
    .minDistance(0)
    .shouldCancelWhenOutside(false)
    .onBegin((event) => {
      auto.set(false);
      scheduleOnRN(touched);
      cursor.set(clamp01(event.x / width));
      down.set(1);
      held.set(still ? 1 : withSpring(1, DOT_SPRING));
      lastDay.set(Math.round(cursor.get() * 7 - 0.5));
      scheduleOnRN(scrub, scrubbed.get());
    })
    .onUpdate((event) => {
      cursor.set(clamp01(event.x / width));
      // A tick each time the cursor passes a day.
      const day = Math.round(cursor.get() * 7 - 0.5);
      if (day !== lastDay.get()) {
        lastDay.set(day);
        scheduleOnRN(haptic, 'tick');
      }
      scheduleOnRN(scrub, scrubbed.get());
    })
    .onFinalize(() => {
      down.set(0);
      held.set(withTiming(0, { duration: still ? 120 : 250 }));
      scheduleOnRN(touched);
      scheduleOnRN(scrub, null);
    });

  // A tap outside the chart stops autoPlay where it is, lifting the finger it may have had down.
  const takeOver = () => {
    if (auto.get()) {
      auto.set(false);
      down.set(0);
      held.set(withTiming(0, { duration: still ? 120 : 250 }));
    }
    touched();
  };

  const toggle = () => {
    takeOver();
    haptic('tap');
    // The knob, not the state, says where the switch is: autoPlay may have moved it.
    const next = knob.get() < 0.5;
    setEcoOn(next);
    morph.set(still ? (next ? 1 : 0) : withTiming(next ? 1 : 0, { duration: MORPH_FOR * 1000, easing: SMOOTH }));
    knob.set(still ? (next ? 1 : 0) : withSpring(next ? 1 : 0, KNOB_SPRING));
    onEcoChange?.(next);
  };

  const digitsFont = { fontSize: 17, lineHeight: 22, fontWeight: '600' as const, color: INK };
  const headlineFont = { fontSize: 34, lineHeight: 41, fontWeight: '600' as const, color: INK, letterSpacing: -0.6 };
  const costFont = { fontSize: 13, lineHeight: 18, fontWeight: '500' as const, color: BODY };

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="dark" />
      <View style={[styles.heading, { paddingTop: insets.top + 12 }]}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </View>

      <View style={[styles.middle, { paddingBottom: insets.bottom + 48 }]}>
        <View style={styles.card}>
          <View style={styles.header}>
            <View>
              <View style={styles.mode}>
                <View style={styles.badge}>
                  <Ionicons name="flash" size={17} color={ORANGE} />
                </View>
                <Text style={styles.modeLabel}>{mode}</Text>
              </View>
              <Text style={styles.place}>{place}</Text>
            </View>
            <View style={styles.reading}>
              <View style={styles.headline}>
                <RollingNumber value={headline} decimals={1} wholeDigits={2} font={headlineFont} still={still} />
                <Text style={styles.unit}>{unit}</Text>
              </View>
              <View style={styles.cost}>
                <Text style={styles.costLabel}>~$</Text>
                <RollingNumber value={cost} decimals={2} wholeDigits={2} font={costFont} still={still} />
                <Text style={styles.costLabel}> / hr</Text>
              </View>
            </View>
          </View>

          <GestureDetector gesture={pan}>
            <View style={{ height: height + LABEL_GAP + 16 }} accessible accessibilityLabel={`${place} energy use this week`}>
              <Canvas style={{ width, height }}>
                <Path path={area}>
                  <LinearGradient start={vec(0, CHART_TOP)} end={vec(0, base)} colors={FILL} positions={FILL_AT} />
                </Path>
                <Line p1={vec(0, base)} p2={vec(width, base)} color={RULE} strokeWidth={1} />
                {days.map((day, index) => (
                  <Line
                    key={day}
                    p1={vec(dayX(index) * width, base + TICK_GAP)}
                    p2={vec(dayX(index) * width, base + TICK_GAP + TICK)}
                    color={AXIS}
                    strokeWidth={1}
                  />
                ))}
                <Group opacity={shown}>
                  <Line p1={cursorTop} p2={cursorBottom} color={AXIS} strokeWidth={1}>
                    <DashPathEffect intervals={DASH} />
                  </Line>
                </Group>
                <Path path={line} style="stroke" strokeWidth={LINE} strokeCap="round" strokeJoin="round" color={ORANGE} />
                <Circle c={dot} r={haloRadius} color={ORANGE} opacity={0.16} />
                <Circle c={dot} r={dotRadius} color={ORANGE} />
              </Canvas>
              <View style={[styles.days, { marginTop: LABEL_GAP }]}>
                {days.map((day, index) => (
                  <Text key={day} style={[styles.day, { left: dayX(index) * width - 20 }]}>
                    {day}
                  </Text>
                ))}
              </View>
              <Animated.View pointerEvents="none" style={[styles.bubble, bubbleStyle]}>
                <RollingNumber value={scrubbed} decimals={1} wholeDigits={2} font={digitsFont} still={still} />
                <Text style={styles.bubbleUnit}> {unit}</Text>
              </Animated.View>
            </View>
          </GestureDetector>

          <View style={styles.eco}>
            <View>
              <Text style={styles.ecoLabel}>{ecoLabel}</Text>
              <Text style={styles.ecoHint}>{ecoHint}</Text>
            </View>
            <Pressable onPress={toggle} hitSlop={8} accessibilityRole="switch" accessibilityLabel={ecoLabel} accessibilityState={{ checked: ecoOn }}>
              <Animated.View style={[styles.track, trackStyle]}>
                <Animated.View style={[styles.knob, knobStyle]}>
                  <Animated.View style={[styles.glyph, offGlyph]}>
                    <MaterialCommunityIcons name="home-lightning-bolt-outline" size={19} color={INK} />
                  </Animated.View>
                  <Animated.View style={[styles.glyph, onGlyph]}>
                    <MaterialCommunityIcons name="home-lightning-bolt" size={19} color={ORANGE} />
                  </Animated.View>
                </Animated.View>
              </Animated.View>
            </Pressable>
          </View>

          <Pressable
            onPress={() => {
              takeOver();
              onAction?.();
            }}
            accessibilityRole="button"
          >
            {({ pressed }) => (
              <View style={[styles.action, { opacity: pressed ? 0.6 : 1 }]}>
                <View style={styles.actionIcon}>
                  <SpotIcon />
                </View>
                <Text style={styles.actionLabel}>{actionLabel}</Text>
                <Ionicons name="chevron-forward" size={20} color={BODY} />
              </View>
            )}
          </Pressable>
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  heading: {
    paddingHorizontal: SIDE + 8,
  },
  title: {
    color: INK,
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '700',
    letterSpacing: -0.4,
  },
  subtitle: {
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
  },
  middle: {
    flex: 1,
    justifyContent: 'center',
  },
  card: {
    marginHorizontal: SIDE,
    padding: PAD,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    boxShadow: [{ offsetX: 0, offsetY: 24, blurRadius: 48, color: 'rgba(0,0,0,0.08)' }],
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  mode: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  badge: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: ORANGE_SOFT,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  modeLabel: {
    color: BODY,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
  },
  place: {
    color: INK,
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '600',
    letterSpacing: -0.6,
  },
  reading: {
    alignItems: 'flex-end',
  },
  headline: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    marginTop: -6,
  },
  unit: {
    color: MUTE,
    fontSize: 17,
    lineHeight: 22,
    marginLeft: 3,
    marginBottom: 5,
  },
  cost: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 24,
    marginTop: 2,
    paddingHorizontal: 8,
    borderRadius: 12,
    backgroundColor: PILL,
  },
  costLabel: {
    color: BODY,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
  },
  days: {
    height: 16,
  },
  day: {
    position: 'absolute',
    top: 0,
    width: 40,
    textAlign: 'center',
    color: MUTE,
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.3,
  },
  bubble: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BUBBLE_W,
    height: BUBBLE_H,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: RULE,
    backgroundColor: '#ffffff',
  },
  bubbleUnit: {
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
  },
  eco: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 24,
  },
  ecoLabel: {
    color: INK,
    fontSize: 20,
    lineHeight: 25,
    fontWeight: '600',
  },
  ecoHint: {
    marginTop: 4,
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
  },
  track: {
    width: TOGGLE_W,
    height: TOGGLE_H,
    borderRadius: TOGGLE_H / 2,
    padding: KNOB_INSET,
  },
  knob: {
    width: KNOB,
    height: KNOB,
    borderRadius: KNOB / 2,
    backgroundColor: '#ffffff',
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 6, color: 'rgba(0,0,0,0.14)' }],
  },
  glyph: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  action: {
    flexDirection: 'row',
    alignItems: 'center',
    height: ROW,
    marginTop: 16,
    paddingLeft: 7,
    paddingRight: 18,
    borderRadius: ROW / 2,
    borderWidth: 1,
    borderColor: RULE,
    backgroundColor: '#ffffff',
  },
  actionIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: RULE,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  actionLabel: {
    flex: 1,
    color: INK,
    fontSize: 18,
    lineHeight: 23,
  },
});
