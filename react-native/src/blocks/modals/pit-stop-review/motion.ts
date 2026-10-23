/**
 * Pit Stop Review — the whole choreography as pure functions of one scene time `s` (seconds).
 *
 * The autoplay loop samples s = loop time. A tap on "Write a review" plays the same timeline from TAP
 * to HOLD, Close plays CLOSE → REST, so touch and autoplay draw exactly the same frames. Every value
 * the Swift / Compose ports need is a closed-form function here (no accumulated state, seeded noise).
 *
 * Story: the car is tracked at speed (the pin's frame). On "Write a review" the camera whip-pans to the
 * pit box, the car blasts in from the left with motion blur, heat haze and speed lines, brakes hard
 * (nose dips, discs glow, the front tyres lock and smoke), and stops on its mark. Five start lights come
 * on one by one under it and fly down into the composer, where they become its five rating stars. On
 * Close the stars fly back up as five red lights, the lights go out, and the car launches back to speed.
 */

export const LOOP = 10;

/** Scene times (s). Frames before PRESS and from REST on are the rest frame (the pin). */
export const T = {
  press: 0.8,
  tap: 0.9,
  whipEnd: 1.3,
  carGone: 1.22,
  enter: 1.38,
  stop: 1.92,
  lightsOn: 2.05,
  lightStep: 0.1,
  panelOpen: 2.55,
  panelDur: 0.28,
  flyStart: 2.62,
  flyStagger: 0.045,
  flyDur: 0.46,
  hold: 3.3,
  close: 6.0,
  backStagger: 0.04,
  backDur: 0.34,
  panelClose: 6.08,
  lightsOut: 6.72,
  rest: 7.8,
} as const;

/** Distances in the hero's design points (402 × 220), velocities in pt/s. */
export const M = {
  whipCarTravel: 480,
  entryDistance: 440,
  stopLurch: 3.2,
  launchLead: 34,
  pitchBrake: 3,
  pitchSquat: -1.5,
  blurRest: 220,
  blurWhip: 300,
  whipDistance: 3000,
  /** World speed at rest; chosen so the launch returns the world to its rest offset family. */
  worldSpeed: 2545,
  carBlurPerVelocity: 0.0055,
  carBlurMax: 22,
  /** At racing speed the car itself sits in the pin's horizontal motion blur (pt) under light streaks (0…1). */
  carRestBlur: 2,
  carStreaks: 0.75,
  discEmber: 0.28,
  /** Manual opening runs the open timeline this much faster than the loop; a takeover finishes it at `rush`. */
  manualRate: 1.5,
  rush: 4,
  closeRush: 4,
  /** Panel rise (pt) and scale at the start of its opening. */
  panelRise: 18,
  panelScale: 0.96,
  backdrop: 0.4,
  ctaPressed: 0.97,
} as const;

export function clamp(v: number, lo = 0, hi = 1) { 'worklet'; return Math.min(hi, Math.max(lo, v)); }
export function smooth(v: number) { 'worklet'; const t = clamp(v); return t * t * (3 - 2 * t); }
export function span(s: number, a: number, b: number) { 'worklet'; return clamp((s - a) / Math.max(1e-4, b - a)); }
export function easeOutCubic(v: number) { 'worklet'; const t = 1 - clamp(v); return 1 - t * t * t; }
export function easeInOutCubic(v: number) { 'worklet'; const t = clamp(v); return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }
export function cubicBezier(value: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp(value);
  const coordinate = (t: number, a: number, b: number) => { const i = 1 - t; return 3 * i * i * t * a + 3 * i * t * t * b + t * t * t; };
  let low = 0, high = 1, t = x;
  for (let n = 0; n < 18; n++) { const c = coordinate(t, x1, x2); if (Math.abs(c - x) < 1e-5) break; if (c < x) low = t; else high = t; t = (low + high) * 0.5; }
  return coordinate(t, y1, y2);
}
/** Damped oscillation that starts at 1 (cos form). f in Hz, zeta the damping ratio. */
export function ring(tau: number, f: number, zeta: number) {
  'worklet';
  if (tau <= 0) return 1;
  const w = 2 * Math.PI * f, wd = w * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * w * tau) * Math.cos(wd * tau);
}
/** Damped oscillation that starts at 0 (sin form), peak about 1. */
export function kick(tau: number, f: number, zeta: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = 2 * Math.PI * f, wd = w * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * w * tau) * Math.sin(wd * tau);
}
export function wrap(s: number) { 'worklet'; return ((s % LOOP) + LOOP) % LOOP; }
/** Frames from REST on are the rest frame; treat them as 0 so every function has one rest branch. */
function scene(s: number) { 'worklet'; return s >= T.rest ? 0 : s; }

/* ───────────── Car ───────────── */

/** Horizontal offset of the car from its rest place (design pt, + = towards the nose). */
export function carX(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.tap) return 0;
  if (s < T.carGone) { const u = span(s, T.tap, T.carGone); return M.whipCarTravel * u * u * u; }
  if (s < T.enter) return M.whipCarTravel;
  if (s < T.stop) { const u = span(s, T.enter, T.stop); return -M.entryDistance * (1 - u) * (1 - u); }
  if (s < T.lightsOut) return M.stopLurch * kick(s - T.stop, 1.8, 0.45) * (1 - span(s, T.stop + 0.6, T.stop + 0.9));
  const tau = s - T.lightsOut;
  return tau < 0.4 ? M.launchLead * easeOutCubic(tau / 0.4) : M.launchLead * (1 - smooth((tau - 0.4) / 0.66));
}
/** Speed of the car across the frame (pt/s), for its motion blur. */
export function carVelocity(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s >= T.tap && s < T.carGone) { const d = T.carGone - T.tap, u = span(s, T.tap, T.carGone); return (3 * M.whipCarTravel * u * u) / d; }
  if (s >= T.enter && s < T.stop) { const d = T.stop - T.enter, u = span(s, T.enter, T.stop); return (2 * M.entryDistance * (1 - u)) / d; }
  if (s >= T.lightsOut && s < T.lightsOut + 0.4) { const u = (s - T.lightsOut) / 0.4; return (3 * M.launchLead * (1 - u) * (1 - u)) / 0.4; }
  return 0;
}

/** Body pitch in degrees, + = nose down. The renders cover −1.5 … +3. */
export function pitch(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s >= T.enter && s < T.stop) return M.pitchBrake * smooth(span(s, T.enter + 0.04, T.enter + 0.2));
  if (s >= T.stop && s < T.hold) return clamp(M.pitchBrake * ring(s - T.stop, 2.0, 0.32), M.pitchSquat, M.pitchBrake) * (1 - span(s, T.hold - 0.35, T.hold));
  if (s >= T.lightsOut) {
    const tau = s - T.lightsOut;
    const squat = M.pitchSquat * smooth(tau / 0.12);
    const release = tau < 0.33 ? 1 : ring(tau - 0.33, 1.6, 0.45);
    return squat * release * (1 - span(s, T.rest - 0.2, T.rest));
  }
  return 0;
}
/** Brake disc heat 0…1 (drives the Blender glow layer). */
export function disc(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.enter + 0.12) return 0;
  if (s < T.stop) return smooth(span(s, T.enter + 0.12, T.stop));
  if (s < T.lightsOut) return M.discEmber + (1 - M.discEmber) * Math.exp(-3 * (s - T.stop)) * (1 - span(s, T.stop + 1.1, T.hold));
  return M.discEmber * (1 - smooth(span(s, T.lightsOut, T.rest - 0.2)));
}

/* ───────────── World (track / pit lane shader) ───────────── */

/** 1 = racing speed (the pin's streaks), 0 = parked in the pit box. */
export function speed(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.tap + 0.05) return 1;
  if (s < T.lightsOut + 0.13) return 1 - smooth(span(s, T.tap + 0.05, T.whipEnd - 0.05));
  return smooth(span(s, T.lightsOut + 0.13, T.rest - 0.15));
}
/** (Declared after speed(): worklet closures capture functions in source order.) Horizontal blur of the car (pt): its own speed across the frame, plus the pin's speed blur while racing. Sharp only parked. */
export function carBlur(s: number) { 'worklet'; return Math.min(M.carBlurMax, Math.abs(carVelocity(s)) * M.carBlurPerVelocity + M.carRestBlur * speed(s)); }
/** Light streaks over the car body, 0…1 (the pin's look at speed; none when parked). */
export function carStreaks(s: number) { 'worklet'; return M.carStreaks * speed(s); }
/** Whip pan: 0 → 1 → 0 between TAP and WHIP_END. */
export function whip(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s <= T.tap || s >= T.whipEnd) return 0;
  const v = Math.sin(Math.PI * span(s, T.tap, T.whipEnd));
  return v * v;
}
/** Motion-blur length of the world (pt). */
export function worldBlur(s: number) { 'worklet'; return M.blurRest * speed(s) + M.blurWhip * whip(s); }
/** World offset (pt): the world sample x is screen x + this. The pit box sits at −whipDistance. */
export function worldX(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.tap) return 0;
  if (s < T.lightsOut + 0.13) return -M.whipDistance * smooth(span(s, T.tap, T.whipEnd));
  const a = T.lightsOut + 0.13, b = T.rest - 0.15, d = b - a;
  const u = span(s, a, b);
  // ∫ smoothstep = d (u³ − u⁴/2): the world accelerates with the launch, then runs at full speed.
  return -M.whipDistance + M.worldSpeed * (d * (u * u * u - (u * u * u * u) / 2) + Math.max(0, s - b));
}
/** Heat haze behind the rear wing, 0…1. */
export function haze(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s >= T.enter && s < T.lightsOut) return smooth(span(s, T.enter, T.enter + 0.1)) * (1 - 0.45 * span(s, T.stop - 0.1, T.stop)) * (1 - smooth(span(s, T.stop + 0.3, T.hold - 0.1)));
  if (s >= T.lightsOut) return smooth(span(s, T.lightsOut, T.lightsOut + 0.15)) * (1 - smooth(span(s, T.lightsOut + 0.5, T.rest - 0.1)));
  return 0;
}
/** Exhaust / rain-light trail behind the car, 0…1 (1 = the pin). */
export function trail(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.enter) return 1;
  if (s < T.lightsOut) { const u = span(s, T.enter, T.stop); return (0.45 + 0.55 * (1 - u)) * (1 - smooth(span(s, T.stop - 0.08, T.stop + 0.3))); }
  return smooth(span(s, T.lightsOut + 0.1, T.rest - 0.2));
}
/** Speed lines across the frame, 0…1. */
export function speedLines(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.tap) return 0;
  if (s < T.whipEnd) return whip(s);
  if (s < T.stop) return smooth(span(s, T.enter - 0.02, T.enter + 0.08)) * (1 - smooth(span(s, T.enter + 0.12, T.stop - 0.12)));
  if (s >= T.lightsOut) { const v = Math.sin(Math.PI * span(s, T.lightsOut + 0.15, T.rest - 0.25)); return 0.6 * v * v; }
  return 0;
}

/* ───────────── Start lights → stars ───────────── */

/** Gantry housing under the car, 0…1. */
export function gantry(raw: number) {
  'worklet';
  const s = scene(raw);
  return smooth(span(s, T.stop + 0.03, T.stop + 0.16)) * (1 - smooth(span(s, T.lightsOut + 0.06, T.lightsOut + 0.24)));
}
/** Light i switched on (0…1), before it flies or after it returns. */
export function lightOn(raw: number, i: number) {
  'worklet';
  const s = scene(raw);
  const on = T.lightsOn + i * T.lightStep;
  return span(s, on, on + 0.05) * (1 - span(s, T.lightsOut, T.lightsOut + 0.03));
}
/** Flight of light i from the gantry to its star slot, 0…1 (eased). */
export function flyIn(raw: number, i: number) {
  'worklet';
  const s = scene(raw);
  const a = T.flyStart + i * T.flyStagger;
  return easeInOutCubic(span(s, a, a + T.flyDur));
}
/** Flight of star i back up to the gantry, 0…1 (eased). Stars leave right to left. */
export function flyBack(raw: number, i: number) {
  'worklet';
  const s = scene(raw);
  const a = T.close + (4 - i) * T.backStagger;
  return easeInOutCubic(span(s, a, a + T.backDur));
}
/** Where light i is: 0 in the gantry, 1 in the composer; between them it is flying. */
export function lightAway(raw: number, i: number) { 'worklet'; return flyIn(raw, i) * (1 - flyBack(raw, i)); }

/* ───────────── Composer ───────────── */

export function panel(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.panelOpen) return 0;
  if (s < T.panelClose) return cubicBezier((s - T.panelOpen) / T.panelDur, 0.32, 0.72, 0, 1);
  return 1 - cubicBezier((s - T.panelClose) / T.panelDur, 0.23, 1, 0.32, 1);
}
export function ctaScale(raw: number) {
  'worklet';
  const s = scene(raw);
  if (s < T.press || s > T.tap + 0.15) return 1;
  if (s < T.tap) return 1 - (1 - M.ctaPressed) * cubicBezier((s - T.press) / (T.tap - T.press), 0.23, 1, 0.32, 1);
  return M.ctaPressed + (1 - M.ctaPressed) * cubicBezier((s - T.tap) / 0.15, 0.23, 1, 0.32, 1);
}
/** Star i shown in the composer itself (landed and not yet lifted off). */
export function starLanded(raw: number, i: number) { 'worklet'; return flyIn(raw, i) >= 1 && flyBack(raw, i) <= 0 ? 1 : 0; }
/** Landing squash of star i: 1.06 → 1 over 180 ms after it lands. */
export function starScale(raw: number, i: number) {
  'worklet';
  const s = scene(raw);
  const land = T.flyStart + i * T.flyStagger + T.flyDur;
  if (s < land || s > land + 0.18) return 1;
  return 1 + 0.06 * (1 - cubicBezier((s - land) / 0.18, 0.23, 1, 0.32, 1));
}

/* ───────────── Smoke (seeded particles) ───────────── */

export function hash(n: number) { 'worklet'; const x = Math.sin(n * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); }

export type Puff = { x: number; y: number; r: number; a: number };
export const SMOKE = {
  front: { count: 16, from: 1.66, to: 1.92, life: 1.3, alpha: 0.42, drag: 5, grow: 40, spread: 44 },
  rear: { count: 16, from: 6.72, to: 7.22, life: 1.0, alpha: 0.42, drag: 3, grow: 46, spread: 40 },
} as const;
/**
 * Puff k of a tyre (`tyre` = its contact point in design pt at rest, `seed` distinct per tyre).
 * Front lock-up smoke keeps the car's speed and drags to a stop; rear wheelspin smoke is thrown back
 * and is carried away by the world as the camera starts tracking again.
 */
export function puff(raw: number, kind: 'front' | 'rear', tyre: [number, number], seed: number, k: number, side = 1): Puff | null {
  'worklet';
  const s = scene(raw);
  const P = SMOKE[kind];
  if (k >= P.count) return null;
  const born = P.from + (P.to - P.from) * ((k + hash(seed + k * 7.3)) / P.count);
  const age = s - born;
  if (age <= 0 || age >= P.life) return null;
  const jitterX = (hash(seed + k * 3.1) - 0.5) * 8, jitterY = (hash(seed + k * 5.7) - 0.5) * 10;
  const x0 = tyre[0] + carX(born) + jitterX, y0 = tyre[1] + jitterY;
  // Smoke rolls out sideways, away from the car (side = +1 below the car, −1 above it).
  const spread = side * (0.25 + 0.75 * hash(seed + k * 9.9)) * P.spread;
  const ease = 1 - Math.exp(-P.drag * age);
  let x: number;
  if (kind === 'front') x = x0 + (carVelocity(born) * ease) / P.drag;
  else x = x0 - 70 * (1 - Math.exp(-3 * age)) - (worldX(s) - worldX(born));
  const y = y0 + spread * (1 - Math.exp(-2.4 * age));
  const r = 6 + P.grow * (1 - Math.exp(-1.8 * age)) * (0.7 + 0.6 * hash(seed + k * 1.7));
  const life = age / P.life;
  const a = P.alpha * smooth(age / 0.06) * (1 - life) * (1 - life) * (kind === 'rear' ? 1 - span(s, T.rest - 0.35, T.rest) : 1);
  return { x, y, r, a };
}

/**
 * Every live puff of the four tyres packed for the smoke shader: `data` holds (x, y, r, a) × 32 (zeros after `count`),
 * `box` the union of their discs (x0, y0, x1, y1) so the shader is only run where there is smoke.
 */
export const PUFF_SLOTS = 32;
export function packPuffs(raw: number, tyres: readonly (readonly [number, number, number, number, number])[]) {
  'worklet';
  const data: number[] = [];
  let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
  for (let t = 0; t < tyres.length; t++) {
    const ty = tyres[t];
    const kind = ty[3] === 0 ? 'front' : 'rear';
    for (let k = 0; k < 16 && data.length < PUFF_SLOTS * 4; k++) {
      const q = puff(raw, kind, [ty[0], ty[1]], ty[2], k, ty[4]);
      if (!q || q.a <= 0.003) continue;
      data.push(q.x, q.y, q.r, q.a);
      x0 = Math.min(x0, q.x - q.r); y0 = Math.min(y0, q.y - q.r); x1 = Math.max(x1, q.x + q.r); y1 = Math.max(y1, q.y + q.r);
    }
  }
  const count = data.length / 4;
  while (data.length < PUFF_SLOTS * 4) data.push(0);
  return { data, count, box: count > 0 ? [x0, y0, x1, y1] : [0, 0, 0, 0] };
}

/* ───────────── Reduced motion & loop helpers ───────────── */

/** Scene frames the clock must run through (anything else is a still). */
export function moving(raw: number) { 'worklet'; const s = wrap(raw); return (s >= T.press && s < T.hold) || (s >= T.close && s < T.rest); }

/** 20-vertex circle → five-point star, sampled at `progress` (0 circle, 1 star). */
export function morphPoint(index: number, progress: number, center: [number, number], radius: number): [number, number] {
  'worklet';
  const i = ((index % 20) + 20) % 20, angle = -Math.PI / 2 + (i * 2 * Math.PI) / 20, p = clamp(progress), segment = Math.floor(i / 2), fraction = i % 2 === 0 ? 0 : 0.5;
  const star = (v: number): [number, number] => { const n = ((v % 10) + 10) % 10, a = -Math.PI / 2 + (n * Math.PI) / 5, r = n % 2 === 0 ? radius : radius * 0.46; return [Math.cos(a) * r, Math.sin(a) * r]; };
  const [ax, ay] = star(segment), [bx, by] = star(segment + 1);
  return [center[0] + Math.cos(angle) * radius + (ax + (bx - ax) * fraction - Math.cos(angle) * radius) * p, center[1] + Math.sin(angle) * radius + (ay + (by - ay) * fraction - Math.sin(angle) * radius) * p];
}
