import { Canvas, Circle, LinearGradient, Path, Rect, RoundedRect, Skia, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path as SvgPath, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled text; leave it out and the field keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Records, previews and sends a voice note in a loop, without opening the keyboard. */
  autoPlay?: boolean;
  placeholder?: string;
  /** The message already in the chat, above the composer. */
  incoming?: string;
  generatingLabel?: string;
  /** What the sent bubble reads once the transcript has arrived. */
  transcript?: string;
  /** Called with the typed text when the send button or the keyboard's send key is pressed. */
  onSend?: (text: string) => void;
  onRecordStart?: () => void;
  /** Called with the length of the take in seconds when stop is pressed. */
  onRecordEnd?: (seconds: number) => void;
  /** Called with the length of the take in seconds when the preview is sent. */
  onSendVoice?: (seconds: number) => void;
  onDiscard?: () => void;
  onAttach?: () => void;
};

const WIDTH = 350;
const HEIGHT = 300;
const RADIUS = 32;
const PAD = 16;
const INCOMING_Y = 24;
const INCOMING_WIDTH = 166;
const INCOMING_HEIGHT = 40;
const PILL_HEIGHT = 44;
// Top of the composer row: the round plus, then the pill.
const ROW_Y = HEIGHT - PAD - PILL_HEIGHT;
const PLUS = 40;
const PILL_X = PAD + PLUS + 8;
const PILL_WIDTH = WIDTH - PAD - PILL_X;
// Inside the pill: the button at its right end, the discard cross, the right edge of the timer.
const MAIN_X = PILL_WIDTH - 4 - 36;
const DISCARD_X = MAIN_X - 30;
const TIMER_RIGHT = MAIN_X - 8;

// The waveform: sixteen bars a second, 2 points wide and 2 apart.
const RATE = 16;
const PITCH = 4;
const BAR_MIN = 3;
const BAR_MAX = 22;
// Seconds a new bar takes to reach its height.
const GROW = 0.12;
// Where the live bars are born and where they have faded out, inside the pill.
const LIVE_RIGHT = TIMER_RIGHT - 44;
const LIVE_LEFT = 14;
const LIVE_FADE = 18;
const LIVE_BARS = 44;
const PREVIEW_X = 44;
const PREVIEW_BARS = 28;
const RIPPLE = 0.35;
const SHORTEST_TAKE = 0.4;

// The sent bubble: 12 points of padding, the play button and the bars, then one line of text.
const BUBBLE_WIDTH = 220;
const BUBBLE_HEIGHT = 82;
const BUBBLE_GAP = 14;
const BUBBLE_BARS = 38;
const BUBBLE_TEXT_Y = 52;
const BUBBLE_TEXT = BUBBLE_WIDTH - 24 - 40;
const TEXT_LINE = 18;
const SHIMMER_WIDTH = 48;
const SHIMMER = 1.1;
// The shimmer's windows: how wide each is, and how much ink its copy of the label adds.
const SHIMMER_WINDOWS = [
  { width: 48, ink: 0.3 },
  { width: 22, ink: 0.45 },
];
// Seconds between the send and the transcript.
const GENERATE = 1.4;

// autoPlay (seconds): each press lasts `PRESS`, and the take is 2.4 s long. The loop is a whole
// number of seconds, so the drawn caret blinks in step with it.
const LOOP = 9;
const RECORD_AT = 0.8;
const STOP_AT = 3.2;
const PLAY_AT = 3.9;
const SEND_AT = 4.9;
const FADE_AT = 8.3;
const PRESS = 0.12;
// After a touch the script waits this long, then starts again from rest.
const RESUME_AFTER = 4;

const INK = '#243447';
const MUTE = 'rgba(36,52,71,0.55)';
const FAINT = 'rgba(36,52,71,0.32)';
const FROST = 'rgba(255,255,255,0.55)';
// The darker disc behind stop, send and the bubble's play button.
const WELL = 'rgba(36,52,71,0.1)';
const SKY = ['#8fa3c4', '#c5b5c6', '#f6c7a0', '#f7b25e'];
const SKY_STOPS = [0, 0.36, 0.68, 1];

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
// The preview lands a little past its place as a bubble and settles.
const LIFT_BACK = Easing.bezier(0.34, 1.4, 0.64, 1);
const SOFT_BACK = Easing.bezier(0.3, 1.3, 0.6, 1);

const PLUS_ICON = 'M9 3.5V14.5M3.5 9H14.5';
const CROSS_ICON = 'M5 5L13 13M13 5L5 13';
const ARROW_ICON = 'M9 14.5V3.5M4.5 8L9 3.5L13.5 8';
// Five bars of a waveform: the icon that starts a take.
const WAVE_ICON = [4, 9, 14, 8, 3].map((height, index) => `M${3 + 3 * index} ${9 - height / 2}V${9 + height / 2}`).join('');
const TRIANGLE_ICON = 'M6 4.2L14 9L6 13.8Z';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** How loud sample `k` of the take is, 0…1: a fixed pattern with the rise and fall of speech. */
function level(k: number) {
  'worklet';
  const phrase = 0.55 + 0.45 * Math.sin(k * 0.45 + 1);
  const grain = 0.3 + 0.7 * Math.abs(Math.sin(k * 2.3 + 0.7) * Math.cos(k * 0.9));
  return Math.min(1, Math.max(0.08, phrase * grain * 1.3));
}

/** One swing that dies away within a third of a second. */
function wobble(t: number) {
  'worklet';
  return t <= 0 ? 0 : Math.sin(22 * t) * Math.exp(-9 * t);
}

/** Seconds as "0:07". */
function clock(seconds: number) {
  const whole = Math.max(0, Math.floor(seconds));
  return `${Math.floor(whole / 60)}:${whole % 60 < 10 ? '0' : ''}${whole % 60}`;
}

/** The bubble at one instant: its box, its corners, the opacity of its fill and of what is written on it. */
function bubbleFrame(lift: number, unfold: number, gone: number, still: boolean) {
  'worklet';
  const height = BUBBLE_HEIGHT + TEXT_LINE * unfold;
  const placeX = WIDTH - PAD - BUBBLE_WIDTH;
  const placeY = ROW_Y - BUBBLE_GAP - height;
  // It starts as the pill itself and lands a little past its place; a fading one drifts up.
  const t = still ? 1 : lift;
  return {
    x: PILL_X + (placeX - PILL_X) * t,
    y: ROW_Y + (placeY - ROW_Y) * t + (still ? 0 : -8 * gone),
    width: PILL_WIDTH + (BUBBLE_WIDTH - PILL_WIDTH) * t,
    height: PILL_HEIGHT + (height - PILL_HEIGHT) * t,
    radius: PILL_HEIGHT / 2 + (18 - PILL_HEIGHT / 2) * clamp01(t),
    fill: Math.min(1, Math.max(lift, 0) * 6) * (1 - gone),
    ink: clamp01((lift - 0.6) / 0.3) * (1 - gone),
  };
}

type Motion = {
  time: SharedValue<number>;
  rec: SharedValue<number>;
  pre: SharedValue<number>;
  lift: SharedValue<number>;
  unfold: SharedValue<number>;
  gone: SharedValue<number>;
  carrying: SharedValue<boolean>;
  recordAt: SharedValue<number>;
  stopAt: SharedValue<number>;
  /** When the preview and the bubble started playing; negative while they are still. */
  previewPlayAt: SharedValue<number>;
  bubblePlayAt: SharedValue<number>;
  /** Seconds of the take in the composer, and of the one in the bubble. */
  take: SharedValue<number>;
  sent: SharedValue<number>;
  still: boolean;
};

/** A bar of the waveform: a round-capped stroke 2 points wide, `amount` of the way to full height. */
function addBar(path: ReturnType<typeof Skia.Path.Make>, x: number, y: number, amount: number) {
  'worklet';
  const half = (BAR_MIN + (BAR_MAX - BAR_MIN) * amount) / 2 - 1;
  path.moveTo(x + 1, y - half);
  path.lineTo(x + 1, y + half);
}

/**
 * A finished take as `bars` bars: the part before the playhead (`side` 0) or after it (`side` 1).
 * `played` is negative when it is not playing, and then every bar is on side 0.
 */
function takePath(bars: number, x: number, y: number, seconds: number, played: number, settle: number, side: number) {
  'worklet';
  const path = Skia.Path.Make();
  const samples = Math.max(1, Math.floor(seconds * RATE));
  for (let index = 0; index < bars; index++) {
    const after = played >= 0 && index >= played * bars;
    if (after !== (side === 1)) continue;
    // Stopping sends one ripple along the bars before they stand still.
    const ripple = 1 + RIPPLE * wobble(settle - index * 0.008);
    addBar(path, x + index * PITCH, y, level(Math.floor((index * samples) / bars)) * ripple);
  }
  return path;
}

/** The sunset panel, the frosted shapes and every bar of the waveforms. */
function Backdrop(m: Motion) {
  const { time, rec, pre, lift, unfold, gone, carrying, recordAt, stopAt, previewPlayAt, bubblePlayAt, take, sent, still } = m;
  const pillY = ROW_Y + PILL_HEIGHT / 2;

  // The take: a bar is born at the right end every sixteenth of a second and travels left.
  const live = useDerivedValue(() => {
    const path = Skia.Path.Make();
    if (rec.get() <= 0) return path;
    const head = Math.max(time.get() - recordAt.get(), 0) * RATE;
    const newest = Math.floor(head) - 1;
    for (let k = Math.max(0, newest - LIVE_BARS); k <= newest; k++) {
      const age = head - (k + 1);
      const grown = 1 - Math.pow(1 - Math.min(age / (GROW * RATE), 1), 3);
      addBar(path, PILL_X + LIVE_RIGHT - age * PITCH, pillY, level(k) * (still ? 1 : grown));
    }
    return path;
  });

  const bubble = useDerivedValue(() => bubbleFrame(lift.get(), unfold.get(), gone.get(), still));
  const bubbleShape = useDerivedValue(() => {
    const b = bubble.get();
    return rrect(rect(b.x, b.y, b.width, b.height), b.radius, b.radius);
  });
  const bubbleFill = useDerivedValue(() => bubble.get().fill);
  const bubbleInk = useDerivedValue(() => bubble.get().ink);

  // The preview: the same take, squeezed between the play button and its length. Once sent, it
  // rides up with the bubble as it fades.
  const previewX = useDerivedValue(() => PILL_X + PREVIEW_X + (carrying.get() ? bubble.get().x - PILL_X : 0));
  const previewY = useDerivedValue(() => pillY + (carrying.get() ? bubble.get().y - ROW_Y : 0));
  const previewPlayed = useDerivedValue(() => (previewPlayAt.get() < 0 ? -1 : clamp01((time.get() - previewPlayAt.get()) / take.get())));
  const previewSettle = useDerivedValue(() => (still ? -1 : time.get() - stopAt.get()));
  const previewBefore = useDerivedValue(() =>
    pre.get() <= 0 ? Skia.Path.Make() : takePath(PREVIEW_BARS, previewX.get(), previewY.get(), take.get(), previewPlayed.get(), previewSettle.get(), 0),
  );
  const previewAfter = useDerivedValue(() =>
    pre.get() <= 0 ? Skia.Path.Make() : takePath(PREVIEW_BARS, previewX.get(), previewY.get(), take.get(), previewPlayed.get(), previewSettle.get(), 1),
  );
  const previewHead = useDerivedValue(() => {
    const played = previewPlayed.get();
    const x = previewX.get() + Math.max(played, 0) * PREVIEW_BARS * PITCH - 1;
    return rrect(rect(x - 0.75, previewY.get() - 14, 1.5, played < 0 ? 0 : 28), 0.75, 0.75);
  });

  const bubblePlayed = useDerivedValue(() => (bubblePlayAt.get() < 0 ? -1 : clamp01((time.get() - bubblePlayAt.get()) / sent.get())));
  const bubbleBefore = useDerivedValue(() =>
    bubbleInk.get() <= 0 ? Skia.Path.Make() : takePath(BUBBLE_BARS, bubble.get().x + 52, bubble.get().y + 28, sent.get(), bubblePlayed.get(), -1, 0),
  );
  const bubbleAfter = useDerivedValue(() =>
    bubbleInk.get() <= 0 ? Skia.Path.Make() : takePath(BUBBLE_BARS, bubble.get().x + 52, bubble.get().y + 28, sent.get(), bubblePlayed.get(), -1, 1),
  );
  const bubbleHead = useDerivedValue(() => {
    const played = bubblePlayed.get();
    const x = bubble.get().x + 52 + Math.max(played, 0) * BUBBLE_BARS * PITCH - 1;
    return rrect(rect(x - 0.75, bubble.get().y + 14, 1.5, played < 0 ? 0 : 28), 0.75, 0.75);
  });

  return (
    <Canvas style={styles.canvas}>
      <Rect x={0} y={0} width={WIDTH} height={HEIGHT}>
        <LinearGradient start={vec(0, 0)} end={vec(0, HEIGHT)} colors={SKY} positions={SKY_STOPS} />
      </Rect>
      <RoundedRect x={PAD} y={INCOMING_Y} width={INCOMING_WIDTH} height={INCOMING_HEIGHT} r={18} color={FROST} />
      <Circle cx={PAD + PLUS / 2} cy={ROW_Y + 2 + PLUS / 2} r={PLUS / 2} color={FROST} />
      <RoundedRect x={PILL_X} y={ROW_Y} width={PILL_WIDTH} height={PILL_HEIGHT} r={PILL_HEIGHT / 2} color={FROST} />

      {/* Bars fade out as they reach the left end of the pill. */}
      <Path path={live} style="stroke" strokeWidth={2} strokeCap="round" opacity={rec}>
        <LinearGradient start={vec(PILL_X + LIVE_LEFT, 0)} end={vec(PILL_X + LIVE_LEFT + LIVE_FADE, 0)} colors={['rgba(36,52,71,0)', INK]} />
      </Path>

      {/* While a take plays, the part behind the playhead keeps its ink. */}
      <Path path={previewBefore} style="stroke" strokeWidth={2} strokeCap="round" color={INK} opacity={pre} />
      <Path path={previewAfter} style="stroke" strokeWidth={2} strokeCap="round" color={FAINT} opacity={pre} />
      <RoundedRect rect={previewHead} color={INK} opacity={pre} />

      <RoundedRect rect={bubbleShape} color={FROST} opacity={bubbleFill} />
      <Path path={bubbleBefore} style="stroke" strokeWidth={2} strokeCap="round" color={INK} opacity={bubbleInk} />
      <Path path={bubbleAfter} style="stroke" strokeWidth={2} strokeCap="round" color={FAINT} opacity={bubbleInk} />
      <RoundedRect rect={bubbleHead} color={INK} opacity={bubbleInk} />
    </Canvas>
  );
}

function Icon({ d, width, color = INK }: { d: string; width: number; color?: string }) {
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18" fill="none">
      <SvgPath d={d} stroke={color} strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

function PlayIcon({ playing }: { playing: boolean }) {
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18">
      {playing ? (
        <>
          <SvgRect x={5.4} y={4.6} width={2} height={8.8} fill={INK} stroke={INK} strokeWidth={1.6} strokeLinejoin="round" />
          <SvgRect x={10.6} y={4.6} width={2} height={8.8} fill={INK} stroke={INK} strokeWidth={1.6} strokeLinejoin="round" />
        </>
      ) : (
        <SvgPath d={TRIANGLE_ICON} fill={INK} stroke={INK} strokeWidth={1.6} strokeLinejoin="round" />
      )}
    </Svg>
  );
}

/** A tappable part of the composer: dips while pressed (or while the script presses it), with a light haptic. */
function Tap({
  label,
  onPress,
  held = false,
  style,
  children,
}: {
  label: string;
  onPress?: () => void;
  held?: boolean;
  style?: ViewStyle;
  children: ReactNode;
}) {
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
          style={[
            styles.tap,
            style,
            {
              transform: [{ scale: pressed || held ? 0.86 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

/** One window of the shimmer: it crosses the label and shows a darker copy of it, so only the letters change. */
function Glint({ label, width, ink, since, still }: { label: string; width: number; ink: number; since: SharedValue<number>; still: boolean }) {
  const x = useDerivedValue(() => {
    const sweep = (Math.max(since.get(), 0) % SHIMMER) / SHIMMER;
    return -SHIMMER_WIDTH + (BUBBLE_TEXT + SHIMMER_WIDTH) * sweep + (SHIMMER_WIDTH - width) / 2;
  });
  const windowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: x.get() }] }));
  const copyStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -x.get() }] }));
  if (still) return null;
  return (
    <Animated.View style={[styles.glint, { width }, windowStyle]}>
      <Animated.Text style={[styles.note, styles.glintCopy, { opacity: ink }, copyStyle]} numberOfLines={1}>
        {label}
      </Animated.Text>
    </Animated.View>
  );
}

/**
 * Frosted chat composer on a sunset panel that records a voice note in place. The waveform icon
 * starts a take: bars scroll in from the right under a running timer. Stop turns it into a preview
 * with a play button and a scrubber; send lifts it out of the composer into a bubble that shows
 * "Generating transcript…" under a shimmer, then unfolds the transcript.
 *
 * Nothing is recorded: the waveform is a fixed pattern drawn as if it were live, and
 * `onRecordStart` / `onRecordEnd` are where a real recorder is connected.
 */
export function VoiceNoteComposer({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'What’s on your mind',
  incoming = 'How was the flight?',
  generatingLabel = 'Generating transcript…',
  transcript = 'Just landed in Lisbon. The light here is unreal!',
  onSend,
  onRecordStart,
  onRecordEnd,
  onSendVoice,
  onDiscard,
  onAttach,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion;

  const [draft, setDraft] = useState('');
  const [phase, setPhase] = useState<'idle' | 'recording' | 'preview'>('idle');
  // True while the preview, or the sent bubble, plays.
  const [previewPlaying, setPreviewPlaying] = useState(false);
  const [bubblePlaying, setBubblePlaying] = useState(false);
  const [transcribed, setTranscribed] = useState(false);
  // Whole seconds of the running take, then the length of the finished one and of the sent one.
  const [seconds, setSeconds] = useState(0);
  const [takeSeconds, setTakeSeconds] = useState(0);
  const [sentSeconds, setSentSeconds] = useState(0);
  // The button the autoPlay script is pressing.
  const [scriptPress, setScriptPress] = useState<'main' | 'play' | null>(null);
  // False once a touch has taken the composer from the autoPlay script.
  const [handsOff, setHandsOff] = useState(true);
  const [touches, setTouches] = useState(0);
  const input = useRef<TextInput>(null);
  const focused = useRef(false);
  const sendTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const previewTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const bubbleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const playing = auto && handsOff;
  const text = value ?? draft;
  const hasText = text.length > 0;

  // Seconds since the composer appeared, and the moments its drawing is timed from.
  const time = useSharedValue(0);
  // 0…1 each: how much of the take, of the preview and of the typed text's send arrow shows.
  const rec = useSharedValue(0);
  const pre = useSharedValue(0);
  const arm = useSharedValue(0);
  // 0…1 with an overshoot: the preview on its way from the composer to its place as a bubble.
  const lift = useSharedValue(0);
  const unfold = useSharedValue(0);
  // 0…1: the bubble fading away.
  const gone = useSharedValue(1);
  // True from a send until the next take: the preview rides up with the bubble as it fades.
  const carrying = useSharedValue(false);
  const recording = useSharedValue(false);
  const recordAt = useSharedValue(-100);
  const stopAt = useSharedValue(-100);
  const previewPlayAt = useSharedValue(-1);
  const bubblePlayAt = useSharedValue(-1);
  const sentAt = useSharedValue(-100);
  const take = useSharedValue(1);
  const sent = useSharedValue(1);
  const sinceSent = useDerivedValue(() => time.get() - sentAt.get());

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
  });

  useAnimatedReaction(
    () => (recording.get() ? Math.max(0, Math.floor(time.get() - recordAt.get())) : -1),
    (now, before) => {
      if (now >= 0 && now !== before) scheduleOnRN(setSeconds, now);
    },
  );

  useEffect(() => {
    arm.set(withTiming(hasText ? 1 : 0, { duration: 150, easing: EASE_OUT }));
  }, [hasText, arm]);

  useEffect(
    () => () => {
      for (const timer of [sendTimer, previewTimer, bubbleTimer]) if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const startRecording = useCallback(() => {
    setPhase('recording');
    setSeconds(0);
    input.current?.blur();
    recordAt.set(time.get());
    recording.set(true);
    rec.set(withTiming(1, { duration: 200, easing: EASE_OUT }));
  }, [rec, recordAt, recording, time]);

  /** Ends the take and returns its length in seconds. */
  const stopRecording = useCallback(() => {
    const now = time.get();
    const length = Math.max(SHORTEST_TAKE, now - recordAt.get());
    take.set(length);
    stopAt.set(now);
    recording.set(false);
    carrying.set(false);
    setTakeSeconds(length);
    setPhase('preview');
    rec.set(withTiming(0, { duration: 180, easing: EASE_OUT }));
    pre.set(withTiming(1, { duration: 220, easing: EASE_OUT }));
    return length;
  }, [carrying, pre, rec, recordAt, recording, stopAt, take, time]);

  const stopPreview = useCallback(() => {
    if (previewTimer.current) clearTimeout(previewTimer.current);
    previewPlayAt.set(-1);
    setPreviewPlaying(false);
  }, [previewPlayAt]);

  const togglePreview = useCallback(() => {
    if (previewPlayAt.get() >= 0) {
      stopPreview();
      return;
    }
    previewPlayAt.set(time.get());
    setPreviewPlaying(true);
    previewTimer.current = setTimeout(stopPreview, take.get() * 1000);
  }, [previewPlayAt, stopPreview, take, time]);

  const stopBubble = useCallback(() => {
    if (bubbleTimer.current) clearTimeout(bubbleTimer.current);
    bubblePlayAt.set(-1);
    setBubblePlaying(false);
  }, [bubblePlayAt]);

  const toggleBubble = () => {
    if (bubblePlayAt.get() >= 0) {
      stopBubble();
      return;
    }
    bubblePlayAt.set(time.get());
    setBubblePlaying(true);
    bubbleTimer.current = setTimeout(stopBubble, sent.get() * 1000);
  };

  const discard = () => {
    setPhase('idle');
    stopPreview();
    pre.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
  };

  /** The preview leaves the composer and becomes the bubble; the transcript follows by itself. Returns its length. */
  const sendVoice = useCallback(() => {
    const length = take.get();
    sent.set(length);
    sentAt.set(time.get());
    setSentSeconds(length);
    setPhase('idle');
    stopPreview();
    stopBubble();
    setTranscribed(false);
    carrying.set(true);
    pre.set(withTiming(0, { duration: 300, easing: EASE_OUT }));
    lift.set(0);
    lift.set(withTiming(1, reducedMotion ? { duration: 200, easing: EASE_OUT } : { duration: 500, easing: LIFT_BACK }));
    unfold.set(0);
    gone.set(0);
    if (sendTimer.current) clearTimeout(sendTimer.current);
    sendTimer.current = setTimeout(() => {
      setTranscribed(true);
      unfold.set(withTiming(1, { duration: 450, easing: SOFT_BACK }));
    }, GENERATE * 1000);
    return length;
  }, [reducedMotion, carrying, gone, lift, pre, sent, sentAt, stopBubble, stopPreview, take, time, unfold]);

  const fadeBubble = useCallback(() => {
    if (sendTimer.current) clearTimeout(sendTimer.current);
    stopBubble();
    gone.set(withTiming(1, { duration: 350, easing: EASE_IN_OUT }));
  }, [gone, stopBubble]);

  /** Back to the empty composer, whatever it was doing. */
  const reset = useCallback(() => {
    setPhase('idle');
    stopPreview();
    recording.set(false);
    rec.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
    pre.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
  }, [pre, rec, recording, stopPreview]);

  // autoPlay: record, stop, play a little, send; the bubble fades before the loop closes.
  useEffect(() => {
    if (!playing) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const epoch = Date.now();
    const loop = (lap: number) => {
      const at = (after: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, epoch + (lap * LOOP + after) * 1000 - Date.now())));
      };
      const press = (after: number, control: 'main' | 'play', step: () => void) => {
        at(after, () => setScriptPress(control));
        at(after + PRESS, () => {
          setScriptPress(null);
          step();
        });
      };
      press(RECORD_AT, 'main', startRecording);
      press(STOP_AT, 'main', stopRecording);
      press(PLAY_AT, 'play', togglePreview);
      press(SEND_AT, 'main', sendVoice);
      at(FADE_AT, fadeBubble);
      at(LOOP, () => {
        timers.length = 0;
        loop(lap + 1);
      });
    };
    loop(0);
    return () => timers.forEach(clearTimeout);
  }, [playing, fadeBubble, sendVoice, startRecording, stopRecording, togglePreview]);

  // Any touch stops the script; it comes back a few seconds after the last one, or after a take or
  // a transcript that nobody touches. A take in progress is never cut short.
  useEffect(() => {
    if (!auto || handsOff || phase === 'recording') return;
    const timer = setTimeout(() => {
      if (focused.current) return;
      reset();
      fadeBubble();
      setDraft('');
      setHandsOff(true);
    }, RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [auto, handsOff, phase, transcribed, touches, text, fadeBubble, reset]);

  const touch = () => {
    setHandsOff(false);
    setScriptPress(null);
    setTouches((count) => count + 1);
  };

  const edit = (next: string) => {
    if (!controlled) setDraft(next);
    onChangeText?.(next);
  };

  const sendText = () => {
    if (!hasText) return;
    edit('');
    onSend?.(text);
  };

  /** The button at the right end of the pill: it starts a take, stops it, or sends what is there. */
  const pressMain = () => {
    touch();
    if (phase === 'recording') onRecordEnd?.(stopRecording());
    else if (phase === 'preview') onSendVoice?.(sendVoice());
    else if (hasText) sendText();
    else {
      startRecording();
      onRecordStart?.();
    }
  };

  const idleStyle = useAnimatedStyle(() => ({ opacity: (1 - rec.get()) * (1 - pre.get()) }));
  const recStyle = useAnimatedStyle(() => ({ opacity: rec.get() }));
  const preStyle = useAnimatedStyle(() => ({ opacity: pre.get() }));
  const carriedStyle = useAnimatedStyle(() => {
    const b = bubbleFrame(lift.get(), unfold.get(), gone.get(), reducedMotion);
    return {
      opacity: pre.get(),
      transform: [{ translateX: carrying.get() ? b.x - PILL_X : 0 }, { translateY: carrying.get() ? b.y - ROW_Y : 0 }],
    };
  });
  const caretStyle = useAnimatedStyle(() => ({ opacity: time.get() % 1 < 0.55 ? 1 : 0 }));
  const wellStyle = useAnimatedStyle(() => {
    const idle = (1 - rec.get()) * (1 - pre.get());
    return { opacity: Math.max(rec.get(), pre.get(), idle * arm.get()) };
  });
  const waveStyle = useAnimatedStyle(() => {
    const wave = (1 - rec.get()) * (1 - pre.get()) * (1 - arm.get());
    return { opacity: wave, transform: [{ scale: 0.6 + 0.4 * wave }] };
  });
  const stopStyle = useAnimatedStyle(() => ({ opacity: rec.get(), transform: [{ scale: 0.6 + 0.4 * rec.get() }] }));
  const arrowStyle = useAnimatedStyle(() => {
    const arrow = Math.max(pre.get(), (1 - rec.get()) * (1 - pre.get()) * arm.get());
    return { opacity: arrow, transform: [{ scale: 0.6 + 0.4 * arrow }] };
  });
  const bubbleStyle = useAnimatedStyle(() => {
    const b = bubbleFrame(lift.get(), unfold.get(), gone.get(), reducedMotion);
    return { left: b.x, top: b.y, height: b.height, opacity: b.ink };
  });
  const labelStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, unfold.get() * 3) }));
  // The transcript is uncovered from its first line down as the bubble grows.
  const transcriptStyle = useAnimatedStyle(() => ({
    height: TEXT_LINE * (1 + clamp01(unfold.get())),
    opacity: clamp01(unfold.get() * 1.5),
  }));

  const motion: Motion = { time, rec, pre, lift, unfold, gone, carrying, recordAt, stopAt, previewPlayAt, bubblePlayAt, take, sent, still: reducedMotion };
  const mainLabel = phase === 'recording' ? 'Stop recording' : phase === 'preview' || hasText ? 'Send' : 'Record a voice note';

  return (
    <View style={styles.panel}>
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Backdrop {...motion} />
      </View>

      <View pointerEvents="none" style={styles.incoming}>
        <Text style={styles.message} numberOfLines={1}>
          {incoming}
        </Text>
      </View>

      {/* What sits on the sent bubble: its play button, the label or the transcript, and the length. */}
      <Animated.View style={[styles.bubble, bubbleStyle]}>
        <View style={styles.bubblePlay}>
          <Tap
            label={bubblePlaying ? 'Stop' : 'Play voice note'}
            style={styles.bubbleWell}
            onPress={() => {
              touch();
              toggleBubble();
            }}
          >
            <PlayIcon playing={bubblePlaying} />
          </Tap>
        </View>
        <Animated.View style={[styles.label, labelStyle]}>
          <Text style={[styles.note, styles.mute]} numberOfLines={1}>
            {generatingLabel}
          </Text>
          {SHIMMER_WINDOWS.map((window) => (
            <Glint key={window.width} label={generatingLabel} width={window.width} ink={window.ink} since={sinceSent} still={reducedMotion} />
          ))}
        </Animated.View>
        {transcribed && (
          <Animated.View style={[styles.transcript, transcriptStyle]}>
            <Text style={[styles.note, styles.lines]} numberOfLines={2}>
              {transcript}
            </Text>
          </Animated.View>
        )}
        <Text style={styles.length} numberOfLines={1}>
          {clock(sentSeconds)}
        </Text>
      </Animated.View>

      <View style={styles.plus}>
        <Tap label="Attach" style={styles.plusTap} onPress={onAttach}>
          <Icon d={PLUS_ICON} width={1.8} />
        </Tap>
      </View>

      <Animated.View style={[styles.input, idleStyle]} pointerEvents={phase === 'idle' ? 'auto' : 'none'}>
        {!hasText && (
          <Text style={[styles.placeholder, styles.mute]} numberOfLines={1}>
            {placeholder}
          </Text>
        )}
        <TextInput
          ref={input}
          style={styles.text}
          value={text}
          onChangeText={(next) => {
            touch();
            edit(next);
          }}
          onFocus={() => {
            // A tap on the field takes the composer over from the script and starts from rest.
            if (playing) reset();
            focused.current = true;
            touch();
          }}
          onBlur={() => {
            focused.current = false;
            touch();
          }}
          onSubmitEditing={sendText}
          returnKeyType="send"
          submitBehavior="blurAndSubmit"
          selectionColor={INK}
          cursorColor={INK}
          underlineColorAndroid="transparent"
          accessibilityLabel={placeholder}
        />
        {/* The script does not focus the field, so it draws the caret a focused one shows. */}
        {playing && phase === 'idle' && <Animated.View pointerEvents="none" style={[styles.caret, caretStyle]} />}
      </Animated.View>

      <Animated.Text pointerEvents="none" style={[styles.timer, recStyle]} numberOfLines={1}>
        {clock(phase === 'recording' ? seconds : takeSeconds)}
      </Animated.Text>
      <Animated.Text pointerEvents="none" style={[styles.timer, styles.duration, carriedStyle]} numberOfLines={1}>
        {clock(takeSeconds)}
      </Animated.Text>

      <Animated.View style={[styles.play, carriedStyle]} pointerEvents={phase === 'preview' ? 'auto' : 'none'}>
        <Tap
          label={previewPlaying ? 'Stop' : 'Play'}
          held={scriptPress === 'play'}
          onPress={() => {
            touch();
            togglePreview();
          }}
        >
          <PlayIcon playing={previewPlaying} />
        </Tap>
      </Animated.View>

      <Animated.View style={[styles.discard, preStyle]} pointerEvents={phase === 'preview' ? 'auto' : 'none'}>
        <Tap
          label="Discard"
          style={styles.discardTap}
          onPress={() => {
            touch();
            discard();
            onDiscard?.();
          }}
        >
          <Icon d={CROSS_ICON} width={1.7} color={MUTE} />
        </Tap>
      </Animated.View>

      <View style={styles.main}>
        <Tap label={mainLabel} held={scriptPress === 'main'} onPress={pressMain}>
          <Animated.View style={[styles.well, wellStyle]} />
          <Animated.View style={[styles.glyph, waveStyle]}>
            <Icon d={WAVE_ICON} width={1.8} />
          </Animated.View>
          <Animated.View style={[styles.glyph, stopStyle]}>
            <View style={styles.stop} />
          </Animated.View>
          <Animated.View style={[styles.glyph, arrowStyle]}>
            <Icon d={ARROW_ICON} width={1.9} />
          </Animated.View>
        </Tap>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  canvas: {
    width: WIDTH,
    height: HEIGHT,
  },
  incoming: {
    position: 'absolute',
    left: PAD,
    top: INCOMING_Y,
    height: INCOMING_HEIGHT,
    paddingHorizontal: 14,
    justifyContent: 'center',
  },
  message: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
  },
  mute: {
    color: MUTE,
  },
  bubble: {
    position: 'absolute',
    width: BUBBLE_WIDTH,
  },
  bubblePlay: {
    position: 'absolute',
    left: 12,
    top: 12,
  },
  bubbleWell: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: WELL,
  },
  label: {
    position: 'absolute',
    left: 12,
    top: BUBBLE_TEXT_Y,
    width: BUBBLE_TEXT,
    height: TEXT_LINE,
    overflow: 'hidden',
  },
  note: {
    color: INK,
    fontSize: 13,
    lineHeight: TEXT_LINE,
  },
  glint: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: TEXT_LINE,
    overflow: 'hidden',
  },
  glintCopy: {
    width: BUBBLE_TEXT,
  },
  transcript: {
    position: 'absolute',
    left: 12,
    top: BUBBLE_TEXT_Y,
    width: BUBBLE_TEXT,
    overflow: 'hidden',
  },
  lines: {
    height: TEXT_LINE * 2,
  },
  length: {
    position: 'absolute',
    right: 12,
    bottom: 12,
    width: 36,
    color: MUTE,
    fontSize: 12,
    lineHeight: TEXT_LINE,
    textAlign: 'right',
    fontVariant: ['tabular-nums'],
  },
  tap: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  plus: {
    position: 'absolute',
    left: PAD,
    top: ROW_Y + 2,
  },
  plusTap: {
    width: PLUS,
    height: PLUS,
  },
  input: {
    position: 'absolute',
    left: PILL_X + 16,
    top: ROW_Y,
    width: PILL_WIDTH - 16 - 44,
    height: PILL_HEIGHT,
    justifyContent: 'center',
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    fontSize: 15,
    lineHeight: 20,
  },
  text: {
    height: PILL_HEIGHT,
    padding: 0,
    color: INK,
    fontSize: 15,
  },
  caret: {
    position: 'absolute',
    left: -2,
    width: 1.5,
    height: 20,
    borderRadius: 1,
    backgroundColor: INK,
  },
  timer: {
    position: 'absolute',
    left: PILL_X + TIMER_RIGHT - 40,
    top: ROW_Y + (PILL_HEIGHT - 18) / 2,
    width: 40,
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    textAlign: 'right',
    fontVariant: ['tabular-nums'],
  },
  duration: {
    left: PILL_X + DISCARD_X - 2 - 34,
    width: 34,
  },
  play: {
    position: 'absolute',
    left: PILL_X + 4,
    top: ROW_Y + 4,
  },
  discard: {
    position: 'absolute',
    left: PILL_X + DISCARD_X,
    top: ROW_Y + 7,
  },
  discardTap: {
    width: 30,
    height: 30,
  },
  main: {
    position: 'absolute',
    left: PILL_X + MAIN_X,
    top: ROW_Y + 4,
  },
  well: {
    ...StyleSheet.absoluteFill,
    borderRadius: 18,
    backgroundColor: WELL,
  },
  glyph: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stop: {
    width: 12,
    height: 12,
    borderRadius: 3.5,
    backgroundColor: INK,
  },
});
