import { BlurMask, Canvas, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled share of the budget spent, 0…1. */
  progress?: number;
  /** Lays the card out part by part, counts up to the amount shown in the reference, holds, and starts over. */
  autoPlay?: boolean;
  /** The monthly limit in whole dollars: the spent and remaining amounts are derived from it. */
  budget?: number;
  title?: string;
  subtitle?: string;
  month?: string;
  spentLabel?: string;
  remainingLabel?: string;
  buttonLabel?: string;
  onMonthPress?: () => void;
  onPress?: () => void;
};

const WIDTH = 320;
const HEIGHT = 237;
const RADIUS = 25;
// The card's canvas overflows it by this much on every side to fit its shadow.
const BLEED = 40;

const BAR_X = 21.9;
const BAR_Y = 109.7;
const BAR_W = 276.2;
const BAR_H = 6.4;
// Room around the bar's own canvas for its glow.
const BAR_BLEED = 12;

const BUTTON_X = 21;
const BUTTON_Y = 165.7;
const BUTTON_W = 278;
const BUTTON_H = 50.3;
const BUTTON_BLEED = 16;

const CHIP_X = 240.5;
const CHIP_Y = 21.9;
const CHIP_W = 57.6;
const CHIP_H = 22;
const CHIP_BLEED = 12;

// Rolling digits: the size of one cell, and how fast a column may turn (digits per second).
const DIGIT_W = 8;
const DIGIT_H = 15;
const ROLL_SPEED = 16;

// The reference shows $2,886 of $8,000 spent; the autoPlay loop built around it (seconds).
const REST = 2886 / 8000;
const LOW = 0.08;
const ENTER = 0.9;
const FILL = 2.6;
const HOLD = 3.4;
const EXIT = 0.5;
const LOOP = ENTER + FILL + HOLD + EXIT;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = ENTER + FILL + 1.4;

const INK = '#2b2b2b';
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const CHEVRON = 'M-6.3 -3.2L0 3.3L6.3 -3.2';

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

/** Share of the budget spent at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < ENTER) return LOW;
  if (t < ENTER + FILL) return LOW + (REST - LOW) * easeInOutCubic((t - ENTER) / FILL);
  return REST;
}

/** 0…1: how far part `index` of the card has come in at `t` seconds into the loop. */
function entered(t: number, index: number) {
  'worklet';
  return easeOutCubic((t - 0.08 - 0.075 * index) / 0.55);
}

/** The card's parts fade out together just before the loop starts over. */
function leaving(t: number) {
  'worklet';
  return t > LOOP - EXIT ? clamp01((LOOP - t) / (EXIT * 0.8)) : 1;
}

function digitOf(amount: number, place: number) {
  'worklet';
  return Math.floor(amount / Math.pow(10, place)) % 10;
}

function money(amount: number) {
  return `$${String(Math.round(amount)).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`;
}

function usePart(clock: SharedValue<number>, index: number) {
  return useAnimatedStyle(() => {
    const t = clock.get();
    const e = entered(t, index);
    return { opacity: e * leaving(t), transform: [{ translateY: 9 * (1 - e) }] };
  });
}

type Rolling = {
  /** Whole dollars to show. */
  amount: SharedValue<number>;
  /** The amount at mount as a plain number, so the columns can start in place without reading a shared value while rendering. */
  initial: number;
  /** 1 while the amount is rising, -1 while it falls, 0 at rest: the way the columns turn. ±2 after a jump. */
  trend: SharedValue<number>;
  /** False with reduced motion: digits switch without rolling. */
  live: boolean;
};

/** One odometer column: the digits 0…9 (and 0 again) stacked, shifted so the current one shows. */
function Digit({ amount, initial, trend, live, place }: Rolling & { place: number }) {
  const pos = useSharedValue(digitOf(initial, place));
  // Leading columns collapse while the amount is too small to need them.
  const shown = useSharedValue(place === 0 || initial >= Math.pow(10, place) ? 1 : 0);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const value = amount.get();
    const needed = place === 0 || value >= Math.pow(10, place) ? 1 : 0;
    const way = trend.get();
    if (Math.abs(way) > 1) {
      // The amount jumped (a run starting over): show it in place instead of rolling to it.
      shown.set(needed);
      pos.set(digitOf(value, place));
      return;
    }
    shown.set(shown.get() + (needed - shown.get()) * Math.min(1, dt * 12));

    const at = pos.get();
    const ahead = (((digitOf(value, place) - at) % 10) + 10) % 10;
    // Turn the way the amount is going; at rest take the short way round.
    let delta = ahead;
    if (way < 0 ? ahead > 0.5 : way === 0 ? ahead > 5 : ahead > 9.5) delta = ahead - 10;
    if (Math.abs(delta) < 0.004) {
      if (delta !== 0) pos.set((((at + delta) % 10) + 10) % 10);
      return;
    }
    // Follow with a speed limit, so a fast count reads as a spinning column that then settles.
    const limit = ROLL_SPEED * dt;
    const move = Math.max(-limit, Math.min(limit, delta * Math.min(1, dt * 16)));
    pos.set((((at + move) % 10) + 10) % 10);
  }, live);

  const cell = useAnimatedStyle(() => {
    const s = live ? shown.get() : place === 0 || amount.get() >= Math.pow(10, place) ? 1 : 0;
    return { width: DIGIT_W * s, opacity: s };
  });
  const column = useAnimatedStyle(() => ({
    transform: [{ translateY: -DIGIT_H * (live ? pos.get() : digitOf(amount.get(), place)) }],
  }));

  return (
    <Animated.View style={[styles.digitCell, cell]}>
      <Animated.View style={column}>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0].map((digit, i) => (
          <Text key={i} style={styles.digit}>
            {digit}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

function Comma({ amount, live, place }: { amount: SharedValue<number>; live: boolean; place: number }) {
  const shown = useDerivedValue(() => {
    const needed = amount.get() >= Math.pow(10, place) ? 1 : 0;
    return live ? withTiming(needed, { duration: 160 }) : needed;
  });
  const style = useAnimatedStyle(() => ({ width: 3.6 * shown.get(), opacity: shown.get() }));
  return (
    <Animated.View style={[styles.commaCell, style]}>
      <Text style={styles.comma}>,</Text>
    </Animated.View>
  );
}

/** A dollar amount whose digits roll like an odometer. */
function RollingAmount({ amount, initial, trend, live, places, right }: Rolling & { places: number; right?: boolean }) {
  const cells = [];
  for (let place = places - 1; place >= 0; place--) {
    cells.push(<Digit key={`d${place}`} amount={amount} initial={initial} trend={trend} live={live} place={place} />);
    if (place > 0 && place % 3 === 0) cells.push(<Comma key={`c${place}`} amount={amount} live={live} place={place} />);
  }
  return (
    <View style={[styles.amountRow, right ? styles.amountRight : styles.amountLeft]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Text style={styles.amountSign}>$</Text>
      {cells}
    </View>
  );
}

function Bar({ value, time, ambient }: { value: SharedValue<number>; time: SharedValue<number>; ambient: number }) {
  const x = BAR_BLEED;
  const y = BAR_BLEED;
  const width = useDerivedValue(() => Math.max(BAR_H, BAR_W * clamp01(value.get())));
  const clip = useDerivedValue(() => ({ rect: { x, y, width: width.get(), height: BAR_H }, rx: BAR_H / 2, ry: BAR_H / 2 }));
  const end = useDerivedValue(() => vec(x + width.get(), y));
  const glowWidth = useDerivedValue(() => width.get() * 0.62);
  // The aqua half of the bar has a faint glow that breathes.
  const glow = useDerivedValue(() => 0.3 + ambient * 0.12 * Math.sin(time.get() * 1.7));
  // A bead of light runs along the filled part every few seconds.
  const beadAt = useDerivedValue(() => (time.get() % 3.4) / 1.2);
  const beadX = useDerivedValue(() => x - 16 + (width.get() + 16) * clamp01(beadAt.get()));
  const beadOpacity = useDerivedValue(() => {
    const s = beadAt.get();
    return s >= 1 ? 0 : ambient * 0.85 * Math.sin(Math.PI * s);
  });

  return (
    <Canvas style={styles.barCanvas}>
      <RoundedRect x={x} y={y} width={BAR_W} height={BAR_H} r={BAR_H / 2} color="#d1d1d1" />
      <RoundedRect x={x} y={y + 1} width={glowWidth} height={BAR_H} r={BAR_H / 2} color="#19e9ff" opacity={glow}>
        <BlurMask blur={4.5} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={width} height={BAR_H} r={BAR_H / 2}>
        <LinearGradient
          start={vec(x, y)}
          end={end}
          colors={['#00f6ff', '#2ee9ff', '#79d3fb', '#4a82a6', '#23262b']}
          positions={[0, 0.3, 0.48, 0.74, 1]}
        />
      </RoundedRect>
      <Group clip={clip}>
        <RoundedRect x={beadX} y={y + 0.8} width={16} height={BAR_H - 1.6} r={2.4} color="#ffffff" opacity={beadOpacity}>
          <BlurMask blur={2} style="normal" />
        </RoundedRect>
      </Group>
    </Canvas>
  );
}

/**
 * White budget card: the month chip, the monthly limit, a thin aqua-to-ink bar, the amounts spent
 * and left, and a dark "View More" pill. The card's parts come in one after another, the amounts
 * roll like odometers while the bar grows, and a bead of light runs along the filled part.
 */
export function BudgetBar({
  progress,
  autoPlay = false,
  budget = 8000,
  title = 'Monthly Budget',
  subtitle = 'Monthly Spending Limit',
  month = 'SEP',
  spentLabel = 'Spent',
  remainingLabel = 'Budget Remaining',
  buttonLabel = 'View More',
  onMonthPress,
  onPress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;
  const places = String(Math.round(budget)).length;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(target);
  const trend = useSharedValue(0);
  const reported = useSharedValue(Math.round(budget * target));

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
  const spent = useDerivedValue(() => Math.round(budget * clamp01(value.get())));
  const initialSpent = Math.round(budget * clamp01(controlled ? target : loopValue(autoPlay && !reducedMotion ? 0 : REST_T)));
  const left = useDerivedValue(() => Math.round(budget) - spent.get());

  // Read out the amounts once they have stopped moving, not on every count.
  const [spentShown, setSpentShown] = useState(Math.round(budget * target));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    const now = value.get();
    const step = now - before.get();
    before.set(now);
    trend.set(Math.abs(step) > 0.1 ? 2 : step > 0.00002 ? 1 : step < -0.00002 ? -1 : 0);
    if (step === 0 && reported.get() !== spent.get()) {
      reported.set(spent.get());
      scheduleOnRN(setSpentShown, spent.get());
    }
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

  const part0 = usePart(clock, 0);
  const part1 = usePart(clock, 1);
  const part2 = usePart(clock, 2);
  const part3 = usePart(clock, 3);
  const part4 = usePart(clock, 4);
  const part5 = usePart(clock, 5);

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };
  const tick = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const settled = reducedMotion || controlled ? Math.round(budget * target) : spentShown;
  // What is left falls while the spending rises, so its columns turn the other way.
  const falling = useDerivedValue(() => -trend.get());
  const live = !reducedMotion;

  return (
    <Pressable onPress={restart} style={styles.card} accessible={false}>
      <View pointerEvents="none" style={styles.surface}>
        <Canvas style={styles.canvas}>
          <RoundedRect x={BLEED + 6} y={BLEED + 14} width={WIDTH - 12} height={HEIGHT - 10} r={RADIUS} color="rgba(0,0,0,0.07)">
            <BlurMask blur={16} style="normal" />
          </RoundedRect>
          <RoundedRect x={BLEED} y={BLEED} width={WIDTH} height={HEIGHT} r={RADIUS} color="#ffffff" />
        </Canvas>
      </View>

      <Animated.View style={[styles.part, part0]} pointerEvents="box-none">
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Pressable
          onPress={onMonthPress}
          onPressIn={tick}
          hitSlop={8}
          accessibilityRole="button"
          accessibilityLabel={`Month, ${month}`}
          style={styles.chipSlot}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.fill,
                {
                  transform: [{ scale: pressed ? 0.95 : 1 }],
                  transitionProperty: 'transform',
                  transitionDuration: 130,
                  transitionTimingFunction: CSS_EASE_OUT,
                },
              ]}
            >
              <Canvas style={styles.chipCanvas}>
                <RoundedRect x={CHIP_BLEED + 2} y={CHIP_BLEED + 5} width={CHIP_W - 4} height={CHIP_H - 3} r={6} color="rgba(0,0,0,0.3)">
                  <BlurMask blur={4.5} style="normal" />
                </RoundedRect>
                <RoundedRect x={CHIP_BLEED} y={CHIP_BLEED} width={CHIP_W} height={CHIP_H} r={6}>
                  <LinearGradient start={vec(0, CHIP_BLEED)} end={vec(0, CHIP_BLEED + CHIP_H)} colors={['#282828', '#3d3d3d']} />
                </RoundedRect>
                <Group transform={[{ translateX: CHIP_BLEED + 43.4 }, { translateY: CHIP_BLEED + 11.2 }]}>
                  <Path path={CHEVRON} style="stroke" strokeWidth={1.3} strokeCap="round" strokeJoin="round" color="#ffffff" />
                </Group>
              </Canvas>
              <Text style={styles.month} numberOfLines={1}>
                {month}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>

      <Animated.View style={[styles.part, part1]} pointerEvents="none">
        <Text
          style={styles.amount}
          numberOfLines={1}
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={`${title}, ${money(budget)}`}
          accessibilityValue={{
            min: 0,
            max: Math.round(budget),
            now: settled,
            text: `${spentLabel} ${money(settled)}, ${remainingLabel} ${money(budget - settled)}`,
          }}
        >
          {money(budget).replace('$', '$ ')}
        </Text>
      </Animated.View>

      <Animated.View style={[styles.part, part2]} pointerEvents="none">
        <Text style={styles.subtitle} numberOfLines={1}>
          {subtitle}
        </Text>
      </Animated.View>

      <Animated.View style={[styles.part, part3]} pointerEvents="none">
        <View style={styles.barSlot}>
          <Bar value={value} time={time} ambient={reducedMotion ? 0 : 1} />
        </View>
      </Animated.View>

      <Animated.View style={[styles.part, part4]} pointerEvents="none">
        <Text style={[styles.caption, styles.captionLeft]} numberOfLines={1}>
          {spentLabel}
        </Text>
        <Text style={[styles.caption, styles.captionRight]} numberOfLines={1}>
          {remainingLabel}
        </Text>
        <RollingAmount amount={spent} initial={initialSpent} trend={trend} live={live} places={places} />
        <RollingAmount amount={left} initial={Math.round(budget) - initialSpent} trend={falling} live={live} places={places} right />
      </Animated.View>

      <Animated.View style={[styles.part, part5]} pointerEvents="box-none">
        <Pressable onPress={onPress} onPressIn={tick} accessibilityRole="button" accessibilityLabel={buttonLabel} style={styles.buttonSlot}>
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.fill,
                styles.centered,
                {
                  transform: [{ scale: pressed ? 0.97 : 1 }],
                  transitionProperty: 'transform',
                  transitionDuration: 130,
                  transitionTimingFunction: CSS_EASE_OUT,
                },
              ]}
            >
              <Canvas style={styles.buttonCanvas}>
                <RoundedRect
                  x={BUTTON_BLEED + 8}
                  y={BUTTON_BLEED + 9}
                  width={BUTTON_W - 16}
                  height={BUTTON_H - 4}
                  r={BUTTON_H / 2}
                  color="rgba(0,0,0,0.3)"
                >
                  <BlurMask blur={6} style="normal" />
                </RoundedRect>
                <RoundedRect x={BUTTON_BLEED} y={BUTTON_BLEED} width={BUTTON_W} height={BUTTON_H} r={BUTTON_H / 2}>
                  <LinearGradient start={vec(0, BUTTON_BLEED)} end={vec(0, BUTTON_BLEED + BUTTON_H)} colors={['#242424', '#434343']} />
                </RoundedRect>
                {/* A hairline that is dark along the top and light along the bottom, like a pressed rim. */}
                <RoundedRect
                  x={BUTTON_BLEED + 0.6}
                  y={BUTTON_BLEED + 0.6}
                  width={BUTTON_W - 1.2}
                  height={BUTTON_H - 1.2}
                  r={BUTTON_H / 2 - 0.6}
                  style="stroke"
                  strokeWidth={1.2}
                >
                  <LinearGradient
                    start={vec(0, BUTTON_BLEED)}
                    end={vec(0, BUTTON_BLEED + BUTTON_H)}
                    colors={['rgba(120,120,120,0.55)', 'rgba(120,120,120,0.15)', 'rgba(170,170,170,0.7)']}
                    positions={[0, 0.5, 1]}
                  />
                </RoundedRect>
              </Canvas>
              <Text style={styles.buttonLabel} numberOfLines={1}>
                {buttonLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>
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
  part: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  fill: {
    flex: 1,
  },
  centered: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    position: 'absolute',
    left: 22.4,
    top: 25.4,
    width: 200,
    color: '#a1a1a1',
    fontSize: 10.8,
    lineHeight: 16,
    letterSpacing: -0.2,
  },
  chipSlot: {
    position: 'absolute',
    left: CHIP_X,
    top: CHIP_Y,
    width: CHIP_W,
    height: CHIP_H,
  },
  chipCanvas: {
    position: 'absolute',
    left: -CHIP_BLEED,
    top: -CHIP_BLEED,
    width: CHIP_W + CHIP_BLEED * 2,
    height: CHIP_H + CHIP_BLEED * 2,
  },
  month: {
    position: 'absolute',
    left: 8.7,
    top: 3,
    width: 30,
    color: '#ffffff',
    fontSize: 10.4,
    lineHeight: 16,
    fontWeight: '500',
  },
  amount: {
    position: 'absolute',
    left: 22.4,
    top: 45.9,
    width: 260,
    color: INK,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '600',
    letterSpacing: -0.4,
  },
  subtitle: {
    position: 'absolute',
    left: 22.4,
    top: 86.1,
    width: 260,
    color: '#a3a3a3',
    fontSize: 13,
    lineHeight: 18,
    letterSpacing: -0.2,
  },
  barSlot: {
    position: 'absolute',
    left: BAR_X - BAR_BLEED,
    top: BAR_Y - BAR_BLEED,
  },
  barCanvas: {
    width: BAR_W + BAR_BLEED * 2,
    height: BAR_H + BAR_BLEED * 2,
  },
  caption: {
    position: 'absolute',
    top: 126.4,
    width: 140,
    color: '#a1a1a1',
    fontSize: 10.8,
    lineHeight: 16,
    letterSpacing: -0.2,
  },
  captionLeft: {
    left: 22.4,
  },
  captionRight: {
    left: 297.6 - 140,
    textAlign: 'right',
  },
  amountRow: {
    position: 'absolute',
    top: 149 - DIGIT_H / 2,
    height: DIGIT_H,
    flexDirection: 'row',
  },
  amountLeft: {
    left: 22.4,
  },
  amountRight: {
    right: WIDTH - 297.6,
  },
  amountSign: {
    color: INK,
    fontSize: 13.2,
    lineHeight: DIGIT_H,
    marginRight: 0.6,
  },
  digitCell: {
    height: DIGIT_H,
    overflow: 'hidden',
  },
  digit: {
    width: DIGIT_W,
    height: DIGIT_H,
    textAlign: 'center',
    color: INK,
    fontSize: 13.2,
    lineHeight: DIGIT_H,
    fontVariant: ['tabular-nums'],
  },
  commaCell: {
    height: DIGIT_H,
    overflow: 'hidden',
  },
  comma: {
    width: 3.6,
    textAlign: 'center',
    color: INK,
    fontSize: 13.2,
    lineHeight: DIGIT_H,
  },
  buttonSlot: {
    position: 'absolute',
    left: BUTTON_X,
    top: BUTTON_Y,
    width: BUTTON_W,
    height: BUTTON_H,
  },
  buttonCanvas: {
    position: 'absolute',
    left: -BUTTON_BLEED,
    top: -BUTTON_BLEED,
    width: BUTTON_W + BUTTON_BLEED * 2,
    height: BUTTON_H + BUTTON_BLEED * 2,
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
    letterSpacing: -0.3,
  },
});
