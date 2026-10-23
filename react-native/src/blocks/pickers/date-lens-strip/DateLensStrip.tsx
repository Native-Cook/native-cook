import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
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

/** One day of the strip, already in words. */
type Day = {
  /** "FRI" */
  weekday: string;
  /** "06" */
  number: string;
  /** "NOVEMBER": shown in place of the label for a moment when the lens crosses into this month. */
  month: string;
};

type Props = {
  days?: Day[];
  /** Index of the day that starts under the lens. */
  initial?: number;
  label?: string;
  /** Slides the strip through a short script by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the day the strip is settling on. */
  onSelect?: (index: number) => void;
};

// The component's height: the lens and room for it to wobble.
const HEIGHT = 120;
const MARGIN = 16;
const BAR_H = 64;

// Measured from the bar's left end: where the label starts, the lens's centre, and the days' pitch.
const LABEL_X = 22;
const LENS_X = 214;
const PITCH = 62;
const LENS_W = 66;
const LENS_H = 90;
// How much wider and lower the lens gets at full stretch, and how far it leans.
const STRETCH_W = 12;
const SQUASH_H = 6;
const LEAN = 5;
// The strip's speed, in days per second, that stretches the lens all the way.
const FULL_SPEED = 12;
// The lens's spring (response 0.4 s, damping 0.6), stepped at most `TICK` seconds at a time.
const LENS_STIFFNESS = 247;
const LENS_DAMPING = 18.85;
const TICK = 1 / 240;

// A day is 1.12× under the lens and back to 1× one day away.
const GROW = 0.12;
const WEEKDAY_Y = 18;
const NUMBER_Y = 10;
// Days further than this from the lens are not drawn.
const REACH = 5;
// A day is gone at `FADE_FROM` from the bar's left end and whole `FADE_OVER` further; the same near the right end.
const FADE_FROM = 130;
const FADE_OVER = 22;
const END_FADE = 14;
const END_OVER = 34;
// How far the label travels as it rolls, and how long a month's name stays.
const ROLL_Y = 22;
const MONTH_TIME = 1.2;

// Dragging: the travel that makes a touch a drag, how far a flick carries, and the pull past the ends.
const SLOP = 8;
const GLIDE = 0.16;
const MAX_SPEED = 24;
const RUBBER = 0.35;

// autoPlay (seconds): when the strip sets off, and to which day, counted from the resting one.
const SCRIPT = [
  { at: 1.0, days: 1 },
  { at: 2.25, days: 5 },
  { at: 4.05, days: 3 },
  { at: 5.35, days: 0 },
];
const LOOP = 7;
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const STRIP_SPRING = { mass: 1, stiffness: 180, damping: 17.5, reduceMotion: ReduceMotion.Never };
const ROLL_SPRING = { mass: 1, stiffness: 260, damping: 24, reduceMotion: ReduceMotion.Never };

const WEEKDAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const MONTHS = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'];

/** `count` days in a row, starting at the given date. */
export function daysFrom(year: number, month: number, day: number, count: number): Day[] {
  return Array.from({ length: count }, (_, offset) => {
    const date = new Date(Date.UTC(year, month - 1, day + offset));
    return {
      weekday: WEEKDAYS[date.getUTCDay()],
      number: String(date.getUTCDate()).padStart(2, '0'),
      month: MONTHS[date.getUTCMonth()],
    };
  });
}

// Four weeks from Monday 26 October 2026; Friday 6 November is index 11.
const DAYS = daysFrom(2026, 10, 26, 28);

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

type ColumnProps = {
  day: Day;
  index: number;
  strip: SharedValue<number>;
  /** The bar's width, for the fade at its right end. */
  barWidth: SharedValue<number>;
  /** The copy inside the lens: white, and never faded. */
  lit?: boolean;
};

/** One day: its weekday over its number, growing as it nears the lens. */
function Column({ day, index, strip, barWidth, lit = false }: ColumnProps) {
  const place = useAnimatedStyle(() => {
    const away = index - strip.get();
    if (Math.abs(away) >= REACH) return { opacity: 0, transform: [{ translateX: 0 }, { scale: 1 }] };
    const x = LENS_X + away * PITCH;
    // Days fade as they slide under the label and into the bar's right end.
    const fade = lit ? 1 : Math.min(clamp01((x - FADE_FROM) / FADE_OVER), clamp01((barWidth.get() - END_FADE - x) / END_OVER));
    return {
      opacity: fade,
      transform: [{ translateX: away * PITCH }, { scale: 1 + GROW * clamp01(1 - Math.abs(away)) }],
    };
  });

  return (
    <Animated.View style={[styles.column, place]}>
      <Text style={[styles.weekday, lit && styles.white]} numberOfLines={1}>
        {day.weekday}
      </Text>
      <Text style={[styles.number, lit && styles.white]} numberOfLines={1}>
        {day.number}
      </Text>
    </Animated.View>
  );
}

/**
 * A date picker in a white capsule: a label on the left, and a strip of days that slides under a
 * fixed lens, a tall blue capsule that stands proud of the bar and shows the day beneath it in white.
 * Dragging moves the strip, and it settles on the nearest day; tapping a day brings it under the
 * lens. The lens is soft: it stretches and leans with the strip's speed and wobbles as it stops.
 */
export function DateLensStrip({ days = DAYS, initial = 11, label = 'PICK A DATE:', autoPlay = false, onSelect }: Props) {
  const still = useReducedMotion();
  const last = Math.max(days.length - 1, 0);
  const start = Math.min(Math.max(initial, 0), last);
  const auto = autoPlay && !still && days.length > 1;
  const months = days.map((day) => day.month);

  // The strip: the index under the lens, continuous while it slides.
  const strip = useSharedValue(start);
  // The label's roll: 0 the label, 1 a month's name.
  const roll = useSharedValue(0);
  // The lens's stretch, −1…1 and a little beyond: a spring that chases the strip's speed.
  const stretch = useSharedValue(0);
  const stretchSpeed = useSharedValue(0);
  // The strip's position when the last frame was drawn.
  const seen = useSharedValue(start);
  const barWidth = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const dragZero = useSharedValue(0);
  // A finger is on the strip and has not yet sent it anywhere.
  const held = useSharedValue(false);
  // The strip is sliding under a finger, not under the script: only then it ticks.
  const byHand = useSharedValue(false);
  const [month, setMonth] = useState('');
  // Counts touches: each one stops the script and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const touch = () => setTouches((n) => n + 1);
  const chosen = (target: number) => onSelect?.(target);

  // Steps the lens's spring towards the strip's speed; small steps keep it steady when a frame is late.
  useFrameCallback((frame) => {
    const passed = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.25);
    if (passed <= 0) return;
    const pull = Math.min(Math.max((strip.get() - seen.get()) / passed / FULL_SPEED, -1), 1);
    seen.set(strip.get());
    const steps = Math.ceil(passed / TICK);
    const dt = passed / steps;
    let at = stretch.get();
    let speed = stretchSpeed.get();
    for (let i = 0; i < steps; i++) {
      speed += (LENS_STIFFNESS * (pull - at) - LENS_DAMPING * speed) * dt;
      at += speed * dt;
    }
    if (pull === 0 && Math.abs(at) < 0.001 && Math.abs(speed) < 0.01) {
      at = 0;
      speed = 0;
    }
    stretch.set(at);
    stretchSpeed.set(speed);
  }, !still);

  // When another day comes nearest the lens it ticks, and the label rolls if the month changed.
  useAnimatedReaction(
    () => Math.min(Math.max(Math.round(strip.get()), 0), last),
    (now, before) => {
      if (before === null || now === before) return;
      if (months[now] !== months[before]) {
        scheduleOnRN(setMonth, months[now]);
        // Under reduced motion the name appears and leaves without rolling.
        const jump = { duration: 0, reduceMotion: ReduceMotion.Never };
        roll.set(
          still
            ? withSequence(withTiming(1, jump), withDelay(MONTH_TIME * 1000, withTiming(0, jump), ReduceMotion.Never))
            : withSequence(withSpring(1, ROLL_SPRING), withDelay(MONTH_TIME * 1000 - 500, withSpring(0, ROLL_SPRING))),
        );
      }
      if (byHand.get()) scheduleOnRN(tick);
    },
  );

  /** Sends the strip to a day. */
  const select = (index: number) => {
    const target = Math.min(Math.max(Math.round(index), 0), last);
    strip.set(still ? target : withSpring(target, STRIP_SPRING));
    chosen(target);
  };

  // The timers below call whatever this is at the time they fire.
  const actions = useRef({ select });
  useEffect(() => {
    actions.current = { select };
  });

  // The script a finger would follow: four moves along the strip, the last one back to where it began.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = SCRIPT[n % SCRIPT.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + step.at) * 1000;
      timer = setTimeout(() => {
        actions.current.select(start + step.days);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, bring the first day back, and start over from rest.
      timer = setTimeout(() => {
        if (held.get()) return;
        byHand.set(false);
        actions.current.select(start);
        run(Date.now(), 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, start, held, byHand]);

  // A touch catches the strip, a drag slides it and lets go with momentum.
  const pan = Gesture.Pan()
    .minDistance(SLOP)
    .onBegin(() => {
      cancelAnimation(strip);
      held.set(true);
      byHand.set(true);
      dragFrom.set(strip.get());
      scheduleOnRN(touch);
    })
    .onStart((event) => {
      dragZero.set(event.translationX);
    })
    .onUpdate((event) => {
      // Past either end the strip follows at a third of the finger's speed.
      const free = dragFrom.get() - (event.translationX - dragZero.get()) / PITCH;
      strip.set(free < 0 ? free * RUBBER : free > last ? last + (free - last) * RUBBER : free);
    })
    .onEnd((event) => {
      const speed = Math.min(Math.max(-event.velocityX / PITCH, -MAX_SPEED), MAX_SPEED);
      const target = Math.min(Math.max(Math.round(strip.get() + speed * GLIDE), 0), last);
      held.set(false);
      strip.set(still ? target : withSpring(target, { ...STRIP_SPRING, velocity: speed }));
      scheduleOnRN(chosen, target);
    })
    .onFinalize(() => {
      // A touch that neither dragged nor tapped a day lets the strip fall onto the nearest one.
      if (held.get()) {
        held.set(false);
        const target = Math.min(Math.max(Math.round(strip.get()), 0), last);
        strip.set(still ? target : withSpring(target, STRIP_SPRING));
      }
      scheduleOnRN(touch);
    });

  // A touch that never moved brings the day under it to the lens.
  const tap = Gesture.Tap()
    .maxDistance(SLOP)
    .onEnd((event) => {
      const x = event.x - MARGIN;
      const onDays = x > FADE_FROM && x < barWidth.get() && Math.abs(event.y - HEIGHT / 2) < LENS_H / 2;
      const target = Math.min(Math.max(Math.round(strip.get() + (onDays ? (x - LENS_X) / PITCH : 0)), 0), last);
      held.set(false);
      strip.set(still ? target : withSpring(target, STRIP_SPRING));
      scheduleOnRN(chosen, target);
    });

  // The lens stretches sideways with the strip's speed, thins, and leans the way the days travel.
  const lens = useAnimatedStyle(() => {
    const pull = Math.min(Math.abs(stretch.get()), 1.15);
    const width = LENS_W + STRETCH_W * pull;
    const height = LENS_H - SQUASH_H * pull;
    return {
      left: MARGIN + LENS_X - LEAN * stretch.get() - width / 2,
      top: (HEIGHT - height) / 2,
      width,
      height,
      borderRadius: width / 2,
    };
  });
  // The same days again in white, held still under the moving lens: a number half-way in is half white.
  const glass = useAnimatedStyle(() => {
    const pull = Math.min(Math.abs(stretch.get()), 1.15);
    const width = LENS_W + STRETCH_W * pull;
    const height = LENS_H - SQUASH_H * pull;
    return { left: -(LENS_X - LEAN * stretch.get() - width / 2), top: (height - BAR_H) / 2 };
  });
  // The label rolls up and a month's name rolls in from below, then back.
  const title = useAnimatedStyle(() => ({
    opacity: 1 - clamp01(roll.get()),
    transform: [{ translateY: -ROLL_Y * roll.get() }],
  }));
  const name = useAnimatedStyle(() => ({
    opacity: clamp01(roll.get()),
    transform: [{ translateY: ROLL_Y * (1 - roll.get()) }],
  }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.root}
        onLayout={(event) => barWidth.set(event.nativeEvent.layout.width - MARGIN * 2)}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={label}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={(event) => {
          touch();
          select(Math.round(strip.get()) + (event.nativeEvent.actionName === 'increment' ? 1 : -1));
        }}
      >
        <View style={styles.bar}>
          <View pointerEvents="none" style={styles.inside}>
            <Animated.Text style={[styles.label, title]} numberOfLines={1}>
              {label}
            </Animated.Text>
            <Animated.Text style={[styles.label, name]} numberOfLines={1}>
              {month}:
            </Animated.Text>
            {days.map((day, index) => (
              <Column key={index} day={day} index={index} strip={strip} barWidth={barWidth} />
            ))}
          </View>
        </View>
        <Animated.View pointerEvents="none" style={[styles.lens, lens]}>
          <Animated.View style={[styles.glass, glass]}>
            {days.map((day, index) => (
              <Column key={index} day={day} index={index} strip={strip} barWidth={barWidth} lit />
            ))}
          </Animated.View>
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    alignSelf: 'stretch',
    height: HEIGHT,
  },
  bar: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    top: (HEIGHT - BAR_H) / 2,
    height: BAR_H,
    borderRadius: BAR_H / 2,
    backgroundColor: '#ffffff',
    boxShadow: '0 8 24 0 rgba(0,0,0,0.1)',
  },
  inside: {
    flex: 1,
    borderRadius: BAR_H / 2,
    overflow: 'hidden',
  },
  label: {
    position: 'absolute',
    left: LABEL_X,
    top: BAR_H / 2 - 10,
    height: 20,
    lineHeight: 20,
    fontSize: 13,
    fontWeight: '700',
    color: '#1414C8',
  },
  column: {
    position: 'absolute',
    left: LENS_X - PITCH / 2,
    top: 0,
    width: PITCH,
    height: BAR_H,
  },
  weekday: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: BAR_H / 2 - WEEKDAY_Y - 7,
    height: 14,
    lineHeight: 14,
    textAlign: 'center',
    fontSize: 9.5,
    fontWeight: '500',
    letterSpacing: 0.3,
    color: '#8C8C8C',
  },
  number: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: BAR_H / 2 + NUMBER_Y - 14,
    height: 28,
    lineHeight: 28,
    textAlign: 'center',
    fontSize: 21,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    color: '#000000',
  },
  white: {
    color: '#ffffff',
  },
  lens: {
    position: 'absolute',
    backgroundColor: '#0000F5',
    overflow: 'hidden',
  },
  glass: {
    position: 'absolute',
    width: 0,
    height: BAR_H,
  },
});
