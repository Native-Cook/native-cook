// Potion Vial Upload — the timeline, the driver (autoPlay / touch state machine) and the gem's
// physics. Everything a frame shows is a pure function of the loop time, except the gem on its
// chain and the spider's startle, which are simulated (both are back at rest before the loop ends).
//
// Worklet rule: helpers sit ABOVE their callers (a worklet captures what exists when it is made).

import { FILE, V } from './pvu-theme';

// The reference recording, measured at 30 fps; the phone adds the file's flight from its card and a
// fade to black so the loop can start again (the recording is one take, not a loop).
export const LOOP = 12;

/** Timeline marks (seconds). */
export const T = {
  lift: 0.6, // the file leaves the card (the recording's drag)
  near: 1.0, // it reaches the vial: the gem pops, mist pours out, "Release to drop it in"
  drop: 1.75, // let go: the file slips into the mouth, smaller and smaller (1.75–1.88)
  recork1: 2.0, // the gem snaps back into the stopper
  streak: [2.03, 2.73] as const, // a green cloud streams down the tube
  splash: 2.73, // the potion starts filling at the far end (1 %)
  fill: 8.4, // 100 %, "Upload complete"
  done: 8.5, // the gem bursts off in a fountain of sparks and swings
  climb: [9.3, 10.0] as const, // the spider climbs down the chain onto the gem
  recork2: 11.6, // under the fade: the gem goes home
  fade: [11.15, 11.6] as const, // to black…
  fadeIn: [0, 0.35] as const, // …and back
  still: 10.4, // reduced motion: the finished upload
} as const;

export const clamp01 = (v: number) => {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
};
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return clamp01((t - a) / (b - a));
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
export const easeOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return 1 - (1 - x) * (1 - x) * (1 - x);
};
export const easeIn = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * x;
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
export const easeOutBack = (k: number) => {
  'worklet';
  const x = clamp01(k);
  const c1 = 1.9;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};
export const hash = (i: number) => {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
};

/** Upload progress 0…1: bursts and stalls like a real upload, but the same every loop. */
/** Upload progress from the recording's counter: [seconds, percent]. */
export const KEYS = [
  2.73, 1, 2.8, 3, 2.87, 5, 2.93, 7, 3.0, 8, 3.07, 10, 3.13, 12, 3.2, 14, 3.27, 15, 3.33, 17, 3.4, 18, 3.47, 20, 3.53,
  21, 4.0, 27, 4.4, 34, 4.8, 40, 5.2, 48, 5.6, 58, 6.0, 60, 6.4, 67, 6.8, 78, 7.2, 82, 7.6, 86, 8.0, 94, 8.1, 96, 8.2,
  97, 8.3, 99, 8.4, 100,
];
export const progressAt = (t: number) => {
  'worklet';
  if (t < KEYS[0]) return 0;
  for (let i = 0; i < KEYS.length - 2; i += 2) {
    if (t <= KEYS[i + 2]) return mix(KEYS[i + 1], KEYS[i + 3], (t - KEYS[i]) / (KEYS[i + 2] - KEYS[i])) / 100;
  }
  return 1;
};

/** Vertical centre of the vial and the vial's anchor points, for a given screen. */
export type Geo = { w: number; h: number; s: number; ox: number; cy: number; top: number; bottom: number; pd: number };

/** Where the stopper's chain is fixed (bottom of the first brass band), relative to the vial line. */
export const ANCHOR = { x: (V.band1[0] + V.band1[1]) / 2, y: V.band1[2] - 1 };
/** The gem's centre when it sits in the stopper, relative to the vial line, and its roll there. */
export const CORK = { x: V.band2[1] + 18, y: -1, rot: Math.PI + 0.5 };
/** The mouth (end of the stopper), where mist pours out and files go in. */
export const MOUTH = { x: V.band2[1] + 2, y: 0 };

// ---------------------------------------------------------------------------------------------
// The gem on its chain: a particle tied to ANCHOR by a chain of length V.chain (a slack chain
// sags, a taut one swings it like a pendulum), plus a roll that follows the chain.

export type Gem = {
  x: number; // centre, relative to ANCHOR
  y: number;
  vx: number;
  vy: number;
  rot: number;
  w: number; // roll speed
  corked: number; // 1 in the stopper, 0 free
  homeAt: number; // clock time a re-cork started (-1: none)
  fromX: number;
  fromY: number;
  fromRot: number;
  holdX: number; // finger target while dragged (relative to ANCHOR), NaN when not held
  holdY: number;
  lastHome: number; // clock time the last re-cork started
  homeDur: number; // how long the flight home takes
};

export const HOME = { x: CORK.x - ANCHOR.x, y: CORK.y - ANCHOR.y };
/** The rest pose of a free gem: hanging straight down at the end of its chain. */
export const HANG = { x: 0, y: V.chain };

export const makeGem = (corked: boolean): Gem => {
  'worklet';
  return {
    x: corked ? HOME.x : HANG.x,
    y: corked ? HOME.y : HANG.y,
    vx: 0,
    vy: 0,
    rot: corked ? CORK.rot : 0,
    w: 0,
    corked: corked ? 1 : 0,
    homeAt: -1,
    fromX: 0,
    fromY: 0,
    fromRot: 0,
    holdX: NaN,
    holdY: NaN,
    lastHome: -10,
    homeDur: RECORK,
  };
};

const RECORK = 0.34;
const SNAP = 0.07;

export const popGem = (g: Gem, strength: number) => {
  'worklet';
  if (g.corked === 0 && g.homeAt < 0) return;
  g.corked = 0;
  g.homeAt = -1;
  g.vx = 70 * strength;
  g.vy = -150 * strength;
  g.w = 5.5 * strength;
};

export const recorkGem = (g: Gem, now: number, snap = false) => {
  'worklet';
  if (g.corked === 1 && g.homeAt < 0) return;
  g.corked = 1;
  g.homeAt = now;
  g.lastHome = now;
  g.homeDur = snap ? SNAP : RECORK;
  g.fromX = g.x;
  g.fromY = g.y;
  g.fromRot = g.rot;
  g.vx = 0;
  g.vy = 0;
  g.w = 0;
  g.holdX = NaN;
};

/** Advance the gem by dt seconds (sub-stepped). */
export const stepGem = (g: Gem, dt: number, now: number) => {
  'worklet';
  if (g.corked === 1) {
    if (g.homeAt < 0) {
      g.x = HOME.x;
      g.y = HOME.y;
      g.rot = CORK.rot;
      return;
    }
    const k = clamp01((now - g.homeAt) / g.homeDur);
    const e = easeOutBack(k);
    // it flies home along an arc that rises first, so it does not cut through the chain
    const lift = g.homeDur < 0.1 ? 0 : Math.sin(Math.PI * clamp01(k * 1.15)) * 26;
    g.x = mix(g.fromX, HOME.x, e);
    g.y = mix(g.fromY, HOME.y, e) - lift;
    let d = CORK.rot - g.fromRot;
    d -= Math.round(d / (Math.PI * 2)) * Math.PI * 2;
    g.rot = g.fromRot + d * easeOut(k);
    if (k >= 1) g.homeAt = -1;
    return;
  }
  const L = V.chain;
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  for (let i = 0; i < n; i++) {
    if (g.holdX === g.holdX) {
      // dragged: the gem chases the finger (velocity set, so release keeps the fling)
      g.vx += ((g.holdX - g.x) * 260 - g.vx * 26) * h;
      g.vy += ((g.holdY - g.y) * 260 - g.vy * 26) * h;
    } else {
      g.vy += 980 * h;
    }
    const drag = Math.exp(-0.55 * h);
    g.vx *= drag;
    g.vy *= drag;
    g.x += g.vx * h;
    g.y += g.vy * h;
    const d = Math.hypot(g.x, g.y);
    let taut = 0;
    if (d > L) {
      const nx = g.x / d;
      const ny = g.y / d;
      g.x = nx * L;
      g.y = ny * L;
      const radial = g.vx * nx + g.vy * ny;
      if (radial > 0) {
        g.vx -= nx * radial;
        g.vy -= ny * radial;
      }
      taut = 1;
    }
    // roll: hangs from a corner along the chain when taut, tumbles freely when slack
    if (taut === 1) {
      const target = -Math.atan2(g.x, g.y);
      let diff = target - g.rot;
      diff -= Math.round(diff / (Math.PI * 2)) * Math.PI * 2;
      g.w += (diff * 70 - g.w * 9) * h;
    } else {
      g.w *= Math.exp(-0.8 * h);
    }
    g.rot += g.w * h;
  }
};

// ---------------------------------------------------------------------------------------------
// The driver: the loop time plus who is in charge of it.

export const MODE = { auto: 0, file: 1, gem: 2, hold: 3 } as const;

export type Driver = {
  t: number;
  mode: number;
  last: number; // clock time of the last touch
  sound: number; // 1 while a finger started the run (cues play)
  gem: Gem;
  fileX: number; // finger position of a dragged file (reference points), NaN when not held
  fileY: number;
  fileBack: number; // 0…1 a file that was let go far away flies back to its card
  fileFromX: number;
  fileFromY: number;
  fileBackAt: number;
  kickAt: number; // clock time the spider was poked
  kickDir: number;
  dropX: number; // where a finger let the file go (NaN: the autoplay path)
  dropY: number;
};

export const makeDriver = (t: number): Driver => {
  'worklet';
  return {
    t,
    mode: MODE.auto,
    last: -10,
    sound: 0,
    gem: makeGem(!(t >= T.done && t < T.recork2 - 0.05)),
    fileX: NaN,
    fileY: NaN,
    fileBack: 1,
    fileFromX: 0,
    fileFromY: 0,
    fileBackAt: -10,
    kickAt: -10,
    kickDir: 1,
    dropX: NaN,
    dropY: NaN,
  };
};

const crossed = (a: number, b: number, mark: number) => {
  'worklet';
  return a < mark && b >= mark;
};

/** Events the frame callback answers on the JS side (sound, haptics). */
export const EV = { none: 0, pop: 1, gulp: 2, clink: 3, chime: 4 } as const;

/** Advance the driver; returns the event that happened this frame (EV.*). */
export const stepDriver = (d: Driver, dt: number, now: number, auto: boolean) => {
  'worklet';
  let ev: number = EV.none;
  const g = d.gem;
  if (d.mode === MODE.hold && now - d.last > 2) {
    d.mode = MODE.auto;
    d.sound = 0;
  }
  if (d.mode === MODE.auto && auto) {
    const before = d.t;
    let t = before + dt;
    if (t >= LOOP) {
      t -= LOOP;
      d.sound = 0;
      d.dropX = NaN;
    }
    if (crossed(before, t, T.near)) {
      popGem(g, 0.8);
      ev = EV.pop;
    }
    if (crossed(before, t, T.drop)) ev = EV.gulp;
    if (crossed(before, t, T.recork1)) {
      recorkGem(g, now, true);
      ev = EV.clink;
    }
    if (crossed(before, t, T.done)) {
      popGem(g, 1.0);
      ev = EV.chime;
    }
    if (crossed(before, t, T.recork2)) {
      recorkGem(g, now);
      ev = EV.clink;
    }
    d.t = t;
  }
  stepGem(g, dt, now);
  return ev;
};

/** Is the dragged file (or the auto ghost) close enough to the mouth to drop it? */
export const NEAR_R = 88;

/** Did a finger-dropped file land? Starts the upload from the drop mark. */
export const dropFile = (d: Driver, now: number) => {
  'worklet';
  d.t = T.drop;
  d.mode = MODE.auto;
  d.sound = 1;
  d.last = now;
  d.dropX = d.fileX;
  d.dropY = d.fileY;
  d.fileX = NaN;
};

/** Upload progress text, e.g. "29.0 of 48.2 MB". */
export const mbText = (p: number) => {
  'worklet';
  return `${(p * FILE.mb).toFixed(1)} of ${FILE.mb.toFixed(1)} MB`;
};
