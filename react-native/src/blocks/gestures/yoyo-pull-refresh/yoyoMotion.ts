/*
 Yo-yo Pull Refresh — the timeline and the touch model. Every value of a frame is a pure function of
 the loop time (autoplay) or of the clock and a few touch events (touch); only the yo-yo's angle under
 touch is integrated frame by frame, because it is the integral of a spin that the finger changes.
 Worklets only, each one declared above its callers.
*/

/** One loop, in seconds. */
export const LOOP = 6.0;
/** The pull distance the list rests at while refreshing, in pt. */
export const PULL = 190;
/** Release past this pull to refresh. */
export const THRESHOLD = 120;
/** The yo-yo's centre off screen, and at the end of its string. */
export const Y_UP = -90;
export const Y_DOWN = 258;
/** Seconds without touch before the loop restarts. */
export const IDLE = 2;
/** The loop time the refresh beats start from (the release). */
export const BEAT = 1.7;

export const STATUS = ['Pull to refresh', 'Refreshing…', 'Up to date', 'Pull to refresh'];
/** When each status line starts to cross-fade in (0.24 s: the old one fades out, then the new one in). */
const STATUS_AT = [-1, 1.75, 3.7, 5.3];
const FADE = 0.24;

/** Touch modes (`Touch.mode`). */
export const MODE_REST = 0;
export const MODE_DRAG = 1;
export const MODE_SPRING = 2;
export const MODE_REFRESH = 3;

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutQuad(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}

export function easeInQuad(x: number) {
  'worklet';
  return x * x;
}

export function mod(x: number, m: number) {
  'worklet';
  return ((x % m) + m) % m;
}

/** Spin in turns per second on the beats. */
export function spinAt(t: number) {
  'worklet';
  if (t < 0.8) return 0;
  if (t < 1.7) return 0.8;
  if (t < 2.05) return 0.8 + 4.2 * seg(t, 1.7, 2.05);
  if (t < 3.6) return 5;
  if (t < 4.2) return 5 - 4 * seg(t, 3.6, 4.2);
  return 1;
}

/** The yo-yo's angle in turns at loop time t: the exact integral of spinAt from 0. */
export function turnsAt(t: number) {
  'worklet';
  if (t <= 0.8) return 0;
  let a = 0.8 * (Math.min(t, 1.7) - 0.8);
  if (t <= 1.7) return a;
  const s1 = seg(t, 1.7, 2.05);
  a += 0.35 * (0.8 * s1 + 2.1 * s1 * s1);
  if (t <= 2.05) return a;
  a += 5 * (Math.min(t, 3.6) - 2.05);
  if (t <= 3.6) return a;
  const s2 = seg(t, 3.6, 4.2);
  a += 0.6 * (5 * s2 - 2 * s2 * s2);
  if (t <= 4.2) return a;
  return a + (t - 4.2);
}

/**
 The list after the release: it carries on 20 pt past the pull and settles at 190
 (190 + 20 e^(−7u) sin 14u in the loop, so it starts where the pull ended; released short of 190,
 the gap closes on the same damped curve).
*/
export function settleD(u: number, from: number) {
  'worklet';
  const k = Math.exp(-7 * u);
  return PULL + (from - PULL) * k * Math.cos(14 * u) + 20 * k * Math.sin(14 * u);
}

/** The list offset on the refresh beats (t ≥ 1.7), released at `from`. */
export function beatD(t: number, from: number) {
  'worklet';
  if (t < 3.75) return settleD(t - BEAT, from);
  if (t < 4.25) return PULL * (1 - easeInOutQuad(seg(t, 3.75, 4.25)));
  return 0;
}

/** The yo-yo's centre on the refresh beats (t ≥ 1.7), released at height `from`. */
export function beatY(t: number, from: number) {
  'worklet';
  if (t < 2.05) return from + (Y_DOWN - from) * easeInQuad(seg(t, 1.7, 2.05));
  if (t < 3.6) return Y_DOWN + 12 * Math.exp(-6 * (t - 2.05)) * Math.sin(18 * (t - 2.05));
  if (t < 4.2) return Y_DOWN + (Y_UP - Y_DOWN) * easeInOutQuad(seg(t, 3.6, 4.2));
  return Y_UP;
}

export function pullY(d: number) {
  'worklet';
  return Y_UP + 1.3 * Math.max(0, d);
}

export function loopD(t: number) {
  'worklet';
  if (t >= 0.8 && t < 1.7) return PULL * easeOutCubic(seg(t, 0.8, 1.7));
  if (t >= 1.7) return beatD(t, PULL);
  return 0;
}

export function loopY(t: number) {
  'worklet';
  if (t >= 0.8 && t < 1.7) return pullY(loopD(t));
  if (t >= 1.7) return beatY(t, pullY(PULL));
  return Y_UP;
}

/** Status line on the beats: [from index, to index, progress 0…1 of the cross-fade]. */
export function statusAt(t: number): [number, number, number] {
  'worklet';
  let k = 0;
  for (let i = 1; i < STATUS_AT.length; i++) if (t >= STATUS_AT[i]) k = i;
  if (k === 0) return [0, 0, 1];
  return [k - 1, k, seg(t, STATUS_AT[k], STATUS_AT[k] + FADE)];
}

export type Frame = {
  d: number;
  y: number;
  /** Angle in turns, and spin in turns per second (for the blur and the glow). */
  turns: number;
  spin: number;
  /** Phase time for the glow's pulse. */
  pulse: number;
  status: [number, number, number];
  finger: number;
  /** Yo-yo opacity (Reduce Motion fades it). */
  alpha: number;
};

export function loopFrame(t: number): Frame {
  'worklet';
  return {
    d: loopD(t),
    y: loopY(t),
    turns: turnsAt(t),
    spin: spinAt(t),
    pulse: t,
    status: statusAt(t),
    finger: seg(t, 0.7, 0.82) * (1 - seg(t, 1.7, 1.82)),
    alpha: 1,
  };
}

export type Touch = {
  manual: boolean;
  touching: boolean;
  loopStart: number;
  lastTouch: number;
  mode: number;
  /** Drag: the list offset at the grab and the finger's travel since (pt, down positive). */
  base: number;
  drag: number;
  /** Spring: from, to, start. */
  from: number;
  to: number;
  at: number;
  /** Refresh: the clock time of the release (beat 1.7), the list offset and yo-yo height at release. */
  refAt: number;
  refD: number;
  refY: number;
  /** A status cross-fade started by touch: from, to, start (−1: none). */
  stFrom: number;
  stTo: number;
  stAt: number;
  /** The yo-yo's angle (turns) and spin under touch. */
  turns: number;
  spin: number;
  /** Scroll overflow of the list (pt), from the layout. */
  overflow: number;
};

export function initialTouch(overflow: number): Touch {
  'worklet';
  return {
    manual: false,
    touching: false,
    loopStart: 0,
    lastTouch: -100,
    mode: MODE_REST,
    base: 0,
    drag: 0,
    from: 0,
    to: 0,
    at: 0,
    refAt: -100,
    refD: PULL,
    refY: pullY(PULL),
    stFrom: 0,
    stTo: 0,
    stAt: -1,
    turns: 0,
    spin: 0,
    overflow,
  };
}

function copy(s: Touch): Touch {
  'worklet';
  return {
    manual: s.manual,
    touching: s.touching,
    loopStart: s.loopStart,
    lastTouch: s.lastTouch,
    mode: s.mode,
    base: s.base,
    drag: s.drag,
    from: s.from,
    to: s.to,
    at: s.at,
    refAt: s.refAt,
    refD: s.refD,
    refY: s.refY,
    stFrom: s.stFrom,
    stTo: s.stTo,
    stAt: s.stAt,
    turns: s.turns,
    spin: s.spin,
    overflow: s.overflow,
  };
}

/** The last touch was now (a touch that did not move the list). */
export function touched(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.lastTouch = c;
  return n;
}

export function withOverflow(s: Touch, overflow: number): Touch {
  'worklet';
  const n = copy(s);
  n.overflow = overflow;
  return n;
}

/** Finger travel → list offset: rubber band past the top (the pull) and past the end of the list. */
export function dragD(total: number, overflow: number) {
  'worklet';
  if (total > 0) return PULL * (1 - Math.exp(-total / PULL));
  if (total >= -overflow) return total;
  const over = -overflow - total;
  return -overflow - over / (1 + over / 120);
}

/** The inverse of the pull's rubber band, so a grab mid-pull keeps the list where it is. */
function undragD(d: number, overflow: number) {
  'worklet';
  if (d > 0) return -PULL * Math.log(1 - Math.min(d, PULL - 0.01) / PULL);
  return Math.max(d, -overflow);
}

/** The refresh beat time under touch (−1 when no refresh is playing). */
export function touchBeat(s: Touch, c: number) {
  'worklet';
  if (s.mode !== MODE_REFRESH) return -1;
  return Math.min(BEAT + (c - s.refAt), LOOP);
}

export function touchD(s: Touch, c: number) {
  'worklet';
  if (s.mode === MODE_DRAG) return dragD(s.base + s.drag, s.overflow);
  if (s.mode === MODE_SPRING) return s.from + (s.to - s.from) * easeOutCubic(seg(c, s.at, s.at + 0.35));
  if (s.mode === MODE_REFRESH) return beatD(touchBeat(s, c), s.refD);
  return s.from;
}

export function touchY(s: Touch, c: number) {
  'worklet';
  if (s.mode === MODE_REFRESH) return beatY(touchBeat(s, c), s.refY);
  return pullY(touchD(s, c));
}

/** The status under touch: the beats while refreshing, unless a new pull has faded it back. */
export function touchStatus(s: Touch, c: number): [number, number, number] {
  'worklet';
  if (s.stAt >= 0) return [s.stFrom, s.stTo, seg(c, s.stAt, s.stAt + FADE)];
  if (s.mode === MODE_REFRESH || s.refAt > -100) return statusAt(Math.min(BEAT + (c - s.refAt), LOOP));
  return [0, 0, 1];
}

/** The line shown (the index the cross-fade is on). */
function shownStatus(st: [number, number, number]) {
  'worklet';
  return st[2] < 0.5 ? st[0] : st[1];
}

/** The spin under touch: slow while the yo-yo hangs on a pull, the beats while refreshing. */
function touchSpin(s: Touch, c: number) {
  'worklet';
  if (s.mode === MODE_REFRESH) return spinAt(touchBeat(s, c));
  return touchD(s, c) > 0 ? 0.8 : 0;
}

export function touchFrame(s: Touch, c: number): Frame {
  'worklet';
  const t = touchBeat(s, c);
  return {
    d: touchD(s, c),
    y: touchY(s, c),
    turns: s.turns,
    spin: s.spin,
    pulse: t >= 0 ? t : c,
    status: touchStatus(s, c),
    finger: 0,
    alpha: 1,
  };
}

export function frameAt(s: Touch, c: number): Frame {
  'worklet';
  if (s.manual) return touchFrame(s, c);
  return loopFrame(mod(c - s.loopStart, LOOP));
}

/** Hands the loop over to touch, keeping exactly the frame on screen. */
export function takeOver(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.lastTouch = c;
  if (s.manual) return n;
  const t = mod(c - s.loopStart, LOOP);
  n.manual = true;
  n.turns = turnsAt(t);
  n.spin = spinAt(t);
  n.stAt = -1;
  n.refAt = -100;
  if (t >= 0.8 && t < 1.7) {
    n.mode = MODE_SPRING;
    n.from = loopD(t);
    n.to = n.from;
    n.at = c;
  } else if (t >= 1.7) {
    n.mode = MODE_REFRESH;
    n.refAt = c - (t - BEAT);
    n.refD = PULL;
    n.refY = pullY(PULL);
  } else {
    n.mode = MODE_REST;
    n.from = 0;
  }
  return n;
}

/** Can the finger move the list now? Not while the refresh holds it down. */
export function canGrab(s: Touch, c: number) {
  'worklet';
  if (s.mode !== MODE_REFRESH) return true;
  return touchBeat(s, c) >= 4.25;
}

/** A finger lands on the list: hold it where it is. */
export function grab(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  const d = touchD(s, c);
  if (s.mode === MODE_REFRESH || s.stAt >= 0 || s.refAt > -100) {
    const shown = shownStatus(touchStatus(s, c));
    if (shown !== 0 && shown !== 3) {
      n.stFrom = shown;
      n.stTo = 0;
      n.stAt = c;
    }
  }
  n.mode = MODE_DRAG;
  n.base = undragD(d, s.overflow);
  n.drag = 0;
  n.touching = true;
  n.lastTouch = c;
  return n;
}

export function drag(s: Touch, dy: number, c: number): Touch {
  'worklet';
  const n = copy(s);
  n.drag = dy;
  n.lastTouch = c;
  return n;
}

/** Starts the refresh: the 1.7 s beats from the release. */
export function refresh(s: Touch, c: number): Touch {
  'worklet';
  const n = copy(s);
  const d = touchD(s, c);
  n.mode = MODE_REFRESH;
  n.refAt = c;
  n.refD = Math.max(0, d);
  n.refY = pullY(d);
  n.stAt = -1;
  n.manual = true;
  return n;
}

/** The finger lets go: past the threshold it refreshes, otherwise the list springs home. */
export function release(s: Touch, c: number): Touch {
  'worklet';
  const d = touchD(s, c);
  let n: Touch;
  if (d > THRESHOLD) n = refresh(s, c);
  else {
    n = copy(s);
    n.mode = MODE_SPRING;
    n.from = d;
    n.to = d > 0 ? 0 : d < -s.overflow ? -s.overflow : d;
    n.at = c;
  }
  n.touching = false;
  n.lastTouch = c;
  return n;
}

/** Has every move ended, with the list home and the status back to "Pull to refresh"? */
function atRest(s: Touch, c: number) {
  'worklet';
  if (s.touching || s.mode === MODE_DRAG) return false;
  if (s.mode === MODE_SPRING && c < s.at + 0.35) return false;
  if (Math.abs(touchD(s, c)) > 0.01) return false;
  const st = touchStatus(s, c);
  return shownStatus(st) % 3 === 0 && st[2] >= 1;
}

/**
 Advances touch state once a frame: integrates the yo-yo's angle, settles finished moves, and 2 s
 after the last touch scrolls the list home and restarts the loop at t = 0 (the rest is the t = 0 frame).
*/
export function stepTouch(s: Touch, c: number, dt: number, autoPlay: boolean): Touch | null {
  'worklet';
  if (!s.manual) return null;
  const n = copy(s);
  const spin = touchSpin(s, c);
  n.turns = s.turns + ((s.spin + spin) / 2) * dt;
  n.spin = spin;
  if (n.mode === MODE_SPRING && c >= n.at + 0.35) {
    n.mode = MODE_REST;
    n.from = n.to;
  }
  if (!autoPlay || n.touching || c - n.lastTouch < IDLE) return n;
  if (n.mode === MODE_REST && Math.abs(n.from) > 0.01) {
    n.mode = MODE_SPRING;
    n.to = 0;
    n.at = c;
    return n;
  }
  if (atRest(n, c)) {
    const r = initialTouch(n.overflow);
    r.loopStart = c;
    return r;
  }
  return n;
}

/** The refresh beat this frame is on, for the haptic (2.05: the yo-yo hits the end of its string). */
export function beatOf(s: Touch, c: number) {
  'worklet';
  return s.manual ? touchBeat(s, c) : -1;
}

/** Reduce Motion: the yo-yo fades in at the end of its string and turns slowly while refreshing. */
export const RM_SPIN = 0.5;
export function reducedFrame(u: number): Frame {
  'worklet';
  // u: seconds since the refresh started (−1: none). The list never moves.
  if (u < 0) {
    return { d: 0, y: Y_UP, turns: 0, spin: 0, pulse: 0, status: [0, 0, 1], finger: 0, alpha: 0 };
  }
  const t = Math.min(LOOP, BEAT + u);
  const alpha = seg(u, 0, 0.2) * (1 - seg(u, 1.9, 2.1));
  return {
    d: 0,
    y: Y_DOWN,
    turns: RM_SPIN * u,
    spin: RM_SPIN,
    pulse: u,
    status: statusAt(t),
    finger: 0,
    alpha,
  };
}
