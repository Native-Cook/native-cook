import { D, HUES, checklistGeometry, rowCentreY } from './checklist-theme';

/** Plain counterpart of ChecklistMotion; also the input to the offline renderer. */
export type ChecklistFrame = {
  clock: number; present: number; letters: number[]; open: number; press: number;
  lead: number[]; trail: number[]; hold: number[]; fill: number; hue: number;
  tip: number; surge: number; flash: number; glare: number; still: number;
};

function unit(n: number) {
  'worklet';
  return Math.max(0, Math.min(1, n));
}

function toLinear(n: number) {
  'worklet';
  return n <= 0.04045 ? n / 12.92 : Math.pow((n + 0.055) / 1.055, 2.4);
}

function toSRGB(n: number) {
  'worklet';
  return unit(n <= 0.0031308 ? n * 12.92 : 1.055 * Math.pow(Math.max(0, n), 1 / 2.4) - 0.055);
}

function toLab(rgb: readonly number[]) {
  'worklet';
  const r = toLinear(rgb[0]); const g = toLinear(rgb[1]); const b = toLinear(rgb[2]);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s];
}

function fromLab(lab: number[]) {
  'worklet';
  const l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2];
  const m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2];
  const s = lab[0] - 0.0894841775 * lab[1] - 1.291485548 * lab[2];
  return [toSRGB(4.0767416621 * l * l * l - 3.3077115913 * m * m * m + 0.2309699292 * s * s * s),
    toSRGB(-1.2684380046 * l * l * l + 2.6097574011 * m * m * m - 0.3413193965 * s * s * s),
    toSRGB(-0.0041960863 * l * l * l - 0.7034186147 * m * m * m + 1.707614701 * s * s * s)];
}

function colourBetween(a: readonly number[], b: readonly number[], t: number) {
  'worklet';
  const left = toLab(a); const right = toLab(b);
  return fromLab([left[0] + (right[0] - left[0]) * t,
    left[1] + (right[1] - left[1]) * t, left[2] + (right[2] - left[2]) * t]);
}

export function checklistColour(hue: number, tip: number) {
  'worklet';
  const h = Math.max(0, Math.min(3, hue));
  const i = Math.min(2, Math.floor(h));
  return colourBetween(colourBetween(HUES[i], HUES[i + 1], h - i), HUES[4], unit(tip));
}

function geometryUniforms(frame: ChecklistFrame, width: number, height: number, k: number) {
  'worklet';
  const g = checklistGeometry(unit(frame.open), width, height, k);
  return {
    uCentre: [g.cx, g.capCy], uScale: k * (1 - 0.05 * unit(frame.press)),
    uCap: [g.capHalfW / k * unit(frame.present), g.capHalfH / k,
      Math.min(D.cap.r, D.cap.w * unit(frame.present) / 2)],
    uSlab: [(g.slabRight - g.slabLeft) / (2 * k), (g.slabBottom - g.capCy) / k, D.slab.r],
    uPresent: unit(frame.present), uOpen: unit(frame.open), uCalm: unit(frame.tip),
    uColour: checklistColour(frame.hue, frame.tip),
    uTime: frame.still >= 0.5 ? 0 : frame.clock,
    uStill: frame.still >= 0.5 ? 1 : 0,
  };
}

export function haloUniforms(frame: ChecklistFrame, width: number, height: number, k: number) {
  'worklet';
  const tip = unit(frame.tip);
  const baseReach = 60 + 24 * unit(frame.open) + 12 * Math.max(0, Math.min(3, frame.fill));
  return {
    ...geometryUniforms(frame, width, height, k), uScreen: [width, height], uRaster: 3,
    uReach: baseReach + (160 - baseReach) * tip + Math.max(0, frame.tip - 1) * 160,
    uCalm: tip, uFill: frame.fill,
    uSmokeColour: checklistColour(frame.hue, 0), uSmokeReach: baseReach,
    uSurge: frame.still >= 0.5 ? 0 : frame.surge,
  };
}

export function glassUniforms(frame: ChecklistFrame, width: number, height: number, k: number) {
  'worklet';
  const g = checklistGeometry(unit(frame.open), width, height, k);
  const rows: number[] = [];
  for (let i = 0; i < 4; i++) rows.push((rowCentreY(i, g.slabBottom, k) - g.capCy) / k);
  return {
    ...geometryUniforms(frame, width, height, k), uRaster: 1,
    uRemainder: checklistColour(frame.hue, 0), uFill: frame.fill, uTip: unit(frame.tip),
    uFlash: unit(frame.flash), uGlare: frame.still >= 0.5 ? -1 : frame.glare,
    uRows: rows, uLead: frame.lead, uTrail: frame.trail, uHold: frame.hold,
    uSwitch: [D.sw.x - D.cap.w / 2, D.sw.w, D.sw.h, D.sw.knob],
    uTravel: [D.sw.inset, D.sw.travel],
  };
}

const COMMON_SKSL = `
uniform float2 uCentre;
uniform float uScale;
uniform float3 uCap;
uniform float3 uSlab;
uniform float uPresent;
uniform float uOpen;
uniform float3 uColour;
uniform float uTime;
uniform float uStill;
uniform float uRaster;
uniform float uCalm;

float sq(float v) { return v * v; }
float hash12(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float noise2(float2 p) {
  float2 cell = floor(p); float2 f = fract(p);
  float2 w = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash12(cell), hash12(cell + float2(1,0)), w.x),
    mix(hash12(cell + float2(0,1)), hash12(cell + float2(1,1)), w.x), w.y);
}
float fbm(float2 p) {
  return noise2(p) * 0.57 + noise2(p * 2.03 + 7.1) * 0.29 + noise2(p * 4.11 + 19.7) * 0.14;
}
float roundedBox(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float capSDF(float2 p) { return roundedBox(p, uCap.xy, uCap.z); }
float slabSDF(float2 p) {
  return roundedBox(p - float2(0, uSlab.y * 0.5), float2(uSlab.x, uSlab.y * 0.5), uSlab.z);
}
float silhouette(float2 p) {
  float capD = capSDF(p);
  return uOpen > 0.001 ? min(capD, slabSDF(p)) : capD;
}
// Identical advection and domains on both canvases: the glass transmits this gas.
// Two three-octave fields and one scalar domain warp, independent of frame rate.
float2 gasFields(float2 p) {
  float t = uTime * mix(1.0, 0.30, uCalm);
  float2 origin = float2(0, (uSlab.y - uCap.y) * 0.5);
  float2 radial = normalize(p - origin + float2(0.001));
  float2 domain = p / mix(float2(43.0, 65.0), float2(108.0, 138.0), uCalm)
    - radial * t * 0.48 + float2(0, t * 0.64);
  float warp = noise2(domain * 0.72 + float2(t * 0.13, 7.3));
  float2 warped = domain + float2(warp - 0.5, 0.5 - warp) * 2.6;
  float body = fbm(warped);
  float filaments = fbm(warped * mix(1.85, 1.10, uCalm) + float2(13.1, t * 0.16));
  return float2(body, filaments);
}
// Rotate around the neutral axis by up to eight degrees, retaining saturated veins.
float3 tintedGas(float field, float3 colour) {
  float angle = clamp((field - 0.5) * 4.0, -1.0, 1.0) * 0.139626;
  float3 axis = float3(0.577350);
  return max(float3(0), colour * cos(angle) + cross(axis, colour) * sin(angle)
    + axis * dot(axis, colour) * (1.0 - cos(angle)));
}
float3 gasTint(float field) { return tintedGas(field, uColour); }
float gasModulation(float field, float filled) {
  float depth = mix(mix(0.65, 0.50, clamp(filled / 3.0, 0.0, 1.0)), 0.30, uCalm);
  return 1.0 - depth + depth * smoothstep(0.25, 0.72, field);
}

// Periodic angular domains: no atan seam, and exactly two cheap noise lookups.
// Both canvases use this field, so transmitted rays move with the light outside.
float2 rayFields(float2 p) {
  float2 q = p - float2(0, (uSlab.y - uCap.y) * 0.5);
  float2 direction = q / max(length(q), 0.001);
  float a = uTime * 0.035;
  float b = -uTime * 0.052;
  float2 broad = float2(direction.x * cos(a) - direction.y * sin(a),
    direction.x * sin(a) + direction.y * cos(a));
  float2 fineRay = float2(direction.x * cos(b) - direction.y * sin(b),
    direction.x * sin(b) + direction.y * cos(b));
  return float2(noise2(broad * 2.7 + float2(9.7, 3.2)),
    noise2(fineRay * 9.0 + float2(21.3, 8.1)));
}
float rayStrength(float2 fields) {
  float broad = smoothstep(0.22, 0.78, fields.x);
  return 0.60 + 0.80 * broad + (fields.y - 0.5) * 0.24;
}

`;

export const HALO_SKSL = COMMON_SKSL + `
uniform float2 uScreen;
uniform float uReach;
uniform float uFill;
uniform float uSurge;
uniform float3 uSmokeColour;
uniform float uSmokeReach;

// Compact profile: 0.8 at the glass, 0.4 at R/2, 0.06 at R, zero at 1.35R.
float gasProfile(float x) {
  if (x < 0.5) return mix(0.80, 0.40, smoothstep(0.0, 0.5, x));
  if (x < 1.0) return mix(0.40, 0.06, smoothstep(0.5, 1.0, x));
  return 0.06 * (1.0 - smoothstep(1.0, 1.35, x));
}
float ellipseSDF(float2 p) {
  float halfH = (uSlab.y + uCap.y) * 0.5;
  float2 q = p - float2(0, halfH - uCap.y);
  float2 axes = float2(max(uCap.x, 1.0), max(halfH, 1.0));
  float norm = length(q / axes);
  return (norm - 1.0) * length(q) / max(norm, 0.0001);
}
// A stable hashed point per cell. Each two-second life fades to zero before
// repeating; the moving domain carries the same point upward without respawning.
float moteLayer(float2 p, float cellSize, float speed, float seed) {
  float2 domain = (p + float2(0, uTime * speed)) / cellSize;
  float2 cell = floor(domain);
  float id = hash12(cell + seed);
  float2 centre = 0.2 + 0.6 * float2(hash12(cell + seed + 17.0), hash12(cell + seed + 41.0));
  float2 delta = (fract(domain) - centre) * cellSize;
  float radius = mix(1.25, 2.0, id);
  float life = sq(sin(3.141593 * fract(uTime * 0.5 + id)));
  return exp(-dot(delta, delta) / sq(radius)) * life * smoothstep(0.68, 0.83, id);
}
half4 main(float2 xy) {
  if (uPresent < 0.02) return half4(0);
  float2 screen = xy * uRaster;
  float2 p = (screen - uCentre) / uScale;
  float rectD = silhouette(p);
  float away = max(0.0, rectD);
  bool surging = uSurge > 0.001 && uSurge < 1.0 && uStill < 0.5;
  // During the surge the old smoke keeps its own size and unrest until the front reaches it.
  float smokeCalm = surging ? 0.0 : uCalm;
  float2 fields = float2(0.5);
  if (smokeCalm < 0.999) fields = gasFields(p);
  float unsettled = mix(0.55, 0.35, clamp(uFill * 0.5, 0.0, 1.0));
  unsettled = mix(unsettled, 0.28, clamp(uFill - 2.0, 0.0, 1.0));
  float displacement = mix(unsettled, 0.10, smokeCalm);
  float tongues = clamp((fields.x - 0.5) * 4.0, -1.0, 1.0);
  float breath = 1.0 + 0.04 * uCalm * sin(uTime * 1.570796) * (1.0 - uStill);
  float reach = uReach * breath;
  float smokeReach = surging ? uSmokeReach : reach;
  // Anchor the first few points at the glass, then let the tongues spread outward.
  float displaced = max(0.0, away - tongues * displacement * smokeReach * smoothstep(0.0, 24.0, away));
  float depthOfGas = mix(mix(0.65, 0.50, clamp(uFill / 3.0, 0.0, 1.0)), 0.30, smokeCalm);
  float gas = gasProfile(displaced / max(1.0, smokeReach))
    * (1.0 - depthOfGas + depthOfGas * smoothstep(0.25, 0.72, fields.y));
  float ambient = 0.11 * sq(1.0 - smoothstep(0.0, 2.4 * smokeReach, away));
  // Once resolved, no rectangular smoke envelope may remain ahead of the rays.
  if (smokeCalm >= 0.999) { gas = 0.0; ambient = 0.0; }
  float core = exp(-away / 7.0);
  float3 tint = gasTint(fields.y);
  float3 crestLight = float3(0);
  float calm = uCalm;
  float aura = 0.0;
  float dust = 0.0;
  float flash = 0.0;
  float front = 1000.0;
  float band = 0.0;
  float2 rays = float2(0.5);
  float depth = 0.0;
  // The unresolved material ahead of the front retains the preceding hue.
  if (surging) tint = tintedGas(fields.y, uSmokeColour);
  if (surging) {
    float remaining = max(0.0001, 1.0 - uSurge);
    front = 260.0 * (1.0 - pow(remaining, 2.2));
    float waveD = mix(rectD, ellipseSDF(p), smoothstep(0.0, 180.0, front));
    float waveOffset = waveD - front + (fields.x - 0.5) * 24.0;
    band = exp(-sq(waveOffset / (waveOffset < 0.0 ? 40.0 : 27.0))) * remaining;
    // Resolution is spatial: the teal gas survives ahead of the advancing front.
    calm = 1.0 - smoothstep(front - 70.0, front + 46.0, waveD);
    flash = (1.0 - smoothstep(0.0, 0.12, uSurge));
    core = exp(-away / mix(7.0, 22.0, flash)) * (1.0 + 2.0 * flash);
  }
  if (uCalm > 0.001) {
    rays = rayFields(p);
    float halfH = (uSlab.y + uCap.y) * 0.5;
    float2 q = p - float2(0, halfH - uCap.y);
    // The outer light is elliptical even for a closed capsule. The first 20pt
    // remain attached to the glass; by 60pt only the oval envelope remains.
    float swell = max(0.0, uReach - 160.0) / 160.0;
    float shimmer = 1.0 + 0.035 * sin(uTime * 0.7 + rays.x * 9.0);
    // The ray field stretches the oval into unequal beams; it does not paint petals on it.
    // Beside the panel the beams are seen close to their origin, where the fine ones would
    // bunch into a comb: let only the broad ones through there.
    float upright = smoothstep(0.35, 0.85, abs(q.y) / max(length(q), 0.001));
    float fineRay = mix(0.5, rays.y, mix(0.25, 1.0, upright));
    float beam = mix(0.80, 1.28, smoothstep(0.25, 0.75, 0.5 * (rays.x + fineRay)));
    float beamReach = (1.0 + 1.2 * swell) * breath * shimmer * beam;
    float2 axes = float2(uCap.x + 110.0 * beamReach,
      halfH + mix(150.0, 185.0, uOpen) * beamReach);
    float oval = length(q / axes);
    depth = smoothstep(0.35, 1.25, oval);
    // Light in fog: a hot centre and a long tail, no plateau and no edge.
    float fog = 0.62 * exp(-3.4 * sq(oval))
      + 0.42 * exp(-1.15 * sq(oval)) * (1.0 - smoothstep(1.2, 2.0, oval));
    aura = mix(0.80 * exp(-away / 100.0), fog, smoothstep(20.0, 60.0, away));
    // Beams ignite inside-out and retain unequal soft lengths after settling.
    float ignition = surging ? 1.0 - smoothstep(front - 90.0, front + 40.0, max(0.0, ellipseSDF(p))) : 1.0;
    aura *= mix(0.52, 0.72 + 0.56 * fineRay, ignition);
    aura *= (1.0 + 1.3 * band) * (1.0 + 1.1 * swell);
    if (surging) {
      float organic = sq(smoothstep(0.35, 0.75, rays.y)) * smoothstep(0.25, 0.7, rays.x);
      float waveOffset = ellipseSDF(p) - front + (rays.y - 0.5) * 22.0;
      // The broken crest is subordinate to the broad pressure brightening.
      float crest = exp(-sq(waveOffset / 9.0)) * 0.25 * sq(1.0 - uSurge) * organic;
      crestLight = mix(uColour, float3(1), 0.4) * crest;
    }
    if (uCalm > 0.6) {
      dust = (moteLayer(p, 62.0, 8.0, 3.7) + moteLayer(p, 89.0, 13.0, 39.1))
        * 0.16 * aura * smoothstep(0.6, 1.0, uCalm);
    }
  }
  // Add emitted layers before the shoulder; no clamped flat neon plateaus.
  float3 radiance = mix(uColour, float3(1), 0.30) * core
    + tint * gas + uColour * ambient + crestLight;
  if (uCalm > 0.001) {
    // Mint by the glass, emerald in the body, deep green in the tail.
    float3 resolved = mix(float3(0.20, 0.95, 0.50), float3(0.02, 0.50, 0.30), depth);
    resolved = mix(resolved, float3(0.36, 0.95, 0.30), 0.18 * smoothstep(0.45, 0.85, rays.x));
    float3 calmLight = mix(uColour, float3(1), 0.30 + 0.25 * flash) * core
      + resolved * aura + mix(resolved, float3(1), 0.65) * dust + crestLight;
    radiance = mix(radiance, calmLight, calm);
  }
  float3 rgb = 1.0 - exp(-radiance * 1.4);
  float veil = smoothstep(60.0, 160.0, screen.y)
    * (1.0 - smoothstep(uScreen.y - 130.0, uScreen.y - 6.0, screen.y));
  // Retain a dim, continuous room wash at the sides; a wave may leave the viewport.
  float side = smoothstep(0.0, 100.0, min(screen.x, uScreen.x - screen.x));
  veil *= mix(mix(0.12, 1.0, side), 1.0, uCalm);
  float visibility = smoothstep(0.02, 0.2, uPresent) * uPresent;
  float grain = (hash12(floor(screen * 2.0)) - 0.5) * (4.0 / 255.0);
  rgb = max(float3(0), rgb + grain * smoothstep(0.0, 0.02, mix(gas + ambient, aura, calm)));
  return half4(half3(rgb * veil * visibility), 1.0);
}
`;

export const GLASS_SKSL = COMMON_SKSL + `
uniform float3 uRemainder;
uniform float uFill;
uniform float uTip;
uniform float uFlash;
uniform float uGlare;
uniform float uRows[4];
uniform float uLead[4];
uniform float uTrail[4];
uniform float uHold[4];
uniform float4 uSwitch;
uniform float2 uTravel;

// Front-over-back, with premultiplied output throughout.
float4 over(float4 back, float3 rgb, float alpha) {
  return float4(rgb * alpha + back.rgb * (1.0 - alpha), alpha + back.a * (1.0 - alpha));
}
float coverage(float sdf, float aa) { return 1.0 - smoothstep(-aa, aa, sdf); }

half4 main(float2 xy) {
  if (uPresent < 0.02) return half4(0);
  float2 p = (xy - uCentre) / uScale;
  float aa = 0.45 / uScale;
  float capD = capSDF(p); float slabD = slabSDF(p);
  float outline = silhouette(p);
  if (outline > 32.0) return half4(0);
  float flashGain = 1.0 + 1.5 * uFlash;
  float3 rimColour = mix(uColour, float3(1), 0.20 * uTip);
  float bloom = exp(-max(0.0, outline) / 3.6) * 0.16 * flashGain;
  float4 outColour = float4(mix(rimColour, float3(1.0), 0.35) * bloom, bloom);
  float fine = (hash12(floor(xy * 2.0)) - 0.5) * 0.03;
  if (uOpen > 0.001) {
    float slabAlpha = coverage(slabD, aa) * 0.92;
    float vertical = clamp((p.y - uCap.y) / max(1.0, uSlab.y - uCap.y), 0.0, 1.0);
    float3 glass = float3(mix(0.169, 0.102, vertical));
    float edgeLight = exp(-max(0.0, -slabD) / 36.0);
    float2 fields = gasFields(p);
    float3 transmitted = gasTint(fields.y);
    glass += transmitted * edgeLight * 0.20 * (1.0 + 0.15 * uTip)
      * (0.88 + 0.24 * gasModulation(fields.y, uFill)) * flashGain;
    glass += transmitted * (fields.x - 0.35) * 0.075 + fine * 0.45;
    if (uCalm > 0.001) {
      glass += transmitted * edgeLight * (rayStrength(rayFields(p)) - 1.0) * 0.065 * uCalm;
    }
    for (int i = 0; i < 4; i++) {
      float active = clamp((uLead[i] + uTrail[i]) * 0.5, 0.0, 1.0);
      float2 fromSwitch = p - float2(uSwitch.x + uSwitch.y * 0.5, uRows[i]);
      float pool = exp(-dot(fromSwitch, fromSwitch) / (22.0 * 22.0)) * active * 0.25;
      glass += float3(0.08, 0.78, 0.24) * pool;
    }
    float bevel = 0.73 + 0.27 * abs((p.x / uSlab.x + (p.y / max(1.0, uSlab.y) - 0.5)) * 0.8);
    float rim = coverage(abs(slabD + 0.5) - 0.5, aa);
    float innerDark = exp(-sq((slabD + 1.65) / 0.65)) * 0.35;
    glass *= 1.0 - innerDark;
    glass = mix(glass, mix(float3(0.604), rimColour, 0.5) * bevel * flashGain, rim);
    float capShadow = exp(-max(0.0, p.y - uCap.y) / 10.0) * 0.45;
    glass *= 1.0 - capShadow;
    outColour = over(outColour, glass, slabAlpha);
    // Channels and ceramic capsules. The row curtain shares the cap's exact bottom edge.
    float curtain = smoothstep(uCap.y, uCap.y + 10.0, p.y);
    for (int i = 0; i < 4; i++) {
      float2 q = p - float2(uSwitch.x + uSwitch.y * 0.5, uRows[i]);
      if (abs(q.x) < 42.0 && abs(q.y) < 27.0) {
        float trackD = roundedBox(q, uSwitch.yz * 0.5, uSwitch.z * 0.5);
        float a = clamp(uLead[i], 0.0, 1.0); float b = clamp(uTrail[i], 0.0, 1.0);
        float restLeft = -uSwitch.y * 0.5 + uTravel.x + uSwitch.w * 0.5;
        float extra = clamp(uHold[i], 0.0, 1.0) * 5.0;
        float low = max(restLeft, restLeft + min(a, b) * uTravel.y - extra * (a + b) * 0.5);
        float high = min(restLeft + uTravel.y, restLeft + max(a, b) * uTravel.y + extra * (1.0 - (a + b) * 0.5));
        float mid = (low + high) * 0.5;
        float active = (a + b) * 0.5;
        float lit = (1.0 - smoothstep(mid - 4.0, mid + 4.0, q.x)) * smoothstep(0.0, 0.45, active);
        // At the on rest the emissive channel reaches the rounded right wall.
        lit = mix(lit, 1.0, smoothstep(0.80, 1.0, active));
        float3 track = mix(float3(0.416, 0.416, 0.432), float3(0.133, 0.69, 0.29), lit);
        track += float3(0.05, 0.19, 0.08) * lit * exp(-sq(q.y / 7.0));
        track *= 1.0 - 0.40 * exp(-sq((q.y + uSwitch.z * 0.5 - 1.0) / 2.0));
        float lip = exp(-sq((trackD + 0.6) / 0.5)) * smoothstep(0.0, 10.0, q.y) * 0.18;
        track += lip;
        outColour = over(outColour, track, coverage(trackD, aa) * curtain);
        float knobR = uSwitch.w * 0.5;
        float2 knobQ = q - float2(mid, 0);
        float2 knobHalf = float2(knobR + (high - low) * 0.5, knobR);
        float knobD = roundedBox(knobQ, knobHalf, knobR);
        float shadowD = roundedBox(knobQ - float2(0, 1.5), knobHalf, knobR);
        float contact = exp(-max(shadowD, 0.0) / 3.0) * 0.45 * coverage(trackD, aa) * curtain;
        outColour = over(outColour, float3(0), contact);
        float whiteGlow = exp(-max(knobD, 0.0) / 2.8) * (0.30 + 0.12 * uHold[i]) * curtain;
        outColour = over(outColour, float3(0.94, 1.0, 0.96), whiteGlow);
        float dome = clamp((knobQ.x / max(knobHalf.x, 1.0) + knobQ.y / knobR + 2.0) * 0.25, 0.0, 1.0);
        float3 ceramic = mix(float3(1), float3(0.851, 0.851, 0.871), dome);
        float edge = exp(-sq((knobD + 0.3) / 0.6));
        ceramic *= 1.0 - edge * 0.16;
        float arc = exp(-sq((knobD + 1.6) / 0.5)) * (1.0 - smoothstep(-2.0, 3.0, knobQ.x + knobQ.y));
        ceramic += arc * 0.12;
        ceramic = mix(ceramic, float3(0.69, 1.0, 0.76), lit * edge * smoothstep(-2.0, 8.0, knobQ.y) * 0.23);
        outColour = over(outColour, ceramic, coverage(knobD, aa) * curtain);
      }
    }
  }
  float capAlpha = coverage(capD, aa);
  float across = (p.x + uCap.x) / max(0.001, uCap.x * 2.0);
  float filled = clamp(uFill * 0.25, 0.0, 1.0);
  float boundary = (across - filled) * uCap.x * 2.0;
  float greenShare = filled < 0.001 ? 0.0 : filled > 0.999 ? 1.0 : 1.0 - smoothstep(-1.5, 1.5, boundary);
  float topLight = 1.0 - clamp((p.y + uCap.y) / (uCap.y * 2.0), 0.0, 1.0);
  float3 remainder = uRemainder * 0.24 + float3(0.045, 0.025, 0.027);
  float3 greenGlass = mix(float3(0.184, 0.42, 0.247), float3(0.153, 0.722, 0.29), uTip);
  float3 cap = mix(remainder, greenGlass, greenShare) * (0.70 + 0.30 * topLight);
  cap += uColour * exp(-max(0.0, -capD) / 13.0) * 0.11;
  float cellCoord = across * 8.0;
  float cellEdge = abs(fract(cellCoord + 0.5) - 0.5) * (uCap.x * 2.0 / 8.0);
  cap *= 1.0 - (1.0 - smoothstep(0.3, 0.85, cellEdge)) * greenShare * 0.35;
  float leading = exp(-sq(boundary / 1.5)) * float(filled > 0.001 && filled < 0.999);
  cap += float3(0.15, 0.5, 0.23) * leading * 0.22;
  float lowerShade = exp(-max(0.0, -capD) / 3.0) * smoothstep(0.0, uCap.y, p.y) * 0.32;
  cap *= 1.0 - lowerShade;
  float highlight = exp(-sq((p.y + uCap.y - 1.8) / 0.65)) * (1.0 - smoothstep(0.65, 0.98, abs(p.x) / max(1.0, uCap.x)));
  cap += highlight * float3(0.5) * 0.4;
  if (uGlare >= 0.0 && uGlare <= 1.0) {
    float streak = p.x + p.y * 0.364 - mix(-uCap.x - 25.0, uCap.x + 25.0, uGlare);
    cap += float3(1.0) * (0.22 * exp(-sq(streak / 8.0)) + 0.14 * exp(-sq((streak - 21.0) / 3.0)));
  }
  float rim = coverage(abs(capD + 0.5) - 0.5, aa);
  float directional = 0.85 + 0.15 * topLight;
  cap = mix(cap, mix(float3(0.784), rimColour, 0.35) * directional * flashGain, rim);
  cap += fine * 0.30;
  outColour = over(outColour, cap, capAlpha);
  float visibility = smoothstep(0.02, 0.2, uPresent);
  return half4(outColour * visibility);
}
`;
