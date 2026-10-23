import { Canvas, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Easing, ReduceMotion, cancelAnimation, useDerivedValue, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';

import {
  BURST,
  CLIMB_AT,
  DARK,
  FADE,
  GOAL_HOP,
  HOP,
  LOOP,
  NONE,
  RESET_AT,
  STAGE_HEIGHT,
  STAGE_WIDTH,
  STAIRS_AT,
  STAIR_GAP,
  STAIR_IN,
  USER_HOLD,
  freshPlot,
  plotAt,
  stagePicture,
  words,
} from './Stage';

type Props = {
  /** The words on the stairs, from the lowest step up. */
  steps?: string[];
  goalLabel?: string;
  background?: string;
  /** Plays the whole climb by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the place in `steps` of the label the ball lands on. */
  onStep?: (index: number) => void;
  /** Called when the ball reaches the goal. */
  onGoal?: () => void;
};

const STEPS = ['PLAN', 'ACTION', 'FOCUS', 'PATIENCE', 'DISCIPLINE'];
const BACKGROUND = '#010101';

// As long as anyone will watch: the scene's clock.
const FOREVER = 3600;
// The clock stops this long after the last thing that moves, until the next touch.
const IDLE = 1;
const INTRO = 3;

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function arrived() {
  if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

/**
 * A black stage with two lights: a glowing ball at the lower left and a goal at the upper right.
 * The ball wonders how to get there; a tap calls a staircase of words, each further tap makes the
 * ball hop one step up (a long press runs the rest), every label lights as the ball lands on it,
 * and on the last hop the goal bursts. A tap after that fades everything out and starts again.
 */
export function GoalStaircaseGlow({ steps = STEPS, goalLabel = 'GOAL', background = BACKGROUND, autoPlay = false, onStep, onGoal }: Props) {
  const still = useReducedMotion();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const scale = Math.min(size.width / STAGE_WIDTH, size.height / STAGE_HEIGHT);
  const count = steps.length;

  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The scene's clock in seconds; it stands still while nothing moves.
  const clock = useSharedValue(0);
  const plot = useSharedValue(freshPlot(0, count));
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const idle = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const running = useRef(false);

  const labels = useMemo(() => steps.map((step) => words(step, 'rgba(255,255,255,0.6)', '#111111', 10, 700, 0.8)), [steps]);
  const goal = useMemo(() => words(goalLabel, '#FFFFFF', '#FED01B', 15, 600, 1.5), [goalLabel]);

  /** Keeps the clock running for the next `seconds`, and for good while autoPlay is on. */
  const wake = (seconds: number) => {
    if (!running.current) {
      running.current = true;
      clock.set(withTiming(clock.get() + FOREVER, { duration: FOREVER * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    }
    clearTimeout(idle.current);
    if (autoPlay) return;
    idle.current = setTimeout(
      () => {
        running.current = false;
        cancelAnimation(clock);
      },
      (seconds + IDLE) * 1000,
    );
  };

  const later = (seconds: number, run: () => void) => timers.current.push(setTimeout(run, Math.max(0, seconds) * 1000));

  useEffect(() => {
    const pending = timers.current;
    return () => {
      pending.forEach(clearTimeout);
      clearTimeout(idle.current);
      cancelAnimation(clock);
    };
  }, [clock]);

  /**
   * Everything fades to black, and after a dark moment the scene appears again. Returns when, on
   * the clock: that is also the answer while a fade or the dark is already under way.
   */
  const reset = () => {
    const t = clock.get();
    const now = plotAt(plot.get(), t);
    if (now.fade < NONE) return now.fade + FADE + DARK;
    if (t < now.born) return now.born;
    timers.current.forEach(clearTimeout);
    timers.current = [];
    plot.set({ ...now, fade: t });
    wake(FADE + DARK + INTRO);
    return t + FADE + DARK;
  };

  /** The next thing: the stairs, then a hop (`all`: every hop that is left), then starting over. */
  const act = (all: boolean, byHand: boolean) => {
    const t = clock.get();
    let now = plotAt(plot.get(), t);
    if (now.fade < NONE || t < now.born) return;
    if (now.stairs === NONE) {
      // The first touch calls the stairs; a long press goes on to climb them as they arrive.
      now = { ...now, stairs: t };
      if (!all) {
        plot.set(now);
        wake(count * STAIR_GAP + STAIR_IN);
        return;
      }
    }
    const hops = now.hops.slice();
    let next = hops.indexOf(NONE);
    if (next < 0) {
      // The climb is over: a touch after the burst starts again.
      if (t > hops[count] + GOAL_HOP + BURST) reset();
      return;
    }
    do {
      // A hop waits for the one before it to land, and for its label to be there.
      const free = next === 0 ? 0 : hops[next - 1] + HOP;
      const ready = next < count ? now.stairs + next * STAIR_GAP + STAIR_IN : 0;
      const from = Math.max(t, free, ready);
      hops[next] = from;
      const landing = from + (next < count ? HOP : GOAL_HOP) - t;
      const index = next;
      later(landing, () => {
        // The loop that plays by itself is silent.
        if (index < count) {
          if (byHand) tap();
          onStep?.(index);
        } else {
          if (byHand) arrived();
          onGoal?.();
        }
      });
      next++;
    } while (all && next <= count);
    plot.set({ ...now, hops });
    wake(hops[next - 1] + GOAL_HOP + BURST - t);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ act, reset, wake });
  useEffect(() => {
    latest.current = { act, reset, wake };
  });

  // The scene appears by itself; by hand the clock then rests until the first touch.
  useEffect(() => {
    latest.current.wake(INTRO);
  }, [autoPlay]);

  // Another number of steps is another staircase: the scene starts over.
  useEffect(() => {
    if (plot.get().hops.length === count + 1) return;
    timers.current.forEach(clearTimeout);
    timers.current = [];
    plot.set(freshPlot(clock.get(), count));
    latest.current.wake(INTRO);
  }, [plot, clock, count]);

  useEffect(() => {
    if (!autoPlay) return;
    const queue: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => queue.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      after(origin + STAIRS_AT * 1000 - Date.now(), () => latest.current.act(false, false));
      after(origin + CLIMB_AT * 1000 - Date.now(), () => latest.current.act(true, false));
      after(origin + RESET_AT * 1000 - Date.now(), () => latest.current.reset());
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        // The loop starts over from the dark, when the scene appears again.
        const born = latest.current.reset();
        after(Math.max(0, born - clock.get()) * 1000, () => cycle(Date.now()));
      });
    return () => queue.forEach(clearTimeout);
  }, [autoPlay, touches, clock]);

  const picture = useDerivedValue(() => stagePicture(size.width, size.height, scale, clock.get(), plot.get(), labels, goal, still));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const press = (all: boolean) => {
    // A touch takes over from the autoPlay loop, which starts again from the dark a while later.
    setTouches((n) => n + 1);
    tap();
    act(all, true);
  };

  return (
    <View style={[styles.screen, { backgroundColor: background }]} onLayout={onLayout}>
      <StatusBar style="light" />
      <Pressable
        style={StyleSheet.absoluteFill}
        onPress={() => press(false)}
        onLongPress={() => press(true)}
        accessibilityRole="button"
        accessibilityLabel={`${goalLabel}: ${steps.join(', ')}`}
        accessibilityHint="Takes the next step"
      >
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
});
