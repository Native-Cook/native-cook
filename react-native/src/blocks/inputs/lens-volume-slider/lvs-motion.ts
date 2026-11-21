// Lens Volume Slider: the autoplay timeline (spec §4 with the Pro addendum's timing) as a pure function of the loop
// time, so every frame of the loop is reproducible and the three stacks can match it frame for frame. Times are in
// seconds, values 0..100.

import { G, PT_PER_UNIT, SPRING } from './lvs-theme';

export const LOOP = 8;
/** The poster: the drop at full speed (lead ≈ 10 pt), its ball holding the plate's shadow, mesh and bright seam line
 *  flipped. */
export const POSTER = 0.88;

/** 0 before a, 1 after b. */
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
};
export const clamp = (v: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max(v, a), b);
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const easeOut = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k) * (1 - k);
};
export const smooth = (k: number) => {
  'worklet';
  return k * k * (3 - 2 * k);
};
export const easeInOutQuad = (k: number) => {
  'worklet';
  return k < 0.5 ? 2 * k * k : 1 - 2 * (1 - k) * (1 - k);
};
export const easeInOutCubic = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};

/**
 * A damped spring (mass 1) released at time 0 from `from` towards `to` with initial velocity v0 (units/s): its
 * position at time t. Closed form, so the loop stays a pure function of time.
 */
export const springAt = (t: number, from: number, to: number, v0: number, d: number, k: number) => {
  'worklet';
  if (t <= 0) return from;
  const x0 = from - to;
  const w0 = Math.sqrt(k);
  const z = d / (2 * w0);
  if (z < 1) {
    const wd = w0 * Math.sqrt(1 - z * z);
    const e = Math.exp(-z * w0 * t);
    return to + e * (x0 * Math.cos(wd * t) + ((v0 + z * w0 * x0) / wd) * Math.sin(wd * t));
  }
  if (z === 1) {
    const e = Math.exp(-w0 * t);
    return to + e * (x0 + (v0 + w0 * x0) * t);
  }
  const s = Math.sqrt(z * z - 1);
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

/** Speed (units/s) → how far the drop's end leads its value (pt): speed × 0.06 s, with a soft knee at 16 pt. */
export const stretchFor = (speed: number) => {
  'worklet';
  const x = speed * PT_PER_UNIT * G.leadTime;
  const m = G.maxStretch;
  return x / Math.pow(1 + Math.pow(Math.abs(x) / m, 4), 0.25);
};

/**
 * The numeral's odometer: holds a digit, then rolls to the next one around the rounding point. `w` is the share of a
 * unit the roll takes: 0.28 when slow, narrowing towards a plain switch when the value runs faster than a frame can
 * show a roll (a fast scrub would otherwise show half digits on every frame).
 */
export const detent = (x: number, w = 0.28) => {
  'worklet';
  const f = x - Math.floor(x);
  const ww = Math.max(w, 0.001);
  const k = clamp((f - 0.5 + ww / 2) / ww, 0, 1);
  return Math.floor(x) + k * k * (3 - 2 * k);
};

// ---- the value ----

const REST = 52;
// 0.20–0.32 finger down (the drop lifts); 0.34–0.55 it gives 2 units the other way first (the seam rest stays
// ≤ 0.6 s: 7.55–8.0 plus 0–0.2)
const DOWN = 0.2;
const BACK = 0.34;
// 0.55–1.12: the scrub 50 → 80, fastest around 1.0 s, let go still moving (Hermite, end slope 0.8 × the travel); it
// settles at 85, where the ball holds the speaker's mesh / shadow silhouette flipped
const SCRUB = { a: 0.55, b: 1.12, from: 50, to: 80, m1: 0.8 * 30 };
const RELEASE_V = SCRUB.m1 / (SCRUB.b - SCRUB.a);
const SETTLE = 85; // where the release aims: 80 + its speed × 0.12 s, rounded
// 2.75: the flick left, aimed 6 % past 0 so the drop arrives with speed and snaps into the marble at 4.1 s
const FLICK = { a: 2.75, dur: 1.62, over: 1.06 };
// 5.2: pulled out of the marble like honey, aimed 8 % past 100 so it slams into the right cap
const PULL = { a: 5.2, dur: 1.5, over: 1.08 };
const RETURN_AT = 7.1;

const vRaw = (t: number) => {
  'worklet';
  if (t < BACK) return REST;
  if (t < SCRUB.a) return REST - 2 * easeOut(seg(t, BACK, SCRUB.a));
  if (t < SCRUB.b) {
    const s = seg(t, SCRUB.a, SCRUB.b);
    const h01 = -2 * s * s * s + 3 * s * s;
    const h11 = s * s * s - s * s;
    return SCRUB.from + (SCRUB.to - SCRUB.from) * h01 + SCRUB.m1 * h11;
  }
  if (t < FLICK.a) return springAt(t - SCRUB.b, SCRUB.to, SETTLE, RELEASE_V, SPRING.release.d, SPRING.release.k);
  if (t < PULL.a) {
    const k = seg(t, FLICK.a, FLICK.a + FLICK.dur);
    return Math.max(0, SETTLE * (1 - FLICK.over * easeInOutQuad(k)));
  }
  if (t < RETURN_AT) {
    const k = seg(t, PULL.a, PULL.a + PULL.dur);
    return Math.min(100, 100 * PULL.over * easeInOutCubic(k));
  }
  return springAt(t - RETURN_AT, 100, REST, 0, SPRING.glide.d, SPRING.glide.k);
};

/** The value at loop time t (wraps). */
export const valueAt = (t: number) => {
  'worklet';
  const u = ((t % LOOP) + LOOP) % LOOP;
  return vRaw(u);
};

/** First time in [a, b] where the value crosses `level` (bisection on a monotone stretch). */
const crossing = (a: number, b: number, level: number) => {
  let lo = a;
  let hi = b;
  const up = vRaw(b) > vRaw(a);
  for (let i = 0; i < 40; i++) {
    const m = (lo + hi) / 2;
    if (vRaw(m) >= level === up) hi = m;
    else lo = m;
  }
  return hi;
};

/** Moments of the loop, solved once from the value curve. */
export const MOMENTS = {
  arc2Down: crossing(FLICK.a, FLICK.a + FLICK.dur, 34), // second arc retracts
  hitLeft: crossing(FLICK.a, FLICK.a + FLICK.dur, 0.0001), // the drop snaps into the marble
  arc1Up: crossing(PULL.a, PULL.a + PULL.dur, 0.5), // un-mute
  arc2Up: crossing(PULL.a, PULL.a + PULL.dur, 34),
  hitRight: crossing(PULL.a, PULL.a + PULL.dur, 99.9999), // the drop slams into the right cap
};

/** The photo's zoom follows the value through a ~400 ms critically damped response (louder is closer). */
const ZOOM_W = (() => {
  const w: number[] = [];
  let sum = 0;
  for (let i = 0; i < 13; i++) {
    const tau = i * 0.05;
    const x = 100 * tau * Math.exp(-10 * tau) + (i === 0 ? 0.02 : 0);
    w.push(x);
    sum += x;
  }
  return w.map((x) => x / sum);
})();
const zoomValueAt = (t: number) => {
  'worklet';
  let z = 0;
  for (let i = 0; i < ZOOM_W.length; i++) z += ZOOM_W[i] * valueAt(t - i * 0.05);
  return z;
};

/** Everything the screen draws, for one moment. */
export type Frame = {
  v: number; // value (float)
  vTens: number; // the value the tens digit reads (30 ms later)
  vHund: number; // the value the hundreds digit reads (60 ms later)
  stretch: number; // pt: + the drop's end leads to the right of its value
  lift: number; // 0..1: a finger holds the drop up (h 88 → 96, ×1.8 → ×2.1, deeper shadow)
  dimple: number; // 0..1: the dent under the finger
  dimpleX: number; // its x on the canvas (pt)
  sweep: number; // release highlight along the top arc 0..1, -1 = none
  glint: number; // the resting glint's place along the top rim 0..1
  squash: number; // bead squash: width × s, height × (2 − s)
  honey: number; // pt taken off the drop's height while it is pulled out of the marble
  trackStretch: number; // pt: the capsule's right end pushed out
  flash: number; // cap hit: track rim flash and the caustic's spill arc 0..1
  arc1: number; // glyph trims 0..1
  arc2: number;
  slash: number;
  caustic: number; // caustic strength multiplier
  marble: number; // 0..1: the mute marble (ball lens ×1.6, caustic dot)
  dim: number; // 0..1: photo exposure and saturation down to ×0.8
  zoomV: number; // the value the photo's zoom follows
};

/** Finger down: the drop rises in `up` s from a; let go at b it sinks with one wobble (below rest, then back). */
const press = (t: number, a: number, up: number, b: number) => {
  'worklet';
  if (t < a) return 0;
  if (t < b) return easeOut(seg(t, a, a + up));
  return springAt(t - b, easeOut(seg(b, a, a + up)), 0, 0, SPRING.sink.d, SPRING.sink.k);
};

const liftAt = (t: number) => {
  'worklet';
  const M = MOMENTS;
  if (t < 2.4) return press(t, DOWN, 0.12, SCRUB.b); // the scrub
  if (t < 4.9) return press(t, 2.6, 0.12, 3.32); // the flick: down for the throw only
  if (t < 6.8) return press(t, 5.0, 0.14, M.hitRight - 0.02); // the marble is picked up and pulled out
  return press(t, 6.96, 0.08, RETURN_AT); // a tap at 52 sends it home
};

/** The frame at loop time t. */
export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const v = vRaw(t);
  // the drop's end leads by its speed over the last 80 ms (the spec's low-pass)
  const speed = (v - valueAt(t - 0.08)) / 0.08;
  const stretch = stretchFor(speed);
  const M = MOMENTS;

  // glyph: the second arc lives above 34, the first above 0; the slash marks mute
  let arc2 = 1;
  let arc1 = 1;
  let slash = 0;
  if (t >= FLICK.a && t < PULL.a) {
    arc2 = 1 - seg(t, M.arc2Down, M.arc2Down + 0.12);
    arc1 = 1 - seg(t, M.hitLeft + 0.02, M.hitLeft + 0.14);
    slash = seg(t, M.hitLeft + 0.08, M.hitLeft + 0.24);
  } else if (t >= PULL.a && t < RETURN_AT) {
    slash = 1 - seg(t, M.arc1Up - 0.06, M.arc1Up + 0.06);
    arc1 = seg(t, M.arc1Up, M.arc1Up + 0.12);
    arc2 = seg(t, M.arc2Up, M.arc2Up + 0.14);
  }

  const inMarble = t > M.hitLeft && t < PULL.a + 0.6;
  // the snap: 1.08 × 0.92 at its widest, then pop to round
  const squash = inMarble ? springAt(t - M.hitLeft, 1, 1, 2.4, SPRING.pop.d, SPRING.pop.k) : 1;
  const marble = smooth(seg(t, M.hitLeft, M.hitLeft + 0.16)) * (1 - smooth(seg(t, PULL.a + 0.05, PULL.a + 0.4)));
  const dim = smooth(seg(t, M.hitLeft, M.hitLeft + 0.5)) * (1 - smooth(seg(t, PULL.a, PULL.a + 0.5)));
  // pulled out like honey: the drop thins to 80 pt mid-pull and fills back to 88 as it reaches the cap
  const honey = t > PULL.a && t < M.hitRight + 0.2 ? 8 * Math.sin(Math.PI * seg(t, PULL.a + 0.1, M.hitRight + 0.1)) : 0;
  const tr = t > M.hitRight ? springAt(t - M.hitRight, 0, 0, 265, SPRING.weighty.d, SPRING.weighty.k) : 0;
  const flash = t > M.hitRight ? Math.exp(-(t - M.hitRight) / 0.16) * seg(t, M.hitRight, M.hitRight + 0.03) : 0;
  const sweep = t >= SCRUB.b && t <= SCRUB.b + 0.3 ? seg(t, SCRUB.b, SCRUB.b + 0.3) : -1;
  const lift = liftAt(t);

  const w = 88 + PT_PER_UNIT * v + stretch;
  const fillRight = 32 + Math.max(w, 88);

  return {
    v,
    vTens: valueAt(t - 0.03),
    vHund: valueAt(t - 0.06),
    stretch,
    lift,
    dimple: lift,
    dimpleX: fillRight - Math.min(72, Math.max(w, 88) / 2),
    sweep,
    glint: (t / 4) % 1,
    squash,
    honey,
    trackStretch: Math.max(tr, -0.6),
    flash,
    arc1,
    arc2,
    slash,
    caustic: 0.8 + 0.4 * Math.min(Math.abs(speed) / 40, 1),
    marble,
    dim,
    zoomV: zoomValueAt(t),
  };
};

/** The photo's idle drift (pt): a slow handheld breath, periodic with the loop. */
export const driftAt = (t: number) => {
  'worklet';
  const a = (2 * Math.PI * t) / LOOP;
  return { x: G.plate.driftX * Math.sin(a), y: G.plate.driftY * Math.sin(2 * a + 1) };
};
