// Beam Control — the loop, the optics and the gesture constants. Every frame of the autoplay is a pure function of
// the loop time t (no accumulated state), so the Swift and Kotlin ports can match it frame for frame.
//
// The state is three numbers:
//   b  brightness (flux) 0 … 1          — a level: it never springs
//   w  spread 0 (pencil) … 1 (wide fan) — focus: it settles with one overshoot, like a focusing ring hunting
//   m  the capsule 0 (closed into the island) … 1 (open panel)
// The optics derive everything else from b and w: the half-angle, the reach, how hot the core burns (the same flux
// through a smaller solid angle), the arc that marks the reach, the dotted arc that marks the maximum.
//
// Loop (LOOP = 9 s; payoff at 1.6 s):
//   t start | t end | element  | property          | from → to        | curve / spring
//   0.00    | 0.50  | all      | rest (b 1, w 1)   | the pin          | —
//   0.45    | 0.80  | capsule  | press scale       | 1 → 1.012        | snap (0.35, 0.85)
//   0.50    | 1.54  | beam     | w                 | 1 → 0            | accelerating x^1.8, 1.04 s, finger-scrubbable
//   1.54    | 2.20  | beam     | focus hunt        | 0 → −.046 → 0   | pop impulse (19 / 195 / 1), peak 1.62 s
//   1.43    | 3.10  | beam     | landing           | reach > 200 pt  | spot 46 × 10, halo 60; reach never > 214.65
//   1.60    | 3.10  | beam     | pencil hold       | 3 % shimmer     | 6 whole cycles per LOOP; one focus hunt
//           |       | spill    | page              | .10 · b · w     | 120 pt white veil, .22 ceiling halo
//   2.00    | 2.35  | capsule  | press scale       | 1.012 → 1        | snap
//   3.10    | 4.35  | beam     | w                 | 0 → 1            | spring (1.0 s, ζ 0.80); the held pencil leaves a
//           |       |          | afterimage        | 0 → 1 → 0        |   dark lilac stripe in the opening fan (80 ms in,
//           |       |          |                   |                  |   fades 0.9 s), only after ≥ 1.2 s of pencil
//   4.30    | 5.10  | beam     | b                 | 1 → 0            | inOut (0.65, 0, 0.35, 1); the lens glows 0.14 s on
//   5.20    | 6.30  | capsule  | m (height, then   | 1 → 0            | spring (0.8 s, ζ 0.90); the width follows 0.05 s
//           |       |          |   width 0.05 s on)|                  |   behind, so the panel shrinks into the island
//   6.30    | 7.20  | capsule  | m                 | 0 → 1            | spring (0.75 s, ζ 0.80), width 0.05 s behind
//   6.80    | 7.95  | beam     | b                 | 0 → 1            | spring (0.9 s, ζ 1): the dotted arc shows the
//           |       |          |                   |                  |   maximum, the solid arc climbs to it, dots fade
//   7.95    | 9.00  | all      | rest              | = t 0            | —
// Rest: 0.5 s + 1.05 s = 17 % of the loop.

export const LOOP = 9;

/** The frame a poster or a preview of the effect should use: the pencil landed, held, the spot bloomed. */
export const POSTER_T = 2.3;

export const T = {
  rest0: 0.5,
  pressDown: 0.45,
  pressUp: 2.0,
  squeeze: 0.5,
  squeezeEnd: 1.54,
  huntEnd: 2.2,
  open: 3.1,
  dim: [4.3, 5.1],
  collapse: 5.2,
  expand: 6.3,
  ignite: 6.8,
  restEnd: 7.95,
  widthLag: 0.05,
  afterglow: 0.14,
  afterIn: 0.08, // 80 ms supporting response as the fan opens
  afterOut: [0.08, 0.98], // … and fades over 0.9 s
} as const;

/** Springs, as (response s, damping ratio) for the analytic timeline, and as Reanimated configs for the finger. */
export const SPRING = {
  squeeze: { response: 1.6, zeta: 0.7 },
  open: { response: 1.0, zeta: 0.8 },
  collapse: { response: 0.8, zeta: 0.9 },
  expand: { response: 0.75, zeta: 0.8 },
  ignite: { response: 0.9, zeta: 1.0 },
  press: { response: 0.35, zeta: 0.85 },
} as const;

// DESIGN-SYSTEM §8 presets used by the finger (mass 1)
export const snap = { damping: 30.5, stiffness: 322.3, mass: 1 } as const;
export const glide = { damping: 22.1, stiffness: 157.9, mass: 1 } as const;
export const pop = { damping: 19.0, stiffness: 195.0, mass: 1 } as const; // focus settles with 5 % overshoot
export const weighty = { damping: 15.1, stiffness: 93.4, mass: 1 } as const;

/** Gesture mapping (design pt; × S on screen). */
export const GESTURE = {
  ptPerBrightness: 220, // a vertical drag of 220 pt runs the lamp from off to full
  ptPerSpread: 120, // moving 120 pt toward the beam's axis squeezes the fan into a pencil
  axisLead: 10, // the first 10 pt pick the leading axis; the other follows at 0.6
  follow: 0.6,
  projectB: 0.08, // release: the level carries 80 ms of the finger's speed, then stops (no spring)
  projectW: 0.12, // release: focus aims at where the finger was heading in 120 ms
  rubber: 0.25, // rubber band past the ends, in units of the value
  offBelow: -0.06, // pulled this far under 0, the lamp clicks off
  tapSlop: 8,
  tapTime: 0.35,
  resumeAfter: 2, // s after the last touch the lamp eases to rest and the loop starts again
  pressScale: 1.012,
  pinchGain: 1.1,
  tickB: 0.1, // a selection tick every 10 % of brightness
  tickTheta: 5, // and every 5° of half-angle
  tickGap: 0.06, // s between ticks …
  tickGapFast: 0.035, // … and when the half-angle moves faster than tickFastDeg
  tickFastDeg: 60, // °/s
  sunHit: 22, // pt: the sun is the handle (touch-down within this radius picks it)
  sunGlide: 0.12, // s: a finger landing more than sunNear from the sun pulls both values to it
  sunNear: 12, // pt
  ceilingOn: 205, // pt of reach: the pencil lands on the ceiling (one Rigid beat), …
  ceilingOff: 190, // … and the cue re-arms below this
  dwell: 1.2, // s of held pencil before opening the fan leaves an afterimage
  afterFade: 0.9, // s
} as const;

/** The beam's half-angle range in degrees (the `angle` prop): w 0 is `min`, w 1 is `max`. */
export type AngleRange = { min: number; max: number };

/** The optics (design pt and degrees). */
export const OPTICS = {
  thetaWide: 40,
  thetaNarrow: 4,
  reachWide: 135, // the pin's arc: 135 pt from the lens at full brightness, wide
  narrowGain: 1.59, // the same flux in a pencil throws 1.59 × further: reach 215 pt, the arc 24 pt clear of the island
  reachOff: 22, // off: a short flat line just above the torch
  offHalf: 20,
  hwWide: 72 / 135, // the arc's half-width over its reach
  hwNarrow: 13 / 236, // a 26 pt bar when narrow
  sagWide: 34 / 135, // how far the arc's ends drop below its apex, over its reach
  hotMax: 7.5, // the core's gain at the narrowest beam (tone-mapped)
} as const;

// ---- helpers (declared above their callers: worklets) ----

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const k = clamp01((x - a) / (b - a));
  return k * k * (3 - 2 * k);
}

/** 0 → 1 step response of a spring (response s, damping ratio). */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

/** A timeline spring (response, damping ratio) as a Reanimated config with mass 1, so the finger moves alike. */
export function springCfg(response: number, zeta: number) {
  'worklet';
  const k = Math.pow((2 * Math.PI) / response, 2);
  return { damping: 2 * zeta * Math.sqrt(k), stiffness: k, mass: 1 };
}

/** Rubber band past an end: `over` beyond the limit shows as less and less (CRAFT §3), in units of the value. */
export function rubber(x: number) {
  'worklet';
  const k = GESTURE.rubber;
  if (x < 0) return -(0.55 * -x) / (1 + -x / k);
  if (x > 1) return 1 + (0.55 * (x - 1)) / (1 + (x - 1) / k);
  return x;
}

/** Cubic bezier easing (x1, y1, x2, y2), solved by Newton steps. */
export function bezier(x1: number, y1: number, x2: number, y2: number, x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  let u = x;
  for (let i = 0; i < 6; i++) {
    const cx = 3 * x1 * u * (1 - u) * (1 - u) + 3 * x2 * u * u * (1 - u) + u * u * u - x;
    const dx = 3 * x1 * (1 - u) * (1 - 4 * u + 3 * u * u) + 3 * x2 * u * (2 - 3 * u) + 3 * u * u;
    if (Math.abs(dx) < 1e-6) break;
    u = Math.min(Math.max(u - cx / dx, 0), 1);
  }
  return 3 * y1 * u * (1 - u) * (1 - u) + 3 * y2 * u * u * (1 - u) + u * u * u;
}

export function inOut(x: number) {
  'worklet';
  return bezier(0.65, 0, 0.35, 1, x);
}

// ---- the timeline: one pure function per value ----

export function spreadAt(t: number) {
  'worklet';
  if (t <= T.squeeze) return 1;
  if (t < T.squeezeEnd) return 1 - Math.pow((t - T.squeeze) / (T.squeezeEnd - T.squeeze), 1.8);
  if (t < T.open) {
    // One pop impulse from focus-ring momentum; peak -.046 at 1.62 s, then it rests as a pencil.
    const dt = t - T.squeezeEnd;
    const omega = Math.sqrt(pop.stiffness / pop.mass);
    const decay = pop.damping / (2 * pop.mass);
    const wd = Math.sqrt(omega * omega - decay * decay);
    const hunt = (-1.37 / wd) * Math.exp(-decay * dt) * Math.sin(wd * dt);
    return hunt * (1 - smooth(T.huntEnd - 0.15, T.huntEnd, t));
  }
  return springAt(t - T.open, SPRING.open.response, SPRING.open.zeta);
}

export function brightnessAt(t: number) {
  'worklet';
  if (t < T.dim[0]) return 1;
  if (t < T.ignite) return 1 - inOut((t - T.dim[0]) / (T.dim[1] - T.dim[0]));
  return springAt(t - T.ignite, SPRING.ignite.response, SPRING.ignite.zeta);
}

/** The capsule's height (lead) — its width is the same curve `T.widthLag` later. */
export function morphAt(t: number) {
  'worklet';
  if (t < T.expand) return 1 - springAt(t - T.collapse, SPRING.collapse.response, SPRING.collapse.zeta);
  return springAt(t - T.expand, SPRING.expand.response, SPRING.expand.zeta);
}

/** The afterimage: a held pencil leaves a dark lilac stripe in the fan as it opens (0.25 s in, fading over 0.9 s). */
export function afterAt(t: number) {
  'worklet';
  const k = t - T.open;
  if (k < 0 || k > T.afterOut[1]) return 0;
  return smooth(0, T.afterIn, k) * (1 - smooth(T.afterOut[0], T.afterOut[1], k));
}

export function pressAt(t: number) {
  'worklet';
  const r = SPRING.press.response;
  const z = SPRING.press.zeta;
  return 1 + (GESTURE.pressScale - 1) * (springAt(t - T.pressDown, r, z) - springAt(t - T.pressUp, r, z));
}

export type Look = {
  b: number; // brightness
  w: number; // spread (may overshoot a little below 0 or above 1)
  m: number; // capsule height 0 island … 1 open
  mw: number; // capsule width
  press: number; // capsule scale
  hot: number; // lens heat: b with a thermal lag (the afterglow when it switches off)
  phase: number; // 0 … 1 over the loop: the heat shimmer
  after: number; // 0 … 1: the afterimage of a held pencil
};

// Declared above lookAt: a worklet captures its closure when it is created, so a later const would be undefined there.
export const REST: Look = { b: 1, w: 1, m: 1, mw: 1, press: 1, hot: 1, phase: 0, after: 0 };

/** The whole look at loop time t. */
export function lookAt(t: number): Look {
  'worklet';
  const tt = ((t % LOOP) + LOOP) % LOOP;
  if (tt >= T.restEnd) return REST;
  const b = brightnessAt(tt);
  const settle = smooth(T.restEnd - 0.3, T.restEnd, tt);
  return {
    b: mix(b, REST.b, settle),
    w: mix(spreadAt(tt), REST.w, settle),
    m: mix(morphAt(tt), REST.m, settle),
    mw: mix(morphAt(tt - T.widthLag), REST.mw, settle),
    press: mix(pressAt(tt), REST.press, settle),
    hot: mix(Math.max(b, 0.85 * brightnessAt(tt - T.afterglow)), REST.hot, settle),
    phase: tt / LOOP,
    after: afterAt(tt),
  };
}

// ---- the optics ----

export type Optics = {
  theta: number; // half-angle, radians
  reach: number; // design pt from the lens to the solid arc's apex
  gain: number; // the core's intensity relative to the wide beam (flux over solid angle, tone-mapped)
  hw: number; // the solid arc's half-width, design pt
  sag: number; // how far its ends drop below the apex, design pt
  reachMax: number; // the dotted arc: where the beam reaches at full brightness for this spread
  hwMax: number;
  sagMax: number;
  dots: number; // the dotted arc's opacity 0 … 1
  arcAlpha: number; // the solid arc's opacity
  glyph: number; // the torch's level: 0.45 off … 1 on
};

export function opticsOf(b: number, w: number, min: number = OPTICS.thetaNarrow, max: number = OPTICS.thetaWide): Optics {
  'worklet';
  const wc = Math.min(Math.max(w, -0.08), 1.06);
  const deg = Math.max(mix(min, max, wc), 1.2);
  const theta = (deg * Math.PI) / 180;
  const w01 = clamp01(w);
  const reachMax = OPTICS.reachWide * mix(OPTICS.narrowGain, 1, w01);
  const on = smooth(0, 0.3, b);
  const reach = mix(OPTICS.reachOff, reachMax, clamp01(b));
  const kHw = mix(OPTICS.hwNarrow, OPTICS.hwWide, w01);
  const curve = Math.pow(w01, 1.5) * OPTICS.sagWide;
  // the gain: flux Φ = b through the solid angle 2π(1 − cos θ), relative to the wide beam, compressed
  const thetaWide = (max * Math.PI) / 180;
  const ratio = (1 - Math.cos(thetaWide)) / Math.max(1 - Math.cos(Math.max(theta, 0.035)), 1e-5);
  const gain = Math.min(Math.pow(ratio, 0.45), OPTICS.hotMax);
  return {
    theta,
    reach,
    gain,
    hw: mix(OPTICS.offHalf, reach * kHw, on),
    sag: reach * curve * on,
    reachMax,
    hwMax: reachMax * kHw,
    sagMax: reachMax * curve,
    dots: (1 - smooth(0.86, 0.97, b)) * smooth(0.03, 0.14, b),
    arcAlpha: mix(0.5, 1, on),
    glyph: mix(0.45, 1, smooth(0, 0.25, b)),
  };
}

/**
 * The arc through (cx, apexY) with half-width hw and sag s, as a circle: centre y and radius, plus its right end and
 * the unit tangent there (pointing on, down the arc) — where the sun sits. A sag under 0.3 pt is a straight bar.
 */
export function arcGeometry(cx: number, apexY: number, hw: number, sag: number) {
  'worklet';
  const h = Math.max(hw, 0.5);
  if (sag < 0.3) {
    return { straight: true, cy: apexY, r: 0, half: 0, ex: cx + h, ey: apexY, tx: 1, ty: 0 };
  }
  const r = (h * h + sag * sag) / (2 * sag);
  const cy = apexY + r;
  const half = Math.asin(Math.min(h / r, 1));
  const ex = cx + r * Math.sin(half);
  const ey = cy - r * Math.cos(half);
  return { straight: false, cy, r, half, ex, ey, tx: Math.cos(half), ty: Math.sin(half) };
}

// ---- the page follows the hero: one light source, spent on the wallpaper (a Skia gradient each, no shader) ----

export const SPILL = {
  glow: 0.1, // screen-white alpha at b * w = 1
  glowRadius: 120, // design pt around the capsule's upper half
  glowFrom: 108,
  halo: 0.22,
  haloRadius: 60,
  dark: 0.12, // multiply tint follows the 140 ms hot afterglow
  shadowOff: 0.42,
  shadowOn: 0.28,
} as const;

/** How much the pencil has landed on the ceiling, 0 … 1 (the shader's own term: the reach, and a focused beam). */
export function landing(reach: number, w: number, b: number) {
  'worklet';
  return smooth(190, 212, reach) * smooth(0.93, 1, 1 - clamp01(w)) * clamp01(b);
}

// ---- the sun is the handle ----

/**
 * Where the sun sits for (b, w), in design pt from the lens: x to the right, y up (the arc's end plus the sun's
 * distance along the arc's tangent there).
 */
export function sunOffset(b: number, w: number, min: number, max: number, along: number) {
  'worklet';
  const o = opticsOf(b, w, min, max);
  const g = arcGeometry(0, 0, o.hw, o.sag);
  return { x: g.ex + g.tx * along, y: o.reach - g.ey - g.ty * along };
}

/** Polar sun control law, calibrated to the pin's non-polar arc at touch-down. */
export function solveSun(fx: number, fy: number, b0: number, w0: number, min: number, max: number, along: number) {
  'worklet';
  // Preserve the value on grab: the pin's sun rides a circular arc, not a polar ruler.
  // After this coordinate conversion the spec's 19 pt reach offset / .89 angle factor are used literally.
  const atGrab = sunOffset(clamp01(b0), clamp01(w0), min, max, along);
  const o = opticsOf(b0, w0, min, max);
  const phi = o.theta * 0.89;
  const rho = o.reach + 19;
  const x = fx + rho * Math.sin(phi) - atGrab.x;
  const y = fy + rho * Math.cos(phi) - atGrab.y;
  const thetaDeg = (Math.atan2(Math.abs(x), y) * 180) / (Math.PI * 0.89);
  const w = (thetaDeg - min) / Math.max(max - min, 1);
  const maxReach = opticsOf(1, clamp01(w), min, max).reachMax;
  const reach = Math.hypot(x, y) - 19;
  return { b: (reach - OPTICS.reachOff) / (maxReach - OPTICS.reachOff), w };
}
