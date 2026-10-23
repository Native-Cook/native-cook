import { BlurMask, Canvas, Group, LinearGradient, Path, RoundedRect, Skia, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Position on the dial, 0…1 of `max`. Leave undefined to let the dial keep its own value. */
  progress?: number;
  /** Sweeps the dial by itself (0 → 95 → 68 → 49 → 0) until it is dragged, and again a while after. */
  autoPlay?: boolean;
  max?: number;
  /** Called with the whole number under the needle while the user turns the dial. */
  onValueChange?: (value: number) => void;
  accessibilityLabel?: string;
};

const WIDTH = 330;
const HEIGHT = 124;
const RADIUS = 18;
// The canvas overflows the panel by this much on every side to fit its shadow.
const BLEED = 40;

const CX = WIDTH / 2;
// The ticks fan out from a point well below the panel.
const PIVOT_Y = 211;
const TICK_OUTER = 150.6;
const TICK_INNER = 123.4;
const TICK_W = 1.7;
// One unit of value turns the fan by this much, which is one tick.
const STEP = (3.55 * Math.PI) / 180;
// Ticks further than this from the needle fall outside the panel.
const TICKS_EACH_SIDE = 15;
// How far a finger travels per unit: the tick spacing under the needle.
const UNIT_PT = TICK_OUTER * STEP;
const NEEDLE_TOP = 50;
const NEEDLE_W = 2.9;
const VALUE_Y = 27.5;
const DIGIT_W = 10.2;
const DIGIT_H = 22;
const PLACES = [0, 1, 2];
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

const AUTO_STOPS = [95, 68, 49, 0];
const AUTO_HOLD_MS = 1300;
const AUTO_RESUME_MS = 3000;
const DRAG_SLOP = 4;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);

function seg(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}

type Motion = {
  /** The value under the needle; a fraction while the dial turns. */
  value: SharedValue<number>;
  /** 0…1, how fast the dial is turning: swells the ticks passing the needle and lights its tip. */
  heat: SharedValue<number>;
  /** Radians the needle is bent by the passing ticks; springs back when they stop. */
  lean: SharedValue<number>;
};

function Surface({ value, heat, lean }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const cx = x + CX;
  const pivot = y + PIVOT_Y;
  const panel = rrect(rect(x, y, WIDTH, HEIGHT), RADIUS, RADIUS);

  // Every tick in view, as one path; the ones passing the needle stand a little taller.
  const ticks = useDerivedValue(() => {
    const path = Skia.Path.Make();
    const v = value.get();
    const nearest = Math.round(v);
    for (let k = nearest - TICKS_EACH_SIDE; k <= nearest + TICKS_EACH_SIDE; k++) {
      const steps = k - v;
      const a = steps * STEP;
      const swell = 5 * heat.get() * Math.exp(-steps * steps * 1.2);
      const sin = Math.sin(a);
      const cos = Math.cos(a);
      path.moveTo(cx + TICK_INNER * sin, pivot - TICK_INNER * cos);
      path.lineTo(cx + (TICK_OUTER + swell) * sin, pivot - (TICK_OUTER + swell) * cos);
    }
    return path;
  });
  const needle = useDerivedValue(() => [{ rotate: lean.get() }]);
  const glow = useDerivedValue(() => 0.75 * heat.get());

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 8} y={y + 14} width={WIDTH - 16} height={HEIGHT - 8} r={RADIUS} color="rgba(0,0,0,0.3)">
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect rect={panel} color="#000000" />
      <Group clip={panel}>
        {/* Blue on the left, grey under the needle, gold on the right; the colours stay put while the ticks pass. */}
        <Path path={ticks} style="stroke" strokeWidth={TICK_W}>
          <LinearGradient
            start={vec(cx - 124, 0)}
            end={vec(cx + 124, 0)}
            colors={['rgba(110,128,196,0)', 'rgba(118,136,200,0.7)', '#96a4d2', '#b4b6bc', '#c0b996', 'rgba(196,174,96,0.7)', 'rgba(190,166,84,0)']}
            positions={[0, 0.14, 0.34, 0.5, 0.66, 0.86, 1]}
          />
        </Path>
        {/* The ticks nearest the needle catch its light while the dial turns. */}
        <Path path={ticks} style="stroke" strokeWidth={TICK_W} opacity={heat}>
          <LinearGradient start={vec(cx - 22, 0)} end={vec(cx + 22, 0)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.85)', 'rgba(255,255,255,0)']} />
        </Path>
        <Group transform={needle} origin={vec(cx, y + HEIGHT + 30)}>
          <RoundedRect x={cx - 5} y={y + NEEDLE_TOP - 3} width={10} height={40} r={5} color="#ffffff" opacity={glow}>
            <BlurMask blur={7} style="normal" />
          </RoundedRect>
          <RoundedRect x={cx - NEEDLE_W / 2} y={y + NEEDLE_TOP} width={NEEDLE_W} height={HEIGHT - NEEDLE_TOP + 4} r={NEEDLE_W / 2}>
            <LinearGradient
              start={vec(0, y + NEEDLE_TOP)}
              end={vec(0, y + HEIGHT)}
              colors={['#ffffff', '#ffffff', 'rgba(255,255,255,0.12)']}
              positions={[0, 0.62, 1]}
            />
          </RoundedRect>
        </Group>
      </Group>
    </Canvas>
  );
}

/** One place of the read-out: a strip of digits that rolls like an odometer wheel. */
function Wheel({ place, value, max }: { place: number; value: SharedValue<number>; max: number }) {
  const unit = Math.pow(10, place);
  const column = useAnimatedStyle(() => {
    const v = Math.min(max, Math.max(0, value.get()));
    // Places come and go as the number gains or loses a digit, and the read-out stays centred.
    const places = 1 + seg(v, 9, 10) + seg(v, 99, 100);
    return {
      opacity: place === 0 ? 1 : seg(v, unit - 1, unit - 0.4),
      transform: [{ translateX: (places / 2 - place - 1) * DIGIT_W }],
    };
  });
  const strip = useAnimatedStyle(() => {
    const v = Math.min(max, Math.max(0, value.get()));
    // A wheel only turns while the wheel to its right goes from 9 to 0.
    const below = place === 0 ? 1 : seg((v % unit) - (unit - 1), 0, 1);
    const digit = place === 0 ? v % 10 : (Math.floor(v / unit) % 10) + below;
    return { transform: [{ translateY: -digit * DIGIT_H }] };
  });
  return (
    <Animated.View style={[styles.wheel, column]}>
      <Animated.View style={strip}>
        {DIGITS.map((digit, index) => (
          <Text key={index} style={styles.digit}>
            {digit}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

/**
 * Black panel with a fan of ruler ticks under a white needle and the value above it. Drag sideways
 * to turn the fan: ticks swell and light up as they pass the needle, the needle bends with them and
 * springs back, the digits roll like an odometer, and every tick is a light haptic click.
 */
export function RulerDial({ progress, autoPlay = false, max = 100, onValueChange, accessibilityLabel = 'Dial' }: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress !== undefined;
  const initial = controlled ? Math.min(1, Math.max(0, progress)) * max : 0;
  const [shown, setShown] = useState(Math.round(initial));
  const [autoRun, setAutoRun] = useState(0);

  const value = useSharedValue(initial);
  const heat = useSharedValue(0);
  const lean = useSharedValue(0);
  const leanSpeed = useSharedValue(0);
  const last = useSharedValue(initial);
  // Set while the user is turning the dial (or it is coasting after a flick): clicks and callbacks.
  const handled = useSharedValue(false);

  const latest = useRef({ onValueChange });
  useEffect(() => {
    latest.current = { onValueChange };
  }, [onValueChange]);

  useEffect(() => {
    if (progress === undefined) return;
    // Reanimated drops animations under reduced motion; value changes keep a short tween.
    value.set(
      withTiming(Math.min(1, Math.max(0, progress)) * max, {
        duration: reducedMotion ? 200 : 700,
        easing: EASE_OUT,
        reduceMotion: ReduceMotion.Never,
      }),
    );
  }, [progress, max, reducedMotion, value]);

  // The demo sweep: from wherever the dial is to each stop in turn, with a rest at each.
  useEffect(() => {
    if (!autoPlay || controlled || reducedMotion) return;
    let from = value.get();
    const legs = AUTO_STOPS.map((stop) => {
      const to = Math.min(max, stop);
      const leg = withDelay(AUTO_HOLD_MS, withTiming(to, { duration: 700 + Math.abs(to - from) * 22, easing: EASE_IN_OUT }));
      from = to;
      return leg;
    });
    value.set(withRepeat(withSequence(...legs), -1));
    return () => cancelAnimation(value);
  }, [autoPlay, controlled, reducedMotion, autoRun, max, value]);

  useFrameCallback((frame) => {
    const dt = Math.min(0.05, Math.max(0.001, (frame.timeSincePreviousFrame ?? 16) / 1000));
    const speed = (value.get() - last.get()) / dt;
    last.set(value.get());
    heat.set(heat.get() + (Math.min(1, Math.abs(speed) / 24) - heat.get()) * 0.2);
    // The passing ticks push the needle over; a spring pulls it back and lets it wobble.
    const pushed = Math.max(-0.07, Math.min(0.07, -speed * 0.0022));
    const pull = (pushed - lean.get()) * 260 - leanSpeed.get() * 13;
    leanSpeed.set(leanSpeed.get() + pull * dt);
    lean.set(lean.get() + leanSpeed.get() * dt);
  }, !reducedMotion);

  const click = useCallback((whole: number) => {
    setShown(whole);
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    latest.current.onValueChange?.(whole);
  }, []);

  // One click per tick while the user turns the dial; the demo sweep stays silent.
  useAnimatedReaction(
    () => Math.round(value.get()),
    (now, before) => {
      if (now === before) return;
      if (handled.get()) scheduleOnRN(click, now);
      else if (now % 10 === 0 || before === null) scheduleOnRN(setShown, now);
    },
  );

  // --- Touch: a sideways drag turns the dial; lifting lets it coast to the nearest tick. ---
  const drag = useRef({ x: 0, from: 0, moved: false, lastX: 0, lastT: 0, speed: 0 });
  const resume = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(
    () => () => {
      if (resume.current) clearTimeout(resume.current);
    },
    [],
  );

  const onGrant = (e: GestureResponderEvent) => {
    if (resume.current) clearTimeout(resume.current);
    cancelAnimation(value);
    handled.set(true);
    const { pageX, timestamp } = e.nativeEvent;
    drag.current = { x: pageX, from: value.get(), moved: false, lastX: pageX, lastT: timestamp, speed: 0 };
  };
  const onMove = (e: GestureResponderEvent) => {
    const d = drag.current;
    const { pageX, timestamp } = e.nativeEvent;
    if (!d.moved && Math.abs(pageX - d.x) < DRAG_SLOP) return;
    d.moved = true;
    // Dragging left brings higher numbers under the needle, like sliding a ruler.
    value.set(Math.min(max, Math.max(0, d.from - (pageX - d.x) / UNIT_PT)));
    if (timestamp > d.lastT) d.speed = -(pageX - d.lastX) / UNIT_PT / ((timestamp - d.lastT) / 1000);
    d.lastX = pageX;
    d.lastT = timestamp;
  };
  const onEnd = () => {
    const d = drag.current;
    const coast = d.moved ? Math.max(-40, Math.min(40, d.speed * 0.22)) : 0;
    const to = Math.min(max, Math.max(0, Math.round(value.get() + coast)));
    value.set(
      withTiming(to, { duration: 320 + Math.abs(coast) * 14, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }, () => {
        handled.set(false);
      }),
    );
    resume.current = setTimeout(() => setAutoRun((n) => n + 1), AUTO_RESUME_MS);
  };

  const step = (by: number) => {
    const to = Math.min(max, Math.max(0, Math.round(value.get()) + by));
    handled.set(true);
    value.set(withTiming(to, { duration: 200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }, () => handled.set(false)));
  };

  return (
    <View
      style={styles.panel}
      onStartShouldSetResponder={() => !controlled}
      onResponderTerminationRequest={() => false}
      onResponderGrant={onGrant}
      onResponderMove={onMove}
      onResponderRelease={onEnd}
      onResponderTerminate={onEnd}
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ min: 0, max, now: shown }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={(e) => step(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} heat={heat} lean={lean} />
      </View>
      <View pointerEvents="none" style={styles.readout}>
        {PLACES.map((place) => (
          <Wheel key={place} place={place} value={value} max={max} />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
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
  readout: {
    position: 'absolute',
    left: CX,
    top: VALUE_Y - DIGIT_H / 2,
    height: DIGIT_H,
  },
  // Each wheel is centred on the read-out's origin and slid to its place.
  wheel: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DIGIT_W,
    height: DIGIT_H,
    overflow: 'hidden',
  },
  digit: {
    width: DIGIT_W,
    height: DIGIT_H,
    lineHeight: DIGIT_H,
    textAlign: 'center',
    color: '#ffffff',
    fontSize: 16,
    fontVariant: ['tabular-nums'],
  },
});
