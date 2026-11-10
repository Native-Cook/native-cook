// Filament Bulb Dimmer — the bulb's physics and the loop. Helpers first: a worklet captures only
// what is declared above it.
//
// The bulb hangs from the card on a cord. Its state is polar about the cord's anchor: the swing θ
// (rad, positive to the right), the cord's stretch s (pt; positive when a pull stretches the
// switch's spring, negative when the bulb is lifted and the cord goes slack) and the twist β about
// the bulb's own axis (rad). The light is the filament's heat (0 … 1, the dial's level once warm),
// which lags the switch like tungsten: quick to heat, slower to cool, so switching off leaves a
// fading orange afterglow.

import { BULB, DEFAULT_LEVEL, DEFAULT_SWATCH, GAMMA, type Layout } from './fbd-theme';

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
export function smooth(x: number) {
  'worklet';
  return x * x * (3 - 2 * x);
}

export const LOOP = 10;
/** the pendulum: period 1.6 s, its damping ratio */
export const SWING_W = (2 * Math.PI) / 1.6;
export const SWING_Z = 0.14;
/** the twist about the cord: slow, lightly damped */
export const TWIST_W = (2 * Math.PI) / 2.4;
export const TWIST_Z = 0.12;
/** the pull switch's spring */
export const PULL_W = 22;
export const PULL_Z = 0.32;
export const PULL_MAX = 30;
export const PULL_CLICK = 16;
export const LIFT_MAX = 44;
/** gravity on a slack cord, pt/s² */
export const GRAV = 3400;
/** tungsten: seconds to heat, to cool */
export const HEAT_UP = 0.09;
export const COOL = 0.22;
export const WARM = 0.45;

/** a damped oscillator released from x0 with speed v0, τ seconds later */
export function osc(tau: number, x0: number, v0: number, w: number, z: number) {
  'worklet';
  const wd = w * Math.sqrt(1 - z * z);
  const e = Math.exp(-z * w * tau);
  return e * (x0 * Math.cos(wd * tau) + ((v0 + z * w * x0) / wd) * Math.sin(wd * tau));
}

/** the filament's heat after a switch on: a warm-up with a faint flicker */
export function warmUp(x: number) {
  'worklet';
  return easeOut(x) * (1 - 0.14 * Math.sin(x * 38) * (1 - x));
}

/** the light the filament gives for its heat: the dial's perceived level ↦ output */
export function lumOf(heat: number) {
  'worklet';
  return Math.pow(Math.max(0, heat), GAMMA);
}
/** the filament's own radiance (HDR): output plus an ember that shows when it is nearly cold */
export function radianceOf(heat: number) {
  'worklet';
  return 30 * lumOf(heat) + 1.4 * heat * heat;
}

/** the air's idle sway: whole cycles per loop, so the loop has no seam */
export function idleSwing(t: number) {
  'worklet';
  return 0.004 * Math.sin((2 * Math.PI * t) / LOOP) + 0.002 * Math.sin((4 * Math.PI * t) / LOOP + 1);
}
export function idleTwist(t: number) {
  'worklet';
  return 0.16 * Math.sin((2 * Math.PI * t) / LOOP);
}

export type Pose = { hx: number; hy: number; fx: number; fy: number; tilt: number; sag: number };

/** where the socket's top and the filament are for a swing and stretch, and how the cord sags */
export function poseOf(L: Layout, th: number, s: number): Pose {
  'worklet';
  const len = Math.max(8, L.cord + s);
  const sn = Math.sin(th);
  const cs = Math.cos(th);
  const hx = L.anchorX + len * sn;
  const hy = L.anchorY + len * cs;
  const f = BULB.filament * L.R;
  // a slack cord is longer than the span: a parabola of the same length sags by h
  const slack = Math.max(0, L.cord - len);
  const sag = Math.sqrt((3 * len * slack) / 8);
  return { hx, hy, fx: hx + f * sn, fy: hy + f * cs, tilt: th, sag };
}

/** a point given in the bulb's frame (R units across its axis and down it) on the screen */
export function bulbPoint(L: Layout, p: Pose, across: number, down: number) {
  'worklet';
  const sn = Math.sin(p.tilt);
  const cs = Math.cos(p.tilt);
  return { x: p.hx + (cs * across + sn * down) * L.R, y: p.hy + (-sn * across + cs * down) * L.R };
}

/* ---------------------------------------------------------------- the loop (10 s)
 0.4  a finger takes the globe low on its right and draws it aside and up        → 1.3 lets go
      the bulb swings back, twisting on its cord; the cord snaps taut and bounces
 2.2  the dial down to 18 % (an ember)   3.35 up to 100 %
 4.8  Ice   5.8 Rose   6.8 Warm (the light, the band, the switch take the colour)
 7.35 the dial back to 78 %
 8.1  the bulb pulled down: the switch clicks off at 16 pt   8.42 let go
 9.15 the On switch: the filament warms up                    9.6 … 10 at rest = frame 0 */
export const T = {
  grab: [0.6, 1.3],
  dimDown: [2.2, 3.1],
  dimUp: [3.35, 4.25],
  colours: [
    [4.8, 1],
    [5.8, 4],
    [6.8, 2],
  ],
  dimBack: [7.35, 7.95],
  pull: [8.12, 8.42],
  toggle: 9.15,
} as const;
const SWING0 = 0.32;
const TWIST0 = 0.55;
const LIFT = -8;
const LOW = 0.18;
const PULL_TO = 24;
/** when the scripted pull passes the click */
export const T_OFF = T.pull[0] + (T.pull[1] - T.pull[0]) * (1 - Math.cbrt(1 - PULL_CLICK / PULL_TO));
/** the grab point and the pull point, in the bulb's frame */
export const GRAB_AT = { across: 0.42, down: 2.95 };
export const PULL_AT = { across: 0, down: 3.55 };

export type Frame = {
  th: number;
  s: number;
  tw: number;
  on: number;
  heat: number;
  level: number;
  lead: number;
  trail: number;
  finger: number;
  fingerAt: number; // 0 the grab point, 1 the dial's knob, 2 a dot, 3 the switch, 4 the pull point
  fingerDot: number;
  fingerPress: number;
};

function levelAtT(t: number) {
  'worklet';
  return (
    DEFAULT_LEVEL +
    (LOW - DEFAULT_LEVEL) * easeInOut(seg(t, T.dimDown[0], T.dimDown[1])) +
    (1 - LOW) * easeInOut(seg(t, T.dimUp[0], T.dimUp[1])) +
    (DEFAULT_LEVEL - 1) * easeInOut(seg(t, T.dimBack[0], T.dimBack[1]))
  );
}

/** the swing and twist of the loop alone (no idle sway), for the dust's memory of the bulb */
export function motionAt(t: number) {
  'worklet';
  const fade = 1 - smooth(seg(t, 8.8, 9.8));
  const [ga, gb] = T.grab;
  let th = 0;
  let tw = 0;
  let s = 0;
  if (t >= ga && t < gb) {
    const x = easeInOut(seg(t, ga, gb));
    th = SWING0 * x;
    tw = TWIST0 * x;
    s = LIFT * x;
  } else if (t >= gb) {
    const tau = t - gb;
    th = osc(tau, SWING0, 0, SWING_W, SWING_Z);
    tw = osc(tau, TWIST0, 0, TWIST_W, TWIST_Z);
    // the slack cord drops the bulb, then the switch's spring catches it
    const t0 = Math.sqrt((-2 * LIFT) / GRAV);
    s = tau < t0 ? LIFT + 0.5 * GRAV * tau * tau : osc(tau - t0, 0, GRAV * t0, PULL_W, PULL_Z);
  }
  const [pa, pb] = T.pull;
  if (t >= pa && t < pb) s += PULL_TO * easeOut(seg(t, pa, pb));
  else if (t >= pb) s += osc(t - pb, PULL_TO, 0, PULL_W, PULL_Z * 1.1);
  return { th: th * fade, tw: tw * fade, s: s * fade };
}

export function frameAt(t: number): Frame {
  'worklet';
  const m = motionAt(t);
  const level = levelAtT(t);
  // the colour: the worm's leading edge runs ahead, its tail follows
  let lead = DEFAULT_SWATCH;
  let trail = DEFAULT_SWATCH;
  let from = DEFAULT_SWATCH;
  for (let k = 0; k < T.colours.length; k++) {
    const at = T.colours[k][0];
    const to = T.colours[k][1];
    lead += (to - from) * easeOut(seg(t, at + 0.03, at + 0.36));
    trail += (to - from) * easeInOut(seg(t, at + 0.08, at + 0.62));
    from = to;
  }
  // the switch and the filament's heat
  const on = t >= T_OFF && t < T.toggle ? 0 : 1;
  let heat = levelAtT(t - 0.06);
  if (t >= T_OFF && t < T.toggle) heat = levelAtT(T_OFF) * Math.exp(-(t - T_OFF) / COOL);
  else if (t >= T.toggle) heat = levelAtT(t) * warmUp(seg(t, T.toggle, T.toggle + WARM));

  // the scripted finger
  let finger = 0;
  let fingerAt = 0;
  let fingerDot = 0;
  let fingerPress = 0;
  const grab = easeOut(seg(t, T.grab[0] - 0.18, T.grab[0] - 0.02)) * (1 - easeOut(seg(t, T.grab[1], T.grab[1] + 0.16)));
  if (grab > finger) {
    finger = grab;
    fingerAt = 0;
    fingerPress = grab;
  }
  const drags = [T.dimDown, T.dimUp, T.dimBack];
  for (let k = 0; k < drags.length; k++) {
    const a = drags[k][0];
    const b = drags[k][1];
    const f = easeOut(seg(t, a - 0.22, a - 0.06)) * (1 - easeOut(seg(t, b + 0.05, b + 0.22)));
    if (f > finger) {
      finger = f;
      fingerAt = 1;
      fingerPress = f;
    }
  }
  for (let k = 0; k < T.colours.length; k++) {
    const at = T.colours[k][0];
    const f = easeOut(seg(t, at - 0.18, at - 0.04)) * (1 - easeOut(seg(t, at + 0.14, at + 0.3)));
    if (f > finger) {
      finger = f;
      fingerAt = 2;
      fingerDot = T.colours[k][1];
      fingerPress = easeOut(seg(t, at - 0.04, at + 0.05)) * (1 - easeOut(seg(t, at + 0.1, at + 0.22)));
    }
  }
  const pullF = easeOut(seg(t, T.pull[0] - 0.18, T.pull[0] - 0.03)) * (1 - easeOut(seg(t, T.pull[1], T.pull[1] + 0.16)));
  if (pullF > finger) {
    finger = pullF;
    fingerAt = 4;
    fingerPress = pullF;
  }
  const tg = easeOut(seg(t, T.toggle - 0.18, T.toggle - 0.04)) * (1 - easeOut(seg(t, T.toggle + 0.14, T.toggle + 0.3)));
  if (tg > finger) {
    finger = tg;
    fingerAt = 3;
    fingerPress = easeOut(seg(t, T.toggle - 0.04, T.toggle + 0.05)) * (1 - easeOut(seg(t, T.toggle + 0.1, T.toggle + 0.22)));
  }
  return { th: m.th + idleSwing(t), s: m.s, tw: m.tw + idleTwist(t), on, heat, level, lead, trail, finger, fingerAt, fingerDot, fingerPress };
}
