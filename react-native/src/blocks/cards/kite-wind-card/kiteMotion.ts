/*
 Kite Wind Card — the timeline, the wind data and the touch model. Every value of a frame is a pure
 function of the loop time (autoplay) or of the clock and a few touch events (touch). Worklets only,
 each one declared above its callers.

 The kite's sway, the tail's wave and the clouds' drift use whole cycles per 8 s of the clock, so
 they carry on unbroken between the loop and touch.
*/

/** One loop, in seconds. */
export const LOOP = 8.0;
/** Seconds without touch before the card eases back to 12 pm and rejoins the loop. */
export const IDLE = 2;
/** Points per hour on the hour card. */
export const HOUR_STEP = 31.4;
/** The kite's pull under the finger, at most (pt). */
export const PULL_MAX = 30;

/** Hours 9 am – 6 pm and their wind in km/h (invented). */
export const HOURS = [9, 10, 11, 12, 13, 14, 15, 16, 17, 18];
export const WIND = [10, 12, 15, 18, 21, 24, 26, 22, 17, 12];
/** The resting hour: 12 pm. */
export const REST_P = 3;

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutQuad(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function mod(x: number, m: number) {
  'worklet';
  return ((x % m) + m) % m;
}

/** Wind at a continuous hour position p (0…9), linear between the hours. */
export function windAt(p: number) {
  'worklet';
  const q = Math.min(9, Math.max(0, p));
  const i = Math.min(8, Math.floor(q));
  const f = q - i;
  return WIND[i] + (WIND[i + 1] - WIND[i]) * f;
}

export function hourLabel(h: number) {
  'worklet';
  if (h < 12) return `${h} am`;
  if (h === 12) return '12 pm';
  return `${h - 12} pm`;
}

/** The hour position on the loop. */
export function loopP(t: number) {
  'worklet';
  if (t < 1.2) return REST_P;
  if (t < 3.2) return REST_P + 3 * easeInOutQuad(seg(t, 1.2, 3.2));
  if (t < 4.6) return 6;
  if (t < 6.4) return 6 - 3 * easeInOutQuad(seg(t, 4.6, 6.4));
  return REST_P;
}

/** The demo finger on the scrubber. */
export function loopFinger(t: number) {
  'worklet';
  return seg(t, 1.0, 1.2) * (1 - seg(t, 3.2, 3.4)) + seg(t, 4.4, 4.6) * (1 - seg(t, 6.4, 6.6));
}

export type Frame = {
  /** Hour position and the clock phase time (s) the kite, tail and clouds move with. */
  p: number;
  phaseT: number;
  finger: number;
  /** The kite pulled by a finger (pt). */
  pullX: number;
  pullY: number;
};

export type Touch = {
  manual: boolean;
  loopStart: number;
  lastTouch: number;
  /** Hour position: dragging (from p0 + dx / HOUR_STEP), or easing from pFrom to pTo since pAt. */
  dragging: boolean;
  p0: number;
  dx: number;
  pFrom: number;
  pTo: number;
  pAt: number;
  pDur: number;
  /** The kite pull: dragging, or springing back from (kFromX, kFromY) since kAt. */
  pulling: boolean;
  kx: number;
  ky: number;
  kFromX: number;
  kFromY: number;
  kAt: number;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    manual: false,
    loopStart: 0,
    lastTouch: -100,
    dragging: false,
    p0: REST_P,
    dx: 0,
    pFrom: REST_P,
    pTo: REST_P,
    pAt: -100,
    pDur: 0.35,
    pulling: false,
    kx: 0,
    ky: 0,
    kFromX: 0,
    kFromY: 0,
    kAt: -100,
  };
}

function copy(s: Touch): Touch {
  'worklet';
  return {
    manual: s.manual,
    loopStart: s.loopStart,
    lastTouch: s.lastTouch,
    dragging: s.dragging,
    p0: s.p0,
    dx: s.dx,
    pFrom: s.pFrom,
    pTo: s.pTo,
    pAt: s.pAt,
    pDur: s.pDur,
    pulling: s.pulling,
    kx: s.kx,
    ky: s.ky,
    kFromX: s.kFromX,
    kFromY: s.kFromY,
    kAt: s.kAt,
  };
}

/** Hour position under touch. */
export function touchP(s: Touch, c: number) {
  'worklet';
  if (s.dragging) return Math.min(9, Math.max(0, s.p0 + s.dx / HOUR_STEP));
  return s.pFrom + (s.pTo - s.pFrom) * easeOutCubic(seg(c, s.pAt, s.pAt + s.pDur));
}

/** The kite's pull: a damped spring back to rest after the finger lets go (about 0.6 s). */
export function touchPull(s: Touch, c: number): [number, number] {
  'worklet';
  if (s.pulling) return [s.kx, s.ky];
  const u = c - s.kAt;
  if (u < 0 || u > 1.2) return [0, 0];
  const k = Math.exp(-7 * u) * Math.cos(12 * u);
  return [s.kFromX * k, s.kFromY * k];
}

export function frameAt(s: Touch, c: number, reduced: boolean): Frame {
  'worklet';
  const phaseT = reduced ? 0 : mod(c - s.loopStart, LOOP);
  if (!s.manual) {
    const t = mod(c - s.loopStart, LOOP);
    return { p: reduced ? REST_P : loopP(t), phaseT, finger: reduced ? 0 : loopFinger(t), pullX: 0, pullY: 0 };
  }
  const pull = reduced ? [0, 0] : touchPull(s, c);
  return { p: touchP(s, c), phaseT, finger: 0, pullX: pull[0], pullY: pull[1] };
}

/** Hands the loop over to touch, keeping exactly the frame on screen. */
export function takeOver(s: Touch, c: number, reduced: boolean): Touch {
  'worklet';
  const n = copy(s);
  n.lastTouch = c;
  if (s.manual) return n;
  n.manual = true;
  const p = reduced ? REST_P : loopP(mod(c - s.loopStart, LOOP));
  n.pFrom = p;
  n.pTo = p;
  n.pAt = c;
  return n;
}

/** A finger lands on the hour card. */
export function grabCard(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.p0 = touchP(s, c);
  n.dx = 0;
  n.dragging = true;
  n.lastTouch = c;
  return n;
}

export function dragCard(s: Touch, dx: number, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.dx = dx;
  n.lastTouch = c;
  return n;
}

/** Eases the hour position to `to` (0.35 s, or at once under Reduce Motion). */
export function goTo(s: Touch, to: number, c: number, reduced: boolean): Touch {
  'worklet';
  const n = copy(s);
  n.pFrom = touchP(s, c);
  n.pTo = Math.min(9, Math.max(0, to));
  n.pAt = c;
  n.pDur = 0.35;
  // Reduce Motion: the card changes without animation (and its clock does not run).
  if (reduced) n.pFrom = n.pTo;
  n.dragging = false;
  n.lastTouch = c;
  return n;
}

/** The finger lets go of the card: snap to the nearest hour. */
export function releaseCard(s: Touch, c: number, reduced: boolean): Touch {
  'worklet';
  return goTo(s, Math.round(touchP(s, c)), c, reduced);
}

/** A finger lands on the kite. */
export function grabKite(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  const pull = touchPull(s, c);
  n.pulling = true;
  n.kx = pull[0];
  n.ky = pull[1];
  n.lastTouch = c;
  return n;
}

/** The kite follows the finger, at most PULL_MAX away (softly). */
export function dragKite(s: Touch, dx: number, dy: number, c: number): Touch {
  'worklet';
  const n = copy(s);
  const d = Math.hypot(dx, dy);
  const k = d > 0 ? (PULL_MAX * (1 - Math.exp(-d / PULL_MAX))) / d : 0;
  n.kx = dx * k;
  n.ky = dy * k;
  n.lastTouch = c;
  return n;
}

export function releaseKite(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.pulling = false;
  n.kFromX = s.kx;
  n.kFromY = s.ky;
  n.kAt = c;
  n.lastTouch = c;
  return n;
}

/**
 2 s after the last touch: ease the hours back to 12 pm, then rejoin the loop where it rests at
 12 pm (6.6–8.0 or 0–1.0 s of the loop), so nothing jumps: the sway already runs on the loop's clock.
*/
export function stepTouch(s: Touch, c: number, autoPlay: boolean, reduced: boolean): Touch | null {
  'worklet';
  if (!s.manual || !autoPlay || reduced || s.dragging || s.pulling) return null;
  if (c - s.lastTouch < IDLE) return null;
  const p = touchP(s, c);
  const moving = c < s.pAt + s.pDur || c < s.kAt + 1.2;
  if (moving) return null;
  if (Math.abs(p - REST_P) > 0.001) {
    const n = goTo(s, REST_P, c, false);
    n.lastTouch = s.lastTouch;
    n.pDur = 0.6;
    return n;
  }
  const t = mod(c - s.loopStart, LOOP);
  if (t >= 6.6 || t < 1.0) {
    const r = initialTouch();
    r.loopStart = s.loopStart;
    return r;
  }
  return null;
}
