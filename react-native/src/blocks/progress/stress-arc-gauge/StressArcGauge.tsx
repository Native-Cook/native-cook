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
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled stress level, 0…1. Leave undefined to show the reference's 85% (or the autoPlay loop). */
  progress?: number;
  /** Climbs to 50% ("Balanced", green), then to 85% ("High", red), holds each, and starts over. */
  autoPlay?: boolean;
  title?: string;
  caption?: string;
  /** State names for the chip: below 35%, up to 70%, and above. */
  levels?: [string, string, string];
  onOpenPress?: () => void;
};

const WIDTH = 340;
const HEIGHT = 198;
const RADIUS = 26.5;
// The canvas overflows the card by this much on every side to fit its coloured edge glow.
const BLEED = 24;

// The arc is part of a large circle whose centre lies below the card.
const ARC_CX = 165.1;
const ARC_CY = 273.1;
const ARC_R = 213.6;
// Degrees from straight up: where the stroke enters and leaves the card, and the 0% / 100% marks on it.
const ARC_FROM = -52;
const ARC_TO = 56.5;
const ZERO = -37.4;
const FULL = 42.8;
const DOT_R = 5.3;
const TAIL = 0.2;

// The reference shows 85% (red) and 50% (green); the autoPlay loop visits both (seconds).
const REST = 0.85;
const CALM = 0.5;
const BASE = 0.2;
const RISE = 1.6;
const T1 = 0.3;
const T2 = T1 + RISE + 2.3;
const T3 = T2 + RISE + 2.9;
const FALL = 0.9;
const LOOP = T3 + FALL;
// A moment in the loop where everything is at rest at 85%: used when nothing is playing.
const REST_T = T3 - 1;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// Calm, in-between and stressed colours for the stroke, the text and the ambient light.
const LINE = [[47, 255, 71], [255, 214, 60], [255, 116, 47]];
const INK = [[99, 226, 131], [246, 214, 110], [241, 128, 111]];
const LIGHT = [[38, 150, 36], [150, 120, 30], [190, 52, 30]];
const EDGE = [[90, 255, 120], [255, 214, 120], [255, 130, 110]];

function point(deg: number) {
  const a = (deg * Math.PI) / 180;
  return [ARC_CX + ARC_R * Math.sin(a), ARC_CY - ARC_R * Math.cos(a)];
}
const [AX0, AY0] = point(ARC_FROM);
const [AX1, AY1] = point(ARC_TO);
const ARC = `M${AX0} ${AY0}A${ARC_R} ${ARC_R} 0 0 1 ${AX1} ${AY1}`;

// Person holding their head, in an 18 × 18 box.
const ICON_ARMS =
  'M5.6 11.7C2.2 10.7 1.6 6.7 4.4 4.7C5.2 4.1 6 4.1 6.6 4.5M12.4 11.7C15.8 10.7 16.4 6.7 13.6 4.7C12.8 4.1 12 4.1 11.4 4.5';
const ICON_BODY = 'M5 17.6V12.5C5 11.4 5.9 10.6 7 10.6H11C12.1 10.6 13 11.4 13 12.5V17.6Z';
const ICON_RAYS = 'M9 0.2V1.8M6.2 0.9L6.9 2.2M11.8 0.9L11.1 2.2';
const ARROW = 'M1.5 9L9 1.5M3.2 1.5H9V7.3';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Stress level at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < T1) return BASE;
  if (t < T1 + RISE) return BASE + (CALM - BASE) * easeInOutCubic((t - T1) / RISE);
  if (t < T2) return CALM;
  if (t < T2 + RISE) return CALM + (REST - CALM) * easeInOutCubic((t - T2) / RISE);
  if (t < T3) return REST;
  return REST - (REST - BASE) * easeInOutCubic((t - T3) / FALL);
}

/** Fraction of the stroke (0…1 from where it enters the card) at which the marker sits. */
function markerAt(p: number) {
  'worklet';
  return (ZERO + (FULL - ZERO) * clamp01(p) - ARC_FROM) / (ARC_TO - ARC_FROM);
}

/** Calm → in-between → stressed, crossing over between 56% and 72%. */
function tint(stops: number[][], p: number) {
  'worklet';
  const t = clamp01((p - 0.56) / 0.16) * 2;
  const a = t < 1 ? stops[0] : stops[1];
  const b = t < 1 ? stops[1] : stops[2];
  const f = t < 1 ? t : t - 1;
  return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f];
}

function skia(rgb: number[], alpha: number) {
  'worklet';
  return [rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, alpha];
}

/** Heartbeat for a phase 0…1: a strong thump followed by a softer one. */
function heartbeat(phase: number) {
  'worklet';
  const a = (phase - 0.1) / 0.055;
  const b = (phase - 0.32) / 0.055;
  return Math.exp(-a * a) + 0.55 * Math.exp(-b * b);
}

type Motion = {
  value: SharedValue<number>;
  /** Phase of the heartbeat, 0…1. */
  beat: SharedValue<number>;
  /** −1…1: how fast the marker is moving and which way, smoothed. */
  tail: SharedValue<number>;
  /** 0 with reduced motion: no heartbeat, no tail. */
  ambient: number;
};

function Surface({ value, beat, tail, ambient }: Motion) {
  const x = BLEED;
  const y = BLEED;

  const clip = useMemo(() => rrect(rect(x, y, WIDTH, HEIGHT), RADIUS, RADIUS), [x, y]);

  const at = useDerivedValue(() => markerAt(value.get()));
  const pulse = useDerivedValue(() => ambient * heartbeat(beat.get()));
  const line = useDerivedValue(() => skia(tint(LINE, value.get()), 1));
  const edge = useDerivedValue(() => skia(tint(EDGE, value.get()), 0.36));

  const dot = useDerivedValue(() => {
    const a = ((ZERO + (FULL - ZERO) * clamp01(value.get())) * Math.PI) / 180;
    return [{ translateX: x + ARC_CX + ARC_R * Math.sin(a) }, { translateY: y + ARC_CY - ARC_R * Math.cos(a) }];
  });
  const dotScale = useDerivedValue(() => [{ scale: 1 + 0.16 * pulse.get() }]);
  // Each beat sends a ring out from the marker.
  const ring = useDerivedValue(() => DOT_R + 17 * clamp01((beat.get() - 0.1) / 0.75));
  const ringAlpha = useDerivedValue(() =>
    beat.get() < 0.1 ? 0 : ambient * 0.5 * (1 - clamp01((beat.get() - 0.1) / 0.75)),
  );

  // While the marker moves it drags a bright tail along the stroke, like a comet.
  const tailStart = useDerivedValue(() => clamp01(Math.min(at.get(), at.get() - TAIL * tail.get())));
  const tailEnd = useDerivedValue(() => clamp01(Math.max(at.get(), at.get() - TAIL * tail.get())));
  const tailAlpha = useDerivedValue(() => Math.min(1, Math.abs(tail.get()) * 1.4));

  // Ambient light on the right of the card: it leans towards the marker and throbs with the heartbeat.
  const light = useDerivedValue(() => {
    const a = ((ZERO + (FULL - ZERO) * clamp01(value.get())) * Math.PI) / 180;
    const mx = ARC_CX + ARC_R * Math.sin(a);
    return [{ translateX: x + 259 + 0.25 * (mx - 259) }, { translateY: y + 82 }];
  });
  const lightColors = useDerivedValue(() => {
    const rgb = tint(LIGHT, value.get());
    const strength = 0.5 + 0.14 * pulse.get();
    return [skia(rgb, strength), skia(rgb, strength * 0.45), skia(rgb, 0)];
  });
  const rays = useDerivedValue(() => 1 - 0.55 * pulse.get() * clamp01((value.get() - 0.56) / 0.16));

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 6} y={y + 8} width={WIDTH - 12} height={HEIGHT - 4} r={RADIUS} color={edge}>
        <BlurMask blur={4} style="normal" />
      </RoundedRect>

      <Group clip={clip}>
        <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS}>
          <LinearGradient
            start={vec(x, y)}
            end={vec(x + WIDTH, y + 30)}
            colors={['#222222', '#232323', '#3a3a3a', '#303030', '#373737']}
            positions={[0, 0.2, 0.47, 0.64, 1]}
          />
        </RoundedRect>
        <Group transform={light}>
          <Circle cx={0} cy={0} r={125}>
            <RadialGradient c={vec(0, 0)} r={125} colors={lightColors} positions={[0, 0.5, 1]} />
          </Circle>
        </Group>

        <Group transform={[{ translateX: x }, { translateY: y }]} style="stroke" strokeCap="round">
          {/* What is still ahead of the marker is faint, what is behind glows. */}
          <Path path={ARC} strokeWidth={1.6} color={line} opacity={0.34} start={at} end={1} />
          <Path path={ARC} strokeWidth={5} color={line} opacity={0.5} start={0} end={at}>
            <BlurMask blur={4} style="normal" />
          </Path>
          <Path path={ARC} strokeWidth={1.6} color={line} start={0} end={at} />
          <Path path={ARC} strokeWidth={5} color={line} opacity={tailAlpha} start={tailStart} end={tailEnd}>
            <BlurMask blur={3} style="normal" />
          </Path>
          <Path path={ARC} strokeWidth={2.4} color="#ffffff" opacity={tailAlpha} start={tailStart} end={tailEnd}>
            <BlurMask blur={1.2} style="normal" />
          </Path>
        </Group>

        <Group transform={dot}>
          <Circle cx={0} cy={0} r={ring} style="stroke" strokeWidth={1.2} color={line} opacity={ringAlpha} />
          <Circle cx={0} cy={0} r={DOT_R + 3} color={line} opacity={0.45}>
            <BlurMask blur={5} style="normal" />
          </Circle>
          <Group transform={dotScale}>
            <Circle cx={0} cy={0} r={DOT_R} color={line} />
          </Group>
        </Group>
      </Group>

      {/* Person holding their head; the lines above it twitch with the heartbeat when stress is high. */}
      <Group transform={[{ translateX: x + 18.8 }, { translateY: y + 14.4 }]}>
        <Path path={ICON_ARMS} style="stroke" strokeWidth={2.7} strokeCap="round" strokeJoin="round" color="#ffffff" />
        <Path path={ICON_BODY} color="#ffffff" />
        <Circle cx={9} cy={6.8} r={3.9} color="#262626" />
        <Circle cx={9} cy={6.8} r={3} color="#ffffff" />
        <Path path={ICON_RAYS} style="stroke" strokeWidth={0.9} strokeCap="round" color="#ffffff" opacity={rays} />
      </Group>
    </Canvas>
  );
}

function OpenButton({ onPress }: { onPress?: () => void }) {
  const [pressed, setPressed] = useState(false);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Open"
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      hitSlop={14}
      style={styles.openSlot}
    >
      <Animated.View
        style={{
          transform: [{ scale: pressed ? 0.97 : 1 }],
          transitionProperty: 'transform',
          transitionDuration: 130,
          transitionTimingFunction: CSS_EASE_OUT,
        }}
      >
        <Canvas style={styles.open}>
          <Path path={ARROW} style="stroke" strokeWidth={1.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
        </Canvas>
      </Animated.View>
    </Pressable>
  );
}

/**
 * Mental-health widget: a dark card crossed by a wide glowing arc with a marker on it, the stress
 * level as a large percentage and a chip naming the state. The marker drags a comet tail while it
 * moves, beats like a heart (faster as stress rises, a ring going out with each beat), and the
 * whole card shifts from calm green to stressed red as the level climbs.
 */
export function StressArcGauge({
  progress,
  autoPlay = false,
  title = 'Stress',
  caption = 'Your stress level',
  levels = ['Low', 'Balanced', 'High'],
  onOpenPress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;

  // Seconds: `clock` is the position in the autoPlay loop.
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(target);
  const beat = useSharedValue(0.6);
  const tail = useSharedValue(0);

  useEffect(() => {
    const auto = autoPlay && !controlled && !reducedMotion;
    looping.set(auto);
    playing.set(auto);
    clock.set(auto ? 0 : REST_T);
  }, [autoPlay, controlled, reducedMotion, clock, looping, playing]);

  useEffect(() => {
    if (!controlled) return;
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 900, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
  }, [controlled, target, reducedMotion, held]);

  const value = useDerivedValue(() => (controlled ? held.get() : loopValue(clock.get())));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const now = value.get();
    // The heart beats 54 times a minute when calm and up to 114 when stressed.
    beat.set((beat.get() + dt * (0.9 + now)) % 1);
    const speed = Math.max(-1, Math.min(1, (now - before.get()) / Math.max(dt, 0.001) / 0.22));
    tail.set(tail.get() + (speed - tail.get()) * Math.min(1, dt * 9));
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

  // The number and the chip are real text: re-render only when the whole percent changes.
  const [percent, setPercent] = useState(Math.round(target * 100));
  useAnimatedReaction(
    () => Math.round(value.get() * 100),
    (now, old) => {
      if (now !== old) scheduleOnRN(setPercent, now);
    },
  );
  const level = percent < 35 ? levels[0] : percent < 70 ? levels[1] : levels[2];

  // Text colour follows the whole percent too: a colour animated on the UI thread does not survive
  // the re-render that updates the digits.
  const [r, g, b] = tint(INK, percent / 100);
  const ink = { color: `rgb(${Math.round(r)},${Math.round(g)},${Math.round(b)})` };

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface value={value} beat={beat} tail={tail} ambient={reducedMotion ? 0 : 1} />
      </View>

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${title}. ${caption}`}
        accessibilityValue={{ min: 0, max: 100, now: percent, text: `${percent}%, ${level}` }}
      >
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.caption} numberOfLines={1}>
          {caption}
        </Text>
        <Text style={[styles.number, ink]}>{percent}%</Text>
        <View style={styles.chipRow}>
          <View style={styles.chip}>
            <Text style={[styles.chipLabel, ink]} numberOfLines={1}>
              {level}
            </Text>
          </View>
        </View>
      </View>

      <OpenButton onPress={onOpenPress} />
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
    left: 45.8,
    top: 13,
    color: '#ffffff',
    fontSize: 14.5,
    lineHeight: 20,
    fontWeight: '600',
  },
  openSlot: {
    position: 'absolute',
    left: 314.2,
    top: 17.7,
  },
  open: {
    width: 10.5,
    height: 10.5,
  },
  // The reference centres its text a touch left of the card's middle.
  caption: {
    position: 'absolute',
    left: 0,
    width: 337,
    top: 90.4,
    textAlign: 'center',
    color: '#f2f2f2',
    fontSize: 9.5,
    lineHeight: 14,
    fontWeight: '400',
  },
  number: {
    position: 'absolute',
    left: 0,
    width: 337,
    top: 109.8,
    textAlign: 'center',
    fontSize: 28.5,
    lineHeight: 36,
    fontWeight: '400',
    fontVariant: ['tabular-nums'],
  },
  chipRow: {
    position: 'absolute',
    left: 0,
    width: 337,
    top: 150.2,
    alignItems: 'center',
  },
  chip: {
    height: 20.2,
    paddingHorizontal: 13,
    borderRadius: 10.1,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.22)',
    backgroundColor: 'rgba(255,255,255,0.2)',
    justifyContent: 'center',
  },
  chipLabel: {
    fontSize: 10.5,
    lineHeight: 14,
    fontWeight: '600',
  },
});
