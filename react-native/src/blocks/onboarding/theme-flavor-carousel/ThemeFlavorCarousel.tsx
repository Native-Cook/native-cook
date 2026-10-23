import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  type Transforms3d,
  interpolateColors,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolateColor,
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

export type Flavor = {
  name: string;
  /** The darkest tone: a soft spot at the top of the card. */
  deep: string;
  /** The card's base, and the tint of the button. */
  mid: string;
  /** The pale tone that comes up from the bottom left corner. */
  light: string;
  /** The button's label and glyph on this flavor. */
  ink: string;
};

type Props = {
  /** The cards, in order; the last is followed by the first. At least four. */
  flavors?: Flavor[];
  title?: string;
  subtitle?: string;
  buttonLabel?: string;
  /** Swipes through the flavors by itself, one every 1.6 s: LOOP seconds for the six it comes with. */
  autoPlay?: boolean;
  /** Called when another flavor comes to the middle. */
  onChange?: (flavor: Flavor, index: number) => void;
  /** Called when the button gives the screen the flavor in the middle. */
  onSelect?: (flavor: Flavor, index: number) => void;
};

const FLAVORS: Flavor[] = [
  { name: 'Mint', deep: '#0b9077', mid: '#669990', light: '#e7f6f5', ink: '#0a4f43' },
  { name: 'Clay', deep: '#a17d6f', mid: '#c0a087', light: '#fcf6f8', ink: '#3a2f2b' },
  { name: 'Graphite', deep: '#121013', mid: '#716f73', light: '#f7f5f9', ink: '#2b2b2e' },
  { name: 'Azure', deep: '#009efb', mid: '#0c6ed9', light: '#ecf6fa', ink: '#0b4f8f' },
  { name: 'Cobalt', deep: '#1259ba', mid: '#2362b6', light: '#ebeffa', ink: '#123c7c' },
  { name: 'Lagoon', deep: '#0da1af', mid: '#3093c9', light: '#ecf3f4', ink: '#0b5560' },
];

const BACKGROUND = '#fdfdfd';
const TITLE = '#1c1c1e';
const SUBTITLE = '#3c3c43';
// The texts once the screen has taken a dark flavor.
const ON_DARK = '#ffffff';
const ON_DARK_SUBTITLE = '#e9eef2';

// From the top of the safe area: the title, the two lines under it, then the cards.
const TOP = 12;
const TEXT_LINE = 24;
const TEXT_GAP = 8;
const TEXT_SIDE = 40;
const HEADER = TOP + TEXT_LINE + TEXT_GAP + TEXT_LINE * 2 + 21;
const SIDE = 50;
const CARD_MAX = 340;
// A card is this much taller than wide when the screen has the room.
const CARD_RATIO = 503 / 292;
const RADIUS = 34;
const GAP = 20;
// A card next to the middle one is smaller and sits lower.
const SHRINK = 0.04;
const DROP = 8;
const SHADOW_Y = 12;
const SHADOW_SIGMA = 15;
const SHADOW = 0.18;
const BUTTON = 52;
const BUTTON_SIDE = 51;
const BUTTON_GAP = 70;
const BOTTOM = 22;
const BORDER = 1.5;
const BORDER_ALPHA = 0.7;
// How much of the flavor the bottom of the button takes; its top stays white.
const WASH = 0.3;
const GLOW_Y = 6;
const GLOW_SIGMA = 7;
const GLOW = 0.35;
// Room round the button for its glow.
const BLEED = 28;
const GLYPH = 20;
const CHECK = 'M6.3 10.3L8.9 12.8L13.8 7.5';
const PRESS_SCALE = 0.96;
const PRESS = 120;

// The mesh of a card, in fractions of its width and height: a deep spot at the top, a pale one
// rising from the bottom left corner, both soft discs on the mid tone.
const DEEP_X = 0.5;
const DEEP_Y = 0.12;
const DEEP_RX = 1.3;
const DEEP_RY = 0.56;
const LIGHT_X = 0.04;
const LIGHT_Y = 1.02;
const LIGHT_RX = 1.35;
const LIGHT_RY = 0.84;
// How a disc fades from its centre to its edge.
const FALLOFF = [1, 0.9, 0.5, 0.1, 0];
const FALLOFF_AT = [0, 0.25, 0.5, 0.75, 1];
// The deep spot goes round a circle of DRIFT points, one turn in DRIFT_TURN seconds, while the
// pale one grows and shrinks by BREATH; each card is PHASE radians ahead of the one before.
const DRIFT = 20;
const DRIFT_TURN = 4.8;
const BREATH = 0.06;
const PHASE = 0.9;
// The clock that turns the meshes runs this long and starts over, on a whole turn.
const CLOCK = DRIFT_TURN * 750;

/** autoPlay goes round the six flavors once; a flavor rests, then the carousel travels to the next. */
export const LOOP = 9.6;
const STEP = 1.6;
const REST = 0.8;
const TRAVEL = 0.8;
// autoPlay swipes like a finger: it pulls the cards PULL_BY of a card in PULL seconds, speeding
// up, and lets go at that speed.
const PULL = 0.26;
const PULL_BY = 0.4;
/** What a released carousel settles with (damping ratio 0.8). */
const SPRING = { mass: 1, stiffness: 260, damping: 25.8 };
// A flick carries the carousel this many seconds of travel ahead, and at most this many cards.
const FLICK = 0.15;
const FLICK_MAX = 0.6;
// The fastest a release may throw the cards, in cards a second.
const THROW_MAX = 8;
// After a touch autoPlay waits this long before it goes back to the first flavor.
const USER_HOLD = 3;
// The screen takes a flavor: the card in the middle grows over it.
const APPLY = 600;
const APPLY_EASING = Easing.bezier(0.65, 0, 0.35, 1);
// Under reduced motion the carousel moves in one short step.
const JUMP = 220;
const NEVER = ReduceMotion.Never;
// Colours are blended channel by channel, as the canvas and the other stacks blend them.
const PLAIN = { gamma: 1 };

function channels(hex: string) {
  const value = parseInt(hex.slice(1), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

function fade(hex: string, alpha: number) {
  const [r, g, b] = channels(hex);
  return `rgba(${r},${g},${b},${alpha})`;
}

/** White with `amount` of a colour in it. */
function washed(hex: string, amount: number) {
  const [r, g, b] = channels(hex).map((c) => Math.round(255 + (c - 255) * amount));
  return `rgb(${r},${g},${b})`;
}

function isDark(hex: string) {
  const [r, g, b] = channels(hex);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b < 153;
}

/** A place on the ring of cards: 0 up to the number of cards. */
function lap(value: number, count: number) {
  'worklet';
  return ((value % count) + count) % count;
}

/** How far card `index` is from the middle, in cards, going round the short way. */
function away(index: number, position: number, count: number) {
  'worklet';
  return lap(index - position + count / 2, count) - count / 2;
}

/** A released spring `t` seconds on: how far it still is from its target, having started `from` away at `velocity`. */
function springAway(t: number, from: number, velocity: number) {
  'worklet';
  const omega = Math.sqrt(SPRING.stiffness / SPRING.mass);
  const zeta = SPRING.damping / (2 * Math.sqrt(SPRING.stiffness * SPRING.mass));
  const omega1 = omega * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * omega * t) * (from * Math.cos(omega1 * t) + ((velocity + zeta * omega * from) / omega1) * Math.sin(omega1 * t));
}

/** autoPlay's swipe as an easing over TRAVEL: the pull of a finger, then the spring of a release. */
function swipeEasing(t: number) {
  'worklet';
  const seconds = t * TRAVEL;
  if (seconds < PULL) return PULL_BY * (seconds / PULL) ** 2;
  return 1 + springAway(seconds - PULL, PULL_BY - 1, (2 * PULL_BY) / PULL);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Frame = { x: number; y: number; width: number; height: number; step: number };

/** A soft disc of one colour; the transform places it and gives it its radii. */
function Disc({ transform, color }: { transform: SharedValue<Transforms3d>; color: string }) {
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={1}>
        <RadialGradient c={vec(0, 0)} r={1} colors={FALLOFF.map((alpha) => fade(color, alpha))} positions={FALLOFF_AT} />
      </Circle>
    </Group>
  );
}

/** A flavor's mesh, drawn from the corner of a card of the given size. */
function Mesh({ flavor, index, clock, width, height }: { flavor: Flavor; index: number; clock: SharedValue<number>; width: number; height: number }) {
  const deep = useDerivedValue<Transforms3d>(() => {
    const angle = (clock.get() / DRIFT_TURN) * 2 * Math.PI + index * PHASE;
    return [
      { translateX: DEEP_X * width + DRIFT * Math.cos(angle) },
      { translateY: DEEP_Y * height + DRIFT * Math.sin(angle) },
      { scaleX: DEEP_RX * width },
      { scaleY: DEEP_RY * height },
    ];
  });
  const light = useDerivedValue<Transforms3d>(() => {
    const breath = 1 + BREATH * Math.sin((clock.get() / DRIFT_TURN) * 2 * Math.PI + index * PHASE);
    return [{ translateX: LIGHT_X * width }, { translateY: LIGHT_Y * height }, { scaleX: LIGHT_RX * width * breath }, { scaleY: LIGHT_RY * height * breath }];
  });
  return (
    <>
      <Rect x={0} y={0} width={width} height={height} color={flavor.mid} />
      <Disc transform={deep} color={flavor.deep} />
      <Disc transform={light} color={flavor.light} />
    </>
  );
}

/** One card of the carousel: it rides with the position, smaller and lower away from the middle. */
function Card({
  flavor,
  index,
  count,
  position,
  clock,
  frame,
}: {
  flavor: Flavor;
  index: number;
  count: number;
  position: SharedValue<number>;
  clock: SharedValue<number>;
  frame: Frame;
}) {
  const transform = useDerivedValue<Transforms3d>(() => {
    const by = away(index, position.get(), count);
    const aside = Math.min(1, Math.abs(by));
    return [{ translateX: frame.x + by * frame.step }, { translateY: frame.y + DROP * aside }, { scale: 1 - SHRINK * aside }];
  });
  return (
    <Group transform={transform} origin={vec(frame.width / 2, frame.height / 2)}>
      <RoundedRect x={0} y={SHADOW_Y} width={frame.width} height={frame.height} r={RADIUS} color={flavor.mid} opacity={SHADOW}>
        <BlurMask blur={SHADOW_SIGMA} style="normal" />
      </RoundedRect>
      <Group clip={rrect(rect(0, 0, frame.width, frame.height), RADIUS, RADIUS)}>
        <Mesh flavor={flavor} index={index} clock={clock} width={frame.width} height={frame.height} />
      </Group>
    </Group>
  );
}

/** The chosen flavor's mesh growing from its card to the whole screen. */
function Cover({
  flavor,
  index,
  count,
  position,
  progress,
  clock,
  frame,
  screen,
  still,
}: {
  flavor: Flavor;
  index: number;
  count: number;
  position: SharedValue<number>;
  progress: SharedValue<number>;
  clock: SharedValue<number>;
  frame: Frame;
  screen: { width: number; height: number };
  still: boolean;
}) {
  // It grows from where its card is now, which may still be on its way to the middle. Under
  // reduced motion it does not grow: it fades in over the screen.
  const box = useDerivedValue(() => {
    const t = still ? 1 : progress.get();
    const by = away(index, position.get(), count);
    const aside = Math.min(1, Math.abs(by));
    const scale = 1 - SHRINK * aside;
    const x = frame.x + by * frame.step + (frame.width * (1 - scale)) / 2;
    const y = frame.y + DROP * aside + (frame.height * (1 - scale)) / 2;
    const width = frame.width * scale;
    const height = frame.height * scale;
    return {
      x: x * (1 - t),
      y: y * (1 - t),
      width: width + (screen.width - width) * t,
      height: height + (screen.height - height) * t,
      radius: RADIUS * scale * (1 - t),
    };
  });
  const clip = useDerivedValue(() => {
    const { x, y, width, height, radius } = box.get();
    return rrect(rect(x, y, width, height), radius, radius);
  });
  const transform = useDerivedValue<Transforms3d>(() => {
    const { x, y, width, height } = box.get();
    return [{ translateX: x }, { translateY: y }, { scaleX: width / frame.width }, { scaleY: height / frame.height }];
  });
  const opacity = useDerivedValue(() => (still ? progress.get() : progress.get() > 0 ? 1 : 0));
  return (
    <Group clip={clip} opacity={opacity}>
      <Group transform={transform}>
        <Mesh flavor={flavor} index={index} clock={clock} width={frame.width} height={frame.height} />
      </Group>
    </Group>
  );
}

/**
 * A personalisation step: a carousel of tall mesh-gradient cards, one per colour "flavor", that
 * goes round without an end, and a glassy button that takes the colours of the card in the middle
 * as it passes. The button gives the whole screen that flavor: the card grows over it.
 */
export function ThemeFlavorCarousel({
  flavors = FLAVORS,
  title = 'Select or Create a Flavor',
  subtitle = 'Flavors theme backgrounds, buttons, and more',
  buttonLabel = 'Select Flavor',
  autoPlay = false,
  onChange,
  onSelect,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const count = flavors.length;

  // The flavor the screen took, and whether it still has it: the colours stay while it leaves.
  const [chosen, setChosen] = useState({ index: 0, on: false });
  // The card the carousel is on or heading to; like the position, it keeps counting.
  const [centre, setCentre] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Where the carousel is, in cards. It keeps counting past the last flavor, which the first follows.
  const position = useSharedValue(0);
  const grabbed = useSharedValue(0);
  // Seconds, for the meshes.
  const clock = useSharedValue(0);
  // 0 to 1 as the chosen flavor covers the screen.
  const cover = useSharedValue(0);
  const pressed = useSharedValue(0);

  const bottom = Math.max(insets.bottom, 12) + BOTTOM;
  const cardWidth = Math.min(CARD_MAX, size.width - SIDE * 2);
  const room = size.height - insets.top - HEADER - BUTTON_GAP - BUTTON - bottom;
  const cardHeight = Math.max(0, Math.min(room, cardWidth * CARD_RATIO));
  const frame: Frame = {
    x: (size.width - cardWidth) / 2,
    y: insets.top + HEADER + (room - cardHeight) / 2,
    width: cardWidth,
    height: cardHeight,
    step: cardWidth + GAP,
  };
  const buttonWidth = size.width - BUTTON_SIDE * 2;

  useEffect(() => {
    if (reducedMotion) return;
    clock.set(withRepeat(withTiming(CLOCK, { duration: CLOCK * 1000, easing: Easing.linear, reduceMotion: NEVER }), -1));
    return () => {
      cancelAnimation(clock);
      clock.set(0);
    };
  }, [clock, reducedMotion]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const landed = (to: number, byHand: boolean) => {
    if (to === centre) return;
    setCentre(to);
    if (byHand) tick();
    const index = lap(to, count);
    onChange?.(flavors[index], index);
  };

  const settle = (to: number) => (reducedMotion ? withTiming(to, { duration: JUMP, reduceMotion: NEVER }) : withSpring(to, { ...SPRING, reduceMotion: NEVER }));

  /** autoPlay's swipe to the next card. */
  const swipe = (to: number) => {
    landed(to, false);
    position.set(
      reducedMotion
        ? withTiming(to, { duration: JUMP, reduceMotion: NEVER })
        : withTiming(to, { duration: TRAVEL * 1000, easing: swipeEasing, reduceMotion: NEVER }),
    );
  };

  /** One card on or back, for the accessibility actions. */
  const nudge = (by: number) => {
    if (chosen.on) return;
    touched();
    const to = centre + by;
    landed(to, true);
    position.set(settle(to));
  };

  const dress = (on: boolean) => cover.set(withTiming(on ? 1 : 0, { duration: reducedMotion ? JUMP : APPLY, easing: APPLY_EASING, reduceMotion: NEVER }));

  const select = () => {
    touched();
    tick();
    if (chosen.on) {
      setChosen({ index: chosen.index, on: false });
      dress(false);
      return;
    }
    const index = lap(centre, count);
    setChosen({ index, on: true });
    dress(true);
    onSelect?.(flavors[index], index);
  };

  /** Back to the first flavor on a plain screen; answers the card the carousel is going to. */
  const rest = () => {
    setChosen((now) => ({ index: now.index, on: false }));
    dress(false);
    const home = Math.round(position.get() / count) * count;
    landed(home, false);
    position.set(settle(home));
    return home;
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ swipe, rest });
  useEffect(() => {
    latest.current = { swipe, rest };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // One round of the flavors from card `home`, timed from `origin` so that rounds do not drift.
    const cycle = (origin: number, home: number) => {
      for (let k = 0; k < count; k++) after(origin + (k * STEP + REST) * 1000 - Date.now(), () => latest.current.swipe(home + k + 1));
      after(origin + count * STEP * 1000 - Date.now(), () => cycle(origin + count * STEP * 1000, home + count));
    };
    if (touches === 0) cycle(Date.now(), 0);
    else after(USER_HOLD * 1000, () => cycle(Date.now(), latest.current.rest()));
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, count]);

  const pan = Gesture.Pan()
    .enabled(!chosen.on)
    .onBegin(() => {
      cancelAnimation(position);
      grabbed.set(position.get());
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      position.set(grabbed.get() - event.translationX / frame.step);
    })
    .onFinalize((event, success) => {
      const velocity = success ? Math.max(-THROW_MAX, Math.min(THROW_MAX, -event.velocityX / frame.step)) : 0;
      const to = Math.round(position.get() + Math.max(-FLICK_MAX, Math.min(FLICK_MAX, velocity * FLICK)));
      position.set(
        reducedMotion ? withTiming(to, { duration: JUMP, reduceMotion: NEVER }) : withSpring(to, { ...SPRING, velocity, reduceMotion: NEVER }),
      );
      scheduleOnRN(landed, to, true);
      scheduleOnRN(touched);
    });

  // The button's colours follow the carousel: they are read off the ring of flavors at its position.
  const ring = Array.from({ length: count + 1 }, (_, index) => index);
  const around = (pick: (flavor: Flavor) => string) => [...flavors.map(pick), pick(flavors[0])];
  const mids = around((flavor) => flavor.mid);
  const washes = around((flavor) => washed(flavor.mid, WASH));
  const inks = around((flavor) => flavor.ink);
  const tint = useDerivedValue(() => interpolateColors(lap(position.get(), count), ring, mids));
  const wash = useDerivedValue(() => interpolateColors(lap(position.get(), count), ring, washes));
  const ink = useDerivedValue(() => interpolateColors(lap(position.get(), count), ring, inks));
  const inkStyle = useAnimatedStyle(() => ({ color: interpolateColor(lap(position.get(), count), ring, inks, 'RGB', PLAIN) }));
  const unchecked = useDerivedValue(() => 1 - cover.get());
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS_SCALE) * pressed.get() }] }));

  // On a dark flavor the texts turn white as it covers the screen.
  const dark = isDark(flavors[chosen.index].deep);
  const titleStyle = useAnimatedStyle(() => ({ color: interpolateColor(cover.get(), [0, 1], [TITLE, dark ? ON_DARK : TITLE], 'RGB', PLAIN) }));
  const subtitleStyle = useAnimatedStyle(() => ({ color: interpolateColor(cover.get(), [0, 1], [SUBTITLE, dark ? ON_DARK_SUBTITLE : SUBTITLE], 'RGB', PLAIN) }));

  const press = (down: boolean) => pressed.set(withTiming(down ? 1 : 0, { duration: PRESS, reduceMotion: NEVER }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style={chosen.on && dark ? 'light' : 'dark'} />

      <View pointerEvents="none" style={styles.fill}>
        <Canvas style={styles.fill}>
          {flavors.map((flavor, index) => (
            <Card key={flavor.name} flavor={flavor} index={index} count={count} position={position} clock={clock} frame={frame} />
          ))}
          <Cover flavor={flavors[chosen.index]} index={chosen.index} count={count} position={position} progress={cover} clock={clock} frame={frame} screen={size} still={reducedMotion} />
        </Canvas>
      </View>

      <View pointerEvents="none" style={[styles.header, { paddingTop: insets.top + TOP }]}>
        <Animated.Text style={[styles.title, titleStyle]} numberOfLines={1}>
          {title}
        </Animated.Text>
        {/* iOS breaks the lines as it does for its own apps, so the last word is not left alone. */}
        <Animated.Text style={[styles.subtitle, subtitleStyle]} numberOfLines={2} lineBreakStrategyIOS="standard">
          {subtitle}
        </Animated.Text>
      </View>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.track, { top: insets.top + HEADER, height: Math.max(0, room) + BUTTON_GAP / 2 }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Flavor"
          accessibilityValue={{ text: flavors[lap(centre, count)].name }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => nudge(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
        />
      </GestureDetector>

      <Animated.View style={[styles.buttonSlot, { left: BUTTON_SIDE, width: buttonWidth, bottom }, pressStyle]}>
        <View pointerEvents="none" style={styles.buttonCanvas}>
          <Canvas style={styles.fill}>
            <RoundedRect x={BLEED} y={BLEED + GLOW_Y} width={buttonWidth} height={BUTTON} r={BUTTON / 2} color={tint} opacity={GLOW}>
              <BlurMask blur={GLOW_SIGMA} style="normal" />
            </RoundedRect>
            <RoundedRect x={BLEED} y={BLEED} width={buttonWidth} height={BUTTON} r={BUTTON / 2} color={wash} />
            <RoundedRect x={BLEED} y={BLEED} width={buttonWidth} height={BUTTON} r={BUTTON / 2}>
              <LinearGradient start={vec(0, BLEED)} end={vec(0, BLEED + BUTTON)} colors={['#ffffff', 'rgba(255,255,255,0)']} />
            </RoundedRect>
            <RoundedRect
              x={BLEED + BORDER / 2}
              y={BLEED + BORDER / 2}
              width={buttonWidth - BORDER}
              height={BUTTON - BORDER}
              r={(BUTTON - BORDER) / 2}
              style="stroke"
              strokeWidth={BORDER}
              color={tint}
              opacity={BORDER_ALPHA}
            />
          </Canvas>
        </View>
        <Pressable
          style={styles.button}
          accessibilityRole="button"
          accessibilityState={{ selected: chosen.on }}
          onPressIn={() => press(true)}
          onPressOut={() => press(false)}
          onPress={select}
        >
          {/* A check in a ring; the ring fills once the screen has the flavor. */}
          <Canvas style={styles.glyph}>
            <Circle cx={GLYPH / 2} cy={GLYPH / 2} r={8.25} color={ink} opacity={cover} />
            <Circle cx={GLYPH / 2} cy={GLYPH / 2} r={8.25} style="stroke" strokeWidth={1.5} color={ink} />
            <Path path={CHECK} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color={ink} opacity={unchecked} />
            <Path path={CHECK} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color="#ffffff" opacity={cover} />
          </Canvas>
          <Animated.Text style={[styles.buttonLabel, inkStyle]} numberOfLines={1}>
            {buttonLabel}
          </Animated.Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    paddingHorizontal: TEXT_SIDE,
    alignItems: 'center',
  },
  title: {
    fontSize: 20,
    lineHeight: TEXT_LINE,
    fontWeight: '500',
    textAlign: 'center',
    includeFontPadding: false,
  },
  subtitle: {
    marginTop: TEXT_GAP,
    fontSize: 16,
    lineHeight: TEXT_LINE,
    textAlign: 'center',
    includeFontPadding: false,
  },
  track: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  buttonSlot: {
    position: 'absolute',
    height: BUTTON,
  },
  buttonCanvas: {
    position: 'absolute',
    left: -BLEED,
    right: -BLEED,
    top: -BLEED,
    bottom: -BLEED,
  },
  button: {
    height: BUTTON,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  glyph: {
    width: GLYPH,
    height: GLYPH,
    marginRight: 8,
  },
  buttonLabel: {
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '500',
    includeFontPadding: false,
  },
});
