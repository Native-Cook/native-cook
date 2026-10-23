import Ionicons from '@expo/vector-icons/Ionicons';
import { Canvas, Circle, Group, LinearGradient, Path, RadialGradient, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  interpolateColor,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/** The fixed words beside the slider. */
type Labels = {
  temperatureTitle: string;
  temperature: string;
  humidityTitle: string;
  absoluteTitle: string;
  absolute: string;
  warning: string;
};

type Props = {
  /** The set-point at first, in percent, and the one the self-playing demo rests on. */
  initial?: number;
  /** The labels of the scale from top to bottom. They are evenly spaced although the values are not. */
  stops?: number[];
  /** The labels that carry a yellow dot. */
  extremes?: number[];
  /** The recommended band, lowest and highest percent: the line is cyan inside it. */
  safe?: [number, number];
  labels?: Partial<Labels>;
  /** Moves the knob up and down by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the set-point, in whole percent, each time it changes. */
  onChange?: (value: number) => void;
};

const WIDTH = 340;
const HEIGHT = 600;

// The line, its bump and the knob.
const AXIS = 122;
// How far the line swerves, and how far above and below the knob it leaves its axis.
const BUMP = 23;
const SPAN = 46;
// The furthest the bump's ends trail behind the knob.
const MAX_LEAN = 18;
// The knob sits this far right of the axis.
const KNOB_OUT = 3;
const END_FADE = 28;
// Percent over which the line turns from cyan to magenta above and below the recommended band.
const BLEND_ABOVE = 12;
const BLEND_BELOW = 8;

// The scale.
const SCALE_TOP = 48;
const LABEL_X = 26;
const DOT_X = 17;
const TICK_INSET = 5;
// A label hides while the knob is within this many points of it, and fades in over the next few.
const HIDE_WITHIN = 18;
const HIDE_FADE = 12;

// The read-outs.
const READ_X = 182;
const DIGIT_W = 34;
const DIGIT_H = 60;
// Seconds the large figure takes to cover most of the way to the set-point.
const ROLL_TIME = 0.18;

// The knob's settling, the spring the bump's ends follow it on, and the press.
const STIFFNESS = 300;
const DAMPING = 26;
const LAG_STIFFNESS = 260;
const LAG_DAMPING = 24;
const PRESS_SCALE = 0.92;
const PRESS_TIME = 0.12;
const RELEASE_TIME = 0.2;
const TAP_SLOP = 4;
const WARN_TIME = 0.5;

// autoPlay (seconds): when each move starts, the set-point it goes to (null: the resting one) and how long it takes.
const LOOP = 8;
const SCRIPT: { at: number; to: number | null; time: number }[] = [
  { at: 1.0, to: 48, time: 0.9 },
  { at: 2.6, to: 28, time: 1.1 },
  { at: 4.4, to: 18, time: 0.5 },
  { at: 5.5, to: null, time: 0.9 },
];
// After a touch the loop waits this long, then takes `HOME_TIME` to return to the resting set-point.
const RESUME_AFTER = 2;
const HOME_TIME = 0.7;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.45, 0, 0.55, 1);
// Ease-in-out that runs a little past the end and comes back.
const SETTLE_CURVE = Easing.bezier(0.45, 0, 0.3, 1.18);
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

const NIGHT = '#070a26';
const CYAN = '#1aa9e0';
const MAGENTA = '#d4127a';
const TICK = '#2a2f55';
const TICK_LIT = '#5d68b8';
const NOTE_INK = '#6a72a8';
const YELLOW = '#f5d21f';
const DIM = '#4b5390';
const RING = '#b9bdcc';

const STOPS = [100, 75, 50, 45, 40, 35, 30, 25, 10, 0];
const EXTREMES = [100, 75, 50, 25, 10, 0];
const SAFE: [number, number] = [20, 55];

const LABELS: Labels = {
  temperatureTitle: 'RETURN TEMPERATURE',
  temperature: '20°C',
  humidityTitle: 'CURRENT HUMIDITY',
  absoluteTitle: 'ABSOLUTE HUMIDITY',
  absolute: '4gr/ft3',
  warning: '— extreme humidity levels. Use precaution for set-points outside of 20%-55%.',
};

// A pair of chevrons, drawn around the knob's centre.
const CHEVRONS = 'M -3.5 -2.5 L 0 -6 L 3.5 -2.5 M -3.5 2.5 L 0 6 L 3.5 2.5';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** The distance between two labels of a scale with `count` of them. */
function gapOf(count: number) {
  'worklet';
  return (HEIGHT - SCALE_TOP * 2) / Math.max(1, count - 1);
}

function clampY(stops: number[], y: number) {
  'worklet';
  return Math.min(Math.max(y, SCALE_TOP), SCALE_TOP + gapOf(stops.length) * (stops.length - 1));
}

/** The value a height stands for: the labels are evenly spaced, and a height between two of them is a value between theirs. */
function valueAt(stops: number[], y: number) {
  'worklet';
  if (stops.length < 2) return stops[0] ?? 0;
  const u = (clampY(stops, y) - SCALE_TOP) / gapOf(stops.length);
  const k = Math.min(Math.floor(u), stops.length - 2);
  return stops[k] + (stops[k + 1] - stops[k]) * (u - k);
}

/** The inverse of `valueAt`. */
function yOf(stops: number[], value: number) {
  'worklet';
  const gap = gapOf(stops.length);
  for (let k = 0; k < stops.length - 1; k++) {
    if (value <= stops[k] && value >= stops[k + 1] && stops[k] !== stops[k + 1]) {
      return SCALE_TOP + (k + (stops[k] - value) / (stops[k] - stops[k + 1])) * gap;
    }
  }
  return value > stops[0] ? SCALE_TOP : SCALE_TOP + gap * (stops.length - 1);
}

/** 0 inside the recommended band … 1 well outside it. */
function dangerAt(safe: [number, number], value: number) {
  'worklet';
  return clamp01(Math.max((value - safe[1]) / BLEND_ABOVE, (safe[0] - value) / BLEND_BELOW));
}

/** How far the line is pushed aside at the height `at`, 0…1. The bump's ends are centred `lean` away from the knob. */
function bend(at: number, knob: number, lean: number) {
  'worklet';
  const s = at < knob ? (knob - at) / (SPAN - lean) : (at - knob) / (SPAN + lean);
  return s >= 1 ? 0 : (1 + Math.cos(Math.PI * s)) / 2;
}

function channel(hex: string, at: number) {
  return parseInt(hex.slice(at, at + 2), 16);
}

/** A colour between two six-digit hex colours, with an opacity. */
function blend(a: string, b: string, t: number, opacity: number) {
  const part = (at: number) => Math.round(channel(a, at) + (channel(b, at) - channel(a, at)) * t);
  return `rgba(${part(1)}, ${part(3)}, ${part(5)}, ${opacity})`;
}

/** One digit of the large figure: a strip of numerals that rolls behind a window. */
function Strip({ column, shown }: { column: number; shown: SharedValue<number> }) {
  // A strip only turns while the one to its right goes from 9 to 0. Columns that would show a
  // leading zero are left out, and the rest moves up to the left edge.
  const window = useAnimatedStyle(() => ({ width: shown.get() > 10 ** column - 1 || column === 0 ? DIGIT_W : 0 }));
  const roll = useAnimatedStyle(() => {
    const value = shown.get();
    const unit = 10 ** column;
    const position = Math.floor(value / unit) + (column === 0 ? (value % 1) : Math.max(0, (value % unit) - (unit - 1)));
    return { transform: [{ translateY: -(position % 10) * DIGIT_H }] };
  });
  return (
    <Animated.View style={[styles.window, window]}>
      <Animated.View style={roll}>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0].map((digit, row) => (
          <Text key={row} style={styles.digit}>
            {digit}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/** A label of the scale. One the knob comes close to makes room for the live label. */
function Stop({ value, at, flagged, y }: { value: number; at: number; flagged: boolean; y: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: clamp01((Math.abs(at - y.get()) - HIDE_WITHIN) / HIDE_FADE) }));
  return (
    <>
      {flagged && <View style={[styles.dot, { top: at - 1.5 }]} />}
      <Animated.Text style={[styles.stop, { top: at - 10 }, style]} numberOfLines={1}>
        {value}%
      </Animated.Text>
    </>
  );
}

/**
 * A vertical humidity slider drawn as one thin line that swerves around its knob like a string pushed
 * aside by a bead. The line is cyan through the recommended band and turns magenta outside it; a
 * ladder of ticks left of it bends with it, and the labels of an uneven scale stand further left, the
 * one beside the knob replaced by the live set-point. Right of the line three read-outs are stacked;
 * the large one rolls its digits like a counter as it follows the set-point.
 */
export function BendingLineHumiditySlider({
  initial = 35,
  stops = STOPS,
  extremes = EXTREMES,
  safe = SAFE,
  labels,
  autoPlay = false,
  onChange,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still && stops.length > 1;
  const words = { ...LABELS, ...labels };
  const gap = gapOf(stops.length);

  // The knob's height on the panel.
  const y = useSharedValue(yOf(stops, initial));
  // The point the ends of the bump are centred on. It follows the knob on a spring, so the bump leans on a quick move.
  const lag = useSharedValue(yOf(stops, initial));
  const lagSpeed = useSharedValue(0);
  // The large figure, which rolls towards the set-point.
  const shown = useSharedValue(Math.round(initial));
  // 0 at rest … 1 while the knob is held; its size dips while it is held and bounces on release.
  const press = useSharedValue(0);
  const size = useSharedValue(1);
  // 0…1 over `WARN_TIME` from the moment the set-point leaves the recommended band.
  const warn = useSharedValue(1);
  const held = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  const downX = useSharedValue(0);
  const downY = useSharedValue(0);
  // The moves come from a finger, so they tick; the script's do not.
  const felt = useSharedValue(false);

  // The set-point in whole percent, for the live label.
  const [whole, setWhole] = useState(Math.round(initial));
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const touch = () => setTouches((count) => count + 1);
  const changed = (value: number) => {
    setWhole(value);
    onChange?.(value);
  };

  // The bump's ends and the large figure catch up with the knob.
  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 33) / 1000;
    const knob = y.get();
    const goal = Math.round(valueAt(stops, knob));
    if (still) {
      lag.set(knob);
      shown.set(goal);
      return;
    }
    const speed = lagSpeed.get() + (LAG_STIFFNESS * (knob - lag.get()) - LAG_DAMPING * lagSpeed.get()) * dt;
    lagSpeed.set(speed);
    lag.set(lag.get() + speed * dt);
    const left = goal - shown.get();
    shown.set(Math.abs(left) < 0.01 ? goal : shown.get() + left * (1 - Math.exp(-dt / ROLL_TIME)));
  });

  // Each whole percent ticks, and leaving the recommended band sets off the warning.
  useAnimatedReaction(
    () => Math.round(valueAt(stops, y.get())),
    (now, before) => {
      if (before === null || now === before) return;
      if (felt.get()) scheduleOnRN(tick);
      scheduleOnRN(changed, now);
      const wasSafe = before >= safe[0] && before <= safe[1];
      const isSafe = now >= safe[0] && now <= safe[1];
      if (wasSafe && !isSafe && !still) {
        warn.set(0);
        warn.set(withTiming(1, { duration: WARN_TIME * 1000, ...LINEAR }));
      }
    },
  );

  /** The knob is pressed or let go: held, it shrinks a little under a grey ring; released, it bounces back. */
  const hold = (down: boolean) => {
    'worklet';
    if (still) {
      press.set(down ? 1 : 0);
    } else if (down) {
      press.set(withTiming(1, { duration: PRESS_TIME * 1000, ...LINEAR }));
      size.set(withTiming(PRESS_SCALE, { duration: PRESS_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
    } else {
      press.set(withTiming(0, { duration: RELEASE_TIME * 1000, ...LINEAR }));
      size.set(withSpring(1, { mass: 1, stiffness: 420, damping: 13, reduceMotion: ReduceMotion.Never }));
    }
  };

  /** Sends the knob to a set-point: over `time` seconds (`settle` runs a little past and back), or on a spring when `time` is 0. */
  const move = (value: number, time: number, settle: boolean) => {
    'worklet';
    const to = yOf(stops, value);
    if (still) y.set(to);
    else if (time > 0) y.set(withTiming(to, { duration: time * 1000, easing: settle ? SETTLE_CURVE : EASE_IN_OUT, reduceMotion: ReduceMotion.Never }));
    else y.set(withSpring(to, { mass: 1, stiffness: STIFFNESS, damping: DAMPING, reduceMotion: ReduceMotion.Never }));
  };

  const pan = Gesture.Pan()
    .minDistance(TAP_SLOP)
    .onBegin((event) => {
      cancelAnimation(y);
      held.set(true);
      felt.set(true);
      downX.set(event.x);
      downY.set(event.y);
      hold(true);
      scheduleOnRN(touch);
    })
    .onStart(() => {
      dragFrom.set(y.get());
    })
    .onUpdate((event) => {
      y.set(clampY(stops, dragFrom.get() + event.translationY));
    })
    .onFinalize((_event, success) => {
      held.set(false);
      hold(false);
      // A tap on the scale sends the knob there; either way it comes to rest on a whole percent.
      const at = !success && downX.get() < READ_X - 10 ? clampY(stops, downY.get()) : y.get();
      move(Math.round(valueAt(stops, at)), 0, false);
      scheduleOnRN(touch);
    });

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ hold, move });
  useEffect(() => {
    steps.current = { hold, move };
  });

  // The script a finger would follow: up, down past three labels, down into the magenta, and back.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      // Each move is two steps: the press that starts it and the release that ends it.
      const step = SCRIPT[Math.floor(n / 2) % SCRIPT.length];
      const release = n % 2 === 1;
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / (SCRIPT.length * 2)) * LOOP + step.at + (release ? step.time : 0)) * 1000;
      timer = setTimeout(() => {
        steps.current.hold(!release);
        if (!release) steps.current.move(step.to ?? initial, step.time, step.to === null);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, glide back to the resting set-point, and start over from there.
      timer = setTimeout(() => {
        if (held.get()) return;
        felt.set(false);
        steps.current.move(initial, HOME_TIME, false);
        run(Date.now() + (HOME_TIME + 0.6) * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, initial, held, felt]);

  // The line's colour belongs to the height, so the bump takes the colour of where it is; both ends fade out.
  const lineStops = 48;
  const lineColors: string[] = [];
  const linePositions: number[] = [];
  for (let n = 0; n <= lineStops; n++) {
    const u = n / lineStops;
    lineColors.push(blend(CYAN, MAGENTA, dangerAt(safe, valueAt(stops, u * HEIGHT)), clamp01((Math.min(u, 1 - u) * HEIGHT) / END_FADE)));
    linePositions.push(u);
  }

  const line = useDerivedValue(() => {
    const knob = y.get();
    const lean = Math.min(Math.max(lag.get() - knob, -MAX_LEAN), MAX_LEAN);
    const path = Skia.Path.Make();
    path.moveTo(AXIS, 0);
    const from = Math.max(0, knob - SPAN + lean);
    const to = Math.min(HEIGHT, knob + SPAN + lean);
    path.lineTo(AXIS, from);
    for (let at = from + 2; at < to; at += 2) path.lineTo(AXIS - bend(at, knob, lean) * BUMP, at);
    path.lineTo(AXIS, to);
    path.lineTo(AXIS, HEIGHT);
    return path;
  });
  // Ticks every tenth of a step, a longer one every fifth; those beside the bump are pushed left with it.
  const ticks = useDerivedValue(() => {
    const knob = y.get();
    const lean = Math.min(Math.max(lag.get() - knob, -MAX_LEAN), MAX_LEAN);
    const path = Skia.Path.Make();
    for (let n = 0; n <= (stops.length - 1) * 10; n++) {
      const at = SCALE_TOP + (n * gap) / 10;
      const right = AXIS - TICK_INSET - bend(at, knob, lean) * BUMP;
      path.moveTo(right - (n % 5 === 0 ? 12 : 6), at);
      path.lineTo(right, at);
    }
    return path;
  });
  // The ticks beside the bump are brighter.
  const litFrom = useDerivedValue(() => vec(0, y.get() - SPAN));
  const litTo = useDerivedValue(() => vec(0, y.get() + SPAN));
  const knobPlace = useDerivedValue(() => [{ translateX: AXIS + KNOB_OUT }, { translateY: y.get() }, { scale: size.get() }]);
  const chevronsInk = useDerivedValue(() => 1 - 0.6 * press.get());

  const live = useAnimatedStyle(() => ({
    color: interpolateColor(dangerAt(safe, valueAt(stops, y.get())), [0, 1], [CYAN, MAGENTA]),
    transform: [{ translateY: y.get() - 12 }],
  }));
  const sign = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.25 * Math.sin(Math.PI * warn.get()) }] }));
  const note = useAnimatedStyle(() => ({ color: interpolateColor(Math.sin(Math.PI * warn.get()), [0, 1], [NOTE_INK, YELLOW]) }));

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.root} accessible accessibilityRole="adjustable" accessibilityLabel={words.humidityTitle} accessibilityValue={{ text: `${whole}%` }}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Path path={ticks} style="stroke" strokeWidth={1}>
            <LinearGradient start={litFrom} end={litTo} colors={[TICK, TICK_LIT, TICK]} />
          </Path>
          <Path path={line} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round">
            <LinearGradient start={vec(0, 0)} end={vec(0, HEIGHT)} colors={lineColors} positions={linePositions} />
          </Path>
          {/* The knob: a white bead with a pair of chevrons; held, it shows a grey ring and the chevrons dim. */}
          <Group transform={knobPlace}>
            <Circle cx={0} cy={0} r={30}>
              <RadialGradient c={vec(0, 0)} r={30} colors={['rgba(0,0,0,0.45)', 'rgba(0,0,0,0)']} />
            </Circle>
            <Circle cx={0} cy={0} r={17} color="#ffffff" />
            <Circle cx={0} cy={0} r={12.5} style="stroke" strokeWidth={2} color={RING} opacity={press} />
            <Path path={CHEVRONS} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color={NIGHT} opacity={chevronsInk} />
          </Group>
        </Canvas>

        {stops.map((value, k) => (
          <Stop key={k} value={value} at={SCALE_TOP + k * gap} flagged={extremes.includes(value)} y={y} />
        ))}
        <Animated.Text style={[styles.live, live]} numberOfLines={1}>
          {whole}%
        </Animated.Text>

        <Text style={[styles.caption, { top: 158 }]}>{words.temperatureTitle}</Text>
        <Text style={[styles.value, { top: 176 }]}>{words.temperature}</Text>
        <Text style={[styles.caption, { top: 238 }]}>{words.humidityTitle}</Text>
        <View style={styles.counter}>
          <Strip column={2} shown={shown} />
          <Strip column={1} shown={shown} />
          <Strip column={0} shown={shown} />
          <Text style={[styles.digit, styles.percent]}>%</Text>
        </View>
        <Text style={[styles.caption, { top: 338 }]}>{words.absoluteTitle}</Text>
        <Text style={[styles.value, { top: 356 }]}>{words.absolute}</Text>

        <Animated.View style={[styles.sign, sign]}>
          <Ionicons name="warning-outline" size={18} color={YELLOW} />
        </Animated.View>
        <Animated.Text style={[styles.note, note]} numberOfLines={3}>
          <Text style={styles.bullet}>{'• '}</Text>
          {words.warning}
        </Animated.Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  dot: {
    position: 'absolute',
    left: DOT_X - 1.5,
    width: 3,
    height: 3,
    borderRadius: 1.5,
    backgroundColor: YELLOW,
  },
  stop: {
    position: 'absolute',
    left: LABEL_X,
    width: 60,
    height: 20,
    lineHeight: 20,
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  live: {
    position: 'absolute',
    left: LABEL_X - 2,
    top: 0,
    width: 70,
    height: 24,
    lineHeight: 24,
    fontSize: 18,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  caption: {
    position: 'absolute',
    left: READ_X,
    width: 150,
    height: 14,
    lineHeight: 14,
    color: DIM,
    fontSize: 10,
    fontWeight: '500',
    letterSpacing: 0.4,
  },
  value: {
    position: 'absolute',
    left: READ_X,
    width: 150,
    height: 26,
    lineHeight: 26,
    color: '#ffffff',
    fontSize: 20,
    fontWeight: '700',
  },
  counter: {
    position: 'absolute',
    left: READ_X - 2,
    top: 254,
    height: DIGIT_H,
    flexDirection: 'row',
  },
  window: {
    height: DIGIT_H,
    overflow: 'hidden',
  },
  digit: {
    width: DIGIT_W,
    height: DIGIT_H,
    lineHeight: DIGIT_H,
    color: '#ffffff',
    fontSize: 52,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
  percent: {
    width: undefined,
    marginLeft: 1,
  },
  sign: {
    position: 'absolute',
    left: READ_X - 2,
    top: 410,
    width: 22,
    height: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  note: {
    position: 'absolute',
    left: READ_X,
    top: 438,
    width: 148,
    fontSize: 10,
    lineHeight: 15,
  },
  bullet: {
    color: YELLOW,
  },
});
