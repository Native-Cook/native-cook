import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Canvas, Circle, Group, LinearGradient as SkiaLinearGradient, Path, RadialGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { type ComponentProps, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

type IconName = ComponentProps<typeof MaterialCommunityIcons>['name'];

/** One action of the menu. */
type Item = {
  /** MaterialCommunityIcons glyph drawn on the chip. */
  icon: IconName;
  /** What a screen reader calls it. */
  label: string;
};

/** One of the two tabs beside the notch. */
type Tab = {
  /** Glyph while the tab is not selected, and while it is. */
  icon: IconName;
  selectedIcon: IconName;
  label: string;
};

type Props = {
  /** The actions, left to right along the arc. Three sit best. */
  items?: Item[];
  /** The tab left of the notch and the one right of it. */
  tabs?: Tab[];
  /** The tab selected at first. */
  initialTab?: number;
  /** Opens the menu, picks an action and closes it by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the action that was picked. */
  onSelect?: (index: number) => void;
  /** Called when the menu opens or closes. */
  onOpenChange?: (open: boolean) => void;
  /** Called with the index of the tab that was selected. */
  onTabChange?: (index: number) => void;
};

const WIDTH = 340;
const HEIGHT = 204;

// The bar's top edge, which is also the height of the button's centre.
const BAR_TOP = 124;
const BAR_HEIGHT = 80;
const TOP_CORNER = 6;
const BOTTOM_CORNER = 26;
const NOTCH = 34;
const SHOULDER = 18;
const BUTTON = 58;
const PLUS = 16;
const PLUS_LINE = 1.75;
const TAB_INSET = 90;
const TAB_WIDTH = 110;
const GLOW = 300;

// The chips: on an arc `SPREAD` degrees to each side of 12 o'clock, the middle ones further out.
const CHIP = 46;
const GLYPH = 21;
const SPREAD = 51;
const SIDE_REACH = 70;
const TOP_REACH = 84;
// Where a folded chip waits, in degrees clockwise from 12 o'clock: left of the button, a little low.
const FOLD = -115;
const FOLD_REACH = 0.2;
const FOLD_SCALE = 0.6;

const STAGGER = 0.05;
// A picked chip swells, and the menu closes this long after the tap.
const CLOSE_AFTER = 0.45;
const TAB_TIME = 0.2;
const FADE = 0.15;

// autoPlay (seconds): the button is tapped at `OPEN_AT`, a chip at `PICK_AT`, twice per loop.
const OPEN_AT = [1.0, 4.2];
const PICK_AT = [2.45, 5.45];
const LOOP = 7;
// After a touch the loop waits this long, closes the menu, and starts over once it has settled.
const RESUME_AFTER = 2;
const SETTLE = 0.6;

const OPEN_SPRING = { mass: 1, stiffness: 260, damping: 23, reduceMotion: ReduceMotion.Never };
const CLOSE_SPRING = { mass: 1, stiffness: 300, damping: 34, reduceMotion: ReduceMotion.Never };

const LILAC = '#c77dff';
const GREY = '#9a9a9a';

const ITEMS: Item[] = [
  { icon: 'phone-in-talk', label: 'Call' },
  { icon: 'email-check', label: 'Message' },
  { icon: 'microphone', label: 'Voice note' },
];

const TABS: Tab[] = [
  { icon: 'home-outline', selectedIcon: 'home', label: 'Home' },
  { icon: 'account-outline', selectedIcon: 'account', label: 'Profile' },
];

// The canvas is the glow's square, centred on the button; the bar is drawn in the same coordinates.
const BAR_LEFT = GLOW - WIDTH / 2;
const BAR_RIGHT = GLOW + WIDTH / 2;
const BAR_BOTTOM = GLOW + BAR_HEIGHT;
// The shoulder's circle touches the top edge and the cradle's circle: its centre is `REACH` out, and
// the two circles meet `JOIN` radians below the top edge.
const REACH = Math.sqrt(NOTCH * NOTCH + 2 * NOTCH * SHOULDER);
const JOIN = Math.asin(SHOULDER / (NOTCH + SHOULDER));
const MEET_X = NOTCH * Math.cos(JOIN);
const MEET_Y = NOTCH * Math.sin(JOIN);

/** The bar: square shoulders at the top, round corners at the bottom, and a cradle in the top edge. */
const BAR =
  `M ${BAR_LEFT} ${GLOW + TOP_CORNER} A ${TOP_CORNER} ${TOP_CORNER} 0 0 1 ${BAR_LEFT + TOP_CORNER} ${GLOW} ` +
  `L ${GLOW - REACH} ${GLOW} A ${SHOULDER} ${SHOULDER} 0 0 1 ${GLOW - MEET_X} ${GLOW + MEET_Y} ` +
  `A ${NOTCH} ${NOTCH} 0 0 0 ${GLOW + MEET_X} ${GLOW + MEET_Y} ` +
  `A ${SHOULDER} ${SHOULDER} 0 0 1 ${GLOW + REACH} ${GLOW} ` +
  `L ${BAR_RIGHT - TOP_CORNER} ${GLOW} A ${TOP_CORNER} ${TOP_CORNER} 0 0 1 ${BAR_RIGHT} ${GLOW + TOP_CORNER} ` +
  `L ${BAR_RIGHT} ${BAR_BOTTOM - BOTTOM_CORNER} A ${BOTTOM_CORNER} ${BOTTOM_CORNER} 0 0 1 ${BAR_RIGHT - BOTTOM_CORNER} ${BAR_BOTTOM} ` +
  `L ${BAR_LEFT + BOTTOM_CORNER} ${BAR_BOTTOM} A ${BOTTOM_CORNER} ${BOTTOM_CORNER} 0 0 1 ${BAR_LEFT} ${BAR_BOTTOM - BOTTOM_CORNER} Z`;

// The light the open menu throws on the bottom of the cradle: an arc just inside it.
const RIM_RADIUS = NOTCH - 1.5;
const RIM_FROM = 0.06 * 2 * Math.PI;
const RIM =
  `M ${GLOW + RIM_RADIUS * Math.cos(RIM_FROM)} ${GLOW + RIM_RADIUS * Math.sin(RIM_FROM)} ` +
  `A ${RIM_RADIUS} ${RIM_RADIUS} 0 0 1 ${GLOW - RIM_RADIUS * Math.cos(RIM_FROM)} ${GLOW + RIM_RADIUS * Math.sin(RIM_FROM)}`;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/**
 * Where chip `index` of `count` is at `progress` (0 folded behind the button … 1 on the arc; a spring
 * may carry it a little past either end), measured from the button's centre: it swings clockwise about
 * the button while moving outward and growing, and its glyph turns with it. `turn` is in degrees.
 */
function place(index: number, count: number, progress: number) {
  'worklet';
  const along = count > 1 ? (index / (count - 1)) * 2 - 1 : 0;
  const final = along * SPREAD;
  const reach = mix(TOP_REACH, SIDE_REACH, Math.abs(along));
  const angle = mix(FOLD, final, progress);
  const distance = reach * mix(FOLD_REACH, 1, progress);
  const radians = (angle * Math.PI) / 180;
  return {
    x: distance * Math.sin(radians),
    y: -distance * Math.cos(radians),
    scale: Math.max(0, mix(FOLD_SCALE, 1, progress)),
    turn: angle - final,
  };
}

/** Up in 80 ms, a 40 ms hold, back in 180 ms: a tap. */
function pulse() {
  const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
  return withSequence(withTiming(1, { duration: 80, ...linear }), withDelay(40, withTiming(0, { duration: 180, ...linear })));
}

/** The chip that was picked last; `count` tells one pick of the same chip from the next. */
type Pick = { index: number; count: number };

type ChipProps = {
  index: number;
  count: number;
  item: Item;
  open: boolean;
  picked: Pick;
  still: boolean;
  onPress: () => void;
};

/** One action: folded behind the button until the menu opens. */
function Chip({ index, count, item, open, picked, still, onPress }: ChipProps) {
  // 0 behind the button … 1 in its place on the arc.
  const progress = useSharedValue(0);
  const swell = useSharedValue(0);

  useEffect(() => {
    if (still) {
      progress.set(withTiming(open ? 1 : 0, { duration: FADE * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    } else {
      // The last chip leads; each one before it follows `STAGGER` later.
      progress.set(withDelay((count - 1 - index) * STAGGER * 1000, withSpring(open ? 1 : 0, open ? OPEN_SPRING : CLOSE_SPRING)));
    }
  }, [open, still, index, count, progress]);

  useEffect(() => {
    if (picked.index === index && picked.count > 0) swell.set(pulse());
  }, [picked, index, swell]);

  const style = useAnimatedStyle(() => {
    // Under reduced motion a chip does not travel: it fades in where it belongs.
    const at = place(index, count, still ? 1 : progress.get());
    return {
      opacity: clamp01(progress.get() / 0.3),
      transform: [
        { translateX: at.x },
        { translateY: at.y },
        { scale: at.scale * (1 + 0.12 * swell.get()) },
        { rotate: `${at.turn}deg` },
      ],
    };
  });

  return (
    <Animated.View style={[styles.chip, style]} pointerEvents={open ? 'auto' : 'none'}>
      <Pressable
        style={styles.chipFace}
        accessibilityRole="button"
        accessibilityLabel={item.label}
        accessibilityElementsHidden={!open}
        onPress={onPress}
      >
        <LinearGradient style={StyleSheet.absoluteFill} colors={['#e2a8ff', '#b266f0']} />
        <MaterialCommunityIcons name={item.icon} size={GLYPH} color="#ffffff" />
      </Pressable>
    </Animated.View>
  );
}

type TabProps = {
  tab: Tab;
  /** 0 the tab left of the notch, 1 the one right of it. */
  side: number;
  selected: boolean;
  /** 0 the left tab is lit … 1 the right one. */
  lit: SharedValue<number>;
  onPress: () => void;
};

/** A tab: its grey and its lilac look lie on top of each other and cross-fade. */
function TabButton({ tab, side, selected, lit, onPress }: TabProps) {
  const off = useAnimatedStyle(() => ({ opacity: side === 0 ? lit.get() : 1 - lit.get() }));
  const on = useAnimatedStyle(() => ({ opacity: side === 0 ? 1 - lit.get() : lit.get() }));

  return (
    <Pressable
      style={[styles.tab, { left: (side === 0 ? TAB_INSET : WIDTH - TAB_INSET) - TAB_WIDTH / 2 }]}
      accessibilityRole="tab"
      accessibilityState={{ selected }}
      accessibilityLabel={tab.label}
      onPress={onPress}
    >
      <Animated.View style={[styles.tabLook, off]}>
        <MaterialCommunityIcons name={tab.icon} size={24} color={GREY} />
        <Text style={[styles.tabLabel, { color: GREY }]} numberOfLines={1}>
          {tab.label}
        </Text>
      </Animated.View>
      <Animated.View style={[styles.tabLook, on]}>
        <MaterialCommunityIcons name={tab.selectedIcon} size={24} color={LILAC} />
        <Text style={[styles.tabLabel, { color: LILAC }]} numberOfLines={1}>
          {tab.label}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

/**
 * A tab bar whose top edge dips into a notch that cradles a round button. Tapping the button turns
 * its plus into a cross and fans the actions out of it: the chips leave from behind the button on
 * the left and sweep clockwise onto an arc above it, the furthest one leading. Tapping a chip picks
 * it and folds the fan away; tapping the cross folds it without a pick.
 */
export function NotchRadialMenu({ items = ITEMS, tabs = TABS, initialTab = 0, autoPlay = false, onSelect, onOpenChange, onTabChange }: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still && items.length > 0;
  const firstTab = Math.min(Math.max(initialTab, 0), 1);

  // The button: 0 a plus on violet … 1 a cross on black.
  const openness = useSharedValue(0);
  const press = useSharedValue(0);
  const lit = useSharedValue(firstTab);

  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState(firstTab);
  const [picked, setPicked] = useState<Pick>({ index: -1, count: 0 });
  // Counts taps: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  // `closing`: the close that follows a pick; opening or closing by hand drops it.
  const state = useRef({ open: false, tab: firstTab, closing: undefined as ReturnType<typeof setTimeout> | undefined });

  /** Fans the chips out or folds them away. */
  const show = (next: boolean) => {
    if (state.current.open === next) return;
    state.current.open = next;
    clearTimeout(state.current.closing);
    setOpen(next);
    if (still) openness.set(next ? 1 : 0);
    else openness.set(withSpring(next ? 1 : 0, next ? OPEN_SPRING : CLOSE_SPRING));
    onOpenChange?.(next);
  };

  const toggle = () => {
    press.set(pulse());
    show(!state.current.open);
  };

  /** Picks action `index`: its chip swells for a moment, then the menu closes. */
  const select = (index: number) => {
    if (!state.current.open || index < 0 || index >= items.length) return;
    setPicked((last) => ({ index, count: last.count + 1 }));
    clearTimeout(state.current.closing);
    state.current.closing = setTimeout(() => steps.current.show(false), CLOSE_AFTER * 1000);
    onSelect?.(index);
  };

  const pickTab = (index: number) => {
    if (index === state.current.tab) return;
    state.current.tab = index;
    setTab(index);
    lit.set(withTiming(index, { duration: still ? 0 : TAB_TIME * 1000, easing: Easing.inOut(Easing.quad), reduceMotion: ReduceMotion.Never }));
    onTabChange?.(index);
  };

  const touch = () => {
    setTouches((count) => count + 1);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ show, toggle, select, pickTab });
  useEffect(() => {
    steps.current = { show, toggle, select, pickTab };
  });

  useEffect(() => {
    const waiting = state.current;
    return () => clearTimeout(waiting.closing);
  }, []);

  // The script a finger would follow: open, pick the middle action, open again, pick the last one.
  const count = items.length;
  useEffect(() => {
    if (!auto) return;
    const picks = [Math.floor(count / 2), count - 1];
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const round = Math.floor(n / 2) % 2;
      const tap = n % 2 === 0;
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / 4) * LOOP + (tap ? OPEN_AT[round] : PICK_AT[round])) * 1000;
      timer = setTimeout(() => {
        if (tap) steps.current.toggle();
        else steps.current.select(picks[round]);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the closed menu and the first tab, and start over.
      timer = setTimeout(() => {
        steps.current.show(false);
        steps.current.pickTab(firstTab);
        run(Date.now() + SETTLE * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, count, firstTab]);

  const rim = useDerivedValue(() => 0.55 * clamp01(openness.get()));
  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.06 * press.get() }] }));
  const darkStyle = useAnimatedStyle(() => ({ opacity: clamp01(openness.get()) }));
  const plusStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${45 * openness.get()}deg` }] }));

  return (
    <View style={styles.root}>
      <Canvas style={styles.canvas} pointerEvents="none">
        <Circle cx={GLOW} cy={GLOW} r={GLOW}>
          <RadialGradient c={vec(GLOW, GLOW)} r={GLOW} colors={['#3d2a70', '#120b24']} />
        </Circle>
        <Path path={BAR} color="#050505" />
        <Group opacity={rim}>
          <Path path={RIM} style="stroke" strokeWidth={2} strokeCap="round">
            <SkiaLinearGradient start={vec(GLOW, GLOW - RIM_RADIUS)} end={vec(GLOW, GLOW + RIM_RADIUS)} colors={['#c77dff00', LILAC]} />
          </Path>
        </Group>
      </Canvas>

      {tabs.slice(0, 2).map((item, side) => (
        <TabButton
          key={side}
          tab={item}
          side={side}
          selected={tab === side}
          lit={lit}
          onPress={() => {
            touch();
            pickTab(side);
          }}
        />
      ))}

      {items.map((item, index) => (
        <Chip
          key={index}
          index={index}
          count={items.length}
          item={item}
          open={open}
          picked={picked}
          still={still}
          onPress={() => {
            touch();
            select(index);
          }}
        />
      ))}

      <Animated.View style={[styles.button, buttonStyle]}>
        <Pressable
          style={styles.buttonFace}
          accessibilityRole="button"
          accessibilityLabel={open ? 'Close menu' : 'Open menu'}
          onPress={() => {
            touch();
            toggle();
          }}
        >
          <LinearGradient style={StyleSheet.absoluteFill} colors={['#b06af5', '#7c3aed']} />
          <Animated.View style={[StyleSheet.absoluteFill, styles.dark, darkStyle]} />
          <Animated.View style={[styles.plus, plusStyle]}>
            <View style={[styles.plusLine, { width: PLUS, height: PLUS_LINE }]} />
            <View style={[styles.plusLine, { width: PLUS_LINE, height: PLUS }]} />
          </Animated.View>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  canvas: {
    position: 'absolute',
    left: WIDTH / 2 - GLOW,
    top: BAR_TOP - GLOW,
    width: GLOW * 2,
    height: GLOW * 2,
  },
  tab: {
    position: 'absolute',
    top: BAR_TOP,
    width: TAB_WIDTH,
    height: BAR_HEIGHT,
  },
  tabLook: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  tabLabel: {
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '500',
  },
  chip: {
    position: 'absolute',
    left: WIDTH / 2 - CHIP / 2,
    top: BAR_TOP - CHIP / 2,
    width: CHIP,
    height: CHIP,
  },
  chipFace: {
    flex: 1,
    borderRadius: CHIP / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  button: {
    position: 'absolute',
    left: WIDTH / 2 - BUTTON / 2,
    top: BAR_TOP - BUTTON / 2,
    width: BUTTON,
    height: BUTTON,
  },
  buttonFace: {
    flex: 1,
    borderRadius: BUTTON / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dark: {
    backgroundColor: '#0a0a0a',
  },
  plus: {
    width: PLUS,
    height: PLUS,
    alignItems: 'center',
    justifyContent: 'center',
  },
  plusLine: {
    position: 'absolute',
    borderRadius: PLUS_LINE / 2,
    backgroundColor: '#ffffff',
  },
});
