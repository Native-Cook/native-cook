// Clean Air Dial — the loop, the fan's phase, the dust and the touch driver.
//
// G is the fan speed as a count of green ticks; F is how much of the room's dust the fan has
// pulled out (0 = the pin's 012, up to 0.9; below 0 the room gets dustier); phi is how far the air
// in the dial has turned.

import { LEVELS } from './cad-theme';

export const LOOP = 9.0;
export const RESUME = 2;
const TAU = Math.PI * 2;

export const T = {
  up: [1.0, 1.7], // the dial is turned up to High …
  pull: [1.8, 4.4], // … and the dust spirals into the core
  down: [5.0, 5.6], // back to Medium …
  back: [5.8, 8.4], // … and dust drifts back in from the edge
} as const;
export const F_HIGH = 0.62;

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
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function easeOutBack(x: number) {
  'worklet';
  const p = clamp01(x);
  const c1 = 1.4;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(p - 1, 3) + c1 * Math.pow(p - 1, 2);
}

/** The air's turning speed (rad/s) at a fan speed of g ticks. */
export function omegaAt(g: number) {
  'worklet';
  if (g <= LEVELS[0].ticks) return (LEVELS[0].omega * Math.max(0, g)) / LEVELS[0].ticks;
  for (let i = 0; i < LEVELS.length - 1; i++) {
    const a = LEVELS[i];
    const b = LEVELS[i + 1];
    if (g <= b.ticks) return mix(a.omega, b.omega, (g - a.ticks) / (b.ticks - a.ticks));
  }
  return LEVELS[LEVELS.length - 1].omega;
}
/** The level nearest to g ticks. */
export function levelAt(g: number) {
  'worklet';
  let best = 0;
  for (let i = 1; i < LEVELS.length; i++) if (Math.abs(g - LEVELS[i].ticks) < Math.abs(g - LEVELS[best].ticks)) best = i;
  return best;
}

export function loopG(t: number) {
  'worklet';
  const lo = LEVELS[1].ticks;
  const hi = LEVELS[2].ticks;
  if (t < T.down[0]) return mix(lo, hi, easeOutBack(seg(t, T.up[0], T.up[1])));
  return mix(hi, lo, easeInOut(seg(t, T.down[0], T.down[1])));
}
export function loopF(t: number) {
  'worklet';
  if (t < T.back[0]) return F_HIGH * easeInOut(seg(t, T.pull[0], T.pull[1]));
  return F_HIGH * (1 - easeInOut(seg(t, T.back[0], T.back[1])));
}

// The loop's phase: the integral of the air's speed, scaled so the loop turns a whole number of times.
const STEP = 0.01;
const PHI: number[] = (() => {
  const out = [0];
  let acc = 0;
  for (let i = 1; i <= Math.round(LOOP / STEP); i++) {
    acc += omegaAt(loopG((i - 0.5) * STEP)) * STEP;
    out.push(acc);
  }
  const turns = Math.max(1, Math.round(acc / TAU));
  return out.map((v) => (v * turns * TAU) / acc);
})();
export function loopPhi(t: number) {
  'worklet';
  const x = Math.max(0, Math.min(LOOP, t)) / STEP;
  const i = Math.min(PHI.length - 2, Math.floor(x));
  return mix(PHI[i], PHI[i + 1], x - i);
}

export function pmOf(f: number) {
  'worklet';
  return Math.max(0, 12 - 13 * f);
}

export type Frame = {
  amb: number;
  g: number; // green ticks
  phi: number;
  f: number;
  ret: number; // 1 while dust is coming back in
  power: number; // 0…1
  timer: number; // 0…1 (on)
  timerAt: number; // seconds since the timer changed (for its hand)
  scenes: number; // 0…1 (on)
  auto: number; // 0…1 (on)
  drag: number; // the finger's tick on the ring (−1 none)
  press: number; // a chip held (index 0…5, −1 none) …
  pressK: number;
  hush: number; // 0…1: the dial dims while the power is off
};

export function frameAt(t: number, phi0: number): Frame {
  'worklet';
  return {
    amb: t,
    g: loopG(t),
    phi: loopPhi(t) + phi0,
    f: loopF(t),
    ret: t >= T.down[0] ? 1 : 0,
    power: 1,
    timer: 0,
    timerAt: 10,
    scenes: 1,
    auto: 0,
    drag: -1,
    press: -1,
    pressK: 0,
    hush: 0,
  };
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  phi0: number;
  g: number;
  gv: number;
  level: number;
  dragging: number;
  dragG: number;
  f: number;
  ret: number;
  phi: number;
  power: boolean;
  powerAt: number;
  timer: boolean;
  timerAt: number;
  scenes: boolean;
  scenesAt: number;
  auto: boolean;
  autoAt: number;
  press: number;
  pressAt: number;
  releaseAt: number;
  pressLast: number;
  homing: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return {
    t,
    mode: 0,
    last: -10,
    amb: t,
    phi0: 0,
    g: LEVELS[1].ticks,
    gv: 0,
    level: 1,
    dragging: 0,
    dragG: 0,
    f: 0,
    ret: 0,
    phi: 0,
    power: true,
    powerAt: -10,
    timer: false,
    timerAt: -10,
    scenes: true,
    scenesAt: -10,
    auto: false,
    autoAt: -10,
    press: -1,
    pressAt: -10,
    releaseAt: -10,
    pressLast: 0,
    homing: 0,
  };
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  d.homing = 0;
  if (d.mode === 2) return;
  const f = frameAt(d.t, d.phi0);
  d.mode = 2;
  d.g = f.g;
  d.gv = 0;
  d.level = d.t >= T.up[0] && d.t < T.down[0] ? 2 : 1;
  d.f = f.f;
  d.ret = f.ret;
  d.phi = f.phi;
  d.power = true;
  d.timer = false;
  d.scenes = true;
  d.auto = false;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  // the auto mode picks the speed from the air
  if (d.auto && d.power && d.dragging === 0) d.level = pmOf(d.f) > 7 ? 2 : 1;
  // back home after a while: Medium, on, timer off, scenes on
  if (auto && d.dragging === 0 && d.press < 0 && now - d.last >= RESUME) {
    if (d.homing === 0) {
      d.homing = 1;
      d.level = 1;
      if (!d.power) {
        d.power = true;
        d.powerAt = now;
      }
      if (d.timer) {
        d.timer = false;
        d.timerAt = now;
      }
      if (!d.scenes) {
        d.scenes = true;
        d.scenesAt = now;
      }
      if (d.auto) {
        d.auto = false;
        d.autoAt = now;
      }
    }
  }
  // the green ticks follow the speed on a spring (or the finger)
  const target = d.dragging === 1 ? d.dragG : d.power ? LEVELS[d.level].ticks : 0;
  if (d.dragging === 1) {
    d.g += (target - d.g) * Math.min(1, dt * 22);
    d.gv = 0;
  } else {
    const k = 140;
    const c = 2 * 0.62 * Math.sqrt(k);
    d.gv += (-k * (d.g - target) - c * d.gv) * dt;
    d.g += d.gv * dt;
  }
  // dust: pulled out while the fan beats the room, drifting back in when it doesn't
  const fTarget = d.power ? LEVELS[levelAt(Math.max(0, d.g))].target : -0.5;
  const om = d.power ? omegaAt(Math.max(0, d.g)) : 0;
  if (fTarget > d.f) {
    d.f = Math.min(fTarget, d.f + (fTarget - d.f + 0.05) * 0.24 * om * dt);
    d.ret = 0;
  } else if (fTarget < d.f) {
    d.f = Math.max(fTarget, d.f - (d.f - fTarget + 0.05) * 0.32 * dt);
    d.ret = 1;
  }
  d.phi += om * dt;
  if (d.homing === 1 && Math.abs(d.g - LEVELS[1].ticks) < 0.05 && Math.abs(d.gv) < 0.2 && Math.abs(d.f) < 0.004) {
    d.mode = 0;
    d.t = 0;
    d.phi0 = d.phi % TAU;
    d.homing = 0;
  }
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t, d.phi0);
  const pressK = d.press >= 0 ? smooth((now - d.pressAt) / 0.1) : 1 - smooth((now - d.releaseAt) / 0.16);
  const fade = (on: boolean, at: number, dur: number) => (on ? smooth((now - at) / dur) : 1 - smooth((now - at) / dur));
  return {
    amb: d.amb,
    g: d.g,
    phi: d.phi,
    f: d.f,
    ret: d.ret,
    power: fade(d.power, d.powerAt, 0.35),
    timer: fade(d.timer, d.timerAt, 0.25),
    timerAt: now - d.timerAt,
    scenes: fade(d.scenes, d.scenesAt, 0.25),
    auto: fade(d.auto, d.autoAt, 0.25),
    drag: d.dragging === 1 ? d.dragG : -1,
    press: d.press >= 0 ? d.press : d.pressLast,
    pressK,
    hush: 1 - fade(d.power, d.powerAt, 0.35),
  };
}
