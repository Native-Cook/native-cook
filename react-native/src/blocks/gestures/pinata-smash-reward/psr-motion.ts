// Piñata Smash Reward — the timeline, in closed form.
//
// One story drives everything: a list of hits (time, tap point, kind) plus the moment the
// piñata is cleared (the way back after a burst, or the drain after an unfinished round). Every
// frame is a pure function of (time, story), so the autoPlay loop records frame-exact and the
// finger reuses the same code: a tap appends a hit to a live copy of the story.
//
// autoPlay: LOOP = 8.4 s (plan §4). Pin times in brackets.
//   0.00–0.60  rest: idle sway and twinkle only
//   0.60       hit 1 [0.80]: label → bar, ripple, bloom, token; the piñata hops 120 pt up its ribbon,
//              hangs and lands with a bounce (rise 0.43 s, hang 0.35 s, fall 0.40 s)
//   1.85–3.54  hits 2–8: a jolt each (two-angle pendulum, hit-stop, squash), loot, token, fill +1/9;
//              cracks at 33 % and 67 %, a tremble from 78 %
//   3.74       hit 9 [6.50]: the burst (hit-stop + flash, shock ring, slow-motion loot, punch-in,
//              warm light from the hole), done pill, streamers; tokens bank into the chip
//   6.00–7.35  ours: the pile is vacuumed into the chip, the burst piñata is hoisted away, a fresh
//              one drops in on its ribbon, the done pill turns back into the label
//   7.60–8.40  rest; every spring is windowed to exactly 0 at 7.60, so LOOP − 1/60 matches t = 0

import { STAGE_DY } from './psr-theme';

export const LOOP = 8.4;

/** Hit kinds. */
export const HOP = 0;
export const JOLT = 1;
export const BURST = 2;
export const POKE = 3; // a tap after the burst

export type Hit = {
  t: number;
  x: number;
  y: number;
  kind: number;
  amp: number; // degrees of swing, signed (positive pushes the piñata to the right)
  inc: number; // fill added by this hit
  seed: number;
  loot: number[]; // atlas kinds (LOOT_*)
  conf: number; // confetti count
  tiles: number; // paper tiles count
  tokens: number;
  value: number; // points per token
};

export type Story = { hits: Hit[]; clear: number; clearKind: number };
export const CLEAR_BACK = 0;
export const CLEAR_DRAIN = 1;

// ---------------------------------------------------------------- tunables (plan §4–§6)
export const M = {
  restSway: 1.2, // degrees, 3 cycles per LOOP
  swayCycles: 3,
  tailLag: 0.08,
  joltDecay: 0.3,
  joltPeriod: 0.55,
  ribbonPerDeg: 2.2, // pt of attach-point shift per degree of swing
  bodyLag: 0.05,
  bodyGain: 1.25,
  stop: 0.033, // hit-stop
  burstStop: 0.09,
  squash: 0.06,
  dip: 3,
  yawGain: 1.6,
  yawMax: 16,
  hop: { rise: 0.43, hang: 0.35, fall: 0.4, height: 120, land: 8 },
  bloomDur: 0.42,
  ripple: { dur: 0.32, from: 15, to: 163 },
  ring: { dur: 0.38, from: 20, to: 210 },
  token: { pop: 0.02, ride: 0.25, fly: 0.52 },
  fillGrow: 0.12,
  gravity: 2100,
  restitution: 0.32,
  confGravity: 900,
  confTerm: 260,
  slow: { rate: 0.35, pop: 0.08, hold: 0.42, ramp: 0.25 },
  back: { vacuum: 0, vacuumDur: 0.34, vacuumSpread: 0.14, dip: 0.06, hoist: 0.15, swap: 0.6, land: 0.95, pill: 1.05, chip: 1.05, end: 1.6 },
  drain: { dur: 0.5, pill: 0.5, end: 0.7 },
  idleReturn: 2,
  hopGap: 1.5,
  merge: 0.09,
  liveInc: 1 / 12,
  releaseMax: 520, // pt/s
};

// Atlas kinds (indices into ATLAS_NAMES).
export const ATLAS_NAMES = [
  'green-sweet',
  'magenta-sweet',
  'lollipop',
  'mint',
  'star',
  'bonbon',
  'gumdrop',
  'tile-violet',
  'tile-azure',
  'tile-aqua',
  'glow',
] as const;
export const K_GREEN = 0;
export const K_MAGENTA = 1;
export const K_LOLLY = 2;
export const K_MINT = 3;
export const K_STAR = 4;
export const K_BONBON = 5;
export const K_GUMDROP = 6;
export const K_TILE = 7; // 7, 8, 9
export const K_GLOW = 10;
/** On-screen width of each kind, pt (1.3 × the pin's sizes). */
export const KIND_W = [40, 40, 29, 22, 26, 21, 22, 14, 14, 14, 34];

// ---------------------------------------------------------------- the scripted story

function mk(t: number, x: number, y: number, kind: number, amp: number, inc: number, seed: number, loot: number[], conf: number, tiles: number, tokens: number, value: number): Hit {
  'worklet';
  return { t, x, y, kind, amp, inc, seed, loot, conf, tiles, tokens, value };
}

function burstLoot(): number[] {
  'worklet';
  const out: number[] = [];
  const order = [K_GREEN, K_STAR, K_MAGENTA, K_LOLLY, K_MINT, K_BONBON, K_GUMDROP, K_MAGENTA, K_GREEN];
  for (let i = 0; i < 36; i++) out.push(order[(i * 5 + (i >> 2)) % order.length]);
  return out;
}

export const SCRIPT: Hit[] = [
  mk(0.6, 201, 359 + STAGE_DY, HOP, 0, 1 / 9, 1, [], 3, 0, 1, 10),
  mk(1.85, 170, 370 + STAGE_DY, JOLT, 5, 1 / 9, 2, [K_GREEN, K_MAGENTA], 0, 0, 1, 10),
  mk(2.3, 230, 365 + STAGE_DY, JOLT, -4, 1 / 9, 3, [K_MAGENTA, K_GUMDROP], 4, 0, 1, 10),
  mk(2.62, 165, 350 + STAGE_DY, JOLT, 5, 1 / 9, 4, [K_LOLLY, K_MINT], 4, 0, 1, 10),
  mk(2.9, 240, 380 + STAGE_DY, JOLT, -6, 1 / 9, 5, [K_STAR, K_GREEN], 5, 0, 1, 10),
  mk(3.14, 150, 370 + STAGE_DY, JOLT, 6, 1 / 9, 6, [K_MAGENTA, K_BONBON, K_GREEN], 5, 0, 1, 10),
  mk(3.35, 225, 355 + STAGE_DY, JOLT, -7, 1 / 9, 7, [K_STAR, K_STAR, K_MINT], 6, 0, 1, 10),
  mk(3.54, 175, 365 + STAGE_DY, JOLT, 7, 1 / 9, 8, [K_GUMDROP, K_LOLLY, K_MAGENTA, K_BONBON], 6, 0, 1, 10),
  mk(3.74, 156, 365 + STAGE_DY, BURST, -10, 1 / 9, 9, burstLoot(), 40, 18, 2, 20),
];
export const SCRIPT_BACK = 6.0;
export const SCRIPT_STORY: Story = { hits: SCRIPT, clear: SCRIPT_BACK, clearKind: CLEAR_BACK };

// ---------------------------------------------------------------- helpers (declared before use)
export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeOutCubic(u: number) {
  'worklet';
  const v = 1 - u;
  return 1 - v * v * v;
}
export function easeInCubic(u: number) {
  'worklet';
  return u * u * u;
}
export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
export function easeOutQuad(u: number) {
  'worklet';
  return 1 - (1 - u) * (1 - u);
}
export function smooth(u: number) {
  'worklet';
  const v = clamp01(u);
  return v * v * (3 - 2 * v);
}
export function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}
/** Step response of a spring (0 → 1) after `tau` seconds; response in s, damping ratio z < 1. */
export function spring(tau: number, response: number, z: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w0 = (2 * Math.PI) / response;
  const a = z * w0;
  const wd = w0 * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-a * tau) * (Math.cos(wd * tau) + (a / wd) * Math.sin(wd * tau));
}
/** Impulse-like damped sine that starts at 0. */
export function ring(tau: number, decay: number, period: number) {
  'worklet';
  if (tau <= 0) return 0;
  return Math.exp(-tau / decay) * Math.sin((2 * Math.PI * tau) / period);
}
const DEG = Math.PI / 180;

/** The done pill's width spring, and the time it first reaches 98 % of its travel. */
const DONE_SPRING = { response: 0.24, z: 0.8 };
const DONE_T98 = (() => {
  for (let k = 1; k < 200; k++) if (spring(k / 200, DONE_SPRING.response, DONE_SPRING.z) >= 0.98) return k / 200;
  return 0.3;
})();

/**
 * Slow-motion clock of the burst's loot (U2): the blast pops at full speed for 80 ms so the pieces
 * clear the hole, then hangs at 0.35× for 340 ms and comes back to 1× over 250 ms.
 */
export function slowTau(tau: number) {
  'worklet';
  const r = M.slow.rate;
  const p = M.slow.pop;
  const h = M.slow.hold;
  const g = M.slow.ramp;
  if (tau <= p) return tau;
  if (tau <= h) return p + (tau - p) * r;
  const base = p + (h - p) * r;
  if (tau <= h + g) {
    const u = tau - h;
    return base + r * u + ((1 - r) * u * u) / (2 * g);
  }
  return base + (r * g + (1 - r) * g * 0.5) + (tau - h - g);
}

/** Hit-stop: the body's own clock pauses for a moment at every hit. */
function bodyTime(t: number, hits: Hit[]) {
  'worklet';
  let te = t;
  for (let i = 0; i < hits.length; i++) {
    const h = hits[i];
    if (h.t > t) break;
    const s = h.kind === BURST ? M.burstStop : h.kind === POKE ? 0.02 : M.stop;
    te -= Math.min(t - h.t, s);
  }
  return te;
}

/** Hop of the first hit (y offset, roll and yaw in degrees), as a function of time since the tap. */
function hopAt(tau: number) {
  'worklet';
  const H = M.hop;
  const t0 = 0.02;
  const r = tau - t0;
  let y = 0;
  let roll = 0;
  let yaw = 0;
  if (r <= 0) return { y, roll, yaw };
  if (r < H.rise) {
    const u = easeOutCubic(r / H.rise);
    y = -H.height * u;
    roll = -6 * u;
    yaw = 14 * u;
  } else if (r < H.rise + H.hang) {
    const u = (r - H.rise) / H.hang;
    y = -H.height - 4 * Math.sin(Math.PI * u);
    roll = -6 + 2 * smooth(u);
    yaw = 14 - 24 * smooth(u);
  } else if (r < H.rise + H.hang + H.fall) {
    const u = (r - H.rise - H.hang) / H.fall;
    const e = easeInOutCubic(u);
    y = -H.height + (H.height + H.land) * e;
    roll = -4 + 7 * e;
    yaw = -10 * (1 - e);
  } else {
    const s = r - H.rise - H.hang - H.fall;
    // lands 8 pt low and springs back (response 0.35, damping 0.5)
    y = H.land * (1 - spring(s, 0.35, 0.5));
    roll = 3 * (1 - spring(s, 0.45, 0.55));
  }
  return { y, roll, yaw };
}

// ---------------------------------------------------------------- the frame
export type Loot = { k: number; x: number; y: number; rot: number; s: number; a: number; sx: number };
export type Conf = { x: number; y: number; w: number; h: number; rot: number; c: number; a: number; diamond: number };
export type Token = { x: number; y: number; s: number; a: number; tx: number[]; ty: number[] };

export type Frame = {
  // body: attach point on screen, rotation about it (rad), yaw (rad), squash
  ax: number;
  ay: number;
  roll: number;
  yaw: number;
  sq: number;
  sqAng: number;
  press: number;
  tail: number; // extra tail bend (rad)
  bow: number; // ribbon slack, pt to the left
  burst: number;
  crack1: number;
  crack2: number;
  bloomX: number;
  bloomY: number;
  bloom: number;
  hole: number;
  bulbBoost: number; // global multiplier
  waveFrom: number; // bulb index the latest wave starts from (-1 none)
  waveTau: number;
  flicker: number;
  rippleX: number;
  rippleY: number;
  rippleR: number;
  rippleA: number;
  ringX: number;
  ringY: number;
  ringR: number;
  ringA: number;
  flash: number;
  stage: number;
  stageX: number;
  stageY: number;
  // pill
  pillW: number;
  wL: number;
  wB: number;
  wD: number;
  fill: number;
  fillGlow: number;
  shake: number;
  sparks: number; // time since burst sparks (or -1)
  doneText: number; // 0..1 alpha of "Come back tomorrow" (waits for the width)
  // header
  count: number;
  chipBump: number;
  // particles
  tokens: Token[];
  loot: Loot[];
  conf: Conf[];
  streamers: number; // time since the streamers began (or -1)
  fade: number; // confetti / streamer fade on the way back (1 = visible)
  shadowLift: number;
  swayT: number; // time for the idle twinkle and sway
};

export type Pt = { x: number; y: number };
/** Runtime geometry: the chip's coin, and the art's points at rest (pt). */
export type Geo = { chipX: number; chipY: number; bulbs: Pt[]; flank: Pt; body: Pt; attach: Pt };

/** Fill (0..1) of the current round and the time of the round's first hit. */
function roundState(story: Story, t: number) {
  'worklet';
  const hits = story.hits;
  let fill = 0;
  let first = -1;
  let burstT = -1;
  let after = story.clear >= 0 && t >= story.clear ? story.clear : -1;
  for (let i = 0; i < hits.length; i++) {
    const h = hits[i];
    if (h.t > t) break;
    if (after >= 0 && h.t < after) continue;
    if (h.kind === POKE) continue;
    if (first < 0) first = h.t;
    if (h.kind === BURST) burstT = h.t;
    const u = clamp01((t - h.t) / M.fillGrow);
    // grows in 120 ms with a 3 % overshoot (U7)
    const g = easeOutCubic(u) + 0.03 * Math.sin(Math.PI * u);
    fill += h.inc * g;
  }
  return { fill: Math.min(fill, 1.03), first, burstT, after };
}

/** The fill reached right after each hit (for crack stages), evaluated at the hit's own time. */
function fillAfter(story: Story, idx: number) {
  'worklet';
  const hits = story.hits;
  const h = hits[idx];
  let f = 0;
  const after = story.clear >= 0 && h.t >= story.clear ? story.clear : -1;
  for (let i = 0; i <= idx; i++) {
    const g = hits[i];
    if (after >= 0 && g.t < after) continue;
    if (g.kind === POKE) continue;
    f += g.inc;
  }
  return f;
}

/** First hit, burst time and total fill of the hits in (from, to]. */
function roundInfo(hits: Hit[], from: number, to: number) {
  'worklet';
  let first = -1;
  let burst = -1;
  let inc = 0;
  for (let i = 0; i < hits.length; i++) {
    const h = hits[i];
    if (h.t > to) break;
    if (h.t < from || h.kind === POKE) continue;
    if (first < 0) first = h.t;
    if (h.kind === BURST) burst = h.t;
    inc += h.inc;
  }
  return { first, burst, inc };
}

/** Ballistic loot piece with a floor and two bounces; returns position, angle, alpha. */
function lootPiece(
  out: Loot[],
  k: number,
  x0: number,
  y0: number,
  vx: number,
  vy: number,
  spin: number,
  restRot: number,
  floorY: number,
  tau: number,
  scale: number,
) {
  'worklet';
  const g = M.gravity;
  const dy = floorY - y0;
  const T = (-vy + Math.sqrt(vy * vy + 2 * g * Math.max(dy, 1))) / g;
  const xl = Math.min(Math.max(x0 + vx * T * 0.55, 18), 384);
  const vxe = (xl - x0) / T;
  let x = 0;
  let y = 0;
  let rot = 0;
  if (tau < T) {
    x = x0 + vxe * tau;
    y = y0 + vy * tau + 0.5 * g * tau * tau;
    rot = spin * tau;
  } else {
    const vimp = vy + g * T;
    const v1 = vimp * M.restitution;
    const T1 = (2 * v1) / g;
    const v2 = v1 * M.restitution;
    const T2 = (2 * v2) / g;
    const s = tau - T;
    const drift = vxe * 0.18;
    const aT = spin * T;
    // settle to the nearest rest angle (lying flat-ish)
    let target = restRot;
    const half = Math.PI;
    target = target + Math.round((aT - target) / half) * half;
    if (s < T1) {
      x = xl + drift * s;
      y = floorY - (v1 * s - 0.5 * g * s * s);
      rot = aT + (target - aT) * easeOutQuad(s / (T1 + T2));
    } else if (s < T1 + T2) {
      const q = s - T1;
      x = xl + drift * (T1 + q * 0.5);
      y = floorY - (v2 * q - 0.5 * g * q * q);
      rot = aT + (target - aT) * easeOutQuad(s / (T1 + T2));
    } else {
      x = xl + drift * (T1 + T2 * 0.5);
      y = floorY;
      rot = target;
    }
  }
  out.push({ k, x, y, rot, s: scale, a: 1, sx: 1 });
}

/** Confetti with linear drag (terminal 260 pt/s), flutter and tumble. */
function confPiece(out: Conf[], x0: number, y0: number, vx: number, vy: number, tau: number, seed: number, c: number, fade: number) {
  'worklet';
  const k = M.confTerm / M.confGravity;
  const e = 1 - Math.exp(-tau / k);
  const x = x0 + vx * k * e + 12 * Math.sin(2 * Math.PI * 3 * tau + seed * 6.28) * clamp01(tau * 2);
  const y = y0 + M.confTerm * tau + (vy - M.confTerm) * k * e;
  if (y > 900) return;
  const spin = (4 + hash(seed + 3) * 8) * (hash(seed + 4) > 0.5 ? 1 : -1);
  const ang = spin * tau + seed * 3;
  const diamond = hash(seed + 5) < 0.3 ? 1 : 0;
  const w = diamond ? 8 : 4;
  const h = diamond ? 8 : 9;
  const tumble = Math.cos(ang * 0.8);
  const a = fade * (1 - seg(y, 820, 880));
  out.push({ x, y, w: w * (0.35 + 0.65 * Math.abs(tumble)), h, rot: ang, c, a, diamond });
}

function nearestBulb(geo: Geo, x: number, y: number) {
  'worklet';
  let best = 0;
  let bd = 1e9;
  for (let i = 0; i < geo.bulbs.length; i++) {
    const d = (geo.bulbs[i].x - x) ** 2 + (geo.bulbs[i].y - y) ** 2;
    if (d < bd) {
      bd = d;
      best = i;
    }
  }
  return best;
}

/** Everything the canvas draws at story time t. `geo` gives the chip and bulb positions. */
export function frameAt(t: number, story: Story, geo: Geo, swayT: number): Frame {
  'worklet';
  const hits = story.hits;
  const te = bodyTime(t, hits);
  const clear = story.clear;
  const B = M.back;
  const back = clear >= 0 && story.clearKind === CLEAR_BACK && t >= clear ? t - clear : -1;
  const drain = clear >= 0 && story.clearKind === CLEAR_DRAIN && t >= clear ? t - clear : -1;
  const swapped = back >= B.swap; // the fresh piñata hangs there now
  const rs = roundState(story, t);

  // ---- body pose
  const sway = M.restSway * Math.sin((2 * Math.PI * M.swayCycles * swayT) / LOOP);
  const swayLag = M.restSway * Math.sin((2 * Math.PI * M.swayCycles * (swayT - M.tailLag)) / LOOP);
  let swing = 0; // degrees, ribbon (leads)
  let swingBody = 0; // degrees, body (lags 50 ms)
  let swingTail = 0;
  let yaw = 0;
  let dy = 0;
  let sq = 0;
  let sqAng = 0;
  let hopY = 0;
  let hopRoll = 0;
  let hopYaw = 0;
  let bloom = 0;
  let bloomX = geo.body.x;
  let bloomY = geo.body.y;
  let flashBulbs = 0;
  let waveFrom = -1;
  let waveTau = 0;
  let rippleA = 0;
  let rippleR = 0;
  let rippleX = geo.body.x;
  let rippleY = geo.body.y;
  let ringA = 0;
  let ringR = 0;
  let flash = 0;
  let stage = 1;
  let hole = 0;
  let burstMix = 0;
  let crack1 = 0;
  let crack2 = 0;
  let tremble = 0;
  let shake = 0;
  let sparks = -1;
  let streamers = -1;
  for (let i = 0; i < hits.length; i++) {
    const h = hits[i];
    if (h.t > t) break;
    const tau = t - h.t;
    const tb = te - bodyTime(h.t, hits); // body time since the hit (hit-stop included)
    const old = swapped && h.t < clear; // belongs to the hoisted piñata
    // body reaction
    if (h.kind === HOP) {
      const hp = hopAt(tau);
      hopY += hp.y;
      hopRoll += hp.roll;
      hopYaw += hp.yaw;
    } else if (h.kind === JOLT || h.kind === POKE) {
      const amp = h.amp * M.bodyGain;
      swing += h.amp * ring(tb, M.joltDecay, M.joltPeriod);
      swingBody += amp * ring(tb - M.bodyLag, M.joltDecay, M.joltPeriod);
      swingTail += amp * 1.3 * ring(tb - M.bodyLag - M.tailLag, M.joltDecay * 1.2, M.joltPeriod);
      const ya = Math.min(Math.abs(h.amp) * M.yawGain, M.yawMax) * (h.amp > 0 ? -1 : 1);
      yaw += ya * ring(tb - 0.02, 0.4, 0.8);
    } else if (h.kind === BURST && !old) {
      swing += h.amp * ring(tb, M.joltDecay, M.joltPeriod) + -8 * ring(tb, 1.1, 1.9) * 0.6;
      swingBody += -12 * ring(tb - M.bodyLag, 0.28, 0.6) + -8 * ring(tb - 0.1, 1.1, 1.9);
      swingTail += -16 * ring(tb - 0.13, 0.4, 0.6) + -9 * ring(tb - 0.2, 1.1, 1.9);
      yaw += -16 * ring(tb, 0.35, 0.7);
    }
    if (old) continue;
    if (tb < 0.12) dy += M.dip * Math.sin((Math.PI * tb) / 0.12) * (h.kind === POKE ? 0.5 : 1);
    if (tb < 0.5) {
      const sqv = (h.kind === BURST ? 1.6 : 1) * M.squash * Math.exp(-tb * 10.1) * Math.cos(20 * tb);
      if (Math.abs(sqv) > Math.abs(sq)) {
        sq = sqv;
        sqAng = Math.atan2(h.y - geo.body.y, h.x - geo.body.x);
      }
    }
    // light
    if (tau < M.bloomDur) {
      const q = 1 - tau / M.bloomDur;
      const b = q * Math.sqrt(q) * 0.5;
      if (b > bloom) {
        bloom = b;
        bloomX = h.x;
        bloomY = h.y;
      }
    }
    if (tau < 0.6) {
      waveFrom = i;
      waveTau = tau;
      flashBulbs = Math.max(flashBulbs, h.kind === BURST ? 1 : 0.8);
    }
    if (h.kind === HOP && tau < M.ripple.dur) {
      // the pin's pale disc: grows fast, stays visible while it grows, then lets go
      const q = tau / M.ripple.dur;
      const u = easeOutCubic(q);
      rippleR = M.ripple.from + (M.ripple.to - M.ripple.from) * u;
      rippleA = 0.6 * (1 - q * q);
      rippleX = h.x;
      rippleY = h.y;
    }
    if (h.kind === POKE && tau < 0.4) shake = 4 * ring(tau, 0.12, 0.12);
    if (h.kind === BURST) {
      burstMix = clamp01(tau / 0.06);
      // U2: flash builds over the 90 ms hit-stop, then fades in 160 ms
      flash = tau < 0.09 ? 0.3 * (tau / 0.09) : 0.3 * (1 - clamp01((tau - 0.09) / 0.16));
      if (tau < M.ring.dur) {
        const u = easeOutCubic(tau / M.ring.dur);
        ringR = M.ring.from + (M.ring.to - M.ring.from) * u;
        ringA = 0.9 * (1 - u);
      }
      if (tau < 0.26) {
        const u = easeOutCubic(tau / 0.26);
        rippleR = 15 + 135 * u;
        rippleA = 0.5 * (1 - u);
        rippleX = geo.flank.x;
        rippleY = geo.flank.y;
      }
      stage = 1 + 0.06 * (spring(tau, 0.5, 0.6) - spring(tau - 0.12, 0.5, 0.6));
      hole = 0.35 * (1 - clamp01(tau / 0.6));
      sparks = tau;
      streamers = tau - 0.06;
    }
    // U3: damage builds
    const fa = fillAfter(story, i);
    if (fa > 0.3 && crack1 === 0) crack1 = clamp01(tau / 0.12);
    if (fa > 0.6 && crack2 === 0) crack2 = clamp01(tau / 0.12);
    if (fa > 0.75 && h.kind !== BURST) tremble = Math.max(tremble, clamp01(tau / 0.2));
  }
  if (rs.burstT >= 0) tremble = 0;
  if (drain >= 0) {
    const k = 1 - seg(drain, 0, 0.3);
    crack1 *= k;
    crack2 *= k;
    tremble *= k;
  }
  // ---- the way back (ours) / the drain
  let hoistY = 0;
  let backRoll = 0;
  let tailWhip = 0;
  let fade = 1;
  if (back >= 0) {
    fade = 1 - seg(back, 0, 0.3);
    if (back < B.hoist) hoistY = 12 * Math.sin((Math.PI * 0.5 * seg(back, B.dip, B.hoist)));
    else if (back < B.swap) hoistY = 12 - 572 * easeInCubic(seg(back, B.hoist, B.swap));
    else if (back < B.land) {
      const u = seg(back, B.swap, B.land);
      hoistY = -560 * (1 - u * u);
      backRoll = 6;
    } else {
      const s = back - B.land;
      const win = 1 - smooth(seg(back, B.end - 0.3, B.end));
      hoistY = 16 * ring(s, 0.16, 0.3) * win;
      backRoll = 6 * (1 - spring(s, 0.5, 0.55)) * win;
      tailWhip = 20 * ring(s - 0.04, 0.2, 0.36) * win;
    }
    if (swapped) {
      burstMix = 0;
      crack1 = 0;
      crack2 = 0;
      hole = 0;
    }
  }
  const tr = tremble * 1.2 * Math.sin(2 * Math.PI * 9 * t);

  const rollDeg = sway + swingBody + hopRoll + backRoll + tr;
  const swingX = swing * M.ribbonPerDeg;
  const ax = geo.attach.x + swingX;
  const ay = geo.attach.y + hopY + hoistY + dy;
  const bow = Math.max(0, -hopY) * (16 / M.hop.height);

  // ---- pill (U7: one shape, three states). The round shown is the one before the clear while
  // the way back / drain plays, otherwise the current one.
  const inReturn = (back >= 0 && back < B.end + 0.3) || (drain >= 0 && drain < M.drain.end + 0.4);
  const fresh = clear >= 0 && t >= clear ? roundInfo(hits, clear, t) : null;
  const useOld = inReturn && (fresh === null || fresh.first < 0);
  const info = useOld ? roundInfo(hits, -1, clear) : fresh !== null ? fresh : roundInfo(hits, -1, t);
  const s1 = info.first >= 0 ? spring(t - info.first, 0.22, 0.8) : 0;
  const s2 = info.burst >= 0 ? spring(t - info.burst, DONE_SPRING.response, DONE_SPRING.z) : 0;
  // "Come back tomorrow" appears only once the pill is at 98 % of its width, over 120 ms
  const doneText = info.burst >= 0 ? clamp01((t - info.burst - DONE_T98) / 0.12) : 0;
  // the pin swaps the pill in one frame: colour and text change in 80 ms, the width springs
  const c2 = info.burst >= 0 ? easeOutCubic(clamp01((t - info.burst) / 0.08)) : 0;
  const ret = useOld ? clear + (back >= 0 ? B.pill : M.drain.pill) : -1;
  const s3 = ret >= 0 ? spring(t - ret, 0.3, 0.78) : 0;
  const k1 = clamp01(s1);
  const k2 = c2;
  const k3 = clamp01(s3);
  let wL = 1 - k1;
  let wB = k1 * (1 - k2);
  let wD = k1 * k2;
  wL = wL * (1 - k3) + k3;
  wB *= 1 - k3;
  wD *= 1 - k3;
  // width from the unclamped springs (overshoot)
  const WL = 186;
  const WB = 120;
  const WD = 172.5;
  let pillW = WL + (WB - WL) * s1 + (WD - WB) * s2;
  pillW += (WL - (info.burst >= 0 ? WD : WB)) * s3;
  let fill = rs.fill;
  if (useOld) fill = back >= 0 ? 1 : Math.min(info.inc, 1) * (1 - easeOutCubic(seg(drain, 0, M.drain.dur)));
  const fillGlow = smooth(seg(fill, 0.74, 0.8));

  // ---- tokens and chip (U6)
  const tokens: Token[] = [];
  let count = 0;
  let chipBump = 0;
  const T = M.token;
  const arrive = T.pop + T.ride + T.fly;
  const chipReset = story.clearKind === CLEAR_BACK ? B.chip : M.drain.pill;
  for (let i = 0; i < hits.length; i++) {
    const h = hits[i];
    if (h.t > t) break;
    for (let j = 0; j < h.tokens; j++) {
      const tau = t - h.t - j * 0.12;
      if (tau < T.pop) continue;
      if (tau >= arrive) {
        const k = clamp01((tau - arrive) / 0.2);
        // the balance rolls back to 0 on the way back (the loop has to return)
        const reset = clear >= 0 && h.t < clear ? easeOutCubic(seg(t - clear, chipReset, chipReset + 0.3)) : 0;
        count += h.value * easeOutCubic(k) * (1 - reset);
        if (tau - arrive < 0.3) chipBump = Math.max(chipBump, Math.sin(Math.PI * clamp01((tau - arrive) / 0.3)));
        continue;
      }
      const bx = h.kind === BURST ? geo.flank.x + (j === 0 ? -14 : 18) : h.x - 12 * (hash(h.seed + j) - 0.5);
      const by = h.kind === BURST ? geo.flank.y - 20 : h.y + 6;
      const pop = tau - T.pop;
      const s = 0.6 + 0.4 * spring(pop, 0.25, 0.6);
      // rides with the body, rising a little
      const rx = bx + swingX * 0.6;
      const ry = by + (hopY + dy) * 0.9 - 26 * easeOutCubic(clamp01(pop / (T.ride + 0.1)));
      if (pop < T.ride) {
        tokens.push({ x: rx, y: ry, s, a: 1, tx: [], ty: [] });
        continue;
      }
      // arc to the chip, ease-in, with a five-sample trail
      const tx: number[] = [];
      const ty: number[] = [];
      let px = 0;
      let py = 0;
      let ps = 1;
      for (let q = 0; q < 5; q++) {
        const u = clamp01((pop - T.ride - q * 0.012) / T.fly);
        const e = u * u * (1.6 - 0.6 * u);
        const cx = rx + (geo.chipX - rx) * 0.15;
        const cy = Math.min(ry, geo.chipY) - 150;
        const ix = (1 - e) * (1 - e) * rx + 2 * (1 - e) * e * cx + e * e * geo.chipX;
        const iy = (1 - e) * (1 - e) * ry + 2 * (1 - e) * e * cy + e * e * geo.chipY;
        if (q === 0) {
          px = ix;
          py = iy;
          ps = 1 - 0.4 * e;
        } else {
          tx.push(ix);
          ty.push(iy);
        }
      }
      tokens.push({ x: px, y: py, s: s * ps, a: 1, tx, ty });
    }
  }
  // ---- loot, tiles, confetti (U4)
  const loot: Loot[] = [];
  const conf: Conf[] = [];
  let pieceIdx = 0;
  for (let i = 0; i < hits.length; i++) {
    const h = hits[i];
    if (h.t > t) break;
    const tau0 = t - h.t;
    const isBurst = h.kind === BURST;
    const tau = isBurst ? slowTau(tau0) : tau0;
    // vacuum time of this hit's loot: the clear after it
    const vac = clear >= 0 && h.t < clear && t >= clear ? t - clear : -1;
    const cfade = clear >= 0 && h.t < clear && t >= clear ? 1 - seg(t - clear, 0, 0.3) : 1;
    // direction from the body centre through the tap point, biased upward
    const base = Math.atan2(h.y - geo.body.y, h.x - geo.body.x);
    const n = h.loot.length;
    for (let j = 0; j < n; j++) {
      const sd = h.seed * 31 + j * 7.13;
      let ang = 0;
      let sp = 0;
      if (isBurst) {
        ang = (150 + 240 * ((j + hash(sd) * 0.8) / n)) * DEG;
        sp = 420 + 560 * hash(sd + 1);
      } else {
        const dirx = Math.cos(base);
        // bias 30° upward from the outward direction
        const up = -Math.PI / 2;
        let a0 = base + (up - base) * 0.35;
        if (Math.abs(h.x - geo.body.x) < 6 && Math.abs(h.y - geo.body.y) < 6) a0 = up;
        ang = a0 + (hash(sd) - 0.5) * 100 * DEG * (n > 1 ? 1 : 0.4);
        sp = 420 + 340 * hash(sd + 1);
        if (dirx === 0) ang = up;
      }
      const vx = Math.cos(ang) * sp;
      const vy = Math.sin(ang) * sp;
      const spin = (200 + 500 * hash(sd + 2)) * DEG * (hash(sd + 3) > 0.5 ? 1 : -1);
      const restRot = (hash(sd + 4) - 0.5) * 0.7;
      const floorY = 836 - 18 * hash(sd + 5) - (pieceIdx % 3) * 6;
      const k = h.loot[j];
      const before = loot.length;
      const ox0 = isBurst ? geo.flank.x + Math.cos(ang) * 16 : h.x;
      const oy0 = isBurst ? geo.flank.y + Math.sin(ang) * 16 : h.y;
      lootPiece(loot, k, ox0, oy0, vx, vy, spin, restRot, floorY, tau, 1);
      pieceIdx++;
      const p = loot[before];
      if (!p) continue;
      // the burst makes the settled pile jump (6 pt at +0.5 s)
      if (!isBurst && rs.burstT >= 0 && !swapped) {
        const pj = t - rs.burstT - 0.5;
        if (pj > 0 && pj < 0.3) p.y -= 6 * Math.sin((Math.PI * pj) / 0.3);
      }
      if (vac >= 0) {
        // nearest to the chip leaves first, 380 ms each
        const d = Math.hypot(p.x - geo.chipX, p.y - geo.chipY);
        const delay = clamp01((d - 480) / 420) * M.back.vacuumSpread;
        const u = clamp01((vac - delay) / M.back.vacuumDur);
        if (u >= 1) {
          loot.pop();
          continue;
        }
        const e = u * u;
        const cx = p.x + (geo.chipX - p.x) * 0.2;
        const cy = p.y - 240;
        p.x = (1 - e) * (1 - e) * p.x + 2 * (1 - e) * e * cx + e * e * geo.chipX;
        p.y = (1 - e) * (1 - e) * p.y + 2 * (1 - e) * e * cy + e * e * geo.chipY;
        p.s = 1 - 0.7 * e;
        p.a = 1 - smooth(seg(u, 0.75, 1));
        p.rot += e * 3;
      }
    }
    // paper tiles (burst) and crack tiles (U3)
    const nt = h.tiles + (fillAfter(story, i) > 0.3 && fillAfter(story, i) - h.inc <= 0.3 ? 4 : 0) + (fillAfter(story, i) > 0.6 && fillAfter(story, i) - h.inc <= 0.6 ? 4 : 0);
    for (let j = 0; j < nt; j++) {
      const sd = h.seed * 17 + j * 3.31 + 100;
      const ang = isBurst ? (160 + 220 * hash(sd)) * DEG : (200 + 140 * hash(sd)) * DEG;
      const sp = isBurst ? 300 + 500 * hash(sd + 1) : 120 + 160 * hash(sd + 1);
      const ox = isBurst ? geo.flank.x : geo.flank.x + (hash(sd + 6) - 0.5) * 50;
      const oy = isBurst ? geo.flank.y : geo.flank.y + (hash(sd + 7) - 0.5) * 40;
      const k = K_TILE + Math.floor(hash(sd + 2) * 3);
      const tt = isBurst ? tau : tau0;
      const x = ox + Math.cos(ang) * sp * Math.min(tt, 0.5) * (1 - Math.min(tt, 0.5)) * 1.4 + 18 * Math.sin(tt * 5 + sd);
      const y = oy + Math.sin(ang) * sp * Math.min(tt, 0.5) + 0.5 * 700 * tt * tt;
      if (y > 900) continue;
      const s = (10 + 8 * hash(sd + 3)) / KIND_W[K_TILE];
      const spin = (3 + 6 * hash(sd + 4)) * (hash(sd + 5) > 0.5 ? 1 : -1);
      loot.push({ k, x, y, rot: spin * tt, s, a: cfade * (1 - seg(y, 820, 880)), sx: Math.cos(spin * tt * 0.7) });
    }
    // confetti
    const nc = h.conf;
    for (let j = 0; j < nc; j++) {
      const sd = h.seed * 13 + j * 1.77 + 50;
      let ang = 0;
      let sp = 0;
      if (isBurst) {
        ang = (150 + 240 * hash(sd)) * DEG;
        sp = 420 + 560 * hash(sd + 1);
      } else {
        ang = (-90 + (hash(sd) - 0.5) * 140) * DEG;
        sp = 160 + 240 * hash(sd + 1);
      }
      const c = Math.floor(hash(sd + 2) * 6);
      confPiece(conf, isBurst ? geo.flank.x : h.x, isBurst ? geo.flank.y : h.y, Math.cos(ang) * sp, Math.sin(ang) * sp, isBurst ? tau : tau0, sd, c, cfade);
    }
  }
  if (back >= 0 && swapped) {
    // nothing of the old round remains on the stage but what is being vacuumed
  }
  const lift = clamp01(-(hopY + hoistY) / 140);

  // bulbs (U5)
  let flicker = 1;
  if (tremble > 0) flicker = 1 - 0.18 * tremble * (0.5 + 0.5 * Math.sin(2 * Math.PI * 6 * t));
  let bulbBoost = 1;
  if (rs.burstT >= 0 && !swapped) {
    const bt = t - rs.burstT;
    if (bt < 0.36) bulbBoost = Math.floor(bt / 0.06) % 2 === 0 ? 1.8 : 0.9;
    else bulbBoost = 1 + 0.2 * (1 - seg(bt, 0.36, 1.5));
  }

  return {
    ax,
    ay,
    roll: rollDeg * DEG,
    yaw: (yaw + hopYaw) * DEG,
    sq,
    sqAng,
    press: 0,
    tail: (swingTail - swingBody + (sway - swayLag) * 2 + tailWhip) * DEG,
    bow,
    burst: burstMix,
    crack1,
    crack2,
    bloomX,
    bloomY,
    bloom: bloom * (1 + 0.15 * ((crack1 > 0 ? 1 : 0) + (crack2 > 0 ? 1 : 0))),
    hole,
    bulbBoost,
    waveFrom: waveFrom >= 0 ? nearestBulb(geo, hits[waveFrom].x, hits[waveFrom].y) : -1,
    waveTau: waveFrom >= 0 ? waveTau * (flashBulbs > 0 ? 1 : 0) : 0,
    flicker,
    rippleX,
    rippleY,
    rippleR,
    rippleA,
    ringX: geo.flank.x,
    ringY: geo.flank.y,
    ringR,
    ringA,
    flash,
    stage,
    stageX: geo.flank.x,
    stageY: geo.flank.y,
    pillW,
    wL,
    wB,
    wD,
    fill: Math.max(0, fill),
    fillGlow,
    shake,
    sparks,
    doneText,
    count,
    chipBump,
    tokens,
    loot,
    conf,
    streamers: back >= B.swap ? -1 : streamers,
    fade,
    shadowLift: lift,
    swayT,
  };
}


/** Brightness of bulb i (1 = as painted): twinkle, the hit's wave along the wire, the burst strobe. */
export function bulbLevel(i: number, f: Frame) {
  'worklet';
  const k = 2 + Math.floor(hash(i + 0.5) * 4);
  const tw = 0.85 + 0.15 * Math.sin((2 * Math.PI * k * f.swayT) / LOOP + hash(i + 9) * 6.28);
  let wave = 0;
  if (f.waveFrom >= 0) {
    const d = Math.abs(i - f.waveFrom) * 0.04;
    const s = f.waveTau - d;
    if (s >= 0) wave = 0.8 * Math.exp(-s / 0.18);
  }
  return (tw + wave) * f.bulbBoost * f.flicker;
}

// ---------------------------------------------------------------- the driver (autoPlay ↔ finger)
export type Driver = {
  mode: number; // 0 auto, 1 live
  loopT: number;
  t: number; // live story time
  story: Story;
  last: number; // live time of the last touch
  // drag (live): attach-point offset and pendulum
  drag: number;
  fx: number;
  fy: number;
  gx: number; // grab offset
  gy: number;
  ox: number;
  oy: number;
  vx: number;
  vy: number;
  th: number; // pendulum angle (rad) after release
  om: number;
  press: number;
  pressAim: number;
  // a soft hand-over when returning to autoPlay
  blendT: number;
  blendRoll: number;
  blendX: number;
  blendY: number;
  blendPending: number;
  // the last drawn attach point and roll (hit-testing, the hand-over)
  lastAx: number;
  lastAy: number;
  lastRoll: number;
};

export function makeDriver(): Driver {
  return {
    mode: 0,
    loopT: 0,
    t: 0,
    story: { hits: [], clear: -1, clearKind: 0 },
    last: 0,
    drag: 0,
    fx: 0,
    fy: 0,
    gx: 0,
    gy: 0,
    ox: 0,
    oy: 0,
    vx: 0,
    vy: 0,
    th: 0,
    om: 0,
    press: 0,
    pressAim: 0,
    blendT: -1,
    blendRoll: 0,
    blendX: 0,
    blendY: 0,
    blendPending: 0,
    lastAx: 0,
    lastAy: 0,
    lastRoll: 0,
  };
}

/** A touch takes over from autoPlay: copy the part of the script that already happened. */
export function takeOver(d: Driver) {
  'worklet';
  if (d.mode === 1) return;
  const t0 = d.loopT;
  d.mode = 1;
  d.t = t0;
  const hits: Hit[] = [];
  if (t0 < SCRIPT_BACK + M.back.end) {
    for (let i = 0; i < SCRIPT.length; i++) if (SCRIPT[i].t <= t0) hits.push(SCRIPT[i]);
  }
  d.story = { hits, clear: t0 >= SCRIPT_BACK && t0 < SCRIPT_BACK + M.back.end ? SCRIPT_BACK : -1, clearKind: CLEAR_BACK };
  d.last = d.t;
}

function storyBusy(d: Driver) {
  'worklet';
  const s = d.story;
  if (s.clear < 0) return false;
  const span = s.clearKind === CLEAR_BACK ? M.back.pill + 0.3 : M.drain.pill;
  return d.t >= s.clear && d.t < s.clear + span;
}

/** A tap at (x, y) in reference points. Returns the haptic: 0 none, 1 light, 2 medium, 3 burst, 4 poke. */
export function tapAt(d: Driver, x: number, y: number, geo: Geo): number {
  'worklet';
  takeOver(d);
  d.last = d.t;
  if (storyBusy(d)) return 0;
  const s = d.story;
  const hits = s.hits;
  const lastHit = hits.length > 0 ? hits[hits.length - 1] : null;
  if (lastHit && d.t - lastHit.t < M.merge) return 0;
  // fill of the current round
  let fill = 0;
  let burst = false;
  let lastRound = -1;
  for (let i = 0; i < hits.length; i++) {
    const h = hits[i];
    if (s.clear >= 0 && h.t < s.clear) continue;
    if (h.kind === POKE) continue;
    fill += h.inc;
    if (h.kind === BURST) burst = true;
    lastRound = h.t;
  }
  const seed = 20 + hits.length * 1.37 + Math.floor(d.t * 10);
  const side = x < geo.body.x ? 1 : -1;
  const amp = side * (4 + 3 * Math.min(1, fill + 0.1));
  const newHits = hits.slice();
  if (burst) {
    newHits.push(mk(d.t, x, y, POKE, side * 4, 0, seed, [], 2, 0, 0, 0));
    d.story = { hits: newHits, clear: s.clear, clearKind: s.clearKind };
    return 4;
  }
  const inc = M.liveInc;
  const willBurst = fill + inc >= 0.999;
  const rest = lastRound < 0 || d.t - lastRound > M.hopGap;
  const lootPool = [K_GREEN, K_MAGENTA, K_LOLLY, K_MINT, K_STAR, K_BONBON, K_GUMDROP];
  if (willBurst) {
    newHits.push(mk(d.t, geo.flank.x, geo.flank.y, BURST, -10, inc, seed, burstLoot(), 40, 18, 2, 20));
  } else if (rest && fill === 0) {
    newHits.push(mk(d.t, x, y, HOP, 0, inc, seed, [], 3, 0, 1, 10));
  } else {
    const n = 2 + Math.floor(hash(seed) * 2.2);
    const loot: number[] = [];
    for (let j = 0; j < n; j++) loot.push(lootPool[Math.floor(hash(seed + j * 3.1) * lootPool.length)]);
    newHits.push(mk(d.t, x, y, JOLT, amp, inc, seed, loot, 4 + Math.floor(hash(seed + 1) * 3), 0, 1, 10));
  }
  d.story = { hits: newHits, clear: s.clear, clearKind: s.clearKind };
  if (willBurst) return 3;
  return fill + inc >= 0.66 ? 2 : 1;
}

const PEND_W = (2 * Math.PI) / 1.7;
const RIBBON = 371.5 + STAGE_DY; // from the anchor above the screen to the attach point

/** The finger holds the piñata on its ribbon (spring), released it swings as a pendulum. */
function stepDrag(d: Driver, dt: number) {
  'worklet';
  const steps = Math.max(1, Math.ceil(dt / 0.004));
  const h = dt / steps;
  for (let i = 0; i < steps; i++) {
    if (d.drag === 1) {
      // target offset = finger + grab offset, kept within the ribbon's length (+12 % rubber band)
      let tx = d.fx + d.gx;
      let ty = d.fy + d.gy;
      const dx = tx;
      const dy = ty + RIBBON;
      const r = Math.hypot(dx, dy);
      if (r > RIBBON) {
        const over = r - RIBBON;
        const rr = RIBBON + (over * 0.12) / (1 + over / 120);
        tx = dx * (rr / r);
        ty = dy * (rr / r) - RIBBON;
      }
      const ax = -320 * (d.ox - tx) - 26 * d.vx;
      const ay = -320 * (d.oy - ty) - 26 * d.vy;
      d.vx += ax * h;
      d.vy += ay * h;
      d.ox += d.vx * h;
      d.oy += d.vy * h;
    } else if (Math.abs(d.ox) + Math.abs(d.oy) + Math.abs(d.vx) + Math.abs(d.vy) + Math.abs(d.th) + Math.abs(d.om) > 0.001) {
      // pendulum about the anchor: angle from the attach offset, radial spring back to length
      const px = d.ox;
      const py = d.oy + RIBBON;
      const r = Math.hypot(px, py);
      let th = Math.atan2(px, py);
      const om = (d.vx * Math.cos(th) - d.vy * Math.sin(th)) / Math.max(r, 1);
      let vr = d.vx * Math.sin(th) + d.vy * Math.cos(th);
      const acc = -PEND_W * PEND_W * Math.sin(th) - 2 * 0.9 * om;
      const om2 = om + acc * h;
      th += om2 * h;
      let rr = r + vr * h;
      vr += (-300 * (rr - RIBBON) - 30 * vr) * h;
      rr = Math.max(0, rr);
      d.ox = rr * Math.sin(th);
      d.oy = rr * Math.cos(th) - RIBBON;
      d.vx = om2 * rr * Math.cos(th) + vr * Math.sin(th);
      d.vy = -om2 * rr * Math.sin(th) + vr * Math.cos(th);
      d.th = th;
      d.om = om2;
      if (Math.abs(d.ox) + Math.abs(d.oy) < 0.02 && Math.abs(d.vx) + Math.abs(d.vy) < 0.05) {
        d.ox = 0;
        d.oy = 0;
        d.vx = 0;
        d.vy = 0;
        d.th = 0;
        d.om = 0;
      }
    }
  }
}

/** Advance the driver by dt; returns the story time to draw. */
export function stepDriver(d: Driver, dt: number, auto: boolean) {
  'worklet';
  // press-in (0.97) eases toward its aim
  d.press += (d.pressAim - d.press) * (1 - Math.exp(-dt * 30));
  if (d.mode === 0) {
    if (auto) d.loopT = (d.loopT + dt) % LOOP;
    if (d.blendT >= 0) {
      d.blendT += dt;
      if (d.blendT > 0.5) d.blendT = -1;
    }
    stepDrag(d, dt);
    return d.loopT;
  }
  d.t += dt;
  stepDrag(d, dt);
  const s = d.story;
  const idle = d.t - d.last;
  if (d.drag === 0 && idle > M.idleReturn) {
    // 2 s after the last touch: the way back (after a burst) or the drain
    let burst = false;
    let any = false;
    for (let i = 0; i < s.hits.length; i++) {
      const h = s.hits[i];
      if (s.clear >= 0 && h.t < s.clear) continue;
      any = true;
      if (h.kind === BURST) burst = true;
    }
    const cleared = s.clear >= 0 && s.clear >= d.last;
    if (!cleared && (burst || any)) {
      const kept: Hit[] = [];
      for (let i = 0; i < s.hits.length; i++) if (s.clear < 0 || s.hits[i].t >= s.clear) kept.push(s.hits[i]);
      d.story = { hits: kept, clear: d.t, clearKind: burst ? CLEAR_BACK : CLEAR_DRAIN };
    } else {
      const end = s.clear >= 0 ? s.clear + (s.clearKind === CLEAR_BACK ? M.back.end : M.drain.end) : d.last + M.idleReturn;
      const settled = Math.abs(d.ox) + Math.abs(d.oy) + Math.abs(d.th) < 1.5;
      if (d.t >= end && settled && auto) {
        d.mode = 0;
        d.loopT = SCRIPT_BACK + M.back.end;
        d.blendT = 0;
        d.blendPending = 1;
      }
    }
  }
  return d.t;
}

/** The finger's effect on the frame: drag offset, tilt with velocity, press-in, hand-over blend. */
export function applyDriver(f: Frame, d: Driver) {
  'worklet';
  f.ax += d.ox;
  f.ay += d.oy;
  // the body leans against its motion (it trails the ribbon)
  f.roll += Math.atan2(d.ox, d.oy + RIBBON) * 0.6 - Math.max(-0.35, Math.min(0.35, d.vx * 0.0006));
  f.tail += -Math.max(-0.5, Math.min(0.5, d.vx * 0.0012));
  f.press = d.press;
  if (d.blendT >= 0) {
    const k = 1 - smooth(d.blendT / 0.5);
    f.roll += d.blendRoll * k;
    f.ax += d.blendX * k;
    f.ay += d.blendY * k;
  }
}
