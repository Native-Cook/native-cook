/**
 * Saved Places Map — the timeline. Every value on screen is a pure function of the loop time `t`
 * (seconds, 0 ≤ t < LOOP): nothing accumulates between frames, so t = LOOP equals t = 0 (the pin's still).
 *
 *   0.00–1.00  rest = the pin (sheet at its peek detent, flat map, top of the map under a progressive blur)
 *   0.85–0.97  the grabber is taken (widens 1.12×, darkens), as a finger would
 *   1.00       hero: the sheet springs up to the half detent (+150 pt) and the map pitches into 3D with it —
 *              60° tilt, the camera dips in (scale 1.8, overshoot of the sheet's spring = the dip), a hazy
 *              horizon appears under the blur; chips and the locate button ride the sheet and fade
 *   1.25–2.05  the five cafés stand up as 3D pins, far ones first: stalk, a light beam, a long contact shadow
 *              on the ground and a landing ripple
 *   1.50–2.50  the 38 loose flags, thumbs and stars glide on arcs into five count bubbles, one per pin,
 *              nearest first; each bubble's count rolls up as they land
 *   2.80–3.20  the list card's checkbox is pressed and ticks; 2.86 the counter rolls 0/59 → 1/59, the café's
 *              pin pops with a check badge and its beam flares
 *   4.00–4.40  unticked: badge and tick fade, the counter rolls back
 *   4.60       the grabber is taken again; 4.75 the sheet settles back to its peek (critically damped), the map
 *              lies flat, the pins lie down and the glyphs scatter back to their places
 *   6.00–7.50  rest
 *
 * Springs are closed-form step responses (ω natural frequency in rad/s, ζ damping ratio) so the native
 * ports reproduce them value for value; Reanimated's touch springs use the same ω/ζ (stiffness ω², damping 2ζω).
 */

export const LOOP = 7.5;

/** After the last touch, the screen waits this long, then settles to rest and the loop restarts. */
export const RESUME_AFTER = 2000;

/** Half detent: how far the sheet rises (pt). */
export const RISE = 150;
/**
 * The map's 3D pitch, driven by the sheet progress p (0 = flat, the pin; 1 = half detent). Ref px (÷3 = pt).
 * Ground transform (RN order): translateY(lift·p) · perspective(P) · rotateX(pitch·p) · scale(1 + (scale−1)·p),
 * about (603, originY). `project()` is the same maths for a ground point, so pins sit exactly on their spots.
 */
export const TILT = {
  pitch: 60,
  perspective: 2700,
  scale: 1.8,
  originX: 603,
  originY: 1140,
  lift: -225,
  /** The sheet's spring overshoot carries the camera a little past 1 (the dip), never further than this. */
  max: 1.08,
  /** Billboards (pins, glyphs) scale by perspective relative to this mean f·s, so the cafés keep their size on average. */
  norm: 1.55,
  /** Haze over the horizon: opacity over this range of p. */
  fog: [0.25, 1] as const,
} as const;

/** Pins standing up: pin i (far first) stands over p ∈ [start + step·rank, + span], with a back-out hop. */
export const STAND = {
  start: 0.25,
  step: 0.07,
  span: 0.45,
  /** back-out overshoot constant (≈ 9 % hop). */
  back: 1.4,
  /** Stalk length under the disc, ref px. */
  stalk: 96,
} as const;

/** Light beam rising from each pin's foot (ref px), and the visited flare. */
export const BEAM = { h: 360, w: 22, core: 4, opacity: 0.9, flare: 4 } as const;

/** Long contact shadow on the ground, from the pin's foot away from a low light (ref px, ground units). */
export const SHADOW = { len: 150, w: 30, angle: 28, opacity: 0.34 } as const;

/** Landing ripple on the ground at the pin's foot (ref px). */
export const RIPPLE = { d: 44, grow: 1.9, at: 0.55 } as const;
/** Chips and the locate button fade over this range of sheet progress. */
export const CONTROLS_FADE = [0.1, 0.5] as const;

export const T = {
  rise: 1.0,
  clusterIn: 1.5,
  press: 2.8,
  check: 2.9,
  unpress: 4.0,
  uncheck: 4.08,
  fall: 4.75,
  clusterOut: 4.75,
} as const;

export const SPRINGS = {
  /** Sheet to the half detent (and the camera with it): a slow, heavy move with a small overshoot = the dip. */
  sheetRise: { omega: 7.5, zeta: 0.72 },
  /** Sheet back to rest: no overshoot past the pin's frame. */
  sheetFall: { omega: 8, zeta: 1 },
  /** Counter digit roll. */
  roll: { omega: 20, zeta: 0.7 },
  /** Marker pop (impulse response): +12 % peak, −3 % undershoot. */
  pop: { omega: 18, zeta: 0.4, amp: 0.22 },
  /** Press release. */
  press: { omega: 22, zeta: 0.6 },
} as const;

export const CLUSTER = {
  /** Seconds for the whole gather (and for the scatter). Touch chases the sheet at the same rate. */
  dur: 1.0,
  /** Glyph i starts at span · rank / (n − 1) of the gather, nearest first. */
  span: 0.4,
  /** Each glyph's own share of the gather. */
  glide: 0.6,
  /** Arc bulge, as a fraction of the glyph's distance (all clockwise: they swirl in). */
  arc: 0.15,
  /** Scale on arrival. */
  endScale: 0.55,
  /** A glyph fades over the last 30 % of its path, and counts as landed over the last 25 %. */
  fadeFrom: 0.7,
  landFrom: 0.75,
  /** The bubble swells a little each time a glyph lands. */
  gulp: 0.06,
  bubbleFrom: 0.9,
} as const;

/** Grabber taken: widens and darkens just before the sheet moves (autoplay) or while a finger drags it. */
export const GRAB = { scale: 1.12, lead: 0.15, in: 0.12, out: 0.15, hold: 0.15 } as const;

/** A second toggle is taken only after the first has landed (s), so the tick never jumps. */
export const TOGGLE_GAP = 0.3;

export const PRESS = {
  checkbox: 0.9,
  tile: 0.97,
  inMs: 100,
} as const;

export const VISIT = {
  fill: 0.15,
  draw: [0.05, 0.3] as const,
  roll: 0.06,
  badge: [0.06, 0.26] as const,
  fadeOut: 0.15,
  /** Reduced motion: every change is a fade of this length. */
  reducedFade: 0.2,
} as const;

export const DRAG = {
  /** Rubber band past a detent: r(x) = x / (1 + k·x), so the overshoot never exceeds 1/k of the travel. */
  rubber: 5,
  /** Velocity projection (s) used to pick the detent on release. */
  project: 0.15,
  slop: 6,
} as const;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}

/** Solve the curve's x coordinate before sampling y; shared by all native ports. */
function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 18; i++) {
    const m = (lo + hi) / 2;
    const u = 1 - m;
    const at = 3 * u * u * m * x1 + 3 * u * m * m * x2 + m * m * m;
    if (at < x) lo = m;
    else hi = m;
  }
  const m = (lo + hi) / 2;
  const u = 1 - m;
  return 3 * u * u * m * y1 + 3 * u * m * m * y2 + m * m * m;
}

/** Strong ease-out: cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(p: number) {
  'worklet';
  return bezier(p, 0.23, 1, 0.32, 1);
}

/** On-screen movement: cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(p: number) {
  'worklet';
  return bezier(p, 0.77, 0, 0.175, 1);
}

/** Step response 0 → 1 of a spring released at rest (τ in s). */
export function springStep(tau: number, omega: number, zeta: number) {
  'worklet';
  if (tau <= 0) return 0;
  if (zeta >= 1) return 1 - Math.exp(-omega * tau) * (1 + omega * tau);
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * tau) * (Math.cos(wd * tau) + ((zeta * omega) / wd) * Math.sin(wd * tau));
}

/** Pop scale: 1 at τ ≤ 0, a damped bump above 1, back to 1. */
export function popScale(tau: number) {
  'worklet';
  if (tau <= 0 || tau > 1.2) return 1;
  const { omega, zeta, amp } = SPRINGS.pop;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  return 1 + amp * Math.exp(-zeta * omega * tau) * Math.sin(wd * tau);
}

/** Rubber band past the detents [0, 1]. */
export function band(s: number) {
  'worklet';
  const k = DRAG.rubber;
  if (s > 1) return 1 + (s - 1) / (1 + k * (s - 1));
  if (s < 0) return s / (1 - k * s);
  return s;
}

/** Sheet progress: 0 = peek (the pin), 1 = half detent. */
export function sheetAt(t: number) {
  'worklet';
  if (t < T.rise) return 0;
  if (t < T.fall) return springStep(t - T.rise, SPRINGS.sheetRise.omega, SPRINGS.sheetRise.zeta);
  const tau = t - T.fall;
  if (tau > 1.6) return 0;
  return 1 - springStep(tau, SPRINGS.sheetFall.omega, SPRINGS.sheetFall.zeta);
}

/** Cluster progress, linear in time (each glyph eases its own window). */
export function clusterAt(t: number) {
  'worklet';
  if (t < T.fall) return seg(t, T.clusterIn, T.clusterIn + CLUSTER.dur);
  return 1 - seg(t, T.clusterOut, T.clusterOut + CLUSTER.dur);
}

/** Checkbox press depth (0..1) at the two taps of the loop. */
export function pressAt(t: number) {
  'worklet';
  const one = (a: number) => {
    const dip = a + PRESS.inMs / 1000;
    if (t < a) return 0;
    if (t < dip) return easeOut(seg(t, a, dip));
    return 1 - springStep(t - dip, SPRINGS.press.omega, SPRINGS.press.zeta);
  };
  return Math.max(one(T.press), one(T.unpress));
}

/** Grabber taken (0..1) around the two sheet moves of the loop. */
export function grabAt(t: number) {
  'worklet';
  const one = (move: number) => {
    const a = move - GRAB.lead;
    const b = move + GRAB.hold;
    return easeOut(seg(t, a, a + GRAB.in)) * (1 - easeOut(seg(t, b, b + GRAB.out)));
  };
  return Math.max(one(T.rise), one(T.fall));
}

/** Visited state as (direction, seconds since the toggle). τ is large at rest. */
export function visitAt(t: number) {
  'worklet';
  if (t >= T.check && t < T.uncheck) return { dir: 1, tau: t - T.check };
  if (t >= T.uncheck) return { dir: 0, tau: t - T.uncheck };
  return { dir: 0, tau: 99 };
}

export type VisitFrame = { fill: number; draw: number; roll: number; badge: number; pop: number };

/** Everything the visited toggle drives, from its direction and the time since it was toggled. */
export function visitFrame(dir: number, tau: number, reduced: boolean): VisitFrame {
  'worklet';
  if (reduced) {
    const p = seg(tau, 0, VISIT.reducedFade);
    const v = dir === 1 ? p : 1 - p;
    return { fill: v, draw: v, roll: v, badge: v, pop: 1 };
  }
  if (dir === 1) {
    return {
      fill: easeOut(seg(tau, 0, VISIT.fill)),
      draw: easeOut(seg(tau, VISIT.draw[0], VISIT.draw[1])),
      roll: springStep(tau - VISIT.roll, SPRINGS.roll.omega, SPRINGS.roll.zeta),
      badge: easeOut(seg(tau, VISIT.badge[0], VISIT.badge[1])),
      pop: popScale(tau - VISIT.roll),
    };
  }
  const out = 1 - easeOut(seg(tau, 0, VISIT.fadeOut));
  return {
    fill: out,
    draw: out,
    roll: 1 - springStep(tau - 0.02, SPRINGS.roll.omega, SPRINGS.roll.zeta),
    badge: out,
    pop: 1,
  };
}

/** One glyph's own progress along its path, from the cluster progress. */
export function glyphProgress(c: number, delay: number) {
  'worklet';
  return easeInOut(seg(c, delay, delay + CLUSTER.glide));
}

/** Pitch progress from the sheet progress (rubber band and overshoot included, capped). */
export function tiltOf(sheet: number) {
  'worklet';
  return sheet < 0 ? 0 : sheet > TILT.max ? TILT.max : sheet;
}

export type Projected = { x: number; y: number; f: number; m: number };

/**
 * Screen position (ref px) of a ground point (ref px, flat map) at pitch progress p, its perspective factor f
 * and the billboard scale m (1 when flat).
 */
export function project(x: number, y: number, p: number): Projected {
  'worklet';
  const s = 1 + (TILT.scale - 1) * p;
  const th = (TILT.pitch * p * Math.PI) / 180;
  const dx = (x - TILT.originX) * s;
  const dy = (y - TILT.originY) * s;
  const z = dy * Math.sin(th);
  const f = TILT.perspective / (TILT.perspective - z);
  return {
    x: TILT.originX + dx * f,
    y: TILT.originY + dy * Math.cos(th) * f + TILT.lift * p,
    f,
    m: (f * s) / (1 + (TILT.norm - 1) * p),
  };
}

/** back-out easing (overshoots, then settles at 1). */
export function backOut(p: number) {
  'worklet';
  const c = STAND.back;
  const q = clamp01(p) - 1;
  return 1 + (c + 1) * q * q * q + c * q * q;
}

/** Linear stand progress of pin `rank` (0 = farthest) at pitch progress p. */
export function standLinear(p: number, rank: number) {
  'worklet';
  const a = STAND.start + STAND.step * rank;
  return seg(p, a, a + STAND.span);
}

/** How far pin `rank` stands: 0 flat → 1 standing, with a hop. */
export function standOf(p: number, rank: number) {
  'worklet';
  return backOut(standLinear(p, rank));
}

/** Screen centre (ref px) of a café's disc and its billboard scale, for its ground anchor (cx, cy). */
export function discAt(cx: number, cy: number, p: number, rank: number, radius: number) {
  'worklet';
  const g = project(cx, cy, p);
  const st = standOf(p, rank);
  return { x: g.x, y: g.y - st * (STAND.stalk + radius) * g.m, m: g.m, st, gx: g.x, gy: g.y };
}
