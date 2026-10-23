import { Ionicons } from '@expo/vector-icons';
import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RadialGradient, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  Keyframe,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The code that verifies; its length is the number of boxes. */
  code?: string;
  /** Types the code by itself every LOOP seconds, plays the effect and empties the boxes again. */
  autoPlay?: boolean;
  title?: string;
  /** The grey lines under the title; a line break starts a new line. */
  subtitle?: string;
  resendPrompt?: string;
  resendLabel?: string;
  successTitle?: string;
  /** The border of the focused box, and of every box after a wrong code. */
  accent?: string;
  /** The light that traces the boxes and outlines the check tile. */
  neon?: string;
  /** Called when the code matches, as the boxes have become the check tile. */
  onVerified?: (code: string) => void;
  /** Called with a complete code that does not match. */
  onRejected?: (code: string) => void;
  onResend?: () => void;
};

/** autoPlay: one verification every LOOP seconds. */
export const LOOP = 6.9;
// The first key goes down at TYPE_AT, the next ones TYPE_EVERY apart, each held for KEY_FOR.
const TYPE_AT = 0.8;
const TYPE_EVERY = 0.32;
const KEY_FOR = 0.13;
// The check tile leaves this long after the last key.
const RESET_AFTER = 3.6;
// After a touch autoPlay waits this long, and for the sheet to be idle, before it starts over.
const USER_HOLD = 3;

// The clock of a verification, in seconds since the last digit went in.
const TRACE_AT = 0.15;
const TRACE_FOR = 0.45;
// The lit boxes swell, then slide into a fanned deck, and the deck closes into one tile.
const LIFT_AT = TRACE_AT + TRACE_FOR;
const LIFT_FOR = 0.12;
const MERGE_AT = 0.74;
const DECK_FOR = 0.18;
const CLOSE_FOR = 0.12;
const TILE_AT = MERGE_AT + DECK_FOR + CLOSE_FOR - 0.06;
const TILE_IN = 0.08;
const POP_FOR = 0.14;
const CHECK_FOR = 0.22;
const TEXT_OUT = 0.2;
const TITLE_AFTER = 0.08;
const TITLE_IN = 0.3;
// The clock stops here, with the tile at rest.
const SETTLED = 2.2;
// Leaving: the tile fades in the first half, the empty boxes and the texts return in the second.
const RESET_FOR = 0.5;
// A wrong code: the boxes turn red, shake after a beat, and are emptied.
const SHAKE_AFTER = 0.1;
const SHAKE = [-10, 9, -7, 5, -2, 0];
const SHAKE_STEP = 0.065;
const WRONG_FOR = 0.62;

const SCREEN = '#0B0B0C';
const SHEET = '#1B1B1D';
const GRABBER = '#5A5A5E';
const INK = '#FFFFFF';
const MUTE = '#7C7C82';
const KEY_INK = '#F2F2F4';
const BOX_FILL = '#1F1F21';
const BOX_BORDER = '#2E2E31';
const TILE_FILL = '#24120F';
const ACCENT = '#E0452A';
const NEON = '#FF5A2A';
// The head of the trace is close to white; the tile's outline is paler while it bursts.
const HOT = '#FFD9CB';
const WARM = '#FF9C7A';

// The sheet, in points from its top edge.
const SHEET_RADIUS = 28;
const GRABBER_TOP = 13;
const TITLE_TOP = 52;
const SUBTITLE_TOP = 97;
const ROW_TOP = 184;
const RESEND_TOP = 289;
const PAD_TOP = 326;
// The success title sits lower, closer to the tile.
const SUCCESS_TOP = 83;
const BOX = 70;
const BOX_RADIUS = 17;
const BOX_GAP = 15;
const ROW_MARGIN = 24;
const KEY_ROW = 54;
const KEY_ROW_MIN = 44;
const KEY_FLASH = { width: 92, height: 46 };
const KEYS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['', '0', 'back'],
];

// The lit boxes: how much they swell, how far each leans (degrees), and the deck they slide into
// (points between neighbours, degrees turned about the vertical axis).
const LIFT = 0.08;
const LEAN = [-2.5, 1.5, -1.5, 2.5];
const DECK_STEP = 15;
const DECK_TURN = -44;
// The front box of the deck grows a little more, flat, while the ones behind it turn about their
// right edge: each then shows as a narrow sliver to the right of the box before it.
const DECK_FRONT = 0.08;
const PERSPECTIVE = 500;
// Room round a box for the light that spills from its outline.
const SPILL = 30;
const HEAD = 0.42;

// The tile is as large as a box. The scale it enters at and bursts to, and the spring that brings
// it down (angular frequency in rad/s, damping ratio).
const TILE_ENTER = 1 + LIFT + DECK_FRONT;
const TILE_BURST = 1.4;
const TILE_SPRING = { omega: 10, zeta: 0.6 };
const TILE_CANVAS = 280;
// The check, in a box of BOX points with its centre at 0, 0.
const CHECK = 'M-12 1.5L-4 9.5L13 -9.5';

// The warm haze behind the row: wide and low under the lit boxes, round behind the tile.
const HAZE_CANVAS = 360;
const HAZE_ROW = { rx: 0.56, ry: 124, drop: 28, alpha: 0.4 };
const HAZE_TILE = { r: 150, burst: 0.36, rest: 0.17 };
// cos² from the centre to the edge: no rim and no bright point.
const FALLOFF_AT = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1];
const FALLOFF = [1, 0.976, 0.905, 0.794, 0.655, 0.5, 0.345, 0.206, 0.095, 0.024, 0];

// Ember specks that drop from the tile as it bursts: x from the tile's centre, delay (s), drift
// and fall (points a second), radius. They start EMBER_FROM under the tile's centre, fall under
// EMBER_GRAVITY and last EMBER_FOR.
const EMBERS = [
  [-27, 0.0, -12, 14, 1.3],
  [-10, 0.07, -4, 26, 1.0],
  [6, 0.03, 5, 18, 1.5],
  [23, 0.1, 13, 12, 1.1],
  [-18, 0.16, -7, 30, 0.9],
  [15, 0.2, 8, 24, 1.2],
];
const EMBER_FROM = 56;
const EMBER_GRAVITY = 110;
const EMBER_FOR = 1;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** What is left of a spring's way `t` seconds after it was let go at rest: 1, through 0, settling at 0. */
function springLeft(t: number, omega: number, zeta: number) {
  'worklet';
  if (t <= 0) return 1;
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * omega * t) * (Math.cos(damped * t) + ((zeta * omega) / damped) * Math.sin(damped * t));
}

/** A rounded square's outline that starts in the middle of its top edge and runs clockwise. */
function outline(size: number, radius: number, origin: number) {
  const a = origin;
  const b = origin + size;
  const r = radius;
  const path = Skia.Path.MakeFromSVGString(
    `M${a + size / 2} ${a}H${b - r}A${r} ${r} 0 0 1 ${b} ${a + r}V${b - r}A${r} ${r} 0 0 1 ${b - r} ${b}H${a + r}A${r} ${r} 0 0 1 ${a} ${b - r}V${a + r}A${r} ${r} 0 0 1 ${a + r} ${a}Z`,
  );
  return path ?? Skia.Path.Make();
}

// A digit pops into its box.
const DIGIT_IN = new Keyframe({
  0: { opacity: 0, transform: [{ scale: 0.6 }] },
  100: { opacity: 1, transform: [{ scale: 1 }], easing: Easing.out(Easing.back(2)) },
}).duration(170);

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function notify(type: Haptics.NotificationFeedbackType) {
  if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(type).catch(() => {});
}

type Clocks = {
  /** Seconds since the last digit of a right code went in; negative while nothing is verified. */
  clock: SharedValue<number>;
  /** 0 → 1 while the tile leaves and the empty boxes return. */
  away: SharedValue<number>;
  /** Reduced motion: nothing slides, turns or bursts; the light and the tile fade. */
  still: boolean;
};

type BoxProps = Clocks & {
  index: number;
  count: number;
  size: number;
  digit: string;
  focused: boolean;
  wrong: boolean;
  accent: string;
  neon: string;
};

/** One code box: its digit, its focus border, and the light that traces its outline. */
function Box({ index, count, size, digit, focused, wrong, accent, neon, clock, away, still }: BoxProps) {
  const radius = (BOX_RADIUS * size) / BOX;
  // How far the box stands from the middle of the row.
  const home = (index - (count - 1) / 2) * (size + BOX_GAP);
  const lean = LEAN[index % LEAN.length];
  const path = useMemo(() => outline(size - 1.5, radius - 0.75, SPILL + 0.75), [size, radius]);

  const style = useAnimatedStyle(() => {
    const t = clock.get();
    const a = away.get();
    if (a > 0) {
      const back = smooth(a, 0.5, 1);
      return {
        opacity: back,
        transform: [
          { perspective: PERSPECTIVE },
          { translateX: 0 },
          { scale: 0.92 + 0.08 * back },
          { rotateZ: '0deg' },
          { translateX: size / 2 },
          { rotateY: '0deg' },
          { translateX: -size / 2 },
        ],
      };
    }
    const lift = still ? 0 : smooth(t, LIFT_AT, LIFT_AT + LIFT_FOR);
    const deck = still ? 0 : smooth(t, MERGE_AT, MERGE_AT + DECK_FOR);
    const close = still ? 0 : smooth(t, MERGE_AT + DECK_FOR, MERGE_AT + DECK_FOR + CLOSE_FOR);
    // In the deck the boxes stand DECK_STEP apart; it then closes onto the middle.
    const rank = index - (count - 1) / 2;
    const x = home + (rank * DECK_STEP * (1 - close) - home) * deck;
    return {
      opacity: t < 0 ? 1 : 1 - smooth(t, TILE_AT, TILE_AT + TILE_IN),
      transform: [
        { perspective: PERSPECTIVE },
        { translateX: x - home },
        { scale: 1 + LIFT * lift + (index === 0 ? DECK_FRONT * deck : 0) },
        { rotateZ: `${lean * lift * (1 - close)}deg` },
        { translateX: size / 2 },
        { rotateY: `${index === 0 ? 0 : DECK_TURN * deck * (1 - close)}deg` },
        { translateX: -size / 2 },
      ],
    };
  });

  // The digits behind the front box dim as the boxes light up, and all of them go in the deck.
  const digitStyle = useAnimatedStyle(() => {
    const t = clock.get();
    if (t < 0 || away.get() > 0) return { opacity: 1, transform: [{ scale: 1 }] };
    const dim = index === 0 ? 0 : 0.5 * smooth(t, LIFT_AT, MERGE_AT);
    const gone = smooth(t, MERGE_AT, MERGE_AT + DECK_FOR);
    return { opacity: (1 - dim) * (1 - gone), transform: [{ scale: 1 - 0.5 * gone }] };
  });

  // How far round the outline the light has run, and its bright head.
  const end = useDerivedValue(() => (still ? 1 : clamp01((clock.get() - TRACE_AT) / TRACE_FOR)));
  const headStart = useDerivedValue(() => Math.max(0, end.get() - HEAD));
  const lit = useDerivedValue(() => {
    const t = clock.get();
    if (t < TRACE_AT || away.get() > 0) return 0;
    return still ? smooth(t, TRACE_AT, TRACE_AT + TRACE_FOR) : 1;
  });
  // The outline behind the head glows weaker until the light closes on itself.
  const body = useDerivedValue(() => lit.get() * (0.5 + 0.5 * smooth(end.get(), 0.82, 1)));
  const head = useDerivedValue(() => lit.get() * (1 - smooth(end.get(), 0.9, 1)));
  // A closed outline lights the inside of its box from below; it cools as the boxes lift.
  const inside = useDerivedValue(() => {
    const t = clock.get();
    return lit.get() * smooth(t, TRACE_AT + TRACE_FOR * 0.45, LIFT_AT) * (1 - 0.55 * smooth(t, LIFT_AT, MERGE_AT));
  });

  return (
    <Animated.View style={[{ width: size, height: size }, style]}>
      <Animated.View
        style={[
          styles.halo,
          {
            borderRadius: radius,
            boxShadow: [{ offsetX: 0, offsetY: 0, blurRadius: 12, spreadDistance: 0, color: `${accent}66` }],
            opacity: focused ? 1 : 0,
            transitionProperty: 'opacity',
            transitionDuration: 140,
          },
        ]}
      />
      <Animated.View
        style={[
          styles.box,
          { borderRadius: radius, borderColor: focused || wrong ? accent : BOX_BORDER, transitionProperty: 'borderColor', transitionDuration: 140 },
        ]}
      />
      <Canvas pointerEvents="none" style={[styles.spill, { width: size + SPILL * 2, height: size + SPILL * 2 }]}>
        <RoundedRect x={SPILL} y={SPILL} width={size} height={size} r={radius} opacity={inside}>
          <LinearGradient start={vec(0, SPILL)} end={vec(0, SPILL + size)} colors={[`${neon}14`, `${neon}2E`, `${neon}8C`]} positions={[0, 0.5, 1]} />
        </RoundedRect>
        <Path path={path} style="stroke" strokeWidth={6} strokeCap="round" color={neon} start={0} end={end} opacity={body}>
          <BlurMask blur={9} style="normal" />
        </Path>
        <Path path={path} style="stroke" strokeWidth={7} strokeCap="round" color={neon} start={headStart} end={end} opacity={head}>
          <BlurMask blur={12} style="normal" />
        </Path>
        <Path path={path} style="stroke" strokeWidth={2} strokeCap="round" color={neon} start={0} end={end} opacity={lit} />
        <Path path={path} style="stroke" strokeWidth={2} strokeCap="round" color={HOT} start={headStart} end={end} opacity={head}>
          <BlurMask blur={0.6} style="normal" />
        </Path>
      </Canvas>
      <Animated.View style={[styles.digitSlot, digitStyle]}>
        {digit !== '' && (
          <Animated.Text entering={DIGIT_IN} style={styles.digit}>
            {digit}
          </Animated.Text>
        )}
      </Animated.View>
    </Animated.View>
  );
}

/** The warm haze behind the row. */
function Haze({ clock, away, still, width, neon }: Clocks & { width: number; neon: string }) {
  const colors = useMemo(() => {
    const [r, g, b] = Skia.Color(neon);
    return FALLOFF.map((f) => Float32Array.of(r, g, b, f));
  }, [neon]);
  // From the wide glow under the boxes to the round one behind the tile.
  const gathered = useDerivedValue(() => smooth(clock.get(), MERGE_AT, TILE_AT + TILE_IN));
  const transform = useDerivedValue(() => {
    const g = gathered.get();
    const rx = HAZE_ROW.rx * width + (HAZE_TILE.r - HAZE_ROW.rx * width) * g;
    const ry = HAZE_ROW.ry + (HAZE_TILE.r - HAZE_ROW.ry) * g;
    return [{ translateX: width / 2 }, { translateY: HAZE_CANVAS / 2 + HAZE_ROW.drop * (1 - g) }, { scaleX: rx }, { scaleY: ry }];
  });
  const opacity = useDerivedValue(() => {
    const t = clock.get();
    if (t < 0) return 0;
    const grown = HAZE_ROW.alpha * smooth(t, TRACE_AT, LIFT_AT);
    const burst = still ? HAZE_TILE.rest : HAZE_TILE.burst + (HAZE_TILE.rest - HAZE_TILE.burst) * smooth(t, TILE_AT + POP_FOR, TILE_AT + 0.8);
    return (grown + (burst - grown) * gathered.get()) * (1 - smooth(away.get(), 0, 0.5));
  });
  return (
    <Canvas pointerEvents="none" style={[styles.haze, { width }]}>
      <Group transform={transform} opacity={opacity}>
        <Circle cx={0} cy={0} r={1} dither>
          <RadialGradient c={vec(0, 0)} r={1} colors={colors} positions={FALLOFF_AT} />
        </Circle>
      </Group>
    </Canvas>
  );
}

function Ember({ spec, clock, neon }: { spec: number[]; clock: SharedValue<number>; neon: string }) {
  const [x, delay, drift, fall, radius] = spec;
  const age = useDerivedValue(() => clock.get() - TILE_AT - delay);
  const cx = useDerivedValue(() => TILE_CANVAS / 2 + x + drift * Math.max(0, age.get()));
  const cy = useDerivedValue(() => {
    const e = Math.max(0, age.get());
    return TILE_CANVAS / 2 + EMBER_FROM + fall * e + 0.5 * EMBER_GRAVITY * e * e;
  });
  const opacity = useDerivedValue(() => 0.9 * smooth(age.get(), 0, 0.06) * (1 - smooth(age.get(), EMBER_FOR * 0.4, EMBER_FOR)));
  return <Circle cx={cx} cy={cy} r={radius} color={neon} opacity={opacity} />;
}

/** The tile the boxes become: a glowing outline, a check that draws in, and the embers it drops. */
function Tile({ clock, away, still, neon, size }: Clocks & { neon: string; size: number }) {
  const frame = { x: (TILE_CANVAS - size) / 2, y: (TILE_CANVAS - size) / 2, width: size, height: size, r: (BOX_RADIUS * size) / BOX };
  const opacity = useDerivedValue(() => {
    const t = clock.get();
    return t < 0 ? 0 : smooth(t, TILE_AT, TILE_AT + TILE_IN) * (1 - smooth(away.get(), 0, 0.5));
  });
  // It comes in at the size of the lit boxes, bursts, and springs down to rest.
  const transform = useDerivedValue(() => {
    const t = clock.get() - TILE_AT;
    if (still) return [{ scale: 1 }];
    const u = clamp01(t / POP_FOR);
    const up = TILE_ENTER + (TILE_BURST - TILE_ENTER) * (1 - (1 - u) * (1 - u));
    const scale = t < POP_FOR ? up : 1 + (TILE_BURST - 1) * springLeft(t - POP_FOR, TILE_SPRING.omega, TILE_SPRING.zeta);
    return [{ scale: scale * (1 - 0.12 * smooth(away.get(), 0, 0.5)) }];
  });
  // 1 at the burst, 0 once the tile is at rest.
  const heat = useDerivedValue(() => (still ? 0 : 1 - smooth(clock.get(), TILE_AT + POP_FOR, TILE_AT + 0.75)));
  const bloom = useDerivedValue(() => 0.3 + 0.7 * heat.get());
  const flare = useDerivedValue(() => 0.5 * heat.get());
  const inside = useDerivedValue(() => 0.2 + 0.8 * heat.get());
  const pale = useDerivedValue(() => 0.8 * heat.get());
  const check = useDerivedValue(() => smooth(clock.get(), TILE_AT + 0.02, TILE_AT + 0.02 + CHECK_FOR));
  const dot = useDerivedValue(() => smooth(clock.get(), TILE_AT - 0.04, TILE_AT + 0.02));
  const centre = vec(TILE_CANVAS / 2, TILE_CANVAS / 2);

  return (
    <Canvas pointerEvents="none" style={styles.tile}>
      <Group opacity={opacity}>
        <Group transform={transform} origin={centre}>
          <RoundedRect {...frame} color={neon} opacity={flare}>
            <BlurMask blur={30} style="normal" />
          </RoundedRect>
          <RoundedRect {...frame} style="stroke" strokeWidth={12} color={neon} opacity={bloom}>
            <BlurMask blur={24} style="normal" />
          </RoundedRect>
          <RoundedRect {...frame} style="stroke" strokeWidth={5} color={neon}>
            <BlurMask blur={7} style="normal" />
          </RoundedRect>
          <RoundedRect {...frame} color={TILE_FILL} />
          <RoundedRect {...frame} opacity={inside}>
            <LinearGradient start={vec(0, frame.y)} end={vec(0, frame.y + size)} colors={[`${neon}1F`, `${neon}47`]} />
          </RoundedRect>
          <RoundedRect {...frame} style="stroke" strokeWidth={2} color={neon} />
          <RoundedRect {...frame} style="stroke" strokeWidth={2} color={WARM} opacity={pale} />
          <Group transform={[{ translateX: TILE_CANVAS / 2 }, { translateY: TILE_CANVAS / 2 }, { scale: size / BOX }]}>
            <Path path={CHECK} style="stroke" strokeWidth={3.2} strokeCap="round" strokeJoin="round" color={INK} end={check} opacity={dot} />
          </Group>
        </Group>
        {!still && EMBERS.map((spec, index) => <Ember key={index} spec={spec} clock={clock} neon={neon} />)}
      </Group>
    </Canvas>
  );
}

type KeyProps = { label: string; height: number; held: boolean; onPress: () => void };

function Key({ label, height, held, onPress }: KeyProps) {
  if (label === '') return <View style={styles.key} />;
  return (
    <Pressable onPress={onPress} style={[styles.key, { height }]} accessibilityRole="button" accessibilityLabel={label === 'back' ? 'Delete' : label}>
      {({ pressed }) => (
        <>
          {/* On at once, off slowly: a flash. */}
          <Animated.View
            style={[
              styles.flash,
              { opacity: pressed || held ? 1 : 0, transitionProperty: 'opacity', transitionDuration: pressed || held ? 40 : 320 },
            ]}
          />
          {label === 'back' ? <Ionicons name="backspace-outline" size={25} color={KEY_INK} /> : <Text style={styles.keyLabel}>{label}</Text>}
        </>
      )}
    </Pressable>
  );
}

type Status = 'idle' | 'wrong' | 'verifying' | 'verified' | 'leaving';

/**
 * A phone verification sheet: code boxes filled from the sheet's own number pad. The right code
 * verifies by itself: a neon light traces every box, the boxes fuse into one glowing tile and a
 * check draws in. A wrong one shakes the boxes and empties them. Nothing is sent anywhere: compare
 * the code in `onVerified` / `onRejected` or replace `code` with what the server expects.
 */
export function NeonOtpVerify({
  code = '6988',
  autoPlay = false,
  title = 'Let’s verify your number',
  subtitle = 'We’ve sent a 4-digit code to your phone.\nIt’ll auto-verify once entered.',
  resendPrompt = 'Didn’t receive the code?',
  resendLabel = 'Resend',
  successTitle = 'Verified successfully',
  accent = ACCENT,
  neon = NEON,
  onVerified,
  onRejected,
  onResend,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [digits, setDigits] = useState('');
  const [status, setStatus] = useState<Status>('idle');
  // The key autoPlay is holding down.
  const [autoKey, setAutoKey] = useState<string | null>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const clock = useSharedValue(-1);
  const away = useSharedValue(0);
  const shake = useSharedValue(0);
  // Whether the code being verified was typed by hand, for the haptic at the end.
  const byHand = useRef(false);
  const wrongTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const count = code.length;
  const box = Math.min(BOX, (size.width - ROW_MARGIN * 2 - BOX_GAP * (count - 1)) / count);
  const bottom = Math.max(insets.bottom, 16) + 6;
  const keyRow = Math.min(KEY_ROW, Math.max(KEY_ROW_MIN, (size.height - insets.top - 24 - PAD_TOP - bottom) / KEYS.length));
  const rowCentre = ROW_TOP + BOX / 2;

  const arrive = () => {
    setStatus('verified');
    if (byHand.current) notify(Haptics.NotificationFeedbackType.Success);
    onVerified?.(code);
  };

  const home = () => setStatus('idle');

  /** The tile leaves and the empty boxes come back. */
  const reset = () => {
    setDigits('');
    setStatus('leaving');
    away.set(
      withTiming(1, { duration: RESET_FOR * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (!finished) return;
        clock.set(-1);
        away.set(0);
        scheduleOnRN(home);
      }),
    );
  };

  const submit = (entered: string, hand: boolean) => {
    byHand.current = hand;
    if (entered === code) {
      setStatus('verifying');
      clock.set(0);
      clock.set(withTiming(SETTLED, { duration: SETTLED * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
      return;
    }
    setStatus('wrong');
    if (hand) notify(Haptics.NotificationFeedbackType.Error);
    onRejected?.(entered);
    // Under reduced motion the row stays where it is and only turns red.
    shake.set(
      withDelay(
        SHAKE_AFTER * 1000,
        withSequence(...SHAKE.map((x) => withTiming(x, { duration: SHAKE_STEP * 1000, easing: Easing.inOut(Easing.quad) }))),
      ),
    );
    wrongTimer.current = setTimeout(() => {
      setDigits('');
      setStatus('idle');
    }, WRONG_FOR * 1000);
  };

  const enter = (key: string, hand: boolean) => {
    if (status !== 'idle') return;
    if (key === 'back') return setDigits(digits.slice(0, -1));
    if (digits.length >= count) return;
    const entered = digits + key;
    setDigits(entered);
    if (entered.length === count) submit(entered, hand);
  };

  useAnimatedReaction(
    () => clock.get() >= TILE_AT,
    (now, old) => {
      if (now && old === false) scheduleOnRN(arrive);
    },
  );

  useEffect(
    () => () => {
      cancelAnimation(clock);
      cancelAnimation(away);
      if (wrongTimer.current) clearTimeout(wrongTimer.current);
    },
    [clock, away],
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ enter, reset, status, digits });
  useEffect(() => {
    latest.current = { enter, reset, status, digits };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      code.split('').forEach((key, index) => {
        at(TYPE_AT + index * TYPE_EVERY, () => {
          setAutoKey(key);
          latest.current.enter(key, false);
        });
        at(TYPE_AT + index * TYPE_EVERY + KEY_FOR, () => setAutoKey(null));
      });
      at(TYPE_AT + (code.length - 1) * TYPE_EVERY + RESET_AFTER, () => latest.current.reset());
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    // After a touch the loop starts over from an idle, empty sheet.
    const rest = () => {
      const now = latest.current;
      if (now.status === 'verified') now.reset();
      if (now.status !== 'idle') return after(100, rest);
      if (now.digits !== '') {
        setDigits('');
        return after(100, rest);
      }
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, code]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoKey(null);
    setTouches((n) => n + 1);
  };

  const press = (key: string) => {
    touched();
    tick();
    enter(key, true);
  };

  const resend = () => {
    touched();
    tick();
    if (status === 'idle') setDigits('');
    onResend?.();
  };

  const rowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: shake.get() }] }));
  // The title, the lines under it and the resend line leave as the boxes lift.
  const askStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const a = away.get();
    const out = smooth(t, LIFT_AT, LIFT_AT + TEXT_OUT);
    return { opacity: a > 0 ? smooth(a, 0.5, 1) : 1 - out, transform: [{ translateY: a > 0 || reducedMotion ? 0 : -6 * out }] };
  });
  const successStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const a = away.get();
    const shown = smooth(t, MERGE_AT + TITLE_AFTER, MERGE_AT + TITLE_AFTER + TITLE_IN);
    return { opacity: t < 0 ? 0 : shown * (1 - smooth(a, 0, 0.5)), transform: [{ translateY: reducedMotion ? 0 : 10 * (1 - shown) }] };
  });
  // The pad has nothing left to type: it dims while the code verifies.
  const padStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const a = away.get();
    const dimmed = t < 0 ? 0 : a > 0 ? 1 - smooth(a, 0.5, 1) : smooth(t, TRACE_AT, LIFT_AT);
    return { opacity: 1 - 0.74 * dimmed };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const idle = status === 'idle';
  const indices = code.split('').map((_, index) => count - 1 - index);
  const clocks = { clock, away, still: reducedMotion };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <View style={[styles.sheet, { paddingBottom: bottom }]}>
        <View style={styles.grabber} />

        <Animated.View pointerEvents="none" style={[styles.ask, askStyle]} accessibilityElementsHidden={!idle}>
          <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit>
            {title}
          </Text>
          <Text style={styles.subtitle}>{subtitle}</Text>
        </Animated.View>
        <Animated.Text
          style={[styles.title, styles.success, successStyle]}
          numberOfLines={1}
          adjustsFontSizeToFit
          accessibilityLiveRegion="polite"
          accessibilityElementsHidden={status !== 'verified'}
        >
          {successTitle}
        </Animated.Text>

        <View pointerEvents="none" style={[styles.glow, { top: rowCentre - HAZE_CANVAS / 2 }]}>
          <Haze {...clocks} width={size.width} neon={neon} />
        </View>

        <Animated.View
          style={[styles.row, { gap: BOX_GAP, height: BOX }, rowStyle]}
          accessible
          accessibilityLabel="Verification code"
          accessibilityValue={{ text: digits.split('').join(' ') }}
        >
          {/* Drawn last to first, so that the first box is the front of the deck. */}
          {indices.map((index) => (
            <Box
              key={index}
              {...clocks}
              index={index}
              count={count}
              size={box}
              digit={digits[index] ?? ''}
              focused={idle && index === digits.length}
              wrong={status === 'wrong'}
              accent={accent}
              neon={neon}
            />
          ))}
        </Animated.View>

        <View pointerEvents="none" style={[styles.glow, { top: rowCentre - TILE_CANVAS / 2 }]}>
          <Tile {...clocks} neon={neon} size={box} />
        </View>

        <Animated.View style={[styles.resend, askStyle]} pointerEvents={idle ? 'auto' : 'none'}>
          <Text style={styles.resendPrompt}>{resendPrompt} </Text>
          <Pressable onPress={resend} hitSlop={12} accessibilityRole="button">
            {({ pressed }) => <Text style={[styles.resendLabel, pressed && styles.pressed]}>{resendLabel}</Text>}
          </Pressable>
        </Animated.View>

        <Animated.View style={[styles.pad, padStyle]} pointerEvents={idle ? 'auto' : 'none'}>
          {KEYS.map((row) => (
            <View key={row[1]} style={styles.keys}>
              {row.map((key) => (
                <Key key={key} label={key} height={keyRow} held={autoKey === key} onPress={() => press(key)} />
              ))}
            </View>
          ))}
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SCREEN,
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: SHEET,
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    paddingTop: PAD_TOP,
  },
  grabber: {
    position: 'absolute',
    top: GRABBER_TOP,
    alignSelf: 'center',
    width: 40,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: GRABBER,
  },
  ask: {
    position: 'absolute',
    left: 24,
    right: 24,
    top: TITLE_TOP,
  },
  title: {
    color: INK,
    fontSize: 27,
    lineHeight: 34,
    fontWeight: '600',
    letterSpacing: -0.3,
    textAlign: 'center',
  },
  success: {
    position: 'absolute',
    left: 24,
    right: 24,
    top: SUCCESS_TOP,
  },
  subtitle: {
    marginTop: SUBTITLE_TOP - TITLE_TOP - 34,
    color: MUTE,
    fontSize: 14,
    lineHeight: 24,
    textAlign: 'center',
  },
  glow: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  haze: {
    height: HAZE_CANVAS,
  },
  tile: {
    width: TILE_CANVAS,
    height: TILE_CANVAS,
  },
  row: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: ROW_TOP,
    flexDirection: 'row-reverse',
    alignItems: 'center',
    justifyContent: 'center',
  },
  halo: {
    ...StyleSheet.absoluteFill,
  },
  box: {
    ...StyleSheet.absoluteFill,
    backgroundColor: BOX_FILL,
    borderWidth: 1.5,
  },
  spill: {
    position: 'absolute',
    left: -SPILL,
    top: -SPILL,
  },
  digitSlot: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  digit: {
    color: INK,
    fontSize: 25,
    lineHeight: 30,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  resend: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: RESEND_TOP,
    flexDirection: 'row',
    justifyContent: 'center',
  },
  resendPrompt: {
    color: MUTE,
    fontSize: 14,
    lineHeight: 20,
  },
  resendLabel: {
    color: INK,
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '600',
  },
  pressed: {
    opacity: 0.6,
  },
  pad: {
    paddingHorizontal: 20,
  },
  keys: {
    flexDirection: 'row',
  },
  key: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  flash: {
    position: 'absolute',
    width: KEY_FLASH.width,
    height: KEY_FLASH.height,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,255,255,0.09)',
  },
  keyLabel: {
    color: KEY_INK,
    fontSize: 26,
    lineHeight: 32,
    fontVariant: ['tabular-nums'],
  },
});
