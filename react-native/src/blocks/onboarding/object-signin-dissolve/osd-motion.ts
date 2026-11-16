// Object Sign-In Dissolve — every motion value. Frame t of the autoplay loop is a pure function of t (springs are solved
// analytically); the finger drives the same variables through `stepLive`. Angles in degrees unless named `rad`.
// Worklet helpers are declared above their callers.

export const LOOP = 8.0;

// ---- the loop (s) ----
export const T = {
  rest0: 0.6, // still first frame: the pin's pose
  touch: 0.6, // finger lands on the pear: scale 1 -> 1.02, the plane runs down from above the stalk
  dragA: 0.7,
  dragB: 1.8, // drag: turn 200 deg, plane to the waist
  holdEnd: 2.3, // held, the gasp is at 2.0
  gasp: 2.0,
  release: 2.3, // flick, heal, home
  wipeInA: 3.0,
  wipeInB: 3.9, // dark -> cream, from the bottom-left corner
  flickDown: 4.0, // light theme: one hard flick, the skin lets go
  wipeOutA: 5.6,
  wipeOutB: 6.5, // cream -> dark
  restEnd: 6.5, // rest 1.5 s
} as const;

// ---- poses (deg) ----
export const HERO = [-18, 162] as const; // the two designed angles
export const YAW0 = HERO[0];
export const DRAG_TURN = 200;
export const FLICK1 = 520; // deg/s released at 2.3
export const FLICK2 = 4800; // deg/s of the hard flick in the light theme
export const FRICTION = 6.02; // 1/s: 0.994 per ms (concept), the coast's decay rate

// ---- the cut: c = fraction of the object that is shell. 0 = solid, 1 = all shell ----
export const CUT = { rest: 0, finger0: 0.25, waist: 0.55, rubber: 0.35, rubberMax: 20 } as const;
// the plane's world y is `top + (bottom - top) * c` relative to the pear's centre
export const PLANE = { top: -214, bottom: 196 } as const; // 20 pt above the stalk, 20 pt below the base
export const planeY = (c: number) => {
  'worklet';
  return PLANE.top + (PLANE.bottom - PLANE.top) * c;
};

// ---- gesture ----
export const GESTURE = {
  degPerPt: 0.55, // finger dx -> yaw, 1:1
  tapSlop: 8,
  tapTime: 0.18,
  maxTurns: 3, // coast clamp
  resumeAfter: 2.0,
  settle: 0.6,
  tickEvery: 25, // pt of plane travel per haptic tick (five scan lines)
  tickGap: 0.1,
  bank: 0.012, // roll rad per deg/s ... see rollOf
  bankMax: 12,
  pitch: 0.03,
  pitchMax: 8,
  spinLo: 540,
  spinHi: 1260,
  breathe: 1.5,
  breatheHz: 0.5,
  breatheAfter: 0.6,
  press: 1.02,
} as const;

// ---- springs: { damping, stiffness, mass 1 } from the concept, as (omega, zeta) for the analytic solver ----
export type Spring = { damping: number; stiffness: number; mass: number };
export const SPRING = {
  touch: { damping: 18, stiffness: 300, mass: 1 }, // scale
  plane: { damping: 20, stiffness: 240, mass: 1 }, // the plane to the finger
  heal: { damping: 22, stiffness: 160, mass: 1 }, // the skin zips shut
  home: { damping: 16, stiffness: 90, mass: 1 }, // yaw to the nearest hero angle
  bank: { damping: 12, stiffness: 160, mass: 1 },
} as const;

export const clamp = (x: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max(x, a), b);
};
export const smoothstep = (a: number, b: number, x: number) => {
  'worklet';
  const t = clamp((x - a) / (b - a), 0, 1);
  return t * t * (3 - 2 * t);
};
export const mix = (a: number, b: number, t: number) => {
  'worklet';
  return a + (b - a) * t;
};
export const easeInOut = (t: number) => {
  'worklet';
  const x = clamp(t, 0, 1);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};

/** Damped spring x(t) from x0 with velocity v0 toward `to`: position only. */
export const springPos = (t: number, x0: number, v0: number, to: number, k: number, c: number) => {
  'worklet';
  if (t <= 0) return x0;
  const w = Math.sqrt(k);
  const z = c / (2 * w);
  const e = x0 - to;
  if (z < 1) {
    const wd = w * Math.sqrt(1 - z * z);
    return to + Math.exp(-z * w * t) * (e * Math.cos(wd * t) + ((v0 + z * w * e) / wd) * Math.sin(wd * t));
  }
  return to + Math.exp(-w * t) * (e + (v0 + w * e) * t);
};
/** Its velocity (central difference: the springs here are smooth). */
export const springVel = (t: number, x0: number, v0: number, to: number, k: number, c: number) => {
  'worklet';
  const h = 1 / 240;
  return (springPos(t + h, x0, v0, to, k, c) - springPos(t - h, x0, v0, to, k, c)) / (2 * h);
};
/** 0 -> 1 step response. */
export const stepAt = (t: number, s: Spring) => {
  'worklet';
  return springPos(t, 0, 0, 1, s.stiffness / s.mass, s.damping / s.mass);
};

/** The hero angle the pear will rest at if released at yaw with rate v: the nearest of -18 + 360 n and 162 + 360 n. */
export const heroNear = (yaw: number, v: number) => {
  'worklet';
  const proj = yaw + clamp(v / FRICTION, -GESTURE.maxTurns * 360, GESTURE.maxTurns * 360);
  const base = HERO[0] + Math.round((proj - HERO[0]) / 180) * 180;
  return base;
};

/** Skin strips off a fast spin: 0 under 540 deg/s, 1 over 1260. */
export const spinShell = (rate: number) => {
  'worklet';
  return smoothstep(GESTURE.spinLo, GESTURE.spinHi, Math.abs(rate));
};

export const rollOf = (rate: number) => {
  'worklet';
  return (clamp(rate * GESTURE.bank, -GESTURE.bankMax, GESTURE.bankMax) * Math.PI) / 180;
};
export const pitchOf = (planeVel: number) => {
  'worklet';
  return (clamp(planeVel * GESTURE.pitch, -GESTURE.pitchMax, GESTURE.pitchMax) * Math.PI) / 180;
};

/** Rubber band past the stalk / the base: (over / (1 + over / max)) with the concept's 0.35 give. */
export const rubberC = (c: number) => {
  'worklet';
  if (c >= 0 && c <= 1) return c;
  const span = PLANE.bottom - PLANE.top;
  const over = (c < 0 ? -c : c - 1) * span;
  const give = (CUT.rubber * over) / (1 + (CUT.rubber * over) / CUT.rubberMax);
  return c < 0 ? -give / span : 1 + give / span;
};

// ---- the wipe: a disc centred outside the bottom-left corner; inside it, the light page ----
export const WIPE = { R: 1100, ms: 880, cx: -80, cy: 954 } as const;
/** The radius that reaches the far corner of a W x H screen from the disc's centre (80 pt outside the bottom-left corner). */
export const wipeReach = (W: number, H: number) => Math.hypot(W + 80, H + 80) + 6;
export const wipeRadius = (p: number, R: number = WIPE.R) => {
  'worklet';
  return R * easeInOut(p);
};

// ---- timeline ----
export type Look = {
  yaw: number; // deg
  c: number; // cut fraction
  roll: number; // rad
  pitchP: number; // rad
  scale: number;
  wipeP: number; // 0 dark ... 1 light (progress of the disc)
  rate: number; // deg/s
  seam: number; // 0..1
  key: number; // rad
  zip: number; // s since the skin closed at the stalk (-1 none)
};

const FINGER = (t: number) => {
  'worklet';
  // finger's plane while held: starts at the upper third, ends at the waist
  return mix(CUT.finger0, CUT.waist, easeInOut((t - T.dragA) / (T.dragB - T.dragA)));
};

// yaw segments
const yawDrag = (t: number) => {
  'worklet';
  return YAW0 + DRAG_TURN * easeInOut((t - T.dragA) / (T.dragB - T.dragA));
};
const Y_AT_RELEASE = YAW0 + DRAG_TURN;
const TARGET1 = heroNear(Y_AT_RELEASE, FLICK1);
const FLICK2_AT = T.flickDown + 0.1; // the finger lifts 100 ms after it lands
const yawA = (t: number) => {
  'worklet';
  const k = SPRING.home.stiffness;
  const c = SPRING.home.damping;
  return springPos(t - T.release, Y_AT_RELEASE, FLICK1, TARGET1, k, c);
};
const Y_AT_FLICK2 = TARGET1; // settled long before 4.0
const TARGET2 = heroNear(Y_AT_FLICK2, FLICK2);
const yawB = (t: number) => {
  'worklet';
  return springPos(t - FLICK2_AT, Y_AT_FLICK2, FLICK2, TARGET2, SPRING.home.stiffness, SPRING.home.damping);
};

export const yawAt = (t: number) => {
  'worklet';
  if (t < T.dragA) return YAW0;
  if (t < T.release) return yawDrag(Math.min(t, T.dragB));
  if (t < FLICK2_AT) return yawA(t);
  return yawB(t);
};

const cDrag = (t: number) => {
  'worklet';
  return FINGER(Math.min(t, T.dragB)) * clamp(stepAt(t - T.touch, SPRING.plane), 0, 1.2);
};
const C_REL = CUT.waist;
const cAt = (t: number) => {
  'worklet';
  if (t < T.touch) return 0;
  if (t < T.release) return cDrag(t);
  if (t < FLICK2_AT - 0.1) return Math.max(C_REL * (1 - stepAt(t - T.release, SPRING.heal)), 0);
  if (t < T.flickDown) return 0;
  // the hard flick: the skin lets go as the spin passes 540 deg/s and zips back from the base as it falls under it; the plane
  // follows the target through a one-pole lag (the spring {22, 160, 1} is close to it), sampled so the frame stays a pure function of t
  let acc = 0;
  let wsum = 0;
  for (let j = 0; j < 36; j++) {
    const tau = t - j * 0.0125;
    const w = Math.exp(-(j * 0.0125) / 0.11);
    wsum += w;
    if (tau < T.flickDown) continue;
    const rate = Math.abs(springVel(tau - FLICK2_AT, Y_AT_FLICK2, FLICK2, TARGET2, SPRING.home.stiffness, SPRING.home.damping));
    acc += w * (tau < FLICK2_AT ? 0 : spinShell(rate));
  }
  return acc / wsum;
};

const wipeIn = (t: number) => {
  'worklet';
  return clamp((t - T.wipeInA) / (T.wipeInB - T.wipeInA), 0, 1);
};
const wipeOut = (t: number) => {
  'worklet';
  return clamp((t - T.wipeOutA) / (T.wipeOutB - T.wipeOutA), 0, 1);
};

/** The seam fades in over the first 6 % of the cut and out over the last: there is no plane to see off the pear. */
export const seamOf = (c: number) => {
  'worklet';
  return smoothstep(0.012, 0.07, c) * (1 - smoothstep(0.93, 0.99, c));
};
/** Idle life: the key light drifts +-3 deg of azimuth over one loop, sliding the highlight about 5 pt. */
export const keyDrift = (t: number) => {
  'worklet';
  return ((3 * Math.PI) / 180) * Math.sin((2 * Math.PI * t) / LOOP);
};

export const lookAt = (tt: number): Look => {
  'worklet';
  const t = ((tt % LOOP) + LOOP) % LOOP;
  const h = 1 / 120;
  const yaw = yawAt(t);
  const rate = (yawAt(t + h) - yawAt(t - h)) / (2 * h);
  const c = cAt(t);
  const planeVel = ((cAt(t + h) - cAt(t - h)) / (2 * h)) * (PLANE.bottom - PLANE.top);
  const wipeP = t < T.wipeOutA ? wipeIn(t) : 1 - wipeOut(t);
  const pressed =
    t >= T.touch && t < T.release
      ? stepAt(t - T.touch, SPRING.touch)
      : t >= T.release
        ? Math.max(1 - stepAt(t - T.release, SPRING.touch), 0)
        : 0;
  const pressed2 = t >= T.flickDown && t < FLICK2_AT ? stepAt(t - T.flickDown, SPRING.touch) : 0;
  return {
    yaw,
    c,
    roll: rollOf(rate),
    pitchP: pitchOf(planeVel),
    scale: 1 + (GESTURE.press - 1) * Math.max(pressed, pressed2),
    wipeP,
    rate,
    seam: seamOf(c),
    key: keyDrift(t),
    zip: -1,
  };
};

export const REST_LOOK: Look = lookAt(0);

// ---- the page answers the shell (pro layer 1): glow = c, lags of 0 / 60 / 120 ms ----
export const LAG = { pool: 0, word: 0.06, pill: 0.12 } as const;
export const PULSE = { pool: 0.42, word: 0.24, r0: 190, r1: 230, a: 0.06 } as const;

/** Seconds after the skin last closed at the stalk (c under 0.02 after having been over 0.2), for the pulse. Looks it up in the loop. */
export const ZIPS = [2.62, 4.78] as const;
export const zipSince = (t: number) => {
  'worklet';
  const a = t - ZIPS[0];
  const b = t - ZIPS[1];
  if (b >= 0 && b < 1) return b;
  if (a >= 0 && a < 1) return a;
  return -1;
};

// ---- the finger: live state, stepped on the UI thread each frame ----
export type Live = {
  yaw: number; // deg
  yawV: number; // deg/s
  yawTarget: number; // where a released pear is heading
  c: number;
  cV: number;
  roll: number; // rad
  rollV: number;
  pitchP: number;
  pitchV: number;
  scale: number; // 1 .. 1.02
  scaleV: number;
  q: number; // wipe progress 0 dark .. 1 light (linear; radius = wipeRadius(q))
  qTarget: number;
};

export const liveFrom = (k: Look): Live => {
  'worklet';
  return {
    yaw: k.yaw,
    yawV: k.rate,
    yawTarget: heroNear(k.yaw, k.rate),
    c: k.c,
    cV: 0,
    roll: k.roll,
    rollV: 0,
    pitchP: k.pitchP,
    pitchV: 0,
    scale: k.scale,
    scaleV: 0,
    q: k.wipeP,
    qTarget: k.wipeP > 0.5 ? 1 : 0,
  };
};

export const lookOfLive = (s: Live, t: number): Look => {
  'worklet';
  return {
    yaw: s.yaw,
    c: s.c,
    roll: s.roll,
    pitchP: s.pitchP,
    scale: s.scale,
    wipeP: s.q,
    rate: s.yawV,
    seam: seamOf(s.c),
    key: keyDrift(t),
    zip: -1,
  };
};

const spring1 = (x: number, v: number, to: number, k: number, c: number, dt: number) => {
  'worklet';
  const a = -k * (x - to) - c * v;
  const nv = v + a * dt;
  return [x + nv * dt, nv] as const;
};

/**
 * One step of the live physics. `held`: the finger owns yaw (set by the pan) and `fingerC` is where it holds the plane.
 * Reduce Motion: no coast (yaw eases to the hero angle), no bank, no spin dissolve.
 */
export const stepLive = (s: Live, dt: number, held: boolean, fingerC: number, reduce: boolean, breathe: number): Live => {
  'worklet';
  const n = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / n;
  let { yaw, yawV, c, cV, roll, rollV, pitchP, pitchV, scale, scaleV } = s;
  const span = PLANE.bottom - PLANE.top;
  for (let i = 0; i < n; i++) {
    if (!held) {
      if (reduce) {
        const r = spring1(yaw, yawV, s.yawTarget, 900, 60, h);
        yaw = r[0];
        yawV = r[1];
      } else {
        const r = spring1(yaw, yawV, s.yawTarget, SPRING.home.stiffness, SPRING.home.damping, h);
        yaw = r[0];
        yawV = r[1];
      }
    }
    const spin = reduce ? 0 : spinShell(yawV);
    const finger = held ? rubberC(fingerC) + breathe / span : 0;
    const target = Math.max(finger, spin);
    const sp = target > c ? SPRING.plane : SPRING.heal;
    const rc = spring1(c, cV, target, sp.stiffness, sp.damping, h);
    c = rc[0];
    cV = rc[1];
    const rr = spring1(roll, rollV, reduce ? 0 : rollOf(yawV), SPRING.bank.stiffness, SPRING.bank.damping, h);
    roll = rr[0];
    rollV = rr[1];
    const rp = spring1(pitchP, pitchV, reduce ? 0 : pitchOf(cV * span), SPRING.bank.stiffness, SPRING.bank.damping, h);
    pitchP = rp[0];
    pitchV = rp[1];
    const rs = spring1(scale, scaleV, held && !reduce ? GESTURE.press : 1, SPRING.touch.stiffness, SPRING.touch.damping, h);
    scale = rs[0];
    scaleV = rs[1];
  }
  const dq = s.qTarget - s.q === 0 ? 0 : Math.sign(s.qTarget - s.q) * (dt / (WIPE.ms / 1000));
  let q = s.q + dq;
  if ((dq > 0 && q > s.qTarget) || (dq < 0 && q < s.qTarget)) q = s.qTarget;
  return { yaw, yawV, yawTarget: s.yawTarget, c, cV, roll, rollV, pitchP, pitchV, scale, scaleV, q, qTarget: s.qTarget };
};
