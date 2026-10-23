import { Canvas, Path, RoundedRect, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  successLabel?: string;
  onPress?: () => void;
  /** Submits by itself over and over, with a pause between runs. */
  autoPlay?: boolean;
};

const WIDTH = 200;
const HEIGHT = 48;
// The pill narrows to this while it is loading.
const LOADING_WIDTH = 160;
// The canvases overflow the button by this much so the confetti has room to fly.
const BLEED = 116;
const ICON = 16;
const AUTO_PAUSE_MS = 1500;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

// The whole run is one clock in seconds; every part below is a window on it.
const T_LABEL_OUT = [0, 0.2];
const T_SQUEEZE = [0, 0.4];
const T_DOTS_IN = 0.25;
const T_DOTS_OUT = [2.0, 2.15];
const T_WIDEN = [2.0, 2.45];
const T_BURST = 2.2;
const T_DONE_IN = [2.7, 2.95];
const T_CHECK = [2.9, 3.2];
const T_DONE_OUT = [5.3, 5.55];
const T_LABEL_IN = [5.85, 6.15];
const TOTAL = 6.2;

const DOT_GAP = 9;
const DOT_R = 2.3;
const DOT_PERIOD = 1.0;

const CONFETTI_COLORS = ['#a9b9f3', '#2b55cd', '#6444ec', '#8393de'];
const CONFETTI_COUNT = 40;
const CONFETTI_LIFE = 2.6;
// Air drag: pieces shoot out, stall within a third of a second, then drift down.
const DRAG = 3.2;

const ARROW = 'M8 2.5V13M3.5 8.5L8 13L12.5 8.5';
const CHECK = 'M2.5 8.5L6.5 12.5L13.5 4.5';

function seg(t: number, window: number[]) {
  'worklet';
  return Math.min(1, Math.max(0, (t - window[0]) / (window[1] - window[0])));
}
function easeOut(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x) * (1 - x);
}
function easeOutBack(x: number) {
  'worklet';
  const c = 1.9;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

/** Cheap repeatable noise in 0…1, identical on every platform. */
function noise(i: number, k: number) {
  const s = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
  return s - Math.floor(s);
}

type Piece = {
  color: number;
  x0: number;
  vx: number;
  vy: number;
  fall: number;
  rot: number;
  spin: number;
  flip: number;
  phase: number;
  hw: number;
  hh: number;
  tiny: boolean;
  life: number;
  sway: number;
  swayRate: number;
};

const PIECES: Piece[] = Array.from({ length: CONFETTI_COUNT }, (_, i) => {
  const x0 = (noise(i, 1) - 0.5) * WIDTH * 0.72;
  // Fan out: pieces born towards an end of the pill lean that way.
  const angle = (noise(i, 2) - 0.5) * 1.3 + (x0 / WIDTH) * 0.9;
  const speed = 200 + noise(i, 3) * 260;
  const tiny = noise(i, 9) < 0.22;
  return {
    color: i % CONFETTI_COLORS.length,
    x0,
    vx: Math.sin(angle) * speed,
    vy: -Math.cos(angle) * speed,
    fall: 95 + noise(i, 4) * 75,
    rot: noise(i, 5) * TAU,
    spin: (noise(i, 6) - 0.5) * 12,
    flip: 3 + noise(i, 7) * 7,
    phase: noise(i, 8) * TAU,
    hw: tiny ? 1.2 : 2.6 + noise(i, 10) * 2.6,
    hh: tiny ? 1.2 : 2 + noise(i, 11) * 2.4,
    tiny,
    life: 1.7 + noise(i, 12) * 0.9,
    sway: 6 + noise(i, 13) * 10,
    swayRate: 2 + noise(i, 14) * 2,
  };
});

/** Every piece of one colour, `age` seconds after the burst, as a single path of tumbling quads. */
function confetti(age: number, color: number) {
  'worklet';
  const path = Skia.Path.Make();
  if (age <= 0 || age >= CONFETTI_LIFE) return path;
  const cx = BLEED + WIDTH / 2;
  const top = BLEED + 4;
  const d = (1 - Math.exp(-DRAG * age)) / DRAG;
  for (const p of PIECES) {
    if (p.color !== color || age >= p.life) continue;
    const size = Math.min(1, age / 0.06) * (1 - seg(age, [p.life - 0.35, p.life]));
    const x = cx + p.x0 + p.vx * d + p.sway * Math.sin(p.swayRate * age + p.phase) * Math.min(1, age);
    const y = top + p.vy * d + p.fall * (age - d);
    const a = p.rot + p.spin * age;
    // Tumbling end over end squashes the piece to a sliver and back.
    const hh = p.hh * size * (p.tiny ? 1 : Math.cos(p.flip * age + p.phase));
    const hw = p.hw * size;
    const ax = Math.cos(a) * hw;
    const ay = Math.sin(a) * hw;
    const bx = -Math.sin(a) * hh;
    const by = Math.cos(a) * hh;
    path.moveTo(x - ax - bx, y - ay - by);
    path.lineTo(x + ax - bx, y + ay - by);
    path.lineTo(x + ax + bx, y + ay + by);
    path.lineTo(x - ax + bx, y - ay + by);
    path.close();
  }
  return path;
}

/** Pill width at clock time `t`: squeezes in for loading, springs back out for the result. */
function pillWidth(t: number, still: boolean) {
  'worklet';
  if (still) return WIDTH;
  const squeeze = easeOut(seg(t, T_SQUEEZE)) - easeOutBack(seg(t, T_WIDEN));
  return WIDTH - (WIDTH - LOADING_WIDTH) * squeeze;
}

/** The three loading dots: they pop in one after another, then a wave runs through them every second. */
function dots(t: number, still: boolean) {
  'worklet';
  const path = Skia.Path.Make();
  if (t <= T_DOTS_IN || t >= T_DOTS_OUT[1]) return path;
  for (let i = 0; i < 3; i++) {
    const start = T_DOTS_IN + i * 0.06;
    const pop = still ? seg(t, [start, start + 0.2]) : easeOutBack(seg(t, [start, start + 0.25]));
    const beat = ((((t - 0.9 - i * 0.12) / DOT_PERIOD) % 1) + 1) % 1;
    const hop = still || beat > 0.45 ? 0 : Math.sin((beat / 0.45) * Math.PI) * 5;
    const r = DOT_R * pop * (1 - seg(t, T_DOTS_OUT));
    if (r > 0.05) path.addCircle(BLEED + WIDTH / 2 + (i - 1) * DOT_GAP, BLEED + HEIGHT / 2 - hop, r);
  }
  return path;
}

function Pill({ t, still }: { t: SharedValue<number>; still: boolean }) {
  const x = useDerivedValue(() => BLEED + (WIDTH - pillWidth(t.get(), still)) / 2);
  const w = useDerivedValue(() => pillWidth(t.get(), still));
  const loading = useDerivedValue(() => dots(t.get(), still));
  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x} y={BLEED} width={w} height={HEIGHT} r={HEIGHT / 2} color="#191e2c" />
      <Path path={loading} color="#5d7bff" />
    </Canvas>
  );
}

function Confetti({ t }: { t: SharedValue<number> }) {
  const a = useDerivedValue(() => confetti(t.get() - T_BURST, 0));
  const b = useDerivedValue(() => confetti(t.get() - T_BURST, 1));
  const c = useDerivedValue(() => confetti(t.get() - T_BURST, 2));
  const d = useDerivedValue(() => confetti(t.get() - T_BURST, 3));
  return (
    <Canvas style={styles.canvas}>
      <Path path={a} color={CONFETTI_COLORS[0]} />
      <Path path={b} color={CONFETTI_COLORS[1]} />
      <Path path={c} color={CONFETTI_COLORS[2]} />
      <Path path={d} color={CONFETTI_COLORS[3]} />
    </Canvas>
  );
}

function CheckIcon({ t }: { t: SharedValue<number> }) {
  const end = useDerivedValue(() => easeOut(seg(t.get(), T_CHECK)));
  return (
    <Canvas style={styles.icon}>
      <Path path={CHECK} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#3ee888" start={0} end={end} />
    </Canvas>
  );
}

/**
 * Navy "Submit" pill. A tap squeezes it into three loading dots, then it springs back open with a
 * burst of confetti and a green-checked "Success" before returning to its label.
 */
export function SubmitConfettiButton({
  label = 'Submit',
  successLabel = 'Success',
  onPress,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [busy, setBusy] = useState(false);
  const t = useSharedValue(0);

  // One run: start the clock, and go back to idle when it has run out.
  useEffect(() => {
    if (!busy) return;
    // Reanimated drops animations under reduced motion, which would leave the button stuck, so the
    // clock opts back in; the squeeze, the dot wave and the confetti are switched off instead.
    const run = (to: number, seconds: number) =>
      withTiming(to, { duration: seconds * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never });
    // The clock ends where it began (label showing), so the rewind at the end is invisible.
    t.set(withSequence(ReduceMotion.Never, run(0, 0), run(TOTAL, TOTAL), run(0, 0)));
    const timer = setTimeout(() => setBusy(false), TOTAL * 1000);
    return () => clearTimeout(timer);
  }, [busy, t]);

  useEffect(() => {
    if (!autoPlay || reducedMotion || busy || pressed) return;
    const auto = setTimeout(() => setBusy(true), AUTO_PAUSE_MS);
    return () => clearTimeout(auto);
  }, [autoPlay, reducedMotion, busy, pressed]);

  const slide = reducedMotion ? 0 : 1;
  // The label drops away on the way out and comes back down from above.
  const labelStyle = useAnimatedStyle(() => {
    const out = seg(t.get(), T_LABEL_OUT);
    const back = seg(t.get(), T_LABEL_IN);
    return {
      opacity: 1 - out + back,
      transform: [{ translateY: slide * (back > 0 ? -8 * (1 - easeOut(back)) : 10 * easeOut(out)) }],
    };
  });
  const doneStyle = useAnimatedStyle(() => {
    const shown = seg(t.get(), T_DONE_IN);
    return {
      opacity: shown - seg(t.get(), T_DONE_OUT),
      transform: [{ translateY: slide * 8 * (1 - easeOut(shown)) }],
    };
  });

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ busy }}
      onPress={() => {
        if (busy) return;
        onPress?.();
        setBusy(true);
      }}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      pressRetentionOffset={16}
    >
      <Animated.View
        style={[
          styles.button,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={styles.bleed}>
          <Pill t={t} still={reducedMotion} />
        </View>
        <Animated.View style={[styles.row, labelStyle]}>
          <Canvas style={styles.icon}>
            <Path path={ARROW} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#4a7aff" />
          </Canvas>
          <Text style={styles.label} numberOfLines={1}>
            {label}
          </Text>
        </Animated.View>
        <Animated.View style={[styles.row, doneStyle]}>
          <CheckIcon t={t} />
          <Text style={styles.label} numberOfLines={1}>
            {successLabel}
          </Text>
        </Animated.View>
        {/* Confetti flies in front of the pill and its label. */}
        {!reducedMotion && (
          <View pointerEvents="none" style={styles.bleed}>
            <Confetti t={t} />
          </View>
        )}
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
  },
  bleed: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  row: {
    ...StyleSheet.absoluteFill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  icon: {
    width: ICON,
    height: ICON,
  },
  label: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '500',
  },
});
