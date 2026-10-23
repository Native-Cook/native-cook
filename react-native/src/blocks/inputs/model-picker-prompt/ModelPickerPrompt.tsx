import { Canvas, LinearGradient, RadialGradient, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, memo, useEffect, useEffectEvent, useRef, useState } from 'react';
import {
  Platform,
  Pressable,
  type StyleProp,
  StyleSheet,
  Text,
  TextInput,
  type TextLayoutEvent,
  View,
  type ViewStyle,
} from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  type WithSpringConfig,
  cubicBezier,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { Arrow, Check, Chevron, ChipIcon, Mark, Ring, Waveform } from './icons';

export type ModelPickerPromptModel = {
  name: string;
  /** One line under the name in the menu. */
  detail: string;
};

type Props = {
  /** The field's value when the caller owns it; left undefined, the component keeps its own. */
  value?: string;
  /** Types `script`, sends it, waits for the answer and changes the model, by itself. Never opens the keyboard. */
  autoPlay?: boolean;
  name?: string;
  placeholder?: string;
  /** What autoPlay types. */
  script?: string;
  models?: ModelPickerPromptModel[];
  /** Index of the model selected at first. */
  initialModel?: number;
  /** Up to four shortcuts under the box. */
  chips?: string[];
  /** How long the stop ring turns after a send, in seconds, unless `onStop` ends it. */
  generateFor?: number;
  onChangeText?: (text: string) => void;
  /** Called with the trimmed message when the send button or the keyboard's send key is used. */
  onSend?: (text: string) => void;
  /** Called when the stop ring is tapped while generating. */
  onStop?: () => void;
  /** Called with the index of the model chosen in the menu. */
  onModelChange?: (index: number) => void;
  onChip?: (label: string) => void;
  onAttach?: () => void;
  onVoice?: () => void;
};

/** What autoPlay is pressing. */
type Press = 'send' | 'model';

const WIDTH = 350;
const HEIGHT = 500;
const GLOW_Y = 236;
const GLOW_RADIUS = 250;

const TITLE_Y = 150;
const TITLE_H = 48;
const TITLE_GAP = 9;
const NAME_SIZE = 38;
// Padding that keeps the name's glow inside its own text box, where it is drawn.
const NAME_GLOW = 24;
// While generating, the name rises and shrinks to make room for the answer.
const TITLE_LIFT = 42;
const TITLE_LIFT_SCALE = 0.86;

const ANSWER_TOP = 136;
const ANSWER_LINE = 10;
const ANSWER_GAP = 9;
const ANSWER_WIDTHS = [262, 290, 184];
// A line grows in from this share of its width.
const ANSWER_SCALE = 0.2;
// The band of light: where its centre starts and how far it travels, in line widths, and half its width.
const BAND_FROM = -0.4;
const BAND_TRAVEL = 1.8;
const BAND_HALF = 0.35;

const BOX_X = 14;
const BOX_TOP = 196;
const BOX_W = 322;
const BOX_H = 100;
const BOX_RADIUS = 18;
const PAD_X = 16;
const PAD_TOP = 14;
const TEXT_W = BOX_W - PAD_X * 2;
const ENTER_BLUR = 12;
const ENTER_SCALE = 0.92;

const FONT_SIZE = 16;
const PLACEHOLDER_SIZE = 15;
const LINE = 21;
const MAX_LINES = 2;
const CARET_H = 19;
// Width kept free at the end of a line for the drawn caret.
const CARET_ROOM = 3;

// Centre line and height of the row of controls along the bottom of the box.
const ROW_Y = 77;
const ROW_H = 30;
const BUTTON = 40;
const PLUS_X = 24;
const ACTION_X = 299;
const MODEL_RIGHT = 276;
// Room for the longest model name in the label.
const MODEL_NAME = 110;
const ROLL = 9;
const SEND_SIZE = 30;
const SEND_RADIUS = 9;
const RING_SIZE = 26;
const RING_ARC = 0.78;
const STOP_SIZE = 9;
const SWAP_SCALE = 0.4;
const PRESS_SCALE = 0.88;

const MAX_CHIPS = 4;
const CHIP_GAP = 14;
const CHIP_H = 30;
const CHIP_RADIUS = 9;
const CHIP_SPACING = 6;
const CHIP_FLY_X = 44;
const CHIP_FLY_Y = 30;

const MENU_GAP = 6;
const MENU_W = 262;
const MENU_ROW = 50;
const MENU_PAD = 6;
const MENU_RADIUS = 14;
const MENU_SCALE = 0.9;

// The name rising above the answer and coming back.
const LIFT: WithSpringConfig = { mass: 1, stiffness: 220, damping: 22, reduceMotion: ReduceMotion.Never };
// Waveform, send button and stop ring trading places.
const SWAP: WithSpringConfig = { mass: 1, stiffness: 420, damping: 24, reduceMotion: ReduceMotion.Never };
// A chip landing in the row, with a little overshoot.
const CHIP: WithSpringConfig = { mass: 1, stiffness: 300, damping: 20, reduceMotion: ReduceMotion.Never };
const MENU: WithSpringConfig = { mass: 1, stiffness: 420, damping: 28, reduceMotion: ReduceMotion.Never };
// What reduced motion puts in a spring's place.
const REDUCED_EASE = 0.2;

// The entrance, in seconds from the first appearance.
const ENTER_BOX_AT = 0.1;
const ENTER_BOX = 0.5;
const ENTER_CHIPS_AT = 0.3;
const ENTER_CHIP_EVERY = 0.12;

// The autoPlay loop, in seconds from its start.
const CARET_AT = 0.5;
const TYPE_AT = 0.8;
// The whole script is typed in this time; a short one is typed no slower than `SLOWEST_KEY` per letter.
const TYPE_SPAN = 1.5;
const SLOWEST_KEY = 0.06;
const PRESS_AT = 2.75;
const PRESS = 0.12;
const MENU_AT = 5.9;
const FLASH = 0.16;
const CHOOSE_AT = 7;
const LOOP = 8.6;

const FADE = 0.2;
const ROLL_FOR = 0.3;
const MENU_CLOSE = 0.16;
const GLOW_FADE = 0.6;
const ANSWER_AT = 0.15;
const ANSWER_STAGGER = 0.15;
const ANSWER_GROW = 0.35;
const SHIMMER_SWEEP = 1.2;
// Seconds per turn of the stop ring.
const RING_TURN = 1.6;
// A finger on a button, and a menu row lighting up.
const TOUCH = 0.13;
const ROW_LIGHT = 0.12;
// After the last touch, autoPlay waits this long.
const RESUME_AFTER = 3;

const JITTER = [0, 0.45, -0.2, 0.6, 0.1, -0.3, 0.75];

const CORAL = '#d97757';
const BOX = '#2f2d2b';
const INK = '#f3efea';
const PLACEHOLDER = '#8f8b86';
const CONTROL = '#a8a39d';
const CHIP_TEXT = '#cfcac4';
const CHIP_BORDER = '#55514d';
const MENU_FILL = '#262422';
const MENU_BORDER = '#3f3b38';
const ANSWER = 'rgba(255,255,255,0.13)';
const ANSWER_LIGHT = 'rgba(255,255,255,0.42)';

/** The glow's ember at `alpha`. */
const ember = (alpha: number) => `rgba(198,97,63,${alpha})`;

const SERIF = Platform.select({ ios: 'ui-serif', default: 'serif' });

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
const CSS_EASE_OUT = cubicBezier(0, 0, 0.58, 1);
const CSS_PRESS = cubicBezier(0.23, 1, 0.32, 1);
const ROLL_TIMING = { duration: ROLL_FOR * 1000, easing: Easing.bezier(0.4, 0, 0.2, 1), reduceMotion: ReduceMotion.Never };

const MODELS: ModelPickerPromptModel[] = [
  { name: 'Swift', detail: 'Fastest for everyday tasks' },
  { name: 'Balanced', detail: 'Smart and quick for most work' },
  { name: 'Deep', detail: 'Thinks longer on hard problems' },
];
const CHIPS = ['Create', 'Write', 'Learn', 'Code'];

// What the hidden copy of the script reports: the text it laid out, and its lines.
const UNMEASURED = { text: '', rows: [''] };

function ease(seconds: number) {
  return { duration: seconds * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
}

/** Reduced motion trades the springs for a short ease. */
function spring(to: number, config: WithSpringConfig, reduced: boolean) {
  return reduced ? withTiming(to, ease(REDUCED_EASE)) : withSpring(to, config);
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/**
 * Seconds after typing starts at which each letter lands: uneven like a person's typing (a beat
 * after a space, a pause after punctuation) but the same every time.
 */
function rhythm(letters: string[]) {
  const weights = letters.map((_, index) => {
    if (index === 0) return 0;
    const previous = letters[index - 1];
    let weight = 1 + JITTER[index % JITTER.length];
    if (previous === ' ') weight += 0.6;
    if (',.?!'.includes(previous)) weight += 3.2;
    return weight;
  });
  const total = weights.reduce((sum, weight) => sum + weight, 0);
  const unit = Math.min(TYPE_SPAN / Math.max(total, 1), SLOWEST_KEY);
  let at = 0;
  return weights.map((weight) => {
    at += weight * unit;
    return at;
  });
}

/** Solid while keys are landing; after half a second without one it blinks like the system caret. */
function blink(idle: number) {
  'worklet';
  if (idle <= 0.5) return 1;
  const t = (idle - 0.5) % 1;
  if (t < 0.12) return 1 - t / 0.12;
  if (t < 0.5) return 0;
  if (t < 0.62) return (t - 0.5) / 0.12;
  return 1;
}

/** Centre of the band of light, in line widths, `elapsed` seconds into the answer. */
function band(elapsed: number, reduced: boolean) {
  'worklet';
  const sweep = reduced ? 0.5 : (elapsed / SHIMMER_SWEEP) % 1;
  return BAND_FROM + BAND_TRAVEL * sweep;
}

/** One of the three buttons in the action slot: `shown` runs 0…1, past 1 while its spring overshoots. */
function swapStyle(shown: number, reduced: boolean) {
  'worklet';
  return {
    opacity: clamp01(shown),
    transform: [{ scale: reduced ? 1 : SWAP_SCALE + (1 - SWAP_SCALE) * shown }],
  };
}

/** The dip of a button autoPlay is pressing. */
function dip(on: boolean) {
  return {
    transform: [{ scale: on ? PRESS_SCALE : 1 }],
    transitionProperty: 'transform' as const,
    transitionDuration: PRESS * 1000,
    transitionTimingFunction: CSS_EASE_OUT,
  };
}

function impact() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** A shared value that goes after `target` on a spring. */
function useSpringTo(target: number, config: WithSpringConfig, reduced: boolean) {
  const value = useSharedValue(target);
  useEffect(() => {
    value.set(spring(target, config, reduced));
  }, [config, reduced, target, value]);
  return value;
}

/**
 * The warm vignette behind everything; a second, brighter layer comes up while the answer generates.
 * Its canvas is larger than the component: the glow fades out on the screen around it, not at its edges.
 */
function Glow({ bright }: { bright: SharedValue<number> }) {
  const size = GLOW_RADIUS * 2;
  const centre = vec(GLOW_RADIUS, GLOW_RADIUS);
  return (
    <View pointerEvents="none" style={styles.glow}>
      <Canvas style={styles.glowCanvas}>
        <Rect x={0} y={0} width={size} height={size}>
          <RadialGradient c={centre} r={GLOW_RADIUS} colors={[ember(0.34), ember(0.12), ember(0)]} />
        </Rect>
        <Rect x={0} y={0} width={size} height={size} opacity={bright}>
          <RadialGradient c={centre} r={GLOW_RADIUS} colors={[ember(0.3), ember(0)]} />
        </Rect>
      </Canvas>
    </View>
  );
}

type AnswerLineProps = {
  index: number;
  width: number;
  active: boolean;
  reduced: boolean;
  elapsed: SharedValue<number>;
};

/** One line of the faux answer: it grows in from the left, and a band of light keeps crossing it. */
function AnswerLine({ index, width, active, reduced, elapsed }: AnswerLineProps) {
  const grown = useSharedValue(0);

  useEffect(() => {
    grown.set(
      active
        ? withDelay((ANSWER_AT + index * ANSWER_STAGGER) * 1000, withTiming(1, ease(ANSWER_GROW)), ReduceMotion.Never)
        : withTiming(0, ease(FADE)),
    );
  }, [active, index, grown]);

  const style = useAnimatedStyle(() => ({
    opacity: grown.get(),
    transform: [{ scaleX: reduced ? 1 : ANSWER_SCALE + (1 - ANSWER_SCALE) * grown.get() }],
  }));
  const start = useDerivedValue(() => vec((band(elapsed.get(), reduced) - BAND_HALF) * width, 0));
  const end = useDerivedValue(() => vec((band(elapsed.get(), reduced) + BAND_HALF) * width, 0));

  return (
    <Animated.View style={[styles.answerLine, { width }, style]}>
      <Canvas style={styles.answerCanvas}>
        <RoundedRect x={0} y={0} width={width} height={ANSWER_LINE} r={ANSWER_LINE / 2}>
          <LinearGradient start={start} end={end} colors={[ANSWER, ANSWER_LIGHT, ANSWER]} />
        </RoundedRect>
      </Canvas>
    </Animated.View>
  );
}

type TapProps = {
  label: string;
  /** Read out after the label, for a button that shows a choice. */
  value?: string;
  onPress?: () => void;
  style: StyleProp<ViewStyle>;
  children: ReactNode;
};

/** A button whose face dips under the finger. */
function Tap({ label, value, onPress, style, children }: TapProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityValue={value === undefined ? undefined : { text: value }}
    >
      {({ pressed }) => (
        <Animated.View
          style={[
            style,
            {
              transform: [{ scale: pressed ? PRESS_SCALE : 1 }],
              transitionProperty: 'transform',
              transitionDuration: TOUCH * 1000,
              transitionTimingFunction: CSS_PRESS,
            },
          ]}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * The caret autoPlay draws after the last letter, since the field itself is not focused. Memoised:
 * the field renders with every letter, and the caret's clock should not start over with it.
 */
const Caret = memo(function Caret({ idle }: { idle: SharedValue<number> }) {
  useFrameCallback((frame) => {
    idle.set(idle.get() + (frame.timeSincePreviousFrame ?? 0) / 1000);
  });
  const style = useAnimatedStyle(() => ({ opacity: blink(idle.get()) }));
  return <Animated.View style={[styles.caret, style]} />;
});

type ModelNameProps = {
  name: string;
  active: boolean;
  reduced: boolean;
  /** 0…1 as the menu opens: the label brightens with it. */
  open: SharedValue<number>;
};

/**
 * One model's name in the label's slot. The names are stacked there, right-aligned: the chosen one
 * comes in from below while the one before it leaves upwards.
 */
function ModelName({ name, active, reduced, open }: ModelNameProps) {
  // 0 in place, 1 waiting below, -1 gone above.
  const place = useSharedValue(active ? 0 : -1);
  const wasActive = useRef(active);

  useEffect(() => {
    if (wasActive.current === active) return;
    wasActive.current = active;
    if (active) place.set(1);
    place.set(withTiming(active ? 0 : -1, ROLL_TIMING));
  }, [active, place]);

  const style = useAnimatedStyle(() => ({
    opacity: 1 - Math.abs(place.get()),
    color: interpolateColor(open.get(), [0, 1], [CONTROL, INK]),
    transform: [{ translateY: reduced ? 0 : ROLL * place.get() }],
  }));

  return (
    <View style={styles.nameLayer}>
      <Animated.Text style={[styles.modelName, style]} numberOfLines={1}>
        {name}
      </Animated.Text>
    </View>
  );
}

/** A square inside an open ring that turns slowly while the answer is on its way. */
function StopRing({ elapsed, reduced }: { elapsed: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => ({
    transform: [{ rotate: `${reduced ? 0 : (elapsed.get() / RING_TURN) * 360}deg` }],
  }));
  return (
    <>
      <Animated.View style={[styles.ring, style]}>
        <Ring size={RING_SIZE} share={RING_ARC} color={CORAL} />
      </Animated.View>
      <View style={styles.stopSquare} />
    </>
  );
}

type ChipProps = { index: number; label: string; reduced: boolean; onPress: () => void };

/** A shortcut under the box. It flies in from below-right on first appearance, later ones after earlier ones. */
function Chip({ index, label, reduced, onPress }: ChipProps) {
  const arrived = useSharedValue(0);

  useEffect(() => {
    arrived.set(
      reduced
        ? withTiming(1, ease(REDUCED_EASE))
        : withDelay((ENTER_CHIPS_AT + index * ENTER_CHIP_EVERY) * 1000, withSpring(1, CHIP), ReduceMotion.Never),
    );
  }, [arrived, index, reduced]);

  const style = useAnimatedStyle(() => {
    const away = reduced ? 0 : 1 - arrived.get();
    return {
      opacity: clamp01(arrived.get()),
      transform: [{ translateX: CHIP_FLY_X * away }, { translateY: CHIP_FLY_Y * away }],
    };
  });

  return (
    <Animated.View style={style}>
      <Tap label={label} onPress={onPress} style={styles.chip}>
        <ChipIcon kind={index} color={CONTROL} />
        <Text style={styles.chipText} numberOfLines={1}>
          {label}
        </Text>
      </Tap>
    </Animated.View>
  );
}

/**
 * Dark prompt box of an assistant, glowing on a warm vignette under its name. The box holds the
 * text, a plus, a model picker and one button that changes with the state: a waveform while the
 * field is empty, a coral send button once there is text, and a stop ring while the answer is
 * "generating" (the name lifts and three lines of a faux answer shimmer in under it). The model
 * label opens a small menu of models; choosing one closes it and rolls the label.
 */
export function ModelPickerPrompt({
  value,
  autoPlay = false,
  name = 'Atlas',
  placeholder = 'How can I help you today?',
  script = 'Plan three days in Lisbon in May',
  models = MODELS,
  initialModel = 0,
  chips = CHIPS,
  generateFor = 2.4,
  onChangeText,
  onSend,
  onStop,
  onModelChange,
  onChip,
  onAttach,
  onVoice,
}: Props) {
  const reducedMotion = useReducedMotion();
  const input = useRef<TextInput>(null);

  const [draft, setDraft] = useState('');
  // What autoPlay has typed so far; the caller's value is never touched by the script.
  const [typed, setTyped] = useState('');
  // True while autoPlay owns the component.
  const [playing, setPlaying] = useState(autoPlay);
  const [caretOn, setCaretOn] = useState(false);
  // The sent text is fading out of the field.
  const [sending, setSending] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [model, setModel] = useState<number | null>(null);
  // The menu row autoPlay lights before choosing it.
  const [flashed, setFlashed] = useState<number | null>(null);
  const [pressed, setPressed] = useState<Press | null>(null);
  const [focused, setFocused] = useState(false);
  // Lines the keyboard's text takes, measured from the field itself.
  const [fieldLines, setFieldLines] = useState(1);
  const [measured, setMeasured] = useState(UNMEASURED);
  const [seen, setSeen] = useState({ autoPlay, script });

  const reset = () => {
    setTyped('');
    setCaretOn(false);
    setPressed(null);
    setFlashed(null);
    setMenuOpen(false);
    setSending(false);
    setGenerating(false);
  };

  // A change of `autoPlay`, or a new script, starts over from rest.
  if (seen.autoPlay !== autoPlay || seen.script !== script) {
    setSeen({ autoPlay, script });
    if (seen.autoPlay !== autoPlay) setPlaying(autoPlay);
    reset();
  }

  const current = value ?? draft;
  const shown = playing ? typed : current;
  const hasText = shown !== '';
  const selected = Math.min(Math.max(model ?? initialModel, 0), Math.max(models.length - 1, 0));
  const mode = generating ? 2 : hasText ? 1 : 0;
  // The script's text line by line, so the drawn caret can follow the last one. The measurement
  // arrives a frame after a letter; one left over from an earlier text is not shown.
  const rows = typed.startsWith(measured.text) ? measured.rows.slice(-MAX_LINES) : UNMEASURED.rows;
  // Nothing is going on that autoPlay would interrupt.
  const atRest = !focused && current === '' && !generating && !menuOpen;

  const entered = useSharedValue(reducedMotion ? 1 : 0);
  const bright = useSharedValue(0);
  const lift = useSpringTo(generating ? 1 : 0, LIFT, reducedMotion);
  const wave = useSpringTo(mode === 0 ? 1 : 0, SWAP, reducedMotion);
  const arrow = useSpringTo(mode === 1 ? 1 : 0, SWAP, reducedMotion);
  const ring = useSpringTo(mode === 2 ? 1 : 0, SWAP, reducedMotion);
  const menu = useSharedValue(0);
  // 0…1 as the menu opens: the label brightens and its chevron turns over.
  const open = useSharedValue(0);
  const field = useSharedValue(playing ? 0 : 1);
  const hint = useSharedValue(1);
  // Seconds since the script's last key landed.
  const idle = useSharedValue(0);
  // Seconds since the answer started, on a clock that only runs while it generates.
  const elapsed = useSharedValue(0);
  const startedAt = useSharedValue(-1);

  const clock = useFrameCallback((frame) => {
    if (startedAt.get() < 0) startedAt.set(frame.timestamp);
    elapsed.set((frame.timestamp - startedAt.get()) / 1000);
  }, false);

  useEffect(() => {
    clock.setActive(generating && !reducedMotion);
  }, [clock, generating, reducedMotion]);

  // The box fades in from a blur while it scales up.
  useEffect(() => {
    entered.set(reducedMotion ? 1 : withDelay(ENTER_BOX_AT * 1000, withTiming(1, ease(ENTER_BOX)), ReduceMotion.Never));
  }, [entered, reducedMotion]);

  useEffect(() => {
    bright.set(withTiming(generating ? 1 : 0, { duration: GLOW_FADE * 1000, easing: EASE_IN_OUT, reduceMotion: ReduceMotion.Never }));
  }, [bright, generating]);

  useEffect(() => {
    menu.set(menuOpen ? spring(1, MENU, reducedMotion) : withTiming(0, ease(MENU_CLOSE)));
  }, [menu, menuOpen, reducedMotion]);

  const lastSelected = useRef(selected);
  useEffect(() => {
    // Closing on a new model, the label dims and the chevron turns back in step with the roll.
    const rolled = lastSelected.current !== selected;
    lastSelected.current = selected;
    open.set(withTiming(menuOpen ? 1 : 0, rolled ? ROLL_TIMING : ease(FADE)));
  }, [menuOpen, open, selected]);

  useEffect(() => {
    // The sent text fades out; once it is cleared the field is back at once.
    field.set(playing ? 0 : sending ? withTiming(0, ease(FADE)) : 1);
  }, [field, playing, sending]);

  const hadText = useRef(hasText);
  useEffect(() => {
    // The placeholder fades with the text, but makes way for the script's caret at once.
    const to = hasText || caretOn ? 0 : 1;
    hint.set(hadText.current === hasText ? to : withTiming(to, ease(FADE)));
    hadText.current = hasText;
  }, [caretOn, hasText, hint]);

  const change = (next: string) => {
    if (value === undefined) setDraft(next);
    onChangeText?.(next);
  };

  /** The sent text has faded: take it out of the field. */
  const clearSent = () => {
    if (playing) setTyped('');
    else change('');
    setSending(false);
  };

  const send = () => {
    const message = shown.trim();
    if (generating || sending || message === '') return;
    if (!playing) {
      impact();
      onSend?.(message);
    }
    setSending(true);
    setCaretOn(false);
    startedAt.set(-1);
    elapsed.set(0);
    setGenerating(true);
  };

  const stop = () => {
    if (!generating) return;
    impact();
    onStop?.();
    // A stop that lands while the sent text is still fading must not leave it behind, unseen.
    if (sending) clearSent();
    setGenerating(false);
  };

  const choose = (index: number) => {
    tick();
    if (index !== selected) onModelChange?.(index);
    setModel(index);
    setFlashed(null);
    setMenuOpen(false);
  };

  /** A touch stops the script and leaves the box at rest. */
  const takeOver = () => {
    if (!playing) return;
    setPlaying(false);
    reset();
  };

  const faded = useEffectEvent(clearSent);

  // The sent text fades and is cleared; the answer "generates" for a while, then the box is at rest again.
  useEffect(() => {
    if (!generating) return;
    const fade = setTimeout(() => faded(), FADE * 1000);
    const done = setTimeout(() => setGenerating(false), Math.max(FADE, generateFor) * 1000);
    return () => {
      clearTimeout(fade);
      clearTimeout(done);
    };
  }, [generating, generateFor]);

  const pressSend = useEffectEvent(send);
  // The second model, then back to the first on the next loop: two loops end where they started.
  const otherModel = useEffectEvent(() => (selected === 0 && models.length > 1 ? 1 : 0));

  // The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays `LOOP`.
  useEffect(() => {
    if (!playing) return;
    const letters = Array.from(script);
    let target = 0;
    const steps = [
      {
        at: CARET_AT,
        run: () => {
          idle.set(0);
          setCaretOn(true);
        },
      },
      ...rhythm(letters).map((at, index) => ({
        at: TYPE_AT + at,
        run: () => {
          setTyped(letters.slice(0, index + 1).join(''));
          idle.set(0);
        },
      })),
      { at: PRESS_AT, run: () => setPressed('send') },
      {
        at: PRESS_AT + PRESS,
        run: () => {
          setPressed(null);
          pressSend();
        },
      },
      { at: MENU_AT - PRESS, run: () => setPressed('model') },
      {
        at: MENU_AT,
        run: () => {
          setPressed(null);
          target = otherModel();
          setMenuOpen(true);
        },
      },
      { at: CHOOSE_AT - FLASH, run: () => setFlashed(target) },
      {
        at: CHOOSE_AT,
        run: () => {
          setModel(target);
          setFlashed(null);
          setMenuOpen(false);
        },
      },
    ];

    let timer: ReturnType<typeof setTimeout>;
    const wait = (start: number, index: number) => {
      const over = index === steps.length;
      const due = start + (over ? LOOP : steps[index].at) * 1000;
      timer = setTimeout(
        () => {
          if (over) {
            wait(due, 0);
          } else {
            steps[index].run();
            wait(start, index + 1);
          }
        },
        Math.max(0, due - performance.now()),
      );
    };
    wait(performance.now(), 0);
    return () => clearTimeout(timer);
  }, [playing, script, idle]);

  // autoPlay comes back once nothing has been touched for a while and the box is at rest.
  useEffect(() => {
    if (!autoPlay || playing || !atRest) return;
    const timer = setTimeout(() => setPlaying(true), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, playing, atRest]);

  const onMeasure = (event: TextLayoutEvent) => {
    const texts = event.nativeEvent.lines.map((line) => line.text);
    const text = texts.join('');
    // A text that ends in a line break has one more, empty line, which is not always reported.
    if (texts.length === 0 || texts[texts.length - 1].endsWith('\n')) texts.push('');
    setMeasured({ text, rows: texts.map((row) => row.replace(/\n$/, '')) });
  };

  const titleStyle = useAnimatedStyle(() => ({
    transform: [
      { translateY: -TITLE_LIFT * lift.get() },
      { scale: reducedMotion ? 1 : 1 - (1 - TITLE_LIFT_SCALE) * lift.get() },
    ],
  }));
  const boxStyle = useAnimatedStyle(() => {
    const blur = ENTER_BLUR * (1 - entered.get());
    return {
      opacity: entered.get(),
      transform: [{ scale: ENTER_SCALE + (1 - ENTER_SCALE) * entered.get() }],
      // No filter at all once the box has arrived, so the field is not left inside an effect layer.
      filter: blur > 0 ? [{ blur }] : [],
    };
  });
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() }));
  const fieldStyle = useAnimatedStyle(() => ({ opacity: field.get() }));
  const waveStyle = useAnimatedStyle(() => swapStyle(wave.get(), reducedMotion));
  const arrowStyle = useAnimatedStyle(() => swapStyle(arrow.get(), reducedMotion));
  const ringStyle = useAnimatedStyle(() => swapStyle(ring.get(), reducedMotion));
  const chevronStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${180 * open.get()}deg` }] }));
  const litStyle = useAnimatedStyle(() => ({ opacity: open.get() }));
  const menuStyle = useAnimatedStyle(() => ({
    opacity: clamp01(menu.get()),
    transform: [{ scale: reducedMotion ? 1 : MENU_SCALE + (1 - MENU_SCALE) * menu.get() }],
  }));

  return (
    <View style={styles.root}>
      <Glow bright={bright} />

      <Animated.View style={[styles.title, titleStyle]} accessible accessibilityLabel={name}>
        <Mark color={CORAL} />
        <Text style={styles.name} numberOfLines={1}>
          {name}
        </Text>
      </Animated.View>

      <View pointerEvents="none" style={styles.answer}>
        {ANSWER_WIDTHS.map((width, index) => (
          <AnswerLine key={index} index={index} width={width} active={generating} reduced={reducedMotion} elapsed={elapsed} />
        ))}
      </View>

      <Animated.View style={[styles.box, boxStyle]}>
        <View style={styles.editor}>
          <Animated.Text style={[styles.placeholder, hintStyle]} numberOfLines={1}>
            {placeholder}
          </Animated.Text>
          <Animated.View style={fieldStyle}>
            <TextInput
              ref={input}
              style={[styles.text, styles.field]}
              value={current}
              onChangeText={change}
              multiline
              scrollEnabled={fieldLines > MAX_LINES}
              submitBehavior="submit"
              returnKeyType="send"
              onSubmitEditing={send}
              onContentSizeChange={(event) => setFieldLines(Math.max(1, Math.round(event.nativeEvent.contentSize.height / LINE)))}
              onFocus={() => {
                setFocused(true);
                takeOver();
              }}
              onBlur={() => setFocused(false)}
              selectionColor={CORAL}
              cursorColor={CORAL}
              underlineColorAndroid="transparent"
            />
          </Animated.View>
          {playing && (
            <>
              {/* A hidden copy of the script's text at the field's width: where it breaks into lines. */}
              <Text
                style={[styles.text, styles.measure]}
                onTextLayout={onMeasure}
                accessible={false}
                importantForAccessibility="no"
                pointerEvents="none"
              >
                {typed}
              </Text>
              <Animated.View
                pointerEvents="none"
                style={[
                  styles.mirror,
                  {
                    opacity: sending ? 0 : 1,
                    transitionProperty: 'opacity',
                    transitionDuration: FADE * 1000,
                    transitionTimingFunction: CSS_EASE_OUT,
                  },
                ]}
              >
                {rows.map((row, index) => (
                  <View key={index} style={styles.row}>
                    <Text style={styles.text} numberOfLines={1} ellipsizeMode="clip">
                      {row}
                    </Text>
                    {caretOn && index === rows.length - 1 && <Caret idle={idle} />}
                  </View>
                ))}
              </Animated.View>
            </>
          )}
        </View>

        <View style={styles.plus}>
          <Tap label="Attach" onPress={onAttach} style={styles.face}>
            <View style={styles.plusH} />
            <View style={styles.plusV} />
          </Tap>
        </View>

        <Animated.View style={[styles.model, dip(pressed === 'model')]}>
          <Tap
            label="Model"
            value={models[selected]?.name ?? ''}
            onPress={() => setMenuOpen((isOpen) => !isOpen)}
            style={styles.modelFace}
          >
            {/* A fixed slot: the name on its way out stays right-aligned beside the one coming in. */}
            <View style={styles.nameSlot}>
              {models.map((item, index) => (
                <ModelName key={index} name={item.name} active={index === selected} reduced={reducedMotion} open={open} />
              ))}
            </View>
            <Animated.View style={chevronStyle}>
              <Chevron color={CONTROL} />
              <Animated.View style={[styles.lit, litStyle]}>
                <Chevron color={INK} />
              </Animated.View>
            </Animated.View>
          </Tap>
        </Animated.View>

        {/* One slot, three buttons: the waveform, the send button and the stop ring trade places. */}
        <Animated.View style={[styles.action, waveStyle]} pointerEvents={mode === 0 ? 'auto' : 'none'}>
          <Tap label="Dictate" onPress={onVoice} style={styles.face}>
            <Waveform color={CONTROL} />
          </Tap>
        </Animated.View>
        <Animated.View style={[styles.action, arrowStyle]} pointerEvents={mode === 1 ? 'auto' : 'none'}>
          <Animated.View style={dip(pressed === 'send')}>
            <Tap label="Send" onPress={send} style={styles.face}>
              <View style={styles.send}>
                <Arrow color="#ffffff" />
              </View>
            </Tap>
          </Animated.View>
        </Animated.View>
        <Animated.View style={[styles.action, ringStyle]} pointerEvents={mode === 2 ? 'auto' : 'none'}>
          <Tap label="Stop" onPress={stop} style={styles.face}>
            <StopRing elapsed={elapsed} reduced={reducedMotion} />
          </Tap>
        </Animated.View>

        <View pointerEvents="none" style={styles.boxBorder} />
      </Animated.View>

      <View style={styles.chips}>
        {chips.slice(0, MAX_CHIPS).map((label, index) => (
          <Chip key={index} index={index} label={label} reduced={reducedMotion} onPress={() => onChip?.(label)} />
        ))}
      </View>

      {/* A touch outside the menu closes it. */}
      {menuOpen && <Pressable style={styles.cover} accessible={false} onPress={() => setMenuOpen(false)} />}

      <Animated.View style={[styles.menu, menuStyle]} pointerEvents={menuOpen ? 'auto' : 'none'}>
        {models.map((item, index) => (
          <Pressable key={index} style={styles.menuRow} onPress={() => choose(index)} accessibilityRole="button">
            {({ pressed: held }) => (
              <>
                {/* A row lights up while it is pressed, or when autoPlay is about to choose it. */}
                <Animated.View
                  style={[
                    styles.rowLight,
                    {
                      opacity: held || flashed === index ? 1 : 0,
                      transitionProperty: 'opacity',
                      transitionDuration: ROW_LIGHT * 1000,
                      transitionTimingFunction: CSS_EASE_OUT,
                    },
                  ]}
                />
                <View style={styles.rowText}>
                  <Text style={styles.rowName} numberOfLines={1}>
                    {item.name}
                  </Text>
                  <Text style={styles.rowDetail} numberOfLines={1}>
                    {item.detail}
                  </Text>
                </View>
                <Animated.View
                  style={{
                    opacity: index === selected ? 1 : 0,
                    transitionProperty: 'opacity',
                    transitionDuration: MENU_CLOSE * 1000,
                    transitionTimingFunction: CSS_EASE_OUT,
                  }}
                >
                  <Check color={CORAL} />
                </Animated.View>
              </>
            )}
          </Pressable>
        ))}
      </Animated.View>

      {/* While autoPlay runs, a touch stops it; on the text it also hands the field to the keyboard. */}
      {playing && (
        <Pressable
          style={styles.cover}
          accessible={false}
          onPress={(event) => {
            const { locationX, locationY } = event.nativeEvent;
            takeOver();
            const onText =
              locationX >= BOX_X && locationX < BOX_X + BOX_W && locationY >= BOX_TOP && locationY < BOX_TOP + BOX_H - ROW_H;
            if (onText) input.current?.focus();
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  // A square around the glow's centre that overflows the component on every side but the bottom.
  glow: {
    position: 'absolute',
    left: WIDTH / 2 - GLOW_RADIUS,
    top: GLOW_Y - GLOW_RADIUS,
  },
  glowCanvas: {
    width: GLOW_RADIUS * 2,
    height: GLOW_RADIUS * 2,
  },
  cover: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
  title: {
    position: 'absolute',
    left: 0,
    top: TITLE_Y - TITLE_H / 2,
    width: WIDTH,
    height: TITLE_H,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: TITLE_GAP,
  },
  name: {
    color: '#ffffff',
    fontFamily: SERIF,
    fontSize: NAME_SIZE,
    fontWeight: '500',
    textShadowColor: 'rgba(255,255,255,0.35)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 20,
    padding: NAME_GLOW,
    margin: -NAME_GLOW,
  },
  answer: {
    position: 'absolute',
    left: BOX_X + PAD_X,
    top: ANSWER_TOP,
    gap: ANSWER_GAP,
  },
  answerLine: {
    height: ANSWER_LINE,
    transformOrigin: 'left center',
  },
  answerCanvas: {
    flex: 1,
  },
  box: {
    position: 'absolute',
    left: BOX_X,
    top: BOX_TOP,
    width: BOX_W,
    height: BOX_H,
    borderRadius: BOX_RADIUS,
    backgroundColor: BOX,
    boxShadow: '0 0 32px rgba(217,119,87,0.22)',
  },
  // The hairline lies over the box, so it takes no room from what the box holds.
  boxBorder: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: BOX_RADIUS,
    borderWidth: 1,
    borderColor: 'rgba(217,119,87,0.3)',
  },
  editor: {
    position: 'absolute',
    left: PAD_X,
    top: PAD_TOP,
    width: TEXT_W,
  },
  text: {
    color: INK,
    fontFamily: SERIF,
    fontSize: FONT_SIZE,
    lineHeight: LINE,
    includeFontPadding: false,
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: TEXT_W,
    color: PLACEHOLDER,
    fontSize: PLACEHOLDER_SIZE,
    lineHeight: LINE,
    includeFontPadding: false,
  },
  field: {
    maxHeight: MAX_LINES * LINE,
    padding: 0,
    // iOS gives a multiline field 5 points of top padding unless this edge is named.
    paddingTop: 0,
    textAlignVertical: 'top',
  },
  measure: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: TEXT_W - CARET_ROOM,
    opacity: 0,
  },
  // Wider than any line, so a space that hangs past the end of one is not cut off.
  mirror: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BOX_W,
  },
  row: {
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  caret: {
    width: 2,
    height: CARET_H,
    borderRadius: 1,
    backgroundColor: CORAL,
  },
  plus: {
    position: 'absolute',
    left: PLUS_X - BUTTON / 2,
    top: ROW_Y - BUTTON / 2,
  },
  action: {
    position: 'absolute',
    left: ACTION_X - BUTTON / 2,
    top: ROW_Y - BUTTON / 2,
  },
  face: {
    width: BUTTON,
    height: BUTTON,
    alignItems: 'center',
    justifyContent: 'center',
  },
  plusH: {
    position: 'absolute',
    width: 15,
    height: 1.6,
    borderRadius: 0.8,
    backgroundColor: CONTROL,
  },
  plusV: {
    position: 'absolute',
    width: 1.6,
    height: 15,
    borderRadius: 0.8,
    backgroundColor: CONTROL,
  },
  // The label ends at `MODEL_RIGHT`, and autoPlay's press shrinks it towards that end.
  model: {
    position: 'absolute',
    right: BOX_W - MODEL_RIGHT,
    top: ROW_Y - ROW_H / 2,
    transformOrigin: 'right center',
  },
  modelFace: {
    height: ROW_H,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  nameSlot: {
    width: MODEL_NAME,
    height: ROW_H,
    overflow: 'hidden',
  },
  // Wider than the slot, so a name too long for it is cut on the left rather than squeezed.
  nameLayer: {
    position: 'absolute',
    right: 0,
    top: 0,
    width: WIDTH,
    height: ROW_H,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  modelName: {
    fontSize: 13,
    fontWeight: '500',
    includeFontPadding: false,
  },
  lit: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  send: {
    width: SEND_SIZE,
    height: SEND_SIZE,
    borderRadius: SEND_RADIUS,
    backgroundColor: CORAL,
  },
  ring: {
    position: 'absolute',
  },
  stopSquare: {
    width: STOP_SIZE,
    height: STOP_SIZE,
    borderRadius: 2.5,
    backgroundColor: CORAL,
  },
  chips: {
    position: 'absolute',
    left: 0,
    top: BOX_TOP + BOX_H + CHIP_GAP,
    width: WIDTH,
    height: CHIP_H,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: CHIP_SPACING,
  },
  // The outline is drawn inside the chip: its point comes off the 10 points of padding.
  chip: {
    height: CHIP_H,
    paddingHorizontal: 9,
    borderRadius: CHIP_RADIUS,
    borderWidth: 1,
    borderColor: CHIP_BORDER,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  chipText: {
    color: CHIP_TEXT,
    fontSize: 13,
    includeFontPadding: false,
  },
  // Under the box, its right edge on the box's; it opens out of that corner.
  menu: {
    position: 'absolute',
    left: BOX_X + BOX_W - MENU_W,
    top: BOX_TOP + BOX_H + MENU_GAP,
    width: MENU_W,
    padding: MENU_PAD - 1,
    borderRadius: MENU_RADIUS,
    borderWidth: 1,
    borderColor: MENU_BORDER,
    backgroundColor: MENU_FILL,
    boxShadow: '0 12px 36px rgba(0,0,0,0.5)',
    transformOrigin: 'right top',
  },
  menuRow: {
    height: MENU_ROW,
    paddingHorizontal: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  rowLight: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: 9,
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  rowName: {
    color: INK,
    fontSize: 14,
    fontWeight: '500',
    includeFontPadding: false,
  },
  rowDetail: {
    color: PLACEHOLDER,
    fontSize: 12,
    includeFontPadding: false,
  },
});
