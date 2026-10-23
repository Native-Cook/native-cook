import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  LayoutAnimationConfig,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BAR,
  DOCK_IN,
  DOCK_OUT,
  type Frame,
  ICON,
  LABEL_X,
  LIST,
  MARK,
  MARK_Y,
  PITCH,
  RAIL,
  ROW_Y,
  clamp,
  easeInOut,
  easeOut,
  iconAt,
  markAt,
  mix,
  panelAt,
} from './geometry';

type IconName = keyof typeof Ionicons.glyphMap;

export type MorphItem = {
  id: string;
  label: string;
  /** The line icon, and the solid one that tints the lit icon of the bar. */
  icon: IconName;
  solid: IconName;
};

type Shape = 'list' | 'rail' | 'bar';

type Props = {
  /** The destinations, in the order of the list. */
  items?: MorphItem[];
  /** How many of them, from the top, the bar holds. */
  barItems?: number;
  /** The field's colour, which also marks the current destination. */
  color?: string;
  /** Goes through the three shapes by itself, back at the list every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (item: MorphItem) => void;
};

const ITEMS: MorphItem[] = [
  { id: 'home', label: 'Home', icon: 'home-outline', solid: 'home' },
  { id: 'explore', label: 'Explore', icon: 'compass-outline', solid: 'compass' },
  { id: 'notifications', label: 'Notifications', icon: 'notifications-outline', solid: 'notifications' },
  { id: 'messages', label: 'Messages', icon: 'mail-outline', solid: 'mail' },
  { id: 'bookmarks', label: 'Bookmarks', icon: 'bookmark-outline', solid: 'bookmark' },
  { id: 'lists', label: 'Lists', icon: 'reader-outline', solid: 'reader' },
  { id: 'profile', label: 'Profile', icon: 'person-outline', solid: 'person' },
];

const FIELD = '#2F6BFF';
const INK = '#111111';
const CAPSULE_FILL = '#E8F0FF';
const SCRIM = '#081030';
const SCRIM_ALPHA = 0.42;
const HALO = 'rgba(255,255,255,0.05)';
const CARD = 'rgba(255,255,255,0.14)';
const LINE = 'rgba(255,255,255,0.45)';
const DOT_FILL = 'rgba(17,17,17,0.12)';
const FILL_ALPHA = 0.2;

const HIT = 44;
const CAPSULE_X = 16;
const CAPSULE = 44;
const CAPSULE_PAD = 20;
const DOT = 10;
const DOT_DROP = 16;
const MARGIN = 20;
const TITLE_Y = 56;
const CARDS = [150, 96, 96];
// The two lines on a card, as parts of its width; a destination starts at its own place in the table.
const LINES = [
  [0.62, 0.4],
  [0.48, 0.3],
  [0.7, 0.52],
];
// The two soft circles behind the content: radii, and how far down the screen their centre is.
const HALOS = [260, 420];
const HALO_Y = 0.78;

// The list narrows to the rail on a spring.
const WIDE_SPRING = { mass: 1, stiffness: 200, damping: 24 };
// A label fades and slides over LABEL_IN seconds, LABEL_GAP after the one above it.
const LABEL_IN = 0.25;
const LABEL_GAP = 0.02;
const LABEL_SHIFT = 10;
// From the bar, the labels wait for the list to open; by hand the list collapses to the rail and,
// a moment later, on to the bar.
const LABELS_AFTER = 0.55;
const COLLAPSE_AFTER = 0.25;
// The highlight moves in SELECT seconds; its leading edge arrives after LEAD of that, so the
// capsule stretches on the way.
const SELECT = 0.25;
const LEAD = 0.64;
// A tap in the bar: the icon hops for HOP_UP of the time and lands with a squash.
const HOP_FOR = 0.4;
const HOP_UP = 0.7;
const HOP = 8;
const SQUASH = 0.1;
const SWAP = 250;
// Reduced motion: the shapes jump and the surface fades back in.
const FADE = 200;
// A swipe to the right that starts in this band brings the list out of the bar.
const EDGE = 40;

const USER_HOLD = 3;
const LOOP = 10;
// The loop that plays by itself: what is due when, in seconds from the start of a loop.
const SCRIPT: { at: number; shape?: Shape; current?: number }[] = [
  { at: 0.8, current: 1 },
  { at: 1.5, current: 2 },
  { at: 2.3, shape: 'rail' },
  { at: 3.2, shape: 'bar' },
  { at: 4.4, current: 3 },
  { at: 5.4, current: 4 },
  { at: 6.4, current: 0 },
  { at: 7.8, shape: 'list' },
];

const NEVER = ReduceMotion.Never;
const LINEAR = Easing.linear;
// A disc with its top right quarter cut out.
const MARK_PATH = `M${MARK / 2} ${MARK / 2}L${MARK / 2} 0A${MARK / 2} ${MARK / 2} 0 1 0 ${MARK} ${MARK / 2}Z`;

type Clocks = {
  /** 1 for the list, 0 for the rail and the bar. */
  wide: SharedValue<number>;
  /** 0 for the list and the rail, 1 for the bar: a clock, eased where it is read. */
  dock: SharedValue<number>;
  /** The highlight on its way from one row to another. */
  selFrom: SharedValue<number>;
  selTo: SharedValue<number>;
  selClock: SharedValue<number>;
  /** The icon that hops, and the clock of its hop. */
  hopIndex: SharedValue<number>;
  hop: SharedValue<number>;
  /** The whole surface, which fades back in after a jump under reduced motion. */
  fade: SharedValue<number>;
};

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function Mark({ color }: { color: string }) {
  return (
    <Svg width={MARK} height={MARK}>
      <Path d={MARK_PATH} fill={color} />
    </Svg>
  );
}

/**
 * One destination's icon. It is the same view in the three shapes: it rides the column, flies to
 * its place in the bar, turns to the field's colour while it is current and, in the bar, fills
 * from the bottom and hops when it is tapped.
 */
function NavIcon({
  item,
  index,
  docked,
  frame,
  clocks,
  color,
  enabled,
  selected,
  onPress,
}: {
  item: MorphItem;
  index: number;
  docked: number;
  frame: Frame;
  clocks: Clocks;
  color: string;
  enabled: boolean;
  selected: boolean;
  onPress: () => void;
}) {
  const { wide, dock, selFrom, selTo, selClock, hopIndex, hop, fade } = clocks;

  const tint = () => {
    'worklet';
    const c = selClock.get();
    return selTo.get() === index ? c : selFrom.get() === index ? 1 - c : 0;
  };
  const lift = () => {
    'worklet';
    const u = hopIndex.get() === index ? hop.get() : 1;
    return u < HOP_UP ? Math.sin((Math.PI * u) / HOP_UP) : 0;
  };

  const boxStyle = useAnimatedStyle(() => {
    const at = iconAt(index, docked, wide.get(), dock.get(), frame);
    return { opacity: at.alpha * fade.get(), transform: [{ translateX: at.x - HIT / 2 }, { translateY: at.y - HIT / 2 }] };
  });
  const glyphStyle = useAnimatedStyle(() => {
    const at = iconAt(index, docked, wide.get(), dock.get(), frame);
    const u = hopIndex.get() === index ? hop.get() : 1;
    const squash = u < HOP_UP ? 1 : 1 - SQUASH * Math.sin((Math.PI * (u - HOP_UP)) / (1 - HOP_UP));
    // The squash is against the ground: the icon's foot stays where it is.
    return { transform: [{ translateY: -HOP * lift() + ((1 - squash) * ICON) / 2 }, { scale: at.scale }, { scaleY: squash }] };
  });
  const dotStyle = useAnimatedStyle(() => {
    const at = iconAt(index, docked, wide.get(), dock.get(), frame);
    return { opacity: tint() * at.way, transform: [{ scale: 1 - 0.6 * lift() }] };
  });
  const inkStyle = useAnimatedStyle(() => ({ opacity: 1 - tint() }));
  const tintStyle = useAnimatedStyle(() => ({ opacity: tint() }));
  // The solid icon shows through a window that opens from the bottom; the icon inside moves
  // against the window so that it stays in place.
  const windowStyle = useAnimatedStyle(() => {
    const at = iconAt(index, docked, wide.get(), dock.get(), frame);
    return { transform: [{ translateY: ICON * (1 - tint() * at.way) }] };
  });
  const solidStyle = useAnimatedStyle(() => {
    const at = iconAt(index, docked, wide.get(), dock.get(), frame);
    return { transform: [{ translateY: -ICON * (1 - tint() * at.way) }] };
  });

  return (
    <Animated.View style={[styles.iconBox, boxStyle]} pointerEvents={enabled ? 'auto' : 'none'}>
      <Pressable
        style={styles.iconHit}
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={item.label}
        accessibilityState={{ selected }}
        accessibilityElementsHidden={!enabled}
        importantForAccessibility={enabled ? 'auto' : 'no-hide-descendants'}
      >
        <Animated.View style={[styles.dot, dotStyle]} />
        <Animated.View style={[styles.glyph, glyphStyle]}>
          <Animated.View style={[styles.layer, styles.window, windowStyle]}>
            <Animated.View style={[styles.layer, { opacity: FILL_ALPHA }, solidStyle]}>
              <Ionicons name={item.solid} size={ICON} color={color} />
            </Animated.View>
          </Animated.View>
          <Animated.View style={[styles.layer, inkStyle]}>
            <Ionicons name={item.icon} size={ICON} color={INK} />
          </Animated.View>
          <Animated.View style={[styles.layer, tintStyle]}>
            <Ionicons name={item.icon} size={ICON} color={color} />
          </Animated.View>
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

/** A row's name in the list. The names leave and come back one after another, from the top. */
function Label({
  text,
  index,
  count,
  top,
  clocks,
  labels,
  showing,
  still,
  color,
  onWidth,
}: {
  text: string;
  index: number;
  count: number;
  top: number;
  clocks: Clocks;
  labels: SharedValue<number>;
  showing: SharedValue<number>;
  still: boolean;
  color: string;
  onWidth: (index: number, width: number) => void;
}) {
  const { selFrom, selTo, selClock } = clocks;
  const boxStyle = useAnimatedStyle(() => {
    const total = LABEL_IN + (count - 1) * LABEL_GAP;
    const arrived = easeOut((labels.get() * total - index * LABEL_GAP) / LABEL_IN);
    const shown = showing.get() === 1 ? arrived : 1 - arrived;
    return { opacity: shown, transform: [{ translateX: still ? 0 : -LABEL_SHIFT * (1 - shown) }] };
  });
  const tintStyle = useAnimatedStyle(() => {
    const c = selClock.get();
    return { opacity: selTo.get() === index ? c : selFrom.get() === index ? 1 - c : 0 };
  });
  return (
    <Animated.View style={[styles.label, { top }, boxStyle]}>
      <Text
        style={styles.labelText}
        numberOfLines={1}
        onLayout={(event: LayoutChangeEvent) => onWidth(index, event.nativeEvent.layout.width)}
      >
        {text}
      </Text>
      <Animated.Text style={[styles.labelText, styles.labelTint, { color }, tintStyle]} numberOfLines={1}>
        {text}
      </Animated.Text>
    </Animated.View>
  );
}

/** The sample screen behind the navigation: the destination's name over three cards. */
function Content({ title, index }: { title: string; index: number }) {
  return (
    <LayoutAnimationConfig skipEntering>
      <Animated.View key={index} style={styles.content} entering={FadeIn.duration(SWAP)} exiting={FadeOut.duration(SWAP)}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        {CARDS.map((height, card) => {
          const lines = LINES[(index + card) % LINES.length];
          return (
            <View key={card} style={[styles.card, { height }]}>
              <View style={[styles.line, { width: `${lines[0] * 100}%` }]} />
              <View style={[styles.line, { width: `${lines[1] * 100}%` }]} />
            </View>
          );
        })}
      </Animated.View>
    </LayoutAnimationConfig>
  );
}

/**
 * One white navigation surface in three shapes: a labelled list, a narrow icon rail and a bottom
 * bar. The surface itself morphs from one to the next, and every icon is the same view all the
 * way: it flies from its row in the column to its place in the bar along a curve.
 */
export function RailToBarMorph({ items = ITEMS, barItems = 5, color = FIELD, autoPlay = false, onSelect }: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [shape, setShape] = useState<Shape>('list');
  const [current, setCurrent] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The labels' widths as laid out, which the capsule hugs; a guess stands in until then.
  const [measured, setMeasured] = useState<Record<number, number>>({});

  const wide = useSharedValue(1);
  const dock = useSharedValue(0);
  const selFrom = useSharedValue(0);
  const selTo = useSharedValue(0);
  const selClock = useSharedValue(1);
  const hopIndex = useSharedValue(-1);
  const hop = useSharedValue(1);
  const fade = useSharedValue(1);
  // The clock of the labels leaving or coming back, and which of the two it is (1: coming back).
  const labels = useSharedValue(1);
  const showing = useSharedValue(1);
  const swipeFrom = useSharedValue(0);
  const clocks: Clocks = { wide, dock, selFrom, selTo, selClock, hopIndex, hop, fade };

  const docked = Math.min(barItems, items.length);
  const frame: Frame = { width: size.width, height: size.height, top: insets.top, bar: BAR + insets.bottom };
  const widths = items.map((item, index) => measured[index] ?? item.label.length * 9.4);

  const reshape = (to: Shape) => {
    if (to === shape) return;
    const from = shape;
    setShape(to);
    const toWide = to === 'list' ? 1 : 0;
    const toDock = to === 'bar' ? 1 : 0;
    if (reducedMotion) {
      wide.set(toWide);
      dock.set(toDock);
      showing.set(toWide);
      labels.set(1);
      fade.set(0);
      fade.set(withTiming(1, { duration: FADE, reduceMotion: NEVER }));
      return;
    }
    if ((from === 'list') !== (to === 'list')) {
      const total = LABEL_IN + (items.length - 1) * LABEL_GAP;
      showing.set(toWide);
      labels.set(0);
      labels.set(withDelay(from === 'bar' ? LABELS_AFTER * 1000 : 0, withTiming(1, { duration: total * 1000, easing: LINEAR })));
      wide.set(withSpring(toWide, WIDE_SPRING));
    }
    if ((from === 'bar') !== (to === 'bar')) {
      const takes = toDock === 1 ? DOCK_IN : DOCK_OUT;
      dock.set(withDelay(from === 'list' ? COLLAPSE_AFTER * 1000 : 0, withTiming(toDock, { duration: takes * 1000, easing: LINEAR })));
    }
  };

  const go = (index: number, hopping: boolean) => {
    if (index !== current) {
      selFrom.set(current);
      selTo.set(index);
      selClock.set(0);
      selClock.set(withTiming(1, { duration: SELECT * 1000, easing: LINEAR, reduceMotion: NEVER }));
      setCurrent(index);
      onSelect?.(items[index]);
    }
    if (hopping && shape === 'bar' && index < docked && !reducedMotion) {
      hopIndex.set(index);
      hop.set(0);
      hop.set(withTiming(1, { duration: HOP_FOR * 1000, easing: LINEAR }));
    }
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const pick = (index: number) => {
    touched();
    tick();
    go(index, true);
    // A row of the list goes there and folds the list away.
    if (shape === 'list') reshape('bar');
  };

  const unfold = () => {
    if (shape !== 'bar') return;
    touched();
    tick();
    reshape('list');
  };

  // The latest of everything the loop's timers call.
  const latest = useRef({ reshape, go });
  useEffect(() => {
    latest.current = { reshape, go };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      // Each step sets the state that is due at its time, whatever the steps before it did.
      for (const step of SCRIPT) {
        after(origin + step.at * 1000 - Date.now(), () => {
          if (step.shape !== undefined) latest.current.reshape(step.shape);
          if (step.current !== undefined) latest.current.go(step.current, true);
        });
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // Back to rest; the list is open again by the time the loop first moves.
        latest.current.go(0, false);
        latest.current.reshape('list');
        cycle(Date.now());
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const swipe = Gesture.Pan()
    .activeOffsetX(20)
    .failOffsetY([-20, 20])
    .onBegin((event) => {
      swipeFrom.set(event.x);
    })
    .onStart(() => {
      if (swipeFrom.get() < EDGE) scheduleOnRN(unfold);
    });

  const panelStyle = useAnimatedStyle(() => {
    const panel = panelAt(wide.get(), dock.get(), docked, frame);
    return {
      top: panel.top,
      width: panel.width,
      borderTopLeftRadius: panel.topLeft,
      borderTopRightRadius: panel.topRight,
      borderBottomRightRadius: panel.bottomRight,
      opacity: fade.get(),
    };
  });
  // What is on the panel is placed on the screen, whatever the panel's frame is.
  const insideStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -panelAt(wide.get(), dock.get(), docked, frame).top }] }));
  const markStyle = useAnimatedStyle(() => {
    const at = markAt(wide.get(), dock.get(), frame);
    return { transform: [{ translateX: at.x - MARK / 2 }, { translateY: at.y - MARK / 2 }] };
  });
  const fadeStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));
  const scrimStyle = useAnimatedStyle(() => ({ opacity: SCRIM_ALPHA * clamp(wide.get()) * (1 - easeInOut(dock.get())) }));
  // The content clears the rail, and takes the whole width over the bar.
  const contentStyle = useAnimatedStyle(() => ({ left: mix(RAIL + MARGIN, MARGIN, easeInOut(dock.get())) }));
  const capsuleStyle = useAnimatedStyle(() => {
    const c = selClock.get();
    const to = selTo.get();
    const lead = reducedMotion ? to : mix(selFrom.get(), to, easeOut(c / LEAD));
    const trail = reducedMotion ? to : mix(selFrom.get(), to, easeInOut(c));
    const upper = Math.min(lead, trail);
    const middle = (lead + trail) / 2;
    const above = Math.min(widths.length - 1, Math.floor(middle));
    const below = Math.min(widths.length - 1, above + 1);
    return {
      top: frame.top + ROW_Y + upper * PITCH - CAPSULE / 2,
      height: CAPSULE + Math.abs(lead - trail) * PITCH,
      width: LABEL_X + mix(widths[above], widths[below], middle - above) + CAPSULE_PAD - CAPSULE_X,
      opacity: clamp(wide.get()) * (1 - easeInOut(dock.get())),
    };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };
  const onWidth = (index: number, width: number) => {
    setMeasured((known) => (known[index] === width ? known : { ...known, [index]: width }));
  };

  const title = items[current]?.label ?? '';

  return (
    <GestureDetector gesture={swipe}>
      <View style={[styles.screen, { backgroundColor: color }]} onLayout={onLayout}>
        <StatusBar hidden />

        {HALOS.map((radius) => (
          <View
            key={radius}
            style={[
              styles.halo,
              { width: radius * 2, height: radius * 2, borderRadius: radius, left: size.width / 2 - radius, top: size.height * HALO_Y - radius },
            ]}
          />
        ))}

        <Animated.View
          style={[styles.contentSlot, { top: insets.top + TITLE_Y }, contentStyle]}
          accessibilityElementsHidden={shape === 'list'}
          importantForAccessibility={shape === 'list' ? 'no-hide-descendants' : 'auto'}
        >
          <Content title={title} index={current} />
        </Animated.View>

        {/* The mark twice at one place: white on the field, and in colour on the panel, which hides the white one. */}
        <Animated.View pointerEvents="none" style={[styles.mark, markStyle, fadeStyle]}>
          <Mark color="#ffffff" />
        </Animated.View>

        <Animated.View pointerEvents="none" style={[styles.fill, styles.scrim, scrimStyle]} />

        <Animated.View pointerEvents="none" style={[styles.panel, panelStyle]}>
          <Animated.View style={[styles.inside, { width: size.width, height: size.height }, insideStyle]}>
            <Animated.View style={[styles.capsule, capsuleStyle]} />
            {items.map((item, index) => (
              <Label
                key={item.id}
                text={item.label}
                index={index}
                count={items.length}
                top={insets.top + ROW_Y + index * PITCH - 11}
                clocks={clocks}
                labels={labels}
                showing={showing}
                still={reducedMotion}
                color={color}
                onWidth={onWidth}
              />
            ))}
            <Animated.View style={[styles.mark, markStyle]}>
              <Mark color={color} />
            </Animated.View>
          </Animated.View>
        </Animated.View>

        {items.map((item, index) => (
          <NavIcon
            key={item.id}
            item={item}
            index={index}
            docked={docked}
            frame={frame}
            clocks={clocks}
            color={color}
            enabled={shape === 'rail' || (shape === 'bar' && index < docked)}
            selected={index === current}
            onPress={() => pick(index)}
          />
        ))}

        {shape === 'list' && (
          <>
            <Pressable
              style={[styles.outside, { left: LIST }]}
              accessibilityRole="button"
              accessibilityLabel="Close menu"
              onPress={() => {
                touched();
                reshape('bar');
              }}
            />
            {items.map((item, index) => (
              <Pressable
                key={item.id}
                style={[styles.row, { top: insets.top + ROW_Y + index * PITCH - PITCH / 2 }]}
                onPress={() => pick(index)}
                accessibilityRole="button"
                accessibilityLabel={item.label}
                accessibilityState={{ selected: index === current }}
              />
            ))}
          </>
        )}

        {shape === 'bar' && (
          <Pressable
            style={[styles.markHit, { left: size.width / 2 - HIT / 2, top: insets.top + MARK_Y - HIT / 2 }]}
            onPress={unfold}
            accessibilityRole="button"
            accessibilityLabel="Menu"
          />
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  halo: {
    position: 'absolute',
    backgroundColor: HALO,
  },
  contentSlot: {
    position: 'absolute',
    right: MARGIN,
    bottom: 0,
  },
  content: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  title: {
    marginBottom: 20,
    color: '#ffffff',
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '700',
    includeFontPadding: false,
  },
  card: {
    marginBottom: 12,
    padding: 18,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    justifyContent: 'flex-end',
    gap: 10,
  },
  line: {
    height: 10,
    borderRadius: 5,
    backgroundColor: LINE,
  },
  scrim: {
    backgroundColor: SCRIM,
  },
  panel: {
    position: 'absolute',
    left: 0,
    bottom: 0,
    overflow: 'hidden',
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
  },
  inside: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  capsule: {
    position: 'absolute',
    left: CAPSULE_X,
    borderRadius: CAPSULE / 2,
    backgroundColor: CAPSULE_FILL,
  },
  label: {
    position: 'absolute',
    left: LABEL_X,
  },
  labelText: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
    includeFontPadding: false,
  },
  labelTint: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  mark: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: MARK,
    height: MARK,
  },
  iconBox: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: HIT,
    height: HIT,
  },
  iconHit: {
    flex: 1,
  },
  glyph: {
    position: 'absolute',
    left: (HIT - ICON) / 2,
    top: (HIT - ICON) / 2,
    width: ICON,
    height: ICON,
  },
  layer: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: ICON,
    height: ICON,
  },
  window: {
    overflow: 'hidden',
  },
  dot: {
    position: 'absolute',
    left: (HIT - DOT) / 2,
    top: HIT / 2 + DOT_DROP - DOT / 2,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: DOT_FILL,
  },
  outside: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
  },
  row: {
    position: 'absolute',
    left: 0,
    width: LIST,
    height: PITCH,
  },
  markHit: {
    position: 'absolute',
    width: HIT,
    height: HIT,
  },
});
