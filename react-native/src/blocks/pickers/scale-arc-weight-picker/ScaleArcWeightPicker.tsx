import { Canvas, Circle, FillType, Group, LinearGradient as Fade, Path, Rect, Shadow, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  title?: string;
  message?: string;
  /** Today's weight, in kilograms. */
  current?: number;
  /** The target at first, in kilograms; `autoPlay` comes back to it at the end of every loop. */
  target?: number;
  /** The lightest and the heaviest target the dial offers, in kilograms. */
  range?: [number, number];
  /** Turns the dial to three targets and back by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the target and whether it is in pounds whenever it changes. */
  onChange?: (value: number, pounds: boolean) => void;
};

// The card.
const WIDTH = 340;
const HEIGHT = 356;
const RADIUS = 32;
const MIDDLE = WIDTH / 2;
const TITLE_Y = 38;
const MESSAGE_Y = 67;
// The kg / lbs switch.
const SWITCH_Y = 110;
const SWITCH_W = 96;
const SWITCH_H = 28;
// The two boxes with today's weight and the target.
const BOX_Y = 162;
const BOX_W = 54;
const BOX_H = 48;
const BOX_GAP = 84;
const TRIANGLE_Y = 198;
// The dial is the top of a circle of `DIAL_R` whose highest point is at `DIAL_Y`.
const DIAL_Y = 214;
const DIAL_R = 380;
const CENTER_Y = DIAL_Y + DIAL_R;
const BAND = 90;
// Degrees between two units, and the points a finger travels for one unit.
const PITCH = 2.6;
const UNIT_ARC = 16.6;
const LONG_EVERY = 5;
const POUND = 2.2046;
const MARK_W = 40;
const MARK_H = 18;

// The simulation's step, in seconds, and the longest a frame is counted as.
const TICK = 1 / 240;
const LONGEST_FRAME = 0.25;
const SETTLE = { stiffness: 60, damping: 15 };
// A released dial rests this many seconds of its speed further on.
const COAST = 0.25;
// A held dial follows the finger this closely, in seconds.
const GRIP = 0.03;
const NEEDLE = { stiffness: 180, damping: 11 };
// The needle leans up to `LEAN` degrees, `DRAG` degrees for each unit a second, and gets a push of
// `SWING` degrees a second when the dial comes to rest.
const LEAN = 1.2;
const DRAG = 0.06;
const SWING = 36;
const BOB = { stiffness: 500, damping: 22 };
const KNOCK = 60;
const PULSE = { stiffness: 300, damping: 20 };
const SWELL = 1.2;
const KNOB = { stiffness: 320, damping: 26 };
const CONVERT_TIME = 0.7;

// autoPlay (seconds). Each turn: when it starts, the units from the first target, how long it takes.
const LOOP = 8;
const BACK_TIME = 0.8;
const SCRIPT = [
  { at: 1, offset: 6, time: 0.9 },
  { at: 2.7, offset: -6, time: 1.1 },
  { at: 4.5, offset: -13, time: 0.6 },
  { at: 5.9, offset: 0, time: 1 },
];
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const INK = '#1e1e1e';
const LEAF = '#18b63c';
const GREEN = [24, 182, 60];
const AMBER = [245, 166, 35];

// A finger holds the dial; the dial follows a set course (the script, or the spin to the other
// unit); the dial coasts to the whole unit it will rest on.
const HELD = 0;
const GLIDING = 1;
const FREE = 2;

/** Everything that moves, stepped in small fixed steps on every frame. */
type Sim = {
  /** The weight under the needle and today's weight, in the unit shown. */
  value: number;
  current: number;
  pounds: boolean;
  /** The switch's knob: 0 on kg … 1 on lbs. */
  knob: number;
  knobSpeed: number;
  /** The needle's lean, in degrees. */
  needle: number;
  needleSpeed: number;
  /** The triangle's bob, in points, and the swell of the target's box. */
  bob: number;
  bobSpeed: number;
  pulse: number;
  pulseSpeed: number;
  mode: number;
  /** The dial was last moved by a finger, not by the script. */
  byHand: boolean;
  goal: number;
  speed: number;
  // A course: where the target and today's weight start and end, and how far along it is.
  from: number;
  to: number;
  currentFrom: number;
  currentTo: number;
  elapsed: number;
  duration: number;
  /** The dial's place when the finger came down. */
  grabbed: number;
  moving: boolean;
  /** The way the dial last turned: 1 towards heavier, -1 towards lighter. */
  heading: number;
  whole: number;
  /** The last order carried out. */
  order: number;
};

/** What the script or a button asks of the dial. */
type Order = { id: number; kind: 'glide' | 'convert' | 'nudge'; value: number; pounds: boolean; duration: number };

function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(Math.max(value, low), high);
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function smooth(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

/** Cubic ease-in-out, for the dial's set courses. */
function inOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** 0 when the target is above today's weight, 1 when it is below. */
function lossOf(value: number, current: number) {
  'worklet';
  return clamp01((current - value) / 0.6 + 0.5);
}

/** Green for a gain, amber for a loss. */
function tone(loss: number, opacity: number) {
  'worklet';
  return `rgba(${Math.round(mix(GREEN[0], AMBER[0], loss))},${Math.round(mix(GREEN[1], AMBER[1], loss))},${Math.round(mix(GREEN[2], AMBER[2], loss))},${opacity})`;
}

/** The bound `kilograms` in the unit shown. */
function bound(kilograms: number, pounds: boolean) {
  'worklet';
  return pounds ? Math.round(kilograms * POUND) : kilograms;
}

function settle(s: Sim) {
  'worklet';
  if (s.mode === GLIDING) {
    s.current = s.currentTo;
    s.mode = FREE;
  }
  if (s.mode === FREE) s.value = Math.round(s.goal);
  s.speed = 0;
  s.moving = false;
  s.whole = Math.round(s.value);
  s.knob = s.pounds ? 1 : 0;
  s.knobSpeed = 0;
  s.needle = 0;
  s.needleSpeed = 0;
  s.bob = 0;
  s.bobSpeed = 0;
  s.pulse = 0;
  s.pulseSpeed = 0;
}

function resting(current: number, target: number): Sim {
  'worklet';
  return {
    value: target,
    current,
    pounds: false,
    knob: 0,
    knobSpeed: 0,
    needle: 0,
    needleSpeed: 0,
    bob: 0,
    bobSpeed: 0,
    pulse: 0,
    pulseSpeed: 0,
    mode: FREE,
    byHand: false,
    goal: target,
    speed: 0,
    from: 0,
    to: 0,
    currentFrom: 0,
    currentTo: 0,
    elapsed: 0,
    duration: 0,
    grabbed: 0,
    moving: false,
    heading: 1,
    whole: target,
    order: 0,
  };
}

/** Sets the dial on a course to `value`, with today's weight going to `current`. */
function course(s: Sim, value: number, current: number, duration: number) {
  'worklet';
  s.mode = GLIDING;
  s.from = s.value;
  s.to = value;
  s.currentFrom = s.current;
  s.currentTo = current;
  s.goal = value;
  s.elapsed = 0;
  s.duration = duration;
  s.moving = true;
}

/** The state `passed` seconds later. `low` and `high` are the dial's ends, in kilograms. */
function advance(before: Sim, order: Order, passed: number, low: number, high: number, still: boolean): Sim {
  'worklet';
  const s: Sim = { ...before };

  if (order.id !== s.order) {
    s.order = order.id;
    if (order.kind === 'glide') {
      s.byHand = false;
      course(s, clamp(order.value, bound(low, s.pounds), bound(high, s.pounds)), s.current, order.duration);
    } else if (order.kind === 'nudge') {
      s.mode = FREE;
      s.byHand = true;
      s.goal = clamp(Math.round(s.goal) + order.value, bound(low, s.pounds), bound(high, s.pounds));
      s.moving = true;
    } else if (order.pounds !== s.pounds) {
      // The same weights in the other unit: the dial spins to the new numbers.
      const factor = order.pounds ? POUND : 1 / POUND;
      s.byHand = false;
      s.pounds = order.pounds;
      course(s, clamp(Math.round(Math.round(s.goal) * factor), bound(low, s.pounds), bound(high, s.pounds)), Math.round(Math.round(s.current) * factor), CONVERT_TIME);
    }
  }

  const steps = Math.max(1, Math.ceil(passed / TICK));
  const h = passed / steps;
  for (let n = 0; n < steps; n++) {
    const was = s.value;
    if (s.mode === HELD) {
      // A touch closer than a frame apart, so the speed has no steps in it.
      s.value += (s.goal - s.value) * (1 - Math.exp(-h / GRIP));
      s.speed = (s.value - was) / h;
    } else if (s.mode === GLIDING) {
      s.elapsed += h;
      const t = Math.min(1, s.elapsed / Math.max(s.duration, 0.001));
      const eased = inOut(t);
      s.value = s.from + (s.to - s.from) * eased;
      s.current = s.currentFrom + (s.currentTo - s.currentFrom) * eased;
      s.speed = (s.value - was) / h;
      if (t >= 1) {
        s.mode = FREE;
        s.speed = 0;
      }
    } else {
      s.speed += (SETTLE.stiffness * (s.goal - s.value) - SETTLE.damping * s.speed) * h;
      s.value += s.speed * h;
    }

    if (Math.abs(s.speed) > 0.5) s.heading = s.speed > 0 ? 1 : -1;
    // Each unit that passes under the needle knocks the triangle.
    const unit = Math.round(s.value);
    if (unit !== s.whole) {
      s.whole = unit;
      s.bobSpeed -= KNOCK;
    }
    // At rest: the needle gets a push the way the dial was going, and the box swells.
    if (s.moving && s.mode === FREE && Math.abs(s.goal - s.value) < 0.02 && Math.abs(s.speed) < 0.4) {
      s.moving = false;
      s.needleSpeed -= s.heading * SWING;
      s.pulseSpeed += SWELL;
    }

    // The needle is dragged along by the dial while it turns.
    const lean = clamp(-s.speed * DRAG, -LEAN, LEAN);
    s.needleSpeed += (NEEDLE.stiffness * (lean - s.needle) - NEEDLE.damping * s.needleSpeed) * h;
    s.needle += s.needleSpeed * h;
    s.bobSpeed += (-BOB.stiffness * s.bob - BOB.damping * s.bobSpeed) * h;
    s.bob = clamp(s.bob + s.bobSpeed * h, -3, 3);
    s.pulseSpeed += (-PULSE.stiffness * s.pulse - PULSE.damping * s.pulseSpeed) * h;
    s.pulse += s.pulseSpeed * h;
    s.knobSpeed += (KNOB.stiffness * ((s.pounds ? 1 : 0) - s.knob) - KNOB.damping * s.knobSpeed) * h;
    s.knob += s.knobSpeed * h;
  }
  // Under reduced motion nothing turns: every change is a jump.
  if (still) settle(s);
  return s;
}

/** The point `radius` from the dial's centre, `degrees` clockwise from straight up. */
function at(degrees: number, radius: number) {
  'worklet';
  const angle = (degrees * Math.PI) / 180;
  return { x: MIDDLE + Math.sin(angle) * radius, y: CENTER_Y - Math.cos(angle) * radius };
}

// The part of the band that holds the ticks, where their fade towards the card's sides is drawn.
const TICK_BAND = Skia.Path.Make();
TICK_BAND.addCircle(MIDDLE, CENTER_Y, DIAL_R - 6);
TICK_BAND.addCircle(MIDDLE, CENTER_Y, DIAL_R - 72);
TICK_BAND.setFillType(FillType.EvenOdd);

/** A number in one of the boxes. It rolls like a counter's wheel: most of the way through a unit it stands still. */
function Roller({ low, sim, today }: { low: number; sim: SharedValue<Sim>; today: boolean }) {
  // `low` is the whole unit below the weight. It arrives a frame late, so the roll is held at its ends meanwhile.
  const oldStyle = useAnimatedStyle(() => {
    const rolled = smooth(clamp01(((today ? sim.get().current : sim.get().value) - low - 0.2) / 0.6));
    return { opacity: 1 - rolled, transform: [{ translateY: -rolled * 26 }] };
  });
  const newStyle = useAnimatedStyle(() => {
    const rolled = smooth(clamp01(((today ? sim.get().current : sim.get().value) - low - 0.2) / 0.6));
    return { opacity: rolled, transform: [{ translateY: (1 - rolled) * 26 }] };
  });
  return (
    <View style={styles.window}>
      <Animated.Text style={[styles.figure, oldStyle]}>{low}</Animated.Text>
      <Animated.Text style={[styles.figure, newStyle]}>{low + 1}</Animated.Text>
    </View>
  );
}

/** The number under a long tick: it turns round the dial's centre and fades out towards the card's sides. */
function Mark({ unit, sim }: { unit: number; sim: SharedValue<Sim> }) {
  const style = useAnimatedStyle(() => {
    const degrees = (unit - sim.get().value) * PITCH;
    const place = at(degrees, DIAL_R - 58);
    return {
      opacity: clamp01((27 - Math.abs(degrees)) / 5),
      transform: [{ translateX: place.x - MARK_W / 2 }, { translateY: place.y - MARK_H / 2 }, { rotate: `${degrees}deg` }],
    };
  });
  return <Animated.Text style={[styles.mark, style]}>{unit}</Animated.Text>;
}

/**
 * A target-weight picker shaped like the window of a bathroom scale. A card holds a kg / lbs switch,
 * today's weight and the target in two boxes, and at the bottom the top of a large white dial with
 * ticks and numbers under a fixed green needle. Dragging the dial turns it with weight: it coasts,
 * settles on a whole unit, and the needle swings. A wash over the ticks spans from today's weight to
 * the target, green for a gain and amber for a loss.
 */
export function ScaleArcWeightPicker({
  title = 'Your Target Weight',
  message = 'Set a target weight to personalize your health\nand progress journey.',
  current = 60,
  target = 70,
  range = [30, 200],
  autoPlay = false,
  onChange,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still;
  const [low, high] = range;

  const sim = useSharedValue<Sim>(resting(current, target));
  const order = useSharedValue<Order>({ id: 0, kind: 'glide', value: target, pounds: false, duration: 0 });
  const orders = useRef(0);
  // The whole numbers the dial and the boxes are drawn from.
  const [reading, setReading] = useState({ value: target, floor: target, current, currentFloor: current, pounds: false });
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  // A finger is on the dial.
  const [holding, setHolding] = useState(false);

  // Other weights: everything goes back to rest.
  const [origin, setOrigin] = useState({ current, target });
  if (origin.current !== current || origin.target !== target) {
    setOrigin({ current, target });
    setReading({ value: target, floor: target, current, currentFloor: current, pounds: false });
  }
  useEffect(() => {
    sim.set(resting(current, target));
  }, [current, target, sim]);

  const read = (value: number, floor: number, now: number, nowFloor: number, pounds: boolean, changed: boolean, feel: boolean) => {
    setReading({ value, floor, current: now, currentFloor: nowFloor, pounds });
    if (changed) onChange?.(value, pounds);
    if (!feel || process.env.EXPO_OS === 'web') return;
    // A firmer tick at each long tick.
    if (value % LONG_EVERY === 0) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };

  useFrameCallback((frame) => {
    const before = sim.get();
    const passed = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, LONGEST_FRAME);
    const after = advance(before, order.get(), passed, low, high, still);
    const stepped = Math.round(after.value) !== Math.round(before.value);
    const switched = after.pounds !== before.pounds;
    if (
      stepped ||
      switched ||
      Math.floor(after.value) !== Math.floor(before.value) ||
      Math.round(after.current) !== Math.round(before.current) ||
      Math.floor(after.current) !== Math.floor(before.current)
    ) {
      // No tick is felt while the dial turns by itself.
      const feel = stepped && after.byHand && after.mode !== GLIDING;
      scheduleOnRN(
        read,
        Math.round(after.value),
        Math.floor(after.value),
        Math.round(after.current),
        Math.floor(after.current),
        after.pounds,
        stepped || switched,
        feel,
      );
    }
    sim.set(after);
  });

  const send = (kind: Order['kind'], value: number, pounds: boolean, duration: number) => {
    orders.current += 1;
    order.set({ id: orders.current, kind, value, pounds, duration });
  };

  // The timers below call whatever this is at the time they fire.
  const steps = useRef({ send });
  useEffect(() => {
    steps.current = { send };
  });

  // The script a finger would follow: up six units, down twelve, on below today's weight, and back.
  useEffect(() => {
    if (!auto || holding) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const move = SCRIPT[n % SCRIPT.length];
      // Every turn is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + move.at) * 1000;
      timer = setTimeout(() => {
        steps.current.send('glide', target + move.offset, false, move.time);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to kilograms and the first target, and start over.
      timer = setTimeout(() => {
        steps.current.send('convert', 0, false, 0);
        timer = setTimeout(() => {
          steps.current.send('glide', target, false, BACK_TIME);
          run(Date.now() + (BACK_TIME + 0.6) * 1000, 0);
        }, CONVERT_TIME * 1000);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, holding, target]);

  const grab = () => {
    setHolding(true);
    setTouches((n) => n + 1);
  };

  const release = () => {
    setHolding(false);
    setTouches((n) => n + 1);
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      scheduleOnRN(grab);
      const s = sim.get();
      sim.set({ ...s, mode: HELD, byHand: true, grabbed: s.value, goal: s.value });
    })
    .onUpdate((event) => {
      // The ticks go with the finger.
      const s = sim.get();
      if (s.mode !== HELD) return;
      sim.set({ ...s, goal: clamp(s.grabbed - event.translationX / UNIT_ARC, bound(low, s.pounds), bound(high, s.pounds)) });
    })
    .onFinalize((event) => {
      // The dial coasts on and rests on a whole unit.
      const s = sim.get();
      if (s.mode === HELD) {
        const speed = -event.velocityX / UNIT_ARC;
        sim.set({
          ...s,
          mode: FREE,
          speed,
          goal: clamp(Math.round(s.value + speed * COAST), bound(low, s.pounds), bound(high, s.pounds)),
          moving: true,
        });
      }
      scheduleOnRN(release);
    });

  const knobStyle = useAnimatedStyle(() => ({ transform: [{ translateX: sim.get().knob * (SWITCH_W / 2 - 3) }] }));
  const kgStyle = useAnimatedStyle(() => ({ opacity: mix(0.45, 1, clamp01(1 - sim.get().knob)) }));
  const lbsStyle = useAnimatedStyle(() => ({ opacity: mix(0.45, 1, clamp01(sim.get().knob)) }));
  const boxStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + sim.get().pulse }] }));
  const triangleStyle = useAnimatedStyle(() => ({ transform: [{ translateY: sim.get().bob }] }));

  // The wash from today's weight to the needle, over the ticks.
  const wash = useDerivedValue(() => {
    const s = sim.get();
    const path = Skia.Path.Make();
    const start = clamp((s.current - s.value) * PITCH, -32, 32);
    if (Math.abs(start) <= 0.05) return path;
    const count = Math.max(2, Math.ceil(Math.abs(start)));
    const first = at(start, DIAL_R - 8);
    path.moveTo(first.x, first.y);
    for (let n = 1; n <= count; n++) {
      const point = at((start * (count - n)) / count, DIAL_R - 8);
      path.lineTo(point.x, point.y);
    }
    for (let n = 0; n <= count; n++) {
      const point = at((start * n) / count, DIAL_R - 46);
      path.lineTo(point.x, point.y);
    }
    path.close();
    return path;
  });
  const washColor = useDerivedValue(() => tone(lossOf(sim.get().value, sim.get().current), 0.25));

  // A tick for every unit near the needle, a longer one at every fifth.
  const ticks = useDerivedValue(() => {
    const value = sim.get().value;
    const path = Skia.Path.Make();
    const first = Math.ceil(value - 12);
    for (let unit = first; unit <= first + 24; unit++) {
      const degrees = (unit - value) * PITCH;
      if (Math.abs(degrees) >= 27) continue;
      const from = at(degrees, DIAL_R - 14);
      const to = at(degrees, DIAL_R - (unit % LONG_EVERY === 0 ? 40 : 30));
      path.moveTo(from.x, from.y);
      path.lineTo(to.x, to.y);
    }
    return path;
  });

  const needle = useDerivedValue(() => {
    const lean = sim.get().needle;
    const path = Skia.Path.Make();
    const from = at(lean, DIAL_R);
    const to = at(lean, DIAL_R - 46);
    path.moveTo(from.x, from.y);
    path.lineTo(to.x, to.y);
    return path;
  });

  const unit = reading.pounds ? 'lbs' : 'kg';
  const gap = reading.value - reading.current;
  const base = Math.floor(reading.floor / LONG_EVERY);
  const marks = [-3, -2, -1, 0, 1, 2, 3, 4].map((n) => (base + n) * LONG_EVERY);
  const lines = message.split('\n');

  return (
    <View style={styles.root}>
      <View style={styles.card}>
        {/* A wash of lilac into aqua, fading to white at the top. */}
        <LinearGradient style={StyleSheet.absoluteFill} colors={['#d9c4f4', '#bfeaf0']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} />
        <LinearGradient style={StyleSheet.absoluteFill} colors={['#ffffff', '#ffffff00']} start={{ x: 0, y: 20 / HEIGHT }} end={{ x: 0, y: 210 / HEIGHT }} />

        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          {/* The dial: a white band with a paler disc inside it, both far larger than the card. */}
          <Circle cx={MIDDLE} cy={CENTER_Y} r={DIAL_R} color="#ffffff">
            <Shadow dx={0} dy={-2} blur={12} color="rgba(106,95,168,0.2)" />
          </Circle>
          <Circle cx={MIDDLE} cy={CENTER_Y} r={DIAL_R - BAND} color="#f1eefa" />
          <Path path={wash} color={washColor} />
          <Path path={ticks} style="stroke" strokeWidth={1.5} strokeCap="round" color="#9ba1a8" />
          {/* Ticks fade out towards the card's sides. */}
          <Group clip={TICK_BAND}>
            <Rect x={0} y={DIAL_Y} width={36} height={HEIGHT - DIAL_Y}>
              <Fade start={vec(4, 0)} end={vec(36, 0)} colors={['#ffffff', '#ffffff00']} />
            </Rect>
            <Rect x={WIDTH - 36} y={DIAL_Y} width={36} height={HEIGHT - DIAL_Y}>
              <Fade start={vec(WIDTH - 4, 0)} end={vec(WIDTH - 36, 0)} colors={['#ffffff', '#ffffff00']} />
            </Rect>
          </Group>
          <Path path={needle} style="stroke" strokeWidth={2.5} strokeCap="round" color={LEAF} />
        </Canvas>
        {marks.map((mark) => (
          <Mark key={mark} unit={mark} sim={sim} />
        ))}

        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        {lines.map((line, n) => (
          <Text key={n} style={[styles.message, { top: MESSAGE_Y + (n - (lines.length - 1) / 2) * 15 - 8 }]} numberOfLines={1}>
            {line}
          </Text>
        ))}

        {/* The unit switch: a white capsule with a grey knob under the chosen unit. */}
        <View style={styles.switch}>
          <Animated.View style={[styles.knob, knobStyle]} />
          <Animated.Text style={[styles.unit, { left: 1.5 }, kgStyle]}>kg</Animated.Text>
          <Animated.Text style={[styles.unit, { right: 1.5 }, lbsStyle]}>lbs</Animated.Text>
        </View>

        {/* The boxes: today's weight, a chevron, and the target, outlined, over the needle. */}
        <View style={[styles.box, { left: MIDDLE - BOX_GAP - BOX_W / 2 }]}>
          <Roller low={reading.currentFloor} sim={sim} today />
        </View>
        <View style={styles.chevron} />
        <Animated.View style={[styles.box, styles.outlined, { left: MIDDLE - BOX_W / 2 }, boxStyle]}>
          <Roller low={reading.floor} sim={sim} today={false} />
        </Animated.View>
        {/* How far the target is from today's weight. */}
        <Text style={[styles.gap, { color: tone(gap < 0 ? 1 : 0, 1) }]} numberOfLines={1}>
          {gap > 0 ? '+' : gap < 0 ? '−' : ''}
          {Math.abs(gap)} {unit}
        </Text>
        <Animated.View style={[styles.triangle, triangleStyle]} />

        <GestureDetector gesture={pan}>
          <View
            style={styles.dial}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={title}
            accessibilityValue={{ text: `${reading.value} ${unit}` }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(event) => {
              setTouches((n) => n + 1);
              send('nudge', event.nativeEvent.actionName === 'increment' ? 1 : -1, false, 0);
            }}
          />
        </GestureDetector>
        {[false, true].map((pounds) => (
          <Pressable
            key={pounds ? 'lbs' : 'kg'}
            style={[styles.unitTouch, { left: MIDDLE + (pounds ? 0 : -SWITCH_W / 2) }]}
            accessibilityRole="button"
            accessibilityLabel={pounds ? 'lbs' : 'kg'}
            onPress={() => {
              setTouches((n) => n + 1);
              if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
              send('convert', 0, pounds, 0);
            }}
          />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    boxShadow: '0 14 44 0 rgba(122,111,176,0.22)',
  },
  card: {
    flex: 1,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  title: {
    position: 'absolute',
    left: 0,
    top: TITLE_Y - 12,
    width: WIDTH,
    height: 24,
    lineHeight: 24,
    textAlign: 'center',
    color: INK,
    fontSize: 17,
    fontWeight: '600',
  },
  message: {
    position: 'absolute',
    left: 0,
    width: WIDTH,
    height: 16,
    lineHeight: 16,
    textAlign: 'center',
    color: '#7a7f87',
    fontSize: 11,
  },
  switch: {
    position: 'absolute',
    left: MIDDLE - SWITCH_W / 2,
    top: SWITCH_Y - SWITCH_H / 2,
    width: SWITCH_W,
    height: SWITCH_H,
    borderRadius: SWITCH_H / 2,
    backgroundColor: '#ffffff',
  },
  knob: {
    position: 'absolute',
    left: 3,
    top: 3,
    width: SWITCH_W / 2 - 3,
    height: SWITCH_H - 6,
    borderRadius: (SWITCH_H - 6) / 2,
    backgroundColor: '#eef0f2',
  },
  unit: {
    position: 'absolute',
    top: 0,
    width: SWITCH_W / 2,
    height: SWITCH_H,
    lineHeight: SWITCH_H,
    textAlign: 'center',
    color: INK,
    fontSize: 11,
    fontWeight: '500',
  },
  unitTouch: {
    position: 'absolute',
    top: SWITCH_Y - 22,
    width: SWITCH_W / 2,
    height: 44,
  },
  box: {
    position: 'absolute',
    top: BOX_Y - BOX_H / 2,
    width: BOX_W,
    height: BOX_H,
    borderRadius: 11,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  outlined: {
    borderWidth: 1.5,
    borderColor: LEAF,
  },
  window: {
    width: BOX_W - 4,
    height: BOX_H - 12,
    overflow: 'hidden',
  },
  figure: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BOX_W - 4,
    height: BOX_H - 12,
    lineHeight: BOX_H - 12,
    textAlign: 'center',
    color: INK,
    fontSize: 18,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  chevron: {
    position: 'absolute',
    left: MIDDLE - BOX_GAP / 2 - 5.5,
    top: BOX_Y - 3.5,
    width: 7,
    height: 7,
    borderTopWidth: 1.5,
    borderRightWidth: 1.5,
    borderColor: '#9ba1a8',
    transform: [{ rotate: '45deg' }],
  },
  gap: {
    position: 'absolute',
    left: MIDDLE + BOX_W / 2 + 12,
    top: BOX_Y - 10,
    height: 20,
    lineHeight: 20,
    fontSize: 13,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  triangle: {
    position: 'absolute',
    left: MIDDLE - 7.5,
    top: TRIANGLE_Y - 6,
    width: 0,
    height: 0,
    borderLeftWidth: 7.5,
    borderRightWidth: 7.5,
    borderBottomWidth: 12,
    borderLeftColor: 'transparent',
    borderRightColor: 'transparent',
    borderBottomColor: LEAF,
  },
  mark: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: MARK_W,
    height: MARK_H,
    lineHeight: MARK_H,
    textAlign: 'center',
    color: '#6f757d',
    fontSize: 13,
  },
  dial: {
    position: 'absolute',
    left: 0,
    top: DIAL_Y - 24,
    width: WIDTH,
    height: HEIGHT - DIAL_Y + 24,
  },
});
