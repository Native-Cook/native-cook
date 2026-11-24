// Ink Wash Page Turn — the loop, the bleed's numbers and the scripted hand, in closed form.
//
// Everything a Swift or Kotlin port needs is a named number here. A page turn is one number,
// `progress` 0…1, with the point the ink starts from; iwp-shader.ts turns it into the picture.

export const LOOP = 10;
export const PAGES = 3;

/** the bleed, in points (iwp-shader.ts) */
export const INK = {
  /** blotches: domain-warped noise of this size pushes the front in and out by ± half of `blot` */
  blotSize: 90,
  blot: 150,
  warp: 1.6,
  /** paper fibres: two sets of streaks (15° and −20° off the horizontal) the ink wicks along, ± half of `fibre` */
  fibre: 26,
  /** fine feathering of the edge, ± half */
  feather: 5,
  /** the front's radius runs from −lead at progress 0 to reach + trail at 1 (both clear the noise's ± 91 and the reveal) */
  lead: 100,
  trail: 130,
  /** the new page shows through over this distance behind the front */
  reveal: 22,
  /** the dark lip on the front (Gaussian sigma), the pooled ink behind it (e-fold) and the soak ahead of it (e-fold) */
  lip: 4.5,
  pool: 36,
  soak: 9,
  /** their weights, and how strongly density turns into ink: cover = 1 − exp(−density × strength) */
  lipWeight: 0.8,
  poolWeight: 0.8,
  soakWeight: 0.5,
  strength: 2.2,
  /** wet paper swells: both pages wobble by up to ± half of this near the front */
  wobble: 9,
  /** the wet sheen: a band this far behind the front, this wide (sigma), this bright */
  sheenAt: 7,
  sheenWidth: 4,
  sheen: 0.1,
  /** wet paper behind the front is darker by this much, drying over this distance (e-fold) */
  wet: 0.07,
  wetTail: 120,
  /** the effects fade in over the first and out over the last of progress, so 0 and 1 are the bare pages */
  ends: 0.03,
} as const;

/** how a finger turns the page */
export const TURN = {
  /** a drag of this share of the screen's width is progress 1 (before the rubber band) */
  scrub: 0.62,
  /** past this progress the drag rubber-bands towards `bandMax` */
  band: 0.6,
  bandMax: 0.9,
  /** a release completes the turn when progress + project × its speed (1/s) passes `commit` */
  project: 0.18,
  commit: 0.3,
  /** a touch that travels less than this is a tap, pt */
  tap: 8,
  /** a tap left of this share of the width turns back, anywhere else forward */
  backZone: 0.3,
  /** the settle is a critically damped spring: x = target + (A + B τ) e^(−ω τ) */
  omega: 4.2,
  /** a tap's bloom is slower */
  tapOmega: 3.0,
  /** the spring's remainder is blended out between these times after the release, s */
  settle: [1.2, 1.6],
  tapSettle: [1.7, 2.2],
} as const;

/** the loop's three turns: [finger down, finger up, x, y of the touch as fractions of the screen] */
export const T = {
  /** 1 → 2: a swipe from the lower right */
  first: [0.7, 1.5, 0.8, 0.74],
  /** 2 → 3: a swipe from the upper left of centre */
  second: [3.5, 4.3, 0.46, 0.36],
  /** 3 → 1: a tap in the middle */
  tap: [6.3, 0.5, 0.52],
  /** the scripted swipes travel this share of the width to the left, accelerating (u²) */
  travel: 0.36,
  /** the ghost finger leads a touch by this much, and lingers this long after it */
  ghostIn: 0.22,
  ghostOut: 0.3,
} as const;

/** a touch hands the pages back this long after it ends */
export const IDLE = 2;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** 0 before a, 1 after b */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(v: number) {
  'worklet';
  return v * v * (3 - 2 * v);
}

export function easeOutCubic(v: number) {
  'worklet';
  const u = 1 - v;
  return 1 - u * u * u;
}

/** progress for a drag of `x` scrub lengths: 1:1 up to TURN.band, then a rubber band */
export function scrub(x: number) {
  'worklet';
  if (x <= TURN.band) return Math.max(0, x);
  const room = TURN.bandMax - TURN.band;
  return TURN.band + room * (1 - Math.exp(-(x - TURN.band) / room));
}

/** the settle: from x0 with speed v0 (1/s) to target, tau seconds after the release, exact at `until[1]` */
export function settle(x0: number, v0: number, target: number, tau: number, omega: number, from: number, until: number) {
  'worklet';
  const a = x0 - target;
  const b = v0 + omega * a;
  const x = target + (a + b * tau) * Math.exp(-omega * tau);
  const k = smooth(seg(tau, from, until));
  return clamp01(x + (target - x) * k);
}

/** progress of a scripted swipe (down, up) at loop time t, 0 before and 1 once settled */
export function swipeAt(t: number, down: number, up: number) {
  'worklet';
  if (t <= down) return 0;
  if (t < up) {
    const u = (t - down) / (up - down);
    return scrub((T.travel * u * u) / TURN.scrub);
  }
  // released at the swipe's final speed
  const x0 = scrub(T.travel / TURN.scrub);
  const v0 = (2 * T.travel) / (up - down) / TURN.scrub;
  return settle(x0, v0, 1, t - up, TURN.omega, TURN.settle[0], TURN.settle[1]);
}

/** progress of the scripted tap at loop time t */
export function tapAt(t: number) {
  'worklet';
  if (t <= T.tap[0]) return 0;
  return settle(0, 0, 1, t - T.tap[0], TURN.tapOmega, TURN.tapSettle[0], TURN.tapSettle[1]);
}

/** which turn the loop is on at t: 0, 1 or 2 (a turn lasts until the next begins) */
export function turnAt(t: number) {
  'worklet';
  return t < T.second[0] ? 0 : t < T.tap[0] ? 1 : 2;
}

/** the ghost finger's opacity at t */
export function ghostAlpha(t: number) {
  'worklet';
  const a = easeOutCubic(seg(t, T.first[0] - T.ghostIn, T.first[0])) * (1 - smooth(seg(t, T.first[1], T.first[1] + T.ghostOut)));
  const b = easeOutCubic(seg(t, T.second[0] - T.ghostIn, T.second[0])) * (1 - smooth(seg(t, T.second[1], T.second[1] + T.ghostOut)));
  const c = easeOutCubic(seg(t, T.tap[0] - T.ghostIn, T.tap[0] - 0.06)) * (1 - smooth(seg(t, T.tap[0] + 0.08, T.tap[0] + T.ghostOut)));
  return Math.max(a, b, c);
}

/** how hard the ghost finger presses at t, 0…1 */
export function ghostPress(t: number) {
  'worklet';
  const a = smooth(seg(t, T.first[0] - 0.08, T.first[0])) * (1 - smooth(seg(t, T.first[1], T.first[1] + 0.2)));
  const b = smooth(seg(t, T.second[0] - 0.08, T.second[0])) * (1 - smooth(seg(t, T.second[1], T.second[1] + 0.2)));
  const c = smooth(seg(t, T.tap[0] - 0.07, T.tap[0])) * (1 - smooth(seg(t, T.tap[0] + 0.06, T.tap[0] + 0.24)));
  return Math.max(a, b, c);
}

/** the ghost finger's x at t, as a fraction of the width */
export function ghostX(t: number) {
  'worklet';
  const k = turnAt(t);
  if (k === 2) return T.tap[1];
  const s = k === 0 ? T.first : T.second;
  const u = seg(t, s[0], s[1]);
  return s[2] - T.travel * u * u;
}

/** the ghost finger's y at t, as a fraction of the height */
export function ghostY(t: number) {
  'worklet';
  const k = turnAt(t);
  return k === 2 ? T.tap[2] : k === 0 ? T.first[3] : T.second[3];
}
