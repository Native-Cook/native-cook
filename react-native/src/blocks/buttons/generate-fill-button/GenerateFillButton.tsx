import {
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Paint,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
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
  withDelay,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  /** Shown while the gradient is flowing. */
  activeLabel?: string;
  onPress?: () => void;
  /**
   * Controlled state: `true` sweeps the gradient in and keeps it flowing, `false` returns to
   * black. Leave undefined and a tap runs one generation of `duration` ms by itself.
   */
  generating?: boolean;
  duration?: number;
  /** Replays a generation by itself with a pause between runs. */
  autoPlay?: boolean;
};

const WIDTH = 168;
const HEIGHT = 58;
// The canvas overflows the button by this much on every side so its edge is not clipped.
const BLEED = 4;
// Radius of the gradient's rounded leading edge as it sweeps in from the left.
const LEAD = 44;
const SWEEP_MS = 2400;
const FADE_MS = 500;
const AUTOPLAY_PAUSE_MS = 1500;
const ICON_X = 29;
// Where the liquid rests when motion is reduced.
const STILL_TIME = 1;

const X = BLEED;
const Y = BLEED;
const CY = Y + HEIGHT / 2;
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function sparkle(cx: number, cy: number, r: number, k = 0.3) {
  const c = r * k;
  return (
    `M${cx} ${cy - r}Q${cx + c} ${cy - c} ${cx + r} ${cy}` +
    `Q${cx + c} ${cy + c} ${cx} ${cy + r}` +
    `Q${cx - c} ${cy + c} ${cx - r} ${cy}` +
    `Q${cx - c} ${cy - c} ${cx} ${cy - r}Z`
  );
}

// Soft blobs of colour that wander over the base gradient: [colour, radius, centre x, x swing,
// x speed, x phase, centre y offset, y swing, y speed], positions as fractions of the size.
const BLOBS: [string, number, number, number, number, number, number, number, number][] = [
  ['40,70,240', 0.9, 0.45, 0.3, 0.6, 2, 0, 0.3, 0.8],
  ['10,200,240', 0.95, 0.62, 0.25, 0.9, 0, 0, 0.25, 0.7],
  ['165,60,245', 0.85, 0.1, 0.12, 0.7, 1, 0, 0.15, 0.5],
  ['20,210,180', 0.6, 0.95, 0.08, 0.5, 0, 0.3, 0.1, 0.6],
];

function Blob({ index, clock }: { index: number; clock: SharedValue<number> }) {
  const [rgb, radius, bx, sx, vx, px, by, sy, vy] = BLOBS[index];
  const r = HEIGHT * radius;
  const c = useDerivedValue(() => {
    const t = clock.get();
    return vec(
      X + WIDTH * (bx + sx * Math.sin(t * vx * 1.6 + px)),
      CY + HEIGHT * (by + sy * Math.cos(t * vy * 1.6 + index)),
    );
  });
  return (
    <Circle c={c} r={r}>
      <RadialGradient c={c} r={r} colors={[`rgba(${rgb},0.85)`, `rgba(${rgb},0.4)`, `rgba(${rgb},0)`]} positions={[0, 0.5, 1]} />
    </Circle>
  );
}

type Motion = {
  sweep: SharedValue<number>;
  lit: SharedValue<number>;
  mix: SharedValue<number>;
  clock: SharedValue<number>;
};

function Surface({ sweep, lit, mix, clock }: Motion) {
  const pill = useMemo(() => rrect(rect(X, Y, WIDTH, HEIGHT), HEIGHT / 2, HEIGHT / 2), []);
  // Everything left of a big rounded edge that travels from the left end to past the right end.
  const lead = useDerivedValue(() => {
    const edge = -2 + sweep.get() * (WIDTH + 14);
    return Skia.RRectXY(Skia.XYWHRect(X - 200, CY - LEAD, 200 + edge, LEAD * 2), LEAD, LEAD);
  });
  const twinkleA = useDerivedValue(() => mix.get() * (0.5 + 0.5 * Math.sin(clock.get() * 5.2)));
  const twinkleB = useDerivedValue(() => mix.get() * (0.5 + 0.5 * Math.sin(clock.get() * 4.1 + 2.4)));
  const ix = X + ICON_X;

  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      <RoundedRect x={X} y={Y} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color="#000000" />
      <Group clip={pill}>
        {/* Soft grey sheen in the lower right corner of the black pill. */}
        <Rect x={X} y={Y} width={WIDTH} height={HEIGHT}>
          <RadialGradient
            c={vec(X + WIDTH * 0.92, Y + HEIGHT * 1.05)}
            r={WIDTH * 0.55}
            colors={['rgba(125,125,125,0.75)', 'rgba(70,70,70,0.35)', 'rgba(0,0,0,0)']}
            positions={[0, 0.45, 1]}
          />
        </Rect>
        <Group layer={<Paint opacity={lit} />}>
          <Group clip={lead}>
            <Rect x={X} y={Y} width={WIDTH} height={HEIGHT}>
              <LinearGradient
                start={vec(X, 0)}
                end={vec(X + WIDTH, 0)}
                colors={['#8b3cf5', '#5a5cf2', '#2f86f0', '#19a8f0', '#12c8dc']}
                positions={[0, 0.22, 0.5, 0.78, 1]}
              />
            </Rect>
            {BLOBS.map((_, i) => (
              <Blob key={i} index={i} clock={clock} />
            ))}
          </Group>
        </Group>
      </Group>

      <Path path={sparkle(ix, CY, 10)} color="#ffffff" />
      {/* Two small stars twinkle beside the big one while it is generating. */}
      <Path path={sparkle(ix - 9.5, CY + 8.5, 2.4)} color="#ffffff" opacity={twinkleA} />
      <Path path={sparkle(ix - 8.5, CY - 9, 1.8)} color="#ffffff" opacity={twinkleB} />
    </Canvas>
  );
}

/**
 * Black "Generate" pill with a sparkle. Tapping it sweeps a violet-to-cyan gradient in from the
 * left behind a rounded edge, the label becomes "Generating" and the colours keep flowing until
 * the run ends and the pill fades back to black.
 */
export function GenerateFillButton({
  label = 'Generate',
  activeLabel = 'Generating',
  onPress,
  generating,
  duration = 5600,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [own, setOwn] = useState(false);
  const active = generating ?? own;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const sweep = useSharedValue(0);
  const lit = useSharedValue(0);
  const mix = useSharedValue(0);
  const clock = useSharedValue(STILL_TIME);

  const flow = useFrameCallback((info) => {
    clock.set(clock.get() + (info.timeSincePreviousFrame ?? 0) / 1000);
  }, false);

  useEffect(() => {
    // The sweep and the fades are the state change itself, so they stay with reduced motion;
    // only the wandering colours and the twinkle are frozen.
    const keep = { reduceMotion: ReduceMotion.Never };
    if (active) {
      sweep.set(0);
      lit.set(1);
      sweep.set(withTiming(1, { duration: SWEEP_MS, easing: Easing.inOut(Easing.sin), ...keep }));
      mix.set(withTiming(1, { duration: 160, ...keep }));
      if (!reducedMotion) flow.setActive(true);
      return;
    }
    lit.set(withTiming(0, { duration: FADE_MS, ...keep }));
    mix.set(withDelay(140, withTiming(0, { duration: 160, ...keep })));
    // Keep the colours moving until the fade has finished.
    const stop = setTimeout(() => flow.setActive(false), FADE_MS + 100);
    return () => clearTimeout(stop);
  }, [active, reducedMotion, sweep, lit, mix, flow]);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  // An uncontrolled run: generate for `duration`, then return. Ignored while one is in flight.
  const run = useCallback(() => {
    if (generating !== undefined || timer.current) return;
    setOwn(true);
    timer.current = setTimeout(() => {
      timer.current = null;
      setOwn(false);
    }, duration);
  }, [generating, duration]);

  useEffect(() => {
    if (!autoPlay || own) return;
    const next = setTimeout(run, AUTOPLAY_PAUSE_MS + FADE_MS);
    return () => clearTimeout(next);
  }, [autoPlay, own, run]);

  const idleStyle = useAnimatedStyle(() => ({ opacity: 1 - mix.get() }));
  const activeStyle = useAnimatedStyle(() => ({ opacity: mix.get() }));

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={active ? activeLabel : label}
      accessibilityState={{ busy: active }}
      onPress={() => {
        onPress?.();
        run();
      }}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      pressRetentionOffset={16}
    >
      <Animated.View
        style={[
          styles.button,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Surface sweep={sweep} lit={lit} mix={mix} clock={clock} />
        </View>
        <Animated.Text style={[styles.label, styles.idleLabel, idleStyle]} numberOfLines={1}>
          {label}
        </Animated.Text>
        <Animated.Text style={[styles.label, styles.activeLabel, activeStyle]} numberOfLines={1}>
          {activeLabel}
        </Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
    justifyContent: 'center',
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  label: {
    position: 'absolute',
    color: '#ffffff',
    fontSize: 21,
    fontWeight: '700',
    letterSpacing: -0.2,
  },
  idleLabel: {
    left: 48,
  },
  // The longer word starts a little further left so it stays inside the pill.
  activeLabel: {
    left: 44,
  },
});
