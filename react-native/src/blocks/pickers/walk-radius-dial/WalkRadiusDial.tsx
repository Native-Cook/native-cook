import { Canvas, Circle, Group, Path, RadialGradient, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The fewest and the most minutes; the circle is smallest and largest at these. */
  min?: number;
  max?: number;
  /** The minutes shown at first. */
  initial?: number;
  question?: string;
  unit?: string;
  nextLabel?: string;
  /** Pulls the handle out and pushes it back by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with each minute the handle passes, and with the one it is released on. */
  onChange?: (minutes: number) => void;
  /** Called with the chosen minutes when the button is tapped. */
  onNext?: (minutes: number) => void;
};

const WIDTH = 340;
const HEIGHT = 476;
const TITLE_HEIGHT = 68;
const BUTTON_HEIGHT = 52;

// The dial's radius and the height of its centre.
const DIAL = 150;
const DIAL_Y = 246;
// The circle's radius at the fewest and at the most minutes.
const MIN_RADIUS = 72;
const MAX_RADIUS = 145.5;
// How far past either end a finger can pull the handle.
const GIVE = 4.5;
const BADGE = 66;
const HANDLE_WIDTH = 42;
const HANDLE_HEIGHT = 30;
const HANDLE_TOUCH = 64;
const HALO = 36;
const BLUE = '#2e8fd0';

// The number rolls over this share of a minute to each side of the half-way point, by this many points.
const ROLL_WINDOW = 0.25;
const ROLL_TRAVEL = 18;

const PRESS_TIME = 0.15;
const RIPPLE_TIME = 0.45;
// The fastest release the spring is given, in points a second.
const MAX_FLING = 600;
// The script's drag ends at this multiple of its average speed (the slope at the end of `DRAG_CURVE`).
const DRAG_END_SPEED = 1.25;

// autoPlay (seconds): the handle is held at `GRAB_AT`, and dragged for `DRAG_TIME` 150 ms later.
const GRAB_AT = [0.9, 3.15, 5.5];
const DRAG_TIME = [1.2, 1.3, 0.5];
const LOOP = 8;
// After a touch the loop waits this long, returns to the first value, and starts over once it has settled.
const RESUME_AFTER = 2;
const SETTLE = 0.9;

const RELEASE_SPRING = { mass: 1, stiffness: 180, damping: 12, reduceMotion: ReduceMotion.Never };
const DRAG_CURVE = Easing.bezier(0.45, 0, 0.8, 0.75);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// The invented neighbourhood, in map units with the dial's centre at the origin: at zoom 1 the dial
// shows 150 units to each side. It is drawn tilted, as a grid of avenues and streets with one
// boulevard across it, a few parks, a route and some pins.
const TILT = (-24 * Math.PI) / 180;
const LAND = '#f1efe9';
const PARK = '#a8d08d';
const PLAZA = '#f3e7b8';
const ROUTE_COLOR = '#f29e4c';
const TRANSIT = '#3b82f6';
const FOOD = '#f08a3c';
const VEIL = 'rgba(174,182,194,0.65)';

// Streets run across every 16 units (every fourth is a wide one), avenues up and down every 30.
const STREET_ROWS = Array.from({ length: 22 }, (_, k) => ({ y: -168 + 16 * k, wide: k % 4 === 1 }));
const NARROW = STREET_ROWS.filter((row) => !row.wide)
  .map((row) => `M -190 ${row.y} L 190 ${row.y}`)
  .join(' ');
const WIDE = STREET_ROWS.filter((row) => row.wide)
  .map((row) => `M -190 ${row.y} L 190 ${row.y}`)
  .join(' ');
const AVENUES = Array.from({ length: 12 }, (_, k) => `M ${-165 + 30 * k} -190 L ${-165 + 30 * k} 190`).join(' ');
const BOULEVARD = 'M -190 62 L 190 -98';
const ROUTE = 'M -190 72 L -45 72 L -45 24 L 15 24 L 15 -8 L 75 -8 L 75 -72 L 190 -72';

// Blocks turned into parks, and some into plazas: x, y, width, height.
const PARKS = [
  [-42.5, 10, 25, 28],
  [77.5, -38, 25, 28],
  [-102.5, -70, 25, 28],
  [17.5, 58, 55, 28],
  [-162.5, 90, 25, 28],
  [107.5, -134, 25, 28],
];
const PLAZAS = [
  [17.5, -86, 25, 12],
  [-72.5, 74, 25, 12],
  [107.5, 26, 25, 12],
];
const TRANSIT_PINS = [
  [-15, -24],
  [45, 40],
  [-75, 56],
  [75, -56],
  [15, -88],
  [-45, -72],
  [135, 8],
  [-135, -8],
];
const FOOD_PINS = [
  [-30, -16],
  [30, 16],
  [60, 48],
  [-60, -48],
  [90, 16],
  [0, 80],
  [30, -48],
  [-120, 32],
  [120, -88],
  [-90, 96],
];

const DIAL_CLIP = { rect: { x: 0, y: 0, width: DIAL * 2, height: DIAL * 2 }, rx: DIAL, ry: DIAL };
const CHEVRONS = [-1, 1]
  .map((side) => {
    const x = HANDLE_WIDTH / 2;
    const y = HANDLE_HEIGHT / 2;
    return `M ${x + side * 5} ${y - 4.5} L ${x + side * 9.5} ${y} L ${x + side * 5} ${y + 4.5}`;
  })
  .join(' ');

function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function radiusFor(minutes: number, low: number, high: number) {
  'worklet';
  return mix(MIN_RADIUS, MAX_RADIUS, (minutes - low) / Math.max(1, high - low));
}

function minutesAt(radius: number, low: number, high: number) {
  'worklet';
  return low + ((radius - MIN_RADIUS) / (MAX_RADIUS - MIN_RADIUS)) * (high - low);
}

function snapped(radius: number, low: number, high: number) {
  'worklet';
  return clamp(Math.round(minutesAt(radius, low, high)), low, high);
}

/**
 * The map's scale. The ground covered grows with the minutes, so the scale is the circle's radius
 * over the minutes: 1 at the most minutes, where the whole map shows.
 */
function zoomAt(radius: number, low: number, high: number) {
  'worklet';
  const minutes = Math.max(minutesAt(radius, low, high), Math.max(1, low * 0.75));
  return radius / minutes / (MAX_RADIUS / Math.max(1, high));
}

/** Past either end the handle follows the finger at a third of its speed, for a few points. */
function rubber(radius: number) {
  'worklet';
  if (radius > MAX_RADIUS) return MAX_RADIUS + Math.min(GIVE, (radius - MAX_RADIUS) / 3);
  if (radius < MIN_RADIUS) return MIN_RADIUS - Math.min(GIVE, (MIN_RADIUS - radius) / 3);
  return radius;
}

/** Up in 80 ms, a 40 ms hold, back in 180 ms: a tap. */
function pulse() {
  const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
  return withSequence(withTiming(1, { duration: 80, ...linear }), withDelay(40, withTiming(0, { duration: 180, ...linear })));
}

/** The map, drawn about the origin. */
function MapArt() {
  return (
    <>
      <Rect x={-260} y={-260} width={520} height={520} color={LAND} />
      <Path path={NARROW} style="stroke" strokeWidth={1.8} color="#ffffff" />
      <Path path={WIDE} style="stroke" strokeWidth={3.4} color="#ffffff" />
      <Path path={AVENUES} style="stroke" strokeWidth={3} color="#ffffff" />
      {PARKS.map(([x, y, width, height]) => (
        <RoundedRect key={`${x} ${y}`} x={x} y={y} width={width} height={height} r={2.5} color={PARK} />
      ))}
      {PLAZAS.map(([x, y, width, height]) => (
        <RoundedRect key={`${x} ${y}`} x={x} y={y} width={width} height={height} r={2.5} color={PLAZA} />
      ))}
      <Path path={BOULEVARD} style="stroke" strokeWidth={5} color="#ffffff" />
      <Path path={ROUTE} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={ROUTE_COLOR} />
      {TRANSIT_PINS.map(([x, y]) => (
        <Group key={`${x} ${y}`}>
          <Circle cx={x} cy={y} r={2.6} color={TRANSIT} />
          <Circle cx={x} cy={y} r={1} color="#ffffff" />
        </Group>
      ))}
      {FOOD_PINS.map(([x, y]) => (
        <Circle key={`${x} ${y}`} cx={x} cy={y} r={2.1} color={FOOD} />
      ))}
    </>
  );
}

type DigitProps = {
  /** The minutes as the circle's radius gives them, between two whole minutes while it moves. */
  value: SharedValue<number>;
  /** 0 this slot shows the even minutes, 1 the odd ones. */
  parity: number;
};

/**
 * One slot of the number's drum. It shows the whole minute of its parity nearest to `value`: at that
 * minute it is in place, and as `value` leaves it slides up or down and fades, while the other slot
 * arrives. Its text only changes while it is out of sight.
 */
function Digit({ value, parity }: DigitProps) {
  const [shown, setShown] = useState(() => 2 * Math.round((value.get() - parity) / 2) + parity);

  useAnimatedReaction(
    () => 2 * Math.round((value.get() - parity) / 2) + parity,
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );

  const style = useAnimatedStyle(() => {
    const away = value.get() - (2 * Math.round((value.get() - parity) / 2) + parity);
    const turn = clamp((Math.abs(away) - (0.5 - ROLL_WINDOW)) / (2 * ROLL_WINDOW), 0, 1);
    return { opacity: 1 - turn, transform: [{ translateY: -Math.sign(away) * turn * ROLL_TRAVEL }] };
  });

  return <Animated.Text style={[styles.number, style]}>{shown}</Animated.Text>;
}

/**
 * A walking-distance question answered on a round map. A circle of full colour sits on a washed-out
 * street map; a handle on its right edge is pulled outward to give more minutes and pushed inward
 * to give fewer. The map zooms with the circle, so the circle always covers the ground the minutes
 * reach, and the number in the middle rolls from minute to minute. Tapping the map moves the edge
 * there. An invented street grid is drawn in code: there are no map tiles.
 */
export function WalkRadiusDial({
  min = 4,
  max = 18,
  initial = 6,
  question = 'How far are you willing\nto walk?',
  unit = 'MIN',
  nextLabel = 'Next',
  autoPlay = false,
  onChange,
  onNext,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still;
  const first = clamp(initial, min, max);

  // The circle's radius in points. The minutes, the number and the map's zoom all follow it.
  const radius = useSharedValue(radiusFor(first, min, max));
  // 0 the handle at rest … 1 held.
  const press = useSharedValue(0);
  // The ring a released handle sends out, 0…1.
  const ripple = useSharedValue(1);
  const nextPress = useSharedValue(0);
  // The radius when the drag began.
  const grabbed = useSharedValue(0);
  const dragging = useSharedValue(false);

  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  // `carry`: the speed the script's drag ends with, handed to the spring on release.
  const state = useRef({ minutes: first, carry: 0 });

  const value = useDerivedValue(() => clamp(minutesAt(radius.get(), min, max), min, max));
  const reach = useDerivedValue(() => {
    const r = Math.max(0, radius.get());
    return { rect: { x: DIAL - r, y: DIAL - r, width: r * 2, height: r * 2 }, rx: r, ry: r };
  });
  const map = useDerivedValue(() => [
    { translateX: DIAL },
    { translateY: DIAL },
    { scale: zoomAt(radius.get(), min, max) },
    { rotate: TILT },
  ]);

  const touch = () => {
    setTouches((count) => count + 1);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const pass = (minutes: number, tick: boolean) => {
    if (minutes === state.current.minutes) return;
    state.current.minutes = minutes;
    if (tick && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onChange?.(minutes);
  };

  // Every whole minute the circle's edge passes, whoever moves it.
  useAnimatedReaction(
    () => snapped(radius.get(), min, max),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(pass, now, dragging.get());
    },
  );

  /** The handle is held (blue, with a halo) or let go of; letting go sends out a ring. */
  const hold = (held: boolean) => {
    press.set(withTiming(held ? 1 : 0, { duration: still ? 0 : PRESS_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
    if (held || still) return;
    ripple.set(0);
    ripple.set(withTiming(1, { duration: RIPPLE_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  };

  /** Moves the circle's edge to `minutes` on the spring, starting at `speed` points a second. */
  const settle = (minutes: number, speed = 0) => {
    const target = radiusFor(minutes, min, max);
    if (still) radius.set(target);
    else radius.set(withSpring(target, { ...RELEASE_SPRING, velocity: speed }));
  };

  /** The script's drag: the handle travels to `minutes` in `time` seconds and is still moving when it gets there. */
  const drag = (minutes: number, time: number) => {
    const target = radiusFor(minutes, min, max);
    state.current.carry = (DRAG_END_SPEED * (target - radius.get())) / time;
    radius.set(withTiming(target, { duration: time * 1000, easing: DRAG_CURVE, reduceMotion: ReduceMotion.Never }));
  };

  const letGo = (minutes: number) => {
    settle(minutes, state.current.carry);
    hold(false);
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ hold, settle, drag, letGo });
  useEffect(() => {
    steps.current = { hold, settle, drag, letGo };
  });

  // The script a finger would follow: out to the most minutes, in to the fewest, back to the start.
  useEffect(() => {
    if (!auto) return;
    const stops = [max, min, first];
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = Math.floor(n / 3) % 3;
      const part = n % 3;
      // Every step is timed from the script's start, so the loop keeps its period.
      const at = GRAB_AT[step] + (part > 0 ? PRESS_TIME : 0) + (part > 1 ? DRAG_TIME[step] : 0);
      const due = begin + (Math.floor(n / 9) * LOOP + at) * 1000;
      timer = setTimeout(() => {
        if (part === 0) steps.current.hold(true);
        // The drag ends on time even when it starts late.
        else if (part === 1) steps.current.drag(stops[step], Math.max(0.2, DRAG_TIME[step] - (Date.now() - due) / 1000));
        else steps.current.letGo(stops[step]);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the first value, and start over once it has settled.
      timer = setTimeout(() => {
        steps.current.settle(first);
        run(Date.now() + SETTLE * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, min, max, first]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      grabbed.set(radius.get());
      dragging.set(true);
      press.set(withTiming(1, { duration: still ? 0 : PRESS_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
      scheduleOnRN(touch);
    })
    .onUpdate((event) => {
      radius.set(rubber(grabbed.get() + event.translationX));
    })
    .onFinalize((event) => {
      dragging.set(false);
      // The circle springs to the nearest minute, carrying the finger's speed.
      const target = radiusFor(snapped(radius.get(), min, max), min, max);
      if (still) radius.set(target);
      else radius.set(withSpring(target, { ...RELEASE_SPRING, velocity: clamp(event.velocityX, -MAX_FLING, MAX_FLING) }));
      scheduleOnRN(hold, false);
      scheduleOnRN(touch);
    });

  const tap = Gesture.Tap().onEnd((event, success) => {
    if (!success) return;
    const distance = Math.hypot(event.x - DIAL, event.y - DIAL);
    if (distance <= BADGE / 2 || distance > DIAL) return;
    scheduleOnRN(touch);
    scheduleOnRN(settle, snapped(distance, min, max));
  });

  const numberStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -7 - 2 * press.get() }, { scale: 1 + 0.3 * press.get() }],
  }));
  const edgeStyle = useAnimatedStyle(() => ({ transform: [{ translateX: radius.get() }] }));
  const haloStyle = useAnimatedStyle(() => ({ opacity: press.get() }));
  const rippleStyle = useAnimatedStyle(() => ({
    opacity: 0.5 * (1 - ripple.get()),
    transform: [{ scale: mix(18, HALO, ripple.get()) / HALO }],
  }));
  const capsuleStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(press.get(), [0, 1], ['#1f2a2e', BLUE]),
    transform: [{ scale: 1 + 0.06 * press.get() }],
  }));
  const nextStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * nextPress.get() }] }));

  return (
    <View style={styles.root}>
      <Text style={styles.question} numberOfLines={2}>
        {question}
      </Text>

      <GestureDetector gesture={tap}>
        <View style={styles.dial}>
          <Canvas style={StyleSheet.absoluteFill}>
            {/* The whole map under a blue-grey veil, then the same map in full colour inside the circle. */}
            <Group clip={DIAL_CLIP}>
              <Group transform={map}>
                <MapArt />
              </Group>
              <Circle cx={DIAL} cy={DIAL} r={DIAL} color={VEIL} />
            </Group>
            <Group clip={reach}>
              <Group transform={map}>
                <MapArt />
              </Group>
            </Group>
            <Circle cx={DIAL} cy={DIAL} r={radius} style="stroke" strokeWidth={3} color="#ffffff" />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={styles.badge} pointerEvents="none">
        <Animated.View style={[styles.drum, numberStyle]}>
          <Digit value={value} parity={0} />
          <Digit value={value} parity={1} />
        </Animated.View>
        <Text style={styles.unit} numberOfLines={1}>
          {unit}
        </Text>
      </View>

      <Animated.View style={[styles.edge, edgeStyle]} pointerEvents="box-none">
        <Animated.View style={[styles.halo, haloStyle]} pointerEvents="none">
          <Canvas style={StyleSheet.absoluteFill}>
            <Circle cx={HALO} cy={HALO} r={HALO}>
              <RadialGradient c={vec(HALO, HALO)} r={HALO} colors={['rgba(46,143,208,0.45)', 'rgba(46,143,208,0)']} positions={[1 / 3, 1]} />
            </Circle>
          </Canvas>
        </Animated.View>
        <Animated.View style={[styles.ripple, rippleStyle]} pointerEvents="none" />
        <GestureDetector gesture={pan}>
          <View style={styles.touch} accessibilityRole="adjustable" accessibilityLabel="Walking time">
            <Animated.View style={[styles.capsule, capsuleStyle]}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Path path={CHEVRONS} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color="#3ec6c0" />
                <Group opacity={press}>
                  <Path path={CHEVRONS} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color="#ffffff" />
                </Group>
              </Canvas>
            </Animated.View>
          </View>
        </GestureDetector>
      </Animated.View>

      <Animated.View style={[styles.next, nextStyle]}>
        <Pressable
          style={styles.nextFace}
          accessibilityRole="button"
          onPress={() => {
            touch();
            nextPress.set(pulse());
            onNext?.(state.current.minutes);
          }}
        >
          <Text style={styles.nextLabel} numberOfLines={1}>
            {nextLabel}
          </Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  question: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: TITLE_HEIGHT,
    paddingTop: 2,
    color: '#1e1e1e',
    fontSize: 24,
    lineHeight: 32,
    textAlign: 'center',
  },
  dial: {
    position: 'absolute',
    left: WIDTH / 2 - DIAL,
    top: DIAL_Y - DIAL,
    width: DIAL * 2,
    height: DIAL * 2,
    borderRadius: DIAL,
  },
  badge: {
    position: 'absolute',
    left: WIDTH / 2 - BADGE / 2,
    top: DIAL_Y - BADGE / 2,
    width: BADGE,
    height: BADGE,
    borderRadius: BADGE / 2,
    backgroundColor: '#1c1c1c',
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  drum: {
    position: 'absolute',
    left: 0,
    top: BADGE / 2 - 13,
    width: BADGE,
    height: 26,
  },
  number: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BADGE,
    height: 26,
    lineHeight: 26,
    color: '#ffffff',
    fontSize: 20,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
  unit: {
    position: 'absolute',
    left: 0,
    top: BADGE / 2 + 13 - 7,
    width: BADGE,
    height: 14,
    lineHeight: 14,
    color: 'rgba(255,255,255,0.9)',
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1.6,
    textAlign: 'center',
  },
  // The handle's touch target, centred on the dial; it is moved out to the circle's edge. It is larger
  // than the capsule, so the handle is easy to catch.
  edge: {
    position: 'absolute',
    left: WIDTH / 2 - HANDLE_TOUCH / 2,
    top: DIAL_Y - HANDLE_TOUCH / 2,
    width: HANDLE_TOUCH,
    height: HANDLE_TOUCH,
  },
  halo: {
    position: 'absolute',
    left: HANDLE_TOUCH / 2 - HALO,
    top: HANDLE_TOUCH / 2 - HALO,
    width: HALO * 2,
    height: HALO * 2,
  },
  ripple: {
    position: 'absolute',
    left: HANDLE_TOUCH / 2 - HALO,
    top: HANDLE_TOUCH / 2 - HALO,
    width: HALO * 2,
    height: HALO * 2,
    borderRadius: HALO,
    borderWidth: 2,
    borderColor: BLUE,
  },
  touch: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: HANDLE_TOUCH,
    height: HANDLE_TOUCH,
    alignItems: 'center',
    justifyContent: 'center',
  },
  capsule: {
    width: HANDLE_WIDTH,
    height: HANDLE_HEIGHT,
    borderRadius: HANDLE_HEIGHT / 2,
  },
  next: {
    position: 'absolute',
    left: 0,
    top: HEIGHT - BUTTON_HEIGHT,
    width: WIDTH,
    height: BUTTON_HEIGHT,
  },
  nextFace: {
    flex: 1,
    borderRadius: 6,
    backgroundColor: '#1c1c1c',
    alignItems: 'center',
    justifyContent: 'center',
  },
  nextLabel: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
});
