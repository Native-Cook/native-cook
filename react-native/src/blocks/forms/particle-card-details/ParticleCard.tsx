import { Canvas, Fill, Group, Path, Shader, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// The clock of a submission, in seconds since Continue was released.

/** autoPlay: one submission every LOOP seconds. The dots' twinkle repeats with the same period. */
export const LOOP = 7.8;
// The solid card is there after CARD_IN, brighter at first (the flash fades over FLASH_FOR).
export const CARD_IN = 0.09;
const FLASH_FOR = 0.3;
// The line that eats the card starts above its top edge at LINE_AT and crosses it in LINE_FOR;
// it keeps going for TRAIL while the last dust thins out.
export const LINE_AT = 0.08;
export const LINE_FOR = 0.62;
export const TRAIL = 0.5;
// How far above the top and below the bottom the line starts and ends: its ragged edge reaches that far.
const LINE_PAD = 14;
// Points a second the dust rises (the sparse, far dust rises 2.2 times as fast).
const RISE = 34;
// Reduced motion: the card fades in, holds and fades out instead of being eaten.
const STILL_HOLD = 0.35;
const STILL_OUT = 0.35;

/** Room round the card for the dots that scatter past its edges and the dust that rises above it. */
export const SPILL = { side: 36, top: 52, bottom: 36 };
export const CARD_RADIUS = 13;

// The contactless mark: arcs of these radii, opening to the right, this far from the card's
// right and bottom edges.
const MARK = { radii: [4, 8.5, 13, 17.5], sweep: 76, right: 40, bottom: 38, stroke: 2 };
const MARK_INK = '#DCDCE1';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** When the card has gone completely, dust included. */
export const CARD_FOR = LINE_AT + LINE_FOR + TRAIL;

/**
 * Everything in the card's place is one shader, in points:
 * - the cloud: two grids of round dots (cells of 4.2 and 6.6 points, 62% and 30% of them filled),
 *   each dot moved off its cell's centre, 0.4 to 0.9 points in radius, most of them dim, one in
 *   ten violet and one in ten green, twinkling on its own phase with a whole number of beats
 *   (5 to 15) per `period`. The grids thin out from 10 points inside the card's edge to 14 points
 *   outside it.
 * - the solid card, a vertical gradient, present below `line` (points under the card's top edge).
 *   The line is ragged (two noises along x, 12 and 6 points high); within 7 points under it the
 *   card breaks into specks, and within 10 points it is lit pale grey.
 * - the dust above the line: three grids of grains whose share of filled cells falls with the
 *   distance from the line (over 6, 18 and 80 points), the last two rising with `rise`.
 */
const SOURCE = Skia.RuntimeEffect.Make(`
uniform vec2 origin;
uniform vec2 card;
uniform float radius;
uniform float time;
uniform float period;
uniform float solid;
uniform float flash;
uniform float line;
uniform float rise;

const float TAU = 6.2831853;

float hash(vec2 p) {
  vec3 q = fract(vec3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float noise(float x) {
  float i = floor(x);
  float f = x - i;
  f = f * f * (3.0 - 2.0 * f);
  return mix(hash(vec2(i, 3.7)), hash(vec2(i + 1.0, 3.7)), f);
}

float box(vec2 p, vec2 extent, float r) {
  vec2 q = abs(p) - extent + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

vec4 dots(vec2 p, float cell, float fill, float seed) {
  vec2 g = floor(p / cell);
  vec2 s = g + seed;
  vec2 centre = (g + 0.5 + (vec2(hash(s), hash(s + 11.3)) - 0.5) * 0.7) * cell;
  float edge = box(centre - origin - card * 0.5, card * 0.5, radius);
  float keep = step(hash(s + 23.1), fill * (1.0 - smoothstep(-10.0, 14.0, edge)));
  float size = mix(0.4, 0.9, hash(s + 37.9));
  float a = keep * (1.0 - smoothstep(size - 0.45, size + 0.3, distance(p, centre)));
  float beats = floor(mix(5.0, 16.0, hash(s + 41.7)));
  float twinkle = 0.5 + 0.5 * sin(TAU * (beats * time / period + hash(s + 53.3)));
  float glint = hash(s + 67.1);
  float bright = mix(0.22, 1.0, glint * glint) * (0.12 + 0.88 * twinkle * twinkle);
  float tint = hash(s + 79.9);
  vec3 c = tint < 0.1 ? vec3(0.74, 0.64, 1.0) : tint < 0.2 ? vec3(0.6, 0.96, 0.76) : vec3(1.0);
  return vec4(c * bright * a, bright * a);
}

float grain(vec2 p, float cell, float fill, float seed) {
  vec2 g = floor(p / cell);
  vec2 s = g + seed;
  vec2 centre = (g + 0.5 + (vec2(hash(s), hash(s + 11.3)) - 0.5) * 0.6) * cell;
  float size = cell * mix(0.28, 0.5, hash(s + 37.9));
  return step(hash(s + 23.1), fill) * (1.0 - smoothstep(size - 0.4, size + 0.25, distance(p, centre)));
}

vec4 main(vec2 p) {
  vec2 local = p - origin;
  vec4 cloud = dots(p, 4.2, 0.62, 0.0) + dots(p + 2.6, 6.6, 0.3, 91.0);

  float cover = 1.0 - smoothstep(-0.5, 0.5, box(local - card * 0.5, card * 0.5, radius));
  float edge = line + (noise(local.x * 0.07) - 0.5) * 12.0 + (noise(local.x * 0.31 + 40.0) - 0.5) * 6.0;
  float under = local.y - edge;
  float body = cover * solid * step(hash(floor(p / 1.4) + 5.0), smoothstep(-1.5, 7.0, under));
  vec3 base = mix(vec3(0.2, 0.2, 0.215), vec3(0.135, 0.135, 0.15), clamp(local.y / card.y, 0.0, 1.0));
  vec3 lit = mix(vec3(0.66, 0.66, 0.7), base, smoothstep(0.0, 10.0, under)) * (1.0 + flash);

  float above = -under;
  float beside = max(-local.x, local.x - card.x);
  float within = solid * step(0.0, above) * step(local.y, card.y) * smoothstep(-46.0, -6.0, local.y)
    * (1.0 - smoothstep(-2.0, 8.0, beside)) * smoothstep(0.0, 14.0, edge);
  vec2 swayed = p + vec2(sin(local.y * 0.09 + rise * 0.08) * above * 0.06, rise);
  float dust = max(grain(p, 1.7, 0.92 * exp(-above / 6.0), 7.0), grain(swayed, 2.3, 0.4 * exp(-above / 18.0), 19.0));
  dust = within * max(dust, grain(p + vec2(0.0, rise * 2.2), 3.1, 0.12 * exp(-above / 80.0), 43.0));
  vec3 dustColor = mix(vec3(0.72, 0.72, 0.76), vec3(0.5, 0.5, 0.55), smoothstep(0.0, 60.0, above));

  vec3 rgb = cloud.rgb * (1.0 - body) + lit * body;
  float a = cloud.a * (1.0 - body) + body;
  rgb = rgb * (1.0 - dust) + dustColor * dust;
  a = a * (1.0 - dust) + dust;
  return vec4(rgb, a);
}
`);

type Props = {
  /** The card's size; the canvas is larger by SPILL. */
  width: number;
  height: number;
  /** Seconds, running from 0 to `period` again and again: the twinkle. */
  time: SharedValue<number>;
  period: number;
  /** Seconds since Continue was released; negative while the form is idle. */
  elapsed: SharedValue<number>;
  /** Reduced motion: the card fades in and out instead of being eaten from the top. */
  still: boolean;
};

/**
 * The place of the card: a card-shaped cloud of twinkling dots. On submit a solid card flashes in
 * and is eaten from its top edge down by a ragged line that leaves dust rising behind it, until
 * only the cloud is left.
 */
export function ParticleCard({ width, height, time, period, elapsed, still }: Props) {
  const mark = useMemo(() => {
    const builder = Skia.PathBuilder.Make();
    const cx = SPILL.side + width - MARK.right - MARK.radii[MARK.radii.length - 1];
    const cy = SPILL.top + height - MARK.bottom;
    MARK.radii.forEach((r) => builder.addArc({ x: cx - r, y: cy - r, width: r * 2, height: r * 2 }, -MARK.sweep / 2, MARK.sweep));
    return builder.build();
  }, [width, height]);

  // Where the line is, in points under the card's top edge.
  const line = useDerivedValue(() => {
    const e = elapsed.get();
    if (still) return -1000;
    return -LINE_PAD + ((height + LINE_PAD * 2) * Math.max(0, e - LINE_AT)) / LINE_FOR;
  });
  const solid = useDerivedValue(() => {
    const e = elapsed.get();
    if (e < 0) return 0;
    if (still) return smooth(e, 0, CARD_IN * 2) * (1 - smooth(e, CARD_IN * 2 + STILL_HOLD, CARD_IN * 2 + STILL_HOLD + STILL_OUT));
    return e < CARD_FOR ? smooth(e, 0, CARD_IN) : 0;
  });
  const uniforms = useDerivedValue(() => {
    const e = elapsed.get();
    return {
      origin: [SPILL.side, SPILL.top],
      card: [width, height],
      radius: CARD_RADIUS,
      time: time.get(),
      period,
      solid: solid.get(),
      flash: still ? 0 : 0.45 * (1 - smooth(e, CARD_IN, FLASH_FOR)),
      line: line.get(),
      rise: RISE * Math.max(0, e - LINE_AT),
    };
  });
  // The mark goes with the card: cut off at the line.
  const markClip = useDerivedValue(() => {
    const top = SPILL.top + Math.max(0, line.get());
    return { x: 0, y: top, width: width + SPILL.side * 2, height: Math.max(0, SPILL.top + height - top) };
  });

  if (!SOURCE) return null;
  return (
    <Canvas pointerEvents="none" style={[styles.canvas, { width: width + SPILL.side * 2, height: height + SPILL.top + SPILL.bottom }]}>
      <Fill>
        <Shader source={SOURCE} uniforms={uniforms} />
      </Fill>
      <Group clip={markClip} opacity={solid}>
        <Path path={mark} style="stroke" strokeWidth={MARK.stroke} strokeCap="round" color={MARK_INK} />
      </Group>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: {
    position: 'absolute',
    left: -SPILL.side,
    top: -SPILL.top,
  },
});
