// Card Vacuum Seal — motion. One value `p` (the film's state) drives everything the film, the shield disc and
// the label show: 0 = no film, P_DRAPE = the sheet resting on the card as a full air pillow, 1 = no air left
// (sealed, the pin's poster). The autoplay timeline and the finger move the same `p`; every frame of the loop
// is a pure function of the loop time (no accumulated state), so the Swift and Kotlin ports match frame for frame.

export const LOOP = 6.0; // s. Concept said 7.0; the dimple beat moved to touch only (a dent with no finger reads as a ghost)

/** phase table, seconds into the loop (Pro upgrade 4: the half-seal is held for a full second) */
export const T = {
  rest0: [0.0, 0.2], // sealed, the pin
  untap: [0.12, 0.4], // a tap on "Protected" (tile press 0.96 and back)
  unseal: [0.2, 1.0], // air returns (0.20–0.73, p 1 → P_DRAPE), the sheet lifts away (0.73–1.00, p → 0)
  bare: [1.0, 1.2], // bare card, red shield
  press: [1.2, 3.45], // the finger holds "Protect Card"
  drape: [1.25, 1.6], // p 0 → P_DRAPE at the hold rate
  evacA: [1.6, 2.05], // p P_DRAPE → P_HOLD
  pause: [2.05, 3.05], // finger slides off the tile: the film holds half skin, half pillow for one second (the gasp, 2.05 s)
  evacB: [3.05, 3.45], // p P_HOLD → 1
  lock: 3.45, // the film locks
  settle: [3.45, 4.0], // creases relax 3 %, one glint runs over them (3.45–3.70), the tiles are tugged 2 pt
  sweep: [4.0, 5.0], // a slow highlight crosses the creases
  rest1: [5.0, 6.0], // still, identical to frame 0
} as const;

export const AIR_END = 0.73; // the pillow is full again; the sheet lifts away from here
export const P_DRAPE = 0.3;
export const P_HOLD = P_DRAPE + 0.8 * (T.evacA[1] - T.evacA[0]); // 0.66: where the hold pauses
export const COMMIT_AT = 0.62; // a release past this completes, before it reverts
export const SEAL_MS = 1250; // a hold seals in 1.25 s (p 0 → 1)
export const UNSEAL_MS = 900; // and unseals in 0.9 s
export const TICKS = [0.45, 0.6, 0.75, 0.9] as const; // selection haptic as each new crease forms (the last is Rigid)
export const RESUME_AFTER = 2.0; // s after the last touch, the film eases to rest and the loop restarts
export const TAP_MAX = 0.2; // s: a shorter press is a tap, which runs the whole seal by itself

/** the squeegee: the finger is the contact front (Pro upgrade 2) */
export const SQUEEGEE = {
  base: P_DRAPE, // p when the sheet lies on the card, before any air has left
  lead: 0.1, // the air runs this far (in order) ahead of the finger
  edge: 28, // design pt from the bottom / right edge: a drag that starts here peels the sealed film
  stretch: 0.3, // pt of lip per pt of finger beyond the bottom edge
  stretchMax: 8, // pt
  commitLead: 0.12, // release: p + commitLead · velocity decides complete or revert
  flick: 2.2, // p/s: a flick above this leaves the strongest creases
} as const;

/** springs (DESIGN-SYSTEM §8 names, plus the concept's raw film springs) */
export const SPRING = {
  complete: { damping: 26, stiffness: 180, mass: 1 }, // the film finishes sealing from the current p and rate
  revert: { damping: 20, stiffness: 140, mass: 1 }, // the air comes back, overshooting a little
  press: { damping: 18, stiffness: 400, mass: 1 }, // tile 1 → 0.96
  dimple: { damping: 14, stiffness: 260, mass: 1 }, // the dent under a finger on the sealed film
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 }, // back to rest before the loop restarts
  drop: { damping: 22, stiffness: 210, mass: 1 }, // the sheet falls onto the bare card under a finger
  tug: { damping: 14, stiffness: 260, mass: 1 }, // the tiles' tug at the lock and the lip's return
} as const;
/** the tiles are pulled this far toward the card at the lock (design pt); the neighbours follow 40 ms later */
export const TUG = { pt: 2, lag: 0.04, response: 0.39, zeta: 0.434 } as const;

export const DIMPLE = { r: 46, depth: 4.5 } as const; // design pt

// ---------------------------------------------------------------- worklet helpers (declared above their callers)

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** 0 before a, 1 after b */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(v: number) {
  'worklet';
  const x = clamp01(v);
  return x * x * (3 - 2 * x);
}

/** cubic-bezier easing, solved by Newton steps on x */
export function bezier(x1: number, y1: number, x2: number, y2: number, x: number) {
  'worklet';
  const v = clamp01(x);
  let t = v;
  for (let i = 0; i < 6; i++) {
    const it = 1 - t;
    const fx = 3 * it * it * t * x1 + 3 * it * t * t * x2 + t * t * t - v;
    const dx = 3 * it * it * x1 + 6 * it * t * (x2 - x1) + 3 * t * t * (1 - x2);
    if (Math.abs(dx) < 1e-6) break;
    t = clamp01(t - fx / dx);
  }
  const it = 1 - t;
  return 3 * it * it * t * y1 + 3 * it * t * t * y2 + t * t * t;
}

export function easeOut(v: number) {
  'worklet';
  return bezier(0.2, 0.8, 0.2, 1, v);
}

export function easeInOut(v: number) {
  'worklet';
  return bezier(0.65, 0, 0.35, 1, v);
}

/** 0 → 1 step response of a spring (response s, damping ratio zeta), evaluated analytically */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

/** a bump 0 → 1 → 0 over [a, b] */
export function bump(t: number, a: number, b: number) {
  'worklet';
  const x = seg(t, a, b);
  return Math.sin(Math.PI * x);
}

// ---------------------------------------------------------------- what follows from p (timeline and finger alike)

/** the tile's label and shield follow the film's STATE, not its progress: "Protected" and the amber check arrive at the
 *  lock, and leave as soon as air gets back in; each change is one timed morph. (Tied to p, both were held half-way through
 *  the one-second pause, which read as two labels on top of each other.) */
export const LABEL = { ms: 380, off: 0.97, on: 0.999, open: [0.24, 0.62], seal: [T.lock, T.lock + 0.38] } as const;

/** the label's state over the loop: 1 "Protected" … 0 "Protect Card" (the same timed morph a finger gets) */
export function labelAt(t: number) {
  'worklet';
  if (t < T.lock) return 1 - easeInOut(seg(t, LABEL.open[0], LABEL.open[1]));
  return easeInOut(seg(t, LABEL.seal[0], LABEL.seal[1]));
}

/** where the film meets the card, 0 at the top-left corner … 1 at the bottom edge: the shader's contactOrder(), same numbers.
 *  qx, qy: the point in design units from the card's centre; cbx, cby: the card's half size */
export function orderAt(qx: number, qy: number, cbx: number, cby: number) {
  'worklet';
  const ux = Math.min(1.3, Math.max(-1.3, qx / cbx)) * 0.5 + 0.5;
  const uy = Math.min(1.3, Math.max(-1.3, qy / cby)) * 0.5 + 0.5;
  return clamp01(0.42 * ux + 0.58 * uy);
}

/** the squeegee's p under a finger at (qx, qy): the sheet lies at 0.3, and the air has left up to the finger plus a lead */
export function fingerP(qx: number, qy: number, cbx: number, cby: number) {
  'worklet';
  return SQUEEGEE.base + (1 - SQUEEGEE.base) * clamp01(orderAt(qx, qy, cbx, cby) + SQUEEGEE.lead);
}

/** crease amplitude from the speed the air was pressed out at (p/s): a slow hold 0.8, a flick 1.25 */
export function creaseAmpOf(speed: number) {
  'worklet';
  return 0.8 + 0.45 * smooth((speed - 0.9) / (SQUEEGEE.flick - 0.9));
}

/** the tiles' tug at the lock, 0..1 of TUG.pt: pulled toward the card in 70 ms, then back on the lip's spring */
export function tugAt(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const pull = smooth(tau / 0.07);
  const relax = springAt(Math.max(tau - 0.07, 0), TUG.response, TUG.zeta);
  return pull * (1 - relax);
}

/** the film as the timeline plays it */
export type Look = {
  p: number; // film state 0..1
  press: number; // tile pressed 0..1 (scale 1 → 0.96)
  flash: number; // the disc's pale flash on press 0..1
  puff: number; // pillow overshoot as the air comes back 0..1
  settle: number; // crease amplitude factor (1 = rest)
  glint: number; // the lock's highlight running over the creases 0..1.6
  sweep: number; // the slow highlight's position −1..1 (0 = rest position)
  sweepK: number; // its strength 0..1
  tug: number; // the Protect tile's pull toward the card at the lock 0..1 (TUG.pt)
  tugSide: number; // the same for its neighbours, 40 ms later
  discGlint: number; // the amber disc's one glint 0..1
};

/** p over the loop */
export function pAt(t: number) {
  'worklet';
  if (t < T.unseal[0]) return 1;
  if (t < AIR_END) {
    // air in: the film lets go from the bottom right first (the reverse of the seal), easing in
    return 1 - (1 - P_DRAPE) * bezier(0.45, 0, 0.55, 1, seg(t, T.unseal[0], AIR_END));
  }
  if (t < T.unseal[1]) return P_DRAPE * (1 - bezier(0.32, 0, 0.67, 0, seg(t, AIR_END, T.unseal[1])));
  if (t < T.drape[0]) return 0;
  if (t < T.drape[1]) return P_DRAPE * seg(t, T.drape[0], T.drape[1]);
  if (t < T.evacA[1]) return P_DRAPE + (P_HOLD - P_DRAPE) * seg(t, T.evacA[0], T.evacA[1]);
  if (t < T.pause[1]) {
    // the held half-seal: the finger wavers (0.02 at 1.2 Hz), eased in and out so the front never jumps
    const tau = t - T.pause[0];
    const env = smooth(tau / 0.12) * (1 - smooth((tau - 0.88) / 0.12));
    return P_HOLD + 0.02 * env * Math.sin(2 * Math.PI * 1.2 * tau);
  }
  if (t < T.evacB[1]) return P_HOLD + (1 - P_HOLD) * seg(t, T.evacB[0], T.evacB[1]);
  return 1;
}

/** the light's sweeps: over the first second (the sealed card catches a softbox as it lets go), across the held
 *  half-seal, and across the creases after the lock */
export function sweepAt(t: number) {
  'worklet';
  if (t < 0.9) return { sweep: -1 + 2 * easeInOut(seg(t, 0, 0.9)), sweepK: 0.8 * bump(t, 0, 0.9) };
  if (t >= T.pause[0] && t < T.pause[1]) {
    return { sweep: -0.6 + 0.9 * easeInOut(seg(t, T.pause[0], T.pause[1])), sweepK: 0.6 * bump(t, T.pause[0], T.pause[1]) };
  }
  if (t >= T.sweep[0] && t < T.sweep[1]) {
    return { sweep: -1 + 2 * easeInOut(seg(t, T.sweep[0], T.sweep[1])), sweepK: 0.8 * bump(t, T.sweep[0], T.sweep[1]) };
  }
  return { sweep: -1, sweepK: 0 };
}

export function lookAt(t: number): Look {
  'worklet';
  const p = pAt(t);
  // tile press: the tap that unseals, then the long hold that seals (lifted a little while the finger is off)
  const tap = bump(t, T.untap[0], T.untap[1]);
  const hold = smooth(seg(t, T.press[0], T.press[0] + 0.08)) * (1 - smooth(seg(t, T.press[1], T.press[1] + 0.12)));
  const off = smooth(seg(t, T.pause[0], T.pause[0] + 0.15)) * (1 - smooth(seg(t, T.pause[1] - 0.2, T.pause[1]))) * 0.55;
  const press = Math.max(tap, hold * (1 - off));
  const flash = bump(t, T.press[0], T.press[0] + 0.32) + 0.6 * bump(t, T.untap[0], T.untap[0] + 0.3);
  // the pillow swells 4 % past its rest as the last air comes back in, before the sheet leaves
  const puff = bump(t, 0.58, 0.88);
  // creases relax 3 % after the lock (a light spring)
  const settle =
    t >= T.settle[0] ? 1 + 0.03 * (1 - springAt(t - T.settle[0], 0.42, 0.55)) : 1 + 0.03 * smooth(seg(t, T.evacB[0], T.evacB[1]));
  const glint = bump(t, T.settle[0], T.settle[0] + 0.25);
  const sw = sweepAt(t);
  const tug = tugAt(t - T.lock);
  const tugSide = tugAt(t - T.lock - TUG.lag);
  const discGlint = bump(t, T.lock + 0.06, T.lock + 0.18);
  return { p, press, flash, puff, settle, glint, sweep: sw.sweep, sweepK: sw.sweepK, tug, tugSide, discGlint };
}

/** the rest the screen shows first, with Reduce Motion, and between touches without autoplay */
export const REST: Look = {
  p: 1,
  press: 0,
  flash: 0,
  puff: 0,
  settle: 1,
  glint: 0,
  sweep: -1,
  sweepK: 0,
  tug: 0,
  tugSide: 0,
  discGlint: 0,
};

/** the slow drift of the key light, whole periods per loop so it repeats exactly (radians of azimuth) */
export function lightDrift(time: number) {
  'worklet';
  return 0.06 * Math.sin((2 * Math.PI * time) / LOOP) + 0.025 * Math.sin((4 * Math.PI * time) / LOOP + 1.3);
}

/** the wordmark's shimmer clock: one pass of the fill across the letters per loop */
export function shimmerAt(time: number) {
  'worklet';
  return (time % LOOP) / LOOP;
}
