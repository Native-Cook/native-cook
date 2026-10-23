import { Skia } from '@shopify/react-native-skia';

import { NIGHT_SLEEP_SCORE_DATA, NIGHT_SLEEP_SCORE_FRONT, type NightSleepScoreStage } from './motion';
import { NIGHT_SLEEP_SCORE_AURORA, NIGHT_SLEEP_SCORE_CARD, NIGHT_SLEEP_SCORE_COLORS } from './theme';

const f = (n: number) => (Number.isInteger(n) ? `${n}.0` : `${n}`);
const hex3 = (hex: string) => {
  const v = parseInt(hex.slice(1), 16);
  return `float3(${f(+((v >> 16 & 255) / 255).toFixed(4))}, ${f(+((v >> 8 & 255) / 255).toFixed(4))}, ${f(+((v & 255) / 255).toFixed(4))})`;
};

/** Sky palette (sRGB), keyed by the playhead: night at 23:18, the deepest at ~03:00, pre-dawn, dawn at 06:44. */
export const NIGHT_SLEEP_SCORE_SKY = Object.freeze({
  nightTop: '#0A0D2A', nightBottom: '#1A1F52',
  deepTop: '#05071A', deepBottom: '#11153D',
  predawnTop: '#141B4C', predawnBottom: '#433578',
  dawnTop: '#33508F', dawnBottom: '#C57A8E', sun: '#FFB78C',
  duskBottom: '#B68DB4',
  nebula: '#6B4FC8',
  starCell: 11, starChance: .6, // a cell holds a star when its hash is above this
});

const S = NIGHT_SLEEP_SCORE_SKY;
const M = NIGHT_SLEEP_SCORE_CARD.meteor;

/**
 * The sleep card's background at night. Local coordinates are design points of the card (370 × 317).
 * Output alpha is `night`, so at rest (night = 0) the pin's light card shows untouched.
 * Skia treats RuntimeEffect output as Display P3 on iOS, so the sRGB palette is converted at the end.
 */
export const NIGHT_SLEEP_SCORE_SKY_EFFECT = Skia.RuntimeEffect.Make(`
uniform float2 res;
uniform float night;
uniform float phase;
uniform float clock;
uniform float meteor;
uniform float twinkle;

float3 toP3(float3 s) {
  float3 l = pow(max(s, float3(0.0)), float3(2.2));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return pow(max(p, float3(0.0)), float3(1.0 / 2.2));
}
float hash12(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float valueNoise(float2 p) {
  float2 i = floor(p); float2 u = fract(p); u = u * u * (3.0 - 2.0 * u);
  float a = hash12(i); float b = hash12(i + float2(1.0, 0.0));
  float c = hash12(i + float2(0.0, 1.0)); float d = hash12(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 xy) {
  float v = clamp(xy.y / res.y, 0.0, 1.0);
  float a = smoothstep(0.0, 0.45, phase);
  float b = smoothstep(0.55, 0.85, phase);
  float c = smoothstep(0.8, 1.0, phase);
  float3 top = mix(mix(mix(${hex3(S.nightTop)}, ${hex3(S.deepTop)}, a), ${hex3(S.predawnTop)}, b), ${hex3(S.dawnTop)}, c);
  float3 bottom = mix(mix(mix(${hex3(S.nightBottom)}, ${hex3(S.deepBottom)}, a), ${hex3(S.predawnBottom)}, b), ${hex3(S.dawnBottom)}, c);
  // Dusk: while the card darkens at the start, the low sky keeps some evening warmth.
  float dusk = (1.0 - night) * (1.0 - smoothstep(0.0, 0.08, phase));
  bottom = mix(bottom, ${hex3(S.duskBottom)}, 0.85 * dusk);
  float3 col = mix(top, bottom, pow(v, 1.35));

  // Faint nebula drifting with the night.
  float neb = valueNoise(xy * 0.018 + float2(phase * 1.6, 0.0)) * valueNoise(xy * 0.041 - float2(0.0, phase * 0.9));
  col += ${hex3(S.nebula)} * neb * 0.16 * (1.0 - c);

  // Sunrise glow from the lower right, where 06:44 sits on the chart.
  float2 sunAt = float2(res.x * 0.94, res.y * 1.08);
  float sun = exp(-dot(xy - sunAt, xy - sunAt) / (res.x * res.x * 0.42)) * c;
  col = mix(col, ${hex3(S.sun)}, sun * 0.7);

  // Stars: one seeded star per lucky cell, twinkling, fading as dawn comes.
  float cell = ${f(S.starCell)};
  float2 id = floor(xy / cell);
  float stars = 0.0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      float2 k = id + float2(float(i), float(j));
      float h = hash12(k);
      if (h > ${f(S.starChance)}) {
        float2 at = (k + 0.15 + 0.7 * float2(hash12(k + 7.1), hash12(k + 3.7))) * cell;
        float size = mix(0.35, 1.15, pow(hash12(k + 1.3), 3.0));
        float lum = mix(0.35, 1.0, hash12(k + 5.9));
        float tw = mix(0.85, 0.62 + 0.38 * sin(clock * (1.2 + 2.6 * hash12(k + 9.2)) + 6.2832 * hash12(k + 2.4)), twinkle);
        float2 d = xy - at;
        float r = length(d);
        float spot = 1.0 - smoothstep(size * 0.2, size + 0.55, r) + 0.28 * exp(-r * r / (size * size * 9.0));
        float flare = step(0.93, hash12(k + 1.3)) * (exp(-abs(d.x) * 1.1) * exp(-abs(d.y) * 7.0) + exp(-abs(d.y) * 1.1) * exp(-abs(d.x) * 7.0)) * 0.5;
        stars += lum * tw * (spot + flare);
      }
    }
  }
  float starFade = smoothstep(0.35, 0.9, night) * (1.0 - smoothstep(0.62, 0.95, phase)) * (1.0 - 0.55 * smoothstep(0.72, 1.0, v));
  col += float3(0.93, 0.95, 1.0) * stars * starFade;

  // One shooting star.
  if (meteor >= 0.0) {
    float e = 1.0 - (1.0 - meteor) * (1.0 - meteor);
    float2 from = float2(${f(M.fromX)}, ${f(M.fromY)});
    float2 to = float2(${f(M.toX)}, ${f(M.toY)});
    float2 dir = normalize(to - from);
    float2 head = mix(from, to, e);
    float2 rel = xy - head;
    float along = -dot(rel, dir);
    float across = dot(rel, float2(-dir.y, dir.x));
    float tail = clamp(along / ${f(M.tail)}, 0.0, 1.0);
    float streak = step(-0.6, along) * (1.0 - tail) * (1.0 - tail) * exp(-across * across / (0.5 + 1.6 * tail));
    streak += exp(-dot(rel, rel) / 2.2) * 0.8;
    col += float3(1.0, 0.97, 0.92) * streak * sin(3.14159 * meteor) * starFade;
  }

  // Night falls from the top and day comes back from the horizon (same front as nightAtNightSleepScore).
  float fw = ${f(NIGHT_SLEEP_SCORE_FRONT.width)};
  float fx = clamp((night * (1.0 + fw) - v) / fw, 0.0, 1.0);
  float alpha = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
  return half4(half3(toP3(clamp(col, 0.0, 1.0)) * alpha), half(alpha));
}
`);

/** Vertical rays that make the glow above the ribbon read as an aurora curtain. Used as a dstIn mask. */
export const NIGHT_SLEEP_SCORE_RAYS_EFFECT = Skia.RuntimeEffect.Make(`
uniform float clock;
uniform float edge;
half4 main(float2 xy) {
  float reveal = 1.0 - smoothstep(edge - ${f(8)}, edge + 1.0, xy.x);
  float sway = sin(xy.x * 0.13 + clock * 0.7) * 2.2;
  float rays = 0.5 + 0.5 * sin(xy.x * 0.95 + sway + clock * 1.1);
  float fine = 0.5 + 0.5 * sin(xy.x * 2.3 - clock * 1.7 + sway * 0.5);
  float a = 0.42 + 0.4 * rays + 0.18 * fine;
  return half4(a * reveal);
}
`);

type Segment = Readonly<{ stage: NightSleepScoreStage; x0: number; x1: number; y: number }>;

const chart = NIGHT_SLEEP_SCORE_CARD.chart;
const segments: Segment[] = (() => {
  let minute = 0;
  return NIGHT_SLEEP_SCORE_DATA.segments.map((segment) => {
    const x0 = chart.left + chart.width * minute / NIGHT_SLEEP_SCORE_DATA.windowMinutes;
    minute += segment.minutes;
    const x1 = chart.left + chart.width * minute / NIGHT_SLEEP_SCORE_DATA.windowMinutes;
    return { stage: segment.stage, x0, x1, y: chart.top + chart.levels[segment.stage] };
  });
})();

/** Half-width of the stage change at each boundary (boundary i sits between segment i and i + 1). */
const halfWidths = segments.slice(0, -1).map((segment, i) => Math.min(
  NIGHT_SLEEP_SCORE_AURORA.transitionHalfWidth, .45 * (segment.x1 - segment.x0), .45 * (segments[i + 1].x1 - segments[i + 1].x0),
));

/** Plain arrays so worklets can read the ribbon's shape. */
export const NIGHT_SLEEP_SCORE_RIBBON = Object.freeze({
  boundaries: segments.slice(0, -1).map((segment) => segment.x1),
  halfWidths,
  levels: segments.map((segment) => segment.y),
  nightColors: segments.map((segment) => NIGHT_SLEEP_SCORE_COLORS.night.stages[segment.stage]),
});

/** The hypnogram as one smooth ribbon: flat at each stage's level, an S-curve at each change. */
export const NIGHT_SLEEP_SCORE_RIBBON_PATH = (() => {
  const builder = Skia.PathBuilder.Make();
  builder.moveTo(segments[0].x0, segments[0].y);
  segments.forEach((segment, i) => {
    if (i === segments.length - 1) { builder.lineTo(segment.x1, segment.y); return; }
    const h = halfWidths[i]; const next = segments[i + 1];
    builder.lineTo(segment.x1 - h, segment.y);
    builder.cubicTo(segment.x1, segment.y, segment.x1, next.y, segment.x1 + h, next.y);
  });
  return builder.build();
})();

/** Horizontal colour stops for the ribbon: each stage's colour on its flat run, blended across its changes. */
export const NIGHT_SLEEP_SCORE_RIBBON_GRADIENT = (() => {
  const colors: string[] = []; const positions: number[] = [];
  segments.forEach((segment, i) => {
    const start = segment.x0 + (i > 0 ? halfWidths[i - 1] : 0);
    const end = segment.x1 - (i < segments.length - 1 ? halfWidths[i] : 0);
    const color = NIGHT_SLEEP_SCORE_COLORS.night.stages[segment.stage];
    colors.push(color, color);
    positions.push((start - chart.left) / chart.width, (end - chart.left) / chart.width);
  });
  return { colors, positions };
})();

/** The night ring: the night as a clock, each stage's share in its colour (hard stops). */
export const NIGHT_SLEEP_SCORE_RING_GRADIENT = (() => {
  const colors: string[] = []; const positions: number[] = []; let minute = 0;
  NIGHT_SLEEP_SCORE_DATA.segments.forEach((segment) => {
    const color = NIGHT_SLEEP_SCORE_COLORS.night.stages[segment.stage];
    colors.push(color, color);
    positions.push(minute / NIGHT_SLEEP_SCORE_DATA.windowMinutes, (minute + segment.minutes) / NIGHT_SLEEP_SCORE_DATA.windowMinutes);
    minute += segment.minutes;
  });
  return { colors, positions };
})();

/** The ribbon's height at a card x, the same curve as the path (smoothstep across each change). */
export function ribbonYNightSleepScore(x: number, boundaries: readonly number[], widths: readonly number[], levels: readonly number[]): number {
  'worklet';
  for (let i = 0; i < boundaries.length; i += 1) {
    const b = boundaries[i]; const h = widths[i];
    if (x < b - h) return levels[i];
    if (x <= b + h) { const u = (x - (b - h)) / (2 * h); return levels[i] + (levels[i + 1] - levels[i]) * u * u * (3 - 2 * u); }
  }
  return levels[levels.length - 1];
}
