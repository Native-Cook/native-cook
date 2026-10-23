import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PARALLAX, ShopPage } from './ShopPages';

type Props = {
  /** The tabs' names, in the order of their pages. */
  tabs?: string[];
  /** The tab the screen opens on. */
  initialTab?: number;
  /** The names on the bottom bar; the first one is the current one. */
  barItems?: string[];
  /** Pages through the tabs by itself, back at the first page every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (index: number) => void;
};

const TABS = ['NEW IN', 'BROWSE', 'ARCHIVE REVIVAL', 'TRAIL RUNNER'];
const BAR_ITEMS = ['HOME', 'STYLE', 'SEARCH', 'SELL', 'PROFILE'];

const INK = '#111111';
const PAPER = '#FFFFFF';
const BAR_FILL = '#000000';

// The tabs sit on the rim of a wheel whose hub is RADIUS points below their line; neighbours are
// STEP degrees apart, which is PITCH points along the rim.
const RADIUS = 560;
const STEP = 13;
const STEP_RADIANS = (STEP * Math.PI) / 180;
const PITCH = RADIUS * STEP_RADIANS;
// The middle of the tabs' line and the top of the pages, below the top safe-area inset.
const TABS_Y = 41;
const PAGES_Y = 61;
const TAB_WIDTH = 150;
const TAB_HEIGHT = 32;
// A tab one step from the centre is this faint, and so is every tab beyond.
const DIM = 0.55;
// The page that leaves moves at this part of the finger's speed, under the one that comes in.
const OUT = 0.85;
// Past the first and the last page a drag moves the pages by this part of the finger's way.
const RUBBER = 0.25;
// The settle: about 0.4 s, passing the page by a few points.
const SPRING = { mass: 1, stiffness: 170, damping: 21 };
// A release goes to the page the drag would reach in this many seconds at its speed.
const PROJECT = 0.18;
// A press within this many milliseconds of a drag belongs to the drag.
const DRAG_GRACE = 150;
// Reduced motion: the pages cross-fade over this long.
const FADE = 250;
const BAR = 58;

const USER_HOLD = 3;
const LOOP = 9;
// The loop that plays by itself: the page that is due, in seconds from the start of a loop.
const SCRIPT = [
  { at: 1.0, page: 2 },
  { at: 3.0, page: 3 },
  { at: 5.0, page: 2 },
  { at: 5.5, page: 1 },
];

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** A tab on the wheel's rim: upright at the top centre, tilted and lower as it turns away. */
function Tab({
  label,
  index,
  position,
  still,
  center,
  top,
  selected,
  onPress,
}: {
  label: string;
  index: number;
  position: SharedValue<number>;
  still: boolean;
  center: number;
  top: number;
  selected: boolean;
  onPress: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const away = index - position.get();
    const opacity = 1 - (1 - DIM) * Math.min(1, Math.abs(away));
    // Reduced motion: the tabs slide along a straight line.
    if (still) return { opacity, transform: [{ translateX: away * PITCH }] };
    const turn = away * STEP_RADIANS;
    return {
      opacity,
      transform: [{ translateX: RADIUS * Math.sin(turn) }, { translateY: RADIUS * (1 - Math.cos(turn)) }, { rotate: `${turn}rad` }],
    };
  });
  return (
    <Animated.View style={[styles.tab, { left: center - TAB_WIDTH / 2, top: top - TAB_HEIGHT / 2 }, style]}>
      <Pressable style={styles.tabHit} onPress={onPress} accessibilityRole="tab" accessibilityState={{ selected }}>
        <Text style={styles.caps} numberOfLines={1}>
          {label}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

/** One page of the pager. The page that comes in lies over the one that leaves. */
function Page({
  index,
  position,
  still,
  width,
  height,
  hidden,
}: {
  index: number;
  position: SharedValue<number>;
  still: boolean;
  width: number;
  height: number;
  hidden: boolean;
}) {
  const style = useAnimatedStyle(() => {
    const away = index - position.get();
    if (still) return { opacity: 1 - Math.min(1, Math.abs(away)), transform: [{ translateX: 0 }] };
    return { opacity: 1, transform: [{ translateX: width * (away >= 0 ? away : OUT * away) }] };
  });
  // The picture trails the page while it comes in, and has caught up when the page is in place.
  const drift = useAnimatedStyle(() => ({
    transform: [{ translateX: still ? 0 : PARALLAX * Math.min(1, Math.max(0, index - position.get())) }],
  }));
  return (
    <Animated.View
      style={[styles.page, style]}
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}
    >
      <ShopPage index={index} width={width} height={height} drift={drift} />
    </Animated.View>
  );
}

/**
 * Top tabs set on an arc, like the rim of a large wheel whose hub is far below the screen. Paging
 * turns the wheel: the next tab climbs to the top centre and straightens while the page comes in
 * over the one that leaves, its picture trailing behind its text.
 */
export function OrbitingTopTabs({ tabs = TABS, initialTab = 1, barItems = BAR_ITEMS, autoPlay = false, onSelect }: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [current, setCurrent] = useState(initialTab);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The page in the middle of the screen, as a number: 1.5 is half way between two pages.
  const position = useSharedValue(initialTab);
  // Where a drag started, and how many points of it make one page: the screen's width on the
  // pages, the tabs' spacing on the wheel.
  const from = useSharedValue(initialTab);
  const perPage = useSharedValue(1);
  // Above 0 during a drag and for a moment after it.
  const dragged = useSharedValue(0);

  const last = tabs.length - 1;
  const pagesTop = insets.top + PAGES_Y;
  const barHeight = BAR + insets.bottom;
  const pageHeight = Math.max(0, size.height - pagesTop - barHeight);

  const turnTo = (index: number, velocity: number) => {
    position.set(
      reducedMotion
        ? withTiming(index, { duration: FADE, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never })
        : withSpring(index, { ...SPRING, velocity }),
    );
    if (index !== current) {
      setCurrent(index);
      onSelect?.(index);
    }
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const released = (index: number, velocity: number) => {
    if (index !== current) tick();
    turnTo(index, velocity);
  };

  const press = (index: number) => {
    // A tab rides the wheel under the finger, so its press at the end of a drag is not a tap.
    if (dragged.get() > 0) return;
    touched();
    if (index !== current) tick();
    turnTo(index, 0);
  };

  // The latest of everything the loop's timers call.
  const latest = useRef({ turnTo });
  useEffect(() => {
    latest.current = { turnTo };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      // Each step turns to the page that is due at its time, whatever the steps before it did.
      for (const step of SCRIPT) after(origin + step.at * 1000 - Date.now(), () => latest.current.turnTo(step.page, 0));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // Back to rest; the pages have settled by the time the loop first turns them.
        latest.current.turnTo(initialTab, 0);
        cycle(Date.now());
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, initialTab]);

  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-14, 14])
    .onBegin((event) => {
      perPage.set(event.y < pagesTop ? PITCH : size.width);
    })
    .onStart(() => {
      cancelAnimation(position);
      dragged.set(1);
      from.set(position.get());
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      const to = from.get() - event.translationX / perPage.get();
      position.set(to < 0 ? to * RUBBER : to > last ? last + (to - last) * RUBBER : to);
    })
    .onEnd((event) => {
      // Pages a second. A drag of the pages goes one page at most; the wheel may spin further.
      const velocity = -event.velocityX / perPage.get();
      const start = Math.round(from.get());
      const reach = perPage.get() === PITCH ? last : 1;
      const aim = Math.round(position.get() + velocity * PROJECT);
      const to = Math.min(Math.min(last, start + reach), Math.max(Math.max(0, start - reach), aim));
      scheduleOnRN(released, to, velocity);
    })
    .onFinalize(() => {
      dragged.set(withDelay(DRAG_GRACE, withTiming(0, { duration: 0, reduceMotion: ReduceMotion.Never })));
    });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen} onLayout={onLayout}>
        <StatusBar style="dark" />

        <View style={[styles.pages, { top: pagesTop, bottom: barHeight }]}>
          {tabs.map((label, index) => (
            <Page key={label} index={index} position={position} still={reducedMotion} width={size.width} height={pageHeight} hidden={index !== current} />
          ))}
        </View>

        <View style={[styles.wheel, { height: pagesTop }]} accessibilityRole="tablist">
          {tabs.map((label, index) => (
            <Tab
              key={label}
              label={label}
              index={index}
              position={position}
              still={reducedMotion}
              center={size.width / 2}
              top={insets.top + TABS_Y}
              selected={index === current}
              onPress={() => press(index)}
            />
          ))}
        </View>

        <View style={[styles.bar, { height: barHeight, paddingBottom: insets.bottom }]}>
          {barItems.map((label, index) => (
            <Text key={label} style={[styles.barLabel, index === 0 && styles.barCurrent]} numberOfLines={1}>
              {label}
            </Text>
          ))}
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: PAPER,
  },
  pages: {
    position: 'absolute',
    left: 0,
    right: 0,
    overflow: 'hidden',
  },
  page: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  wheel: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    overflow: 'hidden',
    backgroundColor: PAPER,
  },
  tab: {
    position: 'absolute',
    width: TAB_WIDTH,
    height: TAB_HEIGHT,
  },
  tabHit: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  caps: {
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    letterSpacing: 1,
    includeFontPadding: false,
  },
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    backgroundColor: BAR_FILL,
  },
  barLabel: {
    flex: 1,
    textAlign: 'center',
    color: '#FFFFFF',
    fontSize: 7,
    lineHeight: 10,
    fontWeight: '600',
    letterSpacing: 0.8,
    includeFontPadding: false,
  },
  barCurrent: {
    textDecorationLine: 'underline',
  },
});
