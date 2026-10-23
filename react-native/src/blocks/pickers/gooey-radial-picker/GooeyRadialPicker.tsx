import { Canvas, Circle, Group, Oval, Paint, Path, Shadow, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/** A mood the picker can offer, each a small drawn face. */
export type Mood = 'happy' | 'joyful' | 'calm' | 'playful' | 'surprised' | 'unsure' | 'sad' | 'sleepy';

type Props = {
  /** The options, clockwise from 12 o'clock. */
  options?: Mood[];
  /** The option chosen at first; `autoPlay` comes back to it at the end of every loop. */
  selection?: number;
  /** Moves the marker round the ring by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the option the marker reached. */
  onChange?: (index: number) => void;
};

// The square the picker is laid out in, and the room for the shadow round it.
const SIZE = 344;
const BLEED = 44;
// Radius of the disc in the middle, of an option's circle, and of the ring the options sit on.
const DISC = 80;
const DOT = 35;
const RING = 123;
// A joined circle moves this far towards the disc.
const REACH = 4;
// Radius of a face in the middle and on an option.
const LARGE = 40;
const SMALL = 14.5;
// The marker: its radius and the width of its line.
const MARKER = 13;
const MARKER_LINE = 7;
const DEAD_ZONE = 28;

// The simulation's step, in seconds.
const TICK = 1 / 240;
// A frame that took longer than this is counted as this long.
const LONGEST_FRAME = 0.25;
const JOIN = { stiffness: 260, damping: 13 };
const PART = { stiffness: 300, damping: 26 };
const POP = { stiffness: 300, damping: 19 };
const SHRINK = { stiffness: 900, damping: 60 };
const WOBBLE = { stiffness: 400, damping: 16 };
// The speed the disc's radius gets when the marker reaches another option, in points a second.
const KNOCK = 60;
// The new face pops out once the marker has stayed on an option this long.
const POP_AFTER = 0.22;
const LEAD_TIME = 0.03;
const LAG_TIME = 0.08;

// autoPlay (seconds). Each move: when it starts, how much of a turn it covers, how long it takes.
const LOOP = 8;
const SWEEP_TIME = 0.8;
const STEP_TIME = 0.6;
const SCRIPT = [
  { at: 1, turn: 0.5, time: SWEEP_TIME },
  { at: 2.7, turn: 0.125, time: STEP_TIME },
  { at: 4.1, turn: 0.125, time: STEP_TIME },
  { at: 5.5, turn: 0.25, time: SWEEP_TIME },
];
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const MOODS: Mood[] = ['happy', 'joyful', 'calm', 'playful', 'surprised', 'unsure', 'sad', 'sleepy'];

/** Everything that moves, stepped in small fixed steps on every frame. */
type Sim = {
  /** Where the marker points, as an angle round the ring (0 is 3 o'clock, growing clockwise). */
  theta: number;
  // The marker's journey when it travels by itself.
  from: number;
  to: number;
  elapsed: number;
  duration: number;
  /** The option the marker is nearest to, and the one whose face is in the middle. */
  hot: number;
  shown: number;
  /** How far each option is joined to the disc, 0…1, and how fast that is changing. */
  pull: number[];
  speed: number[];
  face: number;
  faceSpeed: number;
  faceTarget: number;
  /** The wobble of the disc's radius, in points. */
  bump: number;
  bumpSpeed: number;
  /** Seconds since the marker last reached another option. */
  calm: number;
  finger: boolean;
  fingerX: number;
  fingerY: number;
  // The marker's head and tail.
  leadX: number;
  leadY: number;
  lagX: number;
  lagY: number;
  /** The last order carried out. */
  order: number;
};

/** What the script or a button asks of the marker: go `sectors` options round, go to `index`, or tidy its angle. */
type Order = { id: number; kind: 'by' | 'to' | 'home'; sectors: number; index: number; duration: number };

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Cubic ease-in-out, for the marker's journeys. */
function inOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function angleOf(index: number, count: number) {
  'worklet';
  return (index / Math.max(count, 1)) * 2 * Math.PI - Math.PI / 2;
}

/** Centre of option `index`'s circle, measured from the middle of the picker. */
function centerOf(index: number, count: number, pull: number) {
  'worklet';
  const angle = angleOf(index, count);
  const radius = RING - REACH * Math.min(Math.max(pull, 0), 1.2);
  return { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius };
}

function settle(s: Sim, count: number) {
  'worklet';
  for (let i = 0; i < count; i++) {
    s.pull[i] = i === s.hot ? 1 : 0;
    s.speed[i] = 0;
  }
  s.shown = s.hot;
  s.face = 1;
  s.faceSpeed = 0;
  s.faceTarget = 1;
  s.bump = 0;
  s.bumpSpeed = 0;
  s.leadX = s.finger ? s.fingerX : Math.cos(s.theta) * RING;
  s.leadY = s.finger ? s.fingerY : Math.sin(s.theta) * RING + DOT;
  s.lagX = s.leadX;
  s.lagY = s.leadY;
}

function resting(count: number, rest: number): Sim {
  'worklet';
  const s: Sim = {
    theta: angleOf(rest, count),
    from: 0,
    to: 0,
    elapsed: 0,
    duration: 0,
    hot: rest,
    shown: rest,
    pull: new Array<number>(count).fill(0),
    speed: new Array<number>(count).fill(0),
    face: 1,
    faceSpeed: 0,
    faceTarget: 1,
    bump: 0,
    bumpSpeed: 0,
    calm: 0,
    finger: false,
    fingerX: 0,
    fingerY: 0,
    leadX: 0,
    leadY: 0,
    lagX: 0,
    lagY: 0,
    order: 0,
  };
  settle(s, count);
  return s;
}

/** The state `passed` seconds later. */
function advance(before: Sim, order: Order, passed: number, count: number, still: boolean): Sim {
  'worklet';
  const s: Sim = { ...before, pull: before.pull.slice(), speed: before.speed.slice() };
  const step = (2 * Math.PI) / count;

  if (order.id !== s.order) {
    s.order = order.id;
    if (order.kind === 'home') {
      // The marker's angle becomes exactly the resting option's: the same place, without the turns the loop has added up.
      if (s.duration === 0 && !s.finger && s.hot === order.index) s.theta = angleOf(order.index, count);
    } else {
      let sectors = order.sectors;
      if (order.kind === 'to') {
        // The short way round.
        sectors = (order.index - s.hot) % count;
        if (sectors > count / 2) sectors -= count;
        if (sectors < -count / 2) sectors += count;
      }
      s.finger = false;
      // Counted from where the marker is going, if it is still on its way.
      const base = s.duration > 0 ? s.to : s.theta;
      s.from = s.theta;
      s.to = Math.round((base + Math.PI / 2) / step) * step - Math.PI / 2 + sectors * step;
      s.elapsed = 0;
      s.duration = order.duration;
    }
  }

  const steps = Math.max(1, Math.ceil(passed / TICK));
  const h = passed / steps;
  for (let n = 0; n < steps; n++) {
    if (s.duration > 0) {
      s.elapsed += h;
      const t = Math.min(1, s.elapsed / s.duration);
      s.theta = s.from + (s.to - s.from) * inOut(t);
      if (t >= 1) s.duration = 0;
    }

    const sector = Math.round((s.theta + Math.PI / 2) / step);
    const nearest = ((sector % count) + count) % count;
    if (nearest !== s.hot) {
      // The marker reached another option: the face shrinks away and the disc gets a knock.
      s.hot = nearest;
      s.calm = 0;
      s.faceTarget = 0;
      s.bumpSpeed += KNOCK;
    }
    s.calm += h;
    if (s.faceTarget === 0 && s.calm >= POP_AFTER) {
      s.shown = s.hot;
      s.faceTarget = 1;
    }

    for (let i = 0; i < count; i++) {
      // Joining is loose and overshoots; letting go is quick.
      const spring = i === s.hot ? JOIN : PART;
      s.speed[i] += (spring.stiffness * ((i === s.hot ? 1 : 0) - s.pull[i]) - spring.damping * s.speed[i]) * h;
      s.pull[i] += s.speed[i] * h;
    }
    const spring = s.faceTarget > 0.5 ? POP : SHRINK;
    s.faceSpeed += (spring.stiffness * (s.faceTarget - s.face) - spring.damping * s.faceSpeed) * h;
    s.face += s.faceSpeed * h;
    s.bumpSpeed += (-WOBBLE.stiffness * s.bump - WOBBLE.damping * s.bumpSpeed) * h;
    s.bump += s.bumpSpeed * h;

    // The marker heads for the finger, or for just below the circle it points at. Its head follows
    // closely and its tail lags, so a fast move smears it into a pill.
    const goalX = s.finger ? s.fingerX : Math.cos(s.theta) * RING;
    const goalY = s.finger ? s.fingerY : Math.sin(s.theta) * RING + DOT;
    const near = 1 - Math.exp(-h / LEAD_TIME);
    const far = 1 - Math.exp(-h / LAG_TIME);
    s.leadX += (goalX - s.leadX) * near;
    s.leadY += (goalY - s.leadY) * near;
    s.lagX += (s.leadX - s.lagX) * far;
    s.lagY += (s.leadY - s.lagY) * far;
  }
  // Under reduced motion nothing flows: every change is a jump.
  if (still) settle(s, count);
  return s;
}

/** Colours of a face: in colour, or the grey of an option that is not chosen. */
type Palette = { fill: string; side: string; ink: string; cheek?: string };

const COLOR: Palette = { fill: '#fbdd2e', side: '#f2b03a', ink: '#4a3a2a', cheek: '#f79e86' };
const GREY: Palette = { fill: '#c9cdd2', side: '#aeb2b8', ink: '#8e9399' };

type Eye = 'dot' | 'up' | 'down' | 'line';
type MouthShape = 'smile' | 'grin' | 'round' | 'slant' | 'frown' | 'flat';

const LOOKS: Record<Mood, { left: Eye; right: Eye; mouth: MouthShape; cheeks: boolean }> = {
  happy: { left: 'dot', right: 'dot', mouth: 'smile', cheeks: true },
  joyful: { left: 'up', right: 'up', mouth: 'grin', cheeks: false },
  calm: { left: 'down', right: 'down', mouth: 'smile', cheeks: true },
  playful: { left: 'dot', right: 'up', mouth: 'grin', cheeks: true },
  surprised: { left: 'dot', right: 'dot', mouth: 'round', cheeks: false },
  unsure: { left: 'dot', right: 'dot', mouth: 'slant', cheeks: false },
  sad: { left: 'dot', right: 'dot', mouth: 'frown', cheeks: false },
  sleepy: { left: 'line', right: 'line', mouth: 'flat', cheeks: false },
};

const MOUTHS: Record<Exclude<MouthShape, 'round'>, string> = {
  smile: 'M -0.28 0.26 Q 0 0.6 0.28 0.26',
  grin: 'M -0.34 0.24 L 0.34 0.24 C 0.32 0.7 -0.32 0.7 -0.34 0.24 Z',
  slant: 'M -0.22 0.46 L 0.22 0.34',
  frown: 'M -0.26 0.5 Q 0 0.22 0.26 0.5',
  flat: 'M -0.2 0.4 L 0.2 0.4',
};

const EYE_Y = -0.14;

function eyePath(eye: Exclude<Eye, 'dot'>, x: number) {
  if (eye === 'up') return `M ${x - 0.15} ${EYE_Y + 0.06} Q ${x} ${EYE_Y - 0.2} ${x + 0.15} ${EYE_Y + 0.06}`;
  if (eye === 'down') return `M ${x - 0.15} ${EYE_Y - 0.04} Q ${x} ${EYE_Y + 0.2} ${x + 0.15} ${EYE_Y - 0.04}`;
  return `M ${x - 0.14} ${EYE_Y} L ${x + 0.14} ${EYE_Y}`;
}

/** A mood's face, drawn round the origin with a radius of 1. */
function Face({ mood, palette }: { mood: Mood; palette: Palette }) {
  const look = LOOKS[mood];
  const pen = { style: 'stroke', strokeWidth: 0.1, strokeCap: 'round', strokeJoin: 'round', color: palette.ink } as const;
  return (
    <>
      {/* A second disc behind gives the face a thick edge, down and to the right. */}
      <Circle cx={0.07} cy={0.09} r={1} color={palette.side} />
      <Circle cx={0} cy={0} r={1} color={palette.fill} />
      {look.cheeks && palette.cheek !== undefined && (
        <>
          <Circle cx={-0.6} cy={0.2} r={0.14} color={palette.cheek} />
          <Circle cx={0.6} cy={0.2} r={0.14} color={palette.cheek} />
        </>
      )}
      {look.left === 'dot' ? <Circle cx={-0.34} cy={EYE_Y} r={0.11} color={palette.ink} /> : <Path path={eyePath(look.left, -0.34)} {...pen} />}
      {look.right === 'dot' ? <Circle cx={0.34} cy={EYE_Y} r={0.11} color={palette.ink} /> : <Path path={eyePath(look.right, 0.34)} {...pen} />}
      {look.mouth === 'round' ? (
        <Oval x={-0.14} y={0.22} width={0.28} height={0.36} color={palette.ink} />
      ) : (
        <>
          {look.mouth === 'grin' && <Path path={MOUTHS.grin} color={palette.ink} />}
          <Path path={MOUTHS[look.mouth]} {...pen} />
        </>
      )}
    </>
  );
}

type DotProps = { index: number; count: number; sim: SharedValue<Sim> };

/** An option's circle as it is when nothing holds it: translucent. */
function DotBase({ index, count, sim }: DotProps) {
  const cx = useDerivedValue(() => centerOf(index, count, sim.get().pull[index] ?? 0).x);
  const cy = useDerivedValue(() => centerOf(index, count, sim.get().pull[index] ?? 0).y);
  return <Circle cx={cx} cy={cy} r={DOT} color="rgba(255,255,255,0.55)" />;
}

/** The same circle as part of the white shape: it turns opaque as the neck takes hold of it. */
function DotLift({ index, count, sim }: DotProps) {
  const cx = useDerivedValue(() => centerOf(index, count, sim.get().pull[index] ?? 0).x);
  const cy = useDerivedValue(() => centerOf(index, count, sim.get().pull[index] ?? 0).y);
  const opacity = useDerivedValue(() => {
    const pull = sim.get().pull[index] ?? 0;
    return pull > 0.02 ? clamp01(pull * 2.5) : 0;
  });
  return <Circle cx={cx} cy={cy} r={DOT} color="#ffffff" opacity={opacity} />;
}

/** An option's small face: grey, with the coloured one fading in over it as it is joined. */
function DotFace({ index, count, sim, mood }: DotProps & { mood: Mood }) {
  const transform = useDerivedValue(() => {
    const at = centerOf(index, count, sim.get().pull[index] ?? 0);
    return [{ translateX: at.x }, { translateY: at.y }, { scale: SMALL }];
  });
  const tint = useDerivedValue(() => clamp01(sim.get().pull[index] ?? 0));
  return (
    <Group transform={transform}>
      <Face mood={mood} palette={GREY} />
      <Group opacity={tint}>
        <Face mood={mood} palette={COLOR} />
      </Group>
    </Group>
  );
}

/**
 * A radial picker made of drops. A white disc in the middle shows the chosen mood large; the options
 * sit round it as small translucent circles with grey faces. The chosen one is opaque, in colour, and
 * joined to the disc by a neck, as two drops that touch. Dragging round the ring (or tapping an
 * option) moves an orange marker: the neck lets go of the circle it leaves and flows out to each
 * circle the marker reaches, and the face in the middle shrinks to a dot and pops back as the new one.
 */
export function GooeyRadialPicker({ options = MOODS, selection = 2, autoPlay = false, onChange }: Props) {
  const still = useReducedMotion();
  const count = options.length;
  const auto = autoPlay && !still && count > 1;
  const rest = Math.min(Math.max(selection, 0), count - 1);

  const sim = useSharedValue<Sim>(resting(count, rest));
  const order = useSharedValue<Order>({ id: 0, kind: 'home', sectors: 0, index: rest, duration: 0 });
  const orders = useRef(0);
  const [selected, setSelected] = useState(rest);
  // The option whose face is in the middle.
  const [shown, setShown] = useState(rest);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  // A finger is on the picker.
  const [holding, setHolding] = useState(false);

  // Other options, or another first choice: everything goes back to rest.
  const [origin, setOrigin] = useState({ count, rest });
  if (origin.count !== count || origin.rest !== rest) {
    setOrigin({ count, rest });
    setSelected(rest);
    setShown(rest);
  }
  useEffect(() => {
    sim.set(resting(count, rest));
  }, [count, rest, sim]);

  const reached = (index: number, byHand: boolean) => {
    setSelected(index);
    if (byHand && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onChange?.(index);
  };

  useFrameCallback((frame) => {
    const before = sim.get();
    if (before.pull.length !== count) return;
    const passed = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, LONGEST_FRAME);
    const after = advance(before, order.get(), passed, count, still);
    if (after.hot !== before.hot) scheduleOnRN(reached, after.hot, after.finger);
    if (after.shown !== before.shown) scheduleOnRN(setShown, after.shown);
    sim.set(after);
  });

  const send = (kind: Order['kind'], sectors: number, index: number, duration: number) => {
    orders.current += 1;
    order.set({ id: orders.current, kind, sectors, index, duration });
  };

  // The timers below call whatever this is at the time they fire.
  const steps = useRef({ send });
  useEffect(() => {
    steps.current = { send };
  });

  // The script a finger would follow: half a turn against the clock, two single steps, and the rest
  // of the turn back to where the loop started.
  useEffect(() => {
    if (!auto || holding) return;
    let timer: ReturnType<typeof setTimeout>;
    // What each move covers, in options: the last one finishes the turn, whatever their number.
    let gone = 0;
    const moves = SCRIPT.map((move, n) => {
      const sectors = n === SCRIPT.length - 1 ? (count - (gone % count)) % count : Math.max(1, Math.round(move.turn * count));
      gone += sectors;
      return sectors;
    });
    const run = (begin: number, n: number) => {
      const step = n % SCRIPT.length;
      const turn = Math.floor(n / SCRIPT.length);
      // Every move is timed from the script's start, so the loop keeps its period.
      const due = begin + (turn * LOOP + SCRIPT[step].at) * 1000;
      timer = setTimeout(() => {
        if (moves[step] > 0) steps.current.send('by', -moves[step], 0, SCRIPT[step].time);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the first option and start over.
      timer = setTimeout(() => {
        steps.current.send('to', 0, rest, SWEEP_TIME);
        run(Date.now() + SWEEP_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, holding, rest, count]);

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
    .onBegin((event) => {
      scheduleOnRN(grab);
      const x = event.x - SIZE / 2;
      const y = event.y - SIZE / 2;
      const s = sim.get();
      // Too near the middle the direction means nothing.
      sim.set({ ...s, duration: 0, finger: true, fingerX: x, fingerY: y, theta: Math.hypot(x, y) > DEAD_ZONE ? Math.atan2(y, x) : s.theta });
    })
    .onUpdate((event) => {
      const x = event.x - SIZE / 2;
      const y = event.y - SIZE / 2;
      const s = sim.get();
      sim.set({ ...s, duration: 0, finger: true, fingerX: x, fingerY: y, theta: Math.hypot(x, y) > DEAD_ZONE ? Math.atan2(y, x) : s.theta });
    })
    .onFinalize(() => {
      // The finger lifted: the marker settles under the option it reached.
      const s = sim.get();
      sim.set({ ...s, finger: false, theta: angleOf(s.hot, count) });
      scheduleOnRN(release);
    });

  const disc = useDerivedValue(() => DISC + sim.get().bump);

  // The bridges between the disc and the circles it holds: two curves that leave each circle along
  // its edge and meet in between, a thread when the pull is small and a thick neck when it is 1.
  const necks = useDerivedValue(() => {
    const s = sim.get();
    const path = Skia.Path.Make();
    const r1 = DISC + s.bump;
    const r2 = DOT;
    for (let i = 0; i < s.pull.length; i++) {
      if (s.pull[i] <= 0.02) continue;
      const v = 0.5 * Math.min(s.pull[i], 1.3);
      const c2 = centerOf(i, count, s.pull[i]);
      const d = Math.hypot(c2.x, c2.y);
      if (d <= Math.abs(r1 - r2)) continue;
      // Where the two circles cross, when they overlap.
      const overlap = d < r1 + r2;
      const u1 = overlap ? Math.acos((r1 * r1 + d * d - r2 * r2) / (2 * r1 * d)) : 0;
      const u2 = overlap ? Math.acos((r2 * r2 + d * d - r1 * r1) / (2 * r2 * d)) : 0;
      const base = Math.atan2(c2.y, c2.x);
      const spread = Math.acos((r1 - r2) / d);
      const a1 = base + u1 + (spread - u1) * v;
      const a2 = base - u1 - (spread - u1) * v;
      const a3 = base + Math.PI - u2 - (Math.PI - u2 - spread) * v;
      const a4 = base - Math.PI + u2 + (Math.PI - u2 - spread) * v;
      const p1x = Math.cos(a1) * r1;
      const p1y = Math.sin(a1) * r1;
      const p2x = Math.cos(a2) * r1;
      const p2y = Math.sin(a2) * r1;
      const p3x = c2.x + Math.cos(a3) * r2;
      const p3y = c2.y + Math.sin(a3) * r2;
      const p4x = c2.x + Math.cos(a4) * r2;
      const p4y = c2.y + Math.sin(a4) * r2;
      // The curves' handles lie along the circles' edges, longer when the bridge is longer.
      const handle = Math.min(v * 2.4, Math.hypot(p1x - p3x, p1y - p3y) / (r1 + r2)) * Math.min(1, (d * 2) / (r1 + r2));
      const h1 = r1 * handle;
      const h2 = r2 * handle;
      const quarter = Math.PI / 2;
      path.moveTo(p1x, p1y);
      path.cubicTo(
        p1x + Math.cos(a1 - quarter) * h1,
        p1y + Math.sin(a1 - quarter) * h1,
        p3x + Math.cos(a3 + quarter) * h2,
        p3y + Math.sin(a3 + quarter) * h2,
        p3x,
        p3y,
      );
      path.lineTo(p4x, p4y);
      path.cubicTo(
        p4x + Math.cos(a4 - quarter) * h2,
        p4y + Math.sin(a4 - quarter) * h2,
        p2x + Math.cos(a2 + quarter) * h1,
        p2y + Math.sin(a2 + quarter) * h1,
        p2x,
        p2y,
      );
      path.close();
    }
    return path;
  });

  const faceTransform = useDerivedValue(() => [{ scale: LARGE * Math.max(sim.get().face, 0.001) }]);
  const faceOpacity = useDerivedValue(() => (sim.get().face > 0.03 ? 1 : 0));

  // The hole of the marker closes as the pill gets longer.
  const markerLine = useDerivedValue(() => {
    const s = sim.get();
    return mix(MARKER_LINE, MARKER, clamp01(Math.hypot(s.leadX - s.lagX, s.leadY - s.lagY) / 26));
  });
  // The marker: a ring at rest, stretched into a pill between its tail and its head as it moves.
  const marker = useDerivedValue(() => {
    const s = sim.get();
    const length = Math.hypot(s.leadX - s.lagX, s.leadY - s.lagY);
    const r = MARKER - mix(MARKER_LINE, MARKER, clamp01(length / 26)) / 2;
    const ux = length > 0.01 ? (s.leadX - s.lagX) / length : 1;
    const uy = length > 0.01 ? (s.leadY - s.lagY) / length : 0;
    const k = 0.5523 * r;
    const path = Skia.Path.Make();
    // A point `along` the pill's axis and `across` it from `(x, y)`.
    const px = (x: number, along: number, across: number) => x + ux * along - uy * across;
    const py = (y: number, along: number, across: number) => y + uy * along + ux * across;
    path.moveTo(px(s.lagX, 0, r), py(s.lagY, 0, r));
    path.lineTo(px(s.leadX, 0, r), py(s.leadY, 0, r));
    path.cubicTo(px(s.leadX, k, r), py(s.leadY, k, r), px(s.leadX, r, k), py(s.leadY, r, k), px(s.leadX, r, 0), py(s.leadY, r, 0));
    path.cubicTo(px(s.leadX, r, -k), py(s.leadY, r, -k), px(s.leadX, k, -r), py(s.leadY, k, -r), px(s.leadX, 0, -r), py(s.leadY, 0, -r));
    path.lineTo(px(s.lagX, 0, -r), py(s.lagY, 0, -r));
    path.cubicTo(px(s.lagX, -k, -r), py(s.lagY, -k, -r), px(s.lagX, -r, -k), py(s.lagY, -r, -k), px(s.lagX, -r, 0), py(s.lagY, -r, 0));
    path.cubicTo(px(s.lagX, -r, k), py(s.lagY, -r, k), px(s.lagX, -k, r), py(s.lagY, -k, r), px(s.lagX, 0, r), py(s.lagY, 0, r));
    path.close();
    return path;
  });

  const indices = options.map((_, index) => index);

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.root}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Mood"
        accessibilityValue={{ text: options[selected] ?? '' }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          setTouches((n) => n + 1);
          send('by', event.nativeEvent.actionName === 'increment' ? 1 : -1, 0, STEP_TIME);
        }}
      >
        {/* The canvas is larger than the picker, so the shadow has room. */}
        <Canvas style={styles.canvas} pointerEvents="none">
          <Group transform={[{ translateX: SIZE / 2 + BLEED }, { translateY: SIZE / 2 + BLEED }]}>
            {indices.map((index) => (
              <DotBase key={index} index={index} count={count} sim={sim} />
            ))}

            {/* The disc, the necks and the circles they hold are one shape with one shadow. */}
            <Group
              layer={
                <Paint>
                  <Shadow dx={12} dy={16} blur={16} color="rgba(143,163,181,0.45)" />
                </Paint>
              }
            >
              <Circle cx={0} cy={0} r={disc} color="#ffffff" />
              <Path path={necks} color="#ffffff" />
              {indices.map((index) => (
                <DotLift key={index} index={index} count={count} sim={sim} />
              ))}
            </Group>

            {indices.map((index) => (
              <DotFace key={index} index={index} count={count} sim={sim} mood={options[index]} />
            ))}
            {shown < count && (
              <Group transform={faceTransform} opacity={faceOpacity}>
                <Face mood={options[shown]} palette={COLOR} />
              </Group>
            )}

            <Path path={marker} style="stroke" strokeWidth={markerLine} color="rgba(242,180,90,0.8)" />
          </Group>
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    width: SIZE,
    height: SIZE,
  },
  canvas: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
    width: SIZE + BLEED * 2,
    height: SIZE + BLEED * 2,
  },
});
