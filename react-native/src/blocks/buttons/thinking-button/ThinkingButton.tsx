import {
  Blur,
  Canvas,
  Group,
  LinearGradient,
  Paint,
  Path,
  Rect,
  RoundedRect,
  Skia,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
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

type Props = {
  /** One label per look: rainbow, silver, blue-to-red. */
  labels?: [string, string, string];
  /** Controlled phase index (0–2). Leave undefined to let the button keep its own. */
  phase?: number;
  onPhaseChange?: (phase: number) => void;
  onPress?: () => void;
  /** Steps through the three phases by itself. A tap always advances to the next one. */
  autoPlay?: boolean;
};

const WIDTH = 220;
const HEIGHT = 64;
// The canvas overflows the button by this much on every side so the rim is not clipped.
const BLEED = 12;
// The liquid is drawn this much past the pill so blurred edges never show inside it.
const SPILL = 24;
const PHASE_MS = 2800;
const FADE_MS = 500;
// Where the liquid rests when motion is reduced.
const STILL_TIME = 1.2;

const X = BLEED;
const Y = BLEED;
const R = HEIGHT / 2;
const TAU = Math.PI * 2;
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const RAINBOW = ['#f28a1c', '#e8c21a', '#58d04a', '#1fc7c4', '#2f6fe8', '#c035e0', '#7a2cf0'];
const RAINBOW_STOPS = [0.02, 0.2, 0.36, 0.52, 0.62, 0.8, 1];

// Fluted glass over the silver look: each strip refracts the bands by its own amount.
const FLUTES = 6;
const FLUTE_SHIFT = [0, 0.1, -0.05, 0.14, 0.03, -0.08];
const FLUTE_TINT = [0, 0.05, 0.02, 0.07, 0.02, 0.04];
const SILVER = ['#5d5d60', '#8e8e90', '#b9b7b3', '#7a5f45', '#0c0b0b', '#0a0a0b', '#2a2a2d', '#5d5d60'];
const SILVER_STOPS = [0, 0.2, 0.36, 0.42, 0.47, 0.6, 0.75, 1];
// Band normal (the bands run about 24° off horizontal) and the distance between two bands.
const BAND_NX = -0.41;
const BAND_NY = 0.91;
const BAND_PERIOD = 130;

const STEPS = 24;

/** Height of the rainbow's surface at `u` (0–1 across the pill), as a fraction of the height. */
function waveLevel(u: number, t: number) {
  'worklet';
  return 0.5 + 0.12 * Math.sin(TAU * (u * 1.1 + t * 0.21)) + 0.07 * Math.sin(TAU * (u * 2.3 - t * 0.33) + 1.3);
}

/** Everything below the rainbow's surface, lowered by `drop`. */
function wavePath(t: number, drop: number) {
  'worklet';
  const x0 = X - SPILL;
  const x1 = X + WIDTH + SPILL;
  const path = Skia.Path.Make();
  path.moveTo(x0, Y + HEIGHT + SPILL);
  for (let i = 0; i <= STEPS; i++) {
    const x = x0 + ((x1 - x0) * i) / STEPS;
    path.lineTo(x, Y + HEIGHT * waveLevel((x - X) / WIDTH, t) + drop);
  }
  path.lineTo(x1, Y + HEIGHT + SPILL);
  path.close();
  return path;
}

/** Where the blue meets the red at `v` (0–1 down the pill), as a fraction of the width. */
function seamLevel(v: number, t: number) {
  'worklet';
  return 0.46 + 0.05 * Math.sin(TAU * (v * 0.9 + t * 0.27)) + 0.03 * Math.sin(TAU * (v * 1.7 - t * 0.41) + 1);
}

/** The seam between blue and red; `closed` adds everything to its right (the red side). */
function seamPath(t: number, closed: boolean) {
  'worklet';
  const y0 = Y - SPILL;
  const y1 = Y + HEIGHT + SPILL;
  const path = Skia.Path.Make();
  for (let i = 0; i <= STEPS; i++) {
    const y = y0 + ((y1 - y0) * i) / STEPS;
    const x = X + WIDTH * seamLevel((y - Y) / HEIGHT, t);
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
  if (closed) {
    path.lineTo(X + WIDTH + SPILL, y1);
    path.lineTo(X + WIDTH + SPILL, y0);
    path.close();
  }
  return path;
}

function Rainbow({ clock, opacity }: { clock: SharedValue<number>; opacity: SharedValue<number> }) {
  const body = useDerivedValue(() => wavePath(clock.get(), 0));
  const fog = useDerivedValue(() => wavePath(clock.get(), HEIGHT * 0.2));
  // The colours drift a little sideways under the surface.
  const start = useDerivedValue(() => vec(X + 14 * Math.sin(clock.get() * 0.6), 0));
  const end = useDerivedValue(() => vec(X + WIDTH + 14 * Math.sin(clock.get() * 0.6), 0));
  return (
    <Group layer={<Paint opacity={opacity} />}>
      <Rect x={X} y={Y} width={WIDTH} height={HEIGHT}>
        <LinearGradient start={vec(0, Y)} end={vec(0, Y + HEIGHT)} colors={['#07031c', '#0e0632']} />
      </Rect>
      <Group layer={<Paint><Blur blur={5} /></Paint>}>
        <Path path={body}>
          <LinearGradient start={start} end={end} colors={RAINBOW} positions={RAINBOW_STOPS} />
        </Path>
      </Group>
      <Group layer={<Paint><Blur blur={8} /></Paint>}>
        <Path path={fog}>
          <LinearGradient
            start={vec(0, Y + HEIGHT * 0.55)}
            end={vec(0, Y + HEIGHT)}
            colors={['rgba(190,190,198,0)', 'rgba(190,190,198,0.7)', 'rgba(226,230,229,1)']}
            positions={[0, 0.6, 1]}
          />
        </Path>
      </Group>
    </Group>
  );
}

function Flute({ index, clock }: { index: number; clock: SharedValue<number> }) {
  const w = WIDTH / FLUTES;
  const x = X + w * index;
  const shift = FLUTE_SHIFT[index] * BAND_PERIOD;
  const start = useDerivedValue(() => {
    const s = ((clock.get() / 3.8) % 1) * BAND_PERIOD + shift;
    return vec(X + WIDTH / 2 + BAND_NX * s, Y + HEIGHT / 2 + BAND_NY * s);
  });
  const end = useDerivedValue(() => {
    const s = ((clock.get() / 3.8) % 1) * BAND_PERIOD + shift + BAND_PERIOD;
    return vec(X + WIDTH / 2 + BAND_NX * s, Y + HEIGHT / 2 + BAND_NY * s);
  });
  return (
    <>
      {/* Half a point of overlap hides the seam between strips. */}
      <Rect x={x} y={Y} width={w + 0.5} height={HEIGHT}>
        <LinearGradient start={start} end={end} colors={SILVER} positions={SILVER_STOPS} mode="repeat" />
      </Rect>
      <Rect x={x} y={Y} width={w} height={HEIGHT} color={`rgba(255,255,255,${FLUTE_TINT[index]})`} />
    </>
  );
}

function Silver({ clock, opacity }: { clock: SharedValue<number>; opacity: SharedValue<number> }) {
  return (
    <Group layer={<Paint opacity={opacity} />}>
      {FLUTE_SHIFT.map((_, i) => (
        <Flute key={i} index={i} clock={clock} />
      ))}
    </Group>
  );
}

function BlueRed({ clock, opacity }: { clock: SharedValue<number>; opacity: SharedValue<number> }) {
  const red = useDerivedValue(() => seamPath(clock.get(), true));
  const seam = useDerivedValue(() => seamPath(clock.get(), false));
  return (
    <Group layer={<Paint opacity={opacity} />}>
      <Rect x={X} y={Y} width={WIDTH} height={HEIGHT}>
        <LinearGradient
          start={vec(X, 0)}
          end={vec(X + WIDTH, 0)}
          colors={['#1b3fd0', '#1a8fe6', '#14b6dc', '#19b0d8']}
          positions={[0, 0.14, 0.38, 1]}
        />
      </Rect>
      <Group layer={<Paint><Blur blur={4} /></Paint>}>
        <Path path={red}>
          <LinearGradient
            start={vec(X + WIDTH * 0.42, 0)}
            end={vec(X + WIDTH, 0)}
            colors={['#ea5a50', '#de3a32', '#d41a1a', '#cf1717']}
            positions={[0, 0.3, 0.7, 1]}
          />
        </Path>
      </Group>
      <Path path={seam} style="stroke" strokeWidth={5} color="rgba(255,150,205,0.55)">
        <Blur blur={5} />
      </Path>
    </Group>
  );
}

function Surface({ clock, fades }: { clock: SharedValue<number>; fades: SharedValue<number>[] }) {
  const pill = rrect(rect(X, Y, WIDTH, HEIGHT), R, R);
  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      <Group clip={pill}>
        {/* Keeps the pill opaque half-way through a cross-fade. */}
        <Rect x={X} y={Y} width={WIDTH} height={HEIGHT} color="#0b0b12" />
        <Rainbow clock={clock} opacity={fades[0]} />
        <Silver clock={clock} opacity={fades[1]} />
        <BlueRed clock={clock} opacity={fades[2]} />
        {/* Glass: a soft light hugging the inside of the edge, strongest along the bottom. */}
        <RoundedRect x={X} y={Y} width={WIDTH} height={HEIGHT} r={R} style="stroke" strokeWidth={7}>
          <LinearGradient
            start={vec(0, Y)}
            end={vec(0, Y + HEIGHT)}
            colors={['rgba(255,255,255,0.1)', 'rgba(255,255,255,0.04)', 'rgba(255,255,255,0.4)']}
            positions={[0, 0.5, 1]}
          />
          <Blur blur={3.5} />
        </RoundedRect>
      </Group>
      <RoundedRect x={X + 0.5} y={Y + 0.5} width={WIDTH - 1} height={HEIGHT - 1} r={R - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient
          start={vec(0, Y)}
          end={vec(0, Y + HEIGHT)}
          colors={['rgba(255,255,255,0.42)', 'rgba(255,255,255,0.16)', 'rgba(255,255,255,0.7)']}
          positions={[0, 0.5, 1]}
        />
      </RoundedRect>
    </Canvas>
  );
}

function FadeLabel({ text, opacity }: { text: string; opacity: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  return (
    <Animated.Text style={[styles.label, style]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

/**
 * AI status pill. Its label steps through "Thinking…", "Searching…", "Planning…" and each phase
 * fills the pill with its own flowing liquid (rainbow wave, fluted silver, blue meeting red) under
 * a glassy rim.
 */
export function ThinkingButton({
  labels = ['Thinking...', 'Searching...', 'Planning...'],
  phase,
  onPhaseChange,
  onPress,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [own, setOwn] = useState(0);
  const current = (((phase ?? own) % 3) + 3) % 3;

  const clock = useSharedValue(STILL_TIME);
  const fade0 = useSharedValue(1);
  const fade1 = useSharedValue(0);
  const fade2 = useSharedValue(0);

  // The liquid always flows; only reduced motion stops it.
  const flow = useFrameCallback((info) => {
    clock.set(clock.get() + (info.timeSincePreviousFrame ?? 0) / 1000);
  }, false);
  useEffect(() => {
    flow.setActive(!reducedMotion);
    return () => flow.setActive(false);
  }, [flow, reducedMotion]);

  useEffect(() => {
    // Plain cross-fades between looks are kept with reduced motion.
    const config = { duration: FADE_MS, reduceMotion: ReduceMotion.Never };
    fade0.set(withTiming(current === 0 ? 1 : 0, config));
    fade1.set(withTiming(current === 1 ? 1 : 0, config));
    fade2.set(withTiming(current === 2 ? 1 : 0, config));
  }, [current, fade0, fade1, fade2]);

  const advance = () => {
    const next = (current + 1) % 3;
    if (phase === undefined) setOwn(next);
    onPhaseChange?.(next);
  };

  // Depending on `current` restarts the wait after a tap, so a tap never gets cut short.
  useEffect(() => {
    if (!autoPlay) return;
    const timer = setTimeout(() => {
      const next = (current + 1) % 3;
      if (phase === undefined) setOwn(next);
      onPhaseChange?.(next);
    }, PHASE_MS);
    return () => clearTimeout(timer);
  }, [autoPlay, current, phase, onPhaseChange]);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={labels[current]}
      onPress={() => {
        onPress?.();
        advance();
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
          <Surface clock={clock} fades={[fade0, fade1, fade2]} />
        </View>
        <FadeLabel text={labels[0]} opacity={fade0} />
        <FadeLabel text={labels[1]} opacity={fade1} />
        <FadeLabel text={labels[2]} opacity={fade2} />
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
    alignItems: 'center',
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
    fontSize: 22,
    fontWeight: '500',
    letterSpacing: 0.1,
    textShadowColor: 'rgba(0,0,0,0.28)',
    textShadowRadius: 4,
    textShadowOffset: { width: 0, height: 1 },
  },
});
