import { BlurMask, Canvas, LinearGradient, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient as GradientView } from 'expo-linear-gradient';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  cubicBezier,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The field's text. Leave it out and the component keeps the text itself. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Holds, speaks and releases by itself, then holds again and cancels, in a loop. */
  autoPlay?: boolean;
  placeholder?: string;
  /** The suggestion chips, one list per row, top row first. */
  suggestionRows?: string[][];
  /** The chip drawn with the gradient outline. */
  highlighted?: string;
  listeningLabel?: string;
  slideHint?: string;
  cancelHint?: string;
  /** What a finished recording puts in the field. */
  transcript?: string;
  /** The aurora along the pill's edge, left to right, as `#rrggbb`. */
  glowColors?: [string, string, string];
  /** The same while the cancel is armed. */
  cancelColors?: [string, string, string];
  /** Called with the text when the keyboard's send key is pressed or a recording is released. */
  onSend?: (text: string) => void;
  onSuggestion?: (label: string) => void;
  onRecordStart?: () => void;
  /** Called when the finger lifts; `true` when the recording was cancelled. */
  onRecordEnd?: (cancelled: boolean) => void;
};

const WIDTH = 350;
const HEIGHT = 52;
const RADIUS = 22;
// Text and level bars start this far from the pill's ends.
const INSET = 22;
// The canvas overflows the pill by this much on every side to fit the aurora.
const BLEED = 40;

const CHIP_HEIGHT = 36;
const CHIP_GAP = 8;
const CHIPS_TO_PILL = 16;
// Top of the pill: three rows of chips and the gap under them.
const PILL_Y = CHIP_HEIGHT * 3 + CHIP_GAP * 2 + CHIPS_TO_PILL;
const CHIP_RISE = 10;
// The rows come back one after another, the bottom one first, over this long; they leave together.
const CHIPS_IN = 0.6;
const CHIPS_OUT = 0.22;
const CHIP_STAGGER = 0.22;

const HINT_WIDTH = 160;
const HINT_HEIGHT = 36;
// Between the hint and the pill while the finger rests on it.
const HINT_GAP = 14;

const BAR_WIDTH = 2;
const BAR_PITCH = 7;
const BAR_MIN = 3;
const BAR_MAX = 14;

// A press shorter than this is a tap, which focuses the field.
const HOLD_DELAY = 0.18;
// Sliding up this far arms the cancel; coming back down past the second line disarms it.
const ARM_AT = -40;
const DISARM_AT = -28;
const MAX_LIFT = 72;
// The label changes side once the finger is this far past the middle.
const SIDE_SLACK = 20;
// The mic keeps this far from the pill's ends.
const MIC_MARGIN = 30;
// How long the transcript stays in the field, and how far above it starts.
const TRANSCRIPT_HOLD = 1.4;
const DROP_FROM = 12;

// The aurora at rest: where it sits (share of the width), how far it sways, and how it breathes.
const REST_X = 0.6;
const REST_SWAY = 0.1;
const REST_GLOW = 0.5;
const REST_PULSE = 0.1;
const REST_SPREAD = 1.7;
const REST_SINK = 4;
// Half the length of the lit stretch while it is gathered under the finger.
const GLOW_REACH = 110;
const BREATH = 3.2;
const BLINK = 1.2;

// autoPlay period, seconds. The aurora breathes three times and the caret blinks eight times in it.
const LOOP = 9.6;
// After a touch, or after the field loses focus, the loop waits this long before it starts over.
const RESUME = 3;

/**
 * The finger of the autoPlay loop: seconds, x as a share of the pill's width, y in points (up is
 * negative). The first pass speaks and lets go; the second slides up and cancels.
 */
const SCRIPTED_PASSES = [
  [
    [0.8, 0.8, 0],
    [1.5, 0.8, 0],
    [2.1, 0.22, 0],
    [2.5, 0.22, 0],
    [3.0, 0.56, 0],
    [3.4, 0.56, 0],
  ],
  [
    [6.0, 0.76, 0],
    [6.6, 0.76, 0],
    [7.0, 0.6, 0],
    [7.4, 0.6, -64],
    [8.0, 0.6, -64],
  ],
];

const BAR_RATES = [9.1, 7.3, 11.2, 8.4, 10.3];
const BAR_RIPPLES = [17.0, 13.6, 19.3, 15.1, 12.2];
const BAR_PHASES = [0.3, 1.9, 4.1, 2.6, 5.2];

const TAU = Math.PI * 2;
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const MIC_STAND = 'M6.5 10.5V11.5A5.5 5.5 0 0 0 17.5 11.5V10.5M12 17V20M8.5 20H15.5';
const CHEVRON = 'M1 8.5L6 3.5L11 8.5';
const CROSS = 'M1.5 1.5L10.5 10.5M10.5 1.5L1.5 10.5';

const IDLE = 0;
const PRESSING = 1;
const LISTENING = 2;
const SENT = 3;
const CLEARING = 4;

/** Per-frame state: a finger (real or scripted) and everything that eases after it. */
type Sim = {
  time: number;
  clock: number;
  looping: boolean;
  /** Seconds the loop still waits after a touch. */
  hold: number;
  phase: number;
  down: boolean;
  /** The current run was started by the loop, not by a finger. */
  scripted: boolean;
  px: number;
  py: number;
  downAt: number;
  listenAt: number;
  sentAt: number;
  armed: boolean;
  labelRight: boolean;
  listen: number;
  field: number;
  cancel: number;
  side: number;
  power: number;
  chips: number;
  // Springs: a position and its velocity.
  micX: number;
  micV: number;
  lift: number;
  liftV: number;
  glowX: number;
  glowV: number;
  drop: number;
  dropV: number;
  pop: number;
  popV: number;
};

const REST: Sim = {
  time: 0,
  clock: 0,
  looping: false,
  hold: 0,
  phase: IDLE,
  down: false,
  scripted: false,
  px: WIDTH / 2,
  py: 0,
  downAt: 0,
  listenAt: 0,
  sentAt: 0,
  armed: false,
  labelRight: false,
  listen: 0,
  field: 1,
  cancel: 0,
  side: 0,
  power: 0,
  chips: 1,
  micX: WIDTH / 2,
  micV: 0,
  lift: 0,
  liftV: 0,
  glowX: WIDTH * REST_X,
  glowV: 0,
  drop: 0,
  dropV: 0,
  pop: 1,
  popV: 0,
};

type Signal = 'tap' | 'listenStart' | 'arm' | 'send' | 'cancel' | 'clear';
/** What a frame decided, sent to React: `flag` is "armed" for `arm` and "by a finger" for `send`. */
type Emit = (signal: Signal, flag: boolean) => void;

function press(s: Sim, x: number, scripted: boolean) {
  'worklet';
  s.down = true;
  s.scripted = scripted;
  s.px = x;
  s.py = 0;
  s.downAt = s.time;
  s.armed = false;
  s.phase = PRESSING;
  if (!scripted) s.hold = RESUME;
}

function move(s: Sim, x: number, y: number) {
  'worklet';
  s.px = x;
  s.py = y;
}

/** A text lands in the field, stays a moment and clears. */
function show(s: Sim) {
  'worklet';
  s.phase = SENT;
  s.sentAt = s.time;
  s.field = 0;
  s.drop = -DROP_FROM;
  s.dropV = 0;
}

function release(s: Sim, emit: Emit) {
  'worklet';
  if (!s.down) return;
  s.down = false;
  if (s.phase === PRESSING) {
    s.phase = IDLE;
    if (!s.scripted) scheduleOnRN(emit, 'tap', true);
  } else if (s.phase === LISTENING && s.armed) {
    s.phase = IDLE;
    if (!s.scripted) scheduleOnRN(emit, 'cancel', true);
  } else if (s.phase === LISTENING) {
    show(s);
    scheduleOnRN(emit, 'send', !s.scripted);
  }
}

function scriptedFinger(t: number) {
  'worklet';
  for (const pass of SCRIPTED_PASSES) {
    if (t < pass[0][0] || t >= pass[pass.length - 1][0]) continue;
    for (let i = 1; i < pass.length; i++) {
      if (t >= pass[i][0]) continue;
      const a = pass[i - 1];
      const b = pass[i];
      const u = (t - a[0]) / (b[0] - a[0]);
      const eased = u * u * (3 - 2 * u);
      return { x: a[1] + (b[1] - a[1]) * eased, y: a[2] + (b[2] - a[2]) * eased };
    }
  }
  return null;
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

function micSlot(x: number) {
  'worklet';
  return Math.min(Math.max(x, MIC_MARGIN), WIDTH - MIC_MARGIN);
}

function advance(s: Sim, dt: number, engaged: boolean, still: boolean, emit: Emit) {
  'worklet';
  s.time += dt;

  if (engaged || (!s.scripted && s.phase !== IDLE)) s.hold = RESUME;
  if (s.hold > 0) {
    // The loop was interrupted: its own press ends without sending anything.
    if (s.down && s.scripted) {
      s.down = false;
      s.phase = IDLE;
    }
    s.hold -= dt;
    if (s.hold <= 0 && s.looping) s.clock = 0;
  } else if (s.looping) {
    s.clock += dt;
    if (s.clock >= LOOP) s.clock -= LOOP;
    const finger = scriptedFinger(s.clock);
    if (finger) {
      if (!s.down) press(s, finger.x * WIDTH, true);
      move(s, finger.x * WIDTH, finger.y);
    } else if (s.down && s.scripted) {
      release(s, emit);
    }
  }

  if (s.phase === PRESSING && s.time - s.downAt >= HOLD_DELAY) {
    s.phase = LISTENING;
    s.listenAt = s.time;
    s.labelRight = s.px < WIDTH / 2;
    s.side = s.labelRight ? 1 : 0;
    s.micX = micSlot(s.px);
    s.micV = 0;
    s.pop = 0.5;
    s.popV = 0;
    if (!s.scripted) scheduleOnRN(emit, 'listenStart', true);
  } else if (s.phase === LISTENING) {
    const now = s.py < (s.armed ? DISARM_AT : ARM_AT);
    if (now !== s.armed) {
      s.armed = now;
      if (!s.scripted) scheduleOnRN(emit, 'arm', now);
    }
    // The label keeps to the side the finger is not on.
    if (s.px > WIDTH / 2 + SIDE_SLACK) s.labelRight = false;
    if (s.px < WIDTH / 2 - SIDE_SLACK) s.labelRight = true;
  } else if (s.phase === SENT && s.time - s.sentAt >= TRANSCRIPT_HOLD) {
    s.phase = CLEARING;
  } else if (s.phase === CLEARING && s.field < 0.03) {
    s.phase = IDLE;
    scheduleOnRN(emit, 'clear', true);
  }

  const listening = s.phase === LISTENING;
  s.listen = approach(s.listen, listening ? 1 : 0, 16, dt);
  s.field = approach(s.field, listening || s.phase === CLEARING ? 0 : 1, 12, dt);
  s.cancel = approach(s.cancel, listening && s.armed ? 1 : 0, 14, dt);
  s.side = approach(s.side, s.labelRight ? 1 : 0, 18, dt);
  s.power = approach(s.power, listening ? 1 : 0, 8, dt);
  s.chips = s.phase === IDLE ? Math.min(1, s.chips + dt / CHIPS_IN) : Math.max(0, s.chips - dt / CHIPS_OUT);
  if (listening) {
    s.micV = sprung(s.micX, s.micV, micSlot(s.px), 30, 0.9, dt);
    s.micX += s.micV * dt;
  }
  s.liftV = sprung(s.lift, s.liftV, listening ? Math.min(0, Math.max(s.py, -MAX_LIFT)) : 0, 22, 0.75, dt);
  s.lift += s.liftV * dt;
  const rest = WIDTH * (REST_X + (still ? 0 : REST_SWAY * Math.sin((TAU * s.time) / BREATH)));
  s.glowV = sprung(s.glowX, s.glowV, listening ? s.micX : rest, 12, 0.9, dt);
  s.glowX += s.glowV * dt;
  s.dropV = sprung(s.drop, s.dropV, 0, 18, 0.55, dt);
  s.drop += s.dropV * dt;
  s.popV = sprung(s.pop, s.popV, 1, 20, 0.5, dt);
  s.pop += s.popV * dt;
}

/** How far a row of chips has come in, 0…1: `order` 0 is the first to arrive. */
function rowProgress(progress: number, order: number, rows: number) {
  'worklet';
  const window = 1 - CHIP_STAGGER * (rows - 1);
  const p = Math.min(1, Math.max(0, (progress - order * CHIP_STAGGER) / window));
  return 1 - Math.pow(1 - p, 3);
}

/** How bright the aurora is: it breathes at rest and is at full strength under a finger. */
function glowAlpha(s: Sim, still: boolean) {
  'worklet';
  const breath = still ? 0 : Math.sin((TAU * s.time) / BREATH + 1.2);
  return (REST_GLOW + REST_PULSE * breath) * (1 - s.power) + s.power;
}

/** At rest the light lies wide along the bottom edge; held, it gathers under the finger. */
function glowReach(s: Sim) {
  'worklet';
  return GLOW_REACH * (REST_SPREAD - (REST_SPREAD - 1) * s.power);
}

/** `#rrggbb` with an alpha, as Skia reads it. */
function withAlpha(hex: string, alpha: number) {
  return hex + Math.round(alpha * 255).toString(16).padStart(2, '0');
}

type Layer = {
  sim: SharedValue<Sim>;
  colors: [string, string, string];
  /** The layer shown while the cancel is armed; the other one fades out as this one comes in. */
  cancelled: boolean;
  still: boolean;
};

/** The light bleeding out from behind the pill: a wide soft halo and a tight bright one. */
function Aurora({ sim, colors, cancelled, still }: Layer) {
  const stops = [withAlpha(colors[0], 0), colors[0], colors[1], colors[2], withAlpha(colors[2], 0)];
  const positions = [0, 0.24, 0.5, 0.76, 1];
  const start = useDerivedValue(() => vec(BLEED + sim.get().glowX - glowReach(sim.get()), 0));
  const end = useDerivedValue(() => vec(BLEED + sim.get().glowX + glowReach(sim.get()), 0));
  const wide = useDerivedValue(() => {
    const s = sim.get();
    return glowAlpha(s, still) * (cancelled ? s.cancel * 0.9 : 1 - s.cancel) * 0.55;
  });
  const tight = useDerivedValue(() => {
    const s = sim.get();
    return glowAlpha(s, still) * (cancelled ? s.cancel * 0.9 : 1 - s.cancel);
  });
  const wideY = useDerivedValue(() => BLEED - 2 + REST_SINK * (1 - sim.get().power));
  const tightY = useDerivedValue(() => BLEED - 1 + REST_SINK * (1 - sim.get().power) * 0.5);

  return (
    <>
      <RoundedRect x={BLEED - 2} y={wideY} width={WIDTH + 4} height={HEIGHT + 4} r={RADIUS + 2} opacity={wide}>
        <LinearGradient start={start} end={end} colors={stops} positions={positions} />
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect x={BLEED - 1} y={tightY} width={WIDTH + 2} height={HEIGHT + 2} r={RADIUS + 1} opacity={tight}>
        <LinearGradient start={start} end={end} colors={stops} positions={positions} />
        <BlurMask blur={5} style="normal" />
      </RoundedRect>
    </>
  );
}

/** The lit stretch of the pill's hairline. */
function Edge({ sim, colors, cancelled }: Omit<Layer, 'still'>) {
  const stops = [withAlpha(colors[0], 0.15), colors[0], colors[1], colors[2], withAlpha(colors[2], 0.15)];
  const start = useDerivedValue(() => vec(BLEED + sim.get().glowX - glowReach(sim.get()), 0));
  const end = useDerivedValue(() => vec(BLEED + sim.get().glowX + glowReach(sim.get()), 0));
  const opacity = useDerivedValue(() => {
    const s = sim.get();
    return (0.45 + 0.55 * s.power) * (cancelled ? s.cancel : 1 - s.cancel);
  });

  return (
    <RoundedRect
      x={BLEED + 0.5}
      y={BLEED + 0.5}
      width={WIDTH - 1}
      height={HEIGHT - 1}
      r={RADIUS - 0.5}
      style="stroke"
      strokeWidth={1}
      opacity={opacity}
    >
      <LinearGradient start={start} end={end} colors={stops} positions={[0, 0.24, 0.5, 0.76, 1]} />
    </RoundedRect>
  );
}

/** Five level bars that move like a voice; armed, or on the way out, they flatten into dots. */
function Bars({ sim, still }: { sim: SharedValue<Sim>; still: boolean }) {
  const bars = useDerivedValue(() => {
    const s = sim.get();
    const time = still ? 0.4 : s.time - s.listenAt;
    const strength = (1 - s.cancel) * s.listen;
    const path = Skia.Path.Make();
    for (let i = 0; i < 5; i++) {
      const wave = 0.5 + 0.34 * Math.sin(time * BAR_RATES[i] + BAR_PHASES[i]) + 0.22 * Math.sin(time * BAR_RIPPLES[i] + BAR_PHASES[4 - i]);
      const level = Math.min(1, Math.max(0, wave));
      const h = BAR_MIN + (BAR_MAX - BAR_MIN) * level * strength;
      path.addRRect(Skia.RRectXY(Skia.XYWHRect(i * BAR_PITCH, (BAR_MAX - h) / 2, BAR_WIDTH, h), BAR_WIDTH / 2, BAR_WIDTH / 2));
    }
    return path;
  });

  return (
    <Canvas style={styles.bars}>
      <Path path={bars} color="rgba(255,255,255,0.92)" />
    </Canvas>
  );
}

/** The bars and the word next to them, at one end of the pill. */
function ListeningLabel({ sim, right, still, label }: { sim: SharedValue<Sim>; right: boolean; still: boolean; label: string }) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    return { opacity: s.listen * (right ? s.side : 1 - s.side) };
  });

  return (
    <Animated.View pointerEvents="none" style={[styles.label, right && styles.labelRight, style]}>
      <Bars sim={sim} still={still} />
      <Text style={styles.labelText} numberOfLines={1}>
        {label}
      </Text>
    </Animated.View>
  );
}

function Hint({ sim, cross, label, color }: { sim: SharedValue<Sim>; cross: boolean; label: string; color: string }) {
  const style = useAnimatedStyle(() => ({ opacity: cross ? sim.get().cancel : 1 - sim.get().cancel }));

  return (
    <Animated.View style={[styles.hintBody, style]}>
      <Canvas style={styles.hintMark}>
        <Path path={cross ? CROSS : CHEVRON} style="stroke" strokeWidth={1.5} strokeCap="round" strokeJoin="round" color={color} />
      </Canvas>
      <Text style={[styles.hintText, { color }]} numberOfLines={1}>
        {label}
      </Text>
    </Animated.View>
  );
}

function ChipRow({ sim, order, rows, still, children }: { sim: SharedValue<Sim>; order: number; rows: number; still: boolean; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const shown = rowProgress(sim.get().chips, order, rows);
    return { opacity: shown, transform: [{ translateY: still ? 0 : (1 - shown) * CHIP_RISE }] };
  });

  return <Animated.View style={[styles.chipRow, style]}>{children}</Animated.View>;
}

function Chip({ label, outline, onPress }: { label: string; outline?: [string, string, string]; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      onPressIn={() => {
        if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }}
      accessibilityRole="button"
      accessibilityLabel={label}
    >
      {({ pressed }) => (
        <Animated.View
          style={{
            transform: [{ scale: pressed ? 0.96 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          }}
        >
          {outline ? (
            <GradientView colors={outline} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={styles.chipOutline}>
              <View style={[styles.chip, styles.chipLit]}>
                <Text style={[styles.chipText, styles.chipTextLit]} numberOfLines={1}>
                  {label}
                </Text>
              </View>
            </GradientView>
          ) : (
            <View style={styles.chip}>
              <Text style={styles.chipText} numberOfLines={1}>
                {label}
              </Text>
            </View>
          )}
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A chat field that is also a push-to-talk button. Tap it to type; press and hold it to speak: the
 * suggestion chips step aside, level bars and "Listening" replace the placeholder, a mic sits under
 * the finger and the aurora on the pill's edge gathers there and follows it. Slide up to arm the
 * cancel, release in place to drop the transcript into the field.
 *
 * Nothing is recorded here: the bars are a drawn pattern. Start and stop a real recorder from
 * `onRecordStart` and `onRecordEnd`, and hand its result in as `transcript`.
 */
export function HoldToSpeakInput({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'Start typing or hold to speak.',
  suggestionRows = [['Plan my day'], ['Schedule', 'Reschedule'], ['Find time', 'Create todo list']],
  highlighted = 'Plan my day',
  listeningLabel = 'Listening',
  slideHint = 'Slide up to cancel',
  cancelHint = 'Release to cancel',
  transcript = 'Plan my day around the 3 pm review',
  glowColors = ['#2fd6c3', '#7be36a', '#f2d94a'],
  cancelColors = ['#ff8a3d', '#ff4d4f', '#ff3b6b'],
  onSend,
  onSuggestion,
  onRecordStart,
  onRecordEnd,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const input = useRef<TextInput>(null);
  const [own, setOwn] = useState('');
  const [focused, setFocused] = useState(false);
  // The text in the field was typed by hand, so the loop must leave it alone.
  const [typed, setTyped] = useState(false);
  // Counts the taps on the resting field; each one hands the focus to the input.
  const [taps, setTaps] = useState(0);
  const current = value ?? own;

  const sim = useSharedValue<Sim>({ ...REST });
  const engaged = useSharedValue(false);
  // Where the finger came down, to measure how far it has slid up.
  const touchY = useSharedValue(0);

  useEffect(() => {
    sim.modify((s) => {
      'worklet';
      s.looping = auto;
      return s;
    });
  }, [auto, sim]);

  useEffect(() => {
    engaged.set(focused || typed);
  }, [engaged, focused, typed]);

  useEffect(() => {
    if (taps > 0) input.current?.focus();
  }, [taps]);

  const store = (text: string) => {
    if (value == null) setOwn(text);
    onChangeText?.(text);
  };

  const buzz = (style: Haptics.ImpactFeedbackStyle) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
  };

  /** Applies what a frame decided: text, focus, haptics and callbacks live on the React side. */
  const handle: Emit = (signal, flag) => {
    if (signal === 'tap') {
      store('');
      setTyped(false);
      setTaps((count) => count + 1);
    } else if (signal === 'listenStart') {
      buzz(Haptics.ImpactFeedbackStyle.Medium);
      onRecordStart?.();
    } else if (signal === 'arm') {
      buzz(flag ? Haptics.ImpactFeedbackStyle.Rigid : Haptics.ImpactFeedbackStyle.Light);
    } else if (signal === 'send') {
      store(transcript);
      setTyped(false);
      if (!flag) return;
      if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      onRecordEnd?.(false);
      onSend?.(transcript);
    } else if (signal === 'cancel') {
      buzz(Haptics.ImpactFeedbackStyle.Light);
      onRecordEnd?.(true);
    } else {
      store('');
    }
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const busy = engaged.get();
    sim.modify((s) => {
      'worklet';
      advance(s, dt, busy, reducedMotion, handle);
      return s;
    });
  });

  const submit = () => {
    const sent = current.trim();
    store('');
    setTyped(false);
    if (sent) onSend?.(sent);
  };

  const suggest = (label: string) => {
    store(label);
    setTyped(false);
    sim.modify((s) => {
      'worklet';
      s.scripted = false;
      show(s);
      return s;
    });
    onSuggestion?.(label);
  };

  // A touch on the resting field: let go at once to type, keep it down to speak.
  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(!focused)
    .onBegin((e) => {
      touchY.set(e.y);
      sim.modify((s) => {
        'worklet';
        press(s, e.x, false);
        return s;
      });
    })
    .onTouchesMove((e) => {
      const finger = e.allTouches[0];
      if (!finger) return;
      const y = finger.y - touchY.get();
      sim.modify((s) => {
        'worklet';
        move(s, finger.x, y);
        return s;
      });
    })
    .onFinalize(() => {
      sim.modify((s) => {
        'worklet';
        release(s, handle);
        return s;
      });
    });

  const chipsProps = useAnimatedProps(() => ({
    pointerEvents: sim.get().chips > 0.9 ? ('auto' as const) : ('none' as const),
  }));

  const fieldStyle = useAnimatedStyle(() => ({
    opacity: sim.get().field,
    transform: [{ translateY: reducedMotion ? 0 : sim.get().drop }],
  }));

  const caretHidden = focused || current.length > 0;
  const caretStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const blink = Math.min(1, Math.max(0, 0.5 + 3 * Math.sin((TAU * s.time) / BLINK)));
    return { opacity: caretHidden ? 0 : s.field * blink };
  });

  const micStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return {
      opacity: s.listen * (1 - s.cancel),
      transform: [
        { translateX: s.micX - 12 },
        { translateY: PILL_Y + HEIGHT / 2 - 12 + s.lift * 0.2 },
        { scale: reducedMotion ? 1 : s.pop },
      ],
    };
  });

  const hintStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return {
      opacity: s.listen,
      transform: [
        { translateX: Math.min(Math.max(s.micX - HINT_WIDTH / 2, 0), WIDTH - HINT_WIDTH) },
        { translateY: PILL_Y - HINT_GAP - HINT_HEIGHT + s.lift },
      ],
    };
  });

  const outline: [string, string, string] = [withAlpha(glowColors[0], 0.8), withAlpha(glowColors[1], 0.8), withAlpha(glowColors[2], 0.8)];

  return (
    <View style={styles.root}>
      <Animated.View style={styles.chips} animatedProps={chipsProps}>
        {suggestionRows.map((row, index) => (
          <ChipRow key={index} sim={sim} order={suggestionRows.length - 1 - index} rows={suggestionRows.length} still={reducedMotion}>
            {row.map((label) => (
              <Chip key={label} label={label} outline={label === highlighted ? outline : undefined} onPress={() => suggest(label)} />
            ))}
          </ChipRow>
        ))}
      </Animated.View>

      {/* The aurora behind the pill, the pill, and its hairline. */}
      <Canvas style={styles.pill} pointerEvents="none">
        <Aurora sim={sim} colors={glowColors} cancelled={false} still={reducedMotion} />
        <Aurora sim={sim} colors={cancelColors} cancelled still={reducedMotion} />
        <RoundedRect x={BLEED} y={BLEED} width={WIDTH} height={HEIGHT} r={RADIUS} color="#161616" />
        <RoundedRect
          x={BLEED + 0.5}
          y={BLEED + 0.5}
          width={WIDTH - 1}
          height={HEIGHT - 1}
          r={RADIUS - 0.5}
          style="stroke"
          strokeWidth={1}
          color="rgba(255,255,255,0.07)"
        />
        <Edge sim={sim} colors={glowColors} cancelled={false} />
        <Edge sim={sim} colors={cancelColors} cancelled />
      </Canvas>

      {/* Until it has the focus, the layer on top decides between a tap and a hold. */}
      <Animated.View style={[styles.field, fieldStyle]} pointerEvents={focused ? 'auto' : 'none'}>
        <TextInput
          ref={input}
          style={styles.input}
          value={current}
          onChangeText={(text) => {
            store(text);
            setTyped(text.length > 0);
          }}
          placeholder={placeholder}
          placeholderTextColor="#9a9a9a"
          selectionColor="#4c8dff"
          cursorColor="#4c8dff"
          returnKeyType="send"
          onSubmitEditing={submit}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
        />
      </Animated.View>

      {/* The caret of the resting field; the system draws its own once the field is focused. */}
      <Animated.View pointerEvents="none" style={[styles.caret, caretStyle]} />

      <ListeningLabel sim={sim} right={false} still={reducedMotion} label={listeningLabel} />
      <ListeningLabel sim={sim} right still={reducedMotion} label={listeningLabel} />

      <Animated.View pointerEvents="none" style={[styles.mic, micStyle]}>
        <Canvas style={styles.micCanvas}>
          <RoundedRect x={9} y={3} width={6} height={11} r={3} color="#ffffff" />
          <Path path={MIC_STAND} style="stroke" strokeWidth={1.7} strokeCap="round" strokeJoin="round" color="#ffffff" />
        </Canvas>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.hint, hintStyle]}>
        <Hint sim={sim} cross={false} label={slideHint} color="#9a9a9a" />
        <Hint sim={sim} cross label={cancelHint} color="#e8e8e8" />
      </Animated.View>

      <GestureDetector gesture={touch}>
        <View
          style={styles.touch}
          pointerEvents={focused ? 'none' : 'auto'}
          accessible
          accessibilityRole="button"
          accessibilityLabel={placeholder}
        />
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: PILL_Y + HEIGHT,
  },
  chips: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: PILL_Y - CHIPS_TO_PILL,
    justifyContent: 'flex-end',
    alignItems: 'flex-start',
    gap: CHIP_GAP,
  },
  chipRow: {
    flexDirection: 'row',
    gap: CHIP_GAP,
  },
  chip: {
    height: CHIP_HEIGHT,
    paddingHorizontal: 16,
    borderRadius: CHIP_HEIGHT / 2,
    backgroundColor: '#1e1e1e',
    justifyContent: 'center',
  },
  // The gradient shows as a one-point line around the chip laid on it.
  chipOutline: {
    padding: 1,
    borderRadius: CHIP_HEIGHT / 2,
  },
  chipLit: {
    height: CHIP_HEIGHT - 2,
    paddingHorizontal: 15,
    backgroundColor: '#1b201e',
  },
  chipText: {
    color: '#bdbdbd',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  chipTextLit: {
    color: '#dcdcdc',
  },
  pill: {
    position: 'absolute',
    left: -BLEED,
    top: PILL_Y - BLEED,
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  field: {
    position: 'absolute',
    left: INSET,
    top: PILL_Y,
    width: WIDTH - INSET * 2,
    height: HEIGHT,
  },
  input: {
    flex: 1,
    padding: 0,
    color: '#ededed',
    fontSize: 16,
    textAlignVertical: 'center',
    includeFontPadding: false,
  },
  caret: {
    position: 'absolute',
    left: INSET - 1,
    top: PILL_Y + HEIGHT / 2 - 10,
    width: 2,
    height: 20,
    borderRadius: 1,
    backgroundColor: '#4c8dff',
  },
  label: {
    position: 'absolute',
    left: INSET,
    top: PILL_Y,
    width: WIDTH - INSET * 2,
    height: HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  labelRight: {
    justifyContent: 'flex-end',
  },
  bars: {
    width: BAR_PITCH * 4 + BAR_WIDTH,
    height: BAR_MAX,
  },
  labelText: {
    color: '#7a7a7a',
    fontSize: 16,
    lineHeight: 21,
  },
  mic: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 24,
    height: 24,
  },
  micCanvas: {
    width: 24,
    height: 24,
  },
  hint: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: HINT_WIDTH,
    height: HINT_HEIGHT,
  },
  hintBody: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: HINT_WIDTH,
    height: HINT_HEIGHT,
    alignItems: 'center',
    gap: 6,
  },
  hintMark: {
    width: 12,
    height: 12,
  },
  hintText: {
    height: 18,
    fontSize: 14,
    lineHeight: 18,
  },
  touch: {
    position: 'absolute',
    left: 0,
    top: PILL_Y,
    width: WIDTH,
    height: HEIGHT,
  },
});
