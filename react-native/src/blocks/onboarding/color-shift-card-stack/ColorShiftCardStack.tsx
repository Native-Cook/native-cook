import { Canvas, Circle, Line, Path, RoundedRect, vec } from '@shopify/react-native-skia';
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
  interpolateColor,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type CardGlyph = 'people' | 'chart' | 'star' | 'play' | 'download';

export type StackCard = {
  /** Two or three lines; the last word of the last line is written in the card's colour. */
  lines: string[];
  /** The colour of that word, of the glyph and of the whole screen while the card is on top. */
  color: string;
  glyph: CardGlyph;
};

type Props = {
  /** The cards, four or more; after the last comes the first again. */
  cards?: StackCard[];
  /** The headline's lines, and the one word of it that is white. */
  headline?: string[];
  highlight?: string;
  body?: string;
  buttonLabel?: string;
  /** Flicks through the cards by itself, one every 1.7 s: LOOP seconds for the five it comes with. */
  autoPlay?: boolean;
  /** Called when another card comes to the top. */
  onChange?: (card: StackCard, index: number) => void;
  onSignIn?: () => void;
};

const CARDS: StackCard[] = [
  { lines: ['two', 'hundred', 'nineteen followers'], color: '#1986ef', glyph: 'people' },
  { lines: ['five thousand', 'thirty two MRR'], color: '#ee424a', glyph: 'chart' },
  { lines: ['one thousand', 'fifty three stars'], color: '#1c7132', glyph: 'star' },
  { lines: ['ten thousand', 'eight hundred', 'subscribers'], color: '#f5333f', glyph: 'play' },
  { lines: ['six thousand', 'fifty eight', 'downloads'], color: '#a7a0a2', glyph: 'download' },
];

const CARD_INK = '#1c1c1e';
// A card's face on top of the stack, one place down and two places down.
const FACES = ['#fffdff', '#f0f0f2', '#e4e4e8'];
// The dark of the headline is made from the screen's colour: this much of it, plus this much of
// its brightness on every channel. The body is the same dark, thinner.
const INK_KEEP = 0.2;
const INK_LIGHT = 0.14;
const BODY_ALPHA = 0.8;

const SIDE = 24;
const HEADLINE = 50;
const HEADLINE_LINE = 68;
// On a short screen the headline is set smaller.
const SHORT = 700;
const HEADLINE_SHORT = 44;
const HEADLINE_LINE_SHORT = 60;
const BODY_GAP = 17;
const BODY_LINE = 18;
const STACK_GAP = 35;
const CARD_SIDE = 42;
const CARD_MAX = 340;
const CARD_HEIGHT = 142;
const CARD_RADIUS = 20;
const GLYPH = 16;
const BUTTON_GAP = 46;
const BUTTON_WIDTH = 120;
const BUTTON = 46;
const BOTTOM = 26;
const PRESS_SCALE = 0.96;
const PRESS = 120;

// A card leaving to the left goes this far, turns this much and fades over the last third of the way.
const LEAVE = 340;
const TURN = 8;
const FADE_FROM = 2 / 3;
// Each place down the stack: turned, moved right and up, and smaller.
const DEPTH_TURN = 2;
const DEPTH_X = 6;
const DEPTH_Y = -3;
const DEPTH_SHRINK = 0.04;
// The top card floats this far up and down, and the white word dims this much, once every WAVE seconds.
const FLOAT = 2;
const SHIMMER = 0.15;
const WAVE = 1.7;
const CLOCK = WAVE * 400000;

/** autoPlay flicks the top card away every STEP seconds, after REST of rest. */
export const LOOP = 8.5;
const STEP = 1.7;
const REST = 1.1;
// The flick: the card is gone in FLICK, the colours have changed in SHIFT.
const FLICK = 350;
const FLICK_EASING = Easing.bezier(0.42, 0, 1, 1);
const SHIFT = 500;
const NEVER = ReduceMotion.Never;
/** The stack moves up a place with a small overshoot (damping ratio 0.7). */
const RISE_SPRING = { mass: 1, stiffness: 200, damping: 19.8, reduceMotion: NEVER };
/** A released card goes where it is sent without a bounce. */
const SNAP_SPRING = { mass: 1, stiffness: 220, damping: 26.5, reduceMotion: NEVER };
// A card dragged this far, or let go this fast (points a second), leaves; otherwise it comes back.
const COMMIT = 90;
const FLING = 600;
// After a touch autoPlay waits this long before it goes back to the first card.
const USER_HOLD = 3;
// Under reduced motion the cards change at once and the colours in one short step.
const JUMP = 250;

function channels(hex: string) {
  const value = parseInt(hex.slice(1), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

/** A place on the ring of cards: 0 up to the number of cards. */
function lap(value: number, count: number) {
  'worklet';
  return ((value % count) + count) % count;
}

/** How many places card `index` is behind `position`: from -1, just gone, up to the last place in the ring. */
function behind(index: number, position: number, count: number) {
  'worklet';
  return lap(index - position + 1, count) - 1;
}

/** The screen's colour at a place on the ring of cards: a plain blend of the two nearest, as red, green, blue. */
function blend(ring: number[][], at: number) {
  'worklet';
  const count = ring.length - 1;
  const place = lap(at, count);
  const index = Math.floor(place) % count;
  const t = place - Math.floor(place);
  return [0, 1, 2].map((c) => ring[index][c] + (ring[index + 1][c] - ring[index][c]) * t);
}

function inkOf(color: number[]) {
  'worklet';
  const light = 0.2126 * color[0] + 0.7152 * color[1] + 0.0722 * color[2];
  return color.map((c) => INK_KEEP * c + INK_LIGHT * light);
}

function rgba(color: number[], alpha: number) {
  'worklet';
  return `rgba(${Math.round(color[0])},${Math.round(color[1])},${Math.round(color[2])},${alpha})`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

// The star's ten corners round (8, 8.4): outer 7.6, inner 3.2.
const STAR = `M${Array.from({ length: 10 }, (_, corner) => {
  const radius = corner % 2 === 0 ? 7.6 : 3.2;
  const angle = (corner * Math.PI) / 5 - Math.PI / 2;
  return `${8 + radius * Math.cos(angle)} ${8.4 + radius * Math.sin(angle)}`;
}).join('L')}Z`;

/** A card's glyph in a 16 × 16 box, in the card's colour with details in the colour of its face. */
function Glyph({ kind, color }: { kind: CardGlyph; color: string }) {
  const face = FACES[0];
  return (
    <Canvas style={styles.glyph}>
      {kind === 'people' && (
        <>
          <Circle cx={11.6} cy={5.3} r={2.1} color={color} />
          <RoundedRect x={9.4} y={9.2} width={6} height={4.8} r={2.4} color={color} />
          <Circle cx={5.6} cy={4.5} r={3.3} color={face} />
          <RoundedRect x={0.2} y={7.8} width={10.8} height={7} r={3.5} color={face} />
          <Circle cx={5.6} cy={4.5} r={2.5} color={color} />
          <RoundedRect x={1} y={8.6} width={9.2} height={5.4} r={2.7} color={color} />
        </>
      )}
      {kind === 'chart' && (
        <>
          <RoundedRect x={0.5} y={0.5} width={15} height={15} r={4} color={color} />
          <RoundedRect x={4} y={8.5} width={2} height={4} r={1} color={face} />
          <RoundedRect x={7} y={4.5} width={2} height={8} r={1} color={face} />
          <RoundedRect x={10} y={6.5} width={2} height={6} r={1} color={face} />
        </>
      )}
      {kind === 'star' && <Path path={STAR} color={color} />}
      {kind === 'play' && (
        <>
          <RoundedRect x={0.5} y={2.5} width={15} height={11} r={3.2} color={color} />
          <Path path="M6.4 5.4L10.8 8L6.4 10.6Z" color={face} />
        </>
      )}
      {kind === 'download' && (
        <>
          <Circle cx={8} cy={8} r={7.5} color={color} />
          <Line p1={vec(8, 4.2)} p2={vec(8, 11.2)} style="stroke" strokeWidth={1.6} strokeCap="round" color={face} />
          <Path path="M5.2 8.6L8 11.4L10.8 8.6" style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color={face} />
        </>
      )}
    </Canvas>
  );
}

/**
 * One card. The card `position` points at is in the hand: it leaves to the left as the position
 * moves on. The others wait behind it, as many places down as they are behind `rise`.
 */
function Card({
  card,
  index,
  count,
  position,
  rise,
  clock,
  still,
}: {
  card: StackCard;
  index: number;
  count: number;
  position: SharedValue<number>;
  rise: SharedValue<number>;
  clock: SharedValue<number>;
  still: boolean;
}) {
  const style = useAnimatedStyle(() => {
    const float = still ? 0 : FLOAT * Math.sin((clock.get() / WAVE) * 2 * Math.PI);
    const gone = behind(index, position.get(), count);
    if (gone <= 0) {
      return {
        zIndex: 100,
        opacity: 1 - clamp01((-gone - FADE_FROM) / (1 - FADE_FROM)),
        transform: [{ translateX: gone * LEAVE }, { translateY: float }, { rotate: `${TURN * gone}deg` }, { scale: 1 }],
      };
    }
    const down = behind(index, rise.get(), count);
    const depth = Math.min(down, 2);
    return {
      zIndex: Math.round(90 - down * 10),
      // The card at the back of the ring comes into view as it reaches the third place.
      opacity: clamp01(3 - down),
      transform: [
        { translateX: DEPTH_X * depth },
        { translateY: DEPTH_Y * depth + float * clamp01(1 - down) },
        { rotate: `${DEPTH_TURN * depth}deg` },
        { scale: 1 - DEPTH_SHRINK * depth },
      ],
    };
  });
  const faceStyle = useAnimatedStyle(() => {
    const down = behind(index, position.get(), count) <= 0 ? 0 : behind(index, rise.get(), count);
    return {
      backgroundColor: interpolateColor(down, [0, 1, 2], FACES, 'RGB', { gamma: 1 }),
      // Only the two cards on top throw a shadow.
      boxShadow: [{ offsetX: 0, offsetY: 10, blurRadius: 24, color: `rgba(0,0,0,${0.22 * clamp01(2 - down)})` }],
    };
  });
  const last = card.lines[card.lines.length - 1];
  const cut = last.lastIndexOf(' ') + 1;
  return (
    <Animated.View style={[styles.card, style]}>
      <Animated.View style={[styles.face, faceStyle]}>
        <View style={styles.glyphSlot}>
          <Glyph kind={card.glyph} color={card.color} />
        </View>
        {card.lines.slice(0, -1).map((line) => (
          <Text key={line} style={styles.cardLine} numberOfLines={1}>
            {line}
          </Text>
        ))}
        <Text style={styles.cardLine} numberOfLines={1}>
          {last.slice(0, cut)}
          <Text style={{ color: card.color }}>{last.slice(cut)}</Text>
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

/**
 * A typographic welcome on one flat colour, with a small stack of white cards to flick through.
 * Each card that comes to the top gives the whole screen its colour: the background blends to it
 * and the headline's dark, which is made from the background, follows.
 */
export function ColorShiftCardStack({
  cards = CARDS,
  headline = ['Thanks', 'for being', 'an early', 'adopter'],
  highlight = 'early',
  body = 'Phase one, starts with a preconfigured\nfeed integration. We’ll add more over time.',
  buttonLabel = 'Sign in',
  autoPlay = false,
  onChange,
  onSignIn,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const count = cards.length;

  // The card on top, or the one on its way there; it keeps counting round the ring.
  const [top, setTop] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Which card is in the hand, and how far it has left: 2.25 is card 2, a quarter gone.
  const position = useSharedValue(0);
  // The place the stack behind it has moved up to.
  const rise = useSharedValue(0);
  // The place on the ring of colours the screen is at.
  const shade = useSharedValue(0);
  // Where the three are heading, and where they were when a finger took the card.
  const heading = useSharedValue(0);
  const grabbed = useSharedValue({ position: 0, rise: 0, shade: 0 });
  // Seconds, for the float and the shimmer.
  const clock = useSharedValue(0);
  const pressed = useSharedValue(0);

  const short = size.height < SHORT;
  const fontSize = short ? HEADLINE_SHORT : HEADLINE;
  const lineHeight = short ? HEADLINE_LINE_SHORT : HEADLINE_LINE;
  const cardWidth = Math.min(CARD_MAX, size.width - CARD_SIDE * 2);
  const ring = [...cards.map((card) => channels(card.color)), channels(cards[0].color)];

  useEffect(() => {
    if (reducedMotion) return;
    clock.set(withTiming(CLOCK, { duration: CLOCK * 1000, easing: Easing.linear, reduceMotion: NEVER }));
    return () => {
      cancelAnimation(clock);
      clock.set(0);
    };
  }, [clock, reducedMotion]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const landed = (to: number, byHand: boolean) => {
    if (to === top) return;
    setTop(to);
    if (byHand) tick();
    const index = lap(to, count);
    onChange?.(cards[index], index);
  };

  /** autoPlay's flick: the card in the hand leaves, the stack moves up and the colours change. */
  const flick = (to: number) => {
    landed(to, false);
    heading.set(to);
    if (reducedMotion) {
      position.set(to);
      rise.set(to);
      shade.set(withTiming(to, { duration: JUMP, easing: Easing.linear, reduceMotion: NEVER }));
      return;
    }
    position.set(withTiming(to, { duration: FLICK, easing: FLICK_EASING, reduceMotion: NEVER }));
    rise.set(withSpring(to, RISE_SPRING));
    shade.set(withTiming(to, { duration: SHIFT, easing: Easing.linear, reduceMotion: NEVER }));
  };

  /** Back to the first card; answers the place on the ring it goes to. */
  const rest = () => {
    const home = Math.round(position.get() / count) * count;
    flick(home);
    return home;
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ flick, rest });
  useEffect(() => {
    latest.current = { flick, rest };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // One round of the cards from place `home`, timed from `origin` so that rounds do not drift.
    const cycle = (origin: number, home: number) => {
      for (let k = 0; k < count; k++) after(origin + (k * STEP + REST) * 1000 - Date.now(), () => latest.current.flick(home + k + 1));
      after(origin + count * STEP * 1000 - Date.now(), () => cycle(origin + count * STEP * 1000, home + count));
    };
    if (touches === 0) cycle(Date.now(), 0);
    else after(USER_HOLD * 1000, () => cycle(Date.now(), latest.current.rest()));
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, count]);

  const pan = Gesture.Pan()
    .onBegin(() => {
      cancelAnimation(position);
      cancelAnimation(rise);
      cancelAnimation(shade);
      grabbed.set({ position: position.get(), rise: rise.get(), shade: shade.get() });
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      // To the left the card in the hand leaves; to the right the one before it comes back.
      const by = -event.translationX / LEAVE;
      const from = grabbed.get();
      const within = (value: number) => Math.min(heading.get() + 1, Math.max(heading.get() - 1, value));
      position.set(within(from.position + by));
      rise.set(within(from.rise + by));
      shade.set(within(from.shade + by));
    })
    .onFinalize((event, success) => {
      const by = success ? -event.translationX : 0;
      const speed = success ? -event.velocityX : 0;
      const to = heading.get() + (by > COMMIT || speed > FLING ? 1 : by < -COMMIT || speed < -FLING ? -1 : 0);
      heading.set(to);
      if (reducedMotion) {
        position.set(to);
        rise.set(to);
        shade.set(withTiming(to, { duration: JUMP, easing: Easing.linear, reduceMotion: NEVER }));
      } else {
        position.set(withSpring(to, { ...SNAP_SPRING, velocity: speed / LEAVE }));
        rise.set(withSpring(to, RISE_SPRING));
        shade.set(withTiming(to, { duration: Math.max(1, SHIFT * Math.min(1, Math.abs(to - shade.get()))), easing: Easing.linear, reduceMotion: NEVER }));
      }
      scheduleOnRN(landed, to, true);
      scheduleOnRN(touched);
    });

  const screenStyle = useAnimatedStyle(() => ({ backgroundColor: rgba(blend(ring, shade.get()), 1) }));
  const inkStyle = useAnimatedStyle(() => ({ color: rgba(inkOf(blend(ring, shade.get())), 1) }));
  const bodyStyle = useAnimatedStyle(() => ({ color: rgba(inkOf(blend(ring, shade.get())), BODY_ALPHA) }));
  const shimmerStyle = useAnimatedStyle(() => ({
    opacity: reducedMotion ? 1 : 1 - SHIMMER * (0.5 - 0.5 * Math.cos((clock.get() / WAVE) * 2 * Math.PI)),
  }));
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS_SCALE) * pressed.get() }] }));
  const press = (down: boolean) => pressed.set(withTiming(down ? 1 : 0, { duration: PRESS, reduceMotion: NEVER }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const type = { fontSize, lineHeight };
  const shown = cards[lap(top, count)];

  return (
    <Animated.View style={[styles.screen, { paddingBottom: Math.max(insets.bottom, 12) + BOTTOM }, screenStyle]} onLayout={onLayout}>
      <StatusBar style="light" />

      <View style={styles.headline} accessible accessibilityRole="header" accessibilityLabel={headline.join(' ')}>
        {headline.map((line) => {
          const at = highlight ? line.indexOf(highlight) : -1;
          if (at < 0) {
            return (
              <Animated.Text key={line} style={[styles.headlineText, type, inkStyle]} numberOfLines={1}>
                {line}
              </Animated.Text>
            );
          }
          return (
            <View key={line} style={styles.headlineRow}>
              <Animated.Text style={[styles.headlineText, type, inkStyle]}>{line.slice(0, at)}</Animated.Text>
              <Animated.Text style={[styles.headlineText, styles.highlight, type, shimmerStyle]}>{highlight}</Animated.Text>
              <Animated.Text style={[styles.headlineText, type, inkStyle]}>{line.slice(at + highlight.length)}</Animated.Text>
            </View>
          );
        })}
      </View>

      <Animated.Text style={[styles.body, bodyStyle]} numberOfLines={2}>
        {body}
      </Animated.Text>

      <GestureDetector gesture={pan}>
        <View
          style={[styles.stack, { width: cardWidth }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={shown.lines.join(' ')}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            touched();
            flick(top + (event.nativeEvent.actionName === 'increment' ? 1 : -1));
          }}
        >
          {cards.map((card, index) => (
            <Card key={card.lines.join(' ')} card={card} index={index} count={count} position={position} rise={rise} clock={clock} still={reducedMotion} />
          ))}
        </View>
      </GestureDetector>

      <Animated.View style={[styles.buttonSlot, pressStyle]}>
        <Pressable
          style={styles.button}
          accessibilityRole="button"
          onPressIn={() => press(true)}
          onPressOut={() => press(false)}
          onPress={() => {
            touched();
            tick();
            onSignIn?.();
          }}
        >
          <Text style={styles.buttonLabel} numberOfLines={1}>
            {buttonLabel}
          </Text>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  headline: {
    paddingHorizontal: SIDE,
  },
  headlineRow: {
    flexDirection: 'row',
  },
  headlineText: {
    fontWeight: '800',
    letterSpacing: -1.5,
    includeFontPadding: false,
  },
  highlight: {
    color: '#ffffff',
  },
  body: {
    marginTop: BODY_GAP,
    paddingHorizontal: SIDE,
    fontSize: 13,
    lineHeight: BODY_LINE,
    includeFontPadding: false,
  },
  stack: {
    marginTop: STACK_GAP,
    height: CARD_HEIGHT,
    alignSelf: 'center',
  },
  card: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  face: {
    flex: 1,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    paddingLeft: 16,
    paddingBottom: 14,
    justifyContent: 'flex-end',
  },
  glyphSlot: {
    position: 'absolute',
    right: 22,
    top: 12,
  },
  glyph: {
    width: GLYPH,
    height: GLYPH,
  },
  cardLine: {
    color: CARD_INK,
    fontSize: 23,
    lineHeight: 32,
    fontWeight: '700',
    letterSpacing: -0.4,
    includeFontPadding: false,
  },
  buttonSlot: {
    marginTop: BUTTON_GAP,
    alignSelf: 'center',
  },
  button: {
    width: BUTTON_WIDTH,
    height: BUTTON,
    borderRadius: 10,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 4, blurRadius: 10, color: 'rgba(0,0,0,0.15)' }],
  },
  buttonLabel: {
    color: CARD_INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
