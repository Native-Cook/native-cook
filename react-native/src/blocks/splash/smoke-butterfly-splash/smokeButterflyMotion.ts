// The loop's timeline and the touch state, as worklets (helpers above their callers).

export const LOOP = 8;
/** The story time of the held, formed pose; a touch blends to it and the loop resumes from it. */
export const PEAK = 5;
export const IDLE = 2;

export type Params = {
  rise: number;
  column: number;
  halo: number;
  form: number;
  spread: number;
  flap: number;
  veins: number;
  title: number;
  tagline: number;
  light: number;
  ember: number;
};

export type Touch = {
  touching: boolean;
  start: number;
  engageFrom: number;
  /** Clock of the last release, or -1. */
  release: number;
  /** Story time = clock - base (autoPlay wraps it). */
  base: number;
  resumed: boolean;
  fingerX: number;
  fingerY: number;
  velX: number;
  velY: number;
  dragX: number;
  dragY: number;
  pullRaw: number;
  pullAtRelease: number;
  torn: boolean;
  anchorX: number;
  anchorY: number;
  lastX: number;
  lastY: number;
  lastClock: number;
};

export type Frame = {
  params: Params;
  /** Continuous loop phase for the smoke's drift and the dust (seconds, 0 until LOOP). */
  clock: number;
  /** Story time that drives the sparkles' gather and burst. */
  story: number;
  engage: number;
  stir: number;
  pull: number;
  fingerX: number;
  fingerY: number;
  velX: number;
  velY: number;
  dragX: number;
  dragY: number;
};

export function initialTouch(): Touch {
  return {
    touching: false, start: 0, engageFrom: 0, release: -1, base: 0, resumed: false,
    fingerX: 196.5, fingerY: 360, velX: 0, velY: 0, dragX: 0, dragY: 0,
    pullRaw: 0, pullAtRelease: 0, torn: false, anchorX: 0, anchorY: 0, lastX: 0, lastY: 0, lastClock: 0,
  };
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}
export function easeOut(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return 1 - Math.pow(1 - u, 3);
}
export function easeIn(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return u * u * u;
}
export function easeInOut(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
export function wrap(t: number) {
  'worklet';
  const r = t % LOOP;
  return r < 0 ? r + LOOP : r;
}
function beat(t: number, a: number, b: number) {
  'worklet';
  if (t <= a || t >= b) return 0;
  const s = Math.sin((Math.PI * (t - a)) / (b - a));
  return s * s;
}

export function params(t: number): Params {
  'worklet';
  const rise = t < 6.5 ? easeOut(seg(t, 0.5, 2.1)) : 0;
  const halo = easeOut(seg(t, 1.5, 2.8)) * (1 - easeInOut(seg(t, 6.0, 7.3)));
  const form = easeInOut(seg(t, 1.6, 3.0)) * (1 - easeIn(seg(t, 5.0, 6.0)));
  return {
    rise,
    column: rise * (1 - easeInOut(seg(t, 1.7, 2.9))),
    halo,
    form,
    spread: easeOut(seg(t, 5.0, 6.6)) * (1 - seg(t, 7.3, 7.4)),
    flap: beat(t, 3.35, 3.9) + beat(t, 3.95, 4.5),
    veins: t < 6.5 ? easeOut(seg(t, 2.4, 3.2)) : 0,
    title: easeOut(seg(t, 2.9, 3.4)) * (1 - easeIn(seg(t, 5.6, 6.1))),
    tagline: easeOut(seg(t, 3.05, 3.55)) * (1 - easeIn(seg(t, 5.65, 6.15))),
    light: 0.65 + 0.35 * Math.max(form, halo * 0.6),
    // A faint incense-like thread at rest: it feeds the rising wisp and comes back at the end.
    ember: 1 - easeInOut(seg(t, 0.6, 1.6)) + easeInOut(seg(t, 6.6, 7.6)),
  };
}

function mixParams(a: Params, b: Params, k: number): Params {
  'worklet';
  return {
    rise: a.rise + (b.rise - a.rise) * k,
    column: a.column + (b.column - a.column) * k,
    halo: a.halo + (b.halo - a.halo) * k,
    form: a.form + (b.form - a.form) * k,
    spread: a.spread + (b.spread - a.spread) * k,
    flap: a.flap + (b.flap - a.flap) * k,
    veins: a.veins + (b.veins - a.veins) * k,
    title: a.title + (b.title - a.title) * k,
    tagline: a.tagline + (b.tagline - a.tagline) * k,
    light: a.light + (b.light - a.light) * k,
    ember: a.ember + (b.ember - a.ember) * k,
  };
}

export function engageAt(s: Touch, clock: number) {
  'worklet';
  const held = s.touching || (s.release >= 0 && clock < s.release + IDLE);
  if (!held) return 0;
  return s.engageFrom + (1 - s.engageFrom) * easeOut((clock - s.start) / 0.45);
}

function stirAt(s: Touch, clock: number) {
  'worklet';
  if (s.touching) return easeOut((clock - s.start) / 0.25);
  if (s.release < 0 || clock - s.release >= 4) return 0;
  return Math.exp(-(clock - s.release) / 0.45);
}

function pullAt(s: Touch, clock: number) {
  'worklet';
  if (s.touching) return s.pullRaw;
  if (s.release < 0) return 0;
  const age = Math.max(0, clock - s.release);
  const tau = 0.13;
  return s.pullAtRelease * (1 + age / tau) * Math.exp(-age / tau);
}

export function beginTouch(s: Touch, x: number, y: number, clock: number): Touch {
  'worklet';
  const current = engageAt(s, clock);
  let base = s.base;
  let resumed = s.resumed;
  if (s.release >= 0 && clock >= s.release + IDLE) {
    base = s.release + IDLE - PEAK;
    resumed = true;
  }
  return {
    touching: true, start: clock, engageFrom: current, release: -1, base, resumed,
    fingerX: x, fingerY: y, velX: 0, velY: 0, dragX: 0, dragY: 0,
    pullRaw: 0, pullAtRelease: 0, torn: false, anchorX: x, anchorY: y, lastX: x, lastY: y, lastClock: clock,
  };
}

/** The new state, and whether the wings tore (pull passed 0.6) for a haptic. */
export function moveTouch(s: Touch, x: number, y: number, clock: number): { next: Touch; tore: boolean } {
  'worklet';
  const next: Touch = Object.assign({}, s);
  const dt = clock - s.lastClock;
  if (dt > 0.004) {
    next.velX = s.velX + ((x - s.lastX) / dt - s.velX) * 0.3;
    next.velY = s.velY + ((y - s.lastY) / dt - s.velY) * 0.3;
    next.lastX = x;
    next.lastY = y;
    next.lastClock = clock;
  }
  next.fingerX = x;
  next.fingerY = y;
  next.dragX = x - s.anchorX;
  next.dragY = y - s.anchorY;
  next.pullRaw = Math.min(1, Math.hypot(next.dragX, next.dragY) / 220);
  let tore = false;
  if (next.pullRaw > 0.6 && !s.torn) {
    next.torn = true;
    tore = true;
  }
  if (next.pullRaw < 0.4) next.torn = false;
  return { next, tore };
}

export function endTouch(s: Touch, clock: number): Touch {
  'worklet';
  if (!s.touching) return s;
  const next: Touch = Object.assign({}, s);
  next.touching = false;
  next.release = clock;
  next.pullAtRelease = s.pullRaw;
  return next;
}

export function frameAt(clock: number, s: Touch, autoPlay: boolean, reduced: boolean): Frame {
  'worklet';
  if (reduced) {
    return {
      params: params(PEAK), clock: PEAK, story: PEAK, engage: 0, stir: 0, pull: 0,
      fingerX: 196.5, fingerY: 360, velX: 0, velY: 0, dragX: 0, dragY: 0,
    };
  }
  const engage = engageAt(s, clock);
  let story = 0;
  if (s.release >= 0 && !s.touching && clock >= s.release + IDLE) {
    const base = s.release + IDLE - PEAK;
    story = autoPlay ? wrap(clock - base) : Math.min(LOOP, clock - base);
  } else if (autoPlay) {
    story = wrap(clock - s.base);
  } else if (s.resumed) {
    story = Math.min(LOOP, clock - s.base);
  }
  if (story >= LOOP) story = 0;
  let p = params(story);
  if (engage > 0) p = mixParams(p, params(PEAK), engage);
  return {
    params: p, clock: autoPlay ? wrap(clock) : story, story, engage,
    stir: stirAt(s, clock), pull: pullAt(s, clock),
    fingerX: s.fingerX, fingerY: s.fingerY, velX: s.velX, velY: s.velY, dragX: s.dragX, dragY: s.dragY,
  };
}
