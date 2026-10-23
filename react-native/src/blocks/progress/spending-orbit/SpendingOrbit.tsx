import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Paint,
  Path,
  Rect,
  RoundedRect,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
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
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled share of the daily budget spent, 0…1. Leave undefined to show the reference's 78% (or the autoPlay loop). */
  progress?: number;
  /** Fills by itself from nothing to 78%, holds, empties and starts over. */
  autoPlay?: boolean;
  title?: string;
};

const SIZE = 290;
// The canvas overflows the widget by this much on every side to fit the orbit's bloom.
const BLEED = 24;

const TILE_W = 250;
const TILE_H = 239;
const TILE_X = (SIZE - TILE_W) / 2;
const TILE_Y = (SIZE - TILE_H) / 2;
const TILE_R = 26;
const INSET = 29.9;

const C = SIZE / 2;
const ORBIT_R = 141.8;

const TICKS = 27;
const TICK_X = TILE_X + 29.4;
const TICK_Y = TILE_Y + 101.2;
const TICK_W = 2.9;
const TICK_H = 14;
const TICK_PITCH = 7.25;

const ROW_Y = TILE_Y + 143.7;
const ROW_PITCH = 20.85;

// The day's categories as in the reference: their amounts and how many ticks of the bar they take.
const CATEGORIES = [
  { label: 'Groceries', amount: 78.46, ticks: 11, color: '#fb6a2c' },
  { label: 'Entertainment', amount: 56.2, ticks: 6, color: '#8b5cf6' },
  { label: 'Transportation', amount: 33.58, ticks: 4, color: '#3aa5f5' },
  { label: 'Utilities', amount: 24.21, ticks: 2, color: '#27d267' },
];
const AMOUNTS = CATEGORIES.map((c) => c.amount);
const COUNTS = CATEGORIES.map((c) => c.ticks);
const SPENT = 192.45;
const SPENT_TICKS = 23;

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.78;
const BUDGET = SPENT / REST;
const START = 0.45;
const FILL = 2.4;
const HOLD = 4.4;
const RESET = 0.8;
const LOOP = START + FILL + HOLD + RESET;
const LANDED = START + FILL;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = LANDED + 1.6;
// Each of the two orbiting arcs spans a quarter turn at the reference's 78%.
const ARC_AT_FULL = 90 / REST;
const GLINT_PERIOD = 3.4;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });
const RAD = Math.PI / 180;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Budget share at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < LANDED) return REST * easeInOutCubic((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how fast the value is moving at `t` seconds into the loop. */
function loopActivity(t: number) {
  'worklet';
  const u = (t - START) / FILL;
  return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
}

/** Money spent in category `index` once `total` has been spent in all: the categories fill one after the other. */
function categorySpend(total: number, index: number) {
  'worklet';
  let before = 0;
  for (let i = 0; i < index; i++) before += AMOUNTS[i];
  const own = Math.max(0, total - before);
  // Anything over the reference's total lands in the last category.
  return index === AMOUNTS.length - 1 ? own : Math.min(AMOUNTS[index], own);
}

/** How many ticks of the bar `total` lights, fractional: each category fills its own run of ticks. */
function litTicks(total: number) {
  'worklet';
  let lit = 0;
  for (let i = 0; i < AMOUNTS.length; i++) lit += COUNTS[i] * clamp01(categorySpend(total, i) / AMOUNTS[i]);
  return lit + (TICKS - SPENT_TICKS) * clamp01((total - SPENT) / (BUDGET - SPENT));
}

function tickColor(index: number) {
  let end = 0;
  for (const category of CATEGORIES) {
    end += category.ticks;
    if (index < end) return category.color;
  }
  return CATEGORIES[CATEGORIES.length - 1].color;
}

/** Arc of the orbit between two angles (degrees clockwise from 12 o'clock). */
function orbitArc(from: number, sweep: number) {
  'worklet';
  const s = Math.min(359, Math.max(0.01, sweep));
  const a0 = (from - 90) * RAD;
  const a1 = (from + s - 90) * RAD;
  const p = (a: number) => `${(C + ORBIT_R * Math.cos(a)).toFixed(2)} ${(C + ORBIT_R * Math.sin(a)).toFixed(2)}`;
  return `M${p(a0)}A${ORBIT_R} ${ORBIT_R} 0 ${s > 180 ? 1 : 0} 1 ${p(a1)}`;
}

const TICK_CLIP = (() => {
  const path = Skia.Path.Make();
  for (let i = 0; i < TICKS; i++) {
    path.addRRect(Skia.RRectXY(Skia.XYWHRect(TICK_X + i * TICK_PITCH, TICK_Y, TICK_W, TICK_H), TICK_W / 2, TICK_W / 2));
  }
  return path;
})();

type Motion = {
  /** Money spent so far. */
  total: SharedValue<number>;
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** Seconds since the value landed; large when nothing happened. */
  landed: SharedValue<number>;
  /** 0 with reduced motion: no glints, no shimmer. */
  ambient: number;
};

/** One tick of the bar: the ticks around the moving head of the fill stretch, and each settles in its category's colour. */
function Tick({ index, total, activity }: { index: number; total: SharedValue<number>; activity: SharedValue<number> }) {
  const x = TICK_X + index * TICK_PITCH;
  const lit = useDerivedValue(() => clamp01(litTicks(total.get()) - index));
  const transform = useDerivedValue(() => {
    const near = clamp01(1 - Math.abs(litTicks(total.get()) - index - 0.5) / 2.2);
    return [{ scaleY: 1 + 0.45 * activity.get() * near * near * (3 - 2 * near) }];
  });
  return (
    <Group transform={transform} origin={vec(x + TICK_W / 2, TICK_Y + TICK_H / 2)}>
      <RoundedRect x={x} y={TICK_Y} width={TICK_W} height={TICK_H} r={TICK_W / 2} color="rgba(255,255,255,0.2)" />
      <RoundedRect x={x} y={TICK_Y} width={TICK_W} height={TICK_H} r={TICK_W / 2} color={tickColor(index)} opacity={lit} />
    </Group>
  );
}

/** Marker in front of a category row: pops as its category starts to fill. */
function Marker({ index, total }: { index: number; total: SharedValue<number> }) {
  const y = ROW_Y + index * ROW_PITCH;
  const share = useDerivedValue(() => clamp01(categorySpend(total.get(), index) / AMOUNTS[index]));
  const transform = useDerivedValue(() => [{ scaleY: 1 + 0.5 * Math.sin(Math.PI * clamp01(share.get() * 2.5)) }]);
  const opacity = useDerivedValue(() => 0.3 + 0.7 * clamp01(share.get() * 5));
  return (
    <Group transform={transform} origin={vec(TILE_X + 30.85, y)} opacity={opacity}>
      <RoundedRect x={TILE_X + 29.4} y={y - 5.8} width={2.9} height={11.6} r={1.45} color={CATEGORIES[index].color} />
    </Group>
  );
}

function Surface({ total, value, time, activity, landed, ambient }: Motion) {
  // The orbit: two opposite arcs that grow with the value while the pair turns once per 78%.
  const spin = useDerivedValue(() => (value.get() / REST) * 360);
  const sweep = useDerivedValue(() => {
    const u = landed.get();
    const wobble = u > 0 && u < 1.2 ? 7 * Math.exp(-4.5 * u) * Math.sin(13 * u) : 0;
    return ARC_AT_FULL * clamp01(value.get()) + wobble;
  });
  const arcs = useDerivedValue(() => orbitArc(spin.get(), sweep.get()) + orbitArc(spin.get() + 180, sweep.get()));
  const arcsAlpha = useDerivedValue(() => clamp01(value.get() / 0.02));
  // Their bloom breathes, and flashes as the value lands.
  const bloom = useDerivedValue(() => {
    const u = landed.get() / 0.8;
    const flash = u > 0 && u < 1 ? 0.5 * (1 - u) : 0;
    return arcsAlpha.get() * Math.min(1, 0.5 + 0.12 * ambient * Math.sin(time.get() * 1.4) + 0.3 * activity.get() + flash);
  });
  // At rest a glint runs along each arc from its tail to its head.
  const glintPhase = useDerivedValue(() => (time.get() % GLINT_PERIOD) / 1.3);
  const glint = useDerivedValue(() => {
    const at = spin.get() + (sweep.get() + 16) * Math.min(1, glintPhase.get()) - 8;
    const from = Math.max(spin.get(), at - 9);
    const to = Math.min(spin.get() + sweep.get(), at + 9);
    return to > from ? orbitArc(from, to - from) + orbitArc(from + 180, to - from) : 'M0 0';
  });
  const glintAlpha = useDerivedValue(() => {
    const phase = glintPhase.get();
    return ambient * (1 - activity.get()) * arcsAlpha.get() * (phase < 1 ? Math.sin(Math.PI * phase) : 0);
  });

  // Colour that the lit categories bleed into the glass.
  const orange = useDerivedValue(() => clamp01(categorySpend(total.get(), 0) / AMOUNTS[0]));
  const blue = useDerivedValue(() => clamp01(categorySpend(total.get(), 2) / AMOUNTS[2]));
  const green = useDerivedValue(() => clamp01(categorySpend(total.get(), 3) / AMOUNTS[3]));

  // A soft highlight that keeps travelling through the lit ticks.
  const lit = useDerivedValue(() => ({ x: TICK_X - 1, y: TICK_Y - 4, width: Math.max(0, litTicks(total.get()) * TICK_PITCH - 1), height: TICK_H + 8 }));
  const shine = useDerivedValue(() => [{ translateX: TICK_X - 24 + (TICKS * TICK_PITCH + 48) * Math.min(1, ((time.get() + 1.2) % GLINT_PERIOD) / 1.5) }]);
  const shineAlpha = useDerivedValue(() => ambient * (1 - activity.get()));

  const tile = { rect: { x: TILE_X, y: TILE_Y, width: TILE_W, height: TILE_H }, rx: TILE_R, ry: TILE_R };

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        {/* Orbit */}
        <Circle cx={C} cy={C} r={ORBIT_R} style="stroke" strokeWidth={2} color="rgba(255,255,255,0.055)" />
        <Group
          layer={
            <Paint opacity={bloom}>
              <Blur blur={7} />
            </Paint>
          }
        >
          <Path path={arcs} style="stroke" strokeWidth={7} strokeCap="round" color="rgba(255,255,255,0.9)" />
        </Group>
        <Path path={arcs} style="stroke" strokeWidth={3.7} strokeCap="round" color="#ffffff" opacity={arcsAlpha} />
        <Path path={glint} style="stroke" strokeWidth={6.5} strokeCap="round" color="#ffffff" opacity={glintAlpha}>
          <BlurMask blur={2.2} style="normal" />
        </Path>

        {/* Glass tile */}
        <RoundedRect x={TILE_X} y={TILE_Y + 6} width={TILE_W} height={TILE_H} r={TILE_R} color="rgba(0,0,0,0.4)">
          <BlurMask blur={12} style="normal" />
        </RoundedRect>
        <RoundedRect x={TILE_X} y={TILE_Y} width={TILE_W} height={TILE_H} r={TILE_R}>
          <LinearGradient
            start={vec(TILE_X + TILE_W, TILE_Y)}
            end={vec(TILE_X, TILE_Y + TILE_H)}
            colors={['#3d3d3d', '#232323', '#1d1d1d', '#434343']}
            positions={[0, 0.34, 0.68, 1]}
          />
        </RoundedRect>
        <Group clip={tile}>
          <Circle cx={TILE_X + 38} cy={TILE_Y + 132} r={62} color="rgba(214,92,36,0.13)" opacity={orange}>
            <BlurMask blur={30} style="normal" />
          </Circle>
          <Circle cx={TILE_X + 138} cy={TILE_Y + 116} r={50} color="rgba(48,84,196,0.14)" opacity={blue}>
            <BlurMask blur={28} style="normal" />
          </Circle>
          <Circle cx={TILE_X + 206} cy={TILE_Y + 150} r={58} color="rgba(30,170,96,0.13)" opacity={green}>
            <BlurMask blur={30} style="normal" />
          </Circle>
        </Group>
        <RoundedRect x={TILE_X + 0.5} y={TILE_Y + 0.5} width={TILE_W - 1} height={TILE_H - 1} r={TILE_R - 0.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(TILE_X + TILE_W, TILE_Y)}
            end={vec(TILE_X, TILE_Y + TILE_H)}
            colors={['rgba(255,255,255,0.3)', 'rgba(255,255,255,0.12)', 'rgba(255,255,255,0.26)']}
          />
        </RoundedRect>

        {Array.from({ length: TICKS }, (_, i) => (
          <Tick key={i} index={i} total={total} activity={activity} />
        ))}
        <Group clip={TICK_CLIP} opacity={shineAlpha}>
          <Group clip={lit}>
            <Group transform={shine}>
              <Rect x={-12} y={TICK_Y - 2} width={24} height={TICK_H + 4}>
                <LinearGradient start={vec(-12, 0)} end={vec(12, 0)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.75)', 'rgba(255,255,255,0)']} />
              </Rect>
            </Group>
          </Group>
        </Group>
        {CATEGORIES.map((_, i) => (
          <Marker key={i} index={i} total={total} />
        ))}
      </Group>
    </Canvas>
  );
}

/** A counting amount: whole part bright, cents dim. Only this small text re-renders while it counts. */
function Money({
  cents,
  initial,
  whole,
  fraction,
}: {
  cents: SharedValue<number>;
  /** Starting amount in cents, as a plain number. */
  initial: number;
  whole: object;
  fraction: object;
}) {
  const [shown, setShown] = useState(() => Math.round(initial));
  useAnimatedReaction(
    () => Math.round(cents.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShown, now);
    },
  );
  return (
    <Text numberOfLines={1}>
      <Text style={whole}>${Math.floor(shown / 100)}</Text>
      <Text style={fraction}>.{String(shown % 100).padStart(2, '0')}</Text>
    </Text>
  );
}

function Row({ index, total, initialTotal }: { index: number; total: SharedValue<number>; initialTotal: number }) {
  const cents = useDerivedValue(() => categorySpend(total.get(), index) * 100);
  // A row is dim until its category starts to fill.
  const style = useAnimatedStyle(() => ({ opacity: 0.38 + 0.62 * clamp01((cents.get() / (AMOUNTS[index] * 100)) * 5) }));
  return (
    <Animated.View style={[styles.row, { top: ROW_Y + index * ROW_PITCH - 8 }, style]}>
      <Text style={styles.label} numberOfLines={1}>
        {CATEGORIES[index].label}
      </Text>
      <Money
        cents={cents}
        initial={categorySpend(initialTotal, index) * 100}
        whole={styles.amount}
        fraction={styles.amountCents}
      />
    </Animated.View>
  );
}

/**
 * Dark glass "today spending" tile wrapped by a thin white orbit: the amount spent, its share of
 * the budget, a bar of coloured ticks split by category and the category list. The ticks light
 * in a wave, each one stretching as the fill reaches it, while the amounts count up row by row
 * and each category bleeds its colour into the glass; the two orbiting arcs grow and turn once
 * around the tile, overshoot as they land with a flash of their bloom, and at rest a glint keeps
 * running along the arcs and through the lit ticks.
 */
export function SpendingOrbit({ progress, autoPlay = false, title = 'TODAY SPENDING' }: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;

  // Seconds: `time` never stops (glints), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  // When a controlled value last changed, on the `time` axis.
  const movedAt = useSharedValue(-10);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    movedAt.set(time.get());
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 900, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held, movedAt, time]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
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

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));
  const total = useDerivedValue(() => clamp01(value.get()) * BUDGET);
  const totalCents = useDerivedValue(() => total.get() * 100);
  // Plain copy of the starting value: children seed their text from it instead of reading a
  // shared value while rendering.
  const initialTotal = clamp01(controlled ? target : loopValue(autoPlay && !reducedMotion ? 0 : REST_T)) * BUDGET;
  const activity = useDerivedValue(() => {
    if (reducedMotion) return 0;
    if (!controlled) return loopActivity(clock.get());
    const u = (time.get() - movedAt.get()) / 0.8;
    return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
  });
  const landed = useDerivedValue(() => {
    if (reducedMotion) return 10;
    if (!controlled) return clock.get() < LOOP - RESET ? clock.get() - LANDED : 10;
    return time.get() - movedAt.get() - 0.7;
  });

  const [percent, setPercent] = useState(Math.round(target * 100));
  useAnimatedReaction(
    () => Math.round(value.get() * 100),
    (now, before) => {
      if (now !== before) scheduleOnRN(setPercent, now);
    },
  );

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable
      onPress={restart}
      style={styles.widget}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={`${title}, $${((percent / 100) * BUDGET).toFixed(2)} spent`}
      accessibilityValue={{ min: 0, max: 100, now: percent }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface total={total} value={value} time={time} activity={activity} landed={landed} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View style={styles.tile} pointerEvents="none">
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <View style={styles.total}>
          <Money cents={totalCents} initial={initialTotal * 100} whole={styles.spent} fraction={styles.spentCents} />
        </View>
        <Text style={styles.percent}>{percent}%</Text>
      </View>
      {CATEGORIES.map((category, i) => (
        <Row key={category.label} index={i} total={total} initialTotal={initialTotal} />
      ))}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  widget: {
    width: SIZE,
    height: SIZE,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: SIZE + BLEED * 2,
    height: SIZE + BLEED * 2,
  },
  tile: {
    position: 'absolute',
    left: TILE_X,
    top: TILE_Y,
    width: TILE_W,
    height: TILE_H,
  },
  title: {
    position: 'absolute',
    left: INSET,
    top: 39.6 - 9,
    color: '#8c8c8c',
    fontSize: 12.6,
    lineHeight: 18,
    fontWeight: '700',
    fontFamily: MONO,
    letterSpacing: 1.5,
  },
  total: {
    position: 'absolute',
    left: INSET - 1,
    top: 83.2 - 29.3,
  },
  spent: {
    color: '#eeeeee',
    fontSize: 29,
    lineHeight: 38,
    fontWeight: '500',
    letterSpacing: -1.3,
    fontVariant: ['tabular-nums'],
  },
  spentCents: {
    color: '#808080',
    fontSize: 16,
    fontWeight: '500',
    letterSpacing: -0.2,
    fontVariant: ['tabular-nums'],
  },
  percent: {
    position: 'absolute',
    right: INSET,
    top: 83.2 - 16,
    color: '#808080',
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '500',
    letterSpacing: -0.2,
    fontVariant: ['tabular-nums'],
  },
  row: {
    position: 'absolute',
    left: TILE_X + 41.5,
    right: TILE_X + INSET,
    height: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  label: {
    color: '#ececec',
    fontSize: 10.3,
    lineHeight: 16,
    fontWeight: '500',
    fontFamily: MONO,
    letterSpacing: 0.35,
  },
  amount: {
    color: '#f2f2f2',
    fontSize: 10.3,
    lineHeight: 16,
    fontWeight: '600',
    fontFamily: MONO,
    letterSpacing: 0.35,
  },
  amountCents: {
    color: '#858585',
    fontSize: 10.3,
    fontWeight: '500',
    fontFamily: MONO,
    letterSpacing: 0.35,
  },
});
