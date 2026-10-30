// Career Web Onboarding — the timeline (analytic, so frame LOOP equals frame 0) and a small spring
// network on top for the finger: the people and roles are nodes, the white bars elastic links.

import { BARS, FITS, NODES } from './cwo-theme';

export const LOOP = 9.0;
export const RESUME = 2;

export const T = {
  markOut: [1.0, 1.3], // the marker lifts off "Dream" (right to left) …
  markIn: [1.3, 1.85], // … and sweeps it again
  arrowOut: [1.7, 2.0],
  arrowIn: [2.0, 2.6], // the arrow draws itself down to the web
  pops: 2.6, // the pop marks flick
  pick: 2.85, // the second person is picked …
  grow: [2.95, 3.45], // … and two links grow from them to the roles that fit
  tug: [3.9, 4.5], // they are tugged down and let go: the web wobbles
  ungrow: [6.2, 6.7],
  nudge: [7.2, 7.8], // the button's arrow nudges forward
} as const;

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
export function easeIn(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * p;
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
export function wobble(tau: number, period: number, decay: number) {
  'worklet';
  if (tau <= 0) return 0;
  return Math.exp(-tau / decay) * Math.cos((2 * Math.PI * tau) / period);
}

const TAU = Math.PI * 2;

/** The scripted tug: pulled out over T.tug, then let go into a damped swing. */
function tugCurve(t: number) {
  'worklet';
  if (t < T.tug[0]) return 0;
  if (t < T.tug[1]) return smooth(seg(t, T.tug[0], T.tug[1]));
  return wobble(t - T.tug[1], 0.5, 0.32);
}
const TUG = { x: 26, y: 30 };
/** How strongly each node follows the tugged one (by its links), and how late. */
const FOLLOW = [
  { k: 0.06, lag: 0.2 },
  { k: 1, lag: 0 },
  { k: 0.06, lag: 0.2 },
  { k: 0.2, lag: 0.16 },
  { k: 0.45, lag: 0.08 },
  { k: 0.45, lag: 0.08 },
  { k: 0.2, lag: 0.16 },
];

/** Node i at loop time t (the drift uses amb, the script uses t). */
export function nodeHome(i: number, t: number, amb: number) {
  'worklet';
  const n = NODES[i];
  const ph = amb / LOOP;
  const f = FOLLOW[i];
  const g = tugCurve(t - f.lag) * f.k;
  return {
    x: n.x + 2.2 * Math.sin(TAU * ((1 + (i % 2)) * ph + i * 0.17)) + TUG.x * g,
    y: n.y + 2.8 * Math.sin(TAU * ((2 - (i % 2)) * ph + i * 0.31)) + TUG.y * g,
  };
}

export type Frame = {
  t: number;
  mark: number; // share of "Dream" the marker covers (from the left)
  arrow: number; // share of the arrow drawn
  head: number; // arrowhead pop (wobble)
  pops: number; // pop marks flick (wobble)
  grow: number[]; // links grown from each person, 0 … 1
  pick: number; // ring round the picked person (−1 none)
  nudge: number; // button arrow nudge 0 … 1 … 0
  tugVib: number; // the links ring after the tug
};

export function frameAt(t: number): Frame {
  'worklet';
  let mark = 1;
  if (t >= T.markOut[0] && t < T.markOut[1]) mark = 1 - easeIn(seg(t, T.markOut[0], T.markOut[1]));
  else if (t >= T.markIn[0] && t < T.markIn[1]) mark = easeOut(seg(t, T.markIn[0], T.markIn[1]));
  let arrow = 1;
  if (t >= T.arrowOut[0] && t < T.arrowOut[1]) arrow = 1 - easeIn(seg(t, T.arrowOut[0], T.arrowOut[1]));
  else if (t >= T.arrowIn[0] && t < T.arrowIn[1]) arrow = easeInOut(seg(t, T.arrowIn[0], T.arrowIn[1]));
  const g1 = t < T.ungrow[0] ? easeOut(seg(t, T.grow[0], T.grow[1])) : 1 - easeInOut(seg(t, T.ungrow[0], T.ungrow[1]));
  const pk = t - T.pick;
  const np = seg(t, T.nudge[0], T.nudge[1]);
  return {
    t,
    mark,
    arrow,
    head: wobble(t - T.arrowIn[1] + 0.05, 0.22, 0.12),
    pops: wobble(t - T.pops, 0.2, 0.12),
    grow: [0, g1, 0],
    pick: pk >= 0 && pk < 0.7 ? pk / 0.7 : -1,
    nudge: np > 0 && np < 1 ? Math.sin(Math.PI * np) : 0,
    tugVib: t >= T.tug[1] ? Math.exp(-(t - T.tug[1]) / 0.45) * Math.sin(TAU * (t - T.tug[1]) * 7) : 0,
  };
}

// ------------------------------------------------------------------ the driver

export const N = NODES.length;
/** Every link that can exist: the two bars, then each person's two fits. */
export const LINKS: [number, number][] = (() => {
  const l: [number, number][] = [...BARS];
  for (let p = 0; p < FITS.length; p++) for (const r of FITS[p]) l.push([p, r]);
  return l;
})();

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  ox: number[];
  oy: number[];
  vx: number[];
  vy: number[];
  grow: number[]; // live links grown per person (−1: the loop decides)
  growAim: number[];
  vib: number[]; // per link: pluck amplitude (pt) …
  vibAt: number[]; // … and when (clock)
  hold: number; // node under the finger (−1 none)
  fx: number;
  fy: number;
  gdx: number;
  gdy: number;
  press: number;
  pressAim: number;
  shootAt: number; // the button's arrow shot (clock, −1 none)
  pickAt: number[]; // ring round a person (clock, −1 none)
};

export function makeDriver(t = 0): Driver {
  'worklet';
  const z = (n: number, v = 0) => {
    const a: number[] = [];
    for (let i = 0; i < n; i++) a.push(v);
    return a;
  };
  return {
    t,
    mode: 0,
    last: -10,
    amb: t,
    ox: z(N),
    oy: z(N),
    vx: z(N),
    vy: z(N),
    grow: z(3, -1),
    growAim: z(3),
    vib: z(LINKS.length),
    vibAt: z(LINKS.length, -10),
    hold: -1,
    fx: 0,
    fy: 0,
    gdx: 0,
    gdy: 0,
    press: 0,
    pressAim: 0,
    shootAt: -1,
    pickAt: z(3, -1),
  };
}

/** How much link l is present (bars always; a person's fits as far as they have grown). */
export function linkOn(l: number, grow: number[]) {
  'worklet';
  if (l < BARS.length) return 1;
  return grow[Math.floor((l - BARS.length) / 2)];
}

const K_HOME = 30;
const K_LINK = 34;

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean, loopGrow: number[]) {
  'worklet';
  if (d.mode === 2) {
    if (auto && d.hold < 0 && now - d.last >= RESUME) {
      d.mode = 0;
      for (let p = 0; p < 3; p++) d.grow[p] = -1;
    }
  } else if (auto) d.t = (d.t + dt) % LOOP;
  d.amb = (d.amb + dt) % LOOP;
  d.press += (d.pressAim - d.press) * (1 - Math.exp(-dt * (d.pressAim > 0 ? 40 : 16)));
  for (let p = 0; p < 3; p++) if (d.grow[p] >= 0) d.grow[p] += (d.growAim[p] - d.grow[p]) * (1 - Math.exp(-dt * 7));
  const grow: number[] = [];
  for (let p = 0; p < 3; p++) grow.push(d.grow[p] >= 0 ? d.grow[p] : loopGrow[p]);

  const n = 3;
  const h = dt / n;
  for (let s = 0; s < n; s++) {
    const px: number[] = [];
    const py: number[] = [];
    const hx: number[] = [];
    const hy: number[] = [];
    for (let i = 0; i < N; i++) {
      const hm = nodeHome(i, d.t, d.amb);
      hx.push(hm.x);
      hy.push(hm.y);
      px.push(hm.x + d.ox[i]);
      py.push(hm.y + d.oy[i]);
    }
    const ax: number[] = [];
    const ay: number[] = [];
    for (let i = 0; i < N; i++) {
      ax.push(-K_HOME * d.ox[i] - 2 * 0.42 * Math.sqrt(K_HOME + 20) * d.vx[i]);
      ay.push(-K_HOME * d.oy[i] - 2 * 0.42 * Math.sqrt(K_HOME + 20) * d.vy[i]);
    }
    // elastic links pull their two ends toward their resting length
    for (let l = 0; l < LINKS.length; l++) {
      const on = linkOn(l, grow);
      if (on < 0.5) continue;
      const a = LINKS[l][0];
      const b = LINKS[l][1];
      const dx = px[b] - px[a];
      const dy = py[b] - py[a];
      const len = Math.hypot(dx, dy) || 1;
      const rest = Math.hypot(hx[b] - hx[a], hy[b] - hy[a]);
      const f = K_LINK * (len - rest) * on;
      ax[a] += (dx / len) * f;
      ay[a] += (dy / len) * f;
      ax[b] -= (dx / len) * f;
      ay[b] -= (dy / len) * f;
    }
    // soft bumps between the circles
    for (let a = 0; a < N; a++) {
      for (let b = a + 1; b < N; b++) {
        const dx = px[b] - px[a];
        const dy = py[b] - py[a];
        const dist = Math.hypot(dx, dy) || 1;
        const min = NODES[a].r + NODES[b].r - 6;
        if (dist >= min) continue;
        const f = (min - dist) * 380;
        ax[a] -= (dx / dist) * f;
        ay[a] -= (dy / dist) * f;
        ax[b] += (dx / dist) * f;
        ay[b] += (dy / dist) * f;
      }
    }
    for (let i = 0; i < N; i++) {
      if (i === d.hold) {
        const tx = d.fx - d.gdx - hx[i];
        const ty = d.fy - d.gdy - hy[i];
        const nx = d.ox[i] + (tx - d.ox[i]) * 0.45;
        const ny = d.oy[i] + (ty - d.oy[i]) * 0.45;
        d.vx[i] = (nx - d.ox[i]) / h;
        d.vy[i] = (ny - d.oy[i]) / h;
        d.ox[i] = nx;
        d.oy[i] = ny;
        continue;
      }
      d.vx[i] += ax[i] * h;
      d.vy[i] += ay[i] * h;
      d.ox[i] += d.vx[i] * h;
      d.oy[i] += d.vy[i] * h;
    }
  }
  return { t: d.t, grow };
}

/** A finger came down: the loop pauses where it is. */
export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 0) {
    const f = frameAt(d.t);
    for (let p = 0; p < 3; p++) {
      d.grow[p] = f.grow[p];
      d.growAim[p] = f.grow[p] > 0.5 ? 1 : 0;
    }
  }
  d.mode = 2;
}

/** Pluck link l (a tap on it, or a node let go hard). */
export function pluck(d: Driver, l: number, amp: number, now: number) {
  'worklet';
  d.vib[l] = amp;
  d.vibAt[l] = now;
}

/** The current swing of link l, perpendicular to it (pt). */
export function vibration(d: Driver, l: number, now: number) {
  'worklet';
  const tau = now - d.vibAt[l];
  if (tau < 0 || tau > 1.6) return 0;
  return d.vib[l] * Math.exp(-tau / 0.4) * Math.sin(TAU * 8 * tau);
}
