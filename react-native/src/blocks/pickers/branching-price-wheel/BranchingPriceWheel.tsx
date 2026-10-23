import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = {
  /** The lowest and the highest amount. The column lists every ten between them. */
  min?: number;
  max?: number;
  /** The amount selected at first. */
  initial?: number;
  /** Written after each amount. */
  currency?: string;
  saveLabel?: string;
  /** Slides the units and turns the column by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the amount after every step. */
  onChange?: (amount: number) => void;
  /** Called with the amount when Save is tapped. */
  onSave?: (amount: number) => void;
  onClose?: () => void;
};

type Step = { unit: number } | { ten: number };

const WIDTH = 320;
const HEIGHT = 500;
const CORNER = 36;
const NAVY = '#1b2540';
const GREEN = '#10d48a';
const PALE = '#cbd5e0';

// The row in the middle, and the distance between rows and between units.
const CENTER_Y = 250;
const ROW_PITCH = 50;
const UNIT_PITCH = 76;
// The left edge of the column's amounts, and of the large one in the shared cell.
const COLUMN_X = 70;
const CELL_X = 38;
const CELL_WIDTH = 140;
const CELL_HEIGHT = 40;
const SMALL_SIZE = 15;
const LARGE_SIZE = 28;
const CLOSE_X = 286;
const CLOSE_Y = 34;
const SAVE_X = 252;
const SAVE_Y = 446;
const SAVE_WIDTH = 84;
const SAVE_HEIGHT = 36;

// A drag picks its wheel after this many points, and coasts for this long on the finger's speed.
const SLOP = 8;
const COAST = 0.18;
const DIMMED = 0.3;
const DIM_TIME = 0.2;
const FOLD_TIME = 0.12;
// The branch shoots out this long after the column is let go: 250 ms a number, 40 ms apart.
const BRANCH_AFTER = 0.3;
const BRANCH_RISE = 0.25;
const BRANCH_STAGGER = 0.04;
const BRANCH_TIME = BRANCH_RISE + BRANCH_STAGGER * 3;
const BRANCH_SLIDE = 20;
// The column is lit again this long after the units are let go.
const SNAP_TIME = 0.45;
const SAVED_TIME = 1;

const LOOP = 8.5;
// After a touch the loop waits this long, returns to the first amount, and starts over once it has settled.
const RESUME_AFTER = 2;
const SETTLE = 1;

// Response 0.4 s, damping ratio 0.8.
const SNAP_SPRING = { mass: 1, stiffness: 247, damping: 25, reduceMotion: ReduceMotion.Never };
const POP_SPRING = { mass: 1, stiffness: 400, damping: 18, reduceMotion: ReduceMotion.Never };
const IN_OUT = Easing.inOut(Easing.quad);

// The drag's wheel: none yet, the column, or the branch.
const NONE = 0;
const COLUMN = 1;
const BRANCH = 2;

const UNITS = [1, 2, 3, 4, 5, 6, 7, 8, 9];

function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Past either end a wheel follows the finger at a third of its speed. */
function rubber(position: number, last: number) {
  'worklet';
  if (position < 0) return position / 3;
  if (position > last) return last + (position - last) / 3;
  return position;
}

/**
 * How far out the branch's number in `place` (1 next to the selection) is, 0…1, when the branch as
 * a whole is at `branch`: each number sets off 40 ms after the one before it and takes 250 ms.
 */
function outAt(branch: number, place: number) {
  'worklet';
  const t = clamp01((branch * BRANCH_TIME - BRANCH_STAGGER * (place - 1)) / BRANCH_RISE);
  return 1 - Math.pow(1 - t, 3);
}

type Motion = {
  /** The column's position: the index of the ten in the middle, fractional while it turns. */
  tens: SharedValue<number>;
  /** The branch's position: the unit in the shared cell, 0 for the ten itself. */
  units: SharedValue<number>;
  /** 0 the branch folded into the selected amount … 1 out. */
  branch: SharedValue<number>;
  /** The opacity of the rest of the column: it dims while the units move. */
  dim: SharedValue<number>;
  /** The selected ten's place in the column, and the selected unit. */
  ten: SharedValue<number>;
  unit: SharedValue<number>;
};

/**
 * A row of the column: an amount set large and scaled down, its heavy face lying on its hairline face
 * in pale grey. The selected row is also the branch's first number: the units push it out to the left.
 */
function Row({ index, label, tens, units, dim, ten }: Motion & { index: number; label: string }) {
  const box = useAnimatedStyle(() => {
    const away = index - tens.get();
    const near = clamp01(1 - Math.abs(away));
    const selected = index === ten.get();
    const pushed = selected ? units.get() : 0;
    return {
      opacity: clamp01(1 - pushed) * clamp01((4.7 - Math.abs(away)) / 0.7) * (selected ? 1 : dim.get()),
      transform: [
        { translateX: mix(COLUMN_X, CELL_X, near) - pushed * UNIT_PITCH },
        { translateY: CENTER_Y + away * ROW_PITCH - CELL_HEIGHT / 2 },
        { scale: mix(SMALL_SIZE / LARGE_SIZE, 1, near) },
      ],
    };
  });
  const thin = useAnimatedStyle(() => ({ opacity: 1 - clamp01(1 - (index === ten.get() ? units.get() : 0)) }));
  const heavy = useAnimatedStyle(() => ({
    opacity: clamp01(1 - (index === ten.get() ? units.get() : 0)),
    color: interpolateColor(clamp01(1 - Math.abs(index - tens.get())), [0, 1], [NAVY, GREEN]),
  }));

  return (
    <Animated.View style={[styles.cell, box]} pointerEvents="none">
      <Animated.Text style={[styles.amount, styles.thin, thin]} numberOfLines={1}>
        {label}
      </Animated.Text>
      <Animated.Text style={[styles.amount, styles.heavy, heavy]} numberOfLines={1}>
        {label}
      </Animated.Text>
    </Animated.View>
  );
}

/** A number of the branch: it waits folded into the selection, and thickens as it reaches the shared cell. */
function Unit({ place, last, label, tens, units, branch, ten, unit }: Motion & { place: number; last: SharedValue<number>; label: string }) {
  const box = useAnimatedStyle(() => {
    const away = place - units.get();
    // Numbers right of the selection wait for the branch, one after the other.
    const ahead = place - unit.get();
    const out = ahead >= 1 ? outAt(branch.get(), ahead) : 1;
    const x = CELL_X + away * UNIT_PITCH - BRANCH_SLIDE * (1 - out);
    const seen = place <= last.get() ? out * (away < 0 ? clamp01(1 + away) : 1) * clamp01((WIDTH - 20 - x) / 60) : 0;
    return {
      opacity: seen,
      // The branch stays on the selected row while the column turns.
      transform: [{ translateX: x }, { translateY: CENTER_Y + (ten.get() - tens.get()) * ROW_PITCH - CELL_HEIGHT / 2 }],
    };
  });
  const thin = useAnimatedStyle(() => ({ opacity: 1 - clamp01(1 - Math.abs(place - units.get())) }));
  const heavy = useAnimatedStyle(() => ({ opacity: clamp01(1 - Math.abs(place - units.get())) }));

  return (
    <Animated.View style={[styles.cell, box]} pointerEvents="none">
      <Animated.Text style={[styles.amount, styles.thin, thin]} numberOfLines={1}>
        {label}
      </Animated.Text>
      <Animated.Text style={[styles.amount, styles.heavy, heavy]} numberOfLines={1}>
        {label}
      </Animated.Text>
    </Animated.View>
  );
}

function Check({ width, height, line }: { width: number; height: number; line: number }) {
  return (
    <Svg width={width + line} height={height + line}>
      <Path
        d={`M ${line / 2} ${line / 2 + height * 0.55} L ${line / 2 + width * 0.36} ${line / 2 + height} L ${line / 2 + width} ${line / 2}`}
        stroke="#ffffff"
        strokeWidth={line}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </Svg>
  );
}

/**
 * A price picker made of two wheels at right angles that share one cell. The tens run down a
 * column; the amount in the middle is large and green, and from it the units branch out to the
 * right in a hairline weight. Dragging up or down turns the column (the branch folds away while
 * it moves and shoots out again from the new ten); dragging sideways slides the units through the
 * shared cell, each one thickening and turning green as it arrives. Tapping an amount goes to it.
 */
export function BranchingPriceWheel({
  min = 30,
  max = 100,
  initial = 60,
  currency = '$',
  saveLabel = 'Save',
  autoPlay = false,
  onChange,
  onSave,
  onClose,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still;

  // The column lists every ten from `min`; the first amount is a place in it and a unit.
  const count = Math.max(1, Math.floor((max - min) / 10) + 1);
  const firstAmount = Math.min(Math.max(initial, min), max);
  const firstTen = Math.min(count - 1, Math.floor((firstAmount - min) / 10));
  const firstUnit = firstAmount - (min + firstTen * 10);

  const tens = useSharedValue(firstTen);
  const units = useSharedValue(firstUnit);
  const branch = useSharedValue(1);
  const dim = useSharedValue(1);
  const ten = useSharedValue(firstTen);
  const unit = useSharedValue(firstUnit);
  // The units the selected ten can take before the range ends.
  const last = useSharedValue(Math.min(9, max - (min + firstTen * 10)));
  // Save's scale, and 0 its label … 1 the large check.
  const pop = useSharedValue(1);
  const saved = useSharedValue(0);
  // The amount Save compares with: the first one, then the last one saved.
  const kept = useSharedValue(firstAmount);
  // The drag's wheel once it is known, and the position it started from.
  const axis = useSharedValue(NONE);
  const grabbed = useSharedValue(0);
  const motion: Motion = { tens, units, branch, dim, ten, unit };

  // The selected ten, for the branch's labels: it changes while the branch is folded.
  const [shownTen, setShownTen] = useState(firstTen);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  const savedTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const touch = () => {
    setTouches((n) => n + 1);
  };

  /** `tick` 1 is the column's haptic, 2 the softer one of the units. */
  const changed = (amount: number, place: number, tick: number) => {
    setShownTen(place);
    if (process.env.EXPO_OS !== 'web') {
      if (tick === 1) Haptics.selectionAsync().catch(() => {});
      if (tick === 2) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
    }
    onChange?.(amount);
  };

  const report = (tick: number) => {
    'worklet';
    const amount = min + ten.get() * 10 + unit.get();
    if (amount !== kept.get() && !still) {
      // Save dips and springs back when there is something new to keep.
      pop.set(withSequence(withTiming(0.9, { duration: 0, reduceMotion: ReduceMotion.Never }), withSpring(1, POP_SPRING)));
    }
    scheduleOnRN(changed, amount, ten.get(), tick);
  };

  const passUnit = (next: number, tick: boolean) => {
    'worklet';
    const to = clamp(next, 0, last.get());
    if (to === unit.get()) return;
    unit.set(to);
    report(tick ? 2 : 0);
  };

  const passTen = (next: number, tick: boolean) => {
    'worklet';
    const to = clamp(next, 0, count - 1);
    if (to === ten.get() && unit.get() === 0) return;
    ten.set(to);
    unit.set(0);
    last.set(clamp(max - (min + to * 10), 0, 9));
    report(tick ? 1 : 0);
  };

  /** Folds the branch into the selected amount; the units go back to the ten itself. */
  const fold = () => {
    'worklet';
    branch.set(withTiming(0, { duration: still ? 0 : FOLD_TIME * 1000, easing: IN_OUT, reduceMotion: ReduceMotion.Never }));
    if (units.get() !== 0) units.set(still ? 0 : withSpring(0, SNAP_SPRING));
    dim.set(withTiming(1, { duration: still ? 0 : DIM_TIME * 1000, easing: IN_OUT, reduceMotion: ReduceMotion.Never }));
  };

  /** Slides the branch until unit `next` is in the shared cell; the column is lit again once it has snapped. */
  const slide = (next: number, speed: number, tick: boolean) => {
    'worklet';
    passUnit(next, tick);
    units.set(still ? unit.get() : withSpring(unit.get(), { ...SNAP_SPRING, velocity: speed }));
    const fade = { duration: still ? 0 : DIM_TIME * 1000, easing: IN_OUT, reduceMotion: ReduceMotion.Never };
    dim.set(withSequence(withTiming(DIMMED, fade), withDelay((SNAP_TIME - DIM_TIME) * 1000, withTiming(1, fade))));
  };

  /** Turns the column until row `next` is in the middle; the branch shoots out once it has snapped. */
  const turn = (next: number, speed: number, tick: boolean) => {
    'worklet';
    fold();
    passTen(next, tick);
    tens.set(still ? ten.get() : withSpring(ten.get(), { ...SNAP_SPRING, velocity: speed }));
    if (still) {
      branch.set(withTiming(1, { duration: FOLD_TIME * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    } else {
      branch.set(
        withSequence(
          withTiming(0, { duration: FOLD_TIME * 1000, easing: IN_OUT, reduceMotion: ReduceMotion.Never }),
          withDelay(
            (BRANCH_AFTER - FOLD_TIME) * 1000,
            withTiming(1, { duration: BRANCH_TIME * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }),
          ),
        ),
      );
    }
  };

  /** Back to the first amount, unless a finger still holds a wheel. */
  const restore = () => {
    'worklet';
    if (axis.get() !== NONE) return;
    if (ten.get() !== firstTen) turn(firstTen, 0, false);
    slide(firstUnit, 0, false);
    kept.set(firstAmount);
  };

  /** Keeps the amount: the label gives way to a large check for a second. */
  const save = () => {
    const amount = min + ten.get() * 10 + unit.get();
    kept.set(amount);
    saved.set(still ? 1 : withSpring(1, POP_SPRING));
    clearTimeout(savedTimer.current);
    savedTimer.current = setTimeout(() => {
      saved.set(withTiming(0, { duration: still ? 0 : DIM_TIME * 1000, easing: IN_OUT, reduceMotion: ReduceMotion.Never }));
    }, SAVED_TIME * 1000);
    onSave?.(amount);
  };

  useEffect(() => () => clearTimeout(savedTimer.current), []);

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ slide, turn, restore });
  useEffect(() => {
    steps.current = { slide, turn, restore };
  });

  // The script a finger would follow: two units along and back, two tens down, one unit along and
  // back, and the column up again. The wheels' steps run on the UI thread, like a drag's.
  useEffect(() => {
    if (!auto) return;
    const far = Math.min(count - 1, firstTen + 2);
    const script: [number, Step][] = [
      [1.0, { unit: 1 }],
      [1.4, { unit: 2 }],
      [2.6, { unit: 0 }],
      [3.3, { ten: far }],
      [4.9, { unit: 1 }],
      [6.0, { unit: 0 }],
      [6.6, { ten: firstTen }],
    ];
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const [at, step] = script[n % script.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / script.length) * LOOP + at) * 1000;
      timer = setTimeout(() => {
        if ('unit' in step) scheduleOnUI(steps.current.slide, step.unit, 0, false);
        else scheduleOnUI(steps.current.turn, step.ten, 0, false);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the first amount, and start over once it has settled.
      timer = setTimeout(() => {
        scheduleOnUI(steps.current.restore);
        run(Date.now() + SETTLE * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, count, firstTen]);

  // One drag over the card. Its first few points decide the wheel: up and down turns the column,
  // sideways slides the branch.
  const pan = Gesture.Pan()
    .minDistance(SLOP)
    .onStart((event) => {
      const sideways = Math.abs(event.translationX) > Math.abs(event.translationY);
      axis.set(sideways ? BRANCH : COLUMN);
      scheduleOnRN(touch);
      if (sideways) {
        grabbed.set(units.get());
        dim.set(withTiming(DIMMED, { duration: still ? 0 : DIM_TIME * 1000, easing: IN_OUT, reduceMotion: ReduceMotion.Never }));
      } else {
        grabbed.set(tens.get());
        fold();
      }
    })
    .onUpdate((event) => {
      if (axis.get() === BRANCH) {
        const at = rubber(grabbed.get() - event.translationX / UNIT_PITCH, last.get());
        units.set(at);
        passUnit(Math.round(at), true);
      } else if (axis.get() === COLUMN) {
        const at = rubber(grabbed.get() - event.translationY / ROW_PITCH, count - 1);
        tens.set(at);
        passTen(Math.round(at), true);
      }
    })
    .onEnd((event) => {
      // The wheel coasts a little way on the finger's speed before it snaps.
      if (axis.get() === BRANCH) {
        const speed = -event.velocityX / UNIT_PITCH;
        slide(clamp(Math.round(units.get() + speed * COAST), 0, last.get()), speed, true);
      } else if (axis.get() === COLUMN) {
        const speed = -event.velocityY / ROW_PITCH;
        turn(clamp(Math.round(tens.get() + speed * COAST), 0, count - 1), speed, true);
      }
    })
    .onFinalize(() => {
      if (axis.get() === NONE) return;
      axis.set(NONE);
      scheduleOnRN(touch);
    });

  // A touch that does not move is a tap on the amount under it.
  const tap = Gesture.Tap().onEnd((event, success) => {
    if (!success) return;
    if (Math.abs(event.x - SAVE_X) < SAVE_WIDTH / 2 + 6 && Math.abs(event.y - SAVE_Y) < SAVE_HEIGHT / 2 + 6) return;
    if (Math.abs(event.x - CLOSE_X) < 22 && Math.abs(event.y - CLOSE_Y) < 22) return;
    if (Math.abs(event.y - CENTER_Y) < ROW_PITCH / 2) {
      scheduleOnRN(touch);
      slide(clamp(unit.get() + Math.round((event.x - CELL_X - 30) / UNIT_PITCH), 0, last.get()), 0, true);
    } else {
      const row = ten.get() + Math.round((event.y - CENTER_Y) / ROW_PITCH);
      if (row < 0 || row > count - 1) return;
      scheduleOnRN(touch);
      turn(row, 0, true);
    }
  });

  const saveStyle = useAnimatedStyle(() => ({ transform: [{ scale: pop.get() }] }));
  const labelStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(saved.get()) }));
  const checkStyle = useAnimatedStyle(() => ({ opacity: clamp01(saved.get()), transform: [{ scale: mix(0.5, 1, saved.get()) }] }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View style={styles.root}>
        <View style={styles.card}>
          {Array.from({ length: count }, (_, index) => (
            <Row key={index} index={index} label={`${min + index * 10}${currency}`} {...motion} />
          ))}
          {UNITS.map((place) => (
            <Unit key={place} place={place} last={last} label={`${min + shownTen * 10 + place}${currency}`} {...motion} />
          ))}

          <Pressable
            style={styles.close}
            accessibilityRole="button"
            accessibilityLabel="Close"
            onPress={() => {
              touch();
              onClose?.();
            }}
          >
            <View style={[styles.cross, { transform: [{ rotate: '45deg' }] }]} />
            <View style={[styles.cross, { transform: [{ rotate: '-45deg' }] }]} />
          </Pressable>

          <Animated.View style={[styles.save, saveStyle]}>
            <Pressable
              style={styles.saveFace}
              accessibilityRole="button"
              accessibilityLabel={saveLabel}
              onPress={() => {
                touch();
                save();
              }}
            >
              <Animated.View style={[styles.saveLook, labelStyle]}>
                <Text style={styles.saveLabel} numberOfLines={1}>
                  {saveLabel}
                </Text>
                <Check width={9} height={7} line={1.8} />
              </Animated.View>
              <Animated.View style={[styles.saveLook, checkStyle]}>
                <Check width={15} height={11} line={2.4} />
              </Animated.View>
            </Pressable>
          </Animated.View>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: CORNER,
    borderCurve: 'continuous',
    boxShadow: '0 18 56 0 rgba(143,163,184,0.28)',
  },
  card: {
    flex: 1,
    borderRadius: CORNER,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    overflow: 'hidden',
  },
  cell: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CELL_WIDTH,
    height: CELL_HEIGHT,
    transformOrigin: 'left center',
  },
  amount: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CELL_WIDTH,
    height: CELL_HEIGHT,
    lineHeight: CELL_HEIGHT,
    fontSize: LARGE_SIZE,
    fontVariant: ['tabular-nums'],
  },
  thin: {
    color: PALE,
    fontWeight: '200',
  },
  heavy: {
    color: GREEN,
    fontWeight: '800',
  },
  close: {
    position: 'absolute',
    left: CLOSE_X - 22,
    top: CLOSE_Y - 22,
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cross: {
    position: 'absolute',
    width: 15.5,
    height: 1.4,
    borderRadius: 0.7,
    backgroundColor: NAVY,
  },
  save: {
    position: 'absolute',
    left: SAVE_X - SAVE_WIDTH / 2,
    top: SAVE_Y - SAVE_HEIGHT / 2,
    width: SAVE_WIDTH,
    height: SAVE_HEIGHT,
  },
  saveFace: {
    flex: 1,
    borderRadius: SAVE_HEIGHT / 2,
    backgroundColor: GREEN,
  },
  saveLook: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    gap: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  saveLabel: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
});
