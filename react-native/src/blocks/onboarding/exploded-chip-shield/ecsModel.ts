/*
 Timing, layout, copy and colours of the Exploded Chip Shield, and the frame it draws (a port of
 ExplodedChipShieldModel.swift). Every value is a function of the loop time (autoPlay) or of the
 open amount q (touch), so the stacks match. Helpers sit above the worklets that call them.
*/

export const LOOP = 7.0;

// Copy
export const BRAND = 'Ferrin';
export const SKIP = 'Skip';
export const EYEBROW = 'VAULT-GRADE PROTECTION';
export const TITLE = 'Every layer,\nencrypted.';
export const BODY = 'Your vault is sealed on this device\nand opens only for you.';
export const CTA = 'Continue';

/** Callouts, top to bottom. */
export const CALLOUTS = [
  { layer: 5, title: 'Alloy shell', caption: 'Tamper-sealed' },
  { layer: 4, title: 'Cipher mesh', caption: '256-bit cipher' },
  { layer: 2, title: 'Key enclave', caption: 'On-device keys' },
];

// Colours
export const INK = 0x14171b;
export const GREY = 0x6b7280;
export const CAPTION = 0x7a818c;
export const TEAL = 0x1e9c8a;
export const GLOW = 0x5fd3c2;
export const LINE = 0x9aa3ad;
export const DOT_OFF = 0xc9ced5;
export const BG_TOP = 0xffffff;
export const BG_BOTTOM = 0xeef1f4;

// Layout (pt)
export const UNIT = 150; // side of the lid
export const SQUASH = 0.58; // isometric: y scale after the 45° turn
export const REST_YAW = Math.PI / 4;
export const SHIFT = 40; // the stack slides left this much as it opens
export const TEXT_BLOCK = 280; // from the top of the eyebrow to the bottom safe edge
export const CLOSED_LIFT = 206; // anchor of the closed stack above the text block
export const OPEN_DROP = 106; // the anchor moves down this much when open
export const DRAG_SPAN = 240; // a drag of 240 pt opens the stack fully
export const CLOSED_ZOOM = 0.16; // the closed chip is drawn 16 % larger

export type Layer = { side: number; radius: number; thick: number; zClosed: number; zOpen: number; rank: number; fall: number };

/** Bottom to top: base, tray, core, frosted ring, glass sheet, lid. */
export const LAYERS: Layer[] = [
  { side: 1.0, radius: 0.22, thick: 9, zClosed: 0, zOpen: 0, rank: 0, fall: 0 },
  { side: 0.9, radius: 0.18, thick: 7, zClosed: 9, zOpen: 50, rank: 1.0, fall: 4.7 },
  { side: 0.74, radius: 0.15, thick: 5, zClosed: 9, zOpen: 76, rank: 0.75, fall: 4.76 },
  { side: 0.84, radius: 0.17, thick: 8, zClosed: 9, zOpen: 128, rank: 0.5, fall: 4.82 },
  { side: 0.92, radius: 0.2, thick: 2.5, zClosed: 9, zOpen: 184, rank: 0.25, fall: 4.88 },
  { side: 1.0, radius: 0.22, thick: 10, zClosed: 9, zOpen: 240, rank: 0, fall: 4.94 },
];
export const DISC_SIDE = 0.44;
export const DISC_THICK = 7;
export const HOLE_SIDE = 0.5;
export const TRAY_HOLE = 0.76;
const LANDING = 5.56;

// Curves

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeIn(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

const SPRING_W = (2 * Math.PI) / 0.55;
const SPRING_Z = 0.72;

/** Damped spring (response 0.55 s, damping ratio 0.72) stepping 0 → 1 at τ = 0. */
function springStep(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  if (tau >= 1.6) return 1;
  const a = SPRING_Z * SPRING_W;
  const wd = SPRING_W * Math.sqrt(1 - SPRING_Z * SPRING_Z);
  return 1 - Math.exp(-a * tau) * (Math.cos(wd * tau) + (a / wd) * Math.sin(wd * tau));
}

/** The landing bump: a fast damped wobble that starts and ends at 0. */
function bump(tau: number) {
  'worklet';
  if (tau <= 0 || tau >= 0.7) return 0;
  return Math.exp(-9 * tau) * Math.sin(18 * tau);
}

export type Frame = {
  t: number; // loop time (bob)
  open: number; // the whole stack: slide, glow, halo rings
  p: number[]; // per layer: closed 0 … open 1
  callout: number[];
  yaw: number;
  lidBump: number;
  dip: number;
};

export function emptyFrame(t: number): Frame {
  'worklet';
  return { t, open: 0, p: [0, 0, 0, 0, 0, 0], callout: [0, 0, 0], yaw: REST_YAW, lidBump: 0, dip: 0 };
}

/** The autoPlay timeline (seconds in 0 ..< LOOP). Frames 0 and LOOP are both the closed chip. */
export function timeline(t: number): Frame {
  'worklet';
  const f = emptyFrame(t);
  const close = easeInOut(seg(t, 4.6, 5.7));
  f.open = easeInOut(seg(t, 1.0, 2.2)) * (1 - close);
  for (let i = 1; i < LAYERS.length; i++) {
    const l = LAYERS[i];
    const rise = springStep(t - 1.0 - 0.07 * 4 * l.rank);
    const fall = easeIn(seg(t, l.fall, l.fall + 0.62));
    f.p[i] = rise * (1 - fall);
  }
  for (let k = 0; k < 3; k++) {
    const d = 0.08 * k;
    f.callout[k] = easeOut(seg(t, 1.9 + d, 2.5 + d)) * (1 - easeIn(seg(t, 4.5, 4.8)));
  }
  f.yaw = REST_YAW + ((5 * Math.PI) / 180) * Math.sin((2 * Math.PI * t) / LOOP - 1.2) * f.open;
  const b = bump(t - LANDING);
  f.lidBump = 5 * Math.max(0, b);
  f.dip = 1.5 * b;
  return f;
}

/** The same stack driven by the open amount q (0 closed … 1 open) under the finger. */
export function manual(q: number, yawOffset: number, t: number): Frame {
  'worklet';
  const f = emptyFrame(t);
  f.open = Math.min(Math.max(q, 0), 1.06);
  for (let i = 1; i < LAYERS.length; i++) {
    const r = LAYERS[i].rank;
    f.p[i] = Math.min(Math.max((q - 0.3 * r) / 0.7, 0), 1) + Math.max(0, q - 1);
  }
  for (let k = 0; k < 3; k++) {
    const d = 0.05 * k;
    f.callout[k] = easeOut(seg(q, 0.7 + d, 0.92 + d));
  }
  f.yaw = REST_YAW + yawOffset;
  return f;
}

/** Height of layer i's bottom face, with a slow bob while it floats. */
export function layerZ(i: number, f: Frame) {
  'worklet';
  if (i === 0) return 0;
  const l = LAYERS[i];
  const p = f.p[i];
  const bob = 3.5 * Math.sin((2 * Math.PI * 2 * f.t) / LOOP + 0.6 * i) * Math.min(p, 1);
  let z = l.zClosed + (l.zOpen - l.zClosed) * p + bob;
  if (i === LAYERS.length - 1) z += f.lidBump;
  return z;
}

// Springs: a damped spring from (x0, v0) towards a target, as a pure function of time.

export type Spring = { from: number; velocity: number; to: number; start: number; zeta: number };

function spring(from: number, velocity: number, to: number, start: number, zeta: number): Spring {
  'worklet';
  return { from, velocity, to, start, zeta };
}

function springValue(s: Spring, now: number) {
  'worklet';
  const tau = Math.max(0, now - s.start);
  const a = s.zeta * SPRING_W;
  const wd = SPRING_W * Math.sqrt(1 - s.zeta * s.zeta);
  const A = s.from - s.to;
  const B = (s.velocity + a * A) / wd;
  return s.to + Math.exp(-a * tau) * (A * Math.cos(wd * tau) + B * Math.sin(wd * tau));
}

function springDone(s: Spring, now: number) {
  'worklet';
  return now - s.start > 1.4;
}

// Driver: plays the timeline, hands over to the finger, springs home 2 s after the last touch.

export type Driver = {
  epoch: number;
  manual: boolean;
  dragging: boolean;
  q: Spring;
  tilt: Spring;
  q0: number;
  yaw0: number;
  lastTouch: number;
  returning: boolean;
  shown: number;
  fadeFrom: number;
  fadeStart: number;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    epoch: -1,
    manual: false,
    dragging: false,
    q: spring(0, 0, 0, 0, SPRING_Z),
    tilt: spring(0, 0, 0, 0, 0.6),
    q0: 0,
    yaw0: 0,
    lastTouch: -100,
    returning: false,
    shown: 1,
    fadeFrom: 1,
    fadeStart: -100,
  };
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (d.epoch < 0) d.epoch = now;
  const t = (now - d.epoch) % LOOP;
  if (reduced) return manual(1, 0, 0);
  if (d.manual) {
    if (auto && !d.dragging && now - d.lastTouch > 2) {
      if (!d.returning) {
        d.returning = true;
        d.q = spring(springValue(d.q, now), 0, 0, now, SPRING_Z);
      } else if (springDone(d.q, now) && springDone(d.tilt, now)) {
        d.manual = false;
        d.returning = false;
        d.epoch = now;
        return timeline(0);
      }
    }
    return manual(springValue(d.q, now), springValue(d.tilt, now), t);
  }
  return auto ? timeline(t) : manual(0, 0, t);
}

export function shownAmount(d: Driver, now: number) {
  'worklet';
  const k = seg(now, d.fadeStart, d.fadeStart + 0.25);
  return d.fadeFrom + (d.shown - d.fadeFrom) * k;
}

export function toggleReduced(d: Driver, now: number) {
  'worklet';
  d.fadeFrom = shownAmount(d, now);
  d.shown = d.shown > 0.5 ? 0 : 1;
  d.fadeStart = now;
}

export function begin(d: Driver, now: number, auto: boolean) {
  'worklet';
  const f = driverFrame(d, now, auto, false);
  const current = d.manual ? springValue(d.q, now) : f.open;
  d.yaw0 = f.yaw - REST_YAW;
  d.q = spring(current, 0, current, now, SPRING_Z);
  d.tilt = spring(d.yaw0, 0, d.yaw0, now, 0.6);
  d.q0 = current;
  d.manual = true;
  d.dragging = true;
  d.returning = false;
  d.lastTouch = now;
}

/** dx, dy: the finger's travel since the touch began (pt). Up opens, sideways tilts. */
export function drag(d: Driver, dx: number, dy: number, now: number) {
  'worklet';
  let v = d.q0 - dy / DRAG_SPAN;
  if (v > 1) {
    const over = v - 1;
    v = 1 + (over / (1 + over * 3)) * 0.35;
  }
  if (v < 0) {
    const over = -v;
    v = -((over / (1 + over * 3)) * 0.35);
  }
  d.q = spring(v, 0, v, now, SPRING_Z);
  const tiltDeg = Math.min(Math.max(dx * 0.08, -10), 10);
  const y = d.yaw0 + (tiltDeg * Math.PI) / 180;
  d.tilt = spring(y, 0, y, now, 0.6);
  d.lastTouch = now;
}

/** Ends the drag or tap; true when the stack commits to the other state. */
export function end(d: Driver, vy: number, tap: boolean, now: number) {
  'worklet';
  d.dragging = false;
  d.lastTouch = now;
  const current = springValue(d.q, now);
  const wasOpen = d.q0 >= 0.5;
  const velocity = -vy / DRAG_SPAN;
  let target = 0;
  if (tap) target = wasOpen ? 0 : 1;
  else target = current + 0.15 * velocity > 0.5 ? 1 : 0;
  d.q = spring(current, tap ? 0 : velocity, target, now, SPRING_Z);
  d.tilt = spring(springValue(d.tilt, now), 0, 0, now, 0.6);
  return target > 0.5 !== wasOpen;
}
