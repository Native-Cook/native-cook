import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Canvas, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ComponentProps, useEffect, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Category = {
  name: string;
  /** Glyph shown on the chip. */
  icon: ComponentProps<typeof MaterialCommunityIcons>['name'];
  /** Six-digit hex. */
  color: string;
  /** The category's budget at first. */
  amount: number;
};

/** What the scale says about an amount of `from` or more. */
type Verdict = {
  from: number;
  title: string;
  lines: string[];
};

type Props = {
  categories?: Category[];
  /** In rising order of `from`; the first one should start at 0. */
  verdicts?: Verdict[];
  /** Index of the category that is open at first. */
  initial?: number;
  /** The scale runs from 0 to `maximum`, one mark every `step`. */
  maximum?: number;
  step?: number;
  currency?: string;
  saveLabel?: string;
  /** Flings the ruler twice, picks another category and comes back by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the category's index and its amount when the ruler settles or another category opens. */
  onChange?: (index: number, amount: number) => void;
  /** Called with every category's amount when Save is tapped. */
  onSave?: (amounts: number[]) => void;
};

const WIDTH = 340;
const HEIGHT = 468;
const CORNER = 36;
const INSET = 24;

// The row of chips: a square, the open pill, and the gap between two.
const ROW_Y = 56;
const CHIP = 52;
const CHIP_RADIUS = 18;
const CHIP_GAP = 16;
const PILL_W = 150;
const PILL_H = 64;

// The white card and the amount on it.
const CARD_Y = 112;
const CARD_RADIUS = 30;
const AMOUNT_Y = 174;
const DIGITS_X = 62;
const DIGIT_W = 28;
const DIGIT_H = 56;
// Seconds a wheel lags behind the amount, and the digits a second at which it is a full smear.
const WHEEL_LAG = 0.05;
const SMEAR_AT = 30;

// The coloured panel, the ruler's centre line and the indicator.
const SCALE_Y = 236;
const RULER_Y = 282;
const INDICATOR_X = 48;
// Points from one mark to the next.
const PITCH = 14;
// A mark stretches by the ruler's speed times this many seconds, up to `STREAK_MAX` points.
const STREAK = 0.016;
const STREAK_MAX = 9;
const VERDICT_Y = 312;
const VERDICT_SHIFT = 12;
const VERDICT_TIME = 0.25;
const SAVE = 64;

// A flick carries the ruler on by its speed times this many seconds, over `GLIDE_TIME`.
const CARRY = 0.2;
const GLIDE_TIME = 1;
// The ruler's run to another category's amount.
const SWITCH_TIME = 0.5;

const LOOP = 7;
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const CHIP_SPRING = { mass: 1, stiffness: 220, damping: 24, reduceMotion: ReduceMotion.Never };
const SNAP_SPRING = { mass: 1, stiffness: 300, damping: 26, reduceMotion: ReduceMotion.Never };
const SWITCH_CURVE = Easing.bezier(0.45, 0, 0.25, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

const INDIGO = '#3d35e0';
const INK = '#2e2e3a';
const ROUNDED = Platform.select({ ios: 'ui-rounded', default: undefined });

const CATEGORIES: Category[] = [
  { name: 'Cafe', icon: 'silverware-fork-knife', color: '#3d35e0', amount: 400 },
  { name: 'Grocery', icon: 'cart', color: '#2eddc0', amount: 650 },
  { name: 'Taxi', icon: 'car', color: '#f5b83d', amount: 170 },
  { name: 'Gym', icon: 'dumbbell', color: '#f58a6b', amount: 320 },
  { name: 'Bills', icon: 'file-document', color: '#f06fa5', amount: 540 },
];

const VERDICTS: Verdict[] = [
  { from: 0, title: 'Normal', lines: ["It's ok to eat at your place", 'Up to 5% of economy'] },
  { from: 600, title: "That's a lot", lines: ['Sometimes, you can eat in cafe', 'Up to 3% of economy'] },
  { from: 1200, title: 'Are you crazy?', lines: ['Eat in restaurants every day', 'Blow off 30% of money'] },
];

function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function grouped(n: number) {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** `hex` with an opacity, as eight-digit hex. */
function fade(hex: string, opacity: number) {
  return `${hex}${Math.round(opacity * 255)
    .toString(16)
    .padStart(2, '0')}`;
}

/** Friction: starts fast and loses speed exponentially, at rest at the end. */
function glide(t: number) {
  'worklet';
  return (1 - Math.exp(-5 * t)) / (1 - Math.exp(-5));
}

/** Up to 1 in `up` seconds and back to 0 in `down`. */
function pulse(up: number, down: number) {
  'worklet';
  return withSequence(withTiming(1, { duration: up * 1000, ...LINEAR }), withTiming(0, { duration: down * 1000, ...LINEAR }));
}

type ChipProps = {
  category: Category;
  /** This is the open category: the chip is the coloured pill. */
  open: boolean;
  /** Its amount, formatted. */
  amount: string;
  still: boolean;
  onPress: () => void;
};

/** One category: a white square, or the coloured pill with its name and amount while it is open. */
function Chip({ category, open, amount, still, onPress }: ChipProps) {
  // From the square, 0, to the pill, 1.
  const expand = useSharedValue(open ? 1 : 0);
  const press = useSharedValue(0);
  const was = useRef(open);

  useEffect(() => {
    if (was.current === open) return;
    was.current = open;
    if (still) {
      expand.set(open ? 1 : 0);
      return;
    }
    expand.set(withSpring(open ? 1 : 0, CHIP_SPRING));
    // The chip that opens dips as if tapped.
    if (open) press.set(pulse(0.08, 0.18));
  }, [open, still, expand, press]);

  const frame = useAnimatedStyle(() => {
    const e = clamp(expand.get(), 0, 1.05);
    return {
      width: CHIP + (PILL_W - CHIP) * e,
      height: CHIP + (PILL_H - CHIP) * e,
      transform: [{ scale: 1 - 0.05 * press.get() }],
    };
  });
  const colour = useAnimatedStyle(() => ({ opacity: clamp(expand.get(), 0, 1) }));
  const tile = useAnimatedStyle(() => {
    const e = clamp(expand.get(), 0, 1);
    return { opacity: e, transform: [{ translateY: 6 * e }] };
  });
  const glyph = useAnimatedStyle(() => {
    const e = clamp(expand.get(), 0, 1);
    return { transform: [{ translateX: 6 * e }, { translateY: 6 * e }] };
  });
  const plain = useAnimatedStyle(() => ({ opacity: 1 - clamp(expand.get(), 0, 1) }));
  const words = useAnimatedStyle(() => ({ opacity: clamp(expand.get() * 2 - 1, 0, 1) }));

  return (
    <Animated.View style={frame}>
      <View style={[StyleSheet.absoluteFill, styles.chipFace]} />
      <Animated.View
        style={[StyleSheet.absoluteFill, styles.chipFace, { backgroundColor: category.color, boxShadow: `0 8 20 0 ${fade(category.color, 0.4)}` }, colour]}
      />
      <View style={[StyleSheet.absoluteFill, styles.chipClip]}>
        <Animated.View style={[styles.chipTile, tile]} />
        <Animated.View style={[styles.chipGlyph, glyph]}>
          <Animated.View style={[StyleSheet.absoluteFill, styles.center, plain]}>
            <MaterialCommunityIcons name={category.icon} size={24} color={category.color} />
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, styles.center, colour]}>
            <MaterialCommunityIcons name={category.icon} size={24} color="#ffffff" />
          </Animated.View>
        </Animated.View>
        <Animated.View style={[styles.chipWords, words]}>
          <Text style={styles.chipName} numberOfLines={1}>
            {category.name}
          </Text>
          <Text style={styles.chipAmount} numberOfLines={1}>
            {amount}
          </Text>
        </Animated.View>
      </View>
      <Pressable
        style={StyleSheet.absoluteFill}
        accessibilityRole="button"
        accessibilityLabel={`${category.name}, ${amount}`}
        accessibilityState={{ selected: open }}
        onPress={onPress}
      />
    </Animated.View>
  );
}

/**
 * One digit wheel of the amount: `place` is 1 for units, 10 for tens and so on. It rolls after the
 * amount with a short lag, smears while it spins, and takes no room while the amount does not reach it.
 */
function Wheel({ place, amount, start, still }: { place: number; amount: SharedValue<number>; start: number; still: boolean }) {
  // The number the wheel has rolled to, how fast it is rolling (digits a second) and how much of its place it takes.
  const rolled = useSharedValue(Math.floor(start / place));
  const speed = useSharedValue(0);
  const presence = useSharedValue(place === 1 || start >= place ? 1 : 0);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 100) / 1000;
    if (dt <= 0) return;
    const whole = Math.max(0, Math.round(amount.get()));
    const gap = Math.floor(whole / place) - rolled.get();
    const move = still || Math.abs(gap) < 0.004 ? gap : gap * (1 - Math.exp(-dt / WHEEL_LAG));
    speed.set(move / dt);
    rolled.set(rolled.get() + move);
    const wanted = place === 1 || whole >= place ? 1 : 0;
    presence.set(still ? wanted : presence.get() + (wanted - presence.get()) * (1 - Math.exp(-dt / WHEEL_LAG)));
  });

  const column = useAnimatedStyle(() => {
    const blur = Math.min(1, Math.abs(speed.get()) / SMEAR_AT);
    return {
      width: DIGIT_W * presence.get(),
      opacity: presence.get() * (1 - 0.45 * blur),
      transform: [{ scaleY: 1 + 0.25 * blur }],
    };
  });
  // Ten digits and the first one again, so the roll from 9 to 0 has somewhere to go.
  const strip = useAnimatedStyle(() => {
    const turn = ((rolled.get() % 10) + 10) % 10;
    return { transform: [{ translateY: -turn * DIGIT_H }] };
  });

  return (
    <Animated.View style={[styles.wheel, column]}>
      <Animated.View style={[styles.strip, strip]}>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0].map((digit, row) => (
          <Text key={row} style={styles.digit}>
            {digit}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/** One verdict: its title rolls in from below and out upward, its lines fade. */
function VerdictLines({ index, verdict, shown, still }: { index: number; verdict: Verdict; shown: SharedValue<number>; still: boolean }) {
  const opacity = useSharedValue(shown.get() === index ? 1 : 0);
  const shift = useSharedValue(0);

  useAnimatedReaction(
    () => shown.get() === index,
    (on, was) => {
      if (was === null || on === was) return;
      const timing = { duration: still ? 0 : VERDICT_TIME * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
      opacity.set(withTiming(on ? 1 : 0, timing));
      if (on) shift.set(VERDICT_SHIFT);
      shift.set(withTiming(on ? 0 : -VERDICT_SHIFT, timing));
    },
  );

  const title = useAnimatedStyle(() => ({ opacity: opacity.get(), transform: [{ translateY: shift.get() }] }));
  const lines = useAnimatedStyle(() => ({ opacity: opacity.get() }));

  return (
    <>
      <Animated.Text style={[styles.verdictTitle, title]} numberOfLines={1}>
        {verdict.title}
      </Animated.Text>
      <Animated.View style={[styles.verdictLines, lines]}>
        {verdict.lines.map((line) => (
          <Text key={line} style={styles.verdictLine} numberOfLines={1}>
            {line}
          </Text>
        ))}
      </Animated.View>
    </>
  );
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/**
 * A budget panel built around a ruler. The ruler is dragged sideways under a fixed indicator and
 * flung with momentum; the amount above it counts along on rolling digits, and a verdict under it
 * rolls over when the amount crosses a threshold. The row of chips on top picks the category: the
 * chosen one opens into a coloured pill that carries its amount, and the ruler slides to it.
 */
export function BudgetScrubScale({
  categories = CATEGORIES,
  verdicts = VERDICTS,
  initial = 0,
  maximum = 2000,
  step = 10,
  currency = '$',
  saveLabel = 'Save',
  autoPlay = false,
  onChange,
  onSave,
}: Props) {
  const still = useReducedMotion();
  const home = clamp(initial, 0, Math.max(0, categories.length - 1));
  const auto = autoPlay && !still && categories.length > 0;
  const unit = Math.max(1, step);
  const pointsPerUnit = PITCH / unit;
  const first = clamp(categories[home]?.amount ?? 0, 0, maximum);

  // The amount under the indicator; the ruler's position follows from it.
  const amount = useSharedValue(first);
  // Where the ruler is heading.
  const heading = useSharedValue(first);
  // Points a second the ruler is moving at.
  const speed = useSharedValue(0);
  const before = useSharedValue(first);
  const grab = useSharedValue(0);
  // How far the row of chips has slid left.
  const scroll = useSharedValue(home * (CHIP + CHIP_GAP));
  // The indicator's twitch as a mark passes under it.
  const twitch = useSharedValue(0);
  const savePress = useSharedValue(0);
  // A finger moved the ruler last, so its marks are felt.
  const byHand = useSharedValue(false);
  const held = useSharedValue(false);

  // Each category's amount, the open one's as of the last time the ruler settled.
  const [amounts, setAmounts] = useState(() => categories.map((category) => clamp(category.amount, 0, maximum)));
  const [selected, setSelected] = useState(home);
  // The amount under the indicator rounded to a mark, for the open pill.
  const [live, setLive] = useState(first);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 100) / 1000;
    if (dt <= 0) return;
    speed.set(((amount.get() - before.get()) / dt) * pointsPerUnit);
    before.set(amount.get());
  });

  // The verdict for the amount on show.
  const verdict = useDerivedValue(() => {
    const whole = Math.max(0, Math.round(amount.get()));
    let index = 0;
    for (let i = 0; i < verdicts.length; i++) {
      if (whole >= verdicts[i].from) index = i;
    }
    return index;
  });

  // A mark has come under the indicator: it twitches, the pill counts, and a ruler moved by hand clicks.
  useAnimatedReaction(
    () => clamp(Math.round(amount.get() / unit) * unit, 0, maximum),
    (now, old) => {
      if (old === null || now === old) return;
      if (!still) twitch.set(pulse(0.05, 0.18));
      scheduleOnRN(setLive, now);
      if (byHand.get()) scheduleOnRN(tick);
    },
  );

  const touch = () => setTouches((count) => count + 1);

  /** Makes `target` the open category's amount. */
  const keep = (target: number) => {
    if (amounts[selected] === target) return;
    setAmounts((list) => list.map((value, index) => (index === selected ? target : value)));
    onChange?.(selected, target);
  };

  /** Sends the ruler to `target` over `time` seconds as if flung, and keeps it. */
  const fling = (target: number, time: number) => {
    const rounded = clamp(Math.round(target / unit) * unit, 0, maximum);
    heading.set(rounded);
    amount.set(withTiming(rounded, { duration: time * 1000, easing: glide, reduceMotion: ReduceMotion.Never }));
    keep(rounded);
  };

  /** The ruler's run to `target`, the amount of the category that has just opened. */
  const run = (target: number) => {
    heading.set(target);
    if (still) amount.set(target);
    else amount.set(withTiming(target, { duration: SWITCH_TIME * 1000, easing: SWITCH_CURVE, reduceMotion: ReduceMotion.Never }));
  };

  /**
   * Opens category `index`: its chip grows into the pill, the row slides it to the left edge, and
   * the ruler runs to its amount.
   */
  const select = (index: number) => {
    if (index === selected || index < 0 || index >= categories.length) return;
    const leaving = heading.get();
    setAmounts((list) => list.map((value, i) => (i === selected ? leaving : value)));
    setSelected(index);
    const offset = index * (CHIP + CHIP_GAP);
    if (still) scroll.set(offset);
    else scroll.set(withSpring(offset, CHIP_SPRING));
    run(amounts[index]);
    onChange?.(index, amounts[index]);
  };

  /** Back to the first category with every amount as it began. */
  const restore = () => {
    const fresh = categories.map((category) => clamp(category.amount, 0, maximum));
    byHand.set(false);
    setAmounts(fresh);
    setSelected(home);
    scroll.set(withSpring(home * (CHIP + CHIP_GAP), CHIP_SPRING));
    run(fresh[home]);
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin(() => {
      held.set(true);
      scheduleOnRN(touch);
    })
    .onStart(() => {
      byHand.set(true);
      grab.set(amount.get());
    })
    .onUpdate((event) => {
      // Past either end the ruler gives less and less, up to four marks.
      const raw = grab.get() - event.translationX / pointsPerUnit;
      const over = raw < 0 ? -raw : Math.max(0, raw - maximum);
      const limit = unit * 4;
      const give = limit * (1 - 1 / (over / limit + 1));
      amount.set(over <= 0 ? raw : raw < 0 ? -give : maximum + give);
    })
    .onEnd((event) => {
      // A flick carries the ruler on and friction stops it on a mark; a slow release just snaps.
      const carry = (-event.velocityX / pointsPerUnit) * CARRY;
      const flung = !still && Math.abs(carry) >= unit;
      const target = clamp(Math.round((amount.get() + (flung ? carry : 0)) / unit) * unit, 0, maximum);
      heading.set(target);
      if (still) amount.set(target);
      else if (flung) amount.set(withTiming(target, { duration: GLIDE_TIME * 1000, easing: glide, reduceMotion: ReduceMotion.Never }));
      else amount.set(withSpring(target, SNAP_SPRING));
      scheduleOnRN(keep, target);
    })
    .onFinalize(() => {
      held.set(false);
      scheduleOnRN(touch);
    });

  /** The script's last step, once the finger has gone; false while it is still down. */
  const resume = () => {
    if (held.get()) return false;
    restore();
    return true;
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ fling, select, restore, resume });
  useEffect(() => {
    steps.current = { fling, select, restore, resume };
  });

  // The script a finger would follow: two flings up the scale, the next category, and back.
  useEffect(() => {
    if (!auto) return;
    const script = [
      { at: 1.0, run: () => steps.current.fling(maximum * 0.45, 0.9) },
      { at: 2.4, run: () => steps.current.fling(maximum * 0.8, 0.8) },
      { at: 3.8, run: () => steps.current.select((home + 1) % categories.length) },
      { at: 5.1, run: () => steps.current.restore() },
    ];
    let timer: ReturnType<typeof setTimeout>;
    const play = (begin: number, n: number) => {
      const step = script[n % script.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / script.length) * LOOP + step.at) * 1000;
      timer = setTimeout(() => {
        step.run();
        play(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      play(Date.now(), 0);
    } else {
      // A touch took over: wait, put every amount back and start over.
      timer = setTimeout(() => {
        if (!steps.current.resume()) return;
        play(Date.now() + 900, 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, home, maximum, categories.length]);

  // The marks in sight: dot, dot, short tick, dot, dot, tall tick. A fast ruler streaks: every mark
  // stretches along its way into a dash and the ticks sink to the dots' height.
  const marks = useDerivedValue(() => {
    const path = Skia.Path.Make();
    const value = amount.get();
    const stretch = still ? 0 : Math.min(Math.abs(speed.get()) * STREAK, STREAK_MAX);
    const flat = stretch / STREAK_MAX;
    const from = Math.max(0, Math.floor((value - INDICATOR_X / pointsPerUnit) / unit) - 1);
    const to = Math.min(Math.floor(maximum / unit), Math.ceil((value + (WIDTH - INDICATOR_X) / pointsPerUnit) / unit) + 1);
    for (let j = from; j <= to; j++) {
      const x = INDICATOR_X + (j * unit - value) * pointsPerUnit;
      const tall = j % 6 === 0 ? 12 : j % 6 === 3 ? 6 : 2.6;
      const height = tall + (2.6 - tall) * flat;
      const width = (tall > 3 ? 2 : 2.6) + stretch;
      path.addRRect(Skia.RRectXY(Skia.XYWHRect(x - width / 2, 12 - height / 2, width, height), 1.3, 1.3));
    }
    return path;
  });
  const marksOpacity = useDerivedValue(() => {
    const stretch = still ? 0 : Math.min(Math.abs(speed.get()) * STREAK, STREAK_MAX);
    return 0.6 * (1 - (0.35 * stretch) / STREAK_MAX);
  });

  const rowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -scroll.get() }] }));
  const indicatorStyle = useAnimatedStyle(() => ({ transform: [{ scaleY: 1 + 0.1 * twitch.get() }] }));
  const saveStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.06 * savePress.get() }] }));

  return (
    <View style={styles.root}>
      <Animated.View style={[styles.row, rowStyle]}>
        {categories.map((category, index) => (
          <Chip
            key={index}
            category={category}
            open={index === selected}
            amount={`${currency} ${grouped(index === selected ? live : amounts[index])}`}
            still={still}
            onPress={() => {
              touch();
              tick();
              select(index);
            }}
          />
        ))}
      </Animated.View>

      <View style={styles.card} />
      <Text style={styles.currency}>{currency}</Text>
      <View style={styles.digits}>
        {[1000, 100, 10, 1].map((place) => (
          <Wheel key={place} place={place} amount={amount} start={first} still={still} />
        ))}
      </View>
      {/* A small calculator on a lavender tile. */}
      <View style={styles.tile}>
        <View style={styles.calculator} />
        <View style={styles.calculatorScreen} />
        {[0, 1, 2, 3].map((key) => (
          <View key={key} style={[styles.calculatorKey, { left: 13 + (key % 2) * 5.2, top: 16.3 + Math.floor(key / 2) * 3.6 }]} />
        ))}
      </View>

      <GestureDetector gesture={pan}>
        <View
          style={styles.scale}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={categories[selected]?.name}
          accessibilityValue={{ text: `${currency} ${grouped(live)}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            const target = clamp(heading.get() + (event.nativeEvent.actionName === 'increment' ? unit : -unit), 0, maximum);
            touch();
            heading.set(target);
            amount.set(still ? target : withSpring(target, SNAP_SPRING));
            keep(target);
          }}
        >
          <Canvas style={styles.ruler} pointerEvents="none">
            <Path path={marks} color="#ffffff" opacity={marksOpacity} />
          </Canvas>
          <Animated.View pointerEvents="none" style={[styles.indicator, indicatorStyle]} />
          <View pointerEvents="none" style={styles.verdict}>
            {verdicts.map((item, index) => (
              <VerdictLines key={index} index={index} verdict={item} shown={verdict} still={still} />
            ))}
          </View>
          <Animated.View style={[styles.save, saveStyle]}>
            <Pressable
              style={styles.saveTouch}
              accessibilityRole="button"
              onPress={() => {
                const kept = amounts.map((value, index) => (index === selected ? heading.get() : value));
                touch();
                tick();
                if (!still) savePress.set(pulse(0.08, 0.18));
                onSave?.(kept);
              }}
            >
              <Text style={styles.saveLabel} numberOfLines={1}>
                {saveLabel}
              </Text>
            </Pressable>
          </Animated.View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: CORNER,
    borderCurve: 'continuous',
    backgroundColor: '#f7f7f8',
    overflow: 'hidden',
  },
  center: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  row: {
    position: 'absolute',
    left: INSET,
    top: ROW_Y - PILL_H / 2,
    height: PILL_H,
    flexDirection: 'row',
    alignItems: 'center',
    gap: CHIP_GAP,
  },
  chipFace: {
    borderRadius: CHIP_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
  },
  chipClip: {
    borderRadius: CHIP_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  chipTile: {
    position: 'absolute',
    left: 10,
    top: 4,
    width: 44,
    height: 44,
    borderRadius: 12,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(0,0,0,0.2)',
  },
  chipGlyph: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CHIP,
    height: CHIP,
  },
  chipWords: {
    position: 'absolute',
    left: 66,
    top: 0,
    bottom: 0,
    width: 80,
    justifyContent: 'center',
    gap: 2,
  },
  chipName: {
    color: '#ffffff',
    fontSize: 14,
    lineHeight: 17,
    fontWeight: '300',
    includeFontPadding: false,
  },
  chipAmount: {
    color: '#ffffff',
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  card: {
    position: 'absolute',
    left: 0,
    top: CARD_Y,
    width: WIDTH,
    height: HEIGHT - CARD_Y,
    borderTopLeftRadius: CARD_RADIUS,
    borderTopRightRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
  },
  currency: {
    position: 'absolute',
    left: INSET + 2,
    top: AMOUNT_Y - 30,
    width: 34,
    height: 60,
    lineHeight: 60,
    color: '#c9c9d1',
    fontSize: 40,
    fontWeight: '300',
    fontFamily: ROUNDED,
    includeFontPadding: false,
  },
  digits: {
    position: 'absolute',
    left: DIGITS_X,
    top: AMOUNT_Y - DIGIT_H / 2,
    height: DIGIT_H,
    flexDirection: 'row',
  },
  wheel: {
    height: DIGIT_H,
    alignItems: 'center',
    overflow: 'hidden',
  },
  strip: {
    width: DIGIT_W,
  },
  digit: {
    width: DIGIT_W,
    height: DIGIT_H,
    lineHeight: DIGIT_H,
    textAlign: 'center',
    color: INK,
    fontSize: 44,
    fontWeight: '800',
    fontFamily: ROUNDED,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  tile: {
    position: 'absolute',
    left: WIDTH - INSET - 34,
    top: AMOUNT_Y - 17,
    width: 34,
    height: 34,
    borderRadius: 10,
    borderCurve: 'continuous',
    backgroundColor: '#ecebfb',
  },
  calculator: {
    position: 'absolute',
    left: 11,
    top: 9.5,
    width: 12,
    height: 15,
    borderRadius: 2.5,
    backgroundColor: INDIGO,
  },
  calculatorScreen: {
    position: 'absolute',
    left: 13,
    top: 11.5,
    width: 8,
    height: 3,
    backgroundColor: '#ecebfb',
  },
  calculatorKey: {
    position: 'absolute',
    width: 2.8,
    height: 2.2,
    backgroundColor: '#ecebfb',
  },
  scale: {
    position: 'absolute',
    left: 0,
    top: SCALE_Y,
    width: WIDTH,
    height: HEIGHT - SCALE_Y,
    borderTopLeftRadius: CORNER,
    borderCurve: 'continuous',
    backgroundColor: INDIGO,
  },
  ruler: {
    position: 'absolute',
    left: 0,
    top: RULER_Y - 12 - SCALE_Y,
    width: WIDTH,
    height: 24,
  },
  indicator: {
    position: 'absolute',
    left: INDICATOR_X - 3,
    top: RULER_Y - 17 - SCALE_Y,
    width: 6,
    height: 34,
    borderRadius: 3,
    backgroundColor: '#2eddc0',
    boxShadow: '0 0 12 0 rgba(46,221,192,0.55)',
  },
  verdict: {
    position: 'absolute',
    left: INSET + 4,
    top: VERDICT_Y - SCALE_Y,
    width: 200,
    height: 64,
  },
  verdictTitle: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 200,
    height: 20,
    lineHeight: 20,
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
    includeFontPadding: false,
  },
  verdictLines: {
    position: 'absolute',
    left: 0,
    top: 28,
    width: 200,
    gap: 3,
  },
  verdictLine: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: 11,
    lineHeight: 13,
    includeFontPadding: false,
  },
  save: {
    position: 'absolute',
    left: WIDTH - INSET - SAVE,
    top: HEIGHT - SCALE_Y - INSET - SAVE,
    width: SAVE,
    height: SAVE,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    boxShadow: '0 6 20 0 rgba(0,0,0,0.14)',
  },
  saveTouch: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  saveLabel: {
    color: INK,
    fontSize: 14,
    fontWeight: '700',
  },
});
