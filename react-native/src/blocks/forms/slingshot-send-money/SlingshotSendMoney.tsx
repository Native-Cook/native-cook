import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, LinearGradient, Path, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  type WithSpringConfig,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = {
  /** What is sent, in units of the currency. */
  amount?: number;
  /** Written before the amount. */
  symbol?: string;
  /** Who it goes to: the name under the picture, and the letters drawn in it. */
  recipient?: string;
  initials?: string;
  /** The line under the amount at rest, and once the pull is deep enough to send. */
  hint?: string;
  armedHint?: string;
  sentLabel?: string;
  doneLabel?: string;
  /** Pulls, lets go and presses Done by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** The screen at rest, top to bottom: one colour per entry of STOPS. */
  colors?: string[];
  /** The screen at a full pull, top to bottom: as many colours as `colors`. */
  heatColors?: string[];
  /** The light that grows under the recipient as the screen heats. */
  glowColor?: string;
  avatarColor?: string;
  avatarInk?: string;
  /** The disc behind the check. */
  successColor?: string;
  /** Called when the amount is let go past the threshold and flies off. */
  onSend?: (amount: number) => void;
  /** Called when the check shows. */
  onSent?: (amount: number) => void;
  /** Called when Done is pressed. */
  onDone?: () => void;
  onClose?: () => void;
  onEdit?: () => void;
};

// The screen is a sheet of satin: bands of rose and near-black brown, top to bottom. Each stop has a
// height at rest and one at a full pull, so the bands stretch down with the amount.
const STOPS = [0, 0.13, 0.21, 0.29, 0.37, 0.47, 0.53, 0.59, 0.645, 0.72, 0.79, 0.86, 0.94, 1];
const STOPS_PULLED = [0, 0.19, 0.33, 0.45, 0.54, 0.61, 0.66, 0.71, 0.76, 0.81, 0.86, 0.91, 0.96, 1];
const SATIN = [
  '#85625f', '#82605e', '#6b504e', '#453233', '#33282a', '#2f2526', '#402e30',
  '#694c4e', '#735553', '#594546', '#483436', '#4a2f31', '#5b3132', '#5e3030',
];
// The same sheet heated: amber floods the top two thirds and the brown below turns warmer.
const HEAT = [
  '#c9801f', '#c2781f', '#b0691d', '#8f561b', '#6e431c', '#5f3d2a', '#6a4a3e',
  '#5d423c', '#47302c', '#3a2622', '#3d2420', '#45241e', '#5a2c28', '#693229',
];
const GLOW = '#ffb347';
const GLOW_ALPHA = 0.3;
const INK = '#ffffff';
const HINT_ALPHA = 0.6;
const ARMED_ALPHA = 0.9;
const ROUND_FILL = 'rgba(255,255,255,0.13)';
const ROUND_PRESSED = 'rgba(255,255,255,0.26)';
const PILL_FILL = '#87595a';
const PILL_PRESSED = '#6c4648';
const AVATAR_FILL = '#f3cfcb';
const AVATAR_INK = '#6e4b4a';
const SUCCESS = '#7fe62c';

// Sizes in points, for a 402 × 874 screen.
const AVATAR = 68;
const AVATAR_TOP = 6;
const AMOUNT_SIZE = 64;
const AMOUNT_LINE = 76;
const HINT_SIZE = 22;
const HINT_LINE = 28;
const HINT_TRACKING = 10;
const ARMED_TRACKING = 6;
const ROUND = 56;
const ROUND_MARGIN = 20;
const CHECK = 62;
const PILL_WIDTH = 184;
const PILL_HEIGHT = 52;
// Where things sit, as fractions of the screen's height.
const AMOUNT_AT = 0.37;
const HINT_AT = 0.578;
const BOTTOM_ROW_AT = 0.885;
const SENT_GAP = 12;

// The pull. The amount follows the finger at FINGER_RATIO until it has gone AMOUNT_TRAVEL of the
// screen's height, then resists: at most OVERPULL points more. The hint goes HINT_FOLLOW as far.
const AMOUNT_TRAVEL = 0.245;
const FINGER_RATIO = 0.8;
const OVERPULL = 56;
const HINT_FOLLOW = 0.62;
// Let go at this much of the travel or more, and it sends.
const ARM_AT = 0.72;
// Sideways the block follows the finger a little and leans as if it hung from the recipient.
const SIDE_RATIO = 0.32;
const SIDE_MAX = 46;
const TILT_ARM = 420;

// Nothing is tied to the finger: every copy of the text chases the pull on a spring of its own. The
// first one is the text itself; the softer ones behind it are its ghosts, which show only while
// they lag, so that moving text smears and resting text is sharp.
const AMOUNT_SPRINGS: WithSpringConfig[] = [
  { mass: 1, stiffness: 170, damping: 16 },
  { mass: 1, stiffness: 118, damping: 13.5 },
  { mass: 1, stiffness: 84, damping: 11.5 },
  { mass: 1, stiffness: 60, damping: 10 },
];
const HINT_SPRINGS: WithSpringConfig[] = [
  { mass: 1, stiffness: 110, damping: 14 },
  { mass: 1, stiffness: 80, damping: 12 },
  { mass: 1, stiffness: 58, damping: 10.5 },
  { mass: 1, stiffness: 42, damping: 9 },
];
const GHOST_ALPHA = [1, 0.36, 0.24, 0.14];
// A ghost is fully out when it trails the pull by this many points.
const GHOST_AT = 22;
// Text that lags is strained: taller and narrower, the most at STRAIN_AT points behind.
const STRAIN_AT = 150;
const STRETCH = 0.24;
const SQUEEZE = 0.07;
// Fired, the amount shrinks to this on its way to the recipient, and fades out over this part of
// the way, so that it is gone as it reaches the name.
const FLOWN_SCALE = 0.4;
const FADE_FROM = 0.3;
const FADE_TO = 0.72;
const HEAT_SPRING: WithSpringConfig = { mass: 1, stiffness: 130, damping: 20 };

// The hint's two wordings cross-fade in this many ms.
const WORDING = 140;

// After the release, in ms.
const HINT_OUT = 220;
const CATCH_AFTER = 150;
const CATCH_IN = 90;
const CATCH_SPRING: WithSpringConfig = { mass: 1, stiffness: 260, damping: 12 };
const CATCH_SCALE = 0.1;
const CONFIRM_AFTER = 900;
const POP: WithSpringConfig = { mass: 1, stiffness: 260, damping: 15 };
const TICK_AFTER = 120;
const TICK_FOR = 220;
const FADE = 250;
const PILL_SPRING: WithSpringConfig = { mass: 1, stiffness: 190, damping: 19 };
const PILL_OUT = 260;
// After Done.
const CHECK_OUT = 180;
const HINT_BACK_AFTER = 320;
const HINT_BACK = 350;
// Done to the screen at rest again, in seconds.
const RETURN = 1;

/** autoPlay, in seconds from the start of a loop: a short pull that springs back, then a full one. */
const LOOP = 9;
const TEST_AT = 0.8;
const TEST_FOR = 0.5;
const TEST_UP = 1.45;
const TEST_DEPTH = 0.42;
const PULL_AT = 2.3;
const PULL_FOR = 0.95;
const PULL_UP = 4.3;
// While it holds, the finger trembles by this share of the pull and wanders this far sideways.
const TREMBLE = 0.02;
const TREMBLE_SPEED = 9;
const WANDER = 34;
const DONE_AT = 7;
const PRESS_FOR = 0.15;
/** After a touch autoPlay waits this long, and for the screen to be idle, before it starts over. */
const USER_HOLD = 3;

// A check in a CHECK × CHECK box.
const TICK = 'M19.5 32.5L27.5 40L43 23.5';

type Stage = 'idle' | 'sending' | 'sent';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

/** How far the amount is drawn down for a finger that went `dy` points. */
function pullFor(dy: number, travel: number) {
  'worklet';
  const y = Math.max(0, dy) * FINGER_RATIO;
  if (y <= travel) return y;
  const over = y - travel;
  return travel + (OVERPULL * over) / (over + OVERPULL);
}

function channels(hex: string) {
  const value = parseInt(hex.slice(1), 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

function money(symbol: string, value: number) {
  return `${symbol}${value.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`;
}

function haptic(kind: 'arm' | 'disarm' | 'sent' | 'press') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'sent') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(kind === 'arm' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type EchoProps = {
  /** Where the text is drawn to, in points from its place at rest. */
  aimX: SharedValue<number>;
  aimY: SharedValue<number>;
  spring: WithSpringConfig;
  /** 0 for the text itself, 1 and up for its ghosts. */
  order: number;
  /** How far above its place the text has shrunk to FLOWN_SCALE and faded out; 0 if it never does. */
  rise: number;
  shown: SharedValue<number>;
  top: number;
  height: number;
  children: ReactNode;
};

/** One copy of a line of text, chasing the pull on its own spring. */
function Echo({ aimX, aimY, spring, order, rise, shown, top, height, children }: EchoProps) {
  const x = useDerivedValue(() => withSpring(aimX.get(), spring));
  const y = useDerivedValue(() => withSpring(aimY.get(), spring));
  const style = useAnimatedStyle(() => {
    const lag = Math.abs(aimY.get() - y.get());
    const strain = Math.min(1, lag / STRAIN_AT);
    const up = rise > 0 ? clamp01(-y.get() / rise) : 0;
    const scale = 1 - (1 - FLOWN_SCALE) * up;
    const trailing = order === 0 ? 1 : Math.min(1, lag / GHOST_AT);
    return {
      opacity: shown.get() * GHOST_ALPHA[order] * trailing * (1 - smooth(up, FADE_FROM, FADE_TO)),
      transform: [
        { translateX: x.get() },
        { translateY: y.get() },
        { rotate: `${-x.get() / TILT_ARM}rad` },
        { scaleX: scale * (1 - SQUEEZE * strain) },
        { scaleY: scale * (1 + STRETCH * strain) },
      ],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.echo, { top, height }, style]}>
      {children}
    </Animated.View>
  );
}

type HintProps = { armed: SharedValue<number>; hint: string; armedHint: string };

/** The line under the amount: one wording fades into the other when the pull is deep enough. */
function Hint({ armed, hint, armedHint }: HintProps) {
  const restStyle = useAnimatedStyle(() => ({ opacity: HINT_ALPHA * (1 - armed.get()) }));
  const armedStyle = useAnimatedStyle(() => ({ opacity: ARMED_ALPHA * armed.get() }));
  return (
    <>
      <Animated.Text style={[styles.hint, styles.hintRest, restStyle]} numberOfLines={1}>
        {hint}
      </Animated.Text>
      <Animated.Text style={[styles.hint, styles.hintArmed, armedStyle]} numberOfLines={1}>
        {armedHint}
      </Animated.Text>
    </>
  );
}

type RoundProps = { icon: 'close' | 'pencil'; size: number; label: string; onPress: () => void };

function Round({ icon, size, label, onPress }: RoundProps) {
  return (
    <Pressable onPress={onPress} hitSlop={8} accessibilityRole="button" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.round,
            {
              backgroundColor: pressed ? ROUND_PRESSED : ROUND_FILL,
              transform: [{ scale: pressed ? 0.92 : 1 }],
              transitionProperty: ['backgroundColor', 'transform'],
              transitionDuration: 120,
            },
          ]}
        >
          <Ionicons name={icon} size={size} color={INK} />
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A send-money screen with one gesture: pull the amount down like the pouch of a slingshot and let
 * go. The text chases the finger on soft springs and smears while it moves, the screen heats to
 * amber with the pull, and a release past the threshold fires the amount up into the recipient,
 * followed by a check. A shorter pull springs back. Nothing is paid here: `onSend` is where the real
 * request goes.
 */
export function SlingshotSendMoney({
  amount = 4.5,
  symbol = '$',
  recipient = 'Dave',
  initials,
  hint = 'Swipe  down',
  armedHint = 'Release to send',
  sentLabel = 'Sent',
  doneLabel = 'Done',
  autoPlay = false,
  colors = SATIN,
  heatColors = HEAT,
  glowColor = GLOW,
  avatarColor = AVATAR_FILL,
  avatarInk = AVATAR_INK,
  successColor = SUCCESS,
  onSend,
  onSent,
  onDone,
  onClose,
  onEdit,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const [stage, setStage] = useState<Stage>('idle');
  // Done held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const avatarTop = insets.top + AVATAR_TOP;
  const amountAt = height * AMOUNT_AT;
  const hintAt = height * HINT_AT;
  const travel = height * AMOUNT_TRAVEL;
  // The finger's way down for a full pull.
  const reach = travel / FINGER_RATIO;
  // From the amount's place up to the middle of the recipient's picture.
  const rise = amountAt - (avatarTop + AVATAR / 2);
  const rowBottom = Math.max(insets.bottom + 12, height * (1 - BOTTOM_ROW_AT) - ROUND / 2);

  // Where the amount is drawn to, in points from its place at rest.
  const aimX = useSharedValue(0);
  const aimY = useSharedValue(0);
  // 1 from the release that sends until Done.
  const flown = useSharedValue(0);
  // A finger is doing this, not the autoPlay loop: haptics are for fingers.
  const byHand = useSharedValue(false);
  const amountShown = useSharedValue(1);
  const hintShown = useSharedValue(1);
  const avatarShown = useSharedValue(1);
  // The recipient's picture gives when the amount hits it.
  const caught = useSharedValue(0);
  const check = useSharedValue(0);
  const tick = useSharedValue(0);
  const sentShown = useSharedValue(0);
  const pill = useSharedValue(0);
  // Seconds into the autoPlay loop; negative while it is off.
  const clock = useSharedValue(-1);
  // A finger is on the screen.
  const holding = useSharedValue(false);

  const hintAimY = useDerivedValue(() => (flown.get() ? 0 : aimY.get() * HINT_FOLLOW));
  const armed = useDerivedValue(() => (flown.get() === 0 && aimY.get() >= travel * ARM_AT ? 1 : 0));
  // Which wording the hint shows; it keeps the armed one while it fades after the release.
  const wording = useDerivedValue<number>(() => withTiming(flown.get() ? 1 : armed.get(), { duration: WORDING, reduceMotion: ReduceMotion.Never }));
  // How hot the screen is: the pull as a share of its travel, a little behind it.
  const heat = useDerivedValue(() => withSpring(flown.get() ? 0 : Math.max(0, aimY.get()) / travel, HEAT_SPRING));

  const rest = useMemo(() => colors.map(channels), [colors]);
  const hot = useMemo(() => heatColors.map(channels), [heatColors]);
  const sheetColors = useDerivedValue(() => {
    const h = clamp01(heat.get());
    const out: string[] = [];
    for (let i = 0; i < rest.length; i++) {
      const a = rest[i];
      const b = hot[i];
      out.push(`rgb(${Math.round(a[0] + (b[0] - a[0]) * h)},${Math.round(a[1] + (b[1] - a[1]) * h)},${Math.round(a[2] + (b[2] - a[2]) * h)})`);
    }
    return out;
  });
  const sheetStops = useDerivedValue(() => {
    const h = clamp01(heat.get());
    const out: number[] = [];
    for (let i = 0; i < STOPS.length; i++) out.push(STOPS[i] + (STOPS_PULLED[i] - STOPS[i]) * h);
    return out;
  });
  const glowOpacity = useDerivedValue(() => GLOW_ALPHA * clamp01(heat.get()));

  /** The same for a finger and for the autoPlay loop: draw the amount to where a drag of dx, dy puts it. */
  const pullTo = (dx: number, dy: number) => {
    'worklet';
    if (flown.get()) return;
    aimX.set(Math.max(-SIDE_MAX, Math.min(SIDE_MAX, dx * SIDE_RATIO)));
    aimY.set(pullFor(dy, travel));
  };

  const sending = () => {
    setStage('sending');
    onSend?.(amount);
  };

  const arrived = (felt: boolean) => {
    setStage('sent');
    if (felt) haptic('sent');
    onSent?.(amount);
  };

  /** The release that sends: the amount flies to the recipient, then the check. */
  const fire = () => {
    'worklet';
    if (flown.get()) return;
    flown.set(1);
    aimX.set(0);
    // Under reduced motion the amount fades where it is; otherwise it is drawn up into the picture.
    if (reducedMotion) amountShown.set(withTiming(0, { duration: FADE, reduceMotion: ReduceMotion.Never }));
    else {
      aimY.set(-rise);
      caught.set(
        withDelay(CATCH_AFTER, withSequence(withTiming(1, { duration: CATCH_IN, easing: Easing.out(Easing.quad) }), withSpring(0, CATCH_SPRING))),
      );
    }
    hintShown.set(withTiming(0, { duration: HINT_OUT, reduceMotion: ReduceMotion.Never }));
    avatarShown.set(withDelay(CONFIRM_AFTER, withTiming(0, { duration: FADE, reduceMotion: ReduceMotion.Never })));
    check.set(
      withDelay(
        CONFIRM_AFTER,
        reducedMotion ? withTiming(1, { duration: FADE, reduceMotion: ReduceMotion.Never }) : withSpring(1, POP),
        ReduceMotion.Never,
      ),
    );
    tick.set(
      withDelay(
        CONFIRM_AFTER + TICK_AFTER,
        withTiming(1, { duration: TICK_FOR, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never }),
        ReduceMotion.Never,
      ),
    );
    sentShown.set(
      withDelay(CONFIRM_AFTER + TICK_AFTER, withTiming(1, { duration: FADE, reduceMotion: ReduceMotion.Never }), ReduceMotion.Never),
    );
    pill.set(
      withDelay(
        CONFIRM_AFTER,
        reducedMotion ? withTiming(1, { duration: FADE, reduceMotion: ReduceMotion.Never }) : withSpring(1, PILL_SPRING),
        ReduceMotion.Never,
      ),
    );
    scheduleOnRN(sending);
  };

  /** Everything springs back to its place. */
  const letGoOf = () => {
    'worklet';
    if (flown.get()) return;
    aimX.set(0);
    aimY.set(0);
  };

  /** The finger comes up: far enough down it sends, otherwise everything springs back. */
  const letGo = () => {
    'worklet';
    if (aimY.get() >= travel * ARM_AT) fire();
    else letGoOf();
  };

  /** Done: the check leaves and the amount drops back out of the recipient's picture. */
  const done = () => {
    setStage('idle');
    setAutoPressed(false);
    const fade = { duration: FADE, reduceMotion: ReduceMotion.Never };
    check.set(withTiming(0, { duration: CHECK_OUT, reduceMotion: ReduceMotion.Never }));
    tick.set(withTiming(0, { duration: CHECK_OUT, reduceMotion: ReduceMotion.Never }));
    sentShown.set(withTiming(0, { duration: CHECK_OUT, reduceMotion: ReduceMotion.Never }));
    pill.set(withTiming(0, { duration: PILL_OUT, easing: Easing.in(Easing.cubic), reduceMotion: ReduceMotion.Never }));
    avatarShown.set(withTiming(1, fade));
    hintShown.set(withDelay(HINT_BACK_AFTER, withTiming(1, { duration: HINT_BACK, reduceMotion: ReduceMotion.Never }), ReduceMotion.Never));
    amountShown.set(withDelay(CHECK_OUT, withTiming(1, fade), ReduceMotion.Never));
    aimX.set(0);
    aimY.set(0);
    flown.set(0);
  };

  useAnimatedReaction(
    () => check.get() > 0 && flown.get() === 1,
    (now, old) => {
      if (now && old === false) scheduleOnRN(arrived, byHand.get());
    },
  );

  useAnimatedReaction(
    () => armed.get(),
    (now, old) => {
      if (old !== null && now !== old && byHand.get() && flown.get() === 0) scheduleOnRN(haptic, now ? 'arm' : 'disarm');
    },
  );

  const autoDone = () => {
    onDone?.();
    done();
  };

  // The autoPlay loop: the clock moves a finger that is not there and lets go for it.
  useAnimatedReaction(
    () => clock.get(),
    (t, last) => {
      if (t < 0) return;
      const was = last === null || last > t ? -1 : last;
      if (t >= TEST_AT && t < TEST_UP) pullTo(0, reach * TEST_DEPTH * easeOut((t - TEST_AT) / TEST_FOR));
      else if (t >= PULL_AT && t < PULL_UP) {
        const held = t - PULL_AT - PULL_FOR;
        const depth = held < 0 ? smooth(t, PULL_AT, PULL_AT + PULL_FOR) : 1 + TREMBLE * Math.sin(held * TREMBLE_SPEED);
        const side =
          held < 0
            ? WANDER * smooth(t, PULL_AT, PULL_AT + PULL_FOR)
            : WANDER * Math.cos((Math.PI * held) / (PULL_UP - PULL_AT - PULL_FOR));
        pullTo(side / SIDE_RATIO, reach * depth);
      }
      if ((was < TEST_UP && t >= TEST_UP) || (was < PULL_UP && t >= PULL_UP)) letGo();
      if (was < DONE_AT && t >= DONE_AT) scheduleOnRN(setAutoPressed, true);
      if (was < DONE_AT + PRESS_FOR && t >= DONE_AT + PRESS_FOR) scheduleOnRN(autoDone);
    },
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ stage, done });
  useEffect(() => {
    latest.current = { stage, done };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const start = () => {
      byHand.set(false);
      clock.set(0);
      clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1, false, undefined, ReduceMotion.Never));
    };
    const resume = () => {
      // A finger still down puts the loop off again when it lifts.
      if (holding.get()) return;
      if (latest.current.stage === 'sending') return after(200, resume);
      if (latest.current.stage === 'sent') {
        latest.current.done();
        return after(RETURN * 1000, start);
      }
      start();
    };
    if (touches === 0) start();
    else after(USER_HOLD * 1000, resume);
    return () => {
      timers.forEach(clearTimeout);
      cancelAnimation(clock);
      clock.set(-1);
    };
  }, [autoPlay, touches, byHand, clock, holding]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(false);
    setTouches((n) => n + 1);
  };

  const pan = Gesture.Pan()
    .enabled(stage === 'idle')
    .onBegin(() => {
      // The loop's own pull is dropped at once, here on the UI thread, before the finger's begins.
      cancelAnimation(clock);
      clock.set(-1);
      letGoOf();
      byHand.set(true);
      holding.set(true);
      scheduleOnRN(touched);
    })
    .onChange((event) => {
      pullTo(event.translationX, event.translationY);
    })
    .onEnd(() => {
      letGo();
    })
    .onFinalize(() => {
      holding.set(false);
      scheduleOnRN(touched);
    });

  const heatStyle = useAnimatedStyle(() => ({ opacity: (1 - smooth(heat.get(), 0.45, 0.8)) * (1 - flown.get()) }));
  // The round buttons leave with the release and come back once the Done pill has gone.
  const cornersStyle = useAnimatedStyle(() => ({
    opacity: withDelay(flown.get() ? 0 : PILL_OUT, withTiming(flown.get() ? 0 : 1, { duration: FADE, reduceMotion: ReduceMotion.Never }), ReduceMotion.Never),
  }));
  const avatarStyle = useAnimatedStyle(() => ({
    opacity: avatarShown.get(),
    transform: [{ scale: 1 + CATCH_SCALE * caught.get() }],
  }));
  const checkStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, check.get() * 4),
    transform: [{ scale: reducedMotion ? 1 : check.get() }],
  }));
  const sentStyle = useAnimatedStyle(() => ({ opacity: sentShown.get() }));
  const pillStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, pill.get() * 3),
    transform: [{ translateY: reducedMotion ? 0 : (1 - pill.get()) * (rowBottom + PILL_HEIGHT + 8) }],
  }));

  const onLayout = (event: LayoutChangeEvent) => {
    setLaidOut({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height });
  };

  const shown = money(symbol, amount);
  const letters = initials ?? recipient.slice(0, 1).toUpperCase();
  const copies = reducedMotion ? 1 : AMOUNT_SPRINGS.length;
  const idle = stage === 'idle';

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={styles.fill}>
        <Rect x={0} y={0} width={width} height={height}>
          <LinearGradient start={vec(0, 0)} end={vec(0, height)} colors={sheetColors} positions={sheetStops} />
        </Rect>
        <Circle cx={width / 2} cy={height * 0.1} r={width * 0.9} opacity={glowOpacity}>
          <RadialGradient c={vec(width / 2, height * 0.1)} r={width * 0.9} colors={[glowColor, `${glowColor}00`]} />
        </Circle>
      </Canvas>

      <GestureDetector gesture={pan}>
        <View
          style={styles.fill}
          accessible={idle}
          accessibilityRole="button"
          accessibilityLabel={`Send ${shown} to ${recipient}`}
          accessibilityHint="Swipe down and release to send"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() => {
            touched();
            byHand.set(true);
            scheduleOnUI(fire);
          }}
        >
          {/* Ghosts first, the softest at the back; the text itself on top. */}
          {AMOUNT_SPRINGS.slice(0, copies)
            .map((spring, order) => (
              <Echo key={order} aimX={aimX} aimY={aimY} spring={spring} order={order} rise={rise} shown={amountShown} top={amountAt - AMOUNT_LINE / 2} height={AMOUNT_LINE}>
                <Text style={styles.amount} numberOfLines={1}>
                  {shown}
                </Text>
              </Echo>
            ))
            .reverse()}
          {HINT_SPRINGS.slice(0, copies)
            .map((spring, order) => (
              <Echo key={order} aimX={aimX} aimY={hintAimY} spring={spring} order={order} rise={0} shown={hintShown} top={hintAt - HINT_LINE / 2} height={HINT_LINE}>
                <Hint armed={wording} hint={hint} armedHint={armedHint} />
              </Echo>
            ))
            .reverse()}
        </View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.recipient, { top: avatarTop }, avatarStyle]}>
        <View style={[styles.avatar, { backgroundColor: avatarColor }]}>
          <Text style={[styles.initials, { color: avatarInk }]}>{letters}</Text>
        </View>
        <Text style={styles.name} numberOfLines={1}>
          {recipient}
        </Text>
      </Animated.View>

      <Animated.View pointerEvents={idle ? 'box-none' : 'none'} style={[styles.corners, { bottom: rowBottom }, cornersStyle]}>
        <Animated.View style={heatStyle}>
          <Round
            icon="close"
            size={30}
            label="Close"
            onPress={() => {
              touched();
              haptic('press');
              onClose?.();
            }}
          />
        </Animated.View>
        <Animated.View style={heatStyle}>
          <Round
            icon="pencil"
            size={22}
            label="Edit amount"
            onPress={() => {
              touched();
              haptic('press');
              onEdit?.();
            }}
          />
        </Animated.View>
      </Animated.View>

      <View pointerEvents="none" style={[styles.sent, { top: amountAt - CHECK / 2 }]} accessible={stage === 'sent'} accessibilityLabel={sentLabel}>
        <Animated.View style={checkStyle}>
          <Canvas style={styles.check}>
            <Circle cx={CHECK / 2} cy={CHECK / 2} r={CHECK / 2} color={successColor} />
            <Path path={TICK} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color={INK} end={tick} />
          </Canvas>
        </Animated.View>
        <Animated.Text style={[styles.sentLabel, sentStyle]} numberOfLines={1}>
          {sentLabel}
        </Animated.Text>
      </View>

      <Animated.View pointerEvents={stage === 'sent' ? 'auto' : 'none'} style={[styles.pillSlot, { bottom: rowBottom + (ROUND - PILL_HEIGHT) / 2 }, pillStyle]}>
        <Pressable
          accessibilityRole="button"
          onPress={() => {
            touched();
            haptic('press');
            onDone?.();
            done();
          }}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.pill,
                {
                  backgroundColor: pressed || autoPressed ? PILL_PRESSED : PILL_FILL,
                  transform: [{ scale: pressed || autoPressed ? 0.96 : 1 }],
                  transitionProperty: ['backgroundColor', 'transform'],
                  transitionDuration: 110,
                },
              ]}
            >
              <Text style={styles.pillLabel} numberOfLines={1}>
                {doneLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SATIN[5],
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  echo: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  amount: {
    color: INK,
    fontSize: AMOUNT_SIZE,
    lineHeight: AMOUNT_LINE,
    fontWeight: '700',
    letterSpacing: -0.6,
    fontVariant: ['tabular-nums'],
  },
  hint: {
    position: 'absolute',
    color: INK,
    fontSize: HINT_SIZE,
    lineHeight: HINT_LINE,
    fontWeight: '600',
    textTransform: 'uppercase',
  },
  // Tracking is added after every letter, the last one too: the padding puts the line back in the middle.
  hintRest: {
    letterSpacing: HINT_TRACKING,
    paddingLeft: HINT_TRACKING,
  },
  hintArmed: {
    letterSpacing: ARMED_TRACKING,
    paddingLeft: ARMED_TRACKING,
  },
  recipient: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  avatar: {
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 6, blurRadius: 16, color: 'rgba(0,0,0,0.22)' }],
  },
  initials: {
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '700',
  },
  name: {
    marginTop: 8,
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
  },
  corners: {
    position: 'absolute',
    left: ROUND_MARGIN,
    right: ROUND_MARGIN,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  round: {
    width: ROUND,
    height: ROUND,
    borderRadius: ROUND / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sent: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  check: {
    width: CHECK,
    height: CHECK,
  },
  sentLabel: {
    marginTop: SENT_GAP,
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '700',
  },
  pillSlot: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  pill: {
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillLabel: {
    color: INK,
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '600',
  },
});
