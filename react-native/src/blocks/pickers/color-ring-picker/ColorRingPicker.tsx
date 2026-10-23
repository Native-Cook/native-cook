import { Canvas, Circle } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The colours, clockwise from 12 o'clock. */
  colors?: string[];
  /** The colour chosen at first; `autoPlay` comes back to it at the end of every loop. */
  selection?: number;
  title?: string;
  message?: string;
  action?: string;
  /** Picks one colour after another by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the index of the colour that was picked. */
  onChange?: (index: number) => void;
  /** Called with the index of the chosen colour when the button is tapped. */
  onFinish?: (index: number) => void;
};

const WIDTH = 340;
// The square the ring is drawn in; the slots sit on a circle of `RADIUS` around its centre.
const BOX = 240;
const RADIUS = 98;
// A circle's width across and its line, on the ring and in the middle.
const SMALL = 26;
const SMALL_LINE = 2.5;
const LARGE = 100;
const LARGE_LINE = 10;
// A finger this close to a slot's centre picks it.
const REACH = 22;
const BUTTON_W = 146;
const BUTTON_H = 36;

// autoPlay (seconds): the first pick, then one every `STEP_TIME`.
const LOOP = 8;
const FIRST_AT = 1;
const STEP_TIME = 1.15;
// Where each pick is, in turns of the ring clockwise from the resting colour.
const TURNS = [0.125, 0.5, 0.25, 0.875, 0.4375, 0];
// After a touch the loop waits this long before it starts over.
const RESUME_AFTER = 2;

const TRAVEL_SPRING = { mass: 1, stiffness: 220, damping: 21, reduceMotion: ReduceMotion.Never };

const COLORS = [
  '#e5383b',
  '#3ddc97',
  '#8e9196',
  '#f06a8c',
  '#1b1e22',
  '#1b8db8',
  '#1fa463',
  '#f3c1ae',
  '#2456e0',
  '#c6f221',
  '#f0743e',
  '#a9a2d6',
  '#5fa8a8',
  '#b58a93',
  '#f2e14c',
  '#6a2fbf',
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Centre of slot `index` in the ring's box. */
function place(index: number, count: number) {
  'worklet';
  const angle = (index / Math.max(count, 1)) * 2 * Math.PI - Math.PI / 2;
  return { x: BOX / 2 + Math.cos(angle) * RADIUS, y: BOX / 2 + Math.sin(angle) * RADIUS };
}

/** The slot under a finger, or -1. */
function slotAt(x: number, y: number, count: number) {
  'worklet';
  let best = -1;
  let nearest = REACH;
  for (let i = 0; i < count; i++) {
    const at = place(i, count);
    const distance = Math.hypot(x - at.x, y - at.y);
    if (distance < nearest) {
      nearest = distance;
      best = i;
    }
  }
  return best;
}

/** Up in 80 ms, a 40 ms hold, back in 180 ms: a tap. */
function pulse() {
  const linear = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };
  return withSequence(withTiming(1, { duration: 80, ...linear }), withDelay(40, withTiming(0, { duration: 180, ...linear })));
}

type RingProps = {
  index: number;
  count: number;
  color: string;
  /** This colour is the chosen one: its circle is in the middle, large. */
  chosen: boolean;
  still: boolean;
};

/** One colour's circle, in its slot on the ring or in the middle. */
function Ring({ index, count, color, chosen, still }: RingProps) {
  // 0 in its slot on the ring … 1 in the middle, large.
  const lift = useSharedValue(chosen ? 1 : 0);
  // The swell of a circle that was just tapped.
  const swell = useSharedValue(0);

  useEffect(() => {
    // Under reduced motion nothing travels: the two circles change places at once.
    if (still) {
      lift.set(chosen ? 1 : 0);
      return;
    }
    lift.set(withSpring(chosen ? 1 : 0, TRAVEL_SPRING));
    if (chosen) swell.set(pulse());
  }, [chosen, still, lift, swell]);

  const from = place(index, count);
  const cx = useDerivedValue(() => mix(from.x, BOX / 2, lift.get()));
  const cy = useDerivedValue(() => mix(from.y, BOX / 2, lift.get()));
  const line = useDerivedValue(() => Math.max(1.5, mix(SMALL_LINE, LARGE_LINE, lift.get())));
  const r = useDerivedValue(() => {
    const t = lift.get();
    // A tapped circle swells in place before it sets off.
    const across = Math.max(8, mix(SMALL, LARGE, t)) * (1 + 0.25 * swell.get() * (1 - clamp01(t * 3)));
    return (across - line.get()) / 2;
  });

  return <Circle cx={cx} cy={cy} r={r} color={color} style="stroke" strokeWidth={line} />;
}

/**
 * A colour picker laid out as a ring. Every colour is a small outlined circle on the ring, except
 * the chosen one: its place is empty and it sits in the middle as a large, thick ring. Tapping a
 * circle (or sliding a finger over it) swaps the two: the large ring shrinks back to its place while
 * the tapped one travels to the middle and grows.
 */
export function ColorRingPicker({
  colors = COLORS,
  selection = 1,
  title = 'Pick a color',
  message = 'Color coding a space makes it easier\nto spot when you need it.',
  action = 'FINISH & SAVE',
  autoPlay = false,
  onChange,
  onFinish,
}: Props) {
  const still = useReducedMotion();
  const count = colors.length;
  const auto = autoPlay && !still && count > 1;
  const rest = Math.min(Math.max(selection, 0), count - 1);

  const [selected, setSelected] = useState(rest);
  const [origin, setOrigin] = useState(rest);
  if (origin !== rest) {
    setOrigin(rest);
    setSelected(rest);
  }
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);
  // A finger is on the ring.
  const [holding, setHolding] = useState(false);
  // The slot under the finger, so a slot is picked once while the finger stays on it.
  const over = useSharedValue(-1);
  const press = useSharedValue(0);

  /** Picks colour `index`: it travels to the middle while the one that was there goes back to its slot. */
  const select = (index: number) => {
    if (index < 0 || index >= count || index === selected) return;
    setSelected(index);
    onChange?.(index);
  };

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const pick = (index: number) => {
    if (index === selected) return;
    tick();
    select(index);
  };

  const grab = () => {
    setHolding(true);
    setTouches((n) => n + 1);
  };

  const release = () => {
    setHolding(false);
    setTouches((n) => n + 1);
  };

  // The timers below call whatever this is at the time they fire.
  const steps = useRef({ select });
  useEffect(() => {
    steps.current = { select };
  });

  // The script a finger would follow: six picks on alternating sides of the ring, the last one back
  // on the colour the loop started with.
  useEffect(() => {
    if (!auto || holding) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = n % TURNS.length;
      // Every pick is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / TURNS.length) * LOOP + FIRST_AT + step * STEP_TIME) * 1000;
      timer = setTimeout(() => {
        steps.current.select((rest + Math.round(TURNS[step] * count)) % count);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, go back to the first colour and start over.
      timer = setTimeout(() => {
        steps.current.select(rest);
        run(Date.now(), 0);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, holding, rest, count]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const slot = slotAt(event.x, event.y, count);
      over.set(slot);
      scheduleOnRN(grab);
      if (slot >= 0) scheduleOnRN(pick, slot);
    })
    .onUpdate((event) => {
      const slot = slotAt(event.x, event.y, count);
      if (slot === over.get()) return;
      over.set(slot);
      if (slot >= 0) scheduleOnRN(pick, slot);
    })
    .onFinalize(() => {
      over.set(-1);
      scheduleOnRN(release);
    });

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.06 * press.get() }] }));

  // The chosen circle is drawn last, over the one it crosses on its way in.
  const order = colors.map((_, index) => index).filter((index) => index !== selected);
  if (selected < count) order.push(selected);

  return (
    <View style={styles.root}>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>
      <Text style={styles.message}>{message}</Text>

      <GestureDetector gesture={pan}>
        <View
          style={styles.ring}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={title}
          accessibilityValue={{ text: `${selected + 1} of ${count}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            setTouches((n) => n + 1);
            select((selected + (event.nativeEvent.actionName === 'increment' ? 1 : count - 1)) % count);
          }}
        >
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            {order.map((index) => (
              <Ring key={index} index={index} count={count} color={colors[index]} chosen={index === selected} still={still} />
            ))}
          </Canvas>
        </View>
      </GestureDetector>

      <Animated.View style={[styles.button, buttonStyle]}>
        <Pressable
          style={styles.buttonTouch}
          accessibilityRole="button"
          accessibilityLabel={action}
          onPress={() => {
            setTouches((n) => n + 1);
            tick();
            if (!still) press.set(pulse());
            onFinish?.(selected);
          }}
        >
          <Text style={styles.action} numberOfLines={1}>
            {action}
          </Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    alignItems: 'center',
  },
  title: {
    height: 38,
    lineHeight: 38,
    color: '#ffffff',
    fontSize: 30,
    fontWeight: '300',
    fontFamily: process.env.EXPO_OS === 'ios' ? 'ui-serif' : 'serif',
  },
  message: {
    height: 42,
    marginTop: 10,
    color: '#8a8f98',
    fontSize: 15,
    lineHeight: 21,
    textAlign: 'center',
  },
  ring: {
    width: BOX,
    height: BOX,
    marginTop: 22,
  },
  button: {
    width: BUTTON_W,
    height: BUTTON_H,
    marginTop: 24,
    borderRadius: BUTTON_H / 2,
    backgroundColor: '#f2431e',
  },
  buttonTouch: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  action: {
    color: '#ffffff',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.4,
  },
});
