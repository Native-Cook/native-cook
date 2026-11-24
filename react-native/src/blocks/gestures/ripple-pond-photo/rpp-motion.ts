// Ripple Pond Photo — the loop, the simulation's constants and the scripted hand, in closed form.
//
// Everything a Swift or Kotlin port needs is a named number here: the 10 s loop, the wave step, the
// drops and the ghost finger. The water itself is the one thing that is not a function of time: it is
// a height field stepped at STEP_HZ from the drops below, cleared at t = 0 while it is provably calm
// (see the notes for the measured energy before the cut).

export const LOOP = 10;

/** the wave simulation (gpu/rpp-wgsl.ts runs it; RppSkiaFallback only imitates its rings) */
export const SIM = {
  /** grid columns; rows follow the screen's aspect so a cell is square */
  gridW: 256,
  /** fixed steps a second, however fast the display refreshes */
  stepHz: 240,
  /** (wave speed x dt / dx)^2: 0.469 cells a step, about 173 pt/s at 393 pt wide */
  c2: 0.22,
  /** amplitude kept per step: 0.34 of it is left after a second */
  damp: 0.9955,
  /** the damping the water is eased to before the loop's cut and before autoplay resumes */
  calmDamp: 0.94,
  /** most steps one frame may run (a stall is not caught up with a spike) */
  maxSteps: 12,
} as const;

/** what a touch does to the water */
export const DROP = {
  /** a tap: a zero-mean dip (1 - q) exp(-q), q = r^2 / radius^2, radius in cells */
  tapRadius: 10,
  tapDepth: -2.2,
  /** a tap is pressed into the field over this many steps with a sin^2 envelope */
  tapSteps: 8,
  /** a drag: the same dip, smaller, laid along the path */
  wakeRadius: 6,
  /** depth per point of travel, so a faster finger cuts a deeper wake */
  wakePerPt: -0.07,
  /** deepest wake one frame may add */
  wakeMax: -0.9,
} as const;

/** how the field is shown (pt and per unit of slope, slope = height difference across one cell) */
export const LOOK = {
  refract: 90,
  refractMax: 26,
  /** red and blue are refracted a little more and less than green */
  chroma: 0.08,
  normal: 9,
  specPower: 48,
  spec: 1.2,
  caustic: 14,
  shade: 7,
} as const;

/** the loop's phases, seconds */
export const T = {
  /** scripted taps: time, x and y as fractions of the screen */
  taps: [
    [0.9, 0.3, 0.72],
    [2.3, 0.72, 0.3],
    [5.9, 0.42, 0.8],
  ],
  /** the scripted drag */
  drag: [3.5, 5.0],
  /** damping eases from SIM.damp to SIM.calmDamp, and stays there until the cut */
  calm: [7.9, 8.4],
  /** the ghost finger leads a tap by this much, and lingers this long after it */
  ghostIn: 0.22,
  ghostOut: 0.3,
} as const;

/** a touch hands the water back this long after it ends */
export const IDLE = 2;
/** then the water is calmed for this long (damping eased over the first part) and the loop restarts */
export const RETURN = { ease: 0.45, total: 1.0 } as const;

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

/** the share of a tap's depth pressed in by the time x (0…1) of its envelope has passed */
export function tapShare(x: number) {
  'worklet';
  const v = clamp01(x);
  return v - Math.sin(2 * Math.PI * v) / (2 * Math.PI);
}

/** the scripted drag's path, fractions of the screen: a shallow arc from the lower left to the right */
export function dragX(u: number) {
  'worklet';
  return 0.16 + 0.68 * smooth(clamp01(u));
}

export function dragY(u: number) {
  'worklet';
  const e = smooth(clamp01(u));
  return 0.7 - 0.1 * Math.sin(Math.PI * e) - 0.02 * e;
}

/** which scripted tap the ghost finger is busy with at t, or -1 */
export function ghostTap(t: number) {
  'worklet';
  for (let i = 0; i < T.taps.length; i++) {
    const at = T.taps[i][0];
    if (t >= at - T.ghostIn && t < at + T.ghostOut) return i;
  }
  return -1;
}

/** the ghost finger's opacity at t */
export function ghostAlpha(t: number) {
  'worklet';
  const i = ghostTap(t);
  if (i >= 0) {
    const at = T.taps[i][0];
    return easeOutCubic(seg(t, at - T.ghostIn, at - 0.06)) * (1 - smooth(seg(t, at + 0.08, at + T.ghostOut)));
  }
  return easeOutCubic(seg(t, T.drag[0] - T.ghostIn, T.drag[0])) * (1 - smooth(seg(t, T.drag[1], T.drag[1] + T.ghostOut)));
}

/** how hard the ghost finger presses at t, 0…1 (it lands on the water at a tap's time) */
export function ghostPress(t: number) {
  'worklet';
  const i = ghostTap(t);
  if (i >= 0) {
    const at = T.taps[i][0];
    return smooth(seg(t, at - 0.07, at)) * (1 - smooth(seg(t, at + 0.06, at + 0.24)));
  }
  return smooth(seg(t, T.drag[0] - 0.08, T.drag[0])) * (1 - smooth(seg(t, T.drag[1], T.drag[1] + 0.2)));
}

/** the damping of a step at loop time t */
export function dampAt(t: number) {
  'worklet';
  return SIM.damp + (SIM.calmDamp - SIM.damp) * seg(t, T.calm[0], T.calm[1]);
}

/** grid rows for a screen, even, so a cell is as tall as it is wide */
export function gridRows(width: number, height: number) {
  return Math.max(256, Math.min(640, Math.round((SIM.gridW * height) / width / 2) * 2));
}
