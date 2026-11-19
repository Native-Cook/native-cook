// One loop drives the whole film. Every film value below is a pure function of the loop time t (seconds,
// 0 <= t < LOOP), so t = LOOP gives the same frame as t = 0 and the Swift / Kotlin ports can match it frame for
// frame. The stage (Filament thread), the Skia layers and the copy (UI thread) all read the same t.
//
// Under a finger the film is replaced by the onboarding's pages: a float page p (0 = intro, 1–3 = the steps,
// 4 = Get started) that springs between whole pages. Every channel has a film form f(t) and a page form g(p); the
// screen shows lerp(f(t), g(p), m), where m eases 0 -> 1 when a touch takes over. Page 0 is exactly the poster,
// so the return (p -> 0, then m = 0 and t = 0) is seamless.
//
//  0.0 –  1.5  poster: the helmet three-quarter on its plinth, "Meet your visor."
//  1.5 –  5.0  step 1 "See more": the camera swings to the front; the HUD boots on the visor (scanline, frame,
//              compass ribbon, two readouts type in)
//  5.0 –  9.0  step 2 "Stay aware": the camera orbits to the side; the panel lamps ramp and pulse once; a radar
//              ring sweeps round the ear sensor
//  9.0 – 13.0  step 3 "Hands free": a high angle; voice rings at the mouthpiece and a waveform with a transcript
// 13.0 – 15.2  "Get started" grows out of the pager's pill; the helmet rises off the plinth under a light sweep
// 15.2 – 17.0  back to the poster (the button folds into the first dot, the helmet settles)
// 17.0 – 18.0  rest; last frame = first frame

import { HUD_NORMAL, PAGE_SHOTS, RISE, SHOTS } from "./visorScene";
import type { Shot, Vec3 } from "./visorScene";

export const LOOP = 18;

export const PHASE = {
  restEnd: 1.5,
  step1: 1.5,
  step2: 5.0,
  step3: 9.0,
  cta: 13.0,
  ctaEnd: 15.2,
  returnEnd: 17.0,
} as const;

/** After the last touch, wait this long, then ease back to the poster in RETURN_MS and loop from 0. */
export const IDLE_MS = 2000;
export const RETURN_MS = 1400;
/** How long a touch takes to blend the film into the pages. */
export const TAKEOVER_MS = 450;

/** Copy blocks: [enter, leave] in film time (copy 0 is the intro, shown at both ends of the loop). */
export const COPY_WINDOWS: readonly (readonly [number, number])[] = [
  [15.75, 1.55],
  [1.95, 5.15],
  [5.55, 9.15],
  [9.55, 13.05],
  [13.5, 15.45],
];

// ---------------------------------------------------------------- helpers (declared before their callers)

export function clamp01(x: number): number {
  "worklet";
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** 0 before a, 1 after b, linear between. */
export function seg(t: number, a: number, b: number): number {
  "worklet";
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, k: number): number {
  "worklet";
  return a + (b - a) * k;
}

export function easeInOutCubic(x: number): number {
  "worklet";
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(x: number): number {
  "worklet";
  return 1 - Math.pow(1 - x, 3);
}

export function easeInCubic(x: number): number {
  "worklet";
  return x * x * x;
}

export function easeInOutSine(x: number): number {
  "worklet";
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

/** Ease out with a small overshoot (about 3 %), settling at 1 with zero velocity. */
export function easeOutBack3(x: number): number {
  "worklet";
  const c1 = 0.72;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

/** A smooth bump: 0 at a and b, 1 in the middle, zero slope at both ends. */
export function bump(t: number, a: number, b: number): number {
  "worklet";
  const k = Math.sin(Math.PI * seg(t, a, b));
  return k * k;
}

/** Weight of whole page i at float page p (1 on it, 0 one page away). */
export function pageWeight(p: number, i: number): number {
  "worklet";
  return clamp01(1 - Math.abs(p - i));
}

/** A channel blended between the film (at t) and the pages (at p) by m. */
export function blendChannel(film: number, page: number, m: number): number {
  "worklet";
  return lerp(film, page, clamp01(m));
}

// ---------------------------------------------------------------- copy, pager, button

/** Copy block i in the film: [opacity, dy]. Enters over 0.45 s rising 12 pt, leaves over 0.3 s lifting 6 pt. */
export function filmCopy(i: number, t: number, out: number[]): void {
  "worklet";
  const w = COPY_WINDOWS[i];
  const inK = easeOutCubic(seg(t, w[0], w[0] + 0.45));
  const outK = easeInCubic(seg(t, w[1], w[1] + 0.3));
  if (i === 0) {
    // the intro is on at t = 0, leaves at 1.55 and comes back at 15.75
    const on = t < 8 ? 1 - outK : inK;
    out[0] = on;
    out[1] = t < 8 ? -6 * outK : 12 * (1 - inK);
    return;
  }
  out[0] = inK * (1 - outK);
  out[1] = outK > 0 ? -6 * outK : 12 * (1 - inK);
}

/** Copy block i at float page p: cross-fades over half a page; the incoming one rises from 12 pt. */
export function pageCopy(i: number, p: number, out: number[]): void {
  "worklet";
  const d = i - p;
  out[0] = clamp01(1 - Math.abs(d) * 2);
  out[1] = Math.max(-6, Math.min(12, d * 24));
}

/** Pager dot i (0..3) stretch, film: follows its copy block; the last dot also carries the Get started page. */
export function filmDot(i: number, t: number): number {
  "worklet";
  const o = [0, 0];
  filmCopy(i, t, o);
  if (i < 3) return o[0];
  const o4 = [0, 0];
  filmCopy(4, t, o4);
  return Math.max(o[0], o4[0]);
}

export function pageDot(i: number, p: number): number {
  "worklet";
  return i < 3 ? pageWeight(p, i) : clamp01(1 - Math.abs(Math.min(p, 3) - 3));
}

/** Get started: 0 = the pager's pill, 1 = the full button. Grows 13.2 – 14.1 (a 3 % overshoot), folds 15.3 – 16.1. */
export function filmCta(t: number): number {
  "worklet";
  if (t < 15) return easeOutBack3(seg(t, 13.2, 14.1));
  return 1 - easeInOutCubic(seg(t, 15.3, 16.1));
}

export function pageCta(p: number): number {
  "worklet";
  return clamp01(p - 3);
}

/** Which dot the button grows out of / folds into: the last step's on the way out, the first on the way back. */
export function filmCtaAnchor(t: number): number {
  "worklet";
  return t < 15 ? 3 : 0;
}

// ---------------------------------------------------------------- the helmet and its light

/** Helmet lift (model units), 0 at rest. */
export function filmRise(t: number): number {
  "worklet";
  return RISE * (easeInOutCubic(seg(t, 13.0, 14.2)) - easeInOutCubic(seg(t, 15.3, 16.9)));
}

export function pageRise(p: number): number {
  "worklet";
  return RISE * clamp01(p - 3);
}

/** Panel lamp level (rest 0.15): ramps up as the side comes round, one strong pulse, settles, dims away. */
export function filmLamps(t: number): number {
  "worklet";
  const up = easeInOutCubic(seg(t, 5.9, 6.8));
  const down = easeInOutCubic(seg(t, 8.6, 9.6));
  const pulse = 2.6 * bump(t, 6.9, 7.9);
  const cta = 0.9 * bump(t, 13.4, 15.4);
  return 0.15 + (1.35 * up + pulse) * (1 - down) + cta;
}

export function pageLamps(p: number): number {
  "worklet";
  return 0.15 + 1.35 * pageWeight(p, 2) + 0.6 * clamp01(p - 3);
}

/** The light sweep of the Get started beat: [level 0..1, azimuth 0..1 across the front]. */
export function filmSweep(t: number, out: number[]): void {
  "worklet";
  out[0] = bump(t, 13.5, 15.1);
  out[1] = easeInOutSine(seg(t, 13.5, 15.1));
}

// ---------------------------------------------------------------- 2D modules (HUD, radar, voice)

/** HUD visibility, film: on at 2.45 – 2.85, off 5.0 – 5.35. */
export function filmHud(t: number): number {
  "worklet";
  return easeOutCubic(seg(t, 2.45, 2.85)) * (1 - easeInCubic(seg(t, 5.0, 5.35)));
}

/** HUD boot progress 0..1 (scanline 0–0.3, frame 0.1–0.35, compass 0.3–0.55, readouts 0.55–1). */
export function filmBoot(t: number): number {
  "worklet";
  return seg(t, 2.5, 4.4);
}

export function filmRadar(t: number): number {
  "worklet";
  return easeOutCubic(seg(t, 6.3, 6.9)) * (1 - easeInCubic(seg(t, 8.75, 9.15)));
}

export function filmVoice(t: number): number {
  "worklet";
  return easeOutCubic(seg(t, 10.1, 10.6)) * (1 - easeInCubic(seg(t, 12.7, 13.05)));
}

/** Transcript typing 0..1 (film); always 1 on the page. */
export function filmTranscript(t: number): number {
  "worklet";
  return seg(t, 10.9, 12.1);
}

/** Radar sweep angle (radians) at a running time s; the film passes its own t. */
export function radarAngle(s: number): number {
  "worklet";
  return (2 * Math.PI * s) / 2.1;
}

/** Compass heading (degrees) at a running time s. */
export function heading(s: number): number {
  "worklet";
  return 24 + 7 * Math.sin((2 * Math.PI * s) / 9) + 2.5 * Math.sin((2 * Math.PI * s) / 3.6);
}

/** Voice level of waveform bar i (0..1) at a running time s: a sum of sines plus a per-bar hash. */
export function voiceBar(i: number, n: number, s: number): number {
  "worklet";
  const x = i / (n - 1);
  const env = Math.sin(Math.PI * x);
  const h = Math.abs(Math.sin(i * 12.9898) * 43758.5453) % 1;
  const v =
    0.5 +
    0.22 * Math.sin(2 * Math.PI * (s * 1.7 + x * 1.3)) +
    0.16 * Math.sin(2 * Math.PI * (s * 3.1 - x * 2.2) + h * 6) +
    0.12 * Math.sin(2 * Math.PI * (s * 0.6 + h));
  const syll = 0.55 + 0.45 * Math.max(0, Math.sin(2 * Math.PI * s * 1.25));
  return clamp01(env * v * syll * (0.75 + 0.5 * h));
}

/** Where the voice waveform sits on screen for a mouthpiece at y = ay: under the helmet, above the copy. */
export function voiceWaveY(ay: number, h: number, limitY: number): number {
  "worklet";
  return Math.min(Math.max(ay + 64, h * 0.3), limitY - 64);
}

// ---------------------------------------------------------------- camera

/** Orbit-style blend of two shots: azimuth, radius and height of the eye; the target moves straight. */
export function blendShot(a: Shot, b: Shot, k: number, out: number[]): void {
  "worklet";
  const az0 = Math.atan2(a.eye[0], a.eye[2]);
  let az1 = Math.atan2(b.eye[0], b.eye[2]);
  if (az1 - az0 > Math.PI) az1 -= 2 * Math.PI;
  if (az0 - az1 > Math.PI) az1 += 2 * Math.PI;
  const r0 = Math.hypot(a.eye[0], a.eye[2]);
  const r1 = Math.hypot(b.eye[0], b.eye[2]);
  const az = lerp(az0, az1, k);
  const r = lerp(r0, r1, k);
  out[0] = Math.sin(az) * r;
  out[1] = lerp(a.eye[1], b.eye[1], k);
  out[2] = Math.cos(az) * r;
  out[3] = lerp(a.target[0], b.target[0], k);
  out[4] = lerp(a.target[1], b.target[1], k);
  out[5] = lerp(a.target[2], b.target[2], k);
}

/** Camera keys: a held key has zero velocity; the others pass through with velocity (monotone tangents). */
type Key = { t: number; shot: Shot; hold: boolean };

const KEYS: Key[] = [
  { t: 0, shot: SHOTS.poster, hold: true },
  { t: PHASE.restEnd, shot: SHOTS.poster, hold: true },
  { t: 3.2, shot: SHOTS.front, hold: false },
  { t: 5.0, shot: SHOTS.frontDrift, hold: false },
  { t: 6.8, shot: SHOTS.side, hold: false },
  { t: 8.9, shot: SHOTS.sideDrift, hold: false },
  { t: 10.8, shot: SHOTS.top, hold: false },
  { t: 12.9, shot: SHOTS.topDrift, hold: false },
  { t: 14.4, shot: SHOTS.rise, hold: false },
  { t: 15.2, shot: SHOTS.riseDrift, hold: false },
  { t: PHASE.returnEnd, shot: SHOTS.poster, hold: true },
  { t: LOOP, shot: SHOTS.poster, hold: true },
];

/** Cylindrical coordinates [az, r, y, tx, ty, tz] of the keys, azimuth unwrapped along the run. */
function cylRun(keys: Key[]): number[][] {
  "worklet";
  const out: number[][] = [];
  let prev = 0;
  for (let i = 0; i < keys.length; i++) {
    const e = keys[i].shot.eye;
    const g = keys[i].shot.target;
    let az = Math.atan2(e[0], e[2]);
    if (i > 0) {
      while (az - prev > Math.PI) az -= 2 * Math.PI;
      while (prev - az > Math.PI) az += 2 * Math.PI;
    }
    prev = az;
    out.push([az, Math.hypot(e[0], e[2]), e[1], g[0], g[1], g[2]]);
  }
  return out;
}
const CYL = cylRun(KEYS);

/** Monotone (Fritsch–Carlson) tangents per key and channel: velocity is continuous through every key, a channel never
 * overshoots between two keys (no back-and-forth hesitation), and it comes to rest where it turns round or holds. */
function monotoneTangents(keys: Key[], cyl: number[][]): number[][] {
  const out: number[][] = [];
  for (let i = 0; i < keys.length; i++) {
    const row: number[] = [];
    for (let c = 0; c < 6; c++) {
      if (keys[i].hold || i === 0 || i === keys.length - 1) {
        row.push(0);
        continue;
      }
      const h0 = keys[i].t - keys[i - 1].t;
      const h1 = keys[i + 1].t - keys[i].t;
      const d0 = (cyl[i][c] - cyl[i - 1][c]) / h0;
      const d1 = (cyl[i + 1][c] - cyl[i][c]) / h1;
      if (d0 * d1 <= 0) {
        row.push(0);
        continue;
      }
      const w1 = 2 * h1 + h0;
      const w2 = h1 + 2 * h0;
      row.push((w1 + w2) / (w1 / d0 + w2 / d1));
    }
    out.push(row);
  }
  return out;
}
const TAN = monotoneTangents(KEYS, CYL);

/** Camera at loop time t: cubic Hermite through the keys (monotone tangents, zero at held keys), written into
 * out = [eyeX, eyeY, eyeZ, targetX, targetY, targetZ]. */
export function filmCamera(t: number, out: number[]): void {
  "worklet";
  const n = KEYS.length;
  let i = 0;
  while (i < n - 2 && t >= KEYS[i + 1].t) i++;
  const t0 = KEYS[i].t;
  const h = KEYS[i + 1].t - t0;
  const u = clamp01((t - t0) / h);
  const h00 = 2 * u * u * u - 3 * u * u + 1;
  const h10 = u * u * u - 2 * u * u + u;
  const h01 = -2 * u * u * u + 3 * u * u;
  const h11 = u * u * u - u * u;
  const v = [0, 0, 0, 0, 0, 0];
  for (let c = 0; c < 6; c++) {
    v[c] = h00 * CYL[i][c] + h10 * h * TAN[i][c] + h01 * CYL[i + 1][c] + h11 * h * TAN[i + 1][c];
  }
  out[0] = Math.sin(v[0]) * v[1];
  out[1] = v[2];
  out[2] = Math.cos(v[0]) * v[1];
  out[3] = v[3];
  out[4] = v[4];
  out[5] = v[5];
}

/** Camera at float page p (orbit blend between neighbouring pages' shots). */
export function pageCamera(p: number, out: number[]): void {
  "worklet";
  const q = Math.max(0, Math.min(PAGE_SHOTS.length - 1, p));
  const i = Math.min(PAGE_SHOTS.length - 2, Math.floor(q));
  blendShot(PAGE_SHOTS[i], PAGE_SHOTS[i + 1], q - i, out);
}

/** Turn the eye about the target: yaw round the vertical, pitch changes the elevation (both degrees). */
export function applyOrbit(c: number[], yawDeg: number, pitchDeg: number): void {
  "worklet";
  if (yawDeg === 0 && pitchDeg === 0) return;
  const vx = c[0] - c[3];
  const vy = c[1] - c[4];
  const vz = c[2] - c[5];
  const r = Math.hypot(vx, vy, vz);
  const flat = Math.hypot(vx, vz);
  const az = Math.atan2(vx, vz) + (yawDeg * Math.PI) / 180;
  const el = Math.max(-0.08, Math.min(1.35, Math.atan2(vy, flat) + (pitchDeg * Math.PI) / 180));
  c[0] = c[3] + Math.sin(az) * Math.cos(el) * r;
  c[1] = c[4] + Math.sin(el) * r;
  c[2] = c[5] + Math.cos(az) * Math.cos(el) * r;
}

/** The camera the screen shows: film at t, pages at p, blended by m, then the finger's orbit. */
export function cameraAt(t: number, p: number, m: number, yawDeg: number, pitchDeg: number, out: number[]): void {
  "worklet";
  filmCamera(t, out);
  const k = clamp01(m);
  if (k > 0) {
    const pc = [0, 0, 0, 0, 0, 0];
    pageCamera(p, pc);
    const a: Shot = { eye: [out[0], out[1], out[2]], target: [out[3], out[4], out[5]] };
    const b: Shot = { eye: [pc[0], pc[1], pc[2]], target: [pc[3], pc[4], pc[5]] };
    blendShot(a, b, easeInOutSine(k), out);
  }
  applyOrbit(out, yawDeg, pitchDeg);
}

/** The page nearest to the film at t (where a touch picks the onboarding up). */
export function pageOfFilm(t: number): number {
  "worklet";
  if (t < 1.8 || t >= 15.6) return 0;
  if (t < 5.2) return 1;
  if (t < 9.2) return 2;
  if (t < 13.1) return 3;
  return 4;
}

// ---------------------------------------------------------------- projection (the stage camera's own maths)

/** Camera basis for the overlay: [fx, fy, fz, rx, rz, ux, uy, uz, focal]. Horizontal FOV, y up. */
export function cameraBasis(c: number[], width: number, hfovDeg: number, out: number[]): void {
  "worklet";
  let fx = c[3] - c[0];
  let fy = c[4] - c[1];
  let fz = c[5] - c[2];
  const fl = Math.hypot(fx, fy, fz) || 1;
  fx /= fl;
  fy /= fl;
  fz /= fl;
  // right = f x up(0,1,0); up' = right x f
  let rx = -fz;
  let rz = fx;
  const rl = Math.hypot(rx, rz) || 1;
  rx /= rl;
  rz /= rl;
  out[0] = fx;
  out[1] = fy;
  out[2] = fz;
  out[3] = rx;
  out[4] = rz;
  out[5] = -rz * fy;
  out[6] = rz * fx - rx * fz;
  out[7] = rx * fy;
  out[8] = width / 2 / Math.tan((hfovDeg * Math.PI) / 360);
}

/** Project a world point; out = [x, y, depth]. Returns false behind the camera. */
export function project(c: number[], b: number[], w: number, h: number, p: Vec3, out: number[]): boolean {
  "worklet";
  const px = p[0] - c[0];
  const py = p[1] - c[1];
  const pz = p[2] - c[2];
  const z = px * b[0] + py * b[1] + pz * b[2];
  out[2] = z;
  if (z < 0.05) return false;
  out[0] = w / 2 + ((px * b[3] + pz * b[4]) / z) * b[8];
  out[1] = h / 2 - ((px * b[5] + py * b[6] + pz * b[7]) / z) * b[8];
  return true;
}

/** How squarely the visor faces the camera (0 edge-on or behind .. 1 head-on); the HUD fades with it. */
export function hudFacing(c: number[], helmetY: number): number {
  "worklet";
  const vx = c[0];
  const vy = c[1] - (helmetY + 0.58);
  const vz = c[2] - 1.1;
  const l = Math.hypot(vx, vy, vz) || 1;
  const d = (vx * HUD_NORMAL[0] + vy * HUD_NORMAL[1] + vz * HUD_NORMAL[2]) / l;
  return clamp01((d - 0.45) / 0.35);
}

/** Homography from the canonical rect (0,0)-(w,h) to the quad p (TL, TR, BR, BL as x0,y0..x3,y3), as a Skia
 * 3x3 row-major matrix. */
export function rectToQuad(q: number[], w: number, h: number, out: number[]): void {
  "worklet";
  const x0 = q[0], y0 = q[1], x1 = q[2], y1 = q[3], x2 = q[4], y2 = q[5], x3 = q[6], y3 = q[7];
  const dx1 = x1 - x2;
  const dx2 = x3 - x2;
  const dx3 = x0 - x1 + x2 - x3;
  const dy1 = y1 - y2;
  const dy2 = y3 - y2;
  const dy3 = y0 - y1 + y2 - y3;
  let g = 0;
  let hh = 0;
  const det = dx1 * dy2 - dx2 * dy1;
  if (Math.abs(det) > 1e-9) {
    g = (dx3 * dy2 - dx2 * dy3) / det;
    hh = (dx1 * dy3 - dx3 * dy1) / det;
  }
  const a = x1 - x0 + g * x1;
  const b = x3 - x0 + hh * x3;
  const d = y1 - y0 + g * y1;
  const e = y3 - y0 + hh * y3;
  // unit square -> quad, then pre-scale the canonical rect into the unit square
  out[0] = a / w;
  out[1] = b / h;
  out[2] = x0;
  out[3] = d / w;
  out[4] = e / h;
  out[5] = y0;
  out[6] = g / w;
  out[7] = hh / h;
  out[8] = 1;
}
