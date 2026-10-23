import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled position along the five steps, 0…1: every quarter is one step reached. */
  progress?: number;
  /** Heads for the second step (the reference frame), holds, then runs through the remaining steps and starts over. */
  autoPlay?: boolean;
  /** Headline while heading for steps 2…5, and once the last one is reached. */
  titles?: [string, string, string, string, string];
  /** Minutes the whole trip takes: the ETA counts down from it. */
  tripMinutes?: number;
  driver?: string;
  vehicle?: string;
  plate?: string;
  onCallPress?: () => void;
  onMessagePress?: () => void;
};

const WIDTH = 356;
const HEIGHT = 148;
const RADIUS = 22;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 44;

const STEP_Y = 67.6;
const STEP_X = 28.2;
const LEG = 74.8;
const SPAN = LEG * 4;
const DOT_R = 12;
const LINE_H = 6;

const BLUE = '#2490ff';
const DIM_ICON = '#3f74c6';

// The reference shows the driver 60% of the way to the second step; the autoPlay loop built around it.
const REST = 0.15;
// Seconds, from, to.
const SEGMENTS = [
  [0.5, 0, 0],
  [1.5, 0, REST],
  [2.8, REST, REST],
  [0.8, REST, 0.25],
  [0.6, 0.25, 0.25],
  [1.1, 0.25, 0.5],
  [0.6, 0.5, 0.5],
  [1.1, 0.5, 0.75],
  [0.6, 0.75, 0.75],
  [1.1, 0.75, 1],
  [2.2, 1, 1],
  [0.8, 1, 0],
];
const LOOP = SEGMENTS.reduce((sum, s) => sum + s[0], 0);
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = 0.5 + 1.5 + 1.4;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// Step glyphs, drawn around the dot's centre: parcel, garment, washer, van, home.
const ICONS = [
  'M0 -5.4L4.7 -2.7V2.7L0 5.4L-4.7 2.7V-2.7ZM-4.7 -2.7L0 0L4.7 -2.7M0 0V5.4',
  'M-2 -4.9L-5.4 -2.9L-4.1 -0.5L-2.9 -1.2V4.9H2.9V-1.2L4.1 -0.5L5.4 -2.9L2 -4.9C1.4 -3.5 -1.4 -3.5 -2 -4.9Z',
  'M-3.6 -5.2H3.6Q4.8 -5.2 4.8 -4V4Q4.8 5.2 3.6 5.2H-3.6Q-4.8 5.2 -4.8 4V-4Q-4.8 -5.2 -3.6 -5.2ZM-2.9 -3.2H-1.5M0 -1.5C1.5 -1.5 2.6 -0.4 2.6 1.1C2.6 2.6 1.5 3.7 0 3.7C-1.5 3.7 -2.6 2.6 -2.6 1.1C-2.6 -0.4 -1.5 -1.5 0 -1.5Z',
  'M-5.6 -3.6H0.8V2.6H-5.6ZM0.8 -1.4H3.5L5.6 0.8V2.6H0.8M-3.6 2.6C-3.6 3.5 -3 4.1 -2.2 4.1C-1.4 4.1 -0.8 3.5 -0.8 2.6M2 2.6C2 3.5 2.6 4.1 3.4 4.1C4.2 4.1 4.8 3.5 4.8 2.6',
  'M-5.4 -0.4L0 -5.1L5.4 -0.4M-3.9 -1.5V4.9H3.9V-1.5M-1.2 4.9V1.7H1.2V4.9',
];
const PHONE =
  'M-3 -5.2C-2.4 -5.4 -1.8 -5.1 -1.6 -4.5L-0.8 -2.4C-0.6 -1.9 -0.8 -1.4 -1.2 -1.1L-2.1 -0.4C-1.3 1.2 -0.1 2.4 1.5 3.2L2.2 2.3C2.5 1.9 3 1.7 3.5 1.9L5.6 2.7C6.2 2.9 6.5 3.5 6.3 4.1C5.9 5.4 4.8 6 3.6 5.8C-1.1 5 -4.7 1.4 -5.5 -3.3C-5.7 -4.2 -4.3 -4.8 -3 -5.2Z';
const BUBBLE =
  'M-4.3 -4.8H4.3Q5.7 -4.8 5.7 -3.4V1.6Q5.7 3 4.3 3H-1.5L-4.3 5.4V3Q-5.7 3 -5.7 1.6V-3.4Q-5.7 -4.8 -4.3 -4.8Z';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function easeOutCubic(t: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(t), 3);
}

/** Position along the steps at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  let at = t;
  for (let i = 0; i < SEGMENTS.length; i++) {
    const [length, from, to] = SEGMENTS[i];
    if (at < length) return from + (to - from) * easeInOutCubic(at / length);
    at -= length;
  }
  return 0;
}

/** Which headline fits the position: the leg being travelled, or 4 once the last step is reached. */
function legIndex(p: number) {
  'worklet';
  return Math.min(4, Math.floor(clamp01(p) * 4 + 0.004));
}

/** 0…1: how lit step `index` is. */
function lit(p: number, index: number) {
  'worklet';
  return index === 0 ? 1 : clamp01((p - (index / 4 - 0.012)) / 0.012);
}

function etaLabel(minutes: number) {
  return minutes <= 0 ? 'Arrived' : `ETA : ${minutes} minute${minutes === 1 ? '' : 's'}`;
}

// Left to right the headline runs from soft blue through aqua to white.
const TITLE_STOPS = [
  [0, 134, 180, 242],
  [0.4, 126, 226, 238],
  [0.66, 226, 248, 252],
  [1, 255, 255, 255],
];

function titleColor(at: number) {
  for (let i = 1; i < TITLE_STOPS.length; i++) {
    const a = TITLE_STOPS[i - 1];
    const b = TITLE_STOPS[i];
    if (at <= b[0]) {
      const k = (at - a[0]) / (b[0] - a[0]);
      return `rgb(${Math.round(a[1] + (b[1] - a[1]) * k)},${Math.round(a[2] + (b[2] - a[2]) * k)},${Math.round(a[3] + (b[3] - a[3]) * k)})`;
    }
  }
  return '#ffffff';
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** The step that was reached last and when, on the `time` axis. */
  popped: SharedValue<number>;
  poppedAt: SharedValue<number>;
  /** 0 with reduced motion: no pulses, breathing or pops. */
  ambient: number;
};

function Step({ index, value, time, popped, poppedAt, ambient }: Motion & { index: number }) {
  const cx = BLEED + STEP_X + LEG * index;
  const cy = BLEED + STEP_Y;
  const on = useDerivedValue(() => lit(value.get(), index));
  const since = useDerivedValue(() => (popped.get() === index ? time.get() - poppedAt.get() : 10));
  // Reaching a step pops its dot with a little overshoot and sends a ring out.
  const transform = useDerivedValue(() => {
    const u = clamp01(since.get() / 0.55);
    return [{ scale: 1 + ambient * 0.5 * Math.sin(Math.PI * u) * (1 - u) }];
  });
  const ringR = useDerivedValue(() => DOT_R + 15 * easeOutCubic(since.get() / 0.85));
  const ringOpacity = useDerivedValue(() => {
    const u = since.get() / 0.85;
    return u >= 1 ? 0 : ambient * 0.8 * (1 - u);
  });
  // The step the driver left last keeps a halo that breathes.
  const halo = useDerivedValue(() => {
    const current = legIndex(value.get()) === index || (index === 4 && value.get() > 0.99) ? 1 : 0.35;
    return on.get() * current * (0.5 + ambient * 0.14 * Math.sin(time.get() * 2.2));
  });
  const fill = useDerivedValue(() => interpolateColor(on.get(), [0, 1], ['#0e3f94', BLUE]));
  const ring = useDerivedValue(() => interpolateColor(on.get(), [0, 1], ['rgba(96,146,232,0.5)', 'rgba(140,200,255,0)']));
  const icon = useDerivedValue(() => interpolateColor(on.get(), [0, 1], [DIM_ICON, '#ffffff']));

  return (
    <Group origin={vec(cx, cy)} transform={transform}>
      <Circle cx={cx} cy={cy} r={ringR} style="stroke" strokeWidth={1.5} color="#7fd0ff" opacity={ringOpacity} />
      <Circle cx={cx} cy={cy} r={DOT_R + 2} color={BLUE} opacity={halo}>
        <BlurMask blur={6} style="normal" />
      </Circle>
      <Circle cx={cx} cy={cy} r={DOT_R} color={fill} />
      <Circle cx={cx} cy={cy} r={DOT_R - 0.5} style="stroke" strokeWidth={1} color={ring} />
      <Group transform={[{ translateX: cx }, { translateY: cy }]}>
        <Path path={ICONS[index]} style="stroke" strokeWidth={1.1} strokeCap="round" strokeJoin="round" color={icon} />
      </Group>
    </Group>
  );
}

function Surface(motion: Motion) {
  const { value, time, ambient } = motion;
  const x = BLEED;
  const y = BLEED;
  const lx = x + STEP_X;
  const cy = y + STEP_Y;

  const head = useDerivedValue(() => lx + SPAN * clamp01(value.get()));
  const litWidth = useDerivedValue(() => head.get() - lx + LINE_H / 2);
  const gradientStart = useDerivedValue(() => vec(head.get() - 46, cy));
  const gradientEnd = useDerivedValue(() => vec(head.get(), cy));
  // The bright head hides while it sits under a step's dot.
  const headShown = useDerivedValue(() => {
    const leg = clamp01(value.get()) * 4;
    const off = Math.abs(leg - Math.round(leg)) * LEG;
    return clamp01((off - 9) / 7);
  });
  const glowX = useDerivedValue(() => head.get() - 30);
  const glowOpacity = useDerivedValue(() => headShown.get() * (0.85 + ambient * 0.15 * Math.sin(time.get() * 3)));
  // Pulses of light run from the last step to the head.
  const pulseAt = useDerivedValue(() => (time.get() * 0.8) % 1);
  const pulseX = useDerivedValue(() => {
    const from = lx + LEG * Math.floor(clamp01(value.get()) * 4 - 0.0001) + DOT_R;
    return from + (head.get() - 8 - from) * pulseAt.get() - 6;
  });
  const pulseOpacity = useDerivedValue(() => ambient * headShown.get() * 0.9 * Math.sin(Math.PI * pulseAt.get()));

  return (
    <Canvas style={styles.canvas}>
      {/* Soft shadow under the card. */}
      <RoundedRect x={x + 14} y={y + 26} width={WIDTH - 28} height={HEIGHT - 16} r={RADIUS} color="rgba(20,50,110,0.42)">
        <BlurMask blur={18} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS}>
        <LinearGradient
          start={vec(x, y)}
          end={vec(x, y + HEIGHT)}
          colors={['#01030a', '#03112e', '#09306f', '#0c4296']}
          positions={[0, 0.3, 0.75, 1]}
        />
      </RoundedRect>
      {/* Glass edge: a light rim that grows towards the bottom. */}
      <RoundedRect x={x + 0.5} y={y + 0.5} width={WIDTH - 1} height={HEIGHT - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient
          start={vec(x, y + HEIGHT * 0.35)}
          end={vec(x, y + HEIGHT)}
          colors={['rgba(120,170,255,0)', 'rgba(120,170,255,0.5)']}
        />
      </RoundedRect>

      {/* Route: dim line, lit part, glowing head. */}
      <RoundedRect x={lx} y={cy - LINE_H / 2} width={SPAN} height={LINE_H} r={LINE_H / 2} color="#0d3d92" />
      <RoundedRect x={glowX} y={cy - LINE_H / 2} width={32} height={LINE_H} r={LINE_H / 2} color="#6fdcff" opacity={glowOpacity}>
        <BlurMask blur={5} style="normal" />
      </RoundedRect>
      <RoundedRect x={lx} y={cy - LINE_H / 2} width={litWidth} height={LINE_H} r={LINE_H / 2}>
        <LinearGradient start={gradientStart} end={gradientEnd} colors={[BLUE, '#7fe4ff', '#f2feff']} positions={[0, 0.65, 1]} />
      </RoundedRect>
      <RoundedRect x={pulseX} y={cy - 1.2} width={12} height={2.4} r={1.2} color="#ffffff" opacity={pulseOpacity}>
        <BlurMask blur={1.2} style="normal" />
      </RoundedRect>
      {[0, 1, 2, 3, 4].map((index) => (
        <Step key={index} index={index} {...motion} />
      ))}

      {/* Driver's avatar tile. */}
      <RoundedRect x={x + 16.1} y={y + 100.9} width={31.4} height={31.4} r={9}>
        <LinearGradient start={vec(x + 16, y + 101)} end={vec(x + 47, y + 132)} colors={['#7b8fa8', '#46586f']} />
      </RoundedRect>
      <RoundedRect x={x + 16.6} y={y + 101.4} width={30.4} height={30.4} r={8.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.22)" />
    </Canvas>
  );
}

function PillButton({ left, width, icon, label, onPress }: { left: number; width: number; icon: string; label: string; onPress?: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      onPressIn={() => {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }}
      hitSlop={6}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={[styles.pillSlot, { left, width }]}
    >
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.pill,
            {
              transform: [{ scale: pressed ? 0.94 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          <Canvas style={styles.pillIcon}>
            <Group transform={[{ translateX: 8 }, { translateY: 8 }]}>
              <Path path={icon} color="#ffffff" />
            </Group>
          </Canvas>
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * Deep blue glass order card: a headline and ETA, five ringed steps joined by a line, and the
 * driver's row with call and message buttons. A glowing head travels the current leg with pulses
 * of light running into it; reaching a step pops its dot, sends a ring out and lights its glyph,
 * the headline blurs into its next state and the ETA counts down.
 */
export function DeliveryStepsProgress({
  progress,
  autoPlay = false,
  titles = ['Driver Heading to Laundry', 'Laundry in Progress', 'Packed and Ready', 'Driver Heading to You', 'Order Delivered'],
  tripMinutes = 3,
  driver = 'Sam Carter',
  vehicle = 'Compact Wagon',
  plate = 'CAK 3816',
  onCallPress,
  onMessagePress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(target);
  const popped = useSharedValue(-1);
  const poppedAt = useSharedValue(-10);
  const leg = useSharedValue(legIndex(target));
  // 0 → 1 after the headline changes: it blurs in.
  const settle = useSharedValue(1);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 1400, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    const now = value.get();
    const was = before.get();
    before.set(now);
    // Reaching a step sets off its pop.
    for (let i = 1; i <= 4; i++) {
      const at = i / 4 - 0.002;
      if (was < at && now >= at) {
        popped.set(i);
        poppedAt.set(time.get());
      }
    }
    // On the way back to the start the headline already shows the first leg.
    leg.set(now < was ? 0 : legIndex(now));
    if (!playing.get()) return;
    let t = clock.get() + dt;
    if (looping.get()) {
      if (t >= LOOP) t -= LOOP;
    } else if (t >= REST_T) {
      // A tapped, one-off run stops at the resting frame.
      t = REST_T;
      playing.set(false);
    }
    clock.set(t);
  }, !reducedMotion);

  const [titleShown, setTitleShown] = useState(legIndex(target));
  useAnimatedReaction(
    () => (reducedMotion ? legIndex(value.get()) : leg.get()),
    (now, old) => {
      if (now === old || old == null) return;
      scheduleOnRN(setTitleShown, now);
      if (reducedMotion) return;
      settle.set(0);
      settle.set(withTiming(1, { duration: 560, easing: EASE_OUT }));
    },
  );
  const [minutes, setMinutes] = useState(Math.ceil(tripMinutes * (1 - target) - 0.001));
  useAnimatedReaction(
    () => Math.ceil(tripMinutes * (1 - clamp01(value.get())) - 0.001),
    (now, old) => {
      if (now !== old) scheduleOnRN(setMinutes, now);
    },
  );
  const [step, setStep] = useState(Math.floor(target * 4 + 0.004) + 1);
  useAnimatedReaction(
    () => Math.floor(clamp01(value.get()) * 4 + 0.004) + 1,
    (now, old) => {
      if (now !== old) scheduleOnRN(setStep, now);
    },
  );

  const titleStyle = useAnimatedStyle(() => {
    const s = settle.get();
    return {
      opacity: 0.2 + 0.8 * s,
      transform: [{ translateY: 5 * (1 - s) }],
      // React Native's types give a text style only the string form of `filter`; views take this list form.
      filter: [{ blur: 6 * (1 - s) }] as unknown as string,
    };
  });

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const title = titles[titleShown];
  const eta = etaLabel(minutes);
  const initials = driver
    .split(' ')
    .map((word) => word[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <Pressable onPress={restart} style={styles.card} accessible={false}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} popped={popped} poppedAt={poppedAt} ambient={reducedMotion ? 0 : 1} />
      </View>

      <Animated.Text
        style={[styles.title, titleStyle]}
        numberOfLines={1}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={title}
        accessibilityValue={{ text: `Step ${step} of 5, ${eta}` }}
      >
        {[...title].map((char, i) => (
          <Text key={i} style={{ color: titleColor(title.length > 1 ? i / (title.length - 1) : 0) }}>
            {char}
          </Text>
        ))}
      </Animated.Text>
      <Text style={styles.eta} numberOfLines={1}>
        {eta}
      </Text>

      <Text style={styles.initials} numberOfLines={1}>
        {initials}
      </Text>
      <Text style={styles.driver} numberOfLines={1}>
        {driver}
      </Text>
      <View style={styles.vehicleRow}>
        <Text style={styles.vehicle} numberOfLines={1}>
          {vehicle}
        </Text>
        <View style={styles.divider} />
        <Text style={styles.vehicle} numberOfLines={1}>
          {plate}
        </Text>
      </View>

      <PillButton left={238.2} width={48.1} icon={PHONE} label={`Call ${driver}`} onPress={onCallPress} />
      <PillButton left={292.4} width={47} icon={BUBBLE} label={`Message ${driver}`} onPress={onMessagePress} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  title: {
    position: 'absolute',
    left: 16.9,
    top: 14.5,
    width: 236,
    color: '#ffffff',
    fontSize: 16.6,
    lineHeight: 22,
    fontWeight: '400',
    letterSpacing: -0.5,
  },
  eta: {
    position: 'absolute',
    left: 218.6,
    top: 17.5,
    width: 120,
    textAlign: 'right',
    color: '#ffffff',
    fontSize: 10.8,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: -0.2,
  },
  initials: {
    position: 'absolute',
    left: 16.1,
    top: 108.6,
    width: 31.4,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
  driver: {
    position: 'absolute',
    left: 59,
    top: 102.3,
    width: 170,
    color: '#ffffff',
    fontSize: 13.2,
    lineHeight: 18,
    fontWeight: '500',
    letterSpacing: -0.3,
  },
  vehicleRow: {
    position: 'absolute',
    left: 59,
    top: 116.8,
    height: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },
  vehicle: {
    color: '#a7bcdc',
    fontSize: 10.8,
    lineHeight: 16,
    letterSpacing: -0.2,
  },
  divider: {
    width: 1,
    height: 11,
    marginHorizontal: 7,
    backgroundColor: 'rgba(167,188,220,0.35)',
  },
  pillSlot: {
    position: 'absolute',
    top: 108.7,
    height: 23.9,
  },
  pill: {
    flex: 1,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.26)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillIcon: {
    width: 16,
    height: 16,
  },
});
