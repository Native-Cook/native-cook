import { Canvas, Circle, LinearGradient, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient as Wash } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  type SharedValue,
  Easing,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { type GestureKind, GestureIcon, PLAY_FOR } from './GestureIcon';

export type { GestureKind } from './GestureIcon';

export type GestureRow = {
  title: string;
  subtitle: string;
  gesture: GestureKind;
};

type Props = {
  title?: string;
  subtitle?: string;
  rows?: GestureRow[];
  footer?: string;
  /** The row highlighted first. */
  initialIndex?: number;
  /** Walks the highlight through the rows by itself, twice every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (index: number, row: GestureRow) => void;
  /** Called when the footer is tapped: the guide is done. */
  onDone?: () => void;
};

const ROWS: GestureRow[] = [
  { title: 'Go forward', subtitle: 'Tap the screen', gesture: 'tap' },
  { title: 'Pause and Seek', subtitle: 'Hold and slide left or right', gesture: 'hold' },
  { title: 'Go back', subtitle: 'Tap the left edge', gesture: 'edge' },
  { title: 'Move between stories', subtitle: 'Swipe left or right', gesture: 'swipe' },
];

const SKY = ['#1d4f8f', '#3a2c8a', '#2a1259'];
const FLOOR = ['rgba(12,8,24,0)', 'rgba(12,8,24,0.85)'];
const GLOW = ['rgba(255,138,61,0.62)', 'rgba(224,65,47,0.38)', 'rgba(224,65,47,0)'];
const INK = '#ffffff';

// Layout, in points.
const MARGIN = 24;
const HEADER_TOP = 48;
const SUBTITLE_W = 280;
const ROWS_TOP = 200;
const ROW_H = 80;
const ROW_GAP = 14;
const PITCH = ROW_H + ROW_GAP;
const ICON = 46;
const ICON_LEFT = 20;
const TEXT_LEFT = ICON_LEFT + ICON + 18;
// A row's title and subtitle, stacked.
const TEXT_H = 43;
const RADIUS = 20;
const FOOTER_GAP = 40;
const FLOOR_H = 160;
// The glow: its radius, where its orbit is centred (a share of the width, points below the first
// row's top) and the orbit's radii.
const GLOW_R = 170;
const GLOW_X = 0.62;
const GLOW_Y = 70;
const ORBIT_X = 46;
const ORBIT_Y = 64;

// The highlight springs between rows (response 0.35 s at damping ratio 0.85) and stretches with its
// distance to the row it is heading for. The row under it grows by ACTIVE_SCALE and brightens.
const SPRING = { mass: 1, stiffness: 322.27, damping: 30.52 };
const STRETCH = 0.3;
const STRETCH_MAX = 22;
const ACTIVE_SCALE = 0.05;
const TITLE_DIM = 0.82;
const SUBTITLE_DIM = 0.55;
const SUBTITLE_LIT = 0.7;

// The autoPlay loop: the highlight stops 1.35 s on each row, twice round from "Go forward", which is
// where it rests at the start and the end; the glow goes once round its orbit in the same time.
const LOOP = 10.8;
const STOP = 1.35;
const FIRST_HOP = 0.65;
const HOPS = [1, 2, 3, 0, 1, 2, 3, 0];
const USER_HOLD = 3;

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function Row({
  row,
  index,
  width,
  top,
  highlight,
  played,
  onPress,
}: {
  row: GestureRow;
  index: number;
  width: number;
  top: number;
  highlight: SharedValue<number>;
  played: number;
  onPress: () => void;
}) {
  // Seconds since the row was last chosen; it rests at the end of its little performance.
  const since = useSharedValue(PLAY_FOR);
  useEffect(() => {
    if (played === 0) return;
    since.set(0);
    since.set(withTiming(PLAY_FOR, { duration: PLAY_FOR * 1000, easing: Easing.linear }));
  }, [played, since]);

  // 1 when the highlight sits on this row, 0 a row away.
  const active = useDerivedValue(() => clamp01(1 - Math.abs(highlight.get() - index * PITCH) / PITCH));
  const contentStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + ACTIVE_SCALE * active.get() }] }));
  const titleStyle = useAnimatedStyle(() => ({ opacity: TITLE_DIM + (1 - TITLE_DIM) * active.get() }));
  const subtitleStyle = useAnimatedStyle(() => ({ opacity: SUBTITLE_DIM + (SUBTITLE_LIT - SUBTITLE_DIM) * active.get() }));

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${row.title}. ${row.subtitle}`}
      style={[styles.row, { top, width }]}
    >
      <Animated.View style={[styles.rowContent, contentStyle]}>
        <View style={styles.icon}>
          <GestureIcon kind={row.gesture} size={ICON} since={since} color={INK} />
        </View>
        <View style={styles.rowText}>
          <Animated.Text style={[styles.rowTitle, titleStyle]} numberOfLines={1}>
            {row.title}
          </Animated.Text>
          <Animated.Text style={[styles.rowSubtitle, subtitleStyle]} numberOfLines={1}>
            {row.subtitle}
          </Animated.Text>
        </View>
      </Animated.View>
    </Pressable>
  );
}

/**
 * A guide to the gestures of a story viewer on a deep blue-to-violet screen with a warm glow
 * drifting behind it. A frosted highlight springs from row to row, stretching as it travels; the
 * row it lands on grows and brightens and its hand icon acts the gesture out.
 */
export function GlowGestureGuide({
  title = 'Watching Stories',
  subtitle = 'You can use these gestures to control playback.',
  rows = ROWS,
  footer = 'Tap to keep watching',
  initialIndex = 0,
  autoPlay = false,
  onSelect,
  onDone,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [index, setIndex] = useState(initialIndex);
  // How many times each row has been chosen: a change plays its icon.
  const [plays, setPlays] = useState<number[]>(() => rows.map(() => 0));
  const [touches, setTouches] = useState(0);

  const width = size.width;
  const height = size.height;
  const cardW = width - MARGIN * 2;
  const rowsTop = insets.top + ROWS_TOP;

  const highlight = useSharedValue(initialIndex * PITCH);
  // Once round the glow's orbit, every LOOP seconds.
  const orbit = useSharedValue(0);
  useEffect(() => {
    if (reducedMotion) return;
    orbit.set(withRepeat(withTiming(1, { duration: LOOP * 1000, easing: Easing.linear }), -1, false));
    return () => cancelAnimation(orbit);
  }, [orbit, reducedMotion]);

  const glowCenter = useDerivedValue(() => {
    const a = orbit.get() * Math.PI * 2;
    return vec(width * GLOW_X + ORBIT_X * Math.cos(a), rowsTop + GLOW_Y + ORBIT_Y * Math.sin(a));
  });

  const cardStyle = useAnimatedStyle(() => {
    const target = index * PITCH;
    const h = ROW_H + Math.min(STRETCH_MAX, Math.abs(highlight.get() - target) * STRETCH);
    return { height: h, transform: [{ translateY: highlight.get() + (ROW_H - h) / 2 }] };
  });

  const select = (next: number, auto: boolean) => {
    if (next === index) return;
    setIndex(next);
    setPlays((list) => list.map((n, i) => (i === next ? n + 1 : n)));
    highlight.set(withSpring(next * PITCH, SPRING));
    if (!auto && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onSelect?.(next, rows[next]);
  };

  const latest = useRef({ select, index });
  useEffect(() => {
    latest.current = { select, index };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const at = (time: number, run: () => void) => timers.push(setTimeout(run, Math.max(0, time - Date.now())));
    const cycle = (origin: number) => {
      HOPS.forEach((row, k) => at(origin + (FIRST_HOP + k * STOP) * 1000, () => latest.current.select(row % rows.length, true)));
      at(origin + LOOP * 1000, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      at(Date.now() + USER_HOLD * 1000, () => {
        if (latest.current.index === 0) return cycle(Date.now());
        // Back to the first row the way the loop ends, then the loop starts over from there.
        latest.current.select(0, true);
        cycle(Date.now() + (LOOP - FIRST_HOP - (HOPS.length - 1) * STOP) * 1000);
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, rows.length]);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={width} height={height}>
          <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={SKY} />
        </Rect>
        <Circle c={glowCenter} r={GLOW_R}>
          <RadialGradient c={glowCenter} r={GLOW_R} colors={GLOW} positions={[0, 0.45, 1]} />
        </Circle>
        <Rect x={0} y={height - FLOOR_H} width={width} height={FLOOR_H}>
          <LinearGradient start={vec(0, height - FLOOR_H)} end={vec(0, height)} colors={FLOOR} />
        </Rect>
      </Canvas>

      <View style={[styles.header, { top: insets.top + HEADER_TOP }]} pointerEvents="none">
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle} lineBreakStrategyIOS="standard">
          {subtitle}
        </Text>
      </View>

      <Animated.View pointerEvents="none" style={[styles.card, { top: rowsTop, width: cardW }, cardStyle]}>
        <Wash colors={['rgba(255,255,255,0.1)', 'rgba(255,255,255,0)']} style={StyleSheet.absoluteFill} />
      </Animated.View>

      {rows.map((row, i) => (
        <Row
          key={row.title}
          row={row}
          index={i}
          width={cardW}
          top={rowsTop + i * PITCH}
          highlight={highlight}
          played={plays[i] ?? 0}
          onPress={() => {
            setTouches((n) => n + 1);
            select(i, false);
          }}
        />
      ))}

      <Pressable
        style={[styles.footer, { bottom: Math.max(insets.bottom, 16) + FOOTER_GAP }]}
        accessibilityRole="button"
        onPress={() => {
          setTouches((n) => n + 1);
          onDone?.();
        }}
      >
        {({ pressed }) => (
          <Text style={[styles.footerLabel, { opacity: pressed ? 0.6 : 0.9 }]} numberOfLines={1}>
            {footer}
          </Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SKY[1],
  },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  title: {
    color: INK,
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '600',
    includeFontPadding: false,
  },
  subtitle: {
    marginTop: 8,
    width: SUBTITLE_W,
    color: 'rgba(255,255,255,0.6)',
    fontSize: 16,
    lineHeight: 21,
    textAlign: 'center',
    includeFontPadding: false,
  },
  card: {
    position: 'absolute',
    left: MARGIN,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: 'rgba(255,255,255,0.13)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.22)',
  },
  row: {
    position: 'absolute',
    left: MARGIN,
    height: ROW_H,
  },
  rowContent: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  icon: {
    position: 'absolute',
    left: ICON_LEFT,
    top: (ROW_H - ICON) / 2,
  },
  rowText: {
    position: 'absolute',
    left: TEXT_LEFT,
    right: 12,
    top: (ROW_H - TEXT_H) / 2,
  },
  rowTitle: {
    color: INK,
    fontSize: 19,
    lineHeight: 23,
    fontWeight: '600',
    includeFontPadding: false,
  },
  rowSubtitle: {
    color: INK,
    fontSize: 16,
    lineHeight: 20,
    includeFontPadding: false,
  },
  footer: {
    position: 'absolute',
    alignSelf: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  footerLabel: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
