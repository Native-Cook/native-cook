import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  RoundedRect,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
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
  /** Controlled position on the track, 0…1 (1 is `maxPercent`). Leave undefined for the reference's 135%. */
  progress?: number;
  /** Climbs from 0 to 135% by itself, holds, drops back and starts over. */
  autoPlay?: boolean;
  /** Profit shown when the knob reaches the flame. */
  maxPercent?: number;
  status?: string;
  name?: string;
  price?: string;
  currency?: string;
  apy?: string;
  units?: number;
  /** Caption shown once profit passes 100%, and the one shown before that. */
  label?: string;
  pendingLabel?: string;
  onTogglePress?: () => void;
};

const WIDTH = 340;
const HEIGHT = 212;
const RADIUS = 18;
const TOP_H = 92;
// The canvas overflows the card by this much on every side to fit its plate and shadow.
const BLEED = 40;

const TRACK_X = 22;
const TRACK_Y = 179.6;
const TRACK_W = 285.5;
const TRACK_H = 6.9;
const KNOB_R = 7.2;
const BADGE_X = TRACK_X + TRACK_W;
const BADGE_R = 13.8;
const LOOP_X = 310.2;
const LOOP_Y = 146;

// Resting position in the reference (135% of a 180% track), and the autoPlay loop around it (seconds).
const REST = 0.75;
const START = 0.4;
const FILL = 2.6;
const HOLD = 3.6;
const RESET = 0.6;
const LOOP = START + FILL + HOLD + RESET;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = START + FILL + 1;

const BLUE = '#4f70e4';

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// Flame in a 20 × 24 box, its base at the bottom centre.
const FLAME = 'M10 0C11 5 17 8 17 15C17 20 14 24 10 24C6 24 3 20 3 15C3 12 4.5 10 6 8.5C6.5 11 7.5 12 8.5 12C8 8 8.5 3 10 0Z';
const FLAME_INNER = 'M10 11C11 14 13.5 15.5 13.5 19C13.5 22 12 24 10 24C8 24 6.5 22 6.5 19C6.5 16 9 14 10 11Z';
const INFINITY = 'M0 0C-3 -5.5 -11 -5.5 -11 0C-11 5.5 -3 5.5 0 0C3 -5.5 11 -5.5 11 0C11 5.5 3 5.5 0 0';
const CHEVRON = 'M-3.6 1.8L0 -1.8L3.6 1.8';

// Side view of a saloon in a 40 × 17 box.
const CAR_BODY =
  'M1 12C1 9.5 2.5 8.5 5 8L11 7.2C13 4 15.5 2.2 19 2L26 2C29 2 31 3.5 33.5 7L37 7.8C38.6 8.2 39.2 9.5 39.2 11L39.2 12.4C39.2 13 38.8 13.4 38.2 13.4L2 13.4C1.4 13.4 1 13 1 12.4Z';
const CAR_GLASS = 'M13.5 7C15 4.6 16.8 3.4 19.3 3.4L22 3.4L22 7ZM23.4 3.4L26 3.4C28.2 3.4 29.8 4.6 31.6 7L23.4 7Z';

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

// Runs a little past the target and springs back: the knob lands with an overshoot.
function easeOutBack(t: number) {
  'worklet';
  const c = 1.15;
  return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
}

/** Position on the track at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + FILL) return REST * easeOutBack((t - START) / FILL);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

function knobX(p: number) {
  'worklet';
  return KNOB_R + (TRACK_W - KNOB_R - BADGE_R) * clamp01(p);
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** When profit last crossed 100%, on the `time` axis. */
  ignitedAt: SharedValue<number>;
  /** Position on the track where profit is 100%. */
  threshold: number;
  /** 0 with reduced motion: no flicker, embers or burst. */
  ambient: number;
};

// Embers drift up from the flame: [phase 0…1, period s, sideways reach, rise, radius].
const EMBERS = [
  [0.0, 1.7, 5, 30, 1.3],
  [0.23, 2.1, -6, 36, 1.0],
  [0.47, 1.5, 3, 26, 1.5],
  [0.66, 1.9, -3, 33, 1.1],
  [0.85, 2.3, 7, 38, 0.9],
];

function Ember({ spec, time, heat }: { spec: number[]; time: SharedValue<number>; heat: SharedValue<number> }) {
  const [phase, period, reach, rise, radius] = spec;
  const life = useDerivedValue(() => (time.get() / period + phase) % 1);
  const transform = useDerivedValue(() => {
    const l = life.get();
    return [
      { translateX: reach * Math.sin(l * 5 + phase * 9) * l },
      { translateY: -BADGE_R * 0.4 - rise * l },
    ];
  });
  const opacity = useDerivedValue(() => {
    const l = life.get();
    return heat.get() * clamp01(l / 0.15) * (1 - l) * 0.9;
  });
  return (
    <Group transform={transform} opacity={opacity}>
      <Circle cx={0} cy={0} r={radius} color="#ffc24a" />
    </Group>
  );
}

const SPARKS = [-2.6, -2.05, -1.5, -0.95, -0.4, 0.3, 2.85, 3.5];

function Spark({ angle, burst }: { angle: number; burst: SharedValue<number> }) {
  const transform = useDerivedValue(() => {
    const d = BADGE_R + 4 + 24 * easeOutCubic(burst.get() / 0.6);
    return [{ translateX: d * Math.cos(angle) }, { translateY: d * Math.sin(angle) }];
  });
  const opacity = useDerivedValue(() => (burst.get() < 0.6 ? 1 - burst.get() / 0.6 : 0));
  return (
    <Group transform={transform} opacity={opacity}>
      <Circle cx={0} cy={0} r={1.6} color="#ffe27a" />
    </Group>
  );
}

function Surface({ value, time, ignitedAt, threshold, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const tx = x + TRACK_X;
  const ty = y + TRACK_Y;
  const bx = x + BADGE_X;

  const clip = useMemo(() => rrect(rect(x, y, WIDTH, HEIGHT), RADIUS, RADIUS), [x, y]);

  const knob = useDerivedValue(() => knobX(value.get()));
  const fillWidth = useDerivedValue(() => knob.get());
  const heatX = useDerivedValue(() => tx + knob.get());
  const heatWidth = useDerivedValue(() => TRACK_W - knob.get());
  const knobTransform = useDerivedValue(() => [{ translateX: tx + knob.get() }, { translateY: ty }]);
  const dot = useDerivedValue(() => 2 + ambient * 0.35 * Math.sin(time.get() * 3.1));

  // 0…1 once profit is past 100%: the flame burns hotter and the caption lights up.
  const lit = useDerivedValue(() => clamp01((value.get() - threshold) / 0.03));
  const heat = useDerivedValue(() => ambient * (0.3 + 0.7 * lit.get()));
  // Seconds since the ignition; large when there is none to show.
  const burst = useDerivedValue(() => (ambient ? time.get() - ignitedAt.get() : 10));

  const haloScale = useDerivedValue(() => [{ scale: 1 + ambient * 0.05 * Math.sin(time.get() * 2.2) }]);
  const haloAlpha = useDerivedValue(() => 0.6 + 0.4 * lit.get());
  const ring = useDerivedValue(() => BADGE_R + 34 * easeOutCubic(burst.get() / 0.75));
  const ringAlpha = useDerivedValue(() => (burst.get() < 0.75 ? 0.7 * (1 - burst.get() / 0.75) : 0));
  const flash = useDerivedValue(() => (burst.get() < 1 ? Math.exp(-6 * burst.get()) : 0));
  // The flame leans and stretches like a real one; it is anchored at its base.
  const flicker = useDerivedValue(() => {
    const t = time.get();
    const s = 0.82 + 0.18 * lit.get();
    return [
      { rotate: ambient * 0.07 * Math.sin(t * 7.3) },
      { scaleX: s * (1 - ambient * 0.04 * Math.sin(t * 13.1)) },
      { scaleY: s * (1 + ambient * (0.07 * Math.sin(t * 13.1) + 0.04 * Math.sin(t * 23.7 + 1))) },
    ];
  });
  const innerFlicker = useDerivedValue(() => [{ scaleY: 1 + ambient * 0.12 * Math.sin(time.get() * 17.3 + 2) }]);
  const loopEnd = useDerivedValue(() => clamp01((value.get() - threshold) / 0.07));

  return (
    <Canvas style={styles.canvas}>
      {/* The pale plate the card sits on in the reference, then its shadow. */}
      <RoundedRect x={x - 13} y={y - 13} width={WIDTH + 26} height={HEIGHT + 60} r={30}>
        <LinearGradient
          start={vec(x, y - 13)}
          end={vec(x, y + HEIGHT + 47)}
          colors={['rgba(0,0,0,0.075)', 'rgba(0,0,0,0.05)', 'rgba(0,0,0,0)']}
          positions={[0, 0.6, 1]}
        />
      </RoundedRect>
      <RoundedRect x={x + 8} y={y + 16} width={WIDTH - 16} height={HEIGHT - 8} r={RADIUS} color="rgba(20,20,45,0.3)">
        <BlurMask blur={14} style="normal" />
      </RoundedRect>

      <Group clip={clip}>
        <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} color="#17181d" />
        <Circle cx={x} cy={y + 110} r={150}>
          <RadialGradient c={vec(x, y + 110)} r={150} colors={['rgba(46,58,130,0.5)', 'rgba(46,58,130,0)']} />
        </Circle>
        <Circle cx={x + 170} cy={y + HEIGHT + 40} r={190}>
          <RadialGradient c={vec(x + 170, y + HEIGHT + 40)} r={190} colors={['rgba(66,38,120,0.5)', 'rgba(66,38,120,0)']} />
        </Circle>
        <Circle cx={bx} cy={ty - 10} r={130}>
          <RadialGradient c={vec(bx, ty - 10)} r={130} colors={['rgba(130,80,24,0.42)', 'rgba(130,80,24,0)']} />
        </Circle>
      </Group>
      <RoundedRect x={x} y={y} width={WIDTH} height={TOP_H} r={RADIUS} color="#ffffff" />

      {/* Thumbnail with a drawn car. */}
      <RoundedRect x={x + 18.2} y={y + 20.7} width={51} height={51} r={8} color="#e5e6f4" />
      <Group transform={[{ translateX: x + 18.2 + 5.5 }, { translateY: y + 20.7 + 19 }]}>
        <RoundedRect x={3} y={14.6} width={34} height={3} r={1.5} color="rgba(30,40,80,0.18)" />
        <Path path={CAR_BODY} color="#263352" />
        <Path path={CAR_GLASS} color="#aebbd6" />
        <Circle cx={9.5} cy={13.4} r={3.4} color="#151a26" />
        <Circle cx={30.5} cy={13.4} r={3.4} color="#151a26" />
        <Circle cx={9.5} cy={13.4} r={1.4} color="#c9cfdb" />
        <Circle cx={30.5} cy={13.4} r={1.4} color="#c9cfdb" />
      </Group>

      {/* Track: what is still ahead warms up towards the flame, what is behind is blue. */}
      <RoundedRect x={heatX} y={ty - TRACK_H / 2} width={heatWidth} height={TRACK_H} r={TRACK_H / 2}>
        <LinearGradient
          start={vec(tx, ty)}
          end={vec(tx + TRACK_W, ty)}
          colors={['rgba(215,118,27,0.22)', 'rgba(215,118,27,0.36)', '#d7761b', '#dcab44', '#feef91']}
          positions={[0, 0.62, 0.77, 0.88, 1]}
        />
      </RoundedRect>
      <RoundedRect x={tx} y={ty - TRACK_H / 2} width={fillWidth} height={TRACK_H} r={TRACK_H / 2}>
        <LinearGradient
          start={vec(tx, ty)}
          end={vec(tx + TRACK_W, ty)}
          colors={['#18d5da', '#4f8ae2', BLUE, '#4667d9']}
          positions={[0, 0.19, 0.38, 1]}
        />
      </RoundedRect>

      {/* Flame badge with its two halos; the ring and sparks are the ignition at 100%. */}
      <Group transform={[{ translateX: bx }, { translateY: ty }]}>
        <Group transform={haloScale} opacity={haloAlpha}>
          <Circle cx={0} cy={0} r={42} color="rgba(255,214,120,0.07)" />
          <Circle cx={0} cy={0} r={21} color="rgba(255,214,120,0.14)" />
        </Group>
        <Circle cx={0} cy={0} r={ring} style="stroke" strokeWidth={1.5} color="#ffd978" opacity={ringAlpha} />
        {SPARKS.map((angle) => (
          <Spark key={angle} angle={angle} burst={burst} />
        ))}
        <Circle cx={0} cy={0} r={BADGE_R}>
          <RadialGradient c={vec(0, -2)} r={BADGE_R} colors={['#fff6ad', '#f8e182', '#f0cf63']} positions={[0, 0.6, 1]} />
        </Circle>
        {EMBERS.map((spec) => (
          <Ember key={spec[0]} spec={spec} time={time} heat={heat} />
        ))}
        <Group transform={[{ translateY: 8.2 }]}>
          <Group transform={flicker}>
            <Group transform={[{ scale: 0.7 }, { translateX: -10 }, { translateY: -24 }]}>
              <Path path={FLAME}>
                <LinearGradient start={vec(10, 0)} end={vec(10, 24)} colors={['#f2401d', '#fb6a1c', '#ff9a26']} />
              </Path>
              <Group transform={innerFlicker} origin={vec(10, 24)}>
                <Path path={FLAME_INNER}>
                  <LinearGradient start={vec(10, 11)} end={vec(10, 24)} colors={['#ffb52e', '#ffe45e', '#fff7c4']} />
                </Path>
              </Group>
            </Group>
          </Group>
        </Group>
        <Circle cx={0} cy={0} r={BADGE_R} color="#fffbe0" opacity={flash} />
      </Group>

      <Group transform={knobTransform}>
        <Circle cx={0} cy={1} r={KNOB_R + 1} color="rgba(0,0,0,0.35)">
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Circle cx={0} cy={0} r={KNOB_R} color={BLUE} />
        <Circle cx={0} cy={0} r={dot} color="#ffffff" />
      </Group>

      {/* The infinity sign draws itself as profit passes 100%. */}
      <Group transform={[{ translateX: x + LOOP_X }, { translateY: y + LOOP_Y }]}>
        <Path path={INFINITY} style="stroke" strokeWidth={2.3} strokeCap="round" color="rgba(255,255,255,0.16)" />
        <Path path={INFINITY} style="stroke" strokeWidth={2.3} strokeCap="round" color="#ffffff" start={0} end={loopEnd} />
      </Group>
    </Canvas>
  );
}

function ToggleButton({ onPress }: { onPress?: () => void }) {
  const [pressed, setPressed] = useState(false);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Collapse"
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      hitSlop={10}
      style={styles.toggleSlot}
    >
      <Animated.View
        style={[
          styles.toggle,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <Canvas style={styles.toggleCanvas}>
          <Group transform={[{ translateX: 11.4 }, { translateY: 11.4 }]}>
            <Path path={CHEVRON} style="stroke" strokeWidth={1.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
          </Group>
        </Canvas>
      </Animated.View>
    </Pressable>
  );
}

/**
 * Two-layer asset card: a white row describing the asset and a dark panel with its profit
 * progress on a blue track that ends in a flame. The flame flickers and sheds embers, the knob
 * lands with a springy overshoot, and crossing 100% ignites the badge (flash, ring, sparks) while
 * the caption lights up and the infinity sign draws itself.
 */
export function ProfitTrackProgress({
  progress,
  autoPlay = false,
  maxPercent = 180,
  status = 'Active',
  name = 'Vela GT 2025',
  price = '270,675',
  currency = 'USDT',
  apy = '178.12%',
  units = 1,
  label = 'Explosive profit',
  pendingLabel = 'Building profit',
  onTogglePress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;
  const threshold = 100 / maxPercent;

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(target);
  const ignitedAt = useSharedValue(-10);

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

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    // Crossing 100% on the way up sets off the ignition.
    const now = value.get();
    if (before.get() < threshold && now >= threshold) ignitedAt.set(time.get());
    before.set(now);
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

  // The number is real text: re-render only when the whole percent changes.
  const [percent, setPercent] = useState(Math.round(target * maxPercent));
  useAnimatedReaction(
    () => Math.round(value.get() * maxPercent),
    (now, old) => {
      if (now !== old) scheduleOnRN(setPercent, now);
    },
  );

  // The two captions cross-fade around 100%.
  const litStyle = useAnimatedStyle(() => ({ opacity: clamp01((value.get() - threshold) / 0.03) }));
  const pendingStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01((value.get() - threshold) / 0.03) }));

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} ignitedAt={ignitedAt} threshold={threshold} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View style={StyleSheet.absoluteFill} pointerEvents="none" accessible accessibilityLabel={`${name}, ${status}, ${price} ${currency}, estimated APY ${apy}, ${units} unit`}>
        <View style={styles.status}>
          <Text style={styles.statusLabel}>{status}</Text>
        </View>
        <Text style={styles.name} numberOfLines={1}>
          {name}
        </Text>
        <Text style={styles.price} numberOfLines={1}>
          {price}
          <Text style={styles.currency}> {currency}</Text>
        </Text>
        <Text style={styles.apy} numberOfLines={1}>
          <Text style={styles.apyEst}>est. </Text>
          <Text style={styles.apyCaption}>APY{'  '}</Text>
          {apy}
        </Text>
        <View style={styles.units}>
          <Text style={styles.unitsLabel}>
            <Text style={styles.unitsCount}>{units}</Text> UNIT
          </Text>
        </View>
      </View>
      <ToggleButton onPress={onTogglePress} />

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel="Profit progress"
        accessibilityValue={{ text: `${percent}%, ${percent >= 100 ? label : pendingLabel}` }}
      >
        <Text style={styles.heading} numberOfLines={1}>
          PROFIT PROGRESS
        </Text>
        <Text style={styles.percent}>{percent}%</Text>
        <Animated.Text style={[styles.caption, pendingStyle]} numberOfLines={1}>
          {pendingLabel}
        </Animated.Text>
        <Animated.Text style={[styles.caption, litStyle]} numberOfLines={1}>
          {label}
        </Animated.Text>
      </View>
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
  status: {
    position: 'absolute',
    left: 79.3,
    top: 19,
    height: 14,
    paddingHorizontal: 5,
    borderRadius: 3.5,
    backgroundColor: '#e8f6e6',
    justifyContent: 'center',
  },
  statusLabel: {
    color: '#5fa468',
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '500',
  },
  name: {
    position: 'absolute',
    left: 79.3,
    top: 36.3,
    color: '#12141a',
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '700',
  },
  price: {
    position: 'absolute',
    left: 79.3,
    top: 57,
    color: BLUE,
    fontSize: 13.5,
    lineHeight: 18,
    fontWeight: '700',
    fontStyle: 'italic',
    fontVariant: ['tabular-nums'],
  },
  currency: {
    color: '#5b7be6',
    fontSize: 9.5,
    fontWeight: '400',
  },
  apy: {
    position: 'absolute',
    right: 48,
    top: 18,
    color: BLUE,
    fontSize: 13.5,
    lineHeight: 18,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  apyEst: {
    color: '#7d828a',
    fontSize: 9.5,
    fontWeight: '400',
    fontStyle: 'italic',
  },
  apyCaption: {
    color: '#9aa0a6',
    fontSize: 9.5,
    fontWeight: '400',
  },
  toggleSlot: {
    position: 'absolute',
    left: 298.3,
    top: 18.6,
  },
  toggle: {
    width: 22.8,
    height: 22.8,
    borderRadius: 11.4,
    backgroundColor: BLUE,
  },
  toggleCanvas: {
    width: 22.8,
    height: 22.8,
  },
  units: {
    position: 'absolute',
    right: 18.8,
    top: 59.2,
    height: 14.3,
    paddingHorizontal: 4,
    borderRadius: 3.5,
    borderWidth: 1,
    borderColor: '#dcdee2',
    justifyContent: 'center',
  },
  unitsLabel: {
    color: '#9aa0a6',
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '400',
  },
  unitsCount: {
    color: '#1d1f24',
    fontWeight: '700',
  },
  heading: {
    position: 'absolute',
    left: 0,
    width: WIDTH,
    top: 112.2,
    // The tracking adds a gap after the last letter; this keeps the word centred.
    paddingLeft: 3.2,
    textAlign: 'center',
    color: '#8b8e96',
    fontSize: 9.5,
    lineHeight: 12,
    fontWeight: '400',
    letterSpacing: 3.2,
  },
  percent: {
    position: 'absolute',
    left: 18.7,
    top: 133.7,
    color: '#eef0f6',
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  caption: {
    position: 'absolute',
    right: 50.4,
    top: 136.7,
    color: '#9c9ea5',
    fontSize: 11.5,
    lineHeight: 18,
    fontWeight: '500',
  },
});
