import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
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
  /** Controlled position on the route, 0…1: 0 is the first station, 0.5 the second, 1 the third. */
  progress?: number;
  /** Rides from the first station to the second (the reference frame), on to the third, and starts over. */
  autoPlay?: boolean;
  line?: string;
  stations?: [string, string, string];
  /** Chip text when the next stop is far, near, and reached. */
  eta?: [string, string, string];
};

const WIDTH = 340;
const HEIGHT = 134;
const RADIUS = 37;
// The canvas overflows the card by this much on every side to fit the device body it sits in.
const BLEED = 70;

const BAR_X = 27.2;
const BAR_Y = 69.1;
const BAR_W = 278.3;
const BAR_H = 15.3;
const BAR_CY = BAR_Y + BAR_H / 2;
const RING_R = 15.5;
// Distance between the first and last station dots.
const SPAN = BAR_W - BAR_H;

const LIME = '#b2fe5e';
const TEXT = '#b4f56c';

// The reference shows the train at the second station; the autoPlay loop built around it (seconds).
const REST = 0.5;
const START = 0.5;
const LEG = 2.4;
const HOLD = 3;
const HOLD_END = 1.8;
const RESET = 0.7;
const T2 = START + LEG + HOLD;
const T3 = T2 + LEG + HOLD_END;
const LOOP = T3 + RESET;
// A moment in the loop where everything is at rest at the second station: used when nothing is playing.
const REST_T = START + LEG + 1.2;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// Front of a train in a 26 × 28 box.
const TRAIN_BODY = 'M8 1H18C21.3 1 24 3.7 24 7V16.5C24 19.5 21.5 22 18.5 22H7.5C4.5 22 2 19.5 2 16.5V7C2 3.7 4.7 1 8 1Z';
const TRAIN_WINDOW = 'M8.2 5H17.8C19 5 20 6 20 7.2V9.6C20 10.8 19 11.8 17.8 11.8H8.2C7 11.8 6 10.8 6 9.6V7.2C6 6 7 5 8.2 5Z';
const TRAIN_LEGS = 'M7.2 22.4L4.6 26.4M18.8 22.4L21.4 26.4';
const CHEVRON = 'M-2 -3L1.4 0L-2 3';

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

/** Position on the route at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + LEG) return 0.5 * easeInOutCubic((t - START) / LEG);
  if (t < T2) return 0.5;
  if (t < T2 + LEG) return 0.5 + 0.5 * easeInOutCubic((t - T2) / LEG);
  if (t < T3) return 1;
  return 1 - easeInOutCubic((t - T3) / RESET);
}

/** Marker centre, measured from the bar's left end. */
function markerX(p: number) {
  'worklet';
  return BAR_H / 2 + SPAN * clamp01(p);
}

/** Which chip text fits the position: 0 far from the next stop, 1 near it, 2 there. */
function etaIndex(p: number) {
  'worklet';
  const leg = p <= 0.5 ? p / 0.5 : (p - 0.5) / 0.5;
  return leg >= 0.985 ? 2 : leg >= 0.5 ? 1 : 0;
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** 0…1, smoothed: is the train moving. */
  moving: SharedValue<number>;
  /** When the train last pulled into a station, on the `time` axis. */
  arrivedAt: SharedValue<number>;
  /** 0 with reduced motion: no pings, chevrons or breathing. */
  ambient: number;
};

function Chevron({ index, value, time, moving }: { index: number; value: SharedValue<number>; time: SharedValue<number>; moving: SharedValue<number> }) {
  // Chevrons run from the marker to the next station while the train is on its way there.
  const from = useDerivedValue(() => markerX(value.get()) + RING_R + 4);
  const room = useDerivedValue(() => {
    const next = BAR_H / 2 + SPAN * (value.get() < 0.5 ? 0.5 : 1);
    return next - 6 - from.get();
  });
  const along = useDerivedValue(() => (room.get() > 4 ? (time.get() * 30 + index * 17) % room.get() : 0));
  const transform = useDerivedValue(() => [{ translateX: from.get() + along.get() }]);
  const opacity = useDerivedValue(() => {
    const r = room.get();
    if (r <= 4) return 0;
    const a = along.get();
    return moving.get() * 0.75 * clamp01(a / 8) * clamp01((r - a) / 8);
  });
  return (
    <Group transform={transform} opacity={opacity}>
      <Path path={CHEVRON} style="stroke" strokeWidth={1.7} strokeCap="round" strokeJoin="round" color={LIME} />
    </Group>
  );
}

function Ping({ delay, time, arrivedAt, ambient }: { delay: number; time: SharedValue<number>; arrivedAt: SharedValue<number>; ambient: number }) {
  const spread = useDerivedValue(() => (time.get() - arrivedAt.get() - delay) / 0.95);
  const r = useDerivedValue(() => RING_R + 26 * easeOutCubic(spread.get()));
  const opacity = useDerivedValue(() => {
    const s = spread.get();
    return s <= 0 || s >= 1 ? 0 : ambient * 0.75 * (1 - s);
  });
  return <Circle cx={0} cy={0} r={r} style="stroke" strokeWidth={1.4} color="#74ffab" opacity={opacity} />;
}

function Surface({ value, time, moving, arrivedAt, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const bx = x + BAR_X;
  const cy = y + BAR_CY;

  const at = useDerivedValue(() => markerX(value.get()));
  // The lit part ends in a round cap centred on the marker.
  const litWidth = useDerivedValue(() => at.get() + BAR_H / 2);
  const marker = useDerivedValue(() => [{ translateX: bx + at.get() }, { translateY: cy }]);
  const ring = useDerivedValue(() => RING_R + ambient * 0.7 * Math.sin(time.get() * 2.4));
  // The marker's dot swells for a moment when the train arrives.
  const dot = useDerivedValue(() => {
    const since = time.get() - arrivedAt.get();
    return 4.2 * (1 + ambient * 0.38 * Math.sin(Math.PI * clamp01(since / 0.42)));
  });

  return (
    <Canvas style={styles.canvas}>
      {/* The device the live activity sits in: a pale body that fades out below the card. */}
      <RoundedRect x={x - 10} y={y - 10} width={WIDTH + 20} height={HEIGHT + 80} r={RADIUS + 10}>
        <LinearGradient
          start={vec(x, y - 10)}
          end={vec(x, y + HEIGHT + 70)}
          colors={['rgba(250,250,250,1)', 'rgba(240,240,240,0.9)', 'rgba(231,231,231,0)']}
          positions={[0, 0.6, 1]}
        />
      </RoundedRect>
      <RoundedRect x={x + 10} y={y + 14} width={WIDTH - 20} height={HEIGHT - 4} r={RADIUS} color="rgba(0,0,0,0.3)">
        <BlurMask blur={16} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} color="#000000" />

      {/* Train glyph. */}
      <Group transform={[{ translateX: x + 27.2 }, { translateY: y + 25.6 }, { scale: 0.98 }]}>
        <Path path={TRAIN_LEGS} style="stroke" strokeWidth={2.7} strokeCap="round" color={TEXT} />
        <Path path={TRAIN_BODY} color={TEXT} />
        <Path path={TRAIN_WINDOW} color="#000000" />
        <Circle cx={8.3} cy={16.6} r={1.9} color="#000000" />
        <Circle cx={17.7} cy={16.6} r={1.9} color="#000000" />
      </Group>

      {/* Route: dim ahead, lit behind, a dot per station. */}
      <RoundedRect x={bx} y={y + BAR_Y} width={BAR_W} height={BAR_H} r={BAR_H / 2} color="#2d6e05" />
      <Group transform={[{ translateX: bx }, { translateY: cy }]}>
        {[0, 1, 2, 3].map((i) => (
          <Chevron key={i} index={i} value={value} time={time} moving={moving} />
        ))}
      </Group>
      <RoundedRect x={bx} y={y + BAR_Y} width={litWidth} height={BAR_H} r={BAR_H / 2} color={LIME} />
      {[0, 0.5, 1].map((stop) => (
        <Circle key={stop} cx={bx + BAR_H / 2 + SPAN * stop} cy={cy} r={3.2} color="#000000" />
      ))}

      <Group transform={marker}>
        <Ping delay={0} time={time} arrivedAt={arrivedAt} ambient={ambient} />
        <Ping delay={0.24} time={time} arrivedAt={arrivedAt} ambient={ambient} />
        <Circle cx={0} cy={0} r={ring} color="rgba(120,255,170,0.12)" />
        <Circle cx={0} cy={0} r={ring} style="stroke" strokeWidth={3} color="rgba(116,255,171,0.55)">
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Circle cx={0} cy={0} r={ring} style="stroke" strokeWidth={1.7} color="#8dffba" />
        <Circle cx={0} cy={0} r={7} color="rgba(255,255,255,0.6)">
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Circle cx={0} cy={0} r={dot} color="#ffffff" />
      </Group>
    </Canvas>
  );
}

function Station({ name, index, value }: { name: string; index: number; value: SharedValue<number> }) {
  // A station's name lights up as the train pulls in.
  const lit = useAnimatedStyle(() => ({ opacity: clamp01((value.get() - (index * 0.5 - 0.07)) / 0.07) }));
  const dim = useAnimatedStyle(() => ({ opacity: 1 - clamp01((value.get() - (index * 0.5 - 0.07)) / 0.07) }));
  const align = index === 0 ? styles.stationStart : index === 1 ? styles.stationMiddle : styles.stationEnd;
  return (
    <>
      <Animated.Text style={[styles.station, align, styles.stationDim, dim]} numberOfLines={1}>
        {name}
      </Animated.Text>
      <Animated.Text style={[styles.station, align, lit]} numberOfLines={1}>
        {name}
      </Animated.Text>
    </>
  );
}

/**
 * Live-activity style route card: a train glyph and line name, a yellow chip with the time to the
 * next stop, and a lime route bar with three stations and a ringed marker for the train. Chevrons
 * run ahead of the train while it moves, each arrival pings (rings from the marker, the dot swells,
 * the station's name lights up), and the chip's text blurs into its next state.
 */
export function TransitRouteProgress({
  progress,
  autoPlay = false,
  line = 'Circle Line',
  stations = ['Harbor', 'Midtown', 'Parkway'],
  eta = ['2 min', '1 min', 'now'],
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
  const moving = useSharedValue(0);
  const arrivedAt = useSharedValue(-10);
  const chip = useSharedValue(etaIndex(target));
  // 0 → 1 after the chip's text changes: it blurs in.
  const settle = useSharedValue(1);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 1200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    const now = value.get();
    const was = before.get();
    const step = now - was;
    moving.set(moving.get() + ((Math.abs(step) > 0.00002 ? 1 : 0) - moving.get()) * Math.min(1, dt * 8));
    // Pulling into the second or third station sets off the ping.
    if ((was < 0.4995 && now >= 0.4995) || (was < 0.9995 && now >= 0.9995)) arrivedAt.set(time.get());
    // On the way back to the start the chip already shows the full wait.
    chip.set(step < 0 ? 0 : etaIndex(now));
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

  const [etaShown, setEtaShown] = useState(etaIndex(target));
  useAnimatedReaction(
    () => (reducedMotion ? etaIndex(value.get()) : chip.get()),
    (now, old) => {
      if (now === old || old == null) return;
      scheduleOnRN(setEtaShown, now);
      if (reducedMotion) return;
      settle.set(0);
      settle.set(withTiming(1, { duration: 520, easing: EASE_OUT }));
    },
  );
  const [stop, setStop] = useState(Math.round(target * 2));
  useAnimatedReaction(
    () => Math.round(value.get() * 2),
    (now, old) => {
      if (now !== old) scheduleOnRN(setStop, now);
    },
  );

  const etaStyle = useAnimatedStyle(() => {
    const s = settle.get();
    return {
      opacity: 0.25 + 0.75 * s,
      transform: [{ scale: 0.86 + 0.14 * s }],
      // React Native's types give a text style only the string form of `filter`; views take this list form.
      filter: [{ blur: 5 * (1 - s) }] as unknown as string,
    };
  });

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
      accessibilityLabel={line}
      accessibilityValue={{ text: `${stations[stop]}, ${eta[etaShown]}` }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} moving={moving} arrivedAt={arrivedAt} ambient={reducedMotion ? 0 : 1} />
      </View>

      <Text style={styles.line} numberOfLines={1}>
        {line}
      </Text>
      <View style={styles.chip}>
        <Animated.Text style={[styles.chipLabel, etaStyle]} numberOfLines={1}>
          {eta[etaShown]}
        </Animated.Text>
      </View>
      {stations.map((name, i) => (
        <Station key={i} name={name} index={i} value={value} />
      ))}
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
  line: {
    position: 'absolute',
    left: 63.5,
    top: 27.8,
    color: TEXT,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  chip: {
    position: 'absolute',
    left: 255,
    top: 27.4,
    width: 50.9,
    height: 24.4,
    borderRadius: 12.2,
    backgroundColor: '#f2e300',
    alignItems: 'center',
    justifyContent: 'center',
  },
  chipLabel: {
    color: '#4d4500',
    fontSize: 12.5,
    lineHeight: 16,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  station: {
    position: 'absolute',
    top: 90.2,
    width: 120,
    color: TEXT,
    fontSize: 13.5,
    lineHeight: 20,
    fontWeight: '500',
  },
  stationDim: {
    color: '#5a8a25',
  },
  stationStart: {
    left: 26,
    textAlign: 'left',
  },
  stationMiddle: {
    left: BAR_X + BAR_W / 2 - 60,
    textAlign: 'center',
  },
  stationEnd: {
    left: BAR_X + BAR_W - 2.5 - 120,
    textAlign: 'right',
  },
});
