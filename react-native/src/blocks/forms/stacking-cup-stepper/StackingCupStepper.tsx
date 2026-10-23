import { Ionicons } from '@expo/vector-icons';
import { Canvas, Group } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CUP_WIDTH, Cup, type CupShape, cupHeight, useCupShape } from './Cup';

export type CupSize = {
  label: string;
  /** Written under the size. */
  volume: string;
  /** Milligrams of caffeine in one cup of this size. */
  caffeine: number;
  /** The cup itself: the height of its body in points (the lid and the sleeve keep theirs), and how much the whole cup is scaled. */
  body: number;
  scale: number;
};

export type Order = { size: CupSize; quantity: number; caffeine: number };

type Props = {
  title?: string;
  subtitle?: string;
  /** The time on the chip over the timeline, and the hours under it (one every eight ticks). */
  suggestedTime?: string;
  hours?: string[];
  drink?: string;
  /** The time on the chip beside the drink's name. */
  time?: string;
  sizes?: CupSize[];
  /** The size and the number of cups the form starts with. */
  size?: number;
  quantity?: number;
  /** The most cups the stepper goes to, five at most. */
  max?: number;
  figureLabel?: string;
  unit?: string;
  confirmLabel?: string;
  /** Goes through sizes and quantities by itself every LOOP seconds. */
  autoPlay?: boolean;
  onChange?: (order: Order) => void;
  onConfirm?: (order: Order) => void;
  onBack?: () => void;
  onInfo?: () => void;
};

const SIZES: CupSize[] = [
  { label: 'Small', volume: '237 ml', caffeine: 75, body: 62, scale: 1 },
  { label: 'Medium', volume: '355 ml', caffeine: 110, body: 84, scale: 1.02 },
  { label: 'Large', volume: '473 ml', caffeine: 150, body: 76, scale: 1.3 },
  { label: 'Extra Large', volume: '591 ml', caffeine: 190, body: 90, scale: 1.38 },
];
const HOURS = ['8 AM', '10 AM', '12 PM', '2 PM'];

const BACKGROUND = '#000000';
const SHEET = '#171717';
const HAIRLINE = '#242424';
const CHIP = '#242424';
const INK = '#FFFFFF';
const TITLE_INK = '#DADADA';
const MUTE = '#8C8C8C';
const PILL = '#222222';
const PILL_PRESSED = '#303030';
const PILL_INK = '#6C6C6C';
const VOLUME_OFF = '#2E2E2E';
const ROUND = '#2B2B2B';
const ROUND_PRESSED = '#3D3D3D';
const CONFIRM_PRESSED = '#BDBDBD';
// The timeline's ticks: before the suggested time, at it, and after.
const TICK_EARLY = '#C78A4E';
const TICK_NOW = '#F2F2F2';
const TICK_LATE = '#3F9C7B';

const MARGIN = 16;
const NAV = 44;
// The timeline: a tick every TICK_STEP points from TICK_LEFT, the chip over tick TICK_AT.
const TICKS = 28;
const TICK_LEFT = 82;
const TICK_STEP = 12.3;
const TICK_AT = 10;
const HEADER = 58;
const PILL_HEIGHT = 37;
const PILL_GAP = 7;
const STEPPER_WIDTH = 145;
const STEPPER_HEIGHT = 38;
const ROUND_SIZE = 32;
const CONFIRM = 40;
// The cups stand this far above the bottom of their area, and stay this far under its top.
const GROUND = 30;
const HEADROOM = 10;
const MAX_CUPS = 5;

/**
 * Where each cup stands for one to five cups, as x and y from the middle of the ground in points
 * of a scale-1 cup (negative y is further back). Cups keep their place in the list as others come,
 * and a list is drawn in order, so the later cups stand in front.
 */
const SLOTS = [
  [[0, 0]],
  [
    [-40, 0],
    [40, 0],
  ],
  [
    [-46, -30],
    [46, -30],
    [0, 2],
  ],
  [
    [-50, -32],
    [50, -32],
    [-38, 3],
    [38, 3],
  ],
  [
    [-38, -33],
    [38, -33],
    [-74, 2],
    [74, 2],
    [0, 5],
  ],
];

// The body's height and the cup's scale follow the size on a soft spring that overshoots.
const STRETCH = { stiffness: 170, damping: 13, mass: 1 };
// A growing cup throws its lid: HOP_AFTER seconds after the tap the lid leaves the body, rises
// HOP_HEIGHT points (of the cup's own scale) on a ballistic arc, lands after HOP_SPLIT of HOP_FOR
// and bounces once more, HOP_BOUNCE as high.
const HOP_AFTER = 0.14;
const HOP_FOR = 0.56;
const HOP_HEIGHT = 10;
const HOP_SPLIT = 0.76;
const HOP_BOUNCE = 0.16;
// A new cup falls for FALL seconds from DROP points up and SIDE points to the right, tilted by
// TILT degrees, then rocks (degrees, fading per second, radians per second) and is squashed for a
// moment (share of its height, fading, rate).
const FALL = 0.42;
const DROP = 150;
const SIDE = 46;
const TILT = 16;
const WOBBLE = 7;
const WOBBLE_FADE = 8;
const WOBBLE_RATE = 17;
const SQUASH = 0.1;
const SQUASH_FADE = 10;
const SQUASH_RATE = 22;
// The cup's own clock stops here, well after it has come to rest.
const SETTLED = 1.6;
// The cups already there slide to their new places on this spring and rock by NUDGE degrees when
// the new one lands.
const SLIDE = { stiffness: 210, damping: 17, mass: 1 };
const NUDGE = 2.4;
// A cup taken away rises, tips and fades.
const LEAVE = 0.24;
const LEAVE_RISE = 46;
const LEAVE_SIDE = 26;
const LEAVE_TILT = 14;
const THUMB = { stiffness: 320, damping: 28, mass: 1 };
// The figure counts to its new value.
const COUNT = 0.45;
// The count in the stepper comes in from this far above or below.
const ROLL = 9;

/** autoPlay, in seconds into each loop: a size by its index, one cup more, or one less. */
export const LOOP = 9.6;
const STEPS: [number, 'size' | 'more' | 'less', number][] = [
  [0.8, 'size', 1],
  [2.0, 'size', 2],
  [3.3, 'more', 0],
  [4.5, 'more', 0],
  [5.8, 'less', 0],
  [6.8, 'less', 0],
  [7.8, 'size', 0],
];
const PRESS_FOR = 0.14;
/** After a touch autoPlay waits this long, puts the form back and starts over. */
const USER_HOLD = 3;
const RESET_FOR = 0.9;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** The lid's height over the body through its hop, 0 to 1. */
function hopAt(t: number) {
  'worklet';
  if (t <= 0 || t >= 1) return 0;
  if (t < HOP_SPLIT) return (4 * t * (HOP_SPLIT - t)) / (HOP_SPLIT * HOP_SPLIT);
  const u = (t - HOP_SPLIT) / (1 - HOP_SPLIT);
  return HOP_BOUNCE * 4 * u * (1 - u);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type PiledCupProps = {
  index: number;
  quantity: number;
  shape: CupShape;
  scale: SharedValue<number>;
  /** Seconds since the newest cup landed; negative while it falls. */
  landed: SharedValue<number>;
};

/** One cup of the pile: it falls into its place, makes room for the next ones and leaves. */
function PiledCup({ index, quantity, shape, scale, landed }: PiledCupProps) {
  const present = index < quantity;
  const slotX = present ? SLOTS[quantity - 1][index][0] : 0;
  const slotY = present ? SLOTS[quantity - 1][index][1] : 0;
  const x = useSharedValue(slotX);
  const y = useSharedValue(slotY);
  // Seconds since it was dropped in: SETTLED at rest, negative while it is not there.
  const age = useSharedValue(present ? SETTLED : -1);
  const away = useSharedValue(0);
  const was = useRef(present);

  useEffect(() => {
    if (present && !was.current) {
      cancelAnimation(away);
      away.set(0);
      x.set(slotX);
      y.set(slotY);
      age.set(0);
      age.set(withTiming(SETTLED, { duration: SETTLED * 1000, easing: Easing.linear }));
    } else if (present) {
      x.set(withSpring(slotX, SLIDE));
      y.set(withSpring(slotY, SLIDE));
    } else if (was.current) {
      cancelAnimation(age);
      away.set(
        withTiming(1, { duration: LEAVE * 1000, easing: Easing.in(Easing.quad) }, (finished) => {
          if (!finished) return;
          age.set(-1);
          away.set(0);
        }),
      );
    }
    was.current = present;
  }, [present, slotX, slotY, x, y, age, away]);

  const transform = useDerivedValue(() => {
    const size = scale.get();
    const fallen = clamp01(age.get() / FALL);
    const since = age.get() - FALL;
    const nudged = landed.get();
    let tilt = TILT * (1 - fallen);
    let squash = 0;
    if (since > 0 && age.get() < SETTLED) {
      tilt = -WOBBLE * Math.exp(-WOBBLE_FADE * since) * Math.sin(WOBBLE_RATE * since);
      squash = SQUASH * Math.exp(-SQUASH_FADE * since) * Math.cos(SQUASH_RATE * since);
    } else if (age.get() >= SETTLED && nudged > 0) {
      // The cups that were there rock away from each other.
      tilt = (index % 2 === 0 ? -1 : 1) * NUDGE * Math.exp(-WOBBLE_FADE * nudged) * Math.sin(WOBBLE_RATE * nudged);
    }
    const gone = away.get();
    return [
      { translateX: x.get() * size + SIDE * (1 - fallen) + LEAVE_SIDE * gone },
      // It falls faster and faster, and leaves quickly at first.
      { translateY: y.get() * size - DROP * (1 - fallen * fallen) - LEAVE_RISE * gone * (2 - gone) },
      { rotate: ((tilt + LEAVE_TILT * gone) * Math.PI) / 180 },
      { scaleX: size * (1 + squash * 0.6) * (1 - 0.3 * gone) },
      { scaleY: size * (1 - squash) * (1 - 0.3 * gone) },
    ];
  });
  const opacity = useDerivedValue(() => (age.get() < 0 ? 0 : Math.min(1, age.get() / 0.08) * (1 - away.get() * away.get())));

  return (
    <Group transform={transform} opacity={opacity}>
      <Cup shape={shape} />
    </Group>
  );
}

type RoundProps = { icon: 'remove' | 'add'; label: string; disabled: boolean; held: boolean; onPress: () => void };

/** A round button of the stepper. */
function Round({ icon, label, disabled, held, onPress }: RoundProps) {
  return (
    <Pressable onPress={onPress} disabled={disabled} hitSlop={8} accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled }}>
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.round,
            {
              backgroundColor: pressed || held ? ROUND_PRESSED : ROUND,
              opacity: disabled ? 0.4 : 1,
              transform: [{ scale: pressed || held ? 0.9 : 1 }],
              transitionProperty: ['backgroundColor', 'opacity', 'transform'],
              transitionDuration: 120,
            },
          ]}
        >
          <Ionicons name={icon} size={18} color={INK} />
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A drink-log form: a paper cup, the caffeine it holds, four sizes and a quantity stepper. A size
 * moves the white pill and stretches the cup on a soft spring, the lid hopping off it and landing
 * again; one cup more drops a cup in from the upper right, the others making room for it, and one
 * less takes it away. The figure counts to the new total. Nothing is stored: `onConfirm` receives
 * the order.
 */
export function StackingCupStepper({
  title = 'Caffeine Window',
  subtitle = 'Suggested beverages',
  suggestedTime = '10:30 AM',
  hours = HOURS,
  drink = 'Caffè Latte',
  time = '9:41 AM',
  sizes = SIZES,
  size = 0,
  quantity: startQuantity = 1,
  max = 3,
  figureLabel = 'Caffeine content',
  unit = 'mg',
  confirmLabel = 'Confirm',
  autoPlay = false,
  onChange,
  onConfirm,
  onBack,
  onInfo,
}: Props) {
  const insets = useSafeAreaInsets();
  const most = Math.min(MAX_CUPS, Math.max(1, max));
  const [sizeIndex, setSizeIndex] = useState(size);
  const [quantity, setQuantity] = useState(Math.min(most, Math.max(1, startQuantity)));
  // The figure while it counts.
  const [figure, setFigure] = useState(sizes[size].caffeine * Math.min(most, Math.max(1, startQuantity)));
  // Where each size's pill is in its row, once laid out.
  const [pills, setPills] = useState<{ x: number; width: number }[]>([]);
  const [area, setArea] = useState({ width: 0, height: 0 });
  // The control the autoPlay loop holds down: "size-1", "more", "less".
  const [autoPressed, setAutoPressed] = useState<string | null>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const body = useSharedValue(sizes[size].body);
  const scale = useSharedValue(sizes[size].scale);
  // 0 to 1 through the lid's hop.
  const hop = useSharedValue(1);
  const landed = useSharedValue(SETTLED);
  // The pile shrinks when it would not fit its area.
  const fit = useSharedValue(1);
  const thumbX = useSharedValue(0);
  const thumbWidth = useSharedValue(0);
  const counted = useSharedValue(figure);
  // The stepper's count comes in from below (1) or from above (-1).
  const roll = useSharedValue(0);

  const lift = useDerivedValue(() => HOP_HEIGHT * hopAt(hop.get()));
  const shape = useCupShape(body, lift);

  const chosen = sizes[sizeIndex];
  const total = chosen.caffeine * quantity;

  const changeSize = (index: number, auto: boolean) => {
    if (index === sizeIndex) return;
    const next = sizes[index];
    if (!auto) tick();
    setSizeIndex(index);
    onChange?.({ size: next, quantity, caffeine: next.caffeine * quantity });
    body.set(withSpring(next.body, STRETCH));
    scale.set(withSpring(next.scale, STRETCH));
    // Only a cup that grows throws its lid.
    if (cupHeight(next.body) * next.scale > cupHeight(chosen.body) * chosen.scale) {
      hop.set(0);
      hop.set(withDelay(HOP_AFTER * 1000, withTiming(1, { duration: HOP_FOR * 1000, easing: Easing.linear })));
    }
  };

  const step = (by: number, auto: boolean) => {
    const next = Math.min(most, Math.max(1, quantity + by));
    if (next === quantity) return;
    if (!auto) tap();
    setQuantity(next);
    onChange?.({ size: chosen, quantity: next, caffeine: chosen.caffeine * next });
    roll.set(by);
    roll.set(withTiming(0, { duration: 220, easing: Easing.out(Easing.cubic) }));
    if (by > 0) {
      landed.set(-FALL);
      landed.set(withTiming(SETTLED, { duration: (SETTLED + FALL) * 1000, easing: Easing.linear }));
    }
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(null);
    setTouches((n) => n + 1);
  };

  useEffect(() => {
    counted.set(withTiming(total, { duration: COUNT * 1000, easing: Easing.out(Easing.cubic) }));
  }, [total, counted]);

  useAnimatedReaction(
    () => Math.round(counted.get()),
    (now, old) => {
      if (now !== old) scheduleOnRN(setFigure, now);
    },
  );

  // The white pill goes to the chosen size.
  const pill = pills[sizeIndex];
  useEffect(() => {
    if (!pill) return;
    // It is put in place the first time, and slides after that.
    const placed = thumbWidth.get() > 0;
    thumbX.set(placed ? withSpring(pill.x, THUMB) : pill.x);
    thumbWidth.set(placed ? withSpring(pill.width, THUMB) : pill.width);
  }, [pill, thumbX, thumbWidth]);

  // The pile is scaled down when it is wider or taller than its area.
  useEffect(() => {
    if (area.width === 0) return;
    const slots = SLOTS[quantity - 1];
    const reach = Math.max(...slots.map(([x]) => Math.abs(x)));
    const rise = Math.max(...slots.map(([, y]) => -y));
    const wide = (reach * 2 + CUP_WIDTH) * chosen.scale + MARGIN * 2;
    const tall = (cupHeight(chosen.body) + rise + HOP_HEIGHT) * chosen.scale;
    fit.set(withSpring(Math.min(1, area.width / wide, (area.height - GROUND - HEADROOM) / tall), SLIDE));
  }, [area, quantity, chosen, fit]);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ changeSize, step, sizeIndex, quantity });
  useEffect(() => {
    latest.current = { changeSize, step, sizeIndex, quantity };
  });

  const startSize = size;
  const startCups = Math.min(most, Math.max(1, startQuantity));

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      STEPS.forEach(([at, what, index]) => {
        const key = what === 'size' ? `size-${index}` : what;
        after(origin + at * 1000 - Date.now(), () => setAutoPressed(key));
        after(origin + (at + PRESS_FOR) * 1000 - Date.now(), () => {
          setAutoPressed(null);
          if (what === 'size') latest.current.changeSize(Math.min(index, sizes.length - 1), true);
          else latest.current.step(what === 'more' ? 1 : -1, true);
        });
      });
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const resume = () => {
      // The loop starts from the form as it was given: one step back at a time, then the size.
      if (latest.current.quantity !== startCups) {
        latest.current.step(latest.current.quantity > startCups ? -1 : 1, true);
        return after(300, resume);
      }
      if (latest.current.sizeIndex !== startSize) {
        latest.current.changeSize(startSize, true);
        return after(RESET_FOR * 1000, resume);
      }
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, resume);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, sizes.length, startSize, startCups]);

  const pileTransform = useDerivedValue(() => [{ translateX: area.width / 2 }, { translateY: area.height - GROUND }, { scale: fit.get() }]);
  const thumbStyle = useAnimatedStyle(() => ({ width: thumbWidth.get(), transform: [{ translateX: thumbX.get() }] }));
  const countStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.abs(roll.get()), transform: [{ translateY: roll.get() * ROLL }] }));

  const onPill = (index: number) => (event: LayoutChangeEvent) => {
    const { x, width } = event.nativeEvent.layout;
    setPills((list) => {
      const next = [...list];
      next[index] = { x, width };
      return next;
    });
  };

  const order = { size: chosen, quantity, caffeine: total };
  const cups = Array.from({ length: most }, (_, index) => index);

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <StatusBar style="light" />

      <View style={styles.nav}>
        <Pressable
          style={styles.navButton}
          accessibilityRole="button"
          accessibilityLabel="Back"
          onPress={() => {
            touched();
            onBack?.();
          }}
        >
          <Ionicons name="arrow-back" size={22} color={TITLE_INK} />
        </Pressable>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Pressable
          style={styles.navButton}
          accessibilityRole="button"
          accessibilityLabel="About"
          onPress={() => {
            touched();
            onInfo?.();
          }}
        >
          <Ionicons name="information-circle" size={26} color={TITLE_INK} />
        </Pressable>
      </View>

      <View style={styles.subtitleRow}>
        <Text style={styles.subtitle} numberOfLines={1}>
          {subtitle}
        </Text>
        <Ionicons name="information-circle" size={16} color="#4A4A4A" />
      </View>

      {/* The timeline is a still picture: when the drink is suggested, on a ruler of quarter hours. */}
      <View style={styles.timeline} accessible accessibilityLabel={`${subtitle}: ${suggestedTime}`}>
        <View style={[styles.suggested, { left: TICK_LEFT + TICK_AT * TICK_STEP - 60 }]}>
          <View style={styles.timeChip}>
            <Text style={styles.timeChipLabel}>{suggestedTime}</Text>
          </View>
          <View style={styles.pointer} />
        </View>
        {Array.from({ length: TICKS }, (_, index) => (
          <View
            key={index}
            style={[
              styles.tick,
              {
                left: TICK_LEFT + index * TICK_STEP,
                height: index % 4 === 0 ? 20 : 15,
                backgroundColor: index < TICK_AT ? TICK_EARLY : index === TICK_AT ? TICK_NOW : TICK_LATE,
              },
            ]}
          />
        ))}
        {hours.map((hour, index) => (
          <Text key={hour} style={[styles.hour, { left: TICK_LEFT + index * 8 * TICK_STEP - 30 }]} numberOfLines={1}>
            {hour}
          </Text>
        ))}
      </View>

      <View style={styles.sheet}>
        <View style={styles.header}>
          <Text style={styles.drink} numberOfLines={1}>
            {drink}
          </Text>
          <View style={styles.chip}>
            <Text style={styles.chipLabel}>{time}</Text>
          </View>
        </View>

        <View
          style={styles.cups}
          onLayout={(event) => setArea({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height })}
          accessible
          accessibilityRole="image"
          accessibilityLabel={`${quantity} × ${chosen.label} ${drink}`}
        >
          {area.width > 0 && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Group transform={pileTransform}>
                {cups.map((index) => (
                  <PiledCup key={index} index={index} quantity={quantity} shape={shape} scale={scale} landed={landed} />
                ))}
              </Group>
            </Canvas>
          )}
        </View>

        <Text style={styles.figureLabel}>{figureLabel}</Text>
        <View style={styles.figureRow} accessible accessibilityLabel={`${total} ${unit}`}>
          <Text style={styles.figure}>{figure}</Text>
          <Text style={styles.unit}>{unit}</Text>
        </View>

        <View style={styles.sizes}>
          {sizes.map((item, index) => (
            <Pressable
              key={item.label}
              style={styles.size}
              onLayout={onPill(index)}
              accessibilityRole="radio"
              accessibilityLabel={`${item.label}, ${item.volume}`}
              accessibilityState={{ selected: index === sizeIndex }}
              onPress={() => {
                touched();
                changeSize(index, false);
              }}
            >
              {({ pressed }) => (
                <>
                  <Animated.View
                    style={[
                      styles.pill,
                      {
                        backgroundColor: pressed || autoPressed === `size-${index}` ? PILL_PRESSED : PILL,
                        transitionProperty: 'backgroundColor',
                        transitionDuration: 120,
                      },
                    ]}
                  >
                    <Text style={[styles.pillLabel, styles.hidden]}>{item.label}</Text>
                  </Animated.View>
                  <Animated.Text
                    style={[styles.volume, { color: index === sizeIndex ? INK : VOLUME_OFF, transitionProperty: 'color', transitionDuration: 200 }]}
                  >
                    {item.volume}
                  </Animated.Text>
                </>
              )}
            </Pressable>
          ))}
          <Animated.View pointerEvents="none" style={[styles.thumb, thumbStyle]} />
          {/* The labels again, over the white pill. */}
          <View pointerEvents="none" style={styles.labels}>
            {sizes.map((item, index) => (
              <View key={item.label} style={styles.pill}>
                <Animated.Text
                  style={[styles.pillLabel, { color: index === sizeIndex ? '#000000' : PILL_INK, transitionProperty: 'color', transitionDuration: 200 }]}
                >
                  {item.label}
                </Animated.Text>
              </View>
            ))}
          </View>
        </View>

        <View style={styles.stepper} accessibilityRole="adjustable" accessibilityValue={{ min: 1, max: most, now: quantity }}>
          <Round
            icon="remove"
            label="One less"
            disabled={quantity <= 1}
            held={autoPressed === 'less'}
            onPress={() => {
              touched();
              step(-1, false);
            }}
          />
          <Animated.Text style={[styles.count, countStyle]}>{quantity}</Animated.Text>
          <Round
            icon="add"
            label="One more"
            disabled={quantity >= most}
            held={autoPressed === 'more'}
            onPress={() => {
              touched();
              step(1, false);
            }}
          />
        </View>

        <Pressable
          accessibilityRole="button"
          style={{ marginBottom: Math.max(insets.bottom, 16) + 16 }}
          onPress={() => {
            touched();
            if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
            onConfirm?.(order);
          }}
        >
          {({ pressed }) => (
            <Animated.View
              style={[styles.confirm, { backgroundColor: pressed ? CONFIRM_PRESSED : INK, transitionProperty: 'backgroundColor', transitionDuration: 100 }]}
            >
              <Text style={styles.confirmLabel}>{confirmLabel}</Text>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  nav: {
    height: NAV,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 4,
  },
  navButton: {
    width: 48,
    height: NAV,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    flex: 1,
    textAlign: 'center',
    color: TITLE_INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  subtitleRow: {
    marginTop: 26,
    height: 22,
    paddingHorizontal: 13,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  subtitle: {
    color: '#B4B4B4',
    fontSize: 15,
    lineHeight: 20,
  },
  timeline: {
    marginTop: 18,
    height: 96,
    overflow: 'hidden',
  },
  suggested: {
    position: 'absolute',
    top: 0,
    width: 120,
    alignItems: 'center',
  },
  timeChip: {
    height: 26,
    paddingHorizontal: 10,
    borderRadius: 13,
    backgroundColor: '#1A1A1A',
    justifyContent: 'center',
  },
  timeChipLabel: {
    color: '#D0D0D0',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 1,
  },
  // A small triangle pointing down at the tick.
  pointer: {
    marginTop: 5,
    width: 0,
    height: 0,
    borderLeftWidth: 4,
    borderRightWidth: 4,
    borderTopWidth: 6,
    borderLeftColor: 'transparent',
    borderRightColor: 'transparent',
    borderTopColor: '#C9C9C9',
  },
  tick: {
    position: 'absolute',
    bottom: 30,
    width: 1.5,
    marginLeft: -0.75,
    borderRadius: 1,
  },
  hour: {
    position: 'absolute',
    bottom: 6,
    width: 60,
    textAlign: 'center',
    color: '#8A8A8A',
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '500',
  },
  sheet: {
    flex: 1,
    marginTop: 20,
    backgroundColor: SHEET,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    borderCurve: 'continuous',
  },
  header: {
    height: HEADER,
    paddingHorizontal: MARGIN,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: HAIRLINE,
  },
  drink: {
    color: INK,
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '700',
  },
  chip: {
    height: 28,
    paddingHorizontal: 14,
    borderRadius: 14,
    backgroundColor: CHIP,
    justifyContent: 'center',
  },
  chipLabel: {
    color: INK,
    fontSize: 13,
    lineHeight: 17,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  cups: {
    flex: 1,
    minHeight: 150,
    overflow: 'hidden',
  },
  figureLabel: {
    textAlign: 'center',
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
  },
  figureRow: {
    marginTop: 1,
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'center',
  },
  figure: {
    color: INK,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  unit: {
    marginLeft: 6,
    color: '#9A9A9A',
    fontSize: 13,
    lineHeight: 18,
  },
  sizes: {
    marginTop: 26,
    alignSelf: 'center',
    flexDirection: 'row',
    gap: PILL_GAP,
  },
  size: {
    alignItems: 'center',
  },
  pill: {
    height: PILL_HEIGHT,
    paddingHorizontal: 15,
    borderRadius: PILL_HEIGHT / 2,
    justifyContent: 'center',
  },
  pillLabel: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  hidden: {
    opacity: 0,
  },
  volume: {
    marginTop: 6,
    fontSize: 10.5,
    lineHeight: 13,
    fontWeight: '700',
  },
  thumb: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    backgroundColor: INK,
  },
  labels: {
    position: 'absolute',
    left: 0,
    top: 0,
    flexDirection: 'row',
    gap: PILL_GAP,
  },
  stepper: {
    marginTop: 26,
    alignSelf: 'center',
    width: STEPPER_WIDTH,
    height: STEPPER_HEIGHT,
    borderRadius: STEPPER_HEIGHT / 2,
    paddingHorizontal: 3,
    backgroundColor: '#1F1F1F',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  round: {
    width: ROUND_SIZE,
    height: ROUND_SIZE,
    borderRadius: ROUND_SIZE / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  count: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  confirm: {
    marginTop: 40,
    marginHorizontal: MARGIN,
    height: CONFIRM,
    borderRadius: CONFIRM / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  confirmLabel: {
    color: '#000000',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
});
