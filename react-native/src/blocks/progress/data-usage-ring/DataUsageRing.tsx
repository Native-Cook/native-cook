import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Paint,
  Path,
  RadialGradient,
  RoundedRect,
  SweepGradient,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
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
  /** Controlled usage, 0…1 of the plan. Leave undefined to show the reference's 36% (or the autoPlay loop). */
  progress?: number;
  /** Draws the ring from 0 to 36% by itself, holds, unwinds and starts over. */
  autoPlay?: boolean;
  title?: string;
  /** Size of the plan in GB; the used amount is derived from the progress. */
  totalGB?: number;
  renews?: string;
  editLabel?: string;
  onEditPress?: () => void;
};

const WIDTH = 280;
const HEIGHT = 366;
const RADIUS = 11;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 30;

const CX = 140;
const CY = 180.5;
const R_OUT = 116;
const R_IN = 69;
const R_MID = (R_OUT + R_IN) / 2;
const THICK = R_OUT - R_IN;
// The arc's corners are rounded by stroking a slightly smaller sector with round joins.
const CORNER = 3;
const TICKS = 12;
const TICK_R = 95.8;
// Conic gradients start at 3 o'clock; this turns the seam to just before 12 o'clock.
const SEAM = -96;

// Value shown in the reference, and the autoPlay loop built around it (seconds).
const REST = 0.36;
const START = 0.45;
const DRAW = 1.9;
const HOLD = 3.9;
const RESET = 0.75;
const LOOP = START + DRAW + HOLD + RESET;
const LANDED = START + DRAW;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = LANDED + 1.6;
const TICK_PERIOD = 3.4;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const RAD = Math.PI / 180;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Usage at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < LANDED) return REST * easeInOutCubic((t - START) / DRAW);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how fast the head of the arc is travelling at `t` seconds into the loop. */
function loopActivity(t: number) {
  'worklet';
  const u = (t - START) / DRAW;
  return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
}

function point(radius: number, degrees: number) {
  'worklet';
  const a = degrees * RAD;
  return `${(CX + radius * Math.cos(a)).toFixed(2)} ${(CY + radius * Math.sin(a)).toFixed(2)}`;
}

/** Ring sector between two angles (degrees clockwise from 3 o'clock), inset by `CORNER`. */
function sector(from: number, to: number) {
  'worklet';
  const inset = (CORNER / R_MID) / RAD;
  const a0 = from + inset;
  const a1 = Math.max(a0 + 0.01, to - inset);
  const large = a1 - a0 > 180 ? 1 : 0;
  const ro = R_OUT - CORNER;
  const ri = R_IN + CORNER;
  return (
    `M${point(ro, a0)}A${ro} ${ro} 0 ${large} 1 ${point(ro, a1)}` +
    `L${point(ri, a1)}A${ri} ${ri} 0 ${large} 0 ${point(ri, a0)}Z`
  );
}

/** Open arc along the middle of the ring. */
function arc(from: number, to: number) {
  'worklet';
  const sweep = Math.min(359.5, Math.max(0.01, to - from));
  return `M${point(R_MID, from)}A${R_MID} ${R_MID} 0 ${sweep > 180 ? 1 : 0} 1 ${point(R_MID, from + sweep)}`;
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** Seconds since the arc landed on its value; large when nothing happened. */
  landed: SharedValue<number>;
  /** 0 with reduced motion: no sheen, no ticking. */
  ambient: number;
};

function Tick({ index, value, time, activity, ambient }: { index: number } & Omit<Motion, 'landed'>) {
  const angle = index * (360 / TICKS);
  const cx = CX + TICK_R * Math.cos((angle - 90) * RAD);
  const cy = CY + TICK_R * Math.sin((angle - 90) * RAD);
  // A tick swells and turns green as the head of the arc reaches it…
  const reached = useDerivedValue(() => {
    const head = value.get() * 360;
    return activity.get() * clamp01(1 - Math.abs(angle - head - 7) / 11);
  });
  // …and the free ticks are swept by a slow pulse that keeps going round the dial.
  const pulse = useDerivedValue(() => {
    const sweep = ((time.get() % TICK_PERIOD) / 1.5) * 360;
    return ambient * (sweep < 360 ? clamp01(1 - Math.abs(angle - sweep) / 26) : 0);
  });
  const transform = useDerivedValue(() => [{ scale: 1 + 1.5 * reached.get() + 0.7 * pulse.get() }]);
  const opacity = useDerivedValue(() => 0.75 + 0.25 * pulse.get());
  return (
    <Group transform={transform} origin={vec(cx, cy)}>
      <RoundedRect x={cx - 1} y={cy - 1} width={2} height={2} r={0.4} color="#b7b7b7" opacity={opacity} />
      <RoundedRect x={cx - 1} y={cy - 1} width={2} height={2} r={0.4} color="#2fcf50" opacity={reached} />
    </Group>
  );
}

function Surface({ value, time, activity, landed, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const center = vec(CX, CY);

  const sweep = useDerivedValue(() => clamp01(value.get()) * 360);
  const shown = useDerivedValue(() => clamp01(value.get() / 0.012));
  const solid = useDerivedValue(() => sector(-90, -90 + sweep.get()));
  // Soft green haze before the start and past the end of the arc; it stretches ahead while the arc moves.
  const haze = useDerivedValue(() => arc(-90 - 30, -90 + sweep.get() + 38 + 26 * activity.get()));
  const hazeCore = useDerivedValue(() => arc(-90 - 12, -90 + sweep.get() + 15 + 20 * activity.get()));
  const hazeAlpha = useDerivedValue(() => 0.9 * shown.get());
  // Bright leading cap while the arc is being drawn.
  const cap = useDerivedValue(() => arc(-90 + sweep.get() - 5, -90 + sweep.get() - 0.5));
  const capAlpha = useDerivedValue(() => activity.get() * shown.get());
  // A glassy sheen that glides along the arc every few seconds.
  const sheen = useDerivedValue(() => {
    const phase = (time.get() % 4.2) / 1.3;
    const at = -90 + (sweep.get() + 30) * Math.min(phase, 1) - 15;
    return arc(Math.max(-90, at - 13), Math.min(-90 + sweep.get(), at + 13));
  });
  const sheenAlpha = useDerivedValue(() => {
    const phase = (time.get() % 4.2) / 1.3;
    return ambient * shown.get() * (phase < 1 ? 0.3 * Math.sin(Math.PI * phase) : 0);
  });
  // A ripple leaves the hub when the arc lands.
  const ripple = useDerivedValue(() => R_IN + 2 + 44 * (1 - Math.pow(1 - clamp01(landed.get() / 0.9), 3)));
  const rippleAlpha = useDerivedValue(() => {
    const u = landed.get() / 0.9;
    return u <= 0 || u >= 1 ? 0 : 0.55 * (1 - u);
  });

  const green = (
    <SweepGradient
      c={center}
      colors={['#35d655', '#58ee6c', '#42de60', '#42de60']}
      positions={[0.017, 0.2, 0.39, 1]}
      transform={[{ rotate: SEAM * RAD }]}
      origin={center}
    />
  );

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: x }, { translateY: y }]}>
        <RoundedRect x={4} y={10} width={WIDTH - 8} height={HEIGHT - 8} r={RADIUS} color="rgba(0,0,0,0.1)">
          <BlurMask blur={14} style="normal" />
        </RoundedRect>
        <RoundedRect x={0} y={0} width={WIDTH} height={HEIGHT} r={RADIUS} color="#ffffff" />
        <RoundedRect x={0.5} y={0.5} width={WIDTH - 1} height={HEIGHT - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1} color="#e7e7e7" />

        {/* Dial: a shallow grey groove, darker along its upper left rim. */}
        <Circle cx={CX} cy={CY} r={R_OUT}>
          <LinearGradient
            start={vec(CX - R_OUT * 0.7, CY - R_OUT * 0.7)}
            end={vec(CX + R_OUT * 0.7, CY + R_OUT * 0.7)}
            colors={['#e2e2e2', '#ededed', '#fbfbfb']}
          />
        </Circle>
        <Group clip={{ rect: { x: CX - R_OUT, y: CY - R_OUT, width: R_OUT * 2, height: R_OUT * 2 }, rx: R_OUT, ry: R_OUT }}>
          <Circle cx={CX + 3} cy={CY + 4} r={R_OUT + 3} style="stroke" strokeWidth={7} color="rgba(0,0,0,0.17)">
            <BlurMask blur={5} style="normal" />
          </Circle>

          {Array.from({ length: TICKS }, (_, i) => (
            <Tick key={i} index={i} value={value} time={time} activity={activity} ambient={ambient} />
          ))}

          <Group
            layer={
              <Paint opacity={hazeAlpha}>
                <Blur blur={11} />
              </Paint>
            }
          >
            <Path path={haze} style="stroke" strokeWidth={THICK + 10} color="rgba(84,226,104,0.3)" />
            <Path path={hazeCore} style="stroke" strokeWidth={THICK + 10} color="rgba(84,226,104,0.45)" />
          </Group>

          <Group opacity={shown}>
            <Path path={solid}>{green}</Path>
            <Path path={solid} style="stroke" strokeWidth={CORNER * 2} strokeJoin="round">
              {green}
            </Path>
            {/* Slightly deeper green towards both rims, so the band reads as a soft raised strip. */}
            <Path path={solid} style="stroke" strokeWidth={CORNER * 2} strokeJoin="round">
              <RadialGradient
                c={center}
                r={R_OUT}
                colors={['rgba(20,150,54,0.2)', 'rgba(20,150,54,0)', 'rgba(20,150,54,0)', 'rgba(20,150,54,0.2)']}
                positions={[R_IN / R_OUT, R_IN / R_OUT + 0.09, 0.93, 1]}
              />
            </Path>
            <Path path={solid}>
              <RadialGradient
                c={center}
                r={R_OUT}
                colors={['rgba(20,150,54,0.2)', 'rgba(20,150,54,0)', 'rgba(20,150,54,0)', 'rgba(20,150,54,0.2)']}
                positions={[R_IN / R_OUT, R_IN / R_OUT + 0.09, 0.93, 1]}
              />
            </Path>
          </Group>
          <Path path={sheen} style="stroke" strokeWidth={THICK - 10} color="#ffffff" opacity={sheenAlpha}>
            <BlurMask blur={7} style="normal" />
          </Path>
          <Path path={cap} style="stroke" strokeWidth={THICK - 8} color="rgba(214,255,220,0.85)" opacity={capAlpha}>
            <BlurMask blur={2.5} style="normal" />
          </Path>

          <Circle cx={CX} cy={CY} r={ripple} style="stroke" strokeWidth={2.5} color="#ffffff" opacity={rippleAlpha}>
            <BlurMask blur={1.5} style="normal" />
          </Circle>

          {/* Raised white hub with its shadow falling on the groove. */}
          <Circle cx={CX - 2} cy={CY + 6} r={R_IN} color="rgba(0,0,0,0.26)">
            <BlurMask blur={8} style="normal" />
          </Circle>
        </Group>
        <Circle cx={CX} cy={CY} r={R_IN} color="#ffffff" />
      </Group>
    </Canvas>
  );
}

function EditButton({ label, onPress }: { label: string; onPress?: () => void }) {
  const [pressed, setPressed] = useState(false);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      hitSlop={12}
    >
      <Animated.View
        style={{
          transform: [{ scale: pressed ? 0.97 : 1 }],
          opacity: pressed ? 0.6 : 1,
          transitionProperty: ['transform', 'opacity'],
          transitionDuration: 130,
          transitionTimingFunction: CSS_EASE_OUT,
        }}
      >
        <Text style={[styles.renews, styles.edit]}>{label}</Text>
      </Animated.View>
    </Pressable>
  );
}

/**
 * White "Summary" card with a thick green usage ring on a soft grey dial with tick dots, the
 * percentage and the used / total amount in its raised hub and the renewal line below. The arc
 * is drawn with a bright leading cap and a haze that stretches ahead of it, each tick swells as
 * the head reaches it, the hub figure pops and sends a ripple across the dial when the arc
 * lands, then a sheen glides along the arc and a slow pulse keeps going round the ticks.
 */
export function DataUsageRing({
  progress,
  autoPlay = false,
  title = 'Summary',
  totalGB = 10,
  renews = 'Renews 1st August',
  editLabel = 'Edit',
  onEditPress,
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
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 800, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
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
  const activity = useDerivedValue(() => {
    if (reducedMotion) return 0;
    if (!controlled) return loopActivity(clock.get());
    const u = (time.get() - movedAt.get()) / 0.7;
    return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
  });
  const landed = useDerivedValue(() => {
    if (reducedMotion) return 10;
    if (!controlled) return clock.get() < LOOP - RESET ? clock.get() - LANDED : 10;
    return time.get() - movedAt.get() - 0.6;
  });

  // The figures are real text: re-render only when the whole percent changes.
  const [percent, setPercent] = useState(Math.round(target * 100));
  useAnimatedReaction(
    () => Math.round(value.get() * 100),
    (now, before) => {
      if (now !== before) scheduleOnRN(setPercent, now);
    },
  );

  // The hub figure swells as the arc lands.
  const figure = useAnimatedStyle(() => {
    const u = clamp01(landed.get() / 0.5);
    const pop = u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u) * (1 - u);
    return { transform: [{ scale: 1 + 0.16 * pop }] };
  });

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const used = ((percent / 100) * totalGB).toFixed(1);

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} activity={activity} landed={landed} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${title}, ${used}GB of ${totalGB}GB used`}
        accessibilityValue={{ min: 0, max: 100, now: percent }}
      >
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Animated.View style={[styles.figure, figure]}>
          <Text style={styles.number}>{percent}</Text>
          <Text style={styles.percent}>%</Text>
        </Animated.View>
        <Text style={styles.amount} numberOfLines={1}>
          <Text style={styles.used}>{used}GB</Text>
          {' / '}
          {totalGB}GB
        </Text>
      </View>

      <View style={styles.footer}>
        <Text style={styles.renews}>{renews}</Text>
        <Text style={[styles.renews, styles.bullet]}>•</Text>
        <EditButton label={editLabel} onPress={onEditPress} />
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
  title: {
    position: 'absolute',
    left: 17,
    top: 20,
    color: '#232323',
    fontSize: 19,
    lineHeight: 25,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  figure: {
    position: 'absolute',
    left: 4,
    right: 0,
    top: CY - 13 - 29,
    height: 58,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'baseline',
  },
  number: {
    color: '#000000',
    fontSize: 48,
    lineHeight: 58,
    fontWeight: '700',
    letterSpacing: -1.2,
    fontVariant: ['tabular-nums'],
  },
  percent: {
    marginLeft: 1.5,
    color: '#000000',
    fontSize: 11.5,
    lineHeight: 14,
    fontWeight: '700',
  },
  amount: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: CY + 19.5 - 7,
    textAlign: 'center',
    color: '#9a9a9a',
    fontSize: 9.8,
    lineHeight: 14,
    fontWeight: '400',
    letterSpacing: -0.2,
  },
  used: {
    color: '#000000',
    fontWeight: '700',
  },
  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 333 - 9,
    height: 18,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  renews: {
    color: '#111111',
    fontSize: 11.6,
    lineHeight: 18,
    fontWeight: '400',
    letterSpacing: -0.2,
  },
  bullet: {
    marginHorizontal: 4,
    fontSize: 9,
  },
  edit: {
    fontWeight: '700',
    textDecorationLine: 'underline',
  },
});
