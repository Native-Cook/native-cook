import { Canvas, Circle, Fill, Group, LinearGradient, RadialGradient, RoundedRect, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useEffectEvent, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, type StyleProp, StyleSheet, Text, TextInput, View, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

type Props = {
  /** Controlled text. Leave it out and the component keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Types the script, sends it, waits for the card to come back and starts over. */
  autoPlay?: boolean;
  assistant?: string;
  heading?: string;
  placeholder?: string;
  /** The question autoPlay types. */
  script?: string;
  /** Fill of the message bubble. */
  accent?: string;
  /** Called with the message when the arrow or the return key is pressed. */
  onSend?: (message: string) => void;
  onAdd?: () => void;
  onMic?: () => void;
  onAssistant?: () => void;
};

const WIDTH = 350;
const HEIGHT = 440;
const RADIUS = 32;
const MARGIN = 16;

// The card in the middle of the panel, and docked at its bottom as a capsule.
const CARD_X = 18;
const CARD_Y = 114;
const CARD_WIDTH = 314;
const CARD_HEIGHT = 200;
const CARD_RADIUS = 20;
const DOCK_X = 14;
const DOCK_Y = 374;
const DOCK_WIDTH = 322;
const DOCK_HEIGHT = 52;
const DOCK_RADIUS = 26;
// The springs carry the card a little past both ends, so its canvas is this much larger than the card at rest.
const OVERSHOOT = 8;
const GLASS_WIDTH = DOCK_WIDTH + OVERSHOOT;
const GLASS_HEIGHT = CARD_HEIGHT + OVERSHOOT;

// The field inside the resting card. Docked, it fills the capsule.
const FIELD_X = 16;
const FIELD_Y = 100;
const FIELD_WIDTH = 282;
const FIELD_HEIGHT = 84;
const FIELD_RADIUS = 12;
const HEADING_Y = 50;
const HEADING_GAP = 5;
/** Centre of the plus and of the action button, from the field's bottom corners. */
const ICON_INSET = 22;
// The square a button answers touches in.
const HIT = 36;

const FONT_SIZE = 15;
const LINE = 20;
const GAP = 4;
const TEXT_X = 14;
const TEXT_Y = 12;
const DOCK_TEXT_X = 50;
const TEXT_WIDTH = 222;
/** Room kept for the caret when a long sentence scrolls. */
const CARET_ROOM = 4;
const CARET_HEIGHT = 18;

const BUBBLE_TOP = 20;
const BUBBLE_MAX = 260;
const BUBBLE_PAD_X = 14;
const BUBBLE_PAD_Y = 8;
const BUBBLE_HEIGHT = LINE + BUBBLE_PAD_Y * 2;

const VIOLET = '#6a2bd9';
const MAGENTA = '#d63cff';
const TINT = '#a77bff';
const BUBBLE_EDGE = '#9a7bff';

// autoPlay (seconds). Keys land `KEY` apart on average, stretched by `RHYTHM` in turn.
const TYPE_AT = 0.8;
const KEY = 0.06;
const RHYTHM = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95];
/** From the last key to the tap on the arrow. */
const SEND_AFTER = 0.5;
const LOOP = 9;
/** After the field is left empty, the script waits this long before it starts again. */
const RESUME_AFTER = 3;

// What follows a send, counted from it.
/** The bubble stands over the words this long before it leaves, so the flight starts from there. */
const LIFT_OFF = 0.02;
const THINK_AFTER = 0.5;
const BACK_AFTER = 2.9;
/** From the card starting to rise to its heading starting to resolve. */
const REVEAL_AFTER = 0.35;

const BLINK = 0.53;
const PRESS = 0.12;
const FADE = 0.25;
const TINT_HOLD = 0.45;
const TINT_FADE = 0.4;
const PULSE = 0.45;
const PULSE_STAGGER = 0.15;

/** The heading resolves over `REVEAL` seconds; each letter takes `REVEAL_SPAN` of that. */
const REVEAL = 0.9;
const REVEAL_SPAN = 0.35;
const REVEAL_SHIFT = 6;

/** The bubble rises this far above a straight path, and swells by this much, half way through its flight. */
const FLY_ARC = 18;
const FLY_SCALE = 0.05;

const DOCK_SPRING = { mass: 1, stiffness: 150, damping: 19 };
const FLY_SPRING = { mass: 1, stiffness: 130, damping: 17 };
const SWAP_SPRING = { mass: 1, stiffness: 500, damping: 24 };
const POP_SPRING = { mass: 1, stiffness: 420, damping: 22 };

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
const PRESS_EASE = cubicBezier(0.23, 1, 0.32, 1);

type Blob = {
  x: number;
  y: number;
  rx: number;
  ry: number;
  /** Degrees its long axis is turned clockwise. */
  tilt: number;
  /** How far its centre swings each way. */
  dx: number;
  dy: number;
  /** Whole circles per loop, so the loop closes. */
  turns: number;
  phase: number;
  color: string;
  alpha: number;
};

const AURORA_BLOBS: Blob[] = [
  { x: 70, y: 70, rx: 250, ry: 135, tilt: -35, dx: 24, dy: 16, turns: 1, phase: 0, color: '#7a1fff', alpha: 0.9 },
  { x: 300, y: 350, rx: 240, ry: 125, tilt: -30, dx: -20, dy: 18, turns: 1, phase: 1.9, color: '#c81fff', alpha: 0.8 },
  { x: 280, y: 90, rx: 170, ry: 110, tilt: 25, dx: 16, dy: -20, turns: 2, phase: 4.1, color: '#4b1fd6', alpha: 0.65 },
  { x: 60, y: 410, rx: 170, ry: 90, tilt: 20, dx: 18, dy: 12, turns: 1, phase: 3.0, color: '#e040fb', alpha: 0.45 },
  { x: 175, y: 225, rx: 210, ry: 90, tilt: -40, dx: 14, dy: -14, turns: 1, phase: 5.2, color: '#5a14c8', alpha: 0.5 },
];

/** How a streak fades from its centre to its rim: steep at first, then a long soft tail. */
const AURORA_FALLOFF = [
  { at: 0, alpha: 1 },
  { at: 0.25, alpha: 0.72 },
  { at: 0.5, alpha: 0.4 },
  { at: 0.7, alpha: 0.16 },
  { at: 0.87, alpha: 0.04 },
  { at: 1, alpha: 0 },
];
const AURORA_STOPS = AURORA_FALLOFF.map((stop) => stop.at);

// Icons, as outlines in a 16 pt box (the chevron in 10 pt).
const CHEVRON_ICON = 'M2.5 3.75 L5 6.25 L7.5 3.75';
const PLUS_ICON = 'M8 3.25 L8 12.75 M3.25 8 L12.75 8';
const ARROW_ICON = 'M8 12.25 L8 3.75 M4.5 7.25 L8 3.75 L11.5 7.25';
const MIC_ICON =
  'M8 2 C9.1 2 10 2.9 10 4 L10 7.5 C10 8.6 9.1 9.5 8 9.5 C6.9 9.5 6 8.6 6 7.5 L6 4 C6 2.9 6.9 2 8 2 Z ' +
  'M4 7.5 C4 9.71 5.79 11.5 8 11.5 C10.21 11.5 12 9.71 12 7.5 M8 11.5 L8 13.75 M6 13.75 L10 13.75';

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** `#rrggbb` at `alpha`, as a colour both the canvas and a style take. */
function faded(hex: string, alpha: number) {
  const value = parseInt(hex.slice(1), 16);
  return `rgba(${value >> 16}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`;
}

/** A fade over `seconds`. It also runs under reduced motion, where the fades are all that is left. */
function easeOut(seconds: number) {
  return { duration: seconds * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
}

/** Where the sentence starts inside the panel while the card is at `p`. */
function textOrigin(p: number) {
  return {
    x: lerp(CARD_X, DOCK_X, p) + lerp(FIELD_X, 0, p) + lerp(TEXT_X, DOCK_TEXT_X, p),
    y: lerp(CARD_Y, DOCK_Y, p) + lerp(FIELD_Y, 0, p) + lerp(TEXT_Y, (DOCK_HEIGHT - LINE) / 2, p),
  };
}

// Where the sentence stands when it is sent. Nothing can be sent until the card is back, so that is always the resting card.
const FROM = textOrigin(0);

/** The heading's words with the position of their first letter among all letters. */
function headingWords(heading: string) {
  let start = 0;
  return heading
    .split(' ')
    .filter(Boolean)
    .map((word) => {
      const letters = Array.from(word);
      const entry = { start, letters };
      start += letters.length;
      return entry;
    });
}

function Streak({ blob, time }: { blob: Blob; time: SharedValue<number> }) {
  const transform = useDerivedValue(() => {
    const angle = (2 * Math.PI * blob.turns * time.get()) / LOOP + blob.phase;
    return [
      { translateX: blob.x + blob.dx * Math.sin(angle) },
      { translateY: blob.y + blob.dy * Math.cos(angle) },
      { rotate: (blob.tilt * Math.PI) / 180 },
      { scaleX: blob.rx },
      { scaleY: blob.ry },
    ];
  });

  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={1}>
        <RadialGradient
          c={vec(0, 0)}
          r={1}
          colors={AURORA_FALLOFF.map((stop) => faded(blob.color, blob.alpha * stop.alpha))}
          positions={AURORA_STOPS}
        />
      </Circle>
    </Group>
  );
}

/** Five soft streaks of violet and magenta, each circling slowly once or twice per loop. */
function Aurora({ time }: { time: SharedValue<number> }) {
  return (
    <View pointerEvents="none" style={styles.aurora}>
      <Canvas style={styles.auroraCanvas}>
        {AURORA_BLOBS.map((blob, index) => (
          <Streak key={index} blob={blob} time={time} />
        ))}
      </Canvas>
    </View>
  );
}

/**
 * The surfaces of the glass card at `dock` of the way from the middle of the panel (0) to its dock (1):
 * its two fills, the glow along its bottom edge, the field's fill and, over them all, the gradient
 * border. Drawn in one canvas so they keep one shape while the card changes size.
 */
function Glass({ dock }: { dock: SharedValue<number> }) {
  const width = useDerivedValue(() => lerp(CARD_WIDTH, DOCK_WIDTH, dock.get()));
  const height = useDerivedValue(() => lerp(CARD_HEIGHT, DOCK_HEIGHT, dock.get()));
  const radius = useDerivedValue(() => Math.max(0, lerp(CARD_RADIUS, DOCK_RADIUS, dock.get())));

  const shape = useDerivedValue(() => rrect(rect(0, 0, width.get(), height.get()), radius.get(), radius.get()));
  const glow = useDerivedValue(() => [
    { translateX: width.get() * 0.56 },
    { translateY: height.get() + 4 },
    { scaleX: width.get() * 0.62 },
    { scaleY: Math.min(72, height.get() * 0.62) },
  ]);
  const field = useDerivedValue(() => {
    const p = dock.get();
    const r = Math.max(0, lerp(FIELD_RADIUS, DOCK_RADIUS, p));
    return rrect(rect(lerp(FIELD_X, 0, p), lerp(FIELD_Y, 0, p), lerp(FIELD_WIDTH, DOCK_WIDTH, p), lerp(FIELD_HEIGHT, DOCK_HEIGHT, p)), r, r);
  });
  const fieldAlpha = useDerivedValue(() => lerp(0.16, 0.08, dock.get()));
  // The border lies inside the shape, so its centre line is half a point in.
  const border = useDerivedValue(() => {
    const r = Math.max(0, radius.get() - 0.5);
    return rrect(rect(0.5, 0.5, width.get() - 1, height.get() - 1), r, r);
  });
  const borderEnd = useDerivedValue(() => vec(0, height.get()));

  return (
    <View pointerEvents="none" style={styles.glass}>
      <Canvas style={styles.glassCanvas}>
        <Group clip={shape}>
          <Fill color="rgba(18, 14, 30, 0.4)" />
          <Fill color="rgba(255, 255, 255, 0.05)" />
          {/* A magenta glow along the bottom edge. */}
          <Group transform={glow}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={[faded(MAGENTA, 0.8), faded(MAGENTA, 0)]} />
            </Circle>
          </Group>
          <RoundedRect rect={field} color="#ffffff" opacity={fieldAlpha} />
        </Group>
        <RoundedRect rect={border} style="stroke" strokeWidth={1}>
          <LinearGradient start={vec(0, 0)} end={borderEnd} colors={[faded(VIOLET, 0.8), faded(MAGENTA, 0.95)]} />
        </RoundedRect>
      </Canvas>
    </View>
  );
}

function Icon({ d, size = 16, color, strokeWidth }: { d: string; size?: number; color: string; strokeWidth: number }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Path d={d} fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

type PressProps = {
  onPress?: () => void;
  accessibilityLabel?: string;
  style: StyleProp<ViewStyle>;
  children: ReactNode;
};

/** A button that gives a little under the finger. */
function Press({ onPress, accessibilityLabel, style, children }: PressProps) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={accessibilityLabel}>
      {({ pressed }) => (
        <Animated.View
          style={[
            style,
            {
              transform: [{ scale: pressed ? 0.94 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: PRESS_EASE,
            },
          ]}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

function Caret({ visible, blink, style }: { visible: boolean; blink: SharedValue<number>; style?: StyleProp<ViewStyle> }) {
  const pulse = useAnimatedStyle(() => ({ opacity: visible ? blink.get() : 0 }));
  return <Animated.View style={[styles.caret, style, pulse]} />;
}

type WordProps = {
  text: string;
  last: boolean;
  /** The whole sentence, handed to the last word only: a change means a key has just arrived. */
  stamp: string;
  caret: boolean;
  spaced: boolean;
  blink: SharedValue<number>;
};

/** One word of the sentence. The newest one is tinted, and carries the caret past its end. */
function Word({ text, last, stamp, caret, spaced, blink }: WordProps) {
  const tint = useSharedValue(last ? 1 : 0);

  useEffect(() => {
    if (last) {
      // The tint arrives at once, stays until the keys have stopped for a moment, and fades away.
      tint.set(1);
      tint.set(withDelay(TINT_HOLD * 1000, withTiming(0, easeOut(TINT_FADE)), ReduceMotion.Never));
    } else {
      tint.set(withTiming(0, easeOut(TINT_FADE)));
    }
  }, [last, stamp, tint]);

  const color = useAnimatedStyle(() => ({ color: interpolateColor(tint.get(), [0, 1], ['#ffffff', TINT]) }));

  return (
    <View>
      <Animated.Text style={[styles.text, color]} numberOfLines={1}>
        {text}
      </Animated.Text>
      {/* The caret hangs off the word without taking room in the line. */}
      {last ? <Caret visible={caret} blink={blink} style={[styles.caretAfter, { marginLeft: spaced ? GAP : 0.5 }]} /> : null}
    </View>
  );
}

type LetterProps = {
  glyph: string;
  /** Where in the reveal this letter starts to show, 0…1. */
  begin: number;
  reveal: SharedValue<number>;
  reducedMotion: boolean;
};

/** One letter of the heading: it fades and slides in a little after the one before. */
function Letter({ glyph, begin, reveal, reducedMotion }: LetterProps) {
  const style = useAnimatedStyle(() => {
    const p = reveal.get();
    const q = reducedMotion ? clamp01(p) : clamp01((p - begin) / REVEAL_SPAN);
    return { opacity: q, transform: [{ translateX: (q - 1) * REVEAL_SHIFT }] };
  });
  return <Animated.Text style={[styles.letter, style]}>{glyph}</Animated.Text>;
}

function Dot({ index, reducedMotion }: { index: number; reducedMotion: boolean }) {
  const beat = useSharedValue(0);

  useEffect(() => {
    if (reducedMotion) return;
    beat.set(
      withDelay(index * PULSE_STAGGER * 1000, withRepeat(withTiming(1, { duration: PULSE * 1000, easing: EASE_IN_OUT }), -1, true)),
    );
    return () => cancelAnimation(beat);
  }, [index, reducedMotion, beat]);

  const style = useAnimatedStyle(() =>
    reducedMotion
      ? { opacity: 1, transform: [{ translateY: 1 }] }
      : { opacity: lerp(0.35, 1, beat.get()), transform: [{ translateY: lerp(1, -2.5, beat.get()) }] },
  );
  return <Animated.View style={[styles.dot, style]} />;
}

type BubbleProps = {
  text: string;
  accent: string;
  /** The card is coming back: the bubble fades away. */
  leaving: boolean;
  reducedMotion: boolean;
  fly: SharedValue<number>;
  size: SharedValue<{ width: number; height: number }>;
};

/**
 * The sent message: it leaves the field as bare text and lands at the top right as a bubble. It is
 * mounted by the send itself, so it stands over the words in the frame the field empties.
 */
function Bubble({ text, accent, leaving, reducedMotion, fly, size }: BubbleProps) {
  // It appears in place of the words at once, and fades when the card comes back.
  const veil = useSharedValue(reducedMotion ? 0 : 1);

  useEffect(() => {
    if (leaving) veil.set(withTiming(0, easeOut(FADE)));
    else if (reducedMotion) veil.set(withTiming(1, easeOut(FADE)));
  }, [leaving, reducedMotion, veil]);

  const flight = useAnimatedStyle(() => {
    const p = fly.get();
    const lift = Math.sin(Math.PI * clamp01(p));
    return {
      opacity: veil.get(),
      transform: [
        { translateX: lerp(FROM.x - BUBBLE_PAD_X, WIDTH - MARGIN - size.get().width, p) },
        { translateY: lerp(FROM.y - BUBBLE_PAD_Y, BUBBLE_TOP, p) - lift * FLY_ARC },
        { scale: 1 + FLY_SCALE * lift },
      ],
    };
  });
  const fill = useAnimatedStyle(() => ({ opacity: clamp01((clamp01(fly.get()) - 0.15) / 0.6) }));
  const edge = useAnimatedStyle(() => ({ opacity: 0.7 * clamp01((clamp01(fly.get()) - 0.15) / 0.6) }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    size.set({ width, height });
  };

  return (
    <Animated.View pointerEvents="none" onLayout={onLayout} style={[styles.bubble, flight]}>
      <Animated.View style={[styles.bubbleFill, { backgroundColor: accent }, fill]} />
      <Animated.View style={[styles.bubbleFill, styles.bubbleEdge, edge]} />
      <Text style={styles.text}>{text}</Text>
    </Animated.View>
  );
}

/**
 * An assistant prompt on a glass card over a drifting aurora. Each word is tinted violet as it is
 * typed. On send the sentence lifts out of the field and flies up into a message bubble while the
 * card shrinks to its field and docks at the bottom of the panel; after a moment of "thinking" the
 * card rises back and its heading resolves again from left to right.
 */
export function DockingPromptCard({
  value: controlledValue,
  onChangeText,
  autoPlay = false,
  assistant = 'AI Assistant',
  heading = 'What are you working on?',
  placeholder = 'Ask me anything',
  script = 'Help me plan a product launch',
  accent = '#5b2be0',
  onSend,
  onAdd,
  onMic,
  onAssistant,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = controlledValue != null;
  const input = useRef<TextInput>(null);

  const [inner, setInner] = useState('');
  const [focused, setFocused] = useState(false);
  const [playing, setPlaying] = useState(autoPlay && !controlled);
  /** Counts sends and resets, to start and cancel what follows a send. */
  const [run, setRun] = useState(0);
  const [sent, setSent] = useState('');
  const [bubble, setBubble] = useState<'hidden' | 'shown' | 'leaving'>('hidden');
  /** A message is on its way: nothing else can be sent until the card is back. */
  const busy = useRef(false);
  /** Counts the times the card came back, to restart the wait before the script resumes. */
  const [rested, setRested] = useState(0);

  const value = controlledValue ?? inner;
  const words = value.split(' ').filter(Boolean);
  const empty = words.length === 0;
  const ready = value.trim().length > 0;
  const caret = focused || (playing && !empty);
  const spaced = value.endsWith(' ');

  const time = useSharedValue(0);
  // The card on its way from the middle of the panel (0) to its dock (1), the bubble from the field
  // (0) to the top right (1), and the heading from hidden (0) to resolved (1).
  const dock = useSharedValue(0);
  const fly = useSharedValue(0);
  const reveal = useSharedValue(1);
  const think = useSharedValue(0);
  /** The microphone (0) gives way to the arrow (1). */
  const swap = useSharedValue(0);
  /** The arrow is held down by the script. */
  const hold = useSharedValue(0);
  const hint = useSharedValue(1);
  const blink = useSharedValue(1);
  const bubbleSize = useSharedValue({ width: 120, height: BUBBLE_HEIGHT });

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
  }, !reducedMotion);

  const edit = (next: string) => {
    if (next === value) return;
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  const send = (byHand: boolean) => {
    const message = words.join(' ');
    input.current?.blur();
    if (message.length === 0 || busy.current) return;
    busy.current = true;
    if (byHand && process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onSend?.(message);
    // The bubble takes the place of the words before anything moves.
    setSent(message);
    setBubble('shown');
    hold.set(withTiming(0, easeOut(PRESS)));
    edit('');
    setRun((count) => count + 1);
  };

  /** Back to the resting card at once, whatever was going on. */
  const reset = () => {
    busy.current = false;
    hold.set(withTiming(0, easeOut(PRESS)));
    think.set(0);
    fly.set(0);
    dock.set(0);
    reveal.set(1);
    setBubble('hidden');
    setRun((count) => count + 1);
    edit('');
  };

  const typeScript = useEffectEvent((typed: string) => edit(typed));
  const sendScript = useEffectEvent(() => send(false));
  const resume = useEffectEvent(() => {
    if (value.length === 0 && !busy.current) setPlaying(true);
  });

  // autoPlay: one loop after another, every step at its own moment after the loop began.
  useEffect(() => {
    if (!playing) return;
    const keys = Array.from(script);
    let timers: ReturnType<typeof setTimeout>[] = [];
    const loop = (origin: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, origin + seconds * 1000 - performance.now())));
      };
      timers = [];
      let when = TYPE_AT;
      keys.forEach((_, index) => {
        const typed = keys.slice(0, index + 1).join('');
        at(when, () => typeScript(typed));
        when += KEY * RHYTHM[index % RHYTHM.length];
      });
      when += SEND_AFTER;
      at(when - PRESS, () => hold.set(withTiming(1, easeOut(PRESS))));
      at(when, () => sendScript());
      // The card is back, and the heading resolved, before the loop is over.
      const period = Math.max(LOOP, when + BACK_AFTER + FADE + REVEAL_AFTER + REVEAL + TYPE_AT);
      at(period, () => loop(origin + period * 1000));
    };
    loop(performance.now());
    return () => timers.forEach(clearTimeout);
  }, [playing, script, hold]);

  // What a send sets off: the flight and the docking, the thinking dots, and the way back.
  useEffect(() => {
    if (!busy.current) return;
    const at = (seconds: number, step: () => void) => setTimeout(step, seconds * 1000);
    const timers = [
      at(LIFT_OFF, () => {
        fly.set(reducedMotion ? 1 : withSpring(1, FLY_SPRING));
        dock.set(reducedMotion ? 1 : withSpring(1, DOCK_SPRING));
        reveal.set(withTiming(0, easeOut(FADE)));
      }),
      at(LIFT_OFF + THINK_AFTER, () => {
        think.set(reducedMotion ? withTiming(1, easeOut(FADE)) : withSpring(1, POP_SPRING));
      }),
      at(LIFT_OFF + BACK_AFTER, () => {
        think.set(withTiming(0, easeOut(FADE)));
        setBubble('leaving');
      }),
      at(LIFT_OFF + BACK_AFTER + FADE, () => {
        dock.set(reducedMotion ? 0 : withSpring(0, DOCK_SPRING));
        // Nobody sees the bubble go back: it is gone by now.
        fly.set(0);
        setBubble('hidden');
      }),
      at(LIFT_OFF + BACK_AFTER + FADE + REVEAL_AFTER, () => {
        reveal.set(
          withTiming(
            1,
            reducedMotion ? easeOut(FADE) : { duration: REVEAL * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never },
          ),
        );
        busy.current = false;
        setRested((count) => count + 1);
      }),
    ];
    return () => timers.forEach(clearTimeout);
  }, [run, reducedMotion, dock, fly, reveal, think]);

  // The script resumes once the field has been left empty, and the card is back, for a while.
  useEffect(() => {
    if (!autoPlay || controlled || focused || playing) return;
    const timer = setTimeout(() => resume(), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, controlled, focused, playing, rested]);

  // The caret stays solid while keys arrive and blinks once they stop.
  useEffect(() => {
    blink.set(withTiming(1, easeOut(0.08)));
    if (!focused && !playing) return;
    let on = true;
    const timer = setInterval(() => {
      on = !on;
      blink.set(withTiming(on ? 1 : 0, easeOut(0.08)));
    }, BLINK * 1000);
    return () => clearInterval(timer);
  }, [value, focused, playing, blink]);

  // The placeholder leaves at once (its slot hides it) and fades back in.
  useEffect(() => {
    hint.set(empty ? withTiming(1, easeOut(FADE)) : 0);
  }, [empty, hint]);

  useEffect(() => {
    swap.set(reducedMotion ? withTiming(ready ? 1 : 0, easeOut(0.15)) : withSpring(ready ? 1 : 0, SWAP_SPRING));
  }, [ready, reducedMotion, swap]);

  const cardStyle = useAnimatedStyle(() => {
    const p = dock.get();
    return { transform: [{ translateX: lerp(CARD_X, DOCK_X, p) }, { translateY: lerp(CARD_Y, DOCK_Y, p) }] };
  });
  const contentStyle = useAnimatedStyle(() => {
    const p = dock.get();
    return {
      width: lerp(CARD_WIDTH, DOCK_WIDTH, p),
      height: lerp(CARD_HEIGHT, DOCK_HEIGHT, p),
      borderRadius: Math.max(0, lerp(CARD_RADIUS, DOCK_RADIUS, p)),
    };
  });
  // The label and the heading are gone before the card is half way down.
  const fadeStyle = useAnimatedStyle(() => ({ opacity: clamp01(1 - dock.get() * 2.5) }));
  // The field: a two-row box in the card, a slim capsule that fills it once docked.
  const fieldStyle = useAnimatedStyle(() => {
    const p = dock.get();
    return {
      width: lerp(FIELD_WIDTH, DOCK_WIDTH, p),
      height: lerp(FIELD_HEIGHT, DOCK_HEIGHT, p),
      transform: [{ translateX: lerp(FIELD_X, 0, p) }, { translateY: lerp(FIELD_Y, 0, p) }],
    };
  });
  const sentenceStyle = useAnimatedStyle(() => {
    const p = dock.get();
    return {
      transform: [{ translateX: lerp(TEXT_X, DOCK_TEXT_X, p) }, { translateY: lerp(TEXT_Y, (DOCK_HEIGHT - LINE) / 2, p) }],
    };
  });
  const plusStyle = useAnimatedStyle(() => {
    const p = dock.get();
    const inset = lerp(ICON_INSET, DOCK_HEIGHT / 2, p);
    return {
      transform: [{ translateX: inset - HIT / 2 }, { translateY: lerp(FIELD_HEIGHT, DOCK_HEIGHT, p) - inset - HIT / 2 }],
    };
  });
  const actionStyle = useAnimatedStyle(() => {
    const p = dock.get();
    const inset = lerp(ICON_INSET, DOCK_HEIGHT / 2, p);
    return {
      transform: [
        { translateX: lerp(FIELD_WIDTH, DOCK_WIDTH, p) - inset - HIT / 2 },
        { translateY: lerp(FIELD_HEIGHT, DOCK_HEIGHT, p) - inset - HIT / 2 },
      ],
    };
  });
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() }));
  const micStyle = useAnimatedStyle(() => {
    const t = swap.get();
    return { opacity: clamp01(1 - t), transform: [{ scale: reducedMotion ? 1 : lerp(1, 0.5, t) }] };
  });
  const arrowStyle = useAnimatedStyle(() => {
    const t = swap.get();
    const held = lerp(1, 0.86, hold.get());
    return { opacity: clamp01(t), transform: [{ scale: reducedMotion ? held : lerp(0.5, held, t) }] };
  });
  const thinkStyle = useAnimatedStyle(() => {
    const t = think.get();
    return {
      opacity: clamp01(t),
      transform: [
        { translateX: MARGIN },
        { translateY: BUBBLE_TOP + bubbleSize.get().height + 12 },
        { scale: reducedMotion ? 1 : lerp(0.8, 1, t) },
      ],
    };
  });

  const onFocus = () => {
    setFocused(true);
    // A touch takes the field over from the script.
    if (playing) {
      setPlaying(false);
      reset();
    }
  };

  const title = headingWords(heading);
  const letters = Math.max(1, title.reduce((count, word) => count + word.letters.length, 0));

  return (
    <View style={styles.panel}>
      <Aurora time={time} />

      <Animated.View pointerEvents="box-none" style={[styles.card, cardStyle]}>
        <Glass dock={dock} />
        <Animated.View style={[styles.content, contentStyle]}>
          <Animated.View style={[styles.assistant, fadeStyle]}>
            <Press onPress={onAssistant} style={styles.assistantRow}>
              <Text style={styles.assistantLabel} numberOfLines={1}>
                {assistant}
              </Text>
              <Icon d={CHEVRON_ICON} size={10} color="#ffffff" strokeWidth={1.4} />
            </Press>
          </Animated.View>

          <Animated.View pointerEvents="none" accessible accessibilityLabel={heading} style={[styles.heading, fadeStyle]}>
            {title.map((word) => (
              <View key={word.start} style={styles.headingWord}>
                {word.letters.map((glyph, index) => (
                  <Letter
                    key={index}
                    glyph={glyph}
                    begin={((word.start + index) / letters) * (1 - REVEAL_SPAN)}
                    reveal={reveal}
                    reducedMotion={reducedMotion}
                  />
                ))}
              </View>
            ))}
          </Animated.View>

          <Animated.View style={[styles.field, fieldStyle]}>
            {/* A tap anywhere on the field goes to the text. */}
            <Pressable accessible={false} style={styles.fieldTap} onPress={() => input.current?.focus()} />

            {/* The words on one line, the caret after the last one, and the real field that owns the text. */}
            <Animated.View style={[styles.sentence, sentenceStyle]}>
              <View pointerEvents="none" style={[styles.hintSlot, { opacity: empty ? 1 : 0 }]}>
                <Animated.View style={hintStyle}>
                  <Text style={[styles.text, styles.hint]} numberOfLines={1}>
                    {placeholder}
                  </Text>
                  <Caret visible={caret && empty} blink={blink} />
                </Animated.View>
              </View>

              <View pointerEvents="none" style={styles.line}>
                {/* Held by its right edge: a sentence longer than the line slides left, so its end and the caret stay in view. */}
                <View style={styles.words}>
                  {words.map((word, index) => {
                    const last = index === words.length - 1;
                    return <Word key={index} text={word} last={last} stamp={last ? value : ''} caret={caret} spaced={spaced} blink={blink} />;
                  })}
                </View>
              </View>

              {/* The real field: it takes the keyboard, the words above are drawn from its text. */}
              <TextInput
                ref={input}
                value={value}
                onChangeText={edit}
                onFocus={onFocus}
                onBlur={() => setFocused(false)}
                onSubmitEditing={() => send(true)}
                returnKeyType="send"
                autoCorrect={false}
                caretHidden
                selection={{ start: value.length, end: value.length }}
                selectionColor="transparent"
                cursorColor="transparent"
                selectionHandleColor="transparent"
                underlineColorAndroid="transparent"
                accessibilityLabel={placeholder}
                style={styles.input}
              />
            </Animated.View>

            <Animated.View style={[styles.button, plusStyle]}>
              <Press onPress={onAdd} style={styles.buttonBox}>
                <Icon d={PLUS_ICON} color="rgba(255, 255, 255, 0.85)" strokeWidth={1.5} />
              </Press>
            </Animated.View>

            {/* A microphone while the field is empty, a white disc with an arrow once there is something to send. */}
            <Animated.View style={[styles.button, actionStyle]}>
              <Press
                onPress={() => {
                  if (ready) send(true);
                  else onMic?.();
                }}
                accessibilityLabel={ready ? 'Send' : 'Dictate'}
                style={styles.buttonBox}
              >
                <Animated.View style={[styles.mic, micStyle]}>
                  <Icon d={MIC_ICON} color="rgba(255, 255, 255, 0.85)" strokeWidth={1.4} />
                </Animated.View>
                <Animated.View style={[styles.arrow, arrowStyle]}>
                  <Icon d={ARROW_ICON} color="#1b1626" strokeWidth={1.8} />
                </Animated.View>
              </Press>
            </Animated.View>
          </Animated.View>
        </Animated.View>
      </Animated.View>

      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[styles.thinking, thinkStyle]}
      >
        {[0, 1, 2].map((index) => (
          <Dot key={index} index={index} reducedMotion={reducedMotion} />
        ))}
      </Animated.View>

      {bubble !== 'hidden' ? (
        <Bubble text={sent} accent={accent} leaving={bubble === 'leaving'} reducedMotion={reducedMotion} fly={fly} size={bubbleSize} />
      ) : null}

      <View pointerEvents="none" style={styles.rim} />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    backgroundColor: '#262626',
    overflow: 'hidden',
  },
  aurora: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  auroraCanvas: {
    width: WIDTH,
    height: HEIGHT,
  },
  rim: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: RADIUS,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.07)',
  },
  // Moved as a whole; the canvas and the content inside it take the card's size.
  card: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GLASS_WIDTH,
    height: GLASS_HEIGHT,
  },
  glass: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  glassCanvas: {
    width: GLASS_WIDTH,
    height: GLASS_HEIGHT,
  },
  // Clipped to the card's shape, like the canvas under it.
  content: {
    position: 'absolute',
    left: 0,
    top: 0,
    overflow: 'hidden',
  },
  assistant: {
    position: 'absolute',
    left: 18,
    top: 14,
  },
  assistantRow: {
    height: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  assistantLabel: {
    color: '#ffffff',
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '500',
  },
  heading: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: HEADING_Y,
    height: 28,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: HEADING_GAP,
  },
  headingWord: {
    flexDirection: 'row',
  },
  letter: {
    color: '#ffffff',
    fontSize: 20,
    lineHeight: 24,
  },
  field: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  fieldTap: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
  sentence: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: TEXT_WIDTH,
    height: LINE,
  },
  hintSlot: {
    position: 'absolute',
    left: 0,
    top: 0,
    maxWidth: TEXT_WIDTH,
    height: LINE,
  },
  hint: {
    color: 'rgba(255, 255, 255, 0.42)',
  },
  line: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: TEXT_WIDTH,
    height: LINE,
    overflow: 'hidden',
  },
  words: {
    position: 'absolute',
    right: CARET_ROOM,
    top: 0,
    minWidth: TEXT_WIDTH - CARET_ROOM,
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: GAP,
  },
  text: {
    color: '#ffffff',
    fontSize: FONT_SIZE,
    lineHeight: LINE,
  },
  caret: {
    position: 'absolute',
    left: 0,
    top: (LINE - CARET_HEIGHT) / 2,
    width: 1.5,
    height: CARET_HEIGHT,
    borderRadius: 0.75,
    backgroundColor: '#ffffff',
  },
  caretAfter: {
    left: '100%',
  },
  input: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: TEXT_WIDTH,
    height: LINE,
    padding: 0,
    fontSize: FONT_SIZE,
    color: 'transparent',
  },
  button: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: HIT,
    height: HIT,
  },
  buttonBox: {
    width: HIT,
    height: HIT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mic: {
    position: 'absolute',
    left: (HIT - 16) / 2,
    top: (HIT - 16) / 2,
  },
  arrow: {
    position: 'absolute',
    left: (HIT - 28) / 2,
    top: (HIT - 28) / 2,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  thinking: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 52,
    height: 30,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.14)',
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    // It pops out of its left end, where the reply will start.
    transformOrigin: 'left center',
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#ffffff',
  },
  bubble: {
    position: 'absolute',
    left: 0,
    top: 0,
    maxWidth: BUBBLE_MAX,
    paddingHorizontal: BUBBLE_PAD_X,
    paddingVertical: BUBBLE_PAD_Y,
  },
  bubbleFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: BUBBLE_HEIGHT / 2,
  },
  bubbleEdge: {
    borderWidth: 1,
    borderColor: BUBBLE_EDGE,
  },
});
