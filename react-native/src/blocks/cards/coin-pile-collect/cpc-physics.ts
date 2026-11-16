// Coin Pile Collect — the pile. A small position-based solver for discs in the tile's plane with a depth ("layer")
// per coin, a seeded settle that makes the resting heap for ANY tile size and coin count, and the kinematic rain /
// return that puts every coin back on exactly that heap.
//
// Everything here is a pure function of its arguments and plain number arrays (no closures, no allocation per
// step), so the same code runs on the JS thread (settle at mount, offline review in node) and in a UI worklet
// (the live pile). Determinism: integer hashes only (no Math.random, no sin-hash), fixed time step.
//
// Coordinates: tile-local pt, x right, y down, z toward the viewer. Gravity is screen-down.

export const STRIDE = 32;
export const X = 0; // centre
export const Y = 1;
export const PX = 2; // previous centre (verlet) — while draining: the start of the slide
export const PY = 3;
export const Z = 4; // height above the tile (centre)
export const AX = 5; // axis tilt: the coin's face normal is (AX, AY, sqrt(1 - AX² - AY²))
export const AY = 6;
export const PHI = 7; // spin of the emblem in the face plane
export const GLOW = 8; // 0..1 emission
export const ALIVE = 9; // 0 gone, 1 present; 0..1 fractional alpha while fading (reduced motion)
export const DRAIN = 10; // 0 not draining, else 0..1 progress of the slide into the mouth
export const VAL = 11; // cents
export const FACE = 12; // 0 emblem face up, 1 mesh face up
export const RC = 13; // collision radius
export const SLEEP = 14; // steps at rest
export const AUX = 15; // last-coin spin timer
export const SX = 16; // slide start
export const SY = 17;
export const DUR = 18; // slide duration (s)
export const Z0 = 19; // z when the slide started
export const RANK = 20; // stacking order: a higher rank lies on a lower one where they overlap
export const ZT = 21; // z the coin is easing to
export const ZB = 22; // preferred height (its layer)
export const CNT = 23; // 1 while the coin counts in the tile's amount (landed, not collected)
export const R0X = 24; // start pose of a return (rain or glide to rest); in the live pile: seconds a finger's push still counts for a flick
export const R0Y = 25;
export const R0Z = 26;
export const R0AX = 27;
export const R0AY = 28;
export const R0PHI = 29;
export const RT0 = 30; // return: delay before this coin starts, s
export const RKIND = 31; // return: 0 glide from where it lies, 1 fall from above

export const NMAX = 32;
export const FLICK_SPEED = 300; // pt/s: a flick faster than this into the mouth collects the coin
export const FLICK_REACH = 30; // pt from the mouth
export const PUSH_MEMORY = 0.5; // s a coin the finger pushed can still be flicked into the mouth

export type Cfg = {
  n: number;
  w: number; // tile width
  h: number; // tile height
  r: number; // face radius
  t: number; // half thickness
  mx: number; // the pill's mouth
  my: number;
  reach: number; // flow reach
  keepW: number; // keep-out over the copy
  keepH: number;
  gravity: number;
  layers: number; // depth layers
  layerGap: number;
  overlap: number; // cross-layer centre distance factor
  rcScale: number; // collision radius as a share of the face radius
  seed: number;
  order: number[]; // coin indices by rank, lowest first
  keepOn: number; // 1 while a heap is being settled: the copy's keep-out shelf is part of the composition, not of the live pile
  heap: number[]; // the resting heap, HS numbers per coin: x, y, z, ax, ay, phi, face, rank, preferred height
};
export const HS = 9;

// event slots written by stepSim
export const EV_COLLECTED = 0; // coins that went through the mouth in this step
export const EV_LAST = 1; // index of the last one
export const EV_IMPACT = 2; // strongest contact speed in this step, pt/s
export const EV_CREDIT = 3; // emission credit carried between steps
export const EV_ALIVE = 4; // coins still in the tile (not draining)
export const EV_FLOW = 5; // 0..1 how much of the flow is on now (smoothed)
export const EV_LASTSPIN = 6; // > 0 while the last coin spins on its rim at the mouth
export const EV_CENTS = 7; // cents that went through the mouth in this step
export const EV_FLICK = 8; // coins collected by a flick in this step
export const EV_SIZE = 12;

// input slots read by stepSim
export const IN_RATE = 0; // coins per second through the valve
export const IN_FINGER = 1; // 1 while a finger is in the pile
export const IN_FX = 2;
export const IN_FY = 3;
export const IN_GX = 4; // sideways gravity from tilt, pt/s²
export const IN_FR = 5; // finger collider radius
export const IN_FLICKON = 6; // 1 while a finger is in the pile or has just left it: a fast coin at the mouth is collected
export const IN_SIZE = 8;

export function hash32(n: number) {
  'worklet';
  let h = n | 0;
  h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
  h = Math.imul(h ^ (h >>> 16), 0x45d9f3b);
  h = h ^ (h >>> 16);
  return (h >>> 0) / 4294967296;
}
export function rnd(seed: number, i: number, k: number) {
  'worklet';
  return hash32(Math.imul(seed | 0, 0x9e3779b1) + Math.imul(i + 1, 0x85ebca6b) + Math.imul(k + 1, 0xc2b2ae35));
}
export function clamp(v: number, a: number, b: number) {
  'worklet';
  return v < a ? a : v > b ? b : v;
}
export function smooth(a: number, b: number, v: number) {
  'worklet';
  const k = clamp((v - a) / (b - a), 0, 1);
  return k * k * (3 - 2 * k);
}

export function makeCfg(
  w: number,
  h: number,
  n: number,
  r: number,
  t: number,
  mx: number,
  my: number,
  keepW: number,
  keepH: number,
  seed: number,
): Cfg {
  return {
    n,
    w,
    h,
    r,
    t,
    mx,
    my,
    reach: 150,
    keepW,
    keepH,
    gravity: 2600,
    layers: 3,
    layerGap: 2.2 * t,
    overlap: 0.45,
    rcScale: 0.7,
    seed,
    order: [],
    keepOn: 0,
    heap: [],
  };
}

export function makeState(n: number) {
  return new Array<number>(NMAX * STRIDE).fill(0);
}

/** value of each coin in cents, seeded, summing to exactly total (largest remainder) */
export function coinValues(n: number, totalCents: number, seed: number, out: number[]) {
  'worklet';
  let sum = 0;
  for (let i = 0; i < n; i++) {
    const wgt = 0.55 + rnd(seed, i, 91) * 0.9;
    out[i] = wgt;
    sum += wgt;
  }
  let acc = 0;
  for (let i = 0; i < n; i++) {
    const c = Math.floor((out[i] / sum) * totalCents);
    out[i] = c;
    acc += c;
  }
  let rest = totalCents - acc;
  let i = 0;
  while (rest > 0) {
    out[i % n] += 1;
    rest--;
    i++;
  }
}

/** face normal of coin i from its pose (units, az >= 0) */
export function faceAz(ax: number, ay: number) {
  'worklet';
  return Math.sqrt(Math.max(1 - ax * ax - ay * ay, 0.0004));
}

/** collision radius of a coin from its tilt: a tilted coin is narrower in one direction */
export function radiusOf(cfg: Cfg, ax: number, ay: number) {
  'worklet';
  const az = Math.sqrt(Math.max(1 - ax * ax - ay * ay, 0.0004));
  return cfg.r * cfg.rcScale * (0.8 + 0.2 * az);
}

/** the seeded start of coin i of a settle: above the tile, biased to the right wall (the pin rains from the top right) */
export function startPose(cfg: Cfg, S: number[], i: number) {
  'worklet';
  const o = i * STRIDE;
  const u = rnd(cfg.seed, i, 1);
  const x = cfg.r + (cfg.w - 2 * cfg.r) * (1 - Math.pow(1 - u, 1.5));
  S[o + X] = x;
  S[o + Y] = -cfg.r - i * 46;
  S[o + PX] = S[o + X];
  S[o + PY] = S[o + Y];
}

/** tilt of coin i: most lie almost flat, a few lean hard (their rim shows) */
export function tiltOf(seed: number, i: number, out: number[]) {
  'worklet';
  const a = rnd(seed, i, 11) * 6.2831853;
  const k = rnd(seed, i, 12);
  const lean = rnd(seed, i, 13) < 0.16;
  const m = lean ? 0.62 + 0.3 * rnd(seed, i, 14) : 0.06 + 0.4 * k * k;
  out[0] = Math.cos(a) * m;
  out[1] = Math.sin(a) * m;
}

/** the 15 sample points of a coin's footprint used by resolveZ: radius share and its cos / sin, computed once */
const SAMPLE_R: number[] = [0];
const SAMPLE_C: number[] = [1];
const SAMPLE_S: number[] = [0];
for (let q = 1; q < 15; q++) {
  const ang = q < 7 ? (q - 1) * 1.0471976 : (q - 7) * 0.7853982;
  SAMPLE_R.push(q < 7 ? 0.55 : 0.95);
  SAMPLE_C.push(Math.cos(ang));
  SAMPLE_S.push(Math.sin(ang));
}

/**
 * Stacking: every coin rests on the ones below it. Where a coin overlaps a lower-ranked coin the height is the
 * smallest that keeps its underside above the other's top face at 15 sample points of its footprint (the faces are
 * tilted planes), so leaning and stacked coins touch instead of cutting through each other.
 */
export function resolveZ(cfg: Cfg, S: number[], dt: number) {
  'worklet';
  const n = cfg.n;
  const R = cfg.r;
  const Hh = cfg.t;
  for (let k = 0; k < n; k++) {
    const i = cfg.order[k];
    const oi = i * STRIDE;
    if (S[oi + ALIVE] < 0.5 || S[oi + DRAIN] > 0) continue;
    const ax = S[oi + AX];
    const ay = S[oi + AY];
    const azi = faceAz(ax, ay);
    const sinT = Math.sqrt(ax * ax + ay * ay);
    // in-plane basis of coin i
    let rx = 0;
    let ry = 1;
    let rz = 0;
    if (Math.abs(ay) > 0.9) {
      rx = 1;
      ry = 0;
    }
    // u0 = normalize(cross(ref, a)), v0 = cross(a, u0)
    let ux = ry * azi - rz * ay;
    let uy = rz * ax - rx * azi;
    let uz = rx * ay - ry * ax;
    const ul = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-9;
    ux /= ul;
    uy /= ul;
    uz /= ul;
    const vx = ay * uz - azi * uy;
    const vy = azi * ux - ax * uz;
    const vz = ax * uy - ay * ux;
    let need = Hh * azi + R * sinT + 0.05;
    if (S[oi + ZB] > need) need = S[oi + ZB];
    for (let m = 0; m < k; m++) {
      const j = cfg.order[m];
      const oj = j * STRIDE;
      if (S[oj + ALIVE] < 0.5 || S[oj + DRAIN] > 0) continue;
      const cdx = S[oi + X] - S[oj + X];
      const cdy = S[oi + Y] - S[oj + Y];
      if (cdx * cdx + cdy * cdy > 4 * (R + Hh) * (R + Hh)) continue;
      const jx = S[oj + AX];
      const jy = S[oj + AY];
      const azj = faceAz(jx, jy);
      const zj = S[oj + ZT];
      for (let q = 0; q < 15; q++) {
        const r = SAMPLE_R[q] * R;
        const ca = SAMPLE_C[q];
        const sa = SAMPLE_S[q];
        const ox = r * (ca * ux + sa * vx);
        const oy = r * (ca * uy + sa * vy);
        const oz = r * (ca * uz + sa * vz);
        const dx = cdx + ox;
        const dy = cdy + oy;
        // top face of j under this point, and is the point inside j's disc
        const top = (Hh - jx * dx - jy * dy) / azj;
        const rad2 = dx * dx + dy * dy + top * top - Hh * Hh;
        if (rad2 > R * R) continue;
        const z = zj + top - oz + azi * Hh + 0.12;
        if (z > need) need = z;
      }
    }
    S[oi + ZT] = need;
  }
  const kz = dt <= 0 ? 1 : Math.min(dt / 0.05, 1);
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    if (S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0) continue;
    S[o + Z] += (S[o + ZT] - S[o + Z]) * kz;
  }
}

/** one solver step; positions are corrected, never integrated through anything */
export function stepSim(cfg: Cfg, S: number[], dt: number, inp: number[], ev: number[]) {
  'worklet';
  const n = cfg.n;
  const w = cfg.w;
  const h = cfg.h;
  const g = cfg.gravity;
  const rate = inp[IN_RATE];
  ev[EV_COLLECTED] = 0;
  ev[EV_IMPACT] = 0;
  ev[EV_CENTS] = 0;
  ev[EV_FLICK] = 0;
  // the flow's on-ness, smoothed (attack 250 ms, release 120 ms)
  const flowTarget = rate > 0.01 ? 1 : 0;
  const fk = flowTarget > ev[EV_FLOW] ? dt / 0.25 : dt / 0.12;
  ev[EV_FLOW] = clamp(ev[EV_FLOW] + (flowTarget - ev[EV_FLOW]) * Math.min(fk, 1), 0, 1);

  // emission: the valve lets one coin go every 1/rate seconds, the nearest to the mouth first
  if (rate > 0.01) {
    ev[EV_CREDIT] += rate * dt;
    while (ev[EV_CREDIT] >= 1) {
      let best = -1;
      let bd = 1e12;
      for (let i = 0; i < n; i++) {
        const o = i * STRIDE;
        if (S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0 || S[o + CNT] < 0.5) continue;
        const dx = cfg.mx - S[o + X];
        const dy = cfg.my - S[o + Y];
        // a coin higher up costs a little more than one beside the mouth, so the river follows the heap's slope
        const d2 = dx * dx + dy * dy * 1.15;
        if (d2 < bd) {
          bd = d2;
          best = i;
        }
      }
      if (best < 0) {
        ev[EV_CREDIT] = 1;
        break;
      }
      const o = best * STRIDE;
      S[o + DRAIN] = 0.0001;
      S[o + SX] = S[o + X];
      S[o + SY] = S[o + Y];
      S[o + Z0] = S[o + Z];
      S[o + R0AX] = S[o + AX];
      S[o + R0AY] = S[o + AY];
      S[o + DUR] = clamp(Math.sqrt(bd) / 360, 0.13, 0.36);
      ev[EV_CREDIT] -= 1;
    }
  } else {
    ev[EV_CREDIT] = Math.min(ev[EV_CREDIT], 0.999);
  }

  // count what is left (not draining)
  let alive = 0;
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    if (S[o + ALIVE] > 0.5 && S[o + DRAIN] === 0 && S[o + CNT] > 0.5) alive++;
  }
  ev[EV_ALIVE] = alive;

  // integrate
  const damp = 0.9985;
  const funnel = 900 * clamp(rate / 14, 0, 1.4);
  // a heap at rest sleeps: no gravity, no creep. Anything that moves it (the valve, a finger, tilt) wakes it all.
  const awakeAll = rate > 0.01 || inp[IN_FINGER] > 0.5 || inp[IN_GX] !== 0;
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    if (S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0) continue;
    if (awakeAll) S[o + SLEEP] = 0;
    if (S[o + SLEEP] >= 24) {
      S[o + PX] = S[o + X];
      S[o + PY] = S[o + Y];
      continue;
    }
    const x = S[o + X];
    const y = S[o + Y];
    let vx = x - S[o + PX];
    let vy = y - S[o + PY];
    // a coin in the heap is held by its neighbours' friction: slow ones lose speed quickly, falling ones hardly any
    const spd = Math.sqrt(vx * vx + vy * vy) / dt;
    const dk = damp - 0.045 * (1 - smooth(25, 140, spd));
    vx *= dk;
    vy *= dk;
    if (S[o + R0X] > 0) S[o + R0X] = Math.max(S[o + R0X] - dt, 0);
    let ax = inp[IN_GX];
    let ay = g;
    if (rate > 0.01) {
      const dx = cfg.mx - x;
      const dy = cfg.my - y;
      const d = Math.sqrt(dx * dx + dy * dy) + 0.001;
      if (d < cfg.reach) {
        const k = (1 - d / cfg.reach) * funnel;
        ax += (dx / d) * k;
        ay += (dy / d) * k * 0.6 - g * 0.25 * (1 - d / cfg.reach);
      }
    }
    vx += ax * dt * dt;
    vy += ay * dt * dt;
    S[o + PX] = x;
    S[o + PY] = y;
    S[o + X] = x + vx;
    S[o + Y] = y + vy;
    // a coin flicked into the mouth faster than 300 pt/s is collected even with the valve closed
    if (rate <= 0.01 && inp[IN_FLICKON] > 0.5 && S[o + R0X] > 0 && S[o + CNT] > 0.5) {
      const sp = Math.sqrt(vx * vx + vy * vy) / dt;
      const mdx = cfg.mx - (x + vx);
      const mdy = cfg.my - (y + vy);
      if (sp > FLICK_SPEED && mdx * mdx + mdy * mdy < FLICK_REACH * FLICK_REACH && mdx * vx + mdy * vy > 0) {
        S[o + DRAIN] = 0.0001;
        S[o + SX] = S[o + X];
        S[o + SY] = S[o + Y];
        S[o + Z0] = S[o + Z];
        S[o + R0AX] = S[o + AX];
        S[o + R0AY] = S[o + AY];
        S[o + DUR] = 0.14;
        ev[EV_FLICK] += 1;
      }
    }
  }

  // draining coins slide into the mouth along a straight eased path, lifted over the heap
  let zTop = 0;
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    if (S[o + ALIVE] > 0.5 && S[o + DRAIN] === 0 && S[o + Z] > zTop) zTop = S[o + Z];
  }
  zTop += cfg.t * 3;
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    if (S[o + ALIVE] < 0.5 || S[o + DRAIN] === 0) continue;
    const p = Math.min(S[o + DRAIN] + dt / S[o + DUR], 1);
    S[o + DRAIN] = p;
    const e = p * p * (3 - 2 * p) * 0.35 + p * p * p * 0.65;
    S[o + X] = S[o + SX] + (cfg.mx - S[o + SX]) * e;
    S[o + Y] = S[o + SY] + (cfg.my - S[o + SY]) * e;
    S[o + PX] = S[o + X];
    S[o + PY] = S[o + Y];
    S[o + Z] = S[o + Z0] + (zTop - S[o + Z0]) * smooth(0, 0.35, p);
    S[o + GLOW] = 1;
    // it tips onto its edge as it goes into the slot: the face turns to look across the way it travels
    const ddx = cfg.mx - S[o + SX];
    const ddy = cfg.my - S[o + SY];
    const dl = Math.sqrt(ddx * ddx + ddy * ddy) + 0.001;
    const tip = smooth(0.2, 0.85, p);
    S[o + AX] = S[o + R0AX] + (-(ddy / dl) * 0.94 - S[o + R0AX]) * tip;
    S[o + AY] = S[o + R0AY] + ((ddx / dl) * 0.94 - S[o + R0AY]) * tip;
    if (p >= 1) {
      if (alive === 0 && S[o + AUX] === 0) {
        // the last coin stands on its rim and spins for 300 ms before it drops
        S[o + AUX] = 0.3;
        ev[EV_LASTSPIN] = 0.3;
      }
      if (S[o + AUX] > 0) {
        S[o + AUX] = Math.max(S[o + AUX] - dt, 0.0001);
        ev[EV_LASTSPIN] = S[o + AUX];
        if (S[o + AUX] > 0.0002) continue;
      }
      S[o + ALIVE] = 0;
      S[o + CNT] = 0;
      S[o + DRAIN] = 0;
      S[o + AUX] = 0;
      ev[EV_COLLECTED] += 1;
      ev[EV_CENTS] += S[o + VAL];
      ev[EV_LAST] = i;
    }
  }

  // constraints
  const fOn = inp[IN_FINGER];
  const fr = inp[IN_FR];
  for (let it = 0; it < 4; it++) {
    for (let i = 0; i < n; i++) {
      const oi = i * STRIDE;
      if (S[oi + ALIVE] < 0.5) continue;
      const drainI = S[oi + DRAIN] > 0;
      for (let j = i + 1; j < n; j++) {
        const oj = j * STRIDE;
        if (S[oj + ALIVE] < 0.5) continue;
        const drainJ = S[oj + DRAIN] > 0;
        if (drainI && drainJ) continue;
        const dz = Math.abs(S[oi + ZB] - S[oj + ZB]);
        const same = 1 - smooth(cfg.layerGap * 0.4, cfg.layerGap * 1.7, dz);
        const f = cfg.overlap + (1 - cfg.overlap) * same;
        const minD = (S[oi + RC] + S[oj + RC]) * f;
        const dx = S[oj + X] - S[oi + X];
        const dy = S[oj + Y] - S[oi + Y];
        const d2 = dx * dx + dy * dy;
        if (d2 >= minD * minD || d2 < 1e-6) continue;
        const d = Math.sqrt(d2);
        const nx = dx / d;
        const ny = dy / d;
        const pen = minD - d;
        let wi = 0.5;
        let wj = 0.5;
        if (drainI) {
          wi = 0;
          wj = 1;
        } else if (drainJ) {
          wi = 1;
          wj = 0;
        } else {
          // a sleeping coin is a wall to an awake one; two sleepers do not move each other
          const sI = S[oi + SLEEP] >= 24;
          const sJ = S[oj + SLEEP] >= 24;
          if (sI && sJ) continue;
          if (sI) {
            wi = 0;
            wj = 1;
            if (pen > 0.8) S[oi + SLEEP] = 0;
          } else if (sJ) {
            wi = 1;
            wj = 0;
            if (pen > 0.8) S[oj + SLEEP] = 0;
          }
        }
        if (it === 0) {
          const rv = (S[oj + X] - S[oj + PX] - (S[oi + X] - S[oi + PX])) * nx + (S[oj + Y] - S[oj + PY] - (S[oi + Y] - S[oi + PY])) * ny;
          const sp = -rv / dt;
          if (sp > ev[EV_IMPACT]) ev[EV_IMPACT] = sp;
        }
        S[oi + X] -= nx * pen * wi;
        S[oi + Y] -= ny * pen * wi;
        S[oj + X] += nx * pen * wj;
        S[oj + Y] += ny * pen * wj;
        // friction: take a part of the relative tangential slide out
        const tx = -ny;
        const ty = nx;
        const rt = (S[oj + X] - S[oj + PX] - (S[oi + X] - S[oi + PX])) * tx + (S[oj + Y] - S[oj + PY] - (S[oi + Y] - S[oi + PY])) * ty;
        const mu = Math.min(0.5 * same, 1) * 0.55;
        S[oi + X] += tx * rt * mu * wi;
        S[oi + Y] += ty * rt * mu * wi;
        S[oj + X] -= tx * rt * mu * wj;
        S[oj + Y] -= ty * rt * mu * wj;
      }
    }
    for (let i = 0; i < n; i++) {
      const o = i * STRIDE;
      if (S[o + ALIVE] < 0.5 || S[o + DRAIN] > 0) continue;
      const rc = S[o + RC];
      if (S[o + SLEEP] >= 24 && fOn < 0.5) continue;
      // walls and floor (friction on the floor)
      if (S[o + X] < rc) S[o + X] = rc;
      if (S[o + X] > w - rc) S[o + X] = w - rc;
      if (S[o + Y] > h - rc) {
        S[o + Y] = h - rc;
        S[o + PX] += (S[o + X] - S[o + PX]) * 0.35;
      }
      // the keep-out over the copy: a soft shelf that only pushes down
      if (cfg.keepOn > 0 && S[o + X] < cfg.keepW + rc * 0.3 && S[o + Y] < cfg.keepH + rc * 0.6 && S[o + Y] > -rc * 2) {
        const over = cfg.keepH + rc * 0.6 - S[o + Y];
        S[o + Y] += Math.min(over * 0.35, 8);
      }
      // the finger is a collider in the pile
      if (fOn > 0.5) {
        const dx = S[o + X] - inp[IN_FX];
        const dy = S[o + Y] - inp[IN_FY];
        const d2 = dx * dx + dy * dy;
        const m = rc * 0.9 + fr;
        if (d2 < m * m && d2 > 1e-6) {
          const d = Math.sqrt(d2);
          S[o + X] += (dx / d) * (m - d);
          S[o + Y] += (dy / d) * (m - d);
          S[o + SLEEP] = 0;
          S[o + R0X] = PUSH_MEMORY;
          if (S[o + GLOW] < 0.15) S[o + GLOW] = 0.15;
        }
      }
    }
  }

  // speed limit, sleeping, glow
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    if (S[o + ALIVE] < 0.5) continue;
    if (S[o + DRAIN] === 0) {
      let vx = S[o + X] - S[o + PX];
      let vy = S[o + Y] - S[o + PY];
      const maxv = 1400 * dt;
      const sp = Math.sqrt(vx * vx + vy * vy);
      if (sp > maxv) {
        vx *= maxv / sp;
        vy *= maxv / sp;
        S[o + PX] = S[o + X] - vx;
        S[o + PY] = S[o + Y] - vy;
      }
      if (S[o + SLEEP] >= 24) {
        // asleep: woken only by a real shove (a neighbour that moves, the finger)
        if (sp > 0.6 * dt * 120) S[o + SLEEP] = 0;
        else {
          S[o + PX] = S[o + X];
          S[o + PY] = S[o + Y];
        }
      } else {
        // it falls asleep once it has stayed within a third of a point of where it was for 40 steps
        const rx = S[o + X] - S[o + SX];
        const ry = S[o + Y] - S[o + SY];
        if (rx * rx + ry * ry < 0.12) {
          S[o + SLEEP] += 1;
          if (S[o + SLEEP] >= 24) {
            S[o + PX] = S[o + X];
            S[o + PY] = S[o + Y];
          }
        } else {
          S[o + SX] = S[o + X];
          S[o + SY] = S[o + Y];
          S[o + SLEEP] = 0;
        }
      }
      const dx = cfg.mx - S[o + X];
      const dy = cfg.my - S[o + Y];
      const d = Math.sqrt(dx * dx + dy * dy);
      // the lit river's length follows the flow: 90 pt up the heap when it runs full, 40 pt at single coins
      const river = 40 + 50 * clamp(rate / 14, 0, 1);
      const target = ev[EV_FLOW] * smooth(river, 28, d) * (rate > 0.01 || ev[EV_FLOW] > 0.05 ? 1 : 0);
      const k = target > S[o + GLOW] ? dt / 0.06 : dt / 0.28;
      S[o + GLOW] += (target - S[o + GLOW]) * Math.min(k, 1);
    }
  }
  resolveZ(cfg, S, dt);
}

/** the resting heap for this tile: coins fall one after another from the top (seeded), the solver settles them */
export function settleHeap(cfg: Cfg, S: number[], steps: number, release: number) {
  const n = cfg.n;
  const inp = new Array<number>(IN_SIZE).fill(0);
  const ev = new Array<number>(EV_SIZE).fill(0);
  const tl = [0, 0];
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    tiltOf(cfg.seed, i, tl);
    S[o + AX] = tl[0];
    S[o + AY] = tl[1];
    S[o + PHI] = rnd(cfg.seed, i, 21) * 6.2831853;
    S[o + FACE] = rnd(cfg.seed, i, 22) < 0.5 ? 0 : 1;
    S[o + RC] = radiusOf(cfg, tl[0], tl[1]);
    const layer = (i * 3 + Math.floor(rnd(cfg.seed, i, 23) * 2)) % cfg.layers;
    S[o + ZB] = cfg.t * 1.0 + layer * cfg.layerGap + rnd(cfg.seed, i, 24) * cfg.t * 0.5;
    S[o + Z] = S[o + ZB];
    S[o + ZT] = S[o + ZB];
    S[o + RANK] = layer * 100 + i;
    S[o + ALIVE] = 0;
    S[o + DRAIN] = 0;
    S[o + GLOW] = 0;
    startPose(cfg, S, i);
  }
  cfg.keepOn = 1;
  cfg.order = [];
  for (let i = 0; i < n; i++) cfg.order.push(i);
  cfg.order.sort((a, b) => S[a * STRIDE + RANK] - S[b * STRIDE + RANK]);
  const dt = 1 / 120;
  for (let s = 0; s < steps; s++) {
    // release the next coin every `release` steps
    const k = Math.floor(s / release);
    if (s % release === 0 && k < n) {
      S[k * STRIDE + ALIVE] = 1;
      S[k * STRIDE + CNT] = 1;
      S[k * STRIDE + Y] = -cfg.r;
      S[k * STRIDE + PY] = -cfg.r - 6;
    }
    stepSim(cfg, S, dt, inp, ev);
  }
  cfg.keepOn = 0;
  resolveZ(cfg, S, 0);
  // hold still
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    S[o + PX] = S[o + X];
    S[o + PY] = S[o + Y];
    S[o + SLEEP] = 100;
  }
  cfg.heap = [];
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    cfg.heap.push(S[o + X], S[o + Y], S[o + Z], S[o + AX], S[o + AY], S[o + PHI], S[o + FACE], S[o + RANK], S[o + ZB]);
  }
}

/** the draw / stacking order of a heap: coin indices by rank, lowest first */
export function orderOf(cfg: Cfg) {
  const idx: number[] = [];
  for (let i = 0; i < cfg.n; i++) idx.push(i);
  idx.sort((a, b) => cfg.heap[a * HS + 7] - cfg.heap[b * HS + 7]);
  cfg.order = idx;
}

/** write the resting heap into the state: every coin present, still, counted, unlit */
export function loadHeap(cfg: Cfg, S: number[]) {
  'worklet';
  const n = cfg.n;
  const Hp = cfg.heap;
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    const h = i * HS;
    S[o + X] = Hp[h];
    S[o + Y] = Hp[h + 1];
    S[o + PX] = Hp[h];
    S[o + PY] = Hp[h + 1];
    S[o + Z] = Hp[h + 2];
    S[o + ZT] = Hp[h + 2];
    S[o + AX] = Hp[h + 3];
    S[o + AY] = Hp[h + 4];
    S[o + PHI] = Hp[h + 5];
    S[o + FACE] = Hp[h + 6];
    S[o + RANK] = Hp[h + 7];
    S[o + RC] = radiusOf(cfg, Hp[h + 3], Hp[h + 4]);
    S[o + ZB] = Hp[h + 8];
    S[o + ALIVE] = 1;
    S[o + CNT] = 1;
    S[o + DRAIN] = 0;
    S[o + GLOW] = 0;
    S[o + SLEEP] = 100;
    S[o + AUX] = 0;
    S[o + R0X] = 0;
  }
}
