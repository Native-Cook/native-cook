import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { Fragment, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  type WithTimingConfig,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { FaceArt, fade, inOut, type Tones, type Travel, TravelIcon } from './RouteProgressCardArt';

type StopPhoto = {
  /** Address of the photo. */
  photo: string;
  /** The photo's main colour, shown until the photo arrives. */
  tint: string;
};

type Mode = Tones & {
  travel: Travel;
  name: string;
  distance: number;
  time: number;
  timeUnit: string;
  /** How far along the leg the marker stops, 0…1. */
  share: number;
  start: string;
  arrive: string;
  /** The next two stops, each with a small photo. */
  stops: string[];
  photos: StopPhoto[];
};

/** The fixed words on the card. */
type Labels = {
  from: string;
  to: string;
  go: string;
  done: string;
  next: string;
  distance: string;
  time: string;
  start: string;
  travelTime: string;
  arrive: string;
  nextStop: string;
  then: string;
};

type Props = {
  from?: string;
  to?: string;
  /** The three ways to travel, top to bottom. The route is followed from the last to the first. */
  modes?: Mode[];
  distanceUnit?: string;
  labels?: Partial<Labels>;
  /** Taps Go, steps through the legs and folds the card away by itself, in a loop. */
  autoPlay?: boolean;
  /** Called when Go opens the card. */
  onStart?: () => void;
  /** Called with the index of the leg that was opened. */
  onSelect?: (index: number) => void;
  /** Called when the card folds back into the two pills. */
  onFinish?: () => void;
};

const WIDTH = 350;
const HEIGHT = 356;

const CARD_W = 330;
const CARD_H = 204;
// Top of the open card: there is room for two pills above it and two below.
const CARD_Y = 76;
const CENTER_Y = CARD_Y + CARD_H / 2;
const CARD_RADIUS = 26;
// The two pills the card unfolds from, the capsule they merge into and the button between them.
const END_W = 128;
const CAPSULE_W = 236;
const CAPSULE_H = 44;
const PILL_GAP = 6;
const GO_W = 56;
// A waiting leg is a pill: wide next to the card, narrower one further away.
const PILL_H = 32;
const NEAR_W = 240;
const FAR_W = 184;
const GAP = 6;

// autoPlay (seconds): Go, then a new leg every `LEG_TIME`, then the card folds away.
const GO_IN = 0.35;
const GO_AT = 0.9;
const LEG_TIME = 2.3;
const LOOP = 10;
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

// Opening and closing, in seconds.
const GO_OUT = 0.16;
const MERGE_TIME = 0.22;
const OPEN_DELAY = 0.14;
// The marker sets off this long after its card starts to open, and travels for `PLAY_TIME`.
const PLAY_DELAY = 0.4;
const PLAY_TIME = 1.5;
const SPLIT_DELAY = 0.34;
const DONE_DELAY = 0.44;
const DONE_OUT = 1.25;
// From the end of the last leg until Go is back.
const CLOSE_TIME = 2.55;

const POP_SPRING = { mass: 1, stiffness: 320, damping: 18, reduceMotion: ReduceMotion.Never };
const OPEN_SPRING = { mass: 1, stiffness: 200, damping: 22, reduceMotion: ReduceMotion.Never };
const STACK_SPRING = { mass: 1, stiffness: 190, damping: 20, reduceMotion: ReduceMotion.Never };
const EASE_IN = Easing.bezier(0.5, 0, 1, 1);
const EASE_IN_OUT = Easing.bezier(0.45, 0, 0.55, 1);

// The figures count in this many steps.
const COUNT_STEPS = 50;

function photoUrl(id: string) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=102&h=102&q=80`;
}

const MODES: Mode[] = [
  {
    travel: 'fly',
    name: 'Flight',
    glow: '#0d9488',
    accent: '#5eead4',
    distance: 953,
    time: 3,
    timeUnit: 'hours',
    share: 0.8,
    start: '11:15 AM',
    arrive: '2:15 PM',
    stops: ['Alder Field', 'Coral Bay Airport'],
    photos: [
      { photo: photoUrl('1436491865332-7a61a109cc05'), tint: '#b4b7b8' },
      { photo: photoUrl('1506905925346-21bda4d32df4'), tint: '#6a6d7f' },
    ],
  },
  {
    travel: 'drive',
    name: 'Driving',
    glow: '#2563eb',
    accent: '#93c5fd',
    distance: 1090,
    time: 18,
    timeUnit: 'hours',
    share: 0.62,
    start: '10:40 AM',
    arrive: '4:40 AM',
    stops: ['Riverton', 'Lakeside'],
    photos: [
      { photo: photoUrl('1500530855697-b586d89ba3ee'), tint: '#6e6462' },
      { photo: photoUrl('1501594907352-04cda38ebc29'), tint: '#79949c' },
    ],
  },
  {
    travel: 'walk',
    name: 'Walking',
    glow: '#6d28d9',
    accent: '#a78bfa',
    distance: 2,
    time: 31,
    timeUnit: 'minutes',
    share: 0.28,
    start: '10:00 AM',
    arrive: '10:31 AM',
    stops: ['Linden Circle', 'Clock Tower'],
    photos: [
      { photo: photoUrl('1493976040374-85c8e12f0c0e'), tint: '#5b5353' },
      { photo: photoUrl('1583417319070-4a69db38a482'), tint: '#977b78' },
    ],
  },
];

const LABELS: Labels = {
  from: 'From',
  to: 'To',
  go: 'Go',
  done: 'Done',
  next: 'Next Step',
  distance: 'Distance',
  time: 'Time',
  start: 'Start',
  travelTime: 'Travel time',
  arrive: 'Arrive',
  nextStop: 'Next stop',
  then: 'Then',
};

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

function grouped(n: number) {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** The share `eased` of `total`: a whole number, except that totals under ten count in tenths. */
function amount(total: number, eased: number) {
  if (total >= 10) return grouped(Math.round(total * eased));
  return String(Math.round(total * eased * 10) / 10);
}

/**
 * Where leg `index`'s panel is. `active` is the open card's place in the list: as it slides from one
 * leg to the next, one card shrinks to a pill while the other grows, and the card's top stays put.
 * At `open` 0 every panel is the capsule the two pills merged into.
 */
function panelLayout(index: number, open: number, active: number) {
  'worklet';
  let top = CARD_Y - active * (PILL_H + GAP);
  let width = 0;
  let height = 0;
  let expand = 0;
  for (let i = 0; i <= index; i++) {
    if (i > 0) top += height + GAP;
    const away = Math.abs(i - active);
    // A little past 1 near the card, so the spring's overshoot does not squeeze it.
    expand = clamp01((1 - away) * 1.1);
    width = mix(mix(NEAR_W, FAR_W, clamp01(away - 1)), CARD_W, expand);
    height = mix(PILL_H, CARD_H, expand);
  }
  return {
    x: WIDTH / 2 - mix(CAPSULE_W, width, open) / 2,
    y: mix(CENTER_Y - CAPSULE_H / 2, top, open),
    width: mix(CAPSULE_W, width, open),
    height: mix(CAPSULE_H, height, open),
    radius: mix(CAPSULE_H / 2, mix(PILL_H / 2, CARD_RADIUS, expand), open),
    expand,
  };
}

/** The panels' contents show once the capsule has grown about halfway into the card. */
function seenAt(open: number) {
  'worklet';
  return clamp01((open - 0.45) / 0.4);
}

/** Up in 80 ms, a 40 ms hold, back in 180 ms: a tap. */
function pulse() {
  const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
  return withSequence(withTiming(1, { duration: 80, ...linear }), withDelay(40, withTiming(0, { duration: 180, ...linear })));
}

type Motion = {
  /** 0 two pills … 1 merged into one capsule. */
  merge: SharedValue<number>;
  /** 0 that capsule … 1 the open card with the other legs around it. */
  open: SharedValue<number>;
  /** Position in the list of the leg whose card is open; it slides when another is picked. */
  active: SharedValue<number>;
  /** A clock for the road. */
  seconds: SharedValue<number>;
  nextPress: SharedValue<number>;
  still: boolean;
};

/** The leg whose marker sets off after `delay` seconds; `tapped` also dips its pill. */
type Play = { leg: number; delay: number; tapped: boolean };

type PanelProps = Motion & {
  index: number;
  mode: Mode;
  labels: Labels;
  distanceUnit: string;
  /** This leg's card is the open one. */
  shown: boolean;
  play: Play;
  onSelect: () => void;
  onNext: () => void;
};

/** One leg: a pill while it waits, the card while it is open. */
function Panel({ index, mode, labels, distanceUnit, shown, play, onSelect, onNext, merge, open, active, seconds, nextPress, still }: PanelProps) {
  const progress = useSharedValue(0);
  const press = useSharedValue(0);
  // The marker's eased progress in steps, for the figures.
  const [step, setStep] = useState(0);

  useEffect(() => {
    if (play.leg !== index) return;
    if (play.tapped) press.set(pulse());
    progress.set(0);
    progress.set(
      withDelay(play.delay * 1000, withTiming(1, { duration: still ? 0 : PLAY_TIME * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never })),
    );
  }, [play, index, press, progress, still]);

  useAnimatedReaction(
    () => Math.round(inOut(progress.get()) * COUNT_STEPS),
    (now, old) => {
      if (now !== old) scheduleOnRN(setStep, now);
    },
  );

  const frame = useAnimatedStyle(() => {
    const box = panelLayout(index, open.get(), active.get());
    return {
      left: box.x,
      top: box.y,
      width: box.width,
      height: box.height,
      borderRadius: box.radius,
      opacity: merge.get(),
      transform: [{ scale: 1 - 0.04 * press.get() }],
    };
  });
  const rim = useAnimatedStyle(() => ({ borderRadius: panelLayout(index, open.get(), active.get()).radius }));
  // The pill's edge that faces the card catches the leg's colour.
  const glowAbove = useAnimatedStyle(() => ({
    opacity: (1 - panelLayout(index, open.get(), active.get()).expand) * clamp01(active.get() - index),
  }));
  const glowBelow = useAnimatedStyle(() => ({
    opacity: (1 - panelLayout(index, open.get(), active.get()).expand) * clamp01(index - active.get()),
  }));
  const face = useAnimatedStyle(() => {
    const box = panelLayout(index, open.get(), active.get());
    return {
      opacity: smooth(clamp01((box.expand - 0.45) / 0.55)) * seenAt(open.get()),
      transform: [{ translateX: (box.width - CARD_W) / 2 }],
    };
  });
  const head = useAnimatedStyle(() => {
    const { expand } = panelLayout(index, open.get(), active.get());
    return {
      opacity: seenAt(open.get()),
      transform: [{ translateX: mix(12, 18, expand) }, { translateY: mix(0, 12, expand) }, { scale: mix(0.9, 1, expand) }],
    };
  });
  const tail = useAnimatedStyle(() => ({
    opacity: (1 - clamp01(panelLayout(index, open.get(), active.get()).expand * 3)) * seenAt(open.get()),
  }));

  const eased = step / COUNT_STEPS;
  const walking = mode.travel === 'walk';
  const labelY = walking ? 116 : 90;

  return (
    <Animated.View style={[styles.panel, { zIndex: shown ? 1 : 0 }, frame]}>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, glowAbove]}>
        <LinearGradient style={StyleSheet.absoluteFill} colors={[fade(mode.glow, 0), fade(mode.glow, 0.5)]} start={{ x: 0.5, y: 0.35 }} end={{ x: 0.5, y: 1 }} />
      </Animated.View>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, glowBelow]}>
        <LinearGradient style={StyleSheet.absoluteFill} colors={[fade(mode.glow, 0.5), fade(mode.glow, 0)]} start={{ x: 0.5, y: 0 }} end={{ x: 0.5, y: 0.65 }} />
      </Animated.View>

      <Pressable
        style={StyleSheet.absoluteFill}
        disabled={shown}
        accessibilityRole="button"
        accessibilityLabel={`${mode.name}, ~${mode.time} ${mode.timeUnit}`}
        onPress={onSelect}
      />

      <Animated.View pointerEvents={shown ? 'box-none' : 'none'} style={[styles.face, face]}>
        <FaceArt travel={mode.travel} glow={mode.glow} accent={mode.accent} share={mode.share} progress={progress} seconds={seconds} nextPress={nextPress} />

        <Text style={[styles.percent, styles.at, { left: 145, top: 20, width: 40, height: 16, lineHeight: 16 }]}>
          {Math.round(mode.share * eased * 100)}%
        </Text>
        <Text style={[styles.next, styles.at, { left: 248, top: 16, width: 48, height: 24, lineHeight: 24 }]}>{labels.next}</Text>
        <Pressable style={styles.nextTouch} accessibilityRole="button" accessibilityLabel={labels.next} onPress={onNext} />

        <Text style={[styles.dim, styles.at, { left: 18, top: 48, width: 120, height: 10, lineHeight: 10 }]}>{labels.distance}</Text>
        <Text style={[styles.figure, styles.at, { left: 18, top: 58, width: 180, height: 26, lineHeight: 26 }]}>
          {amount(mode.distance, eased)} {distanceUnit}
        </Text>
        <Text style={[styles.dim, styles.at, styles.right, { left: 192, top: 48, width: 120, height: 10, lineHeight: 10 }]}>{labels.time}</Text>
        <Text style={[styles.figure, styles.at, styles.right, { left: 132, top: 58, width: 180, height: 26, lineHeight: 26 }]}>
          ~{amount(mode.time, eased)} {mode.timeUnit}
        </Text>

        <Stamp label={labels.start} value={mode.start} x={18} y={labelY} />
        {walking && <Stamp label={labels.travelTime} value={`${mode.time} min`} x={106} y={labelY} />}
        <Stamp label={labels.arrive} value={mode.arrive} x={232} y={labelY} right />

        {mode.stops.slice(0, 2).map((stop, row) => (
          <Fragment key={stop}>
            <Text style={[styles.dim, styles.at, { left: 28, top: 156 + row * 16, width: 44, height: 12, lineHeight: 12 }]}>
              {row === 0 ? labels.nextStop : labels.then}
            </Text>
            <Text style={[styles.stop, styles.at, { left: 74, top: 155 + row * 16, width: 150, height: 14, lineHeight: 14 }]} numberOfLines={1}>
              {stop}
            </Text>
          </Fragment>
        ))}
        {mode.photos.slice(0, 2).map((item, row) => (
          <Image
            key={item.photo}
            source={item.photo}
            style={[styles.thumb, { left: 238 + row * 40, backgroundColor: item.tint }]}
            contentFit="cover"
            transition={200}
          />
        ))}
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.head, head]}>
        <TravelIcon travel={mode.travel} />
        <Text style={styles.name} numberOfLines={1}>
          {mode.name}
        </Text>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.tail, tail]}>
        <Text style={styles.summary} numberOfLines={1}>
          ~{mode.time} {mode.timeUnit}
        </Text>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.rim, rim]} />
    </Animated.View>
  );
}

/** A small grey label over its value. */
function Stamp({ label, value, x, y, right = false }: { label: string; value: string; x: number; y: number; right?: boolean }) {
  return (
    <>
      <Text style={[styles.dim, styles.at, right && styles.right, { left: x, top: y, width: 80, height: 10, lineHeight: 10 }]}>{label}</Text>
      <Text style={[styles.stamp, styles.at, right && styles.right, { left: x, top: y + 10, width: 80, height: 13, lineHeight: 13 }]}>{value}</Text>
    </>
  );
}

/** One of the two pills the card unfolds from. */
function EndPill({ label, place }: { label: string; place: string }) {
  return (
    <>
      <LinearGradient style={StyleSheet.absoluteFill} colors={['#6d28d98c', '#6d28d900']} start={{ x: 0.5, y: 0 }} end={{ x: 0.5, y: 0.6 }} />
      <Text style={styles.endLabel} numberOfLines={1}>
        {label}
      </Text>
      <Text style={styles.endPlace} numberOfLines={1}>
        {place}
      </Text>
      <View style={[StyleSheet.absoluteFill, styles.rim, styles.capsule]} />
    </>
  );
}

/**
 * A route planner that unfolds from two pills. "From" and "To" sit side by side with a Go button
 * between them; tapping it merges them into a dark card for the first leg, whose marker travels while
 * its distance and time count up. The other ways to travel wait as pills above and below the card:
 * tapping one (or "Next Step") shrinks the card into a pill and opens that one, each with its own
 * glow colour and drawing. After the last leg the card folds back into the two pills.
 */
export function RouteProgressCard({
  from = 'Alder Park',
  to = 'Coral Bay',
  modes = MODES,
  distanceUnit = 'miles',
  labels,
  autoPlay = false,
  onStart,
  onSelect,
  onFinish,
}: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still && modes.length > 1;
  const words = { ...LABELS, ...labels };
  const last = modes.length - 1;

  // The Go button: 0 away … 1 between the pills, which it pushes apart.
  const go = useSharedValue(0);
  const merge = useSharedValue(0);
  const open = useSharedValue(0);
  const active = useSharedValue(last);
  const seconds = useSharedValue(0);
  const goPress = useSharedValue(0);
  const nextPress = useSharedValue(0);
  const motion: Motion = { merge, open, active, seconds, nextPress, still };

  // The leg whose card is open, or opens next.
  const [shown, setShown] = useState(last);
  // The button between the pills reads Done instead of Go.
  const [done, setDone] = useState(false);
  const [play, setPlay] = useState<Play>({ leg: -1, delay: 0, tapped: false });
  // Counts taps: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  // `armed`: Go is on screen and can be tapped. `later`: steps still waiting; a new run of the card drops them.
  const state = useRef({ phase: 'idle' as 'idle' | 'open' | 'closing', armed: false, shown: last, later: [] as ReturnType<typeof setTimeout>[] });

  useFrameCallback((frame) => {
    seconds.set(seconds.get() + Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000);
  }, !still);

  // Under reduced motion nothing travels: every change is a jump.
  const tween = (value: SharedValue<number>, target: number, time: number, easing: WithTimingConfig['easing'], delay = 0) => {
    value.set(withDelay(delay * 1000, withTiming(target, { duration: still ? 0 : time * 1000, easing, reduceMotion: ReduceMotion.Never })));
  };
  const spring = (value: SharedValue<number>, target: number, config: typeof POP_SPRING, delay = 0) => {
    if (still) tween(value, target, 0, Easing.linear, delay);
    else value.set(withDelay(delay * 1000, withSpring(target, config)));
  };

  const forget = () => {
    state.current.later.forEach(clearTimeout);
    state.current.later = [];
  };

  const showGo = () => {
    state.current.armed = true;
    setDone(false);
    spring(go, 1, POP_SPRING);
  };

  /** Go: the pills merge, and the capsule they make grows into the first leg's card. */
  const start = () => {
    if (state.current.phase !== 'idle' || !state.current.armed) return;
    state.current.phase = 'open';
    state.current.armed = false;
    state.current.shown = last;
    forget();
    goPress.set(pulse());
    active.set(last);
    setShown(last);
    tween(go, 0, GO_OUT, EASE_IN);
    tween(merge, 1, MERGE_TIME, EASE_IN_OUT);
    spring(open, 1, OPEN_SPRING, OPEN_DELAY);
    setPlay({ leg: last, delay: PLAY_DELAY + OPEN_DELAY, tapped: false });
    onStart?.();
  };

  /** Opens leg `index`: the open card shrinks to a pill as this one grows into the card. */
  const select = (index: number) => {
    if (state.current.phase !== 'open' || index === state.current.shown || index < 0 || index > last) return;
    state.current.shown = index;
    setShown(index);
    spring(active, index, STACK_SPRING);
    setPlay({ leg: index, delay: PLAY_DELAY, tapped: true });
    onSelect?.(index);
  };

  /** The card folds back into the two pills; Done shows between them for a moment, then Go returns. */
  const finish = () => {
    if (state.current.phase !== 'open') return;
    state.current.phase = 'closing';
    setDone(true);
    spring(open, 0, OPEN_SPRING);
    tween(merge, 0, MERGE_TIME, EASE_IN_OUT, SPLIT_DELAY);
    spring(go, 1, POP_SPRING, DONE_DELAY);
    forget();
    state.current.later = [
      setTimeout(() => {
        tween(go, 0, GO_OUT, EASE_IN);
        state.current.phase = 'idle';
      }, DONE_OUT * 1000),
      setTimeout(() => steps.current.showGo(), CLOSE_TIME * 1000),
    ];
    onFinish?.();
  };

  const next = () => {
    nextPress.set(pulse());
    if (state.current.shown > 0) select(state.current.shown - 1);
    else finish();
  };

  const touch = () => {
    setTouches((count) => count + 1);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ showGo, start, select, finish });
  useEffect(() => {
    steps.current = { showGo, start, select, finish };
  });

  useEffect(() => {
    const waiting = state.current;
    waiting.later = [setTimeout(() => steps.current.showGo(), GO_IN * 1000)];
    return () => waiting.later.forEach(clearTimeout);
  }, []);

  // The script a finger would follow: Go, then each leg in turn, then the end.
  useEffect(() => {
    if (!auto) return;
    const legs = last + 1;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = n % (legs + 1);
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / (legs + 1)) * LOOP + GO_AT + step * LEG_TIME) * 1000;
      timer = setTimeout(() => {
        if (step === 0) steps.current.start();
        else if (step < legs) steps.current.select(last - step);
        else steps.current.finish();
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, fold the card away if it is open, and start over from the pills.
      timer = setTimeout(() => {
        const busy = state.current.phase !== 'idle' || !state.current.armed;
        steps.current.finish();
        run(Date.now() + (busy ? (CLOSE_TIME - GO_IN) * 1000 : 0), 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, last]);

  const fromStyle = useAnimatedStyle(() => {
    const gap = mix(PILL_GAP, GO_W + 12, go.get());
    return {
      opacity: 1 - merge.get(),
      transform: [{ translateX: mix(WIDTH / 2 - gap / 2 - END_W, WIDTH / 2 - CAPSULE_W / 2, merge.get()) }],
    };
  });
  const toStyle = useAnimatedStyle(() => {
    const gap = mix(PILL_GAP, GO_W + 12, go.get());
    return {
      opacity: 1 - merge.get(),
      transform: [{ translateX: mix(WIDTH / 2 + gap / 2, WIDTH / 2 + CAPSULE_W / 2 - END_W, merge.get()) }],
    };
  });
  const goStyle = useAnimatedStyle(() => ({
    opacity: clamp01(go.get() * 4),
    transform: [{ scale: Math.max(0, go.get()) * (1 - 0.08 * goPress.get()) }],
  }));

  return (
    <View style={styles.root}>
      {modes.map((mode, index) => (
        <Panel
          key={index}
          index={index}
          mode={mode}
          labels={words}
          distanceUnit={distanceUnit}
          shown={index === shown}
          play={play}
          onSelect={() => {
            touch();
            select(index);
          }}
          onNext={() => {
            touch();
            next();
          }}
          {...motion}
        />
      ))}

      <Animated.View pointerEvents="none" style={[styles.end, fromStyle]}>
        <EndPill label={words.from} place={from} />
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.end, toStyle]}>
        <EndPill label={words.to} place={to} />
      </Animated.View>

      <Animated.View style={[styles.go, goStyle]}>
        <Pressable
          style={styles.goTouch}
          accessibilityRole="button"
          accessibilityLabel={done ? words.done : words.go}
          onPress={() => {
            touch();
            start();
          }}
        >
          <LinearGradient style={StyleSheet.absoluteFill} colors={['#8b5cf6', '#6d28d9']} />
          <Text style={styles.goLabel} numberOfLines={1}>
            {done ? words.done : words.go}
          </Text>
          <View style={[StyleSheet.absoluteFill, styles.goRim]} />
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
  panel: {
    position: 'absolute',
    backgroundColor: '#0b0b0f',
    overflow: 'hidden',
    boxShadow: '0 8 20 0 rgba(0,0,0,0.22)',
  },
  rim: {
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
  },
  capsule: {
    borderRadius: CAPSULE_H / 2,
  },
  face: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CARD_W,
    height: CARD_H,
  },
  at: {
    position: 'absolute',
    fontVariant: ['tabular-nums'],
  },
  right: {
    textAlign: 'right',
  },
  dim: {
    color: 'rgba(255,255,255,0.45)',
    fontSize: 7.5,
  },
  percent: {
    color: 'rgba(255,255,255,0.9)',
    fontSize: 8.5,
    fontWeight: '500',
    textAlign: 'center',
  },
  next: {
    color: 'rgba(255,255,255,0.9)',
    fontSize: 9.5,
    textAlign: 'center',
  },
  nextTouch: {
    position: 'absolute',
    left: 236,
    top: 8,
    width: 82,
    height: 40,
  },
  figure: {
    color: '#ffffff',
    fontSize: 21,
    fontWeight: '300',
    letterSpacing: -0.3,
  },
  stamp: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 9.5,
  },
  stop: {
    color: 'rgba(255,255,255,0.9)',
    fontSize: 10.5,
  },
  thumb: {
    position: 'absolute',
    top: 153,
    width: 34,
    height: 34,
    borderRadius: 8,
  },
  head: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: PILL_H,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    transformOrigin: 'left center',
  },
  name: {
    color: 'rgba(255,255,255,0.9)',
    fontSize: 13,
  },
  tail: {
    position: 'absolute',
    right: 12,
    top: 0,
    height: PILL_H,
    justifyContent: 'center',
  },
  summary: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 11.5,
    fontVariant: ['tabular-nums'],
  },
  end: {
    position: 'absolute',
    left: 0,
    top: CENTER_Y - CAPSULE_H / 2,
    width: END_W,
    height: CAPSULE_H,
    borderRadius: CAPSULE_H / 2,
    paddingLeft: 18,
    justifyContent: 'center',
    backgroundColor: '#0b0b0f',
    overflow: 'hidden',
    boxShadow: '0 8 20 0 rgba(0,0,0,0.22)',
  },
  endLabel: {
    color: 'rgba(255,255,255,0.5)',
    fontSize: 7.5,
    lineHeight: 9,
    marginBottom: 1,
  },
  endPlace: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 14.5,
    fontWeight: '500',
  },
  go: {
    position: 'absolute',
    left: WIDTH / 2 - GO_W / 2,
    top: CENTER_Y - CAPSULE_H / 2,
    width: GO_W,
    height: CAPSULE_H,
    borderRadius: CAPSULE_H / 2,
    boxShadow: '0 5 16 0 rgba(109,40,217,0.45)',
  },
  goTouch: {
    flex: 1,
    borderRadius: CAPSULE_H / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  goLabel: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
  goRim: {
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.22)',
    borderRadius: CAPSULE_H / 2,
  },
});
