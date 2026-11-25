/*
 Back Online Plug — the timeline and the touch model. Every value of a frame is a pure function of
 the loop time (autoplay) or of the clock and a few touch events (touch). Worklets only, each one
 declared above its callers.

 The plug is seen from its round back, on the socket's axis. Two numbers place it: `a`, how far it
 is from the socket across the wall (1 at its rest place, 0 lined up on the socket), and `z`, how far
 it is out toward us (1 out, 0 seated; slightly below 0 during the seat bounce).
*/

/** One loop, in seconds. */
export const LOOP = 7.0;
/** The sway's period: three swings per loop. */
export const SWAY_PERIOD = LOOP / 3;
/** The plug's rest place, relative to the socket's centre (pt). */
export const REST_DX = -64;
export const REST_DY = 92;
/** Its distance from the socket's centre, and how near a release must be to push it in. */
export const REST_DIST = Math.hypot(REST_DX, REST_DY);
export const SNAP = 40;
/** Seconds without touch before the loop restarts. */
export const IDLE = 2;

/** Plug states (`Touch.pk`). */
export const PLUG_STILL = 0;
export const PLUG_DRAG = 1;
export const PLUG_SEAT = 2;
export const PLUG_UNSEAT = 3;
export const PLUG_RETURN = 4;

/** Screen states (`Touch.vk`). */
export const SCREEN_OFFLINE = 0;
export const SCREEN_SEAT = 1;
export const SCREEN_UNSEAT = 2;

export type RGB = [number, number, number];
export const OFFLINE: RGB = [46, 59, 52];
export const ONLINE: RGB = [31, 107, 64];
export const FLASH: RGB = [44, 138, 82];

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInQuad(x: number) {
  'worklet';
  return x * x;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutQuad(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}

export function mod(x: number, m: number) {
  'worklet';
  return ((x % m) + m) % m;
}

export function mixRGB(a: RGB, b: RGB, u: number): RGB {
  'worklet';
  return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u];
}

/** The seat bounce: z = −0.03 e^(−12u) sin 30u for u in 0…0.35 s. */
export function bounce(u: number) {
  'worklet';
  if (u < 0 || u >= 0.35) return 0;
  return -0.03 * Math.exp(-12 * u) * Math.sin(30 * u);
}

/** Lateral position on the loop: 1 at rest, 0 on the socket's axis. */
export function loopA(t: number) {
  'worklet';
  if (t >= 1.0 && t < 1.6) return 1 - easeInOutQuad(seg(t, 1.0, 1.6));
  if (t >= 1.6 && t < 5.3) return 0;
  if (t >= 5.3 && t < 5.9) return easeInOutQuad(seg(t, 5.3, 5.9));
  return 1;
}

/** Depth on the loop: 1 out, 0 seated. */
export function loopZ(t: number) {
  'worklet';
  if (t >= 1.55 && t < 1.85) return 1 - easeInQuad(seg(t, 1.55, 1.85));
  if (t >= 1.85 && t < 4.8) return bounce(t - 1.85);
  if (t >= 4.8 && t < 5.3) return easeOutCubic(seg(t, 4.8, 5.3));
  return 1;
}

/** The wall colour at a time of the loop's beats. */
export function wallAt(t: number): RGB {
  'worklet';
  if (t >= 1.85 && t < 5.0) {
    const a = seg(t, 1.85, 1.97);
    return a < 1 ? mixRGB(OFFLINE, FLASH, a) : mixRGB(FLASH, ONLINE, easeInOutQuad(seg(t, 1.97, 2.6)));
  }
  if (t >= 5.0) return mixRGB(ONLINE, OFFLINE, easeInOutQuad(seg(t, 5.0, 5.6)));
  return OFFLINE;
}

/** Title flip progress: 0 offline, 1 online. */
export function flipAt(t: number) {
  'worklet';
  if (t >= 1.9 && t < 5.1) return seg(t, 1.9, 2.3);
  if (t >= 5.1) return 1 - seg(t, 5.1, 5.5);
  return 0;
}

export function chipAt(t: number) {
  'worklet';
  return Math.min(3, 1 + Math.floor(seg(t, 2.3, 4.6) * 3));
}

export function sparkAt(t: number) {
  'worklet';
  return seg(t, 1.82, 2.05);
}

export type Frame = {
  a: number;
  z: number;
  /** −1…1: the loose plug's sway, already faded by `a` and the finger. */
  sway: number;
  wall: RGB;
  flip: number;
  chip: number;
  spark: number;
};

/** The sway from the clock (continuous across touch and loop restarts), only while the plug is out. */
export function swayAt(c: number, loopStart: number, a: number, z: number, env: number) {
  'worklet';
  if (z <= 0.5) return 0;
  return Math.sin((2 * Math.PI * (c - loopStart)) / SWAY_PERIOD) * a * env;
}

/** The loop's frame at loop time t. */
export function loopFrame(t: number, c: number, loopStart: number): Frame {
  'worklet';
  const a = loopA(t);
  const z = loopZ(t);
  return {
    a,
    z,
    sway: swayAt(c, loopStart, a, z, 1),
    wall: wallAt(t),
    flip: flipAt(t),
    chip: chipAt(t),
    spark: sparkAt(t),
  };
}

export type Touch = {
  /** Touch has taken over from the loop. */
  manual: boolean;
  touching: boolean;
  /** The loop's start on the clock (autoplay t = clock − loopStart, wrapped). */
  loopStart: number;
  /** Clock time the loop restarts at, after touch (−1: not planned). */
  resumeAt: number;
  lastTouch: number;
  /** Plug: kind, the lateral position it started from, its start time. */
  pk: number;
  pFrom: number;
  pAt: number;
  /** Drag: the finger's travel toward the socket, in pt (positive toward it). */
  dragDist: number;
  /** Screen: kind, and the clock time its beats are anchored to (seat at 1.85, unseat at 4.8). */
  vk: number;
  vAt: number;
  /** The chip count frozen when unplugged. */
  chip: number;
  /** Sway envelope: 0 while the finger holds the plug, 1 otherwise. */
  env: number;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    manual: false,
    touching: false,
    loopStart: 0,
    resumeAt: -1,
    lastTouch: -100,
    pk: PLUG_STILL,
    pFrom: 1,
    pAt: 0,
    dragDist: 0,
    vk: SCREEN_OFFLINE,
    vAt: 0,
    chip: 1,
    env: 1,
  };
}

function copy(s: Touch): Touch {
  'worklet';
  return {
    manual: s.manual,
    touching: s.touching,
    loopStart: s.loopStart,
    resumeAt: s.resumeAt,
    lastTouch: s.lastTouch,
    pk: s.pk,
    pFrom: s.pFrom,
    pAt: s.pAt,
    dragDist: s.dragDist,
    vk: s.vk,
    vAt: s.vAt,
    chip: s.chip,
    env: s.env,
  };
}

/**
 The seat from lateral position `from`: it lines up over the socket (0.6 s × from, easeInOutQuad),
 then pushes straight in (0.3 s, easeInQuad), starting 0.05 s before it is lined up — the loop's
 1.0–1.85 s beats when from = 1. Returns [time the push starts, time it seats] after the start.
*/
function seatTimes(from: number): [number, number] {
  'worklet';
  const align = 0.6 * from;
  const push = Math.max(0, align - 0.05);
  return [push, push + 0.3];
}

/** Lateral position and depth under touch. */
export function touchPlug(s: Touch, c: number): [number, number] {
  'worklet';
  if (s.pk === PLUG_DRAG) return [clamp01(s.pFrom - s.dragDist / REST_DIST), 1];
  if (s.pk === PLUG_SEAT) {
    const u = c - s.pAt;
    const align = 0.6 * s.pFrom;
    const a = align > 0 ? s.pFrom * (1 - easeInOutQuad(clamp01(u / align))) : 0;
    const times = seatTimes(s.pFrom);
    const z = u < times[1] ? 1 - easeInQuad(seg(u, times[0], times[1])) : bounce(u - times[1]);
    return [a, z];
  }
  if (s.pk === PLUG_UNSEAT) {
    const u = c - s.pAt;
    return [easeInOutQuad(seg(u, 0.5, 1.1)), easeOutCubic(seg(u, 0, 0.5))];
  }
  if (s.pk === PLUG_RETURN) {
    return [s.pFrom + (1 - s.pFrom) * easeOutCubic(seg(c, s.pAt, s.pAt + 0.35)), 1];
  }
  return [s.pFrom, s.vk === SCREEN_SEAT ? 0 : 1];
}

/** The time on the loop's beats that the screen shows under touch. */
export function touchBeat(s: Touch, c: number) {
  'worklet';
  if (s.vk === SCREEN_SEAT) return Math.min(1.85 + (c - s.vAt), 4.79);
  if (s.vk === SCREEN_UNSEAT) return Math.min(4.8 + (c - s.vAt), 5.9);
  return 0;
}

export function touchFrame(s: Touch, c: number): Frame {
  'worklet';
  const p = touchPlug(s, c);
  const t = touchBeat(s, c);
  return {
    a: p[0],
    z: p[1],
    sway: swayAt(c, s.loopStart, p[0], p[1], s.env),
    wall: wallAt(t),
    flip: flipAt(t),
    chip: s.vk === SCREEN_UNSEAT ? s.chip : chipAt(t),
    spark: s.vk === SCREEN_SEAT ? sparkAt(t) : 0,
  };
}

export function frameAt(s: Touch, c: number): Frame {
  'worklet';
  if (s.manual) return touchFrame(s, c);
  return loopFrame(mod(c - s.loopStart, LOOP), c, s.loopStart);
}

/** Online for the accessibility value and the button: the socket is live. */
export function isOnline(s: Touch, c: number) {
  'worklet';
  if (s.manual) return s.vk === SCREEN_SEAT && c >= s.vAt;
  const t = mod(c - s.loopStart, LOOP);
  return t >= 1.85 && t < 4.8;
}

/** Hands the loop over to touch, keeping exactly the frame on screen. */
export function takeOver(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.resumeAt = -1;
  n.lastTouch = c;
  if (s.manual) return n;
  n.manual = true;
  const t = mod(c - s.loopStart, LOOP);
  n.pk = PLUG_STILL;
  n.pFrom = 1;
  n.vk = SCREEN_OFFLINE;
  if (t >= 1.0 && t < 4.8) {
    n.pk = PLUG_SEAT;
    n.pFrom = 1;
    n.pAt = c - (t - 1.0);
    n.vk = SCREEN_SEAT;
    n.vAt = c - (t - 1.85);
  } else if (t >= 4.8 && t < 5.9) {
    n.pk = PLUG_UNSEAT;
    n.pAt = c - (t - 4.8);
    n.vk = SCREEN_UNSEAT;
    n.vAt = c - (t - 4.8);
    n.chip = 3;
  }
  return n;
}

/** Seats the plug from where it is: it lines up, pushes in, then the 1.85 s beats play. */
export function seat(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  const from = touchPlug(s, c)[0];
  n.pk = PLUG_SEAT;
  n.pFrom = from;
  n.pAt = c;
  n.vk = SCREEN_SEAT;
  n.vAt = c + seatTimes(from)[1];
  return n;
}

/** Pulls the plug out toward us and back to its rest place: the 4.8 s beats. */
export function unseat(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.chip = chipAt(touchBeat(s, c));
  n.pk = PLUG_UNSEAT;
  n.pAt = c;
  n.vk = SCREEN_UNSEAT;
  n.vAt = c;
  return n;
}

/** Is the plug out of the socket and free to be held (not pushing in or seated)? */
export function canGrab(s: Touch, c: number) {
  'worklet';
  return touchPlug(s, c)[1] >= 1 && !(s.vk === SCREEN_SEAT && c >= s.vAt);
}

/** A finger lands on the loose plug: hold it where it is. A seat that has not pushed in yet stops. */
export function grab(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.pFrom = touchPlug(s, c)[0];
  n.pk = PLUG_DRAG;
  n.dragDist = 0;
  if (s.vk === SCREEN_SEAT) n.vk = SCREEN_OFFLINE;
  return n;
}

/** The finger's travel, projected on the way from the rest place to the socket. */
export function drag(s: Touch, dx: number, dy: number, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.dragDist = -(dx * REST_DX + dy * REST_DY) / REST_DIST;
  n.lastTouch = c;
  return n;
}

/** The finger lets go of the plug: a tap or a release within 40 pt of the socket pushes it in. */
export function release(s: Touch, c: number, tap: boolean): Touch {
  'worklet';
  const a = touchPlug(s, c)[0];
  if (tap || a * REST_DIST <= SNAP) return seat(s, c);
  const n = copy(s);
  n.pk = PLUG_RETURN;
  n.pFrom = a;
  n.pAt = c;
  return n;
}

/** Toggles the state (a tap on the seated plug or Try again, or the accessibility action). */
export function toggle(s: Touch, c: number): Touch {
  'worklet';
  if (s.vk === SCREEN_SEAT) return c < s.vAt ? s : unseat(s, c);
  if (s.pk === PLUG_UNSEAT && touchPlug(s, c)[1] < 1) return s;
  return seat(s, c);
}

/** The finger lands or lifts. */
export function withTouching(s: Touch, touching: boolean, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.touching = touching;
  n.lastTouch = c;
  return n;
}

/** Has every move of the plug and the screen ended? */
function settled(s: Touch, c: number) {
  'worklet';
  if (s.pk === PLUG_DRAG) return false;
  if (s.pk === PLUG_SEAT && c < s.vAt + 0.8) return false;
  if (s.pk === PLUG_UNSEAT && c < s.pAt + 1.1) return false;
  if (s.pk === PLUG_RETURN && c < s.pAt + 0.35) return false;
  if (s.vk === SCREEN_UNSEAT && c < s.vAt + 1.1) return false;
  return true;
}

/**
 Advances touch state once a frame: the sway envelope, then — 2 s after the last touch — the ease
 back to offline and the restart at t = 0. The restart waits for a whole sway period so the sway
 carries on unbroken; offline at rest nothing else moves, so the wait does not show.
*/
export function stepTouch(s: Touch, c: number, dt: number, autoPlay: boolean): Touch | null {
  'worklet';
  if (!s.manual) return null;
  const n = copy(s);
  let changed = false;
  const target = s.touching && s.pk === PLUG_DRAG ? 0 : 1;
  if (n.env !== target) {
    const k = 1 - Math.exp(-dt / 0.12);
    n.env = Math.abs(target - n.env) < 0.002 ? target : n.env + (target - n.env) * k;
    changed = true;
  }
  if (!autoPlay || s.touching || c - s.lastTouch < IDLE || !settled(s, c)) return changed ? n : null;
  if (s.vk === SCREEN_SEAT) return unseat(n, c);
  if (touchPlug(n, c)[0] < 1) {
    const r = copy(n);
    r.pk = PLUG_RETURN;
    r.pFrom = touchPlug(n, c)[0];
    r.pAt = c;
    return r;
  }
  if (n.resumeAt < 0) {
    n.resumeAt = s.loopStart + Math.ceil((c - s.loopStart) / SWAY_PERIOD) * SWAY_PERIOD;
    return n;
  }
  if (c >= n.resumeAt && n.env === 1) {
    const r = initialTouch();
    r.loopStart = n.resumeAt;
    return r;
  }
  return changed ? n : null;
}
