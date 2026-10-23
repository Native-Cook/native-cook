import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

/** The fixed words on the sheet. */
type Labels = {
  title: string;
  subtitle: string;
  question: string;
  sleep: string;
  wake: string;
  to24: string;
  to12: string;
  submit: string;
};

type Props = {
  /** The hour of the day at the left end of the scale, 0…23. */
  startHour?: number;
  /** The first sleep and wake times, as hours of the day. */
  sleep?: number;
  wake?: number;
  labels?: Partial<Labels>;
  /** Shows 24-hour times at first. */
  use24Hours?: boolean;
  /** Moves the handles and the band by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the sleep and the wake hour (0…23) after every step. */
  onChange?: (sleep: number, wake: number) => void;
  /** Called with both hours when the button is tapped. */
  onSubmit?: (sleep: number, wake: number) => void;
  onClose?: () => void;
};

// What a drag holds: nothing, a handle, or the band between them.
const NONE = 0;
const SLEEP = 1;
const WAKE = 2;
const BAND = 3;

type Step = { grab: number } | { shift: number } | { release: true } | { format: true };

const WIDTH = 340;
const HEIGHT = 384;
const CORNER = 32;
const INK = '#2b2f36';
const HAIRLINE = 'rgba(43,47,54,0.1)';

const HEADER_Y = 42;
// The read-outs' centre, and how far each is from the middle of the sheet.
const READOUT_Y = 128;
const COLUMN = 76;
const READOUT_WIDTH = 130;
const ROLL = 26;
const FOOTER_Y = 351;

// The scale: 24 hours of `PITCH` points from `SCALE_LEFT`, the ticks centred on `TICK_Y`.
const SCALE_LEFT = 32;
const PITCH = 11.5;
const TICK_Y = 222;
const MINOR_TICK = 12;
const MAJOR_TICK = 20;
// The handles and the band, measured from the ticks' centre.
const HANDLE_TOP = -13;
const HANDLE_HEIGHT = 72;
const HANDLE_WIDTH = 4;
const BAND_TOP = 10;
const BAND_HEIGHT = 30;
// A drag that starts within this many points of a handle takes it.
const CATCH_DISTANCE = 14;
// The drag's box starts this far left of the scale.
const TOUCH_INSET = 24;

const HOLD_TIME = 0.12;
const ROLL_TIME = 0.15;
const FORMAT_TIME = 0.25;

const LOOP = 9;
// After a touch the loop waits this long, returns to the first times, and starts over once they have settled.
const RESUME_AFTER = 2;
const SETTLE = 0.8;

const STEP_SPRING = { mass: 1, stiffness: 600, damping: 34, reduceMotion: ReduceMotion.Never };
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// autoPlay: what happens when, in seconds from the start of a loop. A shift counts hours from where
// the held part was taken.
const SCRIPT: [number, Step][] = [
  [1.0, { grab: WAKE }],
  [1.15, { shift: -1 }],
  [1.4, { shift: -2 }],
  [1.7, { release: true }],
  [2.3, { grab: SLEEP }],
  [2.45, { shift: 1 }],
  [2.7, { shift: 2 }],
  [3.0, { release: true }],
  [3.6, { grab: BAND }],
  [3.75, { shift: 1 }],
  [4.0, { shift: 2 }],
  [4.25, { shift: 3 }],
  [4.55, { release: true }],
  [5.15, { grab: SLEEP }],
  [5.3, { shift: -1 }],
  [5.45, { shift: -2 }],
  [5.6, { shift: -3 }],
  [5.75, { shift: -4 }],
  [5.9, { shift: -5 }],
  [6.15, { release: true }],
  [6.5, { grab: WAKE }],
  [6.65, { shift: -1 }],
  [6.9, { release: true }],
  [7.4, { format: true }],
  [8.3, { format: true }],
];

const LABELS: Labels = {
  title: 'sleep',
  subtitle: 'timings',
  question: 'when did you sleep last night?',
  sleep: 'Sleep time',
  wake: 'Wake time',
  to24: 'Switch to 24h',
  to12: 'Switch to 12h',
  submit: 'Update sleep',
};

const TICKS = Array.from({ length: 25 }, (_, hour) => hour);
const MARKS = Array.from({ length: 7 }, (_, mark) => mark * 4);

function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function wrap(hour: number) {
  return ((hour % 24) + 24) % 24;
}

/** An hour of the day as a number and "AM" or "PM". */
function twelve(hour: number) {
  return [String(hour % 12 === 0 ? 12 : hour % 12), hour < 12 ? 'AM' : 'PM'];
}

function twentyFour(hour: number) {
  return `${hour < 10 ? '0' : ''}${hour}:00`;
}

/** Up in 80 ms, a 40 ms hold, back in 180 ms: a tap. */
function pulse() {
  const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
  return withSequence(withTiming(1, { duration: 80, ...linear }), withDelay(40, withTiming(0, { duration: 180, ...linear })));
}

type SlotProps = {
  /** The time shown, in hours from the scale's left end; between two hours it is part-way through a roll. */
  value: SharedValue<number>;
  /** 0 this slot shows the even positions, 1 the odd ones. */
  parity: number;
  startHour: number;
  /** 0 twelve-hour times … 1 twenty-four-hour times. */
  format: SharedValue<number>;
};

/**
 * One slot of a read-out's drum. It shows the hour of its parity nearest to `value`: at that hour it
 * is in place, and as `value` moves on it slides up (or down, going back) and fades while the other
 * slot arrives. Its text only changes while it is out of sight.
 */
function Slot({ value, parity, startHour, format }: SlotProps) {
  const [shown, setShown] = useState(() => 2 * Math.round((value.get() - parity) / 2) + parity);

  useAnimatedReaction(
    () => 2 * Math.round((value.get() - parity) / 2) + parity,
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );

  const style = useAnimatedStyle(() => {
    const away = value.get() - (2 * Math.round((value.get() - parity) / 2) + parity);
    return { opacity: 1 - Math.min(1, Math.abs(away)), transform: [{ translateY: -away * ROLL }] };
  });
  const twelveStyle = useAnimatedStyle(() => ({ opacity: 1 - format.get() }));
  const twentyFourStyle = useAnimatedStyle(() => ({ opacity: format.get() }));

  const hour = wrap(startHour + clamp(shown, 0, 24));
  const [number, half] = twelve(hour);

  return (
    <Animated.View style={[styles.slot, style]}>
      <Animated.View style={[styles.time, twelveStyle]}>
        <Text style={styles.number}>{number}</Text>
        <Text style={styles.half}>{half}</Text>
      </Animated.View>
      <Animated.View style={[styles.time, twentyFourStyle]}>
        <Text style={styles.clock}>{twentyFour(hour)}</Text>
      </Animated.View>
    </Animated.View>
  );
}

type ReadoutProps = Omit<SlotProps, 'parity'> & {
  icon: 'bed' | 'alarm';
  label: string;
  /** The read-out's centre, from the sheet's left edge. */
  x: number;
};

/** An icon, a label and the time, which is a drum of two slots. */
function Readout({ icon, label, x, ...slot }: ReadoutProps) {
  return (
    <View style={[styles.readout, { left: x - READOUT_WIDTH / 2 }]} pointerEvents="none">
      <Ionicons name={icon} size={22} color="#9a9fa8" style={styles.icon} />
      <Text style={styles.caption} numberOfLines={1}>
        {label}
      </Text>
      <Slot parity={0} {...slot} />
      <Slot parity={1} {...slot} />
    </View>
  );
}

/**
 * A sheet that asks when you slept. A scale of 24 hours, one tick an hour, carries two tall
 * handles with a band of colour between them: dragging a handle moves the sleep or the wake time
 * hour by hour, dragging the band moves both, and the two large read-outs above roll like an
 * odometer with every step. A chip switches the read-outs and the axis between 12 and 24 hours.
 */
export function SleepRangeScale({
  startHour = 20,
  sleep = 23,
  wake = 7,
  labels,
  use24Hours = false,
  autoPlay = false,
  onChange,
  onSubmit,
  onClose,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still;
  const words = { ...LABELS, ...labels };

  // The first positions on the scale, in hours from its left end: sleep 0…23, wake after it and at most 24.
  const firstSleep = wrap(sleep - startHour);
  const wakeOffset = wrap(wake - startHour);
  const firstWake = wakeOffset > firstSleep ? wakeOffset : Math.min(24, Math.max(firstSleep + 1, wakeOffset === 0 ? 24 : wakeOffset));

  // Where the two handles are drawn.
  const sleepAt = useSharedValue(firstSleep);
  const wakeAt = useSharedValue(firstWake);
  // What the two read-outs show: between two hours they are part-way through a roll.
  const sleepShown = useSharedValue(firstSleep);
  const wakeShown = useSharedValue(firstWake);
  // 0 at rest … 1 held, for each handle.
  const sleepHeld = useSharedValue(0);
  const wakeHeld = useSharedValue(0);
  const format = useSharedValue(use24Hours ? 1 : 0);
  const chipPress = useSharedValue(0);
  const submitPress = useSharedValue(0);

  // The hours themselves, and the drag: what it holds, the hours when it took hold, and whether the
  // finger has moved a step since. They live on the UI thread, where the drag is followed.
  const sleepHour = useSharedValue(firstSleep);
  const wakeHour = useSharedValue(firstWake);
  const held = useSharedValue(NONE);
  const fromSleep = useSharedValue(firstSleep);
  const fromWake = useSharedValue(firstWake);
  const moved = useSharedValue(false);

  const [span, setSpan] = useState(firstWake - firstSleep);
  const [is24, setIs24] = useState(use24Hours);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  const shows24 = useRef(use24Hours);

  const touch = () => {
    setTouches((count) => count + 1);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const changed = (nextSleep: number, nextWake: number, tick: boolean) => {
    setSpan(nextWake - nextSleep);
    if (tick && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onChange?.(wrap(startHour + nextSleep), wrap(startHour + nextWake));
  };

  /** Takes hold of a handle or of the band, or lets go: what is held thickens. */
  const hold = (part: number) => {
    'worklet';
    if (part !== NONE) {
      fromSleep.set(sleepHour.get());
      fromWake.set(wakeHour.get());
    }
    held.set(part);
    const config = { duration: still ? 0 : HOLD_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
    sleepHeld.set(withTiming(part === SLEEP || part === BAND ? 1 : 0, config));
    wakeHeld.set(withTiming(part === WAKE || part === BAND ? 1 : 0, config));
  };

  const set = (nextSleep: number, nextWake: number, tick: boolean) => {
    'worklet';
    const lastSleep = sleepHour.get();
    const lastWake = wakeHour.get();
    if (nextSleep === lastSleep && nextWake === lastWake) return;
    const roll = { duration: still ? 0 : ROLL_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
    if (nextSleep !== lastSleep) {
      sleepHour.set(nextSleep);
      sleepAt.set(still ? nextSleep : withSpring(nextSleep, STEP_SPRING));
      sleepShown.set(withTiming(nextSleep, roll));
    }
    if (nextWake !== lastWake) {
      wakeHour.set(nextWake);
      wakeAt.set(still ? nextWake : withSpring(nextWake, STEP_SPRING));
      wakeShown.set(withTiming(nextWake, roll));
    }
    scheduleOnRN(changed, nextSleep, nextWake, tick);
  };

  /** Moves what is held to `by` hours from where it was taken, as far as the scale and the other handle allow. */
  const shift = (by: number, tick: boolean) => {
    'worklet';
    const part = held.get();
    if (part === NONE) return;
    let nextSleep = fromSleep.get();
    let nextWake = fromWake.get();
    if (part === SLEEP) nextSleep = clamp(nextSleep + by, 0, nextWake - 1);
    else if (part === WAKE) nextWake = clamp(nextWake + by, nextSleep + 1, 24);
    else {
      const along = clamp(by, -nextSleep, 24 - nextWake);
      nextSleep += along;
      nextWake += along;
    }
    set(nextSleep, nextWake, tick);
  };

  /** Back to the first times, unless a finger still holds the scale. */
  const restore = () => {
    'worklet';
    if (held.get() === NONE) set(firstSleep, firstWake, false);
  };

  const toggleFormat = () => {
    const next = !shows24.current;
    shows24.current = next;
    setIs24(next);
    chipPress.set(pulse());
    format.set(withTiming(next ? 1 : 0, { duration: still ? 0 : FORMAT_TIME * 1000, easing: Easing.inOut(Easing.quad), reduceMotion: ReduceMotion.Never }));
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ hold, shift, restore, toggleFormat });
  useEffect(() => {
    steps.current = { hold, shift, restore, toggleFormat };
  });

  // The script a finger would follow: the wake handle in, the sleep handle in, the band along, both
  // handles back, and the chip there and back. The scale's steps run on the UI thread, like a drag's.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const [at, step] = SCRIPT[n % SCRIPT.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + at) * 1000;
      timer = setTimeout(() => {
        if ('grab' in step) scheduleOnUI(steps.current.hold, step.grab);
        else if ('shift' in step) scheduleOnUI(steps.current.shift, step.shift, false);
        else if ('release' in step) scheduleOnUI(steps.current.hold, NONE);
        else steps.current.toggleFormat();
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the first times, and start over once they have settled.
      timer = setTimeout(() => {
        scheduleOnUI(steps.current.restore);
        if (shows24.current !== use24Hours) steps.current.toggleFormat();
        run(Date.now() + SETTLE * 1000, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, use24Hours]);

  // One drag over the scale. It takes the handle it starts near, or the band when it starts between
  // the handles; a tap moves the nearer handle to the tapped hour.
  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const at = (event.x - TOUCH_INSET) / PITCH;
      const toSleep = Math.abs(at - sleepHour.get());
      const toWake = Math.abs(at - wakeHour.get());
      const near = Math.min(toSleep, toWake) * PITCH <= CATCH_DISTANCE;
      const inside = at > sleepHour.get() && at < wakeHour.get();
      moved.set(false);
      hold(inside && !near ? BAND : toSleep <= toWake ? SLEEP : WAKE);
      scheduleOnRN(touch);
    })
    .onUpdate((event) => {
      const by = Math.round(event.translationX / PITCH);
      if (by !== 0) moved.set(true);
      shift(by, true);
    })
    .onFinalize((event) => {
      const part = held.get();
      if (part === NONE) return;
      if (!moved.get() && part !== BAND && Math.abs(event.translationX) < 4) {
        const hour = Math.round((event.x - event.translationX - TOUCH_INSET) / PITCH);
        shift(hour - (part === SLEEP ? fromSleep.get() : fromWake.get()), true);
      }
      hold(NONE);
      scheduleOnRN(touch);
    });

  const bandStyle = useAnimatedStyle(() => ({
    left: SCALE_LEFT + sleepAt.get() * PITCH,
    width: Math.max(0, (wakeAt.get() - sleepAt.get()) * PITCH),
  }));
  const sleepStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: sleepAt.get() * PITCH },
      { scaleX: (HANDLE_WIDTH + 2 * sleepHeld.get()) / HANDLE_WIDTH },
      { scaleY: (HANDLE_HEIGHT + 6 * sleepHeld.get()) / HANDLE_HEIGHT },
    ],
  }));
  const wakeStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: wakeAt.get() * PITCH },
      { scaleX: (HANDLE_WIDTH + 2 * wakeHeld.get()) / HANDLE_WIDTH },
      { scaleY: (HANDLE_HEIGHT + 6 * wakeHeld.get()) / HANDLE_HEIGHT },
    ],
  }));
  const twelveStyle = useAnimatedStyle(() => ({ opacity: 1 - format.get() }));
  const twentyFourStyle = useAnimatedStyle(() => ({ opacity: format.get() }));
  const chipStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.05 * chipPress.get() }] }));
  const submitStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.04 * submitPress.get() }] }));

  return (
    <View style={styles.root}>
      <LinearGradient
        style={styles.sheet}
        colors={['#f1edfb', '#fbfbfe', '#e9f2fb', '#f7eaf2']}
        locations={[0, 0.35, 0.7, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      />
      <View style={styles.grabber} />

      <View style={styles.heading}>
        <Text style={styles.title} numberOfLines={1}>
          {words.title}
        </Text>
        <Text style={styles.subtitle} numberOfLines={1}>
          {words.subtitle}
        </Text>
      </View>
      <View style={styles.divider} />
      <Text style={styles.question} numberOfLines={1}>
        {words.question}
      </Text>
      <Pressable
        style={styles.close}
        accessibilityRole="button"
        accessibilityLabel="Close"
        onPress={() => {
          touch();
          onClose?.();
        }}
      >
        <Ionicons name="close-circle-outline" size={26} color={INK} />
      </Pressable>
      <View style={[styles.rule, { top: HEADER_Y + 29 }]} />

      <Readout icon="bed" label={words.sleep} x={WIDTH / 2 - COLUMN} value={sleepShown} startHour={startHour} format={format} />
      <Readout icon="alarm" label={words.wake} x={WIDTH / 2 + COLUMN} value={wakeShown} startHour={startHour} format={format} />
      <View style={styles.between} />
      <Text style={styles.span} numberOfLines={1}>
        ~{span} {span === 1 ? 'hour' : 'hours'} of sleep
      </Text>

      {TICKS.map((hour) => {
        const height = hour % 4 === 0 ? MAJOR_TICK : MINOR_TICK;
        return <View key={hour} style={[styles.tick, { left: SCALE_LEFT + hour * PITCH - 0.5, top: TICK_Y - height / 2, height }]} />;
      })}
      <Animated.View style={[styles.band, bandStyle]}>
        <LinearGradient style={StyleSheet.absoluteFill} colors={['#a9b8ff', '#c9a5f5']} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} />
      </Animated.View>
      <Animated.View style={[styles.handle, sleepStyle]} />
      <Animated.View style={[styles.handle, wakeStyle]} />

      <Animated.View style={[styles.axis, twelveStyle]} pointerEvents="none">
        {MARKS.map((mark) => (
          <Text key={mark} style={[styles.mark, { left: SCALE_LEFT + mark * PITCH - 20 }]} numberOfLines={1}>
            {twelve(wrap(startHour + mark)).join(' ')}
          </Text>
        ))}
      </Animated.View>
      <Animated.View style={[styles.axis, twentyFourStyle]} pointerEvents="none">
        {MARKS.map((mark) => (
          <Text key={mark} style={[styles.mark, { left: SCALE_LEFT + mark * PITCH - 20 }]} numberOfLines={1}>
            {twentyFour(wrap(startHour + mark))}
          </Text>
        ))}
      </Animated.View>

      <GestureDetector gesture={pan}>
        <View style={styles.touch} accessibilityRole="adjustable" accessibilityLabel={`${words.sleep}, ${words.wake}`} />
      </GestureDetector>

      <View style={[styles.rule, { top: FOOTER_Y - 34 }]} />
      <Animated.View style={[styles.chip, chipStyle]}>
        <Pressable
          style={styles.chipFace}
          accessibilityRole="button"
          onPress={() => {
            touch();
            toggleFormat();
          }}
        >
          <Text style={styles.chipLabel} numberOfLines={1}>
            {is24 ? words.to12 : words.to24}
          </Text>
        </Pressable>
      </Animated.View>
      <Animated.View style={[styles.submit, submitStyle]}>
        <Pressable
          style={styles.submitFace}
          accessibilityRole="button"
          onPress={() => {
            touch();
            submitPress.set(pulse());
            onSubmit?.(wrap(startHour + sleepHour.get()), wrap(startHour + wakeHour.get()));
          }}
        >
          <Ionicons name="arrow-up-circle-outline" size={22} color="#ffffff" />
          <Text style={styles.submitLabel} numberOfLines={1}>
            {words.submit}
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
    borderRadius: CORNER,
    borderCurve: 'continuous',
    boxShadow: '0 12 44 0 rgba(28,27,51,0.18)',
  },
  sheet: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: CORNER,
    borderCurve: 'continuous',
  },
  grabber: {
    position: 'absolute',
    left: WIDTH / 2 - 18,
    top: 9,
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#b9bcc6',
  },
  heading: {
    position: 'absolute',
    left: 20,
    top: HEADER_Y - 20,
    width: 60,
    height: 40,
    justifyContent: 'center',
  },
  title: {
    color: INK,
    fontSize: 20,
    lineHeight: 24,
    fontWeight: '700',
  },
  subtitle: {
    marginTop: -1,
    paddingLeft: 7,
    color: '#8a8f98',
    fontSize: 9,
    lineHeight: 11,
  },
  divider: {
    position: 'absolute',
    left: 83.5,
    top: HEADER_Y - 11,
    width: 1,
    height: 22,
    backgroundColor: 'rgba(43,47,54,0.45)',
  },
  question: {
    position: 'absolute',
    left: 98,
    top: HEADER_Y - 10,
    width: 190,
    height: 20,
    lineHeight: 20,
    color: '#6b7078',
    fontSize: 11,
    fontWeight: '600',
  },
  close: {
    position: 'absolute',
    left: WIDTH - 31 - 20,
    top: HEADER_Y - 20,
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rule: {
    position: 'absolute',
    left: 20,
    width: WIDTH - 40,
    height: 1,
    backgroundColor: HAIRLINE,
  },
  readout: {
    position: 'absolute',
    top: READOUT_Y - 50,
    width: READOUT_WIDTH,
    height: 100,
    alignItems: 'center',
  },
  icon: {
    position: 'absolute',
    top: -1,
  },
  caption: {
    position: 'absolute',
    top: 24,
    width: READOUT_WIDTH,
    height: 12,
    lineHeight: 12,
    color: '#8a8f98',
    fontSize: 9,
    fontWeight: '500',
    textAlign: 'center',
  },
  slot: {
    position: 'absolute',
    left: 0,
    top: 40,
    width: READOUT_WIDTH,
    height: 40,
  },
  time: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'center',
    gap: 4,
  },
  number: {
    color: INK,
    fontSize: 34,
    lineHeight: 40,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  },
  half: {
    color: INK,
    fontSize: 14,
    fontWeight: '500',
  },
  clock: {
    color: INK,
    fontSize: 30,
    lineHeight: 40,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  },
  between: {
    position: 'absolute',
    left: WIDTH / 2 - 0.5,
    top: READOUT_Y + 8 - 22,
    width: 1,
    height: 44,
    backgroundColor: HAIRLINE,
  },
  span: {
    position: 'absolute',
    left: 0,
    top: READOUT_Y + 57 - 8,
    width: WIDTH,
    height: 16,
    lineHeight: 16,
    color: '#8a8f98',
    fontSize: 11,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
  tick: {
    position: 'absolute',
    width: 1,
    backgroundColor: '#b8bcc6',
  },
  band: {
    position: 'absolute',
    top: TICK_Y + BAND_TOP,
    height: BAND_HEIGHT,
    overflow: 'hidden',
  },
  // At the scale's left end; it is moved along the scale.
  handle: {
    position: 'absolute',
    left: SCALE_LEFT - HANDLE_WIDTH / 2,
    top: TICK_Y + HANDLE_TOP,
    width: HANDLE_WIDTH,
    height: HANDLE_HEIGHT,
    borderRadius: HANDLE_WIDTH / 2,
    backgroundColor: INK,
  },
  axis: {
    position: 'absolute',
    left: 0,
    top: TICK_Y + 74 - 7,
    width: WIDTH,
    height: 14,
  },
  mark: {
    position: 'absolute',
    top: 0,
    width: 40,
    height: 14,
    lineHeight: 14,
    color: '#9a9fa8',
    fontSize: 9,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
  touch: {
    position: 'absolute',
    left: SCALE_LEFT - TOUCH_INSET,
    top: TICK_Y + 26 - 48,
    width: PITCH * 24 + TOUCH_INSET * 2,
    height: 96,
  },
  chip: {
    position: 'absolute',
    left: 20,
    top: FOOTER_Y - 15,
    width: 104,
    height: 30,
  },
  chipFace: {
    flex: 1,
    borderRadius: 8,
    backgroundColor: '#ecedef',
    alignItems: 'center',
    justifyContent: 'center',
  },
  chipLabel: {
    color: '#3a4048',
    fontSize: 11,
    fontWeight: '600',
  },
  submit: {
    position: 'absolute',
    left: WIDTH - 20 - 140,
    top: FOOTER_Y - 21,
    width: 140,
    height: 42,
  },
  submitFace: {
    flex: 1,
    flexDirection: 'row',
    gap: 8,
    borderRadius: 10,
    backgroundColor: '#3a4048',
    alignItems: 'center',
    justifyContent: 'center',
  },
  submitLabel: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
});
