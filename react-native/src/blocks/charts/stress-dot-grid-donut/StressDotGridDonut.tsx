import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { DotGrid, MORPH_FOR } from './DotGrid';
import { LevelDonut } from './LevelDonut';
import { Roller } from './Roller';
import { DAYS, LEVEL_COLORS, type Period, WEEKS, counts } from './stress-data';

const BACKGROUND = '#000000';
const INK = '#FFFFFF';
const MUTE = '#8E8E93';
const CARD = '#131316';
const CHIP = '#1C1C1F';
const TRACK = '#1C1C1F';
const THUMB = '#2E2E33';
const BUTTON = '#141417';
const TAG = '#FF3B4E';
const TAB_BAR = '#1C1C1F';
const TAB_ON = '#2A2A40';
const ACCENT = LEVEL_COLORS.normal;

// Layout, in points.
const MARGIN = 16;
const NAV = 56;
const ROUND_BUTTON = 40;
const SEGMENT = 36;
const DONUT = 164;
const CHIP_HEIGHT = 62;
const TAB_HEIGHT = 58;
const BUSIEST_SHIFT = 14;
// The share of the morph over which the old text leaves, and the new one arrives at the end.
const HAND_OVER = 0.4;

// Motion.
const THUMB_FOR = 250;
const DONUT_SPRING = { damping: 14, stiffness: 120, mass: 0.9 };
const JUMP_DIP = 0.3;
const JUMP_FOR = 220;
// A swipe this long over the grid switches the view.
const SWIPE = 40;

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 8;
const STEPS = [
  { at: 1.0, view: 1 },
  { at: 5.4, view: 0 },
];
// Seconds after the last touch before the screen returns to the first view and the loop starts again.
const USER_HOLD = 3;

const LEVELS = [
  { label: 'Low', color: LEVEL_COLORS.low },
  { label: 'Normal', color: LEVEL_COLORS.normal },
  { label: 'High', color: LEVEL_COLORS.high },
];

type Props = {
  title?: string;
  /** The two views of the heat map: two-hour periods of a week, and days of four weeks. */
  days?: Period;
  weeks?: Period;
  donutLabel?: string;
  tagLabel?: string;
  /** Switches to the four weeks and back by itself every LOOP seconds. */
  autoPlay?: boolean;
  onViewChange?: (period: Period, index: number) => void;
};

/** How far the old text (side 0) has gone or the new one (side 1) has come, as the morph runs 0 → 1. */
function handOver(m: number, side: 0 | 1) {
  'worklet';
  const from = side === 0 ? 0 : 1 - HAND_OVER;
  return Math.min(1, Math.max(0, (m - from) / HAND_OVER));
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function Tag({ label }: { label: string }) {
  return (
    <View style={styles.tag}>
      <Ionicons name="lock-closed" size={10} color={TAG} />
      <Text style={styles.tagLabel}>{label}</Text>
    </View>
  );
}

function SectionTitle({ title, tag }: { title: string; tag: string }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <Tag label={tag} />
    </View>
  );
}

/**
 * A stress patterns screen: a heat map of dots, one per two-hour period of the week, and a donut of
 * how many were low, normal or high. Switching to four weeks sends every dot to its day in the new
 * grid in a stagger while the grid shrinks, the donut's segments spring to their new lengths and
 * the counts roll; switching back runs it in reverse.
 */
export function StressDotGridDonut({
  title = 'Patterns',
  days = DAYS,
  weeks = WEEKS,
  donutLabel = 'Levels',
  tagLabel = 'Preview',
  autoPlay = false,
  onViewChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [view, setView] = useState(0);
  const [touches, setTouches] = useState(0);
  const [width, setWidth] = useState(0);
  const [segmentWidth, setSegmentWidth] = useState(0);

  const periods = [days, weeks];
  const tallies = [counts(days), counts(weeks)];
  const shares = tallies.map((tally) => tally.map((count) => count / Math.max(1, tally[0] + tally[1] + tally[2])));

  // 0 shows the week's periods, 1 the four weeks; the donut follows on a spring of its own.
  const morph = useSharedValue(0);
  const donut = useSharedValue(0);
  const fade = useSharedValue(1);
  const thumb = useSharedValue(0);
  // Seconds into the autoPlay loop, -1 when it is not running; `seen` is the time it last acted on.
  const loop = useSharedValue(-1);
  const seen = useSharedValue(-1);

  const choose = (index: number, auto: boolean) => {
    if (index === view) return;
    setView(index);
    if (!auto) tick();
    onViewChange?.(periods[index], index);
    if (reducedMotion) {
      morph.set(index);
      donut.set(index);
      thumb.set(index);
      fade.set(
        withSequence(
          withTiming(JUMP_DIP, { duration: 0, reduceMotion: ReduceMotion.Never }),
          withTiming(1, { duration: JUMP_FOR, reduceMotion: ReduceMotion.Never }),
        ),
      );
      return;
    }
    thumb.set(withTiming(index, { duration: THUMB_FOR, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never }));
    morph.set(withTiming(index, { duration: MORPH_FOR * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    donut.set(withSpring(index, { ...DONUT_SPRING, reduceMotion: ReduceMotion.Never }));
  };

  // Timers and worklets call into the latest render.
  const latest = useRef({ choose });
  useEffect(() => {
    latest.current = { choose };
  });
  const step = (index: number) => latest.current.choose(index, true);

  // The autoPlay loop taps the tabs a finger would.
  useAnimatedReaction(
    () => loop.get(),
    (t) => {
      if (t < 0) return;
      const was = seen.get() > t ? -1 : seen.get();
      seen.set(t);
      for (let i = 0; i < STEPS.length; i++) {
        if (was < STEPS[i].at && t >= STEPS[i].at) scheduleOnRN(step, STEPS[i].view);
      }
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
      latest.current.choose(0, true);
      timers.push(setTimeout(begin, MORPH_FOR * 1000 + 300));
    };
    if (touches === 0) begin();
    else timers.push(setTimeout(resume, USER_HOLD * 1000));
    return () => {
      timers.forEach(clearTimeout);
      cancelAnimation(loop);
      loop.set(-1);
    };
  }, [autoPlay, touches, loop, seen]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const swiped = (dx: number) => {
    touched();
    if (dx < -SWIPE) choose(1, false);
    else if (dx > SWIPE) choose(0, false);
  };

  const swipe = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .failOffsetY([-12, 12])
    .onBegin(() => {
      cancelAnimation(loop);
      loop.set(-1);
    })
    .onEnd((event) => {
      scheduleOnRN(swiped, event.translationX);
    });

  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: thumb.get() * segmentWidth }] }));
  // Text swaps one way and then the other, never both at once, so the two never read on top of each other.
  const firstStyle = useAnimatedStyle(() => ({ opacity: 1 - handOver(morph.get(), 0) }));
  const secondStyle = useAnimatedStyle(() => ({ opacity: handOver(morph.get(), 1) }));
  const busiestFirst = useAnimatedStyle(() => {
    const m = handOver(morph.get(), 0);
    return { opacity: 1 - m, transform: [{ translateY: -BUSIEST_SHIFT * m }] };
  });
  const busiestSecond = useAnimatedStyle(() => {
    const m = handOver(morph.get(), 1);
    return { opacity: m, transform: [{ translateY: BUSIEST_SHIFT * (1 - m) }] };
  });

  const tally = tallies[view];

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.content, { paddingTop: insets.top }]}>
        <View style={styles.nav}>
          <View style={styles.round}>
            <Ionicons name="person" size={17} color={INK} />
          </View>
          <View style={styles.navTitle}>
            <Text style={styles.title}>{title}</Text>
            <Ionicons name="lock-closed" size={14} color={INK} />
          </View>
          <View style={styles.round}>
            <Ionicons name="information" size={20} color={INK} />
          </View>
        </View>

        <View style={styles.segments} onLayout={(event: LayoutChangeEvent) => setSegmentWidth((event.nativeEvent.layout.width - 6) / 2)}>
          {segmentWidth > 0 && <Animated.View style={[styles.thumb, { width: segmentWidth }, thumbStyle]} />}
          {periods.map((period, index) => (
            <Pressable
              key={period.label}
              style={styles.segment}
              accessibilityRole="tab"
              accessibilityState={{ selected: index === view }}
              onPress={() => {
                touched();
                choose(index, false);
              }}
            >
              <Text style={[styles.segmentLabel, index === view && styles.segmentOn]}>{period.label}</Text>
            </Pressable>
          ))}
        </View>

        <View style={styles.dateRow}>
          <View style={styles.flex}>
            <Animated.Text style={[styles.date, firstStyle]} numberOfLines={1}>
              {days.range}
            </Animated.Text>
            <Animated.Text style={[styles.date, styles.overlay, secondStyle]} numberOfLines={1}>
              {weeks.range}
            </Animated.Text>
          </View>
          <Tag label={tagLabel} />
        </View>

        <GestureDetector gesture={swipe}>
          <View
            style={styles.grid}
            onLayout={(event) => setWidth(event.nativeEvent.layout.width)}
            accessibilityLabel={`Stress by ${view === 0 ? 'period, last 7 days' : 'day, last 4 weeks'}`}
          >
            {width > 0 && <DotGrid width={width} days={days} weeks={weeks} morph={morph} opacity={fade} />}
          </View>
        </GestureDetector>

        <SectionTitle title="Stress Levels" tag={tagLabel} />
        <View style={styles.levels}>
          <LevelDonut
            size={DONUT}
            colors={LEVELS.map((level) => level.color)}
            from={shares[0]}
            to={shares[1]}
            progress={donut}
            label={donutLabel}
          />
          <View style={styles.chips}>
            {LEVELS.map((level, i) => (
              <View key={level.label} style={styles.chip}>
                <View style={styles.chipHead}>
                  <View style={[styles.dot, { backgroundColor: level.color }]} />
                  <Text style={styles.chipLabel}>{level.label}</Text>
                </View>
                <View style={styles.chipValue}>
                  <Roller text={String(tally[i])} style={styles.count} still={reducedMotion} />
                  <View style={styles.unit}>
                    <Animated.Text style={[styles.unitText, firstStyle]}>{days.unit}</Animated.Text>
                    <Animated.Text style={[styles.unitText, styles.overlay, secondStyle]}>{weeks.unit}</Animated.Text>
                  </View>
                </View>
              </View>
            ))}
          </View>
        </View>

        <SectionTitle title="Stressful Periods" tag={tagLabel} />
        <View style={styles.card}>
          <Animated.View style={busiestFirst}>
            <Text style={styles.cardTitle}>{days.busiestTitle}</Text>
            <Text style={styles.cardValue}>{days.busiest}</Text>
          </Animated.View>
          <Animated.View style={[styles.overlay, styles.cardPad, busiestSecond]}>
            <Text style={styles.cardTitle}>{weeks.busiestTitle}</Text>
            <Text style={styles.cardValue}>{weeks.busiest}</Text>
          </Animated.View>
        </View>
      </View>

      <LinearGradient
        pointerEvents="none"
        colors={['rgba(0,0,0,0)', BACKGROUND]}
        style={[styles.fadeOut, { height: TAB_HEIGHT + Math.max(insets.bottom, 12) + 40 }]}
      />
      <View style={[styles.tabBar, { bottom: Math.max(insets.bottom, 12) }]}>
        <View style={styles.tab}>
          <Ionicons name="time" size={20} color={INK} />
          <Text style={styles.tabLabel}>Today</Text>
        </View>
        <View style={[styles.tab, styles.tabOn]}>
          <Ionicons name="grid" size={19} color={ACCENT} />
          <Text style={[styles.tabLabel, { color: ACCENT }]}>Patterns</Text>
        </View>
        <View style={styles.tab}>
          <Ionicons name="book" size={19} color={INK} />
          <Text style={styles.tabLabel}>Playbook</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  content: {
    flex: 1,
    paddingHorizontal: MARGIN,
    overflow: 'hidden',
  },
  flex: {
    flex: 1,
  },
  overlay: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  nav: {
    height: NAV,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  navTitle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  title: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  round: {
    width: ROUND_BUTTON,
    height: ROUND_BUTTON,
    borderRadius: ROUND_BUTTON / 2,
    backgroundColor: BUTTON,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.12)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  segments: {
    marginTop: 4,
    marginHorizontal: 16,
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
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
  },
  segmentOn: {
    color: INK,
  },
  dateRow: {
    marginTop: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  date: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
  },
  tag: {
    height: 24,
    paddingHorizontal: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,59,78,0.45)',
    backgroundColor: 'rgba(255,59,78,0.1)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  tagLabel: {
    color: INK,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
  },
  grid: {
    marginTop: 12,
  },
  section: {
    marginTop: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sectionTitle: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
  levels: {
    marginTop: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  chips: {
    flex: 1,
    gap: 8,
  },
  chip: {
    height: CHIP_HEIGHT,
    paddingHorizontal: 14,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: CHIP,
    justifyContent: 'center',
  },
  chipHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  chipLabel: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  chipValue: {
    marginTop: 2,
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  count: {
    color: INK,
    fontSize: 22,
    lineHeight: 26,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  unit: {
    marginLeft: 4,
    marginBottom: 3,
  },
  unitText: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
  },
  card: {
    marginTop: 12,
    height: 84,
    padding: 16,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    overflow: 'hidden',
  },
  cardPad: {
    left: 16,
    top: 16,
  },
  cardTitle: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  cardValue: {
    marginTop: 4,
    color: INK,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '600',
  },
  fadeOut: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  tabBar: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    padding: 4,
    borderRadius: (TAB_HEIGHT + 8) / 2,
    backgroundColor: TAB_BAR,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.1)',
  },
  tab: {
    width: 86,
    height: TAB_HEIGHT,
    borderRadius: TAB_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
  },
  tabOn: {
    backgroundColor: TAB_ON,
  },
  tabLabel: {
    color: INK,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
  },
});
