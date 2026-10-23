import { Canvas, Group, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnUI } from 'react-native-worklets';

/** An icon in a 24 × 24 box, as SVG path data made of M, L, C and Z: its outline, and the lines inside it. */
export type HoppingIcon = { outline: string; detail: string };

export type HoppingTab = {
  title: string;
  icon: HoppingIcon;
  /** The heights of the placeholder cards on the tab's page. */
  cards: number[];
  /** A round picture with two lines above the cards. */
  avatar?: boolean;
};

type Props = {
  tabs?: HoppingTab[];
  /** The tab that is open at first, from 0. */
  initialTab?: number;
  /** The screen, the lighter page on it, the bar, and the icons when idle and when chosen. */
  field?: string;
  panel?: string;
  bar?: string;
  idle?: string;
  active?: string;
  /** Goes round the tabs by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the tab a tap chose. */
  onSelect?: (index: number) => void;
};

export const ICONS = {
  home: {
    outline: 'M4.5 11L12 4.5L19.5 11L19.5 19.5L4.5 19.5Z',
    detail: 'M10 19.5L10 16C10 14.9 10.9 14 12 14C13.1 14 14 14.9 14 16L14 19.5',
  },
  messages: {
    outline: 'M5 19L5 11.5C5 7.36 8.36 4 12.5 4C16.64 4 20 7.36 20 11.5C20 15.64 16.64 19 12.5 19Z',
    detail: 'M9.5 11.5L9.51 11.5M12.5 11.5L12.51 11.5M15.5 11.5L15.51 11.5',
  },
  saved: {
    outline: 'M12 19.5C12 19.5 4 15 4 9.5C4 7 6 5 8.3 5C9.9 5 11.3 5.9 12 7.2C12.7 5.9 14.1 5 15.7 5C18 5 20 7 20 9.5C20 15 12 19.5 12 19.5Z',
    detail: 'M7 9.6C7 8.6 7.7 7.8 8.6 7.8',
  },
  profile: {
    outline:
      'M12 4C13.93 4 15.5 5.57 15.5 7.5C15.5 9.43 13.93 11 12 11C10.07 11 8.5 9.43 8.5 7.5C8.5 5.57 10.07 4 12 4Z' +
      'M8.5 14.5L15.5 14.5C16.88 14.5 18 15.62 18 17C18 18.38 16.88 19.5 15.5 19.5L8.5 19.5C7.12 19.5 6 18.38 6 17C6 15.62 7.12 14.5 8.5 14.5Z',
    detail: 'M10.6 8.1C11 9 13 9 13.4 8.1',
  },
} satisfies Record<string, HoppingIcon>;

const TABS: HoppingTab[] = [
  { title: 'Home', icon: ICONS.home, cards: [150, 96, 96] },
  { title: 'Messages', icon: ICONS.messages, cards: [72, 72, 72, 72] },
  { title: 'Saved', icon: ICONS.saved, cards: [96, 150, 96] },
  { title: 'Profile', icon: ICONS.profile, cards: [96, 96], avatar: true },
];

const FIELD = '#244ff6';
const PANEL = '#2e59fe';
const BAR = '#ffffff';
const IDLE = '#b4beef';
const ACTIVE = '#3b55e6';

const SIDE = 16;
const BAR_HEIGHT = 78;
const TOP_RADIUS = 8;
const BOTTOM_RADIUS = 32;
// The page runs on behind the bar this far, so that it shows through a notch.
const PANEL_UNDER = 24;
// The notch: its width along the bar's top edge and its depth at rest. The shoulders and the tip
// are rounded by control points this share of the width from each end of a side.
const NOTCH = 44;
const NOTCH_DEPTH = 16;
const SHOULDER = 0.22;
const TIP = 0.16;
// The dot rests in the notch, its centre this far under the bar's top edge.
const DOT = 10;
const DOT_SINK = 5;
const ICON = 24;
const ICON_Y = 40;
const ICON_LIFT = 4;
const ICON_STROKE = 1.6;
// The page: its content's inset in the panel, and the title's place under the safe area.
const INSET = 24;
const TITLE_TOP = 37;
const AVATAR = 72;

/** Seconds. The loop goes once round the tabs: a hop every HOP_EVERY from HOP_AT. */
export const LOOP = 9;
const HOP_AT = 2;
const HOP_EVERY = 1.8;
// One hop. The dot leaves straight up and starts across after LIFT; it is in the air for AIR plus
// AIR_PER_TAB for each tab it crosses, on a parabola ARC high for a neighbour and ARC_PER_TAB
// higher for each tab more.
const LIFT = 0.1;
const AIR = 0.4;
const AIR_PER_TAB = 0.05;
const AIR_MAX = 0.6;
const ARC = 14;
const ARC_PER_TAB = 23;
const ARC_MIN = 8;
const ARC_MAX = 90;
// The notch it left closes; the one it lands in starts to open just before it arrives, goes a
// fifth too deep under its weight and comes back, while the dot bounces once.
const CLOSE = 0.2;
const OPEN_BEFORE = 0.05;
const OPEN = 0.32;
const PRESS = 2.6;
const BOUNCE = 2;
const BOUNCE_FOR = 0.16;
// An icon takes or loses its colour and its lift in this time.
const TINT = 0.2;
// The page that leaves slides away from where the dot goes; the new one comes from that side.
const PAGE_OUT = 0.25;
const PAGE_IN = 0.28;
const PAGE_SLIDE = 16;
// The clock of a hop runs this long, well past its last movement.
const REST = 1.2;
// With reduced motion the notch and the dot fade out, then in at the new tab, each in this time.
const FADE = 0.15;
// After a touch the loop waits, then goes back to its first tab and waits for that to end.
const USER_HOLD = 3;
const SETTLE = 1.2;

/** Where a hop started: everything that was moving is picked up where it was. */
type Hop = {
  target: number;
  /** The tab whose page is leaving. */
  from: number;
  /** The dot's place along the bar, in tabs, and its height over its rest. */
  x: number;
  up: number;
  /** Each notch's depth and each icon's colour, 0 to 1. */
  depth: number[];
  tint: number[];
  /** How much of the leaving page shows, and how far it has slid. */
  presence: number;
  shift: number;
};

type Sample = {
  depth: number[];
  tint: number[];
  dotX: number;
  dotUp: number;
  dotAlpha: number;
  /** The page that shows, how much of it, and how far aside. */
  page: number;
  presence: number;
  shift: number;
  /** With reduced motion: how far the shown tab's notch is filled in. */
  cover: number;
};

function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

function smooth(t: number) {
  'worklet';
  const u = clamp01(t);
  return u * u * (3 - 2 * u);
}

/** Ease-out that goes past 1 by a fifth and comes back. */
function pressed(t: number) {
  'worklet';
  const u = clamp01(t) - 1;
  return 1 + (PRESS + 1) * u * u * u + PRESS * u * u;
}

/** Everything that moves, `t` seconds into a hop. */
function sample(hop: Hop, t: number, reduced: boolean): Sample {
  'worklet';
  const count = hop.depth.length;
  const depth: number[] = [];
  const tint: number[] = [];
  if (reduced) {
    const arrived = t >= FADE;
    const shown = arrived ? hop.target : hop.from;
    const visible = arrived ? clamp01((t - FADE) / FADE) : hop.presence * (1 - clamp01(t / FADE));
    for (let i = 0; i < count; i++) {
      depth.push(i === shown ? 1 : 0);
      tint.push(i === shown ? visible : 0);
    }
    return { depth, tint, dotX: shown, dotUp: 0, dotAlpha: visible, page: shown, presence: visible, shift: 0, cover: 1 - visible };
  }

  const distance = Math.abs(hop.target - hop.x);
  const air = Math.min(AIR + AIR_PER_TAB * distance, AIR_MAX);
  const arc = Math.min(Math.max(ARC + ARC_PER_TAB * (distance - 1), ARC_MIN), ARC_MAX);
  const flown = clamp01(t / air);
  const landing = t - (air - OPEN_BEFORE);
  for (let i = 0; i < count; i++) {
    const closing = hop.depth[i] * (1 - clamp01(t / CLOSE) * clamp01(t / CLOSE));
    const fading = hop.tint[i] * (1 - clamp01(t / TINT));
    depth.push(i === hop.target ? Math.max(closing, landing > 0 ? pressed(landing / OPEN) : 0) : closing);
    tint.push(i === hop.target ? Math.max(fading, clamp01(landing / TINT)) : fading);
  }
  const bounce = clamp01((t - air) / BOUNCE_FOR);
  const side = hop.target > hop.x ? 1 : hop.target < hop.x ? -1 : 0;
  const gone = clamp01(t / PAGE_OUT);
  const come = smooth(landing / PAGE_IN);
  return {
    depth,
    tint,
    dotX: hop.x + (hop.target - hop.x) * clamp01((t - LIFT) / (air - LIFT)),
    // Gravity: a parabola from where the dot was to its rest, then one small bounce.
    dotUp: t < air ? hop.up * (1 - flown) + 4 * arc * flown * (1 - flown) : 4 * BOUNCE * bounce * (1 - bounce),
    dotAlpha: 1,
    page: landing < 0 ? hop.from : hop.target,
    presence: landing < 0 ? hop.presence * (1 - gone) : come,
    shift: landing < 0 ? hop.shift + (-side * PAGE_SLIDE - hop.shift) * gone : side * PAGE_SLIDE * (1 - come),
    cover: 0,
  };
}

function resting(tab: number, count: number): Hop {
  const zeros = Array.from({ length: count }, () => 0);
  return { target: tab, from: tab, x: tab, up: 0, depth: zeros, tint: zeros, presence: 0, shift: 0 };
}

/** The bar's outline in its own box: a notch in the top edge over each tab that has one. */
function barPath(width: number, depth: number[]) {
  'worklet';
  // A quarter circle as one curve.
  const k = 0.5523;
  const top = TOP_RADIUS;
  const bottom = BOTTOM_RADIUS;
  const tab = width / depth.length;
  const builder = Skia.PathBuilder.Make();
  builder.moveTo(top, 0);
  for (let i = 0; i < depth.length; i++) {
    const d = depth[i] * NOTCH_DEPTH;
    if (d < 0.05) continue;
    const x = tab * (i + 0.5);
    const half = NOTCH / 2;
    builder.lineTo(x - half, 0);
    builder.cubicTo(x - half + NOTCH * SHOULDER, 0, x - NOTCH * TIP, d, x, d);
    builder.cubicTo(x + NOTCH * TIP, d, x + half - NOTCH * SHOULDER, 0, x + half, 0);
  }
  builder.lineTo(width - top, 0);
  builder.cubicTo(width - top + top * k, 0, width, top - top * k, width, top);
  builder.lineTo(width, BAR_HEIGHT - bottom);
  builder.cubicTo(width, BAR_HEIGHT - bottom + bottom * k, width - bottom + bottom * k, BAR_HEIGHT, width - bottom, BAR_HEIGHT);
  builder.lineTo(bottom, BAR_HEIGHT);
  builder.cubicTo(bottom - bottom * k, BAR_HEIGHT, 0, BAR_HEIGHT - bottom + bottom * k, 0, BAR_HEIGHT - bottom);
  builder.lineTo(0, top);
  builder.cubicTo(0, top - top * k, top - top * k, 0, top, 0);
  builder.close();
  return builder.build();
}

/** One notch at rest, closed along the top edge: what fills it in. */
function notchPath(x: number) {
  'worklet';
  const half = NOTCH / 2;
  const builder = Skia.PathBuilder.Make();
  builder.moveTo(x - half, 0);
  builder.cubicTo(x - half + NOTCH * SHOULDER, 0, x - NOTCH * TIP, NOTCH_DEPTH, x, NOTCH_DEPTH);
  builder.cubicTo(x + NOTCH * TIP, NOTCH_DEPTH, x + half - NOTCH * SHOULDER, 0, x + half, 0);
  builder.close();
  return builder.build();
}

function TabIcon({ index, icon, x, idle, active, state }: { index: number; icon: HoppingIcon; x: number; idle: string; active: string; state: SharedValue<Sample> }) {
  const transform = useDerivedValue(() => [{ translateX: x - ICON / 2 }, { translateY: ICON_Y - ICON / 2 - ICON_LIFT * (state.get().tint[index] ?? 0) }]);
  const tint = useDerivedValue(() => state.get().tint[index] ?? 0);
  const layer = (color: string) => (
    <>
      <Path path={icon.outline} color={color} opacity={0.18} />
      <Path path={icon.outline} style="stroke" strokeWidth={ICON_STROKE} strokeCap="round" strokeJoin="round" color={color} />
      <Path path={icon.detail} style="stroke" strokeWidth={ICON_STROKE} strokeCap="round" strokeJoin="round" color={color} opacity={0.7} />
    </>
  );
  return (
    <Group transform={transform}>
      {layer(idle)}
      {/* The chosen colour fades in over the idle one. */}
      <Group opacity={tint}>{layer(active)}</Group>
    </Group>
  );
}

function Page({ index, tab, top, current, state }: { index: number; tab: HoppingTab; top: number; current: boolean; state: SharedValue<Sample> }) {
  const style = useAnimatedStyle(() => {
    const now = state.get();
    const on = now.page === index;
    return { opacity: on ? now.presence : 0, transform: [{ translateX: on ? now.shift : 0 }] };
  });
  return (
    <Animated.View
      pointerEvents="none"
      accessibilityElementsHidden={!current}
      importantForAccessibility={current ? 'auto' : 'no-hide-descendants'}
      style={[styles.page, { paddingTop: top }, style]}
    >
      <Text style={styles.title} numberOfLines={1} accessibilityRole="header">
        {tab.title}
      </Text>
      {tab.avatar && (
        <View style={styles.person}>
          <View style={styles.avatar} />
          <View>
            <View style={[styles.line, styles.long]} />
            <View style={[styles.line, styles.short]} />
          </View>
        </View>
      )}
      {tab.cards.map((height, k) => (
        <View key={k} style={[styles.card, { height }]}>
          <View style={[styles.line, styles.long]} />
          <View style={[styles.line, styles.short]} />
        </View>
      ))}
    </Animated.View>
  );
}

/**
 * A tab bar whose indicator is a white dot resting in a notch of the bar's top edge. Choosing a
 * tab makes the dot hop there on a parabola, higher the further it goes, while the notch it left
 * closes and a new one opens under it as it lands. The page above changes with it.
 */
export function HoppingDotTabBar({
  tabs = TABS,
  initialTab = 0,
  field = FIELD,
  panel = PANEL,
  bar = BAR,
  idle = IDLE,
  active = ACTIVE,
  autoPlay = false,
  onSelect,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const count = tabs.length;
  const first = Math.min(Math.max(initialTab, 0), count - 1);
  const [index, setIndex] = useState(first);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const barWidth = size.width - SIDE * 2;
  const barTop = size.height - Math.max(insets.bottom, 20) - BAR_HEIGHT;
  const tabWidth = barWidth / count;

  const hop = useSharedValue<Hop>(resting(first, count));
  // Seconds since the hop started.
  const clock = useSharedValue(REST);
  const state = useDerivedValue(() => sample(hop.get(), clock.get(), reducedMotion));

  const outline = useDerivedValue(() => barPath(barWidth, state.get().depth));
  const cover = useDerivedValue(() => notchPath(tabWidth * (state.get().page + 0.5)));
  const coverOpacity = useDerivedValue(() => state.get().cover);
  const dotStyle = useAnimatedStyle(() => {
    const now = state.get();
    return {
      opacity: now.dotAlpha,
      transform: [{ translateX: SIDE + tabWidth * (now.dotX + 0.5) - DOT / 2 }, { translateY: barTop + DOT_SINK - DOT / 2 - now.dotUp }],
    };
  });

  useEffect(() => () => cancelAnimation(clock), [clock]);

  const hopTo = (target: number) => {
    'worklet';
    const now = sample(hop.get(), clock.get(), reducedMotion);
    hop.set({ target, from: now.page, x: now.dotX, up: now.dotUp, depth: now.depth, tint: now.tint, presence: now.presence, shift: now.shift });
    clock.set(0);
    clock.set(withTiming(REST, { duration: REST * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  };

  /** Opens a tab: the same path for a tap and for the loop, which is silent. */
  const select = (target: number, byHand: boolean) => {
    if (target === index) return;
    if (byHand && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    setIndex(target);
    scheduleOnUI(hopTo, target);
    if (byHand) onSelect?.(target);
  };

  // The timers read the latest state when they fire.
  const latest = useRef({ select, index, first, count });
  useEffect(() => {
    latest.current = { select, index, first, count };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      for (let k = 0; k < latest.current.count; k++) {
        after(origin + (HOP_AT + k * HOP_EVERY) * 1000 - Date.now(), () => latest.current.select((latest.current.index + 1) % latest.current.count, false));
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // The loop starts over from the tab it was given.
        latest.current.select(latest.current.first, false);
        after(SETTLE * 1000, () => cycle(Date.now()));
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  return (
    <View style={[styles.screen, { backgroundColor: field }]} onLayout={onLayout} onTouchStart={() => setTouches((n) => n + 1)}>
      <StatusBar style="light" />

      <View style={[styles.panel, { height: barTop + PANEL_UNDER, backgroundColor: panel }]}>
        {tabs.map((tab, k) => (
          <Page key={k} index={k} tab={tab} top={insets.top + TITLE_TOP} current={k === index} state={state} />
        ))}
      </View>

      <Canvas style={[styles.bar, { top: barTop, width: barWidth }]} pointerEvents="none">
        <Path path={outline} color={bar} />
        <Path path={cover} color={bar} opacity={coverOpacity} />
        {tabs.map((tab, k) => (
          <TabIcon key={k} index={k} icon={tab.icon} x={tabWidth * (k + 0.5)} idle={idle} active={active} state={state} />
        ))}
      </Canvas>

      <View style={[styles.tabs, { top: barTop, width: barWidth }]} accessibilityRole="tablist">
        {tabs.map((tab, k) => (
          <Pressable
            key={k}
            style={styles.tab}
            onPress={() => select(k, true)}
            accessibilityRole="tab"
            accessibilityLabel={tab.title}
            accessibilityState={{ selected: k === index }}
          />
        ))}
      </View>

      {/* The dot flies over the page, so it is the top layer. */}
      <Animated.View pointerEvents="none" style={[styles.dot, { backgroundColor: bar }, dotStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  panel: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    top: 0,
    overflow: 'hidden',
  },
  page: {
    position: 'absolute',
    left: INSET,
    right: INSET,
    top: 0,
  },
  title: {
    color: '#ffffff',
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '700',
    marginBottom: 12,
    includeFontPadding: false,
  },
  person: {
    marginTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  avatar: {
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    backgroundColor: 'rgba(255,255,255,0.14)',
  },
  card: {
    marginTop: 12,
    padding: 20,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.14)',
  },
  line: {
    height: 10,
    borderRadius: 5,
    backgroundColor: 'rgba(255,255,255,0.4)',
  },
  long: {
    width: 140,
  },
  short: {
    width: 90,
    marginTop: 12,
  },
  bar: {
    position: 'absolute',
    left: SIDE,
    height: BAR_HEIGHT,
  },
  tabs: {
    position: 'absolute',
    left: SIDE,
    height: BAR_HEIGHT,
    flexDirection: 'row',
  },
  tab: {
    flex: 1,
  },
  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
  },
});
