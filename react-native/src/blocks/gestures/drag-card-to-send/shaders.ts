/**
 * Two materials, written as SkSL because gradients cannot do them.
 *
 * Liquid: the island, the drop and the neck are one signed distance field (smooth union), shaded as black liquid
 * with a sessile-drop profile: a little fresnel, the light page reflected low in the rim, a softbox above-left as
 * a soft highlight with one sharp core. The island itself stays matte like the hardware island; the gloss starts
 * below its lip. A soft contact shadow from the drop alone (never around the island) sits under it.
 *
 * Card: midnight anodised aluminium. Bead-blasted grain under a clear oxide layer, laser-etched contour lines, the
 * studio's softbox reflected as a broad lobe that slides as the card moves (it is the room that is reflected, so the
 * reflection moves against the card), a machined chamfer lit from above, and a champagne-gold chip.
 */
import { Skia, type SkRuntimeEffect } from '@shopify/react-native-skia';

import { DROP } from './motion';
import { LAYOUT, MATERIAL } from './theme';

const f = (n: number) => (Number.isInteger(n) ? `${n}.0` : `${n}`);
const v3 = (c: readonly number[]) => `float3(${f(c[0])}, ${f(c[1])}, ${f(c[2])})`;

const COMMON = `
float hash12(float2 p) {
  float3 p3 = fract(float3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 u = fract(p);
  u = u * u * (3.0 - 2.0 * u);
  return mix(mix(hash12(i), hash12(i + float2(1.0, 0.0)), u.x), mix(hash12(i + float2(0.0, 1.0)), hash12(i + float2(1.0, 1.0)), u.x), u.y);
}
float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float segDist(float2 p, float2 a, float2 b) {
  float2 ab = b - a;
  float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
  return length(p - a - ab * t);
}
float3 srgb(float3 c) { return pow(max(c, 0.0), float3(0.4545)); }
`;

const LIQUID = `
uniform float4 uIsland;  // centre, half size (a capsule)
uniform float3 uBulb;    // hanging drop: centre, radius (0 = none)
uniform float4 uDrop;    // falling drop: centre, radius, stretch along uDir
uniform float2 uDir;
uniform float4 uNeck;    // filament from (x, y) to (z, w)
uniform float uNeckR;
uniform float uLip;      // the island's lip, wobbling after the snap
uniform float uTail;     // the drop's tail, 1 at the snap
uniform float uShadow;
uniform float uAA;
${COMMON}
const float K = ${f(DROP.blend)};
const float BIG = 10000.0;

float dDrop(float2 p) {
  if (uDrop.z <= 0.01) return BIG;
  float2 q = p - uDrop.xy;
  float2 n = float2(-uDir.y, uDir.x);
  float s = uDrop.w;
  float2 e = float2(dot(q, uDir) / s, dot(q, n) * sqrt(s));
  float d = (length(e) - uDrop.z) / sqrt(s);
  if (uTail > 0.01) {
    // The tail points back up the path, toward where the neck broke.
    float2 c = uDrop.xy - uDir * uDrop.z * 1.05;
    d = smin(d, length(p - c) - uDrop.z * 0.2 * uTail, 9.0);
  }
  return d;
}
float dLiquid(float2 p) {
  float d = sdBox(p - uIsland.xy, uIsland.zw, uIsland.w);
  if (uBulb.z > 0.01) d = smin(d, length(p - uBulb.xy) - uBulb.z, K);
  if (uNeckR > 0.01) d = smin(d, segDist(p, uNeck.xy, uNeck.zw) - uNeckR, K * 0.7);
  if (uLip > 0.01) d = smin(d, length(p - float2(uIsland.x, uIsland.y + uIsland.w + uLip * 0.2)) - uLip, 8.0);
  return smin(d, dDrop(p), K);
}
// Only the drop throws a shadow: the island is the hardware's and must not grow a halo.
float dCaster(float2 p) {
  float d = BIG;
  if (uBulb.z > 0.01) d = length(p - uBulb.xy) - uBulb.z;
  return min(d, dDrop(p));
}

half4 main(float2 p) {
  float d = dLiquid(p);
  float lip = uIsland.y + uIsland.w;

  // Shadow on the page or the card: offset down, softer and further while falling.
  float air = uDrop.z > 0.01 ? 1.0 : 0.0;
  float2 off = mix(float2(1.5, 5.0), float2(3.0, 12.0), air);
  float blur = mix(7.0, 12.0, air);
  float ds = dCaster(p - off);
  float sh = uShadow * mix(0.16, 0.13, air) * (1.0 - smoothstep(-blur, blur, ds));
  // No shadow above the lip (it would show around the hardware island).
  sh *= smoothstep(lip, lip + 10.0, p.y);
  half4 under = half4(0.0, 0.0, 0.0, half(sh));

  float cov = clamp(0.5 - d / uAA, 0.0, 1.0);
  if (cov <= 0.0) return under;

  // Edge profile: a sessile drop. t = 1 at the rim (surface vertical) to 0 once inside by the bevel.
  float bevel = max(8.0, ${f(DROP.bevel)} * max(uBulb.z, uDrop.z));
  float e = 0.6;
  float2 g = float2(dLiquid(p + float2(e, 0.0)) - dLiquid(p - float2(e, 0.0)), dLiquid(p + float2(0.0, e)) - dLiquid(p - float2(0.0, e)));
  g = g / max(length(g), 0.0001);
  float t = 1.0 - clamp(-d / bevel, 0.0, 1.0);
  float3 n = normalize(float3(g * t, sqrt(max(0.0, 1.0 - t * t)) + 0.0001));
  float3 V = float3(0.0, 0.0, 1.0);
  float3 R = reflect(-V, n);

  // What the surface sees: the light page all around (brighter below, where the page faces it), a softbox
  // above-left, and the dark of the phone's glass straight ahead.
  float3 room = ${v3(MATERIAL.room)};
  float below = clamp(R.y * 0.8 + 0.35, 0.0, 1.0);
  float3 env = room * mix(0.12, 0.8, below * below) * clamp(1.0 - R.z, 0.0, 1.0);
  // Up and to the left, so the highlight sits on the drop's upper-left shoulder, above the amount written on it.
  float3 L = normalize(float3(-0.55, -0.55, 0.63));
  float lobe = dot(R, L);
  float soft = smoothstep(0.82, 0.97, lobe);
  float core = pow(max(lobe, 0.0), 260.0);
  float F = 0.035 + 0.965 * pow(1.0 - n.z, 5.0);

  float3 c = ${v3(MATERIAL.ink)} * (1.0 - F) + env * F + ${v3(MATERIAL.softbox)} * (soft * 0.34 + core * 1.1);
  // The island and the receipt stay pure matte black like the hardware; the gloss begins under the lip.
  float gloss = smoothstep(lip - 1.0, lip + 9.0, p.y);
  c = mix(float3(0.0), c, gloss);
  float3 col = srgb(c);
  half4 liquid = half4(half3(col * cov), half(cov));
  return liquid + under * half(1.0 - cov);
}
`;

const CARD = `
uniform float2 uSize;
uniform float uR;
uniform float uAA;
uniform float uShift;   // the card's offset (pt): the room's reflection slides against it
uniform float uPress;   // 0..1 while pressed: the card dips away from the light
${COMMON}
const float4 CHIP = float4(${f(LAYOUT.chip.x)}, ${f(LAYOUT.chip.y)}, ${f(LAYOUT.chip.w)}, ${f(LAYOUT.chip.h)});
const float CHIP_R = ${f(LAYOUT.chip.r)};

float dCard(float2 p) { return sdBox(p - uSize * 0.5, uSize * 0.5, uR); }

// A groove of half-width w at distance x from its centre line, antialiased.
float groove(float x, float w) { return 1.0 - smoothstep(w, w + uAA * 1.2, abs(x)); }

float4 chip(float2 p) {
  float2 c = CHIP.xy + CHIP.zw * 0.5;
  float d = sdBox(p - c, CHIP.zw * 0.5, CHIP_R);
  float a = clamp(0.5 - d / uAA, 0.0, 1.0);
  float2 q = (p - CHIP.xy) / CHIP.zw;
  // Champagne gold lit from above-left, with a fine brushed grain across it.
  float grain = vnoise(float2(p.x * 2.4, p.y * 0.18)) - 0.5;
  float3 m = mix(${v3(MATERIAL.chipLight)}, ${v3(MATERIAL.chipDark)}, smoothstep(0.0, 1.0, q.y * 0.8 + q.x * 0.3));
  m *= 1.0 + grain * 0.1;
  // A soft sheen band where the softbox crosses it.
  m += float3(0.32, 0.26, 0.16) * exp(-pow((q.x * 0.6 + q.y - 0.42) * 3.2, 2.0)) * 0.5;
  // Contact pads (ISO 7816 layout): a rounded central pad, three pads each side, cut by fine engraved channels.
  float2 o = p - c;
  float2 hs = CHIP.zw * 0.5;
  float inside = step(abs(o.y), hs.y - 1.6) * step(abs(o.x), hs.x - 1.6);
  float px = hs.x * 0.44;
  float lw = 0.32;
  float ch = groove(abs(o.x) - px, lw) * inside;
  float side = step(px, abs(o.x));
  ch = max(ch, groove(abs(o.y) - hs.y * 0.34, lw) * side * inside);
  float mid = sdBox(o, float2(px - 4.0, hs.y * 0.5), 3.2);
  ch = max(ch, groove(mid, lw));
  ch = max(ch, groove(o.y, lw) * step(abs(o.x), px - 4.0) * step(hs.y * 0.5, abs(o.y)) * inside);
  m = mix(m, m * 0.42, ch * 0.85);
  // Its edge, a bevel: light on the upper lip, shade on the lower.
  float rim = 1.0 - clamp(-d / 1.1, 0.0, 1.0);
  float2 gn = normalize(p - c + 0.0001);
  m += rim * (-gn.y * 0.16);
  m *= 1.0 - rim * max(gn.y, 0.0) * 0.35;
  return float4(m, a);
}

half4 main(float2 p) {
  float d = dCard(p);
  float cov = clamp(0.5 - d / uAA, 0.0, 1.0);
  if (cov <= 0.0) return half4(0.0);
  float2 uv = p / uSize;

  // The dye, deeper toward the lower right where the oxide sees the dark room.
  float3 base = mix(${v3(MATERIAL.cobalt)}, ${v3(MATERIAL.cobaltDeep)}, smoothstep(0.0, 1.0, uv.x * 0.5 + uv.y * 0.7));
  // Bead-blasted grain: fine, isotropic, a few percent.
  float grain = vnoise(p * 1.9) * 0.5 + vnoise(float2(p.x * 0.06, p.y * 2.4)) * 0.5 - 0.5;
  base *= 1.0 + grain * 0.06;

  // The softbox reflected in the oxide: a broad lobe high on the left, sliding up as the card is pulled down.
  float s = uShift / uSize.y;
  float2 lc = float2(0.08, -0.16 - s * 1.4);
  float2 dd = (uv - lc) * float2(0.85, 1.25);
  float lobe = exp(-dot(dd, dd) * 3.6) * (1.0 - uPress * 0.35);
  float3 c = base + ${v3(MATERIAL.cobaltLift)} * lobe * 0.26;
  // The softbox's edge, a soft diagonal band across the satin oxide: what tells metal from paint.
  float bandPos = dot(uv, normalize(float2(1.0, 0.62))) - (0.5 - s * 0.9);
  float window = smoothstep(-0.18, -0.03, bandPos) * (1.0 - smoothstep(0.03, 0.24, bandPos));
  float light = (0.1 + grain * 0.2) * (1.0 - uPress * 0.35);
  c += float3(0.12, 0.17, 0.5) * window * light;

  // Laser-etched contours, a cove's depth lines: rings about a point off the lower right, warped by slow noise,
  // fading out toward the printed face on the left. Each line is a hairline cut that catches the softbox.
  float2 cc = float2(uSize.x * 1.06, uSize.y * 1.18);
  float rr = length(p - cc) + (vnoise(p * 0.011 + 3.7) - 0.5) * 16.0;
  float sp = 7.0;
  float fl = (fract(rr / sp) - 0.5) * sp;
  float etch = groove(fl, 0.2);
  // Kept off the printed face: clear of the last four digits, the contactless mark and the card's edges.
  float zone = smoothstep(320.0, 200.0, rr) * smoothstep(96.0, 150.0, rr);
  zone *= smoothstep(0.0, 18.0, p.y) * smoothstep(0.0, 18.0, uSize.y - p.y) * smoothstep(0.0, 18.0, uSize.x - p.x);
  float catchLight = 0.035 + window * 0.32 + lobe * 0.15;
  c += float3(0.42, 0.5, 0.95) * etch * zone * catchLight;

  // Machined chamfer: 1.2 pt around the edge, facing up-left catches the light, facing down-right falls away.
  float e = 0.6;
  float2 g = float2(dCard(p + float2(e, 0.0)) - dCard(p - float2(e, 0.0)), dCard(p + float2(0.0, e)) - dCard(p - float2(0.0, e)));
  g = normalize(g + 0.0001);
  float band = 1.0 - smoothstep(0.2, 1.1, -d);
  float facing = dot(g, normalize(float2(-0.35, -1.0)));
  c += band * pow(max(facing, 0.0), 1.5) * float3(0.32, 0.38, 0.62) * 0.7;
  c *= 1.0 - band * max(-facing, 0.0) * 0.55;

  float4 m = chip(p);
  c = mix(c, m.rgb, m.a);

  float3 col = srgb(c);
  // Film grain on the final colour, a touch, so large flat areas never band.
  col += (hash12(p * 3.1) - 0.5) * 0.012;
  return half4(half3(col * cov), half(cov));
}
`;

const make = (src: string, name: string): SkRuntimeEffect => {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error(`drag-card-to-send: ${name} shader did not compile`);
  return e;
};

export const SHADERS = { liquid: make(LIQUID, 'liquid'), card: make(CARD, 'card') };
