import { BlurMask, Canvas, Circle, Group, RadialGradient, RoundedRect, vec } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// The clock of a top-up, in seconds since Continue was released. The wait (`duration`) is the
// time from Continue to the moment the light gathers and leaves.

/** autoPlay: one top-up every LOOP seconds, Continue pressed at PRESS_AT for PRESS_FOR. */
export const LOOP = 8.5;
export const PRESS_AT = 0.8;
export const PRESS_FOR = 0.15;
/** After a touch autoPlay waits this long, and for the form to be idle, before it starts over. */
export const USER_HOLD = 3;

export const BLUR_IN = 0.3;
export const SWEEP = 0.9;
// The result replaces the spinner this long after the sweep starts, and holds.
export const RESULT_AFTER = 0.3;
export const RESULT_IN = 0.25;
export const RESULT_HOLD = 1.8;
export const STATUS_OUT = 0.3;
export const FORM_BACK_AFTER = 0.1;
export const FORM_BACK_IN = 0.4;
export const COUNT_AFTER = 0.15;
export const COUNT_FOR = 0.7;
// The form takes touches again this long after it started to come back.
export const SETTLE = 0.9;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

export function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

/** When the result starts to leave and the form to come back. */
export function returnAt(duration: number) {
  'worklet';
  return duration + RESULT_AFTER + RESULT_HOLD;
}

export function endAt(duration: number) {
  'worklet';
  return returnAt(duration) + SETTLE;
}

/**
 * One soft ellipse of light: x, y, rx, ry, alpha, red, green, blue. x and rx are fractions of the
 * screen's width, y and ry of its height, so the picture is the same on every phone.
 */
type Light = number[];

function light(x: number, y: number, rx: number, ry: number, alpha: number, hex: number): Light {
  return [x, y, rx, ry, alpha, (hex >> 16) & 0xff, (hex >> 8) & 0xff, hex & 0xff];
}

/**
 * A light through the wait and the sweep: where it rests, at the hottest moment, as it pales
 * before the end, gathered round the rising centre (x and y are offsets from that centre), and
 * as the last trace along the top edge. `wander` is its own slow sine: reach in x and y, speed
 * in radians a second, phase, and how much its size breathes.
 */
type Source = { rest: Light; hot: Light; pale: Light; gathered: Light; trace: Light; wander: number[] };

const SOURCES: Source[] = [
  // Deep violet in the lower left corner.
  {
    rest: light(0.14, 0.888, 0.4, 0.2, 1.0, 0x2a06b4),
    hot: light(0.16, 0.89, 0.5, 0.2, 1.0, 0x2204b6),
    pale: light(0.2, 0.89, 0.48, 0.195, 1.0, 0x4a0cd8),
    gathered: light(-0.44, 0.1, 0.54, 0.34, 1.2, 0x5c14dc),
    trace: light(0.1, -0.04, 0.42, 0.28, 0.9, 0x8f8af0),
    wander: [0.03, 0.01, 2.1, 0.0, 0.05],
  },
  // Magenta through the middle, under the bar.
  {
    rest: light(0.38, 0.965, 0.56, 0.265, 1.0, 0xe060c5),
    hot: light(0.39, 0.975, 0.57, 0.245, 1.0, 0xea8ad8),
    pale: light(0.3, 0.99, 0.42, 0.25, 1.0, 0xffd6ff),
    gathered: light(-0.15, 0.18, 0.6, 0.2, 0.85, 0xa050e0),
    trace: light(0.4, -0.04, 0.5, 0.26, 0.6, 0xb9a8e0),
    wander: [0.035, 0.008, 1.7, 1.7, 0.04],
  },
  // Peach across the right, turning orange in the middle of the wait.
  {
    rest: light(0.7, 0.9, 0.84, 0.255, 0.85, 0xffa890),
    hot: light(0.7, 0.912, 0.85, 0.266, 0.85, 0xf59b34),
    pale: light(0.72, 0.891, 0.9, 0.23, 1.0, 0xfdc88c),
    gathered: light(0.46, 0.0, 0.5, 0.32, 1.0, 0xffd49a),
    trace: light(0.88, -0.04, 0.42, 0.28, 0.9, 0xd2f6d0),
    wander: [0.03, 0.01, 1.9, 3.1, 0.05],
  },
  // The crest of the hill, which breathes the most.
  {
    rest: light(0.62, 0.795, 0.42, 0.125, 0.4, 0xf39a48),
    hot: light(0.56, 0.86, 0.62, 0.205, 0.53, 0xff4a2a),
    pale: light(0.57, 0.868, 0.51, 0.23, 0.7, 0xffb2b1),
    gathered: light(0.08, -0.14, 0.62, 0.22, 0.7, 0xffd8cc),
    trace: light(0.62, -0.04, 0.5, 0.24, 0.5, 0xdfe6e0),
    wander: [0.05, 0.012, 2.6, 4.4, 0.12],
  },
  // Ivory where the light meets the bar; it becomes the core of the blob that rises.
  {
    rest: light(0.85, 0.94, 0.5, 0.22, 0.7, 0xfff6e2),
    hot: light(0.82, 0.95, 0.38, 0.16, 0.5, 0xffd0f0),
    pale: light(0.58, 0.95, 0.7, 0.28, 1.0, 0xffc8e6),
    gathered: light(0.0, -0.02, 0.76, 0.38, 1.3, 0xfff4f8),
    trace: light(0.36, -0.04, 0.4, 0.2, 0.7, 0xd8e6fa),
    wander: [0.025, 0.006, 1.5, 2.3, 0.04],
  },
];

// How a light fades from its centre to its edge: cos², so it has no rim and no bright point.
// A light stronger than 1 is solid in the middle and fades from further out.
const FALLOFF_AT = [0, 0.2, 0.4, 0.6, 0.8, 1];
const FALLOFF = [1, 0.905, 0.655, 0.345, 0.095, 0];

// The gathered light starts at this height and leaves towards the top edge.
const RISE_FROM = 0.9;
const RISE_TO = 0.1;
const BAR_BLUR = 7;

function mix(a: Light, b: Light, t: number): Light {
  'worklet';
  const out: Light = [];
  for (let i = 0; i < 8; i++) out.push(a[i] + (b[i] - a[i]) * t);
  return out;
}

/** The light of `source` at `e` seconds. */
function lightAt(source: Source, e: number, duration: number, still: boolean): Light {
  'worklet';
  const grow = smooth(e, 0.1, 0.75);
  const heat = still ? 0 : smooth(e, 0.34 * duration, 0.66 * duration) * (1 - smooth(e, 0.74 * duration, 0.92 * duration));
  const pale = still ? 0 : smooth(e, 0.78 * duration, duration);
  const s = clamp01((e - duration) / SWEEP);
  const gather = still ? 0 : smooth(s, 0, 0.25);
  const centre = RISE_FROM + (RISE_TO - RISE_FROM) * smooth(s, 0.05, 0.5);
  const settle = still ? 0 : smooth(s, 0.4, 0.62);
  const fade = 1 - (still ? smooth(s, 0, 0.35) : smooth(s, 0.6, 1));

  let l = mix(mix(source.rest, source.hot, heat), source.pale, pale);
  if (!still) {
    const [reachX, reachY, speed, phase, pulse] = source.wander;
    l[0] += reachX * Math.sin(e * speed + phase);
    l[1] += reachY * Math.cos(e * speed * 1.3 + phase);
    const breath = 1 + pulse * Math.sin(e * speed * 1.7 + phase * 2);
    l[2] *= breath;
    l[3] *= breath;
    // It grows out of the bar: lower, narrower and flatter at first.
    l[0] = 0.5 + (l[0] - 0.5) * (0.7 + 0.3 * grow);
    l[1] += 0.07 * (1 - grow);
    l[2] *= 0.6 + 0.4 * grow;
    l[3] *= 0.45 + 0.55 * grow;
  }
  l[4] *= smooth(grow, 0, 0.6);
  const gathered = source.gathered.slice();
  gathered[0] += 0.5;
  gathered[1] += centre;
  l = mix(mix(l, gathered, gather), source.trace, settle);
  l[4] *= fade;
  return l;
}

type GlowProps = {
  source: Source;
  elapsed: SharedValue<number>;
  duration: number;
  still: boolean;
  width: number;
  height: number;
};

function Glow({ source, elapsed, duration, still, width, height }: GlowProps) {
  const state = useDerivedValue(() => lightAt(source, elapsed.get(), duration, still));
  const transform = useDerivedValue(() => {
    const l = state.get();
    return [{ translateX: l[0] * width }, { translateY: l[1] * height }, { scaleX: l[2] * width }, { scaleY: l[3] * height }];
  });
  const colors = useDerivedValue(() => {
    const l = state.get();
    const rgb = `${Math.round(l[5])},${Math.round(l[6])},${Math.round(l[7])}`;
    return FALLOFF.map((f) => `rgba(${rgb},${Math.min(1, l[4] * f)})`);
  });
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={1}>
        <RadialGradient c={vec(0, 0)} r={1} colors={colors} positions={FALLOFF_AT} />
      </Circle>
    </Group>
  );
}

type Props = {
  /** Seconds since Continue was released. */
  elapsed: SharedValue<number>;
  /** Seconds from Continue to the sweep. */
  duration: number;
  /** Reduced motion: a still glow that fades in and out. */
  still: boolean;
  width: number;
  height: number;
  /** The Continue button's frame. */
  bar: { x: number; y: number; width: number; height: number };
};

/**
 * The light of a top-up: five soft ellipses blended as screen so that where they overlap they
 * glow, and the Continue button as a blurred bar of light in front of them.
 */
export function Aurora({ elapsed, duration, still, width, height, bar }: Props) {
  const barAlpha = useDerivedValue(() => {
    const e = elapsed.get();
    const s = clamp01((e - duration) / SWEEP);
    const grow = smooth(e, 0.1, 0.75);
    const gone = still ? smooth(s, 0, 0.35) : smooth(s, 0, 0.25);
    return (0.6 - 0.2 * grow) * smooth(e, 0, 0.18) * (1 - gone);
  });
  return (
    <Canvas style={StyleSheet.absoluteFill}>
      <Group blendMode="screen">
        {SOURCES.map((source, index) => (
          <Glow key={index} source={source} elapsed={elapsed} duration={duration} still={still} width={width} height={height} />
        ))}
      </Group>
      <RoundedRect x={bar.x} y={bar.y} width={bar.width} height={bar.height} r={bar.height / 2} color="white" opacity={barAlpha}>
        <BlurMask blur={BAR_BLUR} style="normal" />
      </RoundedRect>
    </Canvas>
  );
}
