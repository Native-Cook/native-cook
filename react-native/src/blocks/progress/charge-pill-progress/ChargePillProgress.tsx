import { BlurMask, Canvas, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
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
  /** Controlled charge level, 0…1. Leave undefined to show the reference's 76% (or the autoPlay loop). */
  progress?: number;
  /** Charges from 20% to 76% by itself, holds, drops back and starts over. */
  autoPlay?: boolean;
  /** Minutes a full charge takes; the "~15m" estimate is the share of it that is left. */
  fullChargeMinutes?: number;
  accessibilityLabel?: string;
};

const WIDTH = 340;
const HEIGHT = 136;
// The canvas overflows the component by this much on every side to fit the pill's shadow.
const BLEED = 36;

// Compact pill: bolt on the left, level on the right.
const MINI_X = 70;
const MINI_W = 200;
const MINI_H = 36;
const MINI_BOLT_X = MINI_X + 16;
// Expanded pill: bar with a knob, time left on the right.
const PILL_Y = 60;
const PILL_H = 76;
const BAR_X = 33;
const BAR_W = 228;
const BAR_H = 7.2;
const BAR_CY = PILL_Y + PILL_H / 2;
const KNOB_W = 34;
const KNOB_H = 18.4;

const DIGIT_W = 8.7;
const DIGIT_H = 18;

const GREEN = '#3fc760';
const MINT = '#c4ffd2';

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.76;
const FROM = 0.2;
const START = 0.4;
const FILL = 3.2;
const HOLD = 3.4;
const RESET = 0.6;
const LOOP = START + FILL + HOLD + RESET;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = START + FILL + 1;
// A pulse of charge leaves the start of the bar this often; it takes TRAVEL of that to reach the knob.
const PULSE = 1.3;
const TRAVEL = 0.55;
const STREAK = 46;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// SF-style bolt in an 11 × 14 box.
const BOLT = 'M7.8 0L0.2 8.4H4.5L3.2 14L10.8 5.5H6.5Z';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Charge level at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return FROM;
  if (t < START + FILL) return FROM + (REST - FROM) * easeInOutCubic((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST - (REST - FROM) * easeInOutCubic((t - (LOOP - RESET)) / RESET);
}

/** Where the knob's centre sits on the bar, from its left end. */
function knobCenter(p: number) {
  'worklet';
  return KNOB_W / 2 + (BAR_W - KNOB_W) * clamp01(p);
}

/** 0…1 along the bar for the travelling pulse; it accelerates into the knob. */
function pulseTravel(time: number) {
  'worklet';
  const phase = (time % PULSE) / PULSE;
  return phase < TRAVEL ? Math.pow(phase / TRAVEL, 2) : 1;
}

/** 0…1 flash that starts when a pulse reaches the knob and dies away quickly. */
function pulseFlare(time: number) {
  'worklet';
  const since = ((time % PULSE) / PULSE - TRAVEL) * PULSE;
  return since < 0 ? 0 : Math.min(1, since / 0.05) * Math.exp(-5.5 * since);
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** 0 with reduced motion: no pulses, no breathing. */
  ambient: number;
};

function Surface({ value, time, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const bx = x + BAR_X;
  const cy = y + BAR_CY;
  const miniCy = y + MINI_H / 2;

  const knobX = useDerivedValue(() => knobCenter(value.get()));
  const flare = useDerivedValue(() => ambient * pulseFlare(time.get()));

  const fillWidth = useDerivedValue(() => knobX.get());
  const streak = useDerivedValue(() => [
    { translateX: -STREAK + (knobX.get() + STREAK * 0.4) * pulseTravel(time.get()) },
  ]);
  const streakAlpha = useDerivedValue(() => {
    const phase = (time.get() % PULSE) / PULSE;
    return ambient * (phase < TRAVEL ? clamp01(phase / 0.12) : 0);
  });

  const knob = useDerivedValue(() => [{ translateX: bx + knobX.get() }, { translateY: cy }]);
  const knobScale = useDerivedValue(() => [{ scale: 1 + 0.08 * flare.get() }]);
  // The halo trails to the left like a comet, breathes, and flares when a pulse lands.
  const haloAlpha = useDerivedValue(() => 0.62 + ambient * 0.08 * Math.sin(time.get() * 2.3) + 0.38 * flare.get());
  const haloScale = useDerivedValue(() => [{ scale: 1 + 0.22 * flare.get() }]);

  const boltScale = useDerivedValue(() => [{ scale: 1 + 0.1 * flare.get() }]);
  const boltGlow = useDerivedValue(() => 0.75 * flare.get());

  return (
    <Canvas style={styles.canvas}>
      {/* Compact pill. */}
      <RoundedRect x={x + MINI_X} y={y} width={MINI_W} height={MINI_H} r={MINI_H / 2} color="#000000" />
      <Group transform={[{ translateX: x + MINI_BOLT_X }, { translateY: miniCy }]}>
        <RoundedRect x={-9} y={-9} width={18} height={18} r={9} color="#35e066" opacity={boltGlow}>
          <BlurMask blur={7} style="normal" />
        </RoundedRect>
        <Group transform={boltScale}>
          <Group transform={[{ scale: 1.31 }, { translateX: -5.5 }, { translateY: -7 }]}>
            <Path path={BOLT} color={GREEN} strokeJoin="round" />
            <Path path={BOLT} color={GREEN} style="stroke" strokeWidth={1} strokeJoin="round" />
            <Path path={BOLT} color={MINT} opacity={boltGlow} />
          </Group>
        </Group>
      </Group>

      {/* Expanded pill. */}
      <RoundedRect x={x + 8} y={y + PILL_Y + 14} width={WIDTH - 16} height={PILL_H - 6} r={PILL_H / 2} color="rgba(0,0,0,0.22)">
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y + PILL_Y} width={WIDTH} height={PILL_H} r={PILL_H / 2} color="#000000" />

      <RoundedRect x={bx} y={cy - BAR_H / 2} width={BAR_W} height={BAR_H} r={BAR_H / 2} color="#313131" />

      <Group transform={knob}>
        <Group transform={haloScale} opacity={haloAlpha}>
          <RoundedRect x={-62} y={-9} width={76} height={18} r={9} color="rgba(40,214,90,0.55)">
            <BlurMask blur={11} style="normal" />
          </RoundedRect>
          <RoundedRect x={-KNOB_W / 2 - 2} y={-KNOB_H / 2 - 2} width={KNOB_W + 4} height={KNOB_H + 4} r={KNOB_H / 2 + 2} color="rgba(48,226,98,0.85)">
            <BlurMask blur={8} style="normal" />
          </RoundedRect>
        </Group>
      </Group>

      <RoundedRect x={bx} y={cy - BAR_H / 2} width={fillWidth} height={BAR_H} r={BAR_H / 2} color={GREEN} />
      {/* The pulse: a streak of light that runs along the fill into the knob. */}
      <RoundedRect x={bx} y={cy - BAR_H / 2} width={fillWidth} height={BAR_H} r={BAR_H / 2} opacity={streakAlpha}>
        <LinearGradient
          start={vec(bx, cy)}
          end={vec(bx + STREAK, cy)}
          colors={['rgba(196,255,210,0)', 'rgba(196,255,210,0.95)', 'rgba(196,255,210,0)']}
          positions={[0, 0.75, 1]}
          transform={streak}
        />
      </RoundedRect>

      <Group transform={knob}>
        <Group transform={knobScale}>
          <RoundedRect x={-KNOB_W / 2} y={-KNOB_H / 2} width={KNOB_W} height={KNOB_H} r={KNOB_H / 2} color="#41c966" />
          <RoundedRect x={-KNOB_W / 2} y={-KNOB_H / 2} width={KNOB_W} height={KNOB_H} r={KNOB_H / 2} color={MINT} opacity={boltGlow} />
          <Group transform={[{ translateX: -KNOB_W / 2 + 7.8 }, { scale: 0.86 }, { translateX: -5.5 }, { translateY: -7 }]}>
            <Path path={BOLT} color="#b4ecc2" />
          </Group>
        </Group>
      </Group>
    </Canvas>
  );
}

const DIGITS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0];

/** One wheel of the odometer. `place` is 1 for ones, 10 for tens, 100 for hundreds. */
function Wheel({ place, value }: { place: number; value: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const v = clamp01(value.get()) * 100;
    // A wheel turns with the one below it only while that one rolls from 9 to 0.
    const lower = place === 1 ? 0 : (v / (place / 10)) % 10;
    const turn = place === 1 ? v % 10 : (Math.floor(v / place) % 10) + Math.max(0, lower - 9);
    return {
      // Leading zeros stay hidden.
      opacity: place === 1 ? 1 : clamp01(v - (place - 1)),
      transform: [{ translateY: -turn * DIGIT_H }],
    };
  });
  return (
    <View style={styles.wheel}>
      <Animated.View style={style}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={styles.digit}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

/**
 * Live-activity style charging UI: a compact black pill with a bolt and the level, and an expanded
 * pill whose green bar ends in a glowing knob, with the time left beside it. Pulses of charge run
 * along the bar into the knob, which flares and bumps as each one lands (the compact bolt flashes
 * with it), the halo breathes, and the compact level rolls like an odometer.
 */
export function ChargePillProgress({
  progress,
  autoPlay = false,
  fullChargeMinutes = 62,
  accessibilityLabel = 'Charging',
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

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 700, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

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

  // The knob's number and the estimate are real text: re-render only when a whole number changes.
  const [percent, setPercent] = useState(Math.round(target * 100));
  useAnimatedReaction(
    () => Math.round(value.get() * 100),
    (now, before) => {
      if (now !== before) scheduleOnRN(setPercent, now);
    },
  );
  const minutes = Math.round((1 - percent / 100) * fullChargeMinutes);

  const ambient = reducedMotion ? 0 : 1;
  const label = useAnimatedStyle(() => ({
    transform: [
      { translateX: BAR_X + knobCenter(value.get()) },
      { scale: 1 + 0.08 * ambient * pulseFlare(time.get()) },
    ],
  }));

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable
      onPress={restart}
      style={styles.root}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ min: 0, max: 100, now: percent, text: `${percent}%, about ${minutes} minutes left` }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} ambient={ambient} />
      </View>

      <View pointerEvents="none" style={styles.odometer}>
        <Wheel place={100} value={value} />
        <Wheel place={10} value={value} />
        <Wheel place={1} value={value} />
      </View>

      <Animated.View pointerEvents="none" style={[styles.knobLabel, label]}>
        <Text style={styles.knobNumber}>{percent}</Text>
      </Animated.View>

      <Text style={styles.eta} numberOfLines={1}>
        {minutes > 0 ? `~${minutes}m` : 'Full'}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: {
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
  odometer: {
    position: 'absolute',
    right: WIDTH - (MINI_X + MINI_W) + 8.7,
    top: (MINI_H - DIGIT_H) / 2,
    flexDirection: 'row',
  },
  wheel: {
    width: DIGIT_W,
    height: DIGIT_H,
    overflow: 'hidden',
  },
  digit: {
    width: DIGIT_W,
    height: DIGIT_H,
    lineHeight: DIGIT_H,
    textAlign: 'center',
    color: GREEN,
    fontSize: 14,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  // A zero-width anchor at the bar's left end, moved to the knob's centre; the number sits to the
  // right of the knob's bolt.
  knobLabel: {
    position: 'absolute',
    left: 0,
    top: BAR_CY - 8,
    width: 0,
    height: 16,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'visible',
  },
  knobNumber: {
    width: 30,
    left: 5.6,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 12.4,
    lineHeight: 16,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  eta: {
    position: 'absolute',
    left: 270,
    top: BAR_CY - 9,
    color: GREEN,
    fontSize: 13.5,
    lineHeight: 18,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
});
