import { MaterialCommunityIcons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  type SharedValue,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type Mood = {
  name: string;
  /** Used in the caption under a selected column: "42% stressed". */
  adjective: string;
  color: string;
};

export type MoodColumn = {
  name: string;
  checkIns: number;
  /** Percent per mood, in the order of `moods`; a column is drawn full height whatever the sum. */
  shares: number[];
};

type Props = {
  heading?: string;
  dateRange?: string;
  title?: string;
  teaser?: string;
  moods?: Mood[];
  columns?: MoodColumn[];
  byMoodLabel?: string;
  bySizeLabel?: string;
  /** Selects two columns in turn and re-sorts the segments and back every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the column's place, or -1 when the selection is cleared. */
  onSelect?: (index: number) => void;
  onSortChange?: (bySize: boolean) => void;
};

const MOODS: Mood[] = [
  { name: 'Joy', adjective: 'joyful', color: '#ffd04e' },
  { name: 'Calm', adjective: 'calm', color: '#4be29b' },
  { name: 'Low', adjective: 'low', color: '#7ba0ff' },
  { name: 'Stress', adjective: 'stressed', color: '#ff5a4a' },
];

const COLUMNS: MoodColumn[] = [
  { name: 'Morning', checkIns: 5, shares: [80, 0, 0, 20] },
  { name: 'Afternoon', checkIns: 7, shares: [28, 14, 14, 42] },
  { name: 'Evening', checkIns: 6, shares: [0, 50, 33, 16] },
  { name: 'Late Night', checkIns: 1, shares: [0, 100, 0, 0] },
];

const BACKGROUND = '#000000';
const INK = '#ffffff';
const SOFT = '#c7c7cc';
const MUTE = '#8e8e93';
const SEGMENT_INK = '#111111';
const EMPTY = '#1c1c1e';
const TOGGLE = '#1c1c1e';
const TOGGLE_THUMB = '#3a3a3c';
const SERIF = Platform.select({ ios: 'ui-serif', default: 'serif' });

const MARGIN = 20;
const GAP = 6;
const COLUMN_RADIUS = 24;
// The selected column widens by this much of an even share; the others share what is left.
const WIDEN = 1.36;
const DIM = 0.5;
const LABEL_GROW = 1.25;
const PULSE = 1.02;
// The chart's height grows with the screen, between these.
const MIN_CHART = 260;
const MAX_CHART = 420;
const TOGGLE_HEIGHT = 30;

// Motion.
const FOCUS_SPRING = { damping: 18, stiffness: 190, mass: 1 };
const PULSE_FOR = 150;
// Re-sorting: every column's segments slide past each other, one column after the other.
const SORT_FOR = 0.7;
const SORT_STAGGER = 0.04;
const TOGGLE_FOR = 220;
const CAPTION_FOR = 200;

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 9;
const SELECT_AT: [number, number][] = [
  [0.8, 1],
  [2.2, 3],
];
// Tapping the selected column again lets it go.
const RELEASE_AT = 3.6;
const SORT_AT = 4.2;
const UNSORT_AT = 6.0;
const USER_HOLD = 3;
const SETTLE = 1;

function clamp(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function easeInOut(t: number) {
  'worklet';
  const x = clamp(t);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

type Piece = { mood: number; share: number; label: number; byMood: number; bySize: number };

/** A column's segments with their tops (0..1) in mood order and in size order, largest at the bottom. */
function stack(column: MoodColumn) {
  const total = column.shares.reduce((sum, v) => sum + v, 0) || 1;
  const pieces = column.shares
    .map((label, mood) => ({ mood, label, share: label / total }))
    .filter((p) => p.share > 0);
  const tops = (order: typeof pieces) => {
    const at = new Map<number, number>();
    let top = 0;
    for (const p of order) {
      at.set(p.mood, top);
      top += p.share;
    }
    return at;
  };
  const byMood = tops(pieces);
  const bySize = tops(pieces.slice().sort((a, b) => a.share - b.share || a.mood - b.mood));
  return pieces.map((p): Piece => ({ ...p, byMood: byMood.get(p.mood) ?? 0, bySize: bySize.get(p.mood) ?? 0 }));
}

function Segment({
  piece,
  color,
  height,
  sort,
  index,
  focus,
  reduced,
}: {
  piece: Piece;
  color: string;
  height: number;
  sort: SharedValue<number>;
  index: number;
  focus: SharedValue<number>;
  reduced: boolean;
}) {
  const style = useAnimatedStyle(() => {
    // Each column starts its slide a little after the one on its left.
    const p = reduced ? Math.round(sort.get()) : easeInOut((sort.get() * (SORT_FOR + index * SORT_STAGGER) - index * SORT_STAGGER) / SORT_FOR);
    return { top: (piece.byMood + (piece.bySize - piece.byMood) * p) * height };
  });
  const labelStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + (LABEL_GROW - 1) * focus.get() }] }));
  return (
    <Animated.View style={[styles.segment, { height: piece.share * height, backgroundColor: color }, style]}>
      {piece.share * height >= 24 && (
        <Animated.Text style={[styles.segmentLabel, labelStyle]} numberOfLines={1}>
          {piece.label}%
        </Animated.Text>
      )}
    </Animated.View>
  );
}

function Column({
  column,
  index,
  moods,
  base,
  wide,
  narrow,
  height,
  focus,
  any,
  pulse,
  sort,
  selected,
  reduced,
}: {
  column: MoodColumn;
  index: number;
  moods: Mood[];
  base: number;
  wide: number;
  narrow: number;
  height: number;
  focus: SharedValue<number>;
  any: SharedValue<number>;
  pulse: SharedValue<number>;
  sort: SharedValue<number>;
  selected: boolean;
  reduced: boolean;
}) {
  const pieces = useMemo(() => stack(column), [column]);
  const layered = useMemo(() => pieces.slice().sort((a, b) => b.share - a.share), [pieces]);
  const top = layered[0];
  const widthStyle = useAnimatedStyle(() => {
    const f = focus.get();
    const a = any.get();
    // Widths always add up to the same row: one column takes WIDEN, the others share the rest.
    return {
      width: base + (wide - base) * f + (narrow - base) * (a - f),
      marginLeft: index === 0 ? 0 : GAP,
      opacity: 1 - DIM * clamp(a - f),
    };
  });
  const barStyle = useAnimatedStyle(() => ({ transform: [{ scale: pulse.get() }] }));

  const caption = top ? `${top.label}% ${moods[top.mood].adjective}` : '';

  return (
    <Animated.View style={widthStyle} accessible accessibilityLabel={`${column.name}, ${column.checkIns} check-ins, ${caption}`}>
      {/* While segments slide past each other the column shows its largest mood behind them, and the
          smaller segments pass over the larger ones. */}
      <Animated.View style={[styles.bar, { height, backgroundColor: top ? moods[top.mood].color : EMPTY }, barStyle]}>
        {layered.map((piece) => (
          <Segment
            key={piece.mood}
            piece={piece}
            color={moods[piece.mood].color}
            height={height}
            sort={sort}
            index={index}
            focus={focus}
            reduced={reduced}
          />
        ))}
      </Animated.View>
      <Text style={styles.name} numberOfLines={1} adjustsFontSizeToFit>
        {column.name}
      </Text>
      <Text style={styles.checkIns} numberOfLines={1} adjustsFontSizeToFit>
        {column.checkIns} {column.checkIns === 1 ? 'check-in' : 'check-ins'}
      </Text>
      <View style={styles.captionSlot}>
        {selected && top && (
          <Animated.Text
            entering={FadeIn.duration(CAPTION_FOR)}
            exiting={FadeOut.duration(CAPTION_FOR)}
            style={[styles.caption, { color: moods[top.mood].color }]}
            numberOfLines={1}
          >
            {caption}
          </Animated.Text>
        )}
      </View>
    </Animated.View>
  );
}

/**
 * A mood journal's day as 100 % stacked columns, one per part of the day. Tapping or dragging
 * across the columns widens the one under the finger while the others narrow and dim; the toggle
 * re-sorts every column's segments from mood order to size order, the segments sliding past each
 * other column after column.
 */
export function MoodStackColumns({
  heading = 'All time',
  dateRange = 'JUN 28 – OCT 12',
  title = 'Your mood during the day',
  teaser = 'Your emotions during the week',
  moods = MOODS,
  columns = COLUMNS,
  byMoodLabel = 'By mood',
  bySizeLabel = 'By size',
  autoPlay = false,
  onSelect,
  onSortChange,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [selected, setSelected] = useState(-1);
  const [bySize, setBySize] = useState(false);
  const [touches, setTouches] = useState(0);
  const [chartSpace, setChartSpace] = useState(0);

  const count = columns.length;
  const row = window.width - MARGIN * 2 - GAP * (count - 1);
  const base = row / count;
  const wide = count > 1 ? base * WIDEN : base;
  const narrow = count > 1 ? (row - wide) / (count - 1) : base;
  // The space below the title, less the names under the columns.
  const height = Math.max(MIN_CHART, Math.min(MAX_CHART, chartSpace - 84));

  const any = useSharedValue(0);
  const focus0 = useSharedValue(0);
  const focus1 = useSharedValue(0);
  const focus2 = useSharedValue(0);
  const focus3 = useSharedValue(0);
  const focus4 = useSharedValue(0);
  const focus5 = useSharedValue(0);
  const focus6 = useSharedValue(0);
  const pulse0 = useSharedValue(1);
  const pulse1 = useSharedValue(1);
  const pulse2 = useSharedValue(1);
  const pulse3 = useSharedValue(1);
  const pulse4 = useSharedValue(1);
  const pulse5 = useSharedValue(1);
  const pulse6 = useSharedValue(1);
  // Up to seven columns.
  const focuses = [focus0, focus1, focus2, focus3, focus4, focus5, focus6];
  const pulses = [pulse0, pulse1, pulse2, pulse3, pulse4, pulse5, pulse6];
  const sort = useSharedValue(0);
  const toggle = useSharedValue(0);

  useEffect(() => {
    const move = (to: number) => (reduced ? to : withSpring(to, FOCUS_SPRING));
    any.set(move(selected >= 0 ? 1 : 0));
    focuses.forEach((f, i) => f.set(move(i === selected ? 1 : 0)));
    if (selected >= 0 && !reduced) {
      pulses[selected].set(
        withSequence(withTiming(PULSE, { duration: PULSE_FOR, easing: Easing.out(Easing.quad) }), withTiming(1, { duration: PULSE_FOR, easing: Easing.inOut(Easing.quad) })),
      );
    }
    // The shared values are stable; only the selection drives them.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected, reduced]);

  useEffect(() => {
    const target = bySize ? 1 : 0;
    const total = (SORT_FOR + (count - 1) * SORT_STAGGER) * 1000;
    sort.set(reduced ? target : withTiming(target, { duration: total, easing: Easing.linear }));
    toggle.set(withTiming(target, { duration: reduced ? 0 : TOGGLE_FOR, easing: Easing.out(Easing.cubic) }));
  }, [bySize, reduced, count, sort, toggle]);

  const select = (index: number, byHand: boolean) => {
    const next = index === selected ? -1 : index;
    setSelected(next);
    if (byHand) tick();
    onSelect?.(next);
  };

  const resort = (next: boolean, byHand: boolean) => {
    setBySize(next);
    if (byHand) tick();
    onSortChange?.(next);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ select, resort });
  useEffect(() => {
    latest.current = { select, resort };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const at = (origin: number, seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
    const cycle = (origin: number) => {
      const last = SELECT_AT[SELECT_AT.length - 1][1] % count;
      SELECT_AT.forEach(([s, index]) => at(origin, s, () => latest.current.select(index % count, false)));
      at(origin, RELEASE_AT, () => latest.current.select(last, false));
      at(origin, SORT_AT, () => latest.current.resort(true, false));
      at(origin, UNSORT_AT, () => latest.current.resort(false, false));
      at(origin, LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        setSelected(-1);
        setBySize(false);
        after(SETTLE * 1000, () => cycle(Date.now()));
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, count]);

  const touched = () => setTouches((n) => n + 1);

  // The selection as the gesture sees it, ahead of React's state.
  const chosen = useSharedValue(-1);
  const pressStart = useSharedValue(-1);
  const pressedSelected = useSharedValue(false);
  const moved = useSharedValue(false);
  useEffect(() => {
    chosen.set(selected);
  }, [selected, chosen]);

  const apply = (index: number) => {
    touched();
    setSelected(index);
    tick();
    onSelect?.(index);
  };

  /** The column under `x`, where the columns sit once the selection has settled. */
  const columnAt = (x: number) => {
    'worklet';
    const current = chosen.get();
    let left = MARGIN;
    for (let i = 0; i < count; i++) {
      const width = current < 0 ? base : i === current ? wide : narrow;
      if (x < left + width + GAP / 2) return i;
      left += width + GAP;
    }
    return count - 1;
  };

  const choose = (index: number) => {
    'worklet';
    if (index === chosen.get()) return;
    chosen.set(index);
    scheduleOnRN(apply, index);
  };

  // A tap selects a column or lets the selected one go; a drag scrubs across the columns.
  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const index = columnAt(e.x);
      pressStart.set(index);
      pressedSelected.set(index === chosen.get());
      moved.set(false);
      choose(index);
      scheduleOnRN(touched);
    })
    .onUpdate((e) => {
      const index = columnAt(e.x);
      if (index !== pressStart.get()) moved.set(true);
      choose(index);
    })
    .onFinalize(() => {
      if (pressedSelected.get() && !moved.get()) {
        chosen.set(-1);
        scheduleOnRN(apply, -1);
      } else scheduleOnRN(touched);
    });

  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: toggle.get() * TOGGLE_WIDTH }] }));

  const onChartSpace = (event: LayoutChangeEvent) => setChartSpace(event.nativeEvent.layout.height);

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <View style={styles.headingRow}>
          <View style={styles.headingLeft}>
            <Text style={styles.heading} numberOfLines={1}>
              {heading}
            </Text>
            <MaterialCommunityIcons name="chevron-down" size={26} color={INK} style={styles.chevron} />
          </View>
        </View>
        <View style={styles.datesRow}>
          <Text style={styles.dates}>{dateRange}</Text>
          <View style={styles.toggle} accessibilityRole="tablist">
            <Animated.View style={[styles.thumb, thumbStyle]} />
            {[byMoodLabel, bySizeLabel].map((label, i) => (
              <Pressable
                key={label}
                style={styles.toggleItem}
                accessibilityRole="tab"
                accessibilityState={{ selected: bySize === (i === 1) }}
                onPress={() => {
                  touched();
                  if (bySize !== (i === 1)) resort(i === 1, true);
                }}
              >
                <Text style={[styles.toggleLabel, { color: bySize === (i === 1) ? INK : MUTE }]}>{label}</Text>
              </Pressable>
            ))}
          </View>
        </View>
        <View style={styles.rule}>
          <View style={styles.ruleMark} />
        </View>
        <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit>
          {title}
        </Text>
      </View>

      <View style={styles.chartSpace} onLayout={onChartSpace}>
        {chartSpace > 0 && (
          <GestureDetector gesture={gesture}>
            <View style={styles.row}>
              {columns.map((column, i) => (
                <Column
                  key={column.name}
                  column={column}
                  index={i}
                  moods={moods}
                  base={base}
                  wide={wide}
                  narrow={narrow}
                  height={height}
                  focus={focuses[i]}
                  any={any}
                  pulse={pulses[i]}
                  sort={sort}
                  selected={i === selected}
                  reduced={reduced}
                />
              ))}
            </View>
          </GestureDetector>
        )}
      </View>

      <View style={[styles.teaser, { height: 64 + insets.bottom }]} pointerEvents="none">
        <Text style={styles.teaserTitle} numberOfLines={1} adjustsFontSizeToFit>
          {teaser}
        </Text>
        <LinearGradient colors={['rgba(0,0,0,0)', BACKGROUND]} locations={[0.1, 0.9]} style={StyleSheet.absoluteFill} />
      </View>
    </GestureHandlerRootView>
  );
}

const TOGGLE_WIDTH = 70;

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  header: {
    paddingHorizontal: MARGIN,
  },
  headingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headingLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 1,
  },
  heading: {
    color: INK,
    fontFamily: SERIF,
    fontSize: 34,
    lineHeight: 42,
    fontWeight: '600',
    letterSpacing: -0.6,
  },
  chevron: {
    marginLeft: 6,
    marginTop: 4,
  },
  toggle: {
    flexDirection: 'row',
    height: TOGGLE_HEIGHT,
    padding: 2,
    borderRadius: TOGGLE_HEIGHT / 2,
    backgroundColor: TOGGLE,
  },
  thumb: {
    position: 'absolute',
    left: 2,
    top: 2,
    width: TOGGLE_WIDTH,
    height: TOGGLE_HEIGHT - 4,
    borderRadius: (TOGGLE_HEIGHT - 4) / 2,
    backgroundColor: TOGGLE_THUMB,
  },
  toggleItem: {
    width: TOGGLE_WIDTH,
    alignItems: 'center',
    justifyContent: 'center',
  },
  toggleLabel: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  datesRow: {
    marginTop: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  dates: {
    color: SOFT,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.6,
  },
  rule: {
    marginTop: 8,
    height: 1,
    backgroundColor: '#3a3a3c',
  },
  ruleMark: {
    width: '22%',
    height: 1,
    backgroundColor: INK,
  },
  title: {
    marginTop: 26,
    color: INK,
    fontFamily: SERIF,
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '600',
    letterSpacing: -0.4,
  },
  chartSpace: {
    flex: 1,
    justifyContent: 'center',
  },
  row: {
    flexDirection: 'row',
    paddingHorizontal: MARGIN,
  },
  bar: {
    borderRadius: COLUMN_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  segment: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentLabel: {
    color: SEGMENT_INK,
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  name: {
    marginTop: 10,
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
    textAlign: 'center',
  },
  checkIns: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'center',
  },
  captionSlot: {
    height: 22,
    marginTop: 4,
    alignItems: 'center',
  },
  caption: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
  },
  teaser: {
    paddingHorizontal: MARGIN,
    overflow: 'hidden',
  },
  teaserTitle: {
    color: INK,
    opacity: 0.55,
    fontFamily: SERIF,
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '600',
    letterSpacing: -0.4,
  },
});
