import { Canvas, Circle, Group, Oval, Path, RadialGradient, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, type TextStyle, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/** The little picture at the right of a suggestion card. */
export type MorphingMicPillGlyph = 'ball' | 'medal' | 'court';

/** A question the panel offers, and the answer it shows for it. */
export type MorphingMicPillSuggestion = {
  question: string;
  glyph: MorphingMicPillGlyph;
  lead: string;
  detailTitle: string;
  detail: string;
};

type Props = {
  /** The field's text. Leave it out and the component keeps the text itself. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Taps the first suggestion, waits for the answer, holds the mic and starts over, in a loop. */
  autoPlay?: boolean;
  heading?: string;
  suggestions?: MorphingMicPillSuggestion[];
  /** The answer to a question that was typed instead of picked. */
  typedLead?: string;
  typedDetailTitle?: string;
  typedDetail?: string;
  placeholder?: string;
  thinkingLabel?: string;
  /** Seconds the capsule spends thinking before the answer shows. */
  thinkingTime?: number;
  /** Called with the suggestion's index when a card is tapped. */
  onSuggestion?: (index: number) => void;
  /** Called with the typed question when it is sent. */
  onSend?: (question: string) => void;
  /** Called when "+" brings the suggestions back. */
  onNew?: () => void;
  onRecordStart?: () => void;
  onRecordEnd?: () => void;
};

const WIDTH = 350;
const HEIGHT = 400;
const RADIUS = 36;
// Cards keep this far from the panel's sides.
const INSET = 22;
const HEADING_Y = 30;
const CARDS_Y = 72;
const CARD_HEIGHT = 62;
const CARD_GAP = 8;
const ANSWER_INSET = 28;
const ANSWER_Y = 36;
// Rows and lines climb this far as they come in.
const RISE = 10;
const PRESS_DIP = 0.04;

// Top of the bottom bar, and the height of its three controls at rest.
const BAR_Y = 334;
const BAR_HEIGHT = 40;
// Centre of the round buttons, from the panel's sides.
const SIDE_X = 42;
// The capsule's width as a mic, thinking, recording and as a text field.
const MIC_WIDTH = 76;
const THINK_WIDTH = 126;
const RECORD_WIDTH = 138;
const RECORD_HEIGHT = 46;
const FIELD_WIDTH = 210;
const FIELD_HEIGHT = 44;
// The mic slides this far left to make room for the level bars.
const RECORD_SHIFT = 40;
// A tap on the capsule only nudges it, as a hint to hold: points per second added to its width.
const NUDGE = 260;
// A press shorter than this is a tap.
const HOLD_DELAY = 0.2;

const BAR_WIDTH = 2.5;
const BAR_PITCH = 7;
const BAR_MIN = 4;
const BAR_MAX = 18;

// The veil's front edge is this soft, and takes this long to climb the panel.
const VEIL_EDGE = 110;
const VEIL_IN = 0.7;
const SHEEN_PERIOD = 1.6;
const SHEEN_WIDTH = 300;
const SHEEN_TILT = 18;
// Degrees per second a card is thrown at when the veil's front passes it.
const WOBBLE_KICK = 52;
const PULSE = 0.9;

// How long the cards, and the answer's lines, take to arrive; both leave faster.
const CARDS_IN = 0.6;
const ANSWER_IN = 1;
const ANSWER_DELAY = 0.15;
const CONTENT_OUT = 0.2;
const STAGGER = 0.18;

// autoPlay period, seconds.
const LOOP = 8.8;
// After a touch, or once the field is closed, the loop waits this long before it starts over.
const RESUME = 3;

/** The autoPlay loop, as [seconds, action]: press and tap the first card, hold the mic for a moment, press and tap "+". */
const SCRIPT = [
  [0.8, 0],
  [0.95, 1],
  [4.6, 2],
  [6.0, 3],
  [6.9, 4],
  [7.05, 5],
];

/** Where the veil's front has to be for the heading and each card to feel it. */
const WOBBLE_LINES = [HEADING_Y - 20, CARDS_Y - 10, CARDS_Y + CARD_HEIGHT + CARD_GAP - 10, CARDS_Y + (CARD_HEIGHT + CARD_GAP) * 2 - 10];

const BAR_RATES = [9.1, 7.3, 11.2, 8.4, 10.3, 6.7, 12.1];
const BAR_RIPPLES = [17.0, 13.6, 19.3, 15.1, 12.2, 18.4, 14.3];
const BAR_PHASES = [0.3, 1.9, 4.1, 2.6, 5.2, 3.3, 0.9];

const TAU = Math.PI * 2;
const INK = '#1c1b19';

const SUGGESTIONS: MorphingMicPillSuggestion[] = [
  {
    question: 'Why is grass so different\nin tennis?',
    glyph: 'ball',
    lead: 'Grass plays faster and lower than other surfaces, so points stay shorter and timing matters more.',
    detailTitle: 'What changes',
    detail: 'Balls skid and rise less after landing, and a good serve gets through the court quickly.',
  },
  {
    question: 'What makes a serve\nan ace?',
    glyph: 'medal',
    lead: 'An ace is a legal serve the receiver cannot touch, so the point is over after a single shot.',
    detailTitle: 'Where it lands',
    detail: "Most aces go out wide or down the centre line, where the receiver's reach is longest.",
  },
  {
    question: 'How does clay change\nthe game?',
    glyph: 'court',
    lead: 'Clay slows the ball down and makes it bounce higher, so rallies run long and patience wins.',
    detailTitle: 'What changes',
    detail: 'Players slide into their shots, and heavy topspin is rewarded more than raw pace.',
  },
];

// The microphone in a 20-point box; the icons of the round buttons in 40-point boxes; the card's arrow in 14.
const MIC_STAND = 'M5.2 8.6V9.4A4.8 4.8 0 0 0 14.8 9.4V8.6M10 14.2V17M7 17H13';
const PLUS = 'M13 20H27M20 13V27';
const SPACE_KEY = 'M16 23H24';
const SEND_ARROW = 'M20 27V13.5M14.5 19L20 13.5L25.5 19';
const CARD_ARROW = 'M2 7H12M8 3L12 7L8 11';
const RIBBON = 'M12.5 7.5L18 18L23.5 7.5';
const COURT_LINES = 'M10.5 13.5H25.5V22.5H10.5ZM18 13.5V22.5M13.5 18H22.5';
const KEY_XS = [14.6, 18.2, 21.8, 25.4];

/** Per-frame state: what the panel is doing and everything that eases after it. */
type Sim = {
  time: number;
  clock: number;
  /** The next line of the autoPlay script. */
  step: number;
  looping: boolean;
  /** Seconds the loop still waits after a touch. */
  hold: number;
  answering: boolean;
  thinking: boolean;
  thinkAt: number;
  micDown: boolean;
  micByLoop: boolean;
  micAt: number;
  recording: boolean;
  recordAt: number;
  /** The control under the finger: 0…2 a card, 3 "+", 4 the keyboard button; -1 none. */
  pressed: number;
  veilIn: number;
  veilAlpha: number;
  /** Heading and cards, tilted by the veil's front as it passes them: angles and their velocities. */
  wobble: number[];
  wobbleV: number[];
  kicked: boolean[];
  cards: number;
  answer: number;
  // Springs: a position and its velocity.
  width: number;
  widthV: number;
  height: number;
  heightV: number;
  sides: number;
  sidesV: number;
  think: number;
  rec: number;
  type: number;
  press: number[];
};

function restingSim(): Sim {
  return {
    time: 0,
    clock: 0,
    step: 0,
    looping: false,
    hold: 0,
    answering: false,
    thinking: false,
    thinkAt: 0,
    micDown: false,
    micByLoop: false,
    micAt: 0,
    recording: false,
    recordAt: 0,
    pressed: -1,
    veilIn: 0,
    veilAlpha: 0,
    wobble: [0, 0, 0, 0],
    wobbleV: [0, 0, 0, 0],
    kicked: [false, false, false, false],
    cards: 1,
    answer: 0,
    width: MIC_WIDTH,
    widthV: 0,
    height: BAR_HEIGHT,
    heightV: 0,
    sides: 1,
    sidesV: 0,
    think: 0,
    rec: 0,
    type: 0,
    press: [0, 0, 0, 0, 0],
  };
}

type Signal = 'asked' | 'recordStart' | 'recordEnd';
/** What a frame decided, sent to React: `index` is the suggestion asked (-1 for a typed question). */
type Emit = (signal: Signal, index: number) => void;

function touch(s: Sim) {
  'worklet';
  s.hold = RESUME;
}

function ask(s: Sim, index: number, emit: Emit) {
  'worklet';
  if (s.thinking) return;
  s.answering = false;
  s.thinking = true;
  s.thinkAt = s.time;
  s.veilIn = 0;
  s.veilAlpha = 1;
  for (let i = 0; i < 4; i++) s.kicked[i] = false;
  // The answer to lay out is React's to keep.
  scheduleOnRN(emit, 'asked', index);
}

function reset(s: Sim) {
  'worklet';
  s.thinking = false;
  s.answering = false;
}

function micPress(s: Sim, byLoop: boolean) {
  'worklet';
  if (s.thinking) return;
  s.micDown = true;
  s.micByLoop = byLoop;
  s.micAt = s.time;
}

/** True when a recording ended; a short tap only nudges the capsule. */
function micRelease(s: Sim) {
  'worklet';
  if (!s.micDown) return false;
  s.micDown = false;
  if (s.recording) {
    s.recording = false;
    return true;
  }
  s.widthV += NUDGE;
  return false;
}

function run(s: Sim, action: number, emit: Emit) {
  'worklet';
  if (action === 0) {
    s.pressed = 0;
  } else if (action === 1) {
    s.pressed = -1;
    ask(s, 0, emit);
  } else if (action === 2) {
    micPress(s, true);
  } else if (action === 3) {
    micRelease(s);
  } else if (action === 4) {
    s.pressed = 3;
  } else {
    s.pressed = -1;
    reset(s);
  }
}

/** Moves `x` towards `target`, covering the same share of the distance in the same time at any frame rate. */
function approach(x: number, target: number, rate: number, dt: number) {
  'worklet';
  return x + (target - x) * (1 - Math.exp(-rate * dt));
}

/** The new velocity of a spring (mass 1, stiffness omega², damping 2·zeta·omega) after `dt`. */
function sprung(x: number, v: number, target: number, omega: number, zeta: number, dt: number) {
  'worklet';
  return v + (omega * omega * (target - x) - 2 * zeta * omega * v) * dt;
}

/** Top of the veil for a sweep that is `progress` (0…1) done: it starts under the panel and ends above it. */
function veilFront(progress: number) {
  'worklet';
  const eased = 1 - Math.pow(1 - progress, 3);
  return HEIGHT - (HEIGHT + VEIL_EDGE) * eased;
}

/** How far a row has come in, 0…1: `order` 0 is the first to arrive. */
function rowProgress(progress: number, order: number, rows: number) {
  'worklet';
  const window = 1 - STAGGER * (rows - 1);
  const p = Math.min(1, Math.max(0, (progress - order * STAGGER) / window));
  return 1 - Math.pow(1 - p, 3);
}

/** Opacity of one letter of the thinking label: a bright band runs through the word again and again. */
function shimmer(index: number, count: number, time: number) {
  'worklet';
  const cycle = time / SHEEN_PERIOD;
  const band = (cycle - Math.floor(cycle)) * (count + 8) - 4;
  const d = index - band;
  return 0.45 + 0.55 * Math.exp((-d * d) / 5);
}

function advance(s: Sim, dt: number, typing: boolean, thinkingTime: number, still: boolean, emit: Emit) {
  'worklet';
  s.time += dt;

  // The loop stays away while a finger is at work, and until the answer it asked for has been read.
  if (typing || (s.micDown && !s.micByLoop) || (s.hold > 0 && s.thinking)) s.hold = RESUME;
  if (s.hold > 0) {
    s.hold -= dt;
    if (s.hold <= 0 && s.looping) {
      // The loop starts over from the suggestions.
      s.clock = 0;
      s.step = 0;
      s.pressed = -1;
      s.micDown = false;
      s.recording = false;
      reset(s);
    }
  } else if (s.looping) {
    s.clock += dt;
    if (s.clock >= LOOP) {
      s.clock -= LOOP;
      s.step = 0;
    }
    while (s.step < SCRIPT.length && s.clock >= SCRIPT[s.step][0]) {
      run(s, SCRIPT[s.step][1], emit);
      s.step += 1;
    }
  }

  if (s.thinking && s.time - s.thinkAt >= thinkingTime) {
    s.thinking = false;
    s.answering = true;
    s.answer = -ANSWER_DELAY;
  }
  if (s.micDown && !s.recording && s.time - s.micAt >= HOLD_DELAY) {
    s.recording = true;
    s.recordAt = s.time;
    if (!s.micByLoop) scheduleOnRN(emit, 'recordStart', 0);
  }

  if (s.thinking) {
    s.veilIn = still ? 1 : Math.min(1, s.veilIn + dt / VEIL_IN);
  } else if (s.veilAlpha < 0.01) {
    s.veilIn = 0;
  }
  s.veilAlpha = approach(s.veilAlpha, s.thinking ? 1 : 0, 5, dt);
  const front = veilFront(s.veilIn);
  for (let i = 0; i < 4; i++) {
    if (s.thinking && !still && !s.kicked[i] && front < WOBBLE_LINES[i]) {
      s.kicked[i] = true;
      s.wobbleV[i] += i % 2 === 0 ? WOBBLE_KICK : -WOBBLE_KICK;
    }
    s.wobbleV[i] = sprung(s.wobble[i], s.wobbleV[i], 0, 13, 0.3, dt);
    s.wobble[i] += s.wobbleV[i] * dt;
  }
  s.answer = s.answering ? Math.min(1, s.answer + dt / ANSWER_IN) : Math.max(0, s.answer - dt / CONTENT_OUT);
  // The cards wait for the answer to be gone before they come back.
  if (s.answering) {
    s.cards = Math.max(0, s.cards - dt / CONTENT_OUT);
  } else if (s.answer <= 0) {
    s.cards = Math.min(1, s.cards + dt / CARDS_IN);
  }

  const wide = typing ? FIELD_WIDTH : s.thinking ? THINK_WIDTH : s.recording ? RECORD_WIDTH : MIC_WIDTH;
  s.widthV = sprung(s.width, s.widthV, wide, 17, 0.55, dt);
  s.width += s.widthV * dt;
  s.heightV = sprung(s.height, s.heightV, typing ? FIELD_HEIGHT : s.recording ? RECORD_HEIGHT : BAR_HEIGHT, 17, 0.55, dt);
  s.height += s.heightV * dt;
  s.sidesV = sprung(s.sides, s.sidesV, s.thinking ? 0 : 1, 15, 0.5, dt);
  s.sides += s.sidesV * dt;
  s.think = approach(s.think, s.thinking && !typing ? 1 : 0, 14, dt);
  s.rec = approach(s.rec, s.recording ? 1 : 0, 14, dt);
  s.type = approach(s.type, typing ? 1 : 0, 14, dt);
  for (let i = 0; i < s.press.length; i++) {
    s.press[i] = approach(s.press[i], s.pressed === i ? 1 : 0, 30, dt);
  }
}

/** A suggestion's picture on its tinted disc, in a 36-point box. */
function Glyph({ glyph }: { glyph: MorphingMicPillGlyph }) {
  if (glyph === 'ball') {
    return (
      <Canvas style={styles.glyph} pointerEvents="none">
        <Circle cx={18} cy={18} r={18} color="#f6ebe8" />
        <Circle cx={18} cy={18} r={10}>
          <RadialGradient c={vec(15, 14)} r={15} colors={['#f0796c', '#d9463b', '#b5342c']} />
        </Circle>
        <Oval x={12.5} y={11.5} width={5} height={3.6} color="rgba(255,255,255,0.45)" />
      </Canvas>
    );
  }
  if (glyph === 'medal') {
    return (
      <Canvas style={styles.glyph} pointerEvents="none">
        <Circle cx={18} cy={18} r={18} color="#f7f0de" />
        <Path path={RIBBON} style="stroke" strokeWidth={3.2} strokeCap="butt" strokeJoin="miter" color="#e5484d" />
        <Circle cx={18} cy={22} r={6.5}>
          <RadialGradient c={vec(16.5, 20)} r={9} colors={['#ffd978', '#f2b84b', '#d99a2b']} />
        </Circle>
        <Circle cx={18} cy={22} r={3.5} style="stroke" strokeWidth={1} color="rgba(201,138,30,0.7)" />
      </Canvas>
    );
  }
  return (
    <Canvas style={styles.glyph} pointerEvents="none">
      <Circle cx={18} cy={18} r={18} color="#f8ebe2" />
      <RoundedRect x={8} y={11} width={20} height={14} r={2.5} color="#e0703a" />
      <Path path={COURT_LINES} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.92)" />
    </Canvas>
  );
}

type Pressing = {
  onPress: () => void;
  onPressIn: () => void;
  onPressOut: () => void;
};

/** A suggestion card: it comes in with its row, tilts when the veil passes, and dips under a finger. */
function Card({ sim, index, item, still, ...pressing }: { sim: SharedValue<Sim>; index: number; item: MorphingMicPillSuggestion; still: boolean } & Pressing) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    const shown = rowProgress(s.cards, index + 1, 4);
    return {
      opacity: shown,
      transform: [
        { translateY: still ? 0 : (1 - shown) * RISE },
        { rotate: `${s.wobble[index + 1]}deg` },
        { scale: 1 - PRESS_DIP * s.press[index] },
      ],
    };
  });

  return (
    <Pressable
      {...pressing}
      style={[styles.cardSlot, { top: CARDS_Y + index * (CARD_HEIGHT + CARD_GAP) }]}
      accessibilityRole="button"
      accessibilityLabel={item.question.replace(/\n/g, ' ')}
    >
      <Animated.View style={[styles.card, style]}>
        <Text style={styles.question} numberOfLines={2}>
          {item.question}
        </Text>
        <Glyph glyph={item.glyph} />
        <Canvas style={styles.cardArrow} pointerEvents="none">
          <Path path={CARD_ARROW} style="stroke" strokeWidth={1.5} strokeCap="round" strokeJoin="round" color={INK} />
        </Canvas>
      </Animated.View>
    </Pressable>
  );
}

/** One line of the answer: the four of them arrive one after another. */
function AnswerLine({ sim, order, still, style, children }: { sim: SharedValue<Sim>; order: number; still: boolean; style: TextStyle; children: string }) {
  const shownStyle = useAnimatedStyle(() => {
    const shown = rowProgress(Math.max(0, sim.get().answer), order, 4);
    return { opacity: shown, transform: [{ translateY: still ? 0 : (1 - shown) * RISE }] };
  });

  return <Animated.Text style={[style, shownStyle]}>{children}</Animated.Text>;
}

/** One of the two white round buttons beside the capsule. */
function SideButton({
  sim,
  index,
  x,
  live,
  label,
  children,
  ...pressing
}: { sim: SharedValue<Sim>; index: number; x: number; live: boolean; label: string; children: ReactNode } & Pressing) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    const shown = Math.min(1, Math.max(0, s.sides));
    return {
      opacity: shown * (1 - 0.55 * s.rec),
      transform: [{ scale: (0.5 + 0.5 * s.sides) * (1 - PRESS_DIP * 1.5 * s.press[index]) }],
    };
  });

  return (
    <Pressable
      {...pressing}
      style={[styles.sideSlot, { left: x - BAR_HEIGHT / 2 }]}
      pointerEvents={live ? 'auto' : 'none'}
      accessibilityRole="button"
      accessibilityLabel={label}
    >
      <Animated.View style={[styles.side, style]}>
        <Canvas style={styles.sideIcon} pointerEvents="none">
          {children}
        </Canvas>
      </Animated.View>
    </Pressable>
  );
}

/** The veil: a tinted sheet with a soft front edge rising from the bar, and a pale sheen drifting across it. */
function Veil({ sim, still }: { sim: SharedValue<Sim>; still: boolean }) {
  const veil = useAnimatedStyle(() => ({ opacity: sim.get().veilAlpha }));
  const sheet = useAnimatedStyle(() => ({ transform: [{ translateY: veilFront(sim.get().veilIn) }] }));
  const band = useAnimatedStyle(() => {
    const s = sim.get();
    const front = veilFront(s.veilIn);
    const sheen = still ? 0.3 : (s.time - s.thinkAt) / SHEEN_PERIOD;
    return {
      opacity: front < 0 ? 1 : Math.max(0, 1 - front / HEIGHT),
      transform: [
        { translateX: -SHEEN_WIDTH * 1.3 + (WIDTH + SHEEN_WIDTH * 1.6) * (sheen - Math.floor(sheen)) },
        { translateY: -HEIGHT * 0.3 },
        { rotate: `${SHEEN_TILT}deg` },
      ],
    };
  });

  return (
    <Animated.View pointerEvents="none" style={[styles.veil, veil]}>
      <Animated.View style={[styles.veilSheet, sheet]}>
        <LinearGradient
          style={StyleSheet.absoluteFill}
          colors={['rgba(169,163,189,0)', 'rgba(169,163,189,0.52)', 'rgba(169,163,189,0.52)']}
          locations={[0, VEIL_EDGE / (HEIGHT + VEIL_EDGE), 1]}
        />
      </Animated.View>
      {/* Taller than the panel, so the tilted band has no corner inside it. */}
      <Animated.View style={[styles.sheen, band]}>
        <LinearGradient
          style={StyleSheet.absoluteFill}
          colors={['rgba(255,214,236,0)', 'rgba(255,214,236,0.34)', 'rgba(196,232,255,0.34)', 'rgba(214,255,236,0.28)', 'rgba(214,255,236,0)']}
          locations={[0, 0.3, 0.5, 0.7, 1]}
          start={{ x: 0, y: 0.5 }}
          end={{ x: 1, y: 0.5 }}
        />
      </Animated.View>
    </Animated.View>
  );
}

function Letter({ sim, index, count, still, children }: { sim: SharedValue<Sim>; index: number; count: number; still: boolean; children: string }) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: shimmer(index, count, still ? 0.3 : s.time - s.thinkAt) };
  });

  return <Animated.Text style={[styles.letter, style]}>{children}</Animated.Text>;
}

/**
 * An assistant's bottom bar whose black mic capsule changes shape with what it is doing. Tap a
 * suggestion and a frosted veil sweeps up over the cards, which wobble as it passes, the round
 * buttons step back and the capsule stretches into a "Thinking…" pill; then the veil lifts on the
 * answer and the capsule springs back. Hold the capsule and it swells and shows level bars; the
 * keyboard button stretches it into a text field, and "+" brings the suggestions back.
 *
 * Nothing is recorded here: the bars are a drawn pattern. Start and stop a real recorder from
 * `onRecordStart` and `onRecordEnd`.
 */
export function MorphingMicPill({
  value,
  onChangeText,
  autoPlay = false,
  heading = 'You might want to ask',
  suggestions = SUGGESTIONS,
  typedLead = 'Here is a short answer to get you started. Ask a follow-up to go deeper.',
  typedDetailTitle = 'Next',
  typedDetail = 'Tap + to go back to the suggestions and pick another question.',
  placeholder = 'Ask anything',
  thinkingLabel = 'Thinking…',
  thinkingTime = 2.1,
  onSuggestion,
  onSend,
  onNew,
  onRecordStart,
  onRecordEnd,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const input = useRef<TextInput>(null);
  const [own, setOwn] = useState('');
  // The capsule is a text field.
  const [typing, setTyping] = useState(false);
  // The question that was typed last.
  const [typedQuestion, setTypedQuestion] = useState('');
  // The suggestion whose answer is laid out; -1 for a typed question.
  const [asked, setAsked] = useState(0);
  // Which controls take touches now: 1 the cards, 2 the round buttons.
  const [live, setLive] = useState(3);
  const current = value ?? own;

  const sim = useSharedValue<Sim>(restingSim());
  const typingNow = useSharedValue(false);

  useEffect(() => {
    sim.modify((s) => {
      'worklet';
      s.looping = auto;
      return s;
    });
  }, [auto, sim]);

  useEffect(() => {
    typingNow.set(typing);
  }, [typing, typingNow]);

  const buzz = (style: Haptics.ImpactFeedbackStyle) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
  };

  /** Applies what a frame decided: the answer to lay out, haptics and callbacks live on the React side. */
  const handle: Emit = (signal, index) => {
    if (signal === 'asked') {
      setAsked(index);
    } else if (signal === 'recordStart') {
      buzz(Haptics.ImpactFeedbackStyle.Medium);
      onRecordStart?.();
    } else {
      onRecordEnd?.();
    }
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const busy = typingNow.get();
    sim.modify((s) => {
      'worklet';
      advance(s, dt, busy, thinkingTime, reducedMotion, handle);
      return s;
    });
  });

  useAnimatedReaction(
    () => {
      const s = sim.get();
      return (s.cards > 0.9 && !s.thinking ? 1 : 0) + (Math.min(1, Math.max(0, s.sides)) > 0.9 ? 2 : 0);
    },
    (now, old) => {
      if (now !== old) scheduleOnRN(setLive, now);
    },
  );

  const store = (text: string) => {
    if (value == null) setOwn(text);
    onChangeText?.(text);
  };

  const pressing = (index: number) => ({
    onPressIn: () => {
      buzz(Haptics.ImpactFeedbackStyle.Light);
      sim.modify((s) => {
        'worklet';
        touch(s);
        s.pressed = index;
        return s;
      });
    },
    onPressOut: () => {
      sim.modify((s) => {
        'worklet';
        if (s.pressed === index) s.pressed = -1;
        return s;
      });
    },
  });

  const pick = (index: number) => {
    sim.modify((s) => {
      'worklet';
      touch(s);
      ask(s, index, handle);
      return s;
    });
    onSuggestion?.(index);
  };

  const startOver = () => {
    sim.modify((s) => {
      'worklet';
      touch(s);
      reset(s);
      return s;
    });
    onNew?.();
  };

  const send = () => {
    const question = current.trim();
    store('');
    input.current?.blur();
    setTyping(false);
    if (!question) return;
    setTypedQuestion(question);
    sim.modify((s) => {
      'worklet';
      touch(s);
      ask(s, -1, handle);
      return s;
    });
    onSend?.(question);
  };

  /** The keyboard button opens the field; once there is text in it, it sends. */
  const keyboardTapped = () => {
    sim.modify((s) => {
      'worklet';
      touch(s);
      return s;
    });
    if (!typing) {
      setTyping(true);
      input.current?.focus();
    } else if (!current.trim()) {
      store('');
      input.current?.blur();
      setTyping(false);
    } else {
      send();
    }
  };

  const micHold = Gesture.Pan()
    .minDistance(0)
    .enabled(!typing)
    .onBegin(() => {
      sim.modify((s) => {
        'worklet';
        if (!s.micDown || s.micByLoop) {
          touch(s);
          micPress(s, false);
        }
        return s;
      });
    })
    .onFinalize(() => {
      sim.modify((s) => {
        'worklet';
        if (micRelease(s)) scheduleOnRN(handle, 'recordEnd', 0);
        return s;
      });
    });

  const headingStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: rowProgress(s.cards, 0, 4), transform: [{ rotate: `${s.wobble[0]}deg` }] };
  });

  const capsuleStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const w = Math.max(BAR_HEIGHT, s.width);
    const h = Math.max(30, s.height);
    return { left: WIDTH / 2 - w / 2, top: BAR_Y + BAR_HEIGHT / 2 - h / 2, width: w, height: h, borderRadius: h / 2 };
  });
  const outlineStyle = useAnimatedStyle(() => ({ opacity: sim.get().think }));
  const micStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: (1 - s.think) * (1 - s.type), transform: [{ translateX: -RECORD_SHIFT * s.rec }] };
  });
  const barsStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: s.rec * (1 - s.think), transform: [{ translateX: RECORD_SHIFT * 0.45 }] };
  });
  const thinkingStyle = useAnimatedStyle(() => ({ opacity: sim.get().think }));
  const fieldStyle = useAnimatedStyle(() => ({ opacity: sim.get().type }));

  const bars = useDerivedValue(() => {
    const s = sim.get();
    const time = reducedMotion ? 0.4 : s.time - s.recordAt;
    const path = Skia.Path.Make();
    for (let i = 0; i < 7; i++) {
      const wave = 0.5 + 0.34 * Math.sin(time * BAR_RATES[i] + BAR_PHASES[i]) + 0.22 * Math.sin(time * BAR_RIPPLES[i] + BAR_PHASES[6 - i]);
      const level = Math.min(1, Math.max(0, wave));
      const h = BAR_MIN + (BAR_MAX - BAR_MIN) * level * s.rec;
      path.addRRect(Skia.RRectXY(Skia.XYWHRect(i * BAR_PITCH, (BAR_MAX - h) / 2, BAR_WIDTH, h), BAR_WIDTH / 2, BAR_WIDTH / 2));
    }
    return path;
  });
  const dot = useDerivedValue(() => {
    const s = sim.get();
    const beat = reducedMotion ? 0.3 : s.time - s.thinkAt;
    return 2.5 * (0.85 + 0.3 * Math.sin((TAU * beat) / PULSE));
  });
  const keysOpacity = useDerivedValue(() => 1 - sim.get().type);
  const arrowOpacity = useDerivedValue(() => sim.get().type);

  const item = asked >= 0 && asked < suggestions.length ? suggestions[asked] : null;
  const letters = Array.from(thinkingLabel);

  return (
    <View style={styles.root}>
      <View style={styles.panel} />

      <View style={styles.clip}>
        <Animated.View pointerEvents="none" style={[styles.heading, headingStyle]}>
          <Text style={styles.headingText} numberOfLines={1}>
            {heading}
          </Text>
        </Animated.View>

        <View style={StyleSheet.absoluteFill} pointerEvents={live & 1 ? 'box-none' : 'none'}>
          {suggestions.slice(0, 3).map((suggestion, index) => (
            <Card key={index} sim={sim} index={index} item={suggestion} still={reducedMotion} onPress={() => pick(index)} {...pressing(index)} />
          ))}
        </View>

        {/* The answer, where the cards were. */}
        <View pointerEvents="none" style={styles.answer}>
          <AnswerLine sim={sim} order={0} still={reducedMotion} style={styles.answerQuestion}>
            {(item?.question ?? typedQuestion).replace(/\n/g, ' ')}
          </AnswerLine>
          <AnswerLine sim={sim} order={1} still={reducedMotion} style={styles.answerLead}>
            {item?.lead ?? typedLead}
          </AnswerLine>
          <AnswerLine sim={sim} order={2} still={reducedMotion} style={styles.answerTitle}>
            {item?.detailTitle ?? typedDetailTitle}
          </AnswerLine>
          <AnswerLine sim={sim} order={3} still={reducedMotion} style={styles.answerDetail}>
            {item?.detail ?? typedDetail}
          </AnswerLine>
        </View>

        <SideButton sim={sim} index={3} x={SIDE_X} live={(live & 2) > 0} label="New question" onPress={startOver} {...pressing(3)}>
          <Path path={PLUS} style="stroke" strokeWidth={1.7} strokeCap="round" color={INK} />
        </SideButton>
        {/* The keyboard button turns into the send button while the field is open. */}
        <SideButton
          sim={sim}
          index={4}
          x={WIDTH - SIDE_X}
          live={(live & 2) > 0}
          label={typing ? 'Send' : 'Keyboard'}
          onPress={keyboardTapped}
          {...pressing(4)}
        >
          <Group opacity={keysOpacity}>
            <RoundedRect x={11} y={14} width={18} height={12} r={3} style="stroke" strokeWidth={1.4} color={INK} />
            {KEY_XS.map((x) => (
              <Circle key={x} cx={x} cy={17.7} r={0.8} color={INK} />
            ))}
            {KEY_XS.map((x) => (
              <Circle key={x} cx={x} cy={20.4} r={0.8} color={INK} />
            ))}
            <Path path={SPACE_KEY} style="stroke" strokeWidth={1.4} strokeCap="round" color={INK} />
          </Group>
          <Group opacity={arrowOpacity}>
            <Path path={SEND_ARROW} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color={INK} />
          </Group>
        </SideButton>

        <Veil sim={sim} still={reducedMotion} />
      </View>

      {/* The black capsule: mic, level bars, the "Thinking…" label or the text field, whichever it is now. */}
      <GestureDetector gesture={micHold}>
        <Animated.View style={[styles.capsule, capsuleStyle]} accessible={!typing} accessibilityLabel="Hold to speak">
          <Animated.View pointerEvents="none" style={[styles.outline, outlineStyle]} />
          {/* The contents are laid over the capsule, so the widest of them can't stretch it. */}
          <View style={styles.contents} pointerEvents="box-none">
            <Animated.View pointerEvents="none" style={[styles.mic, micStyle]}>
              <Canvas style={styles.micCanvas}>
                <RoundedRect x={7.5} y={2} width={5} height={9.5} r={2.5} color="#ffffff" />
                <Path path={MIC_STAND} style="stroke" strokeWidth={1.5} strokeCap="round" strokeJoin="round" color="#ffffff" />
              </Canvas>
            </Animated.View>

            <Animated.View pointerEvents="none" style={[styles.bars, barsStyle]}>
              <Canvas style={styles.barsCanvas}>
                <Path path={bars} color="#ffffff" />
              </Canvas>
            </Animated.View>

            <Animated.View pointerEvents="none" style={[styles.thinking, thinkingStyle]}>
              <Canvas style={styles.ring}>
                <Circle cx={7} cy={7} r={5.5} style="stroke" strokeWidth={1.6} color="#ffffff" />
                <Circle cx={7} cy={7} r={dot} color="#ffffff" />
              </Canvas>
              <View style={styles.letters}>
                {letters.map((letter, index) => (
                  <Letter key={index} sim={sim} index={index} count={letters.length} still={reducedMotion}>
                    {letter}
                  </Letter>
                ))}
              </View>
            </Animated.View>

            <Animated.View style={[styles.field, fieldStyle]} pointerEvents={typing ? 'auto' : 'none'}>
              <TextInput
                ref={input}
                style={styles.input}
                value={current}
                onChangeText={store}
                placeholder={placeholder}
                placeholderTextColor="#8e8e93"
                selectionColor="#ffffff"
                cursorColor="#ffffff"
                returnKeyType="send"
                onSubmitEditing={send}
                onBlur={() => {
                  // A field left empty folds back into the mic capsule.
                  if (!current) setTyping(false);
                }}
              />
            </Animated.View>
          </View>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  panel: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    backgroundColor: '#f7f6f3',
    boxShadow: '0 14px 48px rgba(0, 0, 0, 0.1)',
  },
  // Everything under the capsule, cut to the panel's corners so the veil stays inside them.
  clip: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  heading: {
    position: 'absolute',
    left: 0,
    top: HEADING_Y,
    width: WIDTH,
    height: 20,
  },
  headingText: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
    textAlign: 'center',
  },
  cardSlot: {
    position: 'absolute',
    left: INSET,
    width: WIDTH - INSET * 2,
    height: CARD_HEIGHT,
  },
  card: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingLeft: 16,
    paddingRight: 14,
    borderRadius: 16,
    backgroundColor: '#ffffff',
    boxShadow: '0 4px 14px rgba(60, 50, 40, 0.09)',
  },
  question: {
    flex: 1,
    color: INK,
    fontSize: 13,
    lineHeight: 17.5,
    fontWeight: '500',
  },
  glyph: {
    width: 36,
    height: 36,
  },
  cardArrow: {
    width: 14,
    height: 14,
  },
  answer: {
    position: 'absolute',
    left: ANSWER_INSET,
    top: ANSWER_Y,
    width: WIDTH - ANSWER_INSET * 2,
    height: BAR_Y - ANSWER_Y,
  },
  answerQuestion: {
    color: '#8a8782',
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '500',
    marginBottom: 12,
  },
  answerLead: {
    color: INK,
    fontSize: 16,
    lineHeight: 22,
    marginBottom: 19,
  },
  answerTitle: {
    color: INK,
    fontSize: 14,
    lineHeight: 17,
    fontWeight: '600',
    marginBottom: 8,
  },
  answerDetail: {
    color: '#5c5953',
    fontSize: 13,
    lineHeight: 18.5,
  },
  sideSlot: {
    position: 'absolute',
    top: BAR_Y,
    width: BAR_HEIGHT,
    height: BAR_HEIGHT,
  },
  side: {
    width: BAR_HEIGHT,
    height: BAR_HEIGHT,
    borderRadius: BAR_HEIGHT / 2,
    backgroundColor: '#ffffff',
    boxShadow: '0 3px 12px rgba(60, 50, 40, 0.14)',
  },
  sideIcon: {
    width: BAR_HEIGHT,
    height: BAR_HEIGHT,
  },
  veil: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  veilSheet: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT + VEIL_EDGE,
  },
  sheen: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: SHEEN_WIDTH,
    height: HEIGHT * 1.6,
  },
  capsule: {
    position: 'absolute',
    backgroundColor: '#111111',
    boxShadow: '0 5px 16px rgba(0, 0, 0, 0.22)',
  },
  outline: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: RECORD_HEIGHT / 2,
    borderWidth: 1.5,
    borderColor: '#ffffff',
  },
  contents: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mic: {
    position: 'absolute',
  },
  micCanvas: {
    width: 20,
    height: 20,
  },
  bars: {
    position: 'absolute',
  },
  barsCanvas: {
    width: BAR_PITCH * 6 + BAR_WIDTH,
    height: BAR_MAX,
  },
  thinking: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  ring: {
    width: 14,
    height: 14,
  },
  letters: {
    flexDirection: 'row',
  },
  letter: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  field: {
    position: 'absolute',
    width: FIELD_WIDTH - 36,
    height: 30,
  },
  input: {
    flex: 1,
    padding: 0,
    color: '#ffffff',
    fontSize: 15,
    textAlignVertical: 'center',
    includeFontPadding: false,
  },
});
