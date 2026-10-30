// Ghost Float Welcome — the timeline, the float, and the touch physics.
// The loop is analytic (every pose a function of loop time), so frame LOOP equals frame 0. Touch
// adds a small spring simulation on top (offsets that always settle back to zero).

import { BODIES, FIELD_BOTTOM, REF_W, STARS, UI } from './gfw-theme';

export const LOOP = 9.0;
export const RESUME = 2;

export const T = {
  blinks: [0.9, 3.95, 7.6],
  inhale: [1.4, 1.75], // the ghost draws a breath …
  puff: 1.75, // … and says boo: a ring of air, every token kicked away
  ring: [1.75, 2.4],
  tickPress: [4.45, 4.56], // the checkbox is pressed …
  tick: [4.56, 4.86], // … filled and ticked
  button: [4.66, 5.0], // Create a new wallet wakes up
  sheen: [5.0, 5.55],
  happy: [4.7, 6.75], // the ghost smiles (^ ^) …
  roll: [4.8, 5.55], // … and loops over in delight
  lean: [5.0, 5.9], // the tokens lean toward the button, then relax
  untick: [6.95, 7.25],
  buttonOff: [7.0, 7.3],
} as const;

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
export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u * u * u;
}
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 16 * p * p * p * p * p : 1 - Math.pow(-2 * p + 2, 5) / 2;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
/** A damped wobble that starts at 0, swings out to about +1 and settles through a small overshoot. */
export function kick(tau: number, period: number, decay: number) {
  'worklet';
  if (tau <= 0) return 0;
  return 1.9 * Math.exp(-tau / decay) * Math.sin((2 * Math.PI * tau) / period);
}
export function wobble(tau: number, period: number, decay: number) {
  'worklet';
  if (tau <= 0) return 0;
  return Math.exp(-tau / decay) * Math.cos((2 * Math.PI * tau) / period);
}

const TAU = Math.PI * 2;

// ------------------------------------------------------------------ the float (loop-periodic)

/** Per body: whole cycles per loop on x and y, phases, drift (pt) and turn (°). */
const FLOAT = [
  { nx: 1, ny: 2, px: 0.1, py: 0.3, ax: 4, ay: 5.5, rot: 4 }, // ghost
  { nx: 2, ny: 1, px: 0.55, py: 0.05, ax: 5, ay: 6, rot: 7 },
  { nx: 1, ny: 2, px: 0.3, py: 0.8, ax: 6, ay: 5, rot: 9 },
  { nx: 2, ny: 1, px: 0.85, py: 0.4, ax: 4.5, ay: 6.5, rot: 10 },
  { nx: 1, ny: 1, px: 0.2, py: 0.65, ax: 5.5, ay: 7, rot: 8 },
  { nx: 2, ny: 2, px: 0.7, py: 0.15, ax: 4, ay: 5, rot: 9 },
  { nx: 1, ny: 2, px: 0.45, py: 0.95, ax: 6, ay: 5, rot: 6 },
];

const GHOST = BODIES[0];

function booDir(x: number, y: number) {
  'worklet';
  const dx = x - GHOST.x;
  const dy = y - GHOST.y;
  const d = Math.hypot(dx, dy) || 1;
  return { ux: dx / d, uy: dy / d, d };
}

/** The scripted boo: a kick away from the ghost that reaches each thing a little later the farther it is. */
export function booOffset(x: number, y: number, t: number, gain: number) {
  'worklet';
  const b = booDir(x, y);
  if (b.d < 1) return { x: 0, y: 0, k: 0 };
  const tau = t - (T.puff + 0.03 + b.d / 700);
  const amp = gain * Math.max(18, 74 - b.d * 0.16);
  const k = kick(tau, 1.3, 0.55);
  return { x: b.ux * amp * k, y: b.uy * amp * k, k };
}

/** The tokens lean toward the button once it wakes up. */
export function leanOffset(x: number, y: number, t: number) {
  'worklet';
  const l = Math.sin(Math.PI * seg(t, T.lean[0], T.lean[1]));
  if (l <= 0) return { x: 0, y: 0 };
  const bx = UI.button.x + UI.button.w / 2;
  const by = UI.button.y;
  const dx = bx - x;
  const dy = by - y;
  const d = Math.hypot(dx, dy) || 1;
  const a = 9 * l * (1 - Math.min(0.6, d / 900));
  return { x: (dx / d) * a, y: (dy / d) * a };
}

export type Pose = { x: number; y: number; rot: number };

/** Where body i is at loop time t (amb drives the float, t the choreography). */
export function bodyPose(i: number, t: number, amb: number): Pose {
  'worklet';
  const b = BODIES[i];
  const f = FLOAT[i];
  const ph = amb / LOOP;
  let x = b.x + f.ax * Math.sin(TAU * (f.nx * ph + f.px));
  let y = b.y + f.ay * Math.sin(TAU * (f.ny * ph + f.py));
  let rot = f.rot * Math.sin(TAU * (ph + f.px + f.py));
  if (i > 0) {
    const o = booOffset(b.x, b.y, t, 1);
    x += o.x;
    y += o.y;
    rot += o.k * 26 * (i % 2 === 0 ? 1 : -1);
    const l = leanOffset(b.x, b.y, t);
    x += l.x;
    y += l.y;
  }
  return { x, y, rot };
}

export function starPose(i: number, t: number, amb: number): Pose {
  'worklet';
  const s = STARS[i];
  const ph = amb / LOOP;
  const o = booOffset(s.x, s.y, t, 0.55);
  return {
    x: s.x + 3 * Math.sin(TAU * (ph + i * 0.17)) + o.x,
    y: s.y + 4 * Math.sin(TAU * (ph * 2 + i * 0.29)) + o.y,
    rot: 18 * Math.sin(TAU * (ph + i * 0.13)) + o.k * 40,
  };
}

// ------------------------------------------------------------------ the frame

export type Frame = {
  t: number;
  inhale: number; // 0 … 1
  puff: number; // ghost stretch after the boo (wobble, −1 … 1)
  ring: number; // −1 none, else 0 … 1
  wide: number; // eyes wide 0 … 1
  blink: number; // eyes closed 0 … 1
  happy: number; // ^ ^ eyes 0 … 1
  roll: number; // ghost loop, degrees
  hop: number; // ghost hop, pt
  press: number; // checkbox pressed 0 … 1
  tick: number; // checkbox filled + ticked 0 … 1
  button: number; // disabled 0 … enabled 1
  sheen: number; // −1 none
};

export function frameAt(t: number): Frame {
  'worklet';
  let blink = 0;
  for (let i = 0; i < T.blinks.length; i++) {
    const p = seg(t, T.blinks[i], T.blinks[i] + 0.16);
    if (p > 0 && p < 1) blink = Math.sin(Math.PI * p);
  }
  const inhale = t >= T.inhale[0] && t < T.puff ? easeInOut(seg(t, T.inhale[0], T.puff)) : 0;
  const puff = t >= T.puff ? wobble(t - T.puff, 0.34, 0.24) : 0;
  const rp = seg(t, T.ring[0], T.ring[1]);
  const wide = t >= T.puff ? Math.max(0, 1 - seg(t, T.puff + 0.3, T.puff + 0.9)) : inhale * 0;
  const happy = t < T.happy[1] ? smooth(seg(t, T.happy[0], T.happy[0] + 0.18)) : 1 - smooth(seg(t, T.happy[1], T.happy[1] + 0.2));
  const rr = seg(t, T.roll[0], T.roll[1]);
  const tickOn = easeOut(seg(t, T.tick[0], T.tick[1]));
  const tickOff = easeOut(seg(t, T.untick[0], T.untick[1]));
  const pp = seg(t, T.tickPress[0], T.tick[0] + 0.12);
  const sp = seg(t, T.sheen[0], T.sheen[1]);
  return {
    t,
    inhale,
    puff,
    ring: rp > 0 && rp < 1 ? rp : -1,
    wide,
    blink,
    happy,
    roll: 360 * easeInOut(rr),
    hop: 22 * Math.sin(Math.PI * rr),
    press: pp > 0 && pp < 1 ? Math.sin(Math.PI * pp) : 0,
    tick: t < T.untick[0] ? tickOn : 1 - tickOff,
    button: t < T.buttonOff[0] ? easeInOut(seg(t, T.button[0], T.button[1])) : 1 - easeInOut(seg(t, T.buttonOff[0], T.buttonOff[1])),
    sheen: sp > 0 && sp < 1 ? sp : -1,
  };
}

// ------------------------------------------------------------------ the driver (touch on top of the loop)

/** Live bodies: the 7 floating things then the 6 stars. */
export const N_LIVE = BODIES.length + STARS.length;

export type Driver = {
  t: number;
  mode: 0 | 2; // 0 autoPlay, 2 parked (a finger has the screen)
  last: number;
  amb: number; // ambient clock, wraps at LOOP
  // spring offsets and velocities per live body (pt, pt/s, °, °/s)
  ox: number[];
  oy: number[];
  vx: number[];
  vy: number[];
  or: number[];
  vr: number[];
  // the finger
  down: number;
  fx: number;
  fy: number;
  fvx: number;
  fvy: number;
  grab: number; // body held by the finger (−1 none)
  gdx: number; // where on it the finger took hold
  gdy: number;
  // the ghost's own touch moments (clock times, −1 none)
  booAt: number;
  rollAt: number;
  // the checkbox and button under the finger's control (−1: the loop decides)
  agree: number;
  agreeAim: number;
  btnPress: number;
  btnAim: number;
  noAt: number; // the button refused (not agreed): it shakes, the checkbox pulses
  lookX: number;
  lookY: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  const z = () => {
    const a: number[] = [];
    for (let i = 0; i < N_LIVE; i++) a.push(0);
    return a;
  };
  return {
    t,
    mode: 0,
    last: -10,
    amb: t,
    ox: z(),
    oy: z(),
    vx: z(),
    vy: z(),
    or: z(),
    vr: z(),
    down: 0,
    fx: 0,
    fy: 0,
    fvx: 0,
    fvy: 0,
    grab: -1,
    gdx: 0,
    gdy: 0,
    booAt: -1,
    rollAt: -1,
    agree: -1,
    agreeAim: 0,
    btnPress: 0,
    btnAim: 0,
    noAt: -1,
    lookX: 0,
    lookY: 0,
  };
}

function follow(cur: number, aim: number, k: number, dt: number) {
  'worklet';
  return cur + (aim - cur) * (1 - Math.exp(-dt * k));
}

/** Home of live body j (before the spring offset) and its radius. */
export function liveHome(j: number, t: number, amb: number) {
  'worklet';
  if (j < BODIES.length) {
    const p = bodyPose(j, t, amb);
    return { x: p.x, y: p.y, r: BODIES[j].r, mass: 1 };
  }
  const s = STARS[j - BODIES.length];
  const p = starPose(j - BODIES.length, t, amb);
  return { x: p.x, y: p.y, r: s.s, mass: 0.4 };
}

const K = 38; // spring home
const DAMP = 2 * 0.5 * Math.sqrt(K);
const STIR_R = 150; // the finger's reach

/** Advance the loop and the touch physics by dt. */
export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  if (d.mode === 2) {
    if (auto && d.down === 0 && now - d.last >= RESUME) {
      d.mode = 0;
      d.agree = -1;
    }
  } else if (auto) d.t = (d.t + dt) % LOOP;
  d.amb = (d.amb + dt) % LOOP;
  if (d.agree >= 0) d.agree = follow(d.agree, d.agreeAim, 16, dt);
  d.btnPress = follow(d.btnPress, d.btnAim, d.btnAim > 0 ? 40 : 16, dt);

  const n = 2;
  const h = dt / n;
  for (let step = 0; step < n; step++) {
    for (let j = 0; j < N_LIVE; j++) {
      const home = liveHome(j, d.t, d.amb);
      const x = home.x + d.ox[j];
      const y = home.y + d.oy[j];
      if (j === d.grab) {
        // held: it follows the finger closely, and keeps the finger's speed for the throw
        const tx = d.fx - d.gdx - home.x;
        const ty = d.fy - d.gdy - home.y;
        d.vx[j] = (tx - d.ox[j]) / Math.max(h, 0.004) * 0.35;
        d.vy[j] = (ty - d.oy[j]) / Math.max(h, 0.004) * 0.35;
        d.ox[j] += (tx - d.ox[j]) * 0.35;
        d.oy[j] += (ty - d.oy[j]) * 0.35;
        d.vr[j] += (d.fvx * 0.08 - d.vr[j]) * 0.2;
        d.or[j] += d.vr[j] * h;
        continue;
      }
      let ax = -K * d.ox[j] - DAMP * d.vx[j];
      let ay = -K * d.oy[j] - DAMP * d.vy[j];
      if (d.down > 0 && d.grab < 0) {
        // the finger stirs the air: things near it are carried along its path and swirl round it
        const dx = x - d.fx;
        const dy = y - d.fy;
        const dist = Math.hypot(dx, dy) || 1;
        if (dist < STIR_R) {
          const w = (1 - dist / STIR_R) * (1 - dist / STIR_R);
          const sp = Math.min(1600, Math.hypot(d.fvx, d.fvy));
          const tx = -dy / dist;
          const ty = dx / dist;
          const side = d.fvx * tx + d.fvy * ty >= 0 ? 1 : -1;
          const g = 5.5 / home.mass;
          ax += w * g * (d.fvx * 0.9 + tx * sp * 0.7 * side);
          ay += w * g * (d.fvy * 0.9 + ty * sp * 0.7 * side);
          // and nothing sits under the fingertip
          const near = home.r + 26;
          if (dist < near) {
            ax += (dx / dist) * (near - dist) * 260;
            ay += (dy / dist) * (near - dist) * 260;
          }
          d.vr[j] += side * w * sp * 0.04 * h * 60;
        }
      }
      d.vx[j] += ax * h;
      d.vy[j] += ay * h;
      const v = Math.hypot(d.vx[j], d.vy[j]);
      if (v > 2400) {
        d.vx[j] *= 2400 / v;
        d.vy[j] *= 2400 / v;
      }
      d.ox[j] += d.vx[j] * h;
      d.oy[j] += d.vy[j] * h;
      // the spin springs back too
      d.vr[j] += (-30 * d.or[j] - 2 * 0.45 * Math.sqrt(30) * d.vr[j]) * h;
      d.or[j] += d.vr[j] * h;
      // the screen's edges bounce
      const px = home.x + d.ox[j];
      const py = home.y + d.oy[j];
      const r = home.r;
      if (px < r * 0.4 && d.vx[j] < 0) d.vx[j] *= -0.6;
      if (px > REF_W - r * 0.4 && d.vx[j] > 0) d.vx[j] *= -0.6;
      if (py < 60 + r && d.vy[j] < 0) d.vy[j] *= -0.6;
      if (py > FIELD_BOTTOM - r && d.vy[j] > 0) d.vy[j] *= -0.6;
    }
    // the floating things bump instead of passing through each other: a soft push, so a spring
    // pulling one home always wins in the end and it slides round whatever is in its way
    for (let a = 0; a < BODIES.length; a++) {
      for (let b = a + 1; b < BODIES.length; b++) {
        const ha = liveHome(a, d.t, d.amb);
        const hb = liveHome(b, d.t, d.amb);
        const dx = hb.x + d.ox[b] - (ha.x + d.ox[a]);
        const dy = hb.y + d.oy[b] - (ha.y + d.oy[a]);
        const dist = Math.hypot(dx, dy) || 1;
        const min = ha.r + hb.r + 3;
        if (dist >= min) continue;
        const f = Math.min(9000, (min - dist) * 420) * h;
        const ux = dx / dist;
        const uy = dy / dist;
        if (a !== d.grab) {
          d.vx[a] -= ux * f;
          d.vy[a] -= uy * f;
        }
        if (b !== d.grab) {
          d.vx[b] += ux * f;
          d.vy[b] += uy * f;
        }
      }
    }
  }
  // the ghost's eyes: the finger, else whatever is flying, else a slow look round
  let lx = 0.35 * Math.sin(TAU * (d.amb / LOOP)) ;
  let ly = 0.25 * Math.sin(TAU * (2 * d.amb / LOOP) + 1);
  const g = liveHome(0, d.t, d.amb);
  const gx = g.x + d.ox[0];
  const gy = g.y + d.oy[0];
  if (d.down > 0 && d.grab !== 0) {
    lx = (d.fx - gx) / 120;
    ly = (d.fy - gy) / 120;
  } else {
    let best = 260;
    for (let j = 1; j < BODIES.length; j++) {
      const v = Math.hypot(d.vx[j], d.vy[j]);
      if (v > best) {
        best = v;
        const hj = liveHome(j, d.t, d.amb);
        lx = (hj.x + d.ox[j] - gx) / 120;
        ly = (hj.y + d.oy[j] - gy) / 120;
      }
    }
  }
  const ll = Math.hypot(lx, ly);
  if (ll > 1) {
    lx /= ll;
    ly /= ll;
  }
  d.lookX = follow(d.lookX, lx, 9, dt);
  d.lookY = follow(d.lookY, ly, 9, dt);
  return d.t;
}

/** A finger came down: the loop pauses where it is (the float keeps drifting). */
export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  d.mode = 2;
}

/** Boo! (a tap on the ghost): every live body is kicked away from it. */
export function boo(d: Driver, now: number, t: number) {
  'worklet';
  d.booAt = now;
  const g = liveHome(0, t, d.amb);
  const gx = g.x + d.ox[0];
  const gy = g.y + d.oy[0];
  for (let j = 1; j < N_LIVE; j++) {
    const h = liveHome(j, t, d.amb);
    const dx = h.x + d.ox[j] - gx;
    const dy = h.y + d.oy[j] - gy;
    const dist = Math.hypot(dx, dy) || 1;
    const k = Math.max(160, 900 - dist * 1.6) / (j < BODIES.length ? 1 : 0.6);
    d.vx[j] += (dx / dist) * k;
    d.vy[j] += (dy / dist) * k;
    d.vr[j] += (j % 2 === 0 ? 1 : -1) * 260;
  }
}

/** The ghost's boo and loop from the clock (touch), as frame values. */
export function ghostExtra(d: Driver, now: number) {
  'worklet';
  let puff = 0;
  let ring = -1;
  let wide = 0;
  if (d.booAt >= 0) {
    const p = now - d.booAt;
    if (p > 1.6) d.booAt = -1;
    else {
      puff = wobble(p, 0.34, 0.24);
      ring = p < 0.65 ? p / 0.65 : -1;
      wide = 1 - seg(p, 0.3, 0.9);
    }
  }
  let roll = 0;
  let hop = 0;
  if (d.rollAt >= 0) {
    const p = (now - d.rollAt) / 0.75;
    if (p >= 1) d.rollAt = -1;
    else {
      roll = 360 * easeInOut(p);
      hop = 22 * Math.sin(Math.PI * p);
    }
  }
  let no = 0;
  if (d.noAt >= 0) {
    const p = now - d.noAt;
    if (p > 0.6) d.noAt = -1;
    else no = Math.exp(-p / 0.16) * Math.sin(2 * Math.PI * p / 0.12);
  }
  return { puff, ring, wide, roll, hop, no, noP: d.noAt >= 0 ? (now - d.noAt) / 0.6 : -1 };
}
