import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Mask,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  type SkPath,
  vec,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { Palette } from './NetworkMark';

export const CARD_RADIUS = 18;
// The new gradient spreads from the network's mark as a soft-edged disc, REVEAL_SOFT pt wide at the
// rim, until it covers the whole card.
const REVEAL_SOFT = 26;
// The fine wavy lines printed across the card, like the guilloche on a banknote.
const GUILLOCHE_LINES = 15;
// Brushed metal: BRUSH_LINES hairlines across the card at seeded heights, half lighter and half
// darker than the base, so the surface has a grain the light can catch.
const BRUSH_LINES = 120;
const BRUSH_SEED = 7;
// The light that crosses the card: a band SHEEN_WIDTH pt wide, leaning SHEEN_TILT°.
const SHEEN_WIDTH = 90;
const SHEEN_TILT = -22;
// The soft specular light over the card, SPECULAR × the card's width in radius. It rests above the
// top left and slides against the card's tilt (SPECULAR_TRAVEL of the card per 10° of tilt).
const SPECULAR = 0.95;
const SPECULAR_TRAVEL = 0.5;
// Chip on the front, in pt from the card's top left.
export const CHIP = { x: 22, y: 62, width: 42, height: 31 };
// Back: the magnetic stripe and the signature strip.
export const STRIPE = { y: 24, height: 42 };
export const STRIP = { x: 22, y: 92, height: 36 };
// The focus outline: corner radius, and the light that runs round it, a bright head COMET_HEAD of
// the perimeter long over a dimmer tail COMET_TAIL long.
export const OUTLINE_RADIUS = 10;
const COMET_HEAD = 0.12;
const COMET_TAIL = 0.34;

/** A tiny linear congruential generator, so the brushed grain is the same on every platform. */
function brushRandom(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

function brush(width: number, height: number) {
  const next = brushRandom(BRUSH_SEED);
  const light = Skia.PathBuilder.Make();
  const dark = Skia.PathBuilder.Make();
  for (let i = 0; i < BRUSH_LINES; i += 1) {
    const y = next() * height;
    const target = next() < 0.5 ? light : dark;
    target.moveTo(0, y).lineTo(width, y);
  }
  return { light: light.build(), dark: dark.build() };
}

function guilloche(width: number, height: number) {
  const builder = Skia.PathBuilder.Make();
  for (let k = 0; k < GUILLOCHE_LINES; k += 1) {
    const base = height * 0.3 + k * 6.5;
    for (let x = -4; x <= width + 4; x += 4) {
      const y = base + 10 * Math.sin(x / 26 + k * 0.42) + 4 * Math.sin(x / 9.5 - k * 0.8);
      if (x === -4) builder.moveTo(x, y);
      else builder.lineTo(x, y);
    }
  }
  return builder.build();
}

function chipLines() {
  const { x, y, width: w, height: h } = CHIP;
  const builder = Skia.PathBuilder.Make();
  builder.moveTo(x, y + h / 3).lineTo(x + w * 0.34, y + h / 3);
  builder.moveTo(x, y + (2 * h) / 3).lineTo(x + w * 0.34, y + (2 * h) / 3);
  builder.moveTo(x + w, y + h / 3).lineTo(x + w * 0.66, y + h / 3);
  builder.moveTo(x + w, y + (2 * h) / 3).lineTo(x + w * 0.66, y + (2 * h) / 3);
  builder.addRRect({ rect: { x: x + w * 0.34, y: y + h * 0.18, width: w * 0.32, height: h * 0.64 }, rx: 3, ry: 3 });
  return builder.build();
}

type LayerProps = { width: number; height: number; palette: SharedValue<Palette> };

/** The card's colour: a diagonal gradient and two soft pools of the palette's light colour. */
function Layer({ width, height, palette }: LayerProps) {
  const colors = useDerivedValue(() => [palette.get()[0], palette.get()[1]]);
  const glow = useDerivedValue(() => palette.get()[2]);
  return (
    <Group>
      <RoundedRect x={0} y={0} width={width} height={height} r={CARD_RADIUS}>
        <LinearGradient start={vec(0, 0)} end={vec(width, height)} colors={colors} />
      </RoundedRect>
      <Circle cx={width * 0.92} cy={-height * 0.12} r={height * 0.62} color={glow} opacity={0.26}>
        <BlurMask blur={42} style="normal" />
      </Circle>
      <Circle cx={width * 0.04} cy={height * 1.08} r={height * 0.5} color={glow} opacity={0.18}>
        <BlurMask blur={36} style="normal" />
      </Circle>
    </Group>
  );
}

/**
 * The stretch of the outline's path that ends at `u` and is `length` long (both 0…1 of the path).
 * When it crosses the path's start it comes in two parts: `part` 0 is the one ending at `u` (or the
 * one at the end of the path), `part` 1 the one at the path's beginning, empty otherwise.
 */
function trim(u: number, length: number, part: 0 | 1) {
  'worklet';
  const start = u - length;
  if (start >= 0) return part === 0 ? [start, u] : [0, 0];
  return part === 0 ? [start + 1, 1] : [0, u];
}

export type OutlineProps = {
  x: SharedValue<number>;
  y: SharedValue<number>;
  w: SharedValue<number>;
  h: SharedValue<number>;
  /** 0 hidden … 1 shown. */
  on: SharedValue<number>;
  /** Light on the card's colour; dark ink on the cream signature strip (the CVV). */
  tone?: 'light' | 'dark';
};

const TONES = {
  light: { fill: 'rgba(255,255,255,0.06)', base: 'rgba(255,255,255,0.34)', tail: 'rgba(255,255,255,0.4)', head: '#ffffff' },
  dark: { fill: 'rgba(29,35,64,0.06)', base: 'rgba(29,35,64,0.3)', tail: 'rgba(29,35,64,0.4)', head: '#1d2340' },
};

/** A comet of light drawn along `path` (a bright head over a dimmer tail), ending at `orbit`. */
type CometProps = { path: SharedValue<SkPath>; orbit: SharedValue<number>; length: number; width: number; color: string };

function Comet({ path, orbit, length, width, color }: CometProps) {
  const a0 = useDerivedValue(() => trim(orbit.get(), length, 0)[0]);
  const a1 = useDerivedValue(() => trim(orbit.get(), length, 0)[1]);
  const b0 = useDerivedValue(() => trim(orbit.get(), length, 1)[0]);
  const b1 = useDerivedValue(() => trim(orbit.get(), length, 1)[1]);
  return (
    <Group>
      <Path path={path} style="stroke" strokeWidth={width} strokeCap="round" color={color} start={a0} end={a1} />
      <Path path={path} style="stroke" strokeWidth={width} strokeCap="round" color={color} start={b0} end={b1} />
    </Group>
  );
}

/**
 * The focus outline: a fine frame of light with a comet running round it at an even speed. It is
 * drawn in the card's own canvas so it turns, tilts and clips with the card.
 */
function Outline({ x, y, w, h, on, tone = 'light', orbit }: OutlineProps & { orbit: SharedValue<number> }) {
  const c = TONES[tone];
  const path = useDerivedValue(() => {
    const b = Skia.PathBuilder.Make();
    b.addRRect({
      rect: { x: x.get(), y: y.get(), width: Math.max(1, w.get()), height: Math.max(1, h.get()) },
      rx: OUTLINE_RADIUS,
      ry: OUTLINE_RADIUS,
    });
    return b.build();
  });
  return (
    <Group opacity={on}>
      <Path path={path} color={c.fill} />
      <Path path={path} style="stroke" strokeWidth={1} color={c.base} />
      <Comet path={path} orbit={orbit} length={COMET_TAIL} width={1.5} color={c.tail} />
      <Comet path={path} orbit={orbit} length={COMET_HEAD} width={1.75} color={c.head} />
    </Group>
  );
}

type Props = {
  width: number;
  height: number;
  face: 'front' | 'back';
  /** The palette the card had, the one it is turning to, and how far the turn has spread (0…1). */
  from: SharedValue<Palette>;
  to: SharedValue<Palette>;
  mix: SharedValue<number>;
  /** 0…1 as a band of light crosses the card; it rests out of sight at 0 and 1. */
  shine: SharedValue<number>;
  /** The card's tilt in degrees (about its horizontal and upright axes): the light slides against it. */
  tiltX: SharedValue<number>;
  tiltY: SharedValue<number>;
  /** Where the new colour spreads from. */
  origin: { x: number; y: number };
  /** The focus outline on this face, and the comet's place round it (0…1, looping). */
  outline: OutlineProps;
  orbit: SharedValue<number>;
};

/** Everything drawn on one face of the card. */
export function CardArt({ width, height, face, from, to, mix, shine, tiltX, tiltY, origin, outline, orbit }: Props) {
  const lines = useMemo(() => guilloche(width, height), [width, height]);
  const grain = useMemo(() => brush(width, height), [width, height]);
  const chip = useMemo(() => chipLines(), []);
  const far = Math.hypot(Math.max(origin.x, width - origin.x), Math.max(origin.y, height - origin.y)) + REVEAL_SOFT * 2;
  const radius = useDerivedValue(() => mix.get() * far);
  const sheen = useDerivedValue(() => [
    { translateX: -SHEEN_WIDTH * 2 + (width + SHEEN_WIDTH * 4) * shine.get() },
    { rotate: (SHEEN_TILT * Math.PI) / 180 },
  ]);
  // The back is the front turned over, so its light sits mirrored and slides the other way.
  const front = face === 'front';
  const lightAt = useDerivedValue(() => {
    const lx = width * (front ? 0.22 : 0.78) - ((front ? 1 : -1) * tiltY.get() * width * SPECULAR_TRAVEL) / 10;
    const ly = -height * 0.18 + (tiltX.get() * height * SPECULAR_TRAVEL) / 10;
    return vec(lx, ly);
  });
  const clip = useMemo(() => ({ rect: { x: 0, y: 0, width, height }, rx: CARD_RADIUS, ry: CARD_RADIUS }), [width, height]);

  return (
    <Canvas style={{ width, height }}>
      <Group clip={clip}>
        <Layer width={width} height={height} palette={from} />
        <Mask
          mask={
            <Circle cx={origin.x} cy={origin.y} r={radius} color="white">
              <BlurMask blur={REVEAL_SOFT / 2} style="normal" />
            </Circle>
          }
        >
          <Layer width={width} height={height} palette={to} />
        </Mask>
        {/* Brushed grain, then the printed guilloche over it. */}
        <Path path={grain.light} style="stroke" strokeWidth={0.6} color="rgba(255, 255, 255, 0.05)" />
        <Path path={grain.dark} style="stroke" strokeWidth={0.6} color="rgba(0, 0, 0, 0.09)" />
        <Path path={lines} style="stroke" strokeWidth={0.7} color="rgba(255, 255, 255, 0.07)" />
        {/* The light over the card, and shade towards the bottom edge. */}
        <Rect x={0} y={0} width={width} height={height}>
          <RadialGradient
            c={lightAt}
            r={width * SPECULAR}
            colors={['rgba(255,255,255,0.2)', 'rgba(255,255,255,0.06)', 'rgba(255,255,255,0)']}
            positions={[0, 0.45, 1]}
          />
        </Rect>
        <Rect x={0} y={height * 0.55} width={width} height={height * 0.45}>
          <LinearGradient start={vec(0, height * 0.55)} end={vec(0, height)} colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.22)']} />
        </Rect>
        {front ? (
          <Group>
            <RoundedRect x={CHIP.x} y={CHIP.y} width={CHIP.width} height={CHIP.height} r={6}>
              <LinearGradient
                start={vec(CHIP.x, CHIP.y)}
                end={vec(CHIP.x + CHIP.width, CHIP.y + CHIP.height)}
                colors={['#f4e3b0', '#b8904a', '#efd9a0', '#9c7634']}
              />
            </RoundedRect>
            <Path path={chip} style="stroke" strokeWidth={1} color="rgba(90, 60, 18, 0.55)" />
            <RoundedRect
              x={CHIP.x + 0.5}
              y={CHIP.y + 0.5}
              width={CHIP.width - 1}
              height={CHIP.height - 1}
              r={5.5}
              style="stroke"
              strokeWidth={1}
              color="rgba(255, 246, 214, 0.5)"
            />
          </Group>
        ) : (
          <Group>
            <Rect x={0} y={STRIPE.y} width={width} height={STRIPE.height} color="rgba(10, 10, 14, 0.9)" />
            <RoundedRect x={STRIP.x} y={STRIP.y} width={width - STRIP.x * 2} height={STRIP.height} r={5} color="rgba(242, 238, 228, 0.94)" />
          </Group>
        )}
        <Group transform={sheen} origin={vec(0, height / 2)}>
          <Rect x={0} y={-height} width={SHEEN_WIDTH} height={height * 3}>
            <LinearGradient
              start={vec(0, 0)}
              end={vec(SHEEN_WIDTH, 0)}
              colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.22)', 'rgba(255,255,255,0)']}
            />
          </Rect>
        </Group>
        <Outline {...outline} orbit={orbit} />
        {/* The card's edge: a hairline of light along the top, darker along the bottom. */}
        <RoundedRect x={0.5} y={0.5} width={width - 1} height={height - 1} r={CARD_RADIUS - 0.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(0, 0)}
            end={vec(0, height)}
            colors={['rgba(255,255,255,0.38)', 'rgba(255,255,255,0.06)', 'rgba(0,0,0,0.25)']}
            positions={[0, 0.5, 1]}
          />
        </RoundedRect>
      </Group>
    </Canvas>
  );
}
