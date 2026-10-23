import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Line,
  LinearGradient,
  Path,
  RadialGradient,
  Shadow,
  Skia,
  interpolateColors,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/**
 * One piece of an icon's outline, in a box of ±24 points around its middle: move to, line to, close,
 * or an arc round (x, y) with a radius, the angle it starts at and the degrees it sweeps, clockwise
 * from 3 o'clock.
 */
type Stroke = ['M', number, number] | ['L', number, number] | ['A', number, number, number, number, number] | ['Z'];

type Condition = {
  name: string;
  /** Degrees shown next to the name. */
  temperature: number;
  /** The icon's outline. */
  icon: Stroke[];
};

type Props = {
  /** The conditions, clockwise round the wheel; it has no end, so the last one is left of the first. */
  conditions?: Condition[];
  /** Index of the condition under the pointer at first. */
  initial?: number;
  /** The small line over the read-out. */
  lead?: string;
  /** Turns the wheel one segment, flicks it two more, taps the knob and spins back by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the condition the wheel settles on. */
  onChange?: (index: number) => void;
  /** Called when the knob is tapped. */
  onRefresh?: () => void;
};

const WIDTH = 340;
const HEIGHT = 320;

// The knob's centre: the wheel turns about it.
const KNOB_X = 170;
const KNOB_Y = 192;
const KNOB = 27;
const BEZEL = 36;

// The fan: its radius, the radius of the bite round the knob, and the degrees it opens to each side.
const RADIUS = 176;
const BITE = 43;
const SPREAD = 62;
// Degrees one condition takes on the wheel.
const SEGMENT = 45;
const ICON_RADIUS = 112;
const ICON_SCALE = 1.3;
const GLOW = 120;

const LEAD_Y = 262;
const READOUT_Y = 294;
// How far a read-out line has slid when its segment is one place from the pointer.
const READOUT_SHIFT = 28;

// A touch that moves less than this is a tap.
const SLOP = 8;
// A flick carries the wheel on by its speed times this many seconds.
const CARRY = 0.18;
// The knob's tap: the arrow's turn, and the speed the wheel is knocked with, in segments a second.
const REFRESH_TIME = 0.6;
const SHIVER = 2.4;

// The spin back to the first condition.
const BACK_TIME = 1;
const LOOP = 7;
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

// Overshoots a segment by about a ninth and settles in 0.45 s.
const CATCH_SPRING = { mass: 1, stiffness: 240, damping: 18, reduceMotion: ReduceMotion.Never };
const FLICK_SPRING = { mass: 1, stiffness: 110, damping: 14, reduceMotion: ReduceMotion.Never };
const REFRESH_CURVE = Easing.bezier(0.45, 0, 0.25, 1);
// Ease-in-out that runs 4.5% past its target at 79% of the time and comes back.
const BACK_CURVE = Easing.bezier(0.5, 0, 0.2, 1.3);
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

const MUSTARD = '#c9b458';
const KNOB_CENTER = vec(KNOB_X, KNOB_Y);

/** A cloud about 38 points wide whose middle is at (x, y). */
function cloud(x: number, y: number): Stroke[] {
  return [
    ['M', x - 12, y + 9],
    ['L', x + 12, y + 9],
    ['A', x + 12, y + 2, 7, 90, -180],
    ['A', x + 1.8, y - 3.5, 10.31, -8.4, -168.5],
    ['A', x - 12, y + 2, 7, -60, -210],
    ['Z'],
  ];
}

/** Short strokes under a cloud, each from a point down by (dx, dy). */
function drops(from: [number, number][], dx: number, dy: number): Stroke[] {
  return from.flatMap(([x, y]): Stroke[] => [
    ['M', x, y],
    ['L', x + dx, y + dy],
  ]);
}

// A crescent open to the upper right.
const MOON: Stroke[] = [['M', 14.85, 2.07], ['A', 0, 0, 15, 7.9, 254.1], ['A', 7, -7, 12, 220.9, -171.8], ['Z']];

// The knob's arrow: three quarters of a circle, clockwise, with a head at its end.
const REFRESH: Stroke[] = [
  ['M', 9.77, -3.56],
  ['A', 0, 0, 10.4, -20, 275],
  ['M', -6.9, -5.8],
  ['L', -2.7, -10.05],
  ['L', -8.5, -11.6],
];

const CONDITIONS: Condition[] = [
  { name: 'Clear', temperature: 24, icon: MOON },
  { name: 'Cloudy', temperature: 21, icon: [...cloud(-3, 3), ['M', -0.4, -10.2], ['A', 8, -7, 9, 201, 205]] },
  { name: 'Drizzle', temperature: 18, icon: [...cloud(0, -5), ...drops([[-8, 12], [0, 16], [8, 12]], 0.4, 0.8)] },
  { name: 'Rain', temperature: 17, icon: [...cloud(0, -6), ...drops([[-7, 10], [1, 10], [9, 10]], -3, 8)] },
  { name: 'Storm', temperature: 16, icon: [...cloud(0, -7), ['M', 3, 7], ['L', -3, 14], ['L', 3, 14], ['L', -2, 22]] },
  {
    name: 'Snow',
    temperature: -1,
    icon: [
      ...cloud(0, -6),
      ['M', -6.4, 13],
      ['A', -8, 13, 1.6, 0, 359.9],
      ['M', 1.6, 17],
      ['A', 0, 17, 1.6, 0, 359.9],
      ['M', 9.6, 13],
      ['A', 8, 13, 1.6, 0, 359.9],
    ],
  },
  { name: 'Fog', temperature: 9, icon: [...cloud(0, -8), ['M', -13, 11], ['L', 13, 11], ['M', -7, 18], ['L', 7, 18]] },
  {
    name: 'Windy',
    temperature: 19,
    icon: [
      ['M', -16, -5],
      ['L', 3, -5],
      ['A', 3, -10, 5, 90, -250],
      ['M', -16, 3],
      ['L', 11, 3],
      ['A', 11, -2, 5, 90, -250],
      ['M', -16, 11],
      ['L', 1, 11],
      ['A', 1, 16, 5, -90, 250],
    ],
  },
];

function outline(strokes: Stroke[]) {
  const path = Skia.Path.Make();
  for (const stroke of strokes) {
    if (stroke[0] === 'M') path.moveTo(stroke[1], stroke[2]);
    else if (stroke[0] === 'L') path.lineTo(stroke[1], stroke[2]);
    else if (stroke[0] === 'A') path.arcToOval(Skia.XYWHRect(stroke[1] - stroke[3], stroke[2] - stroke[3], stroke[3] * 2, stroke[3] * 2), stroke[4], stroke[5], false);
    else path.close();
  }
  return path;
}

/** The point `radius` from the knob's centre, `degrees` clockwise of straight up. */
function point(degrees: number, radius: number) {
  const angle = (degrees * Math.PI) / 180;
  return { x: KNOB_X + radius * Math.sin(angle), y: KNOB_Y - radius * Math.cos(angle) };
}

function at(degrees: number, radius: number) {
  const { x, y } = point(degrees, radius);
  return `${x.toFixed(2)} ${y.toFixed(2)}`;
}

// The fan's outline: a sector of the wheel with a round bite for the knob.
const FAN = Skia.Path.MakeFromSVGString(
  `M${at(-SPREAD, RADIUS)} A${RADIUS} ${RADIUS} 0 0 1 ${at(SPREAD, RADIUS)} L${at(SPREAD, BITE)} A${BITE} ${BITE} 0 0 0 ${at(-SPREAD, BITE)} Z`,
)!;
const POINTER_TIP = Skia.Path.MakeFromSVGString(
  `M${KNOB_X - 12} ${KNOB_Y - RADIUS - 6} L${KNOB_X + 12} ${KNOB_Y - RADIUS - 6} L${KNOB_X} ${KNOB_Y - RADIUS + 7} Z`,
)!;
const REFRESH_PATH = outline(REFRESH);
const GLOW_AT = vec(KNOB_X, KNOB_Y - RADIUS + 6);

/** How many segments `index` is clockwise of the pointer, by the shorter way round. */
function away(index: number, turn: number, count: number) {
  'worklet';
  let d = (index - turn) % count;
  if (d > count / 2) d -= count;
  if (d < -count / 2) d += count;
  return d;
}

function smooth(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

/** Degrees clockwise of straight up at which the point lies from the knob. */
function degreesOf(x: number, y: number) {
  'worklet';
  return (Math.atan2(x - KNOB_X, KNOB_Y - y) * 180) / Math.PI;
}

function inFan(x: number, y: number) {
  'worklet';
  const reach = Math.hypot(x - KNOB_X, y - KNOB_Y);
  return reach > BITE && reach < RADIUS + 12 && Math.abs(degreesOf(x, y)) < SPREAD + 4;
}

/** Up to 1 in `up` seconds and back to 0 in `down`. */
function pulse(up: number, down: number) {
  'worklet';
  return withSequence(withTiming(1, { duration: up * 1000, ...LINEAR }), withTiming(0, { duration: down * 1000, ...LINEAR }));
}

type SegmentProps = {
  index: number;
  count: number;
  icon: Stroke[];
  turn: SharedValue<number>;
};

/**
 * One condition's part of the wheel: the divider on its clockwise side and its icon, which stands
 * along its radius and lights as it comes under the pointer.
 */
function Segment({ index, count, icon, turn }: SegmentProps) {
  const [path] = useState(() => outline(icon));
  const swing = useDerivedValue(() => [{ rotate: (away(index, turn.get(), count) * SEGMENT * Math.PI) / 180 }]);
  const inSight = useDerivedValue(() => (Math.abs(away(index, turn.get(), count)) < 2.2 ? 1 : 0));
  const lit = useDerivedValue(() => smooth(1 - Math.min(1, Math.abs(away(index, turn.get(), count)) * 2)));
  const grow = useDerivedValue(() => [
    { translateX: KNOB_X },
    { translateY: KNOB_Y - ICON_RADIUS },
    { scale: ICON_SCALE * (1 + 0.06 * lit.get()) },
  ]);
  const ink = useDerivedValue(() => interpolateColors(lit.get(), [0, 1], ['#3a3c44', '#f4efdc']));
  const weight = useDerivedValue(() => 3.2 + 0.8 * lit.get());
  const edge = point(SEGMENT / 2, BITE);
  const rim = point(SEGMENT / 2, RADIUS);
  const edgeLit = point(SEGMENT / 2 + 0.5, BITE);
  const rimLit = point(SEGMENT / 2 + 0.5, RADIUS);

  return (
    <Group transform={swing} origin={KNOB_CENTER} opacity={inSight}>
      <Line p1={vec(edge.x, edge.y)} p2={vec(rim.x, rim.y)} color="#15161a" strokeWidth={1.5} />
      <Line p1={vec(edgeLit.x, edgeLit.y)} p2={vec(rimLit.x, rimLit.y)} color="rgba(255,255,255,0.03)" strokeWidth={1} />
      <Group transform={grow}>
        <Path path={path} style="stroke" strokeWidth={weight} strokeCap="round" strokeJoin="round" color={ink} />
      </Group>
    </Group>
  );
}

/** One condition in words. The lines ride with the wheel: the one that leaves slides away and fades as the next one slides in. */
function Readout({ index, count, text, turn }: { index: number; count: number; text: string; turn: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = away(index, turn.get(), count);
    return { opacity: 1 - Math.min(1, Math.abs(d) * 2), transform: [{ translateY: d * READOUT_SHIFT }] };
  });
  return (
    <Animated.Text style={[styles.readout, style]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/**
 * A weather dial sunk into a dark surface. The fan is the part in sight of a wheel of conditions
 * centred on the knob: dragging across it turns the wheel under a fixed pointer, a flick carries it
 * on, and it catches on the nearest segment with a spring. The segment under the pointer lights up
 * and the line below reads it out. Tapping a side segment brings it in; tapping the knob spins its
 * arrow and shakes the wheel.
 */
export function WeatherFanDial({ conditions = CONDITIONS, initial = 0, lead = "It's", autoPlay = false, onChange, onRefresh }: Props) {
  const still = useReducedMotion();
  const count = Math.max(1, conditions.length);
  const home = Math.min(Math.max(initial, 0), count - 1);
  const auto = autoPlay && !still && conditions.length > 3;

  // The wheel's position, in segments; it keeps counting past the last condition.
  const turn = useSharedValue(home);
  // The segment the wheel is heading for.
  const heading = useSharedValue(home);
  // The knob's arrow, in degrees, and the knob's dip under the finger.
  const arrow = useSharedValue(0);
  const knobPress = useSharedValue(0);
  // The pointer's flicker as a segment catches.
  const flicker = useSharedValue(0);
  const grabTurn = useSharedValue(0);
  const lastAngle = useSharedValue(0);
  const swept = useSharedValue(0);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  // A finger turned the wheel last, so each segment is felt.
  const byHand = useSharedValue(false);
  const held = useSharedValue(false);

  // `heading` brought into the list, for the screen reader.
  const [selected, setSelected] = useState(home);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const wrap = (index: number) => ((index % count) + count) % count;
  const label = (condition: Condition) => `${condition.name} & ${condition.temperature}°`;

  const touch = () => setTouches((n) => n + 1);

  /** The wheel is now heading for segment `target`. */
  const report = (target: number) => {
    if (wrap(target) === selected) return;
    setSelected(wrap(target));
    onChange?.(wrap(target));
  };

  /** Turns the wheel `by` segments from where it is heading: one on the catch spring, more on a softer one. */
  const step = (by: number) => {
    if (by === 0) return;
    const target = heading.get() + by;
    heading.set(target);
    if (still) turn.set(target);
    else turn.set(withSpring(target, Math.abs(by) === 1 ? CATCH_SPRING : FLICK_SPRING));
    report(target);
  };

  /** The knob: its arrow goes once round and the wheel shakes in its catch. */
  const refresh = () => {
    if (!still) {
      knobPress.set(pulse(0.08, 0.2));
      arrow.set(0);
      arrow.set(withTiming(360, { duration: REFRESH_TIME * 1000, easing: REFRESH_CURVE, reduceMotion: ReduceMotion.Never }));
      turn.set(withSpring(heading.get(), { ...CATCH_SPRING, velocity: SHIVER }));
    }
    onRefresh?.();
  };

  /** Spins to segment `target` in one sweep that runs a little past and comes back. */
  const spin = (target: number) => {
    heading.set(target);
    turn.set(withTiming(target, { duration: BACK_TIME * 1000, easing: BACK_CURVE, reduceMotion: ReduceMotion.Never }));
    report(target);
  };

  const pick = (x: number, y: number) => {
    if (Math.hypot(x - KNOB_X, y - KNOB_Y) <= BEZEL + 4) {
      refresh();
    } else if (inFan(x, y)) {
      // A side segment comes under the pointer.
      step(Math.round(degreesOf(x, y) / SEGMENT + turn.get()) - heading.get());
    }
  };

  // A segment has come under the pointer: the pointer flickers like a detent catching.
  useAnimatedReaction(
    () => Math.round(turn.get()),
    (now, old) => {
      if (old === null || now === old) return;
      if (!still) flicker.set(pulse(0.05, 0.25));
      if (byHand.get()) scheduleOnRN(tick);
    },
  );

  const pan = Gesture.Pan()
    .minDistance(SLOP)
    .onBegin((event) => {
      held.set(true);
      startX.set(event.x);
      startY.set(event.y);
      scheduleOnRN(touch);
    })
    .onStart((event) => {
      byHand.set(true);
      grabTurn.set(turn.get());
      lastAngle.set(degreesOf(event.x, event.y));
      swept.set(0);
    })
    .onUpdate((event) => {
      if (!inFan(startX.get(), startY.get())) return;
      // The finger's angle wraps behind the knob; the turn it adds up to does not.
      const angle = degreesOf(event.x, event.y);
      let delta = angle - lastAngle.get();
      if (delta > 180) delta -= 360;
      if (delta < -180) delta += 360;
      lastAngle.set(angle);
      swept.set(swept.get() + delta);
      turn.set(grabTurn.get() - swept.get() / SEGMENT);
    })
    .onEnd((event) => {
      if (!inFan(startX.get(), startY.get())) return;
      // How fast the finger was going round the knob, in segments a second.
      const x = event.x - KNOB_X;
      const y = event.y - KNOB_Y;
      const reach = Math.max(x * x + y * y, 50 * 50);
      const speed = still ? 0 : ((-(x * event.velocityY - y * event.velocityX) / reach) * 180) / Math.PI / SEGMENT;
      const target = Math.round(turn.get() + speed * CARRY);
      heading.set(target);
      if (still) turn.set(target);
      else turn.set(withSpring(target, { ...CATCH_SPRING, velocity: speed }));
      scheduleOnRN(report, target);
    })
    .onFinalize(() => {
      held.set(false);
      scheduleOnRN(touch);
    });

  const tap = Gesture.Tap()
    .maxDistance(SLOP)
    .onEnd((event) => {
      byHand.set(true);
      scheduleOnRN(pick, event.x, event.y);
    });

  /** Turns back to the first condition by the shorter way once the finger has gone; false while it is still down. */
  const goHome = () => {
    if (held.get()) return false;
    byHand.set(false);
    spin(home + Math.round((turn.get() - home) / count) * count);
    return true;
  };

  /** Counts the wheel's turns from the first condition again; what shows does not change. */
  const rewind = () => {
    heading.set(home);
    turn.set(home);
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ step, refresh, spin, goHome, rewind });
  useEffect(() => {
    steps.current = { step, refresh, spin, goHome, rewind };
  });

  // The script a finger would follow: one segment on, a flick of two more, the knob, and back.
  useEffect(() => {
    if (!auto) return;
    const script = [
      { at: 1.0, run: () => steps.current.step(1) },
      { at: 2.4, run: () => steps.current.step(2) },
      { at: 4.1, run: () => steps.current.refresh() },
      { at: 5.2, run: () => steps.current.spin(home) },
    ];
    let timer: ReturnType<typeof setTimeout>;
    const play = (begin: number, n: number) => {
      const next = script[n % script.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / script.length) * LOOP + next.at) * 1000;
      timer = setTimeout(() => {
        next.run();
        play(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      play(Date.now(), 0);
    } else {
      // A touch took over: wait, turn back to the first condition and start over.
      timer = setTimeout(() => {
        if (!steps.current.goHome()) return;
        timer = setTimeout(() => {
          steps.current.rewind();
          play(Date.now(), 0);
        }, (BACK_TIME + 0.2) * 1000);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, home]);

  // The light that falls from the pointer onto the segment under it.
  const glow = useDerivedValue(() => {
    const seated = 1 - Math.min(1, Math.abs(turn.get() - Math.round(turn.get())) * 2);
    return (0.2 + 0.14 * seated) * (1 + 0.5 * flicker.get());
  });
  const lineOpacity = useDerivedValue(() => 0.75 + 0.25 * flicker.get());
  const lineWidth = useDerivedValue(() => 1 + 0.6 * flicker.get());
  const pressed = useDerivedValue(() => [{ scale: 1 - 0.04 * knobPress.get() }]);
  const spun = useDerivedValue(() => [{ translateX: KNOB_X }, { translateY: KNOB_Y }, { rotate: (arrow.get() * Math.PI) / 180 }]);

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.root}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={lead}
        accessibilityValue={{ text: conditions[selected] ? label(conditions[selected]) : '' }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          touch();
          step(event.nativeEvent.actionName === 'increment' ? 1 : -1);
        }}
      >
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Path path={FAN} color="#1d1f25" />
          <Group clip={FAN}>
            <Circle c={GLOW_AT} r={GLOW} opacity={glow}>
              <RadialGradient c={GLOW_AT} r={GLOW} colors={[MUSTARD, '#c9b45800']} />
            </Circle>
            {conditions.map((condition, index) => (
              <Segment key={index} index={index} count={count} icon={condition.icon} turn={turn} />
            ))}
            {/* The fan is sunk into the surface: its edges cast a shadow inward. */}
            <Path path={FAN} style="stroke" strokeWidth={10} color="rgba(0,0,0,0.6)">
              <BlurMask blur={7} style="normal" />
            </Path>
          </Group>
          <Path path={FAN} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, KNOB_Y - RADIUS * 0.6)} end={vec(0, KNOB_Y)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.1)']} />
          </Path>

          {/* The pointer: a line from the triangle's tip down to the knob. */}
          <Line p1={vec(KNOB_X, KNOB_Y - RADIUS + 4)} p2={vec(KNOB_X, KNOB_Y - BITE)} color={MUSTARD} opacity={lineOpacity} strokeWidth={lineWidth} />
          <Path path={POINTER_TIP} color={MUSTARD} />

          {/* The raised round button the wheel turns about, with a faint indigo light under it. */}
          <Group transform={[{ translateX: KNOB_X }, { translateY: KNOB_Y + 16 }, { scaleX: 62 }, { scaleY: 46 }]}>
            <Circle cx={0} cy={0} r={1}>
              <RadialGradient c={vec(0, 0)} r={1} colors={['#4b3fd94d', '#4b3fd900']} />
            </Circle>
          </Group>
          <Circle c={KNOB_CENTER} r={BEZEL}>
            <LinearGradient start={vec(0, KNOB_Y - BEZEL)} end={vec(0, KNOB_Y + BEZEL)} colors={['#17181c', '#222327']} />
          </Circle>
          <Group transform={pressed} origin={KNOB_CENTER}>
            <Circle c={KNOB_CENTER} r={KNOB}>
              <LinearGradient start={vec(0, KNOB_Y - KNOB)} end={vec(0, KNOB_Y + KNOB)} colors={['#3a3b42', '#24252a']} />
              <Shadow dx={0} dy={4} blur={5} color="rgba(0,0,0,0.55)" />
            </Circle>
            <Circle c={KNOB_CENTER} r={KNOB - 0.5} style="stroke" strokeWidth={1}>
              <LinearGradient start={vec(0, KNOB_Y - KNOB)} end={vec(0, KNOB_Y)} colors={['rgba(255,255,255,0.14)', 'rgba(255,255,255,0)']} />
            </Circle>
            <Group transform={spun}>
              <Path path={REFRESH_PATH} style="stroke" strokeWidth={3} strokeCap="round" strokeJoin="round" color="#5a5c66" />
            </Group>
          </Group>
        </Canvas>

        <Text style={styles.lead} numberOfLines={1}>
          {lead}
        </Text>
        {conditions.map((condition, index) => (
          <Readout key={index} index={index} count={count} text={label(condition)} turn={turn} />
        ))}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  lead: {
    position: 'absolute',
    left: 0,
    top: LEAD_Y - 11,
    width: WIDTH,
    height: 22,
    lineHeight: 22,
    textAlign: 'center',
    color: '#82848f',
    fontSize: 17,
    fontWeight: '100',
    includeFontPadding: false,
  },
  readout: {
    position: 'absolute',
    left: 0,
    top: READOUT_Y - 19,
    width: WIDTH,
    height: 38,
    lineHeight: 38,
    textAlign: 'center',
    color: '#b0b2bc',
    fontSize: 30,
    fontWeight: '100',
    includeFontPadding: false,
  },
});
