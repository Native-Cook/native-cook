// Ink Wash Page Turn — the bleed, one Skia runtime effect over the three baked pages.
//
// The front is a distance field in points:
//   f(p) = |p − origin| + (blot(p) − ½)·INK.blot + (fibre(p) − ½)·INK.fibre + (fine(p) − ½)·INK.feather
// blot is fbm of a domain-warped coordinate (blotches INK.blotSize across), fibre is value noise
// stretched 14 to 1 along two directions near the horizontal (the ink wicks along the paper's fibres),
// fine is noise at the paper's tooth. The front's radius R runs from −INK.lead to reach + INK.trail
// with progress, and s = R − f is how far behind the front a point is:
//   new page      mix(old, new, smoothstep(0, INK.reveal, s))
//   ink           1 − exp(−density · INK.strength), density = lip (Gaussian at s = 0) + pooled ink
//                 behind it (e^(−s/pool), grainy) + soak ahead of it (e^(s/soak))
//   wet paper     darker and grainier behind the front, both pages wobbling near it, and a thin
//                 bright band INK.sheenAt behind the lip
// Every term is scaled by `ends`, 0 at progress 0 and 1, so a finished turn is exactly the new page
// and the loop can swap pages without a mark.
//
// Colour: the pages arrive through image shaders, which Skia has already converted to the canvas's
// colour space; only the two colours written here as numbers (the ink, the sheen) are converted from
// sRGB to Display P3 on iOS (AGENTS, Mistakes), in JS, and passed in as uniforms.

import { Platform } from 'react-native';

import { INK } from './iwp-motion';

const f = (n: number) => n.toFixed(4);

// Identifiers avoid WGSL builtin and reserved names (Skia may compile this to WGSL).
export const IWP_SKSL = `
uniform shader page0;
uniform shader page1;
uniform shader page2;
uniform float2 res;       // pt
uniform float2 origin;    // where the ink starts, pt
uniform float3 turn;      // page shown, page arriving, progress 0…1
uniform float reach;      // the farthest corner from the origin, pt
uniform float3 inkTone;   // the ink, in the canvas's colour space
uniform float3 sheenTone; // the wet highlight, likewise

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 u = fract(p);
  u = u * u * (3.0 - 2.0 * u);
  return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), u.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), u.x), u.y);
}

// 3 octaves, each turned 37° and doubled
float fbm3(float2 p) {
  float sum = 0.5 * vnoise(p);
  p = float2(0.8 * p.x - 0.6 * p.y, 0.6 * p.x + 0.8 * p.y) * 2.03;
  sum += 0.25 * vnoise(p);
  p = float2(0.8 * p.x - 0.6 * p.y, 0.6 * p.x + 0.8 * p.y) * 2.03;
  sum += 0.125 * vnoise(p);
  return sum / 0.875;
}

float frontAt(float2 p) {
  float2 q = p / ${f(INK.blotSize)};
  float2 w = float2(fbm3(q + float2(3.1, 0.0)), fbm3(q + float2(7.7, 1.3)));
  float blot = fbm3(q + ${f(INK.warp)} * w);
  float2 a = float2(dot(p, float2(0.966, 0.259)), dot(p, float2(-0.259, 0.966)));
  float2 b = float2(dot(p, float2(0.940, -0.342)), dot(p, float2(0.342, 0.940)));
  float fibre = 0.6 * vnoise(a * float2(0.012, 0.17)) + 0.4 * vnoise(b * float2(0.016, 0.21));
  float fine = vnoise(p * 0.9);
  return length(p - origin) + (blot - 0.5) * ${f(INK.blot)} + (fibre - 0.5) * ${f(INK.fibre)} + (fine - 0.5) * ${f(INK.feather)};
}

half4 pageAt(float i, float2 p) {
  float2 at = clamp(p, float2(0.5), res - float2(0.5));
  if (i < 0.5) { return page0.eval(at); }
  if (i < 1.5) { return page1.eval(at); }
  return page2.eval(at);
}

half4 main(float2 p) {
  float progress = turn.z;
  float ends = smoothstep(0.0, ${f(INK.ends)}, progress) * (1.0 - smoothstep(${f(1 - INK.ends)}, 1.0, progress));
  float radius = mix(${f(-INK.lead)}, reach + ${f(INK.trail)}, progress);
  float s = radius - frontAt(p);
  float behind = step(0.0, s);

  // wet paper swells: what is printed on it wobbles near the front
  float near = smoothstep(${f(-3 * INK.soak)}, 0.0, s) * (1.0 - smoothstep(0.0, 60.0, s)) * ends;
  float2 wob = (float2(vnoise(p * 0.11 + float2(5.0, 0.0)), vnoise(p * 0.11 + float2(0.0, 9.0))) - 0.5) * ${f(INK.wobble)} * near;
  float3 old = pageAt(turn.x, p + wob).rgb;
  float3 next = pageAt(turn.y, p + wob * 0.6).rgb;
  float shown = smoothstep(0.0, ${f(INK.reveal)}, s);
  float3 col = mix(old, next, shown);

  // the paper's tooth, and the wet paper behind the front
  float tooth = 0.5 * vnoise(p * 1.7) + 0.5 * vnoise(float2(p.x * 0.09, p.y * 1.3));
  float wet = behind * exp(-max(s, 0.0) / ${f(INK.wetTail)}) * ends;
  col *= 1.0 - ${f(INK.wet)} * wet * (0.8 + 0.4 * tooth);

  // the ink: a dark lip, pooled pigment behind it, a soak ahead of it
  float lip = exp(-(s * s) / ${f(INK.lip * INK.lip)});
  float pool = behind * exp(-max(s, 0.0) / ${f(INK.pool)}) * (0.8 + 0.4 * tooth);
  float soak = (1.0 - behind) * exp(min(s, 0.0) / ${f(INK.soak)});
  float density = (${f(INK.lipWeight)} * lip + ${f(INK.poolWeight)} * pool + ${f(INK.soakWeight)} * soak) * ends;
  col = mix(col, inkTone, 1.0 - exp(-density * ${f(INK.strength)}));

  // the wet sheen just behind the lip
  float band = (s - ${f(INK.sheenAt)}) / ${f(INK.sheenWidth)};
  col += sheenTone * ${f(INK.sheen)} * exp(-band * band) * (0.4 + 0.6 * vnoise(p * 0.25)) * ends;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
`;

/** an sRGB colour (0…1 per channel) as the numbers a runtime effect must write for it: Display P3 on iOS, unchanged elsewhere */
export function tone(r: number, g: number, b: number): [number, number, number] {
  if (Platform.OS !== 'ios') return [r, g, b];
  const dec = (v: number) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
  const enc = (v: number) => (v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055);
  const lr = dec(r);
  const lg = dec(g);
  const lb = dec(b);
  return [enc(0.8225 * lr + 0.1774 * lg), enc(0.0332 * lr + 0.9669 * lg), enc(0.0171 * lr + 0.0724 * lg + 0.9108 * lb)];
}

/** sumi ink, blue-black */
export const INK_TONE = tone(0.086, 0.094, 0.157);
/** the wet highlight, a cool white */
export const SHEEN_TONE = tone(0.92, 0.96, 1.0);
