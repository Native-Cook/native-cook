import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Variant = 'light' | 'dark';

type Props = {
  /** Controlled share of the charge shown in the reference, 0…1: at 1 the capsules show `levels`. */
  progress?: number;
  /** Charges the three capsules one after another up to `levels`, holds, and starts over. */
  autoPlay?: boolean;
  variant?: Variant;
  owner?: string;
  device?: string;
  mode?: string;
  /** Charge of the left bud, the case and the right bud, 0…1. */
  levels?: [number, number, number];
};

const SIZE = 164;
const RADIUS = 26;
// The canvas overflows the widget by this much on every side to fit its shadow.
const BLEED = 24;

const CAP_X = 8.5;
const CAP_STEP = 50.85;
const CAP_W = 44.8;
const CAP_Y = 78.8;
const CAP_H = 76.2;
const CAP_R = 9.4;
const DISC_Y = 132.8;
const DISC_R = 16.25;
const CAST_X = 140.7;
const CAST_Y = 23.3;

// Where in the overall progress each capsule starts charging, and the share it takes.
const DELAY = [0, 0.14, 0.28];
const SPAN = 0.72;
// At or below this charge a capsule is "low" and its fill breathes.
const LOW = 0.1;

// The reference shows every capsule at its level; the autoPlay loop built around it (seconds).
const REST = 1;
const START = 0.4;
const DRAW = 2;
const HOLD = 4.4;
const RESET = 0.6;
const LOOP = START + DRAW + HOLD + RESET;
// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
const REST_T = START + DRAW + 1.6;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// Glyphs, drawn around the centre of their disc.
const BUD_HEAD = 'M-5.5 -3.6A3.7 3.7 0 1 1 1.9 -3.6A3.7 3.7 0 1 1 -5.5 -3.6Z';
const BUD_STEM = 'M-0.9 -2L1.5 -2L1.5 6.3A1.2 1.2 0 0 1 -0.9 6.3Z';
const CASE_BODY = 'M-3.6 -5.3L3.6 -5.3A3.4 3.4 0 0 1 7 -1.9L7 1.9A3.4 3.4 0 0 1 3.6 5.3L-3.6 5.3A3.4 3.4 0 0 1 -7 1.9L-7 -1.9A3.4 3.4 0 0 1 -3.6 -5.3Z';
const CASE_SEAM = 'M-7 -1.3L7 -1.3';
const CAST_TRIANGLE = 'M0 2.6L5.9 8.6L-5.9 8.6Z';

/** An arc of the "playing to" glyph: open at the bottom, running over the top. */
function castArc(r: number) {
  const a = (40 * Math.PI) / 180;
  const x = r * Math.cos(a);
  const y = 1.6 + r * Math.sin(a);
  return `M${(-x).toFixed(2)} ${y.toFixed(2)}A${r} ${r} 0 1 1 ${x.toFixed(2)} ${y.toFixed(2)}`;
}
const CAST_ARCS = [castArc(3.5), castArc(6), castArc(8.5)];

type Palette = {
  card: [string, string];
  shadow: string;
  track: string;
  ink: string;
  dim: string;
  fills: [string, string, string];
  discs: [string, string, string];
  glyph: string;
  glow: number;
  sheen: number;
};

const PALETTES: Record<Variant, Palette> = {
  light: {
    card: ['#d8dee8', '#c9d1dd'],
    shadow: 'rgba(20,30,50,0.22)',
    track: '#bcc4d0',
    ink: '#0b0b0c',
    dim: '#5f7894',
    fills: ['#00d455', '#ffcb05', '#ff4245'],
    discs: ['#e8f8ee', '#fbf8ea', '#fbefef'],
    glyph: '#0b0b0c',
    glow: 0.28,
    sheen: 0.07,
  },
  dark: {
    card: ['#1d232b', '#11151a'],
    shadow: 'rgba(0,0,0,0.4)',
    track: '#343a43',
    ink: '#ffffff',
    dim: '#7d858f',
    fills: ['#06e23f', '#ffd60a', '#ff3b3b'],
    discs: ['#06350f', '#2a2609', '#2b1719'],
    glyph: '#ffffff',
    glow: 0.55,
    sheen: 0.1,
  },
};

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

/** Overall progress at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < START + DRAW) return (t - START) / DRAW;
  if (t < START + DRAW + HOLD) return 1;
  return 1 - easeInOutCubic((t - START - DRAW - HOLD) / RESET);
}

/** 0…1: how far capsule `index` has charged at overall progress `p`. They start one after another. */
function chargeAt(p: number, index: number) {
  'worklet';
  return easeOutCubic((clamp01(p) - DELAY[index]) / SPAN);
}

/** Share of the capsule's height the fill covers for a charge `v` (0…1), as measured on the reference. */
function fillShare(v: number) {
  'worklet';
  return clamp01(v * 20) * (0.08 + 0.8 * v);
}

/** Scale of a disc after its capsule lands: a quick overshoot. */
function pop(since: number, ambient: number) {
  'worklet';
  const u = clamp01(since / 0.5);
  return 1 + ambient * 0.22 * Math.sin(Math.PI * u) * (1 - u);
}

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  /** When each capsule last reached its level, on the `time` axis. */
  landed: [SharedValue<number>, SharedValue<number>, SharedValue<number>];
  /** 0 with reduced motion: no pulses, breathing or pops. */
  ambient: number;
};

function Capsule({ index, level, colors, value, time, landed, ambient }: Motion & { index: number; level: number; colors: Palette }) {
  const x = BLEED + CAP_X + CAP_STEP * index;
  const y = BLEED + CAP_Y;
  const cx = x + CAP_W / 2;
  const tone = colors.fills[index];
  const clip = { rect: { x, y, width: CAP_W, height: CAP_H }, rx: CAP_R, ry: CAP_R };

  const charge = useDerivedValue(() => level * chargeAt(value.get(), index));
  const height = useDerivedValue(() => CAP_H * fillShare(charge.get()));
  const top = useDerivedValue(() => y + CAP_H - height.get());
  // A low capsule breathes like a warning light.
  const breath = useDerivedValue(() => (level <= LOW ? 1 - ambient * 0.28 * (0.5 + 0.5 * Math.sin(time.get() * 4.2)) : 1));
  const fillOpacity = useDerivedValue(() => clamp01(charge.get() * 20) * breath.get());
  const glowOpacity = useDerivedValue(() => colors.glow * fillOpacity.get() * (0.8 + ambient * 0.2 * Math.sin(time.get() * 2 + index)));
  const glowHeight = useDerivedValue(() => height.get() + 2);
  const sheenStart = useDerivedValue(() => vec(0, top.get()));
  // A band of light climbs through the fill, as if charge were flowing in.
  const pulse = useDerivedValue(() => ((time.get() + index * 0.45) % 2.6) / 1.1);
  const pulseY = useDerivedValue(() => y + CAP_H + 6 - (height.get() + 12) * clamp01(pulse.get()));
  const pulseOpacity = useDerivedValue(() => {
    const s = pulse.get();
    return s >= 1 ? 0 : ambient * 0.5 * Math.sin(Math.PI * s) * clamp01(height.get() / 26);
  });
  const disc = useDerivedValue(() => [
    { translateX: cx },
    { translateY: BLEED + DISC_Y },
    { scale: pop(time.get() - landed[index].get(), ambient) },
  ]);

  return (
    <>
      <RoundedRect x={x - 1} y={top} width={CAP_W + 2} height={glowHeight} r={CAP_R} color={tone} opacity={glowOpacity}>
        <BlurMask blur={7} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={CAP_W} height={CAP_H} r={CAP_R} color={colors.track} />
      <Group clip={clip}>
        {/* Blurred so the top edge of the charge is soft, as in the reference. */}
        <Rect x={x - 10} y={top} width={CAP_W + 20} height={CAP_H + 12} color={tone} opacity={fillOpacity}>
          <BlurMask blur={3.2} style="normal" />
        </Rect>
        <Rect x={x} y={top} width={CAP_W} height={CAP_H} opacity={fillOpacity}>
          <LinearGradient
            start={sheenStart}
            end={vec(0, y + CAP_H)}
            colors={['rgba(255,255,255,0)', `rgba(255,255,255,${colors.sheen})`, 'rgba(0,0,0,0.1)']}
            positions={[0, 0.16, 1]}
          />
        </Rect>
        <Rect x={x - 6} y={pulseY} width={CAP_W + 12} height={9} color="#ffffff" opacity={pulseOpacity}>
          <BlurMask blur={5} style="normal" />
        </Rect>
      </Group>
      <Group transform={disc}>
        <Circle cx={0} cy={0} r={DISC_R} color={colors.discs[index]} />
        {index === 1 ? (
          <>
            <Path path={CASE_BODY} style="stroke" strokeWidth={1.5} strokeJoin="round" color={colors.glyph} />
            <Path path={CASE_SEAM} style="stroke" strokeWidth={1.3} color={colors.glyph} />
            <Circle cx={0} cy={1.9} r={0.95} color={colors.glyph} />
          </>
        ) : (
          <Group transform={[{ scaleX: index === 0 ? 1 : -1 }]}>
            <Path path={BUD_HEAD} color={colors.glyph} />
            <Path path={BUD_STEM} color={colors.glyph} />
            {/* The ear tip, with its opening. */}
            <Circle cx={2.7} cy={-1.5} r={2.2} color={colors.glyph} />
            <Circle cx={2.9} cy={-1.4} r={1} color={colors.discs[index]} />
          </Group>
        )}
      </Group>
    </>
  );
}

function CastArc({ index, colors, time, ambient }: { index: number; colors: Palette; time: SharedValue<number>; ambient: number }) {
  // The arcs light one after another, inside out, like sound leaving the triangle.
  const opacity = useDerivedValue(() => 1 - ambient * 0.62 * Math.pow(0.5 + 0.5 * Math.sin(time.get() * 3.2 - index * 1.25 + 2.4), 2));
  return <Path path={CAST_ARCS[index]} style="stroke" strokeWidth={1.25} strokeCap="round" color={colors.ink} opacity={opacity} />;
}

function Surface({ levels, colors, ...motion }: Motion & { levels: [number, number, number]; colors: Palette }) {
  const x = BLEED;
  const y = BLEED;
  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 8} y={y + 14} width={SIZE - 16} height={SIZE - 10} r={RADIUS} color={colors.shadow}>
        <BlurMask blur={12} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={SIZE} height={SIZE} r={RADIUS}>
        <LinearGradient start={vec(x, y)} end={vec(x + SIZE, y + SIZE)} colors={colors.card} />
      </RoundedRect>
      {[0, 1, 2].map((index) => (
        <Capsule key={index} index={index} level={levels[index]} colors={colors} {...motion} />
      ))}
      <Group transform={[{ translateX: x + CAST_X }, { translateY: y + CAST_Y }]}>
        {[0, 1, 2].map((index) => (
          <CastArc key={index} index={index} colors={colors} time={motion.time} ambient={motion.ambient} />
        ))}
        <Path path={CAST_TRIANGLE} style="stroke" strokeWidth={3.4} strokeJoin="round" color={colors.card[0]} />
        <Path path={CAST_TRIANGLE} color={colors.ink} />
        <Path path={CAST_TRIANGLE} style="stroke" strokeWidth={1} strokeJoin="round" color={colors.ink} />
      </Group>
    </Canvas>
  );
}

/** The percentage above a capsule: counts with its charge. */
function Label({
  index,
  level,
  color,
  value,
  initial,
}: {
  index: number;
  level: number;
  color: string;
  value: SharedValue<number>;
  initial: number;
}) {
  const [shown, setShown] = useState(() => Math.round(level * 100 * chargeAt(initial, index)));
  useAnimatedReaction(
    () => Math.round(level * 100 * chargeAt(value.get(), index)),
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );
  return (
    <Text style={[styles.label, { left: CAP_X + CAP_STEP * index, color }]} numberOfLines={1}>
      {shown}%
    </Text>
  );
}

/**
 * Earbuds battery widget, light or dark: the device name and mode over three capsules for the left
 * bud, the case and the right bud. The capsules charge one after another with a soft glowing
 * edge, a band of light keeps climbing through each fill, the low one breathes like a warning
 * light, the icon discs pop as their capsule lands and the arcs of the output glyph ripple.
 */
export function BatteryCapsules({
  progress,
  autoPlay = false,
  variant = 'light',
  owner = 'Alex’s',
  device = 'Buds Pro',
  mode = 'Noise Cancellation',
  levels = [0.8, 0.2, 0.1],
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const target = controlled ? Math.min(1, Math.max(0, progress)) : REST;
  const colors = PALETTES[variant];

  // Seconds: `time` never stops (ambient effects), `clock` is the position in the autoPlay loop.
  const time = useSharedValue(0);
  const clock = useSharedValue(autoPlay && !controlled && !reducedMotion ? 0 : REST_T);
  const playing = useSharedValue(false);
  const looping = useSharedValue(false);
  const held = useSharedValue(target);
  const before = useSharedValue(target);
  const landed0 = useSharedValue(-10);
  const landed1 = useSharedValue(-10);
  const landed2 = useSharedValue(-10);

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
  // Plain copy of the starting value: children seed their text from it instead of reading a
  // shared value while rendering.
  const initialValue = controlled ? target : loopValue(autoPlay && !reducedMotion ? 0 : REST_T);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
    const now = value.get();
    const was = before.get();
    before.set(now);
    // A capsule reaching its level sets off its disc's pop.
    if (chargeAt(was, 0) < 0.985 && chargeAt(now, 0) >= 0.985) landed0.set(time.get());
    if (chargeAt(was, 1) < 0.985 && chargeAt(now, 1) >= 0.985) landed1.set(time.get());
    if (chargeAt(was, 2) < 0.985 && chargeAt(now, 2) >= 0.985) landed2.set(time.get());
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

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const motion: Motion = { value, time, landed: [landed0, landed1, landed2], ambient: reducedMotion ? 0 : 1 };
  const percents = levels.map((level) => `${Math.round(level * target * 100)}%`).join(', ');

  return (
    <Pressable
      onPress={restart}
      style={styles.widget}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={`${owner} ${device}, ${mode}`}
      accessibilityValue={{ text: `Left, case, right: ${percents}` }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface levels={levels} colors={colors} {...motion} />
      </View>
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Text style={[styles.name, { color: colors.ink }]} numberOfLines={2}>
          {owner}
          {'\n'}
          {device}
        </Text>
        <Text style={[styles.mode, { color: colors.dim }]} numberOfLines={1}>
          {mode}
        </Text>
        {[0, 1, 2].map((index) => (
          <Label key={index} index={index} level={levels[index]} color={colors.ink} value={value} initial={initialValue} />
        ))}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  widget: {
    width: SIZE,
    height: SIZE,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: SIZE + BLEED * 2,
    height: SIZE + BLEED * 2,
  },
  name: {
    position: 'absolute',
    left: 13,
    top: 10.4,
    width: 112,
    fontSize: 13.6,
    lineHeight: 19.3,
    fontWeight: '600',
    letterSpacing: -0.1,
  },
  mode: {
    position: 'absolute',
    left: 13,
    top: 52.1,
    width: 146,
    fontSize: 12.8,
    lineHeight: 17,
    fontWeight: '400',
    letterSpacing: -0.1,
  },
  label: {
    position: 'absolute',
    top: 84.2,
    width: CAP_W,
    textAlign: 'center',
    fontSize: 12.6,
    lineHeight: 16,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
});
