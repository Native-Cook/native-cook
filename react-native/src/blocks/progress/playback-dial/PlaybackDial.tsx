import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  Line,
  LinearGradient,
  Paint,
  Path,
  RadialGradient,
  Rect,
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
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Controlled playback position, 0…1 of the recording. Leave undefined to show the reference's frame (or the autoPlay loop). */
  progress?: number;
  /** Plays by itself: the dial scrubs from the start to the reference's position, holds, rewinds and starts over. */
  autoPlay?: boolean;
  title?: string;
  author?: string;
  updated?: string;
  /** Length of the recording in seconds; the minute hand and the time label are derived from it. */
  duration?: number;
  onPlayPress?: () => void;
};

const WIDTH = 330;
const HEIGHT = 250;
const RADIUS = 10;
// The canvases overflow the card by this much on every side to fit the dial's shadow and ripple.
const BLEED = 30;

const CX = 165;
const CY = 127;
const R = 88;
const R_FACE = 77.7;
const R_WEDGE = 58;
const R_HAND = 41.7;
const R_HUB = 8.15;

const PILL_X = 24.5;
const PILL_Y = 70.4;
const PILL_W = 273;
const PILL_H = 31.6;
const PILL_CY = PILL_Y + PILL_H / 2;
const WAVE_X = 57.4;
const WAVE_PITCH = 2.8;
const WAVE_BARS = 66;
const WAVE_HEIGHTS = [3.6, 3.6, 5.4, 3.6, 13.5, 3.6, 9, 15, 15, 12, 6, 10, 4, 14, 8, 5];

// The reference's frame: 16:30 into a 32:42 recording. The minute hand reads the elapsed time on
// a 60 minute face; the wedge is a faster sweep geared to land on 12 o'clock at that position.
const REST = 990 / 1962;
const SWEEP_TURNS = 2 / REST;
const TAIL = 64;

// The autoPlay loop built around it (seconds).
const START = 0.5;
const RUN = 2.8;
const HOLD = 4.2;
const RESET = 1.0;
const LOOP = START + RUN + HOLD + RESET;
const LANDED = START + RUN;
// A moment in the loop where everything is at rest: used when nothing is playing.
const REST_T = LANDED + 1.8;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const RAD = Math.PI / 180;

// Stars on the dial face, relative to its centre: [x, y, radius, twinkle group].
const STARS: [number, number, number, number][] = [
  [-35.5, -34, 0.6, 0], [-31.7, -20.8, 0.7, 1], [37.4, -34, 0.6, 2], [34, -20.8, 0.7, 0],
  [-63, -29.3, 0.9, 1], [65, -29.3, 0.9, 2], [-58, 23.9, 1.1, 2], [60, 23.9, 1.1, 0],
  [-27.2, 37.8, 0.6, 0], [29.4, 37.8, 0.6, 1], [-4, 43.5, 0.5, 2], [5.7, 43.5, 0.5, 1],
  [-46, 63.4, 1, 1], [48, 63.4, 1, 2], [-61.6, 16.3, 0.4, 0], [63.4, 16.3, 0.4, 1],
];
// Dust at the foot of the card, in card coordinates.
const DUST: [number, number, number, number][] = [
  [44.7, 184, 0.6, 0], [50.7, 200.8, 0.5, 1], [61.3, 200.8, 0.4, 2], [97.5, 210, 0.5, 0],
  [235, 210, 0.5, 1], [269.7, 200.8, 0.5, 2], [281.8, 200, 0.6, 0], [287.8, 184, 0.6, 1],
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeInOutCubic(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** Playback position at `t` seconds into the autoPlay loop. */
function loopValue(t: number) {
  'worklet';
  if (t < START) return 0;
  if (t < LANDED) return REST * easeInOutCubic((t - START) / RUN);
  if (t < LOOP - RESET) return REST;
  return REST * (1 - easeInOutCubic((t - (LOOP - RESET)) / RESET));
}

/** 0…1: how fast the dial is turning at `t` seconds into the loop. */
function loopActivity(t: number) {
  'worklet';
  if (t >= LOOP - RESET) return Math.sin(Math.PI * clamp01((t - (LOOP - RESET)) / RESET));
  const u = (t - START) / RUN;
  return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
}

function polar(radius: number, degrees: number) {
  const a = (degrees - 90) * RAD;
  return `${(CX + radius * Math.cos(a)).toFixed(2)} ${(CY + radius * Math.sin(a)).toFixed(2)}`;
}

/** Radial tick marks every `step` degrees between two radii; `skip` leaves out every n-th one. */
function ticks(step: number, from: number, to: number, keep: (index: number) => boolean) {
  let d = '';
  for (let i = 0; i * step < 360; i++) {
    if (keep(i)) d += `M${polar(from, i * step)}L${polar(to, i * step)}`;
  }
  return d;
}

const MAJOR_TICKS = ticks(45, 62, 67, () => true);
const MINOR_TICKS = ticks(9, 68.5, 72.5, (i) => i % 5 !== 0);

function dots(points: [number, number, number, number][], group: number, dx: number, dy: number) {
  let d = '';
  for (const [x, y, r, g] of points) {
    if (g !== group) continue;
    d += `M${(dx + x - r).toFixed(2)} ${(dy + y).toFixed(2)}a${r} ${r} 0 1 0 ${r * 2} 0a${r} ${r} 0 1 0 ${-r * 2} 0Z`;
  }
  return d;
}

const STAR_PATHS = [0, 1, 2].map((g) => dots(STARS, g, CX, CY));
const DUST_PATHS = [0, 1, 2].map((g) => dots(DUST, g, 0, 0));

function waveform() {
  let d = '';
  for (let i = 0; i < WAVE_BARS; i++) {
    const h = WAVE_HEIGHTS[i % WAVE_HEIGHTS.length];
    d += `M${(WAVE_X + i * WAVE_PITCH).toFixed(2)} ${(PILL_CY - h / 2).toFixed(2)}v${h}`;
  }
  return d;
}

const WAVEFORM = waveform();
// A generic "sound" glyph under the hub: three short bars.
const GLYPH = `M${CX - 4.5} ${CY + 23}v4M${CX} ${CY + 20.5}v9M${CX + 4.5} ${CY + 23}v4`;

type Motion = {
  value: SharedValue<number>;
  time: SharedValue<number>;
  activity: SharedValue<number>;
  /** Seconds since the dial landed on its value; large when nothing happened. */
  landed: SharedValue<number>;
  /** 0 with reduced motion: no twinkle, no breathing. */
  ambient: number;
  /** Minutes on the face for a full recording. */
  minutes: number;
};

/** Brightness of twinkle group `group` at `time`. */
function twinkle(time: number, group: number, ambient: number) {
  'worklet';
  return 0.62 + 0.38 * ambient * Math.sin(time * (1.5 + 0.35 * group) + group * 2.1);
}

/** The note card behind the text: surface, recording pill with its waveform, skeleton lines and dust. */
function Card({ value, time, ambient }: Pick<Motion, 'value' | 'time' | 'ambient'>) {
  const played = useDerivedValue(() => ({
    x: WAVE_X - 1,
    y: PILL_Y,
    width: clamp01(value.get()) * (WAVE_BARS - 1) * WAVE_PITCH + 1.6,
    height: PILL_H,
  }));
  const dust0 = useDerivedValue(() => 0.8 * twinkle(time.get(), 0, ambient));
  const dust1 = useDerivedValue(() => 0.8 * twinkle(time.get(), 1, ambient));
  const dust2 = useDerivedValue(() => 0.8 * twinkle(time.get(), 2, ambient));
  const dust = [dust0, dust1, dust2];

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        {/* The card dissolves towards its foot: everything in this layer is masked by the gradient at the end. */}
        <Group layer>
          <RoundedRect x={0} y={0} width={WIDTH} height={HEIGHT + 20} r={RADIUS}>
            <LinearGradient start={vec(0, 0)} end={vec(WIDTH * 0.55, HEIGHT)} colors={['#2e2e3d', '#18181d', '#0e0e11']} positions={[0, 0.45, 1]} />
          </RoundedRect>
          <RoundedRect x={0.5} y={0.5} width={WIDTH - 1} height={HEIGHT + 19} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
            <LinearGradient start={vec(0, 0)} end={vec(WIDTH, HEIGHT * 0.7)} colors={['rgba(150,150,185,0.75)', 'rgba(255,255,255,0.12)', 'rgba(255,255,255,0.05)']} />
          </RoundedRect>

          {/* Recording pill */}
          <RoundedRect x={PILL_X} y={PILL_Y} width={PILL_W} height={PILL_H} r={PILL_H / 2} color="#2a2a2f" />
          <RoundedRect x={PILL_X + 0.35} y={PILL_Y + 0.35} width={PILL_W - 0.7} height={PILL_H - 0.7} r={PILL_H / 2} style="stroke" strokeWidth={0.7} color="rgba(255,255,255,0.16)" />
          <Path path={WAVEFORM} style="stroke" strokeWidth={1.25} strokeCap="round" color="rgba(255,255,255,0.3)" />
          <Group clip={played}>
            <Path path={WAVEFORM} style="stroke" strokeWidth={1.25} strokeCap="round" color="#f4f4f6" />
          </Group>
          {[0, 1, 2].map((i) => (
            <Circle key={i} cx={246.4 + i * 3.35} cy={PILL_CY} r={0.7} color="#77777d" />
          ))}

          {/* Skeleton lines of the summary */}
          <RoundedRect x={245.5} y={160.8} width={50.5} height={4.6} r={2.3} color="rgba(255,255,255,0.13)" />
          <RoundedRect x={24.5} y={184.5} width={205} height={4.6} r={2.3} color="rgba(255,255,255,0.11)" />
          <RoundedRect x={24.5} y={198.5} width={216} height={4.6} r={2.3} color="rgba(255,255,255,0.1)" />
          <RoundedRect x={24.5} y={225} width={186} height={4.6} r={2.3} color="rgba(255,255,255,0.08)" />
          {DUST_PATHS.map((path, i) => (
            <Path key={i} path={path} color="#ffffff" opacity={dust[i]} />
          ))}

          <Rect x={-2} y={-2} width={WIDTH + 4} height={HEIGHT + 24} blendMode="dstIn">
            <LinearGradient start={vec(0, 150)} end={vec(0, HEIGHT - 6)} colors={['#000000', 'rgba(0,0,0,0)']} />
          </Rect>
        </Group>
      </Group>
    </Canvas>
  );
}

/** The dial that floats over the card. */
function Dial({ value, time, activity, landed, ambient, minutes }: Motion) {
  const center = vec(CX, CY);

  // The sweep: a conic gradient whose seam is the bright leading edge. Its tail stretches with speed.
  const sweep = useDerivedValue(() => [{ rotate: (value.get() * SWEEP_TURNS * 360 - 90) * RAD }]);
  const tail = useDerivedValue(() => {
    const length = (TAIL + 76 * activity.get() + 3 * ambient * Math.sin(time.get() * 1.3)) / 360;
    return [0, 1 - length, 1 - 0.56 * length, 1 - 0.2 * length, 1];
  });
  // The edge flares up as the dial lands.
  const flare = useDerivedValue(() => {
    const u = landed.get() / 0.7;
    return 0.55 + 0.25 * activity.get() + (u > 0 && u < 1 ? 0.45 * (1 - u) : 0);
  });
  // The minute hand settles with a small damped wobble.
  const hand = useDerivedValue(() => {
    const u = landed.get();
    const wobble = u > 0 && u < 1.2 ? 3.2 * Math.exp(-5 * u) * Math.sin(17 * u) : 0;
    return [{ rotate: (value.get() * minutes * 6 + wobble) * RAD }];
  });
  // The lit foot of the bezel breathes, and a ring leaves the dial when it lands.
  const glow = useDerivedValue(() => 0.5 + 0.2 * ambient * Math.sin(time.get() * 1.1) + 0.3 * activity.get());
  const ripple = useDerivedValue(() => R + 1 + 18 * (1 - Math.pow(1 - clamp01(landed.get() / 0.9), 3)));
  const rippleAlpha = useDerivedValue(() => {
    const u = landed.get() / 0.9;
    return u <= 0 || u >= 1 ? 0 : 0.5 * (1 - u);
  });
  const star0 = useDerivedValue(() => twinkle(time.get(), 0, ambient));
  const star1 = useDerivedValue(() => twinkle(time.get(), 1, ambient));
  const star2 = useDerivedValue(() => twinkle(time.get(), 2, ambient));
  const stars = [star0, star1, star2];

  const face = { rect: { x: CX - R_FACE, y: CY - R_FACE, width: R_FACE * 2, height: R_FACE * 2 }, rx: R_FACE, ry: R_FACE };

  return (
    <Canvas style={styles.canvas}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        <Circle cx={CX} cy={CY + 9} r={R - 2} color="rgba(0,0,0,0.75)">
          <BlurMask blur={13} style="normal" />
        </Circle>
        {/* Glow under the lit foot of the bezel. */}
        <Circle cx={CX + 6} cy={CY + 26} r={R - 22} color="rgba(186,96,240,0.55)" opacity={glow}>
          <BlurMask blur={20} style="normal" />
        </Circle>
        <Circle cx={CX} cy={CY} r={ripple} style="stroke" strokeWidth={1.6} color="#e7c2ff" opacity={rippleAlpha}>
          <BlurMask blur={1.2} style="normal" />
        </Circle>

        {/* Bezel: grey-violet glass that lights up towards its foot. */}
        <Circle cx={CX} cy={CY} r={R}>
          <LinearGradient
            start={vec(CX - 20, CY - R)}
            end={vec(CX + 20, CY + R)}
            colors={['#56526c', '#4a4760', '#5f5488', '#a37fe6', '#e2b3fa']}
            positions={[0, 0.42, 0.66, 0.88, 1]}
          />
        </Circle>
        <Circle cx={CX} cy={CY} r={R - 0.6} style="stroke" strokeWidth={1.2}>
          <SweepGradient
            c={center}
            colors={['rgba(170,160,235,0.2)', 'rgba(255,225,255,0.85)', 'rgba(170,160,235,0.15)', 'rgba(176,166,240,0.95)', 'rgba(170,160,235,0.2)']}
            positions={[0, 0.22, 0.5, 0.86, 1]}
          />
        </Circle>

        {/* Face */}
        <Circle cx={CX} cy={CY} r={R_FACE}>
          <LinearGradient start={vec(CX, CY - R_FACE)} end={vec(CX, CY + R_FACE)} colors={['#1c1b25', '#1b1a23', '#24143a']} />
        </Circle>
        <Group clip={face}>
          <Circle cx={CX - 34} cy={CY + R_FACE + 18} r={84}>
            <RadialGradient c={vec(CX - 34, CY + R_FACE + 18)} r={84} colors={['rgba(112,74,232,0.95)', 'rgba(112,74,232,0.45)', 'rgba(112,74,232,0)']} positions={[0.2, 0.6, 1]} />
          </Circle>
          <Circle cx={CX + 32} cy={CY + R_FACE + 16} r={84}>
            <RadialGradient c={vec(CX + 32, CY + R_FACE + 16)} r={84} colors={['rgba(226,88,236,1)', 'rgba(204,76,226,0.5)', 'rgba(204,76,226,0)']} positions={[0.2, 0.58, 1]} />
          </Circle>
          {/* Shadow the bezel casts onto the face. */}
          <Circle cx={CX} cy={CY + 3} r={R_FACE + 4} style="stroke" strokeWidth={10} color="rgba(0,0,0,0.6)">
            <BlurMask blur={5} style="normal" />
          </Circle>
        </Group>
        <Circle cx={CX} cy={CY} r={R_FACE - 0.4} style="stroke" strokeWidth={0.9}>
          <LinearGradient start={vec(CX, CY - R_FACE)} end={vec(CX, CY + R_FACE)} colors={['rgba(20,20,28,0.9)', 'rgba(60,50,90,0.6)', 'rgba(246,214,255,0.95)']} positions={[0, 0.6, 1]} />
        </Circle>
        <Circle cx={CX} cy={CY} r={72.5} style="stroke" strokeWidth={0.6} color="rgba(255,255,255,0.07)" />
        <Path path={MINOR_TICKS} style="stroke" strokeWidth={0.6} strokeCap="round" color="rgba(255,255,255,0.13)" />
        <Path path={MAJOR_TICKS} style="stroke" strokeWidth={1.1} strokeCap="round" color="rgba(238,236,248,0.85)" />
        {STAR_PATHS.map((path, i) => (
          <Path key={i} path={path} color="#e9e2ff" opacity={stars[i]} />
        ))}
        <Path path={GLYPH} style="stroke" strokeWidth={2.2} strokeCap="round" color="rgba(178,164,220,0.3)" />

        {/* Sweep wedge, rotated so the gradient's seam is the leading edge. */}
        <Group transform={sweep} origin={center}>
          <Circle cx={CX} cy={CY} r={R_WEDGE}>
            <SweepGradient
              c={center}
              colors={['rgba(120,76,236,0)', 'rgba(120,76,236,0)', 'rgba(128,84,242,0.72)', 'rgba(200,140,255,1)', '#fae4ff']}
              positions={tail}
            />
          </Circle>
          {/* The wedge sinks into the face towards the hub. */}
          <Circle cx={CX} cy={CY} r={R_WEDGE}>
            <RadialGradient c={center} r={R_WEDGE} colors={['rgba(27,26,35,0.7)', 'rgba(27,26,35,0.22)', 'rgba(27,26,35,0)']} positions={[0.1, 0.5, 0.9]} />
          </Circle>
          <Group
            layer={
              <Paint opacity={flare}>
                <Blur blur={3.5} />
              </Paint>
            }
          >
            <Line p1={vec(CX + 16, CY)} p2={vec(CX + R_WEDGE, CY)} strokeWidth={3} color="#f1c9ff" />
          </Group>
          <Line p1={vec(CX + 8, CY)} p2={vec(CX + R_WEDGE, CY)} strokeWidth={0.9} color="rgba(255,255,255,0.92)" />
        </Group>

        {/* Minute hand and hub. */}
        <Group transform={hand} origin={center}>
          <Line p1={vec(CX + 0.6, CY + 1.2)} p2={vec(CX + 0.6, CY - R_HAND + 1.2)} strokeWidth={1.4} strokeCap="round" color="rgba(0,0,0,0.45)">
            <BlurMask blur={1.2} style="normal" />
          </Line>
          <Line p1={center} p2={vec(CX, CY - R_HAND)} strokeWidth={1.05} strokeCap="round" color="#dcdce4" />
        </Group>
        <Circle cx={CX + 0.5} cy={CY + 2.5} r={R_HUB + 0.5} color="rgba(0,0,0,0.5)">
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Circle cx={CX} cy={CY} r={R_HUB}>
          <RadialGradient c={vec(CX - 2.4, CY - 2.8)} r={R_HUB * 1.7} colors={['#ffffff', '#ececf0', '#b9b9c2']} positions={[0, 0.5, 1]} />
        </Circle>
      </Group>
    </Canvas>
  );
}

function PlayButton({ running, onPress }: { running: SharedValue<number>; onPress?: () => void }) {
  const [pressed, setPressed] = useState(false);
  const play = useDerivedValue(() => 1 - running.get());
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Play recording"
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      hitSlop={8}
      style={styles.play}
    >
      <Animated.View
        style={{
          transform: [{ scale: pressed ? 0.92 : 1 }],
          transitionProperty: 'transform',
          transitionDuration: 130,
          transitionTimingFunction: CSS_EASE_OUT,
        }}
      >
        <Canvas style={styles.playCanvas}>
          <Circle cx={12} cy={12} r={10.6}>
            <LinearGradient start={vec(4, 2)} end={vec(20, 22)} colors={['#8777fb', '#6654e6']} />
          </Circle>
          <Circle cx={12} cy={12} r={10.2} style="stroke" strokeWidth={0.8} color="rgba(255,255,255,0.22)" />
          {/* Play while the dial rests, pause while it runs. */}
          <Path path="M10.1 8.9L15.2 12L10.1 15.1Z" color="#ffffff" style="fill" opacity={play} />
          <Path path="M10.1 8.9L15.2 12L10.1 15.1Z" color="#ffffff" style="stroke" strokeWidth={1.1} strokeJoin="round" opacity={play} />
          <Path path="M10.2 9v6M13.8 9v6" color="#ffffff" style="stroke" strokeWidth={1.9} strokeCap="round" opacity={running} />
        </Canvas>
      </Animated.View>
    </Pressable>
  );
}

function timecode(seconds: number) {
  const s = Math.round(seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/**
 * Call-notes card with a recording row, and a glassy violet dial floating over it as the
 * recording's playback indicator: the thin hand reads the elapsed minutes and a wedge sweeps
 * round ahead of it. The wedge is a comet whose tail stretches with speed and whose leading edge
 * flares as it stops on 12 o'clock, the hand settles with a wobble, a ring leaves the bezel on
 * landing, and at rest the stars on the face twinkle while the lit foot of the bezel breathes.
 */
export function PlaybackDial({
  progress,
  autoPlay = false,
  title = 'Design Sync Call notes',
  author = 'Zach',
  updated = 'Last updated 2h ago',
  duration = 1962,
  onPlayPress,
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
    held.set(withTiming(target, { duration: reducedMotion ? 200 : 900, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }));
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
    const u = (time.get() - movedAt.get()) / 0.8;
    return u <= 0 || u >= 1 ? 0 : Math.sin(Math.PI * u);
  });
  const landed = useDerivedValue(() => {
    if (reducedMotion) return 10;
    if (!controlled) return clock.get() < LOOP - RESET ? clock.get() - LANDED : 10;
    return time.get() - movedAt.get() - 0.7;
  });
  // 1 while the dial is moving: switches the button's glyph to "pause".
  const running = useDerivedValue(() => clamp01(activity.get() * 6));

  // Elapsed time for assistive tech: refreshed every half minute of recording and when the dial lands.
  const [elapsed, setElapsed] = useState(Math.round(target * duration));
  useAnimatedReaction(
    () => Math.round(value.get() * duration),
    (now, before) => {
      if (now !== before && (now % 30 === 0 || Math.abs(now - Math.round(target * duration)) < 1)) scheduleOnRN(setElapsed, now);
    },
  );

  const restart = () => {
    if (controlled || reducedMotion) return;
    clock.set(0);
    playing.set(true);
  };

  const ambient = reducedMotion ? 0 : 1;

  return (
    <Pressable onPress={restart} accessible={false} style={styles.card}>
      <View pointerEvents="none" style={styles.surface}>
        <Card value={value} time={time} ambient={ambient} />
      </View>

      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${title}, recording playback`}
        accessibilityValue={{ text: `${timecode(elapsed)} of ${timecode(duration)}` }}
      >
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <View style={styles.avatar}>
          <Text style={styles.initial}>{author.slice(0, 1).toUpperCase()}</Text>
        </View>
        <Text style={styles.author} numberOfLines={1}>
          {author}
          <Text style={styles.updated}>{'   '}{updated}</Text>
        </Text>
        <Text style={styles.duration}>{timecode(duration)}</Text>
        <Text style={styles.heading}>Summary</Text>
        <Text style={styles.body} numberOfLines={1}>
          The team brainstormed new layouts
        </Text>
        <Text style={[styles.body, styles.bodyTail]}>and</Text>
        <Text style={[styles.body, styles.bodySecond]} numberOfLines={1}>
          aligned on what ships next.
        </Text>
      </View>

      <PlayButton running={running} onPress={onPlayPress} />

      <View pointerEvents="none" style={styles.surface}>
        <Dial value={value} time={time} activity={activity} landed={landed} ambient={ambient} minutes={duration / 60} />
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
    left: 24.5,
    right: 16,
    top: 32.3 - 11,
    color: '#f1f1f4',
    fontSize: 15.8,
    lineHeight: 22,
    fontWeight: '500',
    letterSpacing: -0.1,
  },
  avatar: {
    position: 'absolute',
    left: 25.2,
    top: 53.2 - 5,
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#e2795b',
    alignItems: 'center',
    justifyContent: 'center',
  },
  initial: {
    color: '#ffffff',
    fontSize: 5.6,
    lineHeight: 7,
    fontWeight: '700',
  },
  author: {
    position: 'absolute',
    left: 38,
    top: 53.2 - 7,
    color: '#e4e4e8',
    fontSize: 9.4,
    lineHeight: 14,
    fontWeight: '500',
    letterSpacing: 0.2,
  },
  updated: {
    color: '#77777e',
    fontWeight: '400',
  },
  duration: {
    position: 'absolute',
    left: 240,
    width: 47.5,
    top: PILL_CY - 7,
    textAlign: 'right',
    color: '#9c9ca3',
    fontSize: 9.4,
    lineHeight: 14,
    fontWeight: '400',
    letterSpacing: 0.3,
    fontVariant: ['tabular-nums'],
  },
  heading: {
    position: 'absolute',
    left: 24.5,
    top: 126.8 - 7,
    color: '#bcbcc2',
    fontSize: 9.8,
    lineHeight: 14,
    fontWeight: '600',
  },
  body: {
    position: 'absolute',
    left: 24.5,
    top: 147 - 8,
    color: '#8b8b92',
    fontSize: 10.4,
    lineHeight: 16,
    fontWeight: '400',
  },
  bodyTail: {
    left: 252,
  },
  bodySecond: {
    top: 163 - 8,
  },
  play: {
    position: 'absolute',
    left: 40.5 - 12,
    top: PILL_CY - 12,
    width: 24,
    height: 24,
  },
  playCanvas: {
    width: 24,
    height: 24,
  },
});
