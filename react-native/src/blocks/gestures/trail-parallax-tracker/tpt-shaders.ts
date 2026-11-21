// Trail Parallax Tracker: SkSL. Colours arrive as uniforms already encoded for the canvas (`tpt-light.ts` re-encodes
// sRGB for Display P3 on iOS, where Skia reads runtime-effect output as P3).

import { Skia } from '@shopify/react-native-skia';

/**
 * One depth of scenery (far, mid or near), two biome strips at once: `imgA` left of `uSplit` (the biome being left
 * behind), `imgB` right of it (the one ahead), so a border only ever shows under an occluder's centre line.
 * Each strip is flat-fill art; every pixel is relit for the hour by moving it from its nearest source fill to that
 * fill's colour at this hour (index map by nearest fill: anti-aliased edges keep their blend).
 */
const LAYER = `
uniform shader imgA;
uniform shader imgB;
uniform float uSplit;
uniform float uOffA;
uniform float uOffB;
uniform float uTop;
uniform float uScA;
uniform float uScB;
uniform float uNA;
uniform float uNB;
uniform float3 uSrcA[6];
uniform float3 uDstA[6];
uniform float3 uSrcB[6];
uniform float3 uDstB[6];

half4 main(float2 xy) {
  bool left = xy.x < uSplit;
  float sc = left ? uScA : uScB;
  // above the art's top edge the first row is repeated: skies stay empty, cropped trunks and the grove's wash run on up
  float2 p = float2((xy.x + (left ? uOffA : uOffB)) * sc, max((xy.y - uTop) * sc, 0.5));
  half4 c = left ? imgA.eval(p) : imgB.eval(p);
  if (c.a < 0.003) return half4(0.0);
  float3 u = float3(c.rgb) / float(c.a);
  float n = left ? uNA : uNB;
  float best = 1e9;
  float3 delta = float3(0.0);
  for (int i = 0; i < 6; i++) {
    if (float(i) < n) {
      float3 s = left ? uSrcA[i] : uSrcB[i];
      float3 e = u - s;
      float dd = dot(e, e);
      if (dd < best) {
        best = dd;
        delta = (left ? uDstA[i] : uDstB[i]) - s;
      }
    }
  }
  float3 o = clamp(u + delta, 0.0, 1.0);
  return half4(half3(o) * c.a, c.a);
}
`;

/** The sky: a three-stop analytic gradient per side of the split, from the hour's palette. */
const SKY = `
uniform float uSplit;
uniform float uHor;
uniform float3 uTopA;
uniform float3 uMidA;
uniform float3 uHorA;
uniform float3 uTopB;
uniform float3 uMidB;
uniform float3 uHorB;

half4 main(float2 xy) {
  bool left = xy.x < uSplit;
  float k = clamp(xy.y / uHor, 0.0, 1.0);
  float3 top = left ? uTopA : uTopB;
  float3 mid = left ? uMidA : uMidB;
  float3 hor = left ? uHorA : uHorB;
  float3 c = k < 0.6 ? mix(top, mid, k / 0.6) : mix(mid, hor, (k - 0.6) / 0.4);
  return half4(half3(c), 1.0);
}
`;

/** 3 % static grain over the scene, one grain per device pixel, so large flat fills never band in video. */
const GRAIN = `
uniform float uScale;
float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float g = hash2(floor(xy * uScale)) * 2.0 - 1.0;
  float a = 0.03 * abs(g);
  return g > 0.0 ? half4(a, a, a, a) : half4(0.0, 0.0, 0.0, a);
}
`;

const make = (src: string) => {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error('trail-parallax-tracker: shader failed to compile');
  return e;
};

export const LAYER_FX = make(LAYER);
export const SKY_FX = make(SKY);
export const GRAIN_FX = make(GRAIN);
