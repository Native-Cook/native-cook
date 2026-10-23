/**
 * Event Pass Share — the ambient light field (round 3).
 *
 * One colour story, "emerald aurora": a deep ink base lit by an emerald, a teal, a violet and a rose
 * glow, with one warm gold note borrowed from the poster's gold script. The field lives behind the
 * whole screen; the sheet is dark frosted glass over it, the stage is a brighter frosted window onto
 * it, and the pass throws the foil's own thin-film colour onto that window as a soft spill.
 *
 * Everything is a pure function of (loop time, tilt, flip angle): the drift has a period of exactly
 * one LOOP, so any LOOP-long cut loops; the colour follows the pass's tilt through a critically
 * damped follower (`stepAmbientFollower`) that is back at rest long before the loop's rest ends.
 * Geometry is in the pin's reference points (402 pt wide, `theme.ts`), colours in OKLab.
 */
import { LOOP } from './motion';
import { pass, shareButton, sheet, stage, link } from './theme';

/** OKLCH (L, C, h°) of each light; converted to OKLab once below and written into the shader. */
export const AURORA = {
  base: [0.19, 0.035, 232],
  /** centre x, y (ref pt), radius (pt), intensity, orbit radius (pt), orbit phase (rad), OKLCH. */
  lights: [
    { name: 'emerald', x: 112, y: 214, radius: 150, intensity: 0.95, orbit: 26, phase: 0.0, lch: [0.63, 0.15, 162] },
    { name: 'teal', x: 338, y: 352, radius: 128, intensity: 0.85, orbit: 22, phase: 2.1, lch: [0.66, 0.115, 203] },
    { name: 'gold', x: 372, y: 226, radius: 60, intensity: 0.62, orbit: 14, phase: 4.0, lch: [0.79, 0.13, 66] },
    { name: 'violet', x: 52, y: 572, radius: 150, intensity: 0.85, orbit: 24, phase: 1.0, lch: [0.5, 0.17, 290] },
    { name: 'rose', x: 356, y: 652, radius: 104, intensity: 0.6, orbit: 18, phase: 3.3, lch: [0.58, 0.16, 345] },
  ],
} as const;

export const AMBIENT = {
  /** Drift period: one LOOP (AGENTS.md: periodic motion repeats in the loop). Slow by amplitude: ≤ 22 pt/s. */
  period: LOOP,
  /** Looping noise warp: amplitude (pt), feature scale (1/pt), radius of the circle walked in noise space. */
  warp: 16,
  warpScale: 0.0075,
  warpCircle: 0.85,
  /** Colour reacting to tilt: parallax (pt per degree) and hue rotation (rad per degree). */
  parallax: 2.2,
  hueY: 0.02,
  hueX: 0.015,
  /** Follower spring: critically damped, ω 7 rad/s (settles in ≈ 0.8 s; the loop rests 1.9 s). */
  omega: 7,
  /** Sheet glass: share of the field seen through the charcoal, plus a bleed at its top edge. */
  sheetSeeThrough: 0.2,
  sheetTopBleed: 0.22,
  sheetBleedDepth: 38,
  /**
   * Stage window: full gain in the band above the pass, deeper behind it and towards the bottom so
   * every line on both faces of the pass keeps ≥ 4.5:1 (gain = top → mid over y 236…330, → bottom by 690).
   */
  stageGainTop: 1.25,
  stageGainMid: 0.7,
  stageGainBottom: 0.38,
  stageFadeFrom: 236,
  stageFadeMid: 330,
  stageFadeTo: 690,
  /** The window's light mirrored in the glass floor under the stage (it is what the CTA's glass picks up). */
  floorReflection: 0.3,
  floorDepth: 70,
  /** Spill of foil light around the pass: rest level, reach (pt) and strength. */
  spillRest: 0.12,
  spillReach: 20,
  spillStrength: 0.55,
  /** Spill shift per degree of tilt (pt). */
  spillShift: 1.6,
} as const;

const CHARCOAL = [0.0159, 0.0152, 0.0159]; // #222122 in linear sRGB

function oklab(lch: readonly number[]): [number, number, number] {
  const h = (lch[2] * Math.PI) / 180;
  return [lch[0], lch[1] * Math.cos(h), lch[1] * Math.sin(h)];
}

const f = (value: number) => value.toFixed(4);
const v2 = (x: number, y: number) => `float2(${f(x)}, ${f(y)})`;
const v3 = (c: readonly number[]) => `float3(${f(c[0])}, ${f(c[1])}, ${f(c[2])})`;

const LIGHTS = AURORA.lights.map((light) => {
  const lab = oklab(light.lch);
  return `  {
    float2 c = ${v2(light.x, light.y)} + ${f(light.orbit)} * float2(cos(th + ${f(light.phase)}), 0.7 * sin(th + ${f(light.phase)}));
    float2 d = q - c;
    float w = ${f(light.intensity)} * exp(-dot(d, d) / ${f(light.radius * light.radius)});
    lab = mix(lab, ${v3(lab)}, w);
  }`;
}).join('\n');

/** Shared SkSL: hashes, looping value noise, OKLab → linear sRGB, output encoding, the field. */
const COMMON = `
const float TAU = 6.2831853;
const float DEG = 0.01745329;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 g = fract(q);
  g = g * g * (3.0 - 2.0 * g);
  float a = hs(i);
  float b = hs(i + float2(1.0, 0.0));
  float c = hs(i + float2(0.0, 1.0));
  float d = hs(i + float2(1.0, 1.0));
  return mix(mix(a, b, g.x), mix(c, d, g.x), g.y);
}

float3 labToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return float3(
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

float enc1(float x) { return x <= 0.0031308 ? 12.92 * x : 1.055 * pow(x, 1.0 / 2.4) - 0.055; }

// Linear sRGB in, encoded out. Skia reads runtime-effect output as Display P3 on iOS (p3 = 1),
// so the colour is moved into P3 primaries first; Android keeps sRGB (p3 = 0).
half4 encodeOut(float3 lin, float p3, float grain) {
  lin = clamp(lin, 0.0, 1.0);
  if (p3 > 0.5) {
    lin = float3(
      dot(lin, float3(0.8225, 0.1774, 0.0)),
      dot(lin, float3(0.0332, 0.9669, 0.0)),
      dot(lin, float3(0.0171, 0.0724, 0.9108)));
  }
  float3 e = float3(enc1(lin.r), enc1(lin.g), enc1(lin.b)) + grain;
  return half4(half3(clamp(e, 0.0, 1.0)), 1.0);
}

// The light field at reference point q. amb = (drift angle, follower tilt X°, follower tilt Y°, glow).
float3 field(float2 q, float4 amb) {
  float th = amb.x;
  q -= float2(amb.z, -amb.y) * ${f(AMBIENT.parallax)};
  float2 o = ${f(AMBIENT.warpCircle)} * float2(cos(th), sin(th));
  float2 n = q * ${f(AMBIENT.warpScale)};
  q += ${f(AMBIENT.warp)} * float2(vn(n + o) - 0.5, vn(n - o + float2(17.0, 9.0)) - 0.5) * 2.0;
  float3 lab = ${v3(oklab(AURORA.base))};
${LIGHTS}
  float hr = amb.z * ${f(AMBIENT.hueY)} + amb.y * ${f(AMBIENT.hueX)};
  float cr = cos(hr);
  float sr = sin(hr);
  lab.yz = float2(lab.y * cr - lab.z * sr, lab.y * sr + lab.z * cr);
  lab.x *= 1.0 + 0.10 * amb.w;
  return max(labToLinear(lab), 0.0);
}

float sdBox(float2 p, float2 b, float r) {
  float2 d = abs(p) - b + r;
  return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}
`;

/**
 * Behind everything: the field as the page under the sheet (status bar band, and the whole screen
 * once the sheet is closed). Below the sheet's top it only draws the charcoal the sheet covers.
 * geo = (scale S, ref y at canvas y 0, sheet offset in ref pt, p3), res = canvas size (pt).
 */
export const PAGE_SKSL = `
uniform float2 res;
uniform float4 geo;
uniform float4 amb;
${COMMON}
half4 main(float2 p) {
  float2 r = float2(p.x / geo.x, geo.y + p.y / geo.x);
  float3 lin = float3(${f(CHARCOAL[0])}, ${f(CHARCOAL[1])}, ${f(CHARCOAL[2])});
  if (r.y < ${f(sheet.top + sheet.radius)} + geo.z) {
    float3 fld = field(r, amb);
    // Keep the status bar readable: the field darkens towards the top edge.
    lin = fld * mix(0.7, 0.95, smoothstep(10.0, 70.0, r.y));
  }
  return encodeOut(lin, geo.w, (hs(floor(p * 3.0)) - 0.5) * 0.012);
}
`;

const STAGE_C = [stage.left + stage.width / 2, stage.top + stage.height / 2];
const STAGE_H = [stage.width / 2, stage.height / 2];
const CARD_C = [pass.left + pass.width / 2, pass.top + pass.height / 2];
const CARD_H = [pass.width / 2, pass.height / 2];
const CTA_C = [shareButton.left + shareButton.width / 2, shareButton.top + shareButton.height / 2];
const CTA_H = [shareButton.width / 2, shareButton.height / 2];
const LINK_C = [201, link.top + link.hitHeight / 2];
const LINK_H = [link.width / 2, link.height / 2];
const CLOSE_C = [sheet.close.left + sheet.close.size / 2, sheet.headerTop + sheet.close.size / 2];

/**
 * The sheet: dark frosted glass with the field behind it, the stage as a bright frosted window, the
 * pass's foil spill on the window, and a faint spectral rim under each glass control (the glass
 * bends it into a dispersed edge). Drawn in the sheet's own content so it scrolls with it.
 * geo = (S, ref y at canvas y 0, sheet offset in ref pt, p3), card = (tilt X°, shade Y°, foil amount, 0).
 */
export const SHEET_SKSL = `
uniform float2 res;
uniform float4 geo;
uniform float4 amb;
uniform float4 card;
${COMMON}
float3 thinfilm(float opd) {
  return 0.5 + 0.5 * cos(TAU * opd / float3(650.0, 532.0, 450.0));
}

// The foil's colour where the pass's edge is nearest to r: the same thin-film model and angles as
// foil-shader.ts at that point of the poster, so spill and foil change colour together.
float3 foilAt(float2 o) {
  float tx = card.x * DEG;
  float ty = card.y * DEG;
  float3 nrm = normalize(float3(sin(ty), -sin(tx), 1.0));
  float3 eye = normalize(float3(-o, 650.0));
  float3 lgt = normalize(float3(-0.30, -0.70, 1.0));
  float3 hv = normalize(lgt + eye);
  float2 slope = nrm.xy - hv.xy;
  // Only the foil's broad film drift (its fine term would print bars along the pass's edges).
  float flow = 0.85 * vn(o * 0.016 + float2(3.1, 7.7)) + 0.15;
  float phase = dot(slope, float2(0.55, 0.84)) * 7.5 + flow;
  return thinfilm(400.0 + 470.0 * phase);
}

float rim(float2 r, float2 c, float2 h, float rad, float px) {
  float d = sdBox(r - c, h, rad) * geo.x;
  return exp(-(d - 0.9) * (d - 0.9) / 0.8) * px;
}

half4 main(float2 p) {
  float S = geo.x;
  float2 r = float2(p.x / S, geo.y + p.y / S);
  float2 q = r + float2(0.0, geo.z);
  float3 fld = field(q, amb);

  // Sheet glass: charcoal with the field showing through, more at the top edge where light enters.
  float bleed = exp(-(r.y - ${f(sheet.top)}) / ${f(AMBIENT.sheetBleedDepth)});
  float3 charcoal = float3(${f(CHARCOAL[0])}, ${f(CHARCOAL[1])}, ${f(CHARCOAL[2])});
  float3 lin = mix(charcoal, fld * 0.9, ${f(AMBIENT.sheetSeeThrough)} + ${f(AMBIENT.sheetTopBleed)} * bleed);

  // The stage's light mirrored in the glass below it, fading with distance: colour for the CTA's glass.
  float below = r.y - ${f(stage.top + stage.height)};
  if (below > 0.0) {
    float3 mirrored = field(float2(r.x, ${f(stage.top + stage.height)} - below * 1.6) + float2(0.0, geo.z), amb);
    lin += mirrored * ${f(AMBIENT.floorReflection)} * exp(-below / ${f(AMBIENT.floorDepth)}) * smoothstep(0.0, 6.0, below);
  }

  // Specular rim along the sheet's top edge (light catching the glass), tinted by the field.
  float topEdge = (r.y - ${f(sheet.top)}) * S;
  lin += (float3(0.10) + fld * 0.6) * exp(-topEdge * topEdge / 0.9) * (0.55 + 0.45 * exp(-abs(r.x - 201.0) / 120.0));

  // Foil spill: light from the pass, shifted towards where it faces, strongest at its edge.
  float2 shift = float2(card.y, -card.x) * ${f(AMBIENT.spillShift)};
  float2 cc = ${v2(CARD_C[0], CARD_C[1])} + shift;
  float dc = sdBox(r - cc, ${v2(CARD_H[0], CARD_H[1])}, ${f(pass.radius)});
  float reach = dc > 0.0 ? exp(-dc / ${f(AMBIENT.spillReach)}) : exp(dc / 5.0);
  float2 dir = r - cc;
  float face = 0.65 + 0.35 * dot(normalize(dir + 0.0001), normalize(float2(card.y, -card.x) + 0.0001)) * min(1.0, length(card.xy) / 4.0);
  float spill = (${f(AMBIENT.spillRest)} + (1.0 - ${f(AMBIENT.spillRest)}) * card.z) * reach * face;
  float3 spillCol = fld * 1.6 + 0.04;
  if (spill > 0.003) {
    float2 o = clamp(dir, -${v2(CARD_H[0], CARD_H[1])}, ${v2(CARD_H[0], CARD_H[1])}) * ${f(250 / pass.width)};
    spillCol = mix(spillCol, foilAt(o) * 0.9, 0.65);
  }

  // Stage window: the field brighter at the top, deep at the bottom where the caption sits.
  float ds = sdBox(r - ${v2(STAGE_C[0], STAGE_C[1])}, ${v2(STAGE_H[0], STAGE_H[1])}, ${f(stage.radius)}) * S;
  float inStage = clamp(0.5 - ds * 3.0, 0.0, 1.0);
  float gain = r.y < ${f(AMBIENT.stageFadeMid)}
    ? mix(${f(AMBIENT.stageGainTop)}, ${f(AMBIENT.stageGainMid)}, smoothstep(${f(AMBIENT.stageFadeFrom)}, ${f(AMBIENT.stageFadeMid)}, r.y))
    : mix(${f(AMBIENT.stageGainMid)}, ${f(AMBIENT.stageGainBottom)}, smoothstep(${f(AMBIENT.stageFadeMid)}, ${f(AMBIENT.stageFadeTo)}, r.y));
  float3 win = fld * gain + spillCol * spill * ${f(AMBIENT.spillStrength)};
  // Inner highlight of the window: bright on its top edge, a hairline elsewhere.
  float top = smoothstep(${f(stage.top + 40)}, ${f(stage.top)}, r.y);
  win += float3(0.9, 0.95, 1.0) * exp(-(ds + 0.6) * (ds + 0.6) / 0.35) * (0.05 + 0.16 * top);
  lin = mix(lin + spillCol * spill * 0.18, win, inStage);

  // Spectral rims under the glass controls: R up-left, B down-right (a hint of dispersion).
  float3 disp = float3(0.0);
  if (r.y > ${f(CTA_C[1] - CTA_H[1] - 8)}) {
    disp.r += rim(r + 0.35, ${v2(CTA_C[0], CTA_C[1])}, ${v2(CTA_H[0], CTA_H[1])}, ${f(CTA_H[1])}, 1.0);
    disp.g += rim(r, ${v2(CTA_C[0], CTA_C[1])}, ${v2(CTA_H[0], CTA_H[1])}, ${f(CTA_H[1])}, 1.0);
    disp.b += rim(r - 0.35, ${v2(CTA_C[0], CTA_C[1])}, ${v2(CTA_H[0], CTA_H[1])}, ${f(CTA_H[1])}, 1.0);
  } else if (r.y < ${f(LINK_C[1] + LINK_H[1] + 8)}) {
    disp.r += rim(r + 0.3, ${v2(LINK_C[0], LINK_C[1])}, ${v2(LINK_H[0], LINK_H[1])}, ${f(LINK_H[1])}, 1.0);
    disp.g += rim(r, ${v2(LINK_C[0], LINK_C[1])}, ${v2(LINK_H[0], LINK_H[1])}, ${f(LINK_H[1])}, 1.0);
    disp.b += rim(r - 0.3, ${v2(LINK_C[0], LINK_C[1])}, ${v2(LINK_H[0], LINK_H[1])}, ${f(LINK_H[1])}, 1.0);
    float2 cr = r - ${v2(CLOSE_C[0], CLOSE_C[1])};
    float dr = (length(cr + 0.3) - ${f(sheet.close.size / 2)}) * S;
    float dg = (length(cr) - ${f(sheet.close.size / 2)}) * S;
    float db = (length(cr - 0.3) - ${f(sheet.close.size / 2)}) * S;
    disp += float3(exp(-(dr - 0.9) * (dr - 0.9) / 0.8), exp(-(dg - 0.9) * (dg - 0.9) / 0.8), exp(-(db - 0.9) * (db - 0.9) / 0.8));
  }
  lin += disp * (fld * 1.4 + 0.05) * 0.5;

  float grain = (hs(floor(p * 3.0)) - 0.5) * mix(0.010, 0.018, inStage);
  return encodeOut(lin, geo.w, grain);
}
`;

/**
 * Shading angle and light amount of the pass for the spill and the follower: identical to
 * `foilUniform` while the front shows, mirrored past 90° so the light flares edge-on and is
 * gone again with the back flat.
 */
export function spillUniform(tiltX: number, tiltY: number, flipAngle: number): [number, number, number] {
  'worklet';
  const turn = Math.min(90, Math.max(0, flipAngle <= 90 ? flipAngle : 180 - flipAngle));
  const shadeY = tiltY + turn * 0.2;
  const tilt = Math.min(1, Math.hypot(tiltX / 6, tiltY / 7.5));
  const lit = Math.min(1, tilt + Math.sin((turn * Math.PI) / 180) * 0.8);
  return [tiltX, shadeY, lit * lit * (3 - 2 * lit)];
}

/** Drift angle (rad) of the field at logical time t: one turn per LOOP. */
export function ambientAngle(t: number): number {
  'worklet';
  const u = (t % AMBIENT.period) / AMBIENT.period;
  return (u < 0 ? u + 1 : u) * Math.PI * 2;
}

/**
 * One step of the critically damped follower that carries the field's colour after the pass:
 * state = [x, vx, y, vy, glow, vglow], target = (tilt X, shade Y, amount). Sub-steps keep it stable
 * at any frame time; the ports run the same step at their frame rate.
 */
export function stepAmbientFollower(state: number[], tx: number, ty: number, glow: number, dt: number): void {
  'worklet';
  const w = AMBIENT.omega;
  let left = Math.min(Math.max(dt, 0), 0.1);
  while (left > 0) {
    const h = Math.min(left, 1 / 240);
    left -= h;
    state[1] += (w * w * (tx - state[0]) - 2 * w * state[1]) * h;
    state[0] += state[1] * h;
    state[3] += (w * w * (ty - state[2]) - 2 * w * state[3]) * h;
    state[2] += state[3] * h;
    state[5] += (w * w * (glow - state[4]) - 2 * w * state[5]) * h;
    state[4] += state[5] * h;
  }
}
