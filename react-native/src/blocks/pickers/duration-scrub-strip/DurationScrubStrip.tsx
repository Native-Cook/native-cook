import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The longest duration, in minutes. */
  range?: number;
  /** Minutes between two ticks. */
  tickEvery?: number;
  /** The duration at first; `autoPlay` comes back to it at the end of every loop. */
  minutes?: number;
  /** When the stretch of time begins, in minutes after midnight. */
  start?: number;
  /** Scrubs to three durations and back by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the duration in minutes whenever it changes. */
  onChange?: (minutes: number) => void;
};

const WIDTH = 330;
const HEIGHT = 53;
// Room above the strip for the pill, which straddles its top edge.
const TOP = 14;
const RADIUS = 20;
const PAD = 8;
const CHIP_W = 74;
const CHIP_RADIUS = 9;
// The ticks run from `TRACK_X` across `TRACK_W`.
const TRACK_X = 93;
const TRACK_W = 144;
const TICK_W = 1.5;
const TICK_H = 20;
const MARKER_W = 2;
const MARKER_H = 26;
const BADGE_H = 22;
const BADGE_PAD = 8;
// The comet's tail is this many ticks long.
const TAIL = 9;
// The pill holds up to this many characters ("11h 59m"), each in a box this wide.
const SLOTS = 7;
const SLOT_W = 14;
const RED = '#ff3b1f';

// The simulation's step, in seconds, and the longest a frame is counted as.
const TICK = 1 / 240;
const LONGEST_FRAME = 0.25;
const FOLLOW = { stiffness: 420, damping: 36 };
const RIDE = { stiffness: 600, damping: 30 };
const STRETCH = { stiffness: 400, damping: 30 };
const ROLL_TIME = 0.16;
const LIGHT_TIME = 0.03;
const DIM_TIME = 0.12;
const FLASH_TIME = 0.2;

// autoPlay (seconds). Each run: when it starts, the share of the range it goes to (null: back to
// the first duration), how long it takes.
const LOOP = 8;
const BACK_TIME = 0.7;
const SCRIPT: { at: number; share: number | null; time: number }[] = [
  { at: 0.8, share: 0.325, time: 1.1 },
  { at: 2.5, share: 0.686, time: 1 },
  { at: 4.1, share: 1, time: 0.8 },
  { at: 5.8, share: null, time: BACK_TIME },
];
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

/** Everything that moves, stepped in small fixed steps on every frame. */
type Sim = {
  /** Where the marker is heading and where it is, in minutes, and the whole minutes it reads as. */
  target: number;
  value: number;
  valueSpeed: number;
  minutes: number;
  // The marker's journey when it travels by itself.
  from: number;
  to: number;
  elapsed: number;
  duration: number;
  /** The pill's centre and width, in points. */
  badge: number;
  badgeSpeed: number;
  badgeWidth: number;
  widthSpeed: number;
  /** Each tick: how lit it is, 0…1, the flash it got when the marker passed, and whether it has. */
  lit: number[];
  flash: number[];
  passed: boolean[];
  /** The last order carried out. */
  order: number;
};

/** What the script asks of the marker: glide to `minutes` in `duration` seconds. */
type Order = { id: number; minutes: number; duration: number };

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Cubic ease-in-out, for the marker's runs. */
function inOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** "0m", "54m", "3h 54m", "12h". */
function labelOf(minutes: number) {
  'worklet';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}m`;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

/** The room a character of the pill takes: digits all take the same, so the pill does not shiver. */
function advanceOf(character: string) {
  'worklet';
  if (character === ' ') return 3;
  if (character === 'h') return 6.6;
  if (character === 'm') return 9.8;
  return 6.8;
}

function widthOf(label: string) {
  'worklet';
  let width = BADGE_PAD * 2;
  for (let i = 0; i < label.length; i++) width += advanceOf(label[i]);
  return width;
}

/** A time of day as "4:30 PM". */
function clock(minutes: number) {
  const day = ((minutes % 1440) + 1440) % 1440;
  const hour = Math.floor(day / 60) % 12;
  const minute = day % 60;
  return `${hour === 0 ? 12 : hour}:${minute < 10 ? '0' : ''}${minute} ${day < 720 ? 'AM' : 'PM'}`;
}

function settle(s: Sim, range: number, tickEvery: number) {
  'worklet';
  s.value = s.target;
  s.valueSpeed = 0;
  s.minutes = Math.round(Math.min(Math.max(s.value, 0), range));
  s.badge = TRACK_X + (s.value / range) * TRACK_W;
  s.badgeSpeed = 0;
  s.badgeWidth = widthOf(labelOf(s.minutes));
  s.widthSpeed = 0;
  for (let i = 0; i < s.lit.length; i++) {
    s.passed[i] = i <= s.target / tickEvery + 0.02;
    s.lit[i] = s.passed[i] ? 1 : 0;
    s.flash[i] = 0;
  }
}

function resting(range: number, tickEvery: number, minutes: number): Sim {
  'worklet';
  const count = Math.floor(range / tickEvery) + 1;
  const s: Sim = {
    target: minutes,
    value: minutes,
    valueSpeed: 0,
    minutes,
    from: 0,
    to: 0,
    elapsed: 0,
    duration: 0,
    badge: 0,
    badgeSpeed: 0,
    badgeWidth: 0,
    widthSpeed: 0,
    lit: new Array<number>(count).fill(0),
    flash: new Array<number>(count).fill(0),
    passed: new Array<boolean>(count).fill(false),
    order: 0,
  };
  settle(s, range, tickEvery);
  return s;
}

/** The state `gone` seconds later. */
function advance(before: Sim, order: Order, gone: number, range: number, tickEvery: number, still: boolean): Sim {
  'worklet';
  const s: Sim = { ...before, lit: before.lit.slice(), flash: before.flash.slice(), passed: before.passed.slice() };
  if (order.id !== s.order) {
    s.order = order.id;
    s.from = s.target;
    s.to = order.minutes;
    s.elapsed = 0;
    s.duration = order.duration;
  }

  const steps = Math.max(1, Math.ceil(gone / TICK));
  const h = gone / steps;
  for (let n = 0; n < steps; n++) {
    if (s.duration > 0) {
      s.elapsed += h;
      const t = Math.min(1, s.elapsed / s.duration);
      s.target = s.from + (s.to - s.from) * inOut(t);
      if (t >= 1) s.duration = 0;
    }

    // The marker follows its target on a firm spring, the pill follows the marker on a loose one.
    s.valueSpeed += (FOLLOW.stiffness * (s.target - s.value) - FOLLOW.damping * s.valueSpeed) * h;
    s.value += s.valueSpeed * h;
    const x = TRACK_X + (s.value / range) * TRACK_W;
    s.badgeSpeed += (RIDE.stiffness * (x - s.badge) - RIDE.damping * s.badgeSpeed) * h;
    s.badge += s.badgeSpeed * h;

    s.minutes = Math.round(Math.min(Math.max(s.value, 0), range));
    s.widthSpeed += (STRETCH.stiffness * (widthOf(labelOf(s.minutes)) - s.badgeWidth) - STRETCH.damping * s.widthSpeed) * h;
    s.badgeWidth += s.widthSpeed * h;

    const at = s.value / tickEvery;
    for (let i = 0; i < s.lit.length; i++) {
      const on = i <= at + 0.02;
      if (on && !s.passed[i]) s.flash[i] = 1;
      s.passed[i] = on;
      // A tick lights at once and goes out slowly.
      s.lit[i] += ((on ? 1 : 0) - s.lit[i]) * (1 - Math.exp(-h / (on ? LIGHT_TIME : DIM_TIME)));
      s.flash[i] *= Math.exp(-h / FLASH_TIME);
    }
  }
  // Under reduced motion nothing glides: every change is a jump.
  if (still) settle(s, range, tickEvery);
  return s;
}

/** One tick of the row. */
function Tick({ index, count, sim, range }: { index: number; count: number; sim: SharedValue<Sim>; range: number }) {
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    const last = Math.max(count - 1, 1);
    const at = (s.value / range) * last;
    // Not reached: dim, and dimmer towards the end. Passed: bright just behind the marker, greyer further back.
    const dim = mix(0.16, 0.06, index / last);
    const bright = mix(0.92, 0.34, clamp01((at - index) / TAIL));
    const lit = s.lit[index] ?? 0;
    const glow = mix(dim, bright, lit);
    return { opacity: glow + (1 - glow) * (s.flash[index] ?? 0) * lit };
  });
  return <Animated.View style={[styles.tick, { left: TRACK_X + (index / Math.max(count - 1, 1)) * TRACK_W - TICK_W / 2 }, style]} />;
}

type SlotProps = {
  /** The character this place of the pill shows now, if any. */
  character: string | null;
  /** Where its box starts, measured from the middle of the pill. */
  left: number;
  /** +1 when the duration last grew, -1 when it shrank: the way the characters roll. */
  way: number;
  still: boolean;
};

/** One character of the pill. When it changes, the new one rolls in over the one it replaces. */
function Slot({ character, left, way, still }: SlotProps) {
  const [now, setNow] = useState(character);
  const [before, setBefore] = useState<string | null>(null);
  const [turn, setTurn] = useState(0);
  if (character !== now) {
    setBefore(now);
    setNow(character);
    setTurn(turn + 1);
  }
  const roll = useSharedValue(1);

  useEffect(() => {
    if (turn === 0 || still) return;
    roll.set(0);
    roll.set(withTiming(1, { duration: ROLL_TIME * 1000, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never }));
  }, [turn, still, roll]);

  const oldStyle = useAnimatedStyle(() => ({ opacity: 1 - roll.get(), transform: [{ translateY: -way * 9 * roll.get() }] }));
  const newStyle = useAnimatedStyle(() => ({ opacity: roll.get(), transform: [{ translateY: way * 9 * (1 - roll.get()) }] }));
  const room = advanceOf(now ?? before ?? ' ');

  return (
    <View style={[styles.slot, { left: left + room / 2 - SLOT_W / 2 }]}>
      {before !== null && <Animated.Text style={[styles.character, oldStyle]}>{before}</Animated.Text>}
      {now !== null && <Animated.Text style={[styles.character, newStyle]}>{now}</Animated.Text>}
    </View>
  );
}

/**
 * A compact duration scrubber. A dark strip holds the start time and the end time in two chips and
 * a row of ticks between them; a red marker stands in the ticks at the chosen duration, with the
 * duration in a pill riding above it. Dragging along the strip (or tapping it) moves the marker:
 * the ticks it passes light up behind it like a comet's tail, the pill's digits roll, and the end
 * time follows.
 */
export function DurationScrubStrip({ range = 720, tickEvery = 30, minutes = 0, start = 16 * 60 + 30, autoPlay = false, onChange }: Props) {
  const still = useReducedMotion();
  const span = Math.max(range, 1);
  const every = Math.max(tickEvery, 1);
  const auto = autoPlay && !still && range > 0;
  const rest = Math.min(Math.max(minutes, 0), span);
  const count = Math.floor(span / every) + 1;

  const sim = useSharedValue<Sim>(resting(span, every, rest));
  const order = useSharedValue<Order>({ id: 0, minutes: rest, duration: 0 });
  const orders = useRef(0);
  // The tick the finger last passed, so each one is felt once.
  const notch = useSharedValue(0);
  // The whole minutes the marker reads as, and the way they last changed.
  const [reading, setReading] = useState({ minutes: rest, way: 1 });
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  // A finger is on the strip.
  const [holding, setHolding] = useState(false);

  // Another range or first duration: everything goes back to rest.
  const [origin, setOrigin] = useState({ span, every, rest });
  if (origin.span !== span || origin.every !== every || origin.rest !== rest) {
    setOrigin({ span, every, rest });
    setReading({ minutes: rest, way: 1 });
  }
  useEffect(() => {
    sim.set(resting(span, every, rest));
  }, [span, every, rest, sim]);

  const reached = (value: number) => {
    setReading((old) => ({ minutes: value, way: value >= old.minutes ? 1 : -1 }));
    onChange?.(value);
  };

  useFrameCallback((frame) => {
    const before = sim.get();
    if (before.lit.length !== count) return;
    const gone = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, LONGEST_FRAME);
    const after = advance(before, order.get(), gone, span, every, still);
    if (after.minutes !== before.minutes) scheduleOnRN(reached, after.minutes);
    sim.set(after);
  });

  const glide = (to: number, duration: number) => {
    orders.current += 1;
    order.set({ id: orders.current, minutes: to, duration });
  };

  // The timers below call whatever this is at the time they fire.
  const steps = useRef({ glide });
  useEffect(() => {
    steps.current = { glide };
  });

  // The script a finger would follow: three runs up the strip, each further, and a sweep back.
  useEffect(() => {
    if (!auto || holding) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const move = SCRIPT[n % SCRIPT.length];
      // Every run is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + move.at) * 1000;
      timer = setTimeout(() => {
        steps.current.glide(move.share === null ? rest : Math.round(move.share * span), move.time);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the first duration and start over.
      timer = setTimeout(() => {
        steps.current.glide(rest, BACK_TIME);
        run(Date.now() + BACK_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, holding, rest, span]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

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
      const s = sim.get();
      const target = Math.round(clamp01((event.x - TRACK_X) / TRACK_W) * span);
      const passed = Math.floor(target / every);
      if (passed !== Math.floor(s.target / every)) scheduleOnRN(tick);
      notch.set(passed);
      sim.set({ ...s, duration: 0, target });
    })
    .onUpdate((event) => {
      const target = Math.round(clamp01((event.x - TRACK_X) / TRACK_W) * span);
      const passed = Math.floor(target / every);
      if (passed !== notch.get()) {
        notch.set(passed);
        scheduleOnRN(tick);
      }
      sim.set({ ...sim.get(), duration: 0, target });
    })
    .onFinalize(() => {
      scheduleOnRN(release);
    });

  const markerStyle = useAnimatedStyle(() => ({ transform: [{ translateX: TRACK_X + (sim.get().value / span) * TRACK_W - MARKER_W / 2 }] }));
  // The pill leans back while it is behind the marker, about the middle of its lower edge.
  const badgeStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const x = TRACK_X + (s.value / span) * TRACK_W;
    return {
      width: s.badgeWidth,
      transform: [{ translateX: s.badge - s.badgeWidth / 2 }, { rotate: `${Math.min(Math.max((s.badge - x) * 0.6, -8), 8)}deg` }],
    };
  });

  // The characters are laid out by what the pill reads now.
  const label = labelOf(reading.minutes);
  const lefts: number[] = [];
  let left = -(widthOf(label) - BADGE_PAD * 2) / 2;
  for (let i = 0; i < SLOTS; i++) {
    lefts.push(left);
    if (i < label.length) left += advanceOf(label[i]);
  }

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.root}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Duration"
        accessibilityValue={{ text: label }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          setTouches((n) => n + 1);
          const step = event.nativeEvent.actionName === 'increment' ? every : -every;
          glide(Math.min(Math.max(reading.minutes + step, 0), span), 0.2);
        }}
      >
        <View style={styles.strip}>
          <View style={[styles.chip, { left: PAD - 1 }]}>
            <Text style={styles.time} numberOfLines={1}>
              {clock(start)}
            </Text>
          </View>
          <View style={[styles.chip, { right: PAD - 1 }]}>
            <Text style={styles.time} numberOfLines={1}>
              {clock(start + reading.minutes)}
            </Text>
          </View>
        </View>

        {Array.from({ length: count }, (_, index) => (
          <Tick key={index} index={index} count={count} sim={sim} range={span} />
        ))}
        <Animated.View style={[styles.marker, markerStyle]} />

        <Animated.View style={[styles.badge, badgeStyle]}>
          <View style={styles.slots}>
            {lefts.map((at, index) => (
              <Slot key={index} character={index < label.length ? label[index] : null} left={at} way={reading.way} still={still} />
            ))}
          </View>
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: TOP + HEIGHT,
  },
  strip: {
    position: 'absolute',
    left: 0,
    top: TOP,
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    borderWidth: 1,
    borderColor: '#3a3a3a',
    backgroundColor: '#1b1b1b',
  },
  chip: {
    position: 'absolute',
    top: PAD - 1,
    width: CHIP_W,
    height: HEIGHT - PAD * 2,
    borderRadius: CHIP_RADIUS,
    borderWidth: 1,
    borderColor: '#3f3f3f',
    backgroundColor: '#2a2a2a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  time: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
    fontFamily: process.env.EXPO_OS === 'ios' ? 'ui-monospace' : 'monospace',
  },
  tick: {
    position: 'absolute',
    top: TOP + HEIGHT / 2 - TICK_H / 2,
    width: TICK_W,
    height: TICK_H,
    borderRadius: TICK_W / 2,
    backgroundColor: '#ffffff',
  },
  marker: {
    position: 'absolute',
    left: 0,
    top: TOP + HEIGHT / 2 - MARKER_H / 2,
    width: MARKER_W,
    height: MARKER_H,
    borderRadius: MARKER_W / 2,
    backgroundColor: RED,
  },
  badge: {
    position: 'absolute',
    left: 0,
    top: TOP - BADGE_H / 2,
    height: BADGE_H,
    borderRadius: BADGE_H / 2,
    backgroundColor: RED,
    transformOrigin: 'center bottom',
  },
  slots: {
    position: 'absolute',
    left: '50%',
    top: 0,
    width: 0,
    height: BADGE_H,
  },
  slot: {
    position: 'absolute',
    top: 0,
    width: SLOT_W,
    height: BADGE_H,
  },
  character: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: SLOT_W,
    height: BADGE_H,
    lineHeight: BADGE_H,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 11,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
});
