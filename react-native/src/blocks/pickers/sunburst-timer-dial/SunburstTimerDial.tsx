import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  interpolateColor,
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

type Props = {
  /** The value the dial starts on, 0…59. */
  initial?: number;
  /** The caption under the hub. */
  label?: string;
  /** Sweeps the dial through a short script by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the value the dial settles on. */
  onChange?: (value: number) => void;
};

// The square the dial is drawn in.
const SIZE = 320;
const RAYS = 28;
const UNITS = 60;
const TAU = Math.PI * 2;

// Radii: a ray's two ends, the eight markers and the hub.
const INNER = 83;
const OUTER = 140;
const MARKER = 151;
const HUB = 24;
// How far a ray shoots out as it lights, and how far the hub's dot leans.
const FLICK = 4;
const LEAN = 3;
// The value's and the caption's distance from the centre.
const TEXT_Y = 54;
// Touches closer to the centre than this do not turn the dial.
const DEAD = 12;

// Seconds: a ray's change of weight, the sweep to a finger that just landed, and the chase after it.
const LIGHT_TIME = 0.08;
const REACH_TIME = 0.3;
const FOLLOW_TIME = 0.1;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);

// autoPlay (seconds): when each sweep starts, where it ends, how long it takes and on which curve.
const SCRIPT = [
  { at: 0.8, to: 15, time: 1.0, easing: EASE_OUT },
  { at: 2.5, to: 40, time: 1.2, easing: EASE_IN_OUT },
  { at: 4.4, to: 25, time: 0.7, easing: EASE_IN_OUT },
  { at: 5.7, to: 0, time: 1.0, easing: EASE_IN_OUT },
];
const LOOP = 8;
// After a touch the loop waits this long, then takes `RETURN_TIME` to sweep back.
const RESUME_AFTER = 2;
const RETURN_TIME = 0.6;

const SNAP_SPRING = { mass: 1, stiffness: 400, damping: 26, reduceMotion: ReduceMotion.Never };
const PRESS_SPRING = { mass: 1, stiffness: 320, damping: 22, reduceMotion: ReduceMotion.Never };

/** How many rays are lit at `value`, taken to the unit the digits show: the one at 12 o'clock, and one more every 60/28 units. */
function litAt(value: number) {
  'worklet';
  return Math.min(Math.max(Math.floor((Math.round(value) * RAYS) / UNITS + 0.0001) + 1, 1), RAYS);
}

/** The angle of a point seen from the centre: 0 at 12 o'clock, growing clockwise to a full turn. */
function turnOf(x: number, y: number) {
  'worklet';
  const turn = Math.atan2(x, -y);
  return turn < 0 ? turn + TAU : turn;
}

type RayProps = {
  index: number;
  /** How many rays are lit. */
  count: SharedValue<number>;
  /** This ray starts lit. */
  first: boolean;
  still: boolean;
};

/** One ray: a hairline that turns bold when the value passes it, with a flick outward. */
function Ray({ index, count, first, still }: RayProps) {
  const lit = useSharedValue(first ? 1 : 0);
  const flick = useSharedValue(0);

  useAnimatedReaction(
    () => index < count.get(),
    (on, was) => {
      if (on === was || was === null) return;
      const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
      // Under reduced motion nothing eases: rays switch at once and none flicks.
      lit.set(withTiming(on ? 1 : 0, { duration: still ? 0 : LIGHT_TIME * 1000, ...linear }));
      if (on && !still) flick.set(withSequence(withTiming(1, { duration: 30, ...linear }), withTiming(0, { duration: 120, ...linear })));
    },
  );

  const line = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(lit.get(), [0, 1], ['#A3A3A0', '#111111']),
    transform: [{ scaleX: (1 + lit.get()) / 2 }, { scaleY: 1 + (FLICK / (OUTER - INNER)) * flick.get() }],
  }));

  return (
    <View style={[styles.spoke, { transform: [{ rotate: `${(360 * index) / RAYS}deg` }] }]}>
      <Animated.View style={[styles.ray, line]} />
    </View>
  );
}

/**
 * A timer dial drawn as a burst of 28 rays around a dark hub. The face counts 60 units: rays light
 * clockwise from 12 o'clock as the value grows, the two-digit value sits above the hub and a caption
 * below it. Dragging round the dial sets the value to the angle under the finger, without wrapping
 * past the top; each ray flicks outward as it lights, and the hub's dot leans towards the newest ray.
 */
export function SunburstTimerDial({ initial = 0, label = 'Timer', autoPlay = false, onChange }: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still;
  const start = Math.min(Math.max(initial, 0), UNITS - 1);

  // The value, 0…59, continuous while it sweeps.
  const value = useSharedValue(start);
  // 1 while a finger (or the script) is turning the dial: the hub dips and its dot leans.
  const press = useSharedValue(0);
  // The finger's last angle and the value it has dragged to.
  const angle = useSharedValue(0);
  const dragged = useSharedValue(start);
  const held = useSharedValue(false);
  // The dial is turning under a finger, not under the script: only then it ticks.
  const byHand = useSharedValue(false);
  const count = useDerivedValue(() => litAt(value.get()));
  const [shown, setShown] = useState(start);
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

  useAnimatedReaction(
    () => count.get(),
    (now, before) => {
      if (before !== null && now !== before && byHand.get()) scheduleOnRN(tick);
    },
  );
  useAnimatedReaction(
    () => Math.min(Math.max(Math.round(value.get()), 0), UNITS - 1),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShown, now);
    },
  );

  const hold = (down: boolean) => {
    press.set(still ? (down ? 1 : 0) : withSpring(down ? 1 : 0, PRESS_SPRING));
  };
  /** Sweeps to `target` in `time` seconds. */
  const sweep = (to: number, time: number, easing: (typeof SCRIPT)[number]['easing']) => {
    const target = Math.min(Math.max(to, 0), UNITS - 1);
    value.set(withTiming(target, { duration: still ? 0 : time * 1000, easing, reduceMotion: ReduceMotion.Never }));
    onChange?.(target);
  };

  // The timers below call whatever these are at the time they fire.
  const actions = useRef({ hold, sweep });
  useEffect(() => {
    actions.current = { hold, sweep };
  });

  // The script a finger would follow: four sweeps, the last one back to where it began.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      // Each sweep is two moments, its start and its end, both timed from the script's start.
      const step = SCRIPT[Math.floor(n / 2) % SCRIPT.length];
      const lifts = n % 2 === 1;
      const due = begin + (Math.floor(n / 2 / SCRIPT.length) * LOOP + step.at + (lifts ? step.time : 0)) * 1000;
      timer = setTimeout(() => {
        actions.current.hold(!lifts);
        if (!lifts) actions.current.sweep(start + step.to, step.time, step.easing);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, sweep back to the first value, and start over from rest.
      timer = setTimeout(() => {
        if (held.get()) return;
        byHand.set(false);
        actions.current.sweep(start, RETURN_TIME, EASE_IN_OUT);
        run(Date.now() + RETURN_TIME * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, start, held, byHand]);

  // Dragging anywhere on the dial: the value goes to the angle under the finger, then follows it
  // round, stopping at 0 and 59 instead of wrapping past the top.
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const x = event.x - SIZE / 2;
      const y = event.y - SIZE / 2;
      const turn = turnOf(x, y);
      held.set(true);
      byHand.set(true);
      angle.set(turn);
      // Right over the centre the angle means nothing.
      dragged.set(Math.hypot(x, y) > DEAD ? Math.min((turn / TAU) * UNITS, UNITS - 1) : value.get());
      press.set(still ? 1 : withSpring(1, PRESS_SPRING));
      value.set(withTiming(dragged.get(), { duration: still ? 0 : REACH_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
      scheduleOnRN(touch);
    })
    .onUpdate((event) => {
      const x = event.x - SIZE / 2;
      const y = event.y - SIZE / 2;
      if (Math.hypot(x, y) <= DEAD) return;
      const turn = turnOf(x, y);
      let step = turn - angle.get();
      if (step > Math.PI) step -= TAU;
      else if (step < -Math.PI) step += TAU;
      angle.set(turn);
      dragged.set(Math.min(Math.max(dragged.get() + (step / TAU) * UNITS, 0), UNITS - 1));
      value.set(withTiming(dragged.get(), { duration: still ? 0 : FOLLOW_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
    })
    .onFinalize(() => {
      if (!held.get()) return;
      held.set(false);
      const target = Math.round(dragged.get());
      press.set(still ? 0 : withSpring(0, PRESS_SPRING));
      value.set(still ? target : withSpring(target, SNAP_SPRING));
      scheduleOnRN(release, target);
    });

  // The hub dips under the finger, and its dot leans towards the newest ray.
  const hub = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.05 * press.get() }] }));
  const dot = useAnimatedStyle(() => {
    const turn = (TAU * value.get()) / UNITS;
    return {
      transform: [{ translateX: Math.sin(turn) * LEAN * press.get() }, { translateY: -Math.cos(turn) * LEAN * press.get() }],
    };
  });

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.dial}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={label}
        accessibilityValue={{ min: 0, max: UNITS - 1, now: shown }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          const target = Math.min(Math.max(shown + (event.nativeEvent.actionName === 'increment' ? 1 : -1), 0), UNITS - 1);
          value.set(still ? target : withSpring(target, SNAP_SPRING));
          release(target);
        }}
      >
        {/* Everything is placed from the middle of the dial. */}
        <View pointerEvents="none" style={styles.center}>
          {Array.from({ length: RAYS }, (_, index) => (
            <Ray key={index} index={index} count={count} first={index < litAt(start)} still={still} />
          ))}
          {Array.from({ length: 8 }, (_, index) => (
            <View
              key={index}
              style={[
                styles.marker,
                { left: Math.sin((TAU * index) / 8) * MARKER - 1.5, top: -Math.cos((TAU * index) / 8) * MARKER - 1.5 },
              ]}
            />
          ))}
          <Animated.View style={[styles.hub, hub]} />
          <Animated.View style={[styles.dot, dot]} />
          <Text style={[styles.text, styles.digits]}>{shown < 10 ? `0${shown}` : shown}</Text>
          <Text style={[styles.text, styles.caption]} numberOfLines={1}>
            {label}
          </Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  dial: {
    width: SIZE,
    height: SIZE,
  },
  center: {
    position: 'absolute',
    left: SIZE / 2,
    top: SIZE / 2,
    width: 0,
    height: 0,
  },
  spoke: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 0,
    height: 0,
  },
  // Two points wide, drawn at half that until it lights; it stretches from its inner end.
  ray: {
    position: 'absolute',
    left: -1,
    top: -OUTER,
    width: 2,
    height: OUTER - INNER,
    transformOrigin: 'center bottom',
  },
  marker: {
    position: 'absolute',
    width: 3,
    height: 3,
    borderRadius: 1.5,
    backgroundColor: '#111111',
  },
  hub: {
    position: 'absolute',
    left: -HUB,
    top: -HUB,
    width: HUB * 2,
    height: HUB * 2,
    borderRadius: HUB,
    backgroundColor: '#333333',
  },
  dot: {
    position: 'absolute',
    left: -1.5,
    top: -1.5,
    width: 3,
    height: 3,
    borderRadius: 1.5,
    backgroundColor: '#ffffff',
  },
  text: {
    position: 'absolute',
    left: -60,
    width: 120,
    textAlign: 'center',
    color: '#111111',
  },
  digits: {
    top: -TEXT_Y - 14,
    height: 28,
    lineHeight: 28,
    fontSize: 22,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  caption: {
    top: TEXT_Y - 4 - 10,
    height: 20,
    lineHeight: 20,
    fontSize: 14,
  },
});
