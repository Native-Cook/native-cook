import { BlurMask, Canvas, Circle, Group, Path, RadialGradient, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
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
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The time the pill starts on, in minutes after midnight. */
  initial?: number;
  /** When the moon comes over the horizon and when it goes under, in minutes after midnight. */
  moonrise?: number;
  moonset?: number;
  /** Where the "Now" chip sits. */
  now?: number;
  date?: string;
  phase?: string;
  place?: string;
  /** Scrubs through a night by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the time the pill settles on, in minutes after midnight. */
  onChange?: (minutes: number) => void;
};

const HEIGHT = 640;
const MARGIN = 16;
const MAX_WIDTH = 380;
const RADIUS = 36;
const LAST_MINUTE = 1439;
const TAU = Math.PI * 2;

// The pill's run down the right edge: midnight at `TRACK_TOP`, 23:59 at `TRACK_BOTTOM`.
const TRACK_TOP = 64;
const TRACK_BOTTOM = 508;
const PILL_W = 74;
const PILL_H = 30;
const PILL_INSET = 20;
// How far the pill comes away from the edge while it is held.
const PULL = 8;
// The strip along the right edge where a touch takes the pill.
const GRIP = 120;
// The pill settles on a multiple of this many minutes, and ticks every `NOTCH` minutes.
const SNAP = 5;
const NOTCH = 30;

// The model: its centre (left of the card's centre line), the ring's radius, the pyramid's and the moon's size.
const MODEL_X = -16;
const MODEL_Y = 268;
const RING = 96;
const SOLID = 24;
const MOON = 13;
const DOTS = 30;
// The moon's seas: centre and radii, in units of its radius.
const SEAS = [
  [-0.3, -0.2, 0.36, 0.28],
  [0.32, 0.22, 0.26, 0.22],
  [0.02, -0.56, 0.2, 0.13],
  [-0.34, 0.46, 0.18, 0.14],
];

const DATE_Y = 205;
const BAR_Y = 568;
// The lit share of the moon's face at midnight and at 23:59, in percent.
const SHARE_FROM = 82.2;
const SHARE_TO = 88.2;

// autoPlay (seconds): when the pill sets off, to which minute of the day (null: back to where it began), and how long it takes.
const SCRIPT: { at: number; to: number | null; time: number }[] = [
  { at: 0.8, to: 1320, time: 2.2 },
  { at: 3.9, to: 1430, time: 0.7 },
  { at: 5.1, to: 240, time: 1.6 },
  { at: 7.2, to: null, time: 1.3 },
];
const LOOP = 9;
// After a touch the loop waits this long, then takes `RETURN_TIME` to glide back.
const RESUME_AFTER = 2;
const RETURN_TIME = 0.8;

const FOLLOW_SPRING = { mass: 1, stiffness: 600, damping: 46, reduceMotion: ReduceMotion.Never };
const SNAP_SPRING = { mass: 1, stiffness: 300, damping: 24, reduceMotion: ReduceMotion.Never };
const GRAB_SPRING = { mass: 1, stiffness: 320, damping: 20, reduceMotion: ReduceMotion.Never };
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);

const PALE = '#8E93C9';
const INK = '#14163A';
// The pyramid's shaded and lit tones, by night and by day.
const DARK_NIGHT = [0x5e, 0x64, 0x8f];
const DARK_DAY = [0x8a, 0x90, 0xdc];
const LIGHT_NIGHT = [0xb9, 0xbe, 0xdd];
const LIGHT_DAY = [0xd9, 0xdc, 0xff];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number) {
  'worklet';
  const x = clamp01(t);
  return x * x * (3 - 2 * x);
}

/** How much of the sky is day at `hour`: dawn runs from 04:36 to 06:36 and dusk from 17:54 to 19:24. */
function daylight(hour: number) {
  'worklet';
  return smooth((hour - 4.6) / 2) * (1 - smooth((hour - 17.9) / 1.5));
}

/** How open the ring looks at `hour`: its height over its width, from 0.06 (edge-on, at 07:00) to 0.66. */
function openAt(hour: number) {
  'worklet';
  return 0.36 - 0.3 * Math.cos((TAU * (hour - 7)) / 24);
}

/**
 * Where a point of the model falls on the screen at `hour`, in the units it was given in, `y` down;
 * `depth` grows towards us. The model turns once round in a day, and the moon's path is edge-on at
 * 07:30 and 19:30, away from moonrise and moonset.
 */
function project(hour: number, x: number, up: number, z: number) {
  'worklet';
  const open = openAt(hour);
  const turn = (TAU * (hour - 1.5)) / 24;
  const depth = z * Math.cos(turn) - x * Math.sin(turn);
  return { x: x * Math.cos(turn) + z * Math.sin(turn), y: depth * open - up * Math.sqrt(1 - open * open), depth };
}

/** A point of the moon's path, 0 where it rises … 1 where it sets: half an ellipse standing on the ring. */
function onPath(hour: number, along: number) {
  'worklet';
  return project(hour, Math.cos(Math.PI * along), 0.78 * Math.sin(Math.PI * along), 0);
}

/** How brightly side `side` of the pyramid is lit, 0…1, or -1 when it faces away from us. */
function shade(hour: number, side: number) {
  'worklet';
  // A side's outward normal leans up by the base's inradius over the pyramid's height.
  const angle = (TAU * (side + 0.5)) / 3;
  const length = Math.sqrt(1.5 * 1.5 + 0.25);
  const normal = project(hour, (1.5 * Math.cos(angle)) / length, 0, (1.5 * Math.sin(angle)) / length);
  const up = 0.5 / length;
  const open = openAt(hour);
  if (open * up + Math.sqrt(1 - open * open) * normal.depth <= 0) return -1;
  // Light comes from the upper left, a little in front.
  return clamp01(0.5 + (0.5 * (-0.5 * normal.x + 0.62 * up + 0.6 * normal.depth)) / 0.997);
}

/** The pyramid's tone at `lit` (0 shaded … 1 lit) when the sky is `day` of the way to daylight. */
function tone(day: number, lit: number) {
  'worklet';
  const channel = (i: number) => {
    const dark = DARK_NIGHT[i] + (DARK_DAY[i] - DARK_NIGHT[i]) * day;
    const light = LIGHT_NIGHT[i] + (LIGHT_DAY[i] - LIGHT_NIGHT[i]) * day;
    return Math.round(dark + (light - dark) * lit);
  };
  return `rgb(${channel(0)},${channel(1)},${channel(2)})`;
}

function clock(minute: number) {
  return `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;
}

/** One side of the pyramid, laid softly over its outline. */
function Face({ side, time, x }: { side: number; time: SharedValue<number>; x: number }) {
  const path = useDerivedValue(() => {
    const hour = time.get() / 60;
    const face = Skia.Path.Make();
    if (shade(hour, side) < 0) return face;
    const top = project(hour, 0, 1.5, 0);
    const a = project(hour, Math.cos((TAU * side) / 3), 0, Math.sin((TAU * side) / 3));
    const b = project(hour, Math.cos((TAU * (side + 1)) / 3), 0, Math.sin((TAU * (side + 1)) / 3));
    face.moveTo(x + top.x * SOLID, MODEL_Y + top.y * SOLID);
    face.lineTo(x + a.x * SOLID, MODEL_Y + a.y * SOLID);
    face.lineTo(x + b.x * SOLID, MODEL_Y + b.y * SOLID);
    face.close();
    return face;
  });
  const color = useDerivedValue(() => {
    const hour = time.get() / 60;
    return tone(daylight(hour), Math.max(shade(hour, side), 0));
  });
  return (
    <Path path={path} color={color}>
      <BlurMask blur={2.5} style="normal" />
    </Path>
  );
}

/**
 * The horizon ring, the moon's dotted path over it and the pyramid in the middle: one solid model
 * that turns once a day and tips from edge-on in the morning to wide open at night.
 */
function Model({ time, width, moonrise, moonset }: { time: SharedValue<number>; width: number; moonrise: number; moonset: number }) {
  const x = width / 2 + MODEL_X;
  const glow = useDerivedValue(() => 0.05 + 0.07 * daylight(time.get() / 60));
  const ink = useDerivedValue(() => 0.22 + 0.1 * daylight(time.get() / 60));
  const farInk = useDerivedValue(() => ink.get() + 0.12);
  const nearInk = useDerivedValue(() => ink.get() + 0.3);

  const ring = useDerivedValue(() => {
    const open = openAt(time.get() / 60);
    return Skia.Path.Make().addOval(Skia.XYWHRect(x - RING, MODEL_Y - RING * open, RING * 2, RING * 2 * open));
  });
  // The path's far half is drawn before the pyramid and its near half after.
  const far = useDerivedValue(() => {
    const dots = Skia.Path.Make();
    for (let i = 0; i < DOTS; i++) {
      const spot = onPath(time.get() / 60, i / (DOTS - 1));
      if (spot.depth < 0) dots.addCircle(x + spot.x * RING, MODEL_Y + spot.y * RING, 1.3);
    }
    return dots;
  });
  const near = useDerivedValue(() => {
    const dots = Skia.Path.Make();
    for (let i = 0; i < DOTS; i++) {
      const spot = onPath(time.get() / 60, i / (DOTS - 1));
      if (spot.depth >= 0) dots.addCircle(x + spot.x * RING, MODEL_Y + spot.y * RING, 1.3);
    }
    return dots;
  });
  const ground = useDerivedValue(() => {
    const open = openAt(time.get() / 60);
    return Skia.Path.Make().addOval(Skia.XYWHRect(x - SOLID * 1.1, MODEL_Y - SOLID * 1.1 * open, SOLID * 2.2, SOLID * 2.2 * open));
  });
  // The pyramid: a rounded outline in a middle tone, with each face that looks at us laid softly over it.
  const outline = useDerivedValue(() => {
    const hour = time.get() / 60;
    const shape = Skia.Path.Make();
    const top = project(hour, 0, 1.5, 0);
    const base = [0, 1, 2].map((corner) => project(hour, Math.cos((TAU * corner) / 3), 0, Math.sin((TAU * corner) / 3)));
    shape.moveTo(x + base[0].x * SOLID, MODEL_Y + base[0].y * SOLID);
    shape.lineTo(x + base[1].x * SOLID, MODEL_Y + base[1].y * SOLID);
    shape.lineTo(x + base[2].x * SOLID, MODEL_Y + base[2].y * SOLID);
    shape.close();
    for (let side = 0; side < 3; side++) {
      if (shade(hour, side) < 0) continue;
      const a = base[side];
      const b = base[(side + 1) % 3];
      shape.moveTo(x + top.x * SOLID, MODEL_Y + top.y * SOLID);
      shape.lineTo(x + a.x * SOLID, MODEL_Y + a.y * SOLID);
      shape.lineTo(x + b.x * SOLID, MODEL_Y + b.y * SOLID);
      shape.close();
    }
    return shape;
  });
  const middle = useDerivedValue(() => tone(daylight(time.get() / 60), 0.5));

  // The moon rides the path while it is up, and fades where the path meets the ring.
  const up = (moonset - moonrise + 1440) % 1440;
  const along = useDerivedValue(() => {
    const since = (time.get() - moonrise + 1440) % 1440;
    return up > 0 && since <= up ? since / up : -1;
  });
  const moonPlace = useDerivedValue(() => {
    const spot = onPath(time.get() / 60, Math.max(along.get(), 0));
    return [{ translateX: x + spot.x * RING }, { translateY: MODEL_Y + spot.y * RING }, { scale: 1 + 0.1 * spot.depth }];
  });
  const moonSeen = useDerivedValue(() => (along.get() < 0 ? 0 : clamp01(along.get() / 0.06) * clamp01((1 - along.get()) / 0.06)));
  const halo = useDerivedValue(() => 0.3 * (1 - daylight(time.get() / 60)));
  const disc = useMemo(() => Skia.Path.Make().addCircle(0, 0, MOON), []);
  const seas = useMemo(() => {
    const path = Skia.Path.Make();
    for (const [cx, cy, rx, ry] of SEAS) path.addOval(Skia.XYWHRect((cx - rx) * MOON, (cy - ry) * MOON, rx * 2 * MOON, ry * 2 * MOON));
    return path;
  }, []);

  return (
    <>
      <Circle cx={x} cy={MODEL_Y} r={150} opacity={glow}>
        <RadialGradient c={vec(x, MODEL_Y)} r={150} colors={['#ffffff', '#ffffff00']} />
      </Circle>
      <Path path={ring} style="stroke" strokeWidth={1.2} color="#ffffff" opacity={ink} />
      <Path path={far} color="#ffffff" opacity={farInk} />
      <Path path={ground} color="rgba(0,0,0,0.16)">
        <BlurMask blur={4} style="normal" />
      </Path>
      <Path path={outline} color={middle} />
      <Path path={outline} color={middle} style="stroke" strokeWidth={8} strokeJoin="round" />
      <Face side={0} time={time} x={x} />
      <Face side={1} time={time} x={x} />
      <Face side={2} time={time} x={x} />
      <Path path={near} color="#ffffff" opacity={nearInk} />
      <Group transform={moonPlace} opacity={moonSeen}>
        <Circle cx={0} cy={0} r={MOON} color="#ffffff" opacity={halo}>
          <BlurMask blur={7} style="normal" />
        </Circle>
        <Circle cx={0} cy={0} r={MOON}>
          <RadialGradient c={vec(-0.35 * MOON, -0.4 * MOON)} r={1.6 * MOON} colors={['#F6F6F8', '#C8C9D2', '#8B8D9B']} positions={[0, 0.55, 1]} />
        </Circle>
        <Group clip={disc}>
          <Path path={seas} color="rgba(112,114,127,0.5)">
            <BlurMask blur={1} style="normal" />
          </Path>
        </Group>
      </Group>
    </>
  );
}

/** The small drawings that never move: the arrows at moonset and moonrise, the chevrons, the calendar. */
function Marks({ width, moonrise, moonset }: { width: number; moonrise: number; moonset: number }) {
  const lines = useMemo(() => {
    const path = Skia.Path.Make();
    const y = (minute: number) => TRACK_TOP + ((TRACK_BOTTOM - TRACK_TOP) * minute) / LAST_MINUTE;
    // A small moon with an arrow: down where it sets, up where it rises.
    for (const [minute, turn] of [
      [moonset, 1],
      [moonrise, -1],
    ]) {
      const at = y(minute);
      path.moveTo(width - 12, at - 3.5 * turn);
      path.lineTo(width - 12, at + 3.5 * turn);
      path.moveTo(width - 14.5, at + turn);
      path.lineTo(width - 12, at + 3.5 * turn);
      path.lineTo(width - 9.5, at + turn);
    }
    return path;
  }, [width, moonrise, moonset]);
  const chevrons = useMemo(() => {
    const path = Skia.Path.Make();
    for (const [at, tip] of [
      [TRACK_TOP - 34, -1.5],
      [TRACK_BOTTOM + 32, 1.5],
    ]) {
      path.moveTo(width - 26, at - tip);
      path.lineTo(width - 23, at + tip);
      path.lineTo(width - 20, at - tip);
    }
    return path;
  }, [width]);
  const calendar = useMemo(() => {
    const path = Skia.Path.Make();
    path.addRRect(Skia.RRectXY(Skia.XYWHRect(width - 45, BAR_Y + 23, 12, 11), 2.5, 2.5));
    path.moveTo(width - 45, BAR_Y + 26.5);
    path.lineTo(width - 33, BAR_Y + 26.5);
    return path;
  }, [width]);
  const y = (minute: number) => TRACK_TOP + ((TRACK_BOTTOM - TRACK_TOP) * minute) / LAST_MINUTE;
  // A gibbous disc: lit, with a sliver of shadow on its left.
  const tile = useMemo(() => Skia.Path.Make().addCircle(40, BAR_Y + 28, 8), []);

  return (
    <>
      <Circle cx={width - 21} cy={y(moonset)} r={5} color={PALE} />
      <Circle cx={width - 21} cy={y(moonrise)} r={5} color={PALE} />
      <Path path={lines} color={PALE} style="stroke" strokeWidth={1.2} strokeCap="round" strokeJoin="round" />
      <Path path={chevrons} color="rgba(255,255,255,0.6)" style="stroke" strokeWidth={1.1} strokeCap="round" strokeJoin="round" />
      <Path path={calendar} color="rgba(255,255,255,0.8)" style="stroke" strokeWidth={1.2} />
      <Circle cx={40} cy={BAR_Y + 28} r={8} color="#DADDF6" />
      <Group clip={tile}>
        <Circle cx={27.5} cy={BAR_Y + 28} r={8} color="rgba(20,22,58,0.6)" />
      </Group>
    </>
  );
}

/** The time on the pill. */
function Clock({ time }: { time: SharedValue<number> }) {
  const [minute, setMinute] = useState(() => Math.round(time.get()));
  useAnimatedReaction(
    () => Math.min(Math.max(Math.round(time.get()), 0), LAST_MINUTE),
    (now, before) => {
      if (now !== before) scheduleOnRN(setMinute, now);
    },
  );
  return (
    <Text style={styles.clock} numberOfLines={1}>
      {clock(minute)}
    </Text>
  );
}

/** The lit share of the moon's face, counting with the time. */
function Share({ time }: { time: SharedValue<number> }) {
  const [tenths, setTenths] = useState(() => Math.round((SHARE_FROM + ((SHARE_TO - SHARE_FROM) * time.get()) / LAST_MINUTE) * 10));
  useAnimatedReaction(
    () => Math.round((SHARE_FROM + ((SHARE_TO - SHARE_FROM) * time.get()) / LAST_MINUTE) * 10),
    (now, before) => {
      if (now !== before) scheduleOnRN(setTenths, now);
    },
  );
  return (
    <Text style={styles.share}>
      {' '}
      {Math.floor(tenths / 10)}.{tenths % 10}%
    </Text>
  );
}

/**
 * A time scrubber over a little model of the sky. A white pill with the time rides the card's right
 * edge, midnight at the top and 23:59 at the bottom, and everything follows it: the sky turns from
 * navy through violet to periwinkle, the horizon ring and the dotted path of the moon turn as one
 * solid model around a small pyramid, the moon climbs that path between moonrise and moonset, and
 * the bar at the bottom counts the lit share of its face.
 */
export function MoonOrbitTimeScrubber({
  initial = 720,
  moonrise = 980,
  moonset = 250,
  now = 570,
  date = 'Mar 10',
  phase = 'Waxing Gibbous',
  place = 'Reykjavik',
  autoPlay = false,
  onChange,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still;
  const start = Math.min(Math.max(initial, 0), LAST_MINUTE);
  const [room, setRoom] = useState(useWindowDimensions().width);
  const width = Math.min(room - MARGIN * 2, MAX_WIDTH);

  // The time, in minutes after midnight, continuous while the pill moves.
  const time = useSharedValue(start);
  // 1 while the pill is held: it grows and comes away from the edge.
  const grab = useSharedValue(0);
  const dragged = useSharedValue(start);
  const held = useSharedValue(false);
  // The pill moves under a finger, not under the script: only then it ticks.
  const byHand = useSharedValue(false);
  // Counts touches: each one stops the script and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const touch = () => setTouches((n) => n + 1);
  const release = (target: number) => {
    onChange?.(target);
    touch();
  };

  // A tick for each half hour the pill passes under a finger.
  useAnimatedReaction(
    () => Math.floor(time.get() / NOTCH),
    (notch, before) => {
      if (before !== null && notch !== before && byHand.get()) scheduleOnRN(tick);
    },
  );

  const hold = (down: boolean) => {
    grab.set(still ? (down ? 1 : 0) : withSpring(down ? 1 : 0, GRAB_SPRING));
  };
  /** Glides to `target` in `seconds`. */
  const glide = (target: number, seconds: number) => {
    time.set(withTiming(target, { duration: still ? 0 : seconds * 1000, easing: EASE_IN_OUT, reduceMotion: ReduceMotion.Never }));
  };
  const arrived = (target: number) => onChange?.(target);

  // The timers below call whatever these are at the time they fire.
  const actions = useRef({ hold, glide, arrived });
  useEffect(() => {
    actions.current = { hold, glide, arrived };
  });

  // The script a finger would follow: down into the night, to the end of the day, up to before dawn and back to noon.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      // Each move is two moments, its start and its end, both timed from the script's start.
      const step = SCRIPT[Math.floor(n / 2) % SCRIPT.length];
      const lifts = n % 2 === 1;
      const due = begin + (Math.floor(n / 2 / SCRIPT.length) * LOOP + step.at + (lifts ? step.time : 0)) * 1000;
      timer = setTimeout(() => {
        const target = step.to ?? start;
        actions.current.hold(!lifts);
        if (lifts) actions.current.arrived(target);
        else actions.current.glide(target, step.time);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, glide back to the first time, and start over from rest.
      timer = setTimeout(() => {
        if (held.get()) return;
        byHand.set(false);
        actions.current.glide(start, RETURN_TIME);
        run(Date.now() + RETURN_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, start, held, byHand]);

  // A touch on the right-hand strip takes the pill: it comes to the finger and follows it up and down.
  const right = (room + width) / 2;
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      if (event.x < right - GRIP || event.x > right + MARGIN) return;
      held.set(true);
      byHand.set(true);
      dragged.set(clamp01((event.y - TRACK_TOP) / (TRACK_BOTTOM - TRACK_TOP)) * LAST_MINUTE);
      grab.set(still ? 1 : withSpring(1, GRAB_SPRING));
      time.set(still ? dragged.get() : withSpring(dragged.get(), FOLLOW_SPRING));
      scheduleOnRN(touch);
    })
    .onUpdate((event) => {
      if (!held.get()) return;
      dragged.set(clamp01((event.y - TRACK_TOP) / (TRACK_BOTTOM - TRACK_TOP)) * LAST_MINUTE);
      time.set(still ? dragged.get() : withSpring(dragged.get(), FOLLOW_SPRING));
    })
    .onFinalize(() => {
      if (!held.get()) return;
      held.set(false);
      // Let go, the pill settles on the nearest five minutes.
      const target = Math.min(Math.max(Math.round(dragged.get() / SNAP) * SNAP, 0), LAST_MINUTE);
      grab.set(still ? 0 : withSpring(0, GRAB_SPRING));
      time.set(still ? target : withSpring(target, SNAP_SPRING));
      scheduleOnRN(release, target);
    });

  // The sky: night, with day laid over it, and a wash of violet where one turns into the other.
  const daySky = useAnimatedStyle(() => ({ opacity: daylight(time.get() / 60) }));
  const duskSky = useAnimatedStyle(() => {
    const day = daylight(time.get() / 60);
    return { opacity: 0.6 * 4 * day * (1 - day) };
  });
  const nub = useAnimatedStyle(() => ({
    transform: [{ translateY: TRACK_TOP + ((TRACK_BOTTOM - TRACK_TOP) * time.get()) / LAST_MINUTE }],
  }));
  const pill = useAnimatedStyle(() => ({
    transform: [
      { translateX: -PULL * grab.get() },
      { translateY: TRACK_TOP + ((TRACK_BOTTOM - TRACK_TOP) * time.get()) / LAST_MINUTE },
      { scale: 1 + 0.06 * grab.get() },
    ],
  }));
  const y = (minute: number) => TRACK_TOP + ((TRACK_BOTTOM - TRACK_TOP) * minute) / LAST_MINUTE;

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.root}
        onLayout={(event) => setRoom(event.nativeEvent.layout.width)}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Time"
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          const next = Math.round(time.get() / NOTCH + (event.nativeEvent.actionName === 'increment' ? 1 : -1)) * NOTCH;
          const target = Math.min(Math.max(next, 0), LAST_MINUTE);
          time.set(still ? target : withSpring(target, SNAP_SPRING));
          release(target);
        }}
      >
        <View pointerEvents="none" style={[styles.card, { width }]}>
          <LinearGradient style={StyleSheet.absoluteFill} colors={['#0C1033', '#0A0D28']} />
          <Animated.View style={[StyleSheet.absoluteFill, daySky]}>
            <LinearGradient style={StyleSheet.absoluteFill} colors={['#3F45C4', '#6A55D6']} />
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, duskSky]}>
            <LinearGradient style={StyleSheet.absoluteFill} colors={['#5A49C9', '#7C55D2']} />
          </Animated.View>

          <View style={styles.bar} />
          <View style={styles.tile} />
          <View style={styles.button} />
          <Canvas style={StyleSheet.absoluteFill}>
            <Model time={time} width={width} moonrise={moonrise} moonset={moonset} />
            <Marks width={width} moonrise={moonrise} moonset={moonset} />
          </Canvas>

          {/* What is fixed along the two edges: the ends of the day, "Now", and the date. */}
          <View style={[styles.end, { top: TRACK_TOP - 34 - 9 }]}>
            <Text style={styles.endText}>00:00</Text>
          </View>
          <View style={[styles.end, { top: TRACK_BOTTOM + 32 - 9 }]}>
            <Text style={styles.endText}>23:59</Text>
          </View>
          <View style={[styles.now, { top: y(now) - 9.5 }]}>
            <Text style={styles.nowText}>Now</Text>
          </View>
          <View style={[styles.nub, styles.dateNub]} />
          <View style={styles.date}>
            <Text style={styles.dateText} numberOfLines={1}>
              {date}
            </Text>
          </View>
          <View style={[styles.stop, { top: 120 - 2.5 }]} />
          <View style={[styles.stop, styles.stopFilled, { top: 300 - 2.5 }]} />
          <View style={[styles.stop, { top: 470 - 2.5 }]} />

          {/* The bar at the bottom: the phase, the lit share of the moon's face, and the place. */}
          <Text style={styles.phase} numberOfLines={1}>
            {phase}
            <Share time={time} />
          </Text>
          <Text style={styles.place} numberOfLines={1}>
            {place}
          </Text>

          {/* The pill, and the nub that ties it to the edge. */}
          <Animated.View style={[styles.nub, styles.pillNub, nub]} />
          <Animated.View style={[styles.pill, pill]}>
            <Clock time={time} />
          </Animated.View>
          <View style={[StyleSheet.absoluteFill, styles.rim]} />
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    alignSelf: 'stretch',
    height: HEIGHT,
    alignItems: 'center',
  },
  card: {
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  rim: {
    borderRadius: RADIUS,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.07)',
  },
  end: {
    position: 'absolute',
    right: 12,
    width: 52,
    height: 18,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.28)',
    justifyContent: 'center',
  },
  endText: {
    width: 42,
    textAlign: 'center',
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    color: 'rgba(255,255,255,0.6)',
  },
  now: {
    position: 'absolute',
    right: 14,
    width: 36,
    height: 19,
    borderRadius: 9.5,
    backgroundColor: 'rgba(255,255,255,0.16)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  nowText: {
    fontSize: 9.5,
    lineHeight: 12,
    fontWeight: '600',
    color: 'rgba(255,255,255,0.85)',
  },
  nub: {
    position: 'absolute',
    width: 6,
    height: 16,
    borderRadius: 3,
    backgroundColor: '#ffffff',
  },
  dateNub: {
    left: -3,
    top: DATE_Y - 8,
  },
  pillNub: {
    right: -3,
    top: -8,
  },
  date: {
    position: 'absolute',
    left: 14,
    top: DATE_Y - 13,
    width: 60,
    height: 26,
    borderRadius: 13,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dateText: {
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '700',
    color: INK,
  },
  stop: {
    position: 'absolute',
    left: 9.5,
    width: 5,
    height: 5,
    borderRadius: 2.5,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.5)',
  },
  stopFilled: {
    borderWidth: 0,
    backgroundColor: 'rgba(255,255,255,0.7)',
  },
  bar: {
    position: 'absolute',
    left: 12,
    right: 12,
    top: BAR_Y,
    height: 56,
    borderRadius: 22,
    backgroundColor: 'rgba(0,0,0,0.24)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.07)',
  },
  tile: {
    position: 'absolute',
    left: 22,
    top: BAR_Y + 10,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  button: {
    position: 'absolute',
    right: 24,
    top: BAR_Y + 13,
    width: 30,
    height: 30,
    borderRadius: 10,
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  phase: {
    position: 'absolute',
    left: 68,
    right: 64,
    top: BAR_Y + 28 - 8 - 9,
    height: 18,
    lineHeight: 18,
    fontSize: 14,
    fontWeight: '700',
    color: '#ffffff',
  },
  share: {
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    color: PALE,
  },
  place: {
    position: 'absolute',
    left: 68,
    right: 64,
    top: BAR_Y + 28 + 10 - 7,
    height: 14,
    lineHeight: 14,
    fontSize: 11,
    color: 'rgba(142,147,201,0.85)',
  },
  pill: {
    position: 'absolute',
    right: PILL_INSET,
    top: -PILL_H / 2,
    width: PILL_W,
    height: PILL_H,
    borderRadius: PILL_H / 2,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 3 12 0 rgba(0,0,0,0.28)',
  },
  clock: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    color: INK,
  },
});
