import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RadialGradient, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Stop = { code: string; city: string; time: string };

type Props = {
  /** Controlled share of the flight flown, 0…1. */
  progress?: number;
  /** Flies from the gate to the point shown in the reference, holds, and starts over. */
  autoPlay?: boolean;
  /** Colour of the bar, the arrow and the dust; the note under the ETA takes the other colour. */
  tint?: 'lime' | 'orange';
  origin?: Stop;
  destination?: Stop;
  eta?: string;
  zone?: string;
  note?: string;
  /** Length of the flight: the bar's label counts the minutes left down from it. */
  totalMinutes?: number;
  onSwapPress?: () => void;
};

const WIDTH = 356;
const HEIGHT = 160;
const RADIUS = 22;
// The canvas overflows the card by this much on every side so the soft shadow is not clipped.
const BLEED = 24;

const BAR_X = 14.8;
const BAR_Y = 109.1;
const BAR_W = 315.3;
const BAR_H = 36.5;
const BAR_CY = BAR_Y + BAR_H / 2;
// The plane sits this far inside the filled part's leading edge.
const PLANE_INSET = 19.4;

const BOX_X = 220.5;
const BOX_Y = 15.3;
const BOX_W = 120.7;
const BOX_H = 78.2;
const SWAP_X = 319.2;
const SWAP_Y = 36.8;
const SWAP_R = 10.1;

const ARROW_FROM = 94.3;
const ARROW_TO = 122;
const ARROW_Y = 44;

// The reference shows 54% flown; the autoPlay loop built around it (seconds).
const REST = 0.54;
const LOW = 0.04;
const START = 0.5;
const FILL = 3;
const HOLD = 3.4;
const RESET = 0.8;
const LOOP = START + FILL + HOLD + RESET;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = START + FILL + 1.6;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const PALETTES = {
  lime: {
    glow: '#b4f01f',
    fill: ['#ebfb7c', '#c8ef38', '#84bd16'],
    rim: '#c2f41e',
    arrow: '#b9f52c',
    note: '#ff7d1a',
    haze: '184,242,39',
  },
  orange: {
    glow: '#ff8a1f',
    fill: ['#ffd3a8', '#ffb45e', '#ff6a05'],
    rim: '#ffc686',
    arrow: '#ffb90a',
    note: '#c9ef4b',
    haze: '255,150,40',
  },
};

// A small plane seen from the side, nose to the right, centred on its body.
const PLANE_BODY =
  'M-7.2 0.4C-7.2 -1.4 -5.8 -2.2 -4 -2.2L3.2 -2.2C5.9 -2.2 7.8 -1 7.8 0.2C7.8 1.4 5.9 2.3 3.2 2.3L-4.8 2.3C-6.2 2.3 -7.2 1.5 -7.2 0.4Z';
const PLANE_TAIL = 'M-6.9 -1.2L-8 -5.6L-5.3 -5.6L-2.6 -1.6Z';
const PLANE_WING = 'M-1.6 0.4L2.6 0.4L-1.2 5.9L-3.9 5.9Z';
const ARROW =
  `M${ARROW_FROM} ${ARROW_Y}L${ARROW_TO} ${ARROW_Y}` + `M${ARROW_TO - 8.6} ${ARROW_Y - 8.6}L${ARROW_TO} ${ARROW_Y}L${ARROW_TO - 8.6} ${ARROW_Y + 8.6}`;
// A slanted band of light, centred on the bar's middle line.
const SWEEP_BAND = 'M-6 -26L22 -26L6 26L-22 26Z';
const SWAP_CHEVRONS = 'M1.4 -5.6L-1.6 -3.3L1.4 -1M-1.4 1L1.6 3.3L-1.4 5.6';

/**
 * Dust on the card, densest along the top edge: three sets of dots that twinkle out of phase.
 * A fixed-seed generator keeps the pattern identical on every platform.
 */
function dust() {
  let seed = 20240611;
  const next = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
  };
  const sets = ['', '', ''];
  for (let i = 0; i < 420; i++) {
    const x = 4 + next() * (WIDTH - 8);
    const fall = next();
    const y = 2 + fall * fall * fall * 112;
    const r = 0.18 + next() * 0.26;
    sets[i % 3] += `M${(x - r).toFixed(2)} ${y.toFixed(2)}a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(r * 2).toFixed(2)} 0a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(-r * 2).toFixed(2)} 0Z`;
  }
  return sets;
}

const DUST = dust();

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Share of the flight flown at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return LOW;
  if (t < START + FILL) return LOW + (REST - LOW) * easeInOutCubic((t - START) / FILL);
  if (t < START + FILL + HOLD) return REST;
  return REST - (REST - LOW) * easeInOutCubic((t - START - FILL - HOLD) / RESET);
}

function fillWidth(p: number) {
  'worklet';
  return Math.max(BAR_H, BAR_W * clamp01(p));
}

function remainingLabel(p: number, totalMinutes: number) {
  const left = Math.round(totalMinutes * (1 - Math.min(1, Math.max(0, p))));
  return `-${Math.floor(left / 60)}H ${String(left % 60).padStart(2, '0')}M`;
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** 0…1, smoothed: is the plane flying forwards. */
  moving: SharedValue<number>;
  /** When the plane last levelled off, on the `time` axis. */
  arrivedAt: SharedValue<number>;
  /** 0 with reduced motion: no twinkle, breathing, streaks or sweep. */
  ambient: number;
};

function Streak({ index, time, moving, value }: { index: number; time: SharedValue<number>; moving: SharedValue<number>; value: SharedValue<number> }) {
  // Slipstream: short lines that fall behind the plane while it flies.
  const along = useDerivedValue(() => (time.get() * 70 + index * 23) % 62);
  const x = useDerivedValue(() => BLEED + BAR_X + fillWidth(value.get()) - PLANE_INSET - 16 - along.get());
  const opacity = useDerivedValue(() => {
    const a = along.get();
    return moving.get() * 0.7 * clamp01(a / 8) * clamp01((62 - a) / 30);
  });
  const y = BLEED + BAR_CY + (index - 1) * 6.5 + (index === 1 ? 1.5 : 0);
  return <RoundedRect x={x} y={y - 0.6} width={9 + index * 3} height={1.2} r={0.6} color="#ffffff" opacity={opacity} />;
}

function Surface({ value, time, moving, arrivedAt, ambient, tint }: Motion & { tint: 'lime' | 'orange' }) {
  const palette = PALETTES[tint];
  const x = BLEED;
  const y = BLEED;
  const bx = x + BAR_X;
  const by = y + BAR_Y;
  const cy = y + BAR_CY;
  const card = { rect: { x, y, width: WIDTH, height: HEIGHT }, rx: RADIUS, ry: RADIUS };

  const width = useDerivedValue(() => fillWidth(value.get()));
  const fillClip = useDerivedValue(() => ({
    rect: { x: bx, y: by, width: width.get(), height: BAR_H },
    rx: BAR_H / 2,
    ry: BAR_H / 2,
  }));
  const gradientEnd = useDerivedValue(() => vec(bx + width.get(), by));
  // The glow under the bar breathes.
  const glow = useDerivedValue(() => 0.62 + ambient * 0.14 * Math.sin(time.get() * 1.9));

  // The plane noses up and lifts off its ground line while it flies, and bobs a little when level.
  const plane = useDerivedValue(() => {
    const m = moving.get();
    const bob = ambient * (1 - m) * 0.7 * Math.sin(time.get() * 2.3);
    return [
      { translateX: bx + width.get() - PLANE_INSET },
      { translateY: cy - 2.2 - 2.4 * m + bob },
      { rotate: -(0.27 + 0.2 * m) },
    ];
  });
  const ground = useDerivedValue(() => 8.6 * (1 - 0.45 * moving.get()));
  const groundX = useDerivedValue(() => bx + width.get() - PLANE_INSET - ground.get() / 2);
  const groundOpacity = useDerivedValue(() => 1 - 0.5 * moving.get());

  // A band of light crosses the filled part when the plane levels off.
  const sweepAt = useDerivedValue(() => (time.get() - arrivedAt.get() - 0.15) / 1.1);
  const sweep = useDerivedValue(() => [{ translateX: bx - 30 + (width.get() + 60) * clamp01(sweepAt.get()) }, { translateY: cy }]);
  const sweepOpacity = useDerivedValue(() => {
    const s = sweepAt.get();
    return s <= 0 || s >= 1 ? 0 : ambient * 0.5 * Math.sin(Math.PI * s);
  });

  const twinkle0 = useDerivedValue(() => 0.5 + ambient * 0.3 * Math.sin(time.get() * 1.3));
  const twinkle1 = useDerivedValue(() => 0.5 + ambient * 0.3 * Math.sin(time.get() * 1.7 + 2.1));
  const twinkle2 = useDerivedValue(() => 0.5 + ambient * 0.3 * Math.sin(time.get() * 1.1 + 4.2));

  // A spark runs down the arrow every couple of seconds.
  const sparkAt = useDerivedValue(() => (time.get() % 2.6) / 0.9);
  const sparkX = useDerivedValue(() => x + ARROW_FROM + (ARROW_TO - ARROW_FROM) * clamp01(sparkAt.get()));
  const sparkOpacity = useDerivedValue(() => {
    const s = sparkAt.get();
    return s >= 1 ? 0 : ambient * Math.sin(Math.PI * s);
  });

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 8} y={y + 12} width={WIDTH - 16} height={HEIGHT - 8} r={RADIUS} color="rgba(0,0,0,0.55)">
        <BlurMask blur={12} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} color="#080808" />

      <Group clip={card}>
        {/* Tinted haze behind the route, and the dust floating in it. */}
        <Circle cx={x + 150} cy={y + 64} r={170}>
          <RadialGradient
            c={vec(x + 150, y + 64)}
            r={170}
            colors={[`rgba(${palette.haze},0.13)`, `rgba(${palette.haze},0.05)`, `rgba(${palette.haze},0)`]}
            positions={[0, 0.5, 1]}
          />
        </Circle>
        <Group transform={[{ translateX: x }, { translateY: y }]}>
          <Path path={DUST[0]} color={palette.glow} opacity={twinkle0} />
          <Path path={DUST[1]} color={palette.glow} opacity={twinkle1} />
          <Path path={DUST[2]} color={palette.glow} opacity={twinkle2} />
        </Group>
        {/* Bevel: a soft light rim just inside the card's edge. */}
        <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} style="stroke" strokeWidth={5} color="rgba(255,255,255,0.13)">
          <BlurMask blur={5} style="normal" />
        </RoundedRect>
        <RoundedRect x={x + 0.5} y={y + 0.5} width={WIDTH - 1} height={HEIGHT - 1} r={RADIUS} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(x, y)}
            end={vec(x, y + HEIGHT * 0.5)}
            colors={[`rgba(${palette.haze},0.4)`, `rgba(${palette.haze},0)`]}
          />
        </RoundedRect>

        {/* Arrow between the airports. */}
        <Group transform={[{ translateX: x }, { translateY: y }]}>
          <Path path={ARROW} style="stroke" strokeWidth={7} strokeCap="round" strokeJoin="round" color={palette.arrow} opacity={0.5}>
            <BlurMask blur={7} style="normal" />
          </Path>
          <Path path={ARROW} style="stroke" strokeWidth={3.1} strokeCap="round" strokeJoin="round" color={palette.arrow} />
        </Group>
        <Circle cx={sparkX} cy={y + ARROW_Y} r={3.4} color="#ffffff" opacity={sparkOpacity}>
          <BlurMask blur={2.5} style="normal" />
        </Circle>

        {/* ETA box. */}
        <RoundedRect x={x + BOX_X} y={y + BOX_Y} width={BOX_W} height={BOX_H} r={9.5} color="#040404" />
        <RoundedRect x={x + BOX_X} y={y + BOX_Y} width={BOX_W} height={BOX_H} r={9.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.14)" />

        {/* Progress bar: track, glow, glossy fill. */}
        <RoundedRect x={bx} y={by} width={BAR_W} height={BAR_H} r={BAR_H / 2} color="#1b1b1b" />
        <RoundedRect x={bx} y={by} width={width} height={BAR_H} r={BAR_H / 2} color={palette.glow} opacity={glow}>
          <BlurMask blur={9} style="normal" />
        </RoundedRect>
        <Group clip={fillClip}>
          <RoundedRect x={bx} y={by} width={width} height={BAR_H} r={BAR_H / 2}>
            <LinearGradient start={vec(bx, by)} end={gradientEnd} colors={palette.fill} positions={[0, 0.5, 1]} />
          </RoundedRect>
          {/* Gloss: a bright rim that is strongest along the top. */}
          <RoundedRect x={bx} y={by} width={width} height={BAR_H} r={BAR_H / 2} style="stroke" strokeWidth={3.5} color={palette.rim} opacity={0.9}>
            <BlurMask blur={2.2} style="normal" />
          </RoundedRect>
          <RoundedRect x={bx + 6} y={by + BAR_H - 9} width={BAR_W} height={12} r={6} color="rgba(255,255,255,0.16)">
            <BlurMask blur={5} style="normal" />
          </RoundedRect>
          <Streak index={0} time={time} moving={moving} value={value} />
          <Streak index={1} time={time} moving={moving} value={value} />
          <Streak index={2} time={time} moving={moving} value={value} />
          <Group transform={sweep} opacity={sweepOpacity}>
            <Path path={SWEEP_BAND} color="#ffffff">
              <BlurMask blur={8} style="normal" />
            </Path>
          </Group>
        </Group>

        <RoundedRect x={groundX} y={cy + 5.1} width={ground} height={1.1} r={0.55} color="#ffffff" opacity={groundOpacity} />
        <Group transform={plane}>
          <Path path={PLANE_TAIL} color="#ffffff" />
          <Path path={PLANE_WING} color="#ffffff" />
          <Path path={PLANE_BODY} color="#ffffff" />
        </Group>
      </Group>
    </Canvas>
  );
}

/**
 * Dark flight card: both airports with an arrow between them, an ETA box, and a glossy pill bar a
 * small plane rides. The plane noses up and leaves a slipstream while the bar grows, a band of
 * light crosses the bar when it levels off, the label counts the time left down, the dust on the
 * card twinkles and a spark runs down the arrow.
 */
export function FlightProgress({
  progress,
  autoPlay = false,
  tint = 'lime',
  origin = { code: 'NPT', city: 'Northport', time: 'MON, 6:14 PM' },
  destination = { code: 'BVW', city: 'Bayview', time: 'MON, 7:14 AM' },
  eta = 'ETA 2:15 PM',
  zone = 'Bayview Time',
  note = 'DINNER IN 2:34H',
  totalMinutes = 915,
  onSwapPress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;
  const palette = PALETTES[tint];

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(target);
  const moving = useSharedValue(0);
  const airborne = useSharedValue(false);
  const arrivedAt = useSharedValue(-10);

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
    const step = now - before.get();
    before.set(now);
    const flying = step > 0.00004;
    moving.set(moving.get() + ((flying ? 1 : 0) - moving.get()) * Math.min(1, dt * 9));
    // Levelling off after a climb sets off the sweep of light.
    if (flying && moving.get() > 0.5) airborne.set(true);
    else if (!flying && airborne.get() && step >= 0) {
      airborne.set(false);
      arrivedAt.set(time.get());
    } else if (step < 0) airborne.set(false);
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

  const [minutesFlown, setMinutesFlown] = useState(target);
  useAnimatedReaction(
    () => Math.round(totalMinutes * (1 - clamp01(value.get()))),
    (now, old) => {
      if (now !== old) scheduleOnRN(setMinutesFlown, 1 - now / totalMinutes);
    },
  );
  const remaining = remainingLabel(minutesFlown, totalMinutes);

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable
      onPress={restart}
      style={styles.card}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={`${origin.code} ${origin.city} to ${destination.code} ${destination.city}, ${eta}`}
      accessibilityValue={{ min: 0, max: 100, now: Math.round(minutesFlown * 100), text: remaining }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} moving={moving} arrivedAt={arrivedAt} ambient={reducedMotion ? 0 : 1} tint={tint} />
      </View>

      {[origin, destination].map((stop, i) => (
        <View key={i} pointerEvents="none" style={[styles.stop, { left: i === 0 ? 15.3 : 134.5 }]}>
          <Text style={styles.code} numberOfLines={1}>
            {stop.code}
          </Text>
          <Text style={styles.city} numberOfLines={1}>
            {stop.city}
          </Text>
          <Text style={styles.time} numberOfLines={1}>
            {stop.time}
          </Text>
        </View>
      ))}

      <Text style={styles.eta} numberOfLines={1}>
        {eta}
      </Text>
      <Text style={styles.zone} numberOfLines={1}>
        {zone}
      </Text>
      <Text style={[styles.note, { color: palette.note, textShadowColor: palette.note }]} numberOfLines={1}>
        {note}
      </Text>
      <Text style={styles.remaining} numberOfLines={1}>
        {remaining}
      </Text>

      <Pressable
        onPress={onSwapPress}
        onPressIn={() => {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }}
        hitSlop={10}
        accessibilityRole="button"
        accessibilityLabel="Switch time zone"
        style={styles.swapSlot}
      >
        {({ pressed }) => (
          <Animated.View
            style={[
              styles.swap,
              {
                transform: [{ scale: pressed ? 0.9 : 1 }],
                transitionProperty: 'transform',
                transitionDuration: 130,
                transitionTimingFunction: CSS_EASE_OUT,
              },
            ]}
          >
            <Canvas style={styles.swapCanvas}>
              <Group transform={[{ translateX: SWAP_R }, { translateY: SWAP_R }]}>
                <Path path={SWAP_CHEVRONS} style="stroke" strokeWidth={1.15} strokeCap="round" strokeJoin="round" color="#f2f2f2" />
              </Group>
            </Canvas>
          </Animated.View>
        )}
      </Pressable>
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
  stop: {
    position: 'absolute',
    top: 0,
    width: 96,
    height: 80,
  },
  code: {
    position: 'absolute',
    left: -1,
    top: 16.5,
    width: 96,
    color: '#ffffff',
    fontSize: 24.5,
    lineHeight: 29,
    fontWeight: '900',
    letterSpacing: 0.6,
  },
  city: {
    position: 'absolute',
    left: 0,
    top: 44.9,
    width: 96,
    color: '#f1f1f1',
    fontSize: 11.2,
    lineHeight: 16,
    fontWeight: '500',
  },
  time: {
    position: 'absolute',
    left: 0,
    top: 59.4,
    width: 96,
    color: '#6e6e6e',
    fontSize: 10.8,
    lineHeight: 16,
    fontWeight: '500',
  },
  eta: {
    position: 'absolute',
    left: 233,
    top: 28.8,
    width: 80,
    color: '#ffffff',
    fontSize: 12.4,
    lineHeight: 18,
    fontWeight: '500',
  },
  zone: {
    position: 'absolute',
    left: 233,
    top: 50.3,
    width: 104,
    color: '#9a9a9a',
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '500',
  },
  note: {
    position: 'absolute',
    left: 233,
    top: 67.4,
    width: 106,
    fontSize: 10.6,
    lineHeight: 16,
    fontWeight: '500',
    textShadowRadius: 7,
    textShadowOffset: { width: 0, height: 0 },
  },
  remaining: {
    position: 'absolute',
    left: 212.5,
    top: 119.3,
    width: 100,
    textAlign: 'right',
    color: '#4c4c4c',
    fontSize: 10.6,
    lineHeight: 16,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  swapSlot: {
    position: 'absolute',
    left: SWAP_X - SWAP_R,
    top: SWAP_Y - SWAP_R,
    width: SWAP_R * 2,
    height: SWAP_R * 2,
  },
  swap: {
    width: SWAP_R * 2,
    height: SWAP_R * 2,
    borderRadius: SWAP_R,
    backgroundColor: '#2d2d2d',
  },
  swapCanvas: {
    width: SWAP_R * 2,
    height: SWAP_R * 2,
  },
});
