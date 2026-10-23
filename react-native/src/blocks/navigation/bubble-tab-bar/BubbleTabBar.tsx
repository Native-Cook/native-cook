import { Canvas, Circle, Group, Path, Skia, type SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  type SharedValue,
  Easing,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export type BubbleTabIcon = 'profile' | 'saved' | 'add' | 'search' | 'more';

export type BubbleTab = {
  label: string;
  icon: BubbleTabIcon;
  /** The page's heading and the line under it. */
  title: string;
  caption: string;
};

type Props = {
  tabs?: BubbleTab[];
  initialIndex?: number;
  /** Hops through every tab by itself every LOOP seconds, then glides back to the first. */
  autoPlay?: boolean;
  onChange?: (index: number, tab: BubbleTab) => void;
};

const TABS: BubbleTab[] = [
  { label: 'Profile', icon: 'profile', title: 'Profile', caption: 'Your account and activity' },
  { label: 'Saved', icon: 'saved', title: 'Saved', caption: 'Things you kept for later' },
  { label: 'Add', icon: 'add', title: 'Create', caption: 'Start something new' },
  { label: 'Search', icon: 'search', title: 'Search', caption: 'Find people and posts' },
  { label: 'More', icon: 'more', title: 'More', caption: 'Settings, help and about' },
];

// Icons in a 24 × 24 box: stroked paths (M, L, H, V, C and Z only) and stroked circles.
const ICONS: Record<BubbleTabIcon, { d: string; circles: [number, number, number][] }> = {
  profile: { d: 'M4.5 20.5C4.5 16.6 7.9 14 12 14C16.1 14 19.5 16.6 19.5 20.5', circles: [[12, 8, 4]] },
  saved: { d: 'M6.5 4.5C6.5 3.95 6.95 3.5 7.5 3.5H16.5C17.05 3.5 17.5 3.95 17.5 4.5V20.5L12 16.5L6.5 20.5Z', circles: [] },
  add: { d: 'M12 4.5V19.5M4.5 12H19.5', circles: [] },
  search: { d: 'M15.4 15.4L20 20', circles: [[10.8, 10.8, 6.3]] },
  more: { d: 'M4.5 7H19.5M4.5 12H19.5M4.5 17H19.5', circles: [] },
};

const SCREEN = '#202020';
const BAR = '#ffffff';
const INK = '#111111';
const PAGE_INK = '#ffffff';
const PAGE_MUTE = '#8a8a8a';
const ROW = '#2a2a2a';
const ROW_MARK = '#353535';

// The bar, in points. Its top edge is y 0; the bubble rises above it.
const BAR_H = 60;
const SIDE = 22;
const CORNER = 16;
const ICON = 24;
const ICON_Y = 22;
const LABEL_Y = 42;
const BUBBLE_R = 28;
// The bite the bubble sits in: 4 points wider than the bubble, with rounded shoulders.
const NOTCH_R = 32;
const FILLET = 8;
// Where the bubble's and the bite's centres rest, below the bar's top edge.
const REST_Y = 6;
// While the bubble travels the bite closes up (its centre rises this much) and the bubble lifts.
const FLAT_LIFT = 32;
const HOP_RISE = 4;
// How far from a tab the bubble must be before its icon starts to leave.
const REACH = 0.6;
// Room above the bar for the bubble.
const ROOM = 40;
// Polyline steps for each arc of the outline.
const CORNER_STEPS = 8;
const FILLET_STEPS = 8;
const NOTCH_STEPS = 28;

// Motion. A hop is a spring of response 0.45 s at damping ratio 0.7; a hop of more than one tab
// uses response 0.7 s. The bite follows the bubble 40 ms late, and closes over HOP_CLOSE.
const HOP = { mass: 1, stiffness: 194.96, damping: 19.55 };
const LONG_HOP = { mass: 1, stiffness: 80.57, damping: 12.57 };
const NOTCH_DELAY = 0.04;
const HOP_CLOSE = 0.2;
const PAGE_FADE = 0.25;

// The autoPlay loop: a hop every 1.6 s through the tabs, then one long glide back to the first.
const LOOP = 9;
const SCRIPT: [number, number][] = [
  [0.9, 1],
  [2.5, 2],
  [4.1, 3],
  [5.7, 4],
  [7.3, 0],
];
const USER_HOLD = 3;

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function tabX(i: number, count: number, width: number) {
  'worklet';
  return SIDE + ((width - SIDE * 2) * (i + 0.5)) / count;
}

function arc(path: ReturnType<typeof Skia.PathBuilder.Make>, cx: number, cy: number, r: number, from: number, to: number, steps: number) {
  'worklet';
  for (let k = 0; k <= steps; k++) {
    const a = from + ((to - from) * k) / steps;
    path.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
  }
}

/** The bar with a round bite centred at (cx, top + cy), its shoulders rounded by FILLET. */
function outline(width: number, height: number, top: number, cx: number, cy: number): SkPath {
  'worklet';
  const path = Skia.PathBuilder.Make();
  const d = Math.sqrt((NOTCH_R + FILLET) ** 2 - (FILLET - cy) ** 2);
  path.moveTo(0, top + height);
  path.lineTo(0, top + CORNER);
  arc(path, CORNER, top + CORNER, CORNER, Math.PI, 1.5 * Math.PI, CORNER_STEPS);
  arc(path, cx - d, top + FILLET, FILLET, -0.5 * Math.PI, Math.atan2(cy - FILLET, d), FILLET_STEPS);
  arc(path, cx, top + cy, NOTCH_R, Math.atan2(FILLET - cy, -d), Math.atan2(FILLET - cy, d), NOTCH_STEPS);
  arc(path, cx + d, top + FILLET, FILLET, Math.atan2(cy - FILLET, -d), -0.5 * Math.PI, FILLET_STEPS);
  arc(path, width - CORNER, top + CORNER, CORNER, 1.5 * Math.PI, 2 * Math.PI, CORNER_STEPS);
  path.lineTo(width, top + height);
  path.close();
  return path.build();
}

const PATHS = {
  profile: Skia.Path.MakeFromSVGString(ICONS.profile.d)!,
  saved: Skia.Path.MakeFromSVGString(ICONS.saved.d)!,
  add: Skia.Path.MakeFromSVGString(ICONS.add.d)!,
  search: Skia.Path.MakeFromSVGString(ICONS.search.d)!,
  more: Skia.Path.MakeFromSVGString(ICONS.more.d)!,
};

type Motion = { bubbleX: SharedValue<number>; lift: SharedValue<number> };

/** A tab's icon: in the bar, or in the bubble, each fading by how close the bubble is. */
function TabMark({
  icon,
  index,
  count,
  width,
  inBubble,
  motion,
}: {
  icon: BubbleTabIcon;
  index: number;
  count: number;
  width: number;
  inBubble: boolean;
  motion: Motion;
}) {
  const x = tabX(index, count, width);
  const pitch = (width - SIDE * 2) / count;
  // 1 when the bubble is over this tab, 0 when it is REACH tabs away.
  const near = useDerivedValue(() => clamp01(1 - Math.abs(motion.bubbleX.get() - x) / (pitch * REACH)));
  const opacity = useDerivedValue(() => (inBubble ? near.get() : 1 - near.get()));
  const transform = useDerivedValue(() => {
    const n = near.get();
    const scale = inBubble ? 1.1 - 0.1 * n : 0.8 + 0.2 * (1 - n);
    const cx = inBubble ? motion.bubbleX.get() : x;
    const cy = ROOM + (inBubble ? motion.lift.get() : ICON_Y);
    return [{ translateX: cx }, { translateY: cy }, { scale }, { translateX: -ICON / 2 }, { translateY: -ICON / 2 }];
  });
  const width2 = inBubble ? 2 : 1.8;
  return (
    <Group transform={transform} opacity={opacity}>
      <Path path={PATHS[icon]} style="stroke" strokeWidth={width2} strokeCap="round" strokeJoin="round" color={INK} />
      {ICONS[icon].circles.map(([cx, cy, r]) => (
        <Circle key={`${cx}-${cy}`} cx={cx} cy={cy} r={r} style="stroke" strokeWidth={width2} color={INK} />
      ))}
    </Group>
  );
}

/**
 * A white tab bar on a dark screen whose active tab rises out of the bar as a round bubble sitting
 * in a bite cut from the bar's edge. On a change the bite closes up, the bubble glides to the new
 * tab with a little overshoot and the bite opens under it again, 40 ms behind, like a liquid.
 */
export function BubbleTabBar({ tabs = TABS, initialIndex = 0, autoPlay = false, onChange }: Props) {
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [index, setIndex] = useState(initialIndex);
  const [touches, setTouches] = useState(0);

  const width = size.width;
  const count = tabs.length;
  const pitch = (width - SIDE * 2) / count;
  const barHeight = BAR_H + insets.bottom;

  const bubbleX = useSharedValue(tabX(initialIndex, count, width));
  const notchX = useSharedValue(tabX(initialIndex, count, width));
  const targetX = useSharedValue(tabX(initialIndex, count, width));
  // 0 → 1 over HOP_CLOSE at the start of each hop: how far the bite may have closed.
  const hop = useSharedValue(1);

  // How much the bite is closed: as soon as a hop starts, and opening again as the bubble arrives.
  const flat = useDerivedValue(() => Math.min(hop.get(), clamp01(Math.abs(bubbleX.get() - targetX.get()) / (pitch * REACH))));
  const lift = useDerivedValue(() => REST_Y - HOP_RISE * flat.get());
  const bar = useDerivedValue(() => {
    const first = tabX(0, count, width);
    const last = tabX(count - 1, count, width);
    // The bite stays clear of the bar's rounded corners even when the bubble overshoots.
    const cx = Math.min(last + 1.5, Math.max(first - 1.5, notchX.get()));
    return outline(width, barHeight, ROOM, cx, REST_Y - FLAT_LIFT * flat.get());
  });
  const bubbleCx = useDerivedValue(() => bubbleX.get());
  const bubbleCy = useDerivedValue(() => ROOM + lift.get());

  // A new width moves the bubble to its tab at once.
  useEffect(() => {
    const x = tabX(index, count, width);
    if (Math.abs(targetX.get() - x) > 0.5 && Math.abs(bubbleX.get() - targetX.get()) < 0.5) {
      targetX.set(x);
      bubbleX.set(x);
      notchX.set(x);
    }
  }, [width, count, index, targetX, bubbleX, notchX]);

  const select = (next: number, auto: boolean) => {
    if (next === index && Math.abs(bubbleX.get() - tabX(next, count, width)) < 0.5) return;
    const x = tabX(next, count, width);
    const spring = Math.abs(next - index) > 1 ? LONG_HOP : HOP;
    targetX.set(x);
    hop.set(0);
    hop.set(withTiming(1, { duration: HOP_CLOSE * 1000, easing: Easing.out(Easing.quad) }));
    bubbleX.set(withSpring(x, spring));
    notchX.set(withDelay(NOTCH_DELAY * 1000, withSpring(x, spring)));
    setIndex(next);
    if (!auto && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onChange?.(next, tabs[next]);
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
      for (const [time, tab] of SCRIPT) at(origin + time * 1000, () => latest.current.select(tab % count, true));
      at(origin + LOOP * 1000, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      at(Date.now() + USER_HOLD * 1000, () => {
        if (latest.current.index === 0) return cycle(Date.now());
        // Back to the first tab the way the loop ends, then the loop starts over from there.
        latest.current.select(0, true);
        cycle(Date.now() + (LOOP - SCRIPT[SCRIPT.length - 1][0]) * 1000);
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, count]);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const motion: Motion = { bubbleX, lift };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />

      <View style={[styles.page, { paddingTop: insets.top + 24 }]}>
        <View style={styles.heading}>
          {tabs.map((tab, i) => (
            <Animated.View
              key={tab.label}
              pointerEvents="none"
              style={[
                styles.headingSlot,
                { opacity: i === index ? 1 : 0, transitionProperty: 'opacity', transitionDuration: PAGE_FADE * 1000 },
              ]}
            >
              <Text style={styles.title} numberOfLines={1}>
                {tab.title}
              </Text>
              <Text style={styles.caption} numberOfLines={1}>
                {tab.caption}
              </Text>
            </Animated.View>
          ))}
        </View>
        {[0.72, 0.56, 0.64].map((share) => (
          <View key={share} style={styles.row}>
            <View style={styles.rowMark} />
            <View style={styles.rowLines}>
              <View style={[styles.rowLine, { width: `${share * 100}%` }]} />
              <View style={[styles.rowLine, styles.rowLineShort, { width: `${share * 60}%` }]} />
            </View>
          </View>
        ))}
      </View>

      <View style={[styles.barArea, { height: ROOM + barHeight }]}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Path path={bar} color={BAR} />
          {tabs.map((tab, i) => (
            <TabMark key={tab.label} icon={tab.icon} index={i} count={count} width={width} inBubble={false} motion={motion} />
          ))}
          <Circle cx={bubbleCx} cy={bubbleCy} r={BUBBLE_R} color={BAR} />
          {tabs.map((tab, i) => (
            <TabMark key={tab.label} icon={tab.icon} index={i} count={count} width={width} inBubble motion={motion} />
          ))}
        </Canvas>
        {tabs.map((tab, i) => (
          <Pressable
            key={tab.label}
            accessibilityRole="tab"
            accessibilityLabel={tab.label}
            accessibilityState={{ selected: i === index }}
            onPress={() => {
              setTouches((n) => n + 1);
              select(i, false);
            }}
            style={[styles.tab, { left: tabX(i, count, width) - pitch / 2, width: pitch, top: ROOM - 24, height: BAR_H + 24 }]}
          >
            <Text style={[styles.label, { top: 24 + LABEL_Y }]} numberOfLines={1}>
              {tab.label}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SCREEN,
  },
  page: {
    flex: 1,
    paddingHorizontal: 24,
  },
  heading: {
    height: 76,
    marginBottom: 20,
  },
  headingSlot: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  title: {
    color: PAGE_INK,
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '700',
    letterSpacing: -0.6,
    includeFontPadding: false,
  },
  caption: {
    marginTop: 6,
    color: PAGE_MUTE,
    fontSize: 15,
    lineHeight: 20,
    includeFontPadding: false,
  },
  row: {
    height: 72,
    marginBottom: 12,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: ROW,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    gap: 14,
  },
  rowMark: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: ROW_MARK,
  },
  rowLines: {
    flex: 1,
    gap: 8,
  },
  rowLine: {
    height: 10,
    borderRadius: 5,
    backgroundColor: ROW_MARK,
  },
  rowLineShort: {
    height: 8,
  },
  barArea: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  tab: {
    position: 'absolute',
    alignItems: 'center',
  },
  label: {
    position: 'absolute',
    color: INK,
    fontSize: 11,
    lineHeight: 13,
    fontWeight: '500',
    includeFontPadding: false,
  },
});
