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
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled share of the trip ridden, 0…1. */
  progress?: number;
  /** Rides to the point shown in the reference, waits there, rides on to the door, and starts over. */
  autoPlay?: boolean;
  restaurant?: string;
  order?: string;
  price?: string;
  payment?: string;
  courier?: string;
  /** Text in front of the time left. */
  arrival?: string;
  /** Minutes the whole trip takes: the time left counts down from it. */
  tripMinutes?: number;
  onCallPress?: () => void;
};

const WIDTH = 340;
const HEIGHT = 183;
const RADIUS = 35;
// The canvas overflows the panel by this much on every side to fit the screen glow around it.
const BLEED = 60;

const TRACK_X = 17.3;
const TRACK_W = 304;
const TRACK_Y = 109.1;
const TRACK_H = 5.8;
// The scooter is drawn around the point where it meets the road, at this scale.
const RIDER_SCALE = 0.97;
const RIDER_FRONT = 20.2 * RIDER_SCALE + 2.3;
const WHEEL_R = 5;
const GROUND_Y = TRACK_Y - TRACK_H / 2 + 0.5;

const TEAL = '#2fdca8';

// The reference shows the rider 69% of the way; the autoPlay loop built around it (seconds).
const REST = 0.69;
const LOW = 0.14;
const START = 0.6;
const RIDE = 3.2;
const HOLD = 3;
const FINISH = 1.7;
const HOLD_END = 1.6;
const RESET = 0.8;
const T1 = START + RIDE;
const T2 = T1 + HOLD;
const T3 = T2 + FINISH;
const T4 = T3 + HOLD_END;
const LOOP = T4 + RESET;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = T1 + 1.5;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// Scooter and rider, nose to the right; the origin is the middle of the road under the wheels.
const SCOOTER_BODY =
  'M-19.5 -8.5C-19.5 -13.5 -16 -16.5 -10 -16.5L-2.5 -16.5L-2.5 -9.5L5.5 -9.5L9.2 -24.5L12.4 -24.5L14.4 -10C11.5 -9.5 9 -7.6 8.4 -5L-7 -5C-7.6 -9.5 -12 -11.5 -15.5 -10.5C-17 -10 -18.6 -9.4 -19.5 -8.5Z';
const SCOOTER_FENDER = 'M7.8 -6C8.3 -10.2 11 -12 14.2 -12C17 -12 19.6 -10.2 20.2 -7L17 -7.6C16.2 -9 15 -9.6 13.8 -9.6C11.8 -9.6 10.6 -8 10.4 -6Z';
const SCOOTER_BAR = 'M10.4 -25.6L6.2 -28.2';
const RIDER_LEG = 'M-6.5 -20.6L1 -20.6L2.2 -10.8';
const RIDER_SHOE = 'M1.2 -11.2H5.2V-9.4H1.2Z';
const RIDER_TORSO = 'M-9 -19.5L-8 -29C-7.8 -31 -6.2 -32 -4.4 -32C-2.4 -32 -1.2 -30.6 -1.4 -28.6L-2.4 -19.5Z';
const RIDER_ARM = 'M-3.8 -28.8L2 -26.4L6.4 -27.8';
const SPOKES = 'M-3.2 0H3.2M0 -3.2V3.2';

// Burger and drink, centred on the badge.
const BURGER =
  'M-7.6 -0.4C-7.6 -4.4 -4.8 -6.4 -1.4 -6.4C2 -6.4 4.8 -4.4 4.8 -0.4ZM-8 1H5.2V3H-8ZM-7.6 4.4H4.8V5.4C4.8 6.4 4 7 3 7H-5.8C-6.8 7 -7.6 6.4 -7.6 5.4Z';
const DRINK = 'M3.4 -4.6H9.4L8.6 7H6.2V-0.6C6.2 -2.2 5 -3.8 3.4 -4.6Z';
const STRAW = 'M6.8 -4.6L7.6 -8.2L9.4 -8.8';
const PHONE =
  'M-4 -4.2C-3.5 -4.4 -3 -4.1 -2.8 -3.6L-2.1 -1.8C-1.9 -1.4 -2.1 -1 -2.4 -0.7L-3.2 -0.1C-2.5 1.3 -1.5 2.3 -0.1 3L0.5 2.2C0.8 1.9 1.2 1.7 1.6 1.9L3.4 2.6C3.9 2.8 4.2 3.3 4 3.8C3.7 4.9 2.7 5.4 1.7 5.2C-2.3 4.5 -5.4 1.4 -6.1 -2.6C-6.3 -3.4 -5.1 -3.9 -4 -4.2Z';
const WAVE_NEAR = 'M1.2 -3.8Q3.6 -3.2 4.2 -0.8';
const WAVE_FAR = 'M1.8 -6.6Q6.4 -5.6 7.2 -1.2';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Share of the trip ridden at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return LOW;
  if (t < T1) return LOW + (REST - LOW) * easeInOutCubic((t - START) / RIDE);
  if (t < T2) return REST;
  if (t < T3) return REST + (1 - REST) * easeInOutCubic((t - T2) / FINISH);
  if (t < T4) return 1;
  return LOW;
}

/** The rider fades out at the door and back in at the start. */
function loopPresence(t: number) {
  'worklet';
  if (t < START) return clamp01(t / 0.4);
  if (t < T4) return 1;
  return 1 - clamp01((t - T4) / (RESET * 0.6));
}

function fillEnd(p: number) {
  'worklet';
  return TRACK_X + TRACK_W * clamp01(p);
}

function minutesLeft(p: number, tripMinutes: number) {
  'worklet';
  return Math.ceil(tripMinutes * (1 - clamp01(p)) - 0.001);
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** 0…1, smoothed: is the scooter rolling. */
  moving: SharedValue<number>;
  /** Positive while pulling away, negative while braking: tips the scooter. */
  pitch: SharedValue<number>;
  /** How far the wheels have turned, radians. */
  wheel: SharedValue<number>;
  /** 0…1: the rider and the lit part of the road fade with it. */
  presence: SharedValue<number>;
  /** 0 with reduced motion: no bobbing, puffs, breathing or ringing. */
  ambient: number;
};

function Puff({ index, time, moving, value, presence }: Pick<Motion, 'time' | 'moving' | 'value' | 'presence'> & { index: number }) {
  // Exhaust: small clouds that drift back from the rear wheel and thin out.
  const u = useDerivedValue(() => (time.get() * 1.5 + index / 3) % 1);
  const cx = useDerivedValue(() => BLEED + fillEnd(value.get()) - RIDER_FRONT - 22 * RIDER_SCALE - 2 - 15 * u.get());
  const cy = useDerivedValue(() => BLEED + GROUND_Y - 7 - 6 * u.get());
  const r = useDerivedValue(() => 1.3 + 2.6 * u.get());
  const opacity = useDerivedValue(() => presence.get() * moving.get() * 0.55 * (1 - u.get()) * clamp01(u.get() * 6));
  return <Circle cx={cx} cy={cy} r={r} color="#c9efe4" opacity={opacity} />;
}

function Wheel({ x, wheel }: { x: number; wheel: SharedValue<number> }) {
  const spin = useDerivedValue(() => [{ translateX: x }, { translateY: -WHEEL_R }, { rotate: wheel.get() }]);
  return (
    <>
      <Circle cx={x} cy={-WHEEL_R} r={WHEEL_R} color="#3a3c48" />
      <Circle cx={x} cy={-WHEEL_R} r={2.6} color="#c9ccd8" />
      <Group transform={spin}>
        <Path path={SPOKES} style="stroke" strokeWidth={0.9} strokeCap="round" color="#3a3c48" />
      </Group>
    </>
  );
}

function Surface({ value, time, moving, pitch, wheel, presence, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const panel = { rect: { x, y, width: WIDTH, height: HEIGHT }, rx: RADIUS, ry: RADIUS };
  const cy = y + TRACK_Y;

  const end = useDerivedValue(() => x + fillEnd(value.get()));
  const fillWidth = useDerivedValue(() => Math.max(TRACK_H, end.get() - x - TRACK_X));
  const gradientEnd = useDerivedValue(() => vec(end.get(), cy));
  // The glow travels with the rider and breathes.
  const glowAt = useDerivedValue(() => [{ translateX: end.get() - 22 }, { translateY: cy - 14 }]);
  const glow = useDerivedValue(() => presence.get() * (0.86 + ambient * 0.14 * Math.sin(time.get() * 2)));
  const barGlow = useDerivedValue(() => presence.get() * 0.55);
  // The scooter bounces on the road while it rolls, idles with a slow sway, and tips as it pulls away or brakes.
  const rider = useDerivedValue(() => {
    const m = moving.get();
    const bounce = ambient * (m * 0.7 * Math.abs(Math.sin(time.get() * 13)) + (1 - m) * 0.35 * (0.5 + 0.5 * Math.sin(time.get() * 2.4)));
    return [
      { translateX: end.get() - RIDER_FRONT },
      { translateY: y + GROUND_Y - bounce },
      { rotate: -0.12 * ambient * pitch.get() },
      { scale: RIDER_SCALE },
    ];
  });
  const fade = useDerivedValue(() => presence.get());

  return (
    <Canvas style={styles.canvas}>
      {/* The screen the panel floats on: pale at the top, deep blue towards the bottom. */}
      <RoundedRect x={x - 9} y={y - 9} width={WIDTH + 18} height={HEIGHT + 70} r={RADIUS + 9}>
        <LinearGradient
          start={vec(x, y - 9)}
          end={vec(x, y + HEIGHT + 61)}
          colors={['rgba(222,227,238,1)', 'rgba(150,178,214,0.9)', 'rgba(40,86,160,0.5)', 'rgba(10,30,90,0)']}
          positions={[0, 0.5, 0.74, 1]}
        />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS} color="#000000" />

      <Group clip={panel}>
        <Group transform={glowAt} opacity={glow}>
          <Circle cx={0} cy={0} r={92}>
            <RadialGradient
              c={vec(0, 0)}
              r={92}
              colors={['rgba(24,214,160,0.42)', 'rgba(20,190,150,0.2)', 'rgba(16,150,130,0.05)', 'rgba(16,150,130,0)']}
              positions={[0, 0.4, 0.75, 1]}
            />
          </Circle>
        </Group>

        {/* Road: dim track, lit part with its glow. */}
        <RoundedRect x={x + TRACK_X} y={cy - TRACK_H / 2} width={TRACK_W} height={TRACK_H} r={TRACK_H / 2} color="#2d2d2d" />
        <RoundedRect x={x + TRACK_X} y={cy - TRACK_H / 2} width={fillWidth} height={TRACK_H} r={TRACK_H / 2} color="#1fd9b4" opacity={barGlow}>
          <BlurMask blur={4} style="normal" />
        </RoundedRect>
        <RoundedRect x={x + TRACK_X} y={cy - TRACK_H / 2} width={fillWidth} height={TRACK_H} r={TRACK_H / 2} opacity={fade}>
          <LinearGradient start={vec(x + TRACK_X, cy)} end={gradientEnd} colors={['#1aaef5', '#19d2c8', '#22e3a0']} positions={[0, 0.6, 1]} />
        </RoundedRect>

        <Puff index={0} time={time} moving={moving} value={value} presence={presence} />
        <Puff index={1} time={time} moving={moving} value={value} presence={presence} />
        <Puff index={2} time={time} moving={moving} value={value} presence={presence} />

        <Group transform={rider} opacity={fade}>
          <Wheel x={-13} wheel={wheel} />
          <Wheel x={13.5} wheel={wheel} />
          {/* Delivery box and seat. */}
          <RoundedRect x={-20.5} y={-25.5} width={8} height={8.6} r={1} color="#e8562a" />
          <RoundedRect x={-21.2} y={-27.2} width={9.4} height={2.3} r={1} color="#f5c53a" />
          <RoundedRect x={-12.4} y={-19} width={10.4} height={2.8} r={1.4} color="#f5c53a" />
          <Path path={SCOOTER_BODY} color="#3d8df2" />
          <Path path={SCOOTER_FENDER} color="#f2c230" />
          <Path path={SCOOTER_BAR} style="stroke" strokeWidth={1.4} strokeCap="round" color="#2a2c36" />
          <Circle cx={12.6} cy={-25.8} r={2.1} color="#f5c53a" />
          {/* Rider. */}
          <Path path={RIDER_LEG} style="stroke" strokeWidth={3.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
          <Path path={RIDER_SHOE} color="#e9edf5" />
          <Path path={RIDER_TORSO} color="#f04e2c" />
          <Path path={RIDER_ARM} style="stroke" strokeWidth={2.5} strokeCap="round" strokeJoin="round" color="#f04e2c" />
          <Circle cx={-2.2} cy={-34.6} r={2.6} color="#f3c9a4" />
          <Circle cx={-4.2} cy={-35.6} r={3.7} color="#f5c22e" />
        </Group>
      </Group>

      {/* Restaurant badge and courier avatar. */}
      <Circle cx={x + 32.8} cy={y + 36.5} r={15} color="#353c3b" />
      <Group transform={[{ translateX: x + 32.4 }, { translateY: y + 36.8 }, { scale: 0.92 }]}>
        <Path path={DRINK} color="#ffffff" />
        <Path path={STRAW} style="stroke" strokeWidth={1.1} strokeCap="round" strokeJoin="round" color="#ffffff" />
        <Path path={BURGER} color="#ffffff" />
      </Group>
      <Circle cx={x + 32.8} cy={y + 146.1} r={15}>
        <LinearGradient start={vec(x + 20, y + 132)} end={vec(x + 46, y + 160)} colors={['#d49a6a', '#8a5636']} />
      </Circle>
    </Canvas>
  );
}

function CallButton({ time, ambient, label, onPress }: { time: SharedValue<number>; ambient: number; label: string; onPress?: () => void }) {
  // The handset "rings": its two waves light up one after the other.
  const near = useDerivedValue(() => (ambient ? 0.35 + 0.65 * clamp01(Math.sin(time.get() * 3.2) * 1.4 + 0.3) : 1));
  const far = useDerivedValue(() => (ambient ? 0.25 + 0.75 * clamp01(Math.sin(time.get() * 3.2 - 0.9) * 1.4 + 0.3) : 1));
  return (
    <Pressable
      onPress={onPress}
      onPressIn={() => {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }}
      hitSlop={8}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={styles.callSlot}
    >
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.call,
            {
              transform: [{ scale: pressed ? 0.92 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          <Canvas style={styles.callCanvas}>
            <Group transform={[{ translateX: 15.2 }, { translateY: 15 }, { scale: 1.12 }]}>
              <Path path={PHONE} style="stroke" strokeWidth={1.25} strokeJoin="round" color={TEAL} />
              <Path path={WAVE_NEAR} style="stroke" strokeWidth={1.25} strokeCap="round" color={TEAL} opacity={near} />
              <Path path={WAVE_FAR} style="stroke" strokeWidth={1.25} strokeCap="round" color={TEAL} opacity={far} />
            </Group>
          </Canvas>
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * Order-tracking panel on a glowing phone screen: the restaurant and order, a road with a courier
 * on a scooter, and the courier's row with the time left and a call button. The wheels turn with
 * the distance covered, the scooter bounces, tips as it pulls away and brakes, and leaves exhaust
 * puffs; a teal glow rides along, the minutes tick down with a small pop and the handset rings.
 */
export function RiderTrackProgress({
  progress,
  autoPlay = false,
  restaurant = 'Food Hub',
  order = 'Pizza Pepperoni',
  price = '250₹',
  payment = 'COD',
  courier = 'Alex R.',
  arrival = 'Will arrive in',
  tripMinutes = 16,
  onCallPress,
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
  const pitch = useSharedValue(0);
  const wheel = useSharedValue(0);
  // 0 → 1 after the minutes change: the number pops.
  const tick = useSharedValue(1);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 1600, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));
  const presence = useDerivedValue(() => (controlled ? 1 : loopPresence(clock.get())));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    const now = value.get();
    const step = now - before.get();
    before.set(now);
    // A jump back to the start is not a ride.
    const rolling = Math.abs(step) > 0.00003 && Math.abs(step) < 0.05;
    const was = moving.get();
    moving.set(was + ((rolling ? 1 : 0) - was) * Math.min(1, dt * 7));
    pitch.set(pitch.get() + ((rolling ? 1 : 0) - was - pitch.get()) * Math.min(1, dt * 10));
    if (rolling) wheel.set(wheel.get() + (step * TRACK_W) / (WHEEL_R * RIDER_SCALE));
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

  const [minutes, setMinutes] = useState(minutesLeft(target, tripMinutes));
  useAnimatedReaction(
    () => minutesLeft(value.get(), tripMinutes),
    (now, old) => {
      if (now === old || old == null) return;
      scheduleOnRN(setMinutes, now);
      if (reducedMotion) return;
      tick.set(0);
      tick.set(withTiming(1, { duration: 380, easing: EASE_OUT }));
    },
  );
  const tickStyle = useAnimatedStyle(() => {
    const s = tick.get();
    return { transform: [{ translateY: -3 * (1 - s) }, { scale: 1 + 0.14 * (1 - s) }], opacity: 0.4 + 0.6 * s };
  });

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const left = minutes > 0 ? `${minutes}min` : 'a moment';
  const initials = courier
    .split(' ')
    .map((word) => word[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <Pressable onPress={restart} style={styles.card} accessible={false}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} time={time} moving={moving} pitch={pitch} wheel={wheel} presence={presence} ambient={reducedMotion ? 0 : 1} />
      </View>

      <Text
        style={[styles.name, styles.top]}
        numberOfLines={1}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${restaurant}, ${order}`}
        accessibilityValue={{ min: 0, max: tripMinutes, now: tripMinutes - minutes, text: `${courier} ${arrival.toLowerCase()} ${left}` }}
      >
        {restaurant}
      </Text>
      <Text style={[styles.detail, styles.topDetail]} numberOfLines={1}>
        {order}
      </Text>
      <Text style={[styles.name, styles.top, styles.right]} numberOfLines={1}>
        {price}
      </Text>
      <Text style={[styles.detail, styles.topDetail, styles.right]} numberOfLines={1}>
        {payment}
      </Text>

      <Text style={styles.initials} numberOfLines={1}>
        {initials}
      </Text>
      <Text style={[styles.name, styles.bottom]} numberOfLines={1}>
        {courier}
      </Text>
      <View style={styles.arrivalRow}>
        <Text style={styles.arrival} numberOfLines={1}>
          {arrival}{' '}
        </Text>
        <Animated.Text style={[styles.arrival, styles.left, tickStyle]} numberOfLines={1}>
          {left}
        </Animated.Text>
      </View>

      <CallButton time={time} ambient={reducedMotion ? 0 : 1} label={`Call ${courier}`} onPress={onCallPress} />
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
  name: {
    position: 'absolute',
    left: 59.7,
    width: 180,
    color: '#ffffff',
    fontSize: 12.6,
    lineHeight: 18,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  detail: {
    position: 'absolute',
    left: 59.7,
    width: 180,
    color: '#7b7b7b',
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.3,
  },
  top: {
    top: 19.3,
  },
  topDetail: {
    top: 38.1,
  },
  bottom: {
    top: 128.7,
  },
  right: {
    left: 320.9 - 120,
    width: 120,
    textAlign: 'right',
  },
  initials: {
    position: 'absolute',
    left: 17.8,
    top: 138.1,
    width: 30,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  arrivalRow: {
    position: 'absolute',
    left: 59.7,
    top: 147.5,
    height: 16,
    flexDirection: 'row',
  },
  arrival: {
    color: '#7b7b7b',
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 0.3,
  },
  left: {
    color: TEAL,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    transformOrigin: 'left center',
  },
  callSlot: {
    position: 'absolute',
    left: 306.8 - 15,
    top: 146.1 - 15,
    width: 30,
    height: 30,
  },
  call: {
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: '#343b3a',
  },
  callCanvas: {
    width: 30,
    height: 30,
  },
});
