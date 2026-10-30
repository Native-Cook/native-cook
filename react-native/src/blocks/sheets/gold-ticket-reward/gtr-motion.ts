// Gold Ticket Reward — the timeline, the touch driver and the per-frame values.
//
// One loop time `t` (0 … LOOP) decides every phase, so frame LOOP equals frame 0 and the three
// stacks can match frame for frame. autoPlay advances it in real time; a tap plays one part of it
// (rest → reward, or reward → rest) and parks; 2 s after the last touch the loop resumes from there.
//
// The story is the pin's arrow: the white "thanks" sheet (A) turns into the gold reward (B).
//   rest A → press → the cars dissolve, purple ink blooms from the ticket, gold pours across it,
//   the coin is minted out of its slot, spins up and lands with a stamp, stars burst out and settle
//   → reward B holds (one foil sweep, one coin glint) → the gold drains and everything returns.

import { A, B } from './gtr-theme';

export const LOOP = 9.0;
/** Seconds after the last touch before autoPlay takes over again. */
export const RESUME = 2;
/** Where a tap on the ticket parks: the reward, settled. */
export const PEAK = 3.25;
/** Coin frames in the atlas: 24 renders over half a turn (both faces are the same). */
export const COIN_FRAMES = 24;

export const T = {
  press: [1.45, 1.57] as const, // the ticket sinks under the finger (scale 0.97, ease-out)
  commit: 1.57, // ...and lets go: everything below starts here
  carsOut: [1.58, 2.12] as const, // the cars dissolve from the wheels up, a gold edge burning through
  glowOut: [1.6, 2.3] as const,
  thanksOut: [1.6, 1.84] as const, // the copy above leaves upwards, 40 ms apart
  callbackOut: [1.64, 1.88] as const,
  dividerOut: [1.68, 1.9] as const,
  dim: [1.66, 2.36] as const, // the page behind darkens from violet slate to graphite
  ink: [1.66, 2.45] as const, // purple rises from the bottom edge like a dawn and eats the cream sheet
  morph: 1.6, // the ticket springs from its A box to its B box (response 0.55 s, damping 0.74)
  halo: [1.68, 1.95] as const, // the white cradle round the coin sinks into the ticket
  gold: [1.86, 2.42] as const, // gold pours across the ticket left to right
  coinUp: [1.72, 2.3] as const, // the coin lifts out of its slot to 78 pt above it, growing
  coinDrop: [2.3, 2.6] as const, // ...and falls onto the gold ticket (gravity)
  coinSpin: [1.72, 2.6] as const, // two full turns on the way
  land: 2.6, // impact: stamp, ring, flash, stars
  squash: [2.6, 2.95] as const,
  ring: [2.6, 3.05] as const,
  flash: [2.6, 2.95] as const,
  burst: [2.6, 3.15] as const, // the stars fly out of the coin to where the pin shows them
  third: [1.76, 1.96] as const, // "Compare on the spot" makes room
  benefits: [1.8, 2.3] as const, // the two benefits grow, recentre and turn white
  button: [1.84, 2.32] as const, // the CTA turns from violet to white
  closeIn: [2.55, 2.85] as const,
  sweep: [3.7, 4.5] as const, // one specular band crosses the foil
  glint: [4.35, 4.85] as const, // ...and catches the coin
  ctaGlow: [4.9, 5.7] as const, // then Check Now breathes once: the one thing to do next
  carsGlint: [0.35, 1.15] as const, // at rest a showroom light runs across the cars' paint
  anticipation: [1.6, 1.72] as const, // the coin sinks 2.6 pt into its cradle before it jumps
  relief: [1.72, 1.95] as const, // the ticket rises 3 pt when the coin's weight leaves it
  hold: [3.15, 6.7] as const, // the reward rests
  // back to rest
  closeOut: [6.7, 6.84] as const,
  burstBack: [6.7, 6.98] as const,
  coinLift: [6.78, 7.05] as const,
  coinReturn: [7.05, 7.45] as const,
  coinBackSpin: [6.8, 7.45] as const, // one turn
  goldOut: [6.9, 7.32] as const,
  morphBack: [6.95, 7.55] as const,
  inkBack: [6.92, 7.6] as const,
  dimBack: [6.95, 7.6] as const,
  buttonBack: [7.0, 7.45] as const,
  benefitsBack: [7.0, 7.45] as const,
  haloIn: [7.3, 7.55] as const,
  glowIn: [7.15, 7.7] as const,
  carsIn: [7.2, 7.72] as const,
  thirdIn: [7.38, 7.6] as const,
  thanksIn: [7.42, 7.7] as const,
  callbackIn: [7.46, 7.74] as const,
  dividerIn: [7.5, 7.76] as const,
};

const COIN_PEAK = 78; // pt above the A slot
const COIN_PEAK_R = 1.35; // radius at the top of the throw, × A radius
const COIN_LIFT_BACK = 26;

// ------------------------------------------------------------------ easing (all worklets)

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
/** Strong ease-out, close to cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u * u * u;
}
/** On-screen movement, close to cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 16 * p * p * p * p * p : 1 - Math.pow(-2 * p + 2, 5) / 2;
}
export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}
/** Ease-out with a little overshoot (back), for the stars settling. */
export function easeOutBack(x: number, k = 1.4) {
  'worklet';
  const p = clamp01(x) - 1;
  return 1 + (k + 1) * p * p * p + k * p * p;
}
/** An underdamped spring from 0 to 1, `tau` seconds after release (Apple response/damping). */
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}
/** A damped wobble that starts at 1 and dies out (for the ticket's squash after the stamp). */
function wobble(tau: number, period: number, decay: number) {
  'worklet';
  if (tau <= 0) return 0;
  return Math.exp(-tau / decay) * Math.cos((2 * Math.PI * tau) / period);
}

// ------------------------------------------------------------------ the frame

export type Rect = { x: number; y: number; w: number; h: number };
export type Star = { x: number; y: number; s: number; a: number; rot: number };

export type Frame = {
  t: number;
  dim: number; // 0 A → 1 B
  glow: number; // A's golden light
  cars: number; // dissolve, 0 whole → 1 gone
  thanks: { a: number; dy: number };
  callback: { a: number; dy: number };
  divider: number;
  ink: number; // purple reveal radius, 0 … 1
  inkX: number;
  inkY: number;
  ticket: Rect;
  notch: number;
  tScaleX: number;
  tScaleY: number;
  tDy: number;
  gold: number; // 0 white → 1 gold (the wipe's position)
  sweep: number; // −1 off, else 0 … 1 across the foil
  lift: number; // 0 A shadow → 1 B shadow
  halo: number; // A's white cradle, 0 … 1
  coin: { x: number; y: number; r: number; frame: number; shadow: number };
  glint: number;
  ring: { r: number; a: number };
  flash: number;
  stars: Star[]; // B's stars
  starsA: number; // A's stars
  benefits: number; // 0 A → 1 B (first benefit)
  benefits2: number; // the second, 40 ms behind
  third: number; // "Compare on the spot"
  button: number; // 0 violet → 1 white
  close: { a: number; s: number; rot: number };
  bAmount: number; // how much of B is on screen (for idle motion)
  aura: number; // warm gold light behind the ticket: rises with the pour, flares on the stamp, breathes
  ctaGlow: number; // 0 … 1, one soft breath round Check Now while the reward rests
  carsGlint: number; // −1 off, else 0 … 1 across the cars
};

/** Warm light behind the ticket: follows the pour, flares at the stamp, then breathes while it rests. */
function auraAt(t: number, fwd: boolean, bAmount: number) {
  'worklet';
  const pour = fwd ? easeInOut(seg(t, T.gold[0], T.gold[1])) : 1 - easeInOut(seg(t, T.goldOut[0], T.goldOut[1]));
  const since = t - T.land;
  const flare = fwd && since > 0 ? 0.32 * Math.exp(-since / 0.28) * Math.min(1, since / 0.04) : 0;
  return pour * 0.2 + flare + bAmount * 0.04 * Math.sin((2 * Math.PI * 3 * t) / LOOP);
}

function boxMix(a: Rect, b: Rect, p: number): Rect {
  'worklet';
  return { x: mix(a.x, b.x, p), y: mix(a.y, b.y, p), w: mix(a.w, b.w, p), h: mix(a.h, b.h, p) };
}

/**
 * Everything on screen at loop time `t`. `extra` adds what touch brings on top of the timeline:
 * a spin of the coin (frames), a hop, a twinkle after Check Now, and the drag's tilt is applied by
 * the drawing.
 */
export function frameAt(t: number, extraSpin = 0, hop = 0, twinkle = 0): Frame {
  'worklet';
  const fwd = t < 5; // first half of the loop goes A → B, second half B → A

  // the ticket's box: a spring out, an ease back
  const morphP = fwd ? spring(t - T.morph, 0.55, 0.74) : 1 - easeInOut(seg(t, T.morphBack[0], T.morphBack[1]));
  const press = 1 - 0.03 * easeOut(seg(t, T.press[0], T.press[1])) * (1 - spring(t - T.commit, 0.35, 0.8));
  const land = t - T.land;
  const squash = fwd && land > 0 ? 0.035 * wobble(land, 0.24, 0.12) : 0;
  const ticket = boxMix(A.ticket, B.ticket, morphP);

  // coin
  const upP = easeOutCubic(seg(t, T.coinUp[0], T.coinUp[1]));
  const dropP = seg(t, T.coinDrop[0], T.coinDrop[1]);
  let cx = A.coin.x;
  let cy = A.coin.y;
  let cr = A.coin.r;
  let frame = 0;
  if (fwd) {
    if (t < T.coinDrop[0]) {
      cx = mix(A.coin.x, B.coin.x, upP);
      cy = A.coin.y - COIN_PEAK * upP;
      cr = A.coin.r * mix(1, COIN_PEAK_R, upP);
    } else {
      const g = dropP * dropP; // gravity
      cx = B.coin.x;
      cy = mix(A.coin.y - COIN_PEAK, B.coin.y, g);
      cr = mix(A.coin.r * COIN_PEAK_R, B.coin.r, g);
    }
    cy += 2.6 * Math.sin(Math.PI * seg(t, T.anticipation[0], T.anticipation[1]));
    if (land > 0) cy -= 3.2 * Math.max(0, Math.sin(Math.min(land / 0.22, 1) * Math.PI)) * Math.exp(-land * 4);
    frame = COIN_FRAMES * 4 * easeInOut(seg(t, T.coinSpin[0], T.coinSpin[1]));
  } else {
    const liftP = easeOut(seg(t, T.coinLift[0], T.coinLift[1]));
    const retP = easeInOut(seg(t, T.coinReturn[0], T.coinReturn[1]));
    cx = mix(B.coin.x, A.coin.x, retP);
    cy = mix(B.coin.y - COIN_LIFT_BACK * liftP, A.coin.y, retP);
    cr = mix(B.coin.r, A.coin.r, retP);
    frame = COIN_FRAMES * 2 * easeInOut(seg(t, T.coinBackSpin[0], T.coinBackSpin[1]));
  }
  const bAmount = fwd ? seg(t, T.hold[0] - 0.3, T.hold[0]) : 1 - seg(t, T.hold[1], T.hold[1] + 0.3);
  cy += bAmount * 1.6 * Math.sin((2 * Math.PI * 4 * t) / LOOP) - hop;
  frame += extraSpin;
  // how high the coin flies over the ticket, for its shadow (0 resting … 1 at the top)
  const air = fwd
    ? t < T.coinDrop[0] ? upP : 1 - dropP * dropP
    : easeOut(seg(t, T.coinLift[0], T.coinLift[1])) * (1 - seg(t, T.coinReturn[1] - 0.12, T.coinReturn[1]));

  // stars: out of the coin on the stamp, back into it on the way home
  const stars: Star[] = [];
  for (let i = 0; i < B.sparkles.length; i++) {
    const s = B.sparkles[i];
    const tw = 0.8 + 0.2 * (0.5 + 0.5 * Math.sin(2 * Math.PI * ((s.k * t) / LOOP + s.ph)));
    if (fwd) {
      const p = seg(t, T.burst[0] + i * 0.025, T.burst[1] + i * 0.025);
      const q = easeOutBack(p, 1.1);
      stars.push({
        x: mix(B.coin.x, s.x, q),
        y: mix(B.coin.y, s.y, q),
        s: s.s * mix(0.25, 1, easeOut(p)) * tw * (1 + 0.35 * twinkle * Math.sin(Math.PI * twinkle)),
        a: clamp01(p * 6),
        rot: (1 - easeOut(p)) * 1.6,
      });
    } else {
      const p = easeInOut(seg(t, T.burstBack[0], T.burstBack[1]));
      stars.push({ x: mix(s.x, B.coin.x, p * 0.35), y: mix(s.y, B.coin.y, p * 0.35), s: s.s * tw * (1 - 0.7 * p), a: 1 - p, rot: p * 0.8 });
    }
  }

  const outIn = (o: readonly [number, number], i: readonly [number, number]) =>
    fwd ? 1 - seg(t, o[0], o[1]) : seg(t, i[0], i[1]);

  const thanksA = fwd ? 1 - easeOutCubic(seg(t, T.thanksOut[0], T.thanksOut[1])) : easeOut(seg(t, T.thanksIn[0], T.thanksIn[1]));
  const callbackA = fwd ? 1 - easeOutCubic(seg(t, T.callbackOut[0], T.callbackOut[1])) : easeOut(seg(t, T.callbackIn[0], T.callbackIn[1]));

  const closeP = fwd ? easeOut(seg(t, T.closeIn[0], T.closeIn[1])) : 1 - seg(t, T.closeOut[0], T.closeOut[1]);
  const ringP = seg(t, T.ring[0], T.ring[1]);
  const flashP = seg(t, T.flash[0], T.flash[1]);
  const sweepP = seg(t, T.sweep[0], T.sweep[1]);

  return {
    t,
    dim: fwd ? easeInOut(seg(t, T.dim[0], T.dim[1])) : 1 - easeInOut(seg(t, T.dimBack[0], T.dimBack[1])),
    glow: fwd ? 1 - easeInOut(seg(t, T.glowOut[0], T.glowOut[1])) : easeInOut(seg(t, T.glowIn[0], T.glowIn[1])),
    cars: fwd ? seg(t, T.carsOut[0], T.carsOut[1]) : 1 - seg(t, T.carsIn[0], T.carsIn[1]),
    thanks: { a: thanksA, dy: -10 * (1 - thanksA) },
    callback: { a: callbackA, dy: -10 * (1 - callbackA) },
    divider: outIn(T.dividerOut, T.dividerIn),
    ink: fwd ? easeInOut(seg(t, T.ink[0], T.ink[1])) : 1 - easeInOut(seg(t, T.inkBack[0], T.inkBack[1])),
    inkX: A.ticket.x + A.ticket.w / 2,
    inkY: A.ticket.y + A.ticket.h / 2,
    ticket,
    notch: mix(A.notchR, B.notchR, morphP),
    tScaleX: press * (1 + squash * 0.6),
    tScaleY: press * (1 - squash),
    tDy: squash * 40 - (fwd ? 3 * easeOut(seg(t, T.relief[0], T.relief[1])) * (1 - seg(t, T.coinDrop[1] - 0.1, T.coinDrop[1])) : 0),
    gold: fwd ? easeInOut(seg(t, T.gold[0], T.gold[1])) : 1 - easeInOut(seg(t, T.goldOut[0], T.goldOut[1])),
    sweep: sweepP > 0 && sweepP < 1 ? easeInOut(sweepP) : -1,
    lift: fwd ? easeInOut(seg(t, T.gold[0], T.gold[1] + 0.2)) : 1 - easeInOut(seg(t, T.morphBack[0], T.morphBack[1])),
    halo: fwd ? 1 - easeInOut(seg(t, T.halo[0], T.halo[1])) : easeOut(seg(t, T.haloIn[0], T.haloIn[1])),
    coin: { x: cx, y: cy, r: cr, frame, shadow: air },
    glint: seg(t, T.glint[0], T.glint[1]),
    ring: { r: B.coin.r * mix(1.05, 3.4, easeOut(ringP)), a: ringP > 0 && ringP < 1 ? 0.85 * (1 - ringP) : 0 },
    flash: flashP > 0 && flashP < 1 ? Math.sin(Math.PI * Math.min(1, flashP * 2.4)) * (1 - flashP) : 0,
    stars,
    starsA: fwd ? 1 - seg(t, T.glowOut[0], T.glowOut[0] + 0.25) : seg(t, T.glowIn[1] - 0.25, T.glowIn[1]),
    benefits: fwd ? easeInOut(seg(t, T.benefits[0], T.benefits[1])) : 1 - easeInOut(seg(t, T.benefitsBack[0], T.benefitsBack[1])),
    benefits2: fwd
      ? easeInOut(seg(t, T.benefits[0] + 0.04, T.benefits[1] + 0.04))
      : 1 - easeInOut(seg(t, T.benefitsBack[0] + 0.04, T.benefitsBack[1] + 0.04)),
    third: outIn(T.third, T.thirdIn),
    button: fwd ? easeInOut(seg(t, T.button[0], T.button[1])) : 1 - easeInOut(seg(t, T.buttonBack[0], T.buttonBack[1])),
    close: { a: closeP, s: mix(0.8, 1, closeP), rot: (1 - closeP) * -1.0 },
    bAmount,
    aura: auraAt(t, fwd, bAmount),
    ctaGlow: Math.sin(Math.PI * seg(t, T.ctaGlow[0], T.ctaGlow[1])),
    carsGlint: t > T.carsGlint[0] && t < T.carsGlint[1] ? seg(t, T.carsGlint[0], T.carsGlint[1]) : -1,
  };
}

// ------------------------------------------------------------------ the touch driver

/** How far (pt) a drag up the ticket scrubs the whole reveal, and where the scrub stops. */
export const SCRUB_DIST = 210;
export const SCRUB_END = 2.45;
/** Past this share of the scrub (or flicked up faster than FLICK pt/s) the reveal commits. */
export const COMMIT = 0.4;
const FLICK = 600;

export type Driver = {
  t: number;
  mode: 0 | 1 | 2 | 3; // 0 autoPlay, 1 playing to `stop`, 2 parked, 3 the finger scrubs it
  stop: number;
  rate: number; // playback speed while playing to `stop` (negative rewinds)
  last: number; // clock time of the last touch
  ctaAt: number; // clock time of the last Check Now tap in B (−1 none), for the stars' twinkle
  ctaAim: number; // 1 while a finger is on Check Now
  ctaP: number; // the press, following ctaAim
  tiltX: number; // current tilt, −1 … 1
  tiltY: number;
  aimX: number; // where the finger wants it
  aimY: number;
  lift: number; // pt the ticket follows the finger up while scrubbing (springs back)
  liftAim: number;
  // the coin on a flick: frames turned, frames per second, settling onto a face, a hop
  spinPos: number;
  spinVel: number;
  settling: number;
  spinTarget: number;
  hopAt: number;
  hopAmp: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return {
    t,
    mode: 0,
    stop: 0,
    rate: 1,
    last: -10,
    ctaAt: -1,
    ctaAim: 0,
    ctaP: 0,
    tiltX: 0,
    tiltY: 0,
    aimX: 0,
    aimY: 0,
    lift: 0,
    liftAim: 0,
    spinPos: 0,
    spinVel: 0,
    settling: 0,
    spinTarget: 0,
    hopAt: -1,
    hopAmp: 0,
  };
}

/** Rest A covers the start and the end of the loop. */
export function inRestA(t: number) {
  'worklet';
  return t < T.press[0] || t >= T.dividerIn[1];
}
/** The reward is up (from the moment its × has turned in) until the way home starts. */
export function inRewardB(t: number) {
  'worklet';
  return t >= T.closeIn[1] && t < T.hold[1];
}
/** How much of B is on screen at t (for idle motion and the gyroscope). */
export function bAmountAt(t: number) {
  'worklet';
  return t < 5 ? seg(t, T.hold[0] - 0.3, T.hold[0]) : 1 - seg(t, T.hold[1], T.hold[1] + 0.3);
}

function follow(cur: number, aim: number, k: number, dt: number) {
  'worklet';
  return cur + (aim - cur) * (1 - Math.exp(-dt * k));
}

/** The coin after a flick: it spins down with friction, then a loose spring lays it face up (it wobbles). */
function stepCoin(d: Driver, dt: number) {
  'worklet';
  if (d.settling === 0 && d.spinVel === 0) return;
  const n = 2;
  const h = dt / n;
  for (let i = 0; i < n; i++) {
    if (d.settling === 0) {
      d.spinPos += d.spinVel * h;
      d.spinVel *= Math.exp(-2.1 * h);
      if (Math.abs(d.spinVel) < 26) {
        d.settling = 1;
        d.spinTarget = d.spinVel >= 0 ? Math.ceil(d.spinPos / COIN_FRAMES) * COIN_FRAMES : Math.floor(d.spinPos / COIN_FRAMES) * COIN_FRAMES;
      }
    } else {
      const k = 230;
      const c = 2 * 0.32 * Math.sqrt(k);
      d.spinVel += (-k * (d.spinPos - d.spinTarget) - c * d.spinVel) * h;
      d.spinPos += d.spinVel * h;
      if (Math.abs(d.spinPos - d.spinTarget) < 0.03 && Math.abs(d.spinVel) < 0.4) {
        d.spinPos = 0;
        d.spinVel = 0;
        d.settling = 0;
      }
    }
  }
}

/** Advance the driver by dt seconds; returns the loop time to draw. */
export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  if (d.mode === 1) {
    d.t += dt * d.rate;
    if (d.rate >= 0 && d.t >= d.stop) {
      d.t = d.stop >= LOOP ? 0 : d.stop;
      d.mode = 2;
    } else if (d.rate < 0 && d.t <= d.stop) {
      d.t = 0; // rewound to rest
      d.mode = 2;
      d.rate = 1;
    }
  } else if (d.mode === 2) {
    if (auto && now - d.last >= RESUME) {
      d.mode = 0;
      d.rate = 1;
    }
  } else if (d.mode === 0 && auto) {
    d.t = (d.t + dt) % LOOP;
  }
  d.tiltX = follow(d.tiltX, d.aimX, 12, dt);
  d.tiltY = follow(d.tiltY, d.aimY, 12, dt);
  d.lift = follow(d.lift, d.liftAim, d.mode === 3 ? 30 : 9, dt);
  d.ctaP = follow(d.ctaP, d.ctaAim, d.ctaAim > 0 ? 40 : 18, dt);
  stepCoin(d, dt);
  return d.t;
}

/** A finger came down: autoPlay stops; a transition already under way finishes where it leads. */
export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode !== 0) return;
  d.rate = 1;
  if (d.t >= T.press[0] && d.t < PEAK) {
    d.mode = 1;
    d.stop = PEAK;
  } else if (d.t >= T.hold[1] && d.t < T.dividerIn[1]) {
    d.mode = 1;
    d.stop = LOOP;
  } else d.mode = 2;
}

/** The finger is on the ticket or the CTA in A: the ticket sinks and waits. */
export function pressA(d: Driver) {
  'worklet';
  if (!inRestA(d.t)) return false;
  d.t = T.press[0];
  d.mode = 1;
  d.rate = 1;
  d.stop = T.commit - 0.001;
  return true;
}

/** Share of the reveal a drag of `dy` pt scrubs: 1:1 up to the end, then rubber-banded. */
export function scrubShare(dy: number) {
  'worklet';
  const raw = -dy / SCRUB_DIST;
  if (raw <= 0) return 0;
  return raw <= 1 ? raw : 1 + (raw - 1) / (1 + (raw - 1) * 4) * 0.25;
}
/** The finger drags the ticket up: the reveal follows it, frame for frame. */
export function scrubTo(d: Driver, dy: number) {
  'worklet';
  const p = scrubShare(dy);
  d.mode = 3;
  d.t = T.commit + Math.min(p, 1) * (SCRUB_END - T.commit);
  d.liftAim = -Math.min(Math.max(-dy, 0), SCRUB_DIST * 1.4) * 0.12;
  return p;
}
/** Let go of a scrub: commit on distance or an upward flick (faster with the flick), else rewind. */
export function scrubEnd(d: Driver, dy: number, vy: number) {
  'worklet';
  const p = scrubShare(dy);
  d.liftAim = 0;
  d.mode = 1;
  if (p >= COMMIT || vy < -FLICK) {
    d.rate = 1 + Math.min(0.6, Math.max(0, -vy) / 3000);
    d.stop = PEAK;
    return true;
  }
  d.rate = -2.2;
  d.stop = T.press[0];
  return false;
}
/** Released on target without dragging: play on to the reward. Released elsewhere: back to rest. */
export function releaseA(d: Driver, onTarget: boolean) {
  'worklet';
  if (onTarget) {
    d.mode = 1;
    d.rate = 1;
    d.stop = PEAK;
  } else {
    d.t = 0;
    d.mode = 2;
  }
}
/** Close in B: play the way home, 1.6× faster than the demo (a dismissal answers, it doesn't perform). */
export function closeB(d: Driver) {
  'worklet';
  if (!inRewardB(d.t)) return false;
  d.t = T.hold[1];
  d.mode = 1;
  d.rate = 1.6;
  d.stop = LOOP;
  return true;
}

/** Flick (or tap) the coin: its spin follows the finger's speed; a tap gives about one turn. */
export function flickCoin(d: Driver, vx: number, now: number) {
  'worklet';
  const v = Math.abs(vx) < 60 ? 110 : Math.max(-300, Math.min(300, vx * 0.14));
  d.spinVel = d.settling === 1 ? v : d.spinVel + v;
  d.settling = 0;
  d.hopAt = now;
  d.hopAmp = Math.min(16, 6 + Math.abs(v) / 25);
}
/** The coin's extra frames and its hop, from the flick physics. */
export function coinExtra(d: Driver, now: number) {
  'worklet';
  const p = d.hopAt < 0 ? 1 : (now - d.hopAt) / 0.55;
  return { spin: d.spinPos, hop: p < 1 ? d.hopAmp * Math.sin(Math.PI * p) : 0 };
}
export function ctaTwinkle(d: Driver, now: number) {
  'worklet';
  if (d.ctaAt < 0) return 0;
  const p = (now - d.ctaAt) / 0.6;
  if (p >= 1) {
    d.ctaAt = -1;
    return 0;
  }
  return p;
}
