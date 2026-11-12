// Glass Pay Morph — who drives the timeline: the loop clock (autoPlay) or the finger.
//
// Under a finger the scene is still sceneAt(τ) of the same timeline, with τ moved by the gesture:
// a vertical drag scrubs the collapse 1:1 (150 pt = card → pill), a tap plays the collapse or the
// expand, a horizontal drag on the card tilts it, a light can be picked up and thrown. Springs here
// are integrated per frame (touch is not part of the recorded loop). Two seconds after the last
// touch the timeline returns to rest (back along the collapse, or through the expand from the pill),
// the offsets spring home, and the loop restarts at t = 0 with its idle drift fading back in.

import { type Mods, LOOP, progressOf, T, wrap } from './gpm-motion';

export const TOUCH = {
  scrubTravel: 150, // pt of vertical drag for the whole collapse
  rubber: 0.12, // p past either end
  rubberFall: 0.55,
  commitSpeed: 600, // pt/s
  axisLock: 10, // pt
  yawPerPt: 1 / 2.2, // deg per pt
  yawMax: 28,
  parallax: -0.12, // pt of light shift per pt of yaw drag
  idle: 2, // s after the last touch
  driftIn: 0.8, // s for the idle drift to fade back in
  driftOut: 0.35,
  pressIn: 0.97,
  bandScale: 0.45, // glass scale per unit of rubber-band p
};

// spring (stiffness, damping) from response (s) and damping ratio, mass 1
function sp(response: number, ratio: number) {
  const k = Math.pow((2 * Math.PI) / response, 2);
  return { k, c: 2 * ratio * Math.sqrt(k) };
}
const S_TAU = { k: 170, c: 20 }; // the morph spring (plan: stiffness 170, damping 20, mass 1), in p units
const S_YAW = sp(0.5, 0.55);
const S_PRESS = sp(0.22, 0.85);
const S_LIGHT = sp(0.55, 0.5);

export type Driver = {
  mode: number; // 0 autoPlay loop, 1 touch
  t: number; // loop time (mode 0)
  gain: number; // idle drift gain
  tau: number; // timeline position under touch
  tauV: number;
  play: number; // 0 spring to tauTarget, 1 play forward in real time to playEnd
  tauTarget: number;
  playEnd: number;
  holdAfter: number; // 1: when the spring settles on the pill, play the hold (dock, pulse, caption)
  nu: number; // smoothed τ speed, stretches the lights' trails
  yaw: number;
  yawV: number;
  yawTarget: number;
  press: number;
  pressV: number;
  pressTarget: number;
  band: number; // how far a scrub is pulled past either end, in p (− past the card, + past the pill)
  bandV: number;
  aOff: number[]; // x, y, vx, vy
  rOff: number[];
  held: number; // 0 none, 1 scrub, 2 yaw, 3 amber, 4 red, 5 undecided
  last: number; // clock of the last touch
};

export function makeDriver(t = 0): Driver {
  return {
    mode: 0,
    t,
    gain: 1,
    tau: t,
    tauV: 0,
    play: 0,
    tauTarget: t,
    playEnd: t,
    holdAfter: 0,
    nu: 1,
    yaw: 0,
    yawV: 0,
    yawTarget: 0,
    press: 1,
    pressV: 0,
    pressTarget: 1,
    band: 0,
    bandV: 0,
    aOff: [0, 0, 0, 0],
    rOff: [0, 0, 0, 0],
    held: 0,
    last: 0,
  };
}

function springStep(x: number, v: number, target: number, s: { k: number; c: number }, dt: number) {
  'worklet';
  // semi-implicit Euler in 4 sub-steps
  let xx = x;
  let vv = v;
  const h = dt / 4;
  for (let i = 0; i < 4; i++) {
    vv += (-s.k * (xx - target) - s.c * vv) * h;
    xx += vv * h;
  }
  return [xx, vv];
}

export function rubber(over: number, limit: number, fall: number) {
  'worklet';
  if (over <= 0) return 0;
  return (limit * over) / (over + limit / fall);
}

export function isRest(tau: number) {
  'worklet';
  const t = wrap(tau);
  return t <= T.collapse0 + 1e-4 || t >= T.expand1 - 1e-4;
}

/** The finger takes over from whatever the loop was showing. */
export function takeOver(d: Driver, now: number) {
  'worklet';
  if (d.mode === 0) {
    d.mode = 1;
    d.tau = d.t;
    d.tauV = 0;
    d.play = 0;
    d.tauTarget = d.t;
    d.holdAfter = 0;
    // a timeline caught mid-phase keeps playing to its next rest, unless the finger scrubs it
    const t = wrap(d.t);
    if (t > T.collapse0 && t < T.paidHold) {
      d.play = 1;
      d.playEnd = T.paidHold;
    } else if (t > T.expand0 && t < T.expand1) {
      d.play = 1;
      d.playEnd = T.expand1;
    }
  }
  d.last = now;
}

/** Tap: collapse from the card, expand from the pill, reverse a collapse in flight. */
export function toggle(d: Driver) {
  'worklet';
  const t = wrap(d.tau);
  if (isRest(t)) {
    d.tau = T.collapse0;
    d.play = 1;
    d.playEnd = T.paidHold;
  } else if (t >= T.collapse1 && t < T.expand0) {
    d.tau = T.expand0;
    d.play = 1;
    d.playEnd = T.expand1;
  } else if (t > T.collapse0 && t < T.collapse1) {
    d.play = 0;
    d.tauTarget = T.collapse0;
    d.tauV = 0;
  }
}

/** Scrub: the collapse follows the finger, p = p0 − dy / 150, rubber-banded past either end. */
export function scrubTo(d: Driver, p0: number, dy: number) {
  'worklet';
  let p = p0 - dy / TOUCH.scrubTravel;
  if (p < 0) p = -rubber(-p, TOUCH.rubber, TOUCH.rubberFall);
  else if (p > 1) p = 1 + rubber(p - 1, TOUCH.rubber, TOUCH.rubberFall);
  d.play = 0;
  d.tau = T.collapse0 + (T.collapse1 - T.collapse0) * Math.max(0, Math.min(1, p));
  d.tauTarget = d.tau;
  d.band = p < 0 ? p : p > 1 ? p - 1 : 0;
  d.bandV = 0;
  return p;
}

/** Scrub released: commit on speed, else to the nearer end; the spring carries the finger's speed. */
export function scrubEnd(d: Driver, p: number, vy: number) {
  'worklet';
  const vp = -vy / TOUCH.scrubTravel;
  const toPill = Math.abs(vy) > TOUCH.commitSpeed ? vp > 0 : p > 0.5;
  d.play = 0;
  d.tauTarget = toPill ? T.collapse1 : T.collapse0;
  d.tauV = vp * (T.collapse1 - T.collapse0);
  d.holdAfter = toPill ? 1 : 0;
}

/**
 * One frame. Returns an event for haptics: 0 none, 1 docked, 2 pulse, 3 crossed half-way.
 */
export function stepDriver(d: Driver, dt: number, now: number, auto: boolean): number {
  'worklet';
  if (d.mode === 0) {
    if (auto) d.t = wrap(d.t + dt);
    d.gain = Math.min(1, d.gain + dt / TOUCH.driftIn);
    d.nu = 1;
    return 0;
  }
  const prev = d.tau;
  const held = d.held;
  // τ
  if (held !== 1) {
    if (d.play === 1) {
      // the lights' slow first steps (0.30–0.60) play faster under a tap, so a tap answers at once
      const k = Math.min(1, Math.max(0, (d.tau - 0.42) / 0.2));
      d.tau = Math.min(d.playEnd, d.tau + dt * (1 + 1.2 * (1 - k * k * (3 - 2 * k))));
      if (d.tau >= d.playEnd) {
        d.play = 0;
        d.tauTarget = d.playEnd;
        d.tauV = 0;
      }
    } else {
      // the morph spring works in p units: scale by the collapse length
      const L = T.collapse1 - T.collapse0;
      const r = springStep(d.tau / L, d.tauV / L, d.tauTarget / L, S_TAU, dt);
      d.tau = r[0] * L;
      d.tauV = r[1] * L;
      if (d.holdAfter === 1 && Math.abs(d.tau - T.collapse1) < 0.004 && Math.abs(d.tauV) < 0.05) {
        d.holdAfter = 0;
        d.tau = T.collapse1;
        d.play = 1;
        d.playEnd = T.paidHold;
      }
    }
  }
  const speed = dt > 0 ? (d.tau - prev) / dt : 0;
  d.nu += (Math.max(-2.5, Math.min(2.5, speed)) - d.nu) * Math.min(1, dt * 30);
  if (Math.abs(d.nu) < 0.02) d.nu = 0;

  // tilt, press, thrown lights
  if (held !== 2) {
    const r = springStep(d.yaw, d.yawV, 0, S_YAW, dt);
    d.yaw = r[0];
    d.yawV = r[1];
  }
  if (held !== 1) {
    const b = springStep(d.band, d.bandV, 0, S_PRESS, dt);
    d.band = b[0];
    d.bandV = b[1];
  }
  const pr = springStep(d.press, d.pressV, d.pressTarget, S_PRESS, dt);
  d.press = pr[0];
  d.pressV = pr[1];
  if (held !== 3) {
    for (let i = 0; i < 2; i++) {
      const r = springStep(d.aOff[i], d.aOff[i + 2], 0, S_LIGHT, dt);
      d.aOff[i] = r[0];
      d.aOff[i + 2] = r[1];
    }
  }
  if (held !== 4) {
    for (let i = 0; i < 2; i++) {
      const r = springStep(d.rOff[i], d.rOff[i + 2], 0, S_LIGHT, dt);
      d.rOff[i] = r[0];
      d.rOff[i + 2] = r[1];
    }
  }
  d.gain = Math.max(0, d.gain - dt / TOUCH.driftOut);

  // two seconds after the last touch: back to rest, then hand the clock back to the loop
  if (held === 0 && now - d.last > TOUCH.idle) {
    const t = wrap(d.tau);
    if (d.play === 0 && t > T.collapse0 + 1e-4 && t < T.collapse1 && d.holdAfter === 0) d.tauTarget = T.collapse0;
    else if (d.play === 0 && t >= T.collapse1 && t < T.expand0) {
      d.tau = T.expand0;
      d.play = 1;
      d.playEnd = T.expand1;
    }
    const settled =
      isRest(d.tau) &&
      d.play === 0 &&
      Math.abs(d.tauV) < 0.02 &&
      Math.abs(d.yaw) < 0.05 &&
      Math.abs(d.press - 1) < 0.002 &&
      Math.abs(d.band) < 0.002 &&
      Math.hypot(d.aOff[0], d.aOff[1], d.rOff[0], d.rOff[1]) < 0.2;
    if (settled) {
      d.mode = 0;
      d.t = 0;
      d.gain = 0;
      d.yaw = 0;
      d.aOff = [0, 0, 0, 0];
      d.rOff = [0, 0, 0, 0];
      d.press = 1;
      d.nu = 1;
    }
  }

  // events
  const a = wrap(prev);
  const b = wrap(d.tau);
  if (a < T.collapse1 && b >= T.collapse1 && b < T.expand0) return 1;
  if (a < T.pulse && b >= T.pulse && b < T.expand0) return 2;
  if (held === 1 && (progressOf(a) - 0.5) * (progressOf(b) - 0.5) < 0) return 3;
  return 0;
}

export function modsOf(d: Driver): Mods {
  'worklet';
  return {
    nu: d.mode === 0 ? 1 : d.nu,
    driftGain: d.gain,
    yaw: d.yaw,
    // a scrub pulled past an end squashes the glass instead of moving the timeline: past the card it
    // swells (up to 5 %), past the pill it tightens
    press: d.press * (1 - TOUCH.bandScale * d.band),
    parallax: TOUCH.parallax * (d.yaw / TOUCH.yawPerPt),
    aOff: d.aOff,
    rOff: d.rOff,
  };
}

export function timeOf(d: Driver) {
  'worklet';
  return d.mode === 0 ? d.t : d.tau;
}

export { LOOP };
