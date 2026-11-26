/**
 * The clocks of the screen, stepped on the UI thread once per frame.
 *
 * In the loop every value is a pure function of the loop time t (`loopFrame`): the open fraction P (0 → 1 from 0.4 to
 * 4.4 s, ease-in-out quad), the rake (4.4–5.4 s) and the fold back (5.4–6.5 s). The progress is watched, not driven:
 * a touch pauses it (a tap on the sculpture while it opens also pulses the front catch); 2 s after the last touch the
 * fan folds closed from where it is and the loop restarts at t = 0. After a touch, the next 100 % gives a light haptic
 * and the announcement (the untouched gallery stays silent). Reduce Motion: no opening; the whole sculpture fades in,
 * the percent counts, a gentle brightness rise stands in for the rake, and it holds at 100 % (a tap replays it).
 */

/** Loop period, s. */
export const LOOP = 7.0;
const OPEN_FROM = 0.4;
const DONE_FROM = 4.4;
const FOLD_FROM = 5.4;
const REST_FROM = 6.5;
const FOLD = REST_FROM - FOLD_FROM;
const IDLE = 2.0;
const PULSE = 0.3;
const PULSE_GAIN = 0.4;
const STILL_FADE = 0.4;
const LIFT = 0.1;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function io(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}

export function openAt(t: number) {
  'worklet';
  if (t < OPEN_FROM) return 0;
  if (t < DONE_FROM) return io(seg(t, OPEN_FROM, DONE_FROM));
  if (t < FOLD_FROM) return 1;
  if (t < REST_FROM) return 1 - io(seg(t, FOLD_FROM, REST_FROM));
  return 0;
}

export type Frame = {
  /** Open fraction (the shader's), rake strength and position, catch boost, sculpture fade and lift. */
  P: number;
  sw: number;
  T: number;
  boost: number;
  fade: number;
  lift: number;
  /** The progress shown (0…1) and whether it is done (the title and meta change). */
  pct: number;
  done: number;
};

export function loopFrame(t: number): Frame {
  'worklet';
  const P = openAt(t);
  const done = t >= DONE_FROM && t < FOLD_FROM;
  return {
    P, sw: done ? Math.sin(seg(t, DONE_FROM, FOLD_FROM) * Math.PI) : 0, T: seg(t, DONE_FROM, FOLD_FROM), boost: 1, fade: 1,
    lift: 1, pct: P, done: done ? 1 : 0,
  };
}

export const MODE = { auto: 0, live: 1, still: 2 } as const;

export type Core = {
  mode: number;
  last: number;
  start: number;
  /** Live: the loop time it paused at, and the fold back (−1: not folding; else its clock, from P0). */
  t: number;
  fold: number;
  P0: number;
  sw0: number;
  /** The catch pulse's clock (s), or −1. */
  pulse: number;
  touches: number;
  idle: number;
  /** A touch since the last 100 %: the next 100 % buzzes and announces. */
  watching: boolean;
  /** Reduce Motion's clock (s), and whether the sculpture has faded in (a replay keeps it shown). */
  still: number;
  shown: boolean;
  /** Events for the JS side: one per 100 % while watched. */
  dones: number;
  out: Frame;
};

export function newCore(): Core {
  return {
    mode: MODE.auto, last: -1, start: -1, t: 0, fold: -1, P0: 0, sw0: 0, pulse: -1, touches: 0, idle: 0,
    watching: false, still: 0, shown: false, dones: 0, out: loopFrame(0),
  };
}

function loopTime(c: Core) {
  'worklet';
  return c.start < 0 ? 0 : ((c.last - c.start) / 1000) % LOOP;
}

/** A touch on the screen: the progress pauses where it is. */
export function touchDown(c: Core, onSculpture: boolean) {
  'worklet';
  c.watching = true;
  if (c.mode === MODE.still) {
    // Reduce Motion: a tap replays the count; the sculpture stays shown.
    c.shown = c.shown || c.still >= STILL_FADE;
    c.still = 0;
    return;
  }
  if (c.mode === MODE.auto) {
    c.mode = MODE.live;
    c.t = loopTime(c);
    c.fold = -1;
  }
  c.touches += 1;
  c.idle = 0;
  // The pulse is for the opening only (not the fold back).
  if (onSculpture && c.fold < 0 && c.t > OPEN_FROM && c.t < DONE_FROM) c.pulse = 0;
}

export function touchUp(c: Core) {
  'worklet';
  c.touches = Math.max(0, c.touches - 1);
  c.idle = 0;
}

function stillFrame(t: number, shown: boolean): Frame {
  'worklet';
  const pct = openAt(Math.min(t, DONE_FROM));
  const rise = t >= DONE_FROM ? Math.sin(seg(t, DONE_FROM, FOLD_FROM) * Math.PI) : 0;
  return { P: 1, sw: 0, T: 0, boost: 1, fade: shown ? 1 : seg(t, 0, STILL_FADE), lift: 1 + LIFT * rise, pct, done: t >= DONE_FROM ? 1 : 0 };
}

export function step(c: Core, now: number, autoPlay: boolean) {
  'worklet';
  const dt = c.last < 0 ? 0 : Math.min(Math.max((now - c.last) / 1000, 0), 0.05);
  c.last = now;

  if (c.mode === MODE.still) {
    const before = c.still;
    c.still += dt;
    if (before < DONE_FROM && c.still >= DONE_FROM && c.watching) {
      c.dones += 1;
      c.watching = false;
    }
    c.out = stillFrame(c.still, c.shown);
    return;
  }
  if (c.mode === MODE.auto) {
    if (!autoPlay) {
      c.mode = MODE.live;
      c.t = 0;
    } else {
      if (c.start < 0) c.start = now;
      const before = c.out.done;
      c.out = loopFrame(loopTime(c));
      if (c.out.done > 0 && before === 0 && c.watching) {
        c.dones += 1;
        c.watching = false;
      }
      return;
    }
  }

  // Live: paused at c.t, then folding back to the loop's start.
  let F = loopFrame(c.t);
  if (c.pulse >= 0) {
    c.pulse += dt;
    if (c.pulse >= PULSE) c.pulse = -1;
  }
  if (c.fold >= 0) {
    c.fold = Math.min(FOLD, c.fold + dt);
    const k = 1 - io(c.fold / FOLD);
    F = { ...F, P: c.P0 * k, sw: c.sw0 * k, pct: c.P0 * k, done: 0 };
    if (c.fold >= FOLD) {
      c.mode = MODE.auto;
      c.start = now;
      c.out = loopFrame(0);
      return;
    }
  }
  F.boost = c.pulse >= 0 ? 1 + PULSE_GAIN * Math.sin((Math.PI * c.pulse) / PULSE) : 1;
  c.out = F;

  if (c.touches > 0 || c.fold >= 0) {
    c.idle = 0;
    return;
  }
  c.idle += dt;
  if (!autoPlay || c.idle < IDLE) return;
  // Fold closed from where it is, then the loop restarts at t = 0.
  c.fold = 0;
  c.P0 = F.P;
  c.sw0 = F.sw;
}

export function setStill(c: Core, still: boolean) {
  'worklet';
  if (still && c.mode !== MODE.still) {
    c.mode = MODE.still;
    c.still = 0;
    c.shown = false;
  } else if (!still && c.mode === MODE.still) {
    c.mode = MODE.auto;
    c.start = -1;
  }
}
