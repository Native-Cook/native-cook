/**
 * Spark Sphere Score — every number that moves.
 *
 * One value, the pace `v` (−1 … 3, in x), drives the whole screen: the sparks' temperature and state of matter, the membrane,
 * the colour, the ruler's cursor, the score, the card. `v` comes from the finger, from a spring after the finger lets go, or
 * from the 8 s script (`scriptAt`) when `autoPlay` is on. Everything else is a function of `v` and of a few small integrators
 * (swirl, agitation) that the same step function advances in the live gesture and in the script's precomputed table, so the
 * loop and the finger are the same physics.
 *
 * Heat vocabulary: gas (v > 0.8: amber, sparks across the whole disc, wobbling membrane), liquid (v = 0.8, the pin), crystal
 * (v < 0.35: sparks freeze onto a lattice, the membrane sharpens, mint). `cold` and `fill` are continuous, so scrubbing
 * through a state change is a morph.
 */
export const LOOP = 8;

export const V = { min: -1, max: 3, rest: 0.8, step: 0.05, detent: 0.1 } as const;
/** finger travel per 0.05x is one tick pitch (comp.ruler.pitch); the mapping constants below are in x per pt. */
export const GESTURE = {
  xPerPt: 0.05 / 4.174,
  overPt: 18, // rubber band: the cursor may be pulled this far past either end
  overK: 0.3,
  releaseProject: 0.12, // s, the release target looks this far ahead
  idleResume: 2, // s after the last touch before the loop may restart
  grab: 0.1, // s the cursor takes to grow under the finger
  hint: { at: 1.4, ticks: 3 }, // the discovery nudge: s after mount, ticks to the right and back
  probeRadius: 70,
  probeHeat: 0.35,
  dimpleReach: 24,
  dimple: 6,
  stirPerPt: 0.012, // rad/s per pt/s of a finger circling the disc
  stirMax: 4,
  keyStep: 0.1,
} as const;

/** The cursor past either end of the ruler: 0.3 pt per pt of finger travel, never more than GESTURE.overPt (a rubber band). */
export function rubberPt(over: number): number {
  'worklet';
  return over <= 0 ? 0 : GESTURE.overPt * (1 - Math.exp(-(over * GESTURE.overK) / GESTURE.overPt));
}

// Named springs (DESIGN-SYSTEM §8) and the block's own return spring.
export const SPRINGS = {
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  detent: { damping: 40.4, stiffness: 503.5, mass: 1 },
  /** the slow return of the cell cooling (concept: 14 / 60 / 1, about 1.2 s) */
  cool: { damping: 14, stiffness: 60, mass: 1 },
  dimple: { damping: 14, stiffness: 220, mass: 1 },
  cursor: { damping: 16, stiffness: 320, mass: 1 },
} as const;
export const COOL = { response: (2 * Math.PI) / Math.sqrt(60), zeta: 14 / (2 * Math.sqrt(60)) };

/** Lead / lag, seconds after the cursor: the sparks at the rim, the sparks in the hollow, the rim line's colour, the card, the number. */
export const LAG = { rim: 0.15, hollow: 0.4, film: 0.27, card: 0.2, score: 0.06 } as const;

// ---- small worklet maths -------------------------------------------------------------------------------------------------
export function clamp(x: number, a: number, b: number): number {
  'worklet';
  return x < a ? a : x > b ? b : x;
}
export function mix(a: number, b: number, t: number): number {
  'worklet';
  return a + (b - a) * t;
}
export function smoothstep(a: number, b: number, x: number): number {
  'worklet';
  const t = clamp((x - a) / (b - a), 0, 1);
  return t * t * (3 - 2 * t);
}
export function seg(t: number, a: number, b: number): number {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}
/** cubic-bezier(0.65, 0, 0.35, 1), the batch's `inOut`, by Newton on x. */
export function inOut(p: number): number {
  'worklet';
  const x1 = 0.65;
  const y1 = 0;
  const x2 = 0.35;
  const y2 = 1;
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let u = p;
  for (let i = 0; i < 6; i++) {
    const x = ((ax * u + bx) * u + cx) * u - p;
    const dx = (3 * ax * u + 2 * bx) * u + cx;
    if (Math.abs(dx) < 1e-6) break;
    u -= x / dx;
  }
  u = clamp(u, 0, 1);
  return ((ay * u + by) * u + cy) * u;
}
/** 0 → 1 step response of a damped spring (mass 1), analytic. */
export function springAt(t: number, response: number, zeta: number): number {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

// ---- the script (autoPlay) ------------------------------------------------------------------------------------------------
/** Loop table: [start, end, what]. Rest is 0.6 s at the start and 2.2 s at the end (35 % of the loop, the sparks' relaxing). */
export const T = {
  grab: [0.6, 0.72], // the finger lands: the cursor grows, the number leans 0.04x the other way
  up: [0.72, 2.0], // 0.8 → 2.4, the gas; gasp at 1.9 s when the cursor passes 2.0x
  hotHold: [2.0, 2.6],
  down: [2.6, 3.8], // 2.4 → −0.6 through liquid into crystal
  coldHold: [3.8, 4.6],
  tap: 4.05, // a tap on the crystal sends one dislocation ring through the lattice
  release: [4.6, 5.8], // −0.6 → 0.8, spring 14 / 60 / 1
  relax: [5.8, 8.0],
  let: 4.9, // the finger leaves the cursor
} as const;
export const SCRIPT_HI = 2.4;
export const SCRIPT_LO = -0.6;

export function scriptV(t0: number): number {
  'worklet';
  const t = ((t0 % LOOP) + LOOP) % LOOP;
  if (t < 0.6) return V.rest;
  if (t < 0.72) return V.rest - 0.04 * Math.sin((Math.PI * (t - 0.6)) / 0.12);
  if (t < 2.0) return mix(V.rest, SCRIPT_HI, inOut((t - 0.72) / 1.28));
  if (t < 2.6) return SCRIPT_HI;
  if (t < 3.8) return mix(SCRIPT_HI, SCRIPT_LO, inOut((t - 2.6) / 1.2));
  if (t < 4.6) return SCRIPT_LO;
  return mix(SCRIPT_LO, V.rest, springAt(t - 4.6, COOL.response, COOL.zeta));
}
/** 0 … 1: the finger is on the ruler (cursor grown, label scaled). */
export function scriptGrab(t0: number): number {
  'worklet';
  const t = ((t0 % LOOP) + LOOP) % LOOP;
  if (t < 0.6) return 0;
  if (t < 0.72) return smoothstep(0.6, 0.72, t);
  if (t < 4.6) return 1;
  return 1 - smoothstep(4.6, 4.9, t);
}

// ---- pace → everything -------------------------------------------------------------------------------------------------------
export function tempOf(v: number): number {
  'worklet';
  return clamp((v - 0.5) / 2.5, -0.6, 1);
}
/** 0 at the pin's rest, 1 at 3.0x. */
export function heatOf(v: number): number {
  'worklet';
  return clamp((v - V.rest) / (V.max - V.rest), 0, 1);
}
/** 0 above 0.35x, 1 below −0.55x: the lattice. */
export function coldOf(v: number): number {
  'worklet';
  return smoothstep(0.35, -0.55, v);
}
/** The hollow fills with sparks between 0.9x and 2.3x. */
export function fillOf(v: number): number {
  'worklet';
  return 0.85 * smoothstep(0.88, 2.3, v);
}

// OKLab, so the hue ramp through lime to amber stays clean
function srgbToLin(c: number): number {
  'worklet';
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}
function linToSrgb(c: number): number {
  'worklet';
  const x = c <= 0 ? 0 : c;
  return x <= 0.0031308 ? x * 12.92 : 1.055 * Math.pow(x, 1 / 2.4) - 0.055;
}
function toOklab(r: number, g: number, b: number, out: number[]): void {
  'worklet';
  const lr = srgbToLin(r);
  const lg = srgbToLin(g);
  const lb = srgbToLin(b);
  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
  out[0] = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  out[1] = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  out[2] = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
}
function fromOklab(L: number, a: number, b: number, out: number[]): void {
  'worklet';
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.291485548 * b, 3);
  out[0] = linToSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
  out[1] = linToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
  out[2] = linToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s);
}

/** The ramp (v, sRGB 0…1): mint-white below zero, the pin's green at rest, lime, amber at 2.0x, amber-orange at 3.0x. */
export const PALETTE: readonly (readonly [number, number, number, number])[] = [
  [-1.0, 0.812, 1.0, 0.941], // #CFFFF0
  [-0.35, 0.49, 0.96, 0.84], // #7DF5D6
  [0.8, 0.18, 0.941, 0.627], // #2EF0A0  the pin
  [1.5, 0.784, 0.941, 0.227], // #C8F03A
  [2.0, 1.0, 0.69, 0.18], // #FFB02E
  [3.0, 1.0, 0.541, 0.18], // #FF8A2E
];
export type PaletteTable = { at: number[]; lab: number[] };
function hexToRgb01(hex: string): [number, number, number] {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return [0.18, 0.94, 0.63];
  const n = parseInt(m[1], 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
/** Pre-bakes a palette (pace → colour) into OKLab; `stops` default to PALETTE. Colours are '#RRGGBB'. */
export function paletteTable(stops?: readonly { at: number; color: string }[]): PaletteTable {
  const list =
    stops && stops.length >= 2
      ? [...stops].sort((a, b) => a.at - b.at)
      : PALETTE.map((p) => ({ at: p[0], color: '', rgb: [p[1], p[2], p[3]] as [number, number, number] }));
  const at: number[] = [];
  const lab: number[] = [];
  const o = [0, 0, 0];
  for (const st of list) {
    const rgb =
      'rgb' in st && (st as { rgb?: [number, number, number] }).rgb ? (st as { rgb: [number, number, number] }).rgb : hexToRgb01(st.color);
    toOklab(rgb[0], rgb[1], rgb[2], o);
    at.push(st.at);
    lab.push(o[0], o[1], o[2]);
  }
  return { at, lab };
}
export const DEFAULT_PALETTE: PaletteTable = paletteTable();
/** sRGB 0…1 of the spark colour at pace v, written into `out`. */
export function colourAt(v: number, out: number[], tab: PaletteTable): void {
  'worklet';
  const n = tab.at.length;
  let i = 0;
  while (i < n - 2 && v > tab.at[i + 1]) i++;
  const t = clamp((v - tab.at[i]) / (tab.at[i + 1] - tab.at[i]), 0, 1);
  const a = i * 3;
  const b = a + 3;
  const L = tab.lab;
  fromOklab(mix(L[a], L[b], t), mix(L[a + 1], L[b + 1], t), mix(L[a + 2], L[b + 2], t), out);
  out[0] = clamp(out[0], 0, 1);
  out[1] = clamp(out[1], 0, 1);
  out[2] = clamp(out[2], 0, 1);
}

// ---- the score --------------------------------------------------------------------------------------------------------------
export const SCORE = { chrono: 32.2, perUnit: 11.5, pivot: 1.0, decimals: 1 } as const;
export function scoreOf(
  v: number,
  cfg: { base: number; perUnit: number; rest?: number } = { base: SCORE.chrono, perUnit: SCORE.perUnit },
): number {
  'worklet';
  return cfg.base + cfg.perUnit * (v - SCORE.pivot);
}
export const BAND_FROM = [-1000, 0, 1, 2];
/** The index of the band `v` is in: the last band whose `from` is at or below v. */
export function bandOf(v: number, from: number[]): number {
  'worklet';
  let b = 0;
  for (let i = 1; i < from.length; i++) if (v >= from[i]) b = i;
  return b;
}

// ---- membrane -----------------------------------------------------------------------------------------------------------------
/** The pin's outline at rest: harmonics 2 … 5 as [ε, ψ] with r = 1 + Σ ε cos(kφ + ψ), φ from +x, y down (fitted on the pin). */
export const REST_MEM = [0.0257, -2.0402, 0.01437, 0.43109, 0.0061, 0.53582, 0, 0] as const;
const LIVE_W = [1, 0.7, 0.5, 0.35];
const LIVE_PSI = [0.6, 2.1, -1.2, 0.4];
const LIVE_N = [1, -2, 3, -1]; // whole cycles per loop: the wobble repeats
/** Writes the eight harmonic numbers (ε2 ψ2 ε3 ψ3 ε4 ψ4 ε5 ψ5, R-normalised) into `out`. */
export function membrane(cyc: number, heat: number, cold: number, ringAmp: number, ringPhase: number, out: number[]): void {
  'worklet';
  const R0 = 163.6;
  const live = (0.5 + 4.5 * heat) * (1 - cold);
  for (let k = 0; k < 4; k++) {
    const e0 = REST_MEM[2 * k] * (1 - 0.6 * cold);
    const p0 = REST_MEM[2 * k + 1];
    let a = (live * LIVE_W[k]) / R0;
    let p = LIVE_PSI[k] + 2 * Math.PI * LIVE_N[k] * cyc;
    if (k === 0) {
      // the pulse at 1.0x: harmonic 2 rings
      a += ringAmp;
      p += ringPhase;
    }
    const re = e0 * Math.cos(p0) + a * Math.cos(p);
    const im = e0 * Math.sin(p0) + a * Math.sin(p);
    out[2 * k] = Math.sqrt(re * re + im * im);
    out[2 * k + 1] = Math.atan2(im, re);
  }
}
/** The heartbeat: `beats` whole beats per loop (8 at 60 bpm), 0 at t = 0 so a paused first frame is the pin. */
export function beat(cyc: number, beats = 8): number {
  'worklet';
  const f = (cyc * beats) % 1;
  return Math.pow(Math.sin(Math.PI * f), 6);
}

// ---- pulse at 1.0x --------------------------------------------------------------------------------------------------------------
export const PULSE = {
  duration: 0.42,
  bandWidth: 30 / 163.6,
  bandAmp: 0.75,
  ringAmp: 4 / 163.6,
  ringTau: 0.3,
  ringPeriod: 0.32,
  ripple: { speed: 600, reach: 120, amount: 4 },
} as const;
export function pulseBand(age: number, out: number[]): void {
  'worklet';
  // [band q, band amplitude]
  const u = age / PULSE.duration;
  out[0] = 1.05 - 1.05 * clamp(u, 0, 1);
  out[1] = u < 0 || u > 1 ? 0 : PULSE.bandAmp * Math.sin(Math.PI * u);
}
export function pulseRing(age: number): number {
  'worklet';
  return age < 0 ? 0 : PULSE.ringAmp * Math.exp(-age / PULSE.ringTau);
}

// ---- integrators: swirl (rad) and agitation (cycles) -----------------------------------------------------------------------------------
export const STIR = { gain: 0.9, tau: 0.9, base: 0.9 } as const;
/** Pure step of the stir / swirl / agitation state: the live gesture and the script's table both call this. s = [stir, swirl, agit]. */
export function stepStir(s: number[], v: number, vdot: number, dt: number, extra = 0): void {
  'worklet';
  const cold = coldOf(v);
  const heat = heatOf(v);
  const target = clamp(STIR.gain * vdot + extra, -GESTURE.stirMax, GESTURE.stirMax);
  s[0] += (target - s[0]) * (1 - Math.exp(-dt / STIR.tau));
  s[0] = clamp(s[0], -GESTURE.stirMax, GESTURE.stirMax);
  const omega = s[0] + STIR.base * smoothstep(V.rest, 2.4, v) - STIR.base * smoothstep(0, -0.6, v);
  const tauHome = mix(1.4, 0.5, cold);
  s[1] += (omega - s[1] / tauHome) * dt;
  s[1] = clamp(s[1], -4.5, 4.5);
  // agitation: epicycles run faster when hot; at rest the phase eases to the nearest whole cycle (so it matches the loop's start)
  s[2] += 0.9 * heat * dt + (1 - heat) * (Math.round(s[2]) - s[2]) * (1 - Math.exp(-dt * 1.6));
}

/** The script's precomputed channels at 240 Hz over one loop: swirl, agitation, and the crossings of 1.0x (+ 0.0x). */
const TABLE_HZ = 240;
function buildScript() {
  const n = LOOP * TABLE_HZ;
  const swirl: number[] = new Array(n + 1);
  const agit: number[] = new Array(n + 1);
  const s = [0, 0, 0];
  const dt = 1 / TABLE_HZ;
  let prev = scriptV(0);
  swirl[0] = 0;
  agit[0] = 0;
  const cross: number[] = [];
  for (let i = 1; i <= n; i++) {
    const t = i * dt;
    const v = scriptV(t);
    stepStir(s, v, (v - prev) / dt, dt);
    if ((prev - 1) * (v - 1) < 0) cross.push(t - dt * 0.5);
    prev = v;
    // the loop closes: the swirl is back at its home track by 7.4 s, the agitation ends on a whole cycle
    swirl[i] = s[1] * (1 - smoothstep(5.8, 7.4, t));
    agit[i] = s[2];
  }
  const total = agit[n];
  const whole = Math.max(Math.round(total), 1);
  const k = whole / Math.max(total, 1e-6);
  for (let i = 0; i <= n; i++) agit[i] *= k;
  return { swirl, agit, cross };
}
const SCRIPT = buildScript();
export const SCRIPT_CROSS = SCRIPT.cross;
function lut(tab: number[], t0: number): number {
  'worklet';
  const t = ((t0 % LOOP) + LOOP) % LOOP;
  const x = t * TABLE_HZ;
  const i = Math.min(Math.floor(x), tab.length - 2);
  const f = x - i;
  return tab[i] + (tab[i + 1] - tab[i]) * f;
}
export function scriptSwirl(t: number): number {
  'worklet';
  return lut(SCRIPT.swirl, t);
}
export function scriptAgit(t: number): number {
  'worklet';
  return lut(SCRIPT.agit, t);
}
/** Seconds since the script last crossed 1.0x (large when none within the last loop). */
export function scriptPulseAge(t0: number): number {
  'worklet';
  const t = ((t0 % LOOP) + LOOP) % LOOP;
  let age = 99;
  for (let i = 0; i < SCRIPT_CROSS.length; i++) {
    const c = SCRIPT_CROSS[i];
    const a = t >= c ? t - c : t + LOOP - c;
    if (a < age) age = a;
  }
  return age;
}

// ---- the frame: what the shaders get ----------------------------------------------------------------------------------------------------------
export type SssFrame = {
  /** lagged paces: sparks at the rim, in the hollow, the rim line's colour */
  vRim: number;
  vHol: number;
  vFilm: number;
  /** loop position in cycles (free running, 0 … 1) */
  cyc: number;
  swirl: number;
  agit: number;
  /** the finger on the disc: design units from the centre, heat 0 … 1, the dimple (R-normalised depth) and its angle */
  probeX: number;
  probeY: number;
  probeHeat: number;
  dimple: number;
  dimpleAngle: number;
  /** seconds since the 1.0x crossing, and since a tap on the crystal (large = none) */
  pulseAge: number;
  tapAge: number;
  reduceMotion: boolean;
  /** membrane pulses per loop (whole, so the loop closes): 8 at 60 bpm */
  beats: number;
  /** device */
  cx: number;
  cy: number;
  scale: number;
  px: number;
  p3: number;
};

export const FRAME_REST: SssFrame = {
  vRim: V.rest,
  vHol: V.rest,
  vFilm: V.rest,
  cyc: 0,
  swirl: 0,
  agit: 0,
  probeX: 0,
  probeY: 0,
  probeHeat: 0,
  dimple: 0,
  dimpleAngle: 0,
  pulseAge: 99,
  tapAge: 99,
  reduceMotion: false,
  beats: 8,
  cx: 0,
  cy: 0,
  scale: 1,
  px: 3,
  p3: 0,
};

/** Reduced motion: density stays in three states (crystal, shell, spread) cross-faded at the band boundaries. */
export function reducedState(v: number, out: number[]): void {
  'worklet';
  const cold = 1 - smoothstep(-0.08, 0.12, v);
  const fill = 0.45 * smoothstep(0.88, 1.12, v) + 0.4 * smoothstep(1.88, 2.12, v);
  out[0] = cold;
  out[1] = fill;
}
/** The uniforms of DISC_SKSL for a frame. Allocation: the returned arrays only. */
export function discUniforms(f: SssFrame, pal: PaletteTable): Record<string, number | number[]> {
  'worklet';
  // scratch is local: a worklet's captured arrays are copies, so shared module scratch is not safe
  const _mem = [0, 0, 0, 0, 0, 0, 0, 0];
  const _c0 = [0, 0, 0];
  const _c1 = [0, 0, 0];
  const _c2 = [0, 0, 0];
  const _band = [0, 0];
  const _rs = [0, 0];
  const reduce = f.reduceMotion;
  let heat = heatOf(f.vRim);
  let cold = coldOf(f.vRim);
  let fill = fillOf(f.vHol);
  if (reduce) {
    reducedState(f.vHol, _rs);
    cold = _rs[0];
    fill = _rs[1];
    heat = 0;
  }
  const cyc = reduce ? 0 : f.cyc;
  colourAt(f.vRim, _c0, pal);
  colourAt(f.vHol, _c1, pal);
  colourAt(f.vFilm, _c2, pal);
  const pulseAge = reduce ? 99 : f.pulseAge;
  pulseBand(pulseAge, _band);
  const ringA = reduce ? 0 : pulseRing(pulseAge);
  const ringPh = (2 * Math.PI * pulseAge) / PULSE.ringPeriod;
  membrane(cyc, reduce ? 0 : heat, cold, ringA, ringPh, _mem);
  const breath = 1 + (reduce ? 0 : 1.2 / 163.6) * beat(cyc, f.beats);
  const tapQ = (f.tapAge * 300) / 163.6;
  const tapOn = f.tapAge < 1.2 && cold > 0.5 ? 1 : 0;
  const hot = heat;
  // the colour of everything the hot end adds: the haze follows the hollow's colour, dimmer
  // the pin's haze is a deep blue-green with no red at all: the colour scaled, minus its red floor
  return {
    uGeo: [f.cx, f.cy, f.scale, f.px],
    uMemA: [_mem[0], _mem[1], _mem[2], _mem[3]],
    uMemB: [_mem[4], _mem[5], _mem[6], _mem[7]],
    uDim: [f.dimple, f.dimpleAngle, 0.55, breath],
    uS1: [heat, cold, fill, reduce ? 0 : f.swirl],
    uS2: [
      reduce ? 0 : f.agit,
      reduce ? 0 : 0.016 * (1 - cold),
      reduce ? 0 : 0.04 * smoothstep(0.1, 0.8, hot) * (1 - cold),
      reduce ? 0 : 3.0 + 1.6 * hot,
    ],
    uLook: [
      1 + 0.3 * hot,
      (reduce ? 0 : 0.45) * (1 - hot) * (1 - 0.5 * cold),
      reduce ? 0 : -2.62 + 2 * Math.PI * cyc, // the soft brightening starts at 10 o'clock and walks once round the rim per loop
      reduce ? 0 : 0.55 * hot,
    ],
    uClock: [cyc, reduce ? 0 : 2 * Math.PI * 7 * cyc, 0.9 * cold, Math.max(0.65 * hot, 0.88 * cold)],
    uCoreO: [_c0[0], _c0[1], _c0[2]],
    uCoreI: [_c1[0], _c1[1], _c1[2]],
    uHazeC: [Math.max(_c1[0] * 0.375 - 0.063, 0), _c1[1] * 0.375, _c1[2] * 0.4],
    uRimC: [_c2[0] * 0.78, _c2[1] * 0.78, _c2[2] * 0.78, 1],
    uShape: [1.3 - 0.3 * cold, 0.6 * (1 - cold), 14, 0.07],
    uMisc: [0.8, 0.3 + 0.2 * cold, 0.86, f.p3],
    uBase: [0.008, 0.016, 0.02],
    uProbe: [f.probeX, f.probeY, GESTURE.probeRadius, f.probeHeat],
    uBand: [_band[0], _band[1], PULSE.bandWidth, tapOn ? tapQ : 2],
    uRing: [tapOn ? 0.9 * Math.exp(-f.tapAge / 0.9) : 0, 0.06, 0.3, 0.56],
  };
}
