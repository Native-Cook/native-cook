import { Ionicons } from '@expo/vector-icons';
import { BlurMask, Canvas, Circle, Group, Oval, RadialGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  FadeIn,
  FadeOut,
  type WithSpringConfig,
  cancelAnimation,
  useAnimatedReaction,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BAR, BLEED, DRUM_AT, DRUM_WIDTH, PITCH, Roller, TILE_AT, TILE_HEIGHT, TILE_WIDTH, reach } from './Roller';

export type Finish = {
  name: string;
  /** The bead's colour, its lit side and its dark side. */
  color: string;
  light: string;
  dark: string;
};

type Props = {
  /** The quantity the bar starts with. */
  quantity?: number;
  /** The highest quantity: the last number on the tape. */
  max?: number;
  /** The price of one, in units of the currency. */
  price?: number;
  symbol?: string;
  name?: string;
  description?: string;
  /** The colours the product comes in, and the one chosen at first by its place in the list. */
  finishes?: Finish[];
  finish?: number;
  finishLabel?: string;
  addLabel?: string;
  /** The line under the bar. */
  deliveryLabel?: string;
  /** The colour of the small square beside it. */
  accent?: string;
  /** Presses the arrows by itself every LOOP seconds: up three times, down three times. */
  autoPlay?: boolean;
  onChange?: (quantity: number) => void;
  /** Called with the quantity and the total when the bar is pressed. */
  onAdd?: (quantity: number, total: number) => void;
  onBack?: () => void;
  onBag?: () => void;
};

const FINISHES: Finish[] = [
  { name: 'Cyber-Tech', color: '#f08a1c', light: '#ffd08a', dark: '#a85408' },
  { name: 'Blue-Steel', color: '#2f4de0', light: '#93a6ff', dark: '#16268f' },
];

const SURFACE = '#e3e3e1';
const INK = '#2d2d2f';
const TEXT = '#4a4a4c';
const MUTE = '#8b8a88';
const ACCENT = '#f26a1b';
// Clay: what is raised throws a soft shadow down and to the right and catches light from the top left.
const RAISED = [
  { offsetX: 5, offsetY: 6, blurRadius: 12, color: 'rgba(60,50,40,0.16)' },
  { offsetX: -4, offsetY: -4, blurRadius: 10, color: 'rgba(255,255,255,0.9)' },
];
const SUNK = [
  { offsetX: 4, offsetY: 5, blurRadius: 10, color: 'rgba(60,50,40,0.13)', inset: true },
  { offsetX: -4, offsetY: -4, blurRadius: 10, color: 'rgba(255,255,255,0.85)', inset: true },
];

// Sizes in points, for a 402 × 874 screen.
const MARGIN = 16;
const NAV = 44;
const ROUND = 40;
const SPEAKER = 172;
const BEAD = 30;
// Everything beside the tape starts here, so that the tape's lane stays clear above and below the bar.
const LANE = 132;
const ROW = 46;
const BAR_GAP = 22;

// A step: the tape shoots to the next label and lands with a small bounce.
const STEP: WithSpringConfig = { mass: 1, stiffness: 330, damping: 23 };
// At either end the tape gives by this much of a label's pitch and comes back.
const NUDGE = 0.14;
const NUDGE_IN = 90;
// Dragged past either end, the tape follows at this rate.
const RUBBER = 0.25;
// A flick carries the tape this many seconds further before it snaps to a label.
const FLICK = 0.12;
const HOLD_IN = 60;
const HOLD_OUT = 160;
const TOTAL_FADE = 140;

/** autoPlay, in seconds: an arrow every STEP_EVERY from FIRST_AT, each held for PRESS_FOR; +1 is up. */
const LOOP = 7.4;
const FIRST_AT = 0.8;
const STEP_EVERY = 0.9;
const TURN_PAUSE = 0.4;
const PRESS_FOR = 0.14;
const PRESSES = [1, 1, 1, -1, -1, -1];
/** After a touch autoPlay waits this long before it starts over from the first quantity. */
const USER_HOLD = 3;
// The tape back at rest after the last step, in seconds.
const RETURN = 0.8;

function haptic(kind: 'step' | 'tick' | 'limit' | 'press') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'limit') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
  else Haptics.impactAsync(kind === 'step' ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

function money(symbol: string, value: number) {
  return `${symbol}${value.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`;
}

/** A glossy bead of a finish's colour; the chosen one glows. */
function Bead({ finish, on }: { finish: Finish; on: boolean }) {
  const c = BEAD / 2;
  return (
    <Canvas style={styles.bead}>
      {on && (
        <Circle cx={c} cy={c} r={11} color={finish.color} opacity={0.55}>
          <BlurMask blur={4} style="normal" />
        </Circle>
      )}
      <Circle cx={c + 1} cy={c + 2} r={9} color="rgba(0,0,0,0.25)">
        <BlurMask blur={2} style="normal" />
      </Circle>
      <Circle cx={c} cy={c} r={9.5} color="#f6f5f3" />
      <Circle cx={c} cy={c} r={on ? 7 : 7.6}>
        <RadialGradient c={vec(c - 2.5, c - 3)} r={10} colors={[finish.light, finish.color, finish.dark]} positions={[0, 0.5, 1]} />
      </Circle>
      <Oval x={c - 4.6} y={c - 5.4} width={5} height={3} color="rgba(255,255,255,0.75)">
        <BlurMask blur={0.8} style="normal" />
      </Oval>
    </Canvas>
  );
}

/** The product: a round desk speaker in clay, its light in the chosen finish. */
function Speaker({ finish }: { finish: Finish }) {
  const c = SPEAKER / 2;
  const holes = Array.from({ length: 20 }, (_, index) => (index / 20) * Math.PI * 2);
  return (
    <Canvas style={styles.speaker}>
      <Circle cx={c + 8} cy={c + 11} r={62} color="rgba(60,50,40,0.3)">
        <BlurMask blur={14} style="normal" />
      </Circle>
      <Circle cx={c - 6} cy={c - 7} r={62} color="rgba(255,255,255,0.95)">
        <BlurMask blur={10} style="normal" />
      </Circle>
      <Circle cx={c} cy={c} r={66}>
        <RadialGradient c={vec(c - 22, c - 26)} r={110} colors={['#f6f5f3', '#e4e3e1', '#cbcac8']} positions={[0, 0.55, 1]} />
      </Circle>
      <Circle cx={c} cy={c} r={47}>
        <RadialGradient c={vec(c + 14, c + 18)} r={80} colors={['#e9e8e6', '#d6d5d3', '#c2c1bf']} positions={[0, 0.55, 1]} />
      </Circle>
      <Circle cx={c} cy={c} r={47} style="stroke" strokeWidth={1.2} color="rgba(255,255,255,0.8)" />
      {holes.map((angle) => (
        <Group key={angle}>
          <Circle cx={c + Math.cos(angle) * 34 + 0.6} cy={c + Math.sin(angle) * 34 + 0.8} r={2.3} color="rgba(255,255,255,0.8)" />
          <Circle cx={c + Math.cos(angle) * 34} cy={c + Math.sin(angle) * 34} r={2.3} color="#9f9e9c" />
        </Group>
      ))}
      <Circle cx={c} cy={c} r={21} color={finish.color} opacity={0.5}>
        <BlurMask blur={9} style="normal" />
      </Circle>
      <Circle cx={c} cy={c} r={17} color="#f4f3f1" />
      <Circle cx={c} cy={c} r={13.5}>
        <RadialGradient c={vec(c - 4.5, c - 5.5)} r={18} colors={[finish.light, finish.color, finish.dark]} positions={[0, 0.5, 1]} />
      </Circle>
      <Oval x={c - 8.5} y={c - 9.5} width={9} height={5.5} color="rgba(255,255,255,0.7)">
        <BlurMask blur={1.4} style="normal" />
      </Oval>
    </Canvas>
  );
}

type RoundProps = { icon: 'chevron-back' | 'bag-outline'; label: string; onPress: () => void };

function Round({ icon, label, onPress }: RoundProps) {
  return (
    <Pressable onPress={onPress} hitSlop={6} accessibilityRole="button" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View style={[styles.round, { transform: [{ scale: pressed ? 0.94 : 1 }], transitionProperty: 'transform', transitionDuration: 120 }]}>
          <Ionicons name={icon} size={20} color={TEXT} />
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A product screen in soft clay with an add-to-cart bar whose quantity is a roller: a paper tape of
 * hand-written numbers runs behind the bar and over an orange drum. An arrow shoots the tape to the
 * next number, smeared on its way, and it lands with a small bounce; the drum can also be dragged
 * up and down by hand. The total in the bar follows.
 */
export function PaperTapeQuantity({
  quantity = 1,
  max = 4,
  price = 49,
  symbol = '$',
  name = 'Halo Speaker',
  description = 'Desk speaker · 12-hour battery',
  finishes = FINISHES,
  finish = 1,
  finishLabel = 'Finish',
  addLabel = 'Add to cart',
  deliveryLabel = 'Order today to get it by Friday',
  accent = ACCENT,
  autoPlay = false,
  onChange,
  onAdd,
  onBack,
  onBag,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's width stands in until then.
  const [laidOut, setLaidOut] = useState<number | null>(null);
  const width = laidOut ?? window.width;
  const first = Math.min(max, Math.max(1, Math.round(quantity)));
  const [count, setCount] = useState(first);
  const [chosen, setChosen] = useState(finish);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Where the tape is, as the number in the drum's window: whole at rest, between two on its way.
  const place = useSharedValue(first);
  const upHeld = useSharedValue(0);
  const downHeld = useSharedValue(0);
  // The tape as it was caught by a finger, and whether a finger has it.
  const caught = useSharedValue(first);
  const dragging = useSharedValue(false);

  const set = (next: number, felt: boolean) => {
    setCount(next);
    if (felt) haptic('tick');
    onChange?.(next);
  };

  /** One press of an arrow: the tape shoots a label on, or gives and comes back at either end. */
  const step = (by: number, felt: boolean) => {
    const next = Math.min(max, Math.max(1, count + by));
    if (next === count) {
      if (felt) haptic('limit');
      if (!reducedMotion) place.set(withSequence(withTiming(count + by * NUDGE, { duration: NUDGE_IN }), withSpring(count, STEP)));
      return;
    }
    if (felt) haptic('step');
    setCount(next);
    onChange?.(next);
    place.set(withSpring(next, STEP));
  };

  /** Back to the quantity the bar was given. */
  const reset = () => {
    setCount(first);
    place.set(withSpring(first, STEP));
  };

  // While the drum is dragged, the quantity is the number nearest its window.
  useAnimatedReaction(
    () => Math.min(max, Math.max(1, Math.round(place.get()))),
    (now, old) => {
      if (dragging.get() && old !== null && now !== old) scheduleOnRN(set, now, true);
    },
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ step, reset });
  useEffect(() => {
    latest.current = { step, reset };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      let at = FIRST_AT;
      PRESSES.forEach((by, order) => {
        // A breath before the way back.
        if (order > 0 && by !== PRESSES[order - 1]) at += TURN_PAUSE;
        const held = by > 0 ? upHeld : downHeld;
        const due = origin + at * 1000;
        after(due - PRESS_FOR * 1000 - Date.now(), () => held.set(withTiming(1, { duration: HOLD_IN })));
        after(due - Date.now(), () => {
          held.set(withTiming(0, { duration: HOLD_OUT }));
          latest.current.step(by, false);
        });
        at += STEP_EVERY;
      });
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      // The loop starts over from the quantity it was given.
      latest.current.reset();
      after(RETURN * 1000, () => cycle(Date.now()));
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => {
      timers.forEach(clearTimeout);
      upHeld.set(0);
      downHeld.set(0);
    };
  }, [autoPlay, touches, upHeld, downHeld]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const settle = (to: number) => {
    setCount(to);
    onChange?.(to);
    touched();
  };

  const drag = Gesture.Pan()
    .activeOffsetY([-3, 3])
    .onBegin(() => {
      scheduleOnRN(touched);
    })
    .onStart(() => {
      cancelAnimation(place);
      caught.set(place.get());
      dragging.set(true);
    })
    .onChange((event) => {
      // Up the screen is up the numbers.
      const raw = caught.get() - event.translationY / PITCH;
      const held = raw < 1 ? 1 - (1 - raw) * RUBBER : raw > max ? max + (raw - max) * RUBBER : raw;
      place.set(held);
    })
    .onEnd((event) => {
      dragging.set(false);
      const speed = -event.velocityY / PITCH;
      const to = Math.min(max, Math.max(1, Math.round(place.get() + speed * FLICK)));
      place.set(withSpring(to, { ...STEP, velocity: speed }));
      scheduleOnRN(settle, to);
    })
    .onFinalize(() => {
      dragging.set(false);
    });

  const onLayout = (event: LayoutChangeEvent) => setLaidOut(event.nativeEvent.layout.width);

  const lane = reach(max);
  const total = price * count;
  const selected = finishes[chosen];
  const hold = (held: typeof upHeld, down: boolean) => held.set(withTiming(down ? 1 : 0, { duration: down ? HOLD_IN : HOLD_OUT }));

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]} onLayout={onLayout}>
      <StatusBar style="dark" />
      <View style={styles.nav}>
        <Round
          icon="chevron-back"
          label="Back"
          onPress={() => {
            touched();
            onBack?.();
          }}
        />
        <Round
          icon="bag-outline"
          label="Bag"
          onPress={() => {
            touched();
            onBag?.();
          }}
        />
      </View>

      <View style={styles.hero}>
        <Speaker finish={selected} />
      </View>

      <View style={styles.heading}>
        <Text style={styles.name} numberOfLines={1}>
          {name}
        </Text>
        <Text style={styles.price} numberOfLines={1}>
          {money(symbol, price)}
        </Text>
      </View>
      <Text style={styles.description} numberOfLines={1}>
        {description}
      </Text>

      <View style={styles.rule} />
      {/* The tape stands in the left of this part when the quantity is high, so its rows keep to the right. */}
      <View style={[styles.finishes, { height: lane - BAR_GAP }]} accessibilityRole="radiogroup" accessibilityLabel={finishLabel}>
        <Text style={styles.caption} numberOfLines={1}>
          {finishLabel}
        </Text>
        {finishes.map((item, index) => (
          <Pressable
            key={item.name}
            style={styles.finish}
            accessibilityRole="radio"
            accessibilityState={{ selected: index === chosen }}
            onPress={() => {
              touched();
              haptic('tick');
              setChosen(index);
            }}
          >
            <Bead finish={item} on={index === chosen} />
            <Text style={[styles.finishName, index === chosen && styles.finishOn]} numberOfLines={1}>
              {item.name}
            </Text>
          </Pressable>
        ))}
      </View>
      <View style={styles.rule} />

      <View style={styles.bar}>
        <View pointerEvents="none" style={[styles.roller, { top: -(lane + BLEED) }]}>
          <Roller width={width} margin={MARGIN} max={max} place={place} upHeld={upHeld} downHeld={downHeld} canUp={count < max} canDown={count > 1} still={reducedMotion} />
        </View>

        <GestureDetector gesture={drag}>
          <View
            style={[styles.drum, { left: MARGIN + DRUM_AT - DRUM_WIDTH / 2 - 6 }]}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Quantity"
            accessibilityValue={{ min: 1, max, now: count }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(event) => {
              touched();
              step(event.nativeEvent.actionName === 'increment' ? 1 : -1, true);
            }}
          />
        </GestureDetector>
        <Pressable
          style={[styles.arrow, { left: MARGIN + TILE_AT, top: (BAR - TILE_HEIGHT) / 2 }]}
          hitSlop={{ top: 14, left: 6, right: 8 }}
          accessibilityRole="button"
          accessibilityLabel="One more"
          onPressIn={() => hold(upHeld, true)}
          onPressOut={() => hold(upHeld, false)}
          onPress={() => {
            touched();
            step(1, true);
          }}
        />
        <Pressable
          style={[styles.arrow, { left: MARGIN + TILE_AT, top: BAR / 2 }]}
          hitSlop={{ bottom: 14, left: 6, right: 8 }}
          accessibilityRole="button"
          accessibilityLabel="One fewer"
          onPressIn={() => hold(downHeld, true)}
          onPressOut={() => hold(downHeld, false)}
          onPress={() => {
            touched();
            step(-1, true);
          }}
        />
        <Pressable
          style={[styles.add, { left: MARGIN + TILE_AT + TILE_WIDTH + 14 }]}
          accessibilityRole="button"
          accessibilityLabel={`${addLabel}, ${count}, ${money(symbol, total)}`}
          onPress={() => {
            touched();
            haptic('press');
            onAdd?.(count, total);
          }}
        >
          {({ pressed }) => (
            <Animated.View style={{ opacity: pressed ? 0.45 : 1, transitionProperty: 'opacity', transitionDuration: 120 }}>
              <Text style={styles.addLabel} numberOfLines={1}>
                {addLabel}
              </Text>
              <View style={styles.totalSlot}>
                <Animated.Text key={count} style={styles.total} numberOfLines={1} entering={FadeIn.duration(TOTAL_FADE)} exiting={FadeOut.duration(TOTAL_FADE)}>
                  {money(symbol, total)}
                </Animated.Text>
              </View>
            </Animated.View>
          )}
        </Pressable>
      </View>

      <View style={styles.delivery}>
        <View style={[styles.bullet, { backgroundColor: accent }]} />
        <Ionicons name="time" size={20} color={TEXT} />
        <Text style={styles.deliveryLabel} numberOfLines={1}>
          {deliveryLabel}
        </Text>
      </View>
      {/* Room for the tape to hang below the bar at the lowest quantity. */}
      <View style={{ height: Math.max(lane - 38, 24) + Math.max(insets.bottom - 14, 8) }} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SURFACE,
  },
  nav: {
    height: NAV,
    paddingHorizontal: MARGIN,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  round: {
    width: ROUND,
    height: ROUND,
    borderRadius: ROUND / 2,
    backgroundColor: SURFACE,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: RAISED,
  },
  hero: {
    flex: 1,
    minHeight: 120,
    marginTop: 14,
    marginHorizontal: MARGIN,
    borderRadius: 30,
    borderCurve: 'continuous',
    backgroundColor: SURFACE,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: SUNK,
  },
  speaker: {
    width: SPEAKER,
    height: SPEAKER,
  },
  heading: {
    marginTop: 20,
    marginHorizontal: MARGIN + 4,
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
  },
  name: {
    flexShrink: 1,
    color: INK,
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  price: {
    marginLeft: 12,
    color: INK,
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  description: {
    marginTop: 2,
    marginHorizontal: MARGIN + 4,
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
  },
  // A groove in the clay: a dark line with a light one under it.
  rule: {
    height: 1,
    marginTop: 14,
    marginLeft: MARGIN,
    backgroundColor: 'rgba(0,0,0,0.07)',
    boxShadow: [{ offsetX: 0, offsetY: 1, blurRadius: 0, color: 'rgba(255,255,255,0.75)' }],
  },
  finishes: {
    paddingLeft: LANE,
    justifyContent: 'center',
  },
  caption: {
    marginBottom: 2,
    marginLeft: 4,
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  finish: {
    height: ROW,
    flexDirection: 'row',
    alignItems: 'center',
  },
  bead: {
    width: BEAD,
    height: BEAD,
  },
  finishName: {
    marginLeft: 8,
    color: MUTE,
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '500',
  },
  finishOn: {
    color: INK,
  },
  bar: {
    height: BAR,
    marginTop: BAR_GAP,
    // The roller's canvas overflows the bar, over what comes before and after it.
    zIndex: 1,
  },
  roller: {
    position: 'absolute',
    left: 0,
  },
  drum: {
    position: 'absolute',
    top: 0,
    width: DRUM_WIDTH + 12,
    height: BAR,
  },
  arrow: {
    position: 'absolute',
    width: TILE_WIDTH,
    height: TILE_HEIGHT / 2,
  },
  add: {
    position: 'absolute',
    right: MARGIN,
    top: 0,
    height: BAR,
    justifyContent: 'center',
  },
  addLabel: {
    color: TEXT,
    fontSize: 21,
    lineHeight: 26,
    fontWeight: '500',
  },
  totalSlot: {
    height: 20,
  },
  total: {
    position: 'absolute',
    left: 0,
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  delivery: {
    height: 24,
    marginTop: 26,
    paddingLeft: LANE,
    flexDirection: 'row',
    alignItems: 'center',
  },
  bullet: {
    position: 'absolute',
    left: MARGIN + 6,
    width: 8,
    height: 8,
    borderRadius: 1.5,
  },
  deliveryLabel: {
    marginLeft: 8,
    color: TEXT,
    fontSize: 15,
    lineHeight: 20,
  },
});
