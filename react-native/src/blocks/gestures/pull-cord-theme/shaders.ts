/**
 * The light, written as SkSL because it is a light field, which no gradient can draw.
 *
 * Page (one source per colour role: paper, ink, mute ink, accent; all evaluate the same field, so a glyph changes
 * exactly where the paper behind it does). The page is a wall `depth` behind the bulb. Direct light leaves the shade
 * in a cone (soft penumbra) and falls off as cos(incidence)/r²; the ball chain, hanging between bulb and wall, takes
 * a soft shadow out of it. Bounce light from the room reaches everywhere near the lamp, except straight above the
 * shade, which hides the wall from the lit page below. The lit page is that light seen: an ambient share far away, the
 * pool bright and warm under the shade, all of it dimming with the filament. The dark page is the room with the lamp
 * off; while the filament glows its warm light shows on it, and its ambient lifts a little as the eye adapts. The
 * theme front is a level of log illuminance, so the lit page develops along the light's own contours. The type does
 * not fade with the paper (it would vanish where the two meet): it flips to the ink that reads on the paper under it,
 * so every glyph keeps at least 4:1 at every frame of the change. Paper grain doubles as dither.
 *
 * Air: a faint haze in the cone under the shade, thinning with distance, and a few slow dust motes that only show
 * where the light reaches them. Screen-blended over the page, dithered.
 */
import { Skia, type SkRuntimeEffect } from '@shopify/react-native-skia';

import { BALLS } from './cord-sim';
import { BOUNCE, CONE, hex } from './light';
import { FILAMENT } from './motion';
import { AIR, DAY, EXPOSE, LEGIBLE, NIGHT, NIGHT_GLOW, SHADOW, type Palette } from './theme';

const f = (n: number) => (Number.isInteger(n) ? `${n}.0` : `${n}`);
const v3 = (c: string) => {
  const [r, g, b] = hex(c);
  return `float3(${f(r / 255)}, ${f(g / 255)}, ${f(b / 255)})`;
};

/** Shadow points: every ball, then the tip of the pull. */
export const SHADOW_POINTS = BALLS + 1;

const NOISE = `
float hash12(float2 p) {
  float3 p3 = fract(float3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 u = fract(p);
  u = u * u * (3.0 - 2.0 * u);
  float a = hash12(i);
  float b = hash12(i + float2(1.0, 0.0));
  float c = hash12(i + float2(0.0, 1.0));
  float d = hash12(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}`;

function page(role: keyof Palette, paper: boolean) {
  const n = SHADOW_POINTS;
  return `
uniform float2 uBulb;
uniform float2 uAxis;
uniform float uDepth;
uniform float2 uRange;
uniform float uFront;
uniform float uBand;
uniform float uFlat;
uniform float uBright;
uniform float3 uLamp;
uniform float uScale;
uniform float uAdapt;
uniform float2 uChain[${n}];
${NOISE}

float segDist(float2 p, float2 a, float2 b) {
  float2 pa = p - a;
  float2 ba = b - a;
  float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
  return length(pa - ba * h);
}
// WCAG contrast of two colours, sRGB taken as gamma 2.2.
float luma(float3 c) {
  return dot(pow(clamp(c, 0.0, 1.0), float3(2.2)), float3(0.2126, 0.7152, 0.0722));
}
float contrast(float3 a, float3 b) {
  float x = luma(a);
  float y = luma(b);
  return (max(x, y) + 0.05) / (min(x, y) + 0.05);
}
float2 project(float2 c) {
  return uBulb + (c - uBulb) * ${f(SHADOW.k)};
}
float chainShadow(float2 p) {
  float d = 100000.0;
  for (int i = 0; i < ${n - 2}; i++) {
    d = min(d, segDist(p, project(uChain[i]), project(uChain[i + 1])));
  }
  float t = segDist(p, project(uChain[${n - 2}]), project(uChain[${n - 1}]));
  float w = ${f(SHADOW.penumbra)} * uScale;
  float s = ${f(SHADOW.chain)} * exp(-(d * d) / (w * w)) + ${f(SHADOW.pull)} * exp(-(t * t) / (w * w * 1.6));
  return min(s, 0.6);
}

half4 main(float2 p) {
  float2 d = p - uBulb;
  float r = sqrt(dot(d, d) + uDepth * uDepth);
  float k = uDepth / r;
  float direct = smoothstep(${f(CONE.cos0)}, ${f(CONE.cos1)}, dot(d, uAxis) / r) * k * k * k;
  float d2 = 4.0 * uDepth * uDepth;
  float bounce = d2 / (dot(d, d) + d2);
  float e = direct + ${f(BOUNCE)} * bounce;
  // 0 where the light is strongest, 1 where it is weakest: the order in which the page changes.
  float rank = 1.0 - clamp((log(e) - uRange.x) / (uRange.y - uRange.x), 0.0, 1.0);
  float m = clamp((uFront * (1.0 + uBand) - rank) / uBand, 0.0, 1.0);
  m = m * m * (3.0 - 2.0 * m);
  m = mix(m, uFront, uFlat);

  // What is seen: direct light less the chain's shadow; bounce less the shade's own shadow above it.
  float lit = direct * (1.0 - chainShadow(p));
  float glow = pow(clamp(lit / 0.55, 0.0, 1.0), 0.45) * uBright;
  float2 q = (p - uBulb + float2(0.0, ${f(SHADOW.shadeUp)} * uScale)) / (float2(${f(SHADOW.shadeRx)}, ${f(SHADOW.shadeRy)}) * uScale);
  float occ = 1.0 - smoothstep(0.55, 1.45, length(q));
  float heat = pow(uBright, ${f(1 / FILAMENT.power)});
  float3 tint = uLamp / max(max(uLamp.r, uLamp.g), max(uLamp.b, 0.001));
  float room = (${f(EXPOSE.ambient)} + ${f(EXPOSE.bounce)} * bounce * (1.0 - ${f(SHADOW.shadeDepth)} * occ)) * mix(${f(EXPOSE.dim)}, 1.0, heat);
  float3 light = room * mix(float3(1.0), float3(1.0, 0.8, 0.58), 0.35 * (1.0 - heat)) + ${f(EXPOSE.direct)} * glow * tint;
  float lift = 1.0 + ${f(EXPOSE.adaptLift)} * uAdapt;
  float3 paper = mix(${v3(NIGHT.paper)} * lift + uLamp * glow * ${f(NIGHT_GLOW.paper)}, ${v3(DAY.paper)} * light, m);
${paper ? `  float3 col = paper + (vnoise(p * float2(0.7, 2.2) / uScale) - 0.5) * 0.016 + (hash12(floor(p * 3.0)) - 0.5) * 0.008;` : `
  // The type flips where the paper under it passes mid-tone instead of fading with it: a fade would take every glyph
  // through the paper's own colour as the front crosses it. The side is the ink that reads better on this paper. A
  // softer role leans further to that side while its own contrast is under the rest level: mute toward the ink, the
  // green toward a deeper or paler green, so the rule and the progress bar keep their hue.
  float3 dayInk = ${v3(DAY.ink)} * light;
  float3 nightInk = ${v3(NIGHT.ink)} * lift + uLamp * glow * ${f(NIGHT_GLOW.type)};
  bool day = contrast(dayInk, paper) >= contrast(nightInk, paper);
  float3 own = day ? ${v3(DAY[role])} * light : ${v3(NIGHT[role])} * lift + uLamp * glow * ${f(NIGHT_GLOW.type)};
  ${role === 'accent'
    ? `float3 far = day ? own * ${f(1 - LEGIBLE.accentShift)} : mix(own, float3(1.0), ${f(LEGIBLE.accentShift)});`
    : 'float3 far = day ? dayInk : nightInk;'}
  float3 col = mix(far, own, smoothstep(${f(LEGIBLE.floor)}, ${f(LEGIBLE.rest)}, contrast(own, paper)));`}
  return half4(half3(col), 1.0);
}`;
}

function air() {
  const c0 = Math.cos((AIR.cone[1] * Math.PI) / 180);
  const c1 = Math.cos((AIR.cone[0] * Math.PI) / 180);
  return `
uniform float2 uBulb;
uniform float2 uAxis;
uniform float uBright;
uniform float3 uLamp;
uniform float uTime;
uniform float uScale;
${NOISE}

float beam(float2 p) {
  float2 d = p - uBulb;
  float r = max(length(d), 0.001);
  float cone = smoothstep(${f(c0)}, ${f(c1)}, dot(d, uAxis) / r);
  float reach = ${f(AIR.reach)} * uScale;
  float fall = 1.0 / (1.0 + (r * r) / (reach * reach));
  return cone * fall * smoothstep(12.0 * uScale, 44.0 * uScale, r);
}

half4 main(float2 p) {
  const float TAU = 6.2831853;
  float w = TAU * uTime / 12.0; // every motion here repeats in the 12 s loop
  float b = beam(p);
  float2 drift = float2(sin(w), cos(w)) * 0.7;
  float haze = b * (0.7 + 0.6 * vnoise(p / (64.0 * uScale) + drift)) * ${f(AIR.haze)};
  float2 side = float2(uAxis.y, -uAxis.x);
  float ref = max(beam(uBulb + uAxis * 60.0 * uScale), 0.001);
  float motes = 0.0;
  for (int i = 0; i < ${AIR.motes}; i++) {
    float fi = float(i);
    float h1 = hash12(float2(fi, 1.7));
    float h2 = hash12(float2(fi, 9.3));
    float h3 = hash12(float2(fi, 4.1));
    float along = (40.0 + 230.0 * h1) * uScale;
    float across = (h2 - 0.5) * 1.6 * along;
    float2 c = uBulb + uAxis * along + side * across;
    c += float2(sin(w * (1.0 + floor(h3 * 2.0)) + h1 * TAU), sin(w + h2 * TAU)) * ${f(AIR.drift)} * uScale;
    float size = mix(${f(AIR.moteSize[0])}, ${f(AIR.moteSize[1])}, h3) * uScale;
    float2 o = p - c;
    float g = exp(-dot(o, o) / (size * size));
    float twinkle = 0.65 + 0.35 * sin(w * (1.0 + floor(h2 * 3.0)) + h3 * TAU);
    motes += g * twinkle * beam(c) / ref;
  }
  float3 col = uLamp * uBright * (haze + ${f(AIR.mote)} * min(motes, 1.0));
  col += (hash12(p * 1.37) - 0.5) / 255.0;
  col = max(col, float3(0.0));
  return half4(half3(col), half(max(col.r, max(col.g, col.b))));
}`;
}

function make(src: string, name: string): SkRuntimeEffect {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error(`pull-cord-theme: shader ${name} failed to compile`);
  return e;
}

export const SHADERS = {
  paper: make(page('paper', true), 'paper'),
  ink: make(page('ink', false), 'ink'),
  mute: make(page('mute', false), 'mute'),
  accent: make(page('accent', false), 'accent'),
  air: make(air(), 'air'),
};
